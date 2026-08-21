package com.parvaz.tunnel.smoke;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import com.parvaz.tunnel.AutoProfileActivity;
import com.parvaz.tunnel.DomainRulesActivity;
import com.parvaz.tunnel.LeakTestActivity;
import com.parvaz.tunnel.AppPickerActivity;
import com.parvaz.tunnel.LogActivity;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.RulesActivity;
import com.parvaz.tunnel.SettingsActivity;
import com.parvaz.tunnel.UsageActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Every user-reachable screen must survive create -> start -> resume. This is the
 * regression net for the v1.8 launch crash, where jadx had left four methods as
 * "Method not decompiled" stubs that threw UnsupportedOperationException at runtime.
 * Plain JVM unit tests never executed those paths, so nothing caught it before release.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 34}, application = com.parvaz.tunnel.App.class)
public class AllActivitiesSmokeTest {

    @Before
    public void grantSelfPermissions() {
        Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext())
                .grantPermissions("com.parvaz.tunnel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
    }

    private static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private <T extends Activity> void launch(Class<T> clazz) {
        try {
            ActivityController<T> c = Robolectric.buildActivity(clazz);
            c.create().start().resume();
            c.pause().stop().destroy();
        } catch (Throwable t) {
            System.out.println("=== CRASH in " + clazz.getSimpleName() + " ===");
            System.out.println(trace(t));
            fail(clazz.getSimpleName() + " crashed: " + t);
        }
    }

    @Test public void main()      { launch(MainActivity.class); }
    @Test public void settings()  { launch(SettingsActivity.class); }
    @Test public void log()       { launch(LogActivity.class); }
    @Test public void usage()     { launch(UsageActivity.class); }
    @Test public void rules()     { launch(RulesActivity.class); }
    @Test public void appPicker() { launch(AppPickerActivity.class); }

    // New in 1.15. An unregistered activity or a bad layout id compiles fine
    // and only blows up at startActivity, so each new screen gets inflated here.
    @Test public void leakTest()    { launch(LeakTestActivity.class); }
    @Test public void domainRules() { launch(DomainRulesActivity.class); }
    @Test public void autoProfile() { launch(AutoProfileActivity.class); }
}
