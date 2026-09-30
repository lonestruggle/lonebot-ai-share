package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.widgets.WidgetGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Beginner Treasure Trail locations, NPCs, items, map digs, anagrams.
 * Easy/medium/hard are intentionally absent.
 */
public final class BeginnerClueReference {

    public static final String SPADE = "Spade";
    public static final String STRANGE_DEVICE = "Strange device";
    public static final String CLUE_SCROLL_BEGINNER = "Clue scroll (beginner)";
    public static final String REWARD_CASKET_BEGINNER = "Reward casket (beginner)";
    public static final String CHARLIE = "Charlie the Tramp";

    /** Charlie the Tramp — zuid-Varrock (RuneLite NPC overlay / CombatBot). */
    public static final int NPC_ID_CHARLIE_TRAMP = 5209;
    public static final WorldPoint CHARLIE_TILE = new WorldPoint(3208, 3391, 0);
    public static final int CHARLIE_APPROACH = 15;

    public static final WorldPoint RELDO_APPROACH = new WorldPoint(3214, 3490, 0);
    public static final WorldPoint[] RELDO_DOOR_TILES = {
            new WorldPoint(3210, 3495, 0),
            new WorldPoint(3210, 3494, 0),
            new WorldPoint(3211, 3495, 0)
    };
    public static final WorldPoint RELDO_TILE = new WorldPoint(3210, 3494, 0);

    public static final WorldPoint BOB_WAYPOINT = new WorldPoint(3236, 3219, 0);
    public static final WorldPoint BOB_DEST = new WorldPoint(3231, 3203, 0);

    public static final int EMOTE_ARRIVAL = 2;
    public static final int MAP_DIG_ARRIVAL = 1;
    public static final int TALK_ARRIVAL = 4;
    public static final int HOT_COLD_ZONE_NEAR = 5;
    public static final int HOT_COLD_PROBE_MIN = 10;
    public static final int HOT_COLD_PROBE_MAX = 25;
    public static final int HOT_COLD_GRID_MAX = 14;

    public enum StepType {
        ANAGRAM,
        CRYPTIC_TALK,
        EMOTE,
        MAP_DIG,
        HOT_COLD,
        CHARLIE,
        SEARCH,
        UNKNOWN
    }

    public static final class NpcTarget {
        public final String npcName;
        public final WorldPoint tile;
        public final boolean reldoDoors;
        public final boolean talkOnce;

        public NpcTarget(String npcName, WorldPoint tile, boolean reldoDoors, boolean talkOnce) {
            this.npcName = npcName;
            this.tile = tile;
            this.reldoDoors = reldoDoors;
            this.talkOnce = talkOnce;
        }
    }

    public static final class EmoteStep {
        public final String emote;
        public final WorldPoint tile;
        public final boolean bobPath;
        /** Items die vóór emote equipped moeten zijn (CombatBot equipItems); mag leeg. */
        public final String[] equipItems;

        public EmoteStep(String emote, WorldPoint tile, boolean bobPath, String... equipItems) {
            this.emote = emote;
            this.tile = tile;
            this.bobPath = bobPath;
            this.equipItems = equipItems != null ? equipItems : new String[0];
        }
    }

    public static final class MapDig {
        public final int groupId;
        public final String label;
        public final WorldPoint dig;

        public MapDig(int groupId, String label, WorldPoint dig) {
            this.groupId = groupId;
            this.label = label;
            this.dig = dig;
        }
    }

    public static final class SearchStep {
        public final WorldPoint tile;
        public final String objectHint;

        public SearchStep(WorldPoint tile, String objectHint) {
            this.tile = tile;
            this.objectHint = objectHint;
        }
    }

    public static final class HotColdZone {
        public final String name;
        public final WorldPoint center;
        public final int minX;
        public final int maxX;
        public final int minY;
        public final int maxY;

        public HotColdZone(String name, WorldPoint center, int minX, int maxX, int minY, int maxY) {
            this.name = name;
            this.center = center;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        public boolean contains(WorldPoint p) {
            return p != null && p.getPlane() == center.getPlane()
                    && p.getX() >= minX && p.getX() <= maxX
                    && p.getY() >= minY && p.getY() <= maxY;
        }
    }

