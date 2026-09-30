package net.storm.sdk.entities;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.query.TileObjectQuery;
import net.storm.sdk.game.Static;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public final class TileObjects {

    private TileObjects() {
    }

    public static TileObjectQuery query() {
        return new TileObjectQuery(
                () -> getAll((Predicate<ITileObject>) null),
                () -> Static.callOnClientThread(() -> {
                    Client c = Static.getClient();
                    if (c == null || c.getLocalPlayer() == null) {
                        return null;
                    }
                    return c.getLocalPlayer().getWorldLocation();
                }, null)
        );
    }

    public static ITileObject getNearest(net.runelite.api.coords.WorldPoint worldPoint, Predicate<ITileObject> filter) {
        if (worldPoint == null) {
            return getNearest(filter);
        }
        return Static.callOnClientThread(() -> {
            ITileObject best = null;
            int bestDist = Integer.MAX_VALUE;
            for (ITileObject obj : getAllOnClient(filter)) {
                if (obj.getWorldLocation() == null) {
                    continue;
                }
                int d = worldPoint.distanceTo(obj.getWorldLocation());
                if (d < bestDist) {
                    bestDist = d;
                    best = obj;
                }
            }
            return best;
        }, null);
    }

    public static ITileObject getNearest(net.runelite.api.coords.WorldPoint worldPoint, String... names) {
        return getNearest(worldPoint, nameFilter(names));
    }

    public static ITileObject getNearest(net.runelite.api.coords.WorldPoint worldPoint, int... ids) {
        return getNearest(worldPoint, idFilter(ids));
    }

    public static ITileObject getNearest(Predicate<ITileObject> filter) {
        return Static.callOnClientThread(() -> getNearestOnClient(filter), null);
    }

    public static ITileObject getNearest(String... names) {
        return getNearest(nameFilter(names));
    }

    public static ITileObject getNearest(int... ids) {
        return getNearest(idFilter(ids));
    }

    /** Unwrap for menu/useOn — null if not an SDK wrap. */
    public static TileObject unwrap(ITileObject obj) {
        if (obj instanceof RlTileObject) {
            return ((RlTileObject) obj).raw();
        }
        return null;
    }

    public static List<ITileObject> getAll() {
        return getAll((Predicate<ITileObject>) null);
    }

    public static List<ITileObject> getAll(String... names) {
        return getAll(nameFilter(names));
    }

    public static List<ITileObject> getAll(int... ids) {
        return getAll(idFilter(ids));
    }

    public static List<ITileObject> getAll(Predicate<ITileObject> filter) {
        return getAll(filter, 1500L);
    }

    public static List<ITileObject> getAll(Predicate<ITileObject> filter, long timeoutMs) {
        return Static.callOnClientThread(() -> getAllOnClient(filter), new ArrayList<>(), timeoutMs);
    }

    /**
     * Alleen objecten op deze world-tegels — geen 104×104 scene-scan met getName/hasAction
     * (dat blokkeert de client-thread 10s+ en maakt Walk-invoke te laat / op de verkeerde tegel).
     */
    public static List<ITileObject> getOnTiles(Collection<WorldPoint> points,
                                               Predicate<ITileObject> filter, long timeoutMs) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<WorldPoint> copy = new ArrayList<>(points);
        return Static.callOnClientThread(() -> {
            List<ITileObject> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null || c.getScene() == null) {
                return out;
            }
            for (WorldPoint wp : copy) {
                collectAtWorld(c, wp, filter, out);
            }
            return out;
        }, new ArrayList<>(), timeoutMs);
    }

    private static ITileObject getNearestOnClient(Predicate<ITileObject> filter) {
        Client c = Static.getClient();
        if (c == null || c.getLocalPlayer() == null || c.getScene() == null) {
            return null;
        }
        ITileObject best = null;
        int bestDist = Integer.MAX_VALUE;
        for (ITileObject obj : getAllOnClient(filter)) {
            if (obj.getWorldLocation() == null) {
                continue;
            }
            int d = c.getLocalPlayer().getWorldLocation().distanceTo(obj.getWorldLocation());
            if (d < bestDist) {
                bestDist = d;
                best = obj;
            }
        }
        return best;
    }

    private static List<ITileObject> getAllOnClient(Predicate<ITileObject> filter) {
        List<ITileObject> out = new ArrayList<>();
        Client c = Static.getClient();
        if (c == null || c.getScene() == null) {
            return out;
        }
        Tile[][][] tiles = c.getScene().getTiles();
        if (tiles == null) {
            return out;
        }
        int z = c.getPlane();
        if (z < 0 || z >= tiles.length || tiles[z] == null) {
            return out;
        }
        for (Tile[] row : tiles[z]) {
            if (row == null) {
                continue;
            }
            for (Tile tile : row) {
                if (tile == null) {
                    continue;
                }
                collect(tile.getGameObjects(), filter, out);
                if (tile.getWallObject() != null) {
                    maybeAdd(tile.getWallObject(), filter, out);
                }
                if (tile.getDecorativeObject() != null) {
                    maybeAdd(tile.getDecorativeObject(), filter, out);
                }
                if (tile.getGroundObject() != null) {
                    maybeAdd(tile.getGroundObject(), filter, out);
                }
            }
        }
        return out;
    }

    private static void collectAtWorld(Client c, WorldPoint wp, Predicate<ITileObject> filter,
                                       List<ITileObject> out) {
        if (c == null || wp == null || wp.getPlane() != c.getPlane()) {
            return;
        }
        LocalPoint lp = LocalPoint.fromWorld(c, wp);
        if (lp == null) {
            return;
        }
        Tile[][][] tiles = c.getScene().getTiles();
        int z = c.getPlane();
        int sx = lp.getSceneX();
        int sy = lp.getSceneY();
        if (tiles == null || z < 0 || z >= tiles.length || tiles[z] == null) {
            return;
        }
        Tile[][] plane = tiles[z];
        if (sx < 0 || sy < 0 || sx >= plane.length) {
            return;
        }
        Tile[] row = plane[sx];
        if (row == null || sy >= row.length) {
            return;
        }
        Tile tile = row[sy];
        if (tile == null) {
            return;
        }
        collect(tile.getGameObjects(), filter, out);
        if (tile.getWallObject() != null) {
            maybeAdd(tile.getWallObject(), filter, out);
        }
        if (tile.getDecorativeObject() != null) {
            maybeAdd(tile.getDecorativeObject(), filter, out);
        }
        if (tile.getGroundObject() != null) {
            maybeAdd(tile.getGroundObject(), filter, out);
        }
    }

    private static void collect(GameObject[] objs, Predicate<ITileObject> filter, List<ITileObject> out) {
        if (objs == null) {
            return;
        }
        for (GameObject go : objs) {
            maybeAdd(go, filter, out);
        }
    }

    private static void maybeAdd(TileObject obj, Predicate<ITileObject> filter, List<ITileObject> out) {
        if (obj == null) {
            return;
        }
        ITileObject wrap = new RlTileObject(obj);
        if (filter == null || filter.test(wrap)) {
            out.add(wrap);
        }
    }

    private static Predicate<ITileObject> nameFilter(String... names) {
        if (names == null || names.length == 0) {
            return null;
        }
        return obj -> {
            String n = obj.getName();
            if (n == null) {
                return false;
            }
            for (String want : names) {
                if (want != null && want.equalsIgnoreCase(n)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static Predicate<ITileObject> idFilter(int... ids) {
        if (ids == null || ids.length == 0) {
            return null;
        }
        return obj -> {
            int id = obj.getId();
            for (int want : ids) {
                if (want == id) {
                    return true;
                }
            }
            return false;
        };
    }
}
