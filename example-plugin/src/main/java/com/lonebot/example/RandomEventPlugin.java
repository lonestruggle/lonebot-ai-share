package com.lonebot.example;

import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.RandomEventHandler;
import net.storm.sdk.game.RandomEventSettings;

/**
 * Always-on random event tick (Dismiss / Genie / lamp) wanneer een LoneBot-activiteit loopt.
 */
@PluginDescriptor(
        name = "LoneBot Random Events",
        description = "Dismiss random events; Genie + lamp skill"
)
public class RandomEventPlugin extends LoopedPlugin {

    @Override
    public int loop() {
        if (!RandomEventSettings.enabled) {
            BotRuntime.randomEventStatus = "uit";
            return idleDelay();
        }
        int delay = RandomEventHandler.tick();
        if (delay > 0) {
            return delay;
        }
        // Overlay: bewijs dat RE-poll loopt (anders bleef "idle" van bootstrap)
        String last = RandomEventHandler.lastStatus();
        if (last == null || last.isEmpty() || "-".equals(last) || "idle".equals(last)) {
            BotRuntime.randomEventStatus = "poll";
        }
        return idleDelay();
    }

    /** 0 tijdens skill/lopen — anders blokkeert 700–1200ms de walk-chain. */
    private static int idleDelay() {
        if (BotRuntime.woodcuttingEnabled || BotRuntime.fishingEnabled
                || BotRuntime.starMinerEnabled
                || BotRuntime.impKillerEnabled || BotRuntime.imps2Enabled
                || BotRuntime.cowCombatEnabled || BotRuntime.cityCircleTestEnabled) {
            return 0;
        }
        try {
            if (net.storm.sdk.movement.Movement.isMoving()
                    || net.storm.sdk.movement.MovementHelper.getActiveDestination() != null) {
                return 0;
            }
        } catch (Throwable ignored) {
        }
        return 700;
    }
}
