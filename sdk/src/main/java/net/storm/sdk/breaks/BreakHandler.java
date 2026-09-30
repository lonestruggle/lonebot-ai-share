package net.storm.sdk.breaks;

/**
 * Break scheduler.
 *
 * @stub No real break timing / AFK logout yet.
 */
public final class BreakHandler {

    private static volatile boolean breaking;
    private static volatile long breakUntilMs;

    private BreakHandler() {
    }

    /**
     * @stub Records a break window but does not pause plugins automatically.
     */
    public static void schedule(long durationMs) {
        long d = Math.max(0L, durationMs);
        breaking = d > 0;
        breakUntilMs = System.currentTimeMillis() + d;
    }

    /**
     * @stub True while a scheduled break window is active.
     */
    public static boolean isBreaking() {
        if (!breaking) {
            return false;
        }
        if (System.currentTimeMillis() >= breakUntilMs) {
            breaking = false;
            return false;
        }
        return true;
    }

    /** Clear break state. */
    public static void clear() {
        breaking = false;
        breakUntilMs = 0L;
    }
}
