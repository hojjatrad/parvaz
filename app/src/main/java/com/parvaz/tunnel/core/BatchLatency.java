package com.parvaz.tunnel.core;

import android.content.Context;

import com.parvaz.tunnel.config.BatchEngineConfig;
import com.parvaz.tunnel.config.EngineConfig;
import com.parvaz.tunnel.config.FullConfig;
import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Measures a whole server list quickly by sharing one engine instance between many profiles.
 *
 * <p>The old path cost one native core start, one 3-sample HTTPS measurement and one
 * {@code StopLoop} per profile, all behind a 3 permit gate and a 3 thread pool, so a fifty
 * row list took minutes and usually finished as state labels rather than numbers.
 *
 * <p>Here the eligible profiles are grouped, each group gets ONE core carrying one pinned
 * loopback inbound per profile ({@link BatchProbeConfig}), and the authenticated HTTPS
 * round trips run in parallel over those ports. The proof of route is unchanged: a 2xx
 * response that came back through that profile's own outbound and nothing else.
 *
 * <p>Anything that cannot share a core - external engines, full configs, chained profiles,
 * the currently live profile, or a config the composer rejects - is measured by the existing
 * per-profile path, so no profile loses coverage.
 */
public final class BatchLatency {

    /** Receives every result as soon as it is known, and can stop the run. */
    public interface Sink {
        /** @param raw engine-level value; map with {@link LatencyResult#measured(long)}. */
        void result(String profileId, long raw, String target);

        boolean cancelled();
    }

    /** Profiles per shared core. Small enough that one bad config costs little. */
    static final int GROUP = 12;
    /** Upper bound for parallel HTTPS probes inside a group. */
    static final int PARALLEL = 6;
    /** Lower bound: even a small, struggling device keeps two probes in flight. */
    static final int MIN_PARALLEL = 2;

    /**
     * How many probes to run at once.
     *
     * <p>A fixed six was wrong at both ends: a two-core phone spent its time context
     * switching, and a list of three servers started a six thread pool to run three
     * tasks. Parallelism now follows the device and the work in front of it, and backs
     * off while consecutive groups are failing - when a network is dropping probes,
     * adding more concurrent ones only produces more timeouts.
     *
     * @param cpus     available processors, as reported by the runtime
     * @param pending  how many servers this group still has to measure
     * @param failures consecutive fully failed groups observed in this run
     */
    static int parallelism(int cpus, int pending, int failures) {
        int byDevice = Math.max(MIN_PARALLEL, Math.min(PARALLEL, Math.max(1, cpus)));
        int wanted = Math.min(byDevice, Math.max(1, pending));
        for (int i = 0; i < Math.max(0, failures); i++) {
            wanted = Math.max(MIN_PARALLEL, wanted / 2);
        }
        return Math.max(1, Math.min(wanted, Math.max(1, pending)));
    }
    /** Parallel per-profile fallback probes; bounded by the native admission gate anyway. */
    static final int FALLBACK_PARALLEL = 3;
    /** Profiles per shared external engine. Smaller than the Xray group: one child process
     *  carries them all, and a smaller blast radius keeps one bad config cheap. */
    static final int ENGINE_GROUP = 8;

    private BatchLatency() {
    }

    public static void measure(Context context, List<Profile> profiles, String url, Sink sink) {
        measure(context, profiles, url, false, sink);
    }

    /**
     * @param strictTarget accept only {@code url} itself, with no fallback endpoint list.
     *                     The real-bypass test runs this way: it must prove that one
     *                     specific filtered host answered through this profile's route.
     */
    public static void measure(Context context, List<Profile> profiles, String url,
                               boolean strictTarget, Sink sink) {
        if (profiles == null || profiles.isEmpty() || sink == null) {
            return;
        }
        Context app = context.getApplicationContext();
        Prefs prefs = new Prefs(app);
        String chainId = prefs.f343a.getString("chain_profile", "");
        List<Profile> shared = new ArrayList<>();
        List<Profile> engineShared = new ArrayList<>();
        List<Profile> perProfile = new ArrayList<>();
        for (Profile profile : profiles) {
            if (profile == null) {
                continue;
            }
            if (shareable(prefs.f343a, profile, chainId)) {
                shared.add(profile);
            } else if (engineShareable(prefs.f343a, profile, chainId)) {
                engineShared.add(profile);
            } else {
                perProfile.add(profile);
            }
        }
        // Consecutive dead groups mean the network, not the servers, is the problem;
        // the next group then probes more gently instead of piling on timeouts.
        int failures = 0;
        for (int from = 0; from < shared.size(); from += GROUP) {
            if (sink.cancelled()) {
                return;
            }
            List<Profile> group = shared.subList(from, Math.min(shared.size(), from + GROUP));
            List<Profile> leftovers = runGroup(app, prefs, group, url, strictTarget, sink, failures);
            failures = leftovers.size() >= group.size() ? failures + 1 : 0;
            perProfile.addAll(leftovers);
        }
        for (int from = 0; from < engineShared.size(); from += ENGINE_GROUP) {
            if (sink.cancelled()) {
                return;
            }
            List<Profile> group = engineShared.subList(from,
                    Math.min(engineShared.size(), from + ENGINE_GROUP));
            List<Profile> leftovers = runEngineGroup(app, group, url, strictTarget, sink, failures);
            failures = leftovers.size() >= group.size() ? failures + 1 : 0;
            perProfile.addAll(leftovers);
        }
        runPerProfile(app, perProfile, url, strictTarget, sink);
    }

