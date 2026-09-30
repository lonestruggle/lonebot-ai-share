package net.runelite.client.plugins.lonebot;

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

/**
 * Tekent Combat / Mining / Fishing area-centers (als overlay-toggles aan staan).
 */
public class SkillAreaOverlay extends Overlay {

    private static final Color COMBAT = new Color(231, 76, 60, 200);
    private static final Color MINING = new Color(200, 140, 50, 200);
    private static final Color FISHING = new Color(50, 120, 220, 200);
    private static final Color FILL = new Color(255, 255, 255, 40);
    private static final int MAX_RENDER_R = 40;

    private final Client client;
    private final LoneBotConfig config;

    @Inject
    public SkillAreaOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(0.83f);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (config == null) {
            return null;
        }
        if (config.showCombatAreaOverlay() && ScriptOverlayGate.cowWorld(config)) {
            renderBlob(g, AreaCenters.orDefault(config.combatCenters(), AreaCenters.Skill.COMBAT),
                    COMBAT, "Combat");
        }
        if (config.showMiningAreaOverlay()) {
            renderBlob(g, AreaCenters.orDefault(config.miningCenters(), AreaCenters.Skill.MINING),
                    MINING, "Mining");
        }
        if (config.showFishingAreaOverlay() && ScriptOverlayGate.fishWorld(config)) {
            renderBlob(g, AreaCenters.orDefault(config.fishingCenters(), AreaCenters.Skill.FISHING),
                    FISHING, "Fishing");
        }
        return null;
    }

    private void renderBlob(Graphics2D g, String blob, Color edge, String skillTag) {
        List<AreaCenters.Center> centers = AreaCenters.parseActive(blob);
        for (AreaCenters.Center c : centers) {
            if (c == null || c.point == null) {
                continue;
            }
            String label = shortLabel(c, skillTag);
            if (c.hasEdge()) {
                renderEdgeBox(g, c, edge, label);
            } else {
                int r = Math.max(1, Math.min(MAX_RENDER_R, c.radius));
                renderRimCircle(g, c.point, r, edge, label + " r" + r);
            }
            drawTileOutline(g, c.point, FILL, edge);
        }
    }

    private static String shortLabel(AreaCenters.Center c, String skillTag) {
        String n = c.name != null && !c.name.isBlank() ? c.name.trim() : skillTag;
        if (n.length() > 20) {
            n = n.substring(0, 18) + "…";
        }
        return skillTag.charAt(0) + ":" + n;
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
        drawLabel(g, center, label, Color.WHITE);
    }

    private void renderEdgeBox(Graphics2D g, AreaCenters.Center c, Color edge, String label) {
        int plane = c.point.getPlane();
        for (int x = c.minX; x <= c.maxX; x++) {
            drawTileOutline(g, new WorldPoint(x, c.minY, plane), null, edge);
            drawTileOutline(g, new WorldPoint(x, c.maxY, plane), null, edge);
        }
        for (int y = c.minY + 1; y < c.maxY; y++) {
            drawTileOutline(g, new WorldPoint(c.minX, y, plane), null, edge);
            drawTileOutline(g, new WorldPoint(c.maxX, y, plane), null, edge);
        }
        drawLabel(g, c.point, label, Color.WHITE);
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
