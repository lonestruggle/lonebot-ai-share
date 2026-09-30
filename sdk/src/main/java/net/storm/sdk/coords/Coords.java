package net.storm.sdk.coords;

import net.runelite.api.coords.WorldPoint;

/**
 * Coordinate helpers (Chebyshev / distance).
 */
public final class Coords {

    private Coords() {
    }

    /**
     * Chebyshev distance (OSRS tile distance): {@code max(|dx|,|dy|)}.
     * Plane mismatch → {@link Integer#MAX_VALUE}.
     */
    public static int chebyshev(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }
        if (a.getPlane() != b.getPlane()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    /**
     * RuneLite {@link WorldPoint#distanceTo} (also Chebyshev on same plane).
     */
    public static int distance(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }
        return a.distanceTo(b);
    }
}
