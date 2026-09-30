package net.storm.sdk.game;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.entities.Players;

/**
 * Storm {@code House} — player-owned house (POH) presence.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/game/package-summary.html">Storm game</a>
 */
public final class House {

    /** Typical POH instance region (varies; used as a heuristic). */
    private static final int POH_VARBIT = 8415;

    private House() {
    }

    public static boolean isInside() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                if (c.getVarbitValue(POH_VARBIT) > 0) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                return c.isInInstancedRegion();
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }

    public static WorldPoint getLocation() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me.present ? me.worldLocation : null;
    }
}
