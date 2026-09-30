package net.storm.sdk.movement;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.magic.SpellBook;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.interact.InteractWalkHelper;
import net.storm.sdk.interact.InteractWalkHelper.PlayerSnapshot;
import net.storm.sdk.items.Bank;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.magic.TeleportTestHelper;
import net.storm.sdk.widgets.Dialog;

/**
 * Na een walk-klik: moving, tegel, animatie of gele vlag. Geen spam-klik.
 * Laatste redmiddel: Home Teleport als de 30 min cooldown het toelaat.
 */
final class WalkStuckFailsafe {

    private static final long GRACE_MS = 1_000L;
    private static final int MAX_DEAD_BEFORE_HOME = 4;
    private static final long DEAD_MS_BEFORE_HOME = 8_000L;
    private static final int MIN_DEST_FOR_HOME = 25;
    private static final int LAST_STRETCH_NO_HOME = 15;
    private static final long HOME_HOLD_MS = 22_000L;
    private static final int NEAR_HOME_TILES = 22;
    private static final long LOG_THROTTLE_MS = 1_600L;

    private static volatile PlayerSnapshot beforeClick;
    private static volatile long clickMs;
    private static volatile boolean awaiting;
    private static volatile boolean countedThisClick;
    private static volatile int deadClicks;
    private static volatile long firstDeadMs;
    private static volatile WorldPoint destSeen;
    private static volatile long homeUntilMs;
    private static volatile long lastHomeDeniedMs;
    private static volatile WorldPoint homeFrom;
    private static volatile String lastLog = "";
    private static volatile long lastLogMs;

    private WalkStuckFailsafe() {
    }

    static void reset() {
        beforeClick = null;
        clickMs = 0L;
        awaiting = false;
        countedThisClick = false;
        deadClicks = 0;
        firstDeadMs = 0L;
        destSeen = null;
        lastHomeDeniedMs = 0L;
        // homeUntilMs blijft tot land / timeout — clearPath mag hops niet hervatten tijdens Cast
    }

    static void noteIssued(PlayerSnapshot before) {
        beforeClick = before != null ? before : InteractWalkHelper.capture();
        clickMs = System.currentTimeMillis();
        awaiting = true;
        countedThisClick = false;
    }

    /**
     * @return true = deze tick niet hoppen (wacht op effect, backoff, of Home Teleport)
     */
    static boolean holdOrUnstuck(Players.LocalSnap me, WorldPoint dest, int remainingTiles) {
        long now = System.currentTimeMillis();
        if (dest == null || destSeen == null
                || dest.distanceTo(destSeen) > 3
                || dest.getPlane() != destSeen.getPlane()) {
            destSeen = dest;
            if (homeUntilMs < now) {
                deadClicks = 0;
                firstDeadMs = 0L;
            }
        }
        if (holdHomeTeleport(me, now)) {
            return true;
        }
        if (walkStarted(me)) {
            awaiting = false;
            countedThisClick = false;
            deadClicks = 0;
            firstDeadMs = 0L;
            lastHomeDeniedMs = 0L;
            return false;
        }
        if (!awaiting || clickMs <= 0L) {
            return false;
        }
        long since = now - clickMs;
        if (since < GRACE_MS) {
            return true;
        }
        if (!countedThisClick) {
            countedThisClick = true;
            deadClicks++;
            if (firstDeadMs <= 0L) {
                firstDeadMs = now;
            }
            logOnce("klik zonder loop (moving="
                    + (me != null && me.moving)
                    + " flag=" + fmt(me != null ? me.walkDestination : null)
                    + " anim=" + (me != null ? me.animation : -1)
                    + ") dead=" + deadClicks + " — geen spam");
        }
        if (tryHomeTeleport(me, dest, remainingTiles, now)) {
            return true;
        }
        long backoff = 1_100L + (long) deadClicks * 700L;
        if (backoff > 3_400L) {
            backoff = 3_400L;
        }
        if (since < backoff) {
            return true;
        }
        awaiting = false;
        return false;
    }

