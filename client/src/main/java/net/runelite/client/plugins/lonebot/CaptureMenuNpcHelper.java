package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;

/**
 * NPC uit rechtermenu (Talk-to / Examine) voor Get walls (NPC).
 */
public final class CaptureMenuNpcHelper {

    public static final class NpcHit {
        public final WorldPoint tile;
        public final String name;
        public final int id;

        public NpcHit(WorldPoint tile, String name, int id) {
            this.tile = tile;
            this.name = name != null && !name.isEmpty() ? name : "NPC";
            this.id = id;
        }
    }

    private CaptureMenuNpcHelper() {
    }

    public static NpcHit findNpcInMenu(Client client) {
        if (client == null) {
            return null;
        }
        MenuEntry[] entries = client.getMenuEntries();
        if (entries == null) {
            return null;
        }
        for (MenuEntry entry : entries) {
            if (entry == null || !isNpcMenuAction(entry.getType())) {
                continue;
            }
            NPC npc = findNpcByIndex(client, entry.getIdentifier());
            if (npc == null) {
                continue;
            }
            WorldPoint loc = npc.getWorldLocation();
            if (loc == null) {
                continue;
            }
            String name = npc.getName();
            if (name == null || name.isEmpty()) {
                String target = Text.removeTags(entry.getTarget());
                if (target != null && !target.isEmpty()) {
                    name = target;
                }
            }
            return new NpcHit(loc, name, npc.getId());
        }
        return null;
    }

    private static boolean isNpcMenuAction(MenuAction type) {
        if (type == null) {
            return false;
        }
        return type == MenuAction.NPC_FIRST_OPTION
                || type == MenuAction.NPC_SECOND_OPTION
                || type == MenuAction.NPC_THIRD_OPTION
                || type == MenuAction.NPC_FOURTH_OPTION
                || type == MenuAction.NPC_FIFTH_OPTION
                || type == MenuAction.EXAMINE_NPC;
    }

    private static NPC findNpcByIndex(Client client, int index) {
        try {
            for (NPC npc : client.getNpcs()) {
                if (npc != null && npc.getIndex() == index) {
                    return npc;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
