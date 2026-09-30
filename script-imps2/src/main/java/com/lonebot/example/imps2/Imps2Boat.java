package com.lonebot.example.imps2;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.widgets.Dialog;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Port Sarim ↔ Musa Point — zelfde NPC-ids / dialog als Imp Killer.
 */
public final class Imps2Boat {

    private static final long CLICK_COOLDOWN_MS = 2800L;
    private static long lastClickMs;
    private static long awaitingUntilMs;
    private static String lastLog = "";
    private static long lastLogMs;

    private Imps2Boat() {
    }

    public static void reset() {
        lastClickMs = 0;
        awaitingUntilMs = 0;
        lastLog = "";
        lastLogMs = 0;
    }

    /**
     * @param toPortSarim true = Customs (Karamja → Port Sarim); false = Seaman (Port Sarim → Musa)
     */
    public static Imps2Walk.Result tick(WorldPoint pos, boolean toPortSarim) {
        long now = System.currentTimeMillis();
        if (now < awaitingUntilMs) {
            boolean onKj = Imps2Locations.isOnKaramja(pos);
            if (toPortSarim && !onKj) {
                awaitingUntilMs = 0;
                logBoat("aangekomen Port Sarim");
            } else if (!toPortSarim && onKj) {
                awaitingUntilMs = 0;
                logBoat("aangekomen Musa Point");
            } else {
                return Imps2Walk.Result.wait(ThreadLocalRandom.current().nextInt(400, 700), "boat wait");
            }
        }
        if (Dialog.isOpen()) {
            return handleDialog(toPortSarim);
        }
        if (Inventory.getCount("Coins") < Imps2Locations.BOAT_FARE) {
            logBoat("geen 30gp voor boot");
            return Imps2Walk.Result.wait(800, "geen 30gp voor boot");
        }
        if (now - lastClickMs < CLICK_COOLDOWN_MS) {
            return Imps2Walk.Result.wait(ThreadLocalRandom.current().nextInt(400, 700), "boat wait");
        }

        WorldPoint dock = toPortSarim
                ? Imps2Locations.KARAMJA_BOAT_APPROACH
                : Imps2Locations.PORTSARIM_DOCK;
        if (!Imps2Locations.inBoatClickRange(pos, dock)) {
            String label = toPortSarim ? "naar Karamja dock" : "naar Port Sarim dock";
            return Imps2Walk.toward(pos, dock, 2, label);
        }

        INPC npc = toPortSarim ? findCustoms() : findSeaman();
        if (npc != null && ClickOnSight.can(npc)) {
            ClickOnSight.Result r = toPortSarim
                    ? ClickOnSight.interact(npc, "Travel", "Port Sarim", "Pay-fare", "Pay-Fare", "Talk-to")
                    : ClickOnSight.interact(npc, "Pay-fare", "Pay-Fare", "Pay fare", "Travel", "Talk-to");
            logBoat((toPortSarim ? "customs " : "seaman ") + r);
            if (r.clicked) {
                lastClickMs = now;
                awaitingUntilMs = now + ThreadLocalRandom.current().nextInt(3500, 5500);
                AntiBan.get().markBotActivity();
                return Imps2Walk.Result.wait(ThreadLocalRandom.current().nextInt(400, 700),
                        (toPortSarim ? "boot PS " : "boot KJ ") + r.outcome.name().toLowerCase());
            }
            if (r.outcome != ClickOnSight.Outcome.NONE) {
                return Imps2Walk.Result.wait(ThreadLocalRandom.current().nextInt(220, 400),
                        "boat " + r.outcome.name().toLowerCase());
            }
        }

        // NPC nog niet klikbaar of klik mis: walker blijven voeden (niet wachten tot stilstand)
        int d = pos != null ? pos.distanceTo(dock) : -1;
        logBoat("walk dock d=" + d + (npc == null ? " (geen npc)" : " (npc niet ready)"));
        Movement.walkTo(dock);
        return Imps2Walk.Result.wait(ThreadLocalRandom.current().nextInt(280, 450), "boat walk dock");
    }

    private static Imps2Walk.Result handleDialog(boolean toPortSarim) {
        String[] preferred = toPortSarim
                ? new String[]{"Yes", "Okay", "Ok", "Travel", "Pay-fare", "Pay fare", "Port Sarim"}
                : new String[]{"Yes", "Okay", "Ok", "Pay-fare", "Pay fare", "Travel", "Karamja", "Musa Point"};
        for (String opt : preferred) {
            if (Dialog.hasOption(opt) && Dialog.chooseOption(opt)) {
                AntiBan.get().markBotActivity();
                awaitingUntilMs = System.currentTimeMillis()
                        + ThreadLocalRandom.current().nextInt(3000, 5000);
                logBoat("dialog " + opt);
                return Imps2Walk.Result.wait(ThreadLocalRandom.current().nextInt(400, 700), "boot dialoog");
            }
        }
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            return Imps2Walk.Result.wait(ThreadLocalRandom.current().nextInt(300, 500), "boot dialoog…");
        }
        return Imps2Walk.Result.wait(400, "boot dialoog");
    }

    private static INPC findCustoms() {
        return NPCs.getNearest(n -> {
            if (n == null) {
                return false;
            }
            if (n.getId() == Imps2Locations.CUSTOMS_OFFICER_ID) {
                return true;
            }
            String name = n.getName();
            return name != null && name.toLowerCase(Locale.ROOT).contains("customs officer");
        });
    }

    private static INPC findSeaman() {
        return NPCs.getNearest(n -> {
            if (n == null) {
                return false;
            }
            for (int id : Imps2Locations.PORT_SARIM_SEAMAN_IDS) {
                if (n.getId() == id) {
                    return true;
                }
            }
            String name = n.getName();
            if (name == null) {
                return false;
            }
            String l = name.toLowerCase(Locale.ROOT);
            return l.contains("seaman") || l.contains("captain tobias")
                    || l.contains("lorris") || l.contains("thresnor");
        });
    }

    private static void logBoat(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1500L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Imps2/boat] " + msg);
    }
}
