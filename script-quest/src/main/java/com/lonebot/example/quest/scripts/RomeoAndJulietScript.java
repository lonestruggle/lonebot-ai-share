package com.lonebot.example.quest.scripts;

import com.lonebot.example.quest.QuestBotScript;
import com.lonebot.example.quest.QuestBotTarget;
import com.lonebot.example.quest.QuestLog;
import com.lonebot.example.quest.helpers.QuestActions;
import com.lonebot.example.quest.helpers.QuestBankGePrep;
import com.lonebot.example.quest.helpers.QuestCompletionHelper;
import com.lonebot.example.quest.helpers.QuestCutsceneHelper;
import com.lonebot.example.quest.helpers.QuestDialogHelper;
import com.lonebot.example.quest.helpers.QuestGePrices;
import com.lonebot.example.quest.helpers.QuestHelperDialogSteps;
import com.lonebot.example.quest.helpers.QuestImpLootHints;
import com.lonebot.example.quest.helpers.QuestJulietHouseHelper;
import com.lonebot.example.quest.helpers.QuestNpcApproachHelper;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankWithdrawHelper;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.quests.Quests;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Dialog;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Romeo &amp; Juliet — full CombatBot {@code RomeoAndJulietScript} port (VarPlayer 144).
 * <p>
 * 0 Romeo start → 10 Juliet balcony → 20 letter to Romeo → 30 Father Lawrence →
 * 40 cadava berries + Apothecary → 50 potion to Juliet → 60 finish @ Romeo.
 * <p>
 * Cadava from step 10 via {@link QuestBankGePrep} + {@link QuestImpLootHints} (bank before GE);
 * bush failsafe after 45 s stall. Travel = walker ({@code Movement.walkTo}); no CombatBot
 * Varrock-tp kit. Juliet house via {@link QuestJulietHouseHelper}.
 */
public final class RomeoAndJulietScript implements QuestBotScript {

    public static final int VARP = 144;
    public static final int DONE = 100;

    private static final String CADAVA_BERRIES = "Cadava berries";
    private static final String CADAVA_POTION = "Cadava potion";

    /** Cadava bush SE Varrock — free failsafe when bank/GE fails. */
    private static final WorldPoint CADAVA_BUSH = new WorldPoint(3268, 3370, 0);

    private static final QuestBankGePrep.Need[] BERRIES = {
            new QuestBankGePrep.Need(CADAVA_BERRIES, 1, QuestGePrices.CADAVA_BERRIES)
    };

    private static final QuestHelperDialogSteps ROMEO_START = QuestHelperDialogSteps.of(
            "Yes, I have seen her actually!",
            "Yes, ok, I'll let her know.",
            "Yes."
    );
    private static final QuestHelperDialogSteps JULIET_D = QuestHelperDialogSteps.of("Ok, thanks.");
    private static final QuestHelperDialogSteps LAWRENCE_D = QuestHelperDialogSteps.of("Ok, thanks.");
    private static final QuestHelperDialogSteps APOTHECARY_D = QuestHelperDialogSteps.of(
            "Talk about something else.",
            "Talk about Romeo & Juliet."
    );
    private static final QuestHelperDialogSteps CONTINUE_ONLY = QuestHelperDialogSteps.of();

    private static final long STEP_STALL_MS = 30_000L;
    private static final long SUPPLY_STALL_MS = 45_000L;
    private static final long TALK_COOLDOWN_MS = 550L;
    private static final long CHAT_POLL_MS = 1_200L;
    private static final long DIALOG_MIN_MS = 120L;

    private final QuestNpcApproachHelper romeo = QuestNpcApproachHelper.romeo();
    private final QuestNpcApproachHelper lawrence = QuestNpcApproachHelper.fatherLawrence();
    private final QuestNpcApproachHelper apothecary = QuestNpcApproachHelper.apothecary();

    /** Romeo start dialog finished while varp still 0 (client lag). */
    private boolean romeoStarted;
    /** Bank/GE gave up → pick from Cadava bush. */
    private boolean cadavaViaBush;
    private boolean questCompleteSignal;

    private String stepSig = "";
    private long stepSinceMs;
    private String supplySig = "";
    private long supplySinceMs;
    private long lastTalkMs;
    private long lastDialogMs;
    private long lastChatPollMs;
    private int lastSeenVarp = -1;

    @Override
    public String displayName() {
        return "Romeo & Juliet";
    }

    @Override
    public QuestBotTarget target() {
        return QuestBotTarget.ROMEO_AND_JULIET;
    }

    @Override
    public Quest runeliteQuest() {
        return Quest.ROMEO__JULIET;
    }

