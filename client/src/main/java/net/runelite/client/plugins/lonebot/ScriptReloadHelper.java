package net.runelite.client.plugins.lonebot;

import com.lonebot.client.LoneBotInstallRoot;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.LoneBotPaths;
import net.storm.sdk.loop.LoopHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Bouwt / kopieert script-jars en hot-reload (Imp / WC / example apart van LoneBot client).
 */
public final class ScriptReloadHelper {

    private static final Logger log = LoggerFactory.getLogger(ScriptReloadHelper.class);
    private static final ConcurrentHashMap<String, Long> LIB_SYNC_LOG_AT = new ConcurrentHashMap<>();

    private ScriptReloadHelper() {
    }

    public static File projectRoot() {
        File found = projectRootOrNull();
        return found != null ? found : new File(System.getProperty("user.dir", "."));
    }

    /** Gradle-bronboom, of {@code null} op een PC die alleen de GitHub-zip heeft. */
    public static File projectRootOrNull() {
        String prop = System.getProperty("lonebot.projectRoot");
        if (prop != null && !prop.isBlank()) {
            File f = new File(prop.trim());
            if (new File(f, "gradlew.bat").isFile()) {
                return f;
            }
        }
        File hint = new File("C:\\Users\\lonestruggle\\Desktop\\lonebot-client");
        if (new File(hint, "gradlew.bat").isFile()) {
            return hint;
        }
        File cwd = new File(System.getProperty("user.dir", "."));
        if (new File(cwd, "gradlew.bat").isFile()) {
            return cwd;
        }
        File parent = cwd.getParentFile();
        if (parent != null && new File(parent, "gradlew.bat").isFile()) {
            return parent;
        }
        return null;
    }

    public static File buildAndStageImpJar(Consumer<String> status) {
        return buildAndStage(status, ":script-imp-killer:jar",
                "script-imp-killer\\build\\libs\\imp-killer.jar",
                LoneBotPaths.impKillerJar());
    }

    public static File buildAndStageWoodcutterJar(Consumer<String> status) {
        return buildAndStage(status, ":script-woodcutter:jar",
                "script-woodcutter\\build\\libs\\woodcutter.jar",
                LoneBotPaths.woodcutterJar());
    }

    public static File buildAndStageFishingJar(Consumer<String> status) {
        return buildAndStage(status, ":script-fishing:jar",
                "script-fishing\\build\\libs\\fishing.jar",
                LoneBotPaths.fishingJar());
    }

    public static File buildAndStageImps2Jar(Consumer<String> status) {
        return buildAndStage(status, ":script-imps2:jar",
                "script-imps2\\build\\libs\\imps2.jar",
                LoneBotPaths.imps2Jar());
    }

    public static File buildAndStageStarMinerJar(Consumer<String> status) {
        return buildAndStage(status, ":script-star-miner:jar",
                "script-star-miner\\build\\libs\\star-miner.jar",
                LoneBotPaths.starMinerJar());
    }

    public static File buildAndStageGiantsJar(Consumer<String> status) {
        return buildAndStage(status, ":script-giants:jar",
                "script-giants\\build\\libs\\giants.jar",
                LoneBotPaths.giantsJar());
    }

    public static File buildAndStageClueJar(Consumer<String> status) {
        return buildAndStage(status, ":script-clue:jar",
                "script-clue\\build\\libs\\clue.jar",
                LoneBotPaths.clueJar());
    }

    public static File buildAndStageQuesterJar(Consumer<String> status) {
        return buildAndStage(status, ":script-quest:jar",
                "script-quest\\build\\libs\\quest.jar",
                LoneBotPaths.questJar());
    }

    public static File buildAndStageExampleJar(Consumer<String> status) {
        return buildAndStage(status, ":example-plugin:jar",
                "example-plugin\\build\\libs\\example-plugin.jar",
                LoneBotPaths.exampleScriptsJar());
    }

