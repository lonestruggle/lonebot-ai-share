package net.storm.sdk.entities;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.query.ActorQuery;
import net.storm.sdk.game.Static;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Storm {@code Players} — local player + scene players.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/entities/Players.html">Storm Players</a>
 */
public final class Players {

    private Players() {
    }

    public static final class LocalSnap {
        public final boolean present;
        public final WorldPoint worldLocation;
        public final boolean interacting;
        public final String interactingName;
        public final int interactingNpcIndex;
        public final int animation;
        public final boolean animating;
        public final boolean moving;
        public final boolean idle;
        public final int combatLevel;
        /** Gele vlag ({@code localDestinationLocation}), of {@code null}. */
        public final WorldPoint walkDestination;

        public LocalSnap(boolean present, WorldPoint worldLocation, boolean interacting,
                         String interactingName, int interactingNpcIndex, int animation,
                         boolean animating, boolean moving, boolean idle, int combatLevel) {
            this(present, worldLocation, interacting, interactingName, interactingNpcIndex,
                    animation, animating, moving, idle, combatLevel, null);
        }

        public LocalSnap(boolean present, WorldPoint worldLocation, boolean interacting,
                         String interactingName, int interactingNpcIndex, int animation,
                         boolean animating, boolean moving, boolean idle, int combatLevel,
                         WorldPoint walkDestination) {
            this.present = present;
            this.worldLocation = worldLocation;
            this.interacting = interacting;
            this.interactingName = interactingName;
            this.interactingNpcIndex = interactingNpcIndex;
            this.animation = animation;
            this.animating = animating;
            this.moving = moving;
            this.idle = idle;
            this.combatLevel = combatLevel;
            this.walkDestination = walkDestination;
        }
    }

    private static volatile LocalSnap lastGood = empty();
    private static volatile long lastGoodMs;
    /** Gezet op de client-thread (GameTick) — loop leest dit zonder 1.5s-wait. */
    private static volatile LocalSnap tickSnap = empty();
    private static volatile long tickSnapMs;

    /**
     * Alleen client-thread (Bootstrap {@code onGameTick}).
     * Loop-thread mag hier niet vandaan {@code invokeAndWait} doen.
     */
    public static void refreshFromClient() {
        if (!Static.isOnClientThread()) {
            return;
        }
        LocalSnap s = capture(Static.getClient());
        if (s != null && s.present && s.worldLocation != null) {
            long now = System.currentTimeMillis();
            tickSnap = s;
            tickSnapMs = now;
            lastGood = s;
            lastGoodMs = now;
        }
    }

    public static LocalSnap snapshotLocal() {
        long nowSnap = System.currentTimeMillis();
        if (Static.isOnClientThread()) {
            refreshFromClient();
            LocalSnap t = tickSnap;
            return t != null && t.present ? t : empty();
        }
        LocalSnap tick = tickSnap;
        if (tick != null && tick.present && tick.worldLocation != null
                && nowSnap - tickSnapMs < 800L) {
            return tick;
        }
        LocalSnap cachedFresh = lastGood;
        if (cachedFresh != null && cachedFresh.present && cachedFresh.worldLocation != null
                && nowSnap - lastGoodMs < 80L) {
            return cachedFresh;
        }
        LocalSnap s = Static.callOnClientThread(() -> capture(Static.getClient()), empty(), 80L);
        if (s != null && s.present && s.worldLocation != null) {
            lastGood = s;
            lastGoodMs = System.currentTimeMillis();
            return s;
        }
        LocalSnap cached = lastGood;
        if (cached != null && cached.present && cached.worldLocation != null
                && System.currentTimeMillis() - lastGoodMs < 2500L) {
            return cached;
        }
        if (tick != null && tick.present && tick.worldLocation != null
                && nowSnap - tickSnapMs < 2500L) {
            return tick;
        }
        return s != null ? s : empty();
    }

