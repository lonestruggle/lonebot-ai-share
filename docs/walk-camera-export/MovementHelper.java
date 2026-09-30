package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.tiles.NoWalkZones;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.pathfinder.GlobalPathfinder;
import net.storm.sdk.movement.pathfinder.Pathfinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * High-level walk helper: full global path first, then step along it.
 * Lumbridge castle / dining-room avoidance via path avoid + bypass waypoints.
 */
public final class MovementHelper {

    private static final Logger log = LoggerFactory.getLogger(MovementHelper.class);

    private static final int LB_CASTLE_X_MIN = 3203;
    private static final int LB_CASTLE_X_MAX = 3213;
    private static final int LB_CASTLE_Y_MIN = 3207;
    private static final int LB_CASTLE_Y_MAX = 3230;

    private static final int LB_DINING_X_MIN = 3205;
    private static final int LB_DINING_X_MAX = 3212;
    private static final int LB_DINING_Y_MIN = 3218;
    private static final int LB_DINING_Y_MAX = 3226;

    private static final WorldPoint LB_WAYPOINT_SOUTH = new WorldPoint(3208, 3200, 0);
    private static final WorldPoint LB_WAYPOINT_EAST = new WorldPoint(3218, 3210, 0);
    private static final WorldPoint LB_WAYPOINT_WEST = new WorldPoint(3198, 3210, 0);
    private static final WorldPoint LB_WAYPOINT_NORTH = new WorldPoint(3208, 3237, 0);

    public static final WorldPoint LUMBRIDGE_STAIRS_TILE = new WorldPoint(3206, 3208, 0);
    public static final WorldPoint LUMBRIDGE_BANK_FLOOR_STAIRS_TILE = new WorldPoint(3205, 3208, 2);

    private static volatile TilePath activePath;
    private static volatile WorldPoint activeDest;

    static {
        TilePathWalker.init();
    }

    private MovementHelper() {
    }

    public static boolean isInsideLumbridgeCastle(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        return p.getX() >= LB_CASTLE_X_MIN && p.getX() <= LB_CASTLE_X_MAX
                && p.getY() >= LB_CASTLE_Y_MIN && p.getY() <= LB_CASTLE_Y_MAX;
    }

    public static boolean isInsideLumbridgeDiningRoom(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        return p.getX() >= LB_DINING_X_MIN && p.getX() <= LB_DINING_X_MAX
                && p.getY() >= LB_DINING_Y_MIN && p.getY() <= LB_DINING_Y_MAX;
    }

    public static boolean isInLumbridgeBlockedZone(WorldPoint p) {
        return isInsideLumbridgeCastle(p) || isInsideLumbridgeDiningRoom(p)
                || NoWalkZones.isLumbridgePotatoField(p);
    }

    public static boolean isInLumbridgePotatoField(WorldPoint p) {
        return NoWalkZones.isLumbridgePotatoField(p);
    }

    public static void clearPath() {
        activePath = null;
        activeDest = null;
    }

    public static TilePath getActivePath() {
        return activePath;
    }

