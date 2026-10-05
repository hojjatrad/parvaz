package com.parvaz.tunnel.core;

import com.parvaz.tunnel.config.ClashParser;
import com.parvaz.tunnel.config.EngineConfig;
import com.parvaz.tunnel.config.ImportResult;
import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.config.ProtocolNames;
import com.parvaz.tunnel.config.ReadinessConfig;
import com.parvaz.tunnel.config.SingBoxParser;
import com.parvaz.tunnel.model.Profile;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ShadowTLS (a Shadowsocks node behind a TLS hop) and the sing-box importer finally
 * covering every protocol the engine can dial.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34})
public class ShadowTlsAndImportTest {

    private static Profile shadowTls() {
        Profile p = new Profile();
        p.id = "a";
        p.protocol = "shadowtls";
        p.address = "edge.invalid";
        p.port = 443;
        p.encryption = "2022-blake3-aes-128-gcm";
        p.uuid = "ss-secret";
        p.quicKey = "tls-secret";
        p.mode = "3";
        p.sni = "www.microsoft.invalid";
        return p;
    }

    @Test
    public void theChainIsTwoOutboundsWhereTheProxyDialsThroughTheTlsHop() throws Exception {
        JSONArray chain = EngineConfig.outbounds(shadowTls(), "proxy");
        assertEquals(2, chain.length());
        JSONObject proxy = chain.getJSONObject(0);
        JSONObject hop = chain.getJSONObject(1);
        assertEquals("shadowsocks", proxy.getString("type"));
        assertEquals("2022-blake3-aes-128-gcm", proxy.getString("method"));
        assertEquals("ss-secret", proxy.getString("password"));
        assertEquals(hop.getString("tag"), proxy.getString("detour"));
        // The dialling outbound must not carry its own server: the hop owns the socket.
        assertFalse(proxy.has("server"));
        assertFalse(proxy.has("tls"));
        assertEquals("shadowtls", hop.getString("type"));
        assertEquals("edge.invalid", hop.getString("server"));
        assertEquals(443, hop.getInt("server_port"));
        assertEquals(3, hop.getInt("version"));
        assertEquals("tls-secret", hop.getString("password"));
        assertEquals("www.microsoft.invalid", hop.getJSONObject("tls").getString("server_name"));
    }

    @Test
    public void theWholeEngineConfigRoutesThroughTheChainAndStaysVerifiable()
            throws Exception {
        JSONObject root = EngineConfig.build(shadowTls(), 10810, 10853, "u", "p");
        assertEquals(2, root.getJSONArray("outbounds").length());
        assertEquals("proxy", root.getJSONObject("route").getString("final"));
        assertTrue("a ShadowTLS pair is still a proven remote-only route",
                ReadinessConfig.nativeRemoteOnly(root, "shadowtls"));
    }

    @Test
    public void anUnprovenChainIsNotAcceptedAsAVerifiedRoute() throws Exception {
        JSONObject root = EngineConfig.build(shadowTls(), 10810, 10853, "u", "p");
        // A hop that itself detours somewhere else is no longer a two-step chain.
        root.getJSONArray("outbounds").getJSONObject(1).put("detour", "somewhere");
        assertFalse(ReadinessConfig.nativeRemoteOnly(root, "shadowtls"));

        JSONObject mismatched = EngineConfig.build(shadowTls(), 10810, 10853, "u", "p");
        mismatched.getJSONArray("outbounds").getJSONObject(0).put("detour", "not-the-hop");
        assertFalse(ReadinessConfig.nativeRemoteOnly(mismatched, "shadowtls"));
    }

    @Test
    public void aShadowsocksLinkWithTheShadowTlsPluginImportsAsAChain() throws Exception {
        Profile p = LinkParser.parseOne("ss://" + base64("aes-256-gcm:ss-secret")
                + "@edge.invalid:443?plugin=shadow-tls%3Bhost%3Dwww.bing.invalid"
                + "%3Bpassword%3Dtls-secret%3Bversion%3D3#Node");
        assertEquals("shadowtls", p.protocol);
        assertEquals("aes-256-gcm", p.encryption);
        assertEquals("ss-secret", p.uuid);
        assertEquals("tls-secret", p.quicKey);
        assertEquals("3", p.mode);
        assertEquals("www.bing.invalid", p.sni);
        assertEquals("shadowtls", ProtocolNames.canonical("shadow-tls"));
        assertTrue(ProtocolNames.hasEngine("shadowtls"));
    }

