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
import java.util.List;

public class StarDebugOverlay extends MinimizableStatusOverlay {

    private final LoneBotConfig config;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public StarDebugOverlay(LoneBotConfig config) {
        this.config = config;
        applyDefaultPosition(OverlayPosition.TOP_LEFT);
        panel.setBackgroundColor(new Color(40, 20, 70, 170));
    }

    @Override
    public String overlayId() {
        return "star";
    }

    @Override
    protected Color chipColor() {
        return new Color(180, 140, 255);
    }

    @Override
    protected boolean isEnabled() {
        return false; // merged into LoneBotControlPaint
    }

    @Override
    protected Dimension renderExpanded(Graphics2D graphics) {
        List<String> live = BotRuntime.starDebugLines;
        java.util.ArrayList<String> shown = new java.util.ArrayList<>();
        shown.add("Bot: " + (BotRuntime.botEnabled ? "AAN" : "UIT")
                + (BotRuntime.starMinerEnabled ? " · Star" : " · Star-script uit"));
        if (live != null) {
            for (String line : live) {
                if (line != null && line.startsWith("Bot:")) {
                    continue;
                }
                shown.add(line);
            }
        }
        return renderLines(graphics, shown.isEmpty()
                ? List.of("Status: " + (BotRuntime.starStatus != null ? BotRuntime.starStatus : "-"))
                : shown);
    }

    private Dimension renderLines(Graphics2D graphics, List<String> lines) {
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder()
                .text("Star v" + BotRuntime.starPluginVersion)
                .color(new Color(200, 170, 255))
                .build());
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0 && colon < 18) {
                panel.getChildren().add(LineComponent.builder()
                        .left(line.substring(0, colon).trim())
                        .right(line.substring(colon + 1).trim())
                        .build());
            } else {
                panel.getChildren().add(LineComponent.builder()
                        .left("")
                        .right(line)
                        .build());
            }
        }
        return panel.render(graphics);
    }
}
