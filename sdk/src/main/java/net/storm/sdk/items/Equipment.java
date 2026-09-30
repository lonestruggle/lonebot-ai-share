package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.game.Static;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Equipped-item queries (client-thread safe).
 */
public final class Equipment {

    private Equipment() {
    }

    public static boolean contains(String name) {
        if (name == null) {
            return false;
        }
        return getFirst(name) != null;
    }

    public static boolean contains(int itemId) {
        return getFirst(itemId) != null;
    }

    public static boolean contains(String... names) {
        if (names == null || names.length == 0) {
            return false;
        }
        for (String n : names) {
            if (n != null && contains(n)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(int... ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        for (int id : ids) {
            if (contains(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(Predicate<IInventoryItem> filter) {
        if (filter == null) {
            return false;
        }
        for (IInventoryItem i : getAll(filter)) {
            if (i != null) {
                return true;
            }
        }
        return false;
    }

    /** Item in the given equipment slot, or null if empty. */
    public static IInventoryItem get(EquipmentSlot slot) {
        if (slot == null) {
            return null;
        }
        return get(slot.getSlotIdx());
    }

    /** Equipped item at raw container index, or null. */
    public static IInventoryItem get(int slotIdx) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            ItemContainer container = c.getItemContainer(InventoryID.EQUIPMENT);
            if (container == null || container.getItems() == null) {
                return null;
            }
            Item[] items = container.getItems();
            if (slotIdx < 0 || slotIdx >= items.length) {
                return null;
            }
            Item item = items[slotIdx];
            if (item == null || item.getId() < 0) {
                return null;
            }
            return new RlInventoryItem(item, slotIdx);
        }, null);
    }

    /** Storm alias for {@link #get(EquipmentSlot)}. */
    public static IInventoryItem fromSlot(EquipmentSlot slot) {
        return get(slot);
    }

    public static List<IInventoryItem> getAll() {
        return getAll((Predicate<IInventoryItem>) null);
    }

    public static List<IInventoryItem> getAll(Predicate<IInventoryItem> filter) {
        return Static.callOnClientThread(() -> getAllOnClient(filter), new ArrayList<>());
    }

    public static List<IInventoryItem> getAll(int... ids) {
        return getAll(idFilter(ids));
    }

    public static List<IInventoryItem> getAll(String... names) {
        return getAll(nameFilter(names));
    }

    public static boolean containsAll(String... names) {
        if (names == null || names.length == 0) {
            return true;
        }
        for (String n : names) {
            if (n != null && getFirst(n) == null) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsAll(int... ids) {
        if (ids == null || ids.length == 0) {
            return true;
        }
        for (int id : ids) {
            if (getFirst(id) == null) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsAll(Predicate<IInventoryItem> filter) {
        List<IInventoryItem> all = getAll();
        if (all.isEmpty()) {
            return false;
        }
        for (IInventoryItem i : all) {
            if (filter != null && !filter.test(i)) {
                return false;
            }
        }
        return true;
    }

    public static IInventoryItem getLast(Predicate<IInventoryItem> filter) {
        List<IInventoryItem> all = getAll(filter);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    public static IInventoryItem getLast(int... ids) {
        return getLast(i -> {
            for (int id : ids) {
                if (i.getId() == id) {
                    return true;
                }
            }
            return false;
        });
    }

    public static IInventoryItem getLast(String... names) {
        return getLast(i -> {
            if (i.getName() == null) {
                return false;
            }
            for (String n : names) {
                if (n != null && n.equalsIgnoreCase(i.getName())) {
                    return true;
                }
            }
            return false;
        });
    }

    public static IInventoryItem getFirst(String name) {
        if (name == null) {
            return null;
        }
        return getFirst(i -> i.getName() != null && name.equalsIgnoreCase(i.getName()));
    }

    public static IInventoryItem getFirst(int itemId) {
        return getFirst(i -> i.getId() == itemId);
    }

    public static IInventoryItem getFirst(Predicate<IInventoryItem> filter) {
        for (IInventoryItem i : getAll(filter)) {
            return i;
        }
        return null;
    }

    /** Occupied-slot count for name (not stacked qty). */
    public static int getCount(String name) {
        return getCount(false, name);
    }

    public static int getCount(int itemId) {
        return getCount(false, itemId);
    }

    /**
     * @param stacks if true, sum quantities (ammo); if false, count slots
     */
    public static int getCount(boolean stacks, String name) {
        if (name == null) {
            return 0;
        }
        int total = 0;
        int slots = 0;
        for (IInventoryItem i : getAll(i -> i.getName() != null && name.equalsIgnoreCase(i.getName()))) {
            slots++;
            total += Math.max(1, i.getQuantity());
        }
        return stacks ? total : slots;
    }

    public static int getCount(boolean stacks, int itemId) {
        int total = 0;
        int slots = 0;
        for (IInventoryItem i : getAll(i -> i.getId() == itemId)) {
            slots++;
            total += Math.max(1, i.getQuantity());
        }
        return stacks ? total : slots;
    }

    /**
     * @param stacks if true, sum quantities (ammo quiver); if false, count matching slots
     */
    public static int getCount(boolean stacks, Predicate<IInventoryItem> filter) {
        if (filter == null) {
            return 0;
        }
        int total = 0;
        int slots = 0;
        for (IInventoryItem i : getAll(filter)) {
            if (i == null) {
                continue;
            }
            slots++;
            total += Math.max(1, i.getQuantity());
        }
        return stacks ? total : slots;
    }

    /** Display names of non-empty equipment slots. */
    public static List<String> getAllNames() {
        List<String> out = new ArrayList<>();
        for (IInventoryItem i : getAll()) {
            if (i.getName() != null && !i.getName().isEmpty()) {
                out.add(i.getName());
            }
        }
        return out;
    }

    private static List<IInventoryItem> getAllOnClient(Predicate<IInventoryItem> filter) {
        List<IInventoryItem> out = new ArrayList<>();
        Client c = Static.getClient();
        if (c == null) {
            return out;
        }
        ItemContainer container = c.getItemContainer(InventoryID.EQUIPMENT);
        if (container == null || container.getItems() == null) {
            return out;
        }
        Item[] items = container.getItems();
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            if (item == null || item.getId() < 0) {
                continue;
            }
            // Skip non-wearable kit indices that bots rarely query
            if (!isWearableSlot(i)) {
                continue;
            }
            IInventoryItem wrap = new RlInventoryItem(item, i);
            if (filter == null || filter.test(wrap)) {
                out.add(wrap);
            }
        }
        return out;
    }

    private static boolean isWearableSlot(int idx) {
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (s.getSlotIdx() == idx) {
                return true;
            }
        }
        return false;
    }

    private static Predicate<IInventoryItem> nameFilter(String... names) {
        if (names == null || names.length == 0) {
            return null;
        }
        return item -> {
            String n = item.getName();
            if (n == null) {
                return false;
            }
            for (String want : names) {
                if (want != null && want.equalsIgnoreCase(n)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static Predicate<IInventoryItem> idFilter(int... ids) {
        if (ids == null || ids.length == 0) {
            return null;
        }
        return item -> {
            int id = item.getId();
            for (int want : ids) {
                if (want == id) {
                    return true;
                }
            }
            return false;
        };
    }
}
