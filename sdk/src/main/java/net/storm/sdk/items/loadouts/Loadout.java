package net.storm.sdk.items.loadouts;

import java.util.Collections;
import java.util.List;

/**
 * Named equipment + inventory loadout definition.
 *
 * @stub Empty name lists until loadout load/save is wired.
 */
public final class Loadout {

    private final String name;
    private final List<String> equipmentNames;
    private final List<String> inventoryNames;

    public Loadout(String name) {
        this(name, Collections.emptyList(), Collections.emptyList());
    }

    public Loadout(String name, List<String> equipmentNames, List<String> inventoryNames) {
        this.name = name != null ? name : "";
        this.equipmentNames = equipmentNames != null
                ? Collections.unmodifiableList(equipmentNames)
                : Collections.emptyList();
        this.inventoryNames = inventoryNames != null
                ? Collections.unmodifiableList(inventoryNames)
                : Collections.emptyList();
    }

    public String getName() {
        return name;
    }

    /** @stub May be empty. */
    public List<String> getEquipmentNames() {
        return equipmentNames;
    }

    /** @stub May be empty. */
    public List<String> getInventoryNames() {
        return inventoryNames;
    }
}
