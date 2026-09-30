package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.storm.sdk.game.Game;
import net.storm.sdk.game.Static;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Past Jagex-creds toe en zet login-index voorbij New/Existing User (geen muisklik).
 * RuneLite 1.12.35: {@code getLoginIndex() = bf.cw * -47366135}.
 */
public final class LoginWelcomeAdvanceHelper {

    private static final Logger log = LoggerFactory.getLogger(LoginWelcomeAdvanceHelper.class);
    private static final int LOGIN_INDEX_MUL = -47366135;
    /** 2 = Existing User / credentials-form (RL API docs). */
    private static final int INDEX_EXISTING_USER = 2;

    private static long lastForceMs;
    private static Field loginIndexField;
    private static boolean fieldResolved;

    private LoginWelcomeAdvanceHelper() {
    }

    public static void reset() {
        lastForceMs = 0L;
    }

    public static void applyJagexAccount(Client client, JagexCredentialsHelper.ParsedJagexAccount parsed) {
        if (client == null || parsed == null) {
            return;
        }
        try {
            Static.setClient(client);
            JagexRuntimeLogin.applyInMemory(client, parsed);
            Game.setGameAccount(parsed.toGameAccount());
            try {
                Method dq = client.getClass().getDeclaredMethod("dq", int.class);
                dq.setAccessible(true);
                dq.invoke(null, 0);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            log.warn("applyJagexAccount: {}", t.toString());
        }
    }

    /**
     * Op LOGIN_SCREEN: forceer index 2 tot we voorbij welcome zijn.
     * @return true als niet meer op welcome (index≠0 of launcher display name)
     */
    public static boolean tickForcePastWelcome(Client client, JagexCredentialsHelper.ParsedJagexAccount parsed) {
        if (client == null) {
            return false;
        }
        GameState gs = client.getGameState();
        if (gs != GameState.LOGIN_SCREEN && gs != GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            return true;
        }
        if (parsed != null) {
            applyJagexAccount(client, parsed);
        }
        if (isPastWelcome(client)) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastForceMs < 800L) {
            return false;
        }
        lastForceMs = now;
        boolean set = setLoginIndex(client, INDEX_EXISTING_USER);
        log.info("Force loginIndex={} ok={} nowIndex={} launcherName={}",
                INDEX_EXISTING_USER, set, safeIndex(client), safeLauncherName(client));
        return isPastWelcome(client);
    }

    public static boolean isPastWelcome(Client client) {
        if (client == null) {
            return false;
        }
        try {
            int idx = client.getLoginIndex();
            return idx != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean setLoginIndex(Client client, int desiredIndex) {
        try {
            Field f = resolveLoginIndexField(client);
            if (f == null) {
                return false;
            }
            int encoded = desiredIndex * modularInverse(LOGIN_INDEX_MUL);
            f.setInt(null, encoded);
            // verify
            int got = client.getLoginIndex();
            if (got != desiredIndex) {
                log.warn("loginIndex set mismatch want={} got={} encoded={}", desiredIndex, got, encoded);
            }
            return true;
        } catch (Throwable t) {
            log.warn("setLoginIndex mislukt: {}", t.toString());
            return false;
        }
    }

    private static Field resolveLoginIndexField(Client client) {
        if (fieldResolved) {
            return loginIndexField;
        }
        fieldResolved = true;
        try {
            ClassLoader cl = client.getClass().getClassLoader();
            Class<?> bf = Class.forName("bf", false, cl);
            for (Field f : bf.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class && "cw".equals(f.getName())) {
                    f.setAccessible(true);
                    loginIndexField = f;
                    return f;
                }
            }
        } catch (Throwable t) {
            log.warn("bf.cw niet gevonden: {}", t.toString());
        }
        return null;
    }

    private static int modularInverse(int a) {
        long t = 0;
        long newt = 1;
        long r = 1L << 32;
        long newr = a & 0xffffffffL;
        while (newr != 0) {
            long q = r / newr;
            long tmp = t;
            t = newt;
            newt = tmp - q * newt;
            tmp = r;
            r = newr;
            newr = tmp - q * newr;
        }
        return (int) (t & 0xffffffffL);
    }

    private static int safeIndex(Client client) {
        try {
            return client.getLoginIndex();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String safeLauncherName(Client client) {
        try {
            return client.getLauncherDisplayName();
        } catch (Throwable t) {
            return null;
        }
    }
}
