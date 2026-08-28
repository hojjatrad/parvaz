package com.parvaz.tunnel.core;

import android.content.Context;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileStore;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance Cloudflare Clean IP Scanner for Iranian networks.
 */
public final class CleanIpScanner {

    public static final String[] BASE_CLEAN_IPS = {
            "104.16.80.1", "104.17.160.100", "104.18.20.50", "104.19.140.25",
            "104.20.5.1", "104.21.30.15", "104.22.10.5", "104.24.10.15",
            "172.64.150.8", "172.65.200.1", "172.67.180.120", "172.68.50.2",
            "162.159.138.10", "162.159.192.1", "162.159.200.5",
            "198.41.200.5", "198.41.214.162", "188.114.97.2", "188.114.98.5",
            "141.101.120.1", "108.162.193.1", "190.93.242.1"
    };

    public static final class ScannedIp {
        public final String ip;
        public int latencyMs;
        public boolean ok;

        public ScannedIp(String ip, int latencyMs, boolean ok) {
            this.ip = ip;
            this.latencyMs = latencyMs;
            this.ok = ok;
        }
    }

    public interface ScanCallback {
        void onProgress(int scanned, int total, ScannedIp latest);
        void onComplete(List<ScannedIp> workingIps);
    }

    private CleanIpScanner() {
    }

    public static List<String> generateCandidateIps(int count) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, BASE_CLEAN_IPS);
        Random r = new Random();
        int[] subnets = {16, 17, 18, 19, 20, 21, 22, 24, 25, 26, 27};
        for (int i = 0; i < count; i++) {
            int first = r.nextBoolean() ? 104 : (r.nextBoolean() ? 172 : 162);
            int second = (first == 104) ? subnets[r.nextInt(subnets.length)] : (first == 172 ? (64 + r.nextInt(8)) : 159);
            int third = r.nextInt(254) + 1;
            int fourth = r.nextInt(254) + 1;
            list.add(first + "." + second + "." + third + "." + fourth);
        }
        return list;
    }

    public static void scan(final int candidateCount, final ScanCallback callback) {
        final List<String> candidates = generateCandidateIps(candidateCount);
        final List<ScannedIp> results = Collections.synchronizedList(new ArrayList<ScannedIp>());
        final ExecutorService pool = Executors.newFixedThreadPool(16);
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final int total = candidates.size();
                for (int i = 0; i < total; i++) {
                    final String ip = candidates.get(i);
                    final int index = i;
                    pool.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (cancelled.get()) return;
                            int lat = probe(ip, 443, 2000);
                            ScannedIp sip = new ScannedIp(ip, lat, lat > 0 && lat < 1800);
                            if (sip.ok) {
                                results.add(sip);
                            }
                            if (callback != null) {
                                callback.onProgress(index + 1, total, sip);
                            }
                        }
                    });
                }
                pool.shutdown();
                try {
                    pool.awaitTermination(25, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                Collections.sort(results, new Comparator<ScannedIp>() {
                    @Override
                    public int compare(ScannedIp a, ScannedIp b) {
                        return Integer.compare(a.latencyMs, b.latencyMs);
                    }
                });
                if (callback != null) {
                    callback.onComplete(results);
                }
            }
        }, "clean-ip-scanner").start();
    }

    private static int probe(String ip, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return (int) (System.currentTimeMillis() - start);
        } catch (Throwable t) {
            return -1;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Applies the clean IP to all Cloudflare-fronted configs (WS/gRPC) while preserving SNI. */
    public static int applyCleanIpToCloudflareProfiles(Context context, String cleanIp) {
        if (cleanIp == null || cleanIp.isEmpty()) return 0;
        ProfileStore store = ProfileStore.f(context);
        ArrayList<Profile> all = store.e();
        int changed = 0;
        for (Profile p : all) {
            // If profile uses WS or gRPC or has SNI/Host, it's suitable for Clean IP
            if ("ws".equals(p.network) || "grpc".equals(p.network) || "httpupgrade".equals(p.network) || !p.sni.isEmpty() || !p.host.isEmpty()) {
                if (p.sni.isEmpty()) {
                    p.sni = p.address;
                }
                if (p.host.isEmpty()) {
                    p.host = p.sni;
                }
                p.address = cleanIp;
                changed++;
            }
        }
        store.h();
        return changed;
    }
}
