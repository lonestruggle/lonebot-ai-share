package net.storm.sdk.bot;

import java.io.File;

/**
 * Standaard paden onder {@code ~/.lonebot}.
 */
public final class LoneBotPaths {

    private LoneBotPaths() {
    }

    public static File home() {
        return new File(System.getProperty("user.home"), ".lonebot");
    }

    /** Per-account data + {@code logs/}. */
    public static File accountsRoot() {
        return new File(home(), "accounts");
    }

    /**
     * Alle skill-jars in {@code client/lib} én {@code ~/.lonebot/scripts}.
     * GitHub-update moet deze allemaal meenemen — niet alleen Imp.
     */
    public static final String[] STAGED_SCRIPT_JARS = {
            "imp-killer.jar",
            "woodcutter.jar",
            "fishing.jar",
            "imps2.jar",
            "star-miner.jar",
            "giants.jar",
            "clue.jar",
            "quest.jar",
            "example-plugin.jar",
    };

    /** Hot-reload script jars (Imp, WC, Fish, Imps2, Star, example). */
    public static File scriptsDir() {
        return new File(home(), "scripts");
    }

    /** Extra RuneLite-plugins (Watchdog e.d.) — geen Plugin Hub. */
    public static File sideloadedPluginsDir() {
        return new File(home(), "sideloaded-plugins");
    }

    public static File stagedScriptJar(String fileName) {
        return new File(scriptsDir(), fileName);
    }

    public static File impKillerJar() {
        return new File(scriptsDir(), "imp-killer.jar");
    }

    public static File exampleScriptsJar() {
        return new File(scriptsDir(), "example-plugin.jar");
    }

    public static File woodcutterJar() {
        return new File(scriptsDir(), "woodcutter.jar");
    }

    public static File fishingJar() {
        return new File(scriptsDir(), "fishing.jar");
    }

    public static File imps2Jar() {
        return new File(scriptsDir(), "imps2.jar");
    }

    public static File starMinerJar() {
        return new File(scriptsDir(), "star-miner.jar");
    }

    public static File giantsJar() {
        return new File(scriptsDir(), "giants.jar");
    }

    public static File clueJar() {
        return new File(scriptsDir(), "clue.jar");
    }

    public static File questJar() {
        return new File(scriptsDir(), "quest.jar");
    }

    /** Gedeelde excluded tiles (alle accounts). */
    public static File excludedTilesFile() {
        return new File(home(), "excluded-tiles.txt");
    }

    /** Langsgelopen deuren/hekken/ladders (zichtingen). */
    public static File walkObstacleSightingsFile() {
        return new File(home(), "walk-obstacle-sightings.tsv");
    }

    /** Doorgelopen Open/Climb-kanten (Shortest Path TSV). */
    public static File walkObstacleTransportsFile() {
        return new File(home(), "walk-obstacle-transports.tsv");
    }

    /** Zorg dat home/accounts/scripts bestaan. */
    public static void ensureDirs() {
        home().mkdirs();
        accountsRoot().mkdirs();
        scriptsDir().mkdirs();
        sideloadedPluginsDir().mkdirs();
    }
}
