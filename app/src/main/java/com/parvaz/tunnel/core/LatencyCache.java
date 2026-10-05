package com.parvaz.tunnel.core;

import com.parvaz.tunnel.model.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which rows still have to be measured and which already have a number worth reusing.
 *
 * <p>Why this exists: {@link LatencyStamp#fresh()} is process and network scoped, so every cold
 * start throws away perfectly good numbers taken two minutes earlier and the whole list is
 * measured again before anything can be chosen. That is correct but wasteful, and it is the
 * reason automatic mode feels slow the first time the app is opened.
 *
 * <p>The compromise here is deliberately conservative:
 * <ul>
 *   <li>A stored value counts only while it is younger than the time to live.</li>
 *   <li>It counts only while the network scope that produced it is still the current one.
 *       A measurement taken on another Wi-Fi says nothing about this one, so a changed scope
 *       invalidates the whole cache at once rather than row by row.</li>
 *   <li>Nothing here ever invents a measurement or marks a row as verified; it only decides
 *       whether to re-measure. The connection proof is untouched.</li>
 * </ul>
 */
public final class LatencyCache {

    /** Default lifetime of a stored measurement. Short enough that a dead server surfaces. */
    public static final long DEFAULT_TTL_MS = 10L * 60L * 1000L;
    /** Preference holding the network scope key the last completed batch was measured on. */
    public static final String KEY_SCOPE = "latency_scope";

    private LatencyCache() {
    }

    /** True when this row's stored number may be reused instead of measured again. */
    public static boolean usable(Profile profile, long nowMs, long ttlMs, boolean scopeChanged) {
        if (scopeChanged || profile == null || profile.ping <= 0 || profile.latency == null) {
            return false;
        }
        if (ttlMs <= 0) {
            return false;
        }
        long age = nowMs - profile.latency.measuredAt;
        // A clock moved backwards is not evidence of a recent measurement.
        return age >= 0 && age <= ttlMs;
    }

    /** The rows that must be measured: no number, an expired one, or a changed network. */
    public static List<Profile> stale(List<Profile> profiles, long nowMs, long ttlMs,
                                      boolean scopeChanged) {
        List<Profile> pending = new ArrayList<>();
        if (profiles == null) {
            return pending;
        }
        for (Profile profile : profiles) {
            if (profile != null && !usable(profile, nowMs, ttlMs, scopeChanged)) {
                pending.add(profile);
            }
        }
        return pending;
    }

    /** True when every row already has a reusable number, so no probe has to run at all. */
    public static boolean complete(List<Profile> profiles, long nowMs, long ttlMs,
                                   boolean scopeChanged) {
        return profiles != null && !profiles.isEmpty()
                && stale(profiles, nowMs, ttlMs, scopeChanged).isEmpty();
    }

    /**
     * Whether the cache may be trusted at all. An empty stored or current key means the
     * network could not be identified, and an unidentified network is treated as a change.
     */
    public static boolean scopeChanged(String storedKey, String currentKey) {
        return storedKey == null || currentKey == null
                || storedKey.isEmpty() || currentKey.isEmpty()
                || !storedKey.equals(currentKey);
    }
}
