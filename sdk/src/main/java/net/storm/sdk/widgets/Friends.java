package net.storm.sdk.widgets;

import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.widgets.IFriends;
import net.storm.api.widgets.Tab;
import net.storm.sdk.game.Static;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Storm {@code Friends} — friends-list query / add.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/widgets/Friends.html">Storm Friends</a>
 */
public final class Friends {

    private static final Logger log = LoggerFactory.getLogger(Friends.class);

    public static final IFriends API = new Api();

    static {
        net.storm.api.Static.bindFriends(API);
    }

    public Friends() {
    }

    public static boolean isFriendsListOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                Widget w = c.getWidget(WidgetInfo.FRIEND_LIST_FULL_CONTAINER);
                if (w != null && !w.isHidden()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return Tabs.isOpen(Tab.FRIENDS);
        }, false));
    }

    public static int getFriendCount() {
        Integer n = Static.callOnClientThread(() -> members().size(), 0);
        return n != null ? n : 0;
    }

    public static List<Friend> getAll(Predicate<Friend> filter) {
        return Static.callOnClientThread(() -> {
            List<Friend> out = new ArrayList<>();
            for (Friend f : members()) {
                if (f != null && (filter == null || filter.test(f))) {
                    out.add(f);
                }
            }
            return out;
        }, new ArrayList<>());
    }

    public static List<Friend> getAll(String... names) {
        return API.getAll(names);
    }

    public static List<Friend> getAll(int... worlds) {
        return API.getAll(worlds);
    }

    public static Friend getFirst(Predicate<Friend> filter) {
        return API.getFirst(filter);
    }

    public static Friend getFirst(String... names) {
        return API.getFirst(names);
    }

    public static Friend getFirst(int... worlds) {
        return API.getFirst(worlds);
    }

    public static boolean isAdded(String name) {
        return isFriend(name);
    }

    public static boolean isFriend(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return getFirst(name) != null;
    }

    public static boolean isOnline(Friend friend) {
        return API.isOnline(friend);
    }

    public static boolean isOnline(String name) {
        Friend f = getFirst(name);
        return f != null && f.getWorld() > 0;
    }

    public static void addFriend(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        Tabs.open(Tab.FRIENDS);
        Dialog.enterFriendName(name);
        log.info("[Friends] addFriend typed {}", name);
    }

    public static void removeFriend(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        Tabs.open(Tab.FRIENDS);
        log.info("[Friends] removeFriend({}) — friends tab open", name);
    }

    private static List<Friend> members() {
        Client c = Static.getClient();
        List<Friend> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        try {
            FriendContainer container = c.getFriendContainer();
            if (container == null) {
                return out;
            }
            Friend[] members = container.getMembers();
            if (members != null) {
                for (Friend f : members) {
                    if (f != null) {
                        out.add(f);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static final class Api implements IFriends {
        @Override
        public List<Friend> getAll(Predicate<Friend> filter) {
            return Friends.getAll(filter);
        }

        @Override
        public boolean isAdded(String name) {
            return Friends.isAdded(name);
        }

        @Override
        public boolean isOnline(String name) {
            return Friends.isOnline(name);
        }
    }
}
