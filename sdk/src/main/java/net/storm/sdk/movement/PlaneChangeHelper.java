package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Wanneer speler op een andere floor/plane staat dan het loopdoel (grot, kasteelbank),
 * eerst Climb/Ladder — GlobalPathfinder kan geen plane-wissel.
 * <p>
 * Lumbridge-bank (plane 2 → 0): {@code Bottom-floor}, niet left-click Climb-down
 * (dat stopt op de 1e verdieping). Vanaf booth eerst naar zuid-trap lopen — BFS
 * bank↔trap faalt soms; dan stappen i.p.v. “success” zonder beweging.
 */
public final class PlaneChangeHelper {

    private static final Logger log = LoggerFactory.getLogger(PlaneChangeHelper.class);
    private static final long INTERACT_COOLDOWN_MIN_MS = 1_200L;
    private static final long INTERACT_COOLDOWN_SPAN_MS = 800L;
    private static final long PLANE_WAIT_MS = 5_500L;
    /** Interact als we dicht genoeg bij de trap zijn (klik tijdens lopen OK). */
    private static final int LUMB_CLIMB_RANGE = 12;
    private static final int INTERACT_RANGE = 8;

    /** Bank booth → zuid-trap (plane 2), walkable tegels. */
    private static final WorldPoint[] LUMB_BANK_TO_STAIRS = {
            new WorldPoint(3208, 3218, 2),
            new WorldPoint(3208, 3216, 2),
            new WorldPoint(3207, 3214, 2),
            new WorldPoint(3206, 3212, 2),
            new WorldPoint(3205, 3210, 2),
            LumbridgeStairsHelper.SOUTH_BANK
    };

    private static volatile long nextInteractAllowedMs;
    private static volatile int waitStartedPlane = Integer.MIN_VALUE;
    private static volatile long waitUntilMs;
    /** Welke waypoint in {@link #LUMB_BANK_TO_STAIRS} als walk faalt. */
    private static volatile int lumbStepIndex;
    private static volatile int lumbFailStreak;

    private PlaneChangeHelper() {
    }

    public static boolean needsPlaneChange(WorldPoint dest) {
        WorldPoint from = localPos();
        return from != null && dest != null && from.getPlane() != dest.getPlane();
    }

