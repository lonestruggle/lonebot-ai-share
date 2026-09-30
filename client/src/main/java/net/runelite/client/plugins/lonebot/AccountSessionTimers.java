package net.runelite.client.plugins.lonebot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-account sessie-start (CombatBot accountLocalProgress timers — Lite versie).
 * Wis bij Stop als {@code resetAccountTimersOnStop}.
 */
public final class AccountSessionTimers {

    private static final Map<String, Long> SESSION_START_MS = new ConcurrentHashMap<>();

    private AccountSessionTimers() {
    }

    public static void markSessionStart(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) {
            return;
        }
        SESSION_START_MS.put(accountKey.trim().toLowerCase(), System.currentTimeMillis());
    }

    public static long elapsedSec(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) {
            return 0L;
        }
        Long start = SESSION_START_MS.get(accountKey.trim().toLowerCase());
        if (start == null) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
    }

    public static void clearAll() {
        SESSION_START_MS.clear();
    }

    public static int size() {
        return SESSION_START_MS.size();
    }
}
