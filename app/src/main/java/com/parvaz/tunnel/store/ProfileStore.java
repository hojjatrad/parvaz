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
    private long revision;

    public static final class Snapshot {
        public final Subscription subscription;
        final long revision;
        Snapshot(Subscription subscription, long revision) { this.subscription=subscription;this.revision=revision; }
    }
    public static final class StaleRefresh extends IllegalStateException {
        public StaleRefresh() { super("Subscription changed while downloading"); }
    }


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
                    android.util.Log.w("Parvaz/ProfileStore", "Exception ignored", unused);
                }
            }
        } catch (Exception unused2) {
            android.util.Log.w("Parvaz/ProfileStore", "Exception ignored", unused2);
        }
        try {
            JSONArray jSONArray2 = new JSONArray(sharedPreferences.getString("subs", "[]"));
            for (int i2 = 0; i2 < jSONArray2.length(); i2++) {
                arrayList2.add(Subscription.fromJson(jSONArray2.getJSONObject(i2)));
            }
        } catch (Exception unused3) {
            android.util.Log.w("Parvaz/ProfileStore", "Exception ignored", unused3);
        }
        try {
            JSONObject jSONObject = new JSONObject(sharedPreferences.getString("pings", "{}"));
            Iterator<String> keys = jSONObject.keys();
            while (keys.hasNext()) {
                String next = keys.next();
                hashMap.put(next, Integer.valueOf(jSONObject.optInt(next, -1)));
            }
        } catch (Exception unused4) {
            android.util.Log.w("Parvaz/ProfileStore", "Exception ignored", unused4);
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

    public final synchronized int a(ArrayList arrayList, String owner) {
        java.util.HashSet<String> identities = new java.util.HashSet<>();
        for (Object o : f346b) {
            Profile p=(Profile)o;
            identities.add(p.subscriptionId + ":" + ProfileIdentity.fingerprint(p));
        }
        int added=0;
        for (Object o : arrayList) {
            if (!(o instanceof Profile)) continue;
            Profile p=ProfileIdentity.copy((Profile)o);
            if (owner != null) p.subscriptionId=owner;
            if (identities.add(p.subscriptionId + ":" + ProfileIdentity.fingerprint(p))) {
                f346b.add(p);added++;
            }
        }
        h();
        return added;
    }

    public final synchronized Profile b(Profile profile) {
        String identity=ProfileIdentity.fingerprint(profile);
        for (Object o : f346b) {
            Profile existing=(Profile)o;
            if (java.util.Objects.equals(existing.subscriptionId,profile.subscriptionId) &&
                    identity.equals(ProfileIdentity.fingerprint(existing))) return existing;
        }
        return null;
    }

    /** Capture detached metadata and a generation BEFORE starting a download. */
    public synchronized Snapshot beginRefresh(String id) {
        for(Object o:f347c) {
            Subscription sub=(Subscription)o;
            if(sub.id.equals(id) && sub.enabled) {
                try { return new Snapshot(Subscription.fromJson(sub.toJson()),revision); }
                catch(org.json.JSONException e) { throw new IllegalStateException("Subscription snapshot failed"); }
            }
        }
        return null;
    }

    /** One complete SharedPreferences edit, then publish the in-memory plan under the same lock.
     * apply() is asynchronous: this is not a synchronous durability guarantee or a Room migration.
     * If serialization/Editor.apply throws, the old in-memory lists have not been touched.
     */
    public synchronized SubscriptionReconciler.Plan replaceSubscription(Snapshot snapshot,
            com.parvaz.tunnel.config.ImportResult parsed, String userinfo, long now, String selectedId) {
        if(snapshot==null || snapshot.revision!=revision) throw new StaleRefresh();
        if(!parsed.safeToReplace()) throw new IllegalArgumentException("Partial or empty subscription replacement refused");
        int index=-1;
        for(int i=0;i<f347c.size();i++) {
            Subscription current=(Subscription)f347c.get(i);
            if(current.id.equals(snapshot.subscription.id) && current.enabled && current.url.equals(snapshot.subscription.url)) { index=i;break; }
        }
        if(index<0)throw new StaleRefresh();
        SubscriptionReconciler.Plan plan=SubscriptionReconciler.plan(new ArrayList<Profile>(f346b),parsed.profiles,snapshot.subscription.id,selectedId);
        try {
            Subscription updated=Subscription.fromJson(((Subscription)f347c.get(index)).toJson());
            updated.applyUserinfo(userinfo);updated.lastUpdate=now;updated.count=plan.count;
            ArrayList<Subscription> subscriptions=new ArrayList<>(f347c);subscriptions.set(index,updated);
            JSONArray profilesJson=new JSONArray(),subsJson=new JSONArray();JSONObject pings=new JSONObject();
            for(Profile p:plan.all){profilesJson.put(p.toJson());if(p.ping>0)pings.put(p.id,p.ping);}
            for(Subscription sub:subscriptions)subsJson.put(sub.toJson());
            f345a.edit().putString("profiles",profilesJson.toString()).putString("subs",subsJson.toString()).putString("pings",pings.toString()).apply();
            f346b.clear();f346b.addAll(plan.all);f347c.clear();f347c.addAll(subscriptions);revision++;
            return plan;
        } catch(org.json.JSONException e) { throw new IllegalStateException("Subscription serialization failed"); }
    }

    /* renamed from: c */
    public final synchronized ArrayList e() {
        return new ArrayList(this.f346b);
    }

    /* renamed from: d */
    public final synchronized ArrayList f() {
        ArrayList<Subscription> result=new ArrayList<>();
        try { for(Object o:f347c)result.add(Subscription.fromJson(((Subscription)o).toJson())); }
        catch(org.json.JSONException e){throw new IllegalStateException("Subscription snapshot failed");}
        return result;
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
        revision++;
        try {
            JSONArray jSONArray = new JSONArray();
            Iterator it = this.f346b.iterator();
            while (it.hasNext()) {
                Profile profile = (Profile) it.next();
                if (profile != null) {
                    try {
                        jSONArray.put(profile.toJson());
                    } catch (Exception unused) {
                        android.util.Log.w("Parvaz/ProfileStore", "Exception ignored", unused);
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
            android.util.Log.w("Parvaz/ProfileStore", "Exception ignored", unused2);
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
