package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.movement.Movement;

import java.util.Locale;

/**
 * Doric's hut N of Falador — deur openen, binnen, dan Talk-to (anagram).
 * CombatBot {@code DoricHouseApproachHelper} port.
 */
public final class DoricHouseApproachHelper {

    public static final WorldPoint DORIC_TILE = new WorldPoint(2951, 3451, 0);
    private static final WorldPoint DORIC_HOUSE_ANCHOR = new WorldPoint(2952, 3451, 0);
    private static final int DORIC_INSIDE_TILE_RADIUS = 1;
    private static final WorldPoint DORIC_DOOR_TILE = new WorldPoint(2950, 3450, 0);
    private static final WorldPoint DORIC_DOOR_APPROACH_TILE = new WorldPoint(2950, 3449, 0);

    private static long lastWalkMs;
    private static long lastDoorMs;
    private static long lastLogMs;
    private static String lastLogMsg = "";

    private DoricHouseApproachHelper() {
    }

    public static void reset() {
        lastWalkMs = 0L;
        lastDoorMs = 0L;
        lastLogMs = 0L;
        lastLogMsg = "";
    }

    public static boolean isDoricNpcName(String npcName) {
        if (npcName == null || npcName.isEmpty()) {
            return false;
        }
        String low = npcName.toLowerCase(Locale.ROOT);
        return low.equals("doric") || low.contains("doric");
    }

    /** Binnen hut of deur open naast ingang — dan mag Talk-to. */
    public static boolean canTalkToDoricNow(WorldPoint me) {
        if (me == null || me.getPlane() != 0) {
            return false;
        }
        if (insideDoricHouse(me)) {
            return true;
        }
        return isDoricDoorOpen() && me.distanceTo(DORIC_HOUSE_ANCHOR) <= 4;
    }

    public static boolean insideDoricHouse(WorldPoint p) {
        return p != null && p.getPlane() == 0
                && p.distanceTo(DORIC_HOUSE_ANCHOR) <= DORIC_INSIDE_TILE_RADIUS;
    }

    public static int approachAndTalk(IPlayer local) {
        int travel = approachBeforeTalk(local);
        if (travel > 0) {
            return travel;
        }
        WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null || !canTalkToDoricNow(me)) {
            return 350;
        }
        INPC doric = findDoric();
        if (doric == null) {
            logThrottled("[Clue/Doric] NPC niet in scene — walk hut");
            issueWalk(DORIC_TILE);
            return 500;
        }
        logThrottled("[Clue/Doric] Talk-to");
        if (doric.hasAction("Talk-to") && doric.interact("Talk-to")) {
            return 550;
        }
        if (doric.interact("Talk-to") || doric.interact("Talk")) {
            return 550;
        }
        return 400;
    }

    public static int approachAndTalk() {
        return approachAndTalk(Players.getLocal());
    }

    /**
     * @return delay &gt; 0 als nog onderweg; 0 = klaar om met Doric te praten
     */
    public static int approachBeforeTalk(IPlayer local) {
        if (local == null) {
            return 600;
        }
        WorldPoint me = local.getWorldLocation();
        if (me == null) {
            return 600;
        }
        if (canTalkToDoricNow(me)) {
            if (insideDoricHouse(me) && me.distanceTo(DORIC_TILE) > 4) {
                issueWalk(DORIC_TILE);
                return 700;
            }
            return 0;
        }
        if (isDoricDoorOpen()) {
            issueWalkInside();
            return 800;
        }
        if (nearDoor(me)) {
            if (tryOpenDoor(me)) {
                return 1_200;
            }
            if (me.distanceTo(DORIC_DOOR_APPROACH_TILE) > 3) {
                issueWalk(DORIC_DOOR_APPROACH_TILE);
                return 800;
            }
            if (tryOpenDoor(me)) {
                return 1_200;
            }
        }
        issueWalkInside();
        return 800;
    }

    public static INPC findDoric() {
        try {
            INPC exact = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().equalsIgnoreCase("Doric"));
            if (exact != null) {
                return exact;
            }
            return NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().toLowerCase(Locale.ROOT).contains("doric"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean nearDoor(WorldPoint me) {
        return me.distanceTo(DORIC_DOOR_TILE) <= 14
                || me.distanceTo(DORIC_DOOR_APPROACH_TILE) <= 14
                || me.distanceTo(DORIC_HOUSE_ANCHOR) <= 14;
    }

    private static ITileObject findDoricHouseDoor() {
        try {
            return TileObjects.getNearest(obj -> {
                if (obj == null || obj.getName() == null || obj.getWorldLocation() == null) {
                    return false;
                }
                WorldPoint loc = obj.getWorldLocation();
                if (loc.getPlane() != 0 || loc.distanceTo(DORIC_DOOR_TILE) > 1) {
                    return false;
                }
                String n = obj.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("door")) {
                    return false;
                }
                return obj.hasAction("Open") || obj.hasAction("Close");
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isDoricDoorOpen() {
        ITileObject door = findDoricHouseDoor();
        return door != null && door.hasAction("Close");
    }

    private static boolean tryOpenDoor(WorldPoint me) {
        long now = System.currentTimeMillis();
        if (now - lastDoorMs < 900L) {
            return false;
        }
        if (isDoricDoorOpen() || insideDoricHouse(me)) {
            return false;
        }
        if (me.distanceTo(DORIC_DOOR_TILE) > 10) {
            return false;
        }
        ITileObject door = findDoricHouseDoor();
        if (door == null || !door.hasAction("Open")) {
            return false;
        }
        if (me.distanceTo(DORIC_DOOR_TILE) > 2 && me.distanceTo(DORIC_DOOR_APPROACH_TILE) > 2) {
            issueWalk(DORIC_DOOR_APPROACH_TILE);
            return false;
        }
        door.interact("Open");
        lastDoorMs = now;
        BotRuntime.logConsole("[Clue/Doric] hut deur Open @ " + door.getWorldLocation());
        return true;
    }

    private static void issueWalkInside() {
        issueWalk(DORIC_HOUSE_ANCHOR);
    }

    private static void issueWalk(WorldPoint target) {
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900L && Movement.isWalking()) {
            return;
        }
        lastWalkMs = now;
        logThrottled("[Clue/Doric] → " + target.getX() + "," + target.getY());
        Movement.walkTo(target);
    }

    private static void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLogMsg) && now - lastLogMs < 1_500L) {
            return;
        }
        lastLogMs = now;
        lastLogMsg = msg;
        BotRuntime.logConsole(msg);
    }
}
