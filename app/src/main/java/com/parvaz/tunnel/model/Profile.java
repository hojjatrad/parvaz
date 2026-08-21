package com.parvaz.tunnel.model;

import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class Profile {
    public String id = "";
    public String remark = "";
    public String protocol = "";
    public String address = "";
    public int port = 443;
    public String uuid = "";
    public String encryption = "none";
    public int alterId = 0;
    public String flow = "";
    public String network = "tcp";
    public String security = "";
    public String sni = "";
    public String host = "";
    public String path = "";
    public String alpn = "";
    public String fingerprint = "";
    public String publicKey = "";
    public String shortId = "";
    public String spiderX = "";
    public String serviceName = "";
    public String headerType = "none";
    public String seed = "";
    public String quicSecurity = "none";
    public String quicKey = "";
    public String mode = "";
    public boolean allowInsecure = false;
    public String subscriptionId = "";
    public String rawLink = "";
    public String rawJson = "";
    public String localAddress = "";
    public String presharedKey = "";
    public String reserved = "";
    public int wgMtu = 1420;
    public transient int ping = -1;

    public static Profile fromJson(JSONObject jSONObject) {
        Profile profile = new Profile();
        profile.id = jSONObject.optString("id", "");
        profile.remark = jSONObject.optString("remark", "");
        profile.protocol = jSONObject.optString("protocol", "");
        profile.address = jSONObject.optString("address", "");
        profile.port = jSONObject.optInt("port", 443);
        profile.uuid = jSONObject.optString("uuid", "");
        profile.encryption = jSONObject.optString("encryption", "none");
        profile.alterId = jSONObject.optInt("alterId", 0);
        profile.flow = jSONObject.optString("flow", "");
        profile.network = jSONObject.optString("network", "tcp");
        profile.security = jSONObject.optString("security", "");
        profile.sni = jSONObject.optString("sni", "");
        profile.host = jSONObject.optString("host", "");
        profile.path = jSONObject.optString("path", "");
        profile.alpn = jSONObject.optString("alpn", "");
        profile.fingerprint = jSONObject.optString("fingerprint", "");
        profile.rawLink = jSONObject.optString("rawLink", "");
        profile.publicKey = jSONObject.optString("publicKey", "");
        profile.shortId = jSONObject.optString("shortId", "");
        profile.spiderX = jSONObject.optString("spiderX", "");
        profile.serviceName = jSONObject.optString("serviceName", "");
        profile.headerType = jSONObject.optString("headerType", "none");
        profile.seed = jSONObject.optString("seed", "");
        profile.quicSecurity = jSONObject.optString("quicSecurity", "none");
        profile.quicKey = jSONObject.optString("quicKey", "");
        profile.mode = jSONObject.optString("mode", "");
        profile.allowInsecure = jSONObject.optBoolean("allowInsecure", false);
        profile.subscriptionId = jSONObject.optString("subscriptionId", "");
        profile.rawJson = jSONObject.optString("rawJson", "");
        profile.localAddress = jSONObject.optString("localAddress", "");
        profile.presharedKey = jSONObject.optString("presharedKey", "");
        profile.reserved = jSONObject.optString("reserved", "");
        profile.wgMtu = jSONObject.optInt("wgMtu", 1420);
        return profile.normalize();
    }

    private static String safe(String str, String str2) {
        return (str == null || "null".equals(str)) ? str2 : str;
    }

    public String badge() {
        String str;
        String safe = safe(this.protocol, "");
        String safe2 = safe(this.network, "");
        if ("custom".equals(safe)) {
            return "JSON";
        }
        if ("wireguard".equals(safe)) {
            return "WIREGUARD";
        }
        StringBuilder sb = new StringBuilder(safe.toUpperCase());
        if (!safe2.isEmpty() && !"tcp".equals(safe2)) {
            sb.append(" · ");
            sb.append(safe2.toUpperCase());
        }
        // jadx inverted this branch: REALITY must win, TLS is the fallback.
        if ("reality".equals(this.security)) {
            sb.append(" · REALITY");
        } else if ("tls".equals(this.security)) {
            sb.append(" · TLS");
        }
        return sb.toString();
    }

    public String displayAddress() {
        return safe(this.address, "") + ":" + this.port;
    }

    public Profile normalize() {
        String safe = safe(this.id, "");
        this.id = safe;
        if (safe.isEmpty()) {
            this.id = UUID.randomUUID().toString();
        }
        this.remark = safe(this.remark, "");
        this.protocol = safe(this.protocol, "");
        this.address = safe(this.address, "");
        this.uuid = safe(this.uuid, "");
        this.encryption = safe(this.encryption, "none");
        this.flow = safe(this.flow, "");
        this.network = safe(this.network, "");
        this.security = safe(this.security, "");
        this.sni = safe(this.sni, "");
        this.host = safe(this.host, "");
        this.path = safe(this.path, "");
        this.alpn = safe(this.alpn, "");
        this.fingerprint = safe(this.fingerprint, "");
        this.publicKey = safe(this.publicKey, "");
        this.shortId = safe(this.shortId, "");
        this.spiderX = safe(this.spiderX, "");
        this.serviceName = safe(this.serviceName, "");
        this.headerType = safe(this.headerType, "none");
        this.seed = safe(this.seed, "");
        this.quicSecurity = safe(this.quicSecurity, "none");
        this.quicKey = safe(this.quicKey, "");
        this.mode = safe(this.mode, "");
        this.subscriptionId = safe(this.subscriptionId, "");
        this.rawLink = safe(this.rawLink, "");
        this.rawJson = safe(this.rawJson, "");
        this.localAddress = safe(this.localAddress, "");
        this.presharedKey = safe(this.presharedKey, "");
        this.reserved = safe(this.reserved, "");
        int i = this.port;
        if (i <= 0 || i > 65535) {
            this.port = 443;
        }
        return this;
    }

    public JSONObject toJson() throws JSONException {
        normalize();
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("id", this.id);
        jSONObject.put("rawLink", this.rawLink);
        jSONObject.put("remark", this.remark);
        jSONObject.put("protocol", this.protocol);
        jSONObject.put("address", this.address);
        jSONObject.put("port", this.port);
        jSONObject.put("uuid", this.uuid);
        jSONObject.put("encryption", this.encryption);
        jSONObject.put("alterId", this.alterId);
        jSONObject.put("flow", this.flow);
        jSONObject.put("network", this.network);
        jSONObject.put("security", this.security);
        jSONObject.put("sni", this.sni);
        jSONObject.put("host", this.host);
        jSONObject.put("path", this.path);
        jSONObject.put("alpn", this.alpn);
        jSONObject.put("fingerprint", this.fingerprint);
        jSONObject.put("publicKey", this.publicKey);
        jSONObject.put("shortId", this.shortId);
        jSONObject.put("spiderX", this.spiderX);
        jSONObject.put("serviceName", this.serviceName);
        jSONObject.put("headerType", this.headerType);
        jSONObject.put("seed", this.seed);
        jSONObject.put("quicSecurity", this.quicSecurity);
        jSONObject.put("quicKey", this.quicKey);
        jSONObject.put("mode", this.mode);
        jSONObject.put("allowInsecure", this.allowInsecure);
        jSONObject.put("subscriptionId", this.subscriptionId);
        if (!this.rawJson.isEmpty()) {
            jSONObject.put("rawJson", this.rawJson);
        }
        if (!this.localAddress.isEmpty()) {
            jSONObject.put("localAddress", this.localAddress);
        }
        if (!this.presharedKey.isEmpty()) {
            jSONObject.put("presharedKey", this.presharedKey);
        }
        if (!this.reserved.isEmpty()) {
            jSONObject.put("reserved", this.reserved);
        }
        jSONObject.put("wgMtu", this.wgMtu);
        return jSONObject;
    }
}
