package com.lonebot.example.woodcutter;

import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.storm.sdk.bot.BotRuntime;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;

/**
 * WC canvas-panel (hot-reloadbaar via {@link BotRuntime#wcDebugPaint}).
 */
public final class WcDebugPaint {

    private final WoodcutterLoop loop;
    private final PanelComponent panel = new PanelComponent();

    public WcDebugPaint(WoodcutterLoop loop) {
        this.loop = loop;
        panel.setBackgroundColor(new Color(0, 40, 20, 160));
    }

    public Dimension render(Graphics2D g) {
        panel.getChildren().clear();
        String ver = BotRuntime.wcPluginVersion != null ? BotRuntime.wcPluginVersion : "?";
        panel.getChildren().add(TitleComponent.builder()
                .text("WC v" + ver)
                .color(new Color(120, 220, 140))
                .build());
        String status = loop != null ? loop.getStatus() : BotRuntime.wcStatus;
        add("Status", trim(status, 34));
        if (loop != null) {
            List<String> lines = loop.debugLines();
            if (lines != null) {
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
            }
        }
        return panel.render(g);
    }

    private void add(String left, String right) {
        panel.getChildren().add(LineComponent.builder()
                .left(left != null ? left : "")
                .right(right != null ? right : "-")
                .leftColor(new Color(180, 210, 190))
                .rightColor(Color.WHITE)
                .build());
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "-";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
