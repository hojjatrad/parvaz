package com.parvaz.tunnel;

import android.content.SharedPreferences;

/* renamed from: P1.a */
/* loaded from: classes.dex */
public final class RulesActivity__ExternalSyntheticOutline0 {
    /* renamed from: a */
    public static void j(SharedPreferences sharedPreferences, String str, String str2) {
        sharedPreferences.edit().putString(str, str2).apply();
    }

    /* renamed from: b */
    public static void k(SharedPreferences sharedPreferences, String str, boolean z) {
        sharedPreferences.edit().putBoolean(str, z).apply();
    }
}
