package com.lonebot.example.imps;

import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-account quest-loot progress for Imp mode (CombatBot {@code AccountQuestProgressStore} mirror).
 * Caps: hammer / cadava / clay ({@link #CLAY_TARGET}) / wool ({@link #WOOL_TARGET}).
 * <p>Persist: {@code ~/.lonebot/accounts/{RSN}/imps-quest-progress.txt}
 */
public final class ImpQuestLootStore {

    private static final Logger log = LoggerFactory.getLogger(ImpQuestLootStore.class);
    private static final String FILE_NAME = "imps-quest-progress.txt";

    public static final int CLAY_TARGET = 6;
    public static final int WOOL_TARGET = 20;
    public static final String WOOL_ITEM = "Ball of wool";

    private static final ConcurrentHashMap<String, Entry> BY_ACCOUNT = new ConcurrentHashMap<>();

    private ImpQuestLootStore() {
    }

    public static final class Entry {
        public boolean hammerFromImp;
        public boolean cadavaSecured;
        public int claySecured;
        public int woolSecured;
        /** Blue wizard hat ooit gehad / gear-prep klaar — zie {@link #shouldPickWizardHat}. */
        public boolean wizardHatOwned;
    }

    public static Entry get(String accountKey) {
        String k = key(accountKey);
        return BY_ACCOUNT.computeIfAbsent(k, ImpQuestLootStore::loadOrNew);
    }

    public static boolean shouldPickCadava(String accountKey) {
        Entry e = get(accountKey);
        if (e.cadavaSecured) {
            return false;
        }
        return !Inventory.contains("Cadava berries") && bankQty("Cadava berries") <= 0;
    }

    public static boolean shouldPickHammer(String accountKey) {
        Entry e = get(accountKey);
        if (e.hammerFromImp) {
            return false;
        }
        return !Inventory.contains("Hammer") && bankQty("Hammer") <= 0;
    }

    public static boolean shouldPickClay(String accountKey) {
        return countClay(accountKey) < CLAY_TARGET;
    }

    public static boolean shouldPickWool(String accountKey) {
        return countWool(accountKey) < WOOL_TARGET;
    }

    public static int countClay(String accountKey) {
        int inv = Inventory.getCount(true, "Clay");
        int bank = bankQty("Clay");
        int secured = get(accountKey).claySecured;
        return Math.max(inv + bank, secured);
    }

    public static int countWool(String accountKey) {
        int inv = Inventory.getCount(true, WOOL_ITEM);
        int bank = bankQty(WOOL_ITEM);
        int secured = get(accountKey).woolSecured;
        return Math.max(inv + bank, secured);
    }

    public static void markCadava(String accountKey) {
        Entry e = get(accountKey);
        if (e.cadavaSecured) {
            return;
        }
        e.cadavaSecured = true;
        save(accountKey, e);
    }

    public static void markHammer(String accountKey) {
        Entry e = get(accountKey);
        if (e.hammerFromImp) {
            return;
        }
        e.hammerFromImp = true;
        save(accountKey, e);
    }

    public static void addClay(String accountKey, int qty) {
        if (qty <= 0) {
            return;
        }
        Entry e = get(accountKey);
        int invBank = Inventory.getCount(true, "Clay") + bankQty("Clay");
        e.claySecured = Math.min(CLAY_TARGET, Math.max(e.claySecured, Math.max(invBank, e.claySecured + qty)));
        save(accountKey, e);
    }

    public static void addWool(String accountKey, int qty) {
        if (qty <= 0) {
            return;
        }
        Entry e = get(accountKey);
        int invBank = Inventory.getCount(true, WOOL_ITEM) + bankQty(WOOL_ITEM);
        e.woolSecured = Math.min(WOOL_TARGET, Math.max(e.woolSecured, Math.max(invBank, e.woolSecured + qty)));
        save(accountKey, e);
    }

    /** Sync secured from live inv+bank (call when bank open). */
    public static void syncFromInvAndBank(String accountKey) {
        Entry e = get(accountKey);
        boolean changed = false;
        if (Inventory.contains("Cadava berries") || bankQty("Cadava berries") > 0) {
            if (!e.cadavaSecured) {
                e.cadavaSecured = true;
                changed = true;
            }
        }
        if (Inventory.contains("Hammer") || bankQty("Hammer") > 0) {
            if (!e.hammerFromImp) {
                e.hammerFromImp = true;
                changed = true;
            }
        }
        int clay = Math.min(CLAY_TARGET, Math.max(e.claySecured, Inventory.getCount(true, "Clay") + bankQty("Clay")));
        int wool = Math.min(WOOL_TARGET, Math.max(e.woolSecured, Inventory.getCount(true, WOOL_ITEM) + bankQty(WOOL_ITEM)));
        if (clay != e.claySecured) {
            e.claySecured = clay;
            changed = true;
        }
        if (wool != e.woolSecured) {
            e.woolSecured = wool;
            changed = true;
        }
        if (changed) {
            save(accountKey, e);
        }
    }

    /**
     * MAGE wizard-hat ground pickup:
     * <ul>
     *   <li>Al aan / in invent → niet pakken (mark owned)</li>
     *   <li>Niet aan en niet in invent → wél pakken (failsafe), ook als owned/bank</li>
     * </ul>
     */
    public static boolean shouldPickWizardHat(String accountKey, boolean mageStyle) {
        if (!mageStyle) {
            return false;
        }
        if (hasWizardHatEquipped()) {
            markWizardHatOwned(accountKey);
            return false;
        }
        if (hasWizardHatInInventory()) {
            return false;
        }
        // Failsafe: niet aan → grond loot, ook als we 'm al in bank/JSON hebben
        return true;
    }

    public static boolean hasWizardHatOwned(String accountKey) {
        return get(accountKey).wizardHatOwned || hasWizardHatEquipped() || hasWizardHatInInventory();
    }

    public static void markWizardHatOwned(String accountKey) {
        Entry e = get(accountKey);
        if (e.wizardHatOwned) {
            return;
        }
        e.wizardHatOwned = true;
        save(accountKey, e);
    }

    public static boolean hasWizardHatEquipped() {
        try {
            return Equipment.contains("Blue wizard hat") || Equipment.contains("Wizard hat");
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasWizardHatInInventory() {
        try {
            return Inventory.contains("Blue wizard hat") || Inventory.contains("Wizard hat");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Deposit-box: quest-loot wordt gedumpt (met beads) — niet behouden.
     * @deprecated altijd false; progress via mark/sync.
     */
    @Deprecated
    public static boolean isQuestKeepAtDepositBox(String accountKey, String itemName) {
        return false;
    }

    /** Bij deposit/bank: quest-items zijn dump/stort-kandidaten. */
    public static boolean isQuestBankDepositItem(String itemName) {
        if (itemName == null) {
            return false;
        }
        String n = itemName.trim();
        return "Cadava berries".equalsIgnoreCase(n)
                || "Hammer".equalsIgnoreCase(n)
                || "Clay".equalsIgnoreCase(n)
                || WOOL_ITEM.equalsIgnoreCase(n);
    }

    public static boolean isQuestKeepItem(String accountKey, String itemName) {
        return false;
    }

    public static String statusLine(String accountKey) {
        Entry e = get(accountKey);
        return "H:" + (e.hammerFromImp || Inventory.contains("Hammer") ? "1" : "0")
                + " C:" + (e.cadavaSecured || Inventory.contains("Cadava berries") ? "1" : "0")
                + " Clay:" + countClay(accountKey) + "/" + CLAY_TARGET
                + " Wool:" + countWool(accountKey) + "/" + WOOL_TARGET
                + " Hat:" + (hasWizardHatOwned(accountKey) ? (hasWizardHatEquipped() ? "on" : "own") : "0");
    }

    private static int bankQty(String name) {
        try {
            if (!Bank.isOpen()) {
                return 0;
            }
            return Bank.getCount(name);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Entry loadOrNew(String k) {
        File f = progressFile(k);
        if (!f.isFile()) {
            return new Entry();
        }
        Entry e = new Entry();
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
                        e.hammerFromImp = "1".equals(val) || Boolean.parseBoolean(val);
                        break;
                    case "cadava":
                        e.cadavaSecured = "1".equals(val) || Boolean.parseBoolean(val);
                        break;
                    case "clay":
                        try {
                            e.claySecured = Math.max(0, Math.min(CLAY_TARGET, Integer.parseInt(val)));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case "wool":
                        try {
                            e.woolSecured = Math.max(0, Math.min(WOOL_TARGET, Integer.parseInt(val)));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    case "wizardHat":
                    case "hat":
                        e.wizardHatOwned = "1".equals(val) || Boolean.parseBoolean(val);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception ex) {
            log.warn("[ImpQuest] load {} fail: {}", f, ex.toString());
        }
        return e;
    }

    private static void save(String accountKey, Entry e) {
        String k = key(accountKey);
        BY_ACCOUNT.put(k, e);
        File f = progressFile(k);
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("[ImpQuest] mkdir fail {}", parent);
                return;
            }
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
                w.write("# Imp quest loot progress\n");
                w.write("hammer=" + (e.hammerFromImp ? "1" : "0") + "\n");
                w.write("cadava=" + (e.cadavaSecured ? "1" : "0") + "\n");
                w.write("clay=" + e.claySecured + "\n");
                w.write("wool=" + e.woolSecured + "\n");
                w.write("wizardHat=" + (e.wizardHatOwned ? "1" : "0") + "\n");
            }
            log.info("[ImpQuest] saved {} → {}", k, statusLine(k));
        } catch (Exception ex) {
            log.warn("[ImpQuest] save {} fail: {}", f, ex.toString());
        }
    }

    private static File progressFile(String key) {
        String safe = key.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isEmpty()) {
            safe = "_default";
        }
        return new File(System.getProperty("user.home") + File.separator + ".lonebot"
                + File.separator + "accounts", safe + File.separator + FILE_NAME);
    }

    private static String key(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) {
            return "_default";
        }
        return accountKey.trim().toLowerCase(Locale.ROOT);
    }
}
