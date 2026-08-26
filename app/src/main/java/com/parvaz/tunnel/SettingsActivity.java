package com.parvaz.tunnel;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.RulesActivity__ExternalSyntheticOutline0;
import com.parvaz.tunnel.SettingsActivity_28;
import com.parvaz.tunnel.SettingsActivity_29;
import com.parvaz.tunnel.core.CoreManager;
import com.parvaz.tunnel.core.UpdateFlow;
import com.parvaz.tunnel.core.SubscriptionWorker;
import com.parvaz.tunnel.core.TunnelVpnService;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.BackupManager;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.store.ProfileStore;
import com.parvaz.tunnel.R;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import libv2ray.Libv2ray;

/* loaded from: classes.dex */
public class SettingsActivity extends AppCompatActivity {

    /* renamed from: D */
    public static final String[] f6156e = {"global", "block_ads", "iran_direct"};

    /* renamed from: E */
    public static final String[] f6157f = {"none", "error", "warning", "info", "debug"};

    /* renamed from: F */
    public static final String[] g = {"AsIs", "IPIfNonMatch", "IPOnDemand"};

    /* renamed from: G */
    public static final String[] f6158h = {"fa", "en"};

    /* renamed from: H */
    public static final int[] f6159i = {0, 6, 12, 24};

    /* renamed from: I */
    public static final String[] f6160j = {"off", "bypass", "only"};
    public ActivityResultLauncher<String[]> A;

    /* renamed from: B */
    public String f6163c = "";
    public Prefs C;
    public ActivityResultLauncher<String> z;

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.A to com.parvaz.tunnel.SettingsActivity$x */
    /* loaded from: classes.dex */
    public class A implements e {
        public final Prefs a;

        public A(Prefs prefs) {
            this.a = prefs;
        }

