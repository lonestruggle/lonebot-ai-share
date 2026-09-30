package com.lonebot.example.gear;

import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;

/**
 * Item-namen: plain én variants (trim/gilded/heraldic).
 * <p>
 * OSRS: {@code Rune platebody}, {@code Rune platebody (t)}, {@code Rune platebody (g)},
 * {@code Adamant full helm (h1)} … — zelfde stats/reqs, andere cosmetica.
 */
public final class GearItemNames {

    private GearItemNames() {
    }

    /**
     * {@code canonical} = tier-naam zonder suffix.
     * Matcht exact of {@code canonical + " ("…")"} (t/g/h1/…).
     */
    public static boolean matches(String canonical, String actual) {
        if (canonical == null || actual == null) {
            return false;
        }
        String c = canonical.trim();
        String a = actual.trim();
        if (c.isEmpty() || a.isEmpty()) {
            return false;
        }
        if (c.equalsIgnoreCase(a)) {
            return true;
        }
        if (a.length() <= c.length()) {
            return false;
        }
        if (!a.regionMatches(true, 0, c, 0, c.length())) {
            return false;
        }
        // Alleen cosmetische suffix: " (t)", " (g)", " (h1)", …
        return a.charAt(c.length()) == ' ' && a.charAt(c.length() + 1) == '(';
    }

    public static String findInEquipment(String canonical) {
        for (IInventoryItem i : Equipment.getAll()) {
            if (i != null && matches(canonical, i.getName())) {
                return i.getName();
            }
        }
        return null;
    }

    public static String findInInventory(String canonical) {
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && matches(canonical, i.getName())) {
                return i.getName();
            }
        }
        return null;
    }

    public static String findInBank(String canonical) {
        if (!Bank.isOpen()) {
            return null;
        }
        for (IInventoryItem i : Bank.getAll()) {
            if (i != null && matches(canonical, i.getName())) {
                return i.getName();
            }
        }
        return null;
    }

    /** Prefer exact bank name, else eerste (t)/(g)/… variant. */
    public static String findBestNameOnPerson(String canonical) {
        String eq = findInEquipment(canonical);
        if (eq != null) {
            return eq;
        }
        return findInInventory(canonical);
    }

    public static boolean ownedOnPerson(String canonical) {
        return findBestNameOnPerson(canonical) != null;
    }

    public static boolean ownedInBank(String canonical) {
        return findInBank(canonical) != null;
    }

    public static IInventoryItem firstInventoryItem(String canonical) {
        return Inventory.getFirst(i -> i != null && matches(canonical, i.getName()));
    }
}
