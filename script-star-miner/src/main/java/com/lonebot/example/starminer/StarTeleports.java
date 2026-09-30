package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.magic.SpellBook;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.game.Skills;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * F2P standard teleports naar hubs dichtbij ster-spots.
 * Staff dekt elemental (zoals de walker). Cast pas stil. Home alleen als Lumb-spell niet kan.
 */
final class StarTeleports {

    static final int AIR_RUNE = 556;
    static final int FIRE_RUNE = 554;
    static final int WATER_RUNE = 555;
    static final int EARTH_RUNE = 557;
    static final int LAW_RUNE = 563;
    static final int STAFF_OF_AIR = 1381;
    static final int AIR_BATTLESTAFF = 1397;
    static final int MYSTIC_AIR_STAFF = 1405;
    private static final int STAFF_OF_FIRE = 1387;
    private static final int FIRE_BATTLESTAFF = 1393;
    private static final int MYSTIC_FIRE_STAFF = 1401;
    private static final int STAFF_OF_WATER = 1383;
    private static final int WATER_BATTLESTAFF = 1395;
    private static final int MYSTIC_WATER_STAFF = 1403;
    private static final int STAFF_OF_EARTH = 1385;
    private static final int EARTH_BATTLESTAFF = 1399;
    private static final int MYSTIC_EARTH_STAFF = 1407;
    private static final int SMOKE_BATTLESTAFF = 11998;
    private static final int SMOKE_BATTLESTAFF_OR = 11999;
    private static final int MIST_BATTLESTAFF = 20730;
    private static final int MIST_BATTLESTAFF_OR = 20733;
    private static final int DUST_BATTLESTAFF = 20736;
    private static final int DUST_BATTLESTAFF_OR = 20739;
    private static final int MUD_BATTLESTAFF = 6562;
    private static final int MUD_BATTLESTAFF_OR = 6563;
    private static final int LAVA_BATTLESTAFF = 3053;
    private static final int LAVA_BATTLESTAFF_OR = 3054;
    private static final int STEAM_BATTLESTAFF = 11787;
    private static final int STEAM_BATTLESTAFF_OR = 11789;

    /** Alleen tele als huidige afstand minstens zoveel groter is dan hub→ster. */
    private static final int MIN_SAVE_TILES = 35;
    private static final long HOME_CD_MS = 30 * 60_000L;
    /** Max wacht spell-tele (faal); geland → meteen door. */
    private static final long CAST_SETTLE_MS = 2_800L;
    private static final long HOME_SETTLE_MS = 18_000L;

    enum Hub {
        VARROCK(SpellBook.Standard.VARROCK_TELEPORT, new WorldPoint(3213, 3424, 0), FIRE_RUNE),
        LUMBRIDGE(SpellBook.Standard.LUMBRIDGE_TELEPORT, new WorldPoint(3222, 3218, 0), EARTH_RUNE),
        FALADOR(SpellBook.Standard.FALADOR_TELEPORT, new WorldPoint(2965, 3378, 0), WATER_RUNE);

        final SpellBook.Standard spell;
        final WorldPoint land;
        final int elementalRuneId;

        Hub(SpellBook.Standard spell, WorldPoint land, int elementalRuneId) {
            this.spell = spell;
            this.land = land;
            this.elementalRuneId = elementalRuneId;
        }
    }

    private static final Map<String, Hub> SPOT_HUB = new HashMap<>();

    static {
        map("SE_VARROCK_MINE", Hub.VARROCK);
        map("CHAMPIONS_GUILD", Hub.VARROCK);
        map("VARROCK_EAST", Hub.VARROCK);
        map("AL_KHARID_MINE", Hub.VARROCK);
        map("AL_KHARID_BANK", Hub.VARROCK);
        map("EMIRS_ARENA", Hub.VARROCK);
        map("E_LUMB_SWAMP", Hub.LUMBRIDGE);
        map("W_LUMB_SWAMP", Hub.LUMBRIDGE);
        map("DRAYNOR", Hub.LUMBRIDGE);
        map("WEST_FALADOR", Hub.FALADOR);
        map("E_FALADOR_BANK", Hub.FALADOR);
        map("CRAFTING_GUILD", Hub.FALADOR);
        map("RIMMINGTON", Hub.FALADOR);
        map("DWARVEN_MINE", Hub.FALADOR);
        map("TAVERLEY", Hub.FALADOR);
    }

    private static long lastHomeTeleMs;
    private static long castUntilMs;
    private static WorldPoint castFrom;
    /** Landing-tegel van lopende cast (ook tijdens waitingCast met Choice.none). */
    private static WorldPoint pendingLand;
    private static String lastLog = "";
    private static long lastLogMs;

    private StarTeleports() {
    }

    private static void map(String key, Hub hub) {
        SPOT_HUB.put(key, hub);
    }

    static Hub hubFor(StarLocations.Spot spot) {
        if (spot == null || spot.key == null) {
            return null;
        }
        return SPOT_HUB.get(spot.key);
    }

