package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.game.Static;
import net.storm.sdk.game.Vars;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.widgets.Widgets;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.utils.Sleep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Bank helpers — CombatBot/Storm-stijl: withdraw via menu-invoke ({@code Withdraw-1}/{@code Withdraw-All}).
 * Quantity-knoppen alleen als mouse-fallback; All nooit voor non-stackable gear.
 */
public final class Bank {

    private static final Logger log = LoggerFactory.getLogger(Bank.class);

    /** Bank withdraw quantity buttons. */
    public enum WithdrawMode {
        ONE,
        FIVE,
        TEN,
        X,
        ALL
    }

    /** Laatst succesvol gezette mode — voorkomt All/X spam bij elke withdraw. */
    private static volatile WithdrawMode cachedWithdrawMode;
    /** Na {@link #leaveOpenForWalk()}: bank mag open blijven tot we weglopen. */
    private static volatile boolean leaveOpenPending;
    /** Deposit-All op dezelfde stack niet herhalen tot occupied daalt (stale inv-read). */
    private static volatile String lastDepositAllName;
    private static volatile int lastDepositAllOccupied = -1;
    private static volatile long lastDepositAllMs;
    private static volatile long lastDepositSkipLogMs;
    private static final long DEPOSIT_ALL_GUARD_MS = 1600L;

    private Bank() {
    }

    /**
     * Storm {@code Bank.open()} — open nearest bank (F2P-safe via {@link net.storm.sdk.game.BankHelper}).
     * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/items/Bank.html">Storm Bank</a>
     */
    public static boolean open() {
        if (isOpen()) {
            return true;
        }
        return net.storm.sdk.game.BankHelper.tryOpenFullBank();
    }

