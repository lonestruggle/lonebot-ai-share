package net.runelite.client.plugins.lonebot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.storm.api.account.GameAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persistente lijst van Jagex-accounts (Storm-import / handmatig / CRUD-tabel).
 */
public final class ManagedAccountsStore {

    private static final Logger log = LoggerFactory.getLogger(ManagedAccountsStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ManagedAccount>>() {}.getType();

    private static final CopyOnWriteArrayList<ManagedAccount> ACCOUNTS = new CopyOnWriteArrayList<>();
    private static volatile boolean loaded;

    private ManagedAccountsStore() {
    }

    public static final class ManagedAccount {
        public String displayName = "";
        public String characterId = "";
        public String sessionId = "";
        /** Jagex-account e-mail (tijdelijke lijstnaam tot RSN uit de client). */
        public String loginEmail = "";
        /** Legacy e-mail/wachtwoord login (2FA-dashboard / non-JX). */
        public String loginPassword = "";
        /** Authenticator Base32-secret (TOTP). */
        public String totpSecret = "";
        /** Laatste Jagex mail-code (5 tekens) uit 2FA-dashboard. */
        public String lastMailCode = "";
        /** JX_REFRESH_TOKEN indien bekend. */
        public String refreshToken = "";
        /**
         * Stabiele instance-map ({@code ~/.lonebot/instances/…}), meestal characterId.
         * Leeg = folder uit displayName (bestaande accounts).
         */
        public String profileKey = "";
        /** Jagex-import zonder hiscore-OK: 404 is unranked, niet banned. */
        public boolean jagexUnrankedNew = false;
        public String notes = "";
        /** @deprecated gebruik {@link #rotationEnabled} */
        @Deprecated
        public boolean enabled = true;
        /** CombatBot-stijl: meedoen in account-rotatie. */
        public boolean rotationEnabled = true;

        /** NONE | IMP | COW | MONK | WC | FISHING | STAR | GIANTS | CLUE | IMP2 | QUEST | COOKS_ASSISTANT | … | EXAMPLE | BANK_TEST | CITY_TEST */
        public String preferredScript = "NONE";
        /** Per-account skill toggles (bron van waarheid naast preferredScript). */
        public boolean impKillerEnabled = false;
        public boolean cowCombatEnabled = false;
        public boolean woodcuttingEnabled = false;
        public boolean fishingEnabled = false;
        public String accountSwitchWorld = "";
        public boolean accountSwitchWorldHopEnabled = false;
        /**
         * Voorkeurswerelden bij client-start (komma-gescheiden IDs), bv. {@code 301,308,316}.
         * Leeg + {@link #accountSwitchWorld} gevuld → die ene wereld.
         */
        public String preferredWorlds = "";
        /** Leeg = globaal config.impsCombatStyle */
        public String impsCombatStyleOverride = "";
        /** 0 = globaal; anders 25/50/75/100 */
        public int impsAshLootPickPercent = 0;
        public boolean magicAutoUpdate = false;
        public boolean impsFarmMoneyEnabled = false;
        public boolean calibrateBankOnNextLogin = false;
        public int targetAttackLevel = 0;
        public int targetStrengthLevel = 0;
        public int targetDefenceLevel = 0;

        public boolean isEmpty() {
            return (displayName == null || displayName.trim().isEmpty())
                    && (sessionId == null || sessionId.trim().isEmpty())
                    && (loginEmail == null || loginEmail.trim().isEmpty())
                    && (totpSecret == null || totpSecret.trim().isEmpty());
        }

        public ManagedAccount copy() {
            ManagedAccount c = new ManagedAccount();
            c.applyFrom(this);
            return c;
        }

        /** Kopieer alle velden (login + skills + werelden) op dit object. */
        public void applyFrom(ManagedAccount src) {
            if (src == null) {
                return;
            }
            displayName = src.displayName;
            characterId = src.characterId;
            sessionId = src.sessionId;
            loginEmail = src.loginEmail;
            loginPassword = src.loginPassword;
            totpSecret = src.totpSecret;
            lastMailCode = src.lastMailCode;
            refreshToken = src.refreshToken;
            profileKey = src.profileKey;
            jagexUnrankedNew = src.jagexUnrankedNew;
            notes = src.notes;
            enabled = src.enabled;
            rotationEnabled = src.rotationEnabled;
            preferredScript = src.preferredScript;
            impKillerEnabled = src.impKillerEnabled;
            cowCombatEnabled = src.cowCombatEnabled;
            woodcuttingEnabled = src.woodcuttingEnabled;
            fishingEnabled = src.fishingEnabled;
            accountSwitchWorld = src.accountSwitchWorld;
            accountSwitchWorldHopEnabled = src.accountSwitchWorldHopEnabled;
            preferredWorlds = src.preferredWorlds;
            impsCombatStyleOverride = src.impsCombatStyleOverride;
            impsAshLootPickPercent = src.impsAshLootPickPercent;
            magicAutoUpdate = src.magicAutoUpdate;
            impsFarmMoneyEnabled = src.impsFarmMoneyEnabled;
            calibrateBankOnNextLogin = src.calibrateBankOnNextLogin;
            targetAttackLevel = src.targetAttackLevel;
            targetStrengthLevel = src.targetStrengthLevel;
            targetDefenceLevel = src.targetDefenceLevel;
        }

        public GameAccount toGameAccount() {
            String user = characterId != null && !characterId.isEmpty() ? characterId : displayName;
            String sess = sessionId != null ? sessionId : "";
            String disp = displayName != null && !displayName.isEmpty() ? displayName : user;
            return new GameAccount(user, sess, disp, sess, characterId != null ? characterId : "");
        }

        /** Wereld-IDs voor startup. Leeg = geen Default World forceren. */
        public List<Integer> preferredWorldIds() {
            List<Integer> ids = parseWorldIds(preferredWorlds);
            if (!ids.isEmpty()) {
                return ids;
            }
            // Legacy single-world veld alleen als hop nog aan stond
            if (accountSwitchWorldHopEnabled) {
                return parseWorldIds(accountSwitchWorld);
            }
            return ids;
        }

        public void setPreferredWorldIds(List<Integer> ids) {
            if (ids == null || ids.isEmpty()) {
                preferredWorlds = "";
                accountSwitchWorld = "";
                accountSwitchWorldHopEnabled = false;
                return;
            }
            Set<Integer> uniq = new LinkedHashSet<>();
            for (Integer id : ids) {
                if (id != null && id > 0) {
                    uniq.add(id);
                }
            }
            if (uniq.isEmpty()) {
                preferredWorlds = "";
                accountSwitchWorld = "";
                accountSwitchWorldHopEnabled = false;
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Integer id : uniq) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(id);
            }
            preferredWorlds = sb.toString();
            accountSwitchWorld = String.valueOf(uniq.iterator().next());
        }

        /** Korte cel-tekst voor de Werelden-kolom. */
        public String preferredWorldsCellText() {
            List<Integer> ids = preferredWorldIds();
            if (ids.isEmpty()) {
                return "—";
            }
            if (ids.size() == 1) {
                return String.valueOf(ids.get(0));
            }
            if (ids.size() <= 3) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(ids.get(i));
                }
                return sb.toString();
            }
            return ids.get(0) + "," + ids.get(1) + " +" + (ids.size() - 2);
        }

        public static List<Integer> parseWorldIds(String raw) {
            List<Integer> out = new ArrayList<>();
            if (raw == null || raw.trim().isEmpty()) {
                return out;
            }
            Set<Integer> seen = new LinkedHashSet<>();
            for (String part : raw.split("[,;\\s]+")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                if (p.toLowerCase(Locale.ROOT).startsWith("w")) {
                    p = p.substring(1);
                }
                try {
                    int id = Integer.parseInt(p);
                    if (id > 0) {
                        seen.add(id);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            out.addAll(seen);
            return out;
        }

        @Override
        public String toString() {
            String n = displayName != null && !displayName.isEmpty() ? displayName : "(geen naam)";
            String mark = rotationEnabled ? "" : " [niet geselecteerd]";
            return n + mark;
        }
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File f = storeFile();
        if (!f.isFile()) {
            return;
        }
        try (FileReader r = new FileReader(f)) {
            List<ManagedAccount> list = GSON.fromJson(r, LIST_TYPE);
            if (list != null) {
                ACCOUNTS.clear();
                for (ManagedAccount a : list) {
                    if (a != null && !a.isEmpty()) {
                        // migrate oude JSON zonder rotationEnabled
                        if (!a.rotationEnabled && a.enabled) {
                            a.rotationEnabled = true;
                        }
                        a.impsCombatStyleOverride = normalizeImpsCombatStyle(a.impsCombatStyleOverride);
                        normalizeSkills(a);
                        AccountImpsSettingsStore.mergeIntoManaged(a);
                        ACCOUNTS.add(a);
                    }
                }
            }
            log.info("Managed accounts geladen: {} uit {}", ACCOUNTS.size(), f.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Laden managed accounts mislukt: {}", e.toString());
        }
    }

    public static synchronized void save() {
        File f = storeFile();
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            for (ManagedAccount a : ACCOUNTS) {
                if (a != null) {
                    a.impsCombatStyleOverride = normalizeImpsCombatStyle(a.impsCombatStyleOverride);
                    normalizeSkills(a);
                }
            }
            try (FileWriter w = new FileWriter(f)) {
                GSON.toJson(new ArrayList<>(ACCOUNTS), w);
            }
            for (ManagedAccount a : ACCOUNTS) {
                AccountImpsSettingsStore.writeFromManaged(a);
            }
        } catch (Exception e) {
            log.warn("Opslaan managed accounts mislukt: {}", e.toString());
        }
    }

    /**
     * Combat style override normaliseren: leeg = globaal; {@code MAGIC} → {@code MAGE}
     * (oude account-UI bug).
     */
    public static String normalizeImpsCombatStyle(String raw) {
        if (raw == null) {
            return "";
        }
        String u = raw.trim().toUpperCase();
        if (u.isEmpty() || "(GLOBAAL)".equals(u) || "GLOBAL".equals(u)) {
            return "";
        }
        if ("MAGIC".equals(u)) {
            return "MAGE";
        }
        if ("MELEE".equals(u) || "RANGED".equals(u) || "MAGE".equals(u)) {
            return u;
        }
        return "";
    }

    /**
     * Sync preferredScript ← skill flags (flags zijn source of truth).
     * Nooit flags terug aanzetten vanuit preferredScript — dat maakte Imp-checkbox onuitzetbaar.
     */
    public static void normalizeSkills(ManagedAccount a) {
        if (a == null) {
            return;
        }
        if (a.impKillerEnabled && a.cowCombatEnabled) {
            a.cowCombatEnabled = false;
        }
        if (a.impKillerEnabled && a.woodcuttingEnabled) {
            a.woodcuttingEnabled = false;
        }
        if (a.cowCombatEnabled && a.woodcuttingEnabled) {
            a.woodcuttingEnabled = false;
        }
        if (a.impKillerEnabled && a.fishingEnabled) {
            a.fishingEnabled = false;
        }
        if (a.cowCombatEnabled && a.fishingEnabled) {
            a.fishingEnabled = false;
        }
        if (a.woodcuttingEnabled && a.fishingEnabled) {
            a.fishingEnabled = false;
        }
        if (a.impKillerEnabled) {
            a.preferredScript = "IMP";
        } else if (a.woodcuttingEnabled) {
            a.preferredScript = "WC";
        } else if (a.fishingEnabled) {
            a.preferredScript = "FISHING";
        } else if (a.cowCombatEnabled) {
            a.preferredScript = "COW";
        } else {
            String p = a.preferredScript != null ? a.preferredScript.trim().toUpperCase() : "NONE";
            if ("STAR".equals(p) || "STARMINER".equals(p) || "STARS".equals(p)
                    || "IMP2".equals(p) || "IMPS2".equals(p)
                    || "MONK".equals(p) || "MONK_KILLER".equals(p) || "MONKKILLER".equals(p)
                    || "GIANTS".equals(p) || "GIANT".equals(p)
                    || "HILLGIANTS".equals(p) || "HILL_GIANTS".equals(p)
                    || "CLUE".equals(p) || "CLUES".equals(p) || "BEGINNER_CLUE".equals(p)
                    || "BEGINNERCLUE".equals(p) || "TREASURE_TRAIL".equals(p)
                    || LoneBotBotControl.isQuestPreferred(p)) {
                if ("MONK_KILLER".equals(p) || "MONKKILLER".equals(p)) {
                    a.preferredScript = "MONK";
                }
                if ("GIANT".equals(p) || "HILLGIANTS".equals(p) || "HILL_GIANTS".equals(p)) {
                    a.preferredScript = "GIANTS";
                }
                if ("CLUES".equals(p) || "BEGINNER_CLUE".equals(p) || "BEGINNERCLUE".equals(p)
                        || "TREASURE_TRAIL".equals(p)) {
                    a.preferredScript = "CLUE";
                }
                return;
            }
            if ("IMP".equals(p) || "IMPKILLER".equals(p) || "IMPS".equals(p)
                    || "COW".equals(p) || "COWS".equals(p)
                    || "WC".equals(p) || "WOODCUTTER".equals(p)
                    || "FISH".equals(p) || "FISHING".equals(p) || p.isEmpty()) {
                a.preferredScript = "NONE";
            }
        }
    }

    /** Zet skill flags + preferredScript en sla op. */
    public static synchronized void setSkillEnabled(String displayName, boolean imp, boolean cow) {
        ManagedAccount row = findByDisplayName(displayName);
        boolean wc = row != null && row.woodcuttingEnabled && !imp && !cow;
        boolean fish = row != null && row.fishingEnabled && !imp && !cow && !wc;
        setSkillEnabled(displayName, imp, cow, wc, fish);
    }

    public static synchronized void setSkillEnabled(String displayName, boolean imp, boolean cow, boolean wc) {
        ManagedAccount row = findByDisplayName(displayName);
        boolean fish = row != null && row.fishingEnabled && !imp && !cow && !wc;
        setSkillEnabled(displayName, imp, cow, wc, fish);
    }

    public static synchronized void setSkillEnabled(String displayName, boolean imp, boolean cow,
                                                    boolean wc, boolean fish) {
        ManagedAccount row = findByDisplayName(displayName);
        if (row == null) {
            return;
        }
        row.impKillerEnabled = imp;
        row.cowCombatEnabled = cow && !imp && !wc && !fish;
        row.woodcuttingEnabled = wc && !imp && !cow && !fish;
        row.fishingEnabled = fish && !imp && !cow && !wc;
        if (!row.impKillerEnabled && !row.cowCombatEnabled && !row.woodcuttingEnabled && !row.fishingEnabled) {
            row.preferredScript = "NONE";
        }
        normalizeSkills(row);
        save();
    }

    /** Star / Imps2 / Monk / Giants: geen WC/Fish/Imp/Cow-flag, wel preferredScript bewaren. */
    public static synchronized void setPreferredScript(String displayName, String script) {
        ManagedAccount row = findByDisplayName(displayName);
        if (row == null) {
            return;
        }
        row.impKillerEnabled = false;
        row.cowCombatEnabled = false;
        row.woodcuttingEnabled = false;
        row.fishingEnabled = false;
        String p = script != null && !script.isBlank() ? script.trim().toUpperCase() : "NONE";
        if ("MONK_KILLER".equals(p) || "MONKKILLER".equals(p)) {
            p = "MONK";
        }
        if ("GIANT".equals(p) || "HILLGIANTS".equals(p) || "HILL_GIANTS".equals(p)) {
            p = "GIANTS";
        }
        row.preferredScript = p;
        save();
    }

    public static ManagedAccount findByDisplayName(String displayName) {
        ensureLoaded();
        if (displayName == null || displayName.trim().isEmpty()) {
            return null;
        }
        String want = displayName.replace('\u00A0', ' ').trim();
        for (ManagedAccount a : ACCOUNTS) {
            if (a == null || a.displayName == null) {
                continue;
            }
            if (a.displayName.replace('\u00A0', ' ').trim().equalsIgnoreCase(want)) {
                return a;
            }
        }
        return null;
    }

    public static ManagedAccount findByCharacterId(String characterId) {
        ensureLoaded();
        if (characterId == null || characterId.trim().isEmpty()) {
            return null;
        }
        String want = characterId.trim();
        for (ManagedAccount a : ACCOUNTS) {
            if (a == null || a.characterId == null || a.characterId.isBlank()) {
                continue;
            }
            if (want.equalsIgnoreCase(a.characterId.trim())) {
                return a;
            }
        }
        return null;
    }

    /**
     * Effectieve Imp combat style: per-account override, anders globale config.
     */
    public static com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle resolveImpsCombatStyle(
            LoneBotConfig cfg, String displayName) {
        com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle global =
                cfg != null ? cfg.impsCombatStyle()
                        : com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle.MELEE;
        ManagedAccount row = findByDisplayName(displayName);
        if (row == null) {
            return global;
        }
        String ov = normalizeImpsCombatStyle(row.impsCombatStyleOverride);
        if (ov.isEmpty()) {
            return global;
        }
        try {
            return com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle.valueOf(ov);
        } catch (IllegalArgumentException e) {
            return global;
        }
    }

    public static List<ManagedAccount> getAccounts() {
        ensureLoaded();
        return Collections.unmodifiableList(ACCOUNTS);
    }

    /** Mutable live-lijst (zelfde objecten als store) — voor tabel-CRUD. */
    public static List<ManagedAccount> mutableAccounts() {
        ensureLoaded();
        return ACCOUNTS;
    }

    public static synchronized void add(ManagedAccount account) {
        ensureLoaded();
        if (account == null || account.isEmpty()) {
            return;
        }
        ACCOUNTS.add(account);
        save();
    }

    public static synchronized void addAll(List<ManagedAccount> accounts) {
        ensureLoaded();
        if (accounts == null || accounts.isEmpty()) {
            return;
        }
        for (ManagedAccount a : accounts) {
            if (a != null && !a.isEmpty()) {
                ACCOUNTS.add(a);
            }
        }
        save();
    }

    public static synchronized boolean remove(ManagedAccount account) {
        ensureLoaded();
        if (account == null) {
            return false;
        }
        boolean ok = ACCOUNTS.remove(account);
        if (ok) {
            save();
        }
        return ok;
    }

    public static synchronized void clear() {
        ACCOUNTS.clear();
        save();
    }

    public static String nextDefaultDisplayName() {
        ensureLoaded();
        return "account" + (ACCOUNTS.size() + 1);
    }

    public static File storeFile() {
        // Altijd shared ~/.lonebot — niet per client-instance (multi-launch)
        return new File(System.getProperty("user.home") + File.separator + ".lonebot",
                "managed-accounts.json");
    }
}
