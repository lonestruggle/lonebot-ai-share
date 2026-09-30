package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.magic.Magic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tijdens wachten op Mining-level: Prospect op de ster, verder niets / high alch / mine / woodcut in de buurt.
 * Nooit Mine op de crashed star.
 * Mine/WC: CombatBot-/LoneBot-WC-stijl — alleen erts-rotsen / choppable trees + bruikbare tool.
 */
final class StarWaitHelper {

    private static final int RADIUS = 16;
    private static final int NATURE_RUNE = 561;
    private static final int FIRE_RUNE = 554;
    private static final long RECLICK_MS = 5_500L;
    private static final long ALCH_GAP_MS = 2_800L;
    private static final long DEPLETED_AVOID_MS = 8_000L;

    /** Ore-rotsen (CombatBot): naam + Mining-level. Geen kale "Rocks". */
    private static final String[][] ORE_ROCKS = {
            {"runite", "85"},
            {"adamantite", "70"},
            {"adamant", "70"},
            {"mithril", "55"},
            {"gold", "40"},
            {"coal", "30"},
            {"silver", "20"},
            {"iron", "15"},
            {"tin", "1"},
            {"copper", "1"},
            {"clay", "1"},
            {"blurite", "10"}
    };

    /** WC hatchet ids (zelfde als WcAxes) — geen battleaxe op naam. */
    private static final int[] AXE_IDS = {
            20011, 23673, 13241, 6739, 1359, 1357, 1355, 1361, 1353, 1349, 1351, 13242, 23675
    };
    private static final String[][] AXES = {
            {"Dragon axe", "61", "60", "6739"},
            {"Rune axe", "41", "40", "1359"},
            {"Adamant axe", "31", "30", "1357"},
            {"Mithril axe", "21", "20", "1355"},
            {"Black axe", "11", "10", "1361"},
            {"Steel axe", "6", "5", "1353"},
            {"Iron axe", "1", "1", "1349"},
            {"Bronze axe", "1", "1", "1351"}
    };
    private static final String[][] TREE_LEVELS = {
            {"redwood", "90"},
            {"magic", "75"},
            {"yew", "60"},
            {"maple", "45"},
            {"mahogany", "50"},
            {"teak", "35"},
            {"willow", "30"},
            {"oak", "15"}
    };

    private boolean alchSpellReady;
    private int pendingAlchSlot = -1;
    private int pendingAlchId;
    private long lastActMs;
    private long lastLogMs;
    private String lastLog = "";
    private WorldPoint lastSideTile;
    private WorldPoint avoidDepletedTile;
    private long avoidDepletedUntilMs;
    private int lastRockObjectId;
    String status = "wacht";

    void reset() {
        alchSpellReady = false;
        pendingAlchSlot = -1;
        pendingAlchId = 0;
        lastActMs = 0L;
        lastSideTile = null;
        avoidDepletedTile = null;
        avoidDepletedUntilMs = 0L;
        lastRockObjectId = 0;
        status = "wacht";
    }

    int tick(WorldPoint pos, ITileObject star, int liveTier, int need, int mining) {
        StarMinerPlugin.WaitActivity act = StarMinerPlugin.waitActivity();
        String t = liveTier > 0 ? "T" + liveTier : "T?";
        String id = "";
        try {
            if (star != null) {
                id = " id=" + star.getId();
            }
        } catch (Throwable ignored) {
        }
        String waitLbl = "wacht " + t + id + " mining " + mining + "/" + (need > 0 ? need : "?");

        Players.LocalSnap me = Players.snapshotLocal();
        if (me != null && me.animating) {
            status = waitLbl + " · " + act.shortLabel;
            return ThreadLocalRandom.current().nextInt(450, 800);
        }

        if (act == StarMinerPlugin.WaitActivity.HIGH_ALCH) {
            return highAlch(waitLbl);
        }
        if (act == StarMinerPlugin.WaitActivity.MINE) {
            return nearbyMine(pos, star, waitLbl);
        }
        if (act == StarMinerPlugin.WaitActivity.WOODCUT) {
            return nearbyWc(pos, star, waitLbl);
        }
        status = waitLbl;
        log(waitLbl + " — geen Mine");
        return ThreadLocalRandom.current().nextInt(700, 1100);
    }

