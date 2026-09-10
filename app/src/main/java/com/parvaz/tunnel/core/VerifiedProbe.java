package com.parvaz.tunnel.core;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Strict, body-free responses through a pinned session port; no direct retry. */
final class VerifiedProbe {
 static final long UNKNOWN=-2;
 static long measure(int port,String configured)throws InterruptedException{return measure(port,configured,false);}
 static long measure(int port,String configured,boolean twice)throws InterruptedException {
  if(port<=0)return UNKNOWN;
  for(String endpoint:ReadinessMonitor.endpoints(configured)){
   long worst=0;boolean valid=true;
   for(int sample=0;sample<(twice?2:1);sample++){
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    // A fresh bounded worker per completed request avoids racing the previous
    // worker's return; no guessed sleep or abandoned request queue is needed.
    try(StartupWarmup probe=new StartupWarmup()){
     CountDownLatch done=new CountDownLatch(1);AtomicLong result=new AtomicLong(-1);
     probe.start(endpoint,url->StartupWarmup.pinnedConnection(url,port),(ok,ms)->{if(ok)result.set(Math.max(1,ms));else if(ms<0)result.set(UNKNOWN);done.countDown();});
     if(!done.await(StartupWarmup.DEADLINE_MS+1000L,TimeUnit.MILLISECONDS))probe.cancel();
     if(result.get()==UNKNOWN)return UNKNOWN;
     if(result.get()<=0){valid=false;break;}worst=Math.max(worst,result.get());
    }
   }
   if(valid)return worst;
  }
  return -1;
 }
}
