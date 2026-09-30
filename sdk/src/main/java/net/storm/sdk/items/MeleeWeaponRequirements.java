package net.storm.sdk.items;

import net.runelite.api.Skill;
import net.storm.sdk.game.Skills;

import java.util.Locale;

/**
 * OSRS melee Attack requirements. "Best weapon" = highest tier you can wield now.
 * Port of CombatBot {@code MeleeWeaponRequirements}.
 */
public final class MeleeWeaponRequirements {

    private MeleeWeaponRequirements() {
    }

    public static int requiredAttackLevel(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return 99;
        }
        String n = itemName.toLowerCase(Locale.ROOT);
        if (n.contains("dragon") || n.contains("abyssal") || n.contains("granite maul")) {
            return 60;
        }
        if (n.contains("rune")) {
            return 40;
        }
        if (n.contains("adamant")) {
            return 30;
        }
        if (n.contains("mithril")) {
            return 20;
        }
        if (n.contains("black") || n.contains("white")) {
            return 10;
        }
        if (n.contains("steel")) {
            return 5;
        }
        return 1;
    }

    public static int getAttackLevelSafe() {
        try {
            return Skills.getLevel(Skill.ATTACK);
        } catch (Throwable ignored) {
            return 1;
        }
    }

    public static boolean canWield(String itemName) {
        return canWield(itemName, getAttackLevelSafe());
    }

    public static boolean canWield(String itemName, int attackLevel) {
        return attackLevel >= requiredAttackLevel(itemName);
    }

    /** First item from priority list that is in the open bank and wieldable. */
    public static String firstUsableInBank(String[] priorityBestFirst) {
        if (priorityBestFirst == null || !Bank.isOpen()) {
            return null;
        }
        int atk = getAttackLevelSafe();
        for (String weapon : priorityBestFirst) {
            try {
                if (canWield(weapon, atk) && Bank.contains(weapon)) {
                    return weapon;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
