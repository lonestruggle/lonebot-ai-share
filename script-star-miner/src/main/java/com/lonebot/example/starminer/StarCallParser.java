package com.lonebot.example.starminer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Star Miners / CC-style shooting-star calls into {@link StarCall}s.
 */
public final class StarCallParser {

    private static final Pattern WORLD = Pattern.compile(
            "(?i)\\b(?:w(?:orld)?\\s*[:#.-]?\\s*)(\\d{3,4})\\b");
    private static final Pattern TIER = Pattern.compile(
            "(?i)\\b(?:t(?:ier)?|size)\\s*[:#.-]?\\s*([1-9])\\b");
    private static final Pattern MINERS = Pattern.compile(
            "(?i)\\b(\\d{1,3})\\s*miners?\\b");
    private static final Pattern DEAD = Pattern.compile(
            "(?i)\\b(dead|poof(?:ed)?|gone|empty|depleted|despawned|ended)\\b");

    private StarCallParser() {
    }

    public static List<StarCall> parseMany(String blob, long nowMs, String source) {
        List<StarCall> out = new ArrayList<>();
        if (blob == null || blob.isBlank()) {
            return out;
        }
        String[] lines = blob.split("[\\r\\n]+");
        for (String line : lines) {
            StarCall c = parseOne(line, nowMs, source);
            if (c != null) {
                out.add(c);
            }
        }
        if (out.isEmpty()) {
            StarCall one = parseOne(blob.replace('\n', ' '), nowMs, source);
            if (one != null) {
                out.add(one);
            }
        }
        return out;
    }

    public static StarCall parseOne(String text, long nowMs, String source) {
        if (text == null) {
            return null;
        }
        String t = stripMarkdown(text).trim();
        if (t.length() < 4) {
            return null;
        }
        Matcher wm = WORLD.matcher(t);
        if (!wm.find()) {
            return null;
        }
        int world = Integer.parseInt(wm.group(1));
        if (world < 300 || world > 600) {
            return null;
        }
        int tier = 0;
        Matcher tm = TIER.matcher(t);
        if (tm.find()) {
            tier = Integer.parseInt(tm.group(1));
        }
        int miners = -1;
        Matcher mm = MINERS.matcher(t);
        if (mm.find()) {
            miners = Integer.parseInt(mm.group(1));
        }
        boolean dead = DEAD.matcher(t).find();
        String locBit = t
                .replaceAll("(?i)\\b(?:w(?:orld)?\\s*[:#.-]?\\s*)\\d{3,4}\\b", " ")
                .replaceAll("(?i)\\b(?:t(?:ier)?|size)\\s*[:#.-]?\\s*[1-9]\\b", " ")
                .replaceAll("(?i)\\b\\d{1,3}\\s*miners?\\b", " ")
                .replaceAll("(?i)\\b\\d{1,3}\\s*%\\b", " ")
                .replaceAll("(?i)\\b(dead|poof(?:ed)?|gone|empty|depleted)\\b", " ");
        StarLocations.Spot spot = StarLocations.match(locBit);
        if (spot == null) {
            spot = StarLocations.match(t);
        }
        if (spot == null && !dead) {
            return null;
        }
        String locRaw = locBit.replaceAll("\\s+", " ").trim();
        return new StarCall(world, tier, locRaw, spot, miners, nowMs, dead, source, t);
    }

    static String stripMarkdown(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("*", " ")
                .replace("_", " ")
                .replace("`", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("https?://\\S+", " ");
    }

    /** Pull string values from Discord REST JSON without a JSON library. */
    static List<String> extractDiscordTexts(String json) {
        List<String> texts = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return texts;
        }
        collectJsonStrings(json, "content", texts);
        collectJsonStrings(json, "description", texts);
        collectJsonStrings(json, "title", texts);
        collectJsonStrings(json, "value", texts);
        return texts;
    }

    private static void collectJsonStrings(String json, String key, List<String> out) {
        String needle = "\"" + key + "\"";
        int from = 0;
        while (from < json.length()) {
            int k = json.indexOf(needle, from);
            if (k < 0) {
                return;
            }
            int colon = json.indexOf(':', k + needle.length());
            if (colon < 0) {
                return;
            }
            int q = json.indexOf('"', colon + 1);
            if (q < 0) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            boolean esc = false;
            for (int i = q + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (esc) {
                    if (c == 'n') {
                        sb.append('\n');
                    } else if (c == 't') {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
                    esc = false;
                    continue;
                }
                if (c == '\\') {
                    esc = true;
                    continue;
                }
                if (c == '"') {
                    break;
                }
                sb.append(c);
            }
            String v = sb.toString().trim();
            if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) {
                out.add(v);
            }
            from = q + 1;
        }
    }
}
