package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;

/**
 * Walk helpers: arrival-distance + no re-click while already walking to dest.
 * Indoor emotes: Open deur dicht bij dest (CombatBot {@code tryOpenDoorNear}).
 */
public final class ClueWalk {

    /** Binnen deze afstand tot emote-tegel: Open-deur proberen (CombatBot). */
    public static final int EMOTE_DOOR_APPROACH = 12;
    /** Deur mag max. zo ver van emote-tegel liggen. */
    public static final int EMOTE_DOOR_NEAR_DEST = 5;
    private static final long DOOR_OPEN_COOLDOWN_MS = 1_200L;

    private static boolean bobWaypointDone;
    private static long lastDoorOpenMs;
    private static long lastDoorLogMs;

    private ClueWalk() {
    }

    public static void reset() {
        bobWaypointDone = false;
        lastDoorOpenMs = 0L;
        lastDoorLogMs = 0L;
    }

    public static WorldPoint me() {
        try {
            Players.LocalSnap snap = Players.snapshotLocal();
            return snap != null && snap.present ? snap.worldLocation : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean near(WorldPoint dest, int arrival) {
        return dest != null && Movement.isNear(dest, Math.max(0, arrival));
    }

    /**
     * True while an active walk dest is still meaningfully away — skip Read/UI during travel.
     */
    public static boolean isTravelBusy() {
        WorldPoint me = me();
        WorldPoint dest = null;
        try {
            dest = MovementHelper.getActiveDestination();
        } catch (Throwable ignored) {
        }
        if (dest == null) {
            try {
                dest = Movement.getDestination();
            } catch (Throwable ignored) {
            }
        }
        if (me != null && dest != null && me.distanceTo(dest) > 2) {
            return true;
        }
        if (Movement.isWalking()) {
            WorldPoint flag = Movement.getDestination();
            if (me != null && flag != null && me.distanceTo(flag) > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dig only when within {@code arrival} and either on the dig tile or walker idle
     * (avoids Dig racing remaining path hops).
     */
    public static boolean readyToDig(WorldPoint dig, int arrival) {
        if (dig == null || !near(dig, arrival)) {
            return false;
        }
        if (near(dig, 0)) {
            return true;
        }
        return !Movement.isWalking();
    }

    /** Stop residual walk after Dig so COS/STALL does not fight the next clue step. */
    public static void clearWalk() {
        try {
            Movement.clearPath();
        } catch (Throwable ignored) {
        }
    }

    /**
     * @return true if already within {@code arrival} tiles
     */
    public static boolean walkUntilNear(WorldPoint dest, int arrival) {
        if (dest == null) {
            return true;
        }
        if (near(dest, arrival)) {
            return true;
        }
        WorldPoint flag = Movement.getDestination();
        if (Movement.isWalking() && flag != null && flag.distanceTo(dest) <= Math.max(arrival, 2)) {
            return false;
        }
        Movement.walkTo(dest);
        return false;
    }

    /**
     * Emote-aanpak: binnen {@link #EMOTE_DOOR_APPROACH} Open-deur bij dest, daarna walk tot arrival.
     * Bob: via waypoint. CombatBot {@code handleEmoteStep} / {@code tryOpenDoorNear}.
     *
     * @return true als binnen {@code arrival} van emote-tegel
     */
    public static boolean approachEmoteTile(WorldPoint dest, boolean bobPath, int arrival) {
        WorldPoint pos = me();
        if (dest != null && pos != null) {
            int dist = pos.distanceTo(dest);
            if (dist > arrival && dist <= EMOTE_DOOR_APPROACH) {
                tryOpenDoorNear(dest, EMOTE_DOOR_NEAR_DEST);
            }
        }
        if (bobPath) {
            return walkBobPath();
        }
        return walkUntilNear(dest, arrival);
    }

    /**
     * Open dichtstbijzijnde "Open"-object binnen {@code maxDist} van emote-dest (deur/tent).
     * Throttle 1.2s — geen spam.
     */
    public static boolean tryOpenDoorNear(WorldPoint dest, int maxDist) {
        if (dest == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastDoorOpenMs < DOOR_OPEN_COOLDOWN_MS) {
            return false;
        }
        try {
            ITileObject door = TileObjects.getNearest(obj -> {
                if (obj == null || obj.getWorldLocation() == null) {
                    return false;
                }
                if (!obj.hasAction("Open")) {
                    return false;
                }
                WorldPoint loc = obj.getWorldLocation();
                return loc.getPlane() == dest.getPlane() && loc.distanceTo(dest) <= maxDist;
            });
            if (door == null) {
                return false;
            }
            if (!door.interact("Open")) {
                return false;
            }
            lastDoorOpenMs = now;
            if (now - lastDoorLogMs >= 1_500L) {
                lastDoorLogMs = now;
                WorldPoint loc = door.getWorldLocation();
                BotRuntime.logConsole("[Clue/Emote] deur Open @ "
                        + (loc != null ? loc.getX() + "," + loc.getY() : "?")
                        + " (emote-gebied)");
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Lumbridge Bob: waypoint (3236,3219) then dest (3231,3203).
     * Never re-walk the waypoint once south of it.
     */
    public static boolean walkBobPath() {
        WorldPoint pos = me();
        if (pos == null) {
            return false;
        }
        if (pos.getY() <= BeginnerClueReference.BOB_WAYPOINT.getY()) {
            bobWaypointDone = true;
        }
        if (near(BeginnerClueReference.BOB_DEST, BeginnerClueReference.EMOTE_ARRIVAL)) {
            return true;
        }
        if (!bobWaypointDone && pos.getY() > BeginnerClueReference.BOB_WAYPOINT.getY()) {
            BotRuntime.logConsole("[Clue/Walk] Bob waypoint 3236,3219");
            return walkUntilNear(BeginnerClueReference.BOB_WAYPOINT, 2);
        }
        bobWaypointDone = true;
        // Laatste hop naar shop: deur Open als dichtbij
        if (pos.distanceTo(BeginnerClueReference.BOB_DEST) <= EMOTE_DOOR_APPROACH) {
            tryOpenDoorNear(BeginnerClueReference.BOB_DEST, EMOTE_DOOR_NEAR_DEST);
        }
        return walkUntilNear(BeginnerClueReference.BOB_DEST, BeginnerClueReference.EMOTE_ARRIVAL);
    }

    public static boolean inReldoLibrary(WorldPoint pos) {
        return ReldoApproachHelper.isInReldoTalkZone(pos);
    }

    /** @deprecated gebruik {@link ReldoApproachHelper#approachAndTalk()}. */
    @Deprecated
    public static boolean walkReldoDoors() {
        return ReldoApproachHelper.canTalkToReldoNow(me())
                || ReldoApproachHelper.approachBeforeTalk(null) == 0;
    }
}
