package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.actors.IPlayer;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.Reachable;

/**
 * Open NPC (geen muur/deur): Talk-to mag binnen {@value #OPEN_NPC_TALK_DISTANCE} tiles
 * <b>of</b> bij line of sight — anders doorlopen tot interactie kan.
 * CombatBot {@code OpenNpcApproachHelper} port.
 */
public final class ClueOpenNpcApproachHelper {

    public static final int OPEN_NPC_TALK_DISTANCE = 15;

    private final WorldPoint anchor;
    private final String logLabel;

    private long lastWalkMs;
    private long lastLogMs;
    private String lastLogMsg = "";

    public static ClueOpenNpcApproachHelper charlie() {
        return new ClueOpenNpcApproachHelper(BeginnerClueReference.CHARLIE_TILE, "Charlie");
    }

    public ClueOpenNpcApproachHelper(WorldPoint anchor, String logLabel) {
        this.anchor = anchor;
        this.logLabel = logLabel != null ? logLabel : "NPC";
    }

    public WorldPoint getAnchor() {
        return anchor;
    }

    public static boolean hasLineOfSight(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return false;
        }
        try {
            return Reachable.hasLineOfSight(from, to);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Speler mag Talk-to: ≤15 tiles tot NPC/anchor <b>of</b> LOS.
     */
    public boolean canTalkNow(WorldPoint me, INPC npc) {
        if (me == null || me.getPlane() != anchor.getPlane()) {
            return false;
        }
        WorldPoint target = npc != null && npc.getWorldLocation() != null
                ? npc.getWorldLocation() : anchor;
        if (me.distanceTo(target) <= OPEN_NPC_TALK_DISTANCE) {
            return true;
        }
        if (me.distanceTo(anchor) <= OPEN_NPC_TALK_DISTANCE) {
            return true;
        }
        return hasLineOfSight(me, target) || hasLineOfSight(me, anchor);
    }

    public boolean canInteractNow(WorldPoint me, INPC npc) {
        return npc != null && canTalkNow(me, npc);
    }

    /**
     * @return 0 = klaar voor Talk-to; &gt;0 = delay onderweg
     */
    public int approachBeforeTalk(IPlayer local, INPC npc) {
        WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null) {
            return 600;
        }
        if (canInteractNow(me, npc) || (npc == null && canTalkNow(me, null))) {
            return 0;
        }
        WorldPoint walkTarget = npc != null && npc.getWorldLocation() != null
                ? npc.getWorldLocation() : anchor;
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900L && Movement.isWalking()) {
            return 280;
        }
        lastWalkMs = now;
        logThrottled("[Clue/" + logLabel + "] walk → " + walkTarget.getX() + "," + walkTarget.getY()
                + " (nu " + me.getX() + "," + me.getY()
                + ", dist=" + me.distanceTo(walkTarget) + ")");
        Movement.walkTo(walkTarget);
        return 400;
    }

    public int approachBeforeTalk(INPC npc) {
        return approachBeforeTalk(Players.getLocal(), npc);
    }

    public void reset() {
        lastWalkMs = 0L;
        lastLogMs = 0L;
        lastLogMsg = "";
    }

    private void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLogMsg) && now - lastLogMs < 1_500L) {
            return;
        }
        lastLogMs = now;
        lastLogMsg = msg;
        BotRuntime.logConsole(msg);
    }
}
