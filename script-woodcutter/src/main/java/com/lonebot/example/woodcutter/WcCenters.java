package com.lonebot.example.woodcutter;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WcCenters {

    enum LogMode { INHERIT, FIREMAKING, DROP, BANK }

    public static final int DEFAULT_RADIUS = 10;
    /** Cirkel-radius: 120 slikte de GE vanaf Edge yews. */
    public static final int MAX_RADIUS = 40;

    public static final String DEFAULT_BLOB =
            "3165:3400:0:16:Varrock west oaks:1"
                    + "|2991:3200:0:24:Rimmington oaks:1"
                    + "|3086:3232:0:14:Draynor willows:1"
                    + "|e:3147:3220:3185:3255:0:Draynor-Lumb yews:1:bank"
                    + "|3033:3250:0:20:Port Sarim west willows:1";

    public static final class Center {
        public final WorldPoint point;
        public final int radius;
        public final String name;
        public final boolean active;
        public final Integer minX, minY, maxX, maxY;
        public final LogMode mode;

        Center(WorldPoint point, int radius, String name, boolean active,
               Integer minX, Integer minY, Integer maxX, Integer maxY, LogMode mode) {
            this.point = point;
            this.radius = Math.max(1, Math.min(MAX_RADIUS, radius));
            this.name = name != null ? name : "";
            this.active = active;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.mode = mode != null ? mode : LogMode.INHERIT;
        }

        public boolean hasEdge() {
            return minX != null && minY != null && maxX != null && maxY != null;
        }

        /** Format: X:Y:Z:R:Name:A[:mode] of e:X1:Y1:X2:Y2:Z:Name:A[:mode] */
        public String serialize() {
            String safeName = name != null ? name.replace("|", "").replace(":", " ") : "";
            String modeTok = modeToken(mode);
            if (hasEdge()) {
                String base = "e:" + minX + ":" + minY + ":" + maxX + ":" + maxY
                        + ":" + point.getPlane() + ":" + safeName + ":" + (active ? "1" : "0");
                return modeTok.isEmpty() ? base : base + ":" + modeTok;
            }
            String base = point.getX() + ":" + point.getY() + ":" + point.getPlane() + ":" + radius
                    + ":" + safeName + ":" + (active ? "1" : "0");
            return modeTok.isEmpty() ? base : base + ":" + modeTok;
        }

        boolean contains(WorldPoint p) {
            if (p == null || point == null || p.getPlane() != point.getPlane()) {
                return false;
            }
            if (hasEdge()) {
                return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY;
            }
            return Math.abs(p.getX() - point.getX()) <= radius
                    && Math.abs(p.getY() - point.getY()) <= radius;
        }

        boolean containsMargin(WorldPoint p, int m) {
            if (p == null || point == null || p.getPlane() != point.getPlane()) {
                return false;
            }
            if (hasEdge()) {
                return p.getX() >= minX - m && p.getX() <= maxX + m
                        && p.getY() >= minY - m && p.getY() <= maxY + m;
            }
            int r = radius + Math.max(0, m);
            return Math.abs(p.getX() - point.getX()) <= r
                    && Math.abs(p.getY() - point.getY()) <= r;
        }
    }

    private static final int[][] FAKE = {{3086, 3244}, {3086, 3243}, {3085, 3243}, {3085, 3244}};

    private WcCenters() {
    }

    static boolean isFakeTree(WorldPoint p) {
        if (p == null) {
            return false;
        }
        for (int[] xy : FAKE) {
            if (p.getX() == xy[0] && p.getY() == xy[1]) {
                return true;
            }
        }
        return false;
    }

    /** Alle centers (ook inactive). */
    public static List<Center> parseAll(String blob) {
        List<Center> out = new ArrayList<>();
        if (blob == null || blob.isBlank()) {
            return out;
        }
        for (String part : blob.split("\\|")) {
            Center c = deserialize(part.trim());
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    public static List<Center> parseActive(String blob) {
        List<Center> out = new ArrayList<>();
        if (blob == null || blob.isBlank()) {
            blob = DEFAULT_BLOB;
        }
        for (Center c : parseAll(blob)) {
            if (c.active) {
                out.add(c);
            }
        }
        return out;
    }

    public static String serialize(List<Center> centers) {
        if (centers == null || centers.isEmpty()) {
            return "";
        }
        return centers.stream().map(Center::serialize).collect(Collectors.joining("|"));
    }

    public static String addCenter(String blob, WorldPoint point, int radius) {
        return addCenter(blob, point, radius, autoName(point));
    }

    public static String autoName(WorldPoint point) {
        if (point == null) {
            return "WC";
        }
        return "WC " + point.getX() + "," + point.getY();
    }

    /** Weergavenaam voor UI (lege naam → coords). */
    public static String displayName(Center c) {
        if (c == null) {
            return "?";
        }
        if (c.name != null && !c.name.isBlank()) {
            return c.name.trim();
        }
        if (c.point != null) {
            return autoName(c.point);
        }
        return "WC";
    }

    public static String displayLabel(Center c) {
        if (c == null || c.point == null) {
            return "?";
        }
        String n = displayName(c);
        if (c.hasEdge()) {
            return n + " [edge " + c.minX + "," + c.minY + "–" + c.maxX + "," + c.maxY + "]";
        }
        return n + " @" + c.point.getX() + "," + c.point.getY() + " r" + c.radius;
    }

    /** Hernoem center op index in {@link #parseAll}. */
    public static String renameAt(String blob, int index, String newName) {
        List<Center> centers = parseAll(blob);
        if (index < 0 || index >= centers.size()) {
            return blob != null ? blob : "";
        }
        Center c = centers.get(index);
        String safe = newName != null ? newName.replace("|", "").replace(":", " ").trim() : "";
        if (safe.isEmpty()) {
            safe = autoName(c.point);
        }
        if (c.hasEdge()) {
            centers.set(index, new Center(c.point, c.radius, safe, c.active,
                    c.minX, c.minY, c.maxX, c.maxY, c.mode));
        } else {
            centers.set(index, new Center(c.point, c.radius, safe, c.active,
                    null, null, null, null, c.mode));
        }
        return serialize(centers);
    }

    public static String addCenter(String blob, WorldPoint point, int radius, String name) {
        if (point == null) {
            return blob != null ? blob : "";
        }
        List<Center> centers = parseAll(blob);
        centers.removeIf(c -> c.point != null && c.point.equals(point) && !c.hasEdge());
        int r = Math.max(1, Math.min(MAX_RADIUS, radius <= 0 ? DEFAULT_RADIUS : radius));
        centers.add(new Center(point, r, name != null ? name : "", true, null, null, null, null, LogMode.INHERIT));
        return serialize(centers);
    }

    public static String removeNearest(String blob, WorldPoint point) {
        List<Center> centers = parseAll(blob);
        Center nearest = findNearestCenter(centers, point);
        if (nearest != null) {
            centers.remove(nearest);
        }
        return serialize(centers);
    }

    public static Center findNearest(String blob, WorldPoint point) {
        return findNearestCenter(parseAll(blob), point);
    }

    /**
     * Radius ± voor cirkels; voor edge-box: vergroot/verklein elke kant met {@code delta} tegels.
     */
    public static String adjustRadius(String blob, WorldPoint nearPoint, int delta) {
        List<Center> centers = parseAll(blob);
        Center nearest = findNearestCenter(centers, nearPoint);
        if (nearest == null) {
            return serialize(centers);
        }
        int idx = centers.indexOf(nearest);
        if (nearest.hasEdge()) {
            int minX = nearest.minX - delta;
            int maxX = nearest.maxX + delta;
            int minY = nearest.minY - delta;
            int maxY = nearest.maxY + delta;
            if (minX > maxX) {
                int mid = (minX + maxX) / 2;
                minX = mid;
                maxX = mid;
            }
            if (minY > maxY) {
                int mid = (minY + maxY) / 2;
                minY = mid;
                maxY = mid;
            }
            WorldPoint mid = new WorldPoint((minX + maxX) / 2, (minY + maxY) / 2, nearest.point.getPlane());
            int r = Math.max((maxX - minX + 1) / 2, (maxY - minY + 1) / 2);
            centers.set(idx, new Center(mid, r, nearest.name, nearest.active, minX, minY, maxX, maxY, nearest.mode));
        } else {
            int r = Math.max(1, Math.min(MAX_RADIUS, nearest.radius + delta));
            centers.set(idx, new Center(nearest.point, r, nearest.name, nearest.active,
                    null, null, null, null, nearest.mode));
        }
        return serialize(centers);
    }

    /** True als tile dicht genoeg bij nearest center is voor radius/verwijder-opties. */
    public static boolean isNearbyForEdit(Center nearest, WorldPoint tile) {
        if (nearest == null || tile == null || nearest.point == null) {
            return false;
        }
        if (nearest.hasEdge()) {
            return nearest.containsMargin(tile, 3);
        }
        return nearest.point.distanceTo(tile) <= nearest.radius + 3;
    }

    private static Center findNearestCenter(List<Center> centers, WorldPoint point) {
        if (centers == null || point == null) {
            return null;
        }
        Center best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Center c : centers) {
            if (c == null || c.point == null) {
                continue;
            }
            if (c.point.getPlane() != point.getPlane()) {
                continue;
            }
            int d = c.hasEdge() && c.contains(point) ? 0 : c.point.distanceTo(point);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    static Center pick(String blob, int wc) {
        return pick(blob, wc, null);
    }

    /**
     * @param preferredName null/blank/AUTO = level-based; anders match op center-naam (substring)
     */
    static Center pick(String blob, int wc, String preferredName) {
        return pick(blob, wc, preferredName, null);
    }

    /**
     * AUTO: blijf op het center waar de speler al in/bij staat (Edge yews),
     * niet de eerste naam met "yew" (Draynor-Lumb) of een GE-midden.
     */
    static Center pick(String blob, int wc, String preferredName, WorldPoint here) {
        List<Center> active = parseActive(blob);
        if (active.isEmpty()) {
            active = parseActive(DEFAULT_BLOB);
        }
        if (preferredName != null && !preferredName.isBlank()
                && !"AUTO".equalsIgnoreCase(preferredName.trim())
                && !"null".equalsIgnoreCase(preferredName.trim())) {
            String want = preferredName.trim().toLowerCase(Locale.ROOT);
            // Exacte naam eerst (hernoemde centers zoals "tree,oak,willow")
            for (Center c : active) {
                if (c.name != null && c.name.toLowerCase(Locale.ROOT).equals(want)) {
                    return c;
                }
            }
            for (Center c : active) {
                if (c.name != null && c.name.toLowerCase(Locale.ROOT).contains(want)) {
                    return c;
                }
            }
            for (Center c : parseActive(DEFAULT_BLOB)) {
                if (c.name != null && c.name.toLowerCase(Locale.ROOT).equals(want)) {
                    return c;
                }
            }
            for (Center c : parseActive(DEFAULT_BLOB)) {
                if (c.name != null && c.name.toLowerCase(Locale.ROOT).contains(want)) {
                    return c;
                }
            }
        }
        if (here != null) {
            Center in = null;
            int best = Integer.MAX_VALUE;
            for (Center c : active) {
                if (c == null || c.point == null || !eligibleForWcLevel(c, wc)) {
                    continue;
                }
                if (!c.contains(here) && !c.containsMargin(here, 20)) {
                    continue;
                }
                int d = c.point.distanceTo(here);
                if (in == null || d < best) {
                    in = c;
                    best = d;
                }
            }
            if (in != null) {
                return in;
            }
        }
        String treeKw = bestTreeKeywordForWc(wc).toLowerCase(Locale.ROOT);
        // Eerst: center waarvan de naam de target-boom noemt (oak / willow / yew)
        for (Center c : active) {
            if (!eligibleForWcLevel(c, wc)) {
                continue;
            }
            String n = c.name != null ? c.name.toLowerCase(Locale.ROOT) : "";
            if (n.contains(treeKw)) {
                return c;
            }
        }
        Center fallback = null;
        for (Center c : active) {
            if (!eligibleForWcLevel(c, wc)) {
                if (fallback == null && c != null) {
                    // Alleen willow als fallback als we al willow mogen (anders oaks)
                    String n = c.name != null ? c.name.toLowerCase(Locale.ROOT) : "";
                    if (wc >= 30 && (n.contains("willow") || n.contains("port sarim"))) {
                        fallback = c;
                    } else if (wc < 30 && n.contains("oak")) {
                        fallback = c;
                    }
                }
                continue;
            }
            return c;
        }
        if (fallback != null) {
            return fallback;
        }
        // Laatste redmiddel: eerste eligible, anders eerste actief
        for (Center c : active) {
            if (eligibleForWcLevel(c, wc)) {
                return c;
            }
        }
        return active.isEmpty() ? null : active.get(0);
    }

    private static String bestTreeKeywordForWc(int wc) {
        return WcTrees.bestTreeForLevel(wc);
    }

    private static boolean eligibleForWcLevel(Center c, int wc) {
        if (c == null || c.name == null) {
            return true;
        }
        String n = c.name.toLowerCase(Locale.ROOT);
        if ((n.contains("yew") || (c.hasEdge() && n.contains("lumb"))) && wc < 60) {
            return false;
        }
        if ((n.contains("willow") || n.contains("port sarim")) && wc < 30) {
            return false;
        }
        if (n.contains("oak") && wc < 15) {
            return false;
        }
        if ((n.contains("magic") || n.contains("redwood")) && wc < 75) {
            return false;
        }
        return true;
    }

    /** Vul lege namen in (oude centers zonder naam). */
    public static String ensureNames(String blob) {
        List<Center> centers = parseAll(blob);
        if (centers.isEmpty()) {
            return blob != null ? blob : "";
        }
        boolean changed = false;
        for (int i = 0; i < centers.size(); i++) {
            Center c = centers.get(i);
            if (c.name != null && !c.name.isBlank()) {
                continue;
            }
            String auto = autoName(c.point);
            if (c.hasEdge()) {
                centers.set(i, new Center(c.point, c.radius, auto, c.active,
                        c.minX, c.minY, c.maxX, c.maxY, c.mode));
            } else {
                centers.set(i, new Center(c.point, c.radius, auto, c.active,
                        null, null, null, null, c.mode));
            }
            changed = true;
        }
        return changed ? serialize(centers) : (blob != null ? blob : "");
    }

    /** AUTO + namen uit blob (lege namen → displayName met coords). */
    public static String[] presetNames() {
        return presetNames(DEFAULT_BLOB);
    }

    public static String[] presetNames(String blob) {
        List<String> names = new ArrayList<>();
        names.add("AUTO");
        String src = blob != null && !blob.isBlank() ? blob : DEFAULT_BLOB;
        for (Center c : parseActive(src)) {
            String n = displayName(c);
            if (!names.contains(n)) {
                names.add(n);
            }
        }
        for (String def : new String[]{"Varrock west oaks", "Draynor willows", "Port Sarim west willows", "Draynor-Lumb yews"}) {
            if (!names.contains(def)) {
                names.add(def);
            }
        }
        return names.toArray(new String[0]);
    }

    static Center deserialize(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] p = s.split(":");
        if (p.length < 4) {
            return null;
        }
        try {
            LogMode mode = LogMode.INHERIT;
            int last = p.length - 1;
            if (isMode(p[last])) {
                mode = parseMode(p[last]);
                last--;
            }
            if ("e".equalsIgnoreCase(p[0]) || "edge".equalsIgnoreCase(p[0])) {
                int x1 = Integer.parseInt(p[1]);
                int y1 = Integer.parseInt(p[2]);
                int x2 = Integer.parseInt(p[3]);
                int y2 = Integer.parseInt(p[4]);
                int z = Integer.parseInt(p[5]);
                String name = "";
                boolean active = true;
                if (last >= 7) {
                    name = join(p, 6, last);
                    active = !"0".equals(p[last]);
                }
                int minX = Math.min(x1, x2);
                int maxX = Math.max(x1, x2);
                int minY = Math.min(y1, y2);
                int maxY = Math.max(y1, y2);
                WorldPoint mid = new WorldPoint((minX + maxX) / 2, (minY + maxY) / 2, z);
                int r = Math.max((maxX - minX + 1) / 2, (maxY - minY + 1) / 2);
                return new Center(mid, r, name, active, minX, minY, maxX, maxY, mode);
            }
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            int r = Integer.parseInt(p[3]);
            String name = "";
            boolean active = true;
            if (last >= 5) {
                name = join(p, 4, last);
                active = !"0".equals(p[last]);
            }
            return new Center(new WorldPoint(x, y, z), r, name, active, null, null, null, null, mode);
        } catch (Exception e) {
            return null;
        }
    }

    private static String modeToken(LogMode mode) {
        if (mode == null || mode == LogMode.INHERIT) {
            return "";
        }
        switch (mode) {
            case FIREMAKING:
                return "fm";
            case DROP:
                return "drop";
            case BANK:
                return "bank";
            default:
                return "";
        }
    }

    private static boolean isMode(String t) {
        if (t == null) {
            return false;
        }
        String l = t.toLowerCase(Locale.ROOT);
        return l.equals("fm") || l.equals("drop") || l.equals("bank") || l.equals("firemaking");
    }

    private static LogMode parseMode(String t) {
        switch (t.toLowerCase(Locale.ROOT)) {
            case "fm":
            case "firemaking":
                return LogMode.FIREMAKING;
            case "drop":
                return LogMode.DROP;
            case "bank":
                return LogMode.BANK;
            default:
                return LogMode.INHERIT;
        }
    }

    private static String join(String[] p, int from, int toExcl) {
        StringBuilder sb = new StringBuilder(p[from]);
        for (int i = from + 1; i < toExcl && i < p.length; i++) {
            sb.append(':').append(p[i]);
        }
        return sb.toString();
    }
}
