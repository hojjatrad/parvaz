package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;

/* renamed from: R1.n */
/* loaded from: classes.dex */
public final class SpeedTester_3 implements Runnable {
    public final MainActivity.C0021c b;

    public SpeedTester_3(MainActivity.C0021c c0021c) {
        this.b = c0021c;
    }

    @Override // java.lang.Runnable
    public final void run() {
        this.b.a(0.0d, "no data");
    }
}
