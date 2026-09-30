package net.storm.sdk.entities;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.UiClickGuard;
import net.storm.sdk.interact.mouse.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a RuneLite {@link TileItem}. Take is menu-invoke only (no LMB — that chops trees).
 */
final class RlTileItem implements ITileItem {

    private static final Logger log = LoggerFactory.getLogger(RlTileItem.class);

    private final TileItem item;
    private final WorldPoint worldPoint;

    RlTileItem(TileItem item, WorldPoint worldPoint) {
        this.item = item;
        this.worldPoint = worldPoint;
    }

    TileItem raw() {
        return item;
    }

    @Override
    public int getId() {
        return item.getId();
    }

    @Override
    public String getName() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "";
            }
            ItemComposition comp = c.getItemDefinition(item.getId());
            return comp != null && comp.getName() != null ? comp.getName() : "";
        }, "");
    }

    @Override
    public int getQuantity() {
        return item.getQuantity();
    }

    @Override
    public WorldPoint getWorldLocation() {
        return worldPoint;
    }

    @Override
    public boolean canPick() {
        return hasAction("Take") || hasAction("Take-all") || (item != null && item.getId() >= 0);
    }

    @Override
    public boolean pickup() {
        return interact("Take");
    }

    @Override
    public Point getCanvasPoint() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || worldPoint == null) {
                return null;
            }
            LocalPoint lp = LocalPoint.fromWorld(c, worldPoint);
            if (lp == null) {
                return null;
            }
            if (Perspective.getCanvasTilePoly(c, lp) == null) {
                return null;
            }
            Point p = Perspective.localToCanvas(c, lp, c.getPlane());
            if (p == null || p.getX() < 8 || p.getY() < 8) {
                return null;
            }
            return p;
        }, null);
    }

    @Override
    public boolean hasAction(String action) {
        if (action == null) {
            return false;
        }
        return "Take".equalsIgnoreCase(action) || "Take-all".equalsIgnoreCase(action);
    }

    @Override
    public boolean interact(String action) {
        if (action == null) {
            return false;
        }
        // Take = GROUND_ITEM invoke — nooit LMB. LMB op een tile achter een boom = Chop down.
        if ("Take".equalsIgnoreCase(action) || "Take-all".equalsIgnoreCase(action)
                || "Pick-up".equalsIgnoreCase(action)) {
            if (tryInvokeTake()) {
                return true;
            }
            log.debug("[RlTileItem] Take invoke fail id={} @{}", item.getId(), worldPoint);
            return false;
        }
        Point canvas = getCanvasPoint();
        if (canvas == null) {
            log.debug("[RlTileItem] off-screen id={} @{}", item.getId(), worldPoint);
            return false;
        }
        if (UiClickGuard.isBlocked(canvas)) {
            return false;
        }
        return MouseManager.interactAt(canvas);
    }

    private boolean tryInvokeTake() {
        try {
            return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
                Client c = Static.getClient();
                if (c == null || worldPoint == null || item == null) {
                    return false;
                }
                int itemId = item.getId();
                LocalPoint lp = null;
                try {
                    net.runelite.api.WorldView wv = c.findWorldViewFromWorldPoint(worldPoint);
                    if (wv == null) {
                        wv = c.getTopLevelWorldView();
                    }
                    if (wv != null) {
                        lp = LocalPoint.fromWorld(wv, worldPoint);
                    }
                } catch (Throwable ignored) {
                }
                if (lp == null) {
                    lp = LocalPoint.fromWorld(c, worldPoint);
                }
                if (lp == null) {
                    return false;
                }
                String name = "";
                try {
                    ItemComposition comp = c.getItemDefinition(itemId);
                    if (comp != null && comp.getName() != null) {
                        name = comp.getName();
                    }
                } catch (Throwable ignored) {
                }
                int sx = lp.getSceneX();
                int sy = lp.getSceneY();
                MenuAction[] ops = {
                        MenuAction.GROUND_ITEM_THIRD_OPTION,
                        MenuAction.GROUND_ITEM_FIRST_OPTION,
                        MenuAction.GROUND_ITEM_SECOND_OPTION,
                        MenuAction.GROUND_ITEM_FOURTH_OPTION,
                        MenuAction.GROUND_ITEM_FIFTH_OPTION
                };
                for (MenuAction op : ops) {
                    if (MenuInteract.invokeMenuAction(sx, sy, op.getId(), itemId, itemId, -1, "Take", name)) {
                        return true;
                    }
                }
                return false;
            }, false));
        } catch (Throwable t) {
            return false;
        }
    }
}
