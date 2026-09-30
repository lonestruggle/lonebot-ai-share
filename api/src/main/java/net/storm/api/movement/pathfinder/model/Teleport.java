package net.storm.api.movement.pathfinder.model;

import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Instant teleport option for the pathfinder.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/movement/pathfinder/model/Teleport.html">Storm Teleport</a>
 */
public final class Teleport {

    private WorldPoint destination;
    private Callable<Boolean> handler;
    private Requirements requirements;
    private int[] itemRequirements;
    private int[] objectIdRequirements;
    private String[] objectNameRequirements;
    private int priority;
    private int weight = 1;
    private boolean poh;
    private boolean homeTeleport;
    private boolean minigameTeleport;
    private boolean timedTeleport;

    public static Builder builder() {
        return new Builder();
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

    public int[] getItemRequirements() {
        return itemRequirements;
    }

    public int getPriority() {
        return priority;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isPoh() {
        return poh;
    }

    public boolean isHomeTeleport() {
        return homeTeleport;
    }

    public boolean isMinigameTeleport() {
        return minigameTeleport;
    }

    public boolean isTimedTeleport() {
        return timedTeleport;
    }

    public boolean isItem() {
        return itemRequirements != null && itemRequirements.length > 0;
    }

    public boolean isObject() {
        return (objectIdRequirements != null && objectIdRequirements.length > 0)
                || (objectNameRequirements != null && objectNameRequirements.length > 0);
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Teleport)) {
            return false;
        }
        Teleport t = (Teleport) other;
        return poh == t.poh && Objects.equals(destination, t.destination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(destination, poh);
    }

    public static final class Builder {
        private final Teleport t = new Teleport();

        public Builder destination(WorldPoint destination) {
            t.destination = destination;
            return this;
        }

        public Builder action(Runnable action) {
            t.handler = () -> {
                if (action != null) {
                    action.run();
                }
                return true;
            };
            return this;
        }

        public Builder handler(Callable<Boolean> handler) {
            t.handler = handler;
            return this;
        }

        public Builder requirement(java.util.function.BooleanSupplier check) {
            if (t.requirements == null) {
                t.requirements = new Requirements();
            }
            t.requirements.require(check);
            return this;
        }

        public Builder requirements(Requirements requirements) {
            t.requirements = requirements;
            return this;
        }

        public Builder itemRequirements(int... ids) {
            t.itemRequirements = ids;
            return this;
        }

        public Builder objectIdRequirements(int... ids) {
            t.objectIdRequirements = ids;
            return this;
        }

        public Builder objectNameRequirements(String... names) {
            t.objectNameRequirements = names;
            return this;
        }

        public Builder priority(int priority) {
            t.priority = priority;
            return this;
        }

        public Builder weight(int weight) {
            t.weight = weight;
            return this;
        }

        public Builder poh(boolean poh) {
            t.poh = poh;
            return this;
        }

        public Builder homeTeleport(boolean home) {
            t.homeTeleport = home;
            t.timedTeleport = home || t.timedTeleport;
            return this;
        }

        public Builder minigameTeleport(boolean mini) {
            t.minigameTeleport = mini;
            t.timedTeleport = mini || t.timedTeleport;
            return this;
        }

        public Teleport build() {
            return t;
        }
    }

    @Override
    public String toString() {
        return "Teleport{dest=" + destination + ", items=" + Arrays.toString(itemRequirements) + "}";
    }
}
