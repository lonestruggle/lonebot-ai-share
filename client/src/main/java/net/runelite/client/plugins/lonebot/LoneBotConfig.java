package net.runelite.client.plugins.lonebot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("lonebot")
public interface LoneBotConfig extends Config {

    // ---- ▶ Bot Control (CombatBotPanel) ----

    @ConfigItem(
            keyName = "botEnabled",
            name = "Bot inschakelen",
            description = "Master bot aan/uit (sync met Accounts Besturing)",
            position = 0
    )
    default boolean botEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "resetAccountTimersOnStop",
            name = "Reset per-account timers bij stop",
            description = "Wis account-sessietimers bij Stop",
            position = 1
    )
    default boolean resetAccountTimersOnStop() {
        return true;
    }

    @ConfigItem(
            keyName = "loopWatchRecoveryEnabled",
            name = "LoopWatch: herstel bij vaste loop",
            description = "Zelfde skill+status+tile te lang → zacht herstel, daarna bot uit + logout",
            position = 2
    )
    default boolean loopWatchRecoveryEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "loopWatchTriggerSec",
            name = "LoopWatch: herstel na (sec)",
            description = "Zelfde loop-signature langer dan dit → zacht herstel",
            position = 3
    )
    @Range(min = 45, max = 600)
    default int loopWatchTriggerSec() {
        return 90;
    }

    @ConfigItem(
            keyName = "loopWatchLogoutSec",
            name = "LoopWatch: logout na (sec)",
            description = "Zelfde loop-signature langer dan dit → bot uit + uitloggen",
            position = 4
    )
    @Range(min = 60, max = 900)
    default int loopWatchLogoutSec() {
        return 180;
    }

    @ConfigItem(
            keyName = "webGuiUrl",
            name = "Web GUI URL",
            description = "Externe web GUI — knop opent deze URL in de browser",
            position = 5
    )
    default String webGuiUrl() {
        return "";
    }

    @ConfigItem(
            keyName = "activityDiscordWebhook",
            name = "Activity log Discord webhook",
            description = "Optioneel: webhook-URL om stappen-logs naar Discord te sturen (Stop/LOOP/~10 min)",
            position = 7
    )
    default String activityDiscordWebhook() {
        return "";
    }

    @ConfigItem(
            keyName = "uiTheme",
            name = "GUI thema",
            description = "Mid-tone paneel: Balans / Mist / Grafiet / CombatBot / Bos / Oceaan / Warm",
            position = 8
    )
    default LoneBotUiTheme.Preset uiTheme() {
        return LoneBotUiTheme.Preset.BALANS;
    }

    @ConfigItem(
            keyName = "mouseFidgetEnabled",
            name = "Random muis-beweging",
            description = "Beweegt de muis periodiek random over de game-canvas (niet alleen midden)",
            position = 10
    )
    default boolean mouseFidgetEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "dialogAutoContinue",
            name = "Auto-continue dialog",
            description = "Algemeen: Space/invoke bij “Click here to continue”. Clue (Reldo e.d.) doet dit altijd, ook als dit uit staat.",
            position = 11
    )
    default boolean dialogAutoContinue() {
        return true;
    }

    @ConfigItem(
            keyName = "mouseSpeedPercent",
            name = "Muis snelheid %",
            description = "25=traag, 100=normaal, 200=snel (Bezier step-delay)",
            position = 2
    )
    @Range(min = 25, max = 300)
    default int mouseSpeedPercent() {
        return 100;
    }

    @ConfigItem(
            keyName = "cowCombatEnabled",
            name = "Cow combat",
            description = "Valt dichtstbijzijnde Cow / Cow calf aan",
            position = 3
    )
    default boolean cowCombatEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "monkKillerEnabled",
            name = "Monk Killer",
            description = "Monastery monks: fight, eat, cabbage/Edge bank, flee ≤3 HP",
            position = 35
    )
    default boolean monkKillerEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "combatClueSolver",
            name = "Combat: Clues doen",
            description = "Clue scroll/container → solver; daarna combat-script hervatten (Cow/Monk/…)",
            position = 34
    )
    default boolean combatClueSolver() {
        return false;
    }

    @ConfigItem(
            keyName = "combatEatPercent",
            name = "Combat: healen/eten onder HP %",
            description = "Heal (Talk-to of food) als HP op of onder dit percentage is — geldt voor combat-locaties",
            position = 36
    )
    @Range(min = 5, max = 90)
    default int combatEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "combatCriticalHp",
            name = "Combat: nood-flee onder HP",
            description = "Bij absolute HP ≤ dit: nood-flee (cabbage/food) — geldt voor combat-locaties",
            position = 37
    )
    @Range(min = 1, max = 15)
    default int combatCriticalHp() {
        return 3;
    }

    
    @ConfigItem(
            keyName = "monkClueSolver",
            name = "Clues doen (Monk, legacy)",
            description = "Legacy — gebruik Combat: Clues doen",
            position = 1410,
            hidden = true
    )
    default boolean monkClueSolver() {
        return false;
    }

    @ConfigItem(
            keyName = "monkHealViaTalk",
            name = "Monk: heal via Talk-to",
            description = "Praat met rustige monk (Can you heal me?) i.p.v. meteen eten; geen monks in gevecht. Geen talkable monk → cabbage/bank/food",
            position = 35
    )
    default boolean monkHealViaTalk() {
        return true;
    }

    @ConfigItem(
            keyName = "monkEatPercent",
            name = "Monk: eten/healen onder HP % (legacy)",
            description = "Legacy — gebruik Combat: healen/eten",
            position = 136,
            hidden = true
    )
    @Range(min = 5, max = 90)
    default int monkEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "monkCriticalHp",
            name = "Monk: nood-flee onder HP (legacy)",
            description = "Legacy — gebruik Combat: nood-flee",
            position = 137,
            hidden = true
    )
    @Range(min = 1, max = 15)
    default int monkCriticalHp() {
        return 3;
    }

    @ConfigItem(
            keyName = "monkFoodAmount",
            name = "Monk: food uit bank",
            description = "Doel-aantal food in inventory na bank-withdraw",
            position = 38
    )
    @Range(min = 1, max = 28)
    default int monkFoodAmount() {
        return 10;
    }

    @ConfigItem(
            keyName = "monkCabbagePickEnabled",
            name = "Monk: cabbage plukken",
            description = "Cabbage van het veld plukken als food-restock (uit = alleen Talk-heal / bank / inv-food)",
            position = 384
    )
    default boolean monkCabbagePickEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "monkCabbagePickAmount",
            name = "Monk: cabbage aantal",
            description = "Stock-aantal als HP al vol is. Bij HP-tekort: pluk genoeg cabbage (1 HP per stuk) om vol te komen",
            position = 385
    )
    @Range(min = 1, max = 28)
    default int monkCabbagePickAmount() {
        return 5;
    }

    @ConfigItem(
            keyName = "monkFoodSource",
            name = "Monk: food-bron",
            description = "AUTO = cabbage eerst, anders bank. CABBAGE/BANK = alleen die bron",
            position = 39
    )
    default MonkFoodSource monkFoodSource() {
        return MonkFoodSource.AUTO;
    }

    @ConfigItem(
            keyName = "monkBankWhenNoFood",
            name = "Monk: bank bij geen food",
            description = "Ga naar Edgeville bank als food op is (afhankelijk van food-bron)",
            position = 40
    )
    default boolean monkBankWhenNoFood() {
        return true;
    }

    @ConfigItem(
            keyName = "monkLogoutWhenNoFood",
            name = "Monk: uitloggen zonder food",
            description = "Als food niet te restocken is (geen cabbage/bank): bot uit + logout",
            position = 41
    )
    default boolean monkLogoutWhenNoFood() {
        return false;
    }

    enum MonkFoodSource {
        AUTO("Auto (cabbage → bank)"),
        CABBAGE("Alleen cabbage"),
        BANK("Alleen bank");

        private final String label;

        MonkFoodSource(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigItem(
            keyName = "cowLootEnabled",
            name = "Cow loot oppakken",
            description = "Pakt Bones/Cowhide/Raw beef/Coins op na/tijdens cow combat",
            position = 4
    )
    default boolean cowLootEnabled() {
        return true;
    }

    
    @ConfigItem(
            keyName = "cowClueSolver",
            name = "Clues doen (Cow, legacy)",
            description = "Legacy — gebruik Combat: Clues doen",
            position = 1510,
            hidden = true
    )
    default boolean cowClueSolver() {
        return false;
    }

@ConfigItem(
            keyName = "woodcuttingEnabled",
            name = "Woodcutting",
            description = "WC: Draynor willows / Port Sarim / yews, bonfire, forestry kit",
            position = 83
    )
    default boolean woodcuttingEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "wcFiremaking",
            name = "WC firemaking (bonfire)",
            description = "Legacy flag — UI gebruikt Logs: Bank / Firemaking / Drop. true = firemaking",
            position = 84
    )
    default boolean wcFiremaking() {
        return true;
    }

    @ConfigItem(
            keyName = "wcDropLogs",
            name = "WC drop logs",
            description = "Legacy flag — UI gebruikt Logs: Bank / Firemaking / Drop. true = drop",
            position = 85
    )
    default boolean wcDropLogs() {
        return false;
    }

    /**
     * Hoe volle inventory logs afhandelen. Schrijft {@link #wcFiremaking()} /
     * {@link #wcDropLogs()} (BANK = beide uit).
     */
    enum WcLogMode {
        BANK("Bank logs"),
        FIREMAKING("Firemaking (bonfire)"),
        DROP("Drop logs");

        private final String label;

        WcLogMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        public static WcLogMode fromFlags(boolean firemaking, boolean dropLogs) {
            if (firemaking) {
                return FIREMAKING;
            }
            if (dropLogs) {
                return DROP;
            }
            return BANK;
        }
    }

    @ConfigItem(
            keyName = "wcForestryEvents",
            name = "WC forestry events",
            description = "Forestry kit kopen + F2P events (roots, sapling, ent)",
            position = 86
    )
    default boolean wcForestryEvents() {
        return true;
    }

    @ConfigItem(
            keyName = "wcBirdNests",
            name = "WC bird nests",
            description = "Bird nests oppakken en Search",
            position = 87
    )
    default boolean wcBirdNests() {
        return true;
    }

    @ConfigItem(
            keyName = "wcClueSolver",
            name = "Clues doen",
            description = "Clue geode/nest/bottle/scroll → oppakken, openen, solver; daarna WC hervatten",
            position = 871
    )
    default boolean wcClueSolver() {
        return false;
    }

    @ConfigItem(
            keyName = "wcLocation",
            name = "WC locatie",
            description = "AUTO = per WC-level; of vaste spot (Draynor willows / Port Sarim / yews)",
            position = 88
    )
    default String wcLocation() {
        return "AUTO";
    }

    @ConfigItem(
            keyName = "wcCenters",
            name = "WC centers",
            description = "WC-centers: X:Y:Z:R:Naam:1 (meerdere met |). Edge: e:X1:Y1:X2:Y2:Z:Naam:1. In-game: rechtermuisklik als menu aan staat.",
            position = 881
    )
    default String wcCenters() {
        return com.lonebot.example.woodcutter.WcCenters.DEFAULT_BLOB;
    }

    @ConfigItem(
            keyName = "wcCentersMenuEnabled",
            name = "Rechtermenu: WC centers",
            description = "Choose-option: center toevoegen / radius ± / verwijderen",
            position = 882
    )
    default boolean wcCentersMenuEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "combatLocation",
            name = "Combat locatie",
            description = "AUTO of vaste spot uit combatCenters (voor later combat-script)",
            position = 8825
    )
    default String combatLocation() {
        return "AUTO";
    }

    @ConfigItem(
            keyName = "combatCenters",
            name = "Combat centers",
            description = "Combat area centers (CombatBot-format). Rechtsklik als menu aan.",
            position = 883
    )
    default String combatCenters() {
        return AreaCenters.DEFAULT_COMBAT;
    }

    @ConfigItem(
            keyName = "combatCentersMenuEnabled",
            name = "Rechtermenu: Combat centers",
            description = "Choose-option voor combat centers",
            position = 884
    )
    default boolean combatCentersMenuEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "showCombatAreaOverlay",
            name = "Combat area overlay",
            description = "Toon combat center-zones op de ground",
            position = 885
    )
    default boolean showCombatAreaOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "miningLocation",
            name = "Mining locatie",
            description = "AUTO of vaste spot uit miningCenters (voor later mining-script)",
            position = 8855
    )
    default String miningLocation() {
        return "AUTO";
    }

    @ConfigItem(
            keyName = "miningCenters",
            name = "Mining centers",
            description = "Mining area centers (CombatBot-format). Rechtsklik als menu aan.",
            position = 886
    )
    default String miningCenters() {
        return AreaCenters.DEFAULT_MINING;
    }

    @ConfigItem(
            keyName = "miningCentersMenuEnabled",
            name = "Rechtermenu: Mining centers",
            description = "Choose-option voor mining centers",
            position = 887
    )
    default boolean miningCentersMenuEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "showMiningAreaOverlay",
            name = "Mining area overlay",
            description = "Toon mining center-zones op de ground",
            position = 888
    )
    default boolean showMiningAreaOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "fishingLocation",
            name = "Fishing locatie",
            description = "AUTO = per fishing-level (Draynor/Barbarian); of vaste spot uit fishingCenters",
            position = 8885
    )
    default String fishingLocation() {
        return "AUTO";
    }

    @ConfigItem(
            keyName = "fishingCenters",
            name = "Fishing centers",
            description = "Fishing area centers (CombatBot-format). Rechtsklik als menu aan.",
            position = 889
    )
    default String fishingCenters() {
        return AreaCenters.DEFAULT_FISHING;
    }

    @ConfigItem(
            keyName = "fishingCentersMenuEnabled",
            name = "Rechtermenu: Fishing centers",
            description = "Choose-option voor fishing centers",
            position = 890
    )
    default boolean fishingCentersMenuEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "showFishingAreaOverlay",
            name = "Fishing area overlay",
            description = "Toon fishing center-zones op de ground",
            position = 891
    )
    default boolean showFishingAreaOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "fishingEnabled",
            name = "Fishing",
            description = "Fishing: Draynor Net/Bait, Barbarian Lure, bank, GE restock, cooking",
            position = 892
    )
    default boolean fishingEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "fishingUseSpecificMethod",
            name = "Specifieke methode",
            description = "Uit = auto per level/locatie; Aan = spot naam + actie",
            position = 893
    )
    default boolean fishingUseSpecificMethod() {
        return false;
    }

    @ConfigItem(
            keyName = "fishingSpotName",
            name = "Spot naam",
            description = "Naam van de fishing spot NPC",
            position = 894
    )
    default String fishingSpotName() {
        return "Fishing spot";
    }

    @ConfigItem(
            keyName = "fishingAction",
            name = "Vis actie",
            description = "Net, Bait, Lure, Cage, Harpoon",
            position = 895
    )
    default String fishingAction() {
        return "Net";
    }

    @ConfigItem(
            keyName = "fishingDropFish",
            name = "Vis droppen",
            description = "Drop vis i.p.v. banken (als cooking uit staat)",
            position = 896
    )
    default boolean fishingDropFish() {
        return true;
    }

    
    @ConfigItem(
            keyName = "fishClueSolver",
            name = "Clues doen",
            description = "Clue bottle/scroll → solver; daarna Fishing hervatten",
            position = 920
    )
    default boolean fishClueSolver() {
        return false;
    }

