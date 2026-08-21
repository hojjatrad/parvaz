package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ready-made split-tunnel selections, plus detection of apps that are actively harmed
 * by being tunnelled.
 *
 * <p>Picking apps one by one out of a list of 200 is the part of split tunnelling users
 * give up on. Almost everyone wants one of three things: route only the apps that are
 * actually censored, route everything except the apps that break, or start from a clean
 * slate. These presets express exactly that, matched against the packages really
 * installed on the device so a preset never selects something that is not there.
 *
 * <p>The banking list matters most for Iranian users: domestic banking, payment and
 * government identity apps geo-restrict or hard-block foreign exit IPs, so tunnelling
 * them produces login failures that look like app bugs. Detection is deliberately
 * conservative — it matches known package prefixes rather than guessing from names — and
 * it only ever *suggests*; the user stays in control of the final selection.
 */
public final class SplitPresets {

    private SplitPresets() {
    }

    /** Preset ids, also used as the index of the chooser dialog. */
    public static final int PRESET_BROWSERS_MESSENGERS = 0;
    public static final int PRESET_EXCEPT_BANKING = 1;
    public static final int PRESET_CLEAR = 2;

    /**
     * Apps that are normally the reason someone installs a VPN: browsers, messengers and
     * the social/media apps that are blocked. Matched as exact package names.
     */
    private static final String[] BROWSERS_AND_MESSENGERS = {
            // Browsers
            "com.android.chrome",
            "org.mozilla.firefox",
            "org.mozilla.focus",
            "com.brave.browser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "com.kiwibrowser.browser",
            "com.sec.android.app.sbrowser",
            "com.vivaldi.browser",
            "org.torproject.torbrowser",
            "com.UCMobile.intl",
            // Messengers
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.thoughtcrime.securesms",
            "com.viber.voip",
            "jp.naver.line.android",
            "com.discord",
            "com.skype.raider",
            "im.vector.app",
            "org.telegram.plus",
            "com.facebook.orca",
            // Social / media that are commonly blocked
            "com.instagram.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.google.android.youtube",
            "com.reddit.frontpage",
            "com.linkedin.android",
            "com.facebook.katana",
            "com.pinterest",
            "com.snapchat.android",
            "com.spotify.music",
            "tv.twitch.android.app",
            "com.netflix.mediaclient",
            // Dev / work tools that break behind the filter
            "com.github.android",
            "com.slack",
            "com.google.android.apps.translate",
            "com.openai.chatgpt",
            "com.google.android.googlequicksearchbox"
    };

    /**
     * Package prefixes for Iranian banking, payment, brokerage and government identity
     * apps. These reject or throttle foreign exit IPs, so they must stay off the tunnel.
     * Prefix matching covers the many per-bank variants each vendor ships.
     */
    private static final String[] BANKING_PREFIXES = {
            // Banks
            "ir.bmi",                    // Bank Melli
            "com.bsi",                   // Bank Saderat
            "ir.tejaratbank",
            "com.tejarat",
            "ir.mellat",
            "com.pmb",                   // Bank Mellat / Hamrah Bank
            "mob.banking",               // Ayandeh / Sina and friends
            "ir.sepah",
            "com.mabnadp",
            "ir.parsian",
            "com.parsian",
            "ir.eghtesadnovin",
            "com.isc.bank",
            "ir.ba24",
            "ir.shahrbank",
            "com.dotin",                 // Dotin core-banking clients
            "ir.ansarbank",
            "ir.postbank",
            "ir.resalat",
            "ir.sinabank",
            "ir.kbi",                    // Karafarin
            "ir.saman",
            "com.samanbank",
            "ir.co.pna",
            "ir.co.sadad",               // Sadad / Bank Melli payment
            "ir.bpi",
            "ir.gardeshbank",
            "ir.day",
            "ir.iranzamin",
            "ir.melal",
            "ir.refah",
            "com.refah",
            // Payment, wallet and PSP
            "ir.asanpardakht",
            "com.farsitel.bazaar.pay",
            "ir.tgbs.android.iranapp",
            "ir.sep",                    // Saman Pardakht
            "ir.pec",                    // Parsian e-commerce
            "ir.behpardakht",
            "com.behpardakht",
            "ir.nazdika.pay",
            "ir.zarinpal",
            "ir.ap.wallet",
            "ir.balad.pay",
            "com.top",                   // Tosan
            "ir.hamrahcard",
            "ir.kishvic",
            "ir.novin.pardakht",
            "ir.snapp.pay",
            "ir.digipay",
            "com.pardakht",
            // Brokerage / exchange / crypto with KYC geo-locks
            "ir.tsetmc",
            "com.nobitex",
            "ir.wallex",
            "ir.exir",
            "ir.ramzinex",
            // Government and identity
            "ir.gov",
            "ir.ntsw",
            "ir.thr.taxpayer",
            "ir.mcls",
            "ir.sabtahval",
            "ir.police",
            "ir.imto",
            "ir.shaparak",
            "ir.setad",
            "ir.mci",                    // operator self-care, IP-locked
            "ir.irancell",
            "com.mtnirancell",
            "ir.rightel"
    };

