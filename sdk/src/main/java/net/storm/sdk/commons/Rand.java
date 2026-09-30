package net.storm.sdk.commons;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Random helpers (Storm-compat Rand subset).
 */
public final class Rand {

    private Rand() {
    }

    /** Inclusive {@code min}..{@code max}. */
    public static int nextInt(int min, int max) {
        if (max < min) {
            int t = min;
            min = max;
            max = t;
        }
        if (min == max) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * Gaussian-ish delay around {@code meanMs} with soft bounds.
     *
     * @return delay in ms, at least 1
     */
    public static int nextGaussian(int meanMs, int stdDevMs) {
        double g = ThreadLocalRandom.current().nextGaussian();
        int v = (int) Math.round(meanMs + g * Math.max(1, stdDevMs));
        return Math.max(1, v);
    }

    /** Convenience: delay between {@code minMs} and {@code maxMs} with mild gaussian bias toward mid. */
    public static int delay(int minMs, int maxMs) {
        if (maxMs < minMs) {
            int t = minMs;
            minMs = maxMs;
            maxMs = t;
        }
        int mid = (minMs + maxMs) / 2;
        int spread = Math.max(1, (maxMs - minMs) / 3);
        int v = nextGaussian(mid, spread);
        if (v < minMs) {
            return minMs;
        }
        if (v > maxMs) {
            return maxMs;
        }
        return v;
    }
}
