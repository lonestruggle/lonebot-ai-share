package net.storm.api.widgets;

import net.runelite.api.gameval.InterfaceID;

/**
 * Equipment slots as used by Storm widgets / bank worn panel.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/EquipmentSlot.html">Storm EquipmentSlot</a>
 */
public enum EquipmentSlot {
    HEAD(0, InterfaceID.Wornitems.SLOT0),
    CAPE(1, InterfaceID.Wornitems.SLOT1),
    AMULET(2, InterfaceID.Wornitems.SLOT2),
    WEAPON(3, InterfaceID.Wornitems.SLOT3),
    BODY(4, InterfaceID.Wornitems.SLOT4),
    SHIELD(5, InterfaceID.Wornitems.SLOT5),
    LEGS(7, InterfaceID.Wornitems.SLOT7),
    GLOVES(9, InterfaceID.Wornitems.SLOT9),
    BOOTS(10, InterfaceID.Wornitems.SLOT10),
    RING(12, InterfaceID.Wornitems.SLOT12),
    AMMO(13, InterfaceID.Wornitems.SLOT13);

    private final int slotIdx;
    private final int packedWornWidget;

    EquipmentSlot(int slotIdx, int packedWornWidget) {
        this.slotIdx = slotIdx;
        this.packedWornWidget = packedWornWidget;
    }

    public int getSlotIdx() {
        return slotIdx;
    }

    @Deprecated(forRemoval = true)
    public InterfaceAddress getInterfaceAddress() {
        return InterfaceAddress.fromPackedId(packedWornWidget);
    }

    public static EquipmentSlot fromSlotIndex(int slot) {
        for (EquipmentSlot s : values()) {
            if (s.slotIdx == slot) {
                return s;
            }
        }
        return null;
    }
}
