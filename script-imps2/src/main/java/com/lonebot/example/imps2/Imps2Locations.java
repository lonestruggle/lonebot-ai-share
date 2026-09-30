package com.lonebot.example.imps2;

import net.runelite.api.coords.WorldPoint;

/**
 * Karamja / Port Sarim coords — zelfde bron als {@code CoreImpsHandler}.
 */
public final class Imps2Locations {

    private Imps2Locations() {
    }

    /** Imp-jacht (default center). */
    public static final WorldPoint HUNT = new WorldPoint(2826, 3181, 0);
    /** Dock → hunt: rally als je ver van alle hunt-circles bent. */
    public static final WorldPoint RALLY = new WorldPoint(2830, 3182, 0);
    public static final WorldPoint EXTRA_IMP_AREA_1 = new WorldPoint(2826, 3149, 0);
    public static final WorldPoint EXTRA_IMP_AREA_2 = new WorldPoint(2832, 3200, 0);
    public static final WorldPoint SEARCH_SPOT_A = new WorldPoint(2841, 3164, 0);
    public static final WorldPoint SEARCH_SPOT_B = new WorldPoint(2846, 3188, 0);

    /** Musa Point: aanloop vóór Customs — niet op NPC-tegel (2956,3143). */
    public static final WorldPoint KARAMJA_BOAT_APPROACH = new WorldPoint(2954, 3144, 0);
    public static final WorldPoint KARAMJA_DOCK_NPC = new WorldPoint(2956, 3143, 0);
    public static final WorldPoint KARAMJA_DOCK_ALT_WAYPOINT = new WorldPoint(2947, 3154, 0);

    public static final WorldPoint PORTSARIM_DOCK = new WorldPoint(3028, 3210, 0);
    public static final WorldPoint PORTSARIM_BOAT_NPC = new WorldPoint(3027, 3218, 0);
    public static final WorldPoint DRAYNOR_BANK = new WorldPoint(3092, 3243, 0);

    public static final int BOAT_FARE = 30;
    public static final int CUSTOMS_OFFICER_ID = 380;
    public static final int[] PORT_SARIM_SEAMAN_IDS = {364, 365, 326};
    /** Pay-fare / ClickOnSight pas binnen zoveel tegels van de dock-tegel. */
    public static final int BOAT_SIGHT_TILES = 40;

    /** CombatBot Karamja box: 2815–2962 × 3130–3210. */
    public static boolean isOnKaramja(WorldPoint p) {
        if (p == null) {
            return false;
        }
        return p.getX() >= 2815 && p.getX() <= 2962
                && p.getY() >= 3130 && p.getY() <= 3210;
    }

    public static boolean inBoatClickRange(WorldPoint pos, WorldPoint dock) {
        if (pos == null || dock == null || pos.getPlane() != dock.getPlane()) {
            return false;
        }
        return pos.distanceTo(dock) <= BOAT_SIGHT_TILES;
    }

    /** Ver van hunt → rally (CombatBot dock→hunt). */
    public static WorldPoint huntWalkTarget(WorldPoint pos) {
        if (pos == null) {
            return RALLY;
        }
        int r = 20;
        if (pos.distanceTo(HUNT) > r + 15
                && pos.distanceTo(EXTRA_IMP_AREA_1) > r + 15
                && pos.distanceTo(EXTRA_IMP_AREA_2) > r + 15
                && pos.distanceTo(SEARCH_SPOT_A) > r + 15
                && pos.distanceTo(SEARCH_SPOT_B) > r + 15) {
            return RALLY;
        }
        WorldPoint best = RALLY;
        int bestD = pos.distanceTo(RALLY);
        for (WorldPoint c : new WorldPoint[]{
                HUNT, EXTRA_IMP_AREA_1, EXTRA_IMP_AREA_2, SEARCH_SPOT_A, SEARCH_SPOT_B, RALLY}) {
            int d = pos.distanceTo(c);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    public static boolean arrivedAtHunt(WorldPoint pos) {
        if (pos == null) {
            return false;
        }
        int r = 8;
        return pos.distanceTo(HUNT) <= r
                || pos.distanceTo(RALLY) <= r
                || pos.distanceTo(EXTRA_IMP_AREA_1) <= r
                || pos.distanceTo(EXTRA_IMP_AREA_2) <= r
                || pos.distanceTo(SEARCH_SPOT_A) <= r
                || pos.distanceTo(SEARCH_SPOT_B) <= r;
    }
}
