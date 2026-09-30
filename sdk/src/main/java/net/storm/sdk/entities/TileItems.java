package net.storm.sdk.entities;

import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.api.query.ItemQuery;
import net.storm.sdk.game.Static;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Storm-like ground-item queries. All scene reads go through {@link Static#callOnClientThread}.
 */
public final class TileItems {

    private TileItems() {
    }

    public static ItemQuery<ITileItem> query() {
        return new ItemQuery<>(
                () -> getAll((Predicate<ITileItem>) null),
                ITileItem::getName,
                ITileItem::getId
        );
    }

    public static List<ITileItem> getAll() {
        return getAll((Predicate<ITileItem>) null);
    }

    public static List<ITileItem> getAll(Predicate<ITileItem> filter) {
        return Static.callOnClientThread(() -> getAllOnClient(filter), new ArrayList<>());
    }

    public static List<ITileItem> getAll(String... names) {
        return getAll(nameFilter(names));
    }

    public static List<ITileItem> getAll(int... ids) {
        return getAll(idFilter(ids));
    }

    public static ITileItem getNearest(String... names) {
        return getNearest(nameFilter(names));
    }

    public static ITileItem getNearest(int... ids) {
        return getNearest(idFilter(ids));
    }

    public static ITileItem getNearest(Predicate<ITileItem> filter) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null) {
                return null;
            }
            WorldPoint me = c.getLocalPlayer().getWorldLocation();
            ITileItem best = null;
            int bestDist = Integer.MAX_VALUE;
            for (ITileItem item : getAllOnClient(filter)) {
                if (item.getWorldLocation() == null) {
                    continue;
                }
                int d = me.distanceTo(item.getWorldLocation());
                if (d < bestDist) {
                    bestDist = d;
                    best = item;
                }
            }
            return best;
        }, null);
    }

    public static List<ITileItem> getSurrounding(WorldPoint center, int radius, String... names) {
        Predicate<ITileItem> namesPred = nameFilter(names);
        return getAll(item -> {
            if (center == null || item.getWorldLocation() == null) {
                return false;
            }
            if (center.distanceTo(item.getWorldLocation()) > radius) {
                return false;
            }
            return namesPred == null || namesPred.test(item);
        });
    }

    public static List<ITileItem> getAt(WorldPoint worldPoint, String... names) {
        Predicate<ITileItem> namesPred = nameFilter(names);
        return getAll(item -> {
            if (worldPoint == null || item.getWorldLocation() == null) {
                return false;
            }
            if (!worldPoint.equals(item.getWorldLocation())) {
                return false;
            }
            return namesPred == null || namesPred.test(item);
        });
    }

    private static List<ITileItem> getAllOnClient(Predicate<ITileItem> filter) {
        List<ITileItem> out = new ArrayList<>();
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
                List<TileItem> ground = tile.getGroundItems();
                if (ground == null || ground.isEmpty()) {
                    continue;
                }
                WorldPoint wp = tile.getWorldLocation();
                for (TileItem ti : ground) {
                    if (ti == null || ti.getId() < 0) {
                        continue;
                    }
                    ITileItem wrap = new RlTileItem(ti, wp);
                    if (filter == null || filter.test(wrap)) {
                        out.add(wrap);
                    }
                }
            }
        }
        return out;
    }

    private static Predicate<ITileItem> nameFilter(String... names) {
        if (names == null || names.length == 0) {
            return null;
        }
        return item -> {
            String n = item.getName();
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

    private static Predicate<ITileItem> idFilter(int... ids) {
        if (ids == null || ids.length == 0) {
            return null;
        }
        return item -> {
            int id = item.getId();
            for (int want : ids) {
                if (want == id) {
                    return true;
                }
            }
            return false;
        };
    }
}
