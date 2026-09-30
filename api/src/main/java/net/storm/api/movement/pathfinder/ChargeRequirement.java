package net.storm.api.movement.pathfinder;

/**
 * Charge tracking for teleport jewelry / limited-use transports.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/pathfinder/ChargeManager.html">Storm ChargeManager</a>
 */
public final class ChargeRequirement {

    public static final ChargeRequirement RING_OF_DUELING = of("Ring of dueling", 8);
    public static final ChargeRequirement GAMES_NECKLACE = of("Games necklace", 8);
    public static final ChargeRequirement RING_OF_WEALTH = of("Ring of wealth", 5);
    public static final ChargeRequirement AMULET_OF_GLORY = of("Amulet of glory", 6);
    public static final ChargeRequirement COMBAT_BRACELET = of("Combat bracelet", 6);
    public static final ChargeRequirement SKILLS_NECKLACE = of("Skills necklace", 6);
    public static final ChargeRequirement BURNING_AMULET = of("Burning amulet", 5);
    public static final ChargeRequirement NECKLACE_OF_PASSAGE = of("Necklace of passage", 5);
    public static final ChargeRequirement SLAYER_RING = of("Slayer ring", 8);

    private final String name;
    private final int maxCharges;

    public ChargeRequirement(String name, int maxCharges) {
        this.name = name != null ? name : "";
        this.maxCharges = Math.max(0, maxCharges);
    }

    public static ChargeRequirement of(String name, int maxCharges) {
        return new ChargeRequirement(name, maxCharges);
    }

    public String getName() {
        return name;
    }

    public int getMaxCharges() {
        return maxCharges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChargeRequirement)) {
            return false;
        }
        return name.equalsIgnoreCase(((ChargeRequirement) o).name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return name + "(max " + maxCharges + ")";
    }
}