    public static boolean isOpen() {
        boolean open = Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null && isOpenOnClient(c);
        }, false));
        if (!open) {
            cachedWithdrawMode = null;
            lastDepositAllName = null;
        }
        return open;
    }

    /**
     * Sluit bank: echte Close-knop (zoals DepositBox) + muis allowUi, daarna ESC met canvas-focus.
     * Widget 12,2 blindly {@code interact("Close")} doen sluit niets.
     * <p>
     * Alleen gebruiken als we nog in de UI moeten (equip, extra withdraw, GE openen).
     * Klaar en daarna alleen lopen → {@link #leaveOpenForWalk()}.
     */
    public static void close() {
        leaveOpenPending = false;
        if (!isOpen()) {
            cachedWithdrawMode = null;
            return;
        }
        if (clickCloseWidget()) {
            cachedWithdrawMode = null;
            return;
        }
        Keyboard.pressEscape();
        try {
            Widgets.closeInterfaces();
        } catch (Throwable ignored) {
        }
        cachedWithdrawMode = null;
    }

    /**
     * Klaar met banken; volgende actie is <b>lopen</b>. Geen Close-knop en geen Escape.
     * In OSRS sluit de bank vanzelf als de speler van de booth af loopt.
     * <p>
     * Canvas-klik zou bankslots raken — {@link net.storm.sdk.movement.WalkClickHelper}
     * gebruikt dan minimap of walk-invoke ({@link #isBlockingCanvasWalk()}).
     */
    public static void leaveOpenForWalk() {
        if (isOpen()) {
            leaveOpenPending = true;
            log.info("[Bank] leaveOpenForWalk — geen Close/Esc");
            net.storm.sdk.bot.BotRuntime.logConsole("[Bank] leave-walk (geen Close/Esc)");
        }
        cachedWithdrawMode = null;
    }

    /**
     * True zolang de bank open mag blijven omdat de volgende actie lopen is.
     * Valt vanzelf weg als de UI sluit (weglopen).
     */
    public static boolean isLeaveOpenForWalk() {
        if (!isOpen()) {
            leaveOpenPending = false;
            return false;
        }
        return leaveOpenPending;
    }

    /** Nieuwe bank-sessie (deposit/withdraw) — Close/leave-walk-flag resetten. */
    public static void clearLeaveOpenForWalk() {
        leaveOpenPending = false;
    }

    /**
     * Bank-UI is open: een canvas-walk klikt in de bank, niet op de wereld.
     */
    public static boolean isBlockingCanvasWalk() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null && isOpenOnClient(c);
        }, false, 80L));
    }

    /** @return true als de Close-knop is geklikt (muis, UI toegestaan) */
    public static boolean clickCloseWidget() {
        Point click = Static.callOnClientThread(Bank::findCloseClickOnClient, null);
        if (click == null) {
            return false;
        }
        return MouseManager.interactAt(click, true);
    }

    private static Point findCloseClickOnClient() {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        int[] packed = {
                InterfaceID.Bankmain.UNIVERSE,
                InterfaceID.Bankmain.FRAME,
                InterfaceID.Bankmain.TITLE
        };
        for (int id : packed) {
            Point p = findCloseIn(c.getWidget(id), 0);
            if (p != null) {
                return p;
            }
        }
        for (int child = 0; child <= 20; child++) {
            Point p = findCloseIn(c.getWidget(12, child), 0);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private static Point findCloseIn(Widget w, int depth) {
        if (w == null || depth > 5) {
            return null;
        }
        try {
            if (!w.isHidden()) {
                String[] actions = w.getActions();
                if (actions != null) {
                    for (String a : actions) {
                        if (a != null && a.equalsIgnoreCase("Close")) {
                            return ClickPoints.forWidgetOnClient(w);
                        }
                    }
                }
            }
            Widget[][] groups = {w.getChildren(), w.getDynamicChildren(), w.getNestedChildren()};
            for (Widget[] kids : groups) {
                if (kids == null) {
                    continue;
                }
                for (Widget k : kids) {
                    Point p = findCloseIn(k, depth + 1);
                    if (p != null) {
                        return p;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean contains(String name) {
        return getFirst(name) != null;
    }

    public static boolean contains(int itemId) {
        return getFirst(itemId) != null;
    }

    public static List<IInventoryItem> getAll() {
        return getAll((Predicate<IInventoryItem>) null);
    }

    public static List<IInventoryItem> getAll(Predicate<IInventoryItem> filter) {
        return Static.callOnClientThread(() -> getAllOnClient(filter), new ArrayList<>());
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

    /** Stacked quantity of matching name. */
    public static int getCount(String name) {
        return getCount(true, name);
    }

    public static int getCount(int itemId) {
        return getCount(true, itemId);
    }

    /**
     * @param stacks if true, sum quantities; if false, count occupied slots
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
     * Click the bank quantity-mode button for {@code mode} if needed.
     * Skip click when deze mode al actief is in deze bank-sessie (anti All-spam).
     */
    public static boolean setWithdrawMode(WithdrawMode mode) {
        if (mode == null || !isOpen()) {
            return false;
        }
        if (cachedWithdrawMode == mode) {
            return true;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget btn = quantityWidgetOnClient(c, mode);
            return ClickPoints.forWidgetOnClient(btn);
        }, null);
        if (click == null) {
            log.warn("[Bank] setWithdrawMode — quantity widget missing for {}", mode);
            return false;
        }
        boolean ok = MouseManager.interactAt(click);
        if (ok) {
            cachedWithdrawMode = mode;
            Sleep.sleep(80, 160);
        }
        return ok;
    }

    /**
     * Withdraw — CombatBot/Storm-stijl: <b>menu-invoke</b> op bank-item
     * ({@code Withdraw-1}/{@code Withdraw-All}/{@code Withdraw-X}), géén quantity-knoppen.
     * <ul>
     *   <li>Niet-stackable (gear): altijd {@code Withdraw-1} — nooit All</li>
     *   <li>Stackable: All alleen bij {@code MAX_VALUE} of amount ≥ bankstack</li>
     *   <li>Anders 5/10/X via menu-optie + Enter amount</li>
     * </ul>
     */
    public static boolean withdraw(String name, int amount) {
        if (name == null || amount <= 0 || !isOpen()) {
            return false;
        }
        IInventoryItem item = getFirst(name);
        if (item == null) {
            log.warn("[Bank] withdraw — item not in bank: {}", name);
            return false;
        }
        return withdrawItem(item, amount);
    }

    public static boolean withdraw(int itemId, int amount) {
        if (amount <= 0 || !isOpen()) {
            return false;
        }
        IInventoryItem item = getFirst(itemId);
        if (item == null) {
            log.warn("[Bank] withdraw — item id not in bank: {}", itemId);
            return false;
        }
        return withdrawItem(item, amount);
    }

    private static boolean withdrawItem(IInventoryItem item, int amount) {
        if (item == null || amount <= 0) {
            return false;
        }
        int bankCount = Math.max(1, item.getQuantity());
        // getCount may be more accurate for stacks by name
        try {
            String n = item.getName();
            if (n != null && !n.isEmpty()) {
                int c = getCount(true, n);
                if (c > 0) {
                    bankCount = c;
                }
            }
        } catch (Throwable ignored) {
        }

        boolean stackable = isStackable(item.getId()) || bankCount > 1;
        String option;
        int enterAmount = 0;

        if (!stackable) {
            // Gear / single piece — nooit All
            option = "Withdraw-1";
            amount = 1;
        } else if (amount == Integer.MAX_VALUE || amount >= bankCount || amount > 10) {
            option = "Withdraw-All";
        } else if (amount == 1) {
            option = "Withdraw-1";
        } else if (amount == 5) {
            option = "Withdraw-5";
        } else if (amount == 10) {
            option = "Withdraw-10";
        } else {
            option = "Withdraw-All";
        }

        BankWithdrawHelper.setLastRequestedWithdrawAmount(enterAmount > 0 ? enterAmount : amount);
        log.info("[Bank] withdraw invoke {} ×{} (bank={}) stackable={} opt={}",
                item.getName(), amount, bankCount, stackable, option);

        if (invokeBankWithdrawOption(item, option)) {
            if (enterAmount > 0) {
                // Wacht tot Enter-amount prompt zichtbaar is (UI-lag)
                long deadline = System.currentTimeMillis() + 1200L;
                while (System.currentTimeMillis() < deadline
                        && !BankWithdrawHelper.isEnterAmountPromptVisible()) {
                    Sleep.sleep(40, 80);
                }
                if (BankWithdrawHelper.isEnterAmountPromptVisible()) {
                    BankWithdrawHelper.completeEnterAmountPrompt(enterAmount);
                } else {
                    log.warn("[Bank] Withdraw-X: Enter-amount prompt niet zichtbaar na invoke");
                }
            }
            return true;
        }

        // Fallback: oude muis-pad (alleen stackables mogen All-mode knop)
        log.warn("[Bank] invoke {} faalde — mouse fallback", option);
        return withdrawMouseFallback(item, amount, stackable, enterAmount);
    }

    /** True als item stackable is (runes, coins, arrows, …). */
    public static boolean isStackable(int itemId) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || itemId <= 0) {
                return false;
            }
            ItemComposition comp = c.getItemDefinition(itemId);
            return comp != null && comp.isStackable();
        }, false));
    }

    /**
     * Menu-invoke op bank-slot: {@code Withdraw-1} / {@code Withdraw-All} / …
     * Zelfde patroon als inventory {@link RlInventoryItem#interact} (CC_OP).
     */
    public static boolean invokeBankWithdrawOption(IInventoryItem item, String option) {
        if (item == null || option == null || !isOpen()) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(
                () -> invokeBankWithdrawOnClient(item.getId(), item.getSlot(), item.getName(), option),
                false));
    }

    private static boolean invokeBankWithdrawOnClient(int itemId, int slot, String name, String option) {
        Client c = Static.getClient();
        if (c == null) {
            return false;
        }
        Widget slotWidget = findBankSlotWidgetOnClient(c, itemId, slot, name);
        if (slotWidget == null) {
            log.debug("[Bank] invoke — slot widget missing id={} slot={} name={}", itemId, slot, name);
            return false;
        }
        String[] actions = slotWidget.getActions();
        int idx = indexOfAction(actions, option);
        if (idx < 0) {
            // Fallback vaste volgorde OSRS bank
            idx = defaultWithdrawActionIndex(option);
            if (idx < 0) {
                log.debug("[Bank] invoke — geen actie '{}' actions={}", option,
                        actions != null ? java.util.Arrays.toString(actions) : "null");
                return false;
            }
        }
        int identifier = idx + 1;
        MenuAction menu = identifier > 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
        int packed = bankItemContainerPackedId(c);
        String opt = (actions != null && idx < actions.length && actions[idx] != null)
                ? stripCol(actions[idx]) : option;
        String target = name != null ? name : "";
        try {
            c.menuAction(slotWidget.getIndex() >= 0 ? slotWidget.getIndex() : slot,
                    packed, menu, identifier, itemId, opt, target);
            log.info("[Bank] invoke {} → {} id={} slot={}", opt, target, itemId, slot);
            net.storm.sdk.bot.BotRuntime.logConsole("[Bank] " + opt + " " + target);
            return true;
        } catch (Throwable t) {
            return MenuInteract.invokeInventory(opt, target, itemId, identifier,
                    menu.getId(), slotWidget.getIndex() >= 0 ? slotWidget.getIndex() : slot, packed);
        }
    }

    private static int bankItemContainerPackedId(Client c) {
        try {
            Widget w = c.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
            if (w != null) {
                return w.getId();
            }
        } catch (Throwable ignored) {
        }
        return WidgetInfo.BANK_ITEM_CONTAINER.getId();
    }

    private static Widget findBankSlotWidgetOnClient(Client c, int itemId, int slot, String name) {
        Widget container = c.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        if (container == null || container.isHidden()) {
            return null;
        }
        Widget[] children = container.getDynamicChildren();
        if (children == null || children.length == 0) {
            children = container.getChildren();
        }
        if (children == null) {
            return null;
        }
        for (Widget child : children) {
            if (child == null || child.isHidden() || child.getItemId() <= 0) {
                continue;
            }
            if (child.getItemId() == itemId) {
                return child;
            }
        }
        if (name != null) {
            for (Widget child : children) {
                if (child == null || child.isHidden() || child.getItemId() <= 0) {
                    continue;
                }
                ItemComposition comp = c.getItemDefinition(child.getItemId());
                if (comp != null && comp.getName() != null && name.equalsIgnoreCase(comp.getName())) {
                    return child;
                }
            }
        }
        if (slot >= 0 && slot < children.length && children[slot] != null
                && children[slot].getItemId() == itemId) {
            return children[slot];
        }
        return null;
    }

    private static int defaultWithdrawActionIndex(String option) {
        if (option == null) {
            return -1;
        }
        // Typische bank-order: Withdraw-1, Withdraw-5, Withdraw-10, Withdraw-X, Withdraw-All
        switch (option) {
            case "Withdraw-1":
                return 0;
            case "Withdraw-5":
                return 1;
            case "Withdraw-10":
                return 2;
            case "Withdraw-X":
                return 3;
            case "Withdraw-All":
                return 4;
            default:
                return -1;
        }
    }

    private static int indexOfAction(String[] actions, String option) {
        if (actions == null || option == null) {
            return -1;
        }
        String want = stripCol(option);
        // Exact match eerst (voorkomt Withdraw-All-but-1 i.p.v. Withdraw-All)
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && stripCol(actions[i]).equalsIgnoreCase(want)) {
                return i;
            }
        }
        // Fuzzy: "Withdraw-All" matcht start, maar niet -but-1
        if (want.equalsIgnoreCase("Withdraw-All")) {
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] == null) {
                    continue;
                }
                String a = stripCol(actions[i]);
                if (a.equalsIgnoreCase("Withdraw-All")
                        || (a.toLowerCase(Locale.ROOT).startsWith("withdraw-all")
                        && !a.toLowerCase(Locale.ROOT).contains("but"))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String stripCol(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<col=[^>]*>", "").replace("</col>", "").trim();
    }

    /** Legacy mouse fallback — All-knop alleen voor stackables. */
    private static boolean withdrawMouseFallback(IInventoryItem item, int amount, boolean stackable,
                                                 int enterAmount) {
        String name = item.getName();
        int itemId = item.getId();
        WithdrawMode mode;
        if (!stackable || amount == 1) {
            mode = WithdrawMode.ONE;
        } else if (amount == Integer.MAX_VALUE) {
            mode = WithdrawMode.ALL;
        } else if (amount == 5) {
            mode = WithdrawMode.FIVE;
        } else if (amount == 10) {
            mode = WithdrawMode.TEN;
        } else if (name != null && amount >= getCount(true, name)) {
            mode = WithdrawMode.ALL;
        } else {
            mode = WithdrawMode.X;
            if (enterAmount <= 0) {
                enterAmount = amount;
            }
        }
        // Gear: nooit All-quantity knop
        if (!stackable) {
            mode = WithdrawMode.ONE;
        }
        if (!setWithdrawMode(mode)) {
            log.debug("[Bank] mouse fallback setWithdrawMode({}) failed", mode);
        }
        Point click = name != null ? findBankItemClickPoint(name) : findBankItemClickPoint(itemId);
        if (click == null) {
            BankWithdrawHelper.setLastRequestedWithdrawAmount(0);
            return false;
        }
        if (!MouseManager.interactAt(click)) {
            BankWithdrawHelper.setLastRequestedWithdrawAmount(0);
            return false;
        }
        if (mode == WithdrawMode.X && enterAmount > 0) {
            Sleep.sleep(120, 220);
            if (BankWithdrawHelper.isEnterAmountPromptVisible()) {
                BankWithdrawHelper.completeEnterAmountPrompt(enterAmount);
            }
        }
        return true;
    }

    /** Withdraw all of the named stack. */
    public static boolean withdrawAll(String name) {
        return withdraw(name, Integer.MAX_VALUE);
    }

    public static boolean withdrawAll(int itemId) {
        return withdraw(itemId, Integer.MAX_VALUE);
    }

    /**
     * Deposit from bank-inventory via menu invoke (Deposit-1 / Deposit-All), Storm-stijl.
     * {@code amount == Integer.MAX_VALUE} → Deposit-All.
     */
    public static boolean deposit(String name, int amount) {
        if (name == null || !isOpen() || amount <= 0) {
            return false;
        }
        IInventoryItem inv = net.storm.sdk.items.Inventory.getFirst(name);
        if (inv == null) {
            log.warn("[Bank] deposit — niet in inventory: {}", name);
            return false;
        }
        return depositInventoryItem(inv, amount);
    }

    public static boolean deposit(int itemId, int amount) {
        if (!isOpen() || amount <= 0) {
            return false;
        }
        IInventoryItem inv = net.storm.sdk.items.Inventory.getFirst(itemId);
        if (inv == null) {
            log.warn("[Bank] deposit — niet in inventory id={}", itemId);
            return false;
        }
        return depositInventoryItem(inv, amount);
    }

    private static boolean depositInventoryItem(IInventoryItem item, int amount) {
        if (item == null) {
            return false;
        }
        boolean depositAll = amount == Integer.MAX_VALUE || amount >= Math.max(1, item.getQuantity());
        if (depositAll && shouldSkipDuplicateDepositAll(item)) {
            return false;
        }
        String option;
        if (amount == 1) {
            option = "Deposit-1";
        } else if (amount == 5) {
            option = "Deposit-5";
        } else if (amount == 10) {
            option = "Deposit-10";
        } else if (depositAll) {
            option = "Deposit-All";
        } else {
            option = "Deposit-X";
        }
        log.info("[Bank] deposit invoke {} ×{} opt={}", item.getName(), amount, option);
        int occupiedBefore = occupiedNow();
        if (invokeBankInventoryDepositOption(item, option)) {
            if (depositAll) {
                markDepositAll(item, occupiedBefore);
            }
            if ("Deposit-X".equals(option)) {
                long deadline = System.currentTimeMillis() + 1200L;
                while (System.currentTimeMillis() < deadline
                        && !BankWithdrawHelper.isEnterAmountPromptVisible()) {
                    Sleep.sleep(40, 80);
                }
                if (BankWithdrawHelper.isEnterAmountPromptVisible()) {
                    BankWithdrawHelper.completeEnterAmountPrompt(amount);
                }
            }
            return true;
        }
        Point click = Static.callOnClientThread(
                () -> findBankInventoryClickPointByName(item.getName()), null);
        if (click == null) {
            return false;
        }
        boolean clicked = MouseManager.interactAt(click);
        if (clicked && depositAll) {
            markDepositAll(item, occupiedBefore);
        }
        return clicked;
    }

    private static boolean shouldSkipDuplicateDepositAll(IInventoryItem item) {
        if (item == null || lastDepositAllName == null || item.getName() == null) {
            return false;
        }
        if (!lastDepositAllName.equalsIgnoreCase(item.getName())) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastDepositAllMs > DEPOSIT_ALL_GUARD_MS) {
            lastDepositAllName = null;
            return false;
        }
        int occ = occupiedNow();
        if (lastDepositAllOccupied >= 0 && occ >= 0 && occ < lastDepositAllOccupied) {
            lastDepositAllName = null;
            return false;
        }
        if (now - lastDepositSkipLogMs >= 1500L) {
            lastDepositSkipLogMs = now;
            net.storm.sdk.bot.BotRuntime.logConsole(
                    "[Bank] skip Deposit-All " + item.getName() + " — wacht inv-update");
        }
        return true;
    }

    private static void markDepositAll(IInventoryItem item, int occupiedBefore) {
        if (item == null || item.getName() == null) {
            return;
        }
        lastDepositAllName = item.getName();
        lastDepositAllOccupied = occupiedBefore;
        lastDepositAllMs = System.currentTimeMillis();
    }

    private static int occupiedNow() {
        try {
            return net.storm.sdk.items.Inventory.getCount();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean invokeBankInventoryDepositOption(IInventoryItem item, String option) {
        if (item == null || option == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || !isOpenOnClient(c)) {
                return false;
            }
            int itemId = item.getId();
            int slot = item.getSlot();
            String name = item.getName();
            Widget slotWidget = findBankInventorySlotWidgetOnClient(c, itemId, slot, name);
            if (slotWidget == null) {
                return false;
            }
            String[] actions = slotWidget.getActions();
            int idx = indexOfAction(actions, option);
            if (idx < 0) {
                idx = defaultDepositActionIndex(option);
            }
            if (idx < 0) {
                return false;
            }
            int identifier = idx + 1;
            MenuAction menu = identifier > 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
            int packed = bankInventoryContainerPackedId(c);
            String opt = (actions != null && idx < actions.length && actions[idx] != null)
                    ? stripCol(actions[idx]) : option;
            String target = name != null ? name : "";
            try {
                c.menuAction(slotWidget.getIndex() >= 0 ? slotWidget.getIndex() : slot,
                        packed, menu, identifier, itemId, opt, target);
                net.storm.sdk.bot.BotRuntime.logConsole("[Bank] " + opt + " " + target);
                return true;
            } catch (Throwable t) {
                return MenuInteract.invokeInventory(opt, target, itemId, identifier,
                        menu.getId(), slotWidget.getIndex() >= 0 ? slotWidget.getIndex() : slot, packed);
            }
        }, false));
    }

    private static Widget findBankInventorySlotWidgetOnClient(Client c, int itemId, int slot, String name) {
        Widget container = c.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
        if (container == null || container.isHidden()) {
            return null;
        }
        Widget[] children = container.getDynamicChildren();
        if (children == null || children.length == 0) {
            children = container.getChildren();
        }
        if (children == null) {
            return null;
        }
        for (Widget child : children) {
            if (child == null || child.isHidden() || child.getItemId() <= 0) {
                continue;
            }
            if (child.getItemId() == itemId) {
                return child;
            }
        }
        if (name != null) {
            for (Widget child : children) {
                if (child == null || child.isHidden() || child.getItemId() <= 0) {
                    continue;
                }
                ItemComposition comp = c.getItemDefinition(child.getItemId());
                if (comp != null && comp.getName() != null && name.equalsIgnoreCase(comp.getName())) {
                    return child;
                }
            }
        }
        if (slot >= 0 && slot < children.length && children[slot] != null
                && children[slot].getItemId() == itemId) {
            return children[slot];
        }
        return null;
    }

    private static int bankInventoryContainerPackedId(Client c) {
        try {
            Widget w = c.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
            if (w != null) {
                return w.getId();
            }
        } catch (Throwable ignored) {
        }
        return WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId();
    }

    private static int defaultDepositActionIndex(String option) {
        if (option == null) {
            return -1;
        }
        switch (option) {
            case "Deposit-1":
                return 0;
            case "Deposit-5":
                return 1;
            case "Deposit-10":
                return 2;
            case "Deposit-X":
                return 3;
            case "Deposit-All":
                return 4;
            default:
                return -1;
        }
    }

    /**
     * Deposit entire inventory via {@link WidgetInfo#BANK_DEPOSIT_INVENTORY} button.
     * Storm alias: {@code depositInventory()}.
     */
    public static boolean depositAll() {
        if (!isOpen()) {
            return false;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget btn = c.getWidget(WidgetInfo.BANK_DEPOSIT_INVENTORY);
            return ClickPoints.forWidgetOnClient(btn);
        }, null);
        if (click == null) {
            log.warn("[Bank] depositAll — BANK_DEPOSIT_INVENTORY widget not found");
            return false;
        }
        return MouseManager.interactAt(click);
    }

    /** Storm {@code depositInventory()}. */
    public static boolean depositInventory() {
        return depositAll();
    }

    /** Deposit all of one inventory item by name (bank open). */
    public static boolean depositAll(String name) {
        if (name == null || !isOpen()) {
            return false;
        }
        return deposit(name, Integer.MAX_VALUE);
    }

    /**
     * Storm {@code depositAllExcept(String...)} — stort alles behalve keep-namen.
     * Eén stack per aanroep (loop-vriendelijk); {@code true} als er iets gestort is of niets meer te storten.
     */
    public static boolean depositAllExcept(String... keepNames) {
        if (!isOpen()) {
            return false;
        }
        java.util.Set<String> keep = new java.util.HashSet<>();
        if (keepNames != null) {
            for (String k : keepNames) {
                if (k != null && !k.isBlank()) {
                    keep.add(k.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        java.util.List<IInventoryItem> all = net.storm.sdk.items.Inventory.getAll();
        if (all == null || all.isEmpty()) {
            int occ = net.storm.sdk.items.Inventory.getCount();
            // Lege getAll + occupied > 0 = flaky read — niet “klaar”
            return occ <= 0;
        }
        IInventoryItem first = null;
        for (IInventoryItem item : all) {
            if (item == null || item.getName() == null || item.getName().isEmpty()) {
                continue;
            }
            if (keep.contains(item.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            first = item;
            break;
        }
        if (first == null) {
            return true; // alleen keep-items (of leeg) — klaar
        }
        return deposit(first.getName(), Integer.MAX_VALUE);
    }

    /** Storm overload — keep by item id. */
    public static boolean depositAllExcept(int... keepIds) {
        if (!isOpen()) {
            return false;
        }
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        if (keepIds != null) {
            for (int id : keepIds) {
                if (id > 0) {
                    keep.add(id);
                }
            }
        }
        java.util.List<IInventoryItem> all = net.storm.sdk.items.Inventory.getAll();
        if (all == null || all.isEmpty()) {
            int occ = net.storm.sdk.items.Inventory.getCount();
            return occ <= 0;
        }
        IInventoryItem first = null;
        for (IInventoryItem item : all) {
            if (item == null || keep.contains(item.getId())) {
                continue;
            }
            first = item;
            break;
        }
        if (first == null) {
            return true;
        }
        return deposit(first.getId(), Integer.MAX_VALUE);
    }

    /** Storm {@code depositEquipment()} — bank worn-items dump button. */
    public static boolean depositEquipment() {
        if (!isOpen()) {
            return false;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                java.lang.reflect.Field f = InterfaceID.Bankmain.class.getField("DEPOSITWORN");
                Widget btn = c.getWidget(f.getInt(null));
                Point p = ClickPoints.forWidgetOnClient(btn);
                if (p != null) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Field f = WidgetInfo.class.getField("BANK_DEPOSIT_EQUIPMENT");
                Object info = f.get(null);
                java.lang.reflect.Method getId = info.getClass().getMethod("getId");
                Widget btn = c.getWidget((Integer) getId.invoke(info));
                return ClickPoints.forWidgetOnClient(btn);
            } catch (Throwable ignored) {
            }
            return null;
        }, null);
        if (click == null) {
            log.warn("[Bank] depositEquipment — button not found");
            return false;
        }
        return MouseManager.interactAt(click);
    }

    /**
     * Storm noted/unnoted withdraw toggle.
     * Varbit {@code 3958} is BANK_NOTE on most clients; widget fallback if missing.
     */
    public static boolean isNoted() {
        return Vars.getVarbit(3958) == 1;
    }

    public static boolean setNoted(boolean noted) {
        if (!isOpen()) {
            return false;
        }
        if (isNoted() == noted) {
            return true;
        }
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                java.lang.reflect.Field f = InterfaceID.Bankmain.class.getField(noted ? "NOTE" : "ITEM");
                Widget w = c.getWidget(f.getInt(null));
                Point p = ClickPoints.forWidgetOnClient(w);
                if (p != null) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Field f = WidgetInfo.class.getField("BANK_NOTE_BUTTON");
                Object info = f.get(null);
                java.lang.reflect.Method getPacked = info.getClass().getMethod("getPackedId");
                Widget w = c.getWidget((Integer) getPacked.invoke(info));
                return ClickPoints.forWidgetOnClient(w);
            } catch (Throwable ignored) {
            }
            return null;
        }, null);
        if (click == null) {
            log.warn("[Bank] setNoted({}) — toggle widget missing", noted);
            return false;
        }
        return MouseManager.interactAt(click);
    }

    public static List<IInventoryItem> getAll(int... ids) {
        return getAll(i -> {
            if (ids == null) {
                return false;
            }
            for (int id : ids) {
                if (i.getId() == id) {
                    return true;
                }
            }
            return false;
        });
    }

    public static List<IInventoryItem> getAll(String... names) {
        return getAll(i -> {
            if (names == null || i.getName() == null) {
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

    public static boolean containsAll(String... names) {
        if (names == null) {
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
        if (ids == null) {
            return true;
        }
        for (int id : ids) {
            if (getFirst(id) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Storm nested {@code Bank.Inventory} — inventory panel while the bank is open.
     */
    public static final class Inventory {
        private Inventory() {
        }

        public static boolean contains(String... names) {
            return net.storm.sdk.items.Inventory.contains(names);
        }

        public static boolean contains(int... ids) {
            return net.storm.sdk.items.Inventory.contains(ids);
        }

        public static IInventoryItem getFirst(String... names) {
            return net.storm.sdk.items.Inventory.getFirst(names);
        }

        public static IInventoryItem getFirst(int... ids) {
            return net.storm.sdk.items.Inventory.getFirst(ids);
        }

        public static List<IInventoryItem> getAll() {
            return net.storm.sdk.items.Inventory.getAll();
        }

        public static boolean deposit(String name, int amount) {
            return Bank.deposit(name, amount);
        }
    }

    /** Canvas click point for a bank item by name (client-thread safe). */
    public static Point findBankItemClickPoint(String name) {
        if (name == null) {
            return null;
        }
        return Static.callOnClientThread(() -> findBankItemClickPointByName(name), null);
    }

    /** Canvas click point for a bank item by id (client-thread safe). */
    public static Point findBankItemClickPoint(int itemId) {
        return Static.callOnClientThread(() -> findBankItemClickPointById(itemId), null);
    }

    private static Widget quantityWidgetOnClient(Client c, WithdrawMode mode) {
        int packed;
        switch (mode) {
            case ONE:
                packed = InterfaceID.Bankmain.QUANTITY1;
                break;
            case FIVE:
                packed = InterfaceID.Bankmain.QUANTITY5;
                break;
            case TEN:
                packed = InterfaceID.Bankmain.QUANTITY10;
                break;
            case X:
                packed = InterfaceID.Bankmain.QUANTITYX;
                break;
            case ALL:
                packed = InterfaceID.Bankmain.QUANTITYALL;
                break;
            default:
                return null;
        }
        try {
            Widget w = c.getWidget(packed);
            if (w != null && !w.isHidden()) {
                return w;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Point findBankItemClickPointByName(String name) {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        Widget container = c.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        return findChildItemClick(c, container, name, -1);
    }

    private static Point findBankItemClickPointById(int itemId) {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        Widget container = c.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
        return findChildItemClick(c, container, null, itemId);
    }

    private static Point findBankInventoryClickPointByName(String name) {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        Widget container = c.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
        return findChildItemClick(c, container, name, -1);
    }

    private static Point findBankInventoryClickPointById(int itemId) {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        Widget container = c.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
        return findChildItemClick(c, container, null, itemId);
    }

    private static Point findChildItemClick(Client c, Widget container, String name, int itemId) {
        if (container == null || container.isHidden()) {
            return null;
        }
        Widget[] children = container.getDynamicChildren();
        if (children == null || children.length == 0) {
            children = container.getChildren();
        }
        if (children == null) {
            return null;
        }
        for (Widget child : children) {
            if (child == null || child.isHidden() || child.getItemId() <= 0) {
                continue;
            }
            if (itemId >= 0) {
                if (child.getItemId() == itemId) {
                    return ClickPoints.forWidgetOnClient(child);
                }
                continue;
            }
            ItemComposition comp = c.getItemDefinition(child.getItemId());
            if (comp != null && comp.getName() != null && name.equalsIgnoreCase(comp.getName())) {
                return ClickPoints.forWidgetOnClient(child);
            }
        }
        return null;
    }

    private static List<IInventoryItem> getAllOnClient(Predicate<IInventoryItem> filter) {
        List<IInventoryItem> out = new ArrayList<>();
        Client c = Static.getClient();
        if (c == null || !isOpenOnClient(c)) {
            return out;
        }
        ItemContainer container = c.getItemContainer(InventoryID.BANK);
        if (container == null || container.getItems() == null) {
            return out;
        }
        Item[] items = container.getItems();
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            if (item == null || item.getId() < 0) {
                continue;
            }
            IInventoryItem wrap = new RlInventoryItem(item, i);
            if (filter == null || filter.test(wrap)) {
                out.add(wrap);
            }
        }
        return out;
    }

    private static boolean isOpenOnClient(Client c) {
        Widget w = c.getWidget(WidgetInfo.BANK_CONTAINER);
        return w != null && !w.isHidden();
    }
}
