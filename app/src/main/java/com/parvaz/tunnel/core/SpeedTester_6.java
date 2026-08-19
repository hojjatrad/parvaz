package com.parvaz.tunnel.core;

import android.os.Handler;
import com.parvaz.tunnel.MainActivity;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import org.json.JSONObject;

/**
 * Queries speed.cloudflare.com/meta through the local SOCKS inbound so the reported
 * IP/country/city reflect the tunnel exit rather than the device's real connection.
 */
/* renamed from: R1.q */
/* loaded from: classes.dex */
public final class SpeedTester_6 implements Runnable {

    /* renamed from: b */
    public final Handler f6283b;
    public final MainActivity.C0022d c;

    public SpeedTester_6(Handler handler, MainActivity.C0022d c0022d) {
        this.f6283b = handler;
        this.c = c0022d;
    }

    @Override // java.lang.Runnable
    public final void run() {
        Handler handler = this.f6283b;
        MainActivity.C0022d c0022d = this.c;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://speed.cloudflare.com/meta")
                    .openConnection(new Proxy(Proxy.Type.SOCKS,
                            new InetSocketAddress("127.0.0.1", 10808)));
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "curl/8.0");

            StringBuilder sb = new StringBuilder();
            InputStream in = conn.getInputStream();
            try {
                byte[] buf = new byte[4096];
                while (true) {
                    int read = in.read(buf);
                    if (read <= 0) {
                        break;
                    }
                    sb.append(new String(buf, 0, read, "UTF-8"));
                }
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }

            JSONObject json = new JSONObject(sb.toString());
            handler.post(new SpeedTester_7(c0022d,
                    json.optString("clientIp", ""),
                    json.optString("country", ""),
                    json.optString("city", "")));
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isEmpty()) {
                message = e.getClass().getSimpleName();
            }
            handler.post(new SpeedTester_8(c0022d, message));
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
