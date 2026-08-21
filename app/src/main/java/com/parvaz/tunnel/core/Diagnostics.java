package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.store.ProfileStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Builds a self-contained problem report.
 *
 * <p>When a tunnel will not connect, the useful facts are scattered: Android version,
 * which core is loaded, what the active settings are, whether the device even has
 * internet, and the tail of the log. Asking a non-technical user to collect those is
 * hopeless, so this assembles them into one shareable text block.
 *
 * <p>Privacy is the hard constraint. A config contains credentials — UUIDs, passwords,
 * REALITY keys — and server addresses that identify the user's provider. None of that
 * is ever written here: servers appear only as a count and a redacted
 * protocol/network/security shape, and addresses are masked. The report is safe to post
 * in a support chat.
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    /** How many trailing log lines to attach. */
    private static final int LOG_TAIL_LINES = 120;

    /**
     * Produces the full report.
     *
     * @param context any context
     * @return a plain-text report; never null, never contains credentials
     */
    public static String build(Context context) {
        StringBuilder sb = new StringBuilder(4096);
        try {
            sb.append("=== Parvaz diagnostics ===\n");
            sb.append("generated: ")
              .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
              .append('\n');

            appendApp(context, sb);
            sb.append('\n');
            appendDevice(sb);
            sb.append('\n');
            appendNetwork(context, sb);
            sb.append('\n');
            appendSettings(context, sb);
            sb.append('\n');
            appendServers(context, sb);
            sb.append('\n');
            appendLog(sb);
        } catch (Throwable t) {
            sb.append("\n[report generation failed: ").append(t).append("]\n");
        }
        return sb.toString();
    }

    private static void appendApp(Context context, StringBuilder sb) {
        sb.append("--- app ---\n");
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            sb.append("version: ").append(pi.versionName).append(" (").append(pi.versionCode).append(")\n");
        } catch (Throwable t) {
            sb.append("version: unknown\n");
        }
        sb.append("core running: ").append(CoreManager.b().running).append('\n');
        sb.append("service running: ").append(com.parvaz.tunnel.core.TunnelVpnService.serviceRunning).append('\n');
    }

    private static void appendDevice(StringBuilder sb) {
        sb.append("--- device ---\n");
        sb.append("android: ").append(Build.VERSION.RELEASE)
          .append(" (api ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("model: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        String abis = "";
        try {
            String[] supported = Build.SUPPORTED_ABIS;
            if (supported != null) {
                StringBuilder a = new StringBuilder();
                for (int i = 0; i < supported.length; i++) {
                    if (i > 0) {
                        a.append(',');
                    }
                    a.append(supported[i]);
                }
                abis = a.toString();
            }
        } catch (Throwable ignored) {
            // Leave blank.
        }
        sb.append("abis: ").append(abis).append('\n');
        sb.append("locale: ").append(Locale.getDefault()).append('\n');
    }

    private static void appendNetwork(Context context, StringBuilder sb) {
        sb.append("--- network ---\n");
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                sb.append("connectivity: unavailable\n");
                return;
            }
            android.net.Network active = cm.getActiveNetwork();
            if (active == null) {
                sb.append("transport: none (offline?)\n");
                return;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null) {
                sb.append("transport: unknown\n");
                return;
            }
            String transport = "other";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                transport = "wifi";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                transport = "cellular";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                transport = "ethernet";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                transport = "vpn";
            }
            sb.append("transport: ").append(transport).append('\n');
            sb.append("validated: ")
              .append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).append('\n');
            sb.append("context key: ").append(NetContext.key(context)).append('\n');
        } catch (Throwable t) {
            sb.append("network probe failed: ").append(t).append('\n');
        }
    }

    /** Setting keys worth reporting. Credentials and URLs are deliberately absent. */
    private static final String[] REPORTED_BOOLEANS = {
            "auto_switch", "ipv6", "bypass_lan", "battery_saver", "block_ads", "mux_enabled"
    };

    private static final String[] REPORTED_INTS = {
            "health_interval", "health_strikes", "ping_threshold", "buffer_size_kb",
            "vpn_mtu", "battery_idle_multiplier", "sort_mode", "sub_auto_hours"
    };

    private static void appendSettings(Context context, StringBuilder sb) {
        sb.append("--- settings ---\n");
        try {
            Prefs prefs = new Prefs(context);
            for (String key : REPORTED_BOOLEANS) {
                if (prefs.f343a.contains(key)) {
                    sb.append(key).append(": ").append(prefs.f343a.getBoolean(key, false)).append('\n');
                }
            }
            for (String key : REPORTED_INTS) {
                if (prefs.f343a.contains(key)) {
                    sb.append(key).append(": ").append(prefs.f343a.getInt(key, 0)).append('\n');
                }
            }
            sb.append("per_app_mode: ")
              .append(prefs.f343a.getString("per_app_mode", "off")).append('\n');
            sb.append("per_app_count: ").append(prefs.c().size()).append('\n');
        } catch (Throwable t) {
            sb.append("settings read failed: ").append(t).append('\n');
        }
    }

    private static void appendServers(Context context, StringBuilder sb) {
        sb.append("--- servers (redacted) ---\n");
        try {
            ProfileStore store = ProfileStore.f(context);
            java.util.ArrayList all = store.e();
            sb.append("count: ").append(all.size()).append('\n');
            int shown = 0;
            for (Object o : all) {
                Profile p = (Profile) o;
                if (shown >= 15) {
                    sb.append("… and ").append(all.size() - shown).append(" more\n");
                    break;
                }
                sb.append("  [").append(shown).append("] ")
                  .append(safe(p.protocol)).append('/')
                  .append(safe(p.network)).append('/')
                  .append(p.security == null || p.security.isEmpty() ? "none" : p.security)
                  .append("  host=").append(mask(p.address))
                  .append("  port=").append(p.port)
                  .append("  ping=").append(p.ping)
                  .append('\n');
                shown++;
            }
        } catch (Throwable t) {
            sb.append("server read failed: ").append(t).append('\n');
        }
    }

    private static void appendLog(StringBuilder sb) {
        sb.append("--- log tail ---\n");
        try {
            String lines = LogBuffer.lines();
            if (lines == null || lines.length() == 0) {
                sb.append("(empty)\n");
                return;
            }
            String[] split = lines.split("\n");
            int from = Math.max(0, split.length - LOG_TAIL_LINES);
            for (int i = from; i < split.length; i++) {
                sb.append(split[i]).append('\n');
            }
        } catch (Throwable t) {
            sb.append("log read failed: ").append(t).append('\n');
        }
    }

    private static String safe(String s) {
        return (s == null || s.isEmpty()) ? "?" : s;
    }

    /**
     * Masks a server address so the report shows its shape without identifying the
     * provider: {@code de3.example.com} becomes {@code d…3.***.com}, an IP becomes
     * {@code 12.x.x.34}.
     */
    public static String mask(String address) {
        if (address == null || address.isEmpty()) {
            return "?";
        }
        String a = address.trim();
        // Looks like an IPv4 literal?
        if (a.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            String[] parts = a.split("\\.");
            return parts[0] + ".x.x." + parts[3];
        }
        int lastDot = a.lastIndexOf('.');
        String tld = lastDot >= 0 ? a.substring(lastDot) : "";
        String head = lastDot >= 0 ? a.substring(0, lastDot) : a;
        int firstDot = head.indexOf('.');
        String label = firstDot >= 0 ? head.substring(0, firstDot) : head;
        if (label.length() <= 2) {
            return label + ".***" + tld;
        }
        return label.charAt(0) + "…" + label.charAt(label.length() - 1) + ".***" + tld;
    }
}
