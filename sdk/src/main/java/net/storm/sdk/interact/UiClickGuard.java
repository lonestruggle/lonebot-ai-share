package net.storm.sdk.interact;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.storm.sdk.game.Static;
import net.storm.sdk.movement.WalkClickSettings;
import net.storm.sdk.movement.WalkUiZones;

/**
 * Voorkomt canvas-klikken door inventory / chat / minimap-chrome heen.
 */
public final class UiClickGuard {

    private UiClickGuard() {
    }

    /** {@code true} als UI-zones aan staan en het punt over chat/inv/minimap ligt. */
    public static boolean isBlocked(Point canvas) {
        if (canvas == null || canvas.getX() < 0 || canvas.getY() < 0) {
            return true;
        }
        if (!WalkClickSettings.useUiZones) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return WalkUiZones.isOverUi(c, canvas);
        }, true));
    }

    /** Eerste punt dat niet door UI geblokkeerd is; anders {@code null}. */
    public static Point firstUnblocked(Point... points) {
        if (points == null) {
            return null;
        }
        for (Point p : points) {
            if (p != null && p.getX() >= 0 && p.getY() >= 0 && !isBlocked(p)) {
                return p;
            }
        }
        return null;
    }
}
