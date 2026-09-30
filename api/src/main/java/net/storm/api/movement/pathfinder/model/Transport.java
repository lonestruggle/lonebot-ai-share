package net.storm.api.movement.pathfinder.model;

import net.runelite.api.coords.WorldPoint;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Non-instant transport (ladder, boat, door, shortcut).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/movement/pathfinder/model/Transport.html">Storm Transport</a>
 */
public final class Transport {

    public static final int DEFAULT_RADIUS = 5;

    private WorldPoint source;
    private WorldPoint destination;
    private Callable<Boolean> handler;
    private Requirements requirements;
    private int sourceRadius = DEFAULT_RADIUS;
    private int destinationRadius = DEFAULT_RADIUS;
    private int weight = 1;
    private int delayTicks;
    private String name;

    public static Builder builder() {
        return new Builder();
    }

    public WorldPoint getSource() {
        return source;
    }

    public WorldPoint getDestination() {
        return destination;
    }

    public Callable<Boolean> getHandler() {
        return handler;
    }

    public Requirements getRequirements() {
        return requirements;
    }

    public int getSourceRadius() {
        return sourceRadius;
    }

    public int getDestinationRadius() {
        return destinationRadius;
    }

    public int getWeight() {
        return weight;
    }

    public int getDelayTicks() {
        return delayTicks;
    }

    public String getName() {
        return name;
    }

    public boolean execute() {
        if (handler == null) {
            return false;
        }
        try {
            Boolean ok = handler.call();
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transport)) {
            return false;
        }
        Transport t = (Transport) o;
        return Objects.equals(source, t.source) && Objects.equals(destination, t.destination)
                && Objects.equals(name, t.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, destination, name);
    }

    public static final class Builder {
        private final Transport t = new Transport();

        public Builder source(WorldPoint source) {
            t.source = source;
            return this;
        }

        public Builder destination(WorldPoint destination) {
            t.destination = destination;
            return this;
        }

        public Builder handler(Callable<Boolean> handler) {
            t.handler = handler;
            return this;
        }

        public Builder requirements(Requirements requirements) {
            t.requirements = requirements;
            return this;
        }

        public Builder sourceRadius(int radius) {
            t.sourceRadius = Math.max(0, radius);
            return this;
        }

        public Builder destinationRadius(int radius) {
            t.destinationRadius = Math.max(0, radius);
            return this;
        }

        public Builder radius(int radius) {
            return sourceRadius(radius).destinationRadius(radius);
        }

        public Builder weight(int weight) {
            t.weight = weight;
            return this;
        }

        public Builder delayTicks(int delayTicks) {
            t.delayTicks = delayTicks;
            return this;
        }

        public Builder name(String name) {
            t.name = name;
            return this;
        }

        public Transport build() {
            return t;
        }
    }

    @Override
    public String toString() {
        return "Transport{" + name + " " + source + " → " + destination + "}";
    }
}
