package com.lonebot.example;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Game;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.GeRestockHelper;
import net.storm.sdk.items.GrandExchange;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.MovementHelper;

/**
 * Test-tab: walk to Varrock GE and place one buy or sell offer.
 */
@PluginDescriptor(
        name = "LoneBot GE trade test",
        description = "Lopen naar GE + koop of verkoop één item"
)
public class GeTradeTestPlugin extends LoopedPlugin {

    private static final long TIMEOUT_MS = 180_000L;
    private static final String SRC = "[GE/test]";

    private long startedAtMs;
    private boolean loggedStart;
    private boolean offerAttempted;

    @Override
    public int loop() {
        if (!BotRuntime.geTradeTestEnabled) {
            BotRuntime.geTradeStatus = "uit";
            resetLocal();
            return 0;
        }
        if (!BotRuntime.botEnabled) {
            BotRuntime.geTradeStatus = "bot uit";
            return 800;
        }
        if (!Game.isLoggedIn()) {
            BotRuntime.geTradeStatus = "niet ingelogd";
            logOnce("niet ingelogd — log in op dit account");
            return 800;
        }

        String item = BotRuntime.geTradeItem != null ? BotRuntime.geTradeItem.trim() : "";
        int qty = Math.max(1, BotRuntime.geTradeQty);
        int price = Math.max(1, BotRuntime.geTradePrice);
        boolean buy = BotRuntime.geTradeBuy;

        if (item.isEmpty()) {
            fail("geen itemnaam");
            return 800;
        }

        if (startedAtMs == 0) {
            startedAtMs = System.currentTimeMillis();
            MovementHelper.clearPath();
        }
        if (System.currentTimeMillis() - startedAtMs > TIMEOUT_MS) {
            fail("timeout (3 min)");
            return 800;
        }
        if (!loggedStart) {
            loggedStart = true;
            logLine((buy ? "koop" : "verkoop") + " " + qty + "× " + item + " @" + price + "gp — naar GE");
        }

        if (Bank.isOpen()) {
            Bank.close();
            BotRuntime.geTradeStatus = "bank sluiten";
            return 500;
        }

        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (pos == null) {
            BotRuntime.geTradeStatus = "geen speler";
            return 600;
        }

        if (!GrandExchange.isOpen()) {
            if (!GeRestockHelper.isCloseEnoughToOpenGe(pos)) {
                BotRuntime.geTradeStatus = "lopen naar GE d=" + pos.distanceTo(GeRestockHelper.GE_HUB);
                GeRestockHelper.walkTowardGeClerkIfNeeded();
                return 400;
            }
            BotRuntime.geTradeStatus = "GE openen";
            boolean opened = GeRestockHelper.tryOpenGeInterfaceAtReach();
            if (opened) {
                logLine("open GE → true");
            } else {
                logOpenWait();
            }
            return 600;
        }

        if (buy) {
            int coins = GeRestockHelper.inventoryCoinCount();
            long need = (long) qty * (long) price;
            if (coins < need) {
                BotRuntime.geTradeStatus = "coins " + coins + " < ~" + need;
                if (GeRestockHelper.tryWithdrawCoinsBeforeGePurchase((int) Math.min(need, Integer.MAX_VALUE / 4))) {
                    return 500;
                }
                fail("te weinig coins (" + coins + " nodig ~" + need + ")");
                return 800;
            }
            if (offerAttempted) {
                succeed("koop-offer geplaatst (" + item + ")");
                return 800;
            }
            BotRuntime.geTradeStatus = "kopen " + qty + "× " + item;
            logLine("buy " + qty + "× " + item + " @" + price);
            boolean ok = GrandExchange.buy(item, qty, price, false, false);
            offerAttempted = true;
            if (!ok) {
                fail("koop mislukt (zoek/confirm)");
                return 800;
            }
            succeed("koop-offer geplaatst (" + item + ")");
            return 800;
        }

        if (!Inventory.contains(item)) {
            fail("'" + item + "' niet in inventory om te verkopen");
            return 800;
        }
        if (offerAttempted) {
            succeed("verkoop-offer geplaatst (" + item + ")");
            return 800;
        }
        BotRuntime.geTradeStatus = "verkopen " + qty + "× " + item;
        logLine("sell " + qty + "× " + item + " @" + price);
        boolean ok = GeRestockHelper.placeSellOfferRobust(item, qty, price, () -> BotRuntime.geTradeTestEnabled);
        offerAttempted = true;
        if (!ok) {
            fail("verkoop mislukt (setup/qty/confirm)");
            return 800;
        }
        succeed("verkoop-offer geplaatst (" + item + ")");
        return 800;
    }

    private void succeed(String msg) {
        logLine("OK " + msg);
        BotRuntime.geTradeStatus = "✓ " + msg;
        BotRuntime.geTradeTestEnabled = false;
        resetLocal();
    }

    private void fail(String msg) {
        logLine("FAIL " + msg);
        BotRuntime.geTradeStatus = "⚠ " + msg;
        BotRuntime.geTradeTestEnabled = false;
        resetLocal();
    }

    private void resetLocal() {
        startedAtMs = 0;
        loggedStart = false;
        offerAttempted = false;
    }

    private static volatile long lastOpenLogMs;

    private static void logOpenWait() {
        long now = System.currentTimeMillis();
        if (now - lastOpenLogMs < 2_000L) {
            return;
        }
        lastOpenLogMs = now;
        logLine("open GE → false (wacht, geen extra Exchange-klik)");
    }

    private static void logLine(String msg) {
        BotRuntime.logConsole(SRC + " " + msg);
    }

    private static void logOnce(String msg) {
        if (msg != null && msg.equals(BotRuntime.geTradeStatus)) {
            return;
        }
        logLine(msg);
        BotRuntime.geTradeStatus = msg;
    }
}
