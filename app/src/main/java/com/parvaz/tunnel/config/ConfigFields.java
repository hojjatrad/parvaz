package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;
import java.util.*;
import org.json.*;

/** Strict schema helpers: do not silently repair a bad port or stringify complex credentials. */
final class ConfigFields {
    private ConfigFields() {}
    static final class Invalid extends IllegalArgumentException {
        final String code;
        Invalid(String code) { super(code); this.code = code; }
    }
    static void invalid(String code) { throw new Invalid(code); }
    static String text(JSONObject o, String key, String fallback) {
        Object value = o.opt(key);
        if (value == null || value == JSONObject.NULL) return fallback;
        if (value instanceof JSONObject || value instanceof JSONArray) throw new Invalid("INVALID_FIELD_TYPE");
        return String.valueOf(value);
    }
    static String required(JSONObject o, String key) {
        String value = text(o, key, "");
        if (value.trim().isEmpty()) throw new Invalid("MISSING_REQUIRED_FIELD");
        return value;
    }
    static int integer(JSONObject o, String key, int fallback, int min, int max) {
        if (!o.has(key)) return fallback;
        try {
            int value = Integer.parseInt(text(o, key, ""));
            if (value < min || value > max) throw new Invalid("INVALID_NUMBER");
            return value;
        } catch (NumberFormatException e) { throw new Invalid("INVALID_NUMBER"); }
    }
    static boolean bool(JSONObject o, String key, boolean fallback) {
        if (!o.has(key) || o.isNull(key)) return fallback;
        String value = text(o, key, "");
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) return true;
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) return false;
        throw new Invalid("INVALID_BOOLEAN");
    }
    static JSONObject object(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return new JSONObject();
        JSONObject value = o.optJSONObject(key);
        if (value == null) throw new Invalid("INVALID_OBJECT");
        return value;
    }
    static String list(JSONObject o, String key, String fallback) {
        if (!o.has(key) || o.isNull(key)) return fallback;
        JSONArray a = o.optJSONArray(key);
        if (a == null) return text(o, key, fallback);
        StringBuilder out = new StringBuilder();
        for (int i=0; i<a.length(); i++) {
            Object v=a.opt(i);
            if (!(v instanceof String) && !(v instanceof Number)) throw new Invalid("INVALID_LIST");
            if (i>0) out.append(',');
            out.append(v);
        }
        return out.toString();
    }
    static void keys(JSONObject o, String allowed) {
        Set<String> set = new HashSet<>(Arrays.asList(allowed.split(" ")));
        Iterator<String> it=o.keys();
        while (it.hasNext()) if (!set.contains(it.next())) throw new Invalid("UNSUPPORTED_OPTION");
    }
    static String hostHeader(JSONObject headers) {
        keys(headers, "Host host");
        return text(headers, "Host", text(headers, "host", ""));
    }
    static String cidr(String address, boolean ipv6) {
        if (address.isEmpty()) return "";
        return address.contains("/") ? address : address + (ipv6 ? "/128" : "/32");
    }
    static void reserved(Profile p) {
        if (p.reserved.isEmpty()) return;
        String[] values=p.reserved.split(",",-1);
        if (values.length!=3) throw new Invalid("INVALID_RESERVED");
        try { for (String v:values) {int n=Integer.parseInt(v.trim());if(n<0||n>255)throw new NumberFormatException();} }
        catch(NumberFormatException e){throw new Invalid("INVALID_RESERVED");}
    }
    static void credentials(Profile p) {
        if (!LinkParser.valid(p)) throw new Invalid("INVALID_ENDPOINT");
        switch (p.protocol) {
            case "vless": case "vmess": case "tuic":
                if (!p.uuid.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new Invalid("INVALID_UUID");
                if ("tuic".equals(p.protocol) && p.quicKey.isEmpty()) throw new Invalid("MISSING_PASSWORD");
                break;
            case "trojan": case "shadowsocks": case "hysteria2":
                if(p.uuid.isEmpty()) throw new Invalid("MISSING_PASSWORD");
                if ("shadowsocks".equals(p.protocol) && (p.encryption.isEmpty() || "none".equals(p.encryption))) throw new Invalid("MISSING_CIPHER");
                break;
            case "wireguard":
                if(p.uuid.isEmpty()||p.publicKey.isEmpty()||p.localAddress.isEmpty()) throw new Invalid("MISSING_WIREGUARD_FIELD");
                reserved(p);
                break;
            case "socks": case "http":
                if(p.uuid.isEmpty()&&!p.quicKey.isEmpty())throw new Invalid("PASSWORD_WITHOUT_USERNAME");
                break;
            default: throw new Invalid("UNSUPPORTED_PROTOCOL");
        }
        if ("reality".equals(p.security) && p.publicKey.isEmpty()) throw new Invalid("MISSING_REALITY_KEY");
    }
    static void transport(Profile p) {
        if (!(Arrays.asList("tcp","ws","grpc","h2","http","httpupgrade","xhttp","splithttp","udp","").contains(p.network))) throw new Invalid("UNSUPPORTED_TRANSPORT");
    }
}
