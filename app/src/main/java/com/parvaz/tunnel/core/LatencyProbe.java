package com.parvaz.tunnel.core;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
/** One cancellable request worker for the live readiness monitor. No second core. */
final class LatencyProbe implements AutoCloseable {
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"parvaz-live-latency");t.setDaemon(true);return t;});
 private final AtomicReference<Attempt> active=new AtomicReference<>();
 private static final class Attempt {
  final StartupWarmup.Result result;private HttpsLatency.Session session;private boolean cancelled;
  Attempt(StartupWarmup.Result result){this.result=result;}
  synchronized boolean attach(HttpsLatency.Session session){if(cancelled){session.close();return false;}this.session=session;return true;}
  synchronized void cancel(){cancelled=true;if(session!=null)session.cancel();}
 }
 void start(String url,int port,StartupWarmup.Result result){
  cancel();Attempt attempt=new Attempt(result);active.set(attempt);
  try{worker.execute(()->{
   long measured=HttpsLatency.UNKNOWN;
   try(HttpsLatency.Session session=new HttpsLatency.Session(port)){if(attempt.attach(session))measured=session.measure(url,1);}
   catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
   catch(RuntimeException unavailable){/* Never treat local setup failure as verified response. */}
   if(active.compareAndSet(attempt,null))attempt.result.finished(measured>0,measured>0?measured:-1);
  });}catch(RejectedExecutionException busy){if(active.compareAndSet(attempt,null))result.finished(false,-1);}
 }
 void cancel(){Attempt old=active.getAndSet(null);if(old!=null)old.cancel();}
 @Override public void close(){cancel();worker.shutdownNow();}
}
