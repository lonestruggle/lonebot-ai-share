package com.lonebot.example.woodcutter;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Production;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CombatBot firemaking/bonfire flow: qty gate, useOn fire, retry, Forester's campfire,
 * can't-light relocate, finish delay. Status string for loop UI.
 */
final class BonfireHandler {

    private static final WorldPoint COOKING_ONLY_FIRE = new WorldPoint(3096, 3237, 0);
    private static final int MAX_BONFIRE_FAILS = 3;
    private static final long BONFIRE_NO_DECREASE_RETRY_MS = 2_000L;
    private static final long BONFIRE_IDLE_STALL_MS = 5_000L;
    private static final long BONFIRE_FIRE_CLICK_LOCK_MS = 4_000L;
    private static final long BONFIRE_QTY_SETTLE_MS = 700L;
    private static final int MAX_FEED_ATTEMPTS = 2;
    private static final long FEED_RETRY_MS = 8_000L;
    private static final long FEED_STUCK_MS = 10_000L;
    private static final int FORESTRY_DETECT_RADIUS = 12;
    private static final int FORESTRY_TEND_SEARCH_RADIUS = 20;
    private static final int FORESTRY_TEND_MISS_BEFORE_RELOCATE = 4;
    private static final int FORESTRY_MOVE_AWAY_TILES = 8;
    /** Hover-capture: Forester's Campfire oid=49927 */
    private static final int FORESTER_CAMPFIRE_OBJECT_ID = 49927;

    private WorldPoint ownFireLocation;
    private boolean bonfireInProgress;
    private boolean bonfireAwaitingQtyConfirm;
    private long bonfireFireClickTime;
    private int lastBonfireLogCount = -1;
    private long lastBonfireLogChangeTime;
    private int bonfireFailCount;
    private int cantLightHereCount;
    private boolean firemakingMoveTile;
    private boolean fireBurnedOutRestartPending;
    private boolean foresterCampfireTendPreferred;
    private int foresterTendMissStreak;
    private int firemakingTransitionDelay;
    private boolean firemakingFailed;
    /** CombatBot: na tinder.useOn wacht 3.5–5s vóór opnieuw lighten (LoopHost max-sleep=1.2s). */
    private long lastFireAttemptMs;
    /** Multi-methode light alleen voor Test-tab ({@link FmLightHelper}). */
    private FmLightHelper.Pending lightPending = FmLightHelper.Pending.NONE;
    private long lightPendingAtMs;
    private int lightMethodAttempt;
    private long expectingFireUntilMs;
    /** Eén log.useOn(vuur) per vuur — extra klikken = spam (laatste log / Forester-tend). */
    private int feedAttemptsThisFire;
    private String status = "fm idle";

    void reset() {
        ownFireLocation = null;
        bonfireInProgress = false;
        bonfireAwaitingQtyConfirm = false;
        bonfireFireClickTime = 0L;
        resetBonfireStall();
        bonfireFailCount = 0;
        cantLightHereCount = 0;
        firemakingMoveTile = false;
        fireBurnedOutRestartPending = false;
        foresterCampfireTendPreferred = false;
        foresterTendMissStreak = 0;
        firemakingTransitionDelay = 0;
        firemakingFailed = false;
        lastFireAttemptMs = 0L;
        lightPending = FmLightHelper.Pending.NONE;
        lightPendingAtMs = 0L;
        lightMethodAttempt = 0;
        expectingFireUntilMs = 0L;
        feedAttemptsThisFire = 0;
        status = "fm idle";
    }

    String status() {
        return status;
    }

    boolean isFiremakingFailed() {
        return firemakingFailed;
    }

    void armTransitionDelay() {
        // Geen lange chop→fm wacht — LoopHost + AntiBan maakte 3–8 ticks tot 20s+ hang.
        // Test-tab bewees: tinder.useOn(log) werkt meteen.
        firemakingTransitionDelay = 0;
    }

    void onGameMessage(String message) {
        if (message == null) {
            return;
        }
        String m = message.trim();
        String lower = m.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        boolean forestryNear = lower.contains("forester's campfire")
                || lower.contains("foresters campfire")
                || lower.contains("help tend to that one or move further away")
                || (lower.contains("forester") && lower.contains("campfire") && lower.contains("nearby"));
        boolean vanillaCantLight = lower.contains("can't light a fire") || lower.contains("cannot light a fire")
                || lower.contains("you can't light") || lower.contains("unable to light a fire");
        if (forestryNear || vanillaCantLight) {
            ownFireLocation = null;
            bonfireInProgress = false;
            resetBonfireStall();
            firemakingMoveTile = false;
            foresterTendMissStreak = 0;
            if (forestryNear) {
                foresterCampfireTendPreferred = true;
                status = "tend Forester campfire";
            }
            if (vanillaCantLight && !forestryNear) {
                cantLightHereCount = 1;
                firemakingMoveTile = true;
                status = "can't light — andere tegel";
            }
            WcDebug.once("fm", "vuur geweigerd: " + m);
            return;
        }
        if (lower.contains("you finish adding the logs to the fire")) {
            applyFireBurnedOut(bestBurnableLog() != null, "bonfire batch klaar");
            return;
        }
        if (lower.contains("fire has burned out") || lower.contains("the fire goes out")) {
            applyFireBurnedOut(bestBurnableLog() != null, "chat: fire burned out");
        }
    }

