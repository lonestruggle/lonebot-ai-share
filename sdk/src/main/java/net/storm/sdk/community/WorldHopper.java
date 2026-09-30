package net.storm.sdk.community;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.World;
import net.runelite.api.widgets.Widget;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.api.widgets.Tab;
import net.storm.sdk.widgets.Tabs;
import net.storm.sdk.widgets.Widgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Storm-compat world hop via {@code Client.hopToWorld} / world-switcher widgets.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/community/package-summary.html">Storm community</a>
 */
public final class WorldHopper {

    private static final Logger log = LoggerFactory.getLogger(WorldHopper.class);

    /** World switcher group (logout panel). */
    public static final int WORLD_SWITCHER_GROUP = 69;
    /** Niet opnieuw openen: openWorldHopper togglet de hopper dicht. */
    private static final long HOPPER_REOPEN_MS = 6_000L;
    /** Na openen: hopToWorld zodra de wereldlijst er is, ook als isHidden liegt. */
    private static final long HOP_AFTER_OPEN_MS = 1_800L;

    private static volatile long hopperOpenedAtMs;
    private static volatile long lastHopLogMs;
    private static volatile String lastHopLog = "";

    private WorldHopper() {
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(
                () -> hopperUiOpen(Static.getClient()), false));
    }

    /**
     * Hopper dicht na hop — anders slikken minimap-walk-kliks de wereldlijst.
     * ESC i.p.v. {@code openWorldHopper}-toggle (die opent hem weer als timing mis zit).
     */
    public static boolean closeIfOpen() {
        return dismissToInventory();
    }

    /**
     * Failsafe: inventory-tab (ESC of klik) — hopper/logout verdwijnt.
     * Alleen als de wereldlijst écht zichtbaar is.
     */
    public static boolean dismissToInventory() {
        if (!isOpen()) {
            return false;
        }
        try {
            Tabs.open(Tab.INVENTORY);
        } catch (Throwable t) {
            Keyboard.pressKey(java.awt.event.KeyEvent.VK_ESCAPE);
        }
        hopLog("hopper → inventory");
        return true;
    }

    /**
     * Echt open = zichtbare wereld-rijen (Switch/Hop), niet leftover bounds van hidden widgets.
     */
    private static boolean hopperUiOpen(Client c) {
        if (c == null) {
            return false;
        }
        for (int child = 0; child <= 24; child++) {
            Widget root = c.getWidget(WORLD_SWITCHER_GROUP, child);
            if (root == null || safeHidden(root)) {
                continue;
            }
            if (hasVisibleWorldRow(root, 0)) {
                return true;
            }
        }
        return false;
    }

    /** Resultaat van {@link #hopDetailed}: hopper-open ≠ al gehopped. */
    public enum HopIssue {
        FAIL,
        ALREADY_THERE,
        CONFIRM,
        HOPPER_OPENED,
        HOPPED
    }

    public static boolean hop(int worldId) {
        return hopDetailed(worldId) != HopIssue.FAIL;
    }

    /** Na hop-failsafe: openWorldHopper mag niet toggelen omdat de klok nog loopt. */
    public static void resetOpenClock() {
        hopperOpenedAtMs = 0L;
    }

    /** Hopper-rij klikken als {@code hopToWorld} niet landt. */
    public static HopIssue hopViaRow(int worldId) {
        if (worldId <= 0) {
            return HopIssue.FAIL;
        }
        HopIssue[] box = { HopIssue.FAIL };
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            if (c.getWorld() == worldId) {
                box[0] = HopIssue.ALREADY_THERE;
                return true;
            }
            if (findSwitchWorldButton(c) != null) {
                boolean ok = clickSwitchWorldConfirmOnClient(c);
                box[0] = ok ? HopIssue.CONFIRM : HopIssue.FAIL;
                return ok;
            }
            boolean row = clickWorldInSwitcher(c, worldId);
            hopLog(row ? "rij klik W" + worldId : "rij-miss W" + worldId);
            box[0] = row ? HopIssue.HOPPED : HopIssue.FAIL;
            return row;
        }, false);
        return box[0];
    }

    /**
     * Zorg dat {@code Client.getWorldList()} gevuld is (F2P-filter vóór hop-keuze).
     * @return true als er werelden in de lijst staan
     */
    public static boolean ensureWorldListLoaded() {
        try {
            World[] all = net.storm.sdk.game.Worlds.getAll(true);
            if (all != null && all.length > 0) {
                net.storm.sdk.game.Worlds.refreshWorldTypeCacheFromLive(true);
                return true;
            }
        } catch (Throwable ignored) {
        }
        Boolean opened = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                World[] list = c.getWorldList();
                if (list != null && list.length > 0) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                c.openWorldHopper();
                hopperOpenedAtMs = System.currentTimeMillis();
                hopLog("hopper openen (F2P-lijst laden)");
                return true;
            } catch (Throwable t) {
                hopLog("hopper open fail: " + t.getClass().getSimpleName());
                return false;
            }
        }, false);
        try {
            net.storm.sdk.game.Worlds.refreshWorldTypeCacheFromLive(true);
            if (net.storm.sdk.game.Worlds.hasWorldTypeCache()) {
                return true;
            }
            World[] all = net.storm.sdk.game.Worlds.getAll(true);
            return all != null && all.length > 0;
        } catch (Throwable t) {
            return Boolean.TRUE.equals(opened);
        }
    }

    public static HopIssue hopDetailed(int worldId) {
        if (worldId <= 0) {
            return HopIssue.FAIL;
        }
        try {
            if (net.storm.sdk.items.Bank.isOpen()) {
                log.info("[WorldHopper] hop {} overgeslagen — bank open", worldId);
                return HopIssue.FAIL;
            }
        } catch (Throwable ignored) {
        }
        HopIssue[] box = { HopIssue.FAIL };
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                box[0] = HopIssue.FAIL;
                return false;
            }
            GameState gs = c.getGameState();
            if (gs != GameState.LOGGED_IN && gs != GameState.HOPPING) {
                log.warn("[WorldHopper] hop {} skipped — state {}", worldId, gs);
                box[0] = HopIssue.FAIL;
                return false;
            }
            if (c.getWorld() == worldId) {
                hopperOpenedAtMs = 0L;
                hopLog("al op W" + worldId);
                box[0] = HopIssue.ALREADY_THERE;
                return true;
            }
            if (findSwitchWorldButton(c) != null) {
                boolean ok = clickSwitchWorldConfirmOnClient(c);
                box[0] = ok ? HopIssue.CONFIRM : HopIssue.FAIL;
                return ok;
            }
            boolean ui = hopperUiOpen(c);
            World target = findWorld(c, worldId);
            long now = System.currentTimeMillis();
            boolean openedRecently = hopperOpenedAtMs > 0L && now - hopperOpenedAtMs < 20_000L;
            boolean canHopToWorld = target != null && (ui
                    || (openedRecently && now - hopperOpenedAtMs >= HOP_AFTER_OPEN_MS));
            if (canHopToWorld) {
                try {
                    c.hopToWorld(target);
                    hopLog("hopToWorld W" + worldId + (ui ? " (ui)" : " (lijst na open)"));
                    box[0] = HopIssue.HOPPED;
                    return true;
                } catch (Throwable t) {
                    hopLog("hopToWorld fail: " + t.getClass().getSimpleName() + " — hopper-rij");
                }
                boolean row = clickWorldInSwitcher(c, worldId);
                box[0] = row ? HopIssue.HOPPED : HopIssue.FAIL;
                hopLog(row ? "rij klik W" + worldId : "rij-miss W" + worldId);
                return row;
            }
            if (ui) {
                boolean row = clickWorldInSwitcher(c, worldId);
                box[0] = row ? HopIssue.HOPPED : HopIssue.FAIL;
                hopLog(row ? "rij klik W" + worldId : "W" + worldId + " niet in hopper-lijst");
                return row;
            }
            if (hopperOpenedAtMs > 0L && now - hopperOpenedAtMs < HOPPER_REOPEN_MS) {
                hopLog("hopper wacht lijst W" + worldId);
                box[0] = HopIssue.HOPPER_OPENED;
                return true;
            }
            try {
                c.openWorldHopper();
                hopperOpenedAtMs = now;
                hopLog("hopper openen W" + worldId);
                box[0] = HopIssue.HOPPER_OPENED;
                return true;
            } catch (Throwable t) {
                hopLog("openWorldHopper fail: " + t.getClass().getSimpleName());
                box[0] = HopIssue.FAIL;
                return false;
            }
        }, false);
        return box[0];
    }

    private static void hopLog(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastHopLog) && now - lastHopLogMs < 1600L) {
            return;
        }
        lastHopLog = msg;
        lastHopLogMs = now;
        log.info("[WorldHopper] {}", msg);
        try {
            net.storm.sdk.bot.BotRuntime.logConsole("[Hop] " + msg);
        } catch (Throwable ignored) {
        }
    }

    private static World findWorld(Client c, int worldId) {
        try {
            World[] list = c.getWorldList();
            if (list != null) {
                for (World w : list) {
                    if (w != null && w.getId() == worldId) {
                        return w;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean clickWorldInSwitcher(Client c, int worldId) {
        String needle = String.valueOf(worldId);
        if (!hopperUiOpen(c)) {
            try {
                c.openWorldHopper();
                hopperOpenedAtMs = System.currentTimeMillis();
            } catch (Throwable ignored) {
            }
        }
        Widget hit = null;
        for (int child = 0; child <= 24 && hit == null; child++) {
            Widget root = c.getWidget(WORLD_SWITCHER_GROUP, child);
            if (root == null) {
                continue;
            }
            hit = findWorldRow(root, needle, 0);
        }
        if (hit == null) {
            log.warn("[WorldHopper] world {} row not found", worldId);
            return false;
        }
        boolean clicked = Widgets.wrap(hit).interact("Switch")
                || Widgets.wrap(hit).interact("Hop")
                || net.storm.sdk.interact.mouse.MouseManager.interactAt(
                net.storm.sdk.interact.ClickPoints.forWidgetOnClient(hit));
        clickSwitchWorldConfirmOnClient(c);
        return clicked || findSwitchWorldButton(c) != null;
    }

    /**
     * OSRS hop-confirm: "Are you sure you wish to switch to World …?" → knop Switch world.
     * HoverCapture: {@code 193,0} knop / {@code 193,2} tekst. {@code isHidden()} is onbetrouwbaar.
     */
    public static boolean isHopConfirmOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(
                () -> findSwitchWorldButton(Static.getClient()) != null, false));
    }

    /** Klik {@code Switch world} (niet Cancel). Packed 193,0 als fallback. */
    public static boolean confirmSwitchWorld() {
        Boolean ok = Static.callOnClientThread(() -> clickSwitchWorldConfirmOnClient(Static.getClient()), false);
        return Boolean.TRUE.equals(ok);
    }

    /** Console-dump voor Test-tab. */
    public static String diagnose() {
        String[] box = {""};
        Static.callOnClientThread(() -> {
            box[0] = diagnoseOnClient(Static.getClient());
            return true;
        }, false);
        return box[0] != null ? box[0] : "";
    }

    private static boolean clickSwitchWorldConfirmOnClient(Client c) {
        Widget btn = findSwitchWorldButton(c);
        if (btn == null) {
            return false;
        }
        boolean clicked = menuClickSwitchWorld(btn);
        if (!clicked) {
            clicked = net.storm.sdk.interact.MenuInteract.invokeMenu(
                    "Switch world", "", 1,
                    net.runelite.api.MenuAction.CC_OP.getId(),
                    -1, btn.getId());
        }
        hopLog("Switch world packed=" + btn.getId() + " found=true ok=" + clicked);
        return clicked;
    }

    private static boolean menuClickSwitchWorld(Widget btn) {
        int packed = btn.getId();
        int p0 = btn.getIndex();
        if (p0 < 0) {
            p0 = -1;
        }
        String target = btn.getName();
        if (target == null || target.isBlank()) {
            target = btn.getText();
        }
        if (target == null) {
            target = "";
        }
        target = stripTags(target);
        return net.storm.sdk.interact.MenuInteract.invokeMenu(
                "Switch world", target, 1,
                net.runelite.api.MenuAction.CC_OP.getId(),
                p0, packed)
                || net.storm.sdk.interact.MenuInteract.invokeMenu(
                "Switch world", "", 1,
                net.runelite.api.MenuAction.CC_OP.getId(),
                -1, packed);
    }

    private static Widget findSwitchWorldButton(Client c) {
        if (c == null) {
            return null;
        }
        for (int child = 0; child <= 24; child++) {
            Widget hit = scanSwitchWorld(c.getWidget(193, child), 0);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static Widget scanSwitchWorld(Widget w, int depth) {
        if (w == null || depth > 8) {
            return null;
        }
        if (isSwitchWorldButton(w)) {
            return w;
        }
        Widget[][] bags = {w.getDynamicChildren(), w.getStaticChildren(), w.getChildren()};
        for (Widget[] kids : bags) {
            if (kids == null) {
                continue;
            }
            for (Widget k : kids) {
                Widget hit = scanSwitchWorld(k, depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static boolean isSwitchWorldButton(Widget w) {
        if (w == null || !hasSwitchWorldLabel(w)) {
            return false;
        }
        if (!safeHidden(w)) {
            return true;
        }
        try {
            java.awt.Rectangle b = w.getBounds();
            return b != null && b.width > 10 && b.height > 8;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasSwitchWorldLabel(Widget w) {
        String[] actions = w.getActions();
        if (actions != null) {
            for (String a : actions) {
                if (a != null && stripTags(a).equalsIgnoreCase("Switch world")) {
                    return true;
                }
            }
        }
        return stripTags(w.getText()).equalsIgnoreCase("Switch world");
    }

    private static String stripTags(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<col=[^>]*>", "").replace("</col>", "").replace('\n', ' ').trim();
    }

    private static String diagnoseOnClient(Client c) {
        if (c == null) {
            return "geen client";
        }
        StringBuilder sb = new StringBuilder();
        Widget btn = findSwitchWorldButton(c);
        sb.append("match=").append(btn != null);
        if (btn != null) {
            sb.append(" packed=").append(btn.getId())
                    .append(" hidden=").append(safeHidden(btn))
                    .append(" text=").append(stripTags(btn.getText()));
        }
        sb.append(" | 193:");
        int n = 0;
        for (int child = 0; child <= 12; child++) {
            Widget w = c.getWidget(193, child);
            if (w == null) {
                continue;
            }
            n++;
            sb.append(" [").append(child)
                    .append(" hidden=").append(safeHidden(w))
                    .append(" text=").append(trunc(stripTags(w.getText()), 40))
                    .append(" act=").append(trunc(actionsOf(w), 30))
                    .append(']');
        }
        if (n == 0) {
            sb.append(" (geen widgets)");
        }
        return sb.toString();
    }

    private static boolean safeHidden(Widget w) {
        try {
            return w.isHidden();
        } catch (Throwable t) {
            return true;
        }
    }

    private static String actionsOf(Widget w) {
        try {
            String[] a = w.getActions();
            if (a == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String x : a) {
                if (x == null || x.isBlank()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append(stripTags(x));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String trunc(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static boolean hasVisibleWorldRow(Widget w, int depth) {
        if (w == null || depth > 8 || safeHidden(w)) {
            return false;
        }
        if (looksLikeWorldRow(w)) {
            return true;
        }
        Widget[][] bags = {w.getDynamicChildren(), w.getStaticChildren(), w.getChildren()};
        for (Widget[] kids : bags) {
            if (kids == null) {
                continue;
            }
            for (Widget k : kids) {
                if (hasVisibleWorldRow(k, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean looksLikeWorldRow(Widget w) {
        try {
            String[] actions = w.getActions();
            if (actions != null) {
                for (String a : actions) {
                    if (a == null) {
                        continue;
                    }
                    String s = stripTags(a);
                    if (s.equalsIgnoreCase("Switch") || s.equalsIgnoreCase("Hop")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String t = stripTags(w.getText());
        return t.matches("\\d{3,4}");
    }

    private static Widget findWorldRow(Widget w, String needle, int depth) {
        if (w == null || depth > 10) {
            return null;
        }
        String t = w.getText();
        String n = w.getName();
        if ((t != null && t.contains(needle)) || (n != null && n.contains(needle))) {
            return w;
        }
        Widget[] kids = w.getDynamicChildren();
        if (kids == null) {
            kids = w.getChildren();
        }
        if (kids != null) {
            for (Widget k : kids) {
                Widget found = findWorldRow(k, needle, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
