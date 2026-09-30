package net.storm.sdk.input;

import net.runelite.api.Client;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.input.MouseSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Meerdere muis-backends om te testen welke de game accepteert.
 */
public final class Mouse {

    private static final Logger log = LoggerFactory.getLogger(Mouse.class);

    private static volatile int lastX = 100;
    private static volatile int lastY = 100;
    private static volatile int targetX = -1;
    private static volatile int targetY = -1;
    private static volatile String lastAction = "";
    private static volatile MouseBackend backend = MouseBackend.CANVAS_EDT;
    private static volatile String lastProof = "-";
    private static volatile Robot robot;
    /** AWT down-masks currently held (for MOUSE_DRAGGED during MMB camera). */
    private static volatile int buttonsDownMask = 0;

    /**
     * Eén muis-actie tegelijk: walk-klik vs AntiBan-fidget mogen niet door elkaar heen.
     */
    private static final ReentrantLock ACTION_LOCK = new ReentrantLock();
    private static final AtomicBoolean NATIVE_GUARD_INSTALLED = new AtomicBoolean(false);
    private static volatile long lastNativeDropLogMs;

    private Mouse() {
    }

    /** Houdt de muis vast tot {@link Session#close()} — reentrant per thread. */
    public static Session session() {
        ACTION_LOCK.lock();
        return new Session();
    }

    /** Fidget: sla over als een walk-klik de muis al vasthoudt. */
    public static Session trySession() {
        if (!ACTION_LOCK.tryLock()) {
            return null;
        }
        return new Session();
    }

