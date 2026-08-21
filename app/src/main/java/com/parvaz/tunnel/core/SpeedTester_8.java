package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;

/* renamed from: R1.s */
/* loaded from: classes.dex */
public final class SpeedTester_8 implements Runnable {
    public final MainActivity.C0022d b;

    /* renamed from: c */
    public final String f6290c;

    public SpeedTester_8(MainActivity.C0022d c0022d, String str) {
        this.b = c0022d;
        this.f6290c = str;
    }

    @Override // java.lang.Runnable
    public final void run() {
        this.b.outer().E(null, null, this.f6290c);
    }
}
