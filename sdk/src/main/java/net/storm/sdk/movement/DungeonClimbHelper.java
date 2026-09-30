package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.movement.pathfinder.GlobalPathfinder;
import net.storm.sdk.movement.pathfinder.TransportLoader;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Surface ↔ dungeon (zelfde plane, Y±6400): Climb-up / Climb-down.
 * {@link PlaneChangeHelper} dekt alleen floor-wissel (plane 0/1/2), niet Edge-huis stairs.
 * <p>
 * Klimt <b>niet</b> de dichtstbijzijnde trapdoor — alleen als TSV-transport land
 * dicht genoeg bij het loopdoel ligt (anders bovenlangs naar de juiste ingang).
 */
public final class DungeonClimbHelper {

    private static final int CLIMB_RANGE = 6;
    /** Land na climb moet dicht bij dest — voorkomt verkeerde kelder/mijn. */
    private static final int USEFUL_LAND_DIST = 48;
    private static final long COOLDOWN_MS = 1_400L;
    private static final long WAIT_MS = 5_500L;
    private static final long NO_REVERSE_MS = 5_500L;

    private static volatile long nextOkMs;
    private static volatile long waitUntilMs;
    private static volatile WorldPoint waitFrom;
    private static volatile Boolean lastToDungeon;
    private static volatile long noReverseUntilMs;

    private DungeonClimbHelper() {
    }

    /** Na pad-transport climb: zelfde reverse-blokkade als scene-climb. */
    public static void noteClimb(boolean toDungeon) {
        lastToDungeon = toDungeon;
        noReverseUntilMs = System.currentTimeMillis() + NO_REVERSE_MS;
    }

    /**
     * @return true als climb/wacht — caller mag deze tick niet verder pathfind-walken
     */
    public static boolean progress(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null || !GlobalPathfinder.isLikelyInstanceGap(from, dest)) {
            clearWait();
            return false;
        }
        long now = System.currentTimeMillis();
        boolean toDungeon = towardDungeon(from, dest);
        // Net Climb-up → niet meteen Climb-down (en omgekeerd)
        if (lastToDungeon != null && now < noReverseUntilMs && toDungeon != lastToDungeon) {
            BotRuntime.logConsole("[Walk/dung] skip reverse (net andere kant)");
            return false;
        }
        if (waitFrom != null && waitUntilMs > now) {
            // Na climb is Y±6400 — distance tot waitFrom is groot; toch wachten
            if (GlobalPathfinder.isLikelyInstanceGap(from, dest)) {
                return true;
            }
            clearWait();
            return false;
        }
        if (now < nextOkMs) {
            Players.LocalSnap me = Players.snapshotLocal();
            return me != null && me.moving;
        }

