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
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankWithdrawHelper;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.quests.Quests;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Goblin Diplomacy — full CombatBot {@code GoblinDiplomacyScript} port (varbit 2378).
 * <p>
 * ≤3 orange armour → 4 blue → 5 brown → 6 done. Supply via {@link QuestBankGePrep}
 * (3× Goblin mail + Blue dye + Orange dye), dye on unnoted plain mail, three presentations
 * to Bentnoze/Wartface via {@link QuestNpcApproachHelper#goblinGenerals()}.
 */
public final class GoblinDiplomacyScript implements QuestBotScript {

    /** {@code Varbits.QUEST_GOBLIN_DIPLOMACY} */
    public static final int VARBIT = 2378;
    private static final int VARBIT_COMPLETE = 6;

    private static final String GOBLIN_MAIL = "Goblin mail";
    private static final String BLUE_DYE = "Blue dye";
    private static final String ORANGE_DYE = "Orange dye";
    private static final String BLUE_MAIL = "Blue goblin mail";
    private static final String ORANGE_MAIL = "Orange goblin mail";

    private static final int MAIL_NEEDED = 3;

    private static final int GE_PRICE_MAIL = QuestGePrices.GOBLIN_MAIL;
    private static final int GE_PRICE_BLUE = QuestGePrices.BLUE_DYE;
    private static final int GE_PRICE_ORANGE = QuestGePrices.ORANGE_DYE;

    private static final long TALK_STALL_MS = 45_000L;
    private static final long PRESENT_GRACE_MS = 4_000L;
    private static final long DYE_COOLDOWN_MS = 1_200L;
    private static final long CHAT_POLL_MS = 1_200L;
    private static final long NPC_TALK_COOLDOWN_MS = 1_800L;

    /**
     * CombatBot fallback order (QH talkToGeneral1–3). First on-screen match wins —
     * armour lines first so hand-in beats generic Yes.
     */
    private static final QuestHelperDialogSteps DIALOG_ORANGE = QuestHelperDialogSteps.of(
            "I have some orange armour here.",
            "Yes, Wartface looks fat",
            "Yes, he looks fat",
            "So how is life for the goblins?",
            "What about a different colour?",
            "Do you want me to pick an armour colour for you?",
            "Yes."
    );

    private static final QuestHelperDialogSteps DIALOG_BLUE = QuestHelperDialogSteps.of(
            "I have some blue armour here.",
            "So how is life for the goblins?",
            "Yes, Wartface looks fat",
            "Yes, he looks fat"
    );

    private static final QuestHelperDialogSteps DIALOG_BROWN = QuestHelperDialogSteps.of(
            "I have some brown armour here.",
            "Yes, Wartface looks fat",
            "Yes, he looks fat",
            "So how is life for the goblins?"
    );

    private final QuestNpcApproachHelper generals = QuestNpcApproachHelper.goblinGenerals();

    private int lastVarbit = -1;
    private long lastVarbitMs;
    private long lastPresentMs;
    private long lastDyeMs;
    private long lastNpcMs;
    private long lastChatPollMs;
    private boolean questCompleteSignal;

    @Override
    public String displayName() {
        return "Goblin Diplomacy";
    }

    @Override
    public QuestBotTarget target() {
        return QuestBotTarget.GOBLIN_DIPLOMACY;
    }

    @Override
    public Quest runeliteQuest() {
        return Quest.GOBLIN_DIPLOMACY;
    }

    @Override
    public void reset() {
        lastVarbit = -1;
        lastVarbitMs = 0L;
        lastPresentMs = 0L;
        lastDyeMs = 0L;
        lastNpcMs = 0L;
        lastChatPollMs = 0L;
        questCompleteSignal = false;
        generals.reset();
        QuestBankGePrep.reset();
    }

    @Override
    public boolean isFinished() {
        return questCompleteSignal
                || Quests.isFinished(Quest.GOBLIN_DIPLOMACY)
                || readVarbit() >= VARBIT_COMPLETE;
    }

    public void onChatMessage(String message) {
        if (message == null) {
            return;
        }
        String low = message.toLowerCase(Locale.ROOT);
        if (low.contains("congratulations") && low.contains("quest")
                || low.contains("completed the goblin diplomacy")
                || low.contains("you have completed a quest")) {
            questCompleteSignal = true;
            QuestLog.force("Goblin", "voltooid (chat)");
        }
    }

    @Override
    public int loop() {
        if (QuestCompletionHelper.isOpen()) {
            QuestCompletionHelper.dismiss();
            return rand(600, 900);
        }

        pollCompletionChat();

        if (isFinished()) {
            QuestLog.step("Goblin", "klaar");
            closeBankGe();
            return 1200;
        }

        int varbit = readVarbit();
        trackVarbit(varbit);

        if (QuestCutsceneHelper.inCutscene()) {
            if (Dialog.canContinue()) {
                Dialog.continueSpace();
            } else {
                QuestDialogHelper.handle(dialogForVarbit(varbit));
            }
            QuestLog.step("Goblin", "cutscene — geen walk");
            return rand(300, 500);
        }

        // Dialog first: mid hand-in empties inv before varbit ticks.
        if (Dialog.isOpen()) {
            if (QuestDialogHelper.handle(dialogForVarbit(varbit))) {
                return rand(250, 450);
            }
            if (tryFatGeneralFallback(varbit)) {
                return rand(250, 450);
            }
            if (Dialog.canContinue()) {
                Dialog.continueSpace();
                return rand(200, 350);
            }
            return rand(250, 400);
        }

        if (Bank.isOpen()) {
            int block = BankWithdrawHelper.resolveBlockingBankInput();
            if (block > 0) {
                Sleep.sleep(Math.min(block, 600));
                return rand(300, 500);
            }
            maybeDepositJunk();
        }

        if (varbit >= 5) {
            return presentBrown();
        }
        if (varbit == 4) {
            return presentBlue();
        }
        return presentOrange();
    }

    /** Varbit ≤ 3: 3× mail + both dyes, dye blue+orange, show orange set. */
    private int presentOrange() {
        if (!armourDyed()) {
            return prepareArmour();
        }
        return present("orange", DIALOG_ORANGE);
    }

    /** Varbit 4: blue set. Re-dye when blue mail is gone. */
    private int presentBlue() {
        if (!QuestActions.hasUnnoted(BLUE_MAIL)) {
            if (waitingForVarbit()) {
                return rand(500, 800);
            }
            List<QuestBankGePrep.Need> needs = new ArrayList<>();
            if (!QuestActions.hasUnnoted(BLUE_DYE)) {
                needs.add(new QuestBankGePrep.Need(BLUE_DYE, 1, GE_PRICE_BLUE));
            }
            if (plainMail() < 2) {
                needs.add(new QuestBankGePrep.Need(GOBLIN_MAIL, 2, GE_PRICE_MAIL));
            }
            if (!needs.isEmpty()) {
                QuestBankGePrep.Need[] arr = needs.toArray(new QuestBankGePrep.Need[0]);
                QuestLog.step("Goblin", "bank/GE " + describe(arr) + " · " + progress());
                if (QuestBankGePrep.ensureUnnoted(arr)) {
                    return rand(500, 800);
                }
            }
            int dye = dyeIfPossible(BLUE_DYE, BLUE_MAIL);
            if (dye > 0) {
                return dye;
            }
            QuestLog.step("Goblin", "wacht op blue mail · " + progress());
            return rand(700, 1100);
        }
        return present("blue", DIALOG_BLUE);
    }

    /** Varbit 5: plain (brown) mail closes the quest. */
    private int presentBrown() {
        if (plainMail() < 1) {
            if (waitingForVarbit()) {
                return rand(500, 800);
            }
            QuestBankGePrep.Need mail = new QuestBankGePrep.Need(GOBLIN_MAIL, 1, GE_PRICE_MAIL);
            QuestLog.step("Goblin", "bank/GE 1× " + GOBLIN_MAIL + " voor brown · " + progress());
            if (QuestBankGePrep.ensureUnnoted(mail)) {
                return rand(500, 800);
            }
        }
        return present("brown", DIALOG_BROWN);
    }

    private int prepareArmour() {
        if (waitingForVarbit()) {
            return rand(500, 800);
        }

        QuestBankGePrep.Need[] needs = missingSupplies();
        if (needs.length > 0) {
            QuestLog.step("Goblin", "bank/GE " + describe(needs) + " · " + progress());
            if (QuestBankGePrep.ensureUnnoted(needs)) {
                return rand(500, 800);
            }
        }

        int blue = dyeIfPossible(BLUE_DYE, BLUE_MAIL);
        if (blue > 0) {
            return blue;
        }
        int orange = dyeIfPossible(ORANGE_DYE, ORANGE_MAIL);
        if (orange > 0) {
            return orange;
        }

        QuestLog.step("Goblin", "wacht op verf/mail · " + progress());
        return rand(700, 1100);
    }

    private QuestBankGePrep.Need[] missingSupplies() {
        List<QuestBankGePrep.Need> out = new ArrayList<>();
        if (!hasBlueComponent()) {
            out.add(new QuestBankGePrep.Need(BLUE_DYE, 1, GE_PRICE_BLUE));
        }
        if (!hasOrangeComponent()) {
            out.add(new QuestBankGePrep.Need(ORANGE_DYE, 1, GE_PRICE_ORANGE));
        }
        int plainNeeded = plainMailNeeded();
        if (plainMail() < plainNeeded) {
            out.add(new QuestBankGePrep.Need(GOBLIN_MAIL, plainNeeded, GE_PRICE_MAIL));
        }
        return out.toArray(new QuestBankGePrep.Need[0]);
    }

    private static int plainMailNeeded() {
        int dyed = QuestActions.unnotedCount(BLUE_MAIL) + QuestActions.unnotedCount(ORANGE_MAIL);
        return Math.max(1, MAIL_NEEDED - dyed);
    }

    private int dyeIfPossible(String dyeName, String resultName) {
        if (QuestActions.hasUnnoted(resultName) || !QuestActions.hasUnnoted(dyeName)
                || plainMail() < 1) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastDyeMs < DYE_COOLDOWN_MS) {
            return rand(400, 700);
        }
        if (!useDyeOnPlainMail(dyeName)) {
            return 0;
        }
        lastDyeMs = now;
        QuestLog.step("Goblin", dyeName + " → " + GOBLIN_MAIL + " (" + resultName + ")");
        return rand(1200, 1800);
    }

    private int present(String colour, QuestHelperDialogSteps dialog) {
        long now = System.currentTimeMillis();
        if (lastVarbitMs > 0L && now - lastVarbitMs > TALK_STALL_MS) {
            generals.reset();
            lastVarbitMs = now;
            QuestLog.force("Goblin", "stap " + colour + " stil → approach reset");
        }

        if (QuestCutsceneHelper.inCutscene()) {
            return rand(300, 500);
        }

        QuestLog.step("Goblin", "toon " + colour + " armour aan generals · " + progress());
        lastPresentMs = now;

        int approach = generals.approachBeforeTalk();
        if (approach > 0) {
            QuestLog.step("Goblin", "→ generals tent");
            return approach;
        }

        INPC general = findQuestGeneral();
        WorldPoint me = QuestActions.local();
        if (now - lastNpcMs < NPC_TALK_COOLDOWN_MS) {
            if (me == null || general == null || !generals.canTalkNow(me, general)) {
                return rand(400, 700);
            }
        }
        if (general == null || me == null || !generals.canTalkNow(me, general)) {
            int retry = generals.approachBeforeTalk();
            return retry > 0 ? retry : rand(800, 1200);
        }

        if (general.interact("Talk-to") || general.interact("Talk")) {
            lastNpcMs = now;
            QuestLog.step("Goblin", "Talk-to General @ " + me.getX() + "," + me.getY()
                    + " (varbit=" + lastVarbit + ")");
            Sleep.sleep(220, 380);
            return rand(1000, 1600);
        }

        if (generals.talk(dialog)) {
            return rand(600, 900);
        }
        return rand(700, 1100);
    }

    private INPC findQuestGeneral() {
        INPC fromHelper = generals.findNpc();
        if (fromHelper != null && isQuestGeneralName(fromHelper.getName())) {
            return fromHelper;
        }
        try {
            return NPCs.getNearest(n -> n != null && n.getName() != null
                    && isQuestGeneralName(n.getName())
                    && n.getWorldLocation() != null
                    && QuestNpcApproachHelper.inGoblinGeneralsTalkZone(n.getWorldLocation()));
        } catch (Throwable ignored) {
            return fromHelper;
        }
    }

    private static boolean isQuestGeneralName(String name) {
        if (name == null) {
            return false;
        }
        String low = name.toLowerCase(Locale.ROOT);
        return low.contains("general") || low.contains("bentnoze") || low.contains("wartface");
    }

    private boolean waitingForVarbit() {
        return lastPresentMs > 0L && System.currentTimeMillis() - lastPresentMs < PRESENT_GRACE_MS;
    }

    private void trackVarbit(int varbit) {
        long now = System.currentTimeMillis();
        if (varbit != lastVarbit) {
            if (lastVarbit >= 0) {
                QuestLog.force("Goblin", "varbit " + lastVarbit + " → " + varbit + " · " + progress());
            }
            lastVarbit = varbit;
            lastVarbitMs = now;
            lastPresentMs = 0L;
            generals.reset();
        } else if (lastVarbitMs == 0L) {
            lastVarbitMs = now;
        }
    }

    private boolean tryFatGeneralFallback(int varbit) {
        if (!Dialog.isViewingOptions()) {
            return false;
        }
        if (varbit != 4 && varbit != 5 && !(varbit <= 3 && armourDyed())) {
            return false;
        }
        if (Dialog.hasOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains("looks fat"))) {
            Dialog.chooseOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains("looks fat"));
            QuestLog.step("Goblin", "dialog fallback: looks fat");
            return true;
        }
        return false;
    }

    private static QuestHelperDialogSteps dialogForVarbit(int varbit) {
        if (varbit >= VARBIT_COMPLETE) {
            return QuestHelperDialogSteps.of();
        }
        if (varbit >= 5) {
            return DIALOG_BROWN;
        }
        if (varbit == 4) {
            return DIALOG_BLUE;
        }
        return DIALOG_ORANGE;
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

    private void maybeDepositJunk() {
        if (!Bank.isOpen()) {
            return;
        }
        try {
            if (Inventory.getFreeSlots() >= 6) {
                return;
            }
            QuestLog.step("Goblin", "inv vol → stort junk, houd mail/dyes/coins");
            Bank.depositAllExcept(GOBLIN_MAIL, BLUE_MAIL, ORANGE_MAIL, BLUE_DYE, ORANGE_DYE, "Coins");
            Sleep.sleep(220, 400);
        } catch (Throwable ignored) {
        }
    }

    private static void closeBankGe() {
        if (Bank.isOpen()) {
            Bank.leaveOpenForWalk();
        }
    }

    /**
     * Client-thread timeout can return 0; varbit never goes backwards — keep high-water.
     */
    private int readVarbit() {
        int raw;
        try {
            raw = Vars.getBit(VARBIT);
        } catch (Throwable ignored) {
            raw = 0;
        }
        return Math.max(raw, lastVarbit);
    }

    private static boolean armourDyed() {
        return QuestActions.hasUnnoted(BLUE_MAIL)
                && QuestActions.hasUnnoted(ORANGE_MAIL)
                && plainMail() >= 1;
    }

    private static boolean hasBlueComponent() {
        return QuestActions.hasUnnoted(BLUE_DYE) || QuestActions.hasUnnoted(BLUE_MAIL);
    }

    private static boolean hasOrangeComponent() {
        return QuestActions.hasUnnoted(ORANGE_DYE) || QuestActions.hasUnnoted(ORANGE_MAIL);
    }

    private static int plainMail() {
        return QuestActions.unnotedCount(GOBLIN_MAIL);
    }

    private static boolean useDyeOnPlainMail(String dyeName) {
        IInventoryItem mail = firstPlainMail();
        if (mail == null) {
            return false;
        }
        if (!hasNotedMail()) {
            return QuestActions.useItemOnItem(dyeName, GOBLIN_MAIL);
        }
        IInventoryItem dye = Inventory.getFirst(i -> i != null && !i.isNoted()
                && dyeName.equalsIgnoreCase(i.getName()));
        return dye != null && dye.useOn(mail);
    }

    private static IInventoryItem firstPlainMail() {
        try {
            return Inventory.getFirst(i -> i != null && !i.isNoted()
                    && GOBLIN_MAIL.equalsIgnoreCase(i.getName()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasNotedMail() {
        try {
            return Inventory.getFirst(i -> i != null && i.isNoted()
                    && GOBLIN_MAIL.equalsIgnoreCase(i.getName())) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String describe(QuestBankGePrep.Need[] needs) {
        StringBuilder sb = new StringBuilder();
        for (QuestBankGePrep.Need n : needs) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(n.qty).append("× ").append(n.name);
        }
        return sb.toString();
    }

    private static String progress() {
        return "mail=" + plainMail() + "/" + MAIL_NEEDED
                + " blue=" + component(BLUE_DYE, BLUE_MAIL)
                + " orange=" + component(ORANGE_DYE, ORANGE_MAIL);
    }

    private static String component(String dyeName, String mailName) {
        if (QuestActions.hasUnnoted(mailName)) {
            return "mail";
        }
        if (QuestActions.hasUnnoted(dyeName)) {
            return "dye";
        }
        return "—";
    }

    private static int rand(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(max - min);
    }
}
