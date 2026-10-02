package com.zynelabs.dzplay.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automatic category engine.
 *
 * Giant playlists ship hundreds of messy group-titles
 * ("UK: SKY CINEMA...", "UK MOVIES: SKY CINEMA...", "UK-Sky Cinema Family"…).
 * This engine:
 *  1. normalizes group names (trim, collapse spaces, case-insensitive dedupe),
 *  2. buckets every channel into a smart top-level category by keyword rules
 *     matched against "group + channel name".
 */
public class Cats {

    public static final String SPORTS = "\u26BD Sports";
    public static final String MOVIES = "\uD83C\uDFAC Movies";
    public static final String NEWS = "\uD83D\uDCF0 News";
    public static final String KIDS = "\uD83E\uDDD2 Kids";
    public static final String MUSIC = "\uD83C\uDFB5 Music";
    public static final String DOCS = "\uD83D\uDD2C Documentary";
    public static final String ENTERTAIN = "\uD83C\uDFAD Entertainment";
    public static final String RADIO = "\uD83D\uDCFB Radio";
    public static final String ADULT = "\uD83D\uDD1E Adult";
    public static final String OTHER = "\uD83D\uDCE6 Other";

    /** Display order of the smart categories. */
    public static final String[] ORDER = {
            SPORTS, MOVIES, NEWS, KIDS, MUSIC, DOCS, ENTERTAIN, RADIO, ADULT, OTHER
    };

    /** First match wins. Keywords are matched as whole tokens. */
    private static final String[][] RULES = {
            {ADULT, "xxx porn adult adults playboy xhamster redtube 18+ erotic sexy babes brazzers naughty anal hentai tits boobs booty milf lesbian lesbians orgy cum pussy nude naked fetish bdsm escort penthouse hustler vivid wicked private dorcel passionhd bangbros realitykings mofos twistys babescom girlsway sweetheartvideo adulttime"},
            {SPORTS, "sports sport espn bein football soccer nba nfl cricket tennis golf racing wwe ufc mma boxing snooker darts olympics laliga bundesliga serie ligue champions europa league cup f1 motogp nascar indycar pga rugby nrl afl esports dazn skysports foxsports nbcsports cbssports supersport eurosport fifa fight bellator"},
            {MOVIES, "movies movie cinema film films hbo cinemax netflix showtime starz blockbuster premiere paramount peacock hulu tcm film4"},
            {NEWS, "news cnn bbc aljazeera jazeera bloomberg cnbc france24 dwnews cna headlines weather 24h breaking"},
            {KIDS, "kids kid children childrens cartoon cartoons disney junior juniors jr nickjr nicktoons cartoonito boomerang pbskids babytv ducktv minimini pogo toonami"},
            {RADIO, "radio radios fm dab"},
            {MUSIC, "music mtv vh1 trace hits party retro dance club vevo stingray"},
            {DOCS, "documentary documentaries docu discovery natgeo geographic history science animalplanet nature wild planet"},
            {ENTERTAIN, "entertainment comedy drama dramas reality variety show shows series sitcom soap soaps opera talk gameshow telenovela shopping general family lifestyle premium platinum vip classic classics"},
    };

    /** "a-b_c" -> "a b c", lowercased, padded with spaces for token matching. */
    private static String tokens(String s) {
        if (s == null) return " ";
        String t = s.toLowerCase(Locale.US).replaceAll("[^a-z0-9+]+", " ");
        return " " + t + " ";
    }

    /** Smart top-level category for a channel. Never null. */
    public static String of(Channel c) {
        String hay = tokens(c.group + " " + c.name);
        for (String[] rule : RULES) {
            String[] kws = rule[1].split(" ");
            for (String kw : kws) {
                if (kw.isEmpty()) continue;
                if (hay.contains(" " + kw + " ") || hay.contains(" " + kw + "+ ")) return rule[0];
            }
        }
        return OTHER;
    }

    /**
     * Tag every channel with its smart category. Call ONCE off the UI thread
     * (e.g. right after parsing) — afterwards getSmartCat() is a cheap field
     * read and all UI filtering stays fast.
     */
    public static void tagAll(List<Channel> channels) {
        for (Channel c : channels) {
            c.smartCat = of(c);
            c.country = countryOf(c);
        }
    }

