package com.parvaz.tunnel.config;

import android.net.Uri;
import android.util.Base64;

import com.parvaz.tunnel.model.Profile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/**
 * Parses the share links and subscription payloads that Iranian config providers hand
 * out: vmess/vless/trojan/ss/hysteria2/tuic/socks/wireguard links plus raw Xray JSON.
 *
 * <p>R8 merged this class into androidx.work.impl.WorkManagerImplExtKt, so it had to be
 * sliced back out of the decompiled host; parseMany/parseOne/parseRawJson shared one
 * obfuscated name and were rewritten by hand.
 */
public final class LinkParser {

    private LinkParser() {
    }

    /**
     * Parses a whole subscription payload into profiles.
     *
     * <p>Accepts three shapes: a raw Xray JSON config, a base64-wrapped list, or a
     * plain newline-separated list of share links. R8 merged this method with
     * parseRawJson and parseOne onto a single obfuscated name and jadx could not
     * separate them, so this is reconstructed from intent.
     */
    public static ArrayList<Profile> parseMany(String input) {
        ArrayList<Profile> out = new ArrayList<>();
        if (input == null) {
            return out;
        }
        String text = input.trim();
        if (text.isEmpty()) {
            return out;
        }

        // A raw JSON config carries its outbounds inline.
        if (text.startsWith("{")) {
            try {
                Profile profile = parseRawJson(text);
                if (profile != null && valid(profile)) {
                    out.add(profile);
                }
            } catch (Exception ignored) {
                android.util.Log.w("Parvaz/into", "Exception ignored", ignored);
            }
            return out;
        }

        // Subscriptions are usually the whole link list wrapped in base64.
        if (!text.contains("://")) {
            String decoded = tryBase64(text);
            if (decoded != null && !decoded.trim().isEmpty()) {
                text = decoded.trim();
            }
        }

        for (String rawLine : text.split("[\\r\\n]+")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            try {
                Profile profile = parseOne(line);
                if (profile != null && valid(profile)) {
                    out.add(profile);
                }
            } catch (Exception ignored) {
                // A single bad link must never abort the whole import.
            }
        }
        return out;
    }

    /**
     * Parses one share link, dispatching on its scheme. Returns null when the scheme
     * is unknown; throws for a malformed link of a known scheme so the caller can
     * report a useful message.
     */
    public static Profile parseOne(String link) throws JSONException {
        if (link == null) {
            return null;
        }
        String text = link.trim();
        if (text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.US);

        Profile profile;
        if (lower.startsWith("vmess://")) {
            profile = parseVmess(text);
        } else if (lower.startsWith("vless://")) {
            profile = parseVlessLike(text, "vless");
        } else if (lower.startsWith("trojan://")) {
            profile = parseVlessLike(text, "trojan");
        } else if (lower.startsWith("ss://")) {
            profile = parseShadowsocks(text);
        } else if (lower.startsWith("hysteria2://") || lower.startsWith("hy2://")) {
            profile = parseHysteria2(text);
        } else if (lower.startsWith("tuic://")) {
            profile = parseTuic(text);
        } else if (lower.startsWith("socks://") || lower.startsWith("socks5://")) {
            profile = parseSocks(text);
        } else if (lower.startsWith("wg://") || lower.startsWith("wireguard://")) {
            profile = parseWireguard(text);
        } else if (text.startsWith("{")) {
            profile = parseRawJson(text);
        } else {
            return null;
        }

        if (profile != null) {
            profile.rawLink = text;
            profile.normalize();
        }
        return profile;
    }

    /**
     * Wraps a full Xray JSON config as a single "custom" profile, taking the remark
     * from the first real outbound. Freedom/blackhole outbounds are skipped: they are
     * the direct/block helpers, never the actual server.
     */
    public static Profile parseRawJson(String json) throws JSONException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        JSONObject root = new JSONObject(json);

        Profile profile = newProfile();
        profile.protocol = "custom";
        profile.rawJson = json;

