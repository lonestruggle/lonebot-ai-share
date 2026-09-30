package com.lonebot.example.starminer;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shooting-star crash tiles + Discord aliases (Star Miners / CC names).
 */
public final class StarLocations {

    public static final class Spot {
        public final String key;
        public final WorldPoint tile;
        public final String shortName;
        public final boolean wilderness;
        public final boolean f2p;
        public final String[] aliases;

        Spot(String key, int x, int y, String shortName, boolean wilderness, boolean f2p, String... aliases) {
            this.key = key;
            this.tile = new WorldPoint(x, y, 0);
            this.shortName = shortName;
            this.wilderness = wilderness;
            this.f2p = f2p;
            this.aliases = aliases != null ? aliases : new String[0];
        }
    }

    /** Wiki: 21 F2P crash-sites (incl. wilderness). Iets ruimer zodat F2P-sterren niet worden overgeslagen. */
    private static final Set<String> F2P_KEYS = Set.of(
            "DRAYNOR", "W_LUMB_SWAMP", "E_LUMB_SWAMP", "SE_VARROCK_MINE", "CHAMPIONS_GUILD",
            "VARROCK_EAST", "AL_KHARID_MINE", "AL_KHARID_BANK", "EMIRS_ARENA", "RIMMINGTON",
            "CRAFTING_GUILD", "WEST_FALADOR", "E_FALADOR_BANK", "DWARVEN_MINE", "NORTH_CRANDOR",
            "SOUTH_CRANDOR", "CORSAIR_BANK", "TAVERLEY",
            "HOBGOBLIN_MINE", "MAGE_ZAMORAK_MINE", "SKELETON_MINE", "LAVA_MAZE", "PIRATES_HIDEOUT"
    );

    private static final List<Spot> ALL = new ArrayList<>();

