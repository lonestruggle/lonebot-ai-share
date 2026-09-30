package net.storm.sdk.entities;

import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.IActor;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.AimInteractHelper;

import java.awt.Shape;

final class RlNpc implements INPC {

    private NPC npc;

    RlNpc(NPC npc) {
        this.npc = npc;
    }

    @Override
    public NPC getWrapped() {
        return npc;
    }

    NPC unwrap() {
        return npc;
    }

    @Override
    public void update(NPC npc) {
        if (npc != null) {
            this.npc = npc;
        }
    }

    @Override
    public String getName() {
        return ActorAccess.name(this.npc);
    }

    @Override
    public WorldPoint getWorldLocation() {
        return ActorAccess.world(this.npc);
    }

    @Override
    public int getAnimation() {
        return ActorAccess.animation(this.npc);
    }

    @Override
    public boolean isMoving() {
        return ActorAccess.moving(this.npc);
    }

    @Override
    public int getHealthRatio() {
        return ActorAccess.healthRatio(this.npc);
    }

    @Override
    public int getHealthScale() {
        return ActorAccess.healthScale(this.npc);
    }

    @Override
    public boolean hasAction(String action) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            NPCComposition comp = transformedComp();
            if (comp == null || comp.getActions() == null) {
                return false;
            }
            for (String a : comp.getActions()) {
                if (a != null && a.equalsIgnoreCase(action)) {
                    return true;
                }
            }
            return false;
        }, false));
    }

    @Override
    public boolean interact(String action) {
        return AimInteractHelper.interactNpc(this, action);
    }

    @Override
    public int getId() {
        Integer v = Static.callOnClientThread(npc::getId, -1);
        return v != null ? v : -1;
    }

    @Override
    public int getIndex() {
        Integer v = Static.callOnClientThread(npc::getIndex, -1);
        return v != null ? v : -1;
    }

    @Override
    public IActor getInteracting() {
        return ActorAccess.interacting(this.npc);
    }

    @Override
    public int getSpotAnimationCount() {
        return ActorAccess.spotAnimCount(this.npc);
    }

    @Override
    public int getCombatLevel() {
        return ActorAccess.combatLevel(this.npc);
    }

    @Override
    public String getOverheadText() {
        return ActorAccess.overheadText(this.npc);
    }

    @Override
    public LocalPoint getLocalLocation() {
        return ActorAccess.local(this.npc);
    }

    @Override
    public WorldArea getWorldArea() {
        return ActorAccess.worldArea(this.npc);
    }

    @Override
    public int getOrientation() {
        return ActorAccess.orientation(this.npc);
    }

    @Override
    public int getPoseAnimation() {
        return ActorAccess.poseAnimation(this.npc);
    }

    @Override
    public int getIdlePoseAnimation() {
        return ActorAccess.idlePose(this.npc);
    }

    @Override
    public int getGraphic() {
        return ActorAccess.graphic(this.npc);
    }

    @Override
    public boolean isInteracting() {
        return ActorAccess.isInteracting(this.npc);
    }

    @Override
    public Shape getConvexHull() {
        return ActorAccess.convexHull(this.npc);
    }

    @Override
    public Point getMinimapLocation() {
        return ActorAccess.minimap(this.npc);
    }

    @Override
    public int getLogicalHeight() {
        return ActorAccess.logicalHeight(this.npc);
    }

    @Override
    public Object getWorldView() {
        return ActorAccess.worldView(this.npc);
    }

    @Override
    public Point getCanvasPoint() {
        return Static.callOnClientThread(() -> {
            net.runelite.api.Client c = Static.getClient();
            if (c == null || npc == null) {
                return null;
            }
            LocalPoint lp = npc.getLocalLocation();
            if (lp == null) {
                return null;
            }
            int height = Math.max(0, ActorAccess.logicalHeight(npc) / 2);
            return Perspective.localToCanvas(c, lp, c.getPlane(), height);
        }, null);
    }

    @Override
    public int getActualId() {
        Integer v = Static.callOnClientThread(() -> {
            NPCComposition t = transformedComp();
            if (t != null) {
                return t.getId();
            }
            return npc.getId();
        }, -1);
        return v != null ? v : -1;
    }

    @Override
    public NPCComposition getTransformedComposition() {
        return Static.callOnClientThread(this::transformedComp, null);
    }

    @Override
    public HeadIcon[] getOverheadIcons() {
        return Static.callOnClientThread(() -> {
            try {
                Object arr = npc.getClass().getMethod("getOverheadIcons").invoke(npc);
                if (arr instanceof HeadIcon[]) {
                    return (HeadIcon[]) arr;
                }
            } catch (Throwable ignored) {
            }
            try {
                Object one = npc.getClass().getMethod("getOverheadIcon").invoke(npc);
                if (one instanceof HeadIcon) {
                    return new HeadIcon[]{(HeadIcon) one};
                }
            } catch (Throwable ignored) {
            }
            return new HeadIcon[0];
        }, new HeadIcon[0]);
    }

    private NPCComposition transformedComp() {
        NPCComposition comp = npc.getTransformedComposition();
        return comp != null ? comp : npc.getComposition();
    }
}
