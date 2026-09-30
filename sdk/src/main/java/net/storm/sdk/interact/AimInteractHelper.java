package net.storm.sdk.interact;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Camera;
import net.storm.sdk.game.Static;
import net.storm.sdk.movement.WalkUiZones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Shape;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NPC interact: <b>alleen menu-invoke</b> (geen eigen muis).
 * Login-schermen blijven buiten deze helper.
 */
public final class AimInteractHelper {

    private static final Logger log = LoggerFactory.getLogger(AimInteractHelper.class);

    private static volatile long lastTurnMs;
    private static volatile String lastDetail = "";

    private AimInteractHelper() {
    }

    public static String getLastDetail() {
        return lastDetail != null ? lastDetail : "";
    }

    /**
     * Interact met NPC via {@link MenuInteract} — geen canvas-LMB / Bezier.
     */
    public static boolean interactNpc(INPC npc, String action) {
        if (npc == null || action == null) {
            lastDetail = "npc/action null";
            return false;
        }
        String name = npc.getName() != null ? npc.getName() : "?";
        return invokeNpc(npc, action, name);
    }

    /** True als canvas-punt binnen/near convex hull van de NPC ligt. */
    public static boolean clickPointStillOnNpc(INPC npc, Point click) {
        if (npc == null || click == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            NPC raw = null;
            try {
                for (NPC n : c.getNpcs()) {
                    if (n != null && n.getIndex() == npc.getIndex()) {
                        raw = n;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (raw == null) {
                return false;
            }
            try {
                Shape hull = raw.getConvexHull();
                if (hull != null && hull.contains(click.getX(), click.getY())) {
                    return true;
                }
                if (hull != null) {
                    java.awt.Rectangle b = hull.getBounds();
                    if (b != null) {
                        int cx = b.x + b.width / 2;
                        int cy = b.y + b.height / 2;
                        int d = Math.abs(click.getX() - cx) + Math.abs(click.getY() - cy);
                        return d <= Math.max(28, (b.width + b.height) / 2);
                    }
                }
            } catch (Throwable ignored) {
            }
            Point fresh = ClickPoints.forNpcOnClient(npc);
            if (fresh == null) {
                return false;
            }
            return Math.abs(fresh.getX() - click.getX()) + Math.abs(fresh.getY() - click.getY()) <= 40;
        }, false));
    }

    private static boolean invokeNpc(INPC npc, String action, String name) {
        boolean inv = MenuInteract.interactNpcByIndex(npc.getIndex(), action);
        lastDetail = inv
                ? "invoke " + action + " → " + name + " (" + MenuInteract.getLastProbeDetail() + ")"
                : "fail " + action + " → " + name;
        log.info("[AimInteract] {}", lastDetail);
        if (inv) {
            BotRuntime.logConsole("[Aim] " + lastDetail);
        }
        return inv;
    }

    public static boolean turnToward(WorldPoint target) {
        long now = System.currentTimeMillis();
        if (now - lastTurnMs < 900L) {
            return false;
        }
        if (target == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Player me = c.getLocalPlayer();
            if (me == null || me.getWorldLocation() == null) {
                return false;
            }
            WorldPoint from = me.getWorldLocation();
            int dx = target.getX() - from.getX();
            int dy = target.getY() - from.getY();
            if (dx == 0 && dy == 0) {
                return false;
            }
            int yaw = ((int) (Math.atan2(dx, dy) * 325.949D)) & 2047;
            try {
                c.setCameraYawTarget(yaw);
            } catch (Throwable t) {
                Camera.setYaw(yaw);
            }
            try {
                int pitch = c.getCameraPitch();
                if (pitch < 280) {
                    c.setCameraPitchTarget(Math.min(383, pitch + 60 + ThreadLocalRandom.current().nextInt(40)));
                }
            } catch (Throwable ignored) {
            }
            log.info("[AimInteract] camera yaw→{} toward {}", yaw, target);
            return true;
        }, false);
        if (Boolean.TRUE.equals(ok)) {
            lastTurnMs = now;
            return true;
        }
        return false;
    }

    public static boolean isValidClick(Point p) {
        if (p == null || p.getX() < 8 || p.getY() < 8) {
            return false;
        }
        try {
            Client c = Static.getClient();
            if (c != null && WalkUiZones.isOverUi(c, p)) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static boolean isNpcOnScreen(INPC npc) {
        return isValidClick(ClickPoints.forNpc(npc));
    }
}
