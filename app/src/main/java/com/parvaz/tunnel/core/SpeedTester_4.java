package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;

/* renamed from: R1.o */
/* loaded from: classes.dex */
public final class SpeedTester_4 implements Runnable {
    public final MainActivity.C0021c b;

    /* renamed from: c */
    public final double f6280c;

    public SpeedTester_4(MainActivity.C0021c c0021c, double d) {
        this.b = c0021c;
        this.f6280c = d;
    }

    @Override // java.lang.Runnable
    public final void run() {
        this.b.a(this.f6280c, null);
    }
}
