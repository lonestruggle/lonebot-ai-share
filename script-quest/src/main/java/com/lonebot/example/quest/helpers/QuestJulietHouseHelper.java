package com.lonebot.example.quest.helpers;

import com.lonebot.example.quest.QuestLog;
import net.runelite.api.coords.WorldPoint;

/**
 * Juliet's house west of Varrock: door → GF zone → stairs → balcony/upstairs.
 */
public final class QuestJulietHouseHelper {

    public static final WorldPoint HOUSE_DOOR = new WorldPoint(3158, 3434, 0);
    public static final WorldPoint GF_STAND = new WorldPoint(3157, 3435, 0);
    public static final WorldPoint STAIRS_UP = new WorldPoint(3157, 3436, 0);
    public static final WorldPoint STAIRS_DOWN = new WorldPoint(3156, 3435, 1);
    public static final WorldPoint JULIET = new WorldPoint(3158, 3427, 1);

    private static final int GF_X1 = 3148;
    private static final int GF_X2 = 3166;
    private static final int GF_Y1 = 3428;
    private static final int GF_Y2 = 3443;

    private static final int UP_X1 = 3147;
    private static final int UP_X2 = 3166;
    private static final int UP_Y1 = 3425;
    private static final int UP_Y2 = 3443;

    private QuestJulietHouseHelper() {
    }

    public static boolean inJulietRoom() {
        WorldPoint me = QuestActions.local();
        return me != null && me.getPlane() == 1
                && me.getX() >= UP_X1 && me.getX() <= UP_X2
                && me.getY() >= UP_Y1 && me.getY() <= UP_Y2;
    }

    public static boolean inGroundFloor() {
        WorldPoint me = QuestActions.local();
        return me != null && me.getPlane() == 0
                && me.getX() >= GF_X1 && me.getX() <= GF_X2
                && me.getY() >= GF_Y1 && me.getY() <= GF_Y2;
    }

    /**
     * Reach Juliet upstairs. {@code true} if an action was taken (or already there).
     */
    public static boolean goToJuliet() {
        if (inJulietRoom()) {
            return false;
        }
        if (inGroundFloor()) {
            QuestLog.step("Juliet", "stairs omhoog");
            return QuestActions.interactObjectAny(STAIRS_UP, "Climb-up", "Staircase", "Stairs")
                    || QuestActions.walkTo(STAIRS_UP, 1);
        }
        WorldPoint me = QuestActions.local();
        if (me != null && me.getPlane() == 0 && me.distanceTo(HOUSE_DOOR) <= 6) {
            if (QuestActions.interactObject("Door", "Open", HOUSE_DOOR)) {
                return true;
            }
            QuestLog.step("Juliet", "GF zone na deur");
            return !QuestActions.walkTo(GF_STAND, 1);
        }
        QuestLog.step("Juliet", "naar huisdeur " + HOUSE_DOOR.getX() + "," + HOUSE_DOOR.getY());
        QuestActions.walkTo(HOUSE_DOOR, 2);
        return true;
    }

    /** Leave upstairs toward Varrock square. */
    public static boolean leaveToGround() {
        if (!inJulietRoom()) {
            return false;
        }
        QuestLog.step("Juliet", "stairs omlaag");
        return QuestActions.interactObjectAny(STAIRS_DOWN, "Climb-down", "Staircase", "Stairs")
                || QuestActions.walkTo(STAIRS_DOWN, 1);
    }
}
