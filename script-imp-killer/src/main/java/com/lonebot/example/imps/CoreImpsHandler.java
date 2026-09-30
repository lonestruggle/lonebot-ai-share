package com.lonebot.example.imps;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.ActorState;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.combat.RangedAmmoKit;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Combat;
import net.storm.sdk.game.Game;
import net.storm.sdk.game.Skills;
import net.storm.sdk.interact.AimInteractHelper;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.interact.GroundLootPickupHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.HumanBanking;
import net.storm.sdk.items.DepositBox;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.GrandExchange;
import net.storm.sdk.items.Inventory;
import net.storm.api.magic.SpellBook;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.movement.KaramjaVolcano;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.widgets.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core Karamja Imp Killer (CombatBot ImpsHandler happy path).
 * Hunt (3-zone union zonder center-pin) → loot/quest → ashes → boat → deposit → restock/GE.
 * Gear prep + GE buy. Geen farm-money / starter / competitor-hop.
 */
public final class CoreImpsHandler {

    private static final Logger log = LoggerFactory.getLogger(CoreImpsHandler.class);

    public static final WorldPoint HUNT_DEFAULT = new WorldPoint(2826, 3181, 0);
    public static final WorldPoint RALLY = new WorldPoint(2830, 3182, 0);
    /** Extra hunt circles (CombatBot Imps Mode zonder center-pin). */
    public static final WorldPoint EXTRA_IMP_AREA_1 = new WorldPoint(2826, 3149, 0);
    public static final WorldPoint EXTRA_IMP_AREA_2 = new WorldPoint(2832, 3200, 0);
    /** Actieve zoekpunten als er geen imp in scene is (user-markers). */
    public static final WorldPoint SEARCH_SPOT_A = new WorldPoint(2841, 3164, 0);
    public static final WorldPoint SEARCH_SPOT_B = new WorldPoint(2846, 3188, 0);
    public static final WorldPoint SCORPION_SAFE = new WorldPoint(2826, 3172, 0);
    public static final WorldPoint SCORPION_ZONE_1 = new WorldPoint(2852, 3185, 0);
    public static final WorldPoint SCORPION_ZONE_2 = new WorldPoint(2853, 3161, 0);
    /** Default avoid-radius rond vaste zones én live scorpion-NPCs. */
    public static final int DEFAULT_SCORPION_ZONE_RADIUS = 5;
    public static final WorldPoint KARAMJA_BOAT_APPROACH = new WorldPoint(2954, 3144, 0);
    /**
     * Zuidweg Musa Point (y≈3158), zuid om de vulkaan.
     * Noordelijke jungle ~2869,3187 is zoekpunt-B / omloop — niet de bank-route.
     */
    public static final WorldPoint KARAMJA_SOUTH_ROAD = new WorldPoint(2866, 3158, 0);
    /** Aanloop vóór Customs — niet op NPC-tegel (2956,3143), anders hangt de pathfinder. */
    public static final WorldPoint KARAMJA_DOCK_NPC = new WorldPoint(2956, 3143, 0);
    /** Alternatief als de loopbrug/gangplank Customs verbergt. */
    public static final WorldPoint KARAMJA_DOCK_ALT_WAYPOINT = new WorldPoint(2947, 3154, 0);
    public static final WorldPoint PORTSARIM_DOCK = new WorldPoint(3028, 3210, 0);
    /**
     * Zuidelijke weg Draynor → docks (niet via willow-pad ~3064,3254).
     * Hop daarheen eerst als we nog noord van de weg staan.
     */
    public static final WorldPoint PORTSARIM_DOCK_ROAD = new WorldPoint(3046, 3236, 0);
    public static final WorldPoint PORTSARIM_BOAT_NPC = new WorldPoint(3027, 3218, 0);
    public static final WorldPoint PORTSARIM_DEPOSIT = new WorldPoint(3029, 3210, 0);
    public static final WorldPoint DRAYNOR_BANK = new WorldPoint(3092, 3243, 0);
    public static final WorldPoint GE = new WorldPoint(3164, 3487, 0);

    /** Imp Catcher beads + mind talisman + fiendish ashes — dump op item-id. */
    private static final int ID_RED_BEAD = 1470;
    private static final int ID_YELLOW_BEAD = 1472;
    private static final int ID_BLACK_BEAD = 1474;
    private static final int ID_WHITE_BEAD = 1476;
    private static final int ID_MIND_TALISMAN = 1442;
    private static final int ID_FIENDISH_ASHES = 25766;
    private static final int ID_HAMMER = 2347;
    private static final int ID_CADAVA = 753;
    private static final int ID_CLAY = 434;
    private static final int ID_BALL_OF_WOOL = 1759;
    private static final int BOAT_FARE = 30;
    private static final int CUSTOMS_OFFICER_ID = 380;
    private static final int[] PORT_SARIM_SEAMAN_IDS = {364, 365, 326};
    /** Known Port Sarim / bank deposit box object ids (OSRS). */
    private static final int[] DEPOSIT_BOX_OBJECT_IDS = {50902, 26254, 25937, 10529};

    private static final String[] KEEP_ITEMS = {
            "Coins", "Law rune", "Mind rune", "Chaos rune", "Death rune", "Nature rune",
            "Air rune", "Water rune", "Earth rune", "Fire rune",
            "Body rune", "Cosmic rune", "Astral rune", "Blood rune", "Soul rune", "Wrath rune",
            "Bronze arrow", "Iron arrow", "Steel arrow", "Mithril arrow", "Adamant arrow", "Rune arrow",
            "Amulet of power", "Amulet of magic", "Amulet of strength", "Amulet of accuracy",
            "Leather gloves", "Leather boots", "Fighting boots"
    };

    private static final String[] FOOD_NAMES = {
            "Shrimps", "Anchovies", "Sardine", "Herring", "Mackerel", "Trout", "Salmon",
            "Tuna", "Lobster", "Swordfish", "Monkfish", "Shark", "Bread", "Cake", "Meat pie",
            "Cooked meat", "Cooked chicken"
    };

    // ---- runtime settings (set from plugin/config each tick) ----
    public volatile boolean enabled;
    public volatile ImpsTypes.ImpsCombatStyle style = ImpsTypes.ImpsCombatStyle.MELEE;
    public volatile ImpsTypes.ImpsMageSpell mageSpell = ImpsTypes.ImpsMageSpell.WIND_STRIKE;
    public volatile int huntX = 2826;
    public volatile int huntY = 3181;
    public volatile int huntRadius = 20;
    public volatile int impNpcId = 5007;
    public volatile String lootCsv = "Black bead,Red bead,Yellow bead,White bead,Mind rune,Mind talisman,Fiendish ashes";
    public volatile boolean scatterAshes = true;
    public volatile int bankThreshold = 20;
    public volatile int minCoins = 60;
    public volatile boolean avoidScorpions = true;
    public volatile boolean attackScorpions = false;
    /** Radius (tiles) rond vaste scorpion-zones én live Scorpion-NPCs — loot/hunt vermijden. */
    public volatile int scorpionZoneRadius = DEFAULT_SCORPION_ZONE_RADIUS;
    /** CombatBot: agro uit als jouw cmb &gt; {@code scorpionNpcLevel * 2 + 1} (default 14 → cmb 30+). */
    public volatile int scorpionNpcLevel = 14;
    public volatile boolean geSellEnabled = false;
    public volatile int geSellAfterBanks = 5;
    public volatile int geSellPrice = 999;
    public volatile String geSellCsv = "Black bead,Red bead,Yellow bead,White bead,Mind talisman,Fiendish ashes";
    public volatile boolean gearPrepEnabled = true;
    /** Account-flag: Magic ≥ 13 → Fire Strike + Staff of fire + Air (CombatBot magicAutoUpdate). */
    public volatile boolean magicAutoUpdate = false;
    public volatile boolean geRestockEnabled = false;
    public volatile boolean questLootEnabled = true;
    public volatile boolean ashHumanize = false;
    public volatile int ashHumanizeChancePercent = 35;
    public volatile int idleRoamSeconds = 12;
    public volatile boolean meleeOpeningAirStrike = false;
    /** AUTO of Method-naam — zie GroundLootPickupHelper. */
    public volatile String lootPickupMode = "AUTO";
    public volatile boolean lootPickupStrict = false;
    public volatile String accountKey = "";
    /** CombatBot: loot pas na N±random kills; 0 / uit = na combat. */
    public volatile boolean lootDelayEnabled = true;
    public volatile int lootDelayKills = 2;
    /** Items die altijd direct (ook tijdens delay / combat). */
    public volatile String specialLootCsv = "Black bead,Red bead,Yellow bead,White bead,Mind talisman";

    private boolean bankingTrip;
    private boolean restocking;
    private int depositBoxFailCount;
    private boolean depositQtyAllClicked;
    private static final int MAX_DEPOSIT_BOX_FAILS = 6;
    private int bankTripsSinceGe;
    private boolean geSellTrip;
    private boolean preparingGear;
    private boolean gearPrepComplete;
    /** CombatBot: autocast één keer gezet → daarna alleen Attack (geen magic-tab). */
    private boolean mageAutocastReady;
    private String mageAutocastSpellName;
    private boolean geBuying;
    private String geBuyItem;
    private int geBuyQty;
    private int geBuyPrice;
    private long lastAttackMs;
    /** CombatBot: game-msg “geen ammo” — Equipment kan achterlopen. */
    private long lastEmptyQuiverMs;
    private String lastSeenGameMsg = "";
    private String lastAmmoConsole = "";
    private long lastAmmoConsoleMs;
    private long lastLootMs;
    private long lastBoatClickMs;
    private long lastIdleRoamMs;
    /** 0 = SEARCH_SPOT_A, 1 = SEARCH_SPOT_B. */
    private int searchSpotIndex;
    private String lastHuntConsole = "";
    private long lastHuntConsoleMs;
    private long lastHuntWalkMs;
    /** Vast hunt-doel tot aankomst — nearestHuntApproach mag niet elke tick wisselen. */
    private WorldPoint lockedHuntDest;
    private long awaitingBoatUntilMs;
    private long homeTeleUntilMs;
    private String lastBoatLog = "";
    private long lastBoatLogMs;
    private String status = "idle";
    private String lastLoggedStatus = "";
    private static final long BOAT_CLICK_COOLDOWN_MS = 2800;
    /** ClickOnSight / Pay-fare pas binnen zoveel tegels van de dock-tegel. */
    private static final int BOAT_SIGHT_TILES = 40;

    /** Na Take: niet opnieuw klikken terwijl we naar het item lopen. */
    private int pendingLootItemId = -1;
    private String pendingLootName;
    private WorldPoint pendingLootTile;
    private long pendingLootUntilMs;
    private static final long LOOT_WALK_TIMEOUT_MS = 5_500L;
    /** CombatBot: blijf looten tot de grond leeg is — niet na 1 item weer Attack. */
    private boolean pickingUpLoot;
    private int consecutiveLootAttempts;
    private int lootGoneTicks;
    private boolean deferNormalLootUntilAfterKill;
    private static final int MAX_CONSECUTIVE_LOOT_ATTEMPTS = 3;
    private static final int LOOT_PICKUP_COOLDOWN_MS = 180;
    /** Loot achter een boom: tegel even overslaan i.p.v. Chop-spam. */
    private WorldPoint skippedLootTile;
    private long skippedLootUntilMs;
    private static final long SKIP_OCCLUDED_LOOT_MS = 12_000L;
    /** CombatBot: random drempel + batch i.p.v. altijd scatteren bij 3 ashes. */
    private boolean scatteringBatchStarted;
    private int scatterAshesRemainingThisBatch;
    private int nextAshScatterThreshold;
    private boolean scatterShiftClearedThisBatch;
    private long lastLootConsoleMs;
    private String lastLootConsole = "";
    private int killsSinceLastLoot;
    /** -1 = nog niet gerold voor deze cycle. */
    private int effectiveLootKills = -1;
    private INPC currentTarget;
    private boolean targetWasAlive;

    public String getStatus() {
        return status != null ? status : "-";
    }

    /**
     * Game-chat: Chop down i.p.v. Take → skip die loot-tegel (geen axe-spam).
     */
    public void onGameMessage(String message) {
        GroundLootPickupHelper.onGameMessage(message);
        WorldPoint skip = GroundLootPickupHelper.peekChopMisclickTile();
        if (skip == null) {
            return;
        }
        skipLootTile(skip);
        lootConsole("chop-misclick — skip tile " + skip.getX() + "," + skip.getY());
        pickingUpLoot = true;
        consecutiveLootAttempts = 0;
    }

    private void setStatus(String s) {
        status = s != null ? s : "-";
        BotRuntime.impStatus = status;
        if (!status.equals(lastLoggedStatus)) {
            lastLoggedStatus = status;
            ImpActionLog.status(accountKey, status);
            BotRuntime.logConsole("[Imp] " + status);
        }
    }

