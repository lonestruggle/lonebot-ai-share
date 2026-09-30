package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.mouse.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bank deposit box helpers (Port Sarim Imps dump).
 */
public final class DepositBox {

    private static final Logger log = LoggerFactory.getLogger(DepositBox.class);
    /** Classic deposit box inventory container group (CombatBot Imps). */
    public static final int DEPOSIT_BOX_GROUP = 192;
    /**
     * Bank / deposit-box quantity mode (1 / 5 / 10 / X / All).
     * {@code 0=1, 1=5, 2=10, 3=X, 4=All}.
     */
    public static final int VARBIT_QUANTITY_MODE = 6590;
    public static final int QUANTITY_MODE_ALL = 4;

    /**
     * Deposit-box quantity knoppen (iface 192).
     * AAN = nested sprites 1150–1158, UIT = 1141–1149.
     */
    public enum QtyMode {
        ONE(35, "1"),
        FIVE(36, "5"),
        TEN(37, "10"),
        X(38, "X"),
        ALL(39, "All");

        public final int child;
        public final String label;

        QtyMode(int child, String label) {
            this.child = child;
            this.label = label;
        }
    }

    private DepositBox() {
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget inv = c.getWidget(WidgetInfo.DEPOSIT_BOX_INVENTORY_ITEMS_CONTAINER);
            if (inv != null && !inv.isHidden()) {
                return true;
            }
            try {
                Widget universe = c.getWidget(InterfaceID.BankDepositbox.UNIVERSE);
                return universe != null && !universe.isHidden();
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }

    public static boolean depositAll() {
        if (!isOpen()) {
            return false;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                Widget btn = c.getWidget(InterfaceID.BankDepositbox.DEPOSIT_INV);
                return ClickPoints.forWidgetOnClient(btn);
            } catch (Throwable ignored) {
                return null;
            }
        }, null);
        if (click == null) {
            log.warn("[DepositBox] depositAll — DEPOSIT_INV widget not found");
            return false;
        }
        return MouseManager.interactAt(click, true);
    }

    /**
     * True when deposit-box quantity is already All — skip clicking (click toggles it off).
     */
    public static boolean isQuantityAllSelected() {
        if (!isOpen()) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() ->
                qtyLooksSelected(qtyWidget(Static.getClient(), QtyMode.ALL)), false));
    }

    public static boolean isQuantityModeSelected(QtyMode mode) {
        if (mode == null || !isOpen()) {
            return false;
        }
        QtyMode on = findSelectedQtyMode();
        return on == mode;
    }

    /**
     * AAN-knop heeft nested 9-slice sprites 1150–1158; UIT heeft 1141–1149.
     */
    public static QtyMode findSelectedQtyMode() {
        if (!isOpen()) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            QtyMode hit = null;
            int n = 0;
            for (QtyMode m : QtyMode.values()) {
                if (qtyLooksSelected(qtyWidget(Static.getClient(), m))) {
                    hit = m;
                    n++;
                }
            }
            return n == 1 ? hit : null;
        }, null);
    }

    /** Eén regel: {@code 1=UIT  5=AAN  10=UIT  X=UIT  All=UIT}. */
    public static List<String> dumpQuantityButtons() {
        List<String> lines = new ArrayList<>();
        if (!isOpen()) {
            lines.add("deposit box niet open");
            return lines;
        }
        String row = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            StringBuilder sb = new StringBuilder();
            for (QtyMode m : QtyMode.values()) {
                if (sb.length() > 0) {
                    sb.append("  ");
                }
                Widget w = qtyWidget(c, m);
                if (w == null) {
                    sb.append(m.label).append("=?");
                } else {
                    sb.append(m.label).append(qtyLooksSelected(w) ? "=AAN" : "=UIT");
                }
            }
            return sb.toString();
        }, null);
        if (row != null && !row.isBlank()) {
            lines.add(row);
        }
        return lines;
    }

    /**
     * Klik de quantity-knop alleen als die UIT staat. Staat hij al aan: geen klik.
     *
     * @return korte statusregel voor Test-tab / console
     */
    public static String clickQuantityIfOff(QtyMode want) {
        if (want == null) {
            return "geen knop";
        }
        if (!isOpen()) {
            return "deposit box niet open";
        }
        QtyMode on = findSelectedQtyMode();
        if (on == want) {
            return want.label + " al AAN — geen klik";
        }
        boolean ok = clickQuantityChild(want);
        return ok
                ? "klik " + want.label + (on != null ? " (was " + on.label + ")" : "")
                : "klik " + want.label + " mislukt";
    }

    private static boolean clickQuantityChild(QtyMode mode) {
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget w = qtyWidget(c, mode);
            if (w == null || w.isHidden()) {
                return null;
            }
            return ClickPoints.forWidgetOnClient(w);
        }, null);
        if (click == null) {
            log.info("[DepositBox] qty {} widget 192,{} niet klikbaar", mode.label, mode.child);
            return false;
        }
        log.info("[DepositBox] qty {} click @ {},{}", mode.label, click.getX(), click.getY());
        return MouseManager.interactAt(click, true);
    }

    private static Widget qtyWidget(Client c, QtyMode mode) {
        if (c == null || mode == null) {
            return null;
        }
        try {
            return c.getWidget(DEPOSIT_BOX_GROUP, mode.child);
        } catch (Throwable t) {
            return null;
        }
    }

    /** UIT = sprites 1141–1149, AAN = 1150–1158 (9-slice rand). */
    private static boolean qtyLooksSelected(Widget parent) {
        if (parent == null || parent.isHidden()) {
            return false;
        }
        for (Widget k : nested(parent)) {
            int sp = safeSprite(k);
            if (sp >= 1150 && sp <= 1158) {
                return true;
            }
        }
        return false;
    }

    private static List<Widget> nested(Widget w) {
        List<Widget> out = new ArrayList<>();
        if (w == null) {
            return out;
        }
        addWidgets(out, safeArr(() -> w.getDynamicChildren()));
        addWidgets(out, safeArr(() -> w.getStaticChildren()));
        addWidgets(out, safeArr(() -> w.getChildren()));
        return out;
    }

    private static void addWidgets(List<Widget> out, Widget[] arr) {
        if (arr == null) {
            return;
        }
        for (Widget k : arr) {
            if (k != null && !out.contains(k)) {
                out.add(k);
            }
        }
    }

    private static Widget[] safeArr(java.util.function.Supplier<Widget[]> s) {
        try {
            return s.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static int safeSprite(Widget w) {
        try {
            return w.getSpriteId();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Select quantity mode "All" on the deposit box.
     * Skip als All al aan staat (opnieuw klikken zet All uit).
     * Never clicks {@link InterfaceID.BankDepositbox#DEPOSIT_INV} (that dumps the whole inventory).
     */
    public static boolean selectQuantityAll() {
        if (!isOpen()) {
            return false;
        }
        if (isQuantityAllSelected()) {
            log.info("[DepositBox] selectQuantityAll — al AAN, skip klik");
            return true;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                Widget captured = c.getWidget(DEPOSIT_BOX_GROUP, QtyMode.ALL.child);
                if (captured != null && !captured.isHidden()) {
                    Point p = ClickPoints.forWidgetOnClient(captured);
                    if (p != null) {
                        return p;
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                Widget all = c.getWidget(InterfaceID.BankDepositbox.ALL);
                if (all != null && !all.isHidden()) {
                    Point p = ClickPoints.forWidgetOnClient(all);
                    if (p != null) {
                        return p;
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                Widget qtyRoot = c.getWidget(InterfaceID.BankDepositbox.QUANTITY_BUTTONS);
                if (qtyRoot != null && !qtyRoot.isHidden()) {
                    Widget[] kids = qtyRoot.getDynamicChildren();
                    if (kids == null) {
                        kids = qtyRoot.getChildren();
                    }
                    if (kids != null) {
                        for (Widget w : kids) {
                            if (w == null || w.isHidden()) {
                                continue;
                            }
                            String name = w.getName() != null ? w.getName().toLowerCase(Locale.ROOT) : "";
                            String[] actions = w.getActions();
                            boolean isAll = name.equals("all") || name.contains("quantity: all")
                                    || name.endsWith(": all");
                            if (!isAll && actions != null) {
                                for (String a : actions) {
                                    if (a != null && a.equalsIgnoreCase("All")) {
                                        isAll = true;
                                        break;
                                    }
                                }
                            }
                            // Explicitly reject deposit-inventory / deposit-worn
                            if (name.contains("deposit") && !name.contains("quantity")) {
                                continue;
                            }
                            if (isAll) {
                                Point p = ClickPoints.forWidgetOnClient(w);
                                if (p != null) {
                                    return p;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            // Legacy group 192 — quantity All only (never Deposit inventory)
            for (int child : new int[]{4, 5, 6, 7, 8}) {
                try {
                    Widget w = c.getWidget(DEPOSIT_BOX_GROUP, child);
                    if (w == null || w.isHidden()) {
                        continue;
                    }
                    String name = w.getName() != null ? w.getName().toLowerCase(Locale.ROOT) : "";
                    if (name.contains("deposit") && !name.contains("quantity") && !name.contains("all")) {
                        continue;
                    }
                    if (name.contains("all") || name.contains("quantity")) {
                        String[] actions = w.getActions();
                        boolean ok = name.contains("all");
                        if (actions != null) {
                            for (String a : actions) {
                                if (a != null && a.equalsIgnoreCase("All")) {
                                    ok = true;
                                    break;
                                }
                            }
                        }
                        if (ok) {
                            Point p = ClickPoints.forWidgetOnClient(w);
                            if (p != null) {
                                return p;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            return null;
        }, null);
        if (click == null) {
            log.debug("[DepositBox] selectQuantityAll — All-button not found; skip (do NOT deposit-inv)");
            return false;
        }
        log.info("[DepositBox] selectQuantityAll @ {},{}", click.getX(), click.getY());
        return MouseManager.interactAt(click, true);
    }

    /** Close deposit box (ESC / close widget). */
    public static boolean close() {
        if (!isOpen()) {
            return true;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                Widget universe = c.getWidget(InterfaceID.BankDepositbox.UNIVERSE);
                if (universe != null) {
                    Widget[] children = universe.getDynamicChildren();
                    if (children != null) {
                        for (Widget ch : children) {
                            if (ch == null) {
                                continue;
                            }
                            String[] actions = ch.getActions();
                            if (actions != null) {
                                for (String a : actions) {
                                    if (a != null && a.equalsIgnoreCase("Close")) {
                                        return ClickPoints.forWidgetOnClient(ch);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }, null);
        if (click != null) {
            MouseManager.interactAt(click, true);
            return true;
        }
        // ESC fallback
        net.storm.sdk.input.Keyboard.pressKey(java.awt.event.KeyEvent.VK_ESCAPE);
        return true;
    }

    /** Storm {@code depositInventory()}. */
    public static void depositInventory() {
        depositAll();
    }

    public static void depositEquipment() {
        clickPackedOrNamed("DEPOSIT_WORN", "Deposit worn", "Deposit equipment");
    }

    public static void depositLootingBag() {
        clickPackedOrNamed("DEPOSIT_LOOTING_BAG", "Deposit looting bag", "Looting bag");
    }

    public static void selectQuantityOne() {
        selectQuantityNamed("1", "ONE", -1);
    }

    public static void selectQuantityFive() {
        selectQuantityNamed("5", "FIVE", -1);
    }

    public static void selectQuantityTen() {
        selectQuantityNamed("10", "TEN", -1);
    }

    public static void selectQuantityX() {
        selectQuantityNamed("x", "X", -1);
    }

    private static void clickPackedOrNamed(String field, String... labels) {
        if (!isOpen()) {
            return;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                java.lang.reflect.Field f = InterfaceID.BankDepositbox.class.getField(field);
                int packed = f.getInt(null);
                Widget w = c.getWidget(packed);
                Point p = ClickPoints.forWidgetOnClient(w);
                if (p != null) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
            try {
                Widget universe = c.getWidget(InterfaceID.BankDepositbox.UNIVERSE);
                Point p = findLabelClick(universe, labels);
                if (p != null) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }, null);
        if (click != null) {
            MouseManager.interactAt(click, true);
        } else {
            log.warn("[DepositBox] {} widget not found", field);
        }
    }

    private static void selectQuantityNamed(String label, String fieldHint, int packedHint) {
        if (!isOpen()) {
            return;
        }
        if ("all".equalsIgnoreCase(label)) {
            selectQuantityAll();
            return;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            if (packedHint > 0) {
                try {
                    Widget w = c.getWidget(packedHint);
                    Point p = ClickPoints.forWidgetOnClient(w);
                    if (p != null) {
                        return p;
                    }
                } catch (Throwable ignored) {
                }
            }
            try {
                java.lang.reflect.Field f = InterfaceID.BankDepositbox.class.getField(fieldHint);
                Widget w = c.getWidget(f.getInt(null));
                Point p = ClickPoints.forWidgetOnClient(w);
                if (p != null) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
            try {
                Widget root = c.getWidget(InterfaceID.BankDepositbox.QUANTITY_BUTTONS);
                Point p = findLabelClick(root, label);
                if (p != null) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
            return findLabelClick(c.getWidget(DEPOSIT_BOX_GROUP, 0), label);
        }, null);
        if (click != null) {
            MouseManager.interactAt(click, true);
        }
    }

    private static Point findLabelClick(Widget root, String... labels) {
        if (root == null || labels == null) {
            return null;
        }
        String[] lower = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            lower[i] = labels[i] != null ? labels[i].toLowerCase(Locale.ROOT) : "";
        }
        return findLabelClickRec(root, lower, 0);
    }

    private static Point findLabelClickRec(Widget w, String[] labels, int depth) {
        if (w == null || depth > 8) {
            return null;
        }
        String name = w.getName() != null ? w.getName().toLowerCase(Locale.ROOT) : "";
        String text = w.getText() != null ? w.getText().toLowerCase(Locale.ROOT) : "";
        for (String lab : labels) {
            if (!lab.isEmpty() && (name.contains(lab) || text.equals(lab) || text.contains(lab))) {
                Point p = ClickPoints.forWidgetOnClient(w);
                if (p != null) {
                    return p;
                }
            }
        }
        Widget[] kids = w.getDynamicChildren();
        if (kids == null) {
            kids = w.getChildren();
        }
        if (kids != null) {
            for (Widget k : kids) {
                Point p = findLabelClickRec(k, labels, depth + 1);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Deposit one inventory item by id via deposit-box slot (quantity All).
     * Match op item-id — widget-namen van beads zijn vaak leeg.
     * Nooit inventory-tab openen (dat sluit de deposit box).
     */
    public static boolean depositItemId(int itemId) {
        if (itemId <= 0 || itemId == 6512 || !isOpen()) {
            return false;
        }
        return clickDepositSlot(itemId, null);
    }

    /**
     * Deposit one inventory item by name via deposit-box slot click (quantity All).
     */
    public static boolean depositItemNamed(String itemName) {
        if (itemName == null || itemName.isEmpty() || !isOpen()) {
            return false;
        }
        IInventoryItem inv = Inventory.getFirst(itemName);
        int id = inv != null ? inv.getId() : -1;
        if (id > 0 && clickDepositSlot(id, itemName)) {
            return true;
        }
        return clickDepositSlot(-1, itemName);
    }

    private static boolean clickDepositSlot(int itemId, String itemName) {
        String want = cleanName(itemName);
        Boolean invoked = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget container = findDepositInvContainer(c);
            if (container == null) {
                return false;
            }
            Widget[] slots = container.getDynamicChildren();
            if (slots == null) {
                slots = container.getChildren();
            }
            if (slots == null) {
                return false;
            }
            for (int i = 0; i < slots.length; i++) {
                Widget slot = slots[i];
                if (slot == null || slot.isHidden()) {
                    continue;
                }
                int sid = slot.getItemId();
                if (sid <= 0 || sid == 6512) {
                    continue;
                }
                boolean match = itemId > 0 && sid == itemId;
                if (!match && !want.isEmpty()) {
                    String n = cleanName(slot.getName());
                    if (n.equalsIgnoreCase(want)) {
                        match = true;
                    } else {
                        try {
                            ItemComposition comp = c.getItemDefinition(sid);
                            if (comp != null && comp.getName() != null
                                    && cleanName(comp.getName()).equalsIgnoreCase(want)) {
                                match = true;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                if (!match) {
                    continue;
                }
                String[] actions = slot.getActions();
                String option = "Deposit-All";
                int identifier = 1;
                if (actions != null) {
                    for (int a = 0; a < actions.length; a++) {
                        if (actions[a] != null && actions[a].replaceAll("<[^>]*>", "")
                                .equalsIgnoreCase("Deposit-All")) {
                            option = "Deposit-All";
                            identifier = a + 1;
                            break;
                        }
                    }
                    if (identifier == 1 && actions.length > 0 && actions[0] != null) {
                        option = actions[0].replaceAll("<[^>]*>", "").trim();
                    }
                }
                String target = cleanName(slot.getName());
                if (target.isEmpty()) {
                    try {
                        ItemComposition comp = c.getItemDefinition(sid);
                        if (comp != null && comp.getName() != null) {
                            target = comp.getName();
                        }
                    } catch (Throwable ignored) {
                    }
                }
                int p0 = slot.getIndex();
                if (p0 < 0) {
                    p0 = i;
                }
                MenuAction menu = identifier > 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
                if (MenuInteract.invokeInventory(option, target, sid, identifier,
                        menu.getId(), p0, slot.getId())) {
                    log.info("[DepositBox] deposit invoke itemId={} slot={} op={} id={}",
                            sid, p0, option, identifier);
                    return true;
                }
            }
            return false;
        }, false);
        if (Boolean.TRUE.equals(invoked)) {
            return true;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget container = findDepositInvContainer(c);
            if (container == null) {
                return null;
            }
            Widget[] slots = container.getDynamicChildren();
            if (slots == null) {
                slots = container.getChildren();
            }
            if (slots == null) {
                return null;
            }
            for (Widget slot : slots) {
                if (slot == null || slot.isHidden() || slot.getItemId() <= 0 || slot.getItemId() == 6512) {
                    continue;
                }
                int sid = slot.getItemId();
                if (itemId > 0 && sid == itemId) {
                    return ClickPoints.forWidgetOnClient(slot);
                }
                if (!want.isEmpty()) {
                    String n = cleanName(slot.getName());
                    if (n.equalsIgnoreCase(want)) {
                        return ClickPoints.forWidgetOnClient(slot);
                    }
                }
            }
            return null;
        }, null);
        if (click != null) {
            log.info("[DepositBox] deposit mouse itemId={} name={} @{},{}", itemId, itemName,
                    click.getX(), click.getY());
            return MouseManager.interactAt(click, true);
        }
        // Fallback: Deposit-All op inv-item ZONDER inventory-tab (tab sluit de box)
        IInventoryItem inv = itemId > 0 ? Inventory.getFirst(itemId) : Inventory.getFirst(itemName);
        if (inv != null) {
            try {
                if (inv.hasAction("Deposit-All")) {
                    return inv.interact("Deposit-All");
                }
                if (inv.hasAction("Deposit")) {
                    return inv.interact("Deposit");
                }
                return inv.interact("Deposit-1");
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    private static Widget findDepositInvContainer(Client c) {
        try {
            Widget inv = c.getWidget(WidgetInfo.DEPOSIT_BOX_INVENTORY_ITEMS_CONTAINER);
            if (inv != null && !inv.isHidden()) {
                return inv;
            }
        } catch (Throwable ignored) {
        }
        try {
            Widget root = c.getWidget(DEPOSIT_BOX_GROUP, 0);
            if (root == null) {
                return null;
            }
            return findContainerWithSlots(root);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Widget findContainerWithSlots(Widget root) {
        if (root == null) {
            return null;
        }
        Widget[] dyn = root.getDynamicChildren();
        if (dyn != null && dyn.length >= 28) {
            return root;
        }
        Widget[] ch = root.getChildren();
        if (ch != null) {
            for (Widget w : ch) {
                Widget found = findContainerWithSlots(w);
                if (found != null) {
                    return found;
                }
            }
        }
        if (dyn != null) {
            for (Widget w : dyn) {
                Widget found = findContainerWithSlots(w);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("<[^>]*>", "").trim();
        int col = s.indexOf(':');
        if (col >= 0 && col < s.length() - 1) {
            s = s.substring(col + 1).trim();
        }
        return s;
    }
}
