package net.storm.sdk.travel;

import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.items.Bank;
import net.storm.sdk.movement.MovementHelper;

/**
 * Travel shortcuts toward the Varrock Grand Exchange hub.
 */
public final class VarrockGeTravelHelper {

    public static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3164, 3487, 0);

    private VarrockGeTravelHelper() {
    }

    /** Bank mag open blijven; walk sluit de UI. Canvas-klik vermeden via {@link Bank#leaveOpenForWalk()}. */
    public static boolean walkTowardGe() {
        if (Bank.isOpen()) {
            Bank.leaveOpenForWalk();
        }
        return MovementHelper.walkTo(GRAND_EXCHANGE);
    }
}
