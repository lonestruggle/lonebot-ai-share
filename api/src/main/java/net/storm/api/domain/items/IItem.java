package net.storm.api.domain.items;

import net.storm.api.domain.Identifiable;
import net.storm.api.domain.Interactable;
import net.storm.api.domain.Nameable;

/**
 * Storm {@code IItem} — any item with id, name, quantity and menu actions
 * (inventory, equipment, bank, trade, ground).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/items/package-summary.html">Storm domain.items</a>
 */
public interface IItem extends Identifiable, Nameable, Interactable {

    @Override
    int getId();

    @Override
    String getName();

    int getQuantity();

    boolean isNoted();
}
