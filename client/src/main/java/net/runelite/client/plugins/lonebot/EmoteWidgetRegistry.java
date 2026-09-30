package net.runelite.client.plugins.lonebot;

import net.runelite.client.util.Text;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.widgets.Widgets;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emote-knoppen: lookup op <b>naam</b> in emote-panel (niet op shared container-id 216,2).
 * Storm/hover geven vaak id=14155778 voor elke knop — dat is het panel, geen duplicate-emote.
 */
public final class EmoteWidgetRegistry {

    public static final int EMOTE_INTERFACE = 216;

    /** Panel-container child index — Storm geeft vaak id=14155778 op elke knop. */
    public static final int EMOTE_PANEL_CONTAINER_PACKED = (EMOTE_INTERFACE << 16) | 2;

    private static final int SCAN_MAX_DEPTH = 2;
    private static final int SCAN_MAX_CHILD = 60;
    private static final long PANEL_CACHE_TTL_MS = 90_000L;

    private static final Pattern IFACE_PATTERN = Pattern.compile("iface=(\\d+),(\\d+)");
    private static final Pattern ID_PATTERN = Pattern.compile("id=(\\d+)");
    private static final Pattern HUMAN_LABEL_PATTERN = Pattern.compile("humanLabel:\\s*(.+)", Pattern.CASE_INSENSITIVE);

    /** Gecachte panel-knoppen: emote-naam → widget (ververst elke 90s of bij rescan). */
    private static volatile List<Map.Entry<String, IWidget>> cachedPanelButtons = List.of();
    private static volatile long panelCacheMs;

    private static volatile boolean rescanAllowed;

    private EmoteWidgetRegistry() {
    }

    /** No-op in LoneBot (geen WidgetRegistry-aliases). */
    public static void purgeStaleEmoteWidgetRegistryAliases() {
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
        return null;
    }

    public static boolean tryRegisterFromCapture(String captureDetail, String summaryLine) {
        if (captureDetail == null || !captureDetail.contains("iface=216")) {
            return false;
        }
        String emoteName = parseHumanLabel(captureDetail);
        if (emoteName == null || emoteName.isBlank()) {
            emoteName = parseNameFromSummary(summaryLine);
        }
        if (emoteName == null || emoteName.isBlank()) {
            return false;
        }
        refreshPanelButtonCache(true);
        IWidget w = findPerformButton(emoteName);
        if (w != null) {
            int packed = parsePackedId(captureDetail);
            String idNote = packed == EMOTE_PANEL_CONTAINER_PACKED
                    ? " (Storm shared id — lookup by name)" : "";
            BotRuntime.logConsole("[Emote] Emote capture OK (naam): " + emoteName + " "
                    + formatRef(w) + idNote);
            return true;
        }
        BotRuntime.logConsole("[Emote] Emote capture: " + emoteName
                + " niet in panel — open emote-tab en Rescan emotes");
        return false;
    }

    /** Handmatige scan — logt elke emote-naam + bounds (ids mogen gelijk zijn). */
    public static List<String> rescanAndLogLines() {
        List<String> lines = new ArrayList<>();
        refreshPanelButtonCache(true);
        if (cachedPanelButtons.isEmpty()) {
            lines.add("(geen emotes — open emote-tab en probeer opnieuw)");
            BotRuntime.logConsole("[Emote] Emote rescan: 0 knoppen");
            return lines;
        }
        Map<Integer, Integer> idCounts = new LinkedHashMap<>();
        for (Map.Entry<String, IWidget> e : cachedPanelButtons) {
            int id = safeId(e.getValue());
            idCounts.merge(id, 1, Integer::sum);
        }
        boolean sharedIds = idCounts.values().stream().anyMatch(c -> c > 1);
        for (Map.Entry<String, IWidget> e : cachedPanelButtons) {
            IWidget w = e.getValue();
            String name = e.getKey();
            int packed = safeId(w);
            int[] gc = unpack(Math.max(0, packed));
            Rectangle b = safeBounds(w);
            String bounds = b != null ? b.x + "," + b.y + " " + b.width + "x" + b.height : "?";
            lines.add(name + " → " + gc[0] + "," + gc[1]
                    + " id=" + packed + " canvas=" + bounds
                    + (sharedIds ? " (naam=key, id gedeeld)" : ""));
        }
        if (sharedIds) {
            lines.add("ℹ Meerdere emotes, zelfde Storm-id — bot klikt op NAAM, niet op id.");
        }
        BotRuntime.logConsole("[Emote] Emote rescan: " + cachedPanelButtons.size() + " knoppen (naam-index)");
        return lines;
    }

