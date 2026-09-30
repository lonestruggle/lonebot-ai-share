package com.lonebot.example.fishing;

import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * LoneBot Fishing — volledige CombatBot-port (Draynor/Barbarian, bait/bank/GE, cook/drop).
 */
@PluginDescriptor(
        name = "LoneBot Fishing",
        description = "Fishing: Draynor Net/Bait, Barbarian Lure, bank, GE restock, cooking"
)
public class FishingPlugin extends LoopedPlugin {

    public static final String VERSION = "0.1.41";

    public static volatile boolean dropFish = true;
    public static volatile boolean cookEnabled = false;
    public static volatile boolean restockEnabled = false;
    public static volatile boolean useSpecificMethod = false;
    public static volatile boolean useVarrockTeleport = true;
    public static volatile String spotName = "Fishing spot";
    public static volatile String action = "Net";
    public static volatile String centersBlob = FishingCenters.DEFAULT_BLOB;
    /** AUTO = level-based (Draynor/Barbarian); anders vaste center-naam. */
    public static volatile String preferredLocation = "AUTO";
    public static volatile int baitMin = 50;
    public static volatile int restockAmount = 1000;
    public static volatile int baitPrice = 5;
    public static volatile int featherPrice = 3;
    public static volatile int interactDelayMin = 0;
    public static volatile int interactDelayMax = 0;

    private static final Logger log = LoggerFactory.getLogger(FishingPlugin.class);

    private final FishingLoop loop = new FishingLoop();
    private FishDebugPaint debugPaint;
    private BotRuntime.DebugPaint paintHook;

    @Override
    public void startUp() {
        super.startUp();
        debugPaint = new FishDebugPaint(loop);
        paintHook = debugPaint::render;
        BotRuntime.fishPluginVersion = VERSION;
        BotRuntime.fishDebugPaint = paintHook;
        publishOverlay("start");
        log.info("[Fishing] startUp v{} — canvas paint registered", VERSION);
    }

    @Override
    public void shutDown() {
        if (paintHook != null && BotRuntime.fishDebugPaint == paintHook) {
            BotRuntime.fishDebugPaint = null;
        }
        paintHook = null;
        debugPaint = null;
        loop.reset();
        super.shutDown();
    }

    public void onGameMessage(String message) {
        loop.onGameMessage(message);
    }

    public FishingLoop getLoop() {
        return loop;
    }

    @Override
    public int loop() {
        if (net.storm.sdk.loop.LoopHost.isReloading()) {
            return 50;
        }
        BotRuntime.fishPluginVersion = VERSION;
        if (paintHook != null) {
            BotRuntime.fishDebugPaint = paintHook;
        }
        if (!BotRuntime.botEnabled || !BotRuntime.fishingEnabled) {
            BotRuntime.fishStatus = BotRuntime.botEnabled ? "uit" : "bot uit";
            loop.haltIfStopped();
            loop.prepareOverlay();
            publishOverlay(BotRuntime.fishStatus);
            return 800;
        }
        BotRuntime.enforceExclusiveSkills();
        if (!BotRuntime.fishingEnabled) {
            BotRuntime.fishStatus = "uit (andere skill)";
            publishOverlay(BotRuntime.fishStatus);
            return 800;
        }
        try {
            if (net.storm.sdk.bot.ClueSkillHandoff.tryHandoffFromActiveSkill()) {
                BotRuntime.fishStatus = "clue handoff";
                publishOverlay(BotRuntime.fishStatus);
                return 400;
            }
            int delay = loop.tick();
            BotRuntime.fishStatus = loop.getStatus();
            publishOverlay(null);
            return delay;
        } catch (Throwable t) {
            log.warn("[Fishing] loop error: {}", t.toString(), t);
            BotRuntime.fishStatus = "err: " + t.getClass().getSimpleName();
            publishOverlay(BotRuntime.fishStatus);
            return 1000;
        }
    }

    private void publishOverlay(String statusOverride) {
        try {
            List<String> lines = new ArrayList<>();
            boolean on = BotRuntime.botEnabled && BotRuntime.fishingEnabled;
            lines.add("Bot: " + (BotRuntime.botEnabled ? "AAN" : "UIT")
                    + (BotRuntime.fishingEnabled ? " · Fish" : " · Fish-script uit"));
            if (statusOverride != null && !statusOverride.isBlank()) {
                lines.add("Status: " + statusOverride);
            } else {
                lines.add("Status: " + loop.getStatus());
            }
            List<String> dbg = loop.debugLines();
            if (dbg != null) {
                lines.addAll(dbg);
            }
            BotRuntime.fishDebugLines = lines;
            if (on && "bot uit".equals(statusOverride)) {
                log.warn("[Fishing] overlay zei bot uit terwijl master AAN — race");
            }
        } catch (Throwable ignored) {
        }
    }
}
