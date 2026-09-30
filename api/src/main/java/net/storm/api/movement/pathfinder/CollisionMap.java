package net.storm.api.movement.pathfinder;

import net.runelite.api.coords.WorldPoint;

/**
 * Collision queries for pathfinding (Storm / unethicalite compatible).
 */
public interface CollisionMap {

    boolean n(int x, int y, int z);

    boolean e(int x, int y, int z);

    default boolean s(int x, int y, int z) {
        return n(x, y - 1, z);
    }

    default boolean w(int x, int y, int z) {
        return e(x - 1, y, z);
    }

    default boolean ne(int x, int y, int z) {
        return n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z);
    }

    default boolean nw(int x, int y, int z) {
        return n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z);
    }

    default boolean se(int x, int y, int z) {
        return s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z);
    }

    default boolean sw(int x, int y, int z) {
        return s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z);
    }

    default boolean fullBlock(int x, int y, int z) {
        return !n(x, y, z) && !s(x, y, z) && !w(x, y, z) && !e(x, y, z);
    }

    default boolean n(WorldPoint p) {
        return p != null && n(p.getX(), p.getY(), p.getPlane());
    }

    default boolean e(WorldPoint p) {
        return p != null && e(p.getX(), p.getY(), p.getPlane());
    }

    default boolean s(WorldPoint p) {
        return p != null && s(p.getX(), p.getY(), p.getPlane());
    }

    default boolean w(WorldPoint p) {
        return p != null && w(p.getX(), p.getY(), p.getPlane());
    }

    default boolean ne(WorldPoint p) {
        return p != null && ne(p.getX(), p.getY(), p.getPlane());
    }

    default boolean nw(WorldPoint p) {
        return p != null && nw(p.getX(), p.getY(), p.getPlane());
    }

    default boolean se(WorldPoint p) {
        return p != null && se(p.getX(), p.getY(), p.getPlane());
    }

    default boolean sw(WorldPoint p) {
        return p != null && sw(p.getX(), p.getY(), p.getPlane());
    }

    default boolean fullBlock(WorldPoint p) {
        return p != null && fullBlock(p.getX(), p.getY(), p.getPlane());
    }
}
