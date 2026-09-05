package com.parvaz.tunnel.core;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.R;

/**
 * Manages quota threshold notifications (90%, 100%, and expiration warning).
 */
public final class QuotaNotifier {

    public static final String ALERT_CHANNEL_ID = "parvaz_quota_alerts";
    public static final int NOTIF_WARN_90 = 9901;
    public static final int NOTIF_WARN_100 = 9902;
    public static final int NOTIF_WARN_EXPIRE = 9903;

    private static final String PREFS = "parvaz_quota_notif";
    private static final String KEY_WARNED_90 = "warned_90";
    private static final String KEY_WARNED_100 = "warned_100";
    private static final String KEY_WARNED_EXPIRE = "warned_expire";

    private QuotaNotifier() {
    }

    public static void checkAndNotify(Context context, long usedBytes, long totalBytes, long expireSec) {
        if (context == null || totalBytes <= 0) {
            return;
        }

        createChannel(context);
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        int percent = (int) Math.min(100L, (usedBytes * 100) / totalBytes);
        long remainingBytes = Math.max(0L, totalBytes - usedBytes);
        boolean fa = "fa".equals(context.getSharedPreferences("parvaz_prefs", 0).getString("lang", "fa"));

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, openApp, 201326592);

        // 1. 100% Exceeded
        if (percent >= 100) {
            if (!sp.getBoolean(KEY_WARNED_100, false)) {
                NotificationCompat.Builder b = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_tile)
                        .setContentTitle(context.getString(R.string.app_name))
                        .setContentText(context.getString(R.string.quota_warn_100))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pi)
                        .setAutoCancel(true);
                nm.notify(NOTIF_WARN_100, b.build());
                sp.edit().putBoolean(KEY_WARNED_100, true).apply();
            }
        } else {
            sp.edit().putBoolean(KEY_WARNED_100, false).apply();
        }

        // 2. 90% Warning
        if (percent >= 90 && percent < 100) {
            if (!sp.getBoolean(KEY_WARNED_90, false)) {
                String body = fa
                        ? ("بیش از ۹۰٪ حجم سرویس مصرف شده است! (تنها " + MainActivity.fmtBytes(remainingBytes) + " باقیمانده)")
                        : ("Over 90% of service quota used! (" + MainActivity.fmtBytes(remainingBytes) + " left)");
                NotificationCompat.Builder b = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_tile)
                        .setContentTitle(context.getString(R.string.app_name))
                        .setContentText(body)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pi)
                        .setAutoCancel(true);
                nm.notify(NOTIF_WARN_90, b.build());
                sp.edit().putBoolean(KEY_WARNED_90, true).apply();
            }
        } else if (percent < 90) {
            sp.edit().putBoolean(KEY_WARNED_90, false).apply();
        }

        // 3. Expiration Warning (<= 2 days)
        if (expireSec > 0) {
            if (expireSec > 10000000000L) expireSec /= 1000L;
            long nowSec = System.currentTimeMillis() / 1000L;
            long daysLeft = (expireSec - nowSec) / 86400L;
            if (daysLeft >= 0 && daysLeft <= 2) {
                if (!sp.getBoolean(KEY_WARNED_EXPIRE, false)) {
                    String body = fa
                            ? ("زمان سرویس رو به پایان است (تنها " + daysLeft + " روز باقی‌مانده)!")
                            : ("Service expiring soon (" + daysLeft + " days remaining)!");
                    NotificationCompat.Builder b = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_tile)
                            .setContentTitle(context.getString(R.string.app_name))
                            .setContentText(body)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pi)
                            .setAutoCancel(true);
                    nm.notify(NOTIF_WARN_EXPIRE, b.build());
                    sp.edit().putBoolean(KEY_WARNED_EXPIRE, true).apply();
                }
            } else if (daysLeft > 2) {
                sp.edit().putBoolean(KEY_WARNED_EXPIRE, false).apply();
            }
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Service Quota & Expiry Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            chan.setDescription("Alerts when service data or time is running low");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(chan);
            }
        }
    }
}
