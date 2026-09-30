package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankSession;
import net.storm.sdk.items.BankSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * CombatBot mining gear-prep: nearest F2P-bank → beste bruikbare pickaxe → rest storten.
 * Geen GE-koop (supply-chain: alleen bank).
 * Bank-walk = {@link StarWalk} (zelfde als ImpWalk / steden-cirkel).
 */
final class StarGearPrep {

    private static final long UPGRADE_CHECK_MS = 2500L;
    private static final long MISSING_RETRY_MS = 12_000L;
    private static final int BANK_WALK_FAIL_ABORT = 3;
    private static final long BANK_UNREACHABLE_SUPPRESS_MS = 12 * 60 * 1000L;

    private final BankSession bankSession = new BankSession();
    private boolean bankSessionArmed;
    private long lastUpgradeCheckMs;
    private boolean cachedUpgrade;
    private long missingPickUntilMs;
    private long spareSuppressUntilMs;
    private long travelKitSuppressUntilMs;
    private int dumpPasses;
    private int unequipAttempts;
    private int bankWalkFails;
    private WorldPoint lastBankDest;
    /** Vast bank-doel tijdens een trip — voorkomt flip Lumb↔Draynor die het pad cleart. */
    private WorldPoint stickyBankDest;
    private long lastLogMs;
    private String lastLog = "";
    private String status = "gear prep";

    void reset() {
        bankSessionArmed = false;
        bankSession.reset();
        lastUpgradeCheckMs = 0L;
        cachedUpgrade = false;
        missingPickUntilMs = 0L;
        spareSuppressUntilMs = 0L;
        travelKitSuppressUntilMs = 0L;
        dumpPasses = 0;
        unequipAttempts = 0;
        bankWalkFails = 0;
        lastBankDest = null;
        status = "gear prep";
    }

    String status() {
        return status;
    }

