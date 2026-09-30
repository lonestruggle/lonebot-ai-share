package net.storm.api.movement;

import net.runelite.api.coords.Direction;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.Locatable;
import net.storm.api.domain.tiles.ITile;

import java.util.List;

/**
 * Storm {@code IReachable} — collision / wall / door / flood-fill.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/movement/IReachable.html">Storm IReachable</a>
 */
public interface IReachable {

    boolean check(int flag, int checkFlag);

    boolean isObstacle(int endFlag);

    boolean isObstacle(WorldPoint worldPoint);

    int getCollisionFlag(WorldPoint point);

    boolean isWalled(Direction direction, int startFlag);

    boolean isWalled(WorldPoint source, WorldPoint destination);

    boolean isWalled(ITile source, ITile destination);

    boolean hasDoor(WorldPoint source, Direction direction);

    boolean hasDoor(ITile source, Direction direction);

    boolean isDoored(ITile source, ITile destination);

    boolean canWalk(Direction direction, int startFlag, int endFlag);

    WorldPoint getNeighbour(Direction direction, WorldPoint source);

    List<WorldPoint> getVisitedTiles(Locatable locatable);

    List<WorldPoint> getVisitedTiles(WorldPoint worldPoint);

    boolean isInteractable(Locatable locatable);

    boolean isWalkable(WorldPoint worldPoint);
}
