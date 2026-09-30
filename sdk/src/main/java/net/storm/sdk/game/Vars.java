package net.storm.sdk.game;

import net.runelite.api.Client;

/**
 * Varps / varbits / client vars — all reads via {@link Static#callOnClientThread}.
 */
public final class Vars {

    private Vars() {
    }

    public static int getVarp(int varp) {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? c.getVarpValue(varp) : 0;
        }, 0);
        return v != null ? v : 0;
    }

    public static int getVarbit(int varbit) {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? c.getVarbitValue(varbit) : 0;
        }, 0);
        return v != null ? v : 0;
    }

    /** Storm alias for {@link #getVarbit(int)}. */
    public static int getBit(int id) {
        return getVarbit(id);
    }

    public static int getVarcInt(int varClientInt) {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            try {
                return c.getVarcIntValue(varClientInt);
            } catch (Throwable t) {
                return 0;
            }
        }, 0);
        return v != null ? v : 0;
    }

    public static String getVarcStr(int varClientStr) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                return c.getVarcStrValue(varClientStr);
            } catch (Throwable t) {
                return null;
            }
        }, null);
    }
}
