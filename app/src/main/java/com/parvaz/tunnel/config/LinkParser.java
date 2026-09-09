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

    public static final int MAX_INPUT_CHARS = 5 * 1024 * 1024;
    public static final int MAX_PROFILES = 5000;

    /** Compatibility entry point for callers which only need accepted profiles. */
    public static ArrayList<Profile> parseMany(String input) { return parseDetailed(input).profiles; }

    public static ImportResult parseDetailed(String input) {
        ImportResult result = new ImportResult();
        parseInto(input, result, 0);
        return result;
    }

    private static String clean(String input) {
        String text = input == null ? "" : input.trim();
        return text.startsWith("\uFEFF") ? text.substring(1).trim() : text;
    }

    private static void parseInto(String input, ImportResult result, int depth) {
        if (input == null) return;
        if (input.length() > MAX_INPUT_CHARS || depth > 4) {
            throw new IllegalArgumentException("Subscription input exceeds safe limits");
        }
        String text = clean(input);
        if (text.isEmpty()) return;
        if(text.startsWith("\"")) {
            try {parseInto(JsonInput.string(text),result,depth+1);}catch(JSONException e){result.fail("INVALID_JSON_STRING");}
            return;
        }
        if (text.startsWith("{") || text.startsWith("[")) {
            checkJsonDepth(text);
            if (text.startsWith("[")) {
                JSONArray entries;
                try { entries = JsonInput.array(text); }
                catch (JSONException ignored) { result.fail("INVALID_JSON"); return; }
                if (entries.length() > MAX_PROFILES) throw new IllegalArgumentException("Too many subscription profiles");
                for (int i = 0; i < entries.length(); i++) {
                    Object entry = entries.opt(i);
                    if (entry instanceof JSONObject || entry instanceof String || entry instanceof JSONArray) {
                        parseInto(entry.toString(), result, depth + 1);
                    } else result.reject("INVALID_ARRAY_ENTRY", i + 1);
                }
            } else {
                JSONObject root;
                try { root = JsonInput.object(text); }
                catch (JSONException ignored) {
                    if (ClashParser.isClash(text)) result.merge(ClashParser.parseDetailed(text));
                    else result.fail("INVALID_JSON");
                    return;
                }
                if (root.has("proxies")) result.merge(ClashParser.parseDetailed(text));
                else if (SingBoxParser.isSingBox(text)) result.merge(SingBoxParser.parseDetailed(text));
                else if (root.has("links") && !root.has("outbounds")) {
                    // Explicit panel API envelope, e.g. Marzban. No arbitrary JSON-to-server fallback.
                    Object links=root.opt("links");
                    if(!(links instanceof JSONArray)&&!(links instanceof String)){result.fail("INVALID_PANEL_LINKS");return;}
                    parseInto(links.toString(), result, depth + 1);
                } else if(!root.has("outbounds") && (root.has("configs")||root.has("data"))) {
                    Object payload=root.has("configs")?root.opt("configs"):root.opt("data");
                    if(payload instanceof JSONObject||payload instanceof JSONArray||payload instanceof String) {
                        parseInto(payload.toString(),result,depth+1);result.warn("PANEL_ENVELOPE_EXTRACTION",0);
                    } else result.reject("INVALID_PANEL_PAYLOAD",0);
                } else if(root.optJSONArray("outbounds")!=null) {
                    JSONArray outbounds=root.optJSONArray("outbounds");
                    if(outbounds.length()>MAX_PROFILES)throw new IllegalArgumentException("Too many outbounds");
                    for(int i=0;i<outbounds.length();i++) {
                        JSONObject outbound=outbounds.optJSONObject(i);
                        if(outbound==null){result.reject("INVALID_OUTBOUND",i+1);continue;}
                        String protocol=outbound.optString("protocol","");
                        if(protocol.equals("freedom")||protocol.equals("blackhole")||protocol.equals("dns"))continue;
                        try {
                            JSONObject wrapper=new JSONObject();wrapper.put("outbounds",new JSONArray().put(outbound));
                            wrapper.put("remarks",firstNonEmpty(outbound.optString("tag"),root.optString("remarks")));
                            Profile profile=parseRawJson(wrapper.toString());
                            if(profile!=null&&valid(profile))result.add(profile);else result.reject("UNSUPPORTED_OUTBOUND",i+1);
                        }catch(JSONException e){result.reject("INVALID_OUTBOUND",i+1);}
                    }
                    result.warn("RAW_OUTBOUND_EXTRACTION_ONLY",0);
                    if(result.profiles.isEmpty())result.reject("NO_PROXY_OUTBOUNDS",0);
                } else {
                    try {
                        Profile profile = parseRawJson(text);
                        if (profile != null && valid(profile)) {
                            result.add(profile);
                            if (root.has("outbounds")) result.warn("RAW_OUTBOUND_EXTRACTION_ONLY", 0);
                        } else result.reject("NOT_A_SUPPORTED_CONFIG", 0);
                    } catch (JSONException ignored) { result.reject("INVALID_JSON_CONFIG", 0); }
                }
            }
            return;
        }
        if (ClashParser.isClash(text)) { result.merge(ClashParser.parseDetailed(text)); return; }
        if (!text.contains("://")) {
            String decoded = tryBase64(text);
            if (decoded != null && !clean(decoded).isEmpty()) {
                parseInto(decoded, result, depth + 1);
                return;
            }
        }
        int lineNumber = 0;
        for (String rawLine : text.split("[\\r\\n]+")) {
            lineNumber++;
            String line = clean(rawLine);
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            Profile profile;
            try { profile = parseOne(line); }
            catch (Exception ignored) { result.reject("INVALID_SHARE_LINK", lineNumber); continue; }
            if (profile != null && valid(profile)) {
                result.add(profile);
                if (!com.parvaz.tunnel.core.ProtocolSupport.isSupported(profile)) result.warn("CORE_UNSUPPORTED", lineNumber);
            } else result.reject("UNRECOGNIZED_SHARE_LINK", lineNumber);
        }
    }

    /** Reject extreme nesting before handing untrusted input to a recursive JSON parser. */
    public static void checkJsonDepth(String text) {
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 64) throw new IllegalArgumentException("JSON nesting exceeds safe limits");
            } else if (c == '}' || c == ']') depth--;
        }
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
        if (json.length() > MAX_INPUT_CHARS) throw new IllegalArgumentException("JSON input too large");
        checkJsonDepth(json);
        JSONObject root = JsonInput.object(json);
        JSONObject outbound = CustomOutbound.extract(root);
        if (outbound == null) return null;
        Profile profile = newProfile();
        profile.protocol = "custom";
        profile.rawJson = json;
        JSONObject settings = outbound.getJSONObject("settings");
        JSONArray servers = settings.optJSONArray("vnext");
        if (servers == null) servers = settings.optJSONArray("servers");
        if (servers != null && servers.length() > 0) {
            JSONObject server = servers.optJSONObject(0);
            if (server != null) {
                profile.address = server.optString("address", "");
                profile.port = server.optInt("port", 443);
            }
        }
        // Structurally validated WireGuard has an endpoint, not a servers array.
        if (profile.address.isEmpty()) {
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
            JSONObject jSONObject = JsonInput.object(Y);
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
            String methodAndPass = Y2.substring(0, lastIndexOf);
            String hostAndPort = Y2.substring(lastIndexOf + 1);
            int colonMethod = methodAndPass.indexOf(58);
            int colonHost = hostAndPort.lastIndexOf(58);
            if (colonMethod > 0 && colonHost > 0) {
                Profile F2 = newProfile();
                F2.protocol = "shadowsocks";
                F2.encryption = methodAndPass.substring(0, colonMethod);
                F2.uuid = methodAndPass.substring(colonMethod + 1);
                F2.address = hostAndPort.substring(0, colonHost).replace("[", "").replace("]", "");
                try {
                    F2.port = Integer.parseInt(hostAndPort.substring(colonHost + 1).trim());
                } catch (Exception unused) {
                    F2.port = 8388;
                }
                F2.remark = Z.isEmpty() ? F2.address : Z;
                if (!substring.isEmpty()) {
                    applyQuery(F2, Uri.parse("ss://x?".concat(substring)));
                    F2.encryption = methodAndPass.substring(0, colonMethod);
                    F2.uuid = methodAndPass.substring(colonMethod + 1);
                }
                if (valid(F2)) {
                    return F2;
                }
            }
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
