package net.runelite.client.plugins.lonebot;

import com.lonebot.example.imps.CoreImpsHandler;
import net.runelite.api.Client;
import net.runelite.api.NPC;
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
 * Imp hunting-area: buitenrand (cirkel) + scorpion-zones (vierkant, CombatBot-stijl)
 * + live Scorpion-NPC radius (zichtbaar als scorpions in scene zijn).
 */
public class ImpHuntAreaOverlay extends Overlay {

    private static final Color MAIN_EDGE = new Color(231, 76, 60, 200);
    private static final Color EXTRA1_EDGE = new Color(39, 174, 96, 200);
    private static final Color EXTRA2_EDGE = new Color(230, 126, 34, 200);
    private static final Color SCORP_A = new Color(192, 57, 43, 210);
    private static final Color SCORP_B = new Color(142, 68, 173, 210);
    private static final Color SCORP_LIVE = new Color(255, 60, 60, 230);
    /** CombatBot AreaOverlay: cmb > NPC×2+1 → groen. */
    private static final Color SCORP_SAFE = new Color(50, 200, 50, 140);
    private static final Color SCORP_SAFE_LABEL = new Color(100, 255, 100);
    private static final Color SEARCH_FILL = new Color(241, 196, 15, 90);
    private static final Color SEARCH_EDGE = new Color(241, 196, 15, 230);
    private static final Color RALLY_FILL = new Color(80, 180, 255, 90);
    private static final Color RALLY_EDGE = new Color(80, 180, 255, 220);
    private static final int MAX_RENDER_R = 40;

    private final Client client;
    private final LoneBotConfig config;

    @Inject
    public ImpHuntAreaOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(0.85f);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (config == null || !ScriptOverlayGate.impWorld(config) || !config.showImpsHuntOverlay()) {
            return null;
        }

        int r = Math.max(1, Math.min(MAX_RENDER_R, config.impsHuntingRadius()));
        WorldPoint main = new WorldPoint(config.impsHuntingX(), config.impsHuntingY(), 0);

        renderRimCircle(g, main, r, MAIN_EDGE, "Hunt");
        renderRimCircle(g, CoreImpsHandler.EXTRA_IMP_AREA_1, r, EXTRA1_EDGE, "Extra1");
        renderRimCircle(g, CoreImpsHandler.EXTRA_IMP_AREA_2, r, EXTRA2_EDGE, "Extra2");

        drawTileOutline(g, CoreImpsHandler.SEARCH_SPOT_A, SEARCH_FILL, SEARCH_EDGE);
        drawLabel(g, CoreImpsHandler.SEARCH_SPOT_A, "Zoek A", Color.WHITE);
        drawTileOutline(g, CoreImpsHandler.SEARCH_SPOT_B, SEARCH_FILL, SEARCH_EDGE);
        drawLabel(g, CoreImpsHandler.SEARCH_SPOT_B, "Zoek B", Color.WHITE);

        if (config.impsAvoidScorpions()) {
            int sr = Math.max(1, Math.min(MAX_RENDER_R, config.impsScorpionAvoidRadius()));
            boolean safe = scorpionsSafeForPlayer();
            Color a = safe ? SCORP_SAFE : SCORP_A;
            Color b = safe ? SCORP_SAFE : SCORP_B;
            Color live = safe ? SCORP_SAFE : SCORP_LIVE;
            renderScorpionSquare(g, CoreImpsHandler.SCORPION_ZONE_1, sr, a,
                    safe ? "✅ Veilig A" : "⚠ Scorp A", safe);
            renderScorpionSquare(g, CoreImpsHandler.SCORPION_ZONE_2, sr, b,
                    safe ? "✅ Veilig B" : "⚠ Scorp B", safe);
            renderLiveScorpionRadii(g, sr, live, safe);
        }

        drawTileOutline(g, CoreImpsHandler.RALLY, RALLY_FILL, RALLY_EDGE);
        drawLabel(g, CoreImpsHandler.RALLY, "Rally", Color.WHITE);

        return null;
    }

    /** Alleen tiles op de buitenrand van de cirkel (hunt zones). */
    private void renderRimCircle(Graphics2D g, WorldPoint center, int radius, Color edge, String label) {
        if (center == null || radius <= 0) {
            return;
        }
        int r2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int d2 = dx * dx + dy * dy;
                if (d2 > r2 || d2 < inner2) {
                    continue;
                }
                WorldPoint wp = new WorldPoint(center.getX() + dx, center.getY() + dy, center.getPlane());
                drawTileOutline(g, wp, null, edge);
            }
        }
        if (label != null) {
            drawLabel(g, center, label + " r" + radius, Color.WHITE);
        }
    }

    /** CombatBot: veilig als jouw cmb &gt; NPC-level × 2 + 1 (default 14 → cmb 30+). */
    private boolean scorpionsSafeForPlayer() {
        try {
            net.runelite.api.Player lp = client.getLocalPlayer();
            if (lp == null) {
                return false;
            }
            int my = lp.getCombatLevel();
            int npc = Math.max(1, config.impsScorpionLevel());
            return my > (npc * 2) + 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** CombatBot-stijl: Chebyshev-vierkant (zichtbare rand-tegels in scene). */
    private void renderScorpionSquare(Graphics2D g, WorldPoint center, int radius, Color edge,
                                      String label, boolean safe) {
        if (center == null || radius <= 0) {
            return;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
                    continue;
                }
                WorldPoint wp = new WorldPoint(center.getX() + dx, center.getY() + dy, center.getPlane());
                drawTileOutline(g, wp, null, edge);
            }
        }
        if (label != null) {
            Color fg = safe ? SCORP_SAFE_LABEL : edge.brighter();
            drawLabel(g, center, label + " r" + radius, fg);
        }
    }

    private void renderLiveScorpionRadii(Graphics2D g, int radius, Color edge, boolean safe) {
        try {
            List<NPC> npcs = client.getNpcs();
            if (npcs == null) {
                return;
            }
            for (NPC npc : npcs) {
                if (npc == null || npc.getName() == null) {
                    continue;
                }
                if (!"Scorpion".equalsIgnoreCase(npc.getName())) {
                    continue;
                }
                WorldPoint wp = npc.getWorldLocation();
                if (wp == null) {
                    continue;
                }
                renderScorpionSquare(g, wp, radius, edge, safe ? "✅ Scorp" : "⚠ Scorp", safe);
            }
        } catch (Throwable ignored) {
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
        g.setStroke(new BasicStroke(2.5f));
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
