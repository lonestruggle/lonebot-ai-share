package com.lonebot.example.quest.helpers;

import com.lonebot.example.quest.QuestLog;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankSnapshot;
import net.storm.sdk.items.BankWithdrawHelper;
import net.storm.sdk.items.GeRestockHelper;
import net.storm.sdk.items.GrandExchange;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.utils.Sleep;

/**
 * Supply-chain: needed → unnoted inv → bank (live / {@link BankSnapshot} / Imp-JSON) → GE.
 * Deposit only noted / mixed noted+unnoted. Imp-loot (hammer/cadava/clay) niet GE-kopen
 * zolang progress/snapshot zegt dat het in de bank zit.
 */
public final class QuestBankGePrep {

    public static final class Need {
        public final String name;
        public final int qty;
        public final int gePriceHint;

        public Need(String name, int qty, int gePriceHint) {
            this.name = name;
            this.qty = qty;
            this.gePriceHint = gePriceHint;
        }
    }

    /** Live bank open + empty while Imp/snapshot claimed stock — allow GE after this many passes. */
    private static final int EMPTY_BANK_CONFIRMS_BEFORE_GE = 8;
    /** Sticky GE-reis: na leave-bank niet opnieuw Bank.open (anders Lumb↔GE pingpong). */
    private static final long GE_TRIP_TIMEOUT_MS = 180_000L;

    private static int emptyBankConfirmPasses;
    private static String pendingGeName;
    private static int pendingGeTargetQty;
    private static int pendingGePrice;
    private static long pendingGeSinceMs;

    private QuestBankGePrep() {
    }

    public static void reset() {
        emptyBankConfirmPasses = 0;
        clearPendingGe();
    }

    private static void clearPendingGe() {
        pendingGeName = null;
        pendingGeTargetQty = 0;
        pendingGePrice = 0;
        pendingGeSinceMs = 0L;
    }

    public static boolean hasUnnoted(Need n) {
        return QuestActions.unnotedCount(n.name) >= n.qty;
    }

