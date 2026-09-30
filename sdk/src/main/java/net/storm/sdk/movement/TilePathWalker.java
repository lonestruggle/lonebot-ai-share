package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.api.movement.WalkOptions;
import net.storm.api.movement.pathfinder.model.Teleport;
import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.InteractWalkHelper;
import net.storm.sdk.movement.pathfinder.AlKharidGate;
import net.storm.sdk.movement.pathfinder.F2pSpellTeleports;
import net.storm.sdk.movement.pathfinder.GlobalPathfinder;
import net.storm.sdk.movement.pathfinder.Pathfinder;
import net.storm.sdk.movement.pathfinder.PathfinderRequirements;
import net.storm.sdk.widgets.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Walks a precomputed {@link TilePath}: remaining path → in-scene segment → click step.
 * Stapgrootte via {@link WalkClickSettings} (6–12); laatste 15 padtegels: click-on-sight, anders hop 5.
 */
public final class TilePathWalker {

    private static final Logger log = LoggerFactory.getLogger(TilePathWalker.class);

    private static volatile long lastPathClickMs;
    private static volatile long lastStepLogMs;
    private static volatile long lastTransportMs;
    private static volatile WorldPoint pendingTransportLand;
    /** Na Climb-up: blokkeer Climb-down (zelfde stairs) even. */
    private static volatile long blockReverseUntilMs;
    private static volatile long blockReverseSrcKey = Long.MIN_VALUE;
    private static volatile long blockReverseLandKey = Long.MIN_VALUE;
    private static volatile String lastStepLog = "";
    private static volatile WorldPoint waitAnchor;
    private static volatile long waitAnchorMs;
    /** Tegel van de laatste hop-klik (gele vlag), ook als client-dest even null is. */
    private static volatile WorldPoint clickedFlag;
    /** Scene-stairs cache — geen 1.5s client-call per padtegel. */
    private static volatile Set<Long> cachedClimbKeys = Collections.emptySet();
    private static volatile long cachedClimbMs;

    static {
        TilePath.WALKER = TilePathWalker::walk;
    }

    private TilePathWalker() {
    }

    public static void init() {
        TilePath.WALKER = TilePathWalker::walk;
        TilePath.PLAYER = () -> {
            Players.LocalSnap me = Players.snapshotLocal();
            return me != null && me.present ? me.worldLocation : null;
        };
    }

    /** Nieuw pad / script-reset: niet wachten op een oude gele flag. */
    public static void resetSession() {
        lastPathClickMs = 0L;
        lastTransportMs = 0L;
        pendingTransportLand = null;
        blockReverseUntilMs = 0L;
        blockReverseSrcKey = Long.MIN_VALUE;
        blockReverseLandKey = Long.MIN_VALUE;
        waitAnchor = null;
        waitAnchorMs = 0L;
        clickedFlag = null;
        cachedClimbKeys = Collections.emptySet();
        cachedClimbMs = 0L;
        WalkStuckFailsafe.reset();
    }

    public static boolean walk(TilePath path) {
        try {
            return walk0(path);
        } catch (IllegalStateException e) {
            log.warn("[TilePathWalker] client-thread: {}", e.toString());
            return false;
        } catch (RuntimeException e) {
            log.warn("[TilePathWalker] walk: {}", e.toString());
            return false;
        }
    }

