package net.storm.api.domain.items;

import net.storm.api.domain.actors.IActor;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.domain.widgets.IWidget;

/**
 * Storm {@code IInventoryItem} — inventory slot item with use-on and Wear/Drop/Use.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/items/IInventoryItem.html">Storm IInventoryItem</a>
 */
public interface IInventoryItem extends IItem {

    int getSlot();

    /**
     * Use this item on another inventory item (use-with).
     */
    boolean useOn(IInventoryItem other);

    /**
     * Use this item on a tile object (e.g. log → fire).
     */
    boolean useOn(ITileObject object);

    /**
     * Use this item on an actor (NPC/player), e.g. bones on altar NPC, item on cow.
     */
    boolean useOn(IActor actor);

    /**
     * Use this item on a widget (bank slot, shop slot, GE, …).
     */
    boolean useOn(IWidget widget);

    /**
     * Use this item on a ground item.
     */
    boolean useOn(ITileItem ground);

    /** Storm {@code use()} — select item for use-with. */
    default boolean use() {
        return interact("Use");
    }

    /** Storm {@code drop()} — Drop from inventory. */
    default boolean drop() {
        return interact("Drop");
    }
}
