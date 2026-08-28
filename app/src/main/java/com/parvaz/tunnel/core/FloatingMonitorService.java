package com.parvaz.tunnel.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.R;

/**
 * Floating speed and ping overlay monitor on top of other applications.
 */
public class FloatingMonitorService extends Service {

    public static final String ACTION_TOGGLE = "com.parvaz.tunnel.FLOATING_TOGGLE";
    private static final String CHANNEL_ID = "parvaz_floating";
    private static final int NOTIFY_ID = 8822;

    private WindowManager windowManager;
    private View floatingView;
    private TextView speedView;
    private TextView pingView;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int state = intent.getIntExtra("state", 0);
            if (state == 4 && intent.hasExtra("downlink")) {
                long dDown = intent.getLongExtra("downlink", 0L);
                long dUp = intent.getLongExtra("uplink", 0L);
                if (speedView != null) {
                    speedView.setText("↓" + MainActivity.fmtSpeed(dDown) + " ↑" + MainActivity.fmtSpeed(dUp));
                }
            }
            int ping = intent.getIntExtra("ping", -1);
            if (ping > 0 && pingView != null) {
                pingView.setText(ping + " ms");
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.floating_monitor_running))
                .setSmallIcon(R.drawable.ic_tile)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        startForeground(NOTIFY_ID, n);

        initFloatingView();
        registerReceiver(stateReceiver, new IntentFilter("com.parvaz.tunnel.STATE"));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID, "Floating Monitor", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(chan);
        }
    }

    private void initFloatingView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 16);
        root.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#CC1E293B"));
        bg.setCornerRadius(28f);
        bg.setStroke(2, Color.parseColor("#33FFFFFF"));
        root.setBackground(bg);

        speedView = new TextView(this);
        speedView.setTextColor(Color.WHITE);
        speedView.setTextSize(11f);
        speedView.setText("↓0 B/s ↑0 B/s");

        pingView = new TextView(this);
        pingView.setTextColor(Color.parseColor("#10B981"));
        pingView.setTextSize(10f);
        pingView.setText("— ms");

        root.addView(speedView);
        root.addView(pingView);
        floatingView = root;

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 && Math.abs(event.getRawY() - initialTouchY) < 10) {
                            Intent launch = new Intent(FloatingMonitorService.this, MainActivity.class);
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(launch);
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatingView, params);
        } catch (Throwable t) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(stateReceiver);
        } catch (Throwable ignored) {
        }
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Throwable ignored) {
            }
        }
    }
}