    /** True when the profile's own route can be expressed as one outbound inside a shared core. */
    static boolean shareable(android.content.SharedPreferences prefs, Profile profile, String chainId) {
        if (profile == null || profile.protocol == null) {
            return false;
        }
        if (!CoreSelection.XRAY.equals(CoreSelection.active(prefs, profile))
                || FullConfig.isFull(profile.protocol)) {
            return false; // Measured on whichever engine will actually carry it.
        }
        if (chainId != null && !chainId.isEmpty() && !chainId.equals(profile.id)) {
            return false; // A chain needs the full per-profile config.
        }
        if (!ProtocolSupport.isSupported(profile)) {
            return false;
        }
        // The live profile is measured through its existing pinned listener, never by a
        // second engine instance started next to the running tunnel.
        return CoreManager.b().liveProbe(profile) == null;
    }

    /** True when this profile is one external-engine outbound that can share a child engine. */
    static boolean engineShareable(android.content.SharedPreferences prefs, Profile profile,
                                   String chainId) {
        if (!BatchEngineConfig.shareable(profile)
                || !CoreSelection.SINGBOX.equals(CoreSelection.active(prefs, profile))) {
            return false;
        }
        if (chainId != null && !chainId.isEmpty() && !chainId.equals(profile.id)) {
            return false; // A chain needs the full per-profile configuration.
        }
        // The live profile keeps its own pinned listener; never start a second engine
        // beside the running tunnel for it.
        return CoreManager.b().liveProbe(profile) == null;
    }

    /**
     * Measures a group of external-engine profiles behind ONE sing-box child process.
     *
     * @return the profiles of this group that still need the per-profile path.
     */
    private static List<Profile> runEngineGroup(Context app, List<Profile> group, String url,
                                                boolean strictTarget, Sink sink, int failures) {
        List<Profile> leftovers = new ArrayList<>();
        Map<String, Profile> byId = new LinkedHashMap<>();
        List<BatchEngineConfig.Member> members = new ArrayList<>();
        for (Profile profile : group) {
            if (byId.containsKey(profile.id)) {
                continue;
            }
            try {
                members.add(new BatchEngineConfig.Member(profile, freePort()));
                byId.put(profile.id, profile);
            } catch (Throwable unusable) {
                leftovers.add(profile);
            }
        }
        if (members.isEmpty()) {
            return leftovers;
        }
        BatchEngineConfig.Plan plan = BatchEngineConfig.compose(members);
        for (String rejected : plan.rejected) {
            Profile profile = byId.remove(rejected);
            if (profile != null) {
                leftovers.add(profile);
            }
        }
        if (plan.isEmpty()) {
            return leftovers;
        }
        if (sink.cancelled()) {
            return leftovers;
        }
        ExternalCore engine = null;
        try {
            engine = ExternalCore.startComposed(app, plan.config,
                    plan.ports.values().iterator().next());
            probePorts(plan.ports, url, strictTarget, sink, failures);
            return leftovers;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return leftovers;
        } catch (Throwable failure) {
            // One shared engine failing must not fail every row in it: re-measure them
            // individually, where a single bad configuration only costs itself.
            leftovers.addAll(byId.values());
            return leftovers;
        } finally {
            if (engine != null) {
                engine.close();
            }
        }
    }

