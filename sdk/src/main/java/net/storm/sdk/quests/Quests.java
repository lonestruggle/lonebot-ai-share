package net.storm.sdk.quests;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.storm.sdk.game.Static;
import net.storm.sdk.game.Vars;

/**
 * Quest state helpers.
 */
public final class Quests {

    private Quests() {
    }

    /**
     * Quest progress via RuneLite {@link Quest#getState(Client)}.
     */
    public static QuestState getState(Quest quest) {
        if (quest == null) {
            return QuestState.NOT_STARTED;
        }
        QuestState state = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return QuestState.NOT_STARTED;
            }
            try {
                return quest.getState(c);
            } catch (Throwable t) {
                return QuestState.NOT_STARTED;
            }
        }, QuestState.NOT_STARTED);
        return state != null ? state : QuestState.NOT_STARTED;
    }

    /**
     * @stub Fallback when only a varp id is known — returns raw varp value, not a {@link QuestState}.
     */
    public static int getVarp(int varpId) {
        return Static.callOnClientThread(() -> Vars.getVarp(varpId), 0);
    }

    public static boolean isFinished(Quest quest) {
        return getState(quest) == QuestState.FINISHED;
    }

    public static boolean isStarted(Quest quest) {
        QuestState s = getState(quest);
        return s == QuestState.IN_PROGRESS || s == QuestState.FINISHED;
    }

    public static boolean isFinished(String questName) {
        Quest q = find(questName);
        return q != null && isFinished(q);
    }

    public static boolean isStarted(String questName) {
        Quest q = find(questName);
        return q != null && isStarted(q);
    }

    public static Quest find(String questName) {
        if (questName == null || questName.isBlank()) {
            return null;
        }
        String want = questName.trim().replace(' ', '_').toUpperCase();
        try {
            return Quest.valueOf(want);
        } catch (IllegalArgumentException e) {
            for (Quest q : Quest.values()) {
                if (q.name().equalsIgnoreCase(want)
                        || q.name().replace("_", "").equalsIgnoreCase(want.replace("_", ""))) {
                    return q;
                }
            }
            return null;
        }
    }
}
