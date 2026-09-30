package com.lonebot.example.quest.helpers;

import com.lonebot.example.quest.QuestLog;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.movement.Movement;

import java.util.Locale;

/**
 * Doric's hut — same coords as clue {@code DoricHouseApproachHelper} (no script-clue dep).
 */
public final class QuestDoricHouseApproachHelper {

    public static final WorldPoint DORIC_TILE = new WorldPoint(2951, 3451, 0);
    private static final WorldPoint DORIC_HOUSE_ANCHOR = new WorldPoint(2952, 3451, 0);
    private static final int DORIC_INSIDE_TILE_RADIUS = 1;
    private static final WorldPoint DORIC_DOOR_TILE = new WorldPoint(2950, 3450, 0);
    private static final WorldPoint DORIC_DOOR_APPROACH_TILE = new WorldPoint(2950, 3449, 0);

    private static long lastWalkMs;
    private static long lastDoorMs;

    private QuestDoricHouseApproachHelper() {
    }

    public static void reset() {
        lastWalkMs = 0L;
        lastDoorMs = 0L;
    }

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

    /**
     * Approach + Talk-to + dialog. {@code true} if action taken.
     */
    public static boolean talk(QuestHelperDialogSteps dialog) {
        if (QuestDialogHelper.handle(dialog)) {
            return true;
        }
        int travel = approachBeforeTalk();
        if (travel > 0) {
            return true;
        }
        WorldPoint me = QuestActions.local();
        if (me == null || !canTalkToDoricNow(me)) {
            return true;
        }
        INPC doric = findDoric();
        if (doric == null) {
            QuestLog.step("Doric", "NPC niet in scene — walk hut");
            issueWalk(DORIC_TILE);
            return true;
        }
        if (doric.interact("Talk-to") || doric.interact("Talk")) {
            QuestLog.step("Doric", "Talk-to");
            return true;
        }
        return false;
    }

    /** @return delay &gt; 0 if still approaching; 0 = ready */
    public static int approachBeforeTalk() {
        WorldPoint me = QuestActions.local();
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
            issueWalk(DORIC_HOUSE_ANCHOR);
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
        issueWalk(DORIC_HOUSE_ANCHOR);
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
        QuestLog.step("Doric", "hut deur Open @ " + door.getWorldLocation());
        return true;
    }

    private static void issueWalk(WorldPoint target) {
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900L && Movement.isWalking()) {
            return;
        }
        lastWalkMs = now;
        QuestLog.step("Doric", "→ " + target.getX() + "," + target.getY());
        Movement.walkTo(target);
    }
}
