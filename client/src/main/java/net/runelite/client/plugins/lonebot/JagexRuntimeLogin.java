package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Past Jagex-credentials toe op de lopende injected client — zonder herstart.
 * <p>
 * Vanilla RL leest {@code credentials.properties} maar één keer in geheugen ({@code hv}).
 * Storm deed {@code setSessionId} e.d.; hier updaten we dezelfde Properties + token-statics
 * die de login-screen “Play”-flow gebruikt.
 */
public final class JagexRuntimeLogin {

    private static final Logger log = LoggerFactory.getLogger(JagexRuntimeLogin.class);

    private static final String[] CRED_KEYS = {
            "JX_ACCESS_TOKEN",
            "JX_REFRESH_TOKEN",
            "JX_SESSION_ID",
            "JX_CHARACTER_ID",
            "JX_DISPLAY_NAME"
    };

    /** Obfuscated static String holders (RuneLite 1.12.35) — discovery vult/cachet daarna. */
    private static final String[][] STATIC_FALLBACKS = {
            {"lt", "lh", "JX_ACCESS_TOKEN"},
            {"ch", "lz", "JX_REFRESH_TOKEN"},
            {"at", "lb", "JX_SESSION_ID"},
            {"ec", "ly", "JX_CHARACTER_ID"}
    };

    private static final Map<String, Field> CACHED_FIELDS = new LinkedHashMap<>();

    private JagexRuntimeLogin() {
    }

    /**
     * @return true als Properties op de client gezet zijn (statics best-effort)
     */
    public static boolean applyInMemory(Client client, JagexCredentialsHelper.ParsedJagexAccount account) {
        if (client == null || account == null) {
            return false;
        }
        Properties desired = account.toProperties();
        if (desired.getProperty("JX_SESSION_ID", "").isEmpty()
                && desired.getProperty("JX_ACCESS_TOKEN", "").isEmpty()) {
            log.warn("Geen session/access token om toe te passen");
            return false;
        }
        // Access-token mag session zijn als Storm alleen session_id exporteerde
        if (isBlank(desired.getProperty("JX_ACCESS_TOKEN"))) {
            desired.setProperty("JX_ACCESS_TOKEN", desired.getProperty("JX_SESSION_ID", ""));
        }
        if (isBlank(desired.getProperty("JX_SESSION_ID"))) {
            desired.setProperty("JX_SESSION_ID", desired.getProperty("JX_ACCESS_TOKEN", ""));
        }

        try {
            Map<String, String> before = snapshotCurrent(client);

            Properties hv = ensureCredentialsProperties(client);
            if (hv == null) {
                log.warn("client.hv (Properties) niet gevonden");
                return false;
            }
            for (String key : CRED_KEYS) {
                String v = desired.getProperty(key);
                if (v != null && !v.isEmpty()) {
                    hv.setProperty(key, v);
                } else if ("JX_REFRESH_TOKEN".equals(key)) {
                    // leeg refresh is ok — niet verplicht
                    hv.setProperty(key, "");
                }
            }

            applyStaticTokenFields(client, before, desired);
            trySetDisplayName(client, desired.getProperty("JX_DISPLAY_NAME", account.displayName));
            tryResetLoginState(client);
            try {
                client.setUsername("");
            } catch (Throwable ignored) {
            }

            String shown = safeAy(client, "JX_DISPLAY_NAME");
            log.info("Jagex credentials live toegepast: display={} characterId={}",
                    shown != null ? shown : account.displayName,
                    desired.getProperty("JX_CHARACTER_ID", ""));
            return true;
        } catch (Throwable t) {
            log.error("Runtime Jagex login mislukt: {}", t.toString());
            return false;
        }
    }