    private static boolean walkStarted(Players.LocalSnap me) {
        if (me == null || !me.present) {
            return false;
        }
        if (me.moving || me.walkDestination != null) {
            return true;
        }
        PlayerSnapshot before = beforeClick;
        if (before == null) {
            return false;
        }
        if (before.tile != null && me.worldLocation != null
                && (before.tile.getPlane() != me.worldLocation.getPlane()
                || before.tile.distanceTo(me.worldLocation) > 0)) {
            return true;
        }
        return me.animation != -1 && me.animation != before.animation;
    }

    private static boolean holdHomeTeleport(Players.LocalSnap me, long now) {
        if (homeUntilMs <= 0L) {
            return false;
        }
        if (isHomeTeleportAnim(me != null ? me.animation : -1)) {
            return true;
        }
        WorldPoint here = me != null ? me.worldLocation : null;
        if (here != null && here.getPlane() == 0
                && here.distanceTo(TeleportTestHelper.LAND_HOME) <= NEAR_HOME_TILES
                && (homeFrom == null || here.distanceTo(homeFrom) > 8)) {
            homeUntilMs = 0L;
            homeFrom = null;
            awaiting = false;
            deadClicks = 0;
            firstDeadMs = 0L;
            logOnce("Home Teleport land @" + here.getX() + "," + here.getY() + " — pad opnieuw");
            MovementHelper.clearPath();
            return true;
        }
        if (now >= homeUntilMs) {
            homeUntilMs = 0L;
            homeFrom = null;
            return false;
        }
        return true;
    }

    private static boolean tryHomeTeleport(Players.LocalSnap me, WorldPoint dest, int remainingTiles, long now) {
        if (dest == null || me == null || !me.present || me.worldLocation == null) {
            return false;
        }
        if (deadClicks < MAX_DEAD_BEFORE_HOME) {
            return false;
        }
        if (firstDeadMs > 0L && now - firstDeadMs < DEAD_MS_BEFORE_HOME) {
            return false;
        }
        if (remainingTiles > 0 && remainingTiles <= LAST_STRETCH_NO_HOME) {
            return false;
        }
        WorldPoint from = me.worldLocation;
        if (from.getPlane() == dest.getPlane() && from.distanceTo(dest) < MIN_DEST_FOR_HOME) {
            return false;
        }
        if (from.getPlane() == 0 && from.distanceTo(TeleportTestHelper.LAND_HOME) <= NEAR_HOME_TILES) {
            return false;
        }
        if (me.interacting) {
            logOnce("vast, maar in combat — geen Home Teleport");
            return false;
        }
        try {
            if (Bank.isOpen() || Dialog.isOpen()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        if (!Magic.canCast(SpellBook.Standard.HOME_TELEPORT)) {
            int left = Magic.homeTeleportMinutesLeft();
            if (left > 0) {
                logOnce("Home Teleport op cooldown (nog " + left + " min) — geen Cast, geen spam-klik");
            } else {
                logOnce("Home Teleport niet beschikbaar — geen Cast, geen spam-klik");
            }
            if (lastHomeDeniedMs <= 0L) {
                lastHomeDeniedMs = now;
            }
            if (now - lastHomeDeniedMs < 3_200L) {
                return true;
            }
            lastHomeDeniedMs = now;
            awaiting = false;
            return false;
        }
        homeFrom = from;
        boolean ok = Magic.cast(SpellBook.Standard.HOME_TELEPORT);
        if (!ok) {
            logOnce("Home Teleport Cast mislukt — later opnieuw, geen spam");
            awaiting = false;
            clickMs = now;
            return true;
        }
        homeUntilMs = now + HOME_HOLD_MS;
        awaiting = false;
        logOnce("Home Teleport (laatste redmiddel, CD ok) vanaf "
                + from.getX() + "," + from.getY()
                + " dest=" + dest.getX() + "," + dest.getY());
        return true;
    }

    private static boolean isHomeTeleportAnim(int anim) {
        return anim == 4847 || anim == 4850 || anim == 4853 || anim == 4855 || anim == 4857
                || (anim >= 1696 && anim <= 1701);
    }

    private static void logOnce(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < LOG_THROTTLE_MS) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Walk/stuck] " + msg);
    }

    private static String fmt(WorldPoint p) {
        return p == null ? "-" : (p.getX() + "," + p.getY());
    }
}
