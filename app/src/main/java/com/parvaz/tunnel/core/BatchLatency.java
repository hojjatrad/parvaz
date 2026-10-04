package com.parvaz.tunnel.core;

import android.content.Context;

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
    /** Parallel HTTPS probes inside a group. */
    static final int PARALLEL = 6;
    /** Parallel per-profile fallback probes; bounded by the native admission gate anyway. */
    static final int FALLBACK_PARALLEL = 3;

    private BatchLatency() {
    }

    public static void measure(Context context, List<Profile> profiles, String url, Sink sink) {
        if (profiles == null || profiles.isEmpty() || sink == null) {
            return;
        }
        Context app = context.getApplicationContext();
        Prefs prefs = new Prefs(app);
        String chainId = prefs.f343a.getString("chain_profile", "");
        List<Profile> shared = new ArrayList<>();
        List<Profile> perProfile = new ArrayList<>();
        for (Profile profile : profiles) {
            if (profile == null) {
                continue;
            }
            if (shareable(profile, chainId)) {
                shared.add(profile);
            } else {
                perProfile.add(profile);
            }
        }
        for (int from = 0; from < shared.size(); from += GROUP) {
            if (sink.cancelled()) {
                return;
            }
            List<Profile> group = shared.subList(from, Math.min(shared.size(), from + GROUP));
            perProfile.addAll(runGroup(app, prefs, group, url, sink));
        }
        runPerProfile(app, perProfile, url, sink);
    }

    /** True when the profile's own route can be expressed as one outbound inside a shared core. */
    static boolean shareable(Profile profile, String chainId) {
        if (profile == null || profile.protocol == null) {
            return false;
        }
        if (EngineConfig.external(profile.protocol) || FullConfig.isFull(profile.protocol)) {
            return false;
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

    /** @return the profiles of this group that still need the per-profile path. */
    private static List<Profile> runGroup(Context app, Prefs prefs, List<Profile> group,
                                          String url, Sink sink) {
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
            probeAll(plan, byId, url, sink);
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

    private static void probeAll(BatchProbeConfig.Plan plan, Map<String, Profile> byId,
                                 String url, Sink sink) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLEL, plan.ports.size()),
                runnable -> {
                    Thread thread = new Thread(runnable, "Parvaz batch latency");
                    thread.setDaemon(true);
                    return thread;
                });
        List<Future<?>> running = new ArrayList<>();
        try {
            for (Map.Entry<String, Integer> entry : plan.ports.entrySet()) {
                String id = entry.getKey();
                int port = entry.getValue();
                running.add(pool.submit(() -> {
                    if (sink.cancelled()) {
                        return;
                    }
                    long value;
                    String target = null;
                    try {
                        value = VerifiedProbe.measureFast(port, url);
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

    private static void runPerProfile(Context app, List<Profile> profiles, String url, Sink sink) {
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
                        value = ProxyMeasurement.measureQueued(app, profile, url);
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
