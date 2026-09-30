package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.movement.pathfinder.AlKharidGate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Webwalker-obstakels: alleen <b>gesloten</b> deuren/gates Open.
 * Open deuren (wallDoor-flag alleen) tellen niet — anders: Open → nooit naar binnen + dubbelklik.
 */
public final class WalkObstacles {

    private static final int DOOR_OPEN_RANGE = 4;
    private static final int PATH_SCAN = 24;
    /** Na geslaagde Open: even niet opnieuw Open/cap — doorlopen. */
    private static final long POST_OPEN_GRACE_MS = 1_400L;
    private static final long OPEN_FAIL_HOLD_MS = 900L;

    private static volatile long lastDoorMs;
    private static volatile long ignoreDoorUntilMs;
    private static volatile long ignoreDoorKey = Long.MIN_VALUE;
    private static volatile long lastLogMs;
    private static volatile String lastLog = "";

    private WalkObstacles() {
    }

    /**
     * @return true als Open is uitgegeven of korte fail-hold — geen walk-klik deze tick
     */
    public static boolean interactIfNeeded(WorldPoint from, WorldPoint dest, List<WorldPoint> path) {
        if (from == null) {
            return false;
        }
        if (dest != null && PlaneChangeHelper.needsPlaneChange(dest)
                && !LumbridgeStairsHelper.needsGroundApproach(from, dest)
                && (from.getPlane() != dest.getPlane() || from.distanceTo(dest) <= 8)) {
            if (PlaneChangeHelper.progressTowardPlane(dest)) {
                return true;
            }
        }
        PathDoor door = firstClosedDoorOnPath(from, dest, path);
        if (door == null) {
            return tryOpenIfOffPathStuck(from, path);
        }
        if (isIgnored(door.before)) {
            return false;
        }
        if (from.distanceTo(door.before) > DOOR_OPEN_RANGE) {
            return false;
        }
        long now = System.currentTimeMillis();
        int gap = 720 + ThreadLocalRandom.current().nextInt(560);
        if (now - lastDoorMs < gap) {
            // Niet opnieuw Open (dubbelklik) — wel walk toestaan als grace loopt
            return false;
        }
        if (tryOpen(door)) {
            lastDoorMs = now;
            armGrace(door.before);
            logOnce("Open deur/gate @" + door.before.getX() + "," + door.before.getY()
                    + " → " + door.after.getX() + "," + door.after.getY());
            return true;
        }
        // Open mislukt: korte hold, daarna weer hoppen (niet eeuwig stil / omheen)
        if (now - lastDoorMs < OPEN_FAIL_HOLD_MS) {
            logOnce("wacht pad-deur @" + door.before.getX() + "," + door.before.getY());
            return true;
        }
        return false;
    }

    /**
     * Klik niet voorbij een <b>gesloten</b> deur. Open deur / grace → plannedHop ongemoeid.
     */
    public static WorldPoint capHopBeforeDoor(WorldPoint from, List<WorldPoint> path, WorldPoint plannedHop) {
        PathDoor door = firstClosedDoorOnPath(from, null, path);
        if (door == null || plannedHop == null) {
            return plannedHop;
        }
        if (isIgnored(door.before)) {
            return plannedHop;
        }
        if (plannedHop.distanceTo(from) <= door.before.distanceTo(from)) {
            return plannedHop;
        }
        return door.before;
    }

    public static WorldPoint firstClosedDoor(WorldPoint from, WorldPoint dest, List<WorldPoint> path) {
        PathDoor d = firstClosedDoorOnPath(from, dest, path);
        return d != null ? d.before : null;
    }

    static PathDoor firstClosedDoorOnPath(WorldPoint from, WorldPoint dest, List<WorldPoint> path) {
        if (from == null || path == null || path.isEmpty()) {
            return null;
        }
        List<WorldPoint> scan = new ArrayList<>();
        scan.add(from);
        int n = 0;
        for (WorldPoint p : path) {
            if (p == null) {
                continue;
            }
            if (++n > PATH_SCAN) {
                break;
            }
            scan.add(p);
        }
        List<ITileObject> barriers = TileObjects.getOnTiles(scan, ClickOnSight::isClosedWalkBarrier, 80L);
        if (barriers == null || barriers.isEmpty()) {
            return null;
        }
        WorldPoint prev = from;
        int k = 0;
        int idx = 0;
        for (WorldPoint p : path) {
            if (p == null) {
                idx++;
                continue;
            }
            if (++k > PATH_SCAN) {
                break;
            }
            if (prev.getPlane() == p.getPlane() && prev.distanceTo(p) == 1) {
                if (hasClosedBarrierOnEdge(prev, p, barriers) && !isIgnored(prev)
                        && pathGoesThroughDoor(path, idx, prev, p, dest)) {
                    return new PathDoor(prev, p);
                }
            }
            prev = p;
            idx++;
        }
        return null;
    }

