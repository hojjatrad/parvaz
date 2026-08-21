package com.parvaz.tunnel.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* renamed from: T1.a */
/* loaded from: classes.dex */
public final class FlagUtil {

    /* renamed from: a */
    public static final HashMap<String, String> f356a = new HashMap<>();

    static {
        b("US", "united states", "usa", "u.s.", "america", "amrika", "امریکا", "آمریکا");
        b("DE", "germany", "german", "deutsch", "frankfurt", "آلمان", "المان");
        b("NL", "netherlands", "holland", "dutch", "amsterdam", "هلند");
        b("GB", "united kingdom", "uk", "britain", "england", "london", "انگلیس");
        b("FR", "france", "paris", "فرانسه");
        b("TR", "turkey", "turkiye", "istanbul", "ترکیه", "ترکيه");
        b("AE", "emirates", "dubai", "uae", "امارات", "دبی");
        b("IR", "iran", "tehran", "ایران", "تهران");
        b("FI", "finland", "helsinki", "فنلاند");
        b("SE", "sweden", "stockholm", "سوئد");
        b("NO", "norway", "نروژ");
        b("DK", "denmark", "دانمارک");
        b("PL", "poland", "warsaw", "لهستان");
        b("RU", "russia", "moscow", "روسیه");
        b("UA", "ukraine", "اوکراین");
        b("CA", "canada", "toronto", "کانادا");
        b("JP", "japan", "tokyo", "ژاپن");
        b("KR", "korea", "seoul", "کره");
        b("SG", "singapore", "سنگاپور");
        b("HK", "hong kong", "hongkong", "هنگ کنگ");
        b("TW", "taiwan", "تایوان");
        b("CN", "china", "چین");
        b("IN", "india", "mumbai", "هند");
        b("AU", "australia", "sydney", "استرالیا");
        b("BR", "brazil", "برزیل");
        b("IT", "italy", "milan", "ایتالیا");
        b("ES", "spain", "madrid", "اسپانیا");
        b("CH", "switzerland", "zurich", "سوئیس");
        b("AT", "austria", "vienna", "اتریش");
        b("BE", "belgium", "بلژیک");
        b("CZ", "czech", "prague", "چک");
        b("RO", "romania", "bucharest", "رومانی");
        b("LT", "lithuania", "لیتوانی");
        b("LV", "latvia", "لتونی");
        b("EE", "estonia", "استونی");
        b("MD", "moldova", "مولداوی");
        b("BG", "bulgaria", "بلغارستان");
        b("HU", "hungary", "مجارستان");
        b("RS", "serbia", "صربستان");
        b("AM", "armenia", "ارمنستان");
        b("GE", "georgia", "گرجستان");
        b("AZ", "azerbaijan", "آذربایجان");
        b("KZ", "kazakhstan", "قزاقستان");
        b("QA", "qatar", "قطر");
        b("SA", "saudi", "عربستان");
        b("OM", "oman", "عمان");
        b("KW", "kuwait", "کویت");
        b("IL", "israel", "اسرائیل");
        b("ZA", "south africa", "آفریقای جنوبی");
        b("MX", "mexico", "مکزیک");
        b("AR", "argentina", "آرژانتین");
        b("CL", "chile", "شیلی");
        b("ID", "indonesia", "اندونزی");
        b("MY", "malaysia", "مالزی");
        b("TH", "thailand", "تایلند");
        b("VN", "vietnam", "ویتنام");
        b("PH", "philippines", "فیلیپین");
        b("IE", "ireland", "dublin", "ایرلند");
        b("PT", "portugal", "پرتغال");
        b("GR", "greece", "یونان");
        b("CY", "cyprus", "قبرس");
        b("IS", "iceland", "ایسلند");
        b("LU", "luxembourg", "لوکزامبورگ");
    }

    /* renamed from: a */
    public static void b(String str, String... strArr) {
        for (String str2 : strArr) {
            f356a.put(str2, str);
        }
    }

