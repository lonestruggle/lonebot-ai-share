package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Karamja-vulkaan: surface {@code Climb-down Rocks} (id 11441 @ 2856,3168)
 * ↔ dungeon climbing rope (y+6400). Imp/travel mag hier niet in vallen;
 * pad vermijdt de rocks; als we erin zitten → Climb rope.
 */
public final class KaramjaVolcano {

    /** Wiki: Rocks (Karamja Volcano). */
    public static final int ROCKS_OBJECT_ID = 11441;
    public static final WorldPoint SURFACE_ROCKS = new WorldPoint(2856, 3168, 0);
    /** Zelfde tegel +6400 Y (OSRS dungeon). User: 2856,9568–9569. */
    public static final WorldPoint DUNGEON_ROPE = new WorldPoint(2856, 9568, 0);
    /** Loopbaar naast het gat, niet op de Climb-down tegel. */
    public static final WorldPoint SAFE_SURFACE = new WorldPoint(2856, 3165, 0);

    private static volatile String lastLog = "";
    private static volatile long lastLogMs;

    private KaramjaVolcano() {
    }

    /**
     * Karamja-eiland ondergronds (surface-bbox + 6400 Y).
     */
    public static boolean isInDungeon(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        int sy = p.getY() - 6400;
        return p.getX() >= 2815 && p.getX() <= 2962 && sy >= 3130 && sy <= 3210;
    }

    /** Tegels waarop Climb-down Rocks staat — niet als walk-hop. */
    public static boolean isVolcanoRockTile(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        return Math.abs(p.getX() - SURFACE_ROCKS.getX()) <= 2
                && Math.abs(p.getY() - SURFACE_ROCKS.getY()) <= 2;
    }

    public static WorldPoint safeSurfaceTile() {
        return SAFE_SURFACE;
    }

    /**
     * @return delay-ms als we in de dungeon zitten (climb of loop naar rope); 0 = niet hier
     */
    public static int climbRopeIfInside() {
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (!isInDungeon(pos)) {
            return 0;
        }
        if (pos.distanceTo(DUNGEON_ROPE) > 6) {
            log("loop naar rope " + pos.getX() + "," + pos.getY());
            try {
                Movement.walkTo(DUNGEON_ROPE);
            } catch (Throwable ignored) {
            }
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        ITileObject rope = findRope();
        if (rope == null) {
            log("rope niet in scene @ " + pos.getX() + "," + pos.getY() + " — loop");
            try {
                Movement.walkTo(DUNGEON_ROPE);
            } catch (Throwable ignored) {
            }
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        boolean ok = false;
        try {
            if (rope.hasAction("Climb")) {
                ok = rope.interact("Climb");
            }
            if (!ok) {
                ok = rope.interact("Climb-up", "Climb");
            }
        } catch (Throwable t) {
            ok = false;
        }
        if (ok) {
            log("Climb rope → surface");
            return ThreadLocalRandom.current().nextInt(700, 1100);
        }
        log("Climb fail — retry");
        return ThreadLocalRandom.current().nextInt(400, 700);
    }

    private static ITileObject findRope() {
        ITileObject named = TileObjects.getNearest(o -> {
            if (o == null || o.getName() == null) {
                return false;
            }
            String n = o.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("climbing rope") && !n.equals("rope")) {
                return false;
            }
            return o.hasAction("Climb") || o.hasAction("Climb-up");
        });
        if (named != null) {
            return named;
        }
        return TileObjects.getNearest(o -> {
            if (o == null || o.getWorldLocation() == null) {
                return false;
            }
            if (o.getWorldLocation().distanceTo(DUNGEON_ROPE) > 3) {
                return false;
            }
            return o.hasAction("Climb") || o.hasAction("Climb-up");
        });
    }

    private static void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Walk/volcano] " + msg);
    }
}
