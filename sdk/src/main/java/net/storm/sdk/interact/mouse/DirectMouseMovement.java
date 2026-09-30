package net.storm.sdk.interact.mouse;

import net.storm.api.interact.mouse.MouseMovementStrategy;

import java.awt.Point;
import java.util.Collections;

/** Storm-compat DirectMouseMovement — één stap naar target. */
public final class DirectMouseMovement implements MouseMovementStrategy {

    @Override
    public MousePath generatePath(Point current, Point target) {
        if (target == null) {
            return new MousePath(Collections.emptyList());
        }
        return new MousePath(Collections.singletonList(new Point(target)));
    }
}
