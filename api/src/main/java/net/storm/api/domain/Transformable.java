package net.storm.api.domain;

/**
 * Entity whose displayed id/composition can differ from the base definition (varbit transform).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/Transformable.html">Storm Transformable</a>
 */
public interface Transformable<C> {

    int getActualId();

    C getTransformedComposition();

    default C transform() {
        return getTransformedComposition();
    }
}