    /**
     * Probeer dichter bij {@code dest.getPlane()} te komen via scene objects.
     *
     * @return true als Climb/Ladder-interact is gestart, we lopen naar de trap, of we wachten
     *         op de plane-wissel. false = caller mag opnieuw / fallback (niet “stil doen alsof ok”).
     */
    public static boolean progressTowardPlane(WorldPoint dest) {
        WorldPoint from = localPos();
        if (from == null || dest == null) {
            return false;
        }
        int want = dest.getPlane();
        int cur = from.getPlane();
        if (cur == want) {
            clearWait();
            LumbridgeStairsHelper.clearSticky();
            lumbStepIndex = 0;
            lumbFailStreak = 0;
            return false;
        }
        long now = System.currentTimeMillis();
        if (waitStartedPlane != Integer.MIN_VALUE) {
            if (cur != waitStartedPlane) {
                clearWait();
                lumbFailStreak = 0;
            } else if (now < waitUntilMs) {
                return true;
            } else {
                clearWait();
                BotRuntime.logConsole("[Walk/climb] plane-wait timeout — retry");
            }
        }

        boolean goDown = cur > want;
        // Al dicht bij Lumb-trap: Climb/Bottom-floor tijdens lopen — geen walk→stop→klik
        boolean nearLumbStairsReady = false;
        ITileObject earlyStairs = null;
        if (LumbridgeStairsHelper.isInLumbridgeCastleArea(from)) {
            earlyStairs = LumbridgeStairsHelper.pickSouthStairs(from, goDown);
            if (earlyStairs != null && earlyStairs.getWorldLocation() != null) {
                int dEarly = from.distanceTo(earlyStairs.getWorldLocation());
                nearLumbStairsReady = dEarly <= LUMB_CLIMB_RANGE
                        && (goDown || LumbridgeStairsHelper.canClimbNow(from));
            }
        }
        if (now < nextInteractAllowedMs && !nearLumbStairsReady) {
            Players.LocalSnap me = Players.snapshotLocal();
            if (me != null && me.moving) {
                return true;
            }
            // Stil tijdens cooldown na mislukte walk → opnieuw proberen
        }

        boolean lumbDest = LumbridgeStairsHelper.isLumbridgeUpperDest(dest);
        if (lumbDest && !LumbridgeStairsHelper.isInLumbridgeCastleArea(from)) {
            // Draynor e.d.: niet de lokale trap. MovementHelper loopt naar courtyard.
            return false;
        }
        ITileObject obj = null;
        // Lumbridge: alleen zuid-trap (niet nearest = noord bij booth)
        if (LumbridgeStairsHelper.isInLumbridgeCastleArea(from) && lumbDest) {
            if (cur == 0 && !LumbridgeStairsHelper.canClimbNow(from)) {
                return walkTowardLumbridgeStairs(from, now);
            }
            obj = LumbridgeStairsHelper.pickSouthStairs(from, goDown);
            if (obj == null) {
                // Op/nabij trap-tegel maar sticky/filter miste object → breder zoeken
                WorldPoint anchor = LumbridgeStairsHelper.walkAnchor(cur);
                if (from.distanceTo(anchor) <= LUMB_CLIMB_RANGE
                        || LumbridgeStairsHelper.isSouthStairsTile(from)
                        || LumbridgeStairsHelper.canClimbNow(from)) {
                    obj = TileObjects.getNearest(o -> matchesPlaneExit(o, goDown, cur, want)
                            && o.getWorldLocation() != null
                            && o.getWorldLocation().getPlane() == cur
                            && (LumbridgeStairsHelper.isSouthStairsTile(o.getWorldLocation())
                                    || from.distanceTo(o.getWorldLocation()) <= LUMB_CLIMB_RANGE));
                }
            }
            if (obj == null) {
                return walkTowardLumbridgeStairs(from, now);
            }
            WorldPoint stairTile = obj.getWorldLocation();
            int dStairs = stairTile != null ? from.distanceTo(stairTile) : 99;
            if (cur == 0 && !LumbridgeStairsHelper.canClimbNow(from)) {
                return walkTowardLumbridgeStairs(from, now);
            }
            if (dStairs > LUMB_CLIMB_RANGE) {
                return walkTowardLumbridgeStairs(from, now);
            }
            // Dichtbij genoeg: camera + climb (niet blijven walken tot stilstand)
            if (stairTile != null) {
                WalkCamera.ensureLookingAt(stairTile);
            }
        } else if (LumbridgeStairsHelper.isInLumbridgeCastleArea(from)) {
            obj = LumbridgeStairsHelper.pickSouthStairs(from, goDown);
            if (obj == null) {
                return walkTowardLumbridgeStairs(from, now);
            }
            WorldPoint stairTile = obj.getWorldLocation();
            int dStairs = stairTile != null ? from.distanceTo(stairTile) : 99;
            if (dStairs > LUMB_CLIMB_RANGE) {
                return walkTowardLumbridgeStairs(from, now);
            }
            if (stairTile != null) {
                WalkCamera.ensureLookingAt(stairTile);
            }
        }
        if (obj == null && !lumbDest) {
            obj = TileObjects.getNearest(o -> matchesPlaneExit(o, goDown, cur, want)
                    && (!LumbridgeStairsHelper.isInLumbridgeCastleArea(from)
                    || LumbridgeStairsHelper.isSouthStairsTile(o.getWorldLocation())));
        }
        if (obj == null && !lumbDest && !LumbridgeStairsHelper.isInLumbridgeCastleArea(from)) {
            obj = TileObjects.getNearest(o -> matchesPlaneExit(o, goDown, cur, want));
        }
        if (obj == null && !lumbDest && !LumbridgeStairsHelper.isInLumbridgeCastleArea(from)) {
            obj = TileObjects.getNearest(o -> climbActionOk(o, goDown, cur, want)
                    && o.getWorldLocation() != null
                    && from.distanceTo(o.getWorldLocation()) <= 15);
        }
        if (obj != null && obj.getWorldLocation() != null
                && from.distanceTo(obj.getWorldLocation()) > INTERACT_RANGE) {
            if (LumbridgeStairsHelper.isInLumbridgeCastleArea(from) || lumbDest) {
                return walkTowardLumbridgeStairs(from, now);
            }
            BotRuntime.logConsole("[Walk/climb] te ver voor klik d="
                    + from.distanceTo(obj.getWorldLocation()) + " → loop dichterbij");
            return false;
        }
        if (obj == null) {
            log.warn("[PlaneChange] geen ladder/stairs (cur={} want={} goDown={}) @{}",
                    cur, want, goDown, from);
            BotRuntime.logConsole("[Walk/climb] FAIL geen stairs plane " + cur + "→" + want);
            lumbFailStreak++;
            return false;
        }
        String action = pickAction(obj, goDown, cur, want);
        if (action == null) {
            lumbFailStreak++;
            return false;
        }
        log.info("[PlaneChange] {} \"{}\" {} → plane {}→{}",
                action, obj.getName(), obj.getWorldLocation(), cur, want);
        // Bottom-floor / Climb tijdens pad — pad clearen zodat we niet doorlopen na klik
        try {
            MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        boolean ok = false;
        try {
            net.runelite.api.TileObject raw = TileObjects.unwrap(obj);
            if (raw != null) {
                ok = net.storm.sdk.interact.MenuInteract.interactObject(raw, action);
            }
        } catch (Throwable ignored) {
        }
        if (!ok) {
            ok = obj.interact(action);
        }
        if (ok) {
            waitStartedPlane = cur;
            waitUntilMs = now + PLANE_WAIT_MS;
            nextInteractAllowedMs = now + INTERACT_COOLDOWN_MIN_MS
                    + ThreadLocalRandom.current().nextInt((int) INTERACT_COOLDOWN_SPAN_MS);
            lumbFailStreak = 0;
            lumbStepIndex = 0;
            BotRuntime.logConsole("[Walk/climb] " + action + " \"" + obj.getName() + "\" "
                    + obj.getWorldLocation().getX() + "," + obj.getWorldLocation().getY()
                    + " plane " + cur + "→" + want + (nearLumbStairsReady ? " (tijdens loop)" : ""));
        } else {
            lumbFailStreak++;
            BotRuntime.logConsole("[Walk/climb] interact FAIL " + action
                    + " plane " + cur + "→" + want + " streak=" + lumbFailStreak);
            // Failsafe: dichter bij trap lopen i.p.v. idle
            if (LumbridgeStairsHelper.isInLumbridgeCastleArea(from)) {
                return walkTowardLumbridgeStairs(from, now);
            }
        }
        return ok;
    }

    /**
     * Loop via waypoints naar zuid-trap. Mislukte walk → volgende waypoint (geen valse “ok”).
     */
    private static boolean walkTowardLumbridgeStairs(WorldPoint from, long now) {
        if (from != null && from.getPlane() == 0 && LumbridgeStairsHelper.canClimbNow(from)) {
            return false;
        }
        WorldPoint step = nextLumbridgeStairStep(from);
        if (step == null) {
            return false;
        }
        if (from != null && step.getPlane() == from.getPlane() && from.distanceTo(step) <= 1) {
            return false;
        }
        log.info("[PlaneChange] Lumb → loop naar trap @{} (failStreak={})", step, lumbFailStreak);
        BotRuntime.logConsole("[Walk/climb] Lumb → trap @"
                + step.getX() + "," + step.getY() + "," + step.getPlane()
                + " i=" + lumbStepIndex);
        boolean walked = MovementHelper.walkTo(step);
        if (walked) {
            nextInteractAllowedMs = now + INTERACT_COOLDOWN_MIN_MS;
            lumbFailStreak = 0;
            return true;
        }
        lumbFailStreak++;
        lumbStepIndex++;
        // Probeer meteen volgende waypoint (één tick = één poging was te traag / stil)
        if (lumbStepIndex < LUMB_BANK_TO_STAIRS.length + 2) {
            WorldPoint alt = nextLumbridgeStairStep(from);
            BotRuntime.logConsole("[Walk/climb] Lumb walk FAIL → alt @"
                    + alt.getX() + "," + alt.getY());
            walked = MovementHelper.walkTo(alt);
            if (walked) {
                nextInteractAllowedMs = now + INTERACT_COOLDOWN_MIN_MS;
                lumbFailStreak = 0;
                return true;
            }
            lumbStepIndex++;
        }
        BotRuntime.logConsole("[Walk/climb] Lumb walk FAIL streak=" + lumbFailStreak);
        return false;
    }

    private static WorldPoint nextLumbridgeStairStep(WorldPoint from) {
        if (from == null) {
            return LumbridgeStairsHelper.SOUTH_BANK;
        }
        int plane = from.getPlane();
        if (plane < 2) {
            if (plane == 0) {
                if (LumbridgeStairsHelper.canClimbNow(from)) {
                    return from;
                }
                return LumbridgeStairsHelper.pathApproach();
            }
            return LumbridgeStairsHelper.walkAnchor(plane);
        }
        WorldPoint goal = LumbridgeStairsHelper.SOUTH_BANK;
        int myGoal = from.distanceTo(goal);
        // Dicht genoeg: anker zelf (caller doet Bottom-floor)
        if (myGoal <= 2 || LumbridgeStairsHelper.isSouthStairsTile(from)) {
            return goal;
        }
        // Alleen waypoints dichter bij de trap — geen ping-pong terug naar booth
        WorldPoint best = null;
        int bestGoal = Integer.MAX_VALUE;
        for (WorldPoint wp : LUMB_BANK_TO_STAIRS) {
            if (wp == null || from.distanceTo(wp) <= 1) {
                continue;
            }
            int dGoal = wp.distanceTo(goal);
            if (dGoal < myGoal && dGoal < bestGoal) {
                bestGoal = dGoal;
                best = wp;
            }
        }
        return best != null ? best : goal;
    }

    public static String statusHint(WorldPoint dest) {
        WorldPoint from = localPos();
        if (from == null || dest == null) {
            return "plane?";
        }
        if (from.getPlane() == dest.getPlane()) {
            return "";
        }
        return "plane " + from.getPlane() + "→" + dest.getPlane() + " (climb eerst)";
    }

    private static void clearWait() {
        waitStartedPlane = Integer.MIN_VALUE;
        waitUntilMs = 0L;
    }

    private static boolean matchesPlaneExit(ITileObject o, boolean goDown, int cur, int want) {
        if (o == null || o.getName() == null) {
            return false;
        }
        String n = o.getName().toLowerCase();
        boolean nameOk = n.contains("stair") || n.contains("ladder") || n.contains("trapdoor")
                || n.equals("rope") || n.contains("climb") || n.contains("staircase");
        if (!nameOk) {
            return false;
        }
        if (goDown) {
            if (cur - want >= 2 && o.hasAction("Bottom-floor")) {
                return true;
            }
            return o.hasAction("Bottom-floor") || o.hasAction("Climb-down") || o.hasAction("Climb Down")
                    || o.hasAction("Enter") || o.hasAction("Open")
                    || o.hasAction("Climb");
        }
        return o.hasAction("Top-floor") || o.hasAction("Climb-up") || o.hasAction("Climb Up")
                || o.hasAction("Climb");
    }

    /** Fallback: Climb-actie zonder naam-match (geen hardcoded IDs). */
    private static boolean climbActionOk(ITileObject o, boolean goDown, int cur, int want) {
        return o != null && pickAction(o, goDown, cur, want) != null;
    }

    /**
     * Meerdere verdiepingen omlaag (Lumb bank plane 2 → grond 0): Bottom-floor.
     * Eén verdieping: Climb-down. Nooit left-click als Bottom-floor beschikbaar is.
     */
    static String pickAction(ITileObject o, boolean goDown, int cur, int want) {
        if (goDown) {
            int drop = cur - want;
            if (drop >= 2 && o.hasAction("Bottom-floor")) {
                return "Bottom-floor";
            }
            if (want == 0 && o.hasAction("Bottom-floor")) {
                return "Bottom-floor";
            }
            if (o.hasAction("Climb-down")) {
                return "Climb-down";
            }
            if (o.hasAction("Climb Down")) {
                return "Climb Down";
            }
            if (o.hasAction("Bottom-floor")) {
                return "Bottom-floor";
            }
            if (o.hasAction("Enter")) {
                return "Enter";
            }
            if (o.hasAction("Climb")) {
                return "Climb";
            }
            if (o.hasAction("Open")) {
                return "Open";
            }
        } else {
            // Lumb zuid-trap: per verdieping Climb-up. Top-floor vanaf de grond doet niets.
            if (cur == 0) {
                if (o.hasAction("Climb-up")) {
                    return "Climb-up";
                }
                if (o.hasAction("Climb Up")) {
                    return "Climb Up";
                }
                if (o.hasAction("Climb")) {
                    return "Climb";
                }
                if (o.hasAction("Top-floor")) {
                    return "Top-floor";
                }
                return null;
            }
            int rise = want - cur;
            if (rise >= 2 && o.hasAction("Top-floor")) {
                return "Top-floor";
            }
            if (o.hasAction("Climb-up")) {
                return "Climb-up";
            }
            if (o.hasAction("Climb Up")) {
                return "Climb Up";
            }
            if (o.hasAction("Top-floor")) {
                return "Top-floor";
            }
            if (o.hasAction("Climb")) {
                return "Climb";
            }
        }
        return null;
    }

    private static WorldPoint localPos() {
        Players.LocalSnap snap = Players.snapshotLocal();
        return snap != null && snap.present ? snap.worldLocation : null;
    }
}
