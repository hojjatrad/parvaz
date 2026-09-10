package com.parvaz.tunnel.core;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** At most three live measurements across races, including a native call which
 * ignores interruption. Never queues another batch behind abandoned native work. */
final class BoundedProbeRace {
 static final int LIMIT=3;
 private static final Semaphore SLOTS=new Semaphore(LIMIT);
 interface Probe<T>{long measure(T candidate)throws Exception;}
 enum Status{SUCCESS,NO_RESPONSE,TIMEOUT,CANCELLED,BUSY}
 static final class Result<T>{T winner;long delay=-1,elapsedMs;int probed;Status status=Status.NO_RESPONSE;final List<T> failed=new ArrayList<>();}
 static final class Sample<T>{T candidate;long delay;boolean attempted;Sample(T c,long d,boolean a){candidate=c;delay=d;attempted=a;}}
 static <T> Result<T> run(List<T> candidates,int parallel,long timeoutMs,Probe<T> probe,BooleanSupplier current){
  Result<T> result=new Result<>();long started=System.nanoTime();
  int count=Math.min(candidates.size(),Math.min(LIMIT,parallel<=0?LIMIT:parallel));
  if(!current.getAsBoolean()){result.status=Status.CANCELLED;return result;}
  if(count==0)return result;
  AtomicInteger attempted=new AtomicInteger();
  ExecutorService pool=Executors.newFixedThreadPool(count,r->{Thread t=new Thread(r,"parvaz-bounded-probe");t.setDaemon(true);return t;});
  CompletionService<Sample<T>> completed=new ExecutorCompletionService<>(pool);
  List<Future<Sample<T>>> futures=new ArrayList<>();
  try{
   for(int i=0;i<count;i++){
    T candidate=candidates.get(i);
    futures.add(completed.submit(()->{
     if(Thread.currentThread().isInterrupted()||!current.getAsBoolean()||!SLOTS.tryAcquire())return new Sample<>(candidate,-1,false);
     try{
      if(Thread.currentThread().isInterrupted()||!current.getAsBoolean())return new Sample<>(candidate,-1,false);
      attempted.incrementAndGet();long delay;
      try{delay=probe.measure(candidate);}catch(Exception e){delay=-1;}
      if(Thread.currentThread().isInterrupted()||!current.getAsBoolean())return new Sample<>(candidate,-1,false);
      return new Sample<>(candidate,delay,true);
     }finally{SLOTS.release();}
    }));
   }
   for(int left=count;left>0;){
    if(!current.getAsBoolean()){result.status=Status.CANCELLED;break;}
    long remaining=TimeUnit.MILLISECONDS.toNanos(Math.max(0,timeoutMs))-(System.nanoTime()-started);
    if(remaining<=0){result.status=Status.TIMEOUT;break;}
    Future<Sample<T>> future=completed.poll(Math.min(remaining,TimeUnit.MILLISECONDS.toNanos(100)),TimeUnit.NANOSECONDS);
    if(future==null)continue;left--;
    Sample<T> sample;
    try{sample=future.get();}catch(ExecutionException failed){continue;}
    if(!sample.attempted)continue;
    if(sample.delay>0){result.winner=sample.candidate;result.delay=sample.delay;result.status=Status.SUCCESS;break;}
    result.failed.add(sample.candidate);
   }
   if(!current.getAsBoolean()){result.winner=null;result.failed.clear();result.status=Status.CANCELLED;}
   if(result.status==Status.NO_RESPONSE&&attempted.get()==0)result.status=Status.BUSY;
  }catch(InterruptedException cancelled){Thread.currentThread().interrupt();result.winner=null;result.failed.clear();result.status=Status.CANCELLED;}
  finally{for(Future<?> future:futures)future.cancel(true);pool.shutdownNow();}
  result.probed=attempted.get();result.elapsedMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);return result;
 }
}
