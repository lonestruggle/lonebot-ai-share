package com.lonebot.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Entry point — starts RuneLite with LoneBot branding and classpath plugins.
 */
public final class LoneBotMain {

    private static final Logger log = LoggerFactory.getLogger(LoneBotMain.class);

    private LoneBotMain() {
    }

    public static void main(String[] args) {
        System.setProperty("lonebot.role",
                System.getProperty("lonebot.role", "client"));
        System.setProperty("runelite.title",
                System.getProperty("runelite.title", "LoneBot"));
        // Respecteer launcher -Drunelite.dir / -Djagex.userhome (multi-client profiles)
        if (System.getProperty("runelite.dir") == null || System.getProperty("runelite.dir").isEmpty()) {
            String loneHome = System.getProperty("user.home") + "/.lonebot";
            System.setProperty("runelite.dir", loneHome);
        }
        System.setProperty("runelite.pluginhub.enable", "false");
        if (System.getProperty("jagex.userhome") == null || System.getProperty("jagex.userhome").isEmpty()) {
            String jagexHome = System.getProperty("user.home") + File.separator + ".runelite";
            System.setProperty("jagex.userhome", jagexHome);
        }
        LoneBotInstallRoot.applyClientBatPropertyIfMissing();
        if (System.getProperty("lonebot.projectRoot") == null) {
            File hint = new File("C:\\Users\\lonestruggle\\Desktop\\lonebot-client");
            if (new File(hint, "gradlew.bat").isFile()) {
                System.setProperty("lonebot.projectRoot", hint.getAbsolutePath());
            }
        }
        String loneHome = System.getProperty("runelite.dir");
        String jagexHome = System.getProperty("jagex.userhome");
        String credPath = System.getProperty("runelite.credentials.path", "(default credentials.properties)");
        log.info("LoneBot starting — config {} | jagex.userhome {} | creds={} | account={}",
                loneHome, jagexHome, credPath, System.getProperty("lonebot.account", "(default)"));
        log.info("Launching RuneLite client shell (LoneBot fork)");
        try {
            String[] rlArgs = com.lonebot.launcher.RuneliteDeveloperMode.withClientArgs(args);
            net.runelite.client.RuneLite.main(rlArgs);
        } catch (Throwable t) {
            log.error("Failed to start RuneLite/LoneBot", t);
            System.err.println("LoneBot failed to start: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}
