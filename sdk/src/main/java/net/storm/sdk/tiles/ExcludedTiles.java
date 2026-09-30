package net.storm.sdk.tiles;

import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.LoneBotPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Gemarkeerde tegels die de bot negeert: geen NPC/loot zoeken, niet oplopen, niet klikken.
 * Gedeeld voor alle accounts: {@code ~/.lonebot/excluded-tiles.txt}
 * Formaat: {@code X:Y:Z|X2:Y2:Z2}
 */
public final class ExcludedTiles {

    private static final Logger log = LoggerFactory.getLogger(ExcludedTiles.class);
    private static final Set<Long> KEYS = ConcurrentHashMap.newKeySet();
    private static final long RELOAD_MIN_MS = 800L;
    private static final long LOG_MIN_MS = 1500L;

    private static volatile long lastReloadCheckMs;
    private static volatile long lastFileMtime = Long.MIN_VALUE;
    private static volatile long lastLogMs;
    private static volatile boolean saving;

    private ExcludedTiles() {
    }

    public static boolean isExcluded(WorldPoint wp) {
        maybeReloadShared();
        return wp != null && KEYS.contains(key(wp));
    }

    /** True if any tile of the NPC footprint (+1 ring) is excluded. */
    public static boolean isNpcExcluded(WorldPoint sw, int size) {
        maybeReloadShared();
        if (sw == null) {
            return false;
        }
        int s = Math.max(1, size);
        for (int dx = -1; dx <= s; dx++) {
            for (int dy = -1; dy <= s; dy++) {
                if (KEYS.contains(key(sw.getX() + dx, sw.getY() + dy, sw.getPlane()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return true if added, false if removed */
    public static boolean toggle(WorldPoint wp) {
        if (wp == null) {
            return false;
        }
        long k = key(wp);
        if (KEYS.contains(k)) {
            KEYS.remove(k);
            return false;
        }
        KEYS.add(k);
        return true;
    }

    public static void add(WorldPoint wp) {
        if (wp != null) {
            KEYS.add(key(wp));
        }
    }

    public static void remove(WorldPoint wp) {
        if (wp != null) {
            KEYS.remove(key(wp));
        }
    }

    public static void clear() {
        KEYS.clear();
    }

    public static int size() {
        maybeReloadShared();
        return KEYS.size();
    }

    public static List<WorldPoint> all() {
        maybeReloadShared();
        List<WorldPoint> out = new ArrayList<>(KEYS.size());
        for (long k : KEYS) {
            out.add(fromKey(k));
        }
        return Collections.unmodifiableList(out);
    }

    /** Pathfinder avoid predicate (start/end still allowed by pathfinder itself). */
    public static Predicate<WorldPoint> avoidPredicate() {
        return ExcludedTiles::isExcluded;
    }

    public static String serialize() {
        if (KEYS.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (long k : KEYS) {
            WorldPoint wp = fromKey(k);
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(wp.getX()).append(':').append(wp.getY()).append(':').append(wp.getPlane());
        }
        return sb.toString();
    }

    public static void load(String data) {
        KEYS.clear();
        if (data == null || data.trim().isEmpty()) {
            return;
        }
        for (String part : data.split("\\|")) {
            String[] p = part.trim().split(":");
            if (p.length < 3) {
                continue;
            }
            try {
                int x = Integer.parseInt(p[0].trim());
                int y = Integer.parseInt(p[1].trim());
                int z = Integer.parseInt(p[2].trim());
                KEYS.add(key(x, y, z));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * Laad gedeelde lijst. Als het bestand nog niet bestaat, seed vanuit deze account-config en schrijf hem.
     */
    public static void loadSharedOrMigrate(String accountConfig) {
        Path file = sharedPath();
        try {
            LoneBotPaths.ensureDirs();
            if (Files.isRegularFile(file)) {
                load(readFile(file));
                lastFileMtime = Files.getLastModifiedTime(file).toMillis();
                logThrottled("[Tiles] shared load n=" + KEYS.size());
                return;
            }
        } catch (Exception e) {
            log.warn("[Tiles] shared load failed: {}", e.toString());
        }
        load(accountConfig);
        saveShared();
        logThrottled("[Tiles] shared migrate n=" + KEYS.size());
    }

    /** Schrijf naar {@code ~/.lonebot/excluded-tiles.txt} (alle accounts). */
    public static void saveShared() {
        Path file = sharedPath();
        saving = true;
        try {
            LoneBotPaths.ensureDirs();
            Files.writeString(file, serialize(), StandardCharsets.UTF_8);
            if (Files.isRegularFile(file)) {
                lastFileMtime = Files.getLastModifiedTime(file).toMillis();
            }
            logThrottled("[Tiles] shared save n=" + KEYS.size());
        } catch (Exception e) {
            log.warn("[Tiles] shared save failed: {}", e.toString());
        } finally {
            saving = false;
        }
    }

    private static void maybeReloadShared() {
        if (saving) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastReloadCheckMs < RELOAD_MIN_MS) {
            return;
        }
        lastReloadCheckMs = now;
        Path file = sharedPath();
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (mtime == lastFileMtime) {
                return;
            }
            load(readFile(file));
            lastFileMtime = mtime;
            logThrottled("[Tiles] shared reload n=" + KEYS.size());
        } catch (Exception e) {
            log.warn("[Tiles] shared reload failed: {}", e.toString());
        }
    }

    private static Path sharedPath() {
        return LoneBotPaths.excludedTilesFile().toPath();
    }

    private static String readFile(Path file) throws Exception {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastLogMs < LOG_MIN_MS) {
            return;
        }
        lastLogMs = now;
        log.info(msg);
        BotRuntime.logConsole(msg);
    }

    private static long key(WorldPoint wp) {
        return key(wp.getX(), wp.getY(), wp.getPlane());
    }

    private static long key(int x, int y, int z) {
        return ((long) (z & 0x3) << 42) | ((long) (y & 0x1FFFFF) << 21) | (x & 0x1FFFFF);
    }

    private static WorldPoint fromKey(long k) {
        int x = (int) (k & 0x1FFFFF);
        int y = (int) ((k >> 21) & 0x1FFFFF);
        int z = (int) ((k >> 42) & 0x3);
        return new WorldPoint(x, y, z);
    }
}
