package com.parvaz.tunnel.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Robust subscription updater for Marzban, X-UI, 3x-ui, Hiddify, V2board, and custom panels.
 */
public final class SubscriptionUpdater {

    private static final String TAG = "ParvazSub";

    public final Context f6298a;
    public final Handler f6299b = new Handler(Looper.getMainLooper());

    public interface a {
        void a(String str, int i);
    }

    public static class b {
        public final String f6300a;
        public final String f6301b;

        public b(String str, String str2) {
            this.f6300a = str == null ? "" : str;
            this.f6301b = str2;
        }
    }

    public SubscriptionUpdater(Context context) {
        this.f6298a = context.getApplicationContext();
    }

    /**
     * Fetches subscription and extracts server userinfo (total quota, usage, expire date).
     */
    public static b a(String str) throws java.io.IOException {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException("empty subscription url");
        }

        String currentUrl = str.trim();
        String userinfo = null;
        String responseBody = "";

        // Follow redirects up to 5 hops
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(false); // manual redirect to catch headers
            conn.setRequestProperty("User-Agent", "v2rayNG/1.8.5");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8");

            int code;
            try {
                code = conn.getResponseCode();
            } catch (Exception e) {
                conn.disconnect();
                throw e;
            }

            // Check headers for userinfo on each hop
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                String key = entry.getKey();
                if (key != null && (key.equalsIgnoreCase("subscription-userinfo")
                        || key.equalsIgnoreCase("x-userinfo")
                        || key.equalsIgnoreCase("user-info")
                        || key.equalsIgnoreCase("subscription-info"))) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        userinfo = entry.getValue().get(0);
                    }
                }
            }

            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && !location.isEmpty()) {
                    if (!location.startsWith("http://") && !location.startsWith("https://")) {
                        URL base = new URL(currentUrl);
                        currentUrl = new URL(base, location).toString();
                    } else {
                        currentUrl = location;
                    }
                    continue;
                }
            }

            if (code < 200 || code >= 400) {
                conn.disconnect();
                throw new IllegalStateException("HTTP " + code);
            }

            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int read;
            while ((read = in.read(buf)) > 0) {
                bos.write(buf, 0, read);
            }
            in.close();
            conn.disconnect();
            responseBody = bos.toString("UTF-8");
            break;
        }

        // If userinfo header was not found, check if response is JSON with Marzban / panel info
        if (userinfo == null && responseBody.trim().startsWith("{") && responseBody.trim().endsWith("}")) {
            try {
                JSONObject j = new JSONObject(responseBody.trim());
                long total = j.optLong("data_limit", j.optLong("total", -1L));
                long used = j.optLong("used_traffic", j.optLong("used", -1L));
                long exp = j.optLong("expire", -1L);
                if (total > 0 || exp > 0) {
                    userinfo = "upload=0; download=" + Math.max(0L, used) + "; total=" + total + "; expire=" + exp;
                }
            } catch (Throwable ignored) {
            }
        }

        return new b(responseBody, userinfo);
    }
}