    private static boolean walk0(TilePath path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.isIncomplete() && path.isEmpty()) {
            log.warn("[TilePathWalker] weiger leeg incomplete pad");
            return false;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint from = me != null && me.present ? me.worldLocation : null;
        if (from == null) {
            stepLog("geen speler-tegel — klik pad-hop");
            WorldPoint dest = path.getDestination();
            int idx = Math.min(Math.max(1, WalkClickSettings.effectiveStepMin()),
                    Math.max(0, path.size() - 1));
            WorldPoint hop = path.get(idx);
            if (hop == null) {
                hop = dest;
            }
            return hop != null && WalkClickHelper.walkTo(hop, dest != null ? dest : hop);
        }
        try {
            Movement.ensureTravelRun();
        } catch (Throwable ignored) {
        }
        WorldPoint dest = path.getDestination();
        WorldPoint travel = scriptDest(dest);
        if (travel != null && (path.isIncomplete() || dest == null
                || (dest.distanceTo(travel) > 2 && dest.getPlane() == travel.getPlane()))) {
            dest = travel;
        }
        refreshClimbCache(path, from, dest);
        if (AlKharidGate.progressForWalk(from, travel != null ? travel : dest)) {
            return true;
        }
        WorldPoint gateDest = travel != null ? travel : dest;
        if (AlKharidGate.needGate(from, gateDest) && AlKharidGate.canPass()
                && AlKharidGate.closeEnoughToOpen(from)) {
            if (AlKharidGate.tryPayOrOpen()) {
                return true;
            }
            stepLog("bij poort — Pay-toll retry, geen hop voorbij het hek");
            return true;
        }
        if (from.getPlane() == 0 && LumbridgeStairsHelper.isLumbridgeUpperDest(gateDest)
                && LumbridgeStairsHelper.canClimbNow(from)) {
            stepLog("bij Lumb-trap — Climb, geen omloop-hop");
            return true;
        }

        F2pSpellTeleports.dropIfAlreadyWalking(path, from);
        if (tryTransportOrTeleport(path, from)) {
            return true;
        }
        // Alleen climb als doel écht andere floor/dungeon is — niet “langs de trap”
        if (dest != null && climbRequired(from, dest)
                && !LumbridgeStairsHelper.isLumbridgeUpperDest(scriptDest(dest))
                && DungeonClimbHelper.progress(from, dest)) {
            return true;
        }
        // Gesloten deur/gate op het pad — Open vóór hop (niet eromheen lopen)
        TilePath remainingForDoor = path.getRemainingPath(from);
        if (WalkObstacles.interactIfNeeded(from, dest,
                remainingForDoor != null ? remainingForDoor : path)) {
            return true;
        }
        WorldPoint origin = path.get(0);
        if (F2pSpellTeleports.isTelePathStart(path, from) && origin != null) {
            stepLog("wacht tele-land @ " + origin.getX() + "," + origin.getY()
                    + " (geen hop tot je bij pad-start bent)");
            return true;
        }

        if (dest != null && from.distanceTo(dest) <= 0 && from.getPlane() == dest.getPlane()
                && !path.isIncomplete()) {
            return true;
        }
        if (dest != null && from.distanceTo(dest) <= 0 && from.getPlane() == dest.getPlane()
                && path.isIncomplete()) {
            WorldPoint last = path.get(path.size() - 1);
            if (last != null && last.distanceTo(dest) <= 2) {
                return true;
            }
        }

        long now = System.currentTimeMillis();
        TilePath remaining = path.getRemainingPath(from);
        if (remaining.isEmpty()) {
            return false;
        }
        if (WalkStuckFailsafe.holdOrUnstuck(me, dest, remaining.size())) {
            return true;
        }
        if (MovementHelper.incompletePrefixDone(path, from, dest)
                || MovementHelper.incompletePrefixDone(remaining, from, dest)) {
            return false;
        }
        WorldPoint pathEnd = remaining.get(remaining.size() - 1);
        boolean pathReachesDest = dest != null && pathEnd != null
                && dest.getPlane() == pathEnd.getPlane()
                && pathEnd.distanceTo(dest) <= 2;
        boolean lastStretch = pathReachesDest
                && remaining.size() <= WalkClickSettings.finalClickWithinTiles;
        if (AlKharidGate.needGate(from, gateDest) && AlKharidGate.canPass()) {
            lastStretch = false;
        }
        if (from.getPlane() == 0 && LumbridgeStairsHelper.isLumbridgeUpperDest(gateDest)) {
            lastStretch = false;
        }

        WorldPoint walkFlag = plausibleHopFlag(from, me != null ? me.walkDestination : null);
        WorldPoint flag = walkFlag != null ? walkFlag : plausibleHopFlag(from, clickedFlag);
        if (flag == null && lastStretch) {
            flag = dest;
        }
        int hopTiles = WalkClickSettings.lastHopTiles();
        if (hopTiles <= 0) {
            hopTiles = WalkClickSettings.effectiveStepMin();
        }
        hopTiles = Math.min(hopTiles, WalkClickSettings.effectiveStepMax());
        long sinceClick = lastPathClickMs > 0L ? now - lastPathClickMs : Long.MAX_VALUE;
        boolean moving = me != null && me.moving;
        boolean chainReady = moving && flag != null
                && !WalkClickSettings.shouldWaitTwoThirdsToFlag(from, flag);

        int remainAllow = WalkClickSettings.chainRemainAllow();
        if (lastStretch) {
            if (lastPathClickMs > 0L && walkFlag != null && moving
                    && WalkClickSettings.shouldWaitNearFlag(from, walkFlag, dest)) {
                chainWaitLog(from, flag, dest, remaining.size(), hopTiles, remainAllow, sinceClick, true);
                return true;
            }
        } else {
            boolean stuck = waitAnchor != null
                    && waitAnchor.getPlane() == from.getPlane()
                    && waitAnchor.distanceTo(from) <= 0
                    && now - waitAnchorMs > 1200L;
            if (moving && !chainReady && !stuck) {
                if (waitAnchor == null || waitAnchor.distanceTo(from) > 0
                        || waitAnchor.getPlane() != from.getPlane()) {
                    waitAnchor = from;
                    waitAnchorMs = now;
                }
                if (flag != null) {
                    chainWaitLog(from, flag, dest, remaining.size(), hopTiles, remainAllow, sinceClick, false);
                }
                return true;
            }
            if (!moving && lastPathClickMs > 0L && sinceClick < 800L && !stuck) {
                return true;
            }
        }
        waitAnchor = null;

        if (lastStretch) {
            WorldPoint nextHint = hopAlong(remaining, WalkClickSettings.effectiveStepMin());
            int cooldown = WalkClickSettings.pathChainReclickMs(from, flag, nextHint, dest);
            if (sinceClick < cooldown && moving) {
                return true;
            }
            return clickLastStretch(from, dest, remaining);
        }

        List<WorldPoint> inScene = reachableInScene(remaining);
        List<WorldPoint> hopPool = inScene;
        if (inScene.size() < 4) {
            hopPool = new ArrayList<>();
            for (WorldPoint p : remaining) {
                if (p != null) {
                    hopPool.add(p);
                }
            }
            if (hopPool.isEmpty()) {
                hopPool = inScene;
            }
        }
        WorldPoint step;
        if (hopPool.isEmpty()) {
            step = pickConfiguredStep(from, walkFlag, remaining, dest);
            if (step == null) {
                int idx = Math.min(Math.max(1, WalkClickSettings.effectiveStepMin()), remaining.size() - 1);
                step = remaining.get(idx);
            }
            WorldPoint look = dest != null && remaining.size() <= WalkClickSettings.effectiveStepMax() + 4
                    ? dest : step;
            step = capHopBeforeTransport(from, remaining, step);
            step = WalkObstacles.capHopBeforeDoor(from, remaining, step);
            step = capHopAvoidUnwantedClimb(from, remaining, step, dest);
            step = capHopToStepMax(from, hopPool, step, dest);
            step = capBarrierHops(from, gateDest, step);
            stepLog("scene-scan leeg → klik hop " + step.getX() + "," + step.getY()
                    + " remaining=" + remaining.size()
                    + " dest=" + (dest != null ? dest.getX() + "," + dest.getY() : "-"));
            return clickHop(step, look, from, false);
        }

        step = pickNextHop(from, walkFlag != null ? walkFlag : flag, hopPool, dest);

        if (step == null) {
            step = hopPool.get(Math.min(hopPool.size() - 1, Math.max(1, WalkClickSettings.effectiveStepMin())));
        }
        if (from.distanceTo(step) <= 1 && hopPool.size() > 4) {
            WorldPoint nudged = pickConfiguredStep(from, walkFlag, hopPool, dest);
            if (nudged != null) {
                step = nudged;
            }
        }
        step = capHopBeforeTransport(from, remaining, step);
        step = WalkObstacles.capHopBeforeDoor(from, remaining, step);
        step = capHopAvoidUnwantedClimb(from, remaining, step, dest);

        WorldPoint lookAt = step;
        if (pathReachesDest && dest != null
                && remaining.size() <= WalkClickSettings.effectiveStepMax() + 2) {
            lookAt = dest;
        }

        step = capHopToStepMax(from, hopPool, step, dest);
        step = capBarrierHops(from, gateDest, step);
        int hopD = from.distanceTo(step);
        WalkClickSettings.noteHopTiles(hopD);
        boolean chaining = moving && lastPathClickMs > 0L;
        int dFlagNow = flag != null && from.getPlane() == flag.getPlane() ? from.distanceTo(flag) : -1;
        int walked = hopTiles > 0 && dFlagNow >= 0 ? Math.max(0, hopTiles - dFlagNow) : 0;
        stepLog((chaining ? "doorlink " : "step ")
                + step.getX() + "," + step.getY()
                + " hop=" + hopD
                + " remaining=" + remaining.size()
                + " inScene=" + inScene.size()
                + chainSince(sinceClick, walked, dFlagNow, remainAllow)
                + " dest=" + (dest != null ? dest.getX() + "," + dest.getY() : "-"));
        boolean ok = clickHop(step, lookAt, from, false);
        if (ok) {
            return true;
        }
        WorldPoint closer = closerHop(from, hopPool, step);
        if (closer != null && clickHop(closer, lookAt, from, false)) {
            stepLog("minimap closer " + closer.getX() + "," + closer.getY());
            return true;
        }
        stepLog("FAIL click " + step.getX() + "," + step.getY());
        return false;
    }

