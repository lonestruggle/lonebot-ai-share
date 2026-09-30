package net.storm.sdk.script.paint;

import java.util.concurrent.TimeUnit;

/**
 * Paint / overlay string helpers.
 *
 * @stub Minimal formatting only — no Storm paint pipeline.
 */
public final class PaintUtil {

    private PaintUtil() {
    }

    /** Format a runtime duration as {@code H:MM:SS} or {@code M:SS}. */
    public static String formatRuntime(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /** Value per hour from session start / amount gained. */
    public static int perHour(long amount, long runtimeMs) {
        if (runtimeMs <= 0) {
            return 0;
        }
        return (int) (amount * 3_600_000L / runtimeMs);
    }
}
