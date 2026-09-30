package com.lonebot.example.imps;

import net.storm.api.magic.SpellBook;

/**
 * CombatBot-compatible Imps enums for LoneBot.
 */
public final class ImpsTypes {

    private ImpsTypes() {
    }

    public enum ImpsCombatStyle {
        MELEE, RANGED, MAGE
    }

    /**
     * Ground-loot Take-strategie.
     * {@code AUTO} = roteren / laatst-betrouwbare; overige = die methode (evt. met fallbacks).
     */
    public enum LootPickupMode {
        AUTO,
        STORM_PICKUP,
        INTERACT_TAKE,
        MENU_INVOKE,
        MOUSE_LEFT,
        RIGHT_CLICK_TAKE
    }

    public enum ImpsMageSpell {
        WIND_STRIKE("Wind Strike", "Air rune", "Mind rune", "Staff of air", 1, false, SpellBook.Standard.WIND_STRIKE),
        WATER_STRIKE("Water Strike", "Water rune", "Mind rune", "Staff of water", 5, true, SpellBook.Standard.WATER_STRIKE),
        EARTH_STRIKE("Earth Strike", "Earth rune", "Mind rune", "Staff of earth", 9, true, SpellBook.Standard.EARTH_STRIKE),
        FIRE_STRIKE("Fire Strike", "Fire rune", "Mind rune", "Staff of fire", 13, true, SpellBook.Standard.FIRE_STRIKE),
        WIND_BOLT("Wind Bolt", "Air rune", "Chaos rune", "Staff of air", 17, false, SpellBook.Standard.WIND_BOLT),
        WATER_BOLT("Water Bolt", "Water rune", "Chaos rune", "Staff of water", 23, true, SpellBook.Standard.WATER_BOLT),
        EARTH_BOLT("Earth Bolt", "Earth rune", "Chaos rune", "Staff of earth", 29, true, SpellBook.Standard.EARTH_BOLT),
        FIRE_BOLT("Fire Bolt", "Fire rune", "Chaos rune", "Staff of fire", 35, true, SpellBook.Standard.FIRE_BOLT);

        private final String spellName;
        private final String elementalRune;
        private final String catalystRune;
        private final String preferredStaff;
        private final int levelReq;
        private final boolean needsAirRune;
        private final SpellBook.Standard standardSpell;

        ImpsMageSpell(String spellName, String elementalRune, String catalystRune, String preferredStaff,
                      int levelReq, boolean needsAirRune, SpellBook.Standard standardSpell) {
            this.spellName = spellName;
            this.elementalRune = elementalRune;
            this.catalystRune = catalystRune;
            this.preferredStaff = preferredStaff;
            this.levelReq = levelReq;
            this.needsAirRune = needsAirRune;
            this.standardSpell = standardSpell;
        }

        public String getSpellName() {
            return spellName;
        }

        public String getElementalRune() {
            return elementalRune;
        }

        public String getCatalystRune() {
            return catalystRune;
        }

        public String getPreferredStaff() {
            return preferredStaff;
        }

        public int getLevelReq() {
            return levelReq;
        }

        public boolean needsAirRune() {
            return needsAirRune;
        }

        public SpellBook.Standard getStandardSpell() {
            return standardSpell;
        }
    }
}
