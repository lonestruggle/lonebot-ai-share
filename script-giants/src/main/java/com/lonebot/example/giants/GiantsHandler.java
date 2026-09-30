package com.lonebot.example.giants;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Combat;
import net.storm.sdk.game.Game;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.GrandExchange;
import net.storm.sdk.items.HumanBanking;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.utils.AntiBan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hill Giants loop — CombatBot status order:
 * eat → bury → no-food bank → loot → GE key → ground key → entrance → dungeon
 * → hunt zone → bank (food/runes/full) → bank actions → return → combat.
 */
public final class GiantsHandler {

    public volatile boolean enabled;
    public volatile GiantsTypes.GiantsCombatStyle style = GiantsTypes.GiantsCombatStyle.MELEE;
    public volatile GiantsTypes.GiantsMageSpell mageSpell = GiantsTypes.GiantsMageSpell.WIND_STRIKE;
    public volatile String lootCsv = GiantsLocations.DEFAULT_LOOT_CSV;
    public volatile int huntRadius = GiantsLocations.HUNT_RADIUS;
    public volatile int eatPercent = 50;
    public volatile int foodAmount = 10;
    public volatile int foodBankThreshold = 1;
    public volatile boolean magicAutoUpdate;
    public volatile String lootPickupMode = "AUTO";
    public volatile boolean lootPickupStrict;

    private String status = "idle";
    private boolean bankingTrip;
    private boolean geKeyTrip;
    private boolean groundKeyTrip;
    private boolean checkedBankForKey;
    private boolean bankAfterKeyPickup;
    private boolean afterGeBuy;
    private boolean mageAutocastReady;
    private INPC currentTarget;
    private boolean targetWasAlive;
    private long lastAttackMs;
    private long lastKillMs;
    private long lastCombatAnimMs;
    private long hopWaitUntilMs;
    private boolean hopIssued;
    private WorldPoint bankDest;

    public String getStatus() {
        return status;
    }

