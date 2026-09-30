package net.runelite.client.plugins.lonebot;

import net.storm.sdk.bot.BotRuntime;

/**
 * Show Overlay per script = grond/zones/pad. Status-panels volgen {@code statusOverlay}.
 */
public final class ScriptOverlayGate {

    private ScriptOverlayGate() {
    }

    /** Control panel + Imp/WC/Fish/Star/Cow statusbox. */
    public static boolean status(LoneBotConfig config) {
        return config == null || config.statusOverlay();
    }

    public static boolean impWorld(LoneBotConfig config) {
        return config != null && config.impDebugOverlay();
    }

    public static boolean wcWorld(LoneBotConfig config) {
        return config != null && (config.wcDebugOverlay() || config.showWcOverlay());
    }

    public static boolean fishWorld(LoneBotConfig config) {
        return config != null && config.fishDebugOverlay();
    }

    public static boolean starWorld(LoneBotConfig config) {
        return config != null && config.starDebugOverlay();
    }

    public static boolean giantsWorld(LoneBotConfig config) {
        return config != null && config.giantsDebugOverlay();
    }

    public static boolean cowWorld(LoneBotConfig config) {
        return config != null && config.cowDebugOverlay();
    }

    /**
     * Loop-pad op scene/minimap. Uit als het actieve script Overlay uit heeft.
     * Geen skill (walk-test) of Debug Overlay → pad mag.
     */
    public static boolean walkPath(LoneBotConfig config) {
        if (config != null && config.devDebugOverlay()) {
            return true;
        }
        if (BotRuntime.impKillerEnabled) {
            return impWorld(config);
        }
        if (BotRuntime.woodcuttingEnabled) {
            return wcWorld(config);
        }
        if (BotRuntime.fishingEnabled) {
            return fishWorld(config);
        }
        if (BotRuntime.starMinerEnabled) {
            return starWorld(config);
        }
        if (BotRuntime.giantsKillerEnabled) {
            return giantsWorld(config);
        }
        if (BotRuntime.cowCombatEnabled) {
            return cowWorld(config);
        }
        return true;
    }
}
