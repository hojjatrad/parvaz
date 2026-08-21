package com.parvaz.tunnel.core;

import com.parvaz.tunnel.core.SubscriptionUpdater;

/* renamed from: com.parvaz.tunnel.core.d */
/* loaded from: classes.dex */
public final class SubscriptionUpdater_5 implements Runnable {
    public final SubscriptionUpdater.a b;

    /* renamed from: c */
    public final int[] f6296c;

    /* renamed from: d */
    public final String[] f6297d;

    public SubscriptionUpdater_5(SubscriptionUpdater.a aVar, int[] iArr, String[] strArr) {
        this.b = aVar;
        this.f6296c = iArr;
        this.f6297d = strArr;
    }

    @Override // java.lang.Runnable
    public final void run() {
        int i = this.f6296c[0];
        String str = this.f6297d[0];
        if (i > 0) {
            str = null;
        }
        this.b.a(str, i);
    }
}