    /**
     * Hop: alleen minimap Walk-here. Invoke/canvas zette geen vlag of klikte de wereld.
     */
    private static boolean clickHop(WorldPoint step, WorldPoint lookAt, WorldPoint from, boolean preferInvoke) {
        if (step == null) {
            return false;
        }
        InteractWalkHelper.PlayerSnapshot snap = InteractWalkHelper.capture();
        boolean ok = WalkClickHelper.clickMinimapHop(step);
        if (ok) {
            lastPathClickMs = System.currentTimeMillis();
            clickedFlag = step;
            WalkStuckFailsafe.noteIssued(snap);
            if (from != null) {
                WalkClickSettings.noteHopTiles(from.distanceTo(step));
            }
        }
        return ok;
    }

    /** Client-dest / klik-tegel alleen als het een echte hop is — niet dest 50+ tegels verder. */
    private static WorldPoint plausibleHopFlag(WorldPoint from, WorldPoint flag) {
        if (from == null || flag == null || from.getPlane() != flag.getPlane()) {
            return null;
        }
        int max = WalkClickSettings.effectiveStepMax() + 4;
        if (from.distanceTo(flag) > max) {
            return null;
        }
        return flag;
    }

    /** Nooit een hop verder dan stepMax — pad-index ≠ Chebyshev vanaf de speler. */
    private static WorldPoint capHopToStepMax(
            WorldPoint from, List<WorldPoint> tiles, WorldPoint step, WorldPoint dest) {
        int max = WalkClickSettings.effectiveStepMax();
        int min = WalkClickSettings.effectiveStepMin();
        if (from == null || step == null) {
            return step;
        }
        int d0 = from.distanceTo(step);
        if (from.getPlane() == step.getPlane() && d0 >= 1 && d0 <= max) {
            return step;
        }
        if (tiles == null || tiles.isEmpty()) {
            return step;
        }
        WorldPoint best = null;
        WorldPoint nearest = null;
        int nearestD = Integer.MAX_VALUE;
        for (WorldPoint p : tiles) {
            if (p == null || from.getPlane() != p.getPlane()) {
                continue;
            }
            int d = from.distanceTo(p);
            if (d < 1) {
                continue;
            }
            if (d < nearestD) {
                nearest = p;
                nearestD = d;
            }
            if (d > max) {
                if (best != null) {
                    break;
                }
                continue;
            }
            if (shouldAvoidClimbHop(p, from, dest)) {
                continue;
            }
            if (d >= min) {
                best = p;
            } else if (best == null) {
                best = p;
            }
        }
        return best != null ? best : (nearest != null ? nearest : step);
    }

