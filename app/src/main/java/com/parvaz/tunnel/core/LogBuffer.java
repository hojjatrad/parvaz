package com.parvaz.tunnel.core;

import com.parvaz.tunnel.AppPickerActivity_3;
import com.parvaz.tunnel.LogActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/* renamed from: R1.c */
/* loaded from: classes.dex */
public final class LogBuffer {
    public static volatile LogActivity.d a;

    /* renamed from: b */
    public static final ArrayDeque<String> f6244b = new ArrayDeque<>();

    /* renamed from: c */
    public static final SimpleDateFormat f6245c = new SimpleDateFormat("HH:mm:ss", Locale.US);

    /* renamed from: a */
    public static String lines() {
        ArrayList arrayList;
        StringBuilder sb = new StringBuilder();
        ArrayDeque<String> arrayDeque = f6244b;
        synchronized (arrayDeque) {
            arrayList = new ArrayList(arrayDeque);
        }
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            sb.append((String) it.next());
            sb.append('\n');
        }
        return sb.toString();
    }

    /* renamed from: b */
    public static void listener(String str) {
        if (str == null) {
            return;
        }
        String str2 = f6245c.format(new Date()) + "  " + str;
        ArrayDeque<String> arrayDeque = f6244b;
        synchronized (arrayDeque) {
            arrayDeque.addLast(str2);
            while (true) {
                ArrayDeque<String> arrayDeque2 = f6244b;
                if (arrayDeque2.size() <= 500) {
                    break;
                } else {
                    arrayDeque2.removeFirst();
                }
            }
        }
        if (a != null) {
            try {
                LogActivity logActivity = a.outer();
                logActivity.f6092c.post(new AppPickerActivity_3(1, logActivity));
            } catch (Exception unused) {
            }
        }
    }
}
