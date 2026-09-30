package net.storm.sdk.movement.pathfinder;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.pathfinder.model.Requirements;
import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Laadt Shortest Path TSV’s van de classpath → {@link TransportLoader}.
 * <p>
 * <b>Default (F2P):</b> {@code f2p_gates.tsv} + F2P stairs/ladders/doors/dungeons uit
 * {@code transports.tsv}. Geen fairy/boten/members — die maakten A* te zwaar (geen pad).
 * <p>
 * Volledige SP-set: JVM {@code -Dlonebot.walk.fullTransports=true}<br>
 * Alleen gates: JVM {@code -Dlonebot.walk.legacyTransports=true}
 * <p>
 * Teleport-spells/items blijven buiten (aparte teleport-laag).
 *
 * @see <a href="https://github.com/Skretzo/shortest-path">Skretzo/shortest-path</a>
 */
final class TransportTsvLoader {

    private static final Logger log = LoggerFactory.getLogger(TransportTsvLoader.class);
    private static final String RES_DIR = "/net/storm/sdk/movement/pathfinder/transports/";
    private static final String LEGACY_PROP = "lonebot.walk.legacyTransports";
    private static final String FULL_PROP = "lonebot.walk.fullTransports";

    /** Alle SP-bestanden — alleen met {@code -Dlonebot.walk.fullTransports=true}. */
    private static final String[] FULL_FILES = {
            "f2p_gates.tsv",
            "transports.tsv",
            "agility_shortcuts.tsv",
            "boats.tsv",
            "canoes.tsv",
            "charter_ships.tsv",
            "ships.tsv",
            "fairy_rings.tsv",
            "gnome_gliders.tsv",
            "hot_air_balloons.tsv",
            "magic_carpets.tsv",
            "magic_mushtrees.tsv",
            "minecarts.tsv",
            "quetzals.tsv",
            "quetzal_whistle.tsv",
            "spirit_trees.tsv",
            "teleportation_boxes.tsv",
            "teleportation_levers.tsv",
            "teleportation_minigames.tsv",
            "teleportation_portals.tsv",
            "teleportation_portals_poh.tsv",
            "wilderness_obelisks.tsv",
            "seasonal_transports.tsv"
    };

    /** Exacte SP-origin tegels; groter radius = zware A*-index. */
    private static final int RADIUS = 1;
    private static final String[] OPEN_CHAT = {"Yes, okay.", "Yes", "Okay", "I'll pay"};
    private static final AtomicBoolean LOADED = new AtomicBoolean();
    /** true = filter transports.tsv tot F2P climb/door/dungeon. */
    private static volatile boolean f2pFilterActive;

    private TransportTsvLoader() {
    }

