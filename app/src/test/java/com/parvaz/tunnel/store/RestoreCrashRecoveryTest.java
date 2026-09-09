package com.parvaz.tunnel.store;
import android.app.Application;
import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.model.Profile;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class RestoreCrashRecoveryTest {
 static final class SimulatedDeath extends Error {}
 Context base;FaultContext context;String desired;
 static final class FaultContext extends ContextWrapper {
  boolean dieBeforeSettings,failSettings,dieAfterSettings;
  FaultContext(Context base){super(base);}
  @Override public Context getApplicationContext(){return this;}
  @Override public SharedPreferences getSharedPreferences(String name,int mode){
   SharedPreferences original=super.getSharedPreferences(name,mode);
   if(!name.equals("parvaz_prefs"))return original;
   return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
    Object result=method.invoke(original,args);
    if(!method.getName().equals("edit"))return result;
    SharedPreferences.Editor editor=(SharedPreferences.Editor)result;
    return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(p,m,a)->{
     if(m.getName().equals("commit")&&dieBeforeSettings){dieBeforeSettings=false;throw new SimulatedDeath();}
     Object value=m.invoke(editor,a);
     if(m.getName().equals("commit")&&dieAfterSettings){dieAfterSettings=false;throw new SimulatedDeath();}
     if(m.getName().equals("commit")&&failSettings)return false; // Memory/disk may already contain the new values; false is not an acknowledgement.
     return value instanceof SharedPreferences.Editor?p:value;
    });
   });
  }
 }
 @Before public void setup()throws Exception {
  base=ApplicationProvider.getApplicationContext();context=new FaultContext(base);ProfileStore.d=null;
  cleanJournal();base.getSharedPreferences("parvaz_store",0).edit().clear().commit();base.getSharedPreferences("parvaz_prefs",0).edit().clear().commit();
  Profile old=LinkParser.parseMany("trojan://fixture@old.invalid:443").get(0);old.id="old";
  ProfileStore store=ProfileStore.f(context);store.restoreRecords(Arrays.asList(old),Collections.emptyList(),ProfileStore.MANUAL_GROUP);
  base.getSharedPreferences("parvaz_prefs",0).edit().putString("selected_profile","old").putString("remote_dns","old-fixture-dns").commit();
  JSONObject root=new JSONObject(BackupManager.export(context));
  JSONObject profile=root.getJSONArray("profiles").getJSONObject(0);profile.put("id","new").put("address","new.invalid").put("port",8443);
  root.getJSONObject("settings").put("selected_profile","new").put("remote_dns","new-fixture-dns");desired=root.toString();
 }
 private void cleanJournal(){for(String suffix:new String[]{"",".new",".bak"})new File(base.getNoBackupFilesDir(),"backup-restore-journal"+suffix).delete();}
 @After public void cleanup(){ProfileStore.d=null;cleanJournal();}
 @Test public void reopenCompletesRestoreInterruptedBetweenRecordAndSettingsCommits()throws Exception {
  context.dieBeforeSettings=true;
  boolean died=false;try{BackupManager.a(context,desired);}catch(SimulatedDeath expected){died=true;}assertTrue("Crash point not reached",died);
  // Recreate the model; this is JVM fault injection, not a claim of a cold Android process.
  ProfileStore.d=null;ProfileStore reopened=ProfileStore.f(base);
  assertNotNull(reopened.getById("new"));assertNull(reopened.getById("old"));
  assertEquals("new",base.getSharedPreferences("parvaz_prefs",0).getString("selected_profile",""));
  assertEquals("new-fixture-dns",base.getSharedPreferences("parvaz_prefs",0).getString("remote_dns",""));
 }
 @Test public void failedCommitBlocksStoreReadsUntilRecoverySucceeds()throws Exception {
  ProfileStore store=ProfileStore.f(context);context.failSettings=true;
  try{BackupManager.a(context,desired);fail();}catch(ProfileStore.RestoreUnavailable expected){}
  assertTrue(RestoreJournal.hasState(base));
  assertEquals("new",base.getSharedPreferences("parvaz_prefs",0).getString("selected_profile",""));
  try{store.activeProfiles();fail("Mixed generation exposed");}catch(ProfileStore.RestoreUnavailable expected){}
  assertTrue(RestoreJournal.hasState(base));context.failSettings=false;
  assertEquals("new",store.activeProfiles().get(0).id);assertFalse(RestoreJournal.hasState(base));
 }
 @Test public void prefsConstructionRecoversAfterSettingsCommitDeath()throws Exception {
  context.dieAfterSettings=true;
  try{BackupManager.a(context,desired);fail();}catch(SimulatedDeath expected){}
  assertTrue(RestoreJournal.hasState(base));ProfileStore.d=null;
  assertEquals("new",new Prefs(base).f343a.getString("selected_profile",""));assertFalse(RestoreJournal.hasState(base));
 }
 @Test public void generatedIdsAreStableAcrossReplay()throws Exception {
  JSONObject root=new JSONObject(desired);root.getJSONArray("profiles").getJSONObject(0).remove("id");context.dieBeforeSettings=true;
  try{BackupManager.a(context,root.toString());fail();}catch(SimulatedDeath expected){}
  StoreCipher cipher=StoreCipher.open(base,base.getSharedPreferences("parvaz_store",0));
  String before=new JSONArray(cipher.decode("profiles",base.getSharedPreferences("parvaz_store",0).getString("profiles",""))).getJSONObject(0).getString("id");
  ProfileStore.d=null;assertEquals(before,ProfileStore.f(base).activeProfiles().get(0).id);
  assertEquals(before,new Prefs(base).f343a.getString("selected_profile",""));
 }
}
