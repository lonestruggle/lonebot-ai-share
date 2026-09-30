package net.runelite.client.plugins.lonebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OSRS hiscores {@code index_lite.ws}.
 * Onderscheidt <b>niet gevonden (404)</b> van <b>tijdelijke netwerkfout</b> — die laatste is geen ban.
 */
public final class OsrsHiscoreApi {

    private static final Logger log = LoggerFactory.getLogger(OsrsHiscoreApi.class);

    private static final String[] LITE_URL_PREFIXES = {
            "https://secure.runescape.com/m=hiscore_oldschool/index_lite.ws?player=",
            "https://services.runescape.com/m=hiscore_oldschool/index_lite.ws?player=",
            "https://oldschool.runescape.com/hiscores_oldschool/index_lite.ws?player=",
            "https://secure.runescape.com/m=hiscore_oldschool_ironman/index_lite.ws?player=",
            "https://oldschool.runescape.com/hiscores_oldschool_ironman/index_lite.ws?player=",
            "https://secure.runescape.com/m=hiscore_oldschool_hardcore_ironman/index_lite.ws?player=",
            "https://oldschool.runescape.com/hiscores_oldschool_hardcore_ironman/index_lite.ws?player=",
            "https://secure.runescape.com/m=hiscore_oldschool_ultimate/index_lite.ws?player=",
            "https://oldschool.runescape.com/hiscores_oldschool_ultimate/index_lite.ws?player="
    };
    private static final int CONNECT_MS = 12000;
    private static final int READ_MS = 20000;
    private static final Pattern CSV_LINE = Pattern.compile("^-?\\d+,\\d+,\\d+$");

    private static final String[] LITE_LINE_NAMES = {
            "Overall",
            "Attack", "Defence", "Strength", "Hitpoints",
            "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
            "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing",
            "Mining", "Herblore", "Agility", "Thieving", "Slayer",
            "Farming", "Runecraft", "Hunter", "Construction",
            "Sailing"
    };

    public enum Status {
        /** Geldige hiscore-CSV. */
        OK,
        /** Alle boards: speler niet gevonden (404) — sterke ban/rename-indicatie. */
        NOT_FOUND,
        /** Timeout / 5xx / parse / netwerk — geen ban-conclusie. */
        TRANSIENT_ERROR
    }

    public static final class FetchResult {
        public final Status status;
        public final List<String> lines;
        public final String detail;

        public FetchResult(Status status, List<String> lines, String detail) {
            this.status = status;
            this.lines = lines;
            this.detail = detail != null ? detail : "";
        }
    }

    private OsrsHiscoreApi() {
    }

    public static AccountStatSnapshotsStore.AccountStatSnapshot fetchSnapshot(String displayName) {
        FetchResult fr = fetchWithRetries(displayName, 2);
        if (fr.status != Status.OK || fr.lines == null || fr.lines.size() < 16) {
            return null;
        }
        List<String> lines = fr.lines;
        int attack = parseLevel(lines.get(1));
        int defence = parseLevel(lines.get(2));
        int strength = parseLevel(lines.get(3));
        int hitpoints = parseLevel(lines.get(4));
        int ranged = parseLevel(lines.get(5));
        int prayer = parseLevel(lines.get(6));
        int magic = parseLevel(lines.get(7));
        int woodcutting = lines.size() > 9 ? parseLevel(lines.get(9)) : 0;
        int fishing = lines.size() > 11 ? parseLevel(lines.get(11)) : 0;
        int mining = lines.size() > 15 ? parseLevel(lines.get(15)) : 0;
        int combatLevel = computeCombatLevel(attack, defence, strength, hitpoints, ranged, prayer, magic);

        AccountStatSnapshotsStore.AccountStatSnapshot snap = new AccountStatSnapshotsStore.AccountStatSnapshot();
        snap.totalGpApprox = 0;
        snap.hiscoreSuspectBanned = false;
        snap.combatLevel = combatLevel;
        snap.attack = attack;
        snap.strength = strength;
        snap.defence = defence;
        snap.magic = magic;
        snap.woodcutting = woodcutting;
        snap.mining = mining;
        snap.fishing = fishing;
        snap.prayer = prayer;
        snap.updatedEpochMs = System.currentTimeMillis();
        return snap;
    }

