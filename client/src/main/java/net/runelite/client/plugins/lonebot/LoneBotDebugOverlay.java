package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.storm.sdk.bot.BotRuntime;
import com.lonebot.example.CityCircleTestPlugin;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;

/**
 * Canvas debug: alleen city-ring markers (geen pad/CLICK/FLAG/TARGET).
 * Toggle via panel / {@link BotRuntime#canvasDebugEnabled}.
 */
public class LoneBotDebugOverlay extends Overlay {

    private final Client client;
    private final LoneBotConfig config;

    @Inject
    public LoneBotDebugOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_MED);
    }

    @Override
    public Dimension render(Graphics2D g) {
        boolean on = BotRuntime.canvasDebugEnabled
                || (config != null && config.canvasDebugOverlay());
        if (!on) {
            return null;
        }

        List<CityCircleTestPlugin.CityStop> cities = CityCircleTestPlugin.CITIES;
        for (int i = 0; i < cities.size(); i++) {
            CityCircleTestPlugin.CityStop c = cities.get(i);
            drawTile(g, c.tile, new Color(80, 180, 255, 120), new Color(80, 180, 255, 220));
            drawLabel(g, c.tile, c.name);
            CityCircleTestPlugin.CityStop next = cities.get((i + 1) % cities.size());
            drawWorldLine(g, c.tile, next.tile, new Color(80, 180, 255, 90));
        }

        return null;
    }

    private void drawTile(Graphics2D g, WorldPoint wp, Color fill, Color stroke) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return;
        }
        g.setColor(fill);
        g.fillPolygon(poly);
        g.setColor(stroke);
        g.setStroke(new BasicStroke(2f));
        g.drawPolygon(poly);
    }

    private void drawLabel(Graphics2D g, WorldPoint wp, String text) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return;
        }
        Point p = Perspective.localToCanvas(client, lp, wp.getPlane());
        if (p == null) {
            return;
        }
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(p.getX() - 2, p.getY() - 14, text.length() * 7 + 4, 14);
        g.setColor(Color.WHITE);
        g.drawString(text, p.getX(), p.getY() - 3);
    }

    private void drawWorldLine(Graphics2D g, WorldPoint a, WorldPoint b, Color color) {
        LocalPoint la = LocalPoint.fromWorld(client, a);
        LocalPoint lb = LocalPoint.fromWorld(client, b);
        if (la == null || lb == null) {
            return;
        }
        Point pa = Perspective.localToCanvas(client, la, a.getPlane());
        Point pb = Perspective.localToCanvas(client, lb, b.getPlane());
        if (pa == null || pb == null) {
            return;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(pa.getX(), pa.getY(), pb.getX(), pb.getY());
    }
}
