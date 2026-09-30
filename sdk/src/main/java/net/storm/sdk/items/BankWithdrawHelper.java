package net.storm.sdk.items;

import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Widgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;

/**
 * Shared bank-withdraw helpers: Enter-amount prompt, safe close, withdraw-toward-target.
 */
public final class BankWithdrawHelper {

    private static final Logger log = LoggerFactory.getLogger(BankWithdrawHelper.class);

    private static final long DEFAULT_VERIFY_TIMEOUT_MS = 2800L;

    /** Chatbox enter-amount — iface 162,43. */
    private static final int BANK_INTERFACE_GROUP = 162;
    private static final int BANK_ENTER_AMOUNT_CHILD = 43;

    /** Last withdraw amount for Enter-amount completion if bank closes early. */
    private static volatile int lastRequestedWithdrawAmount;

    private BankWithdrawHelper() {
    }

    public static int getLastRequestedWithdrawAmount() {
        return lastRequestedWithdrawAmount;
    }

    public static void setLastRequestedWithdrawAmount(int amount) {
        lastRequestedWithdrawAmount = amount;
    }

    /** {@code true} if the bank {@code Enter amount:} field is visible. */
    public static boolean isEnterAmountPromptVisible() {
        try {
            IWidget prompt = Widgets.get(BANK_INTERFACE_GROUP, BANK_ENTER_AMOUNT_CHILD);
            if (isEnterAmountWidget(prompt)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            List<IWidget> hits = Widgets.getAll("enter amount");
            if (hits != null) {
                for (IWidget w : hits) {
                    if (isEnterAmountWidget(w)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean isBlockingBankClose() {
        return isEnterAmountPromptVisible();
    }

    /**
     * Complete Enter amount (type digits + Enter) or Escape if orphaned.
     *
     * @return suggested delay ms if an action was taken; else 0
     */
    public static int resolveBlockingBankInput() {
        if (!isEnterAmountPromptVisible()) {
            return 0;
        }
        if (Bank.isOpen() && lastRequestedWithdrawAmount > 0) {
            log.debug("[BankWithdraw] Enter amount open — type {}", lastRequestedWithdrawAmount);
            if (completeEnterAmountPrompt(lastRequestedWithdrawAmount)) {
                HumanBanking.pauseBetweenActions();
                return 500;
            }
        }
        log.debug("[BankWithdraw] Enter amount orphan — Escape");
        dismissEnterAmountPrompt();
        return 450;
    }

    /**
     * Close bank only when Enter amount is not open.
     *
     * @return {@code true} if closed or already closed; {@code false} if blocked
     */
    public static boolean safeCloseBank() {
        if (isEnterAmountPromptVisible()) {
            log.debug("[BankWithdraw] safeCloseBank blocked — Enter amount open");
            return false;
        }
        if (Bank.isOpen()) {
            HumanBanking.pauseBeforeClose();
            Bank.close();
            HumanBanking.pauseAfterClose();
        }
        return true;
    }

    /** Inventory stacked count via {@link Inventory#getCount(boolean, String)}. */
    public static int inventoryCount(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return 0;
        }
        try {
            return Inventory.getCount(true, itemName);
        } catch (Throwable ignored) {
            try {
                return Inventory.contains(itemName) ? 1 : 0;
            } catch (Throwable ignored2) {
                return 0;
            }
        }
    }

    public static boolean hasTarget(String itemName, int targetCount) {
        if (targetCount <= 0) {
            return true;
        }
        return inventoryCount(itemName) >= targetCount;
    }

    public static boolean waitForTarget(String itemName, int targetCount, long timeoutMs) {
        if (hasTarget(itemName, targetCount)) {
            return true;
        }
        long deadline = System.currentTimeMillis() + Math.max(200L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (hasTarget(itemName, targetCount)) {
                return true;
            }
            if (isEnterAmountPromptVisible() && lastRequestedWithdrawAmount > 0) {
                completeEnterAmountPrompt(lastRequestedWithdrawAmount);
            }
            Sleep.sleep(80, 140);
        }
        return hasTarget(itemName, targetCount);
    }

    /**
     * Withdraw toward {@code targetCount}; at most {@code maxThisAttempt} per call.
     *
     * @return {@code true} if inventory has reached {@code targetCount}
     */
    public static boolean withdrawTowardsTarget(String itemName, int targetCount, int maxThisAttempt) {
        if (hasTarget(itemName, targetCount)) {
            return true;
        }
        if (!Bank.isOpen()) {
            log.debug("[BankWithdraw] {}: bank not open (target {})", itemName, targetCount);
            return false;
        }
        if (isEnterAmountPromptVisible()) {
            resolveBlockingBankInput();
            if (isEnterAmountPromptVisible()) {
                return false;
            }
        }
        int before = inventoryCount(itemName);
        int need = targetCount - before;
        if (need <= 0) {
            return true;
        }
        if (!Bank.contains(itemName)) {
            log.debug("[BankWithdraw] {}: not in bank (have {}/{})", itemName, before, targetCount);
            return false;
        }
        int amount = Math.min(need, Math.max(1, maxThisAttempt));
        int verifyTarget = Math.min(targetCount, before + amount);
        lastRequestedWithdrawAmount = amount;
        try {
            Bank.withdraw(itemName, amount);
        } catch (Throwable t) {
            log.warn("[BankWithdraw] {}: withdraw failed — {}", itemName, t.getMessage());
            lastRequestedWithdrawAmount = 0;
            return false;
        }
        HumanBanking.pauseBetweenActions();
        if (isEnterAmountPromptVisible()) {
            completeEnterAmountPrompt(amount);
            HumanBanking.pauseBetweenActions();
        }
        boolean verified = waitForTarget(itemName, verifyTarget, DEFAULT_VERIFY_TIMEOUT_MS);
        int after = inventoryCount(itemName);
        log.debug("[BankWithdraw] {}: {} → {} (target {}, verified={})",
                itemName, before, after, targetCount, verified);
        if (!verified && after <= before && Bank.isOpen() && Bank.contains(itemName)) {
            Bank.withdraw(itemName, amount);
            HumanBanking.pauseBetweenActions();
            if (isEnterAmountPromptVisible()) {
                completeEnterAmountPrompt(amount);
                HumanBanking.pauseBetweenActions();
            }
            waitForTarget(itemName, verifyTarget, DEFAULT_VERIFY_TIMEOUT_MS);
        }
        if (hasTarget(itemName, verifyTarget)) {
            lastRequestedWithdrawAmount = 0;
        }
        return hasTarget(itemName, targetCount);
    }

    /** Type amount into Enter-amount prompt and confirm with Enter. */
    public static boolean completeEnterAmountPrompt(int amount) {
        if (!isEnterAmountPromptVisible() || amount <= 0) {
            return false;
        }
        try {
            Keyboard.type(String.valueOf(amount));
            Sleep.sleep(50, 110);
            Keyboard.pressKey(KeyEvent.VK_ENTER);
            Sleep.sleep(140, 260);
            if (!isEnterAmountPromptVisible()) {
                log.debug("[BankWithdraw] Enter amount filled: {}", amount);
                lastRequestedWithdrawAmount = 0;
                return true;
            }
            // Retry eens
            Keyboard.type(String.valueOf(amount));
            Sleep.sleep(40, 80);
            Keyboard.pressKey(KeyEvent.VK_ENTER);
            Sleep.sleep(120, 220);
            if (!isEnterAmountPromptVisible()) {
                lastRequestedWithdrawAmount = 0;
                return true;
            }
        } catch (Throwable t) {
            log.warn("[BankWithdraw] Enter amount type failed: {}", t.getMessage());
        }
        return !isEnterAmountPromptVisible();
    }

    /** Escape a stuck Enter-amount prompt. */
    public static boolean dismissEnterAmountPrompt() {
        if (!isEnterAmountPromptVisible()) {
            return false;
        }
        try {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
            Sleep.sleep(150, 280);
            if (!isEnterAmountPromptVisible()) {
                lastRequestedWithdrawAmount = 0;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return !isEnterAmountPromptVisible();
    }

    private static boolean isEnterAmountWidget(IWidget w) {
        if (w == null || w.isHidden()) {
            return false;
        }
        String t = widgetText(w).toLowerCase(Locale.ROOT);
        return t.contains("enter amount");
    }

    private static String widgetText(IWidget w) {
        if (w == null) {
            return "";
        }
        try {
            String t = w.getText();
            return t != null ? t.trim() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
