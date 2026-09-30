package net.storm.sdk.entities;

import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITile;
import net.storm.sdk.game.Static;

/**
 * Storm {@code Tiles} — loaded-scene tile lookup.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/entities/package-summary.html">Storm entities</a>
 */
public final class Tiles {

    private Tiles() {
    }

    public static Tile getAt(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return null;
        }
        return getAt(worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane());
    }

    /** Storm-style wrap of the scene tile at {@code worldPoint}, or null. */
    public static ITile get(WorldPoint worldPoint) {
        Tile t = getAt(worldPoint);
        return t != null ? wrap(t) : null;
    }

    public static ITile wrap(Tile tile) {
        return tile != null ? new RlTile(tile) : null;
    }

    public static Tile getAt(int worldX, int worldY, int plane) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getScene() == null) {
                return null;
            }
            WorldPoint wp = new WorldPoint(worldX, worldY, plane);
            LocalPoint lp = LocalPoint.fromWorld(c, wp);
            if (lp == null) {
                return null;
            }
            Tile[][][] tiles = c.getScene().getTiles();
            if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null) {
                return null;
            }
            int sx = lp.getSceneX();
            int sy = lp.getSceneY();
            Tile[] row = tiles[plane][sx];
            if (row == null || sy < 0 || sy >= row.length) {
                return null;
            }
            return row[sy];
        }, null);
    }

    public static Tile getAt(LocalPoint localPoint, int plane) {
        if (localPoint == null) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getScene() == null) {
                return null;
            }
            Tile[][][] tiles = c.getScene().getTiles();
            if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null) {
                return null;
            }
            int sx = localPoint.getSceneX();
            int sy = localPoint.getSceneY();
            Tile[] row = tiles[plane][sx];
            if (row == null || sy < 0 || sy >= row.length) {
                return null;
            }
            return row[sy];
        }, null);
    }

    /** Local player's current tile, or null. */
    public static Tile getOccupied() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null || c.getScene() == null) {
                return null;
            }
            WorldPoint me = c.getLocalPlayer().getWorldLocation();
            if (me == null) {
                return null;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, me);
            if (lp == null) {
                return null;
            }
            Tile[][][] tiles = c.getScene().getTiles();
            int plane = me.getPlane();
            if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null) {
                return null;
            }
            int sx = lp.getSceneX();
            int sy = lp.getSceneY();
            Tile[] row = tiles[plane][sx];
            if (row == null || sy < 0 || sy >= row.length) {
                return null;
            }
            return row[sy];
        }, null);
    }
}
