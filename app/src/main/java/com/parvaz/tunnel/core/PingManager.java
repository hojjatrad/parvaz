package com.parvaz.tunnel.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded manual tests, with distinct queued/cancelled/unknown/failed results.
 * Logical cancellation never frees the native permit: ProxyMeasurement owns that
 * until its actual cleanup returns. No result from an older job overwrites a newer one. */
public final class PingManager implements AutoCloseable {
 public interface Listener {void onResult(String id);void onFinished(boolean cancelled);}
 interface Measurer {long measure(Profile profile)throws Exception;}
 private static final ThreadPoolExecutor WORKERS=new ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,
     new LinkedBlockingQueue<>(),r->{Thread t=new Thread(r,"Parvaz latency");t.setDaemon(true);return t;});
 static {WORKERS.allowCoreThreadTimeOut(true);}
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ProfileStore store;
 private final Context app;
 private final ExecutorService executor;
 private final Measurer measurer;
 private final Map<String,Job> jobs=new HashMap<>();
 private Batch batch;
 private boolean closed;
 public PingManager(Context context){this(context.getApplicationContext(),WORKERS,defaultMeasurer(context.getApplicationContext()));}
 private static Measurer defaultMeasurer(Context app){return p->ProxyMeasurement.measureQueued(app,p,
     app.getSharedPreferences("parvaz_prefs",0).getString("ping_url","https://www.gstatic.com/generate_204"));}

 PingManager(Context context,ExecutorService executor,Measurer measurer){this.app=context.getApplicationContext();this.store=ProfileStore.f(app);this.executor=executor;this.measurer=measurer;}
 private static final class Batch {int remaining;boolean cancelled;Listener listener;final List<Job> jobs=new ArrayList<>();Batch(Listener listener){this.listener=listener;}}
 public synchronized boolean isBatchBusy(){return batch!=null;}
 public synchronized void testOne(Profile profile,Listener listener){if(!closed)submit(profile,null,listener,measurer);}
 public synchronized boolean startBatch(List<Profile> profiles,Listener listener){return startBatch(profiles,listener,measurer);}
 public synchronized boolean startBypassBatch(List<Profile> profiles,Listener listener){return startBatch(profiles,listener,p->ProxyMeasurement.measureQueued(app,p,RealBypassTester.FILTERED_PROBE_URL));}
 private boolean startBatch(List<Profile> profiles,Listener listener,Measurer selected){
  if(closed||batch!=null)return false;
  Batch owner=new Batch(listener);batch=owner;owner.remaining=profiles.size();
  if(profiles.isEmpty()){handler.post(()->finishBatch(owner));return true;}
  for(Profile profile:profiles)submit(profile,owner,listener,selected);
  return true;
 }
 private void submit(Profile profile,Batch owner,Listener listener,Measurer selected){
  Job previous=jobs.get(profile.id);if(previous!=null)previous.cancel();
  ProfileStore.Measurement measurement=store.beginMeasurement(profile);
  Job job=new Job(profile.id,measurement,owner,listener,selected);jobs.put(profile.id,job);if(owner!=null)owner.jobs.add(job);
  try{job.future=executor.submit(job);}catch(RejectedExecutionException e){job.finish(LatencyResult.BUSY);}
 }
 public synchronized void cancelBatch(){
  Batch owner=batch;if(owner==null)return;owner.cancelled=true;
  for(Job job:owner.jobs)job.cancel();purge();
 }
 private void purge(){if(executor instanceof ThreadPoolExecutor)((ThreadPoolExecutor)executor).purge();}
 private synchronized void finishBatch(Batch owner){
  if(batch!=owner)return;batch=null;owner.jobs.clear();store.saveMeasurements();Listener listener=owner.listener;owner.listener=null;
  if(!closed&&listener!=null)listener.onFinished(owner.cancelled);
 }
 @Override public synchronized void close(){
  if(closed)return;closed=true;if(batch!=null){batch.listener=null;batch=null;}
  for(Job job:new ArrayList<>(jobs.values())){job.listener=null;job.cancel();}jobs.clear();purge();
 }
 private final class Job implements Runnable {
  final String id;final ProfileStore.Measurement measurement;final Batch owner;final Measurer selected;
  final AtomicBoolean finished=new AtomicBoolean();volatile long observedNetwork=-1;Listener listener;Future<?> future;
  Job(String id,ProfileStore.Measurement m,Batch owner,Listener listener,Measurer selected){this.id=id;this.measurement=m;this.owner=owner;this.listener=listener;this.selected=selected;}
  void cancel(){if(future!=null)future.cancel(true);finish(LatencyResult.CANCELLED);}
  void finish(int value){
   if(!finished.compareAndSet(false,true))return;
   handler.post(()->{
    synchronized(PingManager.this){
     int scoped=value>0&&!NetworkEpoch.owns(observedNetwork)?LatencyResult.UNCONFIRMED:value;
     boolean applied=store.finishMeasurement(measurement,scoped);
     if(jobs.get(id)==this)jobs.remove(id);
     Listener callback=listener;listener=null;
     if(!closed&&applied&&callback!=null)callback.onResult(id);
     if(owner!=null){if(--owner.remaining==0)finishBatch(owner);}
     else if(!closed){store.saveMeasurements();if(callback!=null)callback.onFinished(value==LatencyResult.CANCELLED);}
    }
   });
  }
  @Override public void run(){
   if(finished.get())return;
   int result=LatencyResult.CANCELLED;long network=NetworkEpoch.current();observedNetwork=network;
   try{if(store.ownsMeasurement(measurement))result=LatencyResult.measured(selected.measure(measurement.snapshot));}
   catch(InterruptedException e){Thread.currentThread().interrupt();result=LatencyResult.CANCELLED;}
   catch(Exception e){result=LatencyResult.UNCONFIRMED;}
   if(!NetworkEpoch.owns(network))result=LatencyResult.UNCONFIRMED;
   finish(result);
  }
 }
}
