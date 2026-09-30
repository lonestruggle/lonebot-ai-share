package com.lonebot.example.clue;

import java.util.Locale;

/**
 * Small clue-text utilities (CombatBot {@code ClueScrollHelper} port).
 */
public final class ClueScrollHelper {

    private ClueScrollHelper() {
    }

    public static String strip(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '<') {
                int end = raw.indexOf('>', i);
                if (end > i) {
                    i = end;
                    continue;
                }
            }
            if (c != '\r') {
                sb.append(c);
            }
        }
        return sb.toString().replace('\u00A0', ' ').trim();
    }

    public static String normalize(String raw) {
        String s = strip(raw).toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9 ]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    public static boolean containsNorm(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return normalize(haystack).contains(normalize(needle));
    }

    public static boolean isBeginnerClueName(String name) {
        String n = strip(name).toLowerCase(Locale.ROOT);
        if (n.contains("geode") || n.contains("bottle") || n.contains("nest")) {
            return false;
        }
        return n.contains("clue scroll") && n.contains("beginner");
    }

    public static boolean isBeginnerCasketName(String name) {
        String n = strip(name).toLowerCase(Locale.ROOT);
        return n.contains("casket") && n.contains("beginner");
    }

    public static boolean isClueOrCasketName(String name) {
        String n = strip(name).toLowerCase(Locale.ROOT);
        if (n.contains("geode") || n.contains("bottle") || (n.contains("nest") && !n.contains("casket"))) {
            return false;
        }
        if (n.contains("beginner") && (n.contains("clue scroll") || n.contains("casket"))) {
            return true;
        }
        return n.equals("clue scroll") || n.equals("reward casket");
    }
}
