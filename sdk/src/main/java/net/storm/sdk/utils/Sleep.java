package net.storm.sdk.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Humanised sleep helpers.
 */
public final class Sleep {

    private Sleep() {
    }

    /** Sleep a random duration in [{@code minMs}, {@code maxMs}] inclusive. */
    public static void sleep(int minMs, int maxMs) {
        int lo = Math.min(minMs, maxMs);
        int hi = Math.max(minMs, maxMs);
        int ms = lo >= hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
        sleep(ms);
    }

    public static void sleep(long ms) {
        if (ms <= 0 || Thread.currentThread().isInterrupted()) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
