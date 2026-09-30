package net.storm.sdk.game;

/**
 * Storm-compat aliases for client-thread invocation.
 */
public final class GameThread {

    private GameThread() {
    }

    /** Run on the RuneLite client thread (async queue if off-thread). */
    public static void invoke(Runnable action) {
        Static.runOnClientThread(action);
    }

    /** Alias of {@link #invoke(Runnable)}. */
    public static void invokeLater(Runnable action) {
        invoke(action);
    }

    /** Blocking invoke — see {@link Static#invokeAndWait(Runnable)}. */
    public static void invokeAndWait(Runnable action) {
        Static.invokeAndWait(action);
    }
}
