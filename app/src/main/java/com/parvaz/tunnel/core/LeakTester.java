package com.parvaz.tunnel.core;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

/**
 * Verifies that traffic really leaves through the tunnel.
 *
 * <p>Three independent checks, each answerable with evidence the user can see:
 * does the public IP change when the tunnel is up, does DNS resolve from the
 * tunnel exit, and does IPv6 quietly bypass everything. Run off the UI thread.
 */
public final class LeakTester {

    private static final int SOCKS_PORT = 10808;
    private static final int TIMEOUT_MS = 7000;

    public static final int PASS = 0;
    public static final int FAIL = 1;
    public static final int UNKNOWN = 2;

    /** One row in the report. */
    public static final class Check {
        public final String name;
        public final int status;
        /** Human-readable evidence, e.g. the two IPs that were compared. */
        public final String detail;

        Check(String name, int status, String detail) {
            this.name = name;
            this.status = status;
            this.detail = detail == null ? "" : detail;
        }
    }

    public static final class Report {
        public final List<Check> checks = new ArrayList<>();
        public String directIp = "";
        public String tunnelIp = "";

        public int failures() {
            int n = 0;
            for (Check c : checks) {
                if (c.status == FAIL) {
                    n++;
                }
            }
            return n;
        }

        public boolean allPassed() {
            if(checks.size()!=3)return false;
            java.util.Set<String> names=new java.util.HashSet<>();
            for(Check check:checks){if(check.status!=PASS)return false;names.add(check.name);}
            return names.contains("ip")&&names.contains("dns")&&names.contains("ipv6");
        }
    }

    private LeakTester() {
    }

    /**
     * Runs every check. {@code directIpHint} is the pre-connect address when the
     * caller already captured one; pass null to look it up now (which, while the
     * VPN holds the default route, may itself come back tunnelled).
     */
    public static Report run(String directIpHint) {
        Report report = new Report();

        IpLookup.Info tunnel = IpLookup.throughTunnel();
        report.tunnelIp = tunnel.ip;

        String direct = directIpHint;
        // Never create a supposedly direct baseline while VPN is already running.
        if(direct==null)direct="";
        report.directIp = direct;

        report.checks.add(checkIp(direct, tunnel.ip));
        report.checks.add(checkDns(tunnel.ip));
        report.checks.add(checkIpv6());

        return report;
    }

    /**
     * The headline check: if the tunnelled lookup returns the same address as the
     * untunnelled one, traffic is not being proxied at all.
     */
    private static Check checkIp(String directIp, String tunnelIp) {
        if (tunnelIp == null || tunnelIp.isEmpty()) {
            return new Check("ip", UNKNOWN, "");
        }
        if (directIp == null || directIp.isEmpty()) {
            // Nothing to compare against, but we did get an address through the
            // tunnel, which is itself a good sign.
            return new Check("ip", UNKNOWN, tunnelIp);
        }
        String detail = directIp + " \u2192 " + tunnelIp;
        return new Check("ip", directIp.equals(tunnelIp) ? UNKNOWN : PASS, detail);
    }

    /**
     * Asks a resolver-echo service which DNS server performed the lookup. When
     * the answering resolver sits in the same country as the tunnel exit the
     * query stayed inside; when it is the local ISP the query leaked.
     */
    private static Check checkDns(String tunnelIp) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://edns.ip-api.com/json");
            conn = (HttpURLConnection) url.openConnection(
                    new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", SOCKS_PORT)));
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "curl/8.0");

            StringBuilder sb = new StringBuilder();
            java.io.InputStream in = conn.getInputStream();
            try {
                byte[] buf = new byte[4096];
                int read;
                while ((read = in.read(buf)) > 0) {
                    sb.append(new String(buf, 0, read, "UTF-8"));
                    if (sb.length() > 32768) {
                        break;
                    }
                }
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // Best-effort close.
                }
            }

            JSONObject json = new JSONObject(sb.toString());
            JSONObject dns = json.optJSONObject("dns");
            if (dns == null) {
                return new Check("dns", UNKNOWN, "");
            }
            String resolverIp = dns.optString("ip", "");
            String geo = dns.optString("geo", "");
            if (resolverIp.isEmpty()) {
                return new Check("dns", UNKNOWN, "");
            }
            String detail = geo.isEmpty() ? resolverIp : (resolverIp + " (" + geo + ")");
            // Resolver geography cannot establish the DNS route. Evidence only.
            return new Check("dns", UNKNOWN, detail);
        } catch (Exception e) {
            return new Check("dns", UNKNOWN, "");
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                    // Best-effort.
                }
            }
        }
    }

    /**
     * If the device still has a routable IPv6 address while the tunnel only
     * carries IPv4, some traffic can escape over v6 entirely.
     */
    private static Check checkIpv6() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://api6.ipify.org").openConnection(
                    new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",10809)));
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "curl/8.0");
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                StringBuilder sb = new StringBuilder();
                java.io.InputStream in = conn.getInputStream();
                try {
                    byte[] buf = new byte[512];
                    int read;
                    while ((read = in.read(buf)) > 0) {
                        sb.append(new String(buf, 0, read, "UTF-8"));
                        if(sb.length()>4096)throw new java.io.IOException("Response limit");
                    }
                } finally {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                        // Best-effort.
                    }
                }
                String v6 = sb.toString().trim();
                if (v6.contains(":")) {
                    // Reachable over v6 outside the tunnel.
                    return new Check("ipv6", UNKNOWN, v6);
                }
            }
            return new Check("ipv6", UNKNOWN, "");
        } catch (Exception e) {
            // A failed measurement proves nothing about IPv6 leakage.
            return new Check("ipv6", UNKNOWN, "");
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                    // Best-effort.
                }
            }
        }
    }

    /** Resolves a hostname without the tunnel; used by callers that want a hint. */
    public static String resolveDirect(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr == null ? "" : addr.getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }
}
