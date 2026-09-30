package net.storm.api.widgets;

/**
 * Storm {@code ITabs}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/ITabs.html">Storm ITabs</a>
 */
public interface ITabs {

    void open(Tab tab);

    boolean isOpen(Tab tab);
}