    // ---------------- countries ----------------

    /** ISO code, display name, keywords. Order matters: specific before generic. */
    private static final String[][] COUNTRIES = {
            {"GB", "UK", "uk england britain british london"},
            {"US", "USA", "us usa america american"},
            {"CA", "Canada", "canada canadian"},
            {"AU", "Australia", "australia aussie"},
            {"IE", "Ireland", "ireland irish"},
            {"FR", "France", "france french"},
            {"DE", "Germany", "germany german deutschland deutsche"},
            {"ES", "Spain", "spain spanish espana"},
            {"IT", "Italy", "italy italian"},
            {"PT", "Portugal", "portugal portuguese"},
            {"NL", "Netherlands", "netherlands dutch holland"},
            {"GR", "Greece", "greece greek"},
            {"TR", "Turkey", "turkey turkish"},
            {"SA", "Arabia", "arab arabic saudi emirates dubai qatar kuwait bahrain oman"},
            {"IN", "India", "india indian hindi"},
            {"PK", "Pakistan", "pakistan pakistani urdu"},
            {"BD", "Bangladesh", "bangladesh bangla bengali"},
            {"MM", "Myanmar", "myanmar burma burmese"},
            {"TH", "Thailand", "thailand thai"},
            {"MY", "Malaysia", "malaysia malay"},
            {"SG", "Singapore", "singapore"},
            {"ID", "Indonesia", "indonesia"},
            {"PH", "Philippines", "philippines filipino pinoy"},
            {"VN", "Vietnam", "vietnam vietnamese"},
            {"CN", "China", "china chinese mandarin cantonese"},
            {"JP", "Japan", "japan japanese"},
            {"KR", "Korea", "korea korean"},
            {"RU", "Russia", "russia russian"},
            {"UA", "Ukraine", "ukraine ukrainian"},
            {"PL", "Poland", "poland polish"},
            {"BR", "Brazil", "brazil brazilian"},
            {"MX", "Mexico", "mexico mexican"},
            {"AR", "Argentina", "argentina"},
            {"ZA", "Africa", "africa african nigeria ghana kenya"},
            {"NZ", "New Zealand", "zealand"},
            {"BY", "Belarus", "belarus"},
            {"HK", "Hong Kong", "hong kong"},
            {"TW", "Taiwan", "taiwan"},
            {"AE", "UAE", "uae emirates dubai"},
    };

    /** 3-letter code -> ISO, for "USA - ESPN" style group-title prefixes. */
    private static final String[][] ISO_ALIAS = {
            {"GB", "UK"}, {"US", "USA"}, {"IN", "IND"}, {"PK", "PAK"}, {"BD", "BAN"},
            {"DE", "GER"}, {"FR", "FRA"}, {"ES", "ESP"}, {"IT", "ITA"}, {"PT", "POR"},
            {"RU", "RUS"}, {"UA", "UKR"}, {"KR", "KOR"}, {"JP", "JPN"}, {"CN", "CHN"},
            {"AU", "AUS"}, {"CA", "CAN"}, {"BR", "BRA"}, {"MX", "MEX"}, {"AR", "ARG"},
            {"SA", "ARA"}, {"TR", "TUR"}, {"GR", "GRE"}, {"NL", "NED"}, {"IE", "IRL"},
            {"NZ", "NZL"}, {"ZA", "RSA"}, {"MM", "MYA"}, {"TH", "THA"}, {"MY", "MYS"},
            {"SG", "SGP"}, {"ID", "IDN"}, {"PH", "PHL"}, {"VN", "VIE"}, {"PL", "POL"},
    };

    public static final String COUNTRY_OTHER = "\uD83C\uDF10 Other";

