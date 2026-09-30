package com.lonebot.example.quest.helpers;

import com.lonebot.example.quest.QuestLog;
import net.runelite.api.coords.WorldPoint;

/**
 * Wizards' Tower basement ladder / region (CombatBot {@code WizardsTowerBasementHelper}).
 */
public final class WizardsTowerBasementHelper {

    public static final WorldPoint LADDER_SURFACE = new WorldPoint(3104, 3162, 0);
    public static final WorldPoint TOWER_APPROACH = new WorldPoint(3109, 3167, 0);
    public static final WorldPoint SEDRIDOR = new WorldPoint(3104, 9571, 0);

    private static final int BASE_X1 = 3094;
    private static final int BASE_X2 = 3125;
    private static final int BASE_Y1 = 9553;
    private static final int BASE_Y2 = 9582;

    /** Sedridor talk zone in basement (CombatBot). */
    private static final int SED_X1 = 3102;
    private static final int SED_X2 = 3110;
    private static final int SED_Y1 = 9568;
    private static final int SED_Y2 = 9574;

    private WizardsTowerBasementHelper() {
    }

    public static boolean inSedridorTalkZone(WorldPoint me) {
        if (me == null || me.getPlane() != 0) {
            return false;
        }
        if (!inBasementPoint(me)) {
            return false;
        }
        return me.getX() >= SED_X1 && me.getX() <= SED_X2
                && me.getY() >= SED_Y1 && me.getY() <= SED_Y2;
    }

    private static boolean inBasementPoint(WorldPoint me) {
        return me.getX() >= BASE_X1 && me.getX() <= BASE_X2
                && me.getY() >= BASE_Y1 && me.getY() <= BASE_Y2;
    }

    public static boolean inBasement() {
        WorldPoint me = QuestActions.local();
        return me != null && me.getPlane() == 0 && inBasementPoint(me);
    }

    /**
     * @return {@code true} if still travelling (not yet in basement)
     */
    public static boolean ensureBasement() {
        if (inBasement()) {
            return false;
        }
        WorldPoint me = QuestActions.local();
        if (me != null && me.getPlane() == 0 && me.distanceTo(LADDER_SURFACE) <= 8) {
            QuestLog.step("Tower", "ladder naar kelder");
            if (QuestActions.interactObjectAny(LADDER_SURFACE, "Climb-down", "Ladder")) {
                return true;
            }
        }
        QuestLog.step("Tower", "naar Wizards' Tower ladder");
        QuestActions.walkTo(LADDER_SURFACE, 2);
        return true;
    }

    /** Climb back to the surface. */
    public static boolean leaveBasement() {
        if (!inBasement()) {
            return false;
        }
        QuestLog.step("Tower", "ladder omhoog");
        return QuestActions.interactObjectAny(new WorldPoint(3104, 9576, 0),
                "Climb-up", "Ladder");
    }
}
