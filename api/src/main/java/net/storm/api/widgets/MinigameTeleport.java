package net.storm.api.widgets;

/**
 * Grouping / minigame teleport destinations.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/MinigameTeleport.html">Storm MinigameTeleport</a>
 */
public enum MinigameTeleport {
    BARBARIAN_ASSAULT("Barbarian Assault", true, true),
    BLAST_FURNACE("Blast Furnace", true, true),
    BURTHORPE_GAMES_ROOM("Burthorpe Games Room", true, false),
    CASTLE_WARS("Castle Wars", true, false),
    CLAN_WARS("Clan Wars", true, false),
    DAGANNOTH_KINGS("Dagannoth Kings", true, true),
    FISHING_TRAWLER("Fishing Trawler", true, true),
    GIANTS_FOUNDRY("Giants' Foundry", true, true),
    GOD_WARS("God Wars", true, true),
    GUARDIANS_OF_THE_RIFT("Guardians of the Rift", true, true),
    LAST_MAN_STANDING("Last Man Standing", true, false),
    MAGE_TRAINING_ARENA("Mage Training Arena", true, true),
    NIGHTMARE_ZONE("Nightmare Zone", true, true),
    PEST_CONTROL("Pest Control", true, true),
    PLAYER_OWNED_HOUSES("Player Owned Houses", false, true),
    RAT_PITS_ARDOUGNE("Rat Pits", true, true),
    RAT_PITS_VARROCK("Rat Pits", true, true),
    RAT_PITS_KELDAGRIM("Rat Pits", true, true),
    RAT_PITS_PORT_SARIM("Rat Pits", true, true),
    ROYAL_TITANS("Royal Titans", true, true),
    SHADES_OF_MORTTON("Shades of Mort'ton", true, true),
    SHIELD_OF_ARRAV("Shield of Arrav", false, false),
    SHOOTING_STARS("Shooting Stars", true, true),
    SOUL_WARS("Soul Wars", true, true),
    THEATRE_OF_BLOOD("Theatre of Blood", true, true),
    TITHE_FARM("Tithe Farm", true, true),
    TOMBS_OF_AMASCUT("Tombs of Amascut", true, true),
    TROUBLE_BREWING("Trouble Brewing", true, true),
    TZHAAR_FIGHT_PIT("TzHaar Fight Pit", true, true),
    VOLCANIC_MINE("Volcanic Mine", true, true),
    NONE("", false, false);

    private final String displayName;
    private final boolean hasDestination;
    private final boolean members;

    MinigameTeleport(String displayName, boolean hasDestination, boolean members) {
        this.displayName = displayName;
        this.hasDestination = hasDestination;
        this.members = members;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isMembers() {
        return members;
    }

    public boolean hasDestination() {
        return hasDestination;
    }

    public boolean canUse() {
        return Access.canUse != null && Access.canUse.test(this);
    }

    public static MinigameTeleport getCurrent() {
        return Access.current != null ? Access.current.get() : NONE;
    }

    public static MinigameTeleport byName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        String want = name.trim();
        for (MinigameTeleport t : values()) {
            if (t == NONE) {
                continue;
            }
            if (t.name().equalsIgnoreCase(want) || t.displayName.equalsIgnoreCase(want)) {
                return t;
            }
        }
        String lower = want.toLowerCase();
        for (MinigameTeleport t : values()) {
            if (t != NONE && t.displayName.toLowerCase().contains(lower)) {
                return t;
            }
        }
        return NONE;
    }

    public static final class Access {
        private static CanUseFn canUse;
        private static CurrentFn current;

        private Access() {
        }

        public static void bind(CanUseFn use, CurrentFn cur) {
            canUse = use;
            current = cur;
        }

        @FunctionalInterface
        public interface CanUseFn {
            boolean test(MinigameTeleport dest);
        }

        @FunctionalInterface
        public interface CurrentFn {
            MinigameTeleport get();
        }
    }
}
