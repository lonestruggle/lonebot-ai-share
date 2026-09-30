package net.storm.sdk.interact;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.Locatable;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Static;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.Reachable;
import net.storm.sdk.movement.WalkUiZones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Click-when-reachable: interact as soon as the target is in scene, within
 * {@link #INVOKE_RANGE_TILES}, and the client can path to it. On-screen uses
 * hull/clickbox; <b>off-screen uses menu-invoke</b>. Do <b>not</b> walk next to it first.
 * <p>
 * Blocked by a closed door / wall → open the door or walk to the obstacle.
 * Farther than invoke-range or not in scene → walk toward (not stand adjacent).
 */
public final class ClickOnSight {

    private static final Logger log = LoggerFactory.getLogger(ClickOnSight.class);

    /**
     * Chebyshev tiles: invoke/interact even when off canvas.
     * OSRS scene is large enough; beyond this we walk closer first.
     */
    public static final int INVOKE_RANGE_TILES = 40;

    public enum Outcome {
        /** Interact issued (client will walk if needed). */
        CLICKED,
        /** Camera yaw/pitch toward target — retry next tick. */
        CAMERA,
        /** Walk toward (not “stand on the tile first”). */
        WALK,
        /** Closed door / wall — Open issued or walked to the obstacle. */
        BLOCKED,
        /** No entity / no action. */
        NONE
    }

    public static final class Result {
        public final Outcome outcome;
        public final String detail;
        public final boolean clicked;

        Result(Outcome outcome, String detail) {
            this.outcome = outcome != null ? outcome : Outcome.NONE;
            this.detail = detail != null ? detail : "";
            this.clicked = this.outcome == Outcome.CLICKED;
        }

        @Override
        public String toString() {
            return outcome + " " + detail;
        }
    }

    private static volatile String lastDetail = "";
    private static volatile long lastLogMs;
    private static volatile String lastLogLine = "";

    private ClickOnSight() {
    }

    public static String getLastDetail() {
        return lastDetail != null ? lastDetail : "";
    }

    // ---------- can / on-screen / path ----------

    public static boolean can(INPC npc) {
        return npc != null && pathOk(npc) && samePlane(npc) && inInvokeRange(npc.getWorldLocation());
    }

    public static boolean can(ITileObject obj) {
        return obj != null && pathOk(obj) && samePlane(obj) && inInvokeRange(obj.getWorldLocation());
    }

    public static boolean onScreen(INPC npc) {
        return AimInteractHelper.isNpcOnScreen(npc);
    }

    public static boolean onScreen(ITileObject obj) {
        return AimInteractHelper.isValidClick(ClickPoints.forITileObject(obj));
    }

    public static boolean pathOk(Locatable loc) {
        return loc != null && Reachable.isInteractable(loc);
    }

    // ---------- interact ----------

    public static Result interact(INPC npc, String... actions) {
        if (npc == null) {
            return fail("npc null");
        }
        if (!can(npc)) {
            return fail("cannot click " + nameOf(npc)
                    + " path=" + pathOk(npc) + " d=" + distOf(npc.getWorldLocation())
                    + " onScreen=" + onScreen(npc));
        }
        if (tryNpcActions(npc, actions)) {
            return ok(Outcome.CLICKED, "NPC " + nameOf(npc));
        }
        return fail("interact fail " + nameOf(npc));
    }

    public static Result interact(ITileObject obj, String... actions) {
        if (obj == null) {
            return fail("object null");
        }
        if (!can(obj)) {
            return fail("cannot click " + nameOf(obj)
                    + " path=" + pathOk(obj) + " d=" + distOf(obj.getWorldLocation())
                    + " onScreen=" + onScreen(obj));
        }
        if (tryObjectActions(obj, actions)) {
            return ok(Outcome.CLICKED, "obj " + nameOf(obj));
        }
        return fail("interact fail " + nameOf(obj));
    }

    /**
     * Click/invoke if in range + path ok (also off-screen); otherwise door / walk toward {@code approach}.
     */
    public static Result interactOrApproach(INPC npc, WorldPoint approach, String... actions) {
        if (npc == null) {
            return approachWalk(approach, "npc not in scene");
        }
        WorldPoint dest = npc.getWorldLocation() != null ? npc.getWorldLocation() : approach;
        return decide(onScreen(npc), pathOk(npc), dest, approach,
                () -> tryNpcActions(npc, actions), nameOf(npc), true);
    }

    public static Result interactOrApproach(ITileObject obj, WorldPoint approach, String... actions) {
        if (obj == null) {
            return approachWalk(approach, "object not in scene");
        }
        WorldPoint dest = obj.getWorldLocation() != null ? obj.getWorldLocation() : approach;
        return decide(onScreen(obj), pathOk(obj), dest, approach,
                () -> tryObjectActions(obj, actions), nameOf(obj), false);
    }

    // ---------- internals ----------

    private static Result decide(boolean onScreen, boolean pathOk, WorldPoint dest,
                                 WorldPoint approach, java.util.function.BooleanSupplier click,
                                 String name, boolean npc) {
        WorldPoint me = localTile();
        int dist = distOf(dest);
        String kind = npc ? "NPC " : "obj ";

        if (!pathOk) {
            WorldPoint door = firstClosedDoorBetween(me, dest);
            if (door != null && tryOpenDoorAt(door)) {
                return ok(Outcome.BLOCKED, "Open door @" + fmt(door) + " → " + name);
            }
            WorldPoint walkTo = door != null ? door : (dest != null ? dest : approach);
            MovementHelper.walkTo(walkTo);
            return ok(Outcome.BLOCKED, "blocked → walk " + fmt(walkTo) + " " + name);
        }

        if (inInvokeRange(dest)) {
            if (click.getAsBoolean()) {
                return ok(Outcome.CLICKED, (onScreen ? "click " : "invoke ") + kind + name
                        + " d=" + dist);
            }
            return fail("click fail " + name + " d=" + dist + " onScreen=" + onScreen);
        }

        WorldPoint walkTo = dest != null ? dest : approach;
        if (walkTo == null) {
            return fail("no dest " + name);
        }
        if (MovementHelper.alreadyEnRoute(walkTo)) {
            return ok(Outcome.WALK, "al onderweg → " + fmt(walkTo) + " " + name);
        }
        MovementHelper.walkTo(walkTo);
        return ok(Outcome.WALK, "walk toward " + fmt(walkTo) + " " + name + " d=" + dist);
    }

    private static Result approachWalk(WorldPoint approach, String why) {
        if (approach == null) {
            return fail(why);
        }
        if (MovementHelper.alreadyEnRoute(approach)) {
            return ok(Outcome.WALK, "al onderweg → " + fmt(approach));
        }
        MovementHelper.walkTo(approach);
        return ok(Outcome.WALK, why + " → " + fmt(approach));
    }

    /**
     * Open een gesloten deur op/naast {@code tile}.
     */
    public static boolean openDoorAt(WorldPoint tile) {
        return tryOpenDoorAt(tile);
    }

    /**
     * First closed door (walled + door object) on a Bresenham line. Null if none.
     */
    public static WorldPoint firstClosedDoorBetween(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return null;
        }
        int x0 = from.getX();
        int y0 = from.getY();
        int x1 = to.getX();
        int y1 = to.getY();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        int plane = from.getPlane();
        while (x != x1 || y != y1) {
            int e2 = 2 * err;
            int nx = x;
            int ny = y;
            if (e2 > -dy) {
                err -= dy;
                nx += sx;
            }
            if (e2 < dx) {
                err += dx;
                ny += sy;
            }
            WorldPoint cur = new WorldPoint(x, y, plane);
            WorldPoint next = new WorldPoint(nx, ny, plane);
            if (Math.abs(nx - x) + Math.abs(ny - y) == 1) {
                Direction dir = Reachable.directionBetween(cur, next);
                if (dir != null && Reachable.isDoored(cur, next)) {
                    return cur;
                }
            }
            x = nx;
            y = ny;
        }
        return null;
    }

    /**
     * Gesloten deur / gate / stile op het pad: Open of Climb-over.
     */
    public static boolean isClosedWalkBarrier(ITileObject o) {
        if (o == null) {
            return false;
        }
        if (net.storm.sdk.movement.pathfinder.AlKharidGate.isGateObject(o.getId())
                || net.storm.sdk.movement.pathfinder.AlKharidGate.isGateTile(o.getWorldLocation())) {
            return false;
        }
        boolean open = o.hasAction("Open") || o.hasAction("Open-door") || o.hasAction("Climb-over");
        if (!open) {
            return false;
        }
        String name = o.getName();
        if (name == null || name.isBlank() || name.equals("null")) {
            return o.hasAction("Open") || o.hasAction("Open-door");
        }
        String low = name.toLowerCase(Locale.ROOT);
        if (low.contains("stile") && !net.storm.sdk.movement.WalkClickSettings.useShortcuts) {
            return false;
        }
        return low.contains("door") || low.contains("gate") || low.contains("stile")
                || low.contains("portcullis");
    }

    private static boolean tryOpenDoorAt(WorldPoint tile) {
        if (tile == null) {
            return false;
        }
        ITileObject door = TileObjects.getNearest(tile, o -> o != null
                && o.getWorldLocation() != null
                && o.getWorldLocation().distanceTo(tile) <= 1
                && isClosedWalkBarrier(o));
        if (door == null) {
            return false;
        }
        if (door.hasAction("Open") && door.interact("Open")) {
            return true;
        }
        if (door.hasAction("Open-door") && door.interact("Open-door")) {
            return true;
        }
        if (!net.storm.sdk.movement.WalkClickSettings.useShortcuts) {
            return false;
        }
        return door.hasAction("Climb-over") && door.interact("Climb-over");
    }

    private static boolean tryNpcActions(INPC npc, String... actions) {
        if (npc == null) {
            return false;
        }
        if (actions != null) {
            for (String a : actions) {
                if (a == null || a.isBlank()) {
                    continue;
                }
                // Named action altijd via menu-invoke. Left-click op de hull is de
                // eerste optie (Draynor: Small Net i.p.v. Bait) — zelfde als Storm spot.interact("Bait").
                if (MenuInteract.interactNpcByIndex(npc.getIndex(), a)) {
                    return true;
                }
                if (npc.hasAction(a) && AimInteractHelper.interactNpc(npc, a)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tryObjectActions(ITileObject obj, String... actions) {
        if (obj == null) {
            return false;
        }
        boolean screen = onScreen(obj);
        TileObject raw = TileObjects.unwrap(obj);
        if (actions != null) {
            for (String a : actions) {
                if (a == null || a.isBlank()) {
                    continue;
                }
                if (!obj.hasAction(a)) {
                    continue;
                }
                if (!screen && raw != null && MenuInteract.interactObject(raw, a)) {
                    return true;
                }
                if (raw != null && MenuInteract.interactObject(raw, a)) {
                    return true;
                }
                try {
                    if (screen && TileObjectInteractHelper.interact(obj, a)) {
                        return true;
                    }
                } catch (NoClassDefFoundError | Exception ignored) {
                }
                if (obj.interact(a)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean inInvokeRange(WorldPoint dest) {
        int d = distOf(dest);
        return d >= 0 && d <= INVOKE_RANGE_TILES;
    }

    private static int distOf(WorldPoint dest) {
        WorldPoint me = localTile();
        if (me == null || dest == null || me.getPlane() != dest.getPlane()) {
            return -1;
        }
        return me.distanceTo(dest);
    }

    private static boolean samePlane(Locatable loc) {
        if (loc == null || loc.getWorldLocation() == null) {
            return false;
        }
        WorldPoint me = localTile();
        return me != null && me.getPlane() == loc.getWorldLocation().getPlane();
    }

    private static WorldPoint localTile() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me != null && me.present ? me.worldLocation : null;
    }

    private static String nameOf(INPC n) {
        try {
            String s = n != null ? n.getName() : null;
            return s != null && !s.isBlank() ? s : "npc";
        } catch (Throwable t) {
            return "npc";
        }
    }

    private static String nameOf(ITileObject o) {
        try {
            String s = o != null ? o.getName() : null;
            return s != null && !s.isBlank() ? s : "object";
        } catch (Throwable t) {
            return "object";
        }
    }

    private static String fmt(WorldPoint p) {
        return p == null ? "?" : p.getX() + "," + p.getY();
    }

    private static Result ok(Outcome outcome, String detail) {
        lastDetail = outcome + " " + detail;
        logSight(lastDetail);
        return new Result(outcome, detail);
    }

    private static Result fail(String detail) {
        lastDetail = "NONE " + detail;
        logSight(lastDetail);
        return new Result(Outcome.NONE, detail);
    }

    private static void logSight(String line) {
        long now = System.currentTimeMillis();
        if (line.equals(lastLogLine) && now - lastLogMs < 1400L) {
            return;
        }
        lastLogLine = line;
        lastLogMs = now;
        log.info("[ClickOnSight] {}", line);
        BotRuntime.logConsole("[Sight] " + line);
    }

    /** Package helper used by {@link ClickPoints}. */
    static boolean validCanvas(Client c, Point p) {
        if (!AimInteractHelper.isValidClick(p) || c == null) {
            return false;
        }
        return !WalkUiZones.isOverUi(c, p);
    }

    static Point tileCanvas(Client c, WorldPoint wp) {
        if (c == null || wp == null) {
            return null;
        }
        LocalPoint lp = LocalPoint.fromWorld(c, wp);
        if (lp == null) {
            return null;
        }
        if (Perspective.getCanvasTilePoly(c, lp) == null) {
            return null;
        }
        Point p = Perspective.localToCanvas(c, lp, wp.getPlane());
        if (!validCanvas(c, p)) {
            return null;
        }
        return p;
    }
}
