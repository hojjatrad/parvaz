package com.parvaz.tunnel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.core.GeoIndex;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Set;

/**
 * Guards the "core start failed: config error" regression.
 *
 * <p>Xray resolves {@code geosite:} / {@code geoip:} references while building the
 * config. Naming a tag that the installed .dat files do not contain does not degrade
 * routing — it aborts core startup entirely, and since ping uses the same builder,
 * servers also stop reporting latency. The shipped {@code -lite} geo files contain only
 * {@code category-ir} and {@code private}, while routing also asked for
 * {@code category-ads-all}, {@code category-ads-ir}, {@code geosite:private} and
 * {@code geoip:ir}, so every connection failed until the background download of the
 * full files happened to succeed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GeoRoutingRegressionTest {

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
        GeoIndex.invalidate();
    }

    /**
     * Writes a fake geo .dat carrying the given upper-case tags. The scanner looks for
     * ASCII tag runs, so a protobuf-shaped stub is enough.
     */
    private void writeGeoFile(String name, String... tags) throws Exception {
        File f = new File(context.getFilesDir(), name);
        FileOutputStream out = new FileOutputStream(f);
        try {
            for (String tag : tags) {
                out.write(0x0a);
                out.write(tag.length());
                out.write(tag.getBytes("US-ASCII"));
                out.write(new byte[]{0x12, 0x04, 1, 2, 3, 4});
            }
        } finally {
            out.close();
        }
        GeoIndex.invalidate();
    }

    private Profile profile() throws Exception {
        ArrayList parsed = LinkParser.parseMany(LINK);
        return (Profile) parsed.get(0);
    }

    /** Collects every geosite:/geoip: reference the built config depends on. */
    private ArrayList<String> geoRefsIn(String json) throws Exception {
        ArrayList<String> refs = new ArrayList<>();
        JSONObject root = new JSONObject(json);

        JSONObject routing = root.optJSONObject("routing");
        if (routing != null) {
            JSONArray rules = routing.optJSONArray("rules");
            for (int i = 0; rules != null && i < rules.length(); i++) {
                JSONObject rule = rules.optJSONObject(i);
                if (rule == null) {
                    continue;
                }
                collect(rule.optJSONArray("domain"), refs);
                collect(rule.optJSONArray("ip"), refs);
            }
        }
        JSONObject dns = root.optJSONObject("dns");
        JSONArray servers = dns == null ? null : dns.optJSONArray("servers");
        for (int i = 0; servers != null && i < servers.length(); i++) {
            JSONObject server = servers.optJSONObject(i);
            if (server != null) {
                collect(server.optJSONArray("domains"), refs);
            }
        }
        return refs;
    }

    private void collect(JSONArray arr, ArrayList<String> into) {
        for (int i = 0; arr != null && i < arr.length(); i++) {
            String v = arr.optString(i, "");
            if (v.startsWith("geosite:") || v.startsWith("geoip:")) {
                into.add(v);
            }
        }
    }

    @Test
    public void liteGeoDataProducesNoUnsatisfiableReference() throws Exception {
        // Exactly what shipped: the lite pair.
        writeGeoFile("geosite.dat", "CATEGORY-IR");
        writeGeoFile("geoip.dat", "PRIVATE");

        String json = XrayConfigBuilder.b(profile(), prefs, null, true, true);

        Set<String> site = GeoIndex.geositeTags(context);
        Set<String> ip = GeoIndex.geoipTags(context);
        for (String ref : geoRefsIn(json)) {
            if (ref.startsWith("geosite:")) {
                assertTrue("config references missing " + ref,
                        site.contains(ref.substring("geosite:".length())));
            } else {
                assertTrue("config references missing " + ref,
                        ip.contains(ref.substring("geoip:".length())));
            }
        }
    }

    @Test
    public void adBlockRuleIsDroppedWhenAdListsAreAbsent() throws Exception {
        writeGeoFile("geosite.dat", "CATEGORY-IR");
        writeGeoFile("geoip.dat", "PRIVATE");

        String json = XrayConfigBuilder.b(profile(), prefs, null, true, true);
        assertFalse(json.contains("category-ads-all"));
        assertFalse(json.contains("category-ads-ir"));
    }

    @Test
    public void adBlockRuleReturnsOnceTheFullDataIsInstalled() throws Exception {
        writeGeoFile("geosite.dat",
                "CATEGORY-IR", "CATEGORY-ADS-ALL", "CATEGORY-ADS-IR", "PRIVATE");
        writeGeoFile("geoip.dat", "PRIVATE", "IR");

        String json = XrayConfigBuilder.b(profile(), prefs, null, true, true);
        assertTrue("full data must re-enable ad blocking",
                json.contains("geosite:category-ads-all"));
        assertTrue("full data must re-enable geoip:ir bypass", json.contains("geoip:ir"));
        assertTrue(json.contains("geosite:private"));
    }

    @Test
    public void privateRangesStillBypassWhenGeoipPrivateIsMissing() throws Exception {
        // A geoip.dat with no PRIVATE list at all must not strip LAN bypass.
        writeGeoFile("geosite.dat", "CATEGORY-IR");
        writeGeoFile("geoip.dat", "US");

        String json = XrayConfigBuilder.b(profile(), prefs, null, true, true);
        assertFalse(json.contains("geoip:private"));

        // org.json escapes "/" as "\/", so inspect the parsed rule rather than the text.
        JSONArray rules = new JSONObject(json).getJSONObject("routing").getJSONArray("rules");
        // Since 1.15 the dns_inbound rule leads the list, so locate the LAN rule
        // by its content instead of assuming a fixed index.
        ArrayList<String> cidrs = new ArrayList<>();
        for (int i = 0; i < rules.length(); i++) {
            JSONArray ips = rules.getJSONObject(i).optJSONArray("ip");
            if (ips == null) {
                continue;
            }
            for (int j = 0; j < ips.length(); j++) {
                cidrs.add(ips.getString(j));
            }
            if (cidrs.contains("192.168.0.0/16")) {
                break;
            }
        }
        assertTrue("LAN must still route direct via literal CIDRs",
                cidrs.contains("192.168.0.0/16"));
        assertTrue(cidrs.contains("10.0.0.0/8"));
    }

    @Test
    public void iranBypassSurvivesWithoutGeoipIr() throws Exception {
        writeGeoFile("geosite.dat", "CATEGORY-IR");
        writeGeoFile("geoip.dat", "PRIVATE");

        String json = XrayConfigBuilder.b(profile(), prefs, null, true, true);
        assertFalse("geoip:ir is absent from the lite data", json.contains("geoip:ir"));
        // The suffix rule needs no geo data and must still be there.
        assertTrue(json.contains("domain:ir"));
        assertTrue(json.contains("geosite:category-ir"));
    }

    @Test
    public void configStillBuildsWhenGeoFilesAreEntirelyMissing() throws Exception {
        new File(context.getFilesDir(), "geosite.dat").delete();
        new File(context.getFilesDir(), "geoip.dat").delete();
        GeoIndex.invalidate();

        String json = XrayConfigBuilder.b(profile(), prefs, null, true, true);
        // Not one geo reference may survive, or the core refuses to start.
        assertEquals("no geo refs are permissible with no geo data",
                0, geoRefsIn(json).size());
        assertTrue(json.contains("\"outbounds\""));
    }

    @Test
    public void tagScannerReadsTagsAndCachesUntilTheFileChanges() throws Exception {
        writeGeoFile("geosite.dat", "CATEGORY-IR", "CATEGORY-ADS-ALL");
        assertTrue(GeoIndex.hasGeosite(context, "category-ir"));
        assertTrue(GeoIndex.hasGeosite(context, "category-ads-all"));
        assertFalse(GeoIndex.hasGeosite(context, "category-porn"));

        // Missing files must report "nothing available" rather than throwing.
        new File(context.getFilesDir(), "geoip.dat").delete();
        GeoIndex.invalidate();
        assertFalse(GeoIndex.hasGeoip(context, "private"));
    }
}
