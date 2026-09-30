package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.movement.pathfinder.F2pSpellTeleports;
import net.storm.sdk.movement.pathfinder.GlobalPathfinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lange reizen: volledige A* op achtergrond-thread. Eerste hop via scene-prefix;
 * achtergrond vult het hele pad (geen 14s-wacht vóór de eerste klik).
 */
final class TravelPathSearch {

    /** Achtergrond: F2P-mainland mag afmaken. */
    static final long BACKGROUND_MS = 14_000L;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(daemonFactory());

    private static volatile Job job;

    private TravelPathSearch() {
    }

    static void cancel() {
        Job j = job;
        job = null;
        if (j != null && j.future != null) {
            j.future.cancel(true);
        }
    }

    static boolean isBusy() {
        Job j = job;
        return j != null && j.future != null && !j.future.isDone();
    }

    /**
     * Start (of houd) achtergrond-A*. Niet onderbreken zolang dezelfde dest loopt.
     */
    static void ensure(WorldPoint from, WorldPoint dest, Collection<Transport> transports) {
        if (from == null || dest == null) {
            return;
        }
        Job j = job;
        if (j != null && sameDest(j.dest, dest) && j.future != null && !j.future.isCancelled()) {
            return;
        }
        GlobalPathfinder.map();
        List<Transport> copy = copyTransports(transports);
        WorldPoint start = from;
        WorldPoint target = dest;
        Future<TilePath> fut = EXEC.submit(() -> {
            F2pSpellTeleports.ensureRegistered();
            List<WorldPoint> starts = F2pSpellTeleports.startsFor(start, target);
            TilePath path = GlobalPathfinder.findPath(
                    starts, target.toWorldArea(), GlobalPathfinder.map(),
                    MovementHelper::travelAvoidTiles, copy, BACKGROUND_MS);
            F2pSpellTeleports.attachIfUsed(path, start);
            return path;
        });
        job = new Job(start, target, fut);
        BotRuntime.logConsole("[Walk/path] achtergrond A* " + start.getX() + "," + start.getY()
                + " → " + dest.getX() + "," + dest.getY());
    }

    static TilePath poll(WorldPoint dest) {
        Job j = job;
        if (j == null || j.future == null || !j.future.isDone() || j.future.isCancelled()) {
            return null;
        }
        if (!sameDest(j.dest, dest)) {
            return null;
        }
        try {
            TilePath path = j.future.get();
            job = null;
            return path;
        } catch (Exception e) {
            job = null;
            return null;
        }
    }

    private static boolean sameDest(WorldPoint a, WorldPoint b) {
        return a != null && b != null
                && a.getPlane() == b.getPlane()
                && a.distanceTo(b) <= 8;
    }

    private static List<Transport> copyTransports(Collection<Transport> transports) {
        if (transports == null || transports.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(transports);
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "lonebot-path-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private static final class Job {
        final WorldPoint from;
        final WorldPoint dest;
        final Future<TilePath> future;

        Job(WorldPoint from, WorldPoint dest, Future<TilePath> future) {
            this.from = from;
            this.dest = dest;
            this.future = future;
        }
    }
}
