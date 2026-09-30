package net.storm.sdk.movement.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.widgets.Dialog;

import java.util.Locale;

/**
 * Live interact helpers for {@link TransportLoader} factories — no lying stubs.
 */
final class TransportActions {

    static final int KNIFE_ID = 946;

    private TransportActions() {
    }

    static boolean interactObjectIdOrName(WorldPoint source, int radius, int objId, String name, String action) {
        if (objId >= 0 && interactObject(source, radius, objId, action)) {
            return true;
        }
        if (name != null && !name.isBlank()) {
            return interactObjectNamed(source, radius, name, action);
        }
        return false;
    }

    static boolean dialogOrObjectIdOrName(WorldPoint source, int radius, int objId, String name, String action,
                                          String... chatOptions) {
        if (AlKharidGate.isGateObject(objId) || AlKharidGate.isGateTile(source)) {
            return AlKharidGate.tryPayOrOpen();
        }
        if (Dialog.isOpen()) {
            if (Dialog.canContinue()) {
                Dialog.continueSpace();
                return true;
            }
            if (chatOptions != null) {
                for (String opt : chatOptions) {
                    if (opt != null && Dialog.chooseOption(opt)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return interactObjectIdOrName(source, radius, objId, name, action);
    }

    static boolean interactObject(WorldPoint source, int radius, int objId, String action) {
        ITileObject obj = findObject(source, radius, objId, null);
        if (obj == null) {
            return false;
        }
        if (action != null && !action.isBlank()) {
            if (obj.interact(action)) {
                return true;
            }
            // Trapdoor vaak dicht: eerst Open, volgende tick Climb-down
            String a = action.toLowerCase(Locale.ROOT);
            if (a.contains("climb") && obj.hasAction("Open") && obj.interact("Open")) {
                return true;
            }
            return false;
        }
        return obj.interact("Open", "Climb-up", "Climb-down", "Climb", "Enter", "Use", "Pass", "Cross",
                "Top-floor", "Bottom-floor");
    }

    static boolean interactObjectNamed(WorldPoint source, int radius, String name, String action) {
        ITileObject obj = findObject(source, radius, -1, name);
        if (obj == null) {
            return false;
        }
        if (action != null && !action.isBlank()) {
            if (obj.interact(action)) {
                return true;
            }
            String a = action.toLowerCase(Locale.ROOT);
            if (a.contains("climb") && obj.hasAction("Open") && obj.interact("Open")) {
                return true;
            }
            return false;
        }
        return obj.interact("Open", "Slash", "Climb-down", "Climb-up");
    }

    static boolean interactNpc(WorldPoint source, int radius, int npcId, String npcName, String... actions) {
        INPC npc = findNpc(source, radius, npcId, npcName);
        if (npc == null) {
            return false;
        }
        if (actions != null && actions.length > 0) {
            return npc.interact(actions);
        }
        return npc.interact("Talk-to", "Travel", "Take-boat", "Charter");
    }

    static boolean dialogOrTalk(WorldPoint source, int radius, int npcId, String npcName, String talkAction,
                                String... chatOptions) {
        if (Dialog.isOpen()) {
            if (Dialog.canContinue()) {
                Dialog.continueSpace();
                return true;
            }
            if (chatOptions != null) {
                for (String opt : chatOptions) {
                    if (opt != null && Dialog.chooseOption(opt)) {
                        return true;
                    }
                }
            }
            return false;
        }
        String action = talkAction != null && !talkAction.isBlank() ? talkAction : "Talk-to";
        return interactNpc(source, radius, npcId, npcName, action);
    }

    static boolean dialogOrObject(WorldPoint source, int radius, int objId, String action, String... chatOptions) {
        if (Dialog.isOpen()) {
            if (Dialog.canContinue()) {
                Dialog.continueSpace();
                return true;
            }
            if (chatOptions != null) {
                for (String opt : chatOptions) {
                    if (opt != null && Dialog.chooseOption(opt)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return interactObject(source, radius, objId, action);
    }

    static boolean useItemOnObject(WorldPoint source, int radius, int itemId, int objId) {
        IInventoryItem item = Inventory.getFirst(itemId);
        if (item == null) {
            return false;
        }
        ITileObject obj = findObject(source, radius, objId, null);
        if (obj == null) {
            return false;
        }
        return item.useOn(obj);
    }

    static boolean wearThenObject(WorldPoint source, int radius, int objId, String action, int... itemIds) {
        if (itemIds != null) {
            for (int id : itemIds) {
                if (Equipment.contains(id)) {
                    continue;
                }
                IInventoryItem inv = Inventory.getFirst(id);
                if (inv == null) {
                    return false;
                }
                return inv.interact("Wear", "Wield", "Equip");
            }
        }
        return interactObject(source, radius, objId, action);
    }

    static boolean trapDoor(WorldPoint source, int radius, int closedId, int openedId) {
        ITileObject opened = findObject(source, radius, openedId, null);
        if (opened != null) {
            return opened.interact("Climb-down", "Enter", "Climb", "Open");
        }
        ITileObject closed = findObject(source, radius, closedId, null);
        if (closed != null) {
            return closed.interact("Open", "Climb-down");
        }
        return false;
    }

    static boolean itemUseStateChange(WorldPoint source, int radius, int beforeObjId, int afterObjId, int itemId) {
        if (findObject(source, radius, afterObjId, null) != null) {
            return interactObject(source, radius, afterObjId, null);
        }
        return useItemOnObject(source, radius, itemId, beforeObjId);
    }

    static boolean slashWeb(WorldPoint source, int radius) {
        ITileObject web = findObject(source, radius, -1, "Web");
        if (web == null) {
            return false;
        }
        if (web.hasAction("Slash") && web.interact("Slash")) {
            return true;
        }
        IInventoryItem knife = Inventory.getFirst(KNIFE_ID);
        if (knife == null) {
            knife = Inventory.getFirst(i -> {
                String n = i.getName();
                return n != null && n.toLowerCase(Locale.ROOT).contains("knife");
            });
        }
        if (knife != null) {
            return knife.useOn(web);
        }
        return web.interact("Slash", "Cut");
    }

    static ITileObject findObject(WorldPoint source, int radius, int objId, String name) {
        int r = Math.max(0, radius);
        return TileObjects.getNearest(source, obj -> {
            if (obj == null || obj.getWorldLocation() == null) {
                return false;
            }
            if (source != null && source.distanceTo(obj.getWorldLocation()) > r) {
                return false;
            }
            if (objId >= 0 && obj.getId() != objId) {
                return false;
            }
            if (name != null && !name.isBlank()) {
                String n = obj.getName();
                return n != null && n.equalsIgnoreCase(name);
            }
            return true;
        });
    }

    static INPC findNpc(WorldPoint source, int radius, int npcId, String npcName) {
        int r = Math.max(0, radius);
        return NPCs.getNearest(source, npc -> {
            if (npc == null || npc.getWorldLocation() == null) {
                return false;
            }
            if (source != null && source.distanceTo(npc.getWorldLocation()) > r) {
                return false;
            }
            if (npcId >= 0 && npc.getId() != npcId) {
                return false;
            }
            if (npcName != null && !npcName.isBlank()) {
                String n = npc.getName();
                return n != null && n.equalsIgnoreCase(npcName);
            }
            return true;
        });
    }
}
