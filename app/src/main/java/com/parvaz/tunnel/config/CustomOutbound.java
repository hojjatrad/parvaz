package com.parvaz.tunnel.config;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Minimal structural validation for EXTRACTING an Xray outbound, not full-config execution.
 * Unknown/proxy-chain dependencies and transport semantics still require core validation.
 * A panel metadata JSON object must never become a fake custom:443 server.
 */
public final class CustomOutbound {
    private CustomOutbound() {}

    public static JSONObject fromJson(String json) throws JSONException {
        if(json==null || json.length()>LinkParser.MAX_INPUT_CHARS) throw new IllegalArgumentException("Invalid custom JSON size");
        LinkParser.checkJsonDepth(json);
        return extract(JsonInput.object(json));
    }

    public static JSONObject extract(JSONObject root) throws JSONException {
        JSONArray outbounds = root.optJSONArray("outbounds");
        if (outbounds == null) return validate(root); // also allow one explicit outbound
        for (int i = 0; i < outbounds.length(); i++) {
            JSONObject outbound = outbounds.optJSONObject(i);
            if (outbound == null) continue;
            String protocol = outbound.optString("protocol", "");
            String tag = outbound.optString("tag", "");
            if ("freedom".equals(protocol) || "blackhole".equals(protocol) || "dns".equals(protocol)
                    || "direct".equals(tag) || "block".equals(tag)) continue;
            // Do not silently switch to a different server if the first actual outbound is invalid.
            return validate(outbound);
        }
        return null;
    }

    private static JSONObject validate(JSONObject outbound) throws JSONException {
        String protocol = ProtocolNames.canonical(outbound.optString("protocol", ""));
        if (!ProtocolNames.hasBuilder(protocol)) return null;
        JSONObject settings = outbound.optJSONObject("settings");
        if (settings == null) return null;
        if ("wireguard".equals(protocol)) {
            JSONArray peers = settings.optJSONArray("peers");
            if (settings.optString("secretKey", "").isEmpty() || peers == null || peers.length() == 0) return null;
            JSONObject peer = peers.optJSONObject(0);
            if (peer == null || peer.optString("endpoint", "").isEmpty() || peer.optString("publicKey", "").isEmpty()) return null;
        } else {
            JSONArray servers = settings.optJSONArray("vmess".equals(protocol) || "vless".equals(protocol) ? "vnext" : "servers");
            if (servers == null || servers.length() == 0) return null;
            // Validate every server, not just the first display address.
            for (int i = 0; i < servers.length(); i++) {
                JSONObject server = servers.optJSONObject(i);
                if (server == null || server.optString("address", "").trim().isEmpty()) return null;
                int port = server.optInt("port", -1);
                if (port < 1 || port > 65535) return null;
            }
        }
        JSONObject copy = new JSONObject(outbound.toString());
        copy.put("protocol", protocol);
        return copy;
    }
}
