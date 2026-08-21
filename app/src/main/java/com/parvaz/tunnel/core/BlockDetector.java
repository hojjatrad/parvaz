package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.parvaz.tunnel.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Works out <em>how</em> the connection is being interfered with (idea 1.3), so the app
 * can react instead of just saying "not connected".
 *
 * <p>Filtering in Iran is not one mechanism but several, each with a different remedy:
 * DNS poisoning wants encrypted DNS, SNI inspection wants TLS fragmenting, throttling
 * wants a different protocol, and a total blackout just wants patience.
 *
 * <p>All probes run outside the tunnel against public endpoints and take a few seconds
 * in total. Never call this on the main thread.
 */
public final class BlockDetector {

    public static final int UNKNOWN = 0;
    public static final int NO_INTERFERENCE = 1;
    public static final int DNS_POISONING = 2;
    public static final int SNI_INSPECTION = 3;
    public static final int THROTTLING = 4;
    public static final int BLACKOUT = 5;

    private static final String PREFS = "parvaz_diag";
    private static final String KEY_LAST_KIND = "last_kind";
    private static final String KEY_LAST_AT = "last_at";

    /**
     * Addresses Iranian resolvers hand back for blocked names. Seeing one of these is
     * the signature of DNS-level poisoning rather than a genuine NXDOMAIN.
     */
    private static final HashSet<String> BLACKHOLE_IPS = new HashSet<String>(Arrays.asList(
            "10.10.34.34", "10.10.34.35", "10.10.34.36", "127.0.0.1", "0.0.0.0"));

    /** Hosts that are definitely blocked in Iran, used to detect poisoned answers. */
    private static final String[] BLOCKED_PROBES = {
            "www.youtube.com", "twitter.com", "www.facebook.com"
    };

    /** A host that is not blocked, to prove basic connectivity works at all. */
    private static final String CONTROL_HOST = "www.gstatic.com";

    /** Above this, a working connection counts as throttled. */
    private static final long SLOW_MS = 6000;

    private BlockDetector() {
    }

    /** Result of one diagnosis. */
    public static final class Result {
        public int kind = UNKNOWN;

        /** Round-trip of the control request in ms, or -1. */
        public long controlMs = -1;

        /** Plain-language explanation, suitable for the log or a dialog. */
        public int messageRes = R.string.diag_unknown;

        public boolean isInterference() {
            return kind != NO_INTERFERENCE && kind != UNKNOWN;
        }
    }

    /** Runs the full probe sequence. Blocking. */
    public static Result diagnose(Context context) {
        Result result = new Result();

        // 1. Is anything reachable at all? Plain TCP to a public resolver failing means
        //    a blackout rather than selective filtering.
        if (!tcpReachable("1.1.1.1", 53, 4000) && !tcpReachable("8.8.8.8", 53, 4000)) {
            result.kind = BLACKOUT;
            result.messageRes = R.string.diag_blackout;
            remember(context, result.kind);
            return result;
        }

        // 2. Is DNS honest? A blocked host resolving to a sinkhole address is the
        //    classic poisoning signature, and the one case DoH actually fixes.
        if (dnsPoisoned()) {
            result.kind = DNS_POISONING;
            result.messageRes = R.string.diag_dns;
            remember(context, result.kind);
            return result;
        }

        // 3. TCP connecting but TLS dying means something reads the SNI and kills it.
        boolean tcpOk = tcpReachable("www.youtube.com", 443, 5000);
        boolean tlsOk = tlsHandshakeOk("www.youtube.com", 6000);
        if (tcpOk && !tlsOk) {
            result.kind = SNI_INSPECTION;
            result.messageRes = R.string.diag_sni;
            remember(context, result.kind);
            return result;
        }

        // 4. Everything connects — is it merely crawling?
        long elapsed = timedFetch(CONTROL_HOST);
        result.controlMs = elapsed;
        if (elapsed > 0 && elapsed > SLOW_MS) {
            result.kind = THROTTLING;
            result.messageRes = R.string.diag_throttle;
            remember(context, result.kind);
            return result;
        }

        result.kind = NO_INTERFERENCE;
        result.messageRes = R.string.diag_clear;
        remember(context, result.kind);
        return result;
    }

    /**
     * Applies the obvious remedy where one exists. Returns true when a setting changed
     * and the tunnel should be restarted to pick it up.
     */
    public static boolean applyRemedy(Context context, Result result) {
        if (result == null || !result.isInterference()) {
            return false;
        }
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences("parvaz_prefs", Context.MODE_PRIVATE);

        if (result.kind == SNI_INSPECTION) {
            if (!sp.getBoolean("fragment_enabled", false)) {
                sp.edit().putBoolean("fragment_enabled", true).apply();
                LogBuffer.listener("SNI inspection detected, TLS fragmenting enabled");
                return true;
            }
            return false;
        }
        if (result.kind == DNS_POISONING) {
            String remote = sp.getString("remote_dns", "1.1.1.1,8.8.8.8");
            if (remote == null || !remote.contains("https://")) {
                sp.edit().putString("remote_dns",
                        "https://1.1.1.1/dns-query,https://dns.google/dns-query").apply();
                LogBuffer.listener("DNS poisoning detected, switched to DNS-over-HTTPS");
                return true;
            }
        }
        return false;
    }

    /** The most recent diagnosis kind, or {@link #UNKNOWN}. */
    public static int lastKind(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_LAST_KIND, UNKNOWN);
    }

    /** When the last diagnosis ran (epoch millis, 0 if never). */
    public static long lastAt(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_AT, 0L);
    }

    // -------------------------------------------------------------------- probes

    private static void remember(Context context, int kind) {
        try {
            context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_LAST_KIND, kind)
                    .putLong(KEY_LAST_AT, System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private static boolean tcpReachable(String host, int port, int timeoutMs) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return socket.isConnected();
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * True when a known-blocked host resolves to a sinkhole address. Resolution failing
     * outright is not poisoning — that is ordinary blocking, and DoH would not help.
     */
    private static boolean dnsPoisoned() {
        for (int i = 0; i < BLOCKED_PROBES.length; i++) {
            try {
                InetAddress[] addresses = InetAddress.getAllByName(BLOCKED_PROBES[i]);
                for (int j = 0; j < addresses.length; j++) {
                    String ip = addresses[j].getHostAddress();
                    if (ip != null && BLACKHOLE_IPS.contains(ip)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Attempts a real TLS handshake. HttpsURLConnection is used rather than a raw
     * socket so the platform sends a full ClientHello with SNI — exactly what a
     * middlebox inspects.
     */
    private static boolean tlsHandshakeOk(String host, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://" + host + "/").openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("HEAD");
            conn.setInstanceFollowRedirects(false);
            conn.getResponseCode();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Times a small fetch of a known-good host. Returns ms, or -1 on failure. */
    private static long timedFetch(String host) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            long start = System.currentTimeMillis();
            conn = (HttpURLConnection) new URL("https://" + host + "/generate_204")
                    .openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setUseCaches(false);
            conn.getResponseCode();
            in = conn.getInputStream();
            byte[] buf = new byte[1024];
            while (in.read(buf) > 0) {
                // drain
            }
            return System.currentTimeMillis() - start;
        } catch (Throwable ignored) {
            return -1;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
