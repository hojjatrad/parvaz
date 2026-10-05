package com.parvaz.tunnel.core;

import android.content.SharedPreferences;

import com.parvaz.tunnel.config.EngineConfig;
import com.parvaz.tunnel.config.FullConfig;
import com.parvaz.tunnel.config.ProtocolNames;
import com.parvaz.tunnel.config.SingBoxOutbound;
import com.parvaz.tunnel.model.Profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The single place that answers "which engine runs this server, and what comes next".
 *
 * <p>Before this class the engine was an accident of the protocol scattered across four
 * files: {@code EngineConfig.external(profile.protocol)} was read in the tunnel path, the
 * measurement path, the readiness pinner and the batch ping, and a failure to start simply
 * ended the attempt. Now there is one ordered answer per server:
 *
 * <ol>
 *   <li>the user's explicit per-server choice, when that engine can actually dial it;</li>
 *   <li>otherwise the engine that most recently produced a working start for it;</li>
 *   <li>otherwise the protocol's natural engine;</li>
 *   <li>followed by the other capable engine, as a failover candidate.</li>
 * </ol>
 *
 * <p>What this never does: invent a path. An engine is only listed when it can reproduce
 * the profile's own settings exactly ({@link SingBoxOutbound#capable}), so a failover can
 * change which program carries the bytes but never where they go, and never weakens TLS,
 * adds a direct fallback or forces multiplexing.
 */
public final class CoreSelection {

    public static final String AUTO = "auto";
    public static final String XRAY = "xray";
    public static final String SINGBOX = "singbox";
    public static final String MIHOMO = "mihomo";

    private static final String CHOICE = "core_choice:";
    private static final String LAST_GOOD = "core_last_good:";

    private CoreSelection() {
    }

    /** True when the bundled Xray build can dial this profile itself. */
    public static boolean xrayCapable(Profile profile) {
        if (profile == null || profile.protocol == null) {
            return false;
        }
        String protocol = ProtocolNames.canonical(profile.protocol);
        if (protocol.equals("full-xray")) {
            return true;
        }
        if (FullConfig.isFull(protocol) || EngineConfig.external(protocol)) {
            return false;
        }
        return ProtocolSupport.isSupported(profile);
    }

    /** True when the bundled sing-box build can dial this profile itself. */
    public static boolean singBoxCapable(Profile profile) {
        if (profile == null || profile.protocol == null) {
            return false;
        }
        String protocol = ProtocolNames.canonical(profile.protocol);
        if (protocol.equals("full-singbox")) {
            return true;
        }
        if (FullConfig.isFull(protocol)) {
            return false;
        }
        if (EngineConfig.external(protocol)) {
            return true;
        }
        return SingBoxOutbound.capable(profile);
    }

    /** True only for a full Clash profile, which mihomo alone can interpret. */
    public static boolean mihomoCapable(Profile profile) {
        return profile != null && profile.protocol != null
                && ProtocolNames.canonical(profile.protocol).equals("full-clash");
    }

    public static boolean capable(String core, Profile profile) {
        if (XRAY.equals(core)) {
            return xrayCapable(profile);
        }
        if (SINGBOX.equals(core)) {
            return singBoxCapable(profile);
        }
        if (MIHOMO.equals(core)) {
            return mihomoCapable(profile);
        }
        return false;
    }

    /** The engine this profile uses when nobody has chosen anything: today's behaviour. */
    public static String natural(Profile profile) {
        if (mihomoCapable(profile)) {
            return MIHOMO;
        }
        if (profile != null && profile.protocol != null
                && EngineConfig.external(ProtocolNames.canonical(profile.protocol))) {
            return SINGBOX;
        }
        return xrayCapable(profile) ? XRAY : (singBoxCapable(profile) ? SINGBOX : XRAY);
    }

