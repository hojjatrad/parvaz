package com.parvaz.tunnel.config;

import java.util.Locale;

/** Canonical identifiers shared by storage, import, capability checks and builder. */
public final class ProtocolNames {
    private ProtocolNames() {}

    public static String canonical(String name) {
        String value = name == null ? "" : name.trim().toLowerCase(Locale.US);
        switch (value) {
            case "ss": return "shadowsocks";
            case "socks5": return "socks";
            case "wg": return "wireguard";
            case "hy2": return "hysteria2";
            default: return value;
        }
    }

    public static boolean hasEngine(String name){String p=canonical(name);return hasBuilder(p)||p.equals("hysteria2")||p.equals("tuic")||p.equals("full-xray")||p.equals("full-singbox")||p.equals("full-clash");}

    /** Protocol-level support only. Transport/credentials still need validation by Xray. */
    public static boolean hasBuilder(String name) {
        switch (canonical(name)) {
            case "vmess": case "vless": case "trojan": case "shadowsocks":
            case "socks": case "http": case "wireguard": return true;
            default: return false;
        }
    }
}
