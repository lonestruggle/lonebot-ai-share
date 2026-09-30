package net.storm.sdk.game;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.widgets.Widgets;

import java.awt.event.KeyEvent;

/**
 * Storm {@code WorldMap} — open/close and player-position query.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/game/package-summary.html">Storm game</a>
 */
public final class WorldMap {

    public static final int WORLD_MAP_GROUP = 595;
    /** Minimap-chrome orbs (capture: 160,55 Floating World Map / ComponentID). */
    private static final int[] ORB_CHILDREN = {55, 49, 48};

    private static volatile long lastDismissLogMs;

    private WorldMap() {
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                java.lang.reflect.Method m = c.getClass().getMethod("isWorldMapOpen");
                Object v = m.invoke(c);
                if (Boolean.TRUE.equals(v)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            Widget w = c.getWidget(WORLD_MAP_GROUP, 0);
            return w != null && !w.isHidden();
        }, false));
    }

    public static void open() {
        if (isOpen()) {
            return;
        }
        Keyboard.pressKey(KeyEvent.VK_M);
        if (!isOpen()) {
            clickMinimapOrb();
        }
    }

    public static void close() {
        if (!isOpen()) {
            return;
        }
        Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        if (isOpen()) {
            tryCloseButton();
        }
        if (isOpen()) {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        }
    }

    /**
     * Tijdens lopen: als World Map open staat (per ongeluk orb-klik), dicht met ESC.
     *
     * @return {@code true} als de map open was en we dicht hebben geprobeerd
     */
    public static boolean dismissIfOpenDuringWalk() {
        if (!isOpen()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastDismissLogMs > 2_000L) {
            lastDismissLogMs = now;
            BotRuntime.logConsole("[Walk] World Map open — dicht (walker blijft)");
        }
        // Eerst Close-knop; ESC alleen met suppress — anders WorldWalkerHotkeys cancel’t de walk.
        tryCloseButton();
        if (!isOpen()) {
            return true;
        }
        try {
            net.storm.sdk.movement.WorldWalker.ignoreEscCancelFor(1_500L);
        } catch (Throwable ignored) {
        }
        Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        if (isOpen()) {
            tryCloseButton();
        }
        if (isOpen()) {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        }
        return true;
    }

    private static void tryCloseButton() {
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            // Veelgebruikte close-children op worldmap group
            int[] kids = {38, 37, 4, 3, 1};
            for (int child : kids) {
                Widget w = c.getWidget(WORLD_MAP_GROUP, child);
                if (w == null || w.isHidden()) {
                    continue;
                }
                try {
                    String[] actions = w.getActions();
                    if (actions != null) {
                        for (String a : actions) {
                            if (a != null && a.replaceAll("<[^>]+>", "").equalsIgnoreCase("Close")) {
                                Widgets.wrap(w).interact("Close");
                                return null;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            return null;
        }, null);
    }

    private static void clickMinimapOrb() {
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            for (int child : ORB_CHILDREN) {
                Widget orb = c.getWidget(160, child);
                if (orb == null || orb.isHidden()) {
                    continue;
                }
                try {
                    if (Widgets.wrap(orb).interact("Floating World Map")
                            || Widgets.wrap(orb).interact("World Map")
                            || Widgets.wrap(orb).interact("Fullscreen World Map")) {
                        return null;
                    }
                } catch (Throwable ignored) {
                }
            }
            return null;
        }, null);
    }
}