    boolean needs() {
        if (bankSessionArmed) {
            return true;
        }
        try {
            if (Bank.isOpen()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        long now = System.currentTimeMillis();
        // Bank-pad dood: niet opnieuw gear-loop tot suppress voorbij (anders LoopHost-stall)
        if (now < travelKitSuppressUntilMs && StarPickaxes.onPerson()) {
            return false;
        }
        if (now < missingPickUntilMs && !StarPickaxes.onPerson()) {
            return true;
        }
        if (now < missingPickUntilMs && StarPickaxes.onPerson()) {
            return StarPickaxes.hasForeignItems()
                    || (now >= spareSuppressUntilMs && StarPickaxes.hasSpareBesidesBest())
                    || needsTravelKit();
        }
        if (!StarPickaxes.onPerson()) {
            return true;
        }
        if (StarPickaxes.hasForeignItems()) {
            return true;
        }
        if (needsTravelKit()) {
            return true;
        }
        if (now >= spareSuppressUntilMs && StarPickaxes.hasSpareBesidesBest()) {
            return true;
        }
        return needsUpgradeThrottled(now);
    }

    /** Tele-kit of coins voor poort — mag banken ook als pickaxe al op zak. */
    boolean needsTravelKit() {
        if (System.currentTimeMillis() < travelKitSuppressUntilMs) {
            return false;
        }
        // Altijd ≥10 coins (Al Kharid-poort west↔oost, bv. Emir's Arena)
        if (coinCount() < 10) {
            return true;
        }
        if (!StarMinerPlugin.teleportsEnabled) {
            return false;
        }
        if (!StarTeleports.canCastAnySpell()) {
            return false;
        }
        return !StarTeleports.hasTravelKit();
    }

    private static int coinCount() {
        try {
            return net.storm.sdk.items.Inventory.getCount(true, 995);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Abort bank-trip: pad dood → star verder (niet LoopHost-stall). */
    private void abortBankUnreachable(String why) {
        long now = System.currentTimeMillis();
        travelKitSuppressUntilMs = now + BANK_UNREACHABLE_SUPPRESS_MS;
        spareSuppressUntilMs = Math.max(spareSuppressUntilMs, travelKitSuppressUntilMs);
        cachedUpgrade = false;
        lastUpgradeCheckMs = now;
        bankSessionArmed = false;
        bankSession.reset();
        bankWalkFails = 0;
        lastBankDest = null;
        stickyBankDest = null;
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        status = "bank onbereikbaar";
        log("ABORT bank: " + why + " — verder zonder coins/tele ("
                + (BANK_UNREACHABLE_SUPPRESS_MS / 60_000L) + " min)");
    }

    int tick() {
        long now = System.currentTimeMillis();
        if (now < missingPickUntilMs && !StarPickaxes.onPerson()) {
            status = "geen pickaxe in bank — wacht";
            log("geen pickaxe in bank (geen GE) — deposit er één of koop handmatig");
            return 1600;
        }
        try {
            if (Bank.isOpen()) {
                BankSnapshot.captureIfOpenThrottled();
            }
        } catch (Throwable ignored) {
        }
        if (Bank.isOpen() && !bankSessionArmed && StarPickaxes.onPerson()
                && !StarPickaxes.hasForeignItems() && !StarPickaxes.hasSpareBesidesBest()
                && !needsTravelKit()) {
            Bank.close();
            status = "bank sluiten";
            log("sluit bank (pickaxe op zak)");
            return Rand.nextInt(400, 700);
        }
        if (!Bank.isOpen()) {
            unequipAttempts = 0;
            if (!bankSessionArmed) {
                int walk = tickWalkToBank();
                if (!Bank.isOpen()) {
                    // Abort: needs() wordt false via travelKitSuppress
                    if (System.currentTimeMillis() < travelKitSuppressUntilMs
                            && StarPickaxes.onPerson()) {
                        return Rand.nextInt(200, 360);
                    }
                    return walk;
                }
            }
        }
        if (Bank.isOpen() && unequipAttempts < 3 && StarPickaxes.tryUnequipWorsePickaxe()) {
            unequipAttempts++;
            status = "unequip oude pickaxe";
            log("unequip slechtere pickaxe");
            return Rand.nextInt(400, 700);
        }
        if (!bankSessionArmed) {
            armBankSession(false);
            bankSessionArmed = true;
        }
        BankSession.Status st = bankSession.tick();
        status = "bank " + st.phase + ": " + st.detail;
        if (st.done()) {
            if (st.result == BankSession.Result.OK || st.result == BankSession.Result.PARTIAL) {
                boolean leftover = StarPickaxes.hasSpareBesidesBest() || StarPickaxes.hasForeignItems();
                if (leftover && dumpPasses < 2) {
                    dumpPasses++;
                    armBankSession(true);
                    bankSessionArmed = true;
                    log("dump rest, hou " + StarPickaxes.label(StarPickaxes.bestAvailable()));
                    return st.delayMs > 0 ? st.delayMs : Rand.nextInt(400, 700);
                }
                if (leftover) {
                    spareSuppressUntilMs = now + 2 * 60 * 1000L;
                    log("dump-rest skip (equipped leftover) — verder");
                }
                dumpPasses = 0;
                StarPickaxes.Pick best = StarPickaxes.bestAvailable();
                StarPickaxes.tryWieldIfAllowed(best != null ? best.name : null);
            } else {
                dumpPasses = 0;
            }
            bankSessionArmed = false;
            bankSession.reset();
            if (st.result == BankSession.Result.NEEDS_GE) {
                if (StarPickaxes.onPerson()) {
                    missingPickUntilMs = now + 5 * 60 * 1000L;
                    if (needsTravelKit()) {
                        travelKitSuppressUntilMs = now + 5 * 60 * 1000L;
                        log("tele/coins niet in bank — verder zonder (geen GE)");
                    }
                    status = "geen betere pickaxe in bank → door met huidige";
                    log("NEEDS_GE " + st.missingItem + " — geen GE, verder met "
                            + StarPickaxes.label(StarPickaxes.currentOnPerson()));
                } else {
                    missingPickUntilMs = now + MISSING_RETRY_MS;
                    status = "geen pickaxe in bank";
                    log("NEEDS_GE " + st.missingItem + " — geen pickaxe in bank");
                }
            } else if (st.result == BankSession.Result.FAIL) {
                log("bank FAIL");
            } else {
                if (needsTravelKit()) {
                    travelKitSuppressUntilMs = now + 3 * 60 * 1000L;
                    log("tele/coins nog tekort na bank — verder, retry later");
                }
                log("klaar pick=" + StarPickaxes.label(StarPickaxes.currentOnPerson()));
            }
        }
        return st.delayMs > 0 ? st.delayMs : Rand.nextInt(500, 900);
    }

    private boolean needsUpgradeThrottled(long now) {
        if (now - lastUpgradeCheckMs < UPGRADE_CHECK_MS) {
            return cachedUpgrade;
        }
        lastUpgradeCheckMs = now;
        cachedUpgrade = StarPickaxes.needsUpgradeFromBank();
        if (cachedUpgrade) {
            log("upgrade " + StarPickaxes.label(StarPickaxes.currentOnPerson())
                    + " → " + StarPickaxes.label(StarPickaxes.bestAvailable()));
        }
        return cachedUpgrade;
    }

    private void armBankSession(boolean dumpOnly) {
        StarPickaxes.Pick best = StarPickaxes.bestAvailable();
        if (best == null) {
            best = StarPickaxes.bronzeFallback();
        }
        int[] keepIds = StarPickaxes.idsForName(best.name);
        List<BankSession.Requirement> reqs = new ArrayList<>();
        if (!dumpOnly) {
            boolean haveBest = false;
            for (StarPickaxes.Pick p : StarPickaxes.PICKS) {
                if (p.name.equalsIgnoreCase(best.name) && StarPickaxes.held(p)) {
                    haveBest = true;
                    break;
                }
            }
            if (!haveBest) {
                reqs.add(BankSession.Requirement.byId(best.name, best.itemId, 1, 1));
            }
            addTeleRequirements(reqs);
            addCoinsForGate(reqs);
        }
        boolean wield = StarPickaxes.canWield(best.name);
        // Keep pick + tele runes/staff names on deposit
        List<String> keepNames = new ArrayList<>();
        keepNames.add(best.name);
        if (StarMinerPlugin.teleportsEnabled) {
            keepNames.add("Staff of air");
            keepNames.add("Air battlestaff");
            keepNames.add("Mystic air staff");
            keepNames.add("Air rune");
            keepNames.add("Law rune");
            keepNames.add("Fire rune");
            keepNames.add("Water rune");
            keepNames.add("Earth rune");
        }
        keepNames.add("Brown apron");
        keepNames.add("Crafting cape");
        keepNames.add("Crafting cape(t)");
        keepNames.add("Coins");
        int[] keepAll = mergeKeepIds(keepIds);
        bankSession.configure(keepNames.toArray(new String[0]), keepAll, reqs, true, wield && !dumpOnly);
        log("sessie dumpOnly=" + dumpOnly + " pick=" + best.name + " id=" + best.itemId
                + " wield=" + wield + " reqs=" + reqs.size());
    }

    private static void addTeleRequirements(List<BankSession.Requirement> reqs) {
        if (!StarMinerPlugin.teleportsEnabled) {
            return;
        }
        // Bank is open bij arm — wel contains-check zodat ontbrekende staff geen NEEDS_GE-loop geeft
        boolean hasStaff = StarTeleports.hasAirStaff();
        if (!hasStaff) {
            boolean tookStaff = false;
            try {
                if (Bank.contains(StarTeleports.STAFF_OF_AIR) || Bank.contains("Staff of air")) {
                    reqs.add(BankSession.Requirement.byId("Staff of air", StarTeleports.STAFF_OF_AIR, 1, 1));
                    tookStaff = true;
                } else if (Bank.contains(StarTeleports.AIR_BATTLESTAFF) || Bank.contains("Air battlestaff")) {
                    reqs.add(BankSession.Requirement.byId("Air battlestaff", StarTeleports.AIR_BATTLESTAFF, 1, 1));
                    tookStaff = true;
                } else if (Bank.contains(StarTeleports.MYSTIC_AIR_STAFF) || Bank.contains("Mystic air staff")) {
                    reqs.add(BankSession.Requirement.byId("Mystic air staff", StarTeleports.MYSTIC_AIR_STAFF, 1, 1));
                    tookStaff = true;
                }
            } catch (Throwable ignored) {
            }
            if (!tookStaff && StarTeleports.airRuneCount() < 15) {
                reqs.add(BankSession.Requirement.byId("Air rune", StarTeleports.AIR_RUNE, 30, 30));
            }
        }
        addRuneNeed(reqs, "Law rune", StarTeleports.LAW_RUNE, 5, 10);
        addRuneNeed(reqs, "Fire rune", StarTeleports.FIRE_RUNE, 3, 10);
        addRuneNeed(reqs, "Earth rune", StarTeleports.EARTH_RUNE, 3, 10);
        addRuneNeed(reqs, "Water rune", StarTeleports.WATER_RUNE, 3, 10);
        addCraftingGuildEntry(reqs);
    }

    /** Al Kharid-poort: all coins (min. 10) bij elke bank-sessie. */
    private static void addCoinsForGate(List<BankSession.Requirement> reqs) {
        int have = 0;
        try {
            have = net.storm.sdk.items.Inventory.getCount(true, 995);
        } catch (Throwable ignored) {
        }
        if (have >= 10) {
            return;
        }
        reqs.add(BankSession.Requirement.byId("Coins", 995, 10, 1000));
    }

    private static void addRuneNeed(List<BankSession.Requirement> reqs, String name, int id,
                                    int minHave, int withdraw) {
        if (StarTeleports.runeCount(id) >= minHave) {
            return;
        }
        try {
            if (Bank.isOpen() && !(Bank.contains(id) || Bank.contains(name))) {
                return;
            }
        } catch (Throwable ignored) {
        }
        reqs.add(BankSession.Requirement.byId(name, id, withdraw, withdraw));
    }

    private static void addCraftingGuildEntry(List<BankSession.Requirement> reqs) {
        if (StarCraftingGuild.craftingLevel() < 40) {
            return;
        }
        if (StarCraftingGuild.hasEntryItem()) {
            return;
        }
        try {
            if (Bank.isOpen() && !(Bank.contains(StarCraftingGuild.BROWN_APRON)
                    || Bank.contains("Brown apron"))) {
                return;
            }
        } catch (Throwable ignored) {
        }
        reqs.add(BankSession.Requirement.byId("Brown apron", StarCraftingGuild.BROWN_APRON, 1, 1));
    }

    private static int[] mergeKeepIds(int[] pickIds) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (pickIds != null) {
            for (int id : pickIds) {
                ids.add(id);
            }
        }
        if (StarMinerPlugin.teleportsEnabled) {
            ids.add(StarTeleports.STAFF_OF_AIR);
            ids.add(StarTeleports.AIR_BATTLESTAFF);
            ids.add(StarTeleports.MYSTIC_AIR_STAFF);
            ids.add(StarTeleports.AIR_RUNE);
            ids.add(StarTeleports.LAW_RUNE);
            ids.add(StarTeleports.FIRE_RUNE);
            ids.add(StarTeleports.WATER_RUNE);
            ids.add(StarTeleports.EARTH_RUNE);
        }
        ids.add(StarCraftingGuild.BROWN_APRON);
        ids.add(StarCraftingGuild.CRAFTING_CAPE);
        ids.add(StarCraftingGuild.CRAFTING_CAPE_T);
        ids.add(995); // Coins — Al Kharid gate
        int[] out = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    /** Al Kharid mine / oost-gate: bank via {@link BankHelper} (zelfde-kant bij poort dicht). */
    private static WorldPoint resolveBankPoint(WorldPoint pos) {
        return normalizeLumbridgeBankDest(BankHelper.getNearestF2pBankPoint(pos), pos);
    }

    /**
     * Lumb bank zit op plane 2. Nooit plane-0 sticky houden terwijl we al boven zijn —
     * anders: trap omhoog → PlaneChange Bottom-floor 2→0 → trap omhoog.
     */
    private static final WorldPoint LUMB_BANK_FLOOR = new WorldPoint(3208, 3220, 2);
    private static final WorldPoint LUMB_STAIRS_GROUND = new WorldPoint(3205, 3208, 0);

    private static WorldPoint normalizeLumbridgeBankDest(WorldPoint dest, WorldPoint pos) {
        if (dest == null) {
            return null;
        }
        if (!isLumbridgeCastleXy(dest) && !isLumbridgeStairsTile(dest)) {
            return dest;
        }
        // Ver weg: loop eerst over de grond naar de trap (geen plane-2 dest → vroege climb)
        if (pos != null && pos.getPlane() < 2) {
            boolean near = false;
            try {
                near = BankHelper.isNearLumbridgeCastleBankRoute(pos)
                        || pos.distanceTo(LUMB_STAIRS_GROUND) <= 10;
            } catch (Throwable ignored) {
            }
            if (!near) {
                return LUMB_STAIRS_GROUND;
            }
        }
        // Dichtbij of al boven: altijd bank-floor plane 2
        return LUMB_BANK_FLOOR;
    }

    private static boolean isLumbridgeStairsTile(WorldPoint p) {
        return p != null && p.getX() == 3205 && p.getY() == 3208;
    }

    private static boolean isLumbridgeCastleXy(WorldPoint p) {
        return p != null
                && p.getX() >= 3190 && p.getX() <= 3225
                && p.getY() >= 3195 && p.getY() <= 3235;
    }

    private int tickWalkToBank() {
        Players.LocalSnap me = Players.snapshotLocal();
        if (me == null || !me.present || me.worldLocation == null) {
            status = "→ bank (geen pos)";
            return 1000;
        }
        WorldPoint pos = me.worldLocation;

        if (Bank.isOpen()) {
            bankWalkFails = 0;
            stickyBankDest = null;
            status = "bank open";
            return Rand.nextInt(400, 700);
        }

        // Sticky upgraden: plane-0 Lumb sticky → floor 2 zodra we dichtbij/boven zijn
        if (stickyBankDest != null) {
            stickyBankDest = normalizeLumbridgeBankDest(stickyBankDest, pos);
        }

        try {
            boolean lumbTrip = stickyBankDest == null || isLumbridgeCastleXy(stickyBankDest)
                    || isLumbridgeStairsTile(stickyBankDest);
            if (lumbTrip && BankHelper.ensureLumbridgeCastleStairProgress()) {
                // Zeker sticky op floor 2 zodat we na climb niet 2→0 gaan
                stickyBankDest = LUMB_BANK_FLOOR;
                status = "→ Lumb trap";
                log("Lumb trap omhoog → sticky bank floor p2");
                return Rand.nextInt(800, 1400);
            }
        } catch (Throwable t) {
            log("trap " + t.getClass().getSimpleName());
        }

        WorldPoint openDest = stickyBankDest != null ? stickyBankDest : resolveBankPoint(pos);
        if (isNearBankDest(pos, openDest)) {
            if (BankHelper.tryOpenFullBank()) {
                bankWalkFails = 0;
                stickyBankDest = null;
                status = "bank openen";
                log("open @bank dist≤12");
                return Rand.nextInt(600, 1000);
            }
            status = "bank open retry";
            return Rand.nextInt(500, 900);
        }

        WorldPoint dest = chooseBankDest(pos);
        dest = normalizeLumbridgeBankDest(dest, pos);
        lastBankDest = dest;
        StarWalk.Result wr = StarWalk.toward(pos, dest, 2, "→ bank");
        status = wr.status;
        if (wr.arrived) {
            bankWalkFails = 0;
            stickyBankDest = null;
            return wr.delayMs;
        }
        if (wr.failed) {
            bankWalkFails++;
            log("bank-walk fail ×" + bankWalkFails + " → "
                    + (dest != null ? dest.getX() + "," + dest.getY() + ",p" + dest.getPlane() : "?"));
            if (allowDraynorFallback(pos) && (isLumbridgeCastleXy(dest) || isLumbridgeStairsTile(dest))) {
                WorldPoint dray = BankHelper.DRAYNOR_BANK_AREA_CENTER;
                if (dray != null
                        && !net.storm.sdk.movement.pathfinder.AlKharidGate.crossesGateWithoutPass(pos, dray)) {
                    stickyBankDest = dray;
                    log("bank-fallback sticky → Draynor (Lumb pad leeg)");
                } else {
                    stickyBankDest = null;
                }
            } else if (!allowDraynorFallback(pos)) {
                stickyBankDest = LUMB_BANK_FLOOR;
                log("geen Draynor-fallback (Lumb climb) — sticky bank floor");
            } else {
                stickyBankDest = null;
            }
            try {
                net.storm.sdk.movement.MovementHelper.clearPath();
            } catch (Throwable ignored) {
            }
            if (bankWalkFails >= BANK_WALK_FAIL_ABORT) {
                abortBankUnreachable("pad leeg/stil ×" + bankWalkFails);
                return Rand.nextInt(200, 360);
            }
            return Rand.nextInt(400, 700);
        }
        stickyBankDest = dest;
        return wr.delayMs;
    }

    private WorldPoint chooseBankDest(WorldPoint pos) {
        if (stickyBankDest != null) {
            return normalizeLumbridgeBankDest(stickyBankDest, pos);
        }
        WorldPoint dest = resolveBankPoint(pos);
        if (bankWalkFails >= 1 && allowDraynorFallback(pos)
                && (isLumbridgeCastleXy(dest) || isLumbridgeStairsTile(dest))) {
            WorldPoint dray = BankHelper.DRAYNOR_BANK_AREA_CENTER;
            if (dray != null
                    && !net.storm.sdk.movement.pathfinder.AlKharidGate.crossesGateWithoutPass(pos, dray)) {
                stickyBankDest = dray;
                log("bank-fallback → Draynor (na Lumb-fail)");
                return dray;
            }
        }
        return dest;
    }

    /** Geen Draynor terwijl we in Lumb kasteel klimmen of al op bank-floor staan. */
    private static boolean allowDraynorFallback(WorldPoint pos) {
        if (pos == null) {
            return true;
        }
        try {
            if (BankHelper.isInLumbridgeCastleBuilding(pos) && pos.getPlane() > 0) {
                return false;
            }
            if (BankHelper.isNearLumbridgeCastleBankRoute(pos) && pos.getPlane() < 2) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static boolean isNearBankDest(WorldPoint pos, WorldPoint dest) {
        if (pos == null || dest == null) {
            return false;
        }
        if (pos.getPlane() != dest.getPlane()) {
            return false;
        }
        return pos.distanceTo(dest) <= 12;
    }

    private static boolean isNearAnyBank(WorldPoint pos) {
        if (pos == null) {
            return false;
        }
        try {
            WorldPoint nearest = resolveBankPoint(pos);
            return isNearBankDest(pos, nearest);
        } catch (Throwable t) {
            return false;
        }
    }

    private void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/bank] " + msg);
    }
}
