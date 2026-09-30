package com.lonebot.example.woodcutter;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.widgets.Tab;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Production;
import net.storm.sdk.widgets.Tabs;

import java.awt.Rectangle;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 10 manieren om tinderbox↔logs te combineren (nieuw vuur).
 * Ook aanroepbaar vanuit LoneBot Test-tab via {@link #runTest(int)}.
 */
public final class FmLightHelper {

    public enum Pending {
        NONE,
        TINDER_SELECTED,
        LOG_SELECTED
    }

    public static final class Result {
        public final boolean ok;
        public final Pending pending;
        public final String method;
        public final int delayMs;

        Result(boolean ok, Pending pending, String method, int delayMs) {
            this.ok = ok;
            this.pending = pending != null ? pending : Pending.NONE;
            this.method = method != null ? method : "?";
            this.delayMs = delayMs;
        }

        static Result fail(String method) {
            return new Result(false, Pending.NONE, method, 0);
        }

        static Result done(String method) {
            return new Result(true, Pending.NONE, method, Rand.nextInt(1200, 1800));
        }

        static Result pending(Pending p, String method) {
            return new Result(true, p, method, Rand.nextInt(220, 420));
        }
    }

    private static final String[] NAMES = {
            "1 useOn tinder→log",
            "2 useOn log→tinder",
            "3 menu inv-on-inv tinder",
            "4 menu inv-on-inv log",
            "5 menu+pause 250ms",
            "6 2-tick Use tinder",
            "7 2-tick Use log",
            "8 mouse tinder→log",
            "9 mouse log→tinder",
            "10 raw invoke+pause"
    };

    public static final int METHOD_COUNT = NAMES.length;

    private FmLightHelper() {
    }

    public static String[] methodLabels() {
        return NAMES.clone();
    }

    public static String nameOf(int index) {
        int i = Math.floorMod(index, METHOD_COUNT);
        return NAMES[i];
    }

    /**
     * Test-tab entry: één methode (incl. 2-tick complete + diagnose bij fail).
     */
    public static String runTest(int methodIndex) {
        ensureInventoryTab();
        IInventoryItem tinder = Inventory.getFirst("Tinderbox");
        IInventoryItem log = firstAnyLog();
        String diag = diagnose();
        BotRuntime.logConsole("[FmLightTest] START #" + (Math.floorMod(methodIndex, METHOD_COUNT) + 1)
                + " " + nameOf(methodIndex));
        BotRuntime.logConsole("[FmLightTest] " + diag);
        if (tinder == null) {
            return "⚠ geen Tinderbox | " + diag;
        }
        if (log == null) {
            return "⚠ geen logs in inv | " + diag;
        }
        Result r = tryMethod(methodIndex, tinder, log);
        BotRuntime.logConsole("[FmLightTest] step1 ok=" + r.ok + " pending=" + r.pending
                + " method=" + r.method);
        if (!r.ok) {
            return "✗ " + r.method + " faalde | " + diag;
        }
        if (r.pending != Pending.NONE) {
            sleepQuiet(Math.max(250, r.delayMs));
            IInventoryItem t2 = Inventory.getFirst("Tinderbox");
            IInventoryItem l2 = firstAnyLog();
            Result r2 = completePending(r.pending, t2, l2);
            BotRuntime.logConsole("[FmLightTest] step2 ok=" + r2.ok + " method=" + r2.method);
            return (r2.ok ? "✓ " : "✗ ") + r.method + " → " + r2.method
                    + " ok=" + r2.ok + " | tinder@" + (tinder.getSlot())
                    + " log@" + log.getSlot();
        }
        return "✓ " + r.method + " ok | tinder@" + tinder.getSlot() + " log@" + log.getSlot()
                + " (" + safeName(log) + ")";
    }

    /** Waarom bot-loop light misschien nooit bereikt. */
    public static String diagnose() {
        Players.LocalSnap me = Players.snapshotLocal();
        String burn = BonfireHandler.bestBurnableLog();
        return "tinder=" + (Inventory.contains("Tinderbox") || Inventory.contains(WcTrees.TINDERBOX_ID))
                + " logs=" + BonfireHandler.countAllLogs()
                + " burnable=" + (burn != null ? burn : "—")
                + " fmLvl=" + WcTrees.fmLevel()
                + " prodOpen=" + Production.isOpen()
                + " qtyTitle=" + BonfireQtyHelper.isTitleWidgetVisible()
                + " qtyDlg=" + BonfireQtyHelper.isDialogOpen()
                + " moving=" + (me != null && me.moving)
                + " dest=" + Movement.hasDestination()
                + " anim=" + net.storm.sdk.game.Animations.describe(me != null ? me.animation : -1)
                + " free=" + Inventory.getFreeSlots();
    }

    static void ensureInventoryTab() {
        try {
            if (!Tabs.isOpen(Tab.INVENTORY)) {
                Tabs.open(Tab.INVENTORY);
            }
        } catch (Throwable ignored) {
        }
    }

    static IInventoryItem firstAnyLog() {
        String best = BonfireHandler.bestBurnableLog();
        if (best != null) {
            IInventoryItem i = Inventory.getFirst(best);
            if (i != null) {
                return i;
            }
        }
        return Inventory.getFirst(it -> it != null
                && (WcTrees.isLogId(it.getId()) || WcTrees.isLogName(it.getName())));
    }

    /**
     * Probeer methode {@code attempt % 10}.
     */
    public static Result tryMethod(int attempt, IInventoryItem tinder, IInventoryItem log) {
        if (tinder == null || log == null) {
            return Result.fail("geen items");
        }
        ensureInventoryTab();
        int i = Math.floorMod(attempt, METHOD_COUNT);
        String name = NAMES[i];
        try {
            switch (i) {
                case 0:
                    return tinder.useOn(log) ? Result.done(name) : Result.fail(name);
                case 1:
                    return log.useOn(tinder) ? Result.done(name) : Result.fail(name);
                case 2:
                    return menuInvOnInv(tinder, log, name);
                case 3:
                    return menuInvOnInv(log, tinder, name);
                case 4:
                    return menuWithPause(tinder, log, 250L, name);
                case 5:
                    return tinder.interact("Use")
                            ? Result.pending(Pending.TINDER_SELECTED, name)
                            : Result.fail(name);
                case 6:
                    return log.interact("Use")
                            ? Result.pending(Pending.LOG_SELECTED, name)
                            : Result.fail(name);
                case 7:
                    return mouseThen(tinder, log, name);
                case 8:
                    return mouseThen(log, tinder, name);
                case 9:
                    return menuWithPause(tinder, log, 400L, name);
                default:
                    return Result.fail(name);
            }
        } catch (Throwable t) {
            WcDebug.log("fm-light", name + " throw " + t.getClass().getSimpleName());
            return Result.fail(name);
        }
    }

    /** Voltooi 2-tick selectie (tinder of log al op Use). */
    public static Result completePending(Pending pending, IInventoryItem tinder, IInventoryItem log) {
        if (pending == Pending.TINDER_SELECTED) {
            if (log == null) {
                return Result.fail("pending tinder, geen log");
            }
            String target = "Tinderbox -> " + safeName(log);
            if (MenuInteract.useSelectedOnInventory(log.getSlot(), log.getId(), target)) {
                return Result.done("pending→log menu");
            }
            if (log.interact("Use")) {
                return Result.done("pending→log interact");
            }
            if (tinder != null && tinder.useOn(log)) {
                return Result.done("pending→useOn");
            }
            if (mouseClickSlot(log.getSlot())) {
                return Result.done("pending→log mouse");
            }
            return Result.fail("pending tinder complete");
        }
        if (pending == Pending.LOG_SELECTED) {
            if (tinder == null) {
                tinder = Inventory.getFirst("Tinderbox");
            }
            if (tinder == null) {
                return Result.fail("pending log, geen tinder");
            }
            String target = safeName(log) + " -> Tinderbox";
            if (MenuInteract.useSelectedOnInventory(tinder.getSlot(), tinder.getId(), target)) {
                return Result.done("pending→tinder menu");
            }
            if (tinder.interact("Use")) {
                return Result.done("pending→tinder interact");
            }
            if (log != null && log.useOn(tinder)) {
                return Result.done("pending→useOn");
            }
            if (mouseClickSlot(tinder.getSlot())) {
                return Result.done("pending→tinder mouse");
            }
            return Result.fail("pending log complete");
        }
        return Result.fail("geen pending");
    }

    private static Result menuInvOnInv(IInventoryItem from, IInventoryItem to, String name) {
        int packed = ComponentID.INVENTORY_CONTAINER;
        boolean ok = MenuInteract.useInventoryOnWidget(
                from.getSlot(), from.getId(), safeName(from),
                to.getSlot(), packed, to.getId(), safeName(to));
        return ok ? Result.done(name) : Result.fail(name);
    }

    private static Result menuWithPause(IInventoryItem from, IInventoryItem to, long pauseMs, String name) {
        int packed = ComponentID.INVENTORY_CONTAINER;
        String fromName = safeName(from);
        String toName = safeName(to);
        boolean selected = MenuInteract.invokeMenu("Use", fromName, 0,
                MenuAction.WIDGET_TARGET.getId(), from.getSlot(), packed, from.getId());
        if (!selected) {
            return Result.fail(name);
        }
        sleepQuiet(pauseMs);
        String target = fromName + " -> " + toName;
        boolean on = MenuInteract.useSelectedOnInventory(to.getSlot(), to.getId(), target);
        return on ? Result.done(name) : Result.fail(name);
    }

    private static Result mouseThen(IInventoryItem first, IInventoryItem second, String name) {
        if (!mouseClickSlot(first.getSlot())) {
            return Result.fail(name);
        }
        sleepQuiet(120L + ThreadLocalRandom.current().nextInt(80));
        if (!mouseClickSlot(second.getSlot())) {
            return Result.pending(
                    "Tinderbox".equalsIgnoreCase(safeName(first)) ? Pending.TINDER_SELECTED : Pending.LOG_SELECTED,
                    name + " (half)");
        }
        return Result.done(name);
    }

    private static boolean mouseClickSlot(int slot) {
        if (slot < 0) {
            return false;
        }
        Point p = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget container = c.getWidget(ComponentID.INVENTORY_CONTAINER);
            if (container == null) {
                return null;
            }
            Widget[] children = container.getDynamicChildren();
            if (children == null || children.length == 0) {
                children = container.getChildren();
            }
            if (children == null) {
                return null;
            }
            Widget hit = null;
            for (Widget ch : children) {
                if (ch != null && ch.getIndex() == slot) {
                    hit = ch;
                    break;
                }
            }
            if (hit == null && slot < children.length) {
                hit = children[slot];
            }
            if (hit == null) {
                return null;
            }
            Rectangle b = hit.getBounds();
            if (b == null || b.width < 4 || b.height < 4) {
                return null;
            }
            int x = b.x + 4 + ThreadLocalRandom.current().nextInt(Math.max(1, b.width - 8));
            int y = b.y + 4 + ThreadLocalRandom.current().nextInt(Math.max(1, b.height - 8));
            return new Point(x, y);
        }, null);
        return p != null && MouseManager.interactAt(p);
    }

    private static String safeName(IInventoryItem item) {
        if (item == null || item.getName() == null) {
            return "";
        }
        return item.getName();
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(Math.max(40L, ms));
        } catch (InterruptedException ignored) {
            // interrupt-flag niet zetten — LoopHost
        }
    }
}
