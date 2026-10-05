package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** One ordered answer per server: what runs it now, and what is tried if that fails. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 34})
public class CoreSelectionTest {

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = new Prefs(context).f343a;
        prefs.edit().clear().apply();
    }

    private static Profile vless() {
        Profile p = new Profile();
        p.id = "a";
        p.protocol = "vless";
        p.address = "edge.invalid";
        p.port = 443;
        p.uuid = "11111111-1111-4111-8111-111111111111";
        p.security = "tls";
        p.sni = "edge.invalid";
        return p;
    }

    private static Profile of(String protocol) {
        Profile p = vless();
        p.protocol = protocol;
        p.quicKey = "pw";
        return p;
    }

    @Test
    public void defaultsAreExactlyTheBehaviourFromBeforeTheChoiceExisted() {
        assertEquals(CoreSelection.XRAY, CoreSelection.active(prefs, vless()));
        assertEquals(CoreSelection.SINGBOX, CoreSelection.active(prefs, of("hysteria2")));
        assertEquals(CoreSelection.SINGBOX, CoreSelection.active(prefs, of("snell")));
        assertFalse(CoreSelection.external(prefs, vless()));
        assertTrue(CoreSelection.external(prefs, of("tuic")));
    }

    @Test
    public void bothEnginesAreOfferedOnlyWhenBothCanDialTheSameServer() {
        assertEquals(2, CoreSelection.options(vless()).size());
        assertEquals(CoreSelection.XRAY, CoreSelection.options(vless()).get(0));

        Profile kcp = vless();
        kcp.network = "kcp";
        assertEquals(1, CoreSelection.options(kcp).size());
        assertEquals(CoreSelection.XRAY, CoreSelection.options(kcp).get(0));

        assertEquals(1, CoreSelection.options(of("hysteria2")).size());
        assertEquals(CoreSelection.SINGBOX, CoreSelection.options(of("hysteria2")).get(0));
    }

    @Test
    public void anExplicitChoiceWinsAndTheOtherEngineRemainsAsFailover() {
        Profile profile = vless();
        CoreSelection.choose(prefs, profile, CoreSelection.SINGBOX);
        assertEquals(CoreSelection.SINGBOX, CoreSelection.choice(prefs, profile));
        List<String> order = CoreSelection.order(prefs, profile);
        assertEquals(CoreSelection.SINGBOX, order.get(0));
        assertEquals(CoreSelection.XRAY, order.get(1));
        assertTrue(CoreSelection.external(prefs, profile));

        CoreSelection.choose(prefs, profile, CoreSelection.AUTO);
        assertEquals(CoreSelection.AUTO, CoreSelection.choice(prefs, profile));
        assertEquals(CoreSelection.XRAY, CoreSelection.active(prefs, profile));
    }

    @Test
    public void anImpossibleChoiceIsIgnoredRatherThanBreakingTheServer() {
        Profile hysteria = of("hysteria2");
        CoreSelection.choose(prefs, hysteria, CoreSelection.XRAY);
        assertEquals(CoreSelection.AUTO, CoreSelection.choice(prefs, hysteria));
        assertEquals(CoreSelection.SINGBOX, CoreSelection.active(prefs, hysteria));

        Profile kcp = vless();
        kcp.network = "kcp";
        CoreSelection.choose(prefs, kcp, CoreSelection.SINGBOX);
        assertEquals(CoreSelection.XRAY, CoreSelection.active(prefs, kcp));
    }

    @Test
    public void theEngineThatLastWorkedIsTriedFirstNextTime() {
        Profile profile = vless();
        CoreSelection.remember(prefs, profile, CoreSelection.SINGBOX);
        assertEquals(CoreSelection.SINGBOX, CoreSelection.active(prefs, profile));
        // The natural engine stays as the failover, so nothing is lost by remembering.
        assertEquals(CoreSelection.XRAY, CoreSelection.order(prefs, profile).get(1));

        CoreSelection.remember(prefs, profile, CoreSelection.XRAY);
        assertEquals(CoreSelection.XRAY, CoreSelection.active(prefs, profile));

        CoreSelection.remember(prefs, profile, CoreSelection.SINGBOX);
        CoreSelection.forget(prefs, profile.id);
        assertEquals(CoreSelection.XRAY, CoreSelection.active(prefs, profile));
    }

    @Test
    public void anExplicitChoiceOutranksTheRememberedEngine() {
        Profile profile = vless();
        CoreSelection.remember(prefs, profile, CoreSelection.SINGBOX);
        CoreSelection.choose(prefs, profile, CoreSelection.XRAY);
        assertEquals(CoreSelection.XRAY, CoreSelection.active(prefs, profile));
    }

    @Test
    public void fullConfigurationsStayOnTheEngineThatCanReadThem() {
        Profile clash = of("full-clash");
        assertTrue(CoreSelection.mihomoCapable(clash));
        assertEquals(CoreSelection.MIHOMO, CoreSelection.natural(clash));
        assertEquals(1, CoreSelection.options(clash).size());
        CoreSelection.choose(prefs, clash, CoreSelection.XRAY);
        assertEquals(CoreSelection.MIHOMO, CoreSelection.active(prefs, clash));
        assertTrue(CoreSelection.external(prefs, clash));

        Profile xray = of("full-xray");
        assertEquals(CoreSelection.XRAY, CoreSelection.natural(xray));
        assertFalse(CoreSelection.external(prefs, xray));
    }

    @Test
    public void labelsAreReadableAndChoicesAreScopedToOneServer() {
        assertEquals("sing-box", CoreSelection.label(CoreSelection.SINGBOX));
        assertEquals("Xray", CoreSelection.label(CoreSelection.XRAY));
        assertEquals("mihomo", CoreSelection.label(CoreSelection.MIHOMO));
        assertEquals("Auto", CoreSelection.label(CoreSelection.AUTO));

        Profile first = vless();
        Profile second = vless();
        second.id = "b";
        CoreSelection.choose(prefs, first, CoreSelection.SINGBOX);
        assertEquals(CoreSelection.SINGBOX, CoreSelection.active(prefs, first));
        assertEquals(CoreSelection.XRAY, CoreSelection.active(prefs, second));
    }
}
