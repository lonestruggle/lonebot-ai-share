package com.lonebot.example.quest.helpers;

import com.lonebot.example.quest.QuestLog;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.TileObjects;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.movement.Movement;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Zone/door → Talk-to. CombatBot {@code QuestNpcApproachHelper} port (Movement.walkTo, no ctx).
 */
public final class QuestNpcApproachHelper {

    private static final int COOK_KITCHEN_MIN_X = 3205;
    private static final int COOK_KITCHEN_MAX_X = 3212;
    private static final int COOK_KITCHEN_MIN_Y = 3212;
    private static final int COOK_KITCHEN_MAX_Y = 3217;

    private static final int GOBLIN_GENERALS_MIN_X = 2956;
    private static final int GOBLIN_GENERALS_MAX_X = 2958;
    private static final int GOBLIN_GENERALS_MIN_Y = 3510;
    private static final int GOBLIN_GENERALS_MAX_Y = 3514;

    private static final int APOTHECARY_MIN_X = 3192;
    private static final int APOTHECARY_MAX_X = 3198;
    private static final int APOTHECARY_MIN_Y = 3402;
    private static final int APOTHECARY_MAX_Y = 3406;

    public static final int DEFAULT_TALK_DISTANCE = 2;

    private final String npcName;
    private final WorldPoint npcAnchor;
    private final WorldPoint talkTile;
    private final int talkDistance;
    private final WorldPoint doorTile;
    private final WorldPoint doorApproachTile;
    private final Predicate<WorldPoint> insideRoom;

    private long lastWalkMs;
    private long lastDoorMs;

    private QuestNpcApproachHelper(String npcName,
                                   WorldPoint npcAnchor,
                                   WorldPoint talkTile,
                                   int talkDistance,
                                   WorldPoint doorTile,
                                   WorldPoint doorApproachTile,
                                   Predicate<WorldPoint> insideRoom) {
        this.npcName = npcName;
        this.npcAnchor = npcAnchor;
        this.talkTile = talkTile;
        this.talkDistance = talkDistance;
        this.doorTile = doorTile;
        this.doorApproachTile = doorApproachTile;
        this.insideRoom = insideRoom;
    }

    public static QuestNpcApproachHelper lumbridgeCook() {
        WorldPoint cookTile = new WorldPoint(3206, 3214, 0);
        WorldPoint door = new WorldPoint(3209, 3213, 0);
        WorldPoint talk = new WorldPoint(3208, 3213, 0);
        return new QuestNpcApproachHelper(
                "Cook",
                cookTile,
                talk,
                DEFAULT_TALK_DISTANCE,
                door,
                new WorldPoint(3210, 3213, 0),
                p -> p != null && p.getPlane() == 0
                        && p.getX() <= door.getX() - 1
                        && p.getY() >= 3212 && p.getY() <= 3217);
    }

    public static QuestNpcApproachHelper goblinGenerals() {
        WorldPoint stand = new WorldPoint(2957, 3512, 0);
        WorldPoint door = new WorldPoint(2956, 3509, 0);
        return new QuestNpcApproachHelper(
                "General",
                stand,
                stand,
                DEFAULT_TALK_DISTANCE,
                door,
                new WorldPoint(2957, 3507, 0),
                QuestNpcApproachHelper::inGoblinGeneralsTalkZone);
    }

    public static QuestNpcApproachHelper romeo() {
        WorldPoint tile = new WorldPoint(3214, 3424, 0);
        return new QuestNpcApproachHelper(
                "Romeo", tile, tile, 3, null, null,
                p -> p != null && p.getPlane() == 0 && p.distanceTo(tile) <= 20);
    }

    public static QuestNpcApproachHelper fatherLawrence() {
        WorldPoint tile = new WorldPoint(3253, 3484, 0);
        return new QuestNpcApproachHelper(
                "Father Lawrence", tile, tile, 3, null, null,
                p -> p != null && p.getPlane() == 0 && p.distanceTo(tile) <= 16);
    }

