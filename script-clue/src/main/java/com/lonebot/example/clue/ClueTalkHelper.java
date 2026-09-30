package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.widgets.Dialog;

/**
 * NPC Talk-to: dialog, Reldo / Brian / Hairdresser / Doric enclosed helpers, open NPCs via distance.
 * Charlie delivery FSM leeft in {@link CharlieClueHelper} — niet hier.
 */
public final class ClueTalkHelper {

    private String talkCompletedKey;
    private long lastTalkMs;

    public void reset() {
        talkCompletedKey = null;
        lastTalkMs = 0L;
        ReldoApproachHelper.reset();
        BrianShopApproachHelper.reset();
        HairdresserShopApproachHelper.reset();
        DoricHouseApproachHelper.reset();
    }

    public void resetIfClueChanged(String clueText) {
        String key = ClueScrollHelper.normalize(clueText);
        if (talkCompletedKey != null && key != null && !key.equals(talkCompletedKey)
                && !key.isEmpty() && !talkCompletedKey.isEmpty()
                && !clueKey(clueText).equals(talkCompletedKey)) {
            talkCompletedKey = null;
        }
    }

    public boolean npcTalkCompletedForActiveStep(String clueText) {
        String key = clueKey(clueText);
        return key != null && key.equals(talkCompletedKey);
    }

    public int handleDialog() {
        if (!Dialog.isOpen()) {
            return -1;
        }
        // Charlie tramp-menu: niet "Okay, here you go." kiezen (dat is geen clue)
        if (CharlieClueHelper.isDefaultTrampOptions()) {
            Dialog.continueSpace();
            BotRuntime.logConsole("[Clue/Talk] tramp-opties — niet kiezen, continue");
            return 400;
        }
        if (Dialog.isViewingOptions()) {
            if (ReldoApproachHelper.tryChooseReldoYesIDo()
                    || ReldoApproachHelper.tryChooseReldoDeviceDialog()) {
                BotRuntime.logConsole("[Clue/Talk] Reldo dialog option");
                return 400;
            }
            if (Dialog.chooseOption("Yes", "I have", "Give", "Here", "Sure", "What can I do")
                    || Dialog.chooseAnyOption("Yes", "I have the", "Here's", "Take this",
                    "What can I do for you")) {
                BotRuntime.logConsole("[Clue/Talk] dialog option");
                return 400;
            }
            // Continue-only onder OPTIONS ("Click here to continue") — geen chooseOption(0)
            if (Dialog.autoContinue(true)) {
                BotRuntime.logConsole("[Clue/Talk] auto-continue");
                return 350;
            }
            return 300;
        }
        if (Dialog.autoContinue(true)) {
            return 250;
        }
        return -1;
    }

