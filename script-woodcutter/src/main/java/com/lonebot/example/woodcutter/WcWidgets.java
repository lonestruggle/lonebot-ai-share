package com.lonebot.example.woodcutter;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.mouse.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shop iface 819 — RL widgets + dynamicChildren + menu Buy-1 (CombatBot/Vampire shop-stijl).
 */
final class WcWidgets {

    private static final Logger log = LoggerFactory.getLogger(WcWidgets.class);

    static final class ShopHit {
        final int packedId;
        final int index;
        final int itemId;
        final Rectangle bounds;
        final String[] actions;
        final String label;

        ShopHit(int packedId, int index, int itemId, Rectangle bounds, String[] actions, String label) {
            this.packedId = packedId;
            this.index = index;
            this.itemId = itemId;
            this.bounds = bounds;
            this.actions = actions != null ? actions : new String[0];
            this.label = label != null ? label : "";
        }
    }

    private WcWidgets() {
    }

    static boolean clickRelaxed(int group, int child, int minW, int minH) {
        Point p = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget w = c.getWidget(group, child);
            Rectangle b = boundsIfClickable(w, minW, minH);
            if (b == null) {
                // Nested / dynamic child sometimes holds the real clickbox
                b = findNestedClickable(w, minW, minH);
            }
            return b != null ? randomInBounds(b) : null;
        }, null);
        if (p == null) {
            log.info("[WcWidgets] click {}:{} — geen bounds", group, child);
            return false;
        }
        boolean ok = MouseManager.interactAt(p);
        log.info("[WcWidgets] click {}:{} @{},{} ok={}", group, child, p.getX(), p.getY(), ok);
        return ok;
    }

    static boolean clickWidgetBounds(Rectangle b) {
        if (b == null || b.width < 4 || b.height < 4) {
            return false;
        }
        return MouseManager.interactAt(randomInBounds(b));
    }

    static boolean hasBounds(int group, int child, int minW, int minH) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget w = c.getWidget(group, child);
            return boundsIfClickable(w, minW, minH) != null
                    || findNestedClickable(w, minW, minH) != null;
        }, false));
    }

    static String allText(int group, int child) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            appendTextRecursive(c.getWidget(group, child), sb, 0);
            return clean(sb.toString());
        }, "");
    }

    /** Scan hele iface (alle children + nested dynamic) voor tekst. */
    static String scanIfaceText(int group) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int child = 0; child <= 80; child++) {
                Widget w = c.getWidget(group, child);
                if (w != null) {
                    appendTextRecursive(w, sb, 0);
                }
            }
            return clean(sb.toString());
        }, "");
    }

    static ShopHit findKitHit(int group, int itemId, String labelNeedle, int minW, int minH) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            List<ShopHit> hits = new ArrayList<>();
            for (int child = 0; child <= 80; child++) {
                collectHits(c.getWidget(group, child), itemId, labelNeedle, minW, minH, hits, 0);
            }
            // Prefer exact itemId (>0) — nooit id=-1 fallback (dat was Select/View-slot)
            for (ShopHit h : hits) {
                if (h.itemId == itemId && itemId > 0) {
                    return h;
                }
            }
            for (ShopHit h : hits) {
                if (h.itemId > 0 && labelMatches(h.label, labelNeedle)) {
                    return h;
                }
            }
            return null;
        }, null);
    }

    private static boolean labelMatches(String label, String needle) {
        if (label == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return label.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    static Rectangle findShopItemBounds(int group, int itemId, String labelNeedle, int minW, int minH) {
        ShopHit h = findKitHit(group, itemId, labelNeedle, minW, minH);
        return h != null ? h.bounds : null;
    }

    /**
     * Buy-1 via menu-invoke op kit-widget (betrouwbaarder dan select+klik knop).
     */
    static boolean invokeBuy1OnHit(ShopHit hit) {
        if (hit == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            String option = pickBuyOption(hit.actions);
            if (option == null) {
                // Actions soms leeg op parent — probeer standaard shop Buy-1 op index
                option = "Buy 1";
            }
            int opIdx = indexOfAction(hit.actions, option);
            int identifier = opIdx >= 0 ? opIdx + 1 : 1;
            String target = hit.label.isEmpty() ? "Forestry kit" : hit.label;
            try {
                c.menuAction(hit.index, hit.packedId, MenuAction.CC_OP, identifier, hit.itemId,
                        option, target);
                log.info("[WcWidgets] menuAction Buy idx={} packed={} id={} item={} opt={}",
                        hit.index, hit.packedId, identifier, hit.itemId, option);
                return true;
            } catch (Throwable t) {
                return MenuInteract.invokeMenu(option, target, identifier, MenuAction.CC_OP.getId(),
                        hit.index, hit.packedId);
            }
        }, false);
        if (Boolean.TRUE.equals(ok)) {
            return true;
        }
        // Canvas fallback
        return clickWidgetBounds(hit.bounds);
    }

    /** Diagnose: wat zit er in shop iface? */
    static String diagnoseShop(int group, int kitItemId) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "no client";
            }
            StringBuilder sb = new StringBuilder();
            int withItem = 0;
            int withKitLabel = 0;
            for (int child = 0; child <= 80; child++) {
                Widget w = c.getWidget(group, child);
                if (w == null) {
                    continue;
                }
                List<ShopHit> hits = new ArrayList<>();
                collectHits(w, kitItemId, "forestry kit", 8, 8, hits, 0);
                for (ShopHit h : hits) {
                    if (h.itemId == kitItemId) {
                        withItem++;
                    }
                    if (h.label.toLowerCase(Locale.ROOT).contains("forestry kit")) {
                        withKitLabel++;
                    }
                    if (sb.length() < 180) {
                        sb.append("[c").append(child)
                                .append(" id=").append(h.itemId)
                                .append(" idx=").append(h.index)
                                .append(" b=").append(h.bounds != null ? h.bounds.width + "x" + h.bounds.height : "?")
                                .append(" a=").append(java.util.Arrays.toString(h.actions))
                                .append("] ");
                    }
                }
            }
            return "hits item=" + withItem + " label=" + withKitLabel + " " + sb;
        }, "diag fail");
    }

    private static void collectHits(Widget w, int itemId, String needle, int minW, int minH,
                                    List<ShopHit> out, int depth) {
        if (w == null || depth > 8) {
            return;
        }
        try {
            int id = w.getItemId();
            String label = (clean(w.getName()) + " " + clean(w.getText())).trim();
            String low = label.toLowerCase(Locale.ROOT);
            boolean matchId = id == itemId;
            boolean matchLabel = needle != null && !needle.isEmpty() && low.contains(needle.toLowerCase(Locale.ROOT));
            if (matchId || matchLabel) {
                Rectangle b = boundsIfClickable(w, minW, minH);
                if (b == null) {
                    b = boundsIfClickable(w, 4, 4);
                }
                if (b != null) {
                    String[] actions = w.getActions();
                    out.add(new ShopHit(w.getId(), w.getIndex(), id, b, actions, label));
                }
            }
        } catch (Throwable ignored) {
        }
        for (Widget child : allChildArrays(w)) {
            collectHits(child, itemId, needle, minW, minH, out, depth + 1);
        }
    }

    private static Widget[] allChildArrays(Widget w) {
        List<Widget> all = new ArrayList<>();
        addAll(all, safeChildren(w));
        addAll(all, safeStatic(w));
        addAll(all, safeDynamic(w));
        return all.toArray(new Widget[0]);
    }

    private static void addAll(List<Widget> list, Widget[] arr) {
        if (arr == null) {
            return;
        }
        for (Widget w : arr) {
            if (w != null) {
                list.add(w);
            }
        }
    }

    private static Widget[] safeChildren(Widget w) {
        try {
            return w.getChildren();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Widget[] safeStatic(Widget w) {
        try {
            return w.getStaticChildren();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Widget[] safeDynamic(Widget w) {
        try {
            return w.getDynamicChildren();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Rectangle findNestedClickable(Widget w, int minW, int minH) {
        if (w == null) {
            return null;
        }
        for (Widget child : allChildArrays(w)) {
            Rectangle b = boundsIfClickable(child, minW, minH);
            if (b != null) {
                return b;
            }
            b = findNestedClickable(child, minW, minH);
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    private static Rectangle boundsIfClickable(Widget w, int minW, int minH) {
        if (w == null) {
            return null;
        }
        try {
            Rectangle b = w.getBounds();
            if (b == null || b.width < minW || b.height < minH) {
                return null;
            }
            if (b.x + b.width < 0 || b.y + b.height < 0) {
                return null;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Point randomInBounds(Rectangle b) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x = b.x + 2 + r.nextInt(Math.max(1, b.width - 4));
        int y = b.y + 2 + r.nextInt(Math.max(1, b.height - 4));
        return new Point(x, y);
    }

    private static void appendTextRecursive(Widget w, StringBuilder sb, int depth) {
        if (w == null || depth > 8) {
            return;
        }
        try {
            String name = clean(w.getName());
            String text = clean(w.getText());
            if (!name.isEmpty()) {
                sb.append(name).append(' ');
            }
            if (!text.isEmpty()) {
                sb.append(text).append(' ');
            }
        } catch (Throwable ignored) {
        }
        for (Widget child : allChildArrays(w)) {
            appendTextRecursive(child, sb, depth + 1);
        }
    }

    private static String pickBuyOption(String[] actions) {
        if (actions == null) {
            return null;
        }
        for (String a : actions) {
            if (a == null) {
                continue;
            }
            String low = a.toLowerCase(Locale.ROOT);
            if (low.equals("buy 1") || low.equals("buy-1") || low.equals("buy1")) {
                return a;
            }
        }
        for (String a : actions) {
            if (a != null && a.toLowerCase(Locale.ROOT).startsWith("buy")) {
                return a;
            }
        }
        return null;
    }

    private static int indexOfAction(String[] actions, String option) {
        if (actions == null || option == null) {
            return -1;
        }
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && actions[i].equalsIgnoreCase(option)) {
                return i;
            }
        }
        return -1;
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return Text.removeTags(s).replace('\n', ' ').trim();
    }
}
