package com.lonebot.example.starminer;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;

/**
 * Crafting Guild crash-site zit binnen de muren — Crafting 40 + brown apron / crafting cape.
 */
final class StarCraftingGuild {

    static final String SPOT_KEY = "CRAFTING_GUILD";
    static final int BROWN_APRON = 1757;
    static final int CRAFTING_CAPE = 9780;
    static final int CRAFTING_CAPE_T = 9781;
    private static final int NEED_CRAFTING = 40;
    /** Guild-binnenruimte (gold rocks / workshop). */
    private static final int IN_X1 = 2929, IN_X2 = 2943, IN_Y1 = 3278, IN_Y2 = 3292;
    private static final WorldPoint DOOR = new WorldPoint(2933, 3290, 0);

    private static long lastLogMs;
    private static String lastLog = "";

    private StarCraftingGuild() {
    }

    static boolean isSpot(StarLocations.Spot spot) {
        return spot != null && SPOT_KEY.equals(spot.key);
    }

    static boolean inside(WorldPoint p) {
        return p != null && p.getPlane() == 0
                && p.getX() >= IN_X1 && p.getX() <= IN_X2
                && p.getY() >= IN_Y1 && p.getY() <= IN_Y2;
    }

    static int craftingLevel() {
        try {
            return Skills.getLevel(Skill.CRAFTING);
        } catch (Throwable t) {
            return 0;
        }
    }

    static boolean hasEntryItem() {
        try {
            if (Equipment.contains(BROWN_APRON, CRAFTING_CAPE, CRAFTING_CAPE_T)) {
                return true;
            }
            return Inventory.contains(BROWN_APRON, CRAFTING_CAPE, CRAFTING_CAPE_T)
                    || Inventory.contains("Brown apron", "Crafting cape", "Crafting cape(t)");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Mag deze crash-site bezoeken? Al binnen = ok (apron alleen voor deur). */
    static boolean canVisit() {
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            if (me != null && inside(me.worldLocation)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (craftingLevel() < NEED_CRAFTING) {
            return false;
        }
        return hasEntryItem();
    }

    static String blockReason() {
        if (canVisit()) {
            return null;
        }
        if (craftingLevel() < NEED_CRAFTING) {
            return "Crafting " + craftingLevel() + "/" + NEED_CRAFTING;
        }
        return "geen brown apron / crafting cape";
    }

    /** Wield apron vóór deur (cape mag al equipped zijn). */
    static boolean tryWearEntry() {
        try {
            if (Equipment.contains(BROWN_APRON, CRAFTING_CAPE, CRAFTING_CAPE_T)) {
                return false;
            }
            IInventoryItem item = Inventory.getFirst(CRAFTING_CAPE, CRAFTING_CAPE_T, BROWN_APRON);
            if (item == null) {
                item = Inventory.getFirst("Crafting cape", "Crafting cape(t)", "Brown apron");
            }
            if (item == null) {
                return false;
            }
            boolean ok = item.interact("Wear") || item.interact("Wield") || item.interact("Equip");
            if (ok) {
                log("wear entry-item");
            }
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Open guild-deur als we buiten staan en entry-item hebben.
     * @return true als interact gedaan
     */
    static boolean tryOpenDoor(WorldPoint pos) {
        if (pos == null || inside(pos)) {
            return false;
        }
        if (!hasEntryItem() && craftingLevel() < NEED_CRAFTING) {
            return false;
        }
        if (tryWearEntry()) {
            return true;
        }
        ITileObject door = null;
        try {
            door = TileObjects.getNearest(o -> {
                if (o == null || o.getWorldLocation() == null) {
                    return false;
                }
                WorldPoint t = o.getWorldLocation();
                if (t.getPlane() != 0 || t.distanceTo(DOOR) > 3) {
                    return false;
                }
                try {
                    return o.hasAction("Open") || o.hasAction("Walk-through");
                } catch (Throwable e) {
                    return false;
                }
            });
        } catch (Throwable ignored) {
        }
        if (door == null) {
            return false;
        }
        try {
            boolean ok = door.interact("Open") || door.interact("Walk-through");
            if (ok) {
                log("Open Crafting Guild deur");
            }
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/cguild] " + msg);
    }
}
