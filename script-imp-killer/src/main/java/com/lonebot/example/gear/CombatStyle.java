package com.lonebot.example.gear;

import com.lonebot.example.imps.ImpsTypes;

/** Gedeelde combat-style voor gear (los van per-skill config-enums). */
public enum CombatStyle {
    MELEE, RANGED, MAGE;

    public static CombatStyle fromImps(ImpsTypes.ImpsCombatStyle style) {
        if (style == null) {
            return MELEE;
        }
        switch (style) {
            case RANGED:
                return RANGED;
            case MAGE:
                return MAGE;
            case MELEE:
            default:
                return MELEE;
        }
    }
}
