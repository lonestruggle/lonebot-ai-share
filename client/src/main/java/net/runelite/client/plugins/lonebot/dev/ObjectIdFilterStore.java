package net.runelite.client.plugins.lonebot.dev;

import net.runelite.client.config.ConfigManager;
import net.storm.sdk.bot.BotRuntime;

import java.awt.AWTEvent;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Object-IDs die de scene-ID overlay verbergt. Geen maximum — CSV in config.
 * Default: naamloze scenery 7120–7126 (mijn/rots-clutter).
 */
public final class ObjectIdFilterStore {

    /** Naamloze scenery (screenshot: {@code null oid=7120} … {@code 7126}). */
    public static final int[] DEFAULT_IDS = {7120, 7121, 7122, 7123, 7124, 7125, 7126};

    public static final String DEFAULT_CSV = "7120,7121,7122,7123,7124,7125,7126";

    private static final Set<Integer> HIDDEN = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile ConfigManager configManager;
    private static volatile boolean altHeld;
    private static volatile boolean dispatcherInstalled;

    private ObjectIdFilterStore() {
    }

    public static void init(ConfigManager cm, String csv) {
        configManager = cm;
        installAltDispatcher();
        HIDDEN.clear();
        parseInto(csv == null || csv.isBlank() ? DEFAULT_CSV : csv, HIDDEN);
        fire();
    }

    public static boolean altHeld() {
        return altHeld;
    }

    public static boolean isHidden(int id) {
        return HIDDEN.contains(id);
    }

    public static int size() {
        return HIDDEN.size();
    }

    public static String serialize() {
        List<Integer> copy;
        synchronized (HIDDEN) {
            copy = new ArrayList<>(HIDDEN);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < copy.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(copy.get(i));
        }
        return sb.toString();
    }

    public static boolean add(int id) {
        if (id < 0) {
            return false;
        }
        boolean added = HIDDEN.add(id);
        if (added) {
            persist();
            BotRuntime.logConsole("[Dev/oid] filter + " + id + " (n=" + size() + ")");
            fire();
        }
        return added;
    }

    public static boolean remove(int id) {
        boolean removed = HIDDEN.remove(id);
        if (removed) {
            persist();
            BotRuntime.logConsole("[Dev/oid] filter − " + id + " (n=" + size() + ")");
            fire();
        }
        return removed;
    }

    public static void resetToDefault() {
        HIDDEN.clear();
        for (int id : DEFAULT_IDS) {
            HIDDEN.add(id);
        }
        persist();
        fire();
        BotRuntime.logConsole("[Dev/oid] filter reset default n=" + size());
    }

    public static void replaceFromCsv(String csv) {
        HIDDEN.clear();
        parseInto(csv, HIDDEN);
        persist();
        fire();
    }

    public static void addListener(Runnable r) {
        if (r != null) {
            LISTENERS.add(r);
        }
    }

    public static boolean isUnnamed(String name) {
        return name == null || name.isBlank() || "null".equalsIgnoreCase(name.trim());
    }

    private static void persist() {
        ConfigManager cm = configManager;
        if (cm != null) {
            cm.setConfiguration("lonebot", "devObjectIdFilter", serialize());
        }
    }

    private static void fire() {
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void parseInto(String csv, Set<Integer> dest) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String part : csv.split("[,;\\s]+")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                dest.add(Integer.parseInt(t));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static void installAltDispatcher() {
        if (dispatcherInstalled) {
            return;
        }
        dispatcherInstalled = true;
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher(e -> {
            if (e.getKeyCode() != KeyEvent.VK_ALT) {
                return false;
            }
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                altHeld = true;
            } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                altHeld = false;
            }
            return false;
        });
        kfm.addPropertyChangeListener("focusedWindow", evt -> {
            if (evt.getNewValue() == null) {
                altHeld = false;
            }
        });
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof WindowEvent)) {
                return;
            }
            int id = ev.getID();
            if (id == WindowEvent.WINDOW_LOST_FOCUS
                    || id == WindowEvent.WINDOW_DEACTIVATED
                    || id == WindowEvent.WINDOW_ICONIFIED) {
                altHeld = false;
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }
}
