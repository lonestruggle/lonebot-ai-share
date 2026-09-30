package net.storm.api.widgets;

import net.runelite.api.Friend;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Storm {@code IFriends}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IFriends.html">Storm IFriends</a>
 */
public interface IFriends {

    List<Friend> getAll(Predicate<Friend> filter);

    default List<Friend> getAll(String... names) {
        if (names == null || names.length == 0) {
            return new ArrayList<>();
        }
        return getAll(f -> {
            if (f == null || f.getName() == null) {
                return false;
            }
            for (String n : names) {
                if (n != null && n.equalsIgnoreCase(f.getName())) {
                    return true;
                }
            }
            return false;
        });
    }

    default List<Friend> getAll(int... worlds) {
        if (worlds == null || worlds.length == 0) {
            return new ArrayList<>();
        }
        return getAll(f -> {
            if (f == null) {
                return false;
            }
            int w = f.getWorld();
            for (int want : worlds) {
                if (w == want) {
                    return true;
                }
            }
            return false;
        });
    }

    default Friend getFirst(Predicate<Friend> filter) {
        List<Friend> all = getAll(filter);
        return all.isEmpty() ? null : all.get(0);
    }

    default Friend getFirst(String... names) {
        List<Friend> all = getAll(names);
        return all.isEmpty() ? null : all.get(0);
    }

    default Friend getFirst(int... worlds) {
        List<Friend> all = getAll(worlds);
        return all.isEmpty() ? null : all.get(0);
    }

    boolean isAdded(String name);

    default boolean isOnline(Friend friend) {
        return friend != null && friend.getWorld() > 0;
    }

    boolean isOnline(String name);
}
