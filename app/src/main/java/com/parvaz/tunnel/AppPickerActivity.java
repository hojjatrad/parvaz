package com.parvaz.tunnel;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* loaded from: classes.dex */
public class AppPickerActivity extends AppCompatActivity {

    /* renamed from: A */
    public TextView f6069b;
    public Prefs B;

    /* renamed from: C */
    public View f6071d;

    /* renamed from: D */
    public final ArrayList f6072e = new ArrayList();

    /* renamed from: E */
    public final ArrayList f6073f = new ArrayList();

    /* renamed from: F */
    public final HashSet g = new HashSet();

    /* renamed from: G */
    public final ExecutorService f6074h = Executors.newSingleThreadExecutor();

    /* renamed from: H */
    public final Handler f6075i = new Handler(Looper.getMainLooper());

    /* renamed from: z */
    public c f6068a;

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.AppPickerActivity.a to com.parvaz.tunnel.AppPickerActivity$a */
    /* loaded from: classes.dex */
    public class a implements View.OnClickListener {
        public a() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            AppPickerActivity.this.lambda$onCreate$0(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.AppPickerActivity.b to com.parvaz.tunnel.AppPickerActivity$b */
    /* loaded from: classes.dex */
    public class b implements TextWatcher {
        public b() {
        }

        @Override // android.text.TextWatcher
        public final void afterTextChanged(Editable editable) {
        }

        @Override // android.text.TextWatcher
        public final void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        }

        @Override // android.text.TextWatcher
        public final void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            AppPickerActivity.this.h(charSequence == null ? "" : charSequence.toString());
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.AppPickerActivity.c to com.parvaz.tunnel.AppPickerActivity$c */
    /* loaded from: classes.dex */
    public class c extends RecyclerView.Adapter<e> {
        public c() {
        }

        public AppPickerActivity outer() {
            return AppPickerActivity.this;
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        /* renamed from: a */
        public final int getItemCount() {
            return AppPickerActivity.this.f6073f.size();
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public final void onBindViewHolder(e eVar, int i) {
            e eVar2 = eVar;
            AppPickerActivity appPickerActivity = AppPickerActivity.this;
            d dVar = (d) appPickerActivity.f6073f.get(i);
            eVar2.f6084w.setText(dVar.f6080b);
            eVar2.f6085x.setText(dVar.f6081c);
            eVar2.f6083v.setImageDrawable(dVar.f6079a);
            CheckBox checkBox = eVar2.u;
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(appPickerActivity.g.contains(dVar.f6081c));
            checkBox.setOnCheckedChangeListener(new AppPickerActivity_Adapter_1(this, dVar));
            eVar2.itemView.setOnClickListener(new AppPickerActivity_Adapter_2(eVar2));
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public final e onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            return new e(LayoutInflater.from(AppPickerActivity.this).inflate(R.layout.item_app, viewGroup, false));
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.AppPickerActivity.d to com.parvaz.tunnel.AppPickerActivity$d */
    /* loaded from: classes.dex */
    public static class d {

        /* renamed from: a */
        public Drawable f6079a;

        /* renamed from: b */
        public String f6080b;

        /* renamed from: c */
        public String f6081c;

        /* renamed from: d */
        public boolean f6082d;
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.AppPickerActivity.e to com.parvaz.tunnel.AppPickerActivity$e */
    /* loaded from: classes.dex */
    public static class e extends RecyclerView.ViewHolder {
        public final CheckBox u;

        /* renamed from: v */
        public final ImageView f6083v;

        /* renamed from: w */
        public final TextView f6084w;

        /* renamed from: x */
        public final TextView f6085x;

        public e(View view) {
            super(view);
            this.f6084w = (TextView) view.findViewById(R.id.label);
            this.f6085x = (TextView) view.findViewById(R.id.pkg);
            this.f6083v = (ImageView) view.findViewById(R.id.language);
            this.u = (CheckBox) view.findViewById(R.id.check);
        }
    }

    /* renamed from: A */
    public final void h(String str) {
        String lowerCase = str.trim().toLowerCase(Locale.getDefault());
        ArrayList arrayList = this.f6073f;
        arrayList.clear();
        Iterator it = this.f6072e.iterator();
        while (it.hasNext()) {
            d dVar = (d) it.next();
            if (lowerCase.isEmpty() || dVar.f6080b.toLowerCase(Locale.getDefault()).contains(lowerCase) || dVar.f6081c.toLowerCase(Locale.getDefault()).contains(lowerCase)) {
                arrayList.add(dVar);
            }
        }
        this.f6068a.notifyDataSetChanged();
        this.f6069b.setText(getString(R.string.split_selected, Integer.valueOf(this.g.size())));
    }

    @Override // androidx.appcompat.app.AppCompatActivity, android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    public final void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }

    public void lambda$onCreate$0(View view) {
        finish();
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public final void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_app_picker);
        Prefs prefs = new Prefs(this);
        this.B = prefs;
        this.g.addAll(prefs.c());
        this.f6069b = (TextView) findViewById(R.id.count);
        this.f6071d = findViewById(R.id.progress);
        findViewById(R.id.back).setOnClickListener(new a());
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.app_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        c cVar = new c();
        this.f6068a = cVar;
        recyclerView.setAdapter(cVar);
        ((EditText) findViewById(R.id.search)).addTextChangedListener(new b());
        this.f6071d.setVisibility(0);
        this.f6074h.execute(new AppPickerActivity_3(0, this));
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    public final void onDestroy() {
        super.onDestroy();
        this.f6074h.shutdownNow();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    public final void onPause() {
        super.onPause();
        Prefs prefs = this.B;
        HashSet hashSet = this.g;
        prefs.getClass();
        StringBuilder sb = new StringBuilder();
        Iterator it = hashSet.iterator();
        while (it.hasNext()) {
            String str = (String) it.next();
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(str);
        }
        prefs.f343a.edit().putString("per_app_list", sb.toString()).apply();
    }
}
