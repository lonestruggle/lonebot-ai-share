package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Camera;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.input.Mouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Walk-camera: human MMB (speed×distance), skip when walk-dir already ~visible,
 * start points outside inv/minimap/chat.
 */
public final class WalkCamera {

    private static final Logger log = LoggerFactory.getLogger(WalkCamera.class);

    public static final int YAW_TOLERANCE = 64;
    public static final int PITCH_TOLERANCE = 22;
    public static final int ZOOM_TOLERANCE = 50;
    /** Half-circle in JAU — face% = 100 * (1 - |dYaw|/1024). */
    private static final int HALF_TURN = 1024;
    private static final int MAX_MMB_PASSES = 1;
    private static final int MAX_WHEEL_STEPS = 20;
    /** Langer wachten tussen cam-aanpassingen → minder draai-spam tijdens lopen. */
    private static final long MIN_APPLY_INTERVAL_MS = 2400L;
    /**
     * Yaw alleen bijsturen als screen-ahead onder dit % zit.
     * (visibilitySkip% mag hoger blijven voor “goed genoeg”; yaw niet bij elke klik.)
     * Lager = luier (bestemming mag meer aan de rand).
     */
    private static final int YAW_ADJUST_BELOW_PERCENT = 40;
    /** Ideal screen position for FOLLOW: top-center (pad vooruit). */
    private static final double AHEAD_X = 0.50;
    private static final double AHEAD_Y = 0.30;

    private static volatile int yawDragSign;
    private static volatile int pitchDragSign;
    /** Welke pijltjestoets yaw de goede kant op zet (1=RIGHT verhoogt yaw richting +, -1=LEFT). */
    private static volatile int keyboardYawSign = 1;
    /** Pitch: 1=UP verhoogt pitch, -1=DOWN. */
    private static volatile int keyboardPitchSign = 1;
    private static volatile long lastApplyMs;
    private static volatile boolean enabled = true;

    private WalkCamera() {
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled && WalkCameraSettings.enabled;
    }

    public static void prepareForWalk(WorldPoint destination) {
        if (!isEnabled() || destination == null) {
            return;
        }
        try {
            prepareForWalk0(destination);
        } catch (RuntimeException e) {
            log.debug("[WalkCamera] prepareForWalk: {}", e.toString());
        }
    }

