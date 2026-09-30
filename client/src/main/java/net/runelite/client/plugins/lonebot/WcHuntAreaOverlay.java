package net.runelite.client.plugins.lonebot;

import com.lonebot.example.woodcutter.WcCenters;
import com.lonebot.example.woodcutter.WoodcutterPlugin;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import java.util.Locale;

/**
 * WC-locatie zones op de ground (cirkel-rand of bbox), zoals Imp hunt areas.
 * Aan/uit via {@link LoneBotConfig#showWcOverlay()}.
 */
public class WcHuntAreaOverlay extends Overlay {

    private static final Color ACTIVE_EDGE = new Color(46, 204, 113, 210);
    private static final Color OTHER_EDGE = new Color(52, 152, 219, 160);
    private static final Color CENTER_FILL = new Color(46, 204, 113, 70);
    private static final int MAX_RENDER_R = 40;

    private final Client client;
    private final LoneBotConfig config;

    @Inject
    public WcHuntAreaOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(0.84f);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (config == null || !ScriptOverlayGate.wcWorld(config)) {
            return null;
        }

        String blob = WoodcutterPlugin.centersBlob;
        if (blob == null || blob.isBlank()) {
            blob = WcCenters.DEFAULT_BLOB;
        }
        List<WcCenters.Center> centers = WcCenters.parseActive(blob);
        if (centers.isEmpty()) {
            centers = WcCenters.parseActive(WcCenters.DEFAULT_BLOB);
        }

        String preferred = config.wcLocation();
        String want = preferred != null ? preferred.trim().toLowerCase(Locale.ROOT) : "auto";
        boolean auto = want.isEmpty() || "auto".equals(want);

        for (WcCenters.Center c : centers) {
            if (c == null || c.point == null) {
                continue;
            }
            boolean highlight = !auto && c.name != null
                    && c.name.toLowerCase(Locale.ROOT).contains(want);
            Color edge = highlight || auto ? ACTIVE_EDGE : OTHER_EDGE;
            if (c.hasEdge()) {
                renderEdgeBox(g, c, edge, shortLabel(c));
            } else {
                int r = Math.max(1, Math.min(MAX_RENDER_R, c.radius));
                renderRimCircle(g, c.point, r, edge, shortLabel(c) + " r" + r);
            }
            drawTileOutline(g, c.point, CENTER_FILL, edge);
        }
        return null;
    }

    private static String shortLabel(WcCenters.Center c) {
        if (c.name == null || c.name.isBlank()) {
            return "WC";
        }
        String n = c.name.trim();
        return n.length() > 22 ? n.substring(0, 20) + "…" : n;
    }

    private void renderRimCircle(Graphics2D g, WorldPoint center, int radius, Color edge, String label) {
        int r2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int d2 = dx * dx + dy * dy;
                if (d2 > r2 || d2 < inner2) {
                    continue;
                }
                drawTileOutline(g, new WorldPoint(center.getX() + dx, center.getY() + dy, center.getPlane()),
                        null, edge);
            }
        }
        if (label != null) {
            drawLabel(g, center, label, Color.WHITE);
        }
    }

    private void renderEdgeBox(Graphics2D g, WcCenters.Center c, Color edge, String label) {
        int minX = c.minX;
        int maxX = c.maxX;
        int minY = c.minY;
        int maxY = c.maxY;
        int plane = c.point.getPlane();
        for (int x = minX; x <= maxX; x++) {
            drawTileOutline(g, new WorldPoint(x, minY, plane), null, edge);
            drawTileOutline(g, new WorldPoint(x, maxY, plane), null, edge);
        }
        for (int y = minY + 1; y < maxY; y++) {
            drawTileOutline(g, new WorldPoint(minX, y, plane), null, edge);
            drawTileOutline(g, new WorldPoint(maxX, y, plane), null, edge);
        }
        if (label != null) {
            drawLabel(g, c.point, label, Color.WHITE);
        }
    }

    private void drawTileOutline(Graphics2D g, WorldPoint wp, Color fill, Color stroke) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return;
        }
        if (fill != null) {
            g.setColor(fill);
            g.fillPolygon(poly);
        }
        g.setColor(stroke);
        g.setStroke(new BasicStroke(2.2f));
        g.drawPolygon(poly);
    }

    private void drawLabel(Graphics2D g, WorldPoint wp, String text, Color fg) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Point p = Perspective.localToCanvas(client, lp, wp.getPlane());
        if (p == null) {
            return;
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int lw = fm.stringWidth(text);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(p.getX() - lw / 2 - 3, p.getY() - 14, lw + 6, 14, 3, 3);
        g.setColor(fg != null ? fg : Color.WHITE);
        g.drawString(text, p.getX() - lw / 2, p.getY() - 3);
    }
}
