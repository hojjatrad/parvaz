package com.parvaz.tunnel;

import com.parvaz.tunnel.AppPickerActivity;
import java.text.Collator;
import java.util.Comparator;

/* renamed from: com.parvaz.tunnel.b */
/* loaded from: classes.dex */
public final class AppPickerActivity_4 implements Comparator<AppPickerActivity.d> {

    /* renamed from: b */
    public final Collator f6213b;

    /* renamed from: c */
    public final AppPickerActivity f6214c;

    public AppPickerActivity_4(AppPickerActivity appPickerActivity, Collator collator) {
        this.f6214c = appPickerActivity;
        this.f6213b = collator;
    }

    @Override // java.util.Comparator
    public final int compare(AppPickerActivity.d dVar, AppPickerActivity.d dVar2) {
        AppPickerActivity.d dVar3 = dVar;
        AppPickerActivity.d dVar4 = dVar2;
        AppPickerActivity appPickerActivity = this.f6214c;
        boolean contains = appPickerActivity.g.contains(dVar3.f6081c);
        if (contains != appPickerActivity.g.contains(dVar4.f6081c)) {
            if (!contains) {
                return 1;
            }
        } else {
            boolean z = dVar3.f6082d;
            if (z != dVar4.f6082d) {
                if (z) {
                    return 1;
                }
            } else {
                return this.f6213b.compare(dVar3.f6080b, dVar4.f6080b);
            }
        }
        return -1;
    }
}
