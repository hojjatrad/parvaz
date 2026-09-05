package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses Clash YAML subscription formats into Parvaz Profile models.
 */
public final class ClashParser {

    private ClashParser() {
    }

    /** True if text looks like a Clash configuration. */
    public static boolean isClash(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return t.contains("proxies:") || (t.contains("server:") && t.contains("port:") && t.contains("type:"));
    }

    /** Parses all proxies from a Clash YAML document. */
    public static ArrayList<Profile> parse(String yaml) {
        ArrayList<Profile> list = new ArrayList<>();
        if (yaml == null || yaml.trim().isEmpty()) {
            return list;
        }

        String[] lines = yaml.split("\r?\n");
        boolean inProxies = false;
        Map<String, String> currentMap = null;
        int currentIndent = -1;

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            if (trimmed.equals("proxies:") || trimmed.startsWith("proxies: ")) {
                inProxies = true;
                continue;
            }

            if (inProxies) {
                // If a new top-level key begins (no leading whitespace), we exit proxies section
                if (!rawLine.startsWith(" ") && !rawLine.startsWith("\t") && !rawLine.startsWith("-") && trimmed.endsWith(":")) {
                    if (currentMap != null) {
                        Profile p = mapToProfile(currentMap);
                        if (p != null) list.add(p);
                        currentMap = null;
                    }
                    inProxies = false;
                    continue;
                }

                if (trimmed.startsWith("- ") || trimmed.startsWith("-")) {
                    if (currentMap != null) {
                        Profile p = mapToProfile(currentMap);
                        if (p != null) list.add(p);
                    }
                    currentMap = new HashMap<>();
                    String rest = trimmed.startsWith("- ") ? trimmed.substring(2).trim() : trimmed.substring(1).trim();
                    if (!rest.isEmpty()) {
                        parseKeyValue(rest, currentMap);
                    }
                } else if (currentMap != null) {
                    parseKeyValue(trimmed, currentMap);
                }
            }
        }

        if (currentMap != null) {
            Profile p = mapToProfile(currentMap);
            if (p != null) list.add(p);
        }

        return list;
    }

    private static void parseKeyValue(String line, Map<String, String> map) {
        int colon = line.indexOf(':');
        if (colon > 0) {
            String key = line.substring(0, colon).trim().toLowerCase(java.util.Locale.US);
            String val = line.substring(colon + 1).trim();
            // Strip quotes
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                if (val.length() >= 2) {
                    val = val.substring(1, val.length() - 1);
                }
            }
            map.put(key, val);
        }
    }

    private static Profile mapToProfile(Map<String, String> m) {
        String type = m.get("type");
        if (type == null || type.isEmpty()) {
            return null;
        }
        type = type.toLowerCase(java.util.Locale.US);

        Profile p = LinkParser.newProfile();
        p.protocol = type;
        p.remark = m.getOrDefault("name", m.getOrDefault("server", "Clash Node"));
        p.address = m.getOrDefault("server", "");

        try {
            p.port = Integer.parseInt(m.getOrDefault("port", "443"));
        } catch (Exception e) {
            p.port = 443;
        }

        p.uuid = m.getOrDefault("uuid", m.getOrDefault("password", ""));
        p.encryption = m.getOrDefault("cipher", m.getOrDefault("encryption", "none"));

        String net = m.getOrDefault("network", "tcp").toLowerCase(java.util.Locale.US);
        p.network = net;

        boolean tls = "true".equalsIgnoreCase(m.get("tls")) || "1".equals(m.get("tls"));
        if (tls) {
            p.security = "tls";
        }
        if (m.containsKey("reality-opts") || m.containsKey("public-key") || m.containsKey("pbk")) {
            p.security = "reality";
            p.publicKey = m.getOrDefault("public-key", m.getOrDefault("pbk", ""));
            p.shortId = m.getOrDefault("short-id", m.getOrDefault("sid", ""));
        }

        p.sni = m.getOrDefault("servername", m.getOrDefault("sni", p.address));
        p.host = m.getOrDefault("host", p.sni);
        p.path = m.getOrDefault("path", "/");
        p.flow = m.getOrDefault("flow", "");
        p.fingerprint = m.getOrDefault("client-fingerprint", m.getOrDefault("fp", "chrome"));
        p.alpn = m.getOrDefault("alpn", "");

        if (m.containsKey("alterid")) {
            try {
                p.alterId = Integer.parseInt(m.get("alterid"));
            } catch (Exception ignored) {
            }
        }

        // WireGuard
        if ("wireguard".equals(type) || "wg".equals(type)) {
            p.protocol = "wireguard";
            p.uuid = m.getOrDefault("private-key", p.uuid);
            p.publicKey = m.getOrDefault("public-key", "");
            p.localAddress = m.getOrDefault("ip", "172.16.0.2/32");
            p.presharedKey = m.getOrDefault("preshared-key", "");
        }

        p.normalize();
        return LinkParser.valid(p) ? p : null;
    }
}
