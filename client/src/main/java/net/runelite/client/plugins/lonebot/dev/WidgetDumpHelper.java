package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import net.storm.sdk.bot.BotRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Widget-dump naar LoneBot Console — zoals CombatBot {@code WidgetDebugHelper}.
 */
public final class WidgetDumpHelper {

    private static final int MAX_GROUP = 700;
    private static final int MAX_CHILD = 80;

    private WidgetDumpHelper() {
    }

    public static int dumpVisible(Client client, String filter, int maxLines) {
        if (client == null) {
            BotRuntime.logConsole("[WidgetDump] geen client");
            return 0;
        }
        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        boolean filterOn = !f.isEmpty();
        List<String> lines = new ArrayList<>();
        long nodes = 0;
        outer:
        for (int g = 0; g <= MAX_GROUP; g++) {
            for (int c = 0; c <= MAX_CHILD; c++) {
                Widget root;
                try {
                    root = client.getWidget(g, c);
                } catch (Throwable t) {
                    continue;
                }
                if (root == null) {
                    continue;
                }
                visit(root, g, c, f, filterOn, lines, maxLines);
                nodes++;
                if (lines.size() >= maxLines) {
                    break outer;
                }
            }
        }
        BotRuntime.logConsole("[WidgetDump] ========== " + lines.size()
                + " regels (nodes~" + nodes + ") ==========");
        for (String line : lines) {
            BotRuntime.logConsole("[WidgetDump] " + line);
        }
        BotRuntime.logConsole("[WidgetDump] ========== end ==========");
        return lines.size();
    }

    private static void visit(Widget w, int g, int c, String f, boolean filterOn,
                              List<String> lines, int maxLines) {
        if (w == null || lines.size() >= maxLines) {
            return;
        }
        try {
            if (w.isHidden()) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }
        String name = clean(w.getName());
        String text = clean(w.getText());
        String actions = formatActions(w);
        int itemId = 0;
        try {
            itemId = w.getItemId();
        } catch (Throwable ignored) {
        }
        if (!interesting(name, text, actions, itemId)) {
            // children toch scannen
        } else {
            int packed = w.getId();
            int ig = packed > 0 ? (packed >>> 16) : g;
            int ic = packed > 0 ? (packed & 0xFFFF) : c;
            String line = String.format(Locale.ROOT,
                    "iface=%d,%d id=%d name=%s text=%s item=%d actions=[%s]",
                    ig, ic, packed, trunc(name, 40), trunc(text, 40), itemId, trunc(actions, 50));
            if (!filterOn || line.toLowerCase(Locale.ROOT).contains(f)) {
                lines.add(line);
            }
        }
        Widget[] kids = w.getDynamicChildren();
        if (kids != null) {
            for (Widget k : kids) {
                visit(k, g, c, f, filterOn, lines, maxLines);
                if (lines.size() >= maxLines) {
                    return;
                }
            }
        }
        kids = w.getStaticChildren();
        if (kids != null) {
            for (Widget k : kids) {
                visit(k, g, c, f, filterOn, lines, maxLines);
                if (lines.size() >= maxLines) {
                    return;
                }
            }
        }
        kids = w.getChildren();
        if (kids != null) {
            for (Widget k : kids) {
                visit(k, g, c, f, filterOn, lines, maxLines);
                if (lines.size() >= maxLines) {
                    return;
                }
            }
        }
    }

    private static boolean interesting(String name, String text, String actions, int itemId) {
        return (name != null && !name.isEmpty())
                || (text != null && !text.isEmpty())
                || (actions != null && !actions.isEmpty())
                || itemId > 0;
    }

    private static String formatActions(Widget w) {
        try {
            String[] a = w.getActions();
            if (a == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String x : a) {
                if (x == null || x.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append(x);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return Text.removeTags(s).replace('\n', ' ').trim();
    }

    private static String trunc(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
