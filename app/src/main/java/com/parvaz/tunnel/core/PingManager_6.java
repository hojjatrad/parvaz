package com.parvaz.tunnel.core;

import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.R;

/* renamed from: R1.i */
/* loaded from: classes.dex */
public final class PingManager_6 implements Runnable {
    public final MainActivity.K b;

    public PingManager_6(MainActivity.K k) {
        this.b = k;
    }

    @Override // java.lang.Runnable
    public final void run() {
        MainActivity mainActivity = this.b.outer();
        mainActivity.refresh.setRefreshing(false);
        mainActivity.setPingAllBusy(false);
        Snackbar.make(mainActivity.connectButton, R.string.ping_done, -1).show();
    }
}
