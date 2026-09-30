package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;

/**
 * Runtime toggles for walk-click strategies (panel / config).
 * Storm2-achtig: stap min/max, reclick, delays, far-canvas, channels.
 */
public final class WalkClickSettings {

    private WalkClickSettings() {
    }

    public static volatile boolean useMinimap = true;
    public static volatile boolean useCanvas = true;
    public static volatile boolean useUiZones = true;
    public static volatile boolean useCameraNudge = true;
    public static volatile boolean useInvokeWalk = true;

    /**
     * Lage camera + yaw richting looprichting + canvas-first.
     */
    public static volatile boolean useFarCanvasWalk = false;

    public static volatile int stepMin = 10;
    public static volatile int stepMax = 22;
    public static volatile boolean forceLargeSteps = false;

    /** Basis reclick wanneer ver van de flag. */
    public static volatile int minReclickMs = 700;
    /**
     * Dicht bij flag langer wachten vóór opnieuw klikken.
     * Uit = altijd {@link #minReclickMs}.
     */
    public static volatile boolean flagProximityReclick = true;
    /** Reclick-ms als speler ≤ {@link #flagNearTiles} van de flag. */
    public static volatile int reclickNearFlagMs = 1600;
    /** Afstand waarbij “dicht bij flag” begint. */
    public static volatile int flagNearTiles = 3;
    /** Afstand waarbij we weer de basis-reclick gebruiken. */
    public static volatile int flagFarTiles = 14;

    /**
     * Far/canvas lopen: niet opnieuw klikken tot speler binnen zoveel tegels van de
     * gele flag is (anders dan minimap twin-spam).
     */
    public static volatile int canvasReclickWithinTiles = 5;

    /**
     * Far/canvas: bij twijfel (boom/NPC onder muis) → invoke i.p.v. left-click.
     * Normaal: echte left-click Walk here (zichtbaar voor tile-plugins).
     */
    public static volatile boolean canvasForceWalkHere = true;

    /**
     * Far/canvas: voorkeur echte left-click op grond. {@code false} = altijd invoke
     * (geen klik-visual / geen rode tiles).
     */
    public static volatile boolean canvasPreferRealClick = true;

    public static volatile int postClickDelayMinMs = 120;
    public static volatile int postClickDelayMaxMs = 280;

    /** Kans op dubbelklik (0–100) als twin-quick uit staat. */
    public static volatile int doubleClickChancePercent = 42;
    /** Altijd 2 snelle kliks op dezelfde walk-tegel. */
    public static volatile boolean twinQuickClicks = true;

    public static volatile String lastMethod = "-";
    public static volatile String lastDetail = "-";

    public static volatile String lastHoverKind = "-";
    public static volatile int lastHoverMs = 0;
    public static volatile boolean lastWasDoubleClick = false;
    public static volatile String lastHumanSummary = "-";
    public static volatile long lastHumanAtMs = 0L;

    public static volatile String lastCameraDebug = "-";

    public static void noteHuman(String hoverKind, int hoverMs, boolean doubleClick, String method) {
        lastHoverKind = hoverKind != null ? hoverKind : "-";
        lastHoverMs = hoverMs;
        lastWasDoubleClick = doubleClick;
        lastHumanAtMs = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append(lastHoverKind).append(' ').append(hoverMs).append("ms");
        if (doubleClick) {
            sb.append(" +DBL");
        }
        if (method != null && !method.isEmpty()) {
            sb.append(' ').append(method);
        }
        lastHumanSummary = sb.toString();
    }

    public static int effectiveStepMin() {
        if (forceLargeSteps) {
            return 15;
        }
        int min = Math.max(1, stepMin);
        int max = Math.max(min, stepMax);
        return Math.min(min, max);
    }

    public static int effectiveStepMax() {
        if (forceLargeSteps) {
            return 20;
        }
        int min = Math.max(1, stepMin);
        int max = Math.max(min, stepMax);
        return max;
    }