    /** Build (or reuse) the full tile path to {@code dest}. */
    public static TilePath getPath(WorldPoint dest) {
        if (dest == null) {
            return TilePath.empty();
        }
        WorldPoint from = localPos();
        if (from == null) {
            return TilePath.empty();
        }

        WorldPoint target = dest;
        if (isInsideLumbridgeDiningRoom(dest)) {
            target = LB_WAYPOINT_SOUTH;
        }

        TilePath cached = activePath;
        WorldPoint cachedDest = activeDest;
        if (cached != null && !cached.isEmpty() && cachedDest != null
                && cachedDest.distanceTo(target) <= 2
                && cachedDest.getPlane() == target.getPlane()) {
            TilePath rem = cached.getRemainingPath(from);
            if (!rem.isEmpty() && rem.get(0).distanceTo(from) <= 12) {
                return cached;
            }
        }

        if (shouldBypassCastle(from, target)) {
            WorldPoint waypoint = getLumbridgeBypassWaypoint(from, target);
            if (waypoint != null && from.distanceTo(waypoint) > 3) {
                log.info("[MovementHelper] path via Lumbridge bypass {}", waypoint);
                TilePath via = GlobalPathfinder.findPath(from, waypoint, MovementHelper::preferAvoidBlocked);
                if (!via.isEmpty() && !via.isIncomplete()) {
                    TilePath rest = GlobalPathfinder.findPath(waypoint, target, MovementHelper::preferAvoidBlocked);
                    if (!rest.isEmpty()) {
                        ArrayList<WorldPoint> merged = new ArrayList<>(via);
                        for (int i = 1; i < rest.size(); i++) {
                            merged.add(rest.get(i));
                        }
                        TilePath full = new TilePath(merged, target.toWorldArea(), merged.size(), false);
                        activePath = full;
                        activeDest = target;
                        log.info("[MovementHelper] full path dotted: {} tiles → {}", full.size(), target);
                        return full;
                    }
                }
            }
        }

        TilePath path = GlobalPathfinder.findPath(from, target, MovementHelper::preferAvoidBlocked);
        activePath = path;
        activeDest = target;
        if (!path.isEmpty()) {
            log.info("[MovementHelper] full path dotted: {} tiles → {}", path.size(), target);
        } else {
            log.warn("[MovementHelper] no global path {} → {}", from, target);
        }
        return path;
    }

    /** Walk to destination: compute full path once, then step along it each call. */
    public static boolean walkTo(WorldPoint dest) {
        return walkTo(dest, false);
    }

    public static boolean walkTo(WorldPoint dest, boolean forceDining) {
        if (dest == null) {
            return false;
        }
        WorldPoint from = localPos();
        if (from == null) {
            return Pathfinder.walkTo(dest);
        }
        if (from.distanceTo(dest) <= 0 && from.getPlane() == dest.getPlane()) {
            clearPath();
            return true;
        }

        // Grot / verdieping: eerst plane matchen — anders GlobalPathfinder = leeg
        if (from.getPlane() != dest.getPlane()) {
            clearPath();
            if (PlaneChangeHelper.progressTowardPlane(dest)) {
                return true;
            }
            log.warn("[MovementHelper] vast op plane {} (doel plane {}) — geen climb in scene",
                    from.getPlane(), dest.getPlane());
            return false;
        }

        WorldPoint target = dest;
        if (!forceDining && isInsideLumbridgeDiningRoom(dest)) {
            target = LB_WAYPOINT_SOUTH;
        }

        TilePath path = getPath(target);
        if (path != null && !path.isEmpty()) {
            return path.walk();
        }

        log.debug("[MovementHelper] fallback scene Pathfinder → {}", target);
        return Pathfinder.walkTo(target, MovementHelper::preferAvoidBlocked);
    }

    private static boolean preferAvoidBlocked(WorldPoint p) {
        return isInLumbridgeBlockedZone(p) || ExcludedTiles.isExcluded(p) || NoWalkZones.isBlocked(p);
    }

    private static WorldPoint localPos() {
        Players.LocalSnap snap = Players.snapshotLocal();
        return snap != null && snap.present ? snap.worldLocation : null;
    }

    private static boolean isInLumbridgeRegion(WorldPoint p) {
        if (p == null || p.getPlane() > 2) {
            return false;
        }
        return p.getX() >= 3180 && p.getX() <= 3240
                && p.getY() >= 3180 && p.getY() <= 3250;
    }

    private static boolean isHeadingNorthOfLumbridge(WorldPoint to) {
        return to != null && to.getPlane() == 0 && to.getY() > LB_CASTLE_Y_MAX + 12;
    }

