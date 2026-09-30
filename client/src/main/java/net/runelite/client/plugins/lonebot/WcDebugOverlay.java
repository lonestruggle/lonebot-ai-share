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

/**
 * Woodcutting canvas-panel. Chip = verbergen ({@link MinimizableStatusOverlay}).
 */
public class WcDebugOverlay extends MinimizableStatusOverlay {

    private final LoneBotConfig config;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public WcDebugOverlay(LoneBotConfig config) {
        this.config = config;
        applyDefaultPosition(OverlayPosition.TOP_LEFT);
        panel.setBackgroundColor(new Color(0, 40, 20, 170));
    }

    @Override
    public String overlayId() {
        return "wc";
    }

    @Override
    protected Color chipColor() {
        return new Color(80, 180, 100);
    }

    @Override
    protected boolean isEnabled() {
        // Script-detail zit in LoneBotControlPaint (layout A); apart groen paneel uit.
        return false;
    }

    @Override
    protected Dimension renderExpanded(Graphics2D graphics) {
        List<String> live = BotRuntime.wcDebugLines;
        java.util.ArrayList<String> shown = new java.util.ArrayList<>();
        shown.add("Bot: " + (BotRuntime.botEnabled ? "AAN" : "UIT")
                + (BotRuntime.woodcuttingEnabled ? " · WC" : " · WC-script uit")
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
        BotRuntime.DebugPaint paint = BotRuntime.wcDebugPaint;
        if (paint != null) {
            try {
                return paint.render(graphics);
            } catch (Throwable ignored) {
            }
        }
        return renderLines(graphics, List.of(
                "Status: " + (BotRuntime.wcStatus != null ? BotRuntime.wcStatus : "-")));
    }

    private Dimension renderLines(Graphics2D graphics, List<String> lines) {
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder()
                .text("WC v" + BotRuntime.wcPluginVersion)
                .color(new Color(120, 220, 140))
                .build());
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0 && colon < line.length() - 1) {
                add(line.substring(0, colon).trim(), trim(line.substring(colon + 1).trim(), 34));
            } else {
                add("", trim(line, 36));
            }
        }
        return panel.render(graphics);
    }

    private void add(String left, String right) {
        panel.getChildren().add(LineComponent.builder()
                .left(left != null ? left : "")
                .right(right != null ? right : "-")
                .leftColor(new Color(180, 210, 190))
                .rightColor(Color.WHITE)
                .build());
    }

    private static String loopAgeTag() {
        long t = BotRuntime.loopLastTickMs;
        if (t <= 0L) {
            return " · loop ?";
        }
        long age = System.currentTimeMillis() - t;
        if (age < 2_500L) {
            return " · loop ok";
        }
        return " · loop " + (age / 1000) + "s stil";
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "-";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
