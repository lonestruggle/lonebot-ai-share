package net.runelite.client.plugins.lonebot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Per-account WC-settings — {@code ~/.lonebot/accounts/{RSN}/wc-settings.json}
 * (naast imps-settings.json). ConfigManager blijft live runtime; disk = per RSN.
 */
public final class AccountWcSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(AccountWcSettingsStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "wc-settings.json";

    private AccountWcSettingsStore() {
    }

    public static final class WcAccountSettings {
        public boolean firemaking = true;
        public boolean dropLogs = false;
        public boolean forestryEvents = true;
        public boolean birdNests = true;
        public boolean useSpecificTree = false;
        public String treeName = "";
        public String location = "AUTO";
    }

    public static File settingsFile(String displayName) {
        return new File(AccountImpsSettingsStore.accountDir(displayName), FILE_NAME);
    }

    public static void write(String displayName, WcAccountSettings s) {
        if (displayName == null || displayName.trim().isEmpty() || s == null) {
            return;
        }
        File f = settingsFile(displayName);
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("WC account-map mislukt: {}", parent);
                return;
            }
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(s, w);
            }
            log.debug("WC settings opgeslagen → {}", f.getAbsolutePath());
        } catch (Exception e) {
            log.warn("WC settings schrijven mislukt: {}", e.toString());
        }
    }

    public static WcAccountSettings read(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return null;
        }
        File f = settingsFile(displayName);
        if (!f.isFile()) {
            return null;
        }
        try (FileReader r = new FileReader(f)) {
            return GSON.fromJson(r, WcAccountSettings.class);
        } catch (Exception e) {
            log.warn("WC settings lezen mislukt: {}", e.toString());
            return null;
        }
    }

    /** Schrijf huidige panel/config naar account-bestand. */
    public static void writeFromConfig(String displayName, LoneBotConfig config) {
        if (config == null || displayName == null || displayName.trim().isEmpty()) {
            return;
        }
        WcAccountSettings s = new WcAccountSettings();
        s.firemaking = config.wcFiremaking();
        s.dropLogs = config.wcDropLogs();
        s.forestryEvents = config.wcForestryEvents();
        s.birdNests = config.wcBirdNests();
        s.useSpecificTree = config.wcUseSpecificTree();
        s.treeName = config.wcTreeName() != null ? config.wcTreeName() : "";
        s.location = config.wcLocation() != null ? config.wcLocation() : "AUTO";
        write(displayName, s);
    }

    /**
     * Laad disk → ConfigManager (als bestand bestaat).
     *
     * @return true als iets geladen is
     */
    public static boolean applyToConfigManager(String displayName,
                                              net.runelite.client.config.ConfigManager cm) {
        WcAccountSettings s = read(displayName);
        if (s == null || cm == null) {
            return false;
        }
        cm.setConfiguration("lonebot", "wcFiremaking", s.firemaking);
        cm.setConfiguration("lonebot", "wcDropLogs", s.dropLogs);
        cm.setConfiguration("lonebot", "wcForestryEvents", s.forestryEvents);
        cm.setConfiguration("lonebot", "wcBirdNests", s.birdNests);
        cm.setConfiguration("lonebot", "wcUseSpecificTree", s.useSpecificTree);
        cm.setConfiguration("lonebot", "wcTreeName", s.treeName != null ? s.treeName : "");
        cm.setConfiguration("lonebot", "wcLocation",
                s.location != null && !s.location.isBlank() ? s.location : "AUTO");
        log.info("WC settings geladen voor {} (loc={}, fm={})", displayName, s.location, s.firemaking);
        return true;
    }
}
