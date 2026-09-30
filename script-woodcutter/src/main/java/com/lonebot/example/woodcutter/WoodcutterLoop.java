package com.lonebot.example.woodcutter;

import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.ClueSkillHandoff;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.game.BankOpenZones;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Game;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.interact.InteractWalkHelper;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.TileObjectInteractHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankSession;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.FarWalk;
import net.storm.sdk.movement.LumbridgeStairsHelper;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.PlaneChangeHelper;
import net.storm.sdk.utils.AntiBan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CombatBot woodcutting port: sticky walk, scan/interact, bonfire, forestry events, nests.
 */
final class WoodcutterLoop {

    private static final Logger log = LoggerFactory.getLogger(WoodcutterLoop.class);
    private static final int TREE_APPROACH_MARGIN = 14;
    private static final int INNER_ROAM_CAP = 16;
    private static final int LOCATION_THRESHOLD = 5;
    private static final long INTERACT_COOLDOWN_MS = 1200L;
    /** Na uitkap / andere boom: korte anti-spam (min laag; same-tree blijft 1.2s). */
    private static final long SWITCH_TREE_COOLDOWN_MS = 100L;
    private static final long CHOP_EFFECT_GRACE_MS = 2000L;
    private static final long TREE_TRAVEL_STICKY_MS = 8000L;
    private static final long BOUNDARY_STUCK_THRESHOLD_MS = 3500L;
    private static final long EMPTY_TREE_SKIP_MS = 15_000L;
    private static final long IDLE_TIMEOUT_MS = 30_000L;
    /** CombatBot IDLE_EDGE_TIMEOUT_MS = 2500 */
    private static final long IDLE_EDGE_TIMEOUT_MS = 2_500L;
    /** Zelfde als fishing: tijdens lopen na 280 ms opnieuw walkTo (doorlinken). */
    private static final long TRAVEL_CHAIN_WHILE_MOVING_MS = 280L;
    /** Na NEEDS_GE: niet eindeloos bank-loopen. */
    private long geMissingSuppressUntilMs;
    /** Kit niet in bank / withdraw mislukt — niet opnieuw gear-prep forceren. */
    private long kitBankMissSuppressUntilMs;

    enum State {
        CHOPPING, DROPPING, FIREMAKING, WALKING_TO_BANK, BANKING,
        WALKING_TO_TREES, IDLE
    }

    private final BonfireHandler bonfire = new BonfireHandler();
    private final BankSession bankSession = new BankSession();
    private boolean bankSessionArmed;
    /** Bank-trip: één doel vasthouden (anders Lumb↔Draynor mid-walk + pad clear). */
    private WorldPoint stickyBankDest;
    private long stickyBankSetMs;
    private State state = State.IDLE;
    private WcCenters.Center activeCenter;
    private long lastInteractMs;
    private long lastChopClickMs;
    private InteractWalkHelper.PendingVerify chopPendingVerify;
    private long lastTravelClickMs;
    private WorldPoint lastChoppedTile;
    private WorldPoint secondLastChoppedTile;
    private WorldPoint activeTreeTravelTarget;
    private long activeTreeTravelTargetUntilMs;
    private WorldPoint centerWalkTarget;
    private WorldPoint lastBoundaryStuckPos;
    private long lastBoundaryStuckSinceMs;
    private int walkToTreesFailCount;
    private long idleStartTime;
    private final Map<WorldPoint, Long> skippedTrees = new HashMap<>();
    private boolean burning;
    private String status = "idle";
    private State lastLoggedState;
    private String lastTargetTreeKeyword;
    private final Set<String> seenChat = new HashSet<>();
    private long lastChatPollMs;

    String getStatus() {
        return status;
    }

    private String humanWcState(State s, String want) {
        String loc = activeCenter != null && activeCenter.name != null ? activeCenter.name : "";
        String tree = want != null && !want.isBlank() ? want : "boom";
        if (s == null) {
            return "idle";
        }
        switch (s) {
            case WALKING_TO_BANK:
                return "lopen naar bank";
            case BANKING:
                return "banken";
            case WALKING_TO_TREES:
                return "lopen naar bomen (" + tree
                        + (loc.isEmpty() ? "" : " @ " + loc) + ")";
            case CHOPPING:
                return "hakken " + tree + (loc.isEmpty() ? "" : " @ " + loc);
            case DROPPING:
                return "drop logs";
            case FIREMAKING:
                return "firemaking";
            default:
                return s.name();
        }
    }

    /**
     * {@link FarWalk}: tijdens tree-approach/chop mag WC mid-pad COS doen.
     * Bank/FM/drop → uit (walker doorlinken zonder skill-ticks).
     */
    private void syncFarWalkPolicy() {
        boolean bankish = state == State.WALKING_TO_BANK || state == State.BANKING;
        boolean localOnly = state == State.FIREMAKING || state == State.DROPPING;
        FarWalk.keepSkillTicking(!bankish && !localOnly);
    }

    private boolean haltedWhileOff;

    void haltIfStopped() {
        if (haltedWhileOff) {
            return;
        }
        haltedWhileOff = true;
        stopCenterApproach("Stop/Pauze");
        FarWalk.clear();
        try {
            net.storm.sdk.movement.WorldWalker.cancel();
        } catch (Throwable ignored) {
        }
    }

    /** Overlay-status als bot uit — geen center kiezen / console-log. */
    void prepareOverlay() {
        // bewust leeg: refreshCenter() alleen in tick() als bot AAN
    }