    /**
     * Returns the installed packages a preset selects.
     *
     * @param context   any context
     * @param preset    one of the {@code PRESET_*} constants
     * @param installed every package currently offered in the picker
     * @return the subset of {@code installed} the preset wants tunnelled; never null
     */
    public static HashSet<String> apply(Context context, int preset, List<String> installed) {
        HashSet<String> out = new HashSet<String>();
        if (installed == null || installed.isEmpty()) {
            return out;
        }
        try {
            switch (preset) {
                case PRESET_BROWSERS_MESSENGERS: {
                    Set<String> wanted = new HashSet<String>(Arrays.asList(BROWSERS_AND_MESSENGERS));
                    for (String pkg : installed) {
                        if (pkg != null && wanted.contains(pkg)) {
                            out.add(pkg);
                        }
                    }
                    break;
                }
                case PRESET_EXCEPT_BANKING: {
                    for (String pkg : installed) {
                        if (pkg != null && !isBanking(pkg)) {
                            out.add(pkg);
                        }
                    }
                    break;
                }
                case PRESET_CLEAR:
                default:
                    break;
            }
        } catch (Throwable t) {
            Log.w("ParvazSplit", "preset failed", t);
        }
        return out;
    }

    /**
     * True when the package looks like an Iranian banking, payment or government app
     * that will misbehave through a foreign exit node.
     */
    public static boolean isBanking(String pkg) {
        if (pkg == null || pkg.length() == 0) {
            return false;
        }
        String lower = pkg.toLowerCase(Locale.US);
        for (String prefix : BANKING_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The banking-style apps present in a selection — i.e. the ones currently being
     * tunnelled that probably should not be.
     *
     * @return matching package names, sorted, possibly empty
     */
    public static ArrayList<String> bankingIn(Iterable<String> packages) {
        ArrayList<String> hits = new ArrayList<String>();
        if (packages == null) {
            return hits;
        }
        for (String pkg : packages) {
            if (isBanking(pkg) && !hits.contains(pkg)) {
                hits.add(pkg);
            }
        }
        Collections.sort(hits);
        return hits;
    }

    /**
     * Human-readable labels for packages, falling back to the package name when the app
     * cannot be resolved. Used to make the "exclude these?" prompt readable.
     */
    public static String labelsFor(Context context, List<String> packages, int limit) {
        StringBuilder sb = new StringBuilder();
        if (context == null || packages == null) {
            return "";
        }
        PackageManager pm = context.getPackageManager();
        int shown = 0;
        for (String pkg : packages) {
            if (shown >= limit) {
                sb.append(", …");
                break;
            }
            String label = pkg;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence cs = pm.getApplicationLabel(ai);
                if (cs != null && cs.length() > 0) {
                    label = cs.toString();
                }
            } catch (Throwable ignored) {
                // Keep the package name.
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(label);
            shown++;
        }
        return sb.toString();
    }
}
