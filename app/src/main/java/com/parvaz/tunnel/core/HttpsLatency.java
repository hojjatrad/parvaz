package com.parvaz.tunnel.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.*;
import okhttp3.*;

/** Real HTTPS request/header round-trip through one explicitly pinned HTTP proxy.
 * Connection, proxy negotiation and TLS occur BEFORE the timing window. No TCP-only
 * success, direct fallback, redirects, permissive trust or best-of sampling. */
public final class HttpsLatency {
 public static final long FAILED=-1,UNKNOWN=-2,TIMEOUT=-20,TLS_ERROR=-21,HTTP_ERROR=-22,NETWORK_ERROR=-23,BUSY=-24;
 private static final Semaphore SLOTS=new Semaphore(6);
 private HttpsLatency(){}
 static final class Timing extends okhttp3.EventListener {
  long began=-1,ended=-1;
  @Override public void requestHeadersStart(Call call){began=System.nanoTime();ended=-1;}
  @Override public void responseHeadersEnd(Call call,Response response){ended=System.nanoTime();}
  long millis(){return began>=0&&ended>=began?Math.max(1,TimeUnit.NANOSECONDS.toMillis(ended-began)):UNKNOWN;}
 }
 static long failure(IOException e){
  if(e instanceof SSLException||e instanceof SSLPeerUnverifiedException)return TLS_ERROR;
  if(e instanceof InterruptedIOException)return TIMEOUT;
  return NETWORK_ERROR;
 }
 static long median(long[] values){long[] copy=values.clone();Arrays.sort(copy);return copy[copy.length/2];}
 public static final class Session implements AutoCloseable {
  private final int port;
  private final OkHttpClient client;
  private final AtomicReference<Call> active=new AtomicReference<>();
  private volatile boolean closed;
  public Session(int port){this(port,new OkHttpClient.Builder());}
  // Package-private injection is for loopback trust fixtures only, never user settings.
  Session(int port,OkHttpClient.Builder builder){
   this.port=port;
   client=builder.proxy(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",Math.max(1,port))))
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
    .protocols(Collections.singletonList(Protocol.HTTP_1_1))
    .connectTimeout(5,TimeUnit.SECONDS).readTimeout(6,TimeUnit.SECONDS).writeTimeout(5,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS)
    .connectionPool(new ConnectionPool(1,10,TimeUnit.SECONDS)).build();
  }
  public long measure(String configured,int samples)throws InterruptedException {
   if(samples!=1&&samples!=3)throw new IllegalArgumentException("Expected one or three samples");
   if(closed||port<1||port>65535)return UNKNOWN;
   if(!SLOTS.tryAcquire())return BUSY;
   try{
    HttpUrl endpoint;
    try{endpoint=HttpUrl.get(configured);if(!endpoint.isHttps()||!endpoint.username().isEmpty()||!endpoint.password().isEmpty()||endpoint.fragment()!=null)return UNKNOWN;}
    catch(IllegalArgumentException|NullPointerException invalid){return UNKNOWN;}
    long[] results=new long[samples];
    for(int i=0;i<samples;i++){
     if(Thread.currentThread().isInterrupted())throw new InterruptedException();
     if(closed)return UNKNOWN;
     Timing timing=new Timing();Call call=client.newBuilder().eventListener(timing).build().newCall(new Request.Builder().url(endpoint).head().header("Cache-Control","no-cache").build());
     active.set(call);if(closed){call.cancel();active.compareAndSet(call,null);return UNKNOWN;}
     try(Response response=call.execute()){
      if(closed)return UNKNOWN;
      if(response.code()<200||response.code()>=300)return HTTP_ERROR;
      results[i]=timing.millis();if(results[i]<=0)return UNKNOWN;
     }catch(IOException failure){if(Thread.currentThread().isInterrupted())throw new InterruptedException();return closed?UNKNOWN:failure(failure);}
     finally{active.compareAndSet(call,null);}
    }
    return median(results);
   }finally{SLOTS.release();}
  }
  public void cancel(){closed=true;Call call=active.getAndSet(null);if(call!=null)call.cancel();}
  @Override public void close(){cancel();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
 }
}
