package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.lonebot.HoverCaptureLog;
import net.runelite.client.util.Text;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.LoneBotPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Achtergrond-scan: deuren / hekken / stiles / ladders / trappen terwijl je erlangs loopt.
 * Bestand: {@code ~/.lonebot/walk-obstacle-sightings.tsv} en {@code walk-obstacle-transports.tsv}.
 */
public final class WalkObstacleScan {

    private static final int SCAN_R = 3;
    private static final String TRANSPORT_HEADER =
            "Origin\tDestination\tmenuOption menuTarget objectID\tSkills\tItems\tQuests\tVarbits\tVarPlayers\tDuration\tDisplay info";
    private static final String SIGHT_HEADER = "oid\tname\ttile\tactions\tplayerWhenSeen";

    private static final Map<String, String> SIGHTINGS = new LinkedHashMap<>();
    private static final Map<String, String> TRANSPORTS = new LinkedHashMap<>();
    private static volatile boolean loaded;
    private static volatile WorldPoint lastTile;
    private static volatile String lastLog = "";
    private static volatile long lastLogMs;

    private WalkObstacleScan() {
    }

    public static void tick(Client client, boolean enabled) {
        if (!enabled || client == null || client.getGameState() != GameState.LOGGED_IN) {
            lastTile = null;
            return;
        }
        Player me = client.getLocalPlayer();
        if (me == null || me.getWorldLocation() == null) {
            return;
        }
        ensureLoaded();
        WorldPoint now = me.getWorldLocation();
        List<Hit> nearby = scanNearby(client, now);
        for (Hit h : nearby) {
            String sk = h.id + "@" + fmt(h.tile);
            if (!SIGHTINGS.containsKey(sk)) {
                SIGHTINGS.put(sk, h.id + "\t" + h.name + "\t" + fmt(h.tile) + "\t" + h.actions + "\t" + fmt(now));
                writeSightings();
                logOnce("zien " + h.name + " oid=" + h.id + " @" + fmt(h.tile)
                        + " act=" + h.actions + " (ik " + fmt(now) + ")");
                HoverCaptureLog.add("walk-scan",
                        h.name + " oid=" + h.id + " @" + fmt(h.tile) + " " + h.actions, sk);
            }
        }
        WorldPoint prev = lastTile;
        lastTile = now;
        if (prev == null || prev.equals(now)) {
            return;
        }
        boolean step = prev.getPlane() != now.getPlane() || prev.distanceTo(now) == 1;
        if (!step) {
            return;
        }
        for (Hit h : nearby) {
            if (!isCrossing(prev, now, h.tile, h.kind)) {
                continue;
            }
            String action = pickAction(h, prev, now);
            if (action == null) {
                continue;
            }
            if (addTransport(prev, now, h, action) && prev.getPlane() == now.getPlane()
                    && ("Open".equals(action) || "Climb-over".equals(action))) {
                addTransport(now, prev, h, action);
            }
        }
    }

    private static boolean addTransport(WorldPoint from, WorldPoint to, Hit h, String action) {
        String key = fmtWp(from) + ">" + fmtWp(to) + ">" + h.id + ">" + action;
        if (TRANSPORTS.containsKey(key)) {
            return false;
        }
        String menu = action + " " + h.name + " " + h.id;
        String row = fmtWp(from) + "\t" + fmtWp(to) + "\t" + menu + "\t\t\t\t\t\t1\t" + h.name;
        TRANSPORTS.put(key, row);
        writeTransports();
        logOnce("crossing " + menu + " " + fmt(from) + " → " + fmt(to));
        HoverCaptureLog.add("walk-scan", menu + " " + fmt(from) + " → " + fmt(to), key);
        return true;
    }

    private enum Kind {
        DOOR_GATE, STILE, CLIMB, FENCE
    }

    private static final class Hit {
        final int id;
        final String name;
        final WorldPoint tile;
        final String actions;
        final Kind kind;

        Hit(int id, String name, WorldPoint tile, String actions, Kind kind) {
            this.id = id;
            this.name = name;
            this.tile = tile;
            this.actions = actions;
            this.kind = kind;
        }
    }

    private static List<Hit> scanNearby(Client client, WorldPoint me) {
        List<Hit> out = new ArrayList<>();
        Tile[][][] tiles = client.getScene() != null ? client.getScene().getTiles() : null;
        if (tiles == null || me.getPlane() < 0 || me.getPlane() >= tiles.length) {
            return out;
        }
        Tile[][] plane = tiles[me.getPlane()];
        if (plane == null) {
            return out;
        }
        for (Tile[] row : plane) {
            if (row == null) {
                continue;
            }
            for (Tile tile : row) {
                if (tile == null || tile.getWorldLocation() == null) {
                    continue;
                }
                if (tile.getWorldLocation().distanceTo(me) > SCAN_R) {
                    continue;
                }
                collectTile(client, tile, out);
            }
        }
        return out;
    }

    private static void collectTile(Client client, Tile tile, List<Hit> out) {
        List<TileObject> objs = new ArrayList<>(6);
        if (tile.getGameObjects() != null) {
            for (GameObject go : tile.getGameObjects()) {
                if (go != null) {
                    objs.add(go);
                }
            }
        }
        if (tile.getWallObject() != null) {
            objs.add(tile.getWallObject());
        }
        if (tile.getDecorativeObject() != null) {
            objs.add(tile.getDecorativeObject());
        }
        if (tile.getGroundObject() != null) {
            objs.add(tile.getGroundObject());
        }
        for (TileObject obj : objs) {
            Hit h = classify(client, obj, tile.getWorldLocation());
            if (h != null) {
                out.add(h);
            }
        }
    }

