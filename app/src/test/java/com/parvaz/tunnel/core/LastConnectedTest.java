package com.parvaz.tunnel.core;
import android.app.Application;
import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class LastConnectedTest {
 private Context app;private ProfileStore store;private SharedPreferences prefs;private Profile a,b;
 private CoreManager manager;private StartupDiagnostics.Attempt trace;
 @Before public void setup()throws Exception{
  app=ApplicationProvider.getApplicationContext();ProfileStore.d=null;CoreManager.c=null;TunnelVpnService.serviceRunning=false;
  app.getSharedPreferences("parvaz_store",0).edit().clear().commit();prefs=app.getSharedPreferences("parvaz_prefs",0);prefs.edit().clear().commit();
  store=ProfileStore.f(app);a=profile("a");b=profile("b");store.restoreRecords(Arrays.asList(a,b),Collections.emptyList(),ProfileStore.MANUAL_GROUP);a=store.getActiveById("a");b=store.getActiveById("b");manager=CoreManager.b();live(a);
 }
 private Profile profile(String id){Profile p=new Profile();p.id=id;p.protocol="vless";p.address="fixture.invalid";p.port=443;p.uuid="11111111-1111-4111-8111-111111111111";return p;}
 private void live(Profile p){
  AtomicLong now=new AtomicLong();trace=StartupDiagnostics.begin(0,0,now::get);trace.routeConfigured(true);trace.coreStarted();
  ReflectionHelpers.setField(manager,"diagnostics",trace);ReflectionHelpers.setField(manager,"verifiedPort",12345);ReflectionHelpers.setField(manager,"startupLatency",store.captureStartupLatency(p));ReflectionHelpers.setField(manager,"livePrefs",prefs);ReflectionHelpers.setField(manager,"liveStore",store);ReflectionHelpers.setField(manager,"rememberedDefault",false);manager.running=true;
 }
 private void confirmed(){trace.probeFinished(true,80);manager.publishStartupLatency(store,manager.sessionId());}
 @After public void cleanup(){manager.stop();CoreManager.c=null;ProfileStore.d=null;TunnelVpnService.serviceRunning=false;}
 @Test public void realLiveProofRecordsDurableDefault(){confirmed();assertEquals("a",LastConnected.resolve(store,prefs).id);ProfileStore reopened=new ProfileStore(app);assertEquals("a",LastConnected.resolve(reopened,prefs).id);}
 @Test public void localStartAndTrafficNeverPromoteCandidate(){trace.received(5000);manager.publishStartupLatency(store,manager.sessionId());assertNull(LastConnected.resolve(store,prefs));}
 @Test public void manualPingDoesNotBecomeDefault(){store.i(b.id,5);assertNull(LastConnected.resolve(store,prefs));}
 @Test public void tentativeSelectionDoesNotDestroyLastSuccess(){confirmed();prefs.edit().putString("selected_profile","b").commit();assertEquals("b",prefs.getString("selected_profile",""));assertTrue(LastConnected.restore(app));assertEquals("a",prefs.getString("selected_profile",""));}
 @Test public void stoppingFailedAttemptRestoresPreviousDefault(){confirmed();prefs.edit().putString("selected_profile","b").commit();live(b);trace.probeFinished(false,900);manager.publishStartupLatency(store,manager.sessionId());TunnelVpnService svc=Robolectric.buildService(TunnelVpnService.class).get();try{svc.shutdown(false,false);assertEquals("a",prefs.getString("selected_profile",""));}finally{svc.operations.close();}}
 @Test public void newestSuccessfulConnectionReplacesDefault(){confirmed();live(b);confirmed();assertEquals("b",LastConnected.resolve(store,prefs).id);prefs.edit().putString("selected_profile","a").commit();assertTrue(LastConnected.restore(app));assertEquals("b",prefs.getString("selected_profile",""));}
 @Test public void archivedSourceCannotBeSilentlyReactivated(){confirmed();a.subscriptionId="archived";assertFalse(LastConnected.restore(app));assertNull(LastConnected.resolve(store,prefs));}
 @Test public void changedCredentialsInvalidateOldDefault(){confirmed();a.uuid="22222222-2222-4222-8222-222222222222";assertFalse(LastConnected.restore(app));}
 @Test public void renamingSameConnectionPreservesDefault(){confirmed();a.remark="renamed";assertTrue(LastConnected.restore(app));assertEquals("a",prefs.getString("selected_profile",""));}
 @Test public void deletedDefaultIsNotResurrected()throws Exception{confirmed();store.restoreRecords(Collections.singletonList(b),Collections.emptyList(),ProfileStore.MANUAL_GROUP);assertFalse(LastConnected.restore(app));assertNull(store.getById("a"));}
 @Test public void legacyInstallDoesNotInventSuccessfulConnection(){prefs.edit().putString("selected_profile","b").commit();assertFalse(LastConnected.restore(app));assertEquals("b",prefs.getString("selected_profile",""));}
 @Test public void backupPayloadRetainsDefault()throws Exception{confirmed();String backup=BackupManager.export(app);prefs.edit().remove(LastConnected.KEY).commit();BackupManager.a(app,backup);assertEquals("a",LastConnected.resolve(store,prefs).id);}
 @Test public void legacyBackupClearsUnrelatedLocalDefault()throws Exception{confirmed();JSONObject backup=new JSONObject(BackupManager.export(app));backup.getJSONObject("settings").remove(LastConnected.KEY);BackupManager.a(app,backup.toString());assertFalse(prefs.contains(LastConnected.KEY)&&!prefs.getString(LastConnected.KEY,"").isEmpty());}
 @Test public void staleNetworkProofCannotPromoteCandidate(){trace.probeFinished(true,80);NetworkEpoch.changed();manager.publishStartupLatency(store,manager.sessionId());assertNull(LastConnected.resolve(store,prefs));}
 @Test public void sourceReplacementBeforePublicationRejectsOldOwner()throws Exception{trace.probeFinished(true,80);store.restoreRecords(Arrays.asList(a,b),Collections.emptyList(),ProfileStore.MANUAL_GROUP);manager.publishStartupLatency(store,manager.sessionId());assertNull(LastConnected.resolve(store,prefs));}
 @Test public void pendingManualStatusDoesNotPreventRememberingVerifiedLiveConnection(){store.beginMeasurement(a);confirmed();assertEquals(LatencyResult.TESTING,a.ping);assertEquals("a",LastConnected.resolve(store,prefs).id);}
 @Test public void oldColdTimingsAreNotMixedWithRequestRtt()throws Exception{
  app.getSharedPreferences("parvaz_store",0).edit().putString("pings","{\"a\":9000}").commit();assertEquals(-1,new ProfileStore(app).getById("a").ping);
  ServerMemory.Entry old=ServerMemory.Entry.fromJson(new JSONObject("{\"p\":\"a\",\"s\":7,\"f\":2,\"l\":9000,\"j\":500,\"t\":42}"));assertEquals(7,old.successes);assertEquals(2,old.failures);assertEquals(42,old.lastSuccess);assertEquals(-1,old.avgLatency,0);assertEquals(0,old.jitter,0);
 }
 @Test public void liveProbeRequiresSameProfileSourceIdentityAndResolver(){
  ReflectionHelpers.setField(manager,"liveProfile",ProfileIdentity.copy(a));ReflectionHelpers.setField(manager,"liveIdentity",ProfileIdentity.fingerprint(a));
  CoreManager.LiveProbe lease=manager.liveProbe(a);assertNotNull(lease);assertTrue(manager.ownsLiveProbe(lease));assertNull(manager.liveProbe(b));
  Profile edited=ProfileIdentity.copy(a);edited.uuid="22222222-2222-4222-8222-222222222222";assertNull(manager.liveProbe(edited));
  Profile other=ProfileIdentity.copy(a);other.subscriptionId="other";assertNull(manager.liveProbe(other));NetworkEpoch.resolverChanged();assertNull(manager.liveProbe(a));assertFalse(manager.ownsLiveProbe(lease));
 }
 @Test public void liveProbeResultsExpireOnNetworkAndSessionChange(){
  ReflectionHelpers.setField(manager,"liveProfile",ProfileIdentity.copy(a));ReflectionHelpers.setField(manager,"liveIdentity",ProfileIdentity.fingerprint(a));
  CoreManager.LiveProbe old=manager.liveProbe(a);NetworkEpoch.changed();assertFalse(manager.ownsLiveProbe(old));CoreManager.LiveProbe fresh=manager.liveProbe(a);assertNotNull(fresh);manager.stop();assertFalse(manager.ownsLiveProbe(fresh));
 }
 @Test public void stopBeforeNextTickerStillRemembersVerifiedConnection(){trace.probeFinished(true,80);assertNull(LastConnected.resolve(store,prefs));manager.stop();assertEquals("a",LastConnected.resolve(store,prefs).id);}
 @Test public void restoredDefaultUpdatesVisibleSelection(){
  confirmed();prefs.edit().putString("selected_profile","b").commit();assertTrue(LastConnected.restore(app));
  com.parvaz.tunnel.MainActivity activity=Robolectric.buildActivity(com.parvaz.tunnel.MainActivity.class).get();activity.L=new Prefs(app);activity.b0=store;
  activity.connectButton=new android.view.View(activity);activity.statusText=new android.widget.TextView(activity);activity.serverText=new android.widget.TextView(activity);
  activity.z=new com.parvaz.tunnel.ui.ServerAdapter(new android.view.ContextThemeWrapper(app,com.parvaz.tunnel.R.style.AppTheme),activity.new C0030l());activity.z.f368h="b";activity.state=0;
  activity.renderState();assertEquals("a",activity.z.f368h);
 }
}
