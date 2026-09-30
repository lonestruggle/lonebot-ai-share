package com.lonebot.example.clue;

import net.storm.api.domain.widgets.IWidget;
import net.storm.api.widgets.Tab;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.widgets.Tabs;
import net.storm.sdk.widgets.Widgets;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Emote-knoppen: lookup op <b>naam</b> in emote-panel (niet op shared container-id 216,2).
 * Volledige CombatBot {@code EmoteWidgetRegistry}-port — geen thin shell.
 * <p>
 * Storm/hover geven vaak hetzelfde packed id voor elke knop; klik altijd via naam → Perform.
 */
public final class EmoteWidgetRegistry {

    public static final int EMOTE_INTERFACE = 216;
    /** Panel-container child — Storm deelt dit id vaak over knoppen. */
    public static final int EMOTE_PANEL_CONTAINER_PACKED = (EMOTE_INTERFACE << 16) | 2;

    private static final int SCAN_MAX_DEPTH = 2;
    private static final int SCAN_MAX_CHILD = 60;
    private static final long PANEL_CACHE_TTL_MS = 90_000L;

    /** Gecachte panel-knoppen: emote-naam → widget (ververst elke 90s of bij force). */
    private static volatile List<Map.Entry<String, IWidget>> cachedPanelButtons = List.of();
    private static volatile long panelCacheMs;
    private static long lastFailLogMs;
    private static long lastCacheLogMs;

    private EmoteWidgetRegistry() {
    }

