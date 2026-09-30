package com.lonebot.example.clue;

import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.widgets.Tab;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.widgets.Tabs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bank for Spade, Strange device, and Charlie delivery items (unnoted). Keep clue/casket.
 */
public final class ClueBankHelper {

    private boolean banking;
    private String lastNeed;
    private long lastWithdrawMs;
    private long missingBackoffUntilMs;
    private long lastLogMs;
    private String lastLogMsg = "";
    private final Set<String> missingItems = new HashSet<>();
    private String geBuyPending;
    private long lastGeBuyMs;
    private boolean bankingCasket;
    /** Na casket-deposit: emote/Charlie-kit opruimen (CombatBot cleanup). */
    private boolean kitCleanupPending;

    public void reset() {
        banking = false;
        lastNeed = null;
        lastWithdrawMs = 0L;
        missingBackoffUntilMs = 0L;
        missingItems.clear();
        lastLogMs = 0L;
        lastLogMsg = "";
        geBuyPending = null;
        lastGeBuyMs = 0L;
        bankingCasket = false;
        kitCleanupPending = false;
    }

    public boolean isBanking() {
        return banking || bankingCasket || geBuyPending != null || Bank.isOpen();
    }

    /** GE-buy tick; ≥0 = bezig, -1 = idle. */
    public int tickGeBuy() {
        if (geBuyPending == null || geBuyPending.isBlank()) {
            return -1;
        }
        if (!BotRuntime.clueGeBuyMissing) {
            geBuyPending = null;
            return -1;
        }
        String item = geBuyPending;
        if (Inventory.contains(item) || CharlieClueHelper.hasItemExact(item) || hasKitItem(item)) {
            BotRuntime.logConsole("[Clue/GE] klaar: " + item);
            geBuyPending = null;
            missingItems.remove(item.toLowerCase(Locale.ROOT));
            missingBackoffUntilMs = 0L;
            banking = false;
            if (Bank.isOpen()) {
                Bank.close();
            }
            return 300;
        }
        if (System.currentTimeMillis() - lastGeBuyMs < 600L) {
            return 350;
        }
        banking = true;
        int coins = 0;
        try {
            coins = Inventory.getCount("Coins");
        } catch (Throwable ignored) {
        }
        int price = BeginnerClueReference.geStartPrice(item);
        int needCoins = Math.max(price, 500);

        // Te weinig gp → bank open houden en coins withdrawen (niet open/dicht-loop)
        if (coins < needCoins) {
            if (!Bank.isOpen()) {
                lastGeBuyMs = System.currentTimeMillis();
                if (BankHelper.tryOpenFullBank()) {
                    logThrottled("[Clue/GE] open bank (coins)");
                    return 500;
                }
                if (BankHelper.walkToNearestFullBank()) {
                    logThrottled("[Clue/GE] → bank coins voor " + item);
                    return 700;
                }
                return 600;
            }
            lastGeBuyMs = System.currentTimeMillis();
            boolean hasCoins;
            try {
                hasCoins = Bank.contains("Coins");
            } catch (Throwable t) {
                hasCoins = false;
            }
            if (hasCoins) {
                Bank.withdraw("Coins", Integer.MAX_VALUE);
                BotRuntime.logConsole("[Clue/GE] withdraw Coins (need ≥" + needCoins + ")");
                return 550;
            }
            BotRuntime.logConsole("[Clue/GE] geen coins in bank voor " + item + " — stop GE");
            markMissing(item);
            geBuyPending = null;
            banking = false;
            Bank.close();
            return 800;
        }

        // Genoeg coins → bank dicht, dan GE
        if (Bank.isOpen()) {
            lastGeBuyMs = System.currentTimeMillis();
            Bank.close();
            BotRuntime.logConsole("[Clue/GE] bank dicht → GE " + item);
            return 400;
        }

        lastGeBuyMs = System.currentTimeMillis();
        BotRuntime.logConsole("[Clue/GE] buy 1× " + item + " start@" + price + "gp");
        boolean ok = net.storm.sdk.items.GeRestockHelper.buyWithEscalation(item, 1, price);
        if (ok || Inventory.contains(item) || hasKitItem(item) || CharlieClueHelper.hasItemExact(item)) {
            geBuyPending = null;
            missingItems.remove(item.toLowerCase(Locale.ROOT));
            missingBackoffUntilMs = 0L;
            banking = false;
            BotRuntime.logConsole("[Clue/GE] ok: " + item);
            return 600;
        }
        return 900;
    }

