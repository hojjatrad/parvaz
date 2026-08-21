package com.parvaz.tunnel.ui;

import android.view.View;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.R;

/* renamed from: T1.e */
/* loaded from: classes.dex */
public final class ServerAdapter_3 implements View.OnLongClickListener {

    /* renamed from: a */
    public final Profile f361a;
    public final ServerAdapter b;

    public ServerAdapter_3(ServerAdapter serverAdapter, Profile profile) {
        this.b = serverAdapter;
        this.f361a = profile;
    }

    @Override // android.view.View.OnLongClickListener
    public final boolean onLongClick(View view) {
        MainActivity mainActivity = this.b.d.outer();
        String[] strArr = {mainActivity.getString(R.string.share_link), mainActivity.getString(R.string.show_qr), mainActivity.getString(R.string.delete), mainActivity.getString(R.string.delete_all)};
        MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(mainActivity);
        Profile profile = this.f361a;
        materialAlertDialogBuilder.setTitle(profile.remark);
        materialAlertDialogBuilder.setItems(strArr,
                mainActivity.new DialogInterfaceOnClickListenerC0028j(profile));
        materialAlertDialogBuilder.show();
        return true;
    }
}
