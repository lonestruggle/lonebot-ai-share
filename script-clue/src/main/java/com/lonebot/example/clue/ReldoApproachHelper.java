package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.Reachable;
import net.storm.sdk.widgets.Dialog;

import java.util.Locale;

/**
 * Reldo (Varrock library): talk-zone + LOS; Open doors; Yes I do / device dialog.
 * CombatBot {@code ReldoCastleApproachHelper} + Reldo dialog port.
 */
public final class ReldoApproachHelper {

    public static final WorldPoint RELDO_TALK_TILE = BeginnerClueReference.RELDO_TILE;
    public static final WorldPoint RELDO_ENTRANCE_DOOR = new WorldPoint(3210, 3490, 0);
    public static final WorldPoint RELDO_LIBRARY_DOOR = new WorldPoint(3210, 3495, 0);
    public static final WorldPoint[] RELDO_LIBRARY_DOOR_TILES = {
            new WorldPoint(3210, 3495, 0),
            new WorldPoint(3210, 3494, 0),
            new WorldPoint(3211, 3495, 0)
    };

    private static final int ZONE_MIN_X = 3207;
    private static final int ZONE_MAX_X = 3215;
    private static final int ZONE_MIN_Y = 3490;
    private static final int ZONE_MAX_Y = 3497;
    private static final int DOOR_OPEN_RANGE = 4;

    private static long lastWalkMs;
    private static long lastDoorClickMs;
    private static long lastDialogMs;
    private static long lastLogMs;
    private static String lastLogMsg = "";

    private ReldoApproachHelper() {
    }

    public static void reset() {
        lastWalkMs = 0L;
        lastDoorClickMs = 0L;
        lastDialogMs = 0L;
        lastLogMs = 0L;
        lastLogMsg = "";
    }

    public static boolean isInReldoTalkZone(WorldPoint me) {
        if (me == null || me.getPlane() != RELDO_TALK_TILE.getPlane()) {
            return false;
        }
        return me.getX() >= ZONE_MIN_X && me.getX() <= ZONE_MAX_X
                && me.getY() >= ZONE_MIN_Y && me.getY() <= ZONE_MAX_Y;
    }

    public static boolean hasLineOfSightToReldo(WorldPoint from) {
        if (from == null) {
            return false;
        }
        try {
            return Reachable.hasLineOfSight(from, RELDO_TALK_TILE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** In talk-zone (bibliotheekruimte) — niet door de deur heen praten. */
    public static boolean canTalkToReldoNow(WorldPoint me) {
        if (me == null || me.getPlane() != RELDO_TALK_TILE.getPlane()) {
            return false;
        }
        return isInReldoTalkZone(me);
    }

    /**
     * Approach + doors + Talk-to Reldo (alleen in zone, COS invoke).
     *
     * @return delay ms; {@code -2} = strange device binnen → caller moet scroll vernieuwen
     */
    public static int approachAndTalk(IPlayer local) {
        if (hasStrangeDevice()) {
            Integer dialog = handleReldoTreasureTrailDialog();
            if (dialog != null) {
                return dialog;
            }
            BotRuntime.logConsole("[Clue/Reldo] Strange device in inv — stop Talk-to");
            return -2;
        }

        Integer dialog = handleReldoTreasureTrailDialog();
        if (dialog != null) {
            return dialog;
        }
        WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null) {
            return 600;
        }

        if (!canTalkToReldoNow(me)) {
            if (tryOpenReldoEntranceDoor(me)) {
                return 500;
            }
            if (!isReldoLibraryDoorOpen() && isNearReldoLibraryDoor(me)) {
                if (tryOpenReldoLibraryDoor(me)) {
                    return 500;
                }
            }
            int travel = approachBeforeTalk(local);
            if (travel > 0) {
                return travel;
            }
            me = local != null ? local.getWorldLocation() : ClueWalk.me();
            if (me != null && !canTalkToReldoNow(me)) {
                if (tryOpenReldoEntranceDoor(me) || tryOpenReldoLibraryDoor(me)) {
                    return 500;
                }
                return 350;
            }
        }

        me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null || !canTalkToReldoNow(me)) {
            return 350;
        }

        if (Dialog.isOpen()) {
            Integer again = handleReldoTreasureTrailDialog();
            return again != null ? again : 300;
        }

        INPC reldo = findReldo();
        if (reldo == null) {
            logThrottled("[Clue/Reldo] NPC niet in scene — walk zone");
            return approachBeforeTalk(local) > 0 ? 400 : 500;
        }
        logThrottled("[Clue/Reldo] Talk-to (zone COS)");
        if (ClueNpcClick.talkToInvoke(reldo)) {
            return 550;
        }
        int cos = ClueNpcClick.talkOrApproach(reldo, RELDO_TALK_TILE);
        return cos > 0 ? cos : 400;
    }

    public static int approachAndTalk() {
        return approachAndTalk(Players.getLocal());
    }

    /**
     * @return 0 = klaar; &gt;0 = delay
     */
    public static int approachBeforeTalk(IPlayer local) {
        WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null) {
            return 600;
        }
        if (canTalkToReldoNow(me)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900L && Movement.isWalking()) {
            return 280;
        }
        lastWalkMs = now;
        logThrottled("[Clue/Reldo] → zone " + RELDO_TALK_TILE.getX() + "," + RELDO_TALK_TILE.getY()
                + " (nu " + me.getX() + "," + me.getY() + ")");
        Movement.walkTo(RELDO_TALK_TILE);
        return 400;
    }

