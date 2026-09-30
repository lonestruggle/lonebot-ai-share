package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed UI exclusion zones for canvas walk-clicks (chat, inventory, minimap chrome).
 */
public final class WalkUiZones {

    private WalkUiZones() {
    }

    /**
     * {@code true} if the canvas point overlaps a fixed UI widget we must not left-click for walking.
     */
    public static boolean isOverUi(Client c, Point canvas) {
        if (c == null || canvas == null) {
            return true;
        }
        int x = canvas.getX();
        int y = canvas.getY();
        for (Rectangle r : collectUiBounds(c)) {
            if (r != null && r.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public static List<Rectangle> collectUiBounds(Client c) {
        List<Rectangle> out = new ArrayList<>(8);
        // Chat
        addBounds(out, c, ComponentID.CHATBOX_PARENT);
        addBounds(out, c, ComponentID.CHATBOX_FRAME);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT);
        // Inventory / side panel
        addBounds(out, c, ComponentID.INVENTORY_CONTAINER);
        addBounds(out, c, ComponentID.FIXED_VIEWPORT_INVENTORY_CONTAINER);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_INVENTORY_CONTAINER);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_INVENTORY_PARENT);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_CONTAINER);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_PARENT);
        // Minimap chrome (canvas ground clicks must not hit orbs; intentional minimap walk uses localToMinimap)
        addBounds(out, c, ComponentID.MINIMAP_CONTAINER);
        addBounds(out, c, ComponentID.FIXED_VIEWPORT_MINIMAP);
        addBounds(out, c, ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_MINIMAP);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA);
        return out;
    }

    private static void addBounds(List<Rectangle> out, Client c, int componentId) {
        try {
            Widget w = c.getWidget(componentId);
            if (w == null || w.isHidden()) {
                return;
            }
            Rectangle b = w.getBounds();
            if (b != null && b.width > 2 && b.height > 2) {
                // Slight inflate so we don't clip the edge of chat/inv
                out.add(new Rectangle(b.x - 2, b.y - 2, b.width + 4, b.height + 4));
            }
        } catch (Throwable ignored) {
        }
    }
}
