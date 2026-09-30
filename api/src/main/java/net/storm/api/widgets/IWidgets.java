package net.storm.api.widgets;

import net.storm.api.domain.widgets.IWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Storm {@code IWidgets} — lookup by group/component/child or packed id.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IWidgets.html">Storm IWidgets</a>
 */
public interface IWidgets {

    List<IWidget> getAll(int group);

    IWidget get(int group, int id);

    IWidget get(int group, int id, int child);

    IWidget get(int component);

    IWidget get(int group, Predicate<IWidget> filter);

    IWidget getChild(int component, int child);

    boolean isVisible(IWidget widget);

    boolean isVisible(int group, int id);

    boolean isVisible(int group, int id, int child);

    boolean isVisible(int component);

    List<IWidget> getChildren(IWidget widget, Predicate<IWidget> filter);

    List<IWidget> getChildren(int group, int id, Predicate<IWidget> filter);

    List<IWidget> getChildren(int group, int id, int child, Predicate<IWidget> filter);

    List<IWidget> getChildren(int component, Predicate<IWidget> filter);

    void closeInterfaces();

    @Deprecated(forRemoval = true)
    default IWidget get(InterfaceAddress interfaceAddress) {
        return interfaceAddress == null ? null : get(interfaceAddress.getPackedId());
    }

    @Deprecated(forRemoval = true)
    default boolean isVisible(InterfaceAddress interfaceAddress) {
        return interfaceAddress != null && isVisible(interfaceAddress.getPackedId());
    }

    @Deprecated(forRemoval = true)
    default List<IWidget> getChildren(InterfaceAddress interfaceAddress, Predicate<IWidget> filter) {
        if (interfaceAddress == null) {
            return new ArrayList<>();
        }
        return getChildren(interfaceAddress.getPackedId(), filter);
    }
}