    public int talkTo(BeginnerClueReference.NpcTarget target, String clueText) {
        if (target != null && isReldo(target)) {
            if (net.storm.sdk.items.Inventory.contains(BeginnerClueReference.STRANGE_DEVICE)) {
                markTalked(clueText, target);
                BotRuntime.logConsole("[Clue/Talk] Reldo klaar — Strange device in inv");
                return -2;
            }
            if (target.talkOnce && npcTalkCompletedForActiveStep(clueText)
                    && System.currentTimeMillis() - lastTalkMs < 12_000L
                    && !Dialog.isOpen()) {
                return 500;
            }
            int delay = ReldoApproachHelper.approachAndTalk();
            if (delay == -2) {
                markTalked(clueText, target);
                return -2;
            }
            if (Dialog.isOpen() || delay >= 500) {
                markTalked(clueText, target);
            }
            return delay;
        }
        if (target != null && BrianShopApproachHelper.isBrianNpcName(target.npcName)) {
            if (target.talkOnce && npcTalkCompletedForActiveStep(clueText)
                    && System.currentTimeMillis() - lastTalkMs < 12_000L) {
                return 500;
            }
            int dialog = handleDialog();
            if (dialog >= 0) {
                markTalked(clueText, target);
                return dialog;
            }
            int delay = BrianShopApproachHelper.approachAndTalk();
            if (Dialog.isOpen() || delay >= 500) {
                markTalked(clueText, target);
            }
            return delay;
        }
        if (target != null && HairdresserShopApproachHelper.isHairdresserNpcName(target.npcName)) {
            if (target.talkOnce && npcTalkCompletedForActiveStep(clueText)
                    && System.currentTimeMillis() - lastTalkMs < 12_000L) {
                return 500;
            }
            int dialog = handleDialog();
            if (dialog >= 0) {
                markTalked(clueText, target);
                return dialog;
            }
            int delay = HairdresserShopApproachHelper.approachAndTalk();
            if (Dialog.isOpen() || delay >= 500) {
                markTalked(clueText, target);
            }
            return delay;
        }
        if (target != null && DoricHouseApproachHelper.isDoricNpcName(target.npcName)) {
            if (target.talkOnce && npcTalkCompletedForActiveStep(clueText)
                    && System.currentTimeMillis() - lastTalkMs < 12_000L) {
                return 500;
            }
            int dialog = handleDialog();
            if (dialog >= 0) {
                markTalked(clueText, target);
                return dialog;
            }
            int delay = DoricHouseApproachHelper.approachAndTalk();
            if (Dialog.isOpen() || delay >= 500) {
                markTalked(clueText, target);
            }
            return delay;
        }
        int dialog = handleDialog();
        if (dialog >= 0) {
            markTalked(clueText, target);
            return dialog;
        }
        if (target == null) {
            return 600;
        }
        if (target.talkOnce && npcTalkCompletedForActiveStep(clueText)
                && System.currentTimeMillis() - lastTalkMs < 12_000L) {
            return 500;
        }
        WorldPoint dest = target.tile != null ? target.tile : ClueWalk.me();
        INPC npc = findNpc(target.npcName, dest);
        int dist = npc != null && npc.getWorldLocation() != null
                ? Movement.distanceTo(npc.getWorldLocation())
                : dest != null ? Movement.distanceTo(dest) : 99;
        if (dist > BeginnerClueReference.TALK_ARRIVAL + 4) {
            WorldPoint walkTo = npc != null && npc.getWorldLocation() != null
                    ? npc.getWorldLocation() : dest;
            ClueWalk.walkUntilNear(walkTo, BeginnerClueReference.TALK_ARRIVAL);
            return 400;
        }
        if (npc == null) {
            ClueWalk.walkUntilNear(dest, BeginnerClueReference.TALK_ARRIVAL);
            BotRuntime.logConsole("[Clue/Talk] wacht op " + target.npcName);
            return 500;
        }
        if (talk(npc)) {
            markTalked(clueText, target);
            BotRuntime.logConsole("[Clue/Talk] " + target.npcName);
            return 500;
        }
        return 400;
    }

    /** @deprecated Charlie FSM — gebruik {@link CharlieClueHelper}. */
    @Deprecated
    public int talkCharlie(String clueText, String itemName) {
        BotRuntime.logConsole("[Clue/Talk] talkCharlie deprecated — use CharlieClueHelper");
        return 600;
    }

    private static boolean isReldo(BeginnerClueReference.NpcTarget target) {
        if (target == null) {
            return false;
        }
        if (target.reldoDoors) {
            return true;
        }
        return target.npcName != null && target.npcName.equalsIgnoreCase("Reldo");
    }

    private void markTalked(String clueText, BeginnerClueReference.NpcTarget target) {
        if (target == null || target.talkOnce || BeginnerClueReference.looksLikeAnagram(clueText)) {
            talkCompletedKey = clueKey(clueText);
            lastTalkMs = System.currentTimeMillis();
        }
    }

    private static String clueKey(String clueText) {
        String n = ClueScrollHelper.normalize(clueText);
        return n.isEmpty() ? "_" : n;
    }

    private static INPC findNpc(String name, WorldPoint near) {
        if (name == null || name.isBlank()) {
            return null;
        }
        INPC npc = near != null ? NPCs.getNearest(near, name) : NPCs.getNearest(name);
        if (npc != null) {
            return npc;
        }
        String shortName = name;
        int space = name.indexOf(' ');
        if (space > 2) {
            shortName = name.substring(0, space);
            npc = near != null ? NPCs.getNearest(near, shortName) : NPCs.getNearest(shortName);
        }
        return npc;
    }

    private static boolean talk(INPC npc) {
        return ClueNpcClick.talkToInvoke(npc);
    }
}
