package net.storm.sdk.items;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.travel.VarrockGeTravelHelper;
import net.storm.sdk.utils.Sleep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * GE approach + buy-with-escalation + robust sell (CombatBot procedure).
 */
public final class GeRestockHelper {

    private static final Logger log = LoggerFactory.getLogger(GeRestockHelper.class);

    public static final WorldPoint GE_HUB = new WorldPoint(3164, 3487, 0);
    public static final int GE_INTERACT_RADIUS = 14;
    public static final int GE_SCENE_OPEN_RADIUS = 20;
    /** Compat alias — interact radius. */
    public static final int GE_OPEN_RADIUS = GE_INTERACT_RADIUS;

    private static final double ACTIVE_TRADED_BUY_PREMIUM = 1.25;
    /** Na een lege offer: +10%, +20%, … tot +50% van het startbod. */
    private static final int[] BUY_ESCALATION_PCT = {10, 20, 30, 40, 50};
    /** Wacht per offer vóór abort + nieuw bod (CombatBot-default). */
    private static final long DEFAULT_OFFER_WAIT_MS = 10_000L;
    private static final long GE_CLICK_PATH_GRACE_MS = 4500L;

    private static volatile long lastGeDistantClickMs;
    private static volatile long lastLogMs;
    private static volatile String lastLog = "";

    public enum RestockResult {
        SUCCESS,
        FAILED,
        GE_NOT_AVAILABLE,
        NOT_ENOUGH_COINS,
        NO_SLOT
    }

    private GeRestockHelper() {
    }

    static WorldPoint localPos() {
        Players.LocalSnap snap = Players.snapshotLocal();
        return snap != null && snap.present ? snap.worldLocation : null;
    }

    public static int distanceToGeHub(WorldPoint myPos) {
        if (myPos == null) {
            return Integer.MAX_VALUE;
        }
        return myPos.distanceTo(GE_HUB);
    }

    public static boolean isCloseEnoughToOpenGe(WorldPoint myPos) {
        if (myPos == null) {
            return false;
        }
        if (GrandExchange.isOpen()) {
            return true;
        }
        INPC clerk = findNearestGeClerk(myPos);
        if (clerk != null && clerk.getWorldLocation() != null) {
            int clerkDist = myPos.distanceTo(clerk.getWorldLocation());
            if (clerkDist <= GE_INTERACT_RADIUS) {
                return true;
            }
            if (clerkDist <= GE_SCENE_OPEN_RADIUS) {
                return true;
            }
        }
        return distanceToGeHub(myPos) <= GE_INTERACT_RADIUS;
    }

    /**
     * Walk or scene-click clerk. {@code true} if a walk/click was issued (caller waits).
     */
    public static boolean approachGeForTrade() {
        if (GrandExchange.isOpen()) {
            return false;
        }
        WorldPoint myPos = localPos();
        if (isCloseEnoughToOpenGe(myPos)) {
            return tryOpenGeInterfaceAtReach();
        }
        return walkTowardGeClerkIfNeeded();
    }

