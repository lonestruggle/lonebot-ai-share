package net.storm.api.movement;

import net.storm.api.movement.pathfinder.CollisionMap;
import net.storm.api.movement.pathfinder.model.Requirements;

/**
 * Pathfinding / walk execution options (Storm {@code WalkOptions}).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/movement/WalkOptions.html">Storm WalkOptions</a>
 */
public final class WalkOptions {

    private boolean useTransports = true;
    private boolean useTeleports = true;
    private boolean useHomeTeleports;
    private boolean useMinigameTeleports;
    private boolean usePoh;
    private boolean useCharterShips = true;
    private boolean useGnomeGliders = true;
    private boolean useMagicCarpets = true;
    private boolean avoidWilderness;
    private boolean useCache = true;
    private boolean toggleRun;
    private boolean forceLoad;
    private CollisionMap collisionMap;
    private Requirements requirements;

    public WalkOptions() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public boolean isUseTransports() {
        return useTransports;
    }

    public void setUseTransports(boolean useTransports) {
        this.useTransports = useTransports;
    }

    public boolean isUseTeleports() {
        return useTeleports;
    }

    public void setUseTeleports(boolean useTeleports) {
        this.useTeleports = useTeleports;
    }

    public boolean isUseHomeTeleports() {
        return useHomeTeleports;
    }

    public void setUseHomeTeleports(boolean useHomeTeleports) {
        this.useHomeTeleports = useHomeTeleports;
    }

    public boolean isUseMinigameTeleports() {
        return useMinigameTeleports;
    }

    public void setUseMinigameTeleports(boolean useMinigameTeleports) {
        this.useMinigameTeleports = useMinigameTeleports;
    }

    public boolean isUsePoh() {
        return usePoh;
    }

    public void setUsePoh(boolean usePoh) {
        this.usePoh = usePoh;
    }

    public boolean isUseCharterShips() {
        return useCharterShips;
    }

    public void setUseCharterShips(boolean useCharterShips) {
        this.useCharterShips = useCharterShips;
    }

    public boolean isUseGnomeGliders() {
        return useGnomeGliders;
    }

    public void setUseGnomeGliders(boolean useGnomeGliders) {
        this.useGnomeGliders = useGnomeGliders;
    }

    public boolean isUseMagicCarpets() {
        return useMagicCarpets;
    }

    public void setUseMagicCarpets(boolean useMagicCarpets) {
        this.useMagicCarpets = useMagicCarpets;
    }

    public boolean isAvoidWilderness() {
        return avoidWilderness;
    }

    public void setAvoidWilderness(boolean avoidWilderness) {
        this.avoidWilderness = avoidWilderness;
    }

    public boolean isUseCache() {
        return useCache;
    }

    public void setUseCache(boolean useCache) {
        this.useCache = useCache;
    }

    public boolean isToggleRun() {
        return toggleRun;
    }

    public void setToggleRun(boolean toggleRun) {
        this.toggleRun = toggleRun;
    }

    public boolean isForceLoad() {
        return forceLoad;
    }

    public void setForceLoad(boolean forceLoad) {
        this.forceLoad = forceLoad;
    }

    public CollisionMap getCollisionMap() {
        return collisionMap;
    }

    public void setCollisionMap(CollisionMap collisionMap) {
        this.collisionMap = collisionMap;
    }

    public Requirements getRequirements() {
        return requirements;
    }

    public void setRequirements(Requirements requirements) {
        this.requirements = requirements;
    }

    public static final class Builder {
        private final WalkOptions o;

        public Builder() {
            this.o = new WalkOptions();
        }

        private Builder(WalkOptions src) {
            this.o = new WalkOptions();
            o.useTransports = src.useTransports;
            o.useTeleports = src.useTeleports;
            o.useHomeTeleports = src.useHomeTeleports;
            o.useMinigameTeleports = src.useMinigameTeleports;
            o.usePoh = src.usePoh;
            o.useCharterShips = src.useCharterShips;
            o.useGnomeGliders = src.useGnomeGliders;
            o.useMagicCarpets = src.useMagicCarpets;
            o.avoidWilderness = src.avoidWilderness;
            o.useCache = src.useCache;
            o.toggleRun = src.toggleRun;
            o.forceLoad = src.forceLoad;
            o.collisionMap = src.collisionMap;
            o.requirements = src.requirements;
        }

        public Builder useTransports(boolean v) {
            o.useTransports = v;
            return this;
        }

        public Builder useTeleports(boolean v) {
            o.useTeleports = v;
            return this;
        }

        public Builder useHomeTeleports(boolean v) {
            o.useHomeTeleports = v;
            return this;
        }

        public Builder useMinigameTeleports(boolean v) {
            o.useMinigameTeleports = v;
            return this;
        }

        public Builder usePoh(boolean v) {
            o.usePoh = v;
            return this;
        }

        public Builder useCharterShips(boolean v) {
            o.useCharterShips = v;
            return this;
        }

        public Builder useGnomeGliders(boolean v) {
            o.useGnomeGliders = v;
            return this;
        }

        public Builder useMagicCarpets(boolean v) {
            o.useMagicCarpets = v;
            return this;
        }

        public Builder avoidWilderness(boolean v) {
            o.avoidWilderness = v;
            return this;
        }

        public Builder useCache(boolean v) {
            o.useCache = v;
            return this;
        }

        public Builder toggleRun(boolean v) {
            o.toggleRun = v;
            return this;
        }

        public Builder forceLoad(boolean v) {
            o.forceLoad = v;
            return this;
        }

        public Builder collisionMap(CollisionMap map) {
            o.collisionMap = map;
            return this;
        }

        public Builder requirements(Requirements requirements) {
            o.requirements = requirements;
            return this;
        }

        public WalkOptions build() {
            return o;
        }
    }
}
