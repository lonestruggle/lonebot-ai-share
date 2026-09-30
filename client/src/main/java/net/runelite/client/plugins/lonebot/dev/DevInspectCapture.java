package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.lonebot.HoverCaptureLog;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.runelite.client.util.Text;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.items.Bank;
import net.storm.sdk.movement.Reachable;
import net.storm.sdk.widgets.Dialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture-log dumps: speler-state, menu, collision-tegel, open interfaces.
 */
public final class DevInspectCapture {

    private DevInspectCapture() {
    }

    public static boolean add(LoneBotConfig config, String kind, String line, String detail) {
        if (line == null || line.isBlank()) {
            return false;
        }
        if (config != null && config.captureSkipDuplicates()
                && HoverCaptureLog.wouldSkipDuplicate(kind, line, detail)) {
            return false;
        }
        return HoverCaptureLog.add(kind, line, detail) != null;
    }

    public static void addPlayer(Client client, LoneBotConfig config) {
        Result r = formatPlayer(client);
        if (r == null) {
            return;
        }
        if (add(config, r.kind, r.line, r.detail)) {
            BotRuntime.logConsole("[Inspect/player] " + r.line);
        }
    }

    public static void addMenu(Client client, LoneBotConfig config) {
        MenuEntry[] entries = null;
        try {
            entries = client != null ? client.getMenuEntries() : null;
        } catch (Throwable ignored) {
        }
        addMenu(config, entries);
    }

    public static void addMenu(LoneBotConfig config, MenuEntry[] entries) {
        Result r = formatMenu(entries);
        if (r == null) {
            return;
        }
        if (add(config, r.kind, r.line, r.detail)) {
            BotRuntime.logConsole("[Inspect/menu] " + r.line);
        }
    }

    public static void addCollision(Client client, LoneBotConfig config, WorldPoint tile) {
        Result r = formatCollision(client, tile);
        if (r == null) {
            return;
        }
        if (add(config, r.kind, r.line, r.detail)) {
            BotRuntime.logConsole("[Inspect/col] " + r.line);
        }
    }

    public static void addOpenInterfaces(Client client, LoneBotConfig config) {
        Result r = formatOpenInterfaces(client);
        if (r == null) {
            return;
        }
        if (add(config, r.kind, r.line, r.detail)) {
            BotRuntime.logConsole("[Inspect/iface] " + r.line);
        }
    }

    public static String collisionBlock(Client client, WorldPoint tile) {
        Result r = formatCollision(client, tile);
        return r != null ? r.detail : "";
    }

    public static Result formatPlayer(Client client) {
        if (client == null) {
            return null;
        }
        Player me;
        try {
            me = client.getLocalPlayer();
        } catch (Throwable t) {
            return null;
        }
        if (me == null) {
            return new Result("player", "speler: geen", "kind: player\ngeen local player");
        }
        WorldPoint wp = me.getWorldLocation();
        String coords = wp != null ? wp.getX() + "," + wp.getY() + "," + wp.getPlane() : "?";
        int anim = safeInt(me::getAnimation, -1);
        int pose = safeInt(me::getPoseAnimation, -1);
        int idle = safeInt(me::getIdlePoseAnimation, -1);
        int gfx = safeInt(me::getGraphic, -1);
        Actor target = null;
        try {
            target = me.getInteracting();
        } catch (Throwable ignored) {
        }
        String interacting = formatActor(target);
        WorldPoint dest = destinationWorld(client);
        String destStr = dest != null ? dest.getX() + "," + dest.getY() + "," + dest.getPlane() : "geen";
        boolean moving = dest != null || (anim != -1 && anim != idle && anim != pose);
        String line = "Player @" + coords + " anim=" + anim + " dest=" + destStr;
        String detail = "kind: player\n"
                + "tile: " + coords + "\n"
                + "anim: " + anim + "\n"
                + "pose: " + pose + "\n"
                + "idle-pose: " + idle + "\n"
                + "graphic: " + gfx + "\n"
                + "interacting: " + interacting + "\n"
                + "destination: " + destStr + "\n"
                + "moving?: " + moving + "\n"
                + "WorldPoint(" + (wp != null ? wp.getX() + ", " + wp.getY() + ", " + wp.getPlane() : "?, ?, ?") + ")";
        return new Result("player", line, detail);
    }

