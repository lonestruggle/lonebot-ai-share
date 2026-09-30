package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Client;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.lonebot.HoverCaptureLog;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.storm.sdk.bot.BotRuntime;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Log varbit/varp/varc-wijzigingen naar capture-log, met include-filter en noisy-mute.
 */
@Singleton
public class VarWatchHelper {

    private static final long CONSOLE_THROTTLE_MS = 1600L;
    private static final long NOISY_WINDOW_MS = 2000L;
    private static final int NOISY_MAX_IN_WINDOW = 6;
    private static final long MUTE_MS = 12_000L;
    private static final long GLOBAL_CAPTURE_GAP_MS = 80L;

    private final Client client;
    private final LoneBotConfig config;
    private final ConfigManager configManager;

    private final Map<String, Burst> bursts = new HashMap<>();
    private final Map<String, Long> lastConsole = new HashMap<>();
    private long lastCaptureMs;

    @Inject
    public VarWatchHelper(Client client, LoneBotConfig config, ConfigManager configManager) {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event == null || !enabled()) {
            return;
        }
        int vb = event.getVarbitId();
        int vp = event.getVarpId();
        int value = event.getValue();
        if (vb > 0) {
            if (!boolCfg("varWatchVarbits", true)) {
                return;
            }
            accept("vb", vb, String.valueOf(value), prevVarbit(vb, value));
            return;
        }
        if (vp >= 0) {
            if (!boolCfg("varWatchVarps", true)) {
                return;
            }
            accept("vp", vp, String.valueOf(value), prevVarp(vp, value));
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event == null || !enabled() || !boolCfg("varWatchVarcs", true)) {
            return;
        }
        int idx = event.getIndex();
        int val = 0;
        try {
            val = client.getVarcIntValue(idx);
        } catch (Throwable ignored) {
        }
        accept("vc", idx, String.valueOf(val), "?");
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged event) {
        if (event == null || !enabled() || !boolCfg("varWatchVarcs", true)) {
            return;
        }
        int idx = event.getIndex();
        String val = "";
        try {
            val = client.getVarcStrValue(idx);
        } catch (Throwable ignored) {
        }
        if (val == null) {
            val = "";
        }
        if (val.length() > 64) {
            val = val.substring(0, 64) + "…";
        }
        accept("vc", idx, "\"" + val.replace('\n', ' ') + "\"", "?");
    }

    private void accept(String kind, int id, String newVal, String oldHint) {
        if (!passesInclude(kind, id)) {
            return;
        }
        String key = kind + ":" + id;
        long now = System.currentTimeMillis();
        if (boolCfg("varWatchSkipNoisy", true) && muted(key, now)) {
            return;
        }
        String line = key + " → " + newVal;
        String detail = "kind: var\n"
                + "type: " + kind + "\n"
                + "id: " + id + "\n"
                + "value: " + newVal
                + (oldHint != null && !oldHint.equals("?") && !oldHint.equals(newVal)
                ? "\nprev: " + oldHint : "");
        if (now - lastCaptureMs >= GLOBAL_CAPTURE_GAP_MS) {
            lastCaptureMs = now;
            if (config == null || !config.captureSkipDuplicates()
                    || !HoverCaptureLog.wouldSkipDuplicate("var", line, detail)) {
                HoverCaptureLog.add("var", line, detail);
            }
        }
        Long last = lastConsole.get(key);
        if (last == null || now - last >= CONSOLE_THROTTLE_MS) {
            lastConsole.put(key, now);
            BotRuntime.logConsole("[Inspect/var] " + line);
        }
    }

    private boolean muted(String key, long now) {
        Burst b = bursts.get(key);
        if (b == null) {
            b = new Burst();
            bursts.put(key, b);
        }
        if (now < b.mutedUntil) {
            return true;
        }
        if (now - b.windowStart > NOISY_WINDOW_MS) {
            b.windowStart = now;
            b.count = 0;
        }
        b.count++;
        if (b.count > NOISY_MAX_IN_WINDOW) {
            b.mutedUntil = now + MUTE_MS;
            BotRuntime.logConsole("[Inspect/var] mute " + key + " (" + MUTE_MS / 1000 + "s, te vaak)");
            return true;
        }
        return false;
    }

    private boolean passesInclude(String kind, int id) {
        Set<String> include = parseInclude(liveFilter());
        if (include.isEmpty()) {
            return true;
        }
        return include.contains(kind + ":" + id) || include.contains(String.valueOf(id));
    }

    static Set<String> parseInclude(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,;\\s]+")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String p = part.trim().toLowerCase(Locale.ROOT);
            out.add(p);
        }
        return out;
    }

    private String liveFilter() {
        if (configManager != null) {
            String raw = configManager.getConfiguration("lonebot", "varWatchFilterIds");
            if (raw != null) {
                return raw;
            }
        }
        return config != null ? config.varWatchFilterIds() : "";
    }

    private boolean enabled() {
        return boolCfg("varWatchEnabled", false);
    }

    private boolean boolCfg(String key, boolean fallback) {
        if (configManager != null) {
            String raw = configManager.getConfiguration("lonebot", key);
            if (raw != null && !raw.isBlank()) {
                return Boolean.parseBoolean(raw.trim());
            }
        }
        if (config == null) {
            return fallback;
        }
        switch (key) {
            case "varWatchEnabled":
                return config.varWatchEnabled();
            case "varWatchVarbits":
                return config.varWatchVarbits();
            case "varWatchVarps":
                return config.varWatchVarps();
            case "varWatchVarcs":
                return config.varWatchVarcs();
            case "varWatchSkipNoisy":
                return config.varWatchSkipNoisy();
            default:
                return fallback;
        }
    }

    private String prevVarbit(int id, int nowVal) {
        try {
            int v = client.getVarbitValue(id);
            return v == nowVal ? "?" : String.valueOf(v);
        } catch (Throwable t) {
            return "?";
        }
    }

    private String prevVarp(int id, int nowVal) {
        try {
            int v = client.getVarpValue(id);
            return v == nowVal ? "?" : String.valueOf(v);
        } catch (Throwable t) {
            return "?";
        }
    }

    private static final class Burst {
        long windowStart;
        int count;
        long mutedUntil;
    }
}
