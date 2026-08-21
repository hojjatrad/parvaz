package com.parvaz.tunnel;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.parvaz.tunnel.RulesActivity__ExternalSyntheticOutline0;
import com.parvaz.tunnel.core.TunnelVpnService;
import com.parvaz.tunnel.store.Prefs;
import com.parvaz.tunnel.R;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class RulesActivity extends AppCompatActivity {

    /* renamed from: D */
    public static final String[] f6139e = {"domain", "ip", "port", "app"};

    /* renamed from: E */
    public static final String[] f6140f = {"proxy", "direct", "block"};

    /* renamed from: A */
    public TextView f6142b;
    public Prefs B;

    /* renamed from: C */
    public JSONArray f6144d = new JSONArray();

    /* renamed from: z */
    public d f6141a;

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.RulesActivity.a to com.parvaz.tunnel.RulesActivity$a */
    /* loaded from: classes.dex */
    public class a implements View.OnClickListener {
        public a() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            RulesActivity.this.lambda$onCreate$0(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.RulesActivity.b to com.parvaz.tunnel.RulesActivity$b */
    /* loaded from: classes.dex */
    public class b implements View.OnClickListener {
        public b() {
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(View view) {
            RulesActivity.this.lambda$onCreate$1(view);
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.RulesActivity.c to com.parvaz.tunnel.RulesActivity$c */
    /* loaded from: classes.dex */
    public class c implements DialogInterface.OnClickListener {

        /* renamed from: a */
        public final EditText f6147a;

        /* renamed from: b */
        public final Spinner f6148b;

        /* renamed from: c */
        public final Spinner f6149c;

        /* renamed from: d */
        public final int f6150d;

        public c(EditText editText, Spinner spinner, Spinner spinner2, int i) {
            this.f6147a = editText;
            this.f6148b = spinner;
            this.f6149c = spinner2;
            this.f6150d = i;
        }

        @Override // android.content.DialogInterface.OnClickListener
        public final void onClick(DialogInterface dialogInterface, int i) {
            Spinner spinner = this.f6148b;
            Spinner spinner2 = this.f6149c;
            RulesActivity rulesActivity = RulesActivity.this;
            rulesActivity.getClass();
            EditText editText = this.f6147a;
            String trim = editText.getText() == null ? "" : editText.getText().toString().trim();
            if (trim.isEmpty()) {
                return;
            }
            try {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("kind", RulesActivity.f6139e[spinner.getSelectedItemPosition()]);
                jSONObject.put("outbound", RulesActivity.f6140f[spinner2.getSelectedItemPosition()]);
                jSONObject.put("value", trim);
                int i2 = this.f6150d;
                if (i2 < 0) {
                    rulesActivity.f6144d.put(jSONObject);
                } else {
                    rulesActivity.f6144d.put(i2, jSONObject);
                }
                rulesActivity.h();
            } catch (Exception unused) {
                android.util.Log.w("Parvaz/RulesActivity", "Exception ignored", unused);
            }
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.RulesActivity.d to com.parvaz.tunnel.RulesActivity$d */
    /* loaded from: classes.dex */
    public class d extends RecyclerView.Adapter<e> {

        public RulesActivity outer() {
            return RulesActivity.this;
        }
        public d() {
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        /* renamed from: a */
        public final int getItemCount() {
            return RulesActivity.this.f6144d.length();
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public final void onBindViewHolder(e eVar, int i) {
            int i2;
            int i3;
            int i4;
            e eVar2 = eVar;
            RulesActivity rulesActivity = RulesActivity.this;
            JSONObject optJSONObject = rulesActivity.f6144d.optJSONObject(i);
            if (optJSONObject != null) {
                String optString = optJSONObject.optString("outbound", "proxy");
                String optString2 = optJSONObject.optString("kind", "domain");
                optString2.getClass();
                char c = 65535;
                switch (optString2.hashCode()) {
                    case 3367:
                        if (optString2.equals("ip")) {
                            c = 0;
                            break;
                        }
                        break;
                    case 96801:
                        if (optString2.equals("app")) {
                            c = 1;
                            break;
                        }
                        break;
                    case 3446913:
                        if (optString2.equals("port")) {
                            c = 2;
                            break;
                        }
                        break;
                }
                switch (c) {
                    case 0:
                        i2 = R.string.rule_kind_ip;
                        break;
                    case 1:
                        i2 = R.string.rule_kind_app;
                        break;
                    case 2:
                        i2 = R.string.rule_kind_port;
                        break;
                    default:
                        i2 = R.string.rule_kind_domain;
                        break;
                }
                eVar2.f6153v.setText(rulesActivity.getString(i2));
                eVar2.f6155x.setText(optJSONObject.optString("value", ""));
                optString.getClass();
                if (optString.equals("direct")) {
                    i3 = R.string.rule_out_direct;
                } else if (optString.equals("block")) {
                    i3 = R.string.rule_out_block;
                } else {
                    i3 = R.string.rule_out_proxy;
                }
                String string = rulesActivity.getString(i3);
                TextView textView = eVar2.f6154w;
                textView.setText(string);
                if (optString.equals("direct")) {
                    i4 = -13730510;
                } else if (!optString.equals("block")) {
                    i4 = -12756226;
                } else {
                    i4 = -1754827;
                }
                textView.setTextColor(i4);
                eVar2.itemView.setOnClickListener(new RulesActivity_Adapter_1(this, i));
                eVar2.u.setOnClickListener(new RulesActivity_Adapter_2(this, i));
            }
        }

        @Override // androidx.recyclerview.widget.RecyclerView.Adapter
        public final e onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            return new e(LayoutInflater.from(RulesActivity.this).inflate(R.layout.item_rule, viewGroup, false));
        }
    }

    /* JADX WARN: Can't change package for inner class: com.parvaz.tunnel.RulesActivity.e to com.parvaz.tunnel.RulesActivity$e */
    /* loaded from: classes.dex */
    public static class e extends RecyclerView.ViewHolder {
        public final TextView u;

        /* renamed from: v */
        public final TextView f6153v;

        /* renamed from: w */
        public final TextView f6154w;

        /* renamed from: x */
        public final TextView f6155x;

        public e(View view) {
            super(view);
            this.f6153v = (TextView) view.findViewById(R.id.kind);
            this.f6155x = (TextView) view.findViewById(R.id.value);
            this.f6154w = (TextView) view.findViewById(R.id.outbound);
            this.u = (TextView) view.findViewById(R.id.delete);
        }
    }

    /* renamed from: A */
    public final void h() {
        int i;
        RulesActivity__ExternalSyntheticOutline0.j(this.B.f343a, "custom_rules", this.f6144d.toString());
        this.f6141a.notifyDataSetChanged();
        TextView textView = this.f6142b;
        if (this.f6144d.length() == 0) {
            i = 0;
        } else {
            i = 8;
        }
        textView.setVisibility(i);
        if (TunnelVpnService.serviceRunning) {
            Intent intent = new Intent(this, (Class<?>) TunnelVpnService.class);
            intent.setAction("com.parvaz.tunnel.RESTART");
            startService(intent);
        }
    }

    /* renamed from: B */
    public final void i(int i) {
        int i2;
        JSONObject optJSONObject;
        View inflate = LayoutInflater.from(this).inflate(R.layout.dialog_rule, (ViewGroup) null);
        Spinner spinner = (Spinner) inflate.findViewById(R.id.kind);
        Spinner spinner2 = (Spinner) inflate.findViewById(R.id.outbound);
        EditText editText = (EditText) inflate.findViewById(R.id.value);
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, new String[]{getString(R.string.rule_kind_domain), getString(R.string.rule_kind_ip), getString(R.string.rule_kind_port), getString(R.string.rule_kind_app)});
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter((SpinnerAdapter) arrayAdapter);
        ArrayAdapter arrayAdapter2 = new ArrayAdapter(this, android.R.layout.simple_spinner_item, new String[]{getString(R.string.rule_out_proxy), getString(R.string.rule_out_direct), getString(R.string.rule_out_block)});
        arrayAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner2.setAdapter((SpinnerAdapter) arrayAdapter2);
        if (i >= 0 && (optJSONObject = this.f6144d.optJSONObject(i)) != null) {
            String[] strArr = f6139e;
            String optString = optJSONObject.optString("kind", "domain");
            int i3 = 0;
            int i4 = 0;
            while (true) {
                if (i4 < 4) {
                    if (strArr[i4].equals(optString)) {
                        break;
                    } else {
                        i4++;
                    }
                } else {
                    i4 = 0;
                    break;
                }
            }
            spinner.setSelection(i4);
            String[] strArr2 = f6140f;
            String optString2 = optJSONObject.optString("outbound", "proxy");
            int i5 = 0;
            while (true) {
                if (i5 >= 3) {
                    break;
                }
                if (strArr2[i5].equals(optString2)) {
                    i3 = i5;
                    break;
                }
                i5++;
            }
            spinner2.setSelection(i3);
            editText.setText(optJSONObject.optString("value", ""));
        }
        MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(this);
        if (i < 0) {
            i2 = R.string.rule_add;
        } else {
            i2 = R.string.custom_rules;
        }
        materialAlertDialogBuilder.setTitle(i2);
        materialAlertDialogBuilder.setView(inflate);
        materialAlertDialogBuilder.setNegativeButton(R.string.cancel, null);
        materialAlertDialogBuilder.setPositiveButton(R.string.ok, new c(editText, spinner, spinner2, i));
        materialAlertDialogBuilder.show();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    public final void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }

    public void lambda$onCreate$0(View view) {
        finish();
    }

    public void lambda$onCreate$1(View view) {
        i(-1);
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public final void onCreate(Bundle bundle) {
        int i;
        super.onCreate(bundle);
        setContentView(R.layout.activity_rules);
        this.B = new Prefs(this);
        try {
            this.f6144d = new JSONArray(this.B.f343a.getString("custom_rules", "[]"));
        } catch (Exception unused) {
            this.f6144d = new JSONArray();
        }
        this.f6142b = (TextView) findViewById(R.id.empty);
        findViewById(R.id.back).setOnClickListener(new a());
        findViewById(R.id.add_rule).setOnClickListener(new b());
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.rule_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        d dVar = new d();
        this.f6141a = dVar;
        recyclerView.setAdapter(dVar);
        this.f6141a.notifyDataSetChanged();
        TextView textView = this.f6142b;
        if (this.f6144d.length() == 0) {
            i = 0;
        } else {
            i = 8;
        }
        textView.setVisibility(i);
    }
}