    static {
        add("YANILLE_BANK", 2605, 3092, "Yanille bank", false, "yanille");
        add("SHAYZIEN_MINE", 1598, 3644, "Shayzien mine", false, "shayzien", "kourend castle mine");
        add("COX_BANK", 1260, 3563, "CoX bank", false, "chambers of xeric", "quidamortem", "cox",
                "chambers of xeric bank");
        add("DRAYNOR", 3089, 3237, "Draynor Village", false, "draynor", "dray");
        add("VOLCANIC_MINE", 3820, 3799, "Volcanic Mine", false, "fossil island volcanic", "vm entrance");
        add("MOUNT_KARUULM_BANK", 1325, 3819, "Mount Karuulm bank", false, "karuulm bank");
        add("DWARVEN_MINE", 3017, 3445, "Dwarven Mine", false, "north dwarven", "ice mountain", "dwarven");
        add("NARDAH", 3434, 2891, "Nardah", false, "nardah bank");
        add("AGILITY_PYRAMID", 3314, 2867, "Agility Pyramid", false, "pyramid mine");
        add("UZER_MINE", 3422, 3159, "Uzer Mine", false, "eagle eyrie", "uzer");
        add("PORT_KHAZARD", 2625, 3143, "Port Khazard", false, "khazard mine", "port khazard mine");
        add("GRAND_TREE", 2446, 3491, "Grand Tree", false, "west of grand tree");
        add("HOBGOBLIN_MINE", 3093, 3751, "Hobgoblin mine", true, "bandit camp mine", "lvl 30 wildy", "hob");
        add("FOSSIL_ISLAND", 3774, 3815, "Fossil Island", false, "fossil island mine", "rune rocks fossil");
        add("TAVERLEY", 2884, 3472, "Taverley", false, "white wolf", "taverley house", "taverley house portal",
                "tav");
        add("FELDIP_HILLS", 2573, 2965, "Feldip Hills", false, "feldip", "aks fairy");
        add("RELLEKKA_MINE", 2680, 3701, "Rellekka mine", false, "rellekka");
        add("PRIF_ZALCANO", 3275, 6057, "Prifddinas Zalcano", false, "zalcano", "trahaearn", "prif",
                "prifddinas zalcano entrance");
        add("W_LUMB_SWAMP", 3156, 3154, "W Lumbridge Swamp", false, "west lumbridge swamp", "w lumb swamp",
                "wlumb", "wls", "west lumbridge swamp mine");
        add("SE_VARROCK_MINE", 3292, 3353, "SE Varrock mine", false, "southeast varrock", "se varrock",
                "vse", "sev", "varrock se", "south east varrock", "southeast varrock mine");
        add("CHAMPIONS_GUILD", 3180, 3365, "Champions' Guild", false, "sw varrock mine", "champions guild",
                "champions guild mine", "vsw", "swv", "varrock sw", "varrock southwest", "southwest varrock",
                "south west varrock mine");
        add("RANTZ_CAVE", 2630, 2991, "Rantz cave", false, "rantz");
        add("MYNYDD", 2173, 3408, "Mynydd", false, "mynydd prif");
        add("CRAFTING_GUILD", 2933, 3276, "Crafting Guild", false,
                "c. guild", "craft guild", "crafting guild", "crafting guild mine",
                "crafting guild mining", "cguild", "c guild");
        add("LEGENDS_GUILD", 2703, 3332, "Legends Guild", false, "legends'", "south of legends");
        add("ISAFDAR", 2271, 3157, "Isafdar", false, "isafdar runite");
        add("CATHERBY", 2804, 3438, "Catherby", false, "catherby bank");
        add("CORSAIR_BANK", 2566, 2858, "Corsair Cove bank", false, "corsair cove", "ccove", "ccbank");
        add("CORSAIR_RESOURCE", 2482, 2881, "Corsair Resource Area", false, "corsair resource");
        add("COAL_TRUCKS", 2594, 3479, "Seers Village", false, "coal trucks", "seers");
        add("KELDAGRIM_ENTRANCE", 2725, 3683, "Keldagrim entrance", false, "keldagrim",
                "keldagrim entrance mine");
        add("PISCATORIS", 2342, 3632, "Piscatoris mine", false, "piscatoris", "akq fairy ring");
        add("SW_BRIMHAVEN", 2743, 3145, "SW Brimhaven", false, "southwest brimhaven", "brimhaven poh",
                "south brimhaven mine", "southwest of brimhaven poh");
        add("AL_KHARID_MINE", 3300, 3299, "Al Kharid mine", false, "al kharid mine", "alkharid mine",
                "akmine", "ak mine");
        add("MAGE_ZAMORAK_MINE", 3110, 3570, "Mage of Zamorak mine", true, "lvl 7 wildy", "zamorak mine");
        add("EMIRS_ARENA", 3352, 3277, "Emirs Arena", false, "pvp arena", "al kharid arena",
                "north of al kharid pvp arena", "emir", "pvpa");
        add("AL_KHARID_BANK", 3275, 3166, "Al Kharid Bank", false, "al kharid bank", "alkharid bank", "akbank");
        add("MAGE_ARENA", 3093, 3961, "Mage Arena bank", true, "mage arena", "lvl 56 wildy");
        add("MYTHS_GUILD", 2468, 2846, "Myths' Guild", false, "myths guild");
        add("VARROCK_EAST", 3260, 3412, "Varrock east bank", false, "varrock e", "e varrock", "varrock east",
                "veast", "veb");
        add("ARDOUGNE_MONASTERY", 2607, 3229, "Ardougne Monastery", false, "ardougne monastery", "se ardougne");
        add("ARCEUUS_ESSENCE", 1761, 3854, "Arceuus Essence mine", false, "dense essence", "arceuus");
        add("GNOME_STRONGHOLD", 2454, 3435, "Gnome Stronghold", false, "tree gnome", "spirit tree gnome");
        add("E_LUMB_SWAMP", 3232, 3152, "E Lumbridge Swamp", false, "east lumbridge swamp", "e lumb swamp",
                "elumb", "els");
        add("RIMMINGTON", 2974, 3243, "Rimmington", false, "rimmy", "rimmington mine", "rim");
        add("BURGH_DE_ROTT", 3497, 3220, "Burgh de Rott", false, "burgh");
        add("NATURE_ALTAR", 2843, 3033, "Nature Altar", false, "karamja jungle", "nature altar mine");
        add("SHILO_GEM", 2825, 2997, "Shilo Gem Mine", false, "shilo village", "shilo mine");
        add("WILDY_RESOURCE", 3188, 3935, "Wilderness Resource Area", true, "resource area", "wildy resource");
        add("JATIZSO", 2392, 3811, "Jatizso", false, "jatizso mine", "jatizso mine entrance");
        add("NEITIZNOT", 2372, 3833, "Neitiznot", false, "fremennik isles");
        add("MOUNT_KARUULM_MINE", 1276, 3811, "Mount Karuulm mine", false, "karuulm mine");
        add("KEBOS_SWAMP", 1210, 3651, "Kebos Swamp mine", false, "kebos");
        add("CANIFIS", 3503, 3483, "Canifis bank", false, "canifis");
        add("LLETYA", 2318, 3268, "Lletya", false);
        add("HOSIDIUS_MINE", 1781, 3491, "Hosidius mine", false, "hosidius");
        add("PISCARILIUS", 1765, 3707, "Piscarilius mine", false, "port piscarilius",
                "port piscarilius mine in kourend");
        add("LOVAKENGJ_BANK", 1534, 3756, "S Lovakengj bank", false, "lovakengj bank", "south lovakengj bank");
        add("ABANDONED_MINE", 3452, 3240, "Abandoned Mine", false, "abandoned mine burgh",
                "abandoned mine west of burgh");
        add("DESERT_QUARRY", 3176, 2909, "Desert Quarry mine", false, "desert quarry");
        add("NORTH_CRANDOR", 2837, 3294, "North Crandor", false, "n crandor", "crandor n", "ncran");
        add("NORTH_BRIMHAVEN", 2733, 3220, "Brimhaven gold mine", false, "nw brimhaven", "brimhaven gold");
        add("LOVAKITE", 1437, 3839, "Lovakite mine", false, "lovakite");
        add("ISLE_OF_SOULS", 2201, 2791, "Soul Wars south mine", false, "isle of souls", "soul wars");
        add("SOUTH_CRANDOR", 2821, 3240, "South Crandor", false, "s crandor", "crandor s", "scran");
        add("WEST_FALADOR", 2908, 3354, "West Falador mine", false, "w falador", "west fally", "wfally", "wf");
        add("DARKMEYER", 3635, 3338, "Darkmeyer mine", false, "daeyalt", "darkmeyer");
        add("TOB_BANK", 3560, 3212, "ToB bank", false, "theatre of blood", "ver sinhaza", "tob",
                "theatre of blood bank");
        add("MISCELLANIA", 2530, 3887, "Miscellania mine", false, "miscellania", "cip");
        add("SKELETON_MINE", 3019, 3593, "Skeleton mine", true, "lvl 10 wildy", "sw wilderness mine", "skel");
        add("MOS_LEHARMLESS", 3683, 2969, "Mos Le'Harmless", false, "mos leharmless", "mos le");
        add("LUNAR_ISLE", 2140, 3940, "Lunar Isle", false, "lunar", "lunar isle mine entrance");
        add("E_FALADOR_BANK", 3030, 3347, "E Falador bank", false, "east falador", "mining guild entrance",
                "e fally", "efally", "mguild");
        add("SE_VARLAMORE", 1742, 2957, "SE Varlamore mine", false, "stonecutter", "varlamore se",
                "varlamore south east mine");
        add("COLOSSEUM", 1771, 3107, "Colosseum entrance", false, "civitas", "fortis", "varlamore colosseum",
                "varlamore colosseum entrance bank");
        add("RALOS_RISE", 1486, 3093, "Ralos Rise Mining Site", false, "ralos", "hunter guild mine");
        add("PIRATES_HIDEOUT", 3049, 3945, "Pirates' Hideout", true, "pirates hideout", "lvl 53 wildy");
        add("LAVA_MAZE", 3057, 3890, "Lava maze", true, "lava maze runite", "lvl 46 wildy");
        add("MISTROCK", 1419, 2872, "Mistrock mine", false, "aldarin", "mistrock");
        add("SALVAGER", 1628, 3273, "Salvager Overlook mine", false, "salvager");
        add("CUSTODIA", 1287, 3414, "Custodia Mountains", false, "custodia", "custodia mountains mine");
    }

