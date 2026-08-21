package com.parvaz.tunnel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.parvaz.tunnel.core.CoreManager;
import com.parvaz.tunnel.core.GeoAssets;
import com.parvaz.tunnel.core.CrashReporter;
import com.parvaz.tunnel.core.SafeMode;
import com.parvaz.tunnel.core.SubscriptionWorker;
import go.Seq;
import java.io.File;
import java.util.Locale;
import libv2ray.Libv2ray;

/* loaded from: classes.dex */
public class App extends Application {
    /* renamed from: a */
    public static Context wrapLocale(Context context) {
        try {
            Locale locale = new Locale(context.getApplicationContext().getSharedPreferences("parvaz_prefs", 0).getString("lang", "fa"));
            Locale.setDefault(locale);
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.setLocale(locale);
            configuration.setLayoutDirection(locale);
            return context.createConfigurationContext(configuration);
        } catch (Throwable unused) {
            return context;
        }
    }

    @Override // android.content.ContextWrapper
    public final void attachBaseContext(Context context) {
        super.attachBaseContext(wrapLocale(context));
    }

    @Override // android.app.Application
    public final void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashReporter.a(getApplicationContext(), Thread.getDefaultUncaughtExceptionHandler()));
        boolean z = false;
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("parvaz_safemode", 0);
        int i = sharedPreferences.getInt("pending_launches", 0) + 1;
        if (i >= 3) {
            z = true;
        }
        SafeMode.sTrippedThisRun = z;
        sharedPreferences.edit().putInt("pending_launches", i).putBoolean("safe_active", SafeMode.sTrippedThisRun).commit();
        try {
            Seq.setContext(getApplicationContext());
        } catch (Throwable unused) {
            android.util.Log.w("Parvaz/App", "Throwable ignored", unused);
        }
        try {
            File filesDir = getFilesDir();
            GeoAssets.installBundled(this);
            Libv2ray.initCoreEnv(filesDir.getAbsolutePath(), "");
            // Bundled files are the small -lite set; fetch the full ones in background.
            GeoAssets.maybeUpgrade(this);
        } catch (Throwable th) {
            Log.e("ParvazCore", "initEnv failed", th);
        }
        // R8 had inlined the body of this call; collapse back to the public API.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        try {
            SubscriptionWorker.g(this);
        } catch (Throwable unused2) {
            android.util.Log.w("Parvaz/App", "Throwable ignored", unused2);
        }
    }
}
