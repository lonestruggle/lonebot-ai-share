package net.storm.sdk.movement.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;
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
import java.util.function.Consumer;

/**
 * Backup van de oude F2P-only loader ({@code f2p_gates.legacy.tsv}).
 * Aanroep via {@code -Dlonebot.walk.legacyTransports=true}.
 */
final class TransportTsvLoaderLegacy {

    private static final Logger log = LoggerFactory.getLogger(TransportTsvLoaderLegacy.class);
    private static final String RES = "/net/storm/sdk/movement/pathfinder/transports/f2p_gates.legacy.tsv";
    private static final int RADIUS = 3;
    private static final String[] OPEN_CHAT = {"Yes, okay.", "Yes", "Okay", "I'll pay"};

    private TransportTsvLoaderLegacy() {
    }

    static void loadInto(Consumer<Transport> sink) {
        int n = 0;
        try (InputStream in = TransportTsvLoaderLegacy.class.getResourceAsStream(RES)) {
            if (in == null) {
                log.warn("[TransportTsv/legacy] ontbreekt {}", RES);
                BotRuntime.logConsole("[Walk/path] LEGACY TSV ontbreekt f2p_gates.legacy.tsv");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                n = parse(reader, sink);
            }
        } catch (Exception e) {
            log.warn("[TransportTsv/legacy] {}", e.toString());
            BotRuntime.logConsole("[Walk/path] LEGACY TSV fout: " + e.getClass().getSimpleName());
            return;
        }
        log.info("[TransportTsv/legacy] loaded {} transports", n);
        BotRuntime.logConsole("[Walk/path] LEGACY TSV: " + n + " F2P gate/stile-kanten");
    }

    private static int parse(BufferedReader reader, Consumer<Transport> sink) throws Exception {
        Map<String, Integer> cols = null;
        int added = 0;
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
            Transport t = toTransport(line.split("\t", -1), cols);
            if (t != null) {
                sink.accept(t);
                added++;
            }
        }
        return added;
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

    private static Transport toTransport(String[] cells, Map<String, Integer> cols) {
        WorldPoint src = parsePoint(cell(cells, cols, "Origin"));
        WorldPoint dest = parsePoint(cell(cells, cols, "Destination"));
        if (src == null || dest == null) {
            return null;
        }
        if (!cell(cells, cols, "Skills").isEmpty() || !cell(cells, cols, "Quests").isEmpty()) {
            return null;
        }
        if (!cell(cells, cols, "Varbits").isEmpty() || !cell(cells, cols, "VarPlayers").isEmpty()) {
            return null;
        }
        String items = cell(cells, cols, "Items");
        if (!items.isEmpty() && !items.toUpperCase(Locale.ROOT).contains("COIN")) {
            return null;
        }
        Menu menu = parseMenu(cell(cells, cols, "menuOption menuTarget objectID"));
        if (menu == null) {
            return null;
        }
        boolean open = menu.action.toLowerCase(Locale.ROOT).startsWith("open");
        String name = menu.target.isEmpty() ? "object" : menu.target.toLowerCase(Locale.ROOT);
        if (open) {
            return TransportLoader.tsvObject(src, dest, RADIUS, menu.objectId, menu.target,
                    menu.action, name, OPEN_CHAT);
        }
        return TransportLoader.tsvObject(src, dest, RADIUS, menu.objectId, menu.target, menu.action, name);
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
        try {
            int id = Integer.parseInt(tok[tok.length - 1]);
            String action = tok[0];
            String target = tok.length > 2 ? String.join(" ", Arrays.copyOfRange(tok, 1, tok.length - 1)) : "";
            return new Menu(action, target, id);
        } catch (NumberFormatException e) {
            return null;
        }
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
