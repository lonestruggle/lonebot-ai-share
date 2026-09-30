package net.storm.sdk.game;

import net.runelite.api.Client;
import net.runelite.api.Skill;

/**
 * Skill level / XP helpers — reads via {@link Static#callOnClientThread}.
 */
public final class Skills {

    private Skills() {
    }

    public static int getLevel(Skill skill) {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || skill == null) {
                return 1;
            }
            return c.getRealSkillLevel(skill);
        }, 1);
        return v != null ? v : 1;
    }

    public static int getBoostedLevel(Skill skill) {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || skill == null) {
                return 1;
            }
            return c.getBoostedSkillLevel(skill);
        }, 1);
        return v != null ? v : 1;
    }

    public static int getExperience(Skill skill) {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || skill == null) {
                return 0;
            }
            return c.getSkillExperience(skill);
        }, 0);
        return v != null ? v : 0;
    }

    /** F2P-skills (non-member) — drempel voor 500/750 skill-total werelden. */
    private static final Skill[] F2P_SKILLS = {
            Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC,
            Skill.RUNECRAFT, Skill.HITPOINTS, Skill.CRAFTING, Skill.MINING, Skill.SMITHING,
            Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING
    };

    /** Som van F2P-skill levels (geen members-skills). */
    public static int getF2pTotalLevel() {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            int sum = 0;
            for (Skill s : F2P_SKILLS) {
                try {
                    sum += c.getRealSkillLevel(s);
                } catch (Throwable ignored) {
                }
            }
            return sum;
        }, 0);
        return v != null ? v : 0;
    }

    /** Total level from client (excludes OVERALL skill). */
    public static int getTotalLevel() {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            try {
                return c.getTotalLevel();
            } catch (Throwable t) {
                int sum = 0;
                for (Skill s : Skill.values()) {
                    if (s == Skill.OVERALL) {
                        continue;
                    }
                    sum += c.getRealSkillLevel(s);
                }
                return sum;
            }
        }, 0);
        return v != null ? v : 0;
    }
}
