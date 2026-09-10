package com.parvaz.tunnel.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
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
    private String observedLinks,observedDns;
    boolean pendingDnsChange;
    static String dnsKey(LinkProperties p){
        if(p==null)return null;
        String key=p.getDnsServers().toString()+"\n"+p.getDomains();
        // TLS validation availability is transient, not a new resolver identity.
        if(android.os.Build.VERSION.SDK_INT>=28)key+="\n"+p.getPrivateDnsServerName();
        return key; // In-memory only. Never log resolver addresses or names.
    }
    private void queueCheck(){f6248c.removeCallbacks(j);f6248c.postDelayed(j,1200L);}

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.a.a to com.parvaz.tunnel.core.NetworkMonitor$C0073b */
    /* renamed from: com.parvaz.tunnel.core.a$a */
    /* loaded from: classes.dex */
    public class a extends ConnectivityManager.NetworkCallback {
        private boolean live(){return f6251f&&NetworkMonitor.this.d==this;}
        @Override public void onAvailable(Network network){
            synchronized(NetworkMonitor.this){
                if(!live()||network==null)return;
                // Capabilities can be absent at onAvailable; the following
                // onCapabilitiesChanged supplies them without assuming readiness.
                observeDefault(network,f6249d.getNetworkCapabilities(network));
            }
        }
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){
            synchronized(NetworkMonitor.this){if(live())observeDefault(network,caps);}
        }
        @Override public void onLinkPropertiesChanged(Network network,LinkProperties properties){
            synchronized(NetworkMonitor.this){
                if(!live()||properties==null||!isCurrentDefault(network,f6249d.getNetworkCapabilities(network))||network.getNetworkHandle()!=g)return;
                String next=properties.toString(),dns=dnsKey(properties);
                if(observedLinks!=null&&!observedLinks.equals(next)){
                    boolean reset=!java.util.Objects.equals(observedDns,dns);pendingDnsChange|=reset;
                    if(reset)NetworkEpoch.resolverChanged();else NetworkEpoch.changed();queueCheck();
                }
                observedLinks=next;observedDns=dns;
            }
        }
        @Override public void onLost(Network network){
            synchronized(NetworkMonitor.this){
                if(!live()||network==null||network.getNetworkHandle()!=g)return;
                Network active=f6249d.getActiveNetwork();
                NetworkCapabilities caps=active==null?null:f6249d.getNetworkCapabilities(active);
                // Own/other VPN notifications are not proof that the physical
                // transport was lost. Do not restart a just-created tunnel for it.
                if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return;
                f6253i=true;NetworkEpoch.changed();
                if(b!=null&&TunnelVpnService.serviceRunning){
                    LogBuffer.listener("default network lost - waiting");
                    TunnelVpnService service=b.outer();
                    service.updateNotification(service.getString(R.string.state_connecting),service.getString(R.string.network_lost));
                }
            }
        }
    }

    private boolean isCurrentDefault(Network network,NetworkCapabilities caps){
        return network!=null&&f6249d!=null&&network.equals(f6249d.getActiveNetwork())&&caps!=null
            &&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            &&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }
    private void observeDefault(Network network,NetworkCapabilities caps){
        if(!isCurrentDefault(network,caps))return;
        long handle=network.getNetworkHandle();boolean changed=g!=-1&&g!=handle;
        if(g!=handle){
            LinkProperties next=f6249d.getLinkProperties(network);
            String dns=dnsKey(next);
            boolean reset=changed&&(observedDns==null||dns==null||!observedDns.equals(dns));
            pendingDnsChange|=reset;observedDns=dns;observedLinks=next==null?null:next.toString();
            if(reset)NetworkEpoch.resolverChanged();else if(changed)NetworkEpoch.changed();
        }
        g=handle;
        f6252h=caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?1:
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?0:
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?3:-1;
        if(changed||f6253i){
            f6253i=false;queueCheck();
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
            NetworkMonitor monitor=NetworkMonitor.this;
            synchronized(monitor){
            if(!monitor.f6251f||monitor.f6249d==null)return;
            Network active=monitor.f6249d.getActiveNetwork();
            NetworkCapabilities caps=active==null?null:monitor.f6249d.getNetworkCapabilities(active);
            if(!monitor.isCurrentDefault(active,caps)||active.getNetworkHandle()!=monitor.g)return;
            c cVar = monitor.b;
            if (cVar != null && TunnelVpnService.serviceRunning) {
                TunnelVpnService tunnelVpnService = cVar.outer();
                if(tunnelVpnService.switching){monitor.queueCheck();return;}
                if (!tunnelVpnService.switching) {
                    LogBuffer.listener("Network changed; checking live transport before restarting");
                    if (tunnelVpnService.profile != null && !tunnelVpnService.switching) {
                        boolean resetDns=monitor.pendingDnsChange;monitor.pendingDnsChange=false;
                        tunnelVpnService.verifyHandover(cVar.newReconnect(),resetDns);
                    }
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
    public final synchronized void start() {
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
            this.f6251f = true;
            // The previous broad INTERNET request observed every matching network,
            // so an idle cellular interface could tear down a working Wi-Fi tunnel.
            this.f6249d.registerDefaultNetworkCallback(this.d);
        } catch (Throwable unused) {
            this.f6251f = false;
        }
    }

    /* renamed from: b */
    public final synchronized void stop() {
        this.f6248c.removeCallbacks(this.j);
        if (this.f6251f && this.f6249d != null && this.d != null) {
            try {
                this.f6249d.unregisterNetworkCallback(this.d);
            } catch (Throwable ignored) {
            }
        }
        this.f6251f = false;
        this.g = -1L;this.observedLinks=null;this.observedDns=null;this.pendingDnsChange=false;
        this.f6252h = -1;
        this.f6253i = false;
    }
}