    public boolean hasGeBuyPending() {
        return geBuyPending != null && !geBuyPending.isBlank();
    }

    /** Queue GE-koop (spade/kit) — bank dicht, geen withdraw-loop. */
    public void queueGeBuy(String itemName) {
        if (itemName == null || itemName.isBlank() || !BotRuntime.clueGeBuyMissing) {
            return;
        }
        if (!BeginnerClueReference.isGePurchasableKitItem(itemName)) {
            return;
        }
        geBuyPending = itemName;
        missingItems.remove(itemName.toLowerCase(Locale.ROOT));
        missingBackoffUntilMs = 0L;
        banking = false;
        if (Bank.isOpen()) {
            try {
                Bank.close();
            } catch (Throwable ignored) {
            }
        }
        BotRuntime.logConsole("[Clue/GE] queued: " + itemName);
    }

    /**
     * Reward casket (+ strange device) naar bank — wist RuneLite Hot/Cold overlay vaak mee.
     *
     * @return delay ≥0 while banking; -1 als klaar / geen casket
     */
    public int bankRewardCasket() {
        boolean hasCasket = BeginnerClueContainerHelper.hasBeginnerCasket();
        boolean hasDevice = Inventory.contains(BeginnerClueReference.STRANGE_DEVICE);
        if (hasCasket) {
            kitCleanupPending = true;
        }
        boolean cleanupKit = kitCleanupPending && hasAnyClueKitInInv();
        if (!hasCasket && !hasDevice && !cleanupKit) {
            bankingCasket = false;
            kitCleanupPending = false;
            if (Bank.isOpen()) {
                Bank.close();
                return 300;
            }
            return -1;
        }
        bankingCasket = true;
        if (!Bank.isOpen()) {
            if (BankHelper.tryOpenFullBank()) {
                logThrottled("[Clue/Casket] bank open");
                return 500;
            }
            if (BankHelper.walkToNearestFullBank()) {
                logThrottled("[Clue/Casket] → bank");
                return 700;
            }
            return 600;
        }
        if (hasCasket) {
            try {
                Bank.depositAll(BeginnerClueReference.REWARD_CASKET_BEGINNER);
            } catch (Throwable ignored) {
            }
            BotRuntime.logConsole("[Clue/Casket] deposit reward casket");
            return 450;
        }
        if (hasDevice) {
            try {
                Bank.depositAll(BeginnerClueReference.STRANGE_DEVICE);
            } catch (Throwable ignored) {
            }
            BotRuntime.logConsole("[Clue/Casket] deposit Strange device (overlay clear)");
            return 450;
        }
        // CombatBot casket-cleanup: één kit-item per tick (geen scroll/spade)
        if (depositOneClueKitItem()) {
            return 400;
        }
        bankingCasket = false;
        kitCleanupPending = false;
        Bank.close();
        return 300;
    }

