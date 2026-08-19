package com.parvaz.tunnel.config;

import android.content.SharedPreferences;

import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* renamed from: Q1.a */
/* loaded from: classes.dex */
public final class XrayConfigBuilder {

    /** Local SOCKS inbound the tunnel and in-app probes dial through. */
    public static final int SOCKS_PORT = 10808;

    /** Local HTTP inbound, also published as the system HTTP proxy on API 29+. */
    public static final int HTTP_PORT = 10809;

    /* JADX WARN: Can't change package for inner class: Q1.a.a to com.parvaz.tunnel.config.XrayConfigBuilder$UnsupportedProtocolException */
    /* renamed from: Q1.a$a */
    /* loaded from: classes.dex */
    public static class a extends IllegalArgumentException {

        /* renamed from: b */
        public final String f6218b;

        public a(String str) {
            super("Unsupported protocol: " + str);
            this.f6218b = str == null ? "" : str;
        }
    }

    /**
     * Fills in {@code streamSettings} (security + transport + sockopt) and {@code mux}
     * on an outbound object.
     *
     * <p>jadx could not decompile this method (R8 duplicated the transport switch into
     * both branches and inverted the mux condition), so it is reconstructed from intent
     * against the Xray transport schema.
     */
    public static void a(JSONObject outbound, Profile profile, Prefs prefs) throws JSONException {
        SharedPreferences sp = prefs.f343a;
        JSONObject stream = new JSONObject();

        String network = (profile.network != null && !profile.network.isEmpty())
                ? profile.network : "tcp";
        stream.put("network", network);

        // ---- security -------------------------------------------------------
        String security = profile.security == null ? "" : profile.security;
        if ("tls".equals(security)) {
            stream.put("security", "tls");
            JSONObject tls = new JSONObject();
            String serverName;
            if (!profile.sni.isEmpty()) {
                serverName = profile.sni;
            } else if (!profile.host.isEmpty()) {
                serverName = profile.host;
            } else {
                serverName = profile.address;
            }
            tls.put("serverName", serverName);
            tls.put("allowInsecure", profile.allowInsecure);
            if (profile.fingerprint != null && !profile.fingerprint.isEmpty()) {
                tls.put("fingerprint", profile.fingerprint);
            }
            if (profile.alpn != null && !profile.alpn.isEmpty()) {
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
            stream.put("tlsSettings", tls);
        } else if ("reality".equals(security)) {
            stream.put("security", "reality");
            JSONObject reality = new JSONObject();
            reality.put("serverName", !profile.sni.isEmpty() ? profile.sni : profile.address);
            reality.put("publicKey", profile.publicKey);
            if (profile.shortId != null && !profile.shortId.isEmpty()) {
                reality.put("shortId", profile.shortId);
            }
            reality.put("fingerprint",
                    (profile.fingerprint != null && !profile.fingerprint.isEmpty())
                            ? profile.fingerprint : "chrome");
            if (profile.spiderX != null && !profile.spiderX.isEmpty()) {
                reality.put("spiderX", profile.spiderX);
            }
            reality.put("show", false);
            stream.put("realitySettings", reality);
        } else {
            stream.put("security", "none");
        }

        // ---- transport ------------------------------------------------------
        if ("httpupgrade".equals(network)) {
            JSONObject hu = new JSONObject();
            hu.put("path", (profile.path == null || profile.path.isEmpty()) ? "/" : profile.path);
            if (profile.host != null && !profile.host.isEmpty()) {
                hu.put("host", profile.host);
            }
            stream.put("httpupgradeSettings", hu);
        } else if ("h2".equals(network) || "http".equals(network)) {
            JSONObject h2 = new JSONObject();
            h2.put("path", (profile.path == null || profile.path.isEmpty()) ? "/" : profile.path);
            JSONArray hosts = new JSONArray();
            if (profile.host != null && !profile.host.isEmpty()) {
                for (String part : profile.host.split(",")) {
                    if (!part.trim().isEmpty()) {
                        hosts.put(part.trim());
                    }
                }
            }
            if (hosts.length() > 0) {
                h2.put("host", hosts);
            }
            stream.put("httpSettings", h2);
            stream.put("network", "h2");
        } else if ("ws".equals(network)) {
            JSONObject ws = new JSONObject();
            ws.put("path", (profile.path == null || profile.path.isEmpty()) ? "/" : profile.path);
            if (profile.host != null && !profile.host.isEmpty()) {
                JSONObject headers = new JSONObject();
                headers.put("Host", profile.host);
                ws.put("headers", headers);
                ws.put("host", profile.host);
            }
            stream.put("wsSettings", ws);
        } else if ("kcp".equals(network)) {
            JSONObject kcp = new JSONObject();
            kcp.put("mtu", 1350);
            kcp.put("tti", 50);
            kcp.put("uplinkCapacity", 12);
            kcp.put("downlinkCapacity", 100);
            kcp.put("congestion", false);
            kcp.put("readBufferSize", 2);
            kcp.put("writeBufferSize", 2);
            JSONObject header = new JSONObject();
            header.put("type", (profile.headerType != null && !profile.headerType.isEmpty())
                    ? profile.headerType : "none");
            kcp.put("header", header);
            if (profile.seed != null && !profile.seed.isEmpty()) {
                kcp.put("seed", profile.seed);
            }
            stream.put("kcpSettings", kcp);
        } else if ("grpc".equals(network)) {
            JSONObject grpc = new JSONObject();
            String serviceName = profile.serviceName == null ? "" : profile.serviceName;
            if (serviceName.isEmpty() && profile.path != null) {
                serviceName = profile.path.replaceFirst("^/", "");
            }
            grpc.put("serviceName", serviceName);
            grpc.put("multiMode", "multi".equals(profile.mode));
            if (profile.host != null && !profile.host.isEmpty()) {
                grpc.put("authority", profile.host);
            }
            stream.put("grpcSettings", grpc);
        } else if ("quic".equals(network)) {
            JSONObject quic = new JSONObject();
            quic.put("security", (profile.quicSecurity == null || profile.quicSecurity.isEmpty())
                    ? "none" : profile.quicSecurity);
            quic.put("key", profile.quicKey == null ? "" : profile.quicKey);
            JSONObject header = new JSONObject();
            header.put("type", (profile.headerType != null && !profile.headerType.isEmpty())
                    ? profile.headerType : "none");
            quic.put("header", header);
            stream.put("quicSettings", quic);
        } else if ("xhttp".equals(network) || "splithttp".equals(network)) {
            JSONObject xhttp = new JSONObject();
            xhttp.put("path", (profile.path == null || profile.path.isEmpty()) ? "/" : profile.path);
            if (profile.host != null && !profile.host.isEmpty()) {
                xhttp.put("host", profile.host);
            }
            if (profile.mode != null && !profile.mode.isEmpty()) {
                xhttp.put("mode", profile.mode);
            }
            stream.put("xhttpSettings", xhttp);
            stream.put("network", "xhttp");
        } else {
            JSONObject tcp = new JSONObject();
            JSONObject header = new JSONObject();
            if ("http".equals(profile.headerType)) {
                header.put("type", "http");
                JSONObject request = new JSONObject();
                request.put("version", "1.1");
                request.put("method", "GET");
                JSONArray paths = new JSONArray();
                paths.put((profile.path == null || profile.path.isEmpty()) ? "/" : profile.path);
                request.put("path", paths);
                JSONObject headers = new JSONObject();
                JSONArray hostList = new JSONArray();
                if (profile.host != null && !profile.host.isEmpty()) {
                    for (String part : profile.host.split(",")) {
                        hostList.put(part.trim().isEmpty() ? profile.address : part.trim());
                    }
                }
                headers.put("Host", hostList);
                headers.put("User-Agent", new JSONArray().put("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));
                headers.put("Accept-Encoding", new JSONArray().put("gzip, deflate"));
                headers.put("Connection", new JSONArray().put("keep-alive"));
                headers.put("Pragma", "no-cache");
                request.put("headers", headers);
                header.put("request", request);
            } else {
                header.put("type", "none");
            }
            tcp.put("header", header);
            stream.put("tcpSettings", tcp);
        }

        // ---- sockopt --------------------------------------------------------
        JSONObject sockopt = new JSONObject();
        sockopt.put("tcpKeepAliveIdle", 100);
        sockopt.put("mark", 255);
        sockopt.put("domainStrategy", "UseIP");
        stream.put("sockopt", sockopt);

        if (sp.getBoolean("fragment_enabled", false)) {
            JSONObject existing = stream.optJSONObject("sockopt");
            if (existing == null) {
                existing = new JSONObject();
            }
            existing.put("dialerProxy", "fragment");
            stream.put("sockopt", existing);
        }
        outbound.put("streamSettings", stream);

        // ---- mux ------------------------------------------------------------
        // Mux is incompatible with XTLS Vision, so it is force-disabled there.
        JSONObject mux = new JSONObject();
        boolean muxEnabled = sp.getBoolean("mux_enabled", false)
                && !"xtls-rprx-vision".equals(profile.flow);
        mux.put("enabled", muxEnabled);
        mux.put("concurrency", muxEnabled ? sp.getInt("mux_concurrency", 8) : -1);
        outbound.put("mux", mux);
    }

    /**
     * Builds the full Xray JSON config.
     *
     * <p>Also reconstructed from intent: R8 had duplicated the routing/DNS tail into an
     * unreachable second branch and jadx emitted both copies with the loops emptied.
     *
     * @param profile       the active server
     * @param prefs         user settings
     * @param chain         optional second hop (proxy chain); ignored if it is the same profile
     * @param emitInbounds  emit the socks/http (and optionally tun) inbounds
     * @param emitTun       emit the tun inbound (only meaningful with emitInbounds)
     */
    public static String b(Profile profile, Prefs prefs, Profile chain,
                           boolean emitInbounds, boolean emitTun) throws JSONException {
        SharedPreferences sp = prefs.f343a;
        JSONObject root = new JSONObject();

        root.put("stats", new JSONObject());

        JSONObject log = new JSONObject();
        log.put("loglevel", sp.getString("log_level", "warning"));
        root.put("log", log);

        // ---- policy ----------------------------------------------------------
        JSONObject level8 = new JSONObject();
        level8.put("handshake", 4);
        level8.put("connIdle", 300);
        level8.put("uplinkOnly", 1);
        level8.put("downlinkOnly", 1);
        level8.put("statsUserUplink", true);
        level8.put("statsUserDownlink", true);
        JSONObject levels = new JSONObject();
        levels.put("8", level8);
        JSONObject system = new JSONObject();
        system.put("statsOutboundUplink", true);
        system.put("statsOutboundDownlink", true);
        JSONObject policy = new JSONObject();
        policy.put("levels", levels);
        policy.put("system", system);
        root.put("policy", policy);

        // ---- inbounds --------------------------------------------------------
        JSONObject sniffing = new JSONObject();
        sniffing.put("enabled", true);
        sniffing.put("destOverride", new JSONArray().put("http").put("tls").put("quic"));
        sniffing.put("routeOnly", false);

        if (emitInbounds) {
            JSONArray inbounds = new JSONArray();

            JSONObject socksSettings = new JSONObject();
            socksSettings.put("auth", "noauth");
            socksSettings.put("udp", true);
            socksSettings.put("userLevel", 8);
            JSONObject socks = new JSONObject();
            socks.put("tag", "socks");
            socks.put("port", SOCKS_PORT);
            socks.put("listen", "127.0.0.1");
            socks.put("protocol", "socks");
            socks.put("settings", socksSettings);
            socks.put("sniffing", sniffing);
            inbounds.put(socks);

            JSONObject httpSettings = new JSONObject();
            httpSettings.put("userLevel", 8);
            JSONObject http = new JSONObject();
            http.put("tag", "http");
            http.put("port", HTTP_PORT);
            http.put("listen", "127.0.0.1");
            http.put("protocol", "http");
            http.put("settings", httpSettings);
            inbounds.put(http);

            if (emitTun) {
                JSONObject tunSettings = new JSONObject();
                tunSettings.put("name", "xray-tun");
                tunSettings.put("mtu", sp.getInt("vpn_mtu", 1500));
                tunSettings.put("userLevel", 8);
                JSONObject tun = new JSONObject();
                tun.put("tag", "tun");
                tun.put("protocol", "tun");
                tun.put("settings", tunSettings);
                tun.put("sniffing", sniffing);
                inbounds.put(tun);
            }
            root.put("inbounds", inbounds);
        }

        // ---- outbounds -------------------------------------------------------
        boolean chained = chain != null && !chain.id.equals(profile.id);

        JSONArray outbounds = new JSONArray();
        JSONObject proxy = c(profile, prefs);
        if (chained) {
            JSONObject stream = proxy.optJSONObject("streamSettings");
            if (stream == null) {
                stream = new JSONObject();
            }
            JSONObject sockopt = stream.optJSONObject("sockopt");
            if (sockopt == null) {
                sockopt = new JSONObject();
            }
            sockopt.put("dialerProxy", "chain");
            stream.put("sockopt", sockopt);
            proxy.put("streamSettings", stream);
        }
        outbounds.put(proxy);

        if (chained) {
            JSONObject chainOut = c(chain, prefs);
            chainOut.put("tag", "chain");
            outbounds.put(chainOut);
        }

        JSONObject directSockopt = new JSONObject();
        directSockopt.put("domainStrategy", "UseIP");
        JSONObject directStream = new JSONObject();
        directStream.put("sockopt", directSockopt);
        JSONObject direct = new JSONObject();
        direct.put("tag", "direct");
        direct.put("protocol", "freedom");
        direct.put("settings", new JSONObject().put("domainStrategy", "UseIP"));
        direct.put("streamSettings", directStream);
        outbounds.put(direct);

        JSONObject blockResponse = new JSONObject();
        blockResponse.put("type", "http");
        JSONObject block = new JSONObject();
        block.put("tag", "block");
        block.put("protocol", "blackhole");
        block.put("settings", new JSONObject().put("response", blockResponse));
        outbounds.put(block);

        if (sp.getBoolean("fragment_enabled", false)) {
            JSONObject fragment = new JSONObject();
            fragment.put("packets", sp.getString("fragment_packets", "tlshello"));
            fragment.put("length", sp.getString("fragment_length", "100-200"));
            fragment.put("interval", sp.getString("fragment_interval", "10-20"));
            JSONObject fragSockopt = new JSONObject();
            fragSockopt.put("tcpKeepAliveIdle", 100);
            fragSockopt.put("mark", 255);
            fragSockopt.put("tcpNoDelay", true);
            JSONObject fragOut = new JSONObject();
            fragOut.put("tag", "fragment");
            fragOut.put("protocol", "freedom");
            fragOut.put("settings", new JSONObject()
                    .put("domainStrategy", "UseIP")
                    .put("fragment", fragment));
            fragOut.put("streamSettings", new JSONObject().put("sockopt", fragSockopt));
            outbounds.put(fragOut);
        }
        root.put("outbounds", outbounds);

        // ---- routing ---------------------------------------------------------
        String mode = sp.getString("routing_mode", "iran_direct");

        JSONObject routing = new JSONObject();
        routing.put("domainStrategy", sp.getString("domain_strategy", "IPIfNonMatch"));
        JSONArray rules = new JSONArray();

        JSONObject privateIp = new JSONObject();
        privateIp.put("type", "field");
        privateIp.put("outboundTag", "direct");
        privateIp.put("ip", new JSONArray().put("geoip:private"));
        rules.put(privateIp);

        JSONObject privateDomain = new JSONObject();
        privateDomain.put("type", "field");
        privateDomain.put("outboundTag", "direct");
        privateDomain.put("domain", new JSONArray().put("geosite:private"));
        rules.put(privateDomain);

        // Ads are blocked in every mode except "global" (send-everything-through-proxy).
        if (!"global".equals(mode)) {
            JSONObject ads = new JSONObject();
            ads.put("type", "field");
            ads.put("outboundTag", "block");
            ads.put("domain", new JSONArray()
                    .put("geosite:category-ads-all")
                    .put("geosite:category-ads-ir"));
            rules.put(ads);
        }

        // Iranian destinations bypass the tunnel entirely.
        if ("iran_direct".equals(mode)) {
            JSONObject irDomain = new JSONObject();
            irDomain.put("type", "field");
            irDomain.put("outboundTag", "direct");
            irDomain.put("domain", new JSONArray().put("geosite:category-ir").put("domain:ir"));
            rules.put(irDomain);

            JSONObject irIp = new JSONObject();
            irIp.put("type", "field");
            irIp.put("outboundTag", "direct");
            irIp.put("ip", new JSONArray().put("geoip:ir"));
            rules.put(irIp);
        }

        // ---- user-defined custom rules ----------------------------------------
        try {
            JSONArray custom = new JSONArray(sp.getString("custom_rules", "[]"));
            for (int i = 0; i < custom.length(); i++) {
                JSONObject rule = custom.optJSONObject(i);
                if (rule == null) {
                    continue;
                }
                try {
                    String kind = rule.optString("kind", "domain");
                    String value = rule.optString("value", "").trim();
                    String outbound = rule.optString("outbound", "proxy");
                    if (value.isEmpty()
                            || !("proxy".equals(outbound) || "direct".equals(outbound)
                                 || "block".equals(outbound))) {
                        continue;
                    }
                    JSONArray values = new JSONArray();
                    for (String part : value.split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            values.put(trimmed);
                        }
                    }
                    if (values.length() == 0) {
                        continue;
                    }
                    JSONObject out = new JSONObject();
                    out.put("type", "field");
                    out.put("outboundTag", outbound);
                    if ("ip".equals(kind)) {
                        out.put("ip", values);
                    } else if ("port".equals(kind)) {
                        out.put("port", value.replace(" ", ""));
                    } else if ("app".equals(kind)) {
                        out.put("protocol", values);
                    } else {
                        out.put("domain", values);
                    }
                    rules.put(out);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        // Everything left over goes through the proxy.
        JSONObject tail = new JSONObject();
        tail.put("type", "field");
        tail.put("outboundTag", "proxy");
        tail.put("network", "tcp,udp");
        rules.put(tail);

        routing.put("rules", rules);
        root.put("routing", routing);

        // ---- dns ---------------------------------------------------------------
        JSONObject dns = new JSONObject();
        JSONArray servers = new JSONArray();
        for (String server : sp.getString("remote_dns", "1.1.1.1,8.8.8.8").split(",")) {
            String trimmed = server.trim();
            if (!trimmed.isEmpty()) {
                servers.put(trimmed);
            }
        }
        if (servers.length() == 0) {
            servers.put("1.1.1.1");
        }
        if ("iran_direct".equals(mode)) {
            // Resolve Iranian names with a direct resolver so they keep resolving to
            // their real in-country addresses instead of leaking through the tunnel.
            JSONObject directDns = new JSONObject();
            directDns.put("address", sp.getString("direct_dns", "8.8.8.8"));
            directDns.put("port", 53);
            directDns.put("domains", new JSONArray().put("geosite:category-ir").put("domain:ir"));
            directDns.put("skipFallback", true);
            servers.put(directDns);
        }
        dns.put("servers", servers);
        dns.put("queryStrategy", "UseIPv4");
        dns.put("disableCache", false);
        dns.put("tag", "dns_inbound");
        root.put("dns", dns);

        return root.toString(2);
    }

    public static JSONObject c(Profile profile, Prefs prefs) throws JSONException {
        String str;
        String str2;
        String str3;
        String str4;
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("tag", "proxy");
        JSONObject jSONObject2 = new JSONObject();
        String str5 = profile.protocol;
        if (str5 == null) {
            str5 = "";
        }
        if ("custom".equals(str5)) {
            JSONObject jSONObject3 = new JSONObject(profile.rawJson);
            JSONArray optJSONArray = jSONObject3.optJSONArray("outbounds");
            if (optJSONArray != null) {
                int i = 0;
                while (true) {
                    if (i >= optJSONArray.length()) {
                        jSONObject3 = null;
                        break;
                    }
                    JSONObject optJSONObject = optJSONArray.optJSONObject(i);
                    if (optJSONObject != null) {
                        String optString = optJSONObject.optString("protocol", "");
                        String optString2 = optJSONObject.optString("tag", "");
                        if (!"freedom".equals(optString) && !"blackhole".equals(optString) && !"direct".equals(optString2) && !"block".equals(optString2)) {
                            jSONObject3 = optJSONObject;
                            break;
                        }
                    }
                    i++;
                }
                if (jSONObject3 == null && optJSONArray.length() > 0) {
                    jSONObject3 = optJSONArray.optJSONObject(0);
                }
            }
            if (jSONObject3 != null) {
                jSONObject3.put("tag", "proxy");
                return jSONObject3;
            }
            throw new IllegalArgumentException("no outbound in JSON config");
        }
        if ("shadowsocks".equals(str5)) {
            jSONObject.put("protocol", "shadowsocks");
            JSONObject jSONObject4 = new JSONObject();
            jSONObject4.put("address", profile.address);
            jSONObject4.put("port", profile.port);
            jSONObject4.put("method", profile.encryption);
            jSONObject4.put("password", profile.uuid);
            jSONObject4.put("level", 8);
            jSONObject2.put("servers", new JSONArray().put(jSONObject4));
            jSONObject.put("settings", jSONObject2);
            a(jSONObject, profile, prefs);
            return jSONObject;
        }
        if ("wireguard".equals(str5)) {
            jSONObject.put("protocol", "wireguard");
            jSONObject2.put("secretKey", profile.uuid);
            int i2 = profile.wgMtu;
            if (i2 <= 0) {
                i2 = 1420;
            }
            jSONObject2.put("mtu", i2);
            JSONArray jSONArray = new JSONArray();
            String str6 = profile.localAddress;
            if (str6 != null && !str6.isEmpty()) {
                str4 = profile.localAddress;
            } else {
                str4 = "172.16.0.2/32";
            }
            for (String str7 : str4.split(",")) {
                String trim = str7.trim();
                if (!trim.isEmpty()) {
                    jSONArray.put(trim);
                }
            }
            jSONObject2.put("address", jSONArray);
            String str8 = profile.reserved;
            if (str8 != null && !str8.isEmpty()) {
                JSONArray jSONArray2 = new JSONArray();
                for (String str9 : profile.reserved.split(",")) {
                    try {
                        jSONArray2.put(Integer.parseInt(str9.trim()));
                    } catch (NumberFormatException unused) {
                    }
                }
                if (jSONArray2.length() == 3) {
                    jSONObject2.put("reserved", jSONArray2);
                }
            }
            JSONObject jSONObject5 = new JSONObject();
            jSONObject5.put("publicKey", profile.publicKey);
            jSONObject5.put("endpoint", profile.address + ":" + profile.port);
            jSONObject5.put("keepAlive", 25);
            jSONObject5.put("allowedIPs", new JSONArray().put("0.0.0.0/0").put("::/0"));
            String str10 = profile.presharedKey;
            if (str10 != null && !str10.isEmpty()) {
                jSONObject5.put("preSharedKey", profile.presharedKey);
            }
            jSONObject2.put("peers", new JSONArray().put(jSONObject5));
            jSONObject.put("settings", jSONObject2);
            return jSONObject;
        }
        if ("trojan".equals(str5)) {
            jSONObject.put("protocol", "trojan");
            JSONObject jSONObject6 = new JSONObject();
            jSONObject6.put("address", profile.address);
            jSONObject6.put("port", profile.port);
            jSONObject6.put("password", profile.uuid);
            jSONObject6.put("level", 8);
            String str11 = profile.flow;
            if (str11 != null && !str11.isEmpty()) {
                jSONObject6.put("flow", profile.flow);
            }
            jSONObject2.put("servers", new JSONArray().put(jSONObject6));
            jSONObject.put("settings", jSONObject2);
            a(jSONObject, profile, prefs);
            return jSONObject;
        }
        if ("vless".equals(str5)) {
            jSONObject.put("protocol", "vless");
            JSONObject jSONObject7 = new JSONObject();
            jSONObject7.put("id", profile.uuid);
            String str12 = profile.encryption;
            if (str12 != null && !str12.isEmpty()) {
                str3 = profile.encryption;
            } else {
                str3 = "none";
            }
            jSONObject7.put("encryption", str3);
            jSONObject7.put("level", 8);
            String str13 = profile.flow;
            if (str13 != null && !str13.isEmpty()) {
                jSONObject7.put("flow", profile.flow);
            }
            JSONObject jSONObject8 = new JSONObject();
            jSONObject8.put("address", profile.address);
            jSONObject8.put("port", profile.port);
            jSONObject8.put("users", new JSONArray().put(jSONObject7));
            jSONObject2.put("vnext", new JSONArray().put(jSONObject8));
            jSONObject.put("settings", jSONObject2);
            a(jSONObject, profile, prefs);
            return jSONObject;
        }
        if ("vmess".equals(str5)) {
            jSONObject.put("protocol", "vmess");
            JSONObject jSONObject9 = new JSONObject();
            jSONObject9.put("id", profile.uuid);
            jSONObject9.put("alterId", profile.alterId);
            String str14 = profile.encryption;
            if (str14 != null && !str14.isEmpty()) {
                str2 = profile.encryption;
            } else {
                str2 = "auto";
            }
            jSONObject9.put("security", str2);
            jSONObject9.put("level", 8);
            JSONObject jSONObject10 = new JSONObject();
            jSONObject10.put("address", profile.address);
            jSONObject10.put("port", profile.port);
            jSONObject10.put("users", new JSONArray().put(jSONObject9));
            jSONObject2.put("vnext", new JSONArray().put(jSONObject10));
            jSONObject.put("settings", jSONObject2);
            a(jSONObject, profile, prefs);
            return jSONObject;
        }
        if (!"socks".equals(str5) && !"socks5".equals(str5)) {
            if (!"hysteria2".equals(str5) && !"hy2".equals(str5) && !"tuic".equals(str5)) {
                throw new a(profile.protocol);
            }
            throw new a(str5);
        }
        jSONObject.put("protocol", "socks");
        JSONObject jSONObject11 = new JSONObject();
        jSONObject11.put("address", profile.address);
        jSONObject11.put("port", profile.port);
        jSONObject11.put("level", 8);
        String str15 = profile.uuid;
        if (str15 != null && !str15.isEmpty()) {
            JSONObject jSONObject12 = new JSONObject();
            jSONObject12.put("user", profile.uuid);
            String str16 = profile.quicKey;
            if (str16 == null) {
                str = "";
            } else {
                str = str16;
            }
            jSONObject12.put("pass", str);
            jSONObject12.put("level", 8);
            jSONObject11.put("users", new JSONArray().put(jSONObject12));
        }
        jSONObject2.put("servers", new JSONArray().put(jSONObject11));
        jSONObject.put("settings", jSONObject2);
        a(jSONObject, profile, prefs);
        return jSONObject;
    }
}
