package net.storm.sdk.items;

import net.storm.api.domain.items.IInventoryItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Search for items by name across inventory, equipment, and open bank.
 */
public final class ItemSearch {

    private ItemSearch() {
    }

    /**
     * First match: inventory → equipment → bank (if open).
     */
    public static IInventoryItem findFirst(String name) {
        if (name == null) {
            return null;
        }
        IInventoryItem inv = Inventory.getFirst(name);
        if (inv != null) {
            return inv;
        }
        IInventoryItem eq = Equipment.getFirst(name);
        if (eq != null) {
            return eq;
        }
        if (Bank.isOpen()) {
            return Bank.getFirst(name);
        }
        return null;
    }

    /**
     * All matches across inventory + equipment + bank (if open).
     */
    public static List<IInventoryItem> findAll(String name) {
        List<IInventoryItem> out = new ArrayList<>();
        if (name == null) {
            return out;
        }
        out.addAll(Inventory.getAll(name));
        for (IInventoryItem i : Equipment.getAll()) {
            if (i.getName() != null && name.equalsIgnoreCase(i.getName())) {
                out.add(i);
            }
        }
        if (Bank.isOpen()) {
            out.addAll(Bank.getAll(i -> i.getName() != null && name.equalsIgnoreCase(i.getName())));
        }
        return out;
    }

    public static boolean contains(String name) {
        return findFirst(name) != null;
    }

    public static boolean contains(int itemId) {
        return findFirst(itemId) != null;
    }

    public static IInventoryItem findFirst(int itemId) {
        IInventoryItem inv = Inventory.getFirst(itemId);
        if (inv != null) {
            return inv;
        }
        IInventoryItem eq = Equipment.getFirst(itemId);
        if (eq != null) {
            return eq;
        }
        if (Bank.isOpen()) {
            return Bank.getFirst(itemId);
        }
        return null;
    }

    public static int getCount(int itemId) {
        int total = Inventory.getCount(itemId) + Equipment.getCount(true, itemId);
        if (Bank.isOpen()) {
            total += Bank.getCount(true, itemId);
        }
        return total;
    }

    /** Total stacked quantity across inventory + equipment + bank (if open). */
    public static int getCount(String name) {
        if (name == null) {
            return 0;
        }
        int total = Inventory.getCount(name) + Equipment.getCount(true, name);
        if (Bank.isOpen()) {
            total += Bank.getCount(true, name);
        }
        return total;
    }
}
