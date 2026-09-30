package net.runelite.client.plugins.lonebot.login;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.lonebot.ClientRemoteBus;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.storm.sdk.bot.BotRuntime;

/**
 * Achtergrond-loop zoals CombatBot {@code LoopedPlugin.loop()} — tikt ook op het login-scherm
 * (RuneLite {@code GameTick} doet dat niet).
 */
public final class RelogLoop implements Runnable {

    private final SameAccountRelogger relogger;
    private final LoneBotConfig config;
    private final ConfigManager configManager;
    private final Thread thread;
    private volatile boolean started;

    private volatile long lastLauncherPublishMs;

    public RelogLoop(SameAccountRelogger relogger, LoneBotConfig config, ConfigManager configManager) {
        this.relogger = relogger;
        this.config = config;
        this.configManager = configManager;
        this.thread = new Thread(this, "lonebot-relog");
        this.thread.setDaemon(true);
    }

    public SameAccountRelogger relogger() {
        return relogger;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        thread.start();
        RelogLog.log("Relog", "loop gestart");
    }

    public void stop() {
        started = false;
        thread.interrupt();
        BotRuntime.relogFlowActive = false;
        BotRuntime.breakPending = false;
    }

    @Override
    public void run() {
        while (started) {
            int delay = 400;
            try {
                if (config != null && config.cancelBreak()) {
                    if (configManager != null) {
                        configManager.setConfiguration("lonebot", "cancelBreak", false);
                    }
                    relogger.cancelBreak();
                    delay = 200;
                } else if (config != null && config.breakNow()) {
                    if (configManager != null) {
                        configManager.setConfiguration("lonebot", "breakNow", false);
                    }
                    relogger.requestBreakNow();
                    delay = 200;
                } else if (config != null && config.loginNow()) {
                    if (configManager != null) {
                        configManager.setConfiguration("lonebot", "loginNow", false);
                    }
                    if (relogger.tryLoginNow()) {
                        delay = 120;
                    }
                } else {
                    delay = relogger.check();
                    if (delay <= 0) {
                        delay = 400;
                    }
                }
            } catch (Throwable t) {
                RelogLog.log("Relog", "loop fout: " + t);
                delay = 1000;
            }
            BotRuntime.breakPending = relogger.isWaitingSafe();
            BotRuntime.relogFlowActive = relogger.isRelogFlowActive();
            if (BotRuntime.relogFlowActive || BotRuntime.breakPending) {
                BotRuntime.heartbeat();
            }
            maybePublishLauncherTimer();
            sleep(delay);
        }
        BotRuntime.relogFlowActive = false;
        BotRuntime.breakPending = false;
        RelogLog.log("Relog", "loop gestopt");
    }

    private void maybePublishLauncherTimer() {
        long now = System.currentTimeMillis();
        if (now - lastLauncherPublishMs < 1_000L) {
            return;
        }
        String acc = System.getProperty("lonebot.account");
        if (acc == null || acc.trim().isEmpty()) {
            return;
        }
        lastLauncherPublishMs = now;
        try {
            ClientRemoteBus.publishLocalBotStatus(acc.trim());
        } catch (Throwable ignored) {
        }
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(Math.max(50, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
