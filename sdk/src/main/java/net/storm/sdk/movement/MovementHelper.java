package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.api.movement.WalkOptions;
import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.tiles.NoWalkZones;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.pathfinder.AlKharidGate;
import net.storm.sdk.movement.pathfinder.F2pSpellTeleports;
import net.storm.sdk.movement.pathfinder.GlobalPathfinder;
import net.storm.sdk.movement.pathfinder.Pathfinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Enige pathfind-walk voor scripts — Storm {@code Walker.buildPath} + {@link TilePath#walk()}.
 * Collision-map + F2P transports zoals [Shortest Path](https://github.com/Skretzo/shortest-path).
 * <p>
 * Storm kent twee lagen (niet meer): {@code walk} = klik zonder pad ({@link Movement});
 * {@code walkTo} = collision-pad bouwen, daarna {@link TilePath#walk()}. Scripts gebruiken
 * alleen deze helper. Lange reizen (doel buiten scene of &gt; {@link #LOCAL_RANGE} tegels)
 * negeren excluded tiles; lokale skill-loops houden ze.
 */
public final class MovementHelper {

    private static final Logger log = LoggerFactory.getLogger(MovementHelper.class);

    private static final int LB_CASTLE_X_MIN = 3203;
    private static final int LB_CASTLE_X_MAX = 3213;
    /** Noord van de zuid-courtyard (3208–3213). Courtyard + trap-aanloop blijven loopbaar. */
    private static final int LB_CASTLE_Y_MIN = 3215;
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

    /** Storm-achtig: lokaal = excluded tiles; verder = alleen collision + Lumbridge-zones. */
    private static final int LOCAL_RANGE = 32;
    private static final long STALL_MS = 12_000L;
    /** Geen vlag + niet moving: snel opnieuw klikken (niet 7s wachten). */
    private static final long STALL_IDLE_MS = 700L;
    private static final long CONSOLE_THROTTLE_MS = 1_500L;

    private static volatile TilePath activePath;
    private static volatile WorldPoint activeDest;
    /**
     * Origineel Lumb bank-doel (p2 booth) terwijl A* naar courtyard-approach loopt.
     * Na Climb: promote i.p.v. Bottom-floor terug naar approach.
     */
    private static volatile WorldPoint lumbUpperGoal;
    private static volatile boolean activeTravel;
    /** Travel (ver / off-scene): geen excluded-tiles — anders jungle-route onmogelijk. */
    /** Travel (ver / off-scene): geen excluded-tiles — anders jungle-route onmogelijk. */
    private static final ThreadLocal<Boolean> TRAVEL = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** LoopHost.tickActive heeft deze cycle al gelopen — script-walkTo niet nog eens muis/1.5s. */
    private static volatile boolean walkedThisCycle;

    private static volatile WorldPoint stallDest;
    private static volatile int stallBestDist = Integer.MAX_VALUE;
    private static volatile long stallProgressMs;
    private static volatile int stallReclears;
    private static volatile String lastWalkLog = "";
    private static volatile long lastWalkLogMs;

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
        lumbUpperGoal = null;
        activeTravel = false;
        BotRuntime.debugPath = null;
        BotRuntime.debugTarget = null;
        TravelPathSearch.cancel();
        TilePathWalker.resetSession();
        resetStall();
    }

    public static TilePath getActivePath() {
        return activePath;
    }

    /** Huidig walk-doel (voor AntiBan proximity e.d.). */
    public static WorldPoint getActiveDestination() {
        return activeDest;
    }

    /** Lange reis (boot, bank, off-scene) — geen canvas-hover, invoke eerst. */
    public static boolean isTravelContext() {
        if (Boolean.TRUE.equals(TRAVEL.get()) || activeTravel) {
            return true;
        }
        WorldPoint from = localPos();
        WorldPoint dest = activeDest;
        if (from == null || dest == null) {
            return false;
        }
        if (from.getPlane() != dest.getPlane()) {
            return true;
        }
        return from.distanceTo(dest) > LOCAL_RANGE;
    }

    /**
     * Al onderweg naar {@code dest}: niet opnieuw walkTo/muis-pad starten.
     * Recent klik / moving telt ook als het pad-doel ietwat gesnapt is (NPC vs stand-tegel).
     */
    public static boolean alreadyEnRoute(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        try {
            if (net.storm.sdk.input.Mouse.isBusy()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        WorldPoint active = activeDest;
        boolean sameTrip = active != null
                && active.getPlane() == dest.getPlane()
                && active.distanceTo(dest) <= 24;
        if (!sameTrip) {
            return false;
        }
        // Alleen wachten zolang de gele flag nog ver is. Dichtbij → TilePathWalker mag doorlinken.
        // Niet: “flag bestaat of moving → skip” (dat is WC-scriptgedrag, niet de walker).
        try {
            WorldPoint from = localPos();
            WorldPoint flag = Movement.getDestination();
            if (flag != null && WalkClickSettings.shouldWaitNearFlag(from, flag, dest)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return WalkClickHelper.millisSinceLastClick() < 350L;
    }

    /** Build (or reuse) the full tile path to {@code dest}. */
    public static TilePath getPath(WorldPoint dest) {
        try {
            return computePath(dest);
        } catch (RuntimeException e) {
            log.warn("[MovementHelper] getPath mislukt: {}", e.toString());
            clearPath();
            return TilePath.empty();
        }
    }

    private static TilePath computePath(WorldPoint dest) {
        if (dest == null) {
            return TilePath.empty();
        }
        WorldPoint from = localPos();
        if (from == null) {
            return TilePath.empty();
        }
        if (isBoatRequiredGap(from, dest)) {
            walkLog("FAIL boot-nodig " + fmt(from) + " → " + fmt(dest));
            return TilePath.empty();
        }
        if (AlKharidGate.crossesGateWithoutPass(from, dest)) {
            AlKharidGate.logDetour();
        }

        WorldPoint target = (activeDest != null && sameWalkDest(activeDest, dest))
                ? activeDest
                : snapWalkTarget(from, dest, false);
        WorldPoint lumb = LumbridgeStairsHelper.pathTarget(from, dest);
        if (lumb != null && lumb != dest) {
            target = lumb;
        }

        boolean travel = Boolean.TRUE.equals(TRAVEL.get()) || isTravel(from, target);
        Boolean prevTravel = TRAVEL.get();
        TRAVEL.set(travel);

        TilePath cached = activePath;
        WorldPoint cachedDest = activeDest;
        if (pathUsesBlockedAlKharid(cached)) {
            AlKharidGate.logSkip();
            activePath = null;
            cached = null;
            cachedDest = null;
        }
        if (cached != null && !cached.isEmpty() && cachedDest != null
                && sameWalkDest(cachedDest, target)) {
            TilePath remaining = remainingOrTeleFull(cached, from);
            if (!remaining.isEmpty() && !incompletePrefixDone(cached, from, target)
                    && !incompletePrefixDone(remaining, from, target)) {
                if (travel) {
                    TilePath bg = TravelPathSearch.poll(target);
                    if (isBetterTravelPath(bg, from, target)) {
                        storePath(bg, target, true);
                        remaining = remainingOrTeleFull(bg, from);
                        walkLog("path achtergrond tiles=" + bg.size() + " → " + fmt(target)
                                + " endD=" + (bg.get(bg.size() - 1) != null
                                ? bg.get(bg.size() - 1).distanceTo(target) : -1));
                    }
                }
                activePath = remaining;
                TRAVEL.set(prevTravel);
                return remaining;
            }
        }

        if (travel) {
            TilePath travelPath = travelPath(from, target, cached, cachedDest);
            TRAVEL.set(prevTravel);
            return travelPath != null ? travelPath : TilePath.empty();
        }

        try {
            TilePath next = computePath0(from, target, travel);
            if (next != null && !next.isEmpty()) {
                return next;
            }
            if (cached != null && !cached.isEmpty() && sameWalkDest(cachedDest, target)
                    && !pathUsesBlockedAlKharid(cached)) {
                walkLog("BFS leeg — houd vorig pad naar " + fmt(cachedDest));
                TRAVEL.set(prevTravel);
                return cached;
            }
            return next != null ? next : TilePath.empty();
        } finally {
            TRAVEL.set(prevTravel);
        }
    }

    private static TilePath travelPath(WorldPoint from, WorldPoint target, TilePath cached, WorldPoint cachedDest) {
        Collection<Transport> transports = Walker.listTransports();
        TilePath bg = TravelPathSearch.poll(target);
        if (isBetterTravelPath(bg, from, target)) {
            storePath(bg, target, true);
            walkLog("path achtergrond tiles=" + bg.size() + " → " + fmt(target)
                    + " endD=" + (bg.get(bg.size() - 1) != null
                    ? bg.get(bg.size() - 1).distanceTo(target) : -1));
            TilePath rem = remainingOrTeleFull(bg, from);
            if (!rem.isEmpty() && !incompletePrefixDone(bg, from, target)
                    && !incompletePrefixDone(rem, from, target)) {
                activePath = rem;
                return rem;
            }
        }
        if (cached != null && !cached.isEmpty() && sameWalkDest(cachedDest, target)) {
            TilePath rem = remainingOrTeleFull(cached, from);
            if (!rem.isEmpty() && !incompletePrefixDone(cached, from, target)
                    && !incompletePrefixDone(rem, from, target)) {
                TravelPathSearch.ensure(from, target, transports);
                return rem;
            }
        }
        TravelPathSearch.ensure(from, target, transports);
        TilePath quick = quickStartPath(from, target);
        if (quick != null && !quick.isEmpty()) {
            storePath(quick, target, true);
            walkLog("quick-start tiles=" + quick.size() + " → " + fmt(target)
                    + (quick.isIncomplete() ? " (A* achtergrond)" : ""));
            TilePath rem = remainingOrTeleFull(quick, from);
            if (!rem.isEmpty()) {
                activePath = rem;
                return rem;
            }
        }
        if (TravelPathSearch.isBusy()) {
            refreshStallWhileWaiting();
            BotRuntime.heartbeat();
            walkLog("A* bezig — geen scene-prefix → " + fmt(target));
            return TilePath.empty();
        }
        bg = TravelPathSearch.poll(target);
        if (isBetterTravelPath(bg, from, target)) {
            storePath(bg, target, true);
            TilePath rem = remainingOrTeleFull(bg, from);
            if (!rem.isEmpty()) {
                activePath = rem;
                walkLog("path A* klaar tiles=" + bg.size() + " → " + fmt(target));
                return rem;
            }
        }
        walkLog("FAIL travel-path empty " + fmt(from) + " → " + fmt(target)
                + " d=" + from.distanceTo(target));
        return TilePath.empty();
    }

    /** Eerste hop ≤2s: korte A* of scene-BFS richting doel terwijl achtergrond-A* loopt. */
    private static TilePath quickStartPath(WorldPoint from, WorldPoint target) {
        if (from == null || target == null) {
            return TilePath.empty();
        }
        Collection<Transport> transports = Walker.listTransports();
        try {
            F2pSpellTeleports.ensureRegistered();
            java.util.List<WorldPoint> starts = F2pSpellTeleports.startsFor(from, target);
            TilePath fast = GlobalPathfinder.findPath(
                    starts, target.toWorldArea(), GlobalPathfinder.map(),
                    MovementHelper::travelAvoidTiles, transports, 450L);
            if (fast != null && !fast.isEmpty() && !isGreedyInScenePrefix(fast, target)
                    && !pathUsesBlockedAlKharid(fast)) {
                F2pSpellTeleports.attachIfUsed(fast, from);
                return fast;
            }
        } catch (RuntimeException e) {
            log.debug("[MovementHelper] quick A*: {}", e.toString());
        }
        List<WorldPoint> scene = Pathfinder.findPathToward(from, target, MovementHelper::travelAvoidTiles);
        if (scene == null || scene.size() < 2) {
            return TilePath.empty();
        }
        return new TilePath(scene, target.toWorldArea(), scene.size(), true);
    }

    private static boolean isBetterTravelPath(TilePath candidate, WorldPoint from, WorldPoint dest) {
        if (candidate == null || candidate.isEmpty() || dest == null || from == null) {
            return false;
        }
        if (candidate.isIncomplete()) {
            return false;
        }
        if (isGreedyInScenePrefix(candidate, dest) || pathUsesBlockedAlKharid(candidate)) {
            return false;
        }
        if (F2pSpellTeleports.isTelePathStart(candidate, from)) {
            return true;
        }
        TilePath rem = candidate.getRemainingPath(from);
        return !rem.isEmpty();
    }

    /** Tele-pad niet in stukken snijden tot je bij de hub landt. */
    private static TilePath remainingOrTeleFull(TilePath path, WorldPoint from) {
        if (path == null || path.isEmpty()) {
            return path != null ? path : TilePath.empty();
        }
        F2pSpellTeleports.dropIfAlreadyWalking(path, from);
        if (F2pSpellTeleports.isTelePathStart(path, from)) {
            return path;
        }
        TilePath rem = path.getRemainingPath(from);
        F2pSpellTeleports.dropIfAlreadyWalking(rem, from);
        return rem != null && !rem.isEmpty() ? rem : path;
    }

    /**
     * [Shortest Path](https://github.com/Skretzo/shortest-path) + Storm Walker.buildPath:
     * globale collision-map + F2P gate/stile transports. Geen scene-BFS over water.
     */
    private static final WalkOptions SHORTEST_PATH_OPTIONS = WalkOptions.builder()
            .useTransports(true)
            .useTeleports(true)
            .useHomeTeleports(true)
            .useCache(true)
            .avoidWilderness(true)
            .build();

    private static TilePath computePath0(WorldPoint from, WorldPoint target, boolean travel) {
        TRAVEL.set(true);
        F2pSpellTeleports.ensureRegistered();
        TilePath path = Walker.buildPath(
                F2pSpellTeleports.startsFor(from, target),
                target.toWorldArea(),
                SHORTEST_PATH_OPTIONS);
        F2pSpellTeleports.attachIfUsed(path, from);
        if (path != null && !path.isEmpty() && !isGreedyInScenePrefix(path, target)
                && !pathUsesBlockedAlKharid(path)) {
            storePath(path, target, true);
            WorldPoint end = path.get(path.size() - 1);
            int endD = end != null ? end.distanceTo(target) : -1;
            walkLog("path shortest-path tiles=" + path.size() + " → " + fmt(target)
                    + " endD=" + endD
                    + viaNote(path, path.getTransports() != null ? path.getTransports().size() : 0)
                    + (path.isIncomplete() ? " incomplete" : ""));
            return path;
        }
        if (path != null && !path.isEmpty()) {
            walkLog("weiger greedy prefix end="
                    + fmt(path.get(path.size() - 1)) + " dest=" + fmt(target)
                    + " (doel in scene, geen complete route)");
        }
        Collection<Transport> transports = Walker.listTransports();
        TilePath snapped = retryFromNearbyStart(from, target, transports, true);
        if (snapped != null && !snapped.isEmpty() && !isGreedyInScenePrefix(snapped, target)) {
            return snapped;
        }
        walkLog("FAIL shortest-path empty " + fmt(from) + " → " + fmt(target)
                + " d=" + from.distanceTo(target));
        return TilePath.empty();
    }

    /**
     * Storm {@code walkTo}: collision-{@link TilePath} bouwen, daarna {@link TilePath#walk()}.
     * Enige walk die scripts nodig hebben (Imp/WC/Fish/bank).
     */
    public static boolean walkTo(WorldPoint dest) {
        return walkTo(dest, false);
    }

    /** Doorgaan op het actieve pad — LoopHost, niet wachten op script-tick. */
    public static void beginLoopTick() {
        walkedThisCycle = false;
    }

    public static void tickActive() {
        WorldPoint d = activeDest;
        if (d == null) {
            return;
        }
        walkTo(d, false);
        walkedThisCycle = true;
    }

    /**
     * @deprecated Zelfde als {@link #walkTo(WorldPoint)} — geen tweede walker.
     */
    @Deprecated
    public static boolean walkToTravel(WorldPoint dest) {
        return walkTo(dest, false);
    }

    /** Stapvoets lopen — alleen gebruiken als een script dat expliciet wil. */
    public static boolean walkToWalking(WorldPoint dest) {
        WalkClickSettings.setForceWalk(true);
        try {
            return walkTo(dest, false);
        } finally {
            WalkClickSettings.setForceWalk(false);
        }
    }

    public static boolean walkTo(WorldPoint dest, boolean forceDining) {
        if (dest == null) {
            return false;
        }
        if (walkedThisCycle && sameWalkDest(activeDest, dest)) {
            return true;
        }
        try {
            return walkTo0(dest, forceDining);
        } catch (IllegalStateException e) {
            log.warn("[MovementHelper] walkTo client-thread: {}", e.toString());
            walkLog("FAIL exception " + e);
            try {
                return walkTo0(dest, forceDining);
            } catch (RuntimeException retry) {
                log.warn("[MovementHelper] walkTo retry: {}", retry.toString());
                return false;
            }
        } catch (RuntimeException e) {
            log.warn("[MovementHelper] walkTo mislukt: {}", e.toString());
            walkLog("FAIL exception " + e);
            clearPath();
            return false;
        }
    }

    private static boolean walkTo0(WorldPoint dest, boolean forceDining) {
        try {
            Movement.ensureTravelRun();
        } catch (Throwable ignored) {
        }
        WorldPoint from = localPos();
        if (from == null) {
            walkLog("FAIL geen speler");
            return false;
        }
        dest = resolveLumbDest(from, dest);
        if (dest == null) {
            return false;
        }
        if (from.distanceTo(dest) <= 0 && from.getPlane() == dest.getPlane()) {
            clearPath();
            resetStall();
            return true;
        }
        WorldPoint pathDest = LumbridgeStairsHelper.pathTarget(from, dest);
        WorldPoint prevDest = activeDest;
        WorldPoint target;
        if (prevDest != null && dest != null && sameWalkDest(prevDest, dest)
                && !LumbridgeStairsHelper.needsGroundApproach(from, dest)) {
            target = prevDest;
        } else if (prevDest != null && LumbridgeStairsHelper.sameGroundApproach(prevDest, dest, from)) {
            target = pathDest;
        } else {
            target = snapWalkTarget(from, pathDest, forceDining);
        }
        if (LumbridgeStairsHelper.needsGroundApproach(from, dest)
                && (target == null || target.getPlane() != 0
                || !LumbridgeStairsHelper.isGroundApproachTile(target))) {
            target = LumbridgeStairsHelper.pathApproach();
        }
        // Vergelijk gesnapte dest (ster/boom is geblokkeerd). Script-tegel vs snap >8
        // wist het pad elke hop → 1 klik + stilstand (Star), Imp-dock niet.
        if (prevDest != null && target != null
                && !LumbridgeStairsHelper.sameGroundApproach(prevDest, dest, from)
                && !sameWalkDest(prevDest, dest)
                && (prevDest.getPlane() != target.getPlane() || prevDest.distanceTo(target) > 8)) {
            walkLog("nieuw doel " + fmt(prevDest) + " → " + fmt(target) + " — oud pad weg");
            clearPathKeepLumbGoal();
        }
        if (LumbridgeStairsHelper.needsGroundApproach(from, dest)
                && (prevDest == null || !sameWalkDest(prevDest, target))) {
            walkLog("Lumb bank p" + dest.getPlane() + " → eerst courtyard "
                    + fmt(target != null ? target : LumbridgeStairsHelper.SOUTH_APPROACH));
        }
        if (AlKharidGate.progressForWalk(from, dest)) {
            return true;
        }
        // Niet skippen bij gele flag / moving — TilePathWalker moet doorlinken
        // zoals WorldWalker.tick(). alreadyEnRoute hier = wachten tot stilstand.
        // Muis bezet: niet afbreken — WalkClickHelper invoke't zonder muis.

        if (LumbridgeStairsHelper.isLumbridgeUpperDest(dest)
                && from.getPlane() != dest.getPlane()
                && LumbridgeStairsHelper.canClimbNow(from)) {
            if (PlaneChangeHelper.progressTowardPlane(dest)) {
                walkLog("plane " + from.getPlane() + " → " + dest.getPlane() + " (Lumb courtyard/trap)");
                return true;
            }
            walkLog("Lumb bij trap — Climb retry, geen omloop");
            return true;
        }

        // Andere plane: A* + stair-transports. Climb alleen als we al bij de trap staan.
        if (from.getPlane() != dest.getPlane()
                && !LumbridgeStairsHelper.needsGroundApproach(from, dest)
                && Math.abs(from.getX() - dest.getX()) <= 12
                && Math.abs(from.getY() - dest.getY()) <= 12) {
            if (PlaneChangeHelper.progressTowardPlane(dest)) {
                walkLog("plane " + from.getPlane() + " → " + dest.getPlane());
                return true;
            }
        }

        // Surface ↔ dungeon (Y±6400) — niet Lumb-kasteel (dat is PlaneChange Climb-up)
        if (GlobalPathfinder.isLikelyInstanceGap(from, dest)
                && !LumbridgeStairsHelper.isLumbridgeUpperDest(dest)
                && !LumbridgeStairsHelper.needsGroundApproach(from, dest)) {
            if (DungeonClimbHelper.progress(from, dest)) {
                walkLog("dungeon-climb " + fmt(from) + " → " + fmt(dest));
                return true;
            }
        }

        if (isBoatRequiredGap(from, dest)) {
            walkLog("FAIL boot-nodig " + fmt(from) + " → " + fmt(dest));
            clearPath();
            return false;
        }

        boolean travel = isTravel(from, target);
        TRAVEL.set(travel);
        try {
            noteProgress(from, target);
            TilePath path = getPath(target);
            if (path != null && !path.isEmpty()) {
                WorldPoint end = path.get(path.size() - 1);
                WorldPoint snapped = activeDest != null ? activeDest : target;
                if (isGreedyInScenePrefix(path, snapped)) {
                    walkLog("weiger pad (greedy/incomplete in scene) end="
                            + fmt(end) + " dest=" + fmt(snapped));
                    clearPath();
                    return false;
                }
                if (path.isIncomplete()) {
                    walkLog("BFS timeout-prefix tot " + fmt(end) + " dest=" + fmt(snapped)
                            + " — niet het volledige pad, loop prefix daarna verder");
                }
                boolean clicked = path.walk();
                if (clicked) {
                    return true;
                }
                Players.LocalSnap me = Players.snapshotLocal();
                return me != null && me.present && me.moving;
            }
        if (TravelPathSearch.isBusy()) {
            TilePath prefix = quickStartPath(from, target);
            if (prefix != null && !prefix.isEmpty()) {
                boolean clicked = prefix.walk();
                if (clicked) {
                    return true;
                }
            }
            refreshStallWhileWaiting();
            BotRuntime.heartbeat();
            return true;
        }
            // Dest in scene zonder pad = water/muur/poort. Nooit “dichtst bij dest”
            // (dat is de Lumbridge-rivier: lopen naar de oever + klik over water).
            walkLog("geen pad — geen greedy cut over water/muur naar " + fmt(target));
            return false;
        } finally {
            TRAVEL.set(false);
        }
    }

    /** True terwijl achtergrond-A* nog loopt — walkTo telt dit niet als fail. */
    public static boolean isWaitingForTravelPath() {
        return TravelPathSearch.isBusy();
    }

    private static void refreshStallWhileWaiting() {
        stallProgressMs = System.currentTimeMillis();
    }

    /**
     * BFS-timeout-prefix is op: speler bij laatste prefix-tegel, echt doel nog ver.
     * Dan opnieuw zoeken vanaf hier — niet “aangekomen” / dezelfde stub herhalen.
     */
    static boolean incompletePrefixDone(TilePath path, WorldPoint from, WorldPoint dest) {
        if (path == null || !path.isIncomplete() || path.isEmpty() || from == null || dest == null) {
            return false;
        }
        WorldPoint end = path.get(path.size() - 1);
        if (end == null || end.getPlane() != from.getPlane() || dest.getPlane() != from.getPlane()) {
            return false;
        }
        if (end.distanceTo(dest) <= 8) {
            return false;
        }
        return from.distanceTo(end) <= 4;
    }

    /**
     * Incomplete BFS die crow-flies dichter bij dest komt (rivieroever) is geen route.
     * Alleen accepteren als dest buiten de scene ligt (echte travel-prefix).
     */
    static boolean isGreedyInScenePrefix(TilePath path, WorldPoint target) {
        if (path == null || path.isEmpty() || target == null) {
            return false;
        }
        WorldPoint end = path.get(path.size() - 1);
        if (end == null) {
            return true;
        }
        if (!path.isIncomplete() && end.distanceTo(target) <= 8) {
            return false;
        }
        if (!Pathfinder.isInLoadedScene(target)) {
            return false;
        }
        return path.isIncomplete() || end.distanceTo(target) > 8;
    }

    /**
     * Musa/Karamja vs Asgarnia (Port Sarim): collision-map heeft geen pad over water.
     * Geen 1-tegel scene-hop richting de overkant.
     */
    public static boolean isBoatRequiredGap(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null || from.getPlane() != dest.getPlane()) {
            return false;
        }
        return isMusaKaramjaIsland(from) != isMusaKaramjaIsland(dest);
    }

    /**
     * Lumb bank-trip: onthoud booth tijdens courtyard-approach; promote na Climb;
     * geen terug-omhoog na Bottom-floor wanneer we de bank verlaten.
     */
    private static WorldPoint resolveLumbDest(WorldPoint from, WorldPoint dest) {
        if (dest == null) {
            return null;
        }
        if (LumbridgeStairsHelper.isLumbridgeBankBoothDest(dest)) {
            lumbUpperGoal = dest;
        } else if (dest.getPlane() == 0 && !LumbridgeStairsHelper.isLumbridgeUpperDest(dest)
                && !LumbridgeStairsHelper.isGroundApproachTile(dest)) {
            lumbUpperGoal = null;
        }

        if (from.getPlane() >= 1
                && LumbridgeStairsHelper.isGroundApproachTile(dest)
                && lumbUpperGoal != null) {
            walkLog("Lumb p" + from.getPlane() + " — approach → bank " + fmt(lumbUpperGoal));
            clearPathKeepLumbGoal();
            return lumbUpperGoal;
        }

        if (from.getPlane() == 0
                && dest.getPlane() >= 1
                && LumbridgeStairsHelper.isLumbridgeUpperDest(dest)
                && lumbUpperGoal == null) {
            walkLog("Lumb p0 — stale upper dest " + fmt(dest) + " weg (geen bank-trip)");
            clearPath();
            return null;
        }
        return dest;
    }

    private static void clearPathKeepLumbGoal() {
        WorldPoint keep = lumbUpperGoal;
        clearPath();
        lumbUpperGoal = keep;
    }

    /**
     * Zelfde reis-doel. 8 tegels = zelfde drempel als pad-clear (ster-snap vs object-tegel).
     * Niet bank-pad hergebruiken voor yews (die liggen verder).
     */
    private static boolean sameWalkDest(WorldPoint a, WorldPoint b) {
        return a != null && b != null
                && a.getPlane() == b.getPlane()
                && a.distanceTo(b) <= 8;
    }

    /** Loopbare stand-tegel: scene eerst, anders collision-map (off-scene ster/bank). */
    private static WorldPoint snapWalkTarget(WorldPoint from, WorldPoint dest, boolean forceDining) {
        if (dest == null) {
            return null;
        }
        WorldPoint target = dest;
        if (!forceDining && isInsideLumbridgeDiningRoom(dest)) {
            target = LB_WAYPOINT_SOUTH;
        }
        WorldPoint walkable = null;
        if (from != null && from.getPlane() == target.getPlane() && from.distanceTo(target) <= LOCAL_RANGE) {
            walkable = Pathfinder.nearestWalkableStand(target);
        }
        if (walkable == null) {
            walkable = GlobalPathfinder.nearestWalkable(target);
        }
        if (walkable != null) {
            if (walkable.distanceTo(target) > 0 || walkable.getPlane() != target.getPlane()) {
                log.info("[MovementHelper] dest snap {} → {}", target, walkable);
            }
            return walkable;
        }
        return target;
    }

    private static boolean isMusaKaramjaIsland(WorldPoint p) {
        if (p == null) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        return x >= 2810 && x <= 2970 && y >= 3125 && y <= 3220;
    }

    private static boolean isTravel(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null) {
            return false;
        }
        if (from.getPlane() != dest.getPlane()) {
            return true;
        }
        if (GlobalPathfinder.isLikelyInstanceGap(from, dest)) {
            return true;
        }
        // In de geladen scene: meteen shortest-path klikken (niet 15s wachten op A*).
        if (Pathfinder.isInLoadedScene(dest)) {
            return false;
        }
        return from.distanceTo(dest) > LOCAL_RANGE;
    }

    private static boolean pathUsesBlockedAlKharid(TilePath path) {
        if (path == null) {
            return false;
        }
        List<Transport> ts = path.getTransports();
        if (ts == null || ts.isEmpty()) {
            return false;
        }
        boolean hasGate = false;
        for (Transport t : ts) {
            if (AlKharidGate.matches(t)) {
                hasGate = true;
                break;
            }
        }
        return hasGate && !AlKharidGate.canPass();
    }

    /** Walker/Shortest Path zette dit pad — overlay + alreadyEnRoute. */
    public static void adoptWalkerPath(TilePath path, WorldPoint dest) {
        if (path == null || path.isEmpty()) {
            return;
        }
        WorldPoint d = dest != null ? dest : path.getDestination();
        storePath(path, d, true);
    }

    private static void storePath(TilePath path, WorldPoint dest, boolean travel) {
        activePath = path;
        activeDest = dest;
        activeTravel = travel;
        BotRuntime.debugPath = path;
        BotRuntime.debugTarget = dest;
    }

    private static void noteProgress(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null) {
            return;
        }
        int d = from.distanceTo(dest);
        long now = System.currentTimeMillis();
        boolean sameDest = stallDest != null
                && stallDest.distanceTo(dest) <= 3
                && stallDest.getPlane() == dest.getPlane();
        if (!sameDest) {
            stallDest = dest;
            stallBestDist = d;
            stallProgressMs = now;
            stallReclears = 0;
            return;
        }
        if (d < stallBestDist) {
            stallBestDist = d;
            stallProgressMs = now;
            stallReclears = 0;
            return;
        }
        Players.LocalSnap stallMe = null;
        try {
            if (WalkClickHelper.millisSinceLastClick() < 400L) {
                stallProgressMs = now;
                return;
            }
            stallMe = Players.snapshotLocal();
            boolean moving = stallMe != null && stallMe.present && stallMe.moving;
            WorldPoint flag = stallMe != null ? stallMe.walkDestination : null;
            if (flag != null || moving) {
                stallProgressMs = now;
                return;
            }
            if (net.storm.sdk.input.Mouse.isBusy()) {
                stallProgressMs = now;
                return;
            }
        } catch (Throwable ignored) {
        }
        long stuck = now - stallProgressMs;
        boolean idle = stallMe == null
                || (!stallMe.moving && stallMe.walkDestination == null);
        long limit = idle ? STALL_IDLE_MS : STALL_MS;
        if (stuck < limit) {
            return;
        }
        stallProgressMs = now;
        stallReclears++;
        boolean prefixStuck = incompletePrefixDone(activePath, from, dest)
                || (activePath != null && activePath.isIncomplete() && stallReclears >= 3
                && !TravelPathSearch.isBusy());
        String movingFlag = stallMe != null
                ? (" moving=" + stallMe.moving + " flag=" + fmt(stallMe.walkDestination))
                : " moving=? flag=-";
        if (prefixStuck) {
            walkLog("STALL " + (stuck / 1000) + "s d=" + d + " best=" + stallBestDist
                    + " reclear=" + stallReclears
                    + movingFlag
                    + " dest=" + fmt(dest)
                    + " — incomplete prefix, rebuild");
            clearPath();
            return;
        }
        walkLog("STALL " + (stuck / 1000) + "s d=" + d + " best=" + stallBestDist
                + " reclear=" + stallReclears
                + movingFlag
                + " dest=" + fmt(dest)
                + " — pad behouden (Storm)");
        // Storm wist het pad niet; opnieuw klikken op hetzelfde pad.
    }

    public static void resetStallTracking() {
        resetStall();
    }

    private static void resetStall() {
        stallDest = null;
        stallBestDist = Integer.MAX_VALUE;
        stallProgressMs = 0L;
        stallReclears = 0;
    }

    private static void walkLog(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastWalkLog) && now - lastWalkLogMs < CONSOLE_THROTTLE_MS) {
            return;
        }
        lastWalkLog = msg;
        lastWalkLogMs = now;
        log.info("[Walk] {}", msg);
        BotRuntime.logConsole("[Walk] " + msg);
    }

    private static String fmt(WorldPoint p) {
        return p == null ? "-" : (p.getX() + "," + p.getY());
    }

    private static String viaNote(TilePath path, int avail) {
        int n = path != null && path.getTransports() != null ? path.getTransports().size() : 0;
        return " via=" + n + "/" + avail;
    }

    /**
     * Speler staat soms op een tegel zonder uitgangen in de collision-map (na hop / object).
     * Probeer BFS vanaf een buurtegel die wél walkable is.
     */
    private static TilePath retryFromNearbyStart(
            WorldPoint from,
            WorldPoint target,
            Collection<Transport> transports,
            boolean travel
    ) {
        if (from == null || target == null) {
            return TilePath.empty();
        }
        ArrayList<WorldPoint> tries = new ArrayList<>();
        WorldPoint snap = GlobalPathfinder.nearestWalkable(from);
        if (snap != null && (snap.getX() != from.getX() || snap.getY() != from.getY())) {
            tries.add(snap);
        }
        int[][] offs = {
                {0, 1}, {0, -1}, {1, 0}, {-1, 0},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
                {0, 2}, {0, -2}, {2, 0}, {-2, 0}
        };
        for (int[] o : offs) {
            WorldPoint n = new WorldPoint(from.getX() + o[0], from.getY() + o[1], from.getPlane());
            WorldPoint w = GlobalPathfinder.nearestWalkable(n);
            if (w == null || (w.getX() == from.getX() && w.getY() == from.getY())) {
                continue;
            }
            boolean dup = false;
            for (WorldPoint t : tries) {
                if (t.getX() == w.getX() && t.getY() == w.getY() && t.getPlane() == w.getPlane()) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                continue;
            }
            tries.add(w);
            if (tries.size() >= 4) {
                break;
            }
        }
        for (WorldPoint start : tries) {
            TilePath p = GlobalPathfinder.findPath(start, target, MovementHelper::preferAvoidBlocked, transports);
            if (p != null && !p.isEmpty() && !isGreedyInScenePrefix(p, target)) {
                storePath(p, target, travel);
                walkLog("path start-snap " + fmt(from) + " via " + fmt(start)
                        + " tiles=" + p.size() + " → " + fmt(target));
                return p;
            }
        }
        return TilePath.empty();
    }

    private static void copyTransports(TilePath into, TilePath from) {
        if (into == null || from == null) {
            return;
        }
        for (Transport t : from.getTransports()) {
            into.addTransport(t);
        }
    }

    private static boolean preferAvoidBlocked(WorldPoint p) {
        if (KaramjaVolcano.isVolcanoRockTile(p)) {
            return true;
        }
        if (Boolean.TRUE.equals(TRAVEL.get())) {
            return travelAvoidTiles(p);
        }
        return isInLumbridgeBlockedZone(p) || ExcludedTiles.isExcluded(p) || NoWalkZones.isBlocked(p);
    }

    /** Travel-BFS (ook achtergrond-thread): geen ThreadLocal TRAVEL nodig. */
    static boolean travelAvoidTiles(WorldPoint p) {
        if (p == null) {
            return false;
        }
        if (KaramjaVolcano.isVolcanoRockTile(p)) {
            return true;
        }
        if (shouldAvoidWildernessTile(p)) {
            return true;
        }
        if (shouldAvoidEdgevilleMausoleum(p)) {
            return true;
        }
        return isInLumbridgeBlockedZone(p) || NoWalkZones.isLumbridgePotatoField(p);
    }

    /**
     * Ruïne naast Edge-yews: pad eromheen, tenzij we erin staan of het doel erin ligt (Giants-trapdoor).
     */
    private static boolean shouldAvoidEdgevilleMausoleum(WorldPoint p) {
        if (!NoWalkZones.isEdgevilleMausoleum(p)) {
            return false;
        }
        if (NoWalkZones.isEdgevilleMausoleum(localPos()) || NoWalkZones.isEdgevilleMausoleum(activeDest)) {
            return false;
        }
        return true;
    }

    /** Wilderness mijden als we er niet in staan en het doel er ook niet ligt (Edge yews → bank). */
    private static boolean shouldAvoidWildernessTile(WorldPoint p) {
        if (!Walker.isWilderness(p)) {
            return false;
        }
        if (Walker.isWilderness(localPos()) || Walker.isWilderness(activeDest)) {
            return false;
        }
        return true;
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
