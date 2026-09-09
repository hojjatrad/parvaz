package com.parvaz.tunnel.store;

import android.content.Context;
import android.content.SharedPreferences;
import com.parvaz.tunnel.RulesActivity__ExternalSyntheticOutline0;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.model.Subscription;
import java.util.ArrayList;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* renamed from: S1.a */
/* loaded from: classes.dex */
public final class BackupManager {

    /* JADX WARN: Can't change package for inner class: S1.a.a to com.parvaz.tunnel.store.BackupManager$Result */
    /* renamed from: S1.a$a */
    /* loaded from: classes.dex */
    public static class a {

        /* renamed from: a */
        public int f341a;

        /* renamed from: b */
        public int f342b;
    }

    public static a a(Context context,String str)throws JSONException {
        if(str==null||str.length()>16*1024*1024)throw new IllegalArgumentException("Backup size limit");
        com.parvaz.tunnel.config.LinkParser.checkJsonDepth(str);
        JSONObject root=new JSONObject(str);
        if(!root.has("profiles")||!root.has("subscriptions"))throw new IllegalArgumentException("Incomplete backup");
        JSONArray nodes=root.getJSONArray("profiles"),sources=root.getJSONArray("subscriptions");
        if(nodes.length()>5000||sources.length()>1000)throw new IllegalArgumentException("Backup count limit");
        ArrayList<Profile> profiles=new ArrayList<>();ArrayList<Subscription> subs=new ArrayList<>();java.util.Set<String> ids=new java.util.HashSet<>();
        for(int i=0;i<sources.length();i++){
            Subscription sub=Subscription.fromJson(sources.getJSONObject(i));
            if(sub.id.isEmpty()||!ids.add(sub.id))throw new IllegalArgumentException("Invalid subscription identity");
            if(!sub.url.isEmpty())com.parvaz.tunnel.config.SubscriptionUrl.normalize(sub.url);
            subs.add(sub);
        }
        ids.clear();for(int i=0;i<nodes.length();i++){
            Profile p=Profile.fromJson(nodes.getJSONObject(i));if(p==null||!com.parvaz.tunnel.config.LinkParser.valid(p)||!ids.add(p.id))throw new IllegalArgumentException("Invalid profile");
            profiles.add(p);
        }
        JSONObject settings=root.optJSONObject("settings");if(settings==null)settings=new JSONObject();
        SharedPreferences prefs=context.getSharedPreferences("parvaz_prefs",0);SharedPreferences.Editor edit=prefs.edit();
        // Explicit allowlist; keys controlling credentials, LAN exposure and update verification never come from a backup.
        for(String key:new String[]{"routing_mode","remote_dns","direct_dns","log_level","domain_strategy","ping_url","lang","per_app_mode","fragment_packets","fragment_length","fragment_interval","custom_rules","domains_direct","domains_proxy","domains_block","auto_wifi","auto_cell","trusted_wifi","chain_profile","selected_profile"})
            if(settings.has(key))edit.putString(key,settings.getString(key));
        for(String key:new String[]{"mux_enabled","ipv6_enabled","bypass_lan","auto_switch","fragment_enabled","kill_switch","haptics","shake_to_switch"})if(settings.has(key))edit.putBoolean(key,settings.getBoolean(key));
        for(String key:new String[]{"mux_concurrency","vpn_mtu","ping_threshold","health_interval","sub_auto_hours","buffer_size_kb","health_strikes"})if(settings.has(key))edit.putInt(key,settings.getInt(key));
        if(settings.has("data_limit_gb")){double v=settings.getDouble("data_limit_gb");if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException("Invalid quota");edit.putFloat("data_limit_gb",(float)v);}
        for(String key:new String[]{"favorites","per_app_list"})if(settings.has(key)){
            JSONArray list=settings.getJSONArray(key);java.util.LinkedHashSet<String> values=new java.util.LinkedHashSet<>();
            for(int i=0;i<list.length();i++){String value=list.getString(i);if(value.contains("\n")||value.length()>1024)throw new IllegalArgumentException("Invalid setting list");values.add(value);}
            edit.putString(key,String.join("\n",values));
        }
        // Import never turns on automatic VPN connection or LAN sharing unexpectedly.
        edit.putBoolean("connect_on_boot",false).putBoolean("lan_proxy",false);
        String selected=settings.optString("selected_profile","");if(!ids.contains(selected))edit.putString("selected_profile",profiles.isEmpty()?"":profiles.get(0).id);
        ProfileStore store=ProfileStore.f(context);
        store.restoreRecords(profiles,subs,root.optString("active_subscription",""));
        if(!edit.commit())throw new IllegalStateException("Settings restore commit failed");
        store.removeDuplicates(prefs);a result=new a();result.f341a=profiles.size();result.f342b=subs.size();return result;
    }

