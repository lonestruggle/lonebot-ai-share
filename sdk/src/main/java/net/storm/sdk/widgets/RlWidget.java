package net.storm.sdk.widgets;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.mouse.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;

/**
 * Storm-style widget wrapper — {@link #interact} via {@code Client.menuAction} (CC_OP),
 * mouse fallback. Zie Storm domain widgets Javadoc.
 */
final class RlWidget implements IWidget {

    private static final Logger log = LoggerFactory.getLogger(RlWidget.class);

    private final Widget widget;

    RlWidget(Widget widget) {
        this.widget = widget;
    }

    Widget raw() {
        return widget;
    }

    @Override
    public int getId() {
        return widget.getId();
    }

    @Override
    public int getItemId() {
        return widget.getItemId();
    }

    @Override
    public String getName() {
        return widget.getName() != null ? widget.getName() : "";
    }

    @Override
    public String getText() {
        return widget.getText() != null ? widget.getText() : "";
    }

    @Override
    public boolean isHidden() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            try {
                return widget == null || widget.isHidden();
            } catch (Throwable t) {
                return true;
            }
        }, true));
    }

    @Override
    public Rectangle getBounds() {
        return widget.getBounds();
    }

    @Override
    public boolean hasAction(String action) {
        if (widget == null || action == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            try {
                String[] actions = widget.getActions();
                if (actions == null) {
                    return false;
                }
                for (String a : actions) {
                    if (a != null && stripCol(a).equalsIgnoreCase(action)) {
                        return true;
                    }
                }
                return false;
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }

    @Override
    public boolean interact(String action) {
        if (widget == null || isHidden()) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> interactOnClient(action), false);
        if (Boolean.TRUE.equals(ok)) {
            return true;
        }
        try {
            net.runelite.api.Point p = ClickPoints.forWidgetOnClient(widget);
            if (p != null) {
                return MouseManager.interactAt(p, true);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean interactOnClient(String action) {
        Client c = Static.getClient();
        if (c == null) {
            return false;
        }
        String[] actions = widget.getActions();
        int idx = indexOfAction(actions, action);
        String opt;
        int identifier;
        if (idx >= 0) {
            opt = stripCol(actions[idx]);
            identifier = idx + 1;
        } else if (action != null && !action.isBlank()) {
            opt = action.trim();
            identifier = 1;
        } else {
            opt = (actions != null && actions.length > 0 && actions[0] != null)
                    ? stripCol(actions[0]) : "Select";
            identifier = 1;
        }
        MenuAction menu = identifier > 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
        int p0 = widget.getIndex();
        if (p0 < 0) {
            p0 = -1;
        }
        int packed = widget.getId();
        int itemId = widget.getItemId() > 0 ? widget.getItemId() : 0;
        String target = getName();
        if (target.isEmpty()) {
            target = getText();
        }
        try {
            c.menuAction(p0, packed, menu, identifier, itemId, opt, target != null ? target : "");
            log.debug("[Widget] interact {} → {} packed={} p0={} id={}", opt, target, packed, p0, identifier);
            return true;
        } catch (Throwable t) {
            return MenuInteract.invokeMenu(opt, target != null ? target : "", identifier, menu.getId(),
                    p0, packed, itemId);
        }
    }

    private static int indexOfAction(String[] actions, String action) {
        if (actions == null || action == null || action.isBlank()) {
            return -1;
        }
        String want = stripCol(action);
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && stripCol(actions[i]).equalsIgnoreCase(want)) {
                return i;
            }
        }
        return -1;
    }

    private static String stripCol(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<col=[^>]*>", "").replace("</col>", "").trim();
    }

    @Override
    public int getIndex() {
        return widget.getIndex();
    }

    @Override
    public IWidget[] getChildren() {
        Widget[] children = widget.getChildren();
        if (children == null) {
            return new IWidget[0];
        }
        IWidget[] out = new IWidget[children.length];
        for (int i = 0; i < children.length; i++) {
            out[i] = children[i] != null ? new RlWidget(children[i]) : null;
        }
        return out;
    }

    @Override
    public IWidget[] getDynamicChildren() {
        try {
            Widget[] children = widget.getDynamicChildren();
            return wrapArray(children);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public IWidget[] getStaticChildren() {
        try {
            Widget[] children = widget.getStaticChildren();
            return wrapArray(children);
        } catch (Throwable t) {
            return null;
        }
    }

    private static IWidget[] wrapArray(Widget[] children) {
        if (children == null) {
            return null;
        }
        IWidget[] out = new IWidget[children.length];
        for (int i = 0; i < children.length; i++) {
            out[i] = children[i] != null ? new RlWidget(children[i]) : null;
        }
        return out;
    }
}
