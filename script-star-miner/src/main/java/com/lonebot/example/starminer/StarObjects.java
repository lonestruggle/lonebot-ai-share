package com.lonebot.example.starminer;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.movement.Movement;

import java.util.List;

/**
 * Crashed star — OSRS Wiki Infobox ({@code version1=Size-1 … version9=Size-9}):
 * Size-1=41229 … Size-7=41223, Size-8=41021, Size-9=41020.
 * (Niet omgekeerd: 41227 is size-3 / mining 30, geen T7.)
 */
public final class StarObjects {

    /** Alle crashed-star object-IDs (size 1 t/m 9). */
    public static final int[] STAR_IDS = {
            41229, 41228, 41227, 41226, 41225, 41224, 41223, 41021, 41020
    };

    private static final int SCENE_PICK_TILES = 24;

    private StarObjects() {
    }

    public static int miningLevelForTier(int tier) {
        if (tier <= 1) {
            return 10;
        }
        return Math.min(90, tier * 10);
    }

    /** Hoogste crashed-star laag die dit Mining-level nu mag minen (0 = onder T1). */
    public static int highestMineableTier(int miningLevel) {
        for (int t = 9; t >= 1; t--) {
            if (miningLevel >= miningLevelForTier(t)) {
                return t;
            }
        }
        return 0;
    }

    /**
     * Dichtstbijzijnde ster; bij meerdere op dezelfde plek de <b>laagste</b> tier
     * (oude T6-scenery mag een verse T5 niet verbergen).
     */
    public static ITileObject nearest() {
        return nearest(null);
    }

    /**
     * {@code hint} = laatste ster-tegel: tijdens T6→T5 is de oude ID even weg;
     * pak dan nog Mine/Prospect op die tegel (nieuwe ID).
     */
    public static ITileObject nearest(WorldPoint hint) {
        WorldPoint me = null;
        try {
            Players.LocalSnap snap = Players.snapshotLocal();
            me = snap != null ? snap.worldLocation : null;
        } catch (Throwable ignored) {
        }
        List<ITileObject> all;
        try {
            // Alleen object-ID — geen getName/hasAction op heel de scene (dat blokkeert walk-ticks).
            all = TileObjects.getAll(o -> o != null && isStarId(o.getId()), 80L);
        } catch (Throwable t) {
            all = null;
        }
        if (all == null || all.isEmpty()) {
            return null;
        }
        ITileObject best = null;
        int bestRank = Integer.MAX_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (ITileObject o : all) {
            if (o == null || o.getWorldLocation() == null) {
                continue;
            }
            int d = me != null && me.getPlane() == o.getWorldLocation().getPlane()
                    ? me.distanceTo(o.getWorldLocation()) : 99;
            boolean atHint = hintStar(o, hint);
            if (d > SCENE_PICK_TILES && !atHint) {
                continue;
            }
            int tier = tierFromObject(o);
            int rank = tier > 0 ? tier : 100;
            if (rank < bestRank || (rank == bestRank && d < bestDist)) {
                bestRank = rank;
                bestDist = d;
                best = o;
            }
        }
        return best;
    }

