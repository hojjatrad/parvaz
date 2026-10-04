package com.parvaz.tunnel.core;

import com.parvaz.tunnel.model.Profile;

import java.util.List;
import java.util.Set;

/**
 * Picks the server to connect to from measured latency.
 *
 * <p>Only a positive, measured value counts: a state label (untested, unconfirmed, busy,
 * route unverified, ...) is never treated as a fast server. Among measured rows the lowest
 * round trip wins; a favourite gets a small, fixed head start and a history of successful
 * connections on this network breaks ties, so a server that merely answered a probe once
 * does not outrank one that has actually worked here.
 */
public final class BestServer {

    /** Milliseconds of advantage given to a favourite. Deliberately small. */
    static final int FAVORITE_BONUS = 60;
    /** A stale measurement is still usable but is ranked behind fresh ones. */
    static final int STALE_PENALTY = 250;

    private BestServer() {
    }

    public static Profile choose(List<Profile> profiles, Set<String> favorites) {
        return choose(profiles, favorites, null, null);
    }

    /**
     * @param memory optional (server, network) memory used only to break ties.
     * @param context application context the memory is keyed against; may be null.
     */
    public static Profile choose(List<Profile> profiles, Set<String> favorites,
                                 ServerMemory memory, android.content.Context context) {
        Profile best = null;
        int bestScore = Integer.MAX_VALUE;
        int bestConfidence = -1;
        if (profiles == null) {
            return null;
        }
        for (Profile profile : profiles) {
            if (profile == null || profile.ping <= 0 || !ProtocolSupport.isSupported(profile)) {
                continue;
            }
            int score = profile.ping;
            if (favorites != null && favorites.contains(profile.id)) {
                score -= FAVORITE_BONUS;
            }
            // A stored number without current process/network proof is history, not a
            // measurement of now, so it ranks behind anything freshly measured.
            if (profile.latency == null || !profile.latency.fresh()) {
                score += STALE_PENALTY;
            }
            int confidence = confidence(memory, context, profile);
            if (score < bestScore || (score == bestScore && confidence > bestConfidence)) {
                best = profile;
                bestScore = score;
                bestConfidence = confidence;
            }
        }
        return best;
    }

    private static int confidence(ServerMemory memory, android.content.Context context,
                                  Profile profile) {
        if (memory == null || context == null) {
            return -1;
        }
        try {
            return memory.scoreFor(context, profile.id);
        } catch (Throwable unavailable) {
            return -1;
        }
    }

    /** True when no row carries a measured value, i.e. the list must be measured first. */
    public static boolean needsMeasurement(List<Profile> profiles) {
        if (profiles == null) {
            return true;
        }
        for (Profile profile : profiles) {
            if (profile != null && profile.ping > 0 && profile.latency != null
                    && profile.latency.fresh()) {
                return false;
            }
        }
        return true;
    }
}
