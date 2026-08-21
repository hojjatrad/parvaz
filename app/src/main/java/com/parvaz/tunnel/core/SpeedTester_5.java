package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;

/* renamed from: R1.p */
/* loaded from: classes.dex */
public final class SpeedTester_5 implements Runnable {
    public final MainActivity.C0021c b;

    /* renamed from: c */
    public final String f6282c;

    public SpeedTester_5(MainActivity.C0021c c0021c, String str) {
        this.b = c0021c;
        this.f6282c = str;
    }

    @Override // java.lang.Runnable
    public final void run() {
        this.b.a(0.0d, this.f6282c);
    }
}
