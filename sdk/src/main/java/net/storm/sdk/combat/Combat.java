package net.storm.sdk.combat;

/**
 * @deprecated Use {@link net.storm.sdk.game.Combat} (Storm package path).
 */
@Deprecated
public final class Combat {

    private Combat() {
    }

    public static boolean isInCombat() {
        return net.storm.sdk.game.Combat.isInCombat();
    }

    public static int getHealthPercent() {
        return (int) Math.round(net.storm.sdk.game.Combat.getHealthPercent());
    }
}
