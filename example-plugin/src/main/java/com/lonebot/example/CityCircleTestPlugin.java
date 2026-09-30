package com.lonebot.example;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Game;
import net.storm.sdk.movement.MovementHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * F2P banken-cirkel: Lumb boven → Al Kharid → Varrock E/W → GE → Edge → Falador E/W → Draynor → Lumb.
 * Start bij de dichtstbijzijnde bank. Zelfde walker als map Walk-here.
 */
@PluginDescriptor(
        name = "LoneBot F2P banken-cirkel",
        description = "Loop alle F2P-banken (Lumb plane 2 + Pay-toll Al Kharid)"
)
public class CityCircleTestPlugin extends LoopedPlugin {

    private static final Logger log = LoggerFactory.getLogger(CityCircleTestPlugin.class);

    public static final class CityStop {
        public final String name;
        public final WorldPoint tile;

        public CityStop(String name, WorldPoint tile) {
            this.name = name;
            this.tile = tile;
        }
    }

    /** Clockwise F2P bank ring (geen Cooking Guild / Shantay / Corsair / Emir). */
    public static final List<CityStop> CITIES = Arrays.asList(
            new CityStop("Lumb bank", new WorldPoint(3208, 3220, 2)),
            new CityStop("Al Kharid", new WorldPoint(3269, 3167, 0)),
            new CityStop("Varrock E", new WorldPoint(3253, 3422, 0)),
            new CityStop("Varrock W", new WorldPoint(3189, 3436, 0)),
            new CityStop("GE", new WorldPoint(3164, 3486, 0)),
            new CityStop("Edgeville", new WorldPoint(3096, 3492, 0)),
            new CityStop("Falador E", new WorldPoint(3013, 3355, 0)),
            new CityStop("Falador W", new WorldPoint(2946, 3368, 0)),
            new CityStop("Draynor", new WorldPoint(3092, 3243, 0))
    );

    private static final int ARRIVE_DIST = 6;

    private int targetIndex = -1;
    private int lapsCompleted;
    private boolean initialized;
    private long startedAtMs;

    @Override
    public int loop() {
        if (!BotRuntime.cityCircleTestEnabled) {
            BotRuntime.cityCircleStatus = "uit";
            BotRuntime.debugPath = null;
            BotRuntime.debugTarget = null;
            initialized = false;
            targetIndex = -1;
            return 0;
        }
        if (!BotRuntime.botEnabled) {
            BotRuntime.cityCircleStatus = "bot uit";
            return 800;
        }

        if (!Game.isLoggedIn()) {
            BotRuntime.cityCircleStatus = "niet ingelogd";
            return 800;
        }

        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (pos == null) {
            BotRuntime.cityCircleStatus = "geen speler";
            return 500;
        }

        if (!initialized) {
            startedAtMs = System.currentTimeMillis();
            lapsCompleted = 0;
            targetIndex = nearestCityIndex(pos);
            initialized = true;
            CityStop start = CITIES.get(targetIndex);
            log.info("[CityCircle] start nearest={} @{} → F2P bank ring", start.name, start.tile);
            BotRuntime.cityCircleStatus = "start → " + start.name;
            return ThreadLocalRandom.current().nextInt(180, 280);
        }

        CityStop target = CITIES.get(targetIndex);
        BotRuntime.debugTarget = target.tile;
        int dist = xyDist(pos, target.tile);

        if (arrived(pos, target.tile)) {
            int prev = targetIndex;
            targetIndex = (targetIndex + 1) % CITIES.size();
            if (targetIndex == 0) {
                lapsCompleted++;
            }
            CityStop next = CITIES.get(targetIndex);
            log.info("[CityCircle] arrived {} → next {} (lap={})",
                    CITIES.get(prev).name, next.name, lapsCompleted);
            BotRuntime.cityCircleStatus = "✓ " + CITIES.get(prev).name
                    + " → " + next.name + " (lap " + lapsCompleted + ")";
            BotRuntime.debugSummary = BotRuntime.cityCircleStatus;
            return ThreadLocalRandom.current().nextInt(180, 280);
        }

        boolean walked = MovementHelper.walkTo(target.tile);
        TilePath path = MovementHelper.getActivePath();
        BotRuntime.debugPath = path;
        int rem = path != null && !path.isEmpty() && pos != null ? path.getRemainingPath(pos).size() : 0;
        int pathLen = path != null ? path.size() : 0;
        long sec = (System.currentTimeMillis() - startedAtMs) / 1000L;

        BotRuntime.cityCircleStatus = "→ " + target.name
                + " d=" + dist
                + " p" + pos.getPlane() + "→" + target.tile.getPlane()
                + " rem=" + rem + "/" + pathLen
                + " lap=" + lapsCompleted
                + " t=" + sec + "s";
        BotRuntime.debugSummary = BotRuntime.cityCircleStatus;

        if (!walked && me != null && !me.moving) {
            log.warn("[CityCircle] walk false → {} d={}", target.name, dist);
        }

        return ThreadLocalRandom.current().nextInt(200, 260);
    }

    private static boolean arrived(WorldPoint from, WorldPoint dest) {
        return from != null && dest != null
                && from.getPlane() == dest.getPlane()
                && from.distanceTo(dest) <= ARRIVE_DIST;
    }

    private static int xyDist(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(from.getX() - dest.getX()), Math.abs(from.getY() - dest.getY()));
    }

    private static int nearestCityIndex(WorldPoint from) {
        int best = 0;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < CITIES.size(); i++) {
            int d = xyDist(from, CITIES.get(i).tile);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
