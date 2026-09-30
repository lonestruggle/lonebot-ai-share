package com.lonebot.example.giants;

import com.lonebot.example.gear.CombatStyle;
import com.lonebot.example.imps.ImpsTypes;
import net.storm.api.magic.SpellBook;

/**
 * Giants combat enums. Style/spell names match Imp Killer so gear helpers stay shared.
 */
public final class GiantsTypes {

    private GiantsTypes() {
    }

    public enum GiantsCombatStyle {
        MELEE, RANGED, MAGE;

        public CombatStyle toGear() {
            switch (this) {
                case RANGED:
                    return CombatStyle.RANGED;
                case MAGE:
                    return CombatStyle.MAGE;
                case MELEE:
                default:
                    return CombatStyle.MELEE;
            }
        }

        public ImpsTypes.ImpsCombatStyle toImps() {
            switch (this) {
                case RANGED:
                    return ImpsTypes.ImpsCombatStyle.RANGED;
                case MAGE:
                    return ImpsTypes.ImpsCombatStyle.MAGE;
                case MELEE:
                default:
                    return ImpsTypes.ImpsCombatStyle.MELEE;
            }
        }

        public static GiantsCombatStyle from(Object raw) {
            if (raw == null) {
                return MELEE;
            }
            String n = raw instanceof Enum ? ((Enum<?>) raw).name() : String.valueOf(raw);
            if (n == null) {
                return MELEE;
            }
            switch (n.trim().toUpperCase()) {
                case "RANGED":
                    return RANGED;
                case "MAGE":
                case "MAGIC":
                    return MAGE;
                default:
                    return MELEE;
            }
        }
    }

    public enum GiantsMageSpell {
        WIND_STRIKE(ImpsTypes.ImpsMageSpell.WIND_STRIKE),
        WATER_STRIKE(ImpsTypes.ImpsMageSpell.WATER_STRIKE),
        EARTH_STRIKE(ImpsTypes.ImpsMageSpell.EARTH_STRIKE),
        FIRE_STRIKE(ImpsTypes.ImpsMageSpell.FIRE_STRIKE),
        WIND_BOLT(ImpsTypes.ImpsMageSpell.WIND_BOLT),
        WATER_BOLT(ImpsTypes.ImpsMageSpell.WATER_BOLT),
        EARTH_BOLT(ImpsTypes.ImpsMageSpell.EARTH_BOLT),
        FIRE_BOLT(ImpsTypes.ImpsMageSpell.FIRE_BOLT);

        private final ImpsTypes.ImpsMageSpell imps;

        GiantsMageSpell(ImpsTypes.ImpsMageSpell imps) {
            this.imps = imps;
        }

        public ImpsTypes.ImpsMageSpell toImps() {
            return imps;
        }

        public String getSpellName() {
            return imps.getSpellName();
        }

        public SpellBook.Standard getStandardSpell() {
            return imps.getStandardSpell();
        }

        public static GiantsMageSpell from(Object raw) {
            if (raw == null) {
                return WIND_STRIKE;
            }
            String n = raw instanceof Enum ? ((Enum<?>) raw).name() : String.valueOf(raw);
            if (n == null) {
                return WIND_STRIKE;
            }
            try {
                return valueOf(n.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return WIND_STRIKE;
            }
        }
    }
}
