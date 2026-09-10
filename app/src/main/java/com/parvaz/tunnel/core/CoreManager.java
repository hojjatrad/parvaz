package com.parvaz.tunnel.core;

import android.content.Context;
import android.util.Log;
import com.parvaz.tunnel.config.XrayConfigBuilder;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.store.ProfileStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

/* renamed from: R1.a */
/* loaded from: classes.dex */
public final class CoreManager {
    public static CoreManager c;

    /* renamed from: a */
    public CoreController controller;

    /* renamed from: b */
    public volatile boolean running = false;
    private ExternalCore external;
    private final ReadinessMonitor startupWarmup=new ReadinessMonitor();
    private volatile long generation;
    private volatile int verifiedPort;
    private int activeTunFd;
    private volatile String liveIdentity="";
    String liveIdentity(long owner){return owner==generation?liveIdentity:"";}
    synchronized void startIsolatedProbe(CoreController probe,String config)throws Exception {
        // The wrapper writes a process-global TUN fd even for TUN-less probes.
        // Preserve the active value and serialize with real core startup.
        probe.startLoop(config,activeTunFd);
    }
    synchronized void markUnconfirmed(long owner){if(owner==generation&&diagnostics!=null)diagnostics.unconfirmed();}
    synchronized void acceptVerifiedHealth(long owner,long delay){if(owner==generation&&running&&verifiedPort>0&&delay>0&&diagnostics!=null){diagnostics.probeFinished(true,delay);startupWarmup.confirmedExternally();}}
    int verifiedPort(long owner){return owner==generation&&running?verifiedPort:0;}
    private volatile StartupDiagnostics.Attempt diagnostics;
    StartupDiagnostics.Attempt startupAttempt(){return diagnostics;}

    /* JADX WARN: Can't change package for inner class: R1.a.a to com.parvaz.tunnel.core.CoreManager$1 */
    /* renamed from: R1.a$a */
    /* loaded from: classes.dex */
    public class a implements CoreCallbackHandler {

        /* renamed from: b */
        public final /* synthetic */ Runnable val$runnable;
        private final long ownerGeneration = CoreManager.this.generation;

        public a(Runnable runnable) {
            this.val$runnable = runnable;
        }

        @Override // libv2ray.CoreCallbackHandler
        public final long onEmitStatus(long j, String str) {
            Log.i("ParvazCore", "core status " + j + ": " + str);
            return 0L;
        }

        @Override // libv2ray.CoreCallbackHandler
        public final long shutdown() {
            if (ownerGeneration != CoreManager.this.generation) return 0L;
            CoreManager.this.running = false;
            Runnable runnable = this.val$runnable;
            if (runnable == null) {
                return 0L;
            }
            runnable.run();
            return 0L;
        }

        @Override // libv2ray.CoreCallbackHandler
        public final long startup() {
            return 0L;
        }
    }

    /* JADX WARN: Can't change package for inner class: R1.a.b to com.parvaz.tunnel.core.CoreManager$2 */
    /* renamed from: R1.a$b */
    /* loaded from: classes.dex */
    public class b implements CoreCallbackHandler {

        /* renamed from: b */
        public final /* synthetic */ Runnable val$runnable;
        private final long ownerGeneration = CoreManager.this.generation;

        public b(Runnable runnable) {
            this.val$runnable = runnable;
        }

        @Override // libv2ray.CoreCallbackHandler
        public final long onEmitStatus(long j, String str) {
            Log.i("ParvazCore", "core status " + j + ": " + str);
            return 0L;
        }

        @Override // libv2ray.CoreCallbackHandler
        public final long shutdown() {
            if (ownerGeneration != CoreManager.this.generation) return 0L;
            CoreManager.this.running = false;
            Runnable runnable = this.val$runnable;
            if (runnable == null) {
                return 0L;
            }
            runnable.run();
            return 0L;
        }

        @Override // libv2ray.CoreCallbackHandler
        public final long startup() {
            return 0L;
        }
    }

    /* renamed from: a */
    public static void copyAssetIfNeeded(Context context, String str, File file) {
        try {
            if (file.exists() && file.length() > 0) {
                return;
            }
            InputStream open = context.getAssets().open(str);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            byte[] bArr = new byte[65536];
            while (true) {
                int read = open.read(bArr);
                if (read <= 0) {
                    fileOutputStream.close();
                    open.close();
                    return;
                }
                fileOutputStream.write(bArr, 0, read);
            }
        } catch (Exception e) {
            Log.w("ParvazCore", "asset copy skipped: " + str + " (" + e.getMessage() + ")");
        }
    }

