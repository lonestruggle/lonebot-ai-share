package net.storm.api.widgets;

import net.storm.api.domain.widgets.IWidget;

/**
 * Storm {@code BankWornItem}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/BankWornItem.html">Storm BankWornItem</a>
 */
public interface BankWornItem {

    IWidget getWidget();

    int getId();

    int getQuantity();

    EquipmentSlot getSlot();

    void deposit();

    void unequip();
}
