package com.lonebot.example.giants;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.interact.TileObjectInteractHelper;
import net.storm.sdk.utils.AntiBan;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Two F2P entries: Varrock shed + Brass key (faster), Edgeville trapdoor (no key).
 */
public final class GiantsDungeon {

    private GiantsDungeon() {
    }

    public static int enterShed(WorldPoint pos) {
        if (pos == null) {
            return 400;
        }
        if (GiantsLocations.isUnderground(pos)) {
            return 0;
        }
        ITileObject door = TileObjects.getNearest(GiantsLocations.VARROCK_SHED, GiantsLocations.SHED_DOOR_ID);
        if (door == null) {
            door = TileObjects.getNearest(o -> o != null
                    && (o.getId() == GiantsLocations.SHED_DOOR_ID
                    || (o.getName() != null && o.getName().equalsIgnoreCase("Door")
                    && o.getWorldLocation() != null
                    && o.getWorldLocation().distanceTo(GiantsLocations.VARROCK_SHED) <= 4)));
        }
        if (pos.distanceTo(GiantsLocations.VARROCK_SHED) > 3 && door == null) {
            GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.VARROCK_SHED, 2, "shed");
            return w.delayMs;
        }
        if (door != null && firstAction(door, "Open", "Unlock") != null) {
            String a = firstAction(door, "Open", "Unlock");
            if (TileObjectInteractHelper.interact(door, a)) {
                AntiBan.get().markBotActivity();
                GiantsLog.action("dungeon", "shed " + a);
                return ThreadLocalRandom.current().nextInt(700, 1100);
            }
        }
        ITileObject ladder = TileObjects.getNearest(GiantsLocations.VARROCK_SHED,
                o -> o != null && o.getName() != null && o.getName().toLowerCase().contains("ladder")
                        && o.getWorldLocation() != null
                        && o.getWorldLocation().distanceTo(GiantsLocations.VARROCK_SHED) <= 6);
        if (ladder != null) {
            String a = firstAction(ladder, "Climb-down", "Climb");
            if (a != null && TileObjectInteractHelper.interact(ladder, a)) {
                AntiBan.get().markBotActivity();
                GiantsLog.action("dungeon", "shed ladder " + a);
                return ThreadLocalRandom.current().nextInt(800, 1200);
            }
        }
        GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.VARROCK_SHED, 1, "shed-in");
        return w.delayMs;
    }

    public static int enterTrapdoor(WorldPoint pos) {
        if (pos == null) {
            return 400;
        }
        if (GiantsLocations.isUnderground(pos)) {
            return 0;
        }
        if (pos.distanceTo(GiantsLocations.EDGE_TRAPDOOR) > 4
                && pos.distanceTo(GiantsLocations.EDGE_PRE_TRAPDOOR) > 2) {
            WorldPoint via = pos.distanceTo(GiantsLocations.EDGE_PRE_TRAPDOOR)
                    <= pos.distanceTo(GiantsLocations.EDGE_TRAPDOOR)
                    ? GiantsLocations.EDGE_PRE_TRAPDOOR
                    : GiantsLocations.EDGE_TRAPDOOR;
            GiantsWalk.Result w = GiantsWalk.toward(pos, via, 1, "trapdoor");
            return w.delayMs;
        }
        ITileObject trap = TileObjects.getNearest(GiantsLocations.EDGE_TRAPDOOR, GiantsLocations.EDGE_TRAPDOOR_ID);
        if (trap == null) {
            trap = TileObjects.getNearest(o -> o != null && o.getId() == GiantsLocations.EDGE_TRAPDOOR_ID);
        }
        if (trap == null && pos.distanceTo(GiantsLocations.EDGE_PRE_TRAPDOOR) > 1) {
            GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.EDGE_PRE_TRAPDOOR, 1, "pre-trapdoor");
            return w.delayMs;
        }
        if (trap != null) {
            String a = firstAction(trap, "Climb-down", "Open", "Climb");
            if (a != null && TileObjectInteractHelper.interact(trap, a)) {
                AntiBan.get().markBotActivity();
                GiantsLog.action("dungeon", "trapdoor " + a);
                return ThreadLocalRandom.current().nextInt(800, 1300);
            }
        }
        GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.EDGE_TRAPDOOR, 1, "trapdoor-tile");
        return w.delayMs;
    }

    public static int exitDungeon(WorldPoint pos, boolean hasKey) {
        if (pos == null || !GiantsLocations.isUnderground(pos)) {
            return 0;
        }
        if (hasKey) {
            ITileObject hop = TileObjects.getNearest(GiantsLocations.HOP_LADDER, GiantsLocations.HOP_LADDER_ID);
            if (hop == null && pos.distanceTo(GiantsLocations.HOP_LADDER) > 2) {
                GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.HOP_LADDER, 1, "exit-shed-ladder");
                return w.delayMs;
            }
            if (hop != null) {
                String a = firstAction(hop, "Climb-up", "Climb");
                if (a != null && TileObjectInteractHelper.interact(hop, a)) {
                    AntiBan.get().markBotActivity();
                    GiantsLog.action("dungeon", "exit hop-ladder " + a);
                    return ThreadLocalRandom.current().nextInt(800, 1200);
                }
            }
        }
        if (pos.distanceTo(GiantsLocations.EDGE_LADDER_BELOW) > 2) {
            GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.EDGE_LADDER_BELOW, 1, "exit-edge-ladder");
            return w.delayMs;
        }
        ITileObject ladder = TileObjects.getNearest(GiantsLocations.EDGE_LADDER_BELOW,
                o -> o != null && o.getName() != null && o.getName().toLowerCase().contains("ladder"));
        if (ladder != null) {
            String a = firstAction(ladder, "Climb-up", "Climb");
            if (a != null && TileObjectInteractHelper.interact(ladder, a)) {
                AntiBan.get().markBotActivity();
                GiantsLog.action("dungeon", "exit edge ladder " + a);
                return ThreadLocalRandom.current().nextInt(800, 1200);
            }
        }
        return ThreadLocalRandom.current().nextInt(400, 700);
    }

    public static int walkHunt(WorldPoint pos, int radius) {
        if (GiantsLocations.isInHuntZone(pos, radius)) {
            return 0;
        }
        GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.HUNT_CENTER, 4, "hunt");
        return w.delayMs;
    }

    private static String firstAction(ITileObject obj, String... names) {
        if (obj == null) {
            return null;
        }
        for (String a : names) {
            try {
                if (obj.hasAction(a)) {
                    return a;
                }
            } catch (Throwable ignored) {
            }
        }
        return names.length > 0 ? names[0] : null;
    }
}
