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
 * Giants debug — chip = verbergen. Verborgen bij open bank/GE.
 */
public class GiantsDebugOverlay extends MinimizableStatusOverlay {

    private final LoneBotConfig config;
    private final PanelComponent fallback = new PanelComponent();

    @Inject
    public GiantsDebugOverlay(LoneBotConfig config) {
        this.config = config;
        applyDefaultPosition(OverlayPosition.TOP_RIGHT);
        fallback.setBackgroundColor(new Color(20, 28, 18, 170));
    }

    @Override
    public String overlayId() {
        return "giants";
    }

    @Override
    protected Color chipColor() {
        return new Color(140, 190, 80);
    }

    @Override
    protected boolean isEnabled() {
        return false; // merged into LoneBotControlPaint
    }

    @Override
    protected Dimension renderExpanded(Graphics2D graphics) {
        BotRuntime.DebugPaint paint = BotRuntime.giantsDebugPaint;
        if (paint != null) {
            try {
                return paint.render(graphics);
            } catch (Throwable ignored) {
            }
        }
        fallback.getChildren().clear();
        fallback.getChildren().add(TitleComponent.builder()
                .text("Giants v" + BotRuntime.giantsPluginVersion)
                .color(new Color(180, 220, 120))
                .build());
        fallback.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(BotRuntime.giantsStatus != null ? BotRuntime.giantsStatus : "-")
                .leftColor(new Color(200, 200, 200))
                .rightColor(Color.WHITE)
                .build());
        return fallback.render(graphics);
    }
}
