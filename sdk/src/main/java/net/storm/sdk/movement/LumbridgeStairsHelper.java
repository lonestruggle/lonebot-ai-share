package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.TileObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lumbridge castle: altijd de zuid-trap (niet noord).
 */
public final class LumbridgeStairsHelper {

    private static final Logger log = LoggerFactory.getLogger(LumbridgeStairsHelper.class);

    public static final WorldPoint SOUTH_GROUND = new WorldPoint(3205, 3208, 0);
    public static final WorldPoint SOUTH_BANK = new WorldPoint(3205, 3208, 2);
    /**
     * Courtyard bij zuid-trap (capture 3207,3210) — <b>binnen</b> de muur, niet buiten
     * op 3208,3205 (dat liet A* + Climb/Bottom-floor ping-pongen).
     */
    public static final WorldPoint SOUTH_APPROACH = new WorldPoint(3207, 3210, 0);

    /** Alleen zuid — Y ≤ dit = zuid-trap zone. */
    private static final int SOUTH_Y_MAX = 3214;
    private static final int CASTLE_X_MIN = 3198;
    private static final int CASTLE_X_MAX = 3215;
    /**
     * Binnenplaats bij zuid-trap (Get walls 3205,3208→3210,3213 + hoek 3204,3212).
     * In zone op plane 0 = Climb-up mag.
     */
    private static final int COURT_X1 = 3204;
    private static final int COURT_X2 = 3210;
    private static final int COURT_Y1 = 3208;
    private static final int COURT_Y2 = 3213;
    private static final long STICKY_MS = 10_000L;
    private static final long APPROACH_STICKY_MS = 90_000L;

    private static volatile WorldPoint stickyStairsTile;
    private static volatile long stickyUntilMs;
    /** Zelfde bank-rit: één ±1 approach-tegel (geen pad-clear elke tick). */
    private static volatile WorldPoint stickyApproach;
    private static volatile long stickyApproachUntilMs;

    private LumbridgeStairsHelper() {
    }

    public static void clearSticky() {
        stickyStairsTile = null;
        stickyUntilMs = 0L;
        stickyApproach = null;
        stickyApproachUntilMs = 0L;
    }

    /**
     * A*-doel naar Lumb bank: courtyard {@link #SOUTH_APPROACH} ±1 (sticky per rit).
     */
    public static WorldPoint pathApproach() {
        long now = System.currentTimeMillis();
        if (stickyApproach != null && now < stickyApproachUntilMs
                && stickyApproach.getPlane() == 0
                && isInSouthCourtyard(stickyApproach)) {
            return stickyApproach;
        }
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        int x = SOUTH_APPROACH.getX() + r.nextInt(-1, 2);
        int y = SOUTH_APPROACH.getY() + r.nextInt(-1, 2);
        x = Math.max(COURT_X1, Math.min(COURT_X2, x));
        y = Math.max(COURT_Y1, Math.min(COURT_Y2, y));
        stickyApproach = new WorldPoint(x, y, 0);
        stickyApproachUntilMs = now + APPROACH_STICKY_MS;
        return stickyApproach;
    }

    /** Approach-tegel of courtyard-stand vóór Climb. */
    public static boolean isGroundApproachTile(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        if (isInSouthCourtyard(p)) {
            return true;
        }
        return p.distanceTo(SOUTH_APPROACH) <= 3
                || p.distanceTo(SOUTH_GROUND) <= 6
                || isSouthStairsTile(p);
    }

    /**
     * Bank-booth zone (niet de zuid-trap). Alleen dit mag als upper goal tijdens approach.
     */
    public static boolean isLumbridgeBankBoothDest(WorldPoint dest) {
        if (!isLumbridgeUpperDest(dest)) {
            return false;
        }
        return dest.getY() > SOUTH_Y_MAX;
    }

    public static boolean isInLumbridgeCastleArea(WorldPoint p) {
        return p != null
                && p.getX() >= 3195 && p.getX() <= 3220
                && p.getY() >= 3195 && p.getY() <= 3235;
    }

    /** Courtyard bij zuid-trap — in zone = Climb-up, niet vanaf de weg. */
    public static boolean isInSouthCourtyard(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        return x >= COURT_X1 && x <= COURT_X2 && y >= COURT_Y1 && y <= COURT_Y2;
    }

    /** Lumb bank / 1e-2e verdieping kasteel. */
    public static boolean isLumbridgeUpperDest(WorldPoint dest) {
        if (dest == null || dest.getPlane() < 1) {
            return false;
        }
        int x = dest.getX();
        int y = dest.getY();
        return x >= 3198 && x <= 3218 && y >= 3204 && y <= 3232;
    }

    /** In courtyard (p0) of op zuid-trap (elke floor) → Climb mag. */
    public static boolean canClimbNow(WorldPoint from) {
        if (from == null) {
            return false;
        }
        if (from.getPlane() >= 1) {
            return isSouthStairsTile(from) || isInLumbridgeCastleArea(from);
        }
        return isInSouthCourtyard(from)
                || from.distanceTo(SOUTH_GROUND) <= 5
                || from.distanceTo(SOUTH_APPROACH) <= 2;
    }

