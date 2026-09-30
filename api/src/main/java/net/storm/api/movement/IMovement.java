package net.storm.api.movement;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.pathfinder.CollisionMap;
import net.storm.api.movement.pathfinder.model.Teleport;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Predicate;

/**
 * Storm {@code IMovement} — walk, pathfind, run energy.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/movement/IMovement.html">Storm IMovement</a>
 */
public interface IMovement {

    void setDestination(int sceneX, int sceneY);

    WorldPoint getDestination();

    boolean isWalking();

    void walk(WorldPoint worldPoint);

    boolean walkTo(WorldPoint worldPoint);

    boolean walkTo(WorldArea worldArea);

    boolean walkTo(WorldArea area, CollisionMap collisionMap, boolean useTeleports);

    boolean walkTo(WorldArea area, WalkOptions options);

    boolean isRunEnabled();

    void toggleRun();

    boolean isStaminaBoosted();

    int getRunEnergy();

    TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap,
                     boolean useCache, boolean useTransports, HashMap<WorldPoint, Teleport> teleports);

    TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, WalkOptions options,
                     HashMap<WorldPoint, Teleport> teleports);

    WorldPoint getNearestWalkableTile(WorldPoint source, CollisionMap collisionMap, Predicate<WorldPoint> filter);

    default TilePath getPath(WorldPoint destination) {
        return getPath(destination, null);
    }

    default TilePath getPath(WorldPoint destination, CollisionMap collisionMap) {
        if (destination == null) {
            return TilePath.empty();
        }
        return getPath(Collections.emptyList(), destination.toWorldArea(), collisionMap, true, true, null);
    }

    default TilePath getPath(Collection<WorldPoint> startPoints, WorldPoint destination) {
        return getPath(startPoints, destination, null);
    }

    default TilePath getPath(Collection<WorldPoint> startPoints, WorldPoint destination, CollisionMap collisionMap) {
        if (destination == null) {
            return TilePath.empty();
        }
        return getPath(startPoints, destination.toWorldArea(), collisionMap, true, true, null);
    }

    default TilePath getPath(WorldArea destination) {
        return getPath(destination, null);
    }

    default TilePath getPath(WorldArea destination, CollisionMap collisionMap) {
        return getPath(Collections.emptyList(), destination, collisionMap, true, true, null);
    }

    default TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination) {
        return getPath(startPoints, destination, null, true, true, null);
    }

    default TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap) {
        return getPath(startPoints, destination, collisionMap, true, true, null);
    }

    default TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, boolean useCache) {
        return getPath(startPoints, destination, null, useCache, true, null);
    }

    default TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap,
                             boolean useCache) {
        return getPath(startPoints, destination, collisionMap, useCache, true, null);
    }

    default WorldPoint getNearestWalkableTile(WorldPoint source, Predicate<WorldPoint> filter) {
        return getNearestWalkableTile(source, null, filter);
    }

    default WorldPoint getNearestWalkableTile(WorldPoint source) {
        return getNearestWalkableTile(source, null, null);
    }

    default WorldPoint getNearestWalkableTile(WorldPoint source, CollisionMap collisionMap) {
        return getNearestWalkableTile(source, collisionMap, null);
    }
}