    private static boolean hasAnyClueKitInInv() {
        for (String kit : BeginnerClueReference.allKitItemNames()) {
            if (kit == null || kit.isBlank()) {
                continue;
            }
            if (BeginnerClueReference.SPADE.equalsIgnoreCase(kit)
                    || BeginnerClueReference.STRANGE_DEVICE.equalsIgnoreCase(kit)) {
                continue; // spade mag blijven; device apart
            }
            try {
                if (Inventory.contains(kit)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** CombatBot {@code depositClueKitItemsFromInventory} — 1 stack per tick. */
    private boolean depositOneClueKitItem() {
        if (!Bank.isOpen()) {
            return false;
        }
        for (String kit : BeginnerClueReference.allKitItemNames()) {
            if (kit == null || kit.isBlank()) {
                continue;
            }
            if (BeginnerClueReference.CLUE_SCROLL_BEGINNER.equalsIgnoreCase(kit)
                    || BeginnerClueReference.REWARD_CASKET_BEGINNER.equalsIgnoreCase(kit)
                    || BeginnerClueReference.SPADE.equalsIgnoreCase(kit)) {
                continue;
            }
            if (!Inventory.contains(kit)) {
                continue;
            }
            try {
                Bank.depositAll(kit);
            } catch (Throwable ignored) {
            }
            BotRuntime.logConsole("[Clue/Casket] cleanup deposit " + kit);
            return true;
        }
        return false;
    }

    /** Alleen device storten na afgeronde clue (RuneLite Hot/Cold box). */
    public int bankStrangeDeviceIfIdle() {
        if (BeginnerClueContainerHelper.hasBeginnerClue()
                || BeginnerClueContainerHelper.hasBeginnerCasket()) {
            return -1;
        }
        if (!Inventory.contains(BeginnerClueReference.STRANGE_DEVICE)) {
            return -1;
        }
        return bankRewardCasket();
    }

    /**
     * @param needSpade dig/emote/hot-cold
     * @param hotCold   strange device
     * @param charlieItem item Charlie vroeg, of null
     */
    public int ensureTools(boolean needSpadeFlag, boolean hotCold, String charlieItem) {
        if (geBuyPending != null && !geBuyPending.isBlank()) {
            return tickGeBuy();
        }
        boolean needSpade = needSpadeFlag && !Inventory.contains(BeginnerClueReference.SPADE);
        boolean needDevice = hotCold && !Inventory.contains(BeginnerClueReference.STRANGE_DEVICE);
        boolean needCharlie = charlieItem != null && !charlieItem.isBlank()
                && !CharlieClueHelper.hasItemExact(charlieItem);
        if (!needSpade && !needDevice && !needCharlie) {
            if (Bank.isOpen()) {
                Bank.close();
                banking = false;
                return 300;
            }
            banking = false;
            return -1;
        }
        if (needCharlie && CharlieClueHelper.hasNotedItem(charlieItem) && !CharlieClueHelper.hasItemExact(charlieItem)) {
            int fix = fixCharlieNoted(charlieItem);
            if (fix >= 0) {
                return fix;
            }
        }
        // Spade ontbreekt + GE aan: niet eindeloos bank-openen na bekende miss
        if (needSpade && BotRuntime.clueGeBuyMissing
                && missingItems.contains(BeginnerClueReference.SPADE.toLowerCase(Locale.ROOT))) {
            queueGeBuy(BeginnerClueReference.SPADE);
            return tickGeBuy();
        }
        if (System.currentTimeMillis() < missingBackoffUntilMs) {
            // Tijdens backoff: als GE mag en we spade nodig hebben → toch GE
            if (needSpade && BotRuntime.clueGeBuyMissing) {
                queueGeBuy(BeginnerClueReference.SPADE);
                return tickGeBuy();
            }
            return -1;
        }
        banking = true;
        if (!Bank.isOpen()) {
            if (BankHelper.tryOpenFullBank()) {
                logThrottled("[Clue/Bank] open");
                return 500;
            }
            if (BankHelper.walkToNearestFullBank()) {
                logThrottled("[Clue/Bank] → bank");
                return 700;
            }
            return 600;
        }
        List<String> keep = new ArrayList<>(BeginnerClueReference.keepNames(hotCold, charlieItem));
        if (Inventory.isFull()) {
            Bank.depositAllExcept(keep.toArray(new String[0]));
            return 400;
        }
        if (needSpade) {
            return withdrawOne(BeginnerClueReference.SPADE, false);
        }
        if (needDevice) {
            return withdrawOne(BeginnerClueReference.STRANGE_DEVICE, false);
        }
        return withdrawCharlieUnnoted(charlieItem);
    }

    /** Compat: oude signature = altijd spade + optioneel device/charlie. */
    public int ensureTools(boolean hotCold, String charlieItem) {
        return ensureTools(true, hotCold, charlieItem);
    }

    /**
     * Charlie-only: unnoted withdraw; deposit noted same-name stacks first.
     *
     * @return delay ≥0 while banking; -1 if item available or missing (backoff)
     */
    public int ensureCharlieItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return -1;
        }
        if (CharlieClueHelper.hasItemExact(itemName)) {
            if (Bank.isOpen()) {
                Bank.close();
                banking = false;
                return 300;
            }
            return -1;
        }
        if (CharlieClueHelper.hasNotedItem(itemName)) {
            int fix = fixCharlieNoted(itemName);
            if (fix >= 0) {
                return fix;
            }
        }
        return ensureTools(false, false, itemName);
    }

    /**
     * Emote-kit: withdraw one item if missing from inv+equip (geen GE).
     *
     * @return delay ≥0 while banking/withdraw; -1 als aanwezig of missing-backoff
     */
    public int ensureKitItem(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return -1;
        }
        if (hasKitItem(itemName)) {
            if (Bank.isOpen()) {
                Bank.close();
                banking = false;
                return 300;
            }
            return -1;
        }
        if (System.currentTimeMillis() < missingBackoffUntilMs) {
            return -1;
        }
        banking = true;
        if (!Bank.isOpen()) {
            if (BankHelper.tryOpenFullBank()) {
                logThrottled("[Clue/Bank] open (emote-kit " + itemName + ")");
                return 500;
            }
            if (BankHelper.walkToNearestFullBank()) {
                logThrottled("[Clue/Bank] → bank emote-kit " + itemName);
                return 700;
            }
            return 600;
        }
        List<String> keep = new ArrayList<>(BeginnerClueReference.keepNames(false, null));
        keep.add(itemName);
        if (Inventory.isFull()) {
            Bank.depositAllExcept(keep.toArray(new String[0]));
            return 400;
        }
        int w = withdrawOne(itemName, false);
        if (w >= 0) {
            return w;
        }
        if (BotRuntime.clueGeBuyMissing
                && BeginnerClueReference.isGePurchasableKitItem(itemName)
                && !hasKitItem(itemName)) {
            queueGeBuy(itemName);
            return 400;
        }
        return w;
    }

    public static boolean hasKitItem(String requiredName) {
        if (requiredName == null || requiredName.isBlank()) {
            return true;
        }
        try {
            if (net.storm.sdk.items.Equipment.contains(eq -> eq != null && eq.getName() != null
                    && BeginnerClueReference.kitRequirementMetByItemName(requiredName, eq.getName()))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return Inventory.contains(i -> i != null && i.getName() != null
                    && BeginnerClueReference.kitRequirementMetByItemName(requiredName, i.getName()));
        } catch (Throwable ignored) {
            return Inventory.contains(requiredName);
        }
    }

    /** Wear/Wield from inventory if present; else bank via {@link #ensureKitItem}. */
    public int ensureEquipped(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return -1;
        }
        try {
            if (net.storm.sdk.items.Equipment.contains(eq -> eq != null && eq.getName() != null
                    && BeginnerClueReference.kitRequirementMetByItemName(itemName, eq.getName()))) {
                return -1;
            }
        } catch (Throwable ignored) {
        }
        IInventoryItem inv = null;
        try {
            inv = Inventory.getFirst(i -> i != null && i.getName() != null
                    && BeginnerClueReference.kitRequirementMetByItemName(itemName, i.getName()));
        } catch (Throwable ignored) {
            inv = Inventory.getFirst(itemName);
        }
        if (inv == null) {
            int banked = ensureKitItem(itemName);
            return banked >= 0 ? banked : 800;
        }
        String action = inv.hasAction("Wear") ? "Wear"
                : (inv.hasAction("Wield") ? "Wield"
                : (inv.hasAction("Equip") ? "Equip" : "Wear"));
        if (inv.interact(action)) {
            BotRuntime.logConsole("[Clue/Emote] equip " + itemName);
            return 500;
        }
        return 400;
    }

    private int fixCharlieNoted(String needed) {
        String canon = BeginnerClueReference.canonicalCharlieItemName(needed);
        banking = true;
        if (!Bank.isOpen()) {
            if (BankHelper.tryOpenFullBank()) {
                logThrottled("[Clue/Bank] open (noted→unnoted " + canon + ")");
                return 500;
            }
            if (BankHelper.walkToNearestFullBank()) {
                logThrottled("[Clue/Bank] → bank noted→unnoted " + canon);
                return 700;
            }
            return 600;
        }
        depositCharlieNoted(canon);
        ensureUnnotedWithdrawMode();
        if (bankUsableQty(canon) > 0) {
            return withdrawCharlieUnnoted(canon);
        }
        logThrottled("[Clue/Bank] noted gestort, geen unnoted " + canon + " in bank");
        markMissing(canon);
        Bank.close();
        banking = false;
        return 500;
    }

    private int withdrawCharlieUnnoted(String name) {
        if (name == null) {
            return 400;
        }
        ensureUnnotedWithdrawMode();
        String canon = BeginnerClueReference.canonicalCharlieItemName(name);
        if (CharlieClueHelper.hasNotedItem(canon)) {
            depositCharlieNoted(canon);
        }
        return withdrawOne(canon, true);
    }

    private void depositCharlieNoted(String canon) {
        if (canon == null || canon.isEmpty() || !Bank.isOpen()) {
            return;
        }
        try {
            for (IInventoryItem item : Inventory.getAll()) {
                if (item == null || item.getName() == null || !item.isNoted()) {
                    continue;
                }
                if (item.getName().equalsIgnoreCase(canon)) {
                    Bank.depositAll(item.getName());
                    BotRuntime.logConsole("[Clue/Bank] deposit noted " + canon);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void ensureUnnotedWithdrawMode() {
        try {
            if (Bank.isOpen() && Bank.isNoted()) {
                Bank.setNoted(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private int withdrawOne(String name, boolean charlieUnnoted) {
        if (name == null) {
            return 400;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (missingItems.contains(key)) {
            if (BotRuntime.clueGeBuyMissing
                    && BeginnerClueReference.isGePurchasableKitItem(name)) {
                queueGeBuy(name);
                return 400;
            }
            missingBackoffUntilMs = System.currentTimeMillis() + 60_000L;
            if (Bank.isOpen()) {
                Bank.close();
            }
            banking = false;
            return -1;
        }
        if (System.currentTimeMillis() - lastWithdrawMs < 400L && name.equals(lastNeed)) {
            return 300;
        }
        if (charlieUnnoted) {
            ensureUnnotedWithdrawMode();
        }
        int qty = bankUsableQty(name);
        if (qty <= 0) {
            if (BotRuntime.clueGeBuyMissing
                    && BeginnerClueReference.isGePurchasableKitItem(name)) {
                String buyName = name;
                if (charlieUnnoted) {
                    String canon = BeginnerClueReference.canonicalCharlieItemName(name);
                    if (canon != null && !canon.isBlank()) {
                        buyName = canon;
                    }
                }
                logThrottled("[Clue/Bank] ontbreekt in bank → GE: " + buyName);
                queueGeBuy(buyName);
                return 400;
            }
            logThrottled("[Clue/Bank] ontbreekt: " + name + (charlieUnnoted ? " (geen GE)" : ""));
            markMissing(name);
            Bank.close();
            banking = false;
            return 500;
        }
        int before = CharlieClueHelper.countUnnoted(name);
        if (Bank.withdraw(name, 1)) {
            lastNeed = name;
            lastWithdrawMs = System.currentTimeMillis();
            BotRuntime.logConsole("[Clue/Bank] withdraw " + name + (charlieUnnoted ? " (unnoted)" : ""));
            return 500;
        }
        IInventoryItem inBank = Bank.getFirst(name);
        if (inBank != null && inBank.interact("Withdraw-1")) {
            lastWithdrawMs = System.currentTimeMillis();
            return 500;
        }
        int after = CharlieClueHelper.countUnnoted(name);
        if (after <= before) {
            BotRuntime.logConsole("[Clue/Bank] withdraw fail: " + name);
            if (BotRuntime.clueGeBuyMissing
                    && BeginnerClueReference.isGePurchasableKitItem(name)) {
                queueGeBuy(name);
                return 400;
            }
            markMissing(name);
        }
        return 400;
    }

    private void markMissing(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        missingItems.add(key);
        lastNeed = name;
        missingBackoffUntilMs = System.currentTimeMillis() + 60_000L;
    }

    private static int bankUsableQty(String name) {
        try {
            var item = Bank.getFirst(name);
            if (item == null) {
                item = Bank.getFirst(i -> i != null && i.getName() != null
                        && i.getName().equalsIgnoreCase(name));
            }
            return item != null ? Math.max(0, item.getQuantity()) : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean inventoryHasSpade() {
        return Inventory.contains(BeginnerClueReference.SPADE);
    }

    public static boolean inventoryHasDevice() {
        return Inventory.contains(BeginnerClueReference.STRANGE_DEVICE);
    }

    public static void openInventory() {
        if (!Tabs.isOpen(Tab.INVENTORY)) {
            Tabs.open(Tab.INVENTORY);
        }
    }

    private void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLogMsg) && now - lastLogMs < 1_500L) {
            return;
        }
        lastLogMs = now;
        lastLogMsg = msg;
        BotRuntime.logConsole(msg);
    }
}
