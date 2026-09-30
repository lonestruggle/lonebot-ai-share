package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.pathfinder.model.Transport;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime toggles for walk-click strategies (panel / config).
 * Storm2-achtig: stap min/max, reclick, delays, far-canvas, channels.
 */
public final class WalkClickSettings {

    private WalkClickSettings() {
    }

    public static volatile boolean useMinimap = false;
    /**
     * Minimap helemaal uitzoomen (1 px/tegel) zodat walk-hops niet op de rand vallen.
     */
    public static volatile boolean minimapFullyZoomedOut = true;
    public static volatile boolean useCanvas = false;
    public static volatile boolean useUiZones = true;
    public static volatile boolean useCameraNudge = true;
    public static volatile boolean useInvokeWalk = true;

    /**
     * Lage camera + yaw richting looprichting + canvas-first.
     */
    public static volatile boolean useFarCanvasWalk = false;

    /**
     * Stiles / agility-shortcuts. Uit = eromheen lopen, niet Climb-over.
     * Deuren/gates (Open) blijven wel.
     */
    public static volatile boolean useShortcuts = false;

    public static volatile int stepMin = 10;
    public static volatile int stepMax = 20;
    public static volatile boolean forceLargeSteps = false;

    /**
     * Laatste N <b>pad</b>-tegels tot B: click-on-sight op B.
     * Geen hemelsbrede afstand (omweg/hek kan 14 crow-fly zijn met 66 padtegels).
     */
    public static volatile int finalClickWithinTiles = 15;
    /** Als click-on-sight op B faalt: zoveel padtegels verder richting B. */
    public static volatile int finalClickFallbackTiles = 5;

    /** Basis reclick wanneer ver van de flag. Travel: kort — doorlinken. */
    public static volatile int minReclickMs = 250;
    /**
     * Dicht bij flag langer wachten vóór opnieuw klikken.
     * Uit = altijd {@link #minReclickMs} (geen 1.6s-stilstand).
     */
    public static volatile boolean flagProximityReclick = false;
    /** Reclick-ms als speler ≤ {@link #flagNearTiles} van de flag. */
    public static volatile int reclickNearFlagMs = 400;
    /** Afstand waarbij “dicht bij flag” begint. */
    public static volatile int flagNearTiles = 3;
    /** Afstand waarbij we weer de basis-reclick gebruiken. */
    public static volatile int flagFarTiles = 14;

    /**
     * Far/canvas lopen: niet opnieuw klikken tot speler binnen zoveel tegels van de
     * gele flag is — daarna doorlinken terwijl je nog rent.
     */
    public static volatile int canvasReclickWithinTiles = 10;
    /**
     * Volgende hop zó veel tegels vóór de gele flag (niet na 2 tegels van de hop).
     * Hop 6 → chain bij 4. Hop 12 → ~8 lopen, dan volgende 6–12.
     */
    public static volatile int chainNearFlagMin = 4;
    public static volatile int chainNearFlagMax = 4;
    /** Zelfde als {@link #chainNearFlagMin} — geen aparte Star-travel-versnelling. */
    public static volatile int travelChainNearFlagMin = 4;
    public static volatile int travelChainNearFlagMax = 4;
    private static volatile WorldPoint chainNearFlagKey;
    private static volatile int chainNearFlagTiles = 3;
    /** Laatste uitgegeven hop (tegels) — doorlinken schaalt mee. */
    private static volatile int lastHopTiles;

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

    public static volatile int postClickDelayMinMs = 60;
    public static volatile int postClickDelayMaxMs = 140;

    /** Korte pauze tussen hops bij doorlinken. */
    public static volatile int chainClickMinMs = 40;
    public static volatile int chainClickMaxMs = 120;

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

