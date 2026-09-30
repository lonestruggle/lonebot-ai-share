package net.storm.sdk.movement;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.Locatable;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.movement.IMovement;
import net.storm.api.movement.TilePath;
import net.storm.api.movement.WalkOptions;
import net.storm.api.movement.pathfinder.CollisionMap;
import net.storm.api.movement.pathfinder.model.Teleport;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Static;
import net.storm.sdk.game.Vars;
import net.storm.sdk.items.BankLocation;
import net.storm.sdk.widgets.Widgets;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * Storm {@code Movement} / {@code IMovement}: {@link #walk} = klik; {@link #walkTo} = collision-pad + doorlinken.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/Movement.html">Storm Movement</a>
 */
public final class Movement {

    /** Stamina potion effect (RuneLite {@code Varbits.RUN_SLOWED_DEPLETION_ACTIVE}). */
    private static final int STAMINA_VARBIT = 25;

    /**
     * Korte hop (Chebyshev, zelfde plane): max afstand voor {@link #walk}.
     * Daarboven → {@link #walkTo} (collision-pad). Zie lonebot-script-walk rule.
     */
    public static final int SHORT_WALK_TILES = 15;

    public static final IMovement API = new Api();

    static {
        net.storm.api.Static.bindMovement(API);
        TilePathWalker.init();
    }

    public Movement() {
    }

    /**
     * Storm {@code IMovement.walkTo} — pad bouwen, daarna hops klikken.
     * Zelfde als {@link MovementHelper#walkTo}.
     */
    public static boolean walkTo(WorldPoint worldPoint) {
        return MovementHelper.walkTo(worldPoint);
    }

    public static boolean walkTo(WorldPoint worldPoint, boolean useTeleports) {
        if (!useTeleports) {
            return walkTo(worldPoint);
        }
        return Walker.walkTo(worldPoint, WalkOptions.builder().useTeleports(true).build());
    }

    public static boolean walkTo(WorldPoint worldPoint, CollisionMap collisionMap) {
        return Walker.walkTo(worldPoint, WalkOptions.builder().collisionMap(collisionMap).build());
    }

    public static boolean walkTo(WorldPoint worldPoint, CollisionMap collisionMap, boolean useTeleports) {
        return Walker.walkTo(worldPoint, WalkOptions.builder()
                .collisionMap(collisionMap).useTeleports(useTeleports).build());
    }

    public static boolean walkTo(WorldPoint destination, WalkOptions options) {
        return Walker.walkTo(destination, options);
    }

    public static boolean walkTo(WorldArea worldArea) {
        return Walker.walkTo(worldArea, WalkOptions.builder().useTeleports(false).useTransports(true).build());
    }

    public static boolean walkTo(WorldArea worldArea, Boolean useTeleports) {
        boolean tele = Boolean.TRUE.equals(useTeleports);
        return Walker.walkTo(worldArea, WalkOptions.builder().useTeleports(tele).useTransports(true).build());
    }

    public static boolean walkTo(WorldArea worldArea, CollisionMap collisionMap) {
        return Walker.walkTo(worldArea, WalkOptions.builder().collisionMap(collisionMap).useTeleports(false).build());
    }

    public static boolean walkTo(WorldArea worldArea, CollisionMap collisionMap, Boolean useTeleports) {
        return Walker.walkTo(worldArea, collisionMap, Boolean.TRUE.equals(useTeleports));
    }

    public static boolean walkTo(WorldArea destination, WalkOptions options) {
        return Walker.walkTo(destination, options);
    }

    public static boolean walkTo(Locatable locatable) {
        return locatable != null && walkTo(locatable.getWorldLocation());
    }

    public static boolean walkTo(BankLocation bankLocation) {
        return bankLocation != null && walkTo(bankLocation.getWorldPoint());
    }

    public static boolean walkTo(int x, int y) {
        net.storm.sdk.entities.Players.LocalSnap me = net.storm.sdk.entities.Players.snapshotLocal();
        int plane = me != null && me.present && me.worldLocation != null ? me.worldLocation.getPlane() : 0;
        return walkTo(x, y, plane);
    }

    public static boolean walkTo(int x, int y, int plane) {
        return walkTo(new WorldPoint(x, y, plane));
    }

    /** Storm {@code IMovement.walk}: directe klik, geen pad. */
    public static void walk(WorldPoint worldPoint) {
        WalkClickHelper.walkTo(worldPoint);
    }

    public static void walk(Locatable locatable) {
        if (locatable != null) {
            walk(locatable.getWorldLocation());
        }
    }

    /**
     * {@code true} als zelfde plane en Chebyshev ≤ {@link #SHORT_WALK_TILES}
     * → gebruik {@link #walk}, anders {@link #walkTo}.
     */
    public static boolean isShortWalk(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return false;
        }
        return from.distanceTo(to) <= SHORT_WALK_TILES;
    }

    /**
     * Kort: {@link #walk}; lang: {@link #walkTo}.
     *
     * @return true als een walk-actie is uitgegeven / al op bestemming
     */
    public static boolean walkNearOrPath(WorldPoint dest) {
        if (dest == null) {
            return false;
        }
        net.storm.sdk.entities.Players.LocalSnap me = net.storm.sdk.entities.Players.snapshotLocal();
        WorldPoint from = me != null && me.present ? me.worldLocation : null;
        if (from == null) {
            return walkTo(dest);
        }
        if (from.getPlane() == dest.getPlane() && from.distanceTo(dest) <= 0) {
            return true;
        }
        if (isShortWalk(from, dest)) {
            walk(dest);
            return true;
        }
        return walkTo(dest);
    }

    public static TilePath getPath(WorldPoint dest) {
        return MovementHelper.getPath(dest);
    }

    public static TilePath getPath(WorldPoint destination, CollisionMap collisionMap) {
        return Walker.buildPath(destination != null ? destination.toWorldArea() : null,
                WalkOptions.builder().collisionMap(collisionMap).build());
    }

    public static TilePath getPath(WorldArea destination) {
        return Walker.buildPath(destination, WalkOptions.builder().build());
    }

    public static TilePath getPath(WorldArea destination, CollisionMap collisionMap) {
        return Walker.buildPath(destination, WalkOptions.builder().collisionMap(collisionMap).build());
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldPoint destination) {
        return getPath(startPoints, destination, null);
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldPoint destination, CollisionMap collisionMap) {
        WorldArea area = destination != null ? destination.toWorldArea() : null;
        return Walker.buildPath(startPoints, area, WalkOptions.builder().collisionMap(collisionMap).build());
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination) {
        return Walker.buildPath(startPoints, destination, WalkOptions.builder().build());
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, boolean useCache) {
        return Walker.buildPath(startPoints, destination, WalkOptions.builder().useCache(useCache).build());
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap) {
        return Walker.buildPath(startPoints, destination, WalkOptions.builder().collisionMap(collisionMap).build());
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination,
                                   CollisionMap collisionMap, boolean useCache) {
        return Walker.buildPath(startPoints, destination,
                WalkOptions.builder().collisionMap(collisionMap).useCache(useCache).build());
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination,
                                   CollisionMap collisionMap, boolean useCache, boolean useTransports) {
        return Walker.buildPath(startPoints, destination, WalkOptions.builder()
                .collisionMap(collisionMap).useCache(useCache).useTransports(useTransports).build());
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination,
                                   CollisionMap collisionMap, boolean useCache, boolean useTransports,
                                   HashMap<WorldPoint, Teleport> teleports) {
        WalkOptions options = WalkOptions.builder()
                .collisionMap(collisionMap).useCache(useCache).useTransports(useTransports).build();
        return Walker.buildPath(startPoints, destination, options, teleports, Walker.buildTransportLinks(options));
    }

    public static TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination,
                                   WalkOptions options, HashMap<WorldPoint, Teleport> teleports) {
        return Walker.buildPath(startPoints, destination, options, teleports, Walker.buildTransportLinks(options));
    }

    public static WorldPoint getNearestWalkableTile(WorldPoint source) {
        return Walker.getNearestWalkableTile(source, null, null);
    }

    public static WorldPoint getNearestWalkableTile(WorldPoint source, Predicate<WorldPoint> filter) {
        return Walker.getNearestWalkableTile(source, null, filter);
    }

    public static WorldPoint getNearestWalkableTile(WorldPoint source, CollisionMap collisionMap) {
        return Walker.getNearestWalkableTile(source, collisionMap, null);
    }

    public static WorldPoint getNearestWalkableTile(WorldPoint source, CollisionMap collisionMap,
                                                    Predicate<WorldPoint> filter) {
        return Walker.getNearestWalkableTile(source, collisionMap, filter);
    }

    public static void setDestination(int sceneX, int sceneY) {
        WorldPoint wp = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            LocalPoint lp = LocalPoint.fromScene(sceneX, sceneY);
            return lp != null ? WorldPoint.fromLocal(c, lp) : null;
        }, null);
        walk(wp);
    }

    public static boolean isWalking() {
        return isMoving();
    }

    public static boolean isStaminaBoosted() {
        return Vars.getVarbit(STAMINA_VARBIT) > 0;
    }

    public static void clearPath() {
        MovementHelper.clearPath();
    }

    /** Walk to local scene point via world conversion. */
    public static boolean walkToLocal(LocalPoint localPoint) {
        if (localPoint == null) {
            return false;
        }
        WorldPoint wp = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? WorldPoint.fromLocal(c, localPoint) : null;
        }, null);
        return WalkClickHelper.walkTo(wp);
    }

    public static boolean canClickWalk(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return false;
        }
        // Cheap check: in scene or has minimap projection handled inside helper resolve
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            return LocalPoint.fromWorld(c, worldPoint) != null;
        }, false);
    }

    public static Point resolveWalkClick(WorldPoint worldPoint) {
        // Kept for callers; prefer WalkClickHelper.walkTo for actual walking
        if (worldPoint == null) {
            return null;
        }
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, worldPoint);
            if (lp == null) {
                return null;
            }
            Point mini = net.runelite.api.Perspective.localToMinimap(
                    c, lp, net.runelite.api.Perspective.LOCAL_TILE_SIZE * 70);
            if (mini != null && mini.getX() >= 0) {
                return mini;
            }
            return net.runelite.api.Perspective.localToCanvas(c, lp, worldPoint.getPlane());
        }, null);
    }

    public static boolean isMoving() {
        try {
            net.storm.sdk.entities.Players.LocalSnap me =
                    net.storm.sdk.entities.Players.snapshotLocal();
            return me != null && me.present && me.moving;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int distanceTo(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return Integer.MAX_VALUE;
        }
        Integer d = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || c.getLocalPlayer() == null) {
                return Integer.MAX_VALUE;
            }
            WorldPoint me = c.getLocalPlayer().getWorldLocation();
            return me != null ? me.distanceTo(worldPoint) : Integer.MAX_VALUE;
        }, Integer.MAX_VALUE);
        return d != null ? d : Integer.MAX_VALUE;
    }

    public static boolean isNear(WorldPoint worldPoint, int maxDistance) {
        return distanceTo(worldPoint) <= maxDistance;
    }

    public static WorldPoint getDestination() {
        try {
            net.storm.sdk.entities.Players.LocalSnap me =
                    net.storm.sdk.entities.Players.snapshotLocal();
            return me != null && me.present ? me.walkDestination : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean hasDestination() {
        return getDestination() != null;
    }

    private static final long RUN_ORB_THROTTLE_MS = 1_800L;
    /** Jagex varp 173: 0 = lopen, 1 = rennen. Geen sprite — die lag vaak omgekeerd. */
    private static final int VARP_RUN = 173;
    /**
     * Klikbare run-orb. Officieel {@code 160,27} (RUNBUTTON). Boot-hover was {@code 160,30}.
     * Niet 28 (energie-tekst) en niet 33 (spec).
     */
    private static final int[] RUN_TOGGLE_CHILDREN = {27, 30, 25, 22};
    private static volatile long lastRunOrbMs;
    private static volatile long lastRunLogMs;
    private static volatile Boolean lastKnownRunOn;
    private static volatile Boolean pendingRunOn;
    private static volatile long pendingRunUntilMs;

    private static volatile long suppressAutoRunUntilMs;

    /**
     * Travel + idle: rennen volgens Auto-run (standaard aan vanaf 20% energie).
     * OSRS zet de orb zelf uit bij 0%; wij klikken hem weer aan vanaf de drempel.
     */
    public static void ensureTravelRun() {
        tickAutoRun();
    }

    /**
     * Elke tick aanroepen (ook zonder walk) zodat energie-herstel Auto-run triggert.
     */
    public static void tickAutoRun() {
        try {
            tickAutoRun0();
        } catch (Throwable ignored) {
        }
    }

    private static void tickAutoRun0() {
        if (net.storm.sdk.input.Mouse.isBusy()) {
            return;
        }
        try {
            if (net.storm.sdk.items.Bank.isOpen()) {
                return;
            }
        } catch (Throwable ignored) {
        }
        if (System.currentTimeMillis() < suppressAutoRunUntilMs) {
            return;
        }
        Boolean on = readRunVarpLive();
        if (on == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (pendingRunOn != null && now < pendingRunUntilMs) {
            if (on.equals(pendingRunOn)) {
                pendingRunOn = null;
            }
            return;
        }
        pendingRunOn = null;
        if (WalkClickSettings.isForceWalk()) {
            if (on && setRunEnabled(false)) {
                logRunConsole("auto-run: UIT (lopen gevraagd)");
            }
            return;
        }
        if (!WalkClickSettings.autoRun || !WalkClickSettings.preferRun) {
            return;
        }
        if (on) {
            return;
        }
        int min = Math.max(1, Math.min(100, WalkClickSettings.autoRunMinEnergy));
        int energy = getRunEnergy();
        // Nooit klikken zonder genoeg energie (OSRS: "You don't have enough energy left to run!")
        if (energy <= 0 || energy < min) {
            logRunConsole("auto-run: wacht energie " + energy + "% < " + min + "%",
                    energy <= 0 ? 8_000L : 2_000L);
            return;
        }
        if (setRunEnabled(true)) {
            logRunConsole("auto-run: AAN gezet @ " + energy + "% (≥" + min + ")");
        } else {
            logRunConsole("auto-run: klik mislukt");
        }
    }

    /**
     * Run energy 0–100%. RuneLite {@code Client#getEnergy()} is 0–10000 — altijd /100.
     * (Fout: raw≤100 als % behandelen → bij ~0% dacht de bot dat je 50–100% had.)
     */
    public static int getRunEnergy() {
        Integer pct = Static.callOnClientThread(() -> energyPercentFromClient(Static.getClient()), 0);
        return pct != null ? pct : 0;
    }

    private static int energyPercentFromClient(Client c) {
        if (c == null) {
            return 0;
        }
        try {
            int raw = c.getEnergy();
            if (raw < 0) {
                return 0;
            }
            // 0–10000 → 0–100%; waarden ≤100 zijn nog steeds 0.xx–1%, niet "100%"
            return Math.max(0, Math.min(100, raw / 100));
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Test-tab: korte AAN/UIT-regel. Widget-details → {@code [Walk/run/dbg]}.
     */
    public static String dumpRunState() {
        RunSnap snap = readRunSnap();
        logRunDbg(snap);
        String user = formatRunUser(snap);
        BotRuntime.logConsole("[Walk/run] " + user);
        return user;
    }

    /**
     * Test-tab: forceer run aan of uit, herlees na 700 ms. Auto-run 8 s stil.
     */
    public static String testSetRunEnabled(boolean enabled) {
        suppressAutoRunUntilMs = System.currentTimeMillis() + 8_000L;
        pendingRunOn = null;
        pendingRunUntilMs = 0L;
        lastRunOrbMs = 0L;
        RunSnap before = readRunSnap();
        logRunDbg(before);
        if (before.on != null && before.on == enabled) {
            String user = "Run was al " + (enabled ? "AAN" : "UIT") + " — niets geklikt";
            BotRuntime.logConsole("[Walk/run] " + user);
            return user;
        }
        if (enabled && getRunEnergy() < 1) {
            String user = "Run AAN — geen energie, orb niet geklikt";
            BotRuntime.logConsole("[Walk/run] " + user);
            return user;
        }
        boolean clicked = setRunEnabled(enabled);
        try {
            Thread.sleep(700L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        RunSnap after = readRunSnap();
        logRunDbg(after);
        boolean nowOn = Boolean.TRUE.equals(after.on);
        boolean ok = after.on != null && nowOn == enabled;
        String user;
        if (!clicked && after.on != null && after.on != enabled) {
            user = "Run " + (enabled ? "AAN" : "UIT") + " — klik mislukt, nu " + (nowOn ? "AAN" : "UIT");
        } else if (ok) {
            user = "Run " + (enabled ? "AAN" : "UIT") + " gezet — gelukt, nu " + (nowOn ? "AAN" : "UIT");
        } else {
            user = "Run " + (enabled ? "AAN" : "UIT") + " gezet — MISLUKT, nu "
                    + (after.on == null ? "?" : (nowOn ? "AAN" : "UIT"));
        }
        BotRuntime.logConsole("[Walk/run] " + user);
        return user;
    }

    private static String formatRunUser(RunSnap snap) {
        if (snap == null || snap.on == null) {
            return "Run: onbekend (geen client)";
        }
        return "Run staat " + (snap.on ? "AAN" : "UIT") + " (energie " + snap.energy + "%)";
    }

    private static void logRunDbg(RunSnap snap) {
        if (snap == null || snap.dbg == null || snap.dbg.isBlank()) {
            return;
        }
        BotRuntime.logConsole("[Walk/run/dbg] " + snap.dbg);
    }

    private static RunSnap readRunSnap() {
        RunSnap snap = Static.callOnClientThread(() -> {
            String dbg = dumpRunStateOnClient();
            Client c = Static.getClient();
            if (c == null) {
                return new RunSnap(null, 0, dbg);
            }
            int varp = 0;
            try {
                varp = c.getVarpValue(VARP_RUN);
            } catch (Throwable ignored) {
            }
            int energy = 0;
            try {
                energy = energyPercentFromClient(c);
            } catch (Throwable ignored) {
            }
            lastKnownRunOn = varp == 1;
            return new RunSnap(varp == 1, energy, dbg);
        }, null);
        return snap != null ? snap : new RunSnap(null, 0, "geen-thread");
    }

    private static final class RunSnap {
        final Boolean on;
        final int energy;
        final String dbg;

        RunSnap(Boolean on, int energy, String dbg) {
            this.on = on;
            this.energy = energy;
            this.dbg = dbg;
        }
    }

    private static String dumpRunStateOnClient() {
        Client c = Static.getClient();
        if (c == null) {
            return "geen-client";
        }
        int varp = 0;
        try {
            varp = c.getVarpValue(VARP_RUN);
            lastKnownRunOn = varp == 1;
        } catch (Throwable ignored) {
        }
        int energy = 0;
        try {
            energy = energyPercentFromClient(c);
        } catch (Throwable ignored) {
        }
        StringBuilder sb = new StringBuilder();
        sb.append("varp173=").append(varp).append(varp == 1 ? " →AAN" : " →UIT");
        sb.append(" energy=").append(energy);
        sb.append(" autoRun=").append(WalkClickSettings.autoRun);
        net.runelite.api.widgets.Widget pick = resolveRunOrbWidget(c);
        sb.append(" klik=");
        if (pick == null) {
            sb.append("geen");
        } else {
            sb.append("160,").append(pick.getId() & 0xFFFF);
        }
        int[] kids = {27, 30, 25, 28, 22};
        for (int k : kids) {
            net.runelite.api.widgets.Widget w;
            try {
                w = c.getWidget(160, k);
            } catch (Throwable t) {
                w = null;
            }
            sb.append(" | 160,").append(k).append("=");
            if (w == null) {
                sb.append("null");
                continue;
            }
            sb.append(w.isHidden() ? "hidden" : "vis");
            sb.append(" spr=").append(w.getSpriteId());
            String[] ac = w.getActions();
            if (ac != null) {
                sb.append(" act=[");
                boolean first = true;
                for (String a : ac) {
                    if (a == null || a.isBlank()) {
                        continue;
                    }
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    sb.append(a.replaceAll("<[^>]+>", ""));
                }
                sb.append(']');
            }
            try {
                String tx = w.getText();
                if (tx != null && !tx.isBlank()) {
                    sb.append(" txt=").append(tx.trim());
                }
            } catch (Throwable ignored) {
            }
        }
        return sb.toString();
    }

    public static boolean setRunEnabled(boolean enabled) {
        Boolean on = readRunVarpLive();
        if (on == null) {
            return false;
        }
        if (on == enabled) {
            pendingRunOn = null;
            return true;
        }
        if (enabled) {
            int energy = getRunEnergy();
            if (energy <= 0) {
                logRunConsole("skip AAN — energie 0", 8_000L);
                return false;
            }
        }
        long now = System.currentTimeMillis();
        if (pendingRunOn != null && pendingRunOn == enabled && now < pendingRunUntilMs) {
            return true;
        }
        if (now - lastRunOrbMs < RUN_ORB_THROTTLE_MS) {
            return false;
        }
        if (!toggleRun()) {
            return false;
        }
        lastRunOrbMs = now;
        pendingRunOn = enabled;
        pendingRunUntilMs = now + RUN_ORB_THROTTLE_MS;
        return true;
    }

    /**
     * Toggle run via de echte orb ({@code Toggle Run} op 160,27 — niet energie-tekst of spec).
     * Bij 0% energie weigert AAN-zetten: OSRS zet de orb zelf uit, klikken spamt alleen.
     */
    public static boolean toggleRun() {
        long now = System.currentTimeMillis();
        if (now - lastRunOrbMs < RUN_ORB_THROTTLE_MS) {
            return false;
        }
        Boolean on = readRunVarpLive();
        if (on != null && !on && getRunEnergy() < 1) {
            logRunConsole("geen energie — run-orb niet klikken", 8_000L);
            return false;
        }
        Boolean menu = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            net.runelite.api.widgets.Widget orb = resolveRunOrbWidget(c);
            if (orb == null) {
                return false;
            }
            IWidget wrap = Widgets.wrap(orb);
            return wrap != null && wrap.hasAction("Toggle Run") && wrap.interact("Toggle Run");
        }, false);
        if (Boolean.TRUE.equals(menu)) {
            lastRunOrbMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /**
     * Run-orb aan = varp 173 is 1. Timeout/fail telt niet als “uit”.
     */
    public static boolean isRunEnabled() {
        Boolean live = readRunVarpLive();
        if (live != null) {
            return live;
        }
        return Boolean.TRUE.equals(lastKnownRunOn);
    }

    /** Live varp, of {@code null} bij timeout — dan niet klikken. */
    private static Boolean readRunVarpLive() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                boolean on = c.getVarpValue(VARP_RUN) == 1;
                lastKnownRunOn = on;
                return on;
            } catch (Throwable t) {
                return null;
            }
        }, null);
    }

    private static net.runelite.api.widgets.Widget resolveRunOrbWidget(Client c) {
        for (int child : RUN_TOGGLE_CHILDREN) {
            net.runelite.api.widgets.Widget w = c.getWidget(160, child);
            if (w != null && !w.isHidden() && widgetHasToggleRun(w)) {
                return w;
            }
        }
        for (int child = 20; child <= 32; child++) {
            net.runelite.api.widgets.Widget w = c.getWidget(160, child);
            if (w != null && !w.isHidden() && widgetHasToggleRun(w)) {
                return w;
            }
        }
        return null;
    }

    private static boolean widgetHasToggleRun(net.runelite.api.widgets.Widget w) {
        try {
            String[] actions = w.getActions();
            if (actions == null) {
                return false;
            }
            for (String a : actions) {
                if (a != null && a.replaceAll("<[^>]+>", "").equalsIgnoreCase("Toggle Run")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void logRunConsole(String detail) {
        logRunConsole(detail, 2_000L);
    }

    private static void logRunConsole(String detail, long minIntervalMs) {
        long now = System.currentTimeMillis();
        if (now - lastRunLogMs < Math.max(500L, minIntervalMs)) {
            return;
        }
        lastRunLogMs = now;
        BotRuntime.logConsole("[Walk/run] " + detail + " energy=" + getRunEnergy());
    }

    /** Path length in tiles via global pathfinder; falls back to Chebyshev. */
    public static int calculateDistance(WorldPoint worldPoint) {
        return calculateDistance((WorldPoint) null, worldPoint);
    }

    public static int calculateDistance(WorldArea destination) {
        if (destination == null) {
            return Integer.MAX_VALUE;
        }
        return calculateDistance((WorldPoint) null, destination);
    }

    public static int calculateDistance(WorldPoint start, WorldPoint destination) {
        if (destination == null) {
            return Integer.MAX_VALUE;
        }
        WorldPoint from = start != null ? start : localOrNull();
        if (from == null) {
            return Integer.MAX_VALUE;
        }
        try {
            TilePath path = start == null
                    ? MovementHelper.getPath(destination)
                    : getPath(java.util.Collections.singletonList(start), destination);
            if (path != null && !path.isEmpty()) {
                return path.size();
            }
        } catch (Throwable ignored) {
        }
        return from.distanceTo(destination);
    }

    public static int calculateDistance(WorldPoint start, WorldArea destination) {
        if (destination == null) {
            return Integer.MAX_VALUE;
        }
        WorldPoint from = start != null ? start : localOrNull();
        WorldPoint dest = destination.toWorldPoint();
        if (from == null || dest == null) {
            return Integer.MAX_VALUE;
        }
        try {
            TilePath path = getPath(start != null ? java.util.Collections.singletonList(start) : java.util.Collections.emptyList(),
                    destination);
            if (path != null && !path.isEmpty()) {
                return path.size();
            }
        } catch (Throwable ignored) {
        }
        return from.distanceTo(dest);
    }

    public static int calculateDistance(List<WorldPoint> start, WorldPoint destination) {
        if (destination == null) {
            return Integer.MAX_VALUE;
        }
        TilePath path = getPath(start, destination);
        if (path != null && !path.isEmpty()) {
            return path.size();
        }
        WorldPoint from = start != null && !start.isEmpty() ? start.get(0) : localOrNull();
        return from != null ? from.distanceTo(destination) : Integer.MAX_VALUE;
    }

    public static int calculateDistance(List<WorldPoint> start, WorldArea destination) {
        if (destination == null) {
            return Integer.MAX_VALUE;
        }
        TilePath path = getPath(start, destination);
        if (path != null && !path.isEmpty()) {
            return path.size();
        }
        WorldPoint from = start != null && !start.isEmpty() ? start.get(0) : localOrNull();
        WorldPoint dest = destination.toWorldPoint();
        return from != null && dest != null ? from.distanceTo(dest) : Integer.MAX_VALUE;
    }

    /** Walk toward destination until within radius tiles. */
    public static boolean walkToArea(WorldPoint center, int radius) {
        if (center == null) {
            return false;
        }
        if (distanceTo(center) <= Math.max(0, radius)) {
            return true;
        }
        return walkTo(center);
    }

    private static WorldPoint localOrNull() {
        net.storm.sdk.entities.Players.LocalSnap me = net.storm.sdk.entities.Players.snapshotLocal();
        return me != null && me.present ? me.worldLocation : null;
    }

    private static final class Api implements IMovement {
        @Override
        public void setDestination(int sceneX, int sceneY) {
            Movement.setDestination(sceneX, sceneY);
        }

        @Override
        public WorldPoint getDestination() {
            return Movement.getDestination();
        }

        @Override
        public boolean isWalking() {
            return Movement.isWalking();
        }

        @Override
        public void walk(WorldPoint worldPoint) {
            Movement.walk(worldPoint);
        }

        @Override
        public boolean walkTo(WorldPoint worldPoint) {
            return Movement.walkTo(worldPoint);
        }

        @Override
        public boolean walkTo(WorldArea worldArea) {
            return Movement.walkTo(worldArea);
        }

        @Override
        public boolean walkTo(WorldArea area, CollisionMap collisionMap, boolean useTeleports) {
            return Movement.walkTo(area, collisionMap, useTeleports);
        }

        @Override
        public boolean walkTo(WorldArea area, WalkOptions options) {
            return Movement.walkTo(area, options);
        }

        @Override
        public boolean isRunEnabled() {
            return Movement.isRunEnabled();
        }

        @Override
        public void toggleRun() {
            Movement.toggleRun();
        }

        @Override
        public boolean isStaminaBoosted() {
            return Movement.isStaminaBoosted();
        }

        @Override
        public int getRunEnergy() {
            return Movement.getRunEnergy();
        }

        @Override
        public TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, CollisionMap collisionMap,
                                boolean useCache, boolean useTransports, HashMap<WorldPoint, Teleport> teleports) {
            return Movement.getPath(startPoints, destination, collisionMap, useCache, useTransports, teleports);
        }

        @Override
        public TilePath getPath(Collection<WorldPoint> startPoints, WorldArea destination, WalkOptions options,
                                HashMap<WorldPoint, Teleport> teleports) {
            return Movement.getPath(startPoints, destination, options, teleports);
        }

        @Override
        public WorldPoint getNearestWalkableTile(WorldPoint source, CollisionMap collisionMap, Predicate<WorldPoint> filter) {
            return Movement.getNearestWalkableTile(source, collisionMap, filter);
        }
    }
}
