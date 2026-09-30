package net.storm.api.widgets;

/**
 * Storm {@code IBankWornItems}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IBankWornItems.html">Storm IBankWornItems</a>
 */
public interface IBankWornItems {

    boolean isOpen();

    void open();

    void close();

    BankWornItem getHead();

    BankWornItem getCape();

    BankWornItem getAmulet();

    BankWornItem getWeapon();

    BankWornItem getBody();

    BankWornItem getShield();

    BankWornItem getLegs();

    BankWornItem getGloves();

    BankWornItem getBoots();

    BankWornItem getRing();

    BankWornItem getAmmo();

    BankWornItem fromSlot(EquipmentSlot slot);
}
