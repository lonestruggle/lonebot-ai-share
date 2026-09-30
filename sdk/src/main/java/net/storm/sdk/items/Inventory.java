package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.query.ItemQuery;
import net.storm.sdk.game.Static;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Storm-like inventory helpers — all container reads via {@link Static#callOnClientThread}.
 */
public final class Inventory {

    private static final int CAPACITY = 28;
    /**
     * Laatst bekende occupied-slots. Client-thread timeout mag niet als “lege inv” gelden
     * (banken dacht dan ten onrechte dat deposit klaar was).
     */
    private static volatile int lastKnownOccupied = -1;

    private Inventory() {
    }

    public static ItemQuery<IInventoryItem> query() {
        return new ItemQuery<>(Inventory::getAll, IInventoryItem::getName, IInventoryItem::getId);
    }

    public static List<IInventoryItem> getAll() {
        return getAll((Predicate<IInventoryItem>) null);
    }

    private static volatile List<IInventoryItem> lastGoodAll = new ArrayList<>();
    private static volatile long lastGoodAllMs;

    public static List<IInventoryItem> getAll(Predicate<IInventoryItem> filter) {
        List<IInventoryItem> all = Static.callOnClientThread(() -> getAllOnClient(null), null);
        if (all == null || all.isEmpty()) {
            int occupied = occupiedSlotsOnClientSafe();
            if (occupied > 0 && !lastGoodAll.isEmpty()
                    && System.currentTimeMillis() - lastGoodAllMs < 8_000L) {
                all = lastGoodAll;
            } else if (all == null) {
                all = new ArrayList<>();
            }
        } else {
            lastGoodAll = all;
            lastGoodAllMs = System.currentTimeMillis();
        }
        if (filter == null) {
            return new ArrayList<>(all);
        }
        List<IInventoryItem> out = new ArrayList<>();
        for (IInventoryItem i : all) {
            if (i != null && filter.test(i)) {
                out.add(i);
            }
        }
        return out;
    }

    public static List<IInventoryItem> getAll(int... ids) {
        return getAll(idFilter(ids));
    }

    public static List<IInventoryItem> getAll(String... names) {
        return getAll(nameFilter(names));
    }

    public static IInventoryItem getFirst(Predicate<IInventoryItem> filter) {
        List<IInventoryItem> all = getAll(filter);
        return all.isEmpty() ? null : all.get(0);
    }

    public static IInventoryItem getFirst(int... ids) {
        return getFirst(idFilter(ids));
    }

    public static IInventoryItem getFirst(String... names) {
        return getFirst(nameFilter(names));
    }

    public static IInventoryItem getLast(Predicate<IInventoryItem> filter) {
        List<IInventoryItem> all = getAll(filter);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    public static IInventoryItem getLast(int... ids) {
        return getLast(idFilter(ids));
    }

    public static IInventoryItem getLast(String... names) {
        return getLast(nameFilter(names));
    }

    public static IInventoryItem get(int slot) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            ItemContainer container = c.getItemContainer(InventoryID.INVENTORY);
            if (container == null || container.getItems() == null) {
                return null;
            }
            Item[] items = container.getItems();
            if (slot < 0 || slot >= items.length) {
                return null;
            }
            Item item = items[slot];
            if (item == null || item.getId() < 0) {
                return null;
            }
            return new RlInventoryItem(item, slot);
        }, null);
    }

    public static boolean contains(String... names) {
        if (names == null || names.length == 0) {
            return false;
        }
        for (String n : names) {
            if (n != null && getFirst(n) != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(int... ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        return getFirst(ids) != null;
    }

    public static boolean contains(Predicate<IInventoryItem> filter) {
        return filter != null && getFirst(filter) != null;
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
        Set<Integer> found = new HashSet<>();
        for (IInventoryItem item : getAll(ids)) {
            found.add(item.getId());
        }
        for (int id : ids) {
            if (!found.contains(id)) {
                return false;
            }
        }
        return true;
    }

    public static int getCount(boolean stacks, Predicate<IInventoryItem> filter) {
        int total = 0;
        int slots = 0;
        for (IInventoryItem i : getAll(filter)) {
            slots++;
            total += Math.max(1, i.getQuantity());
        }
        return stacks ? total : slots;
    }

    public static int getCount(boolean stacks, int... ids) {
        return getCount(stacks, idFilter(ids));
    }

    public static int getCount(boolean stacks, String... names) {
        return getCount(stacks, nameFilter(names));
    }

    public static int getCount(Predicate<IInventoryItem> filter) {
        return getCount(false, filter);
    }

    public static int getCount(String name) {
        return getCount(true, name);
    }

    public static int getCount(int itemId) {
        return getCount(true, itemId);
    }

    /** Number of occupied slots (not stacked quantity). */
    public static int getCount() {
        return occupiedSlotsOnClientSafe();
    }

    public static boolean isEmpty() {
        return getCount() == 0;
    }

    public static boolean isFull() {
        return getFreeSlots() <= 0;
    }

    public static int getFreeSlots() {
        return Math.max(0, CAPACITY - getCount());
    }

    /**
     * Tel alleen de eerste 28 inventory-slots met itemId &gt; 0.
     * Voorkomt vals-“niet vol” als container-read leeg/fallback is of extra lege slots telt.
     */
    private static int occupiedSlotsOnClientSafe() {
        Integer n = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return -1;
            }
            ItemContainer container = c.getItemContainer(InventoryID.INVENTORY);
            if (container == null) {
                return -1;
            }
            Item[] items = container.getItems();
            if (items == null) {
                return -1;
            }
            int occupied = 0;
            int lim = Math.min(CAPACITY, items.length);
            for (int slot = 0; slot < lim; slot++) {
                Item item = items[slot];
                if (item != null && item.getId() > 0) {
                    occupied++;
                }
            }
            return occupied;
        }, -1);
        if (n == null || n < 0) {
            // Timeout / geen client — níet als 0 (leeg) behandelen
            return lastKnownOccupied >= 0 ? lastKnownOccupied : 0;
        }
        lastKnownOccupied = n;
        return n;
    }

    private static List<IInventoryItem> getAllOnClient(Predicate<IInventoryItem> filter) {
        List<IInventoryItem> out = new ArrayList<>();
        Client c = Static.getClient();
        if (c == null) {
            return out;
        }
        ItemContainer container = c.getItemContainer(InventoryID.INVENTORY);
        if (container == null) {
            return out;
        }
        Item[] items = container.getItems();
        if (items == null) {
            return out;
        }
        for (int slot = 0; slot < items.length; slot++) {
            Item item = items[slot];
            if (item == null || item.getId() < 0) {
                continue;
            }
            IInventoryItem wrap = new RlInventoryItem(item, slot);
            if (filter == null || filter.test(wrap)) {
                out.add(wrap);
            }
        }
        return out;
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
