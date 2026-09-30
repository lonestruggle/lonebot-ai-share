package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NPC / object / ground-item IDs — labels in scene + tooltip onder muis.
 * Object-labels mogen nooit op NPC-canvaspixels (schorpioen ≠ Hole/Crystal).
 */
public class SceneIdOverlay extends Overlay {

    private static final List<OidHit> OID_HITS = new CopyOnWriteArrayList<>();

    private final Client client;
    private final LoneBotConfig config;

    @Inject
    public SceneIdOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_MED);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (config == null || client == null || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        boolean altEdit = config.devObjectIdFilterAltEdit() && ObjectIdFilterStore.altHeld();
        boolean npcOn = config.devNpcIds();
        boolean objOn = config.devObjectIds() || altEdit;
        boolean groundOn = config.devGroundItemIds();
        if (!npcOn && !objOn && !groundOn) {
            return null;
        }

        Player me = client.getLocalPlayer();
        if (me == null || me.getWorldLocation() == null) {
            return null;
        }
        WorldPoint myWp = me.getWorldLocation();
        Point mouse = client.getMouseCanvasPosition();
        int mx = mouse != null ? mouse.getX() : -1;
        int my = mouse != null ? mouse.getY() : -1;

        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        OID_HITS.clear();
        HoverBest hover = new HoverBest();
        List<Rectangle> npcBlocks = new ArrayList<>();
        List<Rectangle> occupied = new ArrayList<>();

        collectNpcBlocks(npcBlocks);
        if (npcOn) {
            drawNpcLabels(g, myWp, mx, my, hover, occupied);
            occupied.addAll(npcBlocks);
        } else {
            occupied.addAll(npcBlocks);
        }

        if (objOn || groundOn) {
            Tile[][][] tiles = client.getScene() != null ? client.getScene().getTiles() : null;
            if (tiles != null) {
                int plane = client.getPlane();
                Tile[][] planeTiles = tiles[plane];
                // Multi-tile object staat op N tegels → frame-breed 1 label per instance
                java.util.HashSet<Long> seenObjInstances = new java.util.HashSet<>();
                if (planeTiles != null) {
                    for (Tile[] row : planeTiles) {
                        if (row == null) {
                            continue;
                        }
                        for (Tile tile : row) {
                            if (tile == null || tile.getWorldLocation() == null) {
                                continue;
                            }
                            if (tile.getWorldLocation().distanceTo(myWp) > 14) {
                                continue;
                            }
                            if (objOn) {
                                drawTileObjects(g, tile, mx, my, hover, occupied, npcBlocks, npcOn,
                                        seenObjInstances);
                            }
                            if (groundOn) {
                                drawGroundItems(g, tile, mx, my, hover, occupied, npcBlocks, npcOn);
                            }
                        }
                    }
                }
            }
        }

        if (hover.label != null) {
            drawHoverTip(g, mx, my, hover.label);
        }
        return null;
    }

    /** NPC-lichaam op canvas — ook als NPC IDs uit staat, zodat Hole/Crystal niet op de schorpioen zit. */
    private void collectNpcBlocks(List<Rectangle> npcBlocks) {
        Player me = client.getLocalPlayer();
        if (me == null || me.getWorldLocation() == null) {
            return;
        }
        WorldPoint myWp = me.getWorldLocation();
        for (NPC n : client.getNpcs()) {
            if (n == null || n.getWorldLocation() == null) {
                continue;
            }
            if (n.getWorldLocation().distanceTo(myWp) > 18) {
                continue;
            }
            LocalPoint lp = n.getLocalLocation();
            if (lp == null) {
                continue;
            }
            Point body = Perspective.localToCanvas(client, lp, client.getPlane(), n.getLogicalHeight() / 2);
            if (body == null) {
                continue;
            }
            npcBlocks.add(new Rectangle(body.getX() - 28, body.getY() - 32, 56, 64));
        }
    }

    private void drawNpcLabels(Graphics2D g, WorldPoint myWp, int mx, int my, HoverBest hover,
                               List<Rectangle> occupied) {
        for (NPC n : client.getNpcs()) {
            if (n == null || n.getWorldLocation() == null) {
                continue;
            }
            if (n.getWorldLocation().distanceTo(myWp) > 18) {
                continue;
            }
            LocalPoint lp = n.getLocalLocation();
            Point p = n.getCanvasTextLocation(g, "x", n.getLogicalHeight());
            if (p == null && lp != null) {
                p = Perspective.localToCanvas(client, lp, client.getPlane(), n.getLogicalHeight());
            }
            if (p == null) {
                continue;
            }
            String name = npcDefinitionName(n);
            String label = "NPC " + name + " nid=" + n.getId() + " idx=" + n.getIndex();
            Rectangle box = drawLabel(g, p.getX(), p.getY(), label, new Color(120, 200, 255));
            occupied.add(box);
            considerHover(hover, mx, my, box, label);
            if (lp != null) {
                Point body = Perspective.localToCanvas(client, lp, client.getPlane(), n.getLogicalHeight() / 2);
                if (body != null) {
                    considerHover(hover, mx, my,
                            new Rectangle(body.getX() - 28, body.getY() - 32, 56, 64), label);
                }
            }
        }
    }

    private void drawTileObjects(Graphics2D g, Tile tile, int mx, int my, HoverBest hover,
                                 List<Rectangle> occupied, List<Rectangle> npcBlocks, boolean npcOn,
                                 java.util.Set<Long> seenObjInstances) {
        List<TileObject> objs = new ArrayList<>();
        if (tile.getGameObjects() != null) {
            for (GameObject go : tile.getGameObjects()) {
                if (go != null) {
                    objs.add(go);
                }
            }
        }
        if (tile.getWallObject() != null) {
            objs.add(tile.getWallObject());
        }
        if (tile.getDecorativeObject() != null) {
            objs.add(tile.getDecorativeObject());
        }
        if (tile.getGroundObject() != null) {
            objs.add(tile.getGroundObject());
        }
        for (TileObject obj : objs) {
            try {
                long inst = objectInstanceKey(obj, tile);
                if (seenObjInstances != null && !seenObjInstances.add(inst)) {
                    continue;
                }
                int id = obj.getId();
                LocalPoint olp = obj.getLocalLocation();
                Point p = olp != null
                        ? Perspective.localToCanvas(client, olp, client.getPlane(), 0)
                        : null;
                if (p == null) {
                    continue;
                }
                String name = objectDefinitionName(id);
                boolean unnamed = ObjectIdFilterStore.isUnnamed(name);
                boolean hideUnnamed = config.devObjectIdFilterHideUnnamed();
                // Null-scenery nooit tonen als hide aan staat — ook niet tijdens Alt-edit
                if (hideUnnamed && unnamed) {
                    continue;
                }
                boolean idHidden = ObjectIdFilterStore.isHidden(id);
                boolean filterOn = config.devObjectIdFilterEnabled();
                boolean altEdit = config.devObjectIdFilterAltEdit() && ObjectIdFilterStore.altHeld();
                boolean filtered = filterOn && idHidden;
                if (filtered && !altEdit) {
                    continue;
                }
                String label = "OBJ " + name + " oid=" + id;
                Color color = filtered ? new Color(255, 110, 110) : new Color(255, 200, 100);
                if (altEdit) {
                    color = filtered ? new Color(255, 140, 140) : new Color(90, 255, 160);
                }
                int extra = altEdit ? extraAltWidth(g) : 0;
                Point placed = dodgeLabel(g, p.getX(), p.getY(), label, extra, occupied);
                if (placed == null) {
                    continue;
                }
                Rectangle box = drawObjectLabel(g, placed.getX(), placed.getY(), label, id, color, altEdit);
                occupied.add(box);
                if (!(npcOn && mouseIn(mx, my, npcBlocks))) {
                    WorldPoint wp = obj.getWorldLocation() != null
                            ? obj.getWorldLocation() : tile.getWorldLocation();
                    considerHover(hover, mx, my, box, label
                            + " @" + wp.getX() + "," + wp.getY());
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Unieke key per object-instance. Multi-tile GameObject deelt sceneMin → 1 label;
     * twee deuren met hetzelfde oid op andere tegels blijven apart.
     */
    private static long objectInstanceKey(TileObject obj, Tile tile) {
        int id = obj != null ? obj.getId() : 0;
        if (obj instanceof GameObject) {
            try {
                Point sm = ((GameObject) obj).getSceneMinLocation();
                if (sm != null) {
                    return (((long) id) << 32) | (((long) (sm.getX() & 0xFFFF)) << 16) | (sm.getY() & 0xFFFF);
                }
            } catch (Throwable ignored) {
            }
        }
        WorldPoint wp = obj != null ? obj.getWorldLocation() : null;
        if (wp == null && tile != null) {
            wp = tile.getWorldLocation();
        }
        if (wp == null) {
            return id;
        }
        return (((long) id) << 32) | (((long) (wp.getX() & 0xFFFF)) << 16) | (wp.getY() & 0xFFFF);
    }

    private void drawGroundItems(Graphics2D g, Tile tile, int mx, int my, HoverBest hover,
                                 List<Rectangle> occupied, List<Rectangle> npcBlocks, boolean npcOn) {
        List<TileItem> items = tile.getGroundItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        LocalPoint lp = tile.getLocalLocation();
        if (lp == null) {
            return;
        }
        Point p = Perspective.localToCanvas(client, lp, client.getPlane(), 0);
        if (p == null) {
            return;
        }
        int yOff = 0;
        for (TileItem item : items) {
            if (item == null) {
                continue;
            }
            int id = item.getId();
            String name = "?";
            try {
                if (client.getItemDefinition(id) != null) {
                    name = Text.removeTags(client.getItemDefinition(id).getName());
                }
            } catch (Throwable ignored) {
            }
            String label = "ITEM " + name + " iid=" + id + " x" + item.getQuantity();
            Point placed = dodgeLabel(g, p.getX(), p.getY() + yOff, label, 0, occupied);
            if (placed == null) {
                continue;
            }
            Rectangle box = drawLabel(g, placed.getX(), placed.getY(), label, new Color(160, 255, 140));
            occupied.add(box);
            if (!(npcOn && mouseIn(mx, my, npcBlocks))) {
                considerHover(hover, mx, my, box, "GROUND " + label
                        + " @" + tile.getWorldLocation().getX() + "," + tile.getWorldLocation().getY());
            }
            yOff += 13;
        }
    }

    private String npcDefinitionName(NPC n) {
        try {
            NPCComposition t = n.getTransformedComposition();
            if (t != null && usableNpcName(t.getName())) {
                return Text.removeTags(t.getName());
            }
        } catch (Throwable ignored) {
        }
        try {
            NPCComposition c = n.getComposition();
            if (c != null && usableNpcName(c.getName())) {
                return Text.removeTags(c.getName());
            }
        } catch (Throwable ignored) {
        }
        try {
            NPCComposition d = client.getNpcDefinition(n.getId());
            if (d != null && usableNpcName(d.getName())) {
                return Text.removeTags(d.getName());
            }
        } catch (Throwable ignored) {
        }
        if (n.getName() != null && usableNpcName(n.getName())) {
            return Text.removeTags(n.getName());
        }
        return "?";
    }

    private static boolean usableNpcName(String name) {
        return name != null && !name.isBlank() && !"null".equalsIgnoreCase(name);
    }

    private String objectDefinitionName(int id) {
        try {
            if (client.getObjectDefinition(id) != null) {
                String n = client.getObjectDefinition(id).getName();
                if (n != null) {
                    return Text.removeTags(n);
                }
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    private static int extraAltWidth(Graphics2D g) {
        FontMetrics fm = g.getFontMetrics();
        return fm.stringWidth("[+]") + fm.stringWidth("[-]") + 6;
    }

    private static Rectangle labelBox(Graphics2D g, int x, int y, String label, int extra) {
        int tw = g.getFontMetrics().stringWidth(label);
        return new Rectangle(x - tw / 2 - 2, y - 10, tw + extra + 4, 12);
    }

    /** Schuif label naar beneden tot het geen NPC/andere label overlapt. */
    private static Point dodgeLabel(Graphics2D g, int x, int y, String label, int extra, List<Rectangle> occupied) {
        int ly = y;
        for (int i = 0; i < 8; i++) {
            Rectangle r = labelBox(g, x, ly, label, extra);
            boolean hit = false;
            for (Rectangle o : occupied) {
                if (o != null && o.intersects(r)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return new Point(x, ly);
            }
            ly += 13;
        }
        return new Point(x, ly);
    }

    private static boolean mouseIn(int mx, int my, List<Rectangle> blocks) {
        if (mx < 0 || my < 0 || blocks == null) {
            return false;
        }
        for (Rectangle r : blocks) {
            if (r != null && r.contains(mx, my)) {
                return true;
            }
        }
        return false;
    }

    private static Rectangle drawLabel(Graphics2D g, int x, int y, String label, Color color) {
        Rectangle box = labelBox(g, x, y, label, 0);
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(box.x, box.y, box.width, box.height);
        g.setColor(color);
        g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, y);
        return box;
    }

    private static Rectangle drawObjectLabel(Graphics2D g, int x, int y, String label, int id, Color color,
                                             boolean altEdit) {
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(label);
        int plusW = fm.stringWidth("[+]");
        int minusW = fm.stringWidth("[-]");
        int extra = altEdit ? plusW + minusW + 6 : 0;
        Rectangle box = labelBox(g, x, y, label, extra);
        g.setColor(altEdit ? new Color(0, 40, 20, 200) : new Color(0, 0, 0, 160));
        g.fillRect(box.x, box.y, box.width, box.height);
        if (altEdit) {
            g.setColor(new Color(80, 255, 140, 220));
            g.drawRect(box.x, box.y, box.width - 1, box.height - 1);
        }
        g.setColor(color);
        g.drawString(label, x - tw / 2, y);
        Rectangle plus = null;
        Rectangle minus = null;
        if (altEdit) {
            int plusX = x - tw / 2 + tw + 3;
            g.setColor(new Color(120, 255, 140));
            g.drawString("[+]", plusX, y);
            int minusX = plusX + plusW + 2;
            g.setColor(new Color(255, 140, 140));
            g.drawString("[-]", minusX, y);
            plus = new Rectangle(plusX - 1, box.y, plusW + 2, 12);
            minus = new Rectangle(minusX - 1, box.y, minusW + 2, 12);
        }
        OID_HITS.add(new OidHit(id, plus, minus, box));
        return box;
    }

    /**
     * Alt+klik op + / − / label. {@code true} = event afhandelen (niet in-game klikken).
     */
    public static boolean tryClick(int mx, int my, boolean addIfBody) {
        for (int i = OID_HITS.size() - 1; i >= 0; i--) {
            OidHit h = OID_HITS.get(i);
            if (h.plus != null && h.plus.contains(mx, my)) {
                ObjectIdFilterStore.add(h.id);
                return true;
            }
            if (h.minus != null && h.minus.contains(mx, my)) {
                ObjectIdFilterStore.remove(h.id);
                return true;
            }
            if (addIfBody && h.body != null && h.body.contains(mx, my)) {
                ObjectIdFilterStore.add(h.id);
                return true;
            }
        }
        return false;
    }

    private static void considerHover(HoverBest hover, int mx, int my, Rectangle box, String label) {
        if (mx < 0 || my < 0 || box == null) {
            return;
        }
        Rectangle pad = new Rectangle(box.x - 4, box.y - 4, box.width + 8, box.height + 8);
        if (!pad.contains(mx, my)) {
            return;
        }
        int cx = box.x + box.width / 2;
        int cy = box.y + box.height / 2;
        int d = Math.abs(mx - cx) + Math.abs(my - cy);
        if (d < hover.dist) {
            hover.dist = d;
            hover.label = label;
        }
    }

    private void drawHoverTip(Graphics2D g, int mx, int my, String label) {
        if (mx < 0) {
            return;
        }
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        int pad = 5;
        int tw = g.getFontMetrics().stringWidth(label);
        int th = g.getFontMetrics().getHeight();
        int x = mx + 12;
        int y = my + 12;
        g.setColor(new Color(0, 0, 0, 200));
        g.fill(new RoundRectangle2D.Float(x, y, tw + pad * 2, th + pad, 6, 6));
        g.setColor(new Color(255, 230, 120));
        g.draw(new RoundRectangle2D.Float(x, y, tw + pad * 2, th + pad, 6, 6));
        g.setColor(Color.WHITE);
        g.drawString(label, x + pad, y + pad + g.getFontMetrics().getAscent() - 2);
    }

    private static final class HoverBest {
        int dist = Integer.MAX_VALUE;
        String label;
    }

    private static final class OidHit {
        final int id;
        final Rectangle plus;
        final Rectangle minus;
        final Rectangle body;

        OidHit(int id, Rectangle plus, Rectangle minus, Rectangle body) {
            this.id = id;
            this.plus = plus;
            this.minus = minus;
            this.body = body;
        }
    }
}
