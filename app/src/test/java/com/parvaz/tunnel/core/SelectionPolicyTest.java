package com.parvaz.tunnel.core;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileStore;
import com.parvaz.tunnel.store.SelectionPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A server the user picks on purpose must still be the one used after the app is closed
 * and reopened, without weakening the automatic last-verified-connection default.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34}, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SelectionPolicyTest {

    private Context app;
    private ProfileStore store;
    private SharedPreferences prefs;
    private Profile a;
    private Profile b;
    private CoreManager manager;
    private StartupDiagnostics.Attempt trace;

    @Before
    public void setup() {
        app = ApplicationProvider.getApplicationContext();
        ProfileStore.d = null;
        CoreManager.c = null;
        TunnelVpnService.serviceRunning = false;
        app.getSharedPreferences("parvaz_store", 0).edit().clear().commit();
        prefs = app.getSharedPreferences("parvaz_prefs", 0);
        prefs.edit().clear().commit();
        store = ProfileStore.f(app);
        a = profile("a");
        b = profile("b");
        store.restoreRecords(Arrays.asList(a, b), Collections.emptyList(), ProfileStore.MANUAL_GROUP);
        a = store.getActiveById("a");
        b = store.getActiveById("b");
        manager = CoreManager.b();
    }

    @After
    public void cleanup() {
        manager.stop();
        CoreManager.c = null;
        ProfileStore.d = null;
        TunnelVpnService.serviceRunning = false;
    }

    private Profile profile(String id) {
        Profile p = new Profile();
        p.id = id;
        p.protocol = "vless";
        p.address = "fixture.invalid";
        p.port = 443;
        p.uuid = "11111111-1111-4111-8111-111111111111";
        return p;
    }

    /** Records a real verified connection for the given profile, as the tunnel would. */
    private void connected(Profile p) {
        AtomicLong now = new AtomicLong();
        trace = StartupDiagnostics.begin(0, 0, now::get);
        trace.routeConfigured(true);
        trace.coreStarted();
        ReflectionHelpers.setField(manager, "diagnostics", trace);
        ReflectionHelpers.setField(manager, "verifiedPort", 12345);
        ReflectionHelpers.setField(manager, "startupLatency", store.captureStartupLatency(p));
        ReflectionHelpers.setField(manager, "livePrefs", prefs);
        ReflectionHelpers.setField(manager, "liveStore", store);
        ReflectionHelpers.setField(manager, "rememberedDefault", false);
        manager.running = true;
        trace.probeFinished(true, 80);
        manager.publishStartupLatency(store, manager.sessionId());
    }

    @Test
    public void pinnedServerSurvivesRestartAndBeatsLastConnected() {
        connected(a);
        assertTrue(SelectionPolicy.pin(app, b));
        assertEquals("b", prefs.getString("selected_profile", ""));
        prefs.edit().putString("selected_profile", "").commit(); // process restart
        assertTrue(SelectionPolicy.restore(app));
        assertEquals("b", prefs.getString("selected_profile", ""));
    }

    @Test
    public void withoutAPinTheLastVerifiedConnectionIsStillRestored() {
        connected(a);
        prefs.edit().putString("selected_profile", "b").commit();
        assertTrue(SelectionPolicy.restore(app));
        assertEquals("a", prefs.getString("selected_profile", ""));
    }

    @Test
    public void clearingThePinHandsControlBackToTheAutomaticDefault() {
        connected(a);
        SelectionPolicy.pin(app, b);
        assertTrue(SelectionPolicy.clearPin(app));
        assertTrue(SelectionPolicy.restore(app));
        assertEquals("a", prefs.getString("selected_profile", ""));
    }

    @Test
    public void automaticModeIgnoresAStalePin() {
        connected(a);
        SelectionPolicy.pin(app, b);
        prefs.edit().putBoolean(SelectionPolicy.KEY_AUTO_BEST, true).commit();
        assertTrue(SelectionPolicy.restore(app));
        assertEquals("a", prefs.getString("selected_profile", ""));
    }

    @Test
    public void editedCredentialsInvalidateThePin() {
        SelectionPolicy.pin(app, b);
        b.uuid = "22222222-2222-4222-8222-222222222222";
        assertNull(SelectionPolicy.pinned(store, prefs));
        assertFalse(SelectionPolicy.restore(app));
    }

    @Test
    public void renamingTheSameServerKeepsThePin() {
        SelectionPolicy.pin(app, b);
        b.remark = "renamed";
        prefs.edit().putString("selected_profile", "a").commit();
        assertTrue(SelectionPolicy.restore(app));
        assertEquals("b", prefs.getString("selected_profile", ""));
    }

    @Test
    public void aDeletedPinnedServerIsNotResurrected() throws Exception {
        SelectionPolicy.pin(app, b);
        store.restoreRecords(Collections.singletonList(a), Collections.emptyList(),
                ProfileStore.MANUAL_GROUP);
        assertNull(SelectionPolicy.pinned(store, prefs));
        prefs.edit().putString("selected_profile", "a").commit();
        assertFalse(SelectionPolicy.restore(app));
        assertEquals("a", prefs.getString("selected_profile", ""));
    }

    @Test
    public void archivedSourceCannotBeReachedThroughThePin() {
        SelectionPolicy.pin(app, b);
        b.subscriptionId = "archived";
        assertNull(SelectionPolicy.pinned(store, prefs));
    }

    @Test
    public void malformedPinRecordsAreIgnoredSafely() {
        prefs.edit().putString(SelectionPolicy.KEY_PIN, "{not json").commit();
        assertNull(SelectionPolicy.pinned(store, prefs));
        assertEquals("", SelectionPolicy.pinnedId(app));
        prefs.edit().putString(SelectionPolicy.KEY_PIN, "{\"schema\":9,\"id\":\"b\"}").commit();
        assertNull(SelectionPolicy.pinned(store, prefs));
        assertEquals("", SelectionPolicy.pinnedId(app));
    }

    @Test
    public void pinIsVisibleToTheRowWithoutAFullIdentityCheck() {
        SelectionPolicy.pin(app, b);
        assertEquals("b", SelectionPolicy.pinnedId(app));
        assertTrue(SelectionPolicy.isPinned(app, "b"));
        assertFalse(SelectionPolicy.isPinned(app, "a"));
        assertFalse(SelectionPolicy.isPinned(app, ""));
    }

    @Test
    public void networkChangeDoesNotDropTheManualChoice() {
        SelectionPolicy.pin(app, b);
        NetworkEpoch.changed();
        prefs.edit().putString("selected_profile", "").commit();
        assertTrue(SelectionPolicy.restore(app));
        assertEquals("b", prefs.getString("selected_profile", ""));
    }
}
