package com.lonebot.example.quest;

import com.lonebot.example.quest.scripts.DoricsQuestScript;
import com.lonebot.example.quest.scripts.TutorialIslandScript;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.Inventory;

/**
 * Optional v1: next unfinished feasible F2P quest.
 * Level-too-low skip; coins-low still try.
 */
public final class QuestRotationManager {

    private static final QuestBotTarget[] ORDER = {
            QuestBotTarget.TUTORIAL,
            QuestBotTarget.COOKS_ASSISTANT,
            QuestBotTarget.DORICS,
            QuestBotTarget.GOBLIN_DIPLOMACY,
            QuestBotTarget.ROMEO_AND_JULIET,
            QuestBotTarget.RUNE_MYSTERIES,
            QuestBotTarget.VAMPYRE_SLAYER
    };

    private QuestRotationManager() {
    }

    public static QuestBotScript next(QuestRegistry registry, boolean skipLowLevel) {
        if (registry == null) {
            return null;
        }
        if (Vars.getVarp(TutorialIslandScript.VARP) < TutorialIslandScript.DONE) {
            QuestBotScript tut = registry.get(QuestBotTarget.TUTORIAL);
            if (tut != null && !tut.isFinished()) {
                QuestLog.step("Rotatie", "Tutorial Island eerst (varp 281)");
                return tut;
            }
        }
        int coins = Inventory.getCount(true, "Coins");
        for (QuestBotTarget t : ORDER) {
            if (t == QuestBotTarget.TUTORIAL) {
                continue;
            }
            QuestBotScript s = registry.get(t);
            if (s == null || s.isFinished()) {
                continue;
            }
            if (skipLowLevel && t == QuestBotTarget.DORICS && DoricsQuestScript.skipForRotation()) {
                QuestLog.step("Rotatie", "Doric skip — Mining ≥ 10");
                continue;
            }
            if (skipLowLevel && !s.isFeasible()) {
                QuestLog.step("Rotatie", s.displayName() + " skip — niet haalbaar");
                continue;
            }
            if (coins < 50) {
                QuestLog.step("Rotatie", s.displayName() + " (weinig coins — toch proberen)");
            } else {
                QuestLog.step("Rotatie", "volgende: " + s.displayName());
            }
            return s;
        }
        QuestLog.step("Rotatie", "geen open F2P-quest");
        return null;
    }
}
