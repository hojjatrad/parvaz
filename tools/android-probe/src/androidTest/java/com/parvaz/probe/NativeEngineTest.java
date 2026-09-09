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
  try(java.io.OutputStream input=process.getOutputStream()){input.write(com.parvaz.tunnel.config.EngineConfig.serialize(config,profile.protocol).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  if(!process.waitFor(8,java.util.concurrent.TimeUnit.SECONDS))process.destroyForcibly();reader.join(1500);
  android.util.Log.i("ParvazProbe","FIXTURE_DIAGNOSTICS "+output.toString("UTF-8"));
 }
 @Test public void packagedEnginesStartAuthenticateAndStop()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  for(String kind:new String[]{"full-singbox","full-clash"}){
   Profile p=new Profile();p.protocol=kind;p.rawJson=kind.equals("full-singbox")?"{\"outbounds\":[{\"type\":\"direct\"}]}":"{\"proxies\":[],\"rules\":[\"MATCH,DIRECT\"]}";
   ExternalCore core;
   try{core=ExternalCore.start(context,p,null);}catch(Exception failure){fixtureDiagnostics(context,p);throw failure;}
   try{
    assertTrue(core.isRunning());
    try(java.net.Socket socket=new java.net.Socket("127.0.0.1",core.port)){
     socket.setSoTimeout(2000);socket.getOutputStream().write(new byte[]{5,1,0});
     int version=socket.getInputStream().read(),method=socket.getInputStream().read();
     assertFalse("Loopback proxy accepted unauthenticated access",version==5&&method==0);
    }
   }finally{core.close();}assertFalse(core.isRunning());
   android.util.Log.i("ParvazProbe","NATIVE_ENGINE_OK "+kind+" SDK="+android.os.Build.VERSION.SDK_INT);
  }
 }
}
