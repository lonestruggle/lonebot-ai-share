package com.lonebot.example.quest.scripts;

import com.lonebot.example.quest.QuestBotScript;
import com.lonebot.example.quest.QuestBotTarget;
import com.lonebot.example.quest.QuestLog;
import com.lonebot.example.quest.helpers.QuestActions;
import com.lonebot.example.quest.helpers.QuestBankGePrep;
import com.lonebot.example.quest.helpers.QuestDialogHelper;
import com.lonebot.example.quest.helpers.QuestDoricHouseApproachHelper;
import com.lonebot.example.quest.helpers.QuestGePrices;
import com.lonebot.example.quest.helpers.QuestHelperDialogSteps;
import com.lonebot.example.quest.helpers.QuestImpLootHints;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Skills;
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
 * Doric's Quest — full CombatBot {@code DoricsQuestHandler} port (VarPlayer 31).
 * <p>
 * Unnoted 6× Clay + 4× Copper ore + 2× Iron ore in one delivery. Supply-chain via
 * {@link QuestBankGePrep} (inv → bank / {@link QuestImpLootHints} clay → GE). One trip to the
 * hut: door + Talk-to starts and finishes the quest. Rotation skips when Mining ≥ 10.
 */
public final class DoricsQuestScript implements QuestBotScript {

    public static final int VARP = 31;
    public static final int DONE = 100;

    public static final WorldPoint DORIC = QuestDoricHouseApproachHelper.DORIC_TILE;
    public static final WorldPoint DOOR = new WorldPoint(2950, 3450, 0);
    public static final WorldPoint APPROACH = new WorldPoint(2950, 3449, 0);

    private static final int MINING_LEVEL_SKIP = 10;

    private static final String CLAY = "Clay";
    private static final String COPPER = "Copper ore";
    private static final String IRON = "Iron ore";

    private static final int NEED_CLAY = 6;
    private static final int NEED_COPPER = 4;
    private static final int NEED_IRON = 2;

    /**
     * CombatBot dialog preferences (QH + numbered Yes / materials / anvil).
     * Priority: first on-screen match wins.
     */
    private static final QuestHelperDialogSteps DIALOG = QuestHelperDialogSteps.of(
            "Yes, I will get you the materials.",
            "I wanted to use your anvils.",
            "I wanted to use your anvil.",
            "Yes.",
            "Yes"
    );

    private static final QuestBankGePrep.Need[] ORES = {
            new QuestBankGePrep.Need(CLAY, NEED_CLAY, QuestGePrices.CLAY),
            new QuestBankGePrep.Need(COPPER, NEED_COPPER, QuestGePrices.COPPER_ORE),
            new QuestBankGePrep.Need(IRON, NEED_IRON, QuestGePrices.IRON_ORE)
    };

    private static final long SUPPLY_STALL_MS = 45_000L;
    private static final long DELIVER_STALL_MS = 30_000L;
    private static final long CHAT_SCAN_MS = 1_200L;
    private static final long REACH_RETRIGGER_MS = 15_000L;

    private String lastSupplySig = "";
    private long lastSupplyProgressMs;
    private long deliverSinceMs;
    private int lastVarpSeen = -1;
    private long lastChatScanMs;
    private long reachCooldownUntilMs;
    /** Chat "I can't reach that!" → deur dicht vóór Doric. */
    private boolean doorBlocksTalk;
    private int dialogFallbackHits;

    @Override
    public String displayName() {
        return "Doric's Quest";
    }

    @Override
    public QuestBotTarget target() {
        return QuestBotTarget.DORICS;
    }

    @Override
    public Quest runeliteQuest() {
        return Quest.DORICS_QUEST;
    }

    @Override
    public void reset() {
        lastSupplySig = "";
        lastSupplyProgressMs = 0L;
        deliverSinceMs = 0L;
        lastVarpSeen = -1;
        lastChatScanMs = 0L;
        reachCooldownUntilMs = 0L;
        doorBlocksTalk = false;
        dialogFallbackHits = 0;
        QuestDoricHouseApproachHelper.reset();
        QuestBankGePrep.reset();
    }

    @Override
    public boolean isFinished() {
        return Quests.isFinished(Quest.DORICS_QUEST) || varp() >= DONE;
    }

