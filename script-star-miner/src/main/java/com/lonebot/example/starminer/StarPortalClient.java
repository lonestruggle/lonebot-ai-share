package com.lonebot.example.starminer;

import net.storm.sdk.bot.BotRuntime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OSRS Portal shooting-stars tracker — zelfde feed als
 * https://osrsportal.com/shooting-stars-tracker
 */
public final class StarPortalClient {

    static final String DEFAULT_URL = "https://osrsportal.com/activestars-foxtrot";
    private static final String PAGE = "https://osrsportal.com/shooting-stars-tracker";

    private static final Pattern OBJ = Pattern.compile("\\{([^{}]{8,500})\\}");
    private static final Pattern WORLD = Pattern.compile("(?i)\"world\"\\s*:\\s*\"?(\\d{3,4})");
    private static final Pattern TIER = Pattern.compile("(?i)\"tier\"\\s*:\\s*\"?([1-9])");
    private static final Pattern LOC = Pattern.compile("(?i)\"loc\"\\s*:\\s*\"([^\"]{2,80})\"");
    private static final Pattern TIME_MIN = Pattern.compile("(?i)\"time\"\\s*:\\s*\"?(\\d{1,4})");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private volatile boolean lastOk;
    private long lastLogMs;
    private String lastLog = "";

    public boolean lastOk() {
        return lastOk;
    }

    public List<StarCall> poll(String url, long nowMs) {
        List<StarCall> out = new ArrayList<>();
        String target = url != null && !url.isBlank() ? url.trim() : DEFAULT_URL;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "LoneBot-StarMiner")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Referer", PAGE)
                    .header("Origin", "https://osrsportal.com")
                    .header("Authorization", "Bearer " + unsignedJwt())
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code < 200 || code >= 300) {
                lastOk = false;
                logOnce("portal HTTP " + code);
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
            lastOk = !out.isEmpty();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            lastOk = false;
        } catch (Exception e) {
            lastOk = false;
            logOnce("portal fout: " + e.getClass().getSimpleName());
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
        long calledAt = nowMs;
        Matcher agem = TIME_MIN.matcher(obj);
        if (agem.find()) {
            int mins = Integer.parseInt(agem.group(1));
            calledAt = nowMs - Math.min(mins, 24 * 60) * 60_000L;
        }
        StarLocations.Spot spot = StarLocations.match(loc);
        if (spot == null) {
            spot = StarLocations.match(obj);
        }
        if (spot == null) {
            return null;
        }
        return new StarCall(world, tier, loc, spot, -1, calledAt, false, "portal", loc);
    }

    /** Zelfde unsigned JWT als de tracker-pagina (alg: none, data: osrs_stars). */
    static String unsignedJwt() {
        long exp = System.currentTimeMillis() / 1000L + 300L;
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String payload = "{\"data\":\"osrs_stars\",\"exp\":" + exp + "}";
        Base64.Encoder enc = Base64.getEncoder();
        return enc.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "."
                + enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + ".";
    }

    private void logOnce(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 60_000L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/feed] " + msg);
    }
}