@ConfigItem(
            keyName = "fishingCookEnabled",
            name = "Fishing cooking",
            description = "Kook vis bij een vuur voordat je dropt/bankt",
            position = 897
    )
    default boolean fishingCookEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "fishingRestockEnabled",
            name = "Fishing bait restock (GE)",
            description = "Koop bait/tool bij de Grand Exchange als bank leeg is (GE nog niet klaar — default uit)",
            position = 898
    )
    default boolean fishingRestockEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "fishingRestockAmount",
            name = "Restock hoeveelheid",
            description = "Aantal bait/feathers kopen (max 1000)",
            position = 899
    )
    default int fishingRestockAmount() {
        return 1000;
    }

    @ConfigItem(
            keyName = "fishingBaitMin",
            name = "Bait min (bank onder)",
            description = "Ga banken als bait onder dit aantal is",
            position = 900
    )
    default int fishingBaitMin() {
        return 50;
    }

    @ConfigItem(
            keyName = "fishingBaitPrice",
            name = "Max prijs per Bait (gp)",
            description = "Max GP per Fishing bait bij GE-restock",
            position = 901
    )
    default int fishingBaitPrice() {
        return 5;
    }

    @ConfigItem(
            keyName = "fishingFeatherPrice",
            name = "Max prijs per Feather (gp)",
            description = "Max GP per Feather bij GE-restock",
            position = 902
    )
    default int fishingFeatherPrice() {
        return 3;
    }

    @ConfigItem(
            keyName = "fishingUseVarrockTeleport",
            name = "Varrock teleport naar GE",
            description = "Varrock teleport gebruiken om naar de GE te reizen",
            position = 903
    )
    default boolean fishingUseVarrockTeleport() {
        return true;
    }

    @ConfigItem(
            keyName = "fishingInteractDelayMin",
            name = "Fishing delay min (ms)",
            description = "Minimale delay tussen fishing-interacties",
            position = 904
    )
    default int fishingInteractDelayMin() {
        return 0;
    }

    @ConfigItem(
            keyName = "fishingInteractDelayMax",
            name = "Fishing delay max (ms)",
            description = "Maximale delay tussen fishing-interacties",
            position = 905
    )
    default int fishingInteractDelayMax() {
        return 0;
    }

    @ConfigItem(
            keyName = "fishDebugOverlay",
            name = "Fishing debug overlay",
            description = "Fishing zones op de ground. Status-panel blijft via Display Overlay.",
            position = 906
    )
    default boolean fishDebugOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "starMinerEnabled",
            name = "Star Miner",
            description = "Shooting stars: Discord + OSRS Portal, world hop, lopen, Mine",
            position = 910
    )
    default boolean starMinerEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "starMinerMinTier",
            name = "Star min tier",
            description = "Negeer feed-sterren onder deze tier (T1 is vaak al weg). Default 2.",
            position = 911
    )
    @Range(min = 1, max = 9)
    default int starMinerMinTier() {
        return 2;
    }

    @ConfigItem(
            keyName = "starMinerMaxTier",
            name = "Star max tier",
            description = "Hoogste ster-tier om te minen (1-9)",
            position = 912
    )
    default int starMinerMaxTier() {
        return 9;
    }

    @ConfigItem(
            keyName = "starMinerAvoidWilderness",
            name = "Star: geen wilderness",
            description = "Sla wilderness-crash sites over",
            position = 913
    )
    default boolean starMinerAvoidWilderness() {
        return true;
    }

    @ConfigItem(
            keyName = "starMinerF2pOnly",
            name = "Star: alleen F2P",
            description = "Alleen F2P-werelden én F2P-plekken: hop er niet heen én verberg ze in de live-lijst. Uit = members zichtbaar.",
            position = 914
    )
    default boolean starMinerF2pOnly() {
        return true;
    }

    @ConfigItem(
            keyName = "starMinerHopEnabled",
            name = "Star: world hop",
            description = "Hop naar de wereld van de gekozen ster",
            position = 915
    )
    default boolean starMinerHopEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "starMinerWaitForLevel",
            name = "Star: wacht bij te hoge ster",
            description = "Verouderd — gebruik 'Wacht extra lagen'. True als extra lagen > 0.",
            position = 916,
            hidden = true
    )
    default boolean starMinerWaitForLevel() {
        return true;
    }

    @ConfigItem(
            keyName = "starMinerWaitTiersAbove",
            name = "Star: wacht extra lagen",
            description = "0 = alleen sterren die je nu kunt minen. 1 = T5-miner wacht bij T6, niet T7+. 8 = alle te hoge. Binnen de cap: hoogste minebare; anders dichtst-bij-lvl wachten.",
            position = 917
    )
    @Range(min = 0, max = 8)
    default int starMinerWaitTiersAbove() {
        return 1;
    }

    @ConfigItem(
            keyName = "starMinerWaitActivity",
            name = "Star: tijdens wachten",
            description = "Bij te hoge ster: niets, High Alchemy, mine of woodcut in de buurt. Klikt niet op de ster tot Mining klopt.",
            position = 918
    )
    default StarWaitActivity starMinerWaitActivity() {
        return StarWaitActivity.NONE;
    }

    @ConfigItem(
            keyName = "starMinerParkSkill",
            name = "Star: geen minebare tier → skill",
            description = "Geen T die je nu aankunt (alleen T5 terwijl jij T2 bent): pauzeer Star, start deze skill. Feed minebaar → Star + gear prep. Niet = blijf wachten / te hoge ster.",
            position = 9181
    )
    default StarParkSkill starMinerParkSkill() {
        return StarParkSkill.NONE;
    }

    enum StarParkSkill {
        NONE("Niet (Star blijft wachten)"),
        WOODCUTTING("Woodcutting"),
        FISHING("Fishing"),
        IMP("Imp Killer"),
        IMP2("Imps2"),
        COW("Cows"),
        GIANTS("Giants"),
        MONK("Monks");

        private final String label;

        StarParkSkill(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum StarWaitActivity {
        NONE("Niets (wacht)"),
        HIGH_ALCH("High Alchemy"),
        MINE("Mine in de buurt"),
        WOODCUT("Woodcut in de buurt");

        private final String label;

        StarWaitActivity(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigItem(
            keyName = "starMinerTravelMode",
            name = "Star: travel-mode",
            description = "Hoe naar de ster lopen. 11 = WorldWalker tot ≤30, daarna Mine-COS. Andere modes ongewijzigd.",
            position = 9185
    )
    default StarTravelModeOpt starMinerTravelMode() {
        return StarTravelModeOpt.FATAL_ONLY;
    }

    @ConfigItem(
            keyName = "starMinerWorldWalkerTravel",
            name = "Star: travel via WorldWalker",
            description = "Default uit. Aan: WorldWalker tot ≤30 tegels (Star/Imp/WC pauzeren, random events blijven). Daarna Mine-COS. Hop/tele/bank eerst.",
            position = 9186
    )
    default boolean starMinerWorldWalkerTravel() {
        return false;
    }

    enum StarTravelModeOpt {
        WALK_ONLY("1 Walk-only"),
        PRE_TICK("2 Walk vóór andere logica"),
        BACKGROUND("3 Imp-walk elke tick"),
        IMP_CLONE("4 ImpWalk"),
        FATAL_ONLY("5 Fatale-only + Mine≤30"),
        INTERLEAVE_3_1("6 Interleave 3×walk"),
        FORCE_IF_MOVING("7 Force walk als moving"),
        SHORT_HOPS("8 API-walker"),
        LARGE_HOPS("9 API-walker"),
        INTERACT_THEN_WALK("10 Mine-on-sight anders walk"),
        WORLD_WALKER("11 WorldWalker tot ≤30");

        private final String label;

        StarTravelModeOpt(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigItem(
            keyName = "starMinerTeleportsEnabled",
            name = "Star: teleports",
            description = "F2P teles (Varrock/Lumb/Fally). Air staff of air runes. Home Teleport als Lumb-spell niet kan. Geen runes → lopen.",
            position = 9187
    )
    default boolean starMinerTeleportsEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "starMinerBankGemsEnabled",
            name = "Star: gems banken",
            description = "Uncut gems naar de bank — alleen vóór/na hop of bij start, niet tijdens minen.",
            position = 9188
    )
    default boolean starMinerBankGemsEnabled() {
        return false;
    }

    
    @ConfigItem(
            keyName = "starClueSolver",
            name = "Clues doen",
            description = "Clue geode/scroll → solver; daarna Star hervatten",
            position = 1210
    )
    default boolean starClueSolver() {
        return false;
    }

@ConfigItem(
            keyName = "starMinerBankGemsAt",
            name = "Star: gems banken vanaf",
            description = "Bank als je zoveel uncut gems hebt (1–28).",
            position = 9189
    )
    @Range(min = 1, max = 28)
    default int starMinerBankGemsAt() {
        return 10;
    }

    @ConfigItem(
            keyName = "starMinerJsonUrl",
            name = "Star JSON-feed URL",
            description = "Optionele extra GET-JSON naast Discord/Portal. Mag leeg.",
            position = 919
    )
    default String starMinerJsonUrl() {
        return "";
    }

    @ConfigItem(
            keyName = "starDebugOverlay",
            name = "Star debug overlay",
            description = "Star ground-overlays. Status-panel blijft via Display Overlay.",
            position = 920
    )
    default boolean starDebugOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "giantsKillerEnabled",
            name = "Giants",
            description = "Hill Giants Edgeville Dungeon (F2P)",
            position = 925
    )
    default boolean giantsKillerEnabled() {
        return false;
    }

    
    @ConfigItem(
            keyName = "giantsClueSolver",
            name = "Clues doen",
            description = "Clue scroll/container → solver; daarna Giants hervatten",
            position = 1310
    )
    default boolean giantsClueSolver() {
        return false;
    }

@ConfigItem(
            keyName = "giantsCombatStyle",
            name = "Giants combat style",
            description = "MELEE / RANGED / MAGE",
            position = 926
    )
    default com.lonebot.example.giants.GiantsTypes.GiantsCombatStyle giantsCombatStyle() {
        return com.lonebot.example.giants.GiantsTypes.GiantsCombatStyle.MELEE;
    }

    @ConfigItem(
            keyName = "giantsMageSpell",
            name = "Giants mage spell",
            description = "Spell bij MAGE",
            position = 927
    )
    default com.lonebot.example.giants.GiantsTypes.GiantsMageSpell giantsMageSpell() {
        return com.lonebot.example.giants.GiantsTypes.GiantsMageSpell.WIND_STRIKE;
    }

    @ConfigItem(
            keyName = "giantsLootItems",
            name = "Giants loot",
            description = "Komma-gescheiden lootlijst (plus defaults)",
            position = 928
    )
    default String giantsLootItems() {
        return com.lonebot.example.giants.GiantsLocations.DEFAULT_LOOT_CSV;
    }

    @ConfigItem(
            keyName = "giantsEatPercent",
            name = "Giants: eten onder HP %",
            description = "Eet/drink als HP op of onder dit percentage is",
            position = 929
    )
    @Range(min = 5, max = 90)
    default int giantsEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "giantsFoodAmount",
            name = "Giants: food uit bank",
            description = "Doel-aantal food na bank-withdraw",
            position = 930
    )
    @Range(min = 1, max = 28)
    default int giantsFoodAmount() {
        return 10;
    }

    @ConfigItem(
            keyName = "giantsFoodBankThreshold",
            name = "Giants: food-bank drempel",
            description = "Eigen food-bank: banken als food-count onder deze drempel is",
            position = 931
    )
    @Range(min = 1, max = 20)
    default int giantsFoodBankThreshold() {
        return 1;
    }

    @ConfigItem(
            keyName = "giantsDebugOverlay",
            name = "Giants overlay",
            description = "Giants status-overlay tijdens hunt (verborgen bij bank/GE)",
            position = 932
    )
    default boolean giantsDebugOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "wcUseSpecificTree",
            name = "WC specifieke boom",
            description = "Uit = beste boom voor WC-niveau; Aan = alleen 'Boom naam'",
            position = 89
    )
    default boolean wcUseSpecificTree() {
        return false;
    }

    @ConfigItem(
            keyName = "wcTreeName",
            name = "WC boom naam",
            description = "Bijv. Willow, Oak, Yew (als specifieke boom aan)",
            position = 90
    )
    default String wcTreeName() {
        return "Willow";
    }

    @ConfigItem(
            keyName = "wcGeAxeRestockEnabled",
            name = "WC axe via GE",
            description = "Bijtekopen van bijl via Grand Exchange (nog niet klaar — default uit)",
            position = 91
    )
    default boolean wcGeAxeRestockEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "wcBankLumbridge",
            name = "WC bank Lumbridge",
            description = "Lumbridge castle-bank toestaan (uit = alleen andere aangevinkte banken)",
            position = 911
    )
    default boolean wcBankLumbridge() {
        return true;
    }

    @ConfigItem(
            keyName = "wcBankDraynor",
            name = "WC bank Draynor",
            description = "Draynor-bank toestaan (uit = alleen andere aangevinkte banken)",
            position = 912
    )
    default boolean wcBankDraynor() {
        return true;
    }

    @ConfigItem(
            keyName = "wcInteractDelayMin",
            name = "WC delay min (ms)",
            description = "Minimale delay tussen WC-interacties",
            position = 92
    )
    @Range(min = 0, max = 5000)
    default int wcInteractDelayMin() {
        return 200;
    }

    @ConfigItem(
            keyName = "wcInteractDelayMax",
            name = "WC delay max (ms)",
            description = "Maximale delay tussen WC-interacties",
            position = 93
    )
    @Range(min = 0, max = 10000)
    default int wcInteractDelayMax() {
        return 600;
    }

    @ConfigItem(
            keyName = "showWcOverlay",
            name = "WC overlay",
            description = "Toon WC overlay (zelfde als WC debug overlay)",
            position = 94
    )
    default boolean showWcOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "statusOverlay",
            name = "Status overlay",
            description = "Toon LoneBot control panel (CombatBotPaint-stijl) op canvas",
            position = 5
    )
    default boolean statusOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "paintOverlayMinimized",
            name = "Control panel geminimaliseerd",
            description = "Alleen goud chip tonen (klik chip om te togglen)",
            position = 6
    )
    default boolean paintOverlayMinimized() {
        return false;
    }

    @ConfigItem(
            keyName = "mouseDebugOverlay",
            name = "Muis debug",
            description = "Toon muis-kruisje + target op canvas (los van status overlay)",
            position = 7
    )
    default boolean mouseDebugOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "paintShowProgress",
            name = "Paint: voortgang",
            description = "XP / XP-hr sectie op control panel",
            position = 8
    )
    default boolean paintShowProgress() {
        return true;
    }

    @ConfigItem(
            keyName = "paintShowTarget",
            name = "Paint: doel",
            description = "Center / bestemming sectie op control panel",
            position = 9
    )
    default boolean paintShowTarget() {
        return true;
    }

    @ConfigItem(
            keyName = "paintShowAction",
            name = "Paint: actie",
            description = "State / stil / anim sectie op control panel",
            position = 10
    )
    default boolean paintShowAction() {
        return true;
    }

    @ConfigItem(
            keyName = "paintShowKit",
            name = "Paint: kit / event",
            description = "Forestry kit / FM / bonfire regels (WC)",
            position = 11
    )
    default boolean paintShowKit() {
        return false;
    }

    @ConfigItem(
            keyName = "paintShowBankSupply",
            name = "Paint: bank / supply",
            description = "Bank-snap / inv regels op control panel",
            position = 12
    )
    default boolean paintShowBankSupply() {
        return false;
    }

    @ConfigItem(
            keyName = "paintShowAntiBan",
            name = "Paint: anti-ban",
            description = "Laatste anti-ban actie op control panel",
            position = 13
    )
    default boolean paintShowAntiBan() {
        return false;
    }

    @ConfigItem(
            keyName = "paintShowScriptDebug",
            name = "Paint: script debug details",
            description = "Extra script-debugLines (FM-diag, sticky, pref, …) op control panel",
            position = 14
    )
    default boolean paintShowScriptDebug() {
        return false;
    }

    // ---- DevTools: IDs / widgets / ground (CombatBot-port) ----

    @ConfigItem(
            keyName = "devWidgetHoverTooltip",
            name = "Widget hover tooltip",
            description = "Toon widget-info (iface, id, text, actions) onder de muis",
            position = 70
    )
    default boolean devWidgetHoverTooltip() {
        return false;
    }

    @ConfigItem(
            keyName = "devWidgetHoverHighlight",
            name = "Widget hover highlight",
            description = "Highlight de widget onder de muis (paars kader)",
            position = 71
    )
    default boolean devWidgetHoverHighlight() {
        return false;
    }

    @ConfigItem(
            keyName = "devInventoryItemIds",
            name = "Inventory item-IDs",
            description = "Teken item-ID op elk inventory-vakje",
            position = 72
    )
    default boolean devInventoryItemIds() {
        return false;
    }

    @ConfigItem(
            keyName = "devNpcIds",
            name = "NPC IDs",
            description = "Teken NPC-naam + id + index in de scene",
            position = 73
    )
    default boolean devNpcIds() {
        return false;
    }

    @ConfigItem(
            keyName = "devObjectIds",
            name = "Object IDs",
            description = "Teken game-object IDs in de scene",
            position = 74
    )
    default boolean devObjectIds() {
        return false;
    }

    @ConfigItem(
            keyName = "devGroundItemIds",
            name = "Ground item IDs",
            description = "Teken ground-item IDs in de scene",
            position = 75
    )
    default boolean devGroundItemIds() {
        return false;
    }

    @ConfigItem(
            keyName = "devObjectIdFilterEnabled",
            name = "Object-ID filter",
            description = "Verberg object-IDs die in de filterlijst staan",
            position = 78
    )
    default boolean devObjectIdFilterEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "devObjectIdFilterHideUnnamed",
            name = "Verberg naamloze objecten (null)",
            description = "Verberg scenery zonder naam (null oid=…). Ook tijdens Alt — geen null-spam meer.",
            position = 79
    )
    default boolean devObjectIdFilterHideUnnamed() {
        return true;
    }

    @ConfigItem(
            keyName = "devObjectIdFilterAltEdit",
            name = "Alt: object-ID +/− / klik filter",
            description = "Alt ingedrukt: highlight IDs met + en −. Alt+linksklik voegt ID toe aan de filter.",
            position = 80
    )
    default boolean devObjectIdFilterAltEdit() {
        return true;
    }

    @ConfigItem(
            keyName = "devObjectIdFilter",
            name = "Object-ID filterlijst",
            description = "CSV van object-IDs om te verbergen. Geen maximum. Standaard 7120–7126 (naamloze mijn-scenery).",
            position = 81
    )
    default String devObjectIdFilter() {
        return "7120,7121,7122,7123,7124,7125,7126";
    }

    @ConfigItem(
            keyName = "devHoverCaptureMiddleClick",
            name = "Middenklik → Developer Tools",
            description = "Middenmuisklik zet widget/NPC/object/tegel in de Developer Tools capture-log",
            position = 76
    )
    default boolean devHoverCaptureMiddleClick() {
        return true;
    }

    @ConfigItem(
            keyName = "walkObstacleScanEnabled",
            name = "Scan deuren/hekken/ladders",
            description = "Achtergrond: sla deuren, hekken, stiles, ladders en trappen op terwijl je erlangs loopt (~/.lonebot/walk-obstacle-*.tsv)",
            position = 82
    )
    default boolean walkObstacleScanEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "walkUseShortcuts",
            name = "Walk: shortcuts (stile)",
            description = "Aan = stiles/shortcuts nemen. Uit = eromheen lopen, niet blijven klikken.",
            position = 83
    )
    default boolean walkUseShortcuts() {
        return false;
    }

    @ConfigItem(
            keyName = "devWidgetDumpFilter",
            name = "Widget dump filter",
            description = "Substring-filter voor Dump widgets (leeg = alles interessant)",
            position = 77
    )
    default String devWidgetDumpFilter() {
        return "";
    }

    @ConfigItem(
            keyName = "credentialsHint",
            name = "Credentials hint",
            description = "Plak JX_* credentials in het LoneBot sidebar-paneel (Accounts).",
            position = 7
    )
    default String credentialsHint() {
        return "Open LoneBot-paneel (sidebar icoon) → plak JX_DISPLAY_NAME / JX_CHARACTER_ID / JX_SESSION_ID";
    }

    // ---- Walk click strategies (test toggles) ----

    @ConfigItem(
            keyName = "walkUseMinimap",
            name = "Walk: minimap",
            description = "Lopen via minimap-klik (voorkeur — veiligste)",
            position = 10
    )
    default boolean walkUseMinimap() {
        return false;
    }

    @ConfigItem(
            keyName = "walkMinimapFullyZoomedOut",
            name = "Walk: minimap volledig uit",
            description = "Minimap helemaal uitzoomen zodat walk-kliks niet op de rand vallen",
            position = 16
    )
    default boolean walkMinimapFullyZoomedOut() {
        return true;
    }

    @ConfigItem(
            keyName = "walkUseCanvas",
            name = "Walk: canvas",
            description = "Lopen via grond-klik op het spelvenster (alleen UI-safe als zones aan)",
            position = 11
    )
    default boolean walkUseCanvas() {
        return false;
    }

    @ConfigItem(
            keyName = "walkUseUiZones",
            name = "Walk: UI-zones",
            description = "Geen canvas-klik over chat / inventory / minimap-chrome",
            position = 12
    )
    default boolean walkUseUiZones() {
        return true;
    }

    @ConfigItem(
            keyName = "walkUseCameraNudge",
            name = "Walk: camera nudge",
            description = "Camera draaien als canvas-klik geblokkeerd is",
            position = 13
    )
    default boolean walkUseCameraNudge() {
        return true;
    }

    @ConfigItem(
            keyName = "walkUseInvoke",
            name = "Walk: invoke WALK",
            description = "Client.menuAction(WALK) zonder muis (vanilla RL)",
            position = 14
    )
    default boolean walkUseInvoke() {
        return true;
    }

    @ConfigItem(
            keyName = "walkFarCanvas",
            name = "Walk: far canvas",
            description = "Lage camera + yaw naar pad + canvas-first zo ver mogelijk",
            position = 15
    )
    default boolean walkFarCanvas() {
        return false;
    }

    // ---- Walk camera (tunable) ----

    @ConfigItem(
            keyName = "walkCamEnabled",
            name = "Walk-camera aan",
            description = "Voor elke walk: yaw/pitch/zoom volgens instellingen",
            position = 40
    )
    default boolean walkCamEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "walkCamHumanMmb",
            name = "Walk-camera MMB (menselijk)",
            description = "Vloeiende middle-mouse drag zoals anti-ban i.p.v. harde snap (uit als toetsenbord aan)",
            position = 40
    )
    default boolean walkCamHumanMmb() {
        return true;
    }

    @ConfigItem(
            keyName = "walkCamUseKeyboard",
            name = "Walk-camera toetsenbord",
            description = "Pijltjestoetsen ←→↑↓ i.p.v. MMB voor yaw/pitch (zoom blijft scroll). Stabieler.",
            position = 40
    )
    default boolean walkCamUseKeyboard() {
        return false;
    }

    @ConfigItem(
            keyName = "walkCamYawMode",
            name = "Walk-camera yaw",
            description = "FOLLOW=kijk looprichting, CONTRA=omgekeerd, OFFSET=follow+graden",
            position = 41
    )
    default String walkCamYawMode() {
        return "FOLLOW";
    }

    @ConfigItem(
            keyName = "walkCamYawOffsetDeg",
            name = "Walk-camera yaw offset °",
            description = "Extra draai t.o.v. looprichting (−180…180)",
            position = 42
    )
    @Range(min = -180, max = 180)
    default int walkCamYawOffsetDeg() {
        return 0;
    }

    @ConfigItem(
            keyName = "walkCamPitch",
            name = "Walk-camera pitch",
            description = "Hoogte/hoek (client getCameraPitch). Default 3064 = gemeten loop-hoek",
            position = 43
    )
    @Range(min = 128, max = 4096)
    default int walkCamPitch() {
        return 3064;
    }

    @ConfigItem(
            keyName = "walkCamPitchBand",
            name = "Walk-camera pitch band",
            description = "Toegestane afwijking rond pitch",
            position = 44
    )
    @Range(min = 0, max = 400)
    default int walkCamPitchBand() {
        return 30;
    }

    @ConfigItem(
            keyName = "walkCamVisibilitySkip",
            name = "Walk-camera skip bij zicht %",
            description = "Pas skippen als face% ≥ dit (75≈max ~45° scheef). Te laag (23) = camera blijft scheef.",
            position = 45
    )
    @Range(min = 50, max = 95)
    default int walkCamVisibilitySkip() {
        return 70;
    }

    @ConfigItem(
            keyName = "walkCamZoomPercent",
            name = "Walk-camera zoom %",
            description = "0=volledig uit … 100=sterk in (stap 1)",
            position = 46
    )
    @Range(min = 0, max = 100)
    default int walkCamZoomPercent() {
        return 5;
    }

    @ConfigItem(
            keyName = "walkCamZoomScaleMax",
            name = "Walk-camera zoom max-scale (legacy)",
            description = "Oud: gebruik walkCamZoomPercent. Lager=verder uit.",
            position = 47
    )
    @Range(min = 180, max = 900)
    default int walkCamZoomScaleMax() {
        return 320;
    }

    @ConfigItem(
            keyName = "walkStepMin",
            name = "Walk stap min (tiles)",
            description = "Minimale pad-stap in tegels (kortste route A→B). Default 6.",
            position = 16
    )
    @Range(min = 3, max = 25)
    default int walkStepMin() {
        return 6;
    }

    @ConfigItem(
            keyName = "walkStepMax",
            name = "Walk stap max (tiles)",
            description = "Maximale pad-stap in tegels. Default 12. Volgende hop vóór de gele flag.",
            position = 17
    )
    @Range(min = 5, max = 40)
    default int walkStepMax() {
        return 12;
    }

    @ConfigItem(
            keyName = "walkForceLargeSteps",
            name = "Walk: grote stappen 15-20",
            description = "Forceer stapbereik 15–20 tiles (Storm2 impsForceLargeSteps)",
            position = 18
    )
    default boolean walkForceLargeSteps() {
        return false;
    }

    @ConfigItem(
            keyName = "walkReclickMs",
            name = "Walk reclick (ms)",
            description = "Basis min. tijd tussen walk-clicks (ver van de flag)",
            position = 19
    )
    @Range(min = 150, max = 5000)
    default int walkReclickMs() {
        return 700;
    }

    @ConfigItem(
            keyName = "walkFlagProximityReclick",
            name = "Walk: langer wachten dicht bij flag",
            description = "Hoe dichter bij de gele flag, hoe langer wachten vóór opnieuw klikken",
            position = 20
    )
    default boolean walkFlagProximityReclick() {
        return true;
    }

    @ConfigItem(
            keyName = "walkReclickNearFlagMs",
            name = "Walk reclick dicht bij flag (ms)",
            description = "Reclick-interval als je bijna bij de flag bent",
            position = 21
    )
    @Range(min = 400, max = 5000)
    default int walkReclickNearFlagMs() {
        return 1600;
    }

    @ConfigItem(
            keyName = "walkFinalClickTiles",
            name = "Eindklik binnen (pad-tegels)",
            description = "Laatste N tegels op het pad: click-on-sight op B. Mislukt → 5 padtegels verder. Niet hemelsbreed.",
            position = 18
    )
    @Range(min = 8, max = 40)
    default int walkFinalClickTiles() {
        return 15;
    }

    @ConfigItem(
            keyName = "walkTwinQuickClicks",
            name = "Walk: 2 snelle kliks",
            description = "Twee snelle kliks: eerst de hop, tweede de volgende tegel (niet dezelfde — dat zet de gele flag uit)",
            position = 22
    )
    default boolean walkTwinQuickClicks() {
        return true;
    }

    @ConfigItem(
            keyName = "walkAutoRun",
            name = "Auto-run",
            description = "Zet run weer aan vanaf Auto-run drempel (OSRS orb gaat uit bij 0%)",
            position = 26
    )
    default boolean walkAutoRun() {
        return true;
    }

    @ConfigItem(
            keyName = "walkAutoRunMinEnergy",
            name = "Auto-run vanaf %",
            description = "Run-orb aanklikken vanaf deze energie (standaard 20)",
            position = 27
    )
    @Range(min = 5, max = 80)
    default int walkAutoRunMinEnergy() {
        return 20;
    }

    @ConfigItem(
            keyName = "walkPostClickDelayMin",
            name = "Walk delay na click min",
            description = "Pauze na een walk-klik (min ms)",
            position = 23
    )
    @Range(min = 0, max = 2000)
    default int walkPostClickDelayMin() {
        return 60;
    }

    @ConfigItem(
            keyName = "walkPostClickDelayMax",
            name = "Walk delay na click max",
            description = "Pauze na een walk-klik (max ms)",
            position = 24
    )
    @Range(min = 0, max = 3000)
    default int walkPostClickDelayMax() {
        return 140;
    }

    @ConfigItem(
            keyName = "walkChainClickMin",
            name = "Hop-doorlink min (ms)",
            description = "Korte pauze vóór de volgende hop als je dicht bij de gele vlag bent",
            position = 28
    )
    @Range(min = 20, max = 800)
    default int walkChainClickMin() {
        return 70;
    }

    @ConfigItem(
            keyName = "walkChainClickMax",
            name = "Hop-doorlink max (ms)",
            description = "Max. pauze vóór de volgende hop bij doorlinken",
            position = 29
    )
    @Range(min = 20, max = 1500)
    default int walkChainClickMax() {
        return 220;
    }

    @ConfigItem(
            keyName = "canvasDebugOverlay",
            name = "Canvas debug",
            description = "Teken city-ring markers op de game-canvas",
            position = 25
    )
    default boolean canvasDebugOverlay() {
        return true;
    }

    // ---- Anti-ban (Storm/CombatBot port) ----

    @ConfigItem(keyName = "antiBanEnabled", name = "Anti-ban", description = "Periodieke camera/idle/muis/tab acties", position = 30)
    default boolean antiBanEnabled() { return true; }

    @ConfigItem(keyName = "antiBanCamera", name = "AB: camera", description = "Pijltjes + MMB camera-drag", position = 31)
    default boolean antiBanCamera() { return true; }

    @ConfigItem(keyName = "antiBanIdle", name = "AB: idle pauzes", description = "Korte/middel/lange pauzes", position = 32)
    default boolean antiBanIdle() { return true; }

    @ConfigItem(keyName = "antiBanMouse", name = "AB: random muis", description = "Af en toe muis bewegen / hover", position = 33)
    default boolean antiBanMouse() { return true; }

    @ConfigItem(keyName = "antiBanKeyboardPan", name = "AB: random toetsenbord",
            description = "Achtergrond pijltje links/rechts (beeld draait). Zeldzamer dan muis-fidget.",
            position = 34)
    default boolean antiBanKeyboardPan() { return true; }

    @ConfigItem(keyName = "antiBanMisclick", name = "AB: misclick", description = "Zeldzame misclick op lege canvas", position = 35)
    default boolean antiBanMisclick() { return true; }

    @ConfigItem(keyName = "antiBanTabGlance", name = "AB: tab glance", description = "F-toets tab bekijken + ESC terug", position = 36)
    default boolean antiBanTabGlance() { return true; }

    @ConfigItem(keyName = "antiBanMouseFidget", name = "AB: continuous fidget", description = "Achtergrond micro-muisbewegingen", position = 37)
    default boolean antiBanMouseFidget() { return true; }

    @ConfigItem(keyName = "antiBanFrequencySec", name = "AB: frequentie (sec)", description = "Basis seconden tussen anti-ban acties", position = 38)
    @Range(min = 10, max = 180)
    default int antiBanFrequencySec() { return 40; }

    // ---- Random events ----

    @ConfigItem(
            keyName = "randomEventsEnabled",
            name = "Random events",
            description = "Dismiss events die met jou interacten; Genie Talk-to als lamp-skill ≠ Geen",
            position = 39
    )
    default boolean randomEventsEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "genieLampSkill",
            name = "Genie lamp skill",
            description = "Skill bij Genie lamp (Geen = Genie ook Dismiss)",
            position = 40
    )
    default GenieLampSkill genieLampSkill() {
        return GenieLampSkill.NONE;
    }

    enum GenieLampSkill {
        NONE("Geen"),
        ATTACK("Attack"),
        STRENGTH("Strength"),
        DEFENCE("Defence"),
        HITPOINTS("Hitpoints"),
        RANGED("Ranged"),
        PRAYER("Prayer"),
        MAGIC("Magic"),
        COOKING("Cooking"),
        WOODCUTTING("Woodcutting"),
        FLETCHING("Fletching"),
        FISHING("Fishing"),
        FIREMAKING("Firemaking"),
        CRAFTING("Crafting"),
        SMITHING("Smithing"),
        MINING("Mining"),
        HERBLORE("Herblore"),
        AGILITY("Agility"),
        THIEVING("Thieving"),
        SLAYER("Slayer"),
        FARMING("Farming"),
        RUNECRAFT("Runecraft"),
        HUNTER("Hunter"),
        CONSTRUCTION("Construction");

        private final String label;

        GenieLampSkill(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        /** Key voor RandomEventSettings (NONE / ATTACK / …). */
        public String settingsKey() {
            return name();
        }
    }

    // ---- Imp Killer (Karamja core) ----

    @ConfigItem(keyName = "impKillerEnabled", name = "Imp Killer", description = "Karamja imps: jagen, loot, boat, deposit, restock", position = 40)
    default boolean impKillerEnabled() { return false; }

    @ConfigItem(
            keyName = "impDebugOverlay",
            name = "Imp debug overlay",
            description = "Imp hunt/rally/zones op de ground. Status-panel blijft via Display Overlay.",
            position = 41
    )
    default boolean impDebugOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "wcDebugOverlay",
            name = "WC debug overlay",
            description = "WC zones op de ground. Status-panel blijft via Display Overlay.",
            position = 7
    )
    default boolean wcDebugOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "wcDebugOverlayMinimized",
            name = "WC overlay geminimaliseerd",
            description = "Alleen groene chip; klik de chip om te togglen",
            position = 81
    )
    default boolean wcDebugOverlayMinimized() {
        return false;
    }

    @ConfigItem(
            keyName = "cowDebugOverlay",
            name = "Cow debug overlay",
            description = "Cow combat zones op de ground. Status-panel blijft via Display Overlay.",
            position = 8
    )
    default boolean cowDebugOverlay() {
        return true;
    }

    @ConfigItem(keyName = "impsCombatStyle", name = "Imps combat style", description = "MELEE / RANGED / MAGE", position = 42)
    default com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle impsCombatStyle() {
        return com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle.MELEE;
    }

    @ConfigItem(keyName = "impsMageSpell", name = "Imps mage spell", description = "Spell bij MAGE style", position = 43)
    default com.lonebot.example.imps.ImpsTypes.ImpsMageSpell impsMageSpell() {
        return com.lonebot.example.imps.ImpsTypes.ImpsMageSpell.WIND_STRIKE;
    }

    @ConfigItem(keyName = "impsHuntingX", name = "Imps hunting X", description = "Jachtgebied center X", position = 44)
    default int impsHuntingX() { return 2826; }

    @ConfigItem(keyName = "impsHuntingY", name = "Imps hunting Y", description = "Jachtgebied center Y", position = 45)
    default int impsHuntingY() { return 3181; }

    @ConfigItem(keyName = "impsHuntingRadius", name = "Imps hunting radius", description = "Jachtradius in tiles", position = 46)
    @Range(min = 5, max = 80)
    default int impsHuntingRadius() { return 20; }

    @ConfigItem(
            keyName = "impsNpcId",
            name = "Imp NPC ID",
            description = "Hardcoded 5007 — niet meer in UI",
            position = 47,
            hidden = true
    )
    default int impsNpcId() { return 5007; }

    @ConfigItem(keyName = "impsLootItems", name = "Imps loot items", description = "Komma-gescheiden lootlijst", position = 48)
    default String impsLootItems() {
        return "Black bead,Red bead,Yellow bead,White bead,Mind rune,Mind talisman,Fiendish ashes";
    }

    @ConfigItem(
            keyName = "impsSpecialLootItems",
            name = "Imps speciale loots (direct)",
            description = "Altijd direct oppakken, ook tijdens loot-delay / combat (CSV)",
            position = 481
    )
    default String impsSpecialLootItems() {
        return "Black bead,Red bead,Yellow bead,White bead,Mind talisman";
    }

    @ConfigItem(
            keyName = "impsLootDelayEnabled",
            name = "Imps loot delay (per kills)",
            description = "Blijf killen tot N±1 kills, pak daarna de loot-pile (niet na elke kill)",
            position = 482
    )
    default boolean impsLootDelayEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "impsLootDelayKills",
            name = "Imps kills vóór looten",
            description = "Basis aantal kills vóór normale loot. 0 = na combat. ±1 random bij ≥2.",
            position = 483
    )
    @Range(min = 0, max = 15)
    default int impsLootDelayKills() {
        return 2;
    }

    @ConfigItem(
            keyName = "impsLootPickupMode",
            name = "Imps loot pickup mode",
            description = "AUTO of vaste Take-methode (INTERACT_TAKE / MENU_INVOKE — geen canvas-LMB op bomen)",
            position = 49
    )
    default com.lonebot.example.imps.ImpsTypes.LootPickupMode impsLootPickupMode() {
        return com.lonebot.example.imps.ImpsTypes.LootPickupMode.AUTO;
    }

    @ConfigItem(
            keyName = "impsLootPickupStrict",
            name = "Imps loot method strict",
            description = "Alleen gekozen methode — geen fallbacks (handig om te testen)",
            position = 50
    )
    default boolean impsLootPickupStrict() {
        return false;
    }

    @ConfigItem(keyName = "impsScatterAshes", name = "Imps scatter ashes", description = "Fiendish ashes verstrooien", position = 51)
    default boolean impsScatterAshes() { return true; }

    @ConfigItem(keyName = "impsBankThreshold", name = "Imps bank threshold", description = "Bank/deposit bij dit aantal inv-slots", position = 52)
    @Range(min = 1, max = 28)
    default int impsBankThreshold() { return 20; }

    @ConfigItem(keyName = "impsMinCoins", name = "Imps min coins", description = "Restock als coins hieronder (boot = 30)", position = 53)
    @Range(min = 30, max = 5000)
    default int impsMinCoins() { return 60; }

    @ConfigItem(keyName = "impsAvoidScorpions", name = "Imps avoid scorpions", description = "Vermijd scorpion-zones als jouw cmb te laag is (formule: cmb > NPC×2+1). Hoog genoeg → geen vlucht.", position = 54)
    default boolean impsAvoidScorpions() { return true; }

    @ConfigItem(keyName = "impsScorpionAvoidRadius", name = "Imps scorpion radius", description = "Tiles rond vaste zones én live scorpions — geen loot/hunt", position = 55)
    @Range(min = 1, max = 15)
    default int impsScorpionAvoidRadius() { return 5; }

    @ConfigItem(keyName = "impsAttackScorpions", name = "Imps attack scorpions", description = "Scorpions aanvallen i.p.v. alleen vluchten", position = 56)
    default boolean impsAttackScorpions() { return false; }

    @ConfigItem(keyName = "impsGeSellEnabled", name = "Imps GE sell", description = "Verkoop loot op GE na X bank trips", position = 57)
    default boolean impsGeSellEnabled() { return false; }

    @ConfigItem(keyName = "impsGeSellAfterBanks", name = "Imps GE after banks", description = "GE sell na dit aantal bank trips", position = 58)
    @Range(min = 1, max = 50)
    default int impsGeSellAfterBanks() { return 5; }

    @ConfigItem(keyName = "impsGeSellPrice", name = "Imps GE sell price", description = "Vaste verkoopprijs per item", position = 59)
    default int impsGeSellPrice() { return 999; }

    @ConfigItem(keyName = "impsGeSellItems", name = "Imps GE sell items", description = "CSV te verkopen loot", position = 60)
    default String impsGeSellItems() {
        return "Black bead,Red bead,Yellow bead,White bead,Mind talisman,Fiendish ashes";
    }

    @ConfigItem(keyName = "impsGearPrepEnabled", name = "Imp gear prep", description = "Bank withdraw/equip voor style kit", position = 61)
    default boolean impsGearPrepEnabled() { return true; }

    @ConfigItem(keyName = "impsGeRestockEnabled", name = "Imp GE restock", description = "GE-koop ammo/runes/staff bij tekort (GE nog niet klaar — default uit)", position = 62)
    default boolean impsGeRestockEnabled() { return false; }

    @ConfigItem(keyName = "impsQuestLootEnabled", name = "Imp quest loot", description = "Hammer/cadava/clay/wool oppakken", position = 63)
    default boolean impsQuestLootEnabled() { return true; }

    @ConfigItem(keyName = "impsAshHumanize", name = "Imp ash humanize", description = "Ashes soms overslaan (1/N)", position = 64)
    default boolean impsAshHumanize() { return false; }

    @ConfigItem(keyName = "impsAshHumanizeChance", name = "Imp ash humanize %", description = "Kans om ashes wél te pakken", position = 65)
    @Range(min = 5, max = 100)
    default int impsAshHumanizeChance() { return 35; }

    @ConfigItem(keyName = "impsIdleRoamSeconds", name = "Imp idle roam sec", description = "Min. seconden tussen idle roam", position = 66)
    @Range(min = 3, max = 60)
    default int impsIdleRoamSeconds() { return 12; }

    @ConfigItem(keyName = "impsMeleeOpeningAirStrike", name = "Imp melee Air Strike open", description = "1× Wind Strike vóór melee", position = 67)
    default boolean impsMeleeOpeningAirStrike() { return false; }

    @ConfigItem(keyName = "impsMeleeOpeningAirStrikeMinDistance", name = "Imp melee opener min afstand", description = "Min. tiles voor Air Strike opener", position = 67)
    @Range(min = 1, max = 12)
    default int impsMeleeOpeningAirStrikeMinDistance() { return 3; }

    @ConfigItem(keyName = "impsMeleeOpeningAirStrikeDebug", name = "Imp melee opener debug", description = "Extra console-debug voor melee opener", position = 67)
    default boolean impsMeleeOpeningAirStrikeDebug() { return false; }

    @ConfigItem(keyName = "impsFarmMoneyEnabled", name = "Imp farm money", description = "Farm money via Imps tot min. coins", position = 67)
    default boolean impsFarmMoneyEnabled() { return false; }

    @ConfigItem(keyName = "impsFarmMoneyMinCoins", name = "Imp farm money min coins", description = "Stop farm-money boven dit bedrag", position = 67)
    @Range(min = 1000, max = 100000)
    default int impsFarmMoneyMinCoins() { return 25000; }

    @ConfigItem(keyName = "impsFarmMoneyDumpTarget", name = "Imp farm money dumps", description = "Deposit dumps-doel voor farm-money", position = 67)
    @Range(min = 1, max = 20)
    default int impsFarmMoneyDumpTarget() { return 5; }

    @ConfigItem(keyName = "impsMeleeTrainingStyle", name = "Imp melee attack style", description = "BALANCED / ATTACK / STRENGTH / DEFENCE", position = 67)
    default String impsMeleeTrainingStyle() { return "BALANCED"; }

    @ConfigItem(keyName = "impsAshLootMaxPickupTiles", name = "Imp ash loot max tiles", description = "Max afstand tot ash-stack", position = 67)
    @Range(min = 1, max = 25)
    default int impsAshLootMaxPickupTiles() { return 12; }

    @ConfigItem(keyName = "impsAshLootMinSpacingTiles", name = "Imp ash loot min spacing", description = "Min. spacing tussen ash-stapels", position = 67)
    @Range(min = 1, max = 25)
    default int impsAshLootMinSpacingTiles() { return 3; }

    @ConfigItem(keyName = "impsScorpionLevel", name = "Imp scorpion NPC level", description = "Agro-formule: jouw cmb > NPC×2+1 (default 14 → veilig vanaf cmb 30)", position = 67)
    @Range(min = 1, max = 100)
    default int impsScorpionLevel() { return 14; }

    @ConfigItem(keyName = "impsCompetitorWorldHop", name = "Imp wereld-hop bij concurrent", description = "Hop bij andere imp-jager", position = 67)
    default boolean impsCompetitorWorldHop() { return false; }

    @ConfigItem(keyName = "impsLawRuneBuyPrice", name = "Imp law rune koopprijs", description = "Max GE-prijs law runes", position = 67)
    @Range(min = 1, max = 2000)
    default int impsLawRuneBuyPrice() { return 200; }

    @ConfigItem(keyName = "impsTeleportBuyRunes", name = "Imp teleports: runes bijkopen", description = "GE-koop teleport-runes", position = 67)
    default boolean impsTeleportBuyRunes() { return true; }

    @ConfigItem(keyName = "impsUseVarrockTeleport", name = "Imp Varrock teleport", description = "Varrock teleport gebruiken", position = 67)
    default boolean impsUseVarrockTeleport() { return true; }

    @ConfigItem(keyName = "impsUseFaladorTeleport", name = "Imp Falador teleport", description = "Falador teleport gebruiken", position = 67)
    default boolean impsUseFaladorTeleport() { return false; }

    @ConfigItem(keyName = "impsUseLumbridgeTeleport", name = "Imp Lumbridge teleport", description = "Lumbridge teleport gebruiken", position = 67)
    default boolean impsUseLumbridgeTeleport() { return false; }

    @ConfigItem(keyName = "impsStepMinDistance", name = "Imp loop stap min", description = "Minimale loop-stap (tiles)", position = 67)
    @Range(min = 3, max = 25)
    default int impsStepMinDistance() { return 8; }

    @ConfigItem(keyName = "impsStepMaxDistance", name = "Imp loop stap max", description = "Maximale loop-stap (tiles)", position = 67)
    @Range(min = 5, max = 35)
    default int impsStepMaxDistance() { return 14; }

    @ConfigItem(keyName = "impsRallyPointRadius", name = "Imp rally radius", description = "Rally point radius (tiles)", position = 67)
    @Range(min = 1, max = 50)
    default int impsRallyPointRadius() { return 12; }

    @ConfigItem(keyName = "impsArrivalRadius", name = "Imp arrival radius", description = "Aankomst-radius bij dock/hunt (tiles)", position = 67)
    @Range(min = 8, max = 20)
    default int impsArrivalRadius() { return 12; }

    @ConfigItem(keyName = "impsShowRallyRadius", name = "Imp rally radius overlay", description = "Toon rally radius op canvas", position = 67)
    default boolean impsShowRallyRadius() { return false; }

    @ConfigItem(keyName = "impsGoblinCoinCenterX", name = "Imp goblin coin X", description = "Goblin coin-recovery center X", position = 67)
    @Range(min = 2950, max = 3050)
    default int impsGoblinCoinCenterX() { return 3000; }

    @ConfigItem(keyName = "impsGoblinCoinCenterY", name = "Imp goblin coin Y", description = "Goblin coin-recovery center Y", position = 67)
    @Range(min = 3160, max = 3260)
    default int impsGoblinCoinCenterY() { return 3210; }

    @ConfigItem(keyName = "impsGoblinCoinRadius", name = "Imp goblin coin radius", description = "Goblin coin-recovery radius", position = 67)
    @Range(min = 3, max = 30)
    default int impsGoblinCoinRadius() { return 10; }

    @ConfigItem(keyName = "impsShowGoblinCoinOverlay", name = "Imp goblin coin overlay", description = "Toon goblin coin gebied", position = 67)
    default boolean impsShowGoblinCoinOverlay() { return false; }

    @ConfigItem(keyName = "impsStayInsideRadius", name = "Imp stay inside hunt radius", description = "Blijf binnen jachtradius", position = 67)
    default boolean impsStayInsideRadius() { return true; }

    @ConfigItem(keyName = "impsMageTripCastBudget", name = "Imp mage trip cast budget", description = "Max casts per mage-trip", position = 67)
    @Range(min = 1, max = 200)
    default int impsMageTripCastBudget() { return 40; }

    @ConfigItem(keyName = "impsFallbackStyle", name = "Imp fallback combat style", description = "Fallback als primary niet kan", position = 67)
    default com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle impsFallbackStyle() {
        return com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle.MELEE;
    }

    @ConfigItem(keyName = "impsAmmoRestockPrice", name = "Imp ammo/rune koopprijs", description = "Max GE-prijs ammo/runes", position = 67)
    @Range(min = 1, max = 5000)
    default int impsAmmoRestockPrice() { return 50; }

    @ConfigItem(
            keyName = "showImpsHuntOverlay",
            name = "Imp hunting overlay",
            description = "Teken hunt-union (3 zones) + scorpion zones + rally op de canvas",
            position = 68
    )
    default boolean showImpsHuntOverlay() {
        return true;
    }

    // ---- Tile markers (excluded) ----

    @ConfigItem(
            keyName = "excludedTiles",
            name = "Excluded tiles",
            description = "Gedeeld voor alle accounts (~/.lonebot/excluded-tiles.txt). Intern — X:Y:Z|… via rechtermuisklik",
            position = 70
    )
    default String excludedTiles() { return ""; }

    @ConfigItem(
            keyName = "showExcludedTiles",
            name = "Toon excluded tiles",
            description = "Teken rode X op gemarkeerde tegels",
            position = 71
    )
    default boolean showExcludedTiles() { return true; }

    @ConfigItem(
            keyName = "wallsOverlayEnabled",
            name = "Toon muren & deuren overlay",
            description = "Na Get walls / Get walls (NPC): kamer (groen), talk-buiten (cyaan), deuren (oranje)",
            position = 72
    )
    default boolean wallsOverlayEnabled() { return true; }

    @ConfigItem(
            keyName = "wallsScanRadius",
            name = "Get walls scan-radius",
            description = "Chebyshev-tegelradius voor Get walls flood-fill (4–24). Groter = meer groene tegels.",
            position = 73
    )
    @Range(min = 4, max = 24)
    default int wallsScanRadius() { return 12; }

    // ---- Capture tab ----

    @ConfigItem(
            keyName = "captureLogEnabled",
            name = "Capture-log aan",
            description = "Get walls / coords → Capture-tab console",
            position = 74
    )
    default boolean captureLogEnabled() { return true; }

    @ConfigItem(
            keyName = "captureSkipDuplicates",
            name = "Capture skip duplicaten",
            description = "Sla over als laatste entry hetzelfde detail heeft",
            position = 75
    )
    default boolean captureSkipDuplicates() { return true; }

    @ConfigItem(
            keyName = "captureCoordsEnabled",
            name = "Coördinaten bij capture",
            description = "Middenklik / menu: [coords] entries in Capture-log",
            position = 76
    )
    default boolean captureCoordsEnabled() { return true; }

    @ConfigItem(
            keyName = "captureInspectExtras",
            name = "Middenklik: speler/menu/collision",
            description = "Naast widget/NPC/object: speler-state, menu-opcodes en collision van de tegel",
            position = 77
    )
    default boolean captureInspectExtras() { return true; }

    @ConfigItem(
            keyName = "varWatchEnabled",
            name = "Varbit-watch",
            description = "Log gewijzigde varbits/varps/varcs naar capture-log (filter + noisy-mute)",
            position = 78
    )
    default boolean varWatchEnabled() { return false; }

    @ConfigItem(
            keyName = "varWatchVarbits",
            name = "Watch varbits",
            description = "Varbit-wijzigingen (quest/lock-bits)",
            position = 79
    )
    default boolean varWatchVarbits() { return true; }

    @ConfigItem(
            keyName = "varWatchVarps",
            name = "Watch varps",
            description = "Varp-wijzigingen (server vars)",
            position = 80
    )
    default boolean varWatchVarps() { return true; }

    @ConfigItem(
            keyName = "varWatchVarcs",
            name = "Watch varcs (interface)",
            description = "Client-vars — bank/dialog/UI state",
            position = 81
    )
    default boolean varWatchVarcs() { return true; }

    @ConfigItem(
            keyName = "varWatchSkipNoisy",
            name = "Var-watch: skip noisy",
            description = "Mute IDs die >6×/2s wijzigen (combat/tick-spam)",
            position = 82
    )
    default boolean varWatchSkipNoisy() { return true; }

    @ConfigItem(
            keyName = "varWatchFilterIds",
            name = "Var-watch filter IDs",
            description = "Leeg = alles (na skip noisy). Anders alleen deze: 123, vb:456, vp:173, vc:5",
            position = 83
    )
    default String varWatchFilterIds() { return ""; }

    @ConfigItem(
            keyName = "runeliteDeveloperMode",
            name = "RuneLite developer mode",
            description = "Start met --developer-mode: officiële Developer Tools in de RuneLite-sidebar (bug-icoon). Client herstarten.",
            position = 89
    )
    default boolean runeliteDeveloperMode() {
        return false;
    }

    // ---- Storm Developer Tools ----

    @ConfigItem(keyName = "devDebugLogging", name = "Debug Logging",
            description = "Extra walk/map regels in de Console", position = 90)
    default boolean devDebugLogging() { return false; }

    @ConfigItem(keyName = "devDebugOverlay", name = "Debug Overlay",
            description = "Teken pathfind-pad en world-walk doel op de canvas", position = 91)
    default boolean devDebugOverlay() { return false; }

    @ConfigItem(keyName = "devWidgetDebugging", name = "Widget Debugging",
            description = "Widget hover tooltip + highlight", position = 92)
    default boolean devWidgetDebugging() { return false; }

    @ConfigItem(keyName = "devReachableTiles", name = "Debug Reachable Tiles",
            description = "Flood-fill loopbare tegels vanaf de speler", position = 93)
    default boolean devReachableTiles() { return false; }

    @ConfigItem(keyName = "devHighlightGameObjects", name = "Highlight Game Objects",
            description = "Omlijning van game objects", position = 94)
    default boolean devHighlightGameObjects() { return false; }

    @ConfigItem(keyName = "devHighlightWallObjects", name = "Highlight Wall Objects",
            description = "Omlijning van wall objects (deuren/muren)", position = 95)
    default boolean devHighlightWallObjects() { return false; }

    @ConfigItem(keyName = "devHighlightGroundObjects", name = "Highlight Ground Objects",
            description = "Omlijning van ground objects", position = 96)
    default boolean devHighlightGroundObjects() { return false; }

    @ConfigItem(keyName = "devHighlightDecorativeObjects", name = "Highlight Decorative Objects",
            description = "Omlijning van decorative objects", position = 97)
    default boolean devHighlightDecorativeObjects() { return false; }

    @ConfigItem(keyName = "devHighlightNpcs", name = "Highlight NPCs",
            description = "Omlijning van NPCs", position = 98)
    default boolean devHighlightNpcs() { return false; }

    @ConfigItem(keyName = "devExtNpcGraphics", name = "Ext. NPC Graphics",
            description = "Spot-anims / graphics objects bij NPCs", position = 99)
    default boolean devExtNpcGraphics() { return false; }

    @ConfigItem(keyName = "devCollisionData", name = "Debug Collision Data",
            description = "Geblokkeerde tegels (collision flags)", position = 100)
    default boolean devCollisionData() { return false; }

    @ConfigItem(keyName = "devProjectiles", name = "Debug Projectiles",
            description = "Teken projectielen", position = 101)
    default boolean devProjectiles() { return false; }

    // ---- Updates (private GitHub) ----

    @ConfigItem(
            keyName = "updateGithubRepo",
            name = "Update GitHub repo",
            description = "owner/repo van de private LoneBot-repo",
            position = 80
    )
    default String updateGithubRepo() { return LoneBotUpdater.DEFAULT_REPO; }

    @ConfigItem(
            keyName = "updateGithubToken",
            name = "Update GitHub token",
            description = "Fine-grained PAT: Contents Read (private repo)",
            position = 81
    )
    default String updateGithubToken() { return ""; }

    @ConfigItem(
            keyName = "updateCheckOnStartup",
            name = "Check updates bij start",
            description = "Controleer bij opstarten of er een nieuwere release is",
            position = 82
    )
    default boolean updateCheckOnStartup() { return true; }

    // ---- Quest Bot (script-quest) ----

    @ConfigItem(
            keyName = "clueEnabled",
            name = "Beginner Clue",
            description = "Beginner Treasure Trails solver (anagram, cryptic, emote, map, hot/cold, Charlie)",
            position = 2098
    )
    default boolean clueEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "clueBankCasket",
            name = "Casket banken (niet openen)",
            description = "Reward casket naar bank i.p.v. meteen Open + loot",
            position = 2099
    )
    default boolean clueBankCasket() {
        return false;
    }

    @ConfigItem(
            keyName = "clueGeBuyMissing",
            name = "Clue items kopen (GE)",
            description = "Ontbrekende spade/emote-kit/Charlie-items via Grand Exchange (niet Strange device)",
            position = 2104
    )
    default boolean clueGeBuyMissing() {
        return true;
    }

    @ConfigItem(
            keyName = "questEnabled",
            name = "Quest Bot",
            description = "F2P quester (Cook / Goblin / Romeo / Rune / Doric / Vampire / Tutorial)",
            position = 2100
    )
    default boolean questEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "questTarget",
            name = "Quest doel",
            description = "ROTATION of een F2P-quest (COOKS_ASSISTANT, …)",
            position = 2101
    )
    default String questTarget() {
        return "ROTATION";
    }

    @ConfigItem(
            keyName = "questRotationEnabled",
            name = "Quest rotatie",
            description = "Na finish: volgende open F2P-quest",
            position = 2102
    )
    default boolean questRotationEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "questSkipLowLevel",
            name = "Quest skip laag level",
            description = "Rotatie slaat Doric over bij Mining ≥ 10",
            position = 2103
    )
    default boolean questSkipLowLevel() {
        return true;
    }

    @ConfigItem(
            keyName = "impsClueSolver",
            name = "Clues doen",
            description = "Clue scroll/container → solver; daarna Imp hervatten",
            position = 510
    )
    default boolean impsClueSolver() {
        return false;
    }

    @ConfigItem(
            keyName = "imps2ClueSolver",
            name = "Clues doen",
            description = "Clue scroll/container → solver; daarna Imps2 hervatten",
            position = 1520
    )
    default boolean imps2ClueSolver() {
        return false;
    }

    // ---- Re-log (zelfde account) — CombatBot SameAccountRelogger ----

    enum JagexLoginPlayNowMapMode {
        AUTO("Auto"),
        TOP_ANCHOR("Top-anchor (smal/hoog)"),
        LETTERBOX("Letterbox (uniform)"),
        WIDTH_STRETCH("Breedte-stretch (Y vast ~262)");

        private final String label;

        JagexLoginPlayNowMapMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @ConfigItem(
            keyName = "reLogoutEnabled",
            name = "Re-log inschakelen",
            description = "Log na een willekeurige tijd uit en weer in met hetzelfde account; bot gaat daarna verder waar hij was.",
            position = 90
    )
    default boolean reLogoutEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "reLogoutMinMinutes",
            name = "Min. minuten tot re-log",
            description = "Minimaal aantal minuten voordat er wordt uit- en ingelogd",
            position = 91
    )
    @Range(min = 1, max = 240)
    default int reLogoutMinMinutes() {
        return 20;
    }

    @ConfigItem(
            keyName = "reLogoutMaxMinutes",
            name = "Max. minuten tot re-log",
            description = "Maximaal aantal minuten voordat er wordt uit- en ingelogd",
            position = 92
    )
    @Range(min = 1, max = 240)
    default int reLogoutMaxMinutes() {
        return 45;
    }

    @ConfigItem(
            keyName = "reLogoutPauseMinMinutes",
            name = "Min. pauze (min)",
            description = "Minimale pauze op het login scherm voordat opnieuw wordt ingelogd (0 = geen pauze)",
            position = 93
    )
    @Range(min = 0, max = 60)
    default int reLogoutPauseMinMinutes() {
        return 2;
    }

    @ConfigItem(
            keyName = "reLogoutPauseMaxMinutes",
            name = "Max. pauze (min)",
            description = "Maximale pauze op het login scherm (0 = geen pauze)",
            position = 94
    )
    @Range(min = 0, max = 90)
    default int reLogoutPauseMaxMinutes() {
        return 5;
    }

    @ConfigItem(
            keyName = "reLogoutAccount",
            name = "Account (re-log)",
            description = "Leeg = launcher-account. Anders pasted:DisplayName, jagex:/pad/credentials.properties, of email:wachtwoord",
            position = 95
    )
    default String reLogoutAccount() {
        return "";
    }

    @ConfigItem(
            keyName = "loginNow",
            name = "Log in (nu)",
            description = "Vink aan om nu in te loggen (op het login-scherm; launcher-account of Account re-log)",
            position = 96
    )
    default boolean loginNow() {
        return false;
    }

    @ConfigItem(
            keyName = "breakNow",
            name = "Break nu",
            description = "Vink aan of gebruik de Break nu-knop: veilig uitloggen, pauze, weer inloggen",
            position = 101
    )
    default boolean breakNow() {
        return false;
    }

    @ConfigItem(
            keyName = "cancelBreak",
            name = "Annuleer break",
            description = "Stop de huidige break: in game blijven, of pauze overslaan en inloggen",
            position = 102
    )
    default boolean cancelBreak() {
        return false;
    }

    @ConfigItem(
            keyName = "jagexLoginPlayNowCenterYPct",
            name = "Jagex Play Now Y% (canvas)",
            description = "Verticale positie van de grijze Play Now-knop als % van canvas-hoogte.",
            position = 97
    )
    @Range(min = 35, max = 70)
    default int jagexLoginPlayNowCenterYPct() {
        return 52;
    }

    @ConfigItem(
            keyName = "jagexLoginPlayNowBtnWidthPct",
            name = "Jagex Play Now breedte %",
            description = "Klikzone-breedte als % van canvas-breedte (grijze Play Now-knop).",
            position = 98
    )
    @Range(min = 12, max = 50)
    default int jagexLoginPlayNowBtnWidthPct() {
        return 28;
    }

    @ConfigItem(
            keyName = "jagexLoginPlayNowBtnHeightPct",
            name = "Jagex Play Now hoogte %",
            description = "Klikzone-hoogte als % van canvas-hoogte (grijze Play Now-knop).",
            position = 99
    )
    @Range(min = 5, max = 25)
    default int jagexLoginPlayNowBtnHeightPct() {
        return 11;
    }

    @ConfigItem(
            keyName = "jagexLoginPlayNowMapMode",
            name = "Jagex Play Now schaal-modus",
            description = "Hoe 765×503 naar jouw canvas wordt gemapt. Auto: smal/hoog=top-anchor, breed/fullscreen=width-stretch.",
            position = 100
    )
    default JagexLoginPlayNowMapMode jagexLoginPlayNowMapMode() {
        return JagexLoginPlayNowMapMode.AUTO;
    }
}
