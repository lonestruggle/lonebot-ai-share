package net.storm.sdk.game;

import java.awt.Canvas;
import java.lang.reflect.Method;

/**
 * Storm-compat Client facade (incl. Jagex OAuth login helpers via reflection).
 */
public final class Client {

    private Client() {
    }

    public static net.runelite.api.Client get() {
        return Static.getClient();
    }

    public static Canvas getCanvas() {
        net.runelite.api.Client c = Static.getClient();
        return c != null ? c.getCanvas() : null;
    }

    public static int getGameState() {
        net.runelite.api.Client c = Static.getClient();
        return c != null && c.getGameState() != null ? c.getGameState().getState() : -1;
    }

    public static boolean isLoggedIn() {
        net.runelite.api.Client c = Static.getClient();
        return c != null && c.getGameState() == net.runelite.api.GameState.LOGGED_IN;
    }

    public static void setOAuthLoginMode() {
        invokeClient("setOAuthLoginMode");
        invokeClient("setLoginType", 1);
    }

    public static void setNormalLoginMode() {
        invokeClient("setNormalLoginMode");
        invokeClient("setLoginType", 0);
    }

    public static void setUsername(String username) {
        net.runelite.api.Client c = Static.getClient();
        if (c != null && username != null) {
            try {
                c.setUsername(username);
            } catch (Throwable ignored) {
            }
        }
        invokeClient("setUsername", username);
    }

    public static void setDisplayName(String displayName) {
        // Alleen display — NIET setUsername (dat opent klassieke email/wachtwoord-login).
        invokeClient("setDisplayName", displayName);
    }

    public static void setSessionId(String sessionId) {
        invokeClient("setSessionId", sessionId);
    }

    public static void setCharacterId(String characterId) {
        invokeClient("setCharacterId", characterId);
    }

    public static void promptCredentials(boolean prompt) {
        invokeClient("promptCredentials", prompt);
    }

    /**
     * Storm-compat menu invoke — zelfde signature als
     * {@code net.storm.sdk.game.Client.invokeMenuAction} op Storm.
     * Vanilla RL: {@link net.runelite.api.Client#menuAction}.
     */
    public static void invokeMenuAction(int param0, int param1, int opcode, int id, int itemId,
                                        int worldViewId, String option, String target) {
        net.storm.sdk.interact.MenuInteract.invokeMenuAction(
                param0, param1, opcode, id, itemId, worldViewId, option, target);
    }

    private static void invokeClient(String method) {
        net.runelite.api.Client c = Static.getClient();
        if (c == null) {
            return;
        }
        try {
            Method m = findMethod(c.getClass(), method);
            if (m != null) {
                m.setAccessible(true);
                m.invoke(c);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void invokeClient(String method, Object arg) {
        net.runelite.api.Client c = Static.getClient();
        if (c == null) {
            return;
        }
        try {
            Method m = findMethod(c.getClass(), method, arg != null ? arg.getClass() : Object.class);
            if (m == null && arg instanceof String) {
                m = findMethod(c.getClass(), method, String.class);
            }
            if (m == null && arg instanceof Boolean) {
                m = findMethod(c.getClass(), method, boolean.class);
            }
            if (m == null && arg instanceof Integer) {
                m = findMethod(c.getClass(), method, int.class);
            }
            if (m != null) {
                m.setAccessible(true);
                m.invoke(c, arg);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> cur = type;
        while (cur != null) {
            try {
                if (params == null || params.length == 0) {
                    return cur.getDeclaredMethod(name);
                }
                return cur.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // try public
                try {
                    if (params == null || params.length == 0) {
                        return cur.getMethod(name);
                    }
                    return cur.getMethod(name, params);
                } catch (NoSuchMethodException ignored2) {
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }
}
