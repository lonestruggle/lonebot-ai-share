package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Varbits;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.input.MouseSettings;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.utils.AntiBan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Canvas;
import java.awt.Polygon;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Safe walk click pipeline. Channels gated by {@link WalkClickSettings}.
 * Far-canvas: yaw naar lookAt + mid-high pitch (via {@link WalkCamera}), daarna canvas-first.
 */
public final class WalkClickHelper {

    private static final Logger log = LoggerFactory.getLogger(WalkClickHelper.class);

    /** Zelfde als werkende walker (~0.3.111): projectie tot ~70 tegels, geen strenge cirkel. */
    private static final int MINIMAP_DISTANCE = Perspective.LOCAL_TILE_SIZE * 70;
    /** Inset t.o.v. widget-rand (werkende walker ~0.3.111). */
    private static final int MINIMAP_INSET_PX = 28;
    /** Getekende cirkel is kleiner dan de widget-vierkant. */
    private static final double MINIMAP_SAFE_RADIUS = 0.78;
    private static volatile long lastMiniLogMs;
    private static volatile String lastMiniLog = "";
    /** Align with {@link WalkCameraSettings}: mid-high by default, not horizon-low. */
    private static final int FAR_PITCH_MIN = 260;
    private static final int FAR_PITCH_MAX = 340;
    private static volatile long lastCameraNudgeMs;
    private static volatile long lastWalkClickMs;
    private static volatile long lastFarOrientMs;
    /** Stale {@code callOnClientThread} na timeout mag geen late WALK meer vuren. */
    private static final AtomicLong WALK_GEN = new AtomicLong();
    private static volatile boolean lastClientCallTimedOut;
    private static volatile long lastTimeoutLogMs;

    private WalkClickHelper() {
    }

    /**
     * Walk toward {@code dest} using enabled strategies.
     *
     * @return {@code true} if a walk action or camera nudge was started
     */
    public static boolean walkTo(WorldPoint dest) {
        return walkTo(dest, dest);
    }

    /**
     * Click-on-sight: canvas Walk-here op {@code dest} als de tegel zichtbaar is.
     * Geen minimap/invoke — caller hop 5 padtegels verder bij fail.
     */
    public static boolean tryClickOnSight(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        try {
            if (Mouse.isBusy()) {
                return false;
            }
            ClickCandidate cand = resolveCandidates(dest);
            Point aim = cand.canvasSafe != null ? cand.canvasSafe : cand.canvasRaw;
            if (aim == null) {
                return false;
            }
            return walkHereSafeLocked(dest, aim, "canvas-cos", false);
        } catch (RuntimeException e) {
            log.debug("[WalkClick] COS fail: {}", e.toString());
            return false;
        }
    }

