package net.storm.api.widgets;

import java.time.Instant;

/**
 * Storm {@code IMinigames} — grouping teleport (20-minute cooldown).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IMinigames.html">Storm IMinigames</a>
 */
public interface IMinigames {

    boolean canTeleport();

    boolean teleport(MinigameTeleport destination);

    boolean open();

    boolean isOpen();

    boolean isTabOpen();

    Instant getLastMinigameTeleportUsage();
}
