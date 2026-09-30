package com.lonebot.example.fishing;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.game.BankOpenZones;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Game;
import net.storm.sdk.game.Skills;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankSession;
import net.storm.sdk.items.BankSnapshot;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.GeRestockHelper;
import net.storm.sdk.items.GrandExchange;
import net.storm.sdk.items.Inventory;
import net.storm.api.movement.TilePath;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.pathfinder.Pathfinder;
import net.storm.sdk.travel.VarrockGeTravelHelper;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Production;
import net.storm.sdk.widgets.Widgets;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CombatBot FishingHandler-port: unified bank, GE restock, cook, drop, spot-interact.
 */
final class FishingLoop {

    private static final Logger log = LoggerFactory.getLogger(FishingLoop.class);

    private static final int GENIE_LAMP_ITEM_ID = 2528;
    private static final int LOCATION_THRESHOLD = 5;
    private static final int BARBARIAN_MIN_RADIUS = 22;
    /** Alleen klikken/stoppen als de spot écht dichtbij is — anders doorlopen naar het center. */
    private static final int SPOT_IN_REACH_TILES = 12;
    private static final WorldPoint DRAYNOR_FIRE = new WorldPoint(3096, 3237, 0);
    private static final WorldPoint BARBARIAN_FIRE = new WorldPoint(3106, 3432, 0);
    /** CombatBot: Barbarian/Edgeville-vissen → altijd deze bank, nooit Cooking Guild. */
    private static final WorldPoint EDGEVILLE_BANK = new WorldPoint(3096, 3492, 0);
    /** Loopbare tegel vóór de booths — 3096,3492 is vaak de booth zelf (niet loopbaar). */
    private static final WorldPoint EDGEVILLE_BANK_STAND = new WorldPoint(3094, 3491, 0);
    private static final WorldPoint VARROCK_WEST_BANK = new WorldPoint(3189, 3436, 0);
    private static final int EDGEVILLE_FISHING_PROFILE_RADIUS = 80;
    private static final long IDLE_TIMEOUT_MS = 30_000L;
    private static final long INTERACT_COOLDOWN_MS = 1400L;
    private static final int FISHING_POST_CLICK_GRACE_MIN_MS = 5000;
    private static final int FISHING_POST_CLICK_GRACE_MAX_MS = 8000;
    private static final int MAX_BANK_RETRIES = 5;
    private static final int FISHING_RESTOCK_STACK_TARGET = 1000;
    private static final int GE_PRICE_FISHING_ROD = 1000;
    private static final int GE_PRICE_SMALL_NET = 500;
    private static final int GE_PRICE_HARPOON = 1500;
    private static final int GE_PRICE_LOBSTER_POT = 1500;
    private static final int GE_PRICE_DEFAULT_TOOL = 1000;
    private static final long COOKING_FIRE_LOCK_MS = 5000L;
    private static final long FATAL_BANK_ROUTE_GRACE_MS = 90_000L;
    private static final long BANK_ROUTE_FAIL_CHAIN_WINDOW_MS = 20_000L;
    private static final int BANK_ROUTE_FAILS_BEFORE_FATAL = 12;
    /** Anti-spam als je stilstaat. Tijdens lopen: steden-cirkel-tempo (~280 ms). */
    private static final long TRAVEL_RECLICK_MS = 450L;
    private static final long TRAVEL_CHAIN_WHILE_MOVING_MS = 280L;
    private static final String[] EMERGENCY_SELL = {
            "Black bead", "Red bead", "Yellow bead", "White bead", "Mind talisman"
    };

    enum State {
        FISHING, DROPPING, WALKING_TO_BANK, BANKING, WALKING_TO_SPOT,
        WALKING_BACK_TO_SPOT, RESTOCK_WALKING_TO_GE, RESTOCKING,
        WALKING_TO_FIRE, COOKING, IDLE
    }

    private final BankSession bankSession = new BankSession();
    private boolean bankSessionArmed;
    private State state = State.IDLE;
    private State lastLoggedState;
    private FishingCenters.Center activeCenter;
    private WorldPoint fishingSpot;
    private WorldPoint preBankPosition;
    private int areaRadius = 10;
    private long idleStartTime;
    private long lastInteractTime;
    private long fishingPostClickGraceUntil;
    private long lastTravelClickTime;
    private WorldPoint walkTargetSpot;
    private long walkTargetSetTime;
    /** Barbarian vanaf het noorden: vuur ±3, één tegel per bank-trip. */
    private WorldPoint barbarianFireApproachDest;
    private String lastSpotScanLine = "";
    private boolean needsCooking;
    private WorldPoint cookingFireLocation;
    private String lastCookingAttemptItemName;
    private long cookingFireClickTime;
    private boolean bankSessionCompleted;
    private State afterBankClose = State.IDLE;
    private int bankRetries;
    private int pendingGeCoinsWithdrawTarget;
    private int consecutiveBankRoutePathFails;
    private long lastBankRoutePathFailMs;
    private long fatalBankRouteGraceUntilMs;
    private String status = "idle";
    private final Set<String> seenChat = new HashSet<>();
    private long lastChatPollMs;
    /** Overlay/debug mag geen fallback-log triggeren na Stop. */
    private String[] overlayMethodCache;
    private boolean haltedWhileOff;

    String getStatus() {
        return status;
    }

    private static boolean scriptRunning() {
        return BotRuntime.botEnabled && BotRuntime.fishingEnabled;
    }

    private String humanState(State s, String[] method) {
        String loc = activeCenter != null && activeCenter.name != null ? activeCenter.name : "";
        String act = method != null && method.length > 1 ? method[1] : "";
        if (s == null) {
            return "idle";
        }
        switch (s) {
            case WALKING_TO_BANK:
                return "inv vol → lopen naar bank" + (loc.isEmpty() ? "" : " (" + loc + ")");
            case BANKING:
                return "banken";
            case WALKING_TO_SPOT:
            case WALKING_BACK_TO_SPOT:
                return "lopen naar spot" + (loc.isEmpty() ? "" : " (" + loc + ")");
            case FISHING:
                return (act.isEmpty() ? "vissen" : act)
                        + (loc.isEmpty() ? "" : " @ " + loc);
            case DROPPING:
                return "drop vis";
            case WALKING_TO_FIRE:
                return "lopen naar vuur";
            case COOKING:
                return "koken";
            case RESTOCK_WALKING_TO_GE:
                return "lopen naar GE (restock)";
            case RESTOCKING:
                return "GE restock";
            default:
                return s.name();
        }
    }

