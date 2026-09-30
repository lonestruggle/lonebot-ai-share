package net.storm.sdk.interact.mouse;

import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.actors.INPC;
import net.storm.api.interact.mouse.MouseMovementStrategy;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.input.MouseSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Storm-compat MouseManager: strategy → resample → moveAlongPath (easing/fatigue) → click.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/interact/mouse/MouseManager.html">Storm MouseManager</a>
 */
public final class MouseManager {

    private static final Logger log = LoggerFactory.getLogger(MouseManager.class);

    /** StormConfig-achtige defaults. */
    private static final int BASE_DELAY_MS = 8;
    private static final int DELAY_VARIATION_MS = 6;
    private static final boolean EASE_DEFAULT = true;
    private static final double EASE_STRENGTH = 1.35;
    private static final boolean FATIGUE_DEFAULT = true;
    private static final double FATIGUE_MULTIPLIER = 0.0025;
    private static final int MIN_PATH_POINTS = 8;
    private static final double PATH_DENSITY = 0.28; // points per pixel of distance
    private static volatile long lastWalkReturnLogMs;

    private static volatile MouseMovementStrategy strategy = new BezierCurveMouseMovement();

    private MouseManager() {
    }

    public static void setMovementStrategy(MouseMovementStrategy s) {
        if (s != null) {
            strategy = s;
            log.info("MouseMovementStrategy → {}", s.getClass().getSimpleName());
        }
    }

    /** Storm alias. */
    public static void setMouseMovementStrategy(MouseMovementStrategy s) {
        setMovementStrategy(s);
    }

    public static MouseMovementStrategy getMovementStrategy() {
        return strategy;
    }

    public static MouseMovementStrategy getMouseMovementStrategy() {
        return strategy;
    }

    /** Low-level: één move-event (Storm {@code move(x,y)}). */
    public static boolean move(int x, int y) {
        return Mouse.moveRaw(x, y);
    }

    /** Low-level click press+release (Storm {@code click(x,y)} + release). */
    public static boolean click(int x, int y) {
        return Mouse.clickOnly(x, y, true);
    }

    public static void release() {
        // release zit in clickOnly; no-op voor API-compat
    }

    /** Beweegt (Bezier e.d.) naar canvas-punt — zonder klik. */
    public static boolean moveTo(int x, int y) {
        return moveTo(new Point(x, y));
    }

    public static boolean moveTo(Point canvasPoint) {
        if (canvasPoint == null || canvasPoint.getX() < 0 || canvasPoint.getY() < 0) {
            return false;
        }
        java.awt.Point from = new java.awt.Point(Mouse.getX(), Mouse.getY());
        java.awt.Point to = new java.awt.Point(canvasPoint.getX(), canvasPoint.getY());
        MouseMovementStrategy.MousePath raw = strategy.generatePath(from, to);
        List<java.awt.Point> points = resamplePath(raw.getPoints(), to);
        return moveAlongPath(points);
    }

