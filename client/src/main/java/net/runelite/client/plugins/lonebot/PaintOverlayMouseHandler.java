package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;

import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Klik (links of rechts) op de chip van elke {@link MinimizableStatusOverlay} → verbergen / terugzetten.
 */
final class PaintOverlayMouseHandler {

    private final Client client;

    private Canvas attachedCanvas;
    private MouseAdapter listener;

    PaintOverlayMouseHandler(Client client) {
        this.client = client;
    }

    void install() {
        ensureCanvasListener();
    }

    void uninstall() {
        detachListener();
    }

    void ensureCanvasListener() {
        Canvas canvas = resolveGameCanvas(client);
        if (canvas == null) {
            return;
        }
        if (listener != null && canvas == attachedCanvas) {
            return;
        }
        detachListener();
            listener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int btn = e.getButton();
                if (btn != MouseEvent.BUTTON1 && btn != MouseEvent.BUTTON3) {
                    return;
                }
                if (btn == MouseEvent.BUTTON1
                        && LoneBotControlOverlay.hitWorldWalkerStop(e.getX(), e.getY())) {
                    net.storm.sdk.movement.WorldWalker.cancel();
                    e.consume();
                    return;
                }
                for (OverlayMinimizeStore.Chip chip : OverlayMinimizeStore.chips()) {
                    if (chip != null && chip.containsChip(e.getX(), e.getY())) {
                        e.consume();
                        OverlayMinimizeStore.toggle(chip.overlayId());
                        return;
                    }
                }
            }
        };
        attachedCanvas = canvas;
        canvas.addMouseListener(listener);
    }

    private void detachListener() {
        if (attachedCanvas != null && listener != null) {
            try {
                attachedCanvas.removeMouseListener(listener);
            } catch (Throwable ignored) {
            }
        }
        listener = null;
        attachedCanvas = null;
    }

    private static Canvas resolveGameCanvas(Client client) {
        try {
            Canvas storm = net.storm.sdk.game.Client.getCanvas();
            if (storm != null) {
                return storm;
            }
        } catch (Throwable ignored) {
        }
        return client != null ? client.getCanvas() : null;
    }
}
