package net.runelite.client.plugins.lonebot;

import com.lonebot.example.woodcutter.WcCenters;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.movement.WorldWalker;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.tiles.NoWalkZones;
import net.storm.sdk.walls.RoomScan;
import net.storm.sdk.walls.RoomScanner;
import net.storm.sdk.walls.WallDoorCaptureState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Rechtermuisklik: Capture / Get walls / excluded tiles / WC centers (CombatBot-stijl).
 */
@Singleton
public class TileMarkerMenuListener {

    private static final Logger log = LoggerFactory.getLogger(TileMarkerMenuListener.class);
    private static final String WC_HEX = "32c832";

    private final Client client;
    private final ConfigManager configManager;
    private final LoneBotConfig config;

    @Inject
    public TileMarkerMenuListener(Client client, ConfigManager configManager, LoneBotConfig config) {
        this.client = client;
        this.configManager = configManager;
        this.config = config;
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        if (client == null) {
            return;
        }

        WorldPoint mapTile = WorldMapWalkHelper.fromMouse(client);
        if (mapTile != null) {
            addWorldWalkEntries(mapTile, true);
            return;
        }

        Tile hovered = client.getSelectedSceneTile();
        if (hovered == null || hovered.getWorldLocation() == null) {
            if (WorldWalker.isActive()) {
                addCancelWalkOnly();
            }
            return;
        }
        final WorldPoint tilePoint = hovered.getWorldLocation();
        boolean excluded = ExcludedTiles.isExcluded(tilePoint);

        MenuEntry[] existing = client.getMenuEntries();
        List<MenuEntry> gameEntries = new ArrayList<>();
        MenuEntry cancelEntry = null;
        for (MenuEntry entry : existing) {
            if (entry.getOption() != null && entry.getOption().equalsIgnoreCase("Cancel")) {
                cancelEntry = entry;
            } else {
                gameEntries.add(entry);
            }
        }

        List<MenuEntry> custom = new ArrayList<>();

        MenuEntry walkHere = client.createMenuEntry(-1)
                .setOption("Walk here")
                .setTarget("<col=ffcc44>Pathfind " + tilePoint.getX() + "," + tilePoint.getY() + "</col>")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> WorldWalker.go(tilePoint));
        custom.add(walkHere);
        if (WorldWalker.isActive()) {
            MenuEntry cancel = client.createMenuEntry(-1)
                    .setOption("Cancel walk")
                    .setTarget("<col=ff6666>World walker</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> WorldWalker.cancel());
            custom.add(cancel);
        }

        boolean captureOn = config == null || config.captureLogEnabled();
        if (captureOn) {
            MenuEntry addCapture = client.createMenuEntry(-1)
                    .setOption("Voeg toe aan capture-log")
                    .setTarget("<col=00ff88>Capture (coords)</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> addCoordsCapture(tilePoint, false));
            custom.add(addCapture);
        }

        final WorldPoint wallsTile = tilePoint;
        MenuEntry walls = client.createMenuEntry(-1)
                .setOption("Get walls")
                .setTarget("<col=ffaa44>Muren & deuren</col>")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> runWallsScan(wallsTile, null));
        custom.add(walls);

        if (captureOn) {
            MenuEntry dumpMenu = client.createMenuEntry(-1)
                    .setOption("Dump menu → capture")
                    .setTarget("<col=88ddff>opcodes</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> net.runelite.client.plugins.lonebot.dev.DevInspectCapture.addMenu(
                            config, gameEntries.toArray(new MenuEntry[0])));
            custom.add(dumpMenu);
        }

