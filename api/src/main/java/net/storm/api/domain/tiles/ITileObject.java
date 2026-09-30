package net.storm.api.domain.tiles;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.Identifiable;
import net.storm.api.domain.Interactable;
import net.storm.api.domain.Locatable;
import net.storm.api.domain.Nameable;

public interface ITileObject extends Interactable, Identifiable, Nameable, Locatable {
    @Override
    int getId();

    @Override
    String getName();

    @Override
    WorldPoint getWorldLocation();

    @Override
    boolean hasAction(String action);

    @Override
    boolean interact(String action);
}
