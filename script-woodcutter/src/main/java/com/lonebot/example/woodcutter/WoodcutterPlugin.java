package com.lonebot.example.woodcutter;

import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * LoneBot Woodcutter — CombatBot WC-port (centers, bonfire, forestry kit, events).
 * Zichtbaar op Scripts-tab via {@link net.storm.sdk.bot.BotRuntime#woodcuttingEnabled}.
 */
@PluginDescriptor(
        name = "LoneBot Woodcutter",
        description = "Woodcutting: Draynor/Port Sarim/yews, bonfire, forestry kit, bird nests"
)
public class WoodcutterPlugin extends LoopedPlugin {

    public static final String VERSION = "0.1.116";

    private static final Logger log = LoggerFactory.getLogger(WoodcutterPlugin.class);

    public static volatile boolean firemaking = true;
    public static volatile boolean dropLogs = false;
    public static volatile boolean forestryEvents = true;
    public static volatile boolean birdNests = true;
    /** GE-axe restock — default uit tot GE werkt. */
    public static volatile boolean geAxeRestockEnabled = false;
    /** WC mag Lumbridge castle-bank gebruiken. */
    public static volatile boolean bankLumbridge = true;
    /** WC mag Draynor-bank gebruiken. */
    public static volatile boolean bankDraynor = true;
    public static volatile boolean useSpecificTree = false;
    public static volatile String treeName = "";
    public static volatile String centersBlob = WcCenters.DEFAULT_BLOB;
    /** AUTO of center-naam (Draynor willows / Port Sarim… / Draynor-Lumb yews). */
    public static volatile String preferredLocation = "AUTO";

    private final WoodcutterLoop loop = new WoodcutterLoop();
    private WcDebugPaint debugPaint;
    private BotRuntime.DebugPaint paintHook;

    @Override
    public void startUp() {
        super.startUp();
        debugPaint = new WcDebugPaint(loop);
        paintHook = debugPaint::render;
        BotRuntime.wcPluginVersion = VERSION;
        BotRuntime.wcDebugPaint = paintHook;
        publishOverlay("start");
        log.info("[Woodcutter] startUp v{} — canvas paint registered", VERSION);
    }

    @Override
    public void shutDown() {
        if (paintHook != null && BotRuntime.wcDebugPaint == paintHook) {
            BotRuntime.wcDebugPaint = null;
        }
        paintHook = null;
        debugPaint = null;
        loop.reset();
        super.shutDown();
    }

    /** Chat / game-message hook (bootstrap of loop-poll via {@link WoodcutterLoop}). */
    public void onGameMessage(String message) {
        loop.onGameMessage(message);
    }

    public WoodcutterLoop getLoop() {
        return loop;
    }

    @Override
    public int loop() {
        BotRuntime.wcPluginVersion = VERSION;
        if (paintHook != null) {
            BotRuntime.wcDebugPaint = paintHook;
        }
        if (!BotRuntime.botEnabled || !BotRuntime.woodcuttingEnabled) {
            BotRuntime.wcStatus = BotRuntime.botEnabled ? "uit" : "bot uit";
            loop.haltIfStopped();
            loop.prepareOverlay();
            publishOverlay(BotRuntime.wcStatus);
            return 800;
        }
        BotRuntime.enforceExclusiveSkills();
        if (!BotRuntime.woodcuttingEnabled) {
            BotRuntime.wcStatus = "uit (andere skill)";
            publishOverlay(BotRuntime.wcStatus);
            return 800;
        }
        try {
            int delay = loop.tick();
            BotRuntime.wcStatus = loop.getStatus();
            publishOverlay(null);
            return delay;
        } catch (Throwable t) {
            log.warn("[Woodcutter] loop error: {}", t.toString(), t);
            BotRuntime.wcStatus = "err: " + t.getClass().getSimpleName();
            publishOverlay(BotRuntime.wcStatus);
            return 1000;
        }
    }

    /** Snapshot voor overlay — overleeft hot-reload dual-instance (stale paint-hook). */
    private void publishOverlay(String statusOverride) {
        try {
            List<String> lines = new ArrayList<>();
            boolean on = BotRuntime.botEnabled && BotRuntime.woodcuttingEnabled;
            lines.add("Bot: " + (BotRuntime.botEnabled ? "AAN" : "UIT")
                    + (BotRuntime.woodcuttingEnabled ? " · WC" : " · WC-script uit"));
            if (statusOverride != null && !statusOverride.isBlank()) {
                lines.add("Status: " + statusOverride);
            } else {
                lines.add("Status: " + loop.getStatus());
            }
            List<String> dbg = loop.debugLines();
            if (dbg != null) {
                lines.addAll(dbg);
            }
            BotRuntime.wcDebugLines = lines;
            if (on && "bot uit".equals(statusOverride)) {
                log.warn("[Woodcutter] overlay zei bot uit terwijl master AAN — race");
            }
        } catch (Throwable ignored) {
        }
    }
}
