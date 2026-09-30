package com.lonebot.example.quest.helpers;

import com.lonebot.example.quest.QuestLog;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.widgets.Widgets;

import java.awt.event.KeyEvent;

/**
 * Quest-complete scroll: interface 153, child 3 ("Congratulations").
 */
public final class QuestCompletionHelper {

    public static final int IFACE = 153;
    public static final int CHILD = 3;

    private QuestCompletionHelper() {
    }

    public static boolean isOpen() {
        IWidget w = Widgets.get(IFACE, CHILD);
        if (w != null && !w.isHidden()) {
            return true;
        }
        IWidget root = Widgets.get(IFACE, 0);
        return root != null && !root.isHidden();
    }

    /**
     * @return {@code true} if the scroll was dismissed
     */
    public static boolean dismiss() {
        if (!isOpen()) {
            return false;
        }
        IWidget w = Widgets.get(IFACE, CHILD);
        if (w != null && !w.isHidden()) {
            if (w.interact("Continue") || w.interact("Close") || w.interact("OK")) {
                QuestLog.step("Complete", "Congratulations sluiten (153,3)");
                return true;
            }
        }
        IWidget root = Widgets.get(IFACE, 0);
        if (root != null && !root.isHidden()) {
            if (root.interact("Continue") || root.interact("Close")) {
                QuestLog.step("Complete", "Congratulations sluiten (153,0)");
                return true;
            }
        }
        Keyboard.pressKey(KeyEvent.VK_SPACE);
        QuestLog.step("Complete", "Congratulations space");
        return true;
    }
}
