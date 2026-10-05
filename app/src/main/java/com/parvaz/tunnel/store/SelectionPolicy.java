package com.parvaz.tunnel.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.parvaz.tunnel.model.Profile;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Which server the app comes back to after it is closed and reopened.
 *
 * <p>Before this class the cold start always restored the last VERIFIED connection, so a
 * server the user had deliberately picked but not yet connected to was silently replaced on
 * the next launch. The pin recorded here is the user's explicit choice and wins over that
 * automatic default; without a pin the old behaviour is kept exactly.
 *
 * <p>Order at launch: pinned profile -> last verified connection -> whatever is still valid.
 * The pin stores the source and the identity fingerprint as well as the id, so an edited or
 * re-imported node is not silently treated as the same server.
 */
public final class SelectionPolicy {

    public static final String KEY_PIN = "pinned_profile";
    /** When true the app re-measures and picks the best server itself instead of using a pin. */
    public static final String KEY_AUTO_BEST = "auto_best_connect";

    private SelectionPolicy() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("parvaz_prefs", 0);
    }

    /** Records the user's explicit choice; also makes it the current selection. */
    public static boolean pin(Context context, Profile profile) {
        if (profile == null || profile.id == null || profile.id.isEmpty()) {
            return false;
        }
        try {
            String record = new JSONObject()
                    .put("schema", 1)
                    .put("id", profile.id)
                    .put("source", profile.subscriptionId)
                    .put("identity", ProfileIdentity.fingerprint(profile))
                    .toString();
            return prefs(context).edit()
                    .putString(KEY_PIN, record)
                    .putString("selected_profile", profile.id)
                    .commit();
        } catch (JSONException invalid) {
            return false;
        }
    }

    /** Drops the pin; the automatic last-connected default takes over again. */
    public static boolean clearPin(Context context) {
        return prefs(context).edit().remove(KEY_PIN).commit();
    }

    /** The pinned profile when it still exists in the active source, otherwise null. */
    public static Profile pinned(ProfileStore store, SharedPreferences prefs) {
        String value = prefs.getString(KEY_PIN, "");
        if (value.isEmpty() || value.length() > 4096) {
            return null;
        }
        try {
            com.parvaz.tunnel.config.LinkParser.checkJsonDepth(value);
            JSONObject record = new JSONObject(value);
            if (record.optInt("schema", 0) != 1) {
                return null;
            }
            Profile profile = store.getActiveById(record.getString("id"));
            if (profile == null) {
                return null;
            }
            return record.getString("source").equals(profile.subscriptionId)
                    && record.getString("identity").equals(ProfileIdentity.fingerprint(profile))
                    ? profile : null;
        } catch (JSONException | IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Cheap display-only read: the pinned id without re-verifying source and identity. */
    public static String pinnedId(Context context) {
        String value = prefs(context).getString(KEY_PIN, "");
        if (value.isEmpty() || value.length() > 4096) {
            return "";
        }
        try {
            JSONObject record = new JSONObject(value);
            return record.optInt("schema", 0) == 1 ? record.optString("id", "") : "";
        } catch (JSONException invalid) {
            return "";
        }
    }

    public static boolean isPinned(Context context, String profileId) {
        if (profileId == null || profileId.isEmpty()) {
            return false;
        }
        Profile pinned = pinned(ProfileStore.f(context), prefs(context));
        return pinned != null && profileId.equals(pinned.id);
    }

    public static boolean autoBest(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_BEST, false);
    }

    /**
     * Cold launch / STOP / boot restore. A pinned server is restored first; otherwise the
     * existing last-verified-connection behaviour is used unchanged.
     */
    public static boolean restore(Context context) {
        ProfileStore store = ProfileStore.f(context);
        SharedPreferences prefs = prefs(context);
        synchronized (store) {
            // In automatic mode the app measures and chooses on its own, so a stale pin
            // must not pre-empt that decision.
            if (!prefs.getBoolean(KEY_AUTO_BEST, false)) {
                Profile pinned = pinned(store, prefs);
                if (pinned != null) {
                    return prefs.edit().putString("selected_profile", pinned.id).commit();
                }
            } else {
                // Automatic mode used to fall through to the last verified connection on a
                // cold start, a boot restore or a network rule, so the "best server" promise
                // only held while the user was looking at the list. Reuse the measurements
                // already on record instead: no probe is started here, which keeps a boot or
                // a background network change cheap, and an empty or fully stale list still
                // falls back to the old behaviour below.
                Profile best = bestMeasured(context, store);
                if (best != null) {
                    return prefs.edit().putString("selected_profile", best.id).commit();
                }
            }
        }
        return LastConnected.restore(context);
    }

    /**
     * Lowest measured latency among the active servers, or null when nothing on record is
     * usable. Ranking, freshness penalty and favourite handling all come from
     * {@link com.parvaz.tunnel.core.BestServer}, so the automatic choice made here is the
     * same one the button makes.
     */
    static Profile bestMeasured(Context context, ProfileStore store) {
        try {
            java.util.Set<String> favorites = new Prefs(context).getFavorites();
            return com.parvaz.tunnel.core.BestServer.choose(store.activeProfiles(), favorites);
        } catch (Throwable unavailable) {
            return null;
        }
    }
}
