package com.parvaz.tunnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.parvaz.tunnel.core.CrashReporter;
import com.parvaz.tunnel.R;

/* loaded from: classes.dex */
public class CrashActivity extends AppCompatActivity {

    /* renamed from: z */
    public String report = "";

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.CrashActivity.a to com.parvaz.tunnel.CrashActivity$a */
    /* loaded from: classes.dex */
    public class a implements View.OnClickListener {
        public a() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            CrashActivity crashActivity = CrashActivity.this;
            crashActivity.getClass();
            try {
                ClipboardManager clipboardManager = (ClipboardManager) crashActivity.getSystemService("clipboard");
                if (clipboardManager != null) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("parvaz-crash", crashActivity.report));
                    Toast.makeText(crashActivity, R.string.copied, 0).show();
                }
            } catch (Throwable unused) {
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.CrashActivity.b to com.parvaz.tunnel.CrashActivity$b */
    /* loaded from: classes.dex */
    public class b implements View.OnClickListener {
        public b() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            CrashActivity crashActivity = CrashActivity.this;
            crashActivity.getClass();
            try {
                Intent intent = new Intent("android.intent.action.SEND");
                intent.setType("text/plain");
                intent.putExtra("android.intent.extra.SUBJECT", "Parvaz crash report");
                intent.putExtra("android.intent.extra.TEXT", crashActivity.report);
                crashActivity.startActivity(Intent.createChooser(intent, crashActivity.getString(R.string.crash_share_title)));
            } catch (Throwable unused) {
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.CrashActivity.c to com.parvaz.tunnel.CrashActivity$c */
    /* loaded from: classes.dex */
    public class c implements View.OnClickListener {
        public c() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            CrashActivity crashActivity = CrashActivity.this;
            crashActivity.getClass();
            try {
                Intent intent = new Intent(crashActivity, (Class<?>) MainActivity.class);
                intent.addFlags(268468224);
                crashActivity.startActivity(intent);
            } catch (Throwable unused) {
            }
            crashActivity.finishAndRemoveTask();
            System.exit(0);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.CrashActivity.d to com.parvaz.tunnel.CrashActivity$d */
    /* loaded from: classes.dex */
    public class d implements View.OnClickListener {
        public d() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            CrashActivity.this.finishAndRemoveTask();
            System.exit(0);
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public final void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_crash);
        String stringExtra = getIntent() != null ? getIntent().getStringExtra("report") : null;
        if (stringExtra == null || stringExtra.isEmpty()) {
            stringExtra = CrashReporter.read(CrashReporter.latest(this));
        }
        this.report = stringExtra;
        TextView textView = (TextView) findViewById(R.id.crash_report);
        if (textView != null) {
            textView.setText(this.report.isEmpty() ? getString(R.string.crash_none) : this.report);
        }
        Button button = (Button) findViewById(R.id.crash_copy);
        if (button != null) {
            button.setOnClickListener(new a());
        }
        Button button2 = (Button) findViewById(R.id.crash_share);
        if (button2 != null) {
            button2.setOnClickListener(new b());
        }
        Button button3 = (Button) findViewById(R.id.crash_restart);
        if (button3 != null) {
            button3.setOnClickListener(new c());
        }
        Button button4 = (Button) findViewById(R.id.crash_close);
        if (button4 != null) {
            button4.setOnClickListener(new d());
        }
    }
}
