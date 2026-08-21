package com.parvaz.tunnel.ui;

import android.view.View;
import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.R;
import java.util.Iterator;
import java.util.LinkedHashSet;

/* renamed from: T1.c */
/* loaded from: classes.dex */
public final class ServerAdapter_1 implements View.OnClickListener {

    /* renamed from: a */
    public final Profile f357a;
    public final ServerAdapter b;

    public ServerAdapter_1(ServerAdapter serverAdapter, Profile profile) {
        this.b = serverAdapter;
        this.f357a = profile;
    }

    @Override // android.view.View.OnClickListener
    public final void onClick(View view) {
        boolean z;
        int i;
        MainActivity mainActivity = this.b.d.outer();
        Prefs prefs = mainActivity.L;
        String str = this.f357a.id;
        LinkedHashSet favorites = prefs.getFavorites();
        if (favorites.remove(str)) {
            z = false;
        } else {
            favorites.add(str);
            z = true;
        }
        StringBuilder sb = new StringBuilder();
        Iterator it = favorites.iterator();
        while (it.hasNext()) {
            String str2 = (String) it.next();
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(str2);
        }
        prefs.f343a.edit().putString("favorites", sb.toString()).apply();
        mainActivity.haptic(mainActivity.list);
        mainActivity.reload();
        if (z) {
            i = R.string.added_favorite;
        } else {
            i = R.string.removed_favorite;
        }
        Snackbar.make(mainActivity.connectButton, i, -1).show();
    }
}