    /** Walk-loop: niet eindeloos wachten op fidget (dat was hop-uitlopen tot stilstand). */
    public static Session trySession(long waitMs) {
        try {
            if (ACTION_LOCK.tryLock(Math.max(0L, waitMs), TimeUnit.MILLISECONDS)) {
                return new Session();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public static boolean isBusy() {
        return ACTION_LOCK.isLocked();
    }

    public static final class Session implements AutoCloseable {
        private boolean closed;

        private Session() {
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (ACTION_LOCK.isHeldByCurrentThread()) {
                ACTION_LOCK.unlock();
            }
        }
    }

    public static void setBackend(MouseBackend b) {
        if (b == null) {
            return;
        }
        if (MouseSettings.neverStealFocus() && b.stealsFocus()) {
            log.warn("Mouse backend {} geblokkeerd (neverStealFocus) → CANVAS_EDT", b.name());
            backend = MouseBackend.CANVAS_EDT;
            lastAction = "backend blocked → CANVAS_EDT";
            return;
        }
        backend = b;
        lastAction = "backend=" + b.name();
        log.info("Mouse backend → {}", b.label());
    }

    /** True only if Robot/focus is allowed (never, while neverStealFocus is on). */
    private static boolean allowOsMouse() {
        return !MouseSettings.neverStealFocus()
                && (backend == MouseBackend.AWT_ROBOT || backend == MouseBackend.ROBOT_FOCUS);
    }

    public static MouseBackend getBackend() {
        return backend;
    }

    public static int getX() {
        return lastX;
    }

    public static int getY() {
        return lastY;
    }

    public static int getTargetX() {
        return targetX;
    }

    public static int getTargetY() {
        return targetY;
    }

    public static String getLastAction() {
        return lastAction != null ? lastAction : "";
    }

    /** Vergelijking onze coords vs client.getMouseCanvasPosition() na een actie. */
    public static String getLastProof() {
        return lastProof != null ? lastProof : "-";
    }

    public static Point getPosition() {
        return new Point(lastX, lastY);
    }

    /**
     * Zet de game-cursor op een canvas-punt <b>vóór</b> invoke/menuAction.
     * Anders blijft {@code getMouseCanvasPosition()} op 0,0 en tekent OSRS het
     * rode klik-kruisje linksboven.
     * <p>
     * Gebruikt screen-coords in het {@link MouseEvent} (niet alleen AWT-x/y):
     * de Jagex {@code MouseHandler} negeert events zonder geldige screen-punt.
     */
    public static boolean parkOnCanvas(int x, int y) {
        if (x < 8 || y < 8) {
            return false;
        }
        Canvas canvas = canvas();
        if (canvas == null) {
            return false;
        }
        if (x >= canvas.getWidth() || y >= canvas.getHeight()) {
            return false;
        }
        targetX = x;
        targetY = y;
        AtomicBoolean ok = new AtomicBoolean(false);
        runMaybeEdt(() -> {
            try {
                Point screen = new Point(x, y);
                SwingUtilities.convertPointToScreen(screen, canvas);
                long t = System.currentTimeMillis();
                canvas.dispatchEvent(new MouseEvent(
                        canvas, MouseEvent.MOUSE_MOVED, t, 0, x, y, screen.x, screen.y, 0, false, 0));
                lastX = x;
                lastY = y;
                ok.set(true);
            } catch (Throwable ignored) {
            }
        }, true);
        if (!ok.get()) {
            return moveRaw(x, y);
        }
        lastAction = "park " + x + "," + y;
        lastProof = "ours=" + lastX + "," + lastY + " game=" + fmt(gameMouse());
        return true;
    }

    /**
     * Test-klik midden op canvas (ongeacht cows) — om backends te vergelijken.
     */
    public static boolean testClickCenter() {
        Canvas canvas = canvas();
        if (canvas == null) {
            lastAction = "test: geen canvas";
            return false;
        }
        int x = Math.max(10, canvas.getWidth() / 2);
        int y = Math.max(10, canvas.getHeight() / 2);
        return moveAndClick(x, y, "TEST-center");
    }

    /**
     * Beweeg (Bezier) naar een random canvas-punt — optioneel klikken.
     * Dit is de basis anti-ban / test-move (niet altijd midden).
     */
    public static boolean moveRandom(boolean click) {
        Canvas canvas = canvas();
        if (canvas == null) {
            lastAction = "random: geen canvas";
            return false;
        }
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (w < 40 || h < 40) {
            lastAction = "random: canvas te klein";
            return false;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // Vermijd randen / UI-randen
        int x = 30 + r.nextInt(Math.max(1, w - 60));
        int y = 30 + r.nextInt(Math.max(1, h - 80));
        targetX = x;
        targetY = y;
        boolean moved = movePathTo(x, y, null);
        if (!moved) {
            lastAction = "FAIL random move @" + x + "," + y;
            return false;
        }
        lastAction = "random->" + x + "," + y;
        lastProof = "ours=" + lastX + "," + lastY + " game=" + fmt(gameMouse());
        if (!click) {
            return true;
        }
        sleep(MouseSettings.scaleDelay(35 + r.nextInt(80)));
        boolean ok = clickOnly(x, y, true);
        lastAction = (ok ? "OK " : "FAIL ") + "random-click @" + x + "," + y;
        lastProof = "ours=" + lastX + "," + lastY + " game=" + fmt(gameMouse());
        return ok;
    }

    /** Alleen bewegen, geen klik. */
    public static boolean moveRandom() {
        return moveRandom(false);
    }

    public static boolean moveAndClick(int toX, int toY, String label) {
        return moveAndClick(toX, toY, label, null, null);
    }

    /**
     * @param menuNpcIndex optioneel: bij MENU_ONLY backend → menu Attack op dit NPC-index
     */
    public static boolean moveAndClick(int toX, int toY, String label, Integer menuNpcIndex, String menuNpcName) {
        Canvas canvas = canvas();
        if (canvas == null && backend != MouseBackend.MENU_ONLY) {
            lastAction = "geen canvas";
            return false;
        }
        if (canvas != null && (toX < 0 || toY < 0 || toX >= canvas.getWidth() || toY >= canvas.getHeight())) {
            if (backend != MouseBackend.MENU_ONLY) {
                lastAction = "off-screen " + toX + "," + toY;
                return false;
            }
        }

        if (backend == MouseBackend.MENU_ONLY) {
            boolean okMenu = doMenuOnly(menuNpcIndex, menuNpcName);
            lastX = toX;
            lastY = toY;
            targetX = toX;
            targetY = toY;
            lastAction = (okMenu ? "OK " : "FAIL ") + "MENU_ONLY " + label;
            lastProof = "game=" + fmt(gameMouse());
            return okMenu;
        }
        targetX = toX;
        targetY = toY;
        Point beforeGame = gameMouse();
        boolean moved = movePathTo(toX, toY, null);
        if (!moved) {
            lastAction = "FAIL path " + label;
            return false;
        }
        sleep(35 + (int) (Math.random() * 80));
        boolean ok = clickOnly(toX, toY, true);
        lastAction = (ok ? "OK " : "FAIL ") + backend.name() + " path+click " + (label != null ? label : "") + " @" + toX + "," + toY;
        lastProof = "ours=" + lastX + "," + lastY + " before=" + fmt(beforeGame) + " after=" + fmt(gameMouse());
        log.info("[Mouse] {} | {}", lastAction, lastProof);
        return ok;
    }

    public static boolean smoothMoveTo(int toX, int toY) {
        // Voor backends die move apart ondersteunen — eenvoudige stap naar doel
        return movePathTo(toX, toY, new net.storm.sdk.interact.mouse.BezierCurveMouseMovement());
    }

    public static void fidget() {
        if (!MouseSettings.isRandomMoveEnabled()) {
            return;
        }
        moveRandom(false);
    }

    public static void click(int x, int y) {
        moveAndClick(x, y, "click");
    }


    /** Storm-stijl: Bezier/linear pad (alleen moves). */
    public static boolean movePathTo(int toX, int toY, net.storm.api.interact.mouse.MouseMovementStrategy strategy) {
        Canvas canvas = canvas();
        if (canvas == null) {
            lastAction = "geen canvas";
            return false;
        }
        if (toX < 0 || toY < 0 || toX >= canvas.getWidth() || toY >= canvas.getHeight()) {
            lastAction = "off-screen " + toX + "," + toY;
            return false;
        }
        targetX = toX;
        targetY = toY;
        if (strategy == null) {
            strategy = new net.storm.sdk.interact.mouse.BezierCurveMouseMovement();
        }
        if (backend == MouseBackend.ROBOT_FOCUS && allowOsMouse()) {
            runMaybeEdt(() -> {
                canvas.requestFocus();
                canvas.requestFocusInWindow();
            }, true);
            sleep(50);
        }
        // Prefer MouseManager.moveTo (strategy + resample + ease) — same path cow bot uses
        boolean ok = net.storm.sdk.interact.mouse.MouseManager.moveTo(
                new net.runelite.api.Point(toX, toY));
        lastAction = (ok ? "path->" : "FAIL path->") + toX + "," + toY;
        lastProof = "ours=" + lastX + "," + lastY + " game=" + fmt(gameMouse());
        return ok;
    }

    public static boolean moveRaw(int x, int y) {
        if (!MouseSettings.mouseEventsAllowed()) {
            lastAction = "move blocked invoke-only";
            return false;
        }
        installNativeCanvasGuard();
        Canvas canvas = canvas();
        if (canvas == null) {
            return false;
        }
        boolean useRobot = allowOsMouse();
        if (useRobot) {
            try {
                Point screen = new Point(x, y);
                SwingUtilities.convertPointToScreen(screen, canvas);
                robot().mouseMove(screen.x, screen.y);
                lastX = x;
                lastY = y;
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        boolean onEdt = backend != MouseBackend.CANVAS_DISPATCH;
        boolean screenCtor = backend == MouseBackend.CANVAS_EDT_SCREEN;
        AtomicBoolean ok = new AtomicBoolean(false);
        runMaybeEdt(() -> {
            try {
                long t = System.currentTimeMillis();
                if (screenCtor) {
                    Point screen = new Point(x, y);
                    SwingUtilities.convertPointToScreen(screen, canvas);
                    canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, t, 0, x, y, screen.x, screen.y, 0, false, 0));
                } else {
                    dispatchSimple(canvas, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON, 0, t);
                }
                lastX = x;
                lastY = y;
                ok.set(true);
            } catch (Throwable ignored) {
            }
        }, onEdt);
        return ok.get();
    }

    public static boolean clickOnly(int x, int y, boolean left) {
        if (!MouseSettings.mouseEventsAllowed()) {
            lastAction = "blocked invoke-only @" + x + "," + y;
            return false;
        }
        installNativeCanvasGuard();
        boolean ownLock = !ACTION_LOCK.isHeldByCurrentThread();
        Session session = null;
        if (ownLock) {
            boolean clientThread;
            try {
                clientThread = Static.isOnClientThread();
            } catch (Throwable t) {
                clientThread = false;
            }
            session = clientThread ? trySession() : session();
        }
        try {
            return clickOnlyAtomic(x, y, left);
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    /**
     * Move + press + release + click in één EDT-runnable (optie B).
     * Geen sleep ertussen — anders kan de pc-muis tussen move en klik glippen.
     */
    private static boolean clickOnlyAtomic(int x, int y, boolean left) {
        Canvas canvas = canvas();
        if (canvas == null) {
            return false;
        }
        int button = left ? MouseEvent.BUTTON1 : MouseEvent.BUTTON3;
        int down = left ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
        int mask = left ? InputEvent.BUTTON1_MASK : InputEvent.BUTTON3_MASK;
        boolean onEdt = backend != MouseBackend.CANVAS_DISPATCH;
        AtomicBoolean ok = new AtomicBoolean(false);
        runMaybeEdt(() -> {
            try {
                long t = System.currentTimeMillis();
                dispatchSimple(canvas, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON, 0, t);
                dispatchSimple(canvas, MouseEvent.MOUSE_PRESSED, x, y, button, down, t);
                dispatchSimple(canvas, MouseEvent.MOUSE_RELEASED, x, y, button, mask, t);
                dispatchSimple(canvas, MouseEvent.MOUSE_CLICKED, x, y, button, mask, t);
                lastX = x;
                lastY = y;
                ok.set(true);
            } catch (Throwable ignored) {
            }
        }, onEdt);
        return ok.get();
    }


    // --- backends ---

    private static boolean doCanvasDispatch(int x, int y, boolean onEdt) {
        Canvas canvas = canvas();
        if (canvas == null) {
            return false;
        }
        Runnable r = () -> {
            long t = System.currentTimeMillis();
            dispatchSimple(canvas, MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON, 0, t);
            sleep(15);
            dispatchSimple(canvas, MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK, t + 20);
            sleep(30);
            dispatchSimple(canvas, MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON1, InputEvent.BUTTON1_MASK, t + 50);
            dispatchSimple(canvas, MouseEvent.MOUSE_CLICKED, x, y, MouseEvent.BUTTON1, InputEvent.BUTTON1_MASK, t + 50);
            lastX = x;
            lastY = y;
        };
        return runMaybeEdt(r, onEdt);
    }

    private static boolean doCanvasEdtScreen(int x, int y) {
        Canvas canvas = canvas();
        if (canvas == null) {
            return false;
        }
        AtomicBoolean ok = new AtomicBoolean(false);
        runMaybeEdt(() -> {
            try {
                Point screen = new Point(x, y);
                SwingUtilities.convertPointToScreen(screen, canvas);
                long t = System.currentTimeMillis();
                // Constructor met screen coords
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, t, 0, x, y, screen.x, screen.y, 0, false, 0));
                sleep(15);
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, t + 20, InputEvent.BUTTON1_DOWN_MASK, x, y, screen.x, screen.y, 1, false, MouseEvent.BUTTON1));
                sleep(30);
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, t + 50, InputEvent.BUTTON1_MASK, x, y, screen.x, screen.y, 1, false, MouseEvent.BUTTON1));
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, t + 50, InputEvent.BUTTON1_MASK, x, y, screen.x, screen.y, 1, false, MouseEvent.BUTTON1));
                lastX = x;
                lastY = y;
                ok.set(true);
            } catch (Throwable t) {
                log.warn("CANVAS_EDT_SCREEN fail: {}", t.toString());
            }
        }, true);
        return ok.get();
    }

    private static boolean doRobot(int canvasX, int canvasY, boolean focusFirst) {
        if (MouseSettings.neverStealFocus()) {
            log.debug("doRobot blocked (neverStealFocus) → canvas click");
            return doCanvasDispatch(canvasX, canvasY, true);
        }
        Canvas canvas = canvas();
        if (canvas == null) {
            return false;
        }
        try {
            Robot r = robot();
            if (focusFirst) {
                runMaybeEdt(() -> {
                    canvas.requestFocus();
                    canvas.requestFocusInWindow();
                }, true);
                sleep(80);
            }
            Point screen = new Point(canvasX, canvasY);
            SwingUtilities.convertPointToScreen(screen, canvas);
            r.mouseMove(screen.x, screen.y);
            lastX = canvasX;
            lastY = canvasY;
            sleep(40);
            r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            sleep(35);
            r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            return true;
        } catch (Throwable t) {
            log.warn("Robot fail: {}", t.toString());
            lastAction = "robot fail: " + t.getMessage();
            return false;
        }
    }

    private static boolean doMenuOnly(Integer npcIndex, String npcName) {
        if (npcIndex == null) {
            lastAction = "MENU_ONLY zonder npc";
            return false;
        }
        // Gebruik bestaande probe/invoke
        Client c = Static.getClient();
        if (c == null) {
            return false;
        }
        AtomicBoolean ok = new AtomicBoolean(false);
        Static.runOnClientThread(() -> {
            try {
                for (net.runelite.api.NPC n : c.getNpcs()) {
                    if (n != null && n.getIndex() == npcIndex) {
                        ok.set(MenuInteract.interactNpc(n, "Attack"));
                        return;
                    }
                }
            } catch (Throwable t) {
                log.warn("MENU_ONLY: {}", t.toString());
            }
        });
        sleep(100);
        return ok.get();
    }

    /**
     * Jagex MouseHandler negeert events zonder geldige screen-punt. Zonder xAbs/yAbs
     * wint de echte pc-muis (die wél screen-coords heeft).
     */
    private static void dispatchSimple(Component target, int id, int x, int y, int button, int modifiers, long when) {
        Point screen = new Point(x, y);
        try {
            SwingUtilities.convertPointToScreen(screen, target);
        } catch (Throwable ignored) {
        }
        int clicks = (id == MouseEvent.MOUSE_PRESSED
                || id == MouseEvent.MOUSE_RELEASED
                || id == MouseEvent.MOUSE_CLICKED) ? 1 : 0;
        int btn = button == 0 ? MouseEvent.NOBUTTON : button;
        MouseEvent ev = new MouseEvent(
                target,
                id,
                when,
                modifiers,
                x,
                y,
                screen.x,
                screen.y,
                clicks,
                false,
                btn
        );
        target.dispatchEvent(ev);
    }

    /**
     * Eenmalig: OS-muis-events op het game-canvas droppen terwijl de bot klikt.
     * Synthetic {@code canvas.dispatchEvent} gaat niet via deze queue — die blijven werken.
     */
    public static void installNativeCanvasGuard() {
        if (!NATIVE_GUARD_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Runnable install = () -> {
            try {
                Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
                    @Override
                    protected void dispatchEvent(AWTEvent event) {
                        if (shouldDropNativeCanvasMouse(event)) {
                            noteNativeDrop();
                            return;
                        }
                        super.dispatchEvent(event);
                    }
                });
            } catch (Throwable t) {
                log.warn("Native mouse guard: {}", t.toString());
                NATIVE_GUARD_INSTALLED.set(false);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            install.run();
        } else {
            SwingUtilities.invokeLater(install);
        }
    }

    private static boolean shouldDropNativeCanvasMouse(AWTEvent event) {
        if (!(event instanceof MouseEvent)) {
            return false;
        }
        if (!blockNativeCanvasMouse()) {
            return false;
        }
        Object src = event.getSource();
        Canvas game = canvas();
        return game != null && src == game;
    }

    /**
     * Alleen tijdens een bot-muisactie (lock). Bot aan = pc-muis blijft bruikbaar.
     */
    public static boolean blockNativeCanvasMouse() {
        return ACTION_LOCK.isLocked();
    }

    private static void noteNativeDrop() {
        long now = System.currentTimeMillis();
        if (now - lastNativeDropLogMs < 1500L) {
            return;
        }
        lastNativeDropLogMs = now;
        try {
            net.storm.sdk.bot.BotRuntime.logConsole(
                    "[Mouse] OS-muis even genegeerd tijdens bot-klik");
        } catch (Throwable ignored) {
        }
    }

    private static boolean runMaybeEdt(Runnable r, boolean onEdt) {
        if (!onEdt || SwingUtilities.isEventDispatchThread()) {
            try {
                r.run();
                return true;
            } catch (Throwable t) {
                log.warn("mouse run fail: {}", t.toString());
                return false;
            }
        }
        AtomicBoolean ok = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    r.run();
                    ok.set(true);
                } catch (Throwable t) {
                    log.warn("EDT mouse fail: {}", t.toString());
                } finally {
                    latch.countDown();
                }
            });
        } catch (Exception e) {
            log.warn("invokeAndWait: {}", e.toString());
            latch.countDown();
        }
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ok.get();
    }

    private static Point gameMouse() {
        try {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            net.runelite.api.Point p = c.getMouseCanvasPosition();
            return p != null ? new Point(p.getX(), p.getY()) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Storm-compat: synthetic mouse move on canvas (no click).
     * While a button is held (e.g. MMB camera), emits {@link MouseEvent#MOUSE_DRAGGED}
     * with the down-mask — otherwise camera rotation does not register.
     *
     * @param button unused (API compat); use 0
     */
    public static void moved(int x, int y, Canvas canvas, long time) {
        if (!MouseSettings.mouseEventsAllowed()) {
            return;
        }
        if (canvas == null) {
            canvas = canvas();
        }
        if (canvas == null) {
            return;
        }
        final Canvas c = canvas;
        final int mods = buttonsDownMask;
        final int id = mods != 0 ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED;
        runMaybeEdt(() -> {
            try {
                dispatchSimple(c, id, x, y, MouseEvent.NOBUTTON, mods, time);
                lastX = x;
                lastY = y;
            } catch (Throwable ignored) {
            }
        }, true);
    }

    /**
     * Storm-compat: mouse button press. {@code button} = 1 left, 2 middle, 3 right.
     */
    public static void pressed(int x, int y, Canvas canvas, long time, int button) {
        if (!MouseSettings.mouseEventsAllowed()) {
            return;
        }
        if (canvas == null) {
            canvas = canvas();
        }
        if (canvas == null) {
            return;
        }
        final Canvas c = canvas;
        final int btn = buttonToAwt(button);
        final int down = buttonToDownMask(button);
        buttonsDownMask |= down;
        runMaybeEdt(() -> {
            try {
                dispatchSimple(c, MouseEvent.MOUSE_PRESSED, x, y, btn, down, time);
                lastX = x;
                lastY = y;
            } catch (Throwable ignored) {
            }
        }, true);
    }

    /**
     * Storm-compat: mouse button release.
     */
    public static void released(int x, int y, Canvas canvas, long time, int button) {
        if (!MouseSettings.mouseEventsAllowed()) {
            return;
        }
        if (canvas == null) {
            canvas = canvas();
        }
        if (canvas == null) {
            return;
        }
        final Canvas c = canvas;
        final int btn = buttonToAwt(button);
        final int mask = buttonToMask(button);
        final int down = buttonToDownMask(button);
        buttonsDownMask &= ~down;
        runMaybeEdt(() -> {
            try {
                dispatchSimple(c, MouseEvent.MOUSE_RELEASED, x, y, btn, mask, time);
                lastX = x;
                lastY = y;
            } catch (Throwable ignored) {
            }
        }, true);
    }

    /**
     * Synthetic MMB camera drag (Storm-compat) — no {@link Robot}, no {@code requestFocus}.
     * Does not steal OS focus / real cursor. Camera may only move if client accepts canvas events.
     */
    public static boolean mmbCameraDragSynthetic(
            int startX, int startY,
            int endX, int endY,
            int steps,
            int stepDelayMin,
            int stepDelayMax
    ) {
        return mmbCameraDragHuman(startX, startY, endX, endY, steps, stepDelayMin, stepDelayMax);
    }

    /**
     * Human MMB drag: curved path (geen rechte lijn), eindpunt mag offscreen,
     * yaw+pitch in één beweging. Snelheid varieert langs het pad.
     */
    public static boolean mmbCameraDragHuman(
            int startX, int startY,
            int endX, int endY,
            int steps,
            int stepDelayMin,
            int stepDelayMax
    ) {
        if (!MouseSettings.mouseEventsAllowed()) {
            lastAction = "mmb blocked invoke-only";
            return false;
        }
        Canvas canvas = canvas();
        if (canvas == null) {
            return false;
        }
        try {
            java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
            // Bezier control points — organische boog i.p.v. rechte lijn
            double mx = (startX + endX) / 2.0;
            double my = (startY + endY) / 2.0;
            double dx = endX - startX;
            double dy = endY - startY;
            double len = Math.max(40, Math.hypot(dx, dy));
            double nx = -dy / len;
            double ny = dx / len;
            double bulge1 = (r.nextDouble() * 0.35 + 0.12) * len * (r.nextBoolean() ? 1 : -1);
            double bulge2 = (r.nextDouble() * 0.28 + 0.08) * len * (r.nextBoolean() ? 1 : -1);
            double c1x = startX + dx * 0.28 + nx * bulge1;
            double c1y = startY + dy * 0.28 + ny * bulge1;
            double c2x = startX + dx * 0.72 + nx * bulge2;
            double c2y = startY + dy * 0.72 + ny * bulge2;
            // Extra jitter op controls
            c1x += r.nextInt(-18, 19);
            c1y += r.nextInt(-14, 15);
            c2x += r.nextInt(-18, 19);
            c2y += r.nextInt(-14, 15);

            moved(startX, startY, canvas, System.currentTimeMillis());
            sleep(50 + r.nextInt(90));
            pressed(startX, startY, canvas, System.currentTimeMillis(), 2);
            sleep(40 + r.nextInt(70));

            int n = Math.max(10, steps);
            int curX = startX;
            int curY = startY;
            for (int i = 1; i <= n; i++) {
                double t = (double) i / n;
                // Ease-in-out + cubic Bezier
                double u = t * t * (3 - 2 * t);
                double omt = 1 - u;
                double bx = omt * omt * omt * startX
                        + 3 * omt * omt * u * c1x
                        + 3 * omt * u * u * c2x
                        + u * u * u * endX;
                double by = omt * omt * omt * startY
                        + 3 * omt * omt * u * c1y
                        + 3 * omt * u * u * c2y
                        + u * u * u * endY;
                // Micro noise (menselijke trilling)
                int tx = (int) Math.round(bx + r.nextDouble(-1.2, 1.2));
                int ty = (int) Math.round(by + r.nextDouble(-1.0, 1.0));
                if (tx != curX || ty != curY) {
                    moved(tx, ty, canvas, System.currentTimeMillis());
                    curX = tx;
                    curY = ty;
                }
                // Sneller in het midden van de stroke (meer camera-momentum)
                double speedBias = 0.55 + 0.45 * Math.sin(Math.PI * t);
                int dmin = Math.max(4, (int) (stepDelayMin * speedBias));
                int dmax = Math.max(dmin + 1, (int) (stepDelayMax * speedBias));
                sleep(dmin + r.nextInt(dmax - dmin + 1));
            }
            sleep(50 + r.nextInt(90));
            released(curX, curY, canvas, System.currentTimeMillis(), 2);
            lastAction = "mmb-human-curve";
            lastProof = "human MMB " + startX + "," + startY + "→" + endX + "," + endY
                    + " (offscreen ok)";
            return true;
        } catch (Throwable t) {
            log.warn("mmbCameraDragHuman fail: {}", t.toString());
            try {
                released(startX, startY, canvas, System.currentTimeMillis(), 2);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    /**
     * MMB camera drag. With {@link MouseSettings#neverStealFocus()} always uses synthetic
     * canvas events (no Robot / requestFocus) — window stays inactive.
     */
    public static boolean mmbCameraDrag(
            int startX, int startY,
            int endX, int endY,
            int steps,
            int stepDelayMin,
            int stepDelayMax
    ) {
        return mmbCameraDrag(startX, startY, endX, endY, steps, stepDelayMin, stepDelayMax, false);
    }

    public static boolean mmbCameraDrag(
            int startX, int startY,
            int endX, int endY,
            int steps,
            int stepDelayMin,
            int stepDelayMax,
            boolean stealFocus
    ) {
        // Hard policy: never activate the game window
        if (MouseSettings.neverStealFocus() || !stealFocus) {
            return mmbCameraDragSynthetic(startX, startY, endX, endY, steps, stepDelayMin, stepDelayMax);
        }
        Canvas canvas = canvas();
        if (canvas == null) {
            return false;
        }
        try {
            Robot r = robot();
            runMaybeEdt(() -> {
                canvas.requestFocus();
                canvas.requestFocusInWindow();
            }, true);
            sleep(60);

            Point start = new Point(startX, startY);
            SwingUtilities.convertPointToScreen(start, canvas);
            r.mouseMove(start.x, start.y);
            lastX = startX;
            lastY = startY;
            moved(startX, startY, canvas, System.currentTimeMillis());
            sleep(80 + (int) (Math.random() * 120));

            r.mousePress(InputEvent.BUTTON2_DOWN_MASK);
            pressed(startX, startY, canvas, System.currentTimeMillis(), 2);
            sleep(60 + (int) (Math.random() * 80));

            int n = Math.max(6, steps);
            int curX = startX;
            int curY = startY;
            for (int i = 1; i <= n; i++) {
                double t = (double) i / n;
                double eased = (1.0 - Math.cos(t * Math.PI)) / 2.0;
                int tx = startX + (int) ((endX - startX) * eased);
                int ty = startY + (int) ((endY - startY) * eased);
                Point sp = new Point(tx, ty);
                SwingUtilities.convertPointToScreen(sp, canvas);
                r.mouseMove(sp.x, sp.y);
                moved(tx, ty, canvas, System.currentTimeMillis());
                curX = tx;
                curY = ty;
                lastX = tx;
                lastY = ty;
                int dmin = Math.max(5, stepDelayMin);
                int dmax = Math.max(dmin + 1, stepDelayMax);
                sleep(dmin + (int) (Math.random() * (dmax - dmin + 1)));
            }

            sleep(80 + (int) (Math.random() * 120));
            r.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
            released(curX, curY, canvas, System.currentTimeMillis(), 2);
            lastAction = "mmb-camera-drag";
            lastProof = "robot MMB " + startX + "," + startY + "→" + endX + "," + endY;
            return true;
        } catch (Throwable t) {
            log.warn("mmbCameraDrag fail: {}", t.toString());
            try {
                robot().mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    private static int buttonToAwt(int button) {
        if (button == 2) {
            return MouseEvent.BUTTON2;
        }
        if (button == 3) {
            return MouseEvent.BUTTON3;
        }
        return MouseEvent.BUTTON1;
    }

    private static int buttonToDownMask(int button) {
        if (button == 2) {
            return InputEvent.BUTTON2_DOWN_MASK;
        }
        if (button == 3) {
            return InputEvent.BUTTON3_DOWN_MASK;
        }
        return InputEvent.BUTTON1_DOWN_MASK;
    }

    private static int buttonToMask(int button) {
        if (button == 2) {
            return InputEvent.BUTTON2_MASK;
        }
        if (button == 3) {
            return InputEvent.BUTTON3_MASK;
        }
        return InputEvent.BUTTON1_MASK;
    }

    private static String fmt(Point p) {
        return p == null ? "?" : p.x + "," + p.y;
    }

    private static Canvas canvas() {
        try {
            return net.storm.sdk.game.Client.getCanvas();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Robot robot() throws AWTException {
        if (robot == null) {
            robot = new Robot();
            robot.setAutoDelay(5);
        }
        return robot;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
