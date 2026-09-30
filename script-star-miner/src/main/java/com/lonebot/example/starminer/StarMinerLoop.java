package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Game;
import net.storm.sdk.game.Skills;
import net.storm.sdk.game.WorldHopGate;
import net.storm.sdk.game.Worlds;
import net.storm.sdk.community.WorldHopper;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.utils.AntiBan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hop → loop naar crash-tegel → Mine. Te hoge laag → andere ster, anders logout.
 */
public final class StarMinerLoop {

    private static final int[] UNCUT_GEMS = {1623, 1621, 1619, 1617, 1625, 1627, 1629, 1631};
    /** Wacht tot de wereld écht wisselt na hopToWorld (HOPPED ≠ geland). */
    private static final long HOP_LAND_WAIT_MS = 5_500L;
    /** Dichtbij locatie, ster nooit gezien — snel door. */
    private static final long STAR_GONE_MS = 2_200L;
    /** Object weg: laag-wissel vs écht opgemined. Kort = valse skip tijdens minen. */
    private static final long STAR_GONE_WAIT_MS = 12_000L;
    /** Aangekomen, nooit een ster gezien → geen naloop, snel volgende. */
    private static final long STAR_GONE_ARRIVED_MS = 2_500L;
    /** Binnen deze afstand: stop lopen als er geen ster-object is. */
    private static final int EMPTY_SITE_TILES = 12;
    /** Object-ID wisselt bij laag-drop — niet hoppen. */
    private static final long STAR_LAYER_SWAP_MS = 25_000L;
    private static final long HOP_SETTLE_MS = 250L;
    private static final int WALK_FAIL_SKIP = 2;
    /** Tussen mine-swings is animatie even uit — geen herklik, OSRS blijft minen. */
    private static final long MINE_IDLE_MS = 4_200L;
    /**
     * AFK-menselijk: om de 2–3 min één Mine-klik (ook tijdens minen), daarna weer laten lopen.
     * Alleen als de ster er nog is en Mining de laag aankan.
     */
    private static final long AFK_RECLICK_MIN_MS = 2 * 60_000L;
    private static final long AFK_RECLICK_MAX_MS = 3 * 60_000L;
    /** Na een Mine-klik: blijf bij ster bij laag-wissel — geen herklik-timer. */
    private static final long MINE_SESSION_MS = 20_000L;
    private static final long EMPTY_FEED_LOGOUT_MS = 30_000L;
    private static final int SCENE_STAR_TILES = 20;
    /** WorldWalker-handoff = dezelfde 30 als Mine-COS. */
    private static final int WW_HANDOFF = 30;
    /** COS-klik zonder mine-swing: daarna weer lopen naar dezelfde ster. */
    private static final long MINE_CLICK_GRACE_MS = 1_800L;
    /** HP/animatie recent: ster is niet weg. */
    private static final long STAR_ALIVE_MS = 12_000L;

    private String status = "idle";
    private StarCall target;
    private long hopIssuedAt;
    private int hopWorld;
    private long nearSinceMs;
    private long lastMineMs;
    private long lastMiningAnimMs;
    /** Volgende AFK Mine-tik (2–3 min na laatste klik). 0 = nog niet gepland. */
    private long nextAfkReclickMs;
    private long lastLogMs;
    private String lastLog = "";
    private long noMineableSinceMs;
    private boolean logoutIssued;
    private int hopperEscTries;
    private int hopAttempts;
    private int logoutTries;
    private int seenWorld;
    private int lastLoggedLiveTier;
    private WorldPoint lastStarTile;
    /** Crash-tegel van de laatste ster — blijft na skip, zodat gem-bank die plek overslaat. */
    private WorldPoint lastGemAvoidTile;
    private long lastStarSeenMs;
    private boolean sawStarThisVisit;
    private int walkFails;
    private long hopLandedAtMs;
    /** Vast walk-doel tijdens lange aanloop (Imp dock-stijl — niet elke tick hersnapen). */
    private WorldPoint lockedTravelDest;
    private long lastWwLogMs;
    private final StarGearPrep gear = new StarGearPrep();
    private final StarGemBank gemBank = new StarGemBank();
    /** Wacht-ster: tijdelijk weg voor minebare → terug als die nog leeft. */
    private StarCall parkedWait;
    private final StarWaitHelper waitSide = new StarWaitHelper();
    private final Set<Integer> skipWorlds = ConcurrentHashMap.newKeySet();
    private final Set<String> skipSpots = ConcurrentHashMap.newKeySet();
    /** Deze ster afmaken — geen hogere feed-ster tot hij écht weg is. */
    private boolean committedToStar;
    private boolean parkResumePending;
    private long hopStuckSinceMs;
    private long lastHopLoopRestartMs;
    private long lastHopScriptReloadMs;

    public StarMinerLoop() {
        StarParkHandoff.bind(this);
    }

    public void reset() {
        status = "idle";
        BotRuntime.starSuccessCount = 0;
        target = null;
        hopIssuedAt = 0L;
        hopWorld = 0;
        nearSinceMs = 0L;
        lastMineMs = 0L;
        lastMiningAnimMs = 0L;
        nextAfkReclickMs = 0L;
        noMineableSinceMs = 0L;
        logoutIssued = false;
        hopperEscTries = 0;
        hopAttempts = 0;
        logoutTries = 0;
        seenWorld = 0;
        lastLoggedLiveTier = -1;
        clearStarSeen();
        walkFails = 0;
        hopLandedAtMs = 0L;
        lockedTravelDest = null;
        StarTravel.reset();
        cancelOwnedWorldWalker();
        gear.reset();
        gemBank.reset();
        waitSide.reset();
        StarInspect.reset();
        skipWorlds.clear();
        skipSpots.clear();
        parkedWait = null;
        committedToStar = false;
        parkResumePending = false;
        hopStuckSinceMs = 0L;
        lastHopLoopRestartMs = 0L;
        lastHopScriptReloadMs = 0L;
        lastGemAvoidTile = null;
    }

    public String getStatus() {
        return status;
    }

    public boolean isLoggingOut() {
        return logoutIssued;
    }

    public void cancelLogout() {
        if (!logoutIssued) {
            return;
        }
        logoutIssued = false;
        logoutTries = 0;
        log("logout afgebroken (Stop/Pauze)");
    }

    /** Stop/Pauze: travel + pad stoppen (1× per AAN→UIT, niet elke tick). */
    public void onBotStopped() {
        cancelLogout();
        StarTravel.reset();
        lockedTravelDest = null;
        try {
            net.storm.sdk.movement.WorldWalker.cancelScriptTravel();
        } catch (Throwable ignored) {
        }
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        log("Stop/Pauze — travel + pad gestopt");
        BotRuntime.starHopWorld = 0;
    }

    /** Andere skill tot de feed een minebare ster heeft. */
    public void onParkLeave() {
        cancelLogout();
        committedToStar = false;
        target = null;
        parkedWait = null;
        lockedTravelDest = null;
        StarTravel.clear();
        cancelOwnedWorldWalker();
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        hopWorld = 0;
        hopAttempts = 0;
        hopStuckSinceMs = 0L;
        BotRuntime.starHopWorld = 0;
        log("park — andere skill tot minebare ster");
    }

    /** Terug van WC/Fish/combat: pad uit, daarna gear prep + minebare ster. */
    public void onParkResume() {
        cancelLogout();
        committedToStar = false;
        target = null;
        parkedWait = null;
        lockedTravelDest = null;
        StarTravel.clear();
        cancelOwnedWorldWalker();
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        hopWorld = 0;
        hopAttempts = 0;
        hopStuckSinceMs = 0L;
        parkResumePending = true;
        noMineableSinceMs = 0L;
        BotRuntime.starHopWorld = 0;
        log("park hervat — gear prep, dan minebare ster");
    }

    /** Stop → Start: volle reset. Pauze → Start: alleen logout-poging stoppen. */
    public void onBotStarted(boolean resumeAfterPause) {
        if (resumeAfterPause) {
            logoutIssued = false;
            logoutTries = 0;
            noMineableSinceMs = 0L;
            return;
        }
        reset();
    }

