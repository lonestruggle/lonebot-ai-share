package com.lonebot.example.woodcutter;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.MovementHelper;

import java.util.List;
import java.util.Locale;

final class BirdNestHandler {

    private static final int MIN_FREE_SLOTS = 2;
    private static final long ACTION_COOLDOWN_MS = 1200L;

    static final int[] BIRD_NEST_ITEM_IDS = {
            5070, 5071, 5072, 5073, 5074, 5075, 5076, 5077, 5078
    };
    static final int[] CLUE_NEST_ITEM_IDS = {23127, 19712, 19714, 19716, 19718};

    private static long lastActionMs;

    private BirdNestHandler() {
    }

    static boolean isBirdNestName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String low = name.toLowerCase(Locale.ROOT);
        if (low.contains("clue nest")) {
            return false;
        }
        return low.contains("bird nest") || low.contains("bird's nest") || low.contains("birds nest");
    }

    static boolean isClueNestName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("clue nest");
    }

    static boolean isNestId(int id) {
        for (int n : BIRD_NEST_ITEM_IDS) {
            if (n == id) {
                return true;
            }
        }
        for (int n : CLUE_NEST_ITEM_IDS) {
            if (n == id) {
                return true;
            }
        }
        return false;
    }

    static int tick(WorldPoint workCenter, int workRadius) {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 0;
        }
        // Clues doen: nest/scroll-ownership → ClueSkillHandoff (niet mid-WC Search-and-forget)
        if (BotRuntime.wcClueSolver) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastActionMs < ACTION_COOLDOWN_MS) {
            return 0;
        }
        IInventoryItem inv = Inventory.getFirst(i -> i != null && (isNestId(i.getId())
                || isBirdNestName(i.getName()) || isClueNestName(i.getName())));
        if (inv != null) {
            int dropDelay = ensureMinFreeSlots(MIN_FREE_SLOTS);
            if (dropDelay > 0) {
                return dropDelay;
            }
            if (Inventory.getFreeSlots() < MIN_FREE_SLOTS) {
                return 0;
            }
            if (inv.hasAction("Search")) {
                inv.interact("Search");
            } else if (inv.hasAction("Open")) {
                inv.interact("Open");
            } else {
                inv.interact("Search");
            }
            lastActionMs = now;
            WcDebug.once("nest", "Search " + inv.getName());
            return Rand.nextInt(700, 1200);
        }
        ITileItem ground = TileItems.getNearest(item -> {
            if (item == null) {
                return false;
            }
            if (!isNestId(item.getId()) && !isBirdNestName(item.getName()) && !isClueNestName(item.getName())) {
                return false;
            }
            WorldPoint p = item.getWorldLocation();
            if (p == null) {
                return false;
            }
            if (workCenter != null && workRadius > 0 && p.distanceTo(workCenter) > workRadius + 8) {
                return false;
            }
            return me.worldLocation.distanceTo(p) <= 20;
        });
        if (ground == null) {
            return 0;
        }
        WorldPoint tile = ground.getWorldLocation();
        if (tile != null && me.worldLocation.distanceTo(tile) > 2) {
            MovementHelper.walkTo(tile);
            lastActionMs = now;
            WcDebug.log("nest", "→ nest @" + tile.getX() + "," + tile.getY());
            return Rand.nextInt(600, 1100);
        }
        int dropDelay = ensureMinFreeSlots(MIN_FREE_SLOTS);
        if (dropDelay > 0) {
            return dropDelay;
        }
        if (Inventory.getFreeSlots() < MIN_FREE_SLOTS) {
            return 0;
        }
        if (!ground.pickup()) {
            ground.interact("Take");
        }
        lastActionMs = now;
        WcDebug.once("nest", "Take " + ground.getName()
                + (tile != null ? " @" + tile.getX() + "," + tile.getY() : ""));
        return Rand.nextInt(700, 1200);
    }

    /** Drop één log-stack om slots vrij te maken voor nest Search/Take. */
    private static int ensureMinFreeSlots(int minFree) {
        if (Inventory.getFreeSlots() >= minFree) {
            return 0;
        }
        for (String logName : WcTrees.LOG_NAMES) {
            List<IInventoryItem> logs = Inventory.getAll(logName);
            if (logs == null || logs.isEmpty()) {
                continue;
            }
            IInventoryItem log = logs.get(0);
            if (log.hasAction("Drop")) {
                log.interact("Drop");
            } else {
                log.interact("Drop");
            }
            WcDebug.log("nest", logName + " gedropt voor " + minFree + " vrije slots");
            return Rand.nextInt(280, 520);
        }
        IInventoryItem anyLog = Inventory.getFirst(i -> i != null && WcTrees.isLogName(i.getName()));
        if (anyLog != null) {
            anyLog.interact("Drop");
            WcDebug.log("nest", "log gedropt voor nest slots");
            return Rand.nextInt(280, 520);
        }
        return 0;
    }
}
