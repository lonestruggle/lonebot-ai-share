package net.storm.sdk.interact;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Statische tile-objects (bomen, rotsen, fishing spots): <b>muis-clickbox eerst</b>,
 * daarna menu-invoke + camera-retry.
 * <p>
 * Invoke-only gaf vals-OK zonder animatie (WC ping-pong) — mouse-first lost dat op.
 */
public final class TileObjectInteractHelper {

    private static final Logger log = LoggerFactory.getLogger(TileObjectInteractHelper.class);

    private static volatile String lastDetail = "";

    private TileObjectInteractHelper() {
    }

    public static String getLastDetail() {
        return lastDetail != null ? lastDetail : "";
    }

    /**
     * @return true als canvas-klik of menu-invoke is uitgegeven
     */
    public static boolean interact(ITileObject obj, String action) {
        if (obj == null || action == null || action.isBlank()) {
            lastDetail = "obj/action null";
            return false;
        }
        String name = obj.getName() != null ? obj.getName() : "?";
        WorldPoint wp = obj.getWorldLocation();

        // Muis via entity.interact (RlTileObject = mouse-first)
        if (obj.interact(action)) {
            lastDetail = "ok " + action + " " + name
                    + (wp != null ? " @" + wp.getX() + "," + wp.getY() : "");
            log.info("[TileObj] {}", lastDetail);
            return true;
        }

        if (wp != null && AimInteractHelper.turnToward(wp)) {
            sleep(160 + ThreadLocalRandom.current().nextInt(140));
            if (obj.interact(action)) {
                lastDetail = "ok after turn " + action + " " + name;
                log.info("[TileObj] {}", lastDetail);
                return true;
            }
        }

        lastDetail = "fail " + action + " " + name
                + (wp != null ? " @" + wp.getX() + "," + wp.getY() : "");
        log.info("[TileObj] {}", lastDetail);
        return false;
    }

    /** Woodcutting. */
    public static boolean chop(ITileObject tree) {
        return interact(tree, "Chop down");
    }

    /** Mining. */
    public static boolean mine(ITileObject rock) {
        return interact(rock, "Mine");
    }

    /** Fishing spot — action bv. Net / Lure / Cage / Harpoon. */
    public static boolean fish(ITileObject spot, String action) {
        return interact(spot, action != null ? action : "Net");
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
