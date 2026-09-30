package net.storm.sdk.widgets;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.widgets.BankWornItem;
import net.storm.api.widgets.EquipmentSlot;
import net.storm.api.widgets.IBankWornItems;
import net.storm.sdk.game.Static;
import net.storm.sdk.items.Equipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Storm {@code BankEquipment} / {@code IBankWornItems} — worn panel while the bank is open.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IBankWornItems.html">Storm IBankWornItems</a>
 */
public final class BankEquipment {

    private static final Logger log = LoggerFactory.getLogger(BankEquipment.class);

    public static final IBankWornItems API = new Api();

    static {
        net.storm.api.Static.bindBankWornItems(API);
    }

    public BankEquipment() {
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            if (!net.storm.sdk.items.Bank.isOpen()) {
                return false;
            }
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget container = c.getWidget(WidgetInfo.BANK_EQUIPMENT_CONTAINER);
            if (container != null && !container.isHidden()) {
                return true;
            }
            Widget worn = c.getWidget(InterfaceID.Bankmain.WORNITEMS_CONTAINER);
            return worn != null && !worn.isHidden();
        }, false));
    }

    public static void open() {
        if (isOpen() || !net.storm.sdk.items.Bank.isOpen()) {
            return;
        }
        IWidget btn = Widgets.get(InterfaceID.Bankmain.WORNITEMS_BUTTON);
        if (btn == null) {
            btn = Widgets.wrap(Static.callOnClientThread(() -> {
                Client c = Static.getClient();
                return c == null ? null : c.getWidget(WidgetInfo.BANK_EQUIPMENT_BUTTON);
            }, null));
        }
        if (btn != null) {
            btn.interact("Show worn items");
            btn.interact("Worn items");
            btn.interact("Equipment");
        }
    }

    public static void close() {
        if (!isOpen()) {
            return;
        }
        IWidget btn = Widgets.get(InterfaceID.Bankmain.WORNITEMS_BUTTON);
        if (btn != null) {
            btn.interact("Hide worn items");
            btn.interact("Close");
        }
    }

    public static IInventoryItem fromSlot(net.storm.sdk.items.EquipmentSlot slot) {
        return Equipment.fromSlot(slot);
    }

    public static BankWornItem fromSlot(EquipmentSlot slot) {
        if (slot == null) {
            return null;
        }
        IInventoryItem item = Equipment.get(slot.getSlotIdx());
        if (item == null) {
            return null;
        }
        return new Worn(slot, item);
    }

    public static boolean contains(String name) {
        return Equipment.contains(name);
    }

    public static boolean contains(int itemId) {
        return Equipment.contains(itemId);
    }

    public static java.util.List<IInventoryItem> getAll() {
        return Equipment.getAll();
    }

    public static boolean unequip(net.storm.sdk.items.EquipmentSlot slot) {
        if (slot == null) {
            return false;
        }
        EquipmentSlot apiSlot = EquipmentSlot.fromSlotIndex(slot.getSlotIdx());
        BankWornItem worn = fromSlot(apiSlot);
        if (worn == null) {
            return true;
        }
        worn.unequip();
        return true;
    }

    public static boolean unequip(EquipmentSlot slot) {
        BankWornItem worn = fromSlot(slot);
        if (worn == null) {
            return true;
        }
        worn.unequip();
        return true;
    }

    private static int packedWornSlot(EquipmentSlot slot) {
        if (slot == null) {
            return -1;
        }
        switch (slot) {
            case HEAD:
                return InterfaceID.Bankmain.WORNSLOT0;
            case CAPE:
                return InterfaceID.Bankmain.WORNSLOT1;
            case AMULET:
                return InterfaceID.Bankmain.WORNSLOT2;
            case WEAPON:
                return InterfaceID.Bankmain.WORNSLOT3;
            case BODY:
                return InterfaceID.Bankmain.WORNSLOT4;
            case SHIELD:
                return InterfaceID.Bankmain.WORNSLOT5;
            case LEGS:
                return InterfaceID.Bankmain.WORNSLOT7;
            case GLOVES:
                return InterfaceID.Bankmain.WORNSLOT9;
            case BOOTS:
                return InterfaceID.Bankmain.WORNSLOT10;
            case RING:
                return InterfaceID.Bankmain.WORNSLOT12;
            case AMMO:
                return InterfaceID.Bankmain.WORNSLOT13;
            default:
                return -1;
        }
    }

    private static final class Worn implements BankWornItem {
        private final EquipmentSlot slot;
        private final IInventoryItem item;

        private Worn(EquipmentSlot slot, IInventoryItem item) {
            this.slot = slot;
            this.item = item;
        }

        @Override
        public IWidget getWidget() {
            return Widgets.get(packedWornSlot(slot));
        }

        @Override
        public int getId() {
            return item.getId();
        }

        @Override
        public int getQuantity() {
            return item.getQuantity();
        }

        @Override
        public EquipmentSlot getSlot() {
            return slot;
        }

        @Override
        public void deposit() {
            IWidget w = getWidget();
            if (w != null && (w.interact("Bank") || w.interact("Deposit"))) {
                return;
            }
            unequip();
            IInventoryItem inv = net.storm.sdk.items.Inventory.getFirst(item.getId());
            if (inv != null) {
                net.storm.sdk.items.Bank.deposit(inv.getId(), inv.getQuantity());
            }
        }

        @Override
        public void unequip() {
            IWidget w = getWidget();
            if (w != null && (w.interact("Remove") || w.interact("Unequip"))) {
                return;
            }
            log.debug("[BankEquipment] unequip {} — worn slot widget not found", slot);
        }
    }

    private static final class Api implements IBankWornItems {
        @Override
        public boolean isOpen() {
            return BankEquipment.isOpen();
        }

        @Override
        public void open() {
            BankEquipment.open();
        }

        @Override
        public void close() {
            BankEquipment.close();
        }

        @Override
        public BankWornItem getHead() {
            return fromSlot(EquipmentSlot.HEAD);
        }

        @Override
        public BankWornItem getCape() {
            return fromSlot(EquipmentSlot.CAPE);
        }

        @Override
        public BankWornItem getAmulet() {
            return fromSlot(EquipmentSlot.AMULET);
        }

        @Override
        public BankWornItem getWeapon() {
            return fromSlot(EquipmentSlot.WEAPON);
        }

        @Override
        public BankWornItem getBody() {
            return fromSlot(EquipmentSlot.BODY);
        }

        @Override
        public BankWornItem getShield() {
            return fromSlot(EquipmentSlot.SHIELD);
        }

        @Override
        public BankWornItem getLegs() {
            return fromSlot(EquipmentSlot.LEGS);
        }

        @Override
        public BankWornItem getGloves() {
            return fromSlot(EquipmentSlot.GLOVES);
        }

        @Override
        public BankWornItem getBoots() {
            return fromSlot(EquipmentSlot.BOOTS);
        }

        @Override
        public BankWornItem getRing() {
            return fromSlot(EquipmentSlot.RING);
        }

        @Override
        public BankWornItem getAmmo() {
            return fromSlot(EquipmentSlot.AMMO);
        }

        @Override
        public BankWornItem fromSlot(EquipmentSlot slot) {
            return BankEquipment.fromSlot(slot);
        }
    }
}
