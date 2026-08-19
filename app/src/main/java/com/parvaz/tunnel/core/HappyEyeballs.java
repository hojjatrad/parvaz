package com.parvaz.tunnel.core;

import android.content.Context;

import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import libv2ray.Libv2ray;

/**
 * Picks a working server fast by racing several candidates at once (idea 1.2).
 *
 * <p>The old behaviour was strictly serial: try a server, wait out the full timeout,
 * fail, try the next. Under heavy filtering that is 30-40 seconds of spinner. Here the
 * top candidates are probed concurrently and the first to answer wins, which brings a
 * normal connect down to two or three seconds.
 *
 * <p>Candidate order comes from {@link ServerMemory}, so servers that have actually
 * worked on this network at this time of day are probed first. Losing probes are
 * abandoned as soon as there is a winner — each is only a lightweight outbound delay
 * measurement, not a full tunnel, so there is nothing to tear down.
 */
public final class HappyEyeballs {

    /** How many servers to race. Three is plenty and keeps the battery cost trivial. */
    public static final int DEFAULT_PARALLEL = 3;

    /** Give up on the whole race after this long. */
    private static final long RACE_TIMEOUT_MS = 12000;

    private HappyEyeballs() {
    }

    /** Outcome of a race. */
    public static final class Result {
        /** The winning profile, or null when every candidate failed. */
        public Profile winner;

        /** Measured handshake delay of the winner in ms, or -1. */
        public int delayMs = -1;

        /** How many candidates were probed. */
        public int probed = 0;

        /** Wall-clock duration of the race in ms. */
        public long elapsedMs = 0;
    }

    /**
     * Races up to {@code parallel} of the supplied profiles and returns the first that
     * responds. Blocking; call from a background thread.
     */
    public static Result race(Context context, List<Profile> profiles, int parallel) {
        Result result = new Result();
        long started = System.currentTimeMillis();

        if (profiles == null || profiles.isEmpty()) {
            return result;
        }

        final Context app = context.getApplicationContext();

        // Best-first ordering from what we have learned about this network.
        ArrayList<Profile> ranked = new ServerMemory(app).rank(app, profiles);

        int count = Math.min(parallel <= 0 ? DEFAULT_PARALLEL : parallel, ranked.size());
        final List<Profile> candidates = new ArrayList<Profile>(ranked.subList(0, count));
        result.probed = count;

        final Prefs prefs = new Prefs(app);
        final String pingUrl = prefs.f343a.getString(
                "ping_url", "https://www.gstatic.com/generate_204");

        final AtomicBoolean decided = new AtomicBoolean(false);
        final AtomicInteger bestDelay = new AtomicInteger(-1);
        final Profile[] winner = new Profile[1];
        final Object lock = new Object();

        ExecutorService pool = Executors.newFixedThreadPool(count, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "parvaz-race");
                thread.setDaemon(true);
                return thread;
            }
        });

        try {
            for (int i = 0; i < candidates.size(); i++) {
                final Profile candidate = candidates.get(i);
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (decided.get()) {
                            return;   // someone already won; don't waste the radio
                        }
                        long delay;
                        try {
                            String config =
                                    XrayConfigBuilder.b(candidate, prefs, null, false, false);
                            delay = Libv2ray.measureOutboundDelay(config, pingUrl);
                        } catch (Throwable t) {
                            delay = -1;
                        }
                        if (delay <= 0) {
                            return;
                        }
                        if (decided.compareAndSet(false, true)) {
                            synchronized (lock) {
                                winner[0] = candidate;
                                bestDelay.set((int) delay);
                                lock.notifyAll();
                            }
                        }
                    }
                });
            }

            synchronized (lock) {
                long deadline = System.currentTimeMillis() + RACE_TIMEOUT_MS;
                while (winner[0] == null) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    try {
                        lock.wait(remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                result.winner = winner[0];
                result.delayMs = bestDelay.get();
            }
        } finally {
            decided.set(true);
            pool.shutdownNow();
            try {
                pool.awaitTermination(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        result.elapsedMs = System.currentTimeMillis() - started;

        // Feed the outcome back so the next race starts better informed.
        ServerMemory memory = new ServerMemory(app);
        if (result.winner != null) {
            memory.recordSuccess(app, result.winner.id, result.delayMs);
            LogBuffer.listener("connected via " + result.winner.remark
                    + " in " + result.elapsedMs + " ms (raced " + result.probed + ")");
        } else {
            for (int i = 0; i < candidates.size(); i++) {
                memory.recordFailure(app, candidates.get(i).id);
            }
            LogBuffer.listener("no server answered within " + (RACE_TIMEOUT_MS / 1000) + "s");
        }
        return result;
    }

    /** Convenience overload using {@link #DEFAULT_PARALLEL}. */
    public static Result race(Context context, List<Profile> profiles) {
        return race(context, profiles, DEFAULT_PARALLEL);
    }
}
