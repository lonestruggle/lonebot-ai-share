package com.lonebot.example.starminer;

import net.storm.sdk.bot.LoneBotPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * {@code ~/.lonebot/star-feed.properties} — Discord bot token stays off the RuneLite config.
 */
public final class StarFeedSettings {

    public static final int DEFAULT_PORT = 18765;

    public final String discordToken;
    public final List<String> channelIds;
    public final String jsonUrl;
    public final int apiPort;
    public final boolean portalEnabled;
    public final boolean ggEnabled;

    public StarFeedSettings(String discordToken, List<String> channelIds, String jsonUrl, int apiPort,
                            boolean portalEnabled, boolean ggEnabled) {
        this.discordToken = discordToken != null ? discordToken.trim() : "";
        this.channelIds = channelIds != null ? channelIds : List.of();
        this.jsonUrl = jsonUrl != null ? jsonUrl.trim() : "";
        this.apiPort = apiPort > 0 ? apiPort : DEFAULT_PORT;
        this.portalEnabled = portalEnabled;
        this.ggEnabled = ggEnabled;
    }

    public boolean hasDiscord() {
        return !discordToken.isEmpty() && !channelIds.isEmpty();
    }

    public boolean hasJson() {
        return !jsonUrl.isEmpty();
    }

    public static File file() {
        return new File(LoneBotPaths.home(), "star-feed.properties");
    }

    public static StarFeedSettings load() {
        File f = file();
        if (!f.isFile()) {
            writeTemplate(f);
            return new StarFeedSettings("", List.of(), "", DEFAULT_PORT, true, true);
        }
        Properties p = new Properties();
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (Exception e) {
            return new StarFeedSettings("", List.of(), "", DEFAULT_PORT, true, true);
        }
        String token = p.getProperty("discord.bot.token", "").trim();
        List<String> ch = splitIds(p.getProperty("discord.channel.ids", ""));
        String url = p.getProperty("json.url", "").trim();
        int port = DEFAULT_PORT;
        try {
            port = Integer.parseInt(p.getProperty("api.port", String.valueOf(DEFAULT_PORT)).trim());
        } catch (Exception ignored) {
        }
        boolean portal = !"false".equalsIgnoreCase(p.getProperty("portal.enabled", "true").trim());
        boolean gg = !"false".equalsIgnoreCase(p.getProperty("gg.enabled", "true").trim());
        return new StarFeedSettings(token, ch, url, port, portal, gg);
    }

    static List<String> splitIds(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String p : raw.split("[,;\\s]+")) {
            String id = p.trim();
            if (id.matches("\\d{5,}")) {
                out.add(id);
            }
        }
        return out;
    }

    private static void writeTemplate(File f) {
        try {
            LoneBotPaths.ensureDirs();
            String body = "# LoneBot Star Miner — Discord → lokale API\n"
                    + "# Officiële Discord BOT-token (geen user-token / self-bot).\n"
                    + "# https://discord.com/developers/applications → Bot → Reset Token\n"
                    + "# Zet Message Content Intent AAN. Invite de bot in een kanaal met star-calls\n"
                    + "# (eigen relay-server als Star Miners geen bots toelaat).\n"
                    + "discord.bot.token=\n"
                    + "# Kanaal-IDs (Developer Mode → rechterklik kanaal → Copy Channel ID)\n"
                    + "discord.channel.ids=\n"
                    + "# Optionele extra JSON-feed (GET). Mag leeg.\n"
                    + "json.url=\n"
                    + "# OSRS Portal tracker: https://osrsportal.com/shooting-stars-tracker\n"
                    + "portal.enabled=true\n"
                    + "# 07.gg tracker: https://07.gg/trackers/shooting-star\n"
                    + "gg.enabled=true\n"
                    + "api.port=" + DEFAULT_PORT + "\n";
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(body);
            }
        } catch (Exception ignored) {
        }
    }
}
