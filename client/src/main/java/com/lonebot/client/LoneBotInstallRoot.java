package com.lonebot.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Vindt de map met {@code bin/client.bat} + {@code lib/} — Gradle installDist of portable {@code runtime}.
 */
public final class LoneBotInstallRoot {

    private static final Logger log = LoggerFactory.getLogger(LoneBotInstallRoot.class);

    private LoneBotInstallRoot() {
    }

    /**
     * Install-root of {@code null} als die niet te bepalen is.
     * Volgorde: draaiende jar ({@code .../lib/*.jar}), daarna cwd / {@code runtime} / Gradle-pad.
     */
    public static Path detect() {
        Path fromJar = fromRunningJar();
        if (fromJar != null) {
            return fromJar;
        }
        Path fromCwd = fromWorkingDirectory();
        if (fromCwd != null) {
            return fromCwd;
        }
        return fromClientBatProperty();
    }

    /** Zet {@code lonebot.client.bat} alleen als het bestand echt bestaat (geen hardcoded andere-PC-pad). */
    public static void applyClientBatPropertyIfMissing() {
        if (System.getProperty("lonebot.client.bat") != null
                && !System.getProperty("lonebot.client.bat").isBlank()) {
            Path existing = Paths.get(System.getProperty("lonebot.client.bat").trim());
            if (Files.isRegularFile(existing)) {
                return;
            }
            System.clearProperty("lonebot.client.bat");
        }
        Path root = detect();
        if (root == null) {
            return;
        }
        Path bat = root.resolve("bin").resolve("client.bat");
        if (Files.isRegularFile(bat)) {
            System.setProperty("lonebot.client.bat", bat.toAbsolutePath().toString());
        }
    }

    static boolean isInstallRoot(Path root) {
        return root != null
                && Files.isRegularFile(root.resolve("bin").resolve("client.bat"))
                && Files.isDirectory(root.resolve("lib"));
    }

    private static Path fromRunningJar() {
        try {
            var cs = LoneBotInstallRoot.class.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return null;
            }
            Path p = Paths.get(cs.getLocation().toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) {
                p = p.getParent();
            }
            if (p == null || p.getFileName() == null
                    || !"lib".equalsIgnoreCase(p.getFileName().toString())) {
                return null;
            }
            Path root = p.getParent();
            if (isInstallRoot(root)) {
                log.info("[Updater] install-root via jar: {}", root);
                return root;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path fromWorkingDirectory() {
        try {
            Path cwd = Paths.get("").toAbsolutePath().normalize();
            if (cwd.getFileName() != null
                    && "bin".equalsIgnoreCase(cwd.getFileName().toString())
                    && isInstallRoot(cwd.getParent())) {
                return cwd.getParent();
            }
            for (Path p = cwd; p != null; p = p.getParent()) {
                if (isInstallRoot(p)) {
                    return p;
                }
                Path runtime = p.resolve("runtime");
                if (isInstallRoot(runtime)) {
                    log.info("[Updater] install-root via runtime/: {}", runtime);
                    return runtime;
                }
                Path gradle = p.resolve("client").resolve("build").resolve("install").resolve("client");
                if (isInstallRoot(gradle)) {
                    return gradle;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Path fromClientBatProperty() {
        String prop = System.getProperty("lonebot.client.bat");
        if (prop == null || prop.isBlank()) {
            return null;
        }
        Path bat = Paths.get(prop.trim());
        if (!Files.isRegularFile(bat)) {
            return null;
        }
        Path bin = bat.getParent();
        if (bin == null) {
            return null;
        }
        Path root = bin.getParent();
        return isInstallRoot(root) ? root : null;
    }
}
