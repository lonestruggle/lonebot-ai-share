package net.runelite.client.plugins.lonebot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Per-account Imp-settings op disk — {@code ~/.lonebot/accounts/{RSN}/imps-settings.json}
 * (CombatBot-achtig: elk account eigen map).
 */
public final class AccountImpsSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(AccountImpsSettingsStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "imps-settings.json";

    private AccountImpsSettingsStore() {
    }

    public static final class ImpsAccountSettings {
        public String combatStyleOverride = "";
        public int ashLootPickPercent = 0;
        public boolean magicAutoUpdate = false;
        public boolean farmMoneyEnabled = false;
    }

    public static String normalizeRsn(String displayName) {
        if (displayName == null) {
            return "";
        }
        String n = displayName.replace('\u00A0', ' ').trim();
        // filesystem-safe
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public static File accountDir(String displayName) {
        String key = normalizeRsn(displayName);
        if (key.isEmpty()) {
            key = "_unknown";
        }
        return new File(System.getProperty("user.home") + File.separator + ".lonebot"
                + File.separator + "accounts", key);
    }

    public static File settingsFile(String displayName) {
        return new File(accountDir(displayName), FILE_NAME);
    }

    public static void writeFromManaged(ManagedAccountsStore.ManagedAccount a) {
        if (a == null || a.displayName == null || a.displayName.trim().isEmpty()) {
            return;
        }
        ImpsAccountSettings s = new ImpsAccountSettings();
        s.combatStyleOverride = ManagedAccountsStore.normalizeImpsCombatStyle(a.impsCombatStyleOverride);
        s.ashLootPickPercent = a.impsAshLootPickPercent;
        s.magicAutoUpdate = a.magicAutoUpdate;
        s.farmMoneyEnabled = a.impsFarmMoneyEnabled;
        File f = settingsFile(a.displayName);
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("Account-map aanmaken mislukt: {}", parent);
                return;
            }
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(s, w);
            }
        } catch (Exception e) {
            log.warn("Schrijven {} mislukt: {}", f, e.toString());
        }
    }

    /** Merge disk → managed row (disk wint niet over non-empty managed; alleen aanvullen / legacy). */
    public static void mergeIntoManaged(ManagedAccountsStore.ManagedAccount a) {
        if (a == null || a.displayName == null || a.displayName.trim().isEmpty()) {
            return;
        }
        File f = settingsFile(a.displayName);
        if (!f.isFile()) {
            return;
        }
        try (FileReader r = new FileReader(f)) {
            ImpsAccountSettings s = GSON.fromJson(r, ImpsAccountSettings.class);
            if (s == null) {
                return;
            }
            String diskStyle = ManagedAccountsStore.normalizeImpsCombatStyle(s.combatStyleOverride);
            String rowStyle = ManagedAccountsStore.normalizeImpsCombatStyle(a.impsCombatStyleOverride);
            if (rowStyle.isEmpty() && !diskStyle.isEmpty()) {
                a.impsCombatStyleOverride = diskStyle;
            } else if (!rowStyle.isEmpty()) {
                a.impsCombatStyleOverride = rowStyle; // normalize MAGIC→MAGE
            }
            if (a.impsAshLootPickPercent <= 0 && s.ashLootPickPercent > 0) {
                a.impsAshLootPickPercent = s.ashLootPickPercent;
            }
            // magicAutoUpdate: disk OR managed (checkbox blijft true als ooit aangezet in imps-settings)
            if (s.magicAutoUpdate) {
                a.magicAutoUpdate = true;
            }
            if (s.farmMoneyEnabled) {
                a.impsFarmMoneyEnabled = true;
            }
        } catch (Exception e) {
            log.warn("Lezen {} mislukt: {}", f, e.toString());
        }
    }
}