    /**
     * @param dest   tile to walk toward (click target)
     * @param lookAt direction for far-canvas camera (often path destination)
     */
    public static boolean walkTo(WorldPoint dest, WorldPoint lookAt) {
        try {
            return walkTo0(dest, lookAt);
        } catch (IllegalStateException e) {
            log.warn("[WalkClick] client-thread: {}", e.toString());
            BotRuntime.logConsole("[Walk] FAIL client-thread " + dest);
            return dest != null && clickMinimapHop(dest);
        } catch (RuntimeException e) {
            log.warn("[WalkClick] walkTo: {}", e.toString());
            BotRuntime.logConsole("[Walk] FAIL exception " + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Travel/WW-hop: alleen minimap Walk-here (zoals de walker die wél liep).
     * Geen canvas (muis over de wereld) en geen invoke (geen gele vlag).
     */
    public static boolean clickMinimapHop(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        if (KaramjaVolcano.isVolcanoRockTile(dest)) {
            dest = KaramjaVolcano.safeSurfaceTile();
        }
        MinimapZoomHelper.ensureFullyZoomedOut();
        try {
            net.storm.sdk.game.WorldMap.dismissIfOpenDuringWalk();
        } catch (Throwable ignored) {
        }
        ClickCandidate cand = resolveCandidates(dest);
        Point mini = cand.minimap;
        if (mini == null) {
            mini = resolveMinimapClick(dest);
        }
        if (mini == null) {
            miniLog("[Walk] geen minimap-punt " + dest.getX() + "," + dest.getY());
            return false;
        }
        AntiBan.get().markBotActivity();
        Mouse.Session lock = Static.isOnClientThread()
                ? Mouse.trySession()
                : Mouse.trySession(400L);
        try {
            // Snap op de minimap — niet over het canvas slepen (dat leek op wereld-klikken).
            if (!Mouse.moveRaw(mini.getX(), mini.getY())) {
                miniLog("[Walk] minimap move fail " + dest.getX() + "," + dest.getY());
                return false;
            }
            boolean ok = Mouse.clickOnly(mini.getX(), mini.getY(), true);
            if (!ok) {
                miniLog("[Walk] minimap click fail " + dest.getX() + "," + dest.getY());
                return false;
            }
            lastWalkClickMs = System.currentTimeMillis();
            setLast("minimap-hop", dest.getX() + "," + dest.getY()
                    + " @" + mini.getX() + "," + mini.getY(), dest);
            WalkClickSettings.noteHuman("minimap", 0, false, "minimap-hop");
            BotRuntime.logConsole("Walk minimap " + dest.getX() + "," + dest.getY()
                    + " @" + mini.getX() + "," + mini.getY());
            afterSuccessfulClick();
            return true;
        } finally {
            if (lock != null) {
                lock.close();
            }
        }
    }

    private static boolean walkTo0(WorldPoint dest, WorldPoint lookAt) {
        if (dest == null) {
            return false;
        }
        if (KaramjaVolcano.isVolcanoRockTile(dest)) {
            WorldPoint safe = KaramjaVolcano.safeSurfaceTile();
            BotRuntime.logConsole("[Walk/volcano] hop was rocks "
                    + dest.getX() + "," + dest.getY() + " → " + safe.getX() + "," + safe.getY());
            dest = safe;
        }
        MinimapZoomHelper.ensureFullyZoomedOut();
        try {
            // Map dicht, daarna gewoon doorlopen (niet hele tick afbreken)
            net.storm.sdk.game.WorldMap.dismissIfOpenDuringWalk();
        } catch (Throwable ignored) {
        }
        try {
            Movement.ensureTravelRun();
        } catch (Throwable ignored) {
        }
        WorldPoint look = lookAt != null ? lookAt : dest;

        WorldPoint from = null;
        try {
            net.storm.sdk.entities.Players.LocalSnap me = net.storm.sdk.entities.Players.snapshotLocal();
            if (me != null && me.present) {
                from = me.worldLocation;
            }
        } catch (Throwable ignored) {
        }
        if (from == null) {
            from = Static.callOnClientThread(() -> {
                Client c = Static.getClient();
                if (c == null || c.getLocalPlayer() == null) {
                    return null;
                }
                return c.getLocalPlayer().getWorldLocation();
            }, null);
        }
        if (from != null && from.getPlane() == dest.getPlane() && from.distanceTo(dest) <= 0) {
            setLast("arrived", dest.toString(), dest);
            return true;
        }

        // Invoke-only: geen minimap/canvas MouseEvents — alleen menu WALK.
        if (net.storm.sdk.input.MouseSettings.forceInvokeOnly()) {
            if (invokeWalk(dest)) {
                setLast("invoke-WALK-only", dest.toString(), dest);
                afterSuccessfulClick();
                BotRuntime.logConsole("Walk INVOKE-ONLY → " + dest.getX() + "," + dest.getY());
                return true;
            }
            setLast("fail", "invoke-only no WALK " + dest, dest);
            BotRuntime.logConsole("[Walk] FAIL invoke-only " + dest.getX() + "," + dest.getY());
            return false;
        }

        boolean travel = false;
        try {
            travel = MovementHelper.isTravelContext();
        } catch (Throwable ignored) {
        }
        if (travel) {
            return clickMinimapHop(dest);
        }
        if (Mouse.isBusy()) {
            setLast("wait-mouse", dest.toString(), dest);
            return clickMinimapHop(dest);
        }

        ClickCandidate cand = resolveCandidates(dest);
        final boolean resolveTimeout = lastClientCallTimedOut;
        if (resolveTimeout) {
            timeoutLog("[Walk] resolve timeout → minimap " + dest.getX() + "," + dest.getY());
            if (clickMinimapHop(dest)) {
                return true;
            }
        }

        // Bank-UI open: canvas-klik = bankslot. Alleen minimap / invoke.
        // Geen camera-nudge: dat raakt de Close-knop / bankslots.
        if (bankBlocksCanvasWalk()) {
            return walkWithBankUiOpen(dest, cand);
        }

        boolean far = WalkClickSettings.useFarCanvasWalk;
        // Far: camera volgt klik/looprichting; classic mag lookAt (pad-eind) gebruiken
        WorldPoint camLook = far ? dest : look;
        boolean skipCam = false;
        try {
            skipCam = MovementHelper.isTravelContext() && WalkClickSettings.useMinimap;
        } catch (Throwable ignored) {
        }
        if (!skipCam) {
            try {
                WalkCamera.prepareForWalk(camLook);
            } catch (RuntimeException ignored) {
            }
        }

        if (far) {
            // Far: eerst echte Walk-here (geen boom/NPC), daarna minimap fallback
            if (WalkClickSettings.canvasForceWalkHere || WalkClickSettings.useInvokeWalk) {
                if (walkHereSafe(dest, cand.canvasSafe, "canvas-walk-here")) {
                    return true;
                }
            }
            if (WalkClickSettings.useCanvas && cand.canvasSafe != null
                    && walkHereSafe(dest, cand.canvasSafe, "canvas-far")) {
                return true;
            }
            if (WalkClickSettings.useMinimap && cand.minimap != null
                    && clickPoint(cand.minimap, "minimap-far", true, dest)) {
                return true;
            }
            cand = resolveCandidates(dest);
            if ((WalkClickSettings.canvasForceWalkHere || WalkClickSettings.useInvokeWalk)
                    && walkHereSafe(dest, cand.canvasSafe, "canvas-walk-here-2")) {
                return true;
            }
            if (WalkClickSettings.useMinimap && cand.minimap != null
                    && clickPoint(cand.minimap, "minimap-far-postcam", true, dest)) {
                return true;
            }
        } else {
            // Classic: minimap → canvas → camera → invoke
            if (WalkClickSettings.useMinimap && cand.minimap != null) {
                if (clickPoint(cand.minimap, "minimap", true, dest)) {
                    return true;
                }
            }
            if (WalkClickSettings.useCanvas && cand.canvasSafe != null) {
                if (walkHereSafe(dest, cand.canvasSafe, "canvas-safe")) {
                    return true;
                }
            }
            if (!resolveTimeout && WalkClickSettings.useCameraNudge && from != null
                    && (cand.canvasRaw != null || cand.minimap == null)) {
                nudgeCameraToward(from, dest);
                cand = resolveCandidates(dest);
                if (WalkClickSettings.useMinimap && cand.minimap != null
                        && clickPoint(cand.minimap, "minimap-postcam", true, dest)) {
                    return true;
                }
                if (WalkClickSettings.useCanvas && cand.canvasSafe != null
                        && walkHereSafe(dest, cand.canvasSafe, "canvas-postcam")) {
                    return true;
                }
            }
        }

        if (WalkClickSettings.useInvokeWalk) {
            if (invokeWalk(dest)) {
                setLast("invoke-WALK", dest.toString(), dest);
                boolean twin = !far && (WalkClickSettings.twinQuickClicks
                        || ThreadLocalRandom.current().nextInt(100) < WalkClickSettings.doubleClickChancePercent);
                if (twin) {
                    sleepMs(MouseSettings.scaleDelay(20 + ThreadLocalRandom.current().nextInt(40)));
                    invokeWalk(dest);
                    WalkClickSettings.noteHuman("invoke", 0, true, "invoke-WALK");
                    setLast(WalkClickSettings.twinQuickClicks ? "invoke-WALK-twin" : "invoke-WALK-dbl",
                            dest.toString(), dest);
                    net.storm.sdk.bot.BotRuntime.logConsole(
                            (WalkClickSettings.twinQuickClicks ? "Walk invoke TWIN → " : "Walk invoke DBL → ")
                                    + dest.getX() + "," + dest.getY());
                }
                afterSuccessfulClick();
                return true;
            }
        }

        if (WalkClickSettings.useCanvas && cand.canvasRaw != null && !WalkClickSettings.useUiZones) {
            if (walkHereSafe(dest, cand.canvasRaw, "canvas-raw")) {
                return true;
            }
        }

        if (invokeWalk(dest)) {
            setLast("invoke-WALK-last", dest.toString(), dest);
            afterSuccessfulClick();
            return true;
        }

        setLast("fail", "no channel for " + dest, dest);
        log.warn("[WalkClick] fail dest={} mini={} canvasSafe={} invokeSetting={} far={}",
                dest, cand.minimap != null, cand.canvasSafe != null,
                WalkClickSettings.useInvokeWalk, far);
        net.storm.sdk.bot.BotRuntime.logConsole(
                "[Walk] FAIL klik " + dest.getX() + "," + dest.getY()
                        + " mini=" + (cand.minimap != null)
                        + " canvas=" + (cand.canvasSafe != null)
                        + " invoke=" + WalkClickSettings.useInvokeWalk);
        return false;
    }

    private static boolean bankBlocksCanvasWalk() {
        try {
            return net.storm.sdk.items.Bank.isBlockingCanvasWalk();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Minimap of walk-invoke — geen canvas zolang de bank open staat. */
    private static boolean walkWithBankUiOpen(WorldPoint dest, ClickCandidate cand) {
        if (WalkClickSettings.useMinimap && cand.minimap != null
                && clickPoint(cand.minimap, "minimap-bank-open", true, dest)) {
            return true;
        }
        cand = resolveCandidates(dest);
        if (WalkClickSettings.useMinimap && cand.minimap != null
                && clickPoint(cand.minimap, "minimap-bank-open-2", true, dest)) {
            return true;
        }
        if (invokeWalk(dest)) {
            setLast("invoke-WALK-bank-open", dest.toString(), dest);
            afterSuccessfulClick();
            net.storm.sdk.bot.BotRuntime.logConsole(
                    "[Walk] bank-open invoke → " + dest.getX() + "," + dest.getY());
            return true;
        }
        setLast("fail", "bank-open geen minimap/invoke", dest);
        log.warn("[WalkClick] bank-open fail dest={} mini={}", dest, cand.minimap != null);
        return false;
    }

    /**
     * Verste padtegel die veilig op canvas projecteert (UI-zones respecteren).
     */
    public static WorldPoint pickFarthestCanvasTile(java.util.List<WorldPoint> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            return null;
        }
        AtomicReference<WorldPoint> best = new AtomicReference<>();
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            for (int i = tiles.size() - 1; i >= 0; i--) {
                WorldPoint wp = tiles.get(i);
                if (wp == null) {
                    continue;
                }
                LocalPoint lp = LocalPoint.fromWorld(c, wp);
                if (lp == null) {
                    continue;
                }
                Point canvas = Perspective.localToCanvas(c, lp, wp.getPlane());
                if (!isOnGameScreen(c, canvas)) {
                    continue;
                }
                if (WalkClickSettings.useUiZones && WalkUiZones.isOverUi(c, canvas)) {
                    Point safe = findSafePolyPoint(c, lp, wp.getPlane());
                    if (safe == null) {
                        continue;
                    }
                }
                best.set(wp);
                break;
            }
            return null;
        }, null);
        return best.get();
    }

    /**
     * Pitch omlaag + yaw richting lookAt. Draait opnieuw als richting genoeg afwijkt.
     * Ja: camera kan (en moet) meedraaien met de looprichting.
     */
    public static boolean orientCameraForFarWalk(WorldPoint from, WorldPoint lookAt) {
        if (from == null || lookAt == null) {
            return false;
        }
        int dx = lookAt.getX() - from.getX();
        int dy = lookAt.getY() - from.getY();
        if (dx == 0 && dy == 0) {
            return false;
        }
        int wantYaw = ((int) (Math.atan2(dx, dy) * 325.949D)) & 2047;

        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            int curYaw = c.getCameraYaw() & 2047;
            int diff = (wantYaw - curYaw) & 2047;
            if (diff > 1024) {
                diff -= 2048;
            }
            long now = System.currentTimeMillis();
            // Skip alleen als al ongeveer goed gericht én recent gezet
            if (Math.abs(diff) < 40 && now - lastFarOrientMs < 400L) {
                return false;
            }
            // Soft rate-limit bij kleine correcties
            if (Math.abs(diff) < 120 && now - lastFarOrientMs < 180L) {
                return false;
            }
            int pitchTarget = WalkCameraSettings.pitchTarget
                    + ThreadLocalRandom.current().nextInt(-15, 16);
            int lo = WalkCameraSettings.pitchMin();
            int hi = WalkCameraSettings.pitchMax();
            pitchTarget = Math.max(lo, Math.min(hi, pitchTarget));
            try {
                c.setCameraYawTarget(wantYaw);
                int curPitch = c.getCameraPitch();
                if (curPitch < lo || curPitch > hi) {
                    c.setCameraPitchTarget(pitchTarget);
                }
                log.info("[WalkClick] far-cam yaw {}→{} (Δ{}) pitch→{} look={}",
                        curYaw, wantYaw, diff, pitchTarget, lookAt);
                net.storm.sdk.bot.BotRuntime.logConsole(
                        "Far-cam yaw " + curYaw + "→" + wantYaw + " Δ" + diff
                                + " → " + lookAt.getX() + "," + lookAt.getY());
                WalkClickSettings.lastCameraDebug = "yaw " + curYaw + "→" + wantYaw + " Δ" + diff;
                return true;
            } catch (Throwable t) {
                log.debug("[WalkClick] far-cam failed: {}", t.toString());
                return false;
            }
        }, false);
        if (Boolean.TRUE.equals(ok)) {
            lastFarOrientMs = System.currentTimeMillis();
            setLast("far-cam", lookAt.toString());
            // Laat client yaw even lerpen vóór canvas-klik
            sleepMs(120 + ThreadLocalRandom.current().nextInt(140));
            return true;
        }
        return false;
    }

