package com.lonebot.example;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.game.Game;
import net.storm.sdk.items.Bank;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.PlaneChangeHelper;
import net.storm.sdk.utils.AntiBan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Smoke-test: full global path → Varrock East bank → open bank.
 */
@PluginDescriptor(
        name = "LoneBot Varrock East Bank Test",
        description = "Lopen naar Varrock East bank + openen"
)
public class VarrockEastBankTestPlugin extends LoopedPlugin {

    private static final Logger log = LoggerFactory.getLogger(VarrockEastBankTestPlugin.class);

    private static final int NEAR_BANK_TILES = 8;
    private long startedAtMs;
    private boolean loggedStart;
    private int pathTiles = -1;

    @Override
    public int loop() {
        if (!BotRuntime.varrockEastBankTestEnabled) {
            BotRuntime.bankTestStatus = "uit";
            loggedStart = false;
            startedAtMs = 0;
            pathTiles = -1;
            return 0;
        }
        if (!BotRuntime.botEnabled) {
            BotRuntime.bankTestStatus = "bot uit";
            return 800;
        }

        if (!Game.isLoggedIn()) {
            BotRuntime.bankTestStatus = "niet ingelogd";
            return 800;
        }

        if (startedAtMs == 0) {
            startedAtMs = System.currentTimeMillis();
            MovementHelper.clearPath();
            pathTiles = -1;
        }
        if (!loggedStart) {
            loggedStart = true;
            log.info("[VarrockEastBankTest] start → {} (full path first)", BankHelper.VARROCK_EAST_BANK);
        }

        if (Bank.isOpen()) {
            Players.LocalSnap me = Players.snapshotLocal();
            WorldPoint pos = me != null && me.present ? me.worldLocation : null;
            String tile = pos != null ? pos.getX() + "," + pos.getY() + "," + pos.getPlane() : "?";
            BotRuntime.bankTestStatus = "✓ bank open @" + tile;
            BotRuntime.debugSummary = "Varrock East bank OPEN";
            log.info("[VarrockEastBankTest] SUCCESS — bank open @{}", tile);
            BotRuntime.varrockEastBankTestEnabled = false;
            MovementHelper.clearPath();
            startedAtMs = 0;
            loggedStart = false;
            pathTiles = -1;
            return 1200;
        }

        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (pos == null) {
            BotRuntime.bankTestStatus = "geen speler";
            return 500;
        }

        // Ensure full path is dotted before / while walking
        if (PlaneChangeHelper.needsPlaneChange(BankHelper.VARROCK_EAST_BANK)) {
            String hint = PlaneChangeHelper.statusHint(BankHelper.VARROCK_EAST_BANK);
            BotRuntime.bankTestStatus = "climb floor… " + hint + " @" + pos.getX() + "," + pos.getY() + ",p" + pos.getPlane();
            BotRuntime.debugSummary = BotRuntime.bankTestStatus;
            boolean climbed = PlaneChangeHelper.progressTowardPlane(BankHelper.VARROCK_EAST_BANK);
            if (!climbed) {
                BotRuntime.bankTestStatus = "⚠ verkeerde floor (p" + pos.getPlane()
                        + ") — ga naar begane grond / uit grot";
            }
            return ThreadLocalRandom.current().nextInt(500, 900);
        }

        if (pathTiles < 0) {
            BotRuntime.bankTestStatus = "path berekenen…";
            TilePath path = MovementHelper.getPath(BankHelper.VARROCK_EAST_BANK);
            pathTiles = path != null ? path.size() : 0;
            if (pathTiles <= 0) {
                BotRuntime.bankTestStatus = "⚠ geen path @" + pos.getX() + "," + pos.getY()
                        + ",p" + pos.getPlane() + " — grot/afgesloten?";
                log.warn("[VarrockEastBankTest] GlobalPathfinder returned empty path from {}", pos);
                // Toch walkTo proberen (plane-helper / scene fallback)
                MovementHelper.walkTo(BankHelper.VARROCK_EAST_BANK);
                return 1000;
            }
            log.info("[VarrockEastBankTest] path dotted: {} tiles", pathTiles);
            BotRuntime.debugSummary = "path=" + pathTiles + " tiles";
            return ThreadLocalRandom.current().nextInt(200, 400);
        }

        int dist = pos.distanceTo(BankHelper.VARROCK_EAST_BANK);
        long elapsedSec = (System.currentTimeMillis() - startedAtMs) / 1000L;
        boolean near = dist <= NEAR_BANK_TILES;
        TilePath active = MovementHelper.getActivePath();
        int rem = active != null ? active.getRemainingPath(pos).size() : pathTiles;
        BotRuntime.bankTestStatus = (near ? "open bank… " : "walk path… ")
                + "d=" + dist + " rem=" + rem + "/" + pathTiles
                + " @" + pos.getX() + "," + pos.getY()
                + " t=" + elapsedSec + "s";
        BotRuntime.debugSummary = BotRuntime.bankTestStatus;

        int antiDelay = AntiBan.get().check();
        if (antiDelay > 0) {
            BotRuntime.bankTestStatus = "antiban: " + AntiBan.get().getLastActionLabel();
            return Math.min(antiDelay, 2500);
        }

        boolean acted = BankHelper.progressOpenVarrockEastBank();
        if (!acted && !me.moving) {
            BotRuntime.bankTestStatus = "⚠ geen walk/open (d=" + dist + " rem=" + rem + ")";
            log.warn("[VarrockEastBankTest] progress false dist={} rem={} @{},{}",
                    dist, rem, pos.getX(), pos.getY());
        }

        if (near) {
            return ThreadLocalRandom.current().nextInt(420, 720);
        }
        if (me.moving) {
            return ThreadLocalRandom.current().nextInt(280, 480);
        }
        return ThreadLocalRandom.current().nextInt(380, 620);
    }
}
