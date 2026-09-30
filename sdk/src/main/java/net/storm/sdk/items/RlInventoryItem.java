package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inventory item interact — resolves action ids from the <b>slot widget</b> (Microbot-style),
 * not only {@link ItemComposition#getInventoryActions()}. Composition index 0 ≠ always
 * left-click when Menu Entry Swapper / shift-drop remaps the widget.
 */
final class RlInventoryItem implements IInventoryItem {

    private static final Logger log = LoggerFactory.getLogger(RlInventoryItem.class);

    /** Widely used Drop id for inventory CC_OP_LOW_PRIORITY. */
    private static final int DROP_IDENTIFIER = 7;

    private final Item item;
    private final int slot;
    private volatile String cachedName;

    RlInventoryItem(Item item, int slot) {
        this.item = item;
        this.slot = slot;
        if (Static.isOnClientThread()) {
            cachedName = resolveNameOnClient();
        }
    }

    private String resolveNameOnClient() {
        try {
            Client c = Static.getClient();
            if (c == null) {
                return "";
            }
            ItemComposition comp = c.getItemDefinition(item.getId());
            return comp != null && comp.getName() != null ? comp.getName() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public int getId() {
        return item.getId();
    }

    @Override
    public String getName() {
        String cached = cachedName;
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        String n = Static.callOnClientThread(this::resolveNameOnClient, "");
        if (n != null && !n.isEmpty()) {
            cachedName = n;
        }
        return n != null ? n : "";
    }

    @Override
    public int getQuantity() {
        return item.getQuantity();
    }

    @Override
    public int getSlot() {
        return slot;
    }

    @Override
    public boolean isNoted() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            ItemComposition comp = c.getItemDefinition(item.getId());
            return comp != null && comp.getNote() != -1;
        }, false));
    }

    @Override
    public boolean hasAction(String action) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            String[] actions = resolveActions(Static.getClient());
            return indexOfAction(actions, action) >= 0;
        }, false));
    }

    @Override
    public boolean interact(String action) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> interactOnClient(action), false));
    }

    private boolean interactOnClient(String action) {
        Client c = Static.getClient();
        if (c == null || action == null || action.isEmpty()) {
            return false;
        }
        ItemComposition comp = c.getItemDefinition(item.getId());
        String name = comp != null && comp.getName() != null ? comp.getName() : "";
        String[] actions = resolveActions(c);

        // Use is WIDGET_TARGET, not CC_OP
        if (action.equalsIgnoreCase("use")) {
        int packed = inventoryPackedId(c);
        try {
            c.menuAction(slot, packed, MenuAction.WIDGET_TARGET, 0, item.getId(), "Use", name);
            return true;
        } catch (Throwable t) {
            return MenuInteract.invokeMenu("Use", name, 0, MenuAction.WIDGET_TARGET.getId(),
                    slot, packed);
        }
        }

        // Drop: fixed id 7 + low priority (do not use composition index 4 → id 5)
        if (action.equalsIgnoreCase("drop")) {
            return invokeCc(c, "Drop", name, DROP_IDENTIFIER, MenuAction.CC_OP_LOW_PRIORITY);
        }

        int idx = indexOfAction(actions, action);
        if (idx < 0) {
            log.debug("[Inv] no action '{}' on slot={} actions={}", action, slot, java.util.Arrays.toString(actions));
            return false;
        }
        int identifier = idx + 1;
        MenuAction menu = identifier > 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
        log.debug("[Inv] {} slot={} id={} itemId={} actions={}", action, slot, identifier, item.getId(),
                java.util.Arrays.toString(actions));
        return invokeCc(c, actions[idx] != null ? actions[idx] : action, name, identifier, menu);
    }

    private boolean invokeCc(Client c, String option, String target, int identifier, MenuAction menu) {
        try {
            c.menuAction(slot, inventoryPackedId(c), menu, identifier, item.getId(), option, target);
            return true;
        } catch (Throwable t) {
            return MenuInteract.invokeInventory(option, target, item.getId(), identifier,
                    menu.getId(), slot, inventoryPackedId(c));
        }
    }

    /**
     * Prefer live inventory slot widget actions (includes MES remaps); fall back to composition.
     * Belangrijk: zonder widget (inv-tab dicht) is composition-index 1 vaak left-click —
     * bij Forestry kit = View i.p.v. Wear.
     */
    private String[] resolveActions(Client c) {
        if (c != null) {
            Widget slotWidget = findSlotWidget(c);
            if (slotWidget != null && slotWidget.getActions() != null
                    && slotWidget.getActions().length > 0) {
                return slotWidget.getActions();
            }
            ItemComposition comp = c.getItemDefinition(item.getId());
            if (comp != null && comp.getInventoryActions() != null) {
                return comp.getInventoryActions();
            }
        }
        return new String[0];
    }

    private Widget findSlotWidget(Client c) {
        Widget container = findInventoryContainer(c);
        if (container == null) {
            return null;
        }
        Widget[] children = container.getDynamicChildren();
        if (children == null || children.length == 0) {
            children = container.getChildren();
        }
        if (children == null) {
            return null;
        }
        for (Widget child : children) {
            if (child != null && child.getIndex() == slot && child.getItemId() == item.getId()) {
                return child;
            }
        }
        if (slot >= 0 && slot < children.length) {
            return children[slot];
        }
        return null;
    }

    /**
     * GE open: inventory zit in {@code GeOffersSide.ITEMS} (467), niet de backpack-tab.
     * Daar staat Offer — composition heeft die actie niet.
     */
    private static Widget findInventoryContainer(Client c) {
        if (c == null) {
            return null;
        }
        try {
            Widget ge = c.getWidget(InterfaceID.GeOffersSide.ITEMS);
            if (ge != null && !ge.isHidden()) {
                return ge;
            }
        } catch (Throwable ignored) {
        }
        try {
            Widget ge = c.getWidget(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER);
            if (ge != null && !ge.isHidden()) {
                return ge;
            }
        } catch (Throwable ignored) {
        }
        Widget container = c.getWidget(ComponentID.INVENTORY_CONTAINER);
        if (container == null) {
            container = c.getWidget(WidgetInfo.INVENTORY);
        }
        return container;
    }

    private static int inventoryPackedId(Client c) {
        Widget w = findInventoryContainer(c);
        if (w != null) {
            try {
                int id = w.getId();
                if (id != 0) {
                    return id;
                }
            } catch (Throwable ignored) {
            }
        }
        return ComponentID.INVENTORY_CONTAINER;
    }

    private static int indexOfAction(String[] actions, String action) {
        if (actions == null || action == null) {
            return -1;
        }
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && stripCol(actions[i]).equalsIgnoreCase(action)) {
                return i;
            }
        }
        return -1;
    }

    private static String stripCol(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<col=[^>]*>", "").replace("</col>", "").trim();
    }

    /**
     * Use this item on another inventory item (tinderbox→logs, etc.).
     * {@link MenuAction#WIDGET_TARGET} then {@link MenuAction#WIDGET_TARGET_ON_WIDGET}.
     */
    @Override
    public boolean useOn(IInventoryItem other) {
        if (other == null) {
            return false;
        }
        final int otherSlot = other.getSlot();
        final int otherId = other.getId();
        final String otherName = other.getName() != null ? other.getName() : "";
        // Twee client-invokes: zelfde tick WIDGET_TARGET + ON_WIDGET wordt door de client
        // vaak genegeerd (item nog niet geselecteerd). Korte pauze op de loop-thread.
        Boolean selected = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || otherSlot < 0 || otherId <= 0) {
                return false;
            }
            ItemComposition comp = c.getItemDefinition(item.getId());
            String thisName = comp != null && comp.getName() != null ? comp.getName() : "";
            int packed = inventoryPackedId(c);
            try {
                c.menuAction(slot, packed, MenuAction.WIDGET_TARGET, 0, item.getId(), "Use", thisName);
                return true;
            } catch (Throwable t) {
                return MenuInteract.invokeMenu("Use", thisName, 0, MenuAction.WIDGET_TARGET.getId(),
                        slot, packed, item.getId());
            }
        }, false);
        if (!Boolean.TRUE.equals(selected)) {
            return false;
        }
        if (!Static.isOnClientThread()) {
            try {
                Thread.sleep(90L + (System.nanoTime() % 90L));
            } catch (InterruptedException ignored) {
                // interrupt-flag niet zetten — dat stopte LoopHost na chop→fm
            }
        }
        final String target = (getName() != null ? getName() : "") + " -> " + otherName;
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            int packed = inventoryPackedId(c);
            try {
                c.menuAction(otherSlot, packed, MenuAction.WIDGET_TARGET_ON_WIDGET, 0, otherId, "Use", target);
                return true;
            } catch (Throwable t) {
                return MenuInteract.invokeMenu("Use", target, 0, MenuAction.WIDGET_TARGET_ON_WIDGET.getId(),
                        otherSlot, packed, otherId);
            }
        }, false));
    }

    @Override
    public boolean useOn(ITileObject object) {
        if (object == null) {
            return false;
        }
        TileObject raw = TileObjects.unwrap(object);
        if (raw == null) {
            // Fallback: find live object by id + tile
            WorldPoint want = object.getWorldLocation();
            int wantId = object.getId();
            ITileObject live = TileObjects.getNearest(o -> o != null
                    && o.getId() == wantId
                    && o.getWorldLocation() != null
                    && want != null
                    && o.getWorldLocation().getX() == want.getX()
                    && o.getWorldLocation().getY() == want.getY()
                    && o.getWorldLocation().getPlane() == want.getPlane());
            raw = TileObjects.unwrap(live);
        }
        if (raw == null) {
            return false;
        }
        String name = getName();
        return MenuInteract.useInventoryOnObject(slot, item.getId(), name, raw);
    }

    @Override
    public boolean useOn(net.storm.api.domain.actors.IActor actor) {
        if (actor == null) {
            return false;
        }
        if (actor instanceof net.storm.api.domain.actors.INPC) {
            net.runelite.api.NPC raw = net.storm.sdk.entities.NPCs.unwrap(
                    (net.storm.api.domain.actors.INPC) actor);
            if (raw == null) {
                return false;
            }
            return MenuInteract.useInventoryOnNpc(slot, item.getId(), getName(), raw);
        }
        return false;
    }

    @Override
    public boolean useOn(net.storm.api.domain.widgets.IWidget widget) {
        if (widget == null) {
            return false;
        }
        int packed = widget.getId();
        int otherItem = widget.getItemId();
        String otherName = widget.getName();
        if (otherName == null || otherName.isEmpty()) {
            otherName = widget.getText();
        }
        return MenuInteract.useInventoryOnWidget(slot, item.getId(), getName(),
                widget.getIndex(), packed, otherItem, otherName);
    }

    @Override
    public boolean useOn(net.storm.api.domain.tiles.ITileItem ground) {
        if (ground == null || ground.getWorldLocation() == null) {
            return false;
        }
        return MenuInteract.useInventoryOnGroundItem(slot, item.getId(), getName(),
                ground.getId(), ground.getWorldLocation());
    }
}
