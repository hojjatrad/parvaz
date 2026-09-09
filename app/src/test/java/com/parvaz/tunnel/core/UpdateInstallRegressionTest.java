package com.parvaz.tunnel.core;
import android.app.Application;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class UpdateInstallRegressionTest {
    @Test public void archiveCertificateCollectionRequestsLegacyAndModernFlags() {
        assertTrue((ArchiveSignatures.flags()&android.content.pm.PackageManager.GET_SIGNATURES)!=0);
        assertTrue((ArchiveSignatures.flags()&android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!=0);
    }
    @Test public void installIntentGrantsContentUriAndAsksForResult() {
        Intent intent=UpdateInstallActivity.packageIntent(Uri.parse("content://com.parvaz.tunnel.fileprovider/internal_cache/updates/parvaz-1.22.apk"));
        assertEquals(Intent.ACTION_INSTALL_PACKAGE,intent.getAction());assertEquals("content",intent.getData().getScheme());
        assertTrue((intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
        assertEquals(intent.getData(),intent.getClipData().getItemAt(0).getUri());
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT,false));
    }
    @Test public void deniedPermissionOpensSettingsAndRetainsInstallerScreen() {
        Context context=ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(false);
        try(ActivityController<UpdateInstallActivity> controller=Robolectric.buildActivity(UpdateInstallActivity.class).create().start().resume()) {
            Intent launched=Shadows.shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull(launched);assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,launched.getAction());
            assertEquals("package:com.parvaz.tunnel",launched.getDataString());assertFalse(controller.get().isFinishing());
        }
    }
    @Test public void secretExceptionTextNeverAppearsInUpdateReport() {
        assertEquals("UPDATE_IO_OR_PLATFORM_ERROR",UpdateChecker.safeError(new Exception("https://private.invalid/token")));
        assertEquals("UPDATE_ARCHIVE_UNREADABLE",UpdateChecker.safeError(new Exception("UPDATE_ARCHIVE_UNREADABLE")));
    }
}