    public static void allowRescanOnce() {
        rescanAllowed = true;
    }

    public static boolean isRescanAllowed() {
        return rescanAllowed;
    }

    public static IWidget rescanFindOnce(String emoteName) {
        if (!rescanAllowed || emoteName == null || emoteName.isBlank()) {
            return null;
        }
        rescanAllowed = false;
        rescanAndLogLines();
        return findPerformButton(emoteName);
    }

    private static void refreshPanelButtonCache(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && !cachedPanelButtons.isEmpty() && now - panelCacheMs < PANEL_CACHE_TTL_MS) {
            return;
        }
        List<Map.Entry<String, IWidget>> list = collectNamedPerformButtons();
        cachedPanelButtons = list;
        panelCacheMs = now;
        if (!list.isEmpty()) {
            BotRuntime.logConsole("[Emote] Emote panel-cache: " + list.size() + " knoppen (by name)");
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
        for (Map.Entry<String, IWidget> e : byName.entrySet()) {
            list.add(e);
        }
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
        return live == null || live.equals(emoteLow);
    }

    /**
     * Grote panel/container — niet elke widget met id 216,2 (Storm deelt die id op knoppen).
     */
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
        if (w == null) {
            return null;
        }
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

    private static String formatRef(IWidget w) {
        int id = safeId(w);
        int[] gc = unpack(Math.max(0, id));
        return gc[0] + "," + gc[1] + " id=" + id;
    }

    private static void enqueueChildren(Deque<IWidget> q, IWidget w) {
        IWidget[] ch = safeChildren(w);
        if (ch != null) {
            for (IWidget c : ch) {
                if (c != null) {
                    q.add(c);
                }
            }
        }
    }

    private static int parsePackedId(String detail) {
        Matcher idm = ID_PATTERN.matcher(detail);
        if (idm.find()) {
            return Integer.parseInt(idm.group(1));
        }
        Matcher iface = IFACE_PATTERN.matcher(detail);
        if (iface.find()) {
            int group = Integer.parseInt(iface.group(1));
            int child = Integer.parseInt(iface.group(2));
            return (group << 16) | (child & 0xFFFF);
        }
        return -1;
    }

    private static int[] unpack(int packed) {
        return new int[]{packed >>> 16, packed & 0xFFFF};
    }

    private static String parseHumanLabel(String detail) {
        Matcher m = HUMAN_LABEL_PATTERN.matcher(detail);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static String parseNameFromSummary(String line) {
        if (line == null) {
            return null;
        }
        int idx = line.indexOf("name:");
        if (idx < 0) {
            return null;
        }
        return line.substring(idx + 5).trim();
    }

    private static String aliasFor(String emoteLow) {
        return "emote." + emoteLow;
    }

    private static String normalizeEmoteName(String name) {
        return Text.removeTags(name).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean widgetHasPerformAction(IWidget w) {
        try {
            return w != null && w.hasAction("Perform");
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
            String n = Text.removeTags(raw).toLowerCase(Locale.ROOT).trim();
            return n.isEmpty() ? null : n;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rectangle safeBounds(IWidget w) {
        try {
            return w.getBounds();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int safeId(IWidget w) {
        try {
            return w != null ? w.getId() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static IWidget[] safeChildren(IWidget w) {
        try {
            return w.getChildren();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
