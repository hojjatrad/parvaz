package com.parvaz.tunnel.ui;

import android.view.View;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.MainActivity_3_1;
import com.parvaz.tunnel.core.PingManager_1;
import com.parvaz.tunnel.model.Profile;

/* renamed from: T1.f */
/* loaded from: classes.dex */
public final class ServerAdapter_4 implements View.OnClickListener {

    /* renamed from: a */
    public final Profile f363a;
    public final ServerAdapter b;

    public ServerAdapter_4(ServerAdapter serverAdapter, Profile profile) {
        this.b = serverAdapter;
        this.f363a = profile;
    }

    @Override // android.view.View.OnClickListener
    public final void onClick(View view) {
        MainActivity.C0030l c0030l = (MainActivity.C0030l) this.b.d;
        MainActivity mainActivity = this.b.d.outer();
        Profile profile = this.f363a;
        profile.ping = -3;
        mainActivity.z.i(profile.id);
        new Thread(new PingManager_1(mainActivity.K, profile, new MainActivity_3_1(c0030l))).start();
    }
}
