package com.lonebot.example.quest.helpers;

import com.lonebot.example.quest.QuestLog;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;

/**
 * Shared walk / object / pickup / use-on for F2P quest scripts.
 */
public final class QuestActions {

    private QuestActions() {
    }

    public static WorldPoint local() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me.present ? me.worldLocation : null;
    }

    public static boolean moving() {
        return Movement.isWalking() || Movement.isMoving();
    }

    public static int dist(WorldPoint dest) {
        WorldPoint me = local();
        if (me == null || dest == null) {
            return Integer.MAX_VALUE;
        }
        if (me.getPlane() != dest.getPlane()) {
            return 99;
        }
        return me.distanceTo(dest);
    }

    /**
     * @return {@code true} if already at dest (≤ {@code near})
     */
    public static boolean walkTo(WorldPoint dest, int near) {
        if (dest == null) {
            return false;
        }
        int d = dist(dest);
        if (d <= near) {
            return true;
        }
        if (moving() && d <= 12) {
            return false;
        }
        Movement.walkTo(dest);
        return false;
    }

    public static boolean walkTo(WorldPoint dest) {
        return walkTo(dest, 2);
    }

    public static boolean pickup(String name) {
        ITileItem item = TileItems.getNearest(name);
        if (item == null) {
            return false;
        }
        WorldPoint tile = item.getWorldLocation();
        if (tile != null && dist(tile) > 8) {
            walkTo(tile, 2);
            return true;
        }
        if (item.pickup() || item.interact("Take")) {
            QuestLog.step("Actie", "Take " + name);
            return true;
        }
        return false;
    }

    public static boolean interactObject(String name, String action, WorldPoint near) {
        ITileObject obj = near != null
                ? TileObjects.getNearest(near, name)
                : TileObjects.getNearest(name);
        if (obj == null && near != null) {
            if (!walkTo(near, 3)) {
                return true;
            }
            obj = TileObjects.getNearest(name);
        }
        if (obj == null) {
            if (near != null) {
                walkTo(near, 2);
                return true;
            }
            return false;
        }
        WorldPoint tile = obj.getWorldLocation();
        if (tile != null && dist(tile) > 8) {
            walkTo(tile, 3);
            return true;
        }
        if (obj.interact(action)) {
            QuestLog.step("Actie", action + " " + name);
            return true;
        }
        return false;
    }

    public static boolean interactObjectAny(WorldPoint near, String action, String... names) {
        if (names == null) {
            return false;
        }
        for (String n : names) {
            if (interactObject(n, action, near)) {
                return true;
            }
        }
        return false;
    }

    public static boolean useItemOnObject(String itemName, String objectName, WorldPoint near) {
        IInventoryItem item = Inventory.getFirst(itemName);
        if (item == null) {
            return false;
        }
        ITileObject obj = near != null
                ? TileObjects.getNearest(near, objectName)
                : TileObjects.getNearest(objectName);
        if (obj == null) {
            if (near != null) {
                walkTo(near, 3);
                return true;
            }
            return false;
        }
        WorldPoint tile = obj.getWorldLocation();
        if (tile != null && dist(tile) > 8) {
            walkTo(tile, 3);
            return true;
        }
        if (item.useOn(obj)) {
            QuestLog.step("Actie", "Use " + itemName + " → " + objectName);
            return true;
        }
        return false;
    }

    public static boolean useItemOnItem(String a, String b) {
        IInventoryItem ia = Inventory.getFirst(a);
        IInventoryItem ib = Inventory.getFirst(b);
        if (ia == null || ib == null) {
            return false;
        }
        if (ia.useOn(ib)) {
            QuestLog.step("Actie", "Use " + a + " → " + b);
            return true;
        }
        return false;
    }

    public static boolean useItemOnNpc(String itemName, String npcName) {
        IInventoryItem item = Inventory.getFirst(itemName);
        INPC npc = NPCs.getNearest(npcName);
        if (item == null || npc == null) {
            return false;
        }
        WorldPoint tile = npc.getWorldLocation();
        if (tile != null && dist(tile) > 4) {
            walkTo(tile, 2);
            return true;
        }
        if (item.useOn(npc)) {
            QuestLog.step("Actie", "Use " + itemName + " → " + npcName);
            return true;
        }
        return false;
    }

    public static boolean attack(String npcName) {
        INPC npc = NPCs.getNearest(n -> n != null && npcName.equalsIgnoreCase(n.getName())
                && n.hasAction("Attack"));
        if (npc == null) {
            return false;
        }
        WorldPoint tile = npc.getWorldLocation();
        if (tile != null && dist(tile) > 10) {
            walkTo(tile, 3);
            return true;
        }
        if (npc.interact("Attack")) {
            QuestLog.step("Actie", "Attack " + npcName);
            return true;
        }
        return false;
    }

    public static int unnotedCount(String name) {
        int n = 0;
        for (IInventoryItem i : Inventory.getAll(name)) {
            if (i != null && !i.isNoted()) {
                n += Math.max(1, i.getQuantity());
            }
        }
        return n;
    }

    public static boolean hasUnnoted(String name) {
        return unnotedCount(name) > 0;
    }

    public static boolean hasAny(String... names) {
        return Inventory.contains(names);
    }
}
