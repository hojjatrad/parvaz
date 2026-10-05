package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/**
 * Builds a sing-box outbound for the protocols Xray normally carries.
 *
 * <p>Until now the engine a server ran on was decided by its protocol alone: VLESS/VMess/
 * Trojan/Shadowsocks could only ever be Xray, and Hysteria2/TUIC/AnyTLS/Snell could only
 * ever be sing-box. This class is the missing half that lets the same server be dialled by
 * the second engine, which is what makes a real per-server core choice - and a failover
 * from one engine to the other - possible instead of decorative.
 *
 * <p>{@link #capable(Profile)} is deliberately conservative: it answers false for every
 * setting sing-box cannot express exactly as Xray would (mKCP, XHTTP, TCP HTTP-obfuscation,
 * non-Vision flows, Trojan flow, encrypted VLESS, TLS on Shadowsocks). A server whose
 * settings cannot be reproduced faithfully is never offered the second engine, because a
 * silently different dial is worse than no choice at all.
 */
public final class SingBoxOutbound {

    private SingBoxOutbound() {
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String or(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    /** True when sing-box can dial this profile with exactly the settings Xray would use. */
    public static boolean capable(Profile profile) {
        if (profile == null || profile.protocol == null) {
            return false;
        }
        String protocol = ProtocolNames.canonical(profile.protocol);
        if (!Arrays.asList("vless", "vmess", "trojan", "shadowsocks", "socks", "http")
                .contains(protocol)) {
            return false;
        }
        if (blank(profile.address) || profile.port < 1 || profile.port > 65535) {
            return false;
        }
        String security = or(profile.security, "none");
        if (!Arrays.asList("none", "tls", "reality").contains(security)) {
            return false;
        }
        if (security.equals("reality")
                && (!protocol.equals("vless") || blank(profile.publicKey))) {
            return false; // REALITY is a VLESS feature and needs the server's public key.
        }
        String network = or(profile.network, "tcp");
        if (!Arrays.asList("tcp", "ws", "grpc", "h2", "http", "httpupgrade").contains(network)) {
            return false; // mKCP, QUIC transport and XHTTP have no sing-box equivalent.
        }
        if (network.equals("tcp") && !or(profile.headerType, "none").equals("none")) {
            return false; // TCP HTTP-obfuscation is Xray only.
        }
        switch (protocol) {
            case "vless":
                // sing-box implements Vision and plain VLESS; nothing else, and the
                // encryption field does not exist there.
                if (!blank(profile.flow) && !profile.flow.trim().equals("xtls-rprx-vision")) {
                    return false;
                }
                return blank(profile.encryption) || profile.encryption.trim().equals("none");
            case "trojan":
                return blank(profile.flow) && !blank(profile.uuid);
            case "vmess":
                return !blank(profile.uuid);
            case "shadowsocks":
                // Shadowsocks has no TLS or transport layer in sing-box.
                return security.equals("none") && network.equals("tcp")
                        && !blank(profile.encryption) && !blank(profile.uuid);
            case "socks":
                return security.equals("none") && network.equals("tcp");
            case "http":
                return network.equals("tcp");
            default:
                return false;
        }
    }

    /** @throws IllegalArgumentException when {@link #capable(Profile)} is false. */
    public static JSONObject build(Profile profile, String tag) throws JSONException {
        if (!capable(profile)) {
            throw new IllegalArgumentException("sing-box cannot reproduce this profile");
        }
        String protocol = ProtocolNames.canonical(profile.protocol);
        JSONObject outbound = new JSONObject()
                .put("type", protocol)
                .put("server", profile.address)
                .put("server_port", profile.port);

        switch (protocol) {
            case "vless":
                outbound.put("uuid", profile.uuid);
                if (!blank(profile.flow)) {
                    outbound.put("flow", profile.flow.trim());
                }
                break;
            case "vmess":
                outbound.put("uuid", profile.uuid)
                        .put("security", or(profile.encryption, "auto"))
                        .put("alter_id", Math.max(0, profile.alterId));
                break;
            case "trojan":
                outbound.put("password", profile.uuid);
                break;
            case "shadowsocks":
                outbound.put("method", profile.encryption.trim())
                        .put("password", profile.uuid);
                break;
            case "socks":
                outbound.put("version", "5");
                if (!blank(profile.uuid)) {
                    outbound.put("username", profile.uuid)
                            .put("password", profile.quicKey == null ? "" : profile.quicKey);
                }
                break;
            default: // http
                if (!blank(profile.uuid)) {
                    outbound.put("username", profile.uuid)
                            .put("password", profile.quicKey == null ? "" : profile.quicKey);
                }
                break;
        }

        JSONObject tls = tls(profile);
        if (tls != null) {
            outbound.put("tls", tls);
        }
        JSONObject transport = transport(profile);
        if (transport != null) {
            outbound.put("transport", transport);
        }
        return outbound.put("tag", tag);
    }

    private static JSONObject tls(Profile profile) throws JSONException {
        String security = or(profile.security, "none");
        if (security.equals("none")) {
            return null;
        }
        String serverName = !blank(profile.sni) ? profile.sni.trim()
                : (!blank(profile.host) && !security.equals("reality") ? profile.host.trim()
                        : profile.address);
        JSONObject tls = new JSONObject()
                .put("enabled", true)
                .put("server_name", serverName)
                .put("insecure", profile.allowInsecure);
        if (!blank(profile.alpn) && !security.equals("reality")) {
            JSONArray alpn = new JSONArray();
            for (String part : profile.alpn.split(",")) {
                if (!part.trim().isEmpty()) {
                    alpn.put(part.trim());
                }
            }
            if (alpn.length() > 0) {
                tls.put("alpn", alpn);
            }
        }
        if (security.equals("reality")) {
            JSONObject reality = new JSONObject()
                    .put("enabled", true)
                    .put("public_key", profile.publicKey.trim());
            if (!blank(profile.shortId)) {
                reality.put("short_id", profile.shortId.trim());
            }
            tls.put("reality", reality);
            // REALITY is defined in terms of a uTLS ClientHello; sing-box requires it.
            tls.put("utls", new JSONObject().put("enabled", true)
                    .put("fingerprint", or(profile.fingerprint, "chrome")));
        } else if (!blank(profile.fingerprint)) {
            tls.put("utls", new JSONObject().put("enabled", true)
                    .put("fingerprint", profile.fingerprint.trim()));
        }
        return tls;
    }

    private static JSONObject transport(Profile profile) throws JSONException {
        String network = or(profile.network, "tcp");
        String path = or(profile.path, "/");
        switch (network) {
            case "ws": {
                JSONObject ws = new JSONObject().put("type", "ws").put("path", path);
                if (!blank(profile.host)) {
                    ws.put("headers", new JSONObject().put("Host", profile.host.trim()));
                }
                return ws;
            }
            case "grpc": {
                String service = profile.serviceName;
                if (blank(service) && !blank(profile.path)) {
                    service = profile.path.replaceFirst("^/", "");
                }
                return new JSONObject().put("type", "grpc")
                        .put("service_name", service == null ? "" : service.trim());
            }
            case "h2":
            case "http": {
                JSONObject http = new JSONObject().put("type", "http").put("path", path);
                if (!blank(profile.host)) {
                    JSONArray hosts = new JSONArray();
                    for (String part : profile.host.split(",")) {
                        if (!part.trim().isEmpty()) {
                            hosts.put(part.trim());
                        }
                    }
                    if (hosts.length() > 0) {
                        http.put("host", hosts);
                    }
                }
                return http;
            }
            case "httpupgrade": {
                JSONObject upgrade = new JSONObject().put("type", "httpupgrade").put("path", path);
                if (!blank(profile.host)) {
                    upgrade.put("host", profile.host.trim());
                }
                return upgrade;
            }
            default:
                return null; // Plain TCP needs no transport object.
        }
    }
}
