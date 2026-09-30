package com.lonebot.example.quest;

import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console + slf4j: {@code [Quest/...]} every meaningful step.
 */
public final class QuestLog {

    private static final Logger log = LoggerFactory.getLogger(QuestLog.class);
    private static volatile String lastLine = "";
    private static volatile long lastMs;

    private QuestLog() {
    }

    public static void step(String scope, String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        String line = "[Quest/" + (scope != null ? scope : "?") + "] " + msg;
        long now = System.currentTimeMillis();
        if (line.equals(lastLine) && now - lastMs < 2_500L) {
            return;
        }
        lastLine = line;
        lastMs = now;
        BotRuntime.logConsole(line);
        log.info(line);
        BotRuntime.questStatus = msg;
    }

    public static void force(String scope, String msg) {
        lastLine = "";
        step(scope, msg);
    }
}
