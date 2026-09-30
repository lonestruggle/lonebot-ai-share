package net.runelite.client.plugins.lonebot;

import com.lonebot.launcher.LauncherRestartHelper;

/**
 * @deprecated gebruik {@link LauncherRestartHelper} — blijft als thin wrapper voor oude callers.
 */
@Deprecated
public final class ClientRestartHelper {

    private ClientRestartHelper() {
    }

    public static void restartAfterDelay(long delayMs) {
        LauncherRestartHelper.restartAfterDelay(delayMs);
    }

    public static void restartNow() {
        LauncherRestartHelper.restartNow();
    }
}
