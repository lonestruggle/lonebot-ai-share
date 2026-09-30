package net.storm.sdk.interact;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.widgets.Dialog;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Storm/CombatBot interact-verify flow: snapshot vóór klik → grace → {@link #hadEffect}
 * → anders {@link #fallbackPathfind}. Scripts hergebruiken dit i.p.v. eigen sleep-loops.
 * <p>
 * Blauwdruk: CombatBot {@code InteractWalkHelper} / lonebot-api {@code InteractWalk}.
 * <p>
 * <b>Arrive-interact anti-ping-pong:</b> klik terwijl je nog loopt → verify mag
 * {@link #fallbackPathfind} <i>niet</i> opnieuw walken als je al naast het doel bent
 * of al onderweg. Dat voorkomt hak/attack → walk → klik-lussen zonder walker-hops te tunen.
 */
public final class InteractWalkHelper {

    public enum EffectKind {
        WALK_OR_TILE,
        ANIMATION,
        DIALOG,
        COMBAT_OR_ANIM
    }

    /** Resultaat van {@link #approachIfNeeded}. */
    public enum ApproachResult {
        /** Al dicht genoeg — geen walk. */
        ARRIVED,
        /** Al onderweg / moving naar doel — geen nieuwe walk. */
        EN_ROUTE,
        /** Nieuwe walk uitgegeven. */
        WALKED,
        /** Geen dest / geen actie. */
        NONE
    }

    public static final class PlayerSnapshot {
        public final WorldPoint tile;
        public final int animation;
        public final boolean moving;
        public final boolean dialogOpen;
        public final long capturedMs;

        public PlayerSnapshot(WorldPoint tile, int animation, boolean moving,
                              boolean dialogOpen, long capturedMs) {
            this.tile = tile;
            this.animation = animation;
            this.moving = moving;
            this.dialogOpen = dialogOpen;
            this.capturedMs = capturedMs;
        }
    }

    public static final class PendingVerify {
        public final PlayerSnapshot before;
        public final EffectKind kind;
        public final long verifyAfterMs;

        public PendingVerify(PlayerSnapshot before, EffectKind kind, long verifyAfterMs) {
            this.before = before;
            this.kind = kind;
            this.verifyAfterMs = verifyAfterMs;
        }

        public boolean isDue() {
            return System.currentTimeMillis() >= verifyAfterMs;
        }
    }

    public static final int DEFAULT_GRACE_MIN_MS = 220;
    public static final int DEFAULT_GRACE_MAX_MS = 420;
    /** Standaard: al op/naast doel-tegel → geen fallback-walk. */
    public static final int DEFAULT_NEAR_TILES = 1;

    private InteractWalkHelper() {
    }

    public static PlayerSnapshot capture() {
        Players.LocalSnap me = Players.snapshotLocal();
        if (me == null || !me.present) {
            return new PlayerSnapshot(null, -1, false, false, System.currentTimeMillis());
        }
        boolean dialog = false;
        try {
            dialog = Dialog.isOpen();
        } catch (Throwable ignored) {
        }
        return new PlayerSnapshot(me.worldLocation, me.animation, me.moving, dialog,
                System.currentTimeMillis());
    }

    public static boolean hadEffect(PlayerSnapshot before, EffectKind kind) {
        if (before == null || kind == null) {
            return false;
        }
        Players.LocalSnap after = Players.snapshotLocal();
        if (after == null || !after.present) {
            return false;
        }
        switch (kind) {
            case DIALOG:
                try {
                    return Dialog.isOpen() && !before.dialogOpen;
                } catch (Throwable t) {
                    return false;
                }
            case ANIMATION:
                return after.animation != -1 && after.animation != before.animation;
            case WALK_OR_TILE:
                return movingOrTileChanged(before, after);
            case COMBAT_OR_ANIM:
            default:
                if (after.animation != -1 && after.animation != before.animation) {
                    return true;
                }
                return movingOrTileChanged(before, after);
        }
    }

    /**
     * True als speler al dicht bij {@code dest} is, of al die kant op loopt.
     * Gebruik vóór een tweede {@code walkTo} na interact-klik.
     */
    public static boolean isNearOrEnRoute(WorldPoint dest) {
        return isNearOrEnRoute(dest, DEFAULT_NEAR_TILES);
    }

    public static boolean isNearOrEnRoute(WorldPoint dest, int nearTiles) {
        if (dest == null) {
            return false;
        }
        int near = Math.max(0, nearTiles);
        try {
            if (MovementHelper.alreadyEnRoute(dest)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me == null || !me.present || me.worldLocation == null) {
            return false;
        }
        if (me.worldLocation.getPlane() == dest.getPlane()
                && me.worldLocation.distanceTo(dest) <= near) {
            return true;
        }
        if (me.moving || me.walkDestination != null) {
            WorldPoint flag = me.walkDestination;
            if (flag != null && flag.getPlane() == dest.getPlane()
                    && flag.distanceTo(dest) <= Math.max(4, near + 3)) {
                return true;
            }
            // Moving zonder flag maar al dichtbij genoeg voor interact
            if (me.worldLocation.getPlane() == dest.getPlane()
                    && me.worldLocation.distanceTo(dest) <= near + 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Alleen walken als nodig — voorkomt arrive-interact ping-pong.
     */
    public static ApproachResult approachIfNeeded(WorldPoint dest) {
        return approachIfNeeded(dest, DEFAULT_NEAR_TILES);
    }

    public static ApproachResult approachIfNeeded(WorldPoint dest, int nearTiles) {
        if (dest == null) {
            return ApproachResult.NONE;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me != null && me.present && me.worldLocation != null
                && me.worldLocation.getPlane() == dest.getPlane()
                && me.worldLocation.distanceTo(dest) <= Math.max(0, nearTiles)) {
            return ApproachResult.ARRIVED;
        }
        if (isNearOrEnRoute(dest, nearTiles)) {
            return ApproachResult.EN_ROUTE;
        }
        boolean walked = MovementHelper.walkTo(dest);
        return walked ? ApproachResult.WALKED : ApproachResult.NONE;
    }

    public static PendingVerify pendingVerify(PlayerSnapshot before, EffectKind kind) {
        return pendingVerify(before, kind, DEFAULT_GRACE_MIN_MS, DEFAULT_GRACE_MAX_MS);
    }

    public static PendingVerify pendingVerify(PlayerSnapshot before, EffectKind kind,
                                              int graceMinMs, int graceMaxMs) {
        int min = Math.max(80, graceMinMs);
        int max = Math.max(min, graceMaxMs);
        int grace = min + (max > min ? ThreadLocalRandom.current().nextInt(max - min + 1) : 0);
        return new PendingVerify(before, kind, System.currentTimeMillis() + grace);
    }

    public static boolean canDirectInteract(INPC npc) {
        return ClickOnSight.can(npc);
    }

    public static boolean canDirectInteract(ITileObject obj) {
        return ClickOnSight.can(obj);
    }

    public static boolean tryDirectInteract(INPC npc, String action) {
        if (!canDirectInteract(npc) || action == null || !npc.hasAction(action)) {
            return false;
        }
        boolean ok = npc.interact(action);
        if (ok) {
            BotRuntime.logConsole("[Interact] direct NPC " + action);
        }
        return ok;
    }

    public static boolean tryDirectInteract(ITileObject obj, String action) {
        if (!canDirectInteract(obj) || action == null || !obj.hasAction(action)) {
            return false;
        }
        boolean ok = obj.interact(action);
        if (ok) {
            BotRuntime.logConsole("[Interact] direct object " + action);
        }
        return ok;
    }

    /**
     * Failed verify → pathfinder, <b>tenzij</b> al naast/onderweg (geen ping-pong).
     * Raakt walker-hops/minimap niet — alleen of er überhaupt een nieuwe walk start.
     */
    public static boolean fallbackPathfind(WorldPoint destination) {
        return fallbackPathfind(destination, DEFAULT_NEAR_TILES);
    }

    public static boolean fallbackPathfind(WorldPoint destination, int nearTiles) {
        if (destination == null) {
            return false;
        }
        ApproachResult r = approachIfNeeded(destination, nearTiles);
        if (r == ApproachResult.ARRIVED) {
            BotRuntime.logConsole("[Interact] fallback skip — al bij "
                    + destination.getX() + "," + destination.getY());
            return true;
        }
        if (r == ApproachResult.EN_ROUTE) {
            BotRuntime.logConsole("[Interact] fallback skip — al onderweg → "
                    + destination.getX() + "," + destination.getY());
            return true;
        }
        if (r == ApproachResult.WALKED) {
            BotRuntime.logConsole("[Interact] fallback pathfind → "
                    + destination.getX() + "," + destination.getY());
            return true;
        }
        return false;
    }

    public static boolean interactOrWalk(ITileObject obj, String action) {
        if (tryDirectInteract(obj, action)) {
            return true;
        }
        WorldPoint tile = obj != null ? obj.getWorldLocation() : null;
        if (tile != null) {
            approachIfNeeded(tile, DEFAULT_NEAR_TILES);
        }
        return false;
    }

    public static boolean interactOrWalk(INPC npc, String action) {
        if (tryDirectInteract(npc, action)) {
            return true;
        }
        WorldPoint tile = npc != null ? npc.getWorldLocation() : null;
        if (tile != null) {
            approachIfNeeded(tile, 2);
        }
        return false;
    }

    private static boolean movingOrTileChanged(PlayerSnapshot before, Players.LocalSnap after) {
        if (after.moving) {
            return true;
        }
        if (before.tile == null || after.worldLocation == null) {
            return false;
        }
        return before.tile.getPlane() != after.worldLocation.getPlane()
                || before.tile.distanceTo(after.worldLocation) > 0;
    }
}
