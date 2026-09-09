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
 @Test public void actualAndroidKeystoreMigrationAndReopen()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("cipher-probe",0);prefs.edit().clear().putString("profiles","private-test-credential").putString("subs","private-test-url").commit();
  StoreCipher cipher=StoreCipher.open(context,prefs);assertFalse(prefs.getString("profiles","").contains("private-test"));
  assertEquals("private-test-credential",StoreCipher.open(context,prefs).decode("profiles",prefs.getString("profiles","")));
  String old=prefs.getString("profiles","");KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry("com.parvaz.tunnel.records.v1");
  try{StoreCipher.open(context,prefs);fail("Missing key must not silently rotate");}catch(IllegalStateException expected){}
  assertEquals(old,prefs.getString("profiles",""));prefs.edit().clear().commit();
  android.util.Log.i("ParvazProbe","SECURE_RECORDS_OK SDK="+android.os.Build.VERSION.SDK_INT);
 }
}
