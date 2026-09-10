package com.parvaz.tunnel.core;
import android.app.Application;import android.content.Context;import androidx.test.core.app.ApplicationProvider;
import androidx.work.*;import androidx.work.testing.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={29,34},application=Application.class)
public class AppUpdatePolicyTest {
 @Test public void periodicChecksAreSixHoursAndNotUnconditionalRadioWakeups(){PeriodicWorkRequest r=AppUpdateWorker.checkRequest();assertEquals(21600000L,r.getWorkSpec().intervalDuration);assertEquals(NetworkType.CONNECTED,r.getWorkSpec().constraints.getRequiredNetworkType());assertTrue(r.getWorkSpec().constraints.requiresBatteryNotLow());}
 @Test public void automaticDownloadRequiresUnmeteredChargingAndStorage(){OneTimeWorkRequest r=AppUpdateWorker.downloadRequest();Constraints c=r.getWorkSpec().constraints;assertEquals(NetworkType.UNMETERED,c.getRequiredNetworkType());assertTrue(c.requiresCharging());assertTrue(c.requiresBatteryNotLow());assertTrue(c.requiresStorageNotLow());assertTrue(r.getWorkSpec().input.getBoolean("download",false));}
 @Test public void disablingAutomaticChecksSkipsBackgroundNetworkWork(){Context c=ApplicationProvider.getApplicationContext();c.getSharedPreferences("parvaz_prefs",0).edit().putBoolean("background_updates",false).commit();AppUpdateWorker w=TestWorkerBuilder.from(c,AppUpdateWorker.class,Runnable::run).build();assertEquals(ListenableWorker.Result.success(),w.doWork());}
 @Test public void jitterPenalizesUnstableHistory(){ServerMemory.Entry a=new ServerMemory.Entry(),b=new ServerMemory.Entry();a.successes=b.successes=20;a.failures=b.failures=1;a.avgLatency=b.avgLatency=200;a.jitter=400;b.jitter=10;assertTrue(b.score()>a.score());}
}
