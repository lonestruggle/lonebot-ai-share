package com.lonebot.example.giants;

import net.storm.sdk.bot.ClueSkillHandoff;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.interact.GroundLootPickupHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.utils.AntiBan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mine-loot only, bury bones, drop junk. Keep coins / key / food / style kit.
 */
public final class GiantsLoot {

    public static final String[] FOOD_NAMES = {
            "Shrimps", "Anchovies", "Sardine", "Herring", "Mackerel", "Trout", "Salmon",
            "Tuna", "Lobster", "Swordfish", "Monkfish", "Shark", "Bread", "Cake", "Meat pie",
            "Cooked meat", "Cooked chicken", "Wine", "Jug of wine", "Beer", "Cabbage"
    };

    private static final String[] BONES = {"Big bones", "Bones"};

    private GiantsLoot() {
    }

    public static List<String> parseLoot(String csv) {
        List<String> out = new ArrayList<>();
        String src = csv != null && !csv.isBlank() ? csv : GiantsLocations.DEFAULT_LOOT_CSV;
        for (String p : src.split(",")) {
            String t = p != null ? p.trim() : "";
            if (!t.isEmpty() && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean isFoodName(String n) {
        if (n == null) {
            return false;
        }
        for (String f : FOOD_NAMES) {
            if (f.equalsIgnoreCase(n)) {
                return true;
            }
        }
        String lower = n.toLowerCase(Locale.ROOT);
        return lower.contains("potion") || lower.equals("wine") || lower.equals("jug of wine");
    }

    public static boolean hasFood() {
        for (String f : FOOD_NAMES) {
            if (Inventory.contains(f)) {
                return true;
            }
        }
        return Inventory.contains(i -> i != null && i.getName() != null && isFoodName(i.getName()));
    }

    public static int foodCount() {
        int n = 0;
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && isFoodName(i.getName())) {
                n += Math.max(1, i.getQuantity());
            }
        }
        return n;
    }

    public static int eatOrDrink() {
        for (String f : FOOD_NAMES) {
            IInventoryItem food = Inventory.getFirst(f);
            if (food != null) {
                return eatItem(food);
            }
        }
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && isFoodName(i.getName())) {
                return eatItem(i);
            }
        }
        return 0;
    }

    private static int eatItem(IInventoryItem food) {
        try {
            if (food.hasAction("Eat")) {
                food.interact("Eat");
            } else if (food.hasAction("Drink")) {
                food.interact("Drink");
            } else {
                food.interact("Eat");
            }
        } catch (Throwable t) {
            try {
                food.interact("Drink");
            } catch (Throwable ignored) {
            }
        }
        AntiBan.get().markBotActivity();
        GiantsLog.action("eat", food.getName());
        return ThreadLocalRandom.current().nextInt(600, 1000);
    }

    public static int buryBones() {
        for (String b : BONES) {
            IInventoryItem item = Inventory.getFirst(b);
            if (item != null) {
                try {
                    item.interact("Bury");
                } catch (Throwable ignored) {
                }
                AntiBan.get().markBotActivity();
                GiantsLog.action("bury", b);
                return ThreadLocalRandom.current().nextInt(350, 650);
            }
        }
        return 0;
    }

    public static ITileItem findLoot(WorldPoint pos, int radius, String lootCsv) {
        List<String> names = parseLoot(lootCsv);
        return TileItems.getNearest(item -> {
            if (item == null || item.getName() == null || item.getWorldLocation() == null) {
                return false;
            }
            if (pos != null && item.getWorldLocation().distanceTo(pos) > Math.max(8, radius)) {
                return false;
            }
            if (!isWantedLoot(item.getName(), names)) {
                return false;
            }
            boolean stackable = isStackableLoot(item);
            return !Inventory.isFull() || stackable;
        });
    }

    public static int pickup(ITileItem item) {
        if (item == null) {
            return 0;
        }
        GroundLootPickupHelper.Result r = GroundLootPickupHelper.pickup(item);
        AntiBan.get().markBotActivity();
        GiantsLog.action("loot", (r != null && r.ok ? "Take " : "Take fail ") + item.getName());
        return ThreadLocalRandom.current().nextInt(180, 360);
    }

    public static boolean isWantedLoot(String name, List<String> lootNames) {
        if (name == null) {
            return false;
        }
        for (String loot : lootNames) {
            if (loot.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStackableLoot(ITileItem item) {
        if (item == null) {
            return false;
        }
        try {
            if (Bank.isStackable(item.getId())) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String n = item.getName() != null ? item.getName().toLowerCase(Locale.ROOT) : "";
        return n.contains("rune") || n.contains("arrow") || n.contains("coins")
                || n.contains("ore") || n.equals("coal");
    }

    public static boolean isKeepName(String name, GiantsTypes.GiantsCombatStyle style) {
        if (name == null) {
            return false;
        }
        if (name.equalsIgnoreCase(GiantsLocations.BRASS_KEY_NAME) || name.equalsIgnoreCase("Coins")) {
            return true;
        }
        if (isFoodName(name)) {
            return true;
        }
        return GiantsGear.isStyleKeepName(style, name);
    }

    public static int dropJunk(GiantsTypes.GiantsCombatStyle style, String lootCsv) {
        List<String> loot = parseLoot(lootCsv);
        for (IInventoryItem i : Inventory.getAll()) {
            if (i == null || i.getName() == null) {
                continue;
            }
            String n = i.getName();
            if (isKeepName(n, style) || isWantedLoot(n, loot)) {
                continue;
            }
            if (ClueSkillHandoff.shouldKeepClueItem(n)) {
                continue;
            }
            try {
                if (i.hasAction("Drop")) {
                    i.interact("Drop");
                    GiantsLog.action("drop", n);
                    AntiBan.get().markBotActivity();
                    return ThreadLocalRandom.current().nextInt(280, 480);
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }
}