    public static synchronized CoreManager b() {
        if (c == null) {
            c = new CoreManager();
        }
        return c;
    }

    public long sessionId(){return generation;}

    /* renamed from: c */
    public final synchronized void start(Context context,Profile profile,int tunFd,Runnable failure) {start(context,profile,tunFd,failure,-1);}
    public final synchronized void start(Context context,Profile profile,int tunFd,Runnable failure,long tunSetupMs) {
        final StartupDiagnostics.Attempt trace=StartupDiagnostics.begin(System.nanoTime(),tunSetupMs);
        stop();diagnostics=trace;trace.cleanupDone();
        final long ownerGeneration = generation;
        profile=com.parvaz.tunnel.store.ProfileIdentity.copy(profile);
        liveIdentity=com.parvaz.tunnel.store.ProfileIdentity.fingerprint(profile);
        try{
            Prefs prefs=new Prefs(context);String chainId=prefs.f343a.getString("chain_profile","");
            Profile chain=chainId==null||chainId.isEmpty()||chainId.equals(profile.id)?null:ProfileStore.f(context).getById(chainId);
            boolean nativeProfile=com.parvaz.tunnel.config.EngineConfig.external(profile.protocol);
            if(chain!=null&&(nativeProfile||com.parvaz.tunnel.config.FullConfig.isFull(profile.protocol)||com.parvaz.tunnel.config.EngineConfig.external(chain.protocol)))
                throw new IllegalArgumentException("Use a complete same-engine configuration for a multi-engine chain");
            Profile relay=profile;
            if(nativeProfile){external=ExternalCore.start(context,profile,()->nativeFailure(ownerGeneration,failure));relay=external.relay(profile);}
            String config;
            if(nativeProfile)config=com.parvaz.tunnel.config.ManagedConfig.xray(profile,relay,prefs,external.dnsPort,true,true);
            else if(profile.protocol.equals("full-xray")){
                Profile dummy=new Profile();dummy.protocol="socks";dummy.address="127.0.0.1";dummy.port=10810;
                config=com.parvaz.tunnel.config.ManagedConfig.xray(profile,dummy,prefs,0,true,true);
            }else config=XrayConfigBuilder.b(profile,prefs,chain,true,true);
            int readinessPort;
            try(java.net.ServerSocket reserved=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){readinessPort=reserved.getLocalPort();}
            com.parvaz.tunnel.config.ReadinessConfig.Plan readiness=com.parvaz.tunnel.config.ReadinessConfig.prepare(config,profile,external!=null&&external.readinessRemoteOnly,readinessPort);
            config=readiness.config;verifiedPort=readiness.pinned?readinessPort:0;trace.routeConfigured(readiness.pinned);
            trace.configDone();
            activeTunFd=tunFd;controller=Libv2ray.newCoreController(new b(failure));controller.startLoop(config,tunFd);running=controller.getIsRunning()&&(external==null||external.isRunning());
            if(!running)throw new IllegalStateException("Core failed to start");
            trace.coreStarted();
            // Most proxy outbounds dial lazily. Prime the configured connectivity
            // endpoint through the new local proxy without holding up user traffic.
            if(readiness.pinned)startupWarmup.start(prefs.f343a.getString("ping_url","https://www.gstatic.com/generate_204"),readinessPort,trace::probeFinished);
        }catch(Exception error){stop();throw new IllegalStateException("Core start failed: "+error.getMessage(),error);}
    }

    private synchronized void nativeFailure(long ownerGeneration,Runnable failure) {
        if(ownerGeneration!=generation)return;
        stop();
        if(failure!=null)failure.run();
    }

    /* renamed from: d */
    public final synchronized void stop() {
        StartupDiagnostics.Attempt trace=diagnostics;if(trace!=null)trace.stop();
        startupWarmup.cancel();verifiedPort=0;liveIdentity="";
        ++generation; // Invalidate callbacks before closing either core, including intentional restarts.
        HotspotProxyManager.stop();
        this.running = false;
        if(external!=null){external.close();external=null;}
        CoreController coreController = this.controller;
        if (coreController != null) {
            try {
                coreController.stopLoop();
            } catch (Exception e) {
                Log.w("ParvazCore", "stopLoop error", e);
            }
            this.controller = null;
        }
    }
}
