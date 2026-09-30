package net.runelite.client.plugins.lonebot.dev;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.storm.api.widgets.Tab;
import net.storm.sdk.widgets.Tabs;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Item-IDs op inventory-vakjes — CombatBot {@code InventoryItemIdOverlay}.
 */
public class InventoryItemIdOverlay extends Overlay {

    private static final int EMPTY_SLOT_ID = 6512;

    private final Client client;
    private final LoneBotConfig config;

    @Inject
    public InventoryItemIdOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (config == null || !config.devInventoryItemIds()
                || client == null || client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        if (!Tabs.isOpen(Tab.INVENTORY)) {
            return null;
        }

        Widget inventory = client.getWidget(WidgetInfo.INVENTORY);
        if (inventory == null || inventory.isHidden()) {
            return null;
        }
        Widget[] children = inventory.getChildren();
        if (children == null || children.length == 0) {
            return null;
        }

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));

        for (Widget slot : children) {
            if (slot == null || slot.isHidden()) {
                continue;
            }
            int itemId = slot.getItemId();
            if (itemId <= 0 || itemId == EMPTY_SLOT_ID) {
                continue;
            }
            Rectangle bounds = slot.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
                continue;
            }
            String label = String.valueOf(itemId);
            int qty = slot.getItemQuantity();
            if (qty > 1) {
                label += "x" + qty;
            }
            int textW = g.getFontMetrics().stringWidth(label);
            int textH = g.getFontMetrics().getHeight();
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(bounds.x, bounds.y, Math.min(textW + 3, bounds.width), textH);
            g.setColor(new Color(140, 255, 160));
            g.drawString(label, bounds.x + 1, bounds.y + textH - 2);
        }
        return null;
    }
}
