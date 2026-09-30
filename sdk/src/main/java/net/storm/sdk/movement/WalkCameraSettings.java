package net.storm.sdk.movement;

/**
 * Tunable walk-camera settings (panel / config). Used by {@link WalkCamera}.
 */
public final class WalkCameraSettings {

    public enum YawMode {
        /** Camera kijkt dezelfde kant op als looprichting. */
        FOLLOW,
        /** Camera kijkt tegengesteld (180°) — speler loopt “naar” camera. */
        CONTRA,
        /** FOLLOW + extra offset in graden. */
        OFFSET
    }

    private WalkCameraSettings() {
    }

    public static volatile boolean enabled = true;

    /** Vloeiende MMB-drag i.p.v. harde setYaw/setPitch. */
    public static volatile boolean humanMmb = true;

    /**
     * Walk-camera via pijltjestoetsen i.p.v. MMB.
     * Als true: yaw/pitch met ←→↑↓; zoom blijft scrollwiel.
     */
    public static volatile boolean useKeyboard = false;

    public static volatile YawMode yawMode = YawMode.FOLLOW;

    /** Extra yaw in graden (−180…180), toegepast na mode. */
    public static volatile int yawOffsetDeg = 0;

    /** Live client-pitch range (overlay p=…); default = jouw loop-hoek. */
    public static final int PITCH_ABS_MIN = 128;
    public static final int PITCH_ABS_MAX = 4096;

    /**
     * Doel-pitch (client {@code getCameraPitch()}). Default 3064 = gemeten loop-hoek.
     */
    public static volatile int pitchTarget = 3064;

    /** Toegestane band rond pitchTarget. */
    public static volatile int pitchBand = 30;

    /**
     * Skip camera alleen als we al goed genoeg gericht zijn (0–100).
     * face% = 100 − (yawError/1024)*100. 75% ≈ max ~45° scheef.
     * Lager (bv. 23%) skipt te vroeg — camera blijft zijwaarts (zoals bug-report).
     */
    public static volatile int visibilitySkipPercent = 70;

    /**
     * Zoom 0–100: 0 = volledig uit, 100 = sterk in.
     * Default laag = verder uitgezoomd voor canvas-lopen.
     */
    public static volatile int zoomPercent = 5;

    public static final int ZOOM_SCALE_OUT = 200;
    public static final int ZOOM_SCALE_IN = 720;

    /** Live overlay / debug. */
    public static volatile int lastYaw = -1;
    public static volatile int lastPitch = -1;
    public static volatile int lastScale = -1;
    public static volatile int lastFacePercent = -1;
    public static volatile String lastSkipReason = "-";

    public static void apply(
            boolean on,
            boolean mmbHuman,
            YawMode mode,
            int offsetDeg,
            int pitch,
            int band,
            int zoomPct
    ) {
        enabled = on;
        humanMmb = mmbHuman;
        yawMode = mode != null ? mode : YawMode.FOLLOW;
        yawOffsetDeg = clamp(offsetDeg, -180, 180);
        pitchTarget = clamp(pitch, PITCH_ABS_MIN, PITCH_ABS_MAX);
        pitchBand = clamp(band, 0, 400);
        zoomPercent = clamp(zoomPct, 0, 100);
        WalkCamera.setEnabled(enabled);
    }

    public static void apply(
            boolean on,
            YawMode mode,
            int offsetDeg,
            int pitch,
            int band,
            int zoomScaleMax
    ) {
        apply(on, true, mode, offsetDeg, pitch, band, scaleToPercent(zoomScaleMax));
    }

    public static int pitchMin() {
        return clamp(pitchTarget - pitchBand, PITCH_ABS_MIN, PITCH_ABS_MAX);
    }

    public static int pitchMax() {
        return clamp(pitchTarget + pitchBand, PITCH_ABS_MIN, PITCH_ABS_MAX);
    }

    public static int zoomScaleTarget() {
        return ZOOM_SCALE_OUT + (zoomPercent * (ZOOM_SCALE_IN - ZOOM_SCALE_OUT)) / 100;
    }

    public static int scaleToPercent(int scale) {
        int s = clamp(scale, ZOOM_SCALE_OUT, ZOOM_SCALE_IN);
        return ((s - ZOOM_SCALE_OUT) * 100) / (ZOOM_SCALE_IN - ZOOM_SCALE_OUT);
    }

    public static int degToJau(int deg) {
        return (int) Math.round(deg * 2048.0 / 360.0) & 2047;
    }

    public static String overlayAnglesLine() {
        return "y=" + lastYaw + " p=" + lastPitch + " s=" + lastScale
                + " face=" + lastFacePercent + "%";
    }

    public static String reportBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== WalkCamera REPORT ===\n");
        sb.append("enabled=").append(enabled).append('\n');
        sb.append("humanMmb=").append(humanMmb).append('\n');
        sb.append("useKeyboard=").append(useKeyboard).append('\n');
        sb.append("yawMode=").append(yawMode).append('\n');
        sb.append("yawOffsetDeg=").append(yawOffsetDeg).append('\n');
        sb.append("pitchTarget=").append(pitchTarget).append('\n');
        sb.append("pitchBand=").append(pitchBand)
                .append(" (min=").append(pitchMin()).append(" max=").append(pitchMax()).append(")\n");
        sb.append("visibilitySkipPercent=").append(visibilitySkipPercent).append('\n');
        sb.append("zoomPercent=").append(zoomPercent)
                .append(" → scaleTarget=").append(zoomScaleTarget()).append('\n');
        sb.append("live=").append(WalkCamera.liveSnapshotLine()).append('\n');
        sb.append("lastSkip=").append(lastSkipReason).append('\n');
        sb.append("=========================\n");
        sb.append("Kopieer dit blok en stuur naar de AI voor de perfecte defaults.");
        return sb.toString();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
