package com.lonebot.example;

import com.lonebot.example.quest.QuestBotTarget;
import com.lonebot.example.quest.QuestLog;
import com.lonebot.example.quest.QuesterHandler;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LoneBot Quest solver — F2P Quester (Cook / Goblin / Romeo / Rune / Doric / Vampire / Tutorial).
 */
@PluginDescriptor(
        name = "LoneBot Quester",
        description = "F2P quests: Cook's Assistant, Goblin Diplomacy, Romeo & Juliet, Rune Mysteries, Doric, Vampire Slayer, Tutorial Island"
)
public class QuesterPlugin extends LoopedPlugin {

    public static final String VERSION = "0.1.6";

    /** {@link QuestBotTarget} name — ROTATION or a specific quest. */
    public static volatile String target = QuestBotTarget.ROTATION.name();
    public static volatile boolean rotationEnabled = true;
    public static volatile boolean skipLowLevel = true;

    private static final Logger log = LoggerFactory.getLogger(QuesterPlugin.class);

    private final QuesterHandler handler = new QuesterHandler();
    private long seenStartGen;
    private boolean wasRunning;

    public QuesterHandler getHandler() {
        return handler;
    }

    @Override
    public void startUp() {
        super.startUp();
        BotRuntime.questPluginVersion = VERSION;
        log.info("[Quester] startUp v{}", VERSION);
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
        BotRuntime.questPluginVersion = VERSION;
        boolean on = BotRuntime.botEnabled && BotRuntime.questEnabled;
        if (on && BotRuntime.botStartGeneration != seenStartGen) {
            seenStartGen = BotRuntime.botStartGeneration;
            if (!BotRuntime.lastStartWasResume) {
                handler.reset();
            }
            QuestLog.force("Quester", "loop start v" + VERSION
                    + " target=" + target
                    + " rot=" + rotationEnabled
                    + " resume=" + BotRuntime.lastStartWasResume);
        }
        if (!on) {
            if (wasRunning) {
                wasRunning = false;
                handler.reset();
            }
            BotRuntime.questStatus = BotRuntime.botEnabled ? "uit" : "bot uit";
            return 800;
        }
        wasRunning = true;
        try {
            return handler.loop();
        } catch (Throwable t) {
            log.warn("[Quester] loop error: {}", t.toString(), t);
            BotRuntime.questStatus = "fout";
            return 800;
        }
    }
}
