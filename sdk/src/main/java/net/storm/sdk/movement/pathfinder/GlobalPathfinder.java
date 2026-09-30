package net.storm.sdk.movement.pathfinder;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.api.movement.pathfinder.CollisionMap;
import net.storm.api.movement.pathfinder.GlobalCollisionMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Global pathfinder using {@link GlobalCollisionMap} — A* (Chebyshev), Shortest Path-stijl.
 * Volledig pad of leeg; geen incomplete prefix om op te lopen.
 */
public final class GlobalPathfinder {

    private static final Logger log = LoggerFactory.getLogger(GlobalPathfinder.class);
    private static final int MAX_SEARCH = 5_000_000;
    /** Basisbudget; A* is sneller dan BFS — genoeg voor F2P-mainland. */
    private static final long BFS_BASE_MS = 1_200L;
    private static final long BFS_MS_PER_TILE = 8L;
    private static final long BFS_MAX_MS = 8_000L;
    private static final long CONSOLE_THROTTLE_MS = 1_500L;

    private static volatile GlobalCollisionMap MAP;
    private static volatile String lastConsole = "";
    private static volatile long lastConsoleMs;

    private GlobalPathfinder() {
    }

    public static GlobalCollisionMap map() {
        GlobalCollisionMap m = MAP;
        if (m == null) {
            synchronized (GlobalPathfinder.class) {
                m = MAP;
                if (m == null) {
                    long t0 = System.currentTimeMillis();
                    m = GlobalCollisionMap.loadFromClasspath();
                    MAP = m;
                    log.info("[GlobalPathfinder] loaded {} regions in {} ms",
                            m.loadedRegionCount(), System.currentTimeMillis() - t0);
                }
            }
        }
        return m;
    }

    public static TilePath findPath(WorldPoint start, WorldPoint dest) {
        return findPath(start, dest, null, null);
    }

    public static TilePath findPath(WorldPoint start, WorldPoint dest, Predicate<WorldPoint> avoidTiles) {
        return findPath(start, dest, avoidTiles, null);
    }

    /**
     * Zelfde als {@link #findPath(WorldPoint, WorldPoint, Predicate)} plus transport-edges
     * (deuren/gates/ladders). {@code transports == null} of leeg = alleen lopen.
     */
    public static TilePath findPath(
            WorldPoint start,
            WorldPoint dest,
            Predicate<WorldPoint> avoidTiles,
            Collection<Transport> transports
    ) {
        if (start == null || dest == null) {
            return TilePath.empty();
        }
        boolean hasTransports = transports != null && !transports.isEmpty();
        if (start.getPlane() != dest.getPlane() && !hasTransports) {
            log.warn("[GlobalPathfinder] plane mismatch {} → {}", start, dest);
            return TilePath.empty();
        }
        return findPath(Collections.singletonList(start), dest.toWorldArea(), map(), avoidTiles, transports, 0L);
    }

    public static TilePath findPath(
            WorldPoint start,
            WorldPoint dest,
            Predicate<WorldPoint> avoidTiles,
            Collection<Transport> transports,
            long budgetMs
    ) {
        if (start == null || dest == null) {
            return TilePath.empty();
        }
        boolean hasTransports = transports != null && !transports.isEmpty();
        if (start.getPlane() != dest.getPlane() && !hasTransports) {
            log.warn("[GlobalPathfinder] plane mismatch {} → {}", start, dest);
            return TilePath.empty();
        }
        return findPath(Collections.singletonList(start), dest.toWorldArea(), map(), avoidTiles, transports, budgetMs);
    }

    /**
     * A* from any start to a destination area, optional transport edges, optional collision map.
     */
    public static TilePath findPath(
            Collection<WorldPoint> starts,
            WorldArea dest,
            CollisionMap collisionMap,
            Predicate<WorldPoint> avoidTiles,
            Collection<Transport> transports
    ) {
        return findPath(starts, dest, collisionMap, avoidTiles, transports, 0L);
    }

