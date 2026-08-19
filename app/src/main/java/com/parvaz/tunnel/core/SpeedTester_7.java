package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;

/* renamed from: R1.r */
/* loaded from: classes.dex */
public final class SpeedTester_7 implements Runnable {
    public final MainActivity.C0022d b;

    /* renamed from: c */
    public final String f6286c;

    /* renamed from: d */
    public final String f6287d;

    public SpeedTester_7(MainActivity.C0022d c0022d, String str, String str2, String str3) {
        this.b = c0022d;
        this.f6286c = str;
        this.f6287d = str2;
    }

    @Override // java.lang.Runnable
    public final void run() {
        this.b.outer().E(this.f6286c, this.f6287d, null);
    }
}
