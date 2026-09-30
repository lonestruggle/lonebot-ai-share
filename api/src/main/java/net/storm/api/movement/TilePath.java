package net.storm.api.movement;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.pathfinder.model.Teleport;
import net.storm.api.movement.pathfinder.model.Transport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Ordered walkable tile path (Storm-compatible). Compute once, then {@link #walk()}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/movement/TilePath.html">Storm TilePath</a>
 */
public final class TilePath extends ArrayList<WorldPoint> implements Comparable<TilePath> {

    /**
     * Set by SDK ({@code TilePathWalker}) so {@link #walk()} can run without an api→sdk dependency.
     */
    public static volatile Predicate<TilePath> WALKER;

    /** Live player tile for {@link #getRemainingPath()} (wired by SDK). */
    public static volatile Supplier<WorldPoint> PLAYER;

    private final WorldArea destinationArea;
    private final double weight;
    private final boolean incomplete;
    private final List<Teleport> teleports = new ArrayList<>();
    private final List<Transport> transports = new ArrayList<>();
    private final Set<WorldPoint> visitedTiles = new LinkedHashSet<>();
    private WalkOptions walkOptions;

    public TilePath(boolean incomplete) {
        this.destinationArea = null;
        this.weight = Double.POSITIVE_INFINITY;
        this.incomplete = incomplete;
    }

    public TilePath(Collection<WorldPoint> worldPoints, boolean incomplete) {
        super(worldPoints != null ? worldPoints : Collections.emptyList());
        this.destinationArea = null;
        this.weight = size();
        this.incomplete = incomplete;
    }

    public TilePath(List<WorldPoint> points, WorldArea destination, double weight, boolean incomplete) {
        super(points != null ? points : Collections.emptyList());
        this.destinationArea = destination;
        this.weight = weight;
        this.incomplete = incomplete;
    }

    public static TilePath empty() {
        return new TilePath(true);
    }

    public static TilePath of(Collection<WorldPoint> points, WorldArea destination, boolean incomplete) {
        List<WorldPoint> list = points != null ? new ArrayList<>(points) : new ArrayList<>();
        return new TilePath(list, destination, list.size(), incomplete);
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public WorldPoint getDestination() {
        if (destinationArea != null) {
            return destinationArea.toWorldPoint();
        }
        return isEmpty() ? null : get(size() - 1);
    }

    public WorldArea getDestinationArea() {
        if (destinationArea != null) {
            return destinationArea;
        }
        WorldPoint last = getDestination();
        return last != null ? last.toWorldArea() : null;
    }

    public List<Teleport> getTeleports() {
        return teleports;
    }

    public List<Transport> getTransports() {
        return transports;
    }

    public Set<WorldPoint> getVisitedTiles() {
        return visitedTiles;
    }

    public WalkOptions getWalkOptions() {
        return walkOptions;
    }

    public void setWalkOptions(WalkOptions walkOptions) {
        this.walkOptions = walkOptions;
    }

    public void addTeleport(Teleport teleport) {
        if (teleport != null && !teleports.contains(teleport)) {
            teleports.add(teleport);
        }
    }

    public void addTransport(Transport transport) {
        if (transport != null && !transports.contains(transport)) {
            transports.add(transport);
        }
    }

    public void addVisitedTile(WorldPoint point) {
        if (point != null) {
            visitedTiles.add(point);
        }
    }

    /** Remaining tiles from the closest path point to the player onward. */
    public TilePath getRemainingPath() {
        WorldPoint me = PLAYER != null ? PLAYER.get() : null;
        return getRemainingPath(me);
    }

    /** Remaining tiles from the closest path point to {@code playerPos} onward. */
    public TilePath getRemainingPath(WorldPoint playerPos) {
        if (playerPos == null || isEmpty()) {
            return empty();
        }
        int nearestIdx = -1;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < size(); i++) {
            WorldPoint p = get(i);
            if (p == null) {
                continue;
            }
            int d = p.distanceTo(playerPos);
            if (d < best) {
                best = d;
                nearestIdx = i;
            }
        }
        if (nearestIdx < 0) {
            return empty();
        }
        while (nearestIdx + 1 < size()) {
            WorldPoint cur = get(nearestIdx);
            WorldPoint next = get(nearestIdx + 1);
            if (next != null && cur != null && next.distanceTo(playerPos) <= cur.distanceTo(playerPos)) {
                nearestIdx++;
            } else {
                break;
            }
        }
        TilePath rest = new TilePath(new ArrayList<>(subList(nearestIdx, size())), destinationArea, size() - nearestIdx, incomplete);
        copyMeta(rest);
        return rest;
    }

    /** Walk one step along this path (canvas or minimap). */
    public boolean walk() {
        Predicate<TilePath> w = WALKER;
        return w != null && w.test(this);
    }

    public boolean walk(boolean useTransports) {
        WalkOptions opts = walkOptions != null ? walkOptions.toBuilder().useTransports(useTransports).build()
                : WalkOptions.builder().useTransports(useTransports).build();
        return walk(opts);
    }

    public boolean walk(WalkOptions options) {
        this.walkOptions = options;
        return walk();
    }

    @Override
    public TilePath subList(int fromIndex, int toIndex) {
        TilePath part = new TilePath(new ArrayList<>(super.subList(fromIndex, toIndex)), destinationArea, weight, incomplete);
        copyMeta(part);
        return part;
    }

    private void copyMeta(TilePath into) {
        into.teleports.addAll(teleports);
        into.transports.addAll(transports);
        into.visitedTiles.addAll(visitedTiles);
        into.walkOptions = walkOptions;
    }

    @Override
    public int compareTo(TilePath other) {
        return Double.compare(this.weight, other != null ? other.weight : Double.POSITIVE_INFINITY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TilePath)) {
            return false;
        }
        return incomplete == ((TilePath) o).incomplete && super.equals(o);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Boolean.hashCode(incomplete);
    }
}
