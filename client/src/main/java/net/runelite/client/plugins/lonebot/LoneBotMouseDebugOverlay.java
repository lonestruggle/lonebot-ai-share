package net.runelite.client.plugins.lonebot;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.input.Mouse;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Alleen muis-debug (kruisje + target) — los van {@link LoneBotStatusOverlay}.
 */
public class LoneBotMouseDebugOverlay extends Overlay {

    private final LoneBotConfig config;

    @Inject
    public LoneBotMouseDebugOverlay(LoneBotConfig config) {
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        boolean on = BotRuntime.mouseDebugEnabled
                || (config != null && config.mouseDebugOverlay());
        if (!on) {
            return null;
        }

        int mx = Mouse.getX();
        int my = Mouse.getY();
        graphics.setColor(new Color(0, 220, 255, 220));
        graphics.drawLine(mx - 8, my, mx + 8, my);
        graphics.drawLine(mx, my - 8, mx, my + 8);
        graphics.drawOval(mx - 4, my - 4, 8, 8);

        int tx = Mouse.getTargetX();
        int ty = Mouse.getTargetY();
        if (tx >= 0 && ty >= 0) {
            graphics.setColor(new Color(255, 160, 0, 200));
            graphics.drawRect(tx - 6, ty - 6, 12, 12);
            graphics.drawLine(mx, my, tx, ty);
        }

        graphics.setColor(new Color(200, 240, 255, 200));
        graphics.drawString("canvas " + mx + "," + my, mx + 10, my - 8);
        String proof = Mouse.getLastProof();
        if (proof != null && !proof.isEmpty() && proof.length() < 48) {
            graphics.drawString(proof, mx + 10, my + 14);
        }
        graphics.setColor(new Color(255, 210, 140, 180));
        graphics.drawString("muis-px (geen looptegel)", mx + 10, my + 28);
        return null;
    }
}