    /**
     * Alleen Open als het pad écht over de drempel verder gaat — niet een zijdeur
     * waar je 1 tegel naast staat (Varrock West-bank).
     */
    private static boolean pathGoesThroughDoor(
            List<WorldPoint> path, int afterIdx, WorldPoint before, WorldPoint after, WorldPoint dest) {
        if (path == null || after == null) {
            return false;
        }
        if (dest != null && dest.getPlane() == after.getPlane() && dest.distanceTo(after) <= 10) {
            return true;
        }
        int afterCount = 0;
        for (int j = afterIdx + 1; j < path.size() && afterCount < 8; j++) {
            WorldPoint q = path.get(j);
            if (q == null) {
                continue;
            }
            afterCount++;
        }
        if (afterCount < 4) {
            return false;
        }
        if (dest == null || before == null) {
            return afterCount >= 4;
        }
        int dxDoor = after.getX() - before.getX();
        int dyDoor = after.getY() - before.getY();
        int dxDest = dest.getX() - before.getX();
        int dyDest = dest.getY() - before.getY();
        return dxDoor * dxDest + dyDoor * dyDest > 0;
    }

    private static boolean tryOpen(PathDoor door) {
        if (door == null) {
            return false;
        }
        if (ClickOnSight.openDoorAt(door.before)) {
            return true;
        }
        return ClickOnSight.openDoorAt(door.after);
    }

    /**
     * Vast achter een huisdeur die <b>niet</b> op het pad ligt: Open om terug te lopen.
     * Niet Openen als je langs de deur op de weg staat.
     */
    private static boolean tryOpenIfOffPathStuck(WorldPoint from, List<WorldPoint> path) {
        if (from == null || path == null) {
            return false;
        }
        WorldPoint pathTile = null;
        for (WorldPoint p : path) {
            if (p == null || p.getPlane() != from.getPlane()) {
                continue;
            }
            if (p.distanceTo(from) <= 0) {
                return false;
            }
            pathTile = p;
            break;
        }
        if (pathTile == null || from.distanceTo(pathTile) > DOOR_OPEN_RANGE) {
            return false;
        }
        WorldPoint toward = null;
        for (net.runelite.api.coords.Direction dir : net.runelite.api.coords.Direction.values()) {
            WorldPoint n = Reachable.getNeighbour(dir, from);
            if (n == null || n.getPlane() != from.getPlane()) {
                continue;
            }
            if (n.distanceTo(pathTile) >= from.distanceTo(pathTile)) {
                continue;
            }
            if (Reachable.isDoored(from, n)) {
                toward = n;
                break;
            }
        }
        if (toward == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        int gap = 720 + ThreadLocalRandom.current().nextInt(560);
        if (now - lastDoorMs < gap) {
            return false;
        }
        PathDoor exit = new PathDoor(from, toward);
        if (!tryOpen(exit)) {
            return false;
        }
        lastDoorMs = now;
        armGrace(from);
        logOnce("Open (terug op pad) @" + from.getX() + "," + from.getY()
                + " → " + toward.getX() + "," + toward.getY());
        return true;
    }

    private static boolean hasClosedBarrierOnEdge(WorldPoint a, WorldPoint b, List<ITileObject> barriers) {
        boolean objectOnEdge = false;
        for (ITileObject o : barriers) {
            WorldPoint loc = o != null ? o.getWorldLocation() : null;
            if (loc == null) {
                continue;
            }
            if ((a != null && loc.distanceTo(a) == 0) || (b != null && loc.distanceTo(b) == 0)) {
                if (AlKharidGate.isGateTile(loc) || AlKharidGate.isGateObject(o.getId())) {
                    continue;
                }
                objectOnEdge = true;
                break;
            }
        }
        if (!objectOnEdge) {
            return false;
        }
        // Padtegel naast een huisdeur ≠ erdoorheen. Alleen Open als deze stap de drempel is.
        return Reachable.isDoored(a, b);
    }

    private static void armGrace(WorldPoint before) {
        if (before == null) {
            return;
        }
        ignoreDoorKey = tileKey(before);
        ignoreDoorUntilMs = System.currentTimeMillis() + POST_OPEN_GRACE_MS;
    }

    private static boolean isIgnored(WorldPoint before) {
        if (before == null || System.currentTimeMillis() >= ignoreDoorUntilMs) {
            return false;
        }
        return tileKey(before) == ignoreDoorKey;
    }

    private static long tileKey(WorldPoint p) {
        return (((long) p.getPlane()) << 42) | (((long) p.getX()) << 21) | (p.getY() & 0x1FFFFF);
    }

    private static void logOnce(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1500L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Walk/door] " + msg);
    }

    static final class PathDoor {
        final WorldPoint before;
        final WorldPoint after;

        PathDoor(WorldPoint before, WorldPoint after) {
            this.before = before;
            this.after = after;
        }
    }
}
