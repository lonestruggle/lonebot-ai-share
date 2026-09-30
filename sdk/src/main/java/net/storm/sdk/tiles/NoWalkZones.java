package net.storm.sdk.tiles;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.walls.RoomScan;
import net.storm.sdk.walls.RoomScanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vaste no-walk zones. Potato field = Get walls kamer-tegels (flood-fill), geen AABB-vierkant.
 */
public final class NoWalkZones {

    public static final int LB_POTATO_PLANE = 0;

    /** Capture center Get walls in het veld. */
    public static final WorldPoint LB_POTATO_CENTER = new WorldPoint(3256, 3309, 0);
    public static final int LB_POTATO_SCAN_RADIUS = 12;

    /** Snelle reject tot scan klaar is / voor overlap-check. */
    public static final int LB_POTATO_X_MIN = 3241;
    public static final int LB_POTATO_X_MAX = 3266;
    public static final int LB_POTATO_Y_MIN = 3299;
    public static final int LB_POTATO_Y_MAX = 3321;

    private static volatile Set<Long> potatoKeys = Collections.emptySet();
    private static volatile List<WorldPoint> potatoTiles = Collections.emptyList();
    private static volatile int cachedMinX = LB_POTATO_X_MIN;
    private static volatile int cachedMaxX = LB_POTATO_X_MAX;
    private static volatile int cachedMinY = LB_POTATO_Y_MIN;
    private static volatile int cachedMaxY = LB_POTATO_Y_MAX;

    private NoWalkZones() {
    }

    public static boolean isBlocked(WorldPoint p) {
        return isLumbridgePotatoField(p);
    }

    /**
     * Edgeville ruins interior (kist / lever / Death's Domain / trapdoor).
     * Yews staan west in de tuin (~3086–3089) — niet in deze box.
     */
    public static boolean isEdgevilleMausoleum(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        return x >= 3091 && x <= 3098 && y >= 3468 && y <= 3476;
    }

    public static boolean isLumbridgePotatoField(WorldPoint p) {
        if (p == null || p.getPlane() != LB_POTATO_PLANE) {
            return false;
        }
        Set<Long> keys = potatoKeys;
        if (keys.isEmpty()) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        if (x < cachedMinX || x > cachedMaxX || y < cachedMinY || y > cachedMaxY) {
            return false;
        }
        return keys.contains(pack(x, y, p.getPlane()));
    }

    public static List<WorldPoint> potatoFieldTiles() {
        return potatoTiles;
    }

    public static int potatoTileCount() {
        return potatoTiles.size();
    }

    public static boolean hasPotatoCache() {
        return !potatoKeys.isEmpty();
    }

    /**
     * Neem kamer-tegels van Get walls over als dit het potato field is.
     */
    public static boolean applyIfPotatoField(RoomScan scan) {
        if (scan == null || scan.roomTiles == null || scan.roomTiles.isEmpty()) {
            return false;
        }
        if (!looksLikePotatoField(scan)) {
            return false;
        }
        applyRoomTiles(scan.roomTiles);
        return true;
    }

    public static boolean looksLikePotatoField(RoomScan scan) {
        if (scan == null || scan.center == null || scan.center.getPlane() != LB_POTATO_PLANE) {
            return false;
        }
        if (scan.center.distanceTo(LB_POTATO_CENTER) <= 18) {
            return true;
        }
        if (scan.bboxMin == null || scan.bboxMax == null) {
            return false;
        }
        // Overlap met bekende field-bbox
        return scan.bboxMin.getX() <= LB_POTATO_X_MAX
                && scan.bboxMax.getX() >= LB_POTATO_X_MIN
                && scan.bboxMin.getY() <= LB_POTATO_Y_MAX
                && scan.bboxMax.getY() >= LB_POTATO_Y_MIN
                && scan.roomTiles.size() >= 80;
    }

    /** Flood-fill opnieuw vanaf vaste center (zelfde als Get walls). */
    public static boolean refreshPotatoField(Client client) {
        if (client == null) {
            return false;
        }
        RoomScan scan = RoomScanner.scan(client, LB_POTATO_CENTER, LB_POTATO_SCAN_RADIUS);
        if (scan == null || scan.roomTiles.isEmpty()) {
            return false;
        }
        applyRoomTiles(scan.roomTiles);
        return true;
    }

    /** Alleen als speler dicht genoeg is (scene geladen). */
    public static boolean refreshPotatoFieldIfNear(Client client) {
        if (client == null || client.getLocalPlayer() == null) {
            return false;
        }
        WorldPoint me = client.getLocalPlayer().getWorldLocation();
        if (me == null || me.getPlane() != LB_POTATO_PLANE) {
            return false;
        }
        if (me.distanceTo(LB_POTATO_CENTER) > 48) {
            return false;
        }
        return refreshPotatoField(client);
    }

    public static void applyRoomTiles(List<WorldPoint> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            potatoKeys = Collections.emptySet();
            potatoTiles = Collections.emptyList();
            return;
        }
        Set<Long> keys = new HashSet<>(tiles.size() * 2);
        List<WorldPoint> list = new ArrayList<>(tiles.size());
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (WorldPoint p : tiles) {
            if (p == null || p.getPlane() != LB_POTATO_PLANE) {
                continue;
            }
            keys.add(pack(p.getX(), p.getY(), p.getPlane()));
            list.add(p);
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        if (keys.isEmpty()) {
            potatoKeys = Collections.emptySet();
            potatoTiles = Collections.emptyList();
            return;
        }
        cachedMinX = minX;
        cachedMaxX = maxX;
        cachedMinY = minY;
        cachedMaxY = maxY;
        potatoKeys = keys;
        potatoTiles = Collections.unmodifiableList(list);
    }

    public static void clearPotatoCache() {
        potatoKeys = Collections.emptySet();
        potatoTiles = Collections.emptyList();
        cachedMinX = LB_POTATO_X_MIN;
        cachedMaxX = LB_POTATO_X_MAX;
        cachedMinY = LB_POTATO_Y_MIN;
        cachedMaxY = LB_POTATO_Y_MAX;
    }

    private static long pack(int x, int y, int plane) {
        return (((long) plane) << 42) | (((long) x) << 16) | (y & 0xFFFF);
    }
}