    /** Compacte regels voor Imp debug-paint (geen bank/chat-overlap-ruis). */
    public java.util.List<String> debugLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Style: " + (style != null ? style.name() : "?")
                + (magicAutoUpdate ? " autoFire" : ""));
        lines.add("Phase: " + phaseLabel());
        ImpGearCheck.Snapshot gear = currentGearSnapshot();
        lines.add("Gear: " + (preparingGear ? "prep " : "")
                + (gear.readyToHunt ? "READY" : "WAIT")
                + " — " + trimDebug(gear.reason, 28));
        if (style == ImpsTypes.ImpsCombatStyle.MAGE && gear.activeSpell != null) {
            lines.add("Spell: " + gear.activeSpell.getSpellName()
                    + " casts~" + gear.usableCasts);
        }
        if (bankingTrip || geBuying || geSellTrip) {
            lines.add("Trip: "
                    + (bankingTrip ? "bank" : "")
                    + (restocking ? "+restock" : "")
                    + (geBuying ? " GE-buy" : "")
                    + (geSellTrip ? " GE-sell" : ""));
        }
        int need = peekLootKillsNeeded();
        if (lootDelayEnabled && need > 0) {
            lines.add("Kills: " + killsSinceLastLoot + "/" + need
                    + (pickingUpLoot ? " looting" : ""));
        } else if (pickingUpLoot || pendingLootName != null) {
            lines.add("Loot: " + (pendingLootName != null ? pendingLootName : "chain"));
        }
        if (deferNormalLootUntilAfterKill) {
            lines.add("Loot: defer tot kill");
        }
        try {
            int ashes = Inventory.getCount("Fiendish ashes");
            if (ashes > 0 || scatteringBatchStarted) {
                lines.add("Ashes: " + ashes
                        + " next≥" + Math.max(1, nextAshScatterThreshold)
                        + (scatteringBatchStarted ? " batch " + scatterAshesRemainingThisBatch : ""));
            }
        } catch (Throwable ignored) {
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me != null && me.worldLocation != null) {
            WorldPoint p = me.worldLocation;
            lines.add("Pos: " + p.getX() + "," + p.getY()
                    + (isOnKaramja(p) ? " K" : "")
                    + (isInHuntingArea(p) ? " hunt" : ""));
        }
        if (avoidScorpions) {
            int my = localCombatLevel();
            int npc = scorpionNpcLevel > 0 ? scorpionNpcLevel : 14;
            int thresh = (npc * 2) + 1;
            if (my > thresh) {
                lines.add("Scorp: OK cmb=" + my + " > " + thresh);
            } else {
                lines.add("Scorp: agro cmb=" + my + " ≤ " + thresh);
            }
        }
        return lines;
    }

    private static String trimDebug(String s, int max) {
        if (s == null) {
            return "-";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String phaseLabel() {
        if (preparingGear) {
            return "gear-prep";
        }
        if (geBuying) {
            return "ge-buy";
        }
        if (geSellTrip) {
            return "GE-sell";
        }
        if (System.currentTimeMillis() < awaitingBoatUntilMs) {
            return "boat-wait";
        }
        if (bankingTrip) {
            if (restocking && !hasLootDumpDepositItems()) {
                return "restock-bank";
            }
            if (DepositBox.isOpen()) {
                return "deposit-open";
            }
            return restocking ? "deposit-then-restock" : "deposit-dump";
        }
        if (status != null) {
            if (status.startsWith("boot") || status.contains("Customs") || status.contains("Pay-fare")
                    || status.contains("boat")) {
                return "boat";
            }
            if (status.contains("loot") || status.contains("pickup") || status.contains("quest")
                    || status.contains("scatter")) {
                return "loot";
            }
            if (status.contains("vecht") || status.contains("combat") || status.contains("imp")
                    || status.contains("aanval") || status.contains("mage")) {
                return "hunt";
            }
        }
        return "idle/hunt";
    }

    public void reset() {
        bankingTrip = false;
        restocking = false;
        depositBoxFailCount = 0;
        depositQtyAllClicked = false;
        geSellTrip = false;
        preparingGear = false;
        gearPrepComplete = false;
        geBuying = false;
        geBuyItem = null;
        awaitingBoatUntilMs = 0;
        homeTeleUntilMs = 0;
        clearPendingLoot();
        pickingUpLoot = false;
        consecutiveLootAttempts = 0;
        lootGoneTicks = 0;
        skippedLootTile = null;
        skippedLootUntilMs = 0L;
        deferNormalLootUntilAfterKill = false;
        resetScatterBatch();
        killsSinceLastLoot = 0;
        effectiveLootKills = -1;
        currentTarget = null;
        targetWasAlive = false;
        lastEmptyQuiverMs = 0L;
        lastSeenGameMsg = "";
        lockedHuntDest = null;
        setStatus("reset");
    }

    public int loop() {
        return loopFull();
    }

    public int loopFull() {
        if (!enabled || !Game.isLoggedIn()) {
            setStatus(enabled ? "niet ingelogd" : "uit");
            return 800;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me == null || !me.present || me.worldLocation == null) {
            setStatus("geen speler");
            return 600;
        }
        WorldPoint pos = me.worldLocation;
        long now = System.currentTimeMillis();

        int volcano = KaramjaVolcano.climbRopeIfInside();
        if (volcano > 0) {
            setStatus("vulkaan → rope omhoog");
            return volcano;
        }

        boolean travelingToHunt = isOnKaramja(pos) && !isInHuntingArea(pos);
        boolean travelingToBoat = !isOnKaramja(pos);
        boolean holdForBoatOrTele = now < awaitingBoatUntilMs || now < homeTeleUntilMs;
        if (!holdForBoatOrTele && !travelingToHunt && !travelingToBoat) {
            int anti = AntiBan.get().check();
            if (anti > 0) {
                setStatus("antiban: " + AntiBan.get().getLastActionLabel());
                return Math.min(anti, 800);
            }
        }

        if (now < homeTeleUntilMs) {
            if (!isOnKaramja(pos)) {
                homeTeleUntilMs = 0;
                awaitingBoatUntilMs = 0;
                setStatus("Home tele klaar");
            } else {
                setStatus("Home tele…");
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
        }

        if (now < awaitingBoatUntilMs) {
            setStatus("boat wait");
            // Vertrek Karamja: bank / gear / GE — aankomst mainland
            boolean leavingKaramja = bankingTrip || preparingGear || geBuying || geSellTrip;
            if (leavingKaramja && !isOnKaramja(pos)) {
                awaitingBoatUntilMs = 0;
            } else if (!leavingKaramja && isOnKaramja(pos)) {
                // Terug naar Karamja (hunt)
                awaitingBoatUntilMs = 0;
            } else {
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
        }

        // Gear prep / GE buy before hunt — hard gate (geen best-effort)
        if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
            maybeForceFireStrikeUpgrade();
        }
        if (gearPrepEnabled) {
            ImpGearCheck.Snapshot gear = currentGearSnapshot();
            if (gear.readyToHunt) {
                preparingGear = false;
                gearPrepComplete = true;
            } else if (gearPrepComplete) {
                // Mid-session kit kapot (runes op / staff unequip / auto-fire unlock)
                gearPrepComplete = false;
                preparingGear = true;
                setStatus("gear re-prep: " + gear.reason);
            } else {
                preparingGear = true;
            }
        }
        if (preparingGear) {
            return handleGearPreparation(pos);
        }
        if (geBuying) {
            return handleGeBuy(pos);
        }

        // Optional GE sell trip
        if (geSellTrip) {
            return handleGeSell(pos);
        }

        // Dump/restock vóór pad naar boot — daarna geen eten/magic tijdens het lopen.
        if (shouldStartBanking() && !bankingTrip) {
            bankingTrip = true;
            restocking = checkIfRestockNeeded();
            pickingUpLoot = false;
            int occupied = Inventory.getCount();
            int stacks = countDumpStacks();
            setStatus(restocking ? "bank trip (restock)" : "bank trip (deposit)");
            BotRuntime.logConsole("[Imp/bank] trip start slots=" + occupied + "/" + bankThreshold
                    + " loot=" + stacks + " — dump ALL loot"
                    + (Inventory.isFull() ? " inv-full" : "")
                    + (restocking ? " + restock" : ""));
        }

        if (scatterAshes && Inventory.contains("Fiendish ashes") && bankingTrip) {
            int left = Inventory.getCount("Fiendish ashes");
            if (!scatteringBatchStarted || scatterAshesRemainingThisBatch < left) {
                beginScatterBatch(left, "pre-dump");
            }
            setStatus("scatter ashes pre-dump (" + left + ")");
            lootConsole("pre-dump scatter " + left + " left — dump rest after");
            int s = handleScatterAshes();
            if (s > 0) {
                return s;
            }
        }

        if (bankingTrip) {
            return handleBankingTrip(pos);
        }

        if (!isOnKaramja(livePos(pos))) {
            lockedHuntDest = null;
            ImpGearCheck.Snapshot gear = currentGearSnapshot();
            if (gearPrepEnabled && !gear.readyToHunt) {
                gearPrepComplete = false;
                preparingGear = true;
                setStatus("geen boot: " + gear.reason);
                return handleGearPreparation(pos);
            }
            setStatus("boot terug naar Karamja");
            return payFareBack(livePos(pos));
        }

        if (!isInHuntingArea(pos)) {
            pickingUpLoot = false;
            return walkTowardHuntArea(pos);
        }
        lockedHuntDest = null;

        // Vanaf hier: in het jachtgebied. Eten/scorpion/loot mag weer.
        if (!bankingTrip && avoidScorpions) {
            int flee = handleScorpion(pos);
            if (flee > 0) {
                return flee;
            }
        }

        if (Combat.getHealthPercent() < 45 && hasFood()) {
            setStatus("eten");
            return eatFood();
        }

        if (scatterAshes && Inventory.isFull() && Inventory.contains("Fiendish ashes")) {
            beginScatterBatch(1, "inv-full");
            int s = handleScatterAshes();
            if (s > 0) {
                return s;
            }
        }

        maybeRegisterKill();

        // Priority + loot-window (CombatBot): niet na elke kill, wel chain zonder stilstand
        int lootDelay = handleHuntLootPriority(pos);
        if (lootDelay > 0) {
            return lootDelay;
        }

        // Random ash-batch ná loot (niet tijdens chain, nooit vast bij 3)
        if (scatterAshes && !pickingUpLoot && Inventory.contains("Fiendish ashes") && shouldScatterAshesNow()) {
            int s = handleScatterAshes();
            if (s > 0) {
                return s;
            }
        }

        return handleKilling(pos);
    }

    // ---------------- combat ----------------

    private int handleKilling(WorldPoint pos) {
        if (!isInHuntingArea(pos)) {
            return walkTowardHuntArea(pos);
        }

        maybeRegisterKill();

        int ammoGate = enforceRangedAmmoOrDelay();
        if (ammoGate > 0) {
            return ammoGate;
        }

        if (meInCombat()) {
            targetWasAlive = true;
            if (currentTargetDead()) {
                currentTarget = null;
            }
            INPC next = findImp(pos);
            if (next != null && (currentTarget == null || currentTargetDead())) {
                return issueAttack(next);
            }
            int need = lootKillsNeeded();
            if (lootDelayEnabled && need > 0) {
                setStatus("vechten " + killsSinceLastLoot + "/" + need);
            } else {
                setStatus("vechten");
            }
            return ThreadLocalRandom.current().nextInt(90, 180);
        }

        INPC imp = findImp(pos);
        if (imp == null) {
            return roamForImp(pos);
        }

        if (meleeOpeningAirStrike && style == ImpsTypes.ImpsCombatStyle.MELEE
                && Inventory.getCount("Mind rune") > 0
                && (Inventory.getCount("Air rune") > 0 || Equipment.contains(i -> i != null && i.getName() != null
                && i.getName().toLowerCase(Locale.ROOT).contains("staff of air")))) {
            if (Magic.cast(net.storm.api.magic.SpellBook.Standard.WIND_STRIKE, imp)) {
                lastAttackMs = System.currentTimeMillis();
                currentTarget = imp;
                targetWasAlive = true;
                setStatus("open Air Strike");
                AntiBan.get().markBotActivity();
                return ThreadLocalRandom.current().nextInt(280, 480);
            }
        }

        if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
            maybeForceFireStrikeUpgrade();
            ImpsTypes.ImpsMageSpell spell = resolveMageSpell();
            SpellBook.Standard std = spell.getStandardSpell();
            String want = spell.getSpellName();

            // CombatBot: Magic.isAutoCasting (varbit) — setAutoCast(spell, false) via Combat Options
            if (Magic.isAutoCasting(std)) {
                mageAutocastReady = true;
                mageAutocastSpellName = want;
            } else {
                mageAutocastReady = false;
                setStatus("autocast " + want);
                if (Magic.setAutoCast(std, false) && Magic.isAutoCasting(std)) {
                    mageAutocastReady = true;
                    mageAutocastSpellName = want;
                    setStatus("autocast OK — Attack");
                    AntiBan.get().markBotActivity();
                    try {
                        net.storm.sdk.widgets.Tabs.open(net.storm.api.widgets.Tab.INVENTORY);
                    } catch (Throwable ignored) {
                    }
                    return ThreadLocalRandom.current().nextInt(400, 700);
                }
                setStatus("autocast retry…");
                return ThreadLocalRandom.current().nextInt(500, 800);
            }
        }

        return issueAttack(imp);
    }

    private int issueAttack(INPC imp) {
        if (imp == null) {
            return 80;
        }
        long now = System.currentTimeMillis();
        if (now - lastAttackMs < 220L) {
            return ThreadLocalRandom.current().nextInt(60, 120);
        }
        if (AimInteractHelper.interactNpc(imp, "Attack")) {
            lastAttackMs = now;
            currentTarget = imp;
            targetWasAlive = true;
            String aim = AimInteractHelper.getLastDetail();
            setStatus("aanval " + (imp.getName() != null ? imp.getName() : "Imp")
                    + (aim.isEmpty() ? "" : " [" + aim + "]"));
            AntiBan.get().markBotActivity();
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        setStatus("attack fail");
        return 200;
    }

    private boolean currentTargetDead() {
        if (currentTarget == null) {
            return true;
        }
        try {
            return currentTarget.isDead();
        } catch (Throwable t) {
            return false;
        }
    }

    private void maybeRegisterKill() {
        if (currentTarget != null && currentTargetDead()) {
            registerKill("target dood");
            currentTarget = null;
            targetWasAlive = meInCombat();
            return;
        }
        if (targetWasAlive && !meInCombat()) {
            registerKill("combat uit");
            currentTarget = null;
            targetWasAlive = false;
        } else if (meInCombat()) {
            targetWasAlive = true;
        }
    }

    private void registerKill(String why) {
        killsSinceLastLoot++;
        deferNormalLootUntilAfterKill = false;
        lootConsole("kill " + killsSinceLastLoot + "/" + lootKillsNeeded() + " (" + why + ")");
    }

    private void finishLootWindow() {
        pickingUpLoot = false;
        consecutiveLootAttempts = 0;
        lootGoneTicks = 0;
        killsSinceLastLoot = 0;
        effectiveLootKills = -1;
        clearPendingLoot();
    }

    private INPC findImp(WorldPoint pos) {
        List<INPC> all = NPCs.getAll(n -> {
            if (n == null || n.getWorldLocation() == null) {
                return false;
            }
            if (!isImpNpc(n)) {
                return false;
            }
            if (!isInHuntingArea(n.getWorldLocation())) {
                return false;
            }
            if (avoidScorpions && !attackScorpions && isScorpionAvoidTile(n.getWorldLocation())) {
                return false;
            }
            if (ExcludedTiles.isNpcExcluded(n.getWorldLocation(), npcSize(n))) {
                return false;
            }
            return !n.isDead();
        });
        INPC best = null;
        int bestD = Integer.MAX_VALUE;
        for (INPC n : all) {
            int d = pos.distanceTo(n.getWorldLocation());
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    private boolean isImpNpc(INPC n) {
        if (impNpcId > 0 && n.getId() == impNpcId) {
            return true;
        }
        String name = n.getName();
        return name != null && name.equalsIgnoreCase("Imp");
    }

    private static int npcSize(INPC n) {
        return 1; // Imp is 1x1; footprint ring still applied in ExcludedTiles.isNpcExcluded
    }

    // ---------------- loot / ashes ----------------

    /**
     * CombatBot: normale loot pas na N±random kills (blijven vechten).
     * Priority/quest altijd direct. Loot-window: ketenen zonder 2–5s stilstand.
     */
    private int handleHuntLootPriority(WorldPoint pos) {
        boolean nearby = hasImpLootNearby(pos);
        boolean specialNearby = hasPriorityLootNearby(pos);
        boolean specialUnderfoot = specialNearby && hasPriorityLootWithin(pos, 2);

        if (pickingUpLoot) {
            if (Inventory.isFull()) {
                pickingUpLoot = false;
                consecutiveLootAttempts = 0;
                lootGoneTicks = 0;
                clearPendingLoot();
                return 0;
            }
            // CombatBot: eenmaal loot-window → door tot de grond leeg is.
            // Geen kill-drempel opnieuw, geen 1× lege TileItems-read.
            boolean stillWant = nearby || specialNearby || pendingLootStillOnGround();
            if (!stillWant) {
                lootGoneTicks++;
                if (lootGoneTicks < 2) {
                    lootConsole("loot read empty — keep window");
                    setStatus("loot check…");
                    return ThreadLocalRandom.current().nextInt(80, 140);
                }
                pickingUpLoot = false;
                consecutiveLootAttempts = 0;
                lootGoneTicks = 0;
                clearPendingLoot();
                if (isLootAllowed()) {
                    killsSinceLastLoot = 0;
                    effectiveLootKills = -1;
                    deferNormalLootUntilAfterKill = false;
                }
                return 0;
            }
            lootGoneTicks = 0;
            if (consecutiveLootAttempts >= MAX_CONSECUTIVE_LOOT_ATTEMPTS
                    && !Movement.isMoving()
                    && !pendingLootStillOnGround()) {
                if (specialNearby) {
                    consecutiveLootAttempts = 0;
                    return handleLooting(pos);
                }
                deferNormalLootUntilAfterKill = true;
                pickingUpLoot = false;
                consecutiveLootAttempts = 0;
                clearPendingLoot();
                lootConsole("loot stuck → kill eerst");
                setStatus("loot uitgesteld (stuck)");
                return 0;
            }
            boolean underfoot = hasLootWithin(pos, 2) || specialUnderfoot;
            if (meInCombat() && !underfoot && !Movement.isMoving()) {
                return stopCombatForLoot(pos);
            }
            return handleLooting(pos);
        }

        if (Inventory.isFull()) {
            consecutiveLootAttempts = 0;
            return 0;
        }

        // Priority (special + quest): altijd, ook tijdens delay / combat
        if (specialNearby) {
            if (meInCombat() && !specialUnderfoot) {
                pickingUpLoot = true;
                return stopCombatForLoot(pos);
            }
            pickingUpLoot = true;
            lootConsole("priority loot");
            return handleLooting(pos);
        }

        if (!nearby) {
            if (deferNormalLootUntilAfterKill) {
                deferNormalLootUntilAfterKill = false;
            }
            consecutiveLootAttempts = 0;
            return 0;
        }

        if (deferNormalLootUntilAfterKill) {
            return 0;
        }

        // Normale loot: alleen buiten gevecht én kill-drempel gehaald
        if (meInCombat()) {
            return 0;
        }
        if (!isLootAllowed()) {
            int need = lootKillsNeeded();
            setStatus("kill " + killsSinceLastLoot + "/" + need + " → loot later");
            return 0;
        }

        pickingUpLoot = true;
        lootConsole("loot window na " + killsSinceLastLoot + " kills");
        return handleLooting(pos);
    }

    private int stopCombatForLoot(WorldPoint pos) {
        if (Movement.isMoving() && pendingLootStillOnGround()) {
            setStatus("loot walk " + (pendingLootName != null ? pendingLootName : "…"));
            pickingUpLoot = true;
            return ThreadLocalRandom.current().nextInt(120, 220);
        }
        setStatus("stop combat → loot");
        lootConsole("break combat for loot");
        MovementHelper.walkTo(pos);
        pickingUpLoot = true;
        return ThreadLocalRandom.current().nextInt(280, 480);
    }

    private boolean hasImpLootNearby(WorldPoint pos) {
        try {
            return TileItems.getNearest(ti -> isImpLootPickupCandidate(ti, pos)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasLootWithin(WorldPoint pos, int tiles) {
        if (pos == null) {
            return false;
        }
        try {
            return TileItems.getNearest(ti -> ti != null && ti.getWorldLocation() != null
                    && pos.distanceTo(ti.getWorldLocation()) <= tiles
                    && isImpLootPickupCandidate(ti, pos)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasPriorityLootNearby(WorldPoint pos) {
        try {
            return TileItems.getNearest(ti -> isPriorityLootPickup(ti, pos)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasPriorityLootWithin(WorldPoint pos, int tiles) {
        if (pos == null) {
            return false;
        }
        try {
            return TileItems.getNearest(ti -> ti != null && ti.getWorldLocation() != null
                    && pos.distanceTo(ti.getWorldLocation()) <= tiles
                    && isPriorityLootPickup(ti, pos)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isPriorityLootPickup(ITileItem ti, WorldPoint playerPos) {
        if (!isImpLootGroundOk(ti, playerPos)) {
            return false;
        }
        String n = ti.getName();
        if (n == null) {
            return false;
        }
        if (isWizardHatLootName(n)) {
            boolean mage = style == ImpsTypes.ImpsCombatStyle.MAGE;
            return ImpQuestLootStore.shouldPickWizardHat(accountKey, mage);
        }
        if (questLootEnabled && isQuestLootName(n)) {
            return true;
        }
        for (String want : getSpecialLootNames()) {
            if (n.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    private boolean isQuestLootName(String n) {
        if (n == null) {
            return false;
        }
        String ak = accountKey;
        if ("Cadava berries".equalsIgnoreCase(n)) {
            return ImpQuestLootStore.shouldPickCadava(ak);
        }
        if ("Hammer".equalsIgnoreCase(n)) {
            return ImpQuestLootStore.shouldPickHammer(ak);
        }
        if ("Clay".equalsIgnoreCase(n)) {
            return ImpQuestLootStore.shouldPickClay(ak);
        }
        return ImpQuestLootStore.WOOL_ITEM.equalsIgnoreCase(n) && ImpQuestLootStore.shouldPickWool(ak);
    }

    private boolean isImpLootGroundOk(ITileItem ti, WorldPoint playerPos) {
        if (ti == null || ti.getWorldLocation() == null) {
            return false;
        }
        if (ExcludedTiles.isExcluded(ti.getWorldLocation())) {
            return false;
        }
        if (!isInHuntingArea(ti.getWorldLocation())) {
            return false;
        }
        if (avoidScorpions && isScorpionAvoidTile(ti.getWorldLocation())) {
            return false;
        }
        if (playerPos != null && ti.getWorldLocation().distanceTo(playerPos) > Math.max(8, huntRadius)) {
            return false;
        }
        if (isSkippedLootTile(ti.getWorldLocation())) {
            return false;
        }
        return true;
    }

    private boolean isLootAllowed() {
        return killsSinceLastLoot >= lootKillsNeeded();
    }

    /** 0 = loot zodra combat klaar is. */
    private int lootKillsNeeded() {
        if (!lootDelayEnabled) {
            return 0;
        }
        if (lootDelayKills <= 0) {
            return 0;
        }
        if (effectiveLootKills < 0) {
            effectiveLootKills = applyLootKillVariance(lootDelayKills);
            lootConsole("loot na " + effectiveLootKills + " kills (base " + lootDelayKills + ")");
        }
        return effectiveLootKills;
    }

    private int peekLootKillsNeeded() {
        if (!lootDelayEnabled || lootDelayKills <= 0) {
            return 0;
        }
        return effectiveLootKills > 0 ? effectiveLootKills : lootDelayKills;
    }

    /** CombatBot ±1; bij 2 ook random 1–3 zodat het geen vast ritme is. */
    private static int applyLootKillVariance(int base) {
        if (base <= 0) {
            return 0;
        }
        if (base == 1) {
            return ThreadLocalRandom.current().nextBoolean() ? 1 : 2;
        }
        int v = ThreadLocalRandom.current().nextInt(3) - 1;
        return Math.max(1, base + v);
    }

    private int handleLooting(WorldPoint pos) {
        long now = System.currentTimeMillis();
        boolean specialNearby = hasPriorityLootNearby(pos);
        if (meInCombat() && !pickingUpLoot && !hasPriorityLootWithin(pos, 2)) {
            return 0;
        }
        if (now - lastLootMs < LOOT_PICKUP_COOLDOWN_MS) {
            if (pickingUpLoot || specialNearby) {
                pickingUpLoot = true;
                return ThreadLocalRandom.current().nextInt(50, 110);
            }
            return 0;
        }
        if (Inventory.isFull()) {
            clearPendingLoot();
            pickingUpLoot = false;
            return 0;
        }
        if (!isInHuntingArea(pos)) {
            if (pendingLootStillOnGround()) {
                setStatus("loot walk " + (pendingLootName != null ? pendingLootName : "…"));
                pickingUpLoot = true;
                MovementHelper.walkTo(pendingLootTile);
                return ThreadLocalRandom.current().nextInt(200, 360);
            }
            pickingUpLoot = false;
            return 0;
        }

        Players.LocalSnap movingSnap = Players.snapshotLocal();
        boolean moving = Movement.isMoving() || (movingSnap != null && movingSnap.moving);
        if (moving && (pendingLootStillOnGround() || pickingUpLoot)) {
            setStatus("loot walk " + (pendingLootName != null ? pendingLootName : "…"));
            BotRuntime.impStatus = status;
            pickingUpLoot = true;
            return ThreadLocalRandom.current().nextInt(120, 220);
        }

        if (shouldWaitForPendingLoot(pos)) {
            setStatus("loot walk " + (pendingLootName != null ? pendingLootName : "…"));
            BotRuntime.impStatus = status;
            pickingUpLoot = true;
            return ThreadLocalRandom.current().nextInt(120, 220);
        }

        if (questLootEnabled) {
            int q = tryPickupQuestLoot(pos);
            if (q > 0) {
                pickingUpLoot = true;
                return q;
            }
        }

        ITileItem item = null;
        boolean window = isLootAllowed();
        if (specialNearby) {
            item = TileItems.getNearest(ti -> isPriorityLootPickup(ti, pos));
        }
        if (item == null && window) {
            item = TileItems.getNearest(ti -> isImpLootPickupCandidate(ti, pos));
        }
        if (item == null) {
            if (pendingLootStillOnGround()) {
                pickingUpLoot = true;
                MovementHelper.walkTo(pendingLootTile);
                setStatus("loot walk " + (pendingLootName != null ? pendingLootName : "…"));
                return ThreadLocalRandom.current().nextInt(200, 360);
            }
            pickingUpLoot = false;
            consecutiveLootAttempts = 0;
            if (window) {
                killsSinceLastLoot = 0;
                effectiveLootKills = -1;
            }
            return 0;
        }

        pickingUpLoot = true;
        Players.LocalSnap me = Players.snapshotLocal();
        boolean idle = me == null || (!me.moving && me.animation == -1);
        if (idle && !Movement.isMoving()) {
            consecutiveLootAttempts++;
        }
        return startGroundLootPickup(item, isPriorityLootPickup(item, pos) ? "prio loot" : "loot");
    }

    private int tryPickupQuestLoot(WorldPoint pos) {
        String ak = accountKey;
        ITileItem target = null;
        String kind = null;
        if (ImpQuestLootStore.shouldPickCadava(ak)) {
            target = TileItems.getNearest(ti -> ti != null && "Cadava berries".equalsIgnoreCase(ti.getName())
                    && ti.getWorldLocation() != null && isInHuntingArea(ti.getWorldLocation())
                    && !isScorpionAvoidTile(ti.getWorldLocation())
                    && ti.getWorldLocation().distanceTo(pos) <= Math.max(8, huntRadius));
            kind = "cadava";
        }
        if (target == null && ImpQuestLootStore.shouldPickHammer(ak)) {
            target = TileItems.getNearest(ti -> ti != null && "Hammer".equalsIgnoreCase(ti.getName())
                    && ti.getWorldLocation() != null && isInHuntingArea(ti.getWorldLocation())
                    && !isScorpionAvoidTile(ti.getWorldLocation())
                    && ti.getWorldLocation().distanceTo(pos) <= Math.max(8, huntRadius));
            kind = "hammer";
        }
        if (target == null && ImpQuestLootStore.shouldPickClay(ak)) {
            target = TileItems.getNearest(ti -> ti != null && "Clay".equalsIgnoreCase(ti.getName())
                    && ti.getWorldLocation() != null && isInHuntingArea(ti.getWorldLocation())
                    && !isScorpionAvoidTile(ti.getWorldLocation())
                    && ti.getWorldLocation().distanceTo(pos) <= Math.max(8, huntRadius));
            kind = "clay";
        }
        if (target == null && ImpQuestLootStore.shouldPickWool(ak)) {
            target = TileItems.getNearest(ti -> ti != null
                    && ImpQuestLootStore.WOOL_ITEM.equalsIgnoreCase(ti.getName())
                    && ti.getWorldLocation() != null && isInHuntingArea(ti.getWorldLocation())
                    && !isScorpionAvoidTile(ti.getWorldLocation())
                    && ti.getWorldLocation().distanceTo(pos) <= Math.max(8, huntRadius));
            kind = "wool";
        }
        if (target == null) {
            return 0;
        }
        int delay = startGroundLootPickup(target, "quest loot");
        if (delay > 0) {
            int qty = Math.max(1, target.getQuantity());
            if ("cadava".equals(kind)) {
                ImpQuestLootStore.markCadava(ak);
            } else if ("hammer".equals(kind)) {
                ImpQuestLootStore.markHammer(ak);
            } else if ("clay".equals(kind)) {
                ImpQuestLootStore.addClay(ak, qty);
            } else if ("wool".equals(kind)) {
                ImpQuestLootStore.addWool(ak, qty);
            }
        }
        return delay;
    }

    /**
     * Ground Take via {@link net.storm.sdk.interact.GroundLootPickupHelper}
     * (Storm pickup + menu + muis + rechtsklik Take).
     * In beeld → direct Take (geen loop alleen omdat afstand &gt; 5).
     */
    private int startGroundLootPickup(ITileItem item, String statusPrefix) {
        if (item == null || item.getWorldLocation() == null) {
            return 0;
        }
        if (isSkippedLootTile(item.getWorldLocation())
                || GroundLootPickupHelper.isChopSkipTile(item.getWorldLocation())) {
            if (GroundLootPickupHelper.isChopSkipTile(item.getWorldLocation())) {
                skipLootTile(item.getWorldLocation());
            }
            return 0;
        }
        String name = item.getName() != null ? item.getName() : "?";
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint here = me != null ? me.worldLocation : null;
        int dist = here != null ? here.distanceTo(item.getWorldLocation()) : 99;

        boolean onScreen = GroundLootPickupHelper.isItemOnScreen(item);
        // Alleen lopen als niet in beeld — invoke/muis werken op afstand als canvas-punt bestaat
        if (!onScreen) {
            setStatus(statusPrefix + " offscreen→walk " + name);
            BotRuntime.impStatus = status;
            pickingUpLoot = true;
            MovementHelper.walkTo(item.getWorldLocation());
            markPendingLoot(item);
            lastLootMs = System.currentTimeMillis();
            return ThreadLocalRandom.current().nextInt(280, 450);
        }

        // Eerste klik van een chain: AUTO (50/50 RMB). Daarna Take-invoke — sneller ketenen.
        boolean chaining = pickingUpLoot && lastLootMs > 0
                && System.currentTimeMillis() - lastLootMs < 4_000L;
        if (chaining && (lootPickupMode == null || "AUTO".equalsIgnoreCase(lootPickupMode))) {
            GroundLootPickupHelper.setPickupModeFromName("INTERACT_TAKE", false);
        } else {
            GroundLootPickupHelper.setPickupModeFromName(lootPickupMode, lootPickupStrict);
        }

        GroundLootPickupHelper.Result r = GroundLootPickupHelper.pickup(item);
        setStatus(statusPrefix + " " + name
                + (r.method != null ? " [" + r.method.name() + "]" : "")
                + (r.ok ? "" : " fail"));
        BotRuntime.impStatus = status;
        log.info("[Imps] loot {} → {} onScreen=true d={}", name, r, dist);
        lootConsole((r.ok ? "Take " : "Take fail ") + name + " d=" + dist);

        if (!r.ok) {
            if (GroundLootPickupHelper.isOccludedByObject(item)
                    || GroundLootPickupHelper.isChopSkipTile(item.getWorldLocation())
                    || (r.detail != null && (r.detail.contains("occluded")
                    || r.detail.contains("chop-misclick")
                    || r.detail.contains("hover Chop")
                    || r.detail.contains("Chop-menu")))) {
                skipLootTile(item.getWorldLocation());
                lootConsole("skip loot behind tree " + name + " @"
                        + item.getWorldLocation().getX() + "," + item.getWorldLocation().getY());
                pickingUpLoot = true;
                consecutiveLootAttempts = 0;
                return ThreadLocalRandom.current().nextInt(180, 320);
            }
            // Nog steeds zichtbaar → niet meteen lopen; kort wachten / retry volgende tick
            if (!GroundLootPickupHelper.isItemOnScreen(item) && dist > 1) {
                MovementHelper.walkTo(item.getWorldLocation());
                markPendingLoot(item);
                pickingUpLoot = true;
                setStatus(statusPrefix + " walk-retry " + name);
                return ThreadLocalRandom.current().nextInt(280, 450);
            }
            pickingUpLoot = true;
            return ThreadLocalRandom.current().nextInt(160, 280);
        }

        // Succesvolle Take: geen pending-walk (ook niet bij dist>1)
        clearPendingLoot();
        lastLootMs = System.currentTimeMillis();
        consecutiveLootAttempts = 0;
        pickingUpLoot = true;
        AntiBan.get().markBotActivity();

        if (isWizardHatLootName(name)) {
            ImpQuestLootStore.markWizardHatOwned(accountKey);
        }

        if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
            IInventoryItem hat = Inventory.getFirst("Blue wizard hat", "Wizard hat");
            if (hat != null && !Equipment.contains("Blue wizard hat") && !Equipment.contains("Wizard hat")) {
                try {
                    hat.interact("Wear");
                    ImpQuestLootStore.markWizardHatOwned(accountKey);
                } catch (Throwable ignored) {
                }
            } else if (ImpQuestLootStore.hasWizardHatEquipped()) {
                ImpQuestLootStore.markWizardHatOwned(accountKey);
            }
        }
        return ThreadLocalRandom.current().nextInt(160, 340);
    }

    private void skipLootTile(WorldPoint tile) {
        skippedLootTile = tile;
        skippedLootUntilMs = System.currentTimeMillis() + SKIP_OCCLUDED_LOOT_MS;
    }

    private boolean isSkippedLootTile(WorldPoint tile) {
        if (tile == null || skippedLootTile == null || skippedLootUntilMs <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() > skippedLootUntilMs) {
            skippedLootTile = null;
            skippedLootUntilMs = 0L;
            return false;
        }
        return tile.getX() == skippedLootTile.getX()
                && tile.getY() == skippedLootTile.getY()
                && tile.getPlane() == skippedLootTile.getPlane();
    }

    private void markPendingLoot(ITileItem item) {
        if (item == null || item.getWorldLocation() == null) {
            return;
        }
        pendingLootItemId = item.getId();
        pendingLootName = item.getName();
        pendingLootTile = item.getWorldLocation();
        pendingLootUntilMs = System.currentTimeMillis() + LOOT_WALK_TIMEOUT_MS;
    }

    private void clearPendingLoot() {
        pendingLootItemId = -1;
        pendingLootName = null;
        pendingLootTile = null;
        pendingLootUntilMs = 0L;
    }

    /** true = nog onderweg / wachten — geen nieuwe loot-klik. */
    private boolean shouldWaitForPendingLoot(WorldPoint pos) {
        if (pendingLootTile == null || pendingLootUntilMs <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() > pendingLootUntilMs) {
            clearPendingLoot();
            return false;
        }
        if (!pendingLootStillOnGround()) {
            clearPendingLoot();
            return false;
        }
        // Loot intussen in beeld → niet blijven "walk-waiten", direct opnieuw Take
        ITileItem pending = findPendingLootItem();
        if (pending != null && GroundLootPickupHelper.isItemOnScreen(pending) && !Movement.isMoving()) {
            clearPendingLoot();
            return false;
        }
        if (Movement.isMoving()) {
            return true;
        }
        // Nog op de grond, niet in beeld: opnieuw lopen i.p.v. loot-window afbreken
        if (pendingLootStillOnGround() && pendingLootTile != null) {
            MovementHelper.walkTo(pendingLootTile);
            return true;
        }
        clearPendingLoot();
        return false;
    }

    private ITileItem findPendingLootItem() {
        if (pendingLootTile == null) {
            return null;
        }
        final int id = pendingLootItemId;
        final String name = pendingLootName;
        final WorldPoint tile = pendingLootTile;
        return TileItems.getNearest(ti -> {
            if (ti == null || ti.getWorldLocation() == null) {
                return false;
            }
            if (ti.getWorldLocation().distanceTo(tile) > 0) {
                return false;
            }
            if (id > 0 && ti.getId() == id) {
                return true;
            }
            return name != null && name.equalsIgnoreCase(ti.getName());
        });
    }

    private boolean pendingLootStillOnGround() {
        return findPendingLootItem() != null;
    }

    /**
     * Alleen items van de loot-lijst (config), plus wizard hat bij MAGE als we die nog niet hebben.
     * Geen bones/junk buiten de lijst — zoals CombatBot {@code isImpLootPickupCandidate}.
     */
    private boolean isImpLootPickupCandidate(ITileItem ti, WorldPoint playerPos) {
        if (!isImpLootGroundOk(ti, playerPos)) {
            return false;
        }
        String n = ti.getName();
        if (n == null) {
            return false;
        }
        if (isWizardHatLootName(n)) {
            boolean mage = style == ImpsTypes.ImpsCombatStyle.MAGE;
            return ImpQuestLootStore.shouldPickWizardHat(accountKey, mage);
        }
        // Ashes: CombatBot-stijl — kans in de filter, zodat getNearest het volgende item kiest
        if ("Fiendish ashes".equalsIgnoreCase(n)) {
            boolean onList = false;
            for (String want : getLootNames()) {
                if ("Fiendish ashes".equalsIgnoreCase(want)) {
                    onList = true;
                    break;
                }
            }
            if (!onList) {
                return false;
            }
            if (!ashHumanize) {
                return true;
            }
            int pct = Math.max(0, Math.min(100, ashHumanizeChancePercent));
            if (pct <= 0) {
                return false;
            }
            if (pct >= 100) {
                return true;
            }
            return ThreadLocalRandom.current().nextInt(100) < pct;
        }
        for (String want : getLootNames()) {
            if (n.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWizardHatLootName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("blue wizard hat") || lower.equals("wizard hat");
    }

    private boolean shouldScatterAshesNow() {
        int count = Inventory.getCount("Fiendish ashes");
        if (count <= 0) {
            resetScatterBatch();
            return false;
        }
        if (nextAshScatterThreshold <= 0) {
            nextAshScatterThreshold = rollNextAshThreshold();
        }

        boolean forceBank = bankingTrip;
        boolean forceFull = Inventory.isFull();
        boolean thresholdReached = count >= nextAshScatterThreshold;

        if ((forceBank || forceFull || thresholdReached) && !scatteringBatchStarted) {
            int batch;
            if (forceBank) {
                batch = count;
            } else if (forceFull) {
                batch = 1;
            } else {
                int max = Math.min(count, 7);
                batch = Math.max(1, ThreadLocalRandom.current().nextInt(1, max + 1));
            }
            beginScatterBatch(batch, forceBank ? "bank" : (forceFull ? "inv-full" : "threshold " + nextAshScatterThreshold));
        }

        return scatteringBatchStarted && scatterAshesRemainingThisBatch > 0;
    }

    private int rollNextAshThreshold() {
        // 2–9: nooit vast 3, zelden na elke enkele ash
        return ThreadLocalRandom.current().nextInt(2, 10);
    }

    private void beginScatterBatch(int batch, String reason) {
        int count = Inventory.getCount("Fiendish ashes");
        scatterAshesRemainingThisBatch = Math.max(1, Math.min(batch, Math.max(1, count)));
        scatteringBatchStarted = true;
        lootConsole("scatter batch " + scatterAshesRemainingThisBatch
                + "/" + count + " (" + reason + ")");
    }

    private void resetScatterBatch() {
        scatteringBatchStarted = false;
        scatterAshesRemainingThisBatch = 0;
        scatterShiftClearedThisBatch = false;
    }

    private int handleScatterAshes() {
        IInventoryItem ashes = Inventory.getFirst("Fiendish ashes");
        if (ashes == null) {
            resetScatterBatch();
            nextAshScatterThreshold = rollNextAshThreshold();
            return 0;
        }
        if (!scatteringBatchStarted) {
            beginScatterBatch(1, "direct");
        }
        setStatus("scatter ashes (" + scatterAshesRemainingThisBatch + ")");
        BotRuntime.impStatus = status;
        int before = Inventory.getCount("Fiendish ashes");
        int slot = ashes.getSlot();
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint here = me != null && me.present ? me.worldLocation : null;

        try {
            if (!net.storm.sdk.widgets.Tabs.isOpen(net.storm.api.widgets.Tab.INVENTORY)) {
                net.storm.sdk.widgets.Tabs.open(net.storm.api.widgets.Tab.INVENTORY);
            }
        } catch (Throwable ignored) {
        }
        if (!scatterShiftClearedThisBatch) {
            try {
                net.storm.sdk.input.Keyboard.released(java.awt.event.KeyEvent.VK_SHIFT);
            } catch (Throwable ignored) {
            }
            scatterShiftClearedThisBatch = true;
        }

        boolean ok = false;
        try {
            ok = ashes.interact("Scatter");
            log.info("[Imps] scatter menu slot={} before={} ok={}", slot, before, ok);
        } catch (Throwable t) {
            log.warn("[Imps] scatter menu: {}", t.toString());
        }
        if (!ok) {
            try {
                ok = net.storm.sdk.interact.mouse.MouseManager.interactInventorySlot(slot);
                log.info("[Imps] scatter mouse slot={} ok={}", slot, ok);
            } catch (Throwable t) {
                log.warn("[Imps] scatter mouse: {}", t.toString());
            }
        }
        AntiBan.get().markBotActivity();

        int after = Inventory.getCount("Fiendish ashes");
        if (here != null && after < before) {
            try {
                ITileItem ground = TileItems.getNearest(i ->
                        i != null && i.getName() != null
                                && "Fiendish ashes".equalsIgnoreCase(i.getName())
                                && i.getWorldLocation() != null
                                && i.getWorldLocation().distanceTo(here) <= 1);
                if (ground != null) {
                    log.warn("[Imps] scatter → DROP detected @{} — picking up", ground.getWorldLocation());
                    BotRuntime.impStatus = "ashes dropped — pickup";
                    lootConsole("scatter was DROP — pickup");
                    try {
                        ground.interact("Take");
                    } catch (Throwable ignored) {
                    }
                    return mixedDelay(280, 480, 480, 720, 720, 980);
                }
            } catch (Throwable ignored) {
            }
        }

        if (scatterAshesRemainingThisBatch > 0) {
            scatterAshesRemainingThisBatch--;
        }
        int left = Inventory.getCount("Fiendish ashes");
        if (scatterAshesRemainingThisBatch <= 0 || left <= 0) {
            resetScatterBatch();
            nextAshScatterThreshold = rollNextAshThreshold();
            lootConsole("scatter batch done, next≥" + nextAshScatterThreshold);
        }
        return mixedDelay(280, 480, 480, 780, 780, 1280);
    }

    /**
     * Menselijke mix i.p.v. één uniforme min–max (voorkomt vast ritme).
     * ~62% burst, ~28% mid, ~10% aarzeling.
     */
    private static int mixedDelay(int burstMin, int burstMax, int midMin, int midMax, int slowMin, int slowMax) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(100);
        if (roll < 62) {
            return r.nextInt(burstMin, burstMax + 1);
        }
        if (roll < 90) {
            return r.nextInt(midMin, midMax + 1);
        }
        return r.nextInt(slowMin, slowMax + 1);
    }

    /** Zelfde delay als Fish/WC/Star {@link HumanBanking} — deposit = withdraw. */
    private static int bankSlotDelay() {
        return HumanBanking.afterActionMs();
    }

    private void lootConsole(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastLootConsole) && now - lastLootConsoleMs < 1400L) {
            return;
        }
        lastLootConsole = msg;
        lastLootConsoleMs = now;
        BotRuntime.logConsole("[Imp/loot] " + msg);
        ImpActionLog.action(accountKey, msg);
    }

    // ---------------- scorpion ----------------
    // Oude Imp Mode: alleen vluchten bij ECHTE aanval (NPC interact met local).
    // Zone = pad/target vermijden — niet proactief wegrennen als je erin staat / scorpion in buurt.

    private boolean fleeingFromScorpion = false;
    private WorldPoint scorpionFleeTarget = null;
    private long scorpionSafeSinceMs = 0L;
    private static final long SCORPION_SAFE_WAIT_MS = 2_500L;
    /** Random rond safe spot — straal 8 tegels (user capture 2826,3172). */
    private static final int FLEE_RANDOM_OFFSET = 8;
    /** Laatste geldige cmb — timeout 0 telt niet als nog-agro. */
    private int cachedCombatLevel;

    private int handleScorpion(WorldPoint pos) {
        if (!scorpionsAreAggressive() && !isBeingAttackedByScorpion()) {
            logScorpionSafeOnce();
            fleeingFromScorpion = false;
            scorpionFleeTarget = null;
            return 0;
        }
        if (isBeingAttackedByScorpion()) {
            if (!fleeingFromScorpion) {
                scorpionSafeSinceMs = 0L;
                scorpionFleeTarget = rollScorpionFleeTarget();
                log.info("[Imp] Scorpion-aanval — vlucht naar safe-spot buurt {}", scorpionFleeTarget);
            }
            fleeingFromScorpion = true;
        }

        // Soft avoid alleen als scorpions nog agro zijn (CombatBot: cmb > NPC*2+1)
        if (!fleeingFromScorpion && avoidScorpions && !attackScorpions
                && scorpionsAreAggressive()
                && pos != null && isNearLiveScorpion(pos)) {
            if (Movement.isMoving()) {
                setStatus("scorpion radius — lopen");
                return ThreadLocalRandom.current().nextInt(250, 420);
            }
            scorpionFleeTarget = rollScorpionFleeTargetNearSafe(pos);
            setStatus("scorpion radius — safe spot");
            ensureRun();
            MovementHelper.walkTo(scorpionFleeTarget);
            clearPendingLoot();
            return ThreadLocalRandom.current().nextInt(300, 500);
        }

        if (!fleeingFromScorpion) {
            return 0;
        }

        if (!isBeingAttackedByScorpion()) {
            long now = System.currentTimeMillis();
            if (scorpionSafeSinceMs <= 0L) {
                scorpionSafeSinceMs = now;
                setStatus("scorpion weg — kort wachten");
            }
            if (now - scorpionSafeSinceMs < SCORPION_SAFE_WAIT_MS) {
                return ThreadLocalRandom.current().nextInt(200, 400);
            }
            fleeingFromScorpion = false;
            scorpionFleeTarget = null;
            scorpionSafeSinceMs = 0L;
            setStatus("hervat na scorpion");
            return 0;
        }

        scorpionSafeSinceMs = 0L;
        if (attackScorpions && Combat.getHealthPercent() > 50) {
            INPC attacker = findScorpionAttackingUs();
            if (attacker != null) {
                int ammoGate = enforceRangedAmmoOrDelay();
                if (ammoGate > 0) {
                    return ammoGate;
                }
                setStatus("aanval scorpion");
                attacker.interact("Attack");
                AntiBan.get().markBotActivity();
                return ThreadLocalRandom.current().nextInt(450, 700);
            }
        }

        if (Movement.isMoving()) {
            setStatus("vlucht scorpion…");
            return ThreadLocalRandom.current().nextInt(280, 450);
        }
        // CombatBot: nieuw random punt rond safe spot als target bereikt / ongeldig
        if (scorpionFleeTarget == null
                || (pos != null && pos.distanceTo(scorpionFleeTarget) <= 2)
                || (scorpionFleeTarget != null && isNearLiveScorpion(scorpionFleeTarget))) {
            scorpionFleeTarget = rollScorpionFleeTarget();
        }
        setStatus("vlucht scorpion → safe " + scorpionFleeTarget);
        ensureRun();
        MovementHelper.walkTo(scorpionFleeTarget);
        return ThreadLocalRandom.current().nextInt(300, 500);
    }

    private static boolean isScorpionName(String name) {
        return name != null && name.equalsIgnoreCase("Scorpion");
    }

    /** Alleen als een scorpion ons target (Imp Mode). */
    private boolean isBeingAttackedByScorpion() {
        ActorState.ThreatSnap threat = ActorState.threatToLocal();
        if (threat != null && threat.underAttack && isScorpionName(threat.attackerName)) {
            return true;
        }
        return findScorpionAttackingUs() != null;
    }

    private INPC findScorpionAttackingUs() {
        return NPCs.getNearest(n -> {
            if (n == null || !isScorpionName(n.getName())) {
                return false;
            }
            ActorState.NpcSnap snap = ActorState.ofIndex(n.getIndex());
            return snap != null && snap.found && snap.interactingWithLocal;
        });
    }

    private INPC findNearestLiveScorpion(WorldPoint from) {
        if (from == null) {
            return null;
        }
        return NPCs.getNearest(n -> n != null
                && isScorpionName(n.getName())
                && !n.isDead()
                && n.getWorldLocation() != null
                && n.getWorldLocation().getPlane() == from.getPlane());
    }

    /**
     * Random tegel binnen straal {@link #FLEE_RANDOM_OFFSET} rond safe spot {@code 2826,3172}.
     */
    private WorldPoint rollScorpionFleeTarget() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int tries = 0; tries < 32; tries++) {
            int rx = SCORPION_SAFE.getX() + r.nextInt(FLEE_RANDOM_OFFSET * 2 + 1) - FLEE_RANDOM_OFFSET;
            int ry = SCORPION_SAFE.getY() + r.nextInt(FLEE_RANDOM_OFFSET * 2 + 1) - FLEE_RANDOM_OFFSET;
            WorldPoint cand = new WorldPoint(rx, ry, SCORPION_SAFE.getPlane());
            if (cand.distanceTo(SCORPION_SAFE) > FLEE_RANDOM_OFFSET) {
                continue;
            }
            if (isNearLiveScorpion(cand) || isInScorpionZone(cand)) {
                continue;
            }
            return cand;
        }
        return SCORPION_SAFE;
    }

    /** Soft avoid: als we al dicht bij safe spot zijn, klein offset; anders volledige roll. */
    private WorldPoint rollScorpionFleeTargetNearSafe(WorldPoint pos) {
        if (pos != null && pos.distanceTo(SCORPION_SAFE) <= FLEE_RANDOM_OFFSET + 2
                && !isNearLiveScorpion(pos) && !isInScorpionZone(pos)) {
            return pos;
        }
        return rollScorpionFleeTarget();
    }

    private int scorpionAvoidRadius() {
        return scorpionZoneRadius > 0 ? scorpionZoneRadius : DEFAULT_SCORPION_ZONE_RADIUS;
    }

    /**
     * CombatBot / OSRS agro: veilig als jouw combat &gt; NPC-level × 2 + 1.
     * Default scorpion 14 → cmb 30+ valt niet meer aan.
     */
    private boolean scorpionsAreAggressive() {
        if (!avoidScorpions) {
            return false;
        }
        int my = localCombatLevel();
        int npc = scorpionNpcLevel > 0 ? scorpionNpcLevel : 14;
        if (my <= 0) {
            return true;
        }
        return my <= (npc * 2) + 1;
    }

    private int localCombatLevel() {
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            if (me != null && me.present && me.combatLevel > 0) {
                cachedCombatLevel = me.combatLevel;
                return me.combatLevel;
            }
        } catch (Throwable ignored) {
        }
        return cachedCombatLevel;
    }

    private void logScorpionSafeOnce() {
        int my = localCombatLevel();
        int npc = scorpionNpcLevel > 0 ? scorpionNpcLevel : 14;
        int need = (npc * 2) + 1;
        huntConsole("scorpions niet agro cmb=" + my + " > " + npc + "*2+1=" + need
                + " — geen vlucht/zone-skip");
    }

    /** Vaste zones of live Scorpion-NPC. Uit als combat hoog genoeg is (niet agro). */
    private boolean isScorpionAvoidTile(WorldPoint p) {
        if (!scorpionsAreAggressive()) {
            return false;
        }
        return isInScorpionZone(p) || isNearLiveScorpion(p);
    }

    private boolean isInScorpionZone(WorldPoint p) {
        if (p == null) {
            return false;
        }
        int zoneRadius = scorpionAvoidRadius();
        return p.distanceTo(SCORPION_ZONE_1) <= zoneRadius
                || p.distanceTo(SCORPION_ZONE_2) <= zoneRadius;
    }

    private boolean isNearLiveScorpion(WorldPoint p) {
        if (p == null) {
            return false;
        }
        int r = scorpionAvoidRadius();
        return NPCs.getNearest(n -> n != null
                && isScorpionName(n.getName())
                && !n.isDead()
                && n.getWorldLocation() != null
                && n.getWorldLocation().getPlane() == p.getPlane()
                && n.getWorldLocation().distanceTo(p) <= r) != null;
    }

    private void ensureRun() {
        try {
            Movement.ensureTravelRun();
        } catch (RuntimeException ignored) {
        }
    }

    /** Tweede snapshot na boot-teleport: eerste tick kan nog Port Sarim-coords hebben. */
    private WorldPoint livePos(WorldPoint fallback) {
        Players.LocalSnap snap = Players.snapshotLocal();
        if (snap != null && snap.present && snap.worldLocation != null) {
            return snap.worldLocation;
        }
        return fallback;
    }

    // ---------------- banking trip ----------------

    private boolean shouldStartBanking() {
        if (Inventory.isFull()) {
            return true;
        }
        int occupied = Inventory.getCount();
        return occupied >= Math.max(1, bankThreshold);
    }

    /** Loot-stacks die naar de deposit gaan (geen keep-gear/runes/coins). */
    private int countDumpStacks() {
        int stacks = 0;
        for (IInventoryItem i : Inventory.getAll()) {
            if (i == null) {
                continue;
            }
            if (isAlwaysDumpLootId(i.getId())) {
                stacks++;
                continue;
            }
            if (i.getName() == null || i.getName().isEmpty()) {
                continue;
            }
            if (shouldKeepItemByName(i.getName())) {
                continue;
            }
            stacks++;
        }
        return stacks;
    }

    private boolean checkIfRestockNeeded() {
        ImpGearCheck.Snapshot gear = currentGearSnapshot();
        return !gear.adequateSupplies || gear.needCoins > 0 || !gear.combatReady;
    }

    private int handleBankingTrip(WorldPoint pos) {
        if (isOnKaramja(pos)) {
            return payFareToPortSarim(pos);
        }

        if (DepositBox.isOpen()) {
            return handleDepositBoxDeposit();
        }

        // Altijd eerst loot dumpen bij deposit box — ook als restock nodig is.
        // Voorheen: restocking=true → meteen Draynor, Port Sarim stilstand / nooit dump.
        if (hasLootDumpDepositItems()) {
            if (depositBoxFailCount >= MAX_DEPOSIT_BOX_FAILS) {
                setStatus("deposit fail → bank");
                log.warn("[Imps] deposit box failed {}x — fallback bank", depositBoxFailCount);
                return handleBankRestock(pos);
            }
            return openOrWalkDepositBox(pos);
        }

        if (restocking) {
            return handleBankRestock(pos);
        }

        finishBankingTrip("nothing-to-deposit");
        return ThreadLocalRandom.current().nextInt(350, 550);
    }

    private boolean hasLootDumpDepositItems() {
        List<IInventoryItem> all = Inventory.getAll();
        int seen = 0;
        for (IInventoryItem item : all) {
            if (item == null) {
                continue;
            }
            seen++;
            if (isAlwaysDumpLootId(item.getId())) {
                return true;
            }
            if (item.getName() == null || item.getName().isEmpty()) {
                continue;
            }
            if (!shouldKeepItemByName(item.getName())) {
                return true;
            }
        }
        // Client-thread timeout → lege lijst is geen lege inv: trip niet afbreken.
        if (bankingTrip && seen == 0 && Inventory.getCount() > 0) {
            return true;
        }
        return false;
    }

    private boolean isDepositBoxObject(ITileObject o) {
        if (o == null) {
            return false;
        }
        int id = o.getId();
        for (int want : DEPOSIT_BOX_OBJECT_IDS) {
            if (id == want) {
                return true;
            }
        }
        String n = o.getName();
        if (n == null || n.isEmpty() || "null".equalsIgnoreCase(n)) {
            return false;
        }
        String lower = n.toLowerCase(Locale.ROOT);
        return lower.contains("deposit") || lower.contains("bank deposit");
    }

    private int openOrWalkDepositBox(WorldPoint pos) {
        ITileObject box = TileObjects.getNearest(this::isDepositBoxObject);
        ClickOnSight.Result r = ClickOnSight.interactOrApproach(
                box, PORTSARIM_DEPOSIT, "Deposit", "Deposit-All", "Open", "Use");
        setStatus("deposit " + r.outcome.name().toLowerCase());
        BotRuntime.logConsole("[Imp/sight] deposit " + r);
        if (r.clicked) {
            depositBoxFailCount = 0;
            AntiBan.get().markBotActivity();
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        if (r.outcome == ClickOnSight.Outcome.CAMERA
                || r.outcome == ClickOnSight.Outcome.WALK
                || r.outcome == ClickOnSight.Outcome.BLOCKED) {
            return ThreadLocalRandom.current().nextInt(220, 400);
        }
        depositBoxFailCount++;
        setStatus("deposit box zoeken… (" + depositBoxFailCount + ")");
        log.info("[Imps] deposit box not clickable near {} (fail #{}) {}", pos, depositBoxFailCount, r);
        return ThreadLocalRandom.current().nextInt(500, 850);
    }

    private int handleDepositBoxDeposit() {
        if (!hasLootDumpDepositItems()) {
            if (finishBankingTrip("deposit-box")) {
                DepositBox.close();
            }
            if (restocking) {
                setStatus("deposit klaar → restock");
            }
            return bankSlotDelay();
        }

        // Quantity-All: max 1× per trip; al aan (sprites 1150–1158) → geen klik.
        if (!depositQtyAllClicked) {
            depositQtyAllClicked = true;
            String qty = DepositBox.clickQuantityIfOff(DepositBox.QtyMode.ALL);
            setStatus(qty);
            BotRuntime.logConsole("[Imp/bank] " + qty);
            for (String line : DepositBox.dumpQuantityButtons()) {
                BotRuntime.logConsole("[Imp/bank] " + line);
            }
            return mixedDelay(80, 140, 140, 220, 220, 320);
        }

        for (IInventoryItem item : Inventory.getAll()) {
            if (item == null) {
                continue;
            }
            int id = item.getId();
            String name = item.getName();
            boolean dump = isAlwaysDumpLootId(id)
                    || (name != null && !name.isEmpty() && !shouldKeepItemByName(name));
            if (!dump) {
                continue;
            }
            String label = (name != null && !name.isEmpty()) ? name : ("id=" + id);
            setStatus("deposit " + label);
            boolean ok = id > 0 && DepositBox.depositItemId(id);
            if (!ok && name != null && !name.isEmpty()) {
                ok = DepositBox.depositItemNamed(name);
            }
            if (!ok) {
                try {
                    if (item.hasAction("Deposit-All")) {
                        ok = item.interact("Deposit-All");
                    } else if (item.hasAction("Deposit")) {
                        ok = item.interact("Deposit");
                    } else if (item.hasAction("Deposit-1")) {
                        ok = item.interact("Deposit-1");
                    }
                } catch (Throwable t) {
                    log.warn("[Imps] deposit interact: {}", t.toString());
                }
            }
            if (!ok) {
                depositBoxFailCount++;
                setStatus("deposit click fail (" + depositBoxFailCount + ")");
            } else {
                depositBoxFailCount = 0;
                AntiBan.get().markBotActivity();
                if (name != null && ImpQuestLootStore.isQuestBankDepositItem(name)) {
                    ImpQuestLootStore.syncFromInvAndBank(accountKey);
                }
            }
            int wait = bankSlotDelay();
            BotRuntime.logConsole("[Imp/bank] deposit " + label + " id=" + id + " wait " + wait + "ms");
            return wait;
        }
        if (restocking && !hasLootDumpDepositItems()) {
            setStatus("deposit klaar → restock");
            return bankSlotDelay();
        }
        if (finishBankingTrip("deposit-box")) {
            DepositBox.close();
        }
                return bankSlotDelay();
    }

    private int handleBankRestock(WorldPoint pos) {
        if (Bank.isOpen()) {
            // Quest-items eerst storten (gebankt) + sync progress
            if (questLootEnabled) {
                for (IInventoryItem item : Inventory.getAll()) {
                    if (item == null || item.getName() == null) {
                        continue;
                    }
                    if (!ImpQuestLootStore.isQuestBankDepositItem(item.getName())) {
                        continue;
                    }
                    setStatus("bank quest " + item.getName());
                    Bank.depositAll(item.getName());
                    AntiBan.get().markBotActivity();
                    ImpQuestLootStore.syncFromInvAndBank(accountKey);
                    return bankSlotDelay();
                }
                ImpQuestLootStore.syncFromInvAndBank(accountKey);
            }
            // Deposit junk except keep (incl. verkeerde staff)
            for (IInventoryItem item : Inventory.getAll()) {
                if (item == null || item.getName() == null) {
                    continue;
                }
                if (shouldKeepItemByName(item.getName())) {
                    continue;
                }
                setStatus("bank dump " + item.getName());
                Bank.depositAll(item.getName());
                return bankSlotDelay();
            }
            // Withdraw coins
            if (Inventory.getCount("Coins") < minCoins && Bank.contains("Coins")) {
                setStatus("withdraw coins");
                Bank.withdraw("Coins", Integer.MAX_VALUE);
                AntiBan.get().markBotActivity();
                return bankSlotDelay();
            }
            if (style == ImpsTypes.ImpsCombatStyle.RANGED) {
                int a = withdrawAmmoIfNeeded();
                if (a > 0) {
                    return a;
                }
            }
            if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
                int r = withdrawRunesIfNeeded();
                if (r > 0) {
                    return r;
                }
            }
            if (style == ImpsTypes.ImpsCombatStyle.MELEE) {
                int w = withdrawMeleeIfNeeded();
                if (w > 0) {
                    return w;
                }
            }
            int equip = tryEquipCombatKit();
            if (equip > 0) {
                return equip;
            }
            int arm = handleStyleArmourTick();
            if (arm > 0) {
                return arm;
            }
            Bank.leaveOpenForWalk();
            finishBankingTrip("bank-restock");
            return 500;
        }

        WorldPoint bank = nearestRestockBank(pos);
        if (pos.distanceTo(bank) > 8) {
            return applyWalk(ImpWalk.toward(pos, bank, 8, "naar bank restock"));
        }
        setStatus("open bank");
        BankHelper.openNearestBank();
        AntiBan.get().markBotActivity();
        return ThreadLocalRandom.current().nextInt(500, 800);
    }

    private WorldPoint nearestRestockBank(WorldPoint pos) {
        if (pos.distanceTo(DRAYNOR_BANK) <= pos.distanceTo(GE)) {
            return DRAYNOR_BANK;
        }
        return GE;
    }

    private boolean finishBankingTrip(String reason) {
        if (hasLootDumpDepositItems()) {
            BotRuntime.logConsole("[Imp/bank] refuse finish (" + reason + ") — loot still in inv, dump ALL");
            bankingTrip = true;
            return false;
        }
        bankingTrip = false;
        restocking = false;
        depositBoxFailCount = 0;
        depositQtyAllClicked = false;
        bankTripsSinceGe++;
        setStatus("bank klaar (" + reason + ")");
        BotRuntime.logConsole("[Imp/bank] trip done (" + reason + ")");
        log.info("[Imps] finish banking via {}", reason);
        if (geSellEnabled && bankTripsSinceGe >= Math.max(1, geSellAfterBanks)) {
            geSellTrip = true;
            bankTripsSinceGe = 0;
            setStatus("GE sell trip");
        }
        return true;
    }

    // ---------------- boats ----------------

    private int payFareToPortSarim(WorldPoint pos) {
        WorldPoint here = livePos(pos);
        if (here != null && !isOnKaramja(here)) {
            boatLog("Port Sarim aankomst @ " + here.getX() + "," + here.getY());
            awaitingBoatUntilMs = 0;
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        if (Dialog.isOpen()) {
            return handleBoatDialog(true);
        }
        if (Inventory.getCount("Coins") < BOAT_FARE) {
            setStatus("geen 30gp voor boot");
            restocking = true;
            return 800;
        }
        long now = System.currentTimeMillis();
        if (now - lastBoatClickMs < BOAT_CLICK_COOLDOWN_MS) {
            setStatus("boat wait");
            return ThreadLocalRandom.current().nextInt(400, 700);
        }

        if (!inBoatClickOnSightRange(here, KARAMJA_BOAT_APPROACH)) {
            return walkToDockUntilSight(here, KARAMJA_BOAT_APPROACH, "naar Karamja dock");
        }
        return tryBoatNpcOrWalkCloser(here, findKaramjaCustomsOfficer(), true);
    }

    /** ClickOnSight pas ≤ {@link #BOAT_SIGHT_TILES} van de dock-tegel. */
    private static boolean inBoatClickOnSightRange(WorldPoint pos, WorldPoint dock) {
        if (pos == null || dock == null || pos.getPlane() != dock.getPlane()) {
            return false;
        }
        return pos.distanceTo(dock) <= BOAT_SIGHT_TILES;
    }

    /**
     * Reis zoals Imps2: elke tick {@link Movement#walkTo}, geen alreadyEnRoute-wacht.
     * Vanaf Draynor/noord eerst de zuidelijke weg, niet het willow-pad (3064,3254).
     */
    private int walkToDockUntilSight(WorldPoint pos, WorldPoint dock, String status) {
        WorldPoint dest = dock;
        int arrive = 2;
        if (PORTSARIM_DOCK.equals(dock) && pos != null && pos.getPlane() == 0
                && pos.getY() >= 3242 && pos.distanceTo(PORTSARIM_DOCK) > 16) {
            dest = PORTSARIM_DOCK_ROAD;
            arrive = 5;
            status = status + " via weg";
        }
        // Noord van de vulkaan/jungle (zoekpunt B ~2846,3188 of stall ~2869,3187):
        // eerst zuidweg, niet oost door de bomen naar de dock.
        if (KARAMJA_BOAT_APPROACH.equals(dock) && pos != null && pos.getPlane() == 0
                && pos.getY() >= 3172 && pos.getX() < 2915
                && pos.distanceTo(KARAMJA_SOUTH_ROAD) > 6) {
            dest = KARAMJA_SOUTH_ROAD;
            arrive = 5;
            status = status + " via zuidweg";
        }
        return applyWalk(ImpWalk.toward(pos, dest, arrive, status));
    }

    private int applyWalk(ImpWalk.Result r) {
        setStatus(r.status);
        return r.delayMs;
    }

    private void boatLog(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastBoatLog) && now - lastBoatLogMs < 1500L) {
            return;
        }
        lastBoatLog = msg;
        lastBoatLogMs = now;
        BotRuntime.logConsole("[Imp/boat] " + msg);
    }

    private int payFareBack(WorldPoint pos) {
        WorldPoint here = livePos(pos);
        if (here != null && isOnKaramja(here)) {
            boatLog("Musa aankomst @ " + here.getX() + "," + here.getY() + " — geen seaman");
            awaitingBoatUntilMs = 0;
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        if (Dialog.isOpen()) {
            return handleBoatDialog(false);
        }
        if (Inventory.getCount("Coins") < BOAT_FARE) {
            setStatus("geen 30gp — bank coins");
            bankingTrip = true;
            restocking = true;
            return 400;
        }
        long now = System.currentTimeMillis();
        if (now - lastBoatClickMs < BOAT_CLICK_COOLDOWN_MS) {
            setStatus("boat wait");
            return ThreadLocalRandom.current().nextInt(400, 700);
        }

        // Ver weg: alleen pad naar dock. ClickOnSight pas ≤40 tegels.
        if (!inBoatClickOnSightRange(here, PORTSARIM_DOCK)) {
            return walkToDockUntilSight(here, PORTSARIM_DOCK, "naar Port Sarim dock");
        }
        return tryBoatNpcOrWalkCloser(here, findPortSarimBoatNpc(), false);
    }

    /**
     * Seaman/Customs niet in beeld: invoke (off-screen) of dichter naar de NPC-tegel lopen.
     * Niet wachten op de dock-tegel waar we al staan.
     */
    private int tryBoatNpcOrWalkCloser(WorldPoint pos, INPC npc, boolean toPortSarim) {
        WorldPoint here = livePos(pos);
        if (here != null && toPortSarim && !isOnKaramja(here)) {
            boatLog("customs skip — al mainland @ " + here.getX() + "," + here.getY());
            awaitingBoatUntilMs = 0;
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        if (here != null && !toPortSarim && isOnKaramja(here)) {
            boatLog("seaman skip — al Musa @ " + here.getX() + "," + here.getY());
            awaitingBoatUntilMs = 0;
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        if (here != null) {
            pos = here;
        }
        long now = System.currentTimeMillis();
        String[] actions = toPortSarim
                ? new String[]{"Travel", "Port Sarim", "Pay-fare", "Pay-Fare", "Talk-to"}
                : new String[]{"Pay-fare", "Pay-Fare", "Pay fare", "Travel", "Talk-to"};
        String who = toPortSarim ? "customs" : "seaman";
        WorldPoint closer = boatNpcApproach(pos, toPortSarim);

        if (npc != null) {
            ClickOnSight.Result r = ClickOnSight.interactOrApproach(npc, closer, actions);
            int dNpc = npc.getWorldLocation() != null ? pos.distanceTo(npc.getWorldLocation()) : -1;
            boatLog(who + " " + r
                    + " screen=" + ClickOnSight.onScreen(npc)
                    + " can=" + ClickOnSight.can(npc)
                    + " dNpc=" + dNpc);
            setStatus("boot " + who + " " + r.outcome.name().toLowerCase());
            if (r.clicked) {
                lastBoatClickMs = now;
                awaitingBoatUntilMs = now + ThreadLocalRandom.current().nextInt(3500, 5500);
                AntiBan.get().markBotActivity();
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            if (r.outcome == ClickOnSight.Outcome.CAMERA
                    || r.outcome == ClickOnSight.Outcome.BLOCKED) {
                return ThreadLocalRandom.current().nextInt(220, 400);
            }
        } else {
            boatLog(who + " niet in beeld @ " + pos.getX() + "," + pos.getY()
                    + " → loop naar " + closer.getX() + "," + closer.getY());
        }

        int d = pos.distanceTo(closer);
        if (d <= 2) {
            boatLog(who + " dichtbij maar geen klik d=" + d + " npc=" + (npc != null));
            return ThreadLocalRandom.current().nextInt(280, 450);
        }
        return applyWalk(ImpWalk.toward(pos, closer, 2, "dichter bij " + who));
    }

    /** Seaman staat noord van de dock-tegel; Customs niet op de NPC-tegel zelf. */
    private WorldPoint boatNpcApproach(WorldPoint pos, boolean toPortSarim) {
        if (!toPortSarim) {
            return PORTSARIM_BOAT_NPC;
        }
        if (pos != null && pos.distanceTo(KARAMJA_BOAT_APPROACH) <= 3) {
            return KARAMJA_DOCK_ALT_WAYPOINT;
        }
        return KARAMJA_BOAT_APPROACH;
    }

    private INPC findKaramjaCustomsOfficer() {
        return NPCs.getNearest(n -> {
            if (n == null) {
                return false;
            }
            if (n.getId() == CUSTOMS_OFFICER_ID) {
                return true;
            }
            String name = n.getName();
            if (name == null) {
                return false;
            }
            String l = name.toLowerCase(Locale.ROOT);
            return l.contains("customs officer");
        });
    }

    private INPC findPortSarimBoatNpc() {
        return NPCs.getNearest(n -> {
            if (n == null) {
                return false;
            }
            for (int id : PORT_SARIM_SEAMAN_IDS) {
                if (n.getId() == id) {
                    return true;
                }
            }
            String name = n.getName();
            if (name == null) {
                return false;
            }
            String l = name.toLowerCase(Locale.ROOT);
            return l.contains("seaman") || l.contains("captain tobias") || l.contains("lorris") || l.contains("thresnor");
        });
    }

    private int handleBoatDialog(boolean toPortSarim) {
        setStatus("boot dialoog");
        String[] preferred = toPortSarim
                ? new String[]{"Yes", "Okay", "Ok", "Travel", "Pay-fare", "Pay fare", "Port Sarim"}
                : new String[]{"Yes", "Okay", "Ok", "Pay-fare", "Pay fare", "Travel", "Karamja", "Musa Point"};
        for (String opt : preferred) {
            if (Dialog.hasOption(opt) && Dialog.chooseOption(opt)) {
                AntiBan.get().markBotActivity();
                awaitingBoatUntilMs = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(3000, 5000);
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
        }
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            return ThreadLocalRandom.current().nextInt(300, 500);
        }
        return 400;
    }

    // ---------------- GE sell (optional) ----------------

    private int handleGeSell(WorldPoint pos) {
        if (pos.distanceTo(GE) > 12) {
            return applyWalk(ImpWalk.toward(pos, GE, 12, "naar GE verkopen"));
        }
        if (!GrandExchange.isOpen()) {
            // open GE clerk
            INPC clerk = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().toLowerCase(Locale.ROOT).contains("grand exchange"));
            if (clerk != null) {
                clerk.interact("Exchange");
                AntiBan.get().markBotActivity();
                return 700;
            }
            ITileObject booth = TileObjects.getNearest(o -> o != null && o.getName() != null
                    && o.getName().toLowerCase(Locale.ROOT).contains("grand exchange"));
            if (booth != null) {
                if (booth.hasAction("Exchange")) {
                    booth.interact("Exchange");
                } else {
                    booth.interact("Open");
                }
                return 700;
            }
            setStatus("GE openen…");
            return 600;
        }
        // Sell from config CSV
        for (String name : getGeSellNames()) {
            if (Inventory.contains(name)) {
                setStatus("GE sell " + name);
                GrandExchange.sell(name, Inventory.getCount(name), Math.max(1, geSellPrice));
                AntiBan.get().markBotActivity();
                return ThreadLocalRandom.current().nextInt(800, 1200);
            }
        }
        if (GrandExchange.canCollect()) {
            GrandExchange.collect(false);
            return 600;
        }
        geSellTrip = false;
        setStatus("GE sell klaar");
        return 500;
    }

    // ---------------- helpers ----------------

    private boolean shouldKeepItemByName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String n = name.trim();
        // Quest-loot (cadava/hammer/clay/wool) → dump bij deposit + bank (progress blijft in store)
        if (ImpQuestLootStore.isQuestBankDepositItem(n)) {
            return false;
        }
        if (isAlwaysDumpLootName(n)) {
            return false;
        }
        for (String k : getFullKeepList()) {
            if (n.equalsIgnoreCase(k)) {
                return true;
            }
        }
        String lower = n.toLowerCase(Locale.ROOT);
        if (isFoodName(n) || lower.contains("food")) {
            return true;
        }
        // Style-wapen / staff — alleen de juiste (geen air staff bij Fire Strike)
        if (style == ImpsTypes.ImpsCombatStyle.MELEE && isMeleeWeaponName(lower)) {
            return true;
        }
        if (style == ImpsTypes.ImpsCombatStyle.RANGED
                && (lower.contains("bow") || lower.contains("arrow") || lower.contains("crossbow"))) {
            return true;
        }
        if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
            if (isWizardHatLootName(n)) {
                return true;
            }
            if (lower.contains("staff") || lower.contains("wand")) {
                return isPreferredMageStaff(n);
            }
        }
        if (ImpStyleArmourHelper.isStyleArmourName(style, n)) {
            return true;
        }
        // Equipped trip-gear met dezelfde naam (extra stack in inv)
        try {
            if (Equipment.contains(eq -> eq != null && eq.getName() != null
                    && eq.getName().equalsIgnoreCase(n))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // Genie lamp e.d. — niet dumpen
        if (lower.contains("lamp") || lower.contains("clue scroll")) {
            return true;
        }
        return false;
    }

    private List<String> getFullKeepList() {
        List<String> keep = new ArrayList<>(Arrays.asList(KEEP_ITEMS));
        if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
            ImpsTypes.ImpsMageSpell s = resolveMageSpell();
            if (s != null) {
                if (s.getCatalystRune() != null && !keep.contains(s.getCatalystRune())) {
                    keep.add(s.getCatalystRune());
                }
                if (s.needsAirRune() && !keep.contains("Air rune")) {
                    keep.add("Air rune");
                }
                if (s.getElementalRune() != null && !keep.contains(s.getElementalRune())) {
                    keep.add(s.getElementalRune());
                }
                String pref = s.getPreferredStaff();
                boolean preferFire = magicAutoUpdate || s == ImpsTypes.ImpsMageSpell.FIRE_STRIKE;
                if (preferFire) {
                    if (!keep.contains("Staff of fire")) {
                        keep.add("Staff of fire");
                    }
                    if (!keep.contains("Air rune")) {
                        keep.add("Air rune");
                    }
                    // Geen Staff of air in keep bij Fire Strike
                } else if (pref != null && !pref.isBlank() && !keep.contains(pref)) {
                    keep.add(pref.trim());
                }
            }
        }
        for (String armour : ImpStyleArmourHelper.armourItemNamesForStyle(style)) {
            if (armour != null && !armour.isBlank() && !keep.contains(armour)) {
                keep.add(armour);
            }
        }
        return keep;
    }

    /**
     * Bank overbodige combat-items (air staff bij Fire Strike, verkeerd wapen).
     * @return delay ms als gestort, anders 0
     */
    private int depositUnneededCombatGear() {
        if (!Bank.isOpen()) {
            return 0;
        }
        // Equipped verkeerde staff → eerst inventory (als mogelijk)
        try {
            if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
                var eq = Equipment.fromSlot(net.storm.sdk.items.EquipmentSlot.WEAPON);
                if (eq != null && eq.getName() != null
                        && (eq.getName().toLowerCase(Locale.ROOT).contains("staff")
                        || eq.getName().toLowerCase(Locale.ROOT).contains("wand"))
                        && !isPreferredMageStaff(eq.getName())) {
                    // Probeer preferred staff te equippen als die in inv zit
                    for (IInventoryItem inv : Inventory.getAll()) {
                        if (inv != null && inv.getName() != null && isPreferredMageStaff(inv.getName())) {
                            setStatus("equip " + inv.getName() + " (dump oude staff)");
                            inv.interact("Wield");
                            AntiBan.get().markBotActivity();
                            return ThreadLocalRandom.current().nextInt(350, 550);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        for (IInventoryItem item : Inventory.getAll()) {
            if (item == null || item.getName() == null) {
                continue;
            }
            String n = item.getName();
            if (shouldKeepItemByName(n)) {
                continue;
            }
            // Alleen combat-achtige dump hier (staff/wapen/wrong style)
            String lower = n.toLowerCase(Locale.ROOT);
            boolean combatish = lower.contains("staff") || lower.contains("wand")
                    || isMeleeWeaponName(lower)
                    || lower.contains("bow") || lower.contains("arrow");
            if (!combatish && !ImpStyleArmourHelper.isStyleArmourName(style, n)) {
                continue;
            }
            setStatus("bank dump " + n);
            Bank.depositAll(n);
            AntiBan.get().markBotActivity();
            BotRuntime.logConsole("[Imp] deposit unneeded: " + n);
            return bankSlotDelay();
        }
        return 0;
    }

    /** Alleen preferred / fire staff houden bij Fire Strike; air staff → bank. */
    private boolean isPreferredMageStaff(String name) {
        if (name == null) {
            return false;
        }
        ImpsTypes.ImpsMageSpell spell = resolveMageSpell();
        boolean preferFire = magicAutoUpdate
                || spell == ImpsTypes.ImpsMageSpell.FIRE_STRIKE;
        String lower = name.toLowerCase(Locale.ROOT);
        if (preferFire) {
            return lower.contains("fire") && (lower.contains("staff") || lower.contains("wand"));
        }
        String pref = spell != null ? spell.getPreferredStaff() : null;
        if (pref != null && name.equalsIgnoreCase(pref.trim())) {
            return true;
        }
        // Wind Strike e.d.: air staff ok
        return lower.contains("air") && (lower.contains("staff") || lower.contains("wand"));
    }

    private static boolean isMeleeWeaponName(String lower) {
        return lower.contains("sword") || lower.contains("scimitar") || lower.contains("dagger")
                || lower.contains("mace") || lower.contains("battleaxe") || lower.contains("warhammer")
                || lower.contains("halberd") || lower.contains("claws") || lower.contains("whip")
                || (lower.contains("axe") && !lower.contains("pickaxe") && !lower.contains("hatchet"));
    }

    /** Loot die altijd naar deposit/bank mag (niet trip-gear). */
    private static boolean isAlwaysDumpLootId(int id) {
        return id == ID_RED_BEAD || id == ID_YELLOW_BEAD || id == ID_BLACK_BEAD || id == ID_WHITE_BEAD
                || id == ID_MIND_TALISMAN || id == ID_FIENDISH_ASHES
                || id == ID_HAMMER || id == ID_CADAVA || id == ID_CLAY || id == ID_BALL_OF_WOOL;
    }

    private boolean isAlwaysDumpLootName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("bead") || lower.contains("talisman") || lower.equals("fiendish ashes")) {
            return true;
        }
        // Overige loot-lijst items dumpen, behalve runes/arrows (combat keep)
        for (String loot : getLootNames()) {
            if (!loot.equalsIgnoreCase(name)) {
                continue;
            }
            String ll = loot.toLowerCase(Locale.ROOT);
            if (ll.endsWith(" rune") || ll.contains("arrow")) {
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean isFoodName(String n) {
        for (String f : FOOD_NAMES) {
            if (f.equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFood() {
        for (String f : FOOD_NAMES) {
            if (Inventory.contains(f)) {
                return true;
            }
        }
        return false;
    }

    private int eatFood() {
        for (String f : FOOD_NAMES) {
            IInventoryItem food = Inventory.getFirst(f);
            if (food != null) {
                try {
                    food.interact("Eat");
                } catch (Throwable t) {
                    try {
                        food.interact("Drink");
                    } catch (Throwable ignored) {
                    }
                }
                AntiBan.get().markBotActivity();
                return ThreadLocalRandom.current().nextInt(600, 1000);
            }
        }
        return 400;
    }

    private boolean hasAnyArrows() {
        return ImpGearCheck.firstArrowNameOnPerson() != null;
    }

    private ImpGearCheck.Snapshot currentGearSnapshot() {
        return ImpGearCheck.evaluate(style, mageSpell, magicAutoUpdate, minCoins);
    }

    private int tryEquipCombatKit() {
        ImpGearCheck.Snapshot s = currentGearSnapshot();
        if (style == ImpsTypes.ImpsCombatStyle.MELEE && s.needEquipMelee && s.meleeWeapon != null) {
            IInventoryItem w = Inventory.getFirst(s.meleeWeapon);
            if (w != null) {
                setStatus("wield " + s.meleeWeapon);
                try {
                    w.interact("Wield");
                } catch (Throwable ignored) {
                }
                AntiBan.get().markBotActivity();
                return ThreadLocalRandom.current().nextInt(350, 550);
            }
        }
        if (style == ImpsTypes.ImpsCombatStyle.RANGED && s.needEquipBow && s.bowName != null) {
            IInventoryItem b = Inventory.getFirst(s.bowName);
            if (b != null) {
                setStatus("wield " + s.bowName);
                try {
                    b.interact("Wield");
                } catch (Throwable ignored) {
                }
                AntiBan.get().markBotActivity();
                ammoConsole("wield bow " + s.bowName);
                return ThreadLocalRandom.current().nextInt(350, 550);
            }
        }
        if (style == ImpsTypes.ImpsCombatStyle.RANGED && s.needEquipArrows) {
            int rng = Skills.getLevel(Skill.RANGED);
            String equipped = RangedAmmoKit.tryEquipRangedAmmoFromInventory(rng, lastEmptyQuiverMs);
            if (equipped != null && !equipped.isEmpty()) {
                lastEmptyQuiverMs = 0L;
                setStatus("wield " + equipped);
                AntiBan.get().markBotActivity();
                ammoConsole("ammo uit inv → quiver: " + equipped);
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            if (equipped == null) {
                ammoConsole("quiver-fill fail — retry");
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
        }
        if (style == ImpsTypes.ImpsCombatStyle.MAGE && s.needEquipStaff) {
            String staff = s.staffName;
            IInventoryItem st = staff != null ? Inventory.getFirst(staff) : null;
            if (st == null) {
                st = Inventory.getFirst(i -> i != null && i.getName() != null
                        && i.getName().toLowerCase(Locale.ROOT).contains("staff"));
            }
            if (st != null) {
                setStatus("wield " + st.getName());
                try {
                    st.interact("Wield");
                } catch (Throwable ignored) {
                }
                AntiBan.get().markBotActivity();
                return ThreadLocalRandom.current().nextInt(350, 550);
            }
        }
        // Style armour / amulet (F2P tiers)
        ImpStyleArmourHelper.ArmourAction arm = ImpStyleArmourHelper.tryEquipOneFromInventory(style);
        if (arm != null) {
            setStatus(("unequip".equals(arm.slot) ? "unequip " : "wear ") + arm.itemName);
            AntiBan.get().markBotActivity();
            return ThreadLocalRandom.current().nextInt(350, 550);
        }
        return 0;
    }

    /** Bank: één armour/amulet upgrade; anders inventory equip. */
    private int handleStyleArmourTick() {
        if (Bank.isOpen()) {
            ImpStyleArmourHelper.ArmourAction w = ImpStyleArmourHelper.tryWithdrawOneUpgrade(style);
            if (w != null) {
                ImpActionLog.tryDo(accountKey, "bank withdraw armour " + w.itemName + " [" + w.slot + "]");
                setStatus("withdraw " + w.itemName + " (" + w.slot + ")");
                AntiBan.get().markBotActivity();
                return bankSlotDelay();
            }
        }
        ImpStyleArmourHelper.ArmourAction e = ImpStyleArmourHelper.tryEquipOneFromInventory(style);
        if (e != null) {
            setStatus(("unequip".equals(e.slot) ? "unequip " : "wear ") + e.itemName);
            AntiBan.get().markBotActivity();
            return ThreadLocalRandom.current().nextInt(350, 550);
        }
        return 0;
    }

    private int withdrawMeleeIfNeeded() {
        ImpGearCheck.Snapshot s = currentGearSnapshot();
        if (s.meleeWeapon != null) {
            if (s.needEquipMelee) {
                return tryEquipCombatKit();
            }
            return 0;
        }
        String bankW = ImpGearCheck.firstUsableMeleeInBank();
        if (bankW != null) {
            setStatus("withdraw " + bankW);
            Bank.withdraw(bankW, 1);
            AntiBan.get().markBotActivity();
            return bankSlotDelay();
        }
        return 0;
    }

    private int withdrawAmmoIfNeeded() {
        ImpGearCheck.Snapshot s = currentGearSnapshot();
        if (s.bowName == null) {
            String bow = ImpGearCheck.firstBowInBank();
            if (bow != null) {
                setStatus("withdraw " + bow);
                Bank.withdraw(bow, 1);
                AntiBan.get().markBotActivity();
                return bankSlotDelay();
            }
        }
        if (s.needArrows > 0 || s.arrowCount < ImpGearCheck.MIN_ARROWS) {
            String a = s.arrowName != null ? s.arrowName : ImpGearCheck.firstArrowInBank();
            if (a == null) {
                a = ImpGearCheck.firstArrowInBank();
            }
            if (a != null && Bank.contains(a)) {
                setStatus("withdraw " + a);
                Bank.withdraw(a, Integer.MAX_VALUE);
                AntiBan.get().markBotActivity();
                return bankSlotDelay();
            }
        }
        if (s.needEquipBow) {
            return tryEquipCombatKit();
        }
        if (s.needEquipArrows) {
            return tryEquipCombatKit();
        }
        return 0;
    }

    private int withdrawRunesIfNeeded() {
        ImpGearCheck.Snapshot s = currentGearSnapshot();
        ImpsTypes.ImpsMageSpell spell = s.activeSpell != null ? s.activeSpell : resolveMageSpell();
        boolean preferFire = s.preferFireStaff;

        if (s.staffName == null || (preferFire && !ImpGearCheck.hasStaffWithElement("fire"))) {
            String bankStaff = ImpGearCheck.findStaffInBank(spell, preferFire);
            if (bankStaff != null) {
                setStatus("withdraw " + bankStaff);
                Bank.withdraw(bankStaff, 1);
                AntiBan.get().markBotActivity();
                return bankSlotDelay();
            }
        }
        if (s.catalystNeed > 0 && Bank.contains(spell.getCatalystRune())) {
            setStatus("withdraw " + spell.getCatalystRune());
            Bank.withdrawAll(spell.getCatalystRune());
            AntiBan.get().markBotActivity();
            return bankSlotDelay();
        }
        if (s.elementalNeed > 0 && Bank.contains(spell.getElementalRune())) {
            setStatus("withdraw " + spell.getElementalRune());
            Bank.withdrawAll(spell.getElementalRune());
            AntiBan.get().markBotActivity();
            return bankSlotDelay();
        }
        if (s.airNeed > 0 && Bank.contains("Air rune")) {
            setStatus("withdraw Air rune");
            Bank.withdrawAll("Air rune");
            AntiBan.get().markBotActivity();
            return bankSlotDelay();
        }
        if (s.needEquipStaff || !s.staffEquipped) {
            int eq = tryEquipCombatKit();
            if (eq > 0) {
                return eq;
            }
        }
        return 0;
    }

    private ImpsTypes.ImpsMageSpell resolveMageSpell() {
        return ImpGearCheck.resolveSpell(mageSpell, magicAutoUpdate);
    }

    /**
     * CombatBot {@code maybeAutoUpgradeToFireStrike}: Magic ≥ 13 + (auto-update OF Fire Strike gekozen)
     * → forceer Fire Strike + gear-prep als fire staff/runes ontbreken.
     */
    private void maybeForceFireStrikeUpgrade() {
        int mag = Skills.getLevel(Skill.MAGIC);
        boolean wantFire = magicAutoUpdate
                || mageSpell == ImpsTypes.ImpsMageSpell.FIRE_STRIKE;
        if (!wantFire || mag < ImpsTypes.ImpsMageSpell.FIRE_STRIKE.getLevelReq()) {
            return;
        }
        if (mageSpell != ImpsTypes.ImpsMageSpell.FIRE_STRIKE) {
            mageSpell = ImpsTypes.ImpsMageSpell.FIRE_STRIKE;
            Magic.clearAutoCastCache();
            mageAutocastReady = false;
            mageAutocastSpellName = null;
            setStatus("Auto Fire Strike (Magic " + mag + ")");
            BotRuntime.logConsole("[Imp] force Fire Strike (Magic " + mag + ")");
        }
        ImpGearCheck.Snapshot gear = currentGearSnapshot();
        if (!gear.readyToHunt
                || (gear.preferFireStaff && !ImpGearCheck.hasStaffWithElement("fire"))) {
            preparingGear = true;
            gearPrepComplete = false;
        }
    }

    private List<String> getLootNames() {
        List<String> out = new ArrayList<>();
        if (lootCsv != null) {
            for (String p : lootCsv.split(",")) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        if (out.isEmpty()) {
            out.addAll(Arrays.asList("Black bead", "Red bead", "Yellow bead", "White bead",
                    "Mind rune", "Mind talisman", "Fiendish ashes"));
        }
        return out;
    }

    private List<String> getSpecialLootNames() {
        List<String> out = new ArrayList<>();
        if (specialLootCsv != null) {
            for (String p : specialLootCsv.split(",")) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private List<String> getGeSellNames() {
        List<String> out = new ArrayList<>();
        String src = geSellCsv != null && !geSellCsv.isBlank() ? geSellCsv : lootCsv;
        if (src != null) {
            for (String p : src.split(",")) {
                String t = p.trim();
                if (!t.isEmpty() && !t.toLowerCase(Locale.ROOT).endsWith(" rune")
                        && !t.toLowerCase(Locale.ROOT).contains("arrow")) {
                    out.add(t);
                }
            }
        }
        if (out.isEmpty()) {
            out.addAll(Arrays.asList("Black bead", "Red bead", "Yellow bead", "White bead",
                    "Mind talisman", "Fiendish ashes"));
        }
        return out;
    }

    private WorldPoint getHuntCenter() {
        return new WorldPoint(huntX, huntY, 0);
    }

    /** CombatBot Imps Mode zonder center-pin: main + 2 extra circles. */
    private boolean isInHuntingArea(WorldPoint pos) {
        if (pos == null) {
            return false;
        }
        int r = Math.max(1, huntRadius);
        if (pos.distanceTo(getHuntCenter()) <= r) {
            return true;
        }
        if (pos.distanceTo(EXTRA_IMP_AREA_1) <= r) {
            return true;
        }
        if (pos.distanceTo(EXTRA_IMP_AREA_2) <= r) {
            return true;
        }
        if (pos.distanceTo(SEARCH_SPOT_A) <= r) {
            return true;
        }
        return pos.distanceTo(SEARCH_SPOT_B) <= r;
    }

    private WorldPoint nearestHuntApproach(WorldPoint pos) {
        WorldPoint best = RALLY;
        int bestD = pos.distanceTo(RALLY);
        for (WorldPoint c : new WorldPoint[]{
                getHuntCenter(), EXTRA_IMP_AREA_1, EXTRA_IMP_AREA_2,
                SEARCH_SPOT_A, SEARCH_SPOT_B, RALLY}) {
            int d = pos.distanceTo(c);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        // Far from all → rally first (CombatBot dock→hunt pattern)
        if (pos.distanceTo(getHuntCenter()) > huntRadius + 15
                && pos.distanceTo(EXTRA_IMP_AREA_1) > huntRadius + 15
                && pos.distanceTo(EXTRA_IMP_AREA_2) > huntRadius + 15
                && pos.distanceTo(SEARCH_SPOT_A) > huntRadius + 15
                && pos.distanceTo(SEARCH_SPOT_B) > huntRadius + 15) {
            return RALLY;
        }
        return best;
    }

    /** Musa Point → hunt: vast doel + elke tick walkTo (Imps2). */
    private int walkTowardHuntArea(WorldPoint pos) {
        ensureRun();
        if (lockedHuntDest == null) {
            lockedHuntDest = nearestHuntApproach(pos);
            huntConsole("hunt-doel vast " + lockedHuntDest.getX() + "," + lockedHuntDest.getY());
        }
        ImpWalk.Result r = ImpWalk.toward(pos, lockedHuntDest, 8, "Musa → imps");
        if (r.arrived) {
            lockedHuntDest = null;
        }
        return applyWalk(r);
    }

    private void huntConsole(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastHuntConsole) && now - lastHuntConsoleMs < 1600L) {
            return;
        }
        lastHuntConsole = msg;
        lastHuntConsoleMs = now;
        BotRuntime.logConsole("[Imp/hunt] " + msg);
    }

    /**
     * CombatBot {@code enforceImpsRangedAmmoOrDelay}: geen Attack met lege quiver.
     * Failsafe: Wield-retry dezelfde ammo; geen andere skill.
     */
    private int enforceRangedAmmoOrDelay() {
        if (style != ImpsTypes.ImpsCombatStyle.RANGED) {
            return 0;
        }
        try {
            if (Equipment.contains("Toxic blowpipe")) {
                return 0;
            }
        } catch (Throwable ignored) {
        }
        pollEmptyQuiverChat();
        int kit = tryEquipCombatKit();
        if (kit > 0) {
            return kit;
        }
        int rng = Skills.getLevel(Skill.RANGED);
        if (!RangedAmmoKit.quiverNeedsRefillFromInventory(lastEmptyQuiverMs)) {
            return 0;
        }
        if (RangedAmmoKit.inventoryHasUsableRangedAmmo(rng)) {
            String equipped = RangedAmmoKit.tryEquipRangedAmmoFromInventory(rng, lastEmptyQuiverMs);
            if (equipped == null) {
                ammoConsole("quiver-fill fail — volgende tick retry");
                setStatus("arrows wield retry");
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            if (!equipped.isEmpty()) {
                lastEmptyQuiverMs = 0L;
                ammoConsole("ammo uit inv → quiver: " + equipped);
                setStatus("wield " + equipped);
            }
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        ammoConsole("quiver leeg, geen arrows in inv — gear prep");
        preparingGear = true;
        gearPrepComplete = false;
        setStatus("geen ammo — gear prep");
        return ThreadLocalRandom.current().nextInt(350, 550);
    }

    private void pollEmptyQuiverChat() {
        List<String> msgs;
        try {
            msgs = Chat.getRecentMessages(8);
        } catch (Throwable t) {
            return;
        }
        if (msgs == null || msgs.isEmpty()) {
            return;
        }
        String newest = msgs.get(msgs.size() - 1);
        if (newest == null) {
            return;
        }
        if (lastSeenGameMsg.isEmpty()) {
            lastSeenGameMsg = newest;
            return;
        }
        if (newest.equals(lastSeenGameMsg)) {
            return;
        }
        for (int i = msgs.size() - 1; i >= 0; i--) {
            String m = msgs.get(i);
            if (m == null) {
                continue;
            }
            if (m.equals(lastSeenGameMsg)) {
                break;
            }
            if (RangedAmmoKit.isRangedEmptyQuiverGameMessage(m)) {
                lastEmptyQuiverMs = System.currentTimeMillis();
                ammoConsole("game: geen ammo in quiver");
            }
        }
        lastSeenGameMsg = newest;
    }

    private void ammoConsole(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastAmmoConsole) && now - lastAmmoConsoleMs < 1600L) {
            return;
        }
        lastAmmoConsole = msg;
        lastAmmoConsoleMs = now;
        BotRuntime.logConsole("[Imp/ammo] " + msg);
    }

    /** Geen imp in scene: actief heen-en-weer rond de twee zoekpunten tot er één is. */
    private int roamForImp(WorldPoint pos) {
        WorldPoint dest = nextSearchWalk(pos);
        String tag = (searchSpotIndex & 1) == 0 ? "A" : "B";
        setStatus("zoek imp " + tag + " " + dest.getX() + "," + dest.getY());
        MovementHelper.walkTo(dest);
        lastIdleRoamMs = System.currentTimeMillis();
        huntConsole("roam walk " + dest.getX() + "," + dest.getY() + " spot=" + tag);
        return ThreadLocalRandom.current().nextInt(180, 320);
    }

    private WorldPoint nextSearchWalk(WorldPoint pos) {
        WorldPoint[] spots = {SEARCH_SPOT_A, SEARCH_SPOT_B};
        if (pos.distanceTo(SEARCH_SPOT_A) > 12 && pos.distanceTo(SEARCH_SPOT_B) > 12) {
            searchSpotIndex = pos.distanceTo(SEARCH_SPOT_A) <= pos.distanceTo(SEARCH_SPOT_B) ? 0 : 1;
        }
        WorldPoint cur = spots[searchSpotIndex & 1];
        if (pos.distanceTo(cur) <= 4) {
            searchSpotIndex = 1 - (searchSpotIndex & 1);
            cur = spots[searchSpotIndex & 1];
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            int j = 4;
            int dx = ThreadLocalRandom.current().nextInt(-j, j + 1);
            int dy = ThreadLocalRandom.current().nextInt(-j, j + 1);
            WorldPoint p = new WorldPoint(cur.getX() + dx, cur.getY() + dy, cur.getPlane());
            if (avoidScorpions && isScorpionAvoidTile(p)) {
                continue;
            }
            if (ExcludedTiles.isExcluded(p)) {
                continue;
            }
            return p;
        }
        if (avoidScorpions && isScorpionAvoidTile(cur)) {
            WorldPoint fallback = randomInHunt();
            return fallback != null ? fallback : cur;
        }
        return cur;
    }

    private WorldPoint randomInHunt() {
        WorldPoint[] centers = {
                getHuntCenter(), EXTRA_IMP_AREA_1, EXTRA_IMP_AREA_2, SEARCH_SPOT_A, SEARCH_SPOT_B
        };
        WorldPoint c = centers[ThreadLocalRandom.current().nextInt(centers.length)];
        int r = Math.max(2, huntRadius / 2);
        int dx = ThreadLocalRandom.current().nextInt(-r, r + 1);
        int dy = ThreadLocalRandom.current().nextInt(-r, r + 1);
        return new WorldPoint(c.getX() + dx, c.getY() + dy, c.getPlane());
    }

    // ---------------- gear prep + GE buy ----------------

    private boolean hasCombatKitReady() {
        return currentGearSnapshot().readyToHunt;
    }

    private int handleGearPreparation(WorldPoint pos) {
        ImpGearCheck.Snapshot gear = currentGearSnapshot();

        // Equip zonder bank als supplies al op zak (wapen + armour)
        if (!Bank.isOpen()) {
            int eq = tryEquipCombatKit();
            if (eq > 0) {
                return eq;
            }
        }

        // Combat-kit klaar → nog bank-armour best-effort, daarna done
        if (gear.readyToHunt) {
            if (Bank.isOpen()) {
                int arm = handleStyleArmourTick();
                if (arm > 0) {
                    return arm;
                }
                Bank.leaveOpenForWalk();
                preparingGear = false;
                gearPrepComplete = true;
                setStatus("gear klaar (+armour)");
                return 400;
            }
            // Nog armour in bank? → even bank openen
            if (!isOnKaramja(pos)) {
                // Alleen banken voor armour als we dichtbij zijn; anders klaar
                preparingGear = false;
                gearPrepComplete = true;
                setStatus("gear klaar (" + ImpGearCheck.statusLine(gear) + ")");
                return 300;
            }
            preparingGear = false;
            gearPrepComplete = true;
            setStatus("gear klaar");
            return 300;
        }

        // Op Karamja: eerst boot naar Port Sarim (bank/GE liggen op mainland)
        if (isOnKaramja(pos)) {
            return payFareToPortSarim(pos);
        }

        if (Bank.isOpen()) {
            // Eerst overbodige gear storten (air staff bij fire strike, verkeerd wapen, …)
            int dump = depositUnneededCombatGear();
            if (dump > 0) {
                return dump;
            }
            if (Inventory.getCount("Coins") < Math.max(minCoins, ImpGearCheck.BOAT_FARE) && Bank.contains("Coins")) {
                setStatus("withdraw coins");
                Bank.withdraw("Coins", Math.max(minCoins * 2, 200));
                AntiBan.get().markBotActivity();
                return bankSlotDelay();
            }
            if (style == ImpsTypes.ImpsCombatStyle.MELEE) {
                int w = withdrawMeleeIfNeeded();
                if (w > 0) {
                    return w;
                }
            } else if (style == ImpsTypes.ImpsCombatStyle.RANGED) {
                int a = withdrawAmmoIfNeeded();
                if (a > 0) {
                    return a;
                }
            } else {
                int r = withdrawRunesIfNeeded();
                if (r > 0) {
                    return r;
                }
            }
            int eq = tryEquipCombatKit();
            if (eq > 0) {
                return eq;
            }
            int arm = handleStyleArmourTick();
            if (arm > 0) {
                return arm;
            }

            gear = currentGearSnapshot();
            if (gear.readyToHunt) {
                Bank.leaveOpenForWalk();
                preparingGear = false;
                gearPrepComplete = true;
                setStatus("gear klaar");
                return 400;
            }
            // Bank heeft tekort → GE (alleen als restock aan) — geen GE voor pure armour
            if (geRestockEnabled && !bankCanFillGear(gear)) {
                Bank.leaveOpenForWalk();
                startGeBuyForDeficit(gear);
                preparingGear = false;
                return 400;
            }
            Bank.close();
            preparingGear = true;
            gearPrepComplete = false;
            setStatus("gear tekort (geen bank/GE): " + gear.reason);
            log.warn("[Imps] gear prep blocked: {}", gear.reason);
            return ThreadLocalRandom.current().nextInt(1200, 1800);
        }

        WorldPoint bank = nearestRestockBank(pos);
        if (pos.distanceTo(bank) > 8) {
            return applyWalk(ImpWalk.toward(pos, bank, 8, "gear prep → bank (" + gear.reason + ")"));
        }
        setStatus("gear prep open bank");
        BankHelper.openNearestBank();
        return ThreadLocalRandom.current().nextInt(500, 800);
    }

    /** Bank open: kan bank nog iets leveren voor huidige tekorten? */
    private boolean bankCanFillGear(ImpGearCheck.Snapshot gear) {
        if (!Bank.isOpen() || gear == null) {
            return false;
        }
        if (gear.needCoins > 0 && Bank.contains("Coins")) {
            return true;
        }
        if (style == ImpsTypes.ImpsCombatStyle.MELEE) {
            return ImpGearCheck.firstUsableMeleeInBank() != null;
        }
        if (style == ImpsTypes.ImpsCombatStyle.RANGED) {
            if (gear.bowName == null && ImpGearCheck.firstBowInBank() != null) {
                return true;
            }
            return gear.needArrows > 0 && ImpGearCheck.firstArrowInBank() != null;
        }
        ImpsTypes.ImpsMageSpell spell = gear.activeSpell != null ? gear.activeSpell : resolveMageSpell();
        if (gear.preferFireStaff && !ImpGearCheck.hasStaffWithElement("fire")
                && ImpGearCheck.findStaffInBank(spell, true) != null) {
            return true;
        }
        if (!ImpGearCheck.hasAnyStaffOnPerson()
                && ImpGearCheck.findStaffInBank(spell, gear.preferFireStaff) != null) {
            return true;
        }
        if (gear.catalystNeed > 0 && Bank.contains(spell.getCatalystRune())) {
            return true;
        }
        if (gear.elementalNeed > 0 && Bank.contains(spell.getElementalRune())) {
            return true;
        }
        return gear.airNeed > 0 && Bank.contains("Air rune");
    }

    private void startGeBuyForDeficit(ImpGearCheck.Snapshot gear) {
        if (gear == null) {
            gear = currentGearSnapshot();
        }
        if (style == ImpsTypes.ImpsCombatStyle.RANGED) {
            if (gear.bowName == null) {
                geBuyItem = "Shortbow";
                geBuyQty = 1;
                geBuyPrice = 80;
            } else {
                geBuyItem = gear.arrowName != null ? gear.arrowName : "Bronze arrow";
                geBuyQty = ImpGearCheck.ARROW_WITHDRAW;
                geBuyPrice = 10;
            }
        } else if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
            ImpsTypes.ImpsMageSpell spell = gear.activeSpell != null ? gear.activeSpell : resolveMageSpell();
            if (gear.preferFireStaff && !ImpGearCheck.hasStaffWithElement("fire")) {
                geBuyItem = "Staff of fire";
                geBuyQty = 1;
                geBuyPrice = 2500;
            } else if (!ImpGearCheck.hasAnyStaffOnPerson()) {
                geBuyItem = spell.getPreferredStaff();
                geBuyQty = 1;
                geBuyPrice = 2000;
            } else if (gear.catalystNeed > 0) {
                geBuyItem = spell.getCatalystRune();
                geBuyQty = ImpGearCheck.CATALYST_WITHDRAW;
                geBuyPrice = spell.getCatalystRune().toLowerCase(Locale.ROOT).contains("chaos") ? 120 : 50;
            } else if (gear.airNeed > 0) {
                geBuyItem = "Air rune";
                geBuyQty = ImpGearCheck.AIR_WITHDRAW;
                geBuyPrice = 10;
            } else if (gear.elementalNeed > 0) {
                geBuyItem = spell.getElementalRune();
                geBuyQty = ImpGearCheck.CATALYST_WITHDRAW;
                geBuyPrice = 20;
            } else {
                geBuyItem = spell.getPreferredStaff();
                geBuyQty = 1;
                geBuyPrice = 2000;
            }
        } else {
            // Melee GE alleen als echt geen wieldbaar wapen — skip (user confirm was for new GE)
            geBuying = false;
            return;
        }
        geBuying = true;
        setStatus("GE buy " + geBuyItem);
    }

    private int handleGeBuy(WorldPoint pos) {
        if (geBuyItem == null) {
            geBuying = false;
            return 300;
        }
        if (isOnKaramja(pos)) {
            return payFareToPortSarim(pos);
        }
        if (Inventory.contains(geBuyItem) && (geBuyQty <= 1 || Inventory.getCount(geBuyItem) >= Math.min(20, geBuyQty))) {
            geBuying = false;
            ImpGearCheck.Snapshot gear = currentGearSnapshot();
            gearPrepComplete = gear.readyToHunt;
            preparingGear = !gearPrepComplete && gearPrepEnabled;
            setStatus("GE buy klaar → " + (gearPrepComplete ? "ready" : gear.reason));
            return 400;
        }
        if (Inventory.getCount("Coins") < geBuyPrice * Math.max(1, geBuyQty / 10)) {
            if (!Bank.isOpen()) {
                if (pos.distanceTo(GE) > 12 && pos.distanceTo(DRAYNOR_BANK) > 12) {
                    return applyWalk(ImpWalk.toward(pos, nearestRestockBank(pos), 12, "GE coins → bank"));
                }
                BankHelper.openNearestBank();
                return 600;
            }
            if (Bank.contains("Coins")) {
                Bank.withdraw("Coins", Integer.MAX_VALUE);
                return bankSlotDelay();
            }
            Bank.close();
        }
        setStatus("GE buy " + geBuyItem);
        boolean ok = net.storm.sdk.items.GeRestockHelper.buyWithEscalation(geBuyItem, geBuyQty, geBuyPrice);
        AntiBan.get().markBotActivity();
        if (ok) {
            return ThreadLocalRandom.current().nextInt(900, 1400);
        }
        return ThreadLocalRandom.current().nextInt(600, 900);
    }

    private boolean isOnKaramja(WorldPoint p) {
        if (p == null) {
            return false;
        }
        if (KaramjaVolcano.isInDungeon(p)) {
            return true;
        }
        // Bounding box from CombatBot (surface Musa Point)
        return p.getX() >= 2815 && p.getX() <= 2962 && p.getY() >= 3130 && p.getY() <= 3210;
    }

    private boolean meInCombat() {
        try {
            return Combat.isInCombat();
        } catch (Throwable t) {
            Players.LocalSnap me = Players.snapshotLocal();
            return me != null && me.interacting;
        }
    }
}
