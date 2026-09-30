package net.runelite.client.plugins.lonebot;

import net.storm.sdk.movement.WorldWalker;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

/**
 * ESC tijdens WorldWalker → {@link WorldWalker#cancel()} (niet meer blijven klikken).
 */
final class WorldWalkerHotkeys {

    private KeyEventDispatcher dispatcher;

    void install() {
        uninstall();
        dispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }
            if (e.getKeyCode() != KeyEvent.VK_ESCAPE) {
                return false;
            }
            if (!WorldWalker.isActive()) {
                return false;
            }
            // ESC om World Map te sluiten ≠ walk stoppen
            if (WorldWalker.shouldIgnoreEscCancel()) {
                return false;
            }
            WorldWalker.cancel();
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
    }

    void uninstall() {
        if (dispatcher == null) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
        dispatcher = null;
    }
}
