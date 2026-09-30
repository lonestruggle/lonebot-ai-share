package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;

import java.awt.Rectangle;

/**
 * World map muis → {@link WorldPoint} (RuneLite WorldMapOverlay inverse).
 */
final class WorldMapWalkHelper {

    private static final int WORLD_MAP_GROUP = 595;

    private WorldMapWalkHelper() {
    }

    static WorldPoint fromMouse(Client client) {
        if (client == null) {
            return null;
        }
        WorldMap worldMap = client.getWorldMap();
        if (worldMap == null) {
            return null;
        }
        Widget map = mapContainer(client);
        if (map == null || map.isHidden()) {
            return null;
        }
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) {
            return null;
        }
        Rectangle rect = map.getBounds();
        if (rect == null || rect.width < 8 || rect.height < 8) {
            return null;
        }
        if (!rect.contains(mouse.getX(), mouse.getY())) {
            return null;
        }

        float pixelsPerTile = worldMap.getWorldMapZoom();
        if (pixelsPerTile <= 0.01f) {
            return null;
        }
        int widthInTiles = (int) Math.ceil(rect.getWidth() / pixelsPerTile);
        int heightInTiles = (int) Math.ceil(rect.getHeight() / pixelsPerTile);
        Point worldMapPosition = worldMap.getWorldMapPosition();
        if (worldMapPosition == null) {
            return null;
        }

        double xGraphDiff = mouse.getX() - rect.getX();
        double yGraphDiff = mouse.getY() - rect.getY();
        yGraphDiff = rect.height - yGraphDiff;
        xGraphDiff -= pixelsPerTile - Math.ceil(pixelsPerTile / 2);
        yGraphDiff += pixelsPerTile - Math.ceil(pixelsPerTile / 2);
        double xTileOffset = xGraphDiff / pixelsPerTile;
        double yTileOffset = yGraphDiff / pixelsPerTile;
        int worldX = (int) Math.round(xTileOffset - widthInTiles / 2.0 + worldMapPosition.getX());
        int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
        int worldY = (int) Math.round(yTileOffset + yTileMax - 1);

        int plane = 0;
        Player me = client.getLocalPlayer();
        if (me != null && me.getWorldLocation() != null) {
            plane = me.getWorldLocation().getPlane();
        }
        return new WorldPoint(worldX, worldY, plane);
    }

    private static Widget mapContainer(Client client) {
        Widget w = client.getWidget(WORLD_MAP_GROUP, 7);
        if (w != null && !w.isHidden() && w.getWidth() > 20) {
            return w;
        }
        w = client.getWidget(WORLD_MAP_GROUP, 3);
        if (w != null && !w.isHidden() && w.getWidth() > 20) {
            return w;
        }
        return client.getWidget(WORLD_MAP_GROUP, 0);
    }
}