    static boolean hasAirStaff() {
        try {
            return Equipment.contains(STAFF_OF_AIR, AIR_BATTLESTAFF, MYSTIC_AIR_STAFF)
                    || Inventory.contains(STAFF_OF_AIR, AIR_BATTLESTAFF, MYSTIC_AIR_STAFF);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Genoeg voor F2P teles: air staff of air runes + law + één elemental (rune of staff). */
    static boolean hasTravelKit() {
        if (runeCount(LAW_RUNE) < 1) {
            return false;
        }
        boolean airOk = coversElemental(AIR_RUNE) || airRuneCount() >= 15;
        if (!airOk) {
            return false;
        }
        return coversElemental(FIRE_RUNE) || runeCount(FIRE_RUNE) >= 1
                || coversElemental(EARTH_RUNE) || runeCount(EARTH_RUNE) >= 1
                || coversElemental(WATER_RUNE) || runeCount(WATER_RUNE) >= 1;
    }

    static boolean tryWieldAirStaff() {
        return tryWieldFor(AIR_RUNE);
    }

    static int airRuneCount() {
        try {
            return Inventory.getCount(true, AIR_RUNE);
        } catch (Throwable t) {
            return 0;
        }
    }

    static int runeCount(int id) {
        try {
            return Inventory.getCount(true, id);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Magic-level + law + elemental (rune of staff) + air (staff of 3 runes). */
    static boolean canCastSpell(Hub hub) {
        if (hub == null || !StarMinerPlugin.teleportsEnabled) {
            return false;
        }
        try {
            int magic = Skills.getLevel(Skill.MAGIC);
            if (magic < hub.spell.getLevel()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        if (runeCount(LAW_RUNE) < 1) {
            return false;
        }
        if (!coversElemental(hub.elementalRuneId) && runeCount(hub.elementalRuneId) < 1) {
            return false;
        }
        if (!coversElemental(AIR_RUNE) && airRuneCount() < 3) {
            return false;
        }
        return true;
    }

    /** Magic-level + runes/staff voor minstens één F2P-spell (niet Home). */
    static boolean canCastAnySpell() {
        for (Hub h : Hub.values()) {
            if (canCastSpell(h)) {
                return true;
            }
        }
        return false;
    }

    static boolean canHomeTele() {
        if (!StarMinerPlugin.teleportsEnabled) {
            return false;
        }
        if (Magic.isHomeTeleportOnCooldown()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastHomeTeleMs > 0L && now - lastHomeTeleMs < HOME_CD_MS) {
            return false;
        }
        return true;
    }

    /**
     * Beste hub voor deze ster, of null = lopen.
     * Home-fallback alleen voor Lumb-hub als spell niet kan.
     */
    static Choice choose(WorldPoint pos, StarLocations.Spot spot, WorldPoint starTile) {
        if (!StarMinerPlugin.teleportsEnabled || pos == null || starTile == null) {
            return Choice.none();
        }
        if (pos.getPlane() != starTile.getPlane()) {
            return Choice.none();
        }
        int dNow = pos.distanceTo(starTile);
        if (dNow < MIN_SAVE_TILES) {
            return Choice.none();
        }
        Hub hub = hubFor(spot);
        if (hub == null) {
            return Choice.none();
        }
        int dHub = hub.land.distanceTo(starTile);
        if (dHub + MIN_SAVE_TILES >= dNow) {
            return Choice.none();
        }
        if (canCastSpell(hub)) {
            return Choice.spell(hub);
        }
        log("geen " + hub.spell.getName()
                + " (mage/runes/staff) hub=" + hub.name()
                + " — " + (spot != null ? spot.shortName : "ster"));
        if (hub == Hub.LUMBRIDGE && canHomeTele()) {
            return Choice.home();
        }
        return Choice.none();
    }

    static boolean waitingCast() {
        return System.currentTimeMillis() < castUntilMs;
    }

    /** 0 = niets te doen; &gt;0 = delay na cast/wield. */
    static int tickCast(WorldPoint pos, Choice choice) {
        long now = System.currentTimeMillis();
        if (now < castUntilMs) {
            if (hasLanded(pos)) {
                clearCastWait();
                log("tele geland → loop");
                try {
                    StarPickaxes.tryWield();
                } catch (Throwable ignored) {
                }
                return ThreadLocalRandom.current().nextInt(60, 120);
            }
            return ThreadLocalRandom.current().nextInt(100, 180);
        }
        if (choice == null || choice.kind == Kind.NONE) {
            return 0;
        }
        if (isMoving()) {
            log("wacht stilstand vóór tele");
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        Hub hub = choice.hub;
        if (choice.kind == Kind.SPELL && hub != null) {
            if (tryWieldFor(hub.elementalRuneId)) {
                return ThreadLocalRandom.current().nextInt(280, 420);
            }
            if (tryWieldFor(AIR_RUNE)) {
                return ThreadLocalRandom.current().nextInt(280, 420);
            }
        }
        castFrom = pos;
        if (choice.kind == Kind.HOME) {
            pendingLand = Hub.LUMBRIDGE.land;
            boolean ok = Magic.cast(SpellBook.Standard.HOME_TELEPORT);
            lastHomeTeleMs = now;
            castUntilMs = now + HOME_SETTLE_MS;
            log("Home Teleport klik=" + ok);
            return ThreadLocalRandom.current().nextInt(200, 360);
        }
        if (hub == null) {
            return 0;
        }
        pendingLand = hub.land;
        boolean ok = Magic.cast(hub.spell);
        castUntilMs = now + CAST_SETTLE_MS;
        log(hub.spell.getName() + " klik=" + ok + " → " + hub.land.getX() + "," + hub.land.getY());
        return ThreadLocalRandom.current().nextInt(180, 320);
    }

    private static boolean isMoving() {
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            return me != null && me.present && (me.moving || me.walkDestination != null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean coversElemental(int runeId) {
        int[] ids = stavesFor(runeId);
        if (ids.length == 0) {
            return false;
        }
        try {
            return Equipment.contains(ids) || Inventory.contains(ids);
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return true als deze tick een Wield is uitgegeven */
    private static boolean tryWieldFor(int runeId) {
        if (runeId == AIR_RUNE && airRuneCount() >= 3) {
            return false;
        }
        if (runeId != AIR_RUNE && runeCount(runeId) >= 1) {
            return false;
        }
        int[] ids = stavesFor(runeId);
        if (ids.length == 0) {
            return false;
        }
        try {
            if (Equipment.contains(ids)) {
                return false;
            }
            var item = Inventory.getFirst(ids);
            if (item != null && (item.interact("Wield") || item.interact("Equip"))) {
                log("wield staff voor rune " + runeId);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int[] stavesFor(int runeId) {
        if (runeId == AIR_RUNE) {
            return new int[]{STAFF_OF_AIR, AIR_BATTLESTAFF, MYSTIC_AIR_STAFF,
                    SMOKE_BATTLESTAFF, SMOKE_BATTLESTAFF_OR,
                    MIST_BATTLESTAFF, MIST_BATTLESTAFF_OR,
                    DUST_BATTLESTAFF, DUST_BATTLESTAFF_OR};
        }
        if (runeId == FIRE_RUNE) {
            return new int[]{STAFF_OF_FIRE, FIRE_BATTLESTAFF, MYSTIC_FIRE_STAFF,
                    SMOKE_BATTLESTAFF, SMOKE_BATTLESTAFF_OR,
                    LAVA_BATTLESTAFF, LAVA_BATTLESTAFF_OR,
                    STEAM_BATTLESTAFF, STEAM_BATTLESTAFF_OR};
        }
        if (runeId == WATER_RUNE) {
            return new int[]{STAFF_OF_WATER, WATER_BATTLESTAFF, MYSTIC_WATER_STAFF,
                    MIST_BATTLESTAFF, MIST_BATTLESTAFF_OR,
                    MUD_BATTLESTAFF, MUD_BATTLESTAFF_OR,
                    STEAM_BATTLESTAFF, STEAM_BATTLESTAFF_OR};
        }
        if (runeId == EARTH_RUNE) {
            return new int[]{STAFF_OF_EARTH, EARTH_BATTLESTAFF, MYSTIC_EARTH_STAFF,
                    DUST_BATTLESTAFF, DUST_BATTLESTAFF_OR,
                    MUD_BATTLESTAFF, MUD_BATTLESTAFF_OR,
                    LAVA_BATTLESTAFF, LAVA_BATTLESTAFF_OR};
        }
        return new int[0];
    }

    private static boolean hasLanded(WorldPoint pos) {
        if (pos == null) {
            return false;
        }
        if (pendingLand != null && pos.getPlane() == pendingLand.getPlane()
                && pos.distanceTo(pendingLand) <= 18) {
            return true;
        }
        if (castFrom != null && pos.getPlane() == castFrom.getPlane()
                && pos.distanceTo(castFrom) >= 40) {
            return true;
        }
        return false;
    }

    private static void clearCastWait() {
        castUntilMs = 0L;
        pendingLand = null;
        castFrom = null;
    }

    static final class Choice {
        final Kind kind;
        final Hub hub;

        private Choice(Kind kind, Hub hub) {
            this.kind = kind;
            this.hub = hub;
        }

        static Choice none() {
            return new Choice(Kind.NONE, null);
        }

        static Choice spell(Hub hub) {
            return new Choice(Kind.SPELL, hub);
        }

        static Choice home() {
            return new Choice(Kind.HOME, Hub.LUMBRIDGE);
        }

        boolean use() {
            return kind != Kind.NONE;
        }
    }

    enum Kind {
        NONE, SPELL, HOME
    }

    private static void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/tele] " + msg);
    }
}
