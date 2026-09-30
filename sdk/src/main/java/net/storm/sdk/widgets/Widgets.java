package net.storm.sdk.widgets;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.widgets.IWidgets;
import net.storm.api.widgets.InterfaceAddress;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Storm {@code Widgets} — group/component/child lookup, visibility, ESC-close.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/widgets/Widgets.html">Storm Widgets</a>
 */
public final class Widgets {

    public static final IWidgets API = new Api();

    static {
        net.storm.api.Static.bindWidgets(API);
    }

    public Widgets() {
    }

    public static IWidget wrap(Widget widget) {
        return widget != null ? new RlWidget(widget) : null;
    }

    public static IWidget get(int packedId) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget w = c.getWidget(packedId);
            return wrap(w);
        }, null);
    }

    public static IWidget get(int group, int child) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget w = c.getWidget(group, child);
            return wrap(w);
        }, null);
    }

    /**
     * Collect widgets whose text contains {@code text} (case-insensitive).
     * Scans root widget children recursively (bounded depth).
     */
    public static List<IWidget> getAll(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        String needle = text.toLowerCase(Locale.ROOT);
        return Static.callOnClientThread(() -> {
            List<IWidget> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null) {
                return out;
            }
            Widget[] roots = c.getWidgetRoots();
            if (roots == null) {
                return out;
            }
            for (Widget root : roots) {
                collectMatching(root, needle, out, 0);
            }
            return out;
        }, new ArrayList<>());
    }

    private static void collectMatching(Widget w, String needleLower, List<IWidget> out, int depth) {
        if (w == null || depth > 12) {
            return;
        }
        String t = w.getText();
        if (t != null && t.toLowerCase(Locale.ROOT).contains(needleLower)) {
            out.add(wrap(w));
        }
        Widget[] children = w.getChildren();
        if (children != null) {
            for (Widget child : children) {
                collectMatching(child, needleLower, out, depth + 1);
            }
        }
        Widget[] staticChildren = w.getStaticChildren();
        if (staticChildren != null) {
            for (Widget child : staticChildren) {
                collectMatching(child, needleLower, out, depth + 1);
            }
        }
        Widget[] dynamic = null;
        try {
            dynamic = w.getDynamicChildren();
        } catch (Throwable ignored) {
        }
        if (dynamic != null) {
            for (Widget child : dynamic) {
                collectMatching(child, needleLower, out, depth + 1);
            }
        }
    }

    public static IWidget get(int group, int child, int grandchild) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget w = c.getWidget(group, child);
            if (w == null) {
                return null;
            }
            Widget[] kids = w.getChildren();
            if (kids == null) {
                kids = w.getDynamicChildren();
            }
            if (kids == null || grandchild < 0 || grandchild >= kids.length) {
                return null;
            }
            return wrap(kids[grandchild]);
        }, null);
    }

    public static List<IWidget> getAll(java.util.function.Predicate<IWidget> filter) {
        return Static.callOnClientThread(() -> {
            List<IWidget> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null) {
                return out;
            }
            Widget[] roots = c.getWidgetRoots();
            if (roots == null) {
                return out;
            }
            for (Widget root : roots) {
                collectFilter(root, filter, out, 0);
            }
            return out;
        }, new ArrayList<>());
    }

    public static IWidget getFirst(java.util.function.Predicate<IWidget> filter) {
        List<IWidget> all = getAll(filter);
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<IWidget> getAll(int group) {
        return getAll(group, null);
    }

    public static List<IWidget> getAll(int group, Predicate<IWidget> filter) {
        return Static.callOnClientThread(() -> {
            List<IWidget> out = new ArrayList<>();
            Client c = Static.getClient();
            if (c == null) {
                return out;
            }
            for (int id = 0; id < 1024; id++) {
                Widget w = c.getWidget(group, id);
                if (w == null) {
                    continue;
                }
                IWidget wrap = wrap(w);
                if (wrap != null && (filter == null || filter.test(wrap))) {
                    out.add(wrap);
                }
            }
            return out;
        }, new ArrayList<>());
    }

    public static IWidget get(int group, Predicate<IWidget> filter) {
        List<IWidget> all = getAll(group, filter);
        return all.isEmpty() ? null : all.get(0);
    }

    public static IWidget getChild(int component, int child) {
        int group = component >> 16;
        int id = component & 0xFFFF;
        return get(group, id, child);
    }

    public static boolean isVisible(IWidget widget) {
        return widget != null && !widget.isHidden();
    }

    public static boolean isVisible(int group, int child) {
        IWidget w = get(group, child);
        return isVisible(w);
    }

    public static boolean isVisible(int group, int id, int child) {
        return isVisible(get(group, id, child));
    }

    public static boolean isVisible(int component) {
        return isVisible(get(component));
    }

    @Deprecated(forRemoval = true)
    public static IWidget get(InterfaceAddress interfaceAddress) {
        return interfaceAddress == null ? null : get(interfaceAddress.getPackedId());
    }

    @Deprecated(forRemoval = true)
    public static boolean isVisible(InterfaceAddress interfaceAddress) {
        return interfaceAddress != null && isVisible(interfaceAddress.getPackedId());
    }

    public static List<IWidget> getChildren(IWidget widget, Predicate<IWidget> filter) {
        List<IWidget> out = new ArrayList<>();
        if (widget == null) {
            return out;
        }
        IWidget[] kids = widget.getChildren();
        if (kids == null) {
            return out;
        }
        for (IWidget k : kids) {
            if (k != null && (filter == null || filter.test(k))) {
                out.add(k);
            }
        }
        return out;
    }

    public static List<IWidget> getChildren(int group, int id, Predicate<IWidget> filter) {
        return getChildren(get(group, id), filter);
    }

    public static List<IWidget> getChildren(int group, int id, int child, Predicate<IWidget> filter) {
        return getChildren(get(group, id, child), filter);
    }

    public static List<IWidget> getChildren(int component, Predicate<IWidget> filter) {
        return getChildren(get(component), filter);
    }

    @Deprecated(forRemoval = true)
    public static List<IWidget> getChildren(InterfaceAddress interfaceAddress, Predicate<IWidget> filter) {
        if (interfaceAddress == null) {
            return new ArrayList<>();
        }
        return getChildren(interfaceAddress.getPackedId(), filter);
    }

    public static void closeInterfaces() {
        Keyboard.pressKey(KeyEvent.VK_ESCAPE);
    }

    private static final class Api implements IWidgets {
        @Override
        public IWidget get(int group, int id) {
            return Widgets.get(group, id);
        }

        @Override
        public IWidget get(int group, int id, int child) {
            return Widgets.get(group, id, child);
        }

        @Override
        public IWidget get(int component) {
            return Widgets.get(component);
        }

        @Override
        public IWidget get(int group, Predicate<IWidget> filter) {
            return Widgets.get(group, filter);
        }

        @Override
        public IWidget getChild(int component, int child) {
            return Widgets.getChild(component, child);
        }

        @Override
        public List<IWidget> getAll(int group) {
            return Widgets.getAll(group);
        }

        @Override
        public boolean isVisible(IWidget widget) {
            return Widgets.isVisible(widget);
        }

        @Override
        public boolean isVisible(int group, int id) {
            return Widgets.isVisible(group, id);
        }

        @Override
        public boolean isVisible(int group, int id, int child) {
            return Widgets.isVisible(group, id, child);
        }

        @Override
        public boolean isVisible(int component) {
            return Widgets.isVisible(component);
        }

        @Override
        public List<IWidget> getChildren(IWidget widget, Predicate<IWidget> filter) {
            return Widgets.getChildren(widget, filter);
        }

        @Override
        public List<IWidget> getChildren(int group, int id, Predicate<IWidget> filter) {
            return Widgets.getChildren(group, id, filter);
        }

        @Override
        public List<IWidget> getChildren(int group, int id, int child, Predicate<IWidget> filter) {
            return Widgets.getChildren(group, id, child, filter);
        }

        @Override
        public List<IWidget> getChildren(int component, Predicate<IWidget> filter) {
            return Widgets.getChildren(component, filter);
        }

        @Override
        public void closeInterfaces() {
            Widgets.closeInterfaces();
        }
    }

    private static void collectFilter(Widget w, java.util.function.Predicate<IWidget> filter,
                                      List<IWidget> out, int depth) {
        if (w == null || depth > 12) {
            return;
        }
        IWidget wrap = wrap(w);
        if (wrap != null && (filter == null || filter.test(wrap))) {
            out.add(wrap);
        }
        Widget[] children = w.getChildren();
        if (children != null) {
            for (Widget child : children) {
                collectFilter(child, filter, out, depth + 1);
            }
        }
        Widget[] dyn = null;
        try {
            dyn = w.getDynamicChildren();
        } catch (Throwable ignored) {
        }
        if (dyn != null) {
            for (Widget child : dyn) {
                collectFilter(child, filter, out, depth + 1);
            }
        }
    }
}