    /** @return the profiles of this group that still need the per-profile path. */
    private static List<Profile> runGroup(Context app, Prefs prefs, List<Profile> group,
                                          String url, boolean strictTarget, Sink sink,
                                          int failures) {
        List<Profile> leftovers = new ArrayList<>();
        Map<String, Profile> byId = new LinkedHashMap<>();
        List<BatchProbeConfig.Member> members = new ArrayList<>();
        for (Profile profile : group) {
            if (byId.containsKey(profile.id)) {
                continue;
            }
            try {
                String config = XrayConfigBuilder.b(profile, prefs, null, false, false);
                members.add(new BatchProbeConfig.Member(profile.id, config, freePort()));
                byId.put(profile.id, profile);
            } catch (Throwable unusable) {
                leftovers.add(profile);
            }
        }
        if (members.isEmpty()) {
            return leftovers;
        }
        BatchProbeConfig.Plan plan;
        try {
            plan = BatchProbeConfig.compose(members);
        } catch (Throwable invalid) {
            leftovers.addAll(byId.values());
            return leftovers;
        }
        for (String rejected : plan.rejected) {
            Profile profile = byId.remove(rejected);
            if (profile != null) {
                leftovers.add(profile);
            }
        }
        if (plan.isEmpty()) {
            return leftovers;
        }

        boolean admitted = false;
        CoreController controller = null;
        try {
            admitted = ProxyMeasurement.enterNative(true);
            if (!admitted) {
                leftovers.addAll(byId.values());
                return leftovers;
            }
            if (sink.cancelled()) {
                return leftovers;
            }
            controller = Libv2ray.newCoreController(new CoreCallbackHandler() {
                public long startup() {
                    return 0;
                }

                public long shutdown() {
                    return 0;
                }

                public long onEmitStatus(long code, String message) {
                    return 0;
                }
            });
            CoreManager.b().startIsolatedProbe(controller, plan.config);
            if (!controller.getIsRunning()) {
                // One shared core failed to build: do not punish every row, re-measure
                // them individually where a single bad config only fails itself.
                leftovers.addAll(byId.values());
                return leftovers;
            }
            probePorts(plan.ports, url, strictTarget, sink, failures);
            return leftovers;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return leftovers;
        } catch (Throwable failure) {
            leftovers.addAll(byId.values());
            return leftovers;
        } finally {
            try {
                if (controller != null) {
                    controller.stopLoop();
                }
            } catch (Throwable ignored) {
                // Nothing further to do; the permit is released below either way.
            } finally {
                if (admitted) {
                    ProxyMeasurement.exitNative();
                }
            }
        }
    }

    /** Measures every (profile, loopback port) pair in parallel through the shared engine. */
    private static void probePorts(Map<String, Integer> ports, String url,
                                   boolean strictTarget, Sink sink, int failures) {
        ExecutorService pool = Executors.newFixedThreadPool(
                parallelism(Runtime.getRuntime().availableProcessors(), ports.size(), failures),
                runnable -> {
                    Thread thread = new Thread(runnable, "Parvaz batch latency");
                    thread.setDaemon(true);
                    return thread;
                });
        List<Future<?>> running = new ArrayList<>();
        try {
            for (Map.Entry<String, Integer> entry : ports.entrySet()) {
                String id = entry.getKey();
                int port = entry.getValue();
                running.add(pool.submit(() -> {
                    if (sink.cancelled()) {
                        return;
                    }
                    long value;
                    String target = null;
                    try {
                        value = VerifiedProbe.measureFast(port, url, strictTarget);
                        target = VerifiedProbe.lastTarget();
                    } catch (InterruptedException cancelled) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable failure) {
                        value = VerifiedProbe.START_FAILED;
                    }
                    sink.result(id, value, target);
                }));
            }
            for (Future<?> future : running) {
                try {
                    future.get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable ignored) {
                    // A single probe failing is already reported through the sink.
                }
            }
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void runPerProfile(Context app, List<Profile> profiles, String url,
                                      boolean strictTarget, Sink sink) {
        if (profiles.isEmpty()) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(FALLBACK_PARALLEL, profiles.size()), runnable -> {
                    Thread thread = new Thread(runnable, "Parvaz latency fallback");
                    thread.setDaemon(true);
                    return thread;
                });
        List<Future<?>> running = new ArrayList<>();
        try {
            for (Profile profile : profiles) {
                running.add(pool.submit(() -> {
                    if (sink.cancelled()) {
                        return;
                    }
                    long value;
                    String target = null;
                    VerifiedProbe.clearTarget();
                    try {
                        value = strictTarget
                                ? ProxyMeasurement.measureStrictTarget(app, profile, url)
                                : ProxyMeasurement.measureQueued(app, profile, url);
                        target = VerifiedProbe.lastTarget();
                    } catch (InterruptedException cancelled) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable failure) {
                        value = VerifiedProbe.START_FAILED;
                    }
                    sink.result(profile.id, value, target);
                }));
            }
            for (Future<?> future : running) {
                try {
                    future.get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable ignored) {
                    // Already reported through the sink.
                }
            }
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int freePort() throws java.io.IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }
}