    /** Engines the user may pick for this server, in the order they should be offered. */
    public static List<String> options(Profile profile) {
        List<String> options = new ArrayList<>();
        String natural = natural(profile);
        if (capable(natural, profile)) {
            options.add(natural);
        }
        for (String core : Arrays.asList(XRAY, SINGBOX, MIHOMO)) {
            if (!options.contains(core) && capable(core, profile)) {
                options.add(core);
            }
        }
        return Collections.unmodifiableList(options);
    }

    /** The stored per-server choice: {@link #AUTO} unless the user picked an engine. */
    public static String choice(SharedPreferences prefs, Profile profile) {
        if (prefs == null || profile == null || profile.id == null || profile.id.isEmpty()) {
            return AUTO;
        }
        String stored = prefs.getString(CHOICE + profile.id, AUTO);
        if (stored == null || stored.isEmpty()) {
            return AUTO;
        }
        return Arrays.asList(AUTO, XRAY, SINGBOX, MIHOMO).contains(stored) ? stored : AUTO;
    }

    /** Stores a per-server choice. {@link #AUTO} (or an incapable engine) clears it. */
    public static void choose(SharedPreferences prefs, Profile profile, String core) {
        if (prefs == null || profile == null || profile.id == null || profile.id.isEmpty()) {
            return;
        }
        if (core == null || AUTO.equals(core) || !capable(core, profile)) {
            prefs.edit().remove(CHOICE + profile.id).apply();
            return;
        }
        prefs.edit().putString(CHOICE + profile.id, core).apply();
    }

    /**
     * Remembers the engine that actually brought this server up, so a server that only
     * works on the second engine stops paying for a failed first attempt on every connect.
     */
    public static void remember(SharedPreferences prefs, Profile profile, String core) {
        if (prefs == null || profile == null || profile.id == null || profile.id.isEmpty()
                || core == null || AUTO.equals(core)) {
            return;
        }
        if (core.equals(natural(profile))) {
            prefs.edit().remove(LAST_GOOD + profile.id).apply();
            return;
        }
        prefs.edit().putString(LAST_GOOD + profile.id, core).apply();
    }

    /** Drops both the memory and the explicit choice for one server. */
    public static void forget(SharedPreferences prefs, String profileId) {
        if (prefs == null || profileId == null || profileId.isEmpty()) {
            return;
        }
        prefs.edit().remove(LAST_GOOD + profileId).remove(CHOICE + profileId).apply();
    }

    /**
     * Engines to try for this server, best first. Never empty for a supported profile;
     * an unsupported profile yields an empty list and the caller fails as before.
     */
    public static List<String> order(SharedPreferences prefs, Profile profile) {
        List<String> capable = new ArrayList<>(options(profile));
        if (capable.isEmpty()) {
            return capable;
        }
        String chosen = choice(prefs, profile);
        if (!AUTO.equals(chosen) && capable.contains(chosen)) {
            // An explicit choice is an instruction, not a hint: it is tried first, and the
            // other engine remains only as a failover so the server still connects.
            capable.remove(chosen);
            capable.add(0, chosen);
            return Collections.unmodifiableList(capable);
        }
        String lastGood = prefs == null || profile.id == null || profile.id.isEmpty() ? null
                : prefs.getString(LAST_GOOD + profile.id, null);
        if (lastGood != null && capable.contains(lastGood)) {
            capable.remove(lastGood);
            capable.add(0, lastGood);
        }
        return Collections.unmodifiableList(capable);
    }

    /** The engine that will be used right now for this server. */
    public static String active(SharedPreferences prefs, Profile profile) {
        List<String> order = order(prefs, profile);
        return order.isEmpty() ? natural(profile) : order.get(0);
    }

    /** True when the chosen engine is a child process rather than the in-process Xray. */
    public static boolean external(SharedPreferences prefs, Profile profile) {
        return !XRAY.equals(active(prefs, profile));
    }

    /** Short label for the server list and the chooser dialog. */
    public static String label(String core) {
        if (SINGBOX.equals(core)) {
            return "sing-box";
        }
        if (MIHOMO.equals(core)) {
            return "mihomo";
        }
        if (XRAY.equals(core)) {
            return "Xray";
        }
        return "Auto";
    }
}
