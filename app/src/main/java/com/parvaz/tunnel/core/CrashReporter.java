package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import com.parvaz.tunnel.CrashActivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/* renamed from: R1.b */
/* loaded from: classes.dex */
public final class CrashReporter {

    /* JADX WARN: Can't change package for inner class: R1.b.a to com.parvaz.tunnel.core.CrashReporter$a */
    /* renamed from: R1.b$a */
    /* loaded from: classes.dex */
    public static class a implements Thread.UncaughtExceptionHandler {

        /* renamed from: a */
        public final Context f6219a;

        /* renamed from: b */
        public final Thread.UncaughtExceptionHandler f6220b;

        public a(Context context, Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
            this.f6219a = context;
            this.f6220b = uncaughtExceptionHandler;
        }

        @Override // java.lang.Thread.UncaughtExceptionHandler
        public final void uncaughtException(Thread thread, Throwable th) {
            String str;
            Context context = this.f6219a;
            try {
                str = CrashReporter.buildReport(context, thread, th);
                try {
                    CrashReporter.save(context, str);
                } catch (Throwable unused) {
                    android.util.Log.w("Parvaz/CrashReporter", "Throwable ignored", unused);
                }
            } catch (Throwable unused2) {
                str = "";
            }
            try {
                Intent intent = new Intent(context, (Class<?>) CrashActivity.class);
                intent.addFlags(276856832);
                intent.putExtra("report", str);
                context.startActivity(intent);
            } catch (Throwable unused3) {
                android.util.Log.w("Parvaz/CrashReporter", "Throwable ignored", unused3);
            }
            Thread.UncaughtExceptionHandler uncaughtExceptionHandler = this.f6220b;
            if (uncaughtExceptionHandler != null) {
                uncaughtExceptionHandler.uncaughtException(thread, th);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        }
    }

    /* renamed from: a */
    public static String buildReport(Context context, Thread thread, Throwable th) {
        int i;
        String str;
        String str2 = "?";
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            str = packageInfo.versionName;
            i = packageInfo.versionCode;
        } catch (Throwable unused) {
            i = -1;
            str = "?";
        }
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        printWriter.println("=== Parvaz crash report ===");
        printWriter.println("time     : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        printWriter.println("app      : " + str + " (" + i + ")");
        printWriter.println("android  : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        StringBuilder sb = new StringBuilder("device   : ");
        sb.append(Build.MANUFACTURER);
        sb.append(" ");
        sb.append(Build.MODEL);
        printWriter.println(sb.toString());
        printWriter.println("abi      : " + Arrays.toString(Build.SUPPORTED_ABIS));
        StringBuilder sb2 = new StringBuilder("thread   : ");
        if (thread != null) {
            str2 = thread.getName();
        }
        sb2.append(str2);
        printWriter.println(sb2.toString());
        printWriter.println();
        printWriter.println("--- stack trace ---");
        if (th != null) {
            th.printStackTrace(printWriter);
        }
        printWriter.println();
        printWriter.println("--- recent log ---");
        printWriter.println(LogBuffer.lines());
        printWriter.flush();
        return stringWriter.toString();
    }

    /* renamed from: b */
    public static void clear(Context context) {
        File[] listFiles = new File(context.getFilesDir(), "crashes").listFiles();
        if (listFiles != null) {
            for (File file : listFiles) {
                file.delete();
            }
        }
    }

    /* renamed from: c */
    public static File latest(Context context) {
        File[] listFiles = new File(context.getFilesDir(), "crashes").listFiles();
        File file = null;
        if (listFiles != null && listFiles.length != 0) {
            for (File file2 : listFiles) {
                if (file == null || file2.lastModified() > file.lastModified()) {
                    file = file2;
                }
            }
        }
        return file;
    }

    /* renamed from: d */
    public static String read(File file) {
        try {
            byte[] bArr = new byte[(int) Math.min(file.length(), 262144L)];
            FileInputStream fileInputStream = new FileInputStream(file);
            int read = fileInputStream.read(bArr);
            fileInputStream.close();
            return new String(bArr, 0, Math.max(read, 0), StandardCharsets.UTF_8);
        } catch (Throwable unused) {
            return "";
        }
    }

    /* renamed from: e */
    public static void save(Context context, String str) {
        try {
            File file = new File(context.getFilesDir(), "crashes");
            if (!file.exists() && !file.mkdirs()) {
                return;
            }
            FileOutputStream fileOutputStream = new FileOutputStream(new File(file, "crash-" + System.currentTimeMillis() + ".txt"));
            fileOutputStream.write(str.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
            File[] listFiles = file.listFiles();
            if (listFiles != null) {
                if (listFiles.length > 10) {
                    Arrays.sort(listFiles, new java.util.Comparator<java.io.File>() {
                        @Override
                        public int compare(java.io.File a, java.io.File b) {
                            // newest first, so the oldest reports get pruned below
                            return Long.compare(b.lastModified(), a.lastModified());
                        }
                    });
                    for (int i = 10; i < listFiles.length; i++) {
                        listFiles[i].delete();
                    }
                }
            }
        } catch (Throwable th) {
            Log.e("ParvazCrash", "save failed", th);
        }
    }
}
