package net.storm.sdk.input;

import net.storm.sdk.game.Static;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.KeyEvent;

public final class Keyboard {

    private Keyboard() {
    }

    public static void type(String text) {
        if (text == null) {
            return;
        }
        for (char ch : text.toCharArray()) {
            typeKey(ch);
            sleep(40 + (int) (Math.random() * 60));
        }
    }

    public static void type(String text, boolean sendEnter) {
        type(text);
        if (sendEnter) {
            pressKey(KeyEvent.VK_ENTER);
        }
    }

    public static void typeKey(char ch) {
        Component target = canvas();
        if (target == null) {
            return;
        }
        try {
            target.requestFocusInWindow();
        } catch (Throwable ignored) {
        }
        long t = System.currentTimeMillis();
        int vk = KeyEvent.getExtendedKeyCodeForChar(ch);
        if (vk == KeyEvent.VK_UNDEFINED) {
            vk = KeyEvent.VK_UNDEFINED;
        }
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, t, 0, vk, ch));
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_TYPED, t, 0, KeyEvent.VK_UNDEFINED, ch));
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, t + 12, 0, vk, ch));
    }

    public static void pressKey(int keyCode) {
        Component target = canvas();
        if (target == null) {
            return;
        }
        long t = System.currentTimeMillis();
        char ch = typedChar(keyCode);
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, t, 0, keyCode, ch));
        if (ch != KeyEvent.CHAR_UNDEFINED) {
            target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_TYPED, t, 0, KeyEvent.VK_UNDEFINED, ch));
        }
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, t + 20, 0, keyCode, ch));
    }

    /**
     * Chat-optie 1–9 (OSRS “Select an option”). Canvas-focus + KEY_TYPED digit.
     * @return false als er geen canvas is
     */
    public static boolean pressDigit(int digit) {
        if (digit < 0 || digit > 9) {
            return false;
        }
        Component target = canvas();
        if (target == null) {
            return false;
        }
        try {
            target.requestFocusInWindow();
        } catch (Throwable ignored) {
        }
        pressKey(KeyEvent.VK_0 + digit);
        return true;
    }

    private static char typedChar(int keyCode) {
        if (keyCode == KeyEvent.VK_SPACE) {
            return ' ';
        }
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
            return (char) ('0' + (keyCode - KeyEvent.VK_0));
        }
        if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9) {
            return (char) ('0' + (keyCode - KeyEvent.VK_NUMPAD0));
        }
        return KeyEvent.CHAR_UNDEFINED;
    }

    /** ESC na canvas-focus — sluit bank/interfaces. */
    public static void pressEscape() {
        Component target = canvas();
        if (target != null) {
            try {
                target.requestFocusInWindow();
            } catch (Throwable ignored) {
            }
        }
        pressKey(KeyEvent.VK_ESCAPE);
    }

    /**
     * CombatBot kookscherm: {@code Keyboard.type(" ", false)} — KEY_TYPED spatie, niet alleen press/release.
     */
    public static void pressSpace() {
        Component target = canvas();
        if (target != null) {
            try {
                target.requestFocusInWindow();
            } catch (Throwable ignored) {
            }
        }
        type(String.valueOf((char) KeyEvent.VK_SPACE), false);
        pressKey(KeyEvent.VK_SPACE);
    }

    /** Hold key down (camera arrows) — pair with {@link #released(int)}. */
    public static void pressed(int keyCode) {
        Component target = canvas();
        if (target == null) {
            return;
        }
        long t = System.currentTimeMillis();
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, t, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    /** Release held key. */
    public static void released(int keyCode) {
        Component target = canvas();
        if (target == null) {
            return;
        }
        long t = System.currentTimeMillis();
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, t, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    private static Component canvas() {
        net.runelite.api.Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        Canvas canvas = c.getCanvas();
        return canvas;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
