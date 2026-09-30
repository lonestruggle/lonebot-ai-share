package com.lonebot.example.woodcutter;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;

import java.util.List;

final class WcReach {

    private WcReach() {
    }

    static boolean sameTile(WorldPoint a, WorldPoint b) {
        return a != null && b != null
                && a.getX() == b.getX()
                && a.getY() == b.getY()
                && a.getPlane() == b.getPlane();
    }

    /** Stand-tile N/Z/O/W van object: 0 stappen als je al cardinaal naast staat. */
    static int minWalkStepsToInteract(WorldPoint me, WorldPoint obj) {
        if (me == null || obj == null) {
            return Integer.MAX_VALUE;
        }
        int dx = Math.abs(me.getX() - obj.getX());
        int dy = Math.abs(me.getY() - obj.getY());
        if (dx + dy == 1) {
            return 0;
        }
        int best = Integer.MAX_VALUE;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int sx = obj.getX() + d[0];
            int sy = obj.getY() + d[1];
            int steps = Math.max(Math.abs(me.getX() - sx), Math.abs(me.getY() - sy));
            if (steps < best) {
                best = steps;
            }
        }
        return best;
    }

    static ITileObject pickBestByWalkSteps(List<ITileObject> pool, WorldPoint me, WorldPoint softRecent) {
        if (pool == null || pool.isEmpty() || me == null) {
            return null;
        }
        ITileObject best = null;
        int bestSteps = Integer.MAX_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (ITileObject obj : pool) {
            if (obj == null || obj.getWorldLocation() == null) {
                continue;
            }
            WorldPoint tile = obj.getWorldLocation();
            int steps = minWalkStepsToInteract(me, tile);
            if (softRecent != null && sameTile(tile, softRecent)) {
                steps += 1;
            }
            int dist = me.distanceTo(tile);
            if (steps < bestSteps || (steps == bestSteps && dist < bestDist)) {
                bestSteps = steps;
                bestDist = dist;
                best = obj;
            }
        }
        return best;
    }
}
