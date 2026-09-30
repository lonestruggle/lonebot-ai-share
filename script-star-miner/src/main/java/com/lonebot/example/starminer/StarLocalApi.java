package com.lonebot.example.starminer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.storm.sdk.bot.BotRuntime;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Local read-only API: Discord calls → JSON/HTML on 127.0.0.1.
 */
public final class StarLocalApi {

    private HttpServer server;
    private int port;

    public synchronized void start(int port) {
        stop();
        int p = port > 0 ? port : StarFeedSettings.DEFAULT_PORT;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", p), 0);
            server.createContext("/", this::handleRoot);
            server.createContext("/stars", this::handleStars);
            server.createContext("/health", this::handleHealth);
            server.setExecutor(null);
            server.start();
            this.port = p;
            BotRuntime.logConsole("[Star/api] http://127.0.0.1:" + p + "/stars");
        } catch (Exception e) {
            BotRuntime.logConsole("[Star/api] start mislukt: " + e.getClass().getSimpleName()
                    + " — poort " + p + " vrij?");
            server = null;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Exception ignored) {
            }
            server = null;
        }
    }

    public int port() {
        return port;
    }

    private void handleRoot(HttpExchange ex) {
        if ("/stars".equals(ex.getRequestURI().getPath()) || "/health".equals(ex.getRequestURI().getPath())) {
            return;
        }
        write(ex, 200, "text/html; charset=utf-8", html());
    }

    private void handleStars(HttpExchange ex) {
        write(ex, 200, "application/json; charset=utf-8", StarFeed.get().toJson());
    }

    private void handleHealth(HttpExchange ex) {
        StarFeed f = StarFeed.get();
        String json = "{\"ok\":true,\"count\":" + f.active().size()
                + ",\"updatedAt\":" + f.updatedAtMs()
                + ",\"discord\":" + f.discordOk()
                + ",\"portal\":" + f.portalOk()
                + ",\"gg\":" + f.ggOk()
                + "}";
        write(ex, 200, "application/json; charset=utf-8", json);
    }

    private String html() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=utf-8><title>LoneBot Stars</title>");
        sb.append("<style>body{font-family:sans-serif;background:#111;color:#ddd;padding:16px}");
        sb.append("table{border-collapse:collapse}td,th{border:1px solid #444;padding:6px 10px}");
        sb.append("a{color:#8ec8ff}</style></head><body>");
        sb.append("<h2>Shooting stars</h2><p><a href=/stars>JSON /stars</a> · ");
        sb.append("<a href=/health>health</a></p><table><tr>");
        sb.append("<th>World</th><th>Tier</th><th>Locatie</th><th>Miners</th><th>Leeftijd</th></tr>");
        long now = System.currentTimeMillis();
        for (StarCall c : StarFeed.get().active()) {
            long ageMin = Math.max(0L, (now - c.calledAtMs) / 60_000L);
            sb.append("<tr><td>").append(c.world).append("</td><td>")
                    .append(c.tier > 0 ? "T" + c.tier : "?")
                    .append("</td><td>").append(esc(c.spot != null ? c.spot.shortName : c.locationRaw))
                    .append("</td><td>").append(c.miners >= 0 ? c.miners : "-")
                    .append("</td><td>").append(ageMin).append("m</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void write(HttpExchange ex, int code, String type, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            ex.getResponseHeaders().set("Content-Type", type);
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception ignored) {
        } finally {
            ex.close();
        }
    }
}
