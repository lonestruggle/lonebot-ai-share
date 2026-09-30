package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.interact.GroundLootPickupHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * CombatBot-flow zonder scroll: open geode/bottle/nest (inv → grond).
 * Bank alleen als die <b>al open</b> is — geen proactieve bank-trip (placeholders = loop).
 */
public final class ClueContainerAcquireHelper {

    private static final int ITEM_CLUE_SCROLL_BEGINNER = 23182;
    private static final int ITEM_CLUE_GEODE_BEGINNER = 23442;
    private static final int ITEM_CLUE_BOTTLE_BEGINNER = 23129;
    private static final int GROUND_RADIUS = 18;
    private static final long ACTION_COOLDOWN_MS = 1_400L;
    /** Na lege bank / placeholder: geen herhaalde bank-scan. */
    private static final long EMPTY_BANK_BACKOFF_MS = 900_000L;

    private static final String[] GEODE_NAMES = {
            "Clue geode (beginner)", "Clue geode (easy)", "Clue geode (medium)",
            "Clue geode (hard)", "Clue geode (elite)"
    };
    private static final String[] BOTTLE_NAMES = {
            "Clue bottle (beginner)", "Clue bottle (easy)", "Clue bottle (medium)",
            "Clue bottle (hard)", "Clue bottle (elite)"
    };
    private static final String[] NEST_NAMES = {
            "Clue nest (beginner)", "Clue nest (easy)", "Clue nest (medium)",
            "Clue nest (hard)", "Clue nest (elite)",
            "Bird nest"
    };

    private static long lastActionMs;
    private static long emptyBankUntilMs;
    private static long lastWaitLogMs;
    private static final Set<String> placeholderNames = new HashSet<>();

    private ClueContainerAcquireHelper() {
    }

    public static void reset() {
        lastActionMs = 0L;
        emptyBankUntilMs = 0L;
        lastWaitLogMs = 0L;
        placeholderNames.clear();
    }

    /**
     * @return delay ≥ 0 als er iets gedaan werd; {@code -1} als niets te doen (wacht op drop)
     */
    public static int tick() {
        if (BeginnerClueContainerHelper.hasBeginnerClue()
                || BeginnerClueContainerHelper.hasBeginnerCasket()
                || BeginnerClueContainerHelper.isRewardOpen()) {
            return -1;
        }
        long now = System.currentTimeMillis();
        if (now - lastActionMs < ACTION_COOLDOWN_MS) {
            return 300;
        }

        IInventoryItem geode = findInvContainer(GEODE_NAMES, "Open");
        if (geode != null) {
            return openInv(geode, "Open", "geode");
        }
        IInventoryItem bottle = findInvContainer(BOTTLE_NAMES, "Open");
        if (bottle != null) {
            return openInv(bottle, "Open", "bottle");
        }
        IInventoryItem nest = findInvNest();
        if (nest != null) {
            String action = nest.hasAction("Search") ? "Search"
                    : (nest.hasAction("Open") ? "Open" : null);
            if (action != null) {
                return openInv(nest, action, "nest");
            }
        }

        IPlayer local = Players.getLocal();
        WorldPoint me = local != null ? local.getWorldLocation() : null;
        if (me != null) {
            ITileItem ground = nearestGroundContainer(me);
            if (ground != null) {
                lastActionMs = now;
                if (ground.getWorldLocation() != null && Movement.distanceTo(ground.getWorldLocation()) > 2) {
                    Movement.walkTo(ground.getWorldLocation());
                    BotRuntime.logConsole("[Clue/Container] → " + ground.getName());
                    return 500;
                }
                GroundLootPickupHelper.Result r = GroundLootPickupHelper.pickup(ground);
                BotRuntime.logConsole("[Clue/Container] loot " + ground.getName() + " " + r);
                return 500;
            }
        }

        // Alleen scannen als bank al open is — geen walk/open (CombatBot snapshot-pad).
        if (Bank.isOpen()) {
            if (now < emptyBankUntilMs) {
                Bank.close();
                return 400;
            }
            return tryBankWithdrawWhileOpen();
        }

        return -1;
    }

