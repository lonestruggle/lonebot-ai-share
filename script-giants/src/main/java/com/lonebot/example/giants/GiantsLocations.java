package com.lonebot.example.giants;

import net.runelite.api.coords.WorldPoint;

/**
 * Exact CombatBot Hill Giants / Edgeville Dungeon tiles.
 */
public final class GiantsLocations {

    private GiantsLocations() {
    }

    public static final WorldPoint HUNT_CENTER = new WorldPoint(3115, 9837, 0);
    public static final int HUNT_RADIUS = 24;

    public static final WorldPoint EDGE_PRE_TRAPDOOR = new WorldPoint(3094, 3471, 0);
    public static final WorldPoint EDGE_TRAPDOOR = new WorldPoint(3097, 3468, 0);
    public static final int EDGE_TRAPDOOR_ID = 12342;
    public static final WorldPoint EDGE_LADDER_BELOW = new WorldPoint(3096, 9867, 0);

    public static final int BRASS_KEY_ID = 983;
    public static final String BRASS_KEY_NAME = "Brass key";
    public static final WorldPoint KEY_SPAWN = new WorldPoint(3131, 9862, 0);
    public static final WorldPoint HOP_LADDER = new WorldPoint(3116, 9852, 0);
    public static final int HOP_LADDER_ID = 12441;

    /** Varrock west shed — brass-key entrance (faster). */
    public static final WorldPoint VARROCK_SHED = new WorldPoint(3115, 3452, 0);
    /** OSRS shed door that consumes Brass key. */
    public static final int SHED_DOOR_ID = 2406;

    public static final String HILL_GIANT = "Hill Giant";

    public static final String DEFAULT_LOOT_CSV =
            "Big bones,Limpwurt root,Nature rune,Law rune,Cosmic rune,Death rune,"
                    + "Body rune,Mind rune,Chaos rune,Iron ore,Coal,Steel arrow,Iron arrow,Coins";

    public static boolean isUnderground(WorldPoint p) {
        return p != null && p.getY() >= 9000;
    }

    public static boolean isInHuntZone(WorldPoint p, int radius) {
        if (p == null || !isUnderground(p)) {
            return false;
        }
        int r = radius > 0 ? radius : HUNT_RADIUS;
        return p.distanceTo(HUNT_CENTER) <= r;
    }

    public static boolean isNearShed(WorldPoint p) {
        return p != null && !isUnderground(p) && p.distanceTo(VARROCK_SHED) <= 12;
    }

    public static boolean isNearTrapdoor(WorldPoint p) {
        return p != null && !isUnderground(p) && p.distanceTo(EDGE_TRAPDOOR) <= 10;
    }
}