    public static Result formatMenu(MenuEntry[] entries) {
        if (entries == null || entries.length == 0) {
            return new Result("menu", "menu: leeg", "kind: menu\n(geen entries — open rechtsklik of hover een doel)");
        }
        List<String> rows = new ArrayList<>();
        int game = 0;
        for (int i = entries.length - 1; i >= 0; i--) {
            MenuEntry e = entries[i];
            if (e == null) {
                continue;
            }
            String opt = clean(e.getOption());
            if (opt.equalsIgnoreCase("Cancel")) {
                continue;
            }
            MenuAction type = null;
            try {
                type = e.getType();
            } catch (Throwable ignored) {
            }
            if (type != null && type.name().startsWith("RUNELITE")) {
                continue;
            }
            String tgt = clean(e.getTarget());
            int id = 0;
            int p0 = 0;
            int p1 = 0;
            int item = -1;
            try {
                id = e.getIdentifier();
                p0 = e.getParam0();
                p1 = e.getParam1();
                item = e.getItemId();
            } catch (Throwable ignored) {
            }
            String typeName = type != null ? type.name() : "?";
            String row = opt + (tgt.isEmpty() ? "" : " " + tgt)
                    + " | " + typeName + " id=" + id + " p0=" + p0 + " p1=" + p1
                    + (item > 0 ? " item=" + item : "");
            rows.add(row);
            game++;
        }
        if (rows.isEmpty()) {
            return new Result("menu", "menu: alleen plugin-entries",
                    "kind: menu\n(geen game-acties — hover NPC/object of rechtsklik in-game)");
        }
        String line = "menu: " + game + " actie(s) — " + firstOption(rows);
        StringBuilder detail = new StringBuilder(80 + rows.size() * 48);
        detail.append("kind: menu\n");
        int n = 1;
        for (String row : rows) {
            detail.append(n++).append(". ").append(row).append('\n');
        }
        return new Result("menu", line, detail.toString().trim());
    }

