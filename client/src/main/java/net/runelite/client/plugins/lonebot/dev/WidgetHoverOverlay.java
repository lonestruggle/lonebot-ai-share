package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Widget onder muis — CombatBot {@code WidgetHoverOverlay} (RuneLite-only).
 */
public class WidgetHoverOverlay extends Overlay {

    private static final int MAX_GROUP = 700;
    private static final int MAX_CHILD = 80;

    private final Client client;
    private final LoneBotConfig config;

    private Widget cached;
    private int lastMx = Integer.MIN_VALUE;
    private int lastMy = Integer.MIN_VALUE;
    private int lastTick = -1;

    @Inject
    public WidgetHoverOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (client == null || client.getGameState() != GameState.LOGGED_IN || config == null) {
            return null;
        }
        boolean master = config.devWidgetDebugging();
        boolean tip = master || config.devWidgetHoverTooltip();
        boolean hi = master || config.devWidgetHoverHighlight();
        if (!tip && !hi) {
            return null;
        }

        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) {
            return null;
        }
        int tick = client.getTickCount();
        if (mouse.getX() != lastMx || mouse.getY() != lastMy || tick != lastTick) {
            lastMx = mouse.getX();
            lastMy = mouse.getY();
            lastTick = tick;
            cached = findSmallestAt(client, mouse.getX(), mouse.getY());
        }

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (hi && cached != null) {
            Rectangle b = boundsOf(cached);
            if (b != null) {
                g.setColor(new Color(220, 80, 255, 40));
                g.fill(b);
                g.setColor(new Color(240, 120, 255, 235));
                g.setStroke(new BasicStroke(2.5f));
                g.draw(b);
                String iface = ifaceOf(cached);
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
                g.setColor(new Color(0, 0, 0, 180));
                int ly = Math.max(14, b.y - 4);
                g.fillRoundRect(b.x - 2, ly - 11, g.getFontMetrics().stringWidth(iface) + 6, 14, 4, 4);
                g.setColor(new Color(255, 200, 255));
                g.drawString(iface, b.x, ly);
            }
        }

        if (tip) {
            List<String> lines = buildLines(cached, mouse);
            drawTooltip(g, mouse.getX(), mouse.getY(), lines);
        }
        return null;
    }

    private static List<String> buildLines(Widget w, Point mouse) {
        List<String> lines = new ArrayList<>(8);
        if (w == null) {
            lines.add("geen widget op pixel");
        } else {
            int packed = w.getId();
            lines.add("iface=" + (packed >>> 16) + "," + (packed & 0xFFFF) + "  id=" + packed);
            String name = clean(w.getName());
            String text = clean(w.getText());
            String actions = formatActions(w);
            if (!name.isEmpty()) {
                lines.add("name: " + trunc(name, 56));
            }
            if (!text.isEmpty()) {
                lines.add("text: " + trunc(text, 56));
            }
            if (!actions.isEmpty()) {
                lines.add("actions: " + trunc(actions, 56));
            }
            try {
                int itemId = w.getItemId();
                if (itemId > 0) {
                    lines.add("itemId: " + itemId + " qty=" + w.getItemQuantity());
                }
            } catch (Throwable ignored) {
            }
            Rectangle b = boundsOf(w);
            if (b != null) {
                lines.add(String.format(Locale.ROOT, "canvas: %d,%d %dx%d", b.x, b.y, b.width, b.height));
            }
        }
        lines.add("muis: " + mouse.getX() + "," + mouse.getY());
        return lines;
    }

    private void drawTooltip(Graphics2D g, int mx, int my, List<String> lines) {
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        int pad = 6;
        int lineH = g.getFontMetrics().getHeight();
        int maxW = 0;
        for (String line : lines) {
            maxW = Math.max(maxW, g.getFontMetrics().stringWidth(line));
        }
        int boxW = maxW + pad * 2;
        int boxH = lines.size() * lineH + pad * 2 - 2;
        int drawX = mx + 14;
        int drawY = my + 14;
        int cw = Math.max(100, client.getCanvasWidth());
        int ch = Math.max(100, client.getCanvasHeight());
        if (drawX + boxW > cw - 8) {
            drawX = mx - boxW - 14;
        }
        if (drawY + boxH > ch - 8) {
            drawY = my - boxH - 14;
        }
        drawX = Math.max(4, drawX);
        drawY = Math.max(4, drawY);

        g.setColor(new Color(0, 0, 0, 200));
        g.fill(new RoundRectangle2D.Float(drawX, drawY, boxW, boxH, 8, 8));
        g.setColor(new Color(120, 220, 140));
        g.draw(new RoundRectangle2D.Float(drawX, drawY, boxW, boxH, 8, 8));
        g.setColor(new Color(240, 248, 255));
        int y = drawY + pad + g.getFontMetrics().getAscent();
        for (String line : lines) {
            g.drawString(line, drawX + pad, y);
            y += lineH;
        }
    }

    /** Kleinste zichtbare widget op canvas-pixel (client-thread). */
    public static Widget findSmallestAt(Client client, int mx, int my) {
        if (client == null) {
            return null;
        }
        Best best = new Best();
        for (int g = 0; g <= MAX_GROUP; g++) {
            for (int c = 0; c <= MAX_CHILD; c++) {
                Widget root;
                try {
                    root = client.getWidget(g, c);
                } catch (Throwable t) {
                    continue;
                }
                if (root != null) {
                    visit(root, mx, my, best);
                }
            }
        }
        return best.w;
    }

    public static String captureLine(Widget w) {
        if (w == null) {
            return "widget (geen)";
        }
        int packed = w.getId();
        String name = clean(w.getName());
        String text = clean(w.getText());
        String label = !name.isEmpty() ? name : (!text.isEmpty() ? text : ifaceOf(w));
        return "Widget " + label + " iface=" + (packed >>> 16) + "," + (packed & 0xFFFF);
    }

    public static String captureDetail(Widget w, int mx, int my) {
        List<String> lines = buildLines(w, new Point(mx, my));
        StringBuilder sb = new StringBuilder("kind: widget\n");
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private static void visit(Widget w, int mx, int my, Best best) {
        if (w == null) {
            return;
        }
        try {
            if (w.isHidden()) {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        Rectangle b = boundsOf(w);
        if (b != null && b.contains(mx, my)) {
            int area = b.width * b.height;
            if (area > 0 && area < best.area) {
                best.area = area;
                best.w = w;
            }
        }
        Widget[] kids = w.getDynamicChildren();
        if (kids != null) {
            for (Widget k : kids) {
                visit(k, mx, my, best);
            }
        }
        kids = w.getStaticChildren();
        if (kids != null) {
            for (Widget k : kids) {
                visit(k, mx, my, best);
            }
        }
        kids = w.getChildren();
        if (kids != null) {
            for (Widget k : kids) {
                visit(k, mx, my, best);
            }
        }
    }

    private static Rectangle boundsOf(Widget w) {
        try {
            Point loc = w.getCanvasLocation();
            int ww = w.getWidth();
            int wh = w.getHeight();
            if (loc != null && ww > 0 && wh > 0) {
                return new Rectangle(loc.getX(), loc.getY(), ww, wh);
            }
            return w.getBounds();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String ifaceOf(Widget w) {
        int packed = w.getId();
        return (packed >>> 16) + "," + (packed & 0xFFFF);
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return Text.removeTags(s).replace('\n', ' ').trim();
    }

    private static String formatActions(Widget w) {
        try {
            String[] a = w.getActions();
            if (a == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String x : a) {
                if (x == null || x.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(x);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static final class Best {
        int area = Integer.MAX_VALUE;
        Widget w;
    }
}
