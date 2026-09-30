package net.storm.api.widgets;

import net.runelite.api.Prayer;
import net.storm.api.interact.InteractMethod;

import java.util.List;

/**
 * Storm {@code IPrayers}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IPrayers.html">Storm IPrayers</a>
 */
public interface IPrayers {

    int getMissingPoints();

    boolean isEnabled(Prayer prayer);

    void toggle(InteractMethod interactMethod, Prayer prayer);

    default void toggle(Prayer prayer) {
        toggle(null, prayer);
    }

    int getPoints();

    void toggleQuickPrayer(InteractMethod interactMethod, boolean enabled);

    void toggleQuickPrayer(InteractMethod interactMethod);

    default void toggleQuickPrayer(boolean enabled) {
        toggleQuickPrayer(null, enabled);
    }

    default void toggleQuickPrayer() {
        toggleQuickPrayer((InteractMethod) null);
    }

    boolean isQuickPrayerEnabled();

    boolean anyActive();

    void disableAll(InteractMethod interactMethod);

    default void disableAll() {
        disableAll(null);
    }

    boolean canUse(Prayer prayer);

    Prayer getBestRangeOffensive();

    Prayer getBestMageOffensive();

    Prayer getBestMeleeOffensive();

    boolean isQuickPrayerOpen();

    boolean openQuickPrayer();

    List<Prayer> getSelectedQuickPrayers();

    List<Prayer> getActiveQuickPrayers();

    boolean setQuickPrayers(List<Prayer> prayers, boolean closeInterface);

    default boolean setQuickPrayers(List<Prayer> prayers) {
        return setQuickPrayers(prayers, true);
    }

    default boolean isQuickPrayerSelected(Prayer prayer) {
        return prayer != null && getSelectedQuickPrayers().contains(prayer);
    }

    default boolean isQuickPrayerSelected(List<Prayer> prayers) {
        if (prayers == null || prayers.isEmpty()) {
            return true;
        }
        List<Prayer> selected = getSelectedQuickPrayers();
        return selected.containsAll(prayers);
    }

    default boolean isQuickPrayerActive(Prayer prayer) {
        return isQuickPrayerEnabled() && isEnabled(prayer);
    }

    default boolean isQuickPrayerActive(List<Prayer> prayers) {
        if (!isQuickPrayerEnabled() || prayers == null) {
            return false;
        }
        for (Prayer p : prayers) {
            if (!isEnabled(p)) {
                return false;
            }
        }
        return true;
    }
}
