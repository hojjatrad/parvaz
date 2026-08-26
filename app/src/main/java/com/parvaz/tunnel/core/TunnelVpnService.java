package com.parvaz.tunnel.core;

import android.app.Notification;
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ProxyInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.core.CoreManager;
import com.parvaz.tunnel.core.LogBuffer;
import com.parvaz.tunnel.core.NetworkMonitor;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.store.ProfileStore;
import com.parvaz.tunnel.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import libv2ray.CoreController;

/* loaded from: classes.dex */
public class TunnelVpnService extends VpnService {
    public static final String CHANNEL_ID = "parvaz_vpn";
    public static final int NOTIFY_ID = 8811;


    /* renamed from: t */
    public static final String[] ROUTED_IPV4 = {"0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4", "32.0.0.0/3", "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6", "172.0.0.0/12", "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9", "173.0.0.0/8", "174.0.0.0/7", "176.0.0.0/4", "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16", "192.170.0.0/15", "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10", "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5", "208.0.0.0/4"};

    /* renamed from: u */
    public static volatile boolean serviceRunning = false;

    /* renamed from: v */
    public static volatile int currentState = 0;

    /* renamed from: a */
    public int dayFlushTick;
    public m b;
    public NetworkMonitor c;

    /* renamed from: d */
    public long pendingDayDown;

    /* renamed from: e */
    public long pendingDayUp;
    public Prefs f;

    /* renamed from: g */
    public Profile profile;
    public i h;

    /* renamed from: i */
    public ParcelFileDescriptor tunInterface;

    /* renamed from: j */
    public long startedAt = 0;

    /* renamed from: k */
    public final Handler handler = new Handler(Looper.getMainLooper());

    /* renamed from: l */
    public int strikes = 0;

    /* renamed from: m */
    public int chainedSwitches = 0;

    /* renamed from: n */
    public long lastConnectAt = 0;

    /* renamed from: o */
    public volatile boolean switching = false;
    public final HashMap p = new HashMap();

    /* renamed from: q */
    public long sessionUp = 0;

    /* renamed from: r */
    public long sessionDown = 0;

    /** Session byte total at the previous health check, to detect real traffic flow. */
    public long lastHealthBytes = 0;
    public final d s = new d();

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.a to com.parvaz.tunnel.core.TunnelVpnService$a */
    /* loaded from: classes.dex */
    public class a implements Runnable {
        public a() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService.this.lambda$lambda$autoSwitch$2$2();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.b to com.parvaz.tunnel.core.TunnelVpnService$b */
    /* loaded from: classes.dex */
    public class b implements Runnable {
        public b() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService tunnelVpnService = TunnelVpnService.this;
            tunnelVpnService.stopStatsTicker();
            i iVar = new i();
            tunnelVpnService.h = iVar;
            tunnelVpnService.handler.postDelayed(iVar, 1000L);
            tunnelVpnService.startHealthTicker();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.c to com.parvaz.tunnel.core.TunnelVpnService$c */
    /* loaded from: classes.dex */
    public class c implements Runnable {
        public c() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService tunnelVpnService = TunnelVpnService.this;
            tunnelVpnService.fail(tunnelVpnService.getString(R.string.no_alternative));
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.d to com.parvaz.tunnel.core.TunnelVpnService$d */
    /* loaded from: classes.dex */
    public class d extends BroadcastReceiver {
        public d() {
        }