    /**
     * Travel altijd rennen. Lopen (stapvoets) alleen via {@link #setForceWalk(boolean)} /
     * {@link MovementHelper#walkToWalking(WorldPoint)}.
     */
    public static volatile boolean preferRun = true;
    /**
     * OSRS: run-orb gaat uit bij 0% energie en blijft uit tot je hem weer aanklikt.
     * Auto-run klikt de orb weer aan vanaf {@link #autoRunMinEnergy} (standaard 20).
     * Nooit aanklikken bij 0% of onder de drempel (OSRS weigert en spamt chat).
     */
    public static volatile boolean autoRun = true;
    public static volatile int autoRunMinEnergy = 20;
    private static final ThreadLocal<Boolean> FORCE_WALK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isForceWalk() {
        return Boolean.TRUE.equals(FORCE_WALK.get());
    }

    public static void setForceWalk(boolean walk) {
        FORCE_WALK.set(walk);
    }

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
     * Extra reclick-floor geschaald op afstand — <b>niet</b> vast 1.2s.
     * Alleen als we nog <b>ver</b> van de huidige flag zijn (niet bij doorlinken).
     */
    public static int canvasExtraMinReclickMs(WorldPoint from, WorldPoint flag, WorldPoint dest) {
        return 0;
    }

    /**
     * Reclick-wacht voor pad / doorlinken.
     * {@code finalDest} = eindpunt B (eindklik-regel). {@code nextClickDest} = volgende hop.
     */
    public static int pathChainReclickMs(WorldPoint from, WorldPoint flag, WorldPoint nextClickDest) {
        return pathChainReclickMs(from, flag, nextClickDest, nextClickDest);
    }

    public static int pathChainReclickMs(
            WorldPoint from, WorldPoint flag, WorldPoint nextClickDest, WorldPoint finalDest) {
        try {
            if (!Movement.isMoving() && Movement.getDestination() == null) {
                return chainClickDelayMs();
            }
        } catch (Throwable ignored) {
        }
        int dFinal = distSamePlane(from, finalDest);
        int close = Math.max(4, finalClickWithinTiles);
        boolean flagIsDest = flag != null && finalDest != null
                && flag.getPlane() == finalDest.getPlane()
                && flag.distanceTo(finalDest) <= 2;
        // Alleen eindklik-wacht als de flag al op B staat — niet bij crow-fly ≤15 met lang pad.
        if (flagIsDest && dFinal >= 0 && dFinal <= close) {
            return chainClickDelayMs();
        }

        // Alleen kort doorlinken als we écht bij de vlag zijn. Nieuwe hop-tegel ≠ meteen 40ms.
        if (nearFlagForChain(from, flag)) {
            return chainClickDelayMs();
        }

        int extra = canvasExtraMinReclickMs(from, flag, finalDest != null ? finalDest : nextClickDest);
        return Math.max(effectiveReclickMs(from, flag != null ? flag : nextClickDest), extra);
    }

    /**
     * Tegels tot de gele vlag waarbij we de volgende hop klikken:
     * max(4, hop/3) — hop 18 → keten bij 6, nog rennend.
     */
    public static int chainRemainAllow() {
        int hop = lastHopTiles > 0 ? lastHopTiles : effectiveStepMin();
        hop = Math.max(effectiveStepMin(), Math.min(hop, effectiveStepMax()));
        return Math.max(4, hop / 3);
    }

    /**
     * True = nog te ver van de vlag → geen nieuwe klik.
     * False = ≤4 tegels (of vlag stale) → volgende hop.
     */
    public static boolean shouldWaitTwoThirdsToFlag(WorldPoint from, WorldPoint flag) {
        if (from == null || flag == null || from.getPlane() != flag.getPlane()) {
            return false;
        }
        int hop = lastHopTiles > 0 ? lastHopTiles : effectiveStepMin();
        hop = Math.max(effectiveStepMin(), Math.min(hop, effectiveStepMax()));
        int d = from.distanceTo(flag);
        // Vlag achter ons / verkeerde tegel: niet wachten tot stall.
        if (d > hop + 2) {
            return false;
        }
        return d > chainRemainAllow();
    }

