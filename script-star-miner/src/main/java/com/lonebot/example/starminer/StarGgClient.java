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
 * 07.gg shooting-star tracker — zelfde feed als
 * https://07.gg/trackers/shooting-star ({@code old.07.gg/shooting-stars/api/calls}).
 */
public final class StarGgClient {

    static final String DEFAULT_URL = "https://old.07.gg/shooting-stars/api/calls";
    private static final String PAGE = "https://07.gg/trackers/shooting-star";

    private static final Pattern OBJ = Pattern.compile("\\{([^{}]{12,800})\\}");
    private static final Pattern WORLD = Pattern.compile("(?i)\"world\"\\s*:\\s*(\\d{3,4})");
    private static final Pattern TIER = Pattern.compile("(?i)\"tier\"\\s*:\\s*([1-9])");
    private static final Pattern RAW = Pattern.compile("(?i)\"rawLocation\"\\s*:\\s*\"([^\"]{2,80})\"");
    private static final Pattern KEY = Pattern.compile("(?i)\"locationKey\"\\s*:\\s*\"([^\"]{2,80})\"");
    private static final Pattern CALLED = Pattern.compile("(?i)\"calledAt\"\\s*:\\s*(\\d{10,})");
    private static final Pattern END = Pattern.compile("(?i)\"estimatedEnd\"\\s*:\\s*(\\d{10,})");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private volatile boolean lastOk;
    private long lastLogMs;
    private String lastLog = "";

    public boolean lastOk() {
        return lastOk;
    }

    public List<StarCall> poll(long nowMs) {
        List<StarCall> out = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(DEFAULT_URL))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "LoneBot-StarMiner")
                    .header("Accept", "application/json")
                    .header("Referer", PAGE)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code < 200 || code >= 300) {
                lastOk = false;
                logOnce("07.gg HTTP " + code);
                return out;
            }
            String body = res.body() != null ? res.body() : "";
            Matcher om = OBJ.matcher(body);
            int unmatched = 0;
            while (om.find()) {
                Parse p = parseObject("{" + om.group(1) + "}", nowMs);
                if (p == null) {
                    continue;
                }
                if (p.call != null) {
                    out.add(p.call);
                } else if (p.unmatchedLoc) {
                    unmatched++;
                }
            }
            lastOk = true;
            if (out.isEmpty()) {
                logOnce(unmatched > 0
                        ? "07.gg 0 sterren (" + unmatched + " onbekende locatie)"
                        : "07.gg leeg");
            } else if (unmatched > 0) {
                logOnce("07.gg " + out.size() + " sterren (" + unmatched + " onbekende locatie)");
            } else {
                logOnce("07.gg " + out.size() + " sterren");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            lastOk = false;
        } catch (Exception e) {
            lastOk = false;
            logOnce("07.gg fout: " + e.getClass().getSimpleName());
        }
        return out;
    }

    private static final class Parse {
        final StarCall call;
        final boolean unmatchedLoc;

        Parse(StarCall call, boolean unmatchedLoc) {
            this.call = call;
            this.unmatchedLoc = unmatchedLoc;
        }
    }

    private static Parse parseObject(String obj, long nowMs) {
        Matcher wm = WORLD.matcher(obj);
        if (!wm.find()) {
            return null;
        }
        int world = Integer.parseInt(wm.group(1));
        Matcher endm = END.matcher(obj);
        if (endm.find()) {
            try {
                long end = epochMs(Long.parseLong(endm.group(1)));
                if (end > 0L && end < nowMs) {
                    return null;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        int tier = 0;
        Matcher tm = TIER.matcher(obj);
        if (tm.find()) {
            tier = Integer.parseInt(tm.group(1));
        }
        String raw = "";
        Matcher rm = RAW.matcher(obj);
        if (rm.find()) {
            raw = rm.group(1);
        }
        String key = "";
        Matcher km = KEY.matcher(obj);
        if (km.find()) {
            key = km.group(1);
        }
        long calledAt = nowMs;
        Matcher cm = CALLED.matcher(obj);
        if (cm.find()) {
            try {
                calledAt = epochMs(Long.parseLong(cm.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        StarLocations.Spot spot = StarLocations.match(raw);
        if (spot == null && !key.isBlank()) {
            spot = StarLocations.match(key.replace('_', ' '));
        }
        if (spot == null) {
            return new Parse(null, true);
        }
        String loc = !raw.isBlank() ? raw : key;
        return new Parse(new StarCall(world, tier, loc, spot, -1, calledAt, false, "07gg", loc), false);
    }

    /** 07.gg stuurt millis; seconds (10 cijfers) ook accepteren. */
    private static long epochMs(long v) {
        if (v <= 0L) {
            return 0L;
        }
        return v < 10_000_000_000L ? v * 1000L : v;
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
