package com.parvaz.tunnel.core;

import android.app.Application;

import com.parvaz.tunnel.model.Profile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Reusing a stored measurement is allowed only while it is young AND still belongs to the
 * network it was taken on. Everything else must be measured again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34}, application = Application.class)
public class LatencyCacheTest {

    private static Profile measured(String id, int ping) {
        Profile p = new Profile();
        p.id = id;
        p.protocol = "vless";
        p.address = id + ".invalid";
        p.port = 443;
        p.ping = ping;
        p.latency = LatencyStamp.manual("Google");
        return p;
    }

    private static long now(Profile p) {
        return p.latency.measuredAt;
    }

    @Test
    public void aRecentMeasurementOnTheSameNetworkIsReused() {
        Profile p = measured("a", 120);
        assertTrue(LatencyCache.usable(p, now(p) + 1000L, LatencyCache.DEFAULT_TTL_MS, false));
    }

    @Test
    public void anExpiredMeasurementIsNotReused() {
        Profile p = measured("a", 120);
        assertFalse(LatencyCache.usable(p, now(p) + LatencyCache.DEFAULT_TTL_MS + 1,
                LatencyCache.DEFAULT_TTL_MS, false));
    }

    @Test
    public void aChangedNetworkInvalidatesEvenAFreshMeasurement() {
        Profile p = measured("a", 120);
        assertFalse(LatencyCache.usable(p, now(p), LatencyCache.DEFAULT_TTL_MS, true));
    }

    @Test
    public void stateLabelsAndMissingStampsAreNeverReused() {
        Profile failed = measured("a", -2);
        Profile untested = measured("b", -1);
        Profile noStamp = measured("c", 100);
        noStamp.latency = null;
        long now = System.currentTimeMillis();
        assertFalse(LatencyCache.usable(failed, now, LatencyCache.DEFAULT_TTL_MS, false));
        assertFalse(LatencyCache.usable(untested, now, LatencyCache.DEFAULT_TTL_MS, false));
        assertFalse(LatencyCache.usable(noStamp, now, LatencyCache.DEFAULT_TTL_MS, false));
        assertFalse(LatencyCache.usable(null, now, LatencyCache.DEFAULT_TTL_MS, false));
    }

    @Test
    public void aBackwardsClockIsNotEvidenceOfARecentMeasurement() {
        Profile p = measured("a", 120);
        assertFalse(LatencyCache.usable(p, now(p) - 60000L, LatencyCache.DEFAULT_TTL_MS, false));
    }

    @Test
    public void zeroLifetimeDisablesTheCache() {
        Profile p = measured("a", 120);
        assertFalse(LatencyCache.usable(p, now(p), 0L, false));
    }

    @Test
    public void onlyTheExpiredRowsAreHandedBackForMeasurement() {
        Profile fresh = measured("a", 90);
        Profile old = measured("b", 90);
        long now = now(fresh) + LatencyCache.DEFAULT_TTL_MS - 1000L;
        old.ping = -1;
        List<Profile> pending = LatencyCache.stale(Arrays.asList(fresh, old), now,
                LatencyCache.DEFAULT_TTL_MS, false);
        assertEquals(1, pending.size());
        assertEquals("b", pending.get(0).id);
    }

    @Test
    public void completeOnlyWhenEveryRowIsCovered() {
        Profile a = measured("a", 90);
        Profile b = measured("b", 110);
        long now = now(a) + 1000L;
        assertTrue(LatencyCache.complete(Arrays.asList(a, b), now,
                LatencyCache.DEFAULT_TTL_MS, false));
        b.ping = -1;
        assertFalse(LatencyCache.complete(Arrays.asList(a, b), now,
                LatencyCache.DEFAULT_TTL_MS, false));
        assertFalse(LatencyCache.complete(Collections.<Profile>emptyList(), now,
                LatencyCache.DEFAULT_TTL_MS, false));
        assertFalse(LatencyCache.complete(null, now, LatencyCache.DEFAULT_TTL_MS, false));
    }

    @Test
    public void anUnknownNetworkCountsAsAChangedOne() {
        assertTrue(LatencyCache.scopeChanged("", "123:wifi"));
        assertTrue(LatencyCache.scopeChanged("123:wifi", ""));
        assertTrue(LatencyCache.scopeChanged(null, "123:wifi"));
        assertTrue(LatencyCache.scopeChanged("123:wifi", null));
        assertTrue(LatencyCache.scopeChanged("123:wifi", "124:mobile"));
        assertFalse(LatencyCache.scopeChanged("123:wifi", "123:wifi"));
    }

    @Test
    public void staleOnNullInputIsEmptyNotACrash() {
        assertTrue(LatencyCache.stale(null, System.currentTimeMillis(),
                LatencyCache.DEFAULT_TTL_MS, false).isEmpty());
    }
}
