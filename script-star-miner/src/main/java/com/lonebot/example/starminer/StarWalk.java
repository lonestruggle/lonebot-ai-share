package com.lonebot.example.starminer;

import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.PlaneChangeHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Exact zelfde travel als ImpWalk / steden-cirkel: elke tick {@link Movement#walkTo}.
 * Geen eigen walker — hops 6–12 + doorlinken via de API ({@code TilePathWalker}).
 */
final class StarWalk {

    private static String lastLog = "";
    private static long lastLogMs;

    private StarWalk() {
    }

    /**
     * @return delay ms; {@code arrived} true als binnen {@code arriveTiles}
     */
    static Result toward(WorldPoint pos, WorldPoint dest, int arriveTiles, String label) {
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
            logWalk(label, "boot/eiland-gap d=" + d + " → " + dest.getX() + "," + dest.getY()
                    + " — geen pad (skip)");
            return Result.wait(ThreadLocalRandom.current().nextInt(200, 360),
                    label + " boot/eiland", true);
        }
        try {
            WorldPoint use = dest;
            try {
                WorldPoint active = MovementHelper.getActiveDestination();
                if (active != null && dest.getPlane() == active.getPlane()
                        && dest.distanceTo(active) <= 8) {
                    use = active;
                }
            } catch (Throwable ignored) {
            }
            BotRuntime.debugTarget = use;
            // Alleen walkTo. Doorlinken = walker (4 vóór vlag).
            boolean walked = Movement.walkTo(use);
            net.storm.api.movement.TilePath path = MovementHelper.getActivePath();
            int rem = path != null && !path.isEmpty() && pos != null ? path.getRemainingPath(pos).size() : 0;
            int pathLen = path != null ? path.size() : 0;
            Players.LocalSnap me = Players.snapshotLocal();
            boolean moving = Movement.isMoving() || (me != null && me.moving);
            logWalk(label, (walked ? "walkTo" : "walk-miss")
                    + " d=" + d + " rem=" + rem + "/" + pathLen
                    + (moving ? " moving" : " stil")
                    + (path != null && path.isIncomplete() ? " prefix" : "")
                    + " → " + dest.getX() + "," + dest.getY());
            try {
                net.storm.sdk.utils.AntiBan.get().markBotActivity();
            } catch (Throwable ignored) {
            }
            boolean failed = !walked && !moving && !MovementHelper.isWaitingForTravelPath();
            int wait = moving
                    ? ThreadLocalRandom.current().nextInt(200, 280)
                    : ThreadLocalRandom.current().nextInt(280, 400);
            return Result.wait(wait, label + " d=" + d, failed);
        } catch (RuntimeException e) {
            logWalk(label, "walk-err " + e.getClass().getSimpleName());
            return Result.wait(ThreadLocalRandom.current().nextInt(400, 700),
                    label + " walk-err", true);
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
        BotRuntime.logConsole("[Star/walk] " + line);
    }

    static final class Result {
        final boolean arrived;
        final int delayMs;
        final String status;
        /** walk-miss / exception — {@link StarMinerLoop#afterWalk} skip-failsafe. */
        final boolean failed;

        private Result(boolean arrived, int delayMs, String status, boolean failed) {
            this.arrived = arrived;
            this.delayMs = delayMs;
            this.status = status != null ? status : "-";
            this.failed = failed;
        }

        static Result arrived(int d, String label) {
            return new Result(true, ThreadLocalRandom.current().nextInt(400, 700),
                    "✓ " + label + " d=" + d, false);
        }

        static Result wait(int delayMs, String status) {
            return wait(delayMs, status, false);
        }

        static Result wait(int delayMs, String status, boolean failed) {
            return new Result(false, delayMs, status, failed);
        }
    }
}
