package com.parvaz.tunnel.store;

import android.content.Context;
import android.content.SharedPreferences;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.model.Subscription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* renamed from: S1.c */
/* loaded from: classes.dex */
public final class ProfileStore {
    public static ProfileStore d;
    private long revision;
    private final StoreCipher recordCipher;
    private final Context appContext;
    private final RestoreJournal restoreJournal;
    private boolean restoring;
    public static final class RestoreUnavailable extends IllegalStateException {
        RestoreUnavailable(Exception cause){super("Backup restore did not complete; any existing encrypted journal was retained. Retry after storage becomes available.",cause);}
    }
    public static void recoverBeforeUse(Context context){if(RestoreJournal.hasState(context))f(context);}
    public synchronized void recoverPendingRestore(){
        if(restoring||!restoreJournal.hasState())return;
        revision++;restoring=true;
        try{restoreJournal.recover(canonical->BackupManager.replay(appContext,this,canonical));}
        catch(Exception error){throw new RestoreUnavailable(error);}
        finally{restoring=false;}
    }
    synchronized void restoreBackup(String canonical){
        recoverPendingRestore();revision++;restoring=true;
        try{restoreJournal.execute(canonical,plan->BackupManager.replay(appContext,this,plan));}
        catch(Exception error){throw new RestoreUnavailable(error);}
        finally{restoring=false;}
    }
    private void commit(android.content.SharedPreferences.Editor editor){if(!editor.commit())throw new IllegalStateException("Profile store commit failed");}

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
        this.appContext=context.getApplicationContext();
        this.recordCipher=StoreCipher.open(context,sharedPreferences,RestoreJournal.hasState(context));
        this.restoreJournal=new RestoreJournal(RestoreJournal.path(appContext),new RestoreJournal.Codec(){
            public String seal(String plain){return recordCipher.encode("backup_restore_journal",plain);}
            public String open(String encrypted){return recordCipher.decodeRequired("backup_restore_journal",encrypted);}
        });
        this.f345a = sharedPreferences;
        arrayList.clear();
        arrayList2.clear();
        hashMap.clear();
        try {
            JSONArray jSONArray = new JSONArray(recordCipher.decode("profiles",sharedPreferences.getString("profiles", "[]")));
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
            JSONArray jSONArray2 = new JSONArray(recordCipher.decode("subs",sharedPreferences.getString("subs", "[]")));
            for (int i2 = 0; i2 < jSONArray2.length(); i2++) {
                arrayList2.add(Subscription.fromJson(jSONArray2.getJSONObject(i2)));
            }
        } catch (Exception unused3) {
            android.util.Log.w("Parvaz/ProfileStore", "Exception ignored", unused3);
        }
        try {
            JSONObject jSONObject = new JSONObject(sharedPreferences.getString("pings_https_rtt_v1", "{}"));
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
            profile.ping = com.parvaz.tunnel.core.LatencyResult.restored(intValue);
        }
    }

    public static ProfileStore f(Context context) {
        ProfileStore store;
        synchronized(ProfileStore.class){if(d==null)d=new ProfileStore(context);store=d;}
        // Never hold the class monitor while waiting for a live store's monitor.
        store.recoverPendingRestore();return store;
    }

    public final synchronized int a(ArrayList arrayList, String owner) {
        recoverPendingRestore();
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
        recoverPendingRestore();
        String identity=ProfileIdentity.fingerprint(profile);
        for (Object o : f346b) {
            Profile existing=(Profile)o;
            if (java.util.Objects.equals(existing.subscriptionId,profile.subscriptionId) &&
                    identity.equals(ProfileIdentity.fingerprint(existing))) return existing;
        }
        return null;
    }

    public synchronized Subscription addOrGetSubscription(String input) {
        recoverPendingRestore();
        String url=com.parvaz.tunnel.config.SubscriptionUrl.normalize(input);
        for(Object o:f347c) {
            Subscription sub=(Subscription)o;
            try {if(url.equals(com.parvaz.tunnel.config.SubscriptionUrl.normalize(sub.url))) {
                if(!sub.enabled){sub.enabled=true;h();}
                return Subscription.fromJson(sub.toJson());
            }} catch(org.json.JSONException e){throw new IllegalStateException("Subscription copy failed");}
              catch(IllegalArgumentException ignored){}
        }
        Subscription sub=new Subscription();sub.id=java.util.UUID.randomUUID().toString();sub.url=url;
        try{sub.name=new java.net.URI(url).getHost();}catch(Exception ignored){sub.name="Subscription";}
        f347c.add(sub);h();
        try{return Subscription.fromJson(sub.toJson());}catch(org.json.JSONException e){throw new IllegalStateException("Subscription copy failed");}
    }

    /** Validate first; commit all restored records together, never a delete-first restore. */
    public synchronized void restoreRecords(java.util.List<Profile> profiles,java.util.List<Subscription> subscriptions,String primary)throws JSONException {
        recoverPendingRestore();
        JSONArray pj=new JSONArray(),sj=new JSONArray();JSONObject pings=new JSONObject();boolean found=primary.isEmpty()||MANUAL_GROUP.equals(primary);
        for(Profile p:profiles){pj.put(p.toJson());if(p.ping>0)pings.put(p.id,p.ping);}
        for(Subscription sub:subscriptions){sj.put(sub.toJson());if(sub.id.equals(primary))found=true;}
        if(!found)primary=MANUAL_GROUP;
        if(!f345a.edit().putString("profiles",recordCipher.encode("profiles",pj.toString())).putString("subs",recordCipher.encode("subs",sj.toString()))
            .putString("pings_https_rtt_v1",pings.toString()).putString("primary_subscription",primary).putString("primary_choice","1").commit())throw new IllegalStateException("Restore commit failed");
        f346b.clear();f346b.addAll(profiles);f347c.clear();f347c.addAll(subscriptions);revision++;
    }

    /** All records remain in e() for backups; connection consumers use this scoped view. */
    public static final String MANUAL_GROUP="@manual";
    /** Migrate a combined view using its selected connection, never an unrelated source. */
    public synchronized void ensureActiveSubscription(SharedPreferences prefs) {
        recoverPendingRestore();
        if(!primarySubscription().isEmpty())return;
        Profile selected=getById(prefs.getString("selected_profile",""));
        String owner=selected==null?"":selected.subscriptionId;
        for(Object o:f347c){Subscription sub=(Subscription)o;if(!owner.isEmpty()&&owner.equals(sub.id)){setPrimarySubscription(sub.id,false);return;}}
        if(selected==null&&f347c.size()==1){Subscription sub=(Subscription)f347c.get(0);if(!sub.id.isEmpty()){setPrimarySubscription(sub.id,false);return;}}
        setPrimarySubscription(MANUAL_GROUP,false);
    }
    public synchronized String primarySubscription() {
        recoverPendingRestore(); return f345a.getString("primary_subscription",""); }
    public synchronized boolean scopeConfigured() {
        recoverPendingRestore(); return "1".equals(f345a.getString("primary_choice","")); }
    public synchronized boolean isRefreshSource(String id) {
        recoverPendingRestore(); String primary=primarySubscription();return primary.isEmpty()||primary.equals(id); }
    public synchronized ArrayList<Profile> activeProfiles() {
        recoverPendingRestore();
        ArrayList<Profile> result=new ArrayList<>();String primary=primarySubscription();
        for(Object o:f346b){Profile p=(Profile)o;if(primary.isEmpty()||(MANUAL_GROUP.equals(primary)?p.subscriptionId.isEmpty():primary.equals(p.subscriptionId)))result.add(p);}
        return result;
    }
    public synchronized Profile getActiveById(String id) {
        recoverPendingRestore();
        Profile p=getById(id);String primary=primarySubscription();return p!=null&&(primary.isEmpty()||(MANUAL_GROUP.equals(primary)?p.subscriptionId.isEmpty():primary.equals(p.subscriptionId)))?p:null;
    }
    public synchronized void setPrimarySubscription(String id) {
        recoverPendingRestore(); setPrimarySubscription(id,true); }
    private synchronized void setPrimarySubscription(String id,boolean enable) {
        if(id==null)throw new IllegalArgumentException("Missing primary source");
        try {
            ArrayList<Subscription> subs=f();boolean found=id.isEmpty()||MANUAL_GROUP.equals(id);JSONArray json=new JSONArray();
            for(Subscription sub:subs){if(sub.id.equals(id)){found=true;if(enable)sub.enabled=true;}json.put(sub.toJson());}
            if(!found)throw new IllegalArgumentException("Unknown primary source");
            commit(f345a.edit().putString("primary_subscription",id).putString("primary_choice","1").putString("subs",recordCipher.encode("subs",json.toString())));
            f347c.clear();f347c.addAll(subs);revision++;
        }catch(org.json.JSONException e){throw new IllegalStateException("Source selection failed");}
    }

    /** Remove legacy same-source duplicates and coalesce repeated URLs. Independent sources
     * remain separate underneath the single-row UI, so future refreshes cannot erase them. */
    public synchronized int removeDuplicates(SharedPreferences prefs) {
        recoverPendingRestore();
        String selected=prefs.getString("selected_profile","");
        java.util.Set<String> favorites=new java.util.LinkedHashSet<>(java.util.Arrays.asList(prefs.getString("favorites","").split("\\n")));
        java.util.Map<String,String> owners=new java.util.HashMap<>();
        java.util.Map<String,Subscription> urls=new java.util.LinkedHashMap<>();
        ArrayList<Subscription> subs=new ArrayList<>();
        for(Object o:f347c) {
            Subscription sub=(Subscription)o;String url;
            try{url=com.parvaz.tunnel.config.SubscriptionUrl.normalize(sub.url);}catch(IllegalArgumentException e){subs.add(sub);continue;}
            // Disabled subscriptions are not silently re-enabled by cleanup.
            String key=url+"\n"+sub.enabled;
            Subscription existing=urls.get(key);
            if(existing==null){urls.put(key,sub);subs.add(sub);owners.put(sub.id,sub.id);}
            else owners.put(sub.id,existing.id);
        }
        java.util.LinkedHashMap<String,Profile> unique=new java.util.LinkedHashMap<>();
        java.util.Map<String,String> aliases=new java.util.HashMap<>();
        ArrayList<Profile> copies=new ArrayList<>();
        for(Object o:f346b){
            Profile p=ProfileIdentity.copy((Profile)o);p.subscriptionId=owners.getOrDefault(p.subscriptionId,p.subscriptionId);copies.add(p);
            String key=p.subscriptionId+":"+ProfileIdentity.fingerprint(p);Profile old=unique.get(key);
            if(old==null||ProfileDuplicates.preferred(p,old,selected,favorites))unique.put(key,p);
        }
        for(Profile p:copies){Profile winner=unique.get(p.subscriptionId+":"+ProfileIdentity.fingerprint(p));aliases.put(p.id,winner.id);}
        int removed=f346b.size()-unique.size();
        if(removed==0&&subs.size()==f347c.size())return 0;
        // Keep old favorite IDs too: adding a surviving alias is harmless if a subsequent store write fails.
        for(String id:new java.util.ArrayList<>(favorites)){String target=aliases.get(id);if(target!=null)favorites.add(target);}
        prefs.edit().putString("favorites",String.join("\n",favorites)).apply();
        try {
            JSONArray pj=new JSONArray(),sj=new JSONArray();JSONObject pings=new JSONObject();
            for(Profile p:unique.values()){pj.put(p.toJson());if(p.ping>0)pings.put(p.id,p.ping);}
            for(Subscription sub:subs){Subscription c=Subscription.fromJson(sub.toJson());c.count=0;for(Profile p:unique.values())if(p.subscriptionId.equals(c.id))c.count++;sj.put(c.toJson());}
            commit(f345a.edit().putString("profiles",recordCipher.encode("profiles",pj.toString())).putString("subs",recordCipher.encode("subs",sj.toString())).putString("pings_https_rtt_v1",pings.toString())
                .putString("primary_subscription",owners.getOrDefault(primarySubscription(),primarySubscription())));
            f346b.clear();f346b.addAll(unique.values());f347c.clear();
            for(int i=0;i<sj.length();i++)f347c.add(Subscription.fromJson(sj.getJSONObject(i)));
            revision++;return removed;
        }catch(org.json.JSONException e){throw new IllegalStateException("Cleanup serialization failed");}
    }

    public synchronized void updateQuota(Snapshot snapshot,String userinfo,long now) {
        recoverPendingRestore();
        if(snapshot==null||snapshot.revision!=revision)throw new StaleRefresh();
        try {
            ArrayList<Subscription> subs=f();boolean found=false;
            for(Subscription sub:subs)if(sub.id.equals(snapshot.subscription.id)&&sub.enabled&&sub.url.equals(snapshot.subscription.url)) {
                sub.replaceUserinfo(userinfo,now);found=true;break;
            }
            if(!found)throw new StaleRefresh();
            JSONArray json=new JSONArray();for(Subscription sub:subs)json.put(sub.toJson());
            commit(f345a.edit().putString("subs",recordCipher.encode("subs",json.toString())));
            f347c.clear();f347c.addAll(subs);revision++;
        }catch(org.json.JSONException e){throw new IllegalStateException("Quota serialization failed");}
    }

    public synchronized SubscriptionReconciler.Plan mergeSubscription(Snapshot snapshot,
            com.parvaz.tunnel.config.ImportResult parsed,String userinfo,long now,String selectedId) {
        recoverPendingRestore();
        if(parsed.fatal||parsed.profiles.isEmpty())throw new IllegalArgumentException("No valid partial import");
        com.parvaz.tunnel.config.ImportResult union=new com.parvaz.tunnel.config.ImportResult();
        java.util.Set<String> incoming=new java.util.HashSet<>();
        for(Profile p:parsed.profiles){union.add(p);incoming.add(ProfileIdentity.fingerprint(p));}
        for(Object o:f346b){Profile p=(Profile)o;if(snapshot.subscription.id.equals(p.subscriptionId)&&!incoming.contains(ProfileIdentity.fingerprint(p)))union.add(p);}
        return replaceSubscription(snapshot,union,userinfo,now,selectedId);
    }

    /** Capture detached metadata and a generation BEFORE starting a download. */
    public synchronized Snapshot beginRefresh(String id) {
        recoverPendingRestore();
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
        recoverPendingRestore();
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
            updated.replaceUserinfo(userinfo,now);updated.lastUpdate=now;updated.count=plan.count;
            ArrayList<Subscription> subscriptions=new ArrayList<>(f347c);subscriptions.set(index,updated);
            JSONArray profilesJson=new JSONArray(),subsJson=new JSONArray();JSONObject pings=new JSONObject();
            for(Profile p:plan.all){profilesJson.put(p.toJson());if(p.ping>0)pings.put(p.id,p.ping);}
            for(Subscription sub:subscriptions)subsJson.put(sub.toJson());
            commit(f345a.edit().putString("profiles",recordCipher.encode("profiles",profilesJson.toString())).putString("subs",recordCipher.encode("subs",subsJson.toString())).putString("pings_https_rtt_v1",pings.toString()));
            f346b.clear();f346b.addAll(plan.all);f347c.clear();f347c.addAll(subscriptions);revision++;
            return plan;
        } catch(org.json.JSONException e) { throw new IllegalStateException("Subscription serialization failed"); }
    }

    /* renamed from: c */
    public final synchronized ArrayList e() {
        recoverPendingRestore();
        return new ArrayList(this.f346b);
    }

    /* renamed from: d */
    public final synchronized ArrayList f() {
        recoverPendingRestore();
        ArrayList<Subscription> result=new ArrayList<>();
        try { for(Object o:f347c)result.add(Subscription.fromJson(((Subscription)o).toJson())); }
        catch(org.json.JSONException e){throw new IllegalStateException("Subscription snapshot failed");}
        return result;
    }

    /* renamed from: e */
    public final synchronized void g(String str) {
        recoverPendingRestore();
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
        recoverPendingRestore();
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
        recoverPendingRestore();
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
            commit(this.f345a.edit().putString("profiles",recordCipher.encode("profiles",jSONArray.toString())).putString("subs",recordCipher.encode("subs",jSONArray2.toString())).putString("pings_https_rtt_v1", jSONObject.toString()));
        } catch (Exception error) {
            throw new IllegalStateException("Profile persistence failed",error);
        }
    }

    /** Latency-only persistence must not change the subscription/source revision. */
    public synchronized void saveMeasurements(){
        recoverPendingRestore();
        try{JSONObject pings=new JSONObject();for(Object value:f346b){Profile p=(Profile)value;if(p!=null&&p.ping>0)pings.put(p.id,p.ping);}f345a.edit().putString("pings_https_rtt_v1",pings.toString()).apply();}
        catch(org.json.JSONException e){throw new IllegalStateException("Latency persistence failed");}
    }

    /** Read-only ownership of the row used to start the live core. Does not start a
     * manual test or replace any pending result. Source replacement invalidates it. */
    public static final class StartupLatency {
        private final Profile original, snapshot;private final long revision;
        private StartupLatency(Profile p,long revision){original=p;snapshot=ProfileIdentity.copy(p);this.revision=revision;}
    }
    public synchronized StartupLatency captureStartupLatency(Profile expected){
        recoverPendingRestore();
        Profile p=expected==null?null:getActiveById(expected.id);
        if(p==null||!p.subscriptionId.equals(expected.subscriptionId)||!ProfileIdentity.fingerprint(p).equals(ProfileIdentity.fingerprint(expected)))return null;
        return new StartupLatency(p,revision);
    }
    public synchronized boolean rememberConnected(StartupLatency owner,android.content.SharedPreferences prefs,java.util.function.BooleanSupplier proofCurrent){
        recoverPendingRestore();
        if(owner==null||prefs==null||revision!=owner.revision||getActiveById(owner.snapshot.id)!=owner.original
            ||!owner.snapshot.subscriptionId.equals(owner.original.subscriptionId)
            ||!ProfileIdentity.fingerprint(owner.snapshot).equals(ProfileIdentity.fingerprint(owner.original))||!proofCurrent.getAsBoolean())return false;
        return LastConnected.remember(prefs,owner.snapshot);
    }
    /** Fill only an untested row, never overwrite a manual status/result. Proof is
     * rechecked at the write, not just when the ticker obtained the duration. */
    public synchronized boolean publishStartupLatency(StartupLatency owner,int ms,java.util.function.BooleanSupplier proofCurrent){
        recoverPendingRestore();
        if(owner==null||ms<=0||revision!=owner.revision||getActiveById(owner.snapshot.id)!=owner.original
            ||owner.original.ping!=com.parvaz.tunnel.core.LatencyResult.UNTESTED||measurements.containsKey(owner.snapshot.id)
            ||!owner.snapshot.subscriptionId.equals(owner.original.subscriptionId)
            ||!ProfileIdentity.fingerprint(owner.snapshot).equals(ProfileIdentity.fingerprint(owner.original))||!proofCurrent.getAsBoolean())return false;
        owner.original.ping=ms;saveMeasurements();return true;
    }

    /** Transient latency ownership; never written to subscription/profile JSON. */
    private final java.util.Map<String, Measurement> measurements=new java.util.HashMap<>();
    public static final class Measurement {
        public final Profile snapshot;
        private final Profile original;
        private final long revision;
        private Measurement(Profile original,long revision){this.original=original;this.revision=revision;this.snapshot=ProfileIdentity.copy(original);}
    }
    public synchronized Measurement beginMeasurement(Profile profile){
        recoverPendingRestore();
        if(profile==null)return null;
        Profile current=getActiveById(profile.id);
        if(current==null||!current.subscriptionId.equals(profile.subscriptionId)||!ProfileIdentity.fingerprint(current).equals(ProfileIdentity.fingerprint(profile)))return null;
        profile=current;
        Measurement m=new Measurement(profile,revision);measurements.put(profile.id,m);
        profile.ping=com.parvaz.tunnel.core.LatencyResult.TESTING;return m;
    }
    public synchronized boolean ownsMeasurement(Measurement m){
        recoverPendingRestore();
        return m!=null&&measurements.get(m.snapshot.id)==m&&revision==m.revision
            &&getActiveById(m.snapshot.id)==m.original
            &&m.snapshot.subscriptionId.equals(m.original.subscriptionId)
            &&ProfileIdentity.fingerprint(m.snapshot).equals(ProfileIdentity.fingerprint(m.original));
    }
    public synchronized boolean finishMeasurement(Measurement m,int result){
        recoverPendingRestore();
        if(m==null||measurements.get(m.snapshot.id)!=m)return false;
        boolean valid=ownsMeasurement(m);measurements.remove(m.snapshot.id);
        if(valid){m.original.ping=result;return true;}
        // Retire our own pending marker only, never a newer measurement/result.
        if(m.original.ping==com.parvaz.tunnel.core.LatencyResult.TESTING)m.original.ping=com.parvaz.tunnel.core.LatencyResult.CANCELLED;
        return getActiveById(m.snapshot.id)==m.original;
    }

    public final synchronized void i(String str, int i) {
        recoverPendingRestore();
        Profile byId = getById(str);
        if (byId != null) {
            measurements.remove(str); // A newer live-session result supersedes a pending manual test.
            byId.ping = i;
        }
    }

    public final synchronized void j(Subscription subscription) {
        recoverPendingRestore();
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
