package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.tiles.NoWalkZones;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;

/**
 * Excluded tiles (rood) + vaste no-walk zones (oranje, bv. potato field).
 */
public class TileMarkerOverlay extends Overlay {

    private final Client client;
    private final LoneBotConfig config;

    @Inject
    public TileMarkerOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_MED);
    }

    @Override
    public Dimension render(Graphics2D g) {
        boolean show = config == null || config.showExcludedTiles();
        if (!show) {
            return null;
        }
        // Vaste no-walk (potato field) — oranje, alleen hek-polygoon tegels
        if (client.getPlane() == NoWalkZones.LB_POTATO_PLANE) {
            Color fill = new Color(255, 140, 40, 70);
            Color stroke = new Color(255, 160, 50, 200);
            for (WorldPoint wp : NoWalkZones.potatoFieldTiles()) {
                paintTile(g, wp, fill, stroke, false);
            }
        }

        for (WorldPoint wp : ExcludedTiles.all()) {
            paintTile(g, wp, new Color(220, 40, 40, 90), new Color(255, 80, 80, 220), true);
        }
        return null;
    }

    private void paintTile(Graphics2D g, WorldPoint wp, Color fill, Color stroke, boolean drawX) {
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
        g.setColor(stroke);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(poly);
        if (!drawX) {
            return;
        }
        Rectangle b = poly.getBounds();
        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;
        int arm = Math.max(4, Math.min(b.width, b.height) / 4);
        g.setColor(new Color(255, 60, 60, 240));
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(cx - arm, cy - arm, cx + arm, cy + arm);
        g.drawLine(cx + arm, cy - arm, cx - arm, cy + arm);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        int lw = fm.stringWidth("X");
        g.setColor(Color.BLACK);
        g.drawString("X", cx - lw / 2 + 1, cy + 4);
        g.setColor(new Color(255, 100, 100));
        g.drawString("X", cx - lw / 2, cy + 3);
    }
}
