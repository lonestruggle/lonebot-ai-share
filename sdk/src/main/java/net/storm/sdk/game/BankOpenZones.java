package net.storm.sdk.game;

import net.runelite.api.coords.WorldPoint;

/**
 * Get-walls talk-zones van bankkamers. In de zone = Bank-klik, geen walk naar booth/anker.
 * Andere banken: zelfde capture (4 hoeken / walkable bbox), hier plakken.
 */
public final class BankOpenZones {

    private BankOpenZones() {
    }

    /**
     * Draynor village bank — capture 2026-09-14.
     * walkable bbox 3089,3240 → 3097,3247 (9×8), plane 0.
     */
    public static boolean draynor(WorldPoint p) {
        return inBox(p, 0, 3089, 3240, 3097, 3247);
    }

    /**
     * Al Kharid stad-bank (niet Shantay). Booth-kamer + deur-tegel.
     * walkable bbox 3265,3162 → 3275,3173, plane 0.
     */
    public static boolean alKharid(WorldPoint p) {
        return inBox(p, 0, 3265, 3162, 3275, 3173);
    }

    /** In een bekende open-zone: Bank-klik, geen walk naar booth/Shantay. */
    public static boolean contains(WorldPoint p) {
        return draynor(p) || alKharid(p);
    }

    private static boolean inBox(WorldPoint p, int plane, int x0, int y0, int x1, int y1) {
        if (p == null || p.getPlane() != plane) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        return x >= Math.min(x0, x1) && x <= Math.max(x0, x1)
                && y >= Math.min(y0, y1) && y <= Math.max(y0, y1);
    }
}