    public static boolean walkTowardGeClerkIfNeeded() {
        WorldPoint myPos = localPos();
        if (myPos == null) {
            return false;
        }
        if (GrandExchange.isOpen() || isCloseEnoughToOpenGe(myPos)) {
            return false;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (shouldWaitForGeClickPath(me)) {
            return true;
        }
        INPC clerk = findNearestGeClerk(myPos);
        if (trySceneClickGeClerk(myPos)) {
            return true;
        }
        // Walker bezig (Lumb Climb-down / pad): geen walkTo(GE) — wist tussendoel → pingpong
        try {
            if (MovementHelper.getActivePath() != null
                    || MovementHelper.getActiveDestination() != null) {
                boolean moving = false;
                try {
                    moving = net.storm.sdk.movement.Movement.isWalking()
                            || (me != null && me.moving);
                } catch (Throwable ignored) {
                }
                if (moving || MovementHelper.getActivePath() != null) {
                    debug("→ GE wait (walker bezig, geen re-walk)");
                    if (Bank.isOpen()) {
                        Bank.leaveOpenForWalk();
                    }
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        WorldPoint dest = clerk != null && clerk.getWorldLocation() != null
                ? clerk.getWorldLocation() : GE_HUB;
        debug("→ GE " + dest.getX() + "," + dest.getY()
                + " hubDist=" + distanceToGeHub(myPos));
        if (Bank.isOpen()) {
            Bank.leaveOpenForWalk();
        }
        VarrockGeTravelHelper.walkTowardGe();
        MovementHelper.walkTo(dest);
        return true;
    }

    public static boolean tryOpenGeInterfaceAtReach() {
        if (GrandExchange.isOpen()) {
            return true;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (shouldWaitForGeClickPath(me)) {
            Sleep.sleep(350, 550);
            return GrandExchange.isOpen();
        }
        WorldPoint myPos = localPos();
        if (trySceneClickGeClerk(myPos)) {
            Sleep.sleep(450, 800);
            return GrandExchange.isOpen();
        }
        INPC clerk = findNearestGeClerk(myPos);
        if (clerk != null && clerk.hasAction("Exchange") && clerk.interact("Exchange")) {
            lastGeDistantClickMs = System.currentTimeMillis();
            debug("clerk.interact(Exchange)");
            Sleep.sleep(500, 900);
            return GrandExchange.isOpen();
        }
        ITileObject booth = findGeBooth();
        if (booth != null && booth.hasAction("Exchange") && booth.interact("Exchange")) {
            Sleep.sleep(500, 900);
            return GrandExchange.isOpen();
        }
        return GrandExchange.isOpen();
    }

    public static boolean tryWithdrawCoinsBeforeGePurchase(int minInvCoins) {
        int target = Math.max(0, minInvCoins);
        if (target <= 0 || inventoryCoinCount() >= target) {
            return true;
        }
        if (Bank.isOpen()) {
            return withdrawCoinsFromOpenBank(target);
        }
        WorldPoint myPos = localPos();
        if (myPos == null || !BankHelper.isNearGrandExchange(myPos)) {
            return false;
        }
        debug("coins " + inventoryCoinCount() + "/" + target + " → GE-booth bank");
        BankHelper.tryOpenBankAtGrandExchange();
        return false;
    }

    public static boolean withdrawCoinsFromOpenBank(int amount) {
        if (amount <= 0 || !Bank.isOpen()) {
            return false;
        }
        int inv = inventoryCoinCount();
        int deficit = Math.max(0, amount - inv);
        if (deficit <= 0) {
            return true;
        }
        boolean ok = Bank.withdraw("Coins", deficit);
        Sleep.sleep(220, 400);
        return ok && inventoryCoinCount() >= amount;
    }

    public static int inventoryCoinCount() {
        try {
            return Math.max(0, Inventory.getCount(true, "Coins"));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * Buy {@code quantity} units (not target-total). Existing LoneBot scripts pass deficit/units.
     */
    public static boolean buyWithEscalation(String itemName, int quantity, int startPrice) {
        RestockResult r = buyWithEscalationResult(itemName, quantity, startPrice, null, true);
        return r == RestockResult.SUCCESS;
    }

    public static RestockResult buyWithEscalationResult(String itemName, int quantity, int startPrice,
                                                        BooleanSupplier stillEnabled, boolean closeWhenDone) {
        if (itemName == null || itemName.isBlank() || quantity <= 0 || startPrice <= 0) {
            return RestockResult.FAILED;
        }
        if (cancelled(stillEnabled)) {
            return RestockResult.FAILED;
        }
        debug("buyWithEscalation " + quantity + "× " + itemName + " @" + startPrice + "gp");

        if (!GrandExchange.isOpen()) {
            if (!isCloseEnoughToOpenGe(localPos())) {
                approachGeForTrade();
                return RestockResult.GE_NOT_AVAILABLE;
            }
            if (!tryOpenGeInterfaceAtReach() && !GrandExchange.isOpen()) {
                return RestockResult.GE_NOT_AVAILABLE;
            }
        }
        if (!GrandExchange.isOpen()) {
            return RestockResult.GE_NOT_AVAILABLE;
        }

        int coins = inventoryCoinCount();
        long minCost = (long) quantity * (long) startPrice;
        if (coins < minCost) {
            debug("te weinig coins (" + coins + " < ~" + minCost + ")");
            return RestockResult.NOT_ENOUGH_COINS;
        }

        if (GrandExchange.canCollect()) {
            GrandExchange.collect(false);
            sleepGe(240, 480, stillEnabled);
        }
        if (GrandExchange.isFull()) {
            GrandExchange.collect(false);
            sleepGe(280, 450, stillEnabled);
            if (GrandExchange.isFull()) {
                return RestockResult.NO_SLOT;
            }
        }

        int before = Inventory.getCount(true, itemName);
        int basePrice = resolveStartPrice(itemName, startPrice);
        int price = basePrice;
        RestockResult last = RestockResult.FAILED;
        int maxAttempts = BUY_ESCALATION_PCT.length + 1;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (cancelled(stillEnabled)) {
                if (closeWhenDone) {
                    closeGeIfOpen();
                }
                return RestockResult.FAILED;
            }
            int have = Inventory.getCount(true, itemName);
            if (have >= before + quantity) {
                last = RestockResult.SUCCESS;
                break;
            }
            long need = (long) quantity * (long) price;
            if (inventoryCoinCount() < need) {
                debug("te weinig coins voor bod " + price + "gp (poging " + (attempt + 1) + ")");
                last = RestockResult.NOT_ENOUGH_COINS;
                break;
            }
            debug("poging " + (attempt + 1) + "/" + maxAttempts + " @" + price + "gp — wacht "
                    + (DEFAULT_OFFER_WAIT_MS / 1000) + "s op fill");
            boolean placed = GrandExchange.buy(itemName, quantity, price, false, false, stillEnabled);
            sleepGe(700, 1200, stillEnabled);
            GeHelper.dismissGeHighPriceWarningAfterConfirm(stillEnabled);

            boolean filled = waitForFill(itemName, before + quantity, DEFAULT_OFFER_WAIT_MS, stillEnabled);
            if (filled || Inventory.getCount(true, itemName) >= before + quantity) {
                last = RestockResult.SUCCESS;
                break;
            }
            if (GrandExchange.hasCompletedOffer(itemName)) {
                GrandExchange.collect(false);
                sleepGe(240, 420, stillEnabled);
                if (Inventory.getCount(true, itemName) >= before + quantity) {
                    last = RestockResult.SUCCESS;
                    break;
                }
            }
            if (!placed && attempt == 0) {
                last = RestockResult.FAILED;
                break;
            }
            if (attempt < BUY_ESCALATION_PCT.length) {
                try {
                    GrandExchange.abortOffer(itemName);
                } catch (Throwable ignored) {
                }
                collectAbortedOffer(itemName, stillEnabled);
                int pct = BUY_ESCALATION_PCT[attempt];
                int next = escalateFromBase(basePrice, pct);
                debug("niet gevuld in " + (DEFAULT_OFFER_WAIT_MS / 1000) + "s — abort, +" + pct
                        + "% → " + next + "gp");
                price = next;
                sleepGe(400, 700, stillEnabled);
            } else {
                debug("+50% nog niet gevuld na " + (DEFAULT_OFFER_WAIT_MS / 1000) + "s — stop");
            }
        }

        if (GrandExchange.canCollect()) {
            GrandExchange.collect(false);
            sleepGe(240, 420, stillEnabled);
        }
        if (Inventory.getCount(true, itemName) >= before + quantity) {
            last = RestockResult.SUCCESS;
        }
        if (closeWhenDone) {
            closeGeIfOpen();
        }
        debug("eind → " + last);
        return last;
    }

    public static boolean placeSellOfferRobust(String itemName, int quantity, int unitPrice,
                                               BooleanSupplier isEnabled) {
        quantity = Math.max(1, quantity);
        unitPrice = Math.max(1, unitPrice);
        if (itemName == null || itemName.isBlank() || cancelled(isEnabled)) {
            return false;
        }
        debug("sell robust: " + itemName + " qty=" + quantity + " price=" + unitPrice);
        if (!GrandExchange.isOpen()) {
            if (!tryOpenGeInterfaceAtReach()) {
                return false;
            }
            sleepGe(380, 720, isEnabled);
        }
        if (!GrandExchange.isOpen()) {
            return false;
        }
        if (GrandExchange.canCollect()) {
            GrandExchange.collect(false);
            sleepGe(240, 480, isEnabled);
        }
        if (!GeHelper.isGeOfferSetupForItem(itemName)) {
            GrandExchange.sell(itemName);
            sleepGe(450, 750, isEnabled);
            long deadline = System.currentTimeMillis() + 5000L;
            while (System.currentTimeMillis() < deadline && !cancelled(isEnabled)) {
                if (GeHelper.isGeOfferSetupForItem(itemName)) {
                    break;
                }
                sleepGe(150, 280, isEnabled);
            }
        }
        if (!GeHelper.isGeOfferSetupForItem(itemName)) {
            debug("sell: setup nog niet — retry Create Sell + Offer");
            GrandExchange.createSellOffer();
            sleepGe(280, 450, isEnabled);
            GrandExchange.sell(itemName);
            sleepGe(450, 750, isEnabled);
            long deadline = System.currentTimeMillis() + 4000L;
            while (System.currentTimeMillis() < deadline && !cancelled(isEnabled)) {
                if (GeHelper.isGeOfferSetupForItem(itemName)) {
                    break;
                }
                sleepGe(150, 280, isEnabled);
            }
        }
        if (!GeHelper.isGeOfferSetupForItem(itemName)) {
            debug("sell: geen setup voor " + itemName
                    + " (name='" + GrandExchange.getItemName()
                    + "' selling=" + GrandExchange.isSelling()
                    + " setup=" + GrandExchange.isSetupOpen() + ")");
            return false;
        }
        sleepGe(280, 480, isEnabled);
        if (!GeHelper.trySetGeOfferQuantityRobust(quantity, isEnabled)) {
            debug("sell: quantity niet " + quantity);
            return false;
        }
        sleepGe(280, 480, isEnabled);
        GeHelper.tryTypeGePriceViaWidgets(unitPrice, isEnabled);
        sleepGe(280, 480, isEnabled);
        if (!GeHelper.verifyGeOfferQuantity(quantity)) {
            debug("sell: qty gecorrumpeerd na prijs — opnieuw");
            if (!GeHelper.trySetGeOfferQuantityRobust(quantity, isEnabled)) {
                return false;
            }
        }
        if (!GeHelper.verifyGeOfferQuantity(quantity)) {
            debug("sell: ABORT — qty niet " + quantity);
            return false;
        }
        boolean ok = GrandExchange.confirm();
        sleepGe(700, 1200, isEnabled);
        GeHelper.dismissGeHighPriceWarningAfterConfirm(isEnabled);
        if (!ok) {
            ok = GeHelper.tryClickGeConfirmButton();
            sleepGe(500, 800, isEnabled);
            GeHelper.dismissGeHighPriceWarningAfterConfirm(isEnabled);
        }
        debug("sell confirm → " + ok);
        return ok || !GeHelper.isGeOfferSetupForItem(itemName);
    }

    public static void closeGeIfOpen() {
        if (!GrandExchange.isOpen()) {
            return;
        }
        Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        Sleep.sleep(280, 480);
        if (GrandExchange.isOpen()) {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
            Sleep.sleep(200, 360);
        }
    }

    private static int resolveStartPrice(String itemName, int hint) {
        int h = Math.max(1, hint);
        int active = GeHelper.readActivelyTradedPrice();
        if (active > 0) {
            int bid = (int) Math.ceil(active * ACTIVE_TRADED_BUY_PREMIUM);
            debug("actively traded " + active + "gp → bod " + Math.max(bid, h) + "gp");
            return Math.max(bid, h);
        }
        return h;
    }

    private static int escalateFromBase(int basePrice, int plusPercent) {
        int base = Math.max(1, basePrice);
        int pct = Math.max(0, plusPercent);
        int next = (int) Math.ceil(base * (100.0 + pct) / 100.0);
        return Math.max(base + 1, next);
    }

    private static boolean waitForFill(String itemName, int targetOnPerson, long waitMs,
                                       BooleanSupplier stillEnabled) {
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline && !cancelled(stillEnabled)) {
            BotRuntime.heartbeat();
            if (Inventory.getCount(true, itemName) >= targetOnPerson) {
                return true;
            }
            if (GrandExchange.hasCompletedOffer(itemName)) {
                GrandExchange.collect(false);
                sleepGe(200, 360, stillEnabled);
            }
            Sleep.sleep(280, 450);
        }
        return Inventory.getCount(true, itemName) >= targetOnPerson;
    }

    /** Na abort: coins/item van de slot (Collect-items 465,24), nooit Repeat Offer. */
    private static void collectAbortedOffer(String itemName, BooleanSupplier stillEnabled) {
        sleepGe(280, 450, stillEnabled);
        long deadline = System.currentTimeMillis() + 4_000L;
        while (System.currentTimeMillis() < deadline && !cancelled(stillEnabled)) {
            BotRuntime.heartbeat();
            if (!GrandExchange.hasCompletedOffer(itemName) && !GrandExchange.canCollect()) {
                return;
            }
            GrandExchange.collect(false);
            sleepGe(240, 400, stillEnabled);
        }
    }

    private static INPC findNearestGeClerk(WorldPoint myPos) {
        return NPCs.getNearest(npc -> {
            if (npc == null || npc.getName() == null) {
                return false;
            }
            String n = npc.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("clerk") && !n.contains("grand exchange")) {
                return false;
            }
            return npc.hasAction("Exchange") || npc.hasAction("Talk-to");
        });
    }

    private static ITileObject findGeBooth() {
        return TileObjects.getNearest(o -> {
            if (o == null || o.getName() == null) {
                return false;
            }
            String n = o.getName().toLowerCase(Locale.ROOT);
            return (n.contains("grand exchange") || n.contains("exchange booth") || n.contains("ge booth"))
                    && (o.hasAction("Exchange") || o.hasAction("Bank"));
        });
    }

    private static boolean trySceneClickGeClerk(WorldPoint myPos) {
        if (myPos == null) {
            return false;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (shouldWaitForGeClickPath(me)) {
            return true;
        }
        INPC clerk = findNearestGeClerk(myPos);
        if (clerk == null || clerk.getWorldLocation() == null) {
            return false;
        }
        int d = myPos.distanceTo(clerk.getWorldLocation());
        if (d > GE_SCENE_OPEN_RADIUS) {
            return false;
        }
        if (clerk.hasAction("Exchange") && clerk.interact("Exchange")) {
            lastGeDistantClickMs = System.currentTimeMillis();
            debug("scene Exchange clerk d=" + d);
            return true;
        }
        return false;
    }

    private static boolean shouldWaitForGeClickPath(Players.LocalSnap local) {
        if (GrandExchange.isOpen()) {
            return false;
        }
        if (lastGeDistantClickMs <= 0) {
            return false;
        }
        long ago = System.currentTimeMillis() - lastGeDistantClickMs;
        if (ago > GE_CLICK_PATH_GRACE_MS) {
            return false;
        }
        // Snapshot.moving valt vaak weg terwijl de client nog naar de clerk loopt
        // (gele vlag / invoke). CombatBot wachtte alleen bij isMoving → Exchange-spam.
        debug("wacht Exchange-grace (geen herklik)");
        return true;
    }

    private static void sleepGe(int min, int max, BooleanSupplier stillEnabled) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        int total = lo >= hi ? lo : lo + ThreadLocalRandom.current().nextInt(Math.max(1, hi - lo + 1));
        long deadline = System.currentTimeMillis() + total;
        while (System.currentTimeMillis() < deadline) {
            if (cancelled(stillEnabled)) {
                return;
            }
            BotRuntime.heartbeat();
            Sleep.sleep(40, 80);
        }
    }

    private static boolean cancelled(BooleanSupplier stillEnabled) {
        return stillEnabled != null && !stillEnabled.getAsBoolean();
    }

    private static void debug(String msg) {
        if (msg == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1_500L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[GE/restock] " + msg);
        log.info("[GE/restock] {}", msg);
    }
}
