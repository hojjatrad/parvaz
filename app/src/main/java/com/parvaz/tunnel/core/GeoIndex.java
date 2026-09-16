package com.parvaz.tunnel.core;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Reports which {@code geosite:} / {@code geoip:} tags the installed geo data actually
 * contains.
 *
 * <p>This exists because Xray resolves geo references <em>while building the config</em>,
 * not lazily at routing time. A rule naming a tag that is absent from {@code geosite.dat}
 * aborts core startup with "failed to build" — the whole tunnel dies, and because ping
 * goes through the same builder, servers show no latency either. A routing refinement
 * therefore turns into a total outage.
 *
 * <p>That is exactly what the bundled offline files caused: the {@code -lite} datasets
 * ship only {@code category-ir} and {@code private}, while the routing rules also asked
 * for {@code category-ads-all}, {@code category-ads-ir}, {@code geosite:private} and
 * {@code geoip:ir}. Until the full files finished downloading in the background nothing
 * could connect — and in Iran that download is frequently blocked, so for some users it
 * never finished at all.
 *
 * <p>The tag lists are parsed straight out of the .dat files. Both are protobuf: a
 * sequence of length-delimited entries whose first field is the country/list code in
 * upper case. A bounded structural parser validates entries instead of guessing from
 * ASCII tag strings. Results are cached
 * per file (path + size + mtime) so repeated config builds cost nothing.
 */
public final class GeoIndex {

    private static final String TAG = "ParvazGeo";

    /** Guards the cache fields below. */
    private static final Object LOCK = new Object();

    private static String sGeositeStamp;
    private static Set<String> sGeositeTags = Collections.emptySet();

    private static String sGeoipStamp;
    private static Set<String> sGeoipTags = Collections.emptySet();

    private GeoIndex() {
    }

    /** Lower-case tags available in {@code geosite.dat}, e.g. "category-ads-all". */
    public static Set<String> geositeTags(Context context) {
        File f = new File(GeoAssets.directory(context.getApplicationContext()), "geosite.dat");
        synchronized (LOCK) {
            String stamp = stampOf(f);
            if (!stamp.equals(sGeositeStamp)) {
                sGeositeTags = scan(f);
                sGeositeStamp = stamp;
            }
            return sGeositeTags;
        }
    }

    /** Lower-case tags available in {@code geoip.dat}, e.g. "ir", "private". */
    public static Set<String> geoipTags(Context context) {
        File f = new File(GeoAssets.directory(context.getApplicationContext()), "geoip.dat");
        synchronized (LOCK) {
            String stamp = stampOf(f);
            if (!stamp.equals(sGeoipStamp)) {
                sGeoipTags = scan(f);
                sGeoipStamp = stamp;
            }
            return sGeoipTags;
        }
    }

    /**
     * True when {@code geosite.dat} can satisfy every one of {@code tags}.
     *
     * @param tags bare tag names without the {@code geosite:} prefix
     */
    public static boolean hasGeosite(Context context, String... tags) {
        Set<String> have = geositeTags(context);
        if (have.isEmpty()) {
            return false;
        }
        for (String t : tags) {
            if (!have.contains(t.toLowerCase(Locale.US))) {
                return false;
            }
        }
        return true;
    }

    /** True when {@code geoip.dat} can satisfy every one of {@code tags}. */
    public static boolean hasGeoip(Context context, String... tags) {
        Set<String> have = geoipTags(context);
        if (have.isEmpty()) {
            return false;
        }
        for (String t : tags) {
            if (!have.contains(t.toLowerCase(Locale.US))) {
                return false;
            }
        }
        return true;
    }

    /** Drops the cache so the next query re-reads the files (call after a geo update). */
    public static void invalidate() {
        synchronized (LOCK) {
            sGeositeStamp = null;
            sGeoipStamp = null;
            sGeositeTags = Collections.emptySet();
            sGeoipTags = Collections.emptySet();
        }
    }

    private static String stampOf(File f) {
        return f.getAbsolutePath() + ":" + f.length() + ":" + f.lastModified();
    }

    /**
     * Extracts candidate tag names from a geo .dat file.
     *
     * <p>Entries begin with a protobuf string field holding the code in upper case, so
     * every maximal run of {@code [A-Z0-9-]} of a plausible length is collected. Picking
     * up an occasional false positive is harmless here: the result is only ever used to
     * decide whether a rule is safe to emit, and a tag that really is absent still gets
     * rejected because the true name will not be in the set.
     */
    private static Set<String> scan(File file) {
        try{return GeoData.tags(file,file.getName().equals("geoip.dat"));}
        catch(java.io.IOException invalid){return Collections.emptySet();}
    }
}
