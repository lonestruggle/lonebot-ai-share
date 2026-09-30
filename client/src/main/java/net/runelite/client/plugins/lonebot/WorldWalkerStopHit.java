package net.runelite.client.plugins.lonebot;

import net.storm.sdk.movement.WorldWalker;

import java.awt.Rectangle;

/**
 * Canvas-hitbox van de STOP WALKER-knop op de control-overlay.
 */
final class WorldWalkerStopHit {

    private static volatile int localX;
    private static volatile int localY;
    private static volatile int width;
    private static volatile int height;
    private static volatile boolean armed;

    private WorldWalkerStopHit() {
    }

    static void setLocal(int x, int y, int w, int h) {
        localX = x;
        localY = y;
        width = Math.max(0, w);
        height = Math.max(0, h);
        armed = WorldWalker.isActive() && w > 0 && h > 0;
    }

    static void clear() {
        armed = false;
        width = 0;
        height = 0;
    }

    static boolean containsCanvas(int canvasX, int canvasY, Rectangle overlayBounds) {
        if (!armed || !WorldWalker.isActive() || overlayBounds == null) {
            return false;
        }
        int lx = canvasX - overlayBounds.x;
        int ly = canvasY - overlayBounds.y;
        return lx >= localX && lx < localX + width && ly >= localY && ly < localY + height;
    }
}
