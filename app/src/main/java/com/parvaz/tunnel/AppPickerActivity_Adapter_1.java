package com.parvaz.tunnel;

import android.widget.CompoundButton;
import com.parvaz.tunnel.AppPickerActivity;
import com.parvaz.tunnel.R;

/* renamed from: com.parvaz.tunnel.c */
/* loaded from: classes.dex */
public final class AppPickerActivity_Adapter_1 implements CompoundButton.OnCheckedChangeListener {

    /* renamed from: a */
    public final AppPickerActivity.d f6215a;

    /* renamed from: b */
    public final AppPickerActivity.c f6216b;

    public AppPickerActivity_Adapter_1(AppPickerActivity.c cVar, AppPickerActivity.d dVar) {
        this.f6216b = cVar;
        this.f6215a = dVar;
    }

    @Override // android.widget.CompoundButton.OnCheckedChangeListener
    public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
        AppPickerActivity appPickerActivity = this.f6216b.outer();
        AppPickerActivity.d dVar = this.f6215a;
        if (z) {
            appPickerActivity.g.add(dVar.f6081c);
        } else {
            appPickerActivity.g.remove(dVar.f6081c);
        }
        appPickerActivity.f6069b.setText(appPickerActivity.getString(R.string.split_selected, Integer.valueOf(appPickerActivity.g.size())));
    }
}
