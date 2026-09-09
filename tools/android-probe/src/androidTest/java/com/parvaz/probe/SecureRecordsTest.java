package com.parvaz.probe;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.parvaz.tunnel.store.StoreCipher;
import org.junit.*;
import org.junit.runner.RunWith;
import java.security.KeyStore;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class SecureRecordsTest {
 @Test public void tamperingCrossFieldSwapAndDowngradePreserveExistingBytes()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  SharedPreferences prefs=context.getSharedPreferences("cipher-tamper-probe",0);
  assertTrue(prefs.edit().clear().putString("profiles","fixture-private-profile").putString("subs","fixture-private-subscription").commit());
  try{
   StoreCipher.open(context,prefs);
   String original=prefs.getString("profiles",""),subs=prefs.getString("subs","");
   byte[] bytes=android.util.Base64.decode(original.substring("PARVAZ-GCM-1:".length()),android.util.Base64.NO_WRAP);bytes[bytes.length-1]^=1;
   String tampered="PARVAZ-GCM-1:"+android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);
   for(String bad:new String[]{tampered,subs,"fixture-plaintext-downgrade"}){
    assertTrue(prefs.edit().putString("profiles",bad).commit());
    java.util.Map<String,?> before=new java.util.HashMap<>(prefs.getAll());boolean rejected=false;
    try{StoreCipher.open(context,prefs);}catch(IllegalStateException expected){rejected=true;}
    assertTrue("Corrupt/downgraded records accepted",rejected);assertEquals(before,prefs.getAll());
   }
   assertTrue(prefs.edit().putString("profiles",original).commit());
   assertEquals("fixture-private-profile",StoreCipher.open(context,prefs).decode("profiles",original));
   android.util.Log.i("ParvazProbe","ANDROID_RECORD_TAMPER_SWAP_DOWNGRADE_PRESERVED_OK SDK="+android.os.Build.VERSION.SDK_INT);
  }finally{assertTrue(prefs.edit().clear().commit());}
 }
 @Test public void actualAndroidKeystoreMigrationAndReopen()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("cipher-probe",0);prefs.edit().clear().putString("profiles","private-test-credential").putString("subs","private-test-url").commit();
  StoreCipher cipher=StoreCipher.open(context,prefs);assertFalse(prefs.getString("profiles","").contains("private-test"));
  assertEquals("private-test-credential",StoreCipher.open(context,prefs).decode("profiles",prefs.getString("profiles","")));
  java.io.File file=new java.io.File(context.getApplicationInfo().dataDir,"shared_prefs/cipher-probe.xml");
  String disk=new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);
  assertFalse(disk.contains("private-test-credential"));assertTrue(disk.contains("PARVAZ-GCM-1"));
  String old=prefs.getString("profiles","");KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry("com.parvaz.tunnel.records.v1");
  try{StoreCipher.open(context,prefs);fail("Missing key must not silently rotate");}catch(IllegalStateException expected){}
  assertEquals(old,prefs.getString("profiles",""));prefs.edit().clear().commit();
  android.util.Log.i("ParvazProbe","SECURE_RECORDS_OK SDK="+android.os.Build.VERSION.SDK_INT);
 }
}
