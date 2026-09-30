package net.storm.sdk.game;

import net.runelite.api.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camera accessors. Prefer arrow-key / MMB drag (AntiBan) for human camera moves.
 * {@link #setYaw}/{@link #setPitch} snap via client targets — not for antiban/walk nudge.
 */
public final class Camera {

    private static final Logger log = LoggerFactory.getLogger(Camera.class);

    private Camera() {
    }

    public static int getYaw() {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? c.getCameraYaw() : 0;
        }, 0);
        return v != null ? v : 0;
    }

    public static int getPitch() {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? c.getCameraPitch() : 0;
        }, 0);
        return v != null ? v : 0;
    }

    /**
     * Sets camera yaw target (client will lerp).
     */
    public static void setYaw(int yaw) {
        Static.runOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return;
            }
            try {
                c.setCameraYawTarget(yaw);
            } catch (Throwable t) {
                log.debug("[Camera] setYaw failed: {}", t.toString());
            }
        });
    }

    /**
     * Sets camera pitch target (client will lerp).
     */
    public static void setPitch(int pitch) {
        Static.runOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return;
            }
            try {
                c.setCameraPitchTarget(pitch);
            } catch (Throwable t) {
                log.debug("[Camera] setPitch failed: {}", t.toString());
            }
        });
    }

    /**
     * Best-effort: camera considered locked if free-cam mode is active (mode != 0)
     * or shake-disabled flag is set. RL has no dedicated isCameraLocked.
     */
    public static boolean isCameraLocked() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.getCameraMode() != 0;
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }
}