    /**
     * Dichter bij flag → langer wachten. Ver weg → {@link #minReclickMs}.
     *
     * @param flag huidige walk-flag ({@link Movement#getDestination()}) of klik-doel
     */
    public static int effectiveReclickMs(WorldPoint player, WorldPoint flag) {
        int base = Math.max(100, minReclickMs);
        if (!flagProximityReclick || player == null || flag == null
                || player.getPlane() != flag.getPlane()) {
            return base;
        }
        int nearMs = Math.max(base, reclickNearFlagMs);
        int near = Math.max(1, flagNearTiles);
        int far = Math.max(near + 1, flagFarTiles);
        int dist = player.distanceTo(flag);
        if (dist >= far) {
            return base;
        }
        if (dist <= near) {
            return nearMs;
        }
        double t = (far - dist) / (double) (far - near);
        return (int) Math.round(base + t * (nearMs - base));
    }

    /**
     * Extra floor voor far/canvas — <b>niet</b> vast 1.2s (dat voelt als stap-voor-stap bij korte hops).
     * Korte resterende afstand → 0 extra; alleen lange stukken krijgen geduld.
     */
    public static int canvasExtraMinReclickMs(WorldPoint from, WorldPoint flag, WorldPoint dest) {
        if (!useFarCanvasWalk || from == null) {
            return 0;
        }
        int dDest = distSamePlane(from, dest);
        int dFlag = distSamePlane(from, flag);
        int span = 0;
        if (dDest >= 0) {
            span = Math.max(span, dDest);
        }
        if (dFlag >= 0) {
            span = Math.max(span, dFlag);
        }
        if (span <= 8) {
            return 0;
        }
        if (span <= 14) {
            return 400;
        }
        return 850;
    }

    /** True = wacht tot dicht bij flag vóór herklik (alleen bij langere walks). */
    public static boolean shouldWaitNearFlag(WorldPoint from, WorldPoint flag, WorldPoint dest) {
        if (!useFarCanvasWalk || from == null || flag == null
                || from.getPlane() != flag.getPlane()) {
            return false;
        }
        int dDest = distSamePlane(from, dest);
        // Korte hop / bijna bij einddoel: geen flag-patience (voorkomt stap-loop)
        if (dDest >= 0 && dDest <= 10) {
            return false;
        }
        int needNear = Math.max(2, canvasReclickWithinTiles);
        return from.distanceTo(flag) > needNear;
    }

    private static int distSamePlane(WorldPoint a, WorldPoint b) {
        if (a == null || b == null || a.getPlane() != b.getPlane()) {
            return -1;
        }
        return a.distanceTo(b);
    }

    public static void apply(
            boolean minimap,
            boolean canvas,
            boolean uiZones,
            boolean cameraNudge,
            boolean invokeWalk
    ) {
        useMinimap = minimap;
        useCanvas = canvas;
        useUiZones = uiZones;
        useCameraNudge = cameraNudge;
        useInvokeWalk = invokeWalk;
    }

    public static void applyAll(
            boolean minimap,
            boolean canvas,
            boolean uiZones,
            boolean cameraNudge,
            boolean invokeWalk,
            boolean farCanvas,
            int stepMinTiles,
            int stepMaxTiles,
            boolean forceLarge,
            int reclickMs,
            int postMin,
            int postMax,
            boolean proximityReclick,
            int nearFlagReclickMs,
            boolean twinQuick
    ) {
        apply(minimap, canvas, uiZones, cameraNudge, invokeWalk);
        useFarCanvasWalk = farCanvas;
        stepMin = Math.max(1, Math.min(40, stepMinTiles));
        stepMax = Math.max(stepMin, Math.min(50, stepMaxTiles));
        forceLargeSteps = forceLarge;
        minReclickMs = Math.max(100, Math.min(5000, reclickMs));
        postClickDelayMinMs = Math.max(0, Math.min(3000, postMin));
        postClickDelayMaxMs = Math.max(postClickDelayMinMs, Math.min(5000, postMax));
        flagProximityReclick = proximityReclick;
        reclickNearFlagMs = Math.max(minReclickMs, Math.min(5000, nearFlagReclickMs));
        twinQuickClicks = twinQuick;
    }
}