    public List<String> debugLines() {
        try {
            if (Bank.isOpen()) {
                List<String> bank = new ArrayList<>();
                bank.add("Bank open — geen feed/hopper-scan");
                if (status != null && !status.isBlank()) {
                    bank.add(status);
                }
                return bank;
            }
        } catch (Throwable ignored) {
        }
        List<String> lines = new ArrayList<>();
        StarFeed feed = StarFeed.get();
        try {
            feed.publishUiSnapshot(target);
        } catch (Throwable ignored) {
        }
        int mining = 0;
        try {
            mining = Skills.getLevel(Skill.MINING);
        } catch (Throwable ignored) {
        }
        boolean avoid = StarMinerPlugin.avoidWilderness;
        boolean f2p = StarMinerPlugin.f2pOnly;
        int mineNow = feed.countMineableNow(mining, avoid, f2p, skipWorlds);
        int visit = feed.countUsable(mining, avoid, f2p, skipWorlds);
        lines.add("Feed: " + feed.active().size() + " sterren · nu " + mineNow
                + " · wacht " + Math.max(0, visit - mineNow)
                + " · " + StarFeed.filterDebug(mining, avoid, f2p)
                + " · D:" + (feed.discordOk() ? "ok" : "-")
                + " P:" + (feed.portalOk() ? "ok" : "-")
                + " G:" + (feed.ggOk() ? "ok" : "-"));
        lines.add("Travel: " + StarMinerPlugin.travelMode().label
                + (StarMinerPlugin.worldWalkerTravel() ? " · WW" : "")
                + (StarTravel.isActive() ? " · AAN" : " · uit"));
        if (target != null) {
            lines.add("Doel: " + target.label());
            WorldPoint tile = target.tile();
            if (tile != null) {
                WorldPoint pos = null;
                try {
                    Players.LocalSnap me = Players.snapshotLocal();
                    pos = me != null ? me.worldLocation : null;
                } catch (Throwable ignored) {
                }
                int d = (pos != null && pos.getPlane() == tile.getPlane())
                        ? pos.distanceTo(tile) : -1;
                lines.add("Tegel: " + tile.getX() + "," + tile.getY()
                        + (d >= 0 ? "  (" + d + " tegels)" : ""));
            }
        }
        if (hopWorld > 0) {
            int cur = Worlds.getCurrentWorld();
            if (cur != hopWorld) {
                lines.add("Hop: nu W" + cur + " → W" + hopWorld);
            }
        }
        lines.add("Totaal: " + WorldHopGate.cachedTotalLevel()
                + " · F2P-skills: " + WorldHopGate.cachedF2pTotal());
        lines.add("Stars succes: " + BotRuntime.starSuccessCount);
        lines.add("Mining: " + mining + " · wereld " + Worlds.getCurrentWorld()
                + " · pick " + StarPickaxes.label(StarPickaxes.currentOnPerson())
                + " · extra=+" + StarMinerPlugin.waitTiersAbove()
                + " (T" + StarObjects.highestMineableTier(mining)
                + "→max T" + StarMinerPlugin.maxVisitTier(mining) + ")"
                + " · tijdens=" + StarMinerPlugin.waitActivity().shortLabel
                + (StarParkHandoff.parkSkillEnabled()
                ? " · wacht-skill=" + StarParkHandoff.parkWaitSkill() : ""));
        try {
            if (StarTravel.isActive()) {
                WorldPoint td = StarTravel.destination();
                lines.add("Loop → " + (td != null ? td.getX() + "," + td.getY() : "-")
                        + " (geen scene-scan tijdens travel)");
            } else {
                Players.LocalSnap me = Players.snapshotLocal();
                WorldPoint p = me != null ? me.worldLocation : null;
                ITileObject st = StarObjects.nearest();
                INPC npc = StarInspect.nearestNpc(p);
                lines.add(StarInspect.overlayLine(st, npc));
            }
        } catch (Throwable ignored) {
        }
        return lines;
    }

