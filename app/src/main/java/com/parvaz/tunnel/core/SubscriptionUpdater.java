package com.parvaz.tunnel.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

/**
 * Subscription updater for compatible panel outputs; importing does not guarantee connectivity.
 */
public final class SubscriptionUpdater {

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

    /** Fetch with system TLS validation, bounded bodies and safe redirects. */
    public static b a(String str) throws java.io.IOException {
        SubscriptionHttpClient.Response response = SubscriptionHttpClient.fetch(str);
        String responseBody = response.body;
        String userinfo = response.userinfo;

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
