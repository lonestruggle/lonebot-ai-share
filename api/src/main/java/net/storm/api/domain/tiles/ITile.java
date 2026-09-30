package net.storm.api.domain.tiles;

import net.runelite.api.DecorativeObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.Locatable;

import java.util.List;

/**
 * Storm {@code ITile} — one scene square (objects, ground items, collision).
 * LoneBot does not extend {@link Tile} (too many RL methods); use {@link #unwrap()}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/tiles/ITile.html">Storm ITile</a>
 */
public interface ITile extends Locatable {

    Tile unwrap();

    @Override
    WorldPoint getWorldLocation();

    WallObject getWallObject();

    GroundObject getGroundObject();

    DecorativeObject getDecorativeObject();

    List<ITileObject> getIGameObjects();

    List<ITileItem> getIGroundItems();

    List<ITileObject> getTileObjects();

    ITile getBridge();

    boolean isEmpty();

    boolean isObstructed();

    boolean hasLineOfSightTo(ITile other);

    List<ITile> pathTo(ITile other);
}
