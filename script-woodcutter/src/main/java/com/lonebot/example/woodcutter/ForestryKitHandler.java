package com.lonebot.example.woodcutter;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankSnapshot;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.widgets.Tabs;
import net.storm.api.widgets.Tab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.Locale;

/**
 * Forestry kit — CombatBot-stijl + directe Buy-1 menu-invoke op kit-widget.
 */
final class ForestryKitHandler {

    private static final Logger log = LoggerFactory.getLogger(ForestryKitHandler.class);

    static final WorldPoint FORESTER_ANCHOR = new WorldPoint(3096, 3238, 0);

    private static final long SHOP_SESSION_MS = 55_000L;
    private static final long SHOP_STEP_STUCK_MS = 12_000L;
    private static final long CLICK_COOLDOWN_MS = 450L;
    private static final long AFTER_KIT_CLICK_MS = 300L;
    private static final long AFTER_BUY_CLICK_MS = 1_000L;
    private static final long WAIT_BUY_RETRY_MS = 3_000L;

    private static final int MIN_TITLE_W = 80;
    private static final int MIN_TITLE_H = 10;
    private static final int MIN_KIT_W = 20;
    private static final int MIN_KIT_H = 20;
    private static final int MIN_BUY_W = 16;
    private static final int MIN_BUY_H = 12;

    private static final String KIT_DESC = "equipable kit bag";
    private static final String WELCOME = "welcome to the forestry shop";

    private enum ShopStep {
        WAIT_SHOP, CLICK_KIT, WAIT_SELECTED, WAIT_BUY, CLICK_BUY, VERIFY
    }

    private static long lastNpcMs;
    private static long lastClickMs;
    private static long shopSessionUntilMs;
    private static long shopStepStartedMs;
    private static long kitClickedMs;
    private static long buyClickedMs;
    private static long waitBuyStartedMs;
    private static long lastDebugMs;
    private static ShopStep shopStep = ShopStep.WAIT_SHOP;
    private static String lastStatus = "kit idle";
    /** Wear-pogingen zonder equipped — voorkomt View-spam. */
    private static int wearFailStreak;

    /** Per-account bark read (reset bij login-naam wissel). */
    private static String barkAccountKey = "";
    private static String barkSource = "?";
    private static int cachedBark = -1;
    private static long cachedBarkMs;
    private static final long BARK_CACHE_MS = 800L;

    private ForestryKitHandler() {
    }

    static String status() {
        return lastStatus;
    }

    /**
     * Live anima-infused bark — één client-thread dump (geen 3× 1.5s timeout → tick 2.4s).
     */
    static int animaBarkStored() {
        ensureBarkAccount();
        long now = System.currentTimeMillis();
        if (cachedBark >= 0 && now - cachedBarkMs < BARK_CACHE_MS) {
            return cachedBark;
        }
        int live = readBarkLiveOnce();
        cachedBark = live;
        cachedBarkMs = now;
        return live;
    }

    /** Debug: waar komt de bark-telling vandaan. */
    static String animaBarkSource() {
        ensureBarkAccount();
        return barkSource != null ? barkSource : "?";
    }

    static void onAnimaBarkAwarded(int amount) {
        if (amount <= 0) {
            return;
        }
        cachedBark = -1;
        cachedBarkMs = 0L;
        ensureBarkAccount();
        int live = animaBarkStored();
        WcDebug.log("kit", "bark award +" + amount + " live=" + live
                + " src=" + barkSource + " @" + barkAccountKey);
        BotRuntime.logConsole("[WC/kit] bark +" + amount + " → live " + live
                + " (" + barkSource + ") @" + barkAccountKey);
    }

    private static void ensureBarkAccount() {
        if (!barkAccountKey.isEmpty()) {
            return; // sticky per client-sessie — geen lege-naam flicker
        }
        String key = localAccountKeyOnce();
        if (!key.isEmpty()) {
            barkAccountKey = key;
        }
    }

