package com.parvaz.tunnel.smoke;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.parvaz.tunnel.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Reproduces an on-device launch crash without a device. Robolectric runs the real
 * Application.onCreate and the real Activity lifecycle against the real merged
 * resources, so a bad layout, a missing resource, a static-initialiser blow-up or an
 * NPE in onCreate all surface here exactly as they would on the phone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 29, 34}, application = com.parvaz.tunnel.App.class)
public class LaunchSmokeTest {

    private static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @Test
    public void applicationStarts() {
        Application app = ApplicationProvider.getApplicationContext();
        assertNotNull(app);
        System.out.println("APP OK on sdk " + Build.VERSION.SDK_INT + " -> " + app.getClass());
    }

    @Test
    public void mainActivityReachesResumed() {
        // The app declares (and uses) the signature-level
        // com.parvaz.tunnel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION that androidx
        // adds for RECEIVER_NOT_EXPORTED. A real device auto-grants a self-declared
        // signature permission at install time; Robolectric's shadow PackageManager
        // does not, so grant it explicitly to model real install behaviour.
        org.robolectric.Shadows.shadowOf(
                (Application) ApplicationProvider.getApplicationContext())
                .grantPermissions("com.parvaz.tunnel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");

        ActivityController<MainActivity> c = null;
        try {
            c = Robolectric.buildActivity(MainActivity.class);
            c.create();
            System.out.println("CREATE OK sdk " + Build.VERSION.SDK_INT);
            c.start();
            c.resume();
            System.out.println("RESUME OK sdk " + Build.VERSION.SDK_INT);
        } catch (Throwable t) {
            System.out.println("=== LAUNCH CRASH on sdk " + Build.VERSION.SDK_INT + " ===");
            System.out.println(trace(t));
            fail("MainActivity launch crashed on sdk " + Build.VERSION.SDK_INT + ": " + t);
        }
    }
}
