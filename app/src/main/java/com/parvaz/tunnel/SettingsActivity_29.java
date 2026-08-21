package com.parvaz.tunnel;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.AdapterView;
import com.parvaz.tunnel.SettingsActivity;
import com.parvaz.tunnel.store.Prefs;
import java.util.ArrayList;

/* renamed from: P1.c */
/* loaded from: classes.dex */
public final class SettingsActivity_29 implements AdapterView.OnItemSelectedListener {

    /* renamed from: a */
    public boolean f233a = true;

    /* renamed from: b */
    public final ArrayList f234b;

    /* renamed from: c */
    public final SettingsActivity f235c;

    public SettingsActivity_29(SettingsActivity settingsActivity, ArrayList arrayList) {
        this.f235c = settingsActivity;
        this.f234b = arrayList;
    }

    @Override // android.widget.AdapterView.OnItemSelectedListener
    public final void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
        if (this.f233a) {
            this.f233a = false;
            return;
        }
        Prefs prefs = this.f235c.C;
        String str = (String) this.f234b.get(i);
        SharedPreferences.Editor edit = prefs.f343a.edit();
        if (str == null) {
            str = "";
        }
        edit.putString("chain_profile", str).apply();
    }

    @Override // android.widget.AdapterView.OnItemSelectedListener
    public final void onNothingSelected(AdapterView<?> adapterView) {
    }
}