    /**
     * Rotation only: Mining ≥ 10 skips Doric (CombatBot {@code shouldRunQuest}).
     * An explicit DORICS target never consults this.
     */
    @Override
    public boolean isFeasible() {
        return !skipForRotation();
    }

    public static boolean skipForRotation() {
        return Skills.getLevel(Skill.MINING) >= MINING_LEVEL_SKIP;
    }

    @Override
    public int loop() {
        // 1) Dialog first: mid hand-in empties inventory — never bounce back to bank.
        if (Dialog.isOpen()) {
            return handleDialog();
        }

        int varp = varp();
        if (varp >= DONE || Quests.isFinished(Quest.DORICS_QUEST)) {
            QuestLog.step("Doric", "klaar (varp=" + varp + ")");
            return 1200;
        }

        if (Bank.isOpen()) {
            int block = BankWithdrawHelper.resolveBlockingBankInput();
            if (block > 0) {
                Sleep.sleep(Math.min(block, 600));
                return rand(300, 500);
            }
            if (!oresReady()) {
                maybeDepositJunkForSlots();
            }
        }

        // 2) Strict: only walk Doric with all three unnoted stacks on person.
        if (!oresReady()) {
            return supply(varp);
        }
        lastSupplySig = "";
        lastSupplyProgressMs = 0L;

        // 3) One trip: door + Talk-to (start + deliver in same conversation).
        return deliver(varp);
    }

    /**
     * Bank/GE via {@link QuestBankGePrep}. Imp clay in
     * {@code ~/.lonebot/accounts/{RSN}/imps-quest-progress.txt} blocks GE until bank confirms empty.
     * Failsafe: 45 s no progress → log coins + missing, keep same goal.
     */
    private int supply(int varp) {
        String sig = progressLabel();
        long now = System.currentTimeMillis();
        if (!sig.equals(lastSupplySig)) {
            lastSupplySig = sig;
            lastSupplyProgressMs = now;
            QuestImpLootHints.Hint hint = QuestImpLootHints.load();
            QuestLog.step("Doric", "materialen " + sig + " (varp=" + varp
                    + ", imp-clay=" + hint.clay + ")");
        } else if (lastSupplyProgressMs == 0L) {
            lastSupplyProgressMs = now;
        } else if (now - lastSupplyProgressMs > SUPPLY_STALL_MS) {
            lastSupplyProgressMs = now;
            QuestLog.force("Doric", "geen voortgang 45s · " + sig
                    + " · ontbreekt " + firstMissing() + " · coins=" + coins()
                    + " · impLikely=" + QuestImpLootHints.likelyInBank(firstMissing(), needOf(firstMissing()))
                    + " → bank/GE blijft proberen");
        }

        if (QuestBankGePrep.ensureUnnoted(ORES)) {
            return rand(500, 800);
        }
        QuestLog.step("Doric", "materialen compleet → hut");
        return rand(350, 550);
    }

    private int deliver(int varp) {
        WorldPoint me = QuestActions.local();
        if (me == null) {
            return 800;
        }
        long now = System.currentTimeMillis();
        if (varp != lastVarpSeen) {
            lastVarpSeen = varp;
            deliverSinceMs = now;
        } else if (deliverSinceMs == 0L) {
            deliverSinceMs = now;
        } else if (now - deliverSinceMs > DELIVER_STALL_MS) {
            deliverSinceMs = now;
            QuestDoricHouseApproachHelper.reset();
            QuestLog.force("Doric", "30s geen varp-wijziging bij hut → approach reset ("
                    + me.getX() + "," + me.getY() + ")");
        }

        scanChatForBlockedDoor(me, now);

        if (doorBlocksTalk && !QuestDoricHouseApproachHelper.insideDoricHouse(me)) {
            QuestLog.step("Doric", "deur blokkeert Talk-to → Open @ "
                    + DOOR.getX() + "," + DOOR.getY());
        } else if (QuestDoricHouseApproachHelper.canTalkToDoricNow(me)) {
            QuestLog.step("Doric", "Talk-to Doric · " + progressLabel() + " unnoted");
        } else {
            QuestLog.step("Doric", "→ hut " + DOOR.getX() + "," + DOOR.getY()
                    + " (nu " + me.getX() + "," + me.getY() + ", varp=" + varp + ")");
        }

        if (QuestDoricHouseApproachHelper.talk(DIALOG)) {
            return rand(600, 900);
        }
        QuestLog.step("Doric", "geen klik gelukt → retry hut/Talk-to");
        QuestActions.walkTo(APPROACH, 2);
        return rand(800, 1100);
    }

