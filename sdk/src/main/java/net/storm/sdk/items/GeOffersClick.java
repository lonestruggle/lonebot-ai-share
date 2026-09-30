package net.storm.sdk.items;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.interact.MenuInteract;

import java.util.Locale;

/**
 * GE-widget atlas from LoneBot capture (alleen [widget]+[menu]).
 *
 * <pre>
 * HOME
 *   465,2  titel "Grand Exchange"
 *   465,6  GEEN Collect: hint "Select an offer slot…" (geen menu → klik = tegel)
 *          of "Repeat Offer" CC_OP p0=1 "Buy: 1 x …" — niet tijdens nieuwe koop
 *   467    GE-inventory (GeOffersSide.ITEMS) — Offer op item, niet backpack-tab
 *   465,7  slot 1 — LEGE: Buy-icoon p0=3 / Sell p0=4 (35×35)
 *           BEZET: tekst "Buy" 115×25 — View id=1 / Abort id=2 / Modify id=3, p0=2
 *           "Empty"/"Buy"/"Sell"-labels zijn GEEN knoppen
 *   465,8  slot 2 (zelfde)
 *   465,9  slot 3 (zelfde)
 *
 * ZOEKEN
 *   162,44  "What would you like to buy? *" — GEEN knop; alleen type-indicatie
 *   162,52  resultaten
 *
 * SETUP — nested CC_OP op packed 465,26
 *   p0=12 Enter price · p0=7 Enter quantity · p0=13 +5% · …
 *   465,26 tekst itemnaam / "Choose an item..." / "Buy offer" = GEEN knop
 *   465,27 examine / buy limit (geen naam)
 *   465,30 Confirm
 * </pre>
 */
final class GeOffersClick {

    static final int GE_GROUP = 465;
    static final int TITLE_CHILD = 2;
    static final int SLOT_0_CHILD = 7;
    static final int SLOT_1_CHILD = 8;
    static final int SLOT_2_CHILD = 9;
    static final int HINT_CHILD = 6;
    static final int SETUP_CHILD = 26;
    static final int SETUP_DESC_CHILD = 27;
    static final int CONFIRM_CHILD = 30;

    static final int CHAT_GROUP = 162;
    static final int SEARCH_PROMPT_CHILD = 44;
    static final int SEARCH_RESULTS_CHILD = 52;

    static final int SLOT_OCCUPIED_P0 = 2;
    static final int SLOT_BUY_P0 = 3;
    static final int SLOT_SELL_P0 = 4;
    static final int VIEW_OFFER_ID = 1;
    static final int ABORT_OFFER_ID = 2;
    static final int MODIFY_OFFER_ID = 3;

    static final int QTY_MINUS_1_P0 = 1;
    static final int QTY_PLUS_1_SMALL_P0 = 2;
    static final int QTY_PLUS_1_P0 = 3;
    static final int QTY_PLUS_10_P0 = 4;
    static final int QTY_PLUS_100_P0 = 5;
    static final int QTY_PLUS_1K_P0 = 6;
    static final int QTY_ENTER_P0 = 7;
    static final int QTY_ALL_IDENTIFIER = 2;
    static final int PRICE_MINUS_1_P0 = 8;
    static final int PRICE_PLUS_1_P0 = 9;
    static final int MINUS_FIVE_P0 = 10;
    static final int GUIDE_PRICE_P0 = 11;
    static final int PRICE_ENTER_P0 = 12;
    static final int PLUS_FIVE_P0 = 13;

    private GeOffersClick() {
    }

    static int setupPacked() {
        return InterfaceID.GeOffers.SETUP;
    }

    static int confirmPacked() {
        return InterfaceID.GeOffers.SETUP_CONFIRM;
    }

    static boolean ccOp(int packed, int p0, int identifier, String option) {
        GeHelper.geLog("CC_OP '" + option + "' p0=" + p0
                + " iface=" + (packed >>> 16) + "," + (packed & 0xFFFF)
                + " packed=" + packed + " id=" + identifier);
        return MenuInteract.invokeMenu(option, "", identifier, MenuAction.CC_OP.getId(), p0, packed, 0);
    }

    static boolean createBuyOnSlot(int packedSlotId) {
        return packedSlotId != 0 && ccOp(packedSlotId, SLOT_BUY_P0, 1, "Create Buy offer");
    }

    static boolean createSellOnSlot(int packedSlotId) {
        return packedSlotId != 0 && ccOp(packedSlotId, SLOT_SELL_P0, 1, "Create Sell offer");
    }

