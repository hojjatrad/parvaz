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
     new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"Parvaz latency");t.setDaemon(true);return t;});
 static {WORKERS.allowCoreThreadTimeOut(true);}
 /** One coordinator thread drives a shared-core batch; the parallelism lives inside it. */
 private static final ExecutorService COORDINATOR=Executors.newSingleThreadExecutor(r->{
  Thread t=new Thread(r,"Parvaz latency batch");t.setDaemon(true);return t;});
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ProfileStore store;
 private final Context app;
 private final ExecutorService executor;
 private final Measurer measurer;
 private final Map<String,Job> jobs=new HashMap<>();
 private Batch batch;
 private boolean closed;
 /** False when a test or caller injected its own measurer: that measurer must be honoured
  *  row by row instead of being replaced by the shared-core engine path. */
 private final boolean sharedBatch;
 public PingManager(Context context){this(context.getApplicationContext(),WORKERS,defaultMeasurer(context.getApplicationContext()),true);}
 private static Measurer defaultMeasurer(Context app){return p->ProxyMeasurement.measureQueued(app,p,
     app.getSharedPreferences("parvaz_prefs",0).getString("ping_url","https://www.gstatic.com/generate_204"));}

 PingManager(Context context,ExecutorService executor,Measurer measurer){this(context,executor,measurer,false);}
 private PingManager(Context context,ExecutorService executor,Measurer measurer,boolean sharedBatch){this.app=context.getApplicationContext();this.store=ProfileStore.f(app);this.executor=executor;this.measurer=measurer;this.sharedBatch=sharedBatch;}
 private static final class Batch {int remaining;boolean cancelled;Listener listener;final List<Job> jobs=new ArrayList<>();Batch(Listener listener){this.listener=listener;}}
 public synchronized boolean isBatchBusy(){return batch!=null;}
 public synchronized void testOne(Profile profile,Listener listener){if(!closed)submit(profile,null,listener,measurer);}
 /** List-wide measurement. Uses the shared-core batch path so the whole list is measured
  *  in parallel behind ONE engine instance per group instead of one instance per row. */
 public synchronized boolean startBatch(List<Profile> profiles,Listener listener){
  if(!sharedBatch)return startBatch(profiles,listener,measurer);
  if(closed||batch!=null)return false;
  Batch owner=new Batch(listener);batch=owner;owner.remaining=profiles.size();
  if(profiles.isEmpty()){handler.post(()->finishBatch(owner));return true;}
  List<Profile> snapshot=new ArrayList<>(profiles);
  long network=NetworkEpoch.current();
  Map<String,Job> created=new LinkedHashMap<>();
  for(Profile profile:snapshot){
   Job job=register(profile,owner,listener);job.observedNetwork=network;created.put(profile.id,job);
  }
  String url=app.getSharedPreferences("parvaz_prefs",0).getString("ping_url","https://www.gstatic.com/generate_204");
  try{COORDINATOR.execute(()->runSharedBatch(snapshot,created,owner,url));}
  catch(RejectedExecutionException rejected){for(Job job:created.values())job.finish(LatencyResult.BUSY);}
  return true;
 }
 private void runSharedBatch(List<Profile> profiles,Map<String,Job> created,Batch owner,String url){
  try{
   BatchLatency.measure(app,profiles,url,new BatchLatency.Sink(){
    @Override public void result(String id,long raw,String target){
     Job job=created.get(id);if(job==null)return;
     job.target=target;job.finish(LatencyResult.measured(raw));
    }
    @Override public boolean cancelled(){
     synchronized(PingManager.this){return closed||batch!=owner||owner.cancelled;}
    }
   });
  }catch(Throwable failure){
   for(Job job:created.values())job.finish(LatencyResult.START_ERROR);
  }finally{
   // Nothing may stay in TESTING: whatever the batch did not report is cancelled.
   for(Job job:created.values())job.finish(LatencyResult.CANCELLED);
  }
 }
 public synchronized boolean startBypassBatch(List<Profile> profiles,Listener listener){return startBatch(profiles,listener,p->ProxyMeasurement.measureStrictTarget(app,p,RealBypassTester.FILTERED_PROBE_URL));}
 private boolean startBatch(List<Profile> profiles,Listener listener,Measurer selected){
  if(closed||batch!=null)return false;
  Batch owner=new Batch(listener);batch=owner;owner.remaining=profiles.size();
  if(profiles.isEmpty()){handler.post(()->finishBatch(owner));return true;}
  for(Profile profile:profiles)submit(profile,owner,listener,selected);
  return true;
 }
 private void submit(Profile profile,Batch owner,Listener listener,Measurer selected){
  Job job=register(profile,owner,listener);job.assign(selected);
  try{job.future=executor.submit(job);}catch(RejectedExecutionException e){job.finish(LatencyResult.BUSY);}
 }
 /** Creates and books the row's measurement without deciding how it will be measured. */
 private Job register(Profile profile,Batch owner,Listener listener){
  Job previous=jobs.get(profile.id);if(previous!=null){previous.cancel();purge();}
  ProfileStore.Measurement measurement=store.beginMeasurement(profile);
  Job job=new Job(profile.id,measurement,owner,listener,measurer);jobs.put(profile.id,job);if(owner!=null)owner.jobs.add(job);
  return job;
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
  final String id;final ProfileStore.Measurement measurement;final Batch owner;Measurer selected;
  void assign(Measurer replacement){this.selected=replacement;}
  final AtomicBoolean finished=new AtomicBoolean();volatile long observedNetwork=-1;volatile String target;Listener listener;Future<?> future;
  Job(String id,ProfileStore.Measurement m,Batch owner,Listener listener,Measurer selected){this.id=id;this.measurement=m;this.owner=owner;this.listener=listener;this.selected=selected;}
  void cancel(){if(future!=null)future.cancel(true);finish(LatencyResult.CANCELLED);}
  void finish(int value){
   if(!finished.compareAndSet(false,true))return;
   handler.post(()->{
    synchronized(PingManager.this){
     int scoped=observedNetwork<0?value:LatencyResult.scoped(value,NetworkEpoch.owns(observedNetwork));
     boolean applied=store.finishMeasurement(measurement,scoped,target);
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
   VerifiedProbe.clearTarget();
   try{if(store.ownsMeasurement(measurement))result=LatencyResult.measured(selected.measure(measurement.snapshot));target=VerifiedProbe.lastTarget();}
   catch(InterruptedException e){Thread.currentThread().interrupt();result=LatencyResult.CANCELLED;}
   catch(Exception e){result=LatencyResult.START_ERROR;}
   result=LatencyResult.scoped(result,NetworkEpoch.owns(network));
   finish(result);
  }
 }
}
