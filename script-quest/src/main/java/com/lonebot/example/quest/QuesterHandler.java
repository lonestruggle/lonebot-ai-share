package com.lonebot.example.quest;

import com.lonebot.example.QuesterPlugin;
import com.lonebot.example.quest.helpers.QuestCompletionHelper;
import com.lonebot.example.quest.helpers.QuestCutsceneHelper;
import com.lonebot.example.quest.helpers.QuestDialogHelper;
import com.lonebot.example.quest.helpers.QuestHelperDialogSteps;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Game;
import net.storm.sdk.widgets.Dialog;

/**
 * Picks {@link QuestBotScript} from {@link QuestRegistry} based on {@link QuestBotTarget}.
 */
public final class QuesterHandler {

    private final QuestRegistry registry = new QuestRegistry();
    private QuestBotScript current;
    private QuestBotTarget lockedTarget;

    public void reset() {
        registry.resetAll();
        current = null;
        lockedTarget = null;
        com.lonebot.example.quest.helpers.QuestBankGePrep.reset();
        BotRuntime.questStatus = "reset";
    }

    public QuestRegistry registry() {
        return registry;
    }

    public QuestBotScript current() {
        return current;
    }

    public int loop() {
        if (!Game.isLoggedIn()) {
            BotRuntime.questStatus = "niet ingelogd";
            return 800;
        }
        if (QuestCompletionHelper.dismiss()) {
            return 600;
        }
        if (QuestCutsceneHelper.inCutscene()) {
            QuestLog.step("Cutscene", "geen walk — dialog/space");
            QuestDialogHelper.handle(QuestHelperDialogSteps.of());
            if (Dialog.canContinue()) {
                Dialog.continueSpace();
            }
            return 400;
        }
        QuestBotTarget want = resolveTarget();
        if (want != lockedTarget) {
            lockedTarget = want;
            current = pick(want);
            if (current != null) {
                current.reset();
                QuestLog.force("Handler", "start " + current.displayName());
            }
        }
        if (current == null) {
            BotRuntime.questStatus = want.isRotation()
                    ? "rotatie: niets open"
                    : "geen script voor " + want.label;
            return 1200;
        }
        if (current.isFinished()) {
            QuestLog.step("Handler", current.displayName() + " finished");
            if (want.isRotation() || QuesterPlugin.rotationEnabled) {
                current = null;
                lockedTarget = null;
                return 400;
            }
            BotRuntime.questStatus = current.displayName() + " klaar";
            return 1500;
        }
        try {
            return Math.max(200, current.loop());
        } catch (Throwable t) {
            QuestLog.force("Handler", "fout: " + t.getClass().getSimpleName());
            return 800;
        }
    }

    private QuestBotTarget resolveTarget() {
        return QuestBotTarget.from(QuesterPlugin.target);
    }

    private QuestBotScript pick(QuestBotTarget want) {
        if (want == null || want.isRotation()) {
            return QuestRotationManager.next(registry, QuesterPlugin.skipLowLevel);
        }
        return registry.get(want);
    }
}
