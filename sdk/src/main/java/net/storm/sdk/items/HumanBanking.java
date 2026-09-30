package net.storm.sdk.items;

import net.storm.sdk.commons.Rand;
import net.storm.sdk.utils.Sleep;

/**
 * Zelfde pacing voor elke bank-sessie: deposit en withdraw even snel.
 */
public final class HumanBanking {

    private HumanBanking() {
    }

    /** Na één deposit- of withdraw-klik ( Imp / Fish / WC / Star / BankSession ). */
    public static final int ACTION_MIN_MS = 180;
    public static final int ACTION_MAX_MS = 320;

    public static final int EQUIP_MIN_MS = 200;
    public static final int EQUIP_MAX_MS = 360;

    public static final int UI_SETTLE_MIN_MS = 160;
    public static final int UI_SETTLE_MAX_MS = 280;

    public static final int OPEN_RETRY_MIN_MS = 320;
    public static final int OPEN_RETRY_MAX_MS = 520;

    public static final int CLOSE_MIN_MS = 220;
    public static final int CLOSE_MAX_MS = 380;

    public static int afterActionMs() {
        return Rand.nextInt(ACTION_MIN_MS, ACTION_MAX_MS);
    }

    public static int afterEquipMs() {
        return Rand.nextInt(EQUIP_MIN_MS, EQUIP_MAX_MS);
    }

    public static int uiSettleMs() {
        return Rand.nextInt(UI_SETTLE_MIN_MS, UI_SETTLE_MAX_MS);
    }

    public static int openRetryMs() {
        return Rand.nextInt(OPEN_RETRY_MIN_MS, OPEN_RETRY_MAX_MS);
    }

    public static int closeMs() {
        return Rand.nextInt(CLOSE_MIN_MS, CLOSE_MAX_MS);
    }

    /** Tussen stacks in dezelfde tick (burst). */
    public static void pauseBetweenStacks() {
        Sleep.sleep(40, 90);
    }

    /** Between deposit/withdraw clicks. */
    public static void pauseBetweenActions() {
        pauseBetweenStacks();
    }

    /** Before {@link Bank#close()}. */
    public static void pauseBeforeClose() {
        Sleep.sleep(90, 200);
    }

    /** After bank closes. */
    public static void pauseAfterClose() {
        Sleep.sleep(60, 150);
    }

    /** Before booth/banker open click. */
    public static void pauseBeforeBankOpenClick() {
        Sleep.sleep(70, 180);
    }
}