    private int highAlch(String waitLbl) {
        int magic;
        try {
            magic = Skills.getLevel(Skill.MAGIC);
        } catch (Throwable t) {
            magic = 0;
        }
        if (magic < 55) {
            status = waitLbl + " · alch Magic " + magic + "/55";
            log(status);
            return ThreadLocalRandom.current().nextInt(800, 1200);
        }
        if (!hasNature() || !hasFireSource()) {
            status = waitLbl + " · alch: geen nature/fire";
            log(status);
            return ThreadLocalRandom.current().nextInt(800, 1200);
        }
        if (!fireReady()) {
            status = waitLbl + " · wield fire staff";
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        if (alchSpellReady && pendingAlchId > 0) {
            IInventoryItem item = Inventory.get(pendingAlchSlot);
            if (item == null || item.getId() != pendingAlchId) {
                item = Inventory.getFirst(i -> i != null && i.getId() == pendingAlchId);
            }
            alchSpellReady = false;
            pendingAlchSlot = -1;
            pendingAlchId = 0;
            if (item == null) {
                status = waitLbl + " · alch item weg";
                return ThreadLocalRandom.current().nextInt(350, 600);
            }
            boolean ok = Magic.clickCastOnInventory(item);
            lastActMs = System.currentTimeMillis();
            status = waitLbl + (ok ? " · High Alch " : " · alch-miss ") + safeName(item);
            log(status);
            return ThreadLocalRandom.current().nextInt(500, 800);
        }
        long now = System.currentTimeMillis();
        if (now - lastActMs < ALCH_GAP_MS) {
            status = waitLbl + " · alch…";
            return ThreadLocalRandom.current().nextInt(350, 600);
        }
        IInventoryItem target = pickAlchItem();
        if (target == null) {
            status = waitLbl + " · alch: geen items";
            log(status);
            return ThreadLocalRandom.current().nextInt(800, 1200);
        }
        boolean selected = Magic.cast("High Level Alchemy");
        if (selected) {
            alchSpellReady = true;
            pendingAlchSlot = target.getSlot();
            pendingAlchId = target.getId();
            status = waitLbl + " · select High Alch";
            log(status);
            return ThreadLocalRandom.current().nextInt(220, 380);
        }
        status = waitLbl + " · High Alch widget-miss";
        log(status);
        return ThreadLocalRandom.current().nextInt(500, 800);
    }

    private int nearbyMine(WorldPoint pos, ITileObject star, String waitLbl) {
        if (!StarPickaxes.onPerson()) {
            status = waitLbl + " · mine: geen pickaxe";
            log(status);
            return ThreadLocalRandom.current().nextInt(800, 1200);
        }
        if (dropOreIfFull()) {
            status = waitLbl + " · drop ore";
            return ThreadLocalRandom.current().nextInt(300, 550);
        }
        if (StarPickaxes.tryWield()) {
            status = waitLbl + " · wield pickaxe";
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        long now = System.currentTimeMillis();
        markDepletedIfNeeded(pos, star, now, true);

        boolean urgent = avoidDepletedTile != null && now < avoidDepletedUntilMs;
        if (!urgent && now - lastActMs < RECLICK_MS) {
            status = waitLbl + " · mine…";
            return ThreadLocalRandom.current().nextInt(400, 700);
        }

        ITileObject rock = pickBestOreRock(pos, star, now);
        if (rock == null) {
            status = waitLbl + " · geen erts-rots in de buurt";
            log(status);
            return ThreadLocalRandom.current().nextInt(800, 1200);
        }
        boolean ok = rock.interact("Mine");
        lastActMs = now;
        lastSideTile = rock.getWorldLocation();
        lastRockObjectId = safeObjId(rock);
        status = waitLbl + (ok ? " · Mine " : " · Mine-miss ") + safeObj(rock);
        log(status);
        return ThreadLocalRandom.current().nextInt(450, 800);
    }

    private int nearbyWc(WorldPoint pos, ITileObject star, String waitLbl) {
        if (!hasUsableAxe()) {
            status = waitLbl + " · wc: geen bruikbare axe";
            log(status);
            return ThreadLocalRandom.current().nextInt(900, 1400);
        }
        if (dropLogsIfFull()) {
            status = waitLbl + " · drop logs";
            return ThreadLocalRandom.current().nextInt(300, 550);
        }
        if (tryWieldUsableAxe()) {
            status = waitLbl + " · wield axe";
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        long now = System.currentTimeMillis();
        markDepletedIfNeeded(pos, star, now, false);

        boolean urgent = avoidDepletedTile != null && now < avoidDepletedUntilMs;
        if (!urgent && now - lastActMs < RECLICK_MS) {
            status = waitLbl + " · chop…";
            return ThreadLocalRandom.current().nextInt(400, 700);
        }

        int wc;
        try {
            wc = Skills.getLevel(Skill.WOODCUTTING);
        } catch (Throwable t) {
            wc = 1;
        }
        ITileObject tree = pickBestTree(pos, star, wc, now);
        if (tree == null) {
            status = waitLbl + " · geen boom (level " + wc + ") in de buurt";
            log(status);
            return ThreadLocalRandom.current().nextInt(800, 1200);
        }
        boolean ok = tree.interact("Chop down");
        lastActMs = now;
        lastSideTile = tree.getWorldLocation();
        lastRockObjectId = safeObjId(tree);
        status = waitLbl + (ok ? " · Chop " : " · Chop-miss ") + safeObj(tree);
        log(status);
        return ThreadLocalRandom.current().nextInt(450, 800);
    }

    /** Uitgeput: kale Rocks / object-id gewisseld / weg → tile tijdelijk skippen. */
    private void markDepletedIfNeeded(WorldPoint pos, ITileObject star, long now, boolean mining) {
        if (lastSideTile == null) {
            return;
        }
        ITileObject at = findAtTile(pos, star, lastSideTile, mining);
        boolean gone = at == null;
        boolean bare = false;
        boolean idChanged = false;
        if (at != null) {
            String n = safeObj(at);
            bare = mining && isBareRocksLabel(n);
            int id = safeObjId(at);
            idChanged = lastRockObjectId > 0 && id > 0 && id != lastRockObjectId;
            if (mining && !bare && !isOreRockName(n, miningLevel())) {
                bare = true;
            }
            if (!mining && (n.toLowerCase(Locale.ROOT).contains("dead") || !at.hasAction("Chop down"))) {
                bare = true;
            }
        }
        if (gone || bare || idChanged) {
            avoidDepletedTile = lastSideTile;
            avoidDepletedUntilMs = now + DEPLETED_AVOID_MS;
            log((mining ? "rots" : "boom") + " weg/uitgeput @ "
                    + lastSideTile.getX() + "," + lastSideTile.getY() + " — andere");
            lastSideTile = null;
            lastRockObjectId = 0;
            lastActMs = 0L;
        }
    }

    private ITileObject findAtTile(WorldPoint pos, ITileObject star, WorldPoint tile, boolean mining) {
        if (tile == null) {
            return null;
        }
        WorldPoint anchor = (star != null && star.getWorldLocation() != null)
                ? star.getWorldLocation() : pos;
        List<ITileObject> all = collectCandidates(pos, anchor, mining, mining ? miningLevel() : woodcutLevel(),
                System.currentTimeMillis(), true);
        for (ITileObject o : all) {
            if (o != null && sameTile(o.getWorldLocation(), tile)) {
                return o;
            }
        }
        try {
            return TileObjects.getNearest(tile, o -> o != null && sameTile(o.getWorldLocation(), tile));
        } catch (Throwable t) {
            return null;
        }
    }

    private ITileObject pickBestOreRock(WorldPoint pos, ITileObject star, long now) {
        WorldPoint anchor = (star != null && star.getWorldLocation() != null)
                ? star.getWorldLocation() : pos;
        WorldPoint me = pos != null ? pos : anchor;
        List<ITileObject> pool = collectCandidates(me, anchor, true, miningLevel(), now, false);
        return pickBestByWalkSteps(pool, me, avoidDepletedTile);
    }

    private ITileObject pickBestTree(WorldPoint pos, ITileObject star, int wc, long now) {
        WorldPoint anchor = (star != null && star.getWorldLocation() != null)
                ? star.getWorldLocation() : pos;
        WorldPoint me = pos != null ? pos : anchor;
        List<ITileObject> pool = collectCandidates(me, anchor, false, wc, now, false);
        return pickBestByWalkSteps(pool, me, avoidDepletedTile);
    }

    private List<ITileObject> collectCandidates(WorldPoint me, WorldPoint anchor, boolean mining,
                                                 int skillLvl, long now, boolean ignoreAvoid) {
        List<ITileObject> out = new ArrayList<>();
        if (anchor == null) {
            return out;
        }
        try {
            for (ITileObject o : TileObjects.getAll(obj -> obj != null && obj.getName() != null)) {
                if (o == null || StarObjects.isStar(o)) {
                    continue;
                }
                WorldPoint t = o.getWorldLocation();
                if (t == null || t.distanceTo(anchor) > RADIUS) {
                    continue;
                }
                if (!ignoreAvoid && avoidDepletedTile != null && now < avoidDepletedUntilMs
                        && sameTile(t, avoidDepletedTile)) {
                    continue;
                }
                String name = o.getName();
                if (mining) {
                    if (!o.hasAction("Mine") || isBareRocksLabel(name) || !isOreRockName(name, skillLvl)) {
                        continue;
                    }
                } else {
                    if (!o.hasAction("Chop down") || !isChoppableTree(name, skillLvl)) {
                        continue;
                    }
                }
                out.add(o);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** CombatBot: kale "Rock(s)" = uitgeput / geen erts. */
    private static boolean isBareRocksLabel(String name) {
        if (name == null) {
            return false;
        }
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "rock":
            case "rocks":
                return true;
            default:
                return false;
        }
    }

    /** Alleen echte erts-rotsen op naam + Mining-level. */
    private static boolean isOreRockName(String name, int mining) {
        if (name == null || name.isBlank() || isBareRocksLabel(name)) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("star") || n.contains("crashed")) {
            return false;
        }
        if (!n.contains("rock")) {
            return false;
        }
        for (String[] ore : ORE_ROCKS) {
            if (n.contains(ore[0])) {
                return mining >= Integer.parseInt(ore[1]);
            }
        }
        return false;
    }

    private static boolean isChoppableTree(String name, int wc) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT).trim();
        if (n.contains("star") || n.contains("dead") || n.contains("stump")) {
            return false;
        }
        for (String[] row : TREE_LEVELS) {
            if (n.contains(row[0])) {
                return wc >= Integer.parseInt(row[1]);
            }
        }
        // Normale "Tree" (geen oak/willow/…)
        return n.equals("tree") && wc >= 1;
    }

    private static int miningLevel() {
        try {
            return Skills.getLevel(Skill.MINING);
        } catch (Throwable t) {
            return 1;
        }
    }

    private static int woodcutLevel() {
        try {
            return Skills.getLevel(Skill.WOODCUTTING);
        } catch (Throwable t) {
            return 1;
        }
    }

    private static boolean hasUsableAxe() {
        int wc = woodcutLevel();
        for (String[] a : AXES) {
            int needWc = Integer.parseInt(a[1]);
            int id = Integer.parseInt(a[3]);
            if (wc < needWc) {
                continue;
            }
            try {
                if (Inventory.contains(id) || Equipment.contains(id)
                        || Inventory.contains(a[0]) || Equipment.contains(a[0])) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            return wc >= 61 && (Inventory.contains(13241, 13242, 20011, 23673, 23675)
                    || Equipment.contains(13241, 13242, 20011, 23673, 23675));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean tryWieldUsableAxe() {
        try {
            if (Equipment.contains(AXE_IDS)) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        int wc = woodcutLevel();
        int atk;
        try {
            atk = Skills.getLevel(Skill.ATTACK);
        } catch (Throwable t) {
            atk = 1;
        }
        for (String[] a : AXES) {
            int needWc = Integer.parseInt(a[1]);
            int needAtk = Integer.parseInt(a[2]);
            int id = Integer.parseInt(a[3]);
            if (wc < needWc || atk < needAtk) {
                continue;
            }
            try {
                IInventoryItem axe = Inventory.getFirst(id);
                if (axe == null) {
                    axe = Inventory.getFirst(i -> i != null && a[0].equalsIgnoreCase(safeName(i)));
                }
                if (axe != null && (axe.interact("Wield") || axe.interact("Wear"))) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** Cardinaal walk-steps (CombatBot ObjectReachHelper / WcReach). */
    private static ITileObject pickBestByWalkSteps(List<ITileObject> pool, WorldPoint me,
                                                    WorldPoint softRecent) {
        if (pool == null || pool.isEmpty() || me == null) {
            return null;
        }
        ITileObject best = null;
        int bestSteps = Integer.MAX_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (ITileObject obj : pool) {
            if (obj == null || obj.getWorldLocation() == null) {
                continue;
            }
            WorldPoint tile = obj.getWorldLocation();
            int steps = minWalkStepsToInteract(me, tile);
            if (softRecent != null && sameTile(tile, softRecent)) {
                steps += 1;
            }
            int dist = me.distanceTo(tile);
            if (steps < bestSteps || (steps == bestSteps && dist < bestDist)) {
                bestSteps = steps;
                bestDist = dist;
                best = obj;
            }
        }
        return best;
    }

    private static int minWalkStepsToInteract(WorldPoint me, WorldPoint obj) {
        if (me == null || obj == null) {
            return Integer.MAX_VALUE;
        }
        int dx = Math.abs(me.getX() - obj.getX());
        int dy = Math.abs(me.getY() - obj.getY());
        if (dx + dy == 1) {
            return 0;
        }
        int best = Integer.MAX_VALUE;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int steps = Math.max(Math.abs(me.getX() - (obj.getX() + d[0])),
                    Math.abs(me.getY() - (obj.getY() + d[1])));
            if (steps < best) {
                best = steps;
            }
        }
        return best;
    }

    private static boolean sameTile(WorldPoint a, WorldPoint b) {
        return a != null && b != null
                && a.getX() == b.getX()
                && a.getY() == b.getY()
                && a.getPlane() == b.getPlane();
    }

    private static int safeObjId(ITileObject o) {
        try {
            return o != null ? o.getId() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean hasNature() {
        try {
            return Inventory.getCount(NATURE_RUNE) >= 1;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasFireSource() {
        try {
            if (Inventory.getCount(FIRE_RUNE) >= 5) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return fireStaffEquipped() || fireStaffInInv();
    }

    private static boolean fireReady() {
        try {
            if (Inventory.getCount(FIRE_RUNE) >= 5) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (fireStaffEquipped()) {
            return true;
        }
        IInventoryItem staff = firstFireStaffInv();
        if (staff == null) {
            return true;
        }
        return staff.interact("Wield") || staff.interact("Wear");
    }

    private static boolean fireStaffEquipped() {
        try {
            return Equipment.contains(StarWaitHelper::looksLikeFireStaff);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean fireStaffInInv() {
        return firstFireStaffInv() != null;
    }

    private static IInventoryItem firstFireStaffInv() {
        try {
            return Inventory.getFirst(StarWaitHelper::looksLikeFireStaff);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean looksLikeFireStaff(IInventoryItem item) {
        if (item == null) {
            return false;
        }
        String n = safeName(item).toLowerCase();
        if (n.contains("tome of fire") && !n.contains("empty")) {
            return true;
        }
        return n.contains("staff of fire")
                || n.contains("fire battlestaff")
                || n.contains("mystic fire")
                || n.contains("lava battlestaff")
                || n.contains("mystic lava");
    }

    private static IInventoryItem pickAlchItem() {
        IInventoryItem noted = Inventory.getFirst(i -> i != null && i.isNoted() && !isKeep(i));
        if (noted != null) {
            return noted;
        }
        return Inventory.getFirst(i -> i != null && !isKeep(i));
    }

    private static boolean isKeep(IInventoryItem item) {
        if (item == null) {
            return true;
        }
        int id = item.getId();
        if (id == StarPickaxes.COINS_ID || id == StarPickaxes.STARDUST_ID || id == StarPickaxes.GENIE_LAMP_ID) {
            return true;
        }
        if (StarPickaxes.isPickaxeId(id) || isAxeId(id) || looksLikeFireStaff(item)) {
            return true;
        }
        if (id == NATURE_RUNE || id == FIRE_RUNE) {
            return true;
        }
        try {
            if (item.hasAction("Eat") || item.hasAction("Drink")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String n = safeName(item).toLowerCase();
        if (n.isBlank()) {
            return true;
        }
        if (n.contains("pickaxe")
                || n.contains("stardust")
                || n.startsWith("uncut")
                || n.contains("geode")
                || n.contains("clue")
                || n.contains("lamp")
                || n.contains("coins")
                || n.contains("essence")
                || n.contains("teleport")
                || n.endsWith(" tab")
                || n.contains("pouch")
                || n.endsWith(" rune")
                || n.contains("runes")) {
            return true;
        }
        return n.contains("axe") && !n.contains("pickaxe") && !n.contains("battleaxe");
    }

    private static boolean isAxeId(int id) {
        for (int a : AXE_IDS) {
            if (a == id) {
                return true;
            }
        }
        return false;
    }

    private static boolean dropOreIfFull() {
        try {
            if (!Inventory.isFull()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        IInventoryItem ore = Inventory.getFirst(i -> {
            if (i == null || StarPickaxes.isPickaxeId(i.getId())) {
                return false;
            }
            String n = safeName(i).toLowerCase();
            return n.contains("ore") || n.equals("coal") || n.contains("clay");
        });
        return ore != null && ore.drop();
    }

    private static boolean dropLogsIfFull() {
        try {
            if (!Inventory.isFull()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        IInventoryItem logs = Inventory.getFirst(i -> {
            if (i == null) {
                return false;
            }
            String n = safeName(i).toLowerCase();
            return n.contains("logs") || n.equals("log");
        });
        return logs != null && logs.drop();
    }

    private static String safeName(IInventoryItem item) {
        try {
            String n = item.getName();
            return n != null ? n : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static String safeObj(ITileObject o) {
        try {
            String n = o.getName();
            return n != null && !n.isBlank() ? n : ("id " + o.getId());
        } catch (Throwable t) {
            return "object";
        }
    }

    private void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/wacht] " + msg);
    }
}