    /** Zelfde tegel als de ster die we net nog hadden — ID mag wisselen. */
    static boolean hintStar(ITileObject o, WorldPoint hint) {
        if (o == null || hint == null || o.getWorldLocation() == null) {
            return false;
        }
        if (o.getWorldLocation().getPlane() != hint.getPlane()) {
            return false;
        }
        if (o.getWorldLocation().distanceTo(hint) > 2) {
            return false;
        }
        int id = o.getId();
        if (tierFromId(id) > 0) {
            return true;
        }
        try {
            return o.hasAction("Mine") || o.hasAction("Prospect");
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isStar(ITileObject o) {
        return looksLikeStarObject(o);
    }

    static boolean isStarId(int id) {
        for (int sid : STAR_IDS) {
            if (id == sid) {
                return true;
            }
        }
        return false;
    }

    static boolean looksLikeStarObject(ITileObject o) {
        if (o == null) {
            return false;
        }
        if (isStarId(o.getId())) {
            return true;
        }
        if (nameLooksLikeStar(o.getName())) {
            return true;
        }
        try {
            return o.hasAction("Prospect") && o.hasAction("Mine");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Live laag: object-ID (wiki size) is de bron. Verse Prospect-chat wint bij conflict.
     * Feed telt hier niet.
     */
    public static int liveTier(ITileObject obj) {
        int objT = tierFromObject(obj);
        int p = StarInspect.prospectTier();
        int trusted = StarInspect.trustedTier();
        int t;
        if (StarInspect.prospectFresh() && p > 0) {
            t = p;
        } else if (objT > 0) {
            t = objT;
        } else {
            t = p > 0 ? p : trusted;
        }
        if (t <= 0) {
            return trusted;
        }
        if (trusted > 0 && t > trusted && !StarInspect.prospectFresh()) {
            return trusted;
        }
        return t;
    }

    /** Live object/prospect, niet de feed. */
    public static int requiredMining(ITileObject live, StarCall target) {
        int t = liveTier(live);
        if (t > 0) {
            return miningLevelForTier(t);
        }
        if (live == null && target != null && target.tier > 0) {
            return target.miningLevelRequired();
        }
        return 0;
    }

    /**
     * Stand-tegel naast de ster — niet de object-tegel zelf (die left-click is Mine).
     */
    public static WorldPoint waitStand(WorldPoint starTile, WorldPoint me) {
        if (starTile == null) {
            return me;
        }
        if (me != null && me.getPlane() == starTile.getPlane()) {
            int d = me.distanceTo(starTile);
            if (d >= 2 && d <= 8) {
                return me;
            }
            // Ver: loopbare tegel naast de ster (collision-map), niet de geblokkeerde object-tegel.
            if (d > 14) {
                WorldPoint stand = new WorldPoint(
                        starTile.getX(), starTile.getY() - 2, starTile.getPlane());
                WorldPoint w;
                try {
                    w = Movement.getNearestWalkableTile(stand);
                } catch (Throwable t) {
                    w = stand;
                }
                if (w != null && (w.getX() != starTile.getX() || w.getY() != starTile.getY())) {
                    return w;
                }
                return stand;
            }
        }
        int[][] offs = {
                {0, -2}, {0, 2}, {-2, 0}, {2, 0},
                {0, -3}, {0, 3}, {-3, 0}, {3, 0},
                {-2, -2}, {2, 2}, {-2, 2}, {2, -2}
        };
        for (int[] o : offs) {
            WorldPoint p = new WorldPoint(
                    starTile.getX() + o[0], starTile.getY() + o[1], starTile.getPlane());
            WorldPoint w;
            try {
                w = Movement.getNearestWalkableTile(p);
            } catch (Throwable t) {
                w = p;
            }
            if (w == null) {
                continue;
            }
            if (w.getX() == starTile.getX() && w.getY() == starTile.getY()) {
                continue;
            }
            return w;
        }
        return new WorldPoint(starTile.getX(), starTile.getY() - 2, starTile.getPlane());
    }

    static boolean nameLooksLikeStar(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase();
        return n.contains("crashed star") || n.equals("shooting star") || n.contains("crashed star");
    }

    public static int tierFromObject(ITileObject obj) {
        return obj == null ? 0 : tierFromId(obj.getId());
    }

    /**
     * Wiki Infobox: id1=Size-1=41229 … id9=Size-9=41020.
     * Draynor-check: obj 41227 + chat "size-3" / mining 30.
     */
    public static int tierFromId(int id) {
        switch (id) {
            case 41229:
                return 1;
            case 41228:
                return 2;
            case 41227:
                return 3;
            case 41226:
                return 4;
            case 41225:
                return 5;
            case 41224:
                return 6;
            case 41223:
                return 7;
            case 41021:
                return 8;
            case 41020:
                return 9;
            default:
                return 0;
        }
    }
}
