package com.parvaz.tunnel.core;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

import org.json.JSONObject;

/**
 * Resolves the public IP as seen from a given network path.
 *
 * <p>The previous implementation queried a single endpoint
 * ({@code speed.cloudflare.com/meta}). When that host was blocked or slow the
 * lookup reported "IP not detected" with no second chance, which is exactly
 * what users in Iran were hitting. This class walks a list of independent
 * providers and returns the first that answers, so one blocked domain no
 * longer breaks the feature.
 */
public final class IpLookup {

    /** Local SOCKS inbound published by the core; see XrayConfigBuilder.SOCKS_PORT. */
    private static final int SOCKS_PORT = 10808;

    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int READ_TIMEOUT_MS = 6000;

    /** Result of one lookup attempt. */
    public static final class Info {
        public final String ip;
        public final String country;
        public final String city;
        /** Non-null when every provider failed; carries the last error text. */
        public final String error;

        Info(String ip, String country, String city, String error) {
            this.ip = ip == null ? "" : ip;
            this.country = country == null ? "" : country;
            this.city = city == null ? "" : city;
            this.error = error;
        }

        public boolean ok() {
            return error == null && !ip.isEmpty();
        }
    }

    /** One IP-echo provider and the JSON keys it uses. */
    private static final class Provider {
        final String url;
        final String ipKey;
        final String countryKey;
        final String cityKey;
        /** true when the body is a bare IP string rather than JSON. */
        final boolean plainText;

        Provider(String url, String ipKey, String countryKey, String cityKey, boolean plainText) {
            this.url = url;
            this.ipKey = ipKey;
            this.countryKey = countryKey;
            this.cityKey = cityKey;
            this.plainText = plainText;
        }
    }

    /**
     * Ordered by how likely each is to be reachable from Iran. Cloudflare stays
     * first because it also reports the city, but it is no longer load-bearing.
     */
    private static final Provider[] PROVIDERS = new Provider[] {
        new Provider("https://speed.cloudflare.com/meta", "clientIp", "country", "city", false),
        new Provider("https://ipinfo.io/json", "ip", "country", "city", false),
        new Provider("https://api.ip.sb/geoip", "ip", "country_code", "city", false),
        new Provider("https://ipwho.is/", "ip", "country_code", "city", false),
        new Provider("https://api.ipify.org", null, null, null, true),
        new Provider("https://icanhazip.com", null, null, null, true),
    };

    private IpLookup() {
    }

    /**
     * Looks the address up through the tunnel's local SOCKS inbound.
     * Call only while the core is running.
     */
    public static Info throughTunnel() {
        return query(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", SOCKS_PORT)));
    }

    /**
     * Looks the address up over the device's ordinary connection, bypassing the
     * tunnel. Used to show the "before" value next to the tunnelled one.
     */
    public static Info direct() {
        return query(Proxy.NO_PROXY);
    }

    private static Info query(Proxy proxy) {
        String lastError = "";
        for (Provider provider : PROVIDERS) {
            try {
                Info info = fetch(provider, proxy);
                if (info != null && !info.ip.isEmpty()) {
                    return info;
                }
            } catch (Exception e) {
                String message = e.getMessage();
                lastError = (message == null || message.isEmpty())
                        ? e.getClass().getSimpleName()
                        : message;
            }
        }
        return new Info("", "", "", lastError.isEmpty() ? "unreachable" : lastError);
    }

    private static Info fetch(Provider provider, Proxy proxy) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(provider.url);
            conn = (HttpURLConnection) (proxy == Proxy.NO_PROXY
                    ? url.openConnection()
                    : url.openConnection(proxy));
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            // Some echo services return HTML to browser-like agents.
            conn.setRequestProperty("User-Agent", "curl/8.0");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }

            String body = readAll(conn);
            if (body.isEmpty()) {
                return null;
            }

            if (provider.plainText) {
                String ip = body.trim();
                return isPlausibleIp(ip) ? new Info(ip, "", "", null) : null;
            }

            JSONObject json = new JSONObject(body);
            String ip = json.optString(provider.ipKey, "");
            if (!isPlausibleIp(ip)) {
                return null;
            }
            return new Info(
                    ip,
                    provider.countryKey == null ? "" : json.optString(provider.countryKey, ""),
                    provider.cityKey == null ? "" : json.optString(provider.cityKey, ""),
                    null);
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                    // Disconnecting is best-effort; the socket closes with the process.
                }
            }
        }
    }

    private static String readAll(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        InputStream in = conn.getInputStream();
        try {
            byte[] buf = new byte[4096];
            while (true) {
                int read = in.read(buf);
                if (read <= 0) {
                    break;
                }
                sb.append(new String(buf, 0, read, "UTF-8"));
                // Guard against a provider streaming an unexpectedly large body.
                if (sb.length() > 65536) {
                    break;
                }
            }
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
                // Already draining; nothing useful to do here.
            }
        }
        return sb.toString();
    }

    /** Cheap sanity filter so an HTML error page never lands in the UI. */
    private static boolean isPlausibleIp(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        if (v.length() < 3 || v.length() > 45) {
            return false;
        }
        boolean hasDigit = false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if (c != '.' && c != ':'
                    && !(c >= 'a' && c <= 'f') && !(c >= 'A' && c <= 'F')) {
                return false;
            }
        }
        return hasDigit && (v.indexOf('.') >= 0 || v.indexOf(':') >= 0);
    }
}
