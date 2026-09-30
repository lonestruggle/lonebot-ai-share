package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.items.IItem;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.widgets.Widgets;
import net.storm.sdk.widgets.WidgetText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Storm {@code Trade} — first screen (335) and confirmation screen (334).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/items/Trade.html">Storm Trade</a>
 */
public final class Trade {

    private static final Logger log = LoggerFactory.getLogger(Trade.class);

    public static final int FIRST_SCREEN_GROUP = 335;
    public static final int SECOND_SCREEN_GROUP = 334;

    private Trade() {
    }

    public static boolean isOpen() {
        return isFirstScreenOpen() || isSecondScreenOpen();
    }

    public static boolean isFirstScreenOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget header = safeWidget(c, WidgetInfo.TRADE_WINDOW_HEADER);
            if (visible(header)) {
                return true;
            }
            Widget root = c.getWidget(FIRST_SCREEN_GROUP, 0);
            return visible(root);
        }, false));
    }

    public static boolean isSecondScreenOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget root = c.getWidget(SECOND_SCREEN_GROUP, 0);
            return visible(root);
        }, false));
    }

    public static void accept() {
        if (isSecondScreenOpen()) {
            acceptSecondScreen();
        } else {
            acceptFirstScreen();
        }
    }

    public static void acceptFirstScreen() {
        clickAction(FIRST_SCREEN_GROUP, "Accept");
    }

    public static void acceptSecondScreen() {
        clickAction(SECOND_SCREEN_GROUP, "Accept");
    }

    public static void decline() {
        if (isSecondScreenOpen()) {
            declineSecondScreen();
        } else {
            declineFirstScreen();
        }
    }

    public static void declineFirstScreen() {
        if (!clickAction(FIRST_SCREEN_GROUP, "Decline")) {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        }
    }

    public static void declineSecondScreen() {
        if (!clickAction(SECOND_SCREEN_GROUP, "Decline")) {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        }
    }

    public static boolean hasAccepted(boolean them) {
        if (isSecondScreenOpen()) {
            return hasAcceptedSecondScreen(them);
        }
        return hasAcceptedFirstScreen(them);
    }

    public static boolean hasAcceptedFirstScreen(boolean them) {
        return acceptedText(FIRST_SCREEN_GROUP, them);
    }

    public static boolean hasAcceptedSecondScreen(boolean them) {
        return acceptedText(SECOND_SCREEN_GROUP, them);
    }

    public static void offer(int id, int quantity) {
        offer(id, quantity, false);
    }

    public static void offer(int id, int quantity, boolean quick) {
        offer(i -> i.getId() == id, quantity, quick);
    }

    public static void offer(String name, int quantity) {
        offer(name, quantity, false);
    }

    public static void offer(String name, int quantity, boolean quick) {
        if (name == null) {
            return;
        }
        offer(i -> i.getName() != null && name.equalsIgnoreCase(i.getName()), quantity, quick);
    }

    public static void offer(Predicate<IItem> filter, int quantity) {
        offer(filter, quantity, false);
    }

    public static void offer(Predicate<IItem> filter, int quantity, boolean quick) {
        if (!isFirstScreenOpen() || filter == null) {
            return;
        }
        IInventoryItem inv = Inventory.getFirst(i -> filter.test(i));
        if (inv == null) {
            log.debug("[Trade] offer — no matching inventory item");
            return;
        }
        String action = offerAction(inv, quantity, quick);
        boolean ok = inv.interact(action);
        log.info("[Trade] offer {} qty={} action={} ok={}", inv.getName(), quantity, action, ok);
    }

    public static List<IItem> getAll(boolean theirs) {
        return getAll(theirs, (Predicate<IItem>) null);
    }

    public static List<IItem> getAll(boolean theirs, int... ids) {
        return getAll(theirs, idFilter(ids));
    }

    public static List<IItem> getAll(boolean theirs, String... names) {
        return getAll(theirs, nameFilter(names));
    }

    public static List<IItem> getAll(boolean theirs, Predicate<IItem> filter) {
        return Static.callOnClientThread(() -> {
            List<IItem> out = new ArrayList<>();
            for (IInventoryItem i : containerItems(theirs)) {
                if (filter == null || filter.test(i)) {
                    out.add(i);
                }
            }
            return out;
        }, new ArrayList<>());
    }

    public static List<IItem> getInventory(Predicate<IItem> filter) {
        List<IItem> out = new ArrayList<>();
        for (IInventoryItem i : Inventory.getAll()) {
            if (filter == null || filter.test(i)) {
                out.add(i);
            }
        }
        return out;
    }

    public static IItem getFirst(boolean theirs, Predicate<IItem> filter) {
        List<IItem> all = getAll(theirs, filter);
        return all.isEmpty() ? null : all.get(0);
    }

    public static IItem getFirst(boolean theirs, int... ids) {
        return getFirst(theirs, idFilter(ids));
    }

    public static IItem getFirst(boolean theirs, String... names) {
        return getFirst(theirs, nameFilter(names));
    }

    public static boolean contains(boolean theirs, Predicate<IItem> filter) {
        return getFirst(theirs, filter) != null;
    }

    public static boolean contains(boolean theirs, int... ids) {
        return getFirst(theirs, ids) != null;
    }

    public static boolean contains(boolean theirs, String... names) {
        return getFirst(theirs, names) != null;
    }

    public static String getTradingPlayer() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget header = safeWidget(c, WidgetInfo.TRADE_WINDOW_HEADER);
            if (header != null && header.getText() != null) {
                return parseTrader(header.getText());
            }
            for (int group : new int[]{FIRST_SCREEN_GROUP, SECOND_SCREEN_GROUP}) {
                for (int child = 0; child < 40; child++) {
                    Widget w = c.getWidget(group, child);
                    if (w == null || w.getText() == null) {
                        continue;
                    }
                    String t = w.getText();
                    if (t.toLowerCase(Locale.ROOT).contains("trading with")) {
                        return parseTrader(t);
                    }
                }
            }
            return null;
        }, null);
    }

    private static String parseTrader(String text) {
        String s = WidgetText.strip(text);
        int idx = s.toLowerCase(Locale.ROOT).indexOf("trading with");
        if (idx >= 0) {
            s = s.substring(idx + "trading with".length()).trim();
            if (s.startsWith(":")) {
                s = s.substring(1).trim();
            }
        }
        return s.isEmpty() ? null : s;
    }

    private static List<IInventoryItem> containerItems(boolean theirs) {
        List<IInventoryItem> out = new ArrayList<>();
        Client c = Static.getClient();
        if (c == null) {
            return out;
        }
        InventoryID id = theirs ? InventoryID.TRADEOTHER : InventoryID.TRADE;
        ItemContainer container = c.getItemContainer(id);
        if (container == null || container.getItems() == null) {
            return out;
        }
        Item[] items = container.getItems();
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            if (item == null || item.getId() <= 0) {
                continue;
            }
            out.add(new RlInventoryItem(item, i));
        }
        return out;
    }

    private static String offerAction(IInventoryItem inv, int quantity, boolean quick) {
        if (quick || quantity >= inv.getQuantity()) {
            if (inv.hasAction("Offer-All")) {
                return "Offer-All";
            }
            if (inv.hasAction("Offer All")) {
                return "Offer All";
            }
        }
        if (quantity == 1 && inv.hasAction("Offer-1")) {
            return "Offer-1";
        }
        if (quantity == 5 && inv.hasAction("Offer-5")) {
            return "Offer-5";
        }
        if (quantity == 10 && inv.hasAction("Offer-10")) {
            return "Offer-10";
        }
        if (inv.hasAction("Offer-All")) {
            return "Offer-All";
        }
        return "Offer-1";
    }

    private static boolean clickAction(int group, String action) {
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget hit = findAction(c.getWidget(group, 0), action, 0);
            if (hit == null) {
                for (int child = 0; child < 48; child++) {
                    hit = findAction(c.getWidget(group, child), action, 0);
                    if (hit != null) {
                        break;
                    }
                }
            }
            if (hit == null) {
                log.warn("[Trade] {} button not found on group {}", action, group);
                return false;
            }
            return Widgets.wrap(hit).interact(action);
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    private static Widget findAction(Widget root, String action, int depth) {
        if (root == null || depth > 8) {
            return null;
        }
        String[] actions = root.getActions();
        if (actions != null) {
            for (String a : actions) {
                if (a != null && WidgetText.strip(a).equalsIgnoreCase(action)) {
                    return root;
                }
            }
        }
        String t = root.getText();
        if (t != null && WidgetText.strip(t).equalsIgnoreCase(action)) {
            return root;
        }
        Widget[] kids = root.getDynamicChildren();
        if (kids == null) {
            kids = root.getChildren();
        }
        if (kids != null) {
            for (Widget k : kids) {
                Widget found = findAction(k, action, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean acceptedText(int group, boolean them) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            for (int child = 0; child < 48; child++) {
                Widget w = c.getWidget(group, child);
                if (w == null || w.getText() == null) {
                    continue;
                }
                String t = w.getText().toLowerCase(Locale.ROOT);
                if (them) {
                    if (t.contains("other player has accepted") || t.contains("accepted.")) {
                        return true;
                    }
                } else if (t.contains("waiting for other") || t.contains("you have accepted")) {
                    return true;
                }
            }
            return false;
        }, false));
    }

    private static Predicate<IItem> nameFilter(String... names) {
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

    private static Predicate<IItem> idFilter(int... ids) {
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
