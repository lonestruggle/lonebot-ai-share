package net.storm.api.interact.mouse;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Storm-compat: bepaalt de VORM van een muispad (snelheid apart).
 */
public interface MouseMovementStrategy {

    MousePath generatePath(Point current, Point target);

    final class MousePath {
        private final List<Point> points;

        public MousePath(List<Point> points) {
            this.points = points != null
                    ? Collections.unmodifiableList(new ArrayList<>(points))
                    : Collections.emptyList();
        }

        public List<Point> getPoints() {
            return points;
        }

        public boolean isEmpty() {
            return points.isEmpty();
        }

        public Point last() {
            return points.isEmpty() ? null : points.get(points.size() - 1);
        }
    }
}
