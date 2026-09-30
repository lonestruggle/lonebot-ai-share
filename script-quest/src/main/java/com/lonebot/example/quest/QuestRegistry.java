package com.lonebot.example.quest;

import com.lonebot.example.quest.scripts.CooksAssistantScript;
import com.lonebot.example.quest.scripts.DoricsQuestScript;
import com.lonebot.example.quest.scripts.GoblinDiplomacyScript;
import com.lonebot.example.quest.scripts.RomeoAndJulietScript;
import com.lonebot.example.quest.scripts.RuneMysteriesScript;
import com.lonebot.example.quest.scripts.TutorialIslandScript;
import com.lonebot.example.quest.scripts.VampireSlayerScript;

import java.util.EnumMap;
import java.util.Map;

/**
 * CombatBot {@code QuestRegistry} — F2P scripts only in this PR.
 */
public final class QuestRegistry {

    private final Map<QuestBotTarget, QuestBotScript> byTarget = new EnumMap<>(QuestBotTarget.class);

    public QuestRegistry() {
        register(new CooksAssistantScript());
        register(new GoblinDiplomacyScript());
        register(new RomeoAndJulietScript());
        register(new RuneMysteriesScript());
        register(new DoricsQuestScript());
        register(new VampireSlayerScript());
        register(new TutorialIslandScript());
    }

    private void register(QuestBotScript s) {
        if (s != null && s.target() != null && s.target() != QuestBotTarget.ROTATION) {
            byTarget.put(s.target(), s);
        }
    }

    public QuestBotScript get(QuestBotTarget target) {
        if (target == null || target.isRotation()) {
            return null;
        }
        return byTarget.get(target);
    }

    public Iterable<QuestBotScript> all() {
        return byTarget.values();
    }

    public void resetAll() {
        for (QuestBotScript s : byTarget.values()) {
            s.reset();
        }
    }
}
