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

    /* JADX WARN: Can't change package for inner class: R1.a.a to com.parvaz.tunnel.core.CoreManager$1 */
    /* renamed from: R1.a$a */
    /* loaded from: classes.dex */
    public class a implements CoreCallbackHandler {

        /* renamed from: b */
        public final /* synthetic */ Runnable val$runnable;

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

    /* renamed from: c */
    public final synchronized void start(Context context,Profile profile,int tunFd,Runnable failure) {
        stop();
        try{
            Prefs prefs=new Prefs(context);String chainId=prefs.f343a.getString("chain_profile","");
            Profile chain=chainId==null||chainId.isEmpty()||chainId.equals(profile.id)?null:ProfileStore.f(context).getById(chainId);
            boolean nativeProfile=com.parvaz.tunnel.config.EngineConfig.external(profile.protocol);
            if(chain!=null&&(nativeProfile||com.parvaz.tunnel.config.FullConfig.isFull(profile.protocol)||com.parvaz.tunnel.config.EngineConfig.external(chain.protocol)))
                throw new IllegalArgumentException("Use a complete same-engine configuration for a multi-engine chain");
            Profile relay=profile;
            if(nativeProfile){external=ExternalCore.start(context,profile,()->{CoreManager.this.stop();if(failure!=null)failure.run();});relay=external.relay(profile);}
            String config;
            if(nativeProfile)config=com.parvaz.tunnel.config.ManagedConfig.xray(profile,relay,prefs,external.dnsPort,true,true);
            else if(profile.protocol.equals("full-xray")){
                Profile dummy=new Profile();dummy.protocol="socks";dummy.address="127.0.0.1";dummy.port=10810;
                config=com.parvaz.tunnel.config.ManagedConfig.xray(profile,dummy,prefs,0,true,true);
            }else config=XrayConfigBuilder.b(profile,prefs,chain,true,true);
            controller=Libv2ray.newCoreController(new b(failure));controller.startLoop(config,tunFd);running=controller.getIsRunning();
            if(!running)throw new IllegalStateException("Core failed to start");
        }catch(Exception error){stop();throw new IllegalStateException("Core start failed: "+error.getMessage(),error);}
    }

    /* renamed from: d */
    public final synchronized void stop() {
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
