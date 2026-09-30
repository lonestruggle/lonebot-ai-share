package com.lonebot.example.starminer;

import net.runelite.api.coords.WorldPoint;

/**
 * One shooting-star call (Discord / JSON).
 */
public final class StarCall {

    public final int world;
    public final int tier;
    public final String locationRaw;
    public final StarLocations.Spot spot;
    public final int miners;
    public final long calledAtMs;
    public final boolean dead;
    public final String source;
    public final String raw;

    public StarCall(int world, int tier, String locationRaw, StarLocations.Spot spot,
                    int miners, long calledAtMs, boolean dead, String source, String raw) {
        this.world = world;
        this.tier = Math.max(0, Math.min(9, tier));
        this.locationRaw = locationRaw != null ? locationRaw : "";
        this.spot = spot;
        this.miners = miners;
        this.calledAtMs = calledAtMs;
        this.dead = dead;
        this.source = source != null ? source : "";
        this.raw = raw != null ? raw : "";
    }

    public WorldPoint tile() {
        return spot != null ? spot.tile : null;
    }

    public boolean usable() {
        return !dead && world > 0 && spot != null && tile() != null;
    }

    public int miningLevelRequired() {
        return StarObjects.miningLevelForTier(tier > 0 ? tier : 9);
    }

    public StarCall withTier(int newTier) {
        int t = Math.max(0, Math.min(9, newTier));
        if (t == this.tier) {
            return this;
        }
        return new StarCall(world, t, locationRaw, spot, miners, calledAtMs, dead, source, raw);
    }

    public String label() {
        String loc = spot != null ? spot.shortName : locationRaw;
        String t = tier > 0 ? "T" + tier : "T?";
        return "W" + world + " " + t + " " + loc;
    }

    /** Regel voor Star-tab: leeftijd, wereld, locatie (nieuwste eerst in de lijst). */
    public String tabLine(long nowMs) {
        String loc = spot != null ? spot.shortName : locationRaw;
        if (loc == null || loc.isBlank()) {
            loc = "?";
        }
        String t = tier > 0 ? " T" + tier : "";
        return ageLabel(nowMs) + "  W" + world + t + "  " + loc;
    }

    public String ageLabel(long nowMs) {
        long age = Math.max(0L, nowMs - calledAtMs);
        if (age < 60_000L) {
            return (age / 1000L) + "s";
        }
        return (age / 60_000L) + "m";
    }
}
