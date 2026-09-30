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
 * Brian (Port Sarim battleaxe shop) — enclosed shop; zone bbox + LOS + Open door.
 * CombatBot {@code BrianShopApproachHelper} port.
 */
public final class BrianShopApproachHelper {

    public static final int NPC_ID = 2892;
    public static final WorldPoint BRIAN_TALK_TILE = new WorldPoint(3027, 3247, 0);

    private static final WorldPoint BRIAN_DOOR_WEST = new WorldPoint(3026, 3245, 0);
    private static final WorldPoint BRIAN_DOOR_EAST = new WorldPoint(3030, 3248, 0);
    private static final WorldPoint[] BRIAN_DOORS = {BRIAN_DOOR_WEST, BRIAN_DOOR_EAST};

    private static final int ZONE_MIN_X = 3021;
    private static final int ZONE_MAX_X = 3032;
    private static final int ZONE_MIN_Y = 3240;
    private static final int ZONE_MAX_Y = 3253;

    private static long lastWalkMs;
    private static long lastDoorMs;
    private static long lastLogMs;
    private static String lastLogMsg = "";

    private BrianShopApproachHelper() {
    }

    public static void reset() {
        lastWalkMs = 0L;
        lastDoorMs = 0L;
        lastLogMs = 0L;
        lastLogMsg = "";
    }

    public static boolean isBrianNpcName(String npcName) {
        if (npcName == null || npcName.isEmpty()) {
            return false;
        }
        String low = npcName.toLowerCase(Locale.ROOT);
        return low.equals("brian") || low.contains("brian");
    }

    public static boolean isInBrianTalkZone(WorldPoint me) {
        if (me == null || me.getPlane() != BRIAN_TALK_TILE.getPlane()) {
            return false;
        }
        return me.getX() >= ZONE_MIN_X && me.getX() <= ZONE_MAX_X
                && me.getY() >= ZONE_MIN_Y && me.getY() <= ZONE_MAX_Y;
    }

    public static boolean hasLineOfSightToBrian(WorldPoint from) {
        if (from == null) {
            return false;
        }
        try {
            return Reachable.hasLineOfSight(from, BRIAN_TALK_TILE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** In talk-zone of LOS — dan 1× Talk-to (anagram). */
    public static boolean canTalkToBrianNow(WorldPoint me) {
        if (me == null || me.getPlane() != BRIAN_TALK_TILE.getPlane()) {
            return false;
        }
        return isInBrianTalkZone(me) || hasLineOfSightToBrian(me);
    }

    /**
     * Approach + door + Talk-to Brian.
     *
     * @return delay ms
     */
    public static int approachAndTalk(IPlayer local) {
        WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null) {
            return 600;
        }
        if (!canTalkToBrianNow(me)) {
            if (!isBrianDoorOpen() && nearDoor(me) && tryOpenDoor(me)) {
                return 500;
            }
            int travel = approachBeforeTalk(local);
            if (travel > 0) {
                return travel;
            }
            me = local != null ? local.getWorldLocation() : ClueWalk.me();
            if (me != null && !canTalkToBrianNow(me)) {
                if (tryOpenDoor(me)) {
                    return 500;
                }
                return 350;
            }
        }
        me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null || !canTalkToBrianNow(me)) {
            return 350;
        }
        INPC brian = findBrianNpc();
        if (brian == null) {
            logThrottled("[Clue/Brian] NPC niet in scene — walk zone");
            return approachBeforeTalk(local) > 0 ? 400 : 500;
        }
        logThrottled("[Clue/Brian] Talk-to");
        if (brian.hasAction("Talk-to") && brian.interact("Talk-to")) {
            return 550;
        }
        if (brian.interact("Talk-to") || brian.interact("Talk")) {
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
        if (canTalkToBrianNow(me)) {
            return 0;
        }
        if (!isBrianDoorOpen() && nearDoor(me) && tryOpenDoor(me)) {
            return 900;
        }
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900L && Movement.isWalking()) {
            return 280;
        }
        lastWalkMs = now;
        logThrottled("[Clue/Brian] → zone " + BRIAN_TALK_TILE.getX() + "," + BRIAN_TALK_TILE.getY()
                + " (nu " + me.getX() + "," + me.getY() + ")");
        Movement.walkTo(BRIAN_TALK_TILE);
        return 400;
    }

    public static boolean tryOpenDoor(WorldPoint me) {
        long now = System.currentTimeMillis();
        if (now - lastDoorMs < 800L) {
            return false;
        }
        if (isBrianDoorOpen() || isInBrianTalkZone(me)) {
            return false;
        }
        if (me == null || distanceToNearestDoor(me) > 3) {
            return false;
        }
        ITileObject door = findShopDoor();
        if (door == null || !door.hasAction("Open")) {
            return false;
        }
        door.interact("Open");
        lastDoorMs = now;
        BotRuntime.logConsole("[Clue/Brian] deur Open @ " + door.getWorldLocation());
        return true;
    }

    public static INPC findBrianNpc() {
        try {
            INPC byId = NPCs.getNearest(n -> n != null && n.getId() == NPC_ID
                    && n.getWorldLocation() != null
                    && isNearBrianShop(n.getWorldLocation()));
            if (byId != null) {
                return byId;
            }
            INPC exact = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().equalsIgnoreCase("Brian")
                    && n.getWorldLocation() != null
                    && isNearBrianShop(n.getWorldLocation()));
            if (exact != null) {
                return exact;
            }
            return NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().toLowerCase(Locale.ROOT).contains("brian")
                    && n.getWorldLocation() != null
                    && isNearBrianShop(n.getWorldLocation()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isNearBrianShop(WorldPoint p) {
        if (p == null || p.getPlane() != BRIAN_TALK_TILE.getPlane()) {
            return false;
        }
        if (isInBrianTalkZone(p)) {
            return true;
        }
        return p.distanceTo(BRIAN_TALK_TILE) <= 10;
    }

    private static boolean nearDoor(WorldPoint me) {
        if (me == null) {
            return false;
        }
        return distanceToNearestDoor(me) <= 12 || me.distanceTo(BRIAN_TALK_TILE) <= 14;
    }

    private static int distanceToNearestDoor(WorldPoint me) {
        int best = Integer.MAX_VALUE;
        for (WorldPoint door : BRIAN_DOORS) {
            best = Math.min(best, me.distanceTo(door));
        }
        return best;
    }

    private static ITileObject findShopDoor() {
        try {
            return TileObjects.getNearest(obj -> {
                if (obj == null || obj.getName() == null || obj.getWorldLocation() == null) {
                    return false;
                }
                WorldPoint loc = obj.getWorldLocation();
                if (loc.getPlane() != BRIAN_TALK_TILE.getPlane()) {
                    return false;
                }
                if (distanceToDoorTile(loc) > 1) {
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

    private static int distanceToDoorTile(WorldPoint loc) {
        int best = Integer.MAX_VALUE;
        for (WorldPoint door : BRIAN_DOORS) {
            best = Math.min(best, loc.distanceTo(door));
        }
        return best;
    }

    private static boolean isBrianDoorOpen() {
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
