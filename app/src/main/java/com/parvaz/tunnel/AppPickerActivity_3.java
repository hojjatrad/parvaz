package com.parvaz.tunnel;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import com.parvaz.tunnel.AppPickerActivity;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/* renamed from: com.parvaz.tunnel.a */
/* loaded from: classes.dex */
public final class AppPickerActivity_3 implements Runnable {

    /* renamed from: b */
    public final int f228b;

    /* renamed from: c */
    public final Object f229c;

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.a.a to com.parvaz.tunnel.AppPickerActivity_3$1 */
    /* renamed from: com.parvaz.tunnel.a$a */
    /* loaded from: classes.dex */
    public class a implements Runnable {

        /* renamed from: b */
        public final /* synthetic */ AppPickerActivity val$act;

        /* renamed from: c */
        public final /* synthetic */ ArrayList val$rows;

        public a(AppPickerActivity appPickerActivity, ArrayList arrayList) {
            this.val$act = appPickerActivity;
            this.val$rows = arrayList;
        }

        @Override // java.lang.Runnable
        public final void run() {
            AppPickerActivity appPickerActivity = this.val$act;
            ArrayList arrayList = appPickerActivity.f6072e;
            arrayList.clear();
            arrayList.addAll(this.val$rows);
            appPickerActivity.h("");
            appPickerActivity.f6071d.setVisibility(8);
        }
    }

    public AppPickerActivity_3(int i, Object obj) {
        this.f228b = i;
        this.f229c = obj;
    }

    @Override // java.lang.Runnable
    public final void run() {
        Object obj = this.f229c;
        int i = this.f228b;
        if (i != 0) {
            if (i != 1) {
                return;
            }
            ((LogActivity) obj).h();
            return;
        }
        AppPickerActivity appPickerActivity = (AppPickerActivity) obj;
        PackageManager packageManager = appPickerActivity.getPackageManager();
        List<ApplicationInfo> installedApplications;
        try {
            installedApplications = packageManager.getInstalledApplications(0);
        } catch (Exception e) {
            // A dead//throttled PackageManager must not strand the screen on its spinner.
            android.util.Log.w("ParvazPicker", "getInstalledApplications failed: " + e);
            installedApplications = java.util.Collections.emptyList();
        }
        ArrayList arrayList = new ArrayList();
        String packageName = appPickerActivity.getPackageName();
        for (ApplicationInfo applicationInfo : installedApplications) {
            if (applicationInfo == null || applicationInfo.packageName == null) {
                continue;
            }
            if (!applicationInfo.packageName.equals(packageName) && packageManager.checkPermission("android.permission.INTERNET", applicationInfo.packageName) == 0) {
                AppPickerActivity.d dVar = new AppPickerActivity.d();
                dVar.f6081c = applicationInfo.packageName;
                try {
                    dVar.f6080b = String.valueOf(packageManager.getApplicationLabel(applicationInfo));
                } catch (Exception unused) {
                    dVar.f6080b = applicationInfo.packageName;
                }
                try {
                    dVar.f6079a = packageManager.getApplicationIcon(applicationInfo);
                } catch (Exception unused2) {
                    dVar.f6079a = null;
                }
                dVar.f6082d = (applicationInfo.flags & 1) != 0;
                arrayList.add(dVar);
            }
        }
        Collections.sort(arrayList, new AppPickerActivity_4(appPickerActivity, Collator.getInstance(Locale.getDefault())));
        appPickerActivity.f6075i.post(new a(appPickerActivity, arrayList));
    }
}
