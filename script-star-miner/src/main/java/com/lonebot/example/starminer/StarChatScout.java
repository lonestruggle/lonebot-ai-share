package com.lonebot.example.starminer;

import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clan/friends/public chat als fallback als Discord/Portal geen locatie heeft.
 * Voorbeeld: {@code 435 vsw} → wereld 435, Champions' Guild (Varrock SW).
 */
final class StarChatScout {

    private static final Pattern WORLD = Pattern.compile("(?i)\\b(?:w(?:orld)?\\s*[:#.-]?\\s*)?(\\d{3,4})\\b");
    private static final Pattern HERE = Pattern.compile("(?i)\\bhere\\b");
    private static final long POLL_MS = 1_500L;

    private static long lastPollMs;
    private static long lastLogMs;
    private static String lastLog = "";

    private StarChatScout() {
    }

    static List<StarCall> poll(long nowMs) {
        if (nowMs - lastPollMs < POLL_MS) {
            return List.of();
        }
        lastPollMs = nowMs;
        List<String> lines = new ArrayList<>();
        try {
            List<String> player = Chat.getRecentPlayerChat(40);
            if (player != null) {
                lines.addAll(player);
            }
        } catch (Throwable ignored) {
        }
        try {
            List<String> game = Chat.getRecentMessages(12);
            if (game != null) {
                lines.addAll(game);
            }
        } catch (Throwable ignored) {
        }
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<Integer, StarLocations.Spot> locByWorld = new HashMap<>();
        List<StarCall> out = new ArrayList<>();
        for (String raw : lines) {
            String t = clean(raw);
            if (t.length() < 5) {
                continue;
            }
            Matcher wm = WORLD.matcher(t);
            if (!wm.find()) {
                continue;
            }
            int world = Integer.parseInt(wm.group(1));
            if (world < 300 || world > 600) {
                continue;
            }
            StarCall parsed = StarCallParser.parseOne(t, nowMs, "chat");
            if (parsed != null && parsed.spot != null) {
                locByWorld.put(world, parsed.spot);
                out.add(parsed);
                continue;
            }
            StarLocations.Spot spot = StarLocations.match(t);
            if (spot != null) {
                locByWorld.put(world, spot);
                out.add(new StarCall(world, 0, spot.shortName, spot, -1, nowMs, false, "chat", t));
            }
        }
        for (String raw : lines) {
            String t = clean(raw);
            Matcher wm = WORLD.matcher(t);
            if (!wm.find() || !HERE.matcher(t).find()) {
                continue;
            }
            int world = Integer.parseInt(wm.group(1));
            if (world < 300 || world > 600) {
                continue;
            }
            StarLocations.Spot spot = locByWorld.get(world);
            if (spot == null) {
                continue;
            }
            boolean already = false;
            for (StarCall c : out) {
                if (c.world == world && c.spot != null && c.spot.key.equals(spot.key)) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                out.add(new StarCall(world, 0, spot.shortName, spot, -1, nowMs, false, "chat", t));
            }
        }
        return out;
    }

    static void logOnce(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 4_000L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/chat] " + msg);
    }

    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        int colon = t.indexOf(':');
        if (colon > 0 && colon < 20) {
            t = t.substring(colon + 1).trim();
        }
        return t.toLowerCase(Locale.ROOT);
    }
}