        @Override // android.content.BroadcastReceiver
        public final void onReceive(Context context, Intent intent) {
            if ("com.parvaz.tunnel.STOP".equals(intent.getAction())) {
                TunnelVpnService.this.shutdown(true, false);
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.e to com.parvaz.tunnel.core.TunnelVpnService$e */
    /* loaded from: classes.dex */
    public class e implements NetworkMonitor.c {
        public e() {
        }

        @Override
        public TunnelVpnService outer() {
            return TunnelVpnService.this;
        }

        @Override
        public Runnable newReconnect() {
            return TunnelVpnService.this.new f();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.f to com.parvaz.tunnel.core.TunnelVpnService$f */
    /* loaded from: classes.dex */
    public class f implements Runnable {

        /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.f.a to com.parvaz.tunnel.core.TunnelVpnService$f$a */
        /* loaded from: classes.dex */
        public class a implements Runnable {
            public a() {
            }

            @Override // java.lang.Runnable
            public final void run() {
                TunnelVpnService.this.lambda$lambda$autoSwitch$2$2();
            }
        }

        /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.f.b to com.parvaz.tunnel.core.TunnelVpnService$f$b */
        /* loaded from: classes.dex */
        public class b implements Runnable {
            public b() {
            }

            @Override // java.lang.Runnable
            public final void run() {
                TunnelVpnService tunnelVpnService = TunnelVpnService.this;
                tunnelVpnService.stopStatsTicker();
                i iVar = new i();
                tunnelVpnService.h = iVar;
                tunnelVpnService.handler.postDelayed(iVar, 1000L);
                tunnelVpnService.startHealthTicker();
            }
        }

        /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.f.c to com.parvaz.tunnel.core.TunnelVpnService$f$c */
        /* loaded from: classes.dex */
        public class c implements Runnable {
            public c() {
            }

            @Override // java.lang.Runnable
            public final void run() {
                TunnelVpnService tunnelVpnService = TunnelVpnService.this;
                tunnelVpnService.fail(tunnelVpnService.getString(R.string.no_alternative));
            }
        }

        public f() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService tunnelVpnService = TunnelVpnService.this;
            try {
                tunnelVpnService.h(tunnelVpnService.profile.remark, 5);
                tunnelVpnService.stopStatsTicker();
                tunnelVpnService.stopHealthTicker();
                // The network just changed underneath us -- re-pin before the core
                // dials out again, otherwise it reconnects over the old interface.
                tunnelVpnService.bindUnderlyingNetwork();
                CoreManager.b().stop();
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException unused) {
                    android.util.Log.w("Parvaz/TunnelVpnService", "InterruptedException ignored", unused);
                }
                ParcelFileDescriptor parcelFileDescriptor = tunnelVpnService.tunInterface;
                CoreManager.b().start(tunnelVpnService, tunnelVpnService.profile, parcelFileDescriptor == null ? 0 : parcelFileDescriptor.getFd(), new a());
                tunnelVpnService.lastConnectAt = System.currentTimeMillis();
                tunnelVpnService.switching = false;
                TunnelVpnService.serviceRunning = true;
                tunnelVpnService.h(tunnelVpnService.profile.remark, 2);
                tunnelVpnService.updateNotification(tunnelVpnService.profile.remark, tunnelVpnService.getString(R.string.reconnected));
                tunnelVpnService.handler.post(new b());
            } catch (Throwable th) {
                Log.e("ParvazVpn", "reconnect failed", th);
                tunnelVpnService.switching = false;
                tunnelVpnService.handler.post(new c());
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.g to com.parvaz.tunnel.core.TunnelVpnService$g */
    /* loaded from: classes.dex */
    public class g implements Runnable {
        public g() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            String str;
            String message;
            String string;
            TunnelVpnService tunnelVpnService = TunnelVpnService.this;
            if (!tunnelVpnService.switching) {
                tunnelVpnService.h(tunnelVpnService.getString(R.string.state_connecting), 1);
            }
            if (tunnelVpnService.switching) {
                str = "switching server…";
            } else {
                str = "connecting…";
            }
            LogBuffer.listener(str);
            try {
                Prefs prefs = new Prefs(tunnelVpnService);
                tunnelVpnService.f = prefs;
                Profile byId = ProfileStore.f(tunnelVpnService).getById(prefs.f343a.getString("selected_profile", ""));
                tunnelVpnService.profile = byId;
                if (byId == null) {
                    string = tunnelVpnService.getString(R.string.err_no_server);
                } else if (VpnService.prepare(tunnelVpnService) != null) {
                    string = tunnelVpnService.getString(R.string.err_no_permission);
                } else {
                    ParcelFileDescriptor c = tunnelVpnService.c(tunnelVpnService.f);
                    tunnelVpnService.tunInterface = c;
                    if (c == null) {
                        string = tunnelVpnService.getString(R.string.err_tun);
                    } else {
                        CoreManager.b().start(tunnelVpnService, tunnelVpnService.profile, tunnelVpnService.tunInterface.getFd(), new h());
                        TunnelVpnService.serviceRunning = true;
                        if (tunnelVpnService.startedAt == 0) {
                            tunnelVpnService.startedAt = System.currentTimeMillis();
                        }
                        tunnelVpnService.lastConnectAt = System.currentTimeMillis();
                        tunnelVpnService.strikes = 0;
                        tunnelVpnService.switching = false;
                        tunnelVpnService.h(tunnelVpnService.profile.remark, 2);
                        LogBuffer.listener("connected: " + tunnelVpnService.profile.remark + " (" + tunnelVpnService.profile.protocol + " " + tunnelVpnService.profile.displayAddress() + ")");
                        tunnelVpnService.updateNotification(tunnelVpnService.profile.remark, tunnelVpnService.getString(R.string.state_connected));
                        tunnelVpnService.stopStatsTicker();
                        i iVar = new i();
                        tunnelVpnService.h = iVar;
                        tunnelVpnService.handler.postDelayed(iVar, 1000L);
                        tunnelVpnService.startHealthTicker();
                        return;
                    }
                }
                tunnelVpnService.fail(string);
            } catch (XrayConfigBuilder.a e) {
                Log.e("ParvazVpn", "unsupported protocol", e);
                message = tunnelVpnService.getString(R.string.err_unsupported_protocol, e.f6218b);
                tunnelVpnService.fail(message);
            } catch (Throwable th) {
                Log.e("ParvazVpn", "connect failed", th);
                if (th.getMessage() == null) {
                    message = th.getClass().getSimpleName();
                } else {
                    message = th.getMessage();
                }
                tunnelVpnService.fail(message);
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.h to com.parvaz.tunnel.core.TunnelVpnService$h */
    /* loaded from: classes.dex */
    public class h implements Runnable {
        public h() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService.this.lambda$lambda$autoSwitch$2$2();
        }
    }

    /**
     * Stats ticker: once a second, pulls cumulative up/down counters out of the Xray
     * core, converts them into a per-second rate, broadcasts them to the UI and
     * accumulates them into the daily-usage ledger. Reschedules itself while the
     * tunnel is up.
     */
    /* renamed from: com.parvaz.tunnel.core.TunnelVpnService$i */
    /* loaded from: classes.dex */
    public class i implements Runnable {

        public i() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService svc = TunnelVpnService.this;
            if (!serviceRunning) {
                return;
            }

            // libv2ray's QueryStats() ends with `counter.Set(0)`: it returns the bytes
            // accumulated SINCE THE LAST CALL and resets the counter. The value is
            // therefore already a delta -- subtracting a previous reading (as an earlier
            // build did) drove every tick to zero, which is why the speed line stayed
            // blank. Read it once per tick and use it as-is.
            //
            // Count ONLY the "proxy" outbound. "chain" and "fragment" are dialerProxy
            // wrappers, not routing destinations -- the routing table never targets them,
            // so the same bytes are counted once by "proxy" and again by the wrapper.
            // Summing them would report double the real speed whenever a chained server
            // or fragmentation is enabled.
            long dUp = 0;
            long dDown = 0;
            try {
                CoreController controller = CoreManager.b().controller;
                if (controller != null) {
                    // One call returns every outbound counter and resets them all,
                    // which keeps the read atomic and avoids losing bytes between calls.
                    String all = controller.queryAllOutboundTrafficStats();
                    if (all != null && !all.isEmpty()) {
                        for (String entry : all.split(";")) {
                            if (entry.isEmpty()) {
                                continue;
                            }
                            String[] parts = entry.split(",");
                            if (parts.length != 3) {
                                continue;
                            }
                            if (!"proxy".equals(parts[0])) {
                                continue;   // direct/block/dns, or a dialer wrapper
                            }
                            long value;
                            try {
                                value = Long.parseLong(parts[2]);
                            } catch (NumberFormatException ignored) {
                                continue;
                            }
                            if (value <= 0) {
                                continue;
                            }
                            if ("uplink".equals(parts[1])) {
                                dUp += value;
                            } else if ("downlink".equals(parts[1])) {
                                dDown += value;
                            }
                        }
                    }
                }
            } catch (Throwable unused) {
                dUp = 0;
                dDown = 0;
            }

            svc.sessionUp += dUp;
            svc.sessionDown += dDown;
            svc.pendingDayUp += dUp;
            svc.pendingDayDown += dDown;

            Prefs prefs = svc.f;
            if (prefs != null) {
                SharedPreferences sp = prefs.f343a;
                sp.edit()
                        .putLong("data_up", sp.getLong("data_up", 0L) + dUp)
                        .putLong("data_down", sp.getLong("data_down", 0L) + dDown)
                        .apply();

                // Flush the daily ledger every 30 ticks instead of every second.
                svc.dayFlushTick++;
                if (svc.dayFlushTick >= 30) {
                    prefs.addDailyUsage(svc.pendingDayUp, svc.pendingDayDown);
                    svc.pendingDayUp = 0L;
                    svc.pendingDayDown = 0L;
                    svc.dayFlushTick = 0;
                }
            }

            long duration = svc.startedAt > 0
                    ? (System.currentTimeMillis() - svc.startedAt) / 1000L : 0L;

            Intent intent = new Intent("com.parvaz.tunnel.STATE");
            intent.setPackage(svc.getPackageName());
            intent.putExtra("state", 4);
            intent.putExtra("uplink", dUp);
            intent.putExtra("downlink", dDown);
            intent.putExtra("duration", duration);
            Profile p = svc.profile;
            if (p != null) {
                intent.putExtra("profile_id", p.id);
            }
            svc.sendBroadcast(intent);

            // Live speed in the status bar / notification shade. Rebuilding the
            // notification every second is what makes the ongoing notification show
            // "↓ 1.2 MB/s   ↑ 340 KB/s" the way earlier versions did. setOnlyAlertOnce
            // keeps it silent, and NotificationManager coalesces the updates.
            if (p != null) {
                svc.updateNotification(
                        p.remark,
                        "↓ " + fmtSpeed(dDown) + "    ↑ " + fmtSpeed(dUp));
            }

            svc.handler.postDelayed(this, 1000L);
        }
    }


    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.j to com.parvaz.tunnel.core.TunnelVpnService$j */
    /* loaded from: classes.dex */
    public class j implements Runnable {
        public j() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService.this.lambda$onCoreStopped$1();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.k to com.parvaz.tunnel.core.TunnelVpnService$k */
    /* loaded from: classes.dex */
    public class k implements Runnable {
        public k() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService tunnelVpnService = TunnelVpnService.this;
            tunnelVpnService.fail(tunnelVpnService.getString(R.string.state_disconnected));
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.l to com.parvaz.tunnel.core.TunnelVpnService$l */
    /* loaded from: classes.dex */
    public class l implements Runnable {

        /* renamed from: b */
        public final Profile f6237b;

        /* renamed from: c */
        public final String f6238c;

        public l(Profile profile, String str) {
            this.f6237b = profile;
            this.f6238c = str;
        }

        @Override // java.lang.Runnable
        public final void run() {
            int fd;
            TunnelVpnService tunnelVpnService = TunnelVpnService.this;
            Profile profile = this.f6237b;
            String str = this.f6238c;
            tunnelVpnService.stopStatsTicker();
            tunnelVpnService.stopHealthTicker();
            CoreManager.b().stop();
            try {
                Thread.sleep(300L);
            } catch (InterruptedException unused) {
                android.util.Log.w("Parvaz/TunnelVpnService", "InterruptedException ignored", unused);
            }
            try {
                tunnelVpnService.profile = profile;
                CoreManager b = CoreManager.b();
                Profile profile2 = tunnelVpnService.profile;
                ParcelFileDescriptor parcelFileDescriptor = tunnelVpnService.tunInterface;
                if (parcelFileDescriptor == null) {
                    fd = 0;
                } else {
                    fd = parcelFileDescriptor.getFd();
                }
                b.start(tunnelVpnService, profile2, fd, new a());
                tunnelVpnService.lastConnectAt = System.currentTimeMillis();
                tunnelVpnService.switching = false;
                TunnelVpnService.serviceRunning = true;
                tunnelVpnService.h(tunnelVpnService.profile.remark, 2);
                tunnelVpnService.updateNotification(tunnelVpnService.profile.remark, tunnelVpnService.getString(R.string.switched_to, str));
                tunnelVpnService.handler.post(new b());
            } catch (Throwable th) {
                Log.e("ParvazVpn", "auto-switch failed", th);
                tunnelVpnService.switching = false;
                tunnelVpnService.handler.post(new c());
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.m to com.parvaz.tunnel.core.TunnelVpnService$m */
    /* loaded from: classes.dex */
    public class m implements Runnable {

        public TunnelVpnService outer() {
            return TunnelVpnService.this;
        }

        /* renamed from: b */
        public final long f6240b;

        /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.m.a to com.parvaz.tunnel.core.TunnelVpnService$m$a */
        /* loaded from: classes.dex */
        public class a implements Runnable {
            public a() {
            }

            @Override // java.lang.Runnable
            public final void run() {
                boolean z;
                CoreController coreController;
                m mVar = m.this;
                mVar.getClass();
                if (TunnelVpnService.serviceRunning) {
                    TunnelVpnService tunnelVpnService = TunnelVpnService.this;
                    if (!tunnelVpnService.switching) {
                        CoreManager b = CoreManager.b();
                        if (b.running && (coreController = b.controller) != null && coreController.getIsRunning()) {
                            z = true;
                        } else {
                            z = false;
                        }
                        boolean z2 = !z;
                        long j = -1;
                        if (!z2) {
                            CoreManager b2 = CoreManager.b();
                            String string = tunnelVpnService.f.f343a.getString("ping_url", "https://www.gstatic.com/generate_204");
                            b2.getClass();
                            try {
                                CoreController coreController2 = b2.controller;
                                if (coreController2 != null) {
                                    j = coreController2.measureDelay(string);
                                }
                            } catch (Exception unused) {
                                android.util.Log.w("Parvaz/TunnelVpnService", "Exception ignored", unused);
                            }
                        }
                        int i = tunnelVpnService.f.f343a.getInt("ping_threshold", 1200);

                        // Real traffic is a far stronger liveness signal than a probe.
                        // The probe URL itself can be throttled or blocked while the
                        // tunnel is perfectly healthy, and MeasureDelay has a 12 s
                        // timeout on a 15 s interval, so a single slow probe used to be
                        // enough to start tearing down a working connection. If bytes
                        // moved since the last check, the tunnel is alive: clear the
                        // strikes and skip the switch entirely.
                        long movedNow = tunnelVpnService.sessionUp + tunnelVpnService.sessionDown;
                        boolean trafficMoving = movedNow > tunnelVpnService.lastHealthBytes;
                        tunnelVpnService.lastHealthBytes = movedNow;

                        if (!z2 && trafficMoving && (j < 0 || j > i)) {
                            // Core is up and data is flowing, the probe is just unhappy.
                            tunnelVpnService.strikes = 0;
                            return;
                        }

                        if (!z2 && j >= 0 && j <= i) {
                            tunnelVpnService.strikes = 0;
                            tunnelVpnService.chainedSwitches = 0;
                            if (tunnelVpnService.profile != null) {
                                ProfileStore.f(tunnelVpnService).i(tunnelVpnService.profile.id, (int) j);
                                // A healthy probe is the strongest signal this server
                                // works here and now (idea 1.1).
                                new ServerMemory(tunnelVpnService).recordSuccess(
                                        tunnelVpnService, tunnelVpnService.profile.id, (int) j);
                            }
                            Intent intent = new Intent("com.parvaz.tunnel.STATE");
                            intent.setPackage(tunnelVpnService.getPackageName());
                            intent.putExtra("state", 4);
                            intent.putExtra("ping", (int) j);
                            Profile profile = tunnelVpnService.profile;
                            if (profile != null) {
                                intent.putExtra("profile_id", profile.id);
                            }
                            tunnelVpnService.sendBroadcast(intent);
                            return;
                        }
                        tunnelVpnService.strikes++;
                        Log.w("ParvazVpn", "health strike " + tunnelVpnService.strikes + " (coreDead=" + z2 + " delay=" + j + " threshold=" + i + ")");
                        // A dead core is unambiguous -- switch at once. A merely slow or
                        // failing probe needs more evidence before we throw away a
                        // connection the user may be actively using: 3 consecutive
                        // failures with no traffic at all (~45 s).
                        int required = z2 ? 1 : tunnelVpnService.f.f343a.getInt("health_strikes", 3);
                        if (tunnelVpnService.strikes >= required) {
                            tunnelVpnService.handler.post(new TunnelVpnService_RunnableC0008AnonymousClass3_2(mVar));
                        }
                    }
                }
            }
        }

        public m(long j) {
            this.f6240b = j;
        }

        /**
         * Health probing is the service's main background cost: every tick wakes the
         * core and issues a real network request. While the screen is off the user
         * cannot see a stall anyway, so we back the interval off (default 4x) instead
         * of hammering the radio every 15 s from the user's pocket. Traffic still
         * keeps the tunnel honest via the liveness short-circuit below, and the
         * interval snaps back the moment the screen comes on.
         */
        private long currentInterval() {
            TunnelVpnService svc = TunnelVpnService.this;
            long base = this.f6240b;
            try {
                if (svc.f == null || !svc.f.f343a.getBoolean("battery_saver", true)) {
                    return base;
                }
                android.os.PowerManager pm =
                        (android.os.PowerManager) svc.getSystemService(Context.POWER_SERVICE);
                boolean screenOn = pm == null || pm.isInteractive();
                if (screenOn) {
                    return base;
                }
                int mult = svc.f.f343a.getInt("battery_idle_multiplier", 4);
                if (mult < 1) {
                    mult = 1;
                }
                if (mult > 12) {
                    mult = 12;
                }
                return base * mult;
            } catch (Throwable t) {
                return base;
            }
        }

        @Override // java.lang.Runnable
        public final void run() {
            if (TunnelVpnService.serviceRunning) {
                TunnelVpnService tunnelVpnService = TunnelVpnService.this;
                if (tunnelVpnService.switching) {
                    return;
                }
                tunnelVpnService.handler.postDelayed(this, currentInterval());
                if (System.currentTimeMillis() - tunnelVpnService.lastConnectAt < 10000) {
                    return;
                }
                new Thread(new a()).start();
            }
        }
    }

    /* renamed from: e */
    public static String fmtSpeed(long j2) {
        double d2 = j2;
        String[] strArr = {"B/s", "KB/s", "MB/s", "GB/s"};
        int i2 = 0;
        while (d2 >= 1024.0d && i2 < 3) {
            d2 /= 1024.0d;
            i2++;
        }
        return String.format(Locale.US, d2 < 10.0d ? "%.1f %s" : "%.0f %s", Double.valueOf(d2), strArr[i2]);
    }

    public final void a(VpnService.Builder builder, Prefs prefs) {
        String string = prefs.f343a.getString("per_app_mode", "off");
        LinkedHashSet c2 = prefs.c();
        if ((!"bypass".equals(string) && !"only".equals(string)) || c2.isEmpty()) {
            try {
                builder.addDisallowedApplication(getPackageName());
                return;
            } catch (Exception unused) {
                return;
            }
        }
        try {
            if ("bypass".equals(string)) {
                Iterator it = c2.iterator();
                while (it.hasNext()) {
                    String str = (String) it.next();
                    if (!str.equals(getPackageName())) {
                        try {
                            builder.addDisallowedApplication(str);
                        } catch (Exception e2) {
                            Log.w("ParvazVpn", "disallow " + str + " failed: " + e2.getMessage());
                        }
                    }
                }
                builder.addDisallowedApplication(getPackageName());
                return;
            }
            Iterator it2 = c2.iterator();
            boolean z = false;
            while (it2.hasNext()) {
                String str2 = (String) it2.next();
                if (!str2.equals(getPackageName())) {
                    try {
                        builder.addAllowedApplication(str2);
                        z = true;
                    } catch (Exception e3) {
                        Log.w("ParvazVpn", "allow " + str2 + " failed: " + e3.getMessage());
                    }
                }
            }
            if (z) {
                return;
            }
            builder.addDisallowedApplication(getPackageName());
        } catch (Exception unused2) {
            android.util.Log.w("Parvaz/TunnelVpnService", "Exception ignored", unused2);
        }
    }

    /**
     * Builds the ongoing foreground notification: title = server name, text = live
     * speeds, plus a Disconnect action wired to the STOP broadcast.
     */
    /* renamed from: b */
    public final Notification buildNotification(String str, String str2) {
        PendingIntent activity = PendingIntent.getActivity(
                this, 0, new Intent(this, (Class<?>) MainActivity.class), 201326592);
        PendingIntent broadcast = PendingIntent.getBroadcast(
                this, 1,
                new Intent("com.parvaz.tunnel.STOP").setPackage(getPackageName()),
                201326592);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setSmallIcon(R.drawable.ic_tile);
        builder.setContentTitle(str);
        builder.setContentText(str2);
        builder.setContentIntent(activity);
        builder.setOngoing(true);
        builder.setShowWhen(false);
        builder.setOnlyAlertOnce(true);
        // PRIORITY_MIN hides the notification icon from the status bar and collapses the
        // entry at the bottom of the shade, so the live speed was effectively invisible
        // "at the top of the phone". LOW keeps it silent (no sound, no heads-up) while
        // still showing the icon and the ↓/↑ line.
        builder.setPriority(NotificationCompat.PRIORITY_LOW);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE);
        builder.addAction(R.drawable.ic_tile, getString(R.string.disconnect), broadcast);
        return builder.build();
    }

    public final ParcelFileDescriptor c(Prefs prefs) {
        try {
            VpnService.Builder builder = new VpnService.Builder();
            builder.setSession(getString(R.string.app_name));
            SharedPreferences sharedPreferences = prefs.f343a;
            builder.setMtu(sharedPreferences.getInt("vpn_mtu", 1500));
            builder.addAddress("26.26.26.1", 30);
            if (sharedPreferences.getBoolean("bypass_lan", true)) {
                String[] strArr = ROUTED_IPV4;
                for (int i2 = 0; i2 < 30; i2++) {
                    String[] split = strArr[i2].split("/");
                    builder.addRoute(split[0], Integer.parseInt(split[1]));
                }
            } else {
                builder.addRoute("0.0.0.0", 0);
            }
            if (sharedPreferences.getBoolean("ipv6_enabled", false)) {
                builder.addAddress("da26:2626::1", 126);
                builder.addRoute("::", 0);
            }
            int addedDns = 0;
            String rawDns = sharedPreferences.getString("remote_dns",
                    "https://1.1.1.1/dns-query,https://dns.google/dns-query");
            for (String str : rawDns.split(",")) {
                String trim = str.trim();
                if (trim.isEmpty()) {
                    continue;
                }
                if (trim.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") || (trim.contains(":") && !trim.contains("/"))) {
                    builder.addDnsServer(trim);
                    addedDns++;
                } else if (trim.startsWith("https://") || trim.startsWith("http://") || trim.startsWith("tls://") || trim.startsWith("quic://")) {
                    try {
                        java.net.URI uri = java.net.URI.create(trim);
                        String host = uri.getHost();
                        if (host != null && (host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") || (host.contains(":") && !host.contains("/")))) {
                            builder.addDnsServer(host);
                            addedDns++;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (addedDns == 0) {
                builder.addDnsServer("1.1.1.1");
                builder.addDnsServer("8.8.8.8");
            }
            a(builder, prefs);
            if (Build.VERSION.SDK_INT >= 29) {
                builder.setMetered(false);
                try {
                    builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", 10809));
                } catch (Throwable th) {
                    Log.w("ParvazVpn", "setHttpProxy unavailable", th);
                }
            }
            builder.setConfigureIntent(PendingIntent.getActivity(this, 0, new Intent(this, (Class<?>) MainActivity.class), 201326592));
            ParcelFileDescriptor established = builder.establish();
            // Pin the tunnel to the network that is actually carrying traffic right now.
            // Without this the protected sockets keep using whatever network was the
            // system default when the tunnel came up, so a Wi-Fi -> mobile handover
            // leaves the core dialling out over a dead interface until a health strike
            // finally forces a reconnect. That is a large part of the "connection feels
            // weak / drops" reports.
            bindUnderlyingNetwork();
            return established;
        } catch (Exception e2) {
            Log.e("ParvazVpn", "establish failed", e2);
            return null;
        }
    }

    /* renamed from: d */
    public final void fail(String str) {
        boolean z;
        Log.e("ParvazVpn", "fail: " + str);
        LogBuffer.listener("ERROR: " + str);
        Prefs prefs = this.f;
        if (prefs != null && prefs.f343a.getBoolean("kill_switch", false) && this.tunInterface != null) {
            z = true;
        } else {
            z = false;
        }
        h(str, 3);
        if (z) {
            LogBuffer.listener("kill switch active - traffic blocked");
            shutdown(false, true);
            updateNotification(getString(R.string.kill_switch), getString(R.string.kill_switch_desc));
            return;
        }
        shutdown(true, false);
    }

    /* renamed from: f */
    public final void lambda$lambda$autoSwitch$2$2() {
        Handler handler;
        Runnable kVar;
        if (!serviceRunning || this.switching) {
            return;
        }
        Log.w("ParvazVpn", "core stopped unexpectedly");
        LogBuffer.listener("core stopped unexpectedly");
        Prefs prefs = this.f;
        if (prefs == null || !prefs.f343a.getBoolean("auto_switch", true)) {
            handler = this.handler;
            kVar = new k();
        } else {
            handler = this.handler;
            kVar = new j();
        }
        handler.post(kVar);
    }

    /* JADX WARN: Removed duplicated region for block: B:84:0x00ce  */
    /* JADX WARN: Removed duplicated region for block: B:90:0x00eb  */
    /* renamed from: g */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    /**
     * Picks a replacement server after the health check failed, and switches to it.
     *
     * <p>Rather than walking the list one at a time waiting out each timeout, the top
     * candidates are raced in parallel (idea 1.2) and ordered by what has actually
     * worked on this network at this hour (idea 1.1). Servers tried in the last five
     * minutes are skipped so a flapping server cannot capture the rotation, and after
     * four chained switches we stop and report failure rather than loop forever.
     */
    public final void lambda$onCoreStopped$1() {
        if (this.switching || !serviceRunning) {
            return;
        }
        if (this.chainedSwitches >= 4) {
            Log.w("ParvazVpn", "too many chained switches, giving up");
            fail(getString(R.string.no_alternative));
            return;
        }
        LogBuffer.listener("health check failed, looking for a better server");

        Profile current = this.profile;
        String currentId = current == null ? "" : current.id;

        // Remember that the current server just let us down.
        if (!currentId.isEmpty()) {
            new ServerMemory(this).recordFailure(this, currentId);
        }

        ArrayList<Profile> all = ProfileStore.f(this).e();
        long now = System.currentTimeMillis();

        ArrayList<Profile> candidates = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Profile candidate = all.get(i);
            if (candidate == null || candidate.id.equals(currentId)) {
                continue;
            }
            Long triedAt = (Long) this.p.get(candidate.id);
            if (triedAt != null && now - triedAt.longValue() < 300000) {
                continue;   // tried very recently, give it a rest
            }
            candidates.add(candidate);
        }

        // Everything is on cooldown: clear it and allow a second pass.
        if (candidates.isEmpty()) {
            this.p.clear();
            for (int i = 0; i < all.size(); i++) {
                Profile candidate = all.get(i);
                if (candidate != null && !candidate.id.equals(currentId)) {
                    candidates.add(candidate);
                }
            }
        }

        if (candidates.isEmpty()) {
            fail(getString(R.string.no_alternative));
            return;
        }

        this.switching = true;
        this.chainedSwitches++;
        h(getString(R.string.state_switching), 5);

        final ArrayList<Profile> raceCandidates = candidates;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final HappyEyeballs.Result race = HappyEyeballs.race(TunnelVpnService.this, raceCandidates);
                TunnelVpnService.this.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Profile winner = race.winner;
                        if (winner == null) {
                            TunnelVpnService.this.switching = false;
                            TunnelVpnService.this.fail(TunnelVpnService.this.getString(R.string.no_alternative));
                            return;
                        }

                        TunnelVpnService.this.p.put(winner.id, Long.valueOf(System.currentTimeMillis()));
                        ProfileStore.f(TunnelVpnService.this).i(winner.id, race.delayMs);
                        LogBuffer.listener("switching to " + winner.remark);
                        TunnelVpnService.this.handler.post(new l(winner, TunnelVpnService.this.getString(R.string.state_switching)));
                    }
                });
            }
        }, "parvaz-autoswitch").start();
    }

    public final void h(String str, int i2) {
        currentState = i2;
        Intent intent = new Intent("com.parvaz.tunnel.STATE");
        intent.setPackage(getPackageName());
        intent.putExtra("state", i2);
        if (str == null) {
            str = "";
        }
        intent.putExtra("message", str);
        sendBroadcast(intent);
        try {
            android.service.quicksettings.TileService.requestListeningState(this, new ComponentName(this, (Class<?>) TileService.class));
        } catch (Throwable unused) {
            android.util.Log.w("Parvaz/TunnelVpnService", "Throwable ignored", unused);
        }
        ParvazWidget.a(this);
    }

    /* renamed from: i */
    public final void shutdown(boolean z, boolean z2) {
        stopStatsTicker();
        stopHealthTicker();
        serviceRunning = false;
        this.switching = false;
        this.strikes = 0;
        this.chainedSwitches = 0;
        this.startedAt = 0L;
        this.sessionUp = 0L;
        this.sessionDown = 0L;
        this.lastHealthBytes = 0L;
        CoreManager.b().stop();
        if (z) {
            stopForeground(true);
            stopSelf();
        }
        if (z2) {
            return;
        }
        try {
            ParcelFileDescriptor parcelFileDescriptor = this.tunInterface;
            if (parcelFileDescriptor != null) {
                parcelFileDescriptor.close();
                this.tunInterface = null;
            }
        } catch (Exception e2) {
            Log.w("ParvazVpn", "close tun failed", e2);
        }
        h("", 0);
    }

    /* renamed from: j */
    /**
     * Tells the framework which physical network the tunnel rides on, so protected
     * sockets follow Wi-Fi/mobile handovers instead of sticking to a dead interface.
     * Passing null would mean "use the system default", which is wrong here because the
     * VPN itself becomes the default once it is up.
     */
    public final void bindUnderlyingNetwork() {
        if (Build.VERSION.SDK_INT < 22) {
            return;
        }
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }
            Network active = cm.getActiveNetwork();
            if (active == null) {
                return;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            // Never pin to our own tunnel: that would be a routing loop.
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return;
            }
            setUnderlyingNetworks(new Network[]{active});
        } catch (Throwable th) {
            Log.w("ParvazVpn", "setUnderlyingNetworks failed", th);
        }
    }

    public final void startHealthTicker() {
        stopHealthTicker();
        Prefs prefs = this.f;
        if (prefs == null || !prefs.f343a.getBoolean("auto_switch", true)) {
            return;
        }
        long max = Math.max(5, this.f.f343a.getInt("health_interval", 15)) * 1000;
        m mVar = new m(max);
        this.b = mVar;
        this.handler.postDelayed(mVar, max);
    }

    /* renamed from: k */
    public final void stopHealthTicker() {
        m mVar = this.b;
        if (mVar != null) {
            this.handler.removeCallbacks(mVar);
        }
        this.b = null;
    }

    /* renamed from: l */
    public final void stopStatsTicker() {
        i iVar = this.h;
        if (iVar != null) {
            this.handler.removeCallbacks(iVar);
        }
        this.h = null;
        Prefs prefs = this.f;
        if (prefs != null) {
            long j2 = this.pendingDayUp;
            if (j2 > 0 || this.pendingDayDown > 0) {
                prefs.addDailyUsage(j2, this.pendingDayDown);
            }
        }
        this.pendingDayUp = 0L;
        this.pendingDayDown = 0L;
        this.dayFlushTick = 0;
    }

    /* renamed from: m */
    public final void updateNotification(String str, String str2) {
        NotificationManager notificationManager = (NotificationManager) getSystemService("notification");
        if (notificationManager != null) {
            notificationManager.notify(8811, buildNotification(str, str2));
        }
    }

    @Override // android.app.Service
    public final void onCreate() {
        super.onCreate();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel b2 = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            b2.setShowBadge(false);
            b2.setLockscreenVisibility(1);
            NotificationManager notificationManager = (NotificationManager) getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(b2);
            }
        }
        ContextCompat.registerReceiver(this, this.s,
                new IntentFilter("com.parvaz.tunnel.STOP"), ContextCompat.RECEIVER_NOT_EXPORTED);
        if (this.c == null) {
            try {
                NetworkMonitor networkMonitor = new NetworkMonitor(this, new e());
                this.c = networkMonitor;
                networkMonitor.start();
            } catch (Throwable th) {
                Log.w("ParvazVpn", "network monitor unavailable", th);
            }
        }
    }

    @Override // android.app.Service
    public final void onDestroy() {
        ConnectivityManager connectivityManager;
        NetworkMonitor.a aVar;
        try {
            NetworkMonitor networkMonitor = this.c;
            if (networkMonitor != null) {
                networkMonitor.f6248c.removeCallbacks(networkMonitor.j);
                if (networkMonitor.f6251f && (connectivityManager = networkMonitor.f6249d) != null && (aVar = networkMonitor.d) != null) {
                    try {
                        connectivityManager.unregisterNetworkCallback(aVar);
                    } catch (Throwable unused) {
                        android.util.Log.w("Parvaz/TunnelVpnService", "Throwable ignored", unused);
                    }
                }
                networkMonitor.f6251f = false;
                networkMonitor.g = -1L;
                networkMonitor.f6252h = -1;
                networkMonitor.f6253i = false;
                this.c = null;
            }
        } catch (Throwable unused2) {
            android.util.Log.w("Parvaz/TunnelVpnService", "Throwable ignored", unused2);
        }
        try {
            unregisterReceiver(this.s);
        } catch (Exception unused3) {
            android.util.Log.w("Parvaz/TunnelVpnService", "Exception ignored", unused3);
        }
        shutdown(false, false);
        super.onDestroy();
    }

    @Override // android.net.VpnService
    public final void onRevoke() {
        shutdown(true, false);
        super.onRevoke();
    }

    @Override // android.app.Service
    public final int onStartCommand(Intent intent, int flags, int startId) {
        // Null intent => the system restarted us (START_STICKY) or an always-on
        // VPN profile launched us; treat that as a normal START.
        String action = (intent == null) ? "com.parvaz.tunnel.START" : intent.getAction();

        if (intent == null || intent.getAction() == null) {
            boolean lockdown = false;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    lockdown = isLockdownEnabled();
                }
            } catch (Throwable unused) {
                lockdown = false;
            }
            LogBuffer.listener("started by always-on VPN".concat(lockdown ? " (lockdown)" : ""));
        }

        if ("com.parvaz.tunnel.STOP".equals(action)) {
            shutdown(true, false);
            return START_NOT_STICKY;
        }

        // Android requires startForeground() within ~5 s of the service starting,
        // so post the placeholder notification before doing any work.
        Notification n = buildNotification(getString(R.string.state_connecting), "");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFY_ID, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFY_ID, n);
        }

        if (serviceRunning && "com.parvaz.tunnel.RESTART".equals(action)) {
            shutdown(false, false);
        }

        new Thread(new g()).start();
        return START_STICKY;
    }
}
