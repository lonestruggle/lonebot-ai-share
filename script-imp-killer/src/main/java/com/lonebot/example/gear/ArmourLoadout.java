package com.lonebot.example.gear;

/**
 * Welke armour-loadout een activity gebruikt.
 * <p>
 * Imps = low-level, gewicht telt → {@link #LIGHT} (geen plate).
 * Giants / SOH / toekomstige combat → {@link #FULL} (beste F2P plate/leather die je levels aankunnen).
 */
public enum ArmourLoadout {
    /** Alleen amulet/cape/gloves/boots — geen helm/body/legs/shield. */
    LIGHT,
    /** Volledige F2P style-set + accessories; altijd best wearbaar op levels. */
    FULL
}
