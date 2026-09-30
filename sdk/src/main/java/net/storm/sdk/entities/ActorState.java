package net.storm.sdk.entities;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.game.Static;

/**
 * Client-thread snapshots for combat decisions (Storm IActor-style checks).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/actors/IActor.html">Storm IActor</a>
 */
public final class ActorState {

    private ActorState() {
    }

    public static final class NpcSnap {
        public final boolean found;
        public final int index;
        public final int id;
        public final String name;
        public final WorldPoint worldLocation;
        public final int animation;
        public final boolean animating;
        public final boolean moving;
        public final boolean idle;
        public final boolean dead;
        public final boolean healthBarVisible;
        public final int healthRatio;
        public final int healthScale;
        public final String interactingName;
        public final boolean interactingWithLocal;
        public final boolean hasAttack;

        public NpcSnap(boolean found, int index, int id, String name, WorldPoint worldLocation,
                       int animation, boolean animating, boolean moving, boolean idle, boolean dead,
                       boolean healthBarVisible, int healthRatio, int healthScale,
                       String interactingName, boolean interactingWithLocal, boolean hasAttack) {
            this.found = found;
            this.index = index;
            this.id = id;
            this.name = name;
            this.worldLocation = worldLocation;
            this.animation = animation;
            this.animating = animating;
            this.moving = moving;
            this.idle = idle;
            this.dead = dead;
            this.healthBarVisible = healthBarVisible;
            this.healthRatio = healthRatio;
            this.healthScale = healthScale;
            this.interactingName = interactingName;
            this.interactingWithLocal = interactingWithLocal;
            this.hasAttack = hasAttack;
        }
    }

    public static final class ThreatSnap {
        public final boolean underAttack;
        public final String attackerName;
        public final int attackerIndex;

        public ThreatSnap(boolean underAttack, String attackerName, int attackerIndex) {
            this.underAttack = underAttack;
            this.attackerName = attackerName;
            this.attackerIndex = attackerIndex;
        }
    }

    public static NpcSnap ofIndex(int index) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return missing();
            }
            Player local = c.getLocalPlayer();
            for (NPC n : c.getNpcs()) {
                if (n != null && n.getIndex() == index) {
                    return fromNpc(n, local);
                }
            }
            return missing();
        }, missing());
    }

    /** Worden wij aangevallen? (NPC interactingt met local player) */
    public static ThreatSnap threatToLocal() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return new ThreatSnap(false, null, -1);
            }
            Player local = c.getLocalPlayer();
            if (local == null) {
                return new ThreatSnap(false, null, -1);
            }
            for (NPC n : c.getNpcs()) {
                if (n == null) {
                    continue;
                }
                try {
                    Actor inter = n.getInteracting();
                    if (inter == local) {
                        return new ThreatSnap(true, n.getName(), n.getIndex());
                    }
                } catch (Throwable ignored) {
                }
            }
            return new ThreatSnap(false, null, -1);
        }, new ThreatSnap(false, null, -1));
    }

    private static NpcSnap fromNpc(NPC n, Player local) {
        int anim = n.getAnimation();
        boolean moving = n.getPoseAnimation() != n.getIdlePoseAnimation();
        boolean animating = anim != -1;
        int ratio = n.getHealthRatio();
        int scale = n.getHealthScale();
        boolean dead;
        try {
            dead = n.isDead() || (scale > 0 && ratio == 0);
        } catch (Throwable t) {
            dead = scale > 0 && ratio == 0;
        }
        boolean healthBar = scale > 0 && ratio >= 0;
        String interName = null;
        boolean interLocal = false;
        try {
            Actor inter = n.getInteracting();
            if (inter != null) {
                interName = inter.getName();
                interLocal = local != null && inter == local;
            }
        } catch (Throwable ignored) {
        }
        boolean hasAttack = false;
        try {
            net.runelite.api.NPCComposition comp = n.getTransformedComposition();
            if (comp == null) {
                comp = n.getComposition();
            }
            if (comp != null && comp.getActions() != null) {
                for (String a : comp.getActions()) {
                    if (a != null && a.equalsIgnoreCase("Attack")) {
                        hasAttack = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new NpcSnap(true, n.getIndex(), n.getId(), n.getName(), n.getWorldLocation(),
                anim, animating, moving, !animating && !moving, dead, healthBar, ratio, scale,
                interName, interLocal, hasAttack);
    }

    private static NpcSnap missing() {
        return new NpcSnap(false, -1, -1, null, null, -1, false, false, true, true,
                false, -1, -1, null, false, false);
    }
}
