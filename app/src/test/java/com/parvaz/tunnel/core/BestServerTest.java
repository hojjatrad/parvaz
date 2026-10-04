package com.parvaz.tunnel.core;

import android.app.Application;

import com.parvaz.tunnel.model.Profile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Automatic selection must only ever believe a measured number. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34}, application = Application.class)
public class BestServerTest {

    private static Profile profile(String id, int ping) {
        Profile p = new Profile();
        p.id = id;
        p.protocol = "vless";
        p.address = id + ".invalid";
        p.port = 443;
        p.uuid = "11111111-1111-4111-8111-111111111111";
        p.ping = ping;
        p.latency = LatencyStamp.manual("Google");
        return p;
    }

    @Test
    public void lowestMeasuredLatencyWins() {
        List<Profile> list = Arrays.asList(profile("a", 300), profile("b", 120), profile("c", 900));
        assertEquals("b", BestServer.choose(list, Collections.<String>emptySet()).id);
    }

    @Test
    public void stateLabelsAreNeverTreatedAsFastServers() {
        List<Profile> list = new ArrayList<>();
        for (int state : new int[]{LatencyResult.UNTESTED, LatencyResult.FAILED,
                LatencyResult.TESTING, LatencyResult.UNCONFIRMED, LatencyResult.BUSY,
                LatencyResult.CANCELLED, LatencyResult.TIMEOUT, LatencyResult.TLS_ERROR,
                LatencyResult.HTTP_ERROR, LatencyResult.NETWORK_ERROR, LatencyResult.START_ERROR,
                LatencyResult.ROUTE_UNVERIFIED, LatencyResult.NETWORK_CHANGED}) {
            list.add(profile("s" + state, state));
        }
        assertNull(BestServer.choose(list, Collections.<String>emptySet()));
        list.add(profile("real", 480));
        assertEquals("real", BestServer.choose(list, Collections.<String>emptySet()).id);
    }

    @Test
    public void favouriteGetsOnlyASmallHeadStart() {
        List<Profile> list = Arrays.asList(profile("fav", 150), profile("plain", 100));
        assertEquals("fav", BestServer.choose(list, new HashSet<>(Arrays.asList("fav"))).id);
        List<Profile> far = Arrays.asList(profile("fav", 400), profile("plain", 100));
        assertEquals("plain", BestServer.choose(far, new HashSet<>(Arrays.asList("fav"))).id);
    }

    @Test
    public void unsupportedProtocolsAreNotChosenEvenWhenFast() {
        Profile unsupported = profile("hy", 10);
        unsupported.protocol = "hysteria2";
        List<Profile> list = Arrays.asList(unsupported, profile("ok", 700));
        assertEquals("ok", BestServer.choose(list, Collections.<String>emptySet()).id);
    }

    @Test
    public void historicValuesRankBehindFreshOnes() {
        Profile stale = profile("stale", 100);
        stale.latency = null; // No stamp at all: a stored number from an earlier run.
        Profile fresh = profile("fresh", 300);
        assertEquals("fresh", BestServer.choose(Arrays.asList(stale, fresh),
                Collections.<String>emptySet()).id);
    }

    @Test
    public void measurementIsRequiredUntilSomethingFreshExists() {
        assertTrue(BestServer.needsMeasurement(null));
        assertTrue(BestServer.needsMeasurement(Collections.<Profile>emptyList()));
        Profile untested = profile("u", LatencyResult.UNTESTED);
        assertTrue(BestServer.needsMeasurement(Collections.singletonList(untested)));
        Profile noStamp = profile("n", 120);
        noStamp.latency = null;
        assertTrue(BestServer.needsMeasurement(Collections.singletonList(noStamp)));
        assertFalse(BestServer.needsMeasurement(Collections.singletonList(profile("f", 120))));
    }

    @Test
    public void networkChangeInvalidatesFreshness() {
        Profile measured = profile("a", 120);
        assertFalse(BestServer.needsMeasurement(Collections.singletonList(measured)));
        NetworkEpoch.changed();
        assertTrue(BestServer.needsMeasurement(Collections.singletonList(measured)));
    }
}