    private static final Map<String, NpcTarget> ANAGRAMS = new LinkedHashMap<>();
    private static final List<CrypticTalk> CRYPTICS = new ArrayList<>();
    private static final List<EmoteMatch> EMOTES = new ArrayList<>();
    private static final List<SearchMatch> SEARCHES = new ArrayList<>();
    private static final MapDig[] MAPS = {
            // CombatBot / RuneLite BeginnerMapClue exact dig tiles
            new MapDig(WidgetGroup.BEGINNER_CLUE_MAP_CHAMPIONS_GUILD, "Champions Guild",
                    new WorldPoint(3167, 3360, 0)),
            new MapDig(WidgetGroup.BEGINNER_CLUE_MAP_VARROCK_EAST_MINE, "Varrock East Mine",
                    new WorldPoint(3290, 3373, 0)),
            new MapDig(WidgetGroup.BEGINNER_CLUE_MAP_DRAYNOR, "south of Draynor bank",
                    new WorldPoint(3092, 3226, 0)),
            new MapDig(WidgetGroup.BEGINNER_CLUE_MAP_NORTH_OF_FALADOR, "standing stones",
                    new WorldPoint(3043, 3399, 0)),
            new MapDig(WidgetGroup.BEGINNER_CLUE_MAP_WIZARDS_TOWER, "Wizards Tower",
                    new WorldPoint(3109, 3153, 0))
    };
    /**
     * Charlie vraagt altijd één van deze (wiki Transcript:Charlie the Tramp; pre-made mag sinds 2022).
     * Alias {@link #CHARLIE_ITEMS} voor oudere callers.
     */
    public static final String[] CHARLIE_TRAMP_REQUEST_ITEMS = {
            "Trout", "Pike", "Leather body", "Leather chaps",
            "Raw trout", "Raw herring", "Iron ore", "Iron dagger"
    };

    /** Emote-kit items (CombatBot COMMON_EMOTE_ITEMS + STASH-gear texts). */
    public static final String[] COMMON_EMOTE_ITEMS = {
            "Bronze axe", "Chef's hat", "Gold necklace", "Gold ring",
            "Leather boots", "Leather gloves", "Red cape", "Cape",
            "Bronze med helm", "Iron chainbody"
    };

    /** @deprecated gebruik {@link #CHARLIE_TRAMP_REQUEST_ITEMS} */
    @Deprecated
    public static final String[] CHARLIE_ITEMS = CHARLIE_TRAMP_REQUEST_ITEMS;

    private static final class CrypticTalk {
        final String[] keys;
        final NpcTarget target;

        CrypticTalk(NpcTarget target, String... keys) {
            this.target = target;
            this.keys = keys;
        }
    }

    private static final class EmoteMatch {
        final String[] keys;
        final EmoteStep step;

        EmoteMatch(EmoteStep step, String... keys) {
            this.step = step;
            this.keys = keys;
        }
    }

    private static final class SearchMatch {
        final String[] keys;
        final SearchStep step;

        SearchMatch(SearchStep step, String... keys) {
            this.step = step;
            this.keys = keys;
        }
    }

