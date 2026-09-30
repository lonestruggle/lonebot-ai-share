package net.runelite.client.plugins.lonebot;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generieke area-centers (Combat / Mining / Fishing) — zelfde format als CombatBot
 * {@code CenterManager}: {@code X:Y:Z:R:Name:A} of edge {@code e:X1:Y1:X2:Y2:Z:Name:A},
 * meerdere entries met {@code |}.
 */
public final class AreaCenters {

    public static final int DEFAULT_RADIUS = 10;
    public static final int MAX_RADIUS = 120;

    public static final String DEFAULT_COMBAT =
            "3051:3492:0:15:Monastery monks:1";

    public static final String DEFAULT_MINING =
            "3285:3368:0:14:Varrock east mine:1"
                    + "|3175:3298:0:12:lumb south:1"
                    + "|3146:3148:0:10:draynor zuid:1"
                    + "|3297:3291:0:15:alkarid 2:1"
                    + "|3295:3310:0:4:alkarid 3:1";

    public static final String DEFAULT_FISHING =
            "3104:3433:0:14:Barbarian village:1"
                    + "|3090:3230:0:14:Draynor village:1";

    public enum Skill {
        COMBAT("Combat", "ff3232", "combatCenters", "combatCentersMenuEnabled", "showCombatAreaOverlay"),
        MINING("Mining", "c88c32", "miningCenters", "miningCentersMenuEnabled", "showMiningAreaOverlay"),
        FISHING("Fishing", "3278dc", "fishingCenters", "fishingCentersMenuEnabled", "showFishingAreaOverlay");

        public final String label;
        public final String hex;
        public final String configKey;
        public final String menuKey;
        public final String overlayKey;

        Skill(String label, String hex, String configKey, String menuKey, String overlayKey) {
            this.label = label;
            this.hex = hex;
            this.configKey = configKey;
            this.menuKey = menuKey;
            this.overlayKey = overlayKey;
        }

        public String defaultBlob() {
            switch (this) {
                case COMBAT:
                    return DEFAULT_COMBAT;
                case MINING:
                    return DEFAULT_MINING;
                case FISHING:
                    return DEFAULT_FISHING;
                default:
                    return "";
            }
        }
    }

    public static final class Center {
        public final WorldPoint point;
        public final int radius;
        public final String name;
        public final boolean active;
        public final Integer minX, minY, maxX, maxY;

