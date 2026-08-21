package com.parvaz.tunnel;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.parvaz.tunnel.core.LeakTester;
import com.parvaz.tunnel.core.TunnelVpnService;
import com.parvaz.tunnel.store.Prefs;

/**
 * Shows the user, with evidence, whether their traffic actually goes through the
 * tunnel: the public IP before and after, whether DNS answers from inside, and
 * whether IPv6 slips past.
 */
public class LeakTestActivity extends AppCompatActivity {

    private TextView ipBefore;
    private TextView ipAfter;
    private LinearLayout container;
    private TextView summary;
    private MaterialButton runButton;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaktest);

        ipBefore = findViewById(R.id.ip_before_value);
        ipAfter = findViewById(R.id.ip_after_value);
        container = findViewById(R.id.checks_container);
        summary = findViewById(R.id.leak_summary);
        runButton = findViewById(R.id.btn_run);

        ImageButton back = findViewById(R.id.back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        // The pre-connect address is captured by MainActivity before the tunnel
        // comes up; without it the "before" column would itself be tunnelled.
        String cached = new Prefs(this).f343a.getString("last_direct_ip", "");
        if (!cached.isEmpty()) {
            ipBefore.setText(cached);
        }

        runButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
    }

    private void start() {
        if (running) {
            return;
        }
        if (!TunnelVpnService.serviceRunning) {
            summary.setText(R.string.leak_test_need_connection);
            return;
        }
        running = true;
        runButton.setEnabled(false);
        runButton.setText(R.string.leak_test_running);
        container.removeAllViews();
        summary.setText("");
        ipAfter.setText(R.string.ip_checking_now);

        final String hint = new Prefs(this).f343a.getString("last_direct_ip", "");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final LeakTester.Report report = LeakTester.run(hint.isEmpty() ? null : hint);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        render(report);
                        running = false;
                        runButton.setEnabled(true);
                        runButton.setText(R.string.leak_test_run);
                    }
                });
            }
        }, "leak-test").start();
    }

    private void render(LeakTester.Report report) {
        ipBefore.setText(report.directIp.isEmpty() ? "—" : report.directIp);
        ipAfter.setText(report.tunnelIp.isEmpty() ? "—" : report.tunnelIp);

        LayoutInflater inflater = getLayoutInflater();
        for (LeakTester.Check check : report.checks) {
            View row = inflater.inflate(R.layout.item_leakcheck, container, false);
            TextView icon = row.findViewById(R.id.check_icon);
            TextView name = row.findViewById(R.id.check_name);
            TextView detail = row.findViewById(R.id.check_detail);
            TextView status = row.findViewById(R.id.check_status);

            name.setText(labelFor(check.name));
            detail.setText(explain(check));
            if (check.detail.isEmpty()) {
                detail.setVisibility(View.GONE);
            }

            if (check.status == LeakTester.PASS) {
                icon.setText("\u2713");
                icon.setTextColor(0xFF2E7D32);
                status.setText(R.string.leak_test_pass);
                status.setTextColor(0xFF2E7D32);
            } else if (check.status == LeakTester.FAIL) {
                icon.setText("\u2717");
                icon.setTextColor(0xFFC62828);
                status.setText(R.string.leak_test_fail);
                status.setTextColor(0xFFC62828);
            } else {
                icon.setText("?");
                icon.setTextColor(0xFFF9A825);
                status.setText(R.string.leak_test_warn);
                status.setTextColor(0xFFF9A825);
            }
            container.addView(row);
        }

        int failures = report.failures();
        if (failures == 0) {
            summary.setText(R.string.leak_test_summary_ok);
            summary.setTextColor(0xFF2E7D32);
        } else {
            summary.setText(getString(R.string.leak_test_summary_fail, failures));
            summary.setTextColor(0xFFC62828);
        }
    }

    private String labelFor(String key) {
        if ("ip".equals(key)) {
            return getString(R.string.leak_test_ip);
        }
        if ("dns".equals(key)) {
            return getString(R.string.leak_test_dns);
        }
        if ("ipv6".equals(key)) {
            return getString(R.string.leak_test_ipv6);
        }
        return key;
    }

    /** Prefers a plain-language verdict, falling back to the raw evidence. */
    private String explain(LeakTester.Check check) {
        if ("ip".equals(check.name)) {
            if (check.status == LeakTester.PASS) {
                return getString(R.string.leak_test_ip_changed) + "\n" + check.detail;
            }
            if (check.status == LeakTester.FAIL) {
                return getString(R.string.leak_test_ip_same) + "\n" + check.detail;
            }
        } else if ("dns".equals(check.name)) {
            if (check.status == LeakTester.PASS) {
                return getString(R.string.leak_test_dns_ok) + "\n" + check.detail;
            }
            if (check.status == LeakTester.FAIL) {
                return getString(R.string.leak_test_dns_leak) + "\n" + check.detail;
            }
        } else if ("ipv6".equals(check.name)) {
            if (check.status == LeakTester.PASS) {
                return getString(R.string.leak_test_ipv6_ok);
            }
            if (check.status == LeakTester.FAIL) {
                return getString(R.string.leak_test_ipv6_leak) + "\n" + check.detail;
            }
        }
        return check.detail;
    }

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }
}