    public List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Style: " + style);
        lines.add("Key: " + (GiantsKey.hasOnPerson() ? "op zak" : (checkedBankForKey ? "nee" : "?")));
        lines.add("Food: " + GiantsLoot.foodCount());
        lines.add("Bank: " + (bankingTrip ? "trip" : "-"));
        if (geKeyTrip) {
            lines.add("Key: GE");
        }
        if (groundKeyTrip) {
            lines.add("Key: ground");
        }
        return lines;
    }

    public void reset() {
        bankingTrip = false;
        geKeyTrip = false;
        groundKeyTrip = false;
        checkedBankForKey = false;
        bankAfterKeyPickup = false;
        afterGeBuy = false;
        mageAutocastReady = false;
        currentTarget = null;
        targetWasAlive = false;
        hopWaitUntilMs = 0L;
        hopIssued = false;
        bankDest = null;
        status = "idle";
    }

    public int loop() {
        if (!Game.isLoggedIn()) {
            setStatus("niet ingelogd");
            return 1000;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me == null || !me.present || me.worldLocation == null) {
            setStatus("geen speler");
            return 600;
        }
        WorldPoint pos = me.worldLocation;
        if (me.animating || me.animation > 0) {
            lastCombatAnimMs = System.currentTimeMillis();
        }

        int anti = AntiBan.get().check();
        if (anti > 0 && !bankingTrip && !geKeyTrip) {
            setStatus("antiban: " + AntiBan.get().getLastActionLabel());
            return Math.min(anti, 2500);
        }

        try {
            net.storm.sdk.interact.GroundLootPickupHelper.setPickupModeFromName(
                    lootPickupMode, lootPickupStrict);
        } catch (Throwable ignored) {
        }

        ensureRun();

        int equip = GiantsGear.tryEquipOne(style);
        if (equip > 0 && !Bank.isOpen()) {
            setStatus("equip");
            return equip;
        }

        // 1. Eat / drink
        if (Combat.getHealthPercent() < Math.max(5, eatPercent) && GiantsLoot.hasFood()) {
            setStatus("eten");
            int d = GiantsLoot.eatOrDrink();
            if (d > 0) {
                return d;
            }
        }

        // 2. Bury bones
        int bury = GiantsLoot.buryBones();
        if (bury > 0) {
            setStatus("begraven");
            return bury;
        }

        // 3. Own food-bank flow (independent of global bankWhenNoFood)
        if (!GiantsLoot.hasFood() || GiantsLoot.foodCount() < Math.max(1, foodBankThreshold)) {
            if (!bankingTrip) {
                GiantsLog.action("food", "geen food → bank");
            }
            bankingTrip = true;
        }

        maybeRegisterKill();

        // 4. After a kill, wait briefly then pick loot
        if (!bankingTrip && lastKillMs > 0L) {
            long since = System.currentTimeMillis() - lastKillMs;
            if (since < 450L) {
                setStatus("loot wacht");
                return ThreadLocalRandom.current().nextInt(80, 160);
            }
            ITileItem loot = GiantsLoot.findLoot(pos, 10, lootCsv);
            if (loot != null) {
                setStatus("loot " + loot.getName());
                return GiantsLoot.pickup(loot);
            }
        }
        if (!bankingTrip) {
            ITileItem loot = GiantsLoot.findLoot(pos, 6, lootCsv);
            if (loot != null && !GiantsCombat.meInCombat()) {
                setStatus("loot " + loot.getName());
                return GiantsLoot.pickup(loot);
            }
        }

        int junk = GiantsLoot.dropJunk(style, lootCsv);
        if (junk > 0) {
            setStatus("drop junk");
            return junk;
        }

        // 5–6. Brass key
        if (!GiantsKey.hasOnPerson()) {
            int key = handleKey(pos);
            if (key > 0) {
                return key;
            }
        } else {
            geKeyTrip = false;
            groundKeyTrip = false;
        }

        // 10–11. Bank when food/runes low or inv full
        if (!bankingTrip && (GiantsGear.suppliesLow(style, mageSpell, magicAutoUpdate)
                || GiantsBank.inventoryFullOfLoot(style, lootCsv))) {
            bankingTrip = true;
            GiantsLog.action("bank", "supplies/inv → bank");
        }

        if (bankingTrip || bankAfterKeyPickup || Bank.isOpen()) {
            return handleBank(pos);
        }

        if (GrandExchange.isOpen()) {
            try {
                GrandExchange.collect(false);
            } catch (Throwable ignored) {
            }
        }

        // 7–9. Walk to entrance / enter / hunt zone
        if (!GiantsLocations.isUnderground(pos)) {
            return handleEnter(pos);
        }
        if (!GiantsLocations.isInHuntZone(pos, huntRadius)) {
            setStatus("naar giants");
            int d = GiantsDungeon.walkHunt(pos, huntRadius);
            return d > 0 ? d : ThreadLocalRandom.current().nextInt(280, 450);
        }

        // 13. Combat + anti-stuck
        return handleCombat(me, pos);
    }

    private int handleKey(WorldPoint pos) {
        if (Bank.isOpen() && GiantsKey.hasInOpenBank()) {
            checkedBankForKey = true;
            GiantsKey.withdrawFromOpenBank();
            setStatus("withdraw Brass key");
            return HumanBanking.afterActionMs();
        }
        if (!checkedBankForKey && !groundKeyTrip && !geKeyTrip) {
            bankingTrip = true;
            setStatus("bank: Brass key?");
            return handleBank(pos);
        }
        if (GiantsKey.canAffordGe() && !groundKeyTrip) {
            geKeyTrip = true;
            if (GiantsLocations.isUnderground(pos)) {
                setStatus("uit dungeon → GE key");
                return GiantsDungeon.exitDungeon(pos, false);
            }
            if (GiantsLocations.isNearShed(pos)) {
                GiantsWalk.Result w = GiantsWalk.toward(pos, net.storm.sdk.game.BankHelper.GE_BANK, 8, "GE (niet shed)");
                setStatus(w.status);
                return w.delayMs;
            }
            setStatus("GE Brass key");
            afterGeBuy = true;
            int d = GiantsKey.buyAtGe();
            if (GiantsKey.hasOnPerson()) {
                geKeyTrip = false;
                bankingTrip = true;
                bankAfterKeyPickup = true;
                GiantsLog.action("key", "GE klaar → bank eerst");
            }
            return d;
        }
        groundKeyTrip = true;
        geKeyTrip = false;
        return handleGroundKey(pos);
    }

    private int handleGroundKey(WorldPoint pos) {
        ITileItem key = GiantsKey.groundKey(pos);
        if (key != null) {
            hopWaitUntilMs = 0L;
            hopIssued = false;
            int d = GiantsKey.pickupGround(key);
            if (GiantsKey.hasOnPerson()) {
                bankAfterKeyPickup = true;
                bankingTrip = true;
                groundKeyTrip = false;
                GiantsLog.action("key", "ground → bank eerst (geen shed/GE ping-pong)");
            }
            setStatus("pak Brass key");
            return d;
        }
        if (!GiantsLocations.isUnderground(pos)) {
            setStatus("trapdoor → key spawn");
            return GiantsDungeon.enterTrapdoor(pos);
        }
        if (pos.distanceTo(GiantsLocations.KEY_SPAWN) > 3) {
            if (hopWaitUntilMs > 0L && System.currentTimeMillis() < hopWaitUntilMs) {
                setStatus("hop-wait key");
                return ThreadLocalRandom.current().nextInt(800, 1400);
            }
            GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.KEY_SPAWN, 1, "key-spawn");
            setStatus(w.status);
            return w.delayMs;
        }
        long now = System.currentTimeMillis();
        if (hopWaitUntilMs > now) {
            setStatus("wacht key-spawn " + ((hopWaitUntilMs - now) / 1000) + "s");
            return ThreadLocalRandom.current().nextInt(800, 1400);
        }
        if (!hopIssued) {
            if (pos.distanceTo(GiantsLocations.HOP_LADDER) > 2) {
                GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.HOP_LADDER, 1, "hop-ladder");
                setStatus(w.status);
                return w.delayMs;
            }
            int climb = GiantsKey.climbHopLadder(pos);
            hopIssued = true;
            hopWaitUntilMs = now + GiantsKey.HOP_WAIT_MS;
            GiantsKey.hopF2pWorld();
            setStatus("world hop ~3 min");
            return Math.max(climb, 1200);
        }
        hopIssued = false;
        setStatus("terug key-spawn");
        GiantsWalk.Result w = GiantsWalk.toward(pos, GiantsLocations.KEY_SPAWN, 1, "key-spawn");
        return w.delayMs;
    }

    private int handleBank(WorldPoint pos) {
        checkedBankForKey = true;
        if (GiantsLocations.isUnderground(pos)) {
            setStatus("uit dungeon → bank");
            return GiantsDungeon.exitDungeon(pos, GiantsKey.hasOnPerson());
        }
        if (bankDest == null) {
            bankDest = GiantsBank.preferBank(pos, afterGeBuy, bankAfterKeyPickup);
        }
        if (!Bank.isOpen()) {
            setStatus("naar bank");
            int d = GiantsBank.walkOpen(pos, bankDest);
            return d > 0 ? d : HumanBanking.openRetryMs();
        }
        int dep = GiantsBank.depositLoot(style, lootCsv);
        if (dep > 0) {
            setStatus("deposit loot");
            return dep;
        }
        int key = GiantsBank.withdrawKey();
        if (key > 0) {
            setStatus("withdraw key");
            return key;
        }
        int food = GiantsBank.withdrawFood(Math.max(1, foodAmount));
        if (food > 0) {
            setStatus("withdraw food");
            return food;
        }
        int gear = GiantsGear.tryWithdrawOne(style, mageSpell);
        if (gear > 0) {
            setStatus("withdraw gear");
            return gear;
        }
        int eq = GiantsGear.tryEquipOne(style);
        if (eq > 0) {
            setStatus("equip");
            return eq;
        }
        boolean foodOk = GiantsLoot.foodCount() >= Math.max(1, foodBankThreshold)
                || !GiantsBank.foodInBank();
        if (!foodOk) {
            setStatus("wacht food");
            return HumanBanking.afterActionMs();
        }
        Bank.close();
        bankingTrip = false;
        bankAfterKeyPickup = false;
        afterGeBuy = false;
        bankDest = null;
        GiantsLog.action("bank", "klaar → dungeon");
        setStatus("bank klaar");
        return HumanBanking.closeMs();
    }

    private int handleEnter(WorldPoint pos) {
        if (GiantsKey.hasOnPerson()) {
            setStatus("shed ingang");
            int d = GiantsDungeon.enterShed(pos);
            return d > 0 ? d : ThreadLocalRandom.current().nextInt(400, 700);
        }
        setStatus("trapdoor (geen key)");
        int d = GiantsDungeon.enterTrapdoor(pos);
        return d > 0 ? d : ThreadLocalRandom.current().nextInt(400, 700);
    }

    private int handleCombat(Players.LocalSnap me, WorldPoint pos) {
        if (style == GiantsTypes.GiantsCombatStyle.MAGE && !mageAutocastReady) {
            int ac = GiantsCombat.ensureAutocast(mageSpell);
            if (ac > 0) {
                setStatus("autocast " + mageSpell.getSpellName());
                return ac;
            }
            mageAutocastReady = true;
        }

        if (GiantsCombat.shouldForceUnstuck(me, lastCombatAnimMs)) {
            INPC stuck = GiantsCombat.nearestGiant(pos, huntRadius);
            if (stuck != null) {
                setStatus("anti-stuck Attack");
                GiantsLog.action("combat", "anti-stuck — geen anim");
                lastCombatAnimMs = System.currentTimeMillis();
                return GiantsCombat.attack(stuck);
            }
        }

        if (GiantsCombat.meInCombat()) {
            targetWasAlive = true;
            if (currentTarget != null && isDead(currentTarget)) {
                currentTarget = null;
            }
            INPC next = GiantsCombat.nearestGiant(pos, huntRadius);
            if (next != null && (currentTarget == null || isDead(currentTarget))) {
                return issueAttack(next);
            }
            setStatus("vechten");
            return ThreadLocalRandom.current().nextInt(90, 180);
        }

        INPC giant = GiantsCombat.nearestGiant(pos, huntRadius);
        if (giant == null) {
            setStatus("geen giant");
            int d = GiantsDungeon.walkHunt(pos, huntRadius);
            return d > 0 ? d : ThreadLocalRandom.current().nextInt(400, 700);
        }
        return issueAttack(giant);
    }

    private int issueAttack(INPC giant) {
        long now = System.currentTimeMillis();
        if (now - lastAttackMs < 220L) {
            return ThreadLocalRandom.current().nextInt(60, 120);
        }
        int d = GiantsCombat.attack(giant);
        lastAttackMs = now;
        currentTarget = giant;
        targetWasAlive = true;
        setStatus("aanval Hill Giant");
        return d;
    }

    private void maybeRegisterKill() {
        if (currentTarget != null && isDead(currentTarget)) {
            lastKillMs = System.currentTimeMillis();
            currentTarget = null;
            targetWasAlive = GiantsCombat.meInCombat();
            GiantsLog.action("combat", "kill");
            return;
        }
        if (targetWasAlive && !GiantsCombat.meInCombat()) {
            lastKillMs = System.currentTimeMillis();
            currentTarget = null;
            targetWasAlive = false;
            GiantsLog.action("combat", "combat uit");
        } else if (GiantsCombat.meInCombat()) {
            targetWasAlive = true;
        }
    }

    private static boolean isDead(INPC n) {
        if (n == null) {
            return true;
        }
        try {
            return n.isDead();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void ensureRun() {
        try {
            if (!Movement.isRunEnabled()) {
                Movement.setRunEnabled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void setStatus(String s) {
        status = s != null ? s : "idle";
    }
}
