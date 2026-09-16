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
public class CleanIpActivity extends com.parvaz.tunnel.LockedActivity {

    private TextView statusText;
    private ProgressBar progressBar;
    private TextView bestIpText;
    private TextView resultsList;
    private MaterialButton startBtn;
    private MaterialButton applyBtn;
    private Thread verifyWorker;

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
        findViewById(R.id.btn_undo_clean_ip).setOnClickListener(v->afterUnlock(()->{
            if(com.parvaz.tunnel.core.TunnelVpnService.serviceRunning||!com.parvaz.tunnel.store.ProfileStore.f(this).undoEndpointEdit())Snackbar.make(v,R.string.cdn_not_applied,Snackbar.LENGTH_LONG).show();else Snackbar.make(v,R.string.saved,Snackbar.LENGTH_SHORT).show();
        }));

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
                    selectProfile(bestCleanIp);
                }
            }
        });
    }

    private void selectProfile(String ip){
        com.parvaz.tunnel.store.ProfileStore store=com.parvaz.tunnel.store.ProfileStore.f(this);
        java.util.ArrayList<com.parvaz.tunnel.model.Profile> eligible=new java.util.ArrayList<>();
        for(com.parvaz.tunnel.model.Profile p:store.activeProfiles())if(CleanIpScanner.eligible(p))eligible.add(com.parvaz.tunnel.store.ProfileIdentity.copy(p));
        if(eligible.isEmpty()){Snackbar.make(applyBtn,R.string.cdn_no_candidates,Snackbar.LENGTH_LONG).show();return;}
        String[] names=new String[eligible.size()];for(int i=0;i<names.length;i++)names[i]=eligible.get(i).remark;
        new com.parvaz.tunnel.core.SecureDialogBuilder(this).setTitle(R.string.cdn_select_one).setItems(names,(dialog,index)->{
            com.parvaz.tunnel.model.Profile original=eligible.get(index),candidate=CleanIpScanner.candidate(original,ip);
            new com.parvaz.tunnel.core.SecureDialogBuilder(this).setTitle(R.string.cdn_verify_title)
                .setMessage(original.address+" → "+candidate.address+"\nSNI: "+candidate.sni+"\n"+getString(R.string.cdn_verify_help))
                .setPositiveButton(R.string.ok,(d,w)->verifyEdit(store,original,candidate)).setNegativeButton(R.string.cancel,null).show();
        }).setNegativeButton(R.string.cancel,null).show();
    }
    private void verifyEdit(com.parvaz.tunnel.store.ProfileStore store,com.parvaz.tunnel.model.Profile original,com.parvaz.tunnel.model.Profile candidate){
        if(com.parvaz.tunnel.core.TunnelVpnService.serviceRunning){Snackbar.make(applyBtn,R.string.cdn_stop_first,Snackbar.LENGTH_LONG).show();return;}
        com.parvaz.tunnel.store.ProfileStore.StartupLatency owner=store.captureStartupLatency(original);applyBtn.setEnabled(false);
        final long epoch=com.parvaz.tunnel.core.LatencyStamp.networkRevision();
        verifyWorker=new Thread(()->{long measured=-1;try{measured=com.parvaz.tunnel.core.ProxyMeasurement.measureQueued(getApplicationContext(),candidate,"https://www.gstatic.com/generate_204");}catch(Exception ignored){}
            final long result=measured;runOnUiThread(()->afterUnlock(()->{
                if(isFinishing()||isDestroyed())return;applyBtn.setEnabled(true);
                if(result<=0||epoch!=com.parvaz.tunnel.core.LatencyStamp.networkRevision()||com.parvaz.tunnel.core.TunnelVpnService.serviceRunning||!store.replaceEndpoint(owner,candidate)){Snackbar.make(applyBtn,R.string.cdn_not_applied,Snackbar.LENGTH_LONG).show();return;}
                Snackbar.make(applyBtn,R.string.cdn_applied_one,Snackbar.LENGTH_INDEFINITE).setAction(R.string.undo,v->{
                    if(com.parvaz.tunnel.core.TunnelVpnService.serviceRunning||!store.undoEndpointEdit())Snackbar.make(applyBtn,R.string.cdn_not_applied,Snackbar.LENGTH_LONG).show();
                }).show();
            }));
        },"parvaz-cdn-verify");verifyWorker.start();
    }

    @Override protected void onDestroy(){if(verifyWorker!=null)verifyWorker.interrupt();handler.removeCallbacksAndMessages(null);super.onDestroy();}

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
