package net.runelite.client.plugins.lonebot;

import net.storm.api.account.GameAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Parse / schrijf Jagex credentials. Vanilla RuneLite leest
 * {@code ~/.runelite/credentials.properties} (via jagex.userhome).
 */
public final class JagexCredentialsHelper {

    private static final Logger log = LoggerFactory.getLogger(JagexCredentialsHelper.class);
    private static final String BLOCK_SEP = "---";

    private JagexCredentialsHelper() {
    }

    public static final class ParsedJagexAccount {
        public final String displayName;
        public final Map<String, String> props;

        ParsedJagexAccount(String displayName, Map<String, String> props) {
            this.displayName = displayName;
            this.props = props;
        }

        public GameAccount toGameAccount() {
            String characterId = first(props, "JX_CHARACTER_ID", "CHARACTER_ID");
            String session = first(props, "JX_SESSION_ID", "SESSION_ID", "sessionId",
                    "JX_ACCESS_TOKEN", "JX_REFRESH_TOKEN");
            if (session == null) {
                session = "";
            }
            if (characterId == null) {
                characterId = "";
            }
            String disp = first(props, "JX_DISPLAY_NAME", "displayName");
            if (disp == null || disp.isEmpty()) {
                disp = displayName;
            }
            String user = !characterId.isEmpty() ? characterId : disp;
            return new GameAccount(user, session, disp, session, characterId);
        }

