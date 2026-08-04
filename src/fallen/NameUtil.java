package fallen;

import arc.util.Strings;

/**
 * Stable nick normalization so freeze icons / status glyphs do not break history matching.
 */
public final class NameUtil {
    private NameUtil() {}

    /** Strip colors + freeze/status symbols commonly prepended by freeze plugins. */
    public static String normalize(String name) {
        if (name == null) return "";
        String s = Strings.stripColors(name);
        if (s == null) return "";
        // Common freeze / status markers near nick
        s = s.replace("❄", "")
            .replace("❄️", "")
            .replace("🧊", "")
            .replace("☃", "")
            .replace("⛄", "")
            .replace("✦", "")
            .replace("★", "")
            .replace("☆", "");
        // Leading non-letter symbols (emoji / punctuation prefixes)
        s = s.replaceAll("^[\\s\\p{So}\\p{Cn}\\p{P}]+", "");
        return s.trim();
    }

    public static boolean samePlayer(String a, String b) {
        if (a == null || b == null) return false;
        String na = normalize(a).toLowerCase();
        String nb = normalize(b).toLowerCase();
        if (na.isEmpty() || nb.isEmpty()) return false;
        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }
}
