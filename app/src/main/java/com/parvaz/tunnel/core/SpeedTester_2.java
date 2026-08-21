package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;
import java.util.Locale;

/* renamed from: R1.m */
/* loaded from: classes.dex */
public final class SpeedTester_2 implements Runnable {

    /* renamed from: b */
    public final double f6277c;
    public final MainActivity.C0021c c;

    public SpeedTester_2(MainActivity.C0021c c0021c, double d) {
        this.c = c0021c;
        this.f6277c = d;
    }

    @Override // java.lang.Runnable
    public final void run() {
        this.c.outer().speedTestButton.setText(String.format(Locale.US, "%.1f Mbps", Double.valueOf(this.f6277c)));
    }
}
