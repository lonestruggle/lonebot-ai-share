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
 * Imp debug — chip = verbergen. Verborgen bij open bank/GE.
 */
public class ImpDebugOverlay extends MinimizableStatusOverlay {

    private final LoneBotConfig config;
    private final PanelComponent fallback = new PanelComponent();

    @Inject
    public ImpDebugOverlay(LoneBotConfig config) {
        this.config = config;
        applyDefaultPosition(OverlayPosition.TOP_RIGHT);
        fallback.setBackgroundColor(new Color(0, 0, 0, 170));
    }

    @Override
    public String overlayId() {
        return "imp";
    }

    @Override
    protected Color chipColor() {
        return new Color(220, 140, 50);
    }

    @Override
    protected boolean isEnabled() {
        return false; // merged into LoneBotControlPaint
    }

    @Override
    protected Dimension renderExpanded(Graphics2D graphics) {
        BotRuntime.DebugPaint paint = BotRuntime.impDebugPaint;
        if (paint != null) {
            try {
                return paint.render(graphics);
            } catch (Throwable ignored) {
            }
        }
        fallback.getChildren().clear();
        fallback.getChildren().add(TitleComponent.builder()
                .text("Imp Killer v" + BotRuntime.impPluginVersion)
                .color(new Color(255, 180, 80))
                .build());
        fallback.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(BotRuntime.impStatus != null ? BotRuntime.impStatus : "-")
                .leftColor(new Color(200, 200, 200))
                .rightColor(Color.WHITE)
                .build());
        fallback.getChildren().add(LineComponent.builder()
                .left("Paint")
                .right("wacht op plugin…")
                .leftColor(new Color(200, 200, 200))
                .rightColor(Color.WHITE)
                .build());
        return fallback.render(graphics);
    }
}
