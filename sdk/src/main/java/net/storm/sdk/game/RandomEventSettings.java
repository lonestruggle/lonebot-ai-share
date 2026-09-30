package net.storm.sdk.game;

/**
 * Runtime toggles for {@link RandomEventHandler} (panel/config → hier).
 */
public final class RandomEventSettings {

    /** Master: dismiss / genie / lamp. */
    public static volatile boolean enabled = true;

    /**
     * Genie lamp skill key (Attack, Strength, …) of {@code NONE} = Genie dismissen i.p.v. Talk-to.
     */
    public static volatile String genieLampSkill = "NONE";

    private RandomEventSettings() {
    }

    public static void apply(boolean on, String lampSkill) {
        enabled = on;
        genieLampSkill = lampSkill != null && !lampSkill.isEmpty() ? lampSkill : "NONE";
    }
}
