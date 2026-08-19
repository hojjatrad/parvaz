package com.parvaz.tunnel.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.parvaz.tunnel.store.ProfileStore;

/* loaded from: classes.dex */
public class BootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public final void onReceive(Context context, Intent intent) {
        String str;
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        if ("android.intent.action.BOOT_COMPLETED".equals(action) || "android.intent.action.MY_PACKAGE_REPLACED".equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            SharedPreferences sharedPreferences = context.getApplicationContext().getSharedPreferences("parvaz_prefs", 0);
            if (sharedPreferences.getBoolean("connect_on_boot", false)) {
                if (ProfileStore.f(context).getById(sharedPreferences.getString("selected_profile", "")) == null) {
                    str = "boot: no selected profile, skipping";
                } else {
                    if (VpnService.prepare(context) == null) {
                        Intent action2 = new Intent(context, (Class<?>) TunnelVpnService.class).setAction("com.parvaz.tunnel.START");
                        try {
                            if (Build.VERSION.SDK_INT >= 26) {
                                ContextCompat.startForegroundService(context, action2);
                            } else {
                                context.startService(action2);
                            }
                            return;
                        } catch (Exception e) {
                            Log.e("ParvazVpn", "boot: start failed", e);
                            return;
                        }
                    }
                    str = "boot: no VPN consent yet, skipping";
                }
                Log.w("ParvazVpn", str);
            }
        }
    }
}
