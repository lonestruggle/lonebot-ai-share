package com.lonebot.example;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.ActorState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.game.Combat;
import net.storm.sdk.game.Game;
import net.storm.sdk.interact.AimInteractHelper;
import net.storm.sdk.interact.InteractWalkHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.Reachable;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.widgets.Dialog;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monastery monks — att/def trainen.
 * <p>
 * Prioriteit: nood-flee (HP≤crit) → heal via Talk-to (optie) / eten → food restock
 * (cabbage / Edge bank) → terug naar combat center → Attack.
 * <p>
 * Defaults (user capture): center {@code 3051,3492 r15}, Monk id {@code 2579},
 * cabbage {@code 3052,3503}.
 */
@PluginDescriptor(
        name = "LoneBot Monk Killer",
        description = "Monastery monks: fight, Talk-heal/eat, cabbage/bank, flee at low HP"
)
public class MonkKillerPlugin extends LoopedPlugin {

    public static final String VERSION = "0.1.11";

    public static final int MONK_NPC_ID = 2579;
    public static final WorldPoint FIGHT_CENTER = new WorldPoint(3051, 3492, 0);
    public static final int FIGHT_RADIUS = 15;
    public static final WorldPoint CABBAGE_TILE = new WorldPoint(3052, 3503, 0);
    public static final WorldPoint EDGEVILLE_BANK = BankHelper.EDGEVILLE_BANK;
    /** Monastery/veld-cabbage heelt 1 HP. */
    private static final int CABBAGE_HEAL_HP = 1;

    private static final String[] BANK_FOOD_NAMES = {
            "Cabbage", "Trout", "Salmon", "Tuna", "Lobster", "Swordfish",
            "Bread", "Cake", "Shrimp", "Anchovies", "Cooked meat", "Meat",
            "Herring", "Pike", "Sardine", "Potato", "Onion"
    };

    private enum Phase {
        FIGHT, FLEE, EAT, HEAL_TALK, CABBAGE, TOP_UP, BANK_WALK, BANKING, RETURN
    }

    private Phase phase = Phase.FIGHT;
    private long lastAttackMs;
    private long lastEatMs;
    private long lastConsoleMs;
    private long lastStatusMs;
    private int lastTargetIndex = -1;
    private int bankNoFoodTries;
    /** Na Attack: wacht tot combat/approach klaar is — geen walk naar center tussendoor. */
    private long approachTargetUntilMs;
    /** Cabbage-sessie: food-count bij start + target. Alleen restock bij 0 food. */
    private int cabbageFoodAtStart = -1;
    private int cabbagePickGoal;
    private long lastCabbagePickMs;
    private int cabbageFoodAfterPick = -1;
    private long lastHealTalkMs;
    private long healTalkSessionStartMs;
    private int healTalkHpAtStart = -1;
    private int healTalkFailStreak;
    private int healTalkNpcIndex = -1;
    /** Na Talk-to: wacht op dialog — niet meteen “ok” als LMB Attack deed. */
    private long healTalkAwaitDialogUntilMs;

    @Override
    public int loop() {
        BotRuntime.monkPluginVersion = VERSION;
        if (!BotRuntime.botEnabled || !BotRuntime.monkKillerEnabled) {
            setStatus(BotRuntime.botEnabled ? "uit" : "bot uit");
            phase = Phase.FIGHT;
            lastTargetIndex = -1;
            bankNoFoodTries = 0;
            cabbageFoodAtStart = -1;
            resetHealTalkSession();
            return 800;
        }
        if (net.storm.sdk.bot.ClueSkillHandoff.tryHandoffFromActiveSkill()) {
            setStatus("clue handoff");
            return 400;
        }

        if (!Game.isLoggedIn()) {
            setStatus("niet ingelogd");
            return 1000;
        }

        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            setStatus("geen speler");
            return 600;
        }

        int anti = AntiBan.get().check();
        if (anti > 0) {
            setStatus("antiban: " + AntiBan.get().getLastActionLabel());
            return Math.min(anti, 2500);
        }

        WorldPoint pos = me.worldLocation;
        int hp = Combat.getCurrentHealth();
        double hpPct = Combat.getHealthPercent();
        boolean hasFood = hasFoodToEat();
        int critHp = criticalHp();
        int eatPct = eatPercent();

        // Dialog open tijdens heal → eerst afronden
        if (phase == Phase.HEAL_TALK && net.storm.sdk.widgets.Dialog.isOpen()) {
            return tickHealTalk(pos, hp, hpPct, eatPct);
        }

        // --- Prioriteit 1: kritische HP ---
        if (hp > 0 && hp <= critHp) {
            // Actieve cabbage-sessie afbreken alleen bij nood
            if (phase == Phase.CABBAGE) {
                cabbageFoodAtStart = -1;
            }
            phase = Phase.FLEE;
            return tickFlee(pos, hasFood, hp);
        }