    /** Occupied slot: Abort offer (capture CC_OP id=2 p0=2). Not the "Buy" label. */
    static boolean abortOnSlot(int packedSlotId) {
        return packedSlotId != 0 && ccOp(packedSlotId, SLOT_OCCUPIED_P0, ABORT_OFFER_ID, "Abort offer");
    }

    static boolean enterQuantity() {
        return ccOp(setupPacked(), QTY_ENTER_P0, 1, "Enter quantity");
    }

    static boolean enterQuantityAll() {
        return ccOp(setupPacked(), QTY_ENTER_P0, QTY_ALL_IDENTIFIER, "All");
    }

    static boolean enterPrice() {
        return ccOp(setupPacked(), PRICE_ENTER_P0, 1, "Enter price");
    }

    static boolean plusFivePercent() {
        return ccOp(setupPacked(), PLUS_FIVE_P0, 1, "+5%");
    }

    static boolean guidePrice() {
        return ccOp(setupPacked(), GUIDE_PRICE_P0, 1, "Guide price");
    }

    static boolean confirmOffer() {
        return ccOp(confirmPacked(), 0, 1, "Confirm");
    }

    static boolean quantityPreset(int qty) {
        if (qty == 1) {
            return ccOp(setupPacked(), QTY_PLUS_1_P0, 1, "+1");
        }
        if (qty == 10) {
            return ccOp(setupPacked(), QTY_PLUS_10_P0, 1, "+10");
        }
        if (qty == 100) {
            return ccOp(setupPacked(), QTY_PLUS_100_P0, 1, "+100");
        }
        if (qty == 1000) {
            return ccOp(setupPacked(), QTY_PLUS_1K_P0, 1, "+1K");
        }
        return false;
    }

    static Widget searchPrompt(Client c) {
        return c == null ? null : c.getWidget(CHAT_GROUP, SEARCH_PROMPT_CHILD);
    }

    static Widget searchResults(Client c) {
        return c == null ? null : c.getWidget(CHAT_GROUP, SEARCH_RESULTS_CHILD);
    }

    static Widget setupDesc(Client c) {
        return c == null ? null : c.getWidget(GE_GROUP, SETUP_DESC_CHILD);
    }

