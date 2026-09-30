package net.storm.sdk.interact.builder;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.interact.AutomatedMenu;
import net.storm.api.interact.builder.MenuBuilder;
import net.storm.sdk.game.Static;

/**
 * Factory for {@link MenuBuilder} / {@link AutomatedMenu} from common entity types.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/interact/builder/MenuFactory.html">Storm MenuFactory</a>
 */
public final class MenuFactory {

    private MenuFactory() {
    }

    public static MenuBuilder npc(INPC npc, String action) {
        MenuBuilder b = new MenuBuilder();
        if (npc == null || action == null) {
            return b;
        }
        b.setOption(action)
                .setTarget(npc.getName() != null ? npc.getName() : "")
                .setIdentifier(npc.getIndex())
                .setParam0(0)
                .setParam1(0)
                .setClickPoint(npc.getCanvasPoint());
        Integer opcode = Static.callOnClientThread(() -> resolveNpcOpcode(npc, action), null);
        if (opcode != null) {
            b.setOpcode(opcode);
        } else {
            b.setOpcode(MenuAction.NPC_FIRST_OPTION);
        }
        return b;
    }

    public static AutomatedMenu npcMenu(INPC npc, String action) {
        return npc(npc, action).build();
    }

    public static MenuBuilder object(ITileObject object, String action) {
        MenuBuilder b = new MenuBuilder();
        if (object == null || action == null) {
            return b;
        }
        b.setOption(action)
                .setTarget(object.getName() != null ? object.getName() : "")
                .setIdentifier(object.getId());
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            if (object.getWorldLocation() != null) {
                LocalPoint lp = LocalPoint.fromWorld(c, object.getWorldLocation());
                if (lp != null) {
                    b.setParam0(lp.getSceneX());
                    b.setParam1(lp.getSceneY());
                }
            }
            ObjectComposition comp = c.getObjectDefinition(object.getId());
            int idx = actionIndex(comp != null ? comp.getActions() : null, action);
            b.setOpcode(objectOpcode(idx));
            if (comp != null && comp.getName() != null) {
                b.setTarget(comp.getName());
            }
            return Boolean.TRUE;
        }, null);
        return b;
    }

    public static AutomatedMenu objectMenu(ITileObject object, String action) {
        return object(object, action).build();
    }

    public static MenuBuilder item(IInventoryItem item, String action) {
        MenuBuilder b = new MenuBuilder();
        if (item == null || action == null) {
            return b;
        }
        b.setOption(action)
                .setTarget(item.getName() != null ? item.getName() : "")
                .setIdentifier(1)
                .setParam0(item.getSlot())
                .setParam1(WidgetInfo.INVENTORY.getId())
                .setItemId(item.getId());
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            ItemComposition comp = c.getItemDefinition(item.getId());
            int idx = actionIndex(comp != null ? comp.getInventoryActions() : null, action);
            // Prefer live widget later in InteractManager; identifier from composition is fallback only
            b.setIdentifier(Math.max(1, idx + 1));
            if ("Drop".equalsIgnoreCase(action)) {
                b.setIdentifier(7);
                b.setOpcode(MenuAction.CC_OP_LOW_PRIORITY);
            } else {
                b.setOpcode(idx >= 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP);
            }
            if (comp != null && comp.getName() != null) {
                b.setTarget(comp.getName());
            }
            return Boolean.TRUE;
        }, null);
        return b;
    }

    public static AutomatedMenu itemMenu(IInventoryItem item, String action) {
        return item(item, action).build();
    }

    public static MenuBuilder widget(IWidget widget, String action) {
        MenuBuilder b = new MenuBuilder();
        if (widget == null || action == null) {
            return b;
        }
        b.setOption(action)
                .setTarget(widget.getName() != null ? widget.getName() : "")
                .setIdentifier(1)
                .setOpcode(MenuAction.CC_OP)
                .setParam0(-1)
                .setParam1(widget.getId());
        if (widget.getBounds() != null) {
            java.awt.Rectangle r = widget.getBounds();
            b.setClickPoint(new net.runelite.api.Point(r.x + r.width / 2, r.y + r.height / 2));
        }
        return b;
    }

    public static AutomatedMenu widgetMenu(IWidget widget, String action) {
        return widget(widget, action).build();
    }

    private static Integer resolveNpcOpcode(INPC npc, String action) {
        // Composition must be read on client thread; caller already wraps.
        try {
            // Best-effort via interactable actions — RlNpc hasAction path uses composition.
            // Without unwrap, default to first option unless we scan via client NPCs.
            Client c = Static.getClient();
            if (c == null) {
                return MenuAction.NPC_FIRST_OPTION.getId();
            }
            for (net.runelite.api.NPC n : c.getNpcs()) {
                if (n == null || n.getIndex() != npc.getIndex()) {
                    continue;
                }
                NPCComposition comp = n.getTransformedComposition();
                if (comp == null) {
                    comp = n.getComposition();
                }
                int idx = actionIndex(comp != null ? comp.getActions() : null, action);
                return npcOpcode(idx).getId();
            }
        } catch (Throwable ignored) {
        }
        return MenuAction.NPC_FIRST_OPTION.getId();
    }

    private static int actionIndex(String[] actions, String action) {
        if (actions == null || action == null) {
            return 0;
        }
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && actions[i].equalsIgnoreCase(action)) {
                return i;
            }
        }
        return 0;
    }

    private static MenuAction npcOpcode(int i) {
        switch (i) {
            case 1:
                return MenuAction.NPC_SECOND_OPTION;
            case 2:
                return MenuAction.NPC_THIRD_OPTION;
            case 3:
                return MenuAction.NPC_FOURTH_OPTION;
            case 4:
                return MenuAction.NPC_FIFTH_OPTION;
            default:
                return MenuAction.NPC_FIRST_OPTION;
        }
    }

    private static MenuAction objectOpcode(int i) {
        switch (i) {
            case 1:
                return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 2:
                return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 3:
                return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            case 4:
                return MenuAction.GAME_OBJECT_FIFTH_OPTION;
            default:
                return MenuAction.GAME_OBJECT_FIRST_OPTION;
        }
    }
}
