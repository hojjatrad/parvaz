package com.parvaz.tunnel.core;
import android.app.Application;import android.content.Context;import androidx.test.core.app.ApplicationProvider;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={29,34},application=Application.class)
public class SafeModeTest {
 Context context;
 @Before public void clear(){context=ApplicationProvider.getApplicationContext();context.getSharedPreferences("parvaz_safemode",0).edit().clear().commit();SafeMode.sTrippedThisRun=false;}
 @Test public void successfulBackgroundStartsNeverCountAsFailedUiLaunches(){for(int i=0;i<8;i++){SafeMode.beginProcess(context);SafeMode.completeProcess(context);assertFalse(SafeMode.sTrippedThisRun);}assertEquals(0,context.getSharedPreferences("parvaz_safemode",0).getInt("pending_launches",0));}
 @Test public void repeatedFailedForegroundLaunchesStillTrip(){for(int i=0;i<3;i++){SafeMode.beginProcess(context);SafeMode.completeProcess(context);SafeMode.beginForegroundLaunch(context);}assertTrue(SafeMode.sTrippedThisRun);}
 @Test public void repeatedFailedInitializationStillTrips(){for(int i=0;i<3;i++)SafeMode.beginProcess(context);assertTrue(SafeMode.sTrippedThisRun);}
 @Test public void healthyUiResetsPersistentCounters(){SafeMode.beginProcess(context);SafeMode.completeProcess(context);SafeMode.beginForegroundLaunch(context);SafeMode.markHealthy(context);SafeMode.beginProcess(context);SafeMode.completeProcess(context);SafeMode.beginForegroundLaunch(context);assertFalse(SafeMode.sTrippedThisRun);}
}
