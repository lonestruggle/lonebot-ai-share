package net.storm.sdk.game;

import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.interact.AimInteractHelper;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.HumanBanking;
import net.storm.sdk.movement.LumbridgeStairsHelper;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.WalkCamera;
import net.storm.sdk.movement.pathfinder.AlKharidGate;
import net.storm.sdk.movement.pathfinder.GlobalPathfinder;
import net.storm.sdk.movement.pathfinder.Pathfinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Lean F2P-safe bank helpers: exclusions, Lumbridge stairs, open/walk nearest full bank.
 */
public final class BankHelper {

    private static final Logger log = LoggerFactory.getLogger(BankHelper.class);

    private static final WorldPoint COOKING_GUILD_CENTER = new WorldPoint(3143, 3443, 0);
    private static final WorldPoint CRAFTING_GUILD_CENTER = new WorldPoint(2934, 3282, 0);
    private static final WorldPoint SHANTAY_PASS_BANK_CENTER = new WorldPoint(3309, 3120, 0);
    private static final int AVOID_BANK_RADIUS = 20;
    /** Woestijn zuid van Al Kharid-stad (Shantay + omloop), geen stad-bank. */
    private static final int SHANTAY_DESERT_Y_MAX = 3140;
    private static final int SHANTAY_DESERT_X_MIN = 3235;
    private static final int SHANTAY_DESERT_X_MAX = 3360;

    private static final WorldPoint LUMBRIDGE_CASTLE_CENTER = new WorldPoint(3208, 3220, 0);
    private static final int LUMBRIDGE_DETECT_RADIUS = 40;
    private static final int LUMBRIDGE_CASTLE_BUILDING_X_MIN = 3195;
    private static final int LUMBRIDGE_CASTLE_BUILDING_X_MAX = 3220;
    private static final int LUMBRIDGE_CASTLE_BUILDING_Y_MIN = 3195;
    private static final int LUMBRIDGE_CASTLE_BUILDING_Y_MAX = 3235;

    /**
     * Courtyard bij zuid-trap (zelfde als {@link LumbridgeStairsHelper#SOUTH_APPROACH}).
     */
    private static final WorldPoint LUMBRIDGE_BANK_GROUND_APPROACH = LumbridgeStairsHelper.SOUTH_APPROACH;

    /** Bank booth area on Lumbridge castle top floor (plane 2). */
    private static final WorldPoint LUMBRIDGE_BANK_FLOOR_CENTER = new WorldPoint(3208, 3220, 2);

    /**
     * Trap → bank: korte waypoints (niet rechtstreeks booth-tegel — die is fullBlock,
     * BFS faalt dan met empty/timeout vanaf zuid-trap).
     */
    private static final WorldPoint[] LUMBRIDGE_BANK_FLOOR_STEPS = {
            new WorldPoint(3206, 3213, 2),
            new WorldPoint(3207, 3217, 2),
            LUMBRIDGE_BANK_FLOOR_CENTER
    };

    /** Bank-floor south stairs (plane 2). */
    public static final WorldPoint LUMBRIDGE_BANK_FLOOR_STAIRS_TILE = LumbridgeStairsHelper.SOUTH_BANK;

    /** Varrock East bank booth area (CombatBot / F2P list). */
    public static final WorldPoint VARROCK_EAST_BANK = new WorldPoint(3253, 3422, 0);
    /** Varrock West — ~50 tiles zuid van GE; samen in 40-tile COS-bereik. */
    public static final WorldPoint VARROCK_WEST_BANK = new WorldPoint(3189, 3436, 0);

    /**
     * F2P full banks (booth / banker / chest). Geen Cooking Guild, Crafting Guild (40 Craft),
     * geen Shantay (woestijnpoort).
     */
    public static final List<WorldPoint> F2P_BANK_POINTS = Arrays.asList(
            LUMBRIDGE_BANK_FLOOR_CENTER,     // Lumbridge castle bank (plane 2)
            VARROCK_EAST_BANK,              // Varrock East
            VARROCK_WEST_BANK,              // Varrock West
            new WorldPoint(3164, 3486, 0),   // Grand Exchange
            new WorldPoint(3092, 3243, 0),   // Draynor
            new WorldPoint(3096, 3492, 0),   // Edgeville
            new WorldPoint(3013, 3355, 0),   // Falador East
            new WorldPoint(2946, 3368, 0),   // Falador West
            new WorldPoint(3269, 3167, 0),   // Al Kharid stad (niet Shantay)
            new WorldPoint(2566, 2858, 0),   // Corsair Cove (The Corsair Curse)
            new WorldPoint(3352, 3277, 0)    // Emir's Arena chest
    );

    /**
     * Standalone F2P deposit boxes (alleen storten, geen withdraw). Niet Shantay.
     * WC loopt hier niet heen — BankSession moet bijl/kit kunnen pakken.
     */
    public static final List<WorldPoint> F2P_DEPOSIT_BOX_POINTS = Arrays.asList(
            new WorldPoint(3045, 3235, 0),   // Port Sarim (Entrana-monniken)
            new WorldPoint(3029, 3210, 0)    // Port Sarim zuid-dok (Imps)
    );

    private static final int BANK_OPEN_RANGE = 5;
    /**
     * Booth/banker in scene: Click-on-Sight Bank-klik tot deze afstand (client path't zelf).
     * Gelijk aan {@link ClickOnSight#INVOKE_RANGE_TILES} — niet eerst tot 5 tiles stil staan.
     */
    private static final int BANK_APPROACH_MAX = ClickOnSight.INVOKE_RANGE_TILES;
    /** Scene search radius around a bank anchor for booth/banker. */
    private static final int BANK_ANCHOR_SEARCH = 18;
    private static final int DEFAULT_BANK_INTERFACE_WAIT_MS = 400;
    /** Na scene-klik: niet opnieuw walken terwijl client naar booth loopt. */
    private static final long BANK_CLICK_PATH_GRACE_MS = 4_500L;

    /** Draynor village bank — booth only (geen banker-NPC langs muur). */
    public static final WorldPoint DRAYNOR_BANK_AREA_CENTER = new WorldPoint(3092, 3243, 0);
    private static final int DRAYNOR_BANK_AREA_RADIUS = 12;
    private static WorldPoint pinnedDraynorBoothTileForOpen;
    private static WorldPoint lastDraynorBankInteractTile;
    private static long lastBankOpenClickMs;
    private static long lastBankDistantClickMs;
    /**
     * GE ↔ Varrock West (en andere buur-banken): nearest flip't op de weg.
     * Blijf bij de eerste keuze tot de andere écht dichter is.
     */
    private static final int STICKY_BANK_TTL_MS = 90_000;
    private static final int STICKY_SWITCH_MARGIN = 16;
    private static volatile WorldPoint stickyF2pBank;
    private static volatile long stickyF2pBankMs;
    /** Lumb bank-floor: mislukte open-pogingen → sneller lopen i.p.v. stil bij trap. */
    private static int lumbBankOpenFails;
    private static long lumbBankOpenFailResetMs;

    private BankHelper() {
    }

    public static final WorldPoint GE_BANK = new WorldPoint(3164, 3486, 0);
    public static final WorldPoint EDGEVILLE_BANK = new WorldPoint(3096, 3492, 0);

