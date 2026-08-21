package com.parvaz.tunnel;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.ui.UsageChartView;
import com.parvaz.tunnel.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class UsageActivity extends AppCompatActivity {

    /* renamed from: A */
    public TextView f6207b;
    public Prefs B;

    /* renamed from: C */
    public TextView f6209d;

    /* renamed from: z */
    public UsageChartView f6206a;

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.UsageActivity.a to com.parvaz.tunnel.UsageActivity$a */
    /* loaded from: classes.dex */
    public class a implements View.OnClickListener {
        public a() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            UsageActivity.this.lambda$onCreate$0(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.UsageActivity.b to com.parvaz.tunnel.UsageActivity$b */
    /* loaded from: classes.dex */
    public class b implements View.OnClickListener {
        public b() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            UsageActivity.this.lambda$onCreate$2(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.UsageActivity.c to com.parvaz.tunnel.UsageActivity$c */
    /* loaded from: classes.dex */
    public class c implements DialogInterface.OnClickListener {
        public c() {
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            UsageActivity usageActivity = UsageActivity.this;
            usageActivity.B.f343a.edit().putString("daily_usage", "{}").apply();
            usageActivity.h();
        }
    }

    /* renamed from: A */
    public final void h() {
        JSONObject jSONObject;
        long j;
        long j2;
        ArrayList arrayList = new ArrayList();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat("E", Locale.getDefault());
        try {
            jSONObject = new JSONObject(this.B.f343a.getString("daily_usage", "{}"));
        } catch (Exception unused) {
            jSONObject = new JSONObject();
        }
        Calendar calendar = Calendar.getInstance();
        calendar.add(6, -6);
        int i = 0;
        boolean z = false;
        long j3 = 0;
        long j4 = 0;
        while (i < 7) {
            Date time = calendar.getTime();
            JSONArray optJSONArray = jSONObject.optJSONArray(simpleDateFormat.format(time));
            long optLong = optJSONArray != null ? optJSONArray.optLong(0) : 0L;
            if (optJSONArray != null) {
                j2 = optJSONArray.optLong(1);
                j = 0;
            } else {
                j = 0;
                j2 = 0;
            }
            if (optLong > j || j2 > j) {
                z = true;
            }
            j3 += optLong;
            j4 += j2;
            arrayList.add(new UsageChartView.a(i == 6 ? getString(R.string.usage_today) : simpleDateFormat2.format(time), optLong, j2));
            calendar.add(6, 1);
            i++;
        }
        this.f6206a.setDays(arrayList);
        this.f6207b.setVisibility(z ? 8 : 0);
        this.f6206a.setVisibility(z ? 0 : 4);
        this.f6209d.setText("↑ " + MainActivity.fmtBytes(j3) + "    ↓ " + MainActivity.fmtBytes(j4) + "    Σ " + MainActivity.fmtBytes(j3 + j4));
    }

    @Override // androidx.appcompat.app.AppCompatActivity, android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    public final void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }

    public void lambda$onCreate$0(View view) {
        finish();
    }

    public void lambda$onCreate$2(View view) {
        MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(this);
        materialAlertDialogBuilder.setMessage(R.string.usage_chart);
        materialAlertDialogBuilder.setNegativeButton(R.string.cancel, null);
        materialAlertDialogBuilder.setPositiveButton(R.string.ok, new c());
        materialAlertDialogBuilder.show();
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public final void onCreate(Bundle bundle) {
        int i;
        super.onCreate(bundle);
        setContentView(R.layout.activity_usage);
        this.B = new Prefs(this);
        this.f6206a = (UsageChartView) findViewById(R.id.chart);
        this.f6209d = (TextView) findViewById(R.id.summary);
        this.f6207b = (TextView) findViewById(R.id.empty);
        findViewById(R.id.back).setOnClickListener(new a());
        findViewById(R.id.clear).setOnClickListener(new b());
        UsageChartView usageChartView = this.f6206a;
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)) {
            i = ContextCompat.getColor(this, typedValue.resourceId);
        } else {
            i = -6381922;
        }
        usageChartView.setLabelColor(i);
        h();
    }
}