    /** Regional-indicator flag emoji for an ISO code, e.g. "GB" -> flag. */
    public static String flag(String iso) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < iso.length(); i++) {
            char ch = iso.charAt(i);
            if (ch >= 'A' && ch <= 'Z') sb.appendCodePoint(0x1F1E6 + (ch - 'A'));
        }
        return sb.toString();
    }

    /** First alphabetic token of a string, e.g. "US - ESPN" -> "US". */
    private static String firstToken(String s) {
        if (s == null) return "";
        int i = 0;
        while (i < s.length() && !Character.isLetter(s.charAt(i))) i++;
        StringBuilder b = new StringBuilder();
        while (i < s.length() && Character.isLetter(s.charAt(i))) b.append(s.charAt(i++));
        return b.toString();
    }

    /** Match a leading country code: "US - ESPN", "UK|Sky", "[IN] Star", "USA Movies". */
    private static String prefixCountry(String s) {
        String t = firstToken(s);
        if (t.length() < 2 || t.length() > 3) return null;
        // 2-letter codes must be ALL CAPS ("US", not "It"/"In" as English words)
        if (t.length() == 2 && !t.equals(t.toUpperCase(Locale.US))) return null;
        String tu = t.toUpperCase(Locale.US);
        String tl = " " + t.toLowerCase(Locale.US) + " ";
        for (String[] co : COUNTRIES) {
            if (tu.equals(co[0])) return flag(co[0]) + " " + co[1];
            if ((" " + co[2] + " ").contains(tl) && t.length() == 3)
                return flag(co[0]) + " " + co[1];
        }
        for (String[] a : ISO_ALIAS) {
            if (tu.equals(a[1])) {
                for (String[] co : COUNTRIES)
                    if (co[0].equals(a[0])) return flag(co[0]) + " " + co[1];
            }
        }
        return null;
    }

    /** Country label for a channel, e.g. "<flag> UK". Empty string if unknown. */
    public static String countryOf(Channel c) {
        // Non-Latin scripts are a stronger signal than any Latin prefix: a provider
        // brand code like "MM" on Cyrillic channel names must not win over the
        // script itself (those are Russian channels, not Myanmar).
        String hit = scriptCountry(c.group + " " + c.name);
        if (hit != null) return hit;
        hit = prefixCountry(c.group);
        if (hit != null) return hit;
        hit = prefixCountry(c.name);
        if (hit != null) return hit;
        String hay = tokens(c.group + " " + c.name);
        for (String[] co : COUNTRIES) {
            String[] kws = co[2].split(" ");
            for (String kw : kws) {
                if (kw.isEmpty()) continue;
                if (hay.contains(" " + kw + " ")) return flag(co[0]) + " " + co[1];
            }
        }
        return "";
    }

    /** Non-Latin script -> country guess (Cyrillic text is almost always Russian).
     *  Runs before prefix/keyword matching. Null when the text is Latin-only. */
    private static String scriptCountry(String s) {
        if (s == null) return null;
        boolean cyrillic = false, thai = false, myanmar = false, hangul = false;
        boolean kana = false, cjk = false, arabic = false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            switch (Character.UnicodeScript.of(cp)) {
                case CYRILLIC: cyrillic = true; break;
                case THAI: thai = true; break;
                case MYANMAR: myanmar = true; break;
                case HANGUL: hangul = true; break;
                case HIRAGANA:
                case KATAKANA: kana = true; break;
                case HAN: cjk = true; break;
                case ARABIC: arabic = true; break;
                default: break;
            }
        }
        String lower = s.toLowerCase(Locale.US);
        if (cyrillic) {
            if (lower.contains("україн") || lower.contains("ukrain")) return flag("UA") + " Ukraine";
            if (lower.contains("беларус") || lower.contains("belarus")) return flag("BY") + " Belarus";
            return flag("RU") + " Russia";
        }
        if (thai) return flag("TH") + " Thailand";
        if (myanmar) return flag("MM") + " Myanmar";
        if (kana) return flag("JP") + " Japan";
        if (hangul) return flag("KR") + " Korea";
        if (arabic) {
            if (lower.contains("emirates") || lower.contains("dubai")) return flag("AE") + " UAE";
            return flag("SA") + " Saudi Arabia";
        }
        if (cjk) {
            if (lower.contains("hong kong") || lower.contains("香港")) return flag("HK") + " Hong Kong";
            if (lower.contains("taiwan") || lower.contains("台灣") || lower.contains("台湾"))
                return flag("TW") + " Taiwan";
            return flag("CN") + " China";
        }
        return null;
    }

    /** Country label never empty: unknown -> COUNTRY_OTHER. */
    public static String countryKey(Channel c) {
        String co = c.getCountry();
        return co.isEmpty() ? COUNTRY_OTHER : co;
    }

    /** Count channels per country label (single pass, uses cached field). */
    public static Map<String, Integer> countryCounts(List<Channel> channels) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Channel c : channels) {
            String co = countryKey(c);
            Integer n = m.get(co);
            m.put(co, n == null ? 1 : n + 1);
        }
        return m;
    }

    /** Normalize a raw group-title for display + dedupe. */
    public static String normGroup(String g) {
        if (g == null) return "";
        return g.trim().replaceAll("\\s+", " ");
    }

    /**
     * Dedupe key for a normalized group: merges variants like
     * "UK | SPORTS", "Sports " and "SPORTS-HD" into one sub-group by
     * dropping leading country-code tokens and trailing quality tags.
     */
    public static String groupKey(String g) {
        String t = normGroup(g).toLowerCase(Locale.US);
        String[] parts = t.split("[^a-z0-9]+");
        List<String> keep = new ArrayList<>();
        for (String p : parts) if (!p.isEmpty()) keep.add(p);
        while (!keep.isEmpty() && isCountryToken(keep.get(0))) keep.remove(0);
        while (!keep.isEmpty() && isQualityToken(keep.get(keep.size() - 1)))
            keep.remove(keep.size() - 1);
        if (keep.isEmpty()) return t;
        StringBuilder sb = new StringBuilder();
        for (String p : keep) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
        }
        return sb.toString();
    }

    /** 2-letter ISO code / display code or 3-letter alias, e.g. "uk", "usa". */
    private static boolean isCountryToken(String t) {
        if (t.length() == 2) {
            for (String[] co : COUNTRIES) {
                if (t.equals(co[0].toLowerCase(Locale.US))) return true;
                if (t.equals(co[1].toLowerCase(Locale.US))) return true; // "uk"
            }
        } else if (t.length() == 3) {
            for (String[] a : ISO_ALIAS) if (t.equals(a[1].toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    private static boolean isQualityToken(String t) {
        return "hd".equals(t) || "fhd".equals(t) || "uhd".equals(t)
                || "4k".equals(t) || "8k".equals(t) || "sd".equals(t);
    }

    /**
     * Ordered smart categories present in the list, each mapped to its
     * normalized sub-groups (in first-seen order). Channels are scanned once.
     */
    public static Map<String, List<String>> index(List<Channel> channels) {
        Map<String, List<String>> cats = new LinkedHashMap<>();
        Map<String, String> seenGroups = new LinkedHashMap<>(); // groupKey -> display
        for (Channel c : channels) {
            String cat = c.getSmartCat();
            List<String> subs = cats.get(cat);
            if (subs == null) { subs = new ArrayList<>(); cats.put(cat, subs); }
            String gk = groupKey(c.displayGroup());
            if (!seenGroups.containsKey(gk)) {
                String disp = normGroup(c.displayGroup());
                if (disp.isEmpty()) disp = "Ungrouped";
                seenGroups.put(gk, disp);
            }
            String disp = seenGroups.get(gk);
            if (!subs.contains(disp)) subs.add(disp);
        }
        // order categories by ORDER, keep any extras at the end
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        for (String o : ORDER) if (cats.containsKey(o)) ordered.put(o, cats.get(o));
        for (Map.Entry<String, List<String>> e : cats.entrySet())
            if (!ordered.containsKey(e.getKey())) ordered.put(e.getKey(), e.getValue());
        return ordered;
    }

    /** Count channels per smart category (single pass). */
    public static Map<String, Integer> counts(List<Channel> channels) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Channel c : channels) {
            String cat = c.getSmartCat();
            Integer n = m.get(cat);
            m.put(cat, n == null ? 1 : n + 1);
        }
        return m;
    }
}