    public static boolean isNearGrandExchange(WorldPoint p) {
        return p != null && p.distanceTo(GE_BANK) <= 25;
    }

    public static boolean isNearGrandExchange() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me != null && me.present && isNearGrandExchange(me.worldLocation);
    }

    /** Walk/open bank near GE (F2P GE booths). */
    public static boolean tryOpenBankAtGrandExchange() {
        if (Bank.isOpen()) {
            return true;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (pos == null || pos.distanceTo(GE_BANK) > 8) {
            MovementHelper.walkTo(GE_BANK);
            return false;
        }
        return openNearestBank();
    }

    public static boolean isInExcludedBankArea(WorldPoint p) {
        if (p == null) {
            return true;
        }
        return p.distanceTo(COOKING_GUILD_CENTER) <= AVOID_BANK_RADIUS
                || p.distanceTo(CRAFTING_GUILD_CENTER) <= AVOID_BANK_RADIUS
                || isShantayDesertBank(p);
    }

    /** Shantay-chest én alles zuid van Al Kharid-stad in de woestijn. */
    private static boolean isShantayDesertBank(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        if (p.distanceTo(SHANTAY_PASS_BANK_CENTER) <= AVOID_BANK_RADIUS) {
            return true;
        }
        return p.getX() >= SHANTAY_DESERT_X_MIN && p.getX() <= SHANTAY_DESERT_X_MAX
                && p.getY() <= SHANTAY_DESERT_Y_MAX;
    }

    public static boolean isInLumbridgeCastleBuilding(WorldPoint p) {
        if (p == null || p.getPlane() < 0 || p.getPlane() > 2) {
            return false;
        }
        return p.getX() >= LUMBRIDGE_CASTLE_BUILDING_X_MIN
                && p.getX() <= LUMBRIDGE_CASTLE_BUILDING_X_MAX
                && p.getY() >= LUMBRIDGE_CASTLE_BUILDING_Y_MIN
                && p.getY() <= LUMBRIDGE_CASTLE_BUILDING_Y_MAX;
    }

    /** Lumbridge castle bank route (building or within detect radius of center). */
    public static boolean isNearLumbridgeCastleBankRoute(WorldPoint p) {
        if (p == null) {
            return false;
        }
        if (isInLumbridgeCastleBuilding(p)) {
            return true;
        }
        return p.distanceTo(LUMBRIDGE_CASTLE_CENTER) <= LUMBRIDGE_DETECT_RADIUS;
    }

    /**
     * Open nearest full bank (booth / banker). Lumbridge plane &lt; 2 → stairs first;
     * Draynor → eerst naar booth lopen (CombatBot-parity).
     *
     * @return {@code true} if bank is open or an open/walk action was started
     */
    public static boolean openNearestBank() {
        return tryOpenFullBank();
    }

    /**
     * CombatBot {@code tryOpenFullBank}: stairs → Draynor approach → booth/banker klik → wait open.
     */
    public static boolean tryOpenFullBank() {
        if (Bank.isOpen()) {
            clearStickyF2pBank();
            MovementHelper.clearPath();
            return true;
        }
        try {
            if (RandomEventHandler.isBusy() || RandomEventHandler.shouldYieldForLampAtBank()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint myPos = me != null && me.present ? me.worldLocation : null;
        if (myPos == null) {
            return false;
        }
        if (BankOpenZones.contains(myPos)) {
            MovementHelper.clearPath();
        }

        if (ensureLumbridgeCastleStairProgress()) {
            return true;
        }

        // Al op Lumb bank-floor: nooit naar ground F2P-trap (plane 0) lopen — dat was Bottom-floor i.p.v. bank
        if (myPos.getPlane() >= 2 && isInLumbridgeCastleBuilding(myPos)) {
            return tryOpenLumbridgeBankFloor(myPos);
        }

        ITileObject booth = resolveFullBankObject(myPos);
        INPC banker = isNearDraynorBank(myPos) ? null : resolveBankerNpc(myPos);

        long now = System.currentTimeMillis();

        // Booth/banker al in scene → Bank-klik tijdens lopen (COS) — niet eerst stil staan
        if (trySceneClickBankBoothOrBanker(myPos, booth, banker, now)) {
            return true;
        }

        WorldPoint boothTile = booth != null ? booth.getWorldLocation() : null;
        WorldPoint bankerTile = banker != null ? banker.getWorldLocation() : null;
        int dBooth = boothTile != null ? myPos.distanceTo(boothTile) : Integer.MAX_VALUE;
        int dBanker = bankerTile != null ? myPos.distanceTo(bankerTile) : Integer.MAX_VALUE;

        // Na Bank-klik: niet lopen — dat sluit de bank terwijl die nog opent.
        // Ruimer bereik + langere quiet dan alleen ≤5 tegels / 900ms.
        if (now - lastBankOpenClickMs < 1_800L
                && (dBooth <= BANK_APPROACH_MAX || dBanker <= BANK_APPROACH_MAX)) {
            MovementHelper.clearPath();
            return true;
        }

        // Fallback dichtbij (scene-klik hierboven faalde)
        if (dBooth <= BANK_OPEN_RANGE && booth != null) {
            lastBankOpenClickMs = now;
            return clickBankObject(booth);
        }
        if (dBanker <= BANK_OPEN_RANGE && banker != null) {
            lastBankOpenClickMs = now;
            return clickBanker(banker);
        }

        if (isNearDraynorBank(myPos)) {
            if (BankOpenZones.draynor(myPos)) {
                MovementHelper.clearPath();
                if (trySceneClickBankBoothOrBanker(myPos, booth, null, System.currentTimeMillis())) {
                    return true;
                }
                if (booth != null) {
                    lastBankOpenClickMs = System.currentTimeMillis();
                    return clickBankObject(booth);
                }
                BotRuntime.logConsole("[Bank] Draynor-zone — geen walk naar booth/anker");
                return true;
            }
            if (walkTowardDraynorBankAccess()) {
                return true;
            }
        }

        if (booth != null && dBooth < dBanker
                && boothBelongsToChosenBank(myPos, boothTile)) {
            return walkToward(boothTile);
        }
        if (banker != null && boothBelongsToChosenBank(myPos, bankerTile)) {
            return walkToward(bankerTile);
        }

        WorldPoint f2p = getNearestF2pBankPoint(myPos);
        return f2p != null && walkToward(f2p);
    }

    /**
     * Lumbridge top floor: COS Bank zodra booth/banker in scene (trap telt mee).
     * Geen “eerst naar booth lopen” — client path't zelf; BFS naar booth-tegel faalt.
     */
    private static boolean tryOpenLumbridgeBankFloor(WorldPoint myPos) {
        if (Bank.isOpen()) {
            lumbBankOpenFails = 0;
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lumbBankOpenFailResetMs > 12_000L) {
            lumbBankOpenFails = 0;
        }

        ITileObject booth = findNearestFullBankObject();
        INPC banker = findNearestBankerNpc();
        WorldPoint boothTile = booth != null ? booth.getWorldLocation() : null;
        WorldPoint bankerTile = banker != null ? banker.getWorldLocation() : null;
        int dBooth = boothTile != null ? myPos.distanceTo(boothTile) : Integer.MAX_VALUE;
        int dBanker = bankerTile != null ? myPos.distanceTo(bankerTile) : Integer.MAX_VALUE;

        // Boven = COS. Trap/zuidkant: bank ~10–15 tegels — nog steeds INVOKE_RANGE.
        if (booth != null && dBooth <= BANK_APPROACH_MAX
                && (banker == null || dBooth <= dBanker)) {
            return openLumbridgeBankTarget(booth, boothTile, myPos, now);
        }
        if (banker != null && dBanker <= BANK_APPROACH_MAX) {
            return openLumbridgeBankBanker(banker, bankerTile, myPos, now);
        }
        // Geen booth/banker in scene → pas naar bank-midden (niet booth-tegel)
        log.info("[BankHelper] Lumb bank-floor — geen booth in scene → walk center");
        BotRuntime.logConsole("[Bank] Lumb geen booth → walk center");
        return walkToward(LUMBRIDGE_BANK_FLOOR_CENTER);
    }

    /**
     * Walkable doel op Lumb plane 2: stand naast booth of bank-midden, via korte stappen
     * (booth-tegel zelf → BFS leeg). Alleen als COS echt niet kan.
     */
    private static WorldPoint lumbridgeBankFloorWalkDest(WorldPoint myPos, WorldPoint boothOrNear) {
        WorldPoint goal = LUMBRIDGE_BANK_FLOOR_CENTER;
        if (boothOrNear != null) {
            WorldPoint stand = Pathfinder.nearestWalkableStand(boothOrNear);
            if (stand == null) {
                stand = GlobalPathfinder.nearestWalkable(boothOrNear);
            }
            if (stand != null
                    && (stand.getX() != boothOrNear.getX() || stand.getY() != boothOrNear.getY())) {
                goal = stand;
            }
        }
        if (myPos == null || myPos.getPlane() < 2) {
            return goal;
        }
        if (myPos.distanceTo(goal) <= 6) {
            return goal;
        }
        for (WorldPoint wp : LUMBRIDGE_BANK_FLOOR_STEPS) {
            if (myPos.distanceTo(wp) > 2) {
                return wp;
            }
        }
        return goal;
    }

    private static boolean walkTowardLumbridgeBankFloor(WorldPoint myPos, WorldPoint boothOrNear) {
        WorldPoint step = lumbridgeBankFloorWalkDest(myPos, boothOrNear);
        if (walkToward(step)) {
            return true;
        }
        if (step.distanceTo(LUMBRIDGE_BANK_FLOOR_CENTER) > 0
                && walkToward(LUMBRIDGE_BANK_FLOOR_CENTER)) {
            BotRuntime.logConsole("[Bank] Lumb walk-fallback center @"
                    + fmtWp(LUMBRIDGE_BANK_FLOOR_CENTER));
            return true;
        }
        return false;
    }

    private static boolean openLumbridgeBankTarget(ITileObject booth, WorldPoint boothTile,
                                                   WorldPoint myPos, long now) {
        if (booth == null || boothTile == null || myPos == null) {
            return false;
        }
        int dist = myPos.distanceTo(boothTile);
        boolean onScreen = ClickOnSight.onScreen(booth);

        if (!onScreen) {
            WalkCamera.ensureLookingAt(boothTile);
        }

        // Alleen na veel fails én als we echt te ver zijn — anders COS blijven proberen
        if (lumbBankOpenFails >= 5 && dist > BANK_OPEN_RANGE + 2) {
            log.info("[BankHelper] Lumb booth fail×{} → walk step (COS gaf op)", lumbBankOpenFails);
            BotRuntime.logConsole("[Bank] Lumb COS opgave → walk d=" + dist);
            lumbBankOpenFails = 0;
            lumbBankOpenFailResetMs = now;
            return walkTowardLumbridgeBankFloor(myPos, boothTile);
        }

        HumanBanking.pauseBeforeBankOpenClick();
        ClickOnSight.Result r = ClickOnSight.interactOrApproach(
                booth, boothTile, "Bank", "Use");
        log.info("[BankHelper] Lumb bank-floor booth d={} onScreen={} → {}", dist, onScreen, r);
        BotRuntime.logConsole("[Bank] Lumb booth COS " + r.outcome + " d=" + dist
                + (onScreen ? " screen" : " off-screen"));

        if (r.clicked) {
            lumbBankOpenFails = 0;
            lastBankOpenClickMs = now;
            lastBankDistantClickMs = now;
            return true;
        }
        if (r.outcome == ClickOnSight.Outcome.WALK || r.outcome == ClickOnSight.Outcome.BLOCKED) {
            // COS liet client al lopen / klik gequeued — niet zelf BFS
            lumbBankOpenFails = 0;
            lastBankOpenClickMs = now;
            return true;
        }

        lumbBankOpenFails++;
        lumbBankOpenFailResetMs = now;
        WalkCamera.ensureLookingAt(boothTile);
        if (tryInvokeBankObject(booth)) {
            lumbBankOpenFails = 0;
            lastBankOpenClickMs = now;
            lastBankDistantClickMs = now;
            BotRuntime.logConsole("[Bank] Lumb booth invoke retry d=" + dist);
            return true;
        }
        // Binnen bereik: volgende tick opnieuw COS — niet richting bank lopen
        BotRuntime.logConsole("[Bank] Lumb COS retry d=" + dist + " fails=" + lumbBankOpenFails);
        return true;
    }

    private static boolean openLumbridgeBankBanker(INPC banker, WorldPoint bankerTile,
                                                   WorldPoint myPos, long now) {
        if (banker == null || bankerTile == null) {
            return false;
        }
        int dist = myPos.distanceTo(bankerTile);
        boolean onScreen = AimInteractHelper.isNpcOnScreen(banker);
        if (!onScreen) {
            WalkCamera.ensureLookingAt(bankerTile);
        }
        HumanBanking.pauseBeforeBankOpenClick();
        if (MenuInteract.interactNpcByIndex(banker.getIndex(), "Bank")) {
            lumbBankOpenFails = 0;
            lastBankOpenClickMs = now;
            lastBankDistantClickMs = now;
            BotRuntime.logConsole("[Bank] Lumb banker invoke d=" + dist);
            return true;
        }
        ClickOnSight.Result r = ClickOnSight.interactOrApproach(banker, bankerTile, "Bank");
        if (r.clicked || r.outcome == ClickOnSight.Outcome.WALK || r.outcome == ClickOnSight.Outcome.BLOCKED) {
            lumbBankOpenFails = 0;
            lastBankOpenClickMs = now;
            lastBankDistantClickMs = now;
            return true;
        }
        lumbBankOpenFails++;
        lumbBankOpenFailResetMs = now;
        WalkCamera.ensureLookingAt(bankerTile);
        if (dist <= BANK_APPROACH_MAX) {
            BotRuntime.logConsole("[Bank] Lumb banker COS retry d=" + dist + " fails=" + lumbBankOpenFails);
            return true;
        }
        return walkTowardLumbridgeBankFloor(myPos, bankerTile);
    }

    private static String fmtWp(WorldPoint wp) {
        return wp != null ? wp.getX() + "," + wp.getY() + "," + wp.getPlane() : "?";
    }

    private static boolean tryInvokeBankObject(ITileObject booth) {
        if (booth == null) {
            return false;
        }
        String action = booth.hasAction("Bank") ? "Bank" : "Use";
        net.runelite.api.TileObject raw = TileObjects.unwrap(booth);
        return raw != null && MenuInteract.interactObject(raw, action);
    }

    /**
     * Booth/banker in geladen scene: Click-on-Sight Bank (ook tijdens lopen, ook Draynor).
     * Client path't zelf — geen “eerst stil staan op 5 tegels”.
     */
    private static boolean trySceneClickBankBoothOrBanker(WorldPoint myPos, ITileObject booth, INPC banker,
                                                          long now) {
        if (myPos == null) {
            return false;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (shouldWaitForBankClickPath(me, now)) {
            log.debug("[BankHelper] wacht op bank-click-pad (COS onderweg)");
            return true;
        }

        // Lumb F0/F1: eerst trap, geen bank-klik beneden
        boolean nearLumbridgeBelow = isNearLumbridgeCastleBankRoute(myPos) && myPos.getPlane() < 2;

        if (booth != null && booth.getWorldLocation() != null) {
            WorldPoint wp = booth.getWorldLocation();
            int dist = myPos.distanceTo(wp);
            if (nearLumbridgeBelow && dist > BANK_OPEN_RANGE) {
                return false;
            }
            if (!boothBelongsToChosenBank(myPos, wp)) {
                logSkipOtherBank("booth", wp, dist, myPos);
                booth = null;
            } else if (dist <= BANK_APPROACH_MAX && Pathfinder.isInLoadedScene(wp)) {
                HumanBanking.pauseBeforeBankOpenClick();
                String action = booth.hasAction("Bank") ? "Bank" : "Use";
                ClickOnSight.Result r = ClickOnSight.interactOrApproach(booth, wp, action, "Use");
                log.info("[BankHelper] COS booth d={} → {}", dist, r);
                BotRuntime.logConsole("[Bank] COS booth " + r.outcome + " d=" + dist);
                if (r.clicked) {
                    lastBankOpenClickMs = now;
                    lastBankDistantClickMs = now;
                    // Stop actief pad — anders klikt de walker Walk-here en sluit de bank weer
                    MovementHelper.clearPath();
                    if (isNearDraynorBank(myPos) || isNearDraynorBank(wp)) {
                        pinnedDraynorBoothTileForOpen = wp;
                        lastDraynorBankInteractTile = wp;
                    }
                    return true;
                }
                if (r.outcome == ClickOnSight.Outcome.WALK || r.outcome == ClickOnSight.Outcome.BLOCKED) {
                    if (walkLeakedFromBank(myPos, wp)) {
                        return stayAtBankAfterBadWalk(myPos, booth, banker);
                    }
                    return true;
                }
                // Fallback: oude invoke/click
                if (clickBankObject(booth)) {
                    lastBankOpenClickMs = now;
                    lastBankDistantClickMs = now;
                    return true;
                }
            }
        }

        boolean nearDraynor = isNearDraynorBank(myPos);
        if (banker != null && banker.getWorldLocation() != null && !nearDraynor) {
            WorldPoint wp = banker.getWorldLocation();
            int dist = myPos.distanceTo(wp);
            if (nearLumbridgeBelow && dist > BANK_OPEN_RANGE) {
                return false;
            }
            if (!boothBelongsToChosenBank(myPos, wp)) {
                logSkipOtherBank("banker", wp, dist, myPos);
            } else if (dist <= BANK_APPROACH_MAX && Pathfinder.isInLoadedScene(wp)) {
                HumanBanking.pauseBeforeBankOpenClick();
                ClickOnSight.Result r = ClickOnSight.interactOrApproach(banker, wp, "Bank");
                log.info("[BankHelper] COS banker d={} → {}", dist, r);
                BotRuntime.logConsole("[Bank] COS banker " + r.outcome + " d=" + dist);
                if (r.clicked) {
                    lastBankOpenClickMs = now;
                    lastBankDistantClickMs = now;
                    MovementHelper.clearPath();
                    return true;
                }
                if (r.outcome == ClickOnSight.Outcome.WALK || r.outcome == ClickOnSight.Outcome.BLOCKED) {
                    if (walkLeakedFromBank(myPos, wp)) {
                        return stayAtBankAfterBadWalk(myPos, booth, banker);
                    }
                    return true;
                }
                if (clickBanker(banker)) {
                    lastBankOpenClickMs = now;
                    lastBankDistantClickMs = now;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldWaitForBankClickPath(Players.LocalSnap me, long now) {
        if (me == null || !me.moving) {
            return false;
        }
        if (Bank.isOpen()) {
            return false;
        }
        return now - lastBankDistantClickMs <= BANK_CLICK_PATH_GRACE_MS;
    }

    /** Storm/CombatBot SDK bank-open poll na klik. */
    public static boolean openSdkBankAndWait() {
        return openSdkBankAndWait(DEFAULT_BANK_INTERFACE_WAIT_MS);
    }

    public static boolean openSdkBankAndWait(int timeoutMs) {
        if (Bank.isOpen()) {
            return true;
        }
        // Probeer opnieuw dichtstbijzijnde booth
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint myPos = me != null && me.present ? me.worldLocation : null;
        ITileObject booth = resolveFullBankObject(myPos);
        if (booth != null && myPos != null && myPos.distanceTo(booth.getWorldLocation()) <= BANK_OPEN_RANGE + 2) {
            clickBankObject(booth);
        }
        return waitUntilBankOpen(timeoutMs);
    }

    private static boolean waitUntilBankOpen(int timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(200, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (Bank.isOpen()) {
                return true;
            }
            try {
                Thread.sleep(40 + (int) (Math.random() * 40));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Bank.isOpen();
            }
        }
        return Bank.isOpen();
    }

    public static boolean isNearDraynorBank(WorldPoint point) {
        return point != null && point.getPlane() == DRAYNOR_BANK_AREA_CENTER.getPlane()
                && point.distanceTo(DRAYNOR_BANK_AREA_CENTER) <= DRAYNOR_BANK_AREA_RADIUS;
    }

    public static boolean isNearDraynorBank() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me != null && me.present && isNearDraynorBank(me.worldLocation);
    }

    /** Dichtstbijzijnde Bank booth (Draynor: booth in het bank-gebied). */
    public static ITileObject findNearestBankBooth() {
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint myPos = me != null && me.present ? me.worldLocation : null;
        return resolveFullBankObject(myPos);
    }

    /** Dichtstbijzijnde banker-NPC (Draynor: meestal null — booth only). */
    public static INPC findNearestBanker() {
        return findNearestBankerNpc();
    }

    /** Loop/COS naar Draynor booth (geen banker-NPC). */
    public static boolean walkTowardDraynorBankAccess() {
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint myPos = me != null && me.present ? me.worldLocation : null;
        if (myPos == null || !isNearDraynorBank(myPos)) {
            return false;
        }
        if (BankOpenZones.draynor(myPos)) {
            MovementHelper.clearPath();
            ITileObject inZone = pickVariedDraynorBankObject(myPos);
            if (inZone != null) {
                long now = System.currentTimeMillis();
                if (trySceneClickBankBoothOrBanker(myPos, inZone, null, now)) {
                    return true;
                }
                return clickBankObject(inZone);
            }
            return true;
        }
        ITileObject booth = pickVariedDraynorBankObject(myPos);
        if (booth != null && booth.getWorldLocation() != null) {
            WorldPoint boothLoc = booth.getWorldLocation();
            int dist = myPos.distanceTo(boothLoc);
            if (dist <= BANK_APPROACH_MAX) {
                long now = System.currentTimeMillis();
                if (trySceneClickBankBoothOrBanker(myPos, booth, null, now)) {
                    return true;
                }
                if (dist <= BANK_OPEN_RANGE) {
                    return clickBankObject(booth);
                }
            }
            if (dist > BANK_OPEN_RANGE) {
                log.debug("[BankHelper] walkTowardDraynorBankAccess booth dist={}", dist);
                return walkToward(boothLoc);
            }
        }
        return walkToward(DRAYNOR_BANK_AREA_CENTER);
    }

    /** Lumbridge F0/F1 → trap omhoog (publiek voor WC/handlers). */
    public static boolean ensureLumbridgeCastleStairProgress() {
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint myPos = me != null && me.present ? me.worldLocation : null;
        if (myPos == null || myPos.getPlane() >= 2 || !isNearLumbridgeCastleBankRoute(myPos)) {
            return false;
        }
        log.debug("[BankHelper] ensureLumbridgeCastleStairs plane={}", myPos.getPlane());
        return progressLumbridgeStairs(myPos);
    }

    /** Alias CombatBot {@code handleLumbridgeStairs}. */
    public static boolean handleLumbridgeStairs() {
        return ensureLumbridgeCastleStairProgress();
    }

    private static ITileObject resolveFullBankObject(WorldPoint myPos) {
        if (myPos != null && isNearDraynorBank(myPos)) {
            return pickVariedDraynorBankObject(myPos);
        }
        WorldPoint anchor = myPos != null ? getNearestF2pBankPoint(myPos) : null;
        if (anchor != null) {
            ITileObject near = findFullBankObjectNear(anchor, BANK_ANCHOR_SEARCH);
            if (near != null) {
                return near;
            }
            // Geen globale nearest: GE-pad zou anders Varrock-West-booth invocen (d=32–37).
            return null;
        }
        return findNearestFullBankObject();
    }

    private static INPC resolveBankerNpc(WorldPoint myPos) {
        WorldPoint anchor = myPos != null ? getNearestF2pBankPoint(myPos) : null;
        if (anchor != null) {
            INPC near = findBankerNear(anchor, BANK_ANCHOR_SEARCH);
            if (near != null) {
                return near;
            }
            return null;
        }
        return findNearestBankerNpc();
    }

    /** COS/walk ging naar Shantay of een booth ver van de F2P-ankerbank. */
    private static boolean walkLeakedFromBank(WorldPoint myPos, WorldPoint intended) {
        WorldPoint going = null;
        try {
            going = MovementHelper.getActiveDestination();
        } catch (Throwable ignored) {
        }
        if (going == null) {
            return false;
        }
        if (isInExcludedBankArea(going)) {
            return true;
        }
        WorldPoint pin = intended != null ? intended : getNearestF2pBankPoint(myPos);
        if (pin != null && going.getPlane() == pin.getPlane() && going.distanceTo(pin) > BANK_ANCHOR_SEARCH) {
            return true;
        }
        WorldPoint anchor = getNearestF2pBankPoint(myPos);
        return anchor != null && going.getPlane() == anchor.getPlane()
                && going.distanceTo(anchor) > BANK_ANCHOR_SEARCH;
    }

    private static boolean stayAtBankAfterBadWalk(WorldPoint myPos, ITileObject booth, INPC banker) {
        try {
            MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        BotRuntime.logConsole("[Bank] open-walk wees af — blijf bij stad-bank (geen Shantay)");
        WorldPoint anchor = getNearestF2pBankPoint(myPos);
        boolean atBank = BankOpenZones.contains(myPos)
                || (anchor != null && myPos != null && myPos.getPlane() == anchor.getPlane()
                && myPos.distanceTo(anchor) <= BANK_OPEN_RANGE + 3);
        if (atBank) {
            long now = System.currentTimeMillis();
            if (booth != null && !isInExcludedBankArea(booth.getWorldLocation())
                    && clickBankObject(booth)) {
                lastBankOpenClickMs = now;
                return true;
            }
            if (banker != null && !isInExcludedBankArea(banker.getWorldLocation())
                    && clickBanker(banker)) {
                lastBankOpenClickMs = now;
                return true;
            }
            return true;
        }
        return anchor != null && walkToward(anchor);
    }

    private static ITileObject pickVariedDraynorBankObject(WorldPoint myPos) {
        java.util.ArrayList<ITileObject> inArea = new java.util.ArrayList<>();
        try {
            java.util.List<ITileObject> all = TileObjects.getAll(BankHelper::isFullBankTileObject);
            if (all != null) {
                for (ITileObject obj : all) {
                    WorldPoint w = obj != null ? obj.getWorldLocation() : null;
                    if (w != null && isNearDraynorBank(w)) {
                        inArea.add(obj);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (inArea.isEmpty()) {
            return findNearestFullBankObject();
        }
        if (pinnedDraynorBoothTileForOpen != null) {
            for (ITileObject obj : inArea) {
                WorldPoint w = obj.getWorldLocation();
                if (w != null && w.equals(pinnedDraynorBoothTileForOpen)) {
                    return obj;
                }
            }
            pinnedDraynorBoothTileForOpen = null;
        }
        ITileObject nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (ITileObject obj : inArea) {
            WorldPoint w = obj.getWorldLocation();
            if (w == null || myPos == null) {
                continue;
            }
            int d = myPos.distanceTo(w);
            if (d < bestDist) {
                bestDist = d;
                nearest = obj;
            }
        }
        if (nearest != null && nearest.getWorldLocation() != null) {
            WorldPoint pt = nearest.getWorldLocation();
            if (bestDist <= BANK_OPEN_RANGE + 1) {
                pinnedDraynorBoothTileForOpen = pt;
                lastDraynorBankInteractTile = pt;
            } else if (lastDraynorBankInteractTile != null && inArea.size() > 1) {
                for (ITileObject obj : inArea) {
                    WorldPoint w = obj.getWorldLocation();
                    if (w != null && !w.equals(lastDraynorBankInteractTile)) {
                        lastDraynorBankInteractTile = w;
                        return obj;
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Walk to nearest F2P bank point (not excluded). Lumbridge near castle plane &lt; 2 → stairs first.
     *
     * @return {@code true} if a walk/interact was started
     */
    public static boolean walkToNearestFullBank() {
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint myPos = me != null && me.present ? me.worldLocation : null;
        if (myPos == null) {
            return false;
        }

        if (myPos.getPlane() < 2 && isNearLumbridgeCastleBankRoute(myPos)) {
            WorldPoint nearestF2p = getNearestF2pBankPoint(myPos);
            boolean nearestIsLumbridge = nearestF2p != null
                    && nearestF2p.getPlane() == 2
                    && isInLumbridgeCastleBuilding(nearestF2p);
            if (isInLumbridgeCastleBuilding(myPos) || nearestIsLumbridge
                    || myPos.distanceTo(LUMBRIDGE_CASTLE_CENTER) <= LUMBRIDGE_DETECT_RADIUS) {
                log.debug("[BankHelper] walkToNearestFullBank: Lumbridge stairs-first");
                return progressLumbridgeStairs(myPos);
            }
        }

        ITileObject booth = resolveFullBankObject(myPos);
        if (booth != null && booth.getWorldLocation() != null
                && boothBelongsToChosenBank(myPos, booth.getWorldLocation())) {
            int d = myPos.distanceTo(booth.getWorldLocation());
            if (d <= BANK_OPEN_RANGE) {
                return clickBankObject(booth);
            }
        }

        INPC banker = resolveBankerNpc(myPos);
        if (banker != null && banker.getWorldLocation() != null
                && boothBelongsToChosenBank(myPos, banker.getWorldLocation())) {
            int d = myPos.distanceTo(banker.getWorldLocation());
            if (d <= BANK_OPEN_RANGE) {
                return clickBanker(banker);
            }
        }

        WorldPoint f2p = getNearestF2pBankPoint(myPos);
        if (f2p == null || isInExcludedBankArea(f2p)) {
            return false;
        }
        return walkToward(f2p);
    }

    public static WorldPoint getNearestF2pBankPoint(WorldPoint from) {
        return getNearestF2pBankPoint(from, null);
    }

    /**
     * Dichtstbijzijnde F2P-bank op <b>loop-score</b> (geen crow-flies door de Al Kharid-poort).
     */
    public static WorldPoint getNearestF2pBankPoint(WorldPoint from,
                                                    java.util.function.Predicate<WorldPoint> skip) {
        if (from == null) {
            return null;
        }
        // Op Lumb bank-floor: anker = booth-midden plane 2 (niet ground-trap)
        if (from.getPlane() >= 2 && isInLumbridgeCastleBuilding(from)) {
            return LUMBRIDGE_BANK_FLOOR_CENTER;
        }
        boolean gateBlocked = !AlKharidGate.canPass();
        boolean fromEast = AlKharidGate.isEastSide(from);
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        WorldPoint bestAny = null;
        int bestAnyDist = Integer.MAX_VALUE;
        for (WorldPoint w : F2P_BANK_POINTS) {
            if (w == null || isInExcludedBankArea(w)) {
                continue;
            }
            if (skip != null && skip.test(w)) {
                continue;
            }
            WorldPoint candidate = w;
            int d = bankTravelScore(from, candidate);
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = candidate;
            }
            // Poort dicht: alleen banken aan dezelfde kant (geen crow-flies over de tol)
            if (gateBlocked && fromEast != AlKharidGate.isEastSide(candidate)) {
                continue;
            }
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        WorldPoint pick = best != null ? best : bestAny;
        pick = applyStickyF2pBank(from, pick, skip);
        if (pick != null) {
            logNearestBank(from, pick, bankTravelScore(from, pick));
        }
        return pick;
    }

    public static WorldPoint getNearestDepositBoxPoint(WorldPoint from) {
        if (from == null) {
            return null;
        }
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        for (WorldPoint w : F2P_DEPOSIT_BOX_POINTS) {
            if (w == null || isInExcludedBankArea(w)) {
                continue;
            }
            int d = planeAwareDistance(from, w);
            if (d < bestDist) {
                bestDist = d;
                best = w;
            }
        }
        return best;
    }

    /** WC: dichtstbijzijnde volle F2P-bank. Naam van de spot telt niet. */
    public static WorldPoint resolveWoodcutBankPoint(WorldPoint from, String centerName) {
        return resolveWoodcutBankPoint(from, centerName, true, true);
    }

    /**
     * WC-bank met optionele Lumb/Draynor-filters (beide aan = dichtstbijzijnde van die twee
     * plus overige F2P; uit = die bank overslaan). Beide uit → beide weer aan.
     */
    public static WorldPoint resolveWoodcutBankPoint(WorldPoint from, String centerName,
                                                    boolean allowLumbridge, boolean allowDraynor) {
        boolean useLumb = allowLumbridge;
        boolean useDray = allowDraynor;
        if (!useLumb && !useDray) {
            useLumb = true;
            useDray = true;
        }
        if (from == null) {
            return null;
        }
        // Op Lumb bank-floor: blijf alleen als Lumb toegestaan
        if (from.getPlane() >= 2 && isInLumbridgeCastleBuilding(from) && useLumb) {
            return LUMBRIDGE_BANK_FLOOR_CENTER;
        }
        boolean gateBlocked = !AlKharidGate.canPass();
        boolean fromEast = AlKharidGate.isEastSide(from);
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        WorldPoint bestAny = null;
        int bestAnyDist = Integer.MAX_VALUE;
        for (WorldPoint w : F2P_BANK_POINTS) {
            if (w == null || isInExcludedBankArea(w)) {
                continue;
            }
            if (isLumbridgeWcBankPoint(w) && !useLumb) {
                continue;
            }
            if (isDraynorWcBankPoint(w) && !useDray) {
                continue;
            }
            int d = bankTravelScore(from, w);
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = w;
            }
            if (gateBlocked && fromEast != AlKharidGate.isEastSide(w)) {
                continue;
            }
            if (d < bestDist) {
                bestDist = d;
                best = w;
            }
        }
        WorldPoint pick = best != null ? best : bestAny;
        if (pick != null) {
            BotRuntime.logConsole("[Bank] WC → "
                    + pick.getX() + "," + pick.getY() + ",p" + pick.getPlane()
                    + " (Lumb=" + useLumb + " Dray=" + useDray
                    + (centerName != null && !centerName.isBlank() ? " spot=" + centerName : "")
                    + ")");
        }
        return pick;
    }

    private static boolean isLumbridgeWcBankPoint(WorldPoint w) {
        if (w == null) {
            return false;
        }
        if (w.getPlane() >= 1 && isInLumbridgeCastleBuilding(w)) {
            return true;
        }
        return w.distanceTo(LUMBRIDGE_BANK_FLOOR_CENTER) <= 2
                || w.distanceTo(LUMBRIDGE_BANK_GROUND_APPROACH) <= 4;
    }

    private static boolean isDraynorWcBankPoint(WorldPoint w) {
        return w != null && w.getPlane() == 0
                && w.distanceTo(DRAYNOR_BANK_AREA_CENTER) <= DRAYNOR_BANK_AREA_RADIUS;
    }

    /**
     * Loop naar dichtstbijzijnde volle F2P-bank (booth/chest). Geen deposit-box (geen withdraw).
     */
    public static boolean walkToWoodcutBank(WorldPoint from, String centerName) {
        WorldPoint dest = resolveWoodcutBankPoint(from, centerName);
        if (dest != null) {
            BotRuntime.logConsole("[Bank] WC → nearest "
                    + dest.getX() + "," + dest.getY()
                    + (centerName != null && !centerName.isBlank() ? " (spot " + centerName + ")" : ""));
            return walkToward(dest);
        }
        return walkToNearestFullBank();
    }

    /** Chebyshev; bij andere plane alleen xy (anders distanceTo = MAX → geen bank-doel boven Lumb). */
    private static int planeAwareDistance(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return Integer.MAX_VALUE;
        }
        if (from.getPlane() == to.getPlane()) {
            return from.distanceTo(to);
        }
        return Math.max(Math.abs(from.getX() - to.getX()), Math.abs(from.getY() - to.getY()));
    }

    /**
     * Loop-kosten i.p.v. vogelvlucht. Pay-toll Lumb swamp → Al Kharid bank was 164 stappen
     * bij crow-flies 39; west van de poort is Lumbridge-kasteel dichter.
     */
    private static final int ALKHARID_GATE_WALK_PENALTY = 90;
    private static final int STAIRS_PLANE_PENALTY = 25;
    private static volatile long lastNearestBankLogMs;
    private static volatile String lastNearestBankLog = "";

    private static int bankTravelScore(WorldPoint from, WorldPoint to) {
        int d = planeAwareDistance(from, to);
        if (from.getPlane() != to.getPlane()) {
            d += STAIRS_PLANE_PENALTY;
        }
        try {
            if (AlKharidGate.needGate(from, to)) {
                d += ALKHARID_GATE_WALK_PENALTY;
            }
        } catch (Throwable ignored) {
        }
        return d;
    }

    private static void logNearestBank(WorldPoint from, WorldPoint pick, int score) {
        String msg = "nearest " + pick.getX() + "," + pick.getY() + ",p" + pick.getPlane()
                + " score=" + score + " from " + from.getX() + "," + from.getY() + ",p" + from.getPlane();
        long now = System.currentTimeMillis();
        if (msg.equals(lastNearestBankLog) && now - lastNearestBankLogMs < 2500L) {
            return;
        }
        lastNearestBankLog = msg;
        lastNearestBankLogMs = now;
        BotRuntime.logConsole("[Bank] " + msg);
    }

    /**
     * One tick of: walk to Varrock East bank (Lumbridge castle bypass), then open booth/banker.
     *
     * @return {@code true} if bank is already open, or walk/open action was started
     */
    public static boolean progressOpenVarrockEastBank() {
        if (Bank.isOpen()) {
            return true;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint myPos = me != null && me.present ? me.worldLocation : null;
        if (myPos == null) {
            return false;
        }

        ITileObject booth = findFullBankObjectNear(VARROCK_EAST_BANK, BANK_ANCHOR_SEARCH);
        INPC banker = findBankerNear(VARROCK_EAST_BANK, BANK_ANCHOR_SEARCH);

        WorldPoint boothTile = booth != null ? booth.getWorldLocation() : null;
        WorldPoint bankerTile = banker != null ? banker.getWorldLocation() : null;
        int dBooth = boothTile != null ? myPos.distanceTo(boothTile) : Integer.MAX_VALUE;
        int dBanker = bankerTile != null ? myPos.distanceTo(bankerTile) : Integer.MAX_VALUE;
        int dAnchor = myPos.distanceTo(VARROCK_EAST_BANK);

        if (dBooth <= BANK_OPEN_RANGE && booth != null) {
            log.info("[BankHelper] Varrock East: Bank booth @{}", boothTile);
            return clickBankObject(booth);
        }
        if (dBanker <= BANK_OPEN_RANGE && banker != null) {
            log.info("[BankHelper] Varrock East: Banker @{}", bankerTile);
            return clickBanker(banker);
        }

        if (booth != null && dBooth <= dBanker && dBooth < Integer.MAX_VALUE) {
            log.debug("[BankHelper] Varrock East: walk booth dist={}", dBooth);
            return walkToward(boothTile);
        }
        if (banker != null && dBanker < Integer.MAX_VALUE) {
            log.debug("[BankHelper] Varrock East: walk banker dist={}", dBanker);
            return walkToward(bankerTile);
        }

        log.debug("[BankHelper] Varrock East: walk anchor dist={}", dAnchor);
        return walkToward(VARROCK_EAST_BANK);
    }

    private static boolean progressLumbridgeStairs(WorldPoint myPos) {
        if (myPos == null || myPos.getPlane() >= 2) {
            return false;
        }
        if (!LumbridgeStairsHelper.canClimbNow(myPos)) {
            return false;
        }
        int plane = myPos.getPlane();
        ITileObject stairs = LumbridgeStairsHelper.pickSouthStairs(myPos, false);
        if (stairs != null) {
            WorldPoint stairTile = stairs.getWorldLocation();
            int d = stairTile != null ? myPos.distanceTo(stairTile) : 99;
            if (d > 6) {
                BotRuntime.logConsole("[Bank] Lumb → courtyard d=" + d);
                return MovementHelper.walkTo(LumbridgeStairsHelper.pathApproach());
            }
            String action = stairs.hasAction("Climb-up") ? "Climb-up"
                    : (stairs.hasAction("Climb Up") ? "Climb Up"
                    : (stairs.hasAction("Top-floor") ? "Top-floor" : null));
            if (action == null) {
                return false;
            }
            log.info("[BankHelper] Lumbridge zuid-trap {} @{} plane={} d={}",
                    action, stairTile, plane, d);
            BotRuntime.logConsole("[Bank] Lumb " + action + " p" + plane + " d=" + d + " (invoke)");
            net.runelite.api.TileObject raw = TileObjects.unwrap(stairs);
            boolean ok = raw != null && MenuInteract.interactObject(raw, action);
            if (!ok) {
                ok = stairs.interact(action);
            }
            return ok;
        }
        BotRuntime.logConsole("[Bank] Lumb geen stairs-obj → courtyard");
        return MovementHelper.walkTo(LumbridgeStairsHelper.pathApproach());
    }

    public static void clearStickyF2pBank() {
        stickyF2pBank = null;
        stickyF2pBankMs = 0L;
    }

    /**
     * Houdt GE vs Varrock West vast: op de weg ertussen is de andere booth
     * binnen COS 40, nearest flip't, en je ping-pongt.
     */
    private static WorldPoint applyStickyF2pBank(WorldPoint from, WorldPoint pick,
                                                 java.util.function.Predicate<WorldPoint> skip) {
        long now = System.currentTimeMillis();
        WorldPoint sticky = stickyF2pBank;
        if (sticky != null) {
            boolean stale = now - stickyF2pBankMs > STICKY_BANK_TTL_MS;
            boolean skipped = false;
            try {
                skipped = skip != null && skip.test(sticky);
            } catch (Throwable ignored) {
            }
            if (stale || skipped || isInExcludedBankArea(sticky)) {
                stickyF2pBank = null;
                sticky = null;
            }
        }
        if (pick == null) {
            return sticky;
        }
        if (sticky == null) {
            stickyF2pBank = pick;
            stickyF2pBankMs = now;
            return pick;
        }
        if (sameF2pBank(sticky, pick)) {
            stickyF2pBankMs = now;
            return sticky;
        }
        int stickyScore = bankTravelScore(from, sticky);
        int pickScore = bankTravelScore(from, pick);
        if (pickScore + STICKY_SWITCH_MARGIN < stickyScore) {
            BotRuntime.logConsole("[Bank] sticky "
                    + fmtWp(sticky) + " → " + fmtWp(pick)
                    + " score " + stickyScore + "→" + pickScore);
            stickyF2pBank = pick;
            stickyF2pBankMs = now;
            return pick;
        }
        return sticky;
    }

    /** Booth/banker hoort bij de gekozen F2P-ankerbank (niet de buur 50 tiles verder). */
    private static boolean boothBelongsToChosenBank(WorldPoint myPos, WorldPoint tile) {
        if (tile == null) {
            return false;
        }
        if (isInExcludedBankArea(tile)) {
            return false;
        }
        WorldPoint chosen = getNearestF2pBankPoint(myPos);
        if (chosen == null) {
            return true;
        }
        WorldPoint boothsBank = closestF2pAnchor(tile);
        if (boothsBank == null) {
            return true;
        }
        return sameF2pBank(boothsBank, chosen);
    }

    private static WorldPoint closestF2pAnchor(WorldPoint tile) {
        if (tile == null) {
            return null;
        }
        WorldPoint best = null;
        int bestD = Integer.MAX_VALUE;
        for (WorldPoint w : F2P_BANK_POINTS) {
            if (w == null || isInExcludedBankArea(w) || w.getPlane() != tile.getPlane()) {
                continue;
            }
            int d = tile.distanceTo(w);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    private static boolean sameF2pBank(WorldPoint a, WorldPoint b) {
        return a != null && b != null && a.getPlane() == b.getPlane() && a.distanceTo(b) <= 8;
    }

    private static void logSkipOtherBank(String kind, WorldPoint tile, int dist, WorldPoint myPos) {
        WorldPoint chosen = getNearestF2pBankPoint(myPos);
        String msg = "skip " + kind + " d=" + dist + " @" + fmtWp(tile)
                + " — blijf " + fmtWp(chosen);
        long now = System.currentTimeMillis();
        if (msg.equals(lastNearestBankLog) && now - lastNearestBankLogMs < 1600L) {
            return;
        }
        lastNearestBankLog = msg;
        lastNearestBankLogMs = now;
        BotRuntime.logConsole("[Bank] " + msg);
    }

    private static ITileObject findNearestFullBankObject() {
        return TileObjects.getNearest(BankHelper::isFullBankTileObject);
    }

    private static INPC findNearestBankerNpc() {
        return NPCs.getNearest(BankHelper::isBankerNpc);
    }

    private static ITileObject findFullBankObjectNear(WorldPoint anchor, int radius) {
        if (anchor == null) {
            return null;
        }
        return TileObjects.getNearest(obj ->
                isFullBankTileObject(obj)
                        && obj.getWorldLocation() != null
                        && obj.getWorldLocation().distanceTo(anchor) <= radius);
    }

    private static INPC findBankerNear(WorldPoint anchor, int radius) {
        if (anchor == null) {
            return null;
        }
        return NPCs.getNearest(npc ->
                isBankerNpc(npc)
                        && npc.getWorldLocation() != null
                        && npc.getWorldLocation().distanceTo(anchor) <= radius);
    }

    private static boolean isFullBankTileObject(ITileObject obj) {
        if (obj == null || obj.getName() == null) {
            return false;
        }
        if (isInExcludedBankArea(obj.getWorldLocation())) {
            return false;
        }
        String name = obj.getName().toLowerCase(Locale.ROOT);
        if (name.contains("deposit")) {
            return false;
        }
        boolean bankName = name.contains("bank booth")
                || name.contains("bank chest")
                || name.contains("bank counter")
                || name.equals("bank")
                || name.contains("bank");
        if (!bankName) {
            return false;
        }
        return obj.hasAction("Bank") || obj.hasAction("Use");
    }

    private static boolean isBankerNpc(INPC npc) {
        if (npc == null || npc.getName() == null) {
            return false;
        }
        if (isInExcludedBankArea(npc.getWorldLocation())) {
            return false;
        }
        String name = npc.getName().toLowerCase(Locale.ROOT);
        return name.contains("bank") && npc.hasAction("Bank");
    }

    private static boolean clickBankObject(ITileObject booth) {
        if (booth == null) {
            return false;
        }
        HumanBanking.pauseBeforeBankOpenClick();
        String action = booth.hasAction("Bank") ? "Bank" : "Use";
        WorldPoint wp = booth.getWorldLocation();
        boolean onScreen = ClickOnSight.onScreen(booth);

        if (wp != null && !onScreen) {
            WalkCamera.ensureLookingAt(wp);
        }

        // Off-screen: alleen menu-invoke — geen left-click (Lumb trap/stairs in beeld)
        if (!onScreen) {
            if (tryInvokeBankObject(booth)) {
                return true;
            }
            return false;
        }

        WalkCamera.ensureLookingAt(wp);
        if (tryInvokeBankObject(booth)) {
            return true;
        }
        if (booth.interact(action)) {
            return true;
        }
        Point p = canvasForTileObject(booth);
        return p != null && MouseManager.interactAt(p);
    }

    private static boolean clickBanker(INPC banker) {
        if (banker == null) {
            return false;
        }
        HumanBanking.pauseBeforeBankOpenClick();
        WorldPoint wp = banker.getWorldLocation();
        boolean onScreen = AimInteractHelper.isNpcOnScreen(banker);
        if (wp != null && !onScreen) {
            WalkCamera.ensureLookingAt(wp);
        }
        if (MenuInteract.interactNpcByIndex(banker.getIndex(), "Bank")) {
            return true;
        }
        if (!onScreen) {
            return false;
        }
        if (banker.hasAction("Bank") && banker.interact("Bank")) {
            return true;
        }
        Point p = ClickPoints.forNpc(banker);
        return p != null && MouseManager.interactAt(p);
    }

    private static Point canvasForTileObject(ITileObject obj) {
        if (obj == null || obj.getWorldLocation() == null) {
            return null;
        }
        WorldPoint wp = obj.getWorldLocation();
        return Static.callOnClientThread(() -> {
            net.runelite.api.Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            net.runelite.api.coords.LocalPoint lp =
                    net.runelite.api.coords.LocalPoint.fromWorld(c, wp);
            if (lp == null) {
                return null;
            }
            return net.runelite.api.Perspective.localToCanvas(c, lp, c.getPlane());
        }, null);
    }

    private static boolean walkToward(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        if (isInExcludedBankArea(dest)) {
            BotRuntime.logConsole("[Bank] geen walk naar uitgesloten bank "
                    + dest.getX() + "," + dest.getY());
            return false;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint from = me != null && me.present ? me.worldLocation : null;
        if (Bank.isOpen() || BankOpenZones.contains(from)) {
            MovementHelper.clearPath();
            return true;
        }
        WorldPoint curDest = Movement.getDestination();
        // Alleen skip als speler echt loopt — stale flag na chop blokkeerde bank-walk
        if (curDest != null && from != null && me != null && me.moving
                && curDest.distanceTo(dest) < from.distanceTo(dest)) {
            return true;
        }
        if (MovementHelper.walkTo(dest)) {
            return true;
        }
        return false;
    }
}
