package net.storm.api.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Fluent item query (inventory / ground items).
 *
 * @param <T> item type
 */
public final class ItemQuery<T> {

    private final Supplier<List<T>> source;
    private final Function<T, String> nameFn;
    private final ToIntFunction<T> idFn;
    private String[] names;
    private int[] ids;
    private Predicate<T> filter;

    public ItemQuery(Supplier<List<T>> source, Function<T, String> nameFn, ToIntFunction<T> idFn) {
        this.source = source != null ? source : ArrayList::new;
        this.nameFn = nameFn != null ? nameFn : t -> null;
        this.idFn = idFn != null ? idFn : t -> -1;
    }

    public ItemQuery<T> names(String... names) {
        this.names = names;
        return this;
    }

    public ItemQuery<T> ids(int... ids) {
        this.ids = ids;
        return this;
    }

    public ItemQuery<T> filter(Predicate<T> filter) {
        this.filter = filter;
        return this;
    }

    public List<T> list() {
        List<T> all = source.get();
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        return all.stream().filter(this::matches).collect(Collectors.toCollection(ArrayList::new));
    }

    public T first() {
        List<T> matches = list();
        return matches.isEmpty() ? null : matches.get(0);
    }

    private boolean matches(T t) {
        if (t == null) {
            return false;
        }
        if (names != null && names.length > 0) {
            String n = nameFn.apply(t);
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
            int id = idFn.applyAsInt(t);
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
        return "ItemQuery{names=" + Arrays.toString(names) + ", ids=" + Arrays.toString(ids)
                + ", filter=" + Objects.nonNull(filter) + '}';
    }
}