    /**
     * Zoek knop op <b>emote-naam</b> in open panel — niet op gedeelde capture-id.
     */
    public static IWidget findPerformButton(String emoteName) {
        if (emoteName == null || emoteName.isBlank()) {
            return null;
        }
        String low = normalizeEmoteName(emoteName);
        refreshPanelButtonCache(false);
        for (Map.Entry<String, IWidget> e : cachedPanelButtons) {
            if (low.equals(e.getKey()) && isPerformButton(e.getValue(), low)) {
                return e.getValue();
            }
        }
        refreshPanelButtonCache(true);
        for (Map.Entry<String, IWidget> e : cachedPanelButtons) {
            if (low.equals(e.getKey()) && isPerformButton(e.getValue(), low)) {
                return e.getValue();
            }
        }
        // Alias: Blow Raspberry ↔ Raspberry
        if ("raspberry".equals(low) || "blow raspberry".equals(low)) {
            for (String alt : new String[]{"raspberry", "blow raspberry"}) {
                for (Map.Entry<String, IWidget> e : cachedPanelButtons) {
                    if (alt.equals(e.getKey()) && isPerformButton(e.getValue(), alt)) {
                        return e.getValue();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Open emote-tab indien nodig, vind knop op naam, Perform / muis.
     */
    public static boolean clickEmote(String emoteName) {
        if (emoteName == null || emoteName.isBlank()) {
            return false;
        }
        if (!Tabs.isOpen(Tab.EMOTES)) {
            Tabs.open(Tab.EMOTES);
            return false;
        }
        IWidget w = findPerformButton(emoteName);
        if (w == null) {
            logFail("registry miss: " + emoteName
                    + " (cache=" + cachedPanelButtons.size() + ")");
            return false;
        }
        String low = normalizeEmoteName(emoteName);
        if (!emoteNameExact(w, low)) {
            logFail("naam-mismatch: wil " + emoteName + " knop=" + widgetDisplayName(w));
            return false;
        }
        if (clickEmoteWidget(w, emoteName)) {
            BotRuntime.logConsole("[Clue/Emote] Perform " + emoteName.trim());
            return true;
        }
        logFail("klik mislukt: " + emoteName);
        return false;
    }

    /** Force cache refresh (na tab-open / fail). */
    public static void invalidateCache() {
        cachedPanelButtons = List.of();
        panelCacheMs = 0L;
    }

    public static int cachedButtonCount() {
        return cachedPanelButtons.size();
    }

    private static boolean clickEmoteWidget(IWidget w, String emoteName) {
        try {
            if (widgetHasPerformAction(w) && w.interact("Perform")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String display = emoteDisplay(normalizeEmoteName(emoteName));
        try {
            if (display != null && !display.isEmpty() && w.hasAction(display) && w.interact(display)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Rectangle b = safeBounds(w);
            if (b != null && b.width >= 8 && b.height >= 8) {
                net.runelite.api.Point p = new net.runelite.api.Point(
                        b.x + b.width / 2, b.y + b.height / 2);
                if (MouseManager.interactAt(p, true)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void refreshPanelButtonCache(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && !cachedPanelButtons.isEmpty() && now - panelCacheMs < PANEL_CACHE_TTL_MS) {
            return;
        }
        List<Map.Entry<String, IWidget>> list = collectNamedPerformButtons();
        cachedPanelButtons = list;
        panelCacheMs = now;
        if (!list.isEmpty() && now - lastCacheLogMs >= 5_000L) {
            lastCacheLogMs = now;
            BotRuntime.logConsole("[Clue/Emote] panel-cache: " + list.size() + " knoppen (by name)");
        }
    }

    private static List<Map.Entry<String, IWidget>> collectNamedPerformButtons() {
        List<Map.Entry<String, IWidget>> list = new ArrayList<>();
        Map<String, IWidget> byName = new LinkedHashMap<>();
        Set<String> seenBounds = new HashSet<>();
        for (int child = 0; child <= SCAN_MAX_CHILD; child++) {
            try {
                IWidget root = Widgets.get(EMOTE_INTERFACE, child);
                if (root != null) {
                    collectPerformButtonsFromRoot(root, 0, byName, seenBounds);
                }
            } catch (Throwable ignored) {
            }
        }
        list.addAll(byName.entrySet());
        return list;
    }

    private static void collectPerformButtonsFromRoot(IWidget root, int depth,
            Map<String, IWidget> byName, Set<String> seenBounds) {
        if (root == null || depth > SCAN_MAX_DEPTH) {
            return;
        }
        indexNamedPerformButton(root, byName, seenBounds);
        if (depth >= SCAN_MAX_DEPTH) {
            return;
        }
        Deque<IWidget> q = new ArrayDeque<>();
        enqueueChildren(q, root);
        while (!q.isEmpty()) {
            IWidget w = q.poll();
            if (w == null) {
                continue;
            }
            indexNamedPerformButton(w, byName, seenBounds);
            if (depth + 1 < SCAN_MAX_DEPTH) {
                enqueueChildren(q, w);
            }
        }
    }

    private static void indexNamedPerformButton(IWidget w, Map<String, IWidget> byName,
            Set<String> seenBounds) {
        if (w == null || isPanelContainer(w) || !widgetHasPerformAction(w) || !hasButtonBounds(w)) {
            return;
        }
        String boundsKey = buttonBoundsKey(w);
        if (boundsKey == null || !seenBounds.add(boundsKey)) {
            return;
        }
        String name = widgetDisplayName(w);
        if (name == null || name.isEmpty()) {
            return;
        }
        byName.putIfAbsent(name, w);
    }

    private static String buttonBoundsKey(IWidget w) {
        Rectangle b = safeBounds(w);
        if (b == null) {
            return null;
        }
        String name = widgetDisplayName(w);
        return b.x + "," + b.y + "," + b.width + "," + b.height
                + (name != null ? "," + name : "");
    }

    private static boolean isPerformButton(IWidget w, String emoteLow) {
        if (w == null || isPanelContainer(w) || !widgetHasPerformAction(w) || !hasButtonBounds(w)) {
            return false;
        }
        String live = widgetDisplayName(w);
        return live == null || live.equals(emoteLow)
                || ("raspberry".equals(emoteLow) && "blow raspberry".equals(live))
                || ("blow raspberry".equals(emoteLow) && "raspberry".equals(live));
    }

    private static boolean emoteNameExact(IWidget w, String wantLow) {
        String live = widgetDisplayName(w);
        if (live == null) {
            return false;
        }
        if (live.equals(wantLow)) {
            return true;
        }
        return ("raspberry".equals(wantLow) && "blow raspberry".equals(live))
                || ("blow raspberry".equals(wantLow) && "raspberry".equals(live));
    }

    private static boolean isPanelContainer(IWidget w) {
        if (w == null) {
            return false;
        }
        int[] gc = widgetGroupChild(w);
        if (gc == null || gc[0] != EMOTE_INTERFACE) {
            return false;
        }
        if (gc[1] > 4) {
            return false;
        }
        Rectangle b = safeBounds(w);
        if (b != null && (b.width > 80 || b.height > 80)) {
            return true;
        }
        return !widgetHasPerformAction(w) && !hasButtonBounds(w);
    }

    private static int[] widgetGroupChild(IWidget w) {
        try {
            int id = w.getId();
            if (id < 0) {
                return null;
            }
            return new int[]{id >>> 16, id & 0xFFFF};
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void enqueueChildren(Deque<IWidget> q, IWidget w) {
        addKids(q, safeChildren(w));
        try {
            addKids(q, w.getDynamicChildren());
        } catch (Throwable ignored) {
        }
        try {
            addKids(q, w.getStaticChildren());
        } catch (Throwable ignored) {
        }
    }

    private static void addKids(Deque<IWidget> q, IWidget[] kids) {
        if (kids == null) {
            return;
        }
        for (IWidget c : kids) {
            if (c != null) {
                q.add(c);
            }
        }
    }

    private static boolean widgetHasPerformAction(IWidget w) {
        try {
            return w.hasAction("Perform");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasButtonBounds(IWidget w) {
        Rectangle b = safeBounds(w);
        return b != null && b.width >= 28 && b.width <= 70 && b.height >= 28 && b.height <= 70;
    }

    private static String widgetDisplayName(IWidget w) {
        try {
            String raw = w.getName();
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            String n = ClueScrollHelper.strip(raw).toLowerCase(Locale.ROOT).trim();
            return n.isEmpty() ? null : n;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeEmoteName(String name) {
        return ClueScrollHelper.normalize(name);
    }

    private static String emoteDisplay(String norm) {
        if ("blow raspberry".equals(norm) || "raspberry".equals(norm)) {
            return "Blow Raspberry";
        }
        if ("jump for joy".equals(norm)) {
            return "Jump for Joy";
        }
        if (norm == null || norm.isEmpty()) {
            return "";
        }
        String[] p = norm.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String s : p) {
            if (s.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }

    private static Rectangle safeBounds(IWidget w) {
        try {
            return w.getBounds();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IWidget[] safeChildren(IWidget w) {
        try {
            return w.getChildren();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logFail(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastFailLogMs < 1_500L) {
            return;
        }
        lastFailLogMs = now;
        BotRuntime.logConsole("[Clue/Emote] " + msg);
    }
}
