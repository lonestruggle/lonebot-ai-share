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
 * Minimap-walk: alleen World Map + Store weigeren — niet alle orbs (dat brak de walker).
 */
public final class WalkUiZones {

    private static final int[] WORLD_MAP_IDS = {
            ComponentID.MINIMAP_WORLDMAP_OPTIONS,
            ComponentID.MINIMAP_WORLDMAP_ORB,
    };

    private static final int[] STORE_IDS = {
            ComponentID.MINIMAP_WIKI_BANNER_PARENT,
    };

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
        return isOverWorldMapOrb(c, canvas) || isOverStoreChrome(c, canvas);
    }

    /** Minimap: alleen World Map / Store — geen hp/prayer/run (false rejects). */
    public static boolean isUnsafeMinimapClick(Client c, Point canvas) {
        return isOverWorldMapOrb(c, canvas) || isOverStoreChrome(c, canvas);
    }

    public static boolean isOverWorldMapOrb(Client c, Point canvas) {
        return hitsAny(c, canvas, WORLD_MAP_IDS, 4);
    }

    public static boolean isOverStoreChrome(Client c, Point canvas) {
        if (hitsAny(c, canvas, STORE_IDS, 4)) {
            return true;
        }
        return hitsStoreOrBondAction(c, canvas);
    }

    private static boolean hitsStoreOrBondAction(Client c, Point canvas) {
        if (c == null || canvas == null) {
            return false;
        }
        int x = canvas.getX();
        int y = canvas.getY();
        try {
            Widget root = c.getWidget(160, 0);
            if (root == null) {
                return false;
            }
            Widget[] kids = root.getDynamicChildren();
            if (kids == null) {
                kids = root.getStaticChildren();
            }
            if (kids == null) {
                return false;
            }
            for (Widget w : kids) {
                if (w == null || w.isHidden()) {
                    continue;
                }
                if (!actionMentionsStore(w)) {
                    continue;
                }
                Rectangle b = w.getBounds();
                if (b == null || b.width < 4 || b.height < 4) {
                    continue;
                }
                Rectangle pad = new Rectangle(b.x - 3, b.y - 3, b.width + 6, b.height + 6);
                if (pad.contains(x, y)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean actionMentionsStore(Widget w) {
        try {
            String[] actions = w.getActions();
            if (actions == null) {
                return false;
            }
            for (String a : actions) {
                if (a == null) {
                    continue;
                }
                String plain = a.replaceAll("<[^>]+>", "").toLowerCase();
                if (plain.contains("store") || plain.contains("bond") || plain.contains("membership")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean hitsAny(Client c, Point canvas, int[] componentIds, int padPx) {
        if (c == null || canvas == null || componentIds == null) {
            return false;
        }
        int x = canvas.getX();
        int y = canvas.getY();
        int pad = Math.max(0, padPx);
        for (int id : componentIds) {
            try {
                Widget w = c.getWidget(id);
                if (w == null || w.isHidden()) {
                    continue;
                }
                Rectangle b = w.getBounds();
                if (b == null || b.width < 3 || b.height < 3) {
                    continue;
                }
                Rectangle padR = new Rectangle(b.x - pad, b.y - pad, b.width + pad * 2, b.height + pad * 2);
                if (padR.contains(x, y)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static List<Rectangle> collectUiBounds(Client c) {
        List<Rectangle> out = new ArrayList<>(8);
        addBounds(out, c, ComponentID.CHATBOX_PARENT);
        addBounds(out, c, ComponentID.CHATBOX_FRAME);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT);
        addBounds(out, c, ComponentID.INVENTORY_CONTAINER);
        addBounds(out, c, ComponentID.FIXED_VIEWPORT_INVENTORY_CONTAINER);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_INVENTORY_CONTAINER);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_INVENTORY_PARENT);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_CONTAINER);
        addBounds(out, c, ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_PARENT);
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
                out.add(new Rectangle(b.x - 2, b.y - 2, b.width + 4, b.height + 4));
            }
        } catch (Throwable ignored) {
        }
    }
}
