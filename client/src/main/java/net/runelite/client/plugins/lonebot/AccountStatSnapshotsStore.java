package net.runelite.client.plugins.lonebot;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Persistente hiscore/stat-snapshots (CombatBot-formaat), naast managed-accounts.json.
 */
public final class AccountStatSnapshotsStore {

    public static final class AccountStatSnapshot {
        public long totalGpApprox;
        public int combatLevel;
        public int woodcutting;
        public int mining;
        public int fishing;
        public int prayer;
        public int magic;
        public int quests;
        public int attack;
        public int strength;
        public int defence;
        public long updatedEpochMs;
        public boolean hiscoreSuspectBanned;
    }

    private AccountStatSnapshotsStore() {
    }

    public static File storeFile() {
        return new File(System.getProperty("user.home") + File.separator + ".lonebot",
                "account-stat-snapshots.txt");
    }

    public static synchronized Map<String, AccountStatSnapshot> load() {
        File f = storeFile();
        if (!f.isFile()) {
            return new LinkedHashMap<>();
        }
        try {
            String blob = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return parseSnapshots(blob);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static synchronized void save(Map<String, AccountStatSnapshot> map) {
        File f = storeFile();
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(f.toPath(), serializeSnapshots(map).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static synchronized void merge(String accountKey, AccountStatSnapshot snap) {
        if (accountKey == null || accountKey.trim().isEmpty() || snap == null) {
            return;
        }
        Map<String, AccountStatSnapshot> m = load();
        m.put(accountKey.trim().toLowerCase(Locale.ROOT), snap);
        save(m);
    }

    public static AccountStatSnapshot snapshotForRow(Map<String, AccountStatSnapshot> map, String displayName) {
        if (map == null || displayName == null) {
            return null;
        }
        return map.get(displayName.trim().toLowerCase(Locale.ROOT));
    }

    public static Map<String, AccountStatSnapshot> parseSnapshots(String blob) {
        Map<String, AccountStatSnapshot> m = new LinkedHashMap<>();
        if (blob == null || blob.trim().isEmpty()) {
            return m;
        }
        for (String line : blob.split("\\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String rest = line.substring(eq + 1);
            String[] p = rest.split("\\|", -1);
            if (p.length < 7) {
                continue;
            }
            try {
                AccountStatSnapshot s = new AccountStatSnapshot();
                s.totalGpApprox = Long.parseLong(p[0]);
                s.combatLevel = Integer.parseInt(p[1]);
                s.woodcutting = Integer.parseInt(p[2]);
                s.mining = Integer.parseInt(p[3]);
                s.fishing = Integer.parseInt(p[4]);
                s.prayer = Integer.parseInt(p[5]);
                s.updatedEpochMs = Long.parseLong(p[6]);
                s.hiscoreSuspectBanned = p.length >= 8 && "1".equals(p[7].trim());
                if (p.length >= 11) {
                    s.attack = parseIntOrZero(p[8]);
                    s.strength = parseIntOrZero(p[9]);
                    s.defence = parseIntOrZero(p[10]);
                }
                if (p.length >= 12) {
                    s.magic = parseIntOrZero(p[11]);
                }
                if (p.length >= 13) {
                    s.quests = parseIntOrZero(p[12]);
                }
                m.put(key, s);
            } catch (NumberFormatException ignored) {
            }
        }
        return m;
    }

    public static String serializeSnapshots(Map<String, AccountStatSnapshot> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, AccountStatSnapshot> e : map.entrySet()) {
            AccountStatSnapshot s = e.getValue();
            if (s == null) {
                continue;
            }
            sb.append(e.getKey()).append('=')
                    .append(s.totalGpApprox).append('|')
                    .append(s.combatLevel).append('|')
                    .append(s.woodcutting).append('|')
                    .append(s.mining).append('|')
                    .append(s.fishing).append('|')
                    .append(s.prayer).append('|')
                    .append(s.updatedEpochMs).append('|')
                    .append(s.hiscoreSuspectBanned ? "1" : "0").append('|')
                    .append(Math.max(0, s.attack)).append('|')
                    .append(Math.max(0, s.strength)).append('|')
                    .append(Math.max(0, s.defence)).append('|')
                    .append(Math.max(0, s.magic)).append('|')
                    .append(Math.max(0, s.quests)).append('\n');
        }
        return sb.toString();
    }

    public static boolean isRotationEligible(ManagedAccountsStore.ManagedAccount r,
                                             Map<String, AccountStatSnapshot> snaps) {
        return r != null && !r.isEmpty() && r.rotationEnabled
                && r.displayName != null && !r.displayName.trim().isEmpty()
                && !isHiscoreSuspectBanned(snaps, r.displayName);
    }

    public static boolean isHiscoreSuspectBanned(Map<String, AccountStatSnapshot> snaps, String displayName) {
        AccountStatSnapshot s = snapshotForRow(snaps, displayName);
        return s != null && s.hiscoreSuspectBanned;
    }

    /** Alleen CombatBot-flag: hiscore API gaf niets terug. */
    public static boolean isBannedAccount(ManagedAccountsStore.ManagedAccount r,
                                          Map<String, AccountStatSnapshot> snaps) {
        if (r == null || r.displayName == null) {
            return false;
        }
        return isHiscoreSuspectBanned(snaps, r.displayName);
    }

    public static synchronized boolean disableRotationForBanned(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return false;
        }
        String norm = displayName.trim().toLowerCase(Locale.ROOT);
        boolean changed = false;
        for (ManagedAccountsStore.ManagedAccount r : ManagedAccountsStore.mutableAccounts()) {
            if (r == null || r.displayName == null) {
                continue;
            }
            if (norm.equals(r.displayName.trim().toLowerCase(Locale.ROOT))) {
                if (r.rotationEnabled) {
                    r.rotationEnabled = false;
                    r.enabled = false;
                    String note = r.notes != null ? r.notes : "";
                    if (!note.contains("[hiscore]")) {
                        r.notes = (note.isEmpty() ? "" : note + " ")
                                + "[hiscore] vermoedelijk gebanned — rotatie uit";
                    }
                    changed = true;
                }
            }
        }
        if (changed) {
            ManagedAccountsStore.save();
        }
        return changed;
    }

    private static int parseIntOrZero(String s) {
        if (s == null || s.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String formatGp(long gp) {
        if (gp <= 0) {
            return "—";
        }
        if (gp >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fm", gp / 1_000_000.0);
        }
        if (gp >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", gp / 1_000.0);
        }
        return String.valueOf(gp);
    }

    public static String formatAgo(long epochMs) {
        if (epochMs <= 0) {
            return "—";
        }
        long sec = Math.max(0, (System.currentTimeMillis() - epochMs) / 1000L);
        if (sec < 60) {
            return sec + "s";
        }
        if (sec < 3600) {
            return (sec / 60) + "m";
        }
        return (sec / 3600) + "u";
    }
}