    static {
        // Anagrams — RuneLite AnagramClue TRAIL_CLUE_BEGINNER (CombatBot coords)
        putAnagram("an earl", npc("Ranael", 3315, 3163, true));
        putAnagram("carpet ahoy", npc("Apothecary", 3195, 3404, true));
        // Sedridor: basement Wizards' Tower (~3102,9570) — niet surface tower
        putAnagram("char game disorder", npc("Archmage Sedridor", 3102, 9570, true));
        putAnagram("disorder", npc("Archmage Sedridor", 3102, 9570, true));
        putAnagram("i cord", npc("Doric", 2951, 3450, true));
        putAnagram("in bar", npc("Brian", BrianShopApproachHelper.BRIAN_TALK_TILE.getX(),
                BrianShopApproachHelper.BRIAN_TALK_TILE.getY(), true));
        putAnagram("rain cove", npc("Veronica", 3110, 3330, true));
        putAnagram("rug deter", npc("Gertrude", 3151, 3412, true));
        putAnagram("sir share red", npc("Hairdresser",
                HairdresserShopApproachHelper.HAIRDRESSER_TALK_TILE.getX(),
                HairdresserShopApproachHelper.HAIRDRESSER_TALK_TILE.getY(), true));
        putAnagram("taunt roof", npc("Fortunato", 3080, 3250, true));

        NpcTarget reldo = new NpcTarget("Reldo", RELDO_TILE, true, true);
        // CombatBot cryptic talk (5) + Reldo CRYPTIC_SPECIAL — geen easy/medium inventies
        CRYPTICS.add(new CrypticTalk(reldo, "buried beneath the ground", "reldo",
                "palace library", "varrock library", "strange device"));
        CRYPTICS.add(new CrypticTalk(npc("Hans", 3221, 3218, false),
                "always walking around the castle grounds", "hans"));
        CRYPTICS.add(new CrypticTalk(npc("Cook", 3208, 3213, false),
                "duke horacio calls home", "castle cook"));
        CRYPTICS.add(new CrypticTalk(npc("Hunding", 3097, 3432, 2, false),
                "village of barbarians", "hunding"));
        CRYPTICS.add(new CrypticTalk(npc("Charlie the Tramp", CHARLIE_TILE.getX(), CHARLIE_TILE.getY(), false),
                "charlie the tramp"));
        CRYPTICS.add(new CrypticTalk(npc("Shantay", 3303, 3123, false),
                "near the open desert", "shantay"));

        // Emote — RuneLite EmoteClue TRAIL_CLUE_BEGINNER (CombatBot)
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Raspberry", new WorldPoint(3203, 3424, 0), false,
                        "Gold ring", "Gold necklace"),
                "blow a raspberry at aris", "blow a raspberry", "raspberry", "aris"));
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Bow", new WorldPoint(3164, 3477, 0), false),
                "bow to brugsen bursen", "bow to brugsen", "brugsen bursen"));
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Cheer", new WorldPoint(3205, 3416, 0), false,
                        "Chef's hat", "Red cape"),
                "cheer at iffie nitter", "cheer at iffie", "iffie nitter", "thessalia"));
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Clap", BOB_DEST, true, "Bronze axe", "Leather boots"),
                "clap at bob's brilliant axes", "clap at bob", "bob's brilliant", "brilliant axes"));
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Panic", new WorldPoint(3303, 3271, 0), false),
                "panic at al kharid mine", "al kharid mine"));
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Spin", new WorldPoint(2950, 3387, 0), false),
                "spin at flynn's mace shop", "spin at flynn", "flynn's mace", "mace shop"));
        // Wiki/STASH beginner extras (gear via bank, geen STASH-unit F2P)
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Cheer", new WorldPoint(3204, 3173, 0), false,
                        "Leather boots", "Leather gloves"),
                "cheer in the centre of lumbridge swamp", "lumbridge swamp"));
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Cheer", new WorldPoint(3205, 3416, 0), false,
                        "Cape", "Chef's hat"),
                "cheer for the knitters of varrock", "cheer for the knitters", "knitters of varrock"));
        EMOTES.add(new EmoteMatch(
                new EmoteStep("Panic", new WorldPoint(3303, 3271, 0), false,
                        "Bronze med helm", "Iron chainbody"),
                "panic at the al kharid mine"));
    }

    private BeginnerClueReference() {
    }

    private static void putAnagram(String key, NpcTarget target) {
        ANAGRAMS.put(key, target);
    }

    private static NpcTarget npc(String name, int x, int y, boolean talkOnce) {
        return npc(name, x, y, 0, talkOnce);
    }

    private static NpcTarget npc(String name, int x, int y, int plane, boolean talkOnce) {
        return new NpcTarget(name, new WorldPoint(x, y, plane), false, talkOnce);
    }

    public static MapDig[] maps() {
        return MAPS;
    }

    /**
     * Bank-prep / casket-cleanup kit (CombatBot {@code allKitItemNames}) — geen scroll.
     */
    public static String[] allKitItemNames() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        names.add(SPADE);
        names.add(STRANGE_DEVICE);
        Collections.addAll(names, COMMON_EMOTE_ITEMS);
        Collections.addAll(names, CHARLIE_TRAMP_REQUEST_ITEMS);
        for (EmoteMatch m : EMOTES) {
            if (m.step != null && m.step.equipItems != null) {
                Collections.addAll(names, m.step.equipItems);
            }
        }
        return names.toArray(new String[0]);
    }

    /** Open map iface group → bekende dig-stap (CombatBot {@code matchByMapInterfaceGroup}). */
    public static MapDig matchByMapInterfaceGroup(int groupId) {
        for (MapDig m : MAPS) {
            if (m.groupId == groupId) {
                return m;
            }
        }
        return null;
    }

    /**
     * Map-clues via instructietekst (CombatBot {@code matchMapClueText}).
     */
    public static MapDig matchMapClueText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return null;
        }
        String norm = ClueScrollHelper.normalize(rawText);
        if (norm.contains("standing") && (norm.contains("stone") || norm.contains("stones"))) {
            return mapByLabelContains("standing");
        }
        if (norm.contains("north") && norm.contains("falador")
                && (norm.contains("stone") || norm.contains("altar"))) {
            return mapByLabelContains("standing");
        }
        if (norm.contains("champions") && norm.contains("guild")) {
            return mapByLabelContains("champions");
        }
        if (norm.contains("varrock") && norm.contains("east") && norm.contains("mine")) {
            return mapByLabelContains("varrock");
        }
        if (norm.contains("draynor") && norm.contains("bank")) {
            return mapByLabelContains("draynor");
        }
        if (norm.contains("wizard") && norm.contains("tower")) {
            return mapByLabelContains("wizard");
        }
        for (MapDig m : MAPS) {
            String lab = ClueScrollHelper.normalize(m.label);
            if (!lab.isEmpty() && norm.contains(lab)) {
                return m;
            }
        }
        return null;
    }

    private static MapDig mapByLabelContains(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (MapDig m : MAPS) {
            if (m.label != null && m.label.toLowerCase(Locale.ROOT).contains(k)) {
                return m;
            }
        }
        return null;
    }

    public static HotColdZone[] hotColdZones() {
        // Compat: dig-centra van CombatBot BeginnerZone
        BeginnerHotColdSolver.BeginnerZone[] zs = BeginnerHotColdSolver.BeginnerZone.values();
        HotColdZone[] out = new HotColdZone[zs.length];
        for (int i = 0; i < zs.length; i++) {
            WorldPoint c = zs[i].center;
            out[i] = new HotColdZone(zs[i].label, c,
                    c.getX() - 3, c.getX() + 3, c.getY() - 3, c.getY() + 3);
        }
        return out;
    }

    public static NpcTarget anagramNpc(String clueText) {
        String n = ClueScrollHelper.normalize(clueText);
        if (n.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, NpcTarget> e : ANAGRAMS.entrySet()) {
            if (n.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    public static NpcTarget crypticNpc(String clueText) {
        String n = ClueScrollHelper.normalize(clueText);
        if (n.isEmpty()) {
            return null;
        }
        NpcTarget best = null;
        int bestLen = 0;
        for (CrypticTalk c : CRYPTICS) {
            for (String key : c.keys) {
                String kn = ClueScrollHelper.normalize(key);
                if (n.contains(kn) && kn.length() > bestLen) {
                    bestLen = kn.length();
                    best = c.target;
                }
            }
        }
        return best;
    }

    public static EmoteStep emoteStep(String clueText) {
        String n = ClueScrollHelper.normalize(clueText);
        if (n.isEmpty() || !looksLikeEmote(n)) {
            return null;
        }
        EmoteMatch best = null;
        int bestScore = 0;
        for (EmoteMatch m : EMOTES) {
            int score = 0;
            for (String key : m.keys) {
                String kn = ClueScrollHelper.normalize(key);
                if (n.contains(kn)) {
                    score = Math.max(score, kn.length());
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best != null && bestScore > 0 ? best.step : null;
    }

    /** Beginner heeft geen Search-stap (CombatBot). */
    public static SearchStep searchStep(String clueText) {
        return null;
    }

    public static boolean looksLikeEmote(String normalized) {
        return normalized.contains("cheer") || normalized.contains("dance")
                || normalized.contains("wave") || normalized.contains("bow")
                || normalized.contains("think") || normalized.contains("panic")
                || normalized.contains("yawn") || normalized.contains("laugh")
                || normalized.contains("shrug") || normalized.contains("clap")
                || normalized.contains("raspberry") || normalized.contains("jump for joy")
                || normalized.contains("headbang") || normalized.contains("beckon")
                || normalized.contains("salute") || normalized.contains("jig")
                || normalized.contains("spin") || normalized.contains("blow a raspberry");
    }

    public static boolean looksLikeHotCold(String clueText) {
        String n = ClueScrollHelper.normalize(clueText);
        return n.contains("strange device") || n.contains("hot and cold")
                || n.contains("buried beneath") || n.contains("feel the device")
                || (n.contains("buried") && n.contains("device"));
    }

    public static boolean looksLikeCharlie(String clueText) {
        String n = ClueScrollHelper.normalize(clueText);
        return n.contains("charlie") || n.contains("tramp");
    }

    public static boolean looksLikeAnagram(String clueText) {
        String n = ClueScrollHelper.normalize(clueText);
        if (n.contains("anagram") || n.contains("rearrange")) {
            return true;
        }
        return anagramNpc(clueText) != null && !looksLikeEmote(n) && !looksLikeCharlie(clueText);
    }

    public static boolean isCharlieRequestItem(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return false;
        }
        String canon = canonicalCharlieItemName(itemName);
        for (String s : CHARLIE_TRAMP_REQUEST_ITEMS) {
            if (s.equalsIgnoreCase(canon)) {
                return true;
            }
        }
        return false;
    }

    public static String canonicalCharlieItemName(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return itemName;
        }
        for (String s : CHARLIE_TRAMP_REQUEST_ITEMS) {
            if (s.equalsIgnoreCase(itemName)) {
                return s;
            }
        }
        return itemName;
    }

    public static boolean scrollTextRequiresCharlieDelivery(String rawOrNormText) {
        if (rawOrNormText == null || rawOrNormText.isEmpty()) {
            return false;
        }
        if (scrollTextCharliePostDeliveryTalk(rawOrNormText)) {
            return false;
        }
        String n = rawOrNormText.contains("give charlie") || rawOrNormText.contains("need to give")
                ? rawOrNormText
                : ClueScrollHelper.normalize(rawOrNormText);
        if (!n.contains("charlie")) {
            return false;
        }
        return n.contains("give charlie") || n.contains("need to give charlie")
                || n.contains("need to give");
    }

    /**
     * Scroll na item-afgifte zonder afgerond gesprek:
     * "I have given the pike to Charlie, maybe I should talk to him."
     */
    public static boolean scrollTextCharliePostDeliveryTalk(String rawOrNormText) {
        if (rawOrNormText == null || rawOrNormText.isEmpty()) {
            return false;
        }
        String n = ClueScrollHelper.normalize(rawOrNormText);
        if (!n.contains("charlie")) {
            return false;
        }
        boolean given = n.contains("given") || n.contains("have given") || n.contains("i gave");
        boolean talk = n.contains("talk to him") || n.contains("talk to charlie")
                || n.contains("should talk") || n.contains("speak to him")
                || n.contains("speak to charlie");
        return given && talk;
    }

    /** Item uit post-delivery scroll ("given the pike to charlie"). */
    public static String parseCharlieItemFromPostDeliveryScroll(String rawOrNormText) {
        if (rawOrNormText == null || rawOrNormText.isEmpty()) {
            return null;
        }
        String n = ClueScrollHelper.normalize(rawOrNormText);
        if (!n.contains("charlie") || !n.contains("given")) {
            return null;
        }
        return parseCharlieItemRequestFromDialog(n);
    }

    /**
     * Parse Charlie dialoog / scroll: "I really need a cooked trout" enz.
     *
     * @return exact inventory item name, of null
     */
    public static String parseCharlieItemRequestFromDialog(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return null;
        }
        String n = ClueScrollHelper.normalize(rawText);
        if (n.contains("cooked trout")) {
            return "Trout";
        }
        if (n.contains("cooked pike")) {
            return "Pike";
        }
        if (n.contains("raw herring")) {
            return "Raw herring";
        }
        if (n.contains("raw trout")) {
            return "Raw trout";
        }
        if (n.contains("leather chaps")) {
            return "Leather chaps";
        }
        if (n.contains("leather body")) {
            return "Leather body";
        }
        if (n.contains("iron dagger")) {
            return "Iron dagger";
        }
        if (n.contains("iron ore")) {
            return "Iron ore";
        }
        for (String item : CHARLIE_TRAMP_REQUEST_ITEMS) {
            if (n.contains(item.toLowerCase(Locale.ROOT))) {
                return item;
            }
        }
        return null;
    }

    public static String charlieItemFromText(String text) {
        String parsed = parseCharlieItemRequestFromDialog(text);
        if (parsed != null) {
            return parsed;
        }
        String n = ClueScrollHelper.normalize(text);
        if (n.isEmpty()) {
            return null;
        }
        String best = null;
        int bestLen = 0;
        for (String item : CHARLIE_TRAMP_REQUEST_ITEMS) {
            String in = ClueScrollHelper.normalize(item);
            if (n.contains(in) && in.length() > bestLen) {
                best = item;
                bestLen = in.length();
            }
        }
        return best;
    }

    public static boolean isCharlieItemName(String name) {
        return isCharlieRequestItem(name);
    }

    public static boolean isKeepName(String name) {
        String n = ClueScrollHelper.strip(name);
        if (n.isEmpty()) {
            return false;
        }
        String low = n.toLowerCase(Locale.ROOT);
        if (low.contains("spade") || low.contains("strange device")) {
            return true;
        }
        return ClueScrollHelper.isClueOrCasketName(n);
    }

    /**
     * Of {@code actualItemName} in inv/equip voldoet aan emote-kit {@code requiredKitName}.
     * CombatBot {@code kitRequirementMetByItemName} (vereenvoudigd).
     */
    public static boolean kitRequirementMetByItemName(String requiredKitName, String actualItemName) {
        if (requiredKitName == null || actualItemName == null) {
            return false;
        }
        if (requiredKitName.equalsIgnoreCase(actualItemName)) {
            return true;
        }
        if ("Cape".equalsIgnoreCase(requiredKitName)) {
            String low = actualItemName.toLowerCase(Locale.ROOT);
            return low.contains("cape") && !low.contains("cape rack");
        }
        if ("Red cape".equalsIgnoreCase(requiredKitName)) {
            return actualItemName.equalsIgnoreCase("Red cape");
        }
        String req = requiredKitName.toLowerCase(Locale.ROOT).replace("'", "");
        String act = actualItemName.toLowerCase(Locale.ROOT).replace("'", "");
        if (req.equals(act)) {
            return true;
        }
        if (req.contains("bronze") && req.contains("helm") && act.contains("bronze") && act.contains("helm")) {
            return true;
        }
        return false;
    }

    public static List<String> keepNames(boolean hotCold, String charlieItem) {
        List<String> keep = new ArrayList<>();
        keep.add(SPADE);
        keep.add(CLUE_SCROLL_BEGINNER);
        keep.add(REWARD_CASKET_BEGINNER);
        if (hotCold) {
            keep.add(STRANGE_DEVICE);
        }
        if (charlieItem != null && !charlieItem.isEmpty()) {
            keep.add(charlieItem);
        }
        return Collections.unmodifiableList(keep);
    }

    /** Strange device: alleen Reldo / bank — niet GE. */
    public static boolean isReldoOnlyItem(String itemName) {
        return itemName != null && itemName.equalsIgnoreCase(STRANGE_DEVICE);
    }

    public static boolean isGePurchasableKitItem(String itemName) {
        return itemName != null && !itemName.isEmpty() && !isReldoOnlyItem(itemName);
    }

    /** Startprijs GE (GeRestockHelper escaleert). */
    public static int geStartPrice(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return 1000;
        }
        String n = itemName.toLowerCase(Locale.ROOT);
        if (n.equals("spade")) {
            return 500;
        }
        if (n.contains("gold ring") || n.contains("gold necklace")) {
            return 350;
        }
        if (n.contains("bronze axe") || n.contains("leather boots") || n.contains("leather gloves")) {
            return 200;
        }
        if (n.contains("chef's hat") || n.contains("red cape") || n.equals("cape")) {
            return 250;
        }
        if (n.contains("bronze med helm") || n.contains("iron chainbody")) {
            return 400;
        }
        if (n.contains("trout") || n.contains("pike") || n.contains("herring")) {
            return 80;
        }
        if (n.contains("leather body") || n.contains("leather chaps")) {
            return 350;
        }
        if (n.contains("iron ore") || n.contains("iron dagger")) {
            return 250;
        }
        return 1500;
    }
}
