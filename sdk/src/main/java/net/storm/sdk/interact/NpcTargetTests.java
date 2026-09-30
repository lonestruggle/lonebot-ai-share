package net.storm.sdk.interact;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.interact.mouse.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meerdere manieren om NPC-targeting te testen (Storm/RL-stijl).
 * NPC-reads op client-thread; muis daarna op worker-thread.
 */
public final class NpcTargetTests {

    private static final Logger log = LoggerFactory.getLogger(NpcTargetTests.class);

    public enum Mode {
        /** Alleen Bezier naar hull-punt (geen klik). */
        MOVE_HULL,
        /** Bezier + klik op hull-punt. */
        CLICK_HULL,
        /** Bezier + klik op Perspective.localToCanvas (geen hull). */
        CLICK_LOCAL_TO_CANVAS,
        /** Menu invoke Attack (geen muis). */
        MENU_INVOKE
    }

    public static final class TargetInfo {
        public final boolean found;
        public final String name;
        public final int index;
        public final int worldX;
        public final int worldY;
        public final Point hullCanvas;
        public final Point localCanvas;
        public final String detail;

        public TargetInfo(boolean found, String name, int index, int worldX, int worldY,
                          Point hullCanvas, Point localCanvas, String detail) {
            this.found = found;
            this.name = name;
            this.index = index;
            this.worldX = worldX;
            this.worldY = worldY;
            this.hullCanvas = hullCanvas;
            this.localCanvas = localCanvas;
            this.detail = detail != null ? detail : "";
        }
    }

    private NpcTargetTests() {
    }

    /** Zoek dichtstbijzijnde Cow* + canvas-punten — volledig op client-thread. */
    public static TargetInfo findNearestCow() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null) {
                return new TargetInfo(false, null, -1, -1, -1, null, null, "geen client/player");
            }
            NPC best = null;
            int bestDist = Integer.MAX_VALUE;
            int total = 0;
            for (NPC npc : c.getNpcs()) {
                if (npc == null || npc.getName() == null) {
                    continue;
                }
                total++;
                String lower = npc.getName().trim().toLowerCase();
                if (!lower.equals("cow") && !lower.equals("cow calf") && !lower.startsWith("cow")) {
                    continue;
                }
                if (npc.getWorldLocation() == null) {
                    continue;
                }
                if (ExcludedTiles.isExcluded(npc.getWorldLocation())) {
                    continue;
                }
                int d = c.getLocalPlayer().getWorldLocation().distanceTo(npc.getWorldLocation());
                if (d > 15) {
                    continue;
                }
                if (d < bestDist) {
                    bestDist = d;
                    best = npc;
                }
            }
            if (best == null) {
                return new TargetInfo(false, null, -1, -1, -1, null, null,
                        "geen cow in range (npcScan=" + total + ")");
            }

            Point hull = randomInShape(safeHull(best));
            Point local = null;
            try {
                LocalPoint lp = best.getLocalLocation();
                if (lp != null) {
                    int h = Math.max(0, best.getLogicalHeight() / 2);
                    local = Perspective.localToCanvas(c, lp, c.getPlane(), h);
                }
            } catch (Throwable ignored) {
            }
            if (hull == null && local != null) {
                hull = local;
            }
            int wx = best.getWorldLocation().getX();
            int wy = best.getWorldLocation().getY();
            String detail = "hull=" + fmt(hull) + " local=" + fmt(local) + " dist=" + bestDist;
            return new TargetInfo(true, best.getName(), best.getIndex(), wx, wy, hull, local, detail);
        }, new TargetInfo(false, null, -1, -1, -1, null, null, "client-thread timeout"));
    }

    /** Voer één testmodus uit (vanaf worker-thread). */
    public static String run(Mode mode) {
        TargetInfo t = findNearestCow();
        BotRuntime.debugSummary = t.detail;
        if (!t.found) {
            BotRuntime.cowStatus = "test: " + t.detail;
            return "⚠ " + t.detail;
        }
        BotRuntime.mouseSummary = "target hull=" + fmt(t.hullCanvas) + " local=" + fmt(t.localCanvas)
                + " world=" + t.worldX + "," + t.worldY;

        switch (mode) {
            case MOVE_HULL: {
                Point p = firstPoint(t.hullCanvas, t.localCanvas);
                if (p == null) {
                    return "⚠ geen canvas-punt";
                }
                boolean ok = MouseManager.moveTo(p);
                return finish((ok ? "✓" : "⚠") + " MOVE_HULL → " + p.getX() + "," + p.getY());
            }
            case CLICK_HULL: {
                Point p = firstPoint(t.hullCanvas, t.localCanvas);
                if (p == null) {
                    return "⚠ geen canvas-punt";
                }
                boolean ok = MouseManager.interactAt(p);
                return finish((ok ? "✓" : "⚠") + " CLICK_HULL " + t.name + " @" + p.getX() + "," + p.getY());
            }
            case CLICK_LOCAL_TO_CANVAS: {
                Point p = firstPoint(t.localCanvas, t.hullCanvas);
                if (p == null) {
                    return "⚠ geen localToCanvas-punt";
                }
                boolean ok = MouseManager.interactAt(p);
                return finish((ok ? "✓" : "⚠") + " CLICK_L2C " + t.name + " @" + p.getX() + "," + p.getY());
            }
            case MENU_INVOKE: {
                final int idx = t.index;
                MenuInteract.ProbeResult r = Static.callOnClientThread(() -> {
                    Client c = Static.getClient();
                    if (c == null) {
                        return MenuInteract.ProbeResult.NO_CLIENT;
                    }
                    for (NPC n : c.getNpcs()) {
                        if (n != null && n.getIndex() == idx) {
                            return MenuInteract.probeNpcAttack(n);
                        }
                    }
                    return MenuInteract.ProbeResult.NO_ACTION;
                }, MenuInteract.ProbeResult.TIMEOUT);
                String msg = "MENU_INVOKE " + r.name() + " | " + MenuInteract.getLastProbeDetail();
                BotRuntime.menuProbeSummary = msg;
                BotRuntime.cowStatus = msg;
                log.info("[TargetTest] {}", msg);
                return msg;
            }
            default:
                return "onbekende mode";
        }
    }

    private static String finish(String msg) {
        String full = msg + " | " + Mouse.getLastProof();
        log.info("[TargetTest] {}", full);
        BotRuntime.cowStatus = full;
        return full;
    }

    private static Point firstPoint(Point a, Point b) {
        if (a != null && a.getX() >= 0 && a.getY() >= 0) {
            return a;
        }
        if (b != null && b.getX() >= 0 && b.getY() >= 0) {
            return b;
        }
        return null;
    }

    private static Shape safeHull(NPC npc) {
        try {
            return npc.getConvexHull();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Point randomInShape(Shape shape) {
        if (shape == null) {
            return null;
        }
        Rectangle b = shape.getBounds();
        if (b == null || b.width < 2 || b.height < 2) {
            return null;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            int x = b.x + r.nextInt(b.width);
            int y = b.y + r.nextInt(b.height);
            if (shape.contains(x, y)) {
                return new Point(x, y);
            }
        }
        return new Point(b.x + b.width / 2, b.y + b.height / 2);
    }

    private static String fmt(Point p) {
        return p == null ? "?" : p.getX() + "," + p.getY();
    }
}
