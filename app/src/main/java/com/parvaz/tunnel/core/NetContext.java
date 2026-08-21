package com.parvaz.tunnel.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;

import java.util.Calendar;
import java.util.Locale;

/**
 * Describes "the network situation right now" compactly enough to use as a lookup key:
 * transport type, mobile carrier, and a coarse time-of-day bucket.
 *
 * <p>Everything here is derived locally and never leaves the device. We deliberately do
 * not read the Wi-Fi SSID: that needs location permission on modern Android, and
 * transport + carrier already separates the cases that matter (home Wi-Fi vs. mobile
 * data on a particular operator).
 */
public final class NetContext {

    public static final int TRANSPORT_UNKNOWN = -1;
    public static final int TRANSPORT_MOBILE = 0;
    public static final int TRANSPORT_WIFI = 1;
    public static final int TRANSPORT_ETHERNET = 3;
    public static final int TRANSPORT_VPN = 4;

    private NetContext() {
    }

    /** Current transport, as one of the TRANSPORT_* constants. */
    public static int transport(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return TRANSPORT_UNKNOWN;
            }
            Network active = cm.getActiveNetwork();
            if (active == null) {
                return TRANSPORT_UNKNOWN;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null) {
                return TRANSPORT_UNKNOWN;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return TRANSPORT_WIFI;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return TRANSPORT_MOBILE;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return TRANSPORT_ETHERNET;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return TRANSPORT_VPN;
            }
        } catch (Throwable ignored) {
        }
        return TRANSPORT_UNKNOWN;
    }

    /**
     * Mobile network operator, normalized. Returns "" on Wi-Fi or when unavailable.
     * Used both in the memory key and to scope auto-tuned fragment settings, since
     * Iranian carriers deploy noticeably different filtering.
     */
    public static String carrier(Context context) {
        try {
            if (transport(context) != TRANSPORT_MOBILE) {
                return "";
            }
            TelephonyManager tm = (TelephonyManager) context.getApplicationContext()
                    .getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                return "";
            }
            String operator = tm.getNetworkOperator();
            if (operator != null && !operator.isEmpty()) {
                return operator;
            }
            String name = tm.getNetworkOperatorName();
            if (name != null) {
                return name.trim().toLowerCase(Locale.US).replace(' ', '_');
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * Coarse time-of-day bucket: 0 = night (00-06), 1 = morning (06-12),
     * 2 = afternoon (12-18), 3 = evening (18-24).
     *
     * <p>Filtering behaviour varies between the working day and late night, but
     * hour-level granularity would spread the samples too thin to ever be useful.
     */
    public static int hourBucket() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) / 6;
    }

    /** Stable key combining transport, carrier and hour bucket. */
    public static String key(Context context) {
        int transport = transport(context);
        String carrier = carrier(context);
        if (carrier.isEmpty()) {
            return transport + "|" + hourBucket();
        }
        return transport + ":" + carrier + "|" + hourBucket();
    }

    /** Key without the time component — used for per-carrier fragment tuning. */
    public static String networkKey(Context context) {
        int transport = transport(context);
        String carrier = carrier(context);
        return carrier.isEmpty() ? String.valueOf(transport) : transport + ":" + carrier;
    }

    /** True when there is any usable network at all. */
    public static boolean online(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            Network active = cm.getActiveNetwork();
            if (active == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
