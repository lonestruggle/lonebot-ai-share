package net.storm.sdk.movement.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.game.Static;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.WalkClickSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Scene-local collision BFS pathfinder (loaded 104×104 plane only).
 */
public final class Pathfinder {

    private static final Logger log = LoggerFactory.getLogger(Pathfinder.class);

    private static final int SCENE_SIZE = Constants.SCENE_SIZE;
    private static final int MAX_NODES = SCENE_SIZE * SCENE_SIZE;

    private static final int[] DX = {0, 0, 1, -1};
    private static final int[] DY = {1, -1, 0, 0};
    /** Directional wall flag on the <em>current</em> tile when stepping N/S/E/W. */
    private static final int[] EXIT_FLAGS = {
            CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
            CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,
            CollisionDataFlag.BLOCK_MOVEMENT_EAST,
            CollisionDataFlag.BLOCK_MOVEMENT_WEST
    };

    private Pathfinder() {
    }

    private static int waypointMin() {
        return WalkClickSettings.effectiveStepMin();
    }

    private static int waypointMax() {
        return WalkClickSettings.effectiveStepMax();
    }

    public static List<WorldPoint> findPath(WorldPoint from, WorldPoint to) {
        return findPath(from, to, null);
    }

    /**
     * BFS on the current plane using {@link Client#getCollisionMaps()}.
     *
     * @param avoidTiles optional tiles to skip (e.g. Lumbridge blocked zone); start/end still allowed
     * @return path including destination, or empty if unreachable in the loaded scene
     */
    public static List<WorldPoint> findPath(WorldPoint from, WorldPoint to, Predicate<WorldPoint> avoidTiles) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return Collections.emptyList();
        }
        List<WorldPoint> path = Static.callOnClientThread(() -> findPathOnClient(from, to, avoidTiles), null);
        return path != null ? path : Collections.emptyList();
    }

    /**
     * Walk toward {@code to} along a scene collision path.
     * Never clicks a straight-line interpolation through walls.
     */
    public static boolean walkTo(WorldPoint to) {
        return walkTo(to, null);
    }

    public static boolean walkTo(WorldPoint to, Predicate<WorldPoint> avoidTiles) {
        if (to == null) {
            return false;
        }
        WorldPoint from = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Player p = c.getLocalPlayer();
            return p != null ? p.getWorldLocation() : null;
        }, null);
        if (from == null) {
            return net.storm.sdk.movement.WalkClickHelper.walkTo(to);
        }
        if (from.getPlane() == to.getPlane() && from.distanceTo(to) <= 0) {
            return true;
        }
        if (from.getPlane() != to.getPlane()) {
            if (net.storm.sdk.movement.LumbridgeStairsHelper.needsGroundApproach(from, to)) {
                return false;
            }
            if (net.storm.sdk.movement.PlaneChangeHelper.progressTowardPlane(to)) {
                return true;
            }
        }

        // Al onderweg naar (bijna) zelfde doel: kort laten lopen, daarna opnieuw klikken
        WorldPoint currentDest = Movement.getDestination();
        if (currentDest != null && from.getPlane() == to.getPlane()
                && currentDest.distanceTo(to) <= 2
                && from.distanceTo(to) > 6) {
            // Ver genoeg van einddoel → toch nieuw waypoint kiezen (sneller doorsturen)
        } else if (currentDest != null && from.getPlane() == to.getPlane()
                && currentDest.distanceTo(to) < from.distanceTo(to)
                && from.distanceTo(currentDest) > 2
                && from.distanceTo(to) <= 6) {
            // Bijna bij einddoel + nog pathing → niet spammen
            return true;
        }

        // Dest outside loaded 104×104 scene → step toward it within the current scene
        WorldPoint walkTarget = to;
        Boolean inScene = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || from.getPlane() != c.getPlane()) {
                return false;
            }
            return LocalPoint.fromWorld(c, to) != null;
        }, false);
        if (!Boolean.TRUE.equals(inScene) || from.getPlane() != to.getPlane()) {
            WorldPoint edge = sceneStepToward(from, to);
            if (edge == null) {
                return false;
            }
            walkTarget = edge;
        }

        List<WorldPoint> path = findPath(from, walkTarget, avoidTiles);
        if (path == null || path.isEmpty()) {
            // Alleen als doel buiten scene: BFS naar scene-rand. Nooit interpoleren
            // naar een punt "richting doel" als het doel al in de scene zit (muren).
            if (!Boolean.TRUE.equals(inScene)) {
                WorldPoint fallback = sceneStepToward(from, to);
                if (fallback != null && !fallback.equals(from)) {
                    path = findPath(from, fallback, avoidTiles);
                }
            }
            if (path == null || path.isEmpty()) {
                log.debug("[Pathfinder] geen BFS-pad {} → {} — weiger rechte lijn", from, to);
                return false;
            }
        }
        WorldPoint click = pickClickableWaypoint(path);
        if (click == null) {
            click = pickWaypoint(path);
        }
        if (click != null && !isWalkableCached(click)) {
            WorldPoint safeClick = nearestWalkableStand(click);
            click = safeClick != null ? safeClick : click;
        }
        if (click == null) {
            return false;
        }
        return net.storm.sdk.movement.WalkClickHelper.walkTo(click);
    }

    /**
     * Collision-BFS in de geladen scene richting {@code dest} (ook als dest buiten de scene
     * of op een andere plane ligt). Eerste hop terwijl achtergrond-A* het volle pad berekent.
     */
    public static List<WorldPoint> findPathToward(WorldPoint from, WorldPoint dest, Predicate<WorldPoint> avoidTiles) {
        if (from == null || dest == null) {
            return Collections.emptyList();
        }
        if (from.getPlane() == dest.getPlane()) {
            if (isInLoadedScene(dest)) {
                List<WorldPoint> exact = findPath(from, dest, avoidTiles);
                if (exact != null && !exact.isEmpty()) {
                    return exact;
                }
            }
            List<WorldPoint> toward = Static.callOnClientThread(
                    () -> findBestTowardOnClient(from, dest, avoidTiles), null);
            if (toward != null && !toward.isEmpty()) {
                return toward;
            }
        }
        WorldPoint edge = sceneStepToward(from, dest);
        if (edge == null || edge.equals(from)) {
            return Collections.emptyList();
        }
        List<WorldPoint> hop = findPath(from, edge, avoidTiles);
        return hop != null ? hop : Collections.emptyList();
    }

    /** True als world-tile in geladen 104×104 scene zit. */
    public static boolean isInLoadedScene(WorldPoint wp) {
        if (wp == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || wp.getPlane() != c.getPlane()) {
                return false;
            }
            return LocalPoint.fromWorld(c, wp) != null;
        }, false, 80L));
    }

    /**
     * Als {@code wp} niet loopbaar is (water / object): dichtstbijzijnde cardinale
     * stand-tegel binnen radius 4, anders null.
     */
    public static WorldPoint nearestWalkableStand(WorldPoint wp) {
        if (wp == null) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            if (isWalkableSceneTile(c, wp)) {
                return wp;
            }
            // Cardinaal eerst (N/Z/O/W), dan ring
            int[][] ring = {
                    {0, 1}, {0, -1}, {1, 0}, {-1, 0},
                    {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
                    {0, 2}, {0, -2}, {2, 0}, {-2, 0},
                    {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                    {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
                    {0, 3}, {0, -3}, {3, 0}, {-3, 0},
                    {0, 4}, {0, -4}, {4, 0}, {-4, 0}
            };
            WorldPoint best = null;
            int bestDist = Integer.MAX_VALUE;
            for (int[] d : ring) {
                WorldPoint cand = new WorldPoint(wp.getX() + d[0], wp.getY() + d[1], wp.getPlane());
                if (!isWalkableSceneTile(c, cand)) {
                    continue;
                }
                int dist = Math.abs(d[0]) + Math.abs(d[1]);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = cand;
                }
            }
            return best;
        }, null, 80L);
    }

    private static boolean isWalkableCached(WorldPoint wp) {
        if (wp == null) {
            return false;
        }
        // Buiten scene: geen harde reject (global pad); in scene wel
        Boolean inScene = isInLoadedScene(wp);
        if (!inScene) {
            return true;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() ->
                isWalkableSceneTile(Static.getClient(), wp), false, 80L));
    }

    /**
     * Pick an in-scene tile ~18–32 steps toward {@code to} (minimap-friendly).
     */
    private static WorldPoint sceneStepToward(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            int plane = from.getPlane();
            if (plane != c.getPlane()) {
                return null;
            }
            int baseX = c.getBaseX();
            int baseY = c.getBaseY();
            int margin = 3;
            int minX = baseX + margin;
            int maxX = baseX + SCENE_SIZE - 1 - margin;
            int minY = baseY + margin;
            int maxY = baseY + SCENE_SIZE - 1 - margin;

            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double len = Math.hypot(dx, dy);
            if (len < 1.0) {
                return from;
            }
            // Stapgrootte uit WalkClickSettings (Storm2-achtig)
            int stepLo = WalkClickSettings.effectiveStepMin();
            int stepHi = WalkClickSettings.effectiveStepMax();
            int step = stepLo >= stepHi
                    ? stepLo
                    : stepLo + ThreadLocalRandom.current().nextInt(stepHi - stepLo + 1);
            int nx = from.getX() + (int) Math.round(dx / len * Math.min(step, len));
            int ny = from.getY() + (int) Math.round(dy / len * Math.min(step, len));
            nx = Math.max(minX, Math.min(maxX, nx));
            ny = Math.max(minY, Math.min(maxY, ny));
            WorldPoint candidate = new WorldPoint(nx, ny, plane);
            if (LocalPoint.fromWorld(c, candidate) == null) {
                return null;
            }
            // Nooit water / blocked — zoek walkable langs de lijn terug naar from
            if (isWalkableSceneTile(c, candidate)) {
                return candidate;
            }
            for (int back = 1; back <= step; back++) {
                int bx = from.getX() + (int) Math.round(dx / len * Math.max(1, Math.min(step, len) - back));
                int by = from.getY() + (int) Math.round(dy / len * Math.max(1, Math.min(step, len) - back));
                bx = Math.max(minX, Math.min(maxX, bx));
                by = Math.max(minY, Math.min(maxY, by));
                WorldPoint alt = new WorldPoint(bx, by, plane);
                if (LocalPoint.fromWorld(c, alt) != null && isWalkableSceneTile(c, alt)) {
                    return alt;
                }
            }
            // Cardinaal naast candidate
            int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] d : dirs) {
                WorldPoint alt = new WorldPoint(candidate.getX() + d[0], candidate.getY() + d[1], plane);
                if (LocalPoint.fromWorld(c, alt) != null && isWalkableSceneTile(c, alt)) {
                    return alt;
                }
            }
            return null;
        }, null);
    }

    /** Prefer a path tile that projects to canvas or minimap. */
    private static WorldPoint pickClickableWaypoint(List<WorldPoint> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int preferred = waypointMin() + ThreadLocalRandom.current().nextInt(
                Math.max(1, waypointMax() - waypointMin() + 1));
        int hi = Math.min(preferred, path.size() - 1);
        for (int i = hi; i >= 1; i--) {
            WorldPoint wp = path.get(i);
            if (Movement.canClickWalk(wp)) {
                return wp;
            }
        }
        for (int i = hi + 1; i < path.size(); i++) {
            WorldPoint wp = path.get(i);
            if (Movement.canClickWalk(wp)) {
                return wp;
            }
        }
        return null;
    }

    private static WorldPoint pickWaypoint(List<WorldPoint> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int step = waypointMin() + ThreadLocalRandom.current().nextInt(
                Math.max(1, waypointMax() - waypointMin() + 1));
        if (path.size() <= step) {
            return path.get(path.size() - 1);
        }
        return path.get(step);
    }

    private static List<WorldPoint> findPathOnClient(WorldPoint from, WorldPoint to, Predicate<WorldPoint> avoidTiles) {
        Client c = Static.getClient();
        if (c == null) {
            return Collections.emptyList();
        }
        int plane = from.getPlane();
        if (plane != c.getPlane()) {
            return Collections.emptyList();
        }
        CollisionData[] maps = c.getCollisionMaps();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) {
            return Collections.emptyList();
        }
        int[][] flags = maps[plane].getFlags();
        if (flags == null || flags.length < SCENE_SIZE) {
            return Collections.emptyList();
        }

        LocalPoint fromLp = LocalPoint.fromWorld(c, from);
        LocalPoint toLp = LocalPoint.fromWorld(c, to);
        if (fromLp == null || toLp == null) {
            return Collections.emptyList();
        }
        int sx0 = fromLp.getSceneX();
        int sy0 = fromLp.getSceneY();
        int sx1 = toLp.getSceneX();
        int sy1 = toLp.getSceneY();
        if (!inScene(sx0, sy0) || !inScene(sx1, sy1)) {
            return Collections.emptyList();
        }
        if (sx0 == sx1 && sy0 == sy1) {
            List<WorldPoint> trivial = new ArrayList<>(1);
            trivial.add(to);
            return trivial;
        }

        boolean[][] visited = new boolean[SCENE_SIZE][SCENE_SIZE];
        int[][] parent = new int[SCENE_SIZE][SCENE_SIZE];
        for (int i = 0; i < SCENE_SIZE; i++) {
            for (int j = 0; j < SCENE_SIZE; j++) {
                parent[i][j] = -1;
            }
        }

        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(pack(sx0, sy0));
        visited[sx0][sy0] = true;
        int nodes = 1;
        boolean found = false;

        while (!q.isEmpty() && nodes < MAX_NODES) {
            int cur = q.poll();
            int cx = unpackX(cur);
            int cy = unpackY(cur);
            if (cx == sx1 && cy == sy1) {
                found = true;
                break;
            }
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                if (!inScene(nx, ny) || visited[nx][ny]) {
                    continue;
                }
                if (!canStep(flags, cx, cy, nx, ny, EXIT_FLAGS[d])) {
                    continue;
                }
                WorldPoint wp = WorldPoint.fromScene(c, nx, ny, plane);
                if (avoidTiles != null && wp != null
                        && !(nx == sx1 && ny == sy1)
                        && !(nx == sx0 && ny == sy0)
                        && avoidTiles.test(wp)) {
                    continue;
                }
                visited[nx][ny] = true;
                parent[nx][ny] = pack(cx, cy);
                q.add(pack(nx, ny));
                nodes++;
                if (nodes >= MAX_NODES) {
                    break;
                }
            }
        }

        if (!found || !visited[sx1][sy1]) {
            return Collections.emptyList();
        }

        List<WorldPoint> rev = new ArrayList<>();
        int cx = sx1;
        int cy = sy1;
        while (!(cx == sx0 && cy == sy0)) {
            rev.add(WorldPoint.fromScene(c, cx, cy, plane));
            int p = parent[cx][cy];
            if (p < 0) {
                return Collections.emptyList();
            }
            cx = unpackX(p);
            cy = unpackY(p);
        }
        Collections.reverse(rev);
        if (rev.isEmpty() || !rev.get(rev.size() - 1).equals(to)) {
            // Prefer exact destination WorldPoint when last scene tile maps equivalently
            if (!rev.isEmpty()) {
                rev.set(rev.size() - 1, to);
            } else {
                rev.add(to);
            }
        }
        return rev;
    }

    /**
     * Flood-fill de scene; kies de bereikbare tegel het dichtst bij {@code dest}.
     */
    private static List<WorldPoint> findBestTowardOnClient(
            WorldPoint from, WorldPoint dest, Predicate<WorldPoint> avoidTiles) {
        Client c = Static.getClient();
        if (c == null || from == null || dest == null) {
            return Collections.emptyList();
        }
        int plane = from.getPlane();
        if (plane != c.getPlane()) {
            return Collections.emptyList();
        }
        CollisionData[] maps = c.getCollisionMaps();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) {
            return Collections.emptyList();
        }
        int[][] flags = maps[plane].getFlags();
        if (flags == null || flags.length < SCENE_SIZE) {
            return Collections.emptyList();
        }
        LocalPoint fromLp = LocalPoint.fromWorld(c, from);
        if (fromLp == null) {
            return Collections.emptyList();
        }
        int sx0 = fromLp.getSceneX();
        int sy0 = fromLp.getSceneY();
        if (!inScene(sx0, sy0)) {
            return Collections.emptyList();
        }

        boolean[][] visited = new boolean[SCENE_SIZE][SCENE_SIZE];
        int[][] parent = new int[SCENE_SIZE][SCENE_SIZE];
        int[][] walkLen = new int[SCENE_SIZE][SCENE_SIZE];
        for (int i = 0; i < SCENE_SIZE; i++) {
            Arrays.fill(parent[i], -1);
        }

        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(pack(sx0, sy0));
        visited[sx0][sy0] = true;
        int bestSx = sx0;
        int bestSy = sy0;
        int bestDestDist = from.distanceTo(dest);
        int bestWalk = 0;
        int nodes = 1;

        while (!q.isEmpty() && nodes < MAX_NODES) {
            int cur = q.poll();
            int cx = unpackX(cur);
            int cy = unpackY(cur);
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                if (!inScene(nx, ny) || visited[nx][ny]) {
                    continue;
                }
                if (!canStep(flags, cx, cy, nx, ny, EXIT_FLAGS[d])) {
                    continue;
                }
                WorldPoint wp = WorldPoint.fromScene(c, nx, ny, plane);
                if (avoidTiles != null && wp != null
                        && !(nx == sx0 && ny == sy0)
                        && avoidTiles.test(wp)) {
                    continue;
                }
                visited[nx][ny] = true;
                parent[nx][ny] = pack(cx, cy);
                walkLen[nx][ny] = walkLen[cx][cy] + 1;
                q.add(pack(nx, ny));
                nodes++;
                int dd = wp != null ? wp.distanceTo(dest) : Integer.MAX_VALUE;
                int wl = walkLen[nx][ny];
                if (dd < bestDestDist || (dd == bestDestDist && wl < bestWalk)) {
                    bestDestDist = dd;
                    bestWalk = wl;
                    bestSx = nx;
                    bestSy = ny;
                }
            }
        }

        if (bestSx == sx0 && bestSy == sy0) {
            return Collections.emptyList();
        }

        List<WorldPoint> rev = new ArrayList<>();
        int cx = bestSx;
        int cy = bestSy;
        while (!(cx == sx0 && cy == sy0)) {
            rev.add(WorldPoint.fromScene(c, cx, cy, plane));
            int p = parent[cx][cy];
            if (p < 0) {
                return Collections.emptyList();
            }
            cx = unpackX(p);
            cy = unpackY(p);
        }
        Collections.reverse(rev);
        return rev;
    }

    private static boolean canStep(int[][] flags, int cx, int cy, int nx, int ny, int exitFlag) {
        if ((flags[cx][cy] & exitFlag) != 0) {
            return false;
        }
        return (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
    }

    public static boolean isWalkableSceneTile(Client c, WorldPoint wp) {
        if (c == null || wp == null || wp.getPlane() != c.getPlane()) {
            return false;
        }
        LocalPoint lp = LocalPoint.fromWorld(c, wp);
        if (lp == null) {
            return false;
        }
        int sx = lp.getSceneX();
        int sy = lp.getSceneY();
        if (!inScene(sx, sy)) {
            return false;
        }
        CollisionData[] maps = c.getCollisionMaps();
        int plane = wp.getPlane();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) {
            return false;
        }
        int[][] flags = maps[plane].getFlags();
        if (flags == null || sx >= flags.length || sy >= flags[sx].length) {
            return false;
        }
        return (flags[sx][sy] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
    }

    private static boolean inScene(int sx, int sy) {
        return sx >= 0 && sy >= 0 && sx < SCENE_SIZE && sy < SCENE_SIZE;
    }

    private static int pack(int x, int y) {
        return (x << 16) | (y & 0xFFFF);
    }

    private static int unpackX(int packed) {
        return packed >> 16;
    }

    private static int unpackY(int packed) {
        return packed & 0xFFFF;
    }
}
