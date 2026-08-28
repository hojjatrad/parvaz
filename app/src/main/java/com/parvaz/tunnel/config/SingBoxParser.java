package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Parses Sing-box JSON configuration format into Parvaz Profile models.
 */
public final class SingBoxParser {

    private SingBoxParser() {
    }

    /** True if JSON looks like a Sing-box config with outbounds. */
    public static boolean isSingBox(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return t.startsWith("{") && t.contains("\"outbounds\"") && (t.contains("\"server\"") || t.contains("\"server_port\""));
    }

    public static ArrayList<Profile> parse(String json) {
        ArrayList<Profile> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray outbounds = root.optJSONArray("outbounds");
            if (outbounds == null) {
                return list;
            }

            for (int i = 0; i < outbounds.length(); i++) {
                JSONObject o = outbounds.optJSONObject(i);
                if (o == null) continue;
                String type = o.optString("type", "").toLowerCase(java.util.Locale.US);
                if (type.equals("direct") || type.equals("block") || type.equals("dns") || type.equals("selector") || type.equals("urltest")) {
                    continue;
                }

                Profile p = LinkParser.newProfile();
                p.protocol = type;
                p.remark = o.optString("tag", o.optString("server", "SingBox Node"));
                p.address = o.optString("server", "");
                p.port = o.optInt("server_port", o.optInt("port", 443));
                p.uuid = o.optString("uuid", o.optString("password", ""));
                p.encryption = o.optString("method", o.optString("security", "none"));

                JSONObject tls = o.optJSONObject("tls");
                if (tls != null && tls.optBoolean("enabled", false)) {
                    p.security = "tls";
                    p.sni = tls.optString("server_name", p.address);
                    p.allowInsecure = tls.optBoolean("insecure", false);
                    JSONObject reality = tls.optJSONObject("reality");
                    if (reality != null && reality.optBoolean("enabled", false)) {
                        p.security = "reality";
                        p.publicKey = reality.optString("public_key", "");
                        p.shortId = reality.optString("short_id", "");
                    }
                    JSONObject utls = tls.optJSONObject("utls");
                    if (utls != null) {
                        p.fingerprint = utls.optString("fingerprint", "chrome");
                    }
                }

                JSONObject transport = o.optJSONObject("transport");
                if (transport != null) {
                    String net = transport.optString("type", "tcp");
                    p.network = net;
                    p.path = transport.optString("path", "/");
                    p.serviceName = transport.optString("service_name", "");
                    JSONObject headers = transport.optJSONObject("headers");
                    if (headers != null) {
                        p.host = headers.optString("Host", headers.optString("host", p.sni));
                    }
                }

                p.flow = o.optString("flow", "");
                p.normalize();
                if (LinkParser.valid(p)) {
                    list.add(p);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
