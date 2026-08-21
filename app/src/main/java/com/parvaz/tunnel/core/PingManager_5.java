package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.model.Profile;

/* renamed from: R1.h */
/* loaded from: classes.dex */
public final class PingManager_5 implements Runnable {
    public final MainActivity.K b;

    /* renamed from: c */
    public final Profile f6268c;

    public PingManager_5(MainActivity.K k, Profile profile) {
        this.b = k;
        this.f6268c = profile;
    }

    @Override // java.lang.Runnable
    public final void run() {
        this.b.outer().z.i(this.f6268c.id);
    }
}