    public static TilePath findPath(
            Collection<WorldPoint> starts,
            WorldArea dest,
            CollisionMap collisionMap,
            Predicate<WorldPoint> avoidTiles,
            Collection<Transport> transports,
            long budgetOverrideMs
    ) {
        if (dest == null) {
            return TilePath.empty();
        }
        List<WorldPoint> startList = new ArrayList<>();
        if (starts != null) {
            for (WorldPoint s : starts) {
                if (s == null) {
                    continue;
                }
                startList.add(s);
            }
        }
        if (startList.isEmpty()) {
            return TilePath.empty();
        }
        CollisionMap map = collisionMap != null ? collisionMap : map();
        // Trap/booth: start-tegel fullBlock → A* leeg in ~12 ms. Snap naar naburige looptegel.
        List<WorldPoint> snappedStarts = new ArrayList<>(startList.size());
        for (WorldPoint s : startList) {
            if (!map.fullBlock(s)) {
                snappedStarts.add(s);
                continue;
            }
            WorldPoint near = nearestWalkable(s, map, avoidTiles == null ? null : p -> !avoidTiles.test(p));
            snappedStarts.add(near != null ? near : s);
        }
        startList = snappedStarts;
        WorldPoint destPt = dest.toWorldPoint();
        int dist = startList.get(0).distanceTo(destPt);
        long budgetMs = budgetOverrideMs > 0L
                ? Math.min(60_000L, budgetOverrideMs)
                : bfsBudgetMs(dist);
        // Dungeon/instance (grote Y-sprong): meer budget — anders A* timeout vóór trapdoor
        if (isLikelyInstanceGap(startList.get(0), destPt)) {
            budgetMs = Math.max(budgetMs, 14_000L);
        }
        long t0 = System.currentTimeMillis();
        Map<Long, List<Transport>> indexed = indexTransports(transports);
        List<Transport> jumps = longJumpTransports(transports);
        BfsResult result = astar(map, startList, dest, avoidTiles, destPt, indexed, jumps, budgetMs);
        long ms = System.currentTimeMillis() - t0;
        List<WorldPoint> tiles = result.tiles;
        if (tiles.isEmpty()) {
            String msg = "starts=" + startList.size() + " → " + fmt(destPt)
                    + " tiles=0 timeout=" + result.timedOut + " in " + ms + " ms (budget " + budgetMs + ")";
            log.info("[GlobalPathfinder] {}", msg);
            console("A* leeg " + msg);
            return TilePath.empty();
        }
        WorldPoint end = tiles.get(tiles.size() - 1);
        boolean reached = dest.contains(end);
        if (result.timedOut && !reached) {
            console("A* timeout — geen volledig pad (d=" + end.distanceTo(destPt)
                    + ", budget " + budgetMs + " ms) — wacht/retry, geen prefix-walk");
            return TilePath.empty();
        }
        String msg = fmt(startList.get(0)) + " → " + fmt(destPt)
                + " tiles=" + tiles.size() + " reached=" + reached
                + " transports=" + result.transports.size()
                + " in " + ms + " ms (budget " + budgetMs + ")";
        log.info("[GlobalPathfinder] {}", msg);
        console("A* OK tiles=" + tiles.size() + " in " + ms + " ms → " + fmt(destPt));
        TilePath path = new TilePath(tiles, dest, tiles.size(), false);
        for (Transport t : result.transports) {
            path.addTransport(t);
        }
        return path;
    }

    static long bfsBudgetMs(int distTiles) {
        long extra = (long) Math.max(0, distTiles) * BFS_MS_PER_TILE;
        return Math.min(BFS_MAX_MS, BFS_BASE_MS + extra);
    }

    /**
     * Scene-{@code nearestWalkableStand} ziet alleen geladen tegels. Bank-booths
     * (Edgeville 3096,3492) liggen vaak nét buiten de scene vanaf Barbarian village
     * en zijn {@code fullBlock} — pad naar de booth zelf faalt dan altijd.
     */
    public static WorldPoint nearestWalkable(WorldPoint wp) {
        return nearestWalkable(wp, map(), null);
    }

