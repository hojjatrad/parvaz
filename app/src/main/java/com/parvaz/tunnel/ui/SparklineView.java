package com.parvaz.tunnel.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A small live throughput graph for the home screen.
 *
 * <p>The numeric speed readout tells the user the instantaneous rate but nothing about
 * stability, which is what actually matters on a censored link: a tunnel that averages
 * 3 MB/s in ragged bursts feels far worse than a steady 1 MB/s. Plotting the last ~60
 * samples makes stalls, sawtoothing and recovery visible at a glance.
 *
 * <p>Download is drawn as a filled area, upload as a thin line on the same auto-scaled
 * axis so the two stay comparable. Scaling follows the window maximum with a floor, so
 * an idle tunnel shows a flat line instead of amplifying noise into a mountain range.
 */
public class SparklineView extends View {

    /** Samples kept; at one tick per second this is the last minute. */
    private static final int CAPACITY = 60;

    /** Never scale below this (bytes/s) or idle jitter fills the graph. */
    private static final long MIN_SCALE = 64L * 1024L;

    private final long[] down = new long[CAPACITY];
    private final long[] up = new long[CAPACITY];
    private int count = 0;
    private int head = 0;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint downPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint upPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int downColor = 0xFF4CC2FF;
    private int upColor = 0xFF8BC34A;
    private int lastWidth = 0;

    public SparklineView(Context context) {
        super(context);
        init();
    }

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SparklineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        downPaint.setStyle(Paint.Style.STROKE);
        downPaint.setStrokeWidth(1.8f * density);
        downPaint.setStrokeCap(Paint.Cap.ROUND);
        downPaint.setStrokeJoin(Paint.Join.ROUND);
        downPaint.setColor(downColor);

        upPaint.setStyle(Paint.Style.STROKE);
        upPaint.setStrokeWidth(1.2f * density);
        upPaint.setStrokeCap(Paint.Cap.ROUND);
        upPaint.setStrokeJoin(Paint.Join.ROUND);
        upPaint.setColor(upColor);

        fillPaint.setStyle(Paint.Style.FILL);

        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(1.0f * density);
        basePaint.setColor(0x22888888);
    }

    /** Overrides the default download/upload colours (e.g. to match the brand theme). */
    public void setColors(int downArgb, int upArgb) {
        this.downColor = downArgb;
        this.upColor = upArgb;
        downPaint.setColor(downArgb);
        upPaint.setColor(upArgb);
        lastWidth = 0;   // force gradient rebuild
        invalidate();
    }

    /**
     * Appends one throughput sample.
     *
     * @param downBytesPerSecond download rate for this tick
     * @param upBytesPerSecond   upload rate for this tick
     */
    public void push(long downBytesPerSecond, long upBytesPerSecond) {
        down[head] = downBytesPerSecond < 0 ? 0 : downBytesPerSecond;
        up[head] = upBytesPerSecond < 0 ? 0 : upBytesPerSecond;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
        invalidate();
    }

    /** Drops every sample — used when the tunnel disconnects. */
    public void clear() {
        count = 0;
        head = 0;
        invalidate();
    }

    /** True when there is nothing worth drawing yet. */
    public boolean isEmpty() {
        return count == 0;
    }

    /** Sample i in chronological order (0 = oldest kept). */
    private long at(long[] buf, int i) {
        int start = (count < CAPACITY) ? 0 : head;
        return buf[(start + i) % CAPACITY];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float baseline = h - 1f;
        canvas.drawLine(0, baseline, w, baseline, basePaint);

        if (count < 2) {
            return;
        }

        if (w != lastWidth) {
            fillPaint.setShader(new LinearGradient(
                    0, 0, 0, h,
                    (downColor & 0x00FFFFFF) | 0x66000000,
                    (downColor & 0x00FFFFFF),
                    Shader.TileMode.CLAMP));
            lastWidth = w;
        }

        long max = MIN_SCALE;
        for (int i = 0; i < count; i++) {
            long d = at(down, i);
            long u = at(up, i);
            if (d > max) {
                max = d;
            }
            if (u > max) {
                max = u;
            }
        }

        // Leave 12% headroom so a peak never touches the top edge.
        float scale = (h - 2f) / (max * 1.12f);
        float step = (count == 1) ? w : (w / (float) (count - 1));

        // Download: filled area.
        path.reset();
        path.moveTo(0, baseline);
        for (int i = 0; i < count; i++) {
            float x = i * step;
            float y = baseline - (at(down, i) * scale);
            path.lineTo(x, y);
        }
        path.lineTo((count - 1) * step, baseline);
        path.close();
        canvas.drawPath(path, fillPaint);

        // Download: outline.
        path.reset();
        for (int i = 0; i < count; i++) {
            float x = i * step;
            float y = baseline - (at(down, i) * scale);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, downPaint);

        // Upload: thin line.
        path.reset();
        for (int i = 0; i < count; i++) {
            float x = i * step;
            float y = baseline - (at(up, i) * scale);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, upPaint);
    }
}
