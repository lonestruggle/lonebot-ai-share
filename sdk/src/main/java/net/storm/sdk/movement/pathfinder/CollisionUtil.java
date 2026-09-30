package net.storm.sdk.movement.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

/**
 * Shared collision walkability for {@link net.storm.sdk.movement.Reachable}.
 */
public final class CollisionUtil {

    private CollisionUtil() {
    }

    public static boolean isWalkable(Client c, WorldPoint wp) {
        return Pathfinder.isWalkableSceneTile(c, wp);
    }
}
