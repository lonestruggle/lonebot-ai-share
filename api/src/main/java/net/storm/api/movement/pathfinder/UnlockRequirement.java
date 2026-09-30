package net.storm.api.movement.pathfinder;

/**
 * Unlock flag for a teleport / transport (quest, diary, cloak, …).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/pathfinder/ChargeManager.html">Storm ChargeManager</a>
 */
public final class UnlockRequirement {

    public static final UnlockRequirement ARDOUGNE_CLOAK = of("Ardougne cloak");
    public static final UnlockRequirement EXPLORERS_RING = of("Explorer's ring");
    public static final UnlockRequirement KARAMJA_GLOVES = of("Karamja gloves");
    public static final UnlockRequirement FALADOR_SHIELD = of("Falador shield");
    public static final UnlockRequirement VARROCK_ARMOUR = of("Varrock armour");
    public static final UnlockRequirement WILDERNESS_SWORD = of("Wilderness sword");
    public static final UnlockRequirement MONKEY_MADNESS = of("Monkey Madness I");

    private final String name;
    private final String questName;

    public UnlockRequirement(String name) {
        this(name, null);
    }

    public UnlockRequirement(String name, String questName) {
        this.name = name != null ? name : "";
        this.questName = questName != null ? questName : this.name;
    }

    public static UnlockRequirement of(String name) {
        return new UnlockRequirement(name, null);
    }

    public static UnlockRequirement of(String name, String questName) {
        return new UnlockRequirement(name, questName);
    }

    public String getName() {
        return name;
    }

    public String getQuestName() {
        return questName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UnlockRequirement)) {
            return false;
        }
        return name.equalsIgnoreCase(((UnlockRequirement) o).name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }
}
