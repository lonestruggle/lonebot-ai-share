package com.lonebot.example.woodcutter;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.widgets.Widgets;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OSRS "How many would you like to burn?" — interface 270.
 * Adapted from CombatBot: {@code Static.getClient()} + Mouse/Widgets/Keyboard (no paint/AntiBan).
 */
final class BonfireQtyHelper {

    static final int IFACE_GROUP = 270;
    static final int CHILD_TITLE = 3;
    static final int CHILD_LOG_ITEM = 15;

    private static final int MIN_CLICK_W = 40;
    private static final int MIN_CLICK_H = 40;
    private static final long MIN_CONFIRM_INTERVAL_MS = 2_000L;

    private static volatile long lastConfirmAttemptMs;
    private static volatile boolean lastDialogOpen;

    private BonfireQtyHelper() {
    }

    static boolean isDialogOpen() {
        lastDialogOpen = detectDialogOpen();
        return lastDialogOpen;
    }

    static boolean isTitleWidgetVisible() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client client = Static.getClient();
            if (client == null) {
                return false;
            }
            Widget title = client.getWidget(IFACE_GROUP, CHILD_TITLE);
            if (title == null) {
                return false;
            }
            try {
                if (title.isHidden() || title.isSelfHidden()) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            if (!titleTextIndicatesBurn(rlWidgetDisplayText(title))) {
                return false;
            }
            Rectangle bounds = rlWidgetCanvasRect(title);
            return bounds != null && bounds.width >= 80 && bounds.height >= 8;
        }, false));
    }

    /** Zolang true: nooit log-useOn-fire — alleen {@link #tryConfirm}. */
    static boolean blocksLogUseOnFire() {
        return isTitleWidgetVisible() || isDialogOpen();
    }

    static boolean shouldConfirmQty() {
        return isTitleWidgetVisible() || detectDialogOpen();
    }

    /**
     * @param force true als qty-wacht actief maar detectie flikkert
     * @return wait-ms (&gt;0) bij poging/cooldown, anders 0
     */
    static int tryConfirm(boolean force) {
        boolean open = shouldConfirmQty();
        if (!open && !force) {
            if (!lastDialogOpen) {
                lastConfirmAttemptMs = 0L;
            }
            return 0;
        }
        if (!open && force) {
            WcDebug.log("fm-qty", "force confirm (awaiting)");
        }

        long now = System.currentTimeMillis();
        long sinceLast = now - lastConfirmAttemptMs;
        if (lastConfirmAttemptMs > 0L && sinceLast < MIN_CONFIRM_INTERVAL_MS) {
            int wait = (int) (MIN_CONFIRM_INTERVAL_MS - sinceLast + 200 + Rand.nextInt(0, 400));
            return Math.max(400, wait);
        }

        lastConfirmAttemptMs = now;
        String method = performConfirmAttempt();
        WcDebug.once("fm-qty", method);
        return Rand.nextInt(1800, 2800);
    }

    private static String performConfirmAttempt() {
        Point click = Static.callOnClientThread(() -> {
            Client client = Static.getClient();
            if (client == null) {
                return null;
            }
            Widget rlItem = client.getWidget(IFACE_GROUP, CHILD_LOG_ITEM);
            Rectangle rlRect = rlWidgetCanvasRect(rlItem);
            if (isGoodClickRect(rlRect)) {
                return randomInBounds(rlRect);
            }
            Rectangle scanned = findLogIconClickBounds(client);
            return isGoodClickRect(scanned) ? randomInBounds(scanned) : null;
        }, null);
        if (click != null && MouseManager.interactAt(click)) {
            return "RL-klik 270,15";
        }

        if (WcWidgets.clickRelaxed(IFACE_GROUP, CHILD_LOG_ITEM, MIN_CLICK_W, MIN_CLICK_H)) {
            return "WcWidgets-klik 270,15";
        }

        IWidget stormItem = Widgets.get(IFACE_GROUP, CHILD_LOG_ITEM);
        if (stormItem != null) {
            try {
                if (!stormItem.isHidden()) {
                    if (stormItem.hasAction("Burn") && stormItem.interact("Burn")) {
                        return "interact Burn 270,15";
                    }
                    if (stormItem.hasAction("Cook") && stormItem.interact("Cook")) {
                        return "interact Cook 270,15";
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        pressSpace();
        return "spatie";
    }

    private static void pressSpace() {
        try {
            Keyboard.pressed(KeyEvent.VK_SPACE);
            Keyboard.released(KeyEvent.VK_SPACE);
        } catch (Throwable ignored) {
            try {
                Keyboard.pressKey(KeyEvent.VK_SPACE);
            } catch (Throwable ignored2) {
            }
        }
    }

    private static boolean detectDialogOpen() {
        if (isTitleWidgetVisible()) {
            return true;
        }
        if (titleTextIndicatesBurn(readRlWidgetText(IFACE_GROUP, CHILD_TITLE))) {
            return true;
        }
        if (titleTextIndicatesBurn(readRlWidgetText(IFACE_GROUP, 0))) {
            return true;
        }
        IWidget stormTitle = Widgets.get(IFACE_GROUP, CHILD_TITLE);
        if (titleTextIndicatesBurn(stormWidgetText(stormTitle))) {
            return true;
        }
        IWidget stormRoot = Widgets.get(IFACE_GROUP, 0);
        return titleTextIndicatesBurn(stormWidgetText(stormRoot));
    }

    private static boolean titleTextIndicatesBurn(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("how many") && lower.contains("burn");
    }

    private static Rectangle findLogIconClickBounds(Client client) {
        Widget direct = client.getWidget(IFACE_GROUP, CHILD_LOG_ITEM);
        Rectangle r = rlWidgetCanvasRect(direct);
        if (isGoodClickRect(r)) {
            return r;
        }
        Widget root = client.getWidget(IFACE_GROUP, 0);
        if (root == null) {
            return null;
        }
        return bestClickCandidate(root, null, 0);
    }

    private static Rectangle bestClickCandidate(Widget w, Rectangle best, int bestArea) {
        if (w == null) {
            return best;
        }
        Rectangle r = rlWidgetCanvasRect(w);
        if (isGoodClickRect(r)) {
            int area = r.width * r.height;
            if (area > bestArea) {
                best = r;
                bestArea = area;
            }
        }
        try {
            Widget[] children = w.getChildren();
            if (children != null) {
                for (Widget c : children) {
                    best = bestClickCandidate(c, best, best != null ? best.width * best.height : bestArea);
                    if (best != null) {
                        bestArea = best.width * best.height;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Widget[] dyn = w.getDynamicChildren();
            if (dyn != null) {
                for (Widget c : dyn) {
                    best = bestClickCandidate(c, best, best != null ? best.width * best.height : bestArea);
                    if (best != null) {
                        bestArea = best.width * best.height;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    private static boolean isGoodClickRect(Rectangle r) {
        return r != null && r.width >= MIN_CLICK_W && r.height >= MIN_CLICK_H;
    }

    private static String readRlWidgetText(int group, int child) {
        return Static.callOnClientThread(() -> {
            Client client = Static.getClient();
            if (client == null) {
                return "";
            }
            return rlWidgetDisplayText(client.getWidget(group, child));
        }, "");
    }

    private static String stormWidgetText(IWidget w) {
        if (w == null) {
            return "";
        }
        try {
            String text = w.getText();
            if (text != null && !text.isEmpty()) {
                return text.trim();
            }
        } catch (Throwable ignored) {
        }
        try {
            String name = w.getName();
            return name != null ? name.trim() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String rlWidgetDisplayText(Widget w) {
        if (w == null) {
            return "";
        }
        try {
            String t = w.getText();
            if (t != null && !t.isEmpty()) {
                return t;
            }
        } catch (Throwable ignored) {
        }
        try {
            String n = w.getName();
            return n != null ? n : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Rectangle rlWidgetCanvasRect(Widget w) {
        if (w == null) {
            return null;
        }
        try {
            if (w.isHidden()) {
                return null;
            }
        } catch (Throwable ignored) {
        }
        try {
            Rectangle b = w.getBounds();
            if (b != null && b.width > 0 && b.height > 0) {
                return b;
            }
        } catch (Throwable ignored) {
        }
        try {
            Point loc = w.getCanvasLocation();
            int ww = w.getWidth();
            int wh = w.getHeight();
            if (loc != null && ww > 0 && wh > 0) {
                return new Rectangle(loc.getX(), loc.getY(), ww, wh);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Point randomInBounds(Rectangle b) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x = b.x + 2 + r.nextInt(Math.max(1, b.width - 4));
        int y = b.y + 2 + r.nextInt(Math.max(1, b.height - 4));
        return new Point(x, y);
    }
}
