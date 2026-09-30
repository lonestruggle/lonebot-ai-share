package net.storm.sdk.entities;

import net.runelite.api.HeadIcon;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Point;
import net.runelite.api.SkullIcon;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.IActor;
import net.storm.api.domain.actors.IPlayer;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;

import java.awt.Shape;

final class RlPlayer implements IPlayer {

    private Player player;

    RlPlayer(Player player) {
        this.player = player;
    }

    @Override
    public Player getWrapped() {
        return player;
    }

    Player unwrap() {
        return player;
    }

    @Override
    public void update(Player player) {
        if (player != null) {
            this.player = player;
        }
    }

    @Override
    public String getName() {
        return ActorAccess.name(this.player);
    }

    @Override
    public WorldPoint getWorldLocation() {
        return ActorAccess.world(this.player);
    }

    @Override
    public int getAnimation() {
        return ActorAccess.animation(this.player);
    }

    @Override
    public boolean isMoving() {
        return ActorAccess.moving(this.player);
    }

    @Override
    public int getHealthRatio() {
        return ActorAccess.healthRatio(this.player);
    }

    @Override
    public int getHealthScale() {
        return ActorAccess.healthScale(this.player);
    }

    @Override
    public boolean hasAction(String action) {
        if (action == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            net.runelite.api.Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                String[] opts = c.getPlayerOptions();
                if (opts == null) {
                    return false;
                }
                for (String o : opts) {
                    if (o != null && o.equalsIgnoreCase(action)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
            return false;
        }, false));
    }

    @Override
    public boolean interact(String action) {
        return MenuInteract.interactPlayer(player, action);
    }

    @Override
    public int getId() {
        Integer v = Static.callOnClientThread(player::getId, -1);
        return v != null ? v : -1;
    }

    @Override
    public int getIndex() {
        return getId();
    }

    @Override
    public IActor getInteracting() {
        return ActorAccess.interacting(this.player);
    }

    @Override
    public int getSpotAnimationCount() {
        return ActorAccess.spotAnimCount(this.player);
    }

    @Override
    public int getCombatLevel() {
        return ActorAccess.combatLevel(this.player);
    }

    @Override
    public String getOverheadText() {
        return ActorAccess.overheadText(this.player);
    }

    @Override
    public LocalPoint getLocalLocation() {
        return ActorAccess.local(this.player);
    }

    @Override
    public WorldArea getWorldArea() {
        return ActorAccess.worldArea(this.player);
    }

    @Override
    public int getOrientation() {
        return ActorAccess.orientation(this.player);
    }

    @Override
    public int getPoseAnimation() {
        return ActorAccess.poseAnimation(this.player);
    }

    @Override
    public int getIdlePoseAnimation() {
        return ActorAccess.idlePose(this.player);
    }

    @Override
    public int getGraphic() {
        return ActorAccess.graphic(this.player);
    }

    @Override
    public boolean isInteracting() {
        return ActorAccess.isInteracting(this.player);
    }

    @Override
    public Shape getConvexHull() {
        return ActorAccess.convexHull(this.player);
    }

    @Override
    public Point getMinimapLocation() {
        return ActorAccess.minimap(this.player);
    }

    @Override
    public Point getCanvasPoint() {
        return Static.callOnClientThread(() -> {
            net.runelite.api.Client c = Static.getClient();
            if (c == null || player == null) {
                return null;
            }
            LocalPoint lp = player.getLocalLocation();
            if (lp == null) {
                return null;
            }
            int height = Math.max(0, ActorAccess.logicalHeight(player) / 2);
            return net.runelite.api.Perspective.localToCanvas(c, lp, c.getPlane(), height);
        }, null);
    }

    @Override
    public int getLogicalHeight() {
        return ActorAccess.logicalHeight(this.player);
    }

    @Override
    public Object getWorldView() {
        return ActorAccess.worldView(this.player);
    }

    @Override
    public boolean isFriend() {
        return Boolean.TRUE.equals(Static.callOnClientThread(player::isFriend, false));
    }

    @Override
    public boolean isClanMember() {
        return Boolean.TRUE.equals(Static.callOnClientThread(player::isClanMember, false));
    }

    @Override
    public boolean isFriendsChatMember() {
        return Boolean.TRUE.equals(Static.callOnClientThread(player::isFriendsChatMember, false));
    }

    @Override
    public HeadIcon getOverheadIcon() {
        return Static.callOnClientThread(player::getOverheadIcon, null);
    }

    @Override
    public int getSkullIcon() {
        Integer v = Static.callOnClientThread(() -> {
            try {
                Object raw = player.getClass().getMethod("getSkullIcon").invoke(player);
                if (raw instanceof Integer) {
                    return (Integer) raw;
                }
                if (raw instanceof Number) {
                    return ((Number) raw).intValue();
                }
            } catch (Throwable ignored) {
            }
            return -1;
        }, -1);
        return v != null ? v : -1;
    }

    @Override
    public int getTeam() {
        Integer v = Static.callOnClientThread(player::getTeam, 0);
        return v != null ? v : 0;
    }

    @Override
    public PlayerComposition getPlayerComposition() {
        return Static.callOnClientThread(player::getPlayerComposition, null);
    }
}
