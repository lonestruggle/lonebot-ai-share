package net.runelite.client.plugins.lonebot;

import net.runelite.client.config.ConfigManager;
import net.storm.sdk.bot.BotRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimized-state voor alle status-overlays. Nieuwe panels: {@link MinimizableStatusOverlay}
 * — klik op de chip, geen extra ConfigItem nodig.
 */
final class OverlayMinimizeStore {

    interface Chip {
        String overlayId();

        boolean containsChip(int canvasX, int canvasY);
    }

    private static final Map<String, Boolean> MIN = new ConcurrentHashMap<>();
    private static final Map<String, Chip> CHIPS = new ConcurrentHashMap<>();
    private static volatile ConfigManager configManager;

    private OverlayMinimizeStore() {
    }

    static void bind(ConfigManager cm) {
        configManager = cm;
    }

    static void register(Chip chip) {
        if (chip == null || chip.overlayId() == null || chip.overlayId().isBlank()) {
            return;
        }
        CHIPS.put(chip.overlayId(), chip);
    }

    static List<Chip> chips() {
        return new ArrayList<>(CHIPS.values());
    }

    static boolean isMinimized(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        Boolean mem = MIN.get(id);
        if (mem != null) {
            return mem;
        }
        boolean v = readConfig(id);
        MIN.put(id, v);
        return v;
    }

    static void setMinimized(String id, boolean minimized) {
        if (id == null || id.isBlank()) {
            return;
        }
        MIN.put(id, minimized);
        if ("wc".equals(id)) {
            BotRuntime.wcDebugOverlayMinimized = minimized;
        }
        writeConfig(id, minimized);
    }

    static void toggle(String id) {
        setMinimized(id, !isMinimized(id));
    }

    private static boolean readConfig(String id) {
        ConfigManager cm = configManager;
        if (cm == null) {
            return false;
        }
        try {
            String v = cm.getConfiguration("lonebot", "overlayMin." + id);
            if (v != null && !v.isBlank()) {
                return Boolean.parseBoolean(v);
            }
            String legacy = legacyKey(id);
            if (legacy != null) {
                String lv = cm.getConfiguration("lonebot", legacy);
                if (lv != null && !lv.isBlank()) {
                    return Boolean.parseBoolean(lv);
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void writeConfig(String id, boolean minimized) {
        ConfigManager cm = configManager;
        if (cm == null) {
            return;
        }
        try {
            cm.setConfiguration("lonebot", "overlayMin." + id, String.valueOf(minimized));
            String legacy = legacyKey(id);
            if (legacy != null) {
                cm.setConfiguration("lonebot", legacy, String.valueOf(minimized));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String legacyKey(String id) {
        if ("control".equals(id)) {
            return "paintOverlayMinimized";
        }
        if ("wc".equals(id)) {
            return "wcDebugOverlayMinimized";
        }
        return null;
    }
}
