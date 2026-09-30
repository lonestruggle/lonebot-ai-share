package net.storm.api.domain.actors;

import net.runelite.api.HeadIcon;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;

/**
 * Player wrapper.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/actors/IPlayer.html">Storm IPlayer</a>
 */
public interface IPlayer extends IActor {

    void update(Player player);

    boolean isFriend();

    boolean isClanMember();

    boolean isFriendsChatMember();

    HeadIcon getOverheadIcon();

    /** Skull sprite id, or -1 if none (RL 1.12+ returns int, not enum). */
    int getSkullIcon();

    int getTeam();

    PlayerComposition getPlayerComposition();

    @Override
    Player getWrapped();
}
