package com.parvaz.tunnel.core;

import android.content.Context;
import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import libv2ray.Libv2ray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * True censorship bypass tester: measures real HTTP 204 access through the proxy
 * to blocked endpoints (YouTube / Google) rather than simple TCP ping.
 */
public final class RealBypassTester {

    public static final String FILTERED_PROBE_URL = "https://www.youtube.com/generate_204";

    public interface Callback {
        void onServerTested(Profile profile, boolean passed, int latencyMs);
        void onAllComplete(int passedCount, int failedCount);
    }

    private RealBypassTester() {
    }

    public static int testSingle(Context context, Profile profile) {
        if (profile == null) return -1;
        try {
            Prefs prefs = new Prefs(context);
            String config = XrayConfigBuilder.b(profile, prefs, null, false, false);
            long delay = Libv2ray.measureOutboundDelay(config, FILTERED_PROBE_URL);
            return (delay > 0 && delay < 10000) ? (int) delay : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void testAll(final Context context, final List<Profile> profiles, final Callback callback) {
        if (profiles == null || profiles.isEmpty()) {
            if (callback != null) callback.onAllComplete(0, 0);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                ExecutorService pool = Executors.newFixedThreadPool(6);
                final List<Profile> passed = Collections.synchronizedList(new ArrayList<Profile>());
                final List<Profile> failed = Collections.synchronizedList(new ArrayList<Profile>());

                for (final Profile p : profiles) {
                    pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            int r = testSingle(context, p);
                            boolean ok = r > 0;
                            if (ok) {
                                p.ping = r;
                                passed.add(p);
                            } else {
                                p.ping = -1;
                                failed.add(p);
                            }
                            if (callback != null) {
                                callback.onServerTested(p, ok, r);
                            }
                        }
                    });
                }
                pool.shutdown();
                try {
                    pool.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                if (callback != null) {
                    callback.onAllComplete(passed.size(), failed.size());
                }
            }
        }, "bypass-tester").start();
    }
}