    static void ensureLoaded() {
        if (!LOADED.compareAndSet(false, true)) {
            return;
        }
        if (Boolean.getBoolean(LEGACY_PROP)) {
            log.warn("[TransportTsv] LEGACY modus — alleen f2p_gates.legacy.tsv");
            BotRuntime.logConsole("[Walk/path] LEGACY transports (f2p_gates only)");
            TransportTsvLoaderLegacy.loadInto(TransportLoader::addCustomTransport);
            return;
        }
        boolean full = Boolean.getBoolean(FULL_PROP);
        f2pFilterActive = !full;
        int n = 0;
        int skipped = 0;
        if (full) {
            for (String file : FULL_FILES) {
                int[] r = loadFile(file);
                n += r[0];
                skipped += r[1];
            }
            log.info("[TransportTsv] FULL loaded {} transports (skipped {})", n, skipped);
            BotRuntime.logConsole("[Walk/path] TSV FULL: " + n + " transports"
                    + " — default F2P: zonder -D" + FULL_PROP);
        } else {
            int[] g = loadFile("f2p_gates.tsv");
            int[] t = loadFile("transports.tsv");
            n = g[0] + t[0];
            skipped = g[1] + t[1];
            log.info("[TransportTsv] F2P loaded {} (gates+stairs/dung, skip {})", n, skipped);
            BotRuntime.logConsole("[Walk/path] TSV F2P: " + n + " transports"
                    + " — full SP: -D" + FULL_PROP + "=true");
        }
    }
    /** @return {added, skipped} */
    private static int[] loadFile(String file) {
        String path = RES_DIR + file;
        try (InputStream in = TransportTsvLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                log.warn("[TransportTsv] ontbreekt {}", path);
                BotRuntime.logConsole("[Walk/path] TSV ontbreekt " + file);
                return new int[]{0, 0};
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parseAndRegister(file, reader);
            }
        } catch (Exception e) {
            log.warn("[TransportTsv] {} : {}", file, e.toString());
            BotRuntime.logConsole("[Walk/path] TSV fout " + file + ": " + e.getClass().getSimpleName());
            return new int[]{0, 0};
        }
    }

    private static int[] parseAndRegister(String file, BufferedReader reader) throws Exception {
        Map<String, Integer> cols = null;
        int added = 0;
        int skipped = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String raw = line.startsWith("#") ? line.substring(1).trim() : line;
            if (cols == null) {
                if (raw.toLowerCase(Locale.ROOT).contains("origin")
                        && raw.toLowerCase(Locale.ROOT).contains("destination")) {
                    cols = headerMap(raw);
                }
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("\t", -1);
            Transport t = toTransport(cells, cols, file);
            if (t != null) {
                TransportLoader.addCustomTransport(t);
                added++;
            } else {
                skipped++;
            }
        }
        if (cols == null) {
            log.warn("[TransportTsv] geen header in {}", file);
        } else {
            log.info("[TransportTsv] {} → +{} (skip {})", file, added, skipped);
        }
        return new int[]{added, skipped};
    }

    private static Map<String, Integer> headerMap(String headerLine) {
        String[] parts = headerLine.split("\t", -1);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < parts.length; i++) {
            String name = parts[i].trim();
            if (!name.isEmpty()) {
                out.put(name, i);
            }
        }
        return out;
    }

    private static String cell(String[] cells, Map<String, Integer> cols, String name) {
        Integer i = cols.get(name);
        if (i == null || i < 0 || i >= cells.length) {
            return "";
        }
        return cells[i].trim();
    }

    private static Transport toTransport(String[] cells, Map<String, Integer> cols, String file) {
        WorldPoint src = parsePoint(cell(cells, cols, "Origin"));
        WorldPoint dest = parsePoint(cell(cells, cols, "Destination"));
        // Fairy/spirit zonder destination = incomplete SP-rij → skip
        if (src == null || dest == null) {
            return null;
        }
        Menu menu = parseMenu(cell(cells, cols, "menuOption menuTarget objectID"));
        if (menu == null) {
            return null;
        }
        if (f2pFilterActive && "transports.tsv".equals(file)
                && !keepF2pTransport(src, dest, menu)) {
            return null;
        }

        Requirements req = buildRequirements(
                cell(cells, cols, "Skills"),
                cell(cells, cols, "Items"),
                cell(cells, cols, "Quests"),
                cell(cells, cols, "Varbits"),
                cell(cells, cols, "VarPlayers"));
        if (req == null) {
            // onparsebare harde lock → niet als open edge in A*
            return null;
        }

        int duration = Math.max(1, parseIntSafe(cell(cells, cols, "Duration"), 1));
        String label = menuLabel(menu);
        boolean open = menu.action.toLowerCase(Locale.ROOT).startsWith("open");
        boolean npc = isNpcAction(menu.action);
        String objName = menu.target.isEmpty() ? null : menu.target;

        if (npc) {
            return TransportLoader.npcTransport(RADIUS, src, dest, menu.objectId, req, menu.action);
        }
        if (open) {
            return TransportLoader.tsvObject(src, dest, RADIUS, menu.objectId, objName,
                    menu.action, label, req, duration, OPEN_CHAT);
        }
        return TransportLoader.tsvObject(src, dest, RADIUS, menu.objectId, objName,
                menu.action, label, req, duration);
    }

    /**
     * F2P-default: dungeon/instance-sprongen + climb/open/enter op F2P-mainland.
     * Geen members agility / wildy-ditch spam.
     */
    private static boolean keepF2pTransport(WorldPoint src, WorldPoint dest, Menu menu) {
        if (GlobalPathfinder.isLikelyInstanceGap(src, dest)) {
            return true;
        }
        if (!isF2pMainland(src) && !isF2pMainland(dest)) {
            return false;
        }
        String a = menu.action != null ? menu.action.toLowerCase(Locale.ROOT) : "";
        String t = menu.target != null ? menu.target.toLowerCase(Locale.ROOT) : "";
        if (t.contains("wilderness") || a.contains("wilderness")) {
            return false;
        }
        if (t.contains("death") && (t.contains("domain") || t.contains("office"))) {
            return false;
        }
        return a.contains("climb")
                || a.contains("open")
                || a.contains("enter")
                || a.contains("pass")
                || a.contains("pay")
                || a.contains("push")
                || a.contains("pull")
                || a.startsWith("go-")
                || a.equals("use");
    }

    /** Port Sarim → Varrock/Edge/Al Kharid (surface) of F2P-dungeon (Y+6400). */
    private static boolean isF2pMainland(WorldPoint p) {
        if (p == null) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        if (x < 2880 || x > 3424) {
            return false;
        }
        // Surface
        if (y >= 3072 && y <= 3520) {
            return true;
        }
        // F2P dungeon / under (Edge, Lumb, …)
        return y >= 3072 + 6400 && y <= 3520 + 6400;
    }

    /**
     * @return empty Requirements ok; {@code null} = skip rij (onveilige/onparsebare lock)
     */
    private static Requirements buildRequirements(String skills, String items, String quests,
                                                    String varbits, String varPlayers) {
        Requirements req = new Requirements();
        if (!parseSkills(skills, req)) {
            return null;
        }
        if (!parseQuests(quests, req)) {
            return null;
        }
        if (!parseItems(items, req)) {
            return null;
        }
        if (!parseVarEquals(varbits, true, req)) {
            return null;
        }
        if (!parseVarEquals(varPlayers, false, req)) {
            return null;
        }
        return req;
    }

    private static boolean parseSkills(String raw, Requirements req) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        // "65 Agility" of "25 Magic;10 Agility"
        for (String part : raw.split("[;,]")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String[] tok = p.split("\\s+");
            if (tok.length < 2) {
                return false;
            }
            int level;
            try {
                level = Integer.parseInt(tok[0]);
            } catch (NumberFormatException e) {
                return false;
            }
            String skillName = String.join("_", Arrays.copyOfRange(tok, 1, tok.length))
                    .toUpperCase(Locale.ROOT);
            Skill skill;
            try {
                skill = Skill.valueOf(skillName);
            } catch (IllegalArgumentException e) {
                return false;
            }
            req.skill(skill, level);
        }
        return true;
    }

    private static boolean parseQuests(String raw, Requirements req) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        for (String part : raw.split("[;,]")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            Quest q = resolveQuest(name);
            if (q == null) {
                // Onbekende quest → niet als open pad (veilig skip)
                return false;
            }
            req.quest(q);
        }
        return true;
    }

    private static Quest resolveQuest(String name) {
        String norm = name.trim().toUpperCase(Locale.ROOT)
                .replace('\'', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", "_");
        try {
            return Quest.valueOf(norm);
        } catch (IllegalArgumentException ignored) {
        }
        // Veelgebruikte aliassen
        if (norm.contains("PRINCE_ALI")) {
            try {
                return Quest.valueOf("PRINCE_ALI_RESCUE");
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Quest q : Quest.values()) {
            if (q.name().equals(norm)) {
                return q;
            }
            String dn = q.name().replace("_", " ");
            if (dn.equalsIgnoreCase(name.trim())) {
                return q;
            }
        }
        return null;
    }

    private static boolean parseItems(String raw, Requirements req) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String u = raw.toUpperCase(Locale.ROOT);
        // Coins / Al Kharid — runtime AlKharidGate
        if (u.contains("COIN") && !u.contains("=") && !u.contains("SLOT")) {
            return true;
        }
        // HEADSLOT / CAPESLOT / complexe AND — skip rij (geen inventaris-emulatie)
        if (u.contains("SLOT") || u.contains("&&") || u.contains("&")) {
            return false;
        }
        // Eenvoudig ITEM=qty of item-id
        for (String part : raw.split("[;,]")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.toUpperCase(Locale.ROOT).contains("COIN")) {
                continue;
            }
            int id = -1;
            int qty = 1;
            if (p.contains("=")) {
                String[] kv = p.split("=", 2);
                try {
                    id = Integer.parseInt(kv[0].trim());
                    qty = Integer.parseInt(kv[1].trim());
                } catch (NumberFormatException e) {
                    return false;
                }
            } else {
                try {
                    id = Integer.parseInt(p);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            final int itemId = id;
            final int need = Math.max(1, qty);
            req.require(() -> {
                try {
                    return Inventory.getCount(itemId) >= need;
                } catch (Throwable t) {
                    return false;
                }
            });
        }
        return true;
    }

    private static boolean parseVarEquals(String raw, boolean varbit, Requirements req) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        for (String part : raw.split("[;,]")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq <= 0) {
                return false;
            }
            try {
                int id = Integer.parseInt(p.substring(0, eq).trim());
                int want = Integer.parseInt(p.substring(eq + 1).trim());
                final int fid = id;
                final int fwant = want;
                final boolean fb = varbit;
                req.require(() -> {
                    try {
                        int v = fb ? Vars.getVarbit(fid) : Vars.getVarp(fid);
                        return v == fwant;
                    } catch (Throwable t) {
                        return false;
                    }
                });
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNpcAction(String action) {
        if (action == null) {
            return false;
        }
        String a = action.toLowerCase(Locale.ROOT);
        return a.equals("talk-to") || a.equals("travel") || a.equals("charter")
                || a.equals("take-boat") || a.equals("pay-fare") || a.equals("pay")
                || a.startsWith("travel-") || a.equals("glider") || a.equals("fly");
    }

    private static String menuLabel(Menu menu) {
        if (menu.target != null && !menu.target.isBlank()) {
            return (menu.action + " " + menu.target).toLowerCase(Locale.ROOT);
        }
        return menu.action.toLowerCase(Locale.ROOT);
    }

    private static int parseIntSafe(String raw, int def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static WorldPoint parsePoint(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] p = raw.trim().split("\\s+");
        if (p.length != 3) {
            return null;
        }
        try {
            return new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Menu parseMenu(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] tok = raw.trim().split("\\s+");
        if (tok.length < 2) {
            return null;
        }
        String last = tok[tok.length - 1];
        int id;
        try {
            id = Integer.parseInt(last);
        } catch (NumberFormatException e) {
            return null;
        }
        String action = tok[0];
        String target = tok.length > 2 ? String.join(" ", Arrays.copyOfRange(tok, 1, tok.length - 1)) : "";
        return new Menu(action, target, id);
    }

    private static final class Menu {
        final String action;
        final String target;
        final int objectId;

        Menu(String action, String target, int objectId) {
            this.action = action;
            this.target = target;
            this.objectId = objectId;
        }
    }
}
