package com.parvaz.tunnel.core;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Checks the project's GitHub releases for a newer build and downloads the APK
 * (idea 2.3).
 *
 * <p>Each release publishes two variants. The arm64-only APK is preferred on 64-bit
 * devices since it is about a third smaller; the universal APK is the fallback.
 *
 * <p>Only the public releases API is used, so no token is required and nothing about
 * the user is transmitted beyond the plain HTTPS request.
 */
public final class UpdateChecker {

    /** Public releases endpoint for the project repository. */
    private static final String RELEASES_API =
            "https://api.github.com/repos/hojjatrad/parvaz/releases/latest";

    private static final String PREFS = "parvaz_update";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_SKIPPED = "skipped_version";

    /** At most one automatic check per day. */
    private static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;

    private UpdateChecker() {
    }

    /** Details of an available release. */
    public static final class Release {
        public String version = "";
        public String notes = "";
        public String downloadUrl = "";
        public long size = 0;

        public boolean valid() {
            return !version.isEmpty() && !downloadUrl.isEmpty();
        }
    }

    /** Reports download progress as a percentage. */
    public interface DownloadProgress {
        void onProgress(int percent);
    }

    /**
     * Fetches the latest release metadata. Blocking; call off the main thread.
     *
     * @return the release when it is newer than the running build, otherwise null
     */
    public static Release check(Context context) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(RELEASES_API).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "Parvaz");

            if (conn.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + conn.getResponseCode());
            }

            StringBuilder body = new StringBuilder();
            InputStream in = conn.getInputStream();
            try {
                InputStreamReader reader = new InputStreamReader(in, "UTF-8");
                char[] buf = new char[8192];
                while (true) {
                    int read = reader.read(buf);
                    if (read <= 0) {
                        break;
                    }
                    body.append(buf, 0, read);
                }
            } finally {
                closeQuietly(in);
            }

            JSONObject json = new JSONObject(body.toString());

            Release release = new Release();
            release.version = normalizeVersion(json.optString("tag_name", ""));
            release.notes = json.optString("body", "");

            JSONArray assets = json.optJSONArray("assets");
            String universal = "";
            long universalSize = 0;
            String arm64 = "";
            long arm64Size = 0;
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) {
                        continue;
                    }
                    String name = asset.optString("name", "").toLowerCase(Locale.US);
                    String url = asset.optString("browser_download_url", "");
                    long size = asset.optLong("size", 0);
                    if (!name.endsWith(".apk") || url.isEmpty()) {
                        continue;
                    }
                    if (name.contains("arm64")) {
                        arm64 = url;
                        arm64Size = size;
                    } else if (name.contains("universal") || universal.isEmpty()) {
                        universal = url;
                        universalSize = size;
                    }
                }
            }

            if (is64Bit() && !arm64.isEmpty()) {
                release.downloadUrl = arm64;
                release.size = arm64Size;
            } else {
                release.downloadUrl = universal;
                release.size = universalSize;
            }

            markChecked(context);

            if (!release.valid()) {
                return null;
            }
            return isNewer(release.version, currentVersion(context)) ? release : null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                    android.util.Log.w("Parvaz/UpdateChecker", "Exception ignored", ignored);
                }
            }
        }
    }

    /**
     * Downloads the APK into external cache so a FileProvider can hand it to the
     * package installer. Blocking.
     */
    public static File download(Context context, Release release, DownloadProgress progress)
            throws Exception {
        File dir = context.getExternalCacheDir();
        if (dir == null) {
            dir = context.getCacheDir();
        }
        File target = new File(dir, "parvaz-" + release.version + ".apk");
        File temp = new File(dir, target.getName() + ".part");

        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(release.downloadUrl).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Parvaz");

            if (conn.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + conn.getResponseCode());
            }

            long total = release.size > 0 ? release.size : conn.getContentLength();
            in = conn.getInputStream();
            out = new FileOutputStream(temp);

            byte[] buf = new byte[65536];
            long done = 0;
            int lastPercent = -1;
            while (true) {
                int read = in.read(buf);
                if (read <= 0) {
                    break;
                }
                out.write(buf, 0, read);
                done += read;
                if (progress != null && total > 0) {
                    int percent = (int) ((done * 100) / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        progress.onProgress(percent);
                    }
                }
            }
            out.flush();
            out.close();
            out = null;

            if (target.exists()) {
                target.delete();
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("could not finalise download");
            }
            return target;
        } catch (Exception e) {
            temp.delete();
            throw e;
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                    android.util.Log.w("Parvaz/UpdateChecker", "Exception ignored", ignored);
                }
            }
        }
    }

    /** True when an automatic background check is due. */
    public static boolean isCheckDue(Context context) {
        long last = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK, 0L);
        return System.currentTimeMillis() - last > CHECK_INTERVAL_MS;
    }

    /** Suppresses prompts for one specific version. */
    public static void skip(Context context, String version) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SKIPPED, version).apply();
    }

    /** True when the user asked not to be reminded about this version. */
    public static boolean isSkipped(Context context, String version) {
        return version.equals(context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SKIPPED, ""));
    }

    /** The running app's versionName. */
    public static String currentVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "0";
        }
    }

    /**
     * Compares dotted versions numerically, so 1.10 correctly beats 1.9. Non-numeric
     * segments are ignored rather than throwing.
     */
    static boolean isNewer(String candidate, String current) {
        String[] a = normalizeVersion(candidate).split("\\.");
        String[] b = normalizeVersion(current).split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int ai = i < a.length ? parse(a[i]) : 0;
            int bi = i < b.length ? parse(b[i]) : 0;
            if (ai != bi) {
                return ai > bi;
            }
        }
        return false;
    }

    private static int parse(String s) {
        try {
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits.append(c);
                } else {
                    break;
                }
            }
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Strips a leading "v" and surrounding whitespace from a tag name. */
    static String normalizeVersion(String tag) {
        if (tag == null) {
            return "";
        }
        String out = tag.trim();
        if (out.startsWith("v") || out.startsWith("V")) {
            out = out.substring(1);
        }
        return out.trim();
    }

    private static boolean is64Bit() {
        try {
            String[] abis = Build.SUPPORTED_64_BIT_ABIS;
            return abis != null && abis.length > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void markChecked(Context context) {
        try {
            context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {
            android.util.Log.w("Parvaz/UpdateChecker", "Throwable ignored", ignored);
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                android.util.Log.w("Parvaz/UpdateChecker", "Exception ignored", ignored);
            }
        }
    }
}
