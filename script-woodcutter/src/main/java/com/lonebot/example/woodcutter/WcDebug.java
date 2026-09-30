package com.lonebot.example.woodcutter;

import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WC → LoneBot Console-tab. Throttle opzelfde regel.
 */
final class WcDebug {

    private static final Logger log = LoggerFactory.getLogger(WcDebug.class);
    private static final long THROTTLE_MS = 1400L;

    private static String lastKey = "";
    private static long lastMs;

    private WcDebug() {
    }

    static void log(String tag, String msg) {
        String line = "[WC/" + tag + "] " + (msg != null ? msg : "");
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

    /** Altijd loggen (geen throttle) — o.a. one-shot acties. */
    static void once(String tag, String msg) {
        String line = "[WC/" + tag + "] " + (msg != null ? msg : "");
        log.info(line);
        BotRuntime.logConsole(line);
    }
}
