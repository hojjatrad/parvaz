package com.parvaz.tunnel.core;

import android.os.Handler;
import com.parvaz.tunnel.MainActivity;

/**
 * Runs the public-IP lookup off the UI thread and posts the outcome back.
 *
 * <p>The actual network work now lives in {@link IpLookup}, which tries several
 * independent providers instead of the single Cloudflare endpoint that used to
 * make this report "IP not detected" whenever that one host was blocked.
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
        IpLookup.Info info = IpLookup.throughTunnel();
        if (info.ok()) {
            this.f6283b.post(new SpeedTester_7(this.c, info.ip, info.country, info.city));
        } else {
            this.f6283b.post(new SpeedTester_8(this.c, info.error));
        }
    }
}
