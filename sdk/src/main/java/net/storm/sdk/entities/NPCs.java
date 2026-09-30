package net.storm.sdk.entities;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.storm.api.domain.actors.INPC;
import net.storm.api.query.ActorQuery;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.ClickPoints;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class NPCs {

    private NPCs() {
    }

    public static final class Snapshot {
        public final int npcTotal;
        public final int matchCount;
        public final INPC nearest;
        /** Canvas click-punt (hull) — null als off-screen. */
        public final Point nearestCanvas;
        public final int nearestWorldX;
        public final int nearestWorldY;

        public Snapshot(int npcTotal, int matchCount, INPC nearest, Point nearestCanvas,
                        int nearestWorldX, int nearestWorldY) {
            this.npcTotal = npcTotal;
            this.matchCount = matchCount;
            this.nearest = nearest;
            this.nearestCanvas = nearestCanvas;
            this.nearestWorldX = nearestWorldX;
            this.nearestWorldY = nearestWorldY;
        }
    }

    public static ActorQuery<INPC> query() {
        return new ActorQuery<>(() -> getAll((Predicate<INPC>) null), () ->
                Static.callOnClientThread(() -> {
                    Client c = Static.getClient();
                    if (c == null || c.getLocalPlayer() == null) {
                        return null;
                    }
                    return c.getLocalPlayer().getWorldLocation();
                }, null));
    }

    /** Unwrap for menu/useOn — null if not an SDK wrap. */
    public static NPC unwrap(INPC npc) {
        if (npc instanceof RlNpc) {
            return ((RlNpc) npc).unwrap();
        }
        return null;
    }

    public static List<INPC> getAll() {
        return getAll((Predicate<INPC>) null);
    }

    public static INPC getHintArrowed() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                NPC hint = c.getHintArrowNpc();
                return hint != null ? new RlNpc(hint) : null;
            } catch (Throwable t) {
                return null;
            }
        }, null);
    }

    public static INPC getNearest(net.runelite.api.coords.WorldPoint worldPoint, Predicate<INPC> filter) {
        if (worldPoint == null) {
            return getNearest(filter);
        }
        return Static.callOnClientThread(() -> {
            INPC best = null;
            int bestDist = Integer.MAX_VALUE;
            for (INPC npc : getAllOnClient(filter)) {
                if (npc.getWorldLocation() == null) {
                    continue;
                }
                int d = worldPoint.distanceTo(npc.getWorldLocation());
                if (d < bestDist) {
                    bestDist = d;
                    best = npc;
                }
            }
            return best;
        }, null);
    }

    public static INPC getNearest(net.runelite.api.coords.WorldPoint worldPoint, String... names) {
        return getNearest(worldPoint, nameFilter(names));
    }

    public static INPC getNearest(net.runelite.api.coords.WorldPoint worldPoint, int... ids) {
        return getNearest(worldPoint, idFilter(ids));
    }

    public static ActorQuery<INPC> query(java.util.function.Supplier<List<INPC>> supplier) {
        return new ActorQuery<>(supplier != null ? supplier : () -> getAll((Predicate<INPC>) null));
    }

    public static INPC getNearest(Predicate<INPC> filter) {
        return Static.callOnClientThread(() -> getNearestOnClient(filter), null);
    }

    public static INPC getNearest(String... names) {
        return getNearest(nameFilter(names));
    }

    public static INPC getNearest(int... ids) {
        return getNearest(idFilter(ids));
    }

    public static List<INPC> getAll(String... names) {
        return getAll(nameFilter(names));
    }

    public static List<INPC> getAll(int... ids) {
        return getAll(idFilter(ids));
    }

    /** NPC by client index, or null. */
    public static INPC get(int index) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            for (NPC npc : c.getNpcs()) {
                if (npc != null && npc.getIndex() == index) {
                    return new RlNpc(npc);
                }
            }
            return null;
        }, null);
    }

    private static INPC getNearestOnClient(Predicate<INPC> filter) {
        Client c = Static.getClient();
        if (c == null || c.getLocalPlayer() == null) {
            return null;
        }
        INPC best = null;
        int bestDist = Integer.MAX_VALUE;
        for (NPC npc : c.getNpcs()) {
            if (npc == null) {
                continue;
            }
            INPC wrap = new RlNpc(npc);
            if (filter != null && !filter.test(wrap)) {
                continue;
            }
            if (wrap.getWorldLocation() == null) {
                continue;
            }
            int d = c.getLocalPlayer().getWorldLocation().distanceTo(wrap.getWorldLocation());
            if (d < bestDist) {
                bestDist = d;
                best = wrap;
            }
        }
        return best;
    }

    /** Alles in één client-thread tick: counts + nearest + canvas hull-coords. */
    public static Snapshot snapshotClientThread(Predicate<INPC> filter) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return new Snapshot(0, 0, null, null, -1, -1);
            }
            int total = 0;
            for (NPC npc : c.getNpcs()) {
                if (npc != null) {
                    total++;
                }
            }
            List<INPC> matches = getAllOnClient(filter);
            INPC nearest = getNearestOnClient(filter);
            Point canvas = nearest != null ? ClickPoints.forNpcOnClient(nearest) : null;
            int wx = -1;
            int wy = -1;
            if (nearest != null && nearest.getWorldLocation() != null) {
                wx = nearest.getWorldLocation().getX();
                wy = nearest.getWorldLocation().getY();
            }
            return new Snapshot(total, matches.size(), nearest, canvas, wx, wy);
        }, new Snapshot(0, 0, null, null, -1, -1));
    }

    public static List<INPC> getAll(Predicate<INPC> filter) {
        return Static.callOnClientThread(() -> getAllOnClient(filter), new ArrayList<>());
    }

    private static List<INPC> getAllOnClient(Predicate<INPC> filter) {
        List<INPC> out = new ArrayList<>();
        Client c = Static.getClient();
        if (c == null) {
            return out;
        }
        for (NPC npc : c.getNpcs()) {
            if (npc == null) {
                continue;
            }
            INPC wrap = new RlNpc(npc);
            if (filter == null || filter.test(wrap)) {
                out.add(wrap);
            }
        }
        return out;
    }

    private static Predicate<INPC> nameFilter(String... names) {
        if (names == null || names.length == 0) {
            return null;
        }
        return npc -> {
            String n = npc.getName();
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

    private static Predicate<INPC> idFilter(int... ids) {
        if (ids == null || ids.length == 0) {
            return null;
        }
        return npc -> {
            int id = npc.getId();
            for (int want : ids) {
                if (want == id) {
                    return true;
                }
            }
            return false;
        };
    }
}