    /* renamed from: b */
    public static String export(Context context) throws JSONException {
        ProfileStore f = ProfileStore.f(context);
        synchronized(f) {
        Prefs prefs = new Prefs(context);
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("format", 2);
        jSONObject.put("active_subscription",f.primarySubscription());
        jSONObject.put("app", "parvaz");
        jSONObject.put("exported", System.currentTimeMillis());
        JSONArray jSONArray = new JSONArray();
        Iterator it = f.e().iterator();
        while (it.hasNext()) {
            jSONArray.put(((Profile) it.next()).toJson());
        }
        jSONObject.put("profiles", jSONArray);
        JSONArray jSONArray2 = new JSONArray();
        Iterator it2 = f.f().iterator();
        while (it2.hasNext()) {
            jSONArray2.put(((Subscription) it2.next()).toJson());
        }
        jSONObject.put("subscriptions", jSONArray2);
        JSONObject jSONObject2 = new JSONObject();
        SharedPreferences sharedPreferences = prefs.f343a;
        jSONObject2.put("selected_profile",sharedPreferences.getString("selected_profile",""));
        jSONObject2.put("routing_mode", sharedPreferences.getString("routing_mode", "iran_direct"));
        jSONObject2.put("remote_dns", sharedPreferences.getString("remote_dns", "https://1.1.1.1/dns-query,https://dns.google/dns-query"));
        jSONObject2.put("direct_dns", sharedPreferences.getString("direct_dns", "78.157.42.100"));
        jSONObject2.put("mux_enabled", sharedPreferences.getBoolean("mux_enabled", false));
        jSONObject2.put("mux_concurrency", sharedPreferences.getInt("mux_concurrency", 8));
        jSONObject2.put("vpn_mtu", sharedPreferences.getInt("vpn_mtu", 1500));
        jSONObject2.put("ipv6_enabled", sharedPreferences.getBoolean("ipv6_enabled", false));
        jSONObject2.put("bypass_lan", sharedPreferences.getBoolean("bypass_lan", true));
        jSONObject2.put("log_level", sharedPreferences.getString("log_level", "warning"));
        jSONObject2.put("domain_strategy", sharedPreferences.getString("domain_strategy", "IPIfNonMatch"));
        jSONObject2.put("ping_url", sharedPreferences.getString("ping_url", "https://www.gstatic.com/generate_204"));
        jSONObject2.put("lang", sharedPreferences.getString("lang", "fa"));
        jSONObject2.put("auto_switch", sharedPreferences.getBoolean("auto_switch", true));
        jSONObject2.put("ping_threshold", sharedPreferences.getInt("ping_threshold", 1200));
        jSONObject2.put("health_interval", sharedPreferences.getInt("health_interval", 15));
        jSONObject2.put("data_limit_gb", sharedPreferences.getFloat("data_limit_gb", 0.0f));
        jSONObject2.put("connect_on_boot", sharedPreferences.getBoolean("connect_on_boot", false));
        jSONObject2.put("per_app_mode", sharedPreferences.getString("per_app_mode", "off"));
        jSONObject2.put("sub_auto_hours", sharedPreferences.getInt("sub_auto_hours", 0));
        jSONObject2.put("fragment_enabled", sharedPreferences.getBoolean("fragment_enabled", false));
        jSONObject2.put("fragment_packets", sharedPreferences.getString("fragment_packets", "tlshello"));
        jSONObject2.put("fragment_length", sharedPreferences.getString("fragment_length", "100-200"));
        jSONObject2.put("fragment_interval", sharedPreferences.getString("fragment_interval", "10-20"));
        jSONObject2.put("kill_switch", sharedPreferences.getBoolean("kill_switch", false));
        jSONObject2.put("haptics", sharedPreferences.getBoolean("haptics", true));
        jSONObject2.put("custom_rules", sharedPreferences.getString("custom_rules", "[]"));
        jSONObject2.put("domains_direct", sharedPreferences.getString("domains_direct", ""));
        jSONObject2.put("domains_proxy", sharedPreferences.getString("domains_proxy", ""));
        jSONObject2.put("domains_block", sharedPreferences.getString("domains_block", ""));
        jSONObject2.put("buffer_size_kb", sharedPreferences.getInt("buffer_size_kb", 512));
        jSONObject2.put("health_strikes", sharedPreferences.getInt("health_strikes", 3));
        jSONObject2.put("auto_wifi", sharedPreferences.getString("auto_wifi", "none"));
        jSONObject2.put("auto_cell", sharedPreferences.getString("auto_cell", "none"));
        jSONObject2.put("trusted_wifi", sharedPreferences.getString("trusted_wifi", ""));
        jSONObject2.put("shake_to_switch", sharedPreferences.getBoolean("shake_to_switch", false));
        jSONObject2.put("chain_profile", sharedPreferences.getString("chain_profile", ""));
        JSONArray jSONArray3 = new JSONArray();
        Iterator it3 = prefs.getFavorites().iterator();
        while (it3.hasNext()) {
            jSONArray3.put(it3.next());
        }
        jSONObject2.put("favorites", jSONArray3);
        JSONArray jSONArray4 = new JSONArray();
        Iterator it4 = prefs.c().iterator();
        while (it4.hasNext()) {
            jSONArray4.put(it4.next());
        }
        jSONObject2.put("per_app_list", jSONArray4);
        jSONObject.put("settings", jSONObject2);
        if(jSONArray.length()>5000||jSONArray2.length()>1000)throw new IllegalArgumentException("Backup count limit");
        String result=jSONObject.toString(2);
        if(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024)throw new IllegalArgumentException("Backup size limit");
        return result;
        }
    }
}
