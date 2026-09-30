package net.storm.sdk.game;

import net.runelite.api.Client;
import net.runelite.api.Player;

/**
 * Speler-animaties — dunne wrapper om {@link AnimationIds} (RuneLite dump) + live local read.
 * <p>
 * Volledige named lijst: {@link AnimationIds}; JSON/txt: {@code docs/animation-ids-runelite.*}.
 */
public final class Animations {

    public static final int NONE = AnimationIds.IDLE;
    public static final int FIREMAKING = AnimationIds.FIREMAKING;
    public static final int BONFIRE_OR_FORESTER_LOW = AnimationIds.FIREMAKING_FORESTERS_CAMPFIRE_ARCTIC_PINE;
    public static final int BONFIRE_OR_FORESTER_HIGH = AnimationIds.FIREMAKING_FORESTERS_CAMPFIRE_YEW;

    private Animations() {
    }

    /** Huidige primary animation van de local player (−1 = idle/geen). */
    public static int ofLocal() {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return NONE;
            }
            Player p = c.getLocalPlayer();
            return p != null ? p.getAnimation() : NONE;
        }, NONE);
        return v != null ? v : NONE;
    }

    public static boolean isAnimating() {
        return ofLocal() != NONE;
    }

    public static boolean isFiremaking(int animId) {
        return AnimationIds.isFiremaking(animId);
    }

    public static boolean isBonfireOrForester(int animId) {
        return AnimationIds.isBonfireOrForester(animId);
    }

    public static boolean isFiremakingRelated(int animId) {
        return AnimationIds.isFiremakingRelated(animId);
    }

    public static boolean isWoodcutting(int animId) {
        return AnimationIds.isWoodcutting(animId);
    }

    public static boolean localIsWoodcutting() {
        return isWoodcutting(ofLocal());
    }

    public static boolean localIsFiremakingRelated() {
        return isFiremakingRelated(ofLocal());
    }

    /** Named label: {@code FIREMAKING_FORESTERS_CAMPFIRE_WILLOW(10572)} of {@code UNKNOWN(n)}. */
    public static String describe(int animId) {
        return AnimationIds.describe(animId);
    }

    public static String describeLocal() {
        return describe(ofLocal());
    }

    /** Aantal named IDs in de RuneLite-dump. */
    public static int knownCount() {
        return AnimationIds.size();
    }
}
