package com.parvaz.tunnel.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if (str == null || str.trim().isEmpty()) {
            return;
        }
        String text = str.trim();

        // Check if JSON response (e.g. Marzban API info: {"used_traffic":..., "data_limit":..., "expire":...})
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                JSONObject json = new JSONObject(text);
                long tot = json.optLong("data_limit", json.optLong("total", -1L));
                long used = json.optLong("used_traffic", json.optLong("used", -1L));
                long exp = json.optLong("expire", -1L);
                if (tot > 0) this.quotaTotal = tot;
                if (used >= 0) {
                    this.quotaDownload = used;
                    this.quotaUpload = 0;
                }
                if (exp > 0) this.quotaExpire = exp;
                return;
            } catch (Throwable ignored) {
            }
        }

        // Standard subscription-userinfo header format: upload=...; download=...; total=...; expire=...
        for (String part : text.split(";")) {
            int indexOf = part.indexOf('=');
            if (indexOf > 0) {
                String key = part.substring(0, indexOf).trim().toLowerCase(Locale.US);
                String val = part.substring(indexOf + 1).trim();
                if (!val.isEmpty()) {
                    try {
                        long num = (long) Double.parseDouble(val);
                        if ("expire".equals(key)) {
                            this.quotaExpire = num;
                        } else if ("upload".equals(key)) {
                            this.quotaUpload = num;
                        } else if ("total".equals(key)) {
                            this.quotaTotal = num;
                        } else if ("download".equals(key)) {
                            this.quotaDownload = num;
                        }
                    } catch (NumberFormatException unused) {
                        android.util.Log.w("Parvaz/Subscription", "NumberFormatException ignored", unused);
                    }
                }
            }
        }
    }

    /**
     * Inspects imported profile remarks for panel traffic/expiry banners.
     * Panels often embed 'حجم: 2.1 GB / 10 GB' or 'انقضا: 2026-09-25' into remark names.
     */
    public void extractInfoFromRemarks(List<Profile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }
        for (Profile p : profiles) {
            if (p == null || p.remark == null || p.remark.isEmpty()) {
                continue;
            }
            String rem = p.remark;

            // Pattern: X GB / Y GB
            if (this.quotaTotal <= 0) {
                Pattern tp = Pattern.compile(
                        "([\\d\\.]+)\\s*(GB|MB|KB|G|M|K|گیگابایت|مگابایت)\\s*[\\/|\\-]\\s*([\\d\\.]+)\\s*(GB|MB|KB|G|M|K|گیگابایت|مگابایت)",
                        Pattern.CASE_INSENSITIVE);
                Matcher tm = tp.matcher(rem);
                if (tm.find()) {
                    long u = parseBytesWithUnit(tm.group(1), tm.group(2));
                    long tot = parseBytesWithUnit(tm.group(3), tm.group(4));
                    if (tot > 0) {
                        this.quotaTotal = tot;
                        this.quotaDownload = u;
                        this.quotaUpload = 0;
                    }
                }
            }

            // Expiry date YYYY-MM-DD or YYYY/MM/DD
            if (this.quotaExpire <= 0) {
                Pattern dp = Pattern.compile("(\\d{4})[-\\/](\\d{1,2})[-\\/](\\d{1,2})");
                Matcher dm = dp.matcher(rem);
                if (dm.find()) {
                    try {
                        String ds = dm.group(1) + "-" + String.format(Locale.US, "%02d", Integer.parseInt(dm.group(2)))
                                + "-" + String.format(Locale.US, "%02d", Integer.parseInt(dm.group(3)));
                        Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(ds);
                        if (d != null) {
                            this.quotaExpire = d.getTime() / 1000L;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            // Days left: X روز or X days
            if (this.quotaExpire <= 0) {
                Pattern dlp = Pattern.compile("(\\d+)\\s*(روز|day|days)", Pattern.CASE_INSENSITIVE);
                Matcher dlm = dlp.matcher(rem);
                if (dlm.find()) {
                    try {
                        int days = Integer.parseInt(dlm.group(1));
                        if (days > 0 && days < 1000) {
                            this.quotaExpire = (System.currentTimeMillis() / 1000L) + (days * 86400L);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private static long parseBytesWithUnit(String valStr, String unitStr) {
        try {
            double v = Double.parseDouble(valStr.trim());
            String u = unitStr.trim().toUpperCase(Locale.US);
            if (u.contains("G") || u.contains("گیگ")) {
                return (long) (v * 1024L * 1024L * 1024L);
            }
            if (u.contains("M") || u.contains("مگ")) {
                return (long) (v * 1024L * 1024L);
            }
            if (u.contains("K")) {
                return (long) (v * 1024L);
            }
            return (long) v;
        } catch (Throwable t) {
            return 0L;
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

    public int getDaysRemaining() {
        if (this.quotaExpire <= 0) {
            return -1;
        }
        long expSec = this.quotaExpire;
        if (expSec > 10000000000L) {
            expSec /= 1000L;
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        return (int) Math.max(0L, (expSec - nowSec) / 86400L);
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
