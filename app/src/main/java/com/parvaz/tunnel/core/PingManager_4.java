package com.parvaz.tunnel.core;

import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileStore;
import java.util.concurrent.atomic.AtomicInteger;

/* renamed from: R1.g */
/* loaded from: classes.dex */
public final class PingManager_4 implements Runnable {

    /* renamed from: b */
    public final AtomicInteger f6263b;
    public final MainActivity.K c;

    /* renamed from: d */
    public final Profile f6265d;
    public final PingManager e;

    public PingManager_4(PingManager pingManager, AtomicInteger atomicInteger, MainActivity.K k, Profile profile) {
        this.e = pingManager;
        this.f6263b = atomicInteger;
        this.c = k;
        this.f6265d = profile;
    }

    @Override // java.lang.Runnable
    public final void run() {
        PingManager pingManager = this.e;
        AtomicInteger atomicInteger = this.f6263b;
        MainActivity.K k = this.c;
        Profile profile = this.f6265d;
        if (pingManager.f6273d) {
            if (atomicInteger.decrementAndGet() <= 0) {
                ProfileStore.f(pingManager.f6270a).h();
                pingManager.f6272c.post(new PingManager_6(k));
                return;
            }
            return;
        }
        ProfileStore.f(pingManager.f6270a).i(profile.id, pingManager.a(profile));
        pingManager.f6272c.post(new PingManager_5(k, profile));
        if (atomicInteger.decrementAndGet() <= 0) {
            ProfileStore.f(pingManager.f6270a).h();
            pingManager.f6272c.post(new PingManager_6(k));
        }
    }
}