        public Properties toProperties() {
            Properties p = new Properties();
            // RuneLite/Jagex launcher verwacht o.a. ACCESS_TOKEN (+ SESSION_ID zoals Storm).
            String session = first(props, "JX_SESSION_ID", "JX_ACCESS_TOKEN", "SESSION_ID");
            String characterId = first(props, "JX_CHARACTER_ID", "CHARACTER_ID");
            String disp = first(props, "JX_DISPLAY_NAME", "displayName");
            String refresh = first(props, "JX_REFRESH_TOKEN");

            if (disp != null && !disp.isEmpty()) {
                p.setProperty("JX_DISPLAY_NAME", disp);
            }
            if (characterId != null && !characterId.isEmpty()) {
                p.setProperty("JX_CHARACTER_ID", characterId);
            }
            if (session != null && !session.isEmpty()) {
                p.setProperty("JX_SESSION_ID", session);
                p.setProperty("JX_ACCESS_TOKEN", session);
            }
            // RL verwacht deze key (mag leeg) — zonder → vaak New/Existing i.p.v. Play Now
            p.setProperty("JX_REFRESH_TOKEN", refresh != null ? refresh : "");
            // Behoud overige keys
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (!p.containsKey(e.getKey()) && e.getValue() != null) {
                    p.setProperty(e.getKey(), e.getValue());
                }
            }
            return p;
        }
    }

    public static List<ParsedJagexAccount> parsePastedCredentials(String pasted) {
        List<ParsedJagexAccount> out = new ArrayList<>();
        if (pasted == null || pasted.trim().isEmpty()) {
            return out;
        }
        Map<String, String> current = new LinkedHashMap<>();
        for (String rawLine : pasted.split("\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.equals(BLOCK_SEP)) {
                if (!current.isEmpty()) {
                    out.add(build(current));
                    current = new LinkedHashMap<>();
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if ("JX_DISPLAY_NAME".equalsIgnoreCase(key) && current.containsKey("JX_DISPLAY_NAME")) {
                out.add(build(current));
                current = new LinkedHashMap<>();
            }
            if (!key.isEmpty()) {
                current.put(key, val);
            }
        }
        if (!current.isEmpty()) {
            out.add(build(current));
        }
        return out;
    }

    public static ParsedJagexAccount fromManaged(ManagedAccountsStore.ManagedAccount a) {
        if (a == null) {
            return null;
        }
        Map<String, String> props = new LinkedHashMap<>();
        props.put("JX_DISPLAY_NAME", nullToEmpty(a.displayName));
        props.put("JX_CHARACTER_ID", nullToEmpty(a.characterId));
        props.put("JX_SESSION_ID", nullToEmpty(a.sessionId));
        props.put("JX_ACCESS_TOKEN", nullToEmpty(a.sessionId));
        props.put("JX_REFRESH_TOKEN", nullToEmpty(a.refreshToken));
        if (a.loginEmail != null && !a.loginEmail.isBlank()) {
            props.put("JX_LOGIN_EMAIL", a.loginEmail.trim());
        }
        return new ParsedJagexAccount(
                a.displayName != null && !a.displayName.isEmpty() ? a.displayName : "Account",
                props);
    }

    public static ParsedJagexAccount loadFromFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (Exception e) {
            log.warn("Kan credentials niet laden: {}", e.toString());
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : p.stringPropertyNames()) {
            map.put(name, p.getProperty(name));
        }
        if (map.isEmpty()) {
            return null;
        }
        return build(map);
    }

    public static boolean writeToFile(File file, ParsedJagexAccount account) {
        if (file == null || account == null) {
            return false;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            // Officieel RL-formaat (UTF-8) — zelfde keys als Jagex launcher dump
            Properties props = account.toProperties();
            StringBuilder sb = new StringBuilder();
            sb.append("#Do not share this file with anyone\n");
            appendProp(sb, props, "JX_CHARACTER_ID");
            appendProp(sb, props, "JX_SESSION_ID");
            appendProp(sb, props, "JX_DISPLAY_NAME");
            appendProp(sb, props, "JX_REFRESH_TOKEN");
            appendProp(sb, props, "JX_ACCESS_TOKEN");
            for (String name : props.stringPropertyNames()) {
                if (name.startsWith("JX_") && !isCoreJx(name)) {
                    appendProp(sb, props, name);
                }
            }
            java.nio.file.Files.write(file.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log.info("Credentials geschreven → {}", file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.warn("Schrijven credentials mislukt: {}", e.toString());
            return false;
        }
    }

    private static boolean isCoreJx(String name) {
        return "JX_CHARACTER_ID".equals(name) || "JX_SESSION_ID".equals(name)
                || "JX_DISPLAY_NAME".equals(name) || "JX_REFRESH_TOKEN".equals(name)
                || "JX_ACCESS_TOKEN".equals(name);
    }

    private static void appendProp(StringBuilder sb, Properties props, String key) {
        String v = props.getProperty(key);
        if (v == null) {
            v = "";
        }
        sb.append(key).append('=').append(v).append('\n');
    }

    /**
     * Pad dat vanilla RuneLite/injected-client leest.
     * Prefer {@code runelite.credentials.path} (mag absoluut zijn), anders
     * {@code runelite.dir/credentials.properties}, anders {@code jagex.userhome}/….
     */
    public static File defaultCredentialsFile() {
        String pathProp = System.getProperty("runelite.credentials.path");
        if (pathProp != null && !pathProp.trim().isEmpty()) {
            File f = new File(pathProp.trim());
            if (f.isAbsolute()) {
                return f;
            }
            String rlDir = System.getProperty("runelite.dir");
            if (rlDir != null && !rlDir.isEmpty()) {
                return new File(rlDir, pathProp.trim());
            }
            return f;
        }
        String rlDir = System.getProperty("runelite.dir");
        if (rlDir != null && !rlDir.isEmpty()) {
            File underRl = new File(rlDir, "credentials.properties");
            // Multi-instance: schrijf/lees onder instance-config, niet shared ~/.runelite
            if (System.getProperty("lonebot.account") != null
                    && !System.getProperty("lonebot.account").isEmpty()) {
                return underRl;
            }
        }
        String jagexHome = System.getProperty("jagex.userhome");
        if (jagexHome == null || jagexHome.isEmpty()) {
            jagexHome = System.getProperty("user.home") + File.separator + ".runelite";
        }
        return new File(jagexHome, "credentials.properties");
    }

    /** Extra kopie onder LoneBot-home (backup). */
    public static File lonebotCredentialsFile() {
        String home = System.getProperty("runelite.dir");
        if (home == null || home.isEmpty()) {
            home = System.getProperty("user.home") + File.separator + ".lonebot";
        }
        return new File(home, "credentials.properties");
    }

    /**
     * Schrijf credentials voor déze client. Raakt {@code ~/.runelite} niet aan
     * als we in multi-instance mode zitten ({@code lonebot.account} gezet).
     */
    public static boolean writeJagexCredentials(ParsedJagexAccount account) {
        boolean ok = writeToFile(defaultCredentialsFile(), account);
        File lone = lonebotCredentialsFile();
        if (!lone.getAbsolutePath().equalsIgnoreCase(defaultCredentialsFile().getAbsolutePath())) {
            writeToFile(lone, account);
        }
        // Alleen shared ~/.runelite bij single-client zonder instance-account
        String launched = System.getProperty("lonebot.account");
        if (launched == null || launched.trim().isEmpty()) {
            File shared = new File(System.getProperty("user.home") + File.separator + ".runelite",
                    "credentials.properties");
            if (!shared.getAbsolutePath().equalsIgnoreCase(defaultCredentialsFile().getAbsolutePath())) {
                writeToFile(shared, account);
            }
        }
        return ok;
    }

    private static ParsedJagexAccount build(Map<String, String> props) {
        String displayName = first(props, "JX_DISPLAY_NAME", "displayName", "display_name");
        if (displayName == null || displayName.isEmpty()) {
            displayName = "Onbekend";
        }
        return new ParsedJagexAccount(displayName, props);
    }

    private static String first(Map<String, String> props, String... keys) {
        for (String k : keys) {
            String v = props.get(k);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
