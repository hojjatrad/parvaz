package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.parvaz.tunnel.model.Profile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Remembers which server worked under which conditions (idea 1.1).
 *
 * <p>Keyed by (server id, network context) where the context is transport + carrier +
 * a six-hour time bucket — see {@link NetContext}. Against each key we keep a success
 * count, a failure count, a rolling average latency and the time of the last success,
 * and from those compute a 0-100 score used to decide what to try first.
 *
 * <p>Stored as one JSON blob in SharedPreferences. No telemetry, nothing uploaded.
 */
public final class ServerMemory {

    private static final String PREFS = "parvaz_memory";
    private static final String KEY_DATA = "entries";

    /** Cap the table so a large subscription cannot grow prefs without bound. */
    private static final int MAX_ENTRIES = 400;

    /** Weight of the newest sample in the latency moving average. */
    private static final double EMA_ALPHA = 0.3d;

    /** A success older than this no longer counts as "recent". */
    private static final long RECENT_WINDOW_MS = 14L * 24 * 60 * 60 * 1000;

    private final SharedPreferences prefs;

    public ServerMemory(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** One row of the memory table. */
    public static final class Entry {
        public String profileId = "";
        public String context = "";
        public int successes = 0;
        public int failures = 0;
        public double avgLatency = -1;
        public long lastSuccess = 0;

        /**
         * 0-100 confidence that this server will work right now, here.
         *
         * <p>Success ratio (Laplace-smoothed, so one lucky connection cannot outrank a
         * long track record) is worth up to 70, latency up to 20 and recency up to 10.
         * An entry with no data scores 50 — neutral, so untried servers sort above
         * known-bad ones but below proven ones.
         */
        public int score() {
            int total = successes + failures;
            if (total == 0) {
                return 50;
            }
            double rate = (successes + 1.0d) / (total + 2.0d);
            double score = rate * 70.0d;

            if (avgLatency > 0) {
                double latencyScore;
                if (avgLatency <= 150) {
                    latencyScore = 20.0d;
                } else if (avgLatency >= 1500) {
                    latencyScore = 0.0d;
                } else {
                    latencyScore = 20.0d * (1.0d - ((avgLatency - 150.0d) / 1350.0d));
                }
                score += latencyScore;
            }

            if (lastSuccess > 0) {
                long age = System.currentTimeMillis() - lastSuccess;
                if (age < RECENT_WINDOW_MS) {
                    score += 10.0d * (1.0d - ((double) age / RECENT_WINDOW_MS));
                }
            }

            if (score < 0) {
                score = 0;
            }
            if (score > 100) {
                score = 100;
            }
            return (int) Math.round(score);
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("p", profileId);
            json.put("c", context);
            json.put("s", successes);
            json.put("f", failures);
            json.put("l", avgLatency);
            json.put("t", lastSuccess);
            return json;
        }

        static Entry fromJson(JSONObject json) {
            Entry entry = new Entry();
            entry.profileId = json.optString("p", "");
            entry.context = json.optString("c", "");
            entry.successes = json.optInt("s", 0);
            entry.failures = json.optInt("f", 0);
            entry.avgLatency = json.optDouble("l", -1);
            entry.lastSuccess = json.optLong("t", 0);
            return entry;
        }
    }

    // ----------------------------------------------------------------- recording

    /** Records a successful connection and its latency (ms, or -1 if unknown). */
    public synchronized void recordSuccess(Context context, String profileId, int latencyMs) {
        if (profileId == null || profileId.isEmpty()) {
            return;
        }
        String ctx = NetContext.key(context);
        List<Entry> all = load();
        Entry entry = find(all, profileId, ctx);
        if (entry == null) {
            entry = new Entry();
            entry.profileId = profileId;
            entry.context = ctx;
            all.add(entry);
        }
        entry.successes++;
        entry.lastSuccess = System.currentTimeMillis();
        if (latencyMs > 0) {
            entry.avgLatency = entry.avgLatency <= 0
                    ? latencyMs
                    : (EMA_ALPHA * latencyMs) + ((1 - EMA_ALPHA) * entry.avgLatency);
        }
        save(all);
    }

    /** Records a failed or unusable connection. */
    public synchronized void recordFailure(Context context, String profileId) {
        if (profileId == null || profileId.isEmpty()) {
            return;
        }
        String ctx = NetContext.key(context);
        List<Entry> all = load();
        Entry entry = find(all, profileId, ctx);
        if (entry == null) {
            entry = new Entry();
            entry.profileId = profileId;
            entry.context = ctx;
            all.add(entry);
        }
        entry.failures++;
        save(all);
    }

    // ------------------------------------------------------------------- queries

    /** Score for one server in the current context, 0-100 (50 when never tried). */
    public int scoreFor(Context context, String profileId) {
        Entry entry = find(load(), profileId, NetContext.key(context));
        return entry == null ? 50 : entry.score();
    }

    /** The stored entry for one server in the current context, or null. */
    public Entry entryFor(Context context, String profileId) {
        return find(load(), profileId, NetContext.key(context));
    }

    /**
     * Orders profiles best-first for the current conditions. Ties break on last
     * measured ping, treating "unknown" as mid-range.
     */
    public ArrayList<Profile> rank(Context context, List<Profile> profiles) {
        final String ctx = NetContext.key(context);
        final List<Entry> all = load();

        ArrayList<Profile> sorted = new ArrayList<Profile>(profiles);
        Collections.sort(sorted, new Comparator<Profile>() {
            @Override
            public int compare(Profile a, Profile b) {
                Entry ea = find(all, a.id, ctx);
                Entry eb = find(all, b.id, ctx);
                int sa = ea == null ? 50 : ea.score();
                int sb = eb == null ? 50 : eb.score();
                if (sa != sb) {
                    return sb - sa;
                }
                int pa = a.ping > 0 ? a.ping : 9999;
                int pb = b.ping > 0 ? b.ping : 9999;
                return pa - pb;
            }
        });
        return sorted;
    }

    /** Forgets everything. Exposed through Settings. */
    public synchronized void clear() {
        prefs.edit().remove(KEY_DATA).apply();
    }

    // ------------------------------------------------------------------- storage

    private static Entry find(List<Entry> entries, String profileId, String ctx) {
        if (profileId == null) {
            return null;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.profileId.equals(profileId) && entry.context.equals(ctx)) {
                return entry;
            }
        }
        return null;
    }

    private List<Entry> load() {
        ArrayList<Entry> out = new ArrayList<Entry>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_DATA, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json != null) {
                    out.add(Entry.fromJson(json));
                }
            }
        } catch (Throwable ignored) {
            // Corrupt store: start over rather than crash the app.
        }
        return out;
    }

    private void save(List<Entry> entries) {
        try {
            if (entries.size() > MAX_ENTRIES) {
                // Evict the stalest rows first.
                Collections.sort(entries, new Comparator<Entry>() {
                    @Override
                    public int compare(Entry a, Entry b) {
                        return Long.compare(b.lastSuccess, a.lastSuccess);
                    }
                });
                while (entries.size() > MAX_ENTRIES) {
                    entries.remove(entries.size() - 1);
                }
            }
            JSONArray array = new JSONArray();
            for (int i = 0; i < entries.size(); i++) {
                array.put(entries.get(i).toJson());
            }
            prefs.edit().putString(KEY_DATA, array.toString()).apply();
        } catch (Throwable ignored) {
        }
    }
}
