package net.storm.sdk.movement.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Quest;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.movement.pathfinder.model.Requirements;
import net.storm.api.movement.pathfinder.model.Transport;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.quests.Quests;
import net.storm.sdk.widgets.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Al Kharid-tolpoort (Lumbridge ↔ Al Kharid).
 * Mag alleen als Prince Ali Rescue klaar is, of minstens 10 coins op zak.
 */
public final class AlKharidGate {

    private static final Logger log = LoggerFactory.getLogger(AlKharidGate.class);

    /** Closed/open pair in {@code f2p_gates.tsv} + wiki 44052/44053. */
    public static final int OBJECT_ID_SOUTH = 44050;
    public static final int OBJECT_ID_NORTH = 44051;
    public static final int OBJECT_ID_SOUTH_B = 44052;
    public static final int OBJECT_ID_NORTH_B = 44053;
    public static final int COINS_ID = 995;
    public static final int TOLL = 10;
    /**
     * Pas binnen deze afstand Pay-toll/COS op de poort — verder weg alleen aanlopen (geen klik vanaf Varrock).
     */
    public static final int APPROACH_COS_TILES = 20;
    /** Open = Talk-dialog. Pay-toll gaat er meteen door. */
    private static final String[] PAY_ACTIONS = {
            "Pay-toll(10gp)",
            "Pay-toll-10gp",
            "Pay-toll",
            "Pay"
    };

    private static volatile long lastSkipLogMs;
    private static volatile long lastChooseMs;

    private AlKharidGate() {
    }

    public static boolean isGateObject(int objectId) {
        return objectId == OBJECT_ID_NORTH || objectId == OBJECT_ID_SOUTH
                || objectId == OBJECT_ID_NORTH_B || objectId == OBJECT_ID_SOUTH_B;
    }

    public static boolean isGateTile(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        return (x == 3267 || x == 3268) && (y == 3227 || y == 3228);
    }

    /**
     * Al Kharid / mine-kant (oost van de tolpoort).
     * Zonder coins/quest: poort-transport uit — Shortest Path gaat noordom (Varrock/mijn), niet zuid om het hek.
     */
    public static boolean isEastSide(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        if (y < 3115 || y > 3340) {
            return false;
        }
        // Poort-breedte: 3267–3268, 3227–3228. Zuid ervan ligt de woestijn al vanaf ~3264.
        if (y >= 3227) {
            return x >= 3268;
        }
        return x >= 3264;
    }

    /** Stand-tegel deze kant van de poort — hops blijven hier, Pay-toll daarna. */
    public static final WorldPoint WEST_APPROACH = new WorldPoint(3261, 3227, 0);
    public static final WorldPoint EAST_APPROACH = new WorldPoint(3272, 3227, 0);

    public static WorldPoint approachOnThisSide(WorldPoint from) {
        return isEastSide(from) ? EAST_APPROACH : WEST_APPROACH;
    }

    /**
     * Nooit een hop naar de overkant / zuid langs het hek vóór Pay-toll.
     * (Pad na de poort gaat naar Al Kharid-bank zuid — klik daarvan = lopen langs de westkant.)
     */
    public static WorldPoint capHop(WorldPoint from, WorldPoint dest, WorldPoint hop) {
        if (from == null || hop == null || from.getPlane() != 0 || hop.getPlane() != 0) {
            return hop;
        }
        if (!canPass()) {
            return hop;
        }
        boolean crossing = dest != null && needGate(from, dest);
        boolean hopOther = isEastSide(from) != isEastSide(hop) || isGateTile(hop);
        if (!crossing && !hopOther) {
            return hop;
        }
        if (hopOther) {
            WorldPoint ap = approachOnThisSide(from);
            logGate("hop-cap poort — niet voorbij hek naar " + hop.getX() + "," + hop.getY());
            return ap;
        }
        // Zelfde kant maar zuid langs het hek (spam na te vroege doorlink)
        if (!isEastSide(from) && hop.getY() < 3224 && hop.getX() >= 3260) {
            logGate("hop-cap poort — niet zuid langs hek @" + hop.getX() + "," + hop.getY());
            return WEST_APPROACH;
        }
        if (isEastSide(from) && dest != null && !isEastSide(dest)
                && hop.getY() < 3224 && hop.getX() <= 3274) {
            return EAST_APPROACH;
        }
        return hop;
    }

