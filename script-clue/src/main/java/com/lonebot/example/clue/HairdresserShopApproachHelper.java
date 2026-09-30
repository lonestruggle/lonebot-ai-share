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
import net.storm.sdk.movement.Reachable;

import java.util.Locale;

/**
 * Hairdresser (Falador salon) — enclosed shop; zone bbox + LOS + Open door.
 * Anagram: 1× Talk-to (niet Charlie's multi-talk flow).
 * CombatBot {@code HairdresserShopApproachHelper} port.
 */
public final class HairdresserShopApproachHelper {

    public static final int NPC_ID = 1305;
    public static final WorldPoint HAIRDRESSER_TALK_TILE = new WorldPoint(2944, 3379, 0);
    public static final WorldPoint HAIRDRESSER_DOOR = new WorldPoint(2948, 3379, 0);

    private static final int ZONE_MIN_X = 2941;
    private static final int ZONE_MAX_X = 2947;
    private static final int ZONE_MIN_Y = 3376;
    private static final int ZONE_MAX_Y = 3384;

    private static long lastWalkMs;
    private static long lastDoorMs;
    private static long lastLogMs;
    private static String lastLogMsg = "";

    private HairdresserShopApproachHelper() {
    }

    public static void reset() {
        lastWalkMs = 0L;
        lastDoorMs = 0L;
        lastLogMs = 0L;
        lastLogMsg = "";
    }

    public static boolean isHairdresserNpcName(String npcName) {
        if (npcName == null || npcName.isEmpty()) {
            return false;
        }
        String low = npcName.toLowerCase(Locale.ROOT);
        return low.equals("hairdresser") || low.contains("hairdresser");
    }

    public static boolean isInHairdresserTalkZone(WorldPoint me) {
        if (me == null || me.getPlane() != HAIRDRESSER_TALK_TILE.getPlane()) {
            return false;
        }
        return me.getX() >= ZONE_MIN_X && me.getX() <= ZONE_MAX_X
                && me.getY() >= ZONE_MIN_Y && me.getY() <= ZONE_MAX_Y;
    }

    public static boolean hasLineOfSightToHairdresser(WorldPoint from) {
        if (from == null) {
            return false;
        }
        try {
            return Reachable.hasLineOfSight(from, HAIRDRESSER_TALK_TILE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** In talk-zone of LOS — dan 1× Talk-to (anagram). */
    public static boolean canTalkToHairdresserNow(WorldPoint me) {
        if (me == null || me.getPlane() != HAIRDRESSER_TALK_TILE.getPlane()) {
            return false;
        }
        return isInHairdresserTalkZone(me) || hasLineOfSightToHairdresser(me);
    }

    public static int approachAndTalk(IPlayer local) {
        WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null) {
            return 600;
        }
        if (!canTalkToHairdresserNow(me)) {
            if (!isHairdresserDoorOpen() && nearDoor(me) && tryOpenDoor(me)) {
                return 500;
            }
            int travel = approachBeforeTalk(local);
            if (travel > 0) {
                return travel;
            }
            me = local != null ? local.getWorldLocation() : ClueWalk.me();
            if (me != null && !canTalkToHairdresserNow(me)) {
                if (tryOpenDoor(me)) {
                    return 500;
                }
                return 350;
            }
        }
        me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null || !canTalkToHairdresserNow(me)) {
            return 350;
        }
        INPC npc = findHairdresser();
        if (npc == null) {
            logThrottled("[Clue/Hairdresser] NPC niet in scene — walk zone");
            return approachBeforeTalk(local) > 0 ? 400 : 500;
        }
        logThrottled("[Clue/Hairdresser] Talk-to");
        if (npc.hasAction("Talk-to") && npc.interact("Talk-to")) {
            return 550;
        }
        if (npc.interact("Talk-to") || npc.interact("Talk")) {
            return 550;
        }
        return 400;
    }

    public static int approachAndTalk() {
        return approachAndTalk(Players.getLocal());
    }

    /**
     * @return 0 = klaar; &gt;0 = delay
     */
    public static int approachBeforeTalk(IPlayer local) {
        WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null) {
            return 600;
        }
        if (canTalkToHairdresserNow(me)) {
            return 0;
        }
        if (!isHairdresserDoorOpen() && nearDoor(me) && tryOpenDoor(me)) {
            return 900;
        }
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900L && Movement.isWalking()) {
            return 280;
        }
        lastWalkMs = now;
        logThrottled("[Clue/Hairdresser] → zone " + HAIRDRESSER_TALK_TILE.getX() + ","
                + HAIRDRESSER_TALK_TILE.getY() + " (nu " + me.getX() + "," + me.getY() + ")");
        Movement.walkTo(HAIRDRESSER_TALK_TILE);
        return 400;
    }

    public static boolean tryOpenDoor(WorldPoint me) {
        long now = System.currentTimeMillis();
        if (now - lastDoorMs < 800L) {
            return false;
        }
        if (isHairdresserDoorOpen() || isInHairdresserTalkZone(me)) {
            return false;
        }
        if (me == null || me.distanceTo(HAIRDRESSER_DOOR) > 3) {
            return false;
        }
        ITileObject door = findShopDoor();
        if (door == null || !door.hasAction("Open")) {
            return false;
        }
        door.interact("Open");
        lastDoorMs = now;
        BotRuntime.logConsole("[Clue/Hairdresser] deur Open @ " + door.getWorldLocation());
        return true;
    }

    public static INPC findHairdresser() {
        try {
            INPC byId = NPCs.getNearest(n -> n != null && n.getId() == NPC_ID);
            if (byId != null) {
                return byId;
            }
            INPC exact = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().equalsIgnoreCase("Hairdresser"));
            if (exact != null) {
                return exact;
            }
            return NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().toLowerCase(Locale.ROOT).contains("hairdresser"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean nearDoor(WorldPoint me) {
        return me != null && (me.distanceTo(HAIRDRESSER_DOOR) <= 12
                || me.distanceTo(HAIRDRESSER_TALK_TILE) <= 14);
    }

    private static ITileObject findShopDoor() {
        try {
            return TileObjects.getNearest(obj -> {
                if (obj == null || obj.getName() == null || obj.getWorldLocation() == null) {
                    return false;
                }
                WorldPoint loc = obj.getWorldLocation();
                if (loc.getPlane() != 0 || loc.distanceTo(HAIRDRESSER_DOOR) > 1) {
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

    private static boolean isHairdresserDoorOpen() {
        ITileObject door = findShopDoor();
        return door != null && door.hasAction("Close");
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
