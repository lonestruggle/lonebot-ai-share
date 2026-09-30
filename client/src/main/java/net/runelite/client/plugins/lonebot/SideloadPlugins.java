package net.runelite.client.plugins.lonebot;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.LoneBotPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Laadt extra RuneLite-plugins uit {@code ~/.lonebot/sideloaded-plugins/*.jar}
 * zonder Plugin Hub. Nieuwe plugins: jar in die map, client herstarten.
 */
final class SideloadPlugins {

    private static final Logger log = LoggerFactory.getLogger(SideloadPlugins.class);
    /** Houd classloaders levend — anders verdwijnen sideload-klassen. */
    private static final List<ClassLoader> LOADERS = new CopyOnWriteArrayList<>();
    private static final String README = ""
            + "LoneBot sideload-plugins\r\n"
            + "========================\r\n"
            + "Zet hier .jar-bestanden van RuneLite-plugins (geen Plugin Hub).\r\n"
            + "Voorbeeld: watchdog.jar\r\n"
            + "Daarna de game-client volledig herstarten (niet alleen Scripts-reload).\r\n"
            + "Zet de plugin aan in de RuneLite plugin-lijst als die uit staat.\r\n";

    private SideloadPlugins() {
    }

    static void load(PluginManager pluginManager) {
        if (pluginManager == null) {
            return;
        }
        File dir = LoneBotPaths.sideloadedPluginsDir();
        try {
            LoneBotPaths.ensureDirs();
            writeReadme(dir);
        } catch (Throwable t) {
            log.warn("[Sideload] map aanmaken: {}", t.toString());
        }
        File[] jars = dir.listFiles((d, n) -> n != null && n.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            log.info("[Sideload] geen jars in {}", dir.getAbsolutePath());
            BotRuntime.logConsole("[Sideload] geen jars — zet .jar in " + dir.getAbsolutePath());
            return;
        }
        List<Plugin> loaded = new ArrayList<>();
        for (File jar : jars) {
            try {
                List<Plugin> part = loadJar(pluginManager, jar);
                loaded.addAll(part);
                String msg = "[Sideload] " + jar.getName() + " → " + part.size() + " plugin(s)";
                log.info(msg);
                BotRuntime.logConsole(msg);
            } catch (Throwable t) {
                String msg = "[Sideload] FAIL " + jar.getName() + ": " + t.getClass().getSimpleName()
                        + " " + (t.getMessage() != null ? t.getMessage() : "");
                log.warn(msg, t);
                BotRuntime.logConsole(msg);
            }
        }
        if (loaded.isEmpty()) {
            return;
        }
        try {
            pluginManager.loadDefaultPluginConfiguration(loaded);
        } catch (Throwable t) {
            log.warn("[Sideload] default config: {}", t.toString());
        }
        List<Plugin> toStart = new ArrayList<>(loaded);
        SwingUtilities.invokeLater(() -> {
            for (Plugin p : toStart) {
                try {
                    if (pluginManager.isPluginEnabled(p)) {
                        pluginManager.startPlugin(p);
                        BotRuntime.logConsole("[Sideload] gestart " + pluginName(p));
                    } else {
                        BotRuntime.logConsole("[Sideload] uit (zet aan in plugin-lijst): " + pluginName(p));
                    }
                } catch (Throwable t) {
                    BotRuntime.logConsole("[Sideload] start FAIL " + pluginName(p) + ": "
                            + t.getClass().getSimpleName());
                    log.warn("[Sideload] start {}", p.getClass().getName(), t);
                }
            }
        });
    }

    private static List<Plugin> loadJar(PluginManager pluginManager, File jar) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                SideloadPlugins.class.getClassLoader());
        LOADERS.add(cl);
        List<Class<?>> classes = pluginClasses(cl, jar);
        if (classes.isEmpty()) {
            throw new IllegalStateException("geen @PluginDescriptor in jar");
        }
        return pluginManager.loadPlugins(classes, null);
    }

    private static List<Class<?>> pluginClasses(ClassLoader cl, File jar) throws Exception {
        List<String> names = pluginClassNames(jar);
        List<Class<?>> out = new ArrayList<>();
        if (!names.isEmpty()) {
            for (String name : names) {
                out.add(cl.loadClass(name));
            }
            return out;
        }
        try (JarFile jf = new JarFile(jar)) {
            boolean fatClientJar = jf.getEntry("net/runelite/client/RuneLite.class") != null
                    || jf.getEntry("net/runelite/client/plugins/gpu/GpuPlugin.class") != null;
            var en = jf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith("Plugin.class") || n.contains("$")) {
                    continue;
                }
                String cn = n.substring(0, n.length() - 6).replace('/', '.');
                if (fatClientJar && cn.startsWith("net.runelite.")) {
                    continue;
                }
                Class<?> c;
                try {
                    c = cl.loadClass(cn);
                } catch (Throwable ignored) {
                    continue;
                }
                if (Plugin.class.isAssignableFrom(c) && c.getAnnotation(PluginDescriptor.class) != null) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static List<String> pluginClassNames(File jar) {
        List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            ZipEntry e = jf.getEntry("runelite-plugin.properties");
            if (e == null) {
                return names;
            }
            Properties p = new Properties();
            try (InputStream in = jf.getInputStream(e)) {
                p.load(in);
            }
            String raw = p.getProperty("plugins", "");
            for (String part : raw.split(",")) {
                String n = part.trim();
                if (!n.isEmpty()) {
                    names.add(n);
                }
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    private static String pluginName(Plugin p) {
        PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class);
        if (d != null && d.name() != null && !d.name().isBlank()) {
            return d.name();
        }
        return p.getClass().getSimpleName();
    }

    private static void writeReadme(File dir) {
        File readme = new File(dir, "README.txt");
        if (readme.isFile()) {
            return;
        }
        try {
            Files.write(readme.toPath(), README.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }
}