    /**
     * True = nog te ver van de gele flag → wacht (geen nieuwe klik).
     * Eindstretch: wacht zolang je naar B rent. Anders: 2/3-regel.
     */
    public static boolean shouldWaitNearFlag(WorldPoint from, WorldPoint flag, WorldPoint dest) {
        if (from == null) {
            return false;
        }
        int dDest = distSamePlane(from, dest);
        int close = Math.max(4, finalClickWithinTiles);
        boolean flagIsDest = flag != null && dest != null
                && flag.getPlane() == dest.getPlane()
                && flag.distanceTo(dest) <= 2;
        if (flagIsDest && dDest >= 0 && dDest <= close) {
            try {
                return Movement.isMoving();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return shouldWaitTwoThirdsToFlag(from, flag);
    }

    private static int distSamePlane(WorldPoint a, WorldPoint b) {
        if (a == null || b == null || a.getPlane() != b.getPlane()) {
            return -1;
        }
        return a.distanceTo(b);
    }

    public static int lastHopTiles() {
        return lastHopTiles;
    }

    /**
     * Per gele-flag-tegel: volgende hop bij 4 tegels (geen aparte travel-afstand).
     */
    public static void noteHopTiles(int tiles) {
        int max = effectiveStepMax();
        lastHopTiles = Math.max(0, Math.min(max, tiles));
    }

    public static int chainNearFlagDistance(WorldPoint flag) {
        boolean travel = false;
        try {
            travel = MovementHelper.isTravelContext();
        } catch (Throwable ignored) {
        }
        int lo = travel ? travelChainNearFlagMin : chainNearFlagMin;
        int hi = travel ? travelChainNearFlagMax : chainNearFlagMax;
        lo = Math.max(1, Math.min(lo, hi));
        hi = Math.max(lo, hi);
        if (flag == null) {
            return hi;
        }
        WorldPoint key = chainNearFlagKey;
        if (key == null || key.getPlane() != flag.getPlane() || key.distanceTo(flag) > 1) {
            chainNearFlagKey = flag;
            chainNearFlagTiles = lo >= hi ? hi : ThreadLocalRandom.current().nextInt(lo, hi + 1);
        }
        return chainNearFlagTiles;
    }

    /** True als we dicht genoeg bij de gele flag zijn om de volgende hop te klikken. */
    public static boolean nearFlagForChain(WorldPoint from, WorldPoint flag) {
        if (from == null || flag == null || from.getPlane() != flag.getPlane()) {
            return false;
        }
        return from.distanceTo(flag) <= chainNearFlagDistance(flag);
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
            boolean twinQuick,
            int chainMin,
            int chainMax
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
        setChainClickDelay(chainMin, chainMax);
    }

    public static int chainClickDelayMs() {
        int lo = Math.max(20, Math.min(800, chainClickMinMs));
        int hi = Math.max(lo, Math.min(1500, chainClickMaxMs));
        if (hi <= lo) {
            return lo;
        }
        return ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    public static void setChainClickDelay(int minMs, int maxMs) {
        chainClickMinMs = Math.max(20, Math.min(800, minMs));
        chainClickMaxMs = Math.max(chainClickMinMs, Math.min(1500, maxMs));
    }

    public static void setFinalClickWithinTiles(int tiles) {
        finalClickWithinTiles = Math.max(4, Math.min(40, tiles));
    }

    /** Stile / agility-shortcut — niet een gewone deur of gate. */
    public static boolean isShortcut(Transport t) {
        if (t == null) {
            return false;
        }
        String n = t.getName();
        if (n == null || n.isBlank()) {
            return false;
        }
        String low = n.toLowerCase(Locale.ROOT);
        return low.contains("stile")
                || low.contains("shortcut")
                || low.contains("agility")
                || low.contains("climb-over");
    }
}
