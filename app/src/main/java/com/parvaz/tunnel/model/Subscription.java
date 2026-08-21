package com.parvaz.tunnel.model;

import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class Subscription {
    public String id = "";
    public String name = "";
    public String url = "";
    public long lastUpdate = 0;
    public boolean enabled = true;
    public int count = 0;
    public long quotaUpload = -1;
    public long quotaDownload = -1;
    public long quotaTotal = -1;
    public long quotaExpire = -1;

    public static Subscription fromJson(JSONObject jSONObject) {
        Subscription subscription = new Subscription();
        subscription.id = jSONObject.optString("id", "");
        subscription.name = jSONObject.optString("name", "");
        subscription.url = jSONObject.optString("url", "");
        subscription.lastUpdate = jSONObject.optLong("lastUpdate", 0L);
        subscription.enabled = jSONObject.optBoolean("enabled", true);
        subscription.count = jSONObject.optInt("count", 0);
        subscription.quotaUpload = jSONObject.optLong("quotaUpload", -1L);
        subscription.quotaDownload = jSONObject.optLong("quotaDownload", -1L);
        subscription.quotaTotal = jSONObject.optLong("quotaTotal", -1L);
        subscription.quotaExpire = jSONObject.optLong("quotaExpire", -1L);
        return subscription;
    }

    public void applyUserinfo(String str) {
        char c;
        if (str == null || str.trim().isEmpty()) {
            return;
        }
        for (String str2 : str.split(";")) {
            int indexOf = str2.indexOf(61);
            if (indexOf > 0) {
                String lowerCase = str2.substring(0, indexOf).trim().toLowerCase();
                String trim = str2.substring(indexOf + 1).trim();
                if (!trim.isEmpty()) {
                    try {
                        long parseDouble = (long) Double.parseDouble(trim);
                        lowerCase.getClass();
                        switch (lowerCase.hashCode()) {
                            case -1289159393:
                                if (lowerCase.equals("expire")) {
                                    c = 0;
                                    break;
                                }
                                break;
                            case -838595071:
                                if (lowerCase.equals("upload")) {
                                    c = 1;
                                    break;
                                }
                                break;
                            case 110549828:
                                if (lowerCase.equals("total")) {
                                    c = 2;
                                    break;
                                }
                                break;
                            case 1427818632:
                                if (lowerCase.equals("download")) {
                                    c = 3;
                                    break;
                                }
                                break;
                        }
                        c = 65535;
                        if (c == 0) {
                            this.quotaExpire = parseDouble;
                        } else if (c == 1) {
                            this.quotaUpload = parseDouble;
                        } else if (c == 2) {
                            this.quotaTotal = parseDouble;
                        } else if (c == 3) {
                            this.quotaDownload = parseDouble;
                        }
                    } catch (NumberFormatException unused) {
                    }
                }
            }
        }
    }

    public boolean hasQuota() {
        return this.quotaTotal > 0;
    }

    public int quotaPercent() {
        if (!hasQuota()) {
            return -1;
        }
        long quotaUsed = quotaUsed();
        long j = this.quotaTotal;
        if (quotaUsed >= j) {
            return 100;
        }
        return (int) ((quotaUsed * 100) / j);
    }

    public long quotaRemaining() {
        if (hasQuota()) {
            return Math.max(0L, this.quotaTotal - quotaUsed());
        }
        return -1L;
    }

    public long quotaUsed() {
        return Math.max(0L, this.quotaUpload) + Math.max(0L, this.quotaDownload);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("id", this.id);
        jSONObject.put("name", this.name);
        jSONObject.put("url", this.url);
        jSONObject.put("lastUpdate", this.lastUpdate);
        jSONObject.put("enabled", this.enabled);
        jSONObject.put("count", this.count);
        jSONObject.put("quotaUpload", this.quotaUpload);
        jSONObject.put("quotaDownload", this.quotaDownload);
        jSONObject.put("quotaTotal", this.quotaTotal);
        jSONObject.put("quotaExpire", this.quotaExpire);
        return jSONObject;
    }
}