    public static WorldPoint nearestWalkable(WorldPoint wp, CollisionMap collisionMap, Predicate<WorldPoint> filter) {
        if (wp == null) {
            return null;
        }
        CollisionMap map = collisionMap != null ? collisionMap : map();
        if (walkable(map, wp, filter)) {
            return wp;
        }
        int[][] ring = {
                {0, 1}, {0, -1}, {1, 0}, {-1, 0},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
                {0, 2}, {0, -2}, {2, 0}, {-2, 0},
                {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
                {0, 3}, {0, -3}, {3, 0}, {-3, 0},
                {0, 4}, {0, -4}, {4, 0}, {-4, 0},
                {0, 5}, {0, -5}, {5, 0}, {-5, 0}
        };
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        int z = wp.getPlane();
        for (int[] d : ring) {
            WorldPoint cand = new WorldPoint(wp.getX() + d[0], wp.getY() + d[1], z);
            if (!walkable(map, cand, filter)) {
                continue;
            }
            int dist = Math.abs(d[0]) + Math.abs(d[1]);
            if (dist < bestDist) {
                bestDist = dist;
                best = cand;
            }
        }
        return best;
    }

    private static boolean walkable(CollisionMap map, WorldPoint wp, Predicate<WorldPoint> filter) {
        if (wp == null || map.fullBlock(wp)) {
            return false;
        }
        return filter == null || filter.test(wp);
    }

    private static Map<Long, List<Transport>> indexTransports(Collection<Transport> transports) {
        if (transports == null || transports.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<Transport>> bySource = new HashMap<>();
        for (Transport t : transports) {
            if (t == null || t.getSource() == null || t.getDestination() == null) {
                continue;
            }
            // Geen Skills/Quests hier — callOnClientThread per transport doodt A*.
            // Eisen: TilePathWalker bij execute.
            int r = Math.max(0, t.getSourceRadius());
            WorldPoint src = t.getSource();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    WorldPoint near = new WorldPoint(src.getX() + dx, src.getY() + dy, src.getPlane());
                    bySource.computeIfAbsent(key(near), k -> new ArrayList<>()).add(t);
                }
            }
        }
        return bySource;
    }