        // --- Cabbage-plukronde: niet onderbreken met eten (anders: 1 pluk → eat → weer pluk) ---
        if (phase == Phase.CABBAGE && cabbageFoodAtStart >= 0) {
            return tickCabbage(pos);
        }

        // --- Na plukken: vol eten vóór Attack ---
        if (phase == Phase.TOP_UP) {
            return tickTopUp(pos, hp, hpPct);
        }

        // --- Prioriteit 2: healen / eten ---
        // Volgorde: Talk-to → inv-food → cabbage/bank (alleen bij 0 food)
        if (shouldEat(hpPct, eatPct) || phase == Phase.HEAL_TALK) {
            if (BotRuntime.monkHealViaTalk || phase == Phase.HEAL_TALK) {
                int talk = tickHealTalk(pos, hp, hpPct, eatPct);
                if (talk > 0) {
                    return talk;
                }
            }
            if (hasFood) {
                phase = Phase.EAT;
                return tickEat(hp, hpPct);
            }
            // Geen food → één restock-ronde (niet “onder 5 houden”)
            int restock = tickFoodRestock(pos);
            if (restock > 0) {
                return restock;
            }
        }

        // Proactief: geen food meer en we staan al bij bank / of bank open
        if (!hasFood && BotRuntime.monkBankWhenNoFood
                && allowBankSource()
                && (Bank.isOpen() || pos.distanceTo(EDGEVILLE_BANK) <= 8)) {
            phase = Phase.BANKING;
            return tickBanking(pos);
        }

        // Na bank/heal terug naar center — CABBAGE/TOP_UP hier NIET forceren naar FIGHT
        // (cabbage-tegel ligt in fight-radius)
        if (phase == Phase.RETURN || phase == Phase.BANKING
                || phase == Phase.BANK_WALK || phase == Phase.HEAL_TALK) {
            if (phase == Phase.HEAL_TALK && !shouldEat(hpPct, eatPct)) {
                resetHealTalkSession();
                lastTargetIndex = -1;
                approachTargetUntilMs = 0L;
            }
            if (!inFightArea(pos)) {
                phase = Phase.RETURN;
                return tickReturn(pos);
            }
            phase = Phase.FIGHT;
        }

        if (!inFightArea(pos)) {
            phase = Phase.RETURN;
            return tickReturn(pos);
        }

