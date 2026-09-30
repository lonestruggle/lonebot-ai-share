package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Static;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimap pixels-per-tile zo laag mogelijk (verder uit = hops verder van de rand).
 * Default RL-zoom is 4; lager = meer wereld in de cirkel.
 */
public final class MinimapZoomHelper {

    private static final Logger log = LoggerFactory.getLogger(MinimapZoomHelper.class);

    /** Pixels per tegel. 4 = vanilla; 1 = zo ver uit als de API toelaat. */
    public static final double FULLY_OUT = 1.0;
    private static final double EPS = 0.08;
    private static final long THROTTLE_MS = 2_000L;
    private static final long LOG_THROTTLE_MS = 1_500L;

    private static volatile long lastApplyMs;
    private static volatile long lastLogMs;
    private static volatile String lastLog = "";

    private MinimapZoomHelper() {
    }

    public static void ensureFullyZoomedOut() {
        ensureFullyZoomedOut(false);
    }

    /**
     * @param force {@code true} = meteen (bot-start / checkbox), anders max 1× per 2s
     */
    public static void ensureFullyZoomedOut(boolean force) {
        if (!WalkClickSettings.minimapFullyZoomedOut) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastApplyMs < THROTTLE_MS) {
            return;
        }
        lastApplyMs = now;
        Runnable apply = () -> {
            Client c = Static.getClient();
            if (c == null) {
                return;
            }
            try {
                if (!c.isMinimapZoom()) {
                    c.setMinimapZoom(true);
                }
                double cur = c.getMinimapZoom();
                if (cur > FULLY_OUT + EPS) {
                    c.setMinimapZoom(FULLY_OUT);
                    console("[Walk/minimap] volledig uit (zoom=" + fmt(FULLY_OUT) + ")");
                }
            } catch (Throwable t) {
                log.warn("[Walk/minimap] zoom-out fail: {}", t.toString());
                console("[Walk/minimap] zoom-out fail: " + t.getClass().getSimpleName());
            }
        };
        if (force || Static.isOnClientThread()) {
            Static.callOnClientThread(() -> {
                apply.run();
                return true;
            }, false);
        } else {
            Static.runOnClientThread(apply);
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static void console(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < LOG_THROTTLE_MS) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole(msg);
        log.info(msg);
    }
}
