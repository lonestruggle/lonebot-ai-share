package net.runelite.client.plugins.lonebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Zet tijdelijke Jagex-email-naam om naar de live RSN uit de client.
 */
public final class AccountDisplayRename {

    private static final Logger log = LoggerFactory.getLogger(AccountDisplayRename.class);

    private AccountDisplayRename() {
    }

    /**
     * @return true als displayName is gewijzigd
     */
    public static synchronized boolean applyLiveRsn(String characterId, String liveRsn) {
        if (liveRsn == null) {
            return false;
        }
        String rsn = liveRsn.replace('\u00A0', ' ').trim();
        if (rsn.isEmpty() || rsn.indexOf('@') >= 0) {
            return false;
        }
        ManagedAccountsStore.ensureLoaded();
        ManagedAccountsStore.ManagedAccount row = null;
        if (characterId != null && !characterId.isBlank()) {
            row = ManagedAccountsStore.findByCharacterId(characterId.trim());
        }
        if (row == null) {
            String launched = System.getProperty("lonebot.account", "");
            if (!launched.isBlank()) {
                row = ManagedAccountsStore.findByDisplayName(launched);
            }
        }
        if (row == null) {
            return false;
        }
        String old = row.displayName != null ? row.displayName.trim() : "";
        if (old.equalsIgnoreCase(rsn)) {
            return false;
        }
        String oldEmail = row.loginEmail;
        row.displayName = rsn;
        if ((oldEmail == null || oldEmail.isBlank()) && old.indexOf('@') >= 0) {
            row.loginEmail = old;
        }
        ManagedAccountsStore.save();
        try {
            System.setProperty("lonebot.account", rsn);
        } catch (Throwable ignored) {
        }
        ClientRemoteBus.rekeyProcess(old, rsn);
        migrateAccountsDir(old, rsn);
        rekeySnapshot(old, rsn);
        log.info("[Jagex/import] RSN uit client: {} → {} (char={})", old, rsn, row.characterId);
        return true;
    }

    private static void migrateAccountsDir(String oldName, String newName) {
        try {
            Path from = AccountImpsSettingsStore.accountDir(oldName).toPath();
            Path to = AccountImpsSettingsStore.accountDir(newName).toPath();
            if (Files.isDirectory(from) && !Files.exists(to)) {
                Files.move(from, to);
            }
        } catch (Exception e) {
            log.debug("[Jagex/import] accounts-map rename: {}", e.toString());
        }
    }

    private static void rekeySnapshot(String oldName, String newName) {
        if (oldName == null || newName == null) {
            return;
        }
        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps = AccountStatSnapshotsStore.load();
        String ok = oldName.trim().toLowerCase(Locale.ROOT);
        String nk = newName.trim().toLowerCase(Locale.ROOT);
        if (ok.equals(nk) || !snaps.containsKey(ok)) {
            return;
        }
        AccountStatSnapshotsStore.AccountStatSnapshot s = snaps.remove(ok);
        if (s != null) {
            snaps.put(nk, s);
            AccountStatSnapshotsStore.save(snaps);
        }
    }
}
