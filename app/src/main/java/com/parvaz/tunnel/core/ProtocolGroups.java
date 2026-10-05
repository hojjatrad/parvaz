package com.parvaz.tunnel.core;

import com.parvaz.tunnel.config.ProtocolNames;
import com.parvaz.tunnel.model.Profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Groups a server list by protocol family so a long mixed list stays usable.
 *
 * <p>Until now the list could only be filtered by favourite or free text, so a user with
 * fifty mixed nodes had no way to look at just the VLESS entries, or to ask for the best
 * Shadowsocks server. This class answers three questions with no Android dependency, which
 * keeps it unit testable: which group a profile belongs to, how many rows each group has,
 * and whether a row passes the currently selected group filter.
 *
 * <p>The group key is the canonical protocol name, so storage, import and capability checks
 * all agree with what the UI shows. Raw full configurations are deliberately collapsed into
 * one {@code full} group: their inner protocol is decided by the external engine at run time
 * and claiming a specific family for them would be a guess.
 */
public final class ProtocolGroups {

    /** Sentinel meaning "no group filter"; stored in preferences as an empty string. */
    public static final String ALL = "";
    /** Raw Clash / sing-box / Xray configurations, whatever they carry inside. */
    public static final String FULL = "full";
    /** A protocol this build has no builder and no external engine for. */
    public static final String OTHER = "other";

    private ProtocolGroups() {
    }

    /** Canonical group key of one profile; never null. */
    public static String groupOf(Profile profile) {
        if (profile == null) {
            return OTHER;
        }
        String protocol = ProtocolNames.canonical(profile.protocol);
        if (protocol.isEmpty()) {
            return OTHER;
        }
        if (protocol.startsWith("full-")) {
            return FULL;
        }
        switch (protocol) {
            case "vless":
            case "vmess":
            case "trojan":
            case "shadowsocks":
            case "hysteria2":
            case "tuic":
            case "wireguard":
            case "socks":
            case "http":
                return protocol;
            default:
                return ProtocolNames.hasEngine(protocol) ? protocol : OTHER;
        }
    }

    /** Human label for a group key. Protocol names are brand names, so they stay Latin. */
    public static String labelOf(String group) {
        if (group == null || group.isEmpty()) {
            return "";
        }
        switch (group) {
            case "vless": return "VLESS";
            case "vmess": return "VMess";
            case "trojan": return "Trojan";
            case "shadowsocks": return "Shadowsocks";
            case "hysteria2": return "Hysteria2";
            case "tuic": return "TUIC";
            case "wireguard": return "WireGuard";
            case "socks": return "SOCKS";
            case "http": return "HTTP";
            case FULL: return "Config";
            case OTHER: return "?";
            default:
                return group.substring(0, 1).toUpperCase(java.util.Locale.US) + group.substring(1);
        }
    }

    /**
     * How many rows each group has, ordered by size descending and then by label, so the
     * biggest families are offered first in the picker.
     */
    public static Map<String, Integer> counts(List<Profile> profiles) {
        Map<String, Integer> tally = new TreeMap<>();
        if (profiles != null) {
            for (Profile profile : profiles) {
                if (profile == null) {
                    continue;
                }
                String group = groupOf(profile);
                Integer previous = tally.get(group);
                tally.put(group, previous == null ? 1 : previous + 1);
            }
        }
        List<Map.Entry<String, Integer>> ordered = new ArrayList<>(tally.entrySet());
        Collections.sort(ordered, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                if (!a.getValue().equals(b.getValue())) {
                    return b.getValue() - a.getValue();
                }
                return labelOf(a.getKey()).compareToIgnoreCase(labelOf(b.getKey()));
            }
        });
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : ordered) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** True when the row must stay visible under this filter. An empty filter shows all. */
    public static boolean matches(Profile profile, String filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        return profile != null && groupOf(profile).equals(filter);
    }

    /** The rows of one group, in the order they were given. */
    public static List<Profile> filter(List<Profile> profiles, String group) {
        List<Profile> kept = new ArrayList<>();
        if (profiles == null) {
            return kept;
        }
        for (Profile profile : profiles) {
            if (profile != null && matches(profile, group)) {
                kept.add(profile);
            }
        }
        return kept;
    }

    /**
     * A stored filter is only honoured while that group still has rows; a subscription
     * refresh that drops every Hysteria2 node must not leave the user with an empty list
     * and no visible reason for it.
     */
    public static String sanitize(String stored, List<Profile> profiles) {
        if (stored == null || stored.isEmpty()) {
            return ALL;
        }
        if (profiles != null) {
            for (Profile profile : profiles) {
                if (profile != null && groupOf(profile).equals(stored)) {
                    return stored;
                }
            }
        }
        return ALL;
    }
}