    private static WorldPoint hopAlong(TilePath remaining, int tiles) {
        if (remaining == null || remaining.isEmpty()) {
            return null;
        }
        int idx = Math.min(Math.max(1, tiles), remaining.size() - 1);
        return remaining.get(idx);
    }

    /**
     * Laatste ≤15 pad-tegels: click-on-sight op B. Fail → 5 padtegels verder richting B.
     */
    private static boolean clickLastStretch(WorldPoint from, WorldPoint dest, TilePath remaining) {
        if (dest == null) {
            return false;
        }
        stepLog("eind COS " + dest.getX() + "," + dest.getY()
                + " remaining=" + remaining.size());
        InteractWalkHelper.PlayerSnapshot snap = InteractWalkHelper.capture();
        if (WalkClickHelper.tryClickOnSight(dest)) {
            lastPathClickMs = System.currentTimeMillis();
            WalkStuckFailsafe.noteIssued(snap);
            WalkClickSettings.noteHopTiles(from != null ? from.distanceTo(dest) : remaining.size());
            return true;
        }
        int fall = Math.max(1, WalkClickSettings.finalClickFallbackTiles);
        WorldPoint hop5 = hopAlong(remaining, fall);
        if (hop5 == null) {
            hop5 = dest;
        }
        hop5 = capHopBeforeTransport(from, remaining, hop5);
        hop5 = WalkObstacles.capHopBeforeDoor(from, remaining, hop5);
        hop5 = capHopAvoidUnwantedClimb(from, remaining, hop5, dest);
        stepLog("COS fail → hop" + fall + " " + hop5.getX() + "," + hop5.getY()
                + " remaining=" + remaining.size());
        return clickHop(hop5, dest, from, false);
    }

    private static void chainWaitLog(
            WorldPoint from,
            WorldPoint flag,
            WorldPoint dest,
            int remaining,
            int hopTiles,
            int remainAllow,
            long sinceClickMs,
            boolean endStretch
    ) {
        int dFlag = from != null && flag != null && from.getPlane() == flag.getPlane()
                ? from.distanceTo(flag) : -1;
        int tilesUntilClick = dFlag >= 0 ? Math.max(0, dFlag - remainAllow) : -1;
        int walked = hopTiles > 0 && dFlag >= 0 ? Math.max(0, hopTiles - dFlag) : 0;
        double secUntil = tilesUntilClick >= 0 ? tilesUntilClick * 0.35 : -1;
        String msg = (endStretch ? "wacht eind " : "wacht klik over ")
                + tilesUntilClick + " tegels / ~"
                + (secUntil >= 0 ? String.format(java.util.Locale.ROOT, "%.1f", secUntil) : "?")
                + "s (al " + fmtSec(sinceClickMs) + "s / " + walked + " tegels van hop " + hopTiles
                + ", flag d=" + dFlag + " klik bij d≤" + remainAllow
                + " rem=" + remaining
                + " dest=" + (dest != null ? dest.getX() + "," + dest.getY() : "-") + ")";
        if (System.currentTimeMillis() - lastStepLogMs >= 1200L || !lastStepLog.startsWith("wacht")) {
            stepLog(msg);
        }
    }

    private static String chainSince(long sinceClickMs, int walked, int dFlag, int remainAllow) {
        if (sinceClickMs >= Long.MAX_VALUE / 4) {
            return "";
        }
        return " na " + fmtSec(sinceClickMs) + "s/" + walked + "t (flag d=" + dFlag
                + " klik≤" + remainAllow + ")";
    }