    /**
     * @return null bij NOT_FOUND of TRANSIENT_ERROR (gebruik {@link #fetchFullSkillLevelsResult} voor status)
     */
    public static Map<String, String> fetchFullSkillLevels(String displayName) {
        FetchResult fr = fetchFullSkillLevelsResult(displayName);
        if (fr.status != Status.OK || fr.lines == null) {
            return null;
        }
        return linesToSkillMap(fr.lines);
    }

    public static FetchResult fetchFullSkillLevelsResult(String displayName) {
        FetchResult fr = fetchWithRetries(displayName, 2);
        if (fr.status != Status.OK) {
            return fr;
        }
        return new FetchResult(Status.OK, fr.lines, fr.detail);
    }

    public static String computeCombatLevelReactDisplay(Map<String, String> stats) {
        if (stats == null || stats.isEmpty()) {
            return "—";
        }
        int def = parseIntSkill(stats, "Defence", 1);
        int hp = parseIntSkill(stats, "Hitpoints", 10);
        int pray = parseIntSkill(stats, "Prayer", 1);
        int att = parseIntSkill(stats, "Attack", 1);
        int str = parseIntSkill(stats, "Strength", 1);
        int rng = parseIntSkill(stats, "Ranged", 1);
        int mag = parseIntSkill(stats, "Magic", 1);
        double base = 0.25 * (def + hp + Math.floor(pray / 2.0));
        double melee = 0.325 * (att + str);
        double range = 0.325 * Math.floor((3.0 * rng) / 2.0);
        double mage = 0.325 * Math.floor((3.0 * mag) / 2.0);
        return String.valueOf((int) Math.floor(base + Math.max(melee, Math.max(range, mage))));
    }