    /** Client-thread. 162,44 "What would you like to buy?" */
    static boolean isSearchPromptVisible(Client c) {
        Widget w = searchPrompt(c);
        if (w == null) {
            return false;
        }
        try {
            if (w.isHidden()) {
                return false;
            }
            String t = w.getText();
            return t != null && t.toLowerCase().contains("what would you like to buy");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * New-offer setup: qty/prijs-knoppen + Confirm. Niet home (lege Buy-slot zichtbaar)
     * en niet de itemnaam-tekst op 465,26.
     */
    static boolean isOfferSetupVisible(Client c) {
        if (c == null) {
            return false;
        }
        Widget setup = c.getWidget(InterfaceID.GeOffers.SETUP);
        if (setup == null) {
            return false;
        }
        try {
            if (setup.isHidden()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        boolean qtyPrice = widgetHasActionDeep(setup, "Enter quantity", 0)
                || widgetHasActionDeep(setup, "Enter price", 0)
                || widgetHasActionDeep(setup, "+5%", 0);
        Widget confirm = c.getWidget(InterfaceID.GeOffers.SETUP_CONFIRM);
        try {
            if (!qtyPrice || confirm == null || confirm.isHidden()) {
                return false;
            }
            java.awt.Rectangle b = confirm.getBounds();
            return b != null && b.width > 12 && b.height > 10;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isHomeWithEmptyBuySlot(Client c) {
        if (c == null) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            Widget slot = c.getWidget(GE_GROUP, SLOT_0_CHILD + i);
            if (slotHasCreateBuy(slot)) {
                return true;
            }
        }
        return false;
    }

    static boolean isHomeSlotVisible(Client c) {
        return isHomeWithEmptyBuySlot(c);
    }

    static boolean slotHasCreateBuy(Widget slot) {
        if (slot == null) {
            return false;
        }
        try {
            if (slot.isHidden()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        return widgetHasActionDeep(slot, "Create Buy offer", 0);
    }

    static boolean slotIsOccupiedOffer(Widget slot) {
        if (slot == null) {
            return false;
        }
        try {
            if (slot.isHidden()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        return widgetHasActionDeep(slot, "View offer", 0)
                || widgetHasActionDeep(slot, "Abort offer", 0)
                || widgetHasActionDeep(slot, "Modify offer", 0);
    }

    /**
     * Echte Collect op <b>dit</b> widget (geen children). 465,6 Repeat/hint telt niet,
     * ook niet als een kind ergens Collect heeft.
     */
    static boolean isRealCollectWidget(Widget w) {
        return firstCollectAction(w) != null;
    }

    static boolean isRepeatOrHintWidget(IWidget w) {
        if (w == null) {
            return false;
        }
        try {
            int packed = w.getId();
            int group = packed >>> 16;
            int child = packed & 0xFFFF;
            String t = strip(w.getText()).toLowerCase(Locale.ROOT);
            String n = strip(w.getName()).toLowerCase(Locale.ROOT);
            if (group == GE_GROUP && child == HINT_CHILD) {
                return !w.hasAction("Collect") && !w.hasAction("Collect to inventory")
                        && !w.hasAction("Collect to bank") && !w.hasAction("Collect-items");
            }
            if (t.contains("select an offer slot") || t.equals("repeat offer")
                    || n.equals("repeat offer")) {
                return true;
            }
            return n.startsWith("buy:") || n.startsWith("sell:")
                    || t.startsWith("buy:") || t.startsWith("sell:");
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isRepeatOrHintWidget(Widget w) {
        if (w == null) {
            return false;
        }
        try {
            int packed = w.getId();
            int group = packed >>> 16;
            int child = packed & 0xFFFF;
            String t = strip(w.getText()).toLowerCase(Locale.ROOT);
            String n = strip(w.getName()).toLowerCase(Locale.ROOT);
            if (group == GE_GROUP && child == HINT_CHILD) {
                return firstCollectAction(w) == null;
            }
            if (t.contains("select an offer slot") || t.equals("repeat offer")
                    || n.equals("repeat offer")) {
                return true;
            }
            return looksLikeRepeatOfferAction(w);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isSearchPromptWidget(Widget w) {
        if (w == null) {
            return false;
        }
        try {
            int packed = w.getId();
            int group = packed >>> 16;
            int child = packed & 0xFFFF;
            if (group == CHAT_GROUP && child == SEARCH_PROMPT_CHILD) {
                return true;
            }
            String t = strip(w.getText()).toLowerCase(Locale.ROOT);
            return t.contains("what would you like to buy");
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isSearchPromptWidget(IWidget w) {
        if (w == null) {
            return false;
        }
        try {
            int packed = w.getId();
            int group = packed >>> 16;
            int child = packed & 0xFFFF;
            if (group == CHAT_GROUP && child == SEARCH_PROMPT_CHILD) {
                return true;
            }
            String t = strip(w.getText()).toLowerCase(Locale.ROOT);
            return t.contains("what would you like to buy");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Own actions only — Collect / Collect-items / Collect to inventory|bank. */
    static String firstCollectAction(Widget w) {
        if (w == null) {
            return null;
        }
        try {
            if (w.isHidden()) {
                return null;
            }
            String t = strip(w.getText()).toLowerCase(Locale.ROOT);
            String n = strip(w.getName()).toLowerCase(Locale.ROOT);
            if (t.contains("select an offer slot") || t.equals("repeat offer")
                    || n.equals("repeat offer") || t.contains("what would you like to buy")) {
                return null;
            }
            if (looksLikeRepeatOfferAction(w)) {
                return null;
            }
            String[] actions = w.getActions();
            if (actions == null) {
                return null;
            }
            for (String a : actions) {
                String s = strip(a).toLowerCase(Locale.ROOT);
                if (s.isEmpty() || s.equals("repeat offer") || s.startsWith("buy:")
                        || s.startsWith("sell:") || s.contains("create buy")
                        || s.contains("create sell") || s.contains("abort")
                        || s.contains("modify") || s.equals("view offer")) {
                    continue;
                }
                if (s.equals("collect") || s.equals("collect-items") || s.equals("collect items")
                        || s.equals("collect to inventory") || s.equals("collect to bank")
                        || s.startsWith("collect")) {
                    return strip(a);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Widget findRealCollectInTree(Widget w, int depth) {
        if (w == null || depth > 8) {
            return null;
        }
        try {
            if (w.isHidden()) {
                return null;
            }
            if (isRepeatOrHintWidget(w) || isSearchPromptWidget(w)) {
                Widget[] kids = allKids(w);
                for (Widget k : kids) {
                    Widget hit = findRealCollectInTree(k, depth + 1);
                    if (hit != null) {
                        return hit;
                    }
                }
                return null;
            }
            if (firstCollectAction(w) != null) {
                return w;
            }
            for (Widget k : allKids(w)) {
                Widget hit = findRealCollectInTree(k, depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static boolean viewOnSlot(int packedSlotId) {
        return packedSlotId != 0 && ccOp(packedSlotId, SLOT_OCCUPIED_P0, VIEW_OFFER_ID, "View offer");
    }

    private static boolean looksLikeRepeatOfferAction(Widget w) {
        try {
            String[] actions = w.getActions();
            if (actions == null) {
                return false;
            }
            for (String a : actions) {
                String s = strip(a).toLowerCase(Locale.ROOT);
                if (s.startsWith("buy:") || s.startsWith("sell:")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Widget[] allKids(Widget w) {
        java.util.ArrayList<Widget> out = new java.util.ArrayList<>();
        addKids(out, w.getChildren());
        try {
            addKids(out, w.getDynamicChildren());
        } catch (Throwable ignored) {
        }
        try {
            addKids(out, w.getStaticChildren());
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

    static String setupTitleItemName(Client c) {
        if (c == null || !isOfferSetupVisible(c)) {
            return "";
        }
        Widget setup = c.getWidget(GE_GROUP, SETUP_CHILD);
        if (setup == null) {
            return "";
        }
        try {
            if (setup.isHidden()) {
                return "";
            }
            String t = strip(setup.getText());
            return looksLikeItemTitle(t) ? t : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Slot-header "Buy"/"Empty", itemnaam op 465,26 — lezen mag, klikken niet.
     */
    static boolean isDeadLabel(IWidget w) {
        if (w == null) {
            return true;
        }
        String text = strip(w.getText());
        String low = text.toLowerCase(Locale.ROOT);
        if (isSearchPromptWidget(w) || isRepeatOrHintWidget(w)
                || low.contains("what would you like to buy")) {
            return true;
        }
        int packed = w.getId();
        int group = packed >>> 16;
        int child = packed & 0xFFFF;
        if (group == GE_GROUP && child == HINT_CHILD) {
            return !w.hasAction("Collect") && !w.hasAction("Collect to inventory");
        }
        if (group == GE_GROUP && child == SETUP_CHILD && looksLikeItemTitle(text)) {
            return true;
        }
        if (low.equals("empty") || low.equals("buy") || low.equals("sell")
                || low.equals("grand exchange") || low.equals("repeat offer")
                || low.equals("buy offer") || low.equals("sell offer")
                || low.contains("choose an item")
                || low.contains("select an offer slot")) {
            return true;
        }
        if (isGeButtonAction(w)) {
            return false;
        }
        return w.hasAction("View offer") || w.hasAction("Abort offer") || w.hasAction("Modify offer");
    }

    static String viewLabel(Client c) {
        if (isSearchPromptVisible(c)) {
            return "SEARCH 162,44";
        }
        if (isOfferSetupVisible(c)) {
            return "SETUP 465,26 qty/prijs";
        }
        if (isHomeSlotVisible(c)) {
            return "HOME lege Buy-slot";
        }
        return "GE";
    }

    private static boolean isGeButtonAction(IWidget w) {
        String[] buttons = {
                "Create Buy offer", "Create Sell offer", "Enter quantity", "Enter price",
                "+1", "+10", "+100", "+1K", "+5%", "-5%", "-1", "Guide price", "Confirm",
                "Collect", "Collect to inventory", "All", "Select", "Yes", "Cancel", "Customise"
        };
        for (String a : buttons) {
            if (w.hasAction(a)) {
                return true;
            }
        }
        return false;
    }

    static boolean looksLikeItemTitle(String t) {
        if (t == null || t.length() < 2) {
            return false;
        }
        String low = t.toLowerCase(Locale.ROOT);
        if (low.equals("empty") || low.equals("buy") || low.equals("sell")
                || low.equals("grand exchange") || low.contains("enter quantity")
                || low.contains("enter price") || low.contains("actively traded")
                || low.contains("buy limit") || low.startsWith("+") || low.startsWith("-")
                || low.contains("choose an item") || low.equals("buy offer")
                || low.equals("sell offer") || low.contains("select an offer")
                || low.equals("repeat offer")) {
            return false;
        }
        return true;
    }

    private static String strip(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<[^>]*>", "").trim();
    }

    private static boolean widgetHasActionDeep(Widget w, String want, int depth) {
        if (w == null || depth > 8) {
            return false;
        }
        try {
            if (w.isHidden()) {
                return false;
            }
            String[] actions = w.getActions();
            if (actions != null && want != null) {
                String n = want.toLowerCase();
                for (String a : actions) {
                    if (a != null && a.replaceAll("<[^>]*>", "").trim().equalsIgnoreCase(n)) {
                        return true;
                    }
                }
            }
            Widget[] kids = w.getChildren();
            if (kids != null) {
                for (Widget k : kids) {
                    if (widgetHasActionDeep(k, want, depth + 1)) {
                        return true;
                    }
                }
            }
            Widget[] dyn = w.getDynamicChildren();
            if (dyn != null) {
                for (Widget k : dyn) {
                    if (widgetHasActionDeep(k, want, depth + 1)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
