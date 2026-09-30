package com.lonebot.example.clue;

import net.storm.api.domain.tiles.ITileItem;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.interact.GroundLootPickupHelper;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;

/**
 * Pick up beginner clue / casket / tools / Charlie items / nearby rewards.
 */
public final class BeginnerClueGroundPickupHelper {

    private static final int PICKUP_RANGE = 8;

    private BeginnerClueGroundPickupHelper() {
    }

    public static int pickupIfNeeded(boolean wantDevice, String charlieItem) {
        if (Inventory.isFull()) {
            return -1;
        }
        ITileItem item = nearestWanted(wantDevice, charlieItem);
        if (item == null) {
            return -1;
        }
        if (item.getWorldLocation() != null && Movement.distanceTo(item.getWorldLocation()) > PICKUP_RANGE) {
            Movement.walkTo(item.getWorldLocation());
            return 400;
        }
        GroundLootPickupHelper.Result r = GroundLootPickupHelper.pickup(item);
        BotRuntime.logConsole("[Clue/Loot] " + item.getName() + " " + (r != null ? r : "null"));
        return 400;
    }

    private static ITileItem nearestWanted(boolean wantDevice, String charlieItem) {
        return TileItems.getNearest(item -> {
            if (item == null || item.getName() == null) {
                return false;
            }
            String n = item.getName();
            if (ClueScrollHelper.isClueOrCasketName(n)) {
                return true;
            }
            if (ClueScrollHelper.containsNorm(n, BeginnerClueReference.SPADE)
                    && !Inventory.contains(BeginnerClueReference.SPADE)) {
                return true;
            }
            if (wantDevice && ClueScrollHelper.containsNorm(n, BeginnerClueReference.STRANGE_DEVICE)
                    && !Inventory.contains(BeginnerClueReference.STRANGE_DEVICE)) {
                return true;
            }
            return charlieItem != null && ClueScrollHelper.containsNorm(n, charlieItem)
                    && !Inventory.contains(charlieItem);
        });
    }
}
