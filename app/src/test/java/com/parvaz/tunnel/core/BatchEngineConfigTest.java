package com.parvaz.tunnel.core;

import com.parvaz.tunnel.config.BatchEngineConfig;
import com.parvaz.tunnel.config.FullConfig;
import com.parvaz.tunnel.model.Profile;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * One sing-box child process must be able to measure many external-engine servers without
 * ever creating a path that is not the profile's own tunnel.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34})
public class BatchEngineConfigTest {

    private static Profile profile(String id, String protocol) {
        Profile p = new Profile();
        p.id = id;
        p.protocol = protocol;
        p.remark = id;
        p.address = id + ".invalid";
        p.port = 443;
        p.uuid = "11111111-1111-4111-8111-111111111111";
        p.quicKey = "server-password";
        p.sni = id + ".invalid";
        return p;
    }

    private static BatchEngineConfig.Plan plan(Profile... profiles) {
        List<BatchEngineConfig.Member> members = new ArrayList<>();
        int port = 21000;
        for (Profile p : profiles) {
            members.add(new BatchEngineConfig.Member(p, port++));
        }
        return BatchEngineConfig.compose(members);
    }

    @Test
    public void everyAcceptedProfileGetsItsOwnInboundOutboundAndSingleRule() throws Exception {
        BatchEngineConfig.Plan plan = plan(profile("a", "hysteria2"), profile("b", "tuic"),
                profile("c", "anytls"));
        assertEquals(3, plan.ports.size());
        assertTrue(plan.rejected.isEmpty());
        JSONObject root = new JSONObject(plan.config);
        JSONArray inbounds = root.getJSONArray("inbounds");
        JSONArray outbounds = root.getJSONArray("outbounds");
        JSONArray rules = root.getJSONObject("route").getJSONArray("rules");
        assertEquals(3, inbounds.length());
        assertEquals(3, rules.length());
        // Three profile outbounds plus exactly one fail-closed sink.
        assertEquals(4, outbounds.length());

        Set<String> outTags = new HashSet<>();
        for (int i = 0; i < outbounds.length(); i++) {
            outTags.add(outbounds.getJSONObject(i).getString("tag"));
        }
        Set<String> inTags = new HashSet<>();
        for (int i = 0; i < inbounds.length(); i++) {
            JSONObject in = inbounds.getJSONObject(i);
            assertEquals("http", in.getString("type"));
            assertEquals("127.0.0.1", in.getString("listen"));
            inTags.add(in.getString("tag"));
        }
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            assertEquals(1, rule.getJSONArray("inbound").length());
            assertTrue(inTags.contains(rule.getJSONArray("inbound").getString(0)));
            assertTrue(outTags.contains(rule.getString("outbound")));
        }
        assertEquals(3, inTags.size());
    }

    @Test
    public void noDirectPathExistsAndUnmatchedTrafficIsDropped() throws Exception {
        JSONObject root = new JSONObject(plan(profile("a", "hysteria2")).config);
        JSONArray outbounds = root.getJSONArray("outbounds");
        for (int i = 0; i < outbounds.length(); i++) {
            String type = outbounds.getJSONObject(i).getString("type");
            assertFalse("direct".equals(type));
            assertFalse("freedom".equals(type));
            assertFalse("socks".equals(type));
        }
        assertEquals(BatchEngineConfig.SINK, root.getJSONObject("route").getString("final"));
        assertFalse(root.getJSONObject("route").getBoolean("auto_detect_interface"));
    }

    @Test
    public void theOutboundIsTheSameOneTheLiveTunnelWouldDial() throws Exception {
        Profile hy = profile("a", "hysteria2");
        hy.mode = "salamander";
        hy.host = "obfs-secret";
        JSONObject root = new JSONObject(plan(hy).config);
        JSONObject out = root.getJSONArray("outbounds").getJSONObject(0);
        assertEquals("hysteria2", out.getString("type"));
        assertEquals(hy.uuid, out.getString("password"));
        assertEquals("obfs-secret", out.getJSONObject("obfs").getString("password"));
        assertEquals("a.invalid", out.getJSONObject("tls").getString("server_name"));
    }

    @Test
    public void protocolAliasesAreAcceptedUnderTheirCanonicalType() throws Exception {
        BatchEngineConfig.Plan plan = plan(profile("a", "hy2"), profile("b", "hy"));
        assertEquals(2, plan.ports.size());
        JSONArray outbounds = new JSONObject(plan.config).getJSONArray("outbounds");
        assertEquals("hysteria2", outbounds.getJSONObject(0).getString("type"));
        assertEquals("hysteria", outbounds.getJSONObject(1).getString("type"));
    }

    @Test
    public void fullConfigurationsAndXrayProtocolsAreTurnedAway() {
        Profile vless = profile("x", "vless");
        Profile clash = profile("y", "full-clash");
        Profile sing = profile("z", "full-singbox");
        BatchEngineConfig.Plan plan = plan(vless, clash, sing);
        assertTrue(plan.isEmpty());
        assertEquals(3, plan.rejected.size());
        assertFalse(BatchEngineConfig.shareable(vless));
        assertFalse(BatchEngineConfig.shareable(clash));
        assertFalse(BatchEngineConfig.shareable(sing));
        assertFalse(BatchEngineConfig.shareable(null));
        assertTrue(BatchEngineConfig.shareable(profile("a", "snell")));
        assertTrue(FullConfig.isFull("full-clash"));
    }

    @Test
    public void duplicateIdsAreMeasuredOnceAndDuplicatePortsAreRejected() {
        Profile first = profile("a", "hysteria2");
        Profile again = profile("a", "tuic");
        BatchEngineConfig.Plan plan = plan(first, again);
        assertEquals(1, plan.ports.size());

        BatchEngineConfig.Plan clash = BatchEngineConfig.compose(Arrays.asList(
                new BatchEngineConfig.Member(profile("a", "hysteria2"), 21000),
                new BatchEngineConfig.Member(profile("b", "tuic"), 21000)));
        assertEquals(1, clash.ports.size());
        assertEquals(Collections.singletonList("b"), clash.rejected);
    }

    @Test
    public void impossiblePortsAndEmptyInputProduceAnEmptyPlan() {
        assertTrue(BatchEngineConfig.compose(null).isEmpty());
        assertTrue(BatchEngineConfig.compose(Collections.<BatchEngineConfig.Member>emptyList())
                .isEmpty());
        BatchEngineConfig.Plan bad = BatchEngineConfig.compose(Collections.singletonList(
                new BatchEngineConfig.Member(profile("a", "tuic"), 0)));
        assertTrue(bad.isEmpty());
        assertEquals(Collections.singletonList("a"), bad.rejected);
        assertEquals("", bad.config);
    }

    @Test
    public void aSnellMemberCarriesNoTlsSectionInTheSharedConfig() throws Exception {
        Profile snell = profile("a", "snell");
        snell.mode = "4";
        JSONObject out = new JSONObject(plan(snell).config)
                .getJSONArray("outbounds").getJSONObject(0);
        assertEquals("snell", out.getString("type"));
        assertFalse(out.has("tls"));
        assertEquals(4, out.getInt("version"));
    }

    @Test
    public void theSharedConfigResolvesNamesThroughItsOwnBootstrapServer() throws Exception {
        JSONObject root = new JSONObject(plan(profile("a", "tuic")).config);
        assertNotNull(root.getJSONObject("dns"));
        assertEquals("bootstrap", root.getJSONObject("route")
                .getJSONObject("default_domain_resolver").getString("server"));
        assertTrue(root.getJSONObject("log").getBoolean("disabled"));
    }
}
