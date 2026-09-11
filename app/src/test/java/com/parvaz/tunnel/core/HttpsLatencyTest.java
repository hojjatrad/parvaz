package com.parvaz.tunnel.core;
import android.app.Application;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class HttpsLatencyTest {
 private MockWebServer target;private Tunnel proxy;private HandshakeCertificates trusted;
 @Before public void setup()throws Exception{
  HeldCertificate cert=new HeldCertificate.Builder().commonName("fixture").addSubjectAlternativeName("localhost").build();
  trusted=new HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate()).build();
  HandshakeCertificates server=new HandshakeCertificates.Builder().heldCertificate(cert).build();
  target=new MockWebServer();target.useHttps(server.sslSocketFactory(),false);target.start();proxy=new Tunnel(target.getPort());
 }
 @After public void cleanup()throws Exception{proxy.close();target.shutdown();}
 private String url(){return "https://localhost:"+target.getPort()+"/generate_204";}
 private HttpsLatency.Session session(){return new HttpsLatency.Session(proxy.port(),new OkHttpClient.Builder().sslSocketFactory(trusted.sslSocketFactory(),trusted.trustManager()));}
 private MockResponse reply(long delay){return new MockResponse().setResponseCode(204).setHeadersDelay(delay,TimeUnit.MILLISECONDS);}
 @Test public void proxySetupDelayIsNotReportedAsRequestRtt()throws Exception{
  proxy.delayMs=1000;target.enqueue(reply(80));long began=System.nanoTime();
  try(HttpsLatency.Session s=session()){long ms=s.measure(url(),1);long total=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began);assertTrue("Missing real response delay: "+ms,ms>=60);assertTrue("Setup leaked into RTT: "+ms,ms<700);assertTrue(total>=1000);}
 }
 @Test public void medianIsNotWorstColdSampleOrMinimum()throws Exception{
  assertEquals(90,HttpsLatency.median(new long[]{1200,90,40}));target.enqueue(reply(30));target.enqueue(reply(500));target.enqueue(reply(60));
  try(HttpsLatency.Session s=session()){long ms=s.measure(url(),3);assertTrue(ms>=35&&ms<450);assertEquals(3,target.getRequestCount());}
 }
 @Test public void defaultTrustDoesNotAcceptFixtureCertificate()throws Exception{target.enqueue(reply(0));try(HttpsLatency.Session s=new HttpsLatency.Session(proxy.port())){assertEquals(HttpsLatency.TLS_ERROR,s.measure(url(),1));}}
 @Test public void wrongHostnameCannotPassEvenWithTrustedIssuer()throws Exception{target.enqueue(reply(0));try(HttpsLatency.Session s=session()){assertEquals(HttpsLatency.TLS_ERROR,s.measure("https://wrong-fixture.invalid:"+target.getPort()+"/",1));}}
 @Test public void redirectsAreNotFollowedOrCountedAsSuccess()throws Exception{target.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",url()));try(HttpsLatency.Session s=session()){assertEquals(HttpsLatency.HTTP_ERROR,s.measure(url(),1));assertEquals(1,target.getRequestCount());}}
 @Test public void httpErrorIsNotAWorkingLatency()throws Exception{target.enqueue(new MockResponse().setResponseCode(503));try(HttpsLatency.Session s=session()){assertEquals(HttpsLatency.HTTP_ERROR,s.measure(url(),3));assertEquals(1,target.getRequestCount());}}
 @Test public void deadProxyNeverFallsBackToDirectTarget()throws Exception{proxy.close();target.enqueue(reply(0));try(HttpsLatency.Session s=session()){assertEquals(HttpsLatency.NETWORK_ERROR,s.measure(url(),1));assertEquals(0,target.getRequestCount());}}
 @Test public void invalidUrlsAndUnpinnedPortsAreUnknown()throws Exception{try(HttpsLatency.Session s=session()){for(String u:new String[]{"http://localhost/","https://a:b@localhost/","https://localhost/#fragment","nonsense"})assertEquals(HttpsLatency.UNKNOWN,s.measure(u,1));}try(HttpsLatency.Session s=new HttpsLatency.Session(0)){assertEquals(HttpsLatency.UNKNOWN,s.measure(url(),1));}}
 @Test public void cancellationClosesInFlightCallWithoutNumericResult()throws Exception{
  target.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));ExecutorService worker=Executors.newSingleThreadExecutor();
  try(HttpsLatency.Session s=session()){
   Future<Long> f=worker.submit(()->s.measure(url(),1));assertNotNull(target.takeRequest(5,TimeUnit.SECONDS));s.cancel();assertEquals(Long.valueOf(HttpsLatency.UNKNOWN),f.get(3,TimeUnit.SECONDS));
  }finally{worker.shutdownNow();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
 }
 @Test public void typedFailuresStayNonNumeric(){assertEquals(HttpsLatency.TIMEOUT,HttpsLatency.failure(new SocketTimeoutException()));assertEquals(LatencyResult.TIMEOUT,LatencyResult.measured(-20));assertEquals(LatencyResult.TLS_ERROR,LatencyResult.measured(-21));assertEquals(LatencyResult.HTTP_ERROR,LatencyResult.measured(-22));assertEquals(LatencyResult.NETWORK_ERROR,LatencyResult.measured(-23));assertEquals(LatencyResult.BUSY,LatencyResult.measured(-24));assertEquals(VerifiedProbe.UNKNOWN,VerifiedProbe.automatic(-24));}
 private static final class Tunnel implements AutoCloseable {
  final ServerSocket server;final int targetPort;final Set<Socket> sockets=ConcurrentHashMap.newKeySet();volatile boolean closed;volatile long delayMs;final Thread accept;
  Tunnel(int targetPort)throws IOException{this.targetPort=targetPort;server=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));accept=new Thread(()->{while(!closed)try{Socket s=server.accept();sockets.add(s);Thread t=new Thread(()->connect(s));t.setDaemon(true);t.start();}catch(IOException done){break;}});accept.setDaemon(true);accept.start();}
  int port(){return server.getLocalPort();}
  private void connect(Socket down){
   try{
    down.setSoTimeout(10000);InputStream in=down.getInputStream();int matched=0,count=0;byte[] end={13,10,13,10};while(matched<4){int b=in.read();if(b<0||++count>8192)throw new IOException();matched=b==end[matched]?matched+1:0;}
    if(delayMs>0)Thread.sleep(delayMs);if(closed)return;
    Socket up=new Socket("127.0.0.1",targetPort);sockets.add(up);down.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("US-ASCII"));down.getOutputStream().flush();
    Thread back=new Thread(()->pipe(up,down));back.setDaemon(true);back.start();pipe(down,up);
   }catch(Exception ignored){}finally{try{down.close();}catch(IOException ignored){}sockets.remove(down);}
  }
  private void pipe(Socket from,Socket to){try{byte[] b=new byte[8192];int n;while((n=from.getInputStream().read(b))!=-1){to.getOutputStream().write(b,0,n);to.getOutputStream().flush();}}catch(IOException ignored){}finally{try{from.close();to.close();}catch(IOException ignored){}sockets.remove(from);sockets.remove(to);}}
  public void close()throws IOException{closed=true;server.close();for(Socket s:sockets)s.close();sockets.clear();}
 }
}
