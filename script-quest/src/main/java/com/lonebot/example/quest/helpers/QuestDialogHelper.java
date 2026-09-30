package com.lonebot.example.quest.helpers;

import com.lonebot.example.quest.QuestLog;
import net.storm.sdk.widgets.Dialog;

/**
 * Continue + exact dialog steps. Never clicks a generic {@code Yes} unless that
 * exact string is in the configured list (avoids skipping mid-quest branches).
 */
public final class QuestDialogHelper {

    private QuestDialogHelper() {
    }

    /**
     * @return {@code true} if a dialog action was taken (caller should delay)
     */
    public static boolean handle(QuestHelperDialogSteps steps) {
        if (QuestCutsceneHelper.inCutscene()) {
            if (Dialog.canContinue()) {
                Dialog.continueSpace();
                QuestLog.step("Dialog", "cutscene continue");
                return true;
            }
            if (Dialog.isViewingOptions() && steps != null && chooseExact(steps)) {
                return true;
            }
            return Dialog.isOpen();
        }
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            QuestLog.step("Dialog", "continue");
            return true;
        }
        if (Dialog.isViewingOptions()) {
            if (steps != null && chooseExact(steps)) {
                return true;
            }
            QuestLog.step("Dialog", "opties zonder exacte match — geen wildcard Yes");
            return true;
        }
        return Dialog.isOpen() && !Dialog.isViewingOptions();
    }

    private static boolean chooseExact(QuestHelperDialogSteps steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        for (String want : steps.steps()) {
            if (Dialog.hasOption(t -> exactOption(t, want))) {
                boolean ok = Dialog.chooseOption(t -> exactOption(t, want));
                if (ok) {
                    QuestLog.step("Dialog", "kies \"" + want + "\"");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Exact or contains — but the configured string must appear in the option.
     * A bare configured {@code Yes} only matches options that are exactly Yes / Yes.
     */
    private static boolean exactOption(String optionText, String want) {
        if (optionText == null || want == null) {
            return false;
        }
        String o = strip(optionText);
        String w = strip(want);
        if (o.equalsIgnoreCase(w)) {
            return true;
        }
        if (isBareYes(w)) {
            return isBareYes(o);
        }
        return o.toLowerCase().contains(w.toLowerCase());
    }

    private static boolean isBareYes(String s) {
        String t = s.replace(".", "").replace("!", "").trim();
        return "yes".equalsIgnoreCase(t) || "yes please".equalsIgnoreCase(t);
    }

    private static String strip(String s) {
        return s.replace('\u00A0', ' ').replaceAll("<[^>]+>", "").trim();
    }
}