    private static Hit classify(Client client, TileObject obj, WorldPoint tile) {
        if (obj == null || tile == null) {
            return null;
        }
        int id = obj.getId();
        ObjectComposition comp;
        try {
            comp = client.getObjectDefinition(id);
        } catch (Throwable t) {
            return null;
        }
        if (comp == null) {
            return null;
        }
        String name = comp.getName() != null ? Text.removeTags(comp.getName()) : "";
        if (name.isBlank() || "null".equalsIgnoreCase(name)) {
            return null;
        }
        String low = name.toLowerCase(Locale.ROOT);
        String actions = joinActions(comp.getActions());
        Kind kind = kindOf(low, actions);
        if (kind == null) {
            return null;
        }
        return new Hit(id, name, tile, actions, kind);
    }

    private static Kind kindOf(String nameLow, String actions) {
        String act = actions.toLowerCase(Locale.ROOT);
        if (nameLow.contains("stile") || act.contains("climb-over")) {
            return Kind.STILE;
        }
        if (nameLow.contains("ladder") || nameLow.contains("stair")
                || act.contains("climb-up") || act.contains("climb-down")) {
            return Kind.CLIMB;
        }
        if (nameLow.contains("door") || nameLow.contains("gate") || nameLow.contains("portcullis")
                || act.contains("open")) {
            return Kind.DOOR_GATE;
        }
        if (nameLow.contains("fence")) {
            return Kind.FENCE;
        }
        return null;
    }

    private static boolean isCrossing(WorldPoint from, WorldPoint to, WorldPoint obj, Kind kind) {
        if (from == null || to == null || obj == null) {
            return false;
        }
        if (from.getPlane() != to.getPlane()) {
            return kind == Kind.CLIMB && from.distanceTo(obj) <= 1;
        }
        int dOld = from.distanceTo(obj);
        int dNew = to.distanceTo(obj);
        if (dOld <= 1 && dNew == 0) {
            return true;
        }
        if (dNew <= 1 && dOld == 0) {
            return true;
        }
        if (dOld <= 1 && dNew <= 1) {
            int dot = (from.getX() - obj.getX()) * (to.getX() - obj.getX())
                    + (from.getY() - obj.getY()) * (to.getY() - obj.getY());
            return dot <= 0;
        }
        return false;
    }

    private static String pickAction(Hit h, WorldPoint from, WorldPoint to) {
        String act = h.actions.toLowerCase(Locale.ROOT);
        if (from.getPlane() < to.getPlane() && act.contains("climb-up")) {
            return "Climb-up";
        }
        if (from.getPlane() > to.getPlane() && act.contains("climb-down")) {
            return "Climb-down";
        }
        if (h.kind == Kind.STILE && act.contains("climb-over")) {
            return "Climb-over";
        }
        if (act.contains("open-door")) {
            return "Open-door";
        }
        if (act.contains("open")) {
            return "Open";
        }
        if (act.contains("climb-over")) {
            return "Climb-over";
        }
        if (act.contains("climb-up")) {
            return "Climb-up";
        }
        if (act.contains("climb-down")) {
            return "Climb-down";
        }
        return null;
    }

    private static String joinActions(String[] actions) {
        if (actions == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String a : actions) {
            if (a == null || a.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(a);
        }
        return sb.toString();
    }

    private static String fmt(WorldPoint p) {
        return p == null ? "-" : (p.getX() + "," + p.getY() + "," + p.getPlane());
    }

    private static String fmtWp(WorldPoint p) {
        return p.getX() + " " + p.getY() + " " + p.getPlane();
    }

    private static void logOnce(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1500L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Walk/scan] " + msg);
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        LoneBotPaths.ensureDirs();
        loadSightings(LoneBotPaths.walkObstacleSightingsFile().toPath());
        loadTransports(LoneBotPaths.walkObstacleTransportsFile().toPath());
        if (!SIGHTINGS.isEmpty() || !TRANSPORTS.isEmpty()) {
            BotRuntime.logConsole("[Walk/scan] geladen zichtingen=" + SIGHTINGS.size()
                    + " crossings=" + TRANSPORTS.size());
        }
    }

    private static void loadSightings(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("oid\t")) {
                    continue;
                }
                String[] c = line.split("\t", -1);
                if (c.length >= 3) {
                    SIGHTINGS.put(c[0].trim() + "@" + c[2].trim(), line);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void loadTransports(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("Origin\t")) {
                    continue;
                }
                String[] c = line.split("\t", -1);
                if (c.length < 3) {
                    continue;
                }
                String menu = c[2].trim();
                String[] tok = menu.split("\\s+");
                String action = tok.length > 0 ? tok[0] : "";
                String oid = tok.length > 0 ? tok[tok.length - 1] : "";
                TRANSPORTS.put(c[0].trim() + ">" + c[1].trim() + ">" + oid + ">" + action, line);
            }
        } catch (IOException ignored) {
        }
    }

    private static synchronized void writeSightings() {
        writeTsv(LoneBotPaths.walkObstacleSightingsFile().toPath(),
                "# Zichtingen — langs deuren/hekken/ladders/trappen gelopen.",
                SIGHT_HEADER, SIGHTINGS);
    }

    private static synchronized void writeTransports() {
        writeTsv(LoneBotPaths.walkObstacleTransportsFile().toPath(),
                "# Crossings — Shortest Path TSV. Later mergen in f2p_gates.tsv.",
                TRANSPORT_HEADER, TRANSPORTS);
    }

    private static void writeTsv(Path path, String comment, String header, Map<String, String> rows) {
        try {
            LoneBotPaths.ensureDirs();
            List<String> out = new ArrayList<>();
            out.add(comment);
            out.add(header);
            out.addAll(rows.values());
            Files.write(path, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            BotRuntime.logConsole("[Walk/scan] schrijven mislukt: " + e.getClass().getSimpleName());
        }
    }
}
