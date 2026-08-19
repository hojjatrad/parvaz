package com.parvaz.tunnel.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* renamed from: com.parvaz.tunnel.core.b */
/* loaded from: classes.dex */
public final class SubscriptionUpdater {

    /* renamed from: a */
    public final Context f6298a;

    /* renamed from: b */
    public final Handler f6299b = new Handler(Looper.getMainLooper());

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.b.a to com.parvaz.tunnel.core.SubscriptionUpdater$Listener */
    /* renamed from: com.parvaz.tunnel.core.b$a */
    /* loaded from: classes.dex */
    public interface a {
        void a(String str, int i);
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.b.b to com.parvaz.tunnel.core.SubscriptionUpdater$b */
    /* renamed from: com.parvaz.tunnel.core.b$b */
    /* loaded from: classes.dex */
    public static class b {

        /* renamed from: a */
        public final String f6300a;

        /* renamed from: b */
        public final String f6301b;

        public b(String str, String str2) {
            this.f6300a = str;
            this.f6301b = str2;
        }
    }

    public SubscriptionUpdater(Context context) {
        this.f6298a = context.getApplicationContext();
    }

    public static b a(String str) throws java.io.IOException {
        String str2;
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(15000);
        httpURLConnection.setReadTimeout(20000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty("User-Agent", "v2rayNG/2.2.6");
        httpURLConnection.setRequestProperty("Accept", "*/*");
        try {
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode < 200 || responseCode >= 400) {
                throw new IllegalStateException("HTTP " + responseCode);
            }
            Iterator<Map.Entry<String, List<String>>> it = httpURLConnection.getHeaderFields().entrySet().iterator();
            while (true) {
                if (!it.hasNext()) {
                    str2 = null;
                    break;
                }
                Map.Entry<String, List<String>> next = it.next();
                String key = next.getKey();
                if (key != null && key.equalsIgnoreCase("subscription-userinfo") && next.getValue() != null && !next.getValue().isEmpty()) {
                    str2 = next.getValue().get(0);
                    break;
                }
            }
            InputStream inputStream = httpURLConnection.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[8192];
            while (true) {
                int read = inputStream.read(bArr);
                if (read <= 0) {
                    b bVar = new b(byteArrayOutputStream.toString("UTF-8"), str2);
                    inputStream.close();
                    return bVar;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
        } finally {
            httpURLConnection.disconnect();
        }
    }
}
