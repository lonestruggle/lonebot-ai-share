package net.storm.sdk.bot;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.items.Inventory;

import java.util.Locale;

/**
 * Detecteert beginner-scroll / clue-container / openbaar bird nest en triggert
 * {@link BotRuntime#interruptForClue()} als de actieve skill “Clues doen” aan heeft.
 */
public final class ClueSkillHandoff {

    private static final int ITEM_CLUE_SCROLL_BEGINNER = 23182;
    private static final int ITEM_CLUE_GEODE_BEGINNER = 23442;
    private static final int ITEM_CLUE_BOTTLE_BEGINNER = 23129;
    private static final int GROUND_RADIUS = 18;
    private static final long LOG_THROTTLE_MS = 1_500L;

    private static final String[] GEODE_NAMES = {
            "Clue geode (beginner)", "Clue geode (easy)", "Clue geode (medium)",
            "Clue geode (hard)", "Clue geode (elite)"
    };
    private static final String[] BOTTLE_NAMES = {
            "Clue bottle (beginner)", "Clue bottle (easy)", "Clue bottle (medium)",
            "Clue bottle (hard)", "Clue bottle (elite)"
    };
    private static final String[] NEST_NAMES = {
            "Clue nest (beginner)", "Clue nest (easy)", "Clue nest (medium)",
            "Clue nest (hard)", "Clue nest (elite)",
            "Bird nest"
    };

    private static long lastLogMs;

    private ClueSkillHandoff() {
    }

    /** True als inv of grond een clue-trigger heeft (scroll/container/nest). */
    public static boolean hasTrigger() {
        if (hasInvTrigger()) {
            return true;
        }
        return hasGroundTrigger();
    }

    public static boolean hasInvTrigger() {
        if (Inventory.contains(ITEM_CLUE_SCROLL_BEGINNER)
                || Inventory.contains(ITEM_CLUE_GEODE_BEGINNER)
                || Inventory.contains(ITEM_CLUE_BOTTLE_BEGINNER)) {
            return true;
        }
        if (Inventory.contains(GEODE_NAMES) || Inventory.contains(BOTTLE_NAMES) || Inventory.contains(NEST_NAMES)) {
            return true;
        }
        IInventoryItem any = Inventory.getFirst(ClueSkillHandoff::isClueTriggerItem);
        return any != null;
    }


    public static boolean hasGroundTrigger() {
        WorldPoint loc = null;
        try {
            var local = Players.getLocal();
            if (local != null) {
                loc = local.getWorldLocation();
            }
        } catch (Throwable ignored) {
        }
        if (loc == null) {
            return TileItems.getNearest(ClueSkillHandoff::isClueTriggerTile) != null;
        }
        final WorldPoint center = loc;
        return TileItems.getNearest(t -> isClueTriggerTile(t) && near(center, t, GROUND_RADIUS)) != null;
    }


    /**
     * Als toggle aan + trigger aanwezig → handoff naar Clue.
     * @return true als skill naar CLUE is gezet (caller moet early-returnen)
     */
    public static boolean tryHandoffFromActiveSkill() {
        BotRuntime.ActiveSkill cur = BotRuntime.activeSkill;
        if (cur == BotRuntime.ActiveSkill.NONE
                || cur == BotRuntime.ActiveSkill.CLUE
                || cur == BotRuntime.ActiveSkill.QUEST) {
            return false;
        }
        if (!BotRuntime.isClueSolverEnabledFor(cur)) {
            return false;
        }
        if (!hasTrigger()) {
            return false;
        }
        boolean ok = BotRuntime.interruptForClue();
        if (ok) {
            throttleLog("[Clue/Handoff] trigger → interrupt (" + cur + ")");
        }
        return ok;
    }

    /** Giants/Imp: niet droppen als clue-solver aan is. */
    public static boolean shouldKeepClueItem(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!BotRuntime.isClueSolverEnabledFor(BotRuntime.activeSkill)
                && BotRuntime.activeSkill != BotRuntime.ActiveSkill.CLUE) {
            return false;
        }
        return isClueTriggerName(name);
    }

    public static boolean isClueTriggerName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String low = name.toLowerCase(Locale.ROOT);
        if (low.contains("clue scroll") || low.contains("clue geode")
                || low.contains("clue bottle") || low.contains("clue nest")
                || low.contains("reward casket")) {
            return true;
        }
        return low.contains("bird nest") || low.contains("bird's nest") || low.contains("birds nest");
    }

    private static boolean isClueTriggerItem(IInventoryItem i) {
        if (i == null) {
            return false;
        }
        int id = i.getId();
        if (id == ITEM_CLUE_SCROLL_BEGINNER || id == ITEM_CLUE_GEODE_BEGINNER || id == ITEM_CLUE_BOTTLE_BEGINNER) {
            return true;
        }
        String n = i.getName();
        if (!isClueTriggerName(n)) {
            return false;
        }
        // Bird nest: alleen als Search/Open beschikbaar (anders nest-loot zonder clue)
        if (n != null && n.toLowerCase(Locale.ROOT).contains("bird nest")) {
            try {
                return i.hasAction("Search") || i.hasAction("Open");
            } catch (Throwable t) {
                return true;
            }
        }
        return true;
    }

    private static boolean isClueTriggerTile(ITileItem t) {
        if (t == null) {
            return false;
        }
        try {
            int id = t.getId();
            if (id == ITEM_CLUE_SCROLL_BEGINNER || id == ITEM_CLUE_GEODE_BEGINNER || id == ITEM_CLUE_BOTTLE_BEGINNER) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return isClueTriggerName(t.getName());
    }

    private static boolean near(WorldPoint center, ITileItem t, int radius) {
        try {
            WorldPoint p = t.getWorldLocation();
            if (p == null || center == null) {
                return true;
            }
            return p.distanceTo(center) <= radius;
        } catch (Throwable e) {
            return true;
        }
    }

    private static void throttleLog(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastLogMs < LOG_THROTTLE_MS) {
            return;
        }
        lastLogMs = now;
        BotRuntime.logConsole(msg);
    }
}
