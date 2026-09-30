package net.storm.api.query;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.IActor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Simple fluent actor query (NPCs / players). Storm has richer query packages;
 * this covers names / ids / filter / nearest / list for LoneBot.
 *
 * @param <T> actor type
 */
public final class ActorQuery<T extends IActor> {

    private final Supplier<List<T>> source;
    private final Supplier<WorldPoint> origin;
    private String[] names;
    private int[] ids;
    private Predicate<T> filter;

    public ActorQuery(Supplier<List<T>> source) {
        this(source, null);
    }

    public ActorQuery(Supplier<List<T>> source, Supplier<WorldPoint> origin) {
        this.source = source != null ? source : ArrayList::new;
        this.origin = origin;
    }

    public ActorQuery<T> names(String... names) {
        this.names = names;
        return this;
    }

    public ActorQuery<T> ids(int... ids) {
        this.ids = ids;
        return this;
    }

    public ActorQuery<T> filter(Predicate<T> filter) {
        this.filter = this.filter == null ? filter : this.filter.and(filter);
        return this;
    }

    /** Storm {@code alive()} — drop dead actors (health bar ratio 0). */
    public ActorQuery<T> alive() {
        return filter(t -> t != null && !t.isDead());
    }

    public List<T> list() {
        List<T> all = source.get();
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        return all.stream().filter(this::matches).collect(Collectors.toCollection(ArrayList::new));
    }

    /** First match, or null. */
    public T first() {
        List<T> matches = list();
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Nearest to origin supplier (local player for {@code NPCs.query()}), else {@link #first()}.
     */
    public T nearest() {
        WorldPoint from = origin != null ? origin.get() : null;
        if (from != null) {
            return nearest(from);
        }
        return first();
    }

    /** Nearest to a world point (RuneLite {@link WorldPoint#distanceTo}). */
    public T nearest(WorldPoint from) {
        return nearest(t -> {
            if (from == null || t == null || t.getWorldLocation() == null) {
                return Integer.MAX_VALUE;
            }
            return from.distanceTo(t.getWorldLocation());
        });
    }

    /**
     * Nearest using an explicit distance function (tiles).
     */
    public T nearest(java.util.function.ToIntFunction<T> distanceFn) {
        List<T> matches = list();
        if (matches.isEmpty() || distanceFn == null) {
            return first();
        }
        T best = null;
        int bestD = Integer.MAX_VALUE;
        for (T t : matches) {
            int d = distanceFn.applyAsInt(t);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    private boolean matches(T t) {
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
            int id = resolveId(t);
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

    private static int resolveId(IActor t) {
        return t != null ? t.getId() : -1;
    }

    @Override
    public String toString() {
        return "ActorQuery{names=" + Arrays.toString(names) + ", ids=" + Arrays.toString(ids)
                + ", filter=" + Objects.nonNull(filter) + '}';
    }
}
