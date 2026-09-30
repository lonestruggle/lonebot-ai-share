package com.lonebot.example.giants;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.magic.SpellBook;
import net.storm.sdk.entities.ActorState;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Combat;
import net.storm.sdk.interact.AimInteractHelper;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.widgets.Tabs;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Attack nearest Hill Giant. Mage: setAutoCast via Combat Options, then only Attack.
 * Moving NPCs: invoke-first. Anti-stuck if targeted without combat anim.
 */
public final class GiantsCombat {

    private static final long STUCK_ANIM_MS = 2_800L;

    private GiantsCombat() {
    }

    public static INPC nearestGiant(WorldPoint pos, int radius) {
        List<INPC> all = NPCs.getAll(n -> isHillGiant(n)
                && n.getWorldLocation() != null
                && GiantsLocations.isInHuntZone(n.getWorldLocation(), radius)
                && !n.isDead()
                && !ExcludedTiles.isNpcExcluded(n.getWorldLocation(), 2));
        INPC best = null;
        int bestD = Integer.MAX_VALUE;
        WorldPoint from = pos != null ? pos : GiantsLocations.HUNT_CENTER;
        for (INPC n : all) {
            int d = from.distanceTo(n.getWorldLocation());
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    public static boolean isHillGiant(INPC n) {
        if (n == null) {
            return false;
        }
        String name = n.getName();
        return name != null && name.equalsIgnoreCase(GiantsLocations.HILL_GIANT);
    }

    public static boolean meInCombat() {
        try {
            if (Combat.isInCombat()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        Players.LocalSnap me = Players.snapshotLocal();
        return me != null && me.interacting;
    }

    public static boolean targetedByGiant() {
        ActorState.ThreatSnap threat = ActorState.threatToLocal();
        if (threat != null && threat.underAttack && threat.attackerName != null
                && threat.attackerName.equalsIgnoreCase(GiantsLocations.HILL_GIANT)) {
            return true;
        }
        return NPCs.getNearest(n -> {
            if (!isHillGiant(n) || n.isDead()) {
                return false;
            }
            ActorState.NpcSnap snap = ActorState.ofIndex(n.getIndex());
            return snap != null && snap.found && snap.interactingWithLocal;
        }) != null;
    }

    public static boolean hasCombatAnim(Players.LocalSnap me) {
        return me != null && (me.animating || me.animation > 0);
    }

    /**
     * @return delay, or 0 if autocast already ready
     */
    public static int ensureAutocast(GiantsTypes.GiantsMageSpell spell) {
        if (spell == null) {
            return 0;
        }
        SpellBook.Standard std = spell.getStandardSpell();
        if (std == null) {
            return 0;
        }
        if (Magic.isAutoCasting(std)) {
            return 0;
        }
        GiantsLog.action("mage", "autocast " + spell.getSpellName());
        if (Magic.setAutoCast(std, false) && Magic.isAutoCasting(std)) {
            try {
                Tabs.open(net.storm.api.widgets.Tab.INVENTORY);
            } catch (Throwable ignored) {
            }
            AntiBan.get().markBotActivity();
            return ThreadLocalRandom.current().nextInt(400, 700);
        }
        return ThreadLocalRandom.current().nextInt(500, 800);
    }

    public static int attack(INPC giant) {
        if (giant == null) {
            return 80;
        }
        boolean ok = false;
        try {
            ok = MenuInteract.interactNpcByIndex(giant.getIndex(), "Attack");
        } catch (Throwable ignored) {
        }
        if (!ok) {
            ok = AimInteractHelper.interactNpc(giant, "Attack");
        }
        if (ok) {
            AntiBan.get().markBotActivity();
            GiantsLog.action("combat", "Attack " + (giant.getName() != null ? giant.getName() : "Hill Giant"));
            return ThreadLocalRandom.current().nextInt(180, 320);
        }
        GiantsLog.action("combat", "attack fail");
        return 200;
    }

    public static boolean shouldForceUnstuck(Players.LocalSnap me, long lastCombatAnimMs) {
        if (!targetedByGiant()) {
            return false;
        }
        if (hasCombatAnim(me)) {
            return false;
        }
        long now = System.currentTimeMillis();
        return lastCombatAnimMs > 0L && now - lastCombatAnimMs >= STUCK_ANIM_MS;
    }
}
