package com.parvaz.tunnel.core;
/** Consistent authenticated HTTPS RTT; never includes queue/core/TLS setup time. */
final class VerifiedProbe {
 static final long UNKNOWN=-2;
 static long measure(int port,String configured)throws InterruptedException{return measure(port,configured,false);}
 static long measure(int port,String configured,boolean multiple)throws InterruptedException {
  long result=measureDetailed(port,configured,multiple);
  return automatic(result); // Local saturation is not server failure.
 }
 static long automatic(long result){return result==HttpsLatency.BUSY?UNKNOWN:result>0||result==UNKNOWN?result:-1;}
 static long measureDetailed(int port,String configured,boolean multiple)throws InterruptedException {
  if(port<=0)return UNKNOWN;
  long last=UNKNOWN;
  try(HttpsLatency.Session probe=new HttpsLatency.Session(port)){
   for(String endpoint:ReadinessMonitor.endpoints(configured)){
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    last=probe.measure(endpoint,multiple?3:1);
    if(last>0||last==UNKNOWN||last==HttpsLatency.BUSY)return last;
   }
  }
  return last;
 }
}