    private StarLocations() {
    }

    private static void add(String key, int x, int y, String shortName, boolean wild, String... extra) {
        List<String> aliases = new ArrayList<>();
        aliases.add(shortName);
        aliases.add(key.replace('_', ' '));
        if (extra != null) {
            Collections.addAll(aliases, extra);
        }
        ALL.add(new Spot(key, x, y, shortName, wild, F2P_KEYS.contains(key), aliases.toArray(new String[0])));
    }

    public static List<Spot> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Dichtstbijzijnde bekende crash-plek, of een scene-spot op deze tegel. */
    public static Spot nearestSpot(WorldPoint p) {
        if (p == null) {
            return new Spot("SCENE", 0, 0, "hier", false, true);
        }
        Spot best = null;
        int bestD = Integer.MAX_VALUE;
        for (Spot s : ALL) {
            if (s.tile == null || s.tile.getPlane() != p.getPlane()) {
                continue;
            }
            int d = p.distanceTo(s.tile);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best != null && bestD <= 40) {
            return best;
        }
        return new Spot("SCENE", p.getX(), p.getY(), "hier", false, true);
    }

    public static Spot match(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String n = norm(text);
        if (n.isBlank()) {
            return null;
        }
        String[] tokens = n.split(" ");
        Spot best = null;
        int bestScore = 0;
        for (Spot s : ALL) {
            for (String a : s.aliases) {
                String an = norm(a);
                if (an.isBlank()) {
                    continue;
                }
                int score = 0;
                boolean shortCode = !an.contains(" ") && an.length() <= 4;
                if (shortCode) {
                    for (String tok : tokens) {
                        if (tok.equals(an)) {
                            score = 50 + an.length();
                            break;
                        }
                    }
                } else if (an.length() >= 3 && n.contains(an)) {
                    score = an.length();
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
        }
        return best;
    }

    static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replace('\'', ' ').replaceAll("[^a-z0-9]+", " ").trim();
    }
}
