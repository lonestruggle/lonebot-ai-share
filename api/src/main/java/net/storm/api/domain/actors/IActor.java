package net.storm.api.domain.actors;

import net.runelite.api.Actor;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.RuneLiteWrapper;
import net.storm.api.domain.SceneEntity;

import java.awt.Shape;

/**
 * Actor (player or NPC). LoneBot wraps RL {@link Actor} instead of extending it
 * (vanilla Actor has client-only setters). Storm methods are on this interface.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/actors/IActor.html">Storm IActor</a>
 */
public interface IActor extends SceneEntity, RuneLiteWrapper<Actor> {

    @Override
    String getName();

    @Override
    WorldPoint getWorldLocation();

    int getAnimation();

    boolean isMoving();

    int getHealthRatio();

    int getHealthScale();

    @Override
    boolean hasAction(String action);

    @Override
    boolean interact(String action);

    /** Storm: left-click Attack. */
    default void attack() {
        interact("Attack");
    }

    int getIndex();

    IActor getInteracting();

    /** Combat target — RL only exposes {@link Actor#getInteracting()}, so same as {@link #getInteracting()}. */
    default IActor getTarget() {
        return getInteracting();
    }

    int getSpotAnimationCount();

    int getCombatLevel();

    String getOverheadText();

    LocalPoint getLocalLocation();

    WorldArea getWorldArea();

    int getOrientation();

    int getPoseAnimation();

    int getIdlePoseAnimation();

    int getGraphic();

    boolean isInteracting();

    Shape getConvexHull();

    Point getMinimapLocation();

    Point getCanvasPoint();

    int getLogicalHeight();

    default boolean isAnimating() {
        return getAnimation() != -1;
    }

    default boolean isIdle() {
        return !isAnimating() && !isMoving();
    }

    default boolean isDead() {
        int scale = getHealthScale();
        int ratio = getHealthRatio();
        return scale > 0 && ratio == 0;
    }

    default boolean isHealthBarVisible() {
        return getHealthScale() > 0 && getHealthRatio() >= 0;
    }
}