    static WorldPoint getLumbridgeBypassWaypoint(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return LB_WAYPOINT_SOUTH;
        }
        if (isHeadingNorthOfLumbridge(to) && from.getY() >= LB_CASTLE_Y_MAX) {
            return LB_WAYPOINT_NORTH;
        }
        boolean toWest = to.getX() < LB_CASTLE_X_MIN;
        boolean toEast = to.getX() > LB_CASTLE_X_MAX;
        boolean fromSouth = from.getY() < LB_CASTLE_Y_MIN;
        boolean fromNorth = from.getY() > LB_CASTLE_Y_MAX;
        boolean fromEast = from.getX() > LB_CASTLE_X_MAX;
        boolean fromWest = from.getX() < LB_CASTLE_X_MIN;

        if (fromEast && toWest) {
            return LB_WAYPOINT_SOUTH;
        }
        if (fromWest && toEast) {
            return LB_WAYPOINT_SOUTH;
        }
        if (fromSouth && toWest) {
            return LB_WAYPOINT_WEST;
        }
        if (fromSouth && toEast) {
            return LB_WAYPOINT_EAST;
        }
        if (isHeadingNorthOfLumbridge(to) && fromEast) {
            return LB_WAYPOINT_NORTH;
        }
        if (fromSouth) {
            return LB_WAYPOINT_SOUTH;
        }
        if (fromNorth) {
            return LB_WAYPOINT_NORTH;
        }
        if (fromEast) {
            return LB_WAYPOINT_EAST;
        }
        if (fromWest) {
            return LB_WAYPOINT_WEST;
        }
        return LB_WAYPOINT_SOUTH;
    }

    static boolean shouldBypassCastle(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return false;
        }
        if (from.getPlane() != 0 || to.getPlane() != 0) {
            return false;
        }
        if (!isInLumbridgeRegion(from) && !isInLumbridgeRegion(to)) {
            return false;
        }
        if (isInsideLumbridgeCastle(from) || isInsideLumbridgeDiningRoom(from)) {
            return false;
        }
        if (isInsideLumbridgeCastle(to)) {
            return false;
        }
        if (isInsideLumbridgeDiningRoom(to)) {
            return true;
        }
        if (isHeadingNorthOfLumbridge(to) && from.getX() > LB_CASTLE_X_MAX) {
            return false;
        }

        boolean fromWest = from.getX() < LB_CASTLE_X_MIN;
        boolean fromEast = from.getX() > LB_CASTLE_X_MAX;
        boolean toWest = to.getX() < LB_CASTLE_X_MIN;
        boolean toEast = to.getX() > LB_CASTLE_X_MAX;
        boolean fromSouth = from.getY() < LB_CASTLE_Y_MIN;
        boolean fromNorth = from.getY() > LB_CASTLE_Y_MAX;
        boolean toSouth = to.getY() < LB_CASTLE_Y_MIN;
        boolean toNorth = to.getY() > LB_CASTLE_Y_MAX;

        if ((fromWest && toEast) || (fromEast && toWest)) {
            int avgY = (from.getY() + to.getY()) / 2;
            if (avgY >= LB_CASTLE_Y_MIN && avgY <= LB_CASTLE_Y_MAX) {
                return true;
            }
        }
        if ((fromSouth && toNorth) || (fromNorth && toSouth)) {
            int avgX = (from.getX() + to.getX()) / 2;
            if (avgX >= LB_CASTLE_X_MIN && avgX <= LB_CASTLE_X_MAX) {
                return true;
            }
        }
        if (from.getY() < LB_DINING_Y_MIN && to.getY() > LB_DINING_Y_MAX) {
            int avgX = (from.getX() + to.getX()) / 2;
            if (avgX >= LB_DINING_X_MIN && avgX <= LB_DINING_X_MAX) {
                return true;
            }
        }
        if (from.getY() > LB_DINING_Y_MAX && to.getY() < LB_DINING_Y_MIN) {
            int avgX = (from.getX() + to.getX()) / 2;
            if (avgX >= LB_DINING_X_MIN && avgX <= LB_DINING_X_MAX) {
                return true;
            }
        }
        return false;
    }
}