        CaptureMenuNpcHelper.NpcHit npcHit = CaptureMenuNpcHelper.findNpcInMenu(client);
        if (npcHit != null) {
            final CaptureMenuNpcHelper.NpcHit npc = npcHit;
            MenuEntry wallsNpc = client.createMenuEntry(-1)
                    .setOption("Get walls (NPC)")
                    .setTarget("<col=ffaa44>" + npc.name + "</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> runWallsScan(npc.tile, npc));
            custom.add(wallsNpc);
        }

        if (config != null && config.wcCentersMenuEnabled()) {
            addWcCenterEntries(custom, tilePoint);
        }
        if (config != null) {
            for (AreaCenters.Skill skill : AreaCenters.Skill.values()) {
                if (LoneBotBootstrapPlugin.isAreaCentersMenuEnabled(skill)) {
                    addAreaCenterEntries(custom, tilePoint, skill);
                }
            }
        }

        if (excluded) {
            MenuEntry remove = client.createMenuEntry(-1)
                    .setOption("Verwijder markering")
                    .setTarget("<col=ff4444>Excluded tile</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        ExcludedTiles.remove(tilePoint);
                        persist();
                        log.info("[Tiles] removed excluded {}", tilePoint);
                    });
            custom.add(remove);
        } else {
            MenuEntry mark = client.createMenuEntry(-1)
                    .setOption("Markeer tile (Excluded)")
                    .setTarget("<col=ff4444>Bot negeert deze tile</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        ExcludedTiles.add(tilePoint);
                        persist();
                        log.info("[Tiles] excluded {}", tilePoint);
                    });
            custom.add(mark);
        }

        List<MenuEntry> finalEntries = new ArrayList<>();
        if (cancelEntry != null) {
            finalEntries.add(cancelEntry);
        }
        finalEntries.addAll(custom);
        finalEntries.addAll(gameEntries);
        client.setMenuEntries(finalEntries.toArray(new MenuEntry[0]));
    }

    private void addWorldWalkEntries(WorldPoint tile, boolean fromMap) {
        MenuEntry[] existing = client.getMenuEntries();
        List<MenuEntry> gameEntries = new ArrayList<>();
        MenuEntry cancelEntry = null;
        for (MenuEntry entry : existing) {
            String opt = entry.getOption();
            if (opt != null && opt.equalsIgnoreCase("Cancel")) {
                cancelEntry = entry;
            } else if (fromMap && opt != null && opt.equalsIgnoreCase("Walk here")) {
                continue;
            } else {
                gameEntries.add(entry);
            }
        }
        List<MenuEntry> custom = new ArrayList<>();
        String where = fromMap ? "Pathfind map" : "Pathfind";
        custom.add(client.createMenuEntry(-1)
                .setOption("Walk here")
                .setTarget("<col=ffcc44>" + where + " " + tile.getX() + "," + tile.getY() + "</col>")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> WorldWalker.go(tile)));
        if (WorldWalker.isActive()) {
            custom.add(client.createMenuEntry(-1)
                    .setOption("Cancel walk")
                    .setTarget("<col=ff6666>World walker</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> WorldWalker.cancel()));
        }
        List<MenuEntry> finalEntries = new ArrayList<>();
        if (cancelEntry != null) {
            finalEntries.add(cancelEntry);
        }
        finalEntries.addAll(custom);
        finalEntries.addAll(gameEntries);
        client.setMenuEntries(finalEntries.toArray(new MenuEntry[0]));
    }

    private void addCancelWalkOnly() {
        client.createMenuEntry(-1)
                .setOption("Cancel walk")
                .setTarget("<col=ff6666>World walker</col>")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> WorldWalker.cancel());
    }

    private void addWcCenterEntries(List<MenuEntry> custom, WorldPoint menuTile) {
        String blob = currentWcBlob();
        WcCenters.Center nearest = WcCenters.findNearest(blob, menuTile);
        boolean nearby = WcCenters.isNearbyForEdit(nearest, menuTile);
        int rShow = nearest != null ? nearest.radius : WcCenters.DEFAULT_RADIUS;

        custom.add(client.createMenuEntry(-1)
                .setOption("Voeg center toe (WC)")
                .setTarget("<col=" + WC_HEX + ">Area center</col>")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> {
                    String auto = WcCenters.autoName(menuTile);
                    String next = WcCenters.addCenter(currentWcBlob(), menuTile, WcCenters.DEFAULT_RADIUS, auto);
                    LoneBotBootstrapPlugin.persistWcCenters(next);
                    BotRuntime.logConsole("WC center + " + auto + " r=" + WcCenters.DEFAULT_RADIUS);
                    log.info("[WC centers] add {} name={}", menuTile, auto);
                }));

        if (nearby) {
            custom.add(client.createMenuEntry(-1)
                    .setOption("Radius + (WC) [r=" + rShow + "]")
                    .setTarget("<col=" + WC_HEX + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> adjustWc(menuTile, 1)));

            custom.add(client.createMenuEntry(-1)
                    .setOption("Radius +5 (WC) [r=" + rShow + "]")
                    .setTarget("<col=" + WC_HEX + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> adjustWc(menuTile, 5)));

            custom.add(client.createMenuEntry(-1)
                    .setOption("Radius - (WC) [r=" + rShow + "]")
                    .setTarget("<col=" + WC_HEX + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> adjustWc(menuTile, -1)));

            custom.add(client.createMenuEntry(-1)
                    .setOption("Radius -5 (WC) [r=" + rShow + "]")
                    .setTarget("<col=" + WC_HEX + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> adjustWc(menuTile, -5)));

            custom.add(client.createMenuEntry(-1)
                    .setOption("Verwijder center (WC)")
                    .setTarget("<col=" + WC_HEX + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        String next = WcCenters.removeNearest(currentWcBlob(), menuTile);
                        LoneBotBootstrapPlugin.persistWcCenters(next);
                        BotRuntime.logConsole("WC center verwijderd bij " + menuTile.getX() + "," + menuTile.getY());
                        log.info("[WC centers] remove near {}", menuTile);
                    }));
        }
    }

    private void addAreaCenterEntries(List<MenuEntry> custom, WorldPoint menuTile, AreaCenters.Skill skill) {
        String blob = LoneBotBootstrapPlugin.readAreaCenters(skill);
        AreaCenters.Center nearest = AreaCenters.findNearest(blob, menuTile);
        boolean nearby = AreaCenters.isNearbyForEdit(nearest, menuTile);
        int rShow = nearest != null ? nearest.radius : AreaCenters.DEFAULT_RADIUS;
        String label = skill.label;
        String hex = skill.hex;

        custom.add(client.createMenuEntry(-1)
                .setOption("Voeg center toe (" + label + ")")
                .setTarget("<col=" + hex + ">Area center</col>")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> {
                    boolean dedupe = skill == AreaCenters.Skill.MINING;
                    String auto = AreaCenters.autoName(menuTile, skill.label);
                    String next = AreaCenters.addCenter(
                            LoneBotBootstrapPlugin.readAreaCenters(skill),
                            menuTile, AreaCenters.DEFAULT_RADIUS, auto, dedupe);
                    LoneBotBootstrapPlugin.persistAreaCenters(skill, next);
                    BotRuntime.logConsole(label + " center + " + auto + " r=" + AreaCenters.DEFAULT_RADIUS);
                    log.info("[{} centers] add {} name={}", label, menuTile, auto);
                }));

        if (nearby) {
            custom.add(client.createMenuEntry(-1)
                    .setOption("Radius + (" + label + ") [r=" + rShow + "]")
                    .setTarget("<col=" + hex + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> adjustArea(skill, menuTile, 1)));

            custom.add(client.createMenuEntry(-1)
                    .setOption("Radius +5 (" + label + ") [r=" + rShow + "]")
                    .setTarget("<col=" + hex + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> adjustArea(skill, menuTile, 5)));

            custom.add(client.createMenuEntry(-1)
                    .setOption("Radius - (" + label + ") [r=" + rShow + "]")
                    .setTarget("<col=" + hex + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> adjustArea(skill, menuTile, -1)));

            custom.add(client.createMenuEntry(-1)
                    .setOption("Radius -5 (" + label + ") [r=" + rShow + "]")
                    .setTarget("<col=" + hex + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> adjustArea(skill, menuTile, -5)));

            custom.add(client.createMenuEntry(-1)
                    .setOption("Verwijder center (" + label + ")")
                    .setTarget("<col=" + hex + ">Area center</col>")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        String next = AreaCenters.removeNearest(
                                LoneBotBootstrapPlugin.readAreaCenters(skill), menuTile);
                        if (skill == AreaCenters.Skill.MINING) {
                            next = AreaCenters.dedupeByTile(next);
                        }
                        LoneBotBootstrapPlugin.persistAreaCenters(skill, next);
                        BotRuntime.logConsole(label + " center verwijderd bij "
                                + menuTile.getX() + "," + menuTile.getY());
                        log.info("[{} centers] remove near {}", label, menuTile);
                    }));
        }
    }

    private void adjustArea(AreaCenters.Skill skill, WorldPoint menuTile, int delta) {
        String next = AreaCenters.adjustRadius(
                LoneBotBootstrapPlugin.readAreaCenters(skill), menuTile, delta);
        if (skill == AreaCenters.Skill.MINING) {
            next = AreaCenters.dedupeByTile(next);
        }
        LoneBotBootstrapPlugin.persistAreaCenters(skill, next);
        AreaCenters.Center n = AreaCenters.findNearest(next, menuTile);
        String msg = n == null ? skill.label + " radius aangepast"
                : (n.hasEdge()
                ? skill.label + " edge ±" + delta
                : skill.label + " radius → " + n.radius);
        BotRuntime.logConsole(msg);
        log.info("[{} centers] {}", skill.label, msg);
    }

    private void adjustWc(WorldPoint menuTile, int delta) {
        String next = WcCenters.adjustRadius(currentWcBlob(), menuTile, delta);
        LoneBotBootstrapPlugin.persistWcCenters(next);
        WcCenters.Center n = WcCenters.findNearest(next, menuTile);
        String msg = n == null ? "WC radius aangepast"
                : (n.hasEdge()
                ? "WC edge ±" + delta + " → " + n.minX + "," + n.minY + "–" + n.maxX + "," + n.maxY
                : "WC radius → " + n.radius);
        BotRuntime.logConsole(msg);
        log.info("[WC centers] {}", msg);
    }

    private String currentWcBlob() {
        if (config != null) {
            String b = config.wcCenters();
            if (b != null && !b.isBlank()) {
                return b;
            }
        }
        return WcCenters.DEFAULT_BLOB;
    }

    private void addCoordsCapture(WorldPoint tile, boolean player) {
        WallDoorCaptureReport.Result r = WallDoorCaptureReport.formatCoords(tile, player);
        if (r == null) {
            return;
        }
        if (config != null && config.captureSkipDuplicates()
                && HoverCaptureLog.wouldSkipDuplicate(r.kind, r.line, r.detail)) {
            BotRuntime.logConsole("Capture skip duplicate: " + r.line);
            return;
        }
        HoverCaptureLog.add(r.kind, r.line, r.detail);
        BotRuntime.logConsole("Capture: " + r.line);
    }

    private void runWallsScan(WorldPoint center, CaptureMenuNpcHelper.NpcHit npc) {
        if (center == null) {
            return;
        }
        int radius = liveWallsScanRadius();
        RoomScan scan;
        if (npc != null) {
            scan = RoomScanner.scanNearNpc(client, center, npc.name, npc.id, radius);
        } else {
            scan = RoomScanner.scan(client, center, radius);
        }
        if (scan == null) {
            BotRuntime.logConsole("Get walls: geen resultaat");
            return;
        }
        WallDoorCaptureState.setLatest(scan);
        if (NoWalkZones.applyIfPotatoField(scan)) {
            log.info("[Walls] potato no-walk ← {} kamer-tegels", NoWalkZones.potatoTileCount());
            BotRuntime.logConsole("Potato no-walk bijgewerkt: " + NoWalkZones.potatoTileCount() + " tegels (Get walls)");
        }
        WallDoorCaptureReport.Result report = WallDoorCaptureReport.format(scan, client);
        if (report == null) {
            return;
        }
        boolean skipDup = config != null && config.captureSkipDuplicates()
                && HoverCaptureLog.wouldSkipDuplicate(report.kind, report.line, report.detail);
        if (!skipDup) {
            HoverCaptureLog.add(report.kind, report.line, report.detail);
        }
        log.info("[Walls] {}", report.line);
        BotRuntime.logConsole("[Walls] " + report.line
                + " (r=" + radius
                + ", tegels=" + scan.roomTiles.size()
                + ", wall-objects=" + (scan.wallObjectTiles != null ? scan.wallObjectTiles.size() : 0)
                + ")");
        if (report.preview != null && !report.preview.isEmpty()) {
            for (String pl : report.preview.split("\n")) {
                if (!pl.trim().isEmpty()) {
                    BotRuntime.logConsole("  " + pl.trim());
                }
            }
        }
    }

    /** Live radius (ConfigManager eerst — proxy kan stale zijn na slider). */
    private int liveWallsScanRadius() {
        int r = 12;
        if (configManager != null) {
            String raw = configManager.getConfiguration("lonebot", "wallsScanRadius");
            if (raw != null && !raw.isBlank()) {
                try {
                    r = Integer.parseInt(raw.trim());
                } catch (NumberFormatException ignored) {
                    if (config != null) {
                        r = config.wallsScanRadius();
                    }
                }
            } else if (config != null) {
                r = config.wallsScanRadius();
            }
        } else if (config != null) {
            r = config.wallsScanRadius();
        }
        return Math.max(4, Math.min(24, r));
    }

    /** Speler-positie naar capture (optioneel vanuit panel). */
    public void capturePlayerTile() {
        Player local = client != null ? client.getLocalPlayer() : null;
        if (local == null || local.getWorldLocation() == null) {
            return;
        }
        addCoordsCapture(local.getWorldLocation(), true);
    }

    void persist() {
        if (configManager != null) {
            configManager.setConfiguration("lonebot", "excludedTiles", ExcludedTiles.serialize());
            ExcludedTiles.saveShared();
        }
    }
}
