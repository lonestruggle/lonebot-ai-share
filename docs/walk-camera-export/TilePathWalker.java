package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Static;
import net.storm.sdk.movement.pathfinder.Pathfinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Walks a precomputed {@link TilePath}: remaining path → in-scene segment → click step.
 * Stapgrootte via {@link WalkClickSettings}; far-canvas klikt zo ver mogelijk on-screen.
 */
public final class TilePathWalker {

    private static final Logger log = LoggerFactory.getLogger(TilePathWalker.class);

    private static volatile long lastPathClickMs;

    static {
        TilePath.WALKER = TilePathWalker::walk;
    }

    private TilePathWalker() {
    }

    public static void init() {
        TilePath.WALKER = TilePathWalker::walk;
    }

    public static boolean walk(TilePath path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint from = me != null && me.present ? me.worldLocation : null;
        if (from == null) {
            return false;
        }

        WorldPoint dest = path.getDestination();
        if (dest != null && from.distanceTo(dest) <= 0 && from.getPlane() == dest.getPlane()) {
            return true;
        }

        long now = System.currentTimeMillis();
        WorldPoint flag = Movement.getDestination();
        if (flag == null) {
            flag = dest;
        }

        WorldPoint walkFlag = Movement.getDestination();
        // Far/canvas: flag-patience alleen bij langere walks
        if (walkFlag != null && Movement.isMoving()
                && WalkClickSettings.shouldWaitNearFlag(from, walkFlag, dest)) {
            return true;
        }

        int cooldown = Math.max(180, WalkClickSettings.effectiveReclickMs(from, flag));
        cooldown = Math.max(cooldown,
                WalkClickSettings.canvasExtraMinReclickMs(from, flag, dest));
        if (now - lastPathClickMs < cooldown && Movement.isMoving()) {
            return true;
        }

        TilePath remaining = path.getRemainingPath(from);
        if (remaining.isEmpty()) {
            return false;
        }

        List<WorldPoint> inScene = reachableInScene(remaining);
        if (inScene.isEmpty()) {
            WorldPoint next = remaining.get(Math.min(1, remaining.size() - 1));
            log.debug("[TilePathWalker] off-scene → Pathfinder step toward {}", next);
            boolean ok = Pathfinder.walkTo(next);
            if (ok) {
                lastPathClickMs = System.currentTimeMillis();
            }
            return ok;
        }

        WorldPoint step;
        if (WalkClickSettings.useFarCanvasWalk) {
            step = WalkClickHelper.pickFarthestCanvasTile(inScene);
            if (step == null) {
                step = farthestClickable(inScene);
            }
        } else {
            step = pickConfiguredStep(inScene);
        }

        if (step == null) {
            step = inScene.get(0);
        }

        // Camera / look = looprichting (klik-tegel), niet eindbestemming ver weg
        WorldPoint lookAt = step;
        if (!WalkClickSettings.useFarCanvasWalk && dest != null
                && from.distanceTo(dest) <= WalkClickSettings.effectiveStepMax() + 2) {
            lookAt = dest;
        }

        log.debug("[TilePathWalker] step {} far={} remaining={} inScene={} look={}",
                step, WalkClickSettings.useFarCanvasWalk, remaining.size(), inScene.size(), lookAt);
        boolean ok = WalkClickHelper.walkTo(step, lookAt);
        if (ok) {
            lastPathClickMs = System.currentTimeMillis();
        }
        return ok;
    }

    private static WorldPoint pickConfiguredStep(List<WorldPoint> inScene) {
        int minStep = WalkClickSettings.effectiveStepMin();
        int maxStep = WalkClickSettings.effectiveStepMax();
        int maxIdx = Math.min(maxStep, inScene.size() - 1);
        int minIdx = Math.min(minStep, maxIdx);
        if (maxIdx <= 0) {
            return inScene.get(0);
        }
        int stepIdx = minIdx >= maxIdx
                ? maxIdx
                : ThreadLocalRandom.current().nextInt(minIdx, maxIdx + 1);
        WorldPoint step = inScene.get(stepIdx);
        for (int i = stepIdx; i >= 1; i--) {
            if (Movement.canClickWalk(inScene.get(i))) {
                step = inScene.get(i);
                break;
            }
        }
        return step;
    }

    private static WorldPoint farthestClickable(List<WorldPoint> inScene) {
        for (int i = inScene.size() - 1; i >= 1; i--) {
            if (Movement.canClickWalk(inScene.get(i))) {
                return inScene.get(i);
            }
        }
        return inScene.get(0);
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
                    break;
                }
                out.add(p);
            }
            return null;
        }, null);
        return out;
    }
}
