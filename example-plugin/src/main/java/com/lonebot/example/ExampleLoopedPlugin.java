package com.lonebot.example;

import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Game;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.input.MouseSettings;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.utils.AntiBan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Smoke-test LoopedPlugin: log tile + optional random canvas mouse move.
 */
@PluginDescriptor(
        name = "LoneBot Example",
        description = "Test loop + random canvas mouse move"
)
public class ExampleLoopedPlugin extends LoopedPlugin {

    private static final Logger log = LoggerFactory.getLogger(ExampleLoopedPlugin.class);

    private long lastFidgetMs;
    private int ticks;

    /** Toggle from bootstrap/panel; random move over canvas. */
    public static volatile boolean mouseFidgetEnabled = true;

    @Override
    public int loop() {
        // Smoke-test mag de enige loop-thread niet blokkeren (AntiBan.sleep) terwijl WC/Imp/Cow tikt
        if (BotRuntime.woodcuttingEnabled || BotRuntime.impKillerEnabled || BotRuntime.cowCombatEnabled
                || BotRuntime.monkKillerEnabled
                || BotRuntime.fishingEnabled || BotRuntime.cityCircleTestEnabled
                || BotRuntime.imps2Enabled || BotRuntime.starMinerEnabled) {
            return 0;
        }
        if (!BotRuntime.botEnabled) {
            return 1500;
        }
        if (!Game.isLoggedIn()) {
            return 1000;
        }
        ticks++;
        Players.LocalSnap me = Players.snapshotLocal();
        if (me.present && me.worldLocation != null && ticks % 5 == 0) {
            int free = Inventory.getFreeSlots();
            log.info("Example loop tick={} tile={},{} invFree={} mouse={},{}",
                    ticks,
                    me.worldLocation.getX(),
                    me.worldLocation.getY(),
                    free,
                    Mouse.getX(),
                    Mouse.getY());
        }

        long now = System.currentTimeMillis();
        if (mouseFidgetEnabled && MouseSettings.isRandomMoveEnabled() && now - lastFidgetMs > 12_000L) {
            lastFidgetMs = now;
            log.info("Random canvas mouse move…");
            Mouse.moveRandom(false);
            AntiBan.get().markBotActivity();
        } else if (mouseFidgetEnabled != MouseSettings.isRandomMoveEnabled()) {
            MouseSettings.setRandomMoveEnabled(mouseFidgetEnabled);
        }
        int antiDelay = AntiBan.get().check();
        if (antiDelay > 0) {
            return Math.min(antiDelay, 2000);
        }
        return 800;
    }
}