    /** Regels voor canvas overlay (links:rechts of plain). */
    java.util.List<String> debugLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("State: " + (state != null ? state.name() : "?"));
        if (activeCenter != null && activeCenter.point != null) {
            lines.add("Center: " + activeCenter.name
                    + " @" + activeCenter.point.getX() + "," + activeCenter.point.getY()
                    + " r=" + activeCenter.radius
                    + " " + activeCenter.mode);
        } else {
            lines.add("Center: (geen)");
        }
        lines.add("Tree: " + targetTreeKeyword());
        lines.add("FM: " + (firemakingNow() ? "aan" : "uit")
                + " (cfg=" + (WoodcutterPlugin.firemaking ? "aan" : "uit")
                + " logs=" + (WoodcutterPlugin.firemaking ? "fm"
                : WoodcutterPlugin.dropLogs ? "drop" : "bank")
                + (activeCenter != null ? " center=" + activeCenter.mode : "")
                + ")"
                + " | drop=" + (dropNow() ? "aan" : "uit")
                + " | burn=" + burning
                + " | inv=" + Inventory.getCount() + "/28"
                + " | burnLog=" + (BonfireHandler.bestBurnableLog() != null
                ? BonfireHandler.bestBurnableLog() : "—"));
        lines.add("Bonfire: " + bonfire.status());
        lines.add("Anim: " + net.storm.sdk.game.Animations.describe(
                Players.snapshotLocal().animation));
        lines.add("FM-diag: " + FmLightHelper.diagnose());
        lines.add("Kit: " + ForestryKitHandler.status() + kitTag());
        int kitBark = ForestryKitHandler.animaBarkStored();
        lines.add("Kit bark: " + kitBark
                + " (" + ForestryKitHandler.animaBarkSource() + ")"
                + (ForestryEventHandler.sessionAnimaBark() > 0
                ? " | sessie +" + ForestryEventHandler.sessionAnimaBark() : "")
                + (ForestryEventHandler.hasActiveEventNearby()
                && ForestryEventHandler.eventAnimaBark() > 0
                ? " | event +" + ForestryEventHandler.eventAnimaBark() : ""));
        lines.add("BankSnap: " + bankSnapTag());
        if (activeTreeTravelTarget != null) {
            lines.add("Sticky: " + activeTreeTravelTarget.getX() + "," + activeTreeTravelTarget.getY());
        }
        lines.add("Pref: " + WoodcutterPlugin.preferredLocation);
        return lines;
    }

    void reset() {
        state = State.IDLE;
        activeCenter = null;
        lastInteractMs = 0L;
        lastChopClickMs = 0L;
        chopPendingVerify = null;
        lastTravelClickMs = 0L;
        lastChoppedTile = null;
        secondLastChoppedTile = null;
        clearTreeTravelTarget();
        centerWalkTarget = null;
        lastBoundaryStuckPos = null;
        lastBoundaryStuckSinceMs = 0L;
        walkToTreesFailCount = 0;
        idleStartTime = 0L;
        skippedTrees.clear();
        burning = false;
        status = "idle";
        lastTargetTreeKeyword = null;
        seenChat.clear();
        lastChatPollMs = 0L;
        haltedWhileOff = false;
        bonfire.reset();
        bankSession.reset();
        bankSessionArmed = false;
        clearStickyBank();
        geMissingSuppressUntilMs = 0L;
        kitBankMissSuppressUntilMs = 0L;
        ForestryKitHandler.reset();
        ForestryEventHandler.reset();
        FarWalk.clear();
    }

    void onGameMessage(String msg) {
        if (msg == null || msg.isEmpty()) {
            return;
        }
        bonfire.onGameMessage(msg);
        if (WoodcutterPlugin.forestryEvents) {
            ForestryEventHandler.onGameMessage(msg);
        }
    }

    int tick() {
        haltedWhileOff = false;
        if (!Game.isLoggedIn()) {
            status = "niet ingelogd";
            FarWalk.clear();
            return 1000;
        }
        // Mid-approach COS: skill mag tijdens farWalk ticken (niet tijdens bank-reis)
        syncFarWalkPolicy();
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present) {
            status = "geen speler";
            return 600;
        }
        if (!firemakingNow() && burning) {
            burning = false;
            bonfire.reset();
        }
        boolean fmNow = burning || (Inventory.isFull() && firemakingNow()
                && BonfireHandler.bestBurnableLog() != null);
        // Volle inv → meteen bank/FM/drop — geen forestry/antiban/nest die bank uitstellen
        if (Inventory.isFull() && !dropNow() && !fmNow) {
            pollChatMessages();
            refreshCenter();
            boolean entered = state != State.WALKING_TO_BANK && state != State.BANKING;
            if (entered) {
                chopPendingVerify = null;
                lastInteractMs = 0L;
                resetTreeNavigationForFreshTarget();
                WcDebug.log("state", "volle inv → bank NU (skip forestry/antiban)");
            }
            state = Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
            FarWalk.keepSkillTicking(false);
            status = "bank";
            return tickBanking(false);
        }
        // Sessie afmaken: 1 clue-deposit maakt 1 slot vrij → was isFull=false → logs bleven
        if (bankSessionArmed && !dropNow() && !fmNow) {
            FarWalk.keepSkillTicking(false);
            status = "bank";
            return tickBanking(false);
        }
        if (!Inventory.isFull()) {
            clearStickyBank();
        }
        // Geen AntiBan tijdens forestry-event (camera/fidget blokkeert Take/mulch)
        if (!fmNow && !(WoodcutterPlugin.forestryEvents
                && ForestryEventHandler.hasActiveEventNearby())) {
            int anti = AntiBan.get().check();
            if (anti > 0) {
                status = "antiban";
                return Math.min(anti, 2500);
            }
        }
        try {
            pollChatMessages();
            refreshCenter();
            WorldPoint centerPt = activeCenter != null ? activeCenter.point : null;
            int radius = activeCenter != null ? activeCenter.radius : 14;

            if (WoodcutterPlugin.forestryEvents && !fmNow && !Inventory.isFull()) {
                ForestryEventHandler.syncEventAnimaBoundary();
                if (!ForestryKitHandler.hasKitOnPerson()
                        && (needsKitFromForester() || ForestryKitHandler.hasActiveShopSession())) {
                    int buy = ForestryKitHandler.tickAcquireFromForester();
                    if (buy > 0 || ForestryKitHandler.hasActiveShopSession()) {
                        status = ForestryKitHandler.status();
                        WcDebug.log("kit", status);
                        return buy > 0 ? buy : Rand.nextInt(600, 1000);
                    }
                } else if (ForestryKitHandler.hasKitOnPerson()) {
                    ForestryKitHandler.reset();
                }
                // Event eerst (vóór nests/gear) — sneller herkennen & interrupt chop
                if (!Bank.isOpen() && !needsGearPrep() && ForestryKitHandler.hasKitOnPerson()) {
                    int forestry = ForestryEventHandler.tick();
                    if (forestry > 0) {
                        status = ForestryEventHandler.statusLine();
                        return forestry;
                    }
                }
                if (!ForestryEventHandler.hasActiveEventNearby()) {
                    int leftover = ForestryEventHandler.tryDestroyLeftovers();
                    if (leftover > 0) {
                        status = ForestryEventHandler.statusWithAnima("forestry leftover");
                        return leftover;
                    }
                }
            }

            if (!fmNow && !Inventory.isFull() && WoodcutterPlugin.forestryEvents
                    && ForestryKitHandler.hasKitOnPerson()
                    && !ForestryKitHandler.isEquipped()) {
                int kitWear = ForestryKitHandler.tryEquipFromInventory();
                if (kitWear > 0) {
                    status = ForestryKitHandler.status();
                    WcDebug.log("kit", status);
                    return kitWear;
                }
            }

            if (!bankSessionArmed && ClueSkillHandoff.tryHandoffFromActiveSkill()) {
                status = "clue handoff";
                return 400;
            }

            if (WoodcutterPlugin.birdNests && !Bank.isOpen() && !Inventory.isFull()) {
                int nest = BirdNestHandler.tick(centerPt, radius);
                if (nest > 0) {
                    status = "bird nest";
                    return nest;
                }
            }

            if (needsGearPrep()) {
                status = "gear prep";
                String why = !WcAxes.hasAxeOnPerson() ? "geen bijl"
                        : WcAxes.needsUpgradeFromBank()
                        ? ("upgrade " + WcAxes.bestOnPerson() + "→" + WcAxes.bestAvailable())
                        : "tinder/kit";
                WcDebug.log("bank", "gear prep (" + why + ")");
                return tickBanking(true);
            }

            state = determineState();
            if (state != lastLoggedState) {
                String centerInfo = activeCenter != null
                        ? activeCenter.name + " @" + activeCenter.point.getX() + "," + activeCenter.point.getY()
                        + " r=" + activeCenter.radius
                        : "geen center";
                WorldPoint pos = me.worldLocation;
                WcDebug.once("state", state.name()
                        + " | " + centerInfo
                        + " | pos=" + (pos != null ? pos.getX() + "," + pos.getY() : "?")
                        + " | tree=" + targetTreeKeyword()
                        + kitTag());
                lastLoggedState = state;
                try {
                    String want = targetTreeKeyword();
                    net.storm.sdk.bot.ActivityLog.step("WC", humanWcState(state, want));
                } catch (Throwable ignored) {
                }
            }
            switch (state) {
                case FIREMAKING:
                    return tickFiremaking();
                case DROPPING:
                    status = "drop logs";
                    return tickDropping();
                case WALKING_TO_BANK:
                case BANKING:
                    status = "bank";
                    return tickBanking(false);
                case WALKING_TO_TREES:
                    status = "naar bomen";
                    return tickWalkToTrees();
                case CHOPPING:
                    // Volle inv eerst — anders bleef Status op "chop" terwijl State al FIREMAKING was
                    if (Inventory.isFull() && firemakingNow() && BonfireHandler.bestBurnableLog() != null) {
                        if (!burning) {
                            burning = true;
                            bonfire.armTransitionDelay();
                        }
                        state = State.FIREMAKING;
                        return tickFiremaking();
                    }
                    status = "chop " + targetTreeKeyword()
                            + (activeCenter != null ? " @" + activeCenter.name : "")
                            + kitTag();
                    return tickChopping();
                case IDLE:
                default: {
                    int force = tryForceChopToClearBonfireTail(me);
                    if (force > 0) {
                        status = "idle→force chop";
                        return force;
                    }
                    status = "idle" + kitTag();
                    return Rand.nextInt(600, 1100);
                }
            }
        } catch (Exception e) {
            log.warn("[Woodcutter] loop error: {}", e.toString(), e);
            WcDebug.once("err", e.getClass().getSimpleName() + ": " + e.getMessage());
            status = "err: " + e.getClass().getSimpleName();
            return 2000;
        }
    }

    private void pollChatMessages() {
        long now = System.currentTimeMillis();
        // Sneller poll tijdens forestry — love-chat niet missen
        long gap = ForestryEventHandler.hasActiveEventNearby() ? 150L : 400L;
        if (now - lastChatPollMs < gap) {
            return;
        }
        lastChatPollMs = now;
        try {
            int limit = ForestryEventHandler.hasActiveEventNearby() ? 24 : 8;
            List<String> recent = Chat.getRecentMessages(limit);
            if (recent == null) {
                return;
            }
            for (String line : recent) {
                if (line == null || line.isEmpty()) {
                    continue;
                }
                if (!seenChat.add(line)) {
                    continue;
                }
                onGameMessage(line);
            }
            if (seenChat.size() > 96) {
                seenChat.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private void refreshCenter() {
        int wc = WcTrees.wcLevel();
        String kwNow = targetTreeKeyword();
        if (lastTargetTreeKeyword != null
                && !lastTargetTreeKeyword.equalsIgnoreCase(kwNow)) {
            WcDebug.log("tree", "switch " + lastTargetTreeKeyword + " → " + kwNow
                    + " (WC " + wc + ") — reset navigatie");
            resetTreeNavigationForFreshTarget();
            skippedTrees.clear();
            // Forceer her-pick van center (AUTO naar willows e.d.)
            activeCenter = null;
        }
        lastTargetTreeKeyword = kwNow;

        List<WcCenters.Center> pool = WcCenters.parseActive(WoodcutterPlugin.centersBlob);
        if (pool.isEmpty()) {
            pool = WcCenters.parseActive(WcCenters.DEFAULT_BLOB);
        }
        Players.LocalSnap mePick = Players.snapshotLocal();
        WorldPoint here = mePick != null && mePick.present ? mePick.worldLocation : null;
        WcCenters.Center picked = WcCenters.pick(
                WoodcutterPlugin.centersBlob, wc, WoodcutterPlugin.preferredLocation, here);
        if (picked == null) {
            picked = WcCenters.pick(WcCenters.DEFAULT_BLOB, wc, WoodcutterPlugin.preferredLocation, here);
        }
        boolean autoPref = WoodcutterPlugin.preferredLocation == null
                || WoodcutterPlugin.preferredLocation.isBlank()
                || "AUTO".equalsIgnoreCase(WoodcutterPlugin.preferredLocation.trim());
        boolean stayOnPicked = picked != null && here != null
                && (picked.contains(here) || picked.containsMargin(here, 25));
        // AUTO + scene-trees: niet weg-switchen als we al in de gekozen spot staan
        if (picked != null && pool.size() > 1 && autoPref && !stayOnPicked) {
            String kw = kwNow;
            WcCenters.Center withTree = null;
            Players.LocalSnap me = Players.snapshotLocal();
            WorldPoint pos = me.present ? me.worldLocation : null;
            int bestDist = Integer.MAX_VALUE;
            for (WcCenters.Center c : pool) {
                if (c == null || !c.active || c.point == null) {
                    continue;
                }
                if ((c.name != null && c.name.toLowerCase().contains("yew") && wc < 60)
                        || (c.name != null && c.name.toLowerCase().contains("willow") && wc < 30)
                        || (c.name != null && c.name.toLowerCase().contains("oak") && wc < 15)) {
                    continue;
                }
                if (!centerHasTargetTree(c, kw)) {
                    continue;
                }
                int d = pos != null ? pos.distanceTo(c.point) : 0;
                if (withTree == null || d < bestDist) {
                    withTree = c;
                    bestDist = d;
                }
            }
            if (withTree != null) {
                picked = withTree;
            }
        }
        // Nooit zonder center: anders State.IDLE voor altijd (geen walk-timeout)
        if (picked == null && !pool.isEmpty()) {
            picked = pool.get(0);
        }
        if (picked == null) {
            List<WcCenters.Center> def = WcCenters.parseActive(WcCenters.DEFAULT_BLOB);
            if (!def.isEmpty()) {
                picked = WcCenters.pick(WcCenters.DEFAULT_BLOB, wc, "AUTO");
                if (picked == null) {
                    picked = def.get(0);
                }
            }
            WcDebug.once("center", "geen pick → DEFAULT " + (picked != null ? picked.name : "LEEG")
                    + " (WC " + wc + ", blobLen="
                    + (WoodcutterPlugin.centersBlob != null ? WoodcutterPlugin.centersBlob.length() : 0)
                    + ")");
        }
        if (picked != activeCenter && (activeCenter == null || picked == null
                || !picked.name.equals(activeCenter.name))) {
            if (picked != null) {
                WcDebug.once("center", "gekozen: " + picked.name
                        + " @" + picked.point.getX() + "," + picked.point.getY()
                        + " r=" + picked.radius
                        + (picked.hasEdge() ? " edge" : "")
                        + " mode=" + picked.mode
                        + " tree=" + kwNow
                        + " (WC " + wc + ", pref=" + WoodcutterPlugin.preferredLocation + ")");
            }
            resetTreeNavigationForFreshTarget();
        }
        activeCenter = picked;
    }

    private boolean centerHasTargetTree(WcCenters.Center c, String keyword) {
        if (c == null || c.point == null || keyword == null) {
            return false;
        }
        return TileObjects.getNearest(obj -> obj != null
                && obj.getName() != null
                && obj.hasAction("Chop down")
                && WcTrees.matchesTree(obj.getName(), keyword)
                && !obj.getName().toLowerCase().contains("dead")
                && obj.getWorldLocation() != null
                && !WcCenters.isFakeTree(obj.getWorldLocation())
                && c.contains(obj.getWorldLocation())) != null;
    }

    /**
     * Master = {@link WoodcutterPlugin#firemaking} (UI). Center {@code :fm} mag niet overschrijven
     * als de toggle uit staat — anders blijft Draynor/Port Sarim bonfiren. Center {@code :bank}/
     * {@code :drop} mag FM wél uitzetten terwijl de globale toggle aan staat.
     */
    private boolean firemakingNow() {
        if (!WoodcutterPlugin.firemaking) {
            return false;
        }
        if (activeCenter != null) {
            if (activeCenter.mode == WcCenters.LogMode.DROP
                    || activeCenter.mode == WcCenters.LogMode.BANK) {
                return false;
            }
        }
        return true;
    }

    /** OSRS tinderbox id=590 — naam-check alleen is onbetrouwbaar bij client-timeout. */
    private static boolean hasTinderbox() {
        return Inventory.contains("Tinderbox") || Inventory.contains(WcTrees.TINDERBOX_ID);
    }

    private boolean dropNow() {
        if (activeCenter != null) {
            if (activeCenter.mode == WcCenters.LogMode.DROP) {
                return true;
            }
            if (activeCenter.mode == WcCenters.LogMode.BANK || activeCenter.mode == WcCenters.LogMode.FIREMAKING) {
                return false;
            }
        }
        return WoodcutterPlugin.dropLogs;
    }

    private String targetTreeKeyword() {
        int wc = WcTrees.wcLevel();
        if (WoodcutterPlugin.useSpecificTree && WoodcutterPlugin.treeName != null
                && !WoodcutterPlugin.treeName.trim().isEmpty()) {
            String specific = WoodcutterPlugin.treeName.trim();
            int req = WcTrees.requiredWcForTree(specific);
            if (wc < req) {
                String fallback = bestTreeInCenter(wc);
                WcDebug.once("tree", "WC " + wc + " < " + req + " voor " + specific + " → " + fallback);
                return fallback;
            }
            return specific;
        }
        // CombatBot: beste boom die DAADWERKELIJK in het center staat
        return bestTreeInCenter(wc);
    }

    /**
     * CombatBot {@code getBestTreeInCenter}: scan hoog→laag welke choppable boom
     * in de area staat; fallback {@link WcTrees#bestTreeForLevel}.
     */
    private String bestTreeInCenter(int wcLevel) {
        if (activeCenter == null) {
            return WcTrees.bestTreeForLevel(wcLevel);
        }
        for (int i = WcTrees.TREE_LEVELS.length - 1; i >= 0; i--) {
            String[] entry = WcTrees.TREE_LEVELS[i];
            String keyword = entry[0];
            int required = Integer.parseInt(entry[1]);
            if (wcLevel < required) {
                continue;
            }
            final String kw = keyword;
            ITileObject found = TileObjects.getNearest(obj ->
                    obj != null
                            && obj.getName() != null
                            && obj.getWorldLocation() != null
                            && WcTrees.matchesTree(obj.getName(), kw)
                            && !obj.getName().toLowerCase(Locale.ROOT).contains("dead")
                            && obj.hasAction("Chop down")
                            && !WcCenters.isFakeTree(obj.getWorldLocation())
                            && activeCenter.contains(obj.getWorldLocation()));
            if (found != null) {
                return keyword;
            }
        }
        return WcTrees.bestTreeForLevel(wcLevel);
    }

    /** CombatBot {@code hasForeignItemsForWc} — junk forceert bank. */
    private boolean hasForeignItemsForWc() {
        return Inventory.getFirst(item -> item != null && !isAllowedWcInventoryItem(item)) != null;
    }

    private boolean isAllowedWcInventoryItem(IInventoryItem item) {
        if (item == null || item.getName() == null) {
            return true;
        }
        String n = item.getName().toLowerCase(Locale.ROOT);
        if (n.contains("axe")
                || n.contains("logs")
                || n.equals("tinderbox")
                || n.equals("knife")
                || item.hasAction("Eat")
                || item.hasAction("Drink")
                || n.contains("coins")
                || n.contains("clue scroll")
                || n.contains("casket")
                || n.contains("bird nest")
                || n.contains("clue geode")
                || n.contains("clue bottle")) {
            return true;
        }
        if (ForestryIds.FORESTRY_KIT_NAME.equalsIgnoreCase(item.getName())
                || item.getId() == ForestryIds.FORESTRY_KIT_ITEM_ID) {
            return true;
        }
        // Forestry leftovers mogen (worden destroy't); niet bank-forcen
        if (ForestryIds.isForestryLeftover(item.getId(), item.getName())) {
            return true;
        }
        return false;
    }

    private boolean needsGearPrep() {
        if (System.currentTimeMillis() < geMissingSuppressUntilMs && WcAxes.hasAxeOnPerson()) {
            // GE/bank had geen betere axe — train door met wat we hebben
            boolean needsTinder = firemakingNow() && !hasTinderbox();
            return needsTinder;
        }
        boolean needsAxe = !WcAxes.hasAxeOnPerson();
        boolean needsUpgrade = WcAxes.needsUpgradeFromBank();
        boolean needsTinder = firemakingNow() && !hasTinderbox();
        boolean needsKit = shouldBankForForestryKit();
        if (needsUpgrade) {
            WcDebug.once("bank", "axe upgrade " + WcAxes.bestOnPerson()
                    + " → " + WcAxes.bestAvailable() + " (bank/snapshot)");
        }
        return needsAxe || needsUpgrade || needsTinder || needsKit;
    }

    /**
     * Kit ophalen uit bank alleen als die er écht lijkt te liggen.
     * Open bank zonder kit / recente withdraw-fail → niet blijven banken.
     */
    private boolean shouldBankForForestryKit() {
        if (!WoodcutterPlugin.forestryEvents) {
            return false;
        }
        if (ForestryKitHandler.hasActiveShopSession() || ForestryKitHandler.hasKitOnPerson()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < kitBankMissSuppressUntilMs) {
            return false;
        }
        // Live bank open: snapshot negeren — alleen echte bank-inhoud
        try {
            if (Bank.isOpen()) {
                return ForestryKitHandler.kitInOpenBank();
            }
        } catch (Throwable ignored) {
        }
        return ForestryKitHandler.kitKnownInBank()
                || !net.storm.sdk.items.BankSnapshot.hasSnapshot();
    }

    private boolean needsKitFromForester() {
        if (!WoodcutterPlugin.forestryEvents || ForestryKitHandler.hasKitOnPerson()) {
            return false;
        }
        long now = System.currentTimeMillis();
        // Na mislukte bank-kit → Friendly Forester (gratis kit)
        if (now < kitBankMissSuppressUntilMs) {
            return true;
        }
        return !ForestryKitHandler.kitKnownInBank()
                && net.storm.sdk.items.BankSnapshot.hasSnapshot();
    }

    private void markForestryKitNotInBank(String reason) {
        kitBankMissSuppressUntilMs = System.currentTimeMillis() + 5 * 60 * 1000L;
        WcDebug.log("bank", "kit niet beschikbaar (" + reason + ") — 5m skip bank-kit, Forester/WC");
    }

    private State determineState() {
        if (bankSessionArmed) {
            return Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
        }
        // Failsafe: bank nog open + logs in tas → dump afmaken (niet hakken)
        if (!dropNow() && !firemakingNow() && Bank.isOpen() && BonfireHandler.hasAnyLogs()) {
            WcDebug.once("bank", "bank open + logs in inv → dump afmaken ("
                    + BonfireHandler.countAllLogs() + ")");
            return State.BANKING;
        }
        // CombatBot: vreemde inv-items eerst banken
        if (hasForeignItemsForWc()) {
            resetTreeNavigationForFreshTarget();
            WcDebug.once("state", "foreign inv → bank");
            return Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
        }

        // Toggle uit (of center bank/drop): bonfire-sessie meteen stoppen
        if (!firemakingNow() && burning) {
            burning = false;
            bonfire.reset();
            WcDebug.log("state", "FM uit → bonfire stop (cfg="
                    + WoodcutterPlugin.firemaking + ")");
        }
        if (burning && !BonfireHandler.hasAnyBurnableLogs()) {
            burning = false;
            bonfire.reset();
        }
        if (burning && firemakingNow() && BonfireHandler.bestBurnableLog() != null) {
            if (!hasTinderbox()) {
                WcDebug.log("state", "FM maar geen tinderbox → bank");
                resetTreeNavigationForFreshTarget();
                return Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
            }
            return State.FIREMAKING;
        }
        if (Inventory.isFull()) {
            if (firemakingNow() && BonfireHandler.bestBurnableLog() != null) {
                if (!hasTinderbox()) {
                    WcDebug.log("state", "volle inv, geen tinderbox → bank (FM kan niet)");
                    resetTreeNavigationForFreshTarget();
                    return Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
                }
                if (!burning) {
                    burning = true;
                    bonfire.armTransitionDelay();
                    WcDebug.log("state", "volle inv → FIREMAKING log="
                            + BonfireHandler.bestBurnableLog()
                            + " free=" + Inventory.getFreeSlots()
                            + " fm=" + WcTrees.fmLevel());
                }
                return State.FIREMAKING;
            }
            if (dropNow()) {
                return State.DROPPING;
            }
            if (firemakingNow() && BonfireHandler.hasAnyLogs()) {
                WcDebug.log("state", "volle inv + logs maar niet burnable (fm="
                        + WcTrees.fmLevel() + ") → bank");
            } else {
                WcDebug.log("state", "volle inv → bank free=" + Inventory.getFreeSlots());
            }
            resetTreeNavigationForFreshTarget();
            return Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
        }

        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me.worldLocation;
        int areaThreshold = activeCenter != null ? activeCenter.radius : LOCATION_THRESHOLD;
        if (activeCenter != null && pos != null && !isAtCenterWithRange(pos, areaThreshold)) {
            idleStartTime = 0L;
            // Al geklikt / bezig met hakken → niet terug naar center-walk (yew Lumb/Draynor-loop)
            if (hasActiveChopSession()) {
                stopCenterApproach("chop-sessie");
                return animationAwareChopOrFiremaking(me);
            }
            ITileObject approachTree = findBestTree(pos, true);
            // Boom al in scene → hakken, niet eerst naar center lopen
            if (approachTree != null && approachTree.getWorldLocation() != null) {
                stopCenterApproach("boom in scene");
                return animationAwareChopOrFiremaking(me);
            }
            return State.WALKING_TO_TREES;
        }
        if (activeCenter != null && pos != null && isAtCenterWithRange(pos, activeCenter.radius)) {
            centerWalkTarget = null;
        }

        ITileObject tree = findBestTree(pos, false);
        if (tree != null) {
            idleStartTime = 0L;
            return animationAwareChopOrFiremaking(me);
        }

        if (idleStartTime == 0L) {
            idleStartTime = System.currentTimeMillis();
        }
        // Geen center + geen boom in scene → toch lopen (naar default pick), niet IDLE voor altijd
        if (activeCenter == null) {
            WcDebug.log("state", "IDLE zonder center → WALKING (tree=" + targetTreeKeyword() + ")");
            idleStartTime = 0L;
            return State.WALKING_TO_TREES;
        }
        if (pos != null) {
            long idleAge = System.currentTimeMillis() - idleStartTime;
            int innerR = innerWorkRadius(activeCenter.radius);
            int distFromCenter = pos.distanceTo(activeCenter.point);
            boolean onOuterRing = distFromCenter > innerR;
            long effectiveTimeout = onOuterRing ? IDLE_EDGE_TIMEOUT_MS : IDLE_TIMEOUT_MS;
            if (idleAge > effectiveTimeout) {
                idleStartTime = 0L;
                centerWalkTarget = null;
                WcDebug.log("state", "idle timeout " + idleAge + "ms → walk @" + activeCenter.name);
                return State.WALKING_TO_TREES;
            }
        }
        return State.IDLE;
    }

    private State animationAwareChopOrFiremaking(Players.LocalSnap me) {
        if (me != null && firemakingNow() && isBonfirePose(me)) {
            if (BonfireHandler.hasAnyBurnableLogs() && Inventory.isFull()) {
                burning = true;
                WcDebug.log("state", "bonfire-anim + volle inv → FIREMAKING");
                return State.FIREMAKING;
            }
        }
        return State.CHOPPING;
    }

    private boolean isBonfirePose(Players.LocalSnap me) {
        if (me == null) {
            return false;
        }
        if (WcTrees.isBonfireAnim(me.animation)) {
            return true;
        }
        return me.animating && !me.moving && me.animation < 0;
    }

    private boolean isAtCenterWithRange(WorldPoint pos, int range) {
        if (activeCenter == null || pos == null) {
            return true;
        }
        if (activeCenter.contains(pos) || activeCenter.containsMargin(pos, 0)) {
            return true;
        }
        return pos.distanceTo(activeCenter.point) <= range;
    }

    private int tickFiremaking() {
        status = "fm…";
        String logName = BonfireHandler.bestBurnableLog();
        if (logName == null) {
            burning = false;
            status = "fm klaar";
            return Rand.nextInt(400, 800);
        }
        if (!hasTinderbox()) {
            WcDebug.log("fm", "geen tinderbox in inv → bank");
            burning = false;
            bonfire.reset();
            return tickBanking(true);
        }

        // Live path =zelfde als Test #1 (tryDirectLightProven). Complex tick() bleef op "fm tick"
        // hangen (qty-gate return 0 / mayUseLogOnFire spin) terwijl useOn wél werkt.
        int delay = bonfire.tryDirectLightProven(logName);
        status = "fm: " + bonfire.status();
        if (delay <= 0) {
            burning = false;
            if (bonfire.isFiremakingFailed()) {
                WcDebug.once("fm", "FM failed → drop/bank");
            }
            return Rand.nextInt(400, 800);
        }
        return delay;
    }

    private int tickDropping() {
        return dropAllInventoryLogs();
    }

    private int dropAllInventoryLogs() {
        int dropped = 0;
        for (String logName : WcTrees.LOG_NAMES) {
            List<IInventoryItem> logs = Inventory.getAll(logName);
            if (logs == null || logs.isEmpty()) {
                continue;
            }
            for (IInventoryItem logItem : logs) {
                if (logItem == null) {
                    continue;
                }
                logItem.interact("Drop");
                dropped++;
                try {
                    Thread.sleep(Rand.nextInt(100, 300));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (dropped == 0) {
            IInventoryItem any = Inventory.getFirst(i -> i != null && WcTrees.isLogName(i.getName()));
            if (any != null) {
                any.interact("Drop");
                dropped = 1;
            }
        }
        if (dropped > 0) {
            WcDebug.log("drop", dropped + " logs gedropt");
            return Rand.nextInt(600, 1000);
        }
        return Rand.nextInt(400, 800);
    }

    private int tickWalkToTrees() {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 1000;
        }
        WorldPoint myPos = me.worldLocation;
        long travelNow = System.currentTimeMillis();

        // Al COS-chop / sticky boom → niet opnieuw center-walk (anders yew-loop)
        if (hasActiveChopSession()) {
            state = State.CHOPPING;
            WcDebug.log("walk", "active chop → CHOPPING (geen center)");
            return tickChopping();
        }

        // Lumb bank-floor / middenverdieping: eerst Bottom-floor
        if (myPos.getPlane() > 0 && BankHelper.isInLumbridgeCastleBuilding(myPos)) {
            WorldPoint ad = MovementHelper.getActiveDestination();
            if (ad != null && (LumbridgeStairsHelper.isLumbridgeBankBoothDest(ad)
                    || (ad.getPlane() >= 1 && LumbridgeStairsHelper.isLumbridgeUpperDest(ad)
                    && ad.getY() > 3214))) {
                MovementHelper.clearPath();
            }
            WorldPoint groundDest = centerWalkTarget();
            if (groundDest == null) {
                groundDest = LumbridgeStairsHelper.SOUTH_GROUND;
            } else if (groundDest.getPlane() != 0) {
                groundDest = new WorldPoint(groundDest.getX(), groundDest.getY(), 0);
            }
            WcDebug.log("walk", "Lumb plane " + myPos.getPlane() + "→0 (naar bomen, inv="
                    + Inventory.getCount() + ")");
            status = "Lumb trap omlaag";
            if (PlaneChangeHelper.progressTowardPlane(groundDest)) {
                lastTravelClickMs = travelNow;
                return travelTickDelay(me);
            }
            lastTravelClickMs = travelNow;
            return Rand.nextInt(500, 900);
        }

        if (isNearAnyBank(myPos)) {
            resetTreeNavigationForFreshTarget();
        }
        cleanupSkipped(travelNow);

        ITileObject tree = null;
        if (hasActiveTreeTravelTarget(travelNow)) {
            WorldPoint sticky = activeTreeTravelTarget;
            int stickyDist = myPos.distanceTo(sticky);
            tree = findChoppableTreeAt(sticky);
            if (tree == null && canScanTreeTile(myPos, sticky)) {
                tree = resolveTreeWithinSight(me, sticky, travelNow);
            }
            if (tree != null) {
                return tryInteractVisibleTree(tree, me, travelNow);
            }
            if (!canScanTreeTile(myPos, sticky)) {
                return walkTowardTreeTileBlind(sticky, stickyDist, travelNow, "→ Gekozen boom");
            }
        }
        if (tree == null) {
            tree = findBestTree(myPos, true);
        }
        if (tree != null && tree.getWorldLocation() != null) {
            return tryInteractVisibleTree(tree, me, travelNow);
        }

        clearTreeTravelTarget();
        if (shouldDeferTravelReclick(me, travelNow)) {
            status = "naar " + (activeCenter != null ? activeCenter.name : "bomen") + "…";
            return travelTickDelay(me);
        }

        boolean justOutside = activeCenter != null
                && !activeCenter.contains(myPos)
                && distanceToArea(myPos) <= 3;
        boolean stuckOnBoundary = false;
        if (justOutside && !me.moving) {
            if (myPos.equals(lastBoundaryStuckPos)) {
                if (lastBoundaryStuckSinceMs > 0L
                        && travelNow - lastBoundaryStuckSinceMs >= BOUNDARY_STUCK_THRESHOLD_MS) {
                    stuckOnBoundary = true;
                }
            } else {
                lastBoundaryStuckPos = myPos;
                lastBoundaryStuckSinceMs = travelNow;
            }
        } else {
            lastBoundaryStuckPos = null;
            lastBoundaryStuckSinceMs = 0L;
        }

        if (stuckOnBoundary && activeCenter != null) {
            int innerRadius = Math.max(1, activeCenter.radius / 2);
            centerWalkTarget = randomPointInRadius(activeCenter.point, innerRadius);
            WcDebug.once("walk", "Boundary-stuck @ " + myPos.getX() + "," + myPos.getY()
                    + " → diep in area");
            boolean forced = centerWalkTarget != null && MovementHelper.walkTo(centerWalkTarget);
            if (!forced) {
                MovementHelper.walkTo(activeCenter.point);
            }
            lastBoundaryStuckPos = null;
            lastBoundaryStuckSinceMs = 0L;
            walkToTreesFailCount = 0;
            lastTravelClickMs = travelNow;
            status = "boundary stuck → area";
            return travelTickDelay(me);
        }

        // Richting area tot COS boom; in area: lichte zoek-walk (geen center-spam na chop)
        boolean walkIssued;
        if (activeCenter != null && distanceToArea(myPos) <= 0) {
            if (centerWalkTarget == null
                    || myPos.distanceTo(centerWalkTarget) <= 1
                    || travelNow - lastTravelClickMs > 20_000L) {
                if (activeCenter.hasEdge()) {
                    centerWalkTarget = clampToCenter(randomPointInRadius(myPos, 8), activeCenter);
                } else {
                    centerWalkTarget = randomPointInRadius(activeCenter.point,
                            innerWorkRadius(activeCenter.radius));
                }
            }
            walkIssued = centerWalkTarget != null && MovementHelper.walkTo(centerWalkTarget);
        } else {
            if (centerWalkTarget == null) {
                centerWalkTarget = centerWalkTarget();
            }
            walkIssued = centerWalkTarget != null && MovementHelper.walkTo(centerWalkTarget);
        }
        if (!walkIssued) {
            walkToTreesFailCount++;
            status = "pathfind fail " + walkToTreesFailCount;
            if (walkToTreesFailCount >= 5) {
                centerWalkTarget = null;
                walkToTreesFailCount = 0;
            }
            return Rand.nextInt(1000, 1800);
        }
        walkToTreesFailCount = 0;
        lastTravelClickMs = travelNow;
        status = "→ " + (activeCenter != null ? activeCenter.name : "WC spot");
        WcDebug.log("walk", status);
        return travelTickDelay(me);
    }

    private int tickChopping() {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 1000;
        }
        WorldPoint chopDest = MovementHelper.getActiveDestination();
        if (destIsStaleCenterWalk(chopDest)) {
            stopCenterApproach("CHOPPING");
        }

        // Hard guard: nooit blijven hakken met volle inv (was: CHOPPING + chop-spam)
        if (Inventory.isFull()) {
            if (firemakingNow() && BonfireHandler.bestBurnableLog() != null) {
                if (!burning) {
                    burning = true;
                    bonfire.armTransitionDelay();
                    WcDebug.log("chop", "STOP chop — volle inv → FM ("
                            + BonfireHandler.bestBurnableLog() + ")");
                }
                state = State.FIREMAKING;
                return tickFiremaking();
            }
            if (dropNow()) {
                state = State.DROPPING;
                return tickDropping();
            }
            chopPendingVerify = null;
            lastInteractMs = 0L;
            resetTreeNavigationForFreshTarget();
            state = Bank.isOpen() ? State.BANKING : State.WALKING_TO_BANK;
            WcDebug.log("chop", "STOP chop — volle inv → bank (free="
                    + Inventory.getFreeSlots() + " fm=" + WcTrees.fmLevel()
                    + " burnable=" + BonfireHandler.bestBurnableLog() + ")");
            return tickBanking(false);
        }

        if (chopPendingVerify != null) {
            // Niet: "al WC-anim" = succes — na uitkap klikken we tijdens rest-anim;
            // die oude anim mag verify niet vals-positief clearen (anders +1.2s cooldown).
            boolean effect = InteractWalkHelper.hadEffect(chopPendingVerify.before,
                    InteractWalkHelper.EffectKind.COMBAT_OR_ANIM);
            if (!effect && me.animating && WcTrees.isWcAnim(me.animation)
                    && !WcTrees.isWcAnim(chopPendingVerify.before.animation)) {
                effect = true; // idle/non-WC → hak-anim = echte start
            }
            // Chop-klik laat client vaak eerst 1–2 tegels lopen: dat telt als effect
            if (!effect && lastChoppedTile != null && me.worldLocation != null) {
                if (me.moving || (me.walkDestination != null
                        && me.walkDestination.distanceTo(lastChoppedTile) <= 4)) {
                    effect = true;
                }
            }
            if (effect) {
                chopPendingVerify = null;
            } else if (chopPendingVerify.isDue()) {
                long since = System.currentTimeMillis() - chopPendingVerify.before.capturedMs;
                if (since >= CHOP_EFFECT_GRACE_MS) {
                    boolean nearTree = lastChoppedTile != null && me.worldLocation != null
                            && WcReach.minWalkStepsToInteract(me.worldLocation, lastChoppedTile) <= 1;
                    if (nearTree) {
                        WcDebug.log("chop", "geen anim @boom — retry Chop (geen walk)");
                        lastInteractMs = 0L;
                        chopPendingVerify = null;
                    } else {
                        WcDebug.log("chop", "geen effect na klik — approach boom");
                        if (lastChoppedTile != null) {
                            // SDK skip als al onderweg/naast — geen walker-hop-tune
                            InteractWalkHelper.fallbackPathfind(lastChoppedTile, 1);
                        }
                        lastInteractMs = 0L;
                        chopPendingVerify = null;
                    }
                }
            }
        }

        int animFix = reconcileChoppingWithBonfireAnimation(me);
        if (animFix > 0) {
            return animFix;
        }
        int forced = tryForceChopToClearBonfireTail(me);
        if (forced > 0) {
            return forced;
        }

        long now = System.currentTimeMillis();
        boolean treeGone = markDepletedIfGone(now);
        if (treeGone) {
            lastInteractMs = 0L;
            chopPendingVerify = null;
            WcDebug.log("chop", "boom weg → skip anim-wacht, zoek volgende");
        }

        // CombatBot wacht hele WC-anim af vóór volgende boom (~1–3s). Wij: alleen wachten
        // zolang we nog een levende hak-target hebben. Na uitkap is lastChoppedTile null → meteen door.
        // Snelle poll → stump eerder gezien → sneller andere boom.
        if (me.animating && lastChoppedTile != null && !treeGone) {
            int anim = me.animation;
            if (isBonfirePose(me)) {
                animFix = reconcileChoppingWithBonfireAnimation(me);
                if (animFix > 0) {
                    return animFix;
                }
                return Rand.nextInt(280, 800);
            }
            if (WcTrees.isWcAnim(anim)) {
                status = "chop anim=" + anim;
                return Rand.nextInt(180, 700);
            }
            if (WcTrees.isBonfireAnim(anim) && BonfireHandler.hasAnyBurnableLogs()) {
                return Rand.nextInt(600, 1200);
            }
        }

        // Same-tree anti-spam alleen met actieve target — niet na uitkap / retarget
        if (lastChoppedTile != null
                && lastInteractMs > 0L
                && now - lastInteractMs < INTERACT_COOLDOWN_MS) {
            return Rand.nextInt(100, 340);
        }
        if (shouldWaitForTreeClickPath(me, now)) {
            return Rand.nextInt(100, 340);
        }

        // Eerst hard center; daarna approach-margin (anders: CHOPPING zonder klik)
        ITileObject tree = findBestTree(me.worldLocation, false);
        if (tree == null) {
            tree = findBestTree(me.worldLocation, true);
            if (tree != null) {
                WcDebug.log("chop", "fallback allowOutside tree="
                        + (tree.getName() != null ? tree.getName() : "?")
                        + " @" + (tree.getWorldLocation() != null
                        ? tree.getWorldLocation().getX() + "," + tree.getWorldLocation().getY() : "?"));
            }
        }
        if (tree != null && tree.getWorldLocation() != null) {
            return tryInteractVisibleTree(tree, me, now);
        }

        // Geen boom: NOOIT opnieuw naar center als we net COS-chopten / sticky target hebben
        if (hasActiveChopSession()) {
            status = "wacht boom/anim";
            return Rand.nextInt(140, 400);
        }
        if (shouldDeferTravelReclick(me, now)) {
            return travelTickDelay(me);
        }
        // Alleen richting area als we er nog niet zijn — stop zodra COS boom (boven)
        if (activeCenter != null && distanceToArea(me.worldLocation) > 0) {
            WorldPoint dest = centerWalkTarget();
            if (dest != null && MovementHelper.walkTo(dest)) {
                lastTravelClickMs = now;
                status = "→ area (zoek boom)";
                WcDebug.log("chop", "geen boom → approach area (niet sticky center-spam)");
            }
        }
        return travelTickDelay(me);
    }

    private int reconcileChoppingWithBonfireAnimation(Players.LocalSnap me) {
        if (me == null || !firemakingNow() || !isBonfirePose(me)) {
            return 0;
        }
        if (BonfireHandler.hasAnyBurnableLogs() && Inventory.isFull() && !bonfire.isFiremakingFailed()) {
            burning = true;
            state = State.FIREMAKING;
            WcDebug.log("anim", "WC→FM (bonfire-anim + volle inv)");
            return tickFiremaking();
        }
        int forced = tryForceChopToClearBonfireTail(me);
        if (forced > 0) {
            return forced;
        }
        if (!WcTrees.isWcAnim(me.animation)) {
            ITileObject tree = findBestTree(me.worldLocation, false);
            if (tree == null) {
                tree = findBestTree(me.worldLocation, true);
            }
            if (tree != null) {
                if (TileObjectInteractHelper.chop(tree)) {
                    lastInteractMs = System.currentTimeMillis();
                    WcDebug.log("anim", "force chop (bonfire-pose) " + TileObjectInteractHelper.getLastDetail());
                    return Rand.nextInt(3, 8) * 600;
                }
            }
        }
        return 0;
    }

    private int tryForceChopToClearBonfireTail(Players.LocalSnap me) {
        if (me == null || BonfireHandler.hasAnyBurnableLogs()) {
            return 0;
        }
        if (!me.animating || me.moving) {
            return 0;
        }
        if (WcTrees.isWcAnim(me.animation)) {
            return 0;
        }
        if (!isBonfirePose(me)) {
            return 0;
        }
        ITileObject targetTree = findBestTree(me.worldLocation, false);
        if (targetTree == null) {
            targetTree = findBestTree(me.worldLocation, true);
        }
        if (targetTree != null) {
            if (TileObjectInteractHelper.chop(targetTree)) {
                lastInteractMs = System.currentTimeMillis();
                WcDebug.log("anim", "FORCEER chop: bonfire-tail " + TileObjectInteractHelper.getLastDetail());
                return Rand.nextInt(3, 8) * 600;
            }
        }
        return Rand.nextInt(600, 1000);
    }

    private void clearTreeTravelTarget() {
        activeTreeTravelTarget = null;
        activeTreeTravelTargetUntilMs = 0L;
    }

    private void setTreeTravelTarget(WorldPoint target, long now) {
        if (target == null) {
            clearTreeTravelTarget();
            return;
        }
        activeTreeTravelTarget = target;
        activeTreeTravelTargetUntilMs = now + TREE_TRAVEL_STICKY_MS;
    }

    private boolean hasActiveTreeTravelTarget(long now) {
        if (activeTreeTravelTarget == null) {
            return false;
        }
        if (now > activeTreeTravelTargetUntilMs) {
            clearTreeTravelTarget();
            return false;
        }
        return true;
    }

    private void resetTreeNavigationForFreshTarget() {
        clearTreeTravelTarget();
        lastChoppedTile = null;
        secondLastChoppedTile = null;
        centerWalkTarget = null;
        walkToTreesFailCount = 0;
        lastBoundaryStuckPos = null;
        lastBoundaryStuckSinceMs = 0L;
    }

    private int walkTowardTreeTileBlind(WorldPoint target, int dist, long travelNow, String label) {
        if (target == null) {
            return 1000;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (shouldDeferTravelReclick(me, travelNow)) {
            return travelTickDelay(me);
        }
        setTreeTravelTarget(target, travelNow);
        MovementHelper.walkTo(target);
        lastTravelClickMs = travelNow;
        // Sticky travel dest — niet wissen; centerWalkTarget apart voor area-walk
        status = label + " (" + dist + " tiles)";
        WcDebug.log("walk", status);
        return travelTickDelay(me);
    }

    /**
     * Fishing-stijl: alleen anti-spam tijdens lopen (~280 ms). Stilstand → meteen opnieuw walkTo.
     */
    private boolean shouldDeferTravelReclick(Players.LocalSnap me, long now) {
        if (me == null || !me.moving || lastTravelClickMs <= 0L) {
            return false;
        }
        return now - lastTravelClickMs < TRAVEL_CHAIN_WHILE_MOVING_MS;
    }

    /** Zelfde poll-tempo als fishing tijdens travel. */
    private static int travelTickDelay(Players.LocalSnap me) {
        if (me != null && me.moving) {
            return Rand.nextInt(280, 480);
        }
        return Rand.nextInt(380, 620);
    }

    private boolean canScanTreeTile(WorldPoint me, WorldPoint tile) {
        return me != null && tile != null && me.getPlane() == tile.getPlane()
                && isWorldPointInScene(tile);
    }

    private static boolean isWorldPointInScene(WorldPoint tile) {
        if (tile == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                int baseX = c.getBaseX();
                int baseY = c.getBaseY();
                int size = Constants.SCENE_SIZE;
                int x = tile.getX();
                int y = tile.getY();
                return x >= baseX && x < baseX + size && y >= baseY && y < baseY + size;
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }

    private ITileObject resolveTreeWithinSight(Players.LocalSnap me, WorldPoint expectedTile, long now) {
        if (me == null || expectedTile == null || me.worldLocation == null) {
            return null;
        }
        if (!canScanTreeTile(me.worldLocation, expectedTile)) {
            return null;
        }
        ITileObject atTile = findChoppableTreeAt(expectedTile);
        if (atTile != null) {
            return atTile;
        }
        ITileObject nearby = findBestTree(me.worldLocation, true);
        if (nearby != null && nearby.getWorldLocation() != null
                && canScanTreeTile(me.worldLocation, nearby.getWorldLocation())) {
            setTreeTravelTarget(nearby.getWorldLocation(), now);
            return nearby;
        }
        skippedTrees.put(expectedTile, now + skipMsForTree(targetTreeKeyword()));
        clearTreeTravelTarget();
        return null;
    }

    /**
     * Fishing-stijl: Chop down tijdens lopen (zelfde boom sticky via findBestTree).
     * Alleen korte verify na klik — geen 4.5s blok.
     */
    private boolean shouldWaitForTreeClickPath(Players.LocalSnap me, long now) {
        if (me == null) {
            return false;
        }
        if (me.animating && WcTrees.isWcAnim(me.animation)) {
            return false;
        }
        return chopPendingVerify != null && !chopPendingVerify.isDue();
    }

    private int tryInteractVisibleTree(ITileObject tree, Players.LocalSnap me, long now) {
        if (tree == null || tree.getWorldLocation() == null || me == null) {
            return 1000;
        }
        if (shouldWaitForTreeClickPath(me, now)) {
            return Rand.nextInt(160, 500);
        }
        WorldPoint tile = tree.getWorldLocation();
        boolean sameTree = WcReach.sameTile(tile, lastChoppedTile);
        long cooldown = sameTree ? INTERACT_COOLDOWN_MS : SWITCH_TREE_COOLDOWN_MS;
        if (lastInteractMs > 0L && now - lastInteractMs < cooldown) {
            return sameTree ? Rand.nextInt(100, 280) : Rand.nextInt(40, 160);
        }
        if (Inventory.isFull() && !dropNow()) {
            return Rand.nextInt(300, 600);
        }
        int dist = me.worldLocation != null ? me.worldLocation.distanceTo(tile) : 0;
        if (!canChopOnSight(tree, dist)) {
            return walkTowardTreeTileBlind(tile, dist, now, "→ boom (geen COS)");
        }
        clearTreeTravelTarget();

        // Snapshot VÓÓR klik — na klik is moving/anim al veranderd → valse "geen effect"
        InteractWalkHelper.PlayerSnapshot snap = InteractWalkHelper.capture();

        // COS: boom op canvas — Chop down
        boolean clicked = false;
        net.runelite.api.TileObject raw = TileObjects.unwrap(tree);
        if (raw != null && MenuInteract.interactObject(raw, "Chop down")) {
            clicked = true;
            WcDebug.log("chop", "menu Chop down dist=" + dist
                    + " " + (tree.getName() != null ? tree.getName() : "?"));
        }
        if (!clicked) {
            clicked = TileObjectInteractHelper.chop(tree);
        }
        if (!clicked && tree.hasAction("Chop down")) {
            clicked = tree.interact("Chop down");
        }
        if (!clicked) {
            status = "chop fail → retry";
            WcDebug.log("chop", "interact FAIL " + TileObjectInteractHelper.getLastDetail()
                    + " kw=" + targetTreeKeyword() + " dist=" + dist);
            return walkTowardTreeTileBlind(tile, dist, now, "→ boom na chop-fail");
        }
        // COS gelukt: stop center-approach — niet verder naar midden lopen
        stopCenterApproach("COS chop");
        noteChop(tile, now);
        chopPendingVerify = InteractWalkHelper.pendingVerify(snap,
                InteractWalkHelper.EffectKind.COMBAT_OR_ANIM, 400, 700);
        status = "chop " + (tree.getName() != null ? tree.getName() : "?")
                + " (" + dist + "t)";
        WcDebug.log("chop", (tree.getName() != null ? tree.getName() : "?")
                + " @" + tile.getX() + "," + tile.getY()
                + " dist=" + dist
                + " kw=" + targetTreeKeyword()
                + " | " + TileObjectInteractHelper.getLastDetail());
        try {
            String got = tree.getName() != null ? tree.getName() : "?";
            String want = targetTreeKeyword();
            String line = "chop " + got + " @" + tile.getX() + "," + tile.getY();
            if (want != null && !want.isBlank() && !got.toLowerCase(Locale.ROOT).contains(want.toLowerCase(Locale.ROOT))) {
                line += "  want=" + want;
            }
            net.storm.sdk.bot.ActivityLog.step("WC", line);
        } catch (Throwable ignored) {
        }
        // Andere boom: sneller door; same-tree max blijft ~900
        return sameTree ? Rand.nextInt(360, 900) : Rand.nextInt(220, 700);
    }

    /**
     * True zolang we een boom hebben gekozen / geklikt — dan geen center-walk meer.
     * Failsafe: verloopt als boom weg is en geen recente klik.
     */
    private boolean hasActiveChopSession() {
        if (chopPendingVerify != null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (lastInteractMs > 0L && now - lastInteractMs < 1_800L) {
            return true;
        }
        if (lastChoppedTile != null && tileHasChopDown(lastChoppedTile)) {
            return true;
        }
        return false;
    }

    /** Na COS-chop / boom in scene: LoopHost mag pad-eind van de center-walk niet blijven klikken. */
    private void stopCenterApproach(String reason) {
        WorldPoint ad = null;
        try {
            ad = MovementHelper.getActiveDestination();
        } catch (Throwable ignored) {
        }
        if (centerWalkTarget == null && ad == null) {
            return;
        }
        WorldPoint was = centerWalkTarget != null ? centerWalkTarget : ad;
        if (was != null) {
            WcDebug.log("walk", "stop center-approach (" + reason + ") was @"
                    + was.getX() + "," + was.getY());
        }
        centerWalkTarget = null;
        try {
            MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
    }

    private boolean destIsStaleCenterWalk(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        if (centerWalkTarget != null && dest.equals(centerWalkTarget)) {
            return true;
        }
        return activeCenter != null && activeCenter.point != null
                && dest.distanceTo(activeCenter.point) <= 2;
    }

    /**
     * Chop zonder eerst ernaartoe te lopen: menu-invoke binnen {@link ClickOnSight#INVOKE_RANGE_TILES}
     * (ook off-screen), of boom op canvas. Alleen verder → walk.
     */
    private static boolean canChopOnSight(ITileObject tree, int dist) {
        if (tree == null || dist < 0) {
            return false;
        }
        if (dist <= ClickOnSight.INVOKE_RANGE_TILES) {
            return true;
        }
        try {
            return ClickOnSight.onScreen(tree);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void noteChop(WorldPoint tile, long now) {
        lastInteractMs = now;
        lastChopClickMs = now;
        if (!WcReach.sameTile(tile, lastChoppedTile)) {
            secondLastChoppedTile = lastChoppedTile;
            lastChoppedTile = tile;
        }
    }

    /** @return true als de huidige hak-boom weg is (stump) — dan meteen volgende. */
    private boolean markDepletedIfGone(long now) {
        if (lastChoppedTile == null) {
            return false;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (!canScanTreeTile(me.worldLocation, lastChoppedTile)) {
            return false;
        }
        if (tileHasChopDown(lastChoppedTile)) {
            return false;
        }
        skippedTrees.put(lastChoppedTile, now + skipMsForTree(targetTreeKeyword()));
        WcDebug.log("chop", "boom uitgekapt @" + lastChoppedTile.getX() + "," + lastChoppedTile.getY()
                + " → volgende");
        lastChoppedTile = null;
        cleanupSkipped(now);
        return true;
    }

    private boolean tileHasChopDown(WorldPoint tile) {
        if (tile == null) {
            return false;
        }
        return TileObjects.getNearest(obj -> obj != null
                && obj.getWorldLocation() != null
                && WcReach.sameTile(obj.getWorldLocation(), tile)
                && obj.hasAction("Chop down")) != null;
    }

    private void cleanupSkipped(long now) {
        skippedTrees.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
    }

    private long skipMsForTree(String keyword) {
        // Iets korter dan CombatBot-tiers → sneller terug naar dichtstbijzijnde stump
        String k = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        if (k.contains("magic") || k.contains("redwood")) {
            return 90_000L;
        }
        if (k.contains("yew")) {
            return 65_000L;
        }
        if (k.contains("mahogany") || k.contains("maple")) {
            return 40_000L;
        }
        if (k.contains("willow") || k.contains("teak")) {
            return 16_000L;
        }
        if (k.contains("oak")) {
            return 14_000L;
        }
        return 12_000L;
    }

    private ITileObject findChoppableTreeAt(WorldPoint tile) {
        if (tile == null) {
            return null;
        }
        Long until = skippedTrees.get(tile);
        if (until != null && until > System.currentTimeMillis()) {
            return null;
        }
        String keyword = targetTreeKeyword();
        return TileObjects.getNearest(obj -> obj != null
                && obj.getName() != null
                && obj.getWorldLocation() != null
                && WcReach.sameTile(obj.getWorldLocation(), tile)
                && WcTrees.matchesTree(obj.getName(), keyword)
                && !obj.getName().toLowerCase().contains("dead")
                && obj.hasAction("Chop down")
                && !WcCenters.isFakeTree(obj.getWorldLocation()));
    }

    private ITileObject findBestTree(WorldPoint me, boolean allowOutside) {
        if (me == null) {
            return null;
        }
        String keyword = targetTreeKeyword();
        long now = System.currentTimeMillis();
        // Alleen sticky als we al naast de boom staan of echt aan het hakken zijn —
        // anders (na bank / onderweg) opnieuw dichtstbijzijnde kiezen.
        if (lastChoppedTile != null) {
            ITileObject current = findChoppableTreeAt(lastChoppedTile);
            if (current != null && isChoppable(current, keyword, me, allowOutside, now)) {
                int steps = WcReach.minWalkStepsToInteract(me, lastChoppedTile);
                if (steps <= 1) {
                    return current;
                }
                Players.LocalSnap snap = Players.snapshotLocal();
                if (snap != null && snap.present && snap.animating && WcTrees.isWcAnim(snap.animation)) {
                    return current;
                }
            }
        }
        List<ITileObject> pool = new ArrayList<>();
        List<ITileObject> all = TileObjects.getAll(obj -> isChoppable(obj, keyword, me, allowOutside, now));
        if (all != null) {
            pool.addAll(all);
        }
        if (pool.isEmpty()) {
            // Geen vrije boom dichtbij: oudste skip laten vallen als die al bijna terug is
            ITileObject fallback = findNearestSkippedChoppable(me, keyword, allowOutside, now);
            if (fallback != null) {
                WorldPoint t = fallback.getWorldLocation();
                skippedTrees.remove(t);
                WcDebug.log("chop", "dichtste skip-boom vrijgegeven @"
                        + t.getX() + "," + t.getY());
                return fallback;
            }
            return null;
        }
        ITileObject best = WcReach.pickBestByWalkSteps(pool, me, secondLastChoppedTile);
        // Onderweg: sticky houden tenzij een andere boom ≥2 walk-steps dichter is
        if (lastChoppedTile != null && best != null && best.getWorldLocation() != null) {
            ITileObject sticky = findChoppableTreeAt(lastChoppedTile);
            if (sticky != null && isChoppable(sticky, keyword, me, allowOutside, now)) {
                int curSteps = WcReach.minWalkStepsToInteract(me, lastChoppedTile);
                int bestSteps = WcReach.minWalkStepsToInteract(me, best.getWorldLocation());
                if (bestSteps >= curSteps - 1) {
                    return sticky;
                }
                WcDebug.log("chop", "switch dichter: steps " + curSteps + "→" + bestSteps
                        + " @" + best.getWorldLocation().getX() + ","
                        + best.getWorldLocation().getY());
            }
        }
        if (best != null && best.getWorldLocation() != null) {
            int steps = WcReach.minWalkStepsToInteract(me, best.getWorldLocation());
            WcDebug.once("chop", "kies boom @" + best.getWorldLocation().getX() + ","
                    + best.getWorldLocation().getY() + " steps=" + steps
                    + " pool=" + pool.size());
        }
        return best;
    }

    /**
     * Als alle dichtbij-bomen nog in skip staan: pak de skip die het dichtst bij de speler is
     * en al ≥ helft van skip-tijd heeft (respawn waarschijnlijk klaar).
     */
    private ITileObject findNearestSkippedChoppable(WorldPoint me, String keyword,
                                                   boolean allowOutside, long now) {
        if (me == null || skippedTrees.isEmpty()) {
            return null;
        }
        ITileObject best = null;
        int bestSteps = Integer.MAX_VALUE;
        long halfSkip = skipMsForTree(keyword) / 2L;
        for (Map.Entry<WorldPoint, Long> e : skippedTrees.entrySet()) {
            WorldPoint tile = e.getKey();
            Long until = e.getValue();
            if (tile == null || until == null) {
                continue;
            }
            long skippedFor = skipMsForTree(keyword) - Math.max(0L, until - now);
            if (skippedFor < halfSkip) {
                continue;
            }
            ITileObject obj = findChoppableTreeAtIgnoreSkip(tile, keyword);
            if (obj == null || !isChoppableIgnoreSkip(obj, keyword, me, allowOutside)) {
                continue;
            }
            int steps = WcReach.minWalkStepsToInteract(me, tile);
            if (steps < bestSteps) {
                bestSteps = steps;
                best = obj;
            }
        }
        return best;
    }

    private ITileObject findChoppableTreeAtIgnoreSkip(WorldPoint tile, String keyword) {
        if (tile == null) {
            return null;
        }
        return TileObjects.getNearest(obj -> obj != null
                && obj.getName() != null
                && obj.getWorldLocation() != null
                && WcReach.sameTile(obj.getWorldLocation(), tile)
                && WcTrees.matchesTree(obj.getName(), keyword)
                && !obj.getName().toLowerCase().contains("dead")
                && obj.hasAction("Chop down")
                && !WcCenters.isFakeTree(obj.getWorldLocation()));
    }

    private boolean isChoppableIgnoreSkip(ITileObject obj, String keyword, WorldPoint me,
                                          boolean allowOutside) {
        if (obj == null || obj.getWorldLocation() == null || obj.getName() == null) {
            return false;
        }
        if (!obj.hasAction("Chop down") || obj.getName().toLowerCase().contains("dead")) {
            return false;
        }
        if (!WcTrees.matchesTree(obj.getName(), keyword) || WcCenters.isFakeTree(obj.getWorldLocation())) {
            return false;
        }
        WorldPoint tile = obj.getWorldLocation();
        if (activeCenter == null) {
            return me.distanceTo(tile) <= 20;
        }
        if (activeCenter.contains(tile)) {
            return true;
        }
        if (!allowOutside) {
            return false;
        }
        if (activeCenter.hasEdge()) {
            return activeCenter.containsMargin(tile, TREE_APPROACH_MARGIN);
        }
        return tile.distanceTo(activeCenter.point) <= activeCenter.radius + TREE_APPROACH_MARGIN;
    }

    private boolean isChoppable(ITileObject obj, String keyword, WorldPoint me, boolean allowOutside, long now) {
        if (obj == null || obj.getWorldLocation() == null || obj.getName() == null) {
            return false;
        }
        if (!obj.hasAction("Chop down")) {
            return false;
        }
        if (obj.getName().toLowerCase().contains("dead")) {
            return false;
        }
        if (!WcTrees.matchesTree(obj.getName(), keyword)) {
            return false;
        }
        WorldPoint tile = obj.getWorldLocation();
        if (WcCenters.isFakeTree(tile)) {
            return false;
        }
        Long until = skippedTrees.get(tile);
        if (until != null && until > now) {
            return false;
        }
        if (activeCenter == null) {
            return me.distanceTo(tile) <= 20;
        }
        if (activeCenter.contains(tile)) {
            return true;
        }
        if (!allowOutside) {
            return false;
        }
        if (activeCenter.hasEdge()) {
            return activeCenter.containsMargin(tile, TREE_APPROACH_MARGIN);
        }
        return tile.distanceTo(activeCenter.point) <= activeCenter.radius + TREE_APPROACH_MARGIN;
    }

    private WorldPoint centerWalkTarget() {
        if (activeCenter == null) {
            return null;
        }
        if (activeCenter.hasEdge()) {
            Players.LocalSnap me = Players.snapshotLocal();
            WorldPoint pos = me != null && me.present ? me.worldLocation : null;
            if (pos != null) {
                return clampToCenter(pos, activeCenter);
            }
        }
        return activeCenter.point;
    }

    /** Dichtstbijzijnde tegel in het center — niet het bbox-midden (dat was de GE). */
    private static WorldPoint clampToCenter(WorldPoint pos, WcCenters.Center c) {
        if (pos == null || c == null) {
            return c != null ? c.point : null;
        }
        if (c.hasEdge()) {
            int x = Math.min(c.maxX, Math.max(c.minX, pos.getX()));
            int y = Math.min(c.maxY, Math.max(c.minY, pos.getY()));
            int z = c.point != null ? c.point.getPlane() : pos.getPlane();
            return new WorldPoint(x, y, z);
        }
        if (c.contains(pos)) {
            return pos;
        }
        return c.point;
    }

    private int distanceToArea(WorldPoint pos) {
        if (activeCenter == null || pos == null) {
            return 0;
        }
        if (activeCenter.contains(pos)) {
            return 0;
        }
        if (activeCenter.hasEdge()) {
            int dx = 0;
            if (pos.getX() < activeCenter.minX) {
                dx = activeCenter.minX - pos.getX();
            } else if (pos.getX() > activeCenter.maxX) {
                dx = pos.getX() - activeCenter.maxX;
            }
            int dy = 0;
            if (pos.getY() < activeCenter.minY) {
                dy = activeCenter.minY - pos.getY();
            } else if (pos.getY() > activeCenter.maxY) {
                dy = pos.getY() - activeCenter.maxY;
            }
            return Math.max(dx, dy);
        }
        int cheb = Math.max(Math.abs(pos.getX() - activeCenter.point.getX()),
                Math.abs(pos.getY() - activeCenter.point.getY()));
        return Math.max(0, cheb - activeCenter.radius);
    }

    private static int innerWorkRadius(int radius) {
        if (radius <= 2) {
            return Math.max(1, radius);
        }
        int inner = Math.max(1, Math.min(radius - 2, (int) Math.floor(radius * 0.70)));
        return Math.min(INNER_ROAM_CAP, inner);
    }

    private static WorldPoint randomPointInRadius(WorldPoint center, int radius) {
        if (center == null) {
            return null;
        }
        int r = Math.max(1, radius);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int dx = rng.nextInt(-r, r + 1);
        int dy = rng.nextInt(-r, r + 1);
        return new WorldPoint(center.getX() + dx, center.getY() + dy, center.getPlane());
    }

    private int tickBanking(boolean gearOnly) {
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me.present ? me.worldLocation : null;

        if (Bank.isOpen()) {
            // Bank open: walker mag niet meer doorlinken / Walk-here
            stopBankWalkForOpen("bank-open");
        }

        if (!Bank.isOpen()) {
            boolean farFromBank = pos == null || !isNearAnyBank(pos);
            if (farFromBank) {
                if (bankSessionArmed) {
                    bankSession.reset();
                    bankSessionArmed = false;
                }
                return tickWalkToBank();
            }
            // Dichtbij booth: pad stoppen, openen — niet opnieuw walkTo sticky dest
            stopBankWalkForOpen("near-bank");
            if (!bankSessionArmed) {
                if (BankHelper.tryOpenFullBank()) {
                    if (Bank.isOpen()) {
                        clearStickyBank();
                        state = State.BANKING;
                    } else {
                        WcDebug.log("bank", "COS/open — wacht UI (geen walk)");
                        status = "bank openen";
                        state = State.BANKING;
                        return Rand.nextInt(400, 700);
                    }
                } else {
                    status = "bank open retry";
                    return Rand.nextInt(500, 900);
                }
            }
        }
        if (!bankSessionArmed) {
            armBankSession(gearOnly);
            bankSessionArmed = true;
        }
        // Tijdens OPEN: pad blijft uit — anders STALL/hop op bank-tegel
        if (!Bank.isOpen()) {
            stopBankWalkForOpen("session-open");
        }
        BankSession.Status st = bankSession.tick();
        status = "bank " + st.phase + ": " + st.detail;
        if (st.done()) {
            bankSessionArmed = false;
            bankSession.reset();
            resetTreeNavigationForFreshTarget();
            if (st.result == BankSession.Result.NEEDS_GE) {
                if (WoodcutterPlugin.geAxeRestockEnabled) {
                    WcDebug.once("bank", "NEEDS_GE " + st.missingItem
                            + " — GE-optie aan maar GE nog niet klaar; train door (5m)");
                } else {
                    WcDebug.once("bank", "NEEDS_GE " + st.missingItem
                            + " — GE uit; bank-upgrade alleen; train door (5m)");
                }
                geMissingSuppressUntilMs = System.currentTimeMillis() + 5 * 60 * 1000L;
                if (st.missingItem != null
                        && st.missingItem.toLowerCase(java.util.Locale.ROOT).contains("forestry kit")) {
                    markForestryKitNotInBank("NEEDS_GE");
                }
                status = "geen " + st.missingItem + " in bank → door";
            } else if (st.result == BankSession.Result.FAIL) {
                WcDebug.once("bank", "bank FAIL");
            } else if (st.result == BankSession.Result.PARTIAL
                    && WoodcutterPlugin.forestryEvents
                    && !ForestryKitHandler.hasKitOnPerson()) {
                // Anti-loop / withdraw mislukt terwijl gear prep kit bleef eisen
                markForestryKitNotInBank("PARTIAL");
                WcDebug.log("bank", "sessie PARTIAL — kit skip");
            } else {
                WcDebug.log("bank", "sessie " + st.result);
            }
        }
        return st.delayMs > 0 ? st.delayMs : Rand.nextInt(500, 900);
    }

    /** Stop actief bank-pad zodat LoopHost/walker de bank-UI niet dichtklikt. */
    private void stopBankWalkForOpen(String reason) {
        WorldPoint ad = null;
        try {
            ad = MovementHelper.getActiveDestination();
        } catch (Throwable ignored) {
        }
        if (ad == null) {
            return;
        }
        try {
            MovementHelper.clearPath();
            WcDebug.log("bank", "pad clear (" + reason + ") dest was "
                    + ad.getX() + "," + ad.getY() + ",p" + ad.getPlane());
        } catch (Throwable t) {
            WcDebug.log("bank", "pad clear fail " + t.getClass().getSimpleName());
        }
    }

    /** Fishing-parity: eerst lopen naar bank, dan pas BankSession OPEN. */
    private int tickWalkToBank() {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 1000;
        }
        WorldPoint pos = me.worldLocation;
        long now = System.currentTimeMillis();

        if (Bank.isOpen()) {
            stopBankWalkForOpen("walk→open");
            clearStickyBank();
            state = State.BANKING;
            return Rand.nextInt(400, 700);
        }
        if (BankOpenZones.contains(pos)) {
            stopBankWalkForOpen("open-zone");
            if (canTryBankOpenCos(pos) && BankHelper.tryOpenFullBank()) {
                if (Bank.isOpen()) {
                    clearStickyBank();
                    state = State.BANKING;
                    return Rand.nextInt(400, 700);
                }
                state = State.BANKING;
                WcDebug.log("bank", "in bank-zone — COS, geen walk naar anker");
                status = "bank openen";
                return Rand.nextInt(400, 700);
            }
            stopBankWalkForOpen("zone-retry");
            status = "bank open retry";
            return Rand.nextInt(500, 900);
        }

        WorldPoint dest = ensureStickyBankDest(pos);
        boolean stickyLumb = isLumbridgeBankSticky(dest);

        // Al op Lumb bank-floor: geen ground-approach / Bottom-floor
        if (stickyLumb && pos.getPlane() >= 2 && BankHelper.isInLumbridgeCastleBuilding(pos)) {
            WorldPoint ad = MovementHelper.getActiveDestination();
            if (ad != null && (ad.getPlane() == 0 || LumbridgeStairsHelper.isGroundApproachTile(ad))) {
                MovementHelper.clearPath();
                stickyBankDest = dest;
                stickyBankSetMs = System.currentTimeMillis();
            }
            clearTreeTravelTarget();
            centerWalkTarget = null;
            if (canTryBankOpenCos(pos) && BankHelper.tryOpenFullBank()) {
                stopBankWalkForOpen("lumb-COS");
                if (Bank.isOpen()) {
                    clearStickyBank();
                    state = State.BANKING;
                    return Rand.nextInt(400, 700);
                }
                state = State.BANKING;
                WcDebug.log("bank", "p2 booth — wacht UI");
                status = "bank openen";
                return Rand.nextInt(400, 700);
            }
            boolean issued = MovementHelper.walkTo(dest);
            WcDebug.log("bank", "p2 → booth issued=" + issued);
            return Rand.nextInt(400, 700);
        }

        // Climb alleen in courtyard / bij trap
        if (stickyLumb && pos.getPlane() < 2 && LumbridgeStairsHelper.canClimbNow(pos)) {
            try {
                if (BankHelper.ensureLumbridgeCastleStairProgress()) {
                    WcDebug.log("bank", "Lumb trap omhoog");
                    status = "→ Lumb trap";
                    lastTravelClickMs = now;
                    return Rand.nextInt(400, 700);
                }
            } catch (Throwable t) {
                WcDebug.log("bank", "trap " + t.getClass().getSimpleName());
            }
        }

        clearTreeTravelTarget();
        centerWalkTarget = null;

        // COS alleen dichtbij booth/banker
        if (canTryBankOpenCos(pos)) {
            if (BankHelper.tryOpenFullBank()) {
                stopBankWalkForOpen("COS-click");
                if (Bank.isOpen()) {
                    clearStickyBank();
                    state = State.BANKING;
                    return Rand.nextInt(400, 700);
                }
                if (isNearAnyBank(pos)) {
                    state = State.BANKING;
                    WcDebug.log("bank", "COS open klik — wacht UI (pad uit)");
                    status = "bank openen";
                    return Rand.nextInt(400, 700);
                }
                WcDebug.log("bank", "tryOpen gaf walk — blijf → bank");
            }
            if (isNearAnyBank(pos)) {
                stopBankWalkForOpen("near-retry");
                status = "bank open retry";
                return Rand.nextInt(500, 900);
            }
        }

        // Al onderweg naar sticky: niet opnieuw nearest kiezen / pad wissen
        if (dest != null && (MovementHelper.alreadyEnRoute(dest) || shouldDeferTravelReclick(me, now))) {
            MovementHelper.walkTo(dest);
            status = "→ bank…";
            return travelTickDelay(me);
        }

        boolean issued = dest != null && MovementHelper.walkTo(dest);
        lastTravelClickMs = now;
        int dist = dest != null && pos.getPlane() == dest.getPlane()
                ? pos.distanceTo(dest)
                : (dest != null
                ? Math.max(Math.abs(pos.getX() - dest.getX()), Math.abs(pos.getY() - dest.getY()))
                : -1);
        status = "→ bank d=" + dist;
        WcDebug.log("bank", "walk → " + (dest != null
                ? dest.getX() + "," + dest.getY() + " d=" + dist : "?")
                + " issued=" + issued + " sticky"
                + (activeCenter != null ? " (" + activeCenter.name + ")" : ""));
        return travelTickDelay(me);
    }

    private WorldPoint ensureStickyBankDest(WorldPoint pos) {
        if (pos == null) {
            return null;
        }
        // Geldig sticky: zelfde trip, niet ouder dan 3 min, en nog toegestaan
        if (stickyBankDest != null
                && System.currentTimeMillis() - stickyBankSetMs < 180_000L) {
            if (isLumbridgeBankSticky(stickyBankDest) && !WoodcutterPlugin.bankLumbridge) {
                clearStickyBank();
            } else if (isDraynorBankSticky(stickyBankDest) && !WoodcutterPlugin.bankDraynor) {
                clearStickyBank();
            } else {
                return stickyBankDest;
            }
        }
        String centerName = activeCenter != null ? activeCenter.name : null;
        stickyBankDest = BankHelper.resolveWoodcutBankPoint(pos, centerName,
                WoodcutterPlugin.bankLumbridge, WoodcutterPlugin.bankDraynor);
        stickyBankSetMs = System.currentTimeMillis();
        if (stickyBankDest != null) {
            WcDebug.log("bank", "sticky @" + stickyBankDest.getX() + "," + stickyBankDest.getY()
                    + ",p" + stickyBankDest.getPlane()
                    + (centerName != null ? " (" + centerName + ")" : ""));
        }
        return stickyBankDest;
    }

    private void clearStickyBank() {
        stickyBankDest = null;
        stickyBankSetMs = 0L;
    }

    private static boolean isDraynorBankSticky(WorldPoint dest) {
        if (dest == null || dest.getPlane() != 0) {
            return false;
        }
        return dest.distanceTo(BankHelper.DRAYNOR_BANK_AREA_CENTER) <= 14;
    }

    private static boolean isLumbridgeBankSticky(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        if (dest.getPlane() >= 2) {
            return dest.getX() >= 3195 && dest.getX() <= 3220
                    && dest.getY() >= 3195 && dest.getY() <= 3235;
        }
        // Ground approach / trap (courtyard 3207,3210 ±)
        return LumbridgeStairsHelper.isGroundApproachTile(dest)
                || dest.distanceTo(LumbridgeStairsHelper.SOUTH_APPROACH) <= 6
                || dest.distanceTo(LumbridgeStairsHelper.SOUTH_GROUND) <= 6;
    }

    private void armBankSession(boolean gearOnly) {
        String axe = WcAxes.bestAvailable();
        if (axe == null) {
            axe = "Bronze axe";
        }
        int axeId = WcAxes.idOf(axe);
        boolean needTinder = firemakingNow();
        boolean needKit = WoodcutterPlugin.forestryEvents;
        // Alleen wat we gebruiken bewaren — lagere bijlen / ongebruikte tinderbox → deposit
        java.util.ArrayList<String> keep = new java.util.ArrayList<>();
        keep.add(axe);
        if (needTinder) {
            keep.add("Tinderbox");
        }
        if (needKit) {
            keep.add(ForestryIds.FORESTRY_KIT_NAME);
        }
        java.util.ArrayList<Integer> keepIdList = new java.util.ArrayList<>();
        if (axeId > 0) {
            keepIdList.add(axeId);
        }
        if (needTinder) {
            keepIdList.add(WcTrees.TINDERBOX_ID);
        }
        if (needKit) {
            keepIdList.add(ForestryIds.FORESTRY_KIT_ITEM_ID);
            keepIdList.add(ForestryIds.FORESTRY_KIT_ITEM_ID_LEGACY);
        }
        // Clues doen: scroll/nest houden, logs wél storten — daarna handoff
        if (BotRuntime.wcClueSolver) {
            try {
                for (var item : Inventory.getAll()) {
                    if (item == null || item.getId() <= 0) {
                        continue;
                    }
                    if (!ClueSkillHandoff.shouldKeepClueItem(item.getName())) {
                        continue;
                    }
                    keepIdList.add(item.getId());
                    String nm = item.getName();
                    if (nm != null && !nm.isBlank() && !keep.contains(nm)) {
                        keep.add(nm);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        int[] keepIds = new int[keepIdList.size()];
        for (int i = 0; i < keepIdList.size(); i++) {
            keepIds[i] = keepIdList.get(i);
        }
        java.util.ArrayList<BankSession.Requirement> reqs = new java.util.ArrayList<>();
        String onPerson = WcAxes.bestOnPerson();
        reqs.add(BankSession.Requirement.byId(axe, axeId, 1, 1));
        if (needTinder && !hasTinderbox()) {
            reqs.add(BankSession.Requirement.byId("Tinderbox", WcTrees.TINDERBOX_ID, 1, 1));
        }
        if (needKit && !ForestryKitHandler.hasKitOnPerson() && shouldBankForForestryKit()) {
            reqs.add(BankSession.Requirement.byId(
                    ForestryIds.FORESTRY_KIT_NAME, ForestryIds.FORESTRY_KIT_ITEM_ID, 1, 1));
        }
        boolean wieldAxe = WcAxes.canWield(axe);
        bankSession.configure(
                keep.toArray(new String[0]),
                keepIds,
                reqs,
                false,
                wieldAxe);
        if (!wieldAxe) {
            WcDebug.log("bank", "axe in tas (geen Wield) " + axe
                    + " att=" + net.storm.sdk.items.MeleeWeaponRequirements.getAttackLevelSafe()
                    + " need=" + WcAxes.requiredAttackToWield(axe));
        }
        WcDebug.log("bank", "sessie start gearOnly=" + gearOnly + " axe=" + axe
                + " axeId=" + axeId + " onPerson=" + onPerson
                + " keep=" + keep + " tinder=" + needTinder + " kit=" + needKit
                + " wield=" + wieldAxe + " reqs=" + reqs.size());
    }

    private static boolean isNearAnyBank(WorldPoint pos) {
        if (pos == null) {
            return false;
        }
        try {
            // Lumb kasteel op plane 1/2: distanceTo(ground-bank) = MAX → vals “ver”
            if (pos.getPlane() > 0 && BankHelper.isInLumbridgeCastleBuilding(pos)) {
                return true;
            }
            WorldPoint nearest = BankHelper.getNearestF2pBankPoint(pos);
            return nearest != null && pos.distanceTo(nearest) <= 12;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Click-on-Sight bank-open: alleen écht dichtbij (niet 40 tegels = yew→Lumb-trap). */
    private static boolean canTryBankOpenCos(WorldPoint pos) {
        if (pos == null) {
            return false;
        }
        try {
            if (BankHelper.isNearDraynorBank(pos)) {
                return true;
            }
            if (pos.getPlane() > 0 && BankHelper.isInLumbridgeCastleBuilding(pos)) {
                return true;
            }
            // ≤12 tegels van bank-anker — zelfde als isNearAnyBank, niet INVOKE_RANGE 40
            WorldPoint nearest = BankHelper.getNearestF2pBankPoint(pos);
            return nearest != null && pos.distanceTo(nearest) <= 12;
        } catch (Throwable t) {
            return isNearAnyBank(pos);
        }
    }

    private static String kitTag() {
        if (!WoodcutterPlugin.forestryEvents) {
            return "";
        }
        if (ForestryKitHandler.isEquipped()) {
            return " | kit✓";
        }
        if (ForestryKitHandler.inInventory()) {
            return " | kit inv";
        }
        if (ForestryKitHandler.kitKnownInBank()) {
            return " | kit bank";
        }
        if (!net.storm.sdk.items.BankSnapshot.hasSnapshot()) {
            return " | kit ?bank";
        }
        return " | kit✗";
    }

    private static String bankSnapTag() {
        try {
            if (!net.storm.sdk.items.BankSnapshot.hasSnapshot()) {
                return "geen (eerst bank)";
            }
            boolean kit = ForestryKitHandler.kitKnownInBank();
            boolean axe = false;
            for (int id : WcAxes.IDS) {
                if (net.storm.sdk.items.BankSnapshot.contains(id)) {
                    axe = true;
                    break;
                }
            }
            return "ok kit=" + (kit ? "ja" : "nee") + " axe=" + (axe ? "ja" : "nee");
        } catch (Throwable t) {
            return "?";
        }
    }
}
