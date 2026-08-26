package com.parvaz.tunnel;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.store.Prefs;

/**
 * Free-form domain lists that force names direct, through the proxy, or into the
 * blackhole. The existing rules screen is per-rule and structured; this is the
 * bulk-paste companion, which is how people actually maintain these lists.
 */
public class DomainRulesActivity extends AppCompatActivity {

    /** Iranian services that misbehave when reached from a foreign exit. */
    private static final String PRESET_IR =
            "digikala.com\n"
            + "aparat.com\n"
            + "varzesh3.com\n"
            + "bank.ir\n"
            + "shaparak.ir\n"
            + "irancell.ir\n"
            + "mci.ir\n"
            + "snapp.ir\n"
            + "divar.ir\n"
            + "cafebazaar.ir\n"
            + "myket.ir\n"
            + "namava.ir\n"
            + "filimo.com\n"
            + "telewebion.com";

    private static final String PRESET_ADS =
            "doubleclick.net\n"
            + "googlesyndication.com\n"
            + "googleadservices.com\n"
            + "google-analytics.com\n"
            + "adservice.google.com\n"
            + "ads.yahoo.com\n"
            + "adnxs.com\n"
            + "scorecardresearch.com\n"
            + "yektanet.com\n"
            + "tapsell.ir\n"
            + "adivery.com";

    private Prefs prefs;
    private EditText directInput;
    private EditText proxyInput;
    private EditText blockInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_domainrules);
        prefs = new Prefs(this);

        directInput = findViewById(R.id.domain_direct_input);
        proxyInput = findViewById(R.id.domain_proxy_input);
        blockInput = findViewById(R.id.domain_block_input);

        directInput.setText(prefs.f343a.getString("domains_direct", ""));
        proxyInput.setText(prefs.f343a.getString("domains_proxy", ""));
        blockInput.setText(prefs.f343a.getString("domains_block", ""));

        ImageButton back = findViewById(R.id.back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        findViewById(R.id.preset_ir).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendUnique(directInput, PRESET_IR);
                Snackbar.make(v, R.string.domain_preset_apply, -1).show();
            }
        });

        findViewById(R.id.preset_ads).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendUnique(blockInput, PRESET_ADS);
                Snackbar.make(v, R.string.domain_preset_apply, -1).show();
            }
        });

        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.f343a.edit()
                        .putString("domains_direct", clean(directInput))
                        .putString("domains_proxy", clean(proxyInput))
                        .putString("domains_block", clean(blockInput))
                        .apply();
                if (com.parvaz.tunnel.core.TunnelVpnService.serviceRunning) {
                    android.content.Intent restart = new android.content.Intent(
                            DomainRulesActivity.this, com.parvaz.tunnel.core.TunnelVpnService.class);
                    restart.setAction("com.parvaz.tunnel.RESTART");
                    DomainRulesActivity.this.startService(restart);
                }
                Snackbar.make(v, R.string.saved, -1).show();
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                }, 400L);
            }
        });
    }

    /** Adds only the preset entries the field does not already contain. */
    private void appendUnique(EditText field, String preset) {
        String current = field.getText().toString();
        StringBuilder sb = new StringBuilder(current.trim());
        for (String line : preset.split("\n")) {
            String entry = line.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (!containsLine(current, entry)) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(entry);
            }
        }
        field.setText(sb.toString());
    }

    private static boolean containsLine(String haystack, String entry) {
        for (String line : haystack.split("\n")) {
            if (line.trim().equalsIgnoreCase(entry)) {
                return true;
            }
        }
        return false;
    }

    /** Normalises to one trimmed, non-empty domain per line. */
    private static String clean(EditText field) {
        StringBuilder sb = new StringBuilder();
        for (String line : field.getText().toString().split("\n")) {
            String entry = line.trim().toLowerCase(java.util.Locale.US);
            // Tolerate pasted URLs and leading wildcards.
            if (entry.startsWith("http://")) {
                entry = entry.substring(7);
            } else if (entry.startsWith("https://")) {
                entry = entry.substring(8);
            }
            if (entry.startsWith("*.")) {
                entry = entry.substring(2);
            }
            int slash = entry.indexOf('/');
            if (slash > 0) {
                entry = entry.substring(0, slash);
            }
            if (entry.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(entry);
        }
        return sb.toString();
    }

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }
}
