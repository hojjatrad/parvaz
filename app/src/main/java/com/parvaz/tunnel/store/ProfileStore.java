package com.parvaz.tunnel.store;

import android.content.Context;
import android.content.SharedPreferences;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.model.Subscription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

/* renamed from: S1.c */
/* loaded from: classes.dex */
public final class ProfileStore {
    public static ProfileStore d;

    /* renamed from: a */
    public final SharedPreferences f345a;

    /* renamed from: b */
    public final ArrayList f346b;

    /* renamed from: c */
    public final ArrayList f347c;

    public ProfileStore(Context context) {
        int intValue;
        Profile fromJson;
        ArrayList arrayList = new ArrayList();
        this.f346b = arrayList;
        ArrayList arrayList2 = new ArrayList();
        this.f347c = arrayList2;
        HashMap hashMap = new HashMap();
        SharedPreferences sharedPreferences = context.getApplicationContext().getSharedPreferences("parvaz_store", 0);
        this.f345a = sharedPreferences;
        arrayList.clear();
        arrayList2.clear();
        hashMap.clear();
        try {
            JSONArray jSONArray = new JSONArray(sharedPreferences.getString("profiles", "[]"));
            for (int i = 0; i < jSONArray.length(); i++) {
                try {
                    JSONObject optJSONObject = jSONArray.optJSONObject(i);
                    if (optJSONObject != null && (fromJson = Profile.fromJson(optJSONObject)) != null) {
                        arrayList.add(fromJson.normalize());
                    }
                } catch (Exception unused) {
                }
            }
        } catch (Exception unused2) {
        }
        try {
            JSONArray jSONArray2 = new JSONArray(sharedPreferences.getString("subs", "[]"));
            for (int i2 = 0; i2 < jSONArray2.length(); i2++) {
                arrayList2.add(Subscription.fromJson(jSONArray2.getJSONObject(i2)));
            }
        } catch (Exception unused3) {
        }
        try {
            JSONObject jSONObject = new JSONObject(sharedPreferences.getString("pings", "{}"));
            Iterator<String> keys = jSONObject.keys();
            while (keys.hasNext()) {
                String next = keys.next();
                hashMap.put(next, Integer.valueOf(jSONObject.optInt(next, -1)));
            }
        } catch (Exception unused4) {
        }
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Profile profile = (Profile) it.next();
            Integer num = (Integer) hashMap.get(profile.id);
            if (num == null) {
                intValue = -1;
            } else {
                intValue = num.intValue();
            }
            profile.ping = intValue;
        }
    }

    public static synchronized ProfileStore f(Context context) {
        if (d == null) {
            d = new ProfileStore(context);
        }
        return d;
    }

    public final synchronized int a(ArrayList arrayList, String str) {
        int i;
        String str2;
        try {
            Iterator it = arrayList.iterator();
            i = 0;
            while (it.hasNext()) {
                Profile profile = (Profile) it.next();
                if (profile != null) {
                    profile.normalize();
                    if (b(profile) == null) {
                        if (str == null) {
                            str2 = "";
                        } else {
                            str2 = str;
                        }
                        profile.subscriptionId = str2;
                        this.f346b.add(profile);
                        i++;
                    }
                }
            }
            h();
        } finally {
        }
        return i;
    }

    public final Profile b(Profile profile) {
        Iterator it = this.f346b.iterator();
        while (it.hasNext()) {
            Profile profile2 = (Profile) it.next();
            if (profile2 != null) {
                profile2.normalize();
                if (profile2.protocol.equals(profile.protocol) && profile2.address.equals(profile.address) && profile2.port == profile.port && profile2.uuid.equals(profile.uuid) && profile2.path.equals(profile.path) && profile2.network.equals(profile.network)) {
                    return profile2;
                }
            }
        }
        return null;
    }

    /* renamed from: c */
    public final synchronized ArrayList e() {
        return new ArrayList(this.f346b);
    }

    /* renamed from: d */
    public final synchronized ArrayList f() {
        return new ArrayList(this.f347c);
    }

    /* renamed from: e */
    public final synchronized void g(String str) {
        try {
            for (int size = this.f346b.size() - 1; size >= 0; size--) {
                if (str.equals(((Profile) this.f346b.get(size)).subscriptionId)) {
                    this.f346b.remove(size);
                }
            }
            h();
        } finally {
        }
    }

    /* renamed from: g */
    public final synchronized Profile getById(String str) {
        String str2;
        Iterator it = this.f346b.iterator();
        while (it.hasNext()) {
            Profile profile = (Profile) it.next();
            if (profile != null && (str2 = profile.id) != null && str2.equals(str)) {
                return profile;
            }
        }
        return null;
    }

    public final synchronized void h() {
        try {
            JSONArray jSONArray = new JSONArray();
            Iterator it = this.f346b.iterator();
            while (it.hasNext()) {
                Profile profile = (Profile) it.next();
                if (profile != null) {
                    try {
                        jSONArray.put(profile.toJson());
                    } catch (Exception unused) {
                    }
                }
            }
            JSONArray jSONArray2 = new JSONArray();
            Iterator it2 = this.f347c.iterator();
            while (it2.hasNext()) {
                jSONArray2.put(((Subscription) it2.next()).toJson());
            }
            JSONObject jSONObject = new JSONObject();
            Iterator it3 = this.f346b.iterator();
            while (it3.hasNext()) {
                Profile profile2 = (Profile) it3.next();
                int i = profile2.ping;
                if (i > 0) {
                    jSONObject.put(profile2.id, i);
                }
            }
            this.f345a.edit().putString("profiles", jSONArray.toString()).putString("subs", jSONArray2.toString()).putString("pings", jSONObject.toString()).apply();
        } catch (Exception unused2) {
        }
    }

    public final synchronized void i(String str, int i) {
        Profile byId = getById(str);
        if (byId != null) {
            byId.ping = i;
        }
    }

    public final synchronized void j(Subscription subscription) {
        int i = 0;
        while (true) {
            if (i >= this.f347c.size()) {
                break;
            }
            if (((Subscription) this.f347c.get(i)).id.equals(subscription.id)) {
                this.f347c.set(i, subscription);
                break;
            }
            i++;
        }
        h();
    }
}
