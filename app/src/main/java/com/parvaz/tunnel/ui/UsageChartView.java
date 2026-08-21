package com.parvaz.tunnel.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
public class UsageChartView extends View {

    /* renamed from: a */
    public final Paint f6308a;

    /* renamed from: b */
    public final ArrayList f6309b;

    /* renamed from: c */
    public int f6310c;

    /* renamed from: d */
    public final RectF f6311d;

    /* renamed from: e */
    public final Paint f6312e;

    /* renamed from: f */
    public final Paint f6313f;

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.ui.UsageChartView.a to com.parvaz.tunnel.ui.UsageChartView$Bar */
    /* loaded from: classes.dex */
    public static class a {

        /* renamed from: a */
        public final long f6314a;

        /* renamed from: b */
        public final String f6315b;

        /* renamed from: c */
        public final long f6316c;

        public a(String str, long j, long j2) {
            this.f6315b = str;
            this.f6316c = j;
            this.f6314a = j2;
        }
    }

    public UsageChartView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.f6309b = new ArrayList();
        this.f6308a = new Paint(1);
        Paint paint = new Paint(1);
        this.f6313f = paint;
        Paint paint2 = new Paint(1);
        this.f6312e = paint2;
        this.f6311d = new RectF();
        this.f6310c = -6381922;
        paint.setColor(579373192);
        paint2.setTextSize(b(10.0f));
        paint2.setTextAlign(Paint.Align.CENTER);
    }

    public final float a(float f) {
        return f * getResources().getDisplayMetrics().density;
    }

    public final float b(float f) {
        return f * getResources().getDisplayMetrics().scaledDensity;
    }

    @Override // android.view.View
    public final void onDraw(Canvas canvas) {
        float f;
        float f2;
        Canvas canvas2;
        a aVar;
        float f3;
        long j;
        int i;
        float f4;
        Canvas canvas3 = canvas;
        super.onDraw(canvas);
        ArrayList arrayList = this.f6309b;
        if (arrayList.isEmpty()) {
            return;
        }
        float b = b(14.0f);
        float b2 = b(13.0f);
        float a2 = a(6.0f);
        float width = getWidth();
        float height = getHeight();
        float a3 = a(2.0f) + b2;
        float f5 = height - b;
        float f6 = f5 - a3;
        if (f6 <= 0.0f) {
            return;
        }
        Iterator it = arrayList.iterator();
        long j2 = 1;
        while (it.hasNext()) {
            a aVar2 = (a) it.next();
            j2 = Math.max(j2, aVar2.f6316c + aVar2.f6314a);
            a3 = a3;
        }
        float f7 = a3;
        float size = width / arrayList.size();
        float f8 = 2.0f;
        float min = Math.min(a(28.0f), size - (a2 * 2.0f)) / 2.0f;
        int i2 = 0;
        while (i2 < arrayList.size()) {
            a aVar3 = (a) arrayList.get(i2);
            float f9 = (i2 * size) + (size / f8);
            float f10 = f9 - min;
            float f11 = f9 + min;
            RectF rectF = this.f6311d;
            int i3 = i2;
            float f12 = f7;
            rectF.set(f10, f12, f11, f5);
            ArrayList arrayList2 = arrayList;
            canvas3.drawRoundRect(rectF, min, min, this.f6313f);
            long j3 = aVar3.f6316c;
            float f13 = size;
            float f14 = height;
            long j4 = aVar3.f6314a;
            if (j3 + j4 > 0) {
                f2 = a(3.0f);
                f = f10;
            } else {
                f = f10;
                f2 = 0.0f;
            }
            long j5 = aVar3.f6316c + j4;
            float max = Math.max(f2, ((float) (j5 / j2)) * f6);
            if (max > 0.0f) {
                float f15 = f5 - max;
                Paint paint = this.f6308a;
                aVar = aVar3;
                f3 = f12;
                i = i3;
                j = j2;
                paint.setShader(new LinearGradient(0.0f, f15, 0.0f, f5, -12756226, -16718432, Shader.TileMode.CLAMP));
                rectF.set(f, f15, f11, f5);
                canvas2 = canvas;
                canvas2.drawRoundRect(rectF, min, min, paint);
            } else {
                canvas2 = canvas;
                aVar = aVar3;
                f3 = f12;
                j = j2;
                i = i3;
            }
            Paint paint2 = this.f6312e;
            if (j5 > 0) {
                paint2.setColor(this.f6310c);
                paint2.setTextSize(b(9.0f));
                double d = j5;
                String[] strArr = {"B", "K", "M", "G", "T"};
                int i4 = 0;
                while (d >= 1024.0d && i4 < 4) {
                    d /= 1024.0d;
                    i4++;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(d < 10.0d ? String.format(Locale.US, "%.1f", Double.valueOf(d)) : String.valueOf(Math.round(d)));
                sb.append(strArr[i4]);
                f4 = 2.0f;
                canvas2.drawText(sb.toString(), f9, f3 - a(2.0f), paint2);
            } else {
                f4 = 2.0f;
            }
            paint2.setColor(this.f6310c);
            paint2.setTextSize(b(10.0f));
            canvas2.drawText(aVar.f6315b, f9, f14 - a(f4), paint2);
            i2 = i + 1;
            f8 = f4;
            canvas3 = canvas2;
            arrayList = arrayList2;
            height = f14;
            size = f13;
            f7 = f3;
            j2 = j;
        }
    }

    public void setDays(List<a> list) {
        ArrayList arrayList = this.f6309b;
        arrayList.clear();
        if (list != null) {
            arrayList.addAll(list);
        }
        invalidate();
    }

    public void setLabelColor(int i) {
        this.f6310c = i;
        invalidate();
    }
}
