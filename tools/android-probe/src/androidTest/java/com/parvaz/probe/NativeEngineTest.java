package com.parvaz.probe;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.parvaz.tunnel.core.ExternalCore;
import com.parvaz.tunnel.model.Profile;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class NativeEngineTest {
 private void fixtureDiagnostics(Context context,Profile profile)throws Exception {
  boolean mihomo=profile.protocol.equals("full-clash");
  java.io.File directory=new java.io.File(context.getNoBackupFilesDir(),"fixture-diagnostics");directory.mkdirs();
  String executable=new java.io.File(context.getApplicationInfo().nativeLibraryDir,mihomo?"libmihomo.so":"libsingbox.so").toString();
  org.json.JSONObject config=com.parvaz.tunnel.config.EngineConfig.build(profile,19080,19081,"fixture","fixture-only-password");if(mihomo)config.put("log-level","debug");else config.put("log",new org.json.JSONObject().put("level","debug"));
  Process process=new ProcessBuilder(mihomo?new String[]{executable,"-d",directory.toString(),"-f","-"}:new String[]{executable,"run","-D",directory.toString(),"-c","stdin"}).redirectErrorStream(true).start();
  java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
  Thread reader=new Thread(()->{try(java.io.InputStream in=process.getInputStream()){byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1){if(output.size()<32768)output.write(buffer,0,n);}}catch(Exception ignored){}});reader.setDaemon(true);reader.start();
  try(java.io.OutputStream input=process.getOutputStream()){input.write(com.parvaz.tunnel.config.EngineConfig.serialize(config,profile.protocol).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException ignored){}
  if(!process.waitFor(8,java.util.concurrent.TimeUnit.SECONDS))process.destroyForcibly();reader.join(1500);
  android.util.Log.i("ParvazProbe","FIXTURE_DIAGNOSTICS "+output.toString("UTF-8"));
 }
 @Test public void singboxStartsAuthenticatesAndStops()throws Exception {assertRuntime("full-singbox");}
 @Test public void mihomoStartsAuthenticatesAndStops()throws Exception {assertRuntime("full-clash");}
 private java.net.Socket authenticated(ExternalCore core)throws Exception {
  java.net.Socket socket=new java.net.Socket("127.0.0.1",core.port);socket.setSoTimeout(2500);
  java.io.DataInputStream in=new java.io.DataInputStream(socket.getInputStream());java.io.OutputStream out=socket.getOutputStream();out.write(new byte[]{5,1,2});assertEquals(5,in.read());assertEquals(2,in.read());
  byte[] user=core.username.getBytes("UTF-8"),pass=core.password.getBytes("UTF-8");out.write(1);out.write(user.length);out.write(user);out.write(pass.length);out.write(pass);assertEquals(1,in.read());assertEquals(0,in.read());return socket;
 }
 private int readAddress(java.net.Socket socket)throws Exception {
  java.io.DataInputStream in=new java.io.DataInputStream(socket.getInputStream());assertEquals(5,in.read());assertEquals(0,in.read());in.read();int type=in.read();int count=type==1?4:type==4?16:in.read();byte[] address=new byte[count];in.readFully(address);return in.readUnsignedShort();
 }
 private void exchangeTcp(ExternalCore core)throws Exception {
  try(java.net.ServerSocket target=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){
   target.setSoTimeout(3000);
   Thread echo=new Thread(()->{try(java.net.Socket peer=target.accept()){peer.setSoTimeout(3000);if(peer.getInputStream().read()!=1)throw new java.io.IOException("Fixture request");peer.getOutputStream().write(new byte[]{79,75});}catch(Exception ignored){}});echo.setDaemon(true);echo.start();
   try(java.net.Socket socket=authenticated(core)){
    int port=target.getLocalPort();socket.getOutputStream().write(new byte[]{5,1,0,1,127,0,0,1,(byte)(port>>8),(byte)port});readAddress(socket);socket.getOutputStream().write(1);assertEquals(79,socket.getInputStream().read());assertEquals(75,socket.getInputStream().read());
   }finally{echo.join(3500);}
  }
 }
 private void exchangeUdp(ExternalCore core)throws Exception {
  try(java.net.DatagramSocket target=new java.net.DatagramSocket(0,java.net.InetAddress.getByName("127.0.0.1"))){
   target.setSoTimeout(3500);
   Thread echo=new Thread(()->{try{byte[] bytes=new byte[64];java.net.DatagramPacket packet=new java.net.DatagramPacket(bytes,bytes.length);target.receive(packet);target.send(packet);}catch(Exception ignored){}});echo.setDaemon(true);echo.start();
   try(java.net.Socket control=authenticated(core);java.net.DatagramSocket udp=new java.net.DatagramSocket()){
    control.getOutputStream().write(new byte[]{5,3,0,1,0,0,0,0,0,0});int relay=readAddress(control),port=target.getLocalPort();udp.setSoTimeout(3500);
    byte[] bytes=new byte[]{0,0,0,1,127,0,0,1,(byte)(port>>8),(byte)port,85,68,80};udp.send(new java.net.DatagramPacket(bytes,bytes.length,java.net.InetAddress.getByName("127.0.0.1"),relay));
    java.net.DatagramPacket reply=new java.net.DatagramPacket(new byte[128],128);udp.receive(reply);int n=reply.getLength();assertTrue(n>=3);assertEquals(85,reply.getData()[n-3]);assertEquals(68,reply.getData()[n-2]);assertEquals(80,reply.getData()[n-1]);
   }finally{echo.join(4000);}
  }
 }
 private String asset(Context context,String name)throws Exception {
  try(java.io.InputStream in=context.getAssets().open(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return out.toString("UTF-8");}
 }
 @Test public void hysteria2AndroidTcpAndUdp()throws Exception {assertQuic("hysteria2");}
 @Test public void tuicAndroidTcpAndUdp()throws Exception {assertQuic("tuic");}
 private void assertQuic(String protocol)throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();int port;
  try(java.net.DatagramSocket reserve=new java.net.DatagramSocket(0,java.net.InetAddress.getByName("127.0.0.1"))){port=reserve.getLocalPort();}
  org.json.JSONObject user=new org.json.JSONObject().put("password","integration-only-secret");
  if(protocol.equals("tuic"))user.put("uuid","11111111-1111-4111-8111-111111111111");
  org.json.JSONObject tls=new org.json.JSONObject().put("enabled",true).put("alpn",new org.json.JSONArray().put("h3")).put("certificate",new org.json.JSONArray().put(asset(context,"fixture-cert.pem"))).put("key",new org.json.JSONArray().put(asset(context,"fixture-key.pem")));
  org.json.JSONObject inbound=new org.json.JSONObject().put("type",protocol).put("listen","127.0.0.1").put("listen_port",port).put("users",new org.json.JSONArray().put(user)).put("tls",tls);
  String config=new org.json.JSONObject().put("log",new org.json.JSONObject().put("disabled",true)).put("inbounds",new org.json.JSONArray().put(inbound)).put("outbounds",new org.json.JSONArray().put(new org.json.JSONObject().put("type","direct"))).toString();
  java.io.File executable=new java.io.File(context.getApplicationInfo().nativeLibraryDir,"libsingbox.so");
  Process server=new ProcessBuilder(executable.toString(),"run","-D",context.getNoBackupFilesDir().toString(),"-c","stdin").redirectErrorStream(true).start();
  Thread drain=new Thread(()->{try(java.io.InputStream in=server.getInputStream()){byte[] bytes=new byte[4096];while(in.read(bytes)!=-1){}}catch(Exception ignored){}});drain.setDaemon(true);drain.start();ExternalCore client=null;
  try{
   try(java.io.OutputStream out=server.getOutputStream()){out.write(config.getBytes("UTF-8"));}
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);boolean ready=false;
   while(System.nanoTime()<deadline){
    assertTrue("Fixture QUIC server exited",server.isAlive());
    try(java.net.DatagramSocket check=new java.net.DatagramSocket(null)){check.bind(new java.net.InetSocketAddress("127.0.0.1",port));}
    catch(java.net.BindException bound){ready=true;break;}
    Thread.sleep(30);
   }
   assertTrue("Fixture QUIC server did not bind",ready);
   Profile p=new Profile();p.protocol=protocol;p.address="127.0.0.1";p.port=port;p.uuid=protocol.equals("tuic")?"11111111-1111-4111-8111-111111111111":"integration-only-secret";p.quicKey="integration-only-secret";p.sni="localhost";p.alpn="h3";p.headerType="native";
   // This self-signed emulator fixture tests transport only. Host fixtures separately
   // test strict certificate verification and wrong-SNI rejection; app defaults stay strict.
   p.allowInsecure=true;
   client=ExternalCore.start(context,p,null);exchangeTcp(client);exchangeUdp(client);
   server.destroy();assertTrue(server.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));
   boolean blocked=false;try{exchangeTcp(client);}catch(java.io.IOException|AssertionError expected){blocked=true;}assertTrue("Unexpected direct fallback",blocked);
   android.util.Log.i("ParvazProbe","ANDROID_QUIC_OK "+protocol+" TCP UDP server-down-blocked SDK="+android.os.Build.VERSION.SDK_INT);
  }finally{if(client!=null)client.close();server.destroyForcibly();server.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);}
 }
 private void assertRuntime(String kind)throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
   Profile p=new Profile();p.protocol=kind;p.rawJson=kind.equals("full-singbox")?"{\"outbounds\":[{\"type\":\"direct\"}]}":"{\"proxies\":[],\"rules\":[\"MATCH,DIRECT\"]}";
   ExternalCore core;
   try{core=ExternalCore.start(context,p,null);}catch(Exception failure){fixtureDiagnostics(context,p);throw failure;}
   try{
    assertTrue(core.isRunning());exchangeTcp(core);
    try(java.net.Socket socket=new java.net.Socket("127.0.0.1",core.port)){
     socket.setSoTimeout(2000);socket.getOutputStream().write(new byte[]{5,1,0});
     int version=socket.getInputStream().read(),method=socket.getInputStream().read();
     assertFalse("Loopback proxy accepted unauthenticated access",version==5&&method==0);
    }
   }finally{core.close();}assertFalse(core.isRunning());
   android.util.Log.i("ParvazProbe","NATIVE_ENGINE_OK "+kind+" SDK="+android.os.Build.VERSION.SDK_INT);
 }
}
