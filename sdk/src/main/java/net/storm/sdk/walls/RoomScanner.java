package net.storm.sdk.walls;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scan walls (collision) and doors around a tile or NPC room — port of CombatBot
 * {@code WallDoorCaptureHelper} without Capture/overlay UI.
 */
public final class RoomScanner {

    /** Safety cap; Chebyshev r=24 is 49×49 = 2401. */
    static final int MAX_ROOM_TILES = 2500;
    private static final int OUTSIDE_TALK_RADIUS = 15;

    private RoomScanner() {
    }

    public static RoomScan scan(Client client, WorldPoint center, int radius) {
        return buildRoomScan(client, center, radius, null, -1, true);
    }

    /** Room around NPC: flood-fill (stops at doors) + boundary walls + talk-from-outside. */
    public static RoomScan scanNearNpc(Client client, WorldPoint npcTile, String npcName, int npcId,
                                        int radius) {
        return buildRoomScan(client, npcTile, radius, npcName, npcId, true);
    }

    /**
     * Open = large plaza / no doors. Enclosed = small room or door in scan.
     */
    public static RoomScan.RoomKind classify(RoomScan scan) {
        if (scan == null) {
            return RoomScan.RoomKind.OPEN;
        }
        if (!scan.doors.isEmpty()) {
            return RoomScan.RoomKind.ENCLOSED;
        }
        int w = 1;
        int h = 1;
        if (scan.bboxMin != null && scan.bboxMax != null) {
            w = scan.bboxMax.getX() - scan.bboxMin.getX() + 1;
            h = scan.bboxMax.getY() - scan.bboxMin.getY() + 1;
        }
        if (w > 11 || h > 11) {
            return RoomScan.RoomKind.OPEN;
        }
        if (scan.wallEdges.size() > 60 && w >= 10) {
            return RoomScan.RoomKind.OPEN;
        }
        return RoomScan.RoomKind.ENCLOSED;
    }

    private static RoomScan buildRoomScan(Client client, WorldPoint center, int radius,
            String npcName, int npcId, boolean stopAtDoors) {
        if (client == null || center == null) {
            return null;
        }
        int r = Math.max(1, radius);
        List<WorldPoint> reachable = floodRoom(client, center, stopAtDoors, r);
        if (reachable.isEmpty()) {
            reachable = List.of(center);
        }
        Set<String> reachSet = new HashSet<>();
        for (WorldPoint p : reachable) {
            reachSet.add(key(p));
        }

        int plane = center.getPlane();
        int minX = center.getX();
        int maxX = center.getX();
        int minY = center.getY();
        int maxY = center.getY();
        for (WorldPoint p : reachable) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }

        List<RoomScan.DoorMark> doors = new ArrayList<>();
        Set<String> doorTiles = new HashSet<>();
        for (WorldPoint p : reachable) {
            collectDoorsNearTile(client, p, doors, doorTiles);
            for (WorldPoint n : neighbors(p)) {
                if (!reachSet.contains(key(n))) {
                    collectDoorsNearTile(client, n, doors, doorTiles);
                }
            }
        }
        doors.sort((a, b) -> Integer.compare(a.tile.distanceTo(center), b.tile.distanceTo(center)));

        List<RoomScan.WallEdgeMark> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();
        for (WorldPoint p : reachable) {
            addBoundaryEdges(client, p, reachSet, edges, edgeKeys);
        }
        List<WorldPoint> wallObjectTiles = collectWallObjectTiles(client, center, r, reachable);
        for (WorldPoint wp : wallObjectTiles) {
            addWallObjectEdges(client, wp, edges, edgeKeys);
        }
        edges.sort((a, b) -> Integer.compare(a.tile.distanceTo(center), b.tile.distanceTo(center)));

        List<WorldPoint> corners = suggestCornersFromRoom(client, reachable);
        WorldPoint bboxMin = new WorldPoint(minX, minY, plane);
        WorldPoint bboxMax = new WorldPoint(maxX, maxY, plane);

        List<WorldPoint> talkOutside = Collections.emptyList();
        if (npcName != null && !npcName.isEmpty()) {
            talkOutside = computeTalkFromOutside(client, center, reachSet);
        }

