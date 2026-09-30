package com.lonebot.example.imps2;

import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Game;
import net.storm.sdk.movement.Movement;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Walk-test: overal → Port Sarim dock → boot → Musa → imps,
 * terug boot → Port Sarim dock → Draynor bank.
 */
public final class Imps2Loop {

    public enum Leg {
        TO_IMPS,
        BACK_TO_PS,
        TO_DRAYNOR,
        DONE
    }

    private static final int ARRIVE_DOCK = 6;
    private static final int ARRIVE_DRAYNOR = 8;

    private Leg leg = Leg.TO_IMPS;
    private boolean initialized;
    private long startedAtMs;
    private String lastStatus = "";
    private boolean doneLogged;
    /** Vast hunt-doel tot aankomst — huntWalkTarget(pos) mag niet elke tick wisselen. */
    private WorldPoint lockedHuntDest;

    public void reset() {
        leg = Leg.TO_IMPS;
        initialized = false;
        startedAtMs = 0L;
        lastStatus = "";
        doneLogged = false;
        lockedHuntDest = null;
        Imps2Boat.reset();
        Movement.clearPath();
        BotRuntime.debugPath = null;
        BotRuntime.debugTarget = null;
    }

    public String getStatus() {
        return lastStatus != null ? lastStatus : "-";
    }

    public int tick() {
        if (!BotRuntime.imps2Enabled) {
            setStatus("uit");
            BotRuntime.debugPath = null;
            BotRuntime.debugTarget = null;
            initialized = false;
            return 600;
        }
        if (!Game.isLoggedIn()) {
            setStatus("niet ingelogd");
            return 800;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (pos == null) {
            setStatus("geen speler");
            return 500;
        }
        if (!initialized) {
            startedAtMs = System.currentTimeMillis();
            initialized = true;
            Imps2Boat.reset();
            Movement.clearPath();
            leg = Leg.TO_IMPS;
            doneLogged = false;
            lockedHuntDest = null;
            BotRuntime.logConsole("[Imps2] start walk-test @ " + pos.getX() + "," + pos.getY()
                    + " p" + pos.getPlane()
                    + (Imps2Locations.isOnKaramja(pos) ? " Karamja" : " mainland")
                    + " → imps → Port Sarim dock → Draynor");
            setStatus("start → imps");
            return ThreadLocalRandom.current().nextInt(200, 400);
        }

        switch (leg) {
            case TO_IMPS:
                return tickToImps(pos);
            case BACK_TO_PS:
                return tickBackToPortSarim(pos);
            case TO_DRAYNOR:
                return tickToDraynor(pos);
            case DONE:
            default:
                return tickDone(pos);
        }
    }

    private int tickToImps(WorldPoint pos) {
        if (Imps2Locations.isOnKaramja(pos) && Imps2Locations.arrivedAtHunt(pos)) {
            BotRuntime.logConsole("[Imps2] ✓ bij imps @ " + pos.getX() + "," + pos.getY()
                    + " — terug naar Port Sarim dock");
            Movement.clearPath();
            Imps2Boat.reset();
            lockedHuntDest = null;
            leg = Leg.BACK_TO_PS;
            setStatus("✓ imps → terug Port Sarim");
            return 0;
        }
        if (Imps2Locations.isOnKaramja(pos)) {
            if (lockedHuntDest == null) {
                lockedHuntDest = Imps2Locations.huntWalkTarget(pos);
                BotRuntime.logConsole("[Imps2] hunt-doel vast "
                        + lockedHuntDest.getX() + "," + lockedHuntDest.getY());
            }
            Imps2Walk.Result r = Imps2Walk.toward(pos, lockedHuntDest, 8, "Musa → imps");
            setStatus("→ imps " + r.status);
            return r.delayMs;
        }
        lockedHuntDest = null;
        Imps2Walk.Result boat = Imps2Boat.tick(pos, false);
        setStatus("→ Port Sarim / boot " + boat.status);
        return boat.delayMs;
    }

    private int tickBackToPortSarim(WorldPoint pos) {
        if (!Imps2Locations.isOnKaramja(pos)
                && pos.distanceTo(Imps2Locations.PORTSARIM_DOCK) <= ARRIVE_DOCK
                && pos.getPlane() == 0) {
            BotRuntime.logConsole("[Imps2] ✓ Port Sarim dock — door naar Draynor");
            Movement.clearPath();
            Imps2Boat.reset();
            leg = Leg.TO_DRAYNOR;
            setStatus("✓ Port Sarim dock → Draynor");
            return 0;
        }
        if (Imps2Locations.isOnKaramja(pos)) {
            Imps2Walk.Result boat = Imps2Boat.tick(pos, true);
            setStatus("imps → dock/boot " + boat.status);
            return boat.delayMs;
        }
        Imps2Walk.Result r = Imps2Walk.toward(pos, Imps2Locations.PORTSARIM_DOCK, ARRIVE_DOCK,
                "naar Port Sarim dock");
        setStatus("→ Port Sarim dock " + r.status);
        return r.delayMs;
    }

    private int tickToDraynor(WorldPoint pos) {
        if (Imps2Locations.isOnKaramja(pos)) {
            BotRuntime.logConsole("[Imps2] nog op Karamja tijdens Draynor-leg — boot eerst");
            Imps2Boat.reset();
            leg = Leg.BACK_TO_PS;
            return 400;
        }
        if (pos.getPlane() == Imps2Locations.DRAYNOR_BANK.getPlane()
                && pos.distanceTo(Imps2Locations.DRAYNOR_BANK) <= ARRIVE_DRAYNOR) {
            long sec = (System.currentTimeMillis() - startedAtMs) / 1000L;
            if (!doneLogged) {
                doneLogged = true;
                BotRuntime.logConsole("[Imps2] ✓ klaar @ Draynor t=" + sec + "s");
            }
            Movement.clearPath();
            BotRuntime.debugPath = null;
            BotRuntime.debugTarget = Imps2Locations.DRAYNOR_BANK;
            leg = Leg.DONE;
            setStatus("✓ klaar Draynor t=" + sec + "s");
            return 0;
        }
        Imps2Walk.Result r = Imps2Walk.toward(pos, Imps2Locations.DRAYNOR_BANK, ARRIVE_DRAYNOR,
                "Port Sarim → Draynor");
        setStatus("→ Draynor " + r.status);
        return r.delayMs;
    }

    private int tickDone(WorldPoint pos) {
        long sec = startedAtMs > 0L ? (System.currentTimeMillis() - startedAtMs) / 1000L : 0L;
        setStatus("✓ klaar Draynor t=" + sec + "s (vink uit om te resetten)");
        BotRuntime.debugTarget = Imps2Locations.DRAYNOR_BANK;
        return 0;
    }

    private void setStatus(String s) {
        lastStatus = s != null ? s : "-";
        BotRuntime.imps2Status = lastStatus;
        BotRuntime.debugSummary = lastStatus;
    }
}
