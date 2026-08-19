package com.parvaz.tunnel.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import java.util.concurrent.ExecutorService;
import libv2ray.Libv2ray;

/* renamed from: R1.d */
/* loaded from: classes.dex */
public final class PingManager {

    /* renamed from: a */
    public final Context f6270a;

    /* renamed from: b */
    public ExecutorService f6271b;

    /* renamed from: c */
    public final Handler f6272c = new Handler(Looper.getMainLooper());

    /* renamed from: d */
    public volatile boolean f6273d = false;

    public PingManager(Context context) {
        this.f6270a = context.getApplicationContext();
    }

    public final int a(Profile profile) {
        long j;
        Context context = this.f6270a;
        try {
            j = Libv2ray.measureOutboundDelay(XrayConfigBuilder.b(profile, new Prefs(context), null, false, false), context.getApplicationContext().getSharedPreferences("parvaz_prefs", 0).getString("ping_url", "https://www.gstatic.com/generate_204"));
        } catch (Exception unused) {
            j = -1;
        }
        if (j <= 0) {
            return -1;
        }
        return (int) j;
    }
}