    /* renamed from: b */
    public static String c(String str) {
        if (str != null && str.length() == 2) {
            String upperCase = str.toUpperCase(Locale.US);
            char charAt = upperCase.charAt(0);
            char charAt2 = upperCase.charAt(1);
            if (charAt >= 'A' && charAt <= 'Z' && charAt2 >= 'A' && charAt2 <= 'Z') {
                try {
                    return new String(Character.toChars(charAt + 127397)).concat(new String(Character.toChars(charAt2 + 127397)));
                } catch (Exception unused) {
                }
            }
        }
        return "🌐";
    }

    /* renamed from: c */
    public static String flagForInner(String str, String str2) {
        String str3;
        String str4;
        int charCount;
        int codePointAt;
        StringBuilder sb = new StringBuilder();
        if (str == null) {
            str3 = "";
        } else {
            str3 = str;
        }
        sb.append(str3);
        sb.append(" ");
        if (str2 == null) {
            str2 = "";
        }
        sb.append(str2);
        String lowerCase = sb.toString().toLowerCase(Locale.US);
        // jadx emitted "str4 = null;" AFTER this loop, which threw away any flag we
        // had just found. Initialise before the loop instead.
        str4 = null;
        if (str != null) {
            for (int i = 0; i < str.length() - 1; i++) {
                int codePointAt2 = str.codePointAt(i);
                if (codePointAt2 >= 127462 && codePointAt2 <= 127487 && (charCount = Character.charCount(codePointAt2) + i) < str.length() && (codePointAt = str.codePointAt(charCount)) >= 127462 && codePointAt <= 127487) {
                    str4 = new String(Character.toChars(codePointAt2)).concat(new String(Character.toChars(codePointAt)));
                    break;
                }
            }
        }
        if (str4 != null) {
            return str4;
        }
        HashMap<String, String> hashMap = f356a;
        for (Map.Entry<String, String> entry : hashMap.entrySet()) {
            if (lowerCase.contains(entry.getKey())) {
                return c(entry.getValue());
            }
        }
        Matcher matcher = Pattern.compile("(?:^|[\\s\\[\\(\\|_\\-.])([a-z]{2})(?:$|[\\s\\]\\)\\|_\\-.\\d])").matcher(lowerCase);
        while (matcher.find()) {
            String upperCase = matcher.group(1).toUpperCase(Locale.US);
            if (hashMap.containsValue(upperCase)) {
                return c(upperCase);
            }
        }
        return "🌐";
    }

    /**
     * Returns the ISO country code inferred from a server's name and address, or null
     * when nothing matches. Shares the alias table used for flags so grouping and flag
     * emoji can never disagree.
     */
    public static String countryCodeFor(String remark, String address) {
        StringBuilder sb = new StringBuilder();
        sb.append(remark == null ? "" : remark);
        sb.append(' ');
        sb.append(address == null ? "" : address);
        String haystack = sb.toString().toLowerCase(Locale.US);

        HashMap<String, String> map = f356a;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (haystack.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        Matcher matcher = Pattern.compile(
                "(?:^|[\\s\\[\\(\\|_\\-.])([a-z]{2})(?:$|[\\s\\]\\)\\|_\\-.\\d])")
                .matcher(haystack);
        while (matcher.find()) {
            String upper = matcher.group(1).toUpperCase(Locale.US);
            if (map.containsValue(upper)) {
                return upper;
            }
        }
        return null;
    }

    /**
     * Human-readable group heading for a server: "\uD83C\uDDE9\uD83C\uDDEA DE", or a
     * globe plus "Other" when the country cannot be determined.
     */
    public static String groupLabelFor(String remark, String address, String otherLabel) {
        String code = countryCodeFor(remark, address);
        if (code == null) {
            return "\uD83C\uDF10 " + otherLabel;
        }
        return c(code) + " " + code;
    }
}