    public static Result formatCollision(Client client, WorldPoint tile) {
        if (tile == null) {
            return null;
        }
        String coords = tile.getX() + "," + tile.getY() + "," + tile.getPlane();
        int flags = flagsAt(client, tile);
        List<String> bits = decodeFlags(flags);
        boolean walkable = flags >= 0 && (flags & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
        Player me = client != null ? client.getLocalPlayer() : null;
        WorldPoint meWp = me != null ? me.getWorldLocation() : null;
        int dx = 0;
        int dy = 0;
        int cheb = -1;
        boolean cardinal = false;
        if (meWp != null && meWp.getPlane() == tile.getPlane()) {
            dx = tile.getX() - meWp.getX();
            dy = tile.getY() - meWp.getY();
            cheb = Math.max(Math.abs(dx), Math.abs(dy));
            cardinal = (Math.abs(dx) + Math.abs(dy)) == 1;
        }
        String wall = wallObjectLine(client, tile);
        boolean los = false;
        try {
            los = meWp != null && Reachable.hasLineOfSight(meWp, tile);
        } catch (Throwable ignored) {
        }
        String why;
        if (flags < 0) {
            why = "tegel niet in scene";
        } else if (!walkable) {
            why = "FULL blocked — niet loopbaar";
        } else if (cheb == 0) {
            why = "je staat erop";
        } else if (cardinal) {
            why = "cardinaal ernaast (0 walk-steps om te interacten)";
        } else if (cheb == 1) {
            why = "diagonaal ernaast (niet gratis — eerst cardinaal staan)";
        } else {
            why = "niet ernaast (chebyshev " + cheb + ")";
        }
        String line = "collision @" + coords + " " + (walkable ? "walkable" : "BLOCK") + " — " + why;
        String detail = "kind: collision\n"
                + "tile: " + coords + "\n"
                + "WorldPoint(" + tile.getX() + ", " + tile.getY() + ", " + tile.getPlane() + ")\n"
                + "flags: " + (flags < 0 ? "n/a" : "0x" + Integer.toHexString(flags))
                + (bits.isEmpty() ? "" : " (" + String.join(", ", bits) + ")") + "\n"
                + "walkable: " + walkable + "\n"
                + "from-player: dx=" + dx + " dy=" + dy + " chebyshev=" + cheb
                + " cardinal=" + cardinal + "\n"
                + "LOS: " + los + "\n"
                + "wall-object: " + wall + "\n"
                + "waarom: " + why;
        return new Result("collision", line, detail);
    }

    public static Result formatOpenInterfaces(Client client) {
        if (client == null) {
            return null;
        }
        int top = -1;
        try {
            top = client.getTopLevelInterfaceId();
        } catch (Throwable ignored) {
        }
        boolean dialog = false;
        boolean bank = false;
        try {
            dialog = Dialog.isOpen();
        } catch (Throwable ignored) {
        }
        try {
            bank = Bank.isOpen();
        } catch (Throwable ignored) {
        }
        List<String> rows = new ArrayList<>();
        Widget[] roots = null;
        try {
            roots = client.getWidgetRoots();
        } catch (Throwable ignored) {
        }
        if (roots != null) {
            for (Widget w : roots) {
                if (w == null) {
                    continue;
                }
                try {
                    if (w.isHidden()) {
                        continue;
                    }
                } catch (Throwable t) {
                    continue;
                }
                int packed = 0;
                try {
                    packed = w.getId();
                } catch (Throwable ignored) {
                }
                int group = packed >>> 16;
                int child = packed & 0xFFFF;
                String name = clean(safeStr(w::getName));
                String text = clean(safeStr(w::getText));
                if (text.length() > 48) {
                    text = text.substring(0, 48) + "…";
                }
                String row = "g" + group + "c" + child
                        + (name.isEmpty() ? "" : " " + name)
                        + (text.isEmpty() ? "" : " \"" + text + "\"");
                rows.add(row);
                if (rows.size() >= 40) {
                    rows.add("… (afgekapt)");
                    break;
                }
            }
        }
        String line = "ifaces top=" + top + " open=" + rows.size()
                + (dialog ? " dialog" : "")
                + (bank ? " bank" : "");
        StringBuilder detail = new StringBuilder(120);
        detail.append("kind: iface\n");
        detail.append("toplevel: ").append(top).append('\n');
        detail.append("dialog: ").append(dialog).append('\n');
        detail.append("bank: ").append(bank).append('\n');
        if (rows.isEmpty()) {
            detail.append("(geen zichtbare widget-roots)");
        } else {
            for (String row : rows) {
                detail.append("  ").append(row).append('\n');
            }
        }
        return new Result("iface", line, detail.toString().trim());
    }

    public static final class Result {
        public final String kind;
        public final String line;
        public final String detail;

        Result(String kind, String line, String detail) {
            this.kind = kind;
            this.line = line;
            this.detail = detail;
        }
    }

    private static String firstOption(List<String> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        String s = rows.get(0);
        int cut = s.indexOf(" | ");
        return cut > 0 ? s.substring(0, cut) : s;
    }

    private static String formatActor(Actor a) {
        if (a == null) {
            return "geen";
        }
        String name = clean(a.getName());
        if (a instanceof NPC) {
            NPC n = (NPC) a;
            return (name.isEmpty() ? "NPC" : name) + " id=" + n.getId() + " idx=" + n.getIndex();
        }
        if (a instanceof Player) {
            return "player " + (name.isEmpty() ? "?" : name);
        }
        return name.isEmpty() ? a.getClass().getSimpleName() : name;
    }

    private static WorldPoint destinationWorld(Client client) {
        try {
            LocalPoint lp = client.getLocalDestinationLocation();
            if (lp == null) {
                return null;
            }
            return WorldPoint.fromLocal(client, lp);
        } catch (Throwable t) {
            return null;
        }
    }

    static int flagsAt(Client client, WorldPoint wp) {
        if (client == null || wp == null) {
            return -1;
        }
        try {
            CollisionData[] maps = client.getCollisionMaps();
            if (maps == null || wp.getPlane() < 0 || wp.getPlane() >= maps.length) {
                return -1;
            }
            CollisionData map = maps[wp.getPlane()];
            if (map == null) {
                return -1;
            }
            LocalPoint lp = LocalPoint.fromWorld(client, wp);
            if (lp == null) {
                return -1;
            }
            int[][] grid = map.getFlags();
            if (grid == null) {
                return -1;
            }
            int sx = lp.getSceneX();
            int sy = lp.getSceneY();
            if (sx < 0 || sy < 0 || sx >= grid.length || sy >= grid[sx].length) {
                return -1;
            }
            return grid[sx][sy];
        } catch (Throwable t) {
            return -1;
        }
    }

    private static List<String> decodeFlags(int flags) {
        List<String> bits = new ArrayList<>();
        if (flags < 0) {
            return bits;
        }
        if ((flags & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0) {
            bits.add("FULL");
        }
        if ((flags & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0) {
            bits.add("N");
        }
        if ((flags & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) {
            bits.add("E");
        }
        if ((flags & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0) {
            bits.add("S");
        }
        if ((flags & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) {
            bits.add("W");
        }
        if ((flags & CollisionDataFlag.BLOCK_MOVEMENT_OBJECT) != 0) {
            bits.add("OBJECT");
        }
        if ((flags & CollisionDataFlag.BLOCK_MOVEMENT_FLOOR) != 0) {
            bits.add("FLOOR");
        }
        if ((flags & CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION) != 0) {
            bits.add("DECO");
        }
        return bits;
    }

    private static String wallObjectLine(Client client, WorldPoint wp) {
        Tile tile = tileAt(client, wp);
        if (tile == null) {
            return "n/a";
        }
        WallObject wall = tile.getWallObject();
        if (wall == null) {
            return "geen";
        }
        String name = "?";
        try {
            if (client.getObjectDefinition(wall.getId()) != null) {
                name = clean(client.getObjectDefinition(wall.getId()).getName());
            }
        } catch (Throwable ignored) {
        }
        return name + " #" + wall.getId();
    }

    private static Tile tileAt(Client client, WorldPoint wp) {
        if (client == null || wp == null || client.getScene() == null) {
            return null;
        }
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) {
            return null;
        }
        Tile[][][] tiles = client.getScene().getTiles();
        int z = wp.getPlane();
        if (tiles == null || z < 0 || z >= tiles.length) {
            return null;
        }
        int sx = lp.getSceneX();
        int sy = lp.getSceneY();
        if (sx < 0 || sy < 0 || sx >= tiles[z].length || sy >= tiles[z][sx].length) {
            return null;
        }
        return tiles[z][sx][sy];
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return Text.removeTags(s).replace('\n', ' ').trim();
    }

    private static int safeInt(IntFn fn, int fallback) {
        try {
            return fn.get();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String safeStr(StrFn fn) {
        try {
            String s = fn.get();
            return s != null ? s : "";
        } catch (Throwable t) {
            return "";
        }
    }

    @FunctionalInterface
    private interface IntFn {
        int get();
    }

    @FunctionalInterface
    private interface StrFn {
        String get();
    }
}
