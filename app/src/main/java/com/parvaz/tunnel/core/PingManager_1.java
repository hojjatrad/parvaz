package com.parvaz.tunnel.core;

import android.content.Context;
import com.parvaz.tunnel.MainActivity_3_1;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileStore;

/* renamed from: R1.e */
/* loaded from: classes.dex */
public final class PingManager_1 implements Runnable {

    /* renamed from: b */
    public final Profile f6257b;
    public final MainActivity_3_1 c;
    public final PingManager d;

    public PingManager_1(PingManager pingManager, Profile profile, MainActivity_3_1 mainActivity_3_1) {
        this.d = pingManager;
        this.f6257b = profile;
        this.c = mainActivity_3_1;
    }

    @Override // java.lang.Runnable
    public final void run() {
        PingManager pingManager = this.d;
        final Profile profile = this.f6257b;
        int a = pingManager.a(profile);
        Context context = pingManager.f6270a;
        ProfileStore.f(context).i(profile.id, a);
        ProfileStore.f(context).h();
        final MainActivity_3_1 callback = this.c;
        pingManager.f6272c.post(new Runnable() {
            @Override
            public void run() {
                callback.a(profile);
            }
        });
    }
}
