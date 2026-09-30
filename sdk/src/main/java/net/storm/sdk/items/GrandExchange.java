package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Point;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.items.GrandExchangeState;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Widgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Storm-compat Grand Exchange static API.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/items/GrandExchange.html">Storm GrandExchange</a>
 */
public final class GrandExchange {

    private static final Logger log = LoggerFactory.getLogger(GrandExchange.class);
    private static final int SLOT_COUNT_F2P = 3;
    private static final int SLOT_COUNT_MEMBERS = 8;
    /** CombatBot: Egg 162,52; search rows 18–65 on iface 162/465. */
    private static final int GE_SEARCH_CHILD_MIN = 18;
    private static final int GE_SEARCH_CHILD_MAX = 65;

    private GrandExchange() {
    }

    public static GrandExchangeState getView() {
        if (!isOpen()) {
            return GrandExchangeState.CLOSED;
        }
        if (isSearchingItem()) {
            return GrandExchangeState.SEARCHING;
        }
        if (isSelling()) {
            return GrandExchangeState.SELLING;
        }
        if (isBuying() || isSetupOpen()) {
            return GrandExchangeState.BUYING;
        }
        return GrandExchangeState.HOME;
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null && isOpenOnClient(c);
        }, false));
    }

    public static boolean isSetupOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || !isOpenOnClient(c)) {
                return false;
            }
            return GeOffersClick.isOfferSetupVisible(c);
        }, false));
    }

    public static boolean isBuying() {
        return isSetupOpen() && !isSelling();
    }

    public static boolean isSelling() {
        return isSellingText();
    }

    public static boolean isSearchingItem() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null && isOpenOnClient(c) && GeOffersClick.isSearchPromptVisible(c);
        }, false));
    }

    public static boolean isSearchResultsOpen() {
        List<GESearchResult> r = getSearchResults();
        return r != null && !r.isEmpty();
    }

    public static void open() {
        if (isOpen()) {
            return;
        }
        if (Bank.isOpen()) {
            Bank.close();
            Sleep.sleep(280, 480);
        }
        if (!GeRestockHelper.isCloseEnoughToOpenGe(GeRestockHelper.localPos())) {
            GeRestockHelper.approachGeForTrade();
            return;
        }
        GeRestockHelper.tryOpenGeInterfaceAtReach();
    }

    public static void openBank() {
        BankHelper.tryOpenBankAtGrandExchange();
    }

    public static void openItemSearch() {
        if (!isOpen()) {
            open();
        }
        if (!isSearchingItem()) {
            createBuyOffer();
        }
        GeHelper.focusGeSearchFieldBeforeTyping();
    }

    public static boolean createBuyOffer() {
        if (!isOpen()) {
            return false;
        }
        if (isSearchingItem()) {
            return true;
        }
        List<Integer> slots = Static.callOnClientThread(() -> emptySlotPackeds(Static.getClient()), null);
        if (slots == null || slots.isEmpty()) {
            GeHelper.geLog("geen lege Buy-slot (iface 465 INDEX_0..7)");
            return false;
        }
        for (int packed : slots) {
            boolean clicked = GeOffersClick.createBuyOnSlot(packed);
            if (!clicked) {
                Point icon = Static.callOnClientThread(
                        () -> buyIconClickPoint(Static.getClient(), packed), null);
                clicked = icon != null && MouseManager.interactAt(icon, true);
            }
            if (!clicked) {
                continue;
            }
            long deadline = System.currentTimeMillis() + 1_800L;
            while (System.currentTimeMillis() < deadline) {
                BotRuntime.heartbeat();
                if (isSearchingItem()) {
                    GeHelper.geLog("zoekscherm open iface=162,44");
                    return true;
                }
                Sleep.sleep(120, 200);
            }
        }
        GeHelper.geLog("na Buy CC_OP nog geen 162,44 searching=" + isSearchingItem()
                + " setup=" + isSetupOpen());
        return isSearchingItem();
    }

    /** Capture: Create Sell offer CC_OP p0=4 on empty slot, then Offer on GE-inv (467). */
    public static boolean createSellOffer() {
        if (!isOpen()) {
            return false;
        }
        if (isSelling() || isSetupOpen()) {
            return true;
        }
        List<Integer> slots = Static.callOnClientThread(() -> emptySlotPackeds(Static.getClient()), null);
        if (slots == null || slots.isEmpty()) {
            GeHelper.geLog("geen lege Sell-slot (Create Sell p0=4)");
            return false;
        }
        for (int packed : slots) {
            if (!GeOffersClick.createSellOnSlot(packed)) {
                continue;
            }
            long deadline = System.currentTimeMillis() + 1_800L;
            while (System.currentTimeMillis() < deadline) {
                BotRuntime.heartbeat();
                if (isSelling() || isSetupOpen()) {
                    GeHelper.geLog("sell-setup open na Create Sell offer");
                    return true;
                }
                Sleep.sleep(120, 200);
            }
        }
        GeHelper.geLog("na Sell CC_OP nog geen setup selling=" + isSelling() + " setup=" + isSetupOpen());
        return isSelling() || isSetupOpen();
    }

    public static boolean setQuantity(int qty) {
        if (qty <= 0 || !isOpen()) {
            return false;
        }
        return GeHelper.trySetGeOfferQuantityRobust(qty);
    }

    public static boolean setPrice(int price) {
        if (price <= 0 || !isOpen()) {
            return false;
        }
        return GeHelper.trySetGeOfferPrice(price);
    }

    public static void setItem(String name) {
        GeHelper.ensureGeItemSelectedForBuy(name);
    }

    public static void setItem(int id) {
        setItem(itemNameOf(id));
    }

    public static String getItemName() {
        if (isSearchingItem()) {
            return "";
        }
        String fromSetup = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? readSetupItemName(c) : "";
        }, "");
        if (fromSetup != null && !isPlaceholderItemName(fromSetup)) {
            return fromSetup.trim();
        }
        int id = getItemId();
        if (id > 0) {
            String fromId = itemNameOf(id);
            if (!isPlaceholderItemName(fromId)) {
                return fromId.trim();
            }
        }
        return "";
    }

    public static int getItemId() {
        if (isSearchingItem()) {
            return -1;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return -1;
            }
            try {
                Widget setup = c.getWidget(InterfaceID.GeOffers.SETUP);
                if (!GeOffersClick.isOfferSetupVisible(c)) {
                    return -1;
                }
                int id = firstRealItemId(setup, 0);
                return id > 0 ? id : -1;
            } catch (Throwable t) {
                return -1;
            }
        }, -1);
    }

    public static int getPrice() {
        return Static.callOnClientThread(() -> parseSetupNumber(Static.getClient(), true), 0);
    }

    public static int getQuantity() {
        return Static.callOnClientThread(() -> parseSetupNumber(Static.getClient(), false), 0);
    }

    public static int getGuidePrice() {
        return GeHelper.readActivelyTradedPrice();
    }

    public static boolean confirm() {
        if (!isOpen()) {
            return false;
        }
        Point confirm = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                Widget w = c.getWidget(InterfaceID.GeOffers.SETUP_CONFIRM);
                if (w != null && !w.isHidden()) {
                    return ClickPoints.forWidgetOnClient(w);
                }
            } catch (Throwable ignored) {
            }
            return null;
        }, null);
        if (confirm != null && MouseManager.interactAt(confirm, true)) {
            GeHelper.geLog("klik Confirm iface=465," + GeOffersClick.CONFIRM_CHILD);
            Sleep.sleep(220, 400);
            GeHelper.dismissHighPriceWarning();
            return true;
        }
        if (GeOffersClick.confirmOffer() || GeHelper.clickGeWidgetByTextContains("Confirm")) {
            Sleep.sleep(220, 400);
            GeHelper.dismissHighPriceWarning();
            return true;
        }
        return false;
    }

    public static boolean canCollect() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || !isOpenOnClient(c)) {
                return false;
            }
            for (GrandExchangeOffer o : offersOnClient(c)) {
                if (o != null && isCompletedOffer(o.getState())) {
                    return true;
                }
            }
            return false;
        }, false));
    }

    public static void collect() {
        collect(false);
    }

    public static boolean collect(boolean toBank) {
        if (!isOpen()) {
            return false;
        }
        if (invokeRealCollectWidget(toBank)) {
            Sleep.sleep(280, 480);
            return true;
        }
        if (viewCompletedSlotThenCollect(toBank)) {
            Sleep.sleep(280, 480);
            return true;
        }
        if (hasCompletedOffer()) {
            GeHelper.geLog("geen Collect (465,6 Repeat/hint — slot/details)");
        }
        return false;
    }

    public static boolean hasCompletedOffer() {
        for (GrandExchangeOffer o : getOffers()) {
            if (o != null && isCompletedOffer(o.getState())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCompletedOffer(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return hasCompletedOffer();
        }
        String want = GeHelper.normalizeGeItemName(itemName);
        for (GrandExchangeOffer o : getOffers()) {
            if (o == null || !isCompletedOffer(o.getState())) {
                continue;
            }
            String n = itemNameOf(o.getItemId());
            if (n != null && GeHelper.normalizeGeItemName(n).equals(want)) {
                return true;
            }
        }
        return false;
    }

    /** Repeat-SETUP of leftover qty/prijs — Back/Esc naar home, daarna Create Buy. */
    public static boolean leaveOfferSetupToHome() {
        if (!isOpen() || isSearchingItem() || !isSetupOpen()) {
            return true;
        }
        GeHelper.geLog("setup sluiten (geen Repeat Offer) — Back/Esc naar home");
        IWidget back = Widgets.get(InterfaceID.GeOffers.BACK);
        if (back != null && !back.isHidden()) {
            if (back.hasAction("Back") && back.interact("Back")) {
                Sleep.sleep(260, 420);
            } else if (back.hasAction("Close") && back.interact("Close")) {
                Sleep.sleep(260, 420);
            }
        }
        for (int i = 0; i < 3 && isSetupOpen(); i++) {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
            Sleep.sleep(220, 380);
            BotRuntime.heartbeat();
        }
        return !isSetupOpen();
    }

    public static int getEmptySlots() {
        int limit = slotLimit();
        int used = 0;
        for (GrandExchangeOffer o : getOffers()) {
            if (o != null && o.getState() != GrandExchangeOfferState.EMPTY) {
                used++;
            }
        }
        return Math.max(0, limit - used);
    }

    public static boolean isFull() {
        return getEmptySlots() <= 0;
    }

    public static boolean isEmpty() {
        for (GrandExchangeOffer o : getOffers()) {
            if (o != null && o.getState() != GrandExchangeOfferState.EMPTY) {
                return false;
            }
        }
        return true;
    }

    public static List<GrandExchangeOffer> getOffers() {
        List<GrandExchangeOffer> list = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return new ArrayList<GrandExchangeOffer>();
            }
            List<GrandExchangeOffer> out = new ArrayList<>();
            for (GrandExchangeOffer o : offersOnClient(c)) {
                if (o != null) {
                    out.add(o);
                }
            }
            return out;
        }, new ArrayList<>());
        return list != null ? list : new ArrayList<>();
    }

    public static List<GESearchResult> getSearchResults() {
        if (!isOpen()) {
            return Collections.emptyList();
        }
        List<GESearchResult> found = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            List<GESearchResult> out = new ArrayList<>();
            if (c == null) {
                return out;
            }
            collectSearchResults(GeOffersClick.searchResults(c), out, 0);
            if (out.isEmpty()) {
                collectSearchResults(c.getWidget(GeOffersClick.CHAT_GROUP, 0), out, 0);
            }
            if (out.isEmpty()) {
                collectSearchResults(c.getWidget(465, 0), out, 0);
            }
            return out;
        }, new ArrayList<>());
        return found != null ? found : Collections.emptyList();
    }

    public static void abortOffer(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return;
        }
        String want = GeHelper.normalizeGeItemName(itemName);
        for (GrandExchangeOffer o : getOffers()) {
            if (o == null || o.getState() == GrandExchangeOfferState.EMPTY) {
                continue;
            }
            String n = itemNameOf(o.getItemId());
            if (n != null && GeHelper.normalizeGeItemName(n).equals(want)) {
                abortOffer(o.getItemId());
                return;
            }
            if (n != null && GeHelper.normalizeGeItemName(n).contains(want)) {
                abortOffer(o.getItemId());
                return;
            }
        }
        GeHelper.geLog("abort: geen offer voor '" + itemName + "'");
    }

    public static void abortOffer(int itemId) {
        if (itemId <= 0) {
            return;
        }
        int idx = 0;
        for (GrandExchangeOffer o : getOffers()) {
            if (o != null && o.getItemId() == itemId && o.getState() != GrandExchangeOfferState.EMPTY) {
                final int slotIdx = idx;
                int packed = Static.callOnClientThread(() -> {
                    Client c = Static.getClient();
                    if (c == null || slotIdx < 0) {
                        return 0;
                    }
                    int[] indices = {
                            InterfaceID.GeOffers.INDEX_0, InterfaceID.GeOffers.INDEX_1, InterfaceID.GeOffers.INDEX_2,
                            InterfaceID.GeOffers.INDEX_3, InterfaceID.GeOffers.INDEX_4, InterfaceID.GeOffers.INDEX_5,
                            InterfaceID.GeOffers.INDEX_6, InterfaceID.GeOffers.INDEX_7
                    };
                    if (slotIdx >= indices.length) {
                        return 0;
                    }
                    Widget slotW = c.getWidget(indices[slotIdx]);
                    return slotW != null ? slotW.getId() : 0;
                }, 0);
                if (packed != 0 && GeOffersClick.abortOnSlot(packed)) {
                    Sleep.sleep(280, 450);
                }
                if (canCollect()) {
                    collect(false);
                }
                return;
            }
            idx++;
        }
    }

    public static boolean buy(String itemName, int quantity, int price) {
        return buy(itemName, quantity, price, false, false);
    }

    public static boolean buy(String itemName, int quantity, int price, boolean collect, boolean toBank) {
        return buy(itemName, quantity, price, collect, toBank, null);
    }

    public static boolean buy(String itemName, int quantity, int price, boolean collect, boolean toBank,
                              BooleanSupplier proceed) {
        return exchange(true, itemName, quantity, price, collect, toBank, proceed);
    }

    public static boolean buy(int itemId, int quantity, int price) {
        return buy(itemNameOf(itemId), quantity, price);
    }

    public static boolean buy(int itemId, int quantity, int price, boolean collect, boolean toBank) {
        return buy(itemNameOf(itemId), quantity, price, collect, toBank);
    }

    public static void sell(String... names) {
        if (names == null || names.length == 0) {
            return;
        }
        if (!isOpen()) {
            open();
            Sleep.sleep(280, 480);
        }
        if (!isOpen()) {
            GeHelper.geLog("sell: GE niet open");
            return;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            IInventoryItem item = Inventory.getFirst(name.trim());
            if (item == null) {
                GeHelper.geLog("sell: '" + name + "' niet in inventory");
                continue;
            }
            GeHelper.geLog("sell item '" + item.getName() + "' slot=" + item.getSlot()
                    + " noted=" + item.isNoted()
                    + " Offer=" + item.hasAction("Offer"));
            if (!isSelling() && !GeHelper.isGeOfferSetupForItem(name.trim())) {
                createSellOffer();
                Sleep.sleep(280, 450);
            }
            boolean offered = offerInventoryItem(item);
            GeHelper.geLog("sell Offer → " + offered + " setup=" + isSetupOpen()
                    + " selling=" + isSelling()
                    + " name='" + getItemName() + "'");
            if (offered) {
                Sleep.sleep(380, 620);
                return;
            }
        }
    }

    private static boolean offerInventoryItem(IInventoryItem item) {
        if (item == null) {
            return false;
        }
        if (item.hasAction("Offer") && item.interact("Offer")) {
            return true;
        }
        if (item.interact("Sell")) {
            return true;
        }
        if (item.hasAction("Offer-All") && item.interact("Offer-All")) {
            return true;
        }
        return false;
    }

    public static void sell(int... ids) {
        if (ids == null) {
            return;
        }
        for (int id : ids) {
            IInventoryItem item = Inventory.getFirst(id);
            if (item != null) {
                sell(item.getName());
                return;
            }
        }
    }

    public static void sell(Predicate<IInventoryItem> filter) {
        IInventoryItem item = Inventory.getFirst(filter);
        if (item != null && item.getName() != null) {
            sell(item.getName());
        }
    }

    public static boolean sell(String itemName, int quantity, int price) {
        return GeRestockHelper.placeSellOfferRobust(itemName, quantity, price, null);
    }

    public static boolean sell(int itemId, int quantity, int price) {
        return sell(itemNameOf(itemId), quantity, price);
    }

    public static boolean sell(int itemId, int price, boolean collect, boolean toBank) {
        IInventoryItem item = Inventory.getFirst(itemId);
        int qty = item != null ? Math.max(1, item.getQuantity()) : 1;
        boolean ok = sell(itemId, qty, price);
        if (ok && collect) {
            Sleep.sleep(600, 1100);
            if (canCollect()) {
                collect(toBank);
            }
        }
        return ok;
    }

    public static boolean sell(int itemId, int quantity, int price, boolean collect, boolean toBank) {
        boolean ok = sell(itemId, quantity, price);
        if (ok && collect) {
            Sleep.sleep(600, 1100);
            if (canCollect()) {
                collect(toBank);
            }
        }
        return ok;
    }

    public static boolean exchange(boolean buy, int itemId, int quantity, int price) {
        return exchange(buy, itemNameOf(itemId), quantity, price, false, false);
    }

    public static boolean exchange(boolean buy, int itemId, int quantity, int price,
                                   boolean collect, boolean toBank) {
        return exchange(buy, itemNameOf(itemId), quantity, price, collect, toBank);
    }

    /**
     * Storm exchange: open GE, collect, select item, qty/price, confirm. Optionally collect.
     */
    public static boolean exchange(boolean buy, String itemName, int quantity, int price,
                                   boolean collectCompleted, boolean toBank) {
        return exchange(buy, itemName, quantity, price, collectCompleted, toBank, null);
    }

    public static boolean exchange(boolean buy, String itemName, int quantity, int price,
                                   boolean collectCompleted, boolean toBank, BooleanSupplier proceed) {
        if (itemName == null || itemName.isEmpty() || quantity <= 0 || price <= 0) {
            return false;
        }
        if (!isOpen()) {
            open();
            Sleep.sleep(350, 600);
        }
        if (isOpen()) {
            String view = Static.callOnClientThread(() -> {
                Client c = Static.getClient();
                return c != null ? GeOffersClick.viewLabel(c) : "?";
            }, "?");
            GeHelper.geLog("exchange " + (buy ? "buy" : "sell") + " '" + itemName + "' view=" + view);
        } else {
            log.warn("[GrandExchange] exchange — GE not open");
            BotRuntime.logConsole("[GE] niet open — eerst naar clerk");
            return false;
        }
        if (cancelled(proceed)) {
            return false;
        }
        if (hasCompletedOffer()) {
            collect(false);
            Sleep.sleep(240, 420);
        }
        if (buy) {
            if (isSetupOpen() && !isSearchingItem()) {
                leaveOfferSetupToHome();
                Sleep.sleep(220, 380);
            }
            boolean selected = false;
            for (int attempt = 1; attempt <= 2 && !selected; attempt++) {
                if (cancelled(proceed)) {
                    return false;
                }
                if (isSetupOpen() && !isSearchingItem()) {
                    leaveOfferSetupToHome();
                    Sleep.sleep(200, 340);
                }
                if (!isSearchingItem()) {
                    createBuyOffer();
                    Sleep.sleep(280, 480);
                }
                if (!isSearchingItem()) {
                    GeHelper.geLog("geen zoekprompt na Create Buy — geen Repeat");
                    continue;
                }
                GeHelper.geLog("zoekprompt 162,44 — typen, geen klik");
                selected = GeHelper.ensureGeItemSelectedForBuy(itemName, proceed);
                if (!selected) {
                    GeHelper.geLog("item '" + itemName + "' niet geselecteerd (poging " + attempt
                            + "/2) searching=" + isSearchingItem()
                            + " results=" + getSearchResults().size());
                    GeHelper.tryReturnToGeItemSearch(proceed);
                }
            }
            if (!selected) {
                log.warn("[GrandExchange] buy — item select failed for {}", itemName);
                BotRuntime.logConsole("[GE] item '" + itemName + "' niet geselecteerd");
                return false;
            }
        } else {
            sell(itemName);
            Sleep.sleep(380, 620);
            if (!GeHelper.isGeOfferSetupForItem(itemName)) {
                BotRuntime.logConsole("[GE] sell-setup niet open voor '" + itemName + "'");
                return false;
            }
        }
        if (cancelled(proceed)) {
            return false;
        }
        Sleep.sleep(280, 450);
        setQuantity(quantity);
        Sleep.sleep(200, 380);
        if (cancelled(proceed)) {
            return false;
        }
        setPrice(price);
        Sleep.sleep(220, 400);
        if (!GeHelper.verifyGeOfferQuantity(quantity)) {
            setQuantity(quantity);
            Sleep.sleep(200, 360);
        }
        if (cancelled(proceed)) {
            GeHelper.geLog("pauze/stop — geen Confirm");
            return false;
        }
        boolean ok = confirm();
        Sleep.sleep(700, 1200);
        GeHelper.dismissGeHighPriceWarningAfterConfirm(proceed);
        if (ok && collectCompleted) {
            long deadline = System.currentTimeMillis() + 8_000L;
            int collects = 0;
            while (System.currentTimeMillis() < deadline && collects < 2 && !cancelled(proceed)) {
                BotRuntime.heartbeat();
                if (hasCompletedOffer(itemName) || canCollect()) {
                    collect(toBank);
                    collects++;
                    Sleep.sleep(400, 700);
                    if (!canCollect()) {
                        break;
                    }
                } else if (collects > 0) {
                    break;
                } else {
                    Sleep.sleep(280, 450);
                }
            }
        }
        return ok;
    }

    private static boolean cancelled(BooleanSupplier proceed) {
        return proceed != null && !proceed.getAsBoolean();
    }

    private static boolean invokeRealCollectWidget(boolean toBank) {
        Widget hit = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget details = c.getWidget(InterfaceID.GeOffers.DETAILS_COLLECT);
            Widget fromDetails = GeOffersClick.findRealCollectInTree(details, 0);
            if (fromDetails != null) {
                return fromDetails;
            }
            int[] indices = {
                    InterfaceID.GeOffers.INDEX_0, InterfaceID.GeOffers.INDEX_1, InterfaceID.GeOffers.INDEX_2,
                    InterfaceID.GeOffers.INDEX_3, InterfaceID.GeOffers.INDEX_4, InterfaceID.GeOffers.INDEX_5,
                    InterfaceID.GeOffers.INDEX_6, InterfaceID.GeOffers.INDEX_7
            };
            for (int packed : indices) {
                Widget slot = c.getWidget(packed);
                Widget fromSlot = GeOffersClick.findRealCollectInTree(slot, 0);
                if (fromSlot != null) {
                    return fromSlot;
                }
            }
            Widget all = c.getWidget(InterfaceID.GeOffers.COLLECTALL);
            if (all != null && GeOffersClick.firstCollectAction(all) != null
                    && !GeOffersClick.isRepeatOrHintWidget(all)) {
                return all;
            }
            return null;
        }, null);
        if (hit == null) {
            return false;
        }
        String action = Static.callOnClientThread(() -> GeOffersClick.firstCollectAction(hit), null);
        if (action == null) {
            return false;
        }
        if (toBank && action.toLowerCase(Locale.ROOT).contains("inventory")) {
            String bank = Static.callOnClientThread(() -> {
                String[] acts = hit.getActions();
                if (acts == null) {
                    return null;
                }
                for (String a : acts) {
                    if (a != null && a.toLowerCase(Locale.ROOT).contains("bank")) {
                        return a.replaceAll("<[^>]*>", "").trim();
                    }
                }
                return null;
            }, null);
            if (bank != null) {
                action = bank;
            }
        }
        IWidget w = Widgets.wrap(hit);
        if (w != null && w.interact(action)) {
            GeHelper.geLog("Collect '" + action + "' iface="
                    + (hit.getId() >>> 16) + "," + (hit.getId() & 0xFFFF));
            return true;
        }
        return false;
    }

    private static boolean viewCompletedSlotThenCollect(boolean toBank) {
        int idx = 0;
        for (GrandExchangeOffer o : getOffers()) {
            if (o != null && isCompletedOffer(o.getState())) {
                final int slotIdx = idx;
                int packed = Static.callOnClientThread(() -> {
                    Client c = Static.getClient();
                    if (c == null) {
                        return 0;
                    }
                    int[] indices = {
                            InterfaceID.GeOffers.INDEX_0, InterfaceID.GeOffers.INDEX_1, InterfaceID.GeOffers.INDEX_2,
                            InterfaceID.GeOffers.INDEX_3, InterfaceID.GeOffers.INDEX_4, InterfaceID.GeOffers.INDEX_5,
                            InterfaceID.GeOffers.INDEX_6, InterfaceID.GeOffers.INDEX_7
                    };
                    if (slotIdx < 0 || slotIdx >= indices.length) {
                        return 0;
                    }
                    Widget slotW = c.getWidget(indices[slotIdx]);
                    return slotW != null ? slotW.getId() : 0;
                }, 0);
                if (packed != 0 && GeOffersClick.viewOnSlot(packed)) {
                    GeHelper.geLog("View offer slot " + slotIdx + " → Collect-items");
                    Sleep.sleep(280, 450);
                    return invokeRealCollectWidget(toBank);
                }
            }
            idx++;
        }
        return false;
    }

    static boolean isOpenOnClient(Client c) {
        Widget w = c.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (w != null && !w.isHidden()) {
            return true;
        }
        try {
            Widget universe = c.getWidget(InterfaceID.GeOffers.UNIVERSE);
            return universe != null && !universe.isHidden();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isSellingText() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null && isOpenOnClient(c)
                    && (widgetTreeContains(c, "sell offer") || widgetTreeContains(c, "how many do you wish to sell"));
        }, false));
    }

    private static List<Integer> emptySlotPackeds(Client c) {
        List<Integer> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        int[] indices = {
                InterfaceID.GeOffers.INDEX_0, InterfaceID.GeOffers.INDEX_1, InterfaceID.GeOffers.INDEX_2,
                InterfaceID.GeOffers.INDEX_3, InterfaceID.GeOffers.INDEX_4, InterfaceID.GeOffers.INDEX_5,
                InterfaceID.GeOffers.INDEX_6, InterfaceID.GeOffers.INDEX_7
        };
        GrandExchangeOffer[] offers = offersOnClient(c);
        int limit = Math.min(indices.length, slotLimitOnClient(c));
        for (int i = 0; i < limit; i++) {
            if (i < offers.length && offers[i] != null
                    && offers[i].getState() != GrandExchangeOfferState.EMPTY) {
                continue;
            }
            Widget slotW = c.getWidget(indices[i]);
            if (slotW == null || slotW.isHidden()) {
                continue;
            }
            if (GeOffersClick.slotIsOccupiedOffer(slotW)) {
                GeHelper.geLog("slot iface=465," + (GeOffersClick.SLOT_0_CHILD + i)
                        + " bezet (View/Abort/Modify) — skip");
                continue;
            }
            if (!GeOffersClick.slotHasCreateBuy(slotW)) {
                continue;
            }
            out.add(slotW.getId());
        }
        return out;
    }

    /** 35×35 Buy icon (capture canvas ~37,193) — nested child p0=3, not the Empty label. */
    private static Point buyIconClickPoint(Client c, int packedSlot) {
        if (c == null) {
            return null;
        }
        Widget slotW = c.getWidget(packedSlot);
        if (slotW == null) {
            return null;
        }
        Widget buy = findBuyIconChild(slotW, 0);
        if (buy != null) {
            return ClickPoints.forWidgetOnClient(buy);
        }
        return null;
    }

    private static Widget findBuyIconChild(Widget w, int depth) {
        if (w == null || depth > 6 || w.isHidden()) {
            return null;
        }
        if (w.getIndex() == GeOffersClick.SLOT_BUY_P0 && widgetHasAction(w, "Create Buy offer")) {
            return w;
        }
        if (widgetHasAction(w, "Create Buy offer") && !widgetHasAction(w, "Create Sell offer")) {
            java.awt.Rectangle b = w.getBounds();
            if (b != null && b.width <= 50 && b.height <= 50) {
                return w;
            }
        }
        for (Widget k : allWidgetKids(w)) {
            Widget hit = findBuyIconChild(k, depth + 1);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static boolean widgetHasAction(Widget w, String want) {
        try {
            String[] actions = w.getActions();
            if (actions == null || want == null) {
                return false;
            }
            String n = want.toLowerCase(Locale.ROOT);
            for (String a : actions) {
                if (a != null && stripTags(a).toLowerCase(Locale.ROOT).equals(n)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Widget[] allWidgetKids(Widget w) {
        java.util.ArrayList<Widget> out = new java.util.ArrayList<>();
        addKids(out, w.getChildren());
        try {
            addKids(out, w.getStaticChildren());
        } catch (Throwable ignored) {
        }
        try {
            addKids(out, w.getDynamicChildren());
        } catch (Throwable ignored) {
        }
        return out.toArray(new Widget[0]);
    }

    private static void addKids(java.util.ArrayList<Widget> out, Widget[] kids) {
        if (kids == null) {
            return;
        }
        for (Widget k : kids) {
            if (k != null) {
                out.add(k);
            }
        }
    }

    private static int slotLimitOnClient(Client c) {
        try {
            EnumSet<WorldType> types = c.getWorldType();
            return types != null && types.contains(WorldType.MEMBERS)
                    ? SLOT_COUNT_MEMBERS : SLOT_COUNT_F2P;
        } catch (Throwable t) {
            return SLOT_COUNT_F2P;
        }
    }

    private static void clickSlotIndex(int idx) {
        int[] indices = {
                InterfaceID.GeOffers.INDEX_0, InterfaceID.GeOffers.INDEX_1, InterfaceID.GeOffers.INDEX_2,
                InterfaceID.GeOffers.INDEX_3, InterfaceID.GeOffers.INDEX_4, InterfaceID.GeOffers.INDEX_5,
                InterfaceID.GeOffers.INDEX_6, InterfaceID.GeOffers.INDEX_7
        };
        if (idx < 0 || idx >= indices.length) {
            return;
        }
        Point p = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget slotW = c.getWidget(indices[idx]);
            return slotW != null ? ClickPoints.forWidgetOnClient(slotW) : null;
        }, null);
        if (p != null) {
            MouseManager.interactAt(p);
        }
    }

    private static GrandExchangeOffer[] offersOnClient(Client c) {
        try {
            GrandExchangeOffer[] arr = c.getGrandExchangeOffers();
            return arr != null ? arr : new GrandExchangeOffer[0];
        } catch (Throwable t) {
            return new GrandExchangeOffer[0];
        }
    }

    private static int slotLimit() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            EnumSet<WorldType> types = c.getWorldType();
            return types != null && types.contains(WorldType.MEMBERS);
        }, false)) ? SLOT_COUNT_MEMBERS : SLOT_COUNT_F2P;
    }

    static String itemNameOf(int itemId) {
        if (itemId <= 0) {
            return "";
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "";
            }
            try {
                ItemComposition def = c.getItemDefinition(itemId);
                String n = def != null && def.getName() != null ? def.getName() : "";
                return isPlaceholderItemName(n) ? "" : n;
            } catch (Throwable t) {
                return "";
            }
        }, "");
    }

    private static String readSetupItemName(Client c) {
        try {
            if (!GeOffersClick.isOfferSetupVisible(c)) {
                return "";
            }
            String title = GeOffersClick.setupTitleItemName(c);
            if (title != null && !title.isEmpty() && GeOffersClick.looksLikeItemTitle(title)) {
                return title;
            }
            Widget setup = c.getWidget(InterfaceID.GeOffers.SETUP);
            String n = firstNamedItem(setup, 0);
            return n != null ? n : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static String firstNamedItem(Widget w, int depth) {
        if (w == null || depth > 8 || w.isHidden()) {
            return null;
        }
        if (w.getItemId() > 0) {
            String n = stripTags(w.getName());
            if (!isPlaceholderItemName(n)) {
                return n;
            }
            String fromDef = itemNameOf(w.getItemId());
            if (!isPlaceholderItemName(fromDef)) {
                return fromDef;
            }
        }
        Widget[] kids = w.getChildren();
        if (kids != null) {
            for (Widget k : kids) {
                String n = firstNamedItem(k, depth + 1);
                if (n != null) {
                    return n;
                }
            }
        }
        return null;
    }

    private static int firstRealItemId(Widget w, int depth) {
        if (w == null || depth > 8) {
            return -1;
        }
        int itemId = w.getItemId();
        if (itemId > 0 && !isPlaceholderItemName(stripTags(w.getName()))
                && !isPlaceholderItemName(itemNameOf(itemId))) {
            return itemId;
        }
        Widget[] kids = w.getChildren();
        if (kids != null) {
            for (Widget k : kids) {
                int id = firstRealItemId(k, depth + 1);
                if (id > 0) {
                    return id;
                }
            }
        }
        try {
            Widget[] dyn = w.getDynamicChildren();
            if (dyn != null) {
                for (Widget k : dyn) {
                    int id = firstRealItemId(k, depth + 1);
                    if (id > 0) {
                        return id;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    static boolean isPlaceholderItemName(String name) {
        if (name == null) {
            return true;
        }
        String n = stripTags(name).trim();
        if (n.isEmpty()) {
            return true;
        }
        String low = n.toLowerCase(Locale.ROOT);
        return low.equals("null") || low.startsWith("null ") || low.equals("(members)");
    }

    private static boolean isCompletedOffer(GrandExchangeOfferState state) {
        return state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.SOLD
                || state == GrandExchangeOfferState.CANCELLED_BUY
                || state == GrandExchangeOfferState.CANCELLED_SELL;
    }

    private static int parseSetupNumber(Client c, boolean price) {
        if (c == null) {
            return 0;
        }
        try {
            Widget setup = c.getWidget(InterfaceID.GeOffers.SETUP);
            int v = walkNumber(setup, price, 0);
            return Math.max(0, v);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int walkNumber(Widget w, boolean price, int depth) {
        if (w == null || depth > 10 || w.isHidden()) {
            return 0;
        }
        String blob = ((w.getText() != null ? w.getText() : "") + " "
                + (w.getName() != null ? w.getName() : "")).toLowerCase(Locale.ROOT);
        if ((price && blob.contains("coins")) || (!price && blob.contains("quantity"))) {
            String digits = (w.getText() != null ? w.getText() : "").replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    return Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String text = w.getText();
        if (text != null && text.matches("\\d+") && text.length() <= 10) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
            }
        }
        Widget[] kids = w.getChildren();
        if (kids != null) {
            for (Widget k : kids) {
                int v = walkNumber(k, price, depth + 1);
                if (v > 0) {
                    return v;
                }
            }
        }
        return 0;
    }

    private static void collectSearchResults(Widget w, List<GESearchResult> out, int depth) {
        if (w == null || depth > 10 || w.isHidden() || out.size() > 40) {
            return;
        }
        int itemId = w.getItemId();
        String name = stripTags(w.getName());
        if (name == null || name.isBlank()) {
            name = stripTags(w.getText());
        }
        if (isGeSearchPromptText(name)) {
            name = "";
        }
        int packed = w.getId();
        int group = packed >>> 16;
        int child = packed & 0xFFFF;
        int idx = w.getIndex();
        boolean resultBand = (group == 162 || group == 465)
                && ((child >= GE_SEARCH_CHILD_MIN && child <= GE_SEARCH_CHILD_MAX)
                || (idx >= GE_SEARCH_CHILD_MIN && idx <= GE_SEARCH_CHILD_MAX));
        boolean clickableRow = widgetHasAnyAction(w);
        if (name != null && !name.isBlank() && !name.matches("\\d+")
                && (itemId > 0 || resultBand || (clickableRow && name.length() >= 3))) {
            String key = GeHelper.normalizeGeItemName(name);
            boolean dup = false;
            for (GESearchResult existing : out) {
                if (existing != null && GeHelper.normalizeGeItemName(existing.getItemName()).equals(key)) {
                    dup = true;
                    break;
                }
            }
            if (!dup && !key.isEmpty()) {
                out.add(new GESearchResult(itemId, name, Widgets.wrap(w)));
            }
        }
        for (Widget k : allWidgetKids(w)) {
            collectSearchResults(k, out, depth + 1);
        }
    }

    private static boolean isGeSearchPromptText(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String low = name.toLowerCase(Locale.ROOT);
        return low.contains("what would you like")
                || low.contains("search for item")
                || low.contains("start typing")
                || low.contains("enter the name")
                || low.contains("grand exchange")
                || low.equals("buy")
                || low.equals("sell")
                || low.contains("how many")
                || low.contains("price per");
    }

    private static boolean widgetHasAnyAction(Widget w) {
        try {
            String[] actions = w.getActions();
            if (actions == null) {
                return false;
            }
            for (String a : actions) {
                if (a != null && !a.isBlank()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean widgetTreeContains(Client c, String needleLower) {
        if (needleLower == null) {
            return false;
        }
        String n = needleLower.toLowerCase(Locale.ROOT);
        Widget[] roots = c.getWidgetRoots();
        if (roots == null) {
            return false;
        }
        for (Widget root : roots) {
            if (containsText(root, n, 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsText(Widget w, String needleLower, int depth) {
        if (w == null || depth > 10 || w.isHidden()) {
            return false;
        }
        String t = w.getText();
        if (t != null && t.toLowerCase(Locale.ROOT).contains(needleLower)) {
            return true;
        }
        String name = w.getName();
        if (name != null && name.toLowerCase(Locale.ROOT).contains(needleLower)) {
            return true;
        }
        Widget[] children = w.getChildren();
        if (children != null) {
            for (Widget child : children) {
                if (containsText(child, needleLower, depth + 1)) {
                    return true;
                }
            }
        }
        try {
            Widget[] dyn = w.getDynamicChildren();
            if (dyn != null) {
                for (Widget child : dyn) {
                    if (containsText(child, needleLower, depth + 1)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String stripTags(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Storm search-result row. {@link #chooseOption()} selects it for the buy offer.
     */
    public static final class GESearchResult {
        private final int itemID;
        private final String itemName;
        private final IWidget mainWidget;

        public GESearchResult(int itemID, String itemName, IWidget mainWidget) {
            this.itemID = itemID;
            this.itemName = itemName != null ? itemName : "";
            this.mainWidget = mainWidget;
        }

        public int getItemID() {
            return itemID;
        }

        public String getItemName() {
            return itemName;
        }

        public IWidget getMainWidget() {
            return mainWidget;
        }

        public void chooseOption() {
            if (mainWidget == null) {
                return;
            }
            GeHelper.logWidgetClick(mainWidget, "search-row '" + itemName + "'");
            if (GeHelper.clickWidget(mainWidget)) {
                Sleep.sleep(220, 400);
                return;
            }
            if (mainWidget.hasAction(itemName) && mainWidget.interact(itemName)) {
                Sleep.sleep(220, 400);
                return;
            }
            if (mainWidget.hasAction("Select") && mainWidget.interact("Select")) {
                Sleep.sleep(220, 400);
            }
        }
    }
}
