package com.parvaz.tunnel.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Detects a deliberate shake of the device.
 *
 * <p>When a server dies, the user is usually mid-task and holding the phone: opening the
 * app, finding the server page and picking a new entry is a lot of taps for something
 * that should be instant. A shake is a gesture the user can perform without looking.
 *
 * <p>Detection has to reject ordinary motion — walking, a phone tossed on a desk, a car
 * over a pothole — so it requires several strong accelerations in alternating directions
 * inside a short window, and then enforces a cool-down so one shake cannot fire twice.
 */
public final class ShakeDetector implements SensorEventListener {

    /** Callback for a confirmed shake. */
    public interface Listener {
        void onShake();
    }

    /** Acceleration, in g, above which a sample counts as a jolt. */
    private static final float THRESHOLD_G = 2.3f;

    /** Jolts required before a shake is reported. */
    private static final int REQUIRED_JOLTS = 4;

    /** Jolts must arrive within this window (ms) of each other. */
    private static final long JOLT_WINDOW_MS = 600L;

    /** Ignore everything for this long after firing (ms). */
    private static final long COOLDOWN_MS = 3000L;

    /** Ignore samples closer together than this (ms) — plain debouncing. */
    private static final long MIN_SAMPLE_GAP_MS = 60L;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Listener listener;

    private int joltCount = 0;
    private long lastJoltAt = 0L;
    private long lastFiredAt = 0L;
    private boolean registered = false;

    /**
     * @param context  any context
     * @param listener invoked on the main thread when a shake is confirmed
     */
    public ShakeDetector(Context context, Listener listener) {
        this.listener = listener;
        SensorManager sm = null;
        Sensor accel = null;
        try {
            sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) {
                accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        } catch (Throwable t) {
            Log.w("ParvazShake", "sensor lookup failed", t);
        }
        this.sensorManager = sm;
        this.accelerometer = accel;
    }

    /** True when this device actually has an accelerometer to listen to. */
    public boolean isAvailable() {
        return sensorManager != null && accelerometer != null;
    }

    /** Begins listening. Safe to call repeatedly. */
    public void start() {
        if (!isAvailable() || registered) {
            return;
        }
        try {
            // UI rate is plenty for a gesture this coarse and costs far less battery
            // than SENSOR_DELAY_GAME.
            registered = sensorManager.registerListener(
                    this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        } catch (Throwable t) {
            Log.w("ParvazShake", "register failed", t);
        }
    }

    /** Stops listening; must be called from onPause so the sensor does not drain power. */
    public void stop() {
        if (!isAvailable() || !registered) {
            return;
        }
        try {
            sensorManager.unregisterListener(this);
        } catch (Throwable t) {
            Log.w("ParvazShake", "unregister failed", t);
        }
        registered = false;
        joltCount = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length < 3 || listener == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastFiredAt < COOLDOWN_MS) {
            return;
        }
        if (now - lastJoltAt < MIN_SAMPLE_GAP_MS) {
            return;
        }

        float gx = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gz = event.values[2] / SensorManager.GRAVITY_EARTH;
        double magnitude = Math.sqrt((gx * gx) + (gy * gy) + (gz * gz));

        if (magnitude < THRESHOLD_G) {
            // A quiet stretch means whatever was building was not a shake.
            if (now - lastJoltAt > JOLT_WINDOW_MS) {
                joltCount = 0;
            }
            return;
        }

        if (now - lastJoltAt > JOLT_WINDOW_MS) {
            joltCount = 0;
        }
        joltCount++;
        lastJoltAt = now;

        if (joltCount >= REQUIRED_JOLTS) {
            joltCount = 0;
            lastFiredAt = now;
            try {
                listener.onShake();
            } catch (Throwable t) {
                Log.w("ParvazShake", "listener threw", t);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not relevant for a threshold gesture.
    }
}
