package com.parvaz.tunnel.core;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileStore;
import com.parvaz.tunnel.R;

/* loaded from: classes.dex */
public class ParvazWidget extends AppWidgetProvider {
    public static void a(Context context) {
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, (Class<?>) ParvazWidget.class));
            if (appWidgetIds == null || appWidgetIds.length == 0) {
                return;
            }
            for (int i : appWidgetIds) {
                b(context, appWidgetManager, i);
            }
        } catch (Throwable unused) {
            android.util.Log.w("Parvaz/ParvazWidget", "Throwable ignored", unused);
        }
    }

    public static void b(Context context, AppWidgetManager appWidgetManager, int i) {
        String string;
        int i2;
        String str;
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_parvaz);
        boolean z = TunnelVpnService.serviceRunning;
        int i3 = TunnelVpnService.currentState;
        if (i3 != 1 && i3 != 5) {
            if (z) {
                string = context.getString(R.string.state_connected);
                i2 = -16725933;
            } else {
                string = context.getString(R.string.state_disconnected);
                i2 = -6381922;
            }
        } else {
            string = context.getString(R.string.state_connecting);
            i2 = -26624;
        }
        remoteViews.setTextViewText(R.id.widget_state, string);
        remoteViews.setTextColor(R.id.widget_state, i2);
        remoteViews.setInt(R.id.widget_dot, "setColorFilter", i2);
        String string2 = context.getString(R.string.app_name);
        try {
            Profile byId = ProfileStore.f(context).getById(context.getApplicationContext().getSharedPreferences("parvaz_prefs", 0).getString("selected_profile", ""));
            if (byId != null && (str = byId.remark) != null && !str.isEmpty()) {
                string2 = byId.remark;
            }
        } catch (Throwable unused) {
            android.util.Log.w("Parvaz/ParvazWidget", "Throwable ignored", unused);
        }
        remoteViews.setTextViewText(R.id.widget_name, string2);
        Intent intent = new Intent(context, (Class<?>) ParvazWidget.class);
        intent.setAction("com.parvaz.tunnel.WIDGET_TOGGLE");
        remoteViews.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(context, 0, intent, 201326592));
        appWidgetManager.updateAppWidget(i, remoteViews);
    }

    @Override // android.appwidget.AppWidgetProvider, android.content.BroadcastReceiver
    public final void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && "com.parvaz.tunnel.WIDGET_TOGGLE".equals(intent.getAction())) {
            if (TunnelVpnService.serviceRunning) {
                Intent intent2 = new Intent(context, (Class<?>) TunnelVpnService.class);
                intent2.setAction("com.parvaz.tunnel.STOP");
                context.startService(intent2);
            } else {
                Intent intent3 = new Intent(context, (Class<?>) MainActivity.class);
                intent3.setFlags(335544320);
                intent3.putExtra("com.parvaz.tunnel.AUTO_CONNECT", true);
                context.startActivity(intent3);
            }
            a(context);
        }
    }

    @Override // android.appwidget.AppWidgetProvider
    public final void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] iArr) {
        for (int i : iArr) {
            b(context, appWidgetManager, i);
        }
    }
}
