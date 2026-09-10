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
 private void awaitCapacity(java.util.concurrent.Semaphore capacity,int expected)throws Exception {
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
  while(capacity.availablePermits()!=expected&&System.nanoTime()<deadline)Thread.sleep(20);
  assertEquals("Capacity must reflect completed child exit/cleanup",expected,capacity.availablePermits());
 }
 @org.junit.After public void awaitNativeCleanupBetweenTests()throws Exception {awaitCapacity((java.util.concurrent.Semaphore)coreField(null,"CAPACITY"),3);}
 @Test public void singboxStartsAuthenticatesAndStops()throws Exception {assertRuntime("full-singbox");}
 @Test public void mihomoStartsAuthenticatesAndStops()throws Exception {assertRuntime("full-clash");}
 private java.net.Socket authenticated(ExternalCore core)throws Exception {
  return authenticated(core.port,core.username,core.password);
 }
 private java.net.Socket authenticated(int port,String username,String password)throws Exception {
  java.net.Socket socket=new java.net.Socket("127.0.0.1",port);socket.setSoTimeout(2500);
  java.io.DataInputStream in=new java.io.DataInputStream(socket.getInputStream());java.io.OutputStream out=socket.getOutputStream();out.write(new byte[]{5,1,2});assertEquals(5,in.read());assertEquals(2,in.read());
  byte[] user=username.getBytes("UTF-8"),pass=password.getBytes("UTF-8");out.write(1);out.write(user.length);out.write(user);out.write(pass.length);out.write(pass);assertEquals(1,in.read());assertEquals(0,in.read());return socket;
 }
 private int readAddress(java.net.Socket socket)throws Exception {
  java.io.DataInputStream in=new java.io.DataInputStream(socket.getInputStream());assertEquals(5,in.read());assertEquals(0,in.read());in.read();int type=in.read();int count=type==1?4:type==4?16:in.read();byte[] address=new byte[count];in.readFully(address);return in.readUnsignedShort();
 }
 private void exchangeTcp(ExternalCore core)throws Exception {
  exchangeTcp(core.port,core.username,core.password);
 }
 private void exchangeTcp(int proxyPort,String username,String password)throws Exception {
  try(java.net.ServerSocket target=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){
   target.setSoTimeout(3000);
   Thread echo=new Thread(()->{try(java.net.Socket peer=target.accept()){peer.setSoTimeout(3000);if(peer.getInputStream().read()!=1)throw new java.io.IOException("Fixture request");peer.getOutputStream().write(new byte[]{79,75});}catch(Exception ignored){}});echo.setDaemon(true);echo.start();
   try(java.net.Socket socket=authenticated(proxyPort,username,password)){
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
   // Only the helper debug APK trusts the disposable CA via Android's TrustManager.
   // Production trust configuration is unchanged; no TLS bypass is used here.
   p.allowInsecure=false;
   client=ExternalCore.start(context,p,null);exchangeTcp(client);exchangeUdp(client);
   Profile wrongName=Profile.fromJson(p.toJson());wrongName.sni="not-the-fixture.invalid";
   try(ExternalCore rejected=ExternalCore.start(context,wrongName,null)){
    boolean blocked=false;try{exchangeTcp(rejected);}catch(java.io.IOException|AssertionError expected){blocked=true;}
    assertTrue("Wrong SNI unexpectedly passed Android TLS verification",blocked);
   }
   server.destroy();assertTrue(server.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));
   boolean blocked=false;try{exchangeTcp(client);}catch(java.io.IOException|AssertionError expected){blocked=true;}assertTrue("Unexpected direct fallback",blocked);
   android.util.Log.i("ParvazProbe","ANDROID_QUIC_OK "+protocol+" strict-TLS wrong-SNI-blocked TCP UDP server-down-TCP-blocked SDK="+android.os.Build.VERSION.SDK_INT);
  }finally{if(client!=null)client.close();server.destroyForcibly();server.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);}
 }
 @Test public void singboxSurvivesCallerThreadExit()throws Exception {assertCallerThreadExit("full-singbox");}
 @Test public void mihomoSurvivesCallerThreadExit()throws Exception {assertCallerThreadExit("full-clash");}
 private Profile directProfile(String kind){
  Profile p=new Profile();p.protocol=kind;
  p.rawJson=kind.equals("full-singbox")?"{\"outbounds\":[{\"type\":\"direct\"}]}":"{\"proxies\":[],\"rules\":[\"MATCH,DIRECT\"]}";return p;
 }
 private void assertCallerThreadExit(String kind)throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  java.util.concurrent.atomic.AtomicReference<ExternalCore> result=new java.util.concurrent.atomic.AtomicReference<>();
  java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
  Thread caller=new Thread(()->{try{result.set(ExternalCore.start(context,directProfile(kind),null));}catch(Throwable e){error.set(e);}},"fixture-short-lived-caller");
  caller.start();caller.join(35000);
  ExternalCore core=result.get();
  try{
   assertFalse("Caller did not finish",caller.isAlive());
   if(error.get()!=null)throw new AssertionError("Caller startup failed",error.get());
   assertNotNull(core);
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
   while(System.nanoTime()<deadline){assertTrue("Engine died when its calling thread exited",core.isRunning());Thread.sleep(25);}
   exchangeTcp(core);
   android.util.Log.i("ParvazProbe","CALLER_THREAD_EXIT_OK "+kind+" SDK="+android.os.Build.VERSION.SDK_INT);
  }finally{if(core!=null)core.close();if(caller.isAlive())caller.interrupt();}
 }
 @Test public void singboxClosesListenerOnOwnerDeath()throws Exception {assertOwnerDeath("full-singbox");}
 @Test public void mihomoClosesListenerOnOwnerDeath()throws Exception {assertOwnerDeath("full-clash");}
 private void assertOwnerDeath(String kind)throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  android.content.Intent intent=new android.content.Intent(context,NativeOwnerService.class);
  java.util.concurrent.ArrayBlockingQueue<android.os.Messenger> owners=new java.util.concurrent.ArrayBlockingQueue<>(2);
  java.util.concurrent.ArrayBlockingQueue<android.os.Message> replies=new java.util.concurrent.ArrayBlockingQueue<>(2);
  java.util.concurrent.CountDownLatch lost=new java.util.concurrent.CountDownLatch(1);
  android.content.ServiceConnection connection=new android.content.ServiceConnection(){
   public void onServiceConnected(android.content.ComponentName name,android.os.IBinder binder){owners.offer(new android.os.Messenger(binder));}
   public void onServiceDisconnected(android.content.ComponentName name){lost.countDown();}
   public void onBindingDied(android.content.ComponentName name){lost.countDown();}
  };
  android.os.Messenger replyTo=new android.os.Messenger(new android.os.Handler(android.os.Looper.getMainLooper(),message->{replies.offer(android.os.Message.obtain(message));return true;}));
  boolean bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);
  try{
   assertTrue("Owner bind failed",bound);
   android.os.Messenger owner=owners.poll(10,java.util.concurrent.TimeUnit.SECONDS);assertNotNull("Owner did not connect",owner);
   android.os.Message start=android.os.Message.obtain();start.what=NativeOwnerService.START;start.replyTo=replyTo;
   android.os.Bundle request=new android.os.Bundle();request.putString("kind",kind);start.setData(request);owner.send(start);
   android.os.Message ready=replies.poll(35,java.util.concurrent.TimeUnit.SECONDS);assertNotNull("Owner startup timed out",ready);
   assertEquals("Owner engine startup failed",NativeOwnerService.READY,ready.what);
   android.os.Bundle data=ready.getData();int port=data.getInt("port"),pid=data.getInt("pid");
   assertTrue("Owner must be a different test process",pid>0&&pid!=android.os.Process.myPid());
   exchangeTcp(port,data.getString("username"),data.getString("password"));
   android.os.Message crash=android.os.Message.obtain();crash.what=NativeOwnerService.CRASH;owner.send(crash);
   assertTrue("Owner process death was not observed",lost.await(10,java.util.concurrent.TimeUnit.SECONDS));
   context.unbindService(connection);bound=false;
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);boolean closed=false;
   while(System.nanoTime()<deadline){
    try(java.net.Socket socket=new java.net.Socket()){socket.connect(new java.net.InetSocketAddress("127.0.0.1",port),300);}
    catch(java.net.ConnectException refused){closed=true;break;}
    Thread.sleep(50);
   }
   assertTrue("Native listener survived owner process death",closed);
   try(java.net.ServerSocket replacement=new java.net.ServerSocket()){
    replacement.setReuseAddress(true);replacement.bind(new java.net.InetSocketAddress("127.0.0.1",port));
   }
   android.util.Log.i("ParvazProbe","OWNER_DEATH_LISTENER_CLOSED_OK "+kind+" SDK="+android.os.Build.VERSION.SDK_INT);
  }finally{if(bound)context.unbindService(connection);context.stopService(intent);}
 }
 // These helpers inspect only Parvaz's own class in the disposable probe APK.
 // No hidden Android API, production test hook or private user data is involved.
 private Object coreField(ExternalCore core,String name)throws Exception {
  java.lang.reflect.Field field=ExternalCore.class.getDeclaredField(name);field.setAccessible(true);return field.get(core);
 }
 private java.util.Set<String> sessionDirectories(Context context){
  java.util.Set<String> names=new java.util.HashSet<>();
  java.io.File[] files=context.getNoBackupFilesDir().listFiles();
  assertNotNull("Cannot inspect fixture storage",files);
  for(java.io.File file:files)if(file.getName().startsWith("engine-session-"))names.add(file.getName());
  return names;
 }
 private void awaitCallerFrame(Thread caller,String className,String method)throws Exception {
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
  while(caller.isAlive()&&System.nanoTime()<deadline){
   for(StackTraceElement frame:caller.getStackTrace())if(frame.getClassName().equals(className)&&frame.getMethodName().equals(method))return;
   Thread.sleep(20);
  }
  fail("Caller did not reach controlled wait: "+className+"."+method);
 }
 @Test public void closedSessionRejectsLateLaunch()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  ExternalCore core=new ExternalCore();core.close();
  java.lang.reflect.Method launch=ExternalCore.class.getDeclaredMethod("launch",ProcessBuilder.class);launch.setAccessible(true);
  java.io.File executable=new java.io.File(context.getApplicationInfo().nativeLibraryDir,"libsingbox.so");assertTrue(executable.canExecute());
  try{
   try{
    launch.invoke(core,new ProcessBuilder(executable.toString(),"version").directory(context.getNoBackupFilesDir()));
    fail("A closed session accepted a late launch");
   }catch(java.lang.reflect.InvocationTargetException expected){assertTrue(expected.getCause() instanceof java.io.IOException);}
   assertNull("Closed session acquired a child",coreField(core,"process"));
  }finally{
   // Clean up even if a regression unexpectedly started the harmless version command.
   Process child=(Process)coreField(core,"process");
   if(child!=null){child.destroyForcibly();child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);}
  }
  android.util.Log.i("ParvazProbe","CLOSED_SESSION_LATE_LAUNCH_REJECTED SDK="+android.os.Build.VERSION.SDK_INT);
 }
 @Test public void interruptedQueuedLaunchRestoresResources()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  java.util.Set<String> baseline=sessionDirectories(context);
  java.util.concurrent.ExecutorService launcher=(java.util.concurrent.ExecutorService)coreField(null,"LAUNCHER");
  java.util.concurrent.Semaphore capacity=(java.util.concurrent.Semaphore)coreField(null,"CAPACITY");
  assertEquals(3,capacity.availablePermits());
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.Future<?> blocker=launcher.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  java.util.concurrent.atomic.AtomicReference<ExternalCore> result=new java.util.concurrent.atomic.AtomicReference<>();
  java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
  Thread caller=new Thread(()->{try{result.set(ExternalCore.start(context,directProfile("full-singbox"),null));}catch(Throwable e){error.set(e);}},"fixture-cancel-queued-launch");
  try{
   assertTrue("Launcher blocker not entered",entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
   caller.start();awaitCallerFrame(caller,"java.util.concurrent.FutureTask","get");
   assertEquals("Queued launch should own one slot",2,capacity.availablePermits());
   assertEquals("Queued launch should own one private directory",baseline.size()+1,sessionDirectories(context).size());
   caller.interrupt();caller.join(5000);assertFalse("Cancelled caller is stuck",caller.isAlive());
   assertTrue("Expected interrupted launch",error.get() instanceof InterruptedException);assertNull(result.get());
   assertEquals("Queued cancellation leaked a slot",3,capacity.availablePermits());
   assertEquals("Queued cancellation left private files",baseline,sessionDirectories(context));
   release.countDown();blocker.get(5,java.util.concurrent.TimeUnit.SECONDS);
   launcher.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS); // Drain the cancelled session's pending launch.
   awaitCapacity(capacity,3);assertEquals(3,capacity.availablePermits());assertEquals(baseline,sessionDirectories(context));
   try(ExternalCore next=ExternalCore.start(context,directProfile("full-singbox"),null)){exchangeTcp(next);}
   awaitCapacity(capacity,3);assertEquals(3,capacity.availablePermits());assertEquals(baseline,sessionDirectories(context));
   android.util.Log.i("ParvazProbe","QUEUED_LAUNCH_CANCEL_CLEAN_OK SDK="+android.os.Build.VERSION.SDK_INT);
  }finally{
   caller.interrupt();release.countDown();caller.join(35000);
   ExternalCore unexpected=result.get();if(unexpected!=null)unexpected.close();
   blocker.get(5,java.util.concurrent.TimeUnit.SECONDS);
   launcher.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
  }
 }
 @Test public void interruptedCapacityWaitDoesNotOverRelease()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();java.util.Set<String> baseline=sessionDirectories(context);
  java.util.concurrent.Semaphore capacity=(java.util.concurrent.Semaphore)coreField(null,"CAPACITY");assertEquals(3,capacity.availablePermits());
  java.util.List<ExternalCore> active=new java.util.ArrayList<>();
  java.util.concurrent.atomic.AtomicReference<ExternalCore> result=new java.util.concurrent.atomic.AtomicReference<>();
  java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
  Thread waiter=new Thread(()->{try{result.set(ExternalCore.start(context,directProfile("full-clash"),null));}catch(Throwable e){error.set(e);}},"fixture-cancel-capacity-wait");
  try{
   for(int i=0;i<3;i++)active.add(ExternalCore.start(context,directProfile(i==1?"full-clash":"full-singbox"),null));
   assertEquals(0,capacity.availablePermits());
   waiter.start();awaitCallerFrame(waiter,"java.util.concurrent.Semaphore","tryAcquire");
   assertEquals(baseline.size()+3,sessionDirectories(context).size());
   waiter.interrupt();waiter.join(5000);assertFalse("Capacity waiter is stuck",waiter.isAlive());
   assertTrue("Expected interrupted capacity wait",error.get() instanceof InterruptedException);assertNull(result.get());
   assertEquals("Unacquired slot was incorrectly released",0,capacity.availablePermits());
   for(ExternalCore core:active){assertTrue(core.isRunning());exchangeTcp(core);}
   Process stopped=(Process)coreField(active.get(0),"process");active.get(0).close();active.get(0).close();assertTrue(stopped.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));awaitCapacity(capacity,1);assertEquals("Double close over-released capacity",1,capacity.availablePermits());
   try(ExternalCore replacement=ExternalCore.start(context,directProfile("full-clash"),null)){
    assertEquals(0,capacity.availablePermits());exchangeTcp(replacement);
   }
  }finally{
   waiter.interrupt();for(ExternalCore core:active)core.close();waiter.join(35000);
   ExternalCore unexpected=result.get();if(unexpected!=null)unexpected.close();
  }
  awaitCapacity(capacity,3);assertEquals(3,capacity.availablePermits());assertEquals(baseline,sessionDirectories(context));
  android.util.Log.i("ParvazProbe","CAPACITY_WAIT_CANCEL_NO_OVERRELEASE_OK SDK="+android.os.Build.VERSION.SDK_INT);
 }
 @Test public void repeatedStopsReapChildrenAndRemoveTrustBundles()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();java.util.Set<String> baseline=sessionDirectories(context);
  java.util.concurrent.Semaphore capacity=(java.util.concurrent.Semaphore)coreField(null,"CAPACITY");assertEquals(3,capacity.availablePermits());
  for(int cycle=0;cycle<4;cycle++){
   ExternalCore core=ExternalCore.start(context,directProfile(cycle%2==0?"full-singbox":"full-clash"),null);
   Process child=(Process)coreField(core,"process");java.io.File directory=(java.io.File)coreField(core,"directory");int port=core.port;
   try{
    assertTrue(directory.isDirectory());boolean trustPresent=false;
    java.io.File[] children=directory.listFiles();assertNotNull(children);
    for(java.io.File file:children)if(file.getName().startsWith("trust-")&&new java.io.File(file,"roots.pem").length()>0)trustPresent=true;
    assertTrue("Session trust bundle missing",trustPresent);exchangeTcp(core);
   }finally{core.close();core.close();}
   assertTrue("Native child was not reaped",child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));
   awaitCapacity(capacity,3);assertFalse(core.isRunning());assertNull(core.password);assertFalse("Private session directory remains",directory.exists());
   try(java.net.ServerSocket socket=new java.net.ServerSocket()){
    socket.setReuseAddress(true);socket.bind(new java.net.InetSocketAddress("127.0.0.1",port));
   }
   awaitCapacity(capacity,3);assertEquals(3,capacity.availablePermits());assertEquals(baseline,sessionDirectories(context));
  }
  android.util.Log.i("ParvazProbe","REPEATED_STOP_CHILD_TRUST_PORT_CAPACITY_CLEAN_OK cycles=4 SDK="+android.os.Build.VERSION.SDK_INT);
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