    /** Throttled console wanneer er echt niets te doen is. */
    public static void logWaitingIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastWaitLogMs < 8_000L) {
            return;
        }
        lastWaitLogMs = now;
        BotRuntime.logConsole("[Clue] geen beginner clue — wacht op geode/nest/drop (geen bank-trip)");
    }

    private static int openInv(IInventoryItem item, String action, String kind) {
        lastActionMs = System.currentTimeMillis();
        if (item.getName() != null) {
            placeholderNames.remove(item.getName().toLowerCase(Locale.ROOT));
        }
        boolean ok = item.interact(action);
        BotRuntime.logConsole("[Clue/Container] " + kind + " " + action + " → " + ok);
        return ok ? 900 : 600;
    }

    private static IInventoryItem findInvContainer(String[] names, String action) {
        for (String name : names) {
            IInventoryItem it = Inventory.getFirst(i -> i != null && i.getName() != null
                    && i.getName().equalsIgnoreCase(name)
                    && (action == null || i.hasAction(action)));
            if (it != null) {
                return it;
            }
        }
        if (names == GEODE_NAMES) {
            IInventoryItem byId = Inventory.getFirst(i -> i != null && i.getId() == ITEM_CLUE_GEODE_BEGINNER
                    && i.hasAction("Open"));
            if (byId != null) {
                return byId;
            }
        }
        if (names == BOTTLE_NAMES) {
            IInventoryItem byId = Inventory.getFirst(i -> i != null && i.getId() == ITEM_CLUE_BOTTLE_BEGINNER
                    && i.hasAction("Open"));
            if (byId != null) {
                return byId;
            }
        }
        return Inventory.getFirst(i -> {
            if (i == null || i.getName() == null) {
                return false;
            }
            String n = i.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("clue")) {
                return false;
            }
            boolean match = (names == GEODE_NAMES && n.contains("geode"))
                    || (names == BOTTLE_NAMES && n.contains("bottle"));
            return match && i.hasAction(action);
        });
    }

    private static IInventoryItem findInvNest() {
        for (String name : NEST_NAMES) {
            IInventoryItem it = Inventory.getFirst(i -> i != null && i.getName() != null
                    && i.getName().equalsIgnoreCase(name)
                    && (i.hasAction("Search") || i.hasAction("Open")));
            if (it != null) {
                return it;
            }
        }
        return Inventory.getFirst(i -> {
            if (i == null || i.getName() == null) {
                return false;
            }
            String n = i.getName().toLowerCase(Locale.ROOT);
            if (!(n.contains("nest") && (n.contains("clue") || n.contains("bird")))) {
                return false;
            }
            return i.hasAction("Search") || i.hasAction("Open");
        });
    }

    private static ITileItem nearestGroundContainer(WorldPoint me) {
        return TileItems.getNearest(item -> {
            if (item == null || item.getName() == null || item.getWorldLocation() == null) {
                return false;
            }
            if (me.distanceTo(item.getWorldLocation()) > GROUND_RADIUS) {
                return false;
            }
            if (!item.hasAction("Take")) {
                return false;
            }
            String n = item.getName().toLowerCase(Locale.ROOT);
            if (n.contains("clue") && (n.contains("geode") || n.contains("bottle") || n.contains("nest"))) {
                return true;
            }
            return n.contains("bird nest") || n.equals("bird nest");
        });
    }

    private static int tryBankWithdrawWhileOpen() {
        lastActionMs = System.currentTimeMillis();

        if (withdrawUsable(BeginnerClueReference.CLUE_SCROLL_BEGINNER)
                || withdrawUsableById(ITEM_CLUE_SCROLL_BEGINNER, BeginnerClueReference.CLUE_SCROLL_BEGINNER)) {
            emptyBankUntilMs = 0L;
            Bank.close();
            return 700;
        }
        for (String name : GEODE_NAMES) {
            if (withdrawUsable(name)) {
                emptyBankUntilMs = 0L;
                Bank.close();
                return 700;
            }
        }
        for (String name : BOTTLE_NAMES) {
            if (withdrawUsable(name)) {
                emptyBankUntilMs = 0L;
                Bank.close();
                return 700;
            }
        }
        for (String name : NEST_NAMES) {
            if (withdrawUsable(name)) {
                emptyBankUntilMs = 0L;
                Bank.close();
                return 700;
            }
        }
        BotRuntime.logConsole("[Clue/Container] bank open: geen scroll/geode/nest (of placeholder) — backoff 15min");
        emptyBankUntilMs = System.currentTimeMillis() + EMPTY_BANK_BACKOFF_MS;
        Bank.close();
        return 500;
    }

    private static boolean withdrawUsable(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (placeholderNames.contains(name.toLowerCase(Locale.ROOT))) {
            return false;
        }
        int qty = bankUsableQty(name);
        if (qty <= 0) {
            // Storm kan contains=true + qty 0 (placeholder) of qty 1 rapporteren — markeer bij mislukte withdraw.
            if (Bank.contains(name)) {
                int before = Inventory.getCount(true, name);
                try {
                    Bank.withdraw(name, 1);
                } catch (Throwable ignored) {
                }
                int after = Inventory.getCount(true, name);
                if (after <= before) {
                    placeholderNames.add(name.toLowerCase(Locale.ROOT));
                    BotRuntime.logConsole("[Clue/Container] placeholder: " + name);
                } else {
                    BotRuntime.logConsole("[Clue/Container] withdraw " + name);
                    return true;
                }
            }
            return false;
        }
        int before = Inventory.getCount(true, name);
        boolean ok = Bank.withdraw(name, 1);
        int after = Inventory.getCount(true, name);
        if (!ok || after <= before) {
            placeholderNames.add(name.toLowerCase(Locale.ROOT));
            BotRuntime.logConsole("[Clue/Container] placeholder/mislukt: " + name
                    + " (qty=" + qty + " inv " + before + "→" + after + ")");
            return false;
        }
        BotRuntime.logConsole("[Clue/Container] withdraw " + name);
        return true;
    }

    private static boolean withdrawUsableById(int itemId, String logName) {
        if (itemId <= 0) {
            return false;
        }
        try {
            var item = Bank.getFirst(i -> i != null && i.getId() == itemId);
            if (item == null) {
                return false;
            }
            String name = item.getName() != null ? item.getName() : logName;
            if (item.getQuantity() <= 0) {
                placeholderNames.add(name.toLowerCase(Locale.ROOT));
                return false;
            }
            return withdrawUsable(name);
        } catch (Throwable ignored) {
            return false;
        }
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
}
