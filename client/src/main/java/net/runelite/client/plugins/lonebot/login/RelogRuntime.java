package net.runelite.client.plugins.lonebot.login;

import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.storm.sdk.game.Static;

/**
 * Live config + relogger voor Play-kalibratie (CombatBot {@code CombatBotRuntime.getActivePlugin()}).
 */
public final class RelogRuntime {

    public static volatile LoneBotConfig config;
    public static volatile ConfigManager configManager;
    public static volatile SameAccountRelogger relogger;
    public static volatile RelogLoop loop;

    private RelogRuntime() {
    }

    /** Break nu: veilig wachten → uitloggen → pauze → inloggen. */
    public static void requestBreakNow() {
        RelogLog.log("Relog", "Break nu gevraagd");
        SameAccountRelogger r = relogger;
        if (r != null) {
            r.requestBreakNow();
            return;
        }
        ConfigManager cm = configManager;
        if (cm != null) {
            cm.setConfiguration("lonebot", "breakNow", true);
        }
    }

    /** Stop wait-safe / pauze; in-world blijven of meteen weer inloggen. */
    public static void cancelBreak() {
        RelogLog.log("Relog", "Annuleer break gevraagd");
        SameAccountRelogger r = relogger;
        if (r != null) {
            r.cancelBreak();
            return;
        }
        ConfigManager cm = configManager;
        if (cm != null) {
            cm.setConfiguration("lonebot", "cancelBreak", true);
        }
    }

    public static String loginScreenDisplayNameHint() {
        try {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            String n = c.getLauncherDisplayName();
            return n != null && !n.isBlank() ? n : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String clientGameStateLabel() {
        try {
            Client c = Static.getClient();
            return c != null && c.getGameState() != null ? c.getGameState().name() : "null";
        } catch (Throwable t) {
            return "err";
        }
    }
}
