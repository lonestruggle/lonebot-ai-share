package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Safe walk click pipeline. Channels gated by {@link WalkClickSettings}.
 * Far-canvas: yaw naar lookAt + mid-high pitch (via {@link WalkCamera}), daarna canvas-first.
 */
public final class WalkClickHelper {

    private static final Logger log = LoggerFactory.getLogger(WalkClickHelper.class);

    private static final int MINIMAP_DISTANCE = Perspective.LOCAL_TILE_SIZE * 70;
    /** Align with {@link WalkCameraSettings}: mid-high by default, not horizon-low. */
    private static final int FAR_PITCH_MIN = 260;
    private static final int FAR_PITCH_MAX = 340;
    private static volatile long lastCameraNudgeMs;
    private static volatile long lastWalkClickMs;
    private static volatile long lastFarOrientMs;

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
     * @param dest   tile to walk toward (click target)
     * @param lookAt direction for far-canvas camera (often path destination)
     */
    public static boolean walkTo(WorldPoint dest, WorldPoint lookAt) {
        if (dest == null) {
            return false;
        }
        WorldPoint look = lookAt != null ? lookAt : dest;

        WorldPoint from = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null) {
                return null;
            }
            return c.getLocalPlayer().getWorldLocation();
        }, null);
        if (from == null) {
            return false;
        }
        if (from.getPlane() == dest.getPlane() && from.distanceTo(dest) <= 0) {
            setLast("arrived", dest.toString());
            return true;
        }

        WorldPoint curDest = Movement.getDestination();
        long now = System.currentTimeMillis();
        WorldPoint flagForWait = curDest != null ? curDest : dest;

        // Far/canvas: wacht tot dicht bij flag — alleen bij langere walks (geen korte-hop stap-gevoel)
        if (curDest != null && Movement.isMoving()
                && WalkClickSettings.shouldWaitNearFlag(from, curDest, dest)) {
            setLast("wait-flag", "d=" + from.distanceTo(curDest)
                    + " need≤" + WalkClickSettings.canvasReclickWithinTiles);
            return true;
        }

        int needWait = WalkClickSettings.effectiveReclickMs(from, flagForWait);
        needWait = Math.max(needWait,
                WalkClickSettings.canvasExtraMinReclickMs(from, flagForWait, dest));
        if (curDest != null && curDest.getPlane() == dest.getPlane()
                && curDest.distanceTo(dest) <= 3
                && now - lastWalkClickMs < needWait) {
            setLast("cooldown", needWait + "ms d=" + from.distanceTo(flagForWait));
            return true;
        }

        boolean far = WalkClickSettings.useFarCanvasWalk;
        // Far: camera volgt klik/looprichting; classic mag lookAt (pad-eind) gebruiken
        WorldPoint camLook = far ? dest : look;
        // Camera: alleen bijsturen als te weinig zicht — nooit pitch terugtrekken via setYaw snap
        WalkCamera.prepareForWalk(camLook);

        ClickCandidate cand = resolveCandidates(dest);

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
                    && clickPoint(cand.minimap, "minimap-far", true)) {
                return true;
            }
            cand = resolveCandidates(dest);
            if ((WalkClickSettings.canvasForceWalkHere || WalkClickSettings.useInvokeWalk)
                    && walkHereSafe(dest, cand.canvasSafe, "canvas-walk-here-2")) {
                return true;
            }
            if (WalkClickSettings.useMinimap && cand.minimap != null
                    && clickPoint(cand.minimap, "minimap-far-postcam", true)) {
                return true;
            }
        } else {
            // Classic: minimap → canvas → camera → invoke
            if (WalkClickSettings.useMinimap && cand.minimap != null) {
                if (clickPoint(cand.minimap, "minimap", true)) {
                    return true;
                }
            }
            if (WalkClickSettings.useCanvas && cand.canvasSafe != null) {
                if (walkHereSafe(dest, cand.canvasSafe, "canvas-safe")) {
                    return true;
                }
            }
            if (WalkClickSettings.useCameraNudge && (cand.canvasRaw != null || cand.minimap == null)) {
                nudgeCameraToward(from, dest);
                cand = resolveCandidates(dest);
                if (WalkClickSettings.useMinimap && cand.minimap != null
                        && clickPoint(cand.minimap, "minimap-postcam", true)) {
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
                setLast("invoke-WALK", dest.toString());
                // Twin alleen op minimap-stijl, niet op canvas/far ground-walk
                boolean twin = !far && (WalkClickSettings.twinQuickClicks
                        || ThreadLocalRandom.current().nextInt(100) < WalkClickSettings.doubleClickChancePercent);
                if (twin) {
                    sleepMs(MouseSettings.scaleDelay(20 + ThreadLocalRandom.current().nextInt(40)));
                    invokeWalk(dest);
                    WalkClickSettings.noteHuman("invoke", 0, true, "invoke-WALK");
                    setLast(WalkClickSettings.twinQuickClicks ? "invoke-WALK-twin" : "invoke-WALK-dbl",
                            dest.toString());
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

        setLast("fail", "no channel for " + dest);
        log.warn("[WalkClick] fail dest={} mini={} canvasSafe={} invoke={} far={}",
                dest, cand.minimap != null, cand.canvasSafe != null,
                WalkClickSettings.useInvokeWalk, far);
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
     * Direct {@link Client#menuAction} Walk-here (scene coords). Prefer over reflection invokeMenuAction.
     */
    public static boolean invokeWalk(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, dest);
            if (lp == null) {
                return false;
            }
            int sx = lp.getSceneX();
            int sy = lp.getSceneY();
            if (sx < 0 || sy < 0 || sx > 103 || sy > 103) {
                return false;
            }
            try {
                c.menuAction(sx, sy, MenuAction.WALK, 0, 0, "Walk here", "");
                log.info("[WalkClick] invoke menuAction WALK scene={},{} → {}", sx, sy, dest);
                lastWalkClickMs = System.currentTimeMillis();
                net.storm.sdk.bot.BotRuntime.logConsole("Walk invoke → " + dest.getX() + "," + dest.getY());
                return true;
            } catch (Throwable t) {
                log.warn("[WalkClick] menuAction WALK failed: {}", t.toString());
                return false;
            }
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    private static ClickCandidate resolveCandidates(WorldPoint dest) {
        AtomicReference<ClickCandidate> ref = new AtomicReference<>(new ClickCandidate());
        Static.callOnClientThread(() -> {
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
            if (mini != null && mini.getX() >= 0 && mini.getY() >= 0) {
                cc.minimap = mini;
            }

            Point canvas = Perspective.localToCanvas(c, lp, dest.getPlane());
            if (isOnGameScreen(c, canvas)) {
                cc.canvasRaw = canvas;
                if (!WalkClickSettings.useUiZones || !WalkUiZones.isOverUi(c, canvas)) {
                    cc.canvasSafe = canvas;
                } else {
                    // Try tile poly offsets for a UI-clear pixel
                    cc.canvasSafe = findSafePolyPoint(c, lp, dest.getPlane());
                }
            } else {
                // Off-screen center — try poly points still on-screen + UI-safe
                cc.canvasSafe = findSafePolyPoint(c, lp, dest.getPlane());
                if (cc.canvasSafe != null) {
                    cc.canvasRaw = cc.canvasSafe;
                }
            }
            ref.set(cc);
            return null;
        }, null);
        return ref.get() != null ? ref.get() : new ClickCandidate();
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
     * Muis naar tegel + echte left-click als “Walk here”; anders invoke (geen Chop/Attack).
     */
    private static boolean walkHereSafe(WorldPoint dest, Point canvasHint, String method) {
        if (dest == null) {
            return false;
        }
        Point aim = canvasHint;
        if (aim == null && WalkClickSettings.canvasPreferRealClick) {
            // Probeer alsnog canvas-punt voor echte klik
            ClickCandidate c = resolveCandidates(dest);
            aim = c.canvasSafe != null ? c.canvasSafe : c.canvasRaw;
        }

        boolean preferClick = WalkClickSettings.canvasPreferRealClick && aim != null;
        if (preferClick) {
            boolean moved = MouseManager.moveTo(aim);
            if (!moved) {
                Mouse.movePathTo(aim.getX(), aim.getY(), null);
            }
            sleepMs(MouseSettings.scaleDelay(30 + ThreadLocalRandom.current().nextInt(50)));

            boolean walkOk = hoverLooksLikeWalkHere();
            if (walkOk || !WalkClickSettings.canvasForceWalkHere) {
                boolean ok = Mouse.clickOnly(aim.getX(), aim.getY(), true);
                if (ok) {
                    lastWalkClickMs = System.currentTimeMillis();
                    setLast(method + "+click", dest.getX() + "," + dest.getY()
                            + " @" + aim.getX() + "," + aim.getY());
                    WalkClickSettings.noteHuman("walk-click", 0, false, method);
                    net.storm.sdk.bot.BotRuntime.logConsole(
                            "Walk CLICK " + method + " → " + dest.getX() + "," + dest.getY());
                    AntiBan.get().markBotActivity();
                    afterSuccessfulClick();
                    return true;
                }
            } else {
                log.info("[WalkClick] hover≠Walk here → invoke WALK ({})", method);
            }
        }

        // Fallback / geen canvas: menuAction WALK (geen echte klik)
        if (invokeWalk(dest)) {
            lastWalkClickMs = System.currentTimeMillis();
            setLast(method + "+invoke", dest.getX() + "," + dest.getY());
            WalkClickSettings.noteHuman("walk-invoke", 0, false, method);
            net.storm.sdk.bot.BotRuntime.logConsole(
                    "Walk INVOKE " + method + " → " + dest.getX() + "," + dest.getY());
            AntiBan.get().markBotActivity();
            afterSuccessfulClick();
            return true;
        }
        return false;
    }

    /** True als top left-click actie Walk here lijkt (anders boom/NPC/etc.). */
    private static boolean hoverLooksLikeWalkHere() {
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return true; // unknown → laat invokeWalk beslissen
            }
            try {
                net.runelite.api.MenuEntry[] entries = c.getMenuEntries();
                if (entries == null || entries.length == 0) {
                    // Geen open menu: left-click target — check tip/option als beschikbaar
                    return true;
                }
                // Laatste entry is vaak de left-click default
                for (int i = entries.length - 1; i >= 0; i--) {
                    net.runelite.api.MenuEntry e = entries[i];
                    if (e == null) {
                        continue;
                    }
                    String opt = e.getOption();
                    if (opt == null) {
                        continue;
                    }
                    String lower = opt.toLowerCase();
                    if (lower.contains("walk here")) {
                        return true;
                    }
                    if (lower.startsWith("chop") || lower.startsWith("attack")
                            || lower.startsWith("mine") || lower.startsWith("talk")
                            || lower.startsWith("take") || lower.startsWith("pick")) {
                        return false;
                    }
                }
            } catch (Throwable ignored) {
            }
            return true;
        }, true);
        return Boolean.TRUE.equals(ok);
    }

    private static boolean clickPoint(Point p, String method) {
        return clickPoint(p, method, method != null && method.contains("minimap"));
    }

    /**
     * @param allowTwin minimap mag twin; canvas/far niet
     */
    private static boolean clickPoint(Point p, String method, boolean allowTwin) {
        if (p == null) {
            return false;
        }
        // Move first
        boolean moved = MouseManager.moveTo(p);
        if (!moved && !Mouse.movePathTo(p.getX(), p.getY(), null)) {
            setLast("fail-move", method);
            return false;
        }

        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean twin = allowTwin && WalkClickSettings.twinQuickClicks
                && !WalkClickSettings.useFarCanvasWalk;
        int hoverMs;
        String hoverKind;
        if (twin) {
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
        int scaledHover = MouseSettings.scaleDelay(hoverMs);
        sleepMs(scaledHover);

        boolean ok = Mouse.clickOnly(p.getX(), p.getY(), true);
        String label = method;
        boolean dbl = false;

        if (ok && twin) {
            sleepMs(MouseSettings.scaleDelay(22 + r.nextInt(28)));
            int jx = p.getX() + r.nextInt(-2, 3);
            int jy = p.getY() + r.nextInt(-2, 3);
            Mouse.clickOnly(jx, jy, true);
            label = method + "-twin";
            dbl = true;
            log.info("[WalkClick] TWIN-QUICK {} @{},{} hover={} {}ms",
                    method, p.getX(), p.getY(), hoverKind, scaledHover);
            net.storm.sdk.bot.BotRuntime.logConsole("Walk TWIN " + method + " @" + p.getX() + "," + p.getY());
        } else if (ok && allowTwin && !WalkClickSettings.useFarCanvasWalk
                && r.nextInt(100) < WalkClickSettings.doubleClickChancePercent) {
            sleepMs(MouseSettings.scaleDelay(18 + r.nextInt(42)));
            int jx = p.getX() + r.nextInt(-2, 3);
            int jy = p.getY() + r.nextInt(-2, 3);
            Mouse.clickOnly(jx, jy, true);
            label = method + "-dbl";
            dbl = true;
            log.info("[WalkClick] DOUBLE-CLICK {} @{},{} hover={} {}ms",
                    method, p.getX(), p.getY(), hoverKind, scaledHover);
            net.storm.sdk.bot.BotRuntime.logConsole("Walk DBL " + method + " @" + p.getX() + "," + p.getY());
        } else if (ok) {
            log.info("[WalkClick] {} @{},{} hover={} {}ms",
                    method, p.getX(), p.getY(), hoverKind, scaledHover);
            net.storm.sdk.bot.BotRuntime.logConsole("Walk " + method + " @" + p.getX() + "," + p.getY());
        }

        WalkClickSettings.noteHuman(hoverKind, scaledHover, dbl, method);

        if (ok) {
            lastWalkClickMs = System.currentTimeMillis();
            setLast(label, hoverKind + " " + scaledHover + "ms" + (dbl ? " +DBL" : "")
                    + " @" + p.getX() + "," + p.getY());
            net.storm.sdk.utils.AntiBan.get().markBotActivity();
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

    private static void sleepMs(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
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

    private static final class ClickCandidate {
        Point minimap;
        Point canvasSafe;
        Point canvasRaw;
    }
}
