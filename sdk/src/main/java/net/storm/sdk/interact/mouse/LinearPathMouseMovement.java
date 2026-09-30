package net.storm.sdk.interact.mouse;

import net.storm.api.interact.mouse.MouseMovementStrategy;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Storm-compat LinearPathMouseMovement. */
public final class LinearPathMouseMovement implements MouseMovementStrategy {

    @Override
    public MousePath generatePath(Point current, Point target) {
        if (current == null || target == null) {
            return new MousePath(Collections.emptyList());
        }
        int dist = Math.max(1, (int) Math.hypot(target.x - current.x, target.y - current.y));
        int steps = Math.min(40, Math.max(6, dist / 10));
        List<Point> out = new ArrayList<>(steps);
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            out.add(new Point(
                    (int) Math.round(current.x + (target.x - current.x) * t),
                    (int) Math.round(current.y + (target.y - current.y) * t)));
        }
        return new MousePath(out);
    }
}