    public static QuestNpcApproachHelper apothecary() {
        WorldPoint tile = new WorldPoint(3197, 3406, 0);
        WorldPoint enter = new WorldPoint(3195, 3404, 0);
        return new QuestNpcApproachHelper(
                "Apothecary", tile, tile, DEFAULT_TALK_DISTANCE, null, enter,
                QuestNpcApproachHelper::inApothecaryTalkZone);
    }

    public static QuestNpcApproachHelper dukeHoracio() {
        WorldPoint tile = new WorldPoint(3211, 3224, 1);
        WorldPoint talk = new WorldPoint(3210, 3224, 1);
        WorldPoint door = new WorldPoint(3207, 3227, 1);
        return new QuestNpcApproachHelper(
                "Duke Horacio", tile, talk, DEFAULT_TALK_DISTANCE, door,
                new WorldPoint(3206, 3226, 1),
                QuestNpcApproachHelper::inDukeHoracioTalkZone);
    }

    public static QuestNpcApproachHelper sedridor() {
        WorldPoint tile = new WorldPoint(3104, 9572, 0);
        return new QuestNpcApproachHelper(
                "Archmage Sedridor", tile, tile, DEFAULT_TALK_DISTANCE,
                new WorldPoint(3108, 9570, 0),
                new WorldPoint(3107, 9570, 0),
                WizardsTowerBasementHelper::inSedridorTalkZone);
    }

    public static QuestNpcApproachHelper aubury() {
        WorldPoint tile = new WorldPoint(3253, 3401, 0);
        return new QuestNpcApproachHelper(
                "Aubury", tile, tile, 3, null, null,
                p -> p != null && p.getPlane() == 0 && p.distanceTo(tile) <= 12);
    }

    public static boolean inDukeHoracioTalkZone(WorldPoint me) {
        if (me == null || me.getPlane() != 1) {
            return false;
        }
        return me.getX() >= 3206 && me.getX() <= 3212
                && me.getY() >= 3220 && me.getY() <= 3228;
    }

    public static boolean inApothecaryTalkZone(WorldPoint me) {
        if (me == null || me.getPlane() != 0) {
            return false;
        }
        return me.getX() >= APOTHECARY_MIN_X && me.getX() <= APOTHECARY_MAX_X
                && me.getY() >= APOTHECARY_MIN_Y && me.getY() <= APOTHECARY_MAX_Y;
    }

    public static boolean inGoblinGeneralsTalkZone(WorldPoint me) {
        if (me == null || me.getPlane() != 0) {
            return false;
        }
        return me.getX() >= GOBLIN_GENERALS_MIN_X && me.getX() <= GOBLIN_GENERALS_MAX_X
                && me.getY() >= GOBLIN_GENERALS_MIN_Y && me.getY() <= GOBLIN_GENERALS_MAX_Y;
    }

    public void reset() {
        lastWalkMs = 0L;
        lastDoorMs = 0L;
    }

    public INPC findNpc() {
        try {
            INPC exact = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().equalsIgnoreCase(npcName));
            if (exact != null && exact.getWorldLocation() != null
                    && exact.getWorldLocation().distanceTo(npcAnchor) <= 14) {
                return exact;
            }
            String low = npcName.toLowerCase(Locale.ROOT);
            return NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().toLowerCase(Locale.ROOT).contains(low)
                    && n.getWorldLocation() != null
                    && n.getWorldLocation().distanceTo(npcAnchor) <= 14);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean canTalkNow(WorldPoint me, INPC npc) {
        if (me == null || me.getPlane() != npcAnchor.getPlane()) {
            return false;
        }
        if (!insideRoom.test(me)) {
            return false;
        }
        WorldPoint npcLoc = npc != null && npc.getWorldLocation() != null
                ? npc.getWorldLocation() : null;
        if (inQuestTalkZone(me) && npcLoc != null && insideRoom.test(npcLoc)) {
            return true;
        }
        if (npcLoc != null && me.distanceTo(npcLoc) <= talkDistance) {
            return true;
        }
        return talkTile != null && me.distanceTo(talkTile) <= talkDistance;
    }

