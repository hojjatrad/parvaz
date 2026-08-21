package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * Decides what the tunnel should do on the current network.
 *
 * <p>Two knobs, set per transport in {@code AutoProfileActivity}: connect
 * automatically, stay disconnected, or do nothing. A trusted-SSID list overrides
 * the Wi-Fi choice, so a home network can be exempted without turning the whole
 * feature off.
 */
public final class AutoProfile {

    public static final int ACTION_NONE = 0;
    public static final int ACTION_CONNECT = 1;
    public static final int ACTION_DISCONNECT = 2;

    private AutoProfile() {
    }

    /** Resolves the action for whatever network the device is on right now. */
    public static int decide(Context context) {
        if (context == null) {
            return ACTION_NONE;
        }
        SharedPreferences sp =
                context.getApplicationContext().getSharedPreferences("parvaz_prefs", Context.MODE_PRIVATE);

        int transport = NetContext.transport(context);
        if (transport == NetContext.TRANSPORT_WIFI) {
            // A trusted network wins over the generic Wi-Fi setting.
            if (isTrustedWifi(context, sp)) {
                return ACTION_DISCONNECT;
            }
            return parse(sp.getString("auto_wifi", "none"));
        }
        if (transport == NetContext.TRANSPORT_MOBILE) {
            return parse(sp.getString("auto_cell", "none"));
        }
        return ACTION_NONE;
    }

    private static int parse(String action) {
        if ("connect".equals(action)) {
            return ACTION_CONNECT;
        }
        if ("disconnect".equals(action)) {
            return ACTION_DISCONNECT;
        }
        return ACTION_NONE;
    }

    /**
     * True when the current SSID appears in the user's trusted list.
     *
     * <p>Reading the SSID needs location permission on API 27+; when it is not
     * granted the platform hands back {@code <unknown ssid>} and we simply treat
     * the network as untrusted rather than guessing.
     */
    public static boolean isTrustedWifi(Context context, SharedPreferences sp) {
        String list = sp.getString("trusted_wifi", "");
        if (list.trim().isEmpty()) {
            return false;
        }
        String current = currentSsid(context);
        if (current.isEmpty()) {
            return false;
        }
        for (String line : list.split("\n")) {
            String ssid = line.trim();
            if (!ssid.isEmpty() && ssid.equalsIgnoreCase(current)) {
                return true;
            }
        }
        return false;
    }

    /** Current Wi-Fi SSID with the quotes the framework wraps it in stripped. */
    public static String currentSsid(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null) {
                return "";
            }
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) {
                return "";
            }
            String ssid = info.getSSID();
            if (ssid == null) {
                return "";
            }
            if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() >= 2) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            if ("<unknown ssid>".equalsIgnoreCase(ssid) || "0x".equals(ssid)) {
                return "";
            }
            return ssid;
        } catch (Throwable t) {
            android.util.Log.w("Parvaz/AutoProfile", "SSID lookup failed", t);
            return "";
        }
    }

    /** True when any per-network automation is configured at all. */
    public static boolean isConfigured(Context context) {
        SharedPreferences sp =
                context.getApplicationContext().getSharedPreferences("parvaz_prefs", Context.MODE_PRIVATE);
        return !"none".equals(sp.getString("auto_wifi", "none"))
                || !"none".equals(sp.getString("auto_cell", "none"))
                || !sp.getString("trusted_wifi", "").trim().isEmpty();
    }
}
