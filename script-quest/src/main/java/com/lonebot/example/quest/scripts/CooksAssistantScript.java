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
import com.lonebot.example.quest.helpers.QuestNpcApproachHelper;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankWithdrawHelper;
import net.storm.sdk.items.GeRestockHelper;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.quests.Quests;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Dialog;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cook's Assistant — full CombatBot {@code CooksAssistantScript} port (VarPlayer 29).
 * <p>
 * Gather unnoted Egg + Pot of flour + Bucket of milk via {@link QuestBankGePrep}
 * (inv → bank → GE), then start + deliver at the Lumbridge Cook via
 * {@link QuestNpcApproachHelper#lumbridgeCook()}. After quest start (varp ≥ 1) never
 * re-gather / bank / GE — only deliver. Chat + inventory-vanish track turn-ins.
 */
public final class CooksAssistantScript implements QuestBotScript {

    /** {@code VarPlayerID.COOKS_ASSISTANT} */
    public static final int VARP = 29;
    private static final int VARP_QUEST_COMPLETE = 2;

    private static final String EGG = "Egg";
    private static final String FLOUR = "Pot of flour";
    private static final String MILK = "Bucket of milk";

    private static final WorldPoint COOK_TILE = new WorldPoint(3206, 3214, 0);

    /** QH {@code finishQuest.addDialogSteps("What's wrong?", "Can I help?", "Yes.")} */
    private static final QuestHelperDialogSteps START_DIALOG = QuestHelperDialogSteps.of(
            "What's wrong?",
            "Can I help?",
            "Yes."
    );

    /** Deliver: Cook asks per item; "Yes." covers confirm. */
    private static final QuestHelperDialogSteps DELIVER_DIALOG = QuestHelperDialogSteps.of(
            "Yes.",
            "I have an egg",
            "I have some flour",
            "I have some milk"
    );

    private static final QuestBankGePrep.Need[] PREP = {
            new QuestBankGePrep.Need(EGG, 1, QuestGePrices.EGG),
            new QuestBankGePrep.Need(FLOUR, 1, QuestGePrices.POT_OF_FLOUR),
            new QuestBankGePrep.Need(MILK, 1, QuestGePrices.BUCKET_OF_MILK)
    };

    private static final int MIN_GE_COINS = 200;
    private static final long NPC_TALK_COOLDOWN_MS = 1_800L;
    private static final long DIALOG_MIN_MS = 120L;
    private static final long CHAT_POLL_MS = 1_200L;

    private enum Phase {
        GATHER,
        BANK_GE,
        WALK_COOK,
        TALK_COOK
    }

    private final QuestNpcApproachHelper cookApproach = QuestNpcApproachHelper.lumbridgeCook();

    private Phase phase = Phase.GATHER;

    private boolean turnedInEgg;
    private boolean turnedInFlour;
    private boolean turnedInMilk;
    private boolean turnedInAll;

    /** Item-vanish fallback: only after quest start + 2× consecutive absent (client-thread flake). */
    private boolean sawEgg;
    private boolean sawFlour;
    private boolean sawMilk;
    private int missEgg;
    private int missFlour;
    private int missMilk;

    private int lastSeenVarp = -1;
    private int prepTicks;
    private int optionStallTicks;

    private long lastNpcMs;
    private long lastDialogMs;
    private long lastChatPollMs;
    private long talkStallSinceMs;

    @Override
    public String displayName() {
        return "Cook's Assistant";
    }

    @Override
    public QuestBotTarget target() {
        return QuestBotTarget.COOKS_ASSISTANT;
    }

    @Override
    public Quest runeliteQuest() {
        return Quest.COOKS_ASSISTANT;
    }

    @Override
    public void reset() {
        phase = Phase.GATHER;
        turnedInEgg = false;
        turnedInFlour = false;
        turnedInMilk = false;
        turnedInAll = false;
        sawEgg = false;
        sawFlour = false;
        sawMilk = false;
        missEgg = 0;
        missFlour = 0;
        missMilk = 0;
        lastSeenVarp = -1;
        prepTicks = 0;
        optionStallTicks = 0;
        lastNpcMs = 0L;
        lastDialogMs = 0L;
        lastChatPollMs = 0L;
        talkStallSinceMs = 0L;
        cookApproach.reset();
        QuestBankGePrep.reset();
    }

    @Override
    public boolean isFinished() {
        return turnedInAll || readVarp() >= VARP_QUEST_COMPLETE || Quests.isFinished(Quest.COOKS_ASSISTANT);
    }

    public void onChatMessage(String message) {
        if (message == null) {
            return;
        }
        String low = strip(message).toLowerCase(Locale.ROOT);
        if (low.isEmpty()) {
            return;
        }
        if (low.contains("here's a bucket of milk")) {
            markTurnIn(MILK);
        } else if (low.contains("here's a pot of flour")) {
            markTurnIn(FLOUR);
        } else if (low.contains("here's a fresh egg")) {
            markTurnIn(EGG);
        } else if (low.contains("you've brought me everything i need")
                || low.contains("congratulations, you've completed a quest")
                || low.contains("you have completed the cook's assistant")) {
            markAllTurnedIn("chat");
        }
    }

    @Override
    public int loop() {
        if (QuestCompletionHelper.isOpen()) {
            QuestCompletionHelper.dismiss();
            return rand(600, 900);
        }

        int varp = readVarp();
        onVarpChanged(varp);
        pollChatTurnIns();
        trackVanishedItems(varp);

        if (isFinished()) {
            QuestLog.step("Cook", "voltooid (varp=" + varp + ")");
            closeBankGe();
            return rand(1200, 1800);
        }

        Integer dialogDelay = handleDialog(varp);
        if (dialogDelay != null) {
            return dialogDelay;
        }

        if (QuestCutsceneHelper.inCutscene()) {
            QuestLog.step("Cook", "cutscene — geen walk, wacht op dialoog");
            return rand(300, 500);
        }

        // Quest started: deliver only. No bank, no GE, no re-gather.
        if (varp >= 1) {
            closeBankGe();
            prepTicks = 0;
            phase = Phase.WALK_COOK;
            QuestLog.step("Cook", "afleveren @ Cook (varp=" + varp + ") · " + progressLabel());
            return walkOrTalkCook(varp);
        }

        // varp == 0: items must be unnoted before start dialog.
        if (!allItemsReady()) {
            phase = Phase.BANK_GE;
            return phaseBankGePrep();
        }

        closeBankGe();
        prepTicks = 0;
        phase = Phase.WALK_COOK;
        QuestLog.step("Cook", "items compleet → quest starten @ Cook · " + progressLabel());
        return walkOrTalkCook(varp);
    }

    private int phaseBankGePrep() {
        if (Bank.isOpen()) {
            int block = BankWithdrawHelper.resolveBlockingBankInput();
            if (block > 0) {
                QuestLog.step("Cook", "Enter-amount blokkeerde bank → opgelost");
                Sleep.sleep(Math.min(block, 600));
                return rand(300, 500);
            }
            maybeDepositJunk();
        }

        prepTicks++;
        if (prepTicks % 25 == 0) {
            QuestLog.force("Cook", "prep loopt lang (" + prepTicks + " ticks) — zelfde doel bank/GE · "
                    + progressLabel() + " · gp=" + coins());
        } else {
            QuestLog.step("Cook", "items halen · bank/GE · " + progressLabel());
        }

        if (missingFromInventory() > 0 && !itemsAvailableInOpenBank() && coins() < MIN_GE_COINS) {
            QuestLog.step("Cook", "weinig gp (" + coins() + ") voor GE — blijf bank/GE proberen");
        }

        if (QuestBankGePrep.ensureUnnoted(PREP)) {
            return rand(500, 800);
        }

        prepTicks = 0;
        phase = Phase.WALK_COOK;
        QuestLog.step("Cook", "prep klaar · " + progressLabel());
        return rand(400, 700);
    }

    private void maybeDepositJunk() {
        if (!Bank.isOpen()) {
            return;
        }
        try {
            if (Inventory.getFreeSlots() >= 3) {
                return;
            }
            QuestLog.step("Cook", "inv vol → stort junk, houd egg/flour/milk/coins");
            Bank.depositAllExcept(EGG, FLOUR, MILK, "Coins");
            Sleep.sleep(220, 400);
        } catch (Throwable ignored) {
        }
    }

    private static boolean itemsAvailableInOpenBank() {
        if (!Bank.isOpen()) {
            return false;
        }
        try {
            return Bank.contains(EGG) || Bank.contains(FLOUR) || Bank.contains(MILK);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int walkOrTalkCook(int varp) {
        long now = System.currentTimeMillis();
        if (talkStallSinceMs == 0L) {
            talkStallSinceMs = now;
        } else if (now - talkStallSinceMs > 45_000L) {
            talkStallSinceMs = now;
            cookApproach.reset();
            QuestLog.force("Cook", "45s geen voortgang bij Cook → approach reset");
        }

        int approach = cookApproach.approachBeforeTalk();
        if (approach > 0) {
            QuestLog.step("Cook", "→ keuken/Cook " + COOK_TILE.getX() + "," + COOK_TILE.getY());
            return approach;
        }

        INPC cook = cookApproach.findNpc();
        WorldPoint me = QuestActions.local();

        if (cook == null || me == null || !cookApproach.canTalkNow(me, cook)) {
            int retry = cookApproach.approachBeforeTalk();
            if (retry > 0) {
                return retry;
            }
            QuestLog.step("Cook", "Cook nog niet bereikbaar — opnieuw benaderen");
            return rand(800, 1200);
        }

        if (now - lastNpcMs < NPC_TALK_COOLDOWN_MS) {
            return rand(400, 700);
        }

        if (cook.interact("Talk-to") || cook.interact("Talk")) {
            lastNpcMs = now;
            talkStallSinceMs = now;
            phase = Phase.TALK_COOK;
            QuestLog.step("Cook", "Talk-to (varp=" + varp + ") @ " + me.getX() + "," + me.getY());
            Sleep.sleep(220, 380);
            return rand(1000, 1600);
        }

        QuestLog.step("Cook", "Talk-to faalde — retry zelfde Cook");
        return rand(600, 1000);
    }

    private Integer handleDialog(int varp) {
        boolean cutscene = QuestCutsceneHelper.inCutscene();
        boolean dialog = Dialog.isOpen();
        if (!dialog && !cutscene) {
            optionStallTicks = 0;
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - lastDialogMs < DIALOG_MIN_MS) {
            return 120;
        }
        lastDialogMs = now;

        QuestHelperDialogSteps steps = varp >= 1 ? DELIVER_DIALOG : START_DIALOG;
        boolean optionMenu = Dialog.isViewingOptions();

        if (optionMenu && !hasWantedOption(steps)) {
            optionStallTicks++;
            if (optionStallTicks >= 3) {
                optionStallTicks = 0;
                QuestLog.force("Cook", "geen exacte dialoog-optie → eerste optie (failsafe)");
                Dialog.chooseOption(0);
                return rand(250, 450);
            }
            return rand(200, 350);
        }
        optionStallTicks = 0;

        if (QuestDialogHelper.handle(steps)) {
            QuestLog.step("Cook", varp >= 1
                    ? "afleveren-dialoog · " + progressLabel()
                    : "start-dialoog (What's wrong? / Can I help? / Yes.)");
            return rand(250, 450);
        }

        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            return rand(150, 280);
        }

        return cutscene ? rand(300, 500) : rand(200, 400);
    }

    private static boolean hasWantedOption(QuestHelperDialogSteps steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        for (String want : steps.steps()) {
            final String w = strip(want).toLowerCase(Locale.ROOT);
            if (w.isEmpty()) {
                continue;
            }
            try {
                if (Dialog.hasOption(t -> optionMatches(t, w))) {
                    return true;
                }
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
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

    private void pollChatTurnIns() {
        if (turnedInAll) {
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

    private void trackVanishedItems(int varp) {
        if (varp < 1 || turnedInAll) {
            return;
        }
        boolean hasEgg = Inventory.contains(EGG);
        boolean hasFlour = Inventory.contains(FLOUR);
        boolean hasMilk = Inventory.contains(MILK);

        if (hasEgg) {
            sawEgg = true;
            missEgg = 0;
        } else if (sawEgg && !turnedInEgg && ++missEgg >= 2) {
            markTurnIn(EGG);
        }

        if (hasFlour) {
            sawFlour = true;
            missFlour = 0;
        } else if (sawFlour && !turnedInFlour && ++missFlour >= 2) {
            markTurnIn(FLOUR);
        }

        if (hasMilk) {
            sawMilk = true;
            missMilk = 0;
        } else if (sawMilk && !turnedInMilk && ++missMilk >= 2) {
            markTurnIn(MILK);
        }
    }

    private void markTurnIn(String item) {
        if (EGG.equals(item) && !turnedInEgg) {
            turnedInEgg = true;
            QuestLog.force("Cook", "Egg afgeleverd · " + progressLabel());
        } else if (FLOUR.equals(item) && !turnedInFlour) {
            turnedInFlour = true;
            QuestLog.force("Cook", "Pot of flour afgeleverd · " + progressLabel());
        } else if (MILK.equals(item) && !turnedInMilk) {
            turnedInMilk = true;
            QuestLog.force("Cook", "Bucket of milk afgeleverd · " + progressLabel());
        }
        if (turnedInEgg && turnedInFlour && turnedInMilk) {
            markAllTurnedIn("alle items");
        }
    }

    private void markAllTurnedIn(String reason) {
        turnedInEgg = true;
        turnedInFlour = true;
        turnedInMilk = true;
        if (!turnedInAll) {
            turnedInAll = true;
            QuestLog.force("Cook", "quest afgerond (" + reason + ")");
        }
    }

    private void onVarpChanged(int varp) {
        if (varp == lastSeenVarp) {
            return;
        }
        if (lastSeenVarp >= 0) {
            QuestLog.force("Cook", "varp " + lastSeenVarp + " → " + varp + " · " + progressLabel());
        }
        int previous = lastSeenVarp;
        lastSeenVarp = varp;
        talkStallSinceMs = System.currentTimeMillis();
        if (varp >= VARP_QUEST_COMPLETE) {
            markAllTurnedIn("varp " + varp);
        } else if (varp >= 1 && previous < 1) {
            prepTicks = 0;
            phase = Phase.WALK_COOK;
            QuestLog.force("Cook", "quest gestart → alleen afleveren, geen bank/GE meer");
        }
    }

    private boolean needsItem(String item) {
        if (EGG.equals(item)) {
            return !turnedInEgg;
        }
        if (FLOUR.equals(item)) {
            return !turnedInFlour;
        }
        if (MILK.equals(item)) {
            return !turnedInMilk;
        }
        return false;
    }

    private boolean allItemsReady() {
        return (!needsItem(EGG) || QuestActions.hasUnnoted(EGG))
                && (!needsItem(FLOUR) || QuestActions.hasUnnoted(FLOUR))
                && (!needsItem(MILK) || QuestActions.hasUnnoted(MILK));
    }

    private int missingFromInventory() {
        int n = 0;
        if (needsItem(EGG) && !QuestActions.hasUnnoted(EGG)) {
            n++;
        }
        if (needsItem(FLOUR) && !QuestActions.hasUnnoted(FLOUR)) {
            n++;
        }
        if (needsItem(MILK) && !QuestActions.hasUnnoted(MILK)) {
            n++;
        }
        return n;
    }

    private String progressLabel() {
        return "egg=" + (turnedInEgg ? "✓" : QuestActions.unnotedCount(EGG))
                + " flour=" + (turnedInFlour ? "✓" : QuestActions.unnotedCount(FLOUR))
                + " milk=" + (turnedInMilk ? "✓" : QuestActions.unnotedCount(MILK))
                + " [" + phase.name().toLowerCase(Locale.ROOT) + "]";
    }

    private static void closeBankGe() {
        if (Bank.isOpen()) {
            Bank.leaveOpenForWalk();
        }
        GeRestockHelper.closeGeIfOpen();
    }

    private static int coins() {
        try {
            return GeRestockHelper.inventoryCoinCount();
        } catch (Throwable ignored) {
            try {
                return Inventory.getCount(true, "Coins");
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    private static int readVarp() {
        try {
            return Vars.getVarp(VARP);
        } catch (Throwable ignored) {
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
