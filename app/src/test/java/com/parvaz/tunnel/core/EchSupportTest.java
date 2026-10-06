package com.parvaz.tunnel.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.config.SingBoxOutbound;
import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Encrypted Client Hello: the share-link parameter must reach Xray's tlsSettings
 * unchanged, survive a save/reload, and never be dropped by sending the server to an
 * engine that cannot speak it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34})
public class EchSupportTest {

    private Prefs prefs() {
        return new Prefs(RuntimeEnvironment.getApplication());
    }

    @Test
    public void theDnsQueryFormReachesXrayTlsSettings() throws Exception {
        Profile p = LinkParser.parseOne("vless://11111111-2222-3333-4444-555555555555"
                + "@edge.invalid:443?encryption=none&security=tls&type=ws&host=edge.invalid"
                + "&sni=edge.invalid&fp=chrome&ech=udp%3A%2F%2F1.1.1.1#Node");
        assertEquals("udp://1.1.1.1", p.ech);

        JSONObject outbound = XrayConfigBuilder.c(p, prefs());
        JSONObject tls = outbound.getJSONObject("streamSettings").getJSONObject("tlsSettings");
        assertEquals("udp://1.1.1.1", tls.getString("echConfigList"));
        // It must sit directly under tlsSettings, not nested in a sub-object.
        assertFalse(tls.has("settings"));
    }

    @Test
    public void theSeparateLookupDomainAndBase64FormsAreKeptVerbatim() throws Exception {
        Profile split = LinkParser.parseOne("vless://11111111-2222-3333-4444-555555555555"
                + "@edge.invalid:443?encryption=none&security=tls"
                + "&ech=cloudflare-ech.com%2Bhttps%3A%2F%2F8.8.8.8%2Fdns-query#Node");
        assertEquals("cloudflare-ech.com+https://8.8.8.8/dns-query", split.ech);

        Profile pinned = LinkParser.parseOne("vless://11111111-2222-3333-4444-555555555555"
                + "@edge.invalid:443?encryption=none&security=tls"
                + "&ech=AEX%2BDQBBPwAgACBcaFV4bG5t#Node");
        assertEquals("AEX+DQBBPwAgACBcaFV4bG5t", pinned.ech);
        JSONObject tls = XrayConfigBuilder.c(pinned, prefs())
                .getJSONObject("streamSettings").getJSONObject("tlsSettings");
        assertEquals("AEX+DQBBPwAgACBcaFV4bG5t", tls.getString("echConfigList"));
    }

    @Test
    public void aServerWithoutEchIsUnchanged() throws Exception {
        Profile p = LinkParser.parseOne("vless://11111111-2222-3333-4444-555555555555"
                + "@edge.invalid:443?encryption=none&security=tls&sni=edge.invalid#Node");
        assertEquals("", p.ech);
        JSONObject tls = XrayConfigBuilder.c(p, prefs())
                .getJSONObject("streamSettings").getJSONObject("tlsSettings");
        assertFalse("No ECH key may appear for a server that never asked for it",
                tls.has("echConfigList"));
        // An untouched profile must also keep its stored shape, so saved servers are
        // not re-identified as new ones after the upgrade.
        assertFalse(p.toJson().has("ech"));
    }

    @Test
    public void echSurvivesSaveAndReload() throws Exception {
        Profile p = LinkParser.parseOne("vless://11111111-2222-3333-4444-555555555555"
                + "@edge.invalid:443?encryption=none&security=tls&ech=udp%3A%2F%2F8.8.8.8#Node");
        Profile reloaded = Profile.fromJson(p.toJson());
        assertEquals("udp://8.8.8.8", reloaded.ech);
    }

    @Test
    public void anEchServerIsNeverHandedToAnEngineThatCannotSpeakIt() throws Exception {
        Profile p = LinkParser.parseOne("vless://11111111-2222-3333-4444-555555555555"
                + "@edge.invalid:443?encryption=none&security=tls&ech=udp%3A%2F%2F1.1.1.1#Node");
        assertFalse(SingBoxOutbound.capable(p));
        assertFalse(CoreSelection.singBoxCapable(p));
        assertTrue(CoreSelection.xrayCapable(p));
        assertEquals(CoreSelection.XRAY, CoreSelection.natural(p));

        // The same server without ECH still has the second engine available.
        p.ech = "";
        assertTrue(CoreSelection.singBoxCapable(p));
    }
}
