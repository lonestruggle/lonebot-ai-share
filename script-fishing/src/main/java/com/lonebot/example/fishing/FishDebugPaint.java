package com.lonebot.example.fishing;

import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.storm.sdk.bot.BotRuntime;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;

final class FishDebugPaint {

    private final FishingLoop loop;
    private final PanelComponent panel = new PanelComponent();

    FishDebugPaint(FishingLoop loop) {
        this.loop = loop;
        panel.setBackgroundColor(new Color(0, 30, 70, 160));
    }

    Dimension render(Graphics2D g) {
        panel.getChildren().clear();
        String ver = BotRuntime.fishPluginVersion != null ? BotRuntime.fishPluginVersion : "?";
        panel.getChildren().add(TitleComponent.builder()
                .text("Fish v" + ver)
                .color(new Color(90, 180, 255))
                .build());
        String status = loop != null ? loop.getStatus() : BotRuntime.fishStatus;
        add("Status", trim(status, 34));
        if (loop != null) {
            List<String> lines = loop.debugLines();
            if (lines != null) {
                for (String line : lines) {
                    if (line == null || line.isEmpty()) {
                        continue;
                    }
                    int colon = line.indexOf(':');
                    if (colon > 0 && colon < 18) {
                        add(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                    } else {
                        add("", trim(line, 40));
                    }
                }
            }
        }
        return panel.render(g);
    }

    private void add(String left, String right) {
        panel.getChildren().add(LineComponent.builder()
                .left(left != null ? left : "")
                .right(right != null ? right : "")
                .build());
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "-";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
