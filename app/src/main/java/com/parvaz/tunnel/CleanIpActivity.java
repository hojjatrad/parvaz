package com.parvaz.tunnel;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.core.CleanIpScanner;

import java.util.List;

/**
 * UI for Cloudflare Clean IP Scanner.
 */
public class CleanIpActivity extends AppCompatActivity {

    private TextView statusText;
    private ProgressBar progressBar;
    private TextView bestIpText;
    private TextView resultsList;
    private MaterialButton startBtn;
    private MaterialButton applyBtn;

    private String bestCleanIp = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cleanip);

        statusText = findViewById(R.id.scan_status);
        progressBar = findViewById(R.id.scan_progress);
        bestIpText = findViewById(R.id.best_ip_label);
        resultsList = findViewById(R.id.results_list);
        startBtn = findViewById(R.id.btn_start_scan);
        applyBtn = findViewById(R.id.btn_apply_clean_ip);

        ImageButton back = findViewById(R.id.back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScanning();
            }
        });

        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!bestCleanIp.isEmpty()) {
                    int changed = CleanIpScanner.applyCleanIpToCloudflareProfiles(CleanIpActivity.this, bestCleanIp);
                    Snackbar.make(v, getString(R.string.clean_ip_applied, bestCleanIp, changed), -1).show();
                }
            }
        });
    }

    private void startScanning() {
        startBtn.setEnabled(false);
        applyBtn.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        statusText.setText(R.string.clean_ip_scanning);
        bestIpText.setVisibility(View.GONE);
        resultsList.setText("");

        CleanIpScanner.scan(60, new CleanIpScanner.ScanCallback() {
            @Override
            public void onProgress(final int scanned, final int total, final CleanIpScanner.ScannedIp latest) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        progressBar.setProgress((scanned * 100) / total);
                        statusText.setText(getString(R.string.clean_ip_progress, scanned, total));
                    }
                });
            }

            @Override
            public void onComplete(final List<CleanIpScanner.ScannedIp> workingIps) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        startBtn.setEnabled(true);
                        progressBar.setVisibility(View.GONE);

                        if (workingIps.isEmpty()) {
                            statusText.setText(R.string.clean_ip_no_working);
                            resultsList.setText(R.string.clean_ip_no_results);
                            return;
                        }

                        bestCleanIp = workingIps.get(0).ip;
                        statusText.setText(getString(R.string.clean_ip_found_n, workingIps.size()));
                        bestIpText.setText(getString(R.string.clean_ip_best, bestCleanIp, workingIps.get(0).latencyMs));
                        bestIpText.setVisibility(View.VISIBLE);
                        applyBtn.setEnabled(true);

                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < Math.min(25, workingIps.size()); i++) {
                            CleanIpScanner.ScannedIp sip = workingIps.get(i);
                            sb.append(String.format(java.util.Locale.US, "%-16s  %4d ms\n", sip.ip, sip.latencyMs));
                        }
                        resultsList.setText(sb.toString().trim());
                    }
                });
            }
        });
    }

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }
}