    /** Tot 1+retries pogingen; OK wint; anders NOT_FOUND alleen als nooit transient. */
    static FetchResult fetchWithRetries(String displayName, int retries) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return new FetchResult(Status.TRANSIENT_ERROR, null, "lege naam");
        }
        String name = displayName.trim();
        FetchResult last = null;
        int attempts = Math.max(1, retries + 1);
        for (int i = 0; i < attempts; i++) {
            last = fetchOnce(name);
            if (last.status == Status.OK) {
                return last;
            }
            if (last.status == Status.NOT_FOUND) {
                // 404 is stabiel — geen nuttige retry-spam, één keer is genoeg
                return last;
            }
            if (i + 1 < attempts) {
                try {
                    Thread.sleep(700L + i * 400L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return last != null ? last : new FetchResult(Status.TRANSIENT_ERROR, null, "geen resultaat");
    }

    private static FetchResult fetchOnce(String name) {
        int notFound = 0;
        int transientErr = 0;
        String lastDetail = "";
        for (String prefix : LITE_URL_PREFIXES) {
            try {
                BoardHit hit = fetchBoard(name, prefix);
                if (hit.kind == BoardHit.Kind.OK) {
                    return new FetchResult(Status.OK, hit.lines, "ok");
                }
                if (hit.kind == BoardHit.Kind.NOT_FOUND) {
                    notFound++;
                    lastDetail = "HTTP 404";
                } else {
                    transientErr++;
                    lastDetail = hit.detail;
                }
            } catch (Exception e) {
                transientErr++;
                lastDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }
        // Alleen verbieden als we wél 404's zagen en géén transient (API down ≠ ban)
        if (notFound > 0 && transientErr == 0) {
            log.info("hiscore {} → NOT_FOUND ({} boards 404)", name, notFound);
            return new FetchResult(Status.NOT_FOUND, null, "niet op hiscores (" + notFound + "×404)");
        }
        if (notFound > 0 && transientErr > 0) {
            // Gemengd: voorzichtig — liever opnieuw dan false ban
            log.info("hiscore {} → TRANSIENT (404={} err={}) detail={}", name, notFound, transientErr, lastDetail);
            return new FetchResult(Status.TRANSIENT_ERROR, null,
                    "gemengd 404+" + transientErr + " errors: " + lastDetail);
        }
        log.info("hiscore {} → TRANSIENT (err={}) detail={}", name, transientErr, lastDetail);
        return new FetchResult(Status.TRANSIENT_ERROR, null, lastDetail);
    }

    private static final class BoardHit {
        enum Kind { OK, NOT_FOUND, ERROR }

        final Kind kind;
        final List<String> lines;
        final String detail;

        BoardHit(Kind kind, List<String> lines, String detail) {
            this.kind = kind;
            this.lines = lines;
            this.detail = detail != null ? detail : "";
        }
    }

    private static BoardHit fetchBoard(String playerName, String urlPrefix) throws IOException {
        String enc = URLEncoder.encode(playerName, StandardCharsets.UTF_8).replace("+", "%20");
        URL url = new URL(urlPrefix + enc);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) LoneBot-Hiscore/1.0");
        c.setRequestProperty("Accept", "text/plain,*/*");
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setInstanceFollowRedirects(true);
        int code;
        try {
            code = c.getResponseCode();
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            c.disconnect();
            return new BoardHit(BoardHit.Kind.NOT_FOUND, null, "404");
        }
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (in == null) {
            c.disconnect();
            return new BoardHit(BoardHit.Kind.ERROR, null, "HTTP " + code + " geen body");
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line.trim());
            }
        } finally {
            c.disconnect();
        }
        if (code == HttpURLConnection.HTTP_OK && lines.size() >= 16 && looksLikeLiteCsv(lines)) {
            return new BoardHit(BoardHit.Kind.OK, lines, "200");
        }
        if (code == HttpURLConnection.HTTP_OK) {
            return new BoardHit(BoardHit.Kind.ERROR, null, "HTTP 200 maar geen hiscore-CSV");
        }
        // Sommige proxies geven 404-body met andere code — tekst check
        String joined = String.join(" ", lines).toLowerCase();
        if (joined.contains("not found") || joined.contains("does not exist") || joined.contains("no player")) {
            return new BoardHit(BoardHit.Kind.NOT_FOUND, null, "HTTP " + code + " not-found-body");
        }
        return new BoardHit(BoardHit.Kind.ERROR, null, "HTTP " + code);
    }

    private static Map<String, String> linesToSkillMap(List<String> lines) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < LITE_LINE_NAMES.length; i++) {
            String skillName = LITE_LINE_NAMES[i];
            if (i >= lines.size()) {
                out.put(skillName, "—");
            } else {
                out.put(skillName, parseLevelString(lines.get(i), skillName));
            }
        }
        return out;
    }

    private static int parseIntSkill(Map<String, String> stats, String key, int defaultVal) {
        String v = stats.get(key);
        if (v == null || v.equals("—")) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static String parseLevelString(String line, String skillName) {
        if (line == null || line.isEmpty()) {
            return "—";
        }
        String[] p = line.split(",");
        if (p.length < 2) {
            return "—";
        }
        String raw = p[1].trim();
        if ("-1".equals(raw)) {
            return "Hitpoints".equals(skillName) ? "10" : "1";
        }
        return raw;
    }

    private static boolean looksLikeLiteCsv(List<String> lines) {
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            String s = lines.get(i);
            if (s != null && !s.isEmpty() && CSV_LINE.matcher(s.trim()).matches()) {
                return true;
            }
        }
        return false;
    }

    private static int parseLevel(String line) {
        if (line == null || line.isEmpty()) {
            return 0;
        }
        String[] p = line.split(",");
        if (p.length < 2) {
            return 0;
        }
        try {
            return Integer.parseInt(p[1].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int computeCombatLevel(int attack, int defence, int strength, int hitpoints,
            int ranged, int prayer, int magic) {
        double base = 0.25 * (defence + hitpoints + Math.floor(prayer / 2.0));
        double melee = 0.325 * (attack + strength);
        double range = 0.325 * (ranged * 2.0);
        double mage = 0.325 * (magic * 2.0);
        double combat = base + Math.max(melee, Math.max(range, mage));
        int cl = (int) Math.floor(combat);
        return Math.min(126, Math.max(3, cl));
    }
}
