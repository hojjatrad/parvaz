package com.parvaz.tunnel.core;

import android.content.Context;
import android.util.Log;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.parvaz.tunnel.core.SubscriptionUpdater;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/* loaded from: classes.dex */
public class SubscriptionWorker extends Worker {

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.core.SubscriptionWorker.a to com.parvaz.tunnel.core.SubscriptionWorker$a */
    /* loaded from: classes.dex */
    public class a implements SubscriptionUpdater.a {

        /* renamed from: a */
        public final CountDownLatch f6221a;

        public a(CountDownLatch countDownLatch) {
            this.f6221a = countDownLatch;
        }

        @Override // com.parvaz.tunnel.core.SubscriptionUpdater.a
        public final void a(String str, int i) {
            String str2;
            StringBuilder sb = new StringBuilder("sub auto-update: added=");
            sb.append(i);
            if (str != null) {
                str2 = " err=".concat(str);
            } else {
                str2 = "";
            }
            sb.append(str2);
            Log.i("ParvazVpn", sb.toString());
            this.f6221a.countDown();
        }
    }

    public SubscriptionWorker(Context context, WorkerParameters workerParameters) {
        super(context, workerParameters);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Removed duplicated region for block: B:14:0x0097  */
    /* JADX WARN: Removed duplicated region for block: B:16:0x00a0  */
    /* JADX WARN: Removed duplicated region for block: B:18:0x00a5  */
    /* JADX WARN: Removed duplicated region for block: B:21:0x00aa  */
    /* JADX WARN: Removed duplicated region for block: B:23:0x00b4  */
    /* JADX WARN: Removed duplicated region for block: B:26:0x00be  */
    /* JADX WARN: Removed duplicated region for block: B:29:0x00cd  */
    /* JADX WARN: Removed duplicated region for block: B:32:0x00e8  */
    /* JADX WARN: Removed duplicated region for block: B:40:0x0119  */
    /* JADX WARN: Removed duplicated region for block: B:42:0x00a2  */
    /* JADX WARN: Type inference failed for: r2v10, types: [java.util.Set, java.lang.Object] */
    /* JADX WARN: Type inference failed for: r3v10, types: [java.util.AbstractCollection, java.util.LinkedHashSet] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static void g(android.content.Context r14) {
        /*
            Method dump skipped, instructions count: 323
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.parvaz.tunnel.core.SubscriptionWorker.g(android.content.Context):void");
    }

    @Override // androidx.work.Worker
    public final ListenableWorker.Result doWork() {
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            new Thread(new SubscriptionUpdater_4(new SubscriptionUpdater(getApplicationContext()), new a(countDownLatch))).start();
            if (!countDownLatch.await(3L, TimeUnit.MINUTES)) {
                return ListenableWorker.Result.retry();
            }
            return ListenableWorker.Result.success();
        } catch (Exception e) {
            Log.e("ParvazVpn", "sub auto-update failed", e);
            return ListenableWorker.Result.retry();
        }
    }
}
