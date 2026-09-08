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

    public static boolean isSupported(String protocol) {
        return com.parvaz.tunnel.config.ProtocolNames.hasBuilder(protocol);
    }

    public static boolean isKnownUnsupported(String protocol) {
        String p = com.parvaz.tunnel.config.ProtocolNames.canonical(protocol);
        return !p.isEmpty() && !"custom".equals(p) && !isSupported(p);
    }

    public static boolean isSupported(Profile profile) {
        if (profile == null) return false;
        if (!"custom".equals(com.parvaz.tunnel.config.ProtocolNames.canonical(profile.protocol))) {
            return isSupported(profile.protocol);
        }
        try {
            return com.parvaz.tunnel.config.CustomOutbound.fromJson(profile.rawJson) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isKnownUnsupported(Profile profile) {
        return profile != null && !isSupported(profile);
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
