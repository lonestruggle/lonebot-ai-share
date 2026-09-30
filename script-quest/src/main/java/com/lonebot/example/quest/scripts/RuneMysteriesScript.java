package com.lonebot.example.quest.scripts;

import com.lonebot.example.quest.QuestBotScript;
import com.lonebot.example.quest.QuestBotTarget;
import com.lonebot.example.quest.QuestLog;
import com.lonebot.example.quest.helpers.QuestActions;
import com.lonebot.example.quest.helpers.QuestCutsceneHelper;
import com.lonebot.example.quest.helpers.QuestHelperDialogSteps;
import com.lonebot.example.quest.helpers.QuestNpcApproachHelper;
import com.lonebot.example.quest.helpers.WizardsTowerBasementHelper;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.LumbridgeStairsHelper;
import net.storm.sdk.quests.Quests;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Dialog;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rune Mysteries — CombatBot {@code RuneMysteriesScript} port (volledig).
 * <p>
 * Route: Duke Horacio (Lumbridge kasteel F1) → Archmage Sedridor (Wizards' Tower kelder)
 * → Aubury (Varrock) → Sedridor. Stappen/dialoog uit Quest Helper
 * {@code helpers/quests/runemysteries/RuneMysteries.java} + CombatBot quest-captures.
 * <p>
 * Stap-bepaling is <b>items + varp 63</b> ({@link #effectiveStep()}): notes → 5, package → 3/4,
 * talisman → 1. Zo blijft de bot correct als de varp achterloopt of de speler mid-quest instapt.
 * <p>
 * Geen gather/bank/GE: alle quest-items komen uit de quest zelf. Reizen = {@code Movement.walkTo}
 * via {@link QuestActions} (walker/SDK regelt pad + F2P-teleports), nooit eigen pad-logica.
 */
public final class RuneMysteriesScript implements QuestBotScript {

    /** {@code VarPlayerID.RUNE_MYSTERIES} */
    public static final int VARP = 63;
    private static final int VARP_QUEST_COMPLETE = 6;

    private static final String AIR_TALISMAN = "Air talisman";
    private static final String RESEARCH_PACKAGE = "Research package";
    private static final String RESEARCH_NOTES = "Research notes";

    /** Duke F1 — capture rune-mis2: outer @ 3207,3227; inner @ 3208,3222. */
    private static final WorldPoint DUKE_DOOR_OUTER = new WorldPoint(3207, 3227, 1);
    private static final WorldPoint DUKE_DOOR_INNER = new WorldPoint(3208, 3222, 1);
    private static final WorldPoint DUKE_STAND = new WorldPoint(3210, 3224, 1);

    private static final WorldPoint AUBURY = new WorldPoint(3253, 3401, 0);

    private static final long NPC_TALK_COOLDOWN_MS = 1_500L;
    private static final long DIALOG_MIN_MS = 120L;
    private static final long DOOR_COOLDOWN_MS = 850L;
    private static final long CHAT_POLL_MS = 1_200L;
    private static final int OPTION_STALL_TICKS = 4;

    private static final QuestHelperDialogSteps DIALOG_DUKE = QuestHelperDialogSteps.of(
            "Have you any quests for me?",
            "Yes."
    );

    private static final QuestHelperDialogSteps DIALOG_SEDRIDOR_TALISMAN = QuestHelperDialogSteps.of(
            "I'm looking for the head wizard.",
            "Okay, here you are."
    );

    private static final QuestHelperDialogSteps DIALOG_SEDRIDOR_PACKAGE = QuestHelperDialogSteps.of(
            "Go ahead.",
            "Yes, certainly."
    );

    private static final QuestHelperDialogSteps DIALOG_AUBURY_PACKAGE = QuestHelperDialogSteps.of(
            "I've been sent here with a package for you."
    );

    /** Tea-prompt ná de notes (CombatBot: tea mag, daarna "No, thank you."). */
    private static final QuestHelperDialogSteps DIALOG_AUBURY_TEA = QuestHelperDialogSteps.of(
            "I'd love a cup of tea.",
            "No, thank you."
    );

    /** Laatste Sedridor-gesprek: alleen continue (geen keuzes in QH). */
    private static final QuestHelperDialogSteps DIALOG_SEDRIDOR_FINAL = QuestHelperDialogSteps.of();

    private final QuestNpcApproachHelper dukeApproach = QuestNpcApproachHelper.dukeHoracio();
    private final QuestNpcApproachHelper sedridorApproach = QuestNpcApproachHelper.sedridor();
    private final QuestNpcApproachHelper auburyApproach = QuestNpcApproachHelper.aubury();

    private int lastSeenVarp = -1;
    private boolean questCompleteSignal;
    private boolean talismanHandedIn;
    private boolean packageTaken;
    private boolean notesFromAubury;
    private boolean auburyTeaHandled;

    private int optionStallTicks;

    private long lastNpcMs;
    private long lastDialogMs;
    private long lastDoorMs;
    private long lastChatPollMs;

    @Override
    public String displayName() {
        return "Rune Mysteries";
    }

    @Override
    public QuestBotTarget target() {
        return QuestBotTarget.RUNE_MYSTERIES;
    }

    @Override
    public Quest runeliteQuest() {
        return Quest.RUNE_MYSTERIES;
    }

    @Override
    public void reset() {
        lastSeenVarp = -1;
        questCompleteSignal = false;
        talismanHandedIn = false;
        packageTaken = false;
        notesFromAubury = false;
        auburyTeaHandled = false;
        optionStallTicks = 0;
        lastNpcMs = 0L;
        lastDialogMs = 0L;
        lastDoorMs = 0L;
        lastChatPollMs = 0L;
        dukeApproach.reset();
        sedridorApproach.reset();
        auburyApproach.reset();
    }

    @Override
    public boolean isFinished() {
        return questCompleteSignal
                || readVarp() >= VARP_QUEST_COMPLETE
                || Quests.isFinished(Quest.RUNE_MYSTERIES);
    }

    @Override
    public int loop() {
        int varp = readVarp();
        onVarpChanged(varp);
        pollChatComplete();

        if (isFinished()) {
            QuestLog.step("Rune", "voltooid (varp=" + varp + ")");
            return rand(1200, 1800);
        }

        int step = effectiveStep();

        Integer dialogDelay = handleDialog(step);
        if (dialogDelay != null) {
            return dialogDelay;
        }

        if (QuestCutsceneHelper.inCutscene()) {
            QuestLog.step("Rune", "cutscene — geen walk, wacht op dialoog");
            return rand(300, 500);
        }

        QuestLog.step("Rune", "stap " + step + " · " + progressLabel());

        switch (step) {
            case 0:
                return talkDuke();
            case 1:
                return talkSedridor(DIALOG_SEDRIDOR_TALISMAN);
            case 2:
            case 3:
                if (hasResearchPackage()) {
                    return travelAndTalkAubury(DIALOG_AUBURY_PACKAGE);
                }
                return talkSedridor(DIALOG_SEDRIDOR_PACKAGE);
            case 4:
                return travelAndTalkAubury(DIALOG_AUBURY_PACKAGE);
            case 5:
                if (hasResearchNotes()) {
                    return talkSedridor(DIALOG_SEDRIDOR_FINAL);
                }
                return travelAndTalkAubury(DIALOG_AUBURY_PACKAGE);
            default:
                return rand(600, 900);
        }
    }

    // ------------------------------------------------------------------ stap

    /**
     * CombatBot {@code effectiveStep}: items gaan vóór de varp — notes → 5, package → 3/4,
     * talisman → 1. Voorkomt dat een achterlopende varp ons terugstuurt naar een gedane stap.
     */
    private int effectiveStep() {
        if (hasResearchNotes()) {
            return 5;
        }
        if (notesFromAubury || lastSeenVarp >= 5) {
            return 5;
        }
        if (hasResearchPackage()) {
            return lastSeenVarp >= 4 ? 4 : 3;
        }
        if (packageTaken || lastSeenVarp >= 2) {
            return Math.max(2, lastSeenVarp);
        }
        if (hasAirTalisman()) {
            return 1;
        }
        if (talismanHandedIn || lastSeenVarp >= 1) {
            return Math.max(2, lastSeenVarp);
        }
        return Math.max(0, lastSeenVarp);
    }

    private String progressLabel() {
        switch (effectiveStep()) {
            case 0:
                return "Duke Horacio";
            case 1:
                return "Sedridor (talisman)";
            case 2:
            case 3:
                return hasResearchPackage() ? "Aubury (pakket)" : "Sedridor (pakket)";
            case 4:
                return "Aubury (notes)";
            case 5:
                return "Sedridor (notes)";
            default:
                return "varp=" + lastSeenVarp;
        }
    }

    // ------------------------------------------------------------------ Duke

    private int talkDuke() {
        WorldPoint me = QuestActions.local();
        if (me == null) {
            return rand(600, 900);
        }
        if (me.getPlane() < 1) {
            return climbToDukeFloor(me);
        }
        int room = approachDukeRoom(me);
        if (room > 0) {
            return room;
        }
        return walkOrTalkNpc(dukeApproach, "Duke Horacio");
    }

    /** Zuid-trap Lumbridge (nooit noord) — SDK {@link LumbridgeStairsHelper}. */
    private int climbToDukeFloor(WorldPoint me) {
        if (LumbridgeStairsHelper.canClimbNow(me) && tryClimb(me, false)) {
            return rand(1000, 1500);
        }
        QuestLog.step("Rune", "→ zuid-trap Lumbridge kasteel");
        QuestActions.walkTo(LumbridgeStairsHelper.pathApproach(), 2);
        return rand(500, 800);
    }

    /** F1: outer + inner deur (captures) → Duke talk-zone. */
    private int approachDukeRoom(WorldPoint me) {
        if (me.getPlane() != 1) {
            return rand(500, 700);
        }
        if (QuestNpcApproachHelper.inDukeHoracioTalkZone(me)) {
            return 0;
        }
        if (needsQuestDoorOpen(me, DUKE_DOOR_OUTER)) {
            if (tryOpenQuestDoor(me, DUKE_DOOR_OUTER)) {
                return rand(900, 1300);
            }
            QuestActions.walkTo(DUKE_DOOR_OUTER, 1);
            return rand(400, 700);
        }
        if (needsQuestDoorOpen(me, DUKE_DOOR_INNER)) {
            if (tryOpenQuestDoor(me, DUKE_DOOR_INNER)) {
                return rand(900, 1300);
            }
            QuestActions.walkTo(DUKE_DOOR_INNER, 1);
            return rand(400, 700);
        }
        if (me.distanceTo(DUKE_STAND) > 2) {
            QuestLog.step("Rune", "→ Duke-kamer " + DUKE_STAND.getX() + "," + DUKE_STAND.getY());
            QuestActions.walkTo(DUKE_STAND, 2);
            return rand(400, 700);
        }
        return rand(350, 550);
    }

    private static boolean needsQuestDoorOpen(WorldPoint me, WorldPoint doorTile) {
        ITileObject door = findQuestDoor(doorTile);
        if (door == null) {
            return false;
        }
        if (door.hasAction("Close")) {
            return false;
        }
        if (!door.hasAction("Open")) {
            return false;
        }
        return me.distanceTo(doorTile) <= 10;
    }

    private boolean tryOpenQuestDoor(WorldPoint me, WorldPoint doorTile) {
        long now = System.currentTimeMillis();
        if (now - lastDoorMs < DOOR_COOLDOWN_MS) {
            return false;
        }
        ITileObject door = findQuestDoor(doorTile);
        if (door == null || !door.hasAction("Open")) {
            return false;
        }
        if (me.distanceTo(doorTile) > 2) {
            QuestActions.walkTo(doorTile, 1);
            return false;
        }
        if (!door.interact("Open")) {
            return false;
        }
        lastDoorMs = now;
        QuestLog.step("Rune", "Duke deur Open @ " + doorTile.getX() + "," + doorTile.getY());
        return true;
    }

    private static ITileObject findQuestDoor(WorldPoint doorTile) {
        try {
            return TileObjects.getNearest(obj -> {
                if (obj == null || obj.getName() == null || obj.getWorldLocation() == null) {
                    return false;
                }
                WorldPoint loc = obj.getWorldLocation();
                if (loc.getPlane() != doorTile.getPlane() || loc.distanceTo(doorTile) > 2) {
                    return false;
                }
                return obj.getName().toLowerCase(Locale.ROOT).contains("door");
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------- Sedridor

    private int talkSedridor(QuestHelperDialogSteps dialog) {
        WorldPoint me = QuestActions.local();
        if (me == null) {
            return rand(600, 900);
        }
        if (!WizardsTowerBasementHelper.inBasement()) {
            int down = descendFromCastleIfNeeded(me);
            if (down > 0) {
                return down;
            }
            QuestLog.step("Rune", "naar Sedridor (Wizards' Tower kelder)");
            if (WizardsTowerBasementHelper.ensureBasement()) {
                return rand(600, 900);
            }
        }
        return walkOrTalkNpc(sedridorApproach, "Archmage Sedridor", dialog);
    }

    /**
     * Duke staat op F1: vóór reizen eerst de zuid-trap omlaag, anders blijft de walker
     * op de verkeerde plane hangen.
     */
    private int descendFromCastleIfNeeded(WorldPoint me) {
        if (me.getPlane() < 1 || !LumbridgeStairsHelper.isInLumbridgeCastleArea(me)) {
            return 0;
        }
        if (tryClimb(me, true)) {
            return rand(1000, 1500);
        }
        QuestActions.walkTo(LumbridgeStairsHelper.walkAnchor(me.getPlane()), 2);
        return rand(500, 800);
    }

    private boolean tryClimb(WorldPoint me, boolean down) {
        ITileObject stairs = LumbridgeStairsHelper.pickSouthStairs(me, down);
        if (stairs == null) {
            return false;
        }
        String action = down ? "Climb-down" : "Climb-up";
        if (stairs.interact(action)
                || stairs.interact("Climb")
                || stairs.interact(down ? "Bottom-floor" : "Top-floor")) {
            QuestLog.step("Rune", action + " zuid-trap Lumbridge");
            Sleep.sleep(220, 380);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Aubury

    private int travelAndTalkAubury(QuestHelperDialogSteps dialog) {
        WorldPoint me = QuestActions.local();
        if (me == null) {
            return rand(600, 900);
        }
        if (WizardsTowerBasementHelper.inBasement()) {
            QuestLog.step("Rune", "kelder uit → Aubury");
            if (WizardsTowerBasementHelper.leaveBasement()) {
                return rand(1000, 1500);
            }
            QuestActions.walkTo(WizardsTowerBasementHelper.SEDRIDOR, 6);
            return rand(500, 800);
        }
        int down = descendFromCastleIfNeeded(me);
        if (down > 0) {
            return down;
        }
        if (!nearAubury(me)) {
            QuestLog.step("Rune", "naar Aubury (Varrock) " + AUBURY.getX() + "," + AUBURY.getY());
            QuestActions.walkTo(AUBURY, 6);
            return rand(500, 800);
        }
        return walkOrTalkNpc(auburyApproach, "Aubury", dialog);
    }

    private static boolean nearAubury(WorldPoint me) {
        return me != null && me.getPlane() == 0 && me.distanceTo(AUBURY) <= 18;
    }

    // ------------------------------------------------------------- walk/talk

    private int walkOrTalkNpc(QuestNpcApproachHelper approach, String label) {
        return walkOrTalkNpc(approach, label, null);
    }

    /**
     * Zone/deur-approach → Talk-to. Failsafe: dezelfde NPC opnieuw benaderen (nooit een
     * andere route/quest) als de NPC niet bereikbaar is of de klik niet pakt.
     */
    private int walkOrTalkNpc(QuestNpcApproachHelper approach, String label,
                              QuestHelperDialogSteps dialog) {
        int travel = approach.approachBeforeTalk();
        if (travel > 0) {
            QuestLog.step("Rune", "→ " + label);
            return travel;
        }
        WorldPoint me = QuestActions.local();
        INPC npc = approach.findNpc();
        if (npc == null) {
            npc = findNpcByLabel(label);
        }
        if (npc == null || me == null || !approach.canTalkNow(me, npc)) {
            int retry = approach.approachBeforeTalk();
            if (retry > 0) {
                return retry;
            }
            QuestLog.step("Rune", label + " nog niet bereikbaar — opnieuw benaderen");
            return rand(700, 1100);
        }
        long now = System.currentTimeMillis();
        if (now - lastNpcMs < NPC_TALK_COOLDOWN_MS) {
            return rand(300, 500);
        }
        if (npc.interact("Talk-to") || npc.interact("Talk")) {
            lastNpcMs = now;
            QuestLog.step("Rune", "Talk-to " + label
                    + (dialog != null && !dialog.isEmpty() ? " · dialoog klaar" : ""));
            Sleep.sleep(220, 380);
            return rand(900, 1400);
        }
        QuestLog.step("Rune", "Talk-to " + label + " faalde — retry zelfde NPC");
        return rand(600, 1000);
    }

    private static INPC findNpcByLabel(String label) {
        if (label == null) {
            return null;
        }
        final String low = label.toLowerCase(Locale.ROOT);
        try {
            return NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().toLowerCase(Locale.ROOT).contains(low));
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ---------------------------------------------------------------- dialog

    /**
     * @return delay wanneer dialoog/cutscene het tick opvult, anders {@code null}
     */
    private Integer handleDialog(int step) {
        boolean cutscene = QuestCutsceneHelper.inCutscene();
        boolean dialog = Dialog.isOpen();
        if (!dialog && !cutscene) {
            optionStallTicks = 0;
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - lastDialogMs < DIALOG_MIN_MS) {
            return (int) DIALOG_MIN_MS;
        }
        lastDialogMs = now;

        if (Dialog.isViewingOptions()) {
            String chosen = chooseStep(dialogForStep(step));
            if (chosen == null && isAuburyDialogStep(step)) {
                // Tea-prompt kan vóór/na de notes komen — altijd afhandelen.
                chosen = chooseStep(DIALOG_AUBURY_TEA);
            }
            if (chosen != null) {
                optionStallTicks = 0;
                QuestLog.step("Rune", "dialoog: \"" + chosen + "\"");
                trackDialogProgress(step, chosen);
                return rand(250, 450);
            }
            optionStallTicks++;
            // Lineaire quest: liever eerste optie dan vastzitten op een onbekende regel.
            if (optionStallTicks >= OPTION_STALL_TICKS) {
                optionStallTicks = 0;
                QuestLog.force("Rune", "geen exacte dialoog-optie → eerste optie (failsafe)");
                Dialog.chooseOption(0);
                return rand(250, 450);
            }
            return rand(200, 350);
        }

        optionStallTicks = 0;
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            QuestLog.step("Rune", "dialoog continue");
            return rand(150, 280);
        }
        return cutscene ? rand(300, 500) : rand(200, 400);
    }

    private static QuestHelperDialogSteps dialogForStep(int step) {
        switch (step) {
            case 0:
                return DIALOG_DUKE;
            case 1:
                return DIALOG_SEDRIDOR_TALISMAN;
            case 2:
            case 3:
                return hasResearchPackage() ? DIALOG_AUBURY_PACKAGE : DIALOG_SEDRIDOR_PACKAGE;
            case 4:
            case 5:
                if (hasResearchNotes()) {
                    return DIALOG_AUBURY_TEA;
                }
                return DIALOG_AUBURY_PACKAGE;
            default:
                return null;
        }
    }

    /**
     * Kiest de eerste geconfigureerde regel die in het optie-menu staat en geeft die terug
     * (nodig voor de CombatBot-voortgangsvlaggen). Nooit een wildcard {@code Yes}.
     */
    private static String chooseStep(QuestHelperDialogSteps steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        for (String want : steps.steps()) {
            final String w = strip(want).toLowerCase(Locale.ROOT);
            if (w.isEmpty()) {
                continue;
            }
            try {
                if (!Dialog.hasOption(t -> optionMatches(t, w))) {
                    continue;
                }
                if (Dialog.chooseOption(t -> optionMatches(t, w))) {
                    return want;
                }
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean optionMatches(String optionText, String wantLower) {
        if (optionText == null) {
            return false;
        }
        String o = strip(optionText).toLowerCase(Locale.ROOT);
        if (o.equals(wantLower)) {
            return true;
        }
        if (isBareYes(wantLower)) {
            return isBareYes(o);
        }
        return o.contains(wantLower);
    }

    private static boolean isBareYes(String s) {
        String t = s.replace(".", "").replace("!", "").trim();
        return "yes".equals(t) || "yes please".equals(t);
    }

    private static String strip(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\u00A0', ' ').replaceAll("<[^>]+>", "").trim();
    }

    private static boolean isAuburyDialogStep(int step) {
        return step >= 2 && step <= 5;
    }

    /** CombatBot {@code trackDialogProgress} — vlaggen los van de varp. */
    private void trackDialogProgress(int step, String chosen) {
        if (step == 1 && "Okay, here you are.".equals(chosen)) {
            talismanHandedIn = true;
            QuestLog.force("Rune", "Air talisman afgegeven aan Sedridor");
        }
        if ((step == 2 || step == 3) && "Yes, certainly.".equals(chosen)) {
            packageTaken = true;
            QuestLog.force("Rune", "Research package aangenomen");
        }
        if ("I'd love a cup of tea.".equals(chosen)) {
            auburyTeaHandled = true;
        }
        if ("No, thank you.".equals(chosen)) {
            auburyTeaHandled = true;
            if (step >= 4) {
                notesFromAubury = true;
                QuestLog.force("Rune", "Research notes van Aubury");
            }
        }
    }

    // ------------------------------------------------------------------ chat

    /** Chat-hook (CombatBot {@code onChatMessage}) — mag ook extern aangeroepen worden. */
    public void onChatMessage(String message) {
        if (message == null) {
            return;
        }
        String low = strip(message).toLowerCase(Locale.ROOT);
        if (low.isEmpty()) {
            return;
        }
        if (low.contains("congratulations, you've completed a quest")
                || low.contains("congratulations! quest complete")
                || low.contains("you have completed rune mysteries")
                || (low.contains("rune mysteries") && low.contains("complete"))) {
            if (!questCompleteSignal) {
                questCompleteSignal = true;
                QuestLog.force("Rune", "voltooid (chat)");
            }
            return;
        }
        if (low.contains("research notes")) {
            notesFromAubury = true;
        } else if (low.contains("research package")) {
            packageTaken = true;
        }
    }

    /** Geen ChatMessage-event in de script-laag → chatbuffer pollen (~1.2 s). */
    private void pollChatComplete() {
        if (questCompleteSignal) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastChatPollMs < CHAT_POLL_MS) {
            return;
        }
        lastChatPollMs = now;
        List<String> lines;
        try {
            lines = Chat.getRecentMessages(12);
        } catch (Throwable ignored) {
            return;
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (String line : lines) {
            onChatMessage(line);
        }
    }

    // ------------------------------------------------------------------ varp

    private void onVarpChanged(int varp) {
        if (varp == lastSeenVarp) {
            return;
        }
        if (lastSeenVarp >= 0) {
            QuestLog.force("Rune", "varp " + lastSeenVarp + " → " + varp + " · " + progressLabel());
        }
        lastSeenVarp = varp;
        if (varp >= 2) {
            talismanHandedIn = true;
        }
        if (varp >= 3) {
            packageTaken = true;
        }
        if (varp >= 5) {
            notesFromAubury = true;
        }
    }

    private static int readVarp() {
        try {
            return Vars.getVarp(VARP);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // ----------------------------------------------------------------- items

    private static boolean hasAirTalisman() {
        return inventoryContains(AIR_TALISMAN);
    }

    private static boolean hasResearchPackage() {
        return inventoryContains(RESEARCH_PACKAGE);
    }

    private static boolean hasResearchNotes() {
        return inventoryContains(RESEARCH_NOTES);
    }

    /** Client-thread read kan timeouten → {@code false} is hier "onbekend", geen lege inventory. */
    private static boolean inventoryContains(String name) {
        try {
            return Inventory.contains(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int rand(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(max - min);
    }
}
