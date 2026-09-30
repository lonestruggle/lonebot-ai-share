package net.storm.sdk.interact.mouse;

import net.storm.api.interact.mouse.MouseMovementStrategy;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Storm-compat BezierCurveMouseMovement — natuurlijke boog naar target.
 */
public final class BezierCurveMouseMovement implements MouseMovementStrategy {

    private final int minSteps;
    private final int maxSteps;

    public BezierCurveMouseMovement() {
        this(18, 42);
    }

    public BezierCurveMouseMovement(int minSteps, int maxSteps) {
        this.minSteps = Math.max(4, minSteps);
        this.maxSteps = Math.max(this.minSteps, maxSteps);
    }

    @Override
    public MousePath generatePath(Point current, Point target) {
        if (current == null || target == null) {
            return new MousePath(Collections.emptyList());
        }
        int dist = Math.abs(target.x - current.x) + Math.abs(target.y - current.y);
        if (dist < 3) {
            return new MousePath(Collections.singletonList(new Point(target)));
        }

        ThreadLocalRandom r = ThreadLocalRandom.current();
        int steps = Math.min(maxSteps, Math.max(minSteps, dist / 8));

        // Control points offset perpendicular to the line
        double mx = (current.x + target.x) / 2.0;
        double my = (current.y + target.y) / 2.0;
        double dx = target.x - current.x;
        double dy = target.y - current.y;
        double len = Math.hypot(dx, dy);
        double nx = len > 0.1 ? -dy / len : 0;
        double ny = len > 0.1 ? dx / len : 1;
        double bulge = (20 + r.nextDouble() * Math.min(80, len * 0.35)) * (r.nextBoolean() ? 1 : -1);
        double c1x = current.x + dx * 0.3 + nx * bulge * 0.6;
        double c1y = current.y + dy * 0.3 + ny * bulge * 0.6;
        double c2x = current.x + dx * 0.7 + nx * bulge;
        double c2y = current.y + dy * 0.7 + ny * bulge;

        List<Point> out = new ArrayList<>(steps);
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            // Cubic bezier
            double u = 1 - t;
            double x = u * u * u * current.x
                    + 3 * u * u * t * c1x
                    + 3 * u * t * t * c2x
                    + t * t * t * target.x;
            double y = u * u * u * current.y
                    + 3 * u * u * t * c1y
                    + 3 * u * t * t * c2y
                    + t * t * t * target.y;
            // tiny jitter
            int jx = (int) Math.round(x + r.nextDouble(-1.2, 1.2));
            int jy = (int) Math.round(y + r.nextDouble(-1.2, 1.2));
            out.add(new Point(jx, jy));
        }
        out.set(out.size() - 1, new Point(target));
        return new MousePath(out);
    }
}
