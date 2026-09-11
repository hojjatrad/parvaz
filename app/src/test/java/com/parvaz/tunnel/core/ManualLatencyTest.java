package com.parvaz.tunnel.core;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ManualLatencyTest {
 private Context app;private ProfileStore store;private final List<PingManager> managers=new ArrayList<>();private ExecutorService workers;
 @Before public void setup(){app=ApplicationProvider.getApplicationContext();ProfileStore.d=null;app.getSharedPreferences("parvaz_store",0).edit().clear().commit();app.getSharedPreferences("parvaz_prefs",0).edit().clear().commit();store=ProfileStore.f(app);workers=Executors.newFixedThreadPool(3);}
 @After public void cleanup()throws Exception{for(PingManager m:managers)m.close();workers.shutdownNow();assertTrue(workers.awaitTermination(4,TimeUnit.SECONDS));idle();ProfileStore.d=null;}
 private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 private List<Profile> profiles(int count){List<Profile> all=new ArrayList<>();for(int i=0;i<count;i++){Profile p=new Profile();p.id="fixture-"+i;p.protocol="vless";p.address="fixture.invalid";p.uuid="11111111-1111-4111-8111-111111111111";all.add(p);}try{store.restoreRecords(all,Collections.emptyList(),ProfileStore.MANUAL_GROUP);}catch(org.json.JSONException e){throw new AssertionError(e);}return store.activeProfiles();}
 private PingManager manager(PingManager.Measurer measure){PingManager m=new PingManager(app,workers,measure);managers.add(m);return m;}
 private static class Listener implements PingManager.Listener{int results,finished;boolean cancelled;public void onResult(String id){results++;}public void onFinished(boolean cancelled){finished++;this.cancelled=cancelled;}}
 private void until(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()&&System.nanoTime()<end){idle();Thread.sleep(2);}idle();assertTrue("Callback did not finish",condition.getAsBoolean());}
 @Test public void batchMeasuresEveryRowAndPublishesBeforeComplete()throws Exception{
  List<Profile> rows=profiles(8);AtomicInteger calls=new AtomicInteger();PingManager m=manager(p->{calls.incrementAndGet();return 120;});Listener listener=new Listener();assertTrue(m.startBatch(rows,listener));assertFalse(m.startBatch(rows,new Listener()));until(()->listener.finished==1);assertEquals(8,calls.get());assertEquals(8,listener.results);for(Profile p:rows)assertEquals(120,p.ping);assertFalse(m.isBatchBusy());
 }
 @Test public void unknownAndExceptionGetVisibleTerminalStates()throws Exception{
  List<Profile> rows=profiles(4);PingManager m=manager(p->{if(p.id.endsWith("0"))return -2;if(p.id.endsWith("1"))throw new IllegalStateException("fixture");if(p.id.endsWith("2"))return ProbeAdmission.BUSY;return -1;});Listener l=new Listener();m.startBatch(rows,l);until(()->l.finished==1);assertEquals(LatencyResult.UNCONFIRMED,rows.get(0).ping);assertEquals(LatencyResult.UNCONFIRMED,rows.get(1).ping);assertEquals(LatencyResult.BUSY,rows.get(2).ping);assertEquals(LatencyResult.FAILED,rows.get(3).ping);
 }
 @Test public void cancellationClearsQueuedAndRunningRowsWithoutLateOverwrite()throws Exception{
  List<Profile> rows=profiles(7);CountDownLatch entered=new CountDownLatch(3),release=new CountDownLatch(1);PingManager m=manager(p->{entered.countDown();while(true){try{release.await();break;}catch(InterruptedException ignored){}}return 999;});Listener l=new Listener();m.startBatch(rows,l);
  try{assertTrue(entered.await(3,TimeUnit.SECONDS));m.cancelBatch();until(()->l.finished==1);assertTrue(l.cancelled);for(Profile p:rows)assertEquals(LatencyResult.CANCELLED,p.ping);}finally{release.countDown();}
  workers.shutdown();assertTrue(workers.awaitTermination(3,TimeUnit.SECONDS));idle();assertEquals(1,l.finished);for(Profile p:rows)assertEquals(LatencyResult.CANCELLED,p.ping);
 }
 @Test public void olderSingleCannotOverwriteNewerSingle()throws Exception{
  Profile p=profiles(1).get(0);CountDownLatch first=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();PingManager m=manager(ignored->{if(calls.incrementAndGet()==1){first.countDown();while(true){try{release.await();break;}catch(InterruptedException e){}}return 900;}return 60;});Listener a=new Listener(),b=new Listener();m.testOne(p,a);
  try{assertTrue(first.await(2,TimeUnit.SECONDS));m.testOne(p,b);until(()->b.finished==1);assertEquals(60,p.ping);}finally{release.countDown();}workers.shutdown();assertTrue(workers.awaitTermination(3,TimeUnit.SECONDS));idle();assertEquals(60,p.ping);
 }
 @Test public void networkChangeRejectsOldMeasurement()throws Exception{
  Profile p=profiles(1).get(0);PingManager m=manager(ignored->{NetworkEpoch.changed();return 100;});Listener l=new Listener();m.testOne(p,l);until(()->l.finished==1);assertEquals(LatencyResult.UNCONFIRMED,p.ping);
 }
 @Test public void changedCredentialAndSupersededTicketsCannotPublish(){
  Profile p=profiles(1).get(0);ProfileStore.Measurement a=store.beginMeasurement(p),b=store.beginMeasurement(p);assertFalse(store.finishMeasurement(a,900));assertTrue(store.ownsMeasurement(b));p.uuid="22222222-2222-4222-8222-222222222222";store.finishMeasurement(b,90);assertEquals(LatencyResult.CANCELLED,p.ping);
 }
 @Test public void newerLiveResultWinsOverPendingManualTest(){Profile p=profiles(1).get(0);ProfileStore.Measurement m=store.beginMeasurement(p);store.i(p.id,41);assertFalse(store.finishMeasurement(m,900));assertEquals(41,p.ping);}
 @Test public void savingLatencyDoesNotInvalidateOtherMeasurements(){List<Profile> p=profiles(2);ProfileStore.Measurement a=store.beginMeasurement(p.get(0)),b=store.beginMeasurement(p.get(1));assertTrue(store.finishMeasurement(a,120));store.saveMeasurements();assertTrue(store.ownsMeasurement(b));assertTrue(store.finishMeasurement(b,130));store.saveMeasurements();ProfileStore reopened=new ProfileStore(app);assertEquals(120,reopened.getById(p.get(0).id).ping);}
 @Test public void sourceRevisionChangeCancelsItsPendingMarker(){Profile p=profiles(1).get(0);ProfileStore.Measurement m=store.beginMeasurement(p);store.h();assertFalse(store.ownsMeasurement(m));store.finishMeasurement(m,50);assertEquals(LatencyResult.CANCELLED,p.ping);}
 @Test public void destroyedManagerDoesNotCallDeadActivity()throws Exception{Profile p=profiles(1).get(0);Listener l=new Listener();PingManager m=manager(ignored->100);m.testOne(p,l);m.close();workers.shutdown();assertTrue(workers.awaitTermination(3,TimeUnit.SECONDS));idle();assertEquals(0,l.finished);assertEquals(0,l.results);}
 @Test public void queuedUiPublicationRechecksNetwork()throws Exception{Profile p=profiles(1).get(0);Listener l=new Listener();PingManager m=manager(ignored->82);m.testOne(p,l);workers.shutdown();assertTrue(workers.awaitTermination(3,TimeUnit.SECONDS));assertEquals(0,l.finished);NetworkEpoch.changed();idle();assertEquals(LatencyResult.UNCONFIRMED,p.ping);}

}
