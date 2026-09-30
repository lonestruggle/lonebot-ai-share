package net.storm.sdk.items;

import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.entities.Players;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Laatst-gezien bank-inhoud (id + naam). Live als de bank open is; anders de snapshot
 * van de laatste open-bank. Persist per RSN onder {@code ~/.lonebot/bank-snapshots/}.
 */
public final class BankSnapshot {

    private static final Logger log = LoggerFactory.getLogger(BankSnapshot.class);
    private static final long THROTTLE_MS = 1_000L;

    private static volatile long lastCaptureMs;
    private static volatile String loadedRsn;
    private static volatile boolean hasSnapshot;
    private static final Map<Integer, Integer> qtyById = new ConcurrentHashMap<>();
    private static final Map<String, Integer> qtyByName = new ConcurrentHashMap<>();

    private BankSnapshot() {
    }

    public static void captureIfOpenThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastCaptureMs < THROTTLE_MS) {
            return;
        }
        lastCaptureMs = now;
        try {
            if (!Bank.isOpen()) {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        capture();
    }

    public static void capture() {
        List<IInventoryItem> all;
        try {
            if (!Bank.isOpen()) {
                return;
            }
            all = Bank.getAll();
        } catch (Throwable t) {
            return;
        }
        Map<Integer, Integer> ids = new ConcurrentHashMap<>();
        Map<String, Integer> names = new ConcurrentHashMap<>();
        if (all != null) {
            for (IInventoryItem i : all) {
                if (i == null || i.getId() <= 0) {
                    continue;
                }
                int q = Math.max(1, i.getQuantity());
                ids.merge(i.getId(), q, Integer::sum);
                String n = i.getName();
                if (n != null && !n.isBlank()) {
                    names.merge(n.toLowerCase(Locale.ROOT).trim(), q, Integer::sum);
                }
            }
        }
        qtyById.clear();
        qtyById.putAll(ids);
        qtyByName.clear();
        qtyByName.putAll(names);
        hasSnapshot = true;
        String rsn = displayName();
        loadedRsn = rsn;
        persist(rsn);
        log.debug("[BankSnapshot] captured {} ids for {}", ids.size(), rsn);
    }

    public static boolean hasSnapshot() {
        ensureLoaded();
        return hasSnapshot;
    }

    public static boolean contains(int itemId) {
        try {
            if (Bank.isOpen()) {
                return itemId > 0 && Bank.contains(itemId);
            }
        } catch (Throwable ignored) {
        }
        ensureLoaded();
        return itemId > 0 && qtyById.getOrDefault(itemId, 0) > 0;
    }

    public static boolean contains(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            if (Bank.isOpen()) {
                return Bank.contains(name);
            }
        } catch (Throwable ignored) {
        }
        ensureLoaded();
        return qtyByName.getOrDefault(name.toLowerCase(Locale.ROOT).trim(), 0) > 0;
    }

    public static int qty(int itemId) {
        try {
            if (Bank.isOpen() && itemId > 0) {
                return Bank.getCount(true, itemId);
            }
        } catch (Throwable ignored) {
        }
        ensureLoaded();
        return itemId > 0 ? qtyById.getOrDefault(itemId, 0) : 0;
    }

    public static int qty(String name) {
        if (name == null || name.isBlank()) {
            return 0;
        }
        try {
            if (Bank.isOpen()) {
                return Bank.getCount(true, name);
            }
        } catch (Throwable ignored) {
        }
        ensureLoaded();
        return qtyByName.getOrDefault(name.toLowerCase(Locale.ROOT).trim(), 0);
    }

    public static String displayName() {
        try {
            IPlayer p = Players.getLocal();
            if (p == null || p.getName() == null) {
                return null;
            }
            String n = p.getName().replaceAll("<[^>]+>", "").trim();
            return n.isEmpty() ? null : n;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void ensureLoaded() {
        String rsn = displayName();
        if (rsn == null) {
            return;
        }
        if (hasSnapshot && rsn.equalsIgnoreCase(loadedRsn)) {
            return;
        }
        if (rsn.equalsIgnoreCase(loadedRsn) && hasSnapshot) {
            return;
        }
        loadFromDisk(rsn);
    }

    private static synchronized void loadFromDisk(String rsn) {
        if (rsn.equalsIgnoreCase(loadedRsn) && hasSnapshot) {
            return;
        }
        File f = snapshotFile(rsn);
        qtyById.clear();
        qtyByName.clear();
        hasSnapshot = false;
        loadedRsn = rsn;
        if (f == null || !f.isFile()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("HAS=")) {
                    hasSnapshot = true;
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                int qty = parseQty(line.substring(eq + 1).trim());
                if (qty <= 0) {
                    continue;
                }
                if (key.startsWith("n:")) {
                    qtyByName.put(key.substring(2).toLowerCase(Locale.ROOT), qty);
                } else {
                    try {
                        qtyById.put(Integer.parseInt(key), qty);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (!qtyById.isEmpty() || !qtyByName.isEmpty()) {
                hasSnapshot = true;
            }
        } catch (Throwable t) {
            log.warn("[BankSnapshot] load failed: {}", t.toString());
        }
    }

    private static void persist(String rsn) {
        File f = snapshotFile(rsn);
        if (f == null) {
            return;
        }
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# BankSnapshot v1\nHAS=1\n");
            for (Map.Entry<Integer, Integer> e : qtyById.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            for (Map.Entry<String, Integer> e : qtyByName.entrySet()) {
                sb.append("n:").append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            Files.writeString(f.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            log.warn("[BankSnapshot] persist failed: {}", t.toString());
        }
    }

    private static File snapshotFile(String rsn) {
        if (rsn == null || rsn.isBlank()) {
            return null;
        }
        String safe = rsn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (safe.isEmpty()) {
            return null;
        }
        return new File(System.getProperty("user.home") + File.separator + ".lonebot"
                + File.separator + "bank-snapshots", safe + ".txt");
    }

    private static int parseQty(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
