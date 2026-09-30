package net.storm.api.domain.actors;

import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.storm.api.domain.Transformable;

/**
 * NPC wrapper.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/actors/INPC.html">Storm INPC</a>
 */
public interface INPC extends IActor, Transformable<NPCComposition> {

    @Override
    int getId();

    HeadIcon[] getOverheadIcons();

    void update(NPC npc);

    default HeadIcon getOverheadIcon() {
        HeadIcon[] icons = getOverheadIcons();
        return icons != null && icons.length > 0 ? icons[0] : null;
    }

    @Override
    NPC getWrapped();
}
