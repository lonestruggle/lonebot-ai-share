package net.storm.sdk.utils;

/**
 * Runtime toggles for {@link AntiBan} (panel / config).
 * Mirrors CombatBot anti-ban feature flags + camera MMB params.
 */
public final class AntiBanSettings {

    private AntiBanSettings() {
    }

    public static volatile boolean enabled = true;
    /** Uit: invoke-only — geen MMB-camera / pijltjes (Camera.setYaw blijft elders). */
    public static volatile boolean cameraMovement = false;
    public static volatile boolean idleChecks = true;
    /** Uit: geen eigen muis-beweging. */
    public static volatile boolean randomMouseMovement = false;
    public static volatile boolean misClickEnabled = false;
    public static volatile boolean tabGlanceEnabled = false;
    /** Uit: geen continuous mouse-fidget. */
    public static volatile boolean mouseFidgetEnabled = false;
    /** Uit: geen keyboard-pan. */
    public static volatile boolean keyboardPanEnabled = false;
    /** Base seconds between anti-ban actions (jitter ±50%). */
    public static volatile int frequencySec = 40;

    // ---- Camera MMB (CombatBotConfig defaults) ----
    public static volatile int cameraDurationMin = 600;
    public static volatile int cameraDurationMax = 2500;
    public static volatile int mmbDragSpeedMin = 25;
    public static volatile int mmbDragSpeedMax = 50;
    public static volatile int mmbDragDistanceMin = 40;
    public static volatile int mmbDragDistanceMax = 120;

    public static void apply(
            boolean enabledFlag,
            boolean camera,
            boolean idle,
            boolean mouse,
            boolean misclick,
            boolean tabGlance,
            boolean fidget,
            boolean keyboardPan,
            int frequency
    ) {
        enabled = enabledFlag;
        cameraMovement = camera;
        idleChecks = idle;
        randomMouseMovement = mouse;
        misClickEnabled = misclick;
        tabGlanceEnabled = tabGlance;
        mouseFidgetEnabled = fidget;
        keyboardPanEnabled = keyboardPan;
        frequencySec = Math.max(8, frequency);
    }

    public static void applyCameraTiming(
            int durationMin,
            int durationMax,
            int dragSpeedMin,
            int dragSpeedMax,
            int dragDistMin,
            int dragDistMax
    ) {
        cameraDurationMin = Math.max(100, durationMin);
        cameraDurationMax = Math.max(cameraDurationMin + 1, durationMax);
        mmbDragSpeedMin = Math.max(5, dragSpeedMin);
        mmbDragSpeedMax = Math.max(mmbDragSpeedMin + 1, dragSpeedMax);
        mmbDragDistanceMin = Math.max(10, dragDistMin);
        mmbDragDistanceMax = Math.max(mmbDragDistanceMin + 1, dragDistMax);
    }
}
