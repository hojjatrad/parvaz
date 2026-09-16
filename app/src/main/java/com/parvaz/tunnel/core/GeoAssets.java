package com.parvaz.tunnel.core;
import android.content.*;import java.io.*;import java.net.*;import java.security.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;
/** Two verified files form one generation. Running processes keep an immutable
 * directory; newly downloaded rules activate only on the next process startup. */
public final class GeoAssets {
 private static final String PREFS="parvaz_geo",API="https://api.github.com/repos/chocolate4u/Iran-v2ray-rules/releases/latest";
 private static final long WEEK=7L*86400000,RETRY=3600000;
 private static final AtomicBoolean BUSY=new AtomicBoolean();private static File pinned;
 private GeoAssets(){}
 private static File bundled(Context c){return new File(c.getFilesDir(),"geo-bundled-"+com.parvaz.tunnel.BuildConfig.VERSION_CODE);}
 public static synchronized File directory(Context c){
  if(pinned!=null)return pinned;SharedPreferences p=c.getSharedPreferences(PREFS,0);
  for(String key:new String[]{"generation","previous_generation"}){String selected=p.getString(key,"");
   if(selected.matches("geo-gen-[a-f0-9-]{36}")){File candidate=new File(c.getFilesDir(),selected);try{verifyGeneration(candidate);pinned=candidate;break;}catch(Exception invalid){}}
  }
  if(pinned==null)pinned=bundled(c);
  File[] dirs=c.getFilesDir().listFiles();if(dirs!=null)for(File dir:dirs){String n=dir.getName();
   if((n.startsWith("geo-gen-")||n.startsWith("geo-stage-")||n.startsWith("geo-bundled-"))&&!dir.equals(pinned)&&!dir.equals(bundled(c))&&!n.equals(p.getString("previous_generation","")))erase(dir);
  }
  return pinned;
 }
 public static void installBundled(Context c){
  File dir=bundled(c);if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Geo bundle directory");
  for(String name:new String[]{"geoip.dat","geosite.dat"}){File file=new File(dir,name);
   try{GeoData.tags(file,name.equals("geoip.dat"));}catch(IOException invalid){if(file.exists()&&!file.delete())throw new IllegalStateException("Geo bundle recovery");CoreManager.copyAssetIfNeeded(c,name,file);}
   try{GeoData.tags(file,name.equals("geoip.dat"));}catch(IOException invalid){throw new IllegalStateException("Geo bundle invalid",invalid);}
  }directory(c);
 }
 public static boolean hasFullData(Context c){return directory(c).getName().startsWith("geo-gen-");}
 public static void maybeUpgrade(Context context){
  Context c=context.getApplicationContext();SharedPreferences p=c.getSharedPreferences(PREFS,0);long now=System.currentTimeMillis();
  if(now-p.getLong("last_success",0)<WEEK||now-p.getLong("last_attempt",0)<RETRY||!BUSY.compareAndSet(false,true))return;
  new Thread(()->{File stage=null;try{
   p.edit().putLong("last_attempt",System.currentTimeMillis()).apply();AppNetwork.Route route=AppNetwork.capture();long deadline=System.nanoTime()+180000000000L;
   ByteArrayOutputStream metadata=new ByteArrayOutputStream();fetch(route,API,metadata,1024*1024,deadline);JSONObject release=new JSONObject(metadata.toString("UTF-8"));
   if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("Unstable Geo release");
   Map<String,JSONObject> assets=new HashMap<>();JSONArray list=release.getJSONArray("assets");for(int i=0;i<list.length();i++){JSONObject a=list.getJSONObject(i);String name=a.optString("name");if(name.equals("geoip.dat")||name.equals("geosite.dat")){if(assets.put(name,a)!=null)throw new IOException("Duplicate asset");}}
   if(assets.size()!=2)throw new IOException("Incomplete Geo release");stage=new File(c.getFilesDir(),"geo-stage-"+UUID.randomUUID());if(!stage.mkdir())throw new IOException("Geo stage");JSONObject seal=new JSONObject();
   for(String name:new String[]{"geoip.dat","geosite.dat"}){
    JSONObject a=assets.get(name);String digest=a.optString("digest"),url=a.getString("browser_download_url");long size=a.getLong("size");
    if(!digest.matches("sha256:[a-fA-F0-9]{64}")||size<2||size>GeoData.MAX_BYTES||!url.startsWith("https://github.com/chocolate4u/Iran-v2ray-rules/releases/download/")||!url.endsWith("/"+name))throw new IOException("Unpinned Geo asset");
    File file=new File(stage,name);try(FileOutputStream out=new FileOutputStream(file)){fetch(route,url,out,size,deadline);out.getFD().sync();}
    if(file.length()!=size||!sha(file).equalsIgnoreCase(digest.substring(7)))throw new IOException("Geo digest mismatch");seal.put(name,digest.substring(7).toLowerCase(Locale.ROOT));
   }
   validatePair(stage);try(FileOutputStream out=new FileOutputStream(new File(stage,"seal.json"))){out.write(seal.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
   File generation=new File(c.getFilesDir(),"geo-gen-"+UUID.randomUUID());if(!stage.renameTo(generation))throw new IOException("Geo promotion");stage=generation;
   // One atomic preference publication; never delete currently usable rule files.
   if(!p.edit().putString("previous_generation",p.getString("generation","")).putString("generation",generation.getName()).putLong("last_success",System.currentTimeMillis()).commit())throw new IOException("Geo commit");stage=null;
   LogBuffer.listener("Verified geo rules downloaded; activation at next process start");
  }catch(Exception error){LogBuffer.listener("Geo update deferred; existing rules retained");}finally{if(stage!=null)erase(stage);BUSY.set(false);}},"parvaz-geo-update").start();
 }
 static void validatePair(File dir)throws IOException{if(!GeoData.tags(new File(dir,"geoip.dat"),true).contains("ir")||!GeoData.tags(new File(dir,"geosite.dat"),false).contains("category-ir"))throw new IOException("Required Geo tags missing");}
 static void verifyGeneration(File dir)throws Exception{File seal=new File(dir,"seal.json");if(!seal.isFile()||seal.length()>512)throw new IOException("Geo seal");byte[] bytes=new byte[(int)seal.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(seal))){in.readFully(bytes);}JSONObject json=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));for(String name:new String[]{"geoip.dat","geosite.dat"})if(!sha(new File(dir,name)).equals(json.getString(name)))throw new IOException("Geo altered");validatePair(dir);}
 static String sha(File f)throws Exception{if(!f.isFile()||f.length()>GeoData.MAX_BYTES)throw new IOException("Geo size");MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder h=new StringBuilder();for(byte b:d.digest())h.append(String.format(Locale.ROOT,"%02x",b&255));return h.toString();}
 private static void fetch(AppNetwork.Route route,String input,OutputStream out,long max,long deadline)throws Exception{
  URL url=new URL(input);for(int redirects=0;redirects<=5;redirects++){
   if(!url.getProtocol().equals("https")||url.getUserInfo()!=null)throw new IOException("Geo HTTPS required");HttpURLConnection c=route.open(url);
   try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(remaining(deadline,10000));c.setReadTimeout(remaining(deadline,15000));c.setRequestProperty("User-Agent","Parvaz");int status=c.getResponseCode();
    if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)throw new IOException("Geo redirect");url=new URL(url,next);continue;}
    if(status!=200||c.getContentLengthLong()>max)throw new IOException("Geo response");long count=0;try(InputStream in=c.getInputStream()){byte[] b=new byte[65536];int n;while(true){c.setReadTimeout(remaining(deadline,15000));n=in.read(b);if(n<0)break;if(n>max-count)throw new IOException("Geo limit");out.write(b,0,n);count+=n;}}return;
   }finally{c.disconnect();}
  }throw new IOException("Geo redirects");
 }
 private static int remaining(long deadline,int cap)throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();long ms=(deadline-System.nanoTime())/1000000;if(ms<=0)throw new java.net.SocketTimeoutException();return (int)Math.max(1,Math.min(ms,cap));}
 private static void erase(File dir){File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();dir.delete();}
}
