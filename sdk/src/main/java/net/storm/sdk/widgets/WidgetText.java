package net.storm.sdk.widgets;

/**
 * Shared widget-text cleanup (Jagex {@code <col>} / {@code <br>} tags).
 */
public final class WidgetText {

    private WidgetText() {
    }

    public static String strip(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("<[^>]*>", " ").replace('\u00a0', ' ');
        s = s.replaceAll("\\s+", " ").trim();
        int col = s.indexOf(':');
        if (col >= 0 && col < s.length() - 1 && col < 24) {
            // "Item: Bronze axe" → "Bronze axe"
            String after = s.substring(col + 1).trim();
            if (!after.isEmpty()) {
                s = after;
            }
        }
        return s;
    }

    public static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return strip(haystack).toLowerCase().contains(strip(needle).toLowerCase());
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        return strip(a).equalsIgnoreCase(strip(b));
    }
}
