package com.parvaz.tunnel.core;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ResumableDownloadTest {
 @Rule public TemporaryFolder folder=new TemporaryFolder();
 final byte[] data="0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 File partial;String hash;
 @Before public void before()throws Exception {partial=new File(folder.getRoot(),"pending.apk");hash=hex(data);}
 static String hex(byte[] bytes)throws Exception {StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
 static class Response extends HttpURLConnection {
  int status;byte[] bytes;String range,encoding;long length=-1;boolean closed;InputStream input;
  Response(int status,byte[] bytes)throws Exception{super(new URL("https://fixture.invalid/asset"));this.status=status;this.bytes=bytes;}
  public void connect(){}public boolean usingProxy(){return true;}public void disconnect(){closed=true;}
  public int getResponseCode(){return status;}public long getContentLengthLong(){return length;}
  public String getHeaderField(String key){return key.equalsIgnoreCase("Content-Range")?range:key.equalsIgnoreCase("Content-Encoding")?encoding:null;}
  public InputStream getInputStream(){return input==null?new ByteArrayInputStream(bytes):input;}
 }
 @Test public void brokenReadKeepsPrefixThen206CompletesAndHashesWholeFile()throws Exception {
  Response broken=new Response(200,data);broken.input=new InputStream(){int pos;
   public int read()throws IOException{if(pos>=3)throw new IOException("fixture disconnect");return data[pos++];}
   public int read(byte[] b,int off,int len)throws IOException{if(pos>=3)throw new IOException("fixture disconnect");int n=3-pos;System.arraycopy(data,pos,b,off,n);pos+=n;return n;}
  };
  try{ResumableDownload.fetch(partial,10,hash,offset->broken,null);fail();}catch(IOException expected){}
  assertEquals(3,partial.length());assertTrue(broken.closed);
  List<Integer> progress=new ArrayList<>();
  ResumableDownload.fetch(partial,10,hash,offset->{assertEquals(3,offset);Response r=new Response(206,Arrays.copyOfRange(data,3,10));r.range="bytes 3-9/10";r.length=7;return r;},progress::add);
  assertArrayEquals(data,Files.readAllBytes(partial.toPath()));assertFalse(progress.contains(100));
 }
 @Test public void eofBeforeExpectedSizeIsResumable()throws Exception {
  try{ResumableDownload.fetch(partial,10,hash,o->new Response(200,Arrays.copyOf(data,4)),null);fail();}catch(EOFException expected){}
  assertEquals(4,partial.length());
 }
 @Test public void ignoredRangeReplacesRatherThanAppends()throws Exception {
  Files.write(partial.toPath(),Arrays.copyOf(data,3));
  ResumableDownload.fetch(partial,10,hash,o->{assertEquals(3,o);return new Response(200,data);},null);
  assertArrayEquals(data,Files.readAllBytes(partial.toPath()));
 }
 @Test public void wrongRangesNeverCompleteAndDiscardPartial()throws Exception {
  for(String range:new String[]{null,"bytes 0-9/10","bytes 3-8/10","bytes 3-9/11","bytes 3-9/*","bytes 999999999999999999999999-9/10"}){
   Files.write(partial.toPath(),Arrays.copyOf(data,3));
   try{ResumableDownload.fetch(partial,10,hash,o->{Response r=new Response(206,Arrays.copyOfRange(data,3,10));r.range=range;return r;},null);fail();}
   catch(ResumableDownload.InvalidTransfer expected){}assertFalse(partial.exists());
  }
 }
 @Test public void tamperedPrefixCannotPassFinalHash()throws Exception {
  Files.write(partial.toPath(),"xxx".getBytes());
  try{ResumableDownload.fetch(partial,10,hash,o->{Response r=new Response(206,Arrays.copyOfRange(data,3,10));r.range="bytes 3-9/10";return r;},null);fail();}
  catch(ResumableDownload.InvalidTransfer expected){assertEquals("UPDATE_CHECKSUM_MISMATCH",expected.getMessage());}assertFalse(partial.exists());
 }
 @Test public void completedCacheIsRehashedWithoutAnotherRequest()throws Exception {
  Files.write(partial.toPath(),data);ResumableDownload.fetch(partial,10,hash,o->{throw new AssertionError("Unexpected network");},null);
  Files.write(partial.toPath(),"xxxxxxxxxx".getBytes());
  try{ResumableDownload.fetch(partial,10,hash,o->{throw new AssertionError();},null);fail();}catch(ResumableDownload.InvalidTransfer expected){}
  assertFalse(partial.exists());
 }
 @Test public void range416RestartsOnceFromZero()throws Exception {
  Files.write(partial.toPath(),Arrays.copyOf(data,3));AtomicInteger calls=new AtomicInteger();
  ResumableDownload.fetch(partial,10,hash,o->{if(calls.incrementAndGet()==1){assertEquals(3,o);return new Response(416,new byte[0]);}assertEquals(0,o);return new Response(200,data);},null);
  assertEquals(2,calls.get());assertArrayEquals(data,Files.readAllBytes(partial.toPath()));
 }
 @Test public void serverErrorKeepsExistingPrefix()throws Exception {
  Files.write(partial.toPath(),Arrays.copyOf(data,3));
  try{ResumableDownload.fetch(partial,10,hash,o->new Response(503,new byte[0]),null);fail();}catch(IOException expected){}
  assertEquals(3,partial.length());
 }
 @Test public void encodedOversizedAndWrongLengthResponsesAreRejected()throws Exception {
  for(int mode=0;mode<3;mode++){
   Response r=new Response(200,mode==1?new byte[11]:data);if(mode==0)r.encoding="gzip";if(mode==2)r.length=9;
   try{ResumableDownload.fetch(partial,10,hash,o->r,null);fail();}catch(ResumableDownload.InvalidTransfer expected){}
   assertFalse(partial.exists());
  }
 }
 @Test public void oversizedExistingPartialIsRemovedWithoutNetwork()throws Exception {
  Files.write(partial.toPath(),new byte[11]);
  try{ResumableDownload.fetch(partial,10,hash,o->{throw new AssertionError();},null);fail();}catch(ResumableDownload.InvalidTransfer expected){}
  assertFalse(partial.exists());
 }
 @Test public void identityChangesWithAbiUrlSizeOrDigest()throws Exception {
  UpdateChecker.Release r=new UpdateChecker.Release();r.version="1.26.4";r.size=10;r.sha256=hash;r.downloadUrl="https://github.com/hojjatrad/parvaz/releases/download/v1.26.4/Parvaz-1.26.4.apk";
  String name=UpdateChecker.partialName(r);assertTrue(name.endsWith(".apk"));
  r.downloadUrl+="-arm64";assertNotEquals(name,UpdateChecker.partialName(r));r.downloadUrl=r.downloadUrl.replace("-arm64","");
  r.size=9;assertNotEquals(name,UpdateChecker.partialName(r));r.size=10;r.sha256=hex(new byte[]{1});assertNotEquals(name,UpdateChecker.partialName(r));
 }
}