        return new RoomScan(
                System.currentTimeMillis(), center, radius,
                doors, edges, wallObjectTiles, corners, reachable, talkOutside,
                bboxMin, bboxMax, npcName, npcId);
    }

    static void collectDoorsNearTile(Client client, WorldPoint wp,
            List<RoomScan.DoorMark> doors, Set<String> doorTiles) {
        if (wp == null || !doorTiles.add(key(wp))) {
            return;
        }
        int before = doors.size();
        scanDoorsOnTile(client, wp, doors);
        if (doors.size() == before) {
            doorTiles.remove(key(wp));
        }
    }

    static void addBoundaryEdges(Client client, WorldPoint p, Set<String> reachSet,
            List<RoomScan.WallEdgeMark> edges, Set<String> edgeKeys) {
        WorldPoint north = new WorldPoint(p.getX(), p.getY() + 1, p.getPlane());
        WorldPoint east = new WorldPoint(p.getX() + 1, p.getY(), p.getPlane());
        WorldPoint south = new WorldPoint(p.getX(), p.getY() - 1, p.getPlane());
        WorldPoint west = new WorldPoint(p.getX() - 1, p.getY(), p.getPlane());

        if (!reachSet.contains(key(north)) && !canStep(client, p, north)) {
            addEdgeKey(p, "north", edges, edgeKeys);
        }
        if (!reachSet.contains(key(east)) && !canStep(client, p, east)) {
            addEdgeKey(p, "east", edges, edgeKeys);
        }
        if (!reachSet.contains(key(south)) && !canStep(client, p, south)) {
            addEdgeKey(p, "south", edges, edgeKeys);
        }
        if (!reachSet.contains(key(west)) && !canStep(client, p, west)) {
            addEdgeKey(p, "west", edges, edgeKeys);
        }
    }

    static void addEdgeKey(WorldPoint wp, String edgeName,
            List<RoomScan.WallEdgeMark> edges, Set<String> keys) {
        String k = wp.getX() + "," + wp.getY() + "," + wp.getPlane() + ":" + edgeName;
        if (keys.add(k)) {
            edges.add(new RoomScan.WallEdgeMark(wp, edgeName));
        }
    }

    /**
     * Zelfde bron als Highlight Wall Objects: {@link Tile#getWallObject()} in scan-radius
     * + muren op/naast kamer-tegels (object zit vaak op de geblokkeerde buur).
     */
    static List<WorldPoint> collectWallObjectTiles(Client client, WorldPoint center, int radius,
            List<WorldPoint> reachable) {
        List<WorldPoint> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (client == null || center == null) {
            return out;
        }
        int r = Math.max(1, radius);
        int plane = center.getPlane();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                addIfWallObject(client, new WorldPoint(center.getX() + dx, center.getY() + dy, plane), seen, out);
            }
        }
        Tile[][][] tiles = client.getScene() != null ? client.getScene().getTiles() : null;
        if (tiles != null && plane >= 0 && plane < tiles.length && tiles[plane] != null) {
            for (Tile[] row : tiles[plane]) {
                if (row == null) {
                    continue;
                }
                for (Tile tile : row) {
                    if (tile == null || tile.getWallObject() == null) {
                        continue;
                    }
                    WorldPoint wp = tile.getWorldLocation();
                    if (wp == null || wp.getPlane() != plane || wp.distanceTo(center) > r) {
                        continue;
                    }
                    if (seen.add(key(wp))) {
                        out.add(wp);
                    }
                }
            }
        }
        if (reachable != null) {
            for (WorldPoint p : reachable) {
                addIfWallObject(client, p, seen, out);
                if (p == null) {
                    continue;
                }
                for (WorldPoint n : neighbors(p)) {
                    addIfWallObject(client, n, seen, out);
                }
            }
        }
        return out;
    }

    static void addIfWallObject(Client client, WorldPoint wp, Set<String> seen, List<WorldPoint> out) {
        if (wp == null || seen.contains(key(wp))) {
            return;
        }
        Tile tile = sceneTile(client, wp);
        if (tile == null || tile.getWallObject() == null) {
            return;
        }
        if (seen.add(key(wp))) {
            out.add(wp);
        }
    }

    static void addWallObjectEdges(Client client, WorldPoint wp,
            List<RoomScan.WallEdgeMark> edges, Set<String> edgeKeys) {
        Tile tile = sceneTile(client, wp);
        if (tile == null) {
            return;
        }
        WallObject wall = tile.getWallObject();
        if (wall == null) {
            return;
        }
        addOrientationEdges(wp, wall.getOrientationA(), edges, edgeKeys);
        try {
            addOrientationEdges(wp, wall.getOrientationB(), edges, edgeKeys);
        } catch (Throwable ignored) {
        }
    }

    /** Scene-orientatie: bits 1=W 2=N 4=O 8=Z, of 0–3 rotatie. */
    static void addOrientationEdges(WorldPoint wp, int orientation,
            List<RoomScan.WallEdgeMark> edges, Set<String> edgeKeys) {
        if (wp == null || orientation <= 0) {
            return;
        }
        boolean any = false;
        if ((orientation & 1) != 0) {
            addEdgeKey(wp, "west", edges, edgeKeys);
            any = true;
        }
        if ((orientation & 2) != 0) {
            addEdgeKey(wp, "north", edges, edgeKeys);
            any = true;
        }
        if ((orientation & 4) != 0) {
            addEdgeKey(wp, "east", edges, edgeKeys);
            any = true;
        }
        if ((orientation & 8) != 0) {
            addEdgeKey(wp, "south", edges, edgeKeys);
            any = true;
        }
        if (!any && orientation <= 3) {
            switch (orientation) {
                case 0:
                    addEdgeKey(wp, "north", edges, edgeKeys);
                    break;
                case 1:
                    addEdgeKey(wp, "east", edges, edgeKeys);
                    break;
                case 2:
                    addEdgeKey(wp, "south", edges, edgeKeys);
                    break;
                default:
                    addEdgeKey(wp, "west", edges, edgeKeys);
                    break;
            }
        }
    }

    static List<WorldPoint> suggestCornersFromRoom(Client client, List<WorldPoint> reachable) {
        if (reachable == null || reachable.isEmpty()) {
            return List.of();
        }
        int plane = reachable.get(0).getPlane();
        int minX = reachable.get(0).getX();
        int maxX = minX;
        int minY = reachable.get(0).getY();
        int maxY = minY;
        for (WorldPoint p : reachable) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        List<WorldPoint> corners = new ArrayList<>(4);
        addCornerIfWalkable(client, corners, new WorldPoint(minX, minY, plane));
        addCornerIfWalkable(client, corners, new WorldPoint(minX, maxY, plane));
        addCornerIfWalkable(client, corners, new WorldPoint(maxX, maxY, plane));
        addCornerIfWalkable(client, corners, new WorldPoint(maxX, minY, plane));
        if (corners.size() < 4) {
            snapCornerToWalkable(client, corners, new WorldPoint(minX, minY, plane), 3);
            snapCornerToWalkable(client, corners, new WorldPoint(minX, maxY, plane), 3);
            snapCornerToWalkable(client, corners, new WorldPoint(maxX, maxY, plane), 3);
            snapCornerToWalkable(client, corners, new WorldPoint(maxX, minY, plane), 3);
        }
        return corners;
    }

    /**
     * Flood-fill room, capped to Chebyshev {@code radius} from start.
     * Stops at collision or (optionally) doors.
     */
    static List<WorldPoint> floodRoom(Client client, WorldPoint start, boolean stopAtDoors, int radius) {
        List<WorldPoint> out = new ArrayList<>();
        if (start == null) {
            return out;
        }
        int r = Math.max(1, radius);
        int maxTiles = Math.min(MAX_ROOM_TILES, (2 * r + 1) * (2 * r + 1));
        Set<String> seen = new HashSet<>();
        ArrayDeque<WorldPoint> q = new ArrayDeque<>();
        q.add(start);
        seen.add(key(start));
        while (!q.isEmpty() && out.size() < maxTiles) {
            WorldPoint cur = q.poll();
            out.add(cur);
            if (stopAtDoors && isDoorOrGateTile(client, cur) && !cur.equals(start)) {
                continue;
            }
            for (WorldPoint n : neighbors(cur)) {
                if (seen.contains(key(n))) {
                    continue;
                }
                if (n.distanceTo(start) > r) {
                    continue;
                }
                if (!canStep(client, cur, n)) {
                    continue;
                }
                if (stopAtDoors && isDoorOrGateTile(client, n)) {
                    seen.add(key(n));
                    out.add(n);
                    continue;
                }
                seen.add(key(n));
                q.add(n);
            }
        }
        return out;
    }

    static boolean isDoorOrGateTile(Client client, WorldPoint wp) {
        List<RoomScan.DoorMark> tmp = new ArrayList<>(1);
        scanDoorsOnTile(client, wp, tmp);
        return !tmp.isEmpty();
    }

    /** Tiles outside the room where Talk-to is already allowed (distance ≤15 or LOS). */
    static List<WorldPoint> computeTalkFromOutside(Client client, WorldPoint npcTile,
            Set<String> roomSet) {
        List<WorldPoint> out = new ArrayList<>();
        if (client == null || npcTile == null || roomSet == null) {
            return out;
        }
        int plane = npcTile.getPlane();
        Set<String> seen = new HashSet<>();
        for (int dx = -OUTSIDE_TALK_RADIUS; dx <= OUTSIDE_TALK_RADIUS; dx++) {
            for (int dy = -OUTSIDE_TALK_RADIUS; dy <= OUTSIDE_TALK_RADIUS; dy++) {
                WorldPoint wp = new WorldPoint(npcTile.getX() + dx, npcTile.getY() + dy, plane);
                if (wp.distanceTo(npcTile) > OUTSIDE_TALK_RADIUS) {
                    continue;
                }
                if (roomSet.contains(key(wp))) {
                    continue;
                }
                int flags = collisionFlags(client, wp);
                if (flags < 0 || isFullyBlocked(flags)) {
                    continue;
                }
                if (!seen.add(key(wp))) {
                    continue;
                }
                if (wp.distanceTo(npcTile) <= OUTSIDE_TALK_RADIUS) {
                    out.add(wp);
                }
            }
        }
        out.sort((a, b) -> Integer.compare(a.distanceTo(npcTile), b.distanceTo(npcTile)));
        return out;
    }

    static void addCornerIfWalkable(Client client, List<WorldPoint> corners, WorldPoint p) {
        if (p == null || corners.size() >= 4) {
            return;
        }
        int flags = collisionFlags(client, p);
        if (flags >= 0 && !isFullyBlocked(flags)) {
            corners.add(p);
        }
    }

    static void snapCornerToWalkable(Client client, List<WorldPoint> corners,
            WorldPoint target, int snapRadius) {
        if (target == null || corners.size() >= 4) {
            return;
        }
        for (WorldPoint c : corners) {
            if (c.equals(target)) {
                return;
            }
        }
        for (int r = 0; r <= snapRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) {
                        continue;
                    }
                    WorldPoint p = new WorldPoint(target.getX() + dx, target.getY() + dy, target.getPlane());
                    int flags = collisionFlags(client, p);
                    if (flags >= 0 && !isFullyBlocked(flags)) {
                        corners.add(p);
                        return;
                    }
                }
            }
        }
        corners.add(target);
    }

    static boolean canStep(Client client, WorldPoint from, WorldPoint to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (Math.abs(dx) + Math.abs(dy) != 1) {
            return false;
        }
        int fromFlags = collisionFlags(client, from);
        int toFlags = collisionFlags(client, to);
        if (fromFlags < 0 || toFlags < 0 || isFullyBlocked(fromFlags) || isFullyBlocked(toFlags)) {
            return false;
        }
        if (dy == 1 && ((fromFlags & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0
                || (toFlags & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0)) {
            return false;
        }
        if (dy == -1 && ((fromFlags & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0
                || (toFlags & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0)) {
            return false;
        }
        if (dx == 1 && ((fromFlags & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0
                || (toFlags & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0)) {
            return false;
        }
        if (dx == -1 && ((fromFlags & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0
                || (toFlags & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0)) {
            return false;
        }
        return true;
    }

    static List<WorldPoint> neighbors(WorldPoint p) {
        return List.of(
                new WorldPoint(p.getX() + 1, p.getY(), p.getPlane()),
                new WorldPoint(p.getX() - 1, p.getY(), p.getPlane()),
                new WorldPoint(p.getX(), p.getY() + 1, p.getPlane()),
                new WorldPoint(p.getX(), p.getY() - 1, p.getPlane()));
    }

    static String key(WorldPoint p) {
        return p.getX() + "," + p.getY() + "," + p.getPlane();
    }

    static void scanDoorsOnTile(Client client, WorldPoint wp,
            List<RoomScan.DoorMark> doors) {
        Tile tile = sceneTile(client, wp);
        if (tile == null) {
            return;
        }
        WallObject wall = tile.getWallObject();
        if (wall != null) {
            tryAddDoor(client, wp, wall.getId(), doors);
        }
        net.runelite.api.GameObject[] objects = tile.getGameObjects();
        if (objects != null) {
            for (net.runelite.api.GameObject obj : objects) {
                if (obj != null) {
                    tryAddDoor(client, wp, obj.getId(), doors);
                }
            }
        }
    }

    static void tryAddDoor(Client client, WorldPoint wp, int id,
            List<RoomScan.DoorMark> doors) {
        ObjectComposition comp = client.getObjectDefinition(id);
        if (comp == null) {
            return;
        }
        String name = comp.getName();
        if (name == null || name.isEmpty()) {
            return;
        }
        String low = name.toLowerCase(Locale.ROOT);
        if (!low.contains("door") && !low.contains("gate") && !low.contains("portcullis")) {
            return;
        }
        for (RoomScan.DoorMark existing : doors) {
            if (existing.tile.equals(wp)) {
                return;
            }
        }
        String state = doorState(comp.getActions());
        doors.add(new RoomScan.DoorMark(wp, name, state));
    }

    static String doorState(String[] actions) {
        if (actions == null) {
            return "";
        }
        boolean open = false;
        boolean close = false;
        for (String a : actions) {
            if (a == null) {
                continue;
            }
            if ("Open".equalsIgnoreCase(a)) {
                open = true;
            }
            if ("Close".equalsIgnoreCase(a)) {
                close = true;
            }
        }
        if (open && !close) {
            return "dicht → Open";
        }
        if (close && !open) {
            return "open → Close";
        }
        if (open) {
            return "Open/Close";
        }
        return "";
    }

    static boolean isFullyBlocked(int flags) {
        return (flags & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0;
    }

    static int collisionFlags(Client client, WorldPoint wp) {
        CollisionData[] maps = client.getCollisionMaps();
        if (maps == null || wp.getPlane() < 0 || wp.getPlane() >= maps.length) {
            return -1;
        }
        CollisionData map = maps[wp.getPlane()];
        if (map == null) {
            return -1;
        }
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return -1;
        }
        int[][] flagGrid = map.getFlags();
        if (flagGrid == null) {
            return -1;
        }
        int sx = lp.getSceneX();
        int sy = lp.getSceneY();
        if (sx < 0 || sy < 0 || sx >= flagGrid.length || sy >= flagGrid[sx].length) {
            return -1;
        }
        return flagGrid[sx][sy];
    }

    static Tile sceneTile(Client client, WorldPoint wp) {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null || client.getScene() == null) {
            return null;
        }
        Tile[][][] tiles = client.getScene().getTiles();
        int plane = wp.getPlane();
        if (plane < 0 || plane >= tiles.length) {
            return null;
        }
        int sx = lp.getSceneX();
        int sy = lp.getSceneY();
        if (sx < 0 || sy < 0 || sx >= tiles[plane].length || sy >= tiles[plane][sx].length) {
            return null;
        }
        return tiles[plane][sx][sy];
    }
}
