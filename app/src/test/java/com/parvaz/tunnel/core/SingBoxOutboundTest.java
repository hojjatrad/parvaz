package com.parvaz.tunnel.core;

import com.parvaz.tunnel.config.EngineConfig;
import com.parvaz.tunnel.config.SingBoxOutbound;
import com.parvaz.tunnel.model.Profile;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The second engine must dial a server exactly as the first one would, or refuse to. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34})
public class SingBoxOutboundTest {

    private static Profile vless() {
        Profile p = new Profile();
        p.id = "a";
        p.protocol = "vless";
        p.address = "edge.invalid";
        p.port = 443;
        p.uuid = "11111111-1111-4111-8111-111111111111";
        p.security = "tls";
        p.sni = "edge.invalid";
        p.network = "ws";
        p.path = "/ray";
        p.host = "cdn.invalid";
        return p;
    }

    @Test
    public void aWebsocketVlessServerKeepsItsPathHostAndSni() throws Exception {
        JSONObject out = SingBoxOutbound.build(vless(), "proxy");
        assertEquals("vless", out.getString("type"));
        assertEquals("edge.invalid", out.getString("server"));
        assertEquals(443, out.getInt("server_port"));
        assertEquals("11111111-1111-4111-8111-111111111111", out.getString("uuid"));
        assertEquals("ws", out.getJSONObject("transport").getString("type"));
        assertEquals("/ray", out.getJSONObject("transport").getString("path"));
        assertEquals("cdn.invalid", out.getJSONObject("transport")
                .getJSONObject("headers").getString("Host"));
        assertEquals("edge.invalid", out.getJSONObject("tls").getString("server_name"));
        assertFalse(out.getJSONObject("tls").getBoolean("insecure"));
        assertEquals("proxy", out.getString("tag"));
    }

    @Test
    public void realityCarriesItsPublicKeyShortIdAndClientHello() throws Exception {
        Profile p = vless();
        p.security = "reality";
        p.network = "tcp";
        p.flow = "xtls-rprx-vision";
        p.publicKey = "DFkd9kEIbx2rQ1Q0rbAorpHLhCnyvEGJ4ZDkS3Vn3Ew";
        p.shortId = "0123abcd";
        p.fingerprint = "firefox";
        JSONObject out = SingBoxOutbound.build(p, "proxy");
        assertEquals("xtls-rprx-vision", out.getString("flow"));
        JSONObject tls = out.getJSONObject("tls");
        assertEquals(p.publicKey, tls.getJSONObject("reality").getString("public_key"));
        assertEquals("0123abcd", tls.getJSONObject("reality").getString("short_id"));
        assertEquals("firefox", tls.getJSONObject("utls").getString("fingerprint"));
        assertFalse("REALITY must not advertise ALPN from the TLS profile", tls.has("alpn"));
        assertFalse(out.has("transport"));
    }

    @Test
    public void vmessTrojanAndShadowsocksMapOntoTheirOwnFields() throws Exception {
        Profile vmess = vless();
        vmess.protocol = "vmess";
        vmess.encryption = "aes-128-gcm";
        vmess.alterId = 0;
        JSONObject m = SingBoxOutbound.build(vmess, "proxy");
        assertEquals("aes-128-gcm", m.getString("security"));
        assertEquals(0, m.getInt("alter_id"));

        Profile trojan = vless();
        trojan.protocol = "trojan";
        trojan.network = "grpc";
        trojan.serviceName = "tunnel";
        JSONObject t = SingBoxOutbound.build(trojan, "proxy");
        assertEquals(trojan.uuid, t.getString("password"));
        assertEquals("tunnel", t.getJSONObject("transport").getString("service_name"));

        Profile ss = vless();
        ss.protocol = "shadowsocks";
        ss.security = "";
        ss.network = "tcp";
        ss.encryption = "2022-blake3-aes-128-gcm";
        JSONObject s = SingBoxOutbound.build(ss, "proxy");
        assertEquals("2022-blake3-aes-128-gcm", s.getString("method"));
        assertEquals(ss.uuid, s.getString("password"));
        assertFalse(s.has("tls"));
    }

    @Test
    public void settingsSingBoxCannotReproduceAreRefusedRatherThanApproximated() {
        Profile kcp = vless();
        kcp.network = "kcp";
        assertFalse(SingBoxOutbound.capable(kcp));

        Profile xhttp = vless();
        xhttp.network = "xhttp";
        assertFalse(SingBoxOutbound.capable(xhttp));

        Profile obfs = vless();
        obfs.network = "tcp";
        obfs.headerType = "http";
        assertFalse(SingBoxOutbound.capable(obfs));

        Profile oddFlow = vless();
        oddFlow.flow = "xtls-rprx-direct";
        assertFalse(SingBoxOutbound.capable(oddFlow));

        Profile trojanFlow = vless();
        trojanFlow.protocol = "trojan";
        trojanFlow.flow = "xtls-rprx-vision";
        assertFalse(SingBoxOutbound.capable(trojanFlow));

        Profile tlsShadowsocks = vless();
        tlsShadowsocks.protocol = "shadowsocks";
        tlsShadowsocks.encryption = "aes-256-gcm";
        tlsShadowsocks.network = "tcp";
        assertFalse(SingBoxOutbound.capable(tlsShadowsocks));

        Profile realityOnTrojan = vless();
        realityOnTrojan.protocol = "trojan";
        realityOnTrojan.security = "reality";
        realityOnTrojan.publicKey = "k";
        assertFalse(SingBoxOutbound.capable(realityOnTrojan));

        Profile hysteria = new Profile();
        hysteria.protocol = "hysteria2";
        hysteria.address = "a.invalid";
        assertFalse("engine protocols keep their own builder",
                SingBoxOutbound.capable(hysteria));
    }

    @Test
    public void theEngineBuilderDelegatesInsteadOfRejectingXrayProtocols() throws Exception {
        JSONObject out = EngineConfig.outbound(vless(), "proxy");
        assertEquals("vless", out.getString("type"));
        JSONObject whole = EngineConfig.build(vless(), 10810, 10853, "u", "p");
        assertEquals("vless", whole.getJSONArray("outbounds").getJSONObject(0).getString("type"));
        assertEquals("proxy", whole.getJSONObject("route").getString("final"));
        assertTrue(whole.getJSONArray("inbounds").getJSONObject(0).getString("type")
                .equals("socks"));
    }

    @Test
    public void anUnreproducibleProfileThrowsInsteadOfBuildingSomethingElse() {
        Profile kcp = vless();
        kcp.network = "kcp";
        try {
            SingBoxOutbound.build(kcp, "proxy");
            throw new AssertionError("expected refusal");
        } catch (IllegalArgumentException | org.json.JSONException expected) {
            assertTrue(true);
        }
    }
}