    public static Integer handleReldoTreasureTrailDialog() {
        // canContinue dekt "Click here to continue" onder OPTIONS + sprite-show-clue
        if (!Dialog.isOpen() && !Dialog.canContinue()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - lastDialogMs < 350L) {
            return 200;
        }
        lastDialogMs = now;
        if (Dialog.isViewingOptions()) {
            if (tryChooseReldoYesIDo()) {
                return 700;
            }
            if (tryChooseReldoDeviceDialog()) {
                return 700;
            }
            // Continue-only onder OPTIONS → centrale autoContinue
            if (Dialog.autoContinue(true)) {
                BotRuntime.logConsole("[Clue/Reldo] auto-continue (options)");
                return 500;
            }
            return 500;
        }
        if (Dialog.autoContinue(true)) {
            BotRuntime.logConsole("[Clue/Reldo] auto-continue");
            return 500;
        }
        return 300;
    }

    private static boolean hasStrangeDevice() {
        try {
            return net.storm.sdk.items.Inventory.contains(BeginnerClueReference.STRANGE_DEVICE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean tryChooseReldoYesIDo() {
        if (Dialog.hasOption(s -> optionContains(s, "yes, i do") || optionContains(s, "yes i do"))) {
            Dialog.chooseOption(s -> optionContains(s, "yes, i do") || optionContains(s, "yes i do"));
            BotRuntime.logConsole("[Clue/Reldo] dialog: Yes, I do.");
            return true;
        }
        if (Dialog.hasOption(s -> {
            if (s == null) {
                return false;
            }
            String low = ClueScrollHelper.strip(s).toLowerCase(Locale.ROOT);
            return low.startsWith("yes") && !low.contains("don't") && !low.contains("do not");
        })) {
            Dialog.chooseOption(s -> {
                if (s == null) {
                    return false;
                }
                String low = ClueScrollHelper.strip(s).toLowerCase(Locale.ROOT);
                return low.startsWith("yes") && !low.contains("don't") && !low.contains("do not");
            });
            BotRuntime.logConsole("[Clue/Reldo] dialog: Yes (fallback)");
            return true;
        }
        return false;
    }

    public static boolean tryChooseReldoDeviceDialog() {
        if (tryChooseReldoYesIDo()) {
            return true;
        }
        String[] preferred = {
                "search for treasure",
                "search the bookshelves",
                "search the books",
                "search bookshelves",
                "search books",
                "a treasure",
                "treasure trails",
                "treasure trail",
                "strange device",
                "search",
                "device",
                "buried",
                "treasure"
        };
        for (String opt : preferred) {
            final String key = opt.toLowerCase(Locale.ROOT);
            if (Dialog.hasOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains(key))) {
                Dialog.chooseOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains(key));
                BotRuntime.logConsole("[Clue/Reldo] dialog: " + opt);
                return true;
            }
        }
        return false;
    }

    private static boolean optionContains(String option, String phrase) {
        if (option == null || phrase == null) {
            return false;
        }
        return ClueScrollHelper.strip(option).toLowerCase(Locale.ROOT).contains(phrase);
    }

    private static boolean isNearReldoLibraryDoor(WorldPoint me) {
        if (me == null) {
            return false;
        }
        if (me.distanceTo(RELDO_LIBRARY_DOOR) <= DOOR_OPEN_RANGE) {
            return true;
        }
        if (me.distanceTo(RELDO_ENTRANCE_DOOR) <= DOOR_OPEN_RANGE) {
            return true;
        }
        return me.distanceTo(new WorldPoint(3210, 3491, 0)) <= DOOR_OPEN_RANGE;
    }

    private static boolean tryOpenReldoEntranceDoor(WorldPoint me) {
        if (me == null || isReldoEntranceDoorOpen()) {
            return false;
        }
        if (me.distanceTo(RELDO_ENTRANCE_DOOR) > DOOR_OPEN_RANGE) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastDoorClickMs < 1_200L) {
            return false;
        }
        ITileObject door = findDoorAt(RELDO_ENTRANCE_DOOR);
        if (door == null || !door.hasAction("Open")) {
            return false;
        }
        door.interact("Open");
        lastDoorClickMs = now;
        BotRuntime.logConsole("[Clue/Reldo] ingang Open @ " + door.getWorldLocation());
        return true;
    }

