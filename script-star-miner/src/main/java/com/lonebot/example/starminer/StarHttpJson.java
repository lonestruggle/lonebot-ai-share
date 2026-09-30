package com.lonebot.example.starminer;

import net.storm.sdk.bot.BotRuntime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional GET JSON feed. Supports LoneBot {@code {stars:[...]}} and Star-Calling-Assist objects.
 */
public final class StarHttpJson {

    private static final Pattern OBJ = Pattern.compile("\\{([^{}]{8,400})\\}");
    private static final Pattern WORLD = Pattern.compile("(?i)\"world\"\\s*:\\s*\"?(\\d{3,4})");
    private static final Pattern TIER = Pattern.compile("(?i)\"(?:tier|size)\"\\s*:\\s*\"?([1-9])");
    private static final Pattern LOC = Pattern.compile("(?i)\"location\"\\s*:\\s*\"([^\"]{2,80})\"");
    private static final Pattern MINERS = Pattern.compile("(?i)\"miners\"\\s*:\\s*\"?(-?\\d+)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public List<StarCall> poll(String url, long nowMs) {
        List<StarCall> out = new ArrayList<>();
        if (url == null || url.isBlank()) {
            return out;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "LoneBot-StarMiner")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                logOnce("json HTTP " + res.statusCode());
                return out;
            }
            String body = res.body();
            Matcher om = OBJ.matcher(body);
            while (om.find()) {
                StarCall c = parseObject("{" + om.group(1) + "}", nowMs);
                if (c != null) {
                    out.add(c);
                }
            }
            if (out.isEmpty()) {
                out.addAll(StarCallParser.parseMany(body, nowMs, "json"));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logOnce("json fout: " + e.getClass().getSimpleName());
        }
        return out;
    }

    private static StarCall parseObject(String obj, long nowMs) {
        Matcher wm = WORLD.matcher(obj);
        if (!wm.find()) {
            return null;
        }
        int world = Integer.parseInt(wm.group(1));
        int tier = 0;
        Matcher tm = TIER.matcher(obj);
        if (tm.find()) {
            tier = Integer.parseInt(tm.group(1));
        }
        String loc = "";
        Matcher lm = LOC.matcher(obj);
        if (lm.find()) {
            loc = lm.group(1);
        }
        int miners = -1;
        Matcher mm = MINERS.matcher(obj);
        if (mm.find()) {
            miners = Integer.parseInt(mm.group(1));
        }
        StarLocations.Spot spot = StarLocations.match(loc);
        if (spot == null) {
            spot = StarLocations.match(obj);
        }
        if (spot == null) {
            return null;
        }
        return new StarCall(world, tier, loc, spot, miners, nowMs, false, "json", loc);
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
