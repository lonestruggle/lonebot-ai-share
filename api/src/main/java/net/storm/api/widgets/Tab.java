package net.storm.api.widgets;

/**
 * Game sidebar tabs. Storm names: {@link #CLAN_CHAT}, {@link #LOG_OUT}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/Tab.html">Storm Tab</a>
 */
public enum Tab {
    COMBAT,
    SKILLS,
    QUESTS,
    INVENTORY,
    EQUIPMENT,
    PRAYER,
    MAGIC,
    CLAN_CHAT,
    ACCOUNT,
    FRIENDS,
    LOG_OUT,
    OPTIONS,
    EMOTES,
    MUSIC,
    /** @deprecated Storm name is {@link #CLAN_CHAT} */
    @Deprecated
    CLAN,
    /** @deprecated Storm name is {@link #LOG_OUT} */
    @Deprecated
    LOGOUT;

    public static Tab canonical(Tab tab) {
        if (tab == CLAN || tab == CLAN_CHAT) {
            return CLAN_CHAT;
        }
        if (tab == LOGOUT || tab == LOG_OUT) {
            return LOG_OUT;
        }
        return tab;
    }

    public static boolean same(Tab a, Tab b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return canonical(a) == canonical(b);
    }
}
