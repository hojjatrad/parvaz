package com.parvaz.tunnel.core;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Fixture connections only: no public endpoint or actual server credentials. */
public class StartupWarmupTest {
 static class Connection extends HttpURLConnection {
  final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),disconnected=new CountDownLatch(1);
  boolean blocking,releaseOnDisconnect=true;int status=204;
  Connection()throws Exception{super(new URL("https://fixture.invalid/generate_204"));}
  @Override public void connect(){}
  @Override public boolean usingProxy(){return true;}
  @Override public void disconnect(){disconnected.countDown();if(releaseOnDisconnect)release.countDown();}
  @Override public int getResponseCode()throws IOException {
   entered.countDown();
   while(blocking&&release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}
   return status;
  }
  @Override public InputStream getInputStream(){throw new AssertionError("Warmup must not download a body");}
 }
 @Test public void oneHeadHasTimeoutsNoRedirectOrBody()throws Exception {
  Connection connection=new Connection();CountDownLatch reported=new CountDownLatch(1);AtomicBoolean confirmed=new AtomicBoolean();AtomicInteger opened=new AtomicInteger();
  try(StartupWarmup warmup=new StartupWarmup(url->{opened.incrementAndGet();return connection;},(ok,ms)->{confirmed.set(ok);reported.countDown();})){
   warmup.start(connection.getURL().toString());assertTrue(reported.await(3,TimeUnit.SECONDS));
   assertTrue(confirmed.get());assertEquals(1,opened.get());assertEquals("HEAD",connection.getRequestMethod());
   assertEquals(StartupWarmup.CONNECT_TIMEOUT_MS,connection.getConnectTimeout());assertEquals(StartupWarmup.READ_TIMEOUT_MS,connection.getReadTimeout());
   assertFalse(connection.getInstanceFollowRedirects());assertFalse(connection.getUseCaches());assertEquals(0,connection.disconnected.getCount());
  }
 }
 @Test public void blockedRequestDoesNotBlockStartAndCancelSuppressesResult()throws Exception {
  Connection connection=new Connection();connection.blocking=true;AtomicInteger reports=new AtomicInteger();
  try(StartupWarmup warmup=new StartupWarmup(url->connection,(ok,ms)->reports.incrementAndGet())){
   warmup.start("https://fixture.invalid/check");assertTrue(connection.entered.await(3,TimeUnit.SECONDS));
   // start() already returned although the fake network has not responded.
   assertEquals(1,connection.release.getCount());warmup.cancel();
   assertTrue(connection.disconnected.await(3,TimeUnit.SECONDS));assertEquals(0,reports.get());
  }
 }
 @Test public void rapidRestartsDoNotAccumulateBlockedRequests()throws Exception {
  Connection connection=new Connection();connection.blocking=true;connection.releaseOnDisconnect=false;AtomicInteger opened=new AtomicInteger();
  try(StartupWarmup warmup=new StartupWarmup(url->{opened.incrementAndGet();return connection;},(ok,ms)->{})){
   warmup.start("https://fixture.invalid/check");assertTrue(connection.entered.await(3,TimeUnit.SECONDS));
   for(int i=0;i<20;i++)warmup.start("https://fixture.invalid/check");
   assertEquals(1,opened.get());
  }finally{connection.release.countDown();}
 }
 @Test public void watchdogDisconnectsEvenWithoutResponse()throws Exception {
  Connection connection=new Connection();connection.blocking=true;AtomicInteger reports=new AtomicInteger();CountDownLatch reported=new CountDownLatch(1);
  try(StartupWarmup warmup=new StartupWarmup(url->connection,(ok,ms)->{assertFalse(ok);reports.incrementAndGet();reported.countDown();},100)){
   warmup.start("https://fixture.invalid/check");assertTrue(connection.entered.await(3,TimeUnit.SECONDS));
   assertTrue(connection.disconnected.await(3,TimeUnit.SECONDS));
   assertTrue(reported.await(3,TimeUnit.SECONDS));assertEquals(1,reports.get());
  }
 }
 @Test public void redirectDoesNotCountAsConfirmedOrOpenAnotherConnection()throws Exception {
  Connection connection=new Connection();connection.status=302;CountDownLatch result=new CountDownLatch(1);AtomicInteger opened=new AtomicInteger();AtomicBoolean confirmed=new AtomicBoolean(true);
  try(StartupWarmup warmup=new StartupWarmup(url->{opened.incrementAndGet();return connection;},(ok,ms)->{confirmed.set(ok);result.countDown();})){
   warmup.start("https://fixture.invalid/check");assertTrue(result.await(3,TimeUnit.SECONDS));assertFalse(confirmed.get());assertEquals(1,opened.get());
  }
 }
 @Test public void realConnectionFactoryPassesExplicitProxyAndKeepsDefaultTls()throws Exception {
  // usingProxy() on a not-yet-connected JDK HTTPS connection may return false.
  // Observe the actual URL.openConnection(Proxy) argument instead of guessing.
  AtomicReference<Proxy> selected=new AtomicReference<>();
  HttpURLConnection original=(HttpURLConnection)new URL("https://fixture.invalid/check").openConnection();
  URL observed=new URL(null,"https://fixture.invalid/check",new URLStreamHandler(){
   @Override protected URLConnection openConnection(URL url){throw new AssertionError("System/direct route used");}
   @Override protected URLConnection openConnection(URL url,Proxy proxy){selected.set(proxy);return original;}
  });
  HttpURLConnection raw=StartupWarmup.tunnelConnection(observed);
  try{
   assertNotNull(selected.get());assertEquals(Proxy.Type.HTTP,selected.get().type());
   InetSocketAddress address=(InetSocketAddress)selected.get().address();
   assertEquals("127.0.0.1",address.getHostString());assertEquals(com.parvaz.tunnel.config.XrayConfigBuilder.HTTP_PORT,address.getPort());
   assertTrue(raw instanceof javax.net.ssl.HttpsURLConnection);
   javax.net.ssl.HttpsURLConnection https=(javax.net.ssl.HttpsURLConnection)raw;
   assertSame(javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier(),https.getHostnameVerifier());
   assertSame(javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory(),https.getSSLSocketFactory());
  }finally{raw.disconnect();}
 }
 @Test public void invalidEndpointReportsFailureInsteadOfAbandoningRetryOwner(){
  AtomicInteger reports=new AtomicInteger();try(StartupWarmup warmup=new StartupWarmup(url->{throw new AssertionError("Invalid URL opened");},(ok,ms)->{assertFalse(ok);reports.incrementAndGet();})){
   warmup.start("http://fixture.invalid/");assertEquals(1,reports.get());
  }
 }
 @Test public void busyWorkerReportsFailureWithoutQueueingOrOpeningDirectly()throws Exception {
  Connection connection=new Connection();connection.blocking=true;connection.releaseOnDisconnect=false;AtomicInteger reports=new AtomicInteger(),opened=new AtomicInteger();
  try(StartupWarmup warmup=new StartupWarmup(url->{opened.incrementAndGet();return connection;},(ok,ms)->{assertFalse(ok);reports.incrementAndGet();})){
   warmup.start("https://fixture.invalid/first");assertTrue(connection.entered.await(3,TimeUnit.SECONDS));
   warmup.start("https://fixture.invalid/second");assertEquals(1,reports.get());assertEquals(1,opened.get());
  }finally{connection.release.countDown();}
 }
 @Test public void deadlineLateSuccessCannotTurnGreen()throws Exception {
  Connection connection=new Connection();connection.blocking=true;connection.releaseOnDisconnect=false;java.util.List<Boolean> results=new java.util.concurrent.CopyOnWriteArrayList<>();CountDownLatch reported=new CountDownLatch(1);
  try(StartupWarmup warmup=new StartupWarmup(url->connection,(ok,ms)->{results.add(ok);reported.countDown();},100)){
   warmup.start("https://fixture.invalid/check");assertTrue(connection.entered.await(3,TimeUnit.SECONDS));assertTrue(reported.await(3,TimeUnit.SECONDS));
   connection.release.countDown();warmup.close();assertEquals(java.util.Collections.singletonList(false),results);
  }finally{connection.release.countDown();}
 }
 @Test public void pinnedFactoryUsesOnlySessionPortAndDefaultTls()throws Exception {
  AtomicReference<Proxy> selected=new AtomicReference<>();HttpURLConnection original=(HttpURLConnection)new URL("https://fixture.invalid/check").openConnection();
  URL observed=new URL(null,"https://fixture.invalid/check",new URLStreamHandler(){
   @Override protected URLConnection openConnection(URL url){throw new AssertionError("Direct fallback");}
   @Override protected URLConnection openConnection(URL url,Proxy proxy){selected.set(proxy);return original;}
  });
  HttpURLConnection result=StartupWarmup.pinnedConnection(observed,23111);try{
   assertSame(original,result);assertEquals(Proxy.Type.HTTP,selected.get().type());InetSocketAddress address=(InetSocketAddress)selected.get().address();assertEquals("127.0.0.1",address.getHostString());assertEquals(23111,address.getPort());
   javax.net.ssl.HttpsURLConnection https=(javax.net.ssl.HttpsURLConnection)result;assertSame(javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier(),https.getHostnameVerifier());assertSame(javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory(),https.getSSLSocketFactory());
  }finally{result.disconnect();}
 }
 @Test public void missingPinnedPortCannotFallBackToNormalProxyOrSystem()throws Exception {
  for(int port:new int[]{-1,0,65536})try{StartupWarmup.pinnedConnection(new URL("https://fixture.invalid/"),port);fail("Missing route accepted");}catch(IOException expected){}
 }
 @Test public void plaintextCredentialAndMalformedEndpointsAreSkipped(){
  for(String value:new String[]{"http://fixture.invalid/","https://user:secret@fixture.invalid/","https://fixture.invalid/#fragment","file:///tmp/test","invalid",""})assertNull(StartupWarmup.endpoint(value));
  assertNotNull(StartupWarmup.endpoint("https://fixture.invalid/generate_204"));
 }
}
