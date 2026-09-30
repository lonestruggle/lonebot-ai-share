package com.lonebot.example.fishing;

import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class FishDebug {

    private static final Logger log = LoggerFactory.getLogger(FishDebug.class);
    private static final long THROTTLE_MS = 1400L;
    private static final Set<String> ONCE = ConcurrentHashMap.newKeySet();

    private static String lastKey = "";
    private static long lastMs;

    private FishDebug() {
    }

    static void log(String tag, String msg) {
        String line = "[Fish/" + tag + "] " + (msg != null ? msg : "");
        long now = System.currentTimeMillis();
        String key = tag + "|" + msg;
        if (key.equals(lastKey) && now - lastMs < THROTTLE_MS) {
            return;
        }
        lastKey = key;
        lastMs = now;
        log.info(line);
        BotRuntime.logConsole(line);
    }

    /** Echt één keer per key tot {@link #clearOnce()} (reload/reset). */
    static void once(String tag, String msg) {
        String key = tag + "|" + (msg != null ? msg : "");
        if (!ONCE.add(key)) {
            return;
        }
        String line = "[Fish/" + tag + "] " + (msg != null ? msg : "");
        log.info(line);
        BotRuntime.logConsole(line);
    }

    static void clearOnce() {
        ONCE.clear();
    }
}
