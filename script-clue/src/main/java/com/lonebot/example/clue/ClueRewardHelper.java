package com.lonebot.example.clue;

import net.storm.api.domain.widgets.IWidget;
import net.storm.api.widgets.WidgetGroup;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.widgets.Widgets;

import java.awt.event.KeyEvent;

/**
 * Open reward interface after casket and take loot.
 * CombatBot: take items then close — nooit vast op lege reward-UI.
 */
public final class ClueRewardHelper {

    private int takeIndex;
    private long lastTakeMs;
    private long lastEmptyLogMs;
    private int emptyTicks;
    private boolean justClosed;

    public void reset() {
        takeIndex = 0;
        lastTakeMs = 0L;
        lastEmptyLogMs = 0L;
        emptyTicks = 0;
        justClosed = false;
    }

    public boolean isOpen() {
        return BeginnerClueContainerHelper.isRewardOpen();
    }

    /** True één tick nadat reward-UI gesloten werd (caller reset step). */
    public boolean consumeClosed() {
        if (!justClosed) {
            return false;
        }
        justClosed = false;
        return true;
    }

    public int takeLoot() {
        if (!isOpen()) {
            emptyTicks = 0;
            return -1;
        }
        if (System.currentTimeMillis() - lastTakeMs < 250L) {
            return 200;
        }
        IWidget hit = nextRewardItem();
        if (hit == null) {
            emptyTicks++;
            long now = System.currentTimeMillis();
            if (now - lastEmptyLogMs >= 1_500L) {
                lastEmptyLogMs = now;
                BotRuntime.logConsole("[Clue/Reward] leeg — sluit UI");
            }
            // Na 2 lege ticks: ESC en door
            if (emptyTicks >= 2) {
                try {
                    Keyboard.pressKey(KeyEvent.VK_ESCAPE);
                } catch (Throwable ignored) {
                }
                emptyTicks = 0;
                justClosed = true;
                return 400;
            }
            return 350;
        }
        emptyTicks = 0;
        boolean ok = hit.interact("Take") || hit.interact("Withdraw");
        lastTakeMs = System.currentTimeMillis();
        takeIndex++;
        if (ok) {
            BotRuntime.logConsole("[Clue/Reward] take");
        }
        return 350;
    }

    private IWidget nextRewardItem() {
        IWidget[] found = Widgets.getAll(w -> w != null && !w.isHidden()
                && (w.getId() >>> 16) == WidgetGroup.CLUE_SCROLL_REWARD_GROUP_ID
                && w.getItemId() > 0).toArray(new IWidget[0]);
        if (found.length == 0) {
            return Widgets.getFirst(w -> w != null && !w.isHidden()
                    && (w.getId() >>> 16) == WidgetGroup.CLUE_SCROLL_REWARD_GROUP_ID
                    && w.hasAction("Take"));
        }
        int i = Math.floorMod(takeIndex, found.length);
        return found[i];
    }
}
