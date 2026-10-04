package com.parvaz.tunnel.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The shared-core probe config must pin every row to its own outbound and nothing else. */
public class BatchProbeConfigTest {

    private static String single(String tag, String protocol, String address) {
        JSONObject proxy = new JSONObject();
        try {
            proxy.put("tag", tag).put("protocol", protocol)
                    .put("settings", new JSONObject().put("address", address));
            JSONArray outbounds = new JSONArray().put(proxy)
                    .put(new JSONObject().put("tag", "direct").put("protocol", "freedom"))
                    .put(new JSONObject().put("tag", "block").put("protocol", "blackhole"));
            return new JSONObject()
                    .put("inbounds", new JSONArray().put(new JSONObject().put("tag", "socks")))
                    .put("outbounds", outbounds)
                    .put("routing", new JSONObject().put("rules", new JSONArray()
                            .put(new JSONObject().put("outboundTag", "direct"))))
                    .toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static BatchProbeConfig.Member member(String id, int port) {
        return new BatchProbeConfig.Member(id, single("proxy", "vless", id + ".invalid"), port);
    }

    @Test
    public void everyAcceptedProfileGetsItsOwnInboundOutboundAndRule() throws Exception {
        BatchProbeConfig.Plan plan = BatchProbeConfig.compose(
                Arrays.asList(member("a", 11001), member("b", 11002), member("c", 11003)));
        assertEquals(3, plan.ports.size());
        assertTrue(plan.rejected.isEmpty());
        JSONObject root = new JSONObject(plan.config);
        assertEquals(3, root.getJSONArray("inbounds").length());
        assertEquals(3, root.getJSONArray("outbounds").length());
        JSONArray rules = root.getJSONObject("routing").getJSONArray("rules");
        assertEquals(3, rules.length());
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            assertEquals(1, rule.getJSONArray("inboundTag").length());
            String inbound = rule.getJSONArray("inboundTag").getString(0);
            String outbound = rule.getString("outboundTag");
            assertEquals(inbound.replace("in-", "out-"), outbound);
        }
        // Each port appears exactly once, on its own inbound.
        for (int i = 0; i < root.getJSONArray("inbounds").length(); i++) {
            JSONObject inbound = root.getJSONArray("inbounds").getJSONObject(i);
            assertEquals("127.0.0.1", inbound.getString("listen"));
            assertEquals("http", inbound.getString("protocol"));
            assertTrue(plan.ports.containsValue(inbound.getInt("port")));
        }
    }

    @Test
    public void noDirectOrBlockOutboundIsEverCopiedIn() throws Exception {
        BatchProbeConfig.Plan plan = BatchProbeConfig.compose(
                Arrays.asList(member("a", 12001), member("b", 12002)));
        JSONArray outbounds = new JSONObject(plan.config).getJSONArray("outbounds");
        for (int i = 0; i < outbounds.length(); i++) {
            JSONObject out = outbounds.getJSONObject(i);
            assertFalse("freedom".equals(out.optString("protocol")));
            assertFalse("blackhole".equals(out.optString("protocol")));
            assertFalse("direct".equals(out.optString("tag")));
        }
        // No inherited routing rule can send probe traffic anywhere else.
        JSONArray rules = new JSONObject(plan.config).getJSONObject("routing").getJSONArray("rules");
        for (int i = 0; i < rules.length(); i++) {
            assertTrue(rules.getJSONObject(i).has("inboundTag"));
        }
    }

    @Test
    public void ambiguousOrChainedConfigsAreRejectedNotGuessed() throws Exception {
        JSONObject twoProxies = new JSONObject(single("proxy", "vless", "a.invalid"));
        twoProxies.getJSONArray("outbounds")
                .put(new JSONObject().put("tag", "proxy").put("protocol", "trojan"));
        JSONObject chained = new JSONObject(single("proxy", "vless", "c.invalid"));
        chained.getJSONArray("outbounds")
                .put(new JSONObject().put("tag", "chain").put("protocol", "socks"));
        List<BatchProbeConfig.Member> members = new ArrayList<>();
        members.add(new BatchProbeConfig.Member("two", twoProxies.toString(), 13001));
        members.add(new BatchProbeConfig.Member("chain", chained.toString(), 13002));
        members.add(new BatchProbeConfig.Member("broken", "{not json", 13003));
        members.add(new BatchProbeConfig.Member("local", single("proxy", "freedom", "x"), 13004));
        members.add(member("good", 13005));
        BatchProbeConfig.Plan plan = BatchProbeConfig.compose(members);
        assertEquals(Collections.singleton("good"), plan.ports.keySet());
        assertTrue(plan.rejected.containsAll(Arrays.asList("two", "chain", "broken", "local")));
    }

    @Test
    public void duplicateIdsAndPortsCannotCollide() throws Exception {
        BatchProbeConfig.Plan plan = BatchProbeConfig.compose(Arrays.asList(
                member("a", 14001), member("a", 14002), member("b", 14001), member("c", 0)));
        assertEquals(Collections.singleton("a"), plan.ports.keySet());
        assertTrue(plan.rejected.contains("b"));
        assertTrue(plan.rejected.contains("c"));
    }

    @Test
    public void fragmentDialerIsSharedOnceAndNeverFirst() throws Exception {
        JSONObject withFragment = new JSONObject(single("proxy", "vless", "a.invalid"));
        withFragment.getJSONArray("outbounds").getJSONObject(0)
                .put("streamSettings", new JSONObject()
                        .put("sockopt", new JSONObject().put("dialerProxy", "fragment")));
        withFragment.getJSONArray("outbounds").put(new JSONObject()
                .put("tag", "fragment").put("protocol", "freedom"));
        BatchProbeConfig.Plan plan = BatchProbeConfig.compose(Arrays.asList(
                new BatchProbeConfig.Member("a", withFragment.toString(), 15001),
                new BatchProbeConfig.Member("b", withFragment.toString(), 15002)));
        assertEquals(2, plan.ports.size());
        JSONArray outbounds = new JSONObject(plan.config).getJSONArray("outbounds");
        assertEquals(3, outbounds.length());
        assertEquals("fragment", outbounds.getJSONObject(2).getString("tag"));
        assertFalse("fragment".equals(outbounds.getJSONObject(0).getString("tag")));
    }

    @Test
    public void unknownDialerKeepsTheProfileOnThePerProfilePath() throws Exception {
        JSONObject exotic = new JSONObject(single("proxy", "vless", "a.invalid"));
        exotic.getJSONArray("outbounds").getJSONObject(0)
                .put("streamSettings", new JSONObject()
                        .put("sockopt", new JSONObject().put("dialerProxy", "mystery")));
        BatchProbeConfig.Plan plan = BatchProbeConfig.compose(Collections.singletonList(
                new BatchProbeConfig.Member("a", exotic.toString(), 16001)));
        assertTrue(plan.isEmpty());
        assertEquals(Collections.singletonList("a"), plan.rejected);
        assertEquals("", plan.config);
    }

    @Test
    public void emptyInputProducesNoConfig() throws Exception {
        assertTrue(BatchProbeConfig.compose(null).isEmpty());
        assertTrue(BatchProbeConfig.compose(Collections.<BatchProbeConfig.Member>emptyList()).isEmpty());
    }

    @Test
    public void composedConfigSilencesLoggingAndPinsDomainStrategy() throws Exception {
        JSONObject root = new JSONObject(
                BatchProbeConfig.compose(Collections.singletonList(member("a", 17001))).config);
        assertEquals("none", root.getJSONObject("log").getString("loglevel"));
        assertEquals("AsIs", root.getJSONObject("routing").getString("domainStrategy"));
        assertNull(root.optJSONObject("dns"));
        assertNotNull(root.optJSONArray("inbounds"));
    }
}
