package net.storm.sdk.items;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.BankHelper;

/**
 * Storm {@code BankLocation} — F2P + common members banks used by LoneBot.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/items/package-summary.html">Storm items</a>
 */
public enum BankLocation {
    LUMBRIDGE_CASTLE(new WorldPoint(3208, 3220, 2)),
    VARROCK_EAST(BankHelper.VARROCK_EAST_BANK),
    VARROCK_WEST(new WorldPoint(3185, 3436, 0)),
    GRAND_EXCHANGE(BankHelper.GE_BANK),
    DRAYNOR(BankHelper.DRAYNOR_BANK_AREA_CENTER),
    EDGEVILLE(new WorldPoint(3094, 3491, 0)),
    FALADOR_EAST(new WorldPoint(3013, 3355, 0)),
    FALADOR_WEST(new WorldPoint(2946, 3368, 0)),
    AL_KHARID(new WorldPoint(3269, 3167, 0));

    private final WorldPoint tile;

    BankLocation(WorldPoint tile) {
        this.tile = tile;
    }

    public WorldPoint getWorldPoint() {
        return tile;
    }

    public WorldArea getArea() {
        return new WorldArea(tile, 5, 5);
    }

    public int distanceTo(WorldPoint from) {
        if (from == null || tile == null) {
            return Integer.MAX_VALUE;
        }
        return from.distanceTo(tile);
    }

    public static BankLocation nearest() {
        Players.LocalSnap me = Players.snapshotLocal();
        return nearest(me.present ? me.worldLocation : null);
    }

    public static BankLocation nearest(WorldPoint from) {
        if (from == null) {
            return VARROCK_EAST;
        }
        BankLocation best = VARROCK_EAST;
        int bestD = Integer.MAX_VALUE;
        for (BankLocation loc : values()) {
            int d = loc.distanceTo(from);
            if (d < bestD) {
                bestD = d;
                best = loc;
            }
        }
        return best;
    }
}
