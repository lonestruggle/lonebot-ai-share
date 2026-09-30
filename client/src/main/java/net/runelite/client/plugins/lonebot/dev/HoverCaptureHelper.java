package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.lonebot.EmoteWidgetRegistry;
import net.runelite.client.plugins.lonebot.HoverCaptureLog;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.runelite.client.plugins.lonebot.WallDoorCaptureReport;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Middenklik → widget / tegel / NPC / object naar Developer Tools capture-log
 * (niet de Debug-console). Scan op client-thread — AWT-reads geven lege widgets.
 */
public class HoverCaptureHelper {

    private final Client client;
    private final LoneBotConfig config;
    private final ClientThread clientThread;
    private MouseAdapter listener;
    private Canvas attachedCanvas;
    private volatile int pendingMx;
    private volatile int pendingMy;

    @Inject
    public HoverCaptureHelper(Client client, LoneBotConfig config, ClientThread clientThread) {
        this.client = client;
        this.config = config;
        this.clientThread = clientThread;
    }

    public void install() {
        uninstall();
        if (client == null) {
            return;
        }
        listener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)
                        && config != null
                        && config.devObjectIdFilterAltEdit()
                        && (e.isAltDown() || ObjectIdFilterStore.altHeld())) {
                    if (SceneIdOverlay.tryClick(e.getX(), e.getY(), true)
                            || addFilterIdAt(e.getX(), e.getY())) {
                        e.consume();
                        return;
                    }
                }
                if (!SwingUtilities.isMiddleMouseButton(e)) {
                    return;
                }
                if (config == null || !config.devHoverCaptureMiddleClick()) {
                    return;
                }
                e.consume();
                pendingMx = e.getX();
                pendingMy = e.getY();
                if (clientThread != null) {
                    clientThread.invokeLater(HoverCaptureHelper.this::captureOnClientThread);
                } else {
                    HoverCaptureHelper.this.captureOnClientThread();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (config != null && config.devHoverCaptureMiddleClick()
                        && SwingUtilities.isMiddleMouseButton(e)) {
                    e.consume();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (config != null && config.devHoverCaptureMiddleClick()
                        && SwingUtilities.isMiddleMouseButton(e)) {
                    e.consume();
                }
            }
        };
        ensureCanvasListener();
    }

    public void ensureCanvasListener() {
        if (listener == null || client == null) {
            return;
        }
        Canvas canvas = resolveGameCanvas();
        if (canvas == null) {
            return;
        }
        if (attachedCanvas == canvas) {
            return;
        }
        if (attachedCanvas != null) {
            attachedCanvas.removeMouseListener(listener);
        }
        canvas.addMouseListener(listener);
        attachedCanvas = canvas;
    }

    public void uninstall() {
        if (attachedCanvas != null && listener != null) {
            attachedCanvas.removeMouseListener(listener);
        }
        attachedCanvas = null;
        listener = null;
    }

    private Canvas resolveGameCanvas() {
        try {
            Canvas storm = net.storm.sdk.game.Client.getCanvas();
            if (storm != null) {
                return storm;
            }
        } catch (Throwable ignored) {
        }
        return client != null ? client.getCanvas() : null;
    }

    /** Alt+klik op object (niet op overlay-label): ID in de filterlijst. */
    private boolean addFilterIdAt(int mx, int my) {
        TileObject obj = nearestObject(mx, my);
        if (obj == null) {
            return false;
        }
        ObjectIdFilterStore.add(obj.getId());
        return true;
    }

    private TileObject nearestObject(int mx, int my) {
        Player me = client.getLocalPlayer();
        WorldPoint myWp = me != null ? me.getWorldLocation() : null;
        if (myWp == null || client.getScene() == null) {
            return null;
        }
        Tile[][][] tiles = client.getScene().getTiles();
        Tile[][] planeTiles = tiles != null ? tiles[client.getPlane()] : null;
        if (planeTiles == null) {
            return null;
        }
        TileObject best = null;
        int bestD = Integer.MAX_VALUE;
        for (Tile[] row : planeTiles) {
            if (row == null) {
                continue;
            }
            for (Tile tile : row) {
                if (tile == null || tile.getWorldLocation() == null) {
                    continue;
                }
                if (tile.getWorldLocation().distanceTo(myWp) > 16) {
                    continue;
                }
                TileObject obj = firstObject(tile);
                if (obj == null) {
                    continue;
                }
                LocalPoint olp = obj.getLocalLocation();
                if (olp == null) {
                    continue;
                }
                Point tp = Perspective.localToCanvas(client, olp, client.getPlane());
                if (tp == null) {
                    continue;
                }
                int d = Math.abs(tp.getX() - mx) + Math.abs(tp.getY() - my);
                if (d < bestD && d < 56) {
                    bestD = d;
                    best = obj;
                }
            }
        }
        return best;
    }

    private void captureOnClientThread() {
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            addCapture("miss", "niet ingelogd", "kind: miss\nniet ingelogd");
            return;
        }
        int mx = pendingMx;
        int my = pendingMy;
        try {
            Point mouse = client.getMouseCanvasPosition();
            if (mouse != null && mouse.getX() >= 0 && mouse.getY() >= 0) {
                mx = mouse.getX();
                my = mouse.getY();
            }
        } catch (Throwable ignored) {
        }

        int added = 0;
        Widget w = WidgetHoverOverlay.findSmallestAt(client, mx, my);
        if (w != null) {
            String line = WidgetHoverOverlay.captureLine(w);
            String detail = WidgetHoverOverlay.captureDetail(w, mx, my);
            if (addCapture("widget", line, detail)) {
                added++;
                try {
                    EmoteWidgetRegistry.tryRegisterFromCapture(detail, line);
                } catch (Throwable ignored) {
                }
            }
        }

        Tile tile = null;
        try {
            tile = client.getSelectedSceneTile();
        } catch (Throwable ignored) {
        }
        WorldPoint wp = tile != null ? tile.getWorldLocation() : null;
        boolean wantCoords = config == null || config.captureCoordsEnabled();
        if (wp != null && wantCoords) {
            WallDoorCaptureReport.Result r = WallDoorCaptureReport.formatCoords(wp, false);
            if (r != null && addCapture(r.kind, r.line, r.detail)) {
                added++;
            }
        }

        HoverTarget menu = pickHoverTarget();
        NPC npc = nearestNpc(mx, my);
        if (npc != null && npc.getWorldLocation() != null) {
            WorldPoint np = npc.getWorldLocation();
            String line = "NPC " + clean(npc.getName()) + " @ " + np.getX() + "," + np.getY() + "," + np.getPlane();
            String detail = "kind: npc\n"
                    + "name: " + clean(npc.getName()) + "\n"
                    + "id: " + npc.getId() + "\n"
                    + "index: " + npc.getIndex() + "\n"
                    + "tile: " + np.getX() + ", " + np.getY() + ", " + np.getPlane() + "\n"
                    + "WorldPoint(" + np.getX() + ", " + np.getY() + ", " + np.getPlane() + ")";
            if (addCapture("npc", line, detail)) {
                added++;
            }
        }

        if (tile != null && wp != null) {
            TileObject bestObj = firstObject(tile);
            TileItem bestItem = firstGround(tile);
            if (bestObj != null) {
                String oname = objectName(bestObj.getId());
                String line = "Object " + oname + " #" + bestObj.getId()
                        + " @ " + wp.getX() + "," + wp.getY() + "," + wp.getPlane();
                String detail = "kind: object\n"
                        + "name: " + oname + "\n"
                        + "id: " + bestObj.getId() + "\n"
                        + "tile: " + wp.getX() + ", " + wp.getY() + ", " + wp.getPlane();
                if (!menu.option.isEmpty()) {
                    detail += "\noption: " + menu.option;
                }
                if (addCapture("object", line, detail)) {
                    added++;
                }
            }
            if (bestItem != null) {
                String iname = itemName(bestItem.getId());
                String line = "Ground " + iname + " #" + bestItem.getId()
                        + " x" + bestItem.getQuantity()
                        + " @ " + wp.getX() + "," + wp.getY() + "," + wp.getPlane();
                String detail = "kind: ground\n"
                        + "name: " + iname + "\n"
                        + "id: " + bestItem.getId() + "\n"
                        + "qty: " + bestItem.getQuantity() + "\n"
                        + "tile: " + wp.getX() + ", " + wp.getY() + ", " + wp.getPlane();
                if (addCapture("ground", line, detail)) {
                    added++;
                }
            }
            if (bestObj == null && bestItem == null && npc == null && !menu.target.isEmpty()) {
                String line = menu.option + " " + menu.target
                        + (wp != null ? " @ " + wp.getX() + "," + wp.getY() + "," + wp.getPlane() : "");
                String detail = "kind: tile\n" + menu.target + "\noption=" + menu.option
                        + (wp != null ? "\nWorldPoint(" + wp.getX() + ", " + wp.getY() + ", " + wp.getPlane() + ")" : "");
                if (addCapture("tile", line, detail)) {
                    added++;
                }
            }
        }

        if (added == 0) {
            addCapture("miss", "geen hit @ canvas " + mx + "," + my,
                    "kind: miss\ncanvas: " + mx + "," + my
                            + (menu.target.isEmpty() ? "" : "\nmenu: " + menu.option + " " + menu.target));
        }

        if (config == null || config.captureInspectExtras()) {
            DevInspectCapture.addPlayer(client, config);
            DevInspectCapture.addMenu(client, config);
            WorldPoint colTile = wp;
            if (colTile == null) {
                Player me = client.getLocalPlayer();
                colTile = me != null ? me.getWorldLocation() : null;
            }
            if (colTile != null) {
                DevInspectCapture.addCollision(client, config, colTile);
            }
        }
    }

    private boolean addCapture(String kind, String line, String detail) {
        if (line == null || line.isBlank()) {
            return false;
        }
        if (config != null && config.captureSkipDuplicates()
                && HoverCaptureLog.wouldSkipDuplicate(kind, line, detail)) {
            return false;
        }
        return HoverCaptureLog.add(kind, line, detail) != null;
    }

    private NPC nearestNpc(int mx, int my) {
        NPC bestNpc = null;
        int bestNpcDist = Integer.MAX_VALUE;
        Iterable<NPC> npcs;
        try {
            npcs = client.getNpcs();
        } catch (Throwable t) {
            return null;
        }
        if (npcs == null) {
            return null;
        }
        for (NPC n : npcs) {
            if (n == null) {
                continue;
            }
            LocalPoint lp = n.getLocalLocation();
            if (lp == null) {
                continue;
            }
            Point p = Perspective.localToCanvas(client, lp, client.getPlane(), n.getLogicalHeight() / 2);
            if (p == null) {
                continue;
            }
            int d = Math.abs(p.getX() - mx) + Math.abs(p.getY() - my);
            if (d < bestNpcDist && d < 48) {
                bestNpcDist = d;
                bestNpc = n;
            }
        }
        return bestNpc;
    }

    private HoverTarget pickHoverTarget() {
        MenuEntry[] entries;
        try {
            entries = client.getMenuEntries();
        } catch (Throwable t) {
            return new HoverTarget("", "");
        }
        if (entries == null || entries.length == 0) {
            return new HoverTarget("", "");
        }
        String walkTarget = "";
        String walkOption = "";
        for (MenuEntry e : entries) {
            if (e == null) {
                continue;
            }
            String opt = Text.removeTags(e.getOption());
            if (opt == null || opt.equalsIgnoreCase("Cancel")) {
                continue;
            }
            String tgt = Text.removeTags(e.getTarget());
            if (tgt == null) {
                tgt = "";
            }
            if ("Walk here".equalsIgnoreCase(opt)) {
                if (walkTarget.isEmpty() && !tgt.isEmpty()) {
                    walkTarget = tgt;
                    walkOption = opt;
                }
                continue;
            }
            if (!tgt.isEmpty() || !opt.isEmpty()) {
                return new HoverTarget(opt, tgt.isEmpty() ? opt : tgt);
            }
        }
        return new HoverTarget(walkOption, walkTarget);
    }

    private String objectName(int id) {
        try {
            if (client.getObjectDefinition(id) != null) {
                return clean(client.getObjectDefinition(id).getName());
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    private String itemName(int id) {
        try {
            if (client.getItemDefinition(id) != null) {
                return clean(client.getItemDefinition(id).getName());
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    private static TileObject firstObject(Tile tile) {
        if (tile.getGameObjects() != null) {
            for (GameObject go : tile.getGameObjects()) {
                if (go != null) {
                    return go;
                }
            }
        }
        if (tile.getWallObject() != null) {
            return tile.getWallObject();
        }
        if (tile.getDecorativeObject() != null) {
            return tile.getDecorativeObject();
        }
        if (tile.getGroundObject() != null) {
            return tile.getGroundObject();
        }
        return null;
    }

    private static TileItem firstGround(Tile tile) {
        List<TileItem> items = tile.getGroundItems();
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return Text.removeTags(s).replace('\n', ' ').trim();
    }

    private static final class HoverTarget {
        final String option;
        final String target;

        HoverTarget(String option, String target) {
            this.option = option != null ? option : "";
            this.target = target != null ? target : "";
        }
    }
}