        Transport jump = bestNearbyJump(from, dest, toDungeon);
        if (jump == null) {
            return false;
        }
        WorldPoint src = jump.getSource();
        ITileObject obj = TileObjects.getNearest(o -> matchesClimbNear(o, from, src, toDungeon));
        if (obj == null) {
            return false;
        }
        WorldPoint tile = obj.getWorldLocation();
        if (tile == null || from.distanceTo(tile) > CLIMB_RANGE) {
            return false;
        }
        String action = pickAction(obj, toDungeon);
        if (action == null) {
            return false;
        }
        WalkCamera.ensureLookingAt(tile);
        boolean ok = obj.interact(action);
        nextOkMs = now + COOLDOWN_MS + ThreadLocalRandom.current().nextInt(400);
        if (ok) {
            waitFrom = from;
            waitUntilMs = now + WAIT_MS;
            lastToDungeon = toDungeon;
            noReverseUntilMs = now + NO_REVERSE_MS;
            WorldPoint land = jump.getDestination();
            BotRuntime.logConsole("[Walk/dung] " + action + " \"" + obj.getName() + "\" @"
                    + tile.getX() + "," + tile.getY()
                    + " land=" + (land != null ? land.getX() + "," + land.getY() : "?")
                    + " dLand=" + (land != null ? land.distanceTo(dest) : -1)
                    + (toDungeon ? " → dungeon" : " → surface"));
        }
        return ok;
    }

    /**
     * TSV long-jump dichtbij speler waarvan land dicht bij dest ligt.
     * Geen match → false (bovenlangs / pad naar juiste ingang).
     */
    private static Transport bestNearbyJump(WorldPoint from, WorldPoint dest, boolean toDungeon) {
        List<Transport> all = TransportLoader.getCustomTransports();
        if (all == null || all.isEmpty()) {
            return null;
        }
        Transport best = null;
        int bestLand = Integer.MAX_VALUE;
        for (Transport t : all) {
            if (t == null || t.getSource() == null || t.getDestination() == null) {
                continue;
            }
            WorldPoint src = t.getSource();
            WorldPoint land = t.getDestination();
            if (from.distanceTo(src) > CLIMB_RANGE) {
                continue;
            }
            if (!GlobalPathfinder.isLikelyInstanceGap(src, land)) {
                continue;
            }
            boolean jumpDown = towardDungeon(src, land);
            if (jumpDown != toDungeon) {
                continue;
            }
            int dLand = land.distanceTo(dest);
            if (dLand > USEFUL_LAND_DIST) {
                continue;
            }
            if (dLand < bestLand) {
                bestLand = dLand;
                best = t;
            }
        }
        if (best == null) {
            logSkipNoUsefulEntrance();
        }
        return best;
    }

    private static volatile long lastSkipLogMs;

    private static void logSkipNoUsefulEntrance() {
        long now = System.currentTimeMillis();
        if (now - lastSkipLogMs < 2_000L) {
            return;
        }
        lastSkipLogMs = now;
        BotRuntime.logConsole("[Walk/dung] geen nuttige ingang ≤" + CLIMB_RANGE
                + "t (land≤" + USEFUL_LAND_DIST + " tot dest) — loop naar juiste trap");
    }

    /** true = we moeten dieper (hogere Y / dungeon). */
    public static boolean towardDungeon(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null) {
            return false;
        }
        return dest.getY() - from.getY() >= 2000
                || (from.getY() < 6400 && dest.getY() >= 6400);
    }

    private static boolean matchesClimbNear(ITileObject o, WorldPoint from, WorldPoint src, boolean toDungeon) {
        if (o == null || o.getWorldLocation() == null || src == null) {
            return false;
        }
        if (o.getWorldLocation().getPlane() != from.getPlane()) {
            return false;
        }
        if (o.getWorldLocation().distanceTo(src) > 2) {
            return false;
        }
        if (from.distanceTo(o.getWorldLocation()) > CLIMB_RANGE) {
            return false;
        }
        String n = o.getName();
        if (n == null || n.isBlank() || "null".equalsIgnoreCase(n)) {
            return false;
        }
        String low = n.toLowerCase(Locale.ROOT);
        boolean stairish = low.contains("stair") || low.contains("ladder") || low.contains("trapdoor");
        if (!stairish) {
            return false;
        }
        if (toDungeon) {
            return o.hasAction("Climb-down") || o.hasAction("Climb Down") || o.hasAction("Enter");
        }
        return o.hasAction("Climb-up") || o.hasAction("Climb Up") || o.hasAction("Climb");
    }

    private static String pickAction(ITileObject o, boolean toDungeon) {
        if (o == null) {
            return null;
        }
        if (toDungeon) {
            if (o.hasAction("Climb-down")) {
                return "Climb-down";
            }
            if (o.hasAction("Climb Down")) {
                return "Climb Down";
            }
            if (o.hasAction("Enter")) {
                return "Enter";
            }
            return null;
        }
        if (o.hasAction("Climb-up")) {
            return "Climb-up";
        }
        if (o.hasAction("Climb Up")) {
            return "Climb Up";
        }
        if (o.hasAction("Climb")) {
            return "Climb";
        }
        return null;
    }

    private static void clearWait() {
        waitFrom = null;
        waitUntilMs = 0L;
    }
}
