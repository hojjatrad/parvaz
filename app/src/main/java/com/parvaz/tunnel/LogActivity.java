package com.parvaz.tunnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.core.LogBuffer;
import com.parvaz.tunnel.R;
import java.util.ArrayDeque;

/* loaded from: classes.dex */
public class LogActivity extends AppCompatActivity {

    /* renamed from: A */
    public TextView f6091b;

    /* renamed from: B */
    public final Handler f6092c = new Handler(Looper.getMainLooper());

    /* renamed from: z */
    public ScrollView f6090a;

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.LogActivity.a to com.parvaz.tunnel.LogActivity$a */
    /* loaded from: classes.dex */
    public class a implements View.OnClickListener {
        public a() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            LogActivity.this.lambda$onCreate$0(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.LogActivity.b to com.parvaz.tunnel.LogActivity$b */
    /* loaded from: classes.dex */
    public class b implements View.OnClickListener {
        public b() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            LogActivity.this.lambda$onCreate$1(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.LogActivity.c to com.parvaz.tunnel.LogActivity$c */
    /* loaded from: classes.dex */
    public class c implements View.OnClickListener {
        public c() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            LogActivity.this.lambda$onCreate$2(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.LogActivity.d to com.parvaz.tunnel.LogActivity$d */
    /* loaded from: classes.dex */
    public class d {
        public d() {
        }

        public LogActivity outer() {
            return LogActivity.this;
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.LogActivity.e to com.parvaz.tunnel.LogActivity$e */
    /* loaded from: classes.dex */
    public class e implements Runnable {
        public e() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            LogActivity.this.f6090a.fullScroll(130);
        }
    }

    /* renamed from: A */
    public final void h() {
        String lines = LogBuffer.lines();
        if (lines.trim().isEmpty()) {
            this.f6091b.setText(R.string.log_empty);
        } else {
            this.f6091b.setText(lines);
        }
        this.f6090a.post(new e());
    }

    @Override // androidx.appcompat.app.AppCompatActivity, android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    public final void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }

    public void lambda$onCreate$0(View view) {
        finish();
    }

    public void lambda$onCreate$1(View view) {
        ArrayDeque<String> arrayDeque = LogBuffer.f6244b;
        synchronized (arrayDeque) {
            arrayDeque.clear();
        }
        h();
    }

    public void lambda$onCreate$2(View view) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("parvaz-log", LogBuffer.lines()));
            Snackbar.make(view, R.string.log_copied, -1).show();
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public final void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_log);
        this.f6091b = (TextView) findViewById(R.id.log_text);
        this.f6090a = (ScrollView) findViewById(R.id.log_scroll);
        findViewById(R.id.back).setOnClickListener(new a());
        findViewById(R.id.log_clear).setOnClickListener(new b());
        findViewById(R.id.log_copy).setOnClickListener(new c());
        h();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    public final void onPause() {
        super.onPause();
        LogBuffer.a = null;
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    public final void onResume() {
        super.onResume();
        LogBuffer.a = new d();
        h();
    }
}
