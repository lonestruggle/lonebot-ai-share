package com.lonebot.example.starminer;

/**
 * Testbare travel-modes voor Star Miner (naar ster lopen).
 * Kies via config / panel — één tegelijk.
 */
public enum StarTravelMode {
    WALK_ONLY(1, "1 Walk-only (steden-cirkel)"),
    PRE_TICK(2, "2 Walk vóór andere logica"),
    BACKGROUND(3, "3 Background = Imp-walk (elke tick)"),
    IMP_CLONE(4, "4 ImpWalk 1:1"),
    FATAL_ONLY(5, "5 Fatale-only + Mine≤30"),
    INTERLEAVE_3_1(6, "6 Interleave 3×walk / 1×other"),
    FORCE_IF_MOVING(7, "7 Force walk als moving"),
    SHORT_HOPS(8, "8 API-walker (zelfde hops)"),
    LARGE_HOPS(9, "9 API-walker (zelfde hops)"),
    INTERACT_THEN_WALK(10, "10 Mine-on-sight anders walk"),
    WORLD_WALKER(11, "11 WorldWalker tot ≤30");

    public final int number;
    public final String label;

    StarTravelMode(int number, String label) {
        this.number = number;
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static StarTravelMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return WALK_ONLY;
        }
        String t = raw.trim();
        for (StarTravelMode m : values()) {
            if (m.name().equalsIgnoreCase(t) || m.label.equalsIgnoreCase(t)) {
                return m;
            }
            if (t.equals(String.valueOf(m.number)) || t.startsWith(m.number + " ")) {
                return m;
            }
        }
        return WALK_ONLY;
    }
}
