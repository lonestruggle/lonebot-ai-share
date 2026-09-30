package net.storm.api.domain;

import net.runelite.api.coords.WorldPoint;

/**
 * Marker for entities that have a world tile location.
 */
public interface Locatable {
    WorldPoint getWorldLocation();
}
