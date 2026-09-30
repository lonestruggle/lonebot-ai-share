package net.storm.api.domain;

/**
 * Storm {@code RuneLiteWrapper} — access the underlying RL object.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/RuneLiteWrapper.html">Storm RuneLiteWrapper</a>
 */
public interface RuneLiteWrapper<T> {

    T getWrapped();
}
