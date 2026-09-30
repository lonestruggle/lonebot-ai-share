package net.storm.sdk.game;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.World;
import net.runelite.api.WorldType;
import net.storm.sdk.community.WorldHopper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storm {@code Worlds} — current world + hop.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/game/package-summary.html">Storm game</a>
 */
public final class Worlds {

    private static final Logger log = LoggerFactory.getLogger(Worlds.class);

    /** Blijft actief na hop (live getWorldList kan tijdelijk leeg zijn). */
    private static final Set<Integer> CACHE_F2P = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> CACHE_MEMBERS = ConcurrentHashMap.newKeySet();
    private static volatile long cacheFilledMs;
    /** Eén live-lijst delen — niet 60× getWorldList per Star-tick. */
    private static volatile World[] listSnapshot = new World[0];
    private static volatile long listSnapshotMs;
    private static volatile long lastListFetchMs;
    private static final long LIST_TTL_MS = 10_000L;
    private static final long EMPTY_RETRY_MS = 2_500L;

    private Worlds() {
    }

    public static int getCurrentWorld() {
        Integer w = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? c.getWorld() : -1;
        }, -1);
        return w != null ? w : -1;
    }

    public static boolean isMembers() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.getWorldType().contains(WorldType.MEMBERS);
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }

    /** Live of cache: members-world? */
    public static boolean isMembersWorld(int worldId) {
        refreshWorldTypeCacheFromLive();
        if (CACHE_MEMBERS.contains(worldId)) {
            return true;
        }
        if (CACHE_F2P.contains(worldId)) {
            return false;
        }
        if (CACHE_F2P.isEmpty() && CACHE_MEMBERS.isEmpty()) {
            return false;
        }
        World w = get(worldId);
        if (w == null) {
            return false;
        }
        try {
            Set<WorldType> types = w.getTypes();
            return types != null && types.contains(WorldType.MEMBERS);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * F2P-hop toegestaan: bekend als F2P in cache/live, géén members.
     * Cache blijft na hop — geen hopper opnieuw openen.
     */
    public static boolean isFreeToPlayWorld(int worldId) {
        refreshWorldTypeCacheFromLive();
        if (CACHE_MEMBERS.contains(worldId)) {
            return false;
        }
        if (CACHE_F2P.contains(worldId)) {
            return true;
        }
        if (CACHE_F2P.isEmpty() && CACHE_MEMBERS.isEmpty()) {
            return false;
        }
        World w = get(worldId);
        if (w == null) {
            return false;
        }
        try {
            Set<WorldType> types = w.getTypes();
            boolean members = types != null && types.contains(WorldType.MEMBERS);
            remember(worldId, members);
            return !members;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True als we al eens een world-list hebben gezien (blijft na hop). */
    public static boolean hasWorldTypeCache() {
        refreshWorldTypeCacheFromLive();
        return !CACHE_F2P.isEmpty() || !CACHE_MEMBERS.isEmpty();
    }

    public static int cachedF2pCount() {
        refreshWorldTypeCacheFromLive();
        return CACHE_F2P.size();
    }

    public static Set<Integer> cachedF2pWorlds() {
        refreshWorldTypeCacheFromLive();
        return Collections.unmodifiableSet(new HashSet<>(CACHE_F2P));
    }

    /** Live-lijst inlezen in cache. Cache vol / leeg: niet 60× getWorldList per tick. */
    public static void refreshWorldTypeCacheFromLive() {
        refreshWorldTypeCacheFromLive(false);
    }

    /** {@code force} na hopper-open: niet de lege-lijst throttle. */
    public static void refreshWorldTypeCacheFromLive(boolean force) {
        boolean have = !CACHE_F2P.isEmpty() || !CACHE_MEMBERS.isEmpty();
        long now = System.currentTimeMillis();
        if (!force && have && cacheFilledMs > 0L && now - cacheFilledMs < LIST_TTL_MS) {
            return;
        }
        World[] live = snapshotWorldList(force);
        if (live == null || live.length == 0) {
            return;
        }
        ingestWorldTypes(live);
    }

    private static boolean remember(int worldId, boolean members) {
        if (worldId <= 0) {
            return false;
        }
        if (members) {
            CACHE_F2P.remove(worldId);
            return CACHE_MEMBERS.add(worldId);
        }
        CACHE_MEMBERS.remove(worldId);
        return CACHE_F2P.add(worldId);
    }

    public static World[] getAll() {
        return getAll(false);
    }

    /** Hopper nét open: verse lijst, geen 2.5s-leeg-cache. */
    public static World[] getAll(boolean force) {
        World[] live = snapshotWorldList(force);
        if (live != null && live.length > 0) {
            ingestWorldTypes(live);
            return live;
        }
        return live != null ? live : new World[0];
    }

    private static World[] snapshotWorldList(boolean force) {
        long now = System.currentTimeMillis();
        World[] snap = listSnapshot;
        boolean fresh = snap != null && snap.length > 0 && listSnapshotMs > 0L
                && now - listSnapshotMs < LIST_TTL_MS;
        if (!force && fresh) {
            return snap;
        }
        if (!force && (snap == null || snap.length == 0) && lastListFetchMs > 0L
                && now - lastListFetchMs < EMPTY_RETRY_MS) {
            return snap != null ? snap : new World[0];
        }
        lastListFetchMs = now;
        World[] live = fetchWorldListLive();
        if (live != null && live.length > 0) {
            listSnapshot = live;
            listSnapshotMs = now;
            return live;
        }
        if (snap != null && snap.length > 0) {
            return snap;
        }
        listSnapshot = live != null ? live : new World[0];
        return listSnapshot;
    }

    private static void ingestWorldTypes(World[] live) {
        if (live == null || live.length == 0) {
            return;
        }
        int added = 0;
        for (World w : live) {
            if (w == null) {
                continue;
            }
            int id;
            try {
                id = w.getId();
            } catch (Throwable t) {
                continue;
            }
            boolean members = false;
            try {
                Set<WorldType> types = w.getTypes();
                members = types != null && types.contains(WorldType.MEMBERS);
            } catch (Throwable ignored) {
            }
            if (remember(id, members)) {
                added++;
            }
        }
        cacheFilledMs = System.currentTimeMillis();
        if (added > 0) {
            log.debug("[Worlds] type-cache bijgewerkt f2p={} members={}",
                    CACHE_F2P.size(), CACHE_MEMBERS.size());
        }
    }

    private static World[] fetchWorldListLive() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return new World[0];
            }
            try {
                java.lang.reflect.Method m = c.getClass().getMethod("getWorldList");
                Object v = m.invoke(c);
                if (v instanceof World[]) {
                    return (World[]) v;
                }
            } catch (Throwable t) {
                return new World[0];
            }
            return new World[0];
        }, new World[0]);
    }

    public static World get(int worldId) {
        if (worldId <= 0) {
            return null;
        }
        World[] all = snapshotWorldList(false);
        if (all == null) {
            return null;
        }
        for (World w : all) {
            if (w != null) {
                try {
                    if (w.getId() == worldId) {
                        return w;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static boolean hop(int worldId) {
        return WorldHopper.hop(worldId);
    }

    public static boolean isHopperOpen() {
        return WorldHopper.isOpen();
    }

    public static boolean isLoggedIn() {
        GameState gs = Game.getState();
        return gs == GameState.LOGGED_IN || gs == GameState.LOADING;
    }
}