    @Test
    public void aPlainShadowsocksLinkIsUntouchedAndAnotherPluginIsNotFaked()
            throws Exception {
        Profile plain = LinkParser.parseOne("ss://" + base64("aes-256-gcm:pw")
                + "@edge.invalid:8388#Node");
        assertEquals("shadowsocks", plain.protocol);

        ImportResult obfs = ClashParser.parseDetailed("proxies:\n"
                + "  - {name: n, type: ss, server: edge.invalid, port: 8388,"
                + " cipher: aes-256-gcm, password: pw, plugin: obfs,"
                + " plugin-opts: {mode: tls, host: a.invalid}}\n");
        assertTrue(obfs.profiles.isEmpty());
    }

    @Test
    public void clashShadowTlsOptionsBecomeTheHopSecretAndVersion() throws Exception {
        List<Profile> profiles = ClashParser.parse("proxies:\n"
                + "  - {name: n, type: ss, server: edge.invalid, port: 443,"
                + " cipher: 2022-blake3-aes-128-gcm, password: ss-secret, plugin: shadow-tls,"
                + " plugin-opts: {host: www.bing.invalid, password: tls-secret, version: 3}}\n");
        assertEquals(1, profiles.size());
        Profile p = profiles.get(0);
        assertEquals("shadowtls", p.protocol);
        assertEquals("ss-secret", p.uuid);
        assertEquals("tls-secret", p.quicKey);
        assertEquals("www.bing.invalid", p.sni);
        assertEquals("3", p.mode);
    }

    @Test
    public void singBoxFullConfigsNowImportHysteriaAnytlsAndSnell() throws Exception {
        List<Profile> profiles = SingBoxParser.parse("{\"outbounds\":["
                + "{\"type\":\"hysteria\",\"tag\":\"h1\",\"server\":\"a.invalid\",\"server_port\":443,"
                + "\"auth_str\":\"secret\",\"up_mbps\":50,\"down_mbps\":200,\"obfs\":\"salt\","
                + "\"tls\":{\"enabled\":true,\"server_name\":\"a.invalid\"}},"
                + "{\"type\":\"anytls\",\"tag\":\"a1\",\"server\":\"b.invalid\",\"server_port\":443,"
                + "\"password\":\"pw\",\"tls\":{\"enabled\":true,\"server_name\":\"b.invalid\"}},"
                + "{\"type\":\"snell\",\"tag\":\"s1\",\"server\":\"c.invalid\",\"server_port\":443,"
                + "\"psk\":\"psk-secret\",\"version\":4}]}");
        assertEquals(3, profiles.size());
        assertEquals("hysteria", profiles.get(0).protocol);
        assertEquals("secret", profiles.get(0).uuid);
        assertEquals("50,200", profiles.get(0).seed);
        assertEquals("salt", profiles.get(0).host);
        assertEquals("anytls", profiles.get(1).protocol);
        assertEquals("pw", profiles.get(1).uuid);
        assertEquals("snell", profiles.get(2).protocol);
        assertEquals("psk-secret", profiles.get(2).uuid);
        assertEquals("4", profiles.get(2).mode);
    }

    @Test
    public void importedSingBoxNodesStillBuildTheOutboundTheEngineExpects() throws Exception {
        List<Profile> profiles = SingBoxParser.parse("{\"outbounds\":["
                + "{\"type\":\"hysteria\",\"tag\":\"h1\",\"server\":\"a.invalid\",\"server_port\":443,"
                + "\"auth_str\":\"secret\",\"up_mbps\":50,\"down_mbps\":200}]}");
        JSONObject out = EngineConfig.outbound(profiles.get(0), "proxy");
        assertEquals("hysteria", out.getString("type"));
        assertEquals("secret", out.getString("auth_str"));
        assertEquals(50, out.getInt("up_mbps"));
        assertEquals(200, out.getInt("down_mbps"));
    }

    @Test
    public void probeParallelismFollowsTheDeviceTheWorkAndTheFailures() {
        assertEquals(6, BatchLatency.parallelism(8, 12, 0));
        assertEquals(4, BatchLatency.parallelism(4, 12, 0));
        assertEquals(2, BatchLatency.parallelism(2, 12, 0));
        assertEquals(2, BatchLatency.parallelism(1, 12, 0));
        // Never more threads than servers to measure.
        assertEquals(3, BatchLatency.parallelism(8, 3, 0));
        assertEquals(1, BatchLatency.parallelism(8, 1, 0));
        // Backing off while whole groups keep failing, but never to zero.
        assertEquals(3, BatchLatency.parallelism(8, 12, 1));
        assertEquals(2, BatchLatency.parallelism(8, 12, 2));
        assertEquals(2, BatchLatency.parallelism(8, 12, 9));
        assertEquals(1, BatchLatency.parallelism(8, 0, 0));
    }

    private static String base64(String text) {
        return android.util.Base64.encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
    }
}
