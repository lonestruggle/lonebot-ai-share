package net.runelite.client.plugins.lonebot;

import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.storm.sdk.bot.BotRuntime;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Cow Combat canvas. Chip = verbergen.
 */
public class CowDebugOverlay extends MinimizableStatusOverlay {

    private final LoneBotConfig config;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public CowDebugOverlay(LoneBotConfig config) {
        this.config = config;
        applyDefaultPosition(OverlayPosition.BOTTOM_LEFT);
        panel.setBackgroundColor(new Color(40, 30, 10, 170));
    }

    @Override
    public String overlayId() {
        return "cow";
    }

    @Override
    protected Color chipColor() {
        return new Color(200, 160, 60);
    }

    @Override
    protected boolean isEnabled() {
        return false; // merged into LoneBotControlPaint
    }

    @Override
    protected Dimension renderExpanded(Graphics2D graphics) {
        BotRuntime.DebugPaint paint = BotRuntime.cowDebugPaint;
        if (paint != null) {
            try {
                return paint.render(graphics);
            } catch (Throwable ignored) {
            }
        }
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder()
                .text("Cow Combat")
                .color(new Color(255, 200, 100))
                .build());
        panel.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(trim(BotRuntime.cowStatus, 34))
                .leftColor(new Color(200, 200, 200))
                .rightColor(Color.WHITE)
                .build());
        panel.getChildren().add(LineComponent.builder()
                .left("Ticks")
                .right(String.valueOf(BotRuntime.cowLoopTicks))
                .build());
        return panel.render(graphics);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "-";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
