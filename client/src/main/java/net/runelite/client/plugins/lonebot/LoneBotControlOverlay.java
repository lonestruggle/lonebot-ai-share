package net.runelite.client.plugins.lonebot;

import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * CombatBotOverlay-equivalent: LoneBot control panel op canvas.
 * Chip = verbergen (via {@link MinimizableStatusOverlay}).
 */
public class LoneBotControlOverlay extends MinimizableStatusOverlay {

    private static volatile LoneBotControlOverlay activeInstance;

    private final LoneBotConfig config;
    private final LoneBotControlPaint paint = new LoneBotControlPaint();

    @Inject
    public LoneBotControlOverlay(LoneBotConfig config) {
        this.config = config;
        this.paint.setConfig(config);
        setPosition(OverlayPosition.TOP_LEFT);
        setPreferredLocation(new Point(10, 30));
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_HIGH);
        try {
            setSnappable(true);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String overlayId() {
        return "control";
    }

    @Override
    protected Color chipColor() {
        return paint.chipColor();
    }

    @Override
    protected boolean isEnabled() {
        return this == activeInstance && (config == null || config.statusOverlay());
    }

    @Override
    protected boolean hideWhenBankOrGe() {
        return false;
    }

    @Override
    protected boolean paintsOwnTitleChip() {
        return true;
    }

    @Override
    protected Dimension renderExpanded(Graphics2D g) {
        return paint.render(g, false);
    }

    @Override
    protected Dimension renderMinimized(Graphics2D g) {
        return paint.render(g, true);
    }

    @Override
    protected Rectangle titleChipHitbox(Rectangle bounds) {
        return new Rectangle(bounds.x + 4, bounds.y + 10, 24, 24);
    }

    public LoneBotControlPaint getPaint() {
        return paint;
    }

    static boolean hitWorldWalkerStop(int canvasX, int canvasY) {
        LoneBotControlOverlay inst = activeInstance;
        if (inst == null) {
            return false;
        }
        return WorldWalkerStopHit.containsCanvas(canvasX, canvasY, inst.getBounds());
    }

    /** Push status vanuit remote/bootstrap zonder inject-cycle. */
    public static void pushStatus(String status) {
        LoneBotControlOverlay inst = activeInstance;
        if (inst != null && status != null && !status.isBlank()) {
            inst.paint.setCurrentStatus(status);
        }
    }

    public void register(net.runelite.client.ui.overlay.OverlayManager overlayManager) {
        LoneBotControlOverlay previous = activeInstance;
        if (previous != null && previous != this) {
            overlayManager.remove(previous);
        }
        activeInstance = this;
        overlayManager.add(this);
    }

    public void unregister(net.runelite.client.ui.overlay.OverlayManager overlayManager) {
        if (activeInstance == this) {
            overlayManager.remove(this);
            activeInstance = null;
        }
    }
}
