package com.parvaz.tunnel;

import android.view.View;
import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.RulesActivity;
import com.parvaz.tunnel.R;
import org.json.JSONArray;

/* renamed from: com.parvaz.tunnel.g */
/* loaded from: classes.dex */
public final class RulesActivity_Adapter_2 implements View.OnClickListener {

    /* renamed from: a */
    public final int f6306a;

    /* renamed from: b */
    public final RulesActivity.d f6307b;

    public RulesActivity_Adapter_2(RulesActivity.d dVar, int i) {
        this.f6307b = dVar;
        this.f6306a = i;
    }

    @Override // android.view.View.OnClickListener
    public final void onClick(View view) {
        RulesActivity rulesActivity = this.f6307b.outer();
        rulesActivity.getClass();
        JSONArray jSONArray = new JSONArray();
        for (int i = 0; i < rulesActivity.f6144d.length(); i++) {
            if (i != this.f6306a) {
                jSONArray.put(rulesActivity.f6144d.opt(i));
            }
        }
        rulesActivity.f6144d = jSONArray;
        rulesActivity.h();
        Snackbar.make(rulesActivity.findViewById(R.id.add_rule), R.string.rule_deleted, -1).show();
    }
}