        public Center(WorldPoint point, int radius, String name, boolean active,
                      Integer minX, Integer minY, Integer maxX, Integer maxY) {
            this.point = point;
            this.radius = Math.max(1, radius);
            this.name = name != null ? name : "";
            this.active = active;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        public boolean hasEdge() {
            return minX != null && minY != null && maxX != null && maxY != null;
        }

        public String serialize() {
            String safeName = name != null ? name.replace("|", "").replace(":", " ") : "";
            if (hasEdge()) {
                return "e:" + minX + ":" + minY + ":" + maxX + ":" + maxY
                        + ":" + point.getPlane() + ":" + safeName + ":" + (active ? "1" : "0");
            }
            return point.getX() + ":" + point.getY() + ":" + point.getPlane() + ":" + radius
                    + ":" + safeName + ":" + (active ? "1" : "0");
        }

        public boolean contains(WorldPoint p) {
            if (p == null || point == null || p.getPlane() != point.getPlane()) {
                return false;
            }
            if (hasEdge()) {
                return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY;
            }
            return Math.abs(p.getX() - point.getX()) <= radius
                    && Math.abs(p.getY() - point.getY()) <= radius;
        }

        public boolean containsMargin(WorldPoint p, int m) {
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

    private AreaCenters() {
    }

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

    public static String orDefault(String blob, Skill skill) {
        if (blob != null && !blob.isBlank()) {
            return blob;
        }
        return skill != null ? skill.defaultBlob() : "";
    }

    public static String addCenter(String blob, WorldPoint point, int radius) {
        return addCenter(blob, point, radius, autoName(point, "Area"), false);
    }

    public static String autoName(WorldPoint point, String prefix) {
        String p = prefix != null && !prefix.isBlank() ? prefix : "Area";
        if (point == null) {
            return p;
        }
        return p + " " + point.getX() + "," + point.getY();
    }

    public static String displayName(Center c) {
        if (c == null) {
            return "?";
        }
        if (c.name != null && !c.name.isBlank()) {
            return c.name.trim();
        }
        return c.point != null ? autoName(c.point, "Area") : "Area";
    }

    /** Vul lege namen in (oude centers zonder naam). */
    public static String ensureNames(String blob, String prefix) {
        List<Center> centers = parseAll(blob);
        if (centers.isEmpty()) {
            return blob != null ? blob : "";
        }
        boolean changed = false;
        String pfx = prefix != null && !prefix.isBlank() ? prefix : "Area";
        for (int i = 0; i < centers.size(); i++) {
            Center c = centers.get(i);
            if (c.name != null && !c.name.isBlank()) {
                continue;
            }
            String auto = autoName(c.point, pfx);
            if (c.hasEdge()) {
                centers.set(i, new Center(c.point, c.radius, auto, c.active,
                        c.minX, c.minY, c.maxX, c.maxY));
            } else {
                centers.set(i, new Center(c.point, c.radius, auto, c.active,
                        null, null, null, null));
            }
            changed = true;
        }
        return changed ? serialize(centers) : (blob != null ? blob : "");
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

    public static String renameAt(String blob, int index, String newName) {
        List<Center> centers = parseAll(blob);
        if (index < 0 || index >= centers.size()) {
            return blob != null ? blob : "";
        }
        Center c = centers.get(index);
        String safe = newName != null ? newName.replace("|", "").replace(":", " ").trim() : "";
        if (safe.isEmpty()) {
            safe = autoName(c.point, "Area");
        }
        if (c.hasEdge()) {
            centers.set(index, new Center(c.point, c.radius, safe, c.active, c.minX, c.minY, c.maxX, c.maxY));
        } else {
            centers.set(index, new Center(c.point, c.radius, safe, c.active, null, null, null, null));
        }
        return serialize(centers);
    }

    public static String addCenter(String blob, WorldPoint point, int radius, String name, boolean dedupeTile) {
        if (point == null) {
            return blob != null ? blob : "";
        }
        List<Center> centers = parseAll(blob);
        centers.removeIf(c -> c.point != null && c.point.equals(point) && !c.hasEdge());
        int r = Math.max(1, Math.min(MAX_RADIUS, radius <= 0 ? DEFAULT_RADIUS : radius));
        centers.add(new Center(point, r, name != null ? name : "", true, null, null, null, null));
        String out = serialize(centers);
        return dedupeTile ? dedupeByTile(out) : out;
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
            centers.set(idx, new Center(mid, r, nearest.name, nearest.active, minX, minY, maxX, maxY));
        } else {
            int r = Math.max(1, Math.min(MAX_RADIUS, nearest.radius + delta));
            centers.set(idx, new Center(nearest.point, r, nearest.name, nearest.active, null, null, null, null));
        }
        return serialize(centers);
    }

    public static boolean isNearbyForEdit(Center nearest, WorldPoint tile) {
        if (nearest == null || tile == null || nearest.point == null) {
            return false;
        }
        if (nearest.hasEdge()) {
            return nearest.containsMargin(tile, 3);
        }
        return nearest.point.distanceTo(tile) <= nearest.radius + 3;
    }

    /** Mining: één entry per tile (actief + grootste radius wint). */
    public static String dedupeByTile(String blob) {
        List<Center> list = parseAll(blob);
        Map<String, Center> byKey = new LinkedHashMap<>();
        for (Center c : list) {
            if (c == null || c.point == null) {
                continue;
            }
            String key = c.point.getX() + ":" + c.point.getY() + ":" + c.point.getPlane();
            Center prev = byKey.get(key);
            if (prev == null) {
                byKey.put(key, c);
                continue;
            }
            boolean prefer = (c.active && !prev.active)
                    || (c.active == prev.active && c.radius > prev.radius);
            if (prefer) {
                byKey.put(key, c);
            }
        }
        return serialize(new ArrayList<>(byKey.values()));
    }

    public static String[] presetNames(String blob, String... alwaysInclude) {
        List<String> names = new ArrayList<>();
        names.add("AUTO");
        for (Center c : parseActive(blob)) {
            if (c.name != null && !c.name.isBlank() && !names.contains(c.name)) {
                names.add(c.name);
            }
        }
        if (alwaysInclude != null) {
            for (String def : alwaysInclude) {
                if (def != null && !def.isBlank() && !names.contains(def)) {
                    names.add(def);
                }
            }
        }
        return names.toArray(new String[0]);
    }

    private static Center findNearestCenter(List<Center> centers, WorldPoint point) {
        if (centers == null || point == null) {
            return null;
        }
        Center best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Center c : centers) {
            if (c == null || c.point == null || c.point.getPlane() != point.getPlane()) {
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

    static Center deserialize(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] p = s.split(":");
        if (p.length < 4) {
            return null;
        }
        try {
            if ("e".equalsIgnoreCase(p[0]) || "edge".equalsIgnoreCase(p[0])) {
                int x1 = Integer.parseInt(p[1]);
                int y1 = Integer.parseInt(p[2]);
                int x2 = Integer.parseInt(p[3]);
                int y2 = Integer.parseInt(p[4]);
                int z = Integer.parseInt(p[5]);
                String name = "";
                boolean active = true;
                if (p.length >= 8) {
                    name = join(p, 6, p.length - 1);
                    active = !"0".equals(p[p.length - 1]);
                }
                int minX = Math.min(x1, x2);
                int maxX = Math.max(x1, x2);
                int minY = Math.min(y1, y2);
                int maxY = Math.max(y1, y2);
                WorldPoint mid = new WorldPoint((minX + maxX) / 2, (minY + maxY) / 2, z);
                int r = Math.max((maxX - minX + 1) / 2, (maxY - minY + 1) / 2);
                return new Center(mid, r, name, active, minX, minY, maxX, maxY);
            }
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            int r = Integer.parseInt(p[3]);
            String name = "";
            boolean active = true;
            if (p.length >= 6) {
                name = join(p, 4, p.length - 1);
                active = !"0".equals(p[p.length - 1]);
            } else if (p.length == 5) {
                if ("0".equals(p[4]) || "1".equals(p[4])) {
                    active = !"0".equals(p[4]);
                } else {
                    name = p[4];
                }
            }
            return new Center(new WorldPoint(x, y, z), r, name, active, null, null, null, null);
        } catch (Exception e) {
            return null;
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
