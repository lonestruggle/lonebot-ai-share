package net.storm.sdk.widgets;

import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.widgets.IMinigames;
import net.storm.api.widgets.MinigameTeleport;
import net.storm.api.widgets.Tab;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/**
 * Storm {@code Minigames} — grouping teleport interface (group 76, 20-minute cooldown).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/widgets/IMinigames.html">Storm IMinigames</a>
 */
public final class Minigames {

    public static final int GROUPING_GROUP = InterfaceID.GROUPING;

    public static final IMinigames API = new Api();

    static {
        net.storm.api.Static.bindMinigames(API);
        MinigameTeleport.Access.bind(Minigames::canUseDestination, Minigames::currentDestination);
    }

    public Minigames() {
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget w = c.getWidget(InterfaceID.Grouping.UNIVERSE);
            if (w != null && !w.isHidden()) {
                return true;
            }
            w = c.getWidget(GROUPING_GROUP, 0);
            return w != null && !w.isHidden();
        }, false));
    }

    public static boolean isTabOpen() {
        return isOpen() || Tabs.isOpen(Tab.QUESTS);
    }

    public static void close() {
        if (isOpen()) {
            Keyboard.pressKey(KeyEvent.VK_ESCAPE);
        }
    }

    public static boolean open() {
        if (isOpen()) {
            return true;
        }
        Tabs.open(Tab.QUESTS);
        IWidget tab = Widgets.getFirst(w -> {
            String t = w.getText();
            String n = w.getName();
            return (t != null && t.toLowerCase().contains("minigame"))
                    || (n != null && n.toLowerCase().contains("minigame"));
        });
        if (tab != null && (tab.interact("Minigames") || tab.interact("View") || tab.interact(tab.getText()))) {
            return true;
        }
        return isOpen();
    }

    public static boolean canTeleport() {
        Instant last = getLastMinigameTeleportUsage();
        if (last == null) {
            return true;
        }
        return Duration.between(last, Instant.now()).toMinutes() >= 20;
    }

    public static Instant getLastMinigameTeleportUsage() {
        Integer raw = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            return c.getVarpValue(VarPlayer.LAST_MINIGAME_TELEPORT);
        }, 0);
        int v = raw != null ? raw : 0;
        if (v <= 0) {
            return null;
        }
        return Instant.ofEpochSecond(v * 60L);
    }

    public static boolean teleport(String destination) {
        MinigameTeleport named = MinigameTeleport.byName(destination);
        if (named != MinigameTeleport.NONE) {
            return teleport(named);
        }
        return clickDestination(destination);
    }

    public static boolean teleport(MinigameTeleport destination) {
        if (destination == null || destination == MinigameTeleport.NONE) {
            return false;
        }
        if (!canTeleport()) {
            return false;
        }
        if (!isOpen() && !open()) {
            return false;
        }
        return clickDestination(destination.getDisplayName());
    }

    static boolean canUseDestination(MinigameTeleport dest) {
        if (dest == null || dest == MinigameTeleport.NONE) {
            return false;
        }
        if (dest.isMembers() && !membersWorld()) {
            return false;
        }
        if (isOpen()) {
            return destinationVisible(dest.getDisplayName());
        }
        return !dest.isMembers();
    }

    static MinigameTeleport currentDestination() {
        if (!isOpen()) {
            return MinigameTeleport.NONE;
        }
        return Static.callOnClientThread(() -> {
            IWidget current = Widgets.get(InterfaceID.Grouping.CURRENTGAME);
            String text = current != null ? current.getText() : null;
            if (text == null || text.isBlank()) {
                return MinigameTeleport.NONE;
            }
            return MinigameTeleport.byName(WidgetText.strip(text));
        }, MinigameTeleport.NONE);
    }

    private static boolean clickDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return false;
        }
        IWidget dropdown = Widgets.get(InterfaceID.Grouping.DROPDOWN);
        if (dropdown != null) {
            dropdown.interact("Select");
            dropdown.interact(dropdown.getText());
        }
        IWidget hit = Widgets.getFirst(w -> {
            String t = w.getText();
            String n = w.getName();
            return (t != null && t.toLowerCase().contains(destination.toLowerCase()))
                    || (n != null && n.toLowerCase().contains(destination.toLowerCase()));
        });
        if (hit != null) {
            hit.interact("Select");
            hit.interact(destination);
        }
        IWidget tele = Widgets.get(InterfaceID.Grouping.TELEPORT);
        if (tele != null) {
            return tele.interact("Teleport") || tele.interact("Enter");
        }
        return hit != null && (hit.interact("Teleport") || hit.interact("Enter") || hit.interact(destination));
    }

    private static boolean destinationVisible(String destination) {
        if (destination == null || destination.isBlank()) {
            return false;
        }
        IWidget hit = Widgets.getFirst(w -> {
            String t = w.getText();
            return t != null && t.toLowerCase().contains(destination.toLowerCase());
        });
        return hit != null && !hit.isHidden();
    }

    private static boolean membersWorld() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                EnumSet<WorldType> types = c.getWorldType();
                return types != null && types.contains(WorldType.MEMBERS);
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }

    private static final class Api implements IMinigames {
        @Override
        public boolean canTeleport() {
            return Minigames.canTeleport();
        }

        @Override
        public boolean teleport(MinigameTeleport destination) {
            return Minigames.teleport(destination);
        }

        @Override
        public boolean open() {
            return Minigames.open();
        }

        @Override
        public boolean isOpen() {
            return Minigames.isOpen();
        }

        @Override
        public boolean isTabOpen() {
            return Minigames.isTabOpen();
        }

        @Override
        public Instant getLastMinigameTeleportUsage() {
            return Minigames.getLastMinigameTeleportUsage();
        }
    }
}
