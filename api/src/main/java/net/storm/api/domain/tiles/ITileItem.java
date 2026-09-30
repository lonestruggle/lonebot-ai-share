package net.storm.api.domain.tiles;

import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.Identifiable;
import net.storm.api.domain.Interactable;
import net.storm.api.domain.Locatable;
import net.storm.api.domain.Nameable;

public interface ITileItem extends Interactable, Identifiable, Nameable, Locatable {
    @Override
    int getId();

    @Override
    String getName();

    int getQuantity();

    @Override
    WorldPoint getWorldLocation();

    /** True if Take / Take-all (or similar) is available. */
    boolean canPick();

    /** Pickup via Take / Take-all. */
    boolean pickup();

    /** Canvas click point; null if off-screen. */
    Point getCanvasPoint();
}
