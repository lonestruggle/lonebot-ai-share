package net.runelite.client.plugins.lonebot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.LoneBotPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Export/import van geselecteerde accounts + per-RSN bestanden naar JSON (andere pc).
 * Bevat Jagex session tokens — privé houden.
 */
public final class AccountPackTransfer {

    public static final String FORMAT = "lonebot-accounts";
    public static final int PACK_VERSION = 1;
    private static final long MAX_FILE_BYTES = 512L * 1024L;

    private static final Logger log = LoggerFactory.getLogger(AccountPackTransfer.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private AccountPackTransfer() {
    }

    public static final class Pack {
        public String format = FORMAT;
        public int version = PACK_VERSION;
        public String clientVersion = "";
        public long exportedAtMs;
        public List<ManagedAccountsStore.ManagedAccount> accounts = new ArrayList<>();
        /** Relatief t.o.v. {@code ~/.lonebot/accounts/}, bv. {@code RSN/wc-settings.json}. */
        public Map<String, String> files = new LinkedHashMap<>();
        public Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snapshots = new LinkedHashMap<>();
    }

    public static final class Result {
        public int added;
        public int updated;
        public int filesWritten;
        public int snapshotsMerged;
        public int skippedInvalid;
        public String fatalError;

        public String summary() {
            if (fatalError != null) {
                return fatalError;
            }
            return added + " toegevoegd, " + updated + " bijgewerkt, "
                    + filesWritten + " bestand(en), " + snapshotsMerged + " snapshot(s)"
                    + (skippedInvalid > 0 ? ", " + skippedInvalid + " overgeslagen" : "");
        }
    }

    public static boolean isPack(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonElement el = new Gson().fromJson(stripBom(json.trim()), JsonElement.class);
            if (el == null || !el.isJsonObject()) {
                return false;
            }
            JsonObject o = el.getAsJsonObject();
            if (!o.has("format") || o.get("format").isJsonNull()) {
                return false;
            }
            return FORMAT.equalsIgnoreCase(o.get("format").getAsString().trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Pack buildPack(List<ManagedAccountsStore.ManagedAccount> selected) {
        Pack pack = new Pack();
        pack.clientVersion = LoneBotBootstrapPlugin.VERSION;
        pack.exportedAtMs = System.currentTimeMillis();
        if (selected == null) {
            return pack;
        }
        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps = AccountStatSnapshotsStore.load();
        for (ManagedAccountsStore.ManagedAccount row : selected) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            ManagedAccountsStore.ManagedAccount copy = row.copy();
            ManagedAccountsStore.normalizeSkills(copy);
            copy.impsCombatStyleOverride = ManagedAccountsStore.normalizeImpsCombatStyle(copy.impsCombatStyleOverride);
            pack.accounts.add(copy);
            collectAccountFiles(copy.displayName, pack.files);
            AccountStatSnapshotsStore.AccountStatSnapshot snap =
                    AccountStatSnapshotsStore.snapshotForRow(snaps, copy.displayName);
            if (snap != null && copy.displayName != null) {
                pack.snapshots.put(copy.displayName.trim().toLowerCase(Locale.ROOT), snap);
            }
        }
        return pack;
    }

    public static String toJson(Pack pack) {
        return GSON.toJson(pack != null ? pack : new Pack());
    }

    public static void writeFile(File dest, Pack pack) throws Exception {
        if (dest == null) {
            throw new IllegalArgumentException("Geen bestand");
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Map aanmaken mislukt: " + parent);
        }
        Files.writeString(dest.toPath(), toJson(pack), StandardCharsets.UTF_8);
    }

    public static Result importJson(String json) {
        Result out = new Result();
        if (json == null || json.isBlank()) {
            out.fatalError = "Lege JSON";
            return out;
        }
        Pack pack;
        try {
            pack = GSON.fromJson(stripBom(json.trim()), Pack.class);
        } catch (Exception e) {
            out.fatalError = "Ongeldige JSON: " + e.getMessage();
            return out;
        }
        if (pack == null || pack.accounts == null || pack.accounts.isEmpty()) {
            out.fatalError = "Geen accounts in bestand";
            return out;
        }
        ManagedAccountsStore.ensureLoaded();
        List<ManagedAccountsStore.ManagedAccount> existing = ManagedAccountsStore.mutableAccounts();
        for (ManagedAccountsStore.ManagedAccount incoming : pack.accounts) {
            if (incoming == null || incoming.isEmpty()) {
                out.skippedInvalid++;
                continue;
            }
            ManagedAccountsStore.normalizeSkills(incoming);
            incoming.impsCombatStyleOverride =
                    ManagedAccountsStore.normalizeImpsCombatStyle(incoming.impsCombatStyleOverride);
            ManagedAccountsStore.ManagedAccount match = findMatch(existing, incoming);
            if (match != null) {
                match.applyFrom(incoming);
                out.updated++;
            } else {
                ManagedAccountsStore.ManagedAccount copy = incoming.copy();
                existing.add(copy);
                out.added++;
            }
        }
        ManagedAccountsStore.save();

        if (pack.files != null) {
            for (Map.Entry<String, String> e : pack.files.entrySet()) {
                if (writeAccountFile(e.getKey(), e.getValue())) {
                    out.filesWritten++;
                }
            }
        }
        if (pack.snapshots != null) {
            for (Map.Entry<String, AccountStatSnapshotsStore.AccountStatSnapshot> e : pack.snapshots.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                AccountStatSnapshotsStore.merge(e.getKey(), e.getValue());
                out.snapshotsMerged++;
            }
        }
        BotRuntime.logConsole("[Accounts] import pack: " + out.summary());
        return out;
    }

    public static Result importFile(File file) {
        Result out = new Result();
        if (file == null || !file.isFile()) {
            out.fatalError = "Bestand niet gevonden";
            return out;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return importJson(json);
        } catch (Exception e) {
            out.fatalError = "Lezen mislukt: " + e.getMessage();
            return out;
        }
    }

    private static ManagedAccountsStore.ManagedAccount findMatch(
            List<ManagedAccountsStore.ManagedAccount> existing,
            ManagedAccountsStore.ManagedAccount incoming) {
        if (existing == null || incoming == null) {
            return null;
        }
        String sid = norm(incoming.sessionId);
        String cid = norm(incoming.characterId);
        String disp = normDisplay(incoming.displayName);
        if (!sid.isEmpty()) {
            for (ManagedAccountsStore.ManagedAccount r : existing) {
                if (r != null && sid.equals(norm(r.sessionId))) {
                    return r;
                }
            }
        }
        if (!cid.isEmpty()) {
            for (ManagedAccountsStore.ManagedAccount r : existing) {
                if (r != null && cid.equals(norm(r.characterId))) {
                    return r;
                }
            }
        }
        if (!disp.isEmpty()) {
            for (ManagedAccountsStore.ManagedAccount r : existing) {
                if (r != null && disp.equals(normDisplay(r.displayName))) {
                    return r;
                }
            }
        }
        return null;
    }

    private static void collectAccountFiles(String displayName, Map<String, String> files) {
        if (displayName == null || displayName.isBlank() || files == null) {
            return;
        }
        File dir = AccountImpsSettingsStore.accountDir(displayName);
        if (!dir.isDirectory()) {
            return;
        }
        Path root = dir.toPath();
        String folder = AccountImpsSettingsStore.normalizeRsn(displayName);
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Path rel = root.relativize(p);
                    if (rel.getNameCount() > 0 && "logs".equalsIgnoreCase(rel.getName(0).toString())) {
                        return;
                    }
                    if (Files.size(p) > MAX_FILE_BYTES) {
                        log.warn("Skip groot account-bestand: {}", p);
                        return;
                    }
                    String key = folder + "/" + rel.toString().replace('\\', '/');
                    files.put(key, Files.readString(p, StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    log.warn("Account-bestand lezen mislukt {}: {}", p, ex.toString());
                }
            });
        } catch (Exception e) {
            log.warn("Account-map lezen mislukt {}: {}", dir, e.toString());
        }
    }

    private static boolean writeAccountFile(String relative, String content) {
        if (relative == null || relative.isBlank() || content == null) {
            return false;
        }
        String cleaned = relative.replace('\\', '/').trim();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty() || cleaned.contains("..") || cleaned.startsWith("logs/")
                || cleaned.contains("/logs/")) {
            return false;
        }
        File dest = new File(LoneBotPaths.accountsRoot(), cleaned.replace('/', File.separatorChar));
        File root;
        try {
            root = LoneBotPaths.accountsRoot().getCanonicalFile();
            File canon = dest.getCanonicalFile();
            if (!canon.getPath().startsWith(root.getPath() + File.separator) && !canon.equals(root)) {
                log.warn("Skip pad buiten accounts-root: {}", relative);
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        try {
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            Files.writeString(dest.toPath(), content, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            log.warn("Account-bestand schrijven mislukt {}: {}", dest, e.toString());
            return false;
        }
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String normDisplay(String s) {
        return s == null ? "" : s.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
    }
}
