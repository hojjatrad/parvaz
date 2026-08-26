package com.parvaz.tunnel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.core.ProtocolSupport;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

/**
 * Covers the v1.15 security work: the DNS leak fix, DoH defaults, the domain
 * rule lists, and the honesty layer around protocols the core cannot dial.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class V115RegressionTest {

    private static final String LINK =
            "vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443"
                    + "?encryption=none&security=reality&sni=www.microsoft.com&fp=chrome"
                    + "&pbk=PUBKEY&sid=abcd&type=tcp&flow=xtls-rprx-vision#Dubai%20New";

    private Context context;
    private Prefs prefs;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = new Prefs(context);
        prefs.f343a.edit().clear().apply();
    }

    private Profile profile() throws Exception {
        ArrayList parsed = LinkParser.parseMany(LINK);
        return (Profile) parsed.get(0);
    }

    private JSONObject build() throws Exception {
        return new JSONObject(XrayConfigBuilder.b(profile(), prefs, null, true, false));
    }

    private JSONArray rules(JSONObject root) {
        JSONObject routing = root.optJSONObject("routing");
        assertNotNull("routing block missing", routing);
        JSONArray rules = routing.optJSONArray("rules");
        assertNotNull("routing rules missing", rules);
        return rules;
    }

    // ---- 1. DNS leak -----------------------------------------------------

    /**
     * The bug: the dns inbound was tagged but no rule referenced it, so queries
     * fell through to the tail rule. The fix must route it explicitly.
     */
    @Test
    public void dnsInboundIsRoutedThroughProxy() throws Exception {
        JSONArray rules = rules(build());
        boolean found = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            JSONArray inbound = rule == null ? null : rule.optJSONArray("inboundTag");
            if (inbound == null) {
                continue;
            }
            for (int j = 0; j < inbound.length(); j++) {
                if ("dns_inbound".equals(inbound.optString(j))) {
                    found = true;
                    assertEquals("DNS must leave through the tunnel",
                            "proxy", rule.optString("outboundTag"));
                }
            }
        }
        assertTrue("no rule references the dns inbound", found);
    }

    /** The DNS rule has to win, so it must come before the geo/IP rules. */
    @Test
    public void dnsRuleIsEvaluatedFirst() throws Exception {
        JSONArray rules = rules(build());
        JSONObject first = rules.optJSONObject(0);
        assertNotNull(first);
        JSONArray inbound = first.optJSONArray("inboundTag");
        assertNotNull("first rule should be the DNS rule", inbound);
        assertEquals("dns_inbound", inbound.optString(0));
    }

    // ---- 2. DoH ----------------------------------------------------------

    @Test
    public void remoteDnsDefaultsToEncrypted() throws Exception {
        JSONObject dns = build().optJSONObject("dns");
        assertNotNull(dns);
        String servers = dns.optJSONArray("servers").toString();
        assertTrue("remote DNS should default to DoH: " + servers,
                servers.contains("https://") || servers.contains("dns-query"));
    }

    /**
     * Plain-IP resolvers still need an explicit port; DoH URLs must not get one,
     * because "https://1.1.1.1/dns-query" with a port field is invalid.
     */
    @Test
    public void dohServerCarriesNoPortField() throws Exception {
        JSONArray servers = build().optJSONObject("dns").optJSONArray("servers");
        for (int i = 0; i < servers.length(); i++) {
            JSONObject server = servers.optJSONObject(i);
            if (server == null) {
                continue;
            }
            String address = server.optString("address", "");
            if (address.contains("://")) {
                assertFalse("DoH server must not declare a port: " + address,
                        server.has("port"));
            }
        }
    }

    /** The direct resolver should be domestic, not Google. */
    @Test
    public void directDnsIsNotGoogle() throws Exception {
        String json = XrayConfigBuilder.b(profile(), prefs, null, true, false);
        assertFalse("Iranian names must not resolve via 8.8.8.8",
                json.contains("\"8.8.8.8\""));
    }

    // ---- 3. Protocol honesty --------------------------------------------

    @Test
    public void hysteriaAndTuicAreReportedUnsupported() {
        assertFalse(ProtocolSupport.isSupported("hysteria2"));
        assertFalse(ProtocolSupport.isSupported("hy2"));
        assertFalse(ProtocolSupport.isSupported("tuic"));
        assertTrue(ProtocolSupport.isKnownUnsupported("tuic"));
    }

    @Test
    public void dialableProtocolsStaySupported() {
        assertTrue(ProtocolSupport.isSupported("vless"));
        assertTrue(ProtocolSupport.isSupported("vmess"));
        assertTrue(ProtocolSupport.isSupported("trojan"));
        assertTrue(ProtocolSupport.isSupported("shadowsocks"));
        assertFalse(ProtocolSupport.isKnownUnsupported("vless"));
    }

    @Test
    public void unsupportedCountIgnoresDialableServers() throws Exception {
        ArrayList<Profile> list = new ArrayList<>();
        list.add(profile());
        assertEquals(0, ProtocolSupport.countUnsupported(list));

        Profile hy = profile();
        hy.protocol = "hysteria2";
        list.add(hy);
        assertEquals(1, ProtocolSupport.countUnsupported(list));
    }

    // ---- 8. Domain rules -------------------------------------------------

    @Test
    public void domainListsProduceRoutingRules() throws Exception {
        prefs.f343a.edit()
                .putString("domains_direct", "bank.ir\nshaparak.ir")
                .putString("domains_block", "ads.example.com")
                .apply();

        JSONArray rules = rules(build());
        boolean direct = false;
        boolean block = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            JSONArray domains = rule == null ? null : rule.optJSONArray("domain");
            if (domains == null) {
                continue;
            }
            String joined = domains.toString();
            if (joined.contains("bank.ir")) {
                direct = true;
                assertEquals("direct", rule.optString("outboundTag"));
                assertTrue("bare names should match subdomains too",
                        joined.contains("domain:bank.ir"));
            }
            if (joined.contains("ads.example.com")) {
                block = true;
                assertEquals("block", rule.optString("outboundTag"));
            }
        }
        assertTrue("direct domain list not applied", direct);
        assertTrue("block domain list not applied", block);
    }

    /** An unset list must not emit an empty rule that matches everything. */
    @Test
    public void emptyDomainListsEmitNothing() throws Exception {
        JSONArray rules = rules(build());
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            JSONArray domains = rule == null ? null : rule.optJSONArray("domain");
            if (domains != null) {
                assertTrue("empty domain rule would match everything",
                        domains.length() > 0);
            }
        }
    }

    /** User lists must outrank the bundled geo categories. */
    @Test
    public void userDomainRulesPrecedeGeoRules() throws Exception {
        prefs.f343a.edit().putString("domains_proxy", "example.com").apply();

        JSONArray rules = rules(build());
        int userRule = -1;
        int geoRule = -1;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            JSONArray domains = rule == null ? null : rule.optJSONArray("domain");
            String joined = domains == null ? "" : domains.toString();
            if (userRule < 0 && joined.contains("example.com")) {
                userRule = i;
            }
            if (geoRule < 0 && joined.contains("geosite:")) {
                geoRule = i;
            }
        }
        assertTrue("user domain rule missing", userRule >= 0);
        if (geoRule >= 0) {
            assertTrue("user rules must be evaluated before geo categories",
                    userRule < geoRule);
        }
    }

    // ---- 5. Advanced prefs ----------------------------------------------

    @Test
    public void bufferSizePrefFeedsThePolicy() throws Exception {
        prefs.f343a.edit().putInt("buffer_size_kb", 128).apply();
        String json = XrayConfigBuilder.b(profile(), prefs, null, true, false);
        JSONObject policy = new JSONObject(json).optJSONObject("policy");
        assertNotNull(policy);
        JSONObject level8 = policy.optJSONObject("levels").optJSONObject("8");
        assertEquals(128, level8.optInt("bufferSize"));
    }

    // ---- 6. Shadowsocks legacy parsing & Backup restore ------------------

    @Test
    public void legacyShadowsocksLinkIsParsed() throws Exception {
        // ss://BASE64(aes-256-gcm:pass123@1.2.3.4:8388)#TestServer
        // "aes-256-gcm:pass123@1.2.3.4:8388" -> YWVzLTI1Ni1nY206cGFzczEyM0AxLjIuMy40OjgzODg=
        String legacyLink = "ss://YWVzLTI1Ni1nY206cGFzczEyM0AxLjIuMy40OjgzODg=#TestServer";
        Profile p = LinkParser.parseOne(legacyLink);
        assertNotNull("legacy shadowsocks link should parse", p);
        assertEquals("shadowsocks", p.protocol);
        assertEquals("aes-256-gcm", p.encryption);
        assertEquals("pass123", p.uuid);
        assertEquals("1.2.3.4", p.address);
        assertEquals(8388, p.port);
        assertEquals("TestServer", p.remark);
    }

    @Test
    public void backupExportAndRestoreRoundtrip() throws Exception {
        prefs.f343a.edit()
                .putString("domains_direct", "bank.ir")
                .putString("domains_block", "ads.com")
                .putInt("buffer_size_kb", 256)
                .putInt("health_strikes", 5)
                .putString("auto_wifi", "disconnect")
                .apply();
        java.util.HashSet<String> favs = new java.util.HashSet<>();
        favs.add("fav-server-1");
        prefs.saveFavorites(favs);

        String exportJson = com.parvaz.tunnel.store.BackupManager.export(context);
        assertTrue("export should contain domains_direct", exportJson.contains("bank.ir"));
        assertTrue("export should contain favorites", exportJson.contains("fav-server-1"));

        // Clear and restore
        prefs.f343a.edit().clear().apply();
        com.parvaz.tunnel.store.BackupManager.a(context, exportJson);

        assertEquals("bank.ir", prefs.f343a.getString("domains_direct", ""));
        assertEquals("ads.com", prefs.f343a.getString("domains_block", ""));
        assertEquals(256, prefs.f343a.getInt("buffer_size_kb", 0));
        assertEquals(5, prefs.f343a.getInt("health_strikes", 0));
        assertEquals("disconnect", prefs.f343a.getString("auto_wifi", ""));
        assertTrue("favorites must be restored", prefs.getFavorites().contains("fav-server-1"));
    }

    @Test
    public void backupCryptoEncryptDecrypt() throws Exception {
        String data = "{\"test\":\"parvaz\"}";
        char[] pass = "SecretPass123".toCharArray();
        String encrypted = com.parvaz.tunnel.store.BackupCrypto.encrypt(data, pass);
        assertTrue(com.parvaz.tunnel.store.BackupCrypto.isEncrypted(encrypted));
        String decrypted = com.parvaz.tunnel.store.BackupCrypto.decrypt(encrypted, pass);
        assertEquals(data, decrypted);
    }
}
