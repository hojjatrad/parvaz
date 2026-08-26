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

    public static a a(Context context, String str) throws JSONException {
        JSONObject jSONObject = new JSONObject(str);
        if (!jSONObject.has("profiles") && !jSONObject.has("subscriptions")) {
            throw new IllegalArgumentException("not BackupManager Parvaz backup");
        }
        ProfileStore f = ProfileStore.f(context);
        SharedPreferences sharedPreferences = context.getApplicationContext().getSharedPreferences("parvaz_prefs", 0);
        a obj = new a();
        synchronized (f) {
            f.f346b.clear();
            f.h();
        }
        JSONArray optJSONArray = jSONObject.optJSONArray("subscriptions");
        if (optJSONArray != null) {
            for (int i = 0; i < optJSONArray.length(); i++) {
                Subscription fromJson = Subscription.fromJson(optJSONArray.getJSONObject(i));
                if (fromJson != null) {
                    synchronized (f) {
                        f.f347c.add(fromJson);
                        f.h();
                    }
                    obj.f342b++;
                }
            }
        }
        JSONArray optJSONArray2 = jSONObject.optJSONArray("profiles");
        if (optJSONArray2 != null) {
            ArrayList arrayList = new ArrayList();
            for (int i2 = 0; i2 < optJSONArray2.length(); i2++) {
                Profile fromJson2 = Profile.fromJson(optJSONArray2.getJSONObject(i2));
                if (fromJson2 != null && !fromJson2.address.isEmpty()) {
                    arrayList.add(fromJson2);
                }
            }
            f.a(arrayList, null);
            obj.f341a = arrayList.size();
        }
        f.h();
        JSONObject optJSONObject = jSONObject.optJSONObject("settings");
        if (optJSONObject != null) {
            if (optJSONObject.has("routing_mode")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "routing_mode", optJSONObject.optString("routing_mode"));
            }
            if (optJSONObject.has("remote_dns")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "remote_dns", optJSONObject.optString("remote_dns"));
            }
            if (optJSONObject.has("direct_dns")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "direct_dns", optJSONObject.optString("direct_dns"));
            }
            if (optJSONObject.has("mux_enabled")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "mux_enabled", optJSONObject.optBoolean("mux_enabled"));
            }
            if (optJSONObject.has("mux_concurrency")) {
                sharedPreferences.edit().putInt("mux_concurrency", optJSONObject.optInt("mux_concurrency", 8)).apply();
            }
            if (optJSONObject.has("vpn_mtu")) {
                sharedPreferences.edit().putInt("vpn_mtu", optJSONObject.optInt("vpn_mtu", 1500)).apply();
            }
            if (optJSONObject.has("ipv6_enabled")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "ipv6_enabled", optJSONObject.optBoolean("ipv6_enabled"));
            }
            if (optJSONObject.has("bypass_lan")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "bypass_lan", optJSONObject.optBoolean("bypass_lan", true));
            }
            if (optJSONObject.has("log_level")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "log_level", optJSONObject.optString("log_level"));
            }
            if (optJSONObject.has("domain_strategy")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "domain_strategy", optJSONObject.optString("domain_strategy"));
            }
            if (optJSONObject.has("ping_url")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "ping_url", optJSONObject.optString("ping_url"));
            }
            if (optJSONObject.has("lang")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "lang", optJSONObject.optString("lang", "fa"));
            }
            if (optJSONObject.has("auto_switch")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "auto_switch", optJSONObject.optBoolean("auto_switch", true));
            }
            if (optJSONObject.has("ping_threshold")) {
                sharedPreferences.edit().putInt("ping_threshold", Math.max(200, optJSONObject.optInt("ping_threshold", 1200))).apply();
            }
            if (optJSONObject.has("health_interval")) {
                sharedPreferences.edit().putInt("health_interval", Math.max(5, optJSONObject.optInt("health_interval", 15))).apply();
            }
            if (optJSONObject.has("data_limit_gb")) {
                sharedPreferences.edit().putFloat("data_limit_gb", Math.max(0.0f, (float) optJSONObject.optDouble("data_limit_gb", 0.0d))).apply();
            }
            if (optJSONObject.has("connect_on_boot")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "connect_on_boot", optJSONObject.optBoolean("connect_on_boot"));
            }
            if (optJSONObject.has("per_app_mode")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "per_app_mode", optJSONObject.optString("per_app_mode", "off"));
            }
            if (optJSONObject.has("sub_auto_hours")) {
                sharedPreferences.edit().putInt("sub_auto_hours", Math.max(0, optJSONObject.optInt("sub_auto_hours", 0))).apply();
            }
            if (optJSONObject.has("fragment_enabled")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "fragment_enabled", optJSONObject.optBoolean("fragment_enabled"));
            }
            if (optJSONObject.has("fragment_packets")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "fragment_packets", optJSONObject.optString("fragment_packets", "tlshello"));
            }
            if (optJSONObject.has("fragment_length")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "fragment_length", optJSONObject.optString("fragment_length", "100-200"));
            }
            if (optJSONObject.has("fragment_interval")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "fragment_interval", optJSONObject.optString("fragment_interval", "10-20"));
            }
            if (optJSONObject.has("kill_switch")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "kill_switch", optJSONObject.optBoolean("kill_switch"));
            }
            if (optJSONObject.has("haptics")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "haptics", optJSONObject.optBoolean("haptics", true));
            }
            if (optJSONObject.has("custom_rules")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "custom_rules", optJSONObject.optString("custom_rules", "[]"));
            }
            JSONArray optJSONArray3 = optJSONObject.optJSONArray("per_app_list");
            if (optJSONArray3 != null) {
                ArrayList arrayList2 = new ArrayList();
                for (int i3 = 0; i3 < optJSONArray3.length(); i3++) {
                    arrayList2.add(optJSONArray3.getString(i3));
                }
                StringBuilder sb = new StringBuilder();
                Iterator it = arrayList2.iterator();
                while (it.hasNext()) {
                    String str2 = (String) it.next();
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(str2);
                }
                sharedPreferences.edit().putString("per_app_list", sb.toString()).apply();
            }
            JSONArray optJSONArrayFavs = optJSONObject.optJSONArray("favorites");
            if (optJSONArrayFavs != null) {
                java.util.HashSet<String> favSet = new java.util.HashSet<>();
                for (int i4 = 0; i4 < optJSONArrayFavs.length(); i4++) {
                    favSet.add(optJSONArrayFavs.getString(i4));
                }
                new Prefs(context).saveFavorites(favSet);
            }
            if (optJSONObject.has("domains_direct")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "domains_direct", optJSONObject.optString("domains_direct", ""));
            }
            if (optJSONObject.has("domains_proxy")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "domains_proxy", optJSONObject.optString("domains_proxy", ""));
            }
            if (optJSONObject.has("domains_block")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "domains_block", optJSONObject.optString("domains_block", ""));
            }
            if (optJSONObject.has("buffer_size_kb")) {
                sharedPreferences.edit().putInt("buffer_size_kb", Math.max(8, optJSONObject.optInt("buffer_size_kb", 512))).apply();
            }
            if (optJSONObject.has("health_strikes")) {
                sharedPreferences.edit().putInt("health_strikes", Math.max(1, optJSONObject.optInt("health_strikes", 3))).apply();
            }
            if (optJSONObject.has("auto_wifi")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "auto_wifi", optJSONObject.optString("auto_wifi", "none"));
            }
            if (optJSONObject.has("auto_cell")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "auto_cell", optJSONObject.optString("auto_cell", "none"));
            }
            if (optJSONObject.has("trusted_wifi")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "trusted_wifi", optJSONObject.optString("trusted_wifi", ""));
            }
            if (optJSONObject.has("shake_to_switch")) {
                RulesActivity__ExternalSyntheticOutline0.k(sharedPreferences, "shake_to_switch", optJSONObject.optBoolean("shake_to_switch", false));
            }
            if (optJSONObject.has("chain_profile")) {
                RulesActivity__ExternalSyntheticOutline0.j(sharedPreferences, "chain_profile", optJSONObject.optString("chain_profile", ""));
            }
        }
        return obj;
    }

    /* renamed from: b */
    public static String export(Context context) throws JSONException {
        ProfileStore f = ProfileStore.f(context);
        Prefs prefs = new Prefs(context);
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("format", 1);
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
        return jSONObject.toString(2);
    }
}
