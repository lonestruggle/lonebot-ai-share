package com.lonebot.example;

import com.lonebot.example.clue.BeginnerClueHandler;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LoneBot Beginner Treasure Trail solver — wraps {@link BeginnerClueHandler}.
 * Beginner clues only (not easy/medium/hard).
 */
@PluginDescriptor(
        name = "LoneBot Beginner Clue",
        description = "Beginner Treasure Trails: anagram, cryptic, emote, map dig, hot/cold, Charlie"
)
public class CluePlugin extends LoopedPlugin {

    public static final String VERSION = "0.1.20";

    private static final Logger log = LoggerFactory.getLogger(CluePlugin.class);

    private final BeginnerClueHandler handler = new BeginnerClueHandler();

    public BeginnerClueHandler getHandler() {
        return handler;
    }

    public void onGameMessage(String message) {
        handler.onGameMessage(message);
    }

    @Override
    public void startUp() {
        super.startUp();
        BotRuntime.cluePluginVersion = VERSION;
        log.info("[Clue] startUp v{}", VERSION);
        BotRuntime.logConsole("[Clue] plugin v" + VERSION + " — beginner trails only");
    }

    @Override
    public void shutDown() {
        handler.reset();
        super.shutDown();
    }

    @Override
    public int loop() {
        if (net.storm.sdk.loop.LoopHost.isReloading()) {
            return 50;
        }
        BotRuntime.cluePluginVersion = VERSION;
        if (!BotRuntime.botEnabled || !BotRuntime.clueEnabled) {
            BotRuntime.clueStatus = BotRuntime.botEnabled ? "uit" : "bot uit";
            return 800;
        }
        BotRuntime.enforceExclusiveSkills();
        if (!BotRuntime.clueEnabled) {
            BotRuntime.clueStatus = "uit (andere skill)";
            return 800;
        }
        try {
            int delay = handler.loop();
            BotRuntime.clueStatus = handler.getStatus();
            return delay;
        } catch (Throwable t) {
            log.warn("[Clue] loop error: {}", t.toString(), t);
            String detail = t.getMessage();
            if ((detail == null || detail.isEmpty()) && t.getCause() != null) {
                detail = t.getCause().getMessage();
            }
            if (detail != null && detail.length() > 48) {
                detail = detail.substring(0, 48);
            }
            BotRuntime.clueStatus = detail != null && !detail.isEmpty()
                    ? "err: " + t.getClass().getSimpleName() + " " + detail
                    : "err: " + t.getClass().getSimpleName();
            return 1000;
        }
    }
}
