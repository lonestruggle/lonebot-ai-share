package net.storm.sdk.items;

/**
 * Equipment container slot indices matching {@link net.runelite.api.EquipmentInventorySlot}.
 *
 * <p>Only wearable slots used by bots (skips ARMS/HAIR/JAW kit slots).
 */
public enum EquipmentSlot {
    HEAD(0),
    CAPE(1),
    AMULET(2),
    WEAPON(3),
    BODY(4),
    SHIELD(5),
    LEGS(7),
    GLOVES(9),
    BOOTS(10),
    RING(12),
    AMMO(13);

    private final int slotIdx;

    EquipmentSlot(int slotIdx) {
        this.slotIdx = slotIdx;
    }

    /** Index into {@link net.runelite.api.InventoryID#EQUIPMENT} item array. */
    public int getSlotIdx() {
        return slotIdx;
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
