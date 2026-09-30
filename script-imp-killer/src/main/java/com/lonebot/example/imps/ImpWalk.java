package com.lonebot.example.imps;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.PlaneChangeHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Zelfde walker als Imps2 / steden-cirkel: elke tick {@link Movement#walkTo}.
 * Hops 6–12 tegels via API-walker; doorlinken vóór de gele vlag — geen mega-minimap-hop.
 */
public final class ImpWalk {

    private static String lastLog = "";
    private static long lastLogMs;

    private ImpWalk() {
    }

    /**
     * @return delay ms; {@code arrived} true als binnen {@code arriveTiles}
     */
    public static Result toward(WorldPoint pos, WorldPoint dest, int arriveTiles, String label) {
        if (dest == null) {
            return Result.wait(600, "geen doel");
        }
        if (pos != null && PlaneChangeHelper.needsPlaneChange(dest)) {
            String hint = PlaneChangeHelper.statusHint(dest);
            logWalk(label, "climb " + hint);
            PlaneChangeHelper.progressTowardPlane(dest);
            return Result.wait(ThreadLocalRandom.current().nextInt(500, 900),
                    "climb… " + hint + " → " + label);
        }
        int d = dist(pos, dest);
        if (d >= 0 && d <= arriveTiles) {
            logWalk(label, "bij doel d=" + d);
            return Result.arrived(d, label);
        }
        if (MovementHelper.isBoatRequiredGap(pos, dest)) {
            logWalk(label, "boot-nodig d=" + d + " → " + dest.getX() + "," + dest.getY());
            return Result.wait(ThreadLocalRandom.current().nextInt(280, 450),
                    label + " boot-nodig d=" + d);
        }
        try {
            BotRuntime.debugTarget = dest;
            boolean walked = Movement.walkTo(dest);
            TilePath path = MovementHelper.getActivePath();
            int rem = path != null && !path.isEmpty() && pos != null ? path.getRemainingPath(pos).size() : 0;
            int pathLen = path != null ? path.size() : 0;
            Players.LocalSnap me = Players.snapshotLocal();
            boolean moving = Movement.isMoving() || (me != null && me.moving);
            logWalk(label, (walked ? "walkTo" : "walk-miss")
                    + " d=" + d + " rem=" + rem + "/" + pathLen
                    + (moving ? " moving" : " stil")
                    + (path != null && path.isIncomplete() ? " prefix" : "")
                    + " → " + dest.getX() + "," + dest.getY());

            return Result.wait(ThreadLocalRandom.current().nextInt(280, 450),
                    label + " d=" + d + " rem=" + rem + "/" + pathLen);
        } catch (RuntimeException e) {
            logWalk(label, "walk-err " + e.getClass().getSimpleName());
            return Result.wait(ThreadLocalRandom.current().nextInt(400, 700),
                    label + " walk-err");
        }
    }

    private static int dist(WorldPoint pos, WorldPoint dest) {
        if (pos == null || dest == null || pos.getPlane() != dest.getPlane()) {
            return -1;
        }
        return pos.distanceTo(dest);
    }

    private static void logWalk(String label, String msg) {
        long now = System.currentTimeMillis();
        String line = label + " " + msg;
        if (line.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = line;
        lastLogMs = now;
        BotRuntime.logConsole("[Imp/walk] " + line);
    }

    public static final class Result {
        public final boolean arrived;
        public final int delayMs;
        public final String status;

        private Result(boolean arrived, int delayMs, String status) {
            this.arrived = arrived;
            this.delayMs = delayMs;
            this.status = status != null ? status : "-";
        }

        static Result arrived(int d, String label) {
            return new Result(true, ThreadLocalRandom.current().nextInt(400, 700),
                    "✓ " + label + " d=" + d);
        }

        static Result wait(int delayMs, String status) {
            return new Result(false, delayMs, status);
        }
    }
}