    private boolean inQuestTalkZone(WorldPoint me) {
        if (me == null || me.getPlane() != npcAnchor.getPlane()) {
            return false;
        }
        if ("Cook".equalsIgnoreCase(npcName)) {
            return me.getX() >= COOK_KITCHEN_MIN_X && me.getX() <= COOK_KITCHEN_MAX_X
                    && me.getY() >= COOK_KITCHEN_MIN_Y && me.getY() <= COOK_KITCHEN_MAX_Y;
        }
        if ("General".equalsIgnoreCase(npcName)) {
            return inGoblinGeneralsTalkZone(me);
        }
        if ("Apothecary".equalsIgnoreCase(npcName)) {
            return inApothecaryTalkZone(me);
        }
        if ("Duke Horacio".equalsIgnoreCase(npcName)) {
            return inDukeHoracioTalkZone(me);
        }
        if ("Archmage Sedridor".equalsIgnoreCase(npcName)) {
            return WizardsTowerBasementHelper.inSedridorTalkZone(me);
        }
        return false;
    }

    /**
     * @return delay &gt; 0 = still approaching; 0 = ready to Talk-to
     */
    public int approachBeforeTalk() {
        WorldPoint me = QuestActions.local();
        if (me == null) {
            return 600;
        }
        INPC npc = findNpc();
        if (canTalkNow(me, npc)) {
            return 0;
        }
        if (insideRoom.test(me)) {
            if (inQuestTalkZone(me)) {
                return npc != null ? 0 : rand(350, 600);
            }
            WorldPoint stand = talkTile != null ? talkTile : npcAnchor;
            if (me.distanceTo(stand) > talkDistance) {
                issueWalk(stand);
                return rand(400, 700);
            }
            return rand(350, 600);
        }
        if (doorTile != null && isDoorOpen()) {
            issueWalk(talkTile != null ? talkTile : npcAnchor);
            QuestLog.step("NPC", npcName + ": deur open → binnen");
            return rand(400, 700);
        }
        if (doorTile != null && nearDoor(me)) {
            if (tryOpenDoor(me)) {
                return rand(900, 1400);
            }
            if (doorApproachTile != null && me.distanceTo(doorApproachTile) > 3) {
                issueWalk(doorApproachTile);
                return rand(400, 700);
            }
            tryOpenDoor(me);
            return rand(800, 1200);
        }
        if (doorTile != null && doorApproachTile != null) {
            issueWalk(doorApproachTile);
        } else if (talkTile != null) {
            issueWalk(talkTile);
        } else {
            issueWalk(npcAnchor);
        }
        return rand(400, 700);
    }

    /**
     * Approach + Talk-to + dialog. {@code true} if an action was taken.
     */
    public boolean talk(QuestHelperDialogSteps dialog) {
        if (QuestDialogHelper.handle(dialog)) {
            return true;
        }
        int travel = approachBeforeTalk();
        if (travel > 0) {
            return true;
        }
        INPC npc = findNpc();
        WorldPoint me = QuestActions.local();
        if (npc == null || me == null || !canTalkNow(me, npc)) {
            if (npcAnchor != null) {
                issueWalk(npcAnchor);
            }
            return true;
        }
        if (npc.interact("Talk-to") || npc.interact("Talk")) {
            QuestLog.step("NPC", "Talk-to " + npcName);
            return true;
        }
        return false;
    }

