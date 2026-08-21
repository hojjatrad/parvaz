package com.parvaz.tunnel;

import android.view.View;
import com.parvaz.tunnel.RulesActivity;

/* renamed from: com.parvaz.tunnel.f */
/* loaded from: classes.dex */
public final class RulesActivity_Adapter_1 implements View.OnClickListener {

    /* renamed from: a */
    public final int f6304a;

    /* renamed from: b */
    public final RulesActivity.d f6305b;

    public RulesActivity_Adapter_1(RulesActivity.d dVar, int i) {
        this.f6305b = dVar;
        this.f6304a = i;
    }

    @Override // android.view.View.OnClickListener
    public final void onClick(View view) {
        this.f6305b.outer().i(this.f6304a);
    }
}
