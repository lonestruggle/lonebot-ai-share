package net.runelite.client.plugins.lonebot;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Getekende landvlaggen voor Swing (emoji-vlaggen werken vaak niet op Windows/Java).
 */
public final class RegionFlagIcons {

    private static final int W = 22;
    private static final int H = 14;
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    private RegionFlagIcons() {
    }

    public static Icon forRegion(String region) {
        String key = normalize(region);
        return CACHE.computeIfAbsent(key, RegionFlagIcons::paint);
    }

    public static String normalize(String region) {
        if (region == null || region.isBlank()) {
            return "UK";
        }
        return region.trim().toUpperCase(Locale.ROOT);
    }

    private static Icon paint(String region) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            switch (region) {
                case "DE":
                    paintGermany(g);
                    break;
                case "US":
                    paintUsa(g);
                    break;
                case "AU":
                    paintAustralia(g);
                    break;
                case "UK":
                default:
                    paintUk(g);
                    break;
            }
            // dunne rand
            g.setColor(new Color(0, 0, 0, 120));
            g.drawRect(0, 0, W - 1, H - 1);
        } finally {
            g.dispose();
        }
        return new ImageIcon(img);
    }

    private static void paintUk(Graphics2D g) {
        g.setColor(new Color(1, 33, 105));
        g.fillRect(0, 0, W, H);
        g.setColor(Color.WHITE);
        // St. Andrew diagonals (simplified)
        g.drawLine(0, 0, W - 1, H - 1);
        g.drawLine(0, 1, W - 1, H);
        g.drawLine(0, H - 1, W - 1, 0);
        g.drawLine(0, H - 2, W - 1, -1);
        g.setColor(new Color(200, 16, 46));
        g.drawLine(0, 0, W - 1, H - 1);
        g.drawLine(0, H - 1, W - 1, 0);
        // Cross
        g.setColor(Color.WHITE);
        g.fillRect(W / 2 - 3, 0, 6, H);
        g.fillRect(0, H / 2 - 3, W, 6);
        g.setColor(new Color(200, 16, 46));
        g.fillRect(W / 2 - 1, 0, 3, H);
        g.fillRect(0, H / 2 - 1, W, 3);
    }

    private static void paintGermany(Graphics2D g) {
        int third = H / 3;
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, third);
        g.setColor(new Color(221, 0, 0));
        g.fillRect(0, third, W, third);
        g.setColor(new Color(255, 206, 0));
        g.fillRect(0, third * 2, W, H - third * 2);
    }

    private static void paintUsa(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(179, 25, 66));
        int stripe = Math.max(1, H / 7);
        for (int i = 0; i < 7; i++) {
            if (i % 2 == 0) {
                g.fillRect(0, i * stripe, W, stripe);
            }
        }
        g.setColor(new Color(10, 49, 97));
        g.fillRect(0, 0, W * 2 / 5, stripe * 4);
        g.setColor(Color.WHITE);
        for (int y = 1; y < stripe * 4 - 1; y += 3) {
            for (int x = 1; x < W * 2 / 5 - 1; x += 3) {
                g.fillRect(x, y, 1, 1);
            }
        }
    }

    private static void paintAustralia(Graphics2D g) {
        g.setColor(new Color(0, 0, 139));
        g.fillRect(0, 0, W, H);
        // small UK canton
        g.setColor(new Color(1, 33, 105));
        g.fillRect(0, 0, W / 2, H / 2);
        g.setColor(Color.WHITE);
        g.drawLine(0, 0, W / 2 - 1, H / 2 - 1);
        g.drawLine(0, H / 2 - 1, W / 2 - 1, 0);
        g.setColor(new Color(200, 16, 46));
        g.fillRect(W / 4 - 1, 0, 2, H / 2);
        g.fillRect(0, H / 4 - 1, W / 2, 2);
        // Southern Cross dots
        g.setColor(Color.WHITE);
        g.fillOval(W * 2 / 3, 3, 2, 2);
        g.fillOval(W - 5, H / 2, 2, 2);
        g.fillOval(W * 2 / 3 + 2, H - 5, 2, 2);
        g.fillOval(W / 2 + 2, H / 2 + 1, 2, 2);
    }
}
