package com.lonebot.example;

import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.ActorState;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileItems;
import net.storm.sdk.game.Combat;
import net.storm.sdk.game.Game;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.interact.NpcTargetTests;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.utils.AntiBan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Cow combat loop (Storm-style checks):
 * loot → under-attack/in-combat wait → skip dead → attack via mouse hull click.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/actors/IActor.html">IActor</a>
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/tiles/ITileItem.html">ITileItem</a>
 */
@PluginDescriptor(
        name = "LoneBot Cow Combat",
        description = "Aanvallen + combat-checks + loot oppakken"
)
public class CowCombatPlugin extends LoopedPlugin {

    private static final Logger log = LoggerFactory.getLogger(CowCombatPlugin.class);

    /** Cow loot (Storm TileItems.pickup). */
    private static final String[] LOOT_NAMES = {
            "Bones", "Cowhide", "Raw beef", "Raw undead beef", "Coins"
    };
    private static final int LOOT_RADIUS = 8;

    /** Toggle from panel/config — default on when cow combat on. */
    public static volatile boolean lootEnabled = true;

    private long lastAttackMs;
    private long lastLootMs;
    private int lastTargetIndex = -1;

    @Override
    public int loop() {
        if (!BotRuntime.botEnabled || !BotRuntime.cowCombatEnabled) {
            BotRuntime.cowStatus = BotRuntime.botEnabled ? "uit" : "bot uit";
            lastTargetIndex = -1;
            return 800;
        }
        if (net.storm.sdk.bot.ClueSkillHandoff.tryHandoffFromActiveSkill()) {
            BotRuntime.cowStatus = "clue handoff";
            return 400;
        }

        BotRuntime.cowLoopTicks++;
        if (!Game.isLoggedIn()) {
            BotRuntime.cowStatus = "niet ingelogd";
            return 1000;
        }

        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present) {
            BotRuntime.cowStatus = "geen speler";
            return 600;
        }

        int antiDelay = AntiBan.get().check();
        if (antiDelay > 0) {
            BotRuntime.cowStatus = "antiban: " + AntiBan.get().getLastActionLabel();
            return Math.min(antiDelay, 2500);
        }

        ActorState.ThreatSnap threat = ActorState.threatToLocal();
        BotRuntime.debugSummary = "hp=" + (int) Combat.getHealthPercent()
                + "% anim=" + me.animation
                + (threat.underAttack ? " ←" + threat.attackerName : "")
                + (me.interacting ? " →" + me.interactingName : "");

        // 1) Loot eerst als we niet midden in een swing zitten
        if (lootEnabled && !Inventory.isFull() && System.currentTimeMillis() - lastLootMs > 400L) {
            int lootDelay = tryLoot(me);
            if (lootDelay > 0) {
                return lootDelay;
            }
        }

        // 2) Wij in combat / worden aangevallen → monitor target (dood? anim?)
        if (me.interacting || threat.underAttack) {
            int targetIdx = me.interactingNpcIndex >= 0 ? me.interactingNpcIndex : threat.attackerIndex;
            if (targetIdx >= 0) {
                lastTargetIndex = targetIdx;
            }
            if (lastTargetIndex >= 0) {
                ActorState.NpcSnap npc = ActorState.ofIndex(lastTargetIndex);
                if (!npc.found || npc.dead) {
                    BotRuntime.cowStatus = "target dood — loot";
                    lastTargetIndex = -1;
                    return random(300, 500);
                }
                BotRuntime.cowStatus = "combat " + safe(npc.name)
                        + " hp=" + npc.healthRatio + "/" + npc.healthScale
                        + (npc.animating ? " anim" : "")
                        + (npc.moving ? " move" : "")
                        + (threat.underAttack ? " (wij onder vuur)" : "");
                return random(350, 600);
            }
            BotRuntime.cowStatus = "in combat… " + safe(me.interactingName)
                    + (me.animating ? " [anim " + me.animation + "]" : "");
            return random(400, 700);
        }

        long now = System.currentTimeMillis();
        if (now - lastAttackMs < 700L) {
            BotRuntime.cowStatus = "anti-spam";
            return random(200, 400);
        }

        // 3) Nieuw target — skip dode cows
        NpcTargetTests.TargetInfo t = NpcTargetTests.findNearestCow();
        if (!t.found) {
            BotRuntime.cowStatus = t.detail;
            return random(500, 900);
        }

        ActorState.NpcSnap live = ActorState.ofIndex(t.index);
        if (live.found && live.dead) {
            BotRuntime.cowStatus = "skip dode " + safe(t.name);
            return random(200, 400);
        }

        Point canvas = t.hullCanvas != null ? t.hullCanvas : t.localCanvas;
        if (canvas == null || canvas.getX() < 0 || canvas.getY() < 0) {
            BotRuntime.cowStatus = "cow off-screen world=" + t.worldX + "," + t.worldY;
            return random(500, 800);
        }
        if (net.storm.sdk.interact.UiClickGuard.isBlocked(canvas)) {
            BotRuntime.cowStatus = "cow achter UI — skip";
            return random(300, 500);
        }

        BotRuntime.cowStatus = "Attack " + t.name + " @" + canvas.getX() + "," + canvas.getY()
                + (live.found && live.healthBarVisible ? " hpBar" : "");
        BotRuntime.mouseSummary = "target=" + canvas.getX() + "," + canvas.getY() + " idx=" + t.index;

        boolean ok = MouseManager.interactAt(canvas);
        BotRuntime.mouseSummary = Mouse.getLastAction() + " | " + Mouse.getLastProof();
        lastAttackMs = System.currentTimeMillis();
        lastTargetIndex = t.index;
        if (ok) {
            log.info("[Cow] Attack {} idx={} canvas={},{} dead={} animating={}",
                    t.name, t.index, canvas.getX(), canvas.getY(), live.dead, live.animating);
            return random(700, 1100);
        }
        BotRuntime.cowStatus = "attack-klik mislukt";
        return random(400, 700);
    }

    /** Storm TileItems: nearest loot in radius → mouse Take. */
    private int tryLoot(Players.LocalSnap me) {
        if (me == null || !me.present || me.worldLocation == null) {
            return 0;
        }
        final WorldPoint here = me.worldLocation;
        ITileItem loot = TileItems.getNearest(item -> {
            if (item == null || item.getName() == null || item.getWorldLocation() == null) {
                return false;
            }
            if (ExcludedTiles.isExcluded(item.getWorldLocation())) {
                return false;
            }
            if (here.distanceTo(item.getWorldLocation()) > LOOT_RADIUS) {
                return false;
            }
            String n = item.getName();
            for (String want : LOOT_NAMES) {
                if (want.equalsIgnoreCase(n)) {
                    return item.canPick();
                }
            }
            return false;
        });
        if (loot == null) {
            return 0;
        }
        BotRuntime.cowStatus = "loot→ " + loot.getName() + " x" + loot.getQuantity();
        boolean ok = loot.pickup();
        lastLootMs = System.currentTimeMillis();
        BotRuntime.mouseSummary = Mouse.getLastAction() + " | " + Mouse.getLastProof();
        if (ok) {
            log.info("[Cow] pickup {} x{}", loot.getName(), loot.getQuantity());
            return random(500, 900);
        }
        BotRuntime.cowStatus = "loot mislukt " + loot.getName();
        return random(300, 500);
    }

    private static String safe(String s) {
        return s != null ? s : "?";
    }

    private static int random(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
