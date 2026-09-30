package com.lonebot.example.giants;

import net.storm.sdk.bot.BotRuntime;

/**
 * Throttled console lines with {@code [Giants/...]} prefix.
 */
public final class GiantsLog {

    private static String lastMsg = "";
    private static long lastMs;

    private GiantsLog() {
    }

    public static void action(String channel, String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        String line = "[Giants/" + (channel != null && !channel.isBlank() ? channel : "loop") + "] " + msg;
        long now = System.currentTimeMillis();
        if (line.equals(lastMsg) && now - lastMs < 1400L) {
            return;
        }
        lastMsg = line;
        lastMs = now;
        BotRuntime.logConsole(line);
    }
}
