package net.storm.sdk.items;

import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tick-vriendelijke bank-sessie (CombatBot UBM / lonebot-api BankSession light).
 * <p>
 * Flow per {@link #tick}: OPEN → DEPOSIT (depositAllExcept) → WITHDRAW → EQUIP → CLOSE of leave-walk.
 * {@code closeWhenDone=false}: geen Close/Esc — {@link Bank#leaveOpenForWalk()} en daarna lopen.
 * Eén actie per tick — geschikt voor LoopedPlugin.
 */
public final class BankSession {

    private static final Logger log = LoggerFactory.getLogger(BankSession.class);
    /** Deposit-All / Withdraw per stack, meerdere stacks per tick. */
    private static final int SLOT_BURST = 5;

    public enum Result {
        /** Nog bezig — opnieuw tick() aanroepen. */
        CONTINUE,
        OK,
        PARTIAL,
        NEEDS_GE,
        FAIL
    }

    public enum Phase {
        OPEN, DEPOSIT, WITHDRAW, EQUIP, CLOSE, DONE
    }

    public static final class Requirement {
        public final String itemName;
        /** 0 = alleen naam. Item-id voorkomt Bronze axe ↔ Bronze dagger mismatches. */
        public final int itemId;
        public final int targetOnPerson;
        public final int withdrawAmount;

        public Requirement(String itemName, int targetOnPerson) {
            this(itemName, 0, targetOnPerson, Math.max(1, targetOnPerson));
        }

        public Requirement(String itemName, int targetOnPerson, int withdrawAmount) {
            this(itemName, 0, targetOnPerson, withdrawAmount);
        }

        private Requirement(String itemName, int itemId, int targetOnPerson, int withdrawAmount) {
            this.itemName = itemName != null ? itemName.trim() : "";
            this.itemId = itemId;
            this.targetOnPerson = Math.max(0, targetOnPerson);
            this.withdrawAmount = Math.max(1, withdrawAmount);
        }

        public static Requirement byId(String itemName, int itemId, int targetOnPerson) {
            return byId(itemName, itemId, targetOnPerson, Math.max(1, targetOnPerson));
        }

        public static Requirement byId(String itemName, int itemId, int targetOnPerson, int withdrawAmount) {
            return new Requirement(itemName, itemId, targetOnPerson, withdrawAmount);
        }
    }

    public static final class Status {
        public final Result result;
        public final Phase phase;
        public final String detail;
        public final int delayMs;
        public final String missingItem;

        public Status(Result result, Phase phase, String detail, int delayMs, String missingItem) {
            this.result = result;
            this.phase = phase;
            this.detail = detail != null ? detail : "";
            this.delayMs = delayMs;
            this.missingItem = missingItem;
        }

        public boolean done() {
            return result == Result.OK || result == Result.PARTIAL
                    || result == Result.NEEDS_GE || result == Result.FAIL;
        }

        public boolean ok() {
            return result == Result.OK;
        }
    }

    private Phase phase = Phase.OPEN;
    private String[] keepOnDeposit = new String[0];
    private int[] keepOnDepositIds = new int[0];
    private List<Requirement> requirements = new ArrayList<>();
    private boolean closeWhenDone = true;
    private boolean tryEquip = true;
    private String missingForGe;
    private int equipAttempts;
    private int closeAttempts;
    private String withdrawGuardItem = "";
    private int withdrawGuardStreak;
    private int withdrawGuardHave = -1;
    /** WITHDRAW mag pas na een geslaagde DEPOSIT-check. */
    private boolean depositComplete;
    /** Eerste DEPOSIT-tick: kort wachten tot bank-UI geladen is. */
    private boolean depositUiWaited;
    /** Occupied slots bij start deposit — tegen flaky “leeg → klaar”. */
    private int depositBaselineOccupied = -1;
    /** Achter elkaar occupied=0 terwijl baseline nog vol was. */
    private int depositEmptyStreak;
    /** Achter elkaar only-keep ticks vóór DEPOSIT klaar (min. 2). */
    private int onlyKeepStreak;
    /** Wield/Wear uitgegeven, volgende tick verifiëren. */
    private String pendingEquipLabel;
    /** Niet gedragen na Wield/Wear (level-req) — niet opnieuw klikken. */
    private final Set<String> equipSkip = new HashSet<>();

    public BankSession() {
    }

    public void reset() {
        phase = Phase.OPEN;
        missingForGe = null;
        equipAttempts = 0;
        closeAttempts = 0;
        withdrawGuardItem = "";
        withdrawGuardStreak = 0;
        withdrawGuardHave = -1;
        depositComplete = false;
        depositUiWaited = false;
        depositBaselineOccupied = -1;
        depositEmptyStreak = 0;
        onlyKeepStreak = 0;
        pendingEquipLabel = null;
        equipSkip.clear();
    }

    public void configure(String[] keepOnDeposit, List<Requirement> requirements,
                          boolean closeWhenDone, boolean tryEquip) {
        configure(keepOnDeposit, null, requirements, closeWhenDone, tryEquip);
    }

    /**
     * @param closeWhenDone {@code true} = Close/Esc na afloop.
     *                      {@code false} = {@link Bank#leaveOpenForWalk()} — daarna lopen, geen Close.
     */
    public void configure(String[] keepOnDeposit, int[] keepOnDepositIds, List<Requirement> requirements,
                          boolean closeWhenDone, boolean tryEquip) {
        this.keepOnDeposit = keepOnDeposit != null ? keepOnDeposit : new String[0];
        this.keepOnDepositIds = keepOnDepositIds != null ? keepOnDepositIds.clone() : new int[0];
        this.requirements = requirements != null ? new ArrayList<>(requirements) : new ArrayList<>();
        this.closeWhenDone = closeWhenDone;
        this.tryEquip = tryEquip;
        Bank.clearLeaveOpenForWalk();
        reset();
    }

    public void configure(String[] keepOnDeposit, Requirement... requirements) {
        configure(keepOnDeposit,
                requirements != null ? Arrays.asList(requirements) : List.of(),
                true, true);
    }

    public Phase phase() {
        return phase;
    }

    /**
     * Eén bank-stap. Caller: {@code return status.delayMs}.
     */
    public Status tick() {
        switch (phase) {
            case OPEN:
                return tickOpen();
            case DEPOSIT:
                return tickDeposit();
            case WITHDRAW:
                return tickWithdraw();
            case EQUIP:
                return tickEquip();
            case CLOSE:
                return tickClose();
            case DONE:
            default:
                return new Status(Result.OK, Phase.DONE, "done", HumanBanking.closeMs(), null);
        }
    }

    private Status tickOpen() {
        if (Bank.isOpen()) {
            phase = Phase.DEPOSIT;
            return cont(Phase.DEPOSIT, "bank open → deposit",
                    HumanBanking.UI_SETTLE_MIN_MS, HumanBanking.UI_SETTLE_MAX_MS);
        }
        boolean acted = BankHelper.tryOpenFullBank();
        log.debug("[BankSession] open acted={}", acted);
        BotRuntime.logConsole("[BankSession] OPEN");
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (pos != null && pos.getPlane() >= 2 && BankHelper.isInLumbridgeCastleBuilding(pos)) {
            return cont(Phase.OPEN, "opening bank (Lumb)",
                    HumanBanking.OPEN_RETRY_MIN_MS, HumanBanking.OPEN_RETRY_MAX_MS);
        }
        return cont(Phase.OPEN, "opening bank",
                HumanBanking.OPEN_RETRY_MIN_MS, HumanBanking.OPEN_RETRY_MAX_MS);
    }

    private Status tickDeposit() {
        if (!Bank.isOpen()) {
            depositUiWaited = false;
            depositBaselineOccupied = -1;
            depositEmptyStreak = 0;
            onlyKeepStreak = 0;
            phase = Phase.OPEN;
            return cont(Phase.OPEN, "bank dicht → reopen",
                    HumanBanking.OPEN_RETRY_MIN_MS, HumanBanking.OPEN_RETRY_MAX_MS);
        }
        if (!depositUiWaited) {
            depositUiWaited = true;
            BotRuntime.logConsole("[BankSession] DEPOSIT wacht bank-UI");
            return cont(Phase.DEPOSIT, "wacht bank-UI",
                    HumanBanking.UI_SETTLE_MIN_MS, HumanBanking.UI_SETTLE_MAX_MS);
        }
        // Alleen keep + al voldane requirements in inv houden
        List<String> keep = new ArrayList<>();
        for (String k : keepOnDeposit) {
            if (k != null && !k.isBlank()) {
                keep.add(k);
            }
        }
        for (Requirement r : requirements) {
            if (r != null && r.itemName != null && !r.itemName.isEmpty()
                    && !containsIgnoreCase(keep, r.itemName)) {
                keep.add(r.itemName);
            }
        }
        Set<Integer> keepIds = collectKeepIds();
        if (depositBaselineOccupied < 0) {
            try {
                depositBaselineOccupied = Inventory.getCount();
            } catch (Throwable t) {
                depositBaselineOccupied = 0;
            }
            BotRuntime.logConsole("[BankSession] DEPOSIT baseline occupied=" + depositBaselineOccupied);
        }
        if (inventoryIsOnlyKeep(keep, keepIds)) {
            onlyKeepStreak++;
            if (onlyKeepStreak < 2) {
                BotRuntime.logConsole("[BankSession] DEPOSIT only-keep confirm " + onlyKeepStreak + "/2");
                return cont(Phase.DEPOSIT, "confirm only-keep",
                        HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
            }
            depositComplete = true;
            phase = Phase.WITHDRAW;
            BotRuntime.logConsole("[BankSession] DEPOSIT klaar — inv alleen keep");
            try {
                net.storm.sdk.bot.ActivityLog.step("Bank", "deposit klaar — inv keep");
            } catch (Throwable ignored) {
            }
            return cont(Phase.WITHDRAW, "deposit klaar",
                    HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
        }
        onlyKeepStreak = 0;
        boolean dumped = depositAllExceptKeep(keep, keepIds);
        BotRuntime.logConsole("[BankSession] DEPOSIT " + (dumped ? "1 stack" : "wacht") + " except " + keep);
        log.debug("[BankSession] deposit dumped={}", dumped);
        if (inventoryIsOnlyKeep(keep, keepIds)) {
            onlyKeepStreak++;
            if (onlyKeepStreak >= 2) {
                depositComplete = true;
                phase = Phase.WITHDRAW;
                return cont(Phase.WITHDRAW, "deposit klaar",
                        HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
            }
            return cont(Phase.DEPOSIT, "confirm only-keep",
                    HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
        }
        onlyKeepStreak = 0;
        return cont(Phase.DEPOSIT, dumped ? "depositing" : "deposit wacht inv",
                HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
    }

    private Status tickWithdraw() {
        if (!Bank.isOpen()) {
            depositComplete = false;
            phase = Phase.OPEN;
            return cont(Phase.OPEN, "bank dicht → reopen",
                    HumanBanking.OPEN_RETRY_MIN_MS, HumanBanking.OPEN_RETRY_MAX_MS);
        }
        if (!depositComplete) {
            phase = Phase.DEPOSIT;
            return tickDeposit();
        }
        int taken = 0;
        String lastLab = null;
        for (Requirement r : requirements) {
            if (taken >= SLOT_BURST) {
                break;
            }
            if (r == null || r.targetOnPerson <= 0 || !hasIdentity(r)) {
                continue;
            }
            int have = countOnPerson(r);
            if (have >= r.targetOnPerson) {
                continue;
            }
            if (!inBank(r)) {
                missingForGe = label(r);
                BotRuntime.logConsole("[BankSession] NEEDS_GE " + missingForGe);
                phase = closeWhenDone ? Phase.CLOSE : Phase.DONE;
                if (!closeWhenDone) {
                    Bank.leaveOpenForWalk();
                    return new Status(Result.NEEDS_GE, Phase.DONE, "missing in bank",
                            HumanBanking.afterActionMs(), missingForGe);
                }
                return cont(Phase.CLOSE, "needs GE " + missingForGe,
                        HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
            }
            int remaining = r.targetOnPerson - have;
            if (remaining <= 0) {
                continue;
            }
            String lab = label(r);
            if (lab.equalsIgnoreCase(withdrawGuardItem) && have <= withdrawGuardHave) {
                withdrawGuardStreak++;
            } else {
                withdrawGuardItem = lab;
                withdrawGuardStreak = 1;
                withdrawGuardHave = have;
            }
            if (withdrawGuardStreak >= 4) {
                BotRuntime.logConsole("[BankSession] WITHDRAW anti-loop " + lab + " have=" + have
                        + " — skip, sluit bank");
                continue;
            }
            boolean takeAll = r.withdrawAmount >= 1000;
            int need = takeAll ? Integer.MAX_VALUE : Math.min(r.withdrawAmount, remaining);
            if (r.itemId > 0) {
                if (takeAll) {
                    Bank.withdrawAll(r.itemId);
                } else {
                    Bank.withdraw(r.itemId, need);
                }
            } else if (takeAll) {
                Bank.withdrawAll(r.itemName);
            } else {
                Bank.withdraw(r.itemName, need);
            }
            lastLab = lab;
            taken++;
            BotRuntime.logConsole("[BankSession] WITHDRAW " + lab + (takeAll ? " All" : " ×" + need));
            try {
                net.storm.sdk.bot.ActivityLog.step("Bank", "withdraw " + lab + (takeAll ? " All" : " ×" + need));
            } catch (Throwable ignored) {
            }
            if (BankWithdrawHelper.isEnterAmountPromptVisible()) {
                break;
            }
            if (taken < SLOT_BURST) {
                HumanBanking.pauseBetweenStacks();
            }
        }
        if (taken > 0) {
            return cont(Phase.WITHDRAW, "withdraw " + lastLab + (taken > 1 ? " ×" + taken : ""),
                    HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
        }
        if (!tryEquip) {
            return finishAfterBankWork("withdraw klaar");
        }
        phase = Phase.EQUIP;
        return cont(Phase.EQUIP, "withdraw klaar",
                HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
    }

    private Status tickEquip() {
        if (equipAttempts++ > 8) {
            return finishAfterBankWork("equip skip");
        }
        if (pendingEquipLabel != null && !pendingEquipLabel.isEmpty()) {
            boolean on = false;
            for (Requirement r : requirements) {
                if (r != null && pendingEquipLabel.equals(label(r)) && isEquipped(r)) {
                    on = true;
                    break;
                }
            }
            if (!on) {
                equipSkip.add(pendingEquipLabel);
                BotRuntime.logConsole("[BankSession] EQUIP skip " + pendingEquipLabel
                        + " (niet gedragen — level-req of geblokkeerd)");
            }
            pendingEquipLabel = null;
        }
        for (Requirement r : requirements) {
            if (r == null || !hasIdentity(r)) {
                continue;
            }
            String lab = label(r);
            if (equipSkip.contains(lab) || isEquipped(r)) {
                continue;
            }
            var item = r.itemId > 0 ? Inventory.getFirst(r.itemId) : Inventory.getFirst(r.itemName);
            if (item == null && r.itemId > 0 && !r.itemName.isEmpty()) {
                item = Inventory.getFirst(r.itemName);
            }
            if (item == null) {
                continue;
            }
            if (item.hasAction("Wield") && item.interact("Wield")) {
                pendingEquipLabel = lab;
                BotRuntime.logConsole("[BankSession] EQUIP Wield " + lab);
                return cont(Phase.EQUIP, "wield " + lab,
                        HumanBanking.EQUIP_MIN_MS, HumanBanking.EQUIP_MAX_MS);
            }
            if (item.hasAction("Wear") && item.interact("Wear")) {
                pendingEquipLabel = lab;
                BotRuntime.logConsole("[BankSession] EQUIP Wear " + lab);
                return cont(Phase.EQUIP, "wear " + lab,
                        HumanBanking.EQUIP_MIN_MS, HumanBanking.EQUIP_MAX_MS);
            }
        }
        return finishAfterBankWork("equip klaar");
    }

    private static boolean isEquipped(Requirement r) {
        if (r == null) {
            return false;
        }
        try {
            if (r.itemId > 0 && Equipment.contains(r.itemId)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return r.itemName != null && !r.itemName.isEmpty() && Equipment.contains(r.itemName);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Na deposit/withdraw/equip: Close-fase, of {@link Bank#leaveOpenForWalk()} als
     * {@code closeWhenDone} uit staat (volgende stap is lopen).
     */
    private Status finishAfterBankWork(String detail) {
        if (closeWhenDone) {
            phase = Phase.CLOSE;
            return cont(Phase.CLOSE, detail, HumanBanking.ACTION_MIN_MS, HumanBanking.ACTION_MAX_MS);
        }
        Bank.leaveOpenForWalk();
        phase = Phase.DONE;
        Result end = missingForGe != null ? Result.NEEDS_GE
                : (allRequirementsMet() ? Result.OK : Result.PARTIAL);
        return new Status(end, Phase.DONE, detail, HumanBanking.afterActionMs(), missingForGe);
    }

    private Status tickClose() {
        Result end = missingForGe != null ? Result.NEEDS_GE
                : (allRequirementsMet() ? Result.OK : Result.PARTIAL);
        if (Bank.isOpen()) {
            if (closeAttempts++ > 10) {
                BotRuntime.logConsole("[BankSession] CLOSE timeout — bank nog open");
                phase = Phase.DONE;
                return new Status(end, Phase.DONE, "close timeout", HumanBanking.closeMs(), missingForGe);
            }
            Bank.close();
            BotRuntime.logConsole("[BankSession] CLOSE klik (" + closeAttempts + ")");
            return new Status(Result.CONTINUE, Phase.CLOSE, "closing", HumanBanking.closeMs(), missingForGe);
        }
        phase = Phase.DONE;
        BotRuntime.logConsole("[BankSession] CLOSE → " + end);
        return new Status(end, Phase.DONE, "done", HumanBanking.closeMs(), missingForGe);
    }

    private boolean allRequirementsMet() {
        for (Requirement r : requirements) {
            if (r == null || r.targetOnPerson <= 0 || !hasIdentity(r)) {
                continue;
            }
            if (countOnPerson(r) < r.targetOnPerson) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasIdentity(Requirement r) {
        return r != null && (r.itemId > 0 || (r.itemName != null && !r.itemName.isEmpty()));
    }

    private static String label(Requirement r) {
        if (r == null) {
            return "";
        }
        if (r.itemName != null && !r.itemName.isEmpty()) {
            return r.itemName;
        }
        return r.itemId > 0 ? ("id:" + r.itemId) : "";
    }

    private Set<Integer> collectKeepIds() {
        Set<Integer> keepIds = new HashSet<>();
        for (int id : keepOnDepositIds) {
            if (id > 0) {
                keepIds.add(id);
            }
        }
        for (Requirement r : requirements) {
            if (r != null && r.itemId > 0) {
                keepIds.add(r.itemId);
            }
        }
        return keepIds;
    }

    private static boolean inBank(Requirement r) {
        try {
            if (r.itemId > 0 && Bank.contains(r.itemId)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return r.itemName != null && !r.itemName.isEmpty() && Bank.contains(r.itemName);
        } catch (Throwable t) {
            return false;
        }
    }

    private static int countOnPerson(Requirement r) {
        int n = 0;
        if (r.itemId > 0) {
            try {
                n += Inventory.getCount(true, r.itemId);
            } catch (Throwable ignored) {
            }
            try {
                if (Equipment.contains(r.itemId)) {
                    n = Math.max(n, 1);
                }
            } catch (Throwable ignored) {
            }
            if (n > 0) {
                return n;
            }
        }
        return countOnPerson(r.itemName);
    }

    private static int countOnPerson(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        int n = 0;
        try {
            n += Inventory.getCount(true, name);
        } catch (Throwable ignored) {
        }
        try {
            if (Equipment.contains(name)) {
                n = Math.max(n, 1);
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    private boolean inventoryIsOnlyKeep(List<String> keep, Set<Integer> keepIds) {
        if (!onlyKeepOrEmpty(keep, keepIds)) {
            return false;
        }
        int occupied = 0;
        try {
            occupied = Inventory.getCount();
        } catch (Throwable ignored) {
        }
        int keepBudget = Math.max(2, keep.size() + (keepIds != null ? keepIds.size() : 0));
        if (occupied > keepBudget) {
            BotRuntime.logConsole("[BankSession] DEPOSIT inv-read flaky occupied=" + occupied
                    + " keepBudget=" + keepBudget + " — stort verder");
            return false;
        }
        if (occupied > 0 && occupied < depositBaselineOccupied) {
            depositBaselineOccupied = occupied;
        }
        // Timeout→lege getAll terwijl baseline nog vol: niet meteen “klaar”.
        // Na 3× achter elkaar leeg wél: junk is écht weg (keep kan equipped zijn).
        if (occupied == 0 && depositBaselineOccupied > keepBudget) {
            depositEmptyStreak++;
            if (depositEmptyStreak < 3) {
                BotRuntime.logConsole("[BankSession] DEPOSIT occupied=0 confirm "
                        + depositEmptyStreak + "/3 (baseline=" + depositBaselineOccupied + ")");
                return false;
            }
            BotRuntime.logConsole("[BankSession] DEPOSIT leeg bevestigd — verder naar keep/withdraw");
            return true;
        }
        depositEmptyStreak = 0;
        return true;
    }

    private static boolean onlyKeepOrEmpty(List<String> keep, Set<Integer> keepIds) {
        for (var item : Inventory.getAll()) {
            if (item == null || item.getId() <= 0) {
                continue;
            }
            if (keepIds != null && keepIds.contains(item.getId())) {
                continue;
            }
            String name = item.getName();
            if (name != null && !name.isEmpty() && containsIgnoreCase(keep, name)) {
                continue;
            }
            // Unnamed slots (client-thread timeout) zijn géén keep — vis moet gestort.
            return false;
        }
        return true;
    }

    private static boolean depositAllExceptKeep(List<String> keep, Set<Integer> keepIds) {
        if (keepIds != null && !keepIds.isEmpty()) {
            int[] ids = new int[keepIds.size()];
            int i = 0;
            for (int id : keepIds) {
                ids[i++] = id;
            }
            return Bank.depositAllExcept(ids);
        }
        return Bank.depositAllExcept(keep.toArray(new String[0]));
    }

    private static boolean containsIgnoreCase(List<String> list, String name) {
        if (name == null) {
            return false;
        }
        String want = name.toLowerCase(Locale.ROOT);
        for (String s : list) {
            if (s != null && s.toLowerCase(Locale.ROOT).equals(want)) {
                return true;
            }
        }
        return false;
    }

    private Status cont(Phase p, String detail, int minDelay, int maxDelay) {
        return new Status(Result.CONTINUE, p, detail, Rand.nextInt(minDelay, maxDelay), null);
    }
}
