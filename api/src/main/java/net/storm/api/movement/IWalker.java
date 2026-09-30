package net.storm.api.movement;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.pathfinder.CollisionMap;
import net.storm.api.movement.pathfinder.model.Teleport;
import net.storm.api.movement.pathfinder.model.Transport;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Storm {@code IWalker} — path build, transports, teleports, walk-along.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/movement/IWalker.html">Storm IWalker</a>
 */
public interface IWalker {

    void walk(WorldPoint worldPoint);

    boolean walkTo(WorldArea destination, CollisionMap collisionMap, boolean useTeleports);

    boolean walkTo(WorldArea destination, WalkOptions options);

    TilePath getCurrentPath();

    TilePath getLastPath();

    void setLastPath(TilePath path);

    void setCurrentPath(TilePath path);

    TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, WalkOptions options,
                       HashMap<WorldPoint, Teleport> teleports, Map<WorldPoint, List<Transport>> transports);

    TilePath buildPath(Collection<WorldPoint> startPoints, List<WorldArea> targetAreas, WalkOptions options,
                       HashMap<WorldPoint, Teleport> teleports, Map<WorldPoint, List<Transport>> transports);

    boolean walkAlong(WorldArea destination, TilePath path, Map<WorldPoint, List<Transport>> transports,
                      WalkOptions options);

    Map<WorldPoint, List<Transport>> buildTransportLinks(WalkOptions options);

    @Deprecated
    LinkedHashMap<WorldPoint, Teleport> buildTeleportLinks(WorldArea destination, WalkOptions options);

    LinkedHashMap<WorldPoint, Teleport> buildExperimentalTeleportLinks(WorldArea destination, WalkOptions options);

    HashMap<WorldPoint, Teleport> buildUnfilteredTeleportLinks(WalkOptions options);

    WorldPoint getNearestWalkableTile(WorldPoint source, CollisionMap collisionMap, Predicate<WorldPoint> filter);

    default TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, WalkOptions options) {
        return buildPath(startPoints, destination, options, new HashMap<>(buildExperimentalTeleportLinks(destination, options)),
                buildTransportLinks(options));
    }

    default TilePath buildPath(WorldArea destination, WalkOptions options) {
        return buildPath(Collections.emptyList(), destination, options);
    }

    default TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap,
                               boolean avoidWilderness, boolean useCache, boolean useTransports,
                               HashMap<WorldPoint, Teleport> teleports) {
        WalkOptions options = WalkOptions.builder()
                .collisionMap(collisionMap)
                .avoidWilderness(avoidWilderness)
                .useCache(useCache)
                .useTransports(useTransports)
                .build();
        return buildPath(startPoints, destination, options, teleports, buildTransportLinks(options));
    }

    default TilePath buildPath(WorldArea destination, CollisionMap collisionMap, boolean avoidWilderness) {
        return buildPath(Collections.emptyList(), destination, collisionMap, avoidWilderness, true, true, new HashMap<>());
    }

    default TilePath buildPath(WorldArea destination, CollisionMap collisionMap) {
        return buildPath(destination, collisionMap, true);
    }

    default TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap) {
        return buildPath(startPoints, destination, collisionMap, true, true, true, new HashMap<>());
    }

    default TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap,
                               boolean avoidWilderness) {
        return buildPath(startPoints, destination, collisionMap, avoidWilderness, true, true, new HashMap<>());
    }

    default TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap,
                               boolean avoidWilderness, boolean useCache) {
        return buildPath(startPoints, destination, collisionMap, avoidWilderness, useCache, true, new HashMap<>());
    }

    default TilePath buildPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap,
                               boolean avoidWilderness, boolean useCache, boolean useTransports) {
        return buildPath(startPoints, destination, collisionMap, avoidWilderness, useCache, useTransports, new HashMap<>());
    }

    default Map<WorldPoint, List<Transport>> buildTransportLinks() {
        return buildTransportLinks(WalkOptions.builder().build());
    }

    default LinkedHashMap<WorldPoint, Teleport> buildTeleportLinks(WorldArea destination) {
        return buildExperimentalTeleportLinks(destination, WalkOptions.builder().build());
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

    default TilePath buildPath(Collection<WorldPoint> startPoints, List<WorldArea> targetAreas, WalkOptions options) {
        HashMap<WorldPoint, Teleport> teles = options != null && options.isUseTeleports()
                ? new HashMap<>(buildUnfilteredTeleportLinks(options))
                : new HashMap<>();
        return buildPath(startPoints, targetAreas, options, teles, buildTransportLinks(options));
    }

    default TilePath buildPath(List<WorldArea> targetAreas, WalkOptions options) {
        return buildPath(Collections.emptyList(), targetAreas, options);
    }

    default HashMap<WorldPoint, Teleport> buildUnfilteredTeleportLinks() {
        return buildUnfilteredTeleportLinks(WalkOptions.builder().build());
    }
}
