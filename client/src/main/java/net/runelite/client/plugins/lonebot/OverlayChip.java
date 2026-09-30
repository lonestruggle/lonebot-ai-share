package net.runelite.client.plugins.lonebot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/** Gouden/gekleurde verberg-chip (CombatBot-stijl). */
final class OverlayChip {

    static final int SIZE = 18;
    static final Color GOLD = new Color(255, 215, 0);

    private OverlayChip() {
    }

    static Dimension drawMinimized(Graphics2D g, Color fill) {
        Color c = fill != null ? fill : GOLD;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(0, 0, SIZE + 8, SIZE + 8, 6, 6);
        g.setColor(c);
        g.fillRoundRect(4, 4, SIZE, SIZE, 4, 4);
        g.setColor(GOLD);
        g.drawRoundRect(0, 0, SIZE + 8, SIZE + 8, 6, 6);
        return new Dimension(SIZE + 16, SIZE + 16);
    }

    static void drawTitle(Graphics2D g, Color fill) {
        Color c = fill != null ? fill : GOLD;
        g.setColor(c);
        g.fillRoundRect(4, 2, 12, 12, 3, 3);
        g.setColor(GOLD);
        g.drawRoundRect(4, 2, 12, 12, 3, 3);
    }
}
