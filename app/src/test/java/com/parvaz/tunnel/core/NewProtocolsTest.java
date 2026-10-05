package com.parvaz.tunnel.core;

import android.app.Application;

import com.parvaz.tunnel.config.ClashParser;
import com.parvaz.tunnel.config.EngineConfig;
import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.config.ProtocolNames;
import com.parvaz.tunnel.model.Profile;

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
 * Hysteria v1, AnyTLS and Snell ride the sing-box engine that is already bundled, so the
 * work is import, canonical naming and a correct outbound - not a new binary.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34}, application = Application.class)
public class NewProtocolsTest {

    private static JSONObject outbound(Profile profile) throws Exception {
        return EngineConfig.build(profile, 10810, 10811, "local", "local-secret")
                .getJSONArray("outbounds").getJSONObject(0);
    }

    @Test
    public void hysteriaVersionOneKeepsItsOwnIdentityAndIsNotHysteria2() {
        assertEquals("hysteria", ProtocolNames.canonical("hy"));
        assertEquals("hysteria", ProtocolNames.canonical("Hysteria1"));
        assertEquals("hysteria2", ProtocolNames.canonical("hy2"));
        assertTrue(ProtocolNames.hasEngine("hysteria"));
        assertTrue(ProtocolNames.hasEngine("anytls"));
        assertTrue(ProtocolNames.hasEngine("snell"));
        assertTrue(EngineConfig.external("hysteria"));
        assertTrue(EngineConfig.external("anytls"));
        assertTrue(EngineConfig.external("snell"));
    }

    @Test
    public void hysteriaLinkCarriesAuthObfuscationAndBothBandwidthHints() throws Exception {
        Profile p = LinkParser.parseMany("hysteria://server.invalid:443?auth=secret-word"
                + "&upmbps=30&downmbps=200&obfs=scramble&peer=edge.invalid#node").get(0);
        assertEquals("hysteria", p.protocol);
        assertEquals("secret-word", p.uuid);
        assertEquals("edge.invalid", p.sni);
        assertEquals("30,200", p.seed);
        assertEquals("node", p.remark);
        JSONObject out = outbound(p);
        assertEquals("hysteria", out.getString("type"));
        assertEquals("secret-word", out.getString("auth_str"));
        assertEquals(30, out.getInt("up_mbps"));
        assertEquals(200, out.getInt("down_mbps"));
        assertEquals("scramble", out.getString("obfs"));
        assertEquals("edge.invalid", out.getJSONObject("tls").getString("server_name"));
    }

    @Test
    public void missingOrJunkBandwidthFallsBackInsteadOfBreakingTheDial() throws Exception {
        Profile p = LinkParser.parseMany("hysteria://server.invalid:443?auth=k").get(0);
        JSONObject out = outbound(p);
        assertEquals(20, out.getInt("up_mbps"));
        assertEquals(100, out.getInt("down_mbps"));
        Profile text = LinkParser.parseMany(
                "hysteria://server.invalid:443?auth=k&upmbps=50%20Mbps&downmbps=abc").get(0);
        assertEquals("50,", text.seed);
        JSONObject mixed = outbound(text);
        assertEquals(50, mixed.getInt("up_mbps"));
        assertEquals(100, mixed.getInt("down_mbps"));
    }

    @Test
    public void anyTlsLinkBecomesAPasswordOutboundOverTls() throws Exception {
        Profile p = LinkParser.parseMany(
                "anytls://pass%252Fword@server.invalid:8443?sni=edge.invalid&insecure=1#any").get(0);
        assertEquals("anytls", p.protocol);
        assertEquals("pass%2Fword", p.uuid);
        assertEquals(8443, p.port);
        assertTrue(p.allowInsecure);
        JSONObject out = outbound(p);
        assertEquals("anytls", out.getString("type"));
        assertEquals("pass%2Fword", out.getString("password"));
        assertEquals("edge.invalid", out.getJSONObject("tls").getString("server_name"));
        assertTrue(out.getJSONObject("tls").getBoolean("insecure"));
    }

    @Test
    public void clashHysteriaNodeIsAcceptedWithItsBandwidthStrings() {
        String yaml = "proxies:\n - name: hy1\n   type: hysteria\n   server: server.invalid\n"
                + "   port: 443\n   auth-str: secret\n   up: 30 Mbps\n   down: 200 Mbps\n"
                + "   obfs: scramble\n   sni: edge.invalid\n";
        List<Profile> profiles = ClashParser.parse(yaml);
        assertEquals(1, profiles.size());
        Profile p = profiles.get(0);
        assertEquals("hysteria", p.protocol);
        assertEquals("secret", p.uuid);
        assertEquals("30,200", p.seed);
        assertEquals("scramble", p.host);
    }

    @Test
    public void clashAnyTlsAndSnellNodesAreAccepted() throws Exception {
        List<Profile> any = ClashParser.parse("proxies:\n - name: a\n   type: anytls\n"
                + "   server: server.invalid\n   port: 443\n   password: secret\n");
        assertEquals(1, any.size());
        assertEquals("anytls", any.get(0).protocol);

        List<Profile> snell = ClashParser.parse("proxies:\n - name: s\n   type: snell\n"
                + "   server: server.invalid\n   port: 8443\n   psk: shared-key\n   version: 4\n");
        assertEquals(1, snell.size());
        Profile p = snell.get(0);
        assertEquals("snell", p.protocol);
        assertEquals("shared-key", p.uuid);
        JSONObject out = outbound(p);
        assertEquals("snell", out.getString("type"));
        assertEquals("shared-key", out.getString("psk"));
        assertEquals(4, out.getInt("version"));
        // Snell has no TLS layer of its own; claiming one would fail at dial time.
        assertFalse(out.has("tls"));
    }

    @Test
    public void aNodeWithoutItsSecretIsRejectedRatherThanStored() {
        assertTrue(ClashParser.parse("proxies:\n - name: a\n   type: anytls\n"
                + "   server: server.invalid\n   port: 443\n").isEmpty());
        assertTrue(ClashParser.parse("proxies:\n - name: s\n   type: snell\n"
                + "   server: server.invalid\n   port: 443\n").isEmpty());
    }

    @Test
    public void theNewFamiliesGetTheirOwnListGroupAndLabel() {
        Profile hysteria = new Profile();
        hysteria.protocol = "hy";
        assertEquals("hysteria", ProtocolGroups.groupOf(hysteria));
        assertEquals("Hysteria", ProtocolGroups.labelOf("hysteria"));
        assertEquals("AnyTLS", ProtocolGroups.labelOf("anytls"));
        assertEquals("Snell", ProtocolGroups.labelOf("snell"));
    }

    @Test
    public void shadowsocks2022CiphersSurviveImportUnchanged() {
        List<Profile> result = ClashParser.parse("proxies:\n - name: ss\n   type: ss\n"
                + "   server: server.invalid\n   port: 8388\n   cipher: 2022-blake3-aes-128-gcm\n"
                + "   password: c2VjcmV0\n");
        assertEquals(1, result.size());
        assertEquals("2022-blake3-aes-128-gcm", result.get(0).encryption);
    }
}
