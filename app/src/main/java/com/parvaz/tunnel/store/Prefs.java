package com.parvaz.tunnel.store;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/* renamed from: S1.b */
/* loaded from: classes.dex */
public final class Prefs {

    /* renamed from: a */
    public final SharedPreferences f343a;

    public Prefs(Context context) {
        this.f343a = context.getApplicationContext().getSharedPreferences("parvaz_prefs", 0);
    }

    /* renamed from: a */
    public final void addDailyUsage(long j, long j2) {
        long j3;
        long j4;
        SharedPreferences sharedPreferences = this.f343a;
        if (j > 0 || j2 > 0) {
            try {
                JSONObject jSONObject = new JSONObject(sharedPreferences.getString("daily_usage", "{}"));
                String format = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                JSONArray optJSONArray = jSONObject.optJSONArray(format);
                JSONArray jSONArray = new JSONArray();
                if (optJSONArray != null) {
                    j3 = optJSONArray.optLong(0);
                } else {
                    j3 = 0;
                }
                JSONArray put = jSONArray.put(j3 + Math.max(0L, j));
                if (optJSONArray != null) {
                    j4 = optJSONArray.optLong(1);
                } else {
                    j4 = 0;
                }
                jSONObject.put(format, put.put(j4 + Math.max(0L, j2)));
                if (jSONObject.length() > 30) {
                    ArrayList arrayList = new ArrayList();
                    Iterator<String> keys = jSONObject.keys();
                    while (keys.hasNext()) {
                        arrayList.add(keys.next());
                    }
                    Collections.sort(arrayList);
                    for (int i = 0; i < arrayList.size() - 30; i++) {
                        jSONObject.remove((String) arrayList.get(i));
                    }
                }
                sharedPreferences.edit().putString("daily_usage", jSONObject.toString()).apply();
            } catch (Exception unused) {
            }
        }
    }

    /* renamed from: b */
    public final LinkedHashSet c() {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        for (String str : this.f343a.getString("per_app_list", "").split("\n")) {
            if (!str.trim().isEmpty()) {
                linkedHashSet.add(str.trim());
            }
        }
        return linkedHashSet;
    }

    /* renamed from: c */
    public final void d() {
        this.f343a.edit().putLong("data_up", 0L).putLong("data_down", 0L).putLong("data_since", System.currentTimeMillis()).apply();
    }

    /* renamed from: d */
    public final LinkedHashSet getFavorites() {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        for (String str : this.f343a.getString("favorites", "").split("\n")) {
            if (!str.trim().isEmpty()) {
                linkedHashSet.add(str.trim());
            }
        }
        return linkedHashSet;
    }
}
