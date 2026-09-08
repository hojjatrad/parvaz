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
import java.util.concurrent.TimeUnit;

/* loaded from: classes.dex */
public class SubscriptionWorker extends Worker {

    public static final String WORK_NAME = "parvaz_sub_auto_update";

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

    @Override
    public final ListenableWorker.Result doWork() {
        SubscriptionRefresh.Result result=SubscriptionRefresh.run(getApplicationContext(),this::isStopped);
        if(result.cancelled || result.retryable) return ListenableWorker.Result.retry();
        if(result.failed>0) return ListenableWorker.Result.failure();
        return ListenableWorker.Result.success();
    }
}
