package com.lonebot.example.quest.helpers;

import net.storm.sdk.items.BankSnapshot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * Leest Imp quest-loot progress ({@code ~/.lonebot/accounts/{RSN}/imps-quest-progress.txt})
 * zodat quests niet GE-kopen wat Imps al verzamelden (hammer / cadava / clay).
 * Zelfde formaat als {@code ImpQuestLootStore} — geen dependency op script-imp-killer.
 */
public final class QuestImpLootHints {

    private QuestImpLootHints() {
    }

    public static final class Hint {
        public final boolean hammer;
        public final boolean cadava;
        public final int clay;

        Hint(boolean hammer, boolean cadava, int clay) {
            this.hammer = hammer;
            this.cadava = cadava;
            this.clay = Math.max(0, clay);
        }
    }

    public static Hint load() {
        String rsn = BankSnapshot.displayName();
        File f = progressFile(rsn);
        if (f == null || !f.isFile()) {
            return new Hint(false, false, 0);
        }
        boolean hammer = false;
        boolean cadava = false;
        int clay = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                switch (name) {
                    case "hammer":
                        hammer = "1".equals(val) || Boolean.parseBoolean(val);
                        break;
                    case "cadava":
                        cadava = "1".equals(val) || Boolean.parseBoolean(val);
                        break;
                    case "clay":
                        try {
                            clay = Math.max(0, Integer.parseInt(val));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception ignored) {
            return new Hint(false, false, 0);
        }
        return new Hint(hammer, cadava, clay);
    }

    /**
     * Imp-JSON/snapshot zegt: dit item hoort in bank (of genoeg clay) — niet GE-kopen.
     */
    public static boolean likelyInBank(String itemName, int needQty) {
        if (itemName == null || itemName.isBlank()) {
            return false;
        }
        String n = itemName.trim();
        Hint h = load();
        if ("Hammer".equalsIgnoreCase(n) && h.hammer) {
            return true;
        }
        if ("Cadava berries".equalsIgnoreCase(n) && h.cadava) {
            return true;
        }
        if ("Clay".equalsIgnoreCase(n) && h.clay >= Math.max(1, needQty)) {
            return true;
        }
        try {
            return BankSnapshot.qty(n) >= Math.max(1, needQty);
        } catch (Throwable t) {
            return false;
        }
    }

    private static File progressFile(String rsn) {
        String safe;
        if (rsn == null || rsn.isBlank()) {
            safe = "_default";
        } else {
            safe = rsn.trim().toLowerCase(Locale.ROOT).replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safe.isEmpty()) {
                safe = "_default";
            }
        }
        return new File(System.getProperty("user.home") + File.separator + ".lonebot"
                + File.separator + "accounts", safe + File.separator + "imps-quest-progress.txt");
    }
}
