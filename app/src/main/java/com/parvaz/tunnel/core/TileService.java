package com.parvaz.tunnel.core;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.service.quicksettings.Tile;
import androidx.core.content.ContextCompat;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.R;

/* loaded from: classes.dex */
public class TileService extends android.service.quicksettings.TileService {
    public final void a() {
        int i;
        int i2;
        Tile qsTile = getQsTile();
        if (qsTile == null) {
            return;
        }
        boolean z = TunnelVpnService.serviceRunning;
        if (z) {
            i = 2;
        } else {
            i = 1;
        }
        qsTile.setState(i);
        qsTile.setLabel(getString(R.string.app_name));
        if (z) {
            i2 = R.string.state_connected;
        } else {
            i2 = R.string.state_disconnected;
        }
        qsTile.setContentDescription(getString(i2));
        qsTile.updateTile();
    }

    @Override // android.service.quicksettings.TileService
    public final void onClick() {
        super.onClick();
        if (TunnelVpnService.serviceRunning) {
            Intent intent = new Intent(this, (Class<?>) TunnelVpnService.class);
            intent.setAction("com.parvaz.tunnel.STOP");
            startService(intent);
        } else if (VpnService.prepare(this) != null) {
            Intent intent2 = new Intent(this, (Class<?>) MainActivity.class);
            intent2.addFlags(268435456);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent2, 201326592));
            } else {
                startActivityAndCollapse(intent2);
            }
        } else {
            Intent intent3 = new Intent(this, (Class<?>) TunnelVpnService.class);
            intent3.setAction("com.parvaz.tunnel.START");
            ContextCompat.startForegroundService(this, intent3);
        }
        a();
    }

    @Override // android.service.quicksettings.TileService
    public final void onStartListening() {
        super.onStartListening();
        a();
    }
}
