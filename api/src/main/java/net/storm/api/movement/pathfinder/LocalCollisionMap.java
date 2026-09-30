package net.storm.api.movement.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Live scene collision (loaded 104×104). Doors can optionally be treated as passable.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/movement/pathfinder/LocalCollisionMap.html">Storm LocalCollisionMap</a>
 */
public final class LocalCollisionMap implements CollisionMap {

    private static final int OBJECT_BLOCK_MASK = objectBlockMask();

    private final boolean blockDoors;

    public LocalCollisionMap() {
        this(true);
    }

    /**
     * @param blockDoors false = treat door/object-block flags as walkable (path executor must open doors)
     */
    public LocalCollisionMap(boolean blockDoors) {
        this.blockDoors = blockDoors;
    }

    @Override
    public boolean n(int x, int y, int z) {
        return canStep(x, y, z, 0, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH);
    }

    @Override
    public boolean e(int x, int y, int z) {
        return canStep(x, y, z, 1, 0, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
    }

    private boolean canStep(int x, int y, int z, int dx, int dy, int exitFlag) {
        Boolean ok = onClient(() -> {
            Client c = client();
            if (c == null || z != c.getPlane()) {
                return false;
            }
            CollisionData[] maps = c.getCollisionMaps();
            if (maps == null || z < 0 || z >= maps.length || maps[z] == null) {
                return false;
            }
            int[][] flags = maps[z].getFlags();
            LocalPoint from = LocalPoint.fromWorld(c, new WorldPoint(x, y, z));
            LocalPoint to = LocalPoint.fromWorld(c, new WorldPoint(x + dx, y + dy, z));
            if (from == null || to == null) {
                return false;
            }
            int sx = from.getSceneX();
            int sy = from.getSceneY();
            int nx = to.getSceneX();
            int ny = to.getSceneY();
            if (!inScene(sx, sy, flags) || !inScene(nx, ny, flags)) {
                return false;
            }
            int here = mask(flags[sx][sy]);
            int dest = mask(flags[nx][ny]);
            if ((here & exitFlag) != 0) {
                return false;
            }
            return (dest & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
        });
        return Boolean.TRUE.equals(ok);
    }

    private int mask(int flags) {
        if (blockDoors) {
            return flags;
        }
        return flags & ~OBJECT_BLOCK_MASK;
    }

    private static boolean inScene(int sx, int sy, int[][] flags) {
        return sx >= 0 && sy >= 0 && sx < flags.length && sy < flags[sx].length;
    }

    private static int objectBlockMask() {
        int mask = 0;
        mask |= flag("BLOCK_MOVEMENT_OBJECT");
        mask |= flag("BLOCK_MOVEMENT_OBJECT_NORTH");
        mask |= flag("BLOCK_MOVEMENT_OBJECT_EAST");
        mask |= flag("BLOCK_MOVEMENT_OBJECT_SOUTH");
        mask |= flag("BLOCK_MOVEMENT_OBJECT_WEST");
        return mask;
    }

    private static int flag(String name) {
        try {
            return CollisionDataFlag.class.getField(name).getInt(null);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Client client() {
        try {
            Class<?> st = Class.forName("net.storm.sdk.game.Static");
            Object c = st.getMethod("getClient").invoke(null);
            return c instanceof Client ? (Client) c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Boolean onClient(Supplier<Boolean> body) {
        try {
            Class<?> st = Class.forName("net.storm.sdk.game.Static");
            Method m = st.getMethod("callOnClientThread", Supplier.class, Object.class);
            Object v = m.invoke(null, body, Boolean.FALSE);
            return v instanceof Boolean ? (Boolean) v : Boolean.FALSE;
        } catch (Throwable t) {
            try {
                return body.get();
            } catch (Throwable ignored) {
                return Boolean.FALSE;
            }
        }
    }
}