        @Override // com.parvaz.tunnel.SettingsActivity.e
        public final void a(String str) {
            RulesActivity__ExternalSyntheticOutline0.j(this.a.f343a, "domain_strategy", str);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.B to com.parvaz.tunnel.SettingsActivity$y */
    /* loaded from: classes.dex */
    public class B implements CompoundButton.OnCheckedChangeListener {
        public B() {
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a, "mux_enabled", z);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.C to com.parvaz.tunnel.SettingsActivity$z */
    /* loaded from: classes.dex */
    public class C implements CompoundButton.OnCheckedChangeListener {
        public C() {
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a, "ipv6_enabled", z);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.a to com.parvaz.tunnel.SettingsActivity$A */
    /* renamed from: com.parvaz.tunnel.SettingsActivity$a, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0031a implements CompoundButton.OnCheckedChangeListener {
        public C0031a() {
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a, "bypass_lan", z);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.b to com.parvaz.tunnel.SettingsActivity$B */
    /* renamed from: com.parvaz.tunnel.SettingsActivity$b, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0032b implements CompoundButton.OnCheckedChangeListener {
        public C0032b() {
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a, "auto_switch", z);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.c to com.parvaz.tunnel.SettingsActivity$C0205b */
    /* renamed from: com.parvaz.tunnel.SettingsActivity$c, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0033c implements CompoundButton.OnCheckedChangeListener {
        public C0033c() {
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a, "connect_on_boot", z);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.d to com.parvaz.tunnel.SettingsActivity$C0206c */
    /* loaded from: classes.dex */
    public class d implements CompoundButton.OnCheckedChangeListener {

        /* renamed from: a */
        public final LinearLayout f6169a;

        public d(LinearLayout linearLayout) {
            this.f6169a = linearLayout;
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a, "fragment_enabled", z);
            this.f6169a.setVisibility(z ? 0 : 8);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.e to com.parvaz.tunnel.SettingsActivity$C */
    /* loaded from: classes.dex */
    public interface e {
        void a(String str);
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.f to com.parvaz.tunnel.SettingsActivity$ViewOnClickListenerC0204a */
    /* loaded from: classes.dex */
    public class f implements View.OnClickListener {
        public f() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$6(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.g to com.parvaz.tunnel.SettingsActivity$d */
    /* loaded from: classes.dex */
    public class g implements e {
        public g() {
        }

        @Override // com.parvaz.tunnel.SettingsActivity.e
        public final void a(String str) {
            SettingsActivity settingsActivity = SettingsActivity.this;
            settingsActivity.C.f343a.edit().putInt("sub_auto_hours", Math.max(0, Integer.parseInt(str))).apply();
            SubscriptionWorker.g(settingsActivity);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.h to com.parvaz.tunnel.SettingsActivity$e */
    /* loaded from: classes.dex */
    public class h implements e {
        public final Prefs a;

        public h(Prefs prefs) {
            this.a = prefs;
        }

        @Override // com.parvaz.tunnel.SettingsActivity.e
        public final void a(String str) {
            RulesActivity__ExternalSyntheticOutline0.j(this.a.f343a, "per_app_mode", str);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.i to com.parvaz.tunnel.SettingsActivity$f */
    /* loaded from: classes.dex */
    public class i implements View.OnClickListener {
        public i() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$10(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.j to com.parvaz.tunnel.SettingsActivity$g */
    /* loaded from: classes.dex */
    public class j implements View.OnClickListener {
        public j() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$11(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.k to com.parvaz.tunnel.SettingsActivity$h */
    /* loaded from: classes.dex */
    public class k implements View.OnClickListener {
        public k() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$12(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.l to com.parvaz.tunnel.SettingsActivity$i */
    /* loaded from: classes.dex */
    public class l implements View.OnClickListener {
        public l() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$13(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.m to com.parvaz.tunnel.SettingsActivity$j */
    /* loaded from: classes.dex */
    public class m implements CompoundButton.OnCheckedChangeListener {
        public m() {
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            SettingsActivity settingsActivity = SettingsActivity.this;
            settingsActivity.getClass();
            RulesActivity__ExternalSyntheticOutline0.k(settingsActivity.C.f343a, "app_lock", z);
            compoundButton.setChecked(false);
            Snackbar.make(compoundButton, compoundButton.getResources().getText(R.string.app_lock_unavailable), 0).show();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.n to com.parvaz.tunnel.SettingsActivity$k */
    /* loaded from: classes.dex */
    public class n implements View.OnClickListener {
        public n() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$0(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.o to com.parvaz.tunnel.SettingsActivity$l */
    /* loaded from: classes.dex */
    public class o implements CompoundButton.OnCheckedChangeListener {
        public o() {
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a, "kill_switch", z);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.p to com.parvaz.tunnel.SettingsActivity$m */
    /* loaded from: classes.dex */
    public class p implements CompoundButton.OnCheckedChangeListener {
        public p() {
        }

        @Override // android.widget.CompoundButton.OnCheckedChangeListener
        public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
            RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a, "haptics", z);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.q to com.parvaz.tunnel.SettingsActivity$n */
    /* loaded from: classes.dex */
    public class q implements View.OnClickListener {
        public q() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$17(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.r to com.parvaz.tunnel.SettingsActivity$o */
    /* loaded from: classes.dex */
    public class r implements ActivityResultCallback<Uri> {
        public r() {
        }

        @Override // androidx.activity.result.ActivityResultCallback
        /* renamed from: a */
        public final void onActivityResult(Uri uri) {
            Uri uri2 = uri;
            SettingsActivity settingsActivity = SettingsActivity.this;
            settingsActivity.getClass();
            if (uri2 != null) {
                try {
                    OutputStream openOutputStream = settingsActivity.getContentResolver().openOutputStream(uri2);
                    if (openOutputStream != null) {
                        openOutputStream.write(settingsActivity.f6163c.getBytes(StandardCharsets.UTF_8));
                        openOutputStream.flush();
                        Snackbar.make(settingsActivity.findViewById(R.id.save), settingsActivity.getString(R.string.backup_saved, uri2.getLastPathSegment()), 0).show();
                        openOutputStream.close();
                        return;
                    }
                    throw new IllegalStateException("stream");
                } catch (Exception e) {
                    Snackbar.make(settingsActivity.findViewById(R.id.save), settingsActivity.getString(R.string.backup_failed, String.valueOf(e.getMessage())), 0).show();
                }
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.s to com.parvaz.tunnel.SettingsActivity$p */
    /* loaded from: classes.dex */
    public class s implements ActivityResultCallback<Uri> {
        public s() {
        }

        @Override // androidx.activity.result.ActivityResultCallback
        /* renamed from: a */
        public final void onActivityResult(Uri uri) {
            Uri uri2 = uri;
            SettingsActivity settingsActivity = SettingsActivity.this;
            settingsActivity.getClass();
            if (uri2 != null) {
                MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(settingsActivity);
                materialAlertDialogBuilder.setMessage(R.string.restore_confirm);
                materialAlertDialogBuilder.setNegativeButton(R.string.cancel, null);
                materialAlertDialogBuilder.setPositiveButton(R.string.ok, new SettingsActivity_28(settingsActivity, uri2));
                materialAlertDialogBuilder.show();
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.t to com.parvaz.tunnel.SettingsActivity$q */
    /* loaded from: classes.dex */
    public class t implements View.OnClickListener {
        public t() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$21(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.u to com.parvaz.tunnel.SettingsActivity$r */
    /* loaded from: classes.dex */
    public class u implements View.OnClickListener {
        public u() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            SettingsActivity.this.lambda$onCreate$22(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.v to com.parvaz.tunnel.SettingsActivity$s */
    /* loaded from: classes.dex */
    public class v implements View.OnClickListener {

        /* renamed from: a */
        public final EditText f6186a;

        /* renamed from: b */
        public final EditText f6187b;

        /* renamed from: c */
        public final EditText f6188c;

        /* renamed from: d */
        public final EditText f6189d;

        /* renamed from: e */
        public final EditText f6190e;

        /* renamed from: f */
        public final EditText f6191f;
        public final EditText g;

        /* renamed from: h */
        public final EditText f6192h;

        /* renamed from: i */
        public final EditText f6193i;

        /* renamed from: j */
        public final EditText f6194j;

        /* renamed from: k */
        public final EditText f6195k;

        public v(EditText editText, EditText editText2, EditText editText3, EditText editText4, EditText editText5, EditText editText6, EditText editText7, EditText editText8, EditText editText9, EditText editText10, EditText editText11) {
            this.f6186a = editText;
            this.f6187b = editText2;
            this.f6188c = editText3;
            this.f6189d = editText4;
            this.f6190e = editText5;
            this.f6191f = editText6;
            this.g = editText7;
            this.f6192h = editText8;
            this.f6193i = editText9;
            this.f6194j = editText10;
            this.f6195k = editText11;
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            EditText editText = this.f6192h;
            SettingsActivity settingsActivity = SettingsActivity.this;
            if (this.f6186a != null) {
                RulesActivity__ExternalSyntheticOutline0.j(settingsActivity.C.f343a, "remote_dns", SettingsActivity.textOr(this.f6186a, "https://1.1.1.1/dns-query,https://dns.google/dns-query"));
            }
            if (this.f6187b != null) {
                RulesActivity__ExternalSyntheticOutline0.j(settingsActivity.C.f343a, "direct_dns", SettingsActivity.textOr(this.f6187b, "78.157.42.100"));
            }
            RulesActivity__ExternalSyntheticOutline0.j(settingsActivity.C.f343a, "ping_url", SettingsActivity.textOr(this.f6188c, "https://www.gstatic.com/generate_204"));
            settingsActivity.C.f343a.edit().putInt("vpn_mtu", SettingsActivity.intOr(this.f6189d, 1500, 576, 9000)).apply();
            settingsActivity.C.f343a.edit().putInt("mux_concurrency", SettingsActivity.intOr(this.f6190e, 8, 1, 128)).apply();
            settingsActivity.C.f343a.edit().putInt("ping_threshold", Math.max(200, SettingsActivity.intOr(this.f6191f, 1200, 200, 10000))).apply();
            settingsActivity.C.f343a.edit().putInt("health_interval", Math.max(5, SettingsActivity.intOr(this.g, 15, 5, 600))).apply();
            Prefs prefs = settingsActivity.C;
            float limit = 0.0f;
            if (editText != null) {
                try {
                    limit = Float.parseFloat(editText.getText().toString().trim());
                } catch (Exception unused) {
                    android.util.Log.w("Parvaz/SettingsActivity", "Exception ignored", unused);
                }
            }
            prefs.f343a.edit().putFloat("data_limit_gb", Math.max(0.0f, limit)).apply();
            RulesActivity__ExternalSyntheticOutline0.j(settingsActivity.C.f343a, "fragment_packets", SettingsActivity.textOr(this.f6193i, "tlshello"));
            RulesActivity__ExternalSyntheticOutline0.j(settingsActivity.C.f343a, "fragment_length", SettingsActivity.textOr(this.f6194j, "100-200"));
            RulesActivity__ExternalSyntheticOutline0.j(settingsActivity.C.f343a, "fragment_interval", SettingsActivity.textOr(this.f6195k, "10-20"));
            Snackbar.make(view, R.string.saved, -1).show();
            if (TunnelVpnService.serviceRunning) {
                Intent intent = new Intent(settingsActivity, (Class<?>) TunnelVpnService.class);
                intent.setAction("com.parvaz.tunnel.RESTART");
                settingsActivity.startService(intent);
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.w to com.parvaz.tunnel.SettingsActivity$t */
    /* loaded from: classes.dex */
    public class w implements e {
        public final Prefs a;

        public w(Prefs prefs) {
            this.a = prefs;
        }

        @Override // com.parvaz.tunnel.SettingsActivity.e
        public final void a(String str) {
            RulesActivity__ExternalSyntheticOutline0.j(this.a.f343a, "routing_mode", str);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.x to com.parvaz.tunnel.SettingsActivity$u */
    /* loaded from: classes.dex */
    public class x implements AdapterView.OnItemSelectedListener {

        /* renamed from: a */
        public boolean f6198a = true;
        public final e b;

        /* renamed from: c */
        public final String[] f6200c;

        public x(e eVar, String[] strArr) {
            this.b = eVar;
            this.f6200c = strArr;
        }

        @Override // android.widget.AdapterView.OnItemSelectedListener
        public final void onItemSelected(AdapterView<?> adapterView, View view, int i, long j) {
            if (this.f6198a) {
                this.f6198a = false;
            } else {
                this.b.a(this.f6200c[i]);
            }
        }

        @Override // android.widget.AdapterView.OnItemSelectedListener
        public final void onNothingSelected(AdapterView<?> adapterView) {
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.y to com.parvaz.tunnel.SettingsActivity$v */
    /* loaded from: classes.dex */
    public class y implements e {
        public y() {
        }

        @Override // com.parvaz.tunnel.SettingsActivity.e
        public final void a(String str) {
            SettingsActivity settingsActivity = SettingsActivity.this;
            if (str.equals(settingsActivity.C.f343a.getString("lang", "fa"))) {
                return;
            }
            settingsActivity.C.f343a.edit().putString("lang", str).apply();
            Intent intent = new Intent(settingsActivity, (Class<?>) MainActivity.class);
            intent.addFlags(335544320);
            settingsActivity.startActivity(intent);
            settingsActivity.finish();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.SettingsActivity.z to com.parvaz.tunnel.SettingsActivity$w */
    /* loaded from: classes.dex */
    public class z implements e {
        public final Prefs a;

        public z(Prefs prefs) {
            this.a = prefs;
        }

        @Override // com.parvaz.tunnel.SettingsActivity.e
        public final void a(String str) {
            RulesActivity__ExternalSyntheticOutline0.j(this.a.f343a, "log_level", str);
        }
    }

    /* renamed from: B */
    /** Parses the field, returning the default {@code i2} when empty, unparseable or out of [i3, i4]. */
    public static int intOr(EditText editText, int i2, int i3, int i4) {
        try {
            int parseInt = Integer.parseInt(editText.getText().toString().trim());
            return (parseInt < i3 || parseInt > i4) ? i2 : parseInt;
        } catch (Exception unused) {
            return i2;
        }
    }

    /* renamed from: C */
    public static String textOr(EditText editText, String str) {
        String trim = editText.getText().toString().trim();
        return trim.isEmpty() ? str : trim;
    }

    public final void A(Spinner spinner, int i2, String[] strArr, String str, e eVar) {
        ArrayAdapter<CharSequence> createFromResource = ArrayAdapter.createFromResource(this, i2, android.R.layout.simple_spinner_item);
        createFromResource.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter((SpinnerAdapter) createFromResource);
        int i3 = 0;
        for (int i4 = 0; i4 < strArr.length; i4++) {
            if (strArr[i4].equals(str)) {
                i3 = i4;
            }
        }
        spinner.setSelection(i3);
        spinner.setOnItemSelectedListener(new x(eVar, strArr));
    }

    @Override // androidx.appcompat.app.AppCompatActivity, android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    public final void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }

    public void lambda$onCreate$0(View view) {
        finish();
    }

    public void lambda$onCreate$10(View view) {
        startActivity(new Intent(this, (Class<?>) AppPickerActivity.class));
    }

    public void lambda$onCreate$11(View view) {
        startActivity(new Intent(this, (Class<?>) RulesActivity.class));
    }

    public void lambda$onCreate$12(View view) {
        startActivity(new Intent(this, (Class<?>) LogActivity.class));
    }

    public void lambda$onCreate$13(View view) {
        startActivity(new Intent(this, (Class<?>) UsageActivity.class));
    }

    public void lambda$onCreate$17(View view) {
        try {
            try {
                startActivity(new Intent("android.net.vpn.SETTINGS"));
            } catch (ActivityNotFoundException unused) {
                startActivity(new Intent("android.settings.VPN_SETTINGS"));
            }
        } catch (Exception unused2) {
            Snackbar.make(view, R.string.always_on_hint, 0).show();
        }
    }

    /**
     * Export. A backup carries every server credential the user owns, and these files
     * routinely get forwarded through Telegram or parked in a cloud drive, so the export
     * now offers AES-256-GCM encryption. Leaving the password blank keeps the old
     * plaintext JSON, which some users still want for hand-editing.
     */
    public void lambda$onCreate$21(final View view) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.backup_password_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_password_title)
                .setMessage(R.string.backup_password_body)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        char[] pw = null;
                        try {
                            String typed = input.getText() == null ? "" : input.getText().toString();
                            String json = BackupManager.export(SettingsActivity.this);
                            String suffix;
                            if (typed.length() == 0) {
                                SettingsActivity.this.f6163c = json;
                                suffix = ".json";
                            } else {
                                pw = typed.toCharArray();
                                SettingsActivity.this.f6163c =
                                        com.parvaz.tunnel.store.BackupCrypto.encrypt(json, pw);
                                suffix = ".pvz";
                            }
                            SettingsActivity.this.z.launch("parvaz-backup-"
                                    + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
                                            .format(new Date()) + suffix);
                        } catch (Exception e2) {
                            Snackbar.make(view, getString(R.string.backup_failed,
                                    String.valueOf(e2.getMessage())), 0).show();
                        } finally {
                            if (pw != null) {
                                java.util.Arrays.fill(pw, '\0');
                            }
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Restores a backup that turned out to be encrypted, prompting for the password and
     * reporting a wrong one distinctly from a corrupt file (GCM tells us which).
     */
    public void restoreEncrypted(final String envelope) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.backup_password_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_locked_title)
                .setMessage(R.string.backup_locked_body)
                .setView(wrap)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        char[] pw = null;
                        try {
                            String typed = input.getText() == null ? "" : input.getText().toString();
                            pw = typed.toCharArray();
                            String json = com.parvaz.tunnel.store.BackupCrypto.decrypt(envelope, pw);
                            BackupManager.a res = BackupManager.a(SettingsActivity.this, json);
                            Snackbar.make(findViewById(R.id.save),
                                    getString(R.string.backup_restored,
                                            Integer.valueOf(res.f341a), Integer.valueOf(res.f342b)),
                                    0).show();
                        } catch (javax.crypto.AEADBadTagException bad) {
                            Snackbar.make(findViewById(R.id.save), R.string.backup_bad_password, 0).show();
                        } catch (Exception e2) {
                            Snackbar.make(findViewById(R.id.save), getString(R.string.restore_failed,
                                    String.valueOf(e2.getMessage())), 0).show();
                        } finally {
                            if (pw != null) {
                                java.util.Arrays.fill(pw, '\0');
                            }
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void lambda$onCreate$22(View view) {
        this.A.launch(new String[]{"application/json", "text/plain", "*/*"});
    }

    public void lambda$onCreate$6(View view) {
        this.C.d();
        Snackbar.make(view, R.string.data_reset_done, -1).show();
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v130, types: [c.a, java.lang.Object] */
    /* JADX WARN: Type inference failed for: r0v132, types: [c.a, java.lang.Object] */
    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public final void onCreate(Bundle bundle) {
        String str;
        int i2;
        super.onCreate(bundle);
        setContentView(R.layout.activity_settings);
        this.C = new Prefs(this);
        findViewById(R.id.back).setOnClickListener(new n());
        Spinner spinner = (Spinner) findViewById(R.id.routing);
        String[] strArr = f6156e;
        String string = this.C.f343a.getString("routing_mode", "iran_direct");
        Prefs prefs = this.C;
        Objects.requireNonNull(prefs);
        A(spinner, R.array.routing_labels, strArr, string, new w(prefs));
        A((Spinner) findViewById(R.id.language), R.array.language_labels, f6158h, this.C.f343a.getString("lang", "fa"), new y());
        Spinner spinner2 = (Spinner) findViewById(R.id.log_level);
        String[] strArr2 = f6157f;
        String string2 = this.C.f343a.getString("log_level", "warning");
        Prefs prefs2 = this.C;
        Objects.requireNonNull(prefs2);
        A(spinner2, R.array.log_labels, strArr2, string2, new z(prefs2));
        Spinner spinner3 = (Spinner) findViewById(R.id.domain_strategy);
        String[] strArr3 = g;
        String string3 = this.C.f343a.getString("domain_strategy", "IPIfNonMatch");
        Prefs prefs3 = this.C;
        Objects.requireNonNull(prefs3);
        A(spinner3, R.array.strategy_labels, strArr3, string3, new A(prefs3));
        EditText editText = (EditText) findViewById(R.id.remote_dns);
        if (editText != null) {
            editText.setText(this.C.f343a.getString("remote_dns", "https://1.1.1.1/dns-query,https://dns.google/dns-query"));
        }
        EditText editText2 = (EditText) findViewById(R.id.direct_dns);
        if (editText2 != null) {
            editText2.setText(this.C.f343a.getString("direct_dns", "78.157.42.100"));
        }
        EditText editText3 = (EditText) findViewById(R.id.mtu);
        editText3.setText(String.valueOf(this.C.f343a.getInt("vpn_mtu", 1500)));
        EditText editText4 = (EditText) findViewById(R.id.ping_url);
        editText4.setText(this.C.f343a.getString("ping_url", "https://www.gstatic.com/generate_204"));
        EditText editText5 = (EditText) findViewById(R.id.mux_concurrency);
        editText5.setText(String.valueOf(this.C.f343a.getInt("mux_concurrency", 8)));
        SwitchCompat switchCompat = (SwitchCompat) findViewById(R.id.mux);
        switchCompat.setChecked(this.C.f343a.getBoolean("mux_enabled", false));
        switchCompat.setOnCheckedChangeListener(new B());
        SwitchCompat switchCompat2 = (SwitchCompat) findViewById(R.id.ipv6);
        switchCompat2.setChecked(this.C.f343a.getBoolean("ipv6_enabled", false));
        switchCompat2.setOnCheckedChangeListener(new C());
        SwitchCompat switchCompat3 = (SwitchCompat) findViewById(R.id.bypass_lan);
        switchCompat3.setChecked(this.C.f343a.getBoolean("bypass_lan", true));
        switchCompat3.setOnCheckedChangeListener(new C0031a());
        SwitchCompat switchCompat4 = (SwitchCompat) findViewById(R.id.auto_switch);
        switchCompat4.setChecked(this.C.f343a.getBoolean("auto_switch", true));
        switchCompat4.setOnCheckedChangeListener(new C0032b());
        EditText editText6 = (EditText) findViewById(R.id.ping_threshold);
        editText6.setText(String.valueOf(this.C.f343a.getInt("ping_threshold", 1200)));
        EditText editText7 = (EditText) findViewById(R.id.health_interval);
        editText7.setText(String.valueOf(this.C.f343a.getInt("health_interval", 15)));
        EditText editText8 = (EditText) findViewById(R.id.data_limit);
        editText8.setText(String.valueOf(this.C.f343a.getFloat("data_limit_gb", 0.0f)));
        findViewById(R.id.reset_data).setOnClickListener(new f());
        TextView textView = (TextView) findViewById(R.id.version);
        CoreManager.b().getClass();
        try {
            str = Libv2ray.checkVersionX();
        } catch (Throwable unused) {
            str = "unknown";
        }
        String appVersion;
        try {
            appVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception unused3) {
            appVersion = "1.8";
        }
        textView.setText(getString(R.string.version_fmt, appVersion, str));
        // ---- v1.8 tools -------------------------------------------------
        findViewById(R.id.btn_diagnose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateFlow.diagnose(SettingsActivity.this);
            }
        });
        findViewById(R.id.btn_tune_fragment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateFlow.tuneFragment(SettingsActivity.this);
            }
        });
        findViewById(R.id.btn_clear_memory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateFlow.clearMemory(SettingsActivity.this);
            }
        });
        findViewById(R.id.btn_check_update).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateFlow.checkForUpdate(SettingsActivity.this, false);
            }
        });

        SwitchCompat switchCompat5 = (SwitchCompat) findViewById(R.id.connect_on_boot);
        switchCompat5.setChecked(this.C.f343a.getBoolean("connect_on_boot", false));
        switchCompat5.setOnCheckedChangeListener(new C0033c());
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.fragment_box);
        EditText editText9 = (EditText) findViewById(R.id.fragment_packets);
        EditText editText10 = (EditText) findViewById(R.id.fragment_length);
        EditText editText11 = (EditText) findViewById(R.id.fragment_interval);
        editText9.setText(this.C.f343a.getString("fragment_packets", "tlshello"));
        editText10.setText(this.C.f343a.getString("fragment_length", "100-200"));
        editText11.setText(this.C.f343a.getString("fragment_interval", "10-20"));
        SwitchCompat switchCompat6 = (SwitchCompat) findViewById(R.id.tls_fragment);
        switchCompat6.setChecked(this.C.f343a.getBoolean("fragment_enabled", false));
        if (this.C.f343a.getBoolean("fragment_enabled", false)) {
            i2 = 0;
        } else {
            i2 = 8;
        }
        linearLayout.setVisibility(i2);
        switchCompat6.setOnCheckedChangeListener(new d(linearLayout));
        Spinner spinner4 = (Spinner) findViewById(R.id.sub_auto);
        String valueOf = String.valueOf(this.C.f343a.getInt("sub_auto_hours", 0));
        String[] strArr4 = new String[4];
        int i3 = 0;
        while (true) {
            int[] iArr = f6159i;
            if (i3 >= 4) {
                break;
            }
            strArr4[i3] = String.valueOf(iArr[i3]);
            i3++;
            editText10 = editText10;
            editText9 = editText9;
            editText8 = editText8;
        }
        EditText editText12 = editText10;
        EditText editText13 = editText9;
        EditText editText14 = editText8;
        A(spinner4, R.array.sub_auto_labels, strArr4, valueOf, new g());
        Spinner spinner5 = (Spinner) findViewById(R.id.split_mode);
        String[] strArr5 = f6160j;
        String string4 = this.C.f343a.getString("per_app_mode", "off");
        Prefs prefs4 = this.C;
        Objects.requireNonNull(prefs4);
        A(spinner5, R.array.split_mode_labels, strArr5, string4, new h(prefs4));
        findViewById(R.id.choose_apps).setOnClickListener(new i());
        findViewById(R.id.custom_rules).setOnClickListener(new j());
        findViewById(R.id.view_log).setOnClickListener(new k());
        findViewById(R.id.usage_chart).setOnClickListener(new l());
        SwitchCompat shakeSwitch = (SwitchCompat) findViewById(R.id.shake_switch);
        shakeSwitch.setChecked(this.C.f343a.getBoolean("shake_to_switch", false));
        shakeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                RulesActivity__ExternalSyntheticOutline0.k(SettingsActivity.this.C.f343a,
                        "shake_to_switch", checked);
            }
        });
        SwitchCompat switchCompat7 = (SwitchCompat) findViewById(R.id.app_lock);
        switchCompat7.setChecked(this.C.f343a.getBoolean("app_lock", false));
        switchCompat7.setOnCheckedChangeListener(new m());
        Spinner spinner6 = (Spinner) findViewById(R.id.chain_entry);
        ArrayList e2 = ProfileStore.f(this).e();
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        arrayList.add(getString(R.string.chain_off));
        arrayList2.add("");
        Iterator it = e2.iterator();
        while (it.hasNext()) {
            Profile profile = (Profile) it.next();
            arrayList.add(profile.remark);
            arrayList2.add(profile.id);
        }
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner6.setAdapter((SpinnerAdapter) arrayAdapter);
        spinner6.setSelection(Math.max(0, arrayList2.indexOf(this.C.f343a.getString("chain_profile", ""))));
        spinner6.setOnItemSelectedListener(new SettingsActivity_29(this, arrayList2));
        SwitchCompat switchCompat8 = (SwitchCompat) findViewById(R.id.kill_switch);
        switchCompat8.setChecked(this.C.f343a.getBoolean("kill_switch", false));
        switchCompat8.setOnCheckedChangeListener(new o());
        SwitchCompat switchCompat9 = (SwitchCompat) findViewById(R.id.haptics);
        switchCompat9.setChecked(this.C.f343a.getBoolean("haptics", true));
        switchCompat9.setOnCheckedChangeListener(new p());
        findViewById(R.id.open_vpn_settings).setOnClickListener(new q());
        this.z = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), new r());
        this.A = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), new s());
        findViewById(R.id.backup_export).setOnClickListener(new t());
        findViewById(R.id.backup_import).setOnClickListener(new u());
        this.originalSave = new v(editText, editText2, editText4, editText3, editText5, editText6, editText7, editText14, editText13, editText12, editText11);
        bindAdvancedSection();
    }

    // ---------------------------------------------------------------------
    // Advanced section: DNS, tuning knobs and the entry points to the domain
    // rules / auto-profile / leak-test screens. These prefs were previously
    // readable only by editing the config, with no UI at all.
    // ---------------------------------------------------------------------

    private EditText remoteDnsInput;
    private EditText directDnsInput;
    private EditText bufferSizeInput;
    private EditText healthStrikesInput;
    private TextView dnsHint;

    private void bindAdvancedSection() {
        remoteDnsInput = (EditText) findViewById(R.id.remote_dns_input);
        directDnsInput = (EditText) findViewById(R.id.direct_dns_input);
        bufferSizeInput = (EditText) findViewById(R.id.buffer_size_input);
        healthStrikesInput = (EditText) findViewById(R.id.health_strikes_input);
        dnsHint = (TextView) findViewById(R.id.dns_hint);

        if (remoteDnsInput != null) {
            remoteDnsInput.setText(this.C.f343a.getString("remote_dns",
                    "https://1.1.1.1/dns-query,https://dns.google/dns-query"));
            refreshDnsHint();
            remoteDnsInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence cs, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence cs, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(android.text.Editable e) {
                    refreshDnsHint();
                }
            });
        }
        if (directDnsInput != null) {
            directDnsInput.setText(this.C.f343a.getString("direct_dns", "78.157.42.100"));
        }
        if (bufferSizeInput != null) {
            bufferSizeInput.setText(String.valueOf(this.C.f343a.getInt("buffer_size_kb", 512)));
        }
        if (healthStrikesInput != null) {
            healthStrikesInput.setText(String.valueOf(this.C.f343a.getInt("health_strikes", 3)));
        }

        View leak = findViewById(R.id.btn_leaktest);
        if (leak != null) {
            leak.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v2) {
                    startActivity(new Intent(SettingsActivity.this, LeakTestActivity.class));
                }
            });
        }
        View domains = findViewById(R.id.btn_domain_rules);
        if (domains != null) {
            domains.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v2) {
                    startActivity(new Intent(SettingsActivity.this, DomainRulesActivity.class));
                }
            });
        }
        View autoProfile = findViewById(R.id.btn_auto_profile);
        if (autoProfile != null) {
            autoProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v2) {
                    startActivity(new Intent(SettingsActivity.this, AutoProfileActivity.class));
                }
            });
        }

        // One save button for the whole screen: write the advanced prefs first,
        // then hand off to the listener that already handled everything else.
        View save = findViewById(R.id.save);
        if (save != null) {
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v2) {
                    persistAdvanced();
                    SettingsActivity.this.runOriginalSave();
                }
            });
        }
    }

    /** Tells the user at a glance whether their resolver is encrypted. */
    private void refreshDnsHint() {
        if (dnsHint == null || remoteDnsInput == null) {
            return;
        }
        String value = remoteDnsInput.getText().toString();
        boolean encrypted = value.contains("https://") || value.contains("tls://")
                || value.contains("quic://") || value.contains("h2c://");
        dnsHint.setText(encrypted ? R.string.dns_doh_hint : R.string.dns_plain_hint);
        dnsHint.setTextColor(encrypted ? 0xFF2E7D32 : 0xFFC62828);
    }

    private void persistAdvanced() {
        android.content.SharedPreferences.Editor ed = this.C.f343a.edit();
        if (remoteDnsInput != null) {
            String v2 = remoteDnsInput.getText().toString().trim();
            if (!v2.isEmpty()) {
                ed.putString("remote_dns", v2);
            }
        }
        if (directDnsInput != null) {
            String v2 = directDnsInput.getText().toString().trim();
            if (!v2.isEmpty()) {
                ed.putString("direct_dns", v2);
            }
        }
        if (bufferSizeInput != null) {
            ed.putInt("buffer_size_kb", clampInt(bufferSizeInput.getText().toString(), 512, 8, 8192));
        }
        if (healthStrikesInput != null) {
            ed.putInt("health_strikes", clampInt(healthStrikesInput.getText().toString(), 3, 1, 20));
        }
        ed.apply();
    }

    private static int clampInt(String raw, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min) {
                return min;
            }
            if (parsed > max) {
                return max;
            }
            return parsed;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** The screen's original save listener, kept so it still runs. */
    private View.OnClickListener originalSave;

    private void runOriginalSave() {
        if (originalSave != null) {
            originalSave.onClick(findViewById(R.id.save));
        }
    }
}
