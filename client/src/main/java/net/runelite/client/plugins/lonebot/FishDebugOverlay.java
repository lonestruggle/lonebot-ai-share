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

public class FishDebugOverlay extends MinimizableStatusOverlay {

    private final LoneBotConfig config;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public FishDebugOverlay(LoneBotConfig config) {
        this.config = config;
        applyDefaultPosition(OverlayPosition.TOP_LEFT);
        panel.setBackgroundColor(new Color(0, 30, 70, 170));
    }

    @Override
    public String overlayId() {
        return "fish";
    }

    @Override
    protected Color chipColor() {
        return new Color(80, 160, 255);
    }

    @Override
    protected boolean isEnabled() {
        return false; // merged into LoneBotControlPaint
    }

    @Override
    protected Dimension renderExpanded(Graphics2D graphics) {
        List<String> live = BotRuntime.fishDebugLines;
        java.util.ArrayList<String> shown = new java.util.ArrayList<>();
        shown.add("Bot: " + (BotRuntime.botEnabled ? "AAN" : "UIT")
                + (BotRuntime.fishingEnabled ? " · Fish" : " · Fish-script uit")
                + loopAgeTag());
        if (live != null) {
            for (String line : live) {
                if (line != null && line.startsWith("Bot:")) {
                    continue;
                }
                shown.add(line);
            }
        }
        if (shown.size() > 1) {
            return renderLines(graphics, shown);
        }
        BotRuntime.DebugPaint paint = BotRuntime.fishDebugPaint;
        if (paint != null) {
            try {
                return paint.render(graphics);
            } catch (Throwable ignored) {
            }
        }
        return renderLines(graphics, List.of(
                "Status: " + (BotRuntime.fishStatus != null ? BotRuntime.fishStatus : "-")));
    }

    private Dimension renderLines(Graphics2D graphics, List<String> lines) {
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder()
                .text("Fish v" + BotRuntime.fishPluginVersion)
                .color(new Color(90, 180, 255))
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

    private static String loopAgeTag() {
        long t = BotRuntime.loopLastTickMs;
        if (t <= 0L) {
            return "";
        }
        long age = System.currentTimeMillis() - t;
        if (age > 4000L) {
            return " · loop " + (age / 1000) + "s";
        }
        return "";
    }
}
