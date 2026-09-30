package com.lonebot.example;

import com.lonebot.example.imps2.Imps2Loop;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.loop.LoopHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imps2 — nieuwe Imp-bouw, fase 1: lopen (Port Sarim dock / Musa → hunt) + walk-test.
 */
@PluginDescriptor(
        name = "LoneBot Imps2",
        description = "Walk-test: overal → Port Sarim dock → Musa → imps → terug → Draynor"
)
public class Imps2Plugin extends LoopedPlugin {

    public static final String VERSION = "0.1.8";

    private static final Logger log = LoggerFactory.getLogger(Imps2Plugin.class);

    private final Imps2Loop loop = new Imps2Loop();

    public Imps2Loop getLoop() {
        return loop;
    }

    public static void resetLive() {
        for (LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"com.lonebot.example.Imps2Plugin".equals(p.getClass().getName())) {
                continue;
            }
            try {
                Object lo = p.getClass().getMethod("getLoop").invoke(p);
                if (lo != null) {
                    lo.getClass().getMethod("reset").invoke(lo);
                }
            } catch (Throwable t) {
                log.warn("[Imps2] resetLive: {}", t.toString());
            }
        }
    }

    @Override
    public void startUp() {
        super.startUp();
        BotRuntime.imps2PluginVersion = VERSION;
        log.info("[Imps2] startUp v{}", VERSION);
    }

    @Override
    public int loop() {
        BotRuntime.imps2PluginVersion = VERSION;
        if (!BotRuntime.botEnabled || !BotRuntime.imps2Enabled) {
            BotRuntime.imps2Status = BotRuntime.botEnabled ? "uit" : "bot uit";
            return 800;
        }
        BotRuntime.enforceExclusiveSkills();
        if (!BotRuntime.imps2Enabled) {
            BotRuntime.imps2Status = "uit (andere skill)";
            return 800;
        }
        if (net.storm.sdk.bot.ClueSkillHandoff.tryHandoffFromActiveSkill()) {
            BotRuntime.imps2Status = "clue handoff";
            return 400;
        }

        try {
            int delay = loop.tick();
            BotRuntime.imps2Status = loop.getStatus();
            return delay;
        } catch (Throwable t) {
            log.warn("[Imps2] loop error: {}", t.toString(), t);
            BotRuntime.imps2Status = "err: " + t.getClass().getSimpleName();
            BotRuntime.logConsole("[Imps2] err " + t.getClass().getSimpleName());
            return 1000;
        }
    }
}
