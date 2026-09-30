package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Dialog;

/**
 * Clue NPC Talk-to: walk → menu-invoke (COS), geen Aim-muis op stale NPC-positie.
 * Bewegende NPCs (Charlie) missen anders met LMB op oude canvas-coords.
 */
public final class ClueNpcClick {

    private ClueNpcClick() {
    }

    /**
     * Talk-to via menu-invoke op live NPC-index. Geen Aim mouse+verify.
     *
     * @return true als interact uitgegeven
     */
    public static boolean talkToInvoke(INPC npc) {
        if (npc == null) {
            return false;
        }
        if (Dialog.isOpen()) {
            return false;
        }
        try {
            int idx = npc.getIndex();
            if (idx >= 0) {
                if (MenuInteract.interactNpcByIndex(idx, "Talk-to")) {
                    BotRuntime.logConsole("[Clue/COS] invoke Talk-to → " + safeName(npc));
                    return true;
                }
                if (MenuInteract.interactNpcByIndex(idx, "Talk")) {
                    BotRuntime.logConsole("[Clue/COS] invoke Talk → " + safeName(npc));
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        // Fallback: ClickOnSight (probeert eerst ook invoke)
        WorldPoint tile = npc.getWorldLocation();
        ClickOnSight.Result r = ClickOnSight.interactOrApproach(npc, tile, "Talk-to", "Talk");
        if (r != null && r.clicked) {
            BotRuntime.logConsole("[Clue/COS] " + r.detail);
            return true;
        }
        return false;
    }

    /**
     * @return delay ms (WALK/CAMERA/BLOCKED) of 0 als klik klaar / niets
     */
    public static int talkOrApproach(INPC npc, WorldPoint approachTile) {
        if (Dialog.isOpen()) {
            return 0;
        }
        if (npc == null) {
            if (approachTile != null) {
                Movement.walkTo(approachTile);
                return 400;
            }
            return 0;
        }
        if (talkToInvoke(npc)) {
            return 550;
        }
        WorldPoint dest = npc.getWorldLocation() != null ? npc.getWorldLocation() : approachTile;
        ClickOnSight.Result r = ClickOnSight.interactOrApproach(npc, dest, "Talk-to", "Talk");
        if (r == null) {
            return 400;
        }
        switch (r.outcome) {
            case CLICKED:
                BotRuntime.logConsole("[Clue/COS] " + r.detail);
                return 550;
            case WALK:
            case BLOCKED:
            case CAMERA:
                BotRuntime.logConsole("[Clue/COS] " + r.outcome + " " + r.detail);
                return 400;
            default:
                return 400;
        }
    }

    private static String safeName(INPC npc) {
        try {
            String n = npc.getName();
            return n != null ? n : ("#" + npc.getIndex());
        } catch (Throwable t) {
            return "?";
        }
    }
}