        phase = Phase.FIGHT;
        return tickFight(me, pos);
    }

    private void resetHealTalkSession() {
        healTalkSessionStartMs = 0L;
        healTalkHpAtStart = -1;
        healTalkFailStreak = 0;
        healTalkNpcIndex = -1;
        healTalkAwaitDialogUntilMs = 0L;
    }

    /** @return delay ms, of 0 als restock niet van toepassing */
    private int tickFoodRestock(WorldPoint pos) {
        String src = foodSource();
        boolean tryCabbage = BotRuntime.monkCabbagePickEnabled
                && ("AUTO".equals(src) || "CABBAGE".equals(src));
        boolean tryBank = BotRuntime.monkBankWhenNoFood
                && ("AUTO".equals(src) || "BANK".equals(src));

        if (tryCabbage && cabbageAvailableNear(pos)) {
            beginCabbageSessionIfNeeded();
            phase = Phase.CABBAGE;
            return tickCabbage(pos);
        }
        if (tryBank) {
            phase = Phase.BANK_WALK;
            return tickBankWalk(pos);
        }
        if (tryCabbage && !tryBank) {
            beginCabbageSessionIfNeeded();
            phase = Phase.CABBAGE;
            Movement.walkTo(CABBAGE_TILE);
            setStatus("→ cabbage (alleen-cabbage)");
            return random(500, 900);
        }
        return requestLogoutNoFood("geen food-bron beschikbaar");
    }

    private void beginCabbageSessionIfNeeded() {
        if (phase == Phase.CABBAGE && cabbageFoodAtStart >= 0) {
            return;
        }
        cabbageFoodAtStart = countFood();
        cabbagePickGoal = computeCabbagePickGoal();
        cabbageFoodAfterPick = -1;
        lastCabbagePickMs = 0L;
        int missing = Combat.getMissingHealth();
        logThrottled("[Monk/cabbage] sessie start have=" + cabbageFoodAtStart
                + " missingHp=" + missing + " heal/" + CABBAGE_HEAL_HP
                + " → pick +" + cabbagePickGoal
                + (missing <= 0 ? " (stock)" : " (fill)"));
    }

    /**
     * Cabbage = 1 HP. Bij HP-tekort: pluk genoeg om vol te komen.
     * Bij volle HP: pluk het ingestelde stock-aantal.
     * Nooit meer dan vrije inv-slots.
     */
    private int computeCabbagePickGoal() {
        int missing = Combat.getMissingHealth();
        int goal;
        if (missing > 0) {
            goal = (missing + CABBAGE_HEAL_HP - 1) / CABBAGE_HEAL_HP;
        } else {
            goal = cabbagePickAmount();
        }
        int free = 28;
        try {
            free = Inventory.getFreeSlots();
        } catch (Throwable ignored) {
        }
        if (free > 0) {
            goal = Math.min(goal, free);
        }
        return Math.max(1, goal);
    }

    private int requestLogoutNoFood(String why) {
        if (!BotRuntime.monkLogoutWhenNoFood) {
            setStatus("geen food — wacht (" + why + ")");
            logThrottled("[Monk/food] " + why + " (logout uit)");
            return random(800, 1400);
        }
        setStatus("geen food → logout");
        logThrottled("[Monk/food] " + why + " → logout");
        BotRuntime.panelLogoutRequestedAtMs = System.currentTimeMillis();
        BotRuntime.panelLogoutRequested = true;
        BotRuntime.botEnabled = false;
        BotRuntime.logConsole("[Monk/food] bot uit + logout gepland");
        return 1200;
    }

    private int tickFlee(WorldPoint pos, boolean hasFood, int hp) {
        setStatus("NOOD HP=" + hp + " → flee cabbage");
        logThrottled("[Monk/flee] HP=" + hp + " food=" + hasFood + " → " + CABBAGE_TILE);
        ensureRunOn();
        if (hasFood && pos.distanceTo(CABBAGE_TILE) <= 6) {
            return tickEat(hp, Combat.getHealthPercent());
        }
        if (pos.distanceTo(CABBAGE_TILE) > 2) {
            InteractWalkHelper.approachIfNeeded(CABBAGE_TILE, 2);
            return random(400, 700);
        }
        if (hasFood) {
            return tickEat(hp, Combat.getHealthPercent());
        }
        // Geen food op flee-punt → restock volgens settings
        return tickFoodRestock(pos);
    }

    private int tickEat(int hp, double hpPct) {
        long now = System.currentTimeMillis();
        if (now - lastEatMs < 500L) {
            return random(200, 400);
        }
        IInventoryItem food = findFood();
        if (food == null) {
            setStatus("eten: geen food");
            return random(300, 500);
        }
        String name = food.getName() != null ? food.getName() : "?";
        setStatus("Eat " + name + " (hp=" + hp + " " + (int) hpPct + "%)");
        logThrottled("[Monk/eat] " + name + " hp=" + hp);
        try {
            if (food.hasAction("Eat")) {
                food.interact("Eat");
            } else {
                food.interact("Drink");
            }
        } catch (Throwable t) {
            setStatus("eat mislukt " + name);
            return random(400, 700);
        }
        lastEatMs = now;
        AntiBan.get().markBotActivity();
        return random(600, 1000);
    }

    /**
     * @return delay ms; {@code 0} = geen talkable monk → caller valt terug op food/cabbage/bank
     */
    private int tickHealTalk(WorldPoint pos, int hp, double hpPct, int eatPct) {
        long now = System.currentTimeMillis();
        if (phase != Phase.HEAL_TALK) {
            phase = Phase.HEAL_TALK;
            healTalkSessionStartMs = now;
            healTalkHpAtStart = hp;
            healTalkFailStreak = 0;
            approachTargetUntilMs = 0L;
            lastTargetIndex = -1;
            // Geen gekaapt walk-pad tijdens heal-dialog
            try {
                MovementHelper.clearPath();
            } catch (Throwable ignored) {
            }
            logThrottled("[Monk/heal] Talk-to start hp=" + hp + " (" + (int) hpPct + "%)");
        }

        // Gesprek open: snel door (continue / heal-optie)
        if (Dialog.isOpen()) {
            healTalkFailStreak = 0;
            healTalkAwaitDialogUntilMs = 0L;
            // Sessietimer resetten bij echt gesprek — anders timeout door eerdere walk-fase
            healTalkSessionStartMs = now;
            if (Dialog.canContinue()) {
                setStatus("heal dialog…");
                Dialog.continueSpace();
                return random(160, 280);
            }
            if (Dialog.chooseOption("Can you heal me", "heal me", "I'm injured", "injured")) {
                setStatus("heal: Can you heal me?");
                logThrottled("[Monk/heal] optie Can you heal me?");
                AntiBan.get().markBotActivity();
                return random(200, 340);
            }
            // Onbekende opties: niet blijven hangen — space/continue
            Dialog.continueSpace();
            return random(180, 300);
        }

        // Net Talk-to geklikt: wacht op dialog — als we aanvallen i.p.v. praten → stop
        if (healTalkAwaitDialogUntilMs > 0L) {
            if (now < healTalkAwaitDialogUntilMs) {
                Players.LocalSnap snap = Players.snapshotLocal();
                ActorState.ThreatSnap threat = ActorState.threatToLocal();
                if (snap.present && threat.underAttack) {
                    logThrottled("[Monk/heal] onder aanval tijdens Talk-to — food-fallback");
                    healTalkAwaitDialogUntilMs = 0L;
                    healTalkFailStreak++;
                    lastTargetIndex = -1;
                    approachTargetUntilMs = 0L;
                    try {
                        MovementHelper.clearPath();
                    } catch (Throwable ignored) {
                    }
                    if (healTalkFailStreak >= 3) {
                        resetHealTalkSession();
                        phase = Phase.FIGHT;
                        return 0;
                    }
                    return random(400, 700);
                }
                // Talk-to zet ook interacting — dat is OK, wacht op dialog
                setStatus("heal wacht dialog…");
                return random(200, 350);
            }
            // Geen dialog binnen grace → opnieuw of fallback
            healTalkAwaitDialogUntilMs = 0L;
            healTalkFailStreak++;
            logThrottled("[Monk/heal] geen dialog na Talk-to (fail=" + healTalkFailStreak + ")");
            if (healTalkFailStreak >= 3) {
                resetHealTalkSession();
                phase = Phase.FIGHT;
                return 0;
            }
        }

        // Klaar genoeg HP
        if (!shouldEat(hpPct, eatPct)) {
            setStatus("heal klaar " + (int) hpPct + "%");
            logThrottled("[Monk/heal] klaar hp=" + hp + " (" + (int) hpPct + "%) → fight");
            resetHealTalkSession();
            lastTargetIndex = -1;
            approachTargetUntilMs = 0L;
            phase = Phase.FIGHT;
            return random(300, 500);
        }

        // Timeout / geen progress → fallback
        if (healTalkSessionStartMs > 0 && now - healTalkSessionStartMs > 14_000L
                && hp <= healTalkHpAtStart) {
            logThrottled("[Monk/heal] timeout → food/cabbage/bank");
            resetHealTalkSession();
            phase = Phase.FIGHT;
            return 0;
        }
        if (healTalkFailStreak >= 4) {
            logThrottled("[Monk/heal] geen talkable monk → food/cabbage/bank");
            resetHealTalkSession();
            phase = Phase.FIGHT;
            return 0;
        }

        if (now - lastHealTalkMs < 700L) {
            setStatus("heal cool…");
            return random(250, 400);
        }

        Players.LocalSnap me = Players.snapshotLocal();
        if (me.present && (me.animating || me.moving) && healTalkNpcIndex >= 0) {
            setStatus("heal approach…");
            return random(250, 400);
        }

        INPC monk = findHealMonk(pos);
        if (monk == null) {
            healTalkFailStreak++;
            setStatus("geen rustige monk");
            logThrottled("[Monk/heal] geen Talk-to kandidaat (fail=" + healTalkFailStreak + ")");
            if (healTalkFailStreak >= 3) {
                resetHealTalkSession();
                phase = Phase.FIGHT;
                return 0;
            }
            return random(400, 700);
        }

        healTalkNpcIndex = monk.getIndex();
        WorldPoint tw = monk.getWorldLocation();
        if (tw != null && pos.distanceTo(tw) > 2
                && !InteractWalkHelper.isNearOrEnRoute(tw, 2)) {
            Movement.walkTo(tw);
            setStatus("→ heal-monk");
            return random(400, 700);
        }

        setStatus("Talk-to Monk (heal)");
        logThrottled("[Monk/heal] Talk-to idx=" + monk.getIndex() + " @ " + tw);
        boolean ok = AimInteractHelper.interactNpc(monk, "Talk-to");
        lastHealTalkMs = now;
        if (ok) {
            healTalkAwaitDialogUntilMs = now + 2_200L;
            AntiBan.get().markBotActivity();
            BotRuntime.logConsole("[Monk/heal] klik " + AimInteractHelper.getLastDetail()
                    + " — wacht dialog");
            return random(400, 650);
        }
        healTalkFailStreak++;
        BotRuntime.logConsole("[Monk/heal] fail " + AimInteractHelper.getLastDetail());
        return random(350, 600);
    }

    /** Rustige monk: Talk-to, niet in gevecht / geen healthbar. */
    private INPC findHealMonk(WorldPoint me) {
        java.util.List<INPC> candidates = NPCs.getAll(n -> {
            if (n == null || n.isDead()) {
                return false;
            }
            if (!n.hasAction("Talk-to")) {
                return false;
            }
            WorldPoint wp = n.getWorldLocation();
            if (wp == null || !inFightArea(wp)) {
                return false;
            }
            boolean isMonk = n.getId() == MONK_NPC_ID;
            String name = n.getName();
            if (!isMonk && (name == null || !name.equalsIgnoreCase("Monk"))) {
                return false;
            }
            return !isMonkBusyInCombat(n);
        });
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        // Één scene-BFS — geen Movement.calculateDistance (kaapt walker)
        Map<Long, Integer> dist = Reachable.walkDistances(me);
        INPC best = null;
        int bestSteps = Integer.MAX_VALUE;
        for (INPC n : candidates) {
            WorldPoint wp = n.getWorldLocation();
            if (wp == null) {
                continue;
            }
            int steps = Reachable.minStepsToAdjacent(dist, me, wp);
            if (steps >= Integer.MAX_VALUE / 4) {
                continue; // achter muur / onbereikbaar
            }
            if (steps < bestSteps) {
                bestSteps = steps;
                best = n;
            }
        }
        return best;
    }

    private boolean isMonkBusyInCombat(INPC n) {
        if (n == null) {
            return true;
        }
        try {
            if (n.getInteracting() != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        ActorState.NpcSnap snap = ActorState.ofIndex(n.getIndex());
        if (!snap.found || snap.dead) {
            return true;
        }
        if (snap.interactingWithLocal) {
            return true;
        }
        if (snap.interactingName != null && !snap.interactingName.isBlank()) {
            return true;
        }
        // Healthbar zichtbaar met schade = in gevecht
        if (snap.healthBarVisible && snap.healthScale > 0 && snap.healthRatio >= 0
                && snap.healthRatio < snap.healthScale) {
            return true;
        }
        // Onze huidige attack-target nooit Talk-to
        return lastTargetIndex >= 0 && n.getIndex() == lastTargetIndex;
    }

    private int tickCabbage(WorldPoint pos) {
        beginCabbageSessionIfNeeded();
        int have = countFood();
        int need = cabbageFoodAtStart + cabbagePickGoal;
        if (have >= need || Inventory.isFull()) {
            setStatus("cabbage klaar " + have + "/" + need + " → vol eten");
            logThrottled("[Monk/cabbage] klaar have=" + have + " need=" + need + " → TOP_UP");
            cabbageFoodAtStart = -1;
            phase = Phase.TOP_UP;
            return tickTopUp(pos, Combat.getCurrentHealth(), Combat.getHealthPercent());
        }

        // Wacht op inv-update na Pick — geen spam
        long now = System.currentTimeMillis();
        if (cabbageFoodAfterPick >= 0) {
            if (have > cabbageFoodAfterPick) {
                cabbageFoodAfterPick = -1;
                logThrottled("[Monk/cabbage] +1 inv → " + have + "/" + need);
            } else if (now - lastCabbagePickMs < 2_200L) {
                setStatus("cabbage wacht inv… " + have + "/" + need);
                return random(350, 550);
            } else {
                // Timeout: opnieuw mogen klikken
                cabbageFoodAfterPick = -1;
            }
        }
        if (now - lastCabbagePickMs < 900L) {
            setStatus("cabbage cool…");
            return random(300, 500);
        }

        Players.LocalSnap me = Players.snapshotLocal();
        if (me.present && (me.animating || me.moving)) {
            setStatus("cabbage anim/move…");
            return random(280, 450);
        }

        ITileObject cabbage = findCabbage();
        if (cabbage == null) {
            logThrottled("[Monk/cabbage] geen cabbage in scene");
            if (have > 0) {
                // Al iets geplukt → toch top-up i.p.v. leeg terug
                cabbageFoodAtStart = -1;
                phase = Phase.TOP_UP;
                return tickTopUp(pos, Combat.getCurrentHealth(), Combat.getHealthPercent());
            }
            if (allowBankSource() && BotRuntime.monkBankWhenNoFood) {
                cabbageFoodAtStart = -1;
                phase = Phase.BANK_WALK;
                return tickBankWalk(pos);
            }
            return requestLogoutNoFood("geen cabbage in scene");
        }
        WorldPoint cp = cabbage.getWorldLocation();
        if (cp != null) {
            if (InteractWalkHelper.isNearOrEnRoute(cp, 1)) {
                if (pos.distanceTo(cp) > 1) {
                    setStatus("→ cabbage… " + have + "/" + need);
                    return random(400, 700);
                }
            } else if (pos.distanceTo(cp) > 1) {
                Movement.walkTo(cp);
                return random(500, 900);
            }
        }

        setStatus("Pick cabbage " + have + "/" + need);
        logThrottled("[Monk/cabbage] Pick @ " + (cp != null ? cp.getX() + "," + cp.getY() : "?")
                + " " + have + "/" + need);
        try {
            cabbage.interact("Pick");
        } catch (Throwable t) {
            setStatus("cabbage Pick mislukt");
            return random(400, 700);
        }
        lastCabbagePickMs = now;
        cabbageFoodAfterPick = have;
        AntiBan.get().markBotActivity();
        return random(700, 1100);
    }

    /** Na cabbage/bank: eten tot (bijna) vol HP, daarna pas vechten. */
    private int tickTopUp(WorldPoint pos, int hp, double hpPct) {
        if (!hasFoodToEat()) {
            setStatus("top-up klaar (geen food)");
            logThrottled("[Monk/topup] geen food meer hp=" + hp + " → fight");
            phase = inFightArea(pos) ? Phase.FIGHT : Phase.RETURN;
            return random(300, 500);
        }
        if (isHpFull(hpPct)) {
            setStatus("top-up klaar HP vol");
            logThrottled("[Monk/topup] HP vol (" + (int) hpPct + "%) → fight");
            phase = inFightArea(pos) ? Phase.FIGHT : Phase.RETURN;
            return random(300, 500);
        }
        setStatus("top-up eten hp=" + hp + " " + (int) hpPct + "%");
        return tickEat(hp, hpPct);
    }

    private boolean isHpFull(double hpPct) {
        return hpPct >= 99.0;
    }

    private int tickBankWalk(WorldPoint pos) {
        if (!allowBankSource() || !BotRuntime.monkBankWhenNoFood) {
            if (allowCabbageSource()) {
                beginCabbageSessionIfNeeded();
                phase = Phase.CABBAGE;
                return tickCabbage(pos);
            }
            return requestLogoutNoFood("bank uitgeschakeld, geen cabbage");
        }
        if (Bank.isOpen()) {
            phase = Phase.BANKING;
            return tickBanking(pos);
        }
        setStatus("→ Edgeville bank (food)");
        logThrottled("[Monk/bank] walk → " + EDGEVILLE_BANK);
        if (pos.distanceTo(EDGEVILLE_BANK) <= 6) {
            if (BankHelper.tryOpenFullBank() || BankHelper.openSdkBankAndWait(2_500)) {
                phase = Phase.BANKING;
                return random(400, 700);
            }
            return random(600, 1000);
        }
        Movement.walkTo(EDGEVILLE_BANK);
        return random(500, 900);
    }

    private int tickBanking(WorldPoint pos) {
        if (!Bank.isOpen()) {
            if (pos.distanceTo(EDGEVILLE_BANK) > 8) {
                phase = Phase.BANK_WALK;
                return tickBankWalk(pos);
            }
            BankHelper.tryOpenFullBank();
            return random(500, 900);
        }
        setStatus("banken: food");
        // Stort alles behalve food
        try {
            Bank.depositAllExcept(BANK_FOOD_NAMES);
        } catch (Throwable ignored) {
            Bank.depositInventory();
        }
        sleep(280, 480);

        int needTotal = foodWithdrawTarget();
        int have = countFood();
        if (have >= needTotal) {
            try {
                Bank.close();
            } catch (Throwable ignored) {
            }
            bankNoFoodTries = 0;
            if (!isHpFull(Combat.getHealthPercent())) {
                phase = Phase.TOP_UP;
                logThrottled("[Monk/bank] food ok have=" + have + " → TOP_UP");
            } else {
                phase = Phase.RETURN;
                logThrottled("[Monk/bank] food ok have=" + have + " → return");
            }
            return random(400, 700);
        }

        String withdrawn = null;
        for (String foodName : BANK_FOOD_NAMES) {
            if (Bank.contains(foodName)) {
                int need = needTotal - countFood();
                Bank.withdraw(foodName, Math.max(1, need));
                withdrawn = foodName;
                break;
            }
        }
        if (withdrawn == null) {
            bankNoFoodTries++;
            try {
                Bank.close();
            } catch (Throwable ignored) {
            }
            if (allowCabbageSource() && bankNoFoodTries < 2) {
                setStatus("bank: geen food — cabbage fallback");
                logThrottled("[Monk/bank] geen food in bank → cabbage");
                beginCabbageSessionIfNeeded();
                phase = Phase.CABBAGE;
                return random(500, 800);
            }
            return requestLogoutNoFood("bank + cabbage zonder food");
        }
        bankNoFoodTries = 0;
        logThrottled("[Monk/bank] withdraw " + withdrawn);
        AntiBan.get().markBotActivity();
        return random(500, 900);
    }

    private int tickReturn(WorldPoint pos) {
        setStatus("→ monastery " + FIGHT_CENTER.getX() + "," + FIGHT_CENTER.getY());
        if (inFightArea(pos)) {
            phase = Phase.FIGHT;
            return random(300, 500);
        }
        InteractWalkHelper.ApproachResult ar =
                InteractWalkHelper.approachIfNeeded(FIGHT_CENTER, 3);
        if (ar == InteractWalkHelper.ApproachResult.EN_ROUTE
                || ar == InteractWalkHelper.ApproachResult.ARRIVED) {
            setStatus("→ monastery…");
            return random(400, 700);
        }
        logThrottled("[Monk/walk] return center");
        return random(500, 900);
    }

    private int tickFight(Players.LocalSnap me, WorldPoint pos) {
        ActorState.ThreatSnap threat = ActorState.threatToLocal();
        if (me.interacting || threat.underAttack) {
            int idx = me.interactingNpcIndex >= 0 ? me.interactingNpcIndex : threat.attackerIndex;
            if (idx >= 0) {
                lastTargetIndex = idx;
            }
            if (lastTargetIndex >= 0) {
                ActorState.NpcSnap npc = ActorState.ofIndex(lastTargetIndex);
                if (!npc.found || npc.dead) {
                    setStatus("target dood");
                    lastTargetIndex = -1;
                    return random(250, 450);
                }
                // Na Talk-to heal blijft interacting zonder healthbar → geen echte combat
                boolean realCombat = threat.underAttack
                        || (npc.healthBarVisible && npc.healthScale > 0 && npc.healthRatio >= 0)
                        || (npc.interactingWithLocal && npc.healthScale > 0 && npc.healthRatio >= 0);
                if (!realCombat) {
                    logThrottled("[Monk/fight] stale interact (geen combat-hp) → nieuwe Attack");
                    lastTargetIndex = -1;
                    approachTargetUntilMs = 0L;
                    // doorvallen naar target-pick
                } else {
                    approachTargetUntilMs = 0L;
                    setStatus("combat " + safe(npc.name) + " hp=" + npc.healthRatio + "/" + npc.healthScale);
                    return random(350, 600);
                }
            } else if (threat.underAttack) {
                setStatus("in combat…");
                return random(400, 700);
            }
        }

        long now = System.currentTimeMillis();
        // Attack-klik laat client naar monk lopen — geen center-walk / geen spam-Attack
        if (now < approachTargetUntilMs && (me.moving || me.walkDestination != null)) {
            setStatus("approach monk…");
            return random(280, 480);
        }
        if (now - lastAttackMs < 650L) {
            setStatus("anti-spam");
            return random(200, 400);
        }

        // Sticky: zelfde monk tot dood / buiten area — geen elke tick opnieuw A*
        INPC target = stickyMonkIfValid(pos);
        if (target == null) {
            target = findMonk(pos);
        }
        if (target == null) {
            setStatus("geen monk in area");
            logThrottled("[Monk/fight] geen monk in r=" + FIGHT_RADIUS);
            lastTargetIndex = -1;
            // Niet naar center als we nog van een Attack onderweg zijn
            if (now >= approachTargetUntilMs && pos.distanceTo(FIGHT_CENTER) > 4
                    && !InteractWalkHelper.isNearOrEnRoute(FIGHT_CENTER, 3)) {
                Movement.walkTo(FIGHT_CENTER);
            }
            return random(500, 900);
        }

        WorldPoint tw = target.getWorldLocation();
        // Al onderweg naar deze monk: wachten, geen tweede Attack die pad reset
        if (tw != null && InteractWalkHelper.isNearOrEnRoute(tw, 2)
                && (me.moving || me.walkDestination != null)
                && now - lastAttackMs < 2_500L) {
            setStatus("approach " + safe(target.getName()));
            return random(280, 480);
        }

        String name = target.getName() != null ? target.getName() : "Monk";
        setStatus("Attack " + name);
        logThrottled("[Monk/attack] " + name + " idx=" + target.getIndex()
                + " @ " + target.getWorldLocation());
        // Wis eventueel oud pad (niet naar andere monk blijven lopen)
        try {
            MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        boolean ok = AimInteractHelper.interactNpc(target, "Attack");
        lastAttackMs = System.currentTimeMillis();
        lastTargetIndex = target.getIndex();
        if (ok) {
            approachTargetUntilMs = lastAttackMs + 4_000L;
            AntiBan.get().markBotActivity();
            BotRuntime.logConsole("[Monk/attack] ok " + AimInteractHelper.getLastDetail());
            return random(700, 1100);
        }
        setStatus("attack mislukt");
        BotRuntime.logConsole("[Monk/attack] fail " + AimInteractHelper.getLastDetail());
        return random(400, 700);
    }

    /** Houd huidige target vast zolang die leeft en in area is. */
    private INPC stickyMonkIfValid(WorldPoint me) {
        if (lastTargetIndex < 0) {
            return null;
        }
        ActorState.NpcSnap snap = ActorState.ofIndex(lastTargetIndex);
        if (!snap.found || snap.dead) {
            lastTargetIndex = -1;
            return null;
        }
        if (snap.worldLocation == null || !inFightArea(snap.worldLocation)) {
            lastTargetIndex = -1;
            return null;
        }
        INPC live = NPCs.getNearest(n -> n != null && n.getIndex() == lastTargetIndex);
        if (live == null || live.isDead() || !live.hasAction("Attack")) {
            lastTargetIndex = -1;
            return null;
        }
        return live;
    }

    private INPC findMonk(WorldPoint me) {
        java.util.List<INPC> candidates = NPCs.getAll(n -> {
            if (n == null || n.isDead()) {
                return false;
            }
            if (!n.hasAction("Attack")) {
                return false;
            }
            WorldPoint wp = n.getWorldLocation();
            if (wp == null || !inFightArea(wp)) {
                return false;
            }
            if (n.getId() == MONK_NPC_ID) {
                return true;
            }
            String name = n.getName();
            return name != null && name.equalsIgnoreCase("Monk");
        });
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        // Één scene-BFS (muren) — geen Walker/MovementHelper (die kaapte het pad)
        Map<Long, Integer> dist = Reachable.walkDistances(me);
        INPC best = null;
        int bestSteps = Integer.MAX_VALUE;
        int bestCrow = Integer.MAX_VALUE;
        for (INPC n : candidates) {
            WorldPoint wp = n.getWorldLocation();
            if (wp == null) {
                continue;
            }
            int crow = me != null ? me.distanceTo(wp) : Integer.MAX_VALUE;
            int steps = Reachable.minStepsToAdjacent(dist, me, wp);
            if (steps >= Integer.MAX_VALUE / 4) {
                continue; // achter muur / onbereikbaar
            }
            if (steps < bestSteps || (steps == bestSteps && crow < bestCrow)) {
                bestSteps = steps;
                bestCrow = crow;
                best = n;
            }
        }
        if (best != null) {
            logThrottled("[Monk/target] sceneSteps=" + bestSteps + " crow=" + bestCrow
                    + " @ " + best.getWorldLocation());
        }
        return best;
    }

    private ITileObject findCabbage() {
        return TileObjects.getNearest(o -> {
            if (o == null || o.getName() == null) {
                return false;
            }
            if (!o.getName().equalsIgnoreCase("Cabbage")) {
                return false;
            }
            return o.hasAction("Pick");
        });
    }

    private boolean cabbageAvailableNear(WorldPoint me) {
        ITileObject c = findCabbage();
        if (c == null || c.getWorldLocation() == null || me == null) {
            // Cabbage-tile is vast — als we dichtbij monastery zijn, probeer cabbage-fase
            return me != null && me.distanceTo(CABBAGE_TILE) <= 40;
        }
        return me.distanceTo(c.getWorldLocation()) <= 40;
    }

    private boolean inFightArea(WorldPoint p) {
        if (p == null || p.getPlane() != FIGHT_CENTER.getPlane()) {
            return false;
        }
        return Math.abs(p.getX() - FIGHT_CENTER.getX()) <= FIGHT_RADIUS
                && Math.abs(p.getY() - FIGHT_CENTER.getY()) <= FIGHT_RADIUS;
    }

    private boolean shouldEat(double hpPct, int eatPct) {
        return hpPct <= eatPct;
    }

    private int eatPercent() {
        return Math.max(5, Math.min(90, BotRuntime.monkEatPercent));
    }

    private int criticalHp() {
        return Math.max(1, Math.min(15, BotRuntime.monkCriticalHp));
    }

    private int foodWithdrawTarget() {
        return Math.max(1, Math.min(28, BotRuntime.monkFoodAmount));
    }

    private int cabbagePickAmount() {
        return Math.max(1, Math.min(28, BotRuntime.monkCabbagePickAmount));
    }

    private String foodSource() {
        String s = BotRuntime.monkFoodSource;
        if (s == null || s.isBlank()) {
            return "AUTO";
        }
        return s.trim().toUpperCase();
    }

    private boolean allowCabbageSource() {
        if (!BotRuntime.monkCabbagePickEnabled) {
            return false;
        }
        String s = foodSource();
        return "AUTO".equals(s) || "CABBAGE".equals(s);
    }

    private boolean allowBankSource() {
        String s = foodSource();
        return "AUTO".equals(s) || "BANK".equals(s);
    }

    private boolean hasFoodToEat() {
        return findFood() != null;
    }

    private int countFood() {
        int n = 0;
        for (IInventoryItem item : Inventory.getAll()) {
            if (item != null && isFoodItem(item)) {
                n += Math.max(1, item.getQuantity());
            }
        }
        return n;
    }

    private IInventoryItem findFood() {
        return Inventory.getFirst(this::isFoodItem);
    }

    private boolean isFoodItem(IInventoryItem item) {
        if (item == null || item.getName() == null) {
            return false;
        }
        if (item.hasAction("Eat") || item.hasAction("Drink")) {
            return true;
        }
        String n = item.getName();
        for (String f : BANK_FOOD_NAMES) {
            if (f.equalsIgnoreCase(n)) {
                return true;
            }
        }
        return false;
    }

    private void ensureRunOn() {
        try {
            if (!Movement.isRunEnabled()) {
                Movement.toggleRun();
            }
        } catch (Throwable ignored) {
        }
    }

    private void setStatus(String s) {
        BotRuntime.monkStatus = s != null ? s : "?";
        long now = System.currentTimeMillis();
        if (now - lastStatusMs > 1500L) {
            lastStatusMs = now;
            // sidebar live label leest monkStatus
        }
    }

    private void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastConsoleMs < 1600L) {
            return;
        }
        lastConsoleMs = now;
        BotRuntime.logConsole(msg);
    }

    private static String safe(String s) {
        return s != null ? s : "?";
    }

    private static int random(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static void sleep(int min, int max) {
        try {
            Thread.sleep(random(min, max));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
