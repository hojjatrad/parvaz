package com.parvaz.tunnel.core;

import com.parvaz.tunnel.config.EngineConfig;
import com.parvaz.tunnel.config.ReadinessConfig;
import com.parvaz.tunnel.model.Profile;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression for a v1.31.0 defect: Hysteria v1, AnyTLS and Snell were added as engine
 * protocols but never added to the readiness "reaches the remote server" list, so their
 * tunnels could not be pinned and therefore could never turn green or report a latency.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34})
public class EngineReadinessTest {

    private static Profile profile(String protocol) {
        Profile p = new Profile();
        p.id = "p";
        p.protocol = protocol;
        p.address = "example.invalid";
        p.port = 443;
        p.uuid = "secret";
        p.quicKey = "secret";
        return p;
    }

    private static String relayConfig() {
        return "{\"inbounds\":[{\"tag\":\"app\",\"protocol\":\"socks\",\"port\":10810}],"
                + "\"outbounds\":[{\"protocol\":\"socks\",\"tag\":\"proxy\"}]}";
    }

    @Test
    public void everyEngineProtocolIsRecognisedAsReachingTheRemoteServer() throws Exception {
        for (String protocol : new String[]{"hysteria2", "tuic", "hysteria", "anytls", "snell"}) {
            JSONObject engine = EngineConfig.build(profile(protocol), 10810, 10853, "u", "p");
            assertTrue(protocol + " must be verifiable",
                    ReadinessConfig.nativeRemoteOnly(engine, protocol));
        }
    }

    @Test
    public void aVerifiableEngineGetsItsOwnPinnedReadinessInbound() throws Exception {
        for (String protocol : new String[]{"hysteria", "anytls", "snell"}) {
            ReadinessConfig.Plan pinned = ReadinessConfig.prepare(relayConfig(),
                    profile(protocol), true, 19000);
            assertTrue(protocol + " must be pinned", pinned.pinned);
            JSONObject root = new JSONObject(pinned.config);
            assertEquals(2, root.getJSONArray("inbounds").length());
            assertEquals("proxy", root.getJSONObject("routing").getJSONArray("rules")
                    .getJSONObject(0).getString("outboundTag"));
        }
    }

    @Test
    public void anUnprovenEngineStillFailsClosedInsteadOfClaimingAVerifiedRoute()
            throws Exception {
        ReadinessConfig.Plan plan = ReadinessConfig.prepare(relayConfig(),
                profile("anytls"), false, 19000);
        assertFalse(plan.pinned);
        assertEquals(relayConfig(), plan.config);
    }
}
