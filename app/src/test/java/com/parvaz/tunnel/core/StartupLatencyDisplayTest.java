package com.parvaz.tunnel.core;
import android.app.Application;
import android.content.*;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.R;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.*;
import com.parvaz.tunnel.ui.ServerAdapter;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
/** Production store/manager/ticker/adapter paths; injected proof, not a real VPN. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class StartupLatencyDisplayTest {
 private Context app;private ProfileStore store;private Profile p;private CoreManager manager;
 private StartupDiagnostics.Attempt trace;private AtomicLong phase,boot;
 @Before public void setup()throws Exception{
  app=ApplicationProvider.getApplicationContext();ProfileStore.d=null;CoreManager.c=null;
  app.getSharedPreferences("parvaz_store",0).edit().clear().commit();app.getSharedPreferences("parvaz_prefs",0).edit().clear().commit();
  store=ProfileStore.f(app);Profile fixture=new Profile();fixture.id="startup-fixture";fixture.protocol="vless";fixture.address="fixture.invalid";fixture.uuid="11111111-1111-4111-8111-111111111111";
  store.restoreRecords(Collections.singletonList(fixture),Collections.emptyList(),ProfileStore.MANUAL_GROUP);p=store.getActiveById(fixture.id);
  manager=CoreManager.b();phase=new AtomicLong();boot=new AtomicLong();trace=StartupDiagnostics.begin(0,0,phase::get,boot::get);trace.routeConfigured(true);trace.coreStarted();
  ReflectionHelpers.setField(manager,"diagnostics",trace);ReflectionHelpers.setField(manager,"verifiedPort",12345);ReflectionHelpers.setField(manager,"startupLatency",store.captureStartupLatency(ProfileIdentity.copy(p)));manager.running=true;
 }
 @After public void cleanup(){manager.stop();CoreManager.c=null;ProfileStore.d=null;TunnelVpnService.serviceRunning=false;}
 private int publish(){return manager.publishStartupLatency(store,manager.sessionId());}
 private void success(){trace.probeFinished(true,87,NetworkEpoch.current());}
 @Test public void startupResponseAutomaticallyFillsAndPersistsUntestedRowOnce(){success();assertEquals(87,publish());assertEquals(87,p.ping);assertEquals(0,publish());assertEquals(87,new ProfileStore(app).getById(p.id).ping);}
 @Test public void localRunningAndTrafficAreNotLatency(){trace.received(9000);assertEquals(0,publish());assertEquals(-1,p.ping);}
 @Test public void failedAndUnpinnedResponsesDoNotCreateNumbers(){trace.probeFinished(false,500);assertEquals(0,publish());trace.routeConfigured(false);success();assertEquals(0,publish());}
 @Test public void laterFailureDurationIsNeverShownAsSuccessfulLatency(){success();trace.probeFinished(false,6000);assertEquals(87,publish());}
 @Test public void networkChangeBeforeTickerRejectsOldResponse(){success();NetworkEpoch.changed();assertEquals(0,publish());}
 @Test public void resolverChangeRejectsOldCoreResponse(){success();NetworkEpoch.resolverChanged();success();assertEquals(0,publish());}
 @Test public void expiredProofCannotFillRow(){success();boot.set(120000);assertEquals(0,publish());}
 @Test public void replacedAndStoppedSessionsCannotPublish(){success();assertEquals(0,manager.publishStartupLatency(store,manager.sessionId()+1));trace.stop();assertEquals(0,publish());}
 @Test public void editedCredentialsCannotReceiveOldLatency(){success();p.uuid="22222222-2222-4222-8222-222222222222";assertEquals(0,publish());}
 @Test public void identicalSourceReplacementStillInvalidatesOldReservation()throws Exception{success();store.restoreRecords(Collections.singletonList(ProfileIdentity.copy(p)),Collections.emptyList(),ProfileStore.MANUAL_GROUP);assertEquals(0,publish());assertEquals(-1,store.getById(p.id).ping);}
 @Test public void manualTestAndTerminalStatesAreNotOverwritten(){success();ProfileStore.Measurement m=store.beginMeasurement(p);assertEquals(0,publish());store.finishMeasurement(m,LatencyResult.FAILED);assertEquals(0,publish());store.i(p.id,44);assertEquals(0,publish());assertEquals(44,p.ping);}
 @Test public void writeRechecksProofAndSourceScope(){ProfileStore.StartupLatency ticket=store.captureStartupLatency(p);assertFalse(store.publishStartupLatency(ticket,87,()->false));store.setPrimarySubscription("");assertFalse(store.publishStartupLatency(ticket,87,()->true));}
 @Test public void adapterNotificationReplacesStaleRowAndRendersCurrentValue()throws Exception{
  Context themed=new ContextThemeWrapper(app,R.style.AppTheme);ServerAdapter adapter=new ServerAdapter(themed,null);Profile old=p;
  store.restoreRecords(Collections.singletonList(ProfileIdentity.copy(p)),Collections.emptyList(),ProfileStore.MANUAL_GROUP);p=store.getById(p.id);store.i(p.id,123);adapter.i(p.id);
  assertNotSame(old,adapter.g.get(0));assertSame(p,adapter.g.get(0));ServerAdapter.b holder=adapter.onCreateViewHolder(new FrameLayout(themed),0);adapter.onBindViewHolder(holder,0);assertEquals("123 ms",holder.f370B.getText().toString());
 }
 @Test public void inactiveRowIsRemovedInsteadOfRebindingArchivedData()throws Exception{ServerAdapter adapter=new ServerAdapter(app,null);store.restoreRecords(Collections.emptyList(),Collections.emptyList(),ProfileStore.MANUAL_GROUP);adapter.i(p.id);assertEquals(0,adapter.getItemCount());}
 @Test public void existingTickerBroadcastCarriesStartupLatencyAndReceiverRefreshesRow(){
  TunnelVpnService svc=Robolectric.buildService(TunnelVpnService.class).get();svc.profile=p;assertTrue(svc.operations.start(false,ticket->{}));TunnelVpnService.serviceRunning=true;svc.h=svc.new i();success();
  try{
   svc.h.run();Intent event=Shadows.shadowOf((Application)app).getBroadcastIntents().stream().filter(i->i.getIntExtra("ping",0)==87).reduce((a,b)->b).orElseThrow(()->new AssertionError("No startup latency broadcast"));
   assertEquals(p.id,event.getStringExtra("profile_id"));assertTrue(event.hasExtra("uplink"));
   MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();Context themed=new ContextThemeWrapper(app,R.style.AppTheme);activity.z=new ServerAdapter(themed,activity.new C0030l());
   Intent pingOnly=new Intent(event);pingOnly.removeExtra("uplink");activity.new C0027i().onReceive(app,pingOnly);
   ServerAdapter.b holder=activity.z.onCreateViewHolder(new FrameLayout(themed),0);activity.z.onBindViewHolder(holder,0);assertEquals("87 ms",holder.f370B.getText().toString());
  }finally{svc.stopStatsTicker();svc.operations.close();}
 }
}