    private static void prepareForWalk0(WorldPoint destination) {
        // Dicht bij bestemming: geen MMB/keyboard camera tijdens walk (AntiBan-band)
        WorldPoint from = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null) {
                return null;
            }
            return c.getLocalPlayer().getWorldLocation();
        }, null);
        if (from != null && from.getPlane() == destination.getPlane()
                && from.distanceTo(destination) <= net.storm.sdk.utils.AntiBan.NEAR_DEST_TILES) {
            WalkCameraSettings.lastSkipReason = "near-dest≤" + net.storm.sdk.utils.AntiBan.NEAR_DEST_TILES;
            refreshLiveSnapshot(destination);
            return;
        }
        try {
            if (MovementHelper.isTravelContext() && WalkClickSettings.useMinimap) {
                WalkCameraSettings.lastSkipReason = "travel-minimap";
                refreshLiveSnapshot(destination);
                return;
            }
        } catch (Throwable ignored) {
        }
        refreshLiveSnapshot(destination);
        long now = System.currentTimeMillis();
        if (now - lastApplyMs < MIN_APPLY_INTERVAL_MS) {
            WalkCameraSettings.lastSkipReason = "throttle";
            return;
        }
        if (applyToward(destination, false)) {
            lastApplyMs = System.currentTimeMillis();
        }
    }

    /**
     * Zorg dat {@code target} vooruit op scherm staat (bank booth, NPC, …).
     * Negeert near-dest skip van {@link #prepareForWalk} — nodig op Lumb bank-floor
     * waar je ≤50t van de booth staat maar camera nog op de trap gericht is.
     *
     * @return true als camera is aangepast
     */
    public static boolean ensureLookingAt(WorldPoint target) {
        if (target == null) {
            return false;
        }
        if (!isEnabled()) {
            // Toch force via applyToward als walk-cam uit staat — bank moet openen
            refreshLiveSnapshot(target);
            return applyToward(target, true);
        }
        refreshLiveSnapshot(target);
        int ahead = screenAheadScore(target);
        int skipAt = Math.max(55, Math.min(95, WalkCameraSettings.visibilitySkipPercent));
        if (ahead >= skipAt) {
            WalkCameraSettings.lastSkipReason = "ensure ok ahead=" + ahead + "%";
            return false;
        }
        lastApplyMs = 0L;
        boolean ok = applyToward(target, true);
        if (ok) {
            lastApplyMs = System.currentTimeMillis();
            WalkCameraSettings.lastSkipReason = "ensure adj ahead→" + screenAheadScore(target) + "%";
            BotRuntime.logConsole("[WalkCamera] ensureLookingAt ahead " + ahead + "%→"
                    + WalkCameraSettings.lastFacePercent + "%");
        }
        return ok;
    }

    public static String runPreview(int tilesNorth) {
        WorldPoint dest = destNorth(tilesNorth);
        if (dest == null) {
            return failNotReady();
        }
        lastApplyMs = 0L;
        int[] before = snapshotCamera();
        boolean ok = applyToward(dest, true);
        sleep(200);
        int[] after = snapshotCamera();
        return formatResult("PREVIEW (geen walk)", dest, before, after, ok);
    }

    public static String runWalkTest(int tilesNorth) {
        WorldPoint dest = destNorth(tilesNorth);
        if (dest == null) {
            return failNotReady();
        }
        lastApplyMs = 0L;
        int[] before = snapshotCamera();
        applyToward(dest, true);
        sleep(150);
        boolean walked = WalkClickHelper.walkTo(dest, dest);
        sleep(300);
        int[] after = snapshotCamera();
        String walkBit = walked
                ? "WALK OK | " + WalkClickSettings.lastMethod + " " + WalkClickSettings.lastDetail
                : "WALK FAIL | " + WalkClickSettings.lastMethod + " " + WalkClickSettings.lastDetail;
        return formatResult("WALK+CAMERA", dest, before, after, walked) + "\n" + walkBit
                + "\n" + WalkCameraSettings.reportBlock();
    }

    public static String runSelfTest(int tilesNorth) {
        return runPreview(tilesNorth);
    }

    public static String liveSnapshotLine() {
        int[] v = snapshotCamera();
        return "yaw=" + v[0] + " pitch=" + v[1] + " scale=" + v[2]
                + " face=" + WalkCameraSettings.lastFacePercent + "%"
                + " zoom%~" + WalkCameraSettings.scaleToPercent(v[2]);
    }

    /**
     * How much the camera already faces the walk direction (0–100).
     * 100 = perfectly aligned, 0 = looking opposite.
     */
    public static int facePercent(int cameraYaw, int walkYaw) {
        int d = yawDelta(cameraYaw, walkYaw);
        int pct = 100 - (d * 100) / HALF_TURN;
        return Math.max(0, Math.min(100, pct));
    }

    private static void refreshLiveSnapshot(WorldPoint dest) {
        int[] v = snapshotCamera();
        WalkCameraSettings.lastYaw = v[0];
        WalkCameraSettings.lastPitch = v[1];
        WalkCameraSettings.lastScale = v[2];
        WorldPoint from = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            Player p = c != null ? c.getLocalPlayer() : null;
            return p != null ? p.getWorldLocation() : null;
        }, null);
        if (from != null && dest != null) {
            int want = resolveYaw(from, dest);
            WalkCameraSettings.lastFacePercent = facePercent(v[0], want);
        }
    }

    private static WorldPoint destNorth(int tilesNorth) {
        if (!Static.isReady()) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getGameState() != GameState.LOGGED_IN) {
                return null;
            }
            Player local = c.getLocalPlayer();
            if (local == null || local.getWorldLocation() == null) {
                return null;
            }
            WorldPoint me = local.getWorldLocation();
            return new WorldPoint(me.getX(), me.getY() + Math.max(1, tilesNorth), me.getPlane());
        }, null);
    }

    private static String failNotReady() {
        if (!Static.isReady()) {
            return "FAIL: geen game-client.\nStart account + log in; Test-tab in die client gebruiken.";
        }
        GameState gs = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? c.getGameState() : null;
        }, null);
        return "FAIL: niet ingelogd (gameState=" + gs + ").";
    }

    private static String formatResult(String kind, WorldPoint dest, int[] before, int[] after, boolean ok) {
        String msg = (ok ? "OK" : "DEEL/SKIP") + " " + kind + " → " + dest.getX() + "," + dest.getY()
                + "\nmode=" + WalkCameraSettings.yawMode
                + " pitch=" + WalkCameraSettings.pitchTarget
                + " zoom%=" + WalkCameraSettings.zoomPercent
                + " face=" + WalkCameraSettings.lastFacePercent + "%"
                + " skip=" + WalkCameraSettings.lastSkipReason
                + "\nbefore yaw/pitch/scale=" + before[0] + "/" + before[1] + "/" + before[2]
                + "\nafter  yaw/pitch/scale=" + after[0] + "/" + after[1] + "/" + after[2];
        BotRuntime.logConsole("WalkCamera: " + msg.replace("\n", " | "));
        log.info("[WalkCamera] {}", msg.replace("\n", " "));
        return msg;
    }

    private static boolean applyToward(WorldPoint destination, boolean force) {
        WorldPoint from = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getGameState() != GameState.LOGGED_IN) {
                return null;
            }
            Player local = c.getLocalPlayer();
            return local != null ? local.getWorldLocation() : null;
        }, null);
        if (from == null) {
            return false;
        }

        int desiredYaw = resolveYaw(from, destination);
        int desiredPitch = WalkCameraSettings.pitchTarget;
        int desiredScale = WalkCameraSettings.zoomScaleTarget();

        int[] snap = snapshotCamera();
        int yawNow = snap[0];
        int pitchNow = snap[1];
        int scaleNow = snap[2];
        int faceYaw = facePercent(yawNow, desiredYaw);
        int ahead = screenAheadScore(destination);
        // face% voor overlay = mix: scherm-vooruit telt zwaarder (dat is wat FOLLOW moet zijn)
        int face = Math.max(faceYaw, ahead);
        if (WalkCameraSettings.yawMode == WalkCameraSettings.YawMode.FOLLOW
                || WalkCameraSettings.yawMode == WalkCameraSettings.YawMode.OFFSET) {
            face = ahead; // FOLLOW = bestemming vooruit op scherm
        }
        WalkCameraSettings.lastYaw = yawNow;
        WalkCameraSettings.lastPitch = pitchNow;
        WalkCameraSettings.lastScale = scaleNow;
        WalkCameraSettings.lastFacePercent = face;

        int skipAt = Math.max(55, Math.min(95, WalkCameraSettings.visibilitySkipPercent));
        int pitchLo = WalkCameraSettings.pitchMin();
        int pitchHi = WalkCameraSettings.pitchMax();

        boolean needPitch = force
                || pitchNow < pitchLo
                || pitchNow > pitchHi;
        boolean needZoom = force
                || Math.abs(scaleNow - desiredScale) > ZOOM_TOLERANCE;
        // Yaw: strikter dan skipAt — voorkomt constante MMB/setYaw bij “redelijk vooruit”
        boolean needYaw = force || ahead < YAW_ADJUST_BELOW_PERCENT;

        if (!force && !needYaw && !needPitch && !needZoom) {
            WalkCameraSettings.lastSkipReason = "ok ahead=" + ahead + "% p/z fine";
            WalkClickSettings.lastCameraDebug = "skip cam " + WalkCameraSettings.lastSkipReason;
            return false;
        }
        // Extra: als scherm al ruim goed is, nooit yaw — wel pitch/zoom indien nodig
        if (!force && ahead >= skipAt) {
            needYaw = false;
            if (!needPitch && !needZoom) {
                WalkCameraSettings.lastSkipReason = "ahead=" + ahead + "%≥" + skipAt + "%";
                WalkClickSettings.lastCameraDebug = "skip cam " + WalkCameraSettings.lastSkipReason;
                return false;
            }
        }

        WalkCameraSettings.lastSkipReason = "adj ahead=" + ahead + "%"
                + (needYaw ? " yaw" : "")
                + (needPitch ? " pitch" : "")
                + (needZoom ? " zoom" : "");

        boolean changed = false;
        // Keyboard (pijltjes) óf MMB óf harde snap
        if (needYaw || needPitch) {
            if (WalkCameraSettings.useKeyboard) {
                changed = keyboardOrient(desiredYaw, desiredPitch, needYaw, needPitch, destination)
                        || changed;
            } else if (WalkCameraSettings.humanMmb) {
                if (needYaw && (WalkCameraSettings.yawMode == WalkCameraSettings.YawMode.FOLLOW
                        || WalkCameraSettings.yawMode == WalkCameraSettings.YawMode.OFFSET
                        || WalkCameraSettings.yawMode == WalkCameraSettings.YawMode.CONTRA)) {
                    changed = orientScreenFollow(destination, desiredYaw,
                            needPitch ? desiredPitch : Integer.MIN_VALUE) || changed;
                } else {
                    changed = humanOrient(desiredYaw, desiredPitch, desiredScale,
                            needYaw, needPitch, false) || changed;
                }
            } else {
                changed = snapOrient(desiredYaw, desiredPitch, desiredScale) || changed;
            }
        }
        // Zoom = scrollwiel (keyboard én MMB)
        if (needZoom && (WalkCameraSettings.humanMmb || WalkCameraSettings.useKeyboard)) {
            changed = humanZoomTo(desiredScale) || changed;
        } else if (needZoom) {
            changed = snapOrient(desiredYaw, desiredPitch, desiredScale) || changed;
        }

        refreshLiveSnapshot(destination);
        int aheadAfter = screenAheadScore(destination);
        WalkCameraSettings.lastFacePercent = aheadAfter;
        if (changed) {
            WalkClickSettings.lastCameraDebug = "cam ahead " + ahead + "%→" + aheadAfter
                    + "% y=" + WalkCameraSettings.lastYaw
                    + " z%~" + WalkCameraSettings.scaleToPercent(WalkCameraSettings.lastScale);
            BotRuntime.logConsole("WalkCamera ahead " + ahead + "%→" + aheadAfter + "%"
                    + " needYaw=" + needYaw + " pitch=" + needPitch + " zoom=" + needZoom);
        }
        return changed;
    }

    /**
     * Score 0–100: staat de loop-bestemming vooruit (boven-midden) op het scherm?
     * Dit is wat FOLLOW moet bereiken — niet alleen world-yaw matching.
     */
    public static int screenAheadScore(WorldPoint dest) {
        Integer score = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || dest == null) {
                return 0;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, dest);
            if (lp == null) {
                return 0;
            }
            Point canvas = Perspective.localToCanvas(c, lp, dest.getPlane());
            if (canvas == null) {
                return 0;
            }
            int cw = c.getCanvasWidth();
            int ch = c.getCanvasHeight();
            if (cw < 80 || ch < 80) {
                return 0;
            }
            int x = canvas.getX();
            int y = canvas.getY();
            if (WalkUiZones.isOverUi(c, canvas)) {
                return 5;
            }
            // Ver buiten canvas = slecht
            if (x < -40 || x > cw + 40 || y < -40 || y > ch + 40) {
                return 0;
            }
            double idealX = cw * AHEAD_X;
            double idealY = ch * AHEAD_Y;
            double nx = Math.abs(x - idealX) / (cw * 0.50);
            double ny = Math.abs(y - idealY) / (ch * 0.45);
            // Onder midden van scherm = “achter/zij” → zware straf
            if (y > ch * 0.58) {
                ny = Math.max(ny, 1.2);
            }
            double bad = Math.min(1.5, nx * 0.55 + ny * 0.70);
            int s = (int) Math.round(100.0 * (1.0 - Math.min(1.0, bad)));
            return Math.max(0, Math.min(100, s));
        }, 0);
        return score != null ? score : 0;
    }

    /**
     * Eén MMB-curve: bestemming naar boven-midden + optioneel pitch in dezelfde stroke.
     * Geen tweede MMB; setYaw-fallback hoogstens één keer (geen +180 flip).
     */
    private static boolean orientScreenFollow(WorldPoint destination, int desiredYaw) {
        return orientScreenFollow(destination, desiredYaw, Integer.MIN_VALUE);
    }

    /**
     * @param desiredPitch {@link Integer#MIN_VALUE} = geen pitch in deze stroke
     */
    private static boolean orientScreenFollow(WorldPoint destination, int desiredYaw, int desiredPitch) {
        int aheadBefore = screenAheadScore(destination);
        int yawBefore = Camera.getYaw() & 2047;
        int pitchBefore = Camera.getPitch();
        boolean wantPitch = desiredPitch != Integer.MIN_VALUE
                && Math.abs(desiredPitch - pitchBefore) > PITCH_TOLERANCE;

        int[] drag = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            int cw = Math.max(200, c.getCanvasWidth());
            int ch = Math.max(200, c.getCanvasHeight());
            LocalPoint lp = LocalPoint.fromWorld(c, destination);
            Point canvas = lp != null ? Perspective.localToCanvas(c, lp, destination.getPlane()) : null;

            ThreadLocalRandom r = ThreadLocalRandom.current();
            int dragX;
            int dragY = r.nextInt(-18, 19);

            if (canvas != null) {
                int cx = (int) (cw * AHEAD_X);
                int cy = (int) (ch * AHEAD_Y);
                int errX = canvas.getX() - cx;
                int errY = canvas.getY() - cy;
                int sign = yawDragSign != 0 ? yawDragSign : -1;
                dragX = clamp(-errX * (sign > 0 ? 1 : -1), -520, 520);
                if (Math.abs(errX) < 40 && errY > 80) {
                    int dYaw = signedYawDelta(c.getCameraYaw() & 2047, desiredYaw);
                    dragX = (dYaw > 0 ? 1 : -1) * clamp((int) (Math.abs(dYaw) * 0.5), 120, 480);
                    if (yawDragSign < 0) {
                        dragX = -dragX;
                    }
                } else {
                    dragX = clamp((int) (dragX * 1.15) + r.nextInt(-25, 26), -520, 520);
                }
                if (Math.abs(dragX) < 50) {
                    dragX = (dragX >= 0 ? 1 : -1) * r.nextInt(70, 140);
                }
            } else {
                int dYaw = signedYawDelta(c.getCameraYaw() & 2047, desiredYaw);
                int sign = yawDragSign != 0 ? yawDragSign : 1;
                dragX = (dYaw > 0 ? sign : -sign) * clamp((int) (Math.abs(dYaw) * 0.55), 100, 520);
            }

            if (WalkCameraSettings.yawMode == WalkCameraSettings.YawMode.CONTRA) {
                dragX = -dragX;
            }

            // Pitch in dezelfde stroke (niet een tweede MMB)
            if (wantPitch) {
                if (pitchDragSign == 0) {
                    pitchDragSign = 1;
                }
                int dPitch = desiredPitch - c.getCameraPitch();
                int py = clamp((int) (Math.abs(dPitch) * 1.5) + r.nextInt(15, 40), 35, 240);
                dragY = (dPitch > 0 ? pitchDragSign : -pitchDragSign) * py;
            }
            return new int[]{dragX, dragY};
        }, null);

        if (drag == null) {
            return snapYawOnly(desiredYaw);
        }

        boolean mmb = false;
        if (WalkCameraSettings.humanMmb) {
            mmb = mmbDragHuman(drag[0], drag[1]);
        }
        sleep(120);

        int aheadAfter = screenAheadScore(destination);
        int yawAfter = Camera.getYaw() & 2047;
        int improved = aheadAfter - aheadBefore;
        int yawMoved = yawDelta(yawBefore, yawAfter);

        if (yawMoved >= 12 && drag[0] != 0) {
            int dYaw = signedYawDelta(yawBefore, yawAfter);
            int learned = Integer.signum(drag[0]) * Integer.signum(dYaw);
            if (learned != 0) {
                yawDragSign = learned > 0 ? 1 : -1;
            }
        }
        int pitchAfter = Camera.getPitch();
        if (wantPitch && Math.abs(pitchAfter - pitchBefore) >= 4 && drag[1] != 0) {
            int learned = Integer.signum(drag[1]) * Integer.signum(pitchAfter - pitchBefore);
            if (learned != 0) {
                pitchDragSign = learned > 0 ? 1 : -1;
            }
        }

        // Eén setYaw-fallback, geen tweede flip-beweging
        if (aheadAfter < 35 && improved < 5 && yawMoved < 10) {
            log.info("[WalkCamera] MMB zwak (ahead {}→{}) → 1× setYaw {}",
                    aheadBefore, aheadAfter, desiredYaw);
            snapYawOnly(desiredYaw);
            return true;
        }
        return mmb || improved > 0 || (wantPitch && Math.abs(pitchAfter - pitchBefore) >= 4);
    }

    /**
     * Yaw/pitch via pijltjestoetsen (zoals AntiBan camera-fidget).
     * Zoom blijft scrollwiel ({@link #humanZoomTo}).
     */
    private static boolean keyboardOrient(
            int wantYaw, int wantPitch, boolean doYaw, boolean doPitch, WorldPoint destination
    ) {
        boolean any = false;
        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (doYaw) {
            int aheadBefore = destination != null ? screenAheadScore(destination) : 0;
            int yawBefore = Camera.getYaw() & 2047;
            int dYaw = signedYawDelta(yawBefore, wantYaw & 2047);
            if (WalkCameraSettings.yawMode == WalkCameraSettings.YawMode.CONTRA) {
                dYaw = -dYaw;
            }
            if (Math.abs(dYaw) > YAW_TOLERANCE / 2 || (destination != null && aheadBefore < YAW_ADJUST_BELOW_PERCENT)) {
                if (keyboardYawSign == 0) {
                    keyboardYawSign = 1;
                }
                // dYaw>0 → we willen yaw omhoog; sign leert of RIGHT of LEFT dat doet
                int key = (dYaw > 0) == (keyboardYawSign > 0)
                        ? KeyEvent.VK_RIGHT : KeyEvent.VK_LEFT;
                int pulseMs = clamp(Math.abs(dYaw) / 3 + r.nextInt(90, 180), 110, 480);
                // FOLLOW met slechte ahead: iets langere pulse
                if (destination != null && aheadBefore < 35) {
                    pulseMs = clamp(pulseMs + r.nextInt(40, 100), 110, 520);
                }
                pulseCameraKey(key, pulseMs);
                any = true;
                sleep(40 + r.nextInt(60));

                int yawAfter = Camera.getYaw() & 2047;
                int moved = signedYawDelta(yawBefore, yawAfter);
                if (Math.abs(moved) >= 8) {
                    // Welke toets bewoog yaw welke kant op?
                    int keySign = (key == KeyEvent.VK_RIGHT) ? 1 : -1;
                    int learned = Integer.signum(moved) * keySign;
                    if (learned != 0) {
                        // Als RIGHT yaw verhoogde → om yaw+ te krijgen: RIGHT wanneer dYaw>0 → sign=+1
                        keyboardYawSign = learned > 0 ? 1 : -1;
                    }
                }
                int aheadAfter = destination != null ? screenAheadScore(destination) : 0;
                // Als ahead slechter werd: andere kant kort
                if (destination != null && aheadAfter + 8 < aheadBefore && Math.abs(moved) >= 6) {
                    int other = key == KeyEvent.VK_RIGHT ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
                    pulseCameraKey(other, clamp(pulseMs * 2 / 3, 80, 320));
                    any = true;
                    keyboardYawSign = -keyboardYawSign;
                } else if (destination != null && aheadAfter < 30 && Math.abs(moved) < 6) {
                    // Weinig beweging → 1× setYaw fallback
                    snapYawOnly(wantYaw);
                    any = true;
                }
            }
        }

        if (doPitch) {
            int pitchBefore = Camera.getPitch();
            int dPitch = wantPitch - pitchBefore;
            if (Math.abs(dPitch) > PITCH_TOLERANCE) {
                if (keyboardPitchSign == 0) {
                    keyboardPitchSign = 1;
                }
                int key = (dPitch > 0) == (keyboardPitchSign > 0)
                        ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
                // Pitch 3064-schaal: korte pulses, meerdere indien nodig
                int pulses = Math.min(3, 1 + Math.abs(dPitch) / 400);
                for (int i = 0; i < pulses; i++) {
                    int pulseMs = clamp(80 + Math.abs(dPitch) / (pulses * 8) + r.nextInt(40, 100), 70, 280);
                    // DOWN in OSRS vaak agressief → korter
                    if (key == KeyEvent.VK_DOWN) {
                        pulseMs = Math.min(pulseMs, 140);
                    }
                    pulseCameraKey(key, pulseMs);
                    any = true;
                    if (i < pulses - 1) {
                        sleep(35 + r.nextInt(50));
                    }
                }
                int pitchAfter = Camera.getPitch();
                int dp = pitchAfter - pitchBefore;
                if (Math.abs(dp) >= 4) {
                    int keySign = (key == KeyEvent.VK_UP) ? 1 : -1;
                    int learned = Integer.signum(dp) * keySign;
                    if (learned != 0) {
                        keyboardPitchSign = learned > 0 ? 1 : -1;
                    }
                }
            }
        }

        if (any) {
            log.info("[WalkCamera] keyboard yawSign={} pitchSign={}", keyboardYawSign, keyboardPitchSign);
            BotRuntime.logConsole("WalkCamera KB yaw/pitch");
        }
        return any;
    }

    private static void pulseCameraKey(int keyCode, int durationMs) {
        int dur = Math.max(40, durationMs);
        try {
            Keyboard.pressed(keyCode);
            Thread.sleep(dur);
            Keyboard.released(keyCode);
        } catch (InterruptedException e) {
            Keyboard.released(keyCode);
            Thread.currentThread().interrupt();
        }
    }

    private static boolean snapYawOnly(int wantYaw) {
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                c.setCameraYawTarget(wantYaw & 2047);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    private static boolean humanOrient(
            int wantYaw, int wantPitch, int wantScale,
            boolean doYaw, boolean doPitch, boolean doZoom
    ) {
        boolean any = false;
        if (doYaw || doPitch) {
            if (yawDragSign == 0) {
                yawDragSign = 1;
            }
            if (pitchDragSign == 0) {
                pitchDragSign = 1;
            }
            // Exact ÉÉN gecombineerde MMB-stroke (H+V tegelijk), geen aparte yaw/pitch moves
            int yaw = Camera.getYaw() & 2047;
            int pitch = Camera.getPitch();
            int dYaw = doYaw ? signedYawDelta(yaw, wantYaw) : 0;
            int dPitch = doPitch ? (wantPitch - pitch) : 0;

            int dragX = 0;
            int dragY = 0;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (Math.abs(dYaw) > YAW_TOLERANCE) {
                // Grote Δ → lange stroke (mag offscreen) zodat 1 beweging genoeg is
                int px = clamp((int) (Math.abs(dYaw) * 0.55) + r.nextInt(40, 90), 80, 520);
                dragX = (dYaw > 0 ? yawDragSign : -yawDragSign) * px;
            } else if (doYaw) {
                // Kleine correctie
                dragX = (dYaw >= 0 ? yawDragSign : -yawDragSign) * r.nextInt(35, 70);
            }
            if (Math.abs(dPitch) > PITCH_TOLERANCE) {
                int py = clamp((int) (Math.abs(dPitch) * 1.6) + r.nextInt(20, 50), 40, 280);
                dragY = (dPitch > 0 ? pitchDragSign : -pitchDragSign) * py;
            } else if (doPitch) {
                dragY = (dPitch >= 0 ? pitchDragSign : -pitchDragSign) * r.nextInt(20, 45);
            } else if (doYaw && dragX != 0) {
                // Menselijke verticale wobble tijdens yaw-only (geen echte pitch-target)
                dragY = r.nextInt(-28, 29);
            }

            if (dragX != 0 || dragY != 0) {
                int yawBefore = Camera.getYaw() & 2047;
                int pitchBefore = Camera.getPitch();
                if (mmbDragHuman(dragX, dragY)) {
                    any = true;
                    // Leer teken na echte stroke (geen aparte probe die camera verpest)
                    int dyaw = signedYawDelta(yawBefore, Camera.getYaw() & 2047);
                    if (Math.abs(dyaw) >= 8 && dragX != 0) {
                        // Als we rechts sleepten (+dragX) en yaw steeg → sign +1 voor “sleep +X om yaw +”
                        int wantSign = Integer.signum(dragX) * Integer.signum(dyaw);
                        if (wantSign != 0) {
                            yawDragSign = wantSign > 0 ? 1 : -1;
                        }
                    }
                    int dp = Camera.getPitch() - pitchBefore;
                    if (Math.abs(dp) >= 4 && dragY != 0) {
                        int wantSign = Integer.signum(dragY) * Integer.signum(dp);
                        if (wantSign != 0) {
                            pitchDragSign = wantSign > 0 ? 1 : -1;
                        }
                    }
                }
            }
        }
        if (doZoom && humanZoomTo(wantScale)) {
            any = true;
        }
        return any;
    }

    /**
     * Human MMB: 1 curved stroke, start buiten UI, eind mag ver offscreen
     * (volledige cam-draai zonder meerdere stappen).
     */
    private static boolean mmbDragHuman(int dragX, int dragY) {
        if (net.storm.sdk.input.MouseSettings.forceInvokeOnly()) {
            return false;
        }
        int[] start = pickSafeStartPoint();
        if (start == null) {
            return false;
        }
        int w = start[2];
        int h = start[3];
        int startX = start[0];
        int startY = start[1];
        // Offscreen toegestaan — meer afstand = meer camera-draai in 1 move
        int endX = startX + dragX;
        int endY = startY + dragY;
        // Soft clamp ver buiten canvas (niet tot canvas-rand knippen)
        endX = clamp(endX, -280, w + 280);
        endY = clamp(endY, -220, h + 220);

        int dist = (int) Math.hypot(endX - startX, endY - startY);
        if (dist < 20) {
            return false;
        }

        ThreadLocalRandom r = ThreadLocalRandom.current();
        int stepDelayMin;
        int stepDelayMax;
        int steps;
        if (dist >= 280) {
            stepDelayMin = 5;
            stepDelayMax = 14;
            steps = Math.max(16, dist / 14 + r.nextInt(4, 10));
        } else if (dist >= 140) {
            stepDelayMin = 7;
            stepDelayMax = 18;
            steps = Math.max(14, dist / 11 + r.nextInt(3, 8));
        } else {
            stepDelayMin = 12;
            stepDelayMax = 28;
            steps = Math.max(12, dist / 7 + r.nextInt(3, 8));
        }

        boolean ok = Mouse.mmbCameraDragHuman(startX, startY, endX, endY, steps, stepDelayMin, stepDelayMax);
        if (ok) {
            log.info("[WalkCamera] 1× MMB-curve @{},{} → {},{} (offscreen) dist={} steps={}",
                    startX, startY, endX, endY, dist, steps);
        }
        return ok;
    }

    /** Random start in world-view, never inv/minimap/chat. */
    private static int[] pickSafeStartPoint() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Canvas canvas = c.getCanvas();
            if (canvas == null) {
                return null;
            }
            int w = Math.max(200, canvas.getWidth());
            int h = Math.max(200, canvas.getHeight());
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int tries = 0; tries < 28; tries++) {
                // Breed spreiden — niet steeds midden
                int x = r.nextInt(Math.max(40, w / 10), Math.max(50, (w * 9) / 10));
                int y = r.nextInt(Math.max(40, h / 12), Math.max(50, (h * 7) / 10));
                Point p = new Point(x, y);
                if (WalkUiZones.isOverUi(c, p)) {
                    continue;
                }
                // Extra: niet te dicht bij randen waar UI vaak zit
                if (x > w - 100 && y < 120) {
                    continue; // minimap zone approx
                }
                if (y > h - 120) {
                    continue; // chat zone approx
                }
                return new int[]{x, y, w, h};
            }
            // Fallback: canvas midden
            int fx = w / 2 + r.nextInt(-60, 61);
            int fy = h / 3 + r.nextInt(-40, 41);
            return new int[]{clamp(fx, 40, w - 40), clamp(fy, 40, h - 40), w, h};
        }, null);
    }

    private static int[] avoidUiEnd(int sx, int sy, int ex, int ey) {
        int[] out = new int[]{ex, ey};
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            if (!WalkUiZones.isOverUi(c, new Point(ex, ey))) {
                return null;
            }
            // Walk back along segment until clear
            for (int i = 8; i >= 1; i--) {
                int x = sx + (ex - sx) * i / 10;
                int y = sy + (ey - sy) * i / 10;
                if (!WalkUiZones.isOverUi(c, new Point(x, y))) {
                    out[0] = x;
                    out[1] = y;
                    return null;
                }
            }
            out[0] = sx;
            out[1] = sy;
            return null;
        }, null);
        return out;
    }

    private static boolean humanZoomTo(int wantScale) {
        int before = snapshotCamera()[2];
        if (Math.abs(before - wantScale) <= ZOOM_TOLERANCE) {
            return false;
        }
        Canvas canvas = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? c.getCanvas() : null;
        }, null);

        for (int i = 0; i < MAX_WHEEL_STEPS; i++) {
            int scale = snapshotCamera()[2];
            int diff = wantScale - scale;
            if (Math.abs(diff) <= ZOOM_TOLERANCE) {
                break;
            }
            int rotation = diff > 0 ? -1 : +1;
            try {
                final int rot = rotation;
                Static.callOnClientThread(() -> {
                    Client c = Static.getClient();
                    if (c == null) {
                        return null;
                    }
                    try {
                        c.runScript(ScriptID.CAMERA_DO_ZOOM, rot > 0 ? -40 : 40, 0);
                    } catch (Throwable t) {
                        if (canvas != null) {
                            int[] pt = pickSafeStartPoint();
                            int cx = pt != null ? pt[0] : Math.max(1, canvas.getWidth() / 2);
                            int cy = pt != null ? pt[1] : Math.max(1, canvas.getHeight() / 2);
                            dispatchWheel(canvas, cx, cy, rot);
                        }
                    }
                    return null;
                }, null);
            } catch (Throwable ignored) {
            }
            sleep(40 + ThreadLocalRandom.current().nextInt(50));
        }
        return snapshotCamera()[2] != before;
    }

    private static boolean snapOrient(int wantYaw, int wantPitch, int wantScale) {
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            c.setCameraYawTarget(wantYaw);
            c.setCameraPitchTarget(wantPitch);
            return true;
        }, false);
        humanZoomTo(wantScale);
        return Boolean.TRUE.equals(ok);
    }

    static int resolveYaw(WorldPoint from, WorldPoint to) {
        int base = yawTo(from, to);
        switch (WalkCameraSettings.yawMode) {
            case CONTRA:
                // Omgekeerd + optionele extra offset
                base = (base + 1024 + WalkCameraSettings.degToJau(WalkCameraSettings.yawOffsetDeg)) & 2047;
                break;
            case OFFSET:
                base = (base + WalkCameraSettings.degToJau(WalkCameraSettings.yawOffsetDeg)) & 2047;
                break;
            case FOLLOW:
            default:
                if (WalkCameraSettings.yawOffsetDeg != 0) {
                    base = (base + WalkCameraSettings.degToJau(WalkCameraSettings.yawOffsetDeg)) & 2047;
                }
                break;
        }
        return base;
    }

    private static int[] snapshotCamera() {
        int[] v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return new int[]{-1, -1, -1};
            }
            return new int[]{c.getCameraYaw() & 2047, c.getCameraPitch(), c.getScale()};
        }, new int[]{-1, -1, -1});
        return v != null ? v : new int[]{-1, -1, -1};
    }

    private static void dispatchWheel(Component canvas, int x, int y, int rotation) {
        long when = System.currentTimeMillis();
        MouseWheelEvent ev = new MouseWheelEvent(
                canvas,
                MouseWheelEvent.MOUSE_WHEEL,
                when,
                0,
                x,
                y,
                0,
                false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL,
                3,
                rotation);
        canvas.dispatchEvent(ev);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int signedYawDelta(int from, int to) {
        int d = ((to & 2047) - (from & 2047)) & 2047;
        if (d > 1024) {
            d -= 2048;
        }
        return d;
    }

    public static int yawTo(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return 0;
        }
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        return ((int) (Math.atan2(dx, dy) * 325.949D)) & 2047;
    }

    public static int yawDelta(int a, int b) {
        return Math.abs(signedYawDelta(a, b));
    }
}
