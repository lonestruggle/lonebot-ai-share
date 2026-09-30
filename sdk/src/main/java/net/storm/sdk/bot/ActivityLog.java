package net.storm.sdk.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Stappen-log per account/dag: bestand + Console + optionele Discord-webhook.
 * Alleen echte stappen — geen tick-spam.
 */
public final class ActivityLog {

    private static final Logger log = LoggerFactory.getLogger(ActivityLog.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter START_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEDUP_MS = 1500L;
    private static final int LOOP_REPEAT = 5;
    private static final long DISCORD_FLUSH_MS = 10 * 60 * 1000L;
    private static final int DISCORD_CHUNK = 1700;

    public static volatile String discordWebhook = "";

    private static volatile String accountKey = "_default";
    private static volatile String lastLine = "";
    private static volatile long lastWriteMs;
    private static volatile String lastKey = "";
    private static volatile String prevKey = "";
    private static volatile int sameCount;
    private static volatile int pingCount;
    private static volatile boolean loopEmitted;
    private static volatile int lastWorld = -1;
    private static volatile long lastDiscordFlushMs;
    private static final StringBuilder discordBuf = new StringBuilder();

    private static final ExecutorService DISCORD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lonebot-activity-discord");
        t.setDaemon(true);
        return t;
    });

    private ActivityLog() {
    }

    public static void setAccount(String account) {
        if (account != null && !account.isBlank()) {
            accountKey = sanitize(account);
        }
    }

    public static void sessionStart(String account, List<String> headerLines) {
        setAccount(account);
        resetLoop();
        lastWorld = -1;
        List<String> block = new ArrayList<>();
        String when = LocalDateTime.now().format(START_TS);
        block.add("===== START " + when + " =====");
        if (headerLines != null) {
            for (String h : headerLines) {
                if (h != null && !h.isBlank()) {
                    block.add(h);
                }
            }
        }
        block.add("=====================================");
        for (String line : block) {
            writeRaw(line, true);
        }
        lastDiscordFlushMs = System.currentTimeMillis();
    }

    public static void sessionStop(String why) {
        String w = why != null && !why.isBlank() ? why : "Stop";
        writeRaw(LocalDateTime.now().format(TS) + " [" + w + "]", true);
        writeRaw("===== STOP " + LocalDateTime.now().format(START_TS) + " =====", true);
        flushDiscord(true);
        resetLoop();
    }

    public static void pause() {
        step("Bot", "Pauze");
    }

    public static void step(String script, String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        String tag = script != null && !script.isBlank() ? script.trim() : "Bot";
        String body = msg.trim();
        String key = tag + "|" + body;
        long now = System.currentTimeMillis();
        if (key.equals(lastLine) && now - lastWriteMs < DEDUP_MS) {
            return;
        }

        if (key.equals(lastKey)) {
            sameCount++;
            pingCount = 0;
            if (sameCount == LOOP_REPEAT && !loopEmitted) {
                loopEmitted = true;
                writeFormatted("LOOP", tag + " zelfde stap " + sameCount + "×: " + body);
            }
            return;
        }
        if (key.equals(prevKey) && lastKey != null && !lastKey.isEmpty()) {
            pingCount++;
            sameCount = 1;
            String a = lastKey.contains("|") ? lastKey.substring(lastKey.indexOf('|') + 1) : lastKey;
            prevKey = lastKey;
            lastKey = key;
            if (pingCount >= LOOP_REPEAT) {
                if (!loopEmitted) {
                    loopEmitted = true;
                    writeFormatted("LOOP", tag + " zelfde 2 stappen " + pingCount + "×: "
                            + a + " ↔ " + body);
                }
                return;
            }
            lastLine = key;
            lastWriteMs = now;
            writeFormatted(tag, body);
            return;
        }

        prevKey = lastKey;
        lastKey = key;
        sameCount = 1;
        pingCount = 1;
        loopEmitted = false;
        lastLine = key;
        lastWriteMs = now;
        writeFormatted(tag, body);
    }

    public static void noteWorld(int world) {
        if (world <= 0) {
            return;
        }
        if (lastWorld <= 0) {
            lastWorld = world;
            return;
        }
        if (world != lastWorld) {
            int from = lastWorld;
            lastWorld = world;
            step("Hop", "wereld " + from + " → " + world);
        }
    }

    private static void writeFormatted(String tag, String body) {
        writeRaw(LocalDateTime.now().format(TS) + " [" + tag + "] " + body, true);
        if ("LOOP".equals(tag)) {
            flushDiscord(true);
        }
    }

    private static synchronized void writeRaw(String line, boolean alsoConsole) {
        File f = logFile(accountKey);
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("[ActivityLog] mkdir fail {}", parent);
                return;
            }
            try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(f, true)))) {
                w.println(line);
            }
        } catch (Exception e) {
            log.debug("[ActivityLog] write fail: {}", e.toString());
        }
        if (alsoConsole) {
            BotRuntime.logConsole(line);
        }
        queueDiscord(line);
    }

    private static synchronized void queueDiscord(String line) {
        if (discordWebhook == null || discordWebhook.isBlank()) {
            return;
        }
        discordBuf.append(line).append('\n');
        long now = System.currentTimeMillis();
        if (now - lastDiscordFlushMs >= DISCORD_FLUSH_MS || discordBuf.length() >= DISCORD_CHUNK) {
            flushDiscord(false);
        }
    }

    private static synchronized void flushDiscord(boolean force) {
        if (discordWebhook == null || discordWebhook.isBlank()) {
            discordBuf.setLength(0);
            return;
        }
        if (discordBuf.length() == 0) {
            return;
        }
        if (!force && System.currentTimeMillis() - lastDiscordFlushMs < 8_000L) {
            return;
        }
        String payload = discordBuf.toString();
        discordBuf.setLength(0);
        lastDiscordFlushMs = System.currentTimeMillis();
        String url = discordWebhook.trim();
        try {
            DISCORD.execute(() -> postDiscord(url, payload));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private static void postDiscord(String webhook, String text) {
        if (webhook == null || !webhook.startsWith("https://")) {
            return;
        }
        if (!webhook.contains("discord.com/api/webhooks/")
                && !webhook.contains("discordapp.com/api/webhooks/")) {
            return;
        }
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + DISCORD_CHUNK);
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > i + 200) {
                    end = nl + 1;
                }
            }
            String chunk = text.substring(i, end);
            i = end;
            try {
                String json = "{\"content\":" + jsonString("```\n" + chunk + "```") + "}";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                HttpURLConnection c = (HttpURLConnection) new URL(webhook).openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(4000);
                c.setReadTimeout(6000);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("User-Agent", "LoneBot-ActivityLog");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body);
                }
                int code = c.getResponseCode();
                c.disconnect();
                if (code < 200 || code >= 300) {
                    log.debug("[ActivityLog] discord HTTP {}", code);
                }
            } catch (Exception e) {
                log.debug("[ActivityLog] discord: {}", e.toString());
                return;
            }
        }
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') {
                sb.append('\\').append(ch);
            } else if (ch == '\n') {
                sb.append("\\n");
            } else if (ch == '\r') {
                sb.append("\\r");
            } else if (ch < 32) {
                sb.append(' ');
            } else {
                sb.append(ch);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static File logFile(String account) {
        String day = LocalDate.now().format(DAY);
        File dir = new File(LoneBotPaths.accountsRoot(), sanitize(account)
                + File.separator + "logs");
        return new File(dir, "activity-" + day + ".log");
    }

    private static String sanitize(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) {
            return "_default";
        }
        return accountKey.trim().replace('\u00A0', ' ')
                .replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void resetLoop() {
        lastKey = "";
        prevKey = "";
        sameCount = 0;
        pingCount = 0;
        loopEmitted = false;
        lastLine = "";
    }
}
