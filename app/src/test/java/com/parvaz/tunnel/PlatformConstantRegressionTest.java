package com.parvaz.tunnel;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Keep decompiled-literal cleanup byte-equivalent at the Android API boundary. */
public class PlatformConstantRegressionTest {
    @Test public void visibilityValuesAreUnchanged() {
        assertEquals(0, android.view.View.VISIBLE);
        assertEquals(4, android.view.View.INVISIBLE);
        assertEquals(8, android.view.View.GONE);
    }
    @Test public void pendingIntentsRemainImmutableAndUpdateExistingExtras() {
        assertEquals(201326592, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
    }
    @Test public void activityFlagsPreserveTheirOriginalCombinations() {
        assertEquals(268468224, android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertEquals(276856832, android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK | android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        assertEquals(335544320, android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        assertEquals(268435456, android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    @Test public void biometricPolicyIsNotWeakened() {
        assertEquals(33023, androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
    }
    @Test public void platformServiceAndToastValuesAreUnchanged() {
        assertEquals("clipboard", android.content.Context.CLIPBOARD_SERVICE);
        assertEquals("connectivity", android.content.Context.CONNECTIVITY_SERVICE);
        assertEquals("notification", android.content.Context.NOTIFICATION_SERVICE);
        assertEquals(0, android.widget.Toast.LENGTH_SHORT);
        assertEquals(0, android.content.pm.PackageManager.PERMISSION_GRANTED);
    }
}
