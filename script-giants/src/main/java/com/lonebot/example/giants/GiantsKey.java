package com.lonebot.example.giants;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Worlds;
import net.storm.sdk.interact.GroundLootPickupHelper;
import net.storm.sdk.interact.TileObjectInteractHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.GeRestockHelper;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.utils.AntiBan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Brass key: inv/equip/bank → GE buy → Edgeville ground spawn + hop-ladder wait.
 */
public final class GiantsKey {

    public static final long HOP_WAIT_MS = 180_000L;
    public static final int GE_KEY_PRICE = 2000;

    private GiantsKey() {
    }

    public static boolean hasOnPerson() {
        return Inventory.contains(GiantsLocations.BRASS_KEY_ID)
                || Inventory.contains(GiantsLocations.BRASS_KEY_NAME)
                || Equipment.contains(GiantsLocations.BRASS_KEY_ID)
                || Equipment.contains(GiantsLocations.BRASS_KEY_NAME);
    }

    public static boolean hasInOpenBank() {
        return Bank.isOpen() && (Bank.contains(GiantsLocations.BRASS_KEY_ID)
                || Bank.contains(GiantsLocations.BRASS_KEY_NAME));
    }

    public static boolean withdrawFromOpenBank() {
        if (!hasInOpenBank()) {
            return false;
        }
        boolean ok = Bank.withdraw(GiantsLocations.BRASS_KEY_NAME, 1)
                || Bank.withdraw(GiantsLocations.BRASS_KEY_ID, 1);
        if (ok) {
            GiantsLog.action("key", "withdraw Brass key");
        }
        return ok;
    }

    public static int coinsOnPerson() {
        return Inventory.getCount("Coins");
    }

    public static boolean canAffordGe() {
        return coinsOnPerson() >= 50 || (Bank.isOpen() && Bank.contains("Coins"));
    }

    public static ITileItem groundKey(WorldPoint pos) {
        ITileItem byId = TileItems.getNearest(GiantsLocations.BRASS_KEY_ID);
        if (byId != null) {
            return byId;
        }
        return TileItems.getNearest(item -> item != null
                && item.getName() != null
                && item.getName().equalsIgnoreCase(GiantsLocations.BRASS_KEY_NAME)
                && (pos == null || item.getWorldLocation() == null
                || item.getWorldLocation().distanceTo(pos) < 40));
    }

    public static int pickupGround(ITileItem key) {
        if (key == null) {
            return 0;
        }
        GroundLootPickupHelper.Result r = GroundLootPickupHelper.pickup(key);
        AntiBan.get().markBotActivity();
        GiantsLog.action("key", (r != null && r.ok ? "pak " : "pak-fail ") + "Brass key");
        return ThreadLocalRandom.current().nextInt(400, 700);
    }

    public static int buyAtGe() {
        GiantsLog.action("key", "GE buy Brass key");
        boolean ok = GeRestockHelper.buyWithEscalation(GiantsLocations.BRASS_KEY_NAME, 1, GE_KEY_PRICE);
        AntiBan.get().markBotActivity();
        return ok
                ? ThreadLocalRandom.current().nextInt(900, 1400)
                : ThreadLocalRandom.current().nextInt(600, 900);
    }

    public static int climbHopLadder(WorldPoint pos) {
        ITileObject ladder = TileObjects.getNearest(GiantsLocations.HOP_LADDER, GiantsLocations.HOP_LADDER_ID);
        if (ladder == null) {
            ladder = TileObjects.getNearest(o -> o != null && o.getId() == GiantsLocations.HOP_LADDER_ID);
        }
        if (ladder == null && pos != null && pos.distanceTo(GiantsLocations.HOP_LADDER) > 2) {
            GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.HOP_LADDER, 1, "hop-ladder");
            return w.delayMs;
        }
        if (ladder == null) {
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        String action = firstAction(ladder, "Climb-up", "Climb-down", "Climb");
        if (action != null && TileObjectInteractHelper.interact(ladder, action)) {
            AntiBan.get().markBotActivity();
            GiantsLog.action("key", "climb hop-ladder " + action);
            return ThreadLocalRandom.current().nextInt(700, 1100);
        }
        return ThreadLocalRandom.current().nextInt(400, 700);
    }

    public static int hopF2pWorld() {
        int cur = Worlds.getCurrentWorld();
        try {
            Worlds.refreshWorldTypeCacheFromLive();
        } catch (Throwable ignored) {
        }
        Set<Integer> f2p = Worlds.cachedF2pWorlds();
        List<Integer> cand = new ArrayList<>();
        if (f2p != null) {
            for (Integer w : f2p) {
                if (w != null && w > 0 && w != cur) {
                    cand.add(w);
                }
            }
        }
        if (cand.isEmpty()) {
            GiantsLog.action("key", "geen F2P-wereld voor hop");
            return ThreadLocalRandom.current().nextInt(800, 1200);
        }
        int world = cand.get(ThreadLocalRandom.current().nextInt(cand.size()));
        GiantsLog.action("key", "hop " + cur + " → " + world + " (key-spawn wait)");
        Worlds.hop(world);
        AntiBan.get().markBotActivity();
        return ThreadLocalRandom.current().nextInt(1200, 2000);
    }

    private static String firstAction(ITileObject obj, String... names) {
        if (obj == null) {
            return null;
        }
        for (String a : names) {
            try {
                if (obj.hasAction(a)) {
                    return a;
                }
            } catch (Throwable ignored) {
            }
        }
        return names.length > 0 ? names[0] : null;
    }
}