    private static String fmtSec(long ms) {
        if (ms < 0L || ms > 60_000L) {
            return "-";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", ms / 1000.0);
    }

    private static void stepLog(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastStepLog) && now - lastStepLogMs < 1500L) {
            return;
        }
        lastStepLog = msg;
        lastStepLogMs = now;
        log.debug("[Walk/step] {}", msg);
        BotRuntime.logConsole("[Walk/step] " + msg);
    }

    /**
     * Stap min–max uit walker-settings (standaard 6–12).
     * Pad-index + afstandscap (niet minimap-filter hier — dat brak soepele hops).
     */
    private static WorldPoint pickNextHop(WorldPoint from, WorldPoint flag, List<WorldPoint> inScene,
                                          WorldPoint dest) {
        if (inScene == null || inScene.isEmpty()) {
            return null;
        }
        WorldPoint mini = WalkClickHelper.farthestOnMinimap(from, inScene,
                WalkClickSettings.effectiveStepMin(), WalkClickSettings.effectiveStepMax());
        if (mini != null && !shouldAvoidClimbHop(mini, from, dest)) {
            return mini;
        }
        if (WalkClickSettings.useFarCanvasWalk) {
            WorldPoint far = WalkClickHelper.pickFarthestCanvasTile(inScene);
            if (far != null && !shouldAvoidClimbHop(far, from, dest)) {
                return far;
            }
            return farthestClickable(inScene, from, dest);
        }
        return pickConfiguredStep(from, flag, inScene, dest);
    }

    /**
     * Volgende hop: 6–12 tegels Chebyshev vanaf de speler (niet lijst-index).
     * Nooit op/vóór de huidige gele vlag (anders negeert OSRS de klik).
     * Geen stair-tegel als we alleen moeten doorlopen.
     */
    private static WorldPoint pickConfiguredStep(WorldPoint from, WorldPoint flag, List<WorldPoint> tiles,
                                                 WorldPoint dest) {
        if (tiles == null || tiles.isEmpty()) {
            return null;
        }
        int minStep = Math.max(1, WalkClickSettings.effectiveStepMin());
        int maxStep = Math.max(minStep, WalkClickSettings.effectiveStepMax());
        int last = tiles.size() - 1;
        int lo = -1;
        int hi = -1;
        for (int i = 0; i < tiles.size(); i++) {
            WorldPoint p = tiles.get(i);
            if (p == null || from == null) {
                continue;
            }
            int d = from.distanceTo(p);
            if (d < minStep) {
                continue;
            }
            if (d > maxStep) {
                if (lo >= 0) {
                    break;
                }
                continue;
            }
            if (flag != null && p.getPlane() == flag.getPlane() && p.distanceTo(flag) <= 1) {
                continue;
            }
            if (lo < 0) {
                lo = i;
            }
            hi = i;
        }
        int minIdx;
        int maxIdx;
        if (lo >= 0 && hi >= lo) {
            minIdx = lo;
            maxIdx = hi;
        } else {
            int pastFlag = 0;
            if (flag != null) {
                for (int i = 0; i < tiles.size(); i++) {
                    WorldPoint p = tiles.get(i);
                    if (p != null && p.getPlane() == flag.getPlane() && p.distanceTo(flag) <= 1) {
                        pastFlag = i + 1;
                        break;
                    }
                }
            }
            if (pastFlag > maxStep + 2) {
                pastFlag = 0;
            }
            minIdx = Math.min(last, Math.max(minStep, pastFlag));
            maxIdx = Math.min(last, Math.max(minIdx, maxStep));
            if (pastFlag > maxStep && pastFlag <= last) {
                minIdx = pastFlag;
                maxIdx = Math.min(last, pastFlag + Math.max(0, maxStep - minStep));
            }
        }
        int stepIdx = minIdx >= maxIdx
                ? maxIdx
                : ThreadLocalRandom.current().nextInt(minIdx, maxIdx + 1);
        WorldPoint cand = tiles.get(stepIdx);
        if (!shouldAvoidClimbHop(cand, from, dest)) {
            return cand;
        }
        for (int i = stepIdx - 1; i >= Math.max(1, minIdx - 3); i--) {
            WorldPoint p = tiles.get(i);
            if (p != null && hopWalkable(p) && !shouldAvoidClimbHop(p, from, dest)) {
                return p;
            }
        }
        for (int i = stepIdx + 1; i <= Math.min(last, maxIdx + 3); i++) {
            WorldPoint p = tiles.get(i);
            if (p != null && hopWalkable(p) && !shouldAvoidClimbHop(p, from, dest)) {
                return p;
            }
        }
        return cand;
    }

    private static WorldPoint farthestClickable(List<WorldPoint> inScene, WorldPoint from, WorldPoint dest) {
        for (int i = inScene.size() - 1; i >= 1; i--) {
            WorldPoint cand = inScene.get(i);
            if (Movement.canClickWalk(cand) && hopWalkable(cand) && !shouldAvoidClimbHop(cand, from, dest)) {
                return cand;
            }
        }
        return inScene.get(0);
    }

    private static WorldPoint closerHop(WorldPoint from, List<WorldPoint> inScene, WorldPoint failed) {
        if (from == null || inScene == null || inScene.isEmpty()) {
            return null;
        }
        int avoid = failed != null ? failed.distanceTo(from) : Integer.MAX_VALUE;
        for (int i = Math.min(8, inScene.size() - 1); i >= 2; i--) {
            WorldPoint cand = inScene.get(i);
            if (cand == null || cand.equals(failed)) {
                continue;
            }
            int d = cand.distanceTo(from);
            if (d >= 2 && d < avoid && hopWalkable(cand)
                    && !shouldAvoidClimbHop(cand, from, MovementHelper.getActiveDestination())) {
                return cand;
            }
        }
        return null;
    }

    private static WorldPoint capHopBeforeTransport(WorldPoint from, TilePath remaining, WorldPoint plannedHop) {
        if (from == null || remaining == null || plannedHop == null) {
            return plannedHop;
        }
        // Nooit Al Kharid-poort-tegel klikken vanaf >20 tegels (scene-scan leeg / invoke FAIL)
        if (AlKharidGate.isGateTile(plannedHop) && AlKharidGate.tooFarToClick(from, plannedHop)) {
            WorldPoint approach = hopBeforeAlKharidGate(from, remaining);
            if (approach != null) {
                return approach;
            }
        }
        List<Transport> used = remaining.getTransports();
        if (used == null || used.isEmpty()) {
            return plannedHop;
        }
        WorldPoint cap = plannedHop;
        int capDist = plannedHop.distanceTo(from);
        for (Transport t : used) {
            if (!WalkClickSettings.useShortcuts && WalkClickSettings.isShortcut(t)) {
                continue;
            }
            if (!transportStillAhead(remaining, t, from)) {
                continue;
            }
            WorldPoint src = t != null ? t.getSource() : null;
            WorldPoint land = t != null ? t.getDestination() : null;
            if (src == null || from.getPlane() != src.getPlane()) {
                continue;
            }
            // Lokale deuren: WalkObstacles regelt Open; niet hop cap’en op deur-tegel
            if (land != null && src.getPlane() == land.getPlane() && src.distanceTo(land) <= 3
                    && !AlKharidGate.matches(t)) {
                continue;
            }
            int radius = Math.max(1, t.getSourceRadius());
            int dSrc = from.distanceTo(src);
            if (dSrc <= radius) {
                continue;
            }
            // Al Kharid: ver weg → hop vóór de poort, niet óp de poort-tegel
            if (AlKharidGate.matches(t) && dSrc > AlKharidGate.APPROACH_COS_TILES) {
                WorldPoint approach = hopBeforeAlKharidGate(from, remaining);
                if (approach != null) {
                    int d = from.distanceTo(approach);
                    if (d >= 1 && d < capDist) {
                        capDist = d;
                        cap = approach;
                    }
                }
                continue;
            }
            if (dSrc < capDist) {
                capDist = dSrc;
                cap = src;
            }
        }
        return cap;
    }

    /** Pad-tegel vóór Al Kharid-poort, binnen normale hop-afstand. */
    private static WorldPoint hopBeforeAlKharidGate(WorldPoint from, TilePath remaining) {
        if (from == null || remaining == null || remaining.isEmpty()) {
            return null;
        }
        java.util.ArrayList<WorldPoint> before = new java.util.ArrayList<>();
        for (WorldPoint p : remaining) {
            if (p == null) {
                continue;
            }
            if (AlKharidGate.isGateTile(p)) {
                break;
            }
            before.add(p);
        }
        if (before.isEmpty()) {
            return null;
        }
        int stepMin = WalkClickSettings.effectiveStepMin();
        int stepMax = WalkClickSettings.effectiveStepMax();
        for (int i = before.size() - 1; i >= 0; i--) {
            WorldPoint p = before.get(i);
            int d = from.distanceTo(p);
            if (d >= stepMin && d <= stepMax) {
                return p;
            }
        }
        int idx = Math.min(Math.max(1, stepMin), before.size() - 1);
        int maxIdx = Math.min(stepMax, before.size() - 1);
        if (maxIdx > idx) {
            idx = maxIdx;
        }
        return before.get(idx);
    }

    private static boolean tryTransportOrTeleport(TilePath path, WorldPoint from) {
        if (pendingTransportLand != null) {
            if (from.getPlane() == pendingTransportLand.getPlane()
                    && from.distanceTo(pendingTransportLand) <= 2) {
                pendingTransportLand = null;
            } else if (Dialog.isOpen()) {
                pendingTransportLand = null;
            } else if (System.currentTimeMillis() - lastTransportMs
                    < (from.distanceTo(pendingTransportLand) > 8 ? 18_000L : 2500L)) {
                if (System.currentTimeMillis() - lastStepLogMs >= 1600L) {
                    stepLog("wacht transport-land @ "
                            + pendingTransportLand.getX() + "," + pendingTransportLand.getY()
                            + " d=" + from.distanceTo(pendingTransportLand));
                }
                return true;
            } else {
                pendingTransportLand = null;
            }
        }
        WalkOptions opts = path.getWalkOptions();
        boolean useTransports = opts == null || opts.isUseTransports();
        if (useTransports) {
            TilePath remaining = path.getRemainingPath(from);
            for (Transport t : path.getTransports()) {
                if (!WalkClickSettings.useShortcuts && WalkClickSettings.isShortcut(t)) {
                    continue;
                }
                WorldPoint src = t.getSource();
                WorldPoint land = t.getDestination();
                if (src == null || from.getPlane() != src.getPlane()) {
                    continue;
                }
                int radius = Math.min(2, Math.max(1, t.getSourceRadius()));
                int dSrc = from.distanceTo(src);
                boolean alkGate = AlKharidGate.matches(t);
                if (alkGate) {
                    WorldPoint alkDest = scriptDest(path.getDestination());
                    if (!AlKharidGate.needGate(from, alkDest)) {
                        continue;
                    }
                    if (!AlKharidGate.canPass()) {
                        AlKharidGate.logSkip();
                        continue;
                    }
                    // Open/COS pas ≤20 tegels — verder alleen aanlopen
                    if (dSrc > AlKharidGate.APPROACH_COS_TILES) {
                        continue;
                    }
                } else if (dSrc > radius) {
                    continue;
                }
                // Lokale deur (1–3 tegels): geen transport-teleport-wacht — WalkObstacles + lopen
                if (!alkGate && land != null && src.getPlane() == land.getPlane()
                        && src.distanceTo(land) <= 3) {
                    continue;
                }
                if (!alkGate && land != null && from.getPlane() == land.getPlane()
                        && from.distanceTo(land) < dSrc
                        && src.distanceTo(land) <= 24) {
                    // Alleen lokale deuren — niet dungeon-trapdoor (zelfde plane, Y+6400)
                    continue;
                }
                WorldPoint walkDest = path.getDestination();
                if (walkDest == null) {
                    walkDest = MovementHelper.getActiveDestination();
                }
                // Trap/ladder alleen als doel écht omhoog/omlaag/dungeon moet — niet “langs lopen”
                if (isStairLikeTransport(t, src, land) && !climbRequired(from, walkDest)) {
                    continue;
                }
                if (isBlockedReverseTransport(src, land)) {
                    continue;
                }
                if (worsensTransportProgress(from, land, walkDest)) {
                    continue;
                }
                if (!transportStillAhead(remaining, t, from)) {
                    continue;
                }
                if (!PathfinderRequirements.met(t.getRequirements())) {
                    MovementHelper.clearPath();
                    return true;
                }
                long now = System.currentTimeMillis();
                int gap = 720 + ThreadLocalRandom.current().nextInt(560);
                if (now - lastTransportMs < gap) {
                    continue;
                }
                lastTransportMs = now;
                String name = t.getName() != null ? t.getName() : "transport";
                if (alkGate) {
                    BotRuntime.logConsole("[Walk/gate] Pay-toll COS d=" + dSrc
                            + " (≤" + AlKharidGate.APPROACH_COS_TILES + ")");
                }
                boolean ok = t.execute();
                if (ok) {
                    pendingTransportLand = land;
                    noteTransportUsed(src, land);
                    BotRuntime.logConsole("[Walk/path] transport " + name + " @" + src.getX() + "," + src.getY()
                            + " → " + (land != null ? land.getX() + "," + land.getY() : "-"));
                    return true;
                }
                BotRuntime.logConsole("[Walk/path] transport fail " + name
                        + " @" + src.getX() + "," + src.getY());
            }
        }
        boolean useTeleports = opts != null && opts.isUseTeleports();
        if (!useTeleports || path.getTeleports().isEmpty() || path.isEmpty()) {
            return false;
        }
        if (!F2pSpellTeleports.isTelePathStart(path, from)) {
            return false;
        }
        WorldPoint first = path.get(0);
        for (Teleport t : path.getTeleports()) {
            WorldPoint land = t.getDestination();
            if (land == null || first == null || land.distanceTo(first) > 6) {
                continue;
            }
            BotRuntime.logConsole("[Walk/path] teleport " + land);
            boolean ok = t.execute();
            if (ok) {
                pendingTransportLand = land;
                lastTransportMs = System.currentTimeMillis();
            }
            return true;
        }
        return false;
    }

    /** True als dit transport nog op het resterende pad zit — of we al op de bron-tegel staan. */
    private static boolean transportStillAhead(TilePath remaining, Transport t, WorldPoint from) {
        if (t == null) {
            return false;
        }
        WorldPoint src = t.getSource();
        WorldPoint land = t.getDestination();
        int radius = Math.max(1, t.getSourceRadius());
        if (from != null && src != null && from.getPlane() == src.getPlane()
                && from.distanceTo(src) <= radius) {
            return true;
        }
        if (remaining == null || remaining.isEmpty()) {
            return false;
        }
        boolean sawSrc = false;
        boolean sawLand = false;
        for (WorldPoint p : remaining) {
            if (p == null) {
                continue;
            }
            if (src != null && p.getPlane() == src.getPlane() && p.distanceTo(src) <= 1) {
                sawSrc = true;
            }
            if (land != null && p.getPlane() == land.getPlane() && p.distanceTo(land) <= 1) {
                sawLand = true;
            }
        }
        return sawSrc && sawLand;
    }

    private static void noteTransportUsed(WorldPoint src, WorldPoint land) {
        if (src == null || land == null) {
            return;
        }
        blockReverseSrcKey = tileKey(src);
        blockReverseLandKey = tileKey(land);
        blockReverseUntilMs = System.currentTimeMillis() + 5_500L;
        DungeonClimbHelper.noteClimb(GlobalPathfinder.isLikelyInstanceGap(src, land)
                && DungeonClimbHelper.towardDungeon(src, land));
    }

    private static boolean isBlockedReverseTransport(WorldPoint src, WorldPoint land) {
        if (src == null || land == null || System.currentTimeMillis() >= blockReverseUntilMs) {
            return false;
        }
        return tileKey(src) == blockReverseLandKey && tileKey(land) == blockReverseSrcKey;
    }

    /**
     * Climb-down direct na Climb-up (of omgekeerd) brengt je verder van het loopdoel.
     */
    private static boolean worsensTransportProgress(WorldPoint from, WorldPoint land, WorldPoint dest) {
        if (from == null || land == null || dest == null) {
            return false;
        }
        boolean fromGap = GlobalPathfinder.isLikelyInstanceGap(from, dest);
        boolean landGap = GlobalPathfinder.isLikelyInstanceGap(land, dest);
        if (fromGap && !landGap) {
            return false;
        }
        if (!fromGap && landGap) {
            return true;
        }
        return land.distanceTo(dest) > from.distanceTo(dest) + 8;
    }

    private static long tileKey(WorldPoint p) {
        return (((long) p.getPlane()) << 42) | (((long) p.getX()) << 21) | (p.getY() & 0x1FFFFF);
    }

    /** Doel op andere floor of surface↔dungeon. */
    private static boolean climbRequired(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null) {
            return false;
        }
        if (from.getPlane() != dest.getPlane()) {
            return true;
        }
        return GlobalPathfinder.isLikelyInstanceGap(from, dest);
    }

    private static boolean isStairLikeTransport(Transport t, WorldPoint src, WorldPoint land) {
        if (src != null && land != null) {
            if (src.getPlane() != land.getPlane()) {
                return true;
            }
            if (GlobalPathfinder.isLikelyInstanceGap(src, land)) {
                return true;
            }
        }
        String n = t != null ? t.getName() : null;
        if (n == null || n.isBlank()) {
            return false;
        }
        String low = n.toLowerCase(java.util.Locale.ROOT);
        return low.contains("climb") || low.contains("stair") || low.contains("ladder")
                || low.contains("trapdoor") || low.contains("enter");
    }

    /** Map/script-doel (bank p2 / Al Kharid), niet het A*-aanlooppunt. */
    private static WorldPoint scriptDest(WorldPoint pathDest) {
        WorldPoint ww = WorldWalker.destination();
        if (ww != null) {
            return ww;
        }
        WorldPoint ad = MovementHelper.getActiveDestination();
        return ad != null ? ad : pathDest;
    }

    /** Na stepMax: niet alsnog voorbij poort/trap hoppen. */
    private static WorldPoint capBarrierHops(WorldPoint from, WorldPoint dest, WorldPoint step) {
        step = AlKharidGate.capHop(from, dest, step);
        return LumbridgeStairsHelper.capHop(from, dest, step);
    }

    /** Niet op stair-tegel klikken als we alleen moeten doorlopen — of Lumb-bank Climb. */
    private static boolean shouldAvoidClimbHop(WorldPoint hop, WorldPoint from, WorldPoint dest) {
        if (hop == null || !tileHasClimbObject(hop)) {
            return false;
        }
        WorldPoint goal = scriptDest(dest);
        if (LumbridgeStairsHelper.isLumbridgeUpperDest(goal)
                || LumbridgeStairsHelper.needsGroundApproach(from, goal)) {
            return true;
        }
        return !climbRequired(from, dest);
    }

    private static WorldPoint capHopAvoidUnwantedClimb(WorldPoint from, TilePath remaining,
                                                       WorldPoint plannedHop, WorldPoint dest) {
        if (plannedHop == null || !shouldAvoidClimbHop(plannedHop, from, dest) || remaining == null) {
            return plannedHop;
        }
        WorldPoint best = null;
        int bestD = Integer.MAX_VALUE;
        int maxHop = WalkClickSettings.effectiveStepMax() + 2;
        for (WorldPoint p : remaining) {
            if (p == null || p.distanceTo(from) < 2) {
                continue;
            }
            int d = from.distanceTo(p);
            if (d > maxHop) {
                continue;
            }
            if (shouldAvoidClimbHop(p, from, dest)) {
                continue;
            }
            if (!hopWalkable(p)) {
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best != null) {
            stepLog("hop vermijd trap @" + plannedHop.getX() + "," + plannedHop.getY()
                    + " → " + best.getX() + "," + best.getY());
            return best;
        }
        return plannedHop;
    }

    private static void refreshClimbCache(TilePath path, WorldPoint from, WorldPoint dest) {
        if (!climbRequired(from, dest)) {
            cachedClimbKeys = Collections.emptySet();
            cachedClimbMs = System.currentTimeMillis();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - cachedClimbMs < 400L) {
            return;
        }
        List<WorldPoint> scan = new ArrayList<>();
        if (from != null) {
            scan.add(from);
        }
        if (path != null) {
            int n = 0;
            for (WorldPoint p : path) {
                if (p == null) {
                    continue;
                }
                if (++n > 32) {
                    break;
                }
                scan.add(p);
            }
        }
        Set<Long> keys = new HashSet<>();
        java.util.List<net.storm.api.domain.tiles.ITileObject> all =
                net.storm.sdk.entities.TileObjects.getOnTiles(scan, TilePathWalker::hasClimbMenu, 80L);
        if (all != null) {
            for (net.storm.api.domain.tiles.ITileObject o : all) {
                if (o == null || o.getWorldLocation() == null) {
                    continue;
                }
                keys.add(tileKey(o.getWorldLocation()));
            }
        }
        cachedClimbKeys = keys;
        cachedClimbMs = now;
    }

    private static boolean tileHasClimbObject(WorldPoint wp) {
        if (wp == null) {
            return false;
        }
        Set<Long> keys = cachedClimbKeys;
        return keys != null && keys.contains(tileKey(wp));
    }

    private static boolean hopWalkable(WorldPoint wp) {
        if (KaramjaVolcano.isVolcanoRockTile(wp)) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return Pathfinder.isWalkableSceneTile(c, wp);
        }, false, 80L));
    }

    private static boolean hasClimbMenu(net.storm.api.domain.tiles.ITileObject o) {
        if (o == null) {
            return false;
        }
        return o.hasAction("Climb-down") || o.hasAction("Climb-up") || o.hasAction("Climb")
                || o.hasAction("Climb Down") || o.hasAction("Climb Up") || o.hasAction("Enter")
                || o.hasAction("Top-floor") || o.hasAction("Bottom-floor");
    }

    private static List<WorldPoint> reachableInScene(TilePath remaining) {
        List<WorldPoint> out = new ArrayList<>();
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            for (WorldPoint p : remaining) {
                if (p == null) {
                    break;
                }
                LocalPoint lp = LocalPoint.fromWorld(c, p);
                if (lp == null) {
                    // dungeon-tegel terwijl je surface bent — niet break
                    continue;
                }
                out.add(p);
            }
            return null;
        }, null, 200L);
        return out;
    }
}
