package net.storm.api.query;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Fluent tile-object query (trees, rocks, doors, …).
 */
public final class TileObjectQuery {

    private final Supplier<List<ITileObject>> source;
    private final Supplier<WorldPoint> origin;
    private String[] names;
    private int[] ids;
    private Predicate<ITileObject> filter;

    public TileObjectQuery(Supplier<List<ITileObject>> source) {
        this(source, null);
    }

    public TileObjectQuery(Supplier<List<ITileObject>> source, Supplier<WorldPoint> origin) {
        this.source = source != null ? source : ArrayList::new;
        this.origin = origin;
    }

    public TileObjectQuery names(String... names) {
        this.names = names;
        return this;
    }

    public TileObjectQuery ids(int... ids) {
        this.ids = ids;
        return this;
    }

    public TileObjectQuery filter(Predicate<ITileObject> filter) {
        this.filter = filter;
        return this;
    }

    public List<ITileObject> list() {
        List<ITileObject> all = source.get();
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        return all.stream().filter(this::matches).collect(Collectors.toCollection(ArrayList::new));
    }

    public ITileObject first() {
        List<ITileObject> matches = list();
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** Nearest to {@code origin} supplier if set; otherwise first match. */
    public ITileObject nearest() {
        WorldPoint from = origin != null ? origin.get() : null;
        if (from != null) {
            return nearest(from);
        }
        return first();
    }

    public ITileObject nearest(WorldPoint from) {
        return nearest(obj -> {
            if (from == null || obj == null || obj.getWorldLocation() == null) {
                return Integer.MAX_VALUE;
            }
            return from.distanceTo(obj.getWorldLocation());
        });
    }

    public ITileObject nearest(ToIntFunction<ITileObject> distanceFn) {
        List<ITileObject> matches = list();
        if (matches.isEmpty() || distanceFn == null) {
            return first();
        }
        ITileObject best = null;
        int bestD = Integer.MAX_VALUE;
        for (ITileObject t : matches) {
            int d = distanceFn.applyAsInt(t);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    private boolean matches(ITileObject t) {
        if (t == null) {
            return false;
        }
        if (names != null && names.length > 0) {
            String n = t.getName();
            boolean ok = false;
            for (String want : names) {
                if (want != null && n != null && want.equalsIgnoreCase(n)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        if (ids != null && ids.length > 0) {
            int id = t.getId();
            boolean ok = false;
            for (int want : ids) {
                if (want == id) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return filter == null || filter.test(t);
    }

    @Override
    public String toString() {
        return "TileObjectQuery{names=" + Arrays.toString(names) + ", ids=" + Arrays.toString(ids)
                + ", filter=" + Objects.nonNull(filter) + '}';
    }
}
