package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.game.Static;
import net.storm.sdk.widgets.Widgets;
import net.storm.sdk.widgets.WidgetText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Storm {@code Shop} — general-store / specialty NPC shops (interface 300/301).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/items/Shop.html">Storm Shop</a>
 */
public final class Shop {

    private static final Logger log = LoggerFactory.getLogger(Shop.class);

    /** Classic OSRS shop stock group. */
    public static final int SHOP_GROUP = 300;
    /** Inventory-while-shop-open group. */
    public static final int SHOP_INVENTORY_GROUP = 301;

    private Shop() {
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget inv = safeWidget(c, WidgetInfo.SHOP_INVENTORY_ITEMS_CONTAINER);
            if (visible(inv)) {
                return true;
            }
            Widget stock = shopStockContainer(c);
            return visible(stock);
        }, false));
    }

    public static List<IWidget> getItemsWidgets() {
        return Static.callOnClientThread(() -> {
            List<IWidget> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null) {
                return out;
            }
            for (Widget w : stockItemWidgets(c)) {
                out.add(Widgets.wrap(w));
            }
            return out;
        }, new ArrayList<>());
    }

    public static IWidget getWidgetForItem(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget w = findStockWidget(c, itemId, null);
            return w != null ? Widgets.wrap(w) : null;
        }, null);
    }

    public static int getStock(int itemId) {
        if (itemId <= 0) {
            return 0;
        }
        Integer n = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            Widget w = findStockWidget(c, itemId, null);
            if (w == null) {
                return 0;
            }
            int qty = w.getItemQuantity();
            return qty > 0 ? qty : 1;
        }, 0);
        return n != null ? n : 0;
    }

    public static int[] getActionQuantities(int itemId) {
        Integer[] boxed = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return new Integer[0];
            }
            Widget w = findStockWidget(c, itemId, null);
            if (w == null || w.getActions() == null) {
                return new Integer[0];
            }
            List<Integer> nums = new ArrayList<>();
            for (String a : w.getActions()) {
                int n = parseQty(a);
                if (n > 0) {
                    nums.add(n);
                }
            }
            return nums.toArray(new Integer[0]);
        }, new Integer[0]);
        if (boxed == null || boxed.length == 0) {
            return new int[0];
        }
        int[] out = new int[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            out[i] = boxed[i] != null ? boxed[i] : 0;
        }
        return out;
    }

    public static int getMaxAction(int itemId) {
        int max = 0;
        for (int n : getActionQuantities(itemId)) {
            if (n > max) {
                max = n;
            }
        }
        return max;
    }

    public static void buyOne(int itemId) {
        buy(itemId, null, 1);
    }

    public static void buyOne(String itemName) {
        buy(-1, itemName, 1);
    }

    public static void buyFive(int itemId) {
        buy(itemId, null, 5);
    }

    public static void buyFive(String itemName) {
        buy(-1, itemName, 5);
    }

    public static void buyTen(int itemId) {
        buy(itemId, null, 10);
    }

    public static void buyTen(String itemName) {
        buy(-1, itemName, 10);
    }

    public static void buyFifty(int itemId) {
        buy(itemId, null, 50);
    }

    public static void buyFifty(String itemName) {
        buy(-1, itemName, 50);
    }

    public static void sellOne(int itemId) {
        sell(itemId, 1);
    }

    public static void sellFive(int itemId) {
        sell(itemId, 5);
    }

    public static void sellTen(int itemId) {
        sell(itemId, 10);
    }

    public static void sellFifty(int itemId) {
        sell(itemId, 50);
    }

    public static List<Integer> getItems() {
        return Static.callOnClientThread(() -> {
            List<Integer> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null) {
                return out;
            }
            for (Widget w : stockItemWidgets(c)) {
                if (w.getItemId() > 0 && !out.contains(w.getItemId())) {
                    out.add(w.getItemId());
                }
            }
            return out;
        }, new ArrayList<>());
    }

    private static void buy(int itemId, String itemName, int qty) {
        if (!isOpen()) {
            log.debug("[Shop] buy skipped — shop not open");
            return;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget w = findStockWidget(c, itemId, itemName);
            if (w == null) {
                log.warn("[Shop] buy {} qty={} — item not in stock", itemName != null ? itemName : itemId, qty);
                return false;
            }
            String action = pickBuyAction(w.getActions(), qty);
            return Widgets.wrap(w).interact(action);
        }, false);
        if (Boolean.TRUE.equals(ok)) {
            log.info("[Shop] buy qty={} id={} name={}", qty, itemId, itemName);
        }
    }

    private static void sell(int itemId, int qty) {
        if (!isOpen() || itemId <= 0) {
            return;
        }
        net.storm.api.domain.items.IInventoryItem inv = Inventory.getFirst(itemId);
        if (inv == null) {
            log.debug("[Shop] sell id={} — not in inventory", itemId);
            return;
        }
        String action = "Sell " + qty;
        if (!inv.hasAction(action)) {
            action = "Sell-" + qty;
        }
        if (!inv.hasAction(action) && qty >= 50) {
            action = inv.hasAction("Sell 50") ? "Sell 50" : "Sell-50";
        }
        boolean ok = inv.interact(action);
        log.info("[Shop] sell id={} qty={} ok={}", itemId, qty, ok);
    }

    private static String pickBuyAction(String[] actions, int qty) {
        String[] candidates = {
                "Buy " + qty,
                "Buy-" + qty,
                "Buy-x",
                "Buy X"
        };
        if (actions != null) {
            for (String want : candidates) {
                for (String a : actions) {
                    if (a != null && WidgetText.strip(a).equalsIgnoreCase(want)) {
                        return WidgetText.strip(a);
                    }
                }
            }
            for (String a : actions) {
                if (a != null && parseQty(a) == qty) {
                    return WidgetText.strip(a);
                }
            }
        }
        return "Buy " + qty;
    }

    private static int parseQty(String action) {
        if (action == null) {
            return -1;
        }
        String s = WidgetText.strip(action).toLowerCase(Locale.ROOT);
        if (!s.contains("buy") && !s.contains("sell")) {
            return -1;
        }
        if (s.contains("50")) {
            return 50;
        }
        if (s.contains("10")) {
            return 10;
        }
        if (s.contains("5") && !s.contains("50")) {
            return 5;
        }
        if (s.contains("1") && !s.contains("10")) {
            return 1;
        }
        return -1;
    }

    private static Widget findStockWidget(Client c, int itemId, String itemName) {
        String want = itemName != null ? WidgetText.strip(itemName) : null;
        for (Widget w : stockItemWidgets(c)) {
            if (itemId > 0 && w.getItemId() == itemId) {
                return w;
            }
            if (want != null && !want.isEmpty()) {
                String n = WidgetText.strip(w.getName());
                if (n.equalsIgnoreCase(want) || n.toLowerCase(Locale.ROOT).contains(want.toLowerCase(Locale.ROOT))) {
                    return w;
                }
                ItemComposition comp = c.getItemDefinition(w.getItemId());
                if (comp != null && comp.getName() != null
                        && WidgetText.strip(comp.getName()).equalsIgnoreCase(want)) {
                    return w;
                }
            }
        }
        return null;
    }

    private static List<Widget> stockItemWidgets(Client c) {
        List<Widget> out = new ArrayList<>();
        Widget container = shopStockContainer(c);
        collectItemSlots(container, out);
        if (out.isEmpty()) {
            Widget root = c.getWidget(SHOP_GROUP, 0);
            collectItemSlots(root, out);
        }
        return out;
    }

    private static Widget shopStockContainer(Client c) {
        for (int child : new int[]{16, 2, 1, 0}) {
            Widget alt = c.getWidget(SHOP_GROUP, child);
            if (visible(alt)) {
                return alt;
            }
        }
        return null;
    }

    private static void collectItemSlots(Widget root, List<Widget> out) {
        if (root == null) {
            return;
        }
        Widget[] dyn = root.getDynamicChildren();
        if (dyn == null || dyn.length == 0) {
            dyn = root.getChildren();
        }
        if (dyn == null) {
            if (root.getItemId() > 0) {
                out.add(root);
            }
            return;
        }
        for (Widget w : dyn) {
            if (w != null && !w.isHidden() && w.getItemId() > 0) {
                out.add(w);
            }
        }
    }

    private static Widget safeWidget(Client c, WidgetInfo info) {
        try {
            return c.getWidget(info);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean visible(Widget w) {
        return w != null && !w.isHidden();
    }
}
