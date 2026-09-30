package net.storm.api.magic;

/**
 * Minimal Storm-compat spellbook enums for Imp Killer mage + teleports.
 * {@code widgetId} = RuneLite {@code InterfaceID.MagicSpellbook.*} packed id (group 218).
 */
public final class SpellBook {

    private SpellBook() {
    }

    public enum Standard {
        HOME_TELEPORT("Home Teleport", 0, 14286854),
        WIND_STRIKE("Wind Strike", 1, 14286859),
        WATER_STRIKE("Water Strike", 5, 14286862),
        EARTH_STRIKE("Earth Strike", 9, 14286865),
        FIRE_STRIKE("Fire Strike", 13, 14286867),
        WIND_BOLT("Wind Bolt", 17, 14286869),
        WATER_BOLT("Water Bolt", 23, 14286873),
        EARTH_BOLT("Earth Bolt", 29, 14286876),
        FIRE_BOLT("Fire Bolt", 35, 14286879),
        VARROCK_TELEPORT("Varrock Teleport", 25, 14286874),
        LUMBRIDGE_TELEPORT("Lumbridge Teleport", 31, 14286877),
        FALADOR_TELEPORT("Falador Teleport", 37, 14286880);

        private final String spellName;
        private final int levelReq;
        /** Packed widget id (218 << 16 | child). */
        private final int widgetId;

        Standard(String spellName, int levelReq, int widgetId) {
            this.spellName = spellName;
            this.levelReq = levelReq;
            this.widgetId = widgetId;
        }

        public String getName() {
            return spellName;
        }

        public int getLevel() {
            return levelReq;
        }

        public int getWidgetId() {
            return widgetId;
        }

        public int getWidgetGroup() {
            return widgetId >>> 16;
        }

        public int getWidgetChild() {
            return widgetId & 0xFFFF;
        }
    }
}
