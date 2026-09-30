package net.storm.sdk.items;

import net.runelite.api.Point;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Widgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CombatBot-style GE UI: human typing, exact search row, qty/price, +5%, confirm, warning.
 */
public final class GeHelper {

    private static final Logger log = LoggerFactory.getLogger(GeHelper.class);

    private static final int TYPING_DELAY_MIN_MS = 95;
    private static final int TYPING_DELAY_MAX_MS = 195;
    private static final int AMOUNT_DIGIT_MIN_MS = 70;
    private static final int AMOUNT_DIGIT_MAX_MS = 140;
    private static final int AMOUNT_PROMPT_WAIT_MS = 1600;
    private static final int CLEAR_BACKSPACE_COUNT = 42;
    private static final int CLEAR_PRICE_BACKSPACE_COUNT = 10;
    private static final int AFTER_TYPE_BEFORE_SELECT_MIN_MS = 420;
    private static final int AFTER_TYPE_BEFORE_SELECT_MAX_MS = 720;
    private static final int[] GE_WIDGET_GROUPS = {465, 467, 304, 162, 193, 219, 217, 231, 289};
    private static final int GE_OFFER_ITEM_DETAIL_GROUP = 465;
    private static final int GE_OFFER_ITEM_DETAIL_CHILD = 27;
    private static final int GE_RESULT_CHILD_MIN = 18;
    private static final int GE_RESULT_CHILD_MAX = 65;
    private static final int GE_GUIDE_PRICE_WARNING_GROUP = 289;
    private static final int GE_GUIDE_PRICE_WARNING_YES_CHILD = 8;
    private static final long GE_SELECT_TIMEOUT_MS = 3_500L;
    private static final Pattern ACTIVELY_TRADED_PRICE = Pattern.compile(
            "actively traded price:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static volatile long lastLogMs;
    private static volatile String lastLog = "";

    private GeHelper() {
    }

    static void geLog(String msg) {
        if (msg == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1_500L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[GE] " + msg);
        log.info("[GE] {}", msg);
    }

    public static boolean isGeOfferSetupForItem(String exactItemName) {
        if (!GrandExchange.isOpen() || GrandExchange.isSearchingItem()) {
            return false;
        }
        if (!GrandExchange.isSetupOpen()) {
            return false;
        }
        try {
            int id = GrandExchange.getItemId();
            if (id > 0) {
                String fromId = GrandExchange.itemNameOf(id);
                if (matchesExactGeItemName(exactItemName, fromId)) {
                    return true;
                }
            }
            String selected = GrandExchange.getItemName();
            return selected != null && !selected.trim().isEmpty()
                    && matchesExactGeItemName(exactItemName, selected);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * One search attempt: focus → clear → type letter-by-letter → exact result (no Enter).
     */
    public static boolean ensureGeItemSelectedForBuy(String exactItemName) {
        return ensureGeItemSelectedForBuy(exactItemName, null);
    }

    public static boolean ensureGeItemSelectedForBuy(String exactItemName, BooleanSupplier proceed) {
        if (exactItemName == null || exactItemName.isEmpty() || !shouldProceed(proceed)) {
            return false;
        }
        if (!GrandExchange.isOpen()) {
            return false;
        }
        if (isGeOfferSetupForItem(exactItemName)) {
            return true;
        }
        if (hasWrongGeOfferSetupItem(exactItemName)) {
            geLog("verkeerd item op setup ('" + readGeOfferSetupItemName() + "') — terug naar zoeken");
            tryReturnToGeItemSearch(proceed);
        }
        if (!ensureGeItemSearchOpen(proceed)) {
            geLog("zoekscherm niet open voor '" + exactItemName + "'");
            return false;
        }
        focusGeSearchFieldBeforeTyping();
        clearGeSearchFieldCompletely(proceed);
        if (!shouldProceed(proceed)) {
            return false;
        }
        typeItemNameLetterByLetter(geSearchQuery(exactItemName), proceed);
        if (!shouldProceed(proceed)) {
            return false;
        }
        sleepQuiet(AFTER_TYPE_BEFORE_SELECT_MIN_MS, AFTER_TYPE_BEFORE_SELECT_MAX_MS, proceed);
        int n = 0;
        try {
            List<GrandExchange.GESearchResult> rows = GrandExchange.getSearchResults();
            n = rows != null ? rows.size() : 0;
        } catch (Throwable ignored) {
        }
        geLog("na typen results=" + n + " searching=" + GrandExchange.isSearchingItem());
        if (selectExactSearchResultAfterTyping(exactItemName, proceed, GE_SELECT_TIMEOUT_MS)) {
            return waitForGeOfferSetup(exactItemName, proceed, 2500L);
        }
        IWidget last = findGeSearchResultWidget(exactItemName);
        geLog("geen exacte selectie voor '" + exactItemName + "' (widget="
                + (last != null ? widgetIface(last) : "geen") + ", geen Enter)");
        return false;
    }

    public static boolean tryReturnToGeItemSearch(BooleanSupplier proceed) {
        try {
            if (GrandExchange.isSearchingItem()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (clickGeWidgetByExactText("Cancel") || clickGeWidgetByTextContains("cancel")) {
            sleepQuiet(320, 520, proceed);
            if (isGeSearchActive(proceed)) {
                return true;
            }
        }
        Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        sleepQuiet(280, 450, proceed);
        if (isGeSearchActive(proceed)) {
            return true;
        }
        if (GrandExchange.isOpen()) {
            GrandExchange.createBuyOffer();
            sleepQuiet(380, 620, proceed);
            focusGeSearchFieldBeforeTyping();
            return isGeSearchActive(proceed);
        }
        return false;
    }

    public static boolean tryClickGeConfirmButton() {
        if (GrandExchange.confirm()) {
            return true;
        }
        return clickGeWidgetByExactText("Confirm") || clickGeWidgetByTextContains("confirm");
    }

    public static boolean isGeHighPriceWarningVisible() {
        IWidget yes = Widgets.get(GE_GUIDE_PRICE_WARNING_GROUP, GE_GUIDE_PRICE_WARNING_YES_CHILD);
        if (yes != null && !yes.isHidden()) {
            return true;
        }
        return clickableTextContains("guide") && (widgetTextVisible("yes") || widgetTextVisible("Yes"));
    }

    public static boolean dismissHighPriceWarning() {
        return tryDismissGeHighPriceWarning();
    }

    public static boolean tryDismissGeHighPriceWarning() {
        IWidget yes = Widgets.get(GE_GUIDE_PRICE_WARNING_GROUP, GE_GUIDE_PRICE_WARNING_YES_CHILD);
        if (yes != null && !yes.isHidden() && clickWidget(yes)) {
            sleepQuiet(220, 380, null);
            return true;
        }
        if (clickGeWidgetByExactText("Yes") || clickGeWidgetByTextContains("yes")) {
            sleepQuiet(220, 380, null);
            return true;
        }
        return true;
    }

    public static void dismissGeHighPriceWarningAfterConfirm(BooleanSupplier proceed) {
        long deadline = System.currentTimeMillis() + 3_500L;
        while (System.currentTimeMillis() < deadline && shouldProceed(proceed)) {
            IWidget yes = Widgets.get(GE_GUIDE_PRICE_WARNING_GROUP, GE_GUIDE_PRICE_WARNING_YES_CHILD);
            if (yes != null && !yes.isHidden()) {
                clickWidget(yes);
                sleepQuiet(220, 380, proceed);
                continue;
            }
            if (clickGeWidgetByExactText("Yes")) {
                sleepQuiet(220, 380, proceed);
                continue;
            }
            break;
        }
    }

    public static boolean clickGeWidgetByExactText(String exactText) {
        if (exactText == null || exactText.isEmpty()) {
            return false;
        }
        String want = exactText.trim();
        for (int group : GE_WIDGET_GROUPS) {
            IWidget root = Widgets.get(group, 0);
            IWidget hit = findExactText(root, want, 0);
            if (hit != null && clickWidget(hit)) {
                return true;
            }
        }
        return false;
    }

    public static boolean clickGeWidgetByTextContains(String needle) {
        if (needle == null || needle.isEmpty()) {
            return false;
        }
        String low = needle.toLowerCase(Locale.ROOT);
        for (int group : GE_WIDGET_GROUPS) {
            IWidget root = Widgets.get(group, 0);
            IWidget hit = findContainsText(root, low, 0);
            if (hit != null && clickWidget(hit)) {
                return true;
            }
        }
        List<IWidget> all = Widgets.getAll(needle);
        for (IWidget w : all) {
            if (w != null && !w.isHidden() && clickWidget(w)) {
                return true;
            }
        }
        return false;
    }

    public static int readActivelyTradedPrice() {
        IWidget detail = Widgets.get(GE_OFFER_ITEM_DETAIL_GROUP, GE_OFFER_ITEM_DETAIL_CHILD);
        int parsed = parseActivelyTradedPriceFromWidget(detail);
        if (parsed > 0) {
            return parsed;
        }
        for (int group : GE_WIDGET_GROUPS) {
            IWidget root = Widgets.get(group, 0);
            int walk = walkActivelyTraded(root, 0);
            if (walk > 0) {
                return walk;
            }
        }
        return -1;
    }

    public static boolean clickGePricePlusFivePercent() {
        int before = safeGePrice();
        if (GeOffersClick.plusFivePercent()) {
            sleepQuiet(180, 320, null);
            return safeGePrice() != before || before <= 0;
        }
        return clickGeWidgetByExactText("+5%");
    }

    public static int applyGePricePlusFivePercentClicks(int clicks) {
        int n = Math.max(0, clicks);
        int before = safeGePrice();
        int applied = 0;
        for (int i = 0; i < n; i++) {
            int prev = safeGePrice();
            if (!clickGePricePlusFivePercent()) {
                break;
            }
            applied++;
            sleepQuiet(160, 280, null);
            int now = safeGePrice();
            if (prev > 0 && now == prev) {
                break;
            }
        }
        int after = safeGePrice();
        if (after <= 0 && before > 0 && applied > 0) {
            return (int) Math.ceil(before * Math.pow(1.05, applied));
        }
        return after > 0 ? after : before;
    }

    public static void focusGePriceFieldForEdit() {
        if (!GeOffersClick.enterPrice()) {
            clickGeWidgetByTextContains("price per item");
        }
        sleepQuiet(220, 380, null);
    }

    public static void focusGeQuantityFieldForEdit() {
        if (!GeOffersClick.enterQuantity()) {
            clickGeWidgetByTextContains("quantity");
        }
        sleepQuiet(220, 380, null);
    }

    public static boolean trySetGeOfferQuantityRobust(int want) {
        return trySetGeOfferQuantityRobust(want, null);
    }

    public static boolean trySetGeOfferQuantityRobust(int want, BooleanSupplier proceed) {
        want = Math.max(1, want);
        if (!GrandExchange.isOpen() || !shouldProceed(proceed)) {
            return false;
        }
        if (verifyGeOfferQuantity(want)) {
            return true;
        }
        if (isStandardGeQuantityPreset(want) && tryClickGeQuantityPreset(want)) {
            sleepQuiet(200, 360, proceed);
            if (verifyGeOfferQuantity(want)) {
                return true;
            }
        }
        return typeIntoGeAmountPrompt(want, false, proceed);
    }

    public static boolean trySetGeOfferPrice(int price) {
        return tryTypeGePriceViaWidgets(price, null);
    }

    public static boolean tryTypeGePriceViaWidgets(int targetPrice, BooleanSupplier proceed) {
        if (targetPrice <= 0 || !GrandExchange.isOpen() || !shouldProceed(proceed)) {
            return false;
        }
        return typeIntoGeAmountPrompt(targetPrice, true, proceed);
    }

    /**
     * Enter price/qty opent chatbox {@code Enter amount:} (162,43). Pas daarna typen —
     * anders gaat het bedrag naar de game-chat.
     */
    private static boolean typeIntoGeAmountPrompt(int value, boolean price, BooleanSupplier proceed) {
        if (price) {
            focusGePriceFieldForEdit();
        } else {
            focusGeQuantityFieldForEdit();
        }
        if (!waitForGeAmountPrompt(AMOUNT_PROMPT_WAIT_MS, proceed)) {
            geLog((price ? "Enter price" : "Enter quantity") + " — prompt niet open, retry");
            if (price) {
                focusGePriceFieldForEdit();
            } else {
                focusGeQuantityFieldForEdit();
            }
            if (!waitForGeAmountPrompt(AMOUNT_PROMPT_WAIT_MS, proceed)) {
                geLog("Enter amount niet open — niet in chat typen");
                return false;
            }
        }
        geLog("Enter amount open — type " + (price ? "prijs " : "qty ") + value);
        clearNumericFieldBestEffort(price ? CLEAR_PRICE_BACKSPACE_COUNT : 8);
        typeAmountDigits(String.valueOf(value));
        sleepQuiet(80, 160, proceed);
        Keyboard.pressKey(KeyEvent.VK_ENTER);
        sleepQuiet(280, 480, proceed);
        if (price) {
            int now = safeGePrice();
            return now == value || now <= 0;
        }
        return verifyGeOfferQuantity(value) || safeGeQuantity() <= 0;
    }

    private static boolean waitForGeAmountPrompt(int timeoutMs, BooleanSupplier proceed) {
        long deadline = System.currentTimeMillis() + Math.max(200, timeoutMs);
        while (System.currentTimeMillis() < deadline && shouldProceed(proceed)) {
            if (isGeAmountPromptVisible()) {
                return true;
            }
            BotRuntime.heartbeat();
            Sleep.sleep(80, 140);
        }
        return isGeAmountPromptVisible();
    }

    static boolean isGeAmountPromptVisible() {
        try {
            if (BankWithdrawHelper.isEnterAmountPromptVisible()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            IWidget prompt = Widgets.get(GeOffersClick.CHAT_GROUP, 43);
            if (isAmountPromptText(prompt)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (String needle : new String[]{"enter amount", "set a price", "how many do you wish"}) {
                List<IWidget> hits = Widgets.getAll(needle);
                if (hits == null) {
                    continue;
                }
                for (IWidget w : hits) {
                    if (isAmountPromptText(w)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isAmountPromptText(IWidget w) {
        if (w == null || w.isHidden()) {
            return false;
        }
        String t = clean(w.getText()).toLowerCase(Locale.ROOT);
        if (t.contains("what would you like to buy")) {
            return false;
        }
        return t.contains("enter amount")
                || t.contains("set a price")
                || t.contains("how many do you wish")
                || t.contains("price for each");
    }

    private static void typeAmountDigits(String digits) {
        if (digits == null || digits.isEmpty()) {
            return;
        }
        for (int i = 0; i < digits.length(); i++) {
            char ch = digits.charAt(i);
            if (!Character.isDigit(ch)) {
                continue;
            }
            Keyboard.typeKey(ch);
            Sleep.sleep(AMOUNT_DIGIT_MIN_MS, AMOUNT_DIGIT_MAX_MS);
            BotRuntime.heartbeat();
        }
    }

    public static boolean verifyGeOfferQuantity(int want) {
        int current = safeGeQuantity();
        return current == want || current <= 0;
    }

    public static int safeGeQuantity() {
        try {
            int q = GrandExchange.getQuantity();
            return Math.max(0, q);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int safeGePrice() {
        try {
            return Math.max(0, GrandExchange.getPrice());
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean clickGeStandardCollectButton() {
        IWidget details = Widgets.get(465, 24);
        if (details != null && !details.isHidden() && !GeOffersClick.isRepeatOrHintWidget(details)
                && !GeOffersClick.isSearchPromptWidget(details)) {
            for (String a : new String[]{"Collect-items", "Collect to inventory", "Collect to bank", "Collect"}) {
                if (details.hasAction(a) && details.interact(a)) {
                    geLog("Collect-items iface=465,24");
                    sleepQuiet(240, 420, null);
                    return true;
                }
            }
        }
        return false;
    }

    public static void typeDigitsHumanLike(String digits) {
        if (digits == null || digits.isEmpty()) {
            return;
        }
        for (int i = 0; i < digits.length(); i++) {
            char ch = digits.charAt(i);
            if (!Character.isDigit(ch)) {
                continue;
            }
            Keyboard.typeKey(ch);
            Sleep.sleep(24, 60);
            BotRuntime.heartbeat();
            if (i > 0 && i % 3 == 0) {
                Sleep.sleep(30, 90);
            }
        }
    }

    public static void typeItemNameLetterByLetter(String itemName) {
        typeItemNameLetterByLetter(itemName, null);
    }

    public static void typeItemNameLetterByLetter(String itemName, BooleanSupplier proceed) {
        if (itemName == null || itemName.isEmpty()) {
            return;
        }
        geLog("type \"" + itemName + "\"");
        for (int i = 0; i < itemName.length() && shouldProceed(proceed); i++) {
            Keyboard.typeKey(itemName.charAt(i));
            Sleep.sleep(TYPING_DELAY_MIN_MS, TYPING_DELAY_MAX_MS);
            BotRuntime.heartbeat();
        }
    }

    public static boolean focusGeSearchFieldBeforeTyping() {
        if (!GrandExchange.isOpen()) {
            return false;
        }
        if (GrandExchange.isSearchingItem()) {
            return true;
        }
        return false;
    }

    public static void clearGeSearchFieldCompletely(BooleanSupplier proceed) {
        for (int i = 0; i < CLEAR_BACKSPACE_COUNT && shouldProceed(proceed); i++) {
            Keyboard.pressKey(KeyEvent.VK_BACK_SPACE);
            Sleep.sleep(12, 28);
            if (i % 8 == 0) {
                BotRuntime.heartbeat();
            }
        }
        sleepQuiet(60, 120, proceed);
    }

    public static boolean matchesExactGeItemName(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return normalizeGeItemName(expected).equals(normalizeGeItemName(actual));
    }

    public static String normalizeGeItemName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("<[^>]*>", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("`", "")
                .replace("’", "");
    }

    private static String geSearchQuery(String exactItemName) {
        return exactItemName == null ? "" : exactItemName.trim();
    }

    private static String readGeOfferSetupItemName() {
        try {
            String selected = GrandExchange.getItemName();
            return selected != null ? selected.trim() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean hasWrongGeOfferSetupItem(String exactItemName) {
        try {
            if (GrandExchange.isSearchingItem()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        String selected = readGeOfferSetupItemName();
        if (selected.isEmpty() || GrandExchange.isPlaceholderItemName(selected)) {
            return false;
        }
        return !matchesExactGeItemName(exactItemName, selected);
    }

    private static boolean isGeSearchActive(BooleanSupplier proceed) {
        return shouldProceed(proceed) && GrandExchange.isSearchingItem();
    }

    private static boolean waitForGeOfferSetup(String exactItemName, BooleanSupplier proceed, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && shouldProceed(proceed)) {
            if (isGeOfferSetupForItem(exactItemName)) {
                return true;
            }
            sleepQuiet(120, 220, proceed);
        }
        return isGeOfferSetupForItem(exactItemName);
    }

    private static boolean ensureGeItemSearchOpen(BooleanSupplier proceed) {
        if (GrandExchange.isSearchingItem()) {
            return true;
        }
        GrandExchange.createBuyOffer();
        sleepQuiet(200, 360, proceed);
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline && shouldProceed(proceed)) {
            if (GrandExchange.isSearchingItem()) {
                return true;
            }
            sleepQuiet(150, 280, proceed);
        }
        if (GrandExchange.isSearchingItem()) {
            return true;
        }
        geLog("zoekscherm timeout — tweede Buy-klik");
        GrandExchange.createBuyOffer();
        sleepQuiet(350, 600, proceed);
        return GrandExchange.isSearchingItem();
    }

    private static boolean selectExactSearchResultAfterTyping(String exactItemName,
                                                             BooleanSupplier proceed,
                                                             long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && shouldProceed(proceed)) {
            if (isGeOfferSetupForItem(exactItemName)) {
                return true;
            }
            GrandExchange.GESearchResult match = findExactSdkSearchResult(exactItemName);
            if (match != null) {
                geLog("chooseOption '" + match.getItemName() + "' id=" + match.getItemID());
                try {
                    match.chooseOption();
                } catch (Throwable t) {
                    IWidget row = match.getMainWidget();
                    if (row != null) {
                        clickWidget(row);
                    }
                }
                sleepQuiet(380, 620, proceed);
                if (waitForSearchSelection(exactItemName, proceed)) {
                    return true;
                }
            }
            IWidget hit = findGeSearchResultWidget(exactItemName);
            if (hit != null) {
                logWidgetClick(hit, "search-widget '" + exactItemName + "'");
                if (clickWidget(hit) && waitForSearchSelection(exactItemName, proceed)) {
                    return true;
                }
            }
            sleepQuiet(280, 420, proceed);
        }
        return isGeOfferSetupForItem(exactItemName);
    }

    private static boolean waitForSearchSelection(String exactItemName, BooleanSupplier proceed) {
        long deadline = System.currentTimeMillis() + 2_200L;
        while (System.currentTimeMillis() < deadline && shouldProceed(proceed)) {
            try {
                if (!GrandExchange.isSearchingItem() && isGeOfferSetupForItem(exactItemName)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            sleepQuiet(100, 180, proceed);
        }
        return isGeOfferSetupForItem(exactItemName);
    }

    private static GrandExchange.GESearchResult findExactSdkSearchResult(String exactItemName) {
        try {
            List<GrandExchange.GESearchResult> results = GrandExchange.getSearchResults();
            if (results == null || results.isEmpty()) {
                return null;
            }
            for (GrandExchange.GESearchResult result : results) {
                if (result == null || result.getItemName() == null) {
                    continue;
                }
                if (isConfusableGeVariant(exactItemName, result.getItemName())) {
                    continue;
                }
                if (matchesExactGeItemName(exactItemName, result.getItemName())) {
                    return result;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static IWidget findGeSearchResultWidget(String exactItemName) {
        if (exactItemName == null || exactItemName.isEmpty()) {
            return null;
        }
        IWidget best = null;
        int bestArea = Integer.MAX_VALUE;
        for (int group : new int[]{162, 465}) {
            for (int child = GE_RESULT_CHILD_MIN; child <= GE_RESULT_CHILD_MAX; child++) {
                IWidget w = Widgets.get(group, child);
                IWidget match = pickGeSearchMatchFromNode(exactItemName, w);
                if (match == null) {
                    continue;
                }
                Rectangle b = match.getBounds();
                if (b == null) {
                    continue;
                }
                int area = Math.max(1, b.width) * Math.max(1, b.height);
                if (area < bestArea && b.width >= 16 && b.height >= 10) {
                    best = match;
                    bestArea = area;
                }
            }
        }
        return best;
    }

    private static IWidget pickGeSearchMatchFromNode(String exactItemName, IWidget w) {
        if (w == null || w.isHidden()) {
            return null;
        }
        if (widgetMatchesGeItem(exactItemName, w)) {
            return w;
        }
        try {
            IWidget[] kids = w.getChildren();
            if (kids != null) {
                for (IWidget ch : kids) {
                    if (ch != null && widgetMatchesGeItem(exactItemName, ch)) {
                        return ch;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean widgetMatchesGeItem(String exactItemName, IWidget w) {
        String name = clean(w.getName());
        String text = clean(w.getText());
        if (isConfusableGeVariant(exactItemName, name) || isConfusableGeVariant(exactItemName, text)) {
            return false;
        }
        return matchesExactGeItemName(exactItemName, name)
                || matchesExactGeItemName(exactItemName, text);
    }

    private static boolean isConfusableGeVariant(String exactItemName, String resultName) {
        String want = normalizeGeItemName(exactItemName);
        String got = normalizeGeItemName(resultName);
        if (want.equals("egg") && got.contains("easter")) {
            return true;
        }
        if (want.equals("cake") && got.contains("chocolate")) {
            return true;
        }
        if (want.equals("logs") && (got.contains("oak") || got.contains("willow") || got.contains("yew"))) {
            return true;
        }
        return false;
    }

    private static boolean isStandardGeQuantityPreset(int want) {
        return want == 1 || want == 10 || want == 100 || want == 1000;
    }

    private static boolean tryClickGeQuantityPreset(int want) {
        return GeOffersClick.quantityPreset(want) || clickGeWidgetByExactText(String.valueOf(want));
    }

    private static int parseActivelyTradedPriceFromWidget(IWidget w) {
        if (w == null) {
            return -1;
        }
        String blob = clean(w.getText()) + " " + clean(w.getName());
        Matcher m = ACTIVELY_TRADED_PRICE.matcher(blob);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static int walkActivelyTraded(IWidget w, int depth) {
        if (w == null || depth > 8) {
            return -1;
        }
        int p = parseActivelyTradedPriceFromWidget(w);
        if (p > 0) {
            return p;
        }
        try {
            IWidget[] kids = w.getChildren();
            if (kids != null) {
                for (IWidget ch : kids) {
                    int hit = walkActivelyTraded(ch, depth + 1);
                    if (hit > 0) {
                        return hit;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static void clearNumericFieldBestEffort(int backspaces) {
        for (int i = 0; i < backspaces; i++) {
            Keyboard.pressKey(KeyEvent.VK_BACK_SPACE);
            Sleep.sleep(12, 28);
        }
    }

    static boolean clickWidget(IWidget w) {
        if (w == null || w.isHidden()) {
            return false;
        }
        if (GeOffersClick.isSearchPromptWidget(w) || GeOffersClick.isRepeatOrHintWidget(w)) {
            geLog("geen klik op " + (GeOffersClick.isSearchPromptWidget(w)
                    ? "zoekprompt 162,44" : "Repeat/hint 465,6"));
            return false;
        }
        if (GeOffersClick.isDeadLabel(w)) {
            geLog("geen klik op label '" + clean(w.getText()) + "' iface="
                    + (w.getId() >>> 16) + "," + (w.getId() & 0xFFFF));
            return false;
        }
        Rectangle b = w.getBounds();
        if (b != null && b.width >= 2 && b.height >= 2) {
            int padX = Math.max(1, b.width / 6);
            int padY = Math.max(1, b.height / 6);
            int innerW = Math.max(1, b.width - 2 * padX);
            int innerH = Math.max(1, b.height - 2 * padY);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            int x = b.x + padX + r.nextInt(innerW);
            int y = b.y + padY + r.nextInt(innerH);
            if (MouseManager.interactAt(new Point(x, y), true)) {
                return true;
            }
        }
        try {
            if (w.hasAction("Select") && w.interact("Select")) {
                return true;
            }
            String name = clean(w.getName());
            if (!name.isEmpty() && w.hasAction(name) && w.interact(name)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static void logWidgetClick(IWidget w, String why) {
        if (w == null) {
            return;
        }
        geLog(why + " " + widgetIface(w));
    }

    private static String widgetIface(IWidget w) {
        try {
            int packed = w.getId();
            Rectangle b = w.getBounds();
            return "iface=" + (packed >>> 16) + "," + (packed & 0xFFFF)
                    + " packed=" + packed
                    + " name='" + clean(w.getName()) + "'"
                    + " text='" + clean(w.getText()) + "'"
                    + (b != null ? " " + b.width + "x" + b.height : "");
        } catch (Throwable t) {
            return "iface=?";
        }
    }

    private static IWidget findContainsText(IWidget w, String needleLower, int depth) {
        if (w == null || depth > 12 || w.isHidden()) {
            return null;
        }
        String t = clean(w.getText()).toLowerCase(Locale.ROOT);
        String n = clean(w.getName()).toLowerCase(Locale.ROOT);
        if ((t.contains(needleLower) || n.contains(needleLower))) {
            Rectangle b = w.getBounds();
            if (b != null && b.width > 1 && b.height > 1 && !GeOffersClick.isDeadLabel(w)) {
                return w;
            }
        }
        try {
            IWidget[] kids = w.getChildren();
            if (kids != null) {
                for (IWidget ch : kids) {
                    IWidget hit = findContainsText(ch, needleLower, depth + 1);
                    if (hit != null) {
                        return hit;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static IWidget findExactText(IWidget w, String want, int depth) {
        if (w == null || depth > 12 || w.isHidden()) {
            return null;
        }
        if (clean(w.getText()).equalsIgnoreCase(want) || clean(w.getName()).equalsIgnoreCase(want)) {
            Rectangle b = w.getBounds();
            if (b != null && b.width > 1 && b.height > 1 && !GeOffersClick.isDeadLabel(w)) {
                return w;
            }
        }
        try {
            IWidget[] kids = w.getChildren();
            if (kids != null) {
                for (IWidget ch : kids) {
                    IWidget hit = findExactText(ch, want, depth + 1);
                    if (hit != null) {
                        return hit;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean widgetTextVisible(String needle) {
        List<IWidget> all = Widgets.getAll(needle);
        for (IWidget w : all) {
            if (w != null && !w.isHidden()) {
                return true;
            }
        }
        return false;
    }

    private static boolean clickableTextContains(String needle) {
        return !Widgets.getAll(needle).isEmpty();
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<[^>]*>", "").trim();
    }

    private static void sleepQuiet(int minMs, int maxMs, BooleanSupplier proceed) {
        int lo = Math.min(minMs, maxMs);
        int hi = Math.max(minMs, maxMs);
        int total = lo >= hi ? lo : lo + ThreadLocalRandom.current().nextInt(Math.max(1, hi - lo + 1));
        long deadline = System.currentTimeMillis() + total;
        while (System.currentTimeMillis() < deadline) {
            if (!shouldProceed(proceed)) {
                return;
            }
            BotRuntime.heartbeat();
            Sleep.sleep(40, 80);
        }
    }

    private static boolean shouldProceed(BooleanSupplier proceed) {
        return proceed == null || proceed.getAsBoolean();
    }
}
