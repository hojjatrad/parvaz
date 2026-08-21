package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/**
 * Download speed test: pulls up to 10 MB from speed.cloudflare.com through the local
 * SOCKS inbound, posting an interim Mbps figure every 250 ms and stopping after 10 s
 * or when the user cancels. Posts the final average, or an error, on the main handler.
 */
/* renamed from: R1.l */
/* loaded from: classes.dex */
public final class SpeedTester_1 implements Runnable {
    public final MainActivity.C0021c b;
    public final SpeedTester c;

    private static final long TEST_MILLIS = 10000;
    private static final long SAMPLE_MILLIS = 250;

    public SpeedTester_1(SpeedTester speedTester, MainActivity.C0021c c0021c) {
        this.c = speedTester;
        this.b = c0021c;
    }

    @Override // java.lang.Runnable
    public final void run() {
        SpeedTester speedTester = this.c;
        MainActivity.C0021c c0021c = this.b;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=10000000")
                    .openConnection(new Proxy(Proxy.Type.SOCKS,
                            new InetSocketAddress("127.0.0.1", 10808)));
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setUseCaches(false);
            conn.setRequestProperty("Cache-Control", "no-cache");

            long startNanos = System.nanoTime();
            byte[] buf = new byte[32768];
            long total = 0;
            long lastSample = 0;

            InputStream in = conn.getInputStream();
            try {
                while (true) {
                    int read = in.read(buf);
                    if (read <= 0 || speedTester.f6291a) {
                        break;
                    }
                    total += read;
                    long elapsed = (System.nanoTime() - startNanos) / 1000000L;
                    if (elapsed >= TEST_MILLIS) {
                        break;
                    }
                    if (elapsed - lastSample >= SAMPLE_MILLIS) {
                        double mbps = elapsed > 0
                                ? ((total * 8.0d) / (elapsed / 1000.0d)) / 1000000.0d
                                : 0.0d;
                        speedTester.f6292b.post(new SpeedTester_2(c0021c, mbps));
                        lastSample = elapsed;
                    }
                }
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                    android.util.Log.w("Parvaz/SpeedTester_1", "Exception ignored", ignored);
                }
            }

            long elapsedMs = Math.max(1L, (System.nanoTime() - startNanos) / 1000000L);
            double avgMbps = ((total * 8.0d) / (elapsedMs / 1000.0d)) / 1000000.0d;
            if (total <= 0) {
                speedTester.f6292b.post(new SpeedTester_3(c0021c));
            } else {
                speedTester.f6292b.post(new SpeedTester_4(c0021c, avgMbps));
            }
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isEmpty()) {
                message = e.getClass().getSimpleName();
            }
            speedTester.f6292b.post(new SpeedTester_5(c0021c, message));
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                    android.util.Log.w("Parvaz/SpeedTester_1", "Exception ignored", ignored);
                }
            }
        }
    }
}
