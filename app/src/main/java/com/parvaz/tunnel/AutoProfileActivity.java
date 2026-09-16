package com.parvaz.tunnel;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.parvaz.tunnel.store.Prefs;

/**
 * Per-network behaviour: connect automatically on mobile data, stay off on a
 * trusted home Wi-Fi, and so on. The decision itself is made by
 * {@link com.parvaz.tunnel.core.AutoProfile} when the network changes.
 */
public class AutoProfileActivity extends com.parvaz.tunnel.LockedActivity {

    /** Index order must match the spinner entries below. */
    private static final String[] ACTIONS = {"none", "connect", "disconnect"};

    private Prefs prefs;
    private Spinner wifiSpinner;
    private Spinner cellSpinner;
    private EditText trustedInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autoprofile);
        prefs = new Prefs(this);

        wifiSpinner = findViewById(R.id.wifi_spinner);
        cellSpinner = findViewById(R.id.cell_spinner);
        trustedInput = findViewById(R.id.trusted_wifi_input);

        String[] labels = {
            getString(R.string.auto_profile_ask),
            getString(R.string.auto_profile_connect),
            getString(R.string.auto_profile_disconnect),
        };

        wifiSpinner.setAdapter((SpinnerAdapter) buildAdapter(labels));
        cellSpinner.setAdapter((SpinnerAdapter) buildAdapter(labels));

        wifiSpinner.setSelection(indexOf(prefs.f343a.getString("auto_wifi", "none")));
        cellSpinner.setSelection(indexOf(prefs.f343a.getString("auto_cell", "none")));
        trustedInput.setText(prefs.f343a.getString("trusted_wifi", ""));

        ImageButton back = findViewById(R.id.back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!normaliseSsids(trustedInput.getText().toString()).isEmpty()
                    &&androidx.core.content.ContextCompat.checkSelfPermission(AutoProfileActivity.this,android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(AutoProfileActivity.this).setTitle(R.string.trusted_wifi_permission_title).setMessage(R.string.trusted_wifi_permission_help)
                      .setPositiveButton(R.string.ok,(d,w)->androidx.core.app.ActivityCompat.requestPermissions(AutoProfileActivity.this,new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION},905))
                      .setNegativeButton(R.string.cancel,null).show();return;
                }
                prefs.f343a.edit()
                        .putString("auto_wifi", ACTIONS[clamp(wifiSpinner.getSelectedItemPosition())])
                        .putString("auto_cell", ACTIONS[clamp(cellSpinner.getSelectedItemPosition())])
                        .putString("trusted_wifi", normaliseSsids(trustedInput.getText().toString()))
                        .apply();
                com.parvaz.tunnel.core.NetworkAutomation.settingsChanged(getApplicationContext());
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

    private ArrayAdapter<String> buildAdapter(String[] labels) {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private static int indexOf(String action) {
        for (int i = 0; i < ACTIONS.length; i++) {
            if (ACTIONS[i].equals(action)) {
                return i;
            }
        }
        return 0;
    }

    private static int clamp(int position) {
        return (position < 0 || position >= ACTIONS.length) ? 0 : position;
    }

    /** One SSID per line, trimmed, blanks dropped. */
    private static String normaliseSsids(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            String ssid = line.trim();
            if (ssid.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(ssid);
        }
        return sb.toString();
    }

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(App.wrapLocale(context));
    }
}
