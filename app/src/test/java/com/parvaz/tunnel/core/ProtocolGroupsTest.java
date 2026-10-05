package com.parvaz.tunnel.core;

import com.parvaz.tunnel.model.Profile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Grouping a mixed list by protocol family. Pure logic, so it runs without Robolectric:
 * the UI only ever asks these three questions - which group, how many, does it pass.
 */
public class ProtocolGroupsTest {

    private static Profile profile(String id, String protocol) {
        Profile p = new Profile();
        p.id = id;
        p.protocol = protocol;
        p.address = id + ".invalid";
        p.port = 443;
        return p;
    }

    @Test
    public void aliasesCollapseIntoTheCanonicalGroup() {
        assertEquals("shadowsocks", ProtocolGroups.groupOf(profile("a", "ss")));
        assertEquals("shadowsocks", ProtocolGroups.groupOf(profile("b", "Shadowsocks")));
        assertEquals("hysteria2", ProtocolGroups.groupOf(profile("c", "hy2")));
        assertEquals("wireguard", ProtocolGroups.groupOf(profile("d", "wg")));
        assertEquals("socks", ProtocolGroups.groupOf(profile("e", "socks5")));
    }

    @Test
    public void rawConfigsShareOneGroupAndUnknownProtocolsAreOther() {
        assertEquals(ProtocolGroups.FULL, ProtocolGroups.groupOf(profile("a", "full-clash")));
        assertEquals(ProtocolGroups.FULL, ProtocolGroups.groupOf(profile("b", "full-singbox")));
        assertEquals(ProtocolGroups.FULL, ProtocolGroups.groupOf(profile("c", "full-xray")));
        assertEquals(ProtocolGroups.OTHER, ProtocolGroups.groupOf(profile("d", "custom")));
        assertEquals(ProtocolGroups.OTHER, ProtocolGroups.groupOf(profile("e", "")));
        assertEquals(ProtocolGroups.OTHER, ProtocolGroups.groupOf(null));
    }

    @Test
    public void externalEngineProtocolsKeepTheirOwnGroup() {
        assertEquals("hysteria2", ProtocolGroups.groupOf(profile("a", "hysteria2")));
        assertEquals("tuic", ProtocolGroups.groupOf(profile("b", "tuic")));
    }

    @Test
    public void labelsAreTheBrandNamesUsersLookFor() {
        assertEquals("VLESS", ProtocolGroups.labelOf("vless"));
        assertEquals("VMess", ProtocolGroups.labelOf("vmess"));
        assertEquals("Shadowsocks", ProtocolGroups.labelOf("shadowsocks"));
        assertEquals("Hysteria2", ProtocolGroups.labelOf("hysteria2"));
        assertEquals("WireGuard", ProtocolGroups.labelOf("wireguard"));
        assertEquals("", ProtocolGroups.labelOf(ProtocolGroups.ALL));
    }

    @Test
    public void countsAreOrderedBySizeThenLabel() {
        List<Profile> list = Arrays.asList(
                profile("1", "vless"), profile("2", "vless"), profile("3", "vless"),
                profile("4", "ss"), profile("5", "shadowsocks"),
                profile("6", "trojan"));
        Map<String, Integer> counts = ProtocolGroups.counts(list);
        assertEquals(Arrays.asList("vless", "shadowsocks", "trojan"),
                new ArrayList<>(counts.keySet()));
        assertEquals(Integer.valueOf(3), counts.get("vless"));
        assertEquals(Integer.valueOf(2), counts.get("shadowsocks"));
        assertEquals(Integer.valueOf(1), counts.get("trojan"));
    }

    @Test
    public void countsToleratesNullsAndEmptyInput() {
        assertTrue(ProtocolGroups.counts(null).isEmpty());
        assertTrue(ProtocolGroups.counts(Collections.<Profile>emptyList()).isEmpty());
        assertEquals(1, ProtocolGroups.counts(Arrays.asList(null, profile("a", "vmess"))).size());
    }

    @Test
    public void emptyFilterKeepsEveryRow() {
        Profile p = profile("a", "vless");
        assertTrue(ProtocolGroups.matches(p, ProtocolGroups.ALL));
        assertTrue(ProtocolGroups.matches(p, null));
        List<Profile> all = Arrays.asList(p, profile("b", "trojan"));
        assertEquals(2, ProtocolGroups.filter(all, ProtocolGroups.ALL).size());
    }

    @Test
    public void filterKeepsOnlyTheChosenFamilyAndItsOrder() {
        List<Profile> all = Arrays.asList(
                profile("1", "vless"), profile("2", "trojan"),
                profile("3", "ss"), profile("4", "vless"));
        List<Profile> kept = ProtocolGroups.filter(all, "vless");
        assertEquals(2, kept.size());
        assertEquals("1", kept.get(0).id);
        assertEquals("4", kept.get(1).id);
        assertFalse(ProtocolGroups.matches(profile("5", "trojan"), "vless"));
    }

    @Test
    public void filterOnNullListIsEmptyNotACrash() {
        assertTrue(ProtocolGroups.filter(null, "vless").isEmpty());
    }

    @Test
    public void aStoredGroupThatNoLongerExistsIsDropped() {
        List<Profile> all = Arrays.asList(profile("1", "vless"), profile("2", "trojan"));
        assertEquals("vless", ProtocolGroups.sanitize("vless", all));
        assertEquals(ProtocolGroups.ALL, ProtocolGroups.sanitize("hysteria2", all));
        assertEquals(ProtocolGroups.ALL, ProtocolGroups.sanitize("vless", null));
        assertEquals(ProtocolGroups.ALL,
                ProtocolGroups.sanitize("vless", Collections.<Profile>emptyList()));
        assertEquals(ProtocolGroups.ALL, ProtocolGroups.sanitize(null, all));
    }
}
