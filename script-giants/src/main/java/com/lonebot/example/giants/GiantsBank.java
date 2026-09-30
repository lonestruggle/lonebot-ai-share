package com.lonebot.example.giants;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.HumanBanking;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.utils.AntiBan;

import java.util.ArrayList;
import java.util.List;

/**
 * Deposit loot, withdraw food + Brass key + style gear/runes.
 * Withdraw-1 for gear, Withdraw-All for stackables/runes/arrows.
 */
public final class GiantsBank {

    private GiantsBank() {
    }

    public static WorldPoint preferBank(WorldPoint pos, boolean afterGe, boolean afterGroundKey) {
        if (afterGe) {
            return BankHelper.GE_BANK;
        }
        if (afterGroundKey || (pos != null && (GiantsLocations.isUnderground(pos)
                || GiantsLocations.isNearTrapdoor(pos)))) {
            return BankHelper.EDGEVILLE_BANK;
        }
        if (pos != null && pos.distanceTo(BankHelper.GE_BANK) <= 18) {
            return BankHelper.GE_BANK;
        }
        return BankHelper.EDGEVILLE_BANK;
    }

    public static int walkOpen(WorldPoint pos, WorldPoint bankTile) {
        WorldPoint dest = bankTile != null ? bankTile : BankHelper.EDGEVILLE_BANK;
        if (Bank.isOpen()) {
            return 0;
        }
        if (pos != null && pos.distanceTo(dest) > 8) {
            GiantsWalk.Result w = GiantsWalk.toward(pos, dest, 6, "bank");
            return w.delayMs;
        }
        BankHelper.openNearestBank();
        AntiBan.get().markBotActivity();
        GiantsLog.action("bank", "open");
        return HumanBanking.openRetryMs();
    }

    public static int depositLoot(GiantsTypes.GiantsCombatStyle style, String lootCsv) {
        if (!Bank.isOpen()) {
            return 0;
        }
        List<String> keep = keepNames(style);
        for (IInventoryItem i : Inventory.getAll()) {
            if (i == null || i.getName() == null) {
                continue;
            }
            String n = i.getName();
            if (isKeep(n, keep, style)) {
                continue;
            }
            Bank.depositAll(n);
            GiantsLog.action("bank", "deposit " + n);
            AntiBan.get().markBotActivity();
            return HumanBanking.afterActionMs();
        }
        return 0;
    }

    public static int withdrawFood(int amount) {
        if (!Bank.isOpen() || amount <= 0) {
            return 0;
        }
        int have = GiantsLoot.foodCount();
        if (have >= amount) {
            return 0;
        }
        for (String f : GiantsLoot.FOOD_NAMES) {
            if (!Bank.contains(f)) {
                continue;
            }
            Bank.withdrawAll(f);
            GiantsLog.action("bank", "withdraw-all " + f);
            AntiBan.get().markBotActivity();
            return HumanBanking.afterActionMs();
        }
        return 0;
    }

    public static int withdrawKey() {
        if (!Bank.isOpen() || GiantsKey.hasOnPerson()) {
            return 0;
        }
        if (GiantsKey.withdrawFromOpenBank()) {
            AntiBan.get().markBotActivity();
            return HumanBanking.afterActionMs();
        }
        return 0;
    }

    public static boolean foodInBank() {
        if (!Bank.isOpen()) {
            return false;
        }
        for (String f : GiantsLoot.FOOD_NAMES) {
            if (Bank.contains(f)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKeep(String name, List<String> keep, GiantsTypes.GiantsCombatStyle style) {
        if (name == null) {
            return false;
        }
        for (String k : keep) {
            if (k.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return GiantsLoot.isKeepName(name, style);
    }

    private static List<String> keepNames(GiantsTypes.GiantsCombatStyle style) {
        List<String> keep = new ArrayList<>();
        keep.add("Coins");
        keep.add(GiantsLocations.BRASS_KEY_NAME);
        keep.add("Amulet of power");
        for (String f : GiantsLoot.FOOD_NAMES) {
            keep.add(f);
        }
        if (style != null) {
            keep.addAll(com.lonebot.example.gear.StyleArmourHelper.armourItemNames(
                    GiantsGear.gearStyle(style),
                    com.lonebot.example.gear.ActivityGearProfile.GIANTS.loadout));
        }
        return keep;
    }

    public static boolean inventoryFullOfLoot(GiantsTypes.GiantsCombatStyle style, String lootCsv) {
        if (!Inventory.isFull()) {
            return false;
        }
        return GiantsLoot.dropJunk(style, lootCsv) == 0;
    }
}
