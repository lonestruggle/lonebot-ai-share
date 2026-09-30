package com.lonebot.example.quest;

import net.runelite.api.Quest;

/**
 * CombatBot {@code QuestBotScript}: displayName, RuneLite quest, reset, loop delay.
 */
public interface QuestBotScript {

    String displayName();

    QuestBotTarget target();

    /**
     * RuneLite {@link Quest} for completion checks, or {@code null} (Tutorial Island).
     */
    Quest runeliteQuest();

    void reset();

    /** @return ms until next loop */
    int loop();

    default boolean isFinished() {
        Quest q = runeliteQuest();
        return q != null && net.storm.sdk.quests.Quests.isFinished(q);
    }

    /**
     * Rotation: skip when a hard skill gate fails. Coins-low still returns {@code true}.
     */
    default boolean isFeasible() {
        return true;
    }
}
