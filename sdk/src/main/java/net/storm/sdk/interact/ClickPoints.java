package net.storm.sdk.interact;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.game.Static;
import net.storm.sdk.movement.WalkUiZones;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Canvas-clickpunten voor NPC / object / widget — altijd veilig via client-thread.
 */
public final class ClickPoints {

    private ClickPoints() {
    }

    public static Point forNpc(INPC npc) {
        if (npc == null) {
            return null;
        }
        return Static.callOnClientThread(() -> forNpcOnClient(npc), null);
    }

    public static Point forTileObject(TileObject obj) {
        if (obj == null) {
            return null;
        }
        return Static.callOnClientThread(() -> forTileObjectOnClient(obj), null);
    }

    /** Storm {@code ITileObject} — clickbox via scene tile, else canvas tile-poly. */
    public static Point forITileObject(net.storm.api.domain.tiles.ITileObject obj) {
        if (obj == null) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            WorldPoint wp = obj.getWorldLocation();
            if (wp == null) {
                return null;
            }
            try {
                net.runelite.api.Tile tile = net.storm.sdk.entities.Tiles.getAt(wp);
                if (tile != null) {
                    net.runelite.api.GameObject[] gos = tile.getGameObjects();
                    if (gos != null) {
                        for (net.runelite.api.GameObject go : gos) {
                            if (go == null) {
                                continue;
                            }
                            Point p = forTileObjectOnClient(go);
                            if (p != null && !WalkUiZones.isOverUi(c, p)) {
                                return p;
                            }
                        }
                    }
                    if (tile.getWallObject() != null) {
                        Point p = forTileObjectOnClient(tile.getWallObject());
                        if (p != null && !WalkUiZones.isOverUi(c, p)) {
                            return p;
                        }
                    }
                    if (tile.getGroundObject() != null) {
                        Point p = forTileObjectOnClient(tile.getGroundObject());
                        if (p != null && !WalkUiZones.isOverUi(c, p)) {
                            return p;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return ClickOnSight.tileCanvas(c, wp);
        }, null);
    }

    public static Point forWidget(Widget w) {
        if (w == null) {
            return null;
        }
        return Static.callOnClientThread(() -> forWidgetOnClient(w), null);
    }

    public static Point forInventorySlot(int slot) {
        return Static.callOnClientThread(() -> forInventorySlotOnClient(slot), null);
    }

    /** Alleen aanroepen op client-thread (snapshot). */
    public static Point forNpcOnClient(INPC npc) {
        if (npc == null) {
            return null;
        }
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        NPC raw = findNpc(npc.getIndex());
        if (raw != null) {
            try {
                Shape hull = raw.getConvexHull();
                for (int attempt = 0; attempt < 12; attempt++) {
                    Point hullPt = randomInShape(hull);
                    if (hullPt != null && hullPt.getX() >= 0 && !WalkUiZones.isOverUi(c, hullPt)) {
                        return hullPt;
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                LocalPoint lp = raw.getLocalLocation();
                if (lp != null) {
                    int h = Math.max(0, raw.getLogicalHeight() / 2);
                    Point p = Perspective.localToCanvas(c, lp, c.getPlane(), h);
                    if (p != null && p.getX() >= 0) {
                        Point j = jitter(p, 4);
                        if (!WalkUiZones.isOverUi(c, j)) {
                            return j;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Point p = npc.getCanvasPoint();
            if (p != null && !WalkUiZones.isOverUi(c, p)) {
                return p;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Point forTileObjectOnClient(TileObject obj) {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        try {
            Shape clickbox = obj.getClickbox();
            Point p = randomInShape(clickbox);
            if (p != null) {
                return p;
            }
        } catch (Throwable ignored) {
        }
        LocalPoint lp = obj.getLocalLocation();
        if (lp == null) {
            return null;
        }
        return Perspective.localToCanvas(c, lp, c.getPlane());
    }

    /** Alleen aanroepen op client-thread (snapshot). */
    public static Point forWidgetOnClient(Widget w) {
        if (w == null || w.isHidden()) {
            return null;
        }
        Rectangle b = w.getBounds();
        if (b == null || b.width < 2 || b.height < 2) {
            return null;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x = b.x + 2 + r.nextInt(Math.max(1, b.width - 4));
        int y = b.y + 2 + r.nextInt(Math.max(1, b.height - 4));
        return new Point(x, y);
    }

    private static Point forInventorySlotOnClient(int slot) {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        Widget inv = c.getWidget(149, 0);
        if (inv == null) {
            return null;
        }
        Widget[] children = inv.getDynamicChildren();
        if (children == null || slot < 0 || slot >= children.length) {
            children = inv.getChildren();
        }
        if (children == null || slot < 0 || slot >= children.length) {
            return null;
        }
        return forWidgetOnClient(children[slot]);
    }

    private static Point randomInShape(Shape shape) {
        if (shape == null) {
            return null;
        }
        Rectangle b = shape.getBounds();
        if (b == null || b.width < 2 || b.height < 2) {
            return null;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            int x = b.x + r.nextInt(b.width);
            int y = b.y + r.nextInt(b.height);
            if (shape.contains(x, y)) {
                return new Point(x, y);
            }
        }
        return new Point(b.x + b.width / 2, b.y + b.height / 2);
    }

    private static Point jitter(Point p, int amp) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Point(p.getX() + r.nextInt(-amp, amp + 1), p.getY() + r.nextInt(-amp, amp + 1));
    }

    private static NPC findNpc(int index) {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        for (NPC n : c.getNpcs()) {
            if (n != null && n.getIndex() == index) {
                return n;
            }
        }
        return null;
    }
}