    /**
     * Legacy static entry — generic walk/door/talk (RoomScanner). Prefer factories + {@link #talk}.
     */
    public static boolean approachAndTalk(String npcName, WorldPoint expectedTile,
                                          QuestHelperDialogSteps dialog) {
        if (QuestDialogHelper.handle(dialog)) {
            return true;
        }
        if ("Cook".equalsIgnoreCase(npcName)) {
            return lumbridgeCook().talk(dialog);
        }
        if (npcName != null && npcName.toLowerCase(Locale.ROOT).contains("general")) {
            return goblinGenerals().talk(dialog);
        }
        if ("Romeo".equalsIgnoreCase(npcName)) {
            return romeo().talk(dialog);
        }
        if ("Father Lawrence".equalsIgnoreCase(npcName)) {
            return fatherLawrence().talk(dialog);
        }
        if ("Apothecary".equalsIgnoreCase(npcName)) {
            return apothecary().talk(dialog);
        }
        if ("Duke Horacio".equalsIgnoreCase(npcName)) {
            return dukeHoracio().talk(dialog);
        }
        if (npcName != null && npcName.toLowerCase(Locale.ROOT).contains("sedridor")) {
            return sedridor().talk(dialog);
        }
        if ("Aubury".equalsIgnoreCase(npcName)) {
            return aubury().talk(dialog);
        }
        if ("Doric".equalsIgnoreCase(npcName)) {
            return QuestDoricHouseApproachHelper.talk(dialog);
        }
        return approachAndTalkGeneric(npcName, expectedTile, dialog);
    }

    private static boolean approachAndTalkGeneric(String npcName, WorldPoint expectedTile,
                                                  QuestHelperDialogSteps dialog) {
        INPC npc = NPCs.getNearest(npcName);
        WorldPoint me = QuestActions.local();
        if (npc == null) {
            if (expectedTile != null) {
                QuestLog.step("NPC", npcName + " niet in scene → "
                        + expectedTile.getX() + "," + expectedTile.getY());
                QuestActions.walkTo(expectedTile, 3);
                return true;
            }
            return false;
        }
        WorldPoint npcTile = npc.getWorldLocation();
        if (npcTile == null) {
            npcTile = expectedTile;
        }
        if (me != null && npcTile != null && me.getPlane() != npcTile.getPlane()) {
            if (expectedTile != null) {
                QuestActions.walkTo(expectedTile, 2);
            }
            return true;
        }
        int d = QuestActions.dist(npcTile);
        if (d > DEFAULT_TALK_DISTANCE) {
            QuestActions.walkTo(npcTile, DEFAULT_TALK_DISTANCE);
            return true;
        }
        if (npc.interact("Talk-to") || npc.interact("Talk")) {
            QuestLog.step("NPC", "Talk-to " + npcName);
            return true;
        }
        return false;
    }

    private boolean nearDoor(WorldPoint me) {
        return doorTile != null && (me.distanceTo(doorTile) <= 14
                || (doorApproachTile != null && me.distanceTo(doorApproachTile) <= 14)
                || insideRoom.test(me));
    }

    private ITileObject findDoor() {
        if (doorTile == null) {
            return null;
        }
        return TileObjects.getNearest(obj -> {
            if (obj == null || obj.getName() == null || obj.getWorldLocation() == null) {
                return false;
            }
            WorldPoint loc = obj.getWorldLocation();
            if (loc.getPlane() != doorTile.getPlane() || loc.distanceTo(doorTile) > 1) {
                return false;
            }
            return obj.getName().toLowerCase(Locale.ROOT).contains("door");
        });
    }

    private boolean isDoorOpen() {
        ITileObject door = findDoor();
        return door != null && door.hasAction("Close");
    }

    private boolean tryOpenDoor(WorldPoint me) {
        long now = System.currentTimeMillis();
        if (now - lastDoorMs < 800) {
            return false;
        }
        if (isDoorOpen() || insideRoom.test(me)) {
            return false;
        }
        ITileObject door = findDoor();
        if (door == null || !door.hasAction("Open")) {
            return false;
        }
        if (me.distanceTo(doorTile) > 2
                && (doorApproachTile == null || me.distanceTo(doorApproachTile) > 2)) {
            if (doorApproachTile != null) {
                issueWalk(doorApproachTile);
            }
            return false;
        }
        door.interact("Open");
        lastDoorMs = now;
        QuestLog.step("NPC", npcName + " deur Open @ " + door.getWorldLocation());
        return true;
    }

    private void issueWalk(WorldPoint target) {
        if (target == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 700L && Movement.isWalking()) {
            return;
        }
        lastWalkMs = now;
        QuestLog.step("NPC", npcName + " → " + target.getX() + "," + target.getY());
        Movement.walkTo(target);
    }

    private static int rand(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(max - min);
    }
}
