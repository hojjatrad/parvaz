package com.parvaz.tunnel.core;

import android.content.Context;
import android.util.Log;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.parvaz.tunnel.core.SubscriptionUpdater;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/* loaded from: classes.dex */
public class SubscriptionWorker extends Worker {

    public static final String WORK_NAME = "parvaz_sub_auto_update";

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

    /**
     * (Re)schedules the periodic subscription refresh from the "sub_auto_hours"
     * preference. 0 hours means the user turned auto-update off, in which case any
     * previously scheduled work is cancelled. Uses REPLACE so changing the interval
     * in Settings takes effect immediately.
     */
    /* renamed from: g */
    public static void g(Context context) {
        try {
            WorkManager wm = WorkManager.getInstance(context.getApplicationContext());

            int hours = context.getApplicationContext()
                    .getSharedPreferences("parvaz_prefs", Context.MODE_PRIVATE)
                    .getInt("sub_auto_hours", 0);

            if (hours <= 0) {
                wm.cancelUniqueWork(WORK_NAME);
                return;
            }
            // PeriodicWorkRequest enforces a 15-minute floor; hours is >= 1 here.
            long interval = Math.max(1L, (long) hours);

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    SubscriptionWorker.class, interval, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.MINUTES)
                    .build();

            wm.enqueueUniquePeriodicWork(
                    WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request);

            Log.i("ParvazVpn", "sub auto-update scheduled every " + interval + "h");
        } catch (Throwable t) {
            // Never let a scheduling failure take down App.onCreate().
            Log.e("ParvazVpn", "sub auto-update scheduling failed", t);
        }
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