    /**
     * Doel is Lumb boven, speler nog op plane 0
     * → A* naar {@link #SOUTH_APPROACH} tot de floor wisselt (niet naar booth 3208,3220).
     */
    public static boolean needsGroundApproach(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null || from.getPlane() != 0) {
            return false;
        }
        return isLumbridgeUpperDest(dest);
    }

    /** A*-doel: zelfde plane, courtyard vóór de trap — niet de bank op plane 2. */
    public static WorldPoint pathTarget(WorldPoint from, WorldPoint dest) {
        if (dest == null) {
            return null;
        }
        if (from != null && from.getPlane() == 0 && isLumbridgeUpperDest(dest)) {
            return pathApproach();
        }
        return dest;
    }

    /**
     * Niet om de trap-tegel heen hoppen. Bij Climb: geen pad noord de courtyard in/uit.
     */
    public static WorldPoint capHop(WorldPoint from, WorldPoint dest, WorldPoint hop) {
        if (from == null || hop == null || from.getPlane() != 0 || hop.getPlane() != 0) {
            return hop;
        }
        if (!isLumbridgeUpperDest(dest)) {
            return hop;
        }
        if (canClimbNow(from)) {
            return from;
        }
        if (isSouthStairsTile(hop) || hop.distanceTo(SOUTH_GROUND) <= 2) {
            return pathApproach();
        }
        return hop;
    }

    public static boolean sameGroundApproach(WorldPoint prevDest, WorldPoint dest, WorldPoint from) {
        if (from == null || dest == null || prevDest == null) {
            return false;
        }
        if (from.getPlane() != 0 || !isLumbridgeUpperDest(dest)) {
            return false;
        }
        return prevDest.getPlane() == 0
                && (prevDest.distanceTo(SOUTH_APPROACH) <= 8
                || prevDest.distanceTo(SOUTH_GROUND) <= 6
                || isInSouthCourtyard(prevDest)
                || isSouthStairsTile(prevDest));
    }

    /** Alleen zuid-trap tegels. */
    public static boolean isSouthStairsTile(WorldPoint p) {
        if (p == null) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        return x >= CASTLE_X_MIN && x <= CASTLE_X_MAX && y >= 3204 && y <= SOUTH_Y_MAX;
    }

    /**
     * Vind zuid-trap in scene; anders null (caller loopt naar {@link #walkAnchor}).
     */
    public static ITileObject pickSouthStairs(WorldPoint me, boolean goDown) {
        if (me == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (stickyStairsTile != null && now < stickyUntilMs && isSouthStairsTile(stickyStairsTile)) {
            ITileObject sticky = findStairsAt(stickyStairsTile, goDown, me.getPlane());
            if (sticky != null) {
                return sticky;
            }
            clearSticky();
        }

        ITileObject south = TileObjects.getNearest(o -> matchesStairs(o, goDown, me.getPlane())
                && o.getWorldLocation() != null
                && isSouthStairsTile(o.getWorldLocation()));
        if (south != null && south.getWorldLocation() != null) {
            stickyStairsTile = south.getWorldLocation();
            stickyUntilMs = now + STICKY_MS;
            log.info("[LumbStairs] zuid @{} goDown={}", stickyStairsTile, goDown);
        }
        return south;
    }

    /** Walk-anker op huidige plane (altijd zuid). */
    public static WorldPoint walkAnchor(int plane) {
        int p = Math.max(0, Math.min(2, plane));
        if (p >= 2) {
            return SOUTH_BANK;
        }
        return new WorldPoint(SOUTH_GROUND.getX(), SOUTH_GROUND.getY(), p);
    }

    private static boolean matchesStairs(ITileObject o, boolean goDown, int plane) {
        if (o == null || o.getName() == null || o.getWorldLocation() == null) {
            return false;
        }
        if (o.getWorldLocation().getPlane() != plane) {
            return false;
        }
        String n = o.getName().toLowerCase();
        if (!(n.contains("stair") || n.contains("ladder"))) {
            return false;
        }
        if (goDown) {
            return o.hasAction("Bottom-floor") || o.hasAction("Climb-down") || o.hasAction("Climb Down")
                    || o.hasAction("Climb");
        }
        return o.hasAction("Top-floor") || o.hasAction("Climb-up") || o.hasAction("Climb Up")
                || o.hasAction("Climb");
    }

    private static ITileObject findStairsAt(WorldPoint tile, boolean goDown, int plane) {
        if (tile == null) {
            return null;
        }
        return TileObjects.getNearest(o -> o != null
                && o.getWorldLocation() != null
                && o.getWorldLocation().getX() == tile.getX()
                && o.getWorldLocation().getY() == tile.getY()
                && o.getWorldLocation().getPlane() == plane
                && matchesStairs(o, goDown, plane));
    }
}
