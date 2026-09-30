package net.storm.sdk.widgets;

import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.widgets.ITabs;
import net.storm.api.widgets.Tab;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.interact.MenuInteract;

import java.awt.event.KeyEvent;

/**
 * Game tab helpers — open / isOpen / getCurrent via WidgetInfo + {@link VarClientInt#INVENTORY_TAB}.
 */
public final class Tabs {

    public static final ITabs API = new Api();

    static {
        net.storm.api.Static.bindTabs(API);
    }

    private Tabs() {
    }

    public static void open(Tab tab) {
        if (tab == null || isOpen(tab)) {
            return;
        }
        WidgetInfo info = tabWidget(tab);
        if (info != null) {
            Boolean clicked = Static.callOnClientThread(() -> {
                Client c = Static.getClient();
                if (c == null) {
                    return false;
                }
                Widget w = c.getWidget(info);
                if (w == null || w.isHidden()) {
                    // try resizable counterpart
                    WidgetInfo alt = resizableTabWidget(tab);
                    if (alt != null) {
                        w = c.getWidget(alt);
                    }
                }
                if (w == null || w.isHidden()) {
                    return false;
                }
                return MenuInteract.invokeMenu(
                        "Open",
                        "",
                        1,
                        net.runelite.api.MenuAction.CC_OP.getId(),
                        -1,
                        w.getId()
                );
            }, false);
            if (Boolean.TRUE.equals(clicked)) {
                return;
            }
        }
        int key = hotkey(tab);
        if (key != -1) {
            Keyboard.pressKey(key);
        }
    }

    public static boolean isOpen(Tab tab) {
        if (tab == null) {
            return false;
        }
        return Tab.same(getCurrent(), tab);
    }

    public static Tab getCurrent() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            int idx = c.getVarcIntValue(VarClientInt.INVENTORY_TAB);
            return fromVarc(idx);
        }, null);
    }

    private static Tab fromVarc(int idx) {
        switch (idx) {
            case 0:
                return Tab.COMBAT;
            case 1:
                return Tab.SKILLS;
            case 2:
                return Tab.QUESTS;
            case 3:
                return Tab.INVENTORY;
            case 4:
                return Tab.EQUIPMENT;
            case 5:
                return Tab.PRAYER;
            case 6:
                return Tab.MAGIC;
            case 7:
                return Tab.CLAN_CHAT;
            case 8:
                return Tab.FRIENDS;
            case 9:
                return Tab.ACCOUNT;
            case 10:
                return Tab.LOG_OUT;
            case 11:
                return Tab.OPTIONS;
            case 12:
                return Tab.EMOTES;
            case 13:
                return Tab.MUSIC;
            default:
                return null;
        }
    }

    private static int hotkey(Tab tab) {
        switch (tab) {
            case COMBAT:
                return KeyEvent.VK_F1;
            case SKILLS:
                return KeyEvent.VK_F2;
            case QUESTS:
                return KeyEvent.VK_F3;
            case INVENTORY:
                return KeyEvent.VK_ESCAPE;
            case EQUIPMENT:
                return KeyEvent.VK_F4;
            case PRAYER:
                return KeyEvent.VK_F5;
            case MAGIC:
                return KeyEvent.VK_F6;
            default:
                return -1;
        }
    }

    private static WidgetInfo tabWidget(Tab tab) {
        switch (tab) {
            case COMBAT:
                return WidgetInfo.FIXED_VIEWPORT_COMBAT_TAB;
            case SKILLS:
                return WidgetInfo.FIXED_VIEWPORT_STATS_TAB;
            case QUESTS:
                return WidgetInfo.FIXED_VIEWPORT_QUESTS_TAB;
            case INVENTORY:
                return WidgetInfo.FIXED_VIEWPORT_INVENTORY_TAB;
            case EQUIPMENT:
                return WidgetInfo.FIXED_VIEWPORT_EQUIPMENT_TAB;
            case PRAYER:
                return WidgetInfo.FIXED_VIEWPORT_PRAYER_TAB;
            case MAGIC:
                return WidgetInfo.FIXED_VIEWPORT_MAGIC_TAB;
            case CLAN:
            case CLAN_CHAT:
                return WidgetInfo.FIXED_VIEWPORT_FRIENDS_CHAT_TAB;
            case FRIENDS:
                return WidgetInfo.FIXED_VIEWPORT_FRIENDS_TAB;
            case ACCOUNT:
                return WidgetInfo.FIXED_VIEWPORT_IGNORES_TAB;
            case LOGOUT:
            case LOG_OUT:
                return WidgetInfo.FIXED_VIEWPORT_LOGOUT_TAB;
            case OPTIONS:
                return WidgetInfo.FIXED_VIEWPORT_OPTIONS_TAB;
            case EMOTES:
                return WidgetInfo.FIXED_VIEWPORT_EMOTES_TAB;
            case MUSIC:
                return WidgetInfo.FIXED_VIEWPORT_MUSIC_TAB;
            default:
                return null;
        }
    }

    private static WidgetInfo resizableTabWidget(Tab tab) {
        switch (tab) {
            case COMBAT:
                return WidgetInfo.RESIZABLE_VIEWPORT_COMBAT_TAB;
            case SKILLS:
                return WidgetInfo.RESIZABLE_VIEWPORT_STATS_TAB;
            case QUESTS:
                return WidgetInfo.RESIZABLE_VIEWPORT_QUESTS_TAB;
            case INVENTORY:
                return WidgetInfo.RESIZABLE_VIEWPORT_INVENTORY_TAB;
            case EQUIPMENT:
                return WidgetInfo.RESIZABLE_VIEWPORT_EQUIPMENT_TAB;
            case PRAYER:
                return WidgetInfo.RESIZABLE_VIEWPORT_PRAYER_TAB;
            case MAGIC:
                return WidgetInfo.RESIZABLE_VIEWPORT_MAGIC_TAB;
            case CLAN:
            case CLAN_CHAT:
                return WidgetInfo.RESIZABLE_VIEWPORT_FRIENDS_CHAT_TAB;
            case FRIENDS:
                return WidgetInfo.RESIZABLE_VIEWPORT_FRIENDS_TAB;
            case ACCOUNT:
                return WidgetInfo.RESIZABLE_VIEWPORT_IGNORES_TAB;
            case LOGOUT:
            case LOG_OUT:
                return WidgetInfo.RESIZABLE_VIEWPORT_LOGOUT_TAB;
            case OPTIONS:
                return WidgetInfo.RESIZABLE_VIEWPORT_OPTIONS_TAB;
            case EMOTES:
                return WidgetInfo.RESIZABLE_VIEWPORT_EMOTES_TAB;
            case MUSIC:
                return WidgetInfo.RESIZABLE_VIEWPORT_MUSIC_TAB;
            default:
                return null;
        }
    }

    private static final class Api implements ITabs {
        @Override
        public void open(Tab tab) {
            Tabs.open(tab);
        }

        @Override
        public boolean isOpen(Tab tab) {
            return Tabs.isOpen(tab);
        }
    }
}