    private static LocalSnap capture(Client c) {
        if (c == null) {
            return empty();
        }
        Player p = c.getLocalPlayer();
        if (p == null) {
            return empty();
        }
        boolean interacting = false;
        String interactingName = null;
        int interNpcIdx = -1;
        try {
            Actor other = p.getInteracting();
            interacting = other != null;
            if (other != null) {
                interactingName = other.getName();
                if (other instanceof net.runelite.api.NPC) {
                    interNpcIdx = ((net.runelite.api.NPC) other).getIndex();
                }
            }
        } catch (Throwable ignored) {
        }
        int anim = p.getAnimation();
        boolean moving = false;
        WorldPoint walkDest = null;
        try {
            LocalPoint dest = c.getLocalDestinationLocation();
            if (dest != null) {
                moving = true;
                walkDest = WorldPoint.fromLocal(c, dest);
            } else {
                int pose = p.getPoseAnimation();
                int walk = p.getWalkAnimation();
                int run = p.getRunAnimation();
                moving = (walk != -1 && pose == walk) || (run != -1 && pose == run)
                        || pose == p.getWalkRotateLeft() || pose == p.getWalkRotateRight()
                        || pose == p.getWalkRotate180();
            }
        } catch (Throwable ignored) {
            moving = false;
        }
        boolean animating = anim != -1;
        int cmb = 0;
        try {
            cmb = p.getCombatLevel();
        } catch (Throwable ignored) {
        }
        return new LocalSnap(true, p.getWorldLocation(), interacting, interactingName, interNpcIdx,
                anim, animating, moving, !animating && !moving, cmb, walkDest);
    }

    private static LocalSnap empty() {
        return new LocalSnap(false, null, false, null, -1, -1, false, false, true, 0, null);
    }

    public static Player unwrap(IPlayer player) {
        if (player instanceof RlPlayer) {
            return ((RlPlayer) player).unwrap();
        }
        return player != null ? player.getWrapped() : null;
    }

    /**
     * Storm {@code getLocal()} — live wrap. Prefer {@link #snapshotLocal()} from the loop thread
     * for combat/state reads.
     */
    public static IPlayer getLocal() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Player p = c.getLocalPlayer();
            return p != null ? new RlPlayer(p) : null;
        }, null);
    }

    public static ActorQuery<IPlayer> query() {
        return new ActorQuery<>(Players::getAll, () -> {
            LocalSnap me = snapshotLocal();
            return me.present ? me.worldLocation : null;
        });
    }

    public static List<IPlayer> getAll() {
        return getAll((Predicate<IPlayer>) null);
    }

    public static List<IPlayer> getAll(Predicate<IPlayer> filter) {
        return Static.callOnClientThread(() -> {
            List<IPlayer> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null) {
                return out;
            }
            for (Player p : c.getPlayers()) {
                if (p == null) {
                    continue;
                }
                IPlayer wrap = new RlPlayer(p);
                if (filter == null || filter.test(wrap)) {
                    out.add(wrap);
                }
            }
            return out;
        }, new ArrayList<>());
    }

    public static List<IPlayer> getAll(String... names) {
        return getAll(nameFilter(names));
    }

    public static IPlayer getNearest(Predicate<IPlayer> filter) {
        LocalSnap me = snapshotLocal();
        WorldPoint from = me.present ? me.worldLocation : null;
        return getNearest(from, filter);
    }

    public static IPlayer getNearest(String... names) {
        return getNearest(nameFilter(names));
    }

    public static IPlayer getNearest(WorldPoint from, Predicate<IPlayer> filter) {
        IPlayer best = null;
        int bestD = Integer.MAX_VALUE;
        for (IPlayer p : getAll(filter)) {
            if (from == null || p.getWorldLocation() == null) {
                continue;
            }
            int d = from.distanceTo(p.getWorldLocation());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static Predicate<IPlayer> nameFilter(String... names) {
        if (names == null || names.length == 0) {
            return null;
        }
        return p -> {
            String n = p.getName();
            if (n == null) {
                return false;
            }
            for (String want : names) {
                if (want != null && want.equalsIgnoreCase(n)) {
                    return true;
                }
            }
            return false;
        };
    }
}