    static int countAllLogs() {
        int n = 0;
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && (WcTrees.isLogId(i.getId()) || WcTrees.isLogName(i.getName()))) {
                n += Math.max(1, i.getQuantity());
            }
        }
        return n;
    }

    static String bestBurnableLog() {
        int fm = WcTrees.fmLevel();
        String best = null;
        int bestReq = -1;
        for (IInventoryItem i : Inventory.getAll()) {
            if (i == null) {
                continue;
            }
            boolean log = WcTrees.isLogId(i.getId()) || WcTrees.isLogName(i.getName());
            if (!log) {
                continue;
            }
            int req = WcTrees.isLogId(i.getId())
                    ? WcTrees.requiredFmForLogId(i.getId())
                    : WcTrees.requiredFmForLog(i.getName());
            if (fm >= req && req >= bestReq) {
                bestReq = req;
                best = i.getName();
                if (best == null || best.isEmpty()) {
                    best = nameForLogId(i.getId());
                }
            }
        }
        return best;
    }

    private static String nameForLogId(int id) {
        switch (id) {
            case 1511:
                return "Logs";
            case 1521:
                return "Oak logs";
            case 1519:
                return "Willow logs";
            case 6333:
                return "Teak logs";
            case 1517:
                return "Maple logs";
            case 6332:
                return "Mahogany logs";
            case 1515:
                return "Yew logs";
            case 1513:
                return "Magic logs";
            case 19669:
                return "Redwood logs";
            default:
                return "Logs";
        }
    }

    static boolean hasAnyLogs() {
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && (WcTrees.isLogId(i.getId()) || WcTrees.isLogName(i.getName()))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasAnyBurnableLogs() {
        return bestBurnableLog() != null;
    }

    /**
     * CombatBot {@code handleFiremaking} — zelfde beslisboom.
     *
     * @return delay ms; {@code 0} = stop burning (caller clears burning flag)
     */
    int tick(String logName) {
        status = "fm tick";
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            status = "geen speler";
            return 1000;
        }
        long now = System.currentTimeMillis();

        // Oude sessies: skip resterende chop→fm wacht, direct lighten
        if (firemakingTransitionDelay > 0) {
            WcDebug.log("fm", "skip chop→fm delay (" + firemakingTransitionDelay + "ms) → light");
            firemakingTransitionDelay = 0;
        }

        int totalLogs = countAllLogs();
        if (!hasAnyBurnableLogs() || totalLogs == 0) {
            firemakingTransitionDelay = 0;
            fireBurnedOutRestartPending = false;
            return finishBonfire("Geen logs meer — terug naar WC");
        }

        if (pollFireBurnedOut(me, totalLogs, now)) {
            fireBurnedOutRestartPending = true;
        }
        if (fireBurnedOutRestartPending) {
            bonfireInProgress = false;
            bonfireAwaitingQtyConfirm = false;
            bonfireFireClickTime = 0L;
            ownFireLocation = null;
            fireBurnedOutRestartPending = false;
            status = "vuur gedoofd — herstart";
            WcDebug.log("fm", status);
        }

        if (logName == null) {
            logName = bestBurnableLog();
        }
        if (logName == null) {
            return finishBonfire("Geen brandbare logs");
        }

        if (cantLightHereCount > 0) {
            cantLightHereCount = 0;
            firemakingMoveTile = false;
            moveRandomly(me);
            status = "andere tegel (can't light)";
            return Rand.nextInt(1200, 2000);
        }

        int qtyGate = gateBonfireQtyDialog(totalLogs, now);
        if (qtyGate > 0) {
            return qtyGate;
        }
        clearBonfireQtyWaitIfBurning(me, totalLogs);

        // CombatBot stap 3: al bezig met bonfire → geen extra log.useOn(vuur)
        if (alreadyAddingLogsToFire(me, now, totalLogs)) {
            noteBonfireLogSnapshot(totalLogs);
            bonfireInProgress = true;
            if (feedAttemptsThisFire >= MAX_FEED_ATTEMPTS
                    && totalLogs > 0
                    && !isPlayerBonfireAnimating(me)
                    && lastBonfireLogChangeTime > 0L
                    && now - lastBonfireLogChangeTime >= FEED_STUCK_MS) {
                return finishBonfire("log plakt — verder WC");
            }
            status = "branden…";
            return Rand.nextInt(600, 1200);
        }

        int foresterTick = tryForesterCampfireFiremaking(me, logName, totalLogs, now);
        if (foresterTick > 0) {
            return foresterTick;
        }

        if (foresterCampfireTendPreferred || isForesterCampfireNearby(me.worldLocation, FORESTRY_DETECT_RADIUS)) {
            if (me.moving || isPlayerBonfireAnimating(me)) {
                ownFireLocation = null;
                bonfireInProgress = true;
                status = me.moving ? "lopen…" : "branden…";
                return Rand.nextInt(350, 700);
            }
        }

        if (mustConfirmBonfireQtyOnly(now)) {
            int qtyOnly = gateBonfireQtyDialog(totalLogs, now);
            if (qtyOnly > 0) {
                return qtyOnly;
            }
            // Gate 0 + stuck awaiting → lock alleen wissen als we NIET al branden
            if (!BonfireQtyHelper.blocksLogUseOnFire()) {
                if (alreadyAddingLogsToFire(me, now, totalLogs)) {
                    status = "branden…";
                    return Rand.nextInt(600, 1200);
                }
                bonfireAwaitingQtyConfirm = false;
                bonfireFireClickTime = 0L;
            } else {
                status = "qty gate stuck";
                return Rand.nextInt(500, 900);
            }
        }

        int qtyConfirm = tryConfirmBonfireQuantityDialog();
        if (qtyConfirm > 0) {
            bonfireInProgress = true;
            noteBonfireLogSnapshot(totalLogs);
            return qtyConfirm;
        }

        // CombatBot: Production altijd als open (niet alleen met ownFireLocation)
        if (Production.isOpen()) {
            int qtyBeforeProd = tryConfirmBonfireQuantityDialog();
            if (qtyBeforeProd > 0) {
                bonfireInProgress = true;
                noteBonfireLogSnapshot(totalLogs);
                return qtyBeforeProd;
            }
            if (Production.chooseOption(logName)) {
                status = "production " + logName;
            } else {
                Production.continueSpace();
                status = "production SPACE " + logName;
            }
            bonfireFailCount = 0;
            bonfireInProgress = true;
            noteBonfireLogSnapshot(totalLogs);
            return Rand.nextInt(1200, 2000);
        }

        if (bonfireFireClickTime > 0L && now - bonfireFireClickTime < BONFIRE_FIRE_CLICK_LOCK_MS) {
            qtyConfirm = tryConfirmBonfireQuantityDialog();
            if (qtyConfirm > 0) {
                bonfireInProgress = true;
                noteBonfireLogSnapshot(totalLogs);
                return qtyConfirm;
            }
            status = "wacht qty-scherm";
            return Rand.nextInt(450, 850);
        }
        if (!bonfireAwaitingQtyConfirm) {
            bonfireFireClickTime = 0L;
        }

        boolean retryUseOn = false;
        boolean fireStillThere = hasActiveOwnFireNearAnchor(me)
                || isForesterCampfireNearby(me.worldLocation, FORESTRY_DETECT_RADIUS);
        if ((bonfireInProgress || isPlayerBonfireAnimating(me)) && fireStillThere) {
            noteBonfireLogSnapshot(totalLogs);
            if (!hasAnyBurnableLogs() || totalLogs == 0) {
                return finishBonfire("Alle logs verbrand");
            }
            if (shouldRetryBonfireUseOn(me, totalLogs, now)) {
                if (!mayUseLogOnFire(now)) {
                    qtyGate = gateBonfireQtyDialog(totalLogs, now);
                    if (qtyGate > 0) {
                        return qtyGate;
                    }
                    if (!BonfireQtyHelper.blocksLogUseOnFire()) {
                        if (alreadyAddingLogsToFire(me, now, totalLogs)) {
                            status = "branden…";
                            return Rand.nextInt(600, 1200);
                        }
                        bonfireAwaitingQtyConfirm = false;
                        bonfireFireClickTime = 0L;
                    } else {
                        status = "qty blokkeert useOn";
                        return Rand.nextInt(500, 900);
                    }
                }
                retryUseOn = true;
                status = "bonfire retry useOn";
            } else {
                long since = bonfireMsSinceLastLogChange(now);
                boolean animating = isPlayerBonfireAnimating(me);
                if (animating || since < BONFIRE_NO_DECREASE_RETRY_MS) {
                    status = animating ? "branden…" : "wacht daling…";
                    return Rand.nextInt(600, 1200);
                }
                if (!animating && since >= BONFIRE_IDLE_STALL_MS && feedAttemptsThisFire == 0) {
                    WcDebug.log("fm", "idle stall " + since + "ms");
                    applyFireBurnedOut(true, "idle stall");
                    retryUseOn = true;
                } else {
                    status = "branden…";
                    return Rand.nextInt(800, 1200);
                }
            }
        } else if (bonfireInProgress || isPlayerBonfireAnimating(me)) {
            WcDebug.log("fm", "anim zonder vuur → herstart");
            applyFireBurnedOut(true, "anim zonder vuur");
            retryUseOn = true;
        }

        // CombatBot: geen light terwijl moving (tenzij retry)
        if (me.moving && !retryUseOn) {
            status = "lopen…";
            return Rand.nextInt(500, 900);
        }

        IInventoryItem logItem = Inventory.getFirst(logName);
        if (logItem == null) {
            return finishBonfire("Log item niet gevonden");
        }

        if (ownFireLocation != null && !foresterCampfireTendPreferred) {
            ITileObject ourFire = findOwnFireNear(ownFireLocation, 1);
            if (ourFire == null) {
                ourFire = findOwnFireNear(ownFireLocation, 2);
            }
            if (ourFire != null) {
                WorldPoint fireWp = ourFire.getWorldLocation();
                int distToFire = fireWp != null ? me.worldLocation.distanceTo(fireWp) : 99;
                if (distToFire > 1 && fireWp != null) {
                    Movement.walk(fireWp);
                    status = "→ eigen vuur (" + distToFire + ")";
                    return Rand.nextInt(800, 1400);
                }
                if (!mayUseLogOnFire(now)) {
                    qtyGate = gateBonfireQtyDialog(totalLogs, now);
                    if (qtyGate > 0) {
                        return qtyGate;
                    }
                    if (!BonfireQtyHelper.blocksLogUseOnFire()) {
                        if (alreadyAddingLogsToFire(me, now, totalLogs)) {
                            status = "branden…";
                            return Rand.nextInt(600, 1200);
                        }
                        bonfireAwaitingQtyConfirm = false;
                        bonfireFireClickTime = 0L;
                    } else {
                        status = "qty blokkeert feed";
                        return Rand.nextInt(500, 900);
                    }
                }
                logItem.useOn(ourFire);
                if (fireWp != null) {
                    ownFireLocation = fireWp;
                }
                noteFeedAttempt(totalLogs, now);
                bonfireFailCount = 0;
                firemakingMoveTile = false;
                bonfireAwaitingQtyConfirm = true;
                bonfireFireClickTime = now;
                lastFireAttemptMs = 0L;
                status = "useOn eigen vuur " + logName;
                WcDebug.log("fm", status + " attempt=" + feedAttemptsThisFire);
                return Rand.nextInt(1800, 2500);
            }
            // CombatBot wacht 3.5–5s na light — LoopHost slaapt max 1.2s, dus hier vasthouden
            long sinceLight = lastFireAttemptMs > 0L ? now - lastFireAttemptMs : 9_999L;
            if (sinceLight < 3_500L) {
                status = "wacht op vuur (" + (sinceLight / 100) / 10.0 + "s)…";
                return Rand.nextInt(500, 900);
            }
            if (sinceLight < 5_000L) {
                status = "wacht op vuur…";
                return Rand.nextInt(500, 900);
            }
            WcDebug.log("fm", "ownFireLocation maar geen object na " + sinceLight + "ms → opnieuw");
            ownFireLocation = null;
        }

        // Light-cooldown (CombatBot 3500–5000 return; hier intern)
        if (lastFireAttemptMs > 0L && now - lastFireAttemptMs < 3_500L) {
            status = "light cooldown…";
            return Rand.nextInt(500, 900);
        }

        bonfireFailCount++;
        if (bonfireFailCount >= MAX_BONFIRE_FAILS || ownFireLocation == null) {
            if (isForesterCampfireNearby(me.worldLocation, FORESTRY_DETECT_RADIUS)) {
                foresterCampfireTendPreferred = true;
                int forestryTick = tryForesterCampfireFiremaking(me, logName, totalLogs, now);
                if (forestryTick > 0) {
                    return forestryTick;
                }
            }
            IInventoryItem tinderbox = Inventory.getFirst("Tinderbox");
            if (tinderbox != null) {
                if (COOKING_ONLY_FIRE.equals(me.worldLocation)) {
                    moveRandomly(me);
                    status = "cooking-tile vermeden";
                    return Rand.nextInt(1000, 1600);
                }
                if (firemakingMoveTile) {
                    firemakingMoveTile = false;
                    moveRandomly(me);
                    status = "verplaatsen voor nieuw vuur";
                    return Rand.nextInt(1200, 2000);
                }
                return lightNewFireCombatBot(me, tinderbox, logItem, logName);
            }
            firemakingFailed = true;
            bonfireInProgress = false;
            status = "geen tinderbox — FM uit";
            WcDebug.once("fm", status);
            return 0;
        }

        status = "wacht op vuur (" + bonfireFailCount + "/" + MAX_BONFIRE_FAILS + ")";
        return Rand.nextInt(1000, 1800);
    }

    /**
     * CombatBot light: {@code tinderbox.useOn(log)}, anker = spelertegel, wacht 3.5–5s.
     */
    private int lightNewFireCombatBot(Players.LocalSnap me, IInventoryItem tinder,
                                      IInventoryItem log, String logName) {
        if (tinder == null || log == null || me == null || me.worldLocation == null) {
            return 0;
        }
        FmLightHelper.ensureInventoryTab();
        ownFireLocation = me.worldLocation;
        boolean ok = tinder.useOn(log);
        feedAttemptsThisFire = 0;
        bonfireFailCount = 0;
        firemakingMoveTile = true;
        lightPending = FmLightHelper.Pending.NONE;
        expectingFireUntilMs = 0L;
        lastFireAttemptMs = System.currentTimeMillis();
        status = "nieuw vuur " + (logName != null ? logName : log.getName())
                + (ok ? "" : " (useOn=false)");
        WcDebug.log("fm", status + " @" + me.worldLocation.getX() + "," + me.worldLocation.getY()
                + " | " + FmLightHelper.diagnose());
        // LoopHost MAX_SLEEP=1200 — echte 3.5–5s via lastFireAttemptMs
        return Rand.nextInt(900, 1200);
    }

    private int finishBonfire(String reason) {
        bonfireInProgress = false;
        bonfireAwaitingQtyConfirm = false;
        fireBurnedOutRestartPending = false;
        ownFireLocation = null;
        expectingFireUntilMs = 0L;
        foresterCampfireTendPreferred = false;
        foresterTendMissStreak = 0;
        bonfireFailCount = 0;
        resetBonfireStall();
        bonfireFireClickTime = 0L;
        lastFireAttemptMs = 0L;
        feedAttemptsThisFire = 0;
        // CombatBot: 3–8 game ticks (LoopHost capped → korte pauze is ok)
        int tickDelay = Rand.nextInt(1, 3);
        int msDelay = tickDelay * 600;
        status = reason + " — " + tickDelay + " tick pauze";
        WcDebug.log("fm", "finish: " + reason);
        return msDelay;
    }

    private void applyFireBurnedOut(boolean stillHasBurnableLogs, String reason) {
        ownFireLocation = null;
        bonfireInProgress = false;
        bonfireAwaitingQtyConfirm = false;
        bonfireFireClickTime = 0L;
        resetBonfireStall();
        feedAttemptsThisFire = 0;
        if (stillHasBurnableLogs) {
            fireBurnedOutRestartPending = true;
            bonfireFailCount = 0;
            firemakingMoveTile = false;
            status = "vuur uit — herstart (" + reason + ")";
        } else {
            fireBurnedOutRestartPending = false;
            status = "vuur uit — geen logs";
        }
        WcDebug.log("fm", status);
    }

    private boolean pollFireBurnedOut(Players.LocalSnap me, int totalLogs, long now) {
        if (foresterCampfireTendPreferred || isForesterCampfireNearby(me.worldLocation, FORESTRY_DETECT_RADIUS)) {
            return false;
        }
        if (ownFireLocation == null) {
            return false;
        }
        if (hasActiveOwnFireNearAnchor(me)) {
            return false;
        }
        if (BonfireQtyHelper.blocksLogUseOnFire()) {
            return false;
        }
        long since = bonfireMsSinceLastLogChange(now);
        if (isPlayerBonfireAnimating(me) && since < BONFIRE_NO_DECREASE_RETRY_MS) {
            return false;
        }
        applyFireBurnedOut(hasAnyBurnableLogs() && totalLogs > 0, "poll: geen vuur-object");
        return true;
    }

    private boolean hasActiveOwnFireNearAnchor(Players.LocalSnap me) {
        if (ownFireLocation == null || foresterCampfireTendPreferred) {
            return false;
        }
        return findOwnFireNear(ownFireLocation, 2) != null;
    }

    private int tryConfirmBonfireQuantityDialog() {
        boolean force = bonfireAwaitingQtyConfirm || BonfireQtyHelper.isTitleWidgetVisible();
        int delay = BonfireQtyHelper.tryConfirm(force);
        if (delay > 0) {
            lastBonfireLogChangeTime = System.currentTimeMillis();
            bonfireFireClickTime = 0L;
            bonfireInProgress = true;
            status = "qty confirm";
        }
        return delay;
    }

    private boolean mustConfirmBonfireQtyOnly(long now) {
        if (BonfireQtyHelper.isTitleWidgetVisible()) {
            bonfireAwaitingQtyConfirm = true;
            return true;
        }
        if (BonfireQtyHelper.isDialogOpen()) {
            bonfireAwaitingQtyConfirm = true;
            return true;
        }
        if (bonfireAwaitingQtyConfirm) {
            return true;
        }
        return bonfireFireClickTime > 0L && now - bonfireFireClickTime < BONFIRE_FIRE_CLICK_LOCK_MS;
    }

    private boolean mayUseLogOnFire(long now) {
        // CombatBot: title + dialog + awaiting + click-lock
        if (BonfireQtyHelper.isTitleWidgetVisible()) {
            return false;
        }
        if (BonfireQtyHelper.blocksLogUseOnFire()) {
            return false;
        }
        if (bonfireAwaitingQtyConfirm) {
            return false;
        }
        return bonfireFireClickTime <= 0L || now - bonfireFireClickTime >= BONFIRE_FIRE_CLICK_LOCK_MS;
    }

    private int gateBonfireQtyDialog(int totalLogs, long now) {
        if (BonfireQtyHelper.isTitleWidgetVisible()) {
            bonfireAwaitingQtyConfirm = true;
        }
        if (bonfireFireClickTime > 0L && now - bonfireFireClickTime < BONFIRE_QTY_SETTLE_MS) {
            status = "wacht qty settle";
            return Rand.nextInt(450, 750);
        }
        if (!mustConfirmBonfireQtyOnly(now)) {
            return 0;
        }
        int qtyOnly = tryConfirmBonfireQuantityDialog();
        if (qtyOnly > 0) {
            noteBonfireLogSnapshot(totalLogs);
            return qtyOnly;
        }
        if (BonfireQtyHelper.isTitleWidgetVisible() || bonfireAwaitingQtyConfirm) {
            status = "270 open — bevestig";
            return Rand.nextInt(1200, 1800);
        }
        return 0;
    }

    private void clearBonfireQtyWaitIfBurning(Players.LocalSnap me, int totalLogs) {
        if (!bonfireAwaitingQtyConfirm) {
            return;
        }
        if (BonfireQtyHelper.isDialogOpen() || BonfireQtyHelper.isTitleWidgetVisible()) {
            return;
        }
        if (isPlayerBonfireAnimating(me)) {
            bonfireAwaitingQtyConfirm = false;
            return;
        }
        if (lastBonfireLogCount >= 0 && totalLogs < lastBonfireLogCount) {
            bonfireAwaitingQtyConfirm = false;
        }
    }

    private boolean isPlayerBonfireAnimating(Players.LocalSnap me) {
        if (me == null) {
            return false;
        }
        // Centrale API: 733 (light) + 10563–10580 (bonfire/Forester)
        if (net.storm.sdk.game.Animations.isFiremakingRelated(me.animation)) {
            return true;
        }
        // Fallback: animatie-id nog −1 maar client zegt animating tijdens bonfire
        return bonfireInProgress && me.animating && !me.moving && me.animation < 0;
    }

    private void noteBonfireLogSnapshot(int totalLogs) {
        if (lastBonfireLogCount < 0) {
            lastBonfireLogCount = totalLogs;
            lastBonfireLogChangeTime = System.currentTimeMillis();
            return;
        }
        if (totalLogs < lastBonfireLogCount) {
            lastBonfireLogCount = totalLogs;
            lastBonfireLogChangeTime = System.currentTimeMillis();
            bonfireFailCount = 0;
        }
    }

    private long bonfireMsSinceLastLogChange(long now) {
        if (lastBonfireLogChangeTime <= 0L) {
            return 0L;
        }
        return now - lastBonfireLogChangeTime;
    }

    /**
     * CombatBot stap 3 + failsafe: na 1× log op vuur niet opnieuw klikken tot logs op zijn.
     * Eén retry na 8s als de eerste klik niks deed. Daarna wachten (geen Forester-spam).
     */
    private boolean alreadyAddingLogsToFire(Players.LocalSnap me, long now, int totalLogs) {
        if (me == null) {
            return false;
        }
        if (isPlayerBonfireAnimating(me) || (bonfireInProgress && me.animating && !me.moving)) {
            return true;
        }
        if (feedAttemptsThisFire > 0 && totalLogs > 0) {
            boolean noDrop = lastBonfireLogCount >= 0 && totalLogs >= lastBonfireLogCount;
            boolean canRetry = feedAttemptsThisFire < MAX_FEED_ATTEMPTS
                    && noDrop
                    && lastBonfireLogChangeTime > 0L
                    && now - lastBonfireLogChangeTime >= FEED_RETRY_MS
                    && !me.animating;
            return !canRetry;
        }
        if (lastBonfireLogChangeTime <= 0L) {
            return false;
        }
        long since = now - lastBonfireLogChangeTime;
        if (since < BONFIRE_NO_DECREASE_RETRY_MS) {
            return true;
        }
        return bonfireInProgress && !me.moving && since < BONFIRE_IDLE_STALL_MS;
    }

    private void noteFeedAttempt(int totalLogs, long now) {
        feedAttemptsThisFire++;
        lastBonfireLogCount = totalLogs;
        lastBonfireLogChangeTime = now;
        bonfireInProgress = true;
    }

    private boolean shouldRetryBonfireUseOn(Players.LocalSnap me, int totalLogs, long now) {
        if (alreadyAddingLogsToFire(me, now, totalLogs)) {
            return false;
        }
        if (mustConfirmBonfireQtyOnly(now) || BonfireQtyHelper.isDialogOpen()) {
            return false;
        }
        if (!hasAnyBurnableLogs() || totalLogs <= 0) {
            return false;
        }
        if (BonfireQtyHelper.isTitleWidgetVisible()) {
            return false;
        }
        long since = bonfireMsSinceLastLogChange(now);
        if (since < BONFIRE_NO_DECREASE_RETRY_MS) {
            return false;
        }
        if (totalLogs < lastBonfireLogCount) {
            return false;
        }
        return true;
    }

    private int tryForesterCampfireFiremaking(Players.LocalSnap me, String logName, int totalLogs, long now) {
        if (me == null || logName == null) {
            return 0;
        }
        int qtyGate = gateBonfireQtyDialog(totalLogs, now);
        if (qtyGate > 0) {
            return qtyGate;
        }
        if (!mayUseLogOnFire(now)) {
            return 0;
        }
        if (alreadyAddingLogsToFire(me, now, totalLogs) || me.moving) {
            noteBonfireLogSnapshot(totalLogs);
            bonfireInProgress = true;
            status = me.moving ? "lopen…" : "branden…";
            return Rand.nextInt(600, 1200);
        }
        boolean forestryContext = foresterCampfireTendPreferred
                || isForesterCampfireNearby(me.worldLocation, FORESTRY_DETECT_RADIUS);
        if (!forestryContext) {
            return 0;
        }

        foresterCampfireTendPreferred = true;
        ownFireLocation = null;

        IInventoryItem logItem = Inventory.getFirst(logName);
        if (logItem == null) {
            return 0;
        }

        ITileObject foresterFire = findNearestForesterCampfire(me.worldLocation, FORESTRY_TEND_SEARCH_RADIUS);
        if (foresterFire == null) {
            foresterTendMissStreak++;
            if (foresterTendMissStreak >= FORESTRY_TEND_MISS_BEFORE_RELOCATE) {
                moveAwayFromForesterCampfire(me);
                foresterCampfireTendPreferred = false;
                foresterTendMissStreak = 0;
                cantLightHereCount = 1;
                firemakingMoveTile = true;
                status = "forestry: weg → eigen vuur";
            } else {
                status = "forestry: geen campfire (#" + foresterTendMissStreak + ")";
            }
            return Rand.nextInt(900, 1500);
        }

        foresterTendMissStreak = 0;
        WorldPoint fireWp = foresterFire.getWorldLocation();
        int distToFire = fireWp != null ? me.worldLocation.distanceTo(fireWp) : 99;
        if (distToFire > 2 && fireWp != null) {
            Movement.walk(fireWp);
            status = "→ Forester campfire (" + distToFire + ")";
            return Rand.nextInt(800, 1400);
        }
        if (!mayUseLogOnFire(now)) {
            return gateBonfireQtyDialog(totalLogs, now);
        }

        // CombatBot: log.useOn(foresterFire) — daarna niet opnieuw tot logs op zijn
        logItem.useOn(foresterFire);
        noteFeedAttempt(totalLogs, now);
        bonfireFailCount = 0;
        firemakingMoveTile = false;
        bonfireAwaitingQtyConfirm = true;
        bonfireFireClickTime = now;
        status = "tend Forester " + logName;
        WcDebug.log("fm", status + " attempt=" + feedAttemptsThisFire);
        return Rand.nextInt(1800, 2500);
    }

    private void moveAwayFromForesterCampfire(Players.LocalSnap me) {
        if (me == null || me.worldLocation == null) {
            return;
        }
        ITileObject forester = findNearestForesterCampfire(me.worldLocation, FORESTRY_TEND_SEARCH_RADIUS);
        WorldPoint pos = me.worldLocation;
        WorldPoint target;
        if (forester != null && forester.getWorldLocation() != null) {
            WorldPoint f = forester.getWorldLocation();
            int dx = pos.getX() - f.getX();
            int dy = pos.getY() - f.getY();
            if (dx == 0 && dy == 0) {
                dx = 1;
            }
            double len = Math.sqrt((double) dx * dx + (double) dy * dy);
            int stepX = (int) Math.round((dx / len) * FORESTRY_MOVE_AWAY_TILES);
            int stepY = (int) Math.round((dy / len) * FORESTRY_MOVE_AWAY_TILES);
            target = new WorldPoint(pos.getX() + stepX, pos.getY() + stepY, pos.getPlane());
        } else {
            target = new WorldPoint(pos.getX() + FORESTRY_MOVE_AWAY_TILES, pos.getY(), pos.getPlane());
        }
        Movement.walk(target);
    }

    private void moveRandomly(Players.LocalSnap me) {
        if (me == null || me.worldLocation == null) {
            return;
        }
        WorldPoint current = me.worldLocation;
        WorldPoint fallback = null;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 5; attempt++) {
            int dx = rng.nextInt(5) - 2;
            int dy = rng.nextInt(5) - 2;
            if (dx == 0 && dy == 0) {
                dx = 2;
            }
            WorldPoint candidate = new WorldPoint(current.getX() + dx, current.getY() + dy, current.getPlane());
            if (fallback == null) {
                fallback = candidate;
            }
            ITileObject nearObstacle = TileObjects.getNearest(obj ->
                    obj != null && obj.getName() != null
                            && obj.getWorldLocation() != null
                            && obj.getWorldLocation().distanceTo(candidate) <= 2
                            && isObstacleForBonfire(obj.getName().toLowerCase(Locale.ROOT)));
            if (nearObstacle != null) {
                continue;
            }
            Movement.walk(candidate);
            return;
        }
        if (fallback != null) {
            Movement.walk(fallback);
        }
    }

    private static boolean isObstacleForBonfire(String nameLower) {
        return nameLower.contains("wall") || nameLower.contains("door") || nameLower.contains("house")
                || nameLower.contains("building") || nameLower.contains("fence") || nameLower.contains("gate");
    }

    private void resetBonfireStall() {
        lastBonfireLogCount = -1;
        lastBonfireLogChangeTime = 0L;
    }

    private static boolean isFireObject(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("fire") || lower.contains("campfire") || lower.contains("camp fire");
    }

    private static boolean isForesterCampfireObject(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        if (lower.contains("forester's campfire")) {
            return true;
        }
        return (lower.contains("forester") || lower.contains("forestry"))
                && (lower.contains("campfire") || lower.contains("camp fire"));
    }

    private static boolean isOwnBonfireObject(String name) {
        return isFireObject(name) && !isForesterCampfireObject(name);
    }

    private boolean isForesterCampfireNearby(WorldPoint me, int radius) {
        return findNearestForesterCampfire(me, radius) != null;
    }

    private ITileObject findNearestForesterCampfire(WorldPoint me, int maxDist) {
        if (me == null) {
            return null;
        }
        // Alleen object-id — getName() per scene-object op de loop-thread = 80s freeze.
        return TileObjects.getNearest(obj -> {
            if (obj == null || obj.getId() != FORESTER_CAMPFIRE_OBJECT_ID) {
                return false;
            }
            WorldPoint wp = obj.getWorldLocation();
            if (wp == null || COOKING_ONLY_FIRE.equals(wp)) {
                return false;
            }
            return wp.distanceTo(me) <= maxDist;
        });
    }

    private ITileObject findOwnFireNear(WorldPoint anchor, int maxDistFromAnchor) {
        if (anchor == null) {
            return null;
        }
        return TileObjects.getNearest(obj ->
                obj != null
                        && obj.getName() != null
                        && isOwnBonfireObject(obj.getName())
                        && obj.getWorldLocation() != null
                        && !COOKING_ONLY_FIRE.equals(obj.getWorldLocation())
                        && obj.getWorldLocation().distanceTo(anchor) <= maxDistFromAnchor);
    }

    /** LoneBot: log.useOn(fire) — OSRS brandt automatisch verder; Add-logs/Tend als beschikbaar. */
    private void useLogOnFire(IInventoryItem log, ITileObject fire) {
        if (log == null || fire == null) {
            return;
        }
        // Prefer useOn — geen qty-menu nodig, game blijft branden
        if (log.useOn(fire)) {
            return;
        }
        if (fire.hasAction("Add-logs")) {
            fire.interact("Add-logs");
            return;
        }
        if (fire.hasAction("Tend")) {
            fire.interact("Tend");
            return;
        }
        log.interact("Use");
        fire.interact("Use");
    }

    /**
     * CombatBot-volgorde: Forester's Campfire (oid=49927) tenden → eigen vuur → pas light.
     * Nooit lighten naast Forester (chat spam: "help tend… or move further away").
     */
    int tryDirectLightProven(String logName) {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 0;
        }
        long now = System.currentTimeMillis();

        // Qty-scherm open (ook zonder vuur-object in scene) — CombatBot: altijd eerst bevestigen
        if (BonfireQtyHelper.isTitleWidgetVisible() || BonfireQtyHelper.isDialogOpen()
                || (bonfireAwaitingQtyConfirm && bonfireFireClickTime > 0L
                && now - bonfireFireClickTime < BONFIRE_FIRE_CLICK_LOCK_MS)) {
            int qtyDelay = BonfireQtyHelper.tryConfirm(true);
            bonfireInProgress = true;
            if (qtyDelay > 0) {
                bonfireAwaitingQtyConfirm = false;
                bonfireFireClickTime = 0L;
                status = "qty bevestig";
                WcDebug.log("fm", status);
                return qtyDelay;
            }
            status = "qty wacht…";
            return Rand.nextInt(400, 700);
        }

        if (lightPending != FmLightHelper.Pending.NONE) {
            if (now - lightPendingAtMs > 2500L) {
                lightPending = FmLightHelper.Pending.NONE;
                lightMethodAttempt++;
            } else {
                IInventoryItem pendingLog = logName != null ? Inventory.getFirst(logName) : null;
                if (pendingLog == null) {
                    pendingLog = FmLightHelper.firstAnyLog();
                }
                return completePendingLight(me, Inventory.getFirst("Tinderbox"), pendingLog);
            }
        }

        IInventoryItem logItem = logName != null ? Inventory.getFirst(logName) : FmLightHelper.firstAnyLog();
        if (logItem == null) {
            logItem = Inventory.getFirst(WcTrees.LOG_IDS);
        }
        if (logItem == null || !hasAnyBurnableLogs()) {
            return 0;
        }

        // 1) Forester alleen als écht dichtbij (≤8) — anders bleef bot zoeken/pathfinden
        ITileObject forester = findNearestForesterCampfire(me.worldLocation, 8);
        if (forester != null) {
            foresterCampfireTendPreferred = true;
            foresterTendMissStreak = 0;
            expectingFireUntilMs = 0L;
            ownFireLocation = null;
            return feedLogsOnFireAuto(me, logItem, forester, logName, now);
        }
        if (foresterCampfireTendPreferred) {
            // Geen dichtbij campfire → flag clear, light (geen moveAway/GlobalPathfinder)
            foresterCampfireTendPreferred = false;
            foresterTendMissStreak = 0;
            WcDebug.log("fm", "forestry flag clear → light");
        }

        // 2) Eigen vuur naast je (≤1) — niet ≤4: andermans vuur blokkeerde light
        ITileObject nearFire = findOwnFireNear(me.worldLocation, 1);
        if (nearFire == null && ownFireLocation != null) {
            nearFire = findOwnFireNear(ownFireLocation, 1);
        }
        if (nearFire != null) {
            expectingFireUntilMs = 0L;
            WorldPoint fireWp = nearFire.getWorldLocation();
            if (fireWp != null) {
                ownFireLocation = fireWp;
            }
            return feedLogsOnFireAuto(me, logItem, nearFire, logName, now);
        }

        if (expectingFireUntilMs > now) {
            status = "wacht op vuur-object…";
            return Rand.nextInt(300, 550);
        }
        if (expectingFireUntilMs > 0L && expectingFireUntilMs <= now) {
            expectingFireUntilMs = 0L;
            ownFireLocation = null;
            WcDebug.log("fm", "expecting-fire timeout → light");
        }

        if (BonfireQtyHelper.isTitleWidgetVisible()) {
            BonfireQtyHelper.tryConfirm(true);
            status = "qty SPACE (zeldzaam)";
            return Rand.nextInt(500, 800);
        }

        if (firemakingMoveTile) {
            firemakingMoveTile = false;
            moveRandomly(me);
            status = "verplaatsen voor nieuw vuur";
            return Rand.nextInt(700, 1200);
        }

        IInventoryItem tinder = Inventory.getFirst("Tinderbox");
        if (tinder == null) {
            tinder = Inventory.getFirst(WcTrees.TINDERBOX_ID);
        }
        if (tinder == null) {
            status = "geen tinderbox";
            return 0;
        }

        // Alleen blokkeren als Forester binnen 3 tegels (can't-light zone)
        ITileObject tooClose = findNearestForesterCampfire(me.worldLocation, 3);
        if (tooClose != null) {
            status = "forester ≤3t — 1 stap weg";
            moveRandomly(me);
            return Rand.nextInt(600, 1000);
        }

        // Proven: altijd methode #1 (tinder.useOn(log)) eerst — geen diagnose() (qty-widgets)
        lightMethodAttempt = 0;
        status = "direct light tinder→log";
        WcDebug.log("fm", status);
        int lit = startLightNewFire(me, tinder, logItem);
        return lit > 0 ? lit : Rand.nextInt(350, 550);
    }

    /**
     * Logs op vuur — CombatBot-parity:
     * qty bevestigen → wacht anim/inv-daling → retry useOn na ~2s als er niks gebeurde.
     * (Oude bug: 1× useOn + nooit qty → leek alsof er geen logs opgingen.)
     */
    private int feedLogsOnFireAuto(Players.LocalSnap me, IInventoryItem logItem, ITileObject fire,
                                   String logName, long now) {
        int totalLogs = countAllLogs();
        if (totalLogs <= 0 || !hasAnyBurnableLogs()) {
            return finishBonfire("Alle logs verbrand");
        }

        noteBonfireLogSnapshot(totalLogs);

        // 1) Qty-scherm eerst (How many would you like to burn?) — CombatBot gate
        if (BonfireQtyHelper.isTitleWidgetVisible() || BonfireQtyHelper.isDialogOpen()
                || bonfireAwaitingQtyConfirm) {
            int qtyDelay = BonfireQtyHelper.tryConfirm(true);
            bonfireInProgress = true;
            if (qtyDelay > 0) {
                bonfireAwaitingQtyConfirm = false;
                bonfireFireClickTime = 0L;
                status = "qty bevestig";
                WcDebug.log("fm", status + " logs=" + totalLogs);
                return qtyDelay;
            }
            // Scherm net geopend na useOn — even settlen
            if (bonfireFireClickTime > 0L && now - bonfireFireClickTime < BONFIRE_QTY_SETTLE_MS) {
                status = "wacht qty settle";
                return Rand.nextInt(200, 400);
            }
            status = "qty wacht…";
            return Rand.nextInt(400, 700);
        }

        // 2) Al aan het branden → niet opnieuw klikken
        if (isPlayerBonfireAnimating(me)) {
            bonfireInProgress = true;
            status = "branden…";
            return Rand.nextInt(400, 700);
        }
        if (bonfireInProgress && lastBonfireLogCount >= 0 && totalLogs < lastBonfireLogCount) {
            status = "branden (inv↓)…";
            return Rand.nextInt(400, 700);
        }

        // 3) Kort na useOn: wacht op anim/daling (CombatBot BONFIRE_NO_DECREASE_RETRY_MS)
        long since = bonfireMsSinceLastLogChange(now);
        if (feedAttemptsThisFire > 0 && since < BONFIRE_NO_DECREASE_RETRY_MS) {
            status = "bonfire wacht…";
            return Rand.nextInt(350, 600);
        }

        // 4) useOn (eerste keer of retry als inv niet daalde — CombatBot shouldRetryBonfireUseOn)
        boolean retry = feedAttemptsThisFire > 0
                && lastBonfireLogCount >= 0
                && totalLogs >= lastBonfireLogCount
                && since >= BONFIRE_NO_DECREASE_RETRY_MS;
        WorldPoint fireWp = fire.getWorldLocation();
        int dist = fireWp != null ? me.worldLocation.distanceTo(fireWp) : 99;
        useLogOnFire(logItem, fire);
        noteFeedAttempt(totalLogs, now);
        // CombatBot: na useOn qty-wacht aanzetten (niet false!)
        bonfireAwaitingQtyConfirm = true;
        bonfireFireClickTime = now;
        bonfireFailCount = 0;
        boolean isForester = isForesterCampfireObject(fire.getName())
                || fire.getId() == FORESTER_CAMPFIRE_OBJECT_ID;
        status = (isForester ? "tend Forester " : "log→vuur ")
                + (logName != null ? logName : logItem.getName())
                + (retry ? " retry" : "")
                + " (" + dist + "t)";
        WcDebug.log("fm", status + " attempt=" + feedAttemptsThisFire);
        return Rand.nextInt(600, 1000);
    }

    /**
     * Probeert tot 10 light-methodes (roteert bij fail). Zie {@link FmLightHelper}.
     */
    private int startLightNewFire(Players.LocalSnap me, IInventoryItem tinder, IInventoryItem log) {
        if (tinder == null || log == null) {
            return 0;
        }
        FmLightHelper.Result r = FmLightHelper.tryMethod(lightMethodAttempt, tinder, log);
        status = "light #" + (Math.floorMod(lightMethodAttempt, FmLightHelper.METHOD_COUNT) + 1)
                + " " + r.method;
        WcDebug.once("fm", status + " ok=" + r.ok + " pending=" + r.pending
                + " tinderSlot=" + tinder.getSlot() + " logSlot=" + log.getSlot());
        if (!r.ok) {
            lightMethodAttempt++;
            return 0;
        }
        if (r.pending != FmLightHelper.Pending.NONE) {
            lightPending = r.pending;
            lightPendingAtMs = System.currentTimeMillis();
            return r.delayMs > 0 ? r.delayMs : Rand.nextInt(220, 420);
        }
        return markLightSuccess(me, log, r);
    }

    private int completePendingLight(Players.LocalSnap me, IInventoryItem tinder, IInventoryItem log) {
        FmLightHelper.Pending was = lightPending;
        lightPending = FmLightHelper.Pending.NONE;
        FmLightHelper.Result r = FmLightHelper.completePending(was, tinder, log);
        status = "light-pending " + r.method;
        WcDebug.log("fm", status + " ok=" + r.ok);
        if (!r.ok) {
            lightMethodAttempt++;
            return Rand.nextInt(400, 700);
        }
        return markLightSuccess(me, log, r);
    }

    private int markLightSuccess(Players.LocalSnap me, IInventoryItem log, FmLightHelper.Result r) {
        // Niet meteen ownFireLocation zetten — dat skipte fast-light terwijl er nog geen vuur was.
        expectingFireUntilMs = System.currentTimeMillis() + 2_800L;
        ownFireLocation = null;
        bonfireFailCount = 0;
        firemakingMoveTile = false;
        bonfireAwaitingQtyConfirm = false;
        status = "nieuw vuur (" + r.method + ")";
        WcDebug.once("fm", status + (me != null && me.worldLocation != null
                ? " @" + me.worldLocation.getX() + "," + me.worldLocation.getY()
                : ""));
        return r.delayMs > 0 ? r.delayMs : Rand.nextInt(1200, 1800);
    }
}
