package net.runelite.client.plugins.lonebot;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.storm.api.domain.actors.IPlayer;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Game;

/**
 * CombatBot LoopWatch: zelfde skill+status+tile+moving+anim te lang → zacht herstel, daarna bot uit + logout.
 */
public final class LoopWatchHelper {

    private static final long LOG_EVERY_MS = 15_000L;
    private static final long RECOVERY_COOLDOWN_MS = 20_000L;

    private static String lastSignature = "";
    private static long signatureSinceMs;
    private static long lastLogMs;
    private static long lastRecoveryMs;

    private LoopWatchHelper() {
    }

    public static void reset() {
        lastSignature = "";
        signatureSinceMs = 0L;
        lastLogMs = 0L;
        BotRuntime.loopWatchStatus = "-";
    }

    /**
     * Tick vanuit Bootstrap {@code onGameTick}. Gebruikt config-drempels.
     */
    public static void tick(LoneBotConfig config, ConfigManager configManager) {
        if (config == null || !config.loopWatchRecoveryEnabled() || !BotRuntime.botEnabled) {
            return;
        }
        if (!Game.isLoggedIn()) {
            reset();
            return;
        }
        IPlayer lp = Players.getLocal();
        if (lp == null || lp.getWorldLocation() == null) {
            reset();
            return;
        }
        String skill = activeSkillName();
        String status = activeStatus();
        if (isExpectedPassiveGathering(skill, status, lp.getAnimation())) {
            reset();
            return;
        }
        WorldPoint p = lp.getWorldLocation();
        String signature = skill + "|" + status + "|" + p.getX() + "," + p.getY()
                + "|m=" + lp.isMoving() + "|a=" + lp.getAnimation();
        long now = System.currentTimeMillis();
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            signatureSinceMs = now;
            lastLogMs = 0L;
            BotRuntime.loopWatchStatus = "ok";
            return;
        }
        long durMs = signatureSinceMs > 0L ? (now - signatureSinceMs) : 0L;
        if (durMs < 45_000L) {
            return;
        }
        if (now - lastLogMs >= LOG_EVERY_MS) {
            lastLogMs = now;
            String msg = "[LoopWatch] zelfde signature " + (durMs / 1000) + "s | skill=" + skill
                    + " | status=" + status + " | tile=" + p.getX() + "," + p.getY();
            BotRuntime.logConsole(msg);
            BotRuntime.loopWatchStatus = (durMs / 1000) + "s stuck";
        }

        int triggerSec = Math.max(45, config.loopWatchTriggerSec());
        int logoutSec = Math.max(triggerSec + 30, config.loopWatchLogoutSec());
        if (durMs < triggerSec * 1000L) {
            return;
        }
        if (now - lastRecoveryMs < RECOVERY_COOLDOWN_MS) {
            return;
        }
        lastRecoveryMs = now;

        if (durMs >= logoutSec * 1000L) {
            BotRuntime.logConsole("[LoopWatch] logout na " + (durMs / 1000) + "s — bot uit + uitloggen");
            BotRuntime.loopWatchStatus = "logout";
            LoneBotBotControl.stop();
            if (configManager != null) {
                configManager.setConfiguration("lonebot", "botEnabled", false);
            }
            if (config.resetAccountTimersOnStop()) {
                AccountSessionTimers.clearAll();
            }
            PanelLogoutHelper.requestLogout();
            reset();
            return;
        }

        // Zacht herstel
        BotRuntime.logConsole("[LoopWatchRecovery] " + (durMs / 1000) + "s → soft reset runtime");
        BotRuntime.loopWatchStatus = "herstel";
        LoneBotBotControl.resetRuntime();
        signatureSinceMs = now;
    }

    private static String activeSkillName() {
        if (BotRuntime.woodcuttingEnabled) {
            return "WC";
        }
        if (BotRuntime.fishingEnabled) {
            return "FISH";
        }
        if (BotRuntime.impKillerEnabled) {
            return "IMP";
        }
        if (BotRuntime.imps2Enabled) {
            return "IMPS2";
        }
        if (BotRuntime.starMinerEnabled) {
            return "STAR";
        }
        if (BotRuntime.giantsKillerEnabled) {
            return "GIANTS";
        }
        if (BotRuntime.cowCombatEnabled) {
            return "COW";
        }
        if (BotRuntime.monkKillerEnabled) {
            return "MONK";
        }
        if (BotRuntime.questEnabled) {
            return "QUEST";
        }
        if (BotRuntime.cityCircleTestEnabled) {
            return "CIRCLE";
        }
        if (BotRuntime.varrockEastBankTestEnabled) {
            return "BANKTEST";
        }
        return "IDLE";
    }

    private static String activeStatus() {
        if (BotRuntime.woodcuttingEnabled) {
            return BotRuntime.wcStatus != null ? BotRuntime.wcStatus : "";
        }
        if (BotRuntime.fishingEnabled) {
            return BotRuntime.fishStatus != null ? BotRuntime.fishStatus : "";
        }
        if (BotRuntime.impKillerEnabled) {
            return BotRuntime.impStatus != null ? BotRuntime.impStatus : "";
        }
        if (BotRuntime.imps2Enabled) {
            return BotRuntime.imps2Status != null ? BotRuntime.imps2Status : "";
        }
        if (BotRuntime.starMinerEnabled) {
            return BotRuntime.starStatus != null ? BotRuntime.starStatus : "";
        }
        if (BotRuntime.giantsKillerEnabled) {
            return BotRuntime.giantsStatus != null ? BotRuntime.giantsStatus : "";
        }
        if (BotRuntime.cowCombatEnabled) {
            return BotRuntime.cowStatus != null ? BotRuntime.cowStatus : "";
        }
        if (BotRuntime.monkKillerEnabled) {
            return BotRuntime.monkStatus != null ? BotRuntime.monkStatus : "";
        }
        if (BotRuntime.questEnabled) {
            return BotRuntime.questStatus != null ? BotRuntime.questStatus : "";
        }
        if (BotRuntime.cityCircleTestEnabled) {
            return BotRuntime.cityCircleStatus != null ? BotRuntime.cityCircleStatus : "";
        }
        if (BotRuntime.varrockEastBankTestEnabled) {
            return BotRuntime.bankTestStatus != null ? BotRuntime.bankTestStatus : "";
        }
        return "";
    }

    private static boolean isExpectedPassiveGathering(String skill, String status, int anim) {
        if (status == null) {
            return false;
        }
        String s = status.toLowerCase();
        if ("WC".equals(skill)) {
            if (anim > 0 && (s.contains("chop") || s.contains("hak") || s.contains("bonfire")
                    || s.contains("fire") || s.contains("fm") || s.contains("forester"))) {
                return true;
            }
            return s.contains("hout hakken") || s.contains("chop") || s.contains("bonfire")
                    || s.contains("verbranden") || s.contains("firemaking") || s.contains("forester");
        }
        if ("FISH".equals(skill)) {
            if (anim > 0 && (s.contains("vis") || s.contains("fish") || s.contains("kook") || s.contains("cook"))) {
                return true;
            }
            return s.contains("vissen") || s.contains("koken") || s.contains("spot");
        }
        return false;
    }
}
