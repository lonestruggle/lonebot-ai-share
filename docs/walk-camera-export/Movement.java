package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.game.Static;

/**
 * Low-level walk — delegates to {@link WalkClickHelper} (minimap-first, UI-safe, invoke WALK).
 */
public final class Movement {

    private Movement() {
    }

    /**
     * Walk toward a world tile using {@link WalkClickHelper} strategies.
     */
    public static boolean walkTo(WorldPoint worldPoint) {
        return WalkClickHelper.walkTo(worldPoint);
    }

    /** Walk to local scene point via world conversion. */
    public static boolean walkToLocal(LocalPoint localPoint) {
        if (localPoint == null) {
            return false;
        }
        WorldPoint wp = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? WorldPoint.fromLocal(c, localPoint) : null;
        }, null);
        return walkTo(wp);
    }

    public static boolean canClickWalk(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return false;
        }
        // Cheap check: in scene or has minimap projection handled inside helper resolve
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            return LocalPoint.fromWorld(c, worldPoint) != null;
        }, false);
    }

    public static Point resolveWalkClick(WorldPoint worldPoint) {
        // Kept for callers; prefer WalkClickHelper.walkTo for actual walking
        if (worldPoint == null) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, worldPoint);
            if (lp == null) {
                return null;
            }
            Point mini = net.runelite.api.Perspective.localToMinimap(
                    c, lp, net.runelite.api.Perspective.LOCAL_TILE_SIZE * 70);
            if (mini != null && mini.getX() >= 0) {
                return mini;
            }
            return net.runelite.api.Perspective.localToCanvas(c, lp, worldPoint.getPlane());
        }, null);
    }

    public static boolean isMoving() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Player p = c.getLocalPlayer();
            if (p == null) {
                return false;
            }
            return p.getPoseAnimation() != p.getIdlePoseAnimation();
        }, false));
    }

    public static int distanceTo(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return Integer.MAX_VALUE;
        }
        Integer d = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null) {
                return Integer.MAX_VALUE;
            }
            WorldPoint me = c.getLocalPlayer().getWorldLocation();
            return me != null ? me.distanceTo(worldPoint) : Integer.MAX_VALUE;
        }, Integer.MAX_VALUE);
        return d != null ? d : Integer.MAX_VALUE;
    }

    public static boolean isNear(WorldPoint worldPoint, int maxDistance) {
        return distanceTo(worldPoint) <= maxDistance;
    }

    public static WorldPoint getDestination() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            LocalPoint dest = c.getLocalDestinationLocation();
            if (dest == null) {
                return null;
            }
            return WorldPoint.fromLocal(c, dest);
        }, null);
    }

    public static boolean hasDestination() {
        return getDestination() != null;
    }

    /**
     * Toggle run energy on/off via orb click (best-effort).
     */
    public static boolean toggleRun() {
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                // Minimap orb run — classic fixed classic widget
                net.runelite.api.widgets.Widget w = c.getWidget(160, 27);
                if (w == null || w.isHidden()) {
                    w = c.getWidget(160, 22);
                }
                if (w == null) {
                    return null;
                }
                return net.storm.sdk.interact.ClickPoints.forWidgetOnClient(w);
            } catch (Throwable t) {
                return null;
            }
        }, null);
        if (click == null) {
            return false;
        }
        return net.storm.sdk.interact.mouse.MouseManager.interactAt(click);
    }

    public static boolean isRunEnabled() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null && c.getVarpValue(173) == 1;
        }, false));
    }

    /** Path length in tiles via global pathfinder; falls back to Chebyshev. */
    public static int calculateDistance(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return Integer.MAX_VALUE;
        }
        try {
            net.storm.api.movement.TilePath path = MovementHelper.getPath(worldPoint);
            if (path != null && !path.isEmpty()) {
                return path.size();
            }
        } catch (Throwable ignored) {
        }
        return distanceTo(worldPoint);
    }

    /** Walk toward destination until within radius tiles. */
    public static boolean walkToArea(WorldPoint center, int radius) {
        if (center == null) {
            return false;
        }
        if (distanceTo(center) <= Math.max(0, radius)) {
            return true;
        }
        return walkTo(center);
    }
}
