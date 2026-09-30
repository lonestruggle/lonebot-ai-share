package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.Locatable;
import net.storm.api.domain.tiles.ITile;
import net.storm.sdk.entities.Tiles;
import net.storm.sdk.game.Static;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Collision, walls, doors and flood-fill reachability on the loaded scene.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/Reachable.html">Storm Reachable</a>
 */
public class Reachable {

    private static final int OUT_OF_SCENE = 0xFFFFFF;
    private static final int MAX_VISIT = 104 * 104;

    public Reachable() {
    }

    public static boolean check(int flag, int checkFlag) {
        return (flag & checkFlag) != 0;
    }

    public static boolean isObstacle(int endFlag) {
        return check(endFlag, CollisionDataFlag.BLOCK_MOVEMENT_FULL);
    }

    public static boolean isObstacle(WorldPoint worldPoint) {
        return isObstacle(getCollisionFlag(worldPoint));
    }

    public static int getCollisionFlag(WorldPoint point) {
        if (point == null) {
            return OUT_OF_SCENE;
        }
        Integer flag = Static.callOnClientThread(() -> collisionFlagOnClient(point), OUT_OF_SCENE);
        return flag != null ? flag : OUT_OF_SCENE;
    }

    public static boolean isWalled(Direction direction, int startFlag) {
        if (direction == null) {
            return false;
        }
        switch (direction) {
            case NORTH:
                return check(startFlag, CollisionDataFlag.BLOCK_MOVEMENT_NORTH);
            case SOUTH:
                return check(startFlag, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH);
            case EAST:
                return check(startFlag, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
            case WEST:
                return check(startFlag, CollisionDataFlag.BLOCK_MOVEMENT_WEST);
            default:
                return false;
        }
    }

    public static boolean isWalled(WorldPoint source, WorldPoint destination) {
        Direction dir = directionBetween(source, destination);
        if (dir == null) {
            return false;
        }
        return isWalled(dir, getCollisionFlag(source));
    }

    public static boolean isWalled(ITile source, ITile destination) {
        if (source == null || destination == null) {
            return false;
        }
        return isWalled(source.getWorldLocation(), destination.getWorldLocation());
    }

    public static boolean hasDoor(WorldPoint source, Direction direction) {
        if (source == null || direction == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            if (tileHasDoor(c, Tiles.getAt(source))) {
                return true;
            }
            WorldPoint neighbour = getNeighbour(direction, source);
            return tileHasDoor(c, Tiles.getAt(neighbour));
        }, false));
    }

    public static boolean hasDoor(ITile source, Direction direction) {
        if (source == null) {
            return false;
        }
        return hasDoor(source.getWorldLocation(), direction);
    }

    public static boolean isDoored(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return false;
        }
        Direction dir = directionBetween(from, to);
        if (dir == null) {
            return false;
        }
        return isWalled(from, to) && (hasDoor(from, dir) || hasDoor(to, opposite(dir)));
    }

    public static boolean isDoored(ITile source, ITile destination) {
        if (source == null || destination == null) {
            return false;
        }
        WorldPoint from = source.getWorldLocation();
        WorldPoint to = destination.getWorldLocation();
        Direction dir = directionBetween(from, to);
        if (dir == null) {
            return false;
        }
        return isWalled(from, to) && (hasDoor(from, dir) || hasDoor(to, opposite(dir)));
    }

    public static boolean canWalk(Direction direction, int startFlag, int endFlag) {
        if (direction == null || isObstacle(endFlag)) {
            return false;
        }
        return !isWalled(direction, startFlag);
    }

    public static WorldPoint getNeighbour(Direction direction, WorldPoint source) {
        if (direction == null || source == null) {
            return null;
        }
        switch (direction) {
            case NORTH:
                return new WorldPoint(source.getX(), source.getY() + 1, source.getPlane());
            case SOUTH:
                return new WorldPoint(source.getX(), source.getY() - 1, source.getPlane());
            case EAST:
                return new WorldPoint(source.getX() + 1, source.getY(), source.getPlane());
            case WEST:
                return new WorldPoint(source.getX() - 1, source.getY(), source.getPlane());
            default:
                return null;
        }
    }

    public static List<WorldPoint> getVisitedTiles(Locatable locatable) {
        return locatable == null ? Collections.emptyList() : getVisitedTiles(locatable.getWorldLocation());
    }

    public static List<WorldPoint> getVisitedTiles(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return Collections.emptyList();
        }
        List<WorldPoint> tiles = Static.callOnClientThread(() -> floodFill(worldPoint), Collections.emptyList());
        return tiles != null ? tiles : Collections.emptyList();
    }

    /**
     * Scene BFS distances from {@code from} (walls respected). Does <b>not</b> touch the walker.
     * Key = {@link #tileKey(WorldPoint)}.
     */
    public static java.util.Map<Long, Integer> walkDistances(WorldPoint from) {
        if (from == null) {
            return Collections.emptyMap();
        }
        java.util.Map<Long, Integer> dist = Static.callOnClientThread(() -> floodDistances(from), null);
        return dist != null ? dist : Collections.emptyMap();
    }

    public static long tileKey(WorldPoint p) {
        return p == null ? 0L : key(p);
    }

    /**
     * Min walk steps on the loaded scene to a stand-tile next to {@code target}
     * (Chebyshev ≤1, not through walls). {@link Integer#MAX_VALUE} if unreachable.
     */
    public static int minStepsToAdjacent(WorldPoint from, WorldPoint target) {
        return minStepsToAdjacent(walkDistances(from), from, target);
    }

    /**
     * Same as {@link #minStepsToAdjacent(WorldPoint, WorldPoint)} using a precomputed
     * {@link #walkDistances} map (one BFS for many targets).
     */
    public static int minStepsToAdjacent(java.util.Map<Long, Integer> dist, WorldPoint from, WorldPoint target) {
        if (from == null || target == null || from.getPlane() != target.getPlane()) {
            return Integer.MAX_VALUE;
        }
        if (from.distanceTo(target) <= 1 && !isWalled(from, target)) {
            return 0;
        }
        if (dist == null || dist.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int best = Integer.MAX_VALUE;
        int[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : offs) {
            WorldPoint stand = new WorldPoint(target.getX() + o[0], target.getY() + o[1], target.getPlane());
            if (isWalled(stand, target)) {
                continue;
            }
            Integer d = dist.get(key(stand));
            if (d != null && d < best) {
                best = d;
            }
        }
        return best;
    }

    /**
     * True if the player can walk (in the loaded scene) to a tile adjacent to {@code locatable}.
     */
    public static boolean isInteractable(Locatable locatable) {
        return locatable != null && isInteractable(locatable.getWorldLocation(), 1);
    }

    public static boolean isInteractable(WorldPoint target) {
        return isInteractable(target, 1);
    }

    /**
     * LoneBot extra: {@code maxDist} is Chebyshev distance from a reachable stand-tile to {@code target}.
     * Storm {@code isInteractable} is {@code maxDist == 1}.
     */
    public static boolean isInteractable(WorldPoint target, int maxDist) {
        if (target == null) {
            return false;
        }
        int limit = Math.max(1, maxDist);
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null) {
                return false;
            }
            WorldPoint me = c.getLocalPlayer().getWorldLocation();
            if (me == null || me.getPlane() != target.getPlane()) {
                return false;
            }
            if (me.distanceTo(target) == 0) {
                return true;
            }
            List<WorldPoint> visited = floodFill(me);
            for (WorldPoint p : visited) {
                if (p != null && p.getPlane() == target.getPlane() && p.distanceTo(target) <= limit) {
                    return true;
                }
            }
            return false;
        }, false));
    }

    public static boolean isWalkable(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return false;
        }
        int flag = getCollisionFlag(worldPoint);
        return flag != OUT_OF_SCENE && !isObstacle(flag);
    }

    /** Bresenham + wall flags — fallback when {@code WorldArea.hasLineOfSightTo} is missing. */
    public static boolean hasLineOfSight(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return false;
        }
        int x0 = from.getX();
        int y0 = from.getY();
        int x1 = to.getX();
        int y1 = to.getY();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        int plane = from.getPlane();
        while (x != x1 || y != y1) {
            int e2 = 2 * err;
            int nx = x;
            int ny = y;
            if (e2 > -dy) {
                err -= dy;
                nx += sx;
            }
            if (e2 < dx) {
                err += dx;
                ny += sy;
            }
            WorldPoint cur = new WorldPoint(x, y, plane);
            WorldPoint next = new WorldPoint(nx, ny, plane);
            if (Math.abs(nx - x) + Math.abs(ny - y) == 1 && isWalled(cur, next)) {
                return false;
            }
            if (Math.abs(nx - x) == 1 && Math.abs(ny - y) == 1) {
                WorldPoint viaX = new WorldPoint(nx, y, plane);
                WorldPoint viaY = new WorldPoint(x, ny, plane);
                boolean blocked = (isWalled(cur, viaX) || isWalled(viaX, next))
                        && (isWalled(cur, viaY) || isWalled(viaY, next));
                if (blocked) {
                    return false;
                }
            }
            x = nx;
            y = ny;
        }
        return true;
    }

    private static List<WorldPoint> floodFill(WorldPoint start) {
        Client c = Static.getClient();
        if (c == null || start == null) {
            return Collections.emptyList();
        }
        List<WorldPoint> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<WorldPoint> q = new ArrayDeque<>();
        q.add(start);
        seen.add(key(start));
        while (!q.isEmpty() && out.size() < MAX_VISIT) {
            WorldPoint cur = q.removeFirst();
            out.add(cur);
            int startFlag = collisionFlagOnClient(cur);
            for (Direction dir : Direction.values()) {
                WorldPoint next = getNeighbour(dir, cur);
                if (next == null || next.getPlane() != cur.getPlane()) {
                    continue;
                }
                if (!seen.add(key(next))) {
                    continue;
                }
                int endFlag = collisionFlagOnClient(next);
                if (endFlag == OUT_OF_SCENE || !canWalk(dir, startFlag, endFlag)) {
                    continue;
                }
                q.addLast(next);
            }
        }
        return out;
    }

    /** Must run on client thread. */
    private static java.util.Map<Long, Integer> floodDistances(WorldPoint start) {
        Client c = Static.getClient();
        if (c == null || start == null) {
            return Collections.emptyMap();
        }
        java.util.Map<Long, Integer> dist = new java.util.HashMap<>();
        ArrayDeque<WorldPoint> q = new ArrayDeque<>();
        dist.put(key(start), 0);
        q.add(start);
        while (!q.isEmpty() && dist.size() < MAX_VISIT) {
            WorldPoint cur = q.removeFirst();
            int d = dist.getOrDefault(key(cur), 0);
            int startFlag = collisionFlagOnClient(cur);
            for (Direction dir : Direction.values()) {
                WorldPoint next = getNeighbour(dir, cur);
                if (next == null || next.getPlane() != cur.getPlane()) {
                    continue;
                }
                long nk = key(next);
                if (dist.containsKey(nk)) {
                    continue;
                }
                int endFlag = collisionFlagOnClient(next);
                if (endFlag == OUT_OF_SCENE || !canWalk(dir, startFlag, endFlag)) {
                    continue;
                }
                dist.put(nk, d + 1);
                q.addLast(next);
            }
        }
        return dist;
    }

    private static int collisionFlagOnClient(WorldPoint point) {
        Client c = Static.getClient();
        if (c == null || point == null) {
            return OUT_OF_SCENE;
        }
        CollisionData[] maps = c.getCollisionMaps();
        int plane = point.getPlane();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) {
            return OUT_OF_SCENE;
        }
        LocalPoint lp = LocalPoint.fromWorld(c, point);
        if (lp == null) {
            return OUT_OF_SCENE;
        }
        int[][] flags = maps[plane].getFlags();
        int sx = lp.getSceneX();
        int sy = lp.getSceneY();
        if (flags == null || sx < 0 || sy < 0 || sx >= flags.length || sy >= flags[sx].length) {
            return OUT_OF_SCENE;
        }
        return flags[sx][sy];
    }

    private static boolean tileHasDoor(Client c, Tile tile) {
        if (c == null || tile == null) {
            return false;
        }
        WallObject wall = tile.getWallObject();
        if (wall != null && isDoorObject(c, wall)) {
            return true;
        }
        GameObject[] objs = tile.getGameObjects();
        if (objs != null) {
            for (GameObject go : objs) {
                if (go != null && isDoorObject(c, go)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDoorObject(Client c, TileObject object) {
        if (object == null) {
            return false;
        }
        ObjectComposition comp = c.getObjectDefinition(object.getId());
        if (comp == null) {
            return false;
        }
        String name = comp.getName();
        if (name != null) {
            String low = name.toLowerCase(Locale.ROOT);
            if (low.contains("door") || low.contains("gate") || low.contains("portcullis")) {
                return true;
            }
        }
        String[] actions = comp.getActions();
        if (actions == null) {
            return false;
        }
        for (String a : actions) {
            if (a != null && ("Open".equalsIgnoreCase(a) || "Close".equalsIgnoreCase(a))) {
                return true;
            }
        }
        return false;
    }

    public static Direction directionBetween(WorldPoint source, WorldPoint destination) {
        if (source == null || destination == null || source.getPlane() != destination.getPlane()) {
            return null;
        }
        int dx = destination.getX() - source.getX();
        int dy = destination.getY() - source.getY();
        if (dx == 0 && dy == 1) {
            return Direction.NORTH;
        }
        if (dx == 0 && dy == -1) {
            return Direction.SOUTH;
        }
        if (dx == 1 && dy == 0) {
            return Direction.EAST;
        }
        if (dx == -1 && dy == 0) {
            return Direction.WEST;
        }
        return null;
    }

    static Direction opposite(Direction direction) {
        if (direction == null) {
            return null;
        }
        switch (direction) {
            case NORTH:
                return Direction.SOUTH;
            case SOUTH:
                return Direction.NORTH;
            case EAST:
                return Direction.WEST;
            case WEST:
                return Direction.EAST;
            default:
                return null;
        }
    }

    private static long key(WorldPoint p) {
        return (((long) p.getPlane()) << 42) | (((long) p.getX()) << 21) | (p.getY() & 0x1FFFFF);
    }
}