    /** Stop/Pauze: geen pad meer, geen walker. Overlay blijft tekenen. */
    void haltIfStopped() {
        if (haltedWhileOff) {
            return;
        }
        haltedWhileOff = true;
        try {
            MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        try {
            net.storm.sdk.movement.WorldWalker.cancel();
        } catch (Throwable ignored) {
        }
        walkTargetSpot = null;
        lastTravelClickTime = 0L;
        barbarianFireApproachDest = null;
    }

    /**
     * Spot is klikbaar / we vissen al: LoopHost.tickActive mag het pad-eind
     * (Barbarian-center) niet blijven klikken.
     */
    private void stopTravelForSpot(String reason) {
        WorldPoint ad = null;
        try {
            ad = MovementHelper.getActiveDestination();
        } catch (Throwable ignored) {
        }
        walkTargetSpot = null;
        barbarianFireApproachDest = null;
        if (ad == null) {
            return;
        }
        try {
            MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        FishDebug.once("walk", "stop pad-eind (" + reason + ") was @"
                + ad.getX() + "," + ad.getY());
    }

    void prepareOverlay() {
        // bewust leeg: refreshCenter() alleen in tick() als bot AAN
    }

    List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        lines.add("State: " + (state != null ? state.name() : "?"));
        if (activeCenter != null && activeCenter.point != null) {
            lines.add("Center: " + activeCenter.name
                    + " @" + activeCenter.point.getX() + "," + activeCenter.point.getY()
                    + " r=" + activeCenter.radius);
        } else {
            lines.add("Center: (geen)");
        }
        lines.add("Pref: " + (FishingPlugin.preferredLocation != null
                ? FishingPlugin.preferredLocation : "AUTO"));
        String[] m = overlayMethodForDisplay();
        lines.add("Method: " + m[1] + " / " + m[2] + (m[3].isEmpty() ? "" : " + " + m[3]));
        lines.add("Drop=" + (FishingPlugin.dropFish ? "aan" : "uit")
                + " | cook=" + (FishingPlugin.cookEnabled ? "aan" : "uit")
                + " | GE=" + (FishingPlugin.restockEnabled ? "aan" : "uit")
                + " | inv=" + Inventory.getCount() + "/28");
        lines.add("Bait min=" + FishingPlugin.baitMin
                + " | retries=" + bankRetries + "/" + MAX_BANK_RETRIES);
        if (lastSpotScanLine != null && !lastSpotScanLine.isEmpty()) {
            lines.add(lastSpotScanLine);
        }
        return lines;
    }

    void reset() {
        state = State.IDLE;
        lastLoggedState = null;
        activeCenter = null;
        fishingSpot = null;
        preBankPosition = null;
        idleStartTime = 0L;
        lastInteractTime = 0L;
        fishingPostClickGraceUntil = 0L;
        lastTravelClickTime = 0L;
        walkTargetSpot = null;
        walkTargetSetTime = 0L;
        barbarianFireApproachDest = null;
        needsCooking = false;
        cookingFireLocation = null;
        lastCookingAttemptItemName = null;
        cookingFireClickTime = 0L;
        bankSessionCompleted = false;
        afterBankClose = State.IDLE;
        bankRetries = 0;
        pendingGeCoinsWithdrawTarget = 0;
        consecutiveBankRoutePathFails = 0;
        lastBankRoutePathFailMs = 0L;
        fatalBankRouteGraceUntilMs = System.currentTimeMillis() + FATAL_BANK_ROUTE_GRACE_MS;
        status = "idle";
        seenChat.clear();
        lastChatPollMs = 0L;
        overlayMethodCache = null;
        haltedWhileOff = false;
        FishDebug.clearOnce();
        bankSession.reset();
        bankSessionArmed = false;
    }

    void onGameMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String lower = message.replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT);
        if (!lower.contains("you can't cook that") && !lower.contains("you cannot cook that")) {
            return;
        }
        needsCooking = false;
        cookingFireClickTime = 0L;
        cookingFireLocation = null;
        state = FishingPlugin.dropFish ? State.DROPPING : State.WALKING_TO_BANK;
        FishDebug.once("cook", "can't cook " + lastCookingAttemptItemName + " → " + state);
        lastCookingAttemptItemName = null;
    }

    int tick() {
        if (!scriptRunning()) {
            haltIfStopped();
            status = "bot uit";
            return 800;
        }
        haltedWhileOff = false;
        if (!Game.isLoggedIn()) {
            status = "niet ingelogd";
            return 1000;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present) {
            status = "geen speler";
            return 600;
        }
        int anti = AntiBan.get().check();
        if (anti > 0 && state != State.COOKING && state != State.RESTOCKING
                && state != State.BANKING && state != State.DROPPING) {
            status = "antiban";
            return Math.min(anti, 2500);
        }
        try {
            pollChatMessages();
            refreshCenter();
            String[] method = getTargetMethod(true);
            overlayMethodCache = method;
            String tool = method[2];
            String bait = method[3];

            if (state == State.RESTOCK_WALKING_TO_GE) {
                return tickRestockWalkToGe();
            }
            if (state == State.RESTOCKING) {
                return tickRestocking();
            }
            if (pendingGeCoinsWithdrawTarget > 0) {
                return tickPendingCoins();
            }

            // Blijf in de bank-flow tot de sessie klaar is — geen determineState tussendoor.
            if (state == State.BANKING) {
                return tickBanking();
            }

            boolean needsTool = !hasTool(tool);
            int baitMin = Math.max(0, FishingPlugin.baitMin);
            boolean needsBait = !bait.isEmpty()
                    && (baitMin <= 0 ? !Inventory.contains(bait) : stackCount(bait) < baitMin);
            if (needsTool || needsBait) {
                return tickNeedSupplies(tool, bait, needsTool);
            }
            bankRetries = 0;

            if (needsCooking && FishingPlugin.cookEnabled) {
                if (cookingFireLocation != null && !isAtLocationWithRange(cookingFireLocation, 3)) {
                    state = State.WALKING_TO_FIRE;
                    status = "→ vuur";
                    return tickWalkToFire();
                }
                state = State.COOKING;
                status = "koken";
                return tickCooking();
            }

            state = determineState();
            if (state != lastLoggedState) {
                FishDebug.once("state", state.name()
                        + " | " + (activeCenter != null ? activeCenter.name : "geen")
                        + " | " + method[1] + "/" + method[2]);
                lastLoggedState = state;
                try {
                    net.storm.sdk.bot.ActivityLog.step("Fish", humanState(state, method));
                } catch (Throwable ignored) {
                }
            }
            // Alleen sluiten als we gaan klikken in de wereld (spot/vuur/drop).
            // Weglopen: bank blijft open → minimap (OSRS sluit vanzelf).
            if (Bank.isOpen() && pendingGeCoinsWithdrawTarget <= 0
                    && (state == State.FISHING
                    || state == State.COOKING
                    || state == State.DROPPING)) {
                closeOpenBank();
                status = "bank sluiten";
                return Rand.nextInt(400, 700);
            }
            switch (state) {
                case FISHING:
                    status = "vissen " + method[1];
                    return tickFishing();
                case DROPPING:
                    status = "drop vis";
                    return tickDropping();
                case WALKING_TO_BANK:
                case BANKING:
                    status = "bank";
                    return tickBanking();
                case WALKING_TO_SPOT:
                    status = "naar spot";
                    return tickWalkToSpot();
                case WALKING_BACK_TO_SPOT:
                    status = "terug naar spot";
                    return tickWalkBackToSpot();
                case WALKING_TO_FIRE:
                    status = "→ vuur";
                    return tickWalkToFire();
                case COOKING:
                    status = "koken";
                    return tickCooking();
                case RESTOCK_WALKING_TO_GE:
                    return tickRestockWalkToGe();
                case RESTOCKING:
                    return tickRestocking();
                case IDLE:
                default:
                    status = "wacht op spot";
                    return tickIdleAtSpot();
            }
        } catch (Throwable e) {
            FishDebug.once("err", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[Fish] tick", e);
            status = "err: " + e.getClass().getSimpleName();
            return 2000;
        }
    }

    private void pollChatMessages() {
        long now = System.currentTimeMillis();
        if (now - lastChatPollMs < 400L) {
            return;
        }
        lastChatPollMs = now;
        try {
            List<String> recent = Chat.getRecentMessages(8);
            if (recent == null) {
                return;
            }
            for (String line : recent) {
                if (line == null || line.isEmpty() || !seenChat.add(line)) {
                    continue;
                }
                onGameMessage(line);
            }
            if (seenChat.size() > 64) {
                seenChat.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private void refreshCenter() {
        int lvl = Skills.getLevel(Skill.FISHING);
        String blob = FishingPlugin.centersBlob;
        String pref = FishingPlugin.preferredLocation;
        FishingCenters.Center picked = FishingCenters.pick(blob, lvl, pref, activeCenter);
        if (picked == null) {
            picked = FishingCenters.pick(FishingCenters.DEFAULT_BLOB, lvl, pref, activeCenter);
        }
        if (picked != activeCenter && (activeCenter == null || picked == null
                || picked.point == null || activeCenter.point == null
                || picked.point.getX() != activeCenter.point.getX()
                || picked.point.getY() != activeCenter.point.getY())) {
            if (picked != null && picked.point != null) {
                FishDebug.once("center", "gekozen: " + picked.name
                        + " @" + picked.point.getX() + "," + picked.point.getY()
                        + " r=" + picked.radius + " (lvl " + lvl
                        + ", pref=" + (pref != null ? pref : "AUTO") + ")");
            }
        }
        activeCenter = picked;
        if (picked != null && picked.point != null) {
            fishingSpot = picked.point;
            areaRadius = picked.radius;
        }
    }

    private int tickNeedSupplies(String tool, String bait, boolean needsTool) {
        if (bankRetries >= MAX_BANK_RETRIES) {
            status = "geen supplies (retries op)";
            state = State.IDLE;
            FishDebug.once("bank", "retries uitgeput");
            return 5000;
        }
        String missing = needsTool ? tool : bait;
        if (Bank.isOpen()) {
            bankSessionCompleted = false;
            state = State.BANKING;
            status = "supplies ophalen";
            return tickBanking();
        }
        boolean walkBank = !BankSnapshot.hasSnapshot()
                || (needsTool && BankSnapshot.contains(tool))
                || (!bait.isEmpty() && BankSnapshot.contains(bait));
        if (!walkBank && FishingPlugin.restockEnabled) {
            int coinsNeeded = estimateRestockCoins(tool, bait);
            if (coinCount() < coinsNeeded && BankSnapshot.contains("Coins")) {
                pendingGeCoinsWithdrawTarget = coinsNeeded;
                saveBankPosition();
                state = State.WALKING_TO_BANK;
                status = "→ bank (coins voor GE)";
                return tickWalkToBank();
            }
            FishDebug.log("ge", "snapshot zonder " + missing + " → GE");
            bankSessionCompleted = false;
            state = State.RESTOCK_WALKING_TO_GE;
            status = "→ GE (" + missing + ")";
            return tickRestockWalkToGe();
        }
        saveBankPosition();
        state = State.WALKING_TO_BANK;
        status = "→ bank (" + missing + ")";
        return tickWalkToBank();
    }

    private int tickPendingCoins() {
        if (Bank.isOpen()) {
            GeRestockHelper.withdrawCoinsFromOpenBank(pendingGeCoinsWithdrawTarget);
            if (coinCount() >= pendingGeCoinsWithdrawTarget) {
                pendingGeCoinsWithdrawTarget = 0;
                bankSessionCompleted = false;
                leaveBankOpenForWalk();
                state = State.RESTOCK_WALKING_TO_GE;
                status = "→ GE";
                return tickRestockWalkToGe();
            }
            return Rand.nextInt(500, 900);
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me.worldLocation != null && BankHelper.isNearGrandExchange(me.worldLocation)) {
            status = "GE-bank (coins)";
            BankHelper.tryOpenBankAtGrandExchange();
            return Rand.nextInt(800, 1400);
        }
        state = State.WALKING_TO_BANK;
        status = "→ bank (coins voor GE)";
        return tickWalkToBank();
    }

    private State determineState() {
        if (hasForeignItems()) {
            bankSessionCompleted = false;
            bankRetries = 0;
            saveBankPosition();
            FishDebug.log("state", "foreign inv → bank");
            return Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
        }
        String[] method = getTargetMethod();
        String bait = method[3];
        int baitMin = Math.max(0, FishingPlugin.baitMin);
        if (!bait.isEmpty() && baitMin > 0 && stackCount(bait) < baitMin) {
            bankSessionCompleted = false;
            saveBankPosition();
            FishDebug.log("state", "bait onder min " + stackCount(bait) + "/" + baitMin);
            return Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
        }
        if (Inventory.isFull()) {
            if (FishingPlugin.cookEnabled && hasRawFishToCook()) {
                needsCooking = true;
                cookingFireLocation = findNearestFire();
                if (cookingFireLocation != null) {
                    return State.WALKING_TO_FIRE;
                }
            }
            if (FishingPlugin.dropFish) {
                return State.DROPPING;
            }
            if (Bank.isOpen()) {
                return State.BANKING;
            }
            saveBankPosition();
            walkTargetSpot = null;
            return State.WALKING_TO_BANK;
        }
        // Bank open maar niets te storten/withdrawen → weglopen (minimap).
        // Niet terug naar BANKING: dat was Close-knop + NoSuchMethodError op oude SDK.
        if (preBankPosition != null && !isAtLocationWithRange(preBankPosition, LOCATION_THRESHOLD)) {
            if (spotInReach(pickNearestSpot(method)) && canUseMethodNow(method)) {
                preBankPosition = null;
                idleStartTime = 0L;
                stopTravelForSpot("spot in bereik");
                return State.FISHING;
            }
            return State.WALKING_BACK_TO_SPOT;
        }
        IPlayer local = Players.getLocal();
        if (local != null && local.isAnimating()) {
            stopTravelForSpot("vis-anim");
            return State.FISHING;
        }
        if (spotInReach(pickNearestSpot(method)) && canUseMethodNow(method)) {
            idleStartTime = 0L;
            stopTravelForSpot("spot in bereik");
            return State.FISHING;
        }
        if (fishingSpot != null) {
            idleStartTime = 0L;
            return State.WALKING_TO_SPOT;
        }
        if (idleStartTime == 0L) {
            idleStartTime = System.currentTimeMillis();
        }
        if (fishingSpot != null && System.currentTimeMillis() - idleStartTime > IDLE_TIMEOUT_MS) {
            idleStartTime = 0L;
            return State.WALKING_TO_SPOT;
        }
        return State.IDLE;
    }

    private int tickFishing() {
        bankSessionCompleted = false;
        IPlayer local = Players.getLocal();
        if (local == null) {
            return 1000;
        }
        long now = System.currentTimeMillis();
        if (local.isAnimating()) {
            fishingPostClickGraceUntil = 0L;
            stopTravelForSpot("vis-anim");
            return Rand.nextInt(600, 1200);
        }
        if (now < fishingPostClickGraceUntil) {
            fishingPostClickGraceUntil = 0L;
        }
        if (now - lastInteractTime < INTERACT_COOLDOWN_MS) {
            return Rand.nextInt(200, 400);
        }
        int dMin = FishingPlugin.interactDelayMin;
        int dMax = FishingPlugin.interactDelayMax;
        if (dMin > 0 && dMax >= dMin && now - lastInteractTime < Rand.nextInt(dMin, dMax + 1)) {
            return Rand.nextInt(200, 400);
        }
        int approached = clickOrApproachSpot(local);
        if (approached > 0) {
            return approached;
        }
        FishSpot nearest = pickNearestSpot(getTargetMethod());
        if (spotInReach(nearest)) {
            state = State.WALKING_TO_SPOT;
            return walkTowardPickedSpot(local, nearest);
        }
        FishDebug.log("spot", "geen klik — loop naar center tot spot in beeld");
        state = State.WALKING_TO_SPOT;
        return tickWalkToSpot();
    }

    private int tickDropping() {
        String[] method = getTargetMethod();
        String tool = method[2];
        String bait = method[3];
        List<IInventoryItem> toDrop = Inventory.getAll(item ->
                item != null && item.getName() != null
                        && !item.getName().equals(tool)
                        && !item.getName().equals(bait)
                        && (isRawFish(item.getName())
                        || item.getName().startsWith("Burnt ")
                        || isCookedFish(item.getName())));
        if (toDrop == null || toDrop.isEmpty()) {
            return 600;
        }
        int n = 0;
        for (IInventoryItem f : toDrop) {
            if (f == null) {
                continue;
            }
            f.interact("Drop");
            n++;
            sleep(100, 300);
        }
        FishDebug.log("drop", n + " vis gedropt");
        return Rand.nextInt(600, 1000);
    }

    private int tickWalkToSpot() {
        IPlayer local = Players.getLocal();
        if (local == null) {
            return 1000;
        }
        long now = System.currentTimeMillis();
        if (local.isAnimating()) {
            state = State.FISHING;
            stopTravelForSpot("vis-anim");
            return Rand.nextInt(400, 800);
        }
        int approached = clickOrApproachSpot(local);
        if (approached > 0) {
            return approached;
        }
        FishSpot nearest = pickNearestSpot(getTargetMethod());
        if (spotInReach(nearest)) {
            return walkTowardPickedSpot(local, nearest);
        }
        if (shouldDeferTravel(local, now)) {
            return Rand.nextInt(280, 480);
        }
        WorldPoint dest = resolveWalkTowardSpot();
        if (dest == null) {
            status = "geen pad naar spot";
            return Rand.nextInt(1000, 1800);
        }
        walkTargetSpot = dest;
        walkTargetSetTime = now;
        boolean issued = safeWalkTo(dest);
        if (!issued) {
            status = "geen pad naar spot";
            return Rand.nextInt(1000, 1800);
        }
        lastTravelClickTime = now;
        WorldPoint myPos = local.getWorldLocation();
        FishDebug.log("walk", "→ fishing spot @"
                + dest.getX() + "," + dest.getY()
                + " nearBank=" + (myPos != null && isNearAnyBank(myPos))
                + " inArea=" + isPlayerWithinFishingArea());
        return travelTickDelay(local);
    }

    private int tickWalkBackToSpot() {
        if (fishingSpot == null) {
            preBankPosition = null;
            return 600;
        }
        IPlayer local = Players.getLocal();
        if (local != null && local.isAnimating()) {
            preBankPosition = null;
            state = State.FISHING;
            stopTravelForSpot("vis-anim");
            return Rand.nextInt(400, 800);
        }
        if (local != null) {
            int approached = clickOrApproachSpot(local);
            if (approached > 0) {
                preBankPosition = null;
                return approached;
            }
        }
        FishSpot nearest = pickNearestSpot(getTargetMethod());
        if (spotInReach(nearest)) {
            preBankPosition = null;
            return walkTowardPickedSpot(local, nearest);
        }
        long now = System.currentTimeMillis();
        if (shouldDeferTravel(local, now)) {
            return Rand.nextInt(280, 480);
        }
        WorldPoint dest = resolveWalkTowardSpot();
        boolean ok = dest != null && safeWalkTo(dest);
        if (ok) {
            walkTargetSpot = dest;
            walkTargetSetTime = now;
            lastTravelClickTime = now;
            FishDebug.log("walk", "terug → spot @" + dest.getX() + "," + dest.getY());
        }
        return ok ? travelTickDelay(local) : Rand.nextInt(1000, 1800);
    }

    private int tickIdleAtSpot() {
        if (fishingSpot == null) {
            return Rand.nextInt(1000, 2000);
        }
        IPlayer local = Players.getLocal();
        if (local == null || local.getWorldLocation() == null) {
            return Rand.nextInt(1000, 2000);
        }
        long idleMs = idleStartTime > 0L ? System.currentTimeMillis() - idleStartTime : 0L;
        WorldPoint myPos = local.getWorldLocation();
        if (isBarbarianCenter() && idleMs > 1200 && !local.isMoving()) {
            String[] method = getTargetMethod();
            if (findFishingSpot(method, local) != null) {
                idleStartTime = 0L;
                state = State.FISHING;
                return tickFishing();
            }
            WorldPoint nearest = findNearestSpotTile(method);
            WorldPoint step = nearest != null ? nearest : FishingConfig.getBarbarianRiverAnchor();
            int d0 = myPos.distanceTo(step);
            if (d0 > 1) {
                walkToward(step, Math.min(18, Math.max(6, d0 - 1)));
                idleStartTime = 0L;
                state = State.WALKING_TO_SPOT;
                walkTargetSpot = step;
                walkTargetSetTime = System.currentTimeMillis();
                return Rand.nextInt(600, 1200);
            }
        }
        if (!isBarbarianCenter() && idleMs > 2000 && !local.isMoving()) {
            WorldPoint nearest = findNearestSpotTile(getTargetMethod());
            WorldPoint dest = nearest != null ? nearest : myPos;
            int dist = nearest != null ? myPos.distanceTo(nearest) : 0;
            if (dist > 2) {
                walkToward(dest, Math.min(8, dist));
                idleStartTime = 0L;
                return Rand.nextInt(800, 1500);
            }
        }
        return Rand.nextInt(1000, 2000);
    }

    private int tickWalkToFire() {
        if (dismissBlockingDialog()) {
            return Rand.nextInt(350, 700);
        }
        if (cookingFireLocation == null) {
            cookingFireLocation = findNearestFire();
            if (cookingFireLocation == null) {
                needsCooking = false;
                return 600;
            }
        }
        IPlayer local = Players.getLocal();
        if (local == null || local.getWorldLocation() == null) {
            return 1000;
        }
        if (isCookInterfaceOpen()) {
            state = State.COOKING;
            return tickCooking();
        }
        ITileObject fire = getCookObjectAtConfiguredFire();
        if (fire == null) {
            fire = findNearestCookObject();
        }
        if (fire != null && clickCookOnFire(fire)) {
            cookingFireClickTime = System.currentTimeMillis();
            state = State.COOKING;
            return Rand.nextInt(800, 1400);
        }
        int dist = local.getWorldLocation().distanceTo(cookingFireLocation);
        if (dist <= 2) {
            state = State.COOKING;
            return 400;
        }
        if (shouldDeferTravel(local, System.currentTimeMillis())) {
            return Rand.nextInt(400, 800);
        }
        boolean walked = safeWalkTo(cookingFireLocation);
        if (walked) {
            lastTravelClickTime = System.currentTimeMillis();
        }
        FishDebug.log("cook", "naar vuur d=" + dist + " click=" + (fire != null));
        return Rand.nextInt(700, 1200);
    }

    private int tickCooking() {
        IPlayer local = Players.getLocal();
        if (local == null) {
            return 1000;
        }
        if (isCookInterfaceOpen()) {
            pressCookSpace();
            cookingFireClickTime = System.currentTimeMillis();
            state = State.COOKING;
            return Rand.nextInt(1200, 2000);
        }
        if (dismissBlockingDialog()) {
            return Rand.nextInt(350, 700);
        }
        if (!hasRawFishToCook()) {
            needsCooking = false;
            cookingFireLocation = null;
            cookingFireClickTime = 0L;
            if (FishingPlugin.dropFish) {
                state = State.DROPPING;
                return 600;
            }
            saveBankPosition();
            state = State.WALKING_TO_BANK;
            return 600;
        }
        if (local.isAnimating()) {
            cookingFireClickTime = System.currentTimeMillis();
            return Rand.nextInt(600, 1200);
        }
        if (System.currentTimeMillis() - cookingFireClickTime < COOKING_FIRE_LOCK_MS) {
            return Rand.nextInt(400, 800);
        }
        ITileObject fire = getCookObjectAtConfiguredFire();
        if (fire == null) {
            fire = findNearestCookObject();
        }
        if (fire == null) {
            state = State.WALKING_TO_FIRE;
            return Rand.nextInt(700, 1200);
        }
        IInventoryItem raw = getCookableRawFish();
        if (raw != null) {
            lastCookingAttemptItemName = raw.getName();
            if (!clickCookOnFire(fire)) {
                try {
                    raw.useOn(fire);
                    FishDebug.log("cook", "fallback useOn " + raw.getName());
                } catch (Throwable t) {
                    FishDebug.log("cook", "useOn " + t.getClass().getSimpleName());
                }
            }
            cookingFireClickTime = System.currentTimeMillis();
            return Rand.nextInt(1600, 2800);
        }
        needsCooking = false;
        return 600;
    }

    private int tickBanking() {
        if (bankSessionCompleted) {
            leaveBankOpenForWalk();
            State next = afterBankClose != null && afterBankClose != State.IDLE
                    ? afterBankClose : (preBankPosition != null
                    ? State.WALKING_BACK_TO_SPOT : State.WALKING_TO_SPOT);
            afterBankClose = State.IDLE;
            state = next;
            FishDebug.log("bank", "leave-walk → " + next);
            if (next == State.WALKING_TO_SPOT) {
                status = "naar spot";
                return tickWalkToSpot();
            }
            if (next == State.WALKING_BACK_TO_SPOT) {
                status = "terug naar spot";
                return tickWalkBackToSpot();
            }
            if (next == State.RESTOCK_WALKING_TO_GE) {
                status = "→ GE";
                return tickRestockWalkToGe();
            }
            if (next == State.WALKING_TO_FIRE) {
                status = "→ vuur";
                return tickWalkToFire();
            }
            return Rand.nextInt(400, 700);
        }
        if (!Bank.isOpen()) {
            // Sessie al bezig (CLOSE): níet opnieuw openen — dat was de open/dicht-loop.
            if (bankSessionArmed) {
                BankSession.Status closing = bankSession.tick();
                status = "bank " + closing.phase + ": " + closing.detail;
                if (!closing.done()) {
                    return closing.delayMs > 0 ? closing.delayMs : Rand.nextInt(400, 700);
                }
                return finishArmedBankSession(closing);
            }
            int walk = tickWalkToBank();
            if (!Bank.isOpen()) {
                return walk;
            }
        }
        if (!bankSessionArmed) {
            armBankSession();
            bankSessionArmed = true;
        }
        BankSession.Status st = bankSession.tick();
        status = "bank " + st.phase + ": " + st.detail;
        if (!st.done()) {
            return st.delayMs > 0 ? st.delayMs : Rand.nextInt(500, 900);
        }
        return finishArmedBankSession(st);
    }

    private int finishArmedBankSession(BankSession.Status st) {
        bankSessionArmed = false;
        bankSession.reset();
        String[] method = getTargetMethod();
        String tool = method[2];
        String bait = method[3];
        if (st.result == BankSession.Result.NEEDS_GE) {
            String missing = st.missingItem != null ? st.missingItem : "";
            int baitNow = bait.isEmpty() ? 0 : stackCount(bait);
            if (!bait.isEmpty() && missing.equalsIgnoreCase(bait) && baitNow >= FishingPlugin.baitMin) {
                bankSessionCompleted = true;
                bankRetries = 0;
                FishDebug.once("bank", "bait ≥ min (" + baitNow + ") — skip GE");
                return finishBankAndClose(State.WALKING_TO_SPOT, Rand.nextInt(700, 1200));
            }
            bankSessionCompleted = true;
            if (FishingPlugin.restockEnabled) {
                bankRetries = 0;
                FishDebug.once("bank", "NEEDS_GE " + missing + " → restock");
                try {
                    net.storm.sdk.bot.ActivityLog.step("Fish", "NEEDS_GE " + missing);
                } catch (Throwable ignored) {
                }
                status = "→ GE (" + missing + ")";
                return finishBankAndClose(State.RESTOCK_WALKING_TO_GE, Rand.nextInt(1000, 2000));
            }
            bankRetries = MAX_BANK_RETRIES;
            status = missing + " op, restock uit";
            return finishBankAndClose(State.IDLE, Rand.nextInt(2000, 3000));
        }
        boolean hasToolNow = hasTool(tool);
        boolean hasBaitNow = bait.isEmpty() || Inventory.contains(bait);
        if (hasToolNow && hasBaitNow) {
            bankSessionCompleted = true;
            bankRetries = 0;
            FishDebug.log("bank", "supplies compleet " + tool + (bait.isEmpty() ? "" : " + " + bait)
                    + " — leave-walk naar spot");
            try {
                net.storm.sdk.bot.ActivityLog.step("Fish", "supplies ok → leave-walk naar spot");
            } catch (Throwable ignored) {
            }
            return finishBankAndClose(State.WALKING_TO_SPOT, Rand.nextInt(600, 1000));
        }
        bankSessionCompleted = false;
        FishDebug.log("bank", "na bank nog tool=" + hasToolNow + " bait=" + hasBaitNow);
        return finishBankAndClose(State.IDLE, Rand.nextInt(600, 1000));
    }

    /** Walk-away: geen Close/Esc. {@link Bank#leaveOpenForWalk()} via reflectie —
     * ↻ Scripts laadt de client-SDK niet; ontbrekende methode mag de loop niet crashen. */
    private int finishBankAndClose(State next, int delayMs) {
        afterBankClose = next != null ? next : State.IDLE;
        if (isWalkAwayAfterBank(next)) {
            leaveBankOpenForWalk();
            afterBankClose = State.IDLE;
            state = next;
            FishDebug.log("bank", "leave-walk → " + next);
            if (next == State.WALKING_TO_SPOT) {
                status = "naar spot";
                return tickWalkToSpot();
            }
            if (next == State.RESTOCK_WALKING_TO_GE) {
                status = "→ GE";
                return tickRestockWalkToGe();
            }
            return delayMs;
        }
        if (Bank.isOpen()) {
            closeOpenBank();
            state = State.BANKING;
            status = "bank sluiten";
            return Rand.nextInt(400, 700);
        }
        afterBankClose = State.IDLE;
        state = next;
        return delayMs;
    }

    private static boolean isWalkAwayAfterBank(State s) {
        return s == State.WALKING_TO_SPOT
                || s == State.WALKING_BACK_TO_SPOT
                || s == State.RESTOCK_WALKING_TO_GE
                || s == State.WALKING_TO_FIRE;
    }

    /**
     * Bank mag open blijven; volgende actie is lopen. Geen invokevirtual op
     * {@code Bank.leaveOpenForWalk} — die ontbreekt in een oude client-SDK.
     */
    private static void leaveBankOpenForWalk() {
        try {
            Bank.class.getMethod("leaveOpenForWalk").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            FishDebug.log("bank", "leave-walk (geen Close) — SDK-methode ontbreekt, loop door");
        }
    }

    /** Minimap-klik zolang de bank open is — canvas raakt Close/slots. */
    private boolean walkMinimapWhileBankOpen(WorldPoint target) {
        if (target == null || !Bank.isOpen()) {
            return false;
        }
        try {
            Point mini = Movement.resolveWalkClick(target);
            if (mini == null) {
                return false;
            }
            if (MouseManager.interactAt(mini, true)) {
                FishDebug.log("walk", "minimap bank-open → " + target.getX() + "," + target.getY());
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** DepositBox-patroon: Close-knop + muis, anders ESC. */
    private boolean closeOpenBank() {
        if (!Bank.isOpen()) {
            return true;
        }
        if (clickBankCloseButton()) {
            FishDebug.log("bank", "Close-knop (muis)");
            return false;
        }
        try {
            if (Bank.clickCloseWidget()) {
                FishDebug.log("bank", "Close-knop (SDK)");
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            Keyboard.pressEscape();
        } catch (Throwable t) {
            try {
                java.awt.Canvas canvas = Static.getClient() != null ? Static.getClient().getCanvas() : null;
                if (canvas != null) {
                    canvas.requestFocusInWindow();
                }
            } catch (Throwable ignored) {
            }
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        }
        try {
            Widgets.closeInterfaces();
        } catch (Throwable ignored) {
        }
        FishDebug.log("bank", "Close ESC");
        return false;
    }

    private boolean clickBankCloseButton() {
        try {
            IWidget root = Widgets.get(12, 0);
            if (clickCloseWidgetTree(root, 0)) {
                return true;
            }
            for (int child = 1; child <= 12; child++) {
                if (clickCloseWidgetTree(Widgets.get(12, child), 0)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            FishDebug.log("bank", "Close scan " + t.getClass().getSimpleName());
        }
        return false;
    }

    private boolean clickCloseWidgetTree(IWidget w, int depth) {
        if (w == null || depth > 5) {
            return false;
        }
        try {
            if (!w.isHidden() && w.hasAction("Close")) {
                java.awt.Rectangle b = w.getBounds();
                if (b != null && b.width >= 2 && b.height >= 2) {
                    Point p = new Point(b.x + Math.max(1, b.width / 2), b.y + Math.max(1, b.height / 2));
                    if (MouseManager.interactAt(p, true)) {
                        return true;
                    }
                }
                return w.interact("Close");
            }
            IWidget[] kids = w.getChildren();
            if (kids != null) {
                for (IWidget n : kids) {
                    if (clickCloseWidgetTree(n, depth + 1)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void armBankSession() {
        String[] method = getTargetMethod();
        String tool = method[2];
        String bait = method[3];
        int toolId = FishingConfig.toolId(tool);
        int baitId = FishingConfig.baitId(bait);
        List<String> keep = new ArrayList<>();
        keep.add(tool);
        if (!bait.isEmpty()) {
            keep.add(bait);
        }
        List<Integer> keepIds = new ArrayList<>();
        if (toolId > 0) {
            keepIds.add(toolId);
        }
        if (baitId > 0) {
            keepIds.add(baitId);
        }
        int[] ids = new int[keepIds.size()];
        for (int i = 0; i < keepIds.size(); i++) {
            ids[i] = keepIds.get(i);
        }
        List<BankSession.Requirement> reqs = new ArrayList<>();
        if (!hasTool(tool)) {
            reqs.add(toolId > 0
                    ? BankSession.Requirement.byId(tool, toolId, 1, 1)
                    : new BankSession.Requirement(tool, 1, 1));
        }
        int baitMin = Math.max(0, FishingPlugin.baitMin);
        int baitNow = bait.isEmpty() ? 0 : stackCount(bait);
        boolean needBaitWithdraw = !bait.isEmpty()
                && (baitMin <= 0 ? baitNow < 1 : baitNow < baitMin);
        if (needBaitWithdraw) {
            int target = Math.max(1, baitMin);
            reqs.add(baitId > 0
                    ? BankSession.Requirement.byId(bait, baitId, target, Integer.MAX_VALUE)
                    : new BankSession.Requirement(bait, target, Integer.MAX_VALUE));
            FishDebug.log("bank", "bait onder min " + baitNow + "/" + baitMin + " → 1× Withdraw-All");
        } else if (!bait.isEmpty()) {
            FishDebug.log("bank", "bait ≥ min (" + baitNow + ") — geen withdraw");
        }
        bankSession.configure(keep.toArray(new String[0]), ids, reqs, false, true);
        bankRetries++;
        FishDebug.log("bank", "sessie start tool=" + tool + " bait=" + bait
                + " reqs=" + reqs.size() + " poging " + bankRetries);
    }

    private int tickWalkToBank() {
        if (dismissBlockingDialog()) {
            return Rand.nextInt(350, 700);
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 1000;
        }
        if (Bank.isOpen()) {
            state = State.BANKING;
            try {
                MovementHelper.clearPath();
            } catch (Throwable ignored) {
            }
            return Rand.nextInt(400, 700);
        }
        if (BankOpenZones.contains(me.worldLocation)) {
            try {
                MovementHelper.clearPath();
            } catch (Throwable ignored) {
            }
            if (clickOpenBank(findBankBoothLocal(), findBankerLocal(), me.worldLocation)
                    || BankHelper.tryOpenFullBank()) {
                lastTravelClickTime = System.currentTimeMillis();
                FishDebug.log("bank", "in bank-zone — klik, geen walk naar booth");
                status = "bank openen";
                return Rand.nextInt(400, 700);
            }
            FishDebug.log("bank", "in bank-zone — retry open");
            return Rand.nextInt(500, 900);
        }
        try {
            if (BankHelper.ensureLumbridgeCastleStairProgress()) {
                return Rand.nextInt(800, 1400);
            }
        } catch (Throwable t) {
            FishDebug.log("bank", "trap " + t.getClass().getSimpleName());
        }

        // CombatBot: Barbarian/Edgeville → Edgeville-coords, nooit dichtstbijzijnde scene-bank
        // (Cooking Guild is dichter bij de rivier en is verboden).
        if (isEdgeBankProfile()) {
            return tickWalkBarbarianBank(me.worldLocation);
        }

        ITileObject booth = findBankBoothLocal();
        INPC banker = findBankerLocal();
        if (clickOpenBank(booth, banker, me.worldLocation)) {
            lastTravelClickTime = System.currentTimeMillis();
            FishDebug.log("bank", "klik booth/banker");
            return Rand.nextInt(800, 1400);
        }

        IPlayer bankLocal = Players.getLocal();
        if (shouldDeferTravel(bankLocal, System.currentTimeMillis())) {
            return travelTickDelay(bankLocal);
        }
        WorldPoint dest = bankWalkDest(me.worldLocation, booth, banker);
        boolean issued = safeWalkTo(dest);
        lastTravelClickTime = System.currentTimeMillis();
        if (!issued) {
            if (bankLocal != null && bankLocal.isMoving()) {
                return travelTickDelay(bankLocal);
            }
            noteBankRouteFail();
            status = "geen pad naar bank";
            return Rand.nextInt(1000, 1800);
        }
        consecutiveBankRoutePathFails = 0;
        FishDebug.log("walk", "→ bank @" + dest.getX() + "," + dest.getY());
        return travelTickDelay(bankLocal);
    }

    /** CombatBot {@code walkToFishingBank} + {@code walkBarbarianBankRouteWithFallbacks}. */
    private int tickWalkBarbarianBank(WorldPoint pos) {
        if (pos == null) {
            return 1000;
        }
        try {
            if (BankHelper.isInExcludedBankArea(pos)) {
                FishDebug.log("bank", "verboden bank (Cooking Guild e.d.) → Edgeville");
            }
        } catch (Throwable ignored) {
        }
        int dEdge = pos.distanceTo(EDGEVILLE_BANK);
        // COS: open vóór stilstand (niet pas ≤8 tegels)
        if (dEdge <= ClickOnSight.INVOKE_RANGE_TILES) {
            try {
                if (BankHelper.tryOpenFullBank()) {
                    lastTravelClickTime = System.currentTimeMillis();
                    FishDebug.log("bank", "Edgeville COS open d=" + dEdge);
                    return Rand.nextInt(500, 900);
                }
            } catch (Throwable t) {
                FishDebug.log("bank", "Edgeville open " + t.getClass().getSimpleName());
            }
            ITileObject booth = findBankBoothNear(EDGEVILLE_BANK, 18);
            INPC banker = findBankerNear(EDGEVILLE_BANK, 18);
            if (clickOpenBank(booth, banker, pos)) {
                lastTravelClickTime = System.currentTimeMillis();
                FishDebug.log("bank", "klik Edgeville booth/banker");
                return Rand.nextInt(800, 1400);
            }
        }
        IPlayer local = Players.getLocal();
        if (shouldDeferTravel(local, System.currentTimeMillis())) {
            return travelTickDelay(local);
        }
        if (walkToEdgevilleBankReliable()) {
            consecutiveBankRoutePathFails = 0;
            lastTravelClickTime = System.currentTimeMillis();
            FishDebug.log("walk", "→ Edgeville bank stand @3094,3491 (niet Cooking Guild)");
            return travelTickDelay(local);
        }
        if (local != null && local.isMoving()) {
            FishDebug.log("walk", "Edgeville retry (nog in beweging)");
            lastTravelClickTime = System.currentTimeMillis();
            return travelTickDelay(local);
        }
        TilePath existing = null;
        try {
            existing = MovementHelper.getPath(EDGEVILLE_BANK_STAND);
            BotRuntime.debugPath = existing;
        } catch (Throwable ignored) {
        }
        int tiles = existing != null && !existing.isEmpty() ? existing.size() : 0;
        if (tiles > 0) {
            FishDebug.log("walk", "Edgeville pad bestaat tiles=" + tiles + " — retry, geen Varrock-West");
            lastTravelClickTime = System.currentTimeMillis();
            return travelTickDelay(local);
        }
        FishDebug.log("walk", "Edgeville geen collision-pad — blijf Edgeville");
        noteBankRouteFail();
        status = "geen pad naar Edgeville";
        return Rand.nextInt(1000, 1800);
    }

    private WorldPoint bankWalkDest(WorldPoint me, ITileObject booth, INPC banker) {
        if (isEdgeBankProfile()) {
            return EDGEVILLE_BANK;
        }
        if (booth != null && booth.getWorldLocation() != null) {
            return booth.getWorldLocation();
        }
        if (banker != null && banker.getWorldLocation() != null) {
            return banker.getWorldLocation();
        }
        try {
            return BankHelper.DRAYNOR_BANK_AREA_CENTER;
        } catch (Throwable ignored) {
            return new WorldPoint(3092, 3243, me != null ? me.getPlane() : 0);
        }
    }

    private ITileObject findBankBoothLocal() {
        return findBankBoothNear(null, Integer.MAX_VALUE);
    }

    private ITileObject findBankBoothNear(WorldPoint anchor, int radius) {
        try {
            return TileObjects.getNearest(o -> o != null && o.getName() != null
                    && o.getName().toLowerCase(Locale.ROOT).contains("bank")
                    && (o.hasAction("Bank") || o.hasAction("Use"))
                    && o.getWorldLocation() != null
                    && !isExcludedBankTile(o.getWorldLocation())
                    && (anchor == null || o.getWorldLocation().distanceTo(anchor) <= radius));
        } catch (Throwable t) {
            FishDebug.log("bank", "booth " + t.getClass().getSimpleName());
            return null;
        }
    }

    private INPC findBankerLocal() {
        return findBankerNear(null, Integer.MAX_VALUE);
    }

    private INPC findBankerNear(WorldPoint anchor, int radius) {
        try {
            return NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.hasAction("Bank")
                    && n.getName().toLowerCase(Locale.ROOT).contains("banker")
                    && n.getWorldLocation() != null
                    && !isExcludedBankTile(n.getWorldLocation())
                    && (anchor == null || n.getWorldLocation().distanceTo(anchor) <= radius));
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isExcludedBankTile(WorldPoint w) {
        if (w == null) {
            return true;
        }
        try {
            return BankHelper.isInExcludedBankArea(w);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean clickOpenBank(ITileObject booth, INPC banker, WorldPoint me) {
        if (banker != null && banker.getWorldLocation() != null
                && (booth == null || me == null
                || me.distanceTo(banker.getWorldLocation()) < me.distanceTo(booth.getWorldLocation()))) {
            try {
                if (MenuInteract.interactNpcByIndex(banker.getIndex(), "Bank")) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (banker.interact("Bank")) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (booth == null) {
            return false;
        }
        try {
            net.runelite.api.TileObject raw = TileObjects.unwrap(booth);
            if (raw != null) {
                if (MenuInteract.interactObject(raw, "Bank") || MenuInteract.interactObject(raw, "Use")) {
                    return true;
                }
            }
        } catch (Throwable t) {
            FishDebug.log("bank", "menu " + t.getClass().getSimpleName());
        }
        try {
            if (booth.hasAction("Bank") && booth.interact("Bank")) {
                return true;
            }
            if (booth.hasAction("Use") && booth.interact("Use")) {
                return true;
            }
        } catch (Throwable t) {
            FishDebug.log("bank", "interact " + t.getClass().getSimpleName());
        }
        return false;
    }

    /**
     * Zelfde pad als steden-cirkel: {@link MovementHelper#walkTo} (collision + TilePath).
     * Nooit rechte-lijn interpolatie / minimap-klik door muren (manor, kasteel).
     * Fishing-spots liggen vaak op water → eerst loopbare stand-tegel.
     */
    private boolean safeWalkTo(WorldPoint dest) {
        if (dest == null || !scriptRunning()) {
            return false;
        }
        WorldPoint target = dest;
        try {
            WorldPoint stand = Pathfinder.nearestWalkableStand(dest);
            if (stand != null) {
                target = stand;
            }
        } catch (Throwable ignored) {
        }
        try {
            TilePath path = MovementHelper.getPath(target);
            BotRuntime.debugPath = path;
            int n = path != null && !path.isEmpty() ? path.size() : 0;
            // MovementHelper klikt minimap als de bank open is. Niet pad-index 18:
            // die tegel kan west liggen (3081,3481) terwijl de spot zuid is.
            if (MovementHelper.walkTo(target)) {
                FishDebug.log("walk", "collision-pad " + n + " tiles → "
                        + target.getX() + "," + target.getY());
                return true;
            }
            if (walkMinimapWhileBankOpen(target)) {
                FishDebug.log("walk", "collision-pad " + n + " tiles → "
                        + target.getX() + "," + target.getY());
                return true;
            }
            FishDebug.log("walk", "geen collision-pad (" + n + ") → "
                    + target.getX() + "," + target.getY());
        } catch (Throwable t) {
            try {
                MovementHelper.clearPath();
            } catch (Throwable ignored) {
            }
            String extra = t.getMessage() != null ? (" " + t.getMessage()) : "";
            FishDebug.log("walk", "MovementHelper " + t.getClass().getSimpleName() + extra);
        }
        return false;
    }

    private boolean walkToEdgevilleBankReliable() {
        return safeWalkTo(EDGEVILLE_BANK_STAND);
    }

    private void noteBankRouteFail() {
        IPlayer local = Players.getLocal();
        if (local != null && local.isMoving()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < fatalBankRouteGraceUntilMs) {
            return;
        }
        if (now - lastBankRoutePathFailMs > BANK_ROUTE_FAIL_CHAIN_WINDOW_MS) {
            consecutiveBankRoutePathFails = 0;
        }
        lastBankRoutePathFailMs = now;
        consecutiveBankRoutePathFails++;
        if (consecutiveBankRoutePathFails >= BANK_ROUTE_FAILS_BEFORE_FATAL) {
            FishDebug.once("bank", "FATAL bank-route — bot uit + logout");
            BotRuntime.botEnabled = false;
            BotRuntime.fishingEnabled = false;
            status = "bank-route faalde";
            try {
                Game.logout();
            } catch (Throwable ignored) {
            }
        }
    }

    private int tickRestockWalkToGe() {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 1000;
        }
        if (me.worldLocation.distanceTo(GeRestockHelper.GE_HUB) <= 12 || GrandExchange.isOpen()) {
            state = State.RESTOCKING;
            status = "GE kopen";
            return tickRestocking();
        }
        IPlayer geLocal = Players.getLocal();
        if (shouldDeferTravel(geLocal, System.currentTimeMillis())) {
            return travelTickDelay(geLocal);
        }
        if (FishingPlugin.useVarrockTeleport) {
            VarrockGeTravelHelper.walkTowardGe();
        } else {
            MovementHelper.walkTo(GeRestockHelper.GE_HUB);
        }
        lastTravelClickTime = System.currentTimeMillis();
        status = "→ GE";
        FishDebug.log("ge", "lopen naar GE dist=" + me.worldLocation.distanceTo(GeRestockHelper.GE_HUB));
        return travelTickDelay(geLocal);
    }

    private int tickRestocking() {
        if (!BotRuntime.botEnabled) {
            state = State.IDLE;
            return 200;
        }
        String[] method = getTargetMethod();
        String tool = method[2];
        String bait = method[3];
        List<RestockBuy> buys = buildRestockPlan(tool, bait);
        if (buys.isEmpty() && stillNeedsSupplies(tool, bait)) {
            closeGe();
            saveBankPosition();
            state = State.WALKING_TO_BANK;
            status = "GE leeg plan → bank";
            return Rand.nextInt(1000, 1800);
        }
        if (buys.isEmpty()) {
            bankRetries = 0;
            state = State.IDLE;
            return 600;
        }
        int coinsNeeded = estimatePlanCoins(buys);
        if (coinCount() < coinsNeeded) {
            if (sellEmergencyLoot()) {
                status = "GE: loot verkopen";
                return Rand.nextInt(800, 1300);
            }
            if (BankSnapshot.contains("Coins")) {
                pendingGeCoinsWithdrawTarget = coinsNeeded;
                closeGe();
                state = State.WALKING_TO_BANK;
                status = "→ bank (coins)";
                return Rand.nextInt(1000, 1800);
            }
            status = "te weinig gp voor restock";
            state = State.IDLE;
            return Rand.nextInt(1000, 1800);
        }
        RestockBuy buy = buys.get(0);
        status = "kopen " + buy.qty + "x " + buy.name;
        FishDebug.log("ge", "buy " + buy.qty + "x " + buy.name + " @" + buy.price);
        boolean ok = GeRestockHelper.buyWithEscalation(buy.name, buy.qty, buy.price);
        if (ok) {
            if (buys.size() == 1) {
                bankRetries = 0;
                state = State.IDLE;
                FishDebug.once("ge", "restock klaar");
                return Rand.nextInt(1000, 2000);
            }
            return Rand.nextInt(800, 1400);
        }
        FishDebug.log("ge", "buy fail " + buy.name + " → retry walk");
        state = State.RESTOCK_WALKING_TO_GE;
        return Rand.nextInt(1200, 2200);
    }

    private List<RestockBuy> buildRestockPlan(String tool, String bait) {
        List<RestockBuy> out = new ArrayList<>();
        String t = tool != null ? tool.trim() : "";
        String b = bait != null ? bait.trim() : "";
        if (!t.isEmpty() && !hasTool(t)) {
            out.add(new RestockBuy(t, 1, gePriceForTool(t)));
            return out;
        }
        int qty = Math.min(Math.max(FishingPlugin.baitMin, FishingPlugin.restockAmount),
                FISHING_RESTOCK_STACK_TARGET);
        if ("Fishing bait".equalsIgnoreCase(b) && stackCount(b) < qty) {
            out.add(new RestockBuy("Fishing bait", qty, Math.max(1, FishingPlugin.baitPrice)));
        } else if ("Feather".equalsIgnoreCase(b) && stackCount(b) < qty) {
            out.add(new RestockBuy("Feather", qty, Math.max(1, FishingPlugin.featherPrice)));
        }
        return out;
    }

    private static final class RestockBuy {
        final String name;
        final int qty;
        final int price;

        RestockBuy(String name, int qty, int price) {
            this.name = name;
            this.qty = qty;
            this.price = Math.max(1, price);
        }
    }

    private int gePriceForTool(String tool) {
        if (tool == null) {
            return GE_PRICE_DEFAULT_TOOL;
        }
        switch (tool.toLowerCase(Locale.ROOT)) {
            case "fishing rod":
            case "fly fishing rod":
            case "barbarian rod":
                return GE_PRICE_FISHING_ROD;
            case "small fishing net":
                return GE_PRICE_SMALL_NET;
            case "harpoon":
                return GE_PRICE_HARPOON;
            case "lobster pot":
                return GE_PRICE_LOBSTER_POT;
            default:
                return GE_PRICE_DEFAULT_TOOL;
        }
    }

    private int estimateRestockCoins(String tool, String bait) {
        return estimatePlanCoins(buildRestockPlan(tool, bait));
    }

    private int estimatePlanCoins(List<RestockBuy> buys) {
        int total = 0;
        for (RestockBuy b : buys) {
            total += b.qty * b.price + (b.qty == 1 ? 400 : 800);
        }
        return total;
    }

    private boolean stillNeedsSupplies(String tool, String bait) {
        return (!tool.isEmpty() && !hasTool(tool)) || (!bait.isEmpty() && !Inventory.contains(bait));
    }

    private boolean sellEmergencyLoot() {
        for (String name : EMERGENCY_SELL) {
            if (!Inventory.contains(name)) {
                continue;
            }
            int qty = Math.max(1, stackCount(name));
            FishDebug.log("ge", "emergency sell " + qty + "x " + name);
            return GrandExchange.sell(name, qty, 1);
        }
        return false;
    }

    private void closeGe() {
        if (GrandExchange.isOpen()) {
            Widgets.closeInterfaces();
        }
    }

    private static final class FishSpot {
        final int index;
        final WorldPoint tile;
        final WorldPoint stand;
        final int dist;
        final int walk;

        FishSpot(int index, WorldPoint tile, WorldPoint stand, int dist, int walk) {
            this.index = index;
            this.tile = tile;
            this.stand = stand;
            this.dist = dist;
            this.walk = walk;
        }

        WorldPoint walkDest() {
            return stand != null ? stand : tile;
        }
    }

    private INPC findFishingSpot(String[] method, IPlayer local) {
        FishSpot pick = pickNearestSpot(method);
        if (pick == null) {
            return null;
        }
        return NPCs.get(pick.index);
    }

    /**
     * Eén client-thread dump: alle matching spots, kies min. loop naar landtegel bij de speler.
     */
    private FishSpot pickNearestSpot(String[] method) {
        List<FishSpot> all = scanFishingSpots(method);
        if (all.isEmpty()) {
            if (lastSpotScanLine == null || lastSpotScanLine.isEmpty()
                    || !lastSpotScanLine.contains("overgeslagen")) {
                lastSpotScanLine = "Spots: 0 in scene";
            }
            FishDebug.log("spot", lastSpotScanLine);
            return null;
        }
        FishSpot best = all.get(0);
        for (FishSpot s : all) {
            if (s.walk < best.walk || (s.walk == best.walk && s.dist < best.dist)) {
                best = s;
            }
        }
        StringBuilder sb = new StringBuilder("Spots: ").append(all.size())
                .append(" → @").append(best.tile.getX()).append(",").append(best.tile.getY())
                .append(" d=").append(best.dist).append(" walk=").append(best.walk);
        if (all.size() > 1) {
            sb.append(" |");
            int n = 0;
            for (FishSpot s : all) {
                if (s.index == best.index) {
                    continue;
                }
                sb.append(" ").append(s.tile.getX()).append(",").append(s.tile.getY())
                        .append(" d").append(s.dist);
                n++;
                if (n >= 4) {
                    break;
                }
            }
        }
        lastSpotScanLine = sb.toString();
        FishDebug.log("spot", lastSpotScanLine);
        return best;
    }

    /** Spot dichtbij genoeg om te klikken — verder weg: doorlopen naar het center. */
    private static boolean spotInReach(FishSpot spot) {
        return spot != null && spot.dist <= SPOT_IN_REACH_TILES;
    }

    private List<FishSpot> scanFishingSpots(String[] method) {
        if (method == null || method.length < 2) {
            return new ArrayList<>();
        }
        String spotName = method[0];
        String action = method[1];
        WorldPoint center = fishingSpot;
        int areaR = getSearchRadius();
        String areaName = activeCenter != null && activeCenter.name != null && !activeCenter.name.isBlank()
                ? activeCenter.name : "visgebied";
        List<FishSpot> found = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null) {
                return new ArrayList<FishSpot>();
            }
            WorldPoint me = c.getLocalPlayer().getWorldLocation();
            List<FishSpot> out = new ArrayList<>();
            int[] skipped = {0};
            for (NPC npc : c.getNpcs()) {
                if (npc == null) {
                    continue;
                }
                WorldPoint tile = npc.getWorldLocation();
                if (tile == null) {
                    continue;
                }
                NPCComposition comp = npc.getTransformedComposition();
                if (comp == null) {
                    comp = npc.getComposition();
                }
                String[] actions = comp != null ? comp.getActions() : null;
                if (!rawSpotMatches(npc.getName(), actions, spotName, action)) {
                    continue;
                }
                if (center == null || Math.abs(tile.getX() - center.getX()) > areaR
                        || Math.abs(tile.getY() - center.getY()) > areaR
                        || tile.getPlane() != center.getPlane()) {
                    skipped[0]++;
                    continue;
                }
                WorldPoint stand = standClosestToPlayerOnClient(c, tile, me);
                int dist = me != null ? me.distanceTo(tile) : Integer.MAX_VALUE;
                int walk = (stand != null && me != null) ? me.distanceTo(stand) : dist;
                out.add(new FishSpot(npc.getIndex(), tile, stand, dist, walk));
            }
            if (skipped[0] > 0 && out.isEmpty()) {
                lastSpotScanLine = "Spots: 0 in " + areaName + " (" + skipped[0] + " onderweg overgeslagen)";
            }
            return out;
        }, new ArrayList<>());
        return found != null ? found : new ArrayList<>();
    }

    private static boolean rawSpotMatches(String rawName, String[] actions,
                                          String spotName, String preferredAction) {
        if (spotName == null) {
            return false;
        }
        boolean blankName = rawName == null || rawName.isBlank();
        boolean nameOk = !blankName
                && rawName.toLowerCase(Locale.ROOT).contains(spotName.toLowerCase(Locale.ROOT));
        boolean actionOk = hasPreferredFishingActionStatic(actions, preferredAction);
        if (blankName) {
            return actionOk;
        }
        if (!nameOk) {
            return false;
        }
        return actionOk;
    }

    private static boolean hasPreferredFishingActionStatic(String[] actions, String preferred) {
        if (actions == null || preferred == null) {
            return false;
        }
        for (String a : actions) {
            if (isSameFishingMethod(a, preferred)) {
                return true;
            }
        }
        return false;
    }

    /** Landtegel naast de water-NPC die het dichtst bij de speler ligt — niet de overkant. */
    private static WorldPoint standClosestToPlayerOnClient(Client c, WorldPoint npcTile, WorldPoint me) {
        if (c == null || npcTile == null) {
            return null;
        }
        WorldPoint best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                if (Math.abs(dx) + Math.abs(dy) > 4) {
                    continue;
                }
                WorldPoint cand = new WorldPoint(npcTile.getX() + dx, npcTile.getY() + dy, npcTile.getPlane());
                if (!Pathfinder.isWalkableSceneTile(c, cand)) {
                    continue;
                }
                int adj = Math.max(Math.abs(dx), Math.abs(dy));
                int fromMe = me != null ? me.distanceTo(cand) : adj;
                int score = fromMe * 10 + adj;
                if (score < bestScore) {
                    bestScore = score;
                    best = cand;
                }
            }
        }
        return best;
    }

    private WorldPoint findNearestSpotTile(String[] method) {
        FishSpot pick = pickNearestSpot(method);
        return pick != null ? pick.walkDest() : null;
    }

    private int walkTowardPickedSpot(IPlayer local, FishSpot pick) {
        if (pick == null) {
            return Rand.nextInt(400, 800);
        }
        long now = System.currentTimeMillis();
        if (shouldDeferTravel(local, now)) {
            return travelTickDelay(local);
        }
        WorldPoint dest = pick.walkDest();
        walkTargetSpot = dest;
        walkTargetSetTime = now;
        boolean ok = dest != null && safeWalkTo(dest);
        if (ok) {
            lastTravelClickTime = now;
            FishDebug.log("walk", "dichtstbijzijnde stand @"
                    + dest.getX() + "," + dest.getY()
                    + " npc@" + pick.tile.getX() + "," + pick.tile.getY()
                    + " d=" + pick.dist);
        }
        return ok ? travelTickDelay(local) : Rand.nextInt(400, 800);
    }

    /**
     * Dichtstbijzijnde matching spot: meteen Lure/Bait-invoke. Nooit naar de watertegel
     * of de overkant lopen (dat leek op “verste spot”).
     */
    private int clickOrApproachSpot(IPlayer local) {
        if (!scriptRunning() || local == null || local.isAnimating()) {
            return -1;
        }
        long now = System.currentTimeMillis();
        String[] method = getTargetMethod();
        if (method == null || method.length < 2) {
            return -1;
        }
        FishSpot pick = pickNearestSpot(method);
        if (!spotInReach(pick)) {
            return -1;
        }
        if (method.length >= 4 && !canUseMethodNow(method)) {
            FishDebug.log("spot", "geen kit voor " + method[1] + " (" + method[2]
                    + (method[3].isEmpty() ? "" : " + " + method[3]) + ") — doorlopen");
            return -1;
        }
        stopTravelForSpot("spot klikbaar");
        if (now - lastInteractTime < INTERACT_COOLDOWN_MS) {
            return -1;
        }
        if (!scriptRunning()) {
            return -1;
        }
        INPC spot = NPCs.get(pick.index);
        String act = spot != null ? resolvePreferredAction(spot, method[1]) : method[1];
        FishDebug.log("spot", "klik dichtstbij @" + pick.tile.getX() + "," + pick.tile.getY()
                + " d=" + pick.dist + " " + act);
        try {
            net.storm.sdk.bot.ActivityLog.step("Fish", act + " @" + pick.tile.getX() + ","
                    + pick.tile.getY() + " d=" + pick.dist);
        } catch (Throwable ignored) {
        }

        if (MenuInteract.interactNpcByIndex(pick.index, act)) {
            FishDebug.log("spot", "menu " + act + " " + MenuInteract.getLastProbeDetail());
            lastInteractTime = now;
            fishingPostClickGraceUntil = now
                    + Rand.nextInt(FISHING_POST_CLICK_GRACE_MIN_MS, FISHING_POST_CLICK_GRACE_MAX_MS + 1);
            state = State.FISHING;
            return Rand.nextInt(500, 900);
        }
        if (spot != null && (ClickOnSight.onScreen(spot) || ClickOnSight.can(spot) || pick.dist <= 12)) {
            ClickOnSight.Result r = ClickOnSight.interact(spot, act);
            FishDebug.log("spot", act + " fallback " + r);
            if (r.clicked) {
                lastInteractTime = now;
                fishingPostClickGraceUntil = now
                        + Rand.nextInt(FISHING_POST_CLICK_GRACE_MIN_MS, FISHING_POST_CLICK_GRACE_MAX_MS + 1);
                state = State.FISHING;
                return Rand.nextInt(500, 900);
            }
        }
        return -1;
    }

    private WorldPoint resolveWalkTowardSpot() {
        FishSpot pick = pickNearestSpot(getTargetMethod());
        if (spotInReach(pick)) {
            return pick.walkDest();
        }
        IPlayer local = Players.getLocal();
        WorldPoint me = local != null ? local.getWorldLocation() : null;
        if (isBarbarianCenter() && me != null && me.getY() >= 3446) {
            WorldPoint fire = barbarianFireApproach();
            if (me.distanceTo(BARBARIAN_FIRE) > 3) {
                FishDebug.log("walk", "→ vuur ±3 @"
                        + fire.getX() + "," + fire.getY());
                return fire;
            }
            barbarianFireApproachDest = null;
        }
        if (fishingSpot != null) {
            if (me != null && me.distanceTo(fishingSpot) <= 4 && pick == null) {
                return pickWalkTargetWhenInsideAreaWithoutSpot();
            }
            FishDebug.log("walk", "doorlopen naar center @"
                    + fishingSpot.getX() + "," + fishingSpot.getY()
                    + (pick != null ? " (spot nog d=" + pick.dist + ")" : " (geen NPC)"));
            return fishingSpot;
        }
        return pickWalkTargetWhenInsideAreaWithoutSpot();
    }

    /** Vuur 3106,3432 ±3 — zelfde trip dezelfde tegel, niet altijd 3102,3442. */
    private WorldPoint barbarianFireApproach() {
        if (barbarianFireApproachDest != null
                && barbarianFireApproachDest.distanceTo(BARBARIAN_FIRE) <= 3) {
            return barbarianFireApproachDest;
        }
        int dx = ThreadLocalRandom.current().nextInt(-3, 4);
        int dy = ThreadLocalRandom.current().nextInt(-3, 4);
        barbarianFireApproachDest = new WorldPoint(
                BARBARIAN_FIRE.getX() + dx,
                BARBARIAN_FIRE.getY() + dy,
                BARBARIAN_FIRE.getPlane());
        return barbarianFireApproachDest;
    }

    private boolean canUseMethodNow(String[] method) {
        if (method == null || method.length < 4) {
            return false;
        }
        if (!hasTool(method[2])) {
            return false;
        }
        String bait = method[3];
        return bait == null || bait.isEmpty() || Inventory.contains(bait);
    }

    private String resolvePreferredAction(INPC spot, String preferred) {
        String[] actions = npcActions(spot);
        if (actions != null) {
            for (String a : actions) {
                if (isSameFishingMethod(a, preferred)) {
                    return a;
                }
            }
        }
        return preferred;
    }

    /** Bait ≠ Net: nooit de andere methode kiezen omdat die tool toevallig in de inv zit. */
    private static boolean isSameFishingMethod(String npcAction, String preferred) {
        if (npcAction == null || preferred == null) {
            return false;
        }
        String a = npcAction.toLowerCase(Locale.ROOT).trim();
        String p = preferred.toLowerCase(Locale.ROOT).trim();
        if (p.contains("lure")) {
            return a.contains("lure");
        }
        if (p.contains("bait")) {
            return a.contains("bait");
        }
        if (p.contains("use-rod") || p.contains("use rod")) {
            return a.contains("use-rod") || a.contains("use rod");
        }
        if (p.contains("cage")) {
            return a.contains("cage");
        }
        if (p.contains("harpoon")) {
            return a.contains("harpoon");
        }
        if (p.contains("net")) {
            return a.contains("net") && !a.contains("bait") && !a.contains("harpoon");
        }
        return a.contains(p);
    }

    private static String[] npcActions(INPC npc) {
        if (npc == null) {
            return null;
        }
        NPCComposition comp = npc.getTransformedComposition();
        return comp != null ? comp.getActions() : null;
    }

    private String[] getTargetMethod() {
        String[] m = getTargetMethod(true);
        overlayMethodCache = m;
        return m;
    }

    private String[] getTargetMethod(boolean logFallback) {
        if (FishingPlugin.useSpecificMethod) {
            String spot = FishingPlugin.spotName != null ? FishingPlugin.spotName : "Fishing spot";
            String act = FishingPlugin.action != null ? FishingPlugin.action : "Net";
            return new String[]{spot, act,
                    FishingConfig.getToolForMethod(spot, act),
                    FishingConfig.getBaitForMethod(spot, act)};
        }
        int lvl = Skills.getLevel(Skill.FISHING);
        if (fishingSpot != null && FishingConfig.isBarbarianFishingLocation(fishingSpot.getX(), fishingSpot.getY())) {
            return maybeFallbackBarbarianToFlyLure(
                    FishingConfig.getBestMethodForLevelBarbarian(lvl), lvl, logFallback);
        }
        if (isEdgeFishingArea(fishingSpot)) {
            return FishingConfig.getBestMethodForLevelEdgeFeatherOnly(lvl);
        }
        if (isDraynorFishingArea(fishingSpot)) {
            return FishingConfig.getBestMethodForLevelDraynor(lvl);
        }
        return FishingConfig.getBestMethodForLevelNonBarbarian(lvl);
    }

    private String[] overlayMethodForDisplay() {
        if (overlayMethodCache != null) {
            return overlayMethodCache;
        }
        return safeMethod();
    }

    private String[] maybeFallbackBarbarianToFlyLure(String[] m, int fishingLevel, boolean logFallback) {
        if (fishingLevel < 20 || m == null || m.length < 4) {
            return m;
        }
        if (!"Rod Fishing spot".equals(m[0]) || !"Use-rod".equalsIgnoreCase(m[1])
                || !"Barbarian rod".equals(m[2])) {
            return m;
        }
        if (hasTool("Barbarian rod") && Inventory.contains("Fishing bait")) {
            return m;
        }
        if (hasTool("Fly fishing rod") && Inventory.contains("Feather")) {
            if (logFallback && scriptRunning()) {
                FishDebug.once("method", "fallback fly lure (inv)");
            }
            return FishingConfig.getFlyLureMethod();
        }
        if (Bank.isOpen() || BankSnapshot.hasSnapshot()) {
            boolean bankBarb = hasBankStack("Barbarian rod") && hasBankStack("Fishing bait");
            boolean bankFly = hasBankStack("Fly fishing rod") && hasBankStack("Feather");
            if (!bankBarb && bankFly) {
                if (logFallback && scriptRunning()) {
                    FishDebug.once("method", "fallback fly lure (bank)");
                }
                return FishingConfig.getFlyLureMethod();
            }
        }
        return m;
    }

    private boolean hasBankStack(String name) {
        try {
            if (Bank.isOpen()) {
                return Bank.contains(name);
            }
        } catch (Throwable ignored) {
        }
        return BankSnapshot.contains(name);
    }

    private String[] safeMethod() {
        try {
            return getTargetMethod(false);
        } catch (Throwable t) {
            return new String[]{"Fishing spot", "Net", "Small fishing net", ""};
        }
    }

    private boolean hasTool(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return true;
        }
        int id = FishingConfig.toolId(toolName);
        if (id > 0 && (Inventory.contains(id) || Equipment.contains(id))) {
            return true;
        }
        return Inventory.contains(toolName) || Equipment.contains(toolName);
    }

    private int stackCount(String name) {
        return Math.max(0, Inventory.getCount(true, name));
    }

    private int coinCount() {
        return Math.max(0, Inventory.getCount(true, FishingConfig.COINS_ID));
    }

    private boolean hasForeignItems() {
        return Inventory.getFirst(item -> {
            if (item == null || item.getName() == null) {
                return false;
            }
            if (item.getId() == GENIE_LAMP_ITEM_ID) {
                return false;
            }
            String n = item.getName().toLowerCase(Locale.ROOT);
            if (n.contains("lamp")) {
                return false;
            }
            boolean fishLike = n.contains("raw ") || n.contains("shrimp") || n.contains("anchovies")
                    || n.contains("herring") || n.contains("sardine") || n.contains("trout")
                    || n.contains("salmon") || n.contains("tuna") || n.contains("lobster")
                    || n.contains("swordfish") || n.contains("shark") || n.contains("monkfish")
                    || n.startsWith("burnt ");
            boolean toolLike = n.contains("net") || n.contains("rod") || n.contains("harpoon")
                    || n.contains("bait") || n.contains("feather") || n.contains("fishing")
                    || n.contains("lobster pot");
            boolean allowed = fishLike || toolLike || item.hasAction("Eat") || item.hasAction("Drink")
                    || n.contains("coins");
            return !allowed;
        }) != null;
    }

    /** CombatBot: widget 270 zichtbaar → Space (All is al geselecteerd). */
    private boolean isCookInterfaceOpen() {
        Boolean open = Static.callOnClientThread(() -> {
            try {
                net.runelite.api.Client c = Static.getClient();
                if (c == null) {
                    return false;
                }
                net.runelite.api.widgets.Widget w = c.getWidget(270, 0);
                return w != null && !w.isHidden();
            } catch (Throwable t) {
                return false;
            }
        }, false);
        if (Boolean.TRUE.equals(open)) {
            return true;
        }
        try {
            return Production.isOpen();
        } catch (Throwable t) {
            return false;
        }
    }

    private void pressCookSpace() {
        // CombatBot: Keyboard.type(" ", false) — KEY_TYPED. pressKey extra (bestaat in oude SDK).
        Keyboard.type(String.valueOf((char) KeyEvent.VK_SPACE), false);
        Keyboard.pressKey(KeyEvent.VK_SPACE);
        FishDebug.log("cook", "kookscherm Space (CombatBot 270)");
    }

    private boolean dismissBlockingDialog() {
        try {
            if (Dialog.isOpen() && Dialog.canContinue()) {
                Dialog.continueSpace();
                FishDebug.log("ui", "dialog continue");
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** CombatBot: Cook via menu-invoke / useOn — niet ClickOnSight (TileObjectInteractHelper ontbreekt in oude client). */
    private boolean clickCookOnFire(ITileObject fire) {
        if (fire == null) {
            return false;
        }
        try {
            net.runelite.api.TileObject raw = TileObjects.unwrap(fire);
            if (raw != null && MenuInteract.interactObject(raw, "Cook")) {
                FishDebug.log("cook", "menu Cook " + MenuInteract.getLastProbeDetail());
                return true;
            }
        } catch (Throwable t) {
            FishDebug.log("cook", "menu " + t.getClass().getSimpleName());
        }
        try {
            if (fire.hasAction("Cook") && fire.interact("Cook")) {
                FishDebug.log("cook", "interact Cook");
                return true;
            }
        } catch (Throwable t) {
            FishDebug.log("cook", "interact " + t.getClass().getSimpleName());
        }
        IInventoryItem fish = getCookableRawFish();
        if (fish != null) {
            try {
                fish.useOn(fire);
                FishDebug.log("cook", "useOn " + fish.getName());
                return true;
            } catch (Throwable t) {
                FishDebug.log("cook", "useOn " + t.getClass().getSimpleName());
            }
        }
        return false;
    }

    private boolean hasRawFishToCook() {
        int cookLvl = Skills.getLevel(Skill.COOKING);
        List<IInventoryItem> items = Inventory.getAll(item ->
                item != null && item.getName() != null
                        && isRawFish(item.getName())
                        && FishingConfig.canCook(item.getName(), cookLvl));
        return items != null && !items.isEmpty();
    }

    private IInventoryItem getCookableRawFish() {
        int cookLvl = Skills.getLevel(Skill.COOKING);
        return Inventory.getFirst(item ->
                item != null && item.getName() != null
                        && isRawFish(item.getName())
                        && FishingConfig.canCook(item.getName(), cookLvl));
    }

    private boolean isRawFish(String name) {
        if (name == null) {
            return false;
        }
        return name.startsWith("Raw ") || name.equals("Shrimps") || name.equals("Anchovies");
    }

    private boolean isCookedFish(String name) {
        if (name == null) {
            return false;
        }
        for (String[] data : FishingConfig.FISH_DATA) {
            if (data[1].equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private WorldPoint findNearestFire() {
        if (fishingSpot != null && isDraynorFishingArea(fishingSpot)) {
            return DRAYNOR_FIRE;
        }
        if (fishingSpot != null && FishingConfig.isBarbarianFishingLocation(fishingSpot.getX(), fishingSpot.getY())) {
            return BARBARIAN_FIRE;
        }
        ITileObject fire = findNearestCookObject();
        return fire != null ? fire.getWorldLocation() : null;
    }

    private ITileObject findNearestCookObject() {
        IPlayer local = Players.getLocal();
        WorldPoint myPos = local != null ? local.getWorldLocation() : null;
        boolean barb = isBarbarianCenter();
        int maxTiles = barb ? 80 : 70;
        return TileObjects.getNearest(obj -> {
            if (obj == null || obj.getName() == null || !obj.hasAction("Cook")) {
                return false;
            }
            String name = obj.getName().toLowerCase(Locale.ROOT);
            boolean cookSpot = name.equals("fire") || name.contains("campfire") || name.contains("forester")
                    || name.contains("range") || name.contains("stove");
            if (!cookSpot) {
                return false;
            }
            return myPos == null || myPos.distanceTo(obj.getWorldLocation()) <= maxTiles;
        });
    }

    private ITileObject getCookObjectAtConfiguredFire() {
        WorldPoint target = cookingFireLocation != null ? cookingFireLocation : findNearestFire();
        if (target == null) {
            return null;
        }
        return TileObjects.getNearest(obj -> obj != null
                && obj.getWorldLocation() != null
                && obj.hasAction("Cook")
                && obj.getWorldLocation().distanceTo(target) <= 1);
    }

    private int getEffectiveAreaRadius() {
        if (fishingSpot != null && FishingConfig.isBarbarianFishingLocation(fishingSpot.getX(), fishingSpot.getY())) {
            return Math.max(areaRadius, BARBARIAN_MIN_RADIUS);
        }
        return areaRadius;
    }

    private int getSearchRadius() {
        return Math.max(getEffectiveAreaRadius() + 14, 22);
    }

    private boolean isPlayerWithinFishingArea() {
        IPlayer local = Players.getLocal();
        if (local == null || fishingSpot == null || local.getWorldLocation() == null) {
            return true;
        }
        WorldPoint myPos = local.getWorldLocation();
        int r = getEffectiveAreaRadius();
        boolean inBox = Math.abs(myPos.getX() - fishingSpot.getX()) <= r
                && Math.abs(myPos.getY() - fishingSpot.getY()) <= r;
        if (!inBox) {
            return false;
        }
        return !(isDraynorFishingArea(fishingSpot) && isNearAnyBank(myPos));
    }

    private boolean isWithinArea(WorldPoint point) {
        if (fishingSpot == null || point == null) {
            return true;
        }
        int r = getEffectiveAreaRadius();
        return Math.abs(point.getX() - fishingSpot.getX()) <= r
                && Math.abs(point.getY() - fishingSpot.getY()) <= r;
    }

    private boolean isWithinSearchRadius(WorldPoint point) {
        if (fishingSpot == null || point == null) {
            return true;
        }
        int r = getSearchRadius();
        return Math.abs(point.getX() - fishingSpot.getX()) <= r
                && Math.abs(point.getY() - fishingSpot.getY()) <= r;
    }

    private boolean isBarbarianCenter() {
        return fishingSpot != null && FishingConfig.isBarbarianFishingLocation(fishingSpot.getX(), fishingSpot.getY());
    }

    private static boolean isDraynorFishingArea(WorldPoint center) {
        return center != null && FishingConfig.isDraynorFishingLocation(center.getX(), center.getY(), null);
    }

    private static boolean isEdgeFishingArea(WorldPoint center) {
        if (center == null) {
            return false;
        }
        return center.distanceTo(EDGEVILLE_BANK) <= EDGEVILLE_FISHING_PROFILE_RADIUS
                || FishingConfig.isBarbarianFishingLocation(center.getX(), center.getY());
    }

    private boolean isEdgeBankProfile() {
        if (isEdgeFishingArea(fishingSpot) || isBarbarianCenter()) {
            return true;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me.present ? me.worldLocation : null;
        if (pos != null && pos.distanceTo(EDGEVILLE_BANK) <= 60) {
            return true;
        }
        try {
            return pos != null && BankHelper.isInExcludedBankArea(pos);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private WorldPoint pickWalkTargetWhenInsideAreaWithoutSpot() {
        if (isBarbarianCenter()) {
            return FishingConfig.getBarbarianRiverAnchor();
        }
        if (isDraynorFishingArea(fishingSpot)) {
            return FishingConfig.getDraynorShoreAnchor();
        }
        return randomInnerTarget();
    }

    private WorldPoint randomInnerTarget() {
        if (fishingSpot == null) {
            return null;
        }
        if (isBarbarianCenter()) {
            return FishingConfig.getBarbarianRiverAnchor();
        }
        int radius = getEffectiveAreaRadius();
        int inner = radius <= 2 ? Math.max(1, radius)
                : Math.max(1, Math.min(radius - 2, (int) Math.floor(radius * 0.70)));
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int dx = rng.nextInt(-inner, inner + 1);
        int dy = rng.nextInt(-inner, inner + 1);
        return new WorldPoint(fishingSpot.getX() + dx, fishingSpot.getY() + dy, fishingSpot.getPlane());
    }

    private void saveBankPosition() {
        if (preBankPosition != null) {
            return;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me.present && me.worldLocation != null && !isNearAnyBank(me.worldLocation)) {
            preBankPosition = me.worldLocation;
        }
    }

    private boolean isAtLocationWithRange(WorldPoint target, int range) {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null || target == null) {
            return true;
        }
        return me.worldLocation.distanceTo(target) <= range;
    }

    private static boolean isNearAnyBank(WorldPoint pos) {
        if (pos == null) {
            return false;
        }
        try {
            WorldPoint nearest = BankHelper.getNearestF2pBankPoint(pos);
            return nearest != null && pos.distanceTo(nearest) <= 8;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean walkToward(WorldPoint target, int maxStep) {
        return safeWalkTo(target);
    }

    private boolean shouldDeferTravel(IPlayer local, long now) {
        if (local == null || !local.isMoving() || lastTravelClickTime <= 0L) {
            return false;
        }
        return now - lastTravelClickTime < TRAVEL_CHAIN_WHILE_MOVING_MS;
    }

    /** Zelfde poll-tempo als steden-cirkel tijdens travel. */
    private static int travelTickDelay(IPlayer local) {
        if (local != null && local.isMoving()) {
            return Rand.nextInt(280, 480);
        }
        return Rand.nextInt(380, 620);
    }

    private static void sleep(int min, int max) {
        try {
            Thread.sleep(Rand.nextInt(min, max + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
