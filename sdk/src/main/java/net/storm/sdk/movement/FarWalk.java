package net.storm.sdk.movement;

import net.storm.sdk.bot.BotRuntime;

/**
 * Opt-in voor skill-scripts tijdens {@code farWalk} (pad-doel &gt;~12 tegels).
 * <p>
 * Standaard pauzeert {@link net.storm.sdk.loop.LoopHost} skills tijdens lange walks
 * zodat de walker vrij kan doorlinken. Scripts die mid-approach
 * {@link net.storm.sdk.interact.ClickOnSight} nodig hebben (WC→boom, Imp→NPC, …)
 * zetten dit aan <b>vóór</b> / terwijl ze naar het werkgebied lopen.
 * </p>
 * <pre>{@code
 * // Aan: naar bomen / spot — COS mag pad onderbreken
 * FarWalk.keepSkillTicking(true);
 * Movement.walkTo(center);
 *
 * // Uit: bank-trip — alleen walker
 * FarWalk.keepSkillTicking(false);
 * Movement.walkTo(bank);
 * }</pre>
 * Flag is sticky tot de volgende {@link #keepSkillTicking(boolean)} / {@link #clear()}.
 * <p>
 * <b>Star park:</b> park-Star tickt nooit via deze opt-in (blokkeerde doorlinken).
 * Alleen de <em>actieve</em> skill ({@link BotRuntime#isSkillPluginAllowed}) mag door —
 * dus Star wél als Star echt aanstaat, niet terwijl WC/Fish loopt.
 * </p>
 */
public final class FarWalk {

    private static volatile boolean keepSkillTicking;

    private FarWalk() {
    }

    /**
     * @param on {@code true} = actieve skill mag ticken tijdens farWalk (COS mid-pad);
     *           {@code false} = LoopHost pauzeert skills weer (default-gedrag).
     */
    public static void keepSkillTicking(boolean on) {
        keepSkillTicking = on;
    }

    /** Of de opt-in aan staat (ruwe flag). */
    public static boolean keepSkillTicking() {
        return keepSkillTicking;
    }

    /**
     * LoopHost: mag deze plugin tijdens farWalk door?
     * Park-Star altijd nee; verder alleen als opt-in + skill echt allowed.
     */
    public static boolean allowsPlugin(String simpleClassName) {
        if (!keepSkillTicking || simpleClassName == null) {
            return false;
        }
        // Park-watch mag travel van WC/Fish niet verstoren (0.3.606/608)
        if ("StarMinerPlugin".equals(simpleClassName)
                && BotRuntime.starParkActive
                && !BotRuntime.starMinerEnabled) {
            return false;
        }
        return BotRuntime.isSkillPluginAllowed(simpleClassName);
    }

    /** Uit — bij Stop/reset of einde skill. */
    public static void clear() {
        keepSkillTicking = false;
    }
}
