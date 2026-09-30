package net.storm.sdk.entities;

import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITile;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.game.Static;
import net.storm.sdk.movement.Reachable;
import net.storm.sdk.movement.pathfinder.Pathfinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RlTile implements ITile {

    private final Tile tile;

    RlTile(Tile tile) {
        this.tile = tile;
    }

    @Override
    public Tile unwrap() {
        return tile;
    }

    @Override
    public WorldPoint getWorldLocation() {
        return Static.callOnClientThread(tile::getWorldLocation, null);
    }

    @Override
    public WallObject getWallObject() {
        return Static.callOnClientThread(tile::getWallObject, null);
    }

    @Override
    public GroundObject getGroundObject() {
        return Static.callOnClientThread(tile::getGroundObject, null);
    }

    @Override
    public DecorativeObject getDecorativeObject() {
        return Static.callOnClientThread(tile::getDecorativeObject, null);
    }

    @Override
    public List<ITileObject> getIGameObjects() {
        return Static.callOnClientThread(() -> {
            List<ITileObject> out = new ArrayList<>();
            GameObject[] objs = tile.getGameObjects();
            if (objs == null) {
                return out;
            }
            for (GameObject go : objs) {
                if (go != null) {
                    out.add(new RlTileObject(go));
                }
            }
            return out;
        }, new ArrayList<>());
    }

    @Override
    public List<ITileItem> getIGroundItems() {
        return Static.callOnClientThread(() -> {
            List<ITileItem> out = new ArrayList<>();
            List<TileItem> ground = tile.getGroundItems();
            WorldPoint wp = tile.getWorldLocation();
            if (ground == null) {
                return out;
            }
            for (TileItem ti : ground) {
                if (ti != null && ti.getId() >= 0) {
                    out.add(new RlTileItem(ti, wp));
                }
            }
            return out;
        }, new ArrayList<>());
    }

    @Override
    public List<ITileObject> getTileObjects() {
        return Static.callOnClientThread(() -> {
            List<ITileObject> out = new ArrayList<>();
            if (tile.getWallObject() != null) {
                out.add(new RlTileObject(tile.getWallObject()));
            }
            if (tile.getGroundObject() != null) {
                out.add(new RlTileObject(tile.getGroundObject()));
            }
            if (tile.getDecorativeObject() != null) {
                out.add(new RlTileObject(tile.getDecorativeObject()));
            }
            GameObject[] objs = tile.getGameObjects();
            if (objs != null) {
                for (GameObject go : objs) {
                    if (go != null) {
                        out.add(new RlTileObject(go));
                    }
                }
            }
            return out;
        }, new ArrayList<>());
    }

    @Override
    public ITile getBridge() {
        return Static.callOnClientThread(() -> {
            Tile bridge = tile.getBridge();
            return bridge != null ? new RlTile(bridge) : null;
        }, null);
    }

    @Override
    public boolean isEmpty() {
        return getTileObjects().isEmpty() && getIGroundItems().isEmpty();
    }

    @Override
    public boolean isObstructed() {
        return Reachable.isObstacle(getWorldLocation());
    }

    @Override
    public boolean hasLineOfSightTo(ITile other) {
        if (other == null) {
            return false;
        }
        return Reachable.hasLineOfSight(getWorldLocation(), other.getWorldLocation());
    }

    @Override
    public List<ITile> pathTo(ITile other) {
        if (other == null || other.getWorldLocation() == null || getWorldLocation() == null) {
            return Collections.emptyList();
        }
        List<WorldPoint> path = Pathfinder.findPath(getWorldLocation(), other.getWorldLocation());
        if (path == null || path.isEmpty()) {
            return Collections.emptyList();
        }
        List<ITile> out = new ArrayList<>(path.size());
        for (WorldPoint p : path) {
            ITile t = Tiles.get(p);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }
}
