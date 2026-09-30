package net.storm.sdk.utils;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Client;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.input.MouseSettings;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.items.Bank;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.WalkClickHelper;
import net.storm.sdk.movement.WalkClickSettings;
import net.storm.sdk.widgets.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Storm/CombatBot-style anti-ban (ported subset):
 * camera (arrow keys + MMB drag), idle, random mouse, tab-glance, misclick,
 * continuous mouse-fidget worker. Skips during dialog/bank and right after bot clicks.
 * <p>
 * Walk-proximity: zware antiban alleen op lange afstanden. Dicht bij bestemming
 * ({@value #NEAR_DEST_TILES} tiles) → geen blocking idle/camera (voorkomt stilstand
 * bij bank / eind van pad).
 */
public final class AntiBan {

    private static final Logger log = LoggerFactory.getLogger(AntiBan.class);
    private static final AntiBan INSTANCE = new AntiBan();

    /**
     * Binnen deze Chebyshev-afstand tot walk-doel: geen blocking antiban.
     * Soft zone tot {@link #SOFT_DEST_TILES}: alleen lichte muis, geen idle/tab/misclick.
     */
    public static final int NEAR_DEST_TILES = 50;
    public static final int SOFT_DEST_TILES = 90;

    private static final int CAMERA_KEY_PULSE_MS_MIN = 280;
    private static final int CAMERA_KEY_PULSE_MS_MAX = 420;
    private static final int CAMERA_DOWN_PULSE_MS_MIN = 120;
    private static final int CAMERA_DOWN_PULSE_MS_MAX = 200;

    private static final int[] TAB_GLANCE_F_KEYS = {
            KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F4, KeyEvent.VK_F5,
            KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8
    };

    private enum ActionType { NONE, CAMERA, IDLE, MOUSE, MISCLICK, TAB_GLANCE }
    private enum ProximityBand { FAR, SOFT, NEAR }

    private final Random random = new Random();
    private Instant lastActionTime = Instant.now();
    private int nextActionDelaySec = 20;
    private ActionType lastActionType = ActionType.NONE;
    private boolean forceTopDownNextCamera;
    private long lastTabGlanceMs;
    private long nextTabGlanceCooldownMs = 120_000L;
    private volatile long lastBotActivityMs;
    private volatile String lastActionLabel = "-";
    private MouseFidgetWorker fidgetWorker;
    private KeyboardPanWorker panWorker;
    private long lastProximityLogMs;
    private long lastKeyPanLogMs;

    private AntiBan() {
        nextActionDelaySec = calculateNextDelaySec();
    }

    public static AntiBan get() {
        return INSTANCE;
    }

    public String getLastActionLabel() {
        return lastActionLabel != null ? lastActionLabel : "-";
    }

    /** Mark real bot click/interact — fidget pauses ~600ms. */
    public void markBotActivity() {
        lastBotActivityMs = System.currentTimeMillis();
    }

    public void notifyHandlerAction(String reason, int millisAhead) {
        lastBotActivityMs = System.currentTimeMillis() + Math.max(0, millisAhead - 600);
    }

    public void notifyHandlerAction(String reason) {
        notifyHandlerAction(reason, 1200);
    }

    public void startFidgetWorker() {
        if (!BotRuntime.botEnabled) {
            return; // Start-knop start workers; niet al bij client-boot
        }
        if (AntiBanSettings.mouseFidgetEnabled && fidgetWorker == null) {
            fidgetWorker = new MouseFidgetWorker();
            fidgetWorker.start();
            log.info("[AntiBan] fidget worker started");
        }
        if (AntiBanSettings.keyboardPanEnabled && panWorker == null) {
            panWorker = new KeyboardPanWorker();
            panWorker.start();
            log.info("[AntiBan] keyboard-pan worker started");
        }
    }

    public void stopFidgetWorker() {
        if (fidgetWorker != null) {
            fidgetWorker.shutdown();
            fidgetWorker = null;
            log.info("[AntiBan] fidget worker stopped");
        }
        if (panWorker != null) {
            panWorker.shutdown();
            panWorker = null;
            log.info("[AntiBan] keyboard-pan worker stopped");
        }
    }

    public void restartFidgetWorkerIfNeeded() {
        // Alleen draaien terwijl bot AAN staat — anders starten settings-tick workers opnieuw na Stop
        if (!BotRuntime.botEnabled) {
            stopFidgetWorker();
            return;
        }
        if (AntiBanSettings.mouseFidgetEnabled) {
            if (fidgetWorker == null) {
                fidgetWorker = new MouseFidgetWorker();
                fidgetWorker.start();
                log.info("[AntiBan] fidget worker started");
            }
        } else if (fidgetWorker != null) {
            fidgetWorker.shutdown();
            fidgetWorker = null;
            log.info("[AntiBan] fidget worker stopped");
        }
        if (AntiBanSettings.keyboardPanEnabled) {
            if (panWorker == null) {
                panWorker = new KeyboardPanWorker();
                panWorker.start();
                log.info("[AntiBan] keyboard-pan worker started");
            }
        } else if (panWorker != null) {
            panWorker.shutdown();
            panWorker = null;
            log.info("[AntiBan] keyboard-pan worker stopped");
        }
    }

    /**
     * Storm-style tick: maybe perform one anti-ban action.
     *
     * @return suggested delay ms (&gt;0 if action ran), or 0 if nothing
     */
    public int check() {
        if (!AntiBanSettings.enabled || !BotRuntime.botEnabled) {
            return 0;
        }
        long elapsed = Duration.between(lastActionTime, Instant.now()).getSeconds();
        if (elapsed < nextActionDelaySec) {
            return 0;
        }
        try {
            if (Dialog.isOpen() || Bank.isOpen()) {
                lastActionTime = Instant.now();
                return 0;
            }
        } catch (Throwable ignored) {
        }
        if (System.currentTimeMillis() < lastBotActivityMs + 600) {
            return 0;
        }
        // Tijdens lopen: nooit blocking idle/camera. Random events hebben een eigen plugin.
        if (isActivelyWalking()) {
            lastActionTime = Instant.now();
            return 0;
        }

        ProximityBand band = walkProximityBand();
        if (band == ProximityBand.NEAR) {
            // Dicht bij doel / bank — geen stilstand (idle 1–15s blokkeerde bank-open)
            lastActionTime = Instant.now();
            nextActionDelaySec = Math.max(8, calculateNextDelaySec() / 2);
            maybeLogProximity(band, remainingWalkTiles());
            return 0;
        }

        int delay = performRandomAction(band);
        lastActionTime = Instant.now();
        nextActionDelaySec = calculateNextDelaySec();
        if (delay <= 0) {
            return 0;
        }
        WalkClickSettings.lastDetail = lastActionLabel;
        return Math.max(delay, 200);
    }

    /** Lopen of nog een pad/flag: geen blocking antiban (random events blijven een eigen plugin). */
    private static boolean isActivelyWalking() {
        try {
            if (net.storm.sdk.movement.WorldWalker.isActive()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (MovementHelper.isTravelContext()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Mouse.isBusy()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            WorldPoint dest = MovementHelper.getActiveDestination();
            if (dest != null) {
                // Alleen "lopend" als we nog onderweg zijn — niet stil bij de ster/bank minen
                if (Movement.isMoving() || Movement.getDestination() != null) {
                    return true;
                }
                Players.LocalSnap me = Players.snapshotLocal();
                WorldPoint from = me != null && me.present ? me.worldLocation : null;
                if (from != null && from.getPlane() == dest.getPlane()
                        && from.distanceTo(dest) > 8) {
                    return true;
                }
                // Stil binnen 8 tegels van dest → antiban mag (camera/muis tijdens mine)
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Movement.isMoving()) {
                return true;
            }
            if (Movement.getDestination() != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            if (me != null && me.present && me.moving) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        int rem = remainingWalkTiles();
        return rem > 8;
    }

    /**
     * Resterende walk-afstand (tiles): destination-flag, actief pad, of -1 als geen walk-context.
     */
    public static int remainingWalkTiles() {
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint from = me != null && me.present ? me.worldLocation : null;
        if (from == null) {
            return -1;
        }

        WorldPoint flag = null;
        try {
            flag = Movement.getDestination();
        } catch (Throwable ignored) {
        }
        WorldPoint active = MovementHelper.getActiveDestination();

        int best = Integer.MAX_VALUE;
        if (flag != null && flag.getPlane() == from.getPlane()) {
            best = Math.min(best, from.distanceTo(flag));
        }
        if (active != null) {
            // Plane-verschil: toch Chebyshev op xy (Lumb trap → bank)
            int d = Math.max(Math.abs(from.getX() - active.getX()), Math.abs(from.getY() - active.getY()));
            best = Math.min(best, d);
        }
        try {
            WorldPoint mapDest = net.storm.sdk.movement.WorldWalker.destination();
            if (mapDest != null) {
                int d = Math.max(Math.abs(from.getX() - mapDest.getX()), Math.abs(from.getY() - mapDest.getY()));
                best = Math.min(best, d);
            }
        } catch (Throwable ignored) {
        }

        try {
            TilePath path = MovementHelper.getActivePath();
            if (path != null && !path.isEmpty()) {
                TilePath rem = path.getRemainingPath(from);
                if (rem != null && !rem.isEmpty()) {
                    best = Math.min(best, rem.size());
                }
            }
        } catch (Throwable ignored) {
        }

        // Lumb bank-floor: vlak na trap, pad vaak leeg — toch "near" tot booth
        if (from.getPlane() >= 2
                && from.getX() >= 3195 && from.getX() <= 3220
                && from.getY() >= 3195 && from.getY() <= 3235) {
            int dBooth = Math.max(Math.abs(from.getX() - 3208), Math.abs(from.getY() - 3220));
            best = Math.min(best, dBooth);
        }

        // Actief aan het lopen (flag) zonder ver doel → near
        if (best == Integer.MAX_VALUE && me.moving && flag != null) {
            best = from.distanceTo(flag);
        }

        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private ProximityBand walkProximityBand() {
        int rem = remainingWalkTiles();
        if (rem < 0) {
            return ProximityBand.FAR;
        }
        if (rem <= NEAR_DEST_TILES) {
            return ProximityBand.NEAR;
        }
        if (rem <= SOFT_DEST_TILES) {
            return ProximityBand.SOFT;
        }
        return ProximityBand.FAR;
    }

    private void maybeLogProximity(ProximityBand band, int rem) {
        long now = System.currentTimeMillis();
        if (now - lastProximityLogMs < 4_000L) {
            return;
        }
        lastProximityLogMs = now;
        log.info("[AntiBan] proximity={} rem≈{} — skip blocking", band, rem);
    }

    /** Convenience for loops: run check + legacy camera tick fallback. */
    public static void tick() {
        int d = get().check();
        if (d <= 0) {
            get().maybeLightCameraIfIdle();
        }
    }

    private void maybeLightCameraIfIdle() {
        // no-op when cameraMovement already covered by check(); kept for API compat
    }

    private int performRandomAction() {
        return performRandomAction(ProximityBand.FAR);
    }

    private int performRandomAction(ProximityBand band) {
        // Soft: geen idle / tab / misclick (die blokkeren 1–15s); wel korte camera/muis
        boolean allowIdle = band == ProximityBand.FAR && AntiBanSettings.idleChecks;
        boolean allowTab = band == ProximityBand.FAR && AntiBanSettings.tabGlanceEnabled
                && (System.currentTimeMillis() - lastTabGlanceMs) >= nextTabGlanceCooldownMs;
        boolean allowMisclick = band == ProximityBand.FAR && AntiBanSettings.misClickEnabled;
        boolean allowCamera = AntiBanSettings.cameraMovement && band != ProximityBand.NEAR;
        boolean allowMouse = AntiBanSettings.randomMouseMovement;

        int cameraWeight = band == ProximityBand.SOFT ? 20 : 35;
        int idleWeight = 25;
        int mouseWeight = band == ProximityBand.SOFT ? 40 : 25;
        int misclickWeight = 15;
        int tabWeight = 10;

        int total = 0;
        if (allowCamera) {
            total += cameraWeight;
        }
        if (allowIdle) {
            total += idleWeight;
        }
        if (allowMouse) {
            total += mouseWeight;
        }
        if (allowMisclick) {
            total += misclickWeight;
        }
        if (allowTab) {
            total += tabWeight;
        }
        if (total <= 0) {
            return -1;
        }

        int effective = total;
        boolean skipCamera = lastActionType == ActionType.CAMERA && allowCamera;
        if (skipCamera) {
            effective -= cameraWeight;
            if (effective <= 0) {
                lastActionType = ActionType.NONE;
                return -1;
            }
        }

        int roll = random.nextInt(effective);
        int cum = 0;

        if (allowCamera && !skipCamera) {
            cum += cameraWeight;
            if (roll < cum) {
                lastActionType = ActionType.CAMERA;
                int cam = doCameraMovement();
                // Soft zone: camera niet te lang laten blokkeren
                if (band == ProximityBand.SOFT) {
                    return Math.min(cam, 900);
                }
                return cam;
            }
        }
        if (allowIdle) {
            cum += idleWeight;
            if (roll < cum) {
                lastActionType = ActionType.IDLE;
                return doIdlePause();
            }
        }
        if (allowMouse) {
            cum += mouseWeight;
            if (roll < cum) {
                lastActionType = ActionType.MOUSE;
                return doRandomMouseMovement();
            }
        }
        if (allowTab) {
            cum += tabWeight;
            if (roll < cum) {
                lastActionType = ActionType.TAB_GLANCE;
                return doTabGlance();
            }
        }
        if (allowMisclick) {
            lastActionType = ActionType.MISCLICK;
            return doMisClick();
        }
        return -1;
    }

    /**
     * Storm Camera beweging — 50% MMB-drag (vloeiend), 50% pijltjes.
     * Publiek voor walk-nudge: geen setYaw, geen korte custom pulse.
     *
     * @return duur ms van de actie
     */
    public int performCameraMovement() {
        return doCameraMovement();
    }

    /**
     * Alleen MMB-drag (CombatBot {@code doMiddleMouseCameraDrag}) — geen pijltjes-pulse.
     * Logs yaw before/after for debug overlay.
     */
    public int performMmbCameraDrag() {
        int yawBefore = 0;
        try {
            yawBefore = net.storm.sdk.game.Camera.getYaw();
        } catch (Throwable ignored) {
        }
        int ms = doMiddleMouseCameraDrag();
        int yawAfter = yawBefore;
        try {
            yawAfter = net.storm.sdk.game.Camera.getYaw();
        } catch (Throwable ignored) {
        }
        int dYaw = yawAfter - yawBefore;
        if (dYaw > 1024) {
            dYaw -= 2048;
        }
        if (dYaw < -1024) {
            dYaw += 2048;
        }
        String dbg = "MMB " + ms + "ms Δyaw=" + dYaw + " (" + yawBefore + "→" + yawAfter + ")";
        lastActionLabel = dbg;
        WalkClickSettings.lastCameraDebug = dbg;
        log.info("[AntiBan] {}", dbg);
        return ms;
    }

    private int doCameraMovement() {
        // Speciaal geval: vorige camera-actie was "inzoomen / naar beneden",
        // dus nu forceren we een uitzoom + top-down/rondom view.
        if (forceTopDownNextCamera) {
            forceTopDownNextCamera = false;
            return doTopDownCameraReset();
        }

        // 50% kans op middenmuisknop drag, 50% pijltjestoetsen (Storm AntiBan)
        if (random.nextBoolean()) {
            return doMiddleMouseCameraDrag();
        }
        return doKeyboardCameraMovement();
    }

    /**
     * Middenmuisknop camera drag — Storm-stijl: synthetische canvas-events.
     * Geen {@link java.awt.Robot} / {@code requestFocus} — die maken het venster actief.
     */
    private int doMiddleMouseCameraDrag() {
        Canvas canvas = Client.getCanvas();
        if (canvas == null) {
            return doKeyboardCameraMovement();
        }

        int startX = randomRange(280, 480);
        int startY = randomRange(180, 320);

        boolean goRight = random.nextBoolean();
        int dragDistMin = AntiBanSettings.mmbDragDistanceMin;
        int dragDistMax = AntiBanSettings.mmbDragDistanceMax;
        if (dragDistMax <= dragDistMin) {
            dragDistMax = dragDistMin + 20;
        }
        int totalDragX = randomRange(dragDistMin, dragDistMax) * (goRight ? 1 : -1);
        int totalDragY = randomRange(-10, 10);
        if (totalDragY > 6) {
            forceTopDownNextCamera = true;
        }

        int minDur = AntiBanSettings.cameraDurationMin;
        int maxDur = AntiBanSettings.cameraDurationMax;
        if (maxDur <= minDur) {
            maxDur = minDur + 500;
        }
        int totalDuration = randomRange(minDur, maxDur);

        int stepDelayMin = AntiBanSettings.mmbDragSpeedMin;
        int stepDelayMax = AntiBanSettings.mmbDragSpeedMax;
        if (stepDelayMax <= stepDelayMin) {
            stepDelayMax = stepDelayMin + 10;
        }

        int avgStepDelay = (stepDelayMin + stepDelayMax) / 2;
        int steps = Math.max(6, totalDuration / avgStepDelay);

        int endX = Math.max(50, Math.min(710, startX + totalDragX));
        int endY = Math.max(50, Math.min(430, startY + totalDragY));

        // Alleen synthetic — geen focus-steal (CombatBot/Storm Mouse.moved)
        boolean ok = Mouse.mmbCameraDragSynthetic(startX, startY, endX, endY, steps, stepDelayMin, stepDelayMax);
        if (!ok) {
            log.warn("[AntiBan] synthetic MMB failed");
        }

        report("CAMERA_MMB", "Camera: MMB drag");
        return totalDuration + randomRange(150, 500);
    }

    private int humanCameraKeyPulseMs() {
        return randomRange(CAMERA_KEY_PULSE_MS_MIN, CAMERA_KEY_PULSE_MS_MAX);
    }

    /**
     * Pijltjestoetsen camera — ~⅓ s ingedrukt (menselijk). VK_DOWN alleen kort, daarna pitch-reset.
     */
    private int doKeyboardCameraMovement() {
        int action = random.nextInt(4);
        int keyCode;
        String label;
        boolean downThenReset = false;

        switch (action) {
            case 0:
                keyCode = KeyEvent.VK_LEFT;
                label = "Camera links draaien";
                break;
            case 1:
                keyCode = KeyEvent.VK_RIGHT;
                label = "Camera rechts draaien";
                break;
            case 2:
                keyCode = KeyEvent.VK_UP;
                label = "Camera omhoog";
                break;
            case 3:
                keyCode = KeyEvent.VK_DOWN;
                label = "Camera kort omlaag → reset";
                downThenReset = true;
                break;
            default:
                keyCode = KeyEvent.VK_LEFT;
                label = "Camera links";
                break;
        }

        int pulseMs = downThenReset
                ? randomRange(CAMERA_DOWN_PULSE_MS_MIN, CAMERA_DOWN_PULSE_MS_MAX)
                : randomRange(120, CAMERA_KEY_PULSE_MS_MAX);

        int totalDuration = pulseCameraKey(keyCode, pulseMs);
        report("CAMERA_KEYS", label);

        if (downThenReset) {
            int resetMs = doTopDownCameraReset();
            return totalDuration + resetMs;
        }
        return totalDuration + randomRange(100, 300);
    }

    private int pulseCameraKey(int keyCode, int durationMs) {
        int dur = Math.max(50, durationMs);
        try {
            Keyboard.pressed(keyCode);
            Thread.sleep(dur);
            Keyboard.released(keyCode);
        } catch (InterruptedException e) {
            Keyboard.released(keyCode);
            Thread.currentThread().interrupt();
        }
        return dur;
    }

    /**
     * Camera-reset na neerwaartse kanteling: meerdere VK_UP-pulsen, daarna korte links/rechts.
     */
    private int doTopDownCameraReset() {
        int totalDuration = 0;

        try {
            int upPulses = randomRange(2, 3);
            for (int i = 0; i < upPulses; i++) {
                totalDuration += pulseCameraKey(KeyEvent.VK_UP, humanCameraKeyPulseMs());
                if (i < upPulses - 1) {
                    int gap = randomRange(50, 120);
                    Thread.sleep(gap);
                    totalDuration += gap;
                }
            }

            int pause = randomRange(60, 140);
            Thread.sleep(pause);
            totalDuration += pause;

            int rotateKey = random.nextBoolean() ? KeyEvent.VK_RIGHT : KeyEvent.VK_LEFT;
            totalDuration += pulseCameraKey(rotateKey, humanCameraKeyPulseMs());

        } catch (InterruptedException e) {
            Keyboard.released(KeyEvent.VK_UP);
            Keyboard.released(KeyEvent.VK_LEFT);
            Keyboard.released(KeyEvent.VK_RIGHT);
            Keyboard.released(KeyEvent.VK_DOWN);
            Thread.currentThread().interrupt();
        }

        report("CAMERA_TOPDOWN", "Camera: pitch reset (uitzoomen)");
        return totalDuration + randomRange(100, 300);
    }

    private int doIdlePause() {
        int t = random.nextInt(3);
        if (t == 0) {
            report("IDLE_SHORT", "Korte pauze");
            return randomRange(1000, 3000);
        }
        if (t == 1) {
            report("IDLE_MEDIUM", "Middel pauze");
            return randomRange(3000, 8000);
        }
        report("IDLE_LONG", "Lange pauze");
        return randomRange(5000, 15000);
    }

    private int doRandomMouseMovement() {
        boolean keepOnMinimap = false;
        try {
            keepOnMinimap = MovementHelper.isTravelContext() || remainingWalkTiles() > 8;
        } catch (Throwable ignored) {
        }
        if (keepOnMinimap) {
            net.runelite.api.Point mini = WalkClickHelper.randomMinimapFidgetPoint();
            if (mini != null) {
                MouseManager.moveToWalkTarget(mini);
                report("MOUSE_MOVE", "Muis op minimap");
                return 0;
            }
        }
        Canvas canvas = Client.getCanvas();
        int w = canvas != null ? Math.max(100, canvas.getWidth()) : 765;
        int h = canvas != null ? Math.max(100, canvas.getHeight()) : 503;
        int type = random.nextInt(3);
        int x;
        int y;
        String label;
        if (type == 0) {
            x = randomRange(100, Math.min(700, w - 20));
            y = randomRange(100, Math.min(450, h - 20));
            label = "Muis bewegen";
        } else if (type == 1) {
            x = random.nextBoolean() ? randomRange(0, 30) : randomRange(Math.max(40, w - 40), Math.max(50, w - 10));
            y = randomRange(50, Math.min(450, h - 20));
            label = "Muis naar rand";
        } else {
            x = randomRange(150, Math.min(600, w - 20));
            y = randomRange(100, Math.min(400, h - 20));
            label = "Muis hover";
        }
        MouseManager.moveTo(new net.runelite.api.Point(x, y));
        report("MOUSE_MOVE", label);
        return 0;
    }

    private int doTabGlance() {
        lastTabGlanceMs = System.currentTimeMillis();
        nextTabGlanceCooldownMs = randomRange(90_000, 180_000);
        int key = TAB_GLANCE_F_KEYS[random.nextInt(TAB_GLANCE_F_KEYS.length)];
        try {
            Keyboard.pressed(key);
            Thread.sleep(randomRange(55, 130));
            Keyboard.released(key);
            Thread.sleep(randomRange(2000, 4000));
            Keyboard.pressed(KeyEvent.VK_ESCAPE);
            Thread.sleep(randomRange(55, 120));
            Keyboard.released(KeyEvent.VK_ESCAPE);
        } catch (InterruptedException e) {
            Keyboard.released(key);
            Keyboard.released(KeyEvent.VK_ESCAPE);
            Thread.currentThread().interrupt();
        }
        report("TAB_GLANCE", "Tab glance");
        return randomRange(200, 500);
    }

    private int doMisClick() {
        Canvas canvas = Client.getCanvas();
        int w = canvas != null ? canvas.getWidth() : 765;
        int h = canvas != null ? canvas.getHeight() : 503;
        // Click empty-ish mid area (UI zones still may catch — low impact for antiban)
        int x = randomRange(120, Math.max(200, w / 2));
        int y = randomRange(80, Math.max(150, h / 3));
        if (MouseManager.moveTo(new net.runelite.api.Point(x, y))) {
            sleep(randomRange(30, 90));
            Mouse.clickOnly(x, y, true);
        }
        report("MISCLICK", "Misclick");
        return randomRange(300, 900);
    }

    private void report(String key, String label) {
        lastActionLabel = label;
        log.info("[AntiBan] {} — {}", key, label);
        markBotActivity();
    }

    private int calculateNextDelaySec() {
        int base = Math.max(8, AntiBanSettings.frequencySec);
        int lo = Math.max(5, base / 2);
        int hi = Math.max(lo + 1, (int) (base * 1.5));
        return randomRange(lo, hi);
    }

    private int randomRange(int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- legacy static helpers (kept for older call sites) ----

    public static void maybeFidget() {
        if (!MouseSettings.isRandomMoveEnabled()) {
            return;
        }
        get().check(); // full antiban may move mouse anyway
    }

    public static void maybeCameraFidget() {
        get().check();
    }

    public static void fidget() {
        Mouse.fidget();
        get().markBotActivity();
    }

    public static void setCameraFidgetEnabled(boolean enabled) {
        AntiBanSettings.cameraMovement = enabled;
    }

    public static void setFidgetChancePercent(int percent) {
        // mapped to frequency roughly — keep API
        AntiBanSettings.frequencySec = Math.max(8, 100 - Math.max(0, Math.min(100, percent)));
    }

    // ---- continuous fidget worker (CombatBot MouseFidgetWorker) ----

    private final class MouseFidgetWorker extends Thread {
        private volatile boolean running = true;
        private final Random fr = new Random();

        MouseFidgetWorker() {
            super("LoneBot-MouseFidget");
            setDaemon(true);
        }

        void shutdown() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    int sleepMs = 2500 + fr.nextInt(6500);
                    Thread.sleep(sleepMs);
                    if (!running) {
                        break;
                    }
                    if (!shouldFidgetNow()) {
                        continue;
                    }
                    if (fr.nextInt(100) >= 55) {
                        continue;
                    }
                    boolean burst = fr.nextInt(100) < 5;
                    int n = burst ? 2 + fr.nextInt(2) : 1;
                    for (int i = 0; i < n && running; i++) {
                        if (!shouldFidgetNow()) {
                            break;
                        }
                        doOneFidget();
                        if (i + 1 < n) {
                            boolean walking = false;
                            try {
                                walking = MovementHelper.isTravelContext() || remainingWalkTiles() > 8;
                            } catch (Throwable ignored) {
                            }
                            if (!walking) {
                                Thread.sleep(120 + fr.nextInt(280));
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    if (!running) {
                        break;
                    }
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    log.debug("[AntiBan] fidget worker: {}", t.toString());
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private boolean shouldFidgetNow() {
            if (!BotRuntime.botEnabled) {
                return false;
            }
            if (!AntiBanSettings.mouseFidgetEnabled || !AntiBanSettings.enabled) {
                return false;
            }
            if (!MouseSettings.isRandomMoveEnabled()) {
                return false;
            }
            if (System.currentTimeMillis() < lastBotActivityMs + 900) {
                return false;
            }
            if (Mouse.isBusy()) {
                return false;
            }
            try {
                if (isActivelyWalking()) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (net.storm.sdk.movement.WorldWalker.isActive()) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            if (Client.getCanvas() == null) {
                return false;
            }
            try {
                if (Dialog.isOpen() || Bank.isOpen()) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            return true;
        }

        private void doOneFidget() {
            try (Mouse.Session lock = Mouse.trySession()) {
                if (lock == null) {
                    return;
                }
                doOneFidgetUnlocked();
            }
        }

        private void doOneFidgetUnlocked() {
            boolean travel = false;
            try {
                travel = MovementHelper.isTravelContext() || remainingWalkTiles() > 8;
            } catch (Throwable ignored) {
            }
            if (travel) {
                net.runelite.api.Point mini = WalkClickHelper.randomMinimapFidgetPoint();
                if (mini != null) {
                    MouseManager.moveToWalkTarget(mini);
                    return;
                }
            }
            Canvas canvas = Client.getCanvas();
            if (canvas == null) {
                return;
            }
            int amp = 2 + fr.nextInt(6);
            double angle = fr.nextDouble() * Math.PI * 2.0;
            int dx = (int) Math.round(Math.cos(angle) * amp);
            int dy = (int) Math.round(Math.sin(angle) * amp);
            int baseX = Mouse.getX();
            int baseY = Mouse.getY();
            if (baseX < 0) {
                baseX = 200 + fr.nextInt(400);
            }
            if (baseY < 0) {
                baseY = 150 + fr.nextInt(250);
            }
            int cw = Math.max(50, canvas.getWidth());
            int ch = Math.max(50, canvas.getHeight());
            int toX = Math.max(10, Math.min(cw - 10, baseX + dx));
            int toY = Math.max(10, Math.min(ch - 10, baseY + dy));
            Mouse.moveRaw(toX, toY);
        }
    }

    /**
     * Alleen pijltje links/rechts. Zeldzamer dan muis-fidget; niet tijdens lopen/bank.
     */
    private final class KeyboardPanWorker extends Thread {
        private volatile boolean running = true;
        private final Random kr = new Random();

        KeyboardPanWorker() {
            super("LoneBot-KeyboardPan");
            setDaemon(true);
        }

        void shutdown() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(12_000L + kr.nextInt(11_000));
                    if (!running) {
                        break;
                    }
                    if (!shouldPanNow()) {
                        continue;
                    }
                    if (kr.nextInt(100) >= 40) {
                        continue;
                    }
                    pulseLeftOrRight();
                } catch (InterruptedException e) {
                    if (!running) {
                        break;
                    }
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    log.debug("[AntiBan] keyboard-pan: {}", t.toString());
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private boolean shouldPanNow() {
            if (!BotRuntime.botEnabled) {
                return false;
            }
            if (!AntiBanSettings.keyboardPanEnabled || !AntiBanSettings.enabled) {
                return false;
            }
            if (System.currentTimeMillis() < lastBotActivityMs + 900) {
                return false;
            }
            try {
                if (Mouse.isBusy()) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (net.storm.sdk.movement.WorldWalker.isActive()) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            try {
                // Stil bij doel (minen): wel pan; alleen skip tijdens echt lopen
                if (Movement.isMoving() || Movement.getDestination() != null) {
                    return false;
                }
                if (isActivelyWalking()) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            if (Client.getCanvas() == null) {
                return false;
            }
            try {
                if (Dialog.isOpen() || Bank.isOpen()) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            return true;
        }

        private void pulseLeftOrRight() {
            boolean right = kr.nextBoolean();
            int key = right ? KeyEvent.VK_RIGHT : KeyEvent.VK_LEFT;
            int ms = 160 + kr.nextInt(181);
            try {
                Keyboard.pressed(key);
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    Keyboard.released(key);
                } catch (Throwable ignored) {
                }
            }
            long now = System.currentTimeMillis();
            if (now - lastKeyPanLogMs >= 2_500L) {
                lastKeyPanLogMs = now;
                String dir = right ? "rechts" : "links";
                log.info("[AB/keys] camera {} {}ms", dir, ms);
                BotRuntime.logConsole("[AB/keys] camera " + dir + " " + ms + "ms");
            }
        }
    }
}