    private int handleDialog() {
        if (QuestDialogHelper.handle(DIALOG)) {
            dialogFallbackHits = 0;
            return rand(400, 700);
        }
        // CombatBot: materials / whetstone / anvil / numbered Yes when exact list misses.
        if (Dialog.isViewingOptions()) {
            String[] contains = {
                    "get you the materials", "materials",
                    "whetstone", "anvil", "use your anvil",
                    "clay", "copper", "iron", "start doric"
            };
            for (String key : contains) {
                final String k = key;
                if (Dialog.hasOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains(k))) {
                    Dialog.chooseOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains(k));
                    dialogFallbackHits = 0;
                    QuestLog.step("Doric", "dialog contains: " + key);
                    return rand(700, 1200);
                }
            }
            dialogFallbackHits++;
            if (dialogFallbackHits >= 2) {
                dialogFallbackHits = 0;
                QuestLog.force("Doric", "dialog fallback → optie 0");
                Dialog.chooseOption(0);
                return rand(700, 1200);
            }
            return rand(250, 400);
        }
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            return rand(400, 700);
        }
        return rand(350, 550);
    }

    /**
     * Chat "I can't reach that!" = hut door closed. Reset approach throttles so Open retries.
     */
    private void scanChatForBlockedDoor(WorldPoint me, long now) {
        if (doorBlocksTalk) {
            if (QuestDoricHouseApproachHelper.insideDoricHouse(me)) {
                doorBlocksTalk = false;
                reachCooldownUntilMs = now + REACH_RETRIGGER_MS;
            }
            return;
        }
        if (now < reachCooldownUntilMs || now - lastChatScanMs < CHAT_SCAN_MS) {
            return;
        }
        lastChatScanMs = now;
        List<String> lines = Chat.getRecentMessages(3);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String low = raw.toLowerCase(Locale.ROOT);
            if (low.contains("reach that") || low.contains("can't reach") || low.contains("cant reach")) {
                doorBlocksTalk = true;
                reachCooldownUntilMs = now + REACH_RETRIGGER_MS;
                QuestDoricHouseApproachHelper.reset();
                QuestLog.force("Doric", "chat \"can't reach that\" → deur dicht, opnieuw Open");
                return;
            }
        }
    }

    /**
     * CombatBot: deposit pickaxes / junk so 12 ore slots fit. Keep coins + quest ores.
     */
    private void maybeDepositJunkForSlots() {
        if (!Bank.isOpen()) {
            return;
        }
        try {
            int free = Inventory.getFreeSlots();
            if (free >= 4) {
                return;
            }
            QuestLog.step("Doric", "inv vol (" + free + " free) → stort junk, houd ores/coins");
            Bank.depositAllExcept(CLAY, COPPER, IRON, "Coins");
            Sleep.sleep(220, 400);
        } catch (Throwable ignored) {
        }
    }

    private static boolean oresReady() {
        return QuestActions.unnotedCount(CLAY) >= NEED_CLAY
                && QuestActions.unnotedCount(COPPER) >= NEED_COPPER
                && QuestActions.unnotedCount(IRON) >= NEED_IRON;
    }

    private static int needOf(String item) {
        if (CLAY.equalsIgnoreCase(item)) {
            return NEED_CLAY;
        }
        if (COPPER.equalsIgnoreCase(item)) {
            return NEED_COPPER;
        }
        if (IRON.equalsIgnoreCase(item)) {
            return NEED_IRON;
        }
        return 1;
    }

    private static String firstMissing() {
        if (QuestActions.unnotedCount(CLAY) < NEED_CLAY) {
            return CLAY;
        }
        if (QuestActions.unnotedCount(COPPER) < NEED_COPPER) {
            return COPPER;
        }
        if (QuestActions.unnotedCount(IRON) < NEED_IRON) {
            return IRON;
        }
        return "niets";
    }

    private static String progressLabel() {
        return QuestActions.unnotedCount(CLAY) + "/" + NEED_CLAY + " clay, "
                + QuestActions.unnotedCount(COPPER) + "/" + NEED_COPPER + " copper, "
                + QuestActions.unnotedCount(IRON) + "/" + NEED_IRON + " iron";
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
