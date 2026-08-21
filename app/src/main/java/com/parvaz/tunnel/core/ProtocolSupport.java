package com.parvaz.tunnel.core;

import com.parvaz.tunnel.model.Profile;

/**
 * Single source of truth for which protocols the bundled Xray core can actually
 * dial.
 *
 * <p>Hysteria2 and TUIC links parse cleanly and store fine, so until now they
 * appeared in the server list, accepted a ping, and only failed at the moment
 * the user pressed connect. Surfacing the limitation at import time — and
 * marking the row in the list — is far kinder than a late error toast.
 */
public final class ProtocolSupport {

    private ProtocolSupport() {
    }

    /** Protocols the built-in core dials natively. */
    private static final String[] SUPPORTED = {
        "vmess", "vless", "trojan", "shadowsocks", "ss", "socks", "http", "wireguard",
    };

    /**
     * Protocols we can parse and store but cannot dial without swapping in a
     * sing-box core.
     */
    private static final String[] UNSUPPORTED = {
        "hysteria2", "hy2", "tuic",
    };

    public static boolean isSupported(String protocol) {
        if (protocol == null || protocol.isEmpty()) {
            return false;
        }
        String p = protocol.trim().toLowerCase(java.util.Locale.US);
        for (String s : SUPPORTED) {
            if (s.equals(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isKnownUnsupported(String protocol) {
        if (protocol == null || protocol.isEmpty()) {
            return false;
        }
        String p = protocol.trim().toLowerCase(java.util.Locale.US);
        for (String s : UNSUPPORTED) {
            if (s.equals(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSupported(Profile profile) {
        return profile != null && isSupported(profile.protocol);
    }

    public static boolean isKnownUnsupported(Profile profile) {
        return profile != null && isKnownUnsupported(profile.protocol);
    }

    /**
     * Counts profiles in a list that cannot be dialled, so the importer can warn
     * once with a total instead of once per server.
     */
    public static int countUnsupported(java.util.List<Profile> profiles) {
        if (profiles == null) {
            return 0;
        }
        int n = 0;
        for (Profile p : profiles) {
            if (isKnownUnsupported(p)) {
                n++;
            }
        }
        return n;
    }

    /** Display label for the unsupported badge, e.g. "HYSTERIA2". */
    public static String badge(Profile profile) {
        if (profile == null || profile.protocol == null) {
            return "";
        }
        return profile.protocol.trim().toUpperCase(java.util.Locale.US);
    }
}
