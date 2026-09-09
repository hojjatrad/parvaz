package com.parvaz.tunnel.core;
import android.app.Application;
import android.content.*;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.store.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class SafetyRegressionTest {
 Context context;
 @Before public void before(){context=ApplicationProvider.getApplicationContext();context.getSharedPreferences("parvaz_store",0).edit().clear().commit();context.getSharedPreferences("parvaz_prefs",0).edit().clear().commit();ProfileStore.d=null;}
 @After public void after(){ProfileStore.d=null;}
 @Test public void unknownLeakResultsNeverPass(){
  LeakTester.Report r=new LeakTester.Report();assertFalse(r.allPassed());r.checks.add(new LeakTester.Check("dns",LeakTester.UNKNOWN,""));assertFalse(r.allPassed());assertEquals(0,r.failures());
  r.checks.clear();r.checks.add(new LeakTester.Check("ip",LeakTester.PASS,""));assertTrue(r.allPassed());r.checks.add(new LeakTester.Check("ipv6",LeakTester.UNKNOWN,""));assertFalse(r.allPassed());
 }
 @Test public void sharedTextAndContentFileAreBoundedAndRecognized()throws Exception {
  String link="vless://11111111-1111-4111-8111-111111111111@test.invalid:443";
  assertEquals(link,SharedInput.read(context,new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT,link)));
  Uri uri=Uri.parse("content://test/config.json");org.robolectric.Shadows.shadowOf(context.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(link.getBytes(StandardCharsets.UTF_8)));
  assertEquals(link,SharedInput.read(context,new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,uri)));
  try{SharedInput.read(context,new Intent(Intent.ACTION_VIEW,Uri.parse("file:///data/private")));fail();}catch(IOException expected){}
  try{SharedInput.readUtf8(new ByteArrayInputStream(new byte[]{(byte)255}));fail();}catch(IOException expected){}
  try{SharedInput.readUtf8(new ByteArrayInputStream(new byte[5*1024*1024+1]));fail();}catch(IOException expected){}
 }
 @Test public void encryptedRecordsMigrateAndRejectTampering()throws Exception {
  SharedPreferences raw=context.getSharedPreferences("parvaz_store",0);raw.edit().putString("profiles","[]").putString("subs","[]").commit();
  StoreCipher cipher=StoreCipher.open(context,raw);String encrypted=raw.getString("profiles","");assertTrue(encrypted.startsWith("PARVAZ-GCM-1:"));assertFalse(encrypted.contains("[]"));assertEquals("[]",cipher.decode("profiles",encrypted));
  String secret=cipher.encode("profiles","private-test-credential");assertFalse(secret.contains("private-test-credential"));assertEquals("private-test-credential",cipher.decode("profiles",secret));
  raw.edit().putString("profiles",raw.getString("subs","")).commit();
  try{StoreCipher.open(context,raw);fail("Cross-key substitution accepted");}catch(IllegalStateException expected){}
  assertEquals(raw.getString("subs",""),raw.getString("profiles",""));
 }
 private String legacy(String kdf)throws Exception {
  byte[] salt=new byte[16],iv=new byte[12];SecretKey key=new SecretKeySpec(SecretKeyFactory.getInstance(kdf).generateSecret(new PBEKeySpec("test".toCharArray(),salt,120000,256)).getEncoded(),"AES");
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
  return "PARVAZ-ENC-1:"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(c.doFinal("test-data".getBytes(StandardCharsets.UTF_8)));
 }
 @Test public void newBackupAuthenticatesMetadataAndReadsBothLegacyKdfs()throws Exception {
  String v2=BackupCrypto.encrypt("test-data","test".toCharArray());assertTrue(v2.startsWith("PARVAZ-ENC-2:PBKDF2-SHA256:210000:"));assertEquals("test-data",BackupCrypto.decrypt(v2,"test".toCharArray()));
  assertEquals("test-data",BackupCrypto.decrypt(legacy("PBKDF2WithHmacSHA256"),"test".toCharArray()));assertEquals("test-data",BackupCrypto.decrypt(legacy("PBKDF2WithHmacSHA1"),"test".toCharArray()));
  try{BackupCrypto.decrypt(v2.replace(":210000:",":220000:"),"test".toCharArray());fail();}catch(java.security.GeneralSecurityException expected){}
  try{BackupCrypto.decrypt(v2.replace(":210000:",":999999999:"),"test".toCharArray());fail();}catch(IllegalArgumentException expected){}
 }
 @Test public void malformedRestoreCannotDeleteCurrentProfiles()throws Exception {
  ProfileStore store=ProfileStore.f(context);store.a(com.parvaz.tunnel.config.LinkParser.parseMany("vless://11111111-1111-4111-8111-111111111111@test.invalid:443"),"");
  String good=BackupManager.export(context);String id=((com.parvaz.tunnel.model.Profile)store.e().get(0)).id;
  try{BackupManager.a(context,"{\"profiles\":[{}],\"subscriptions\":[]}");fail();}catch(IllegalArgumentException expected){}
  assertNotNull(store.getById(id));BackupManager.a(context,good);BackupManager.a(context,good);assertEquals(1,store.e().size());assertEquals(1,new ProfileStore(context).e().size());
 }
 @Test public void lanProxyRequiresAuthAndStripsCredentialsBeforeForwarding()throws Exception {
  try(ServerSocket upstream=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));LanProxyBridge bridge=new LanProxyBridge(InetAddress.getByName("127.0.0.1"),0,upstream.getLocalPort(),"parvaz","012345678901234567890123")){
   upstream.setSoTimeout(1000);
   try(Socket client=new Socket("127.0.0.1",bridge.port())){client.setSoTimeout(2000);client.getOutputStream().write("CONNECT test.invalid:443 HTTP/1.1\r\nHost: test.invalid\r\n\r\n".getBytes(StandardCharsets.US_ASCII));assertTrue(new BufferedReader(new InputStreamReader(client.getInputStream())).readLine().contains("407"));}
   try{upstream.accept();fail("Unauthenticated connection reached upstream");}catch(SocketTimeoutException expected){}
   FutureTask<String> server=new FutureTask<>(()->{try(Socket sock=upstream.accept()){sock.setSoTimeout(3000);BufferedReader r=new BufferedReader(new InputStreamReader(sock.getInputStream()));StringBuilder h=new StringBuilder();String line;while((line=r.readLine())!=null&&!line.isEmpty())h.append(line).append('\n');sock.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".getBytes(StandardCharsets.US_ASCII));return h.toString();}});
   new Thread(server).start();String auth=Base64.getEncoder().encodeToString("parvaz:012345678901234567890123".getBytes(StandardCharsets.US_ASCII));
   try(Socket client=new Socket("127.0.0.1",bridge.port())){client.setSoTimeout(3000);client.getOutputStream().write(("GET http://test.invalid/ HTTP/1.1\r\nHost: test.invalid\r\nProxy-Authorization: Basic "+auth+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));assertTrue(new BufferedReader(new InputStreamReader(client.getInputStream())).readLine().contains("200"));}
   assertFalse(server.get(4,TimeUnit.SECONDS).toLowerCase(Locale.ROOT).contains("authorization"));assertTrue(bridge.isRunning());bridge.close();assertFalse(bridge.isRunning());
  }
  try{new LanProxyBridge(InetAddress.getByName("0.0.0.0"),0,10809,"parvaz","012345678901234567890123");fail();}catch(IllegalArgumentException expected){}
 }
}
