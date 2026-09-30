package net.storm.sdk.movement.pathfinder;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.magic.SpellBook;
import net.storm.api.movement.TilePath;
import net.storm.api.movement.WalkOptions;
import net.storm.api.movement.pathfinder.model.Teleport;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.magic.TeleportTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shortest Path-stijl: F2P spell-teles als extra A*-starts als dat ≥{@link #MIN_SAVE_TILES}
 * scheelt. Geen fairy rings / items / members. Ook WorldWalker / map Walk-here.
 *
 * @see <a href="https://github.com/Skretzo/shortest-path">Skretzo/shortest-path</a>
 */
public final class F2pSpellTeleports {

    /** Alleen tele als hub→doel + deze drempel &lt; lopen (Star/SP: geen 8-tegel-winst). */
    public static final int MIN_SAVE_TILES = 35;
    /** Al bij de landing — geen tele. */
    public static final int SKIP_NEAR_LAND = 30;

    private static final int AIR_RUNE = 556;
    private static final int FIRE_RUNE = 554;
    private static final int WATER_RUNE = 555;
    private static final int EARTH_RUNE = 557;
    private static final int LAW_RUNE = 563;
    private static final int STAFF_OF_AIR = 1381;
    private static final int AIR_BATTLESTAFF = 1397;
    private static final int MYSTIC_AIR_STAFF = 1405;
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

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private F2pSpellTeleports() {
    }

    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        addSpell(SpellBook.Standard.VARROCK_TELEPORT,
                TeleportTestHelper.LAND_VARROCK, FIRE_RUNE, false);
        addSpell(SpellBook.Standard.LUMBRIDGE_TELEPORT,
                TeleportTestHelper.LAND_LUMBRIDGE, EARTH_RUNE, false);
        addSpell(SpellBook.Standard.FALADOR_TELEPORT,
                TeleportTestHelper.LAND_FALADOR, WATER_RUNE, false);
        addSpell(SpellBook.Standard.HOME_TELEPORT,
                TeleportTestHelper.LAND_HOME, 0, true);
    }

    /**
     * Speler-tegel + optionele hub-landingen. Map Walk-here mag F2P-spell starts.
     */
    public static List<WorldPoint> startsFor(WorldPoint from, WorldPoint dest) {
        List<WorldPoint> starts = new ArrayList<>();
        if (from != null) {
            starts.add(from);
        }
        if (from == null || dest == null) {
            return starts;
        }
        int walkEst = chebyshev(from, dest);
        if (from.getPlane() == dest.getPlane() && walkEst < MIN_SAVE_TILES) {
            return starts;
        }
        ensureRegistered();
        for (Teleport t : TeleportLoader.getCustomTeleports()) {
            if (t == null || t.getDestination() == null) {
                continue;
            }
            if (!PathfinderRequirements.met(t.getRequirements())) {
                continue;
            }
            WorldPoint land = t.getDestination();
            if (chebyshev(from, land) < SKIP_NEAR_LAND && from.getPlane() == land.getPlane()) {
                continue;
            }
            if (chebyshev(land, dest) + MIN_SAVE_TILES < walkEst) {
                starts.add(land);
            }
        }
        return starts;
    }

    /** Als A* bij een hub begon: teles op het pad + WalkOptions zodat TilePathWalker Cast doet. */
    public static void attachIfUsed(TilePath path, WorldPoint playerFrom) {
        if (path == null || path.isEmpty() || playerFrom == null) {
            return;
        }
        WorldPoint origin = path.get(0);
        if (origin == null || playerFrom.distanceTo(origin) <= 16) {
            return;
        }
        ensureRegistered();
        for (Teleport t : TeleportLoader.getCustomTeleports()) {
            WorldPoint land = t != null ? t.getDestination() : null;
            if (land != null && land.getPlane() == origin.getPlane() && land.distanceTo(origin) <= 8) {
                path.addTeleport(t);
            }
        }
        if (!path.getTeleports().isEmpty()) {
            path.setWalkOptions(WalkOptions.builder()
                    .useTransports(true)
                    .useTeleports(true)
                    .useHomeTeleports(true)
                    .build());
        }
    }

    /**
     * Na landing (of hop voorbij de hub): teles van het pad af.
     * Anders blijft {@code copyMeta} ze op remaining zetten → hop&gt;16 = oneindig “wacht tele-land”.
     */
    public static void dropIfAlreadyWalking(TilePath path, WorldPoint from) {
        if (path == null || from == null || path.getTeleports().isEmpty()) {
            return;
        }
        WorldPoint dest = path.getDestination();
        path.getTeleports().removeIf(t -> {
            WorldPoint land = t != null ? t.getDestination() : null;
            if (land == null) {
                return false;
            }
            if (from.getPlane() == land.getPlane() && from.distanceTo(land) <= 16) {
                return true;
            }
            return dest != null
                    && from.getPlane() == dest.getPlane()
                    && land.getPlane() == dest.getPlane()
                    && from.distanceTo(dest) <= land.distanceTo(dest) + 8;
        });
    }

    /**
     * Pad begint op een spell-landing en we zijn er nog niet — niet “remaining[0] 17 tegels achter
     * na een hop” (copyMeta houdt teles op het pad).
     */
    public static boolean isTelePathStart(TilePath path, WorldPoint from) {
        if (path == null || path.isEmpty() || from == null || path.getTeleports().isEmpty()) {
            return false;
        }
        WorldPoint origin = path.get(0);
        if (origin == null || from.distanceTo(origin) <= 16) {
            return false;
        }
        WorldPoint land = landingMatchingOrigin(path, origin);
        if (land == null) {
            return false;
        }
        WorldPoint dest = path.getDestination();
        if (dest != null && from.getPlane() == dest.getPlane()
                && from.distanceTo(dest) <= origin.distanceTo(dest) + 16) {
            return false;
        }
        return true;
    }

    private static WorldPoint landingMatchingOrigin(TilePath path, WorldPoint origin) {
        for (Teleport t : path.getTeleports()) {
            WorldPoint land = t != null ? t.getDestination() : null;
            if (land != null && land.getPlane() == origin.getPlane() && land.distanceTo(origin) <= 8) {
                return land;
            }
        }
        return null;
    }

    private static void addSpell(SpellBook.Standard spell, WorldPoint land,
                                 int elementalRuneId, boolean home) {
        Teleport.Builder b = Teleport.builder()
                .destination(land)
                .priority(home ? 80 : 10)
                .weight(home ? 40 : 8)
                .homeTeleport(home)
                .handler(() -> cast(spell, home))
                .requirement(() -> canUse(spell, elementalRuneId, home));
        TeleportLoader.addCustomTeleport(b.build());
    }

    private static boolean canUse(SpellBook.Standard spell, int elementalRuneId, boolean home) {
        try {
            if (!Magic.canCast(spell)) {
                return false;
            }
            if (home) {
                return !canUse(SpellBook.Standard.LUMBRIDGE_TELEPORT, EARTH_RUNE, false);
            }
            if (Skills.getLevel(Skill.MAGIC) < spell.getLevel()) {
                return false;
            }
            if (runeCount(LAW_RUNE) < 1) {
                return false;
            }
            if (elementalRuneId > 0 && !coversElemental(elementalRuneId) && runeCount(elementalRuneId) < 1) {
                return false;
            }
            return coversElemental(AIR_RUNE) || runeCount(AIR_RUNE) >= 3;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean cast(SpellBook.Standard spell, boolean home) {
        try {
            if (!home) {
                tryWieldFor(AIR_RUNE);
                int el = spell == SpellBook.Standard.VARROCK_TELEPORT ? FIRE_RUNE
                        : spell == SpellBook.Standard.LUMBRIDGE_TELEPORT ? EARTH_RUNE
                        : spell == SpellBook.Standard.FALADOR_TELEPORT ? WATER_RUNE : 0;
                if (el > 0) {
                    tryWieldFor(el);
                }
            }
            return Magic.cast(spell);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean coversElemental(int runeId) {
        if (runeId == AIR_RUNE) {
            return hasAny(STAFF_OF_AIR, AIR_BATTLESTAFF, MYSTIC_AIR_STAFF,
                    SMOKE_BATTLESTAFF, SMOKE_BATTLESTAFF_OR,
                    MIST_BATTLESTAFF, MIST_BATTLESTAFF_OR,
                    DUST_BATTLESTAFF, DUST_BATTLESTAFF_OR);
        }
        if (runeId == FIRE_RUNE) {
            return hasAny(STAFF_OF_FIRE, FIRE_BATTLESTAFF, MYSTIC_FIRE_STAFF,
                    SMOKE_BATTLESTAFF, SMOKE_BATTLESTAFF_OR,
                    LAVA_BATTLESTAFF, LAVA_BATTLESTAFF_OR,
                    STEAM_BATTLESTAFF, STEAM_BATTLESTAFF_OR);
        }
        if (runeId == WATER_RUNE) {
            return hasAny(STAFF_OF_WATER, WATER_BATTLESTAFF, MYSTIC_WATER_STAFF,
                    MIST_BATTLESTAFF, MIST_BATTLESTAFF_OR,
                    MUD_BATTLESTAFF, MUD_BATTLESTAFF_OR,
                    STEAM_BATTLESTAFF, STEAM_BATTLESTAFF_OR);
        }
        if (runeId == EARTH_RUNE) {
            return hasAny(STAFF_OF_EARTH, EARTH_BATTLESTAFF, MYSTIC_EARTH_STAFF,
                    DUST_BATTLESTAFF, DUST_BATTLESTAFF_OR,
                    MUD_BATTLESTAFF, MUD_BATTLESTAFF_OR,
                    LAVA_BATTLESTAFF, LAVA_BATTLESTAFF_OR);
        }
        return false;
    }

    private static boolean hasAny(int... ids) {
        try {
            return Equipment.contains(ids) || Inventory.contains(ids);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean tryWieldFor(int runeId) {
        int[] ids = stavesFor(runeId);
        if (ids.length == 0) {
            return false;
        }
        try {
            if (Equipment.contains(ids)) {
                return false;
            }
            var item = Inventory.getFirst(ids);
            return item != null && (item.interact("Wield") || item.interact("Equip"));
        } catch (Throwable t) {
            return false;
        }
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

    private static int chebyshev(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    private static int runeCount(int id) {
        try {
            return Inventory.getCount(true, id);
        } catch (Throwable t) {
            return 0;
        }
    }
}