    private static Map<String, String> snapshotCurrent(Client client) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : CRED_KEYS) {
            String v = safeAy(client, key);
            if (v != null) {
                map.put(key, v);
            }
        }
        return map;
    }

    private static String safeAy(Client client, String key) {
        try {
            Method my = client.getClass().getMethod("my", String.class);
            Object v = my.invoke(null, key);
            return v != null ? v.toString() : null;
        } catch (Throwable t) {
            try {
                Method ay = client.getClass().getMethod("ay", String.class);
                Object v = ay.invoke(client, key);
                return v != null ? v.toString() : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Properties ensureCredentialsProperties(Client client) throws Exception {
        Field hvField = findInstanceField(client.getClass(), Properties.class);
        if (hvField == null) {
            return null;
        }
        hvField.setAccessible(true);
        Properties hv = (Properties) hvField.get(client);
        if (hv == null) {
            // Trigger lazy init in ay()/my()
            safeAy(client, "JX_SESSION_ID");
            hv = (Properties) hvField.get(client);
        }
        if (hv == null) {
            hv = new Properties();
            hvField.set(client, hv);
        }
        return hv;
    }

    private static Field findInstanceField(Class<?> type, Class<?> fieldType) {
        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            for (Field f : cur.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (fieldType.equals(f.getType())) {
                    return f;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static void applyStaticTokenFields(Client client, Map<String, String> before, Properties desired) {
        ClassLoader cl = client.getClass().getClassLoader();

        // 1) Update fields die we al kennen / via oude waarde vinden
        for (String key : CRED_KEYS) {
            if ("JX_DISPLAY_NAME".equals(key) || "JX_REFRESH_TOKEN".equals(key) && isBlank(desired.getProperty(key))) {
                // display via aparte helper; lege refresh skip discovery-noise
            }
            String oldVal = before.get(key);
            String newVal = desired.getProperty(key, "");
            Field cached = CACHED_FIELDS.get(key);
            if (cached != null) {
                setField(cached, newVal);
                continue;
            }
            Field found = findStaticStringFieldByValue(cl, oldVal);
            if (found != null) {
                CACHED_FIELDS.put(key, found);
                setField(found, newVal);
            }
        }

        // 2) Fallback hardcoded namen (1.12.35) als discovery niets vond (eerste keer, nulls)
        for (String[] row : STATIC_FALLBACKS) {
            String className = row[0];
            String fieldName = row[1];
            String key = row[2];
            if (CACHED_FIELDS.containsKey(key)) {
                continue;
            }
            String newVal = desired.getProperty(key, "");
            if (isBlank(newVal) && !"JX_REFRESH_TOKEN".equals(key)) {
                continue;
            }
            Field f = findStaticStringField(cl, className, fieldName);
            if (f != null) {
                CACHED_FIELDS.put(key, f);
                setField(f, newVal);
            }
        }
    }

    private static Field findStaticStringFieldByValue(ClassLoader cl, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (String[] row : STATIC_FALLBACKS) {
            Field f = findStaticStringField(cl, row[0], row[1]);
            if (f == null) {
                continue;
            }
            try {
                f.setAccessible(true);
                if (Objects.equals(f.get(null), value)) {
                    return f;
                }
            } catch (Throwable ignored) {
            }
        }
        // Brede scan: short-named sibling classes of client
        try {
            for (String cn : new String[]{"lt", "ch", "at", "ec", "av", "oe", "kk", "bf"}) {
                Class<?> c = Class.forName(cn, false, cl);
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) {
                        continue;
                    }
                    f.setAccessible(true);
                    if (Objects.equals(f.get(null), value)) {
                        return f;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findStaticStringField(ClassLoader cl, String className, String fieldName) {
        try {
            Class<?> c = Class.forName(className, false, cl);
            Field f = c.getDeclaredField(fieldName);
            if (f.getType() == String.class && Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                return f;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void setField(Field f, String value) {
        try {
            f.setAccessible(true);
            f.set(null, value != null ? value : "");
        } catch (Throwable t) {
            log.debug("Kon veld {} niet zetten: {}", f, t.toString());
        }
    }

    private static void trySetDisplayName(Client client, String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return;
        }
        ClassLoader cl = client.getClass().getClassLoader();
        // kk.ah(String, int) — int is obfuscator-junk, probeer bekende constant uit 1.12.35
        int[] magics = {-1563472338, 0, 1, -1};
        try {
            Class<?> kk = Class.forName("kk", false, cl);
            for (Method m : kk.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && p[1] == int.class) {
                    m.setAccessible(true);
                    for (int magic : magics) {
                        try {
                            m.invoke(null, displayName, magic);
                            return;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log.debug("DisplayName reflect: {}", t.toString());
        }
    }

    private static void tryResetLoginState(Client client) {
        try {
            Method dq = client.getClass().getDeclaredMethod("dq", int.class);
            dq.setAccessible(true);
            dq.invoke(null, -1);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
