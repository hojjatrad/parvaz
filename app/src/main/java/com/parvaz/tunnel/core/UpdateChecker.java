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

    static final String SIGNING_SHA256 = "d1383b8f34da5d3299b13634de421487289d82c1bb47ec3bc7f20ae8d02fe500";
    private static final long MAX_APK_BYTES=128L*1024*1024;
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
        public String sha256 = "";

        public boolean valid() {
            return version.length()<=40 && version.matches("[0-9]+(\\.[0-9]+){1,3}") && size>0 && size<=MAX_APK_BYTES
                    && sha256.matches("[0-9a-f]{64}")
                    && downloadUrl.startsWith("https://github.com/hojjatrad/parvaz/releases/download/v"+version+"/");
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
            conn = AppNetwork.open(new URL(RELEASES_API));
            conn.setUseCaches(false);
            conn.setRequestProperty("Cache-Control","no-cache");
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
                    if(body.length()+read>2*1024*1024)throw new IllegalStateException("UPDATE_METADATA_TOO_LARGE");
                    body.append(buf, 0, read);
                }
            } finally {
                closeQuietly(in);
            }

            JSONObject json = new JSONObject(body.toString());

            if(json.optBoolean("draft",false)||json.optBoolean("prerelease",false))return null;
            Release release = new Release();
            release.version = normalizeVersion(json.optString("tag_name", ""));
            release.notes = json.optString("body", "");

            JSONArray assets = json.optJSONArray("assets");
            String universal = "";
            long universalSize = 0;
            String universalDigest="",arm64Digest="";
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
                    String digest=asset.optString("digest","");
                    digest=digest.startsWith("sha256:")?digest.substring(7).toLowerCase(Locale.ROOT):"";
                    if (!name.endsWith(".apk") || url.isEmpty()) {
                        continue;
                    }
                    if (name.contains("arm64")) {
                        arm64 = url;
                        arm64Size = size;arm64Digest=digest;
                    } else if (name.contains("universal") || universal.isEmpty()) {
                        universal = url;
                        universalSize = size;universalDigest=digest;
                    }
                }
            }

            if (is64Bit() && !arm64.isEmpty()) {
                release.downloadUrl = arm64;
                release.size = arm64Size;release.sha256=arm64Digest;
            } else {
                release.downloadUrl = universal;
                release.size = universalSize;release.sha256=universalDigest;
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

    /** Downloads/resumes an untrusted cache entry, then validates the complete APK.
     * Only the verified final name may be handed to the installer. */
    public static synchronized File download(Context context, Release input, DownloadProgress progress)throws Exception {
        Release release=new Release();release.version=input.version;release.size=input.size;
        release.sha256=input.sha256;release.downloadUrl=input.downloadUrl;
        if(!release.valid())throw new IllegalArgumentException("INVALID_UPDATE_METADATA");
        File dir=new File(context.getCacheDir(),"updates");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("UPDATE_STORAGE_UNAVAILABLE");
        File target=new File(dir,"parvaz-"+release.version+".apk");
        File temp=new File(dir,partialName(release)); // Keep .apk for platform archive parsing.
        // Keep at most one partial for the exact URL/hash/ABI/size. Cached APKs are
        // disposable; configuration, signing keys and backups are never touched.
        File[] previous=dir.listFiles();
        if(previous!=null)for(File file:previous)if(!file.equals(temp)&&!file.equals(target)&&
            file.getName().matches("(?:pending-[0-9.]+(?:-[0-9a-f]{64})?|parvaz-[0-9.]+)\\.apk(?:\\.part)?"))file.delete();
        if(target.isFile()){
            try{verifyReadyFile(context,target,release);if(progress!=null)progress.onProgress(100);return target;}
            catch(Exception invalid){if(!target.delete())throw new IllegalStateException("UPDATE_STORAGE_UNAVAILABLE");}
        }
        final AppNetwork.Route route=AppNetwork.capture(); // Never change route during redirects/retry.
        ResumableDownload.fetch(temp,release.size,release.sha256,
            offset->openApk(release.downloadUrl,offset,route),progress==null?null:progress::onProgress);
        try{verifyArchive(context,temp,release);}
        catch(Exception invalid){temp.delete();throw invalid;}
        if(!temp.renameTo(target))throw new IllegalStateException("UPDATE_FINALIZE_FAILED");
        if(progress!=null)progress.onProgress(100);return target;
    }
    static String partialName(Release release)throws Exception {
        String identity=release.version+"\n"+release.size+"\n"+release.sha256+"\n"+release.downloadUrl;
        return "pending-"+release.version+"-"+hex(java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)))+".apk";
    }

    static String safeError(Exception error) {
        if(error instanceof java.net.SocketTimeoutException)return "UPDATE_TIMEOUT";
        if(error instanceof javax.net.ssl.SSLException)return "UPDATE_TLS_FAILURE";
        if(error instanceof java.net.UnknownHostException)return "UPDATE_DNS_FAILURE";
        String code=error.getMessage();
        if(code!=null&&code.matches("HTTP [0-9]{3}"))return "UPDATE_HTTP_"+code.substring(5);
        return code!=null&&code.matches("[A-Z][A-Z0-9_]{2,80}")?code:"UPDATE_IO_OR_PLATFORM_ERROR";
    }
    static void verifyReadyFile(Context context,File file,Release release)throws Exception {
        if(!release.valid()||!file.isFile()||file.length()!=release.size||
                !file.getCanonicalFile().getParentFile().equals(new File(context.getCacheDir(),"updates").getCanonicalFile()))
            throw new IllegalStateException("INVALID_UPDATE_FILE");
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(InputStream in=new java.io.FileInputStream(file)) {
            byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);
        }
        if(!hex(digest.digest()).equals(release.sha256))throw new IllegalStateException("UPDATE_CHECKSUM_MISMATCH");
        verifyArchive(context,file,release);
    }

    private static HttpURLConnection openApk(String value,long offset,AppNetwork.Route route) throws Exception {
        URL url=new URL(value);
        for(int i=0;i<=5;i++) {
            String host=url.getHost().toLowerCase(Locale.ROOT);
            if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||
                    !(host.equals("github.com")||host.endsWith(".githubusercontent.com")))throw new IllegalStateException("UNTRUSTED_UPDATE_URL");
            HttpURLConnection connection=route.open(url);
            connection.setConnectTimeout(20000);connection.setReadTimeout(60000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","Parvaz");
            connection.setRequestProperty("Accept-Encoding","identity");
            if(offset>0)connection.setRequestProperty("Range","bytes="+offset+"-");
            int status;
            try{status=connection.getResponseCode();}catch(Exception e){connection.disconnect();throw e;}
            if(status==301||status==302||status==303||status==307||status==308) {
                String location=connection.getHeaderField("Location");connection.disconnect();
                if(location==null)throw new IllegalStateException("INVALID_UPDATE_REDIRECT");
                url=new URL(url,location);continue;
            }
            return connection;
        }
        throw new IllegalStateException("UPDATE_REDIRECT_LIMIT");
    }
    static String hex(byte[] bytes) {
        StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
    static void verifyArchive(Context context,File file,Release release) throws Exception {
        android.content.pm.PackageManager pm=context.getPackageManager();
        int flags=ArchiveSignatures.flags();
        android.content.pm.PackageInfo archive=ArchiveSignatures.read(pm,file);
        android.content.pm.PackageInfo installed=pm.getPackageInfo(context.getPackageName(),flags);
        if(archive==null)throw new IllegalStateException("UPDATE_ARCHIVE_UNREADABLE");
        if(!context.getPackageName().equals(archive.packageName))throw new IllegalStateException("UPDATE_PACKAGE_ID_MISMATCH");
        if(!release.version.equals(archive.versionName))throw new IllegalStateException("UPDATE_VERSION_NAME_MISMATCH");
        long next=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        long current=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
        if(next<=current)throw new IllegalStateException("UPDATE_NOT_NEWER");
        android.content.pm.Signature[] signers=Build.VERSION.SDK_INT>=28?(archive.signingInfo==null?null:archive.signingInfo.getApkContentsSigners()):archive.signatures;
        android.content.pm.Signature[] own=Build.VERSION.SDK_INT>=28?(installed.signingInfo==null?null:installed.signingInfo.getApkContentsSigners()):installed.signatures;
        if(signers==null||signers.length!=1||own==null||own.length!=1||!signers[0].equals(own[0])||
                !SIGNING_SHA256.equals(hex(java.security.MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray()))))
            throw new IllegalStateException("UPDATE_SIGNATURE_MISMATCH");
        // The Android package installer performs the final cryptographic/install checks.
    }

    public static synchronized boolean claimAutomaticCheck(Context context) {
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis(),last=prefs.getLong("last_auto_attempt",0);
        if(!isCheckDue(context)||(now>=last&&now-last<6L*60*60*1000))return false;
        prefs.edit().putLong("last_auto_attempt",now).apply();return true;
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
