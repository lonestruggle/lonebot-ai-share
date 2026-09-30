package net.storm.sdk.movement.pathfinder;

import net.storm.api.movement.pathfinder.model.Teleport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Custom teleport registry for the pathfinder.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/pathfinder/TeleportLoader.html">Storm TeleportLoader</a>
 */
public final class TeleportLoader {

    private static final CopyOnWriteArrayList<Teleport> CUSTOM = new CopyOnWriteArrayList<>();

    public TeleportLoader() {
    }

    /**
     * Mutable live list of custom teleports (Storm: may be modified directly).
     */
    public static List<Teleport> getCustomTeleports() {
        return CUSTOM;
    }

    public static void addCustomTeleport(Teleport teleport) {
        if (teleport != null && !CUSTOM.contains(teleport)) {
            CUSTOM.add(teleport);
        }
    }

    public static void removeCustomTeleport(Teleport teleport) {
        if (teleport != null) {
            CUSTOM.remove(teleport);
        }
    }
}
