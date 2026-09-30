package net.runelite.client.plugins.lonebot.login;

import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Console + slf4j voor re-log / Play-klik. Throttle per sleutel (~1,5 s).
 */
public final class RelogLog {

    private static final Logger log = LoggerFactory.getLogger(RelogLog.class);
    private static final long THROTTLE_MS = 2000L;
    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

    private RelogLog() {
    }

    public static void log(String source, String msg) {
        String line = "[" + source + "] " + (msg != null ? msg : "");
        BotRuntime.logConsole(line);
        log.info(line);
    }

    public static void logThrottled(String source, String key, String msg) {
        long now = System.currentTimeMillis();
        String k = source + "|" + key;
        Long prev = LAST.get(k);
        if (prev != null && now - prev < THROTTLE_MS) {
            return;
        }
        LAST.put(k, now);
        log(source, msg);
    }

    public static void logBlock(String source, List<String> lines) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            log(source, line);
        }
    }
}
