package net.storm.api.domain;

/**
 * Scene entity (actor / object / ground item).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/SceneEntity.html">Storm SceneEntity</a>
 */
public interface SceneEntity extends Locatable, Identifiable, Interactable, Nameable {

    /**
     * Instanced world view, or {@code null} if the client has no WorldView API / entity is in the main world.
     */
    Object getWorldView();
}
