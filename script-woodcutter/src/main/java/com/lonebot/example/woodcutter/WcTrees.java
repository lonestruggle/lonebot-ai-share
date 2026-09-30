package com.lonebot.example.woodcutter;

import net.runelite.api.Skill;
import net.storm.sdk.game.Skills;

import java.util.Locale;

public final class WcTrees {

    public static final String[][] TREE_LEVELS = {
            {"Tree", "1", "Logs"},
            {"Oak", "15", "Oak logs"},
            {"Willow", "30", "Willow logs"},
            {"Teak", "35", "Teak logs"},
            {"Maple", "45", "Maple logs"},
            {"Mahogany", "50", "Mahogany logs"},
            {"Yew", "60", "Yew logs"},
            {"Magic", "75", "Magic logs"},
            {"Redwood", "90", "Redwood logs"}
    };

    static final String[] LOG_NAMES = {
            "Logs", "Oak logs", "Willow logs", "Teak logs", "Maple logs",
            "Mahogany logs", "Yew logs", "Magic logs", "Redwood logs"
    };

    /** Unnoted log item ids (OSRS). */
    static final int[] LOG_IDS = {
            1511, 1521, 1519, 6333, 1517, 6332, 1515, 1513, 19669
    };

    static final int TINDERBOX_ID = 590;

    private WcTrees() {
    }

    public static int wcLevel() {
        return Skills.getLevel(Skill.WOODCUTTING);
    }

    static int fmLevel() {
        return Skills.getLevel(Skill.FIREMAKING);
    }

    public static String bestTreeForLevel(int wc) {
        String best = "Tree";
        for (String[] e : TREE_LEVELS) {
            if (wc >= Integer.parseInt(e[1])) {
                best = e[0];
            }
        }
        return best;
    }

    static int requiredWcForTree(String treeKeyword) {
        if (treeKeyword == null) {
            return 1;
        }
        String lower = treeKeyword.toLowerCase(Locale.ROOT);
        for (String[] e : TREE_LEVELS) {
            if (e[0].equalsIgnoreCase("Tree")) {
                continue;
            }
            if (lower.contains(e[0].toLowerCase(Locale.ROOT))) {
                return Integer.parseInt(e[1]);
            }
        }
        return 1;
    }

    static boolean matchesTree(String objectName, String keyword) {
        if (objectName == null || keyword == null) {
            return false;
        }
        String n = objectName.toLowerCase(Locale.ROOT).trim();
        String k = keyword.toLowerCase(Locale.ROOT).trim();
        if (k.equals("tree")) {
            return n.equals("tree");
        }
        return n.contains(k);
    }

    static boolean isLogName(String name) {
        if (name == null) {
            return false;
        }
        String l = name.toLowerCase(Locale.ROOT);
        return l.equals("logs") || l.endsWith(" logs");
    }

    static boolean isLogId(int id) {
        for (int logId : LOG_IDS) {
            if (logId == id) {
                return true;
            }
        }
        return false;
    }

    static int requiredFmForLogId(int id) {
        switch (id) {
            case 1511:
                return 1;
            case 1521:
                return 15;
            case 1519:
                return 30;
            case 6333:
                return 35;
            case 1517:
                return 45;
            case 6332:
                return 50;
            case 1515:
                return 60;
            case 1513:
                return 75;
            case 19669:
                return 90;
            default:
                return 1;
        }
    }

    static int requiredFmForLog(String logName) {
        if (logName == null) {
            return 1;
        }
        for (String[] e : TREE_LEVELS) {
            if (e[2].equalsIgnoreCase(logName)) {
                return Integer.parseInt(e[1]);
            }
        }
        return 1;
    }

    static boolean isWcAnim(int anim) {
        return net.storm.sdk.game.Animations.isWoodcutting(anim);
    }

    static boolean isBonfireAnim(int anim) {
        return net.storm.sdk.game.Animations.isFiremakingRelated(anim);
    }
}
