package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.pathfinder.AlKharidGate;

/**
 * Xplorer / webwalker: één world-doel via map of scene-menu → {@link MovementHelper#walkTo}.
 */
public final class WorldWalker {

    private static volatile WorldPoint dest;
    private static volatile long lastWalkMs;
    private static volatile long startedMs;
    private static volatile long lastTickLogMs;
    /**
     * 0 = map Walk-here (aankomen d≤2, pad weg).
     * &gt;0 = script-travel: dest loslaten op die afstand, pad blijft (COS).
     */
    private static volatile int handoffWithinTiles;
    /** ESC om World Map te sluiten mag de walk niet cancelen. */
    private static volatile long ignoreEscCancelUntilMs;
    private static final long TIMEOUT_MS = 180_000L;

    private WorldWalker() {
    }

    /** Tijdelijk ESC negeren (map dicht tijdens pathfind-walk). */
    public static void ignoreEscCancelFor(long ms) {
        long until = System.currentTimeMillis() + Math.max(0L, ms);
        if (until > ignoreEscCancelUntilMs) {
            ignoreEscCancelUntilMs = until;
        }
    }

    public static boolean shouldIgnoreEscCancel() {
        return System.currentTimeMillis() < ignoreEscCancelUntilMs;
    }

    public static void go(WorldPoint target) {
        go(target, 0);
    }

    /**
     * @param handoffWithin 0 = aankomen op dest (d≤2, map Walk-here).
     *                      &gt;0 = dest loslaten, pad blijft zodat het script COS kan doen.
     */
    public static void go(WorldPoint target, int handoffWithin) {
        if (target == null) {
            return;
        }
        int handoff = Math.max(0, handoffWithin);
        WorldPoint cur = dest;
        if (cur != null && cur.getPlane() == target.getPlane() && cur.distanceTo(target) <= 2) {
            if (handoff > 0) {
                handoffWithinTiles = handoff;
            }
            return;
        }
        dest = target;
        handoffWithinTiles = handoff;
        startedMs = System.currentTimeMillis();
        lastWalkMs = 0L;
        lastTickLogMs = 0L;
        WorldPoint live = MovementHelper.getActiveDestination();
        boolean sameLive = live != null && live.getPlane() == target.getPlane()
                && live.distanceTo(target) <= 2;
        if (!sameLive) {
            MovementHelper.clearPath();
        }
        BotRuntime.debugTarget = target;
        // Map open → dicht zonder walk te cancelen (ESC triggert anders WorldWalker.cancel)
        try {
            if (net.storm.sdk.game.WorldMap.isOpen()) {
                ignoreEscCancelFor(1_500L);
                net.storm.sdk.game.WorldMap.dismissIfOpenDuringWalk();
            }
        } catch (Throwable ignored) {
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint from = me != null && me.present ? me.worldLocation : null;
        if (AlKharidGate.crossesGateWithoutPass(from, target)) {
            AlKharidGate.logDetour();
        }
        if (handoff > 0) {
            BotRuntime.logConsole("[Walk/map] dest " + target.getX() + "," + target.getY() + "," + target.getPlane()
                    + " handoff≤" + handoff);
        } else {
            BotRuntime.logConsole("[Walk/map] dest " + target.getX() + "," + target.getY() + "," + target.getPlane()
                    + " — ESC of Stop WorldWalker om te stoppen");
        }
    }

    public static void cancel() {
        if (dest != null) {
            BotRuntime.logConsole("[Walk/map] geannuleerd");
        }
        dest = null;
        handoffWithinTiles = 0;
        MovementHelper.clearPath();
    }

    /** Alleen script-travel (handoff&gt;0). Map Walk-here blijft staan. */
    public static void cancelScriptTravel() {
        if (handoffWithinTiles > 0) {
            cancel();
        }
    }

    /** Star e.d.: WorldWalker met handoff, mag F2P-spell starts in A* gebruiken. */
    public static boolean isScriptTravel() {
        return dest != null && handoffWithinTiles > 0;
    }

    public static boolean isActive() {
        return dest != null;
    }

    public static WorldPoint destination() {
        return dest;
    }

    /**
     * @return 0 als inactief, anders loop-delay
     */
    public static int tick() {
        WorldPoint d = dest;
        if (d == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - startedMs > TIMEOUT_MS) {
            BotRuntime.logConsole("[Walk/map] timeout → stop");
            cancel();
            return 0;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint from = me != null && me.present ? me.worldLocation : null;
        int arriveAt = handoffWithinTiles > 0 ? handoffWithinTiles : 2;
        if (from != null && from.getPlane() == d.getPlane() && from.distanceTo(d) <= arriveAt) {
            if (handoffWithinTiles > 0) {
                BotRuntime.logConsole("[Walk/map] handoff ≤" + arriveAt + " — script COS");
                dest = null;
                handoffWithinTiles = 0;
                return 0;
            }
            BotRuntime.logConsole("[Walk/map] aangekomen " + d.getX() + "," + d.getY());
            dest = null;
            MovementHelper.clearPath();
            return 0;
        }
        int randomDelay = net.storm.sdk.game.RandomEventHandler.tickTalkingRandomOnly();
        if (randomDelay > 0) {
            return randomDelay;
        }
        if (AlKharidGate.progressForWalk(from, d)) {
            return 280;
        }
        if (AlKharidGate.isTollDialog() && AlKharidGate.needGate(from, d)) {
            return 300;
        }
        Movement.tickAutoRun();
        boolean ok = MovementHelper.walkTo(d);
        if (BotRuntime.debugLogging && now - lastTickLogMs > 1500L) {
            lastTickLogMs = now;
            int dist = from != null ? from.distanceTo(d) : -1;
            BotRuntime.logConsole("[Walk/map] tick d=" + dist + " ok=" + ok);
        }
        return 220;
    }
}