        JSONArray outbounds = root.optJSONArray("outbounds");
        if (outbounds != null) {
            for (int i = 0; i < outbounds.length(); i++) {
                JSONObject outbound = outbounds.optJSONObject(i);
                if (outbound == null) {
                    continue;
                }
                String protocol = outbound.optString("protocol", "");
                String tag = outbound.optString("tag", "");
                if ("freedom".equals(protocol) || "blackhole".equals(protocol)
                        || "direct".equals(tag) || "block".equals(tag)) {
                    continue;
                }
                JSONObject settings = outbound.optJSONObject("settings");
                if (settings != null) {
                    JSONArray servers = settings.optJSONArray("vnext");
                    if (servers == null) {
                        servers = settings.optJSONArray("servers");
                    }
                    if (servers != null && servers.length() > 0) {
                        JSONObject server = servers.optJSONObject(0);
                        if (server != null) {
                            profile.address = server.optString("address", "");
                            profile.port = server.optInt("port", 443);
                        }
                    }
                }
                break;
            }
        }

        if (profile.address.isEmpty()) {
            // No recognisable server block; keep it importable but clearly labelled.
            profile.address = "custom";
            profile.port = 443;
        }
        profile.remark = firstNonEmpty(root.optString("remarks"), profile.address, "custom");
        return profile;
    }

    public static Profile newProfile() {
        Profile profile = new Profile();
        profile.id = UUID.randomUUID().toString();
        return profile;
    }

    public static Profile parseVmess(String str) throws JSONException {
        int i;
        int i2;
        String substring = str.substring(8);
        String Y = tryBase64(substring);
        if (Y != null && Y.trim().startsWith("{")) {
            JSONObject jSONObject = new JSONObject(Y);
            Profile F = newProfile();
            F.protocol = "vmess";
            F.remark = firstNonEmpty(jSONObject.optString("ps"), jSONObject.optString("remarks"), jSONObject.optString("add"));
            F.address = jSONObject.optString("add");
            try {
                i = Integer.parseInt(jSONObject.optString("port").trim());
            } catch (Exception unused) {
                i = 443;
            }
            F.port = i;
            F.uuid = jSONObject.optString("id");
            try {
                i2 = Integer.parseInt(jSONObject.optString("aid", "0").trim());
            } catch (Exception unused2) {
                i2 = 0;
            }
            F.alterId = i2;
            F.encryption = firstNonEmpty(jSONObject.optString("scy"), jSONObject.optString("security"), "auto");
            F.network = firstNonEmpty(jSONObject.optString("net"), "tcp");
            F.headerType = firstNonEmpty(jSONObject.optString("type"), "none");
            F.host = jSONObject.optString("host");
            F.path = jSONObject.optString("path");
            String optString = jSONObject.optString("tls");
            F.security = optString;
            if ("none".equalsIgnoreCase(optString)) {
                F.security = "";
            }
            F.sni = firstNonEmpty(jSONObject.optString("sni"), F.host);
            F.alpn = jSONObject.optString("alpn");
            F.fingerprint = jSONObject.optString("fp");
            if ("grpc".equals(F.network)) {
                F.serviceName = firstNonEmpty(F.path, jSONObject.optString("serviceName"));
                F.mode = jSONObject.optString("mode");
            }
            if ("kcp".equals(F.network)) {
                F.seed = F.path;
            }
            if ("quic".equals(F.network)) {
                F.quicSecurity = firstNonEmpty(F.host, "none");
                F.quicKey = F.path;
            }
            if (F.remark.isEmpty()) {
                F.remark = F.address;
            }
            if (valid(F)) {
                return F;
            }
            return null;
        }
        return parseVlessLike("vless://" + substring, "vmess");
    }

    public static Profile parseVlessLike(String str, String str2) throws JSONException {
        String host;
        int i;
        String str3;
        String str4;
        Uri parse = Uri.parse(str);
        Profile F = newProfile();
        F.protocol = str2;
        String str5 = "";
        if (parse.getHost() == null) {
            host = "";
        } else {
            host = parse.getHost();
        }
        F.address = host;
        if (parse.getPort() > 0) {
            i = parse.getPort();
        } else {
            i = 443;
        }
        F.port = i;
        String userInfo = parse.getUserInfo();
        if (userInfo != null) {
            str5 = urlDecode(userInfo);
        }
        F.uuid = str5;
        String fragment = parse.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            str3 = urlDecode(fragment);
        } else {
            str3 = F.address;
        }
        F.remark = str3;
        applyQuery(F, parse);
        if ("trojan".equals(str2) && F.security.isEmpty()) {
            F.security = "tls";
        }
        if ("vless".equals(str2) && ((str4 = F.encryption) == null || str4.isEmpty())) {
            F.encryption = "none";
        }
        if (valid(F)) {
            return F;
        }
        return null;
    }

    public static void applyQuery(Profile profile, Uri uri) throws JSONException {
        boolean z;
        String P = q(uri, "type", "tcp");
        profile.network = P;
        if (P.isEmpty()) {
            profile.network = "tcp";
        }
        String P2 = q(uri, "security", "");
        profile.security = P2;
        if ("none".equalsIgnoreCase(P2)) {
            profile.security = "";
        }
        profile.encryption = q(uri, "encryption", "none");
        profile.flow = q(uri, "flow", "");
        profile.sni = q(uri, "sni", "");
        profile.host = q(uri, "host", "");
        profile.path = q(uri, "path", "");
        profile.alpn = q(uri, "alpn", "");
        profile.fingerprint = q(uri, "fp", "");
        profile.publicKey = q(uri, "pbk", "");
        profile.shortId = q(uri, "sid", "");
        profile.spiderX = q(uri, "spx", "");
        profile.serviceName = q(uri, "serviceName", "");
        profile.headerType = q(uri, "headerType", "none");
        profile.seed = q(uri, "seed", "");
        profile.quicSecurity = q(uri, "quicSecurity", "none");
        profile.quicKey = q(uri, "key", "");
        profile.mode = q(uri, "mode", "");
        String P3 = q(uri, "allowInsecure", "0");
        if (!"1".equals(P3) && !"true".equalsIgnoreCase(P3)) {
            z = false;
        } else {
            z = true;
        }
        profile.allowInsecure = z;
        if (profile.sni.isEmpty()) {
            profile.sni = profile.host;
        }
        if (profile.sni.isEmpty() && !profile.security.isEmpty()) {
            profile.sni = profile.address;
        }
        if (!profile.publicKey.isEmpty() && profile.security.isEmpty()) {
            profile.security = "reality";
        }
    }

    public static Profile parseShadowsocks(String str) throws JSONException {
        String Z;
        String substring;
        int lastIndexOf;
        int i;
        String substring2 = str.substring(5);
        int indexOf = substring2.indexOf(35);
        if (indexOf < 0) {
            Z = "";
        } else {
            Z = urlDecode(substring2.substring(indexOf + 1));
            substring2 = substring2.substring(0, indexOf);
        }
        int indexOf2 = substring2.indexOf(63);
        if (indexOf2 < 0) {
            substring = "";
        } else {
            substring = substring2.substring(indexOf2 + 1);
            substring2 = substring2.substring(0, indexOf2);
        }
        int lastIndexOf2 = substring2.lastIndexOf(64);
        if (lastIndexOf2 >= 0) {
            String substring3 = substring2.substring(0, lastIndexOf2);
            String substring4 = substring2.substring(lastIndexOf2 + 1);
            String Y = tryBase64(substring3);
            if (Y == null) {
                Y = urlDecode(substring3);
            }
            int indexOf3 = Y.indexOf(58);
            if (indexOf3 < 0) {
                return null;
            }
            Profile F = newProfile();
            F.protocol = "shadowsocks";
            F.encryption = Y.substring(0, indexOf3);
            int i2 = indexOf3 + 1;
            F.uuid = Y.substring(i2);
            int lastIndexOf3 = substring4.lastIndexOf(58);
            if (lastIndexOf3 < 0) {
                return null;
            }
            F.address = substring4.substring(0, lastIndexOf3).replace("[", "").replace("]", "");
            try {
                i = Integer.parseInt(substring4.substring(lastIndexOf3 + 1).trim());
            } catch (Exception unused) {
                i = 8388;
            }
            F.port = i;
            if (Z.isEmpty()) {
                Z = F.address;
            }
            F.remark = Z;
            if (!substring.isEmpty()) {
                applyQuery(F, Uri.parse("ss://x?".concat(substring)));
                F.encryption = Y.substring(0, indexOf3);
                F.uuid = Y.substring(i2);
            }
            if (!valid(F)) {
                return null;
            }
            return F;
        }
        String Y2 = tryBase64(substring2);
        if (Y2 != null && (lastIndexOf = Y2.lastIndexOf(64)) >= 0) {
            Y2.substring(0, lastIndexOf);
            Y2.substring(lastIndexOf + 1);
        }
        return null;
    }

    public static Profile parseHysteria2(String str) throws JSONException {
        String host;
        int i;
        String Z;
        boolean z;
        String str2;
        Uri parse = Uri.parse(str);
        Profile F = newProfile();
        F.protocol = "hysteria2";
        if (parse.getHost() == null) {
            host = "";
        } else {
            host = parse.getHost();
        }
        F.address = host;
        if (parse.getPort() > 0) {
            i = parse.getPort();
        } else {
            i = 443;
        }
        F.port = i;
        String userInfo = parse.getUserInfo();
        if (userInfo == null) {
            Z = "";
        } else {
            Z = urlDecode(userInfo);
        }
        F.uuid = Z;
        F.security = "tls";
        F.network = "udp";
        F.encryption = "none";
        F.sni = firstNonEmpty(q(parse, "sni", ""), q(parse, "peer", ""), F.address);
        F.alpn = q(parse, "alpn", "h3");
        F.host = q(parse, "obfs-password", "");
        F.mode = q(parse, "obfs", "");
        String r = firstNonEmpty(q(parse, "insecure", ""), q(parse, "allowInsecure", "0"));
        if (!"1".equals(r) && !"true".equalsIgnoreCase(r)) {
            z = false;
        } else {
            z = true;
        }
        F.allowInsecure = z;
        String fragment = parse.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            str2 = urlDecode(fragment);
        } else {
            str2 = F.address;
        }
        F.remark = str2;
        if (!valid(F)) {
            return null;
        }
        return F;
    }

    public static Profile parseTuic(String str) throws JSONException {
        String host;
        int i;
        String Z;
        String str2;
        Uri parse = Uri.parse(str);
        Profile F = newProfile();
        F.protocol = "tuic";
        if (parse.getHost() == null) {
            host = "";
        } else {
            host = parse.getHost();
        }
        F.address = host;
        if (parse.getPort() > 0) {
            i = parse.getPort();
        } else {
            i = 443;
        }
        F.port = i;
        if (parse.getUserInfo() == null) {
            Z = "";
        } else {
            Z = urlDecode(parse.getUserInfo());
        }
        int indexOf = Z.indexOf(58);
        boolean z = false;
        if (indexOf > 0) {
            F.uuid = Z.substring(0, indexOf);
            F.quicKey = Z.substring(indexOf + 1);
        } else {
            F.uuid = Z;
            F.quicKey = "";
        }
        F.security = "tls";
        F.network = "udp";
        F.encryption = "none";
        F.sni = firstNonEmpty(q(parse, "sni", ""), F.address);
        F.alpn = q(parse, "alpn", "h3");
        F.mode = q(parse, "congestion_control", "bbr");
        F.headerType = q(parse, "udp_relay_mode", "native");
        String r = firstNonEmpty(q(parse, "insecure", ""), q(parse, "allow_insecure", "0"));
        if ("1".equals(r) || "true".equalsIgnoreCase(r)) {
            z = true;
        }
        F.allowInsecure = z;
        String fragment = parse.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            str2 = urlDecode(fragment);
        } else {
            str2 = F.address;
        }
        F.remark = str2;
        if (!valid(F)) {
            return null;
        }
        return F;
    }

    public static Profile parseSocks(String str) throws JSONException {
        String host;
        int i;
        String str2;
        Uri parse = Uri.parse(str);
        Profile F = newProfile();
        F.protocol = "socks";
        if (parse.getHost() == null) {
            host = "";
        } else {
            host = parse.getHost();
        }
        F.address = host;
        if (parse.getPort() > 0) {
            i = parse.getPort();
        } else {
            i = 1080;
        }
        F.port = i;
        F.network = "tcp";
        F.encryption = "none";
        F.security = "";
        String userInfo = parse.getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            String Z = urlDecode(userInfo);
            String Y = tryBase64(Z);
            if (Y != null && Y.indexOf(58) > 0) {
                Z = Y;
            }
            int indexOf = Z.indexOf(58);
            if (indexOf > 0) {
                F.uuid = Z.substring(0, indexOf);
                F.quicKey = Z.substring(indexOf + 1);
            } else {
                F.uuid = Z;
            }
        }
        String fragment = parse.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            str2 = urlDecode(fragment);
        } else {
            str2 = F.address;
        }
        F.remark = str2;
        if (!valid(F)) {
            return null;
        }
        return F;
    }

    public static Profile parseWireguard(String str) throws JSONException {
        String host;
        int i;
        String Z;
        String str2;
        Uri parse = Uri.parse(str);
        Profile F = newProfile();
        F.protocol = "wireguard";
        if (parse.getHost() == null) {
            host = "";
        } else {
            host = parse.getHost();
        }
        F.address = host;
        if (parse.getPort() > 0) {
            i = parse.getPort();
        } else {
            i = 51820;
        }
        F.port = i;
        String userInfo = parse.getUserInfo();
        if (userInfo == null) {
            Z = "";
        } else {
            Z = urlDecode(userInfo);
        }
        F.uuid = Z;
        String fragment = parse.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            str2 = urlDecode(fragment);
        } else {
            str2 = F.address;
        }
        F.remark = str2;
        F.publicKey = q(parse, "publickey", q(parse, "publicKey", q(parse, "pbk", "")));
        F.presharedKey = q(parse, "presharedkey", q(parse, "presharedKey", ""));
        F.localAddress = q(parse, "address", q(parse, "ip", "172.16.0.2/32"));
        F.reserved = q(parse, "reserved", "");
        try {
            F.wgMtu = Integer.parseInt(q(parse, "mtu", "1420").trim());
        } catch (Exception unused) {
            F.wgMtu = 1420;
        }
        F.network = "";
        F.security = "";
        if (!F.uuid.isEmpty() && !F.publicKey.isEmpty() && valid(F)) {
            return F;
        }
        return null;
    }

    public static boolean valid(Profile profile) {
        int i;
        if (!profile.address.isEmpty() && (i = profile.port) > 0 && i < 65536) {
            return true;
        }
        return false;
    }

    public static String q(Uri uri, String str, String str2) {
        try {
            String queryParameter = uri.getQueryParameter(str);
            if (queryParameter != null) {
                return queryParameter;
            }
        } catch (Exception unused) {
            android.util.Log.w("Parvaz/into", "Exception ignored", unused);
        }
        return str2;
    }

    public static String tryBase64(String str) {
        if (str == null) {
            return null;
        }
        try {
            String cleaned = str.trim().replace('-', '+').replace('_', '/').replaceAll("\\s", "");
            int remainder = cleaned.length() % 4;
            if (remainder == 1) {
                return null;
            }
            if (remainder != 0) {
                cleaned = cleaned + "====".substring(remainder);
            }
            byte[] decoded = Base64.decode(cleaned, 0);
            if (decoded == null || decoded.length == 0) {
                return null;
            }
            String out = new String(decoded, "UTF-8");
            for (int i = 0; i < out.length(); i++) {
                char c = out.charAt(i);
                if (c == 0 || (c >= 0 && c < '\t')) {
                    return null;
                }
            }
            return out;
        } catch (Exception unused) {
            return null;
        }
    }

    public static String urlDecode(String str) {
        try {
            return URLDecoder.decode(str, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException unused) {
            return str;
        }
    }

    public static String firstNonEmpty(String... strArr) {
        for (String str : strArr) {
            if (str != null && !str.isEmpty() && !"null".equals(str)) {
                return str;
            }
        }
        return "";
    }
}
