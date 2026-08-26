package com.parvaz.tunnel.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import com.parvaz.tunnel.core.LogBuffer;
import com.parvaz.tunnel.core.TunnelVpnService;
import com.parvaz.tunnel.R;

/* renamed from: com.parvaz.tunnel.core.a */
/* loaded from: classes.dex */
public final class NetworkMonitor {

    /* renamed from: a */
    public final Context f6246a;
    public final c b;

    /* renamed from: c */
    public ConnectivityManager f6249d;
    public a d;

    /* renamed from: e */
    public final Handler f6248c = new Handler(Looper.getMainLooper());

    /* renamed from: f */
    public boolean f6251f = false;
    public long g = -1;

    /* renamed from: h */
    public int f6252h = -1;

    /* renamed from: i */
    public boolean f6253i = false;
    public final b j = new b();

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.a.a to com.parvaz.tunnel.core.NetworkMonitor$C0073b */
    /* renamed from: com.parvaz.tunnel.core.a$a */
    /* loaded from: classes.dex */
    public class a extends ConnectivityManager.NetworkCallback {
        public a() {
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public final void onAvailable(Network network) {
            long networkHandle;
            boolean z;
            NetworkMonitor networkMonitor = NetworkMonitor.this;
            networkMonitor.getClass();
            if (network == null) {
                networkHandle = -1;
            } else {
                networkHandle = network.getNetworkHandle();
            }
            long j = networkMonitor.g;
            if (j != -1 && networkHandle != j) {
                z = true;
            } else {
                z = false;
            }
            networkMonitor.g = networkHandle;
            if (z || networkMonitor.f6253i) {
                networkMonitor.f6253i = false;
                Handler handler = networkMonitor.f6248c;
                b bVar = networkMonitor.j;
                handler.removeCallbacks(bVar);
                handler.postDelayed(bVar, 1200L);
            }
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public final void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            long networkHandle;
            if (networkCapabilities != null && networkCapabilities.hasCapability(12)) {
                int i = 1;
                if (!networkCapabilities.hasTransport(1)) {
                    i = 0;
                    if (!networkCapabilities.hasTransport(0)) {
                        i = 3;
                        if (!networkCapabilities.hasTransport(3)) {
                            i = -1;
                        }
                    }
                }
                if (network == null) {
                    networkHandle = -1;
                } else {
                    networkHandle = network.getNetworkHandle();
                }
                NetworkMonitor networkMonitor = NetworkMonitor.this;
                int i2 = networkMonitor.f6252h;
                if (i2 != -1 && i != -1 && i != i2) {
                    networkMonitor.f6252h = i;
                    networkMonitor.g = networkHandle;
                    Handler handler = networkMonitor.f6248c;
                    b bVar = networkMonitor.j;
                    handler.removeCallbacks(bVar);
                    handler.postDelayed(bVar, 1200L);
                    return;
                }
                networkMonitor.f6252h = i;
            }
        }

        @Override // android.net.ConnectivityManager.NetworkCallback
        public final void onLost(Network network) {
            long networkHandle;
            if (network == null) {
                networkHandle = -1;
            } else {
                networkHandle = network.getNetworkHandle();
            }
            NetworkMonitor networkMonitor = NetworkMonitor.this;
            if (networkHandle == networkMonitor.g) {
                networkMonitor.f6253i = true;
                c cVar = networkMonitor.b;
                if (cVar != null && TunnelVpnService.serviceRunning) {
                    LogBuffer.listener("network lost - waiting");
                    TunnelVpnService tunnelVpnService = cVar.outer();
                    tunnelVpnService.updateNotification(tunnelVpnService.getString(R.string.state_connecting), tunnelVpnService.getString(R.string.network_lost));
                }
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.a.b to com.parvaz.tunnel.core.NetworkMonitor$a */
    /* renamed from: com.parvaz.tunnel.core.a$b */
    /* loaded from: classes.dex */
    public class b implements Runnable {
        public b() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            c cVar = NetworkMonitor.this.b;
            if (cVar != null && TunnelVpnService.serviceRunning) {
                TunnelVpnService tunnelVpnService = cVar.outer();
                if (!tunnelVpnService.switching) {
                    LogBuffer.listener("network changed - reconnecting");
                    if (tunnelVpnService.profile != null && !tunnelVpnService.switching) {
                        tunnelVpnService.switching = true;
                        new Thread(cVar.newReconnect(), "net-reconnect").start();
                    }
                }
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.a.c to com.parvaz.tunnel.core.NetworkMonitor$c */
    /* renamed from: com.parvaz.tunnel.core.a$c */
    /* loaded from: classes.dex */
    /**
     * Implemented by TunnelVpnService so the monitor can reach it after R8 flattened
     * the inner class. newReconnect() hands back a fresh reconnect Runnable bound to
     * the right service instance.
     */
    public interface c {
        TunnelVpnService outer();

        Runnable newReconnect();
    }

    public NetworkMonitor(Context context, TunnelVpnService.e eVar) {
        this.f6246a = context.getApplicationContext();
        this.b = eVar;
    }

    /* renamed from: a */
    public final void start() {
        if (this.f6251f) {
            return;
        }
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) this.f6246a.getSystemService("connectivity");
            this.f6249d = connectivityManager;
            if (connectivityManager == null) {
                return;
            }
            this.d = new a();
            this.f6249d.registerNetworkCallback(new NetworkRequest.Builder().addCapability(12).build(), this.d);
            this.f6251f = true;
        } catch (Throwable unused) {
            this.f6251f = false;
        }
    }

    /* renamed from: b */
    public final void stop() {
        this.f6248c.removeCallbacks(this.j);
        if (this.f6251f && this.f6249d != null && this.d != null) {
            try {
                this.f6249d.unregisterNetworkCallback(this.d);
            } catch (Throwable ignored) {
            }
        }
        this.f6251f = false;
        this.g = -1L;
        this.f6252h = -1;
        this.f6253i = false;
    }
}
