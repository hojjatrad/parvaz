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
    public long dayFlushAtElapsed;
    final SerialConnectionQueue operations=new SerialConnectionQueue();
    public volatile m b;
    private long nextAutoSearchAt,lastQualitySearch,lastSlowSample;
    private int failedSearches,slowSamples;
    private final java.util.concurrent.atomic.AtomicBoolean healthProbeBusy=new java.util.concurrent.atomic.AtomicBoolean();
    public NetworkMonitor c;

    /* renamed from: d */
    public long pendingDayDown;

    /* renamed from: e */
    public long pendingDayUp;
    public Prefs f;

    /* renamed from: g */
    public volatile Profile profile;
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
    public volatile long sessionDown = 0;

    /** Received proxy byte total at the previous health check (not application-specific). */
    public volatile long lastHealthBytes = 0;
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
            tunnelVpnService.handler.postDelayed(iVar, TrafficSampling.FAST_INTERVAL_MS);
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
        private final long requestedFrom=operations.ticket();

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
                tunnelVpnService.handler.postDelayed(iVar, TrafficSampling.FAST_INTERVAL_MS);
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

        @Override public final void run(){operations.replace(requestedFrom,this::runOwned);}
        private void runOwned(long ticket) {
            if(!operations.current(ticket))return;
            TunnelVpnService tunnelVpnService = TunnelVpnService.this;
            try {
                Prefs latest=new Prefs(tunnelVpnService);
                Profile selected=ProfileStore.f(tunnelVpnService).getActiveById(latest.f343a.getString("selected_profile",""));
                if(selected==null)throw new IllegalStateException("NO_ACTIVE_PROFILE");
                tunnelVpnService.f=latest;tunnelVpnService.profile=selected;
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
                if(!operations.current(ticket))return;
                ParcelFileDescriptor parcelFileDescriptor = tunnelVpnService.tunInterface;
                CoreManager.b().start(tunnelVpnService, tunnelVpnService.profile, parcelFileDescriptor == null ? 0 : parcelFileDescriptor.getFd(), ()->postCoreFailure(ticket));
                operations.commit(ticket,()->{
                tunnelVpnService.lastConnectAt = System.currentTimeMillis();
                tunnelVpnService.switching = false;
                TunnelVpnService.serviceRunning = true;
                tunnelVpnService.h(tunnelVpnService.profile.remark, 2);
                tunnelVpnService.updateNotification(tunnelVpnService.profile.remark, tunnelVpnService.getString(StartupDiagnostics.labelResource()));
                postState(ticket,new b());
                });
                if(!operations.current(ticket))CoreManager.b().stop();
            } catch (Throwable th) {
                postFailure(ticket,getString(R.string.no_alternative));
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.TunnelVpnService.g to com.parvaz.tunnel.core.TunnelVpnService$g */
    /* loaded from: classes.dex */
    public class g implements Runnable {
        public g() {
        }

        @Override public final void run(){operations.start(false,this::runOwned);}
        private void runOwned(long ticket) {
            if(!operations.current(ticket))return;
            shutdownInternal(false,true); // Preserve the blocking TUN until its replacement exists.
            if(!operations.current(ticket))return;
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
                ProfileStore.f(tunnelVpnService).ensureActiveSubscription(prefs.f343a);
                Profile byId = ProfileStore.f(tunnelVpnService).getActiveById(prefs.f343a.getString("selected_profile", ""));
                tunnelVpnService.profile = byId;
                if (byId == null) {
                    string = tunnelVpnService.getString(R.string.err_no_server);
                } else if (VpnService.prepare(tunnelVpnService) != null) {
                    string = tunnelVpnService.getString(R.string.err_no_permission);
                } else {
                    long tunBegan=System.nanoTime();
                    ParcelFileDescriptor c = tunnelVpnService.c(tunnelVpnService.f);
                    long tunSetupMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-tunBegan);
                    if(c!=null){
                        ParcelFileDescriptor previous=tunnelVpnService.tunInterface;tunnelVpnService.tunInterface=c;
                        if(previous!=null&&previous!=c)try{previous.close();}catch(Exception ignored){}
                    }
                    if (c == null) {
                        string = tunnelVpnService.getString(R.string.err_tun);
                    } else {
                        if(!operations.current(ticket))return;
                        CoreManager.b().start(tunnelVpnService, tunnelVpnService.profile, tunnelVpnService.tunInterface.getFd(), ()->postCoreFailure(ticket),tunSetupMs);
                        operations.commit(ticket,()->{
                        TunnelVpnService.serviceRunning = true;
                        // LAN sharing is explicitly enabled per session from Settings.
                        HotspotProxyManager.stop();
                        if (tunnelVpnService.startedAt == 0) {
                            tunnelVpnService.startedAt = System.currentTimeMillis();
                        }
                        tunnelVpnService.lastConnectAt = System.currentTimeMillis();
                        tunnelVpnService.strikes = 0;
                        tunnelVpnService.switching = false;
                        tunnelVpnService.h(tunnelVpnService.profile.remark, 2);
                        LogBuffer.listener("connected: " + tunnelVpnService.profile.remark + " (" + tunnelVpnService.profile.protocol + " " + tunnelVpnService.profile.displayAddress() + ")");
                        tunnelVpnService.updateNotification(tunnelVpnService.profile.remark, tunnelVpnService.getString(StartupDiagnostics.labelResource()));
                        tunnelVpnService.stopStatsTicker();
                        i iVar = new i();
                        tunnelVpnService.h = iVar;
                        tunnelVpnService.handler.postDelayed(iVar, TrafficSampling.FAST_INTERVAL_MS);
                        tunnelVpnService.startHealthTicker();
                        });
                        if(!operations.current(ticket))CoreManager.b().stop();
                        return;
                    }
                }
                failOwned(ticket,string);
            } catch (XrayConfigBuilder.a e) {
                Log.e("ParvazVpn", "unsupported protocol", e);
                message = tunnelVpnService.getString(R.string.err_unsupported_protocol, e.f6218b);
                failOwned(ticket,message);
            } catch (Throwable th) {
                Log.e("ParvazVpn", "connect failed", th);
                if (th.getMessage() == null) {
                    message = th.getClass().getSimpleName();
                } else {
                    message = th.getMessage();
                }
                failOwned(ticket,message);
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
        private final TrafficSampling sampling;
        private final StartupDiagnostics.Attempt trace=CoreManager.b().startupAttempt();
        private int lastReadinessLabel;
        public i() {
            long now=android.os.SystemClock.elapsedRealtime();
            sampling=new TrafficSampling(now);
            TunnelVpnService.this.dayFlushAtElapsed=now;
        }

        @Override // java.lang.Runnable
        public final void run() {
            TunnelVpnService svc = TunnelVpnService.this;
            if (!serviceRunning || svc.h!=this) {
                return; // A queued tick from a replaced session must not reset its counters.
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

            if(trace!=null)trace.received(dDown);
            long sampleAt=android.os.SystemClock.elapsedRealtime();
            TrafficSampling.Sample rates=sampling.sample(sampleAt,dUp,dDown);
            svc.sessionUp += dUp;
            svc.sessionDown += dDown;
            svc.pendingDayUp += dUp;
            svc.pendingDayDown += dDown;

            Prefs prefs = svc.f;
            if (prefs != null) {
                SharedPreferences sp = prefs.f343a;
                if(dUp>0||dDown>0)sp.edit()
                        .putLong("data_up", sp.getLong("data_up", 0L) + dUp)
                        .putLong("data_down", sp.getLong("data_down", 0L) + dDown)
                        .apply();

                // Wall time, not tick count: startup samples arrive four times faster.
                if (sampleAt-svc.dayFlushAtElapsed >= 30000L) {
                    prefs.addDailyUsage(svc.pendingDayUp, svc.pendingDayDown);
                    svc.pendingDayUp = 0L;
                    svc.pendingDayDown = 0L;
                    svc.dayFlushAtElapsed = sampleAt;
                }
            }

            long duration = svc.startedAt > 0
                    ? (System.currentTimeMillis() - svc.startedAt) / 1000L : 0L;

            Intent intent = new Intent("com.parvaz.tunnel.STATE");
            intent.setPackage(svc.getPackageName());
            intent.putExtra("state", 4);
            intent.putExtra("uplink", rates.upPerSecond);
            intent.putExtra("downlink", rates.downPerSecond);
            intent.putExtra("refresh_quota", rates.updateNotification);
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
            if (p != null && rates.updateNotification) {
                svc.updateNotification(
                        p.remark,
                        svc.getString(StartupDiagnostics.labelResource())+" · ↓ " + fmtSpeed(rates.downPerSecond) + "    ↑ " + fmtSpeed(rates.upPerSecond));
                int label=StartupDiagnostics.labelResource();
                if(label!=lastReadinessLabel){
                    lastReadinessLabel=label;ParvazWidget.a(svc);
                    try{android.service.quicksettings.TileService.requestListeningState(svc,new ComponentName(svc,TileService.class));}catch(Exception ignored){}
                }
            }

            if(serviceRunning&&svc.h==this)svc.handler.postDelayed(this, rates.nextDelayMs);
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
        private final long requestedFrom=operations.ticket();

        /* renamed from: b */
        public final Profile f6237b;

        /* renamed from: c */
        public final String f6238c;

        public l(Profile profile, String str) {
            this.f6237b = profile;
            this.f6238c = str;
        }

        @Override public final void run(){operations.replace(requestedFrom,this::runOwned);}
        private void runOwned(long ticket) {
            if(!operations.current(ticket))return;
            if(HappyEyeballs.activeCandidates(java.util.Collections.singletonList(this.f6237b),ProfileStore.f(TunnelVpnService.this).activeProfiles()).isEmpty()){
                if(CoreManager.b().running)operations.commit(ticket,()->{switching=false;h("",2);startHealthTicker();});
                else failOwned(ticket,getString(R.string.no_alternative));
                return;
            }
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
                if(!operations.current(ticket))return;
                b.start(tunnelVpnService,profile2,fd,()->postCoreFailure(ticket));
                operations.commit(ticket,()->{
                if(tunnelVpnService.f!=null)tunnelVpnService.f.f343a.edit().putString("selected_profile",profile.id).apply();
                tunnelVpnService.lastConnectAt = System.currentTimeMillis();
                tunnelVpnService.switching = false;
                TunnelVpnService.serviceRunning = true;
                tunnelVpnService.h(tunnelVpnService.profile.remark, 2);
                tunnelVpnService.updateNotification(tunnelVpnService.profile.remark, tunnelVpnService.getString(StartupDiagnostics.labelResource()));
                postState(ticket,new b());
                });
                if(!operations.current(ticket))CoreManager.b().stop();
            } catch (Throwable th) {
                postFailure(ticket,getString(R.string.no_alternative));
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
        private final long ownerTicket=operations.ticket();
        private final long ownerSession=CoreManager.b().sessionId();
        private final long beganElapsed=android.os.SystemClock.elapsedRealtime();
        boolean isCurrent(){return operations.current(ownerTicket)&&TunnelVpnService.this.b==this&&serviceRunning&&!switching&&CoreManager.b().sessionId()==ownerSession;}
        public class a implements Runnable {
            @Override public void run(){
                boolean handedOff=false;
                try{
                    if(!m.this.isCurrent())return;
                    CoreManager manager=CoreManager.b();
                    // Observe the volatile publication flag before reading its controller.
                    boolean published=manager.running;CoreController controller=manager.controller;
                    final boolean alive=published&&controller!=null&&controller.getIsRunning();
                    final boolean trafficBefore=sessionDown>lastHealthBytes;
                    long delay=VerifiedProbe.UNKNOWN;
                    // Do not spend a probe/radio wakeup when replies already arrived.
                    if(alive&&(!trafficBefore||strikes>0)){
                        try{delay=VerifiedProbe.measure(manager.verifiedPort(ownerSession),f.f343a.getString("ping_url","https://www.gstatic.com/generate_204"));}
                        catch(Exception ignored){/* Endpoint/credential-free diagnostics. */}
                    }
                    final long measured=delay;
                    handedOff=handler.post(()->{
                        try{
                            final boolean[] restart={false},quality={false};
                            operations.commit(ownerTicket,()->{
                            if(!m.this.isCurrent()||profile==null||!com.parvaz.tunnel.store.ProfileIdentity.fingerprint(profile).equals(manager.liveIdentity(ownerSession))||HappyEyeballs.activeCandidates(java.util.Collections.singletonList(profile),ProfileStore.f(TunnelVpnService.this).activeProfiles()).isEmpty())return;
                            long receivedNow=sessionDown;boolean received=trafficBefore&&measured==VerifiedProbe.UNKNOWN;lastHealthBytes=receivedNow;
                            int threshold=f.f343a.getInt("ping_threshold",1200);
                            HealthPolicy.Decision decision=HealthPolicy.evaluate(alive,received,measured,threshold,strikes,f.f343a.getInt("health_strikes",3));
                            strikes=decision.strikes;
                            if(received||measured>0){chainedSwitches=0;failedSearches=0;}
                            long sampleNow=android.os.SystemClock.elapsedRealtime();
                            if(sampleNow-lastSlowSample>120000)slowSamples=0;
                            if(measured>0){slowSamples=measured>threshold?Math.min(100,slowSamples+1):0;lastSlowSample=sampleNow;}
                            else if(measured==-1)slowSamples=0;
                            quality[0]=measured>threshold&&SwitchPolicy.qualityDue(sampleNow,beganElapsed,lastQualitySearch,slowSamples,trafficBefore);
                            if(alive&&measured>0&&profile!=null){
                                manager.acceptVerifiedHealth(ownerSession,measured);
                                ProfileStore.f(TunnelVpnService.this).i(profile.id,(int)measured);
                                new ServerMemory(TunnelVpnService.this).recordSuccess(TunnelVpnService.this,profile,(int)measured);
                                Intent intent=new Intent("com.parvaz.tunnel.STATE");intent.setPackage(getPackageName());
                                intent.putExtra("state",4);intent.putExtra("ping",(int)measured);intent.putExtra("profile_id",profile.id);sendBroadcast(intent);
                            }
                            restart[0]=decision.restart;
                            if(decision.restart)manager.markUnconfirmed(ownerSession);
                            });
                            if(restart[0])autoSwitchOwned(ownerTicket);
                            else if(quality[0])searchReplacement(ownerTicket,true,measured);
                        }finally{healthProbeBusy.set(false);}
                    });
                }catch(Exception ignored){/* Stopped/replaced core: leave the newer session untouched. */}
                finally{if(!handedOff)healthProbeBusy.set(false);}
            }
        }

        public m(long j) {
            this.f6240b = j;
        }

        /**
         * Health probing is the service's main background cost: every tick wakes the
         * core unless received traffic already provides liveness. While the screen is
         * off we back the interval off (default 4x) instead
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

        @Override public final void run(){
            if(!isCurrent())return;
            handler.postDelayed(this,currentInterval());
            if(android.os.SystemClock.elapsedRealtime()-beganElapsed<10000||!healthProbeBusy.compareAndSet(false,true))return;
            try{new Thread(new a(),"parvaz-health").start();}
            catch(RuntimeException failure){healthProbeBusy.set(false);throw failure;}
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
        PendingIntent nextServer = PendingIntent.getService(
                this, 2,
                new Intent(this, TunnelVpnService.class).setAction("com.parvaz.tunnel.SWITCH_NEXT"),
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
        builder.addAction(R.drawable.ic_sort, getString(R.string.shortcut_switch_short), nextServer);
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
    private void postState(long ticket,Runnable update){handler.post(()->operations.commit(ticket,update));}
    private void postCoreFailure(long ticket){handler.post(()->{if(operations.current(ticket))coreStoppedOwned(ticket);});}
    private void postFailure(long ticket,String message){handler.post(()->failOwned(ticket,message));}
    private void failOwned(long ticket,String message){operations.stopIfCurrent(ticket,()->failInternal(message));}
    public final void fail(String str) {operations.stop(()->failInternal(str));}
    private void failInternal(String str) {
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
            shutdownInternal(false,true);
            updateNotification(getString(R.string.kill_switch), getString(R.string.kill_switch_desc));
            return;
        }
        shutdownInternal(true,false);
    }

    /* renamed from: f */
    public final void lambda$lambda$autoSwitch$2$2(){coreStoppedOwned(operations.ticket());}
    private void coreStoppedOwned(long ticket){
        if(!operations.current(ticket)||!serviceRunning||switching)return;
        CoreManager.b().markUnconfirmed(CoreManager.b().sessionId());
        if(f==null||!f.f343a.getBoolean("auto_switch",true))postFailure(ticket,getString(R.string.state_disconnected));
        else handler.post(()->{if(operations.current(ticket))autoSwitchOwned(ticket);});
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
    public final void lambda$onCoreStopped$1(){autoSwitchOwned(operations.ticket());}
    private void autoSwitchOwned(long ticket) {searchReplacement(ticket,false,-1);}
    private void searchReplacement(long ticket,boolean qualityOnly,long currentDelay) {
        if(f==null||!f.f343a.getBoolean("auto_switch",true))return;
        if(!operations.current(ticket))return;
        if (this.switching || !serviceRunning) {
            return;
        }
        if(!PhysicalNetwork.available(this))return;
        long elapsedNow=android.os.SystemClock.elapsedRealtime();
        if(elapsedNow<nextAutoSearchAt)return;
        nextAutoSearchAt=elapsedNow+SwitchPolicy.backoff(++failedSearches);
        if(qualityOnly)lastQualitySearch=elapsedNow;
        LogBuffer.listener(qualityOnly?"Sustained slow responses; comparing verified alternatives":"Repeated failures; comparing verified alternatives");

        Profile current = this.profile;
        String currentId = current == null ? "" : current.id;

        // Remember that the current server just let us down.
        if (!qualityOnly&&!currentId.isEmpty()) {
            new ServerMemory(this).recordFailure(this, current);
        }

        ArrayList<Profile> all = ProfileStore.f(this).activeProfiles();
        long now = android.os.SystemClock.elapsedRealtime();

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

        // Never clear cooldowns to force a flapping server back into rotation.
        if(candidates.isEmpty()){
            if(!qualityOnly&&!CoreManager.b().running&&current!=null&&
                !HappyEyeballs.activeCandidates(java.util.Collections.singletonList(current),all).isEmpty())new l(current,getString(R.string.state_switching)).run();
            return; // Preserve a usable tunnel; next health tick observes backoff.
        }

        final long ownerSession=CoreManager.b().sessionId();
        final int previousState=currentState;
        final java.util.function.BooleanSupplier owns=()->operations.current(ticket)&&serviceRunning&&this.profile==current&&CoreManager.b().sessionId()==ownerSession;
        if(!operations.commit(ticket,()->{
            this.switching = true;this.chainedSwitches++;
            h(getString(R.string.state_switching),5);
        }))return;

        final ArrayList<Profile> raceCandidates = candidates;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final HappyEyeballs.Result race = HappyEyeballs.race(TunnelVpnService.this,raceCandidates,HappyEyeballs.DEFAULT_PARALLEL,owns);
                TunnelVpnService.this.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(!owns.getAsBoolean())return;
                        operations.commit(ticket,()->{for(Profile failed:race.failed)TunnelVpnService.this.p.put(failed.id,Long.valueOf(android.os.SystemClock.elapsedRealtime()));});
                        Profile winner = race.winner;
                        if(race.cancelled||race.deferred||(winner!=null&&HappyEyeballs.activeCandidates(java.util.Collections.singletonList(winner),ProfileStore.f(TunnelVpnService.this).activeProfiles()).isEmpty())){
                            operations.commit(ticket,()->{
                                TunnelVpnService.this.switching=false;
                                TunnelVpnService.this.chainedSwitches=Math.max(0,TunnelVpnService.this.chainedSwitches-1);
                                TunnelVpnService.this.h("",previousState);
                                TunnelVpnService.this.startHealthTicker();
                            });
                            return;
                        }
                        if(winner==null||(qualityOnly&&!SwitchPolicy.better(currentDelay,race.delayMs))){
                            operations.commit(ticket,()->{switching=false;h("",previousState);startHealthTicker();});
                            if(winner==null&&!qualityOnly&&!CoreManager.b().running&&current!=null)new l(current,getString(R.string.state_switching)).run();
                            return;
                        }

                        operations.commit(ticket,()->{
                            TunnelVpnService.this.p.put(winner.id,Long.valueOf(android.os.SystemClock.elapsedRealtime()));
                            ProfileStore.f(TunnelVpnService.this).i(winner.id,race.delayMs);
                            LogBuffer.listener("Switching to a repeatedly verified active-source candidate");
                            new l(winner,TunnelVpnService.this.getString(R.string.state_switching)).run();
                        });
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
    public final void shutdown(boolean z,boolean z2){operations.stop(()->shutdownInternal(z,z2));}
    private void shutdownInternal(boolean z, boolean z2) {
        HotspotProxyManager.stop();
        stopStatsTicker();
        stopHealthTicker();
        serviceRunning = false;
        this.switching = false;
        this.strikes = 0;
        this.chainedSwitches = 0;
        this.failedSearches=0;this.nextAutoSearchAt=0;this.lastQualitySearch=0;this.slowSamples=0;
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

    private final java.util.concurrent.atomic.AtomicBoolean handoverBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private long handoverRevision;
    void verifyHandover(Runnable reconnect){
        final long revision=++handoverRevision,ticket=operations.ticket(),session=CoreManager.b().sessionId();
        bindUnderlyingNetwork();
        if(CoreManager.b().verifiedPort(session)<=0){switching=true;new Thread(reconnect,"parvaz-handover-recovery").start();return;}
        if(!handoverBusy.compareAndSet(false,true)){switching=true;new Thread(reconnect,"parvaz-handover-recovery").start();return;}
        new Thread(()->{
            long delay=-1;
            try{delay=VerifiedProbe.measure(CoreManager.b().verifiedPort(session),f.f343a.getString("ping_url",ReadinessMonitor.GOOGLE));}
            catch(Exception ignored){}
            final boolean responded=delay>0;
            handler.post(()->{
                handoverBusy.set(false);
                if(revision!=handoverRevision||!operations.current(ticket)||!serviceRunning||switching||CoreManager.b().sessionId()!=session)return;
                if(responded){strikes=0;LogBuffer.listener("Transport recovered; keeping live core and its DNS cache");return;}
                switching=true;new Thread(reconnect,"parvaz-handover-recovery").start();
            });
        },"parvaz-handover-check").start();
    }

    public final void startHealthTicker() {
        stopHealthTicker();
        Prefs prefs = this.f;
        if (prefs == null) {
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
        this.dayFlushAtElapsed = 0L;
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
        try {
            if (this.c != null) {
                this.c.stop();
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
        operations.close();
        super.onDestroy();
    }

    @Override // android.net.VpnService
    public final void onRevoke() {
        shutdown(true, false);
        super.onRevoke();
    }

    public final void switchNextServer() {
        if (this.switching||!serviceRunning) return;
        ArrayList<Profile> all = ProfileStore.f(this).activeProfiles();
        if (all.size() < 2) return;
        String curId = this.profile != null ? this.profile.id : "";
        int idx = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(curId)) {
                idx = (i + 1) % all.size();
                break;
            }
        }
        Profile next = all.get(idx);
        this.switching = true;
        if (this.f != null) {
            this.f.f343a.edit().putString("selected_profile", next.id).apply();
        }
        LogBuffer.listener("switching to " + next.remark);
        this.handler.post(new l(next, getString(R.string.state_switching)));
    }

    @Override // android.app.Service
    public final int onStartCommand(Intent intent, int flags, int startId) {
        try{ProfileStore.recoverBeforeUse(this);}catch(RuntimeException unavailable){
            Log.w("ParvazVpn","start: incomplete backup recovery, stopping");stopSelf();return START_NOT_STICKY;
        }
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

        if ("com.parvaz.tunnel.SWITCH_NEXT".equals(action)) {
            switchNextServer();
            return START_STICKY;
        }

        if(!"com.parvaz.tunnel.RESTART".equals(action)&&operations.current(operations.ticket()))return START_STICKY;

        // Android requires startForeground() within ~5 s of the service starting,
        // so post the placeholder notification before doing any work.
        Notification n = buildNotification(getString(R.string.state_connecting), "");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFY_ID, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFY_ID, n);
        }

        operations.start("com.parvaz.tunnel.RESTART".equals(action),ticket->new g().runOwned(ticket));
        return START_STICKY;
    }
}
