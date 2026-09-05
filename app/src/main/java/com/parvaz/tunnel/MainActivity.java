package com.parvaz.tunnel;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.impl.WorkManagerImplExtKt;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.EncodeHintType;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;
import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.RulesActivity__ExternalSyntheticOutline0;
import com.parvaz.tunnel.core.CrashReporter;
import com.parvaz.tunnel.core.PingManager;
import com.parvaz.tunnel.core.PingManager_3;
import com.parvaz.tunnel.core.PingManager_4;
import com.parvaz.tunnel.core.SafeMode;
import com.parvaz.tunnel.core.SpeedTester;
import com.parvaz.tunnel.core.SpeedTester_1;
import com.parvaz.tunnel.core.SpeedTester_6;
import com.parvaz.tunnel.core.SubscriptionUpdater;
import com.parvaz.tunnel.core.SubscriptionUpdater_4;
import com.parvaz.tunnel.core.TunnelVpnService;
import com.parvaz.tunnel.core.RealBypassTester;
import com.parvaz.tunnel.core.QuotaNotifier;
import com.parvaz.tunnel.core.RealBypassTester;
import com.parvaz.tunnel.core.ServerMemory;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.model.Subscription;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.store.ProfileStore;
import com.parvaz.tunnel.ui.FlagUtil;
import com.parvaz.tunnel.ui.ServerAdapter;
import com.parvaz.tunnel.R;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/* loaded from: classes.dex */
public class MainActivity extends AppCompatActivity {

    /* renamed from: q0 */
    public static final /* synthetic */ int $r8$clinit = 0;

    /* renamed from: A */
    public View connectButton;

    /* renamed from: B */
    public TextView emptyText;

    /* renamed from: C */
    public TextView favFilter;

    /* renamed from: D */
    public TextView ipText;

    /* renamed from: E */
    public RecyclerView list;
    public ActivityResultLauncher<String> F;

    /* renamed from: G */
    public View pageHome;

    /* renamed from: H */
    public View pageServers;

    /* renamed from: I */
    public ImageButton pingAllButton;

    /* renamed from: J */
    public ProgressBar pingAllProgress;
    public PingManager K;
    public Prefs L;

    /** Live throughput graph on the home page; null-safe everywhere. */
    public com.parvaz.tunnel.ui.SparklineView sparkline;

    /* renamed from: M */
    public AnimatorSet pulseAnimator;

    /* renamed from: N */
    public boolean pulseFast;

    /* renamed from: O */
    public View pulseRing;
    public ActivityResultLauncher<Intent> P;

    /* renamed from: Q */
    public ProgressBar quotaBar;

    /* renamed from: R */
    public TextView quotaLeftText;

    /* renamed from: S */
    public TextView quotaPercentText;

    /* renamed from: T */
    public TextView quotaUsedText;
    public TextView serviceTitleText;
    public TextView quotaConsumedText;
    public TextView quotaExpireDateText;
    public TextView quotaWarningText;

    /* renamed from: U */
    public SwipeRefreshLayout refresh;

    /* renamed from: V */
    public EditText searchInput;

    /* renamed from: W */
    public TextView serverText;

    /* renamed from: X */
    public TextView speedTestButton;
    public SpeedTester Y;

    /* renamed from: Z */
    public TextView speedText;

    /* renamed from: a0 */
    public TextView statusText;
    public ProfileStore b0;

    /* renamed from: c0 */
    public View tabHome;

    /* renamed from: d0 */
    public ImageView tabHomeIcon;

    /* renamed from: e0 */
    public TextView tabHomeLabel;

    /* renamed from: f0 */
    public View tabServers;

    /* renamed from: g0 */
    public ImageView tabServersIcon;

    /* renamed from: h0 */
    public TextView tabServersLabel;

    /* renamed from: i0 */
    public TextView timerText;
    public ActivityResultLauncher<Intent> j0;

    /* renamed from: k0 */
    public int state = 0;

    /* renamed from: l0 */
    public String query = "";

    /* renamed from: m0 */
    public boolean favOnly = false;

    /* renamed from: n0 */
    public boolean unlocked = false;

