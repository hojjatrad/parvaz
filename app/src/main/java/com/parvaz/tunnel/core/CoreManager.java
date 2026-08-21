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
    public final synchronized void start(Context context, Profile profile, int i, Runnable runnable) {
        boolean z;
        CoreController coreController;
        try {
            try {
                if (this.running && (coreController = this.controller) != null && coreController.getIsRunning()) {
                    z = true;
                } else {
                    z = false;
                }
                if (z) {
                    stop();
                }
                Prefs prefs = new Prefs(context);
                String string = prefs.f343a.getString("chain_profile", "");
                if (string != null && !string.isEmpty() && !string.equals(profile.id)) {
                    String b2 = XrayConfigBuilder.b(profile, prefs, ProfileStore.f(context).getById(string), true, true);
                    Log.i("ParvazCore", "starting core for " + profile.remark);
                    CoreController newCoreController = Libv2ray.newCoreController(new a(runnable));
                    this.controller = newCoreController;
                    newCoreController.startLoop(b2, i);
                    this.running = this.controller.getIsRunning();
                    if (this.running) {
                        throw new IllegalStateException("core failed to start");
                    }
                }
                String b3 = XrayConfigBuilder.b(profile, prefs, null, true, true);
                Log.i("ParvazCore", "starting core for " + profile.remark);
                CoreController newCoreController2 = Libv2ray.newCoreController(new b(runnable));
                this.controller = newCoreController2;
                newCoreController2.startLoop(b3, i);
                this.running = this.controller.getIsRunning();
            } catch (Exception e) {
                this.running = false;
                throw new IllegalStateException("core start failed: " + e.getMessage(), e);
            }
        } catch (Throwable th) {
            throw th;
        }
    }

    /* renamed from: d */
    public final synchronized void stop() {
        this.running = false;
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
