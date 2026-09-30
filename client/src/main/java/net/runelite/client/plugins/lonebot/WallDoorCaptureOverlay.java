package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.storm.sdk.walls.RoomScan;
import net.storm.sdk.walls.WallDoorCaptureState;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.HashSet;
import java.util.Set;

/**
 * Tekent laatste Get walls-scan: kamer (groen), talk-buiten (cyaan), deur (oranje), muur (rood).
 */
public class WallDoorCaptureOverlay extends Overlay {

    private static final long STALE_MS = 30 * 60 * 1000L;

    private final Client client;
    private final LoneBotConfig config;

    @Inject
    public WallDoorCaptureOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(0.85f);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config == null || !config.wallsOverlayEnabled()) {
            return null;
        }
        RoomScan scan = WallDoorCaptureState.getLatest();
        if (scan == null || System.currentTimeMillis() - scan.scannedAtMs > STALE_MS) {
            return null;
        }
        if (client == null || scan.center == null) {
            return null;
        }

        Set<String> cornerKeys = new HashSet<>();
        for (WorldPoint corner : scan.cornerHints) {
            if (corner != null) {
                cornerKeys.add(tileKey(corner));
            }
        }

        if (!scan.roomTiles.isEmpty()) {
            for (WorldPoint tile : scan.roomTiles) {
                if (tile == null || cornerKeys.contains(tileKey(tile))) {
                    continue;
                }
                renderTileFill(graphics, tile, new Color(80, 220, 100, 45), null);
            }
        }

        for (WorldPoint tile : scan.talkFromOutsideTiles) {
            renderTileFill(graphics, tile, new Color(80, 200, 255, 55), null);
        }

        for (RoomScan.WallEdgeMark edge : scan.wallEdges) {
            drawWallEdge(graphics, edge.tile, edge.edge, new Color(255, 60, 60, 120));
        }

        // Live: zelfde getWallObject-loop als Highlight, geknipt op Get-walls radius vanaf click.
        outlineWallObjectsInRadius(graphics, scan.center, scan.radius);

        for (RoomScan.DoorMark door : scan.doors) {
            renderTileFill(graphics, door.tile, new Color(255, 140, 40, 90), "D");
        }

        for (WorldPoint corner : scan.cornerHints) {
            renderTileFill(graphics, corner, new Color(80, 255, 120, 75), "◆");
        }

        renderTileFill(graphics, scan.center, new Color(255, 255, 80, 85), null);
        if (scan.isNpcScan()) {
            renderNpcLabel(graphics, scan.center, scan.npcName);
        }

        return null;
    }

    private static String tileKey(WorldPoint p) {
        return p.getX() + "," + p.getY() + "," + p.getPlane();
    }

    private void drawWallEdge(Graphics2D g, WorldPoint wp, String edge, Color color) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null || poly.npoints < 4) {
            return;
        }
        int i0;
        int i1;
        switch (edge) {
            case "north":
                i0 = 2;
                i1 = 3;
                break;
            case "east":
                i0 = 1;
                i1 = 2;
                break;
            case "south":
                i0 = 0;
                i1 = 1;
                break;
            case "west":
            default:
                i0 = 0;
                i1 = 3;
                break;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(poly.xpoints[i0], poly.ypoints[i0], poly.xpoints[i1], poly.ypoints[i1]);
    }

    /**
     * Zelfde als Highlight Wall Objects (scene-loop + convex hull), maar alleen
     * tegels binnen Get-walls scan-radius van het klik-centrum — ook binnenmuren.
     */
    private void outlineWallObjectsInRadius(Graphics2D g, WorldPoint center, int radius) {
        if (center == null || client.getScene() == null) {
            return;
        }
        if (client.getPlane() != center.getPlane()) {
            return;
        }
        int r = Math.max(1, radius);
        Tile[][][] tiles = client.getScene().getTiles();
        int plane = center.getPlane();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null) {
            return;
        }
        Color color = new Color(255, 80, 80, 200);
        for (Tile[] row : tiles[plane]) {
            if (row == null) {
                continue;
            }
            for (Tile tile : row) {
                if (tile == null) {
                    continue;
                }
                WallObject wall = tile.getWallObject();
                if (wall == null) {
                    continue;
                }
                WorldPoint wp = tile.getWorldLocation();
                if (wp == null || wp.getPlane() != plane || wp.distanceTo(center) > r) {
                    continue;
                }
                Shape hull = wall.getConvexHull();
                if (hull != null) {
                    g.setColor(new Color(255, 80, 80, 40));
                    g.fill(hull);
                    g.setColor(color);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(hull);
                } else {
                    drawAllTileEdges(g, wp, color);
                }
            }
        }
    }

    private void drawAllTileEdges(Graphics2D g, WorldPoint wp, Color color) {
        drawWallEdge(g, wp, "north", color);
        drawWallEdge(g, wp, "east", color);
        drawWallEdge(g, wp, "south", color);
        drawWallEdge(g, wp, "west", color);
    }

    private void renderNpcLabel(Graphics2D g, WorldPoint wp, String name) {
        if (wp == null || name == null || name.isEmpty()) {
            return;
        }
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        net.runelite.api.Point center = Perspective.localToCanvas(client, lp, 0);
        if (center == null) {
            return;
        }
        String label = name.length() > 18 ? name.substring(0, 17) + "…" : name;
        g.setFont(new Font("Arial", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        int lw = fm.stringWidth(label);
        int x = center.getX() - lw / 2;
        int y = center.getY() - 14;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(x - 3, y - 10, lw + 6, 14, 3, 3);
        g.setColor(new Color(255, 220, 120));
        g.drawString(label, x, y);
    }

    private void renderTileFill(Graphics2D g, WorldPoint wp, Color fill, String label) {
        if (wp == null) {
            return;
        }
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return;
        }
        g.setColor(fill);
        g.fill(poly);
        if (label != null && !label.isEmpty()) {
            net.runelite.api.Point center = Perspective.localToCanvas(client, lp, 0);
            if (center != null) {
                g.setFont(new Font("Arial", Font.BOLD, 11));
                FontMetrics fm = g.getFontMetrics();
                int lw = fm.stringWidth(label);
                g.setColor(new Color(0, 0, 0, 140));
                g.fillRoundRect(center.getX() - lw / 2 - 2, center.getY() - 8, lw + 4, 14, 3, 3);
                g.setColor(Color.WHITE);
                g.drawString(label, center.getX() - lw / 2, center.getY() + 3);
            }
        }
    }
}