    /* renamed from: o0 */
    public int currentTab = 0;
    public final C0027i p0 = new C0027i();
    public ServerAdapter z;

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.A to com.parvaz.tunnel.MainActivity$n */
    /* loaded from: classes.dex */
    public class A implements Runnable {
        public A() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            MainActivity.this.getApplicationContext().getSharedPreferences("parvaz_safemode", 0).edit().putInt("pending_launches", 0).putBoolean("safe_active", false).commit();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.B to com.parvaz.tunnel.MainActivity$o */
    /* loaded from: classes.dex */
    public class B implements DialogInterface.OnClickListener {
        public B() {
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            CrashReporter.clear(MainActivity.this);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.C to com.parvaz.tunnel.MainActivity$p */
    /* loaded from: classes.dex */
    public class C implements DialogInterface.OnClickListener {

        /* renamed from: a */
        public final File f6124a;

        public C(File file) {
            this.f6124a = file;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            MainActivity mainActivity = MainActivity.this;
            Intent intent = new Intent(mainActivity, (Class<?>) CrashActivity.class);
            intent.putExtra("report", CrashReporter.read(this.f6124a));
            mainActivity.startActivity(intent);
            CrashReporter.clear(mainActivity);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.D to com.parvaz.tunnel.MainActivity$q */
    /* loaded from: classes.dex */
    public class D implements Runnable {
        public D() {
        }

        @Override // java.lang.Runnable
        public final void run() {
            MainActivity.this.toggle();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.E to com.parvaz.tunnel.MainActivity$s */
    /* loaded from: classes.dex */
    public class E implements Comparator {
        public E() {
        }

        @Override // java.util.Comparator
        public final int compare(Object obj, Object obj2) {
            MainActivity mainActivity = MainActivity.this;
            boolean contains = mainActivity.L.getFavorites().contains(((Profile) obj).id);
            if (contains == mainActivity.L.getFavorites().contains(((Profile) obj2).id)) {
                return 0;
            }
            if (contains) {
                return -1;
            }
            return 1;
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.F to com.parvaz.tunnel.MainActivity$t */
    /* loaded from: classes.dex */
    public class F implements DialogInterface.OnClickListener {
        public F() {
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            Snackbar make;
            String string;
            MainActivity mainActivity = MainActivity.this;
            mainActivity.getClass();
            String str = "";
            if (i == 0) {
                try {
                    ActivityResultLauncher<Intent> activityResultLauncher = mainActivity.P;
                    ScanOptions scanOptions = new ScanOptions();
                    scanOptions.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
                    scanOptions.setPrompt("");
                    scanOptions.setBeepEnabled(false);
                    scanOptions.setOrientationLocked(false);
                    scanOptions.setCaptureActivity(CaptureActivity.class);
                    activityResultLauncher.launch(scanOptions.createScanIntent(mainActivity));
                    return;
                } catch (Exception unused) {
                    Snackbar.make(mainActivity.connectButton, R.string.qr_no_camera, 0).show();
                    return;
                }
            }
            if (i == 1) {
                ClipboardManager clipboardManager = (ClipboardManager) mainActivity.getSystemService("clipboard");
                if (clipboardManager != null && clipboardManager.getPrimaryClip() != null && clipboardManager.getPrimaryClip().getItemCount() != 0) {
                    CharSequence coerceToText = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(mainActivity);
                    if (coerceToText != null) {
                        str = coerceToText.toString();
                    }
                    int importText = mainActivity.importText(str);
                    View view = mainActivity.connectButton;
                    if (importText > 0) {
                        string = mainActivity.getString(R.string.imported_n, Integer.valueOf(importText));
                    } else {
                        string = mainActivity.getString(R.string.import_failed);
                    }
                    make = Snackbar.make(view, string, 0);
                } else {
                    make = Snackbar.make(mainActivity.connectButton, R.string.clipboard_empty, 0);
                }
                make.show();
                return;
            }
            if (i == 2) {
                View inflate = mainActivity.getLayoutInflater().inflate(R.layout.dialog_input, (ViewGroup) null);
                TextInputEditText textInputEditText = (TextInputEditText) inflate.findViewById(R.id.input);
                textInputEditText.setHint(R.string.hint_link);
                MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(mainActivity);
                materialAlertDialogBuilder.setTitle(R.string.add_manual_link);
                materialAlertDialogBuilder.setView(inflate);
                materialAlertDialogBuilder.setNegativeButton(R.string.cancel, null);
                materialAlertDialogBuilder.setPositiveButton(R.string.ok, new G(textInputEditText));
                materialAlertDialogBuilder.show();
                return;
            }
            if (i == 3) {
                View inflate2 = mainActivity.getLayoutInflater().inflate(R.layout.dialog_input, (ViewGroup) null);
                TextInputEditText textInputEditText2 = (TextInputEditText) inflate2.findViewById(R.id.input);
                textInputEditText2.setHint(R.string.hint_raw_json);
                textInputEditText2.setSingleLine(false);
                textInputEditText2.setMaxLines(8);
                MaterialAlertDialogBuilder materialAlertDialogBuilder2 = new MaterialAlertDialogBuilder(mainActivity);
                materialAlertDialogBuilder2.setTitle(R.string.add_raw_json);
                materialAlertDialogBuilder2.setView(inflate2);
                materialAlertDialogBuilder2.setNegativeButton(R.string.cancel, null);
                materialAlertDialogBuilder2.setPositiveButton(R.string.ok, new L(textInputEditText2));
                materialAlertDialogBuilder2.show();
                return;
            }
            if (i == 4) {
                View inflate3 = mainActivity.getLayoutInflater().inflate(R.layout.dialog_input, (ViewGroup) null);
                TextInputEditText textInputEditText3 = (TextInputEditText) inflate3.findViewById(R.id.input);
                textInputEditText3.setHint(R.string.hint_subscription);
                MaterialAlertDialogBuilder materialAlertDialogBuilder3 = new MaterialAlertDialogBuilder(mainActivity);
                materialAlertDialogBuilder3.setTitle(R.string.add_subscription);
                materialAlertDialogBuilder3.setView(inflate3);
                materialAlertDialogBuilder3.setNegativeButton(R.string.cancel, null);
                materialAlertDialogBuilder3.setPositiveButton(R.string.ok, new I(textInputEditText3));
                materialAlertDialogBuilder3.show();
                return;
            }
            if (i == 5) {
                mainActivity.updateSubscriptions();
            } else {
                mainActivity.startActivity(new Intent(mainActivity, (Class<?>) SettingsActivity.class));
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.G to com.parvaz.tunnel.MainActivity$u */
    /* loaded from: classes.dex */
    public class G implements DialogInterface.OnClickListener {

        /* renamed from: a */
        public final TextInputEditText f6130a;

        public G(TextInputEditText textInputEditText) {
            this.f6130a = textInputEditText;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            String obj;
            String string;
            MainActivity mainActivity = MainActivity.this;
            mainActivity.getClass();
            TextInputEditText textInputEditText = this.f6130a;
            if (textInputEditText.getText() == null) {
                obj = "";
            } else {
                obj = textInputEditText.getText().toString();
            }
            int importText = mainActivity.importText(obj);
            View view = mainActivity.connectButton;
            if (importText > 0) {
                string = mainActivity.getString(R.string.imported_n, Integer.valueOf(importText));
            } else {
                string = mainActivity.getString(R.string.import_failed);
            }
            Snackbar.make(view, string, 0).show();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.H to com.parvaz.tunnel.MainActivity$v */
    /* loaded from: classes.dex */
    public class H implements View.OnLongClickListener {
        public H() {
        }

        @Override // android.view.View.OnLongClickListener
        public final boolean onLongClick(View view) {
            MainActivity mainActivity = MainActivity.this;
            mainActivity.getClass();
            MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(mainActivity);
            materialAlertDialogBuilder.setTitle(R.string.data_reset);
            materialAlertDialogBuilder.setPositiveButton(R.string.ok, new z());
            materialAlertDialogBuilder.setNegativeButton(R.string.cancel, null);
            materialAlertDialogBuilder.show();
            return true;
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.I to com.parvaz.tunnel.MainActivity$w */
    /* loaded from: classes.dex */
    public class I implements DialogInterface.OnClickListener {

        /* renamed from: a */
        public final TextInputEditText f6133a;

        public I(TextInputEditText textInputEditText) {
            this.f6133a = textInputEditText;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            String trim;
            MainActivity mainActivity = MainActivity.this;
            TextInputEditText textInputEditText = this.f6133a;
            mainActivity.getClass();
            if (textInputEditText.getText() == null) {
                trim = "";
            } else {
                trim = textInputEditText.getText().toString().trim();
            }
            if (!TextUtils.isEmpty(trim)) {
                Subscription subscription = new Subscription();
                subscription.id = UUID.randomUUID().toString();
                subscription.url = trim;
                if (Uri.parse(trim).getHost() != null) {
                    trim = Uri.parse(trim).getHost();
                }
                subscription.name = trim;
                ProfileStore profileStore = mainActivity.b0;
                synchronized (profileStore) {
                    profileStore.f347c.add(subscription);
                    profileStore.h();
                }
                mainActivity.updateSubscriptions();
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.J to com.parvaz.tunnel.MainActivity$x */
    /* loaded from: classes.dex */
    public class J implements SubscriptionUpdater.a {
        public J() {
        }

        @Override // com.parvaz.tunnel.core.SubscriptionUpdater.a
        public final void a(String str, int i) {
            String string;
            MainActivity mainActivity = MainActivity.this;
            mainActivity.refresh.setRefreshing(false);
            mainActivity.reload();
            View view = mainActivity.connectButton;
            if (str != null) {
                string = mainActivity.getString(R.string.update_failed, str);
            } else {
                string = mainActivity.getString(R.string.imported_n, Integer.valueOf(i));
            }
            Snackbar.make(view, string, 0).show();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.K to com.parvaz.tunnel.MainActivity$y */
    /* loaded from: classes.dex */
    public class K {

        public MainActivity outer() {
            return MainActivity.this;
        }
        public K() {
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.L to com.parvaz.tunnel.MainActivity$z */
    /* loaded from: classes.dex */
    public class L implements DialogInterface.OnClickListener {

        /* renamed from: a */
        public final TextInputEditText f6137a;

        public L(TextInputEditText textInputEditText) {
            this.f6137a = textInputEditText;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            String obj;
            String string;
            MainActivity mainActivity = MainActivity.this;
            mainActivity.getClass();
            TextInputEditText textInputEditText = this.f6137a;
            if (textInputEditText.getText() == null) {
                obj = "";
            } else {
                obj = textInputEditText.getText().toString();
            }
            int importText = mainActivity.importText(obj);
            View view = mainActivity.connectButton;
            if (importText > 0) {
                string = mainActivity.getString(R.string.imported_n, Integer.valueOf(importText));
            } else {
                string = mainActivity.getString(R.string.import_failed);
            }
            Snackbar.make(view, string, 0).show();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.a to com.parvaz.tunnel.MainActivity$1 */
    /* renamed from: com.parvaz.tunnel.MainActivity$a, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0019a implements Comparator<Profile> {
        @Override // java.util.Comparator
        public final int compare(Profile profile, Profile profile2) {
            Profile profile3 = profile2;
            int i = profile.ping;
            int i2 = Integer.MAX_VALUE;
            if (i <= 0) {
                i = Integer.MAX_VALUE;
            }
            int i3 = profile3.ping;
            if (i3 > 0) {
                i2 = i3;
            }
            return Integer.compare(i, i2);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.b to com.parvaz.tunnel.MainActivity$2 */
    /* renamed from: com.parvaz.tunnel.MainActivity$b, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0020b extends BiometricPrompt.AuthenticationCallback {
        public C0020b() {
        }

        @Override // androidx.biometric.BiometricPrompt.AuthenticationCallback
        public final void onAuthenticationError(int errorCode, CharSequence errString) {
            MainActivity.this.finishAndRemoveTask();
        }

        @Override // androidx.biometric.BiometricPrompt.AuthenticationCallback
        public final void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            MainActivity mainActivity = MainActivity.this;
            mainActivity.unlocked = true;
            mainActivity.findViewById(R.id.lock_shade).setVisibility(8);
            mainActivity.maybeAutoConnect();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.c to com.parvaz.tunnel.MainActivity$A */
    /* renamed from: com.parvaz.tunnel.MainActivity$c, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0021c {

        public MainActivity outer() {
            return MainActivity.this;
        }
        public C0021c() {
        }

        public final void a(double d, String str) {
            MainActivity mainActivity = MainActivity.this;
            mainActivity.speedTestButton.setEnabled(true);
            if (str == null) {
                mainActivity.speedTestButton.setText(mainActivity.getString(R.string.speed_result, Double.valueOf(d)));
            } else {
                mainActivity.speedTestButton.setText(R.string.speed_test);
                Snackbar.make(mainActivity.connectButton, mainActivity.getString(R.string.speed_failed, str), 0).show();
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.d to com.parvaz.tunnel.MainActivity$B */
    /* renamed from: com.parvaz.tunnel.MainActivity$d, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0022d {

        public MainActivity outer() {
            return MainActivity.this;
        }
        public C0022d() {
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.e to com.parvaz.tunnel.MainActivity$C0193a */
    /* renamed from: com.parvaz.tunnel.MainActivity$e, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0023e implements TextWatcher {
        public C0023e() {
        }

        @Override // android.text.TextWatcher
        public final void afterTextChanged(Editable editable) {
        }

        @Override // android.text.TextWatcher
        public final void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        }

        @Override // android.text.TextWatcher
        public final void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            String lowerCase = charSequence == null ? "" : charSequence.toString().trim().toLowerCase(Locale.getDefault());
            MainActivity mainActivity = MainActivity.this;
            mainActivity.query = lowerCase;
            mainActivity.reload();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.f to com.parvaz.tunnel.MainActivity$C0198f */
    /* renamed from: com.parvaz.tunnel.MainActivity$f, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0024f implements ActivityResultCallback<ActivityResult> {
        public C0024f() {
        }

        @Override // androidx.activity.result.ActivityResultCallback
        /* renamed from: a */
        public final void onActivityResult(ActivityResult activityResult) {
            MainActivity mainActivity = MainActivity.this;
            mainActivity.getClass();
            if (activityResult.getResultCode() == -1) {
                mainActivity.startVpn("com.parvaz.tunnel.START");
            } else {
                Snackbar.make(mainActivity.connectButton, R.string.err_no_permission, 0).show();
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.g to com.parvaz.tunnel.MainActivity$C0199g */
    /* renamed from: com.parvaz.tunnel.MainActivity$g, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0025g implements ActivityResultCallback<Boolean> {
        @Override // androidx.activity.result.ActivityResultCallback
        /* renamed from: a */
        public final void onActivityResult(Boolean bool) {
            int i = MainActivity.$r8$clinit;
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.h to com.parvaz.tunnel.MainActivity$C0200h */
    /* renamed from: com.parvaz.tunnel.MainActivity$h, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0026h implements ActivityResultCallback<ActivityResult> {
        public C0026h() {
        }

        @Override // androidx.activity.result.ActivityResultCallback
        /* renamed from: a */
        public final void onActivityResult(ActivityResult activityResult) {
            String string;
            ActivityResult activityResult2 = activityResult;
            MainActivity mainActivity = MainActivity.this;
            mainActivity.getClass();
            int i = activityResult2.getResultCode();
            Intent intent = activityResult2.getData();
            int i2 = -1;
            if (i != -1) {
                i2 = 0;
            }
            String str = null;
            try {
                String str2 = ScanIntentResult.parseActivityResult(i2, intent).getContents();
                if (str2 != null && !str2.trim().isEmpty()) {
                    str = str2.trim();
                }
            } catch (Throwable unused) {
                android.util.Log.w("Parvaz/MainActivity", "Throwable ignored", unused);
            }
            int importText = mainActivity.importText(str);
            View view = mainActivity.connectButton;
            if (importText <= 0) {
                string = mainActivity.getString(R.string.imported_n, Integer.valueOf(importText));
            } else {
                string = mainActivity.getString(R.string.import_failed);
            }
            Snackbar.make(view, string, 0).show();
            mainActivity.importText(str);
            Snackbar.make(mainActivity.connectButton, string, 0).show();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.i to com.parvaz.tunnel.MainActivity$C0203k */
    /* renamed from: com.parvaz.tunnel.MainActivity$i, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0027i extends BroadcastReceiver {
        public C0027i() {
        }

        @Override // android.content.BroadcastReceiver
        public final void onReceive(Context context, Intent intent) {
            ServerAdapter serverAdapter;
            int intExtra = intent.getIntExtra("state", 0);
            MainActivity mainActivity = MainActivity.this;
            if (intExtra == 4) {
                // Two different senders use state 4: the 1 Hz stats ticker (carries
                // uplink/downlink/duration) and the 15 s health probe (carries only
                // "ping"). Reading the traffic extras unconditionally made the health
                // probe blank the speed line and reset the timer to 00:00:00 every
                // 15 seconds, so only apply them when this really is a stats tick.
                if (intent.hasExtra("uplink")) {
                    long longExtra = intent.getLongExtra("uplink", 0L);
                    long longExtra2 = intent.getLongExtra("downlink", 0L);
                    long longExtra3 = intent.getLongExtra("duration", 0L);
                    mainActivity.speedText.setText("↓ " + MainActivity.fmtSpeed(longExtra2) + "    ↑ " + MainActivity.fmtSpeed(longExtra));
                    mainActivity.timerText.setText(String.format(Locale.US, "%02d:%02d:%02d", Long.valueOf(longExtra3 / 3600), Long.valueOf((longExtra3 % 3600) / 60), Long.valueOf(longExtra3 % 60)));
                    if (mainActivity.sparkline != null) {
                        mainActivity.sparkline.push(longExtra2, longExtra);
                        mainActivity.sparkline.setVisibility(View.VISIBLE);
                    }
                    mainActivity.renderQuota();
                }
                int intExtra2 = intent.getIntExtra("ping", 0);
                String stringExtra = intent.getStringExtra("profile_id");
                if (intExtra2 > 0 && stringExtra != null && (serverAdapter = mainActivity.z) != null) {
                    serverAdapter.i(stringExtra);
                    return;
                }
                return;
            }
            mainActivity.state = intExtra;
            if (intExtra == 5) {
                mainActivity.renderState();
                String stringExtra2 = intent.getStringExtra("message");
                if (TextUtils.isEmpty(stringExtra2)) {
                    return;
                }
                Snackbar.make(mainActivity.connectButton, mainActivity.getString(R.string.switching_to, stringExtra2), -1).show();
                return;
            }
            String stringExtra3 = intent.getStringExtra("message");
            mainActivity.renderState();
            if (intExtra == 2) {
                // Connected: show the exit address straight away rather than
                // waiting for the user to tap the IP field.
                mainActivity.autoRefreshIp();
            }
            if (intExtra == 3 && !TextUtils.isEmpty(stringExtra3)) {
                Snackbar.make(mainActivity.connectButton, stringExtra3, 0).show();
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.j to com.parvaz.tunnel.MainActivity$C */
    /* renamed from: com.parvaz.tunnel.MainActivity$j, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class DialogInterfaceOnClickListenerC0028j implements DialogInterface.OnClickListener {

        /* renamed from: a */
        public final Profile f6100a;

        public DialogInterfaceOnClickListenerC0028j(Profile profile) {
            this.f6100a = profile;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            String str;
            Bitmap bitmap;
            int i2;
            MainActivity mainActivity = MainActivity.this;
            Profile profile = this.f6100a;
            mainActivity.getClass();
            if (i == 0) {
                Intent intent = new Intent("android.intent.action.SEND");
                intent.setType("text/plain");
                intent.putExtra("android.intent.extra.TEXT", profile.rawLink);
                mainActivity.startActivity(Intent.createChooser(intent, mainActivity.getString(R.string.share_link)));
                return;
            }
            int i3 = 0;
            if (i == 1) {
                String str2 = profile.rawLink;
                if (str2 != null && !str2.isEmpty()) {
                    str = profile.rawLink;
                } else {
                    str = profile.rawJson;
                }
                if (str != null && !str.isEmpty()) {
                    try {
                        HashMap hashMap = new HashMap();
                        hashMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                        hashMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                        hashMap.put(EncodeHintType.MARGIN, 1);
                        BitMatrix q = new MultiFormatWriter().encode(str, BarcodeFormat.QR_CODE, 640, 640, hashMap);
                        int i4 = q.getWidth();
                        int i5 = q.getHeight();
                        int[] iArr = new int[i4 * i5];
                        for (int i6 = 0; i6 < i5; i6++) {
                            int i7 = i6 * i4;
                            for (int i8 = 0; i8 < i4; i8++) {
                                int i9 = i7 + i8;
                                if (q.get(i8, i6)) {
                                    i2 = -16777216;
                                } else {
                                    i2 = -1;
                                }
                                iArr[i9] = i2;
                            }
                        }
                        bitmap = Bitmap.createBitmap(i4, i5, Bitmap.Config.ARGB_8888);
                        bitmap.setPixels(iArr, 0, i4, 0, 0, i4, i5);
                    } catch (Throwable unused) {
                        bitmap = null;
                    }
                    if (bitmap != null) {
                        ImageView imageView = new ImageView(mainActivity);
                        imageView.setImageBitmap(bitmap);
                        int i10 = (int) (mainActivity.getResources().getDisplayMetrics().density * 16.0f);
                        imageView.setPadding(i10, i10, i10, i10);
                        imageView.setAdjustViewBounds(true);
                        MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(mainActivity);
                        materialAlertDialogBuilder.setTitle(profile.remark);
                        materialAlertDialogBuilder.setView(imageView);
                        materialAlertDialogBuilder.setPositiveButton(R.string.ok, null);
                        materialAlertDialogBuilder.show();
                        return;
                    }
                }
                Snackbar.make(mainActivity.connectButton, R.string.import_failed, -1).show();
                return;
            }
            if (i == 2) {
                ProfileStore profileStore = mainActivity.b0;
                String str4 = profile.id;
                synchronized (profileStore) {
                    while (true) {
                        if (i3 >= profileStore.f346b.size()) {
                            break;
                        }
                        if (((Profile) profileStore.f346b.get(i3)).id.equals(str4)) {
                            profileStore.f346b.remove(i3);
                            break;
                        }
                        i3++;
                    }
                    profileStore.h();
                }
                mainActivity.reload();
                return;
            }
            MaterialAlertDialogBuilder materialAlertDialogBuilder2 = new MaterialAlertDialogBuilder(mainActivity);
            materialAlertDialogBuilder2.setMessage(R.string.confirm_delete_all);
            materialAlertDialogBuilder2.setNegativeButton(R.string.cancel, null);
            materialAlertDialogBuilder2.setPositiveButton(R.string.ok, new DialogInterfaceOnClickListenerC0029k());
            materialAlertDialogBuilder2.show();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.k to com.parvaz.tunnel.MainActivity$D */
    /* renamed from: com.parvaz.tunnel.MainActivity$k, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class DialogInterfaceOnClickListenerC0029k implements DialogInterface.OnClickListener {
        public DialogInterfaceOnClickListenerC0029k() {
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            MainActivity mainActivity = MainActivity.this;
            ProfileStore profileStore = mainActivity.b0;
            synchronized (profileStore) {
                profileStore.f346b.clear();
                profileStore.h();
            }
            mainActivity.reload();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.l to com.parvaz.tunnel.MainActivity$E */
    /* renamed from: com.parvaz.tunnel.MainActivity$l, reason: case insensitive filesystem */
    /* loaded from: classes.dex */
    public class C0030l implements ServerAdapter.a {
        public C0030l() {
        }

        public MainActivity outer() {
            return MainActivity.this;
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.m to com.parvaz.tunnel.MainActivity$F */
    /* loaded from: classes.dex */
    public class m implements SwipeRefreshLayout.OnRefreshListener {
        public m() {
        }

        @Override // androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
        public void onRefresh() {
            MainActivity.this.pingAll();
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.n to com.parvaz.tunnel.MainActivity$G */
    /* loaded from: classes.dex */
    public class n implements View.OnClickListener {
        public n() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$2(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.o to com.parvaz.tunnel.MainActivity$H */
    /* loaded from: classes.dex */
    public class o implements View.OnClickListener {
        public o() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$3(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.p to com.parvaz.tunnel.MainActivity$I */
    /* loaded from: classes.dex */
    public class p implements View.OnClickListener {
        public p() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$4(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.q to com.parvaz.tunnel.MainActivity$J */
    /* loaded from: classes.dex */
    public class q implements View.OnClickListener {
        public q() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$5(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.r to com.parvaz.tunnel.MainActivity$K */
    /* loaded from: classes.dex */
    public class r implements View.OnClickListener {
        public r() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$6(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.s to com.parvaz.tunnel.MainActivity$ViewOnClickListenerC0194b */
    /* loaded from: classes.dex */
    public class s implements View.OnClickListener {
        public s() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$7(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.t to com.parvaz.tunnel.MainActivity$ViewOnClickListenerC0195c */
    /* loaded from: classes.dex */
    public class t implements View.OnClickListener {
        public t() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$8(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.u to com.parvaz.tunnel.MainActivity$ViewOnClickListenerC0196d */
    /* loaded from: classes.dex */
    public class u implements View.OnClickListener {
        public u() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$9(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.v to com.parvaz.tunnel.MainActivity$ViewOnClickListenerC0197e */
    /* loaded from: classes.dex */
    public class v implements View.OnClickListener {
        public v() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity.this.lambda$onCreate$10(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.w to com.parvaz.tunnel.MainActivity$ViewOnClickListenerC0201i */
    /* loaded from: classes.dex */
    public class w implements View.OnClickListener {
        public w() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity mainActivity = MainActivity.this;
            mainActivity.haptic(view);
            mainActivity.selectTab(0);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.x to com.parvaz.tunnel.MainActivity$ViewOnClickListenerC0202j */
    /* loaded from: classes.dex */
    public class x implements View.OnClickListener {
        public x() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity mainActivity = MainActivity.this;
            mainActivity.haptic(view);
            mainActivity.selectTab(1);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.y to com.parvaz.tunnel.MainActivity$l */
    /* loaded from: classes.dex */
    public class y implements View.OnClickListener {
        public y() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            MainActivity mainActivity = MainActivity.this;
            mainActivity.haptic(view);
            mainActivity.selectTab(1);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.MainActivity.z to com.parvaz.tunnel.MainActivity$m */
    /* loaded from: classes.dex */
    public class z implements DialogInterface.OnClickListener {
        public z() {
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            MainActivity mainActivity = MainActivity.this;
            mainActivity.L.d();
            mainActivity.renderQuota();
            Snackbar.make(mainActivity.connectButton, R.string.data_reset_done, -1).show();
        }
    }

    /* renamed from: A */
    public static String fmtBytes(long j) {
        double d = j;
        String[] strArr = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (d >= 1024.0d && i < 4) {
            d /= 1024.0d;
            i++;
        }
        return String.format(Locale.US, d < 10.0d ? "%.2f %s" : "%.1f %s", Double.valueOf(d), strArr[i]);
    }

    /* renamed from: B */
    public static String fmtSpeed(long j) {
        double d = j;
        String[] strArr = {"B/s", "KB/s", "MB/s", "GB/s"};
        int i = 0;
        while (d >= 1024.0d && i < 3) {
            d /= 1024.0d;
            i++;
        }
        return String.format(Locale.US, d < 10.0d ? "%.1f %s" : "%.0f %s", Double.valueOf(d), strArr[i]);
    }

    /* renamed from: C */
    public final void handleIntent(Intent intent) {
        Uri data;
        int importText;
        if (intent == null || (data = intent.getData()) == null || (importText = importText(data.toString())) <= 0) {
            return;
        }
        Snackbar.make(this.connectButton, getString(R.string.imported_n, Integer.valueOf(importText)), 0).show();
    }

    /* renamed from: D */
    public final int importText(String str) {
        try {
            try {
                ArrayList H2 = LinkParser.parseMany(str);
                if (H2.isEmpty()) {
                    return 0;
                }
                int a = this.b0.a(H2, "");
                // Every import path funnels through here, so warn once, centrally,
                // when some of what we just saved can never actually connect.
                warnUnsupported(H2);
                if (TextUtils.isEmpty(this.L.f343a.getString("selected_profile", "")) && !this.b0.e().isEmpty()) {
                    Prefs prefs = this.L;
                    RulesActivity__ExternalSyntheticOutline0.j(prefs.f343a, "selected_profile", ((Profile) this.b0.e().get(0)).id);
                }
                reload();
                return a;
            } catch (Throwable unused) {
                reload();
                return -1;
            }
        } catch (Throwable unused2) {
            return -1;
        }
    }

    /**
     * Launcher shortcut: pick another server straight from a list, without
     * scrolling the main screen. Reconnects if the tunnel is already up.
     */
    public final void showQuickSwitch() {
        final ArrayList servers = this.b0.e();
        String currentId = this.L.f343a.getString("selected_profile", "");
        final ArrayList<Profile> others = new ArrayList<>();
        for (Object o : servers) {
            Profile p2 = (Profile) o;
            // Only offer servers the core can actually dial.
            if (!p2.id.equals(currentId)
                    && com.parvaz.tunnel.core.ProtocolSupport.isSupported(p2)) {
                others.add(p2);
            }
        }
        if (others.isEmpty()) {
            Snackbar.make(this.connectButton, R.string.quick_switch_none, 0).show();
            return;
        }
        String[] labels = new String[others.size()];
        for (int i = 0; i < others.size(); i++) {
            Profile p2 = others.get(i);
            String name = p2.remark.isEmpty() ? p2.displayAddress() : p2.remark;
            labels[i] = p2.ping > 0 ? (name + "  \u2014  " + p2.ping + " ms") : name;
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.quick_switch_title);
        dialog.setItems(labels, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                if (which < 0 || which >= others.size()) {
                    return;
                }
                Profile chosen = others.get(which);
                RulesActivity__ExternalSyntheticOutline0.j(
                        MainActivity.this.L.f343a, "selected_profile", chosen.id);
                MainActivity.this.z.f368h = chosen.id;
                MainActivity.this.reload();
                if (TunnelVpnService.serviceRunning) {
                    MainActivity.this.startVpn("com.parvaz.tunnel.RESTART");
                } else {
                    MainActivity.this.connectButton.post(new D());
                }
            }
        });
        dialog.setNegativeButton(R.string.cancel, null);
        dialog.show();
    }

    /**
     * Tells the user how many freshly imported servers use a protocol the
     * bundled core cannot dial, rather than letting them find out on connect.
     */
    private void warnUnsupported(java.util.List<Profile> imported) {
        try {
            int n = com.parvaz.tunnel.core.ProtocolSupport.countUnsupported(imported);
            if (n <= 0) {
                return;
            }
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setTitle(R.string.unsupported_badge);
            dialog.setMessage(getString(R.string.unsupported_import_warn, Integer.valueOf(n)));
            dialog.setPositiveButton(R.string.ok, null);
            dialog.show();
        } catch (Throwable t) {
            android.util.Log.w("Parvaz/MainActivity", "unsupported warning failed", t);
        }
    }

    public final void E(String str, String str2, String str3) {
        String str4;
        if (str3 == null && str != null && !str.isEmpty()) {
            TextView textView = this.ipText;
            StringBuilder sb = new StringBuilder();
            if (str2 != null && !str2.isEmpty()) {
                str4 = FlagUtil.c(str2) + " ";
            } else {
                str4 = "";
            }
            sb.append(str4);
            sb.append(str);
            textView.setText(sb.toString());
            return;
        }
        this.ipText.setText(R.string.ip_failed);
    }

    /* renamed from: F */
    public final void maybeAutoConnect() {
        Intent intent;
        // The launcher long-press shortcut cannot carry extras, so it arrives as a
        // custom action instead; both mean "connect immediately".
        Intent launchIntent = getIntent();
        if (launchIntent != null
                && "com.parvaz.tunnel.QUICK_SWITCH".equals(launchIntent.getAction())) {
            launchIntent.setAction(null);
            showQuickSwitch();
            return;
        }
        boolean viaShortcut = launchIntent != null
                && "com.parvaz.tunnel.QUICK_CONNECT".equals(launchIntent.getAction());
        if (viaShortcut && launchIntent != null) {
            launchIntent.setAction(null);
            launchIntent.putExtra("com.parvaz.tunnel.AUTO_CONNECT", true);
        }
        if (!SafeMode.sTrippedThisRun && !getApplicationContext().getSharedPreferences("parvaz_safemode", 0).getBoolean("safe_active", false) && (intent = getIntent()) != null && intent.getBooleanExtra("com.parvaz.tunnel.AUTO_CONNECT", false)) {
            intent.removeExtra("com.parvaz.tunnel.AUTO_CONNECT");
            if (TunnelVpnService.serviceRunning) {
                return;
            }
            this.connectButton.post(new D());
            return;
        }
        applyAutoProfile();
    }

    /**
     * Applies the per-network rule the user configured (connect on mobile data,
     * stay off on a trusted Wi-Fi, ...). Only ever acts when the current state
     * disagrees with the rule, so it never fights a manual choice made moments
     * earlier on the same network.
     */
    public final void applyAutoProfile() {
        if (SafeMode.sTrippedThisRun) {
            return;
        }
        int action;
        try {
            action = com.parvaz.tunnel.core.AutoProfile.decide(this);
        } catch (Throwable t) {
            android.util.Log.w("Parvaz/MainActivity", "auto profile failed", t);
            return;
        }
        if (action == com.parvaz.tunnel.core.AutoProfile.ACTION_CONNECT) {
            if (!TunnelVpnService.serviceRunning && this.state != 1) {
                this.connectButton.post(new D());
            }
        } else if (action == com.parvaz.tunnel.core.AutoProfile.ACTION_DISCONNECT) {
            if (TunnelVpnService.serviceRunning) {
                startVpn("com.parvaz.tunnel.STOP");
            }
        }
    }

    /* renamed from: G */
    public final void pingAll() {
        ArrayList e = this.b0.e();
        if (e.isEmpty()) {
            this.refresh.setRefreshing(false);
            setPingAllBusy(false);
            return;
        }
        setPingAllBusy(true);
        Iterator it = e.iterator();
        while (it.hasNext()) {
            ((Profile) it.next()).ping = -3;
        }
        this.z.notifyDataSetChanged();
        PingManager pingManager = this.K;
        K k = new K();
        pingManager.f6273d = false;
        pingManager.f6271b = Executors.newFixedThreadPool(4);
        AtomicInteger atomicInteger = new AtomicInteger(e.size());
        if (e.isEmpty()) {
            pingManager.f6272c.post(new PingManager_3(k));
            return;
        }
        Iterator it2 = e.iterator();
        while (it2.hasNext()) {
            pingManager.f6271b.execute(new PingManager_4(pingManager, atomicInteger, k, (Profile) it2.next()));
        }
        pingManager.f6271b.shutdown();
    }

    /* renamed from: H */
    public final void reload() {
        int i;
        ArrayList e = this.b0.e();
        ArrayList arrayList = new ArrayList();
        Iterator it = e.iterator();
        while (it.hasNext()) {
            Profile profile = (Profile) it.next();
            if (this.favOnly) {
                this.L.getFavorites().contains(profile.id);
            }
            if (!this.query.isEmpty()) {
                String str = this.query;
                String str2 = profile.remark;
                if (str2 != null) {
                    if (str2.toLowerCase(Locale.getDefault()).contains(str)) {
                    }
                }
                String str3 = profile.address;
                if (str3 != null) {
                    if (str3.toLowerCase(Locale.US).contains(str)) {
                    }
                }
                String str4 = profile.protocol;
                if (str4 != null && str4.toLowerCase(Locale.US).contains(str)) {
                }
            }
            arrayList.add(profile);
        }
        Collections.sort(arrayList, new E());
        ServerAdapter serverAdapter = this.z;
        ArrayList arrayList2 = serverAdapter.g;
        arrayList2.clear();
        arrayList2.addAll(arrayList);
        serverAdapter.notifyDataSetChanged();
        ServerAdapter serverAdapter2 = this.z;
        String str5 = "";
        String string = this.L.f343a.getString("selected_profile", "");
        serverAdapter2.getClass();
        if (string != null) {
            str5 = string;
        }
        serverAdapter2.f368h = str5;
        serverAdapter2.notifyDataSetChanged();
        if (arrayList.isEmpty()) {
            this.emptyText.setVisibility(0);
            TextView textView = this.emptyText;
            if (e.isEmpty()) {
                i = R.string.no_servers_yet;
            } else {
                i = R.string.no_match;
            }
            textView.setText(i);
        } else {
            this.emptyText.setVisibility(8);
        }
        renderState();
    }

    /* renamed from: I */
    public final void renderFavFilter() {
        this.favFilter.setText(this.favOnly ? "★" : "☆");
        this.favFilter.setTextColor(this.favOnly ? -415707 : 1720223880);
    }

    /* renamed from: J */
    public final void renderQuota() {
        if (this.quotaUsedText == null) {
            return;
        }
        Profile currentProfile = ProfileStore.f(this).getById(this.L.f343a.getString("selected_profile", ""));
        Subscription subscription = null;
        if (currentProfile != null && currentProfile.subscriptionId != null && !currentProfile.subscriptionId.isEmpty()) {
            for (Object obj : ProfileStore.f(this).f()) {
                Subscription sub = (Subscription) obj;
                if (sub.id.equals(currentProfile.subscriptionId)) {
                    subscription = sub;
                    break;
                }
            }
        }
        if (subscription == null) {
            for (Object obj : ProfileStore.f(this).f()) {
                Subscription sub = (Subscription) obj;
                if (sub.hasQuota() && (subscription == null || sub.quotaTotal > subscription.quotaTotal)) {
                    subscription = sub;
                }
            }
        }

        long totalBytes = -1L;
        long usedBytes = 0L;
        long expireSec = -1L;
        boolean fromServer = false;

        long liveSessionBytes = this.L.f343a.getLong("data_up", 0L) + this.L.f343a.getLong("data_down", 0L);

        if (subscription != null && subscription.hasQuota()) {
            totalBytes = subscription.quotaTotal;
            usedBytes = subscription.quotaUsed() + (TunnelVpnService.serviceRunning ? liveSessionBytes : 0L);
            expireSec = subscription.quotaExpire;
            fromServer = true;
        } else {
            float manualGb = this.L.f343a.getFloat("manual_service_total_gb", this.L.f343a.getFloat("data_limit_gb", 0.0f));
            if (manualGb > 0.0f) {
                totalBytes = (long) (manualGb * 1024L * 1024L * 1024L);
                usedBytes = liveSessionBytes;
                int manualDays = this.L.f343a.getInt("manual_service_duration_days", 30);
                long since = this.L.f343a.getLong("manual_service_start_time", System.currentTimeMillis());
                expireSec = (since / 1000L) + (manualDays * 86400L);
            } else {
                usedBytes = liveSessionBytes;
            }
        }

        boolean fa = "fa".equals(this.L.f343a.getString("lang", "fa")) || "fa".equals(Locale.getDefault().getLanguage());

        if (totalBytes <= 0) {
            if (this.serviceTitleText != null) {
                this.serviceTitleText.setText(R.string.service_status_title);
            }
            this.quotaUsedText.setText((fa ? "حجم کل: " : "Total: ") + (fa ? "تعیین‌نشده" : "Not set"));
            this.quotaLeftText.setText(R.string.service_not_set);
            if (this.quotaConsumedText != null) {
                this.quotaConsumedText.setText((fa ? "مصرف‌شده: " : "Used: ") + fmtBytes(usedBytes));
            }
            if (this.quotaExpireDateText != null) {
                this.quotaExpireDateText.setText(R.string.service_unlimited_duration);
            }
            this.quotaPercentText.setText("");
            this.quotaBar.setProgress(0);
            this.quotaBar.setVisibility(View.GONE);
            if (this.quotaWarningText != null) {
                this.quotaWarningText.setVisibility(View.GONE);
            }
            return;
        }

        long remaining = Math.max(0L, totalBytes - usedBytes);
        int percent = (int) Math.min(100L, (usedBytes * 100) / totalBytes);

        if (this.serviceTitleText != null) {
            String title = fromServer
                    ? (getString(R.string.service_status_title) + (fa ? " (سرور)" : " (Server)"))
                    : getString(R.string.service_status_title);
            this.serviceTitleText.setText(title);
        }

        this.quotaUsedText.setText((fa ? "حجم کل: " : "Total: ") + fmtBytes(totalBytes));
        this.quotaLeftText.setText((fa ? "باقی‌مانده: " : "Left: ") + fmtBytes(remaining));

        if (this.quotaConsumedText != null) {
            this.quotaConsumedText.setText((fa ? "مصرف‌شده: " : "Used: ") + fmtBytes(usedBytes));
        }

        long diffDays = -1L;
        if (expireSec > 0) {
            if (expireSec > 10000000000L) expireSec /= 1000L;
            long nowSec = System.currentTimeMillis() / 1000L;
            diffDays = (expireSec - nowSec) / 86400L;
            if (this.quotaExpireDateText != null) {
                if (diffDays >= 0) {
                    this.quotaExpireDateText.setText(diffDays + " " + (fa ? "روز تا تاریخ انقضا" : "days left"));
                } else {
                    this.quotaExpireDateText.setText(R.string.service_expired);
                }
            }
        } else if (this.quotaExpireDateText != null) {
            this.quotaExpireDateText.setText(R.string.service_unlimited_duration);
        }

        this.quotaPercentText.setText(percent + "%");
        this.quotaBar.setProgress(percent);
        this.quotaBar.setVisibility(View.VISIBLE);

        if (this.quotaWarningText != null) {
            if (percent >= 100) {
                this.quotaWarningText.setText(R.string.quota_warn_100);
                this.quotaWarningText.setTextColor(0xFFC62828);
                this.quotaWarningText.setVisibility(View.VISIBLE);
            } else if (percent >= 90) {
                this.quotaWarningText.setText(R.string.quota_warn_90);
                this.quotaWarningText.setTextColor(0xFFE65100);
                this.quotaWarningText.setVisibility(View.VISIBLE);
            } else if (diffDays >= 0 && diffDays <= 2) {
                this.quotaWarningText.setText(getString(R.string.quota_warn_expire, Integer.valueOf((int) Math.max(0, diffDays))));
                this.quotaWarningText.setTextColor(0xFFE65100);
                this.quotaWarningText.setVisibility(View.VISIBLE);
            } else {
                this.quotaWarningText.setVisibility(View.GONE);
            }
        }

        QuotaNotifier.checkAndNotify(this, usedBytes, totalBytes, expireSec);
    }

    public final void showQuotaDetailsDialog() {
        Profile currentProfile = ProfileStore.f(this).getById(this.L.f343a.getString("selected_profile", ""));
        Subscription subscription = null;
        if (currentProfile != null && currentProfile.subscriptionId != null && !currentProfile.subscriptionId.isEmpty()) {
            for (Object obj : ProfileStore.f(this).f()) {
                Subscription sub = (Subscription) obj;
                if (sub.id.equals(currentProfile.subscriptionId)) {
                    subscription = sub;
                    break;
                }
            }
        }
        if (subscription == null) {
            Iterator it = ProfileStore.f(this).f().iterator();
            while (it.hasNext()) {
                Subscription s = (Subscription) it.next();
                if (s.hasQuota() && (subscription == null || s.quotaTotal > subscription.quotaTotal)) {
                    subscription = s;
                }
            }
        }

        boolean fa = "fa".equals(this.L.f343a.getString("lang", "fa")) || "fa".equals(Locale.getDefault().getLanguage());

        if (subscription != null && subscription.hasQuota()) {
            StringBuilder sb = new StringBuilder();
            sb.append(getString(R.string.add_subscription)).append(": ").append(subscription.name).append("\n\n");
            long total = subscription.quotaTotal;
            long upload = Math.max(0L, subscription.quotaUpload);
            long download = Math.max(0L, subscription.quotaDownload);
            long used = upload + download;
            long left = Math.max(0L, total - used);
            int pct = (int) Math.min(100L, (used * 100) / total);

            sb.append(getString(R.string.quota_total)).append(": ").append(fmtBytes(total)).append("\n");
            sb.append(getString(R.string.quota_upload)).append(": ").append(fmtBytes(upload)).append("\n");
            sb.append(getString(R.string.quota_download)).append(": ").append(fmtBytes(download)).append("\n");
            sb.append(getString(R.string.data_used)).append(": ").append(fmtBytes(used)).append(" (").append(pct).append("%)\n");
            sb.append(getString(R.string.data_left)).append(": ").append(fmtBytes(left)).append("\n");

            if (subscription.quotaExpire > 0) {
                long expSec = subscription.quotaExpire;
                if (expSec > 10000000000L) {
                    expSec /= 1000L;
                }
                long diffDays = (expSec - (System.currentTimeMillis() / 1000L)) / 86400L;
                String expDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new java.util.Date(expSec * 1000L));
                sb.append("\n").append(getString(R.string.quota_expires)).append(": ").append(expDate);
                if (diffDays >= 0) {
                    sb.append(" (").append(diffDays).append(" ").append(fa ? "روز مانده" : "days left").append(")");
                }
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.quota_details_title)
                    .setMessage(sb.toString().trim())
                    .setPositiveButton(R.string.quota_refresh, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.this.updateSubscriptions();
                        }
                    })
                    .setNegativeButton(R.string.dismiss, null)
                    .show();
        } else {
            showSetManualServiceDialog();
        }
    }

    public final void showSetManualServiceDialog() {
        View inflate = getLayoutInflater().inflate(R.layout.dialog_input, (ViewGroup) null);
        final TextInputEditText input = (TextInputEditText) inflate.findViewById(R.id.input);
        float currentGb = this.L.f343a.getFloat("manual_service_total_gb", this.L.f343a.getFloat("data_limit_gb", 10.0f));
        input.setHint(R.string.service_gb_hint);
        input.setText(String.valueOf((int) currentGb));
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.service_set_manual)
                .setMessage(R.string.service_gb_hint)
                .setView(inflate)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            float gb = Float.parseFloat(input.getText().toString().trim());
                            if (gb > 0) {
                                MainActivity.this.L.f343a.edit()
                                        .putFloat("manual_service_total_gb", gb)
                                        .putInt("manual_service_duration_days", 30)
                                        .putLong("manual_service_start_time", System.currentTimeMillis())
                                        .apply();
                                MainActivity.this.renderQuota();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public final void renderState() {
        if (this.connectButton == null || this.statusText == null) {
            return;
        }

        int state = this.state;
        boolean connected  = state == 2;
        boolean busy       = state == 1 || state == 5;

        int statusRes;
        int background;
        if (connected) {
            statusRes = R.string.state_connected;
            background = R.drawable.bg_button_connected;
        } else if (busy) {
            statusRes = (state == 5) ? R.string.state_switching : R.string.state_connecting;
            background = R.drawable.bg_button_connecting;
        } else {
            statusRes = R.string.state_disconnected;
            background = R.drawable.bg_button_idle;
        }

        String status = getString(statusRes);
        this.statusText.setText(status);
        this.connectButton.setBackgroundResource(background);
        this.connectButton.setContentDescription(status);

        // Show which server the toggle would use (or is using).
        if (this.serverText != null) {
            Profile selected = this.b0.getById(this.L.f343a.getString("selected_profile", ""));
            this.serverText.setText(selected != null ? selected.remark
                                                     : getString(R.string.no_server_selected));
        }

        // Timer and speed counters are only meaningful for a live session.
        if (!connected) {
            if (this.timerText != null) {
                this.timerText.setText("00:00:00");
            }
            if (this.speedText != null) {
                this.speedText.setText("↓ " + fmtSpeed(0L) + "    ↑ " + fmtSpeed(0L));
            }
            // A graph of a session that has ended is misleading, so drop the history
            // rather than freezing the last shape on screen.
            if (this.sparkline != null) {
                this.sparkline.clear();
                this.sparkline.setVisibility(View.GONE);
            }
        }

        // The pulse ring animates only while connecting/connected, and beats faster
        // while a connection attempt is in flight.
        if (this.pulseRing != null) {
            boolean shouldPulse = connected || busy;
            if (!shouldPulse) {
                stopPulse();
                this.pulseRing.setVisibility(View.GONE);
            } else {
                this.pulseRing.setVisibility(View.VISIBLE);
                if (this.pulseAnimator == null || this.pulseFast != busy) {
                    this.pulseFast = busy;
                    startPulse(busy ? 700L : 1600L);
                }
            }
        }

        if (this.z != null) {
            this.z.notifyDataSetChanged();
        }
        renderQuota();
    }

    /** Starts (or restarts) the looping scale+fade animation on the pulse ring. */
    private void startPulse(long duration) {
        stopPulse();
        if (this.pulseRing == null) {
            return;
        }
        try {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(this.pulseRing, "scaleX", 0.85f, 1.15f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(this.pulseRing, "scaleY", 0.85f, 1.15f);
            ObjectAnimator alpha  = ObjectAnimator.ofFloat(this.pulseRing, "alpha", 0.55f, 0.0f);
            for (ObjectAnimator a : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
                a.setDuration(duration);
                a.setRepeatCount(ValueAnimator.INFINITE);
                a.setRepeatMode(ValueAnimator.RESTART);
            }
            AnimatorSet set = new AnimatorSet();
            set.playTogether(scaleX, scaleY, alpha);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.start();
            this.pulseAnimator = set;
        } catch (Throwable unused) {
            this.pulseAnimator = null;
        }
    }

    /** Cancels the pulse animation and resets the ring to its resting transform. */
    private void stopPulse() {
        AnimatorSet set = this.pulseAnimator;
        if (set != null) {
            try {
                set.cancel();
            } catch (Throwable unused) {
                android.util.Log.w("Parvaz/MainActivity", "Throwable ignored", unused);
            }
        }
        this.pulseAnimator = null;
        View ring = this.pulseRing;
        if (ring != null) {
            ring.setScaleX(1.0f);
            ring.setScaleY(1.0f);
            ring.setAlpha(0.0f);
        }
    }


    /* renamed from: L */
    public final void selectTab(int i) {
        EditText editText;
        this.currentTab = i;
        boolean z2 = i == 0;
        this.pageHome.setVisibility(z2 ? 0 : 8);
        this.pageServers.setVisibility(z2 ? 8 : 0);
        this.tabHome.setBackgroundResource(z2 ? R.drawable.bg_tab_selected : 0);
        this.tabServers.setBackgroundResource(z2 ? 0 : R.drawable.bg_tab_selected);
        int color = getResources().getColor(R.color.brand);
        this.tabHomeIcon.setColorFilter(z2 ? color : -6511697);
        this.tabServersIcon.setColorFilter(z2 ? -6511697 : color);
        this.tabHomeLabel.setTextColor(z2 ? color : -6511697);
        TextView textView = this.tabServersLabel;
        if (z2) {
            color = -6511697;
        }
        textView.setTextColor(color);
        if (z2 || (editText = this.searchInput) == null) {
            return;
        }
        editText.clearFocus();
    }

    /* renamed from: M */
    public final void setPingAllBusy(boolean z2) {
        ProgressBar progressBar = this.pingAllProgress;
        if (progressBar != null) {
            progressBar.setVisibility(z2 ? 0 : 8);
        }
        ImageButton imageButton = this.pingAllButton;
        if (imageButton != null) {
            imageButton.setVisibility(z2 ? 8 : 0);
        }
    }

    /* renamed from: N */
    public final void showAddDialog() {
        String[] strArr = {getString(R.string.scan_qr), getString(R.string.add_from_clipboard), getString(R.string.add_manual_link), getString(R.string.add_raw_json), getString(R.string.add_subscription), getString(R.string.update_subscriptions), getString(R.string.backup_restore)};
        MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(this);
        materialAlertDialogBuilder.setTitle(R.string.add_server);
        materialAlertDialogBuilder.setItems(strArr, new F());
        materialAlertDialogBuilder.show();
    }

    /* renamed from: O */
    public final void startVpn(String str) {
        // Snapshot the untunnelled address before the VPN grabs the default
        // route; afterwards even a "direct" lookup would come back tunnelled,
        // and the leak test needs a real before/after pair.
        captureDirectIp();
        Intent intent = new Intent(this, (Class<?>) TunnelVpnService.class);
        intent.setAction(str);
        ContextCompat.startForegroundService(this, intent);
        this.state = 1;
        renderState();
    }

    /** Records the pre-connect public IP in prefs, off the UI thread. */
    public final void captureDirectIp() {
        final Prefs prefs = this.L;
        if (prefs == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    com.parvaz.tunnel.core.IpLookup.Info info =
                            com.parvaz.tunnel.core.IpLookup.direct();
                    if (info.ok()) {
                        prefs.f343a.edit().putString("last_direct_ip", info.ip).apply();
                    }
                } catch (Throwable t) {
                    android.util.Log.w("Parvaz", "direct IP capture failed", t);
                }
            }
        }, "direct-ip").start();
    }

    /**
     * Refreshes the exit IP automatically once the tunnel reports connected, so
     * the user sees where they came out without hunting for a button.
     */
    public final void autoRefreshIp() {
        if (this.ipText == null) {
            return;
        }
        this.ipText.setText(R.string.ip_checking);
        new Thread(new SpeedTester_6(new Handler(Looper.getMainLooper()), new C0022d()), "ip-auto").start();
    }

    /* renamed from: P */
    /** Shake-to-switch gesture watcher; null when the device has no accelerometer. */
    public com.parvaz.tunnel.core.ShakeDetector shakeDetector;

    /**
     * Switches to the next server in the list, wrapping around. Bound to the shake
     * gesture so a dead server can be escaped without navigating the UI.
     */
    public void shakeToNextServer() {
        try {
            ArrayList all = this.b0.e();
            if (all.size() < 2) {
                Snackbar.make(this.connectButton, R.string.shake_need_servers, -1).show();
                return;
            }
            String current = this.L.f343a.getString("selected_profile", "");
            int index = -1;
            for (int i = 0; i < all.size(); i++) {
                if (((Profile) all.get(i)).id.equals(current)) {
                    index = i;
                    break;
                }
            }
            Profile next = (Profile) all.get((index + 1) % all.size());

            Prefs prefs = this.L;
            RulesActivity__ExternalSyntheticOutline0.j(prefs.f343a, "selected_profile", next.id);
            ServerAdapter serverAdapter = this.z;
            serverAdapter.f368h = next.id == null ? "" : next.id;
            serverAdapter.notifyDataSetChanged();
            renderState();

            haptic(this.connectButton);
            Snackbar.make(this.connectButton,
                    getString(R.string.shake_switched, next.remark), -1).show();

            // Only reconnect if a tunnel is actually up; otherwise just move the pointer.
            if (TunnelVpnService.serviceRunning) {
                startVpn("com.parvaz.tunnel.RESTART");
            }
        } catch (Throwable t) {
            android.util.Log.w("Parvaz", "shake switch failed", t);
        }
    }

    public final void toggle() {
        haptic(this.connectButton);
        int i = this.state;
        if (i != 2 && i != 1) {
            String str = "";
            if (this.b0.getById(this.L.f343a.getString("selected_profile", "")) == null) {
                ArrayList e = this.b0.e();
                if (e.isEmpty()) {
                    Snackbar.make(this.connectButton, R.string.err_no_server, 0).show();
                    showAddDialog();
                    return;
                }
                Prefs prefs = this.L;
                RulesActivity__ExternalSyntheticOutline0.j(prefs.f343a, "selected_profile", ((Profile) e.get(0)).id);
                ServerAdapter serverAdapter = this.z;
                String str2 = ((Profile) e.get(0)).id;
                serverAdapter.getClass();
                if (str2 != null) {
                    str = str2;
                }
                serverAdapter.f368h = str;
                serverAdapter.notifyDataSetChanged();
            }
            Intent prepare = VpnService.prepare(this);
            if (prepare != null) {
                this.j0.launch(prepare);
                return;
            } else {
                startVpn("com.parvaz.tunnel.START");
                return;
            }
        }
        Intent intent = new Intent(this, (Class<?>) TunnelVpnService.class);
        intent.setAction("com.parvaz.tunnel.STOP");
        startService(intent);
        this.state = 0;
        renderState();
    }

    /* renamed from: Q */
    public final void updateSubscriptions() {
        if (this.b0.f().isEmpty()) {
            Snackbar.make(this.connectButton, R.string.no_subscriptions, 0).show();
        } else {
            this.refresh.setRefreshing(true);
            new Thread(new SubscriptionUpdater_4(new SubscriptionUpdater(this), new J())).start();
        }
    }

    @Override // androidx.appcompat.app.AppCompatActivity, android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    public final void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }

    public void haptic(View view) {
        Prefs prefs;
        if (view == null || (prefs = this.L) == null || !prefs.f343a.getBoolean("haptics", true)) {
            return;
        }
        try {
            view.performHapticFeedback(1, 2);
        } catch (Throwable unused) {
            android.util.Log.w("Parvaz/MainActivity", "Throwable ignored", unused);
        }
    }

    public void lambda$onCreate$10(View view) {
        if (this.state != 2) {
            Snackbar.make(this.connectButton, R.string.speed_need_connection, 0).show();
        } else {
            this.ipText.setText(R.string.ip_checking);
            new Thread(new SpeedTester_6(new Handler(Looper.getMainLooper()), new C0022d()), "ip-lookup").start();
        }
    }

    public void lambda$onCreate$2(View view) {
        toggle();
    }

    public void lambda$onCreate$3(View view) {
        showAddDialog();
    }

    public void lambda$onCreate$4(View view) {
        startActivity(new Intent(this, (Class<?>) SettingsActivity.class));
    }

    public void lambda$onCreate$5(View view) {
        PingManager pingManager = this.K;
        ExecutorService executorService = pingManager.f6271b;
        if (executorService == null || executorService.isTerminated() || pingManager.f6271b.isShutdown()) {
            if (this.b0.e().isEmpty()) {
                Snackbar.make(view, R.string.no_servers_yet, -1).show();
                return;
            } else {
                pingAll();
                return;
            }
        }
        PingManager pingManager2 = this.K;
        pingManager2.f6273d = true;
        ExecutorService executorService2 = pingManager2.f6271b;
        if (executorService2 != null) {
            executorService2.shutdownNow();
        }
        setPingAllBusy(false);
        Snackbar.make(view, R.string.ping_cancelled, -1).show();
    }

    /* JADX WARN: Type inference failed for: r2v0, types: [java.lang.Object, java.util.Comparator] */
    /**
     * Sort button. The four modes the strings already promised — ping, quality, name,
     * last success — are now all real, chosen from a dialog and remembered in prefs so
     * the list keeps the user's preferred order across launches.
     */
    public void lambda$onCreate$6(final View view) {
        final String[] labels = {
                getString(R.string.sort_ping),
                getString(R.string.sort_quality),
                getString(R.string.sort_name),
                getString(R.string.sort_recent),
                getString(R.string.sort_country),
                getString(R.string.bypass_test),
                getString(R.string.clean_dead_nodes),
                getString(R.string.clean_ip_title)
        };
        int current = this.L.f343a.getInt("sort_mode", 0);
        if (current < 0 || current >= 5) {
            current = 0;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sort_by)
                .setSingleChoiceItems(labels, current, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        d.dismiss();
                        if (which < 5) {
                            MainActivity.this.L.f343a.edit().putInt("sort_mode", which).apply();
                            MainActivity.this.applySort(which);
                            MainActivity.this.reload();
                            Snackbar.make(view, labels[which], -1).show();
                        } else if (which == 5) {
                            MainActivity.this.runRealBypassTest();
                        } else if (which == 6) {
                            MainActivity.this.cleanDeadNodes();
                        } else if (which == 7) {
                            MainActivity.this.startActivity(new Intent(MainActivity.this, CleanIpActivity.class));
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void cleanDeadNodes() {
        ProfileStore store = this.b0;
        int removed = 0;
        synchronized (store) {
            for (int i = store.f346b.size() - 1; i >= 0; i--) {
                Profile p = (Profile) store.f346b.get(i);
                if (p != null && p.ping == -1) {
                    store.f346b.remove(i);
                    removed++;
                }
            }
            store.h();
        }
        reload();
        Snackbar.make(this.connectButton, getString(R.string.clean_dead_nodes_done, Integer.valueOf(removed)), -1).show();
    }

    public void runRealBypassTest() {
        final ArrayList<Profile> all = this.b0.e();
        if (all.isEmpty()) {
            Snackbar.make(this.connectButton, R.string.no_servers_yet, -1).show();
            return;
        }
        Snackbar.make(this.connectButton, R.string.bypass_test_running, -1).show();
        setPingAllBusy(true);
        RealBypassTester.testAll(this, all, new RealBypassTester.Callback() {
            @Override
            public void onServerTested(final Profile profile, boolean passed, int latencyMs) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (MainActivity.this.z != null) {
                            MainActivity.this.z.notifyDataSetChanged();
                        }
                    }
                });
            }

            @Override
            public void onAllComplete(final int passedCount, final int failedCount) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setPingAllBusy(false);
                        MainActivity.this.reload();
                        Snackbar.make(MainActivity.this.connectButton,
                                getString(R.string.bypass_test_done, Integer.valueOf(passedCount), Integer.valueOf(failedCount)),
                                0).show();
                    }
                });
            }
        });
    }

    /**
     * Reorders the persisted profile list in place.
     *
     * <p>0 = ping (measured, unknown last), 1 = quality (ServerMemory score for the
     * current network context), 2 = name, 3 = last successful connection,
     * 4 = country (groups every server of one country together, unknowns last, and
     * orders within a country by ping so the best entry of each group is on top).
     */
    public void applySort(final int mode) {
        final ServerMemory memory = (mode == 1 || mode == 3) ? new ServerMemory(this) : null;
        final android.content.Context ctx = this;
        ProfileStore profileStore = this.b0;
        synchronized (profileStore) {
            try {
                Collections.sort(profileStore.f346b, new java.util.Comparator<Profile>() {
                    @Override
                    public int compare(Profile a, Profile b) {
                        switch (mode) {
                            case 1: {
                                int sa = memory.scoreFor(ctx, a.id);
                                int sb2 = memory.scoreFor(ctx, b.id);
                                if (sa != sb2) {
                                    return sb2 - sa;   // higher score first
                                }
                                break;
                            }
                            case 3: {
                                ServerMemory.Entry ea = memory.entryFor(ctx, a.id);
                                ServerMemory.Entry eb = memory.entryFor(ctx, b.id);
                                long la = ea == null ? 0L : ea.lastSuccess;
                                long lb = eb == null ? 0L : eb.lastSuccess;
                                if (la != lb) {
                                    return lb > la ? 1 : -1;   // most recent first
                                }
                                break;
                            }
                            case 2:
                                break;
                            case 4: {
                                // Country grouping. Servers with no detectable country
                                // sink below the named groups rather than forming a
                                // group called "null".
                                String ca = com.parvaz.tunnel.ui.FlagUtil
                                        .countryCodeFor(a.remark, a.address);
                                String cb = com.parvaz.tunnel.ui.FlagUtil
                                        .countryCodeFor(b.remark, b.address);
                                if (ca == null && cb != null) {
                                    return 1;
                                }
                                if (ca != null && cb == null) {
                                    return -1;
                                }
                                if (ca != null && !ca.equals(cb)) {
                                    return ca.compareTo(cb);
                                }
                                // Same country: best ping first inside the group.
                                int qa = a.ping > 0 ? a.ping : Integer.MAX_VALUE;
                                int qb = b.ping > 0 ? b.ping : Integer.MAX_VALUE;
                                if (qa != qb) {
                                    return qa - qb;
                                }
                                break;
                            }
                            default: {
                                // Ping: measured values ascending, unmeasured (-1) and
                                // failed (-2) always at the bottom.
                                int pa = a.ping > 0 ? a.ping : Integer.MAX_VALUE;
                                int pb = b.ping > 0 ? b.ping : Integer.MAX_VALUE;
                                if (pa != pb) {
                                    return pa - pb;
                                }
                                break;
                            }
                        }
                        return String.valueOf(a.remark).compareToIgnoreCase(String.valueOf(b.remark));
                    }
                });
                profileStore.h();
            } catch (Throwable t) {
                android.util.Log.w("ParvazMain", "sort failed", t);
            }
        }
    }

    public void lambda$onCreate$7(View view) {
        this.favOnly = !this.favOnly;
        haptic(view);
        renderFavFilter();
        reload();
    }

    public void lambda$onCreate$8(View view) {
        if (this.state != 2) {
            Snackbar.make(this.connectButton, R.string.speed_need_connection, 0).show();
            return;
        }
        SpeedTester speedTester = this.Y;
        if (speedTester != null) {
            speedTester.f6291a = true;
        }
        this.Y = new SpeedTester();
        this.speedTestButton.setText(R.string.speed_testing);
        this.speedTestButton.setEnabled(false);
        haptic(this.speedTestButton);
        SpeedTester speedTester2 = this.Y;
        C0021c c0021c = new C0021c();
        speedTester2.f6291a = false;
        new Thread(new SpeedTester_1(speedTester2, c0021c), "speed-test").start();
    }

    public void lambda$onCreate$9(View view) {
        Iterator it = this.b0.e().iterator();
        Profile profile = null;
        int i = Integer.MAX_VALUE;
        while (it.hasNext()) {
            Profile profile2 = (Profile) it.next();
            int i2 = profile2.ping;
            if (i2 > 0) {
                if (this.L.getFavorites().contains(profile2.id)) {
                    i2 -= 60;
                }
                if (i2 < i) {
                    profile = profile2;
                    i = i2;
                }
            }
        }
        View view2 = this.connectButton;
        if (profile == null) {
            Snackbar.make(view2, R.string.best_server_none, 0).show();
            pingAll();
            return;
        }
        haptic(view2);
        RulesActivity__ExternalSyntheticOutline0.j(this.L.f343a, "selected_profile", profile.id);
        ServerAdapter serverAdapter = this.z;
        String str = profile.id;
        serverAdapter.getClass();
        if (str == null) {
            str = "";
        }
        serverAdapter.f368h = str;
        serverAdapter.notifyDataSetChanged();
        renderState();
        Snackbar.make(this.connectButton, getString(R.string.best_server_picked, profile.remark), -1).show();
        if (this.state == 2) {
            startVpn("com.parvaz.tunnel.RESTART");
        }
    }

    @Override // androidx.activity.ComponentActivity, android.app.Activity
    public final void onBackPressed() {
        if (this.currentTab != 0) {
            selectTab(0);
        } else {
            super.onBackPressed();
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v16, types: [androidx.activity.result.b, java.lang.Object] */
    /* JADX WARN: Type inference failed for: r7v80, types: [c.a, java.lang.Object] */
    /* JADX WARN: Type inference failed for: r7v82, types: [c.a, java.lang.Object] */
    /* JADX WARN: Type inference failed for: r7v84, types: [c.a, java.lang.Object] */
    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public final void onCreate(Bundle bundle) {
        File latest;
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        this.L = new Prefs(this);
        this.b0 = ProfileStore.f(this);
        this.K = new PingManager(this);
        this.connectButton = findViewById(R.id.connect_button);
        this.statusText = (TextView) findViewById(R.id.status_text);
        this.serverText = (TextView) findViewById(R.id.server_text);
        this.speedText = (TextView) findViewById(R.id.speed_text);
        this.timerText = (TextView) findViewById(R.id.timer_text);
        this.emptyText = (TextView) findViewById(R.id.empty_text);
        this.serviceTitleText = (TextView) findViewById(R.id.service_title);
        this.quotaUsedText = (TextView) findViewById(R.id.quota_used);
        this.quotaLeftText = (TextView) findViewById(R.id.quota_left);
        this.quotaPercentText = (TextView) findViewById(R.id.quota_percent);
        this.quotaBar = (ProgressBar) findViewById(R.id.quota_bar);
        this.quotaConsumedText = (TextView) findViewById(R.id.quota_consumed);
        this.quotaExpireDateText = (TextView) findViewById(R.id.quota_expire_date);
        this.quotaWarningText = (TextView) findViewById(R.id.quota_warning);

        View quotaCard = findViewById(R.id.quota_card);
        if (quotaCard != null) {
            quotaCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MainActivity.this.showQuotaDetailsDialog();
                }
            });
            quotaCard.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    MainActivity.this.showQuotaDetailsDialog();
                    return true;
                }
            });
        }
        this.pulseRing = findViewById(R.id.pulse_ring);
        this.list = (RecyclerView) findViewById(R.id.server_list);
        this.refresh = (SwipeRefreshLayout) findViewById(R.id.refresh);
        this.z = new ServerAdapter(this, new C0030l());
        boolean z2 = true;
        this.list.setLayoutManager(new LinearLayoutManager(this));
        this.list.setAdapter(this.z);
        this.refresh.setOnRefreshListener(new m());
        this.connectButton.setOnClickListener(new n());
        findViewById(R.id.btn_add).setOnClickListener(new o());
        findViewById(R.id.btn_settings).setOnClickListener(new p());
        this.pingAllProgress = (ProgressBar) findViewById(R.id.ping_all_progress);
        ImageButton imageButton = (ImageButton) findViewById(R.id.btn_ping_all);
        this.pingAllButton = imageButton;
        imageButton.setOnClickListener(new q());
        ((ImageButton) findViewById(R.id.btn_sort)).setOnClickListener(new r());
        this.searchInput = (EditText) findViewById(R.id.search_input);
        this.favFilter = (TextView) findViewById(R.id.btn_fav_filter);
        this.ipText = (TextView) findViewById(R.id.ip_text);
        this.sparkline = (com.parvaz.tunnel.ui.SparklineView) findViewById(R.id.sparkline);
        this.speedTestButton = (TextView) findViewById(R.id.btn_speed_test);
        this.searchInput.addTextChangedListener(new C0023e());
        renderFavFilter();
        this.favFilter.setOnClickListener(new s());
        this.speedTestButton.setOnClickListener(new t());
        findViewById(R.id.btn_best_server).setOnClickListener(new u());
        this.ipText.setOnClickListener(new v());
        this.j0 = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), new C0024f());
        this.F = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        // Notification permission is optional; nothing to do either way.
                    }
                });
        this.P = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), new C0026h());
        this.pageHome = findViewById(R.id.page_home);
        this.pageServers = findViewById(R.id.page_servers);
        this.tabHome = findViewById(R.id.tab_home);
        this.tabServers = findViewById(R.id.tab_servers);
        this.tabHomeIcon = (ImageView) findViewById(R.id.tab_home_icon);
        this.tabServersIcon = (ImageView) findViewById(R.id.tab_servers_icon);
        this.tabHomeLabel = (TextView) findViewById(R.id.tab_home_label);
        this.tabServersLabel = (TextView) findViewById(R.id.tab_servers_label);
        this.tabHome.setOnClickListener(new w());
        this.tabServers.setOnClickListener(new x());
        View findViewById = findViewById(R.id.btn_goto_servers);
        if (findViewById != null) {
            findViewById.setOnClickListener(new y());
        }
        selectTab(0);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != 0) {
            this.F.launch("android.permission.POST_NOTIFICATIONS");
        }
        handleIntent(getIntent());
        this.connectButton.postDelayed(new A(), 4000L);
        try {
            if (!SafeMode.sTrippedThisRun && !getApplicationContext().getSharedPreferences("parvaz_safemode", 0).getBoolean("safe_active", false)) {
                z2 = false;
            }
            if (z2) {
                this.L.f343a.edit().putBoolean("connect_on_boot", false).apply();
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.safe_mode_title)
                        .setMessage(R.string.safe_mode_desc)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                SafeMode.sTrippedThisRun = false;
                getApplicationContext().getSharedPreferences("parvaz_safemode", 0).edit().putInt("pending_launches", 0).putBoolean("safe_active", false).commit();
            }
        } catch (Throwable unused) {
            android.util.Log.w("Parvaz/MainActivity", "Throwable ignored", unused);
        }
        try {
            if (CrashReporter.latest(this) != null && (latest = CrashReporter.latest(this)) != null) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.crash_title)
                        .setMessage(R.string.crash_desc)
                        .setPositiveButton(R.string.view, new C(latest))
                        .setNegativeButton(R.string.dismiss, new B())
                        .show();
            }
        } catch (Throwable unused2) {
            android.util.Log.w("Parvaz/MainActivity", "Throwable ignored", unused2);
        }
    }

    @Override // androidx.activity.ComponentActivity, android.app.Activity
    public final void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    public final void onPause() {
        super.onPause();
        try {
            unregisterReceiver(this.p0);
        } catch (Exception unused) {
            android.util.Log.w("Parvaz/MainActivity", "Exception ignored", unused);
        }
        // Release the accelerometer; leaving it registered drains the battery.
        if (this.shakeDetector != null) {
            this.shakeDetector.stop();
        }
        // Don't leave an infinite animator running against a backgrounded view.
        stopPulse();
    }

    /* JADX WARN: Type inference failed for: r0v19, types: [androidx.biometric.BiometricPrompt$d$a, java.lang.Object] */
    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    public final void onResume() {
        int i;
        Executor executorCompat$HandlerExecutor;
        super.onResume();
        // Opt-in gesture: shake the phone to jump to the next server.
        if (this.L.f343a.getBoolean("shake_to_switch", false)) {
            if (this.shakeDetector == null) {
                this.shakeDetector = new com.parvaz.tunnel.core.ShakeDetector(this,
                        new com.parvaz.tunnel.core.ShakeDetector.Listener() {
                            @Override
                            public void onShake() {
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.this.shakeToNextServer();
                                    }
                                });
                            }
                        });
            }
            this.shakeDetector.start();
        } else if (this.shakeDetector != null) {
            this.shakeDetector.stop();
        }
        if (this.L.f343a.getBoolean("app_lock", false) && !this.unlocked) {
            try {
                executorCompat$HandlerExecutor = ContextCompat.getMainExecutor(this);
                BiometricPrompt biometricPrompt =
                        new BiometricPrompt(this, executorCompat$HandlerExecutor, new C0020b());
                BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.app_lock_prompt))
                        .setSubtitle(getString(R.string.app_lock_subtitle))
                        .setAllowedAuthenticators(33023)
                        .build();
                biometricPrompt.authenticate(promptInfo);
            } catch (Throwable unused) {
                this.unlocked = true;
                findViewById(R.id.lock_shade).setVisibility(8);
                maybeAutoConnect();
            }
            findViewById(R.id.lock_shade).setVisibility(0);
        } else {
            findViewById(R.id.lock_shade).setVisibility(8);
            maybeAutoConnect();
        }
        ContextCompat.registerReceiver(this, this.p0,
                new IntentFilter("com.parvaz.tunnel.STATE"), ContextCompat.RECEIVER_NOT_EXPORTED);
        if (TunnelVpnService.serviceRunning) {
            i = 2;
        } else {
            i = TunnelVpnService.currentState;
        }
        this.state = i;
        if (!TunnelVpnService.serviceRunning && this.state != 1) {
            this.state = 0;
        }
        reload();
        if (!this.b0.f().isEmpty()) {
            new Thread(new SubscriptionUpdater_4(new SubscriptionUpdater(this), new J())).start();
        }
    }
}