    public int tick() {
        if (!Game.isLoggedIn()) {
            if (logoutIssued) {
                BotRuntime.botEnabled = false;
                logoutIssued = false;
                status = "uitgelogd";
                log("uitgelogd");
            } else {
                status = "niet ingelogd";
                log("niet ingelogd");
            }
            return 800;
        }
        GameState gs = Game.getState();
        if (gs == GameState.HOPPING || gs == GameState.LOADING) {
            status = "hoppen… " + gs;
            log("gameState=" + gs);
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        if (logoutIssued) {
            status = "uitloggen…";
            logoutTries++;
            boolean clicked = false;
            try {
                clicked = Game.logout();
            } catch (Throwable t) {
                log("logout fout: " + t.getClass().getSimpleName());
            }
            log("logout klik=" + clicked + " #" + logoutTries);
            if (logoutTries >= 8) {
                log("logout lukt niet — stop poging (geen loop)");
                logoutIssued = false;
                logoutTries = 0;
                BotRuntime.botEnabled = false;
                status = "logout mislukt";
            }
            return ThreadLocalRandom.current().nextInt(500, 800);
        }

        try {
            if (WorldHopper.isHopConfirmOpen()) {
                boolean clicked = WorldHopper.confirmSwitchWorld();
                status = "hop bevestigen";
                log("Switch world (193,0) klik=" + clicked);
                hopIssuedAt = System.currentTimeMillis();
                return ThreadLocalRandom.current().nextInt(400, 650);
            }
        } catch (Throwable t) {
            log("hop-confirm fout: " + t.getClass().getSimpleName());
        }

        // Imp loopt door: geen Bank.isOpen / hopper-widget elke tick (1.5s wait → hop uitlopen).
        noteMineClickTimeout();
        Players.LocalSnap holdSnap = Players.snapshotLocal();
        if (deferHopForGemBank()) {
            holdStarHopForGems();
        } else if (needsWorldHop()) {
            cancelTravelForHop();
            return hopTo(hopTargetWorld());
        }
        if (holdMiningCancelWalk(holdSnap)) {
            // WW / far-walk niet starten — COS Mine heeft voorrang
        } else {
            int teleDelay = tickStarTeleportIfUseful();
            if (teleDelay >= 0) {
                return teleDelay;
            }
            int wwTravel = tickWorldWalkerTravel();
            if (wwTravel >= 0) {
                return wwTravel;
            }
            int farWalk = tickFarTravelOnly();
            if (farWalk >= 0) {
                return farWalk;
            }
        }

        try {
            if (Worlds.isHopperOpen()) {
                if (deferHopForGemBank()) {
                    holdStarHopForGems();
                } else if (needsWorldHop()) {
                    cancelTravelForHop();
                    return hopTo(hopTargetWorld());
                }
                if (!(StarMinerPlugin.f2pOnly && !Worlds.hasWorldTypeCache())) {
                    WorldHopper.dismissToInventory();
                }
                hopperEscTries = 0;
            } else {
                hopperEscTries = 0;
            }
        } catch (Throwable ignored) {
        }

        try {
            net.storm.sdk.movement.Movement.ensureTravelRun();
        } catch (Throwable ignored) {
        }

        // Zelfde globale AntiBan als Imp/WC/Fish (panel-toggles). Skip bij hop/bank.
        boolean bankOpen = false;
        try {
            bankOpen = Bank.isOpen();
        } catch (Throwable ignored) {
        }
        boolean hopping = hopWorld > 0 && Worlds.getCurrentWorld() != hopWorld;
        boolean traveling = false;
        try {
            traveling = StarTravel.isActive();
        } catch (Throwable ignored) {
        }
        // Imp slaat AntiBan over tijdens reis (boot). Star deed dat niet → hop uitlopen + stop.
        if (!hopping && !bankOpen && !gemBank.isActive() && !traveling) {
            int anti = AntiBan.get().check();
            if (anti > 0) {
                status = "antiban";
                return Math.min(anti, 2500);
            }
        }

        try {
            int curW = Worlds.getCurrentWorld();
            if (seenWorld != 0 && seenWorld != curW) {
                StarInspect.reset();
                clearStarSeen();
                walkFails = 0;
                if (StarTravel.isActive() && StarTravel.destination() != null) {
                    hopLandedAtMs = 0L;
                    log("wereld " + seenWorld + " → " + curW + " — pad behouden, loop");
                } else {
                    hopLandedAtMs = System.currentTimeMillis();
                    lockedTravelDest = null;
                    StarTravel.clear();
                    try {
                        net.storm.sdk.movement.MovementHelper.clearPath();
                    } catch (Throwable ignored) {
                    }
                    try {
                        net.storm.sdk.movement.WorldWalker.cancel();
                    } catch (Throwable ignored) {
                    }
                    log("wereld " + seenWorld + " → " + curW + " — pad/travel reset");
                }
            }
            seenWorld = curW;
            if (hopLandedAtMs > 0L && !StarTravel.isActive()
                    && System.currentTimeMillis() - hopLandedAtMs < HOP_SETTLE_MS) {
                status = "na hop…";
                return ThreadLocalRandom.current().nextInt(80, 160);
            }
            // Na hop geland: gems banken als drempel (niet tijdens minen / travel)
            if (hopLandedAtMs > 0L && !StarTravel.isActive() && gemBank.shouldBank(false)) {
                hopLandedAtMs = 0L;
                gemBank.arm();
                int gd = tickGemBank();
                status = gemBank.status();
                return gd;
            }
            if (hopLandedAtMs > 0L && System.currentTimeMillis() - hopLandedAtMs >= HOP_SETTLE_MS) {
                hopLandedAtMs = 0L;
            }
        } catch (Throwable ignored) {
        }

        if (gemBank.isActive()) {
            int gd = tickGemBank();
            status = gemBank.status();
            return gd;
        }

        if (Inventory.isFull()) {
            IInventoryItem gem = Inventory.getFirst(UNCUT_GEMS);
            if (gem != null) {
                gem.interact("Drop");
                status = "drop gem (inv vol)";
                return ThreadLocalRandom.current().nextInt(350, 600);
            }
        }

        int mining = Skills.getLevel(Skill.MINING);
        Players.LocalSnap meEarly = Players.snapshotLocal();
        WorldPoint posEarly = meEarly != null ? meEarly.worldLocation : null;

        // Exclusive travel (modes 1/2/4/5/7/8/9/10): skip gear/feed tot aankomst
        if (holdMiningCancelWalk(meEarly)) {
            // minen: geen travel-tick
        } else if (needsWorldHop()) {
            StarTravel.clear();
            lockedTravelDest = null;
        } else if (nearEmptyStarSite(posEarly, preferSceneStar(mining, posEarly))) {
            StarTravel.clear();
        } else if (skipStarWalkForWorldWalker()) {
            // hop of WorldWalker: geen tweede walkTo
        } else if (StarTravel.isActive() && StarTravel.exclusiveTravel()) {
            StarWalk.Result wr = StarTravel.preLoopWalk(posEarly);
            if (wr != null && !wr.arrived) {
                status = wr.status;
                noteCosMineFromTravel(wr);
                return afterWalk(wr, mining);
            }
        } else if (StarTravel.isActive()) {
            // Mode 3/6: tick (bg=null → door; interleave walk of yield)
            StarWalk.Result wr = StarTravel.tick(posEarly, null, null);
            if (wr != null && !wr.arrived) {
                status = wr.status;
                noteCosMineFromTravel(wr);
                return afterWalk(wr, mining);
            }
        }

        StarCall sceneStay = preferSceneStar(mining, posEarly);
        if (sceneStay == null && stayAtStarSite(meEarly, posEarly)
                && (committedToStar || starStillHere(meEarly))) {
            int exclude = target != null ? target.world : 0;
            if (pickNext(mining, true, exclude) == null) {
                sceneStay = target;
                log("ster even weg (laag-wissel?) — blijf hier, geen hop");
            }
        }

        if (parkResumePending) {
            parkResumePending = false;
            StarCall travelable = pickNext(mining, true, 0);
            if (travelable == null) {
                StarParkHandoff.noteFalseResume();
                if (StarParkHandoff.forcePark(this, "hervat zonder bereikbare ster")) {
                    status = "park " + StarParkHandoff.parkWaitSkill();
                    return ThreadLocalRandom.current().nextInt(200, 400);
                }
                status = "geen bereikbare ster — Fish/WC niet storen";
                return 800;
            }
            log("park hervat → " + travelable.label());
        }

        boolean gearNow = gear.needs();
        // Bij ster: geen dump-prep, wél banken voor tele/coins als die ontbreken
        if ((committedToStar || sceneStay != null) && StarPickaxes.onPerson() && !gear.needsTravelKit()) {
            gearNow = false;
        }
        if (gearNow && !(StarTravel.isActive() && StarTravel.exclusiveTravel())) {
            int delay = gear.tick();
            status = gear.status();
            return delay;
        }

        // Start / tussen sterren: gems banken vóór hop/travel (niet tijdens mine)
        if (!isKeepMining() && gemBank.shouldBank(false)
                && !(StarTravel.isActive() && StarTravel.exclusiveTravel())) {
            gemBank.arm();
            int gd = tickGemBank();
            status = gemBank.status();
            return gd;
        }
        try {
            if (Bank.isOpen()) {
                Bank.close();
                status = "bank sluiten";
                log("bank nog open — geen hop");
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
        } catch (Throwable ignored) {
        }

        // F2P-filter: 1× hopper-lijst cachen — blijft actief na hop (geen herladen)
        if (StarMinerPlugin.f2pOnly && !Worlds.hasWorldTypeCache()) {
            try {
                WorldHopper.ensureWorldListLoaded();
                Worlds.refreshWorldTypeCacheFromLive(true);
                if (!Worlds.hasWorldTypeCache()) {
                    status = "F2P-wereldlijst laden";
                    log("F2P-cache leeg — eenmalig hopper openen");
                    return ThreadLocalRandom.current().nextInt(200, 360);
                }
                log("F2P-cache klaar n=" + Worlds.cachedF2pCount());
            } catch (Throwable ignored) {
            }
        }

        StarCall next = chooseTarget(mining, sceneStay, meEarly);
        if (next == null) {
            try {
                StarFeed.get().ingestPlayerChat();
            } catch (Throwable ignored) {
            }
            next = chooseTarget(mining, sceneStay, meEarly);
        }
        if (next == null) {
            target = null;
            return noMineableThenLogout(mining);
        }
        if (next.spot != null && !StarSpotGate.accessible(next.spot)) {
            StarSpotGate.logSkipOnce(next.spot);
            skipWorlds.add(next.world);
            if (next.spot.key != null) {
                skipSpots.add(next.spot.key);
            }
            target = null;
            return afterSkip(mining);
        }
        noMineableSinceMs = 0L;
        StarParkHandoff.noteMineablePresent();
        if (target == null || target.world != next.world || !sameSpot(target, next)) {
            StarInspect.reset();
            lastLoggedLiveTier = -1;
            clearStarSeen();
            String why;
            if (StarFeed.canMineNow(next, mining)) {
                why = " — hoogste minebare T" + next.tier;
                if (sceneStay != null && sceneStay.world != next.world
                        && !StarFeed.canMineNow(sceneStay, mining)
                        && sceneVisitOk(sceneStay, mining)) {
                    parkedWait = sceneStay;
                    why += " i.p.v. wachten " + sceneStay.label() + " (park)";
                }
            } else {
                why = " — wacht T" + (next.tier > 0 ? next.tier : "?")
                        + " (dichtst bij lvl, extra=+" + StarMinerPlugin.waitTiersAbove() + ")";
            }
            target = next;
            nearSinceMs = 0L;
            hopAttempts = 0;
            hopWorld = 0;
            walkFails = 0;
            lockedTravelDest = null;
            StarTravel.clear();
            cancelOwnedWorldWalker();
            log((sceneStay != null && next.world == sceneStay.world ? "al bij ster " : "doel ")
                    + next.label() + why + " (mining " + mining + ")"
                    + (sceneStay != null && sceneStay.world != next.world && sameSpot(sceneStay, next)
                    ? " — zelfde plek andere wereld, hop" : ""));
        } else if (next.tier > 0 && next.tier != target.tier) {
            log("live laag T" + target.tier + " → T" + next.tier + " (mining " + mining
                    + "/" + StarObjects.miningLevelForTier(next.tier) + ")");
            target = next;
        }

        int world = Worlds.getCurrentWorld();
        if (StarMinerPlugin.hopEnabled && world != target.world) {
            if (gemBank.shouldBank(false)) {
                gemBank.arm();
                int gd = tickGemBank();
                status = gemBank.status();
                return gd;
            }
            cancelTravelForHop();
            return hopTo(hopTargetWorld());
        }

        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null ? me.worldLocation : null;
        WorldPoint feedTile = target.tile();
        int dFeed = (pos != null && feedTile != null && pos.getPlane() == feedTile.getPlane())
                ? pos.distanceTo(feedTile) : 99;

        // Travel-driver (10 modes) — Imp/steden walkTo
        if (dFeed > 14) {
            int teleDelay = tickStarTeleportIfUseful();
            if (teleDelay >= 0) {
                return teleDelay;
            }
            // F2P-spell tele zit in tickStarTeleportIfUseful (Lumb/Fally/Varrock hub).
            if (StarCraftingGuild.isSpot(target.spot) && StarCraftingGuild.tryOpenDoor(pos)) {
                status = "Crafting Guild deur";
                return ThreadLocalRandom.current().nextInt(500, 800);
            }
            if (lockedTravelDest == null
                    || (feedTile != null && lockedTravelDest.getPlane() != feedTile.getPlane())) {
                lockedTravelDest = StarObjects.waitStand(feedTile, pos);
            }
            WorldPoint dest = lockedTravelDest != null ? lockedTravelDest : feedTile;
            if (dest != null) {
                BotRuntime.debugTarget = dest;
            }
            // Al Kharid-poort: geen eindeloze BFS — coins banken of ster skippen
            if (pos != null && dest != null
                    && net.storm.sdk.movement.pathfinder.AlKharidGate.crossesGateWithoutPass(pos, dest)) {
                StarTravel.clear();
                lockedTravelDest = null;
                net.storm.sdk.movement.pathfinder.AlKharidGate.logSkip();
                if (gear.needsTravelKit()) {
                    status = "poort: coins/tele → bank";
                    log("poort dicht → eerst bank (coins)");
                    return ThreadLocalRandom.current().nextInt(200, 360);
                }
                log("poort dicht → skip "
                        + (target.spot != null ? target.spot.shortName : "ster"));
                skipCurrent("poort");
                return afterSkip(mining);
            }
            if (holdMiningCancelWalk(me)) {
                status = "minen (walker uit)";
                return ThreadLocalRandom.current().nextInt(500, 900);
            }
            String lab = target.spot != null ? target.spot.shortName : "ster";
            StarTravel.arm(dest, lab);
            if (StarMinerPlugin.worldWalkerTravel() && pos != null && dest != null
                    && pos.getPlane() == dest.getPlane() && pos.distanceTo(dest) > WW_HANDOFF) {
                int ww = startWorldWalkerTo(dest);
                if (ww >= 0) {
                    return ww;
                }
            }
            ITileObject starNear = null;
            if (dFeed <= 30) {
                starNear = StarObjects.nearest(feedTile);
            }
            StarWalk.Result wr = StarTravel.tick(pos, starNear, null);
            if (wr == null) {
                // Mode 3 bg / 6 yield — korte poll, andere logica mag later
                status = lab + " travel/" + StarTravel.modeTag();
                return ThreadLocalRandom.current().nextInt(280, 450);
            }
            status = wr.status;
            noteCosMineFromTravel(wr);
            if (wr.arrived) {
                lockedTravelDest = null;
                try {
                    net.storm.sdk.movement.MovementHelper.clearPath();
                } catch (Throwable ignored) {
                }
            }
            return afterWalk(wr, mining);
        }
        lockedTravelDest = null;
        StarTravel.clear();
        // Bij de ster: pad weg zodat AntiBan camera/muis mag (anders "NEAR"/lopen)
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }

        StarInspect.readChat();
        WorldPoint hint = lastStarTile != null ? lastStarTile
                : (target != null ? target.tile() : pos);
        ITileObject star = StarObjects.nearest(hint);
        INPC starNpc = StarInspect.nearestNpc(hint != null ? hint : pos);
        WorldPoint starTile = StarInspect.tile(star, starNpc);
        if (starTile == null) {
            starTile = target.tile();
        }
        WorldPoint dest = StarObjects.waitStand(starTile, pos);
        if (dest != null) {
            BotRuntime.debugTarget = dest;
        }

        int dStar = (pos != null && starTile != null && pos.getPlane() == starTile.getPlane())
                ? pos.distanceTo(starTile) : 99;
        long nowNear = System.currentTimeMillis();
        boolean present = StarInspect.present(star, starNpc);
        if (present) {
            noteStarSeen(starTile);
        }
        boolean stayHere = stayAtStarSite(me, pos);
        boolean keepMining = isKeepMining();
        boolean layerDrop = StarInspect.watch(star, starNpc);
        int liveTier = StarObjects.liveTier(star);
        StarInspect.syncLower(liveTier);
        int need = liveTier > 0
                ? StarObjects.miningLevelForTier(liveTier)
                : StarInspect.prospectNeed();
        if (!present && need <= 0 && target != null) {
            need = target.miningLevelRequired();
            if (liveTier <= 0) {
                liveTier = target.tier;
            }
        }
        if (present && liveTier > 0 && lastLoggedLiveTier != liveTier) {
            log("live T" + liveTier
                    + (star != null ? " obj=" + star.getId() : "")
                    + " mining " + mining + "/" + StarObjects.miningLevelForTier(liveTier)
                    + (mining >= StarObjects.miningLevelForTier(liveTier) ? " → Mine" : " — wacht"));
            lastLoggedLiveTier = liveTier;
        }
        boolean unknownLive = present && StarObjects.liveTier(star) <= 0
                && StarInspect.prospectNeed() <= 0;
        boolean tooHigh = !unknownLive && need > 0 && mining < need;
        int starTier = liveTier > 0 ? liveTier : 0;
        boolean aboveWaitCap = starTier > 0 && !StarMinerPlugin.wouldVisitTier(starTier, mining);

        if (tooHigh && aboveWaitCap) {
            log((starTier > 0 ? "T" + starTier : "T?") + " te hoog (mining " + mining
                    + " < " + need + ", max T" + StarMinerPlugin.maxVisitTier(mining)
                    + ") — volgende");
            skipCurrent("te hoog T" + starTier);
            return afterSkip(mining);
        }
        if (tooHigh || (unknownLive && !keepMining)) {
            return waitAtStar(pos, dest, star, starNpc, dStar, liveTier, need, mining, nowNear,
                    unknownLive || layerDrop);
        }

        if (StarPickaxes.tryWield()) {
            status = "wield pickaxe";
            return ThreadLocalRandom.current().nextInt(400, 700);
        }

        if (!present && stayHere) {
            if (!tryDeclareStarGone("weg", me)) {
                status = keepMining ? "laag wisselt — blijf minen" : "laag wisselt — blijf bij ster";
                log("object/id even weg (tier-drop) — geen hop, wacht nieuwe laag");
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            return afterSkip(mining);
        }

        if (!present && dStar >= 0 && dStar <= EMPTY_SITE_TILES) {
            if (needsWorldHop()) {
                cancelTravelForHop();
                return hopTo(hopTargetWorld());
            }
            StarTravel.clear();
            long emptyWait = sawStarThisVisit ? STAR_GONE_WAIT_MS : STAR_GONE_ARRIVED_MS;
            if (starStillHere(me)) {
                status = "ster even weg — blijf";
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            if (nearSinceMs <= 0L) {
                nearSinceMs = nowNear;
                log("geen ster op de plek d=" + dStar + " — stop lopen, volgende over "
                        + (emptyWait / 1000L) + "s");
            } else if (nowNear - nearSinceMs > emptyWait) {
                if (tryDeclareStarGone("geen ster", me)) {
                    return afterSkip(mining);
                }
                status = "ster even weg — blijf";
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            status = "geen ster d=" + dStar + " — skip over "
                    + Math.max(0L, (emptyWait - (nowNear - nearSinceMs)) / 1000L) + "s";
            return ThreadLocalRandom.current().nextInt(250, 450);
        }
        // COS tijdens aanlopen — niet eerst stil staan op de stand-tegel
        if (present && !tooHigh && need > 0 && mining >= need && lastMineMs == 0L
                && !isMiningSwing(me, starNpc)) {
            int mineNow = tryMineOnSight(star, starNpc, dest, nowNear);
            if (mineNow > 0) {
                return mineNow;
            }
        }
        if (holdMiningCancelWalk(me) && (dStar > 8 || lastMineMs > 0L)) {
            status = "minen (walker uit)";
            return ThreadLocalRandom.current().nextInt(500, 900);
        }
        if ((dStar > 8 && present) || (!present && (dStar < 0 || dStar > EMPTY_SITE_TILES))) {
            if (StarCraftingGuild.isSpot(target != null ? target.spot : null)
                    && StarCraftingGuild.tryOpenDoor(pos)) {
                status = "Crafting Guild deur";
                return ThreadLocalRandom.current().nextInt(500, 800);
            }
            String lab = target.spot != null ? target.spot.shortName : "ster";
            StarTravel.arm(dest, lab);
            StarWalk.Result wr = StarTravel.tick(pos, star, starNpc);
            if (wr == null) {
                status = lab + " travel/" + StarTravel.modeTag();
                return ThreadLocalRandom.current().nextInt(280, 450);
            }
            status = wr.status;
            noteCosMineFromTravel(wr);
            if (present) {
                nearSinceMs = 0L;
            }
            return afterWalk(wr, mining);
        }

        nearSinceMs = 0L;
        if (need <= 0 && !keepMining) {
            return waitAtStar(pos, dest, star, starNpc, dStar, liveTier, need, mining, nowNear, true);
        }
        long now = System.currentTimeMillis();
        if (layerDrop) {
            lastMineMs = 0L;
            lastMiningAnimMs = 0L;
        }
        boolean alreadyMining = lastMineMs > 0L || lastMiningAnimMs > 0L;
        int hpNow = StarInspect.npcHealthPct(starNpc);
        String hpBit = hpNow >= 0 ? " hp " + hpNow + "%" : "";
        holdMiningCancelWalk(me);
        boolean afkDue = alreadyMining && afkReclickDue(now);
        if (isMiningSwing(me, starNpc) && !afkDue) {
            lastMiningAnimMs = now;
            status = "minen " + target.label() + hpBit + afkReclickEta(now);
            return ThreadLocalRandom.current().nextInt(500, 900);
        }
        if (alreadyMining && !afkDue) {
            if (isMiningSwing(me, starNpc)) {
                lastMiningAnimMs = now;
            }
            status = "minen " + target.label() + hpBit + afkReclickEta(now);
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        if (alreadyMining && afkDue) {
            log("AFK-herklik (2–3 min) " + target.label());
        }
        boolean ok = false;
        ClickOnSight.Result sight = trySightMine(star, starNpc, dest);
        if (sight != null && sight.clicked) {
            ok = true;
        } else {
            ok = StarInspect.clickMine(star, starNpc);
        }
        lastMineMs = now;
        if (ok) {
            lastMiningAnimMs = now;
            committedToStar = true;
            StarTravel.cancelWalker("Mine");
            AntiBan.get().markBotActivity();
        }
        scheduleAfkReclick(now);
        status = (ok ? "Mine " : "Mine-miss ") + target.label();
        log(status);
        return ThreadLocalRandom.current().nextInt(450, 800);
    }

    /** Mine zodra ster op scherm of in invoke-range — ook tijdens lopen. 0 = nog lopen. */
    private int tryMineOnSight(ITileObject star, INPC npc, WorldPoint dest, long now) {
        WorldPoint tile = StarInspect.tile(star, npc);
        if (tile == null) {
            tile = dest;
        }
        boolean onScreen = ClickOnSight.onScreen(star) || (npc != null && ClickOnSight.onScreen(npc));
        boolean inRange = ClickOnSight.inInvokeRange(tile);
        if (!onScreen && !inRange) {
            return 0;
        }
        boolean clicked = false;
        if (star != null) {
            ClickOnSight.Result r = ClickOnSight.interact(star, "Mine");
            clicked = r != null && r.clicked;
        }
        if (!clicked && npc != null) {
            ClickOnSight.Result r = ClickOnSight.interact(npc, "Mine");
            clicked = r != null && r.clicked;
        }
        if (!clicked) {
            net.runelite.api.TileObject raw = star != null ? TileObjects.unwrap(star) : null;
            if (raw != null && MenuInteract.interactObject(raw, "Mine")) {
                clicked = true;
            }
        }
        if (!clicked && npc != null && MenuInteract.interactNpcByIndex(npc.getIndex(), "Mine")) {
            clicked = true;
        }
        if (clicked) {
            markCosMineClicked(now);
            AntiBan.get().markBotActivity();
            status = "Mine " + (target != null ? target.label() : "ster");
            log(status + " (COS tijdens lopen)");
            return ThreadLocalRandom.current().nextInt(450, 800);
        }
        return 0;
    }

    private static ClickOnSight.Result trySightMine(ITileObject star, INPC npc, WorldPoint dest) {
        if (star != null) {
            ClickOnSight.Result r = ClickOnSight.interactOrApproach(star, dest, "Mine");
            if (r != null && r.outcome != ClickOnSight.Outcome.NONE) {
                return r;
            }
        }
        if (npc != null) {
            return ClickOnSight.interactOrApproach(npc, dest, "Mine");
        }
        return null;
    }

    /**
     * Al bij een crashed star: eerst minen als het kan, anders wacht-extra.
     * Boven de cap → niet deze wereld, pickNext zoekt verder.
     */
    private StarCall preferSceneStar(int mining, WorldPoint pos) {
        if (pos == null) {
            return null;
        }
        StarInspect.readChat();
        WorldPoint hint = lastStarTile != null ? lastStarTile
                : (target != null ? target.tile() : pos);
        ITileObject star = StarObjects.nearest(hint);
        INPC npc = StarInspect.nearestNpc(hint != null ? hint : pos);
        if (!StarInspect.present(star, npc) && stayAtStarSite(Players.snapshotLocal(), pos)) {
            if (target != null && target.world == Worlds.getCurrentWorld()) {
                return target;
            }
        }
        if (!StarInspect.present(star, npc)) {
            return null;
        }
        WorldPoint tile = StarInspect.tile(star, npc);
        if (tile == null || pos.getPlane() != tile.getPlane() || pos.distanceTo(tile) > SCENE_STAR_TILES) {
            return null;
        }
        StarLocations.Spot sceneSpot = StarLocations.nearestSpot(tile);
        if (sceneSpot != null && !StarSpotGate.accessible(sceneSpot)) {
            StarSpotGate.logSkipOnce(sceneSpot);
            skipWorlds.add(Worlds.getCurrentWorld());
            if (sceneSpot.key != null) {
                skipSpots.add(sceneSpot.key);
            }
            return null;
        }
        int liveTier = StarObjects.liveTier(star);
        if (liveTier > 0 && liveTier < StarMinerPlugin.minTier()) {
            skipWorlds.add(Worlds.getCurrentWorld());
            log("scene T" + liveTier + " < minT" + StarMinerPlugin.minTier() + " — andere ster");
            return null;
        }
        boolean unknown = liveTier <= 0;
        boolean canMine = !unknown && mining >= StarObjects.miningLevelForTier(liveTier);
        boolean waitHere = unknown || (!canMine && StarMinerPlugin.wouldVisitTier(liveTier, mining));
        int world = Worlds.getCurrentWorld();
        if (!canMine && !waitHere) {
            skipWorlds.add(world);
            log("scene T" + liveTier + " mining " + mining + "/"
                    + StarObjects.miningLevelForTier(liveTier)
                    + " max T" + StarMinerPlugin.maxVisitTier(mining)
                    + " — andere ster");
            return null;
        }
        if (target != null && target.world == world && target.spot != null) {
            return liveTier > 0 ? target.withTier(liveTier) : target;
        }
        StarCall fromFeed = StarFeed.get().onWorld(world);
        StarLocations.Spot spot = null;
        if (fromFeed != null && fromFeed.spot != null && fromFeed.spot.tile != null
                && fromFeed.spot.tile.getPlane() == tile.getPlane()
                && fromFeed.spot.tile.distanceTo(tile) <= 40) {
            spot = fromFeed.spot;
        }
        if (spot == null) {
            spot = StarLocations.nearestSpot(tile);
        }
        int tier = liveTier > 0 ? liveTier : (fromFeed != null ? fromFeed.tier : 0);
        return new StarCall(world, tier, "scene", spot,
                fromFeed != null ? fromFeed.miners : 0,
                fromFeed != null ? fromFeed.calledAtMs : System.currentTimeMillis(),
                false, "scene", "");
    }

    /** Te hoge / onbekende ster: Prospect, geen Mine, niet op het object lopen. */
    private int waitAtStar(WorldPoint pos, WorldPoint dest, ITileObject star, INPC npc, int dStar,
                           int liveTier, int need, int mining, long nowNear, boolean unknown) {
        boolean present = StarInspect.present(star, npc);
        Players.LocalSnap meWait = Players.snapshotLocal();
        if (!present && dStar >= 0 && dStar <= EMPTY_SITE_TILES) {
            if (needsWorldHop()) {
                cancelTravelForHop();
                return hopTo(hopTargetWorld());
            }
            StarTravel.clear();
            if (stayAtStarSite(meWait, pos)) {
                if (tryDeclareStarGone("weg", meWait)) {
                    return afterSkip(mining);
                }
                status = "laag wisselt — blijf bij ster";
                log("wacht: object even weg — geen hop");
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            if (starStillHere(meWait)) {
                status = "ster even weg — blijf";
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            long emptyWait = sawStarThisVisit ? STAR_GONE_WAIT_MS : STAR_GONE_ARRIVED_MS;
            if (nearSinceMs <= 0L) {
                nearSinceMs = nowNear;
            } else if (nowNear - nearSinceMs > emptyWait) {
                if (tryDeclareStarGone("geen ster", meWait)) {
                    return afterSkip(mining);
                }
                status = "ster even weg — blijf";
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            status = "geen ster d=" + dStar + " — skip over "
                    + Math.max(0L, (emptyWait - (nowNear - nearSinceMs)) / 1000L) + "s";
            return ThreadLocalRandom.current().nextInt(250, 450);
        }
        int stay = StarMinerPlugin.waitActivity() == StarMinerPlugin.WaitActivity.NONE ? 10 : 16;
        if (dStar > stay || !present) {
            if (!present && stayAtStarSite(meWait, pos)) {
                if (tryDeclareStarGone("weg", meWait)) {
                    return afterSkip(mining);
                }
                status = "laag wisselt — blijf bij ster";
                log("wacht: object even weg — geen hop");
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            String label = "wacht-stand " + (target.spot != null ? target.spot.shortName : "");
            StarWalk.Result wr = StarWalk.toward(pos, dest, 4, label);
            status = wr.status;
            log((unknown ? "laag onbekend" : ("te hoog T" + (liveTier > 0 ? liveTier : "?")))
                    + " mining " + mining + "/" + (need > 0 ? need : "?")
                    + " — geen Mine, loop naar stand");
            return afterWalk(wr, mining);
        }
        nearSinceMs = 0L;
        boolean unknownLive = liveTier <= 0 && StarInspect.prospectNeed() <= 0;
        boolean tooHigh = need > 0 && mining < need;
        // Alleen Prospect als laag écht onbekend is. Bij te hoog: géén klikken op de ster.
        if (unknownLive && !tooHigh) {
            int prospect = StarInspect.maybeProspect(star, npc, true, true);
            if (prospect > 0) {
                int hp = StarInspect.npcHealthPct(npc);
                status = "Prospect (laag onbekend)";
                log(status + " obj=" + (star != null ? star.getId() : 0)
                        + (hp >= 0 ? " hp " + hp + "%" : ""));
                return prospect;
            }
        }
        if (tooHigh) {
            log("te hoog T" + (liveTier > 0 ? liveTier : "?")
                    + " mining " + mining + "/" + need + " — geen Prospect/Mine, wacht");
        }
        int delay = waitSide.tick(pos, star, liveTier, need, mining);
        status = waitSide.status + (star != null ? " id=" + star.getId() : "");
        return delay;
    }

    /**
     * Keuze binnen Star-instellingen (min-tier, extra wachtlagen, F2P, wildy).
     * Tijdens minen: blijf bij deze ster tot hij écht weg is.
     * Nieuwe ster: hoogste minebare laag; anders dichtst-bij-lvl wachtster.
     */
    private StarCall chooseTarget(int mining, StarCall sceneStay, Players.LocalSnap me) {
        if (stickyStayOnStar(me, sceneStay, mining)) {
            log("blijf " + target.label() + " (minen — geen andere feed-ster)");
            if (sceneStay != null && sameWorldSpot(sceneStay, target)) {
                return sceneStay;
            }
            return target;
        }

        boolean sceneMineOk = sceneStay != null
                && StarFeed.canMineNow(sceneStay, mining)
                && sceneVisitOk(sceneStay, mining);
        boolean sceneWaitOk = sceneStay != null
                && !StarFeed.canMineNow(sceneStay, mining)
                && sceneVisitOk(sceneStay, mining);

        StarCall mineable = pickNext(mining, true, 0);
        if (sceneMineOk) {
            if (parkedWait != null && sameSpot(sceneStay, parkedWait)) {
                parkedWait = null;
            }
            if (mineable == null || sameWorldSpot(sceneStay, mineable)
                    || sceneStay.tier >= mineable.tier) {
                return sceneStay;
            }
            parkedWait = null;
            return mineable;
        }
        if (mineable != null) {
            if (sceneWaitOk && parkedWait == null && !StarParkHandoff.parkSkillEnabled()) {
                parkedWait = sceneStay;
            }
            return mineable;
        }

        if (StarParkHandoff.parkSkillEnabled()) {
            parkedWait = null;
            return null;
        }

        if (StarMinerPlugin.waitTiersAbove() <= 0) {
            parkedWait = null;
            return null;
        }

        StarCall waitBest = pickNext(mining, false, 0);
        if (waitBest != null) {
            if (sceneWaitOk && sameWorldSpot(sceneStay, waitBest)) {
                return sceneStay;
            }
            return waitBest;
        }
        if (sceneWaitOk) {
            return sceneStay;
        }
        return reviveParkedWait(mining);
    }

    /** Zelfde wereld+spot als huidige target. */
    private boolean sameWorldSpot(StarCall a, StarCall b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.world != b.world) {
            return false;
        }
        return sameSpot(a, b);
    }

    /**
     * We minen deze ster: geen hogere feed-ster tot object/hp/animatie weg zijn.
     */
    private boolean stickyStayOnStar(Players.LocalSnap me, StarCall sceneStay, int mining) {
        if (!committedToStar || target == null) {
            return false;
        }
        try {
            if (Worlds.getCurrentWorld() != target.world) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        if (sceneStay != null && sameWorldSpot(sceneStay, target)) {
            int t = sceneStay.tier;
            if (t > 0 && mining < StarObjects.miningLevelForTier(t)
                    && !StarMinerPlugin.wouldVisitTier(t, mining)) {
                return false;
            }
            return true;
        }
        if (starStillHere(me)) {
            return true;
        }
        return false;
    }

    /** Scene-ster moet door min-tier + extra-wachtlagen. */
    private static boolean sceneVisitOk(StarCall c, int mining) {
        if (c == null) {
            return false;
        }
        int t = c.tier;
        if (t <= 0) {
            return true;
        }
        return StarMinerPlugin.wouldVisitTier(t, mining);
    }

    /** Na minebare klaar: terug naar wacht-ster als die nog in feed / visitbaar is. */
    private StarCall reviveParkedWait(int mining) {
        if (parkedWait == null) {
            return null;
        }
        if (skipWorlds.contains(parkedWait.world)) {
            log("park-wacht W" + parkedWait.world + " in skip — loslaten");
            parkedWait = null;
            return null;
        }
        if (parkedWait.spot != null && skipSpots.contains(parkedWait.spot.key)) {
            parkedWait = null;
            return null;
        }
        StarCall live = StarFeed.get().onWorld(parkedWait.world);
        if (live == null) {
            log("park-wacht " + parkedWait.label() + " weg uit feed — loslaten");
            parkedWait = null;
            return null;
        }
        if (StarFeed.canMineNow(live, mining)) {
            log("park-wacht nu minebaar: " + live.label());
            parkedWait = null;
            return live;
        }
        if (!StarMinerPlugin.wouldVisitTier(live.tier > 0 ? live.tier : parkedWait.tier, mining)
                && live.tier > 0) {
            log("park-wacht " + live.label() + " te hoog — loslaten");
            parkedWait = null;
            return null;
        }
        log("terug naar park-wacht " + live.label());
        return live;
    }

    /**
     * Zelfde keuze als {@link #chooseTarget} (skip-werelden/spots).
     * Park-resume mag alleen als dit niet {@code null} is.
     */
    StarCall peekTravelableMineable(int mining) {
        return pickNext(mining, true, 0);
    }

    private StarCall pickNext(int mining) {
        return pickNext(mining, false, 0);
    }

    private StarCall pickNext(int mining, boolean mineableOnly, int alsoExcludeWorld) {
        Set<Integer> extra = new HashSet<>(skipWorlds);
        if (alsoExcludeWorld > 0) {
            extra.add(alsoExcludeWorld);
        }
        for (int i = 0; i < 40; i++) {
            StarCall n = mineableOnly
                    ? StarFeed.get().bestMineableNow(mining,
                    StarMinerPlugin.avoidWilderness, StarMinerPlugin.f2pOnly, extra)
                    : StarFeed.get().best(mining,
                    StarMinerPlugin.avoidWilderness, StarMinerPlugin.f2pOnly, extra);
            if (n == null) {
                return null;
            }
            if (n.spot != null && skipSpots.contains(n.spot.key)) {
                extra.add(n.world);
                continue;
            }
            return n;
        }
        return null;
    }

    private static boolean sameSpot(StarCall a, StarCall b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.spot == b.spot) {
            return true;
        }
        if (a.spot == null || b.spot == null || a.spot.key == null) {
            return false;
        }
        return a.spot.key.equals(b.spot.key);
    }

    private int afterSkip(int mining) {
        StarCall back = reviveParkedWait(mining);
        if (back != null) {
            target = back;
            return ThreadLocalRandom.current().nextInt(120, 220);
        }
        StarCall other = pickNext(mining);
        if (other == null) {
            return noMineableThenLogout(mining);
        }
        // Direct volgende tick: nieuw doel + hop (geen 0.5s-pauze)
        return ThreadLocalRandom.current().nextInt(120, 220);
    }

    private void skipCurrent(String why) {
        if (target != null) {
            skipWorlds.add(target.world);
            boolean emptyLoc = "geen ster".equals(why) || "weg".equals(why);
            boolean noPath = "geen pad".equals(why);
            if (emptyLoc) {
                StarFeed.get().markDead(target.world);
            }
            // Locatie alleen skippen als pad onmogelijk is — "geen ster" is díe wereld,
            // niet Varrock East op alle werelden (T9 elders bleef dan liggen).
            if (noPath && target.spot != null) {
                skipSpots.add(target.spot.key);
                log("skip W" + target.world + " + locatie " + target.spot.shortName + " (" + why + ")");
            } else if (emptyLoc && target.spot != null) {
                log("skip W" + target.world + " " + target.spot.shortName + " (" + why + ") — locatie blijft");
            } else {
                log("skip W" + target.world + " (" + why + ")");
            }
        }
        target = null;
        lockedTravelDest = null;
        StarTravel.clear();
        cancelOwnedWorldWalker();
        nearSinceMs = 0L;
        lastMineMs = 0L;
        lastMiningAnimMs = 0L;
        nextAfkReclickMs = 0L;
        committedToStar = false;
        waitSide.reset();
        StarInspect.reset();
        lastLoggedLiveTier = -1;
        clearStarSeen();
        walkFails = 0;
    }

    private int afterWalk(StarWalk.Result wr, int mining) {
        if (wr == null) {
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        if (!wr.failed) {
            walkFails = 0;
            return wr.delayMs;
        }
        walkFails++;
        if (walkFails >= WALK_FAIL_SKIP) {
            log("geen pad naar "
                    + (target != null && target.spot != null ? target.spot.shortName : "ster")
                    + " (" + walkFails + "× walk-miss) — volgende");
            skipCurrent("geen pad");
            return afterSkip(mining);
        }
        return wr.delayMs;
    }

    private void noteStarSeen(WorldPoint tile) {
        if (tile == null) {
            return;
        }
        boolean firstSeeThisVisit = !sawStarThisVisit;
        lastStarTile = tile;
        lastGemAvoidTile = tile;
        lastStarSeenMs = System.currentTimeMillis();
        sawStarThisVisit = true;
        // Succes = ster staat er bij aankomst/zicht (1× per bezoek), niet pas na opminen
        if (firstSeeThisVisit) {
            BotRuntime.starSuccessCount++;
            log("ster gevonden #" + BotRuntime.starSuccessCount
                    + " W" + (target != null ? target.world : 0));
        }
    }

    private void clearStarSeen() {
        lastStarTile = null;
        lastStarSeenMs = 0L;
        sawStarThisVisit = false;
    }

    private boolean atTargetSite(WorldPoint pos) {
        WorldPoint t = lastStarTile != null ? lastStarTile
                : (target != null ? target.tile() : null);
        if (pos == null || t == null || pos.getPlane() != t.getPlane()) {
            return false;
        }
        return pos.distanceTo(t) <= EMPTY_SITE_TILES;
    }

    /** Op de crash-plek, geen ster in scene — niet blijven naloop-klikken. */
    private boolean nearEmptyStarSite(WorldPoint pos, StarCall sceneStay) {
        if (sceneStay != null || pos == null) {
            return false;
        }
        WorldPoint t = lockedTravelDest != null ? lockedTravelDest
                : (target != null ? target.tile() : StarTravel.destination());
        if (t == null || pos.getPlane() != t.getPlane()) {
            return false;
        }
        return pos.distanceTo(t) <= EMPTY_SITE_TILES;
    }

    private WorldPoint gemAvoidTile() {
        if (target != null && target.tile() != null) {
            return target.tile();
        }
        if (lockedTravelDest != null) {
            return lockedTravelDest;
        }
        return StarTravel.destination();
    }

    private int tickGemBank() {
        return gemBank.tick(lastGemAvoidTile, gemAvoidTile());
    }

    /** Gems storten op deze wereld — niet hoppen (LoopHost wist anders het bank-pad). */
    private boolean deferHopForGemBank() {
        if (gemBank.isActive()) {
            return true;
        }
        if (isKeepMining()) {
            return false;
        }
        return gemBank.shouldBank(false);
    }

    private void holdStarHopForGems() {
        BotRuntime.starHopWorld = 0;
        hopStuckSinceMs = 0L;
        int want = hopTargetWorld();
        int cur = 0;
        try {
            cur = Worlds.getCurrentWorld();
        } catch (Throwable ignored) {
        }
        if (want > 0 && cur > 0 && cur != want) {
            log("gems eerst — hop W" + want + " daarna (nu W" + cur + ")");
        }
    }

    private boolean stayAtStarSite(Players.LocalSnap me, WorldPoint pos) {
        if (target == null) {
            return false;
        }
        try {
            if (Worlds.getCurrentWorld() != target.world) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        if (!atTargetSite(pos)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastMineMs > 0L && now - lastMineMs < MINE_SESSION_MS) {
            return true;
        }
        if (lastMiningAnimMs > 0L && now - lastMiningAnimMs < MINE_IDLE_MS) {
            return true;
        }
        return sawStarThisVisit && lastStarSeenMs > 0L && now - lastStarSeenMs < STAR_LAYER_SWAP_MS;
    }

    /**
     * Echte mine-swing: pickaxe-animatie (ook tijdens 1 tegel lopen), of interact met de ster.
     */
    private static boolean isMiningSwing(Players.LocalSnap me, INPC npc) {
        if (me == null) {
            return false;
        }
        if (isMiningAnimation(me.animation)) {
            return true;
        }
        if (me.animating && !me.moving) {
            return true;
        }
        if (!me.interacting) {
            return false;
        }
        try {
            if (npc != null && me.interactingNpcIndex == npc.getIndex()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String n = me.interactingName;
        if (n == null || n.isEmpty()) {
            return false;
        }
        String low = n.toLowerCase();
        return low.contains("star") || low.contains("crashed");
    }

    /** 6746 = o.a. crystal/3a pick; 625–629 bronze–rune. */
    private static boolean isMiningAnimation(int anim) {
        if (anim < 0) {
            return false;
        }
        if (anim >= 625 && anim <= 629) {
            return true;
        }
        switch (anim) {
            case 6746:
            case 6752:
            case 6758:
            case 3873:
            case 4482:
            case 8347:
            case 7201:
            case 831:
                return true;
            default:
                return false;
        }
    }

    private boolean starStillHere(Players.LocalSnap me) {
        if (isMiningSwing(me, null)) {
            return true;
        }
        if (StarInspect.healthRecentlyAlive(STAR_ALIVE_MS)) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (lastMiningAnimMs > 0L && now - lastMiningAnimMs < STAR_ALIVE_MS) {
            return true;
        }
        return lastStarSeenMs > 0L && now - lastStarSeenMs < STAR_GONE_WAIT_MS;
    }

    /** @return true als we overgeslagen hebben */
    private boolean tryDeclareStarGone(String why, Players.LocalSnap me) {
        if (starStillHere(me)) {
            log("geen skip (" + why + ") — ster/hp/animatie nog");
            return false;
        }
        log("ster weg op W" + (target != null ? target.world : 0) + " (opgemined?) — volgende");
        skipCurrent(why);
        return true;
    }

    private void noteCosMineFromTravel(StarWalk.Result wr) {
        if (wr != null && wr.status != null && wr.status.contains("COS Mine")) {
            markCosMineClicked(System.currentTimeMillis());
        }
    }

    private void markCosMineClicked(long now) {
        lastMineMs = now;
        nearSinceMs = 0L;
        lockedTravelDest = null;
        committedToStar = true;
        StarTravel.cancelWalker("COS Mine");
        scheduleAfkReclick(now);
    }

    private void scheduleAfkReclick(long now) {
        long wait = ThreadLocalRandom.current().nextLong(AFK_RECLICK_MIN_MS, AFK_RECLICK_MAX_MS + 1L);
        nextAfkReclickMs = now + wait;
        log("AFK-herklik over " + (wait / 1000L) + "s");
    }

    private boolean afkReclickDue(long now) {
        if (nextAfkReclickMs <= 0L) {
            return false;
        }
        return now >= nextAfkReclickMs;
    }

    private String afkReclickEta(long now) {
        if (nextAfkReclickMs <= 0L) {
            return "";
        }
        long left = Math.max(0L, nextAfkReclickMs - now);
        return " · AFK-klik " + (left / 1000L) + "s";
    }

    /** COS-klik zonder swing: walker mag weer naar dezelfde ster. */
    private void noteMineClickTimeout() {
        long now = System.currentTimeMillis();
        if (lastMineMs <= 0L || now - lastMineMs < MINE_CLICK_GRACE_MS) {
            return;
        }
        if (lastMiningAnimMs >= lastMineMs) {
            return;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (isMiningSwing(me, null) || StarInspect.healthRecentlyAlive(STAR_ALIVE_MS)
                || (lastMiningAnimMs > 0L && now - lastMiningAnimMs < MINE_IDLE_MS)) {
            return;
        }
        log("Mine-klik geen animatie — zelfde ster lopen");
        lastMineMs = 0L;
    }

    /**
     * Aan het minen of net COS-Mine: walker uit, niet opnieuw naar stand-tegel B.
     * Loop-animatie telt niet als mine-swing.
     */
    private boolean holdMiningCancelWalk(Players.LocalSnap me) {
        long now = System.currentTimeMillis();
        boolean miningAnim = isMiningSwing(me, null);
        if (miningAnim) {
            lastMiningAnimMs = now;
        }
        boolean hold = miningAnim
                || (lastMiningAnimMs > 0L && now - lastMiningAnimMs < MINE_IDLE_MS)
                || (lastMineMs > 0L && now - lastMineMs < MINE_CLICK_GRACE_MS);
        if (!hold) {
            return false;
        }
        lockedTravelDest = null;
        StarTravel.cancelWalker("minen");
        return true;
    }

    /** Recente Mine-klik of mine-swing — High Alch-animatie telt niet als minen. */
    private boolean isKeepMining() {
        if (committedToStar) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (lastMiningAnimMs > 0L && now - lastMiningAnimMs < MINE_IDLE_MS) {
            return true;
        }
        return lastMineMs > 0L && now - lastMineMs < MINE_SESSION_MS;
    }

    private int noMineableThenLogout(int mining) {
        if (StarParkHandoff.tryPark(this, "geen minebare T (lvl " + mining + ")")) {
            status = "park " + StarParkHandoff.parkWaitSkill();
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        long now = System.currentTimeMillis();
        boolean feedEmpty = StarFeed.get().active().isEmpty();
        if (noMineableSinceMs <= 0L) {
            noMineableSinceMs = now;
            log("geen minebare ster lvl=" + mining + " · "
                    + StarFeed.filterDebug(mining, StarMinerPlugin.avoidWilderness, StarMinerPlugin.f2pOnly)
                    + " skipW=" + skipWorlds.size()
                    + (feedEmpty ? " · feed leeg" : " — wachten op feed, geen logout"));
        }
        if (!feedEmpty) {
            status = "geen minebare ster (lvl " + mining + ") — wacht op nieuwe/lagere";
            log(status);
            return 2500;
        }
        long wait = EMPTY_FEED_LOGOUT_MS;
        long left = Math.max(0L, wait - (now - noMineableSinceMs));
        status = "geen ster in feed — logout over " + (left / 1000) + "s";
        if (now - noMineableSinceMs < wait) {
            log(status);
            return 1200;
        }
        return logoutNow("geen ster in feed");
    }

    private int logoutNow(String why) {
        logoutIssued = true;
        logoutTries = 0;
        status = "logout: " + why;
        log(status);
        try {
            boolean clicked = Game.logout();
            log("logout klik=" + clicked);
        } catch (Throwable t) {
            log("logout fout: " + t.getClass().getSimpleName());
        }
        return ThreadLocalRandom.current().nextInt(500, 800);
    }

    /**
     * Lumb/Fally/Varrock-spell vóór WorldWalker. Failsafe: zelfde ster, geen andere skill.
     *
     * @return delay, of {@code -1} als er niet geteleport hoeft te worden
     */
    private int tickStarTeleportIfUseful() {
        if (!StarMinerPlugin.teleportsEnabled || target == null) {
            return -1;
        }
        if (StarMinerPlugin.hopEnabled) {
            try {
                if (Worlds.getCurrentWorld() != target.world) {
                    return -1;
                }
            } catch (Throwable ignored) {
                return -1;
            }
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (StarTeleports.waitingCast()) {
            int td = StarTeleports.tickCast(pos, StarTeleports.Choice.none());
            status = "tele…";
            return td > 0 ? td : ThreadLocalRandom.current().nextInt(280, 450);
        }
        WorldPoint starTile = target.tile();
        StarTeleports.Choice choice = StarTeleports.choose(pos, target.spot, starTile);
        if (!choice.use()) {
            return -1;
        }
        cancelOwnedWorldWalker();
        int td = StarTeleports.tickCast(pos, choice);
        if (td <= 0) {
            return -1;
        }
        if (choice.kind == StarTeleports.Kind.HOME) {
            status = "Home Teleport → Lumb";
        } else if (choice.hub != null) {
            status = choice.hub.spell.getName();
        } else {
            status = "tele";
        }
        return td;
    }

    /**
     * Toggle aan + ver van dest of verkeerde wereld: geen Star-walkTo (hop/WW eerst).
     */
    private boolean skipStarWalkForWorldWalker() {
        if (!StarMinerPlugin.worldWalkerTravel() || !StarTravel.isActive()) {
            return false;
        }
        if (target != null && StarMinerPlugin.hopEnabled) {
            try {
                if (Worlds.getCurrentWorld() != target.world) {
                    return true;
                }
            } catch (Throwable ignored) {
                return true;
            }
        }
        WorldPoint dest = StarTravel.destination();
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (dest == null || pos == null || pos.getPlane() != dest.getPlane()) {
            return false;
        }
        return pos.distanceTo(dest) > WW_HANDOFF;
    }

    private void cancelOwnedWorldWalker() {
        try {
            net.storm.sdk.movement.WorldWalker.cancelScriptTravel();
        } catch (Throwable ignored) {
        }
    }

    private int startWorldWalkerTo(WorldPoint dest) {
        if (dest == null) {
            return -1;
        }
        try {
            net.storm.sdk.movement.WorldWalker.go(dest, WW_HANDOFF);
        } catch (Throwable t) {
            return -1;
        }
        status = "WorldWalker → " + dest.getX() + "," + dest.getY();
        long now = System.currentTimeMillis();
        if (now - lastWwLogMs > 2000L) {
            lastWwLogMs = now;
            log("WorldWalker dest " + dest.getX() + "," + dest.getY() + "," + dest.getPlane()
                    + " handoff≤" + WW_HANDOFF);
        }
        return 0;
    }

    /**
     * Hop/tele eerst. Daarna WorldWalker tot ≤30; Star blijft gepauzeerd tot handoff in WW.tick.
     *
     * @return delay, of {@code -1} als de normale tick verder moet
     */
    private int tickWorldWalkerTravel() {
        if (!StarMinerPlugin.worldWalkerTravel()) {
            return -1;
        }
        if (!StarTravel.isActive()) {
            return -1;
        }
        WorldPoint dest = StarTravel.destination();
        if (dest == null) {
            return -1;
        }
        if (target != null && StarMinerPlugin.hopEnabled) {
            try {
                if (Worlds.getCurrentWorld() != target.world) {
                    return -1;
                }
            } catch (Throwable ignored) {
                return -1;
            }
        }
        try {
            int teleDelay = tickStarTeleportIfUseful();
            if (teleDelay >= 0) {
                return teleDelay;
            }
        } catch (Throwable ignored) {
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (pos == null || pos.getPlane() != dest.getPlane()) {
            return -1;
        }
        if (pos.distanceTo(dest) <= WW_HANDOFF) {
            return -1;
        }
        return startWorldWalkerTo(dest);
    }

    /**
     * Ver onderweg: alleen walkTo (zoals Imp). Geen hopper/bank-widget-reads —
     * die timeout'en 1.5s en de hop is dan al uitgelopen.
     *
     * @return delay, of {@code -1} als de normale tick verder moet
     */
    private int tickFarTravelOnly() {
        if (StarMinerPlugin.worldWalkerTravel()) {
            return -1;
        }
        if (needsWorldHop()) {
            return -1;
        }
        if (!StarTravel.isActive() || !StarTravel.exclusiveTravel()) {
            return -1;
        }
        WorldPoint dest = StarTravel.destination();
        if (dest == null) {
            return -1;
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (pos == null || pos.getPlane() != dest.getPlane() || pos.distanceTo(dest) <= 14) {
            return -1;
        }
        int teleDelay = tickStarTeleportIfUseful();
        if (teleDelay >= 0) {
            return teleDelay;
        }
        try {
            net.storm.sdk.movement.Movement.ensureTravelRun();
        } catch (Throwable ignored) {
        }
        StarWalk.Result wr = StarTravel.preLoopWalk(pos);
        if (wr == null || wr.arrived) {
            return -1;
        }
        status = wr.status;
        noteCosMineFromTravel(wr);
        return afterWalk(wr, 0);
    }

    /**
     * Verkeerde wereld: eerst hoppen. Geen pad/arm — LoopHost pauzeert scripts tijdens travel.
     */
    private int hopTargetWorld() {
        if (target != null && target.world > 0) {
            return target.world;
        }
        return BotRuntime.starHopWorld;
    }

    private boolean needsWorldHop() {
        if (!StarMinerPlugin.hopEnabled) {
            BotRuntime.starHopWorld = 0;
            hopStuckSinceMs = 0L;
            return false;
        }
        int want = hopTargetWorld();
        if (want <= 0) {
            BotRuntime.starHopWorld = 0;
            hopStuckSinceMs = 0L;
            return false;
        }
        try {
            int w = Worlds.getCurrentWorld();
            if (w > 0 && w != want) {
                if (deferHopForGemBank()) {
                    BotRuntime.starHopWorld = 0;
                    hopStuckSinceMs = 0L;
                    return false;
                }
                BotRuntime.starHopWorld = want;
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
        BotRuntime.starHopWorld = 0;
        hopStuckSinceMs = 0L;
        return false;
    }

    private void cancelTravelForHop() {
        StarTravel.clear();
        lockedTravelDest = null;
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        cancelOwnedWorldWalker();
        try {
            log("eerst hop W" + target.world + " — geen loop op W" + Worlds.getCurrentWorld());
        } catch (Throwable ignored) {
            log("eerst hop — geen loop op deze wereld");
        }
    }

    private int hopTo(int worldId) {
        if (worldId <= 0) {
            return ThreadLocalRandom.current().nextInt(200, 360);
        }
        long now = System.currentTimeMillis();
        try {
            if (Bank.isOpen()) {
                hopStuckSinceMs = 0L;
                Bank.close();
                status = "bank dicht vóór hop";
                log("geen hop — bank open");
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
        } catch (Throwable ignored) {
        }
        // "Please finish what you're doing before using the World Switcher"
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            if (me != null && me.present && me.moving) {
                hopStuckSinceMs = 0L;
                status = "stilstand vóór hop";
                log("geen hop — nog aan het lopen");
                return ThreadLocalRandom.current().nextInt(280, 450);
            }
            if (me != null && me.present && me.animation != -1) {
                hopStuckSinceMs = 0L;
                status = "idle vóór hop (anim)";
                log("geen hop — animatie " + me.animation);
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
            if (me != null && me.present && me.interacting) {
                status = "idle vóór hop (interact)";
                return ThreadLocalRandom.current().nextInt(400, 700);
            }
        } catch (Throwable ignored) {
        }
        String why = WorldHopGate.blockReason(worldId, StarMinerPlugin.f2pOnly);
        if (why != null) {
            skipWorlds.add(worldId);
            log("skip W" + worldId + " — " + why);
            target = null;
            hopWorld = 0;
            hopAttempts = 0;
            return ThreadLocalRandom.current().nextInt(200, 360);
        }
        String denied = WorldHopGate.hopDeniedFromChat();
        if (denied != null && hopWorld == worldId && hopAttempts > 0
                && now - hopIssuedAt < 8_000L
                && Worlds.getCurrentWorld() != hopWorld) {
            skipWorlds.add(hopWorld);
            log("skip W" + hopWorld + " — " + denied);
            target = null;
            hopWorld = 0;
            hopAttempts = 0;
            return ThreadLocalRandom.current().nextInt(200, 360);
        }
        try {
            if (WorldHopper.isHopConfirmOpen()) {
                boolean clicked = WorldHopper.confirmSwitchWorld();
                hopWorld = worldId;
                hopIssuedAt = now;
                status = "hop bevestigen W" + worldId;
                log("Switch world → W" + worldId + " klik=" + clicked);
                return ThreadLocalRandom.current().nextInt(280, 450);
            }
        } catch (Throwable ignored) {
        }
        int cur = Worlds.getCurrentWorld();
        if (cur == worldId) {
            hopWorld = 0;
            hopIssuedAt = 0L;
            hopAttempts = 0;
            hopStuckSinceMs = 0L;
            BotRuntime.starHopWorld = 0;
            status = "al op W" + worldId;
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        if (hopWorld == worldId && hopIssuedAt > 0L) {
            if (now - hopIssuedAt < HOP_LAND_WAIT_MS) {
                status = "hop W" + worldId + " (wacht wereld, nu W" + cur + ")";
                return ThreadLocalRandom.current().nextInt(280, 480);
            }
            log("hop W" + worldId + " niet geland (nog W" + cur + ") poging " + hopAttempts);
            hopIssuedAt = 0L;
        }
        int unstick = maybeUnstickHop(worldId);
        if (unstick >= 0) {
            return unstick;
        }
        WorldHopper.HopIssue issue = hopAttempts >= 2
                ? WorldHopper.hopViaRow(worldId)
                : WorldHopper.hopDetailed(worldId);
        hopWorld = worldId;
        if (issue == WorldHopper.HopIssue.HOPPER_OPENED) {
            hopIssuedAt = 0L;
            if (hopAttempts < 2) {
                hopAttempts++;
            }
            status = "hopper open → hop W" + worldId;
            log(status);
            // Lijst/cache: members meteen skippen (geen hop-poging)
            String afterOpen = WorldHopGate.blockReason(worldId, StarMinerPlugin.f2pOnly);
            if (afterOpen != null) {
                skipWorlds.add(worldId);
                log("skip W" + worldId + " — " + afterOpen);
                target = null;
                hopWorld = 0;
                hopAttempts = 0;
                hopStuckSinceMs = 0L;
                BotRuntime.starHopWorld = 0;
                WorldHopper.dismissToInventory();
                return ThreadLocalRandom.current().nextInt(120, 220);
            }
            return ThreadLocalRandom.current().nextInt(200, 360);
        }
        hopAttempts++;
        if (issue == WorldHopper.HopIssue.ALREADY_THERE) {
            hopWorld = 0;
            hopIssuedAt = 0L;
            hopAttempts = 0;
            hopStuckSinceMs = 0L;
            BotRuntime.starHopWorld = 0;
            status = "al op W" + worldId;
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        hopIssuedAt = now;
        boolean ok = issue == WorldHopper.HopIssue.HOPPED || issue == WorldHopper.HopIssue.CONFIRM;
        status = (ok ? "hop → W" : "hop-fail W") + worldId;
        log(status + " " + issue + " poging " + hopAttempts);
        if (!ok) {
            int again = maybeUnstickHop(worldId);
            if (again >= 0) {
                return again;
            }
        }
        return ThreadLocalRandom.current().nextInt(280, 450);
    }

    /**
     * Hop blijft op dezelfde wereld. Niet skippen: loop herstarten, daarna ↻ Star.
     */
    private int maybeUnstickHop(int worldId) {
        long now = System.currentTimeMillis();
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            if (me != null && me.present && me.moving) {
                hopStuckSinceMs = 0L;
                return -1;
            }
        } catch (Throwable ignored) {
        }
        if (hopStuckSinceMs <= 0L) {
            hopStuckSinceMs = now;
        }
        long stuck = now - hopStuckSinceMs;
        try {
            if (Worlds.isHopperOpen() && hopAttempts < 2 && stuck < 20_000L) {
                return -1;
            }
        } catch (Throwable ignored) {
        }
        if (stuck >= 20_000L && now - lastHopScriptReloadMs >= 20_000L) {
            lastHopScriptReloadMs = now;
            hopAttempts = 0;
            hopIssuedAt = 0L;
            cancelTravelForHop();
            try {
                WorldHopper.resetOpenClock();
            } catch (Throwable ignored) {
            }
            log("hop vast W" + worldId + " " + (stuck / 1000L) + "s — scripts herstarten");
            BotRuntime.requestScriptReload("star");
            net.storm.sdk.loop.LoopHost.requestLoopRestart("star-hop");
            status = "scripts herstart → hop W" + worldId;
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        if (stuck >= 12_000L && now - lastHopLoopRestartMs >= 10_000L) {
            lastHopLoopRestartMs = now;
            hopIssuedAt = 0L;
            cancelTravelForHop();
            try {
                WorldHopper.resetOpenClock();
            } catch (Throwable ignored) {
            }
            log("hop vast W" + worldId + " " + (stuck / 1000L) + "s — loop herstarten");
            net.storm.sdk.loop.LoopHost.requestLoopRestart("star-hop");
            status = "loop herstart → hop W" + worldId;
            return ThreadLocalRandom.current().nextInt(280, 450);
        }
        return -1;
    }

    private void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star] " + msg);
    }
}