    private static void afterSuccessfulClick() {
        int min = WalkClickSettings.postClickDelayMinMs;
        int max = WalkClickSettings.postClickDelayMaxMs;
        if (max <= 0) {
            return;
        }
        int lo = Math.max(0, min);
        int hi = Math.max(lo, max);
        int wait = lo >= hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
        sleepMs(MouseSettings.scaleDelay(wait));
    }

    /**
     * Direct {@link Client#menuAction} Walk-here.
     * RuneLite verwacht <b>scene</b> 0–103, niet local (0–12800) — local “lukt” zonder te lopen.
     */
    public static boolean invokeWalk(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        WorldPoint target = KaramjaVolcano.isVolcanoRockTile(dest)
                ? KaramjaVolcano.safeSurfaceTile()
                : dest;
        final WorldPoint destF = target;
        final long epoch = WALK_GEN.get();
        Boolean ok = clientCall(() -> {
            if (WALK_GEN.get() != epoch) {
                return false;
            }
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, destF);
            if (lp == null) {
                return false;
            }
            int sx = lp.getSceneX();
            int sy = lp.getSceneY();
            if (sx < 0 || sy < 0 || sx > 103 || sy > 103) {
                return false;
            }
            try {
                tryViewportWalk(c, sx, sy);
                c.menuAction(sx, sy, MenuAction.WALK, 0, 0, "Walk here", "");
                log.info("[WalkClick] invoke menuAction WALK scene={},{} → {}", sx, sy, destF);
                lastWalkClickMs = System.currentTimeMillis();
                net.storm.sdk.bot.BotRuntime.logConsole("Walk invoke scene=" + sx + "," + sy
                        + " → " + destF.getX() + "," + destF.getY());
                return true;
            } catch (Throwable t) {
                log.warn("[WalkClick] menuAction WALK failed: {}", t.toString());
                return false;
            }
        }, false);
        if (lastClientCallTimedOut) {
            timeoutLog("[Walk] invoke timeout — client-thread druk");
            return false;
        }
        return Boolean.TRUE.equals(ok);
    }

    /** True als de laatste {@link #clientCall} de 1,5s-wacht overschreed. */
    public static boolean lastClientCallTimedOut() {
        return lastClientCallTimedOut;
    }

    private static void bumpWalkGen() {
        WALK_GEN.incrementAndGet();
    }

    /** Storm-stijl: scene-tegel selecteren + viewport-walk (menuAction alleen is soms een no-op). */
    private static void tryViewportWalk(Client c, int sceneX, int sceneY) {
        if (c == null) {
            return;
        }
        try {
            c.getClass().getMethod("setSelectedSceneTileX", int.class).invoke(c, sceneX);
            c.getClass().getMethod("setSelectedSceneTileY", int.class).invoke(c, sceneY);
            try {
                c.getClass().getMethod("setCheckClick", boolean.class).invoke(c, false);
            } catch (NoSuchMethodException ignored) {
            }
            c.getClass().getMethod("setViewportWalking", boolean.class).invoke(c, true);
        } catch (Throwable ignored) {
        }
    }

    private static void timeoutLog(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastTimeoutLogMs < 1200L) {
            return;
        }
        lastTimeoutLogMs = now;
        log.warn("[WalkClick] {}", msg);
        BotRuntime.logConsole(msg);
    }

    /**
     * Client-thread call. Timeout → fallback én invalidate in-flight WALK
     * (anders vuurt menuAction alsnog seconden later met verkeerde coords).
     */
    private static <T> T clientCall(Supplier<T> supplier, T fallback) {
        lastClientCallTimedOut = false;
        if (supplier == null) {
            return fallback;
        }
        AtomicBoolean ran = new AtomicBoolean(false);
        T v = Static.callOnClientThread(() -> {
            try {
                return supplier.get();
            } finally {
                ran.set(true);
            }
        }, fallback, 250L);
        if (!ran.get()) {
            lastClientCallTimedOut = true;
            bumpWalkGen();
        }
        return v;
    }

    private static ClickCandidate resolveCandidates(WorldPoint dest) {
        ClickCandidate got = clientCall(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, dest);
            if (lp == null) {
                return null;
            }
            ClickCandidate cc = new ClickCandidate();

            Point mini = Perspective.localToMinimap(c, lp, MINIMAP_DISTANCE);
            if (mini == null) {
                mini = Perspective.localToMinimap(c, lp);
            }
            // Alleen World Map / Store weigeren — geen strenge disk/chrome die hops doodt
            if (mini != null && mini.getX() >= 0 && mini.getY() >= 0
                    && !WalkUiZones.isOverWorldMapOrb(c, mini)
                    && !WalkUiZones.isOverStoreChrome(c, mini)) {
                cc.minimap = mini;
            }

            Point canvas = Perspective.localToCanvas(c, lp, dest.getPlane());
            if (isOnGameScreen(c, canvas)) {
                cc.canvasRaw = canvas;
                if (!WalkClickSettings.useUiZones || !WalkUiZones.isOverUi(c, canvas)) {
                    cc.canvasSafe = canvas;
                } else {
                    cc.canvasSafe = findSafePolyPoint(c, lp, dest.getPlane());
                }
            } else {
                cc.canvasSafe = findSafePolyPoint(c, lp, dest.getPlane());
                if (cc.canvasSafe != null) {
                    cc.canvasRaw = cc.canvasSafe;
                }
            }
            return cc;
        }, null);
        return got != null ? got : new ClickCandidate();
    }

    /**
     * Verste padtegel (op <b>tegel-afstand</b>) die nog veilig in de minimap-cirkel projecteert.
     * Geen korte random hop binnen min–max — dat voelt stap-voor-stap.
     */
    public static WorldPoint farthestOnMinimap(
            WorldPoint from, java.util.List<WorldPoint> tiles, int minDist, int maxDist) {
        if (from == null || tiles == null || tiles.isEmpty()) {
            return farthestOnMinimap(tiles, minDist, maxDist);
        }
        int lo = Math.max(2, Math.min(minDist, maxDist));
        int hi = Math.max(lo, maxDist);
        return clientCall(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            WorldPoint best = null;
            int bestD = -1;
            WorldPoint bestLoose = null;
            int bestLooseD = -1;
            for (WorldPoint wp : tiles) {
                if (wp == null || wp.getPlane() != from.getPlane()) {
                    continue;
                }
                int d = from.distanceTo(wp);
                if (d < 2) {
                    continue;
                }
                LocalPoint lp = LocalPoint.fromWorld(c, wp);
                if (lp == null || minimapPointIfInside(c, lp) == null) {
                    continue;
                }
                if (d > bestLooseD) {
                    bestLooseD = d;
                    bestLoose = wp;
                }
                if (d >= lo && d <= hi && d > bestD) {
                    bestD = d;
                    best = wp;
                }
            }
            if (best != null) {
                // Kleine slack: 0–2 tegels dichter (antiban), niet random hele band
                int slackTarget = Math.max(lo, bestD - ThreadLocalRandom.current().nextInt(0, 3));
                WorldPoint slackPick = null;
                int slackD = -1;
                for (WorldPoint wp : tiles) {
                    if (wp == null || wp.getPlane() != from.getPlane()) {
                        continue;
                    }
                    int d = from.distanceTo(wp);
                    if (d < slackTarget || d > bestD) {
                        continue;
                    }
                    LocalPoint lp = LocalPoint.fromWorld(c, wp);
                    if (lp == null || minimapPointIfInside(c, lp) == null) {
                        continue;
                    }
                    if (d > slackD) {
                        slackD = d;
                        slackPick = wp;
                    }
                }
                return slackPick != null ? slackPick : best;
            }
            return bestLoose;
        }, null);
    }

    /**
     * Verste padtegel die nog <b>ruim in</b> de minimap-cirkel projecteert (niet op/over de rand).
     * Index-based overload — prefer {@link #farthestOnMinimap(WorldPoint, java.util.List, int, int)}.
     */
    public static WorldPoint farthestOnMinimap(java.util.List<WorldPoint> tiles, int minIdx, int maxIdx) {
        if (tiles == null || tiles.isEmpty()) {
            return null;
        }
        return clientCall(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            int hi = Math.min(Math.max(0, maxIdx), tiles.size() - 1);
            int lo = Math.max(1, Math.min(minIdx, hi));
            int best = -1;
            for (int i = hi; i >= 1; i--) {
                WorldPoint wp = tiles.get(i);
                if (wp == null) {
                    continue;
                }
                LocalPoint lp = LocalPoint.fromWorld(c, wp);
                if (lp == null) {
                    continue;
                }
                if (minimapPointIfInside(c, lp) == null) {
                    continue;
                }
                best = i;
                break;
            }
            if (best < 0) {
                return null;
            }
            // Iets dichterbij dan de rand — muis-Bezier duurt 200–400 ms, de kaart schuift mee.
            int slack = Math.max(3, best * 10 / 100);
            int pick = Math.max(1, best - slack);
            if (pick < lo && best >= lo) {
                pick = lo;
            }
            WorldPoint chosen = tiles.get(pick);
            LocalPoint lpPick = chosen != null ? LocalPoint.fromWorld(c, chosen) : null;
            if (lpPick != null && minimapPointIfInside(c, lpPick) != null) {
                return chosen;
            }
            return tiles.get(best);
        }, null);
    }

    private static Point minimapPointIfInside(Client c, LocalPoint lp) {
        if (c == null || lp == null) {
            return null;
        }
        Point mini = Perspective.localToMinimap(c, lp, MINIMAP_DISTANCE);
        if (mini == null) {
            mini = Perspective.localToMinimap(c, lp);
        }
        if (mini == null || mini.getX() < 0 || mini.getY() < 0) {
            return null;
        }
        if (!isInsideMinimapDisk(c, mini)) {
            return null;
        }
        if (WalkUiZones.isOverWorldMapOrb(c, mini) || WalkUiZones.isOverStoreChrome(c, mini)) {
            return null;
        }
        return mini;
    }

    private static Point resolveMinimapClick(WorldPoint dest) {
        if (dest == null) {
            return null;
        }
        return clientCall(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, dest);
            Point inside = minimapPointIfInside(c, lp);
            if (inside != null) {
                return inside;
            }
            Point raw = lp != null ? Perspective.localToMinimap(c, lp) : null;
            Point clamped = clampToMinimapDisk(c, raw);
            if (clamped == null) {
                return null;
            }
            if (WalkUiZones.isOverWorldMapOrb(c, clamped) || WalkUiZones.isOverStoreChrome(c, clamped)) {
                return null;
            }
            // Alleen een paar pixels corrigeren — anders klikken we een andere tegel.
            if (raw != null) {
                int dx = clamped.getX() - raw.getX();
                int dy = clamped.getY() - raw.getY();
                if (dx * dx + dy * dy > 14 * 14) {
                    return null;
                }
            }
            return clamped;
        }, null);
    }

    private static boolean isInsideMinimapDisk(Client c, Point mini) {
        MinimapDisk d = minimapDisk(c);
        if (d == null || mini == null) {
            return false;
        }
        int dx = mini.getX() - d.cx;
        int dy = mini.getY() - d.cy;
        return dx * dx + dy * dy <= d.r * d.r;
    }

    private static Point clampToMinimapDisk(Client c, Point mini) {
        MinimapDisk d = minimapDisk(c);
        if (d == null || mini == null) {
            return null;
        }
        int dx = mini.getX() - d.cx;
        int dy = mini.getY() - d.cy;
        double dist = Math.hypot(dx, dy);
        if (dist <= d.r) {
            return mini;
        }
        if (dist < 1) {
            return new Point(d.cx, d.cy);
        }
        double s = d.r / dist;
        return new Point(d.cx + (int) Math.round(dx * s), d.cy + (int) Math.round(dy * s));
    }

    /**
     * Random punt in de minimap-cirkel — antiban tijdens travel zonder van de minimap af te gaan.
     */
    public static Point randomMinimapFidgetPoint() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            MinimapDisk d = minimapDisk(c);
            if (d == null) {
                return null;
            }
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double ang = r.nextDouble() * Math.PI * 2.0;
            double rad = r.nextDouble() * (d.r * 0.5);
            Point p = new Point(
                    d.cx + (int) Math.round(Math.cos(ang) * rad),
                    d.cy + (int) Math.round(Math.sin(ang) * rad));
            if (WalkUiZones.isOverWorldMapOrb(c, p) || WalkUiZones.isOverStoreChrome(c, p)) {
                return new Point(d.cx, d.cy);
            }
            return p;
        }, null);
    }

    private static MinimapDisk minimapDisk(Client c) {
        Widget w = minimapDrawWidget(c);
        if (w == null || w.isHidden()) {
            return null;
        }
        Point loc = w.getCanvasLocation();
        int width = w.getWidth();
        int height = w.getHeight();
        if (loc == null || width < 20 || height < 20) {
            return null;
        }
        int cx = loc.getX() + width / 2;
        int cy = loc.getY() + height / 2;
        int rBox = Math.min(width, height) / 2;
        int rInset = rBox - MINIMAP_INSET_PX;
        int rFrac = (int) Math.round(rBox * MINIMAP_SAFE_RADIUS);
        int r = Math.min(rInset, rFrac);
        if (r < 16) {
            r = 16;
        }
        return new MinimapDisk(cx, cy, r);
    }

    /**
     * Zelfde widget als {@link Perspective#localToMinimap} (fixed / stones / bottom-line).
     */
    private static Widget minimapDrawWidget(Client c) {
        if (c == null) {
            return null;
        }
        Widget preferred = null;
        try {
            if (c.isResized()) {
                if (c.getVarbitValue(Varbits.SIDE_PANELS) == 1) {
                    preferred = c.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA);
                } else {
                    preferred = c.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
                }
            } else {
                preferred = c.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
            }
        } catch (Throwable ignored) {
        }
        if (preferred != null && !preferred.isHidden() && preferred.getWidth() > 20) {
            return preferred;
        }
        int[] ids = {
                ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA,
                ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA,
                ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA
        };
        for (int id : ids) {
            try {
                Widget w = c.getWidget(id);
                if (w != null && !w.isHidden() && w.getWidth() > 20) {
                    return w;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Point findSafePolyPoint(Client c, LocalPoint lp, int plane) {
        try {
            Polygon poly = Perspective.getCanvasTilePoly(c, lp);
            if (poly == null || poly.npoints < 3) {
                return null;
            }
            // Sample center + vertices
            int cx = 0;
            int cy = 0;
            for (int i = 0; i < poly.npoints; i++) {
                cx += poly.xpoints[i];
                cy += poly.ypoints[i];
            }
            cx /= poly.npoints;
            cy /= poly.npoints;
            Point[] samples = new Point[poly.npoints + 1];
            samples[0] = new Point(cx, cy);
            for (int i = 0; i < poly.npoints; i++) {
                samples[i + 1] = new Point(poly.xpoints[i], poly.ypoints[i]);
            }
            for (Point p : samples) {
                if (!isOnGameScreen(c, p)) {
                    continue;
                }
                if (WalkClickSettings.useUiZones && WalkUiZones.isOverUi(c, p)) {
                    continue;
                }
                return p;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isOnGameScreen(Client c, Point canvas) {
        if (canvas == null) {
            return false;
        }
        int cw = c.getCanvasWidth();
        int ch = c.getCanvasHeight();
        int x = canvas.getX();
        int y = canvas.getY();
        return cw > 40 && ch > 40
                && x >= 8 && y >= 8
                && x < cw - 8 && y < ch - 8;
    }

    /**
     * Muis naar tegel + echte left-click <b>alleen</b> als hover = Walk here.
     * Anders altijd {@link #invokeWalk} — nooit Chop/Attack/Talk/Take/…
     */
    private static boolean walkHereSafe(WorldPoint dest, Point canvasHint, String method) {
        if (dest == null) {
            return false;
        }
        AntiBan.get().markBotActivity();
        return walkHereSafeLocked(dest, canvasHint, method);
    }

    private static boolean walkHereSafeLocked(WorldPoint dest, Point canvasHint, String method) {
        return walkHereSafeLocked(dest, canvasHint, method, true);
    }

    private static boolean walkHereSafeLocked(WorldPoint dest, Point canvasHint, String method, boolean allowInvoke) {
        Mouse.Session lock = Static.isOnClientThread()
                ? Mouse.trySession()
                : Mouse.trySession(50L);
        if (lock == null) {
            if (allowInvoke && invokeWalk(dest)) {
                setLast(method + "+invoke-busy", dest.getX() + "," + dest.getY(), dest);
                afterSuccessfulClick();
                return true;
            }
            return walkHereSafeUnlocked(dest, canvasHint, method, allowInvoke);
        }
        try {
            return walkHereSafeUnlocked(dest, canvasHint, method, allowInvoke);
        } finally {
            lock.close();
        }
    }

    private static boolean walkHereSafeUnlocked(WorldPoint dest, Point canvasHint, String method) {
        return walkHereSafeUnlocked(dest, canvasHint, method, true);
    }

    private static boolean walkHereSafeUnlocked(WorldPoint dest, Point canvasHint, String method, boolean allowInvoke) {
        Point aim = canvasHint;
        if (aim == null && WalkClickSettings.canvasPreferRealClick) {
            ClickCandidate c = resolveCandidates(dest);
            aim = c.canvasSafe != null ? c.canvasSafe : c.canvasRaw;
        }

        boolean preferClick = WalkClickSettings.canvasPreferRealClick && aim != null;
        if (preferClick) {
            boolean moved = MouseManager.moveToWalkTarget(aim);
            if (!moved) {
                Mouse.moveRaw(aim.getX(), aim.getY());
            }
            boolean travelCanvas = false;
            try {
                travelCanvas = MovementHelper.isTravelContext();
            } catch (Throwable ignored) {
            }
            if (!travelCanvas) {
                sleepMs(MouseSettings.scaleDelay(30 + ThreadLocalRandom.current().nextInt(50)));
            }

            boolean walkOk = hoverLooksLikeWalkHere();
            if (walkOk) {
                boolean ok = Mouse.clickOnly(aim.getX(), aim.getY(), true);
                if (ok) {
                    lastWalkClickMs = System.currentTimeMillis();
                    setLast(method + "+click", dest.getX() + "," + dest.getY()
                            + " @" + aim.getX() + "," + aim.getY(), dest);
                    WalkClickSettings.noteHuman("walk-click", 0, false, method);
                    net.storm.sdk.bot.BotRuntime.logConsole(
                            "Walk CLICK " + method + " → " + dest.getX() + "," + dest.getY());
                    AntiBan.get().markBotActivity();
                    afterSuccessfulClick();
                    return true;
                }
            } else if (allowInvoke) {
                log.info("[WalkClick] hover≠Walk here → invoke WALK ({})", method);
            }
        }

        if (allowInvoke && invokeWalk(dest)) {
            lastWalkClickMs = System.currentTimeMillis();
            setLast(method + "+invoke", dest.getX() + "," + dest.getY(), dest);
            WalkClickSettings.noteHuman("walk-invoke", 0, false, method);
            net.storm.sdk.bot.BotRuntime.logConsole(
                    "Walk INVOKE " + method + " → " + dest.getX() + "," + dest.getY());
            AntiBan.get().markBotActivity();
            afterSuccessfulClick();
            return true;
        }
        return false;
    }

    /**
     * True alleen als de <b>left-click default</b> Walk here is.
     * Elke andere optie of twijfel → false → invoke Walk.
     */
    private static boolean hoverLooksLikeWalkHere() {
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                net.runelite.api.MenuEntry[] entries = c.getMenuEntries();
                if (entries == null || entries.length == 0) {
                    return false;
                }
                // Eerste niet-Cancel entry = left-click default
                for (net.runelite.api.MenuEntry e : entries) {
                    if (e == null || e.getOption() == null) {
                        continue;
                    }
                    String opt = e.getOption().trim();
                    if (opt.isEmpty() || opt.equalsIgnoreCase("Cancel")) {
                        continue;
                    }
                    String lower = opt.toLowerCase();
                    return lower.contains("walk here");
                }
            } catch (Throwable ignored) {
            }
            return false;
        }, false, 80L);
        return Boolean.TRUE.equals(ok);
    }

    private static boolean clickPoint(Point p, String method) {
        return clickPoint(p, method, method != null && method.contains("minimap"), null);
    }

    /**
     * @param allowTwin minimap mag twin; canvas/far niet
     */
    private static boolean clickPoint(Point p, String method, boolean allowTwin) {
        return clickPoint(p, method, allowTwin, null);
    }

    private static boolean clickPoint(Point p, String method, boolean allowTwin, WorldPoint dest) {
        if (p == null) {
            return false;
        }
        AntiBan.get().markBotActivity();
        return clickPointLocked(p, method, allowTwin, dest);
    }

    private static boolean clickPointLocked(Point p, String method, boolean allowTwin) {
        return clickPointLocked(p, method, allowTwin, null);
    }

    private static boolean clickPointLocked(Point p, String method, boolean allowTwin, WorldPoint dest) {
        if (p == null) {
            return false;
        }
        Mouse.Session lock = Static.isOnClientThread()
                ? Mouse.trySession()
                : Mouse.trySession(50L);
        if (lock == null) {
            boolean minimap = method != null && method.contains("minimap");
            if (minimap) {
                return clickPointUnlocked(p, method, allowTwin, dest);
            }
            if (dest != null && clickMinimapHop(dest)) {
                return true;
            }
            return clickPointUnlocked(p, method, allowTwin, dest);
        }
        try {
            return clickPointUnlocked(p, method, allowTwin, dest);
        } finally {
            lock.close();
        }
    }

    private static boolean clickPointUnlocked(Point p, String method, boolean allowTwin, WorldPoint dest) {
        if (p == null) {
            return false;
        }
        if (method != null && method.contains("minimap")) {
            Boolean bad = Static.callOnClientThread(() -> {
                Client c = Static.getClient();
                return WalkUiZones.isOverWorldMapOrb(c, p) || WalkUiZones.isOverStoreChrome(c, p);
            }, false, 80L);
            if (Boolean.TRUE.equals(bad)) {
                setLast("skip-worldmap-store", method);
                BotRuntime.logConsole("[Walk] minimap-klik geweigerd — World Map/Store");
                return false;
            }
        }
        try {
            net.storm.sdk.game.WorldMap.dismissIfOpenDuringWalk();
        } catch (Throwable ignored) {
        }
        boolean moved = MouseManager.moveToWalkTarget(p);
        if (!moved && !Mouse.moveRaw(p.getX(), p.getY())) {
            setLast("fail-move", method);
            return false;
        }

        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean travel = false;
        try {
            travel = MovementHelper.isTravelContext();
        } catch (Throwable ignored) {
        }
        boolean twin = allowTwin && WalkClickSettings.twinQuickClicks
                && !WalkClickSettings.useFarCanvasWalk
                && !travel;
        int hoverMs;
        String hoverKind;
        if (travel) {
            hoverMs = r.nextInt(5);
            hoverKind = "travel-snap";
        } else if (twin) {
            hoverMs = 3 + r.nextInt(12);
            hoverKind = "twin-snap";
        } else {
            int roll = r.nextInt(100);
            if (roll < 55) {
                hoverMs = 4 + r.nextInt(18);
                hoverKind = "snappy";
            } else if (roll < 90) {
                hoverMs = 18 + r.nextInt(35);
                hoverKind = "normal";
            } else {
                hoverMs = 50 + r.nextInt(55);
                hoverKind = "slow";
            }
        }
        int scaledHover = travel
                ? MouseSettings.scaleDelayWalkReturn(hoverMs)
                : MouseSettings.scaleDelay(hoverMs);
        if (scaledHover > 0) {
            sleepMs(scaledHover);
        }

        boolean ok = Mouse.clickOnly(p.getX(), p.getY(), true);
        String label = method;
        boolean dbl = false;

        if (ok && twin) {
            sleepMs(MouseSettings.scaleDelay(22 + r.nextInt(28)));
            Point twinPt = jitterInsideMinimap(p, r, method != null && method.contains("minimap"));
            Boolean twinUnsafe = method != null && method.contains("minimap")
                    ? Static.callOnClientThread(() -> {
                        Client c = Static.getClient();
                        return WalkUiZones.isOverWorldMapOrb(c, twinPt)
                                || WalkUiZones.isOverStoreChrome(c, twinPt);
                    }, false, 80L)
                    : false;
            if (!Boolean.TRUE.equals(twinUnsafe)) {
                Mouse.clickOnly(twinPt.getX(), twinPt.getY(), true);
            }
            label = method + "-twin";
            dbl = true;
            log.info("[WalkClick] TWIN-QUICK {} @{},{} hover={} {}ms",
                    method, p.getX(), p.getY(), hoverKind, scaledHover);
            BotRuntime.logConsole("Walk TWIN " + method + " @" + p.getX() + "," + p.getY());
            } else if (ok && allowTwin && !travel && !WalkClickSettings.useFarCanvasWalk
                && r.nextInt(100) < WalkClickSettings.doubleClickChancePercent) {
            sleepMs(MouseSettings.scaleDelay(18 + r.nextInt(42)));
            Point dblPt = jitterInsideMinimap(p, r, method != null && method.contains("minimap"));
            Boolean dblUnsafe = method != null && method.contains("minimap")
                    ? Static.callOnClientThread(() -> {
                        Client c = Static.getClient();
                        return WalkUiZones.isOverWorldMapOrb(c, dblPt)
                                || WalkUiZones.isOverStoreChrome(c, dblPt);
                    }, false, 80L)
                    : false;
            if (!Boolean.TRUE.equals(dblUnsafe)) {
                Mouse.clickOnly(dblPt.getX(), dblPt.getY(), true);
            }
            label = method + "-dbl";
            dbl = true;
            log.info("[WalkClick] DOUBLE-CLICK {} @{},{} hover={} {}ms",
                    method, p.getX(), p.getY(), hoverKind, scaledHover);
            BotRuntime.logConsole("Walk DBL " + method + " @" + p.getX() + "," + p.getY());
        } else if (ok) {
            log.info("[WalkClick] {} @{},{} hover={} {}ms",
                    method, p.getX(), p.getY(), hoverKind, scaledHover);
            BotRuntime.logConsole("Walk " + method + " @" + p.getX() + "," + p.getY());
        }

        WalkClickSettings.noteHuman(hoverKind, scaledHover, dbl, method);

        if (ok) {
            lastWalkClickMs = System.currentTimeMillis();
            setLast(label, hoverKind + " " + scaledHover + "ms" + (dbl ? " +DBL" : "")
                    + " @" + p.getX() + "," + p.getY());
            AntiBan.get().markBotActivity();
            afterSuccessfulClick();
        }
        return ok;
    }

    /**
     * Debug: force snappy hover + double-click on canvas center (overlay Walk hum).
     */
    public static boolean testForceSnappyDoubleClick() {
        Client c = Static.getClient();
        Canvas canvas = c != null ? c.getCanvas() : null;
        int x = canvas != null ? Math.max(80, canvas.getWidth() / 2) : 400;
        int y = canvas != null ? Math.max(80, canvas.getHeight() / 3) : 220;
        Point p = new Point(x, y);

        boolean moved = MouseManager.moveTo(p);
        if (!moved) {
            Mouse.movePathTo(x, y, null);
        }
        int hoverMs = MouseSettings.scaleDelay(12 + ThreadLocalRandom.current().nextInt(20));
        sleepMs(hoverMs);
        boolean ok = Mouse.clickOnly(x, y, true);
        sleepMs(MouseSettings.scaleDelay(40 + ThreadLocalRandom.current().nextInt(50)));
        int jx = x + ThreadLocalRandom.current().nextInt(-2, 3);
        int jy = y + ThreadLocalRandom.current().nextInt(-2, 3);
        Mouse.clickOnly(jx, jy, true);
        WalkClickSettings.noteHuman("snappy", hoverMs, true, "test-force");
        setLast("test-force-dbl", "snappy " + hoverMs + "ms +DBL @" + x + "," + y);
        log.info("[WalkClick] FORCE double-click snappy {}ms @{},{}", hoverMs, x, y);
        AntiBan.get().markBotActivity();
        return ok;
    }

    /** Ms sinds laatste gelukte walk-klik (0 = nooit). */
    public static long millisSinceLastClick() {
        long t = lastWalkClickMs;
        if (t <= 0L) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() - t;
    }

    private static void sleepMs(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            net.storm.sdk.bot.BotRuntime.heartbeat();
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Alleen MMB als canvas geblokkeerd — géén setCameraYawTarget (dat snapt/vliegt terug).
     */
    private static boolean nudgeCameraToward(WorldPoint from, WorldPoint dest) {
        long now = System.currentTimeMillis();
        if (now - lastCameraNudgeMs < 900L) {
            return false;
        }
        if (from == null || dest == null) {
            return false;
        }
        // Skip als WalkCamera al genoeg zicht heeft
        int face = WalkCameraSettings.lastFacePercent;
        if (face >= WalkCameraSettings.visibilitySkipPercent) {
            return false;
        }
        lastCameraNudgeMs = now;
        WalkCamera.prepareForWalk(dest);
        setLast("camera-walkcam", dest.toString());
        return true;
    }

    private static void setLast(String method, String detail) {
        WalkClickSettings.lastMethod = method != null ? method : "-";
        WalkClickSettings.lastDetail = detail != null ? detail : "-";
    }

    private static void setLast(String method, String detail, WorldPoint ignored) {
        setLast(method, detail);
    }

    private static Point jitterInsideMinimap(Point aim, ThreadLocalRandom r, boolean minimap) {
        int jx = aim.getX() + r.nextInt(-2, 3);
        int jy = aim.getY() + r.nextInt(-2, 3);
        Point jittered = new Point(jx, jy);
        if (!minimap) {
            return jittered;
        }
        Point clamped = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            Point in = clampToMinimapDisk(c, jittered);
            return in != null ? in : aim;
        }, aim);
        return clamped != null ? clamped : aim;
    }

    private static void miniLog(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastMiniLog) && now - lastMiniLogMs < 1500L) {
            return;
        }
        lastMiniLog = msg;
        lastMiniLogMs = now;
        log.info(msg);
        BotRuntime.logConsole(msg);
    }

    private static final class MinimapDisk {
        final int cx;
        final int cy;
        final int r;

        MinimapDisk(int cx, int cy, int r) {
            this.cx = cx;
            this.cy = cy;
            this.r = r;
        }
    }

    private static final class ClickCandidate {
        Point minimap;
        Point canvasSafe;
        Point canvasRaw;
    }
}