    /**
     * Terug naar walk-klik (minimap/canvas) na camera/antiban-muis.
     * Korte lijn, geen zware Bezier — anders mis je de volgende hop.
     */
    public static boolean moveToWalkTarget(Point canvasPoint) {
        if (canvasPoint == null || canvasPoint.getX() < 0 || canvasPoint.getY() < 0) {
            return false;
        }
        int tx = canvasPoint.getX();
        int ty = canvasPoint.getY();
        int dx = tx - Mouse.getX();
        int dy = ty - Mouse.getY();
        int dist = (int) Math.round(Math.hypot(dx, dy));
        if (dist <= 8) {
            return Mouse.moveRaw(tx, ty);
        }
        int steps = Math.max(2, Math.min(5, dist / 90));
        List<java.awt.Point> points = new ArrayList<>(steps);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int fromX = Mouse.getX();
        int fromY = Mouse.getY();
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(fromX + dx * t);
            int y = (int) Math.round(fromY + dy * t);
            if (i < steps) {
                x += r.nextInt(-1, 2);
                y += r.nextInt(-1, 2);
            } else {
                x = tx;
                y = ty;
            }
            points.add(new java.awt.Point(x, y));
        }
        long now = System.currentTimeMillis();
        if (now - lastWalkReturnLogMs >= 1500L) {
            lastWalkReturnLogMs = now;
            try {
                net.storm.sdk.bot.BotRuntime.logConsole(
                        "[Walk/mouse] terug klik d=" + dist + "px steps=" + steps);
            } catch (Throwable ignored) {
            }
        }
        return moveAlongPathWalkReturn(points);
    }

    private static boolean moveAlongPathWalkReturn(List<java.awt.Point> points) {
        try (net.storm.sdk.input.Mouse.Session lock = net.storm.sdk.input.Mouse.trySession(40L)) {
            if (lock == null) {
                return false;
            }
            return moveAlongPathUnlocked(points, 1, 1, false, 0, false, 0);
        }
    }

    public static boolean moveTo(INPC npc) {
        Point p = ClickPoints.forNpc(npc);
        if (p == null) {
            log.debug("moveTo NPC: geen clickpoint");
            return false;
        }
        log.info("moveTo NPC {} → {},{}", npc.getName(), p.getX(), p.getY());
        return moveTo(p);
    }

    public static boolean hover(INPC npc) {
        return moveTo(npc);
    }

    /**
     * Storm moveAlongPath — defaults (ease + fatigue).
     */
    public static boolean moveAlongPath(List<java.awt.Point> points) {
        return moveAlongPath(points, BASE_DELAY_MS, DELAY_VARIATION_MS,
                EASE_DEFAULT, EASE_STRENGTH, FATIGUE_DEFAULT, FATIGUE_MULTIPLIER);
    }

    /**
     * Storm-compat path execution met easing + fatigue.
     */
    public static boolean moveAlongPath(
            List<java.awt.Point> points,
            int baseDelay,
            int delayVariation,
            boolean easeMovement,
            double easeStrength,
            boolean fatigueEnabled,
            double fatigueMultiplier) {
        try (net.storm.sdk.input.Mouse.Session lock = clientThreadMustNotBlock()
                ? net.storm.sdk.input.Mouse.trySession()
                : net.storm.sdk.input.Mouse.session()) {
            if (lock == null) {
                return false;
            }
            return moveAlongPathUnlocked(points, baseDelay, delayVariation,
                    easeMovement, easeStrength, fatigueEnabled, fatigueMultiplier);
        }
    }

    private static boolean clientThreadMustNotBlock() {
        return net.storm.sdk.game.Static.isOnClientThread();
    }

    private static boolean moveAlongPathUnlocked(
            List<java.awt.Point> points,
            int baseDelay,
            int delayVariation,
            boolean easeMovement,
            double easeStrength,
            boolean fatigueEnabled,
            double fatigueMultiplier) {
        if (points == null || points.isEmpty()) {
            return false;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = points.size();
        double traveled = 0;
        java.awt.Point prev = null;
        for (int i = 0; i < n; i++) {
            java.awt.Point pt = points.get(i);
            if (pt == null) {
                continue;
            }
            if (!Mouse.moveRaw(pt.x, pt.y)) {
                return false;
            }
            if (prev != null) {
                traveled += Math.hypot(pt.x - prev.x, pt.y - prev.y);
            }
            prev = pt;

            double t = n <= 1 ? 1.0 : i / (double) (n - 1);
            double ease = 1.0;
            if (easeMovement && easeStrength > 0) {
                // slower near ends (ease-in-out), faster middle
                double edge = Math.min(t, 1.0 - t) * 2.0; // 0 at ends, 1 at mid
                ease = 1.0 + easeStrength * (1.0 - edge);
            }
            double fatigue = 1.0;
            if (fatigueEnabled) {
                fatigue = 1.0 + traveled * fatigueMultiplier;
            }
            int delay = (int) Math.round((baseDelay + r.nextInt(Math.max(1, delayVariation + 1))) * ease * fatigue);
            int wait = (baseDelay <= 1 && !easeMovement && !fatigueEnabled)
                    ? MouseSettings.scaleDelayWalkReturn(Math.max(1, delay))
                    : MouseSettings.scaleDelay(Math.max(2, delay));
            sleep(wait);
        }
        return true;
    }

    /**
     * Resample op afstand tot target (Storm resamplePath(points, target)).
     */
    public static List<java.awt.Point> resamplePath(List<java.awt.Point> points, java.awt.Point targetPoint) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        int dist = 40;
        if (targetPoint != null) {
            java.awt.Point last = points.get(points.size() - 1);
            dist = Math.max(8, (int) Math.hypot(
                    targetPoint.x - Mouse.getX(),
                    targetPoint.y - Mouse.getY()));
            if (last != null) {
                dist = Math.max(dist, (int) Math.hypot(targetPoint.x - last.x, targetPoint.y - last.y));
            }
        }
        int count = Math.max(MIN_PATH_POINTS, (int) Math.round(dist * PATH_DENSITY));
        count = Math.min(80, count);
        return resamplePath(points, count);
    }

    /**
     * Even spacing langs padlengte (Storm resamplePath(points, targetPointCount)).
     */
    public static List<java.awt.Point> resamplePath(List<java.awt.Point> points, int targetPointCount) {
        if (points == null || points.size() < 2 || targetPointCount < 2 || points.size() >= targetPointCount) {
            return points == null ? Collections.emptyList() : new ArrayList<>(points);
        }
        double[] cum = new double[points.size()];
        cum[0] = 0;
        for (int i = 1; i < points.size(); i++) {
            java.awt.Point a = points.get(i - 1);
            java.awt.Point b = points.get(i);
            cum[i] = cum[i - 1] + Math.hypot(b.x - a.x, b.y - a.y);
        }
        double total = cum[cum.length - 1];
        if (total < 1) {
            return new ArrayList<>(points);
        }
        List<java.awt.Point> out = new ArrayList<>(targetPointCount);
        out.add(new java.awt.Point(points.get(0)));
        for (int i = 1; i < targetPointCount - 1; i++) {
            double want = total * i / (targetPointCount - 1);
            int seg = 1;
            while (seg < cum.length - 1 && cum[seg] < want) {
                seg++;
            }
            double segStart = cum[seg - 1];
            double segLen = cum[seg] - segStart;
            double f = segLen < 0.001 ? 0 : (want - segStart) / segLen;
            java.awt.Point a = points.get(seg - 1);
            java.awt.Point b = points.get(seg);
            out.add(new java.awt.Point(
                    (int) Math.round(a.x + (b.x - a.x) * f),
                    (int) Math.round(a.y + (b.y - a.y) * f)));
        }
        out.add(new java.awt.Point(points.get(points.size() - 1)));
        return out;
    }

    /**
     * Interact op bekende canvas-coords (al opgevraagd via ClickPoints / snapshot).
     */
    /**
     * Interact op bekende canvas-coords (al opgevraagd via ClickPoints / snapshot).
     * @param allowUi {@code true} voor bank/deposit-box widgets (ligt in inv-zone)
     */
    public static boolean interactAt(Point canvasPoint, boolean allowUi) {
        if (canvasPoint == null || canvasPoint.getX() < 0 || canvasPoint.getY() < 0) {
            return false;
        }
        if (!allowUi && net.storm.sdk.interact.UiClickGuard.isBlocked(canvasPoint)) {
            log.info("interactAt BLOCKED by UI {},{}", canvasPoint.getX(), canvasPoint.getY());
            return false;
        }
        log.info("interactAt canvas {},{} uiOk={}", canvasPoint.getX(), canvasPoint.getY(), allowUi);
        if (!moveTo(canvasPoint)) {
            return false;
        }
        sleep(MouseSettings.scaleDelay(40 + ThreadLocalRandom.current().nextInt(90)));
        return Mouse.clickOnly(canvasPoint.getX(), canvasPoint.getY(), true);
    }

    public static boolean interactAt(Point canvasPoint) {
        return interactAt(canvasPoint, false);
    }

    /**
     * Storm-stijl interact via muis: clickpoint (client-thread) → pad → left click.
     */
    public static boolean interactNpc(INPC npc, String action) {
        if (npc == null) {
            return false;
        }
        Point p = ClickPoints.forNpc(npc);
        if (p == null) {
            log.warn("interactNpc: geen coords voor {}", npc.getName());
            return false;
        }
        return interactAt(p);
    }

    public static boolean interactTileObject(TileObject obj) {
        Point p = ClickPoints.forTileObject(obj);
        if (p == null) {
            return false;
        }
        if (!moveTo(p)) {
            return false;
        }
        sleep(40 + ThreadLocalRandom.current().nextInt(80));
        return Mouse.clickOnly(p.getX(), p.getY(), true);
    }

    public static boolean interactWidget(Widget w) {
        Point p = ClickPoints.forWidget(w);
        if (p == null) {
            return false;
        }
        if (!moveTo(p)) {
            return false;
        }
        sleep(30 + ThreadLocalRandom.current().nextInt(60));
        return Mouse.clickOnly(p.getX(), p.getY(), true);
    }

    public static boolean interactInventorySlot(int slot) {
        Point p = ClickPoints.forInventorySlot(slot);
        if (p == null) {
            return false;
        }
        if (!moveTo(p)) {
            return false;
        }
        sleep(30 + ThreadLocalRandom.current().nextInt(60));
        return Mouse.clickOnly(p.getX(), p.getY(), true);
    }

    private static void sleep(long ms) {
        try {
            net.storm.sdk.bot.BotRuntime.heartbeat();
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