    private static boolean tryOpenReldoLibraryDoor(WorldPoint me) {
        if (isReldoLibraryDoorOpen()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastDoorClickMs < 1_200L) {
            return false;
        }
        if (!isNearReldoLibraryDoor(me)) {
            return false;
        }
        ITileObject door = findReldoLibraryDoor();
        if (door == null || !door.hasAction("Open")) {
            return false;
        }
        door.interact("Open");
        lastDoorClickMs = now;
        BotRuntime.logConsole("[Clue/Reldo] library deur Open @ " + door.getWorldLocation());
        return true;
    }

    private static boolean isReldoEntranceDoorOpen() {
        ITileObject door = findDoorAt(RELDO_ENTRANCE_DOOR);
        return door != null && door.hasAction("Close");
    }

    private static boolean isReldoLibraryDoorOpen() {
        ITileObject door = findReldoLibraryDoor();
        return door != null && door.hasAction("Close");
    }

    private static boolean isReldoLibraryDoorTile(WorldPoint loc) {
        if (loc == null || loc.getPlane() != RELDO_LIBRARY_DOOR.getPlane()) {
            return false;
        }
        for (WorldPoint tile : RELDO_LIBRARY_DOOR_TILES) {
            if (loc.distanceTo(tile) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static ITileObject findReldoLibraryDoor() {
        try {
            return TileObjects.getNearest(obj -> {
                if (obj == null || obj.getName() == null || obj.getWorldLocation() == null) {
                    return false;
                }
                if (!isReldoLibraryDoorTile(obj.getWorldLocation())) {
                    return false;
                }
                String n = obj.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("door")) {
                    return false;
                }
                return obj.hasAction("Open") || obj.hasAction("Close");
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ITileObject findDoorAt(WorldPoint doorTile) {
        if (doorTile == null) {
            return null;
        }
        try {
            return TileObjects.getNearest(obj -> {
                if (obj == null || obj.getName() == null || obj.getWorldLocation() == null) {
                    return false;
                }
                WorldPoint loc = obj.getWorldLocation();
                if (loc.getPlane() != doorTile.getPlane() || loc.distanceTo(doorTile) > 1) {
                    return false;
                }
                String n = obj.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("door")) {
                    return false;
                }
                return obj.hasAction("Open") || obj.hasAction("Close");
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static INPC findReldo() {
        try {
            INPC n = NPCs.getNearest("Reldo");
            if (n != null) {
                return n;
            }
            return NPCs.getNearest(npc -> npc != null && npc.getName() != null
                    && npc.getName().equalsIgnoreCase("Reldo"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLogMsg) && now - lastLogMs < 1_500L) {
            return;
        }
        lastLogMs = now;
        lastLogMsg = msg;
        BotRuntime.logConsole(msg);
    }
}
