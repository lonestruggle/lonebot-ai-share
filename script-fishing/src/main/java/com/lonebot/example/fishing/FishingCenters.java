package com.lonebot.example.fishing;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CombatBot fishing centers: Barbarian vs Draynor pool op fishing-level.
 */
final class FishingCenters {

    static final String DEFAULT_BLOB =
            "3104:3433:0:14:Barbarian village:1"
                    + "|3090:3230:0:14:Draynor village:1";

    static final class Center {
        final WorldPoint point;
        final int radius;
        final String name;
        final boolean active;

        Center(WorldPoint point, int radius, String name, boolean active) {
            this.point = point;
            this.radius = Math.max(1, radius);
            this.name = name != null ? name : "";
            this.active = active;
        }

        boolean contains(WorldPoint p) {
            if (p == null || point == null || p.getPlane() != point.getPlane()) {
                return false;
            }
            return Math.abs(p.getX() - point.getX()) <= radius
                    && Math.abs(p.getY() - point.getY()) <= radius;
        }

        boolean isBarbarian() {
            if (name != null && name.toLowerCase().contains("barbarian")) {
                return true;
            }
            return point != null && FishingConfig.isBarbarianFishingLocation(point.getX(), point.getY());
        }

        boolean isDraynor() {
            if (name != null && name.toLowerCase().contains("draynor")) {
                return true;
            }
            return point != null && FishingConfig.isDraynorFishingLocation(point.getX(), point.getY(), name);
        }
    }

    private FishingCenters() {
    }

    static List<Center> parseAll(String blob) {
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

    static List<Center> parseActive(String blob) {
        List<Center> out = new ArrayList<>();
        for (Center c : parseAll(blob)) {
            if (c.active) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * @param preferredName null/blank/AUTO = level-based ({@link #pickForTraining});
     *                      anders match op center-naam (exact, daarna substring) — WC-parity.
     */
    static Center pick(String blob, int fishingLevel, String preferredName, Center current) {
        if (preferredName != null && !preferredName.isBlank()
                && !"AUTO".equalsIgnoreCase(preferredName.trim())
                && !"null".equalsIgnoreCase(preferredName.trim())) {
            Center named = pickByName(blob, preferredName.trim());
            if (named == null) {
                named = pickByName(DEFAULT_BLOB, preferredName.trim());
            }
            if (named != null) {
                return named;
            }
        }
        return pickForTraining(blob, fishingLevel, current);
    }

    private static Center pickByName(String blob, String preferredName) {
        List<Center> active = parseActive(blob);
        if (active.isEmpty()) {
            return null;
        }
        String want = preferredName.toLowerCase(Locale.ROOT);
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
        return null;
    }

    /**
     * CombatBot {@code pickFishingCenterForTraining}: bij beide pools lvl &lt;20 → Draynor, anders Barbarian.
     * Sticky: huidige center blijft als die nog in de pool zit.
     */
    static Center pickForTraining(String blob, int fishingLevel, Center current) {
        List<Center> active = parseActive(blob);
        if (active.isEmpty()) {
            active = parseActive(DEFAULT_BLOB);
        }
        List<Center> barb = new ArrayList<>();
        List<Center> dray = new ArrayList<>();
        List<Center> other = new ArrayList<>();
        for (Center c : active) {
            if (c.isBarbarian()) {
                barb.add(c);
            } else if (c.isDraynor()) {
                dray.add(c);
            } else {
                other.add(c);
            }
        }
        List<Center> pool;
        if (!barb.isEmpty() && !dray.isEmpty()) {
            pool = fishingLevel < FishingConfig.MIN_FISHING_LEVEL_PREFER_BARBARIAN_OVER_DRAYNOR
                    ? dray : barb;
        } else if (!barb.isEmpty()) {
            pool = barb;
        } else if (!dray.isEmpty()) {
            pool = dray;
        } else {
            pool = active;
        }
        if (pool.isEmpty()) {
            return current;
        }
        if (current != null && current.point != null) {
            for (Center c : pool) {
                if (c.point != null
                        && c.point.getX() == current.point.getX()
                        && c.point.getY() == current.point.getY()
                        && c.point.getPlane() == current.point.getPlane()) {
                    return c;
                }
            }
        }
        return pool.get(0);
    }

    private static Center deserialize(String part) {
        if (part == null || part.isBlank() || part.startsWith("e:")) {
            return null;
        }
        String[] t = part.split(":");
        if (t.length < 6) {
            return null;
        }
        try {
            int x = Integer.parseInt(t[0].trim());
            int y = Integer.parseInt(t[1].trim());
            int z = Integer.parseInt(t[2].trim());
            int r = Integer.parseInt(t[3].trim());
            String name = t[4];
            boolean active = !"0".equals(t[5].trim());
            return new Center(new WorldPoint(x, y, z), r, name, active);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
