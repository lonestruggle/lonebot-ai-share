package com.lonebot.example.imps;

import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.storm.sdk.bot.BotRuntime;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Compacte Imp debug-paint (hot-reload). Geen bank/chat-overlap-ruis.
 */
public final class ImpDebugPaint {

    private final CoreImpsHandler handler;
    private final PanelComponent panel = new PanelComponent();

    public ImpDebugPaint(CoreImpsHandler handler) {
        this.handler = handler;
        panel.setBackgroundColor(new Color(0, 0, 0, 150));
    }

    public Dimension render(Graphics2D g) {
        panel.getChildren().clear();
        String ver = BotRuntime.impPluginVersion != null ? BotRuntime.impPluginVersion : "?";
        panel.getChildren().add(TitleComponent.builder()
                .text("Imp v" + ver)
                .color(new Color(255, 180, 80))
                .build());

        String status = handler != null ? handler.getStatus() : BotRuntime.impStatus;
        add("Status", trim(status, 32));

        if (handler != null) {
            for (String line : handler.debugLines()) {
                int colon = line.indexOf(':');
                if (colon > 0 && colon < line.length() - 1) {
                    add(line.substring(0, colon).trim(), trim(line.substring(colon + 1).trim(), 34));
                } else {
                    add("", trim(line, 36));
                }
            }
        }
        return panel.render(g);
    }

    private void add(String left, String right) {
        panel.getChildren().add(LineComponent.builder()
                .left(left != null ? left : "")
                .right(right != null ? right : "-")
                .leftColor(new Color(200, 200, 200))
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
