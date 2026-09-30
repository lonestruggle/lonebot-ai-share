package net.storm.api.widgets;

import net.storm.api.domain.widgets.IWidget;

import java.util.List;
import java.util.function.Predicate;

/**
 * Storm {@code IDialog}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IDialog.html">Storm IDialog</a>
 */
public interface IDialog {

    void continueTutorial();

    boolean isEnterInputOpen();

    boolean isOpen();

    /**
     * @param inputType 2=friend name, 7=amount, 8=name, 9=text, 10=chat channel
     */
    void input(int inputType, String value);

    default void enterFriendName(String input) {
        input(2, input);
    }

    default void enterChatChannelName(String input) {
        input(10, input);
    }

    default void enterName(String input) {
        input(8, input);
    }

    default void enterText(String input) {
        input(9, input);
    }

    default void enterAmount(int input) {
        input(7, String.valueOf(input));
    }

    default boolean hasOption(String option) {
        return getOption(option) != null;
    }

    default boolean hasOption(Predicate<String> option) {
        return getOption(option) != null;
    }

    IWidget getOption(String option);

    IWidget getOption(Predicate<String> option);

    default boolean chooseOption(String... options) {
        if (options == null) {
            return false;
        }
        for (String o : options) {
            if (o != null && chooseOption(t -> t != null && t.toLowerCase().contains(o.toLowerCase()))) {
                return true;
            }
        }
        return false;
    }

    boolean chooseOption(Predicate<String> option);

    boolean chooseOption(int index);

    boolean canContinue();

    void continueSpace();

    /**
     * Continue-only dialogs: Space + invoke. Geen multi-choice.
     * {@code force=true} negeert globale aan/uit (Clue/Reldo).
     */
    default boolean autoContinue(boolean force) {
        if (!canContinue()) {
            return false;
        }
        continueSpace();
        return true;
    }

    default boolean autoContinue() {
        return autoContinue(false);
    }

    default int autoContinueDelay(boolean force) {
        return autoContinue(force) ? 280 : -1;
    }

    default int autoContinueDelay() {
        return autoContinueDelay(false);
    }

    default boolean isViewingOptions() {
        List<IWidget> opts = getOptions();
        return opts != null && !opts.isEmpty();
    }

    IWidget getOptionTitle();

    List<IWidget> getOptions();

    void forceOpen();

    void forceClose();

    String getText();

    String getName();
}
