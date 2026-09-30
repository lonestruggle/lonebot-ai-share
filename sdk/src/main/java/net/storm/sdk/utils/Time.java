package net.storm.sdk.utils;

/**
 * Time / human-delay helpers (Storm-compat Time subset).
 * Random ints: see {@link net.storm.sdk.commons.Rand}.
 */
public final class Time {

    private Time() {
    }

    public static long nowMs() {
        return System.currentTimeMillis();
    }

    /** Sleep a humanised delay in [{@code minMs}, {@code maxMs}] via {@link Sleep}. */
    public static void humanDelay(int minMs, int maxMs) {
        Sleep.sleep(minMs, maxMs);
    }
}
