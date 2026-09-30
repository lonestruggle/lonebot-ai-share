package com.lonebot.example.quest.helpers;

import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.game.Vars;
import net.storm.sdk.widgets.Widgets;

/**
 * Block walk during cutscenes; dialog/space only (CombatBot {@code QuestCutsceneHelper}).
 */
public final class QuestCutsceneHelper {

    /** Quest Helper / RuneLite cutscene varbit. */
    public static final int CUTSCENE_VARBIT = 4606;

    private QuestCutsceneHelper() {
    }

    public static boolean inCutscene() {
        if (Vars.getVarbit(CUTSCENE_VARBIT) == 1) {
            return true;
        }
        IWidget w = Widgets.get(133, 0);
        return w != null && !w.isHidden();
    }
}
