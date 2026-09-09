package com.parvaz.tunnel.store;

import android.app.Application;
import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.model.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Real restore/parser components; only the device key provider is shadowed for JVM tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class BackupPreservationTest {
 private Context context;
 private ProfileStore store;
 private SharedPreferences records,prefs;
 private String good;
 private Map<String,?> savedRecords,savedPrefs;
 private String savedProfiles,savedSubs;
 @Before public void setup()throws Exception {
  context=ApplicationProvider.getApplicationContext();ProfileStore.d=null;
  records=context.getSharedPreferences("parvaz_store",0);prefs=context.getSharedPreferences("parvaz_prefs",0);
  assertTrue(records.edit().clear().commit());assertTrue(prefs.edit().clear().commit());
  store=ProfileStore.f(context);
  Profile manual=LinkParser.parseMany("vless://11111111-1111-4111-8111-111111111111@manual.invalid:443?security=tls").get(0);
  manual.id="manual-fixture";manual.remark="دستی";
  Profile owned=LinkParser.parseMany("trojan://fixture-only-password@owned.invalid:443?sni=owned.invalid").get(0);
  owned.id="owned-fixture";owned.subscriptionId="active-source";owned.remark="اشتراک";owned.ping=41;
  Subscription active=new Subscription();active.id="active-source";active.url="https://active.invalid/sub/fixture";active.enabled=true;active.count=1;
  Subscription archived=new Subscription();archived.id="archived-source";archived.url="https://archive.invalid/sub/fixture";archived.enabled=false;
  store.restoreRecords(Arrays.asList(manual,owned),Arrays.asList(active,archived),active.id);
  assertTrue(prefs.edit().putString("selected_profile",owned.id).putString("favorites",owned.id)
      .putString("remote_dns","https://dns.invalid/query").putBoolean("kill_switch",true)
      .putString("fixture_unrelated_setting","preserve-me").commit());
  good=BackupManager.export(context);capture();
 }
 @After public void cleanup(){ProfileStore.d=null;}
 private String profiles(ProfileStore source)throws Exception {JSONArray array=new JSONArray();for(Object p:source.e())array.put(((Profile)p).toJson());return array.toString();}
 private String subscriptions(ProfileStore source)throws Exception {JSONArray array=new JSONArray();for(Object s:source.f())array.put(((Subscription)s).toJson());return array.toString();}
 private void capture()throws Exception {
  savedRecords=new HashMap<>(records.getAll());savedPrefs=new HashMap<>(prefs.getAll());
  savedProfiles=profiles(store);savedSubs=subscriptions(store);
 }
 private void assertPreserved()throws Exception {
  assertEquals("Raw record preferences changed",savedRecords,records.getAll());
  assertEquals("App settings changed",savedPrefs,prefs.getAll());
  assertEquals(savedProfiles,profiles(store));assertEquals(savedSubs,subscriptions(store));
  ProfileStore reopened=new ProfileStore(context);
  assertEquals(savedProfiles,profiles(reopened));assertEquals(savedSubs,subscriptions(reopened));
  assertEquals("active-source",reopened.primarySubscription());
  assertEquals("owned-fixture",prefs.getString("selected_profile",""));
 }
 private void reject(String text)throws Exception {
  boolean rejected=false;try{BackupManager.a(context,text);}catch(JSONException|IllegalArgumentException expected){rejected=true;}
  assertTrue("Invalid input unexpectedly restored",rejected);assertPreserved();
 }
 @Test public void truncatedAndIncompleteDocumentsPreserveEverything()throws Exception {
  reject(good.substring(0,good.length()/2));reject("{\"profiles\":[]}");
  reject("{\"profiles\":{},\"subscriptions\":[]}");reject("{\"profiles\":[],\"subscriptions\":{}}");
 }
 @Test public void lateInvalidProfileAndDuplicateIdentityPreserveEverything()throws Exception {
  JSONObject root=new JSONObject(good);root.getJSONArray("profiles").getJSONObject(1).put("port",0);reject(root.toString());
  root=new JSONObject(good);root.getJSONArray("profiles").getJSONObject(1).put("id","manual-fixture");reject(root.toString());
 }
 @Test public void explicitMalformedPortsAreRejectedBeforeAnyWrites()throws Exception {
  for(Object port:new Object[]{-1,65536,443.5,"not-a-port",JSONObject.NULL,true,new JSONArray(),"443.5","9999999999999999999999999999999999999999"}){
   JSONObject root=new JSONObject(good);root.getJSONArray("profiles").getJSONObject(1).put("port",port);reject(root.toString());
  }
 }
 @Test public void omittedAndIntegralLegacyPortFieldsRemainReadable()throws Exception {
  for(Object port:new Object[]{null,"443",443.0}){
   JSONObject root=new JSONObject(good);JSONObject profile=root.getJSONArray("profiles").getJSONObject(1);
   if(port==null)profile.remove("port");else profile.put("port",port);
   BackupManager.a(context,root.toString());assertEquals(443,store.getById("owned-fixture").port);
   assertEquals(2,store.e().size());assertEquals("active-source",store.primarySubscription());
  }
 }
 @Test public void validatedNonDefaultPortIsThePortActuallyRestored()throws Exception {
  for(Object port:new Object[]{8443,"8443","۸۴۴۳",8443.0,"8.443e3"}){
   JSONObject root=new JSONObject(good);root.getJSONArray("profiles").getJSONObject(1).put("port",port);
   BackupManager.a(context,root.toString());assertEquals(8443,store.getById("owned-fixture").port);
   assertEquals(8443,new ProfileStore(context).getById("owned-fixture").port);
  }
 }
 @Test public void lateInvalidSubscriptionPreservesEverything()throws Exception {
  JSONObject root=new JSONObject(good);root.getJSONArray("subscriptions").getJSONObject(1).put("id","active-source");reject(root.toString());
  root=new JSONObject(good);root.getJSONArray("subscriptions").getJSONObject(1).put("url","file:///not-a-subscription");reject(root.toString());
 }
 @Test public void lateInvalidSettingPreservesEverything()throws Exception {
  JSONObject root=new JSONObject(good);root.getJSONObject("settings").put("vpn_mtu","not-an-integer");reject(root.toString());
  root=new JSONObject(good);root.getJSONObject("settings").put("favorites",new JSONArray().put("bad\nentry"));reject(root.toString());
 }
 @Test public void wrongPasswordAndTamperedCiphertextNeverReachRestore()throws Exception {
  String encrypted=BackupCrypto.encrypt(good,"fixture-only".toCharArray());
  boolean rejected=false;
  try{BackupManager.a(context,BackupCrypto.decrypt(encrypted,"wrong-fixture-password".toCharArray()));}
  catch(java.security.GeneralSecurityException expected){rejected=true;}
  assertTrue(rejected);assertPreserved();
  String[] fields=encrypted.split(":",-1);byte[] ciphertext=android.util.Base64.decode(fields[5],android.util.Base64.NO_WRAP);
  ciphertext[ciphertext.length-1]^=1;fields[5]=android.util.Base64.encodeToString(ciphertext,android.util.Base64.NO_WRAP);rejected=false;
  try{BackupManager.a(context,BackupCrypto.decrypt(String.join(":",fields),"fixture-only".toCharArray()));}
  catch(java.security.GeneralSecurityException expected){rejected=true;}
  assertTrue(rejected);assertPreserved();
 }
 @Test public void brokenUtf8AndMidReadIoFailurePreserveEverything()throws Exception {
  boolean rejected=false;
  try{BackupManager.a(context,BackupInput.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28})));}
  catch(IOException expected){rejected=true;}
  assertTrue(rejected);assertPreserved();
  InputStream broken=new InputStream(){
   private boolean first=true;
   public int read()throws IOException{throw new IOException("Injected provider read failure");}
   public int read(byte[] bytes,int off,int len)throws IOException{
    if(!first)throw new IOException("Injected provider read failure");first=false;
    byte[] partial="{\"profiles\":[".getBytes(StandardCharsets.UTF_8);System.arraycopy(partial,0,bytes,off,partial.length);return partial.length;
   }
  };
  rejected=false;try{BackupManager.a(context,BackupInput.read(broken));}catch(IOException expected){rejected=true;}
  assertTrue(rejected);assertPreserved();
 }
 @Test public void repeatedValidEncryptedRestorePreservesIdentityScopeAndArchives()throws Exception {
  String encrypted=BackupCrypto.encrypt(good,"fixture-only".toCharArray());
  for(int i=0;i<2;i++){
   BackupManager.a restored=BackupManager.a(context,BackupCrypto.decrypt(encrypted,"fixture-only".toCharArray()));
   assertEquals(2,restored.f341a);assertEquals(2,restored.f342b);
   assertEquals(2,store.e().size());assertEquals(2,store.f().size());
   assertEquals(1,store.activeProfiles().size());assertEquals("owned-fixture",store.activeProfiles().get(0).id);
   assertEquals("active-source",store.primarySubscription());assertEquals("owned-fixture",prefs.getString("selected_profile",""));
   assertEquals(savedProfiles,profiles(store));assertEquals(savedSubs,subscriptions(store));
   assertEquals("preserve-me",prefs.getString("fixture_unrelated_setting",""));
   assertFalse(prefs.getBoolean("connect_on_boot",true));assertFalse(prefs.getBoolean("lan_proxy",true));
   assertTrue(records.getString("profiles","").startsWith("PARVAZ-GCM-1:"));
   assertFalse(records.getString("profiles","").contains("fixture-only-password"));
   assertEquals(savedProfiles,profiles(new ProfileStore(context)));
  }
 }
}
