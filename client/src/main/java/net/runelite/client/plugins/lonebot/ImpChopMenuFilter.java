package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.client.eventbus.Subscribe;
import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Imp op Karamja: haal {@code Chop down} uit hover + Choose Option,
 * en consumeer een Chop-klik zodat loot/walk geen boom raakt.
 * Niet actief tijdens Woodcutting.
 */
@Singleton
public class ImpChopMenuFilter {

    private static final Logger log = LoggerFactory.getLogger(ImpChopMenuFilter.class);

    private final Client client;
    private volatile long lastLogMs;
    private volatile String lastLog = "";

    @Inject
    public ImpChopMenuFilter(Client client) {
        this.client = client;
    }

    private static boolean impMenuActive() {
        if (!BotRuntime.botEnabled || BotRuntime.woodcuttingEnabled) {
            return false;
        }
        return BotRuntime.impKillerEnabled || BotRuntime.imps2Enabled;
    }

    /** Na menu-sort (hover-tooltip én RMB) — vóór weergave. */
    @Subscribe
    public void onPostMenuSort(PostMenuSort event) {
        stripChopFromMenu();
    }

    /** Na tile-marker custom entries: Chop mag niet terugkomen in Choose Option. */
    @Subscribe(priority = -10)
    public void onMenuOpened(MenuOpened event) {
        stripChopFromMenu();
    }

    /** Failsafe: LMB/invoke die toch Chop is → niet naar de server. */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!impMenuActive() || event == null) {
            return;
        }
        if (!isChopOption(event.getMenuOption())) {
            return;
        }
        MenuAction type = event.getMenuAction();
        if (type != null && !isObjectMenu(type)) {
            return;
        }
        event.consume();
        note("block Chop click");
    }

    private void stripChopFromMenu() {
        if (!impMenuActive() || client == null) {
            return;
        }
        MenuEntry[] entries = client.getMenuEntries();
        if (entries == null || entries.length == 0) {
            return;
        }
        List<MenuEntry> kept = new ArrayList<>(entries.length);
        int removed = 0;
        for (MenuEntry e : entries) {
            if (e != null && isChopOption(e.getOption())) {
                removed++;
                continue;
            }
            kept.add(e);
        }
        if (removed == 0) {
            return;
        }
        client.setMenuEntries(kept.toArray(new MenuEntry[0]));
        note("hide Chop ×" + removed);
    }

    private static boolean isChopOption(String option) {
        if (option == null || option.isBlank()) {
            return false;
        }
        String o = option.trim().toLowerCase(Locale.ROOT);
        return o.equals("chop down") || o.equals("chop");
    }

    private static boolean isObjectMenu(MenuAction type) {
        return type == MenuAction.GAME_OBJECT_FIRST_OPTION
                || type == MenuAction.GAME_OBJECT_SECOND_OPTION
                || type == MenuAction.GAME_OBJECT_THIRD_OPTION
                || type == MenuAction.GAME_OBJECT_FOURTH_OPTION
                || type == MenuAction.GAME_OBJECT_FIFTH_OPTION
                || type == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT;
    }

    private void note(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 2000L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Imp/menu] " + msg);
        log.debug("[Imp/menu] {}", msg);
    }
}
