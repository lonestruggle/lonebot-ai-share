package com.lonebot.example.gear;

/**
 * Activity → armour-beleid. Nieuwe skills hier registreren.
 */
public enum ActivityGearProfile {
    /** Karamja imps — lichte gear (geen plate). */
    IMPS(ArmourLoadout.LIGHT),
    /** Hill/Moss giants e.d. — volle F2P armour. */
    GIANTS(ArmourLoadout.FULL),
    /** Stronghold of Security (minotaurs etc.) — volle F2P armour. */
    SOH(ArmourLoadout.FULL),
    /** Default voor nieuwe combat-skills. */
    DEFAULT(ArmourLoadout.FULL);

    public final ArmourLoadout loadout;

    ActivityGearProfile(ArmourLoadout loadout) {
        this.loadout = loadout;
    }
}
