package com.lonebot.example.imps2;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.PlaneChangeHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Zelfde walker als steden-cirkel: elke tick {@link Movement#walkTo}.
 */
public final class Imps2Walk {

    private static String lastLog = "";
    private static long lastLogMs;

    private Imps2Walk() {
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

        // walkTo is al aangeroepen; delay is alleen de volgende tick (niet “wacht tot stil”)
        return Result.wait(ThreadLocalRandom.current().nextInt(280, 450),
                label + " d=" + d + " rem=" + rem + "/" + pathLen);
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
        BotRuntime.logConsole("[Imps2/walk] " + line);
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