    private static String localAccountKeyOnce() {
        try {
            String n = net.storm.sdk.game.Static.callOnClientThread(() -> {
                net.runelite.api.Client c = net.storm.sdk.game.Static.getClient();
                if (c == null || c.getLocalPlayer() == null) {
                    return "";
                }
                String name = c.getLocalPlayer().getName();
                return name != null ? name.replace('\u00a0', ' ').trim() : "";
            }, "", 100L);
            return n != null ? n : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Eén callOnClientThread: container 814 + kit qty inv/equip + losse bark.
     */
    private static int readBarkLiveOnce() {
        try {
            Integer n = net.storm.sdk.game.Static.callOnClientThread(() -> {
                net.runelite.api.Client c = net.storm.sdk.game.Static.getClient();
                if (c == null) {
                    return 0;
                }
                int container = -1;
                net.runelite.api.ItemContainer kitCont =
                        c.getItemContainer(ForestryIds.FORESTRY_KIT_CONTAINER_ID);
                if (kitCont != null) {
                    container = 0;
                    net.runelite.api.Item[] items = kitCont.getItems();
                    if (items != null) {
                        for (net.runelite.api.Item it : items) {
                            if (it != null && it.getId() == ForestryIds.ANIMA_BARK_ITEM_ID) {
                                container += Math.max(0, it.getQuantity());
                            }
                        }
                    }
                }
                int kitQty = 0;
                try {
                    net.runelite.api.ItemContainer eq =
                            c.getItemContainer(net.runelite.api.InventoryID.EQUIPMENT);
                    if (eq != null && eq.getItems() != null) {
                        for (net.runelite.api.Item it : eq.getItems()) {
                            if (it != null && ForestryIds.isForestryKitItemId(it.getId())
                                    && it.getQuantity() > kitQty) {
                                kitQty = it.getQuantity();
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
                try {
                    net.runelite.api.ItemContainer inv =
                            c.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
                    if (inv != null && inv.getItems() != null) {
                        for (net.runelite.api.Item it : inv.getItems()) {
                            if (it == null) {
                                continue;
                            }
                            if (ForestryIds.isForestryKitItemId(it.getId())
                                    && it.getQuantity() > kitQty) {
                                kitQty = it.getQuantity();
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
                if (kitQty <= 1) {
                    kitQty = 0;
                }
                int loose = 0;
                try {
                    net.runelite.api.ItemContainer inv =
                            c.getItemContainer(net.runelite.api.InventoryID.INVENTORY);
                    if (inv != null && inv.getItems() != null) {
                        for (net.runelite.api.Item it : inv.getItems()) {
                            if (it != null && it.getId() == ForestryIds.ANIMA_BARK_ITEM_ID) {
                                loose += Math.max(0, it.getQuantity());
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
                int best = 0;
                String src = "0";
                if (container >= 0) {
                    best = container;
                    src = "container";
                }
                if (kitQty > best) {
                    best = kitQty;
                    src = "kit-qty";
                }
                if (loose > best) {
                    best = loose;
                    src = "inv";
                }
                barkSource = src;
                return best;
            }, 0, 250L);
            return n != null ? n : 0;
        } catch (Throwable t) {
            barkSource = "err";
            return cachedBark >= 0 ? cachedBark : 0;
        }
    }

    static void reset() {
        clearShopSession();
        wearFailStreak = 0;
        lastStatus = "kit idle";
        barkAccountKey = "";
        barkSource = "reset";
        cachedBark = -1;
        cachedBarkMs = 0L;
    }

    static boolean inInventory() {
        try {
            return Inventory.contains(ForestryIds.FORESTRY_KIT_NAME)
                    || Inventory.contains(ForestryIds.FORESTRY_KIT_ITEM_ID)
                    || Inventory.contains(ForestryIds.FORESTRY_KIT_ITEM_ID_LEGACY)
                    || Inventory.getFirst(i -> i != null && isKitItem(i.getId(), i.getName())) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isEquipped() {
        try {
            return Equipment.contains(ForestryIds.FORESTRY_KIT_NAME)
                    || Equipment.contains(ForestryIds.FORESTRY_KIT_ITEM_ID)
                    || Equipment.contains(ForestryIds.FORESTRY_KIT_ITEM_ID_LEGACY)
                    || Equipment.contains(i -> i != null && isKitItem(i.getId(), i.getName()));
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean hasKitOnPerson() {
        return isEquipped() || inInventory();
    }

    static boolean kitInOpenBank() {
        try {
            return Bank.isOpen()
                    && (Bank.contains(ForestryIds.FORESTRY_KIT_NAME)
                    || Bank.contains(ForestryIds.FORESTRY_KIT_ITEM_ID)
                    || Bank.getFirst(i -> i != null && isKitItem(i.getId(), i.getName())) != null);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean kitKnownInBank() {
        if (kitInOpenBank()) {
            return true;
        }
        try {
            return BankSnapshot.contains(ForestryIds.FORESTRY_KIT_ITEM_ID)
                    || BankSnapshot.contains(ForestryIds.FORESTRY_KIT_ITEM_ID_LEGACY)
                    || BankSnapshot.contains(ForestryIds.FORESTRY_KIT_NAME);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean hasActiveShopSession() {
        return shopSessionUntilMs > System.currentTimeMillis() || isShopOpenOnScreen();
    }

    static int tryEquipFromInventory() {
        if (isEquipped()) {
            wearFailStreak = 0;
            lastStatus = "kit equipped";
            return 0;
        }
        IInventoryItem kit = findKitInInventory();
        if (kit == null) {
            wearFailStreak = 0;
            return 0;
        }
        // Shop open → eerst dicht, daarna Wear (anders faalt interact / inventory-focus)
        if (isShopOpenOnScreen()) {
            lastStatus = "shop sluiten…";
            closeShopUi();
            return Rand.nextInt(550, 950);
        }
        // Zonder open inv-tab: composition-index 1 = left-click = View (MES) i.p.v. Wear
        if (!Tabs.isOpen(Tab.INVENTORY)) {
            lastStatus = "inv-tab openen…";
            Tabs.open(Tab.INVENTORY);
            BotRuntime.logConsole("[ForestryKit] open Inventory (Wear≠View)");
            return Rand.nextInt(350, 650);
        }
        if (wearFailStreak >= 6) {
            lastStatus = "Wear stuck — skip";
            WcDebug.once("kit", "Wear faalt ×" + wearFailStreak
                    + " id=" + kit.getId() + " slot=" + kit.getSlot()
                    + " — skip equip, verder WC");
            clearShopSession();
            return 0;
        }
        lastStatus = "kit dragen";
        boolean ok = false;
        String used = null;
        if (kit.hasAction("Wear")) {
            ok = kit.interact("Wear");
            used = "Wear";
        } else if (kit.hasAction("Wield")) {
            ok = kit.interact("Wield");
            used = "Wield";
        } else if (kit.hasAction("Equip")) {
            ok = kit.interact("Equip");
            used = "Equip";
        }
        clearShopSession();
        if (ok) {
            wearFailStreak = 0;
            log.info("[ForestryKit] {} forestry kit id={} slot={}", used, kit.getId(), kit.getSlot());
            BotRuntime.logConsole("[ForestryKit] " + used + " forestry kit");
        } else {
            wearFailStreak++;
            log.info("[ForestryKit] {} FAIL streak={} id={} slot={} (geen View)",
                    used != null ? used : "Wear", wearFailStreak, kit.getId(), kit.getSlot());
            BotRuntime.logConsole("[ForestryKit] Wear FAIL #" + wearFailStreak
                    + " — geen actie/MES? id=" + kit.getId());
        }
        return Rand.nextInt(900, 1600);
    }

    /** ESC om Forestry Shop te sluiten. */
    static int closeShopIfOpen() {
        if (!isShopOpenOnScreen()) {
            clearShopSession();
            return 0;
        }
        lastStatus = "shop sluiten…";
        closeShopUi();
        return Rand.nextInt(550, 950);
    }

    private static void closeShopUi() {
        try {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        } catch (Throwable t) {
            log.warn("[ForestryKit] ESC shop: {}", t.toString());
        }
        clearShopSession();
        log.info("[ForestryKit] shop dicht (ESC)");
        BotRuntime.logConsole("[ForestryKit] shop dicht (ESC)");
    }

    static int tickAcquireFromForester() {
        if (hasKitOnPerson()) {
            // Eerst shop dicht, pas daarna Wear (niet in dezelfde tick)
            if (isShopOpenOnScreen() || hasActiveShopSession()) {
                int closed = closeShopIfOpen();
                if (closed > 0) {
                    return closed;
                }
            }
            int wear = tryEquipFromInventory();
            lastStatus = isEquipped() ? "kit OK (equipped)" : "kit OK (inv)";
            return wear;
        }

        if (hasActiveShopSession()) {
            long now = System.currentTimeMillis();
            if (shopStepStartedMs > 0L && now - shopStepStartedMs > SHOP_STEP_STUCK_MS
                    && shopStep != ShopStep.VERIFY) {
                log.info("[ForestryKit] shop step timeout op {} — reset", shopStep);
                clearShopSession();
                lastStatus = "shop timeout reset";
                return Rand.nextInt(500, 800);
            }
            logShopThrottled();
            int delay = tickShopBuy();
            if (hasKitOnPerson()) {
                lastStatus = "kit gekocht — shop sluiten";
                log.info("[ForestryKit] kit gekocht — eerst shop dicht");
                BotRuntime.logConsole("[ForestryKit] kit gekocht — shop sluiten");
                int closeDelay = closeShopIfOpen();
                return closeDelay > 0 ? closeDelay : Rand.nextInt(500, 800);
            }
            return delay > 0 ? delay : Rand.nextInt(600, 1000);
        }

        if (kitInOpenBank()) {
            lastStatus = "kit in open bank";
            return 0;
        }

        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 600;
        }

        INPC forester = findFriendlyForester();
        WorldPoint pos = me.worldLocation;

        if (forester != null && pos.distanceTo(forester.getWorldLocation()) <= 15) {
            long now = System.currentTimeMillis();
            if (now - lastNpcMs >= 800) {
                lastStatus = "Trade Friendly Forester";
                if (forester.hasAction("Trade")) {
                    forester.interact("Trade");
                } else {
                    forester.interact("Talk-to");
                }
                lastNpcMs = now;
                beginShopSession();
                log.info("[ForestryKit] Trade Friendly Forester — shop-sessie");
            }
            return Rand.nextInt(1200, 2100);
        }

        if (pos.distanceTo(FORESTER_ANCHOR) > 12 || forester == null) {
            lastStatus = "→ Friendly Forester";
            MovementHelper.walkTo(FORESTER_ANCHOR);
            return Rand.nextInt(800, 1400);
        }

        lastStatus = "Forester zoeken…";
        return Rand.nextInt(900, 1500);
    }

    private static int tickShopBuy() {
        if (hasKitOnPerson()) {
            // Laat outer flow shop sluiten — hier geen clear zonder ESC
            return 0;
        }
        long now = System.currentTimeMillis();

        switch (shopStep) {
            case WAIT_SHOP: {
                lastStatus = "wacht Forestry Shop…";
                if (!isShopTitleOnScreen()) {
                    return poll();
                }
                Integer bought = tryDirectBuy1(now, false);
                if (bought != null) {
                    return bought;
                }
                if (isKitDetailVisible()) {
                    advance(ShopStep.WAIT_BUY, now);
                    waitBuyStartedMs = now;
                    return poll();
                }
                advance(ShopStep.CLICK_KIT, now);
                return reaction();
            }
            case CLICK_KIT: {
                Integer bought = tryDirectBuy1(now, true);
                if (bought != null) {
                    return bought;
                }
                if (isKitDetailVisible()) {
                    advance(ShopStep.WAIT_BUY, now);
                    waitBuyStartedMs = now;
                    return poll();
                }
                lastStatus = "klik/koop Forestry kit…";
                if (now - lastClickMs < CLICK_COOLDOWN_MS) {
                    return poll();
                }
                String diag = WcWidgets.diagnoseShop(
                        ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE, ForestryIds.FORESTRY_KIT_ITEM_ID);
                log.info("[ForestryKit] diag {}", diag);
                BotRuntime.logConsole("[ForestryKit] diag " + diag);

                if (!clickOrBuyKitSlot(now)) {
                    lastStatus = "kit-slot niet gevonden";
                    return reaction();
                }
                lastClickMs = now;
                kitClickedMs = now;
                waitBuyStartedMs = 0L;
                if (shopStep == ShopStep.VERIFY) {
                    return Rand.nextInt((int) AFTER_BUY_CLICK_MS, (int) AFTER_BUY_CLICK_MS + 400);
                }
                advance(ShopStep.WAIT_SELECTED, now);
                lastStatus = "wacht kit-selectie…";
                return reaction();
            }
            case WAIT_SELECTED: {
                lastStatus = "wacht kit-selectie…";
                Integer bought = tryDirectBuy1(now, true);
                if (bought != null) {
                    return bought;
                }
                if (now - kitClickedMs < AFTER_KIT_CLICK_MS) {
                    return poll();
                }
                if (!isKitDetailVisible()) {
                    if (isBuyButtonVisible() && now - kitClickedMs > 800L) {
                        advance(ShopStep.CLICK_BUY, now);
                        return reaction();
                    }
                    if (now - kitClickedMs > 4_000L) {
                        log.info("[ForestryKit] geen detail — opnieuw (scan={})",
                                summarize(readDetailText()));
                        advance(ShopStep.CLICK_KIT, now);
                        kitClickedMs = 0L;
                    }
                    return poll();
                }
                advance(ShopStep.WAIT_BUY, now);
                waitBuyStartedMs = now;
                return poll();
            }
            case WAIT_BUY: {
                lastStatus = "wacht Buy-1…";
                Integer bought = tryDirectBuy1(now, true);
                if (bought != null) {
                    return bought;
                }
                if (waitBuyStartedMs == 0L) {
                    waitBuyStartedMs = now;
                }
                if (!isBuyButtonVisible()) {
                    if (now - waitBuyStartedMs > WAIT_BUY_RETRY_MS) {
                        advance(ShopStep.CLICK_KIT, now);
                        kitClickedMs = 0L;
                        waitBuyStartedMs = 0L;
                    }
                    return poll();
                }
                advance(ShopStep.CLICK_BUY, now);
                return reaction();
            }
            case CLICK_BUY: {
                Integer bought = tryDirectBuy1(now, true);
                if (bought != null) {
                    return bought;
                }
                if (now - lastClickMs < CLICK_COOLDOWN_MS) {
                    return poll();
                }
                lastStatus = "klik Buy-1 knop…";
                if (!clickBuy1Button()) {
                    lastStatus = "Buy-1 knop mislukt";
                    advance(ShopStep.CLICK_KIT, now);
                    return reaction();
                }
                lastClickMs = now;
                buyClickedMs = now;
                advance(ShopStep.VERIFY, now);
                lastStatus = "koop bevestigen…";
                log.info("[ForestryKit] 819,22 Buy-1 geklikt");
                return Rand.nextInt((int) AFTER_BUY_CLICK_MS, (int) AFTER_BUY_CLICK_MS + 350);
            }
            case VERIFY:
            default: {
                lastStatus = "koop bevestigen…";
                if (hasKitOnPerson()) {
                    return 0;
                }
                if (!isShopOpenOnScreen() && now - buyClickedMs > 2_000L) {
                    advance(ShopStep.WAIT_SHOP, now);
                    kitClickedMs = 0L;
                }
                if (now - buyClickedMs > 6_000L) {
                    advance(ShopStep.CLICK_KIT, now);
                    kitClickedMs = 0L;
                    buyClickedMs = 0L;
                }
                return poll();
            }
        }
    }

    /**
     * @param force als true: ook Buy-1 zonder Buy-actie in actions[] (na selectie)
     */
    private static Integer tryDirectBuy1(long now, boolean force) {
        if (now - lastClickMs < CLICK_COOLDOWN_MS) {
            return null;
        }
        WcWidgets.ShopHit hit = WcWidgets.findKitHit(
                ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FORESTRY_KIT_ITEM_ID,
                "forestry kit",
                12, 12);
        if (hit == null) {
            return null;
        }
        boolean hasBuy = false;
        for (String a : hit.actions) {
            if (a != null && a.toLowerCase(Locale.ROOT).startsWith("buy")) {
                hasBuy = true;
                break;
            }
        }
        if (!hasBuy && !force) {
            return null;
        }
        lastStatus = "Buy-1 menu op kit…";
        if (!WcWidgets.invokeBuy1OnHit(hit)) {
            return null;
        }
        lastClickMs = now;
        buyClickedMs = now;
        if (kitClickedMs == 0L) {
            kitClickedMs = now;
        }
        advance(ShopStep.VERIFY, now);
        log.info("[ForestryKit] Buy-1 invoke itemId={} idx={} packed={} actions={}",
                hit.itemId, hit.index, hit.packedId, java.util.Arrays.toString(hit.actions));
        BotRuntime.logConsole("[ForestryKit] Buy-1 invoke kit id=" + hit.itemId);
        return Rand.nextInt((int) AFTER_BUY_CLICK_MS, (int) AFTER_BUY_CLICK_MS + 400);
    }

    private static boolean clickOrBuyKitSlot(long now) {
        WcWidgets.ShopHit hit = WcWidgets.findKitHit(
                ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FORESTRY_KIT_ITEM_ID,
                "forestry kit",
                MIN_KIT_W, MIN_KIT_H);
        if (hit != null) {
            boolean hasBuy = false;
            for (String a : hit.actions) {
                if (a != null && a.toLowerCase(Locale.ROOT).startsWith("buy")) {
                    hasBuy = true;
                    break;
                }
            }
            if (hasBuy && WcWidgets.invokeBuy1OnHit(hit)) {
                buyClickedMs = now;
                advance(ShopStep.VERIFY, now);
                log.info("[ForestryKit] Buy-1 via kit-hit (CLICK_KIT)");
                return true;
            }
            if (WcWidgets.clickWidgetBounds(hit.bounds)) {
                log.info("[ForestryKit] canvas-klik kit {}x{} @{},{}",
                        hit.bounds.width, hit.bounds.height, hit.bounds.x, hit.bounds.y);
                return true;
            }
        }
        return WcWidgets.clickRelaxed(
                ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FRIENDLY_FORESTER_SHOP_KIT_CHILD,
                8, 8);
    }

    private static boolean clickBuy1Button() {
        return WcWidgets.clickRelaxed(
                ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FRIENDLY_FORESTER_SHOP_BUY1_CHILD,
                MIN_BUY_W, MIN_BUY_H);
    }

    private static boolean isBuyButtonVisible() {
        return WcWidgets.hasBounds(
                ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FRIENDLY_FORESTER_SHOP_BUY1_CHILD,
                MIN_BUY_W, MIN_BUY_H);
    }

    private static boolean isShopOpenOnScreen() {
        return isShopTitleOnScreen();
    }

    private static boolean isShopTitleOnScreen() {
        if (!WcWidgets.hasBounds(ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FRIENDLY_FORESTER_SHOP_TITLE_CHILD, MIN_TITLE_W, MIN_TITLE_H)) {
            return WcWidgets.hasBounds(ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE, 0, 80, 40);
        }
        String t = WcWidgets.allText(ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FRIENDLY_FORESTER_SHOP_TITLE_CHILD).toLowerCase(Locale.ROOT);
        return t.isEmpty() || t.contains("forestry shop");
    }

    private static boolean isKitDetailVisible() {
        String all = readDetailText();
        if (isShopWelcomeText(all)) {
            return false;
        }
        return isForestryKitLabel(all) || isKitDescription(all);
    }

    private static String readDetailText() {
        String child31 = WcWidgets.allText(ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FRIENDLY_FORESTER_SHOP_SELECTED_KIT_CHILD);
        if (child31 != null && !child31.isEmpty()
                && (isForestryKitLabel(child31) || isKitDescription(child31) || isShopWelcomeText(child31))) {
            return child31;
        }
        return WcWidgets.scanIfaceText(ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE);
    }

    private static boolean isForestryKitLabel(String label) {
        return label != null && label.toLowerCase(Locale.ROOT).contains("forestry kit");
    }

    private static boolean isKitDescription(String label) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        String low = label.toLowerCase(Locale.ROOT);
        return low.contains(KIT_DESC) || (low.contains("kit bag") && low.contains("woodcutting"));
    }

    private static boolean isShopWelcomeText(String label) {
        return label != null && label.toLowerCase(Locale.ROOT).contains(WELCOME);
    }

    private static boolean isKitItem(int id, String name) {
        if (ForestryIds.isForestryKitItemId(id)) {
            return true;
        }
        return name != null && name.toLowerCase(Locale.ROOT).contains("forestry kit");
    }

    private static IInventoryItem findKitInInventory() {
        IInventoryItem byName = Inventory.getFirst(ForestryIds.FORESTRY_KIT_NAME);
        if (byName != null) {
            return byName;
        }
        return Inventory.getFirst(i -> i != null && isKitItem(i.getId(), i.getName()));
    }

    private static INPC findFriendlyForester() {
        try {
            INPC byId = NPCs.getNearest(n -> n != null && n.getId() == ForestryIds.FRIENDLY_FORESTER_NPC_ID);
            if (byId != null) {
                return byId;
            }
        } catch (Throwable ignored) {
        }
        return NPCs.getNearest(n -> n != null && n.getName() != null
                && n.getName().toLowerCase(Locale.ROOT).contains("friendly forester"));
    }

    private static void beginShopSession() {
        long now = System.currentTimeMillis();
        shopSessionUntilMs = now + SHOP_SESSION_MS;
        kitClickedMs = 0L;
        buyClickedMs = 0L;
        waitBuyStartedMs = 0L;
        advance(ShopStep.WAIT_SHOP, now);
    }

    private static void clearShopSession() {
        shopSessionUntilMs = 0L;
        shopStepStartedMs = 0L;
        kitClickedMs = 0L;
        buyClickedMs = 0L;
        waitBuyStartedMs = 0L;
        shopStep = ShopStep.WAIT_SHOP;
    }

    private static void advance(ShopStep next, long now) {
        shopStep = next;
        shopStepStartedMs = now;
    }

    private static int poll() {
        return Rand.nextInt(80, 160);
    }

    private static int reaction() {
        return Rand.nextInt(260, 460);
    }

    private static String summarize(String t) {
        if (t == null || t.isEmpty()) {
            return "(leeg)";
        }
        return t.length() > 60 ? t.substring(0, 57) + "…" : t;
    }

    private static void logShopThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastDebugMs < 900L) {
            return;
        }
        lastDebugMs = now;
        WcWidgets.ShopHit hit = WcWidgets.findKitHit(
                ForestryIds.FRIENDLY_FORESTER_SHOP_IFACE,
                ForestryIds.FORESTRY_KIT_ITEM_ID,
                "forestry kit",
                8, 8);
        String msg = "shop step=" + shopStep
                + " title=" + isShopTitleOnScreen()
                + " detail=" + summarize(readDetailText())
                + " kitSel=" + isKitDetailVisible()
                + " buyBtn=" + isBuyButtonVisible()
                + " kitHit=" + (hit != null
                ? ("id=" + hit.itemId + " a=" + java.util.Arrays.toString(hit.actions))
                : "null")
                + " onPerson=" + hasKitOnPerson();
        log.info("[ForestryKit] {}", msg);
        BotRuntime.logConsole("[ForestryKit] " + msg);
    }
}