    /** Bestemming aan de andere kant van de poort (zonder transport: BFS leeg). */
    public static boolean crossesGateWithoutPass(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null || canPass()) {
            return false;
        }
        return needGate(from, dest);
    }

    /** Lumbridge/Port Sarim ↔ Al Kharid: poort nodig. Imp naar dock: niet. */
    public static boolean needGate(WorldPoint from, WorldPoint dest) {
        if (from == null || dest == null) {
            return false;
        }
        if (from.getPlane() != 0 || dest.getPlane() != 0) {
            return false;
        }
        return isEastSide(from) != isEastSide(dest);
    }

    public static boolean matches(Transport t) {
        return t != null && (isGateTile(t.getSource()) || isGateTile(t.getDestination()));
    }

    public static Requirements requirementsIfGate(int objectId, WorldPoint source) {
        if (isGateObject(objectId) || isGateTile(source)) {
            return requirements();
        }
        return null;
    }

    public static Requirements requirements() {
        return new Requirements().require(AlKharidGate::canPass);
    }

    public static boolean canPass() {
        if (Quests.isFinished(Quest.PRINCE_ALI_RESCUE)) {
            return true;
        }
        return coinCount() >= TOLL;
    }

    public static int coinCount() {
        int byId = Inventory.getCount(COINS_ID);
        if (byId >= TOLL) {
            return byId;
        }
        int byName = Inventory.getCount("Coins");
        return Math.max(byId, byName);
    }

    public static void logSkip() {
        WorldPoint from = null;
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            if (me != null && me.present) {
                from = me.worldLocation;
            }
        } catch (Throwable ignored) {
        }
        WorldPoint dest = null;
        try {
            dest = net.storm.sdk.movement.MovementHelper.getActiveDestination();
        } catch (Throwable ignored) {
        }
        if (!needGate(from, dest)) {
            return;
        }
        logGate("poort overslaan — Prince Ali=" + questYesNo()
                + " coins=" + coinCount() + "/" + TOLL + " — A* noordom, geen Pay-toll");
    }

    /** Geen 10 coins / Prince Ali: poort-transport uit; Shortest Path noordom. */
    public static void logDetour() {
        logGate("geen 10 coins / Prince Ali — poort dicht, A* noordom (niet zuid om het hek)");
    }

    /**
     * Alleen het tol-gesprek afhandelen als we écht door de poort moeten.
     * Imp/Port Sarim vanaf de westkant: gesprek sluiten, daarna lopen.
     */
    public static boolean progressForWalk(WorldPoint from, WorldPoint dest) {
        // Dialog.isOpen = client-thread (tot 1.5s). Niet vanaf Varrock-east scannen.
        if (!nearGate()) {
            return false;
        }
        if (!Dialog.isOpen()) {
            return false;
        }
        boolean crossing = needGate(from, dest);
        if (!crossing) {
            if (isTollDialog() || (nearGate() && Dialog.canContinue())) {
                return dismissToll();
            }
            return false;
        }
        return progressDialog();
    }

    /** Tol-menu weg als het doel niet door de poort is. */
    public static boolean dismissToll() {
        if (!Dialog.isOpen()) {
            return false;
        }
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            logGate("sluit gesprek — dest niet door poort");
            return true;
        }
        if (isTollDialog()) {
            boolean skip = Dialog.chooseOption("I'll walk around", "No thank you")
                    || Dialog.chooseOption(2);
            if (!skip) {
                Dialog.forceClose();
            }
            logGate("niet door poort → walk around / ESC");
            return true;
        }
        return false;
    }

    /**
     * Door de poort: {@code Pay-toll(10gp)} (geen Talk/Open-dialog).
     * Prince Ali: Pay verdwijnt → Open (gratis). Dialog alleen als die al open is.
     */
    public static boolean tryPayOrOpen() {
        if (Dialog.isOpen()) {
            return progressDialog();
        }
        if (!canPass()) {
            logSkip();
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastChooseMs < 650L) {
            return true;
        }
        ITileObject gate = findGate();
        if (gate != null) {
            String pay = firstPayAction(gate);
            if (pay != null) {
                lastChooseMs = now;
                boolean ok = invokeObject(gate, pay);
                logGate(ok ? "Pay-toll (geen Talk) " + pay : "Pay-toll fail " + pay);
                return ok;
            }
            if (gate.hasAction("Open")) {
                lastChooseMs = now;
                boolean ok = invokeObject(gate, "Open");
                logGate(ok ? "Open (Prince Ali / geen Pay)" : "Open fail");
                return ok;
            }
        }
        INPC guard = NPCs.getNearest(AlKharidGate::isPayGuard);
        if (guard != null) {
            String pay = firstPayAction(guard);
            if (pay != null) {
                lastChooseMs = now;
                boolean ok = guard.interact(pay);
                logGate(ok ? "Pay guard (geen Talk-to) " + pay : "Pay guard fail");
                return ok;
            }
        }
        logGate("geen Pay-toll/poort in scene");
        return false;
    }

    /**
     * @return true als dit het tol-gesprek is (of we het hebben voortgezet).
     *         Alleen aanroepen als {@link #needGate} waar is.
     */
    public static boolean progressDialog() {
        if (!Dialog.isOpen()) {
            return false;
        }
        if (!isTollDialog() && !Dialog.canContinue()) {
            return false;
        }
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            logGate("continue");
            return true;
        }
        if (!isTollDialog()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastChooseMs < 650L) {
            return true;
        }
        lastChooseMs = now;
        if (canPass()) {
            boolean ok = Dialog.chooseOption("Yes, okay.", "I'll pay")
                    || Dialog.chooseOption(0);
            if (ok) {
                logGate("Yes, okay. (10 coins of Prince Ali)");
            } else {
                logGate("optie Yes, okay. nog niet klikbaar");
            }
            return true;
        }
        boolean skip = Dialog.chooseOption("I'll walk around", "No thank you")
                || Dialog.chooseOption(2);
        if (skip) {
            logSkip();
        }
        return true;
    }

    public static boolean isTollDialog() {
        return Dialog.hasOption("Yes, okay.")
                || Dialog.hasOption("Who does my money")
                || Dialog.hasOption("I'll walk around")
                || Dialog.hasOption("No thank you");
    }

    public static boolean nearGate() {
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint p = me != null && me.present ? me.worldLocation : null;
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        return x >= 3264 && x <= 3271 && y >= 3224 && y <= 3231;
    }

    /** Te ver voor poort-klik/Open — eerst aanlopen. */
    public static boolean tooFarToClick(WorldPoint from, WorldPoint gateTile) {
        if (from == null || gateTile == null || !isGateTile(gateTile)) {
            return false;
        }
        if (from.getPlane() != gateTile.getPlane()) {
            return true;
        }
        return from.distanceTo(gateTile) > APPROACH_COS_TILES;
    }

    /** Speler mag Open/COS op de poort (≤ {@link #APPROACH_COS_TILES}). */
    public static boolean closeEnoughToOpen(WorldPoint from) {
        if (from == null || from.getPlane() != 0) {
            return false;
        }
        int d = Math.min(
                from.distanceTo(new WorldPoint(3267, 3227, 0)),
                from.distanceTo(new WorldPoint(3268, 3228, 0)));
        return d <= APPROACH_COS_TILES;
    }

    private static ITileObject findGate() {
        WorldPoint anchor = new WorldPoint(3267, 3227, 0);
        ITileObject byId = TileObjects.getNearest(anchor,
                OBJECT_ID_SOUTH, OBJECT_ID_NORTH, OBJECT_ID_SOUTH_B, OBJECT_ID_NORTH_B);
        if (byId != null && byId.getWorldLocation() != null
                && byId.getWorldLocation().distanceTo(anchor) <= 4) {
            return byId;
        }
        return TileObjects.getNearest(anchor, o -> {
            if (o == null || o.getWorldLocation() == null) {
                return false;
            }
            if (o.getWorldLocation().distanceTo(anchor) > 4) {
                return false;
            }
            return isGateObject(o.getId()) || firstPayAction(o) != null;
        });
    }

    private static boolean isPayGuard(INPC npc) {
        if (npc == null || npc.getWorldLocation() == null) {
            return false;
        }
        if (npc.getWorldLocation().distanceTo(new WorldPoint(3267, 3227, 0)) > 8) {
            return false;
        }
        String n = npc.getName();
        if (n == null || !n.equalsIgnoreCase("Border Guard")) {
            return false;
        }
        return firstPayAction(npc) != null;
    }

    private static String firstPayAction(ITileObject obj) {
        if (obj == null) {
            return null;
        }
        for (String a : PAY_ACTIONS) {
            if (obj.hasAction(a)) {
                return a;
            }
        }
        TileObject raw = TileObjects.unwrap(obj);
        if (raw == null) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            ObjectComposition comp = MenuInteract.resolveObjectComposition(c, raw.getId());
            if (comp == null || comp.getActions() == null) {
                return null;
            }
            for (String a : comp.getActions()) {
                if (a != null && a.toLowerCase(Locale.ROOT).startsWith("pay")) {
                    return a;
                }
            }
            return null;
        }, null);
    }

    private static String firstPayAction(INPC npc) {
        if (npc == null) {
            return null;
        }
        for (String a : PAY_ACTIONS) {
            if (npc.hasAction(a)) {
                return a;
            }
        }
        return null;
    }

    /** Invoke Pay/Open — left-click zou Open/Talk doen. */
    private static boolean invokeObject(ITileObject gate, String action) {
        TileObject raw = TileObjects.unwrap(gate);
        if (raw != null && MenuInteract.interactObject(raw, action)) {
            return true;
        }
        return gate.interact(action);
    }

    private static String questYesNo() {
        return Quests.isFinished(Quest.PRINCE_ALI_RESCUE) ? "ja" : "nee";
    }

    private static void logGate(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastSkipLogMs < 2_500L) {
            return;
        }
        lastSkipLogMs = now;
        log.info("[Walk/gate] {}", msg);
        BotRuntime.logConsole("[Walk/gate] " + msg);
    }
}
