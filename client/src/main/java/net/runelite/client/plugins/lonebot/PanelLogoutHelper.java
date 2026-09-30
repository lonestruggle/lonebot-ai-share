package net.runelite.client.plugins.lonebot;

import net.runelite.client.callback.ClientThread;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Game;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CombatBot panel-logout: request → wacht veilig moment → {@link Game#logout()} op client-thread.
 * Zelfde gate als re-log (combat/loot/bank/wildy/random); force na timeout.
 */
public final class PanelLogoutHelper {

    private static final Logger log = LoggerFactory.getLogger(PanelLogoutHelper.class);
    private static final long FORCE_TIMEOUT_MS = 45_000L;

    private static volatile boolean invokePending;

    private PanelLogoutHelper() {
    }

    public static void requestLogout() {
        if (!BotRuntime.panelLogoutRequested) {
            BotRuntime.panelLogoutRequestedAtMs = System.currentTimeMillis();
        }
        BotRuntime.panelLogoutRequested = true;
        BotRuntime.logConsole("[BotControl] Uitloggen gepland → Game.logout()");
        log.info("Panel logout requested");
    }

    public static void clear() {
        BotRuntime.panelLogoutRequested = false;
        BotRuntime.panelLogoutRequestedAtMs = 0L;
        invokePending = false;
    }

    /** Tick vanuit Bootstrap — ook als bot uit staat (CombatBot doet dat ook). */
    public static void tick(ClientThread clientThread) {
        if (!BotRuntime.panelLogoutRequested) {
            return;
        }
        if (!Game.isLoggedIn()) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        long since = BotRuntime.panelLogoutRequestedAtMs > 0L
                ? now - BotRuntime.panelLogoutRequestedAtMs
                : 0L;
        boolean force = since >= FORCE_TIMEOUT_MS;
        if (!force && !net.storm.sdk.bot.RelogSafeGate.canLogoutNow()) {
            net.storm.sdk.bot.RelogSafeGate.tryMakeSafe();
            return;
        }
        if (force) {
            BotRuntime.logConsole("[BotControl] Logout timeout → force Game.logout()");
        }
        invokeLogout(clientThread);
    }

    private static void invokeLogout(ClientThread clientThread) {
        Runnable r = () -> {
            invokePending = false;
            try {
                boolean ok = Game.logout();
                BotRuntime.logConsole("[BotControl] Game.logout() ok=" + ok
                        + " loggedIn=" + Game.isLoggedIn());
                if (!Game.isLoggedIn()) {
                    clear();
                }
            } catch (Throwable t) {
                BotRuntime.logConsole("[BotControl] Game.logout() fout: " + t.getMessage());
                log.warn("logout failed", t);
            }
        };
        if (clientThread != null) {
            if (invokePending) {
                return;
            }
            invokePending = true;
            clientThread.invokeLater(r);
        } else {
            r.run();
        }
    }
}