    public static File buildAndStage(Consumer<String> status, String gradleTask,
                                      String relativeJar, File dest) {
        try {
            LoneBotPaths.ensureDirs();
            if (isGameClientRole()) {
                status.accept("Client bouwt niet — kopieer jar…");
                File copied = copyBuiltIfNewer(relativeJar, dest, status);
                if (copied != null && copied.isFile() && new File(projectRoot(), relativeJar).isFile()) {
                    return copied;
                }
                File fromLib = stageFromInstallLib(relativeJar, dest, status);
                return fromLib != null ? fromLib : copied;
            }
            File root = projectRootOrNull();
            File gradlew = root != null ? new File(root, "gradlew.bat") : null;
            if (gradlew == null || !gradlew.isFile()) {
                File fromLib = stageFromInstallLib(relativeJar, dest, status);
                if (fromLib != null) {
                    return fromLib;
                }
                status.accept("⚠ Geen Gradle-bron en geen client lib/" + jarFileName(relativeJar)
                        + " — deze PC heeft alleen een zip; gebruik GitHub-update of kopieer lib-jar naar ~/.lonebot/scripts");
                return null;
            }
            status.accept("Bouwen " + gradleTask + "…");
            ProcessBuilder pb = new ProcessBuilder(gradlew.getAbsolutePath(), gradleTask);
            pb.directory(root);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (out.length() < 4000) {
                        out.append(line).append('\n');
                    }
                }
            }
            int code = p.waitFor();
            if (code != 0) {
                log.warn("gradle {} exit {}: {}", gradleTask, code, out);
                status.accept("⚠ Build mislukt (exit " + code + ") — zie logs");
                return null;
            }
            File built = new File(root, relativeJar);
            if (!built.isFile()) {
                File libs = built.getParentFile();
                File[] jars = libs != null && libs.isDirectory()
                        ? libs.listFiles((d, n) -> n.endsWith(".jar")
                        && !n.endsWith("-sources.jar") && !n.endsWith("-javadoc.jar"))
                        : null;
                if (jars != null && jars.length > 0) {
                    built = jars[0];
                }
            }
            if (!built.isFile()) {
                status.accept("⚠ Jar niet gevonden na build");
                return null;
            }
            File staged = copyJarUnlocked(built, dest, status);
            if (staged == null) {
                return null;
            }
            status.accept("✓ " + staged.getName() + " → " + staged.getParent());
            return staged;
        } catch (Exception e) {
            log.warn("buildAndStage: {}", e.toString());
            status.accept("⚠ " + dest.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    public static boolean isGameClientRole() {
        return "client".equalsIgnoreCase(System.getProperty("lonebot.role", ""));
    }

    /**
     * Kopieer project {@code build/libs} → {@code ~/.lonebot/scripts} als de build nieuwer is.
     * Geen Gradle — veilig in de game-client (voorkomt freeze).
     */
    public static File copyBuiltIfNewer(String relativeJar, File dest, Consumer<String> status) {
        if (dest == null) {
            return null;
        }
        LoneBotPaths.ensureDirs();
        File built = new File(projectRoot(), relativeJar);
        if (!built.isFile()) {
            File existing = newestStaged(dest);
            return existing != null && existing.isFile() ? existing : null;
        }
        File staged = newestStaged(dest);
        if (staged != null && staged.isFile() && staged.lastModified() >= built.lastModified()) {
            return staged;
        }
        File copied = copyJarUnlocked(built, dest, status != null ? status : s -> {
        });
        return copied != null ? copied : newestStaged(dest);
    }

    /** Launcher-start / vóór client-launch: jars stagen. Game-client: alleen copy. */
    public static void stageAllScripts(Consumer<String> status) {
        Consumer<String> s = status != null ? status : msg -> {
        };
        if (isGameClientRole()) {
            prefetchStagedJarsFromBuild(s);
            return;
        }
        File imp = buildAndStageImpJar(s);
        File wc = buildAndStageWoodcutterJar(s);
        File fish = buildAndStageFishingJar(s);
        File imps2 = buildAndStageImps2Jar(s);
        File star = buildAndStageStarMinerJar(s);
        File giants = buildAndStageGiantsJar(s);
        File clue = buildAndStageClueJar(s);
        File quest = buildAndStageQuesterJar(s);
        File ex = buildAndStageExampleJar(s);
        int n = 0;
        if (imp != null) {
            n++;
        }
        if (wc != null) {
            n++;
        }
        if (fish != null) {
            n++;
        }
        if (imps2 != null) {
            n++;
        }
        if (star != null) {
            n++;
        }
        if (giants != null) {
            n++;
        }
        if (clue != null) {
            n++;
        }
        if (quest != null) {
            n++;
        }
        if (ex != null) {
            n++;
        }
        s.accept(n > 0 ? "Scripts gestaged (" + n + " jars) → ~/.lonebot/scripts" : "⚠ Geen script-jars gestaged");
    }

    /** Gradle-pad + lib-naam — zelfde set als {@link LoneBotPaths#STAGED_SCRIPT_JARS}. */
    private static final String[][] SCRIPT_STAGE = {
            {"script-imp-killer\\build\\libs\\imp-killer.jar", "imp-killer.jar"},
            {"script-woodcutter\\build\\libs\\woodcutter.jar", "woodcutter.jar"},
            {"script-fishing\\build\\libs\\fishing.jar", "fishing.jar"},
            {"script-imps2\\build\\libs\\imps2.jar", "imps2.jar"},
            {"script-star-miner\\build\\libs\\star-miner.jar", "star-miner.jar"},
            {"script-giants\\build\\libs\\giants.jar", "giants.jar"},
            {"script-clue\\build\\libs\\clue.jar", "clue.jar"},
            {"script-quest\\build\\libs\\quest.jar", "quest.jar"},
            {"example-plugin\\build\\libs\\example-plugin.jar", "example-plugin.jar"},
    };

    public static void prefetchStagedJarsFromBuild(Consumer<String> status) {
        Consumer<String> s = status != null ? status : msg -> {
        };
        for (String[] row : SCRIPT_STAGE) {
            syncStagedJar(row[0], row[1], LoneBotPaths.stagedScriptJar(row[1]), s);
        }
    }

    /**
     * Dev-PC: Gradle {@code build/libs} wint. Andere PC (alleen GitHub-zip):
     * {@code install/lib/*.jar} overschrijft {@code ~/.lonebot/scripts} — anders blijft
     * een oude Imp-jar na een client-update.
     */
    private static void syncStagedJar(String relativeJar, String libName, File dest, Consumer<String> status) {
        File gradleRoot = projectRootOrNull();
        File gradle = gradleRoot != null ? new File(gradleRoot, relativeJar) : null;
        if (gradle != null && gradle.isFile()) {
            copyBuiltIfNewer(relativeJar, dest, status);
            return;
        }
        File lib = installLibJar(libName);
        if (lib == null || dest == null) {
            return;
        }
        File staged = newestStaged(dest);
        if (staged != null && staged.isFile()
                && staged.length() == lib.length()
                && staged.lastModified() >= lib.lastModified()) {
            return;
        }
        File copied = copyJarUnlocked(lib, dest, status);
        if (copied == null) {
            return;
        }
        log.info("[Scripts] {} uit client lib → {}", libName, copied.getAbsolutePath());
        long now = System.currentTimeMillis();
        Long prev = LIB_SYNC_LOG_AT.get(libName);
        if (prev == null || now - prev >= 1500L) {
            LIB_SYNC_LOG_AT.put(libName, now);
            BotRuntime.logConsole("[Scripts] GitHub-client lib/" + libName + " → ~/.lonebot/scripts");
        }
    }

    private static File stageFromInstallLib(String relativeJar, File dest, Consumer<String> status) {
        String libName = jarFileName(relativeJar);
        File lib = installLibJar(libName);
        if (lib == null || dest == null) {
            return null;
        }
        LoneBotPaths.ensureDirs();
        File copied = copyJarUnlocked(lib, dest, status);
        if (copied == null) {
            return null;
        }
        if (status != null) {
            status.accept("✓ " + libName + " uit GitHub-client lib → " + copied.getParent());
        }
        BotRuntime.logConsole("[Scripts] Update zonder Gradle: lib/" + libName + " → ~/.lonebot/scripts");
        return copied;
    }

    private static String jarFileName(String relativeJar) {
        if (relativeJar == null || relativeJar.isBlank()) {
            return "";
        }
        String n = relativeJar.replace('\\', '/');
        int i = n.lastIndexOf('/');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    private static File installLibJar(String name) {
        Path root = LoneBotInstallRoot.detect();
        if (root != null) {
            File f = root.resolve("lib").resolve(name).toFile();
            if (f.isFile()) {
                return f;
            }
        }
        File cwd = new File(System.getProperty("user.dir", "."));
        File[] guesses = {
                new File(cwd, "lib" + File.separator + name),
                new File(cwd.getParentFile() != null ? cwd.getParentFile() : cwd, "lib" + File.separator + name),
        };
        for (File g : guesses) {
            if (g.isFile()) {
                return g;
            }
        }
        return null;
    }

    /**
     * Overschrijf staging-jar. Eerst copy zonder unload (snapshot-loader lockt deze file niet).
     * Bij Windows-lock: classloader dicht, retries, anders {@code *.next.jar}.
     */
    private static File copyJarUnlocked(File built, File dest, Consumer<String> status) {
        if (tryCopyJar(built, dest)) {
            return dest;
        }
        releaseLoaderForDest(dest);
        for (int i = 0; i < 5; i++) {
            if (tryCopyJar(built, dest)) {
                return dest;
            }
            try {
                System.gc();
                Thread.sleep(200L * (i + 1));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        try {
            File next = nextJar(dest);
            Files.copy(built.toPath(), next.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (status != null) {
                status.accept("⚠ " + dest.getName() + " in gebruik → " + next.getName()
                        + " (herstart die game-client één keer)");
            }
            return next;
        } catch (Exception e) {
            if (status != null) {
                status.accept("⚠ " + dest.getAbsolutePath() + ": " + e.getMessage());
            }
            return null;
        }
    }

    private static boolean tryCopyJar(File built, File dest) {
        try {
            Files.copy(built.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File next = nextJar(dest);
            if (next.isFile()) {
                try {
                    Files.deleteIfExists(next.toPath());
                } catch (Exception ignored) {
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static File nextJar(File dest) {
        String n = dest.getName();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        return new File(dest.getParentFile(), base + ".next.jar");
    }

    public static File newestStaged(File dest) {
        if (dest == null) {
            return null;
        }
        File next = nextJar(dest);
        if (next.isFile() && (!dest.isFile() || next.lastModified() >= dest.lastModified())) {
            return next;
        }
        return dest;
    }

    private static void releaseLoaderForDest(File dest) {
        if (dest == null) {
            return;
        }
        String n = dest.getName().toLowerCase(Locale.ROOT);
        try {
            if (n.contains("fishing")) {
                LoopHost.releaseLoader("fishing");
            } else if (n.contains("woodcutter")) {
                LoopHost.releaseLoader("woodcutter");
            } else if (n.contains("imps2")) {
                LoopHost.releaseLoader("imps2");
            } else if (n.contains("star-miner") || n.contains("starminer")) {
                LoopHost.releaseLoader("star");
            } else if (n.contains("giants")) {
                LoopHost.releaseLoader("giants");
            } else if (n.contains("clue")) {
                LoopHost.releaseLoader("clue");
            } else if (n.contains("quest")) {
                LoopHost.releaseLoader("quest");
            } else if (n.contains("imp")) {
                LoopHost.releaseLoader("imp");
            } else if (n.contains("example")) {
                LoopHost.releaseLoader("example");
            }
        } catch (Throwable ignored) {
        }
    }

    public static String reloadImpInThisClient() {
        File jar = newestStaged(LoneBotPaths.impKillerJar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "script-imp-killer\\build\\libs\\imp-killer.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Imp jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadImpJar(jar);
        try {
            LoneBotBootstrapPlugin.applyImpSettingsFromUi();
        } catch (Throwable ignored) {
        }
        BotRuntime.loadedBotClass = msg;
        return msg + " | Imp v" + BotRuntime.impPluginVersion;
    }

    public static String reloadWoodcutterInThisClient() {
        File jar = newestStaged(LoneBotPaths.woodcutterJar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "script-woodcutter\\build\\libs\\woodcutter.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Woodcutter jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadWoodcutterJar(jar);
        try {
            LoneBotBootstrapPlugin.applyWcSettingsFromUi();
        } catch (Throwable ignored) {
        }
        try {
            for (var p : LoopHost.plugins()) {
                if (p != null && "WoodcutterPlugin".equals(p.getClass().getSimpleName())) {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.wcPluginVersion = String.valueOf(ver);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        BotRuntime.loadedBotClass = msg;
        return msg + " | WC v" + BotRuntime.wcPluginVersion;
    }

    public static String reloadFishingInThisClient() {
        copyBuiltIfNewer("script-fishing\\build\\libs\\fishing.jar", LoneBotPaths.fishingJar(), s -> {
        });
        File jar = newestStaged(LoneBotPaths.fishingJar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "script-fishing\\build\\libs\\fishing.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Fishing jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadFishingJar(jar);
        try {
            LoneBotBootstrapPlugin.applyFishSettingsFromUi();
        } catch (Throwable ignored) {
        }
        try {
            for (var p : LoopHost.plugins()) {
                if (p != null && "FishingPlugin".equals(p.getClass().getSimpleName())) {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.fishPluginVersion = String.valueOf(ver);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        BotRuntime.loadedBotClass = msg;
        return msg + " | Fish v" + BotRuntime.fishPluginVersion;
    }

    public static String reloadImps2InThisClient() {
        copyBuiltIfNewer("script-imps2\\build\\libs\\imps2.jar", LoneBotPaths.imps2Jar(), s -> {
        });
        File jar = newestStaged(LoneBotPaths.imps2Jar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "script-imps2\\build\\libs\\imps2.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Imps2 jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadImps2Jar(jar);
        try {
            for (var p : LoopHost.plugins()) {
                if (p != null && "Imps2Plugin".equals(p.getClass().getSimpleName())) {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.imps2PluginVersion = String.valueOf(ver);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        BotRuntime.loadedBotClass = msg;
        return msg + " | Imps2 v" + BotRuntime.imps2PluginVersion;
    }

    public static String reloadStarMinerInThisClient() {
        copyBuiltIfNewer("script-star-miner\\build\\libs\\star-miner.jar", LoneBotPaths.starMinerJar(), s -> {
        });
        File jar = newestStaged(LoneBotPaths.starMinerJar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "script-star-miner\\build\\libs\\star-miner.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Star Miner jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadStarMinerJar(jar);
        try {
            LoneBotBootstrapPlugin.applyStarSettingsFromUi();
        } catch (Throwable ignored) {
        }
        try {
            for (var p : LoopHost.plugins()) {
                if (p != null && "StarMinerPlugin".equals(p.getClass().getSimpleName())) {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.starPluginVersion = String.valueOf(ver);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        BotRuntime.loadedBotClass = msg;
        return msg + " | Star v" + BotRuntime.starPluginVersion;
    }

    public static String reloadGiantsInThisClient() {
        copyBuiltIfNewer("script-giants\\build\\libs\\giants.jar", LoneBotPaths.giantsJar(), s -> {
        });
        File jar = newestStaged(LoneBotPaths.giantsJar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "script-giants\\build\\libs\\giants.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Giants jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadGiantsJar(jar);
        try {
            LoneBotBootstrapPlugin.applyGiantsSettingsFromUi();
        } catch (Throwable ignored) {
        }
        try {
            for (var p : LoopHost.plugins()) {
                if (p != null && "GiantsPlugin".equals(p.getClass().getSimpleName())) {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.giantsPluginVersion = String.valueOf(ver);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        BotRuntime.loadedBotClass = msg;
        return msg + " | Giants v" + BotRuntime.giantsPluginVersion;
    }

    public static String reloadClueInThisClient() {
        copyBuiltIfNewer("script-clue\\build\\libs\\clue.jar", LoneBotPaths.clueJar(), s -> {
        });
        File jar = newestStaged(LoneBotPaths.clueJar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "script-clue\\build\\libs\\clue.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Clue jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadClueJar(jar);
        try {
            for (var p : LoopHost.plugins()) {
                if (p != null && "CluePlugin".equals(p.getClass().getSimpleName())) {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.cluePluginVersion = String.valueOf(ver);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        BotRuntime.loadedBotClass = msg;
        return msg + " | Clue v" + BotRuntime.cluePluginVersion;
    }

    public static String reloadQuesterInThisClient() {
        copyBuiltIfNewer("script-quest\\build\\libs\\quest.jar", LoneBotPaths.questJar(), s -> {
        });
        File jar = newestStaged(LoneBotPaths.questJar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "script-quest\\build\\libs\\quest.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Quest jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadQuesterJar(jar);
        try {
            LoneBotBootstrapPlugin.applyQuestSettingsFromUi();
        } catch (Throwable ignored) {
        }
        try {
            for (var p : LoopHost.plugins()) {
                if (p != null && "QuesterPlugin".equals(p.getClass().getSimpleName())) {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.questPluginVersion = String.valueOf(ver);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        BotRuntime.loadedBotClass = msg;
        return msg + " | Quest v" + BotRuntime.questPluginVersion;
    }

    public static String reloadExampleInThisClient() {
        File jar = newestStaged(LoneBotPaths.exampleScriptsJar());
        if (!jar.isFile()) {
            File root = projectRoot();
            File built = new File(root, "example-plugin\\build\\libs\\example-plugin.jar");
            if (built.isFile()) {
                jar = built;
            }
        }
        if (!jar.isFile()) {
            return "Example jar niet gevonden — eerst builden";
        }
        String msg = LoopHost.hotReloadExampleJar(jar);
        BotRuntime.loadedBotClass = msg;
        return msg;
    }

    /**
     * Laad alle beschikbare staged jars uit {@code ~/.lonebot/scripts/} in deze client.
     * Geen gradle-build — alleen hot-reload als jar bestaat.
     */
    public static String reloadStagedInThisClient() {
        prefetchStagedJarsFromBuild(s -> {
        });
        StringBuilder sb = new StringBuilder();
        if (newestStaged(LoneBotPaths.impKillerJar()).isFile()) {
            appendReload(sb, "Imp", reloadImpInThisClient());
        }
        if (newestStaged(LoneBotPaths.woodcutterJar()).isFile()) {
            appendReload(sb, "WC", reloadWoodcutterInThisClient());
        }
        if (newestStaged(LoneBotPaths.fishingJar()).isFile()) {
            appendReload(sb, "Fish", reloadFishingInThisClient());
        }
        if (newestStaged(LoneBotPaths.imps2Jar()).isFile()) {
            appendReload(sb, "Imps2", reloadImps2InThisClient());
        }
        if (newestStaged(LoneBotPaths.starMinerJar()).isFile()) {
            appendReload(sb, "Star", reloadStarMinerInThisClient());
        }
        if (newestStaged(LoneBotPaths.giantsJar()).isFile()) {
            appendReload(sb, "Giants", reloadGiantsInThisClient());
        }
        if (newestStaged(LoneBotPaths.clueJar()).isFile()) {
            appendReload(sb, "Clue", reloadClueInThisClient());
        }
        if (newestStaged(LoneBotPaths.questJar()).isFile()) {
            appendReload(sb, "Quest", reloadQuesterInThisClient());
        }
        if (newestStaged(LoneBotPaths.exampleScriptsJar()).isFile()) {
            appendReload(sb, "Example", reloadExampleInThisClient());
        }
        if (sb.length() == 0) {
            String msg = "Geen staged jars — builtin scripts";
            BotRuntime.logConsole("[Scripts] startup: " + msg);
            return msg;
        }
        String msg = sb.toString();
        BotRuntime.logConsole("[Scripts] startup staged: " + msg);
        return msg;
    }

    /**
     * Stuur RELOAD_SCRIPTS naar alle live game-clients (launcher herstart / restore).
     * Clients laden zelf staged jars via {@link #reloadStagedInThisClient()}.
     */
    public static int reloadStagedToLiveClients(Consumer<String> status) {
        java.util.Set<String> live = ClientRemoteBus.listLiveDisplayNames();
        if (live.isEmpty()) {
            if (status != null) {
                status.accept("Geen live clients — staged reload overgeslagen");
            }
            return 0;
        }
        int n = 0;
        for (String name : live) {
            if (ClientRemoteBus.sendCommand(name, ClientRemoteBus.Command.RELOAD_SCRIPTS)) {
                n++;
            }
        }
        String msg = "Staged scripts → RELOAD naar " + n + "/" + live.size() + " client(s)";
        log.info(msg);
        if (status != null) {
            status.accept(msg);
        }
        return n;
    }

    private static void appendReload(StringBuilder sb, String label, String result) {
        if (sb.length() > 0) {
            sb.append(" | ");
        }
        sb.append(label).append(':').append(result != null ? result : "?");
    }

    /** Bouw + hot-reload Imp, WC en example in deze client. */
    public static String reloadAllInThisClient(Consumer<String> status) {
        StringBuilder sb = new StringBuilder();
        File imp = buildAndStageImpJar(status);
        if (imp != null) {
            sb.append(reloadImpInThisClient());
        } else {
            sb.append("Imp: build fail");
        }
        sb.append(" || ");
        File wc = buildAndStageWoodcutterJar(status);
        if (wc != null) {
            sb.append(reloadWoodcutterInThisClient());
        } else {
            sb.append("WC: build fail");
        }
        sb.append(" || ");
        File fish = buildAndStageFishingJar(status);
        if (fish != null) {
            sb.append(reloadFishingInThisClient());
        } else {
            sb.append("Fish: build fail");
        }
        sb.append(" || ");
        File imps2 = buildAndStageImps2Jar(status);
        if (imps2 != null) {
            sb.append(reloadImps2InThisClient());
        } else {
            sb.append("Imps2: build fail");
        }
        sb.append(" || ");
        File star = buildAndStageStarMinerJar(status);
        if (star != null) {
            sb.append(reloadStarMinerInThisClient());
        } else {
            sb.append("Star: build fail");
        }
        sb.append(" || ");
        File giants = buildAndStageGiantsJar(status);
        if (giants != null) {
            sb.append(reloadGiantsInThisClient());
        } else {
            sb.append("Giants: build fail");
        }
        sb.append(" || ");
        File clue = buildAndStageClueJar(status);
        if (clue != null) {
            sb.append(reloadClueInThisClient());
        } else {
            sb.append("Clue: build fail");
        }
        sb.append(" || ");
        File quest = buildAndStageQuesterJar(status);
        if (quest != null) {
            sb.append(reloadQuesterInThisClient());
        } else {
            sb.append("Quest: build fail");
        }
        sb.append(" || ");
        File ex = buildAndStageExampleJar(status);
        if (ex != null) {
            sb.append(reloadExampleInThisClient());
        } else {
            sb.append("Example: build fail");
        }
        return sb.toString();
    }

    /** Launcher: bouw Imp + stuur RELOAD_SCRIPTS naar alle live clients. */
    public static void buildImpAndNotifyClients(Consumer<String> status) {
        File jar = buildAndStageImpJar(status);
        if (jar == null) {
            return;
        }
        int n = notifyReload(status);
        status.accept("Imp jar klaar — RELOAD naar " + n + " client(s).");
    }

    /** Launcher: bouw alle scripts + remote reload. */
    public static void buildAllScriptsAndNotifyClients(Consumer<String> status) {
        int unlocked = notifyUnload(status);
        if (unlocked > 0) {
            status.accept("Jars ontgrendelen op " + unlocked + " client(s)…");
            try {
                Thread.sleep(800L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        File imp = buildAndStageImpJar(status);
        File wc = buildAndStageWoodcutterJar(status);
        File fish = buildAndStageFishingJar(status);
        File imps2 = buildAndStageImps2Jar(status);
        File star = buildAndStageStarMinerJar(status);
        File giants = buildAndStageGiantsJar(status);
        File clue = buildAndStageClueJar(status);
        File quest = buildAndStageQuesterJar(status);
        File ex = buildAndStageExampleJar(status);
        if (imp == null && wc == null && fish == null && imps2 == null && star == null
                && giants == null && clue == null && quest == null && ex == null) {
            status.accept("⚠ Geen script-jars gebouwd");
            return;
        }
        int n = notifyReload(status);
        status.accept("Scripts klaar (Imp/WC/Fish/Imps2/Star/Giants/Clue/Quest/example) — RELOAD naar " + n + " client(s).");
    }

    /** Launcher Scripts-tab: één script bouwen + UNLOAD/RELOAD naar live clients. */
    public static void buildOneAndNotifyClients(LoneBotScriptCatalog.Entry entry, Consumer<String> status) {
        if (entry == null) {
            status.accept("Geen script geselecteerd");
            return;
        }
        int unlocked = notifyUnload(status);
        if (unlocked > 0) {
            status.accept("Jars ontgrendelen op " + unlocked + " client(s)…");
            try {
                Thread.sleep(800L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        File jar = LoneBotScriptCatalog.buildAndStage(entry, status);
        if (jar == null) {
            status.accept("⚠ " + entry.name + " build mislukt");
            return;
        }
        int n = notifyReload(status);
        status.accept(entry.name + " v" + entry.version + " klaar — RELOAD naar " + n + " client(s).");
    }

    private static int notifyUnload(Consumer<String> status) {
        int n = 0;
        for (String name : ClientRemoteBus.listLiveDisplayNames()) {
            if (ClientRemoteBus.sendCommand(name, ClientRemoteBus.Command.UNLOAD_SCRIPTS)) {
                n++;
            }
        }
        return n;
    }

    private static int notifyReload(Consumer<String> status) {
        int n = 0;
        for (String name : ClientRemoteBus.listLiveDisplayNames()) {
            if (ClientRemoteBus.sendCommand(name, ClientRemoteBus.Command.RELOAD_SCRIPTS)) {
                n++;
            }
        }
        return n;
    }
}
