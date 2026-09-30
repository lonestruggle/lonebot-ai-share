package net.runelite.client.plugins.lonebot;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.storm.sdk.items.Bank;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * Status-panel met verberg-chip. Nieuwe overlays: extend deze class, implementeer
 * {@link #overlayId()}, {@link #chipColor()}, {@link #isEnabled()}, {@link #renderExpanded}.
 * Chip-klik wordt centraal afgehandeld — geen extra muis-wiring.
 */
public abstract class MinimizableStatusOverlay extends Overlay implements OverlayMinimizeStore.Chip {

    private Dimension lastSize = new Dimension(0, 0);

    protected MinimizableStatusOverlay() {
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_MED);
        try {
            setMovable(true);
        } catch (Throwable ignored) {
        }
        OverlayMinimizeStore.register(this);
    }

    @Override
    public abstract String overlayId();

    protected abstract Color chipColor();

    protected abstract boolean isEnabled();

    /** Volledige panel-inhoud (niet geminimaliseerd). */
    protected abstract Dimension renderExpanded(Graphics2D g);

    /** Bank/GE open → panel weg, chip blijft als geminimaliseerd. */
    protected boolean hideWhenBankOrGe() {
        return true;
    }

    /** True als deze overlay zelf al een title-chip tekent (LoneBot control). */
    protected boolean paintsOwnTitleChip() {
        return false;
    }

    protected Dimension renderMinimized(Graphics2D g) {
        return OverlayChip.drawMinimized(g, chipColor());
    }

    @Override
    public final Dimension render(Graphics2D graphics) {
        OverlayMinimizeStore.register(this);
        if (!isEnabled()) {
            lastSize = new Dimension(0, 0);
            return null;
        }
        if (OverlayMinimizeStore.isMinimized(overlayId())) {
            Dimension d = renderMinimized(graphics);
            lastSize = d != null ? d : new Dimension(0, 0);
            return d;
        }
        if (hideWhenBankOrGe() && bankOrGeOpen()) {
            lastSize = new Dimension(0, 0);
            return null;
        }
        Dimension d = renderExpanded(graphics);
        if (d != null && !paintsOwnTitleChip()) {
            OverlayChip.drawTitle(graphics, chipColor());
        }
        lastSize = d != null ? d : new Dimension(0, 0);
        return d;
    }

    @Override
    public boolean containsChip(int canvasX, int canvasY) {
        Rectangle bounds = resolveBoundsRect();
        if (bounds == null) {
            return false;
        }
        if (OverlayMinimizeStore.isMinimized(overlayId())) {
            return bounds.contains(canvasX, canvasY);
        }
        Rectangle chip = titleChipHitbox(bounds);
        return chip.contains(canvasX, canvasY);
    }

    /** Override voor custom chip-positie (control panel title). */
    protected Rectangle titleChipHitbox(Rectangle bounds) {
        return new Rectangle(bounds.x + 2, bounds.y + 2, 20, 20);
    }

    protected void applyDefaultPosition(OverlayPosition position) {
        if (position != null) {
            setPosition(position);
        }
    }

    private Rectangle resolveBoundsRect() {
        if (lastSize.width <= 0 || lastSize.height <= 0) {
            return null;
        }
        Rectangle bounds = getBounds();
        if (bounds != null && bounds.width > 0 && bounds.height > 0) {
            return bounds;
        }
        Point loc = getPreferredLocation();
        if (loc == null) {
            return null;
        }
        return new Rectangle(loc.x, loc.y, lastSize.width, lastSize.height);
    }

    private static boolean bankOrGeOpen() {
        try {
            if (Bank.isOpen()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return net.storm.sdk.items.GrandExchange.isOpen();
        } catch (Throwable t) {
            return false;
        }
    }
}
