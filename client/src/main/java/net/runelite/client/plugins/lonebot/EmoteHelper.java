package net.runelite.client.plugins.lonebot;

import net.runelite.api.Point;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.widgets.Tab;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.widgets.Tabs;

import java.awt.Rectangle;

/**
 * Productie-emote pad (CombatBot performEmote-stijl): open Emotes-tab → knop op naam → Perform.
 */
public final class EmoteHelper {

    private static volatile long lastEmoteMs;
    private static volatile String lastEmoteName = "";

    private EmoteHelper() {
    }

    public static boolean performEmote(String emoteName) {
        if (emoteName == null || emoteName.isBlank()) {
            BotRuntime.logConsole("[Emote] naam leeg");
            return false;
        }
        long now = System.currentTimeMillis();
        boolean same = emoteName.equalsIgnoreCase(lastEmoteName);
        if (same && now - lastEmoteMs < 200) {
            return false;
        }
        if (!same && now - lastEmoteMs < 80) {
            return false;
        }

        try {
            Tabs.open(Tab.EMOTES);
        } catch (Throwable t) {
            BotRuntime.logConsole("[Emote] Tabs.open(EMOTES) fail: " + t);
        }

        IWidget btn = EmoteWidgetRegistry.findPerformButton(emoteName);
        if (btn == null && EmoteWidgetRegistry.isRescanAllowed()) {
            btn = EmoteWidgetRegistry.rescanFindOnce(emoteName);
        }
        if (btn == null) {
            EmoteWidgetRegistry.allowRescanOnce();
            btn = EmoteWidgetRegistry.rescanFindOnce(emoteName);
        }
        if (btn == null) {
            BotRuntime.logConsole("[Emote] knop niet gevonden: " + emoteName
                    + " — open Emotes-tab en Rescan");
            return false;
        }

        if (!clickPerform(btn, emoteName)) {
            BotRuntime.logConsole("[Emote] klik mislukt: " + emoteName);
            return false;
        }
        lastEmoteMs = now;
        lastEmoteName = emoteName;
        BotRuntime.logConsole("[Emote] OK perform \"" + emoteName + "\"");
        return true;
    }

    private static boolean clickPerform(IWidget w, String emoteName) {
        try {
            if (w.hasAction("Perform")) {
                w.interact("Perform");
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Rectangle b = w.getBounds();
            if (b != null && b.width > 0 && b.height > 0) {
                int x = b.x + b.width / 2;
                int y = b.y + b.height / 2;
                if (MouseManager.interactAt(new Point(x, y))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static String lastDetail() {
        return lastEmoteName != null ? lastEmoteName : "";
    }
}