    /** Surface ↔ dungeon (Y±6400) of andere plane. */
    public static boolean isLikelyInstanceGap(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null) {
            return false;
        }
        if (from.getPlane() != dest.getPlane()) {
            return true;
        }
        return Math.abs(from.getY() - dest.getY()) >= 2000
                || Math.abs(from.getX() - dest.getX()) >= 2000;
    }

    /** Alleen dungeon/instance-sprongen — niet elke boot/fairy. */
    private static List<Transport> longJumpTransports(Collection<Transport> all) {
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        List<Transport> out = new ArrayList<>();
        for (Transport t : all) {
            if (t == null || t.getSource() == null || t.getDestination() == null) {
                continue;
            }
            if (isLikelyInstanceGap(t.getSource(), t.getDestination())) {
                out.add(t);
            }
        }
        return out;
    }

    private static BfsResult astar(
            CollisionMap map,
            List<WorldPoint> starts,
            WorldArea target,
            Predicate<WorldPoint> avoidTiles,
            WorldPoint dest,
            Map<Long, List<Transport>> transports,
            List<Transport> jumps,
            long budgetMs
    ) {
        java.util.PriorityQueue<Node> open = new java.util.PriorityQueue<>(
                java.util.Comparator.comparingInt((Node n) -> n.f).thenComparingInt(n -> n.h));
        Map<Long, Integer> bestG = new HashMap<>();
        long t0 = System.currentTimeMillis();
        int expanded = 0;
        List<Transport> jumpList = jumps != null ? jumps : Collections.emptyList();

        for (WorldPoint s : starts) {
            if (s == null) {
                continue;
            }
            long k = key(s);
            int h = heuristic(s, dest, target, jumpList);
            Node n = new Node(s, null, null, 0, h);
            bestG.put(k, 0);
            open.add(n);
        }

        while (!open.isEmpty()) {
            if (expanded >= MAX_SEARCH) {
                log.debug("[GlobalPathfinder] A* max search {}", MAX_SEARCH);
                return BfsResult.empty();
            }
            int mask = budgetMs <= 500L ? 0x1F : 0xFF;
            if ((expanded & mask) == 0 && (System.currentTimeMillis() - t0) >= budgetMs) {
                log.warn("[GlobalPathfinder] A* time budget {}ms — abort (expanded={})",
                        budgetMs, expanded);
                console("A* timeout " + budgetMs + " ms expanded=" + expanded);
                return BfsResult.timeout();
            }
            Node node = open.poll();
            if (node == null) {
                break;
            }
            long nk = key(node.pos);
            Integer known = bestG.get(nk);
            if (known != null && node.g > known) {
                continue;
            }
            expanded++;
            if (target.contains(node.pos)) {
                return node.result();
            }
            expandNeighbors(map, node, open, bestG, avoidTiles, target, dest, jumpList);
            expandTransports(node, open, bestG, avoidTiles, target, dest, transports, jumpList);
        }
        return BfsResult.empty();
    }

    /** Chebyshev; dungeon/trapdoor via weinige instance-jumps. */
    private static int heuristic(WorldPoint from, WorldPoint dest, WorldArea target, List<Transport> jumps) {
        if (from == null) {
            return 0;
        }
        WorldPoint goal = dest != null ? dest : (target != null ? target.toWorldPoint() : null);
        if (goal == null) {
            return 0;
        }
        int direct = chebyshev(from, goal);
        if (from.getPlane() != goal.getPlane()) {
            direct += 10_000;
        }
        if (jumps == null || jumps.isEmpty() || !isLikelyInstanceGap(from, goal)) {
            return direct;
        }
        int best = direct;
        for (Transport t : jumps) {
            WorldPoint src = t.getSource();
            WorldPoint land = t.getDestination();
            if (src == null || land == null) {
                continue;
            }
            if (src.getPlane() != from.getPlane() && land.getPlane() != goal.getPlane()) {
                continue;
            }
            if (chebyshev(from, src) > 80 && chebyshev(land, goal) > 80) {
                continue;
            }
            int via = chebyshev(from, src) + Math.max(1, t.getWeight()) + chebyshev(land, goal);
            if (via < best) {
                best = via;
            }
        }
        return best;
    }

    private static int chebyshev(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE / 4;
        }
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    private static void expandNeighbors(
            CollisionMap map,
            Node node,
            java.util.PriorityQueue<Node> open,
            Map<Long, Integer> bestG,
            Predicate<WorldPoint> avoidTiles,
            WorldArea target,
            WorldPoint dest,
            List<Transport> jumps
    ) {
        int x = node.pos.getX();
        int y = node.pos.getY();
        int z = node.pos.getPlane();
        tryExpand(map, node, open, bestG, avoidTiles, target, dest, jumps, x - 1, y, z, map.w(x, y, z));
        tryExpand(map, node, open, bestG, avoidTiles, target, dest, jumps, x + 1, y, z, map.e(x, y, z));
        tryExpand(map, node, open, bestG, avoidTiles, target, dest, jumps, x, y - 1, z, map.s(x, y, z));
        tryExpand(map, node, open, bestG, avoidTiles, target, dest, jumps, x, y + 1, z, map.n(x, y, z));
        tryExpand(map, node, open, bestG, avoidTiles, target, dest, jumps, x - 1, y - 1, z, map.sw(x, y, z));
        tryExpand(map, node, open, bestG, avoidTiles, target, dest, jumps, x + 1, y - 1, z, map.se(x, y, z));
        tryExpand(map, node, open, bestG, avoidTiles, target, dest, jumps, x - 1, y + 1, z, map.nw(x, y, z));
        tryExpand(map, node, open, bestG, avoidTiles, target, dest, jumps, x + 1, y + 1, z, map.ne(x, y, z));
    }

    private static void tryExpand(
            CollisionMap map,
            Node node,
            java.util.PriorityQueue<Node> open,
            Map<Long, Integer> bestG,
            Predicate<WorldPoint> avoidTiles,
            WorldArea target,
            WorldPoint dest,
            List<Transport> jumps,
            int x, int y, int z,
            boolean passable
    ) {
        if (!passable) {
            return;
        }
        WorldPoint next = new WorldPoint(x, y, z);
        if (avoidTiles != null && avoidTiles.test(next) && (target == null || !target.contains(next))) {
            return;
        }
        int g = node.g + 1;
        long k = key(next);
        Integer prev = bestG.get(k);
        if (prev != null && prev <= g) {
            return;
        }
        bestG.put(k, g);
        int h = heuristic(next, dest, target, jumps);
        open.add(new Node(next, node, null, g, h));
    }

    private static void expandTransports(
            Node node,
            java.util.PriorityQueue<Node> open,
            Map<Long, Integer> bestG,
            Predicate<WorldPoint> avoidTiles,
            WorldArea target,
            WorldPoint dest,
            Map<Long, List<Transport>> transports,
            List<Transport> jumps
    ) {
        if (transports == null || transports.isEmpty()) {
            return;
        }
        List<Transport> list = transports.get(key(node.pos));
        if (list == null) {
            return;
        }
        for (Transport t : list) {
            // Geen Skills/Quests/canPass hier (client-thread). Execute + walker filteren.
            WorldPoint land = t.getDestination();
            if (land == null) {
                continue;
            }
            if (avoidTiles != null && avoidTiles.test(land) && (target == null || !target.contains(land))) {
                continue;
            }
            int g = node.g + Math.max(1, t.getWeight());
            long k = key(land);
            Integer prev = bestG.get(k);
            if (prev != null && prev <= g) {
                continue;
            }
            bestG.put(k, g);
            int h = heuristic(land, dest, target, jumps);
            open.add(new Node(land, node, t, g, h));
        }
    }

    private static long key(WorldPoint p) {
        return (((long) p.getPlane()) << 42) | (((long) p.getX()) << 21) | (p.getY() & 0x1FFFFF);
    }

    private static final class BfsResult {
        final List<WorldPoint> tiles;
        final List<Transport> transports;
        final boolean timedOut;

        BfsResult(List<WorldPoint> tiles, List<Transport> transports, boolean timedOut) {
            this.tiles = tiles;
            this.transports = transports;
            this.timedOut = timedOut;
        }

        static BfsResult empty() {
            return new BfsResult(Collections.emptyList(), Collections.emptyList(), false);
        }

        static BfsResult timeout() {
            return new BfsResult(Collections.emptyList(), Collections.emptyList(), true);
        }
    }

    private static final class Node {
        final WorldPoint pos;
        final Node prev;
        final Transport via;
        final int g;
        final int h;
        final int f;

        Node(WorldPoint pos, Node prev, Transport via, int g, int h) {
            this.pos = pos;
            this.prev = prev;
            this.via = via;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }

        BfsResult result() {
            return result(false);
        }

        BfsResult result(boolean timedOut) {
            LinkedList<WorldPoint> out = new LinkedList<>();
            List<Transport> used = new ArrayList<>();
            Node n = this;
            while (n != null) {
                out.addFirst(n.pos);
                if (n.via != null) {
                    used.add(0, n.via);
                }
                n = n.prev;
            }
            return new BfsResult(new ArrayList<>(out), used, timedOut);
        }
    }

    private static String fmt(WorldPoint p) {
        return p == null ? "-" : (p.getX() + "," + p.getY());
    }

    private static void console(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastConsole) && now - lastConsoleMs < CONSOLE_THROTTLE_MS) {
            return;
        }
        lastConsole = msg;
        lastConsoleMs = now;
        BotRuntime.logConsole("[Walk/path] " + msg);
    }
}
