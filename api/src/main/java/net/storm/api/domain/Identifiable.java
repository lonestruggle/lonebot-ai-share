package net.storm.api.domain;

/**
 * Marker for entities with a numeric game id (NPC id, item id, object id, …).
 */
public interface Identifiable {
    int getId();
}
