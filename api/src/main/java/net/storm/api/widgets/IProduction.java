package net.storm.api.widgets;

import java.util.function.Predicate;

/**
 * Storm {@code IProduction}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IProduction.html">Storm IProduction</a>
 */
public interface IProduction {

    boolean isOpen();

    void chooseOption(Predicate<String> option);

    default void chooseOption(String option) {
        if (option == null) {
            return;
        }
        String want = option.toLowerCase();
        chooseOption(t -> t != null && t.toLowerCase().contains(want));
    }

    void selectOtherQuantity();

    void chooseOption(int index);

    void choosePreviousOption();

    boolean isEnterInputOpen();

    void enterAmount(int amount);

    void enterName(String input);

    void selectItem(String name);

    void selectItem(int itemId);

    int getMakeXQuantity();

    boolean selectMakeXQuantity(int quantity);

    ProductionQuantity getSelectedQuantity();

    boolean selectQuantity(ProductionQuantity quantity);
}
