package net.storm.sdk.movement;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.IWalker;
import net.storm.api.movement.TilePath;
import net.storm.api.movement.WalkOptions;
import net.storm.api.movement.pathfinder.CollisionMap;
import net.storm.api.movement.pathfinder.model.Teleport;
import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.pathfinder.AlKharidGate;
import net.storm.sdk.movement.pathfinder.GlobalPathfinder;
import net.storm.sdk.movement.pathfinder.PathfinderRequirements;
import net.storm.sdk.movement.pathfinder.TeleportLoader;
import net.storm.sdk.movement.pathfinder.TransportLoader;
import net.storm.sdk.tiles.NoWalkZones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Storm {@code Walker} — path build zoals [Shortest Path](https://github.com/Skretzo/shortest-path):
 * globale collision-map + transport-edges (poorten/stiles), daarna {@link TilePath#walk()}.
 * Geen fallback naar scene-BFS / greedy “richting dest” (dat is over water klikken).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/Walker.html">Storm Walker</a>
 */
public final class Walker {

    private static final Logger log = LoggerFactory.getLogger(Walker.class);
    private static final int TELEPORT_SKIP_NEAR = 30;
    private static final long CONSOLE_THROTTLE_MS = 1500L;

    public static final IWalker API = new Api();

    private static volatile TilePath currentPath;
    private static volatile TilePath lastPath;
    private static final ConcurrentHashMap<String, TilePath> CACHE = new ConcurrentHashMap<>();
    private static volatile String lastLog = "";
    private static volatile long lastLogMs;

    static {
        net.storm.api.Static.bindWalker(API);
    }

    public Walker() {
    }

    public static boolean walkTo(WorldArea destination, CollisionMap collisionMap, boolean useTeleports) {
        WalkOptions options = WalkOptions.builder()
                .collisionMap(collisionMap)
                .useTeleports(useTeleports)
                .build();
        return walkTo(destination, options);
    }

    public static boolean walkTo(WorldArea destination, WalkOptions options) {
        if (destination == null) {
            return false;
        }
        WalkOptions opts = options != null ? options : WalkOptions.builder().build();
        applyRun(opts);
        TilePath path = buildPath(Collections.emptyList(), destination, opts);
        return walkAlong(destination, path, buildTransportLinks(opts), opts);
    }

    public static boolean walkTo(WorldPoint destination, WalkOptions options) {
        if (destination == null) {
            return false;
        }
        return walkTo(destination.toWorldArea(), options);
    }

    public static TilePath getCurrentPath() {
        TilePath active = MovementHelper.getActivePath();
        if (active != null && !active.isEmpty()) {
            return active;
        }
        return currentPath;
    }

    public static TilePath getLastPath() {
        return lastPath;
    }

    public static void invalidatePath() {
        currentPath = null;
        lastPath = null;
        CACHE.clear();
        MovementHelper.clearPath();
    }

    public static boolean walkAlong(WorldArea destination, TilePath path,
                                    Map<WorldPoint, List<Transport>> transports, WalkOptions options) {
        if (path == null || path.isEmpty()) {
            logWalk("FAIL empty path");
            return false;
        }
        WalkOptions opts = options != null ? options : WalkOptions.builder().build();
        applyRun(opts);
        path.setWalkOptions(opts);
        if (transports != null) {
            for (List<Transport> list : transports.values()) {
                if (list == null) {
                    continue;
                }
                for (Transport t : list) {
                    path.addTransport(t);
                }
            }
        }
        currentPath = path;
        lastPath = path;
        BotRuntime.debugPath = path;
        WorldPoint destPt = destination != null ? destination.toWorldPoint() : null;
        if (destPt != null) {
            BotRuntime.debugTarget = destPt;
        }
        if (MovementHelper.isGreedyInScenePrefix(path, destPt)) {
            logWalk("FAIL greedy/incomplete prefix (water/muur) dest=" + destPt);
            return false;
        }
        MovementHelper.adoptWalkerPath(path, destPt);
        WorldPoint me = localPos();
        if (me != null && tryTeleportIfNeeded(path, me)) {
            return true;
        }
        boolean ok = path.walk(opts);
        logWalk("walkAlong tiles=" + path.size() + " ok=" + ok);
        return ok;
    }

    @Deprecated
    public static TilePath buildPath(WorldArea destination, CollisionMap collisionMap, boolean avoidWilderness) {
        return buildPath(Collections.emptyList(), destination,
                WalkOptions.builder().collisionMap(collisionMap).avoidWilderness(avoidWilderness).build());
    }

    @Deprecated
    public static TilePath buildPath(WorldArea destination, CollisionMap collisionMap) {
        return buildPath(destination, collisionMap, true);
    }

    @Deprecated
    public static TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap) {
        return buildPath(startPoints, destination, WalkOptions.builder().collisionMap(collisionMap).build());
    }

    @Deprecated
    public static TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination,
                                     CollisionMap collisionMap, boolean avoidWilderness) {
        return buildPath(startPoints, destination,
                WalkOptions.builder().collisionMap(collisionMap).avoidWilderness(avoidWilderness).build());
    }

    @Deprecated
    public static TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination,
                                     CollisionMap collisionMap, boolean avoidWilderness, boolean useCache) {
        return buildPath(startPoints, destination, WalkOptions.builder()
                .collisionMap(collisionMap).avoidWilderness(avoidWilderness).useCache(useCache).build());
    }

    @Deprecated
    public static TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination,
                                     CollisionMap collisionMap, boolean avoidWilderness, boolean useCache,
                                     boolean useTransports) {
        return buildPath(startPoints, destination, WalkOptions.builder()
                .collisionMap(collisionMap).avoidWilderness(avoidWilderness)
                .useCache(useCache).useTransports(useTransports).build());
    }

    @Deprecated
    public static TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination,
                                     CollisionMap collisionMap, boolean avoidWilderness, boolean useCache,
                                     boolean useTransports, HashMap<WorldPoint, Teleport> teleports) {
        WalkOptions options = WalkOptions.builder()
                .collisionMap(collisionMap).avoidWilderness(avoidWilderness)
                .useCache(useCache).useTransports(useTransports).build();
        return buildPath(startPoints, destination, options, teleports, buildTransportLinks(options));
    }

    public static TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, WalkOptions options,
                                     HashMap<WorldPoint, Teleport> teleports, HashMap<WorldPoint, List<Transport>> transports) {
        Map<WorldPoint, List<Transport>> t = transports != null ? transports : Collections.emptyMap();
        return buildPath(startPoints, destination, options, teleports, t);
    }

    public static TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, WalkOptions options,
                                     HashMap<WorldPoint, Teleport> teleports, Map<WorldPoint, List<Transport>> transports) {
        WalkOptions opts = options != null ? options : WalkOptions.builder().build();
        WorldArea dest = destination;
        if (dest == null) {
            return TilePath.empty();
        }
        List<WorldPoint> starts = resolveStarts(startPoints, teleports, dest, opts);
        if (starts.isEmpty()) {
            return TilePath.empty();
        }
        String cacheKey = opts.isUseCache() ? cacheKey(starts, dest, opts) : null;
        if (cacheKey != null) {
            TilePath cached = CACHE.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                lastPath = cached;
                return cached;
            }
        }
        Predicate<WorldPoint> avoid = avoidPredicate(opts, dest, starts);
        CollisionMap map = opts.getCollisionMap();
        Collection<Transport> transportList = opts.isUseTransports()
                ? flatten(transports != null ? transports : buildTransportLinks(opts))
                : Collections.emptyList();
        TilePath path = GlobalPathfinder.findPath(starts, dest, map, avoid, transportList);
        WorldPoint origin = localPos();
        if (origin == null && !starts.isEmpty()) {
            origin = starts.get(0);
        }
        net.storm.sdk.movement.pathfinder.F2pSpellTeleports.attachIfUsed(path, origin);
        remember(path, cacheKey);
        return path;
    }

    public static TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, WalkOptions options) {
        WalkOptions opts = options != null ? options : WalkOptions.builder().build();
        if (opts.isUseTeleports()) {
            net.storm.sdk.movement.pathfinder.F2pSpellTeleports.ensureRegistered();
        }
        HashMap<WorldPoint, Teleport> teles = opts.isUseTeleports()
                ? new HashMap<>(buildExperimentalTeleportLinks(destination, opts))
                : new HashMap<>();
        return buildPath(startPoints, destination, opts, teles, buildTransportLinks(opts));
    }

    public static TilePath buildPath(WorldArea destination, WalkOptions options) {
        return buildPath(Collections.emptyList(), destination, options);
    }

    public static Map<WorldPoint, List<Transport>> buildTransportLinks(WalkOptions options) {
        WalkOptions opts = options != null ? options : WalkOptions.builder().build();
        Map<WorldPoint, List<Transport>> out = new HashMap<>();
        if (!opts.isUseTransports()) {
            return out;
        }
        for (Transport t : TransportLoader.getCustomTransports()) {
            if (!transportAllowed(t, opts)) {
                continue;
            }
            WorldPoint src = t.getSource();
            if (src == null) {
                continue;
            }
            out.computeIfAbsent(src, k -> new ArrayList<>()).add(t);
        }
        return out;
    }

    public static Map<WorldPoint, List<Transport>> buildTransportLinks() {
        return buildTransportLinks(WalkOptions.builder().build());
    }

    /**
     * Transports die {@link MovementHelper#walkTo} in de globale BFS mag gebruiken.
     * Zelfde set als {@link #buildTransportLinks()} — leeg tot er custom/TSV-data is.
     */
    public static Collection<Transport> listTransports() {
        return flatten(buildTransportLinks(WalkOptions.builder()
                .useTransports(true)
                .useTeleports(false)
                .build()));
    }

    public static LinkedHashMap<WorldPoint, Teleport> buildTeleportLinks(WorldArea destination, WalkOptions options) {
        return buildExperimentalTeleportLinks(destination, options);
    }

    public static LinkedHashMap<WorldPoint, Teleport> buildTeleportLinks(WorldArea destination) {
        return buildExperimentalTeleportLinks(destination, WalkOptions.builder().build());
    }

    @Deprecated
    public static LinkedHashMap<WorldPoint, Teleport> buildExperimentalTeleportLinks(WorldArea destination,
                                                                                     WalkOptions options) {
        WalkOptions opts = options != null ? options : WalkOptions.builder().build();
        LinkedHashMap<WorldPoint, Teleport> out = new LinkedHashMap<>();
        if (!opts.isUseTeleports() || destination == null) {
            return out;
        }
        WorldPoint dest = destination.toWorldPoint();
        WorldPoint me = localPos();
        List<Teleport> ranked = new ArrayList<>();
        for (Teleport t : TeleportLoader.getCustomTeleports()) {
            if (!teleportAllowed(t, opts, me, true)) {
                continue;
            }
            ranked.add(t);
        }
        ranked.sort((a, b) -> {
            int da = a.getDestination() != null && dest != null ? a.getDestination().distanceTo(dest) : Integer.MAX_VALUE;
            int db = b.getDestination() != null && dest != null ? b.getDestination().distanceTo(dest) : Integer.MAX_VALUE;
            if (da != db) {
                return Integer.compare(da, db);
            }
            return Integer.compare(a.getPriority(), b.getPriority());
        });
        for (Teleport t : ranked) {
            if (t.getDestination() != null) {
                out.put(t.getDestination(), t);
            }
        }
        return out;
    }

    public static HashMap<WorldPoint, Teleport> buildUnfilteredTeleportLinks(WalkOptions options) {
        WalkOptions opts = options != null ? options : WalkOptions.builder().build();
        HashMap<WorldPoint, Teleport> out = new HashMap<>();
        if (!opts.isUseTeleports()) {
            return out;
        }
        WorldPoint me = localPos();
        for (Teleport t : TeleportLoader.getCustomTeleports()) {
            if (!teleportAllowed(t, opts, me, false)) {
                continue;
            }
            if (t.getDestination() != null) {
                out.put(t.getDestination(), t);
            }
        }
        return out;
    }

    public static void setLastPath(TilePath path) {
        lastPath = path;
    }

    public static void setCurrentPath(TilePath path) {
        currentPath = path;
    }

    public static WorldPoint getNearestWalkableTile(WorldPoint source, CollisionMap collisionMap,
                                                    Predicate<WorldPoint> filter) {
        CollisionMap map = collisionMap != null ? collisionMap : GlobalPathfinder.map();
        return GlobalPathfinder.nearestWalkable(source, map, filter);
    }

    public static TilePath buildPath(Collection<WorldPoint> startPoints, List<WorldArea> targetAreas, WalkOptions options,
                                     HashMap<WorldPoint, Teleport> teleports, Map<WorldPoint, List<Transport>> transports) {
        if (targetAreas == null || targetAreas.isEmpty()) {
            return TilePath.empty();
        }
        TilePath best = TilePath.empty();
        for (WorldArea area : targetAreas) {
            if (area == null) {
                continue;
            }
            TilePath path = buildPath(startPoints, area, options, teleports, transports);
            if (path == null || path.isEmpty()) {
                continue;
            }
            if (best.isEmpty() || path.compareTo(best) < 0) {
                best = path;
            }
        }
        return best;
    }

    static boolean isWilderness(WorldPoint p) {
        if (p == null) {
            return false;
        }
        return p.getY() >= 3523 && p.getY() <= 4031 && p.getX() >= 2944 && p.getX() <= 3400;
    }

    private static void applyRun(WalkOptions opts) {
        // Geen blind toggleRun — alleen Auto-run vanaf drempel % (energie-check in tickAutoRun)
        Movement.ensureTravelRun();
    }

    private static boolean needsGraph(WalkOptions opts, WorldArea dest) {
        if (opts == null) {
            return false;
        }
        if (opts.getCollisionMap() != null) {
            return true;
        }
        if (opts.isAvoidWilderness() && dest != null && !isWilderness(dest.toWorldPoint())) {
            return true;
        }
        if (opts.isUseTeleports() && !TeleportLoader.getCustomTeleports().isEmpty()) {
            return true;
        }
        return opts.isUseTransports() && !TransportLoader.getCustomTransports().isEmpty();
    }

    private static List<WorldPoint> resolveStarts(Collection<WorldPoint> startPoints,
                                                  HashMap<WorldPoint, Teleport> teleports,
                                                  WorldArea dest, WalkOptions opts) {
        List<WorldPoint> starts = new ArrayList<>();
        if (startPoints != null) {
            for (WorldPoint s : startPoints) {
                if (s != null) {
                    starts.add(s);
                }
            }
        }
        WorldPoint me = localPos();
        if (starts.isEmpty() && me != null) {
            starts.add(me);
        }
        if (opts.isUseTeleports() && teleports != null && dest != null) {
            WorldPoint destPt = dest.toWorldPoint();
            int walkEst = xyChebyshev(me, destPt);
            for (Map.Entry<WorldPoint, Teleport> e : teleports.entrySet()) {
                WorldPoint land = e.getKey();
                if (land == null || destPt == null) {
                    continue;
                }
                if (xyChebyshev(land, destPt)
                        + net.storm.sdk.movement.pathfinder.F2pSpellTeleports.MIN_SAVE_TILES < walkEst) {
                    starts.add(land);
                }
            }
        }
        return starts;
    }

    private static Predicate<WorldPoint> avoidPredicate(WalkOptions opts, WorldArea dest,
                                                        Collection<WorldPoint> starts) {
        boolean destWild = dest != null && isWilderness(dest.toWorldPoint());
        boolean startWild = isWilderness(localPos());
        if (!startWild && starts != null) {
            for (WorldPoint s : starts) {
                if (isWilderness(s)) {
                    startWild = true;
                    break;
                }
            }
        }
        boolean destMaus = dest != null && NoWalkZones.isEdgevilleMausoleum(dest.toWorldPoint());
        boolean startMaus0 = NoWalkZones.isEdgevilleMausoleum(localPos());
        if (!startMaus0 && starts != null) {
            for (WorldPoint s : starts) {
                if (NoWalkZones.isEdgevilleMausoleum(s)) {
                    startMaus0 = true;
                    break;
                }
            }
        }
        final boolean startMaus = startMaus0;
        boolean avoidWild = opts.isAvoidWilderness() && !destWild && !startWild;
        if (avoidWild) {
            return p -> Walker.isWilderness(p)
                    || MovementHelper.isInLumbridgeBlockedZone(p)
                    || (!destMaus && !startMaus && NoWalkZones.isEdgevilleMausoleum(p));
        }
        if (destMaus || startMaus) {
            return MovementHelper::isInLumbridgeBlockedZone;
        }
        return p -> MovementHelper.isInLumbridgeBlockedZone(p) || NoWalkZones.isEdgevilleMausoleum(p);
    }

    private static WorldPoint areaWalkTarget(WorldArea area) {
        if (area == null) {
            return null;
        }
        WorldPoint me = localPos();
        WorldPoint center = area.toWorldPoint();
        if (me != null && area.contains(me)) {
            return me;
        }
        WorldPoint snapped = GlobalPathfinder.nearestWalkable(center);
        return snapped != null ? snapped : center;
    }

    private static boolean transportAllowed(Transport t, WalkOptions opts) {
        if (t == null) {
            return false;
        }
        if (!PathfinderRequirements.met(t.getRequirements())) {
            if (AlKharidGate.matches(t)) {
                AlKharidGate.logSkip();
            }
            return false;
        }
        if (opts.getRequirements() != null && !PathfinderRequirements.met(opts.getRequirements())) {
            return false;
        }
        String n = t.getName() != null ? t.getName().toLowerCase(Locale.ROOT) : "";
        if (!opts.isUseCharterShips() && n.contains("charter")) {
            return false;
        }
        if (!WalkClickSettings.useShortcuts && WalkClickSettings.isShortcut(t)) {
            return false;
        }
        if (!opts.isUseGnomeGliders() && (n.contains("glider") || n.contains("gnome"))) {
            return false;
        }
        return opts.isUseMagicCarpets() || !n.contains("carpet");
    }

    private static boolean teleportAllowed(Teleport t, WalkOptions opts, WorldPoint me, boolean destFilter) {
        if (t == null || t.getDestination() == null) {
            return false;
        }
        if (t.isHomeTeleport() && !opts.isUseHomeTeleports()) {
            return false;
        }
        if (t.isMinigameTeleport() && !opts.isUseMinigameTeleports()) {
            return false;
        }
        if (t.isPoh() && !opts.isUsePoh()) {
            return false;
        }
        if (!PathfinderRequirements.met(t.getRequirements())) {
            return false;
        }
        if (destFilter && me != null && !opts.isForceLoad()
                && me.getPlane() == t.getDestination().getPlane()
                && xyChebyshev(me, t.getDestination()) < TELEPORT_SKIP_NEAR) {
            return false;
        }
        return true;
    }

    private static boolean tryTeleportIfNeeded(TilePath path, WorldPoint me) {
        if (path.getTeleports().isEmpty() || path.isEmpty()) {
            return false;
        }
        WorldPoint first = path.get(0);
        if (first == null || (me.getPlane() == first.getPlane() && xyChebyshev(me, first) <= 12)) {
            return false;
        }
        for (Teleport t : path.getTeleports()) {
            if (t.getDestination() != null && t.getDestination().distanceTo(first) <= 4) {
                logWalk("teleport → " + t.getDestination());
                return t.execute();
            }
        }
        return false;
    }

    private static int xyChebyshev(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    private static Collection<Transport> flatten(Map<WorldPoint, List<Transport>> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }
        List<Transport> out = new ArrayList<>();
        for (List<Transport> list : map.values()) {
            if (list != null) {
                out.addAll(list);
            }
        }
        return out;
    }

    private static void remember(TilePath path, String cacheKey) {
        lastPath = path;
        if (cacheKey != null && path != null && !path.isEmpty() && !path.isIncomplete()) {
            CACHE.put(cacheKey, path);
        }
    }

    private static String cacheKey(List<WorldPoint> starts, WorldArea dest, WalkOptions opts) {
        WorldPoint s = starts.get(0);
        WorldPoint d = dest.toWorldPoint();
        return s.getX() + "," + s.getY() + "," + s.getPlane()
                + ">" + d.getX() + "," + d.getY() + "," + d.getPlane()
                + "|w" + opts.isAvoidWilderness()
                + "|t" + opts.isUseTransports()
                + "|e" + opts.isUseTeleports();
    }

    private static WorldPoint localPos() {
        Players.LocalSnap snap = Players.snapshotLocal();
        return snap != null && snap.present ? snap.worldLocation : null;
    }

    private static void logWalk(String detail) {
        long now = System.currentTimeMillis();
        if (detail.equals(lastLog) && now - lastLogMs < CONSOLE_THROTTLE_MS) {
            return;
        }
        lastLog = detail;
        lastLogMs = now;
        BotRuntime.logConsole("[Walk/path] " + detail);
        log.info("[Walker] {}", detail);
    }

    private static final class Api implements IWalker {
        @Override
        public void walk(WorldPoint worldPoint) {
            Movement.walk(worldPoint);
        }

        @Override
        public boolean walkTo(WorldArea destination, CollisionMap collisionMap, boolean useTeleports) {
            return Walker.walkTo(destination, collisionMap, useTeleports);
        }

        @Override
        public boolean walkTo(WorldArea destination, WalkOptions options) {
            return Walker.walkTo(destination, options);
        }

        @Override
        public TilePath getCurrentPath() {
            return Walker.getCurrentPath();
        }

        @Override
        public TilePath getLastPath() {
            return Walker.getLastPath();
        }

        @Override
        public void setLastPath(TilePath path) {
            Walker.setLastPath(path);
        }

        @Override
        public void setCurrentPath(TilePath path) {
            Walker.setCurrentPath(path);
        }

        @Override
        public TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, WalkOptions options,
                                  HashMap<WorldPoint, Teleport> teleports, Map<WorldPoint, List<Transport>> transports) {
            return Walker.buildPath(startPoints, destination, options, teleports, transports);
        }

        @Override
        public TilePath buildPath(Collection<WorldPoint> startPoints, List<WorldArea> targetAreas, WalkOptions options,
                                  HashMap<WorldPoint, Teleport> teleports, Map<WorldPoint, List<Transport>> transports) {
            return Walker.buildPath(startPoints, targetAreas, options, teleports, transports);
        }

        @Override
        public boolean walkAlong(WorldArea destination, TilePath path, Map<WorldPoint, List<Transport>> transports,
                                 WalkOptions options) {
            return Walker.walkAlong(destination, path, transports, options);
        }

        @Override
        public Map<WorldPoint, List<Transport>> buildTransportLinks(WalkOptions options) {
            return Walker.buildTransportLinks(options);
        }

        @Override
        public LinkedHashMap<WorldPoint, Teleport> buildTeleportLinks(WorldArea destination, WalkOptions options) {
            return Walker.buildTeleportLinks(destination, options);
        }

        @Override
        public LinkedHashMap<WorldPoint, Teleport> buildExperimentalTeleportLinks(WorldArea destination, WalkOptions options) {
            return Walker.buildExperimentalTeleportLinks(destination, options);
        }

        @Override
        public HashMap<WorldPoint, Teleport> buildUnfilteredTeleportLinks(WalkOptions options) {
            return Walker.buildUnfilteredTeleportLinks(options);
        }

        @Override
        public WorldPoint getNearestWalkableTile(WorldPoint source, CollisionMap collisionMap, Predicate<WorldPoint> filter) {
            return Walker.getNearestWalkableTile(source, collisionMap, filter);
        }
    }
}
