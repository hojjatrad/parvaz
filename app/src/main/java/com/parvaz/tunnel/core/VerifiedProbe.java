package com.parvaz.tunnel.core;
/** Consistent authenticated HTTPS RTT; never includes queue/core/TLS setup time. */
final class VerifiedProbe {
 static final long UNKNOWN=-2,START_FAILED=-25,ROUTE_UNVERIFIED=-26;
 private static final ThreadLocal<String> TARGET=new ThreadLocal<>();
 static void clearTarget(){TARGET.remove();}static String lastTarget(){return TARGET.get();}
 static String[] endpoints(String configured,boolean strict){return strict?new String[]{configured}:ReadinessMonitor.endpoints(configured);}
 private static String targetName(String endpoint){try{String host=new java.net.URI(endpoint).getHost();if("www.youtube.com".equalsIgnoreCase(host))return "YouTube";if("www.gstatic.com".equalsIgnoreCase(host))return "Google";if("cp.cloudflare.com".equalsIgnoreCase(host)||"www.cloudflare.com".equalsIgnoreCase(host))return "Cloudflare";}catch(Exception ignored){}return "custom target";}

 static long measure(int port,String configured)throws InterruptedException{return measure(port,configured,false);}
 static long measure(int port,String configured,boolean multiple)throws InterruptedException {
  long result=measureDetailed(port,configured,multiple);
  return automatic(result); // Local saturation is not server failure.
 }
 static long automatic(long result){return result==HttpsLatency.BUSY?UNKNOWN:result>0||result==UNKNOWN?result:-1;}
 static long measureDetailed(int port,String configured,boolean multiple)throws InterruptedException {
  return measureDetailed(port,configured,multiple,false);
 }
 /** One authenticated HTTPS round trip with list-wide deadlines, for shared-core batches.
  *  Same proof as the slow path - 2xx response headers over the pinned route - but a single
  *  sample and shorter timeouts, because the list is measured in parallel. */
 static long measureFast(int port,String configured)throws InterruptedException {return measureFast(port,configured,false);}
 /** @param strictTarget true to accept ONLY the configured endpoint, with no fallback list.
  *  The real-bypass test depends on this: proving a filtered host answered is the whole
  *  point of that run, so silently succeeding against a neutral endpoint would be a lie. */
 static long measureFast(int port,String configured,boolean strictTarget)throws InterruptedException {
  clearTarget();if(port<=0)return UNKNOWN;
  long last=UNKNOWN;
  try(HttpsLatency.Session probe=HttpsLatency.Session.fast(port)){
   for(String endpoint:endpoints(configured,strictTarget)){
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    last=probe.measure(endpoint,1);
    if(last>0){TARGET.set(targetName(endpoint));return last;}
    if(last==HttpsLatency.BUSY)return last;
   }
  }
  return last;
 }
 static long measureDetailed(int port,String configured,boolean multiple,boolean strictTarget)throws InterruptedException {
  clearTarget();if(port<=0)return UNKNOWN;
  long last=UNKNOWN;
  try(HttpsLatency.Session probe=new HttpsLatency.Session(port)){
   for(String endpoint:endpoints(configured,strictTarget)){
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    last=probe.measure(endpoint,multiple?3:1);
    if(last>0)TARGET.set(targetName(endpoint));
    if(last>0||last==UNKNOWN||last==HttpsLatency.BUSY)return last;
   }
  }
  return last;
 }
}
