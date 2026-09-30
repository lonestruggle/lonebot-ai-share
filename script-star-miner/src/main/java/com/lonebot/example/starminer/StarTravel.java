package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.movement.Movement;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Travel naar ster via de algemene API-walker ({@link Movement#walkTo}).
 * Geen eigen hops/settings — zelfde A→B als Imp / Fish / WC.
 * Alle modes: Mine-COS zodra de ster op het scherm / in range is — niet eerst naar de stand-tegel.
 */
final class StarTravel {

    private static final int ARRIVE = 6;
    /** Mode 5: Mine zodra ster in bereik (niet pas op 6 tegels). */
    private static final int MINE_WITHIN_TILES = 30;

    private static volatile WorldPoint dest;
    private static volatile String label = "ster";
    private static volatile boolean active;
    private static int interleaveWalkLeft;
    private static long lastLogMs;
    private static String lastLog = "";

    private static final AtomicBoolean bgRun = new AtomicBoolean(false);
    private static Thread bgThread;

    private StarTravel() {
    }

    static void reset() {
        stopBackground();
        dest = null;
        label = "ster";
        active = false;
        interleaveWalkLeft = 0;
    }

    static boolean isActive() {
        return active && dest != null;
    }

    static WorldPoint destination() {
        return dest;
    }

    static String modeTag() {
        return StarMinerPlugin.travelMode().name();
    }

    /**
     * Start / refresh travel naar ster-stand. Zelfde dest sticky houden.
     */
    static void arm(WorldPoint stand, String lab) {
        if (stand == null) {
            return;
        }
        WorldPoint prev = dest;
        boolean same = prev != null
                && prev.getPlane() == stand.getPlane()
                && prev.distanceTo(stand) <= 4;
        if (!same) {
            dest = stand;
            label = lab != null ? lab : "ster";
            interleaveWalkLeft = 3;
            // Oud pad (vorige ster) weg — anders COS naar oude eindtegel na hop
            try {
                WorldPoint old = net.storm.sdk.movement.MovementHelper.getActiveDestination();
                if (old == null || old.getPlane() != stand.getPlane() || old.distanceTo(stand) > 6) {
                    net.storm.sdk.movement.MovementHelper.clearPath();
                }
            } catch (Throwable ignored) {
            }
            log("arm " + modeTag() + " → " + stand.getX() + "," + stand.getY());
        }
        active = true;
        // Geen aparte bg-thread / walker-settings: lopen = Movement.walkTo (API).
        stopBackground();
    }

    /** Start A* tijdens hop (zelfde coords na land) — niet wachten tot geland. */
    static void prefetch(WorldPoint stand) {
        if (stand == null) {
            return;
        }
        try {
            net.storm.sdk.movement.MovementHelper.getPath(stand);
            log("prefetch A* → " + stand.getX() + "," + stand.getY());
        } catch (Throwable ignored) {
        }
    }

    static void clear() {
        cancelWalker(active ? modeTag() : "clear");
    }

    /**
     * Na Mine-COS / minen: LoopHost mag het pad naar stand-tegel B niet doorlopen.
     * Zelfde idee als WC {@code stopCenterApproach}.
     */
    static void cancelWalker(String why) {
        boolean hadTravel = active && dest != null;
        WorldPoint ad = null;
        boolean ww = false;
        try {
            ad = net.storm.sdk.movement.MovementHelper.getActiveDestination();
        } catch (Throwable ignored) {
        }
        try {
            ww = net.storm.sdk.movement.WorldWalker.isScriptTravel();
        } catch (Throwable ignored) {
        }
        stopBackground();
        dest = null;
        active = false;
        interleaveWalkLeft = 0;
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        try {
            net.storm.sdk.movement.WorldWalker.cancelScriptTravel();
        } catch (Throwable ignored) {
        }
        if (hadTravel || ad != null || ww) {
            String was = ad != null ? (" was " + ad.getX() + "," + ad.getY()) : "";
            log("walker uit (" + why + ")" + was);
        }
    }

    /**
     * Fatale interrupt (hop-confirm, logout, bank) — travel pauzeren zonder clear.
     */
    static void pauseBackground() {
        // background blijft dest houden maar worker checkt active flag via dest
    }

    /**
     * @return travel-result (loop moet returnen), of {@code null} = deze tick andere Star-logica
     */
    static StarWalk.Result tick(WorldPoint pos, ITileObject star, INPC npc) {
        if (!active || dest == null) {
            return null;
        }
        StarTravelMode mode = StarMinerPlugin.travelMode();
        int d = dist(pos, dest);
        // Ver: geen scene-scan (getName/Mine op alle objecten) — dat hield hops vast.
        if (d < 0 || d > MINE_WITHIN_TILES) {
            return walkOnce(pos, label + "/" + mode.number);
        }
        StarWalk.Result mined = tryApproachMine(pos, star, npc, true);
        if (mined != null) {
            return mined;
        }
        if (d >= 0 && d <= ARRIVE) {
            StarWalk.Result arrived = StarWalk.Result.arrived(d, label);
            clear();
            return arrived;
        }

        switch (mode) {
            case WALK_ONLY:
            case PRE_TICK:
            case IMP_CLONE:
            case FATAL_ONLY:
                return walkOnce(pos, label + "/" + mode.number);

            case BACKGROUND:
                // Poort dicht: niet spam — main-loop skip via failed
                if (pos != null && dest != null
                        && net.storm.sdk.movement.pathfinder.AlKharidGate.crossesGateWithoutPass(pos, dest)) {
                    net.storm.sdk.movement.pathfinder.AlKharidGate.logSkip();
                    clear();
                    return StarWalk.Result.wait(
                            ThreadLocalRandom.current().nextInt(200, 360),
                            label + " poort geblokkeerd", true);
                }
                // Elke tick walkTo (Imp) — niet alleen bg @300ms (dat voelde als stap-voor-stap)
                return walkOnce(pos, label + "/3");

            case INTERLEAVE_3_1:
                if (interleaveWalkLeft > 0) {
                    interleaveWalkLeft--;
                    return walkOnce(pos, label + "/6w");
                }
                interleaveWalkLeft = 3;
                log("interleave yield (other)");
                return null; // andere logica één tick

            case FORCE_IF_MOVING: {
                boolean moving = false;
                try {
                    moving = Movement.isMoving() || Movement.getDestination() != null;
                } catch (Throwable ignored) {
                }
                if (moving || d > ARRIVE) {
                    return walkOnce(pos, label + "/7");
                }
                return walkOnce(pos, label + "/7");
            }

            case SHORT_HOPS:
                return walkOnce(pos, label + "/8");

            case LARGE_HOPS:
                return walkOnce(pos, label + "/9");

            case INTERACT_THEN_WALK:
                return walkOnce(pos, label + "/10");

            case WORLD_WALKER:
                return walkOnce(pos, label + "/11");

            default:
                return walkOnce(pos, label);
        }
    }

    /**
     * Mine tijdens lopen: alleen echte COS/invoke-klik, geen walk naar het object
     * (dat vocht met de pad-walker).
     */
    private static StarWalk.Result tryApproachMine(WorldPoint pos, ITileObject star, INPC npc,
                                                    boolean allowScan) {
        ITileObject s = star;
        INPC n = npc;
        if (allowScan && !StarInspect.present(s, n) && dest != null) {
            s = StarObjects.nearest(dest);
            // Geen NPC-scan tijdens travel (getName op alle NPCs = 1.5s → hop uitlopen)
        }
        if (!StarInspect.present(s, n)) {
            return null;
        }
        if (!canMineNow(s)) {
            return null;
        }
        boolean onScreen = ClickOnSight.onScreen(s) || (n != null && ClickOnSight.onScreen(n));
        boolean inRange = ClickOnSight.can(s) || (n != null && ClickOnSight.can(n));
        if (!onScreen && !inRange) {
            return null;
        }
        boolean clicked = false;
        if (s != null) {
            ClickOnSight.Result r = ClickOnSight.interact(s, "Mine");
            clicked = r != null && r.clicked;
        }
        if (!clicked && n != null) {
            ClickOnSight.Result r = ClickOnSight.interact(n, "Mine");
            clicked = r != null && r.clicked;
        }
        if (!clicked && !tryMine(s, n)) {
            return null;
        }
        WorldPoint tile = StarInspect.tile(s, n);
        int dStar = dist(pos, tile != null ? tile : dest);
        log("COS Mine d=" + dStar + " " + modeTag());
        clear();
        return StarWalk.Result.wait(ThreadLocalRandom.current().nextInt(450, 800),
                label + " COS Mine");
    }

    private static boolean canMineNow(ITileObject star) {
        try {
            int mining = net.storm.sdk.game.Skills.getLevel(net.runelite.api.Skill.MINING);
            int live = StarObjects.liveTier(star);
            if (live > 0) {
                int need = StarObjects.miningLevelForTier(live);
                return need <= 0 || mining >= need;
            }
            int prospectNeed = StarInspect.prospectNeed();
            if (prospectNeed > 0) {
                return mining >= prospectNeed;
            }
            // Scene-ster zonder bekende laag: toch Mine als object Mine-actie heeft
            // (feed-doel was al minebaar; voorkomt doorlopen tot 6 tegels)
            return star != null && star.hasAction("Mine");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Mode 2/7: forceer walk aan het begin van de loop als travel actief.
     * @return result om meteen te returnen, of null
     */
    static StarWalk.Result preLoopWalk(WorldPoint pos) {
        if (!active || dest == null) {
            return null;
        }
        int d = dist(pos, dest);
        if (d < 0 || d > MINE_WITHIN_TILES) {
            return walkOnce(pos, label + "/" + StarMinerPlugin.travelMode().number);
        }
        StarWalk.Result mined = tryApproachMine(pos, resolveStar(), null, true);
        if (mined != null) {
            return mined;
        }
        StarTravelMode mode = StarMinerPlugin.travelMode();
        if (mode == StarTravelMode.PRE_TICK) {
            return tick(pos, resolveStar(), null);
        }
        if (mode == StarTravelMode.FORCE_IF_MOVING) {
            boolean moving = false;
            try {
                moving = Movement.isMoving() || Movement.getDestination() != null;
            } catch (Throwable ignored) {
            }
            if (moving) {
                return tick(pos, resolveStar(), null);
            }
        }
        if (mode == StarTravelMode.WALK_ONLY
                || mode == StarTravelMode.FATAL_ONLY
                || mode == StarTravelMode.IMP_CLONE
                || mode == StarTravelMode.BACKGROUND
                || mode == StarTravelMode.SHORT_HOPS
                || mode == StarTravelMode.LARGE_HOPS
                || mode == StarTravelMode.INTERACT_THEN_WALK) {
            return tick(pos, resolveStar(), null);
        }
        return null;
    }

    private static ITileObject resolveStar() {
        WorldPoint hint = dest;
        if (hint == null) {
            return null;
        }
        return StarObjects.nearest(hint);
    }

    private static INPC resolveNpc() {
        WorldPoint hint = dest;
        if (hint == null) {
            return null;
        }
        return StarInspect.nearestNpc(hint);
    }

    /** Modes die gear/feed/prospect overslaan tijdens travel. */
    static boolean exclusiveTravel() {
        if (!isActive()) {
            return false;
        }
        StarTravelMode mode = StarMinerPlugin.travelMode();
        switch (mode) {
            case WALK_ONLY:
            case PRE_TICK:
            case FATAL_ONLY:
            case IMP_CLONE:
            case FORCE_IF_MOVING:
            case SHORT_HOPS:
            case LARGE_HOPS:
            case INTERACT_THEN_WALK:
            case WORLD_WALKER:
            case BACKGROUND:
                // BACKGROUND: ook exclusief — anders AntiBan/gear → stilstand tussen hops
                return true;
            case INTERLEAVE_3_1:
                return false;
            default:
                return true;
        }
    }

    /** Mode 5: alleen fatale interrupts mogen travel breken — caller checkt. */
    static boolean allowOnlyFatalInterrupts() {
        return StarMinerPlugin.travelMode() == StarTravelMode.FATAL_ONLY && isActive();
    }

    private static StarWalk.Result walkOnce(WorldPoint pos, String lab) {
        return StarWalk.toward(pos, dest, ARRIVE, lab);
    }

    private static boolean tryMine(ITileObject star, INPC npc) {
        try {
            net.runelite.api.TileObject raw = star != null ? TileObjects.unwrap(star) : null;
            if (raw != null && MenuInteract.interactObject(raw, "Mine")) {
                return true;
            }
            if (npc != null && MenuInteract.interactNpcByIndex(npc.getIndex(), "Mine")) {
                return true;
            }
            if (star != null && star.hasAction("Mine") && star.interact("Mine")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void startBackground() {
        // No-op: mode 3 = main-tick walk (Imp). Oude bg-thread veroorzaakte stap-spam + verkeerde hops.
        stopBackground();
    }

    private static void ensureBackground() {
        stopBackground();
    }

    private static void stopBackground() {
        bgRun.set(false);
        Thread t = bgThread;
        bgThread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    private static int dist(WorldPoint pos, WorldPoint d) {
        if (pos == null || d == null || pos.getPlane() != d.getPlane()) {
            return -1;
        }
        return pos.distanceTo(d);
    }

    private static void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/travel] " + msg);
    }
}
