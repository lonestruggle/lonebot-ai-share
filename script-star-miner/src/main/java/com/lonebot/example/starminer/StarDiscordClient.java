package com.lonebot.example.starminer;

import net.storm.sdk.bot.BotRuntime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Official Discord REST: GET /channels/{id}/messages (bot token).
 */
public final class StarDiscordClient {

    private static final String API = "https://discord.com/api/v10/channels/%s/messages?limit=40";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public List<StarCall> poll(StarFeedSettings cfg, long nowMs) {
        List<StarCall> out = new ArrayList<>();
        if (cfg == null || !cfg.hasDiscord()) {
            return out;
        }
        for (String channelId : cfg.channelIds) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(API, channelId)))
                        .timeout(Duration.ofSeconds(12))
                        .header("Authorization", "Bot " + cfg.discordToken)
                        .header("User-Agent", "LoneBot-StarMiner (local; +https://localhost)")
                        .GET()
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code == 401 || code == 403) {
                    logOnce("discord HTTP " + code + " — token/intent/rechten checken (geen user-token)");
                    continue;
                }
                if (code < 200 || code >= 300) {
                    logOnce("discord HTTP " + code + " kanaal " + channelId);
                    continue;
                }
                List<String> texts = StarCallParser.extractDiscordTexts(res.body());
                for (String t : texts) {
                    out.addAll(StarCallParser.parseMany(t, nowMs, "discord"));
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return out;
            } catch (Exception e) {
                logOnce("discord fout: " + e.getClass().getSimpleName());
            }
        }
        return out;
    }

    private long lastLogMs;
    private String lastLog = "";

    private void logOnce(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 15_000L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/feed] " + msg);
    }
}
