package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;

import libv2ray.Libv2ray;

/**
 * Finds good TLS-fragmenting parameters for the network the phone is on (idea 1.4).
 *
 * <p>The optimal fragment size and delay are not universal: what defeats the DPI on one
 * Iranian carrier is ignored by another, and home-ISP Wi-Fi behaves differently again.
 * Rather than shipping one compromise, this tries a handful of candidates against the
 * real network and keeps whichever connects fastest. Results are stored per network
 * (see {@link NetContext#networkKey}) so moving between Wi-Fi and mobile data restores
 * the right settings automatically.
 */
public final class FragmentTuner {

    private static final String PREFS = "parvaz_fragment";

    /**
     * Candidates in roughly cheapest-first order. "length" is the byte range each TLS
     * record is split into, "interval" the millisecond gap between pieces. These cover
     * the combinations known to work against common Iranian DPI deployments.
     */
    private static final String[][] CANDIDATES = {
            // packets,    length,     interval
            {"tlshello", "100-200", "10-20"},
            {"tlshello", "40-60",   "10-20"},
            {"tlshello", "10-20",   "10-20"},
            {"tlshello", "1-3",     "5-10"},
            {"1-3",      "40-60",   "5-10"},
            {"1-5",      "100-200", "1-5"},
    };

    /** Anything slower than this counts as a failure for tuning purposes. */
    private static final long ACCEPT_MS = 5000;

    private FragmentTuner() {
    }

    /** Outcome of a tuning run. */
    public static final class Result {
        public boolean found = false;
        public String packets = "";
        public String length = "";
        public String interval = "";
        public long delayMs = -1;
        public int tried = 0;
    }

    /** Progress callback so the UI can show which combination is being tested. */
    public interface Progress {
        void onStep(int index, int total, String label);
    }

    /**
     * Tests each candidate against {@code profile} on the current network and stores
     * the winner. Blocking and slow (up to ~30 s); run on a background thread.
     */
    public static Result tune(Context context, Profile profile, Progress progress) {
        Result result = new Result();
        if (profile == null) {
            return result;
        }

        Context app = context.getApplicationContext();
        Prefs prefs = new Prefs(app);
        SharedPreferences sp = prefs.f343a;

        // Remember the user's settings so a failed run changes nothing.
        boolean origEnabled = sp.getBoolean("fragment_enabled", false);
        String origPackets = sp.getString("fragment_packets", "tlshello");
        String origLength = sp.getString("fragment_length", "100-200");
        String origInterval = sp.getString("fragment_interval", "10-20");

        String pingUrl = sp.getString("ping_url", "https://www.gstatic.com/generate_204");

        long best = Long.MAX_VALUE;
        int bestIndex = -1;

        try {
            for (int i = 0; i < CANDIDATES.length; i++) {
                String[] candidate = CANDIDATES[i];
                if (progress != null) {
                    progress.onStep(i, CANDIDATES.length,
                            candidate[1] + " / " + candidate[2] + " ms");
                }

                sp.edit()
                        .putBoolean("fragment_enabled", true)
                        .putString("fragment_packets", candidate[0])
                        .putString("fragment_length", candidate[1])
                        .putString("fragment_interval", candidate[2])
                        .commit();

                long delay = measure(profile, prefs, pingUrl);
                result.tried++;

                if (delay > 0 && delay < ACCEPT_MS && delay < best) {
                    best = delay;
                    bestIndex = i;
                }
            }
        } catch (Throwable ignored) {
            android.util.Log.w("Parvaz/FragmentTuner", "Throwable ignored", ignored);
        }

        if (bestIndex >= 0) {
            String[] winner = CANDIDATES[bestIndex];
            result.found = true;
            result.packets = winner[0];
            result.length = winner[1];
            result.interval = winner[2];
            result.delayMs = best;

            sp.edit()
                    .putBoolean("fragment_enabled", true)
                    .putString("fragment_packets", winner[0])
                    .putString("fragment_length", winner[1])
                    .putString("fragment_interval", winner[2])
                    .commit();
            remember(app, winner, best);
            LogBuffer.listener("fragment tuned for this network: "
                    + winner[1] + " bytes / " + winner[2] + " ms (" + best + " ms)");
        } else {
            // Nothing beat the timeout: restore exactly what was there before.
            sp.edit()
                    .putBoolean("fragment_enabled", origEnabled)
                    .putString("fragment_packets", origPackets)
                    .putString("fragment_length", origLength)
                    .putString("fragment_interval", origInterval)
                    .commit();
            LogBuffer.listener("fragment tuning found no improvement, settings unchanged");
        }
        return result;
    }

    /**
     * Re-applies the stored best settings for the current network, if any. Called on a
     * network change so the right profile comes back automatically.
     *
     * @return true when settings were applied
     */
    public static boolean applyStored(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = NetContext.networkKey(app);

        String packets = store.getString(key + ".packets", null);
        String length = store.getString(key + ".length", null);
        String interval = store.getString(key + ".interval", null);
        if (packets == null || length == null || interval == null) {
            return false;
        }

        app.getSharedPreferences("parvaz_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("fragment_enabled", true)
                .putString("fragment_packets", packets)
                .putString("fragment_length", length)
                .putString("fragment_interval", interval)
                .apply();
        return true;
    }

    /** True when this network already has tuned settings on file. */
    public static boolean hasStored(Context context) {
        Context app = context.getApplicationContext();
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(NetContext.networkKey(app) + ".packets");
    }

    /** Human-readable summary of the stored settings, or "" when there are none. */
    public static String describeStored(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = NetContext.networkKey(app);
        String length = store.getString(key + ".length", null);
        String interval = store.getString(key + ".interval", null);
        if (length == null || interval == null) {
            return "";
        }
        long delay = store.getLong(key + ".delay", -1);
        return length + " / " + interval + " ms" + (delay > 0 ? " (" + delay + " ms)" : "");
    }

    private static void remember(Context context, String[] winner, long delay) {
        String key = NetContext.networkKey(context);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key + ".packets", winner[0])
                .putString(key + ".length", winner[1])
                .putString(key + ".interval", winner[2])
                .putLong(key + ".delay", delay)
                .putLong(key + ".at", System.currentTimeMillis())
                .apply();
    }

    /** Measures handshake delay through {@code profile} with the settings now in prefs. */
    private static long measure(Profile profile, Prefs prefs, String url) {
        try {
            String config = XrayConfigBuilder.b(profile, prefs, null, false, false);
            return Libv2ray.measureOutboundDelay(config, url);
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
