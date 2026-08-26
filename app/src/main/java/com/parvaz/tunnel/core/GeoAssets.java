package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Hybrid geo-data strategy (idea 2.1).
 *
 * <p>The APK bundles the small Iran-tuned <em>-lite</em> geoip/geosite files so routing
 * is correct on the very first launch with no network round trip. Later — and at most
 * once a week — the full-size files are fetched in the background and swapped in. The
 * full set covers far more domains (notably the long tail of Iranian CDNs and ad
 * networks) but is ~25 MB, too much to ship inside the APK.
 *
 * <p>Downloads land in a temporary file and are only renamed over the live one after
 * the whole body arrives and passes a size check, so a dropped connection can never
 * leave the core with a truncated .dat.
 */
public final class GeoAssets {

    private static final String TAG = "ParvazGeo";

    private static final String PREFS = "parvaz_geo";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_FULL_VERSION = "full_installed";

    /** Upstream releases weekly; no point checking more often. */
    private static final long CHECK_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000;

    /** Anything smaller than this is an error page, not a geo database. */
    private static final long MIN_FULL_SIZE = 1024 * 1024;

    private static final String[] MIRRORS = {
            "https://github.com/chocolate4u/Iran-v2ray-rules/releases/latest/download/",
            "https://cdn.jsdelivr.net/gh/chocolate4u/Iran-v2ray-rules@release/",
    };

    private static volatile boolean sRunning = false;

    private GeoAssets() {
    }

    /**
     * Copies the bundled lite files into place if the core has none yet. Must run
     * before {@code Libv2ray.initCoreEnv}. Cheap: it no-ops once the files exist.
     */
    public static void installBundled(Context context) {
        File dir = context.getFilesDir();
        CoreManager.copyAssetIfNeeded(context, "geoip.dat", new File(dir, "geoip.dat"));
        CoreManager.copyAssetIfNeeded(context, "geosite.dat", new File(dir, "geosite.dat"));
    }

    /**
     * Starts a background upgrade to the full geo files when one is due. Safe to call
     * on every launch: returns immediately if a download is already running or the
     * weekly interval has not elapsed.
     */
    public static void maybeUpgrade(Context context) {
        final Context app = context.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        if (System.currentTimeMillis() - sp.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return;
        }
        if (sRunning) {
            return;
        }
        sRunning = true;

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean ok = download(app, "geoip.dat") && download(app, "geosite.dat");
                    SharedPreferences prefs =
                            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(KEY_LAST_CHECK, System.currentTimeMillis());
                    if (ok) {
                        editor.putBoolean(KEY_FULL_VERSION, true);
                        // Routing decides which geosite:/geoip: rules are safe to emit
                        // from the tag index; it must re-read the new files.
                        GeoIndex.invalidate();
                        LogBuffer.listener("geo data updated to the full Iran ruleset");
                    }
                    editor.apply();
                } catch (Throwable t) {
                    Log.w(TAG, "geo upgrade failed", t);
                } finally {
                    sRunning = false;
                }
            }
        }, "parvaz-geo");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    /** True once the full-size files have replaced the bundled lite ones. */
    public static boolean hasFullData(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_FULL_VERSION, false);
    }

    /**
     * Fetches one .dat file, trying each mirror in turn. Returns true only when a
     * plausibly-sized file was written and swapped into place.
     */
    private static boolean download(Context context, String name) {
        File dir = context.getFilesDir();
        File target = new File(dir, name);
        File temp = new File(dir, name + ".tmp");

        for (int i = 0; i < MIRRORS.length; i++) {
            HttpURLConnection conn = null;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                conn = (HttpURLConnection) new URL(MIRRORS[i] + name).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Parvaz");

                if (conn.getResponseCode() != 200) {
                    continue;
                }

                in = conn.getInputStream();
                out = new FileOutputStream(temp);
                byte[] buf = new byte[65536];
                long total = 0;
                while (true) {
                    int read = in.read(buf);
                    if (read <= 0) {
                        break;
                    }
                    out.write(buf, 0, read);
                    total += read;
                }
                out.flush();
                out.close();
                out = null;

                long minSize = "geoip.dat".equals(name) ? (20 * 1024) : (500 * 1024);
                if (total < minSize) {
                    temp.delete();     // almost certainly an HTML error page
                    continue;
                }

                if (target.exists() && !target.delete()) {
                    temp.delete();
                    return false;
                }
                if (temp.renameTo(target)) {
                    Log.i(TAG, "installed " + name + " (" + total + " bytes)");
                    return true;
                }
                temp.delete();
            } catch (Throwable t) {
                Log.w(TAG, "mirror " + i + " failed for " + name + ": " + t.getMessage());
            } finally {
                closeQuietly(out);
                closeQuietly(in);
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Throwable ignored) {
                        android.util.Log.w("Parvaz/GeoAssets", "Throwable ignored", ignored);
                    }
                }
            }
        }
        temp.delete();
        return false;
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {
                android.util.Log.w("Parvaz/GeoAssets", "Throwable ignored", ignored);
            }
        }
    }
}