    @Override
    public void reset() {
        romeoStarted = false;
        cadavaViaBush = false;
        questCompleteSignal = false;
        stepSig = "";
        stepSinceMs = 0L;
        supplySig = "";
        supplySinceMs = 0L;
        lastTalkMs = 0L;
        lastDialogMs = 0L;
        lastChatPollMs = 0L;
        lastSeenVarp = -1;
        romeo.reset();
        lawrence.reset();
        apothecary.reset();
        QuestBankGePrep.reset();
    }

    @Override
    public boolean isFinished() {
        return questCompleteSignal
                || Quests.isFinished(Quest.ROMEO__JULIET)
                || varp() >= DONE;
    }

    public void onChatMessage(String message) {
        if (message == null) {
            return;
        }
        String low = message.toLowerCase(Locale.ROOT);
        if (low.contains("congratulations") && low.contains("quest")
                || low.contains("completed the romeo")
                || low.contains("you have completed a quest")) {
            questCompleteSignal = true;
            QuestLog.force("Romeo", "voltooid (chat)");
        }
    }

    @Override
    public int loop() {
        if (QuestCompletionHelper.isOpen()) {
            QuestCompletionHelper.dismiss();
            return rand(600, 900);
        }

        pollCompletionChat();

        int varp = varp();
        onVarpChanged(varp);

        // 1) Dialog / cutscene first — never walk mid-conversation.
        if (Dialog.isOpen() || QuestCutsceneHelper.inCutscene()) {
            return handleDialog(varp);
        }

        if (isFinished()) {
            QuestLog.step("Romeo", "klaar (varp=" + varp + ")");
            if (Bank.isOpen()) {
                Bank.leaveOpenForWalk();
            }
            return 1200;
        }

        if (Bank.isOpen()) {
            int block = BankWithdrawHelper.resolveBlockingBankInput();
            if (block > 0) {
                Sleep.sleep(Math.min(block, 600));
                return rand(300, 500);
            }
        }

        int step = effectiveStep(varp);
        watchStepStall(step);

        // 2) Cadava from step 10 (CombatBot prep): bank before GE, Imp-JSON aware.
        if (needsCadava(step)) {
            Integer supply = supplyCadava(step);
            if (supply != null) {
                return supply;
            }
        }

        if (step >= 60) {
            return talkRomeo("afronden", CONTINUE_ONLY);
        }
        if (step >= 50) {
            return potionToJuliet(step);
        }
        if (step >= 40) {
            if (hasPotion()) {
                return potionToJuliet(step);
            }
            return toApothecary();
        }
        if (step >= 30) {
            QuestLog.step("Romeo", "Father Lawrence (Varrock kerk)");
            return talkVia(lawrence, LAWRENCE_D, "Father Lawrence");
        }
        if (step >= 20) {
            return talkRomeo("brief", CONTINUE_ONLY);
        }
        if (step >= 10) {
            return talkJuliet();
        }
        QuestLog.step("Romeo", "start bij Romeo (Varrock Square)");
        return talkVia(romeo, ROMEO_START, "Romeo");
    }

    /**
     * Varp 144 lags after Romeo start dialog — with closed dialog + {@link #romeoStarted}
     * treat as step 10 (CombatBot {@code effectiveQuestStep}).
     */
    private int effectiveStep(int varp) {
        if (varp <= 0 && romeoStarted && !Dialog.isOpen()) {
            return 10;
        }
        return varp;
    }

    private void onVarpChanged(int varp) {
        if (varp == lastSeenVarp) {
            return;
        }
        if (lastSeenVarp >= 0) {
            QuestLog.force("Romeo", "varp " + lastSeenVarp + " → " + varp);
        }
        if (varp >= 10) {
            // Start dialog tracker no longer needed.
        }
        if (lastSeenVarp == 10 && varp >= 20) {
            QuestLog.force("Romeo", "Juliet klaar → loop naar Romeo (brief)");
        }
        lastSeenVarp = varp;
        stepSinceMs = System.currentTimeMillis();
    }

