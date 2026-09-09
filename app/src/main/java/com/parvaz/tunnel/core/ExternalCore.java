package com.parvaz.tunnel.core;
import android.content.Context;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.model.Profile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded child engine. Private stdin, authenticated loopback, no downloaded executable.
 * A compiled PR_SET_PDEATHSIG guard prevents children surviving the Android app process. */
public final class ExternalCore implements AutoCloseable {
 private static final Semaphore CAPACITY=new Semaphore(3);
 private final AtomicBoolean closed=new AtomicBoolean();
 private Process process;private File directory;private boolean permit;
 public int port,dnsPort;public String username="parvaz",password;
 public static ExternalCore start(Context context,Profile profile,Runnable failure)throws Exception {
  ExternalCore session=new ExternalCore();
  try{
   if(!CAPACITY.tryAcquire(30,TimeUnit.SECONDS))throw new IOException("Engine capacity reached");session.permit=true;
   session.port=freePort();session.dnsPort=freePort();session.password=UUID.randomUUID().toString()+UUID.randomUUID();
   session.directory=new File(context.getNoBackupFilesDir(),"engine-session-"+UUID.randomUUID());if(!session.directory.mkdir())throw new IOException("Private runtime unavailable");
   String name=profile.protocol.equals("full-clash")?"mihomo":"singbox";
   File executable=new File(context.getApplicationInfo().nativeLibraryDir,"lib"+name+".so");if(!executable.isFile()||!executable.canExecute())throw new IOException("Bundled engine unavailable");
   List<String> command=name.equals("mihomo")?Arrays.asList(executable.toString(),"-d",session.directory.toString(),"-f","-"):Arrays.asList(executable.toString(),"run","-D",session.directory.toString(),"-c","stdin");
   ProcessBuilder builder=new ProcessBuilder(command).directory(session.directory).redirectErrorStream(true);
   builder.environment().keySet().removeIf(k->k.startsWith("CLASH_")||k.startsWith("SING_BOX_")||k.startsWith("SSL_CERT_"));builder.environment().put("GOMAXPROCS","2");
   session.process=builder.start();
   Thread drain=new Thread(()->{try(InputStream in=session.process.getInputStream()){byte[] buffer=new byte[4096];while(in.read(buffer)!=-1){/* Private engine logs are deliberately not published. */}}catch(IOException ignored){}},"parvaz-engine-output");drain.setDaemon(true);drain.start();
   try(OutputStream input=session.process.getOutputStream()){input.write(EngineConfig.build(profile,session.port,session.dnsPort,session.username,session.password).toString().getBytes(StandardCharsets.UTF_8));}
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);boolean ready=false;
   while(System.nanoTime()<deadline){if(!session.alive())throw new IOException("Native configuration rejected (exit "+session.process.exitValue()+")");
    try{session.authenticate();ready=true;break;}catch(IOException unavailable){Thread.sleep(100);}
   }
   if(!ready||!session.alive())throw new IOException("Native engine startup timeout");
   Thread monitor=new Thread(()->{try{session.process.waitFor();if(!session.closed.get()){session.close();if(failure!=null)failure.run();}}catch(InterruptedException ignored){Thread.currentThread().interrupt();}},"parvaz-engine-exit");monitor.setDaemon(true);monitor.start();return session;
  }catch(Exception e){session.close();throw e;}
 }
 private static int freePort()throws IOException {try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){return s.getLocalPort();}}
 private void authenticate()throws IOException {
  try(Socket s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",port),250);s.setSoTimeout(300);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();out.write(new byte[]{5,1,2});if(in.read()!=5||in.read()!=2)throw new IOException("SOCKS authentication required");
   byte[] user=username.getBytes(StandardCharsets.US_ASCII),pass=password.getBytes(StandardCharsets.US_ASCII);out.write(1);out.write(user.length);out.write(user);out.write(pass.length);out.write(pass);if(in.read()!=1||in.read()!=0)throw new IOException("SOCKS authentication failed");}
 }
 public Profile relay(Profile original){Profile p;try{p=Profile.fromJson(original.toJson());}catch(org.json.JSONException e){throw new IllegalArgumentException("Invalid relay profile",e);}p.protocol="socks";p.address="127.0.0.1";p.port=port;p.uuid=username;p.quicKey=password;p.security="";p.network="tcp";p.sni="";p.host="";p.rawJson="";return p;}
 private boolean alive(){try{process.exitValue();return false;}catch(IllegalThreadStateException stillRunning){return true;}}
 public boolean isRunning(){return !closed.get()&&process!=null&&alive();}
 @Override public void close(){if(closed.getAndSet(true))return;if(process!=null){process.destroy();if(android.os.Build.VERSION.SDK_INT>=26){try{if(!process.waitFor(1500,TimeUnit.MILLISECONDS))process.destroyForcibly();}catch(InterruptedException e){process.destroyForcibly();Thread.currentThread().interrupt();}}}erase(directory);if(permit){permit=false;CAPACITY.release();}password=null;}
 private static void erase(File directory){if(directory==null)return;File[] files=directory.listFiles();if(files!=null)for(File file:files){try{if(file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())){if(file.isDirectory())erase(file);else file.delete();}}catch(IOException ignored){}}directory.delete();}
 public static void cleanOrphans(Context context){File[] files=context.getNoBackupFilesDir().listFiles();if(files!=null)for(File file:files)if(file.isDirectory()&&file.getName().startsWith("engine-session-"))erase(file);}
}
