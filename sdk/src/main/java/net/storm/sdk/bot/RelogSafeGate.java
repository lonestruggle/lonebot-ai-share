package net.storm.sdk.bot;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.game.Combat;
import net.storm.sdk.game.Game;
import net.storm.sdk.game.RandomEventHandler;
import net.storm.sdk.game.WorldMap;
import net.storm.sdk.interact.GroundLootPickupHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.DepositBox;
import net.storm.sdk.items.GrandExchange;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.items.Shop;
import net.storm.sdk.items.Trade;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Production;
import net.storm.sdk.widgets.Widgets;

import java.util.Locale;

/**
 * Veilig moment vóór break/re-log logout: geen combat, loot, bank/GE, wildy, randoms.
 * Scripts blijven ticken tot {@link #canLogoutNow()} (LoopHost slaat skill-plugins over via
 * {@link BotRuntime#breakPending}).
 */
public final class RelogSafeGate {

    public static final long FORCE_TIMEOUT_MS = 120_000L;
    private static final int LOOT_RADIUS = 8;
    private static final long ACTION_THROTTLE_MS = 1_200L;

    private static volatile long lastActionMs;
    private static volatile long lastLogMs;

    private RelogSafeGate() {
    }

    public static boolean canLogoutNow() {
        return blockReason() == null;
    }

    /**
     * @return korte reden, of {@code null} als uitloggen mag
     */
    public static String blockReason() {
        if (!Game.isLoggedIn()) {
            return null;
        }
        try {
            if (Combat.isInCombat()) {
                return "combat";
            }
        } catch (Throwable ignored) {
        }
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            if (me != null && me.present && me.animating) {
                return "actie";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (RandomEventHandler.isBusy()) {
                return "random";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Dialog.isOpen()) {
                return "dialog";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Bank.isOpen()) {
                return "bank";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (GrandExchange.isOpen()) {
                return "ge";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (DepositBox.isOpen()) {
                return "deposit";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Shop.isOpen()) {
                return "shop";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Trade.isOpen()) {
                return "trade";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Production.isOpen()) {
                return "make-x";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (WorldMap.isOpen()) {
                return "kaart";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Game.isInWilderness()) {
                return "wilderness";
            }
        } catch (Throwable ignored) {
        }
        if (nearbyLootBlocks()) {
            return "loot";
        }
        return null;
    }

    /**
     * Sluit bank/GE e.d. en raap nabije combat-loot / nests. Geen dialog-ESC (randoms).
     *
     * @return true als een actie is gedaan
     */
    public static boolean tryMakeSafe() {
        long now = System.currentTimeMillis();
        if (now - lastActionMs < ACTION_THROTTLE_MS) {
            return false;
        }
        if (!Game.isLoggedIn()) {
            return false;
        }
        try {
            if (Combat.isInCombat()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (RandomEventHandler.isBusy() || Dialog.isOpen()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Bank.isOpen()) {
                lastActionMs = now;
                Bank.close();
                logThrottled("bank sluiten");
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (GrandExchange.isOpen() || DepositBox.isOpen() || Shop.isOpen()
                    || Trade.isOpen() || Production.isOpen() || WorldMap.isOpen()) {
                lastActionMs = now;
                if (Trade.isOpen()) {
                    Trade.decline();
                } else {
                    Widgets.closeInterfaces();
                }
                logThrottled("interface sluiten");
                return true;
            }
        } catch (Throwable ignored) {
        }
        ITileItem loot = findNearbyLoot();
        if (loot != null) {
            lastActionMs = now;
            GroundLootPickupHelper.pickup(loot);
            logThrottled("loot " + safeName(loot));
            return true;
        }
        return false;
    }

    public static String formatMmSs(long seconds) {
        long s = Math.max(0L, seconds);
        return (s / 60L) + ":" + String.format(Locale.ROOT, "%02d", s % 60L);
    }

    private static boolean nearbyLootBlocks() {
        if (Inventory.isFull()) {
            return false;
        }
        return findNearbyLoot() != null;
    }

    private static ITileItem findNearbyLoot() {
        if (Inventory.isFull()) {
            return null;
        }
        boolean combat = BotRuntime.impKillerEnabled
                || BotRuntime.giantsKillerEnabled
                || BotRuntime.cowCombatEnabled
                || BotRuntime.monkKillerEnabled
                || BotRuntime.imps2Enabled;
        boolean nests = BotRuntime.woodcuttingEnabled;
        if (!combat && !nests) {
            return null;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me == null || !me.present || me.worldLocation == null) {
            return null;
        }
        WorldPoint here = me.worldLocation;
        return TileItems.getNearest(item -> {
            if (item == null || item.getWorldLocation() == null) {
                return false;
            }
            if (item.getWorldLocation().distanceTo(here) > LOOT_RADIUS) {
                return false;
            }
            if (!item.canPick()) {
                return false;
            }
            if (combat) {
                return true;
            }
            String n = item.getName();
            if (n == null) {
                return false;
            }
            String low = n.toLowerCase(Locale.ROOT);
            return low.contains("nest") || low.contains("bird");
        });
    }

    private static String safeName(ITileItem item) {
        try {
            String n = item.getName();
            return n != null ? n : "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    private static void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastLogMs < 1_800L) {
            return;
        }
        lastLogMs = now;
        BotRuntime.logConsole("[Relog/safe] " + msg);
    }
}