    private int handleDialog(int varp) {
        long now = System.currentTimeMillis();
        if (now - lastDialogMs < DIALOG_MIN_MS) {
            return 100;
        }
        lastDialogMs = now;

        if (QuestCutsceneHelper.inCutscene() && !Dialog.isOpen()) {
            if (Dialog.canContinue()) {
                Dialog.continueSpace();
            }
            QuestLog.step("Romeo", "cutscene — spatie / wacht");
            return rand(300, 500);
        }

        boolean startYes = isQuestStartYesVisible(varp);
        QuestHelperDialogSteps steps = dialogFor(effectiveStep(varp));
        if (QuestDialogHelper.handle(steps)) {
            if (startYes && !romeoStarted) {
                romeoStarted = true;
                QuestLog.force("Romeo", "start-dialoog afgerond (varp nog " + varp + ")");
            }
            return rand(350, 550);
        }
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            return rand(200, 350);
        }
        return rand(400, 600);
    }

    private static QuestHelperDialogSteps dialogFor(int step) {
        if (step <= 0) {
            return ROMEO_START;
        }
        if (step == 10 || step == 50) {
            return JULIET_D;
        }
        if (step == 30) {
            return LAWRENCE_D;
        }
        if (step == 40) {
            return APOTHECARY_D;
        }
        return CONTINUE_ONLY;
    }

    private static boolean isQuestStartYesVisible(int varp) {
        if (varp > 0 || !Dialog.isViewingOptions()) {
            return false;
        }
        if (hasOption("i have seen her") || hasOption("let her know")) {
            return false;
        }
        return Dialog.hasOption(t -> {
            if (t == null) {
                return false;
            }
            String s = t.replaceAll("<[^>]+>", "").replace(".", "").replace("!", "").trim();
            return "yes".equalsIgnoreCase(s);
        });
    }

    private static boolean hasOption(String needle) {
        return Dialog.hasOption(t -> t != null
                && t.toLowerCase(Locale.ROOT).contains(needle));
    }

    private int talkRomeo(String why, QuestHelperDialogSteps dialog) {
        if (QuestJulietHouseHelper.inJulietRoom()) {
            QuestLog.step("Romeo", why + ": eerst omlaag uit Juliets kamer");
            QuestJulietHouseHelper.leaveToGround();
            return rand(600, 900);
        }
        QuestLog.step("Romeo", why + " → Romeo");
        return talkVia(romeo, dialog, "Romeo");
    }

    private int talkJuliet() {
        if (QuestJulietHouseHelper.goToJuliet()) {
            return rand(600, 900);
        }
        if (System.currentTimeMillis() - lastTalkMs < TALK_COOLDOWN_MS) {
            return rand(300, 450);
        }
        INPC juliet = findJuliet();
        WorldPoint me = QuestActions.local();
        if (juliet != null && me != null && QuestJulietHouseHelper.inJulietRoom()
                && me.distanceTo(juliet.getWorldLocation()) <= 4) {
            if (juliet.interact("Talk-to") || juliet.interact("Talk")) {
                lastTalkMs = System.currentTimeMillis();
                QuestLog.step("Romeo", "Talk-to Juliet (balkon)");
                Sleep.sleep(220, 380);
                return rand(600, 900);
            }
        }
        QuestLog.step("Romeo", "Talk-to Juliet (balkon)");
        lastTalkMs = System.currentTimeMillis();
        QuestNpcApproachHelper.approachAndTalk("Juliet", QuestJulietHouseHelper.JULIET, JULIET_D);
        return rand(600, 900);
    }

    private int potionToJuliet(int step) {
        if (!hasPotion()) {
            QuestLog.step("Romeo", "geen cadava potion → Apothecary (stap " + step + ")");
            return toApothecary();
        }
        if (QuestJulietHouseHelper.goToJuliet()) {
            return rand(600, 900);
        }
        if (System.currentTimeMillis() - lastTalkMs < TALK_COOLDOWN_MS) {
            return rand(300, 450);
        }
        QuestLog.step("Romeo", "potion → Juliet");
        lastTalkMs = System.currentTimeMillis();
        INPC juliet = findJuliet();
        if (juliet != null && (juliet.interact("Talk-to") || juliet.interact("Talk"))) {
            Sleep.sleep(220, 380);
            return rand(600, 900);
        }
        QuestNpcApproachHelper.approachAndTalk("Juliet", QuestJulietHouseHelper.JULIET, JULIET_D);
        return rand(600, 900);
    }

    private int toApothecary() {
        if (QuestJulietHouseHelper.inJulietRoom()) {
            QuestLog.step("Romeo", "Apothecary: eerst omlaag uit Juliets kamer");
            QuestJulietHouseHelper.leaveToGround();
            return rand(600, 900);
        }
        if (!hasBerries() && !hasPotion()) {
            Integer supply = supplyCadava(40);
            if (supply != null) {
                return supply;
            }
        }
        QuestLog.step("Romeo", "Apothecary — berries omruilen voor potion");
        return talkVia(apothecary, APOTHECARY_D, "Apothecary");
    }

    private int talkVia(QuestNpcApproachHelper approach, QuestHelperDialogSteps dialog, String label) {
        if (approach.talk(dialog)) {
            return rand(600, 900);
        }
        QuestLog.step("Romeo", label + ": geen klik gelukt → retry");
        return rand(700, 1000);
    }

    private static INPC findJuliet() {
        try {
            return NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().equalsIgnoreCase("Juliet")
                    && n.getWorldLocation() != null
                    && n.getWorldLocation().getPlane() == 1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean needsCadava(int step) {
        if (step < 10 || step >= 50) {
            return false;
        }
        return !hasBerries() && !hasPotion();
    }

    /**
     * Bank before GE via {@link QuestBankGePrep}; Imp cadava flag blocks GE until bank empty.
     * Failsafe: 45 s stall → Cadava bush (same goal, no coins).
     *
     * @return delay while prep runs, or {@code null} when quest step may continue
     */
    private Integer supplyCadava(int step) {
        QuestImpLootHints.Hint hint = QuestImpLootHints.load();
        String sig = "berries=" + QuestActions.unnotedCount(CADAVA_BERRIES)
                + " coins=" + coins() + " impCadava=" + hint.cadava;
        long now = System.currentTimeMillis();
        if (!sig.equals(supplySig)) {
            supplySig = sig;
            supplySinceMs = now;
        } else if (supplySinceMs == 0L) {
            supplySinceMs = now;
        } else if (!cadavaViaBush && now - supplySinceMs > SUPPLY_STALL_MS) {
            supplySinceMs = now;
            cadavaViaBush = true;
            QuestLog.force("Romeo", "45s geen cadava via bank/GE (" + sig
                    + ") → Cadava bush zuidoost Varrock");
        }

        if (cadavaViaBush) {
            return pickCadavaFromBush();
        }

        if (hint.cadava) {
            QuestLog.step("Romeo", "Imp-JSON cadava → bank eerst (stap " + step + ")");
        } else {
            QuestLog.step("Romeo", "cadava berries via bank → GE (stap " + step + ")");
        }
        if (QuestBankGePrep.ensureUnnoted(BERRIES)) {
            return rand(500, 800);
        }
        QuestLog.step("Romeo", "cadava berries op zak");
        cadavaViaBush = false;
        return null;
    }

    private int pickCadavaFromBush() {
        if (hasBerries()) {
            cadavaViaBush = false;
            return rand(350, 550);
        }
        if (QuestActions.interactObject("Cadava bush", "Pick-from", CADAVA_BUSH)) {
            return rand(600, 900);
        }
        if (QuestActions.pickup(CADAVA_BERRIES)) {
            return rand(600, 900);
        }
        QuestLog.step("Romeo", "→ Cadava bush " + CADAVA_BUSH.getX() + "," + CADAVA_BUSH.getY());
        QuestActions.walkTo(CADAVA_BUSH, 2);
        return rand(600, 900);
    }

    private void watchStepStall(int step) {
        String sig = step + "|" + hasBerries() + "|" + hasPotion();
        long now = System.currentTimeMillis();
        if (!sig.equals(stepSig)) {
            stepSig = sig;
            stepSinceMs = now;
            return;
        }
        if (stepSinceMs == 0L) {
            stepSinceMs = now;
            return;
        }
        if (now - stepSinceMs > STEP_STALL_MS) {
            stepSinceMs = now;
            romeo.reset();
            lawrence.reset();
            apothecary.reset();
            WorldPoint me = QuestActions.local();
            QuestLog.force("Romeo", "30s geen voortgang op stap " + step
                    + (me != null ? " (" + me.getX() + "," + me.getY() + ",p" + me.getPlane() + ")" : "")
                    + " → approach reset");
        }
    }

    private void pollCompletionChat() {
        long now = System.currentTimeMillis();
        if (now - lastChatPollMs < CHAT_POLL_MS) {
            return;
        }
        lastChatPollMs = now;
        List<String> lines;
        try {
            lines = Chat.getRecentMessages(8);
        } catch (Throwable ignored) {
            return;
        }
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            onChatMessage(line);
        }
    }

    private static boolean hasBerries() {
        return Inventory.contains(CADAVA_BERRIES) || QuestActions.hasUnnoted(CADAVA_BERRIES);
    }

    private static boolean hasPotion() {
        return Inventory.contains(CADAVA_POTION) || Inventory.contains("Cadava");
    }

    private static int coins() {
        try {
            return Inventory.getCount(true, "Coins");
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int varp() {
        try {
            return Vars.getVarp(VARP);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int rand(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(max - min);
    }
}
