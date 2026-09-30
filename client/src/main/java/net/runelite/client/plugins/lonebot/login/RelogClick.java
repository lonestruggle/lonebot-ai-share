package net.runelite.client.plugins.lonebot.login;

import net.runelite.api.MenuAction;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.game.Client;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.interact.MenuInteract;

import java.awt.Canvas;
import java.awt.Rectangle;

/**
 * Muis + CC_OP voor Play-klikken (CombatBot HumanMouseClickHelper / Client.interact).
 */
public final class RelogClick {

    private RelogClick() {
    }

    public static boolean leftClickAt(int x, int y, boolean fast) {
        Canvas canvas = Client.getCanvas();
        if (canvas == null) {
            return false;
        }
        int maxX = Math.max(20, canvas.getWidth() - 3);
        int maxY = Math.max(20, canvas.getHeight() - 3);
        int cx = Math.max(3, Math.min(maxX, x));
        int cy = Math.max(3, Math.min(maxY, y));
        try {
            if (fast) {
                return Mouse.clickOnly(cx, cy, true);
            }
            return Mouse.moveAndClick(cx, cy, "relog-play");
        } catch (Throwable t) {
            try {
                Mouse.click(cx, cy);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    public static boolean leftClickAt(int x, int y) {
        return leftClickAt(x, y, false);
    }

    public static boolean smoothMoveAndLeftClickWidget(IWidget w) {
        if (w == null) {
            return false;
        }
        try {
            if (w.isHidden()) {
                return false;
            }
            Rectangle b = w.getBounds();
            if (b == null || b.width <= 0 || b.height <= 0) {
                return false;
            }
            int padX = Math.max(2, b.width / 6);
            int padY = Math.max(2, b.height / 6);
            int innerW = Math.max(1, b.width - 2 * padX);
            int innerH = Math.max(1, b.height - 2 * padY);
            int x = b.x + padX + (int) (Math.random() * innerW);
            int y = b.y + padY + (int) (Math.random() * innerH);
            return leftClickAt(x, y, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Storm {@code Client.interact(identifier, opcode, param0, param1)}. */
    public static boolean ccOp(int identifier, int opcode, int param0, int packedId) {
        return MenuInteract.invokeMenu("Play", "", identifier, opcode, param0, packedId);
    }

    public static boolean ccOpPlay(int packedId) {
        return ccOp(1, MenuAction.CC_OP.getId(), -1, packedId);
    }

    /** CombatBot {@code widget.interact(0)} — eerste actie. */
    public static boolean interactFirst(IWidget play) {
        if (play == null) {
            return false;
        }
        try {
            if (play.hasAction("Play")) {
                return play.interact("Play");
            }
        } catch (Throwable ignored) {
        }
        try {
            return play.interact("Play");
        } catch (Throwable ignored) {
        }
        try {
            return ccOpPlay(play.getId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void mouseClick(int x, int y, boolean left) {
        if (!left) {
            Mouse.clickOnly(x, y, false);
            return;
        }
        if (!leftClickAt(x, y, true)) {
            Mouse.click(x, y);
        }
    }
}
