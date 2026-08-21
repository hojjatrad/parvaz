package com.parvaz.tunnel.ui;

import android.view.View;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.RulesActivity__ExternalSyntheticOutline0;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;

/* renamed from: T1.d */
/* loaded from: classes.dex */
public final class ServerAdapter_2 implements View.OnClickListener {

    /* renamed from: a */
    public final Profile f359a;
    public final ServerAdapter b;

    public ServerAdapter_2(ServerAdapter serverAdapter, Profile profile) {
        this.b = serverAdapter;
        this.f359a = profile;
    }

    @Override // android.view.View.OnClickListener
    public final void onClick(View view) {
        MainActivity mainActivity = this.b.d.outer();
        Prefs prefs = mainActivity.L;
        Profile profile = this.f359a;
        RulesActivity__ExternalSyntheticOutline0.j(prefs.f343a, "selected_profile", profile.id);
        ServerAdapter serverAdapter = mainActivity.z;
        String str = profile.id;
        serverAdapter.getClass();
        if (str == null) {
            str = "";
        }
        serverAdapter.f368h = str;
        serverAdapter.notifyDataSetChanged();
        mainActivity.renderState();
        if (mainActivity.state == 2) {
            mainActivity.startVpn("com.parvaz.tunnel.RESTART");
        }
    }
}
