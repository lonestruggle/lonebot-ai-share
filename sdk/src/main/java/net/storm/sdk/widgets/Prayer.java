package net.storm.sdk.widgets;

import net.storm.api.interact.InteractMethod;
import net.storm.api.widgets.IPrayers;

import java.util.List;

/**
 * Storm package location for prayer helpers — delegates to {@link net.storm.sdk.prayer.Prayer}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IPrayers.html">Storm IPrayers</a>
 */
public final class Prayer {

    public static final IPrayers API = new Api();

    static {
        net.storm.api.Static.bindPrayers(API);
    }

    private Prayer() {
    }

    public static boolean isActive(net.runelite.api.Prayer prayer) {
        return net.storm.sdk.prayer.Prayer.isActive(prayer);
    }

    public static boolean isActive(String prayerName) {
        return net.storm.sdk.prayer.Prayer.isActive(prayerName);
    }

    public static boolean toggle(net.runelite.api.Prayer prayer) {
        return net.storm.sdk.prayer.Prayer.toggle(prayer);
    }

    public static boolean toggle(String prayerName) {
        return net.storm.sdk.prayer.Prayer.toggle(prayerName);
    }

    public static boolean setEnabled(net.runelite.api.Prayer prayer, boolean enabled) {
        return net.storm.sdk.prayer.Prayer.setEnabled(prayer, enabled);
    }

    public static boolean isQuickPrayerActive() {
        return net.storm.sdk.prayer.Prayer.isQuickPrayerActive();
    }

    public static boolean toggleQuickPrayer() {
        return net.storm.sdk.prayer.Prayer.toggleQuickPrayer();
    }

    public static boolean flushPray() {
        return net.storm.sdk.prayer.Prayer.flushPray();
    }

    private static final class Api implements IPrayers {
        @Override
        public int getMissingPoints() {
            return net.storm.sdk.prayer.Prayer.getMissingPoints();
        }

        @Override
        public boolean isEnabled(net.runelite.api.Prayer prayer) {
            return net.storm.sdk.prayer.Prayer.isEnabled(prayer);
        }

        @Override
        public void toggle(InteractMethod interactMethod, net.runelite.api.Prayer prayer) {
            net.storm.sdk.prayer.Prayer.toggle(interactMethod, prayer);
        }

        @Override
        public int getPoints() {
            return net.storm.sdk.prayer.Prayer.getPoints();
        }

        @Override
        public void toggleQuickPrayer(InteractMethod interactMethod, boolean enabled) {
            net.storm.sdk.prayer.Prayer.toggleQuickPrayer(interactMethod, enabled);
        }

        @Override
        public void toggleQuickPrayer(InteractMethod interactMethod) {
            net.storm.sdk.prayer.Prayer.toggleQuickPrayer(interactMethod);
        }

        @Override
        public boolean isQuickPrayerEnabled() {
            return net.storm.sdk.prayer.Prayer.isQuickPrayerEnabled();
        }

        @Override
        public boolean anyActive() {
            return net.storm.sdk.prayer.Prayer.anyActive();
        }

        @Override
        public void disableAll(InteractMethod interactMethod) {
            net.storm.sdk.prayer.Prayer.disableAll(interactMethod);
        }

        @Override
        public boolean canUse(net.runelite.api.Prayer prayer) {
            return net.storm.sdk.prayer.Prayer.canUse(prayer);
        }

        @Override
        public net.runelite.api.Prayer getBestRangeOffensive() {
            return net.storm.sdk.prayer.Prayer.getBestRangeOffensive();
        }

        @Override
        public net.runelite.api.Prayer getBestMageOffensive() {
            return net.storm.sdk.prayer.Prayer.getBestMageOffensive();
        }

        @Override
        public net.runelite.api.Prayer getBestMeleeOffensive() {
            return net.storm.sdk.prayer.Prayer.getBestMeleeOffensive();
        }

        @Override
        public boolean isQuickPrayerOpen() {
            return net.storm.sdk.prayer.Prayer.isQuickPrayerOpen();
        }

        @Override
        public boolean openQuickPrayer() {
            return net.storm.sdk.prayer.Prayer.openQuickPrayer();
        }

        @Override
        public List<net.runelite.api.Prayer> getSelectedQuickPrayers() {
            return net.storm.sdk.prayer.Prayer.getSelectedQuickPrayers();
        }

        @Override
        public List<net.runelite.api.Prayer> getActiveQuickPrayers() {
            return net.storm.sdk.prayer.Prayer.getActiveQuickPrayers();
        }

        @Override
        public boolean setQuickPrayers(List<net.runelite.api.Prayer> prayers, boolean closeInterface) {
            return net.storm.sdk.prayer.Prayer.setQuickPrayers(prayers, closeInterface);
        }
    }
}