    public static boolean hasAllUnnoted(Need... needs) {
        if (needs == null) {
            return true;
        }
        for (Need n : needs) {
            if (!hasUnnoted(n)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One tick of prep. {@code true} if still working (caller should return a short delay).
     */
    public static boolean ensureUnnoted(Need... needs) {
        if (needs == null || needs.length == 0) {
            return false;
        }
        if (hasAllUnnoted(needs)) {
            emptyBankConfirmPasses = 0;
            clearPendingGe();
            closeBankAndGeIfIdle();
            return false;
        }

        int block = BankWithdrawHelper.resolveBlockingBankInput();
        if (block > 0) {
            QuestLog.step("Bank", "Enter-amount failsafe");
            Sleep.sleep(Math.min(block, 600));
            return true;
        }

        // Sticky GE: bank dicht laten, niet terug Climb-up naar Lumb-booth
        if (tickPendingGe()) {
            return true;
        }

        if (GrandExchange.isOpen()) {
            if (GrandExchange.canCollect()) {
                GrandExchange.collect(false);
                Sleep.sleep(280, 480);
                return true;
            }
            // GE open maar tekort zit in bank (Imp/snapshot) → bank eerst
            if (anyLikelyInBank(needs)) {
                QuestLog.step("Bank", "GE dicht — Imp/snapshot zegt items in bank");
                clearPendingGe();
                GeRestockHelper.closeGeIfOpen();
                Bank.open();
                return true;
            }
        }

        if (!Bank.isOpen()) {
            // Altijd eerst bank als snapshot/Imp iets claimt of we sowieso moeten checken
            QuestLog.step("Bank", "open voor unnoted"
                    + (anyLikelyInBank(needs) ? " (Imp/snapshot op voorraad)" : ""));
            Bank.open();
            return true;
        }

        try {
            BankSnapshot.captureIfOpenThrottled();
        } catch (Throwable ignored) {
        }

        try {
            Bank.setNoted(false);
        } catch (Throwable ignored) {
        }

        // 1) Deposit only noted / mixed stacks (unnote), never good unnoted alone
        if (depositOneNotedOrMixed(needs)) {
            Sleep.sleep(200, 360);
            return true;
        }

        // 2) Withdraw unnoted deficits (live bank)
        boolean withdrew = false;
        for (Need n : needs) {
            int have = QuestActions.unnotedCount(n.name);
            if (have >= n.qty) {
                continue;
            }
            int inBank = liveBankCount(n.name);
            if (inBank <= 0) {
                continue;
            }
            int deficit = n.qty - have;
            QuestLog.step("Bank", "withdraw unnoted " + n.name + " ×" + Math.min(deficit, inBank)
                    + " (heb " + have + "/" + n.qty + ")");
            if (inBank <= deficit) {
                Bank.withdrawAll(n.name);
            } else {
                Bank.withdraw(n.name, 1);
            }
            Sleep.sleep(220, 400);
            if (BankWithdrawHelper.isEnterAmountPromptVisible()) {
                BankWithdrawHelper.resolveBlockingBankInput();
            }
            withdrew = true;
            emptyBankConfirmPasses = 0;
            return true;
        }

        // 3) Nog tekort? GE alleen als bank+snapshot+Imp geen voorraad claimen
        for (Need n : needs) {
            int haveInv = QuestActions.unnotedCount(n.name);
            if (haveInv >= n.qty) {
                continue;
            }
            int live = liveBankCount(n.name);
            int known = knownBankQty(n.name);
            int totalKnown = haveInv + Math.max(live, known);
            if (totalKnown >= n.qty || QuestImpLootHints.likelyInBank(n.name, n.qty - haveInv)) {
                emptyBankConfirmPasses++;
                QuestLog.step("Bank", n.name + " verwacht in bank (live=" + live
                        + " snap/Imp=" + known + ") — nog geen GE (pass "
                        + emptyBankConfirmPasses + "/" + EMPTY_BANK_CONFIRMS_BEFORE_GE + ")");
                if (emptyBankConfirmPasses < EMPTY_BANK_CONFIRMS_BEFORE_GE) {
                    // Stay at bank / re-open next tick — do not GE
                    return true;
                }
                QuestLog.force("Bank", n.name + " niet gevonden na Imp/snapshot claim → GE fallback");
            }
            int buy = n.qty - haveInv - live;
            if (buy <= 0) {
                continue;
            }
            beginPendingGe(n.name, n.qty, n.gePriceHint);
            Bank.leaveOpenForWalk();
            QuestLog.step("GE", "koop " + n.name + " ×" + buy + " (sticky reis)");
            emptyBankConfirmPasses = 0;
            GeRestockHelper.buyWithEscalation(n.name, buy, n.gePriceHint);
            return true;
        }

        return !withdrew;
    }

    /**
     * @return true als GE-trip nog loopt (caller delay)
     */
    private static boolean tickPendingGe() {
        if (pendingGeName == null) {
            return false;
        }
        int have = QuestActions.unnotedCount(pendingGeName);
        if (have >= pendingGeTargetQty) {
            QuestLog.step("GE", pendingGeName + " binnen (" + have + "/" + pendingGeTargetQty + ")");
            clearPendingGe();
            return false;
        }
        long now = System.currentTimeMillis();
        if (pendingGeSinceMs > 0L && now - pendingGeSinceMs > GE_TRIP_TIMEOUT_MS) {
            QuestLog.force("GE", "timeout " + pendingGeName + " — bank opnieuw");
            clearPendingGe();
            return false;
        }
        if (Bank.isOpen()) {
            Bank.leaveOpenForWalk();
        }
        int buy = pendingGeTargetQty - have;
        if (buy <= 0) {
            clearPendingGe();
            return false;
        }
        QuestLog.step("GE", "reis/koop " + pendingGeName + " ×" + buy
                + " (heb " + have + "/" + pendingGeTargetQty + ")");
        GeRestockHelper.buyWithEscalation(pendingGeName, buy, pendingGePrice);
        return true;
    }

    private static void beginPendingGe(String name, int targetQty, int price) {
        pendingGeName = name;
        pendingGeTargetQty = targetQty;
        pendingGePrice = price;
        pendingGeSinceMs = System.currentTimeMillis();
    }

    private static boolean anyLikelyInBank(Need... needs) {
        for (Need n : needs) {
            int have = QuestActions.unnotedCount(n.name);
            if (have >= n.qty) {
                continue;
            }
            if (QuestImpLootHints.likelyInBank(n.name, n.qty - have)) {
                return true;
            }
            if (knownBankQty(n.name) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Live open bank only. */
    private static int liveBankCount(String name) {
        try {
            return Bank.isOpen() ? Bank.getCount(name) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Snapshot when bank closed, or live when open ({@link BankSnapshot#qty}). */
    private static int knownBankQty(String name) {
        try {
            return Math.max(0, BankSnapshot.qty(name));
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void closeBankAndGeIfIdle() {
        if (Bank.isOpen()) {
            Bank.leaveOpenForWalk();
        }
        if (GrandExchange.isOpen()) {
            try {
                if (GrandExchange.canCollect()) {
                    GrandExchange.collect(false);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean depositOneNotedOrMixed(Need... needs) {
        if (!Bank.isOpen()) {
            return false;
        }
        for (Need n : needs) {
            IInventoryItem noted = Inventory.getFirst(i -> i != null
                    && n.name.equalsIgnoreCase(i.getName()) && i.isNoted());
            if (noted != null) {
                QuestLog.step("Bank", "deposit noted " + n.name);
                if (noted.hasAction("Deposit-All")) {
                    noted.interact("Deposit-All");
                } else if (noted.hasAction("Deposit-all")) {
                    noted.interact("Deposit-all");
                } else {
                    Bank.depositAll(n.name);
                }
                return true;
            }
        }
        for (Need n : needs) {
            boolean hasNoted = Inventory.getFirst(i -> i != null
                    && n.name.equalsIgnoreCase(i.getName()) && i.isNoted()) != null;
            if (!hasNoted) {
                continue;
            }
            IInventoryItem unnoted = Inventory.getFirst(i -> i != null
                    && n.name.equalsIgnoreCase(i.getName()) && !i.isNoted());
            if (unnoted != null) {
                QuestLog.step("Bank", "deposit mixed unnoted " + n.name);
                if (unnoted.hasAction("Deposit-All")) {
                    unnoted.interact("Deposit-All");
                } else {
                    Bank.depositAll(n.name);
                }
                return true;
            }
        }
        return false;
    }
}
