package net.runelite.client.plugins.lonebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Schrijft RuneLite Default World-config in de instance-{@code runelite.dir}
 * vóór process-start, zodat de client meteen op die wereld opent.
 * <p>
 * Zonder gekozen werelden: Default World <b>uit</b> (geen stale w399 van een vorige launch).
 */
public final class InstanceDefaultWorldConfig {

    private static final Logger log = LoggerFactory.getLogger(InstanceDefaultWorldConfig.class);

    private InstanceDefaultWorldConfig() {
    }

    /**
     * Kiest één wereld uit de pool (batch-divers + stabiel per account) en schrijft keys.
     *
     * @param avoidWorlds werelden die andere clients in deze Start-batch al kregen
     * @return gekozen wereld-ID, of -1
     */
    public static int writeForLaunch(
            File runeliteDir,
            List<Integer> worlds,
            String accountKey,
            Set<Integer> avoidWorlds) {
        if (runeliteDir == null || worlds == null || worlds.isEmpty()) {
            clearForLaunch(runeliteDir);
            return -1;
        }
        int pick = pickWorld(worlds, accountKey, avoidWorlds);
        if (pick < 300) {
            pick += 300;
        }
        writeWorld(runeliteDir, pick, true);
        log.info("Default world voor launch: w{} (account={}) → {}",
                pick, accountKey, runeliteDir != null ? runeliteDir.getAbsolutePath() : "?");
        return pick;
    }

    /** Zonder hop/werelden: plugin uit, geen oude defaultWorld laten staan. */
    public static void clearForLaunch(File runeliteDir) {
        if (runeliteDir == null) {
            return;
        }
        writeWorld(runeliteDir, 0, false);
        log.info("Default world gewist (geen voorkeur) → {}", runeliteDir.getAbsolutePath());
    }

    /**
     * Unieke wereld per account in een Start-batch: vermijd al gebruikte IDs,
     * anders stabiele hash op accountKey (niet altijd random → w399).
     */
    public static int pickWorld(List<Integer> worlds, String accountKey, Set<Integer> avoidWorlds) {
        List<Integer> norm = new ArrayList<>();
        for (Integer w : worlds) {
            if (w == null || w <= 0) {
                continue;
            }
            int id = w < 300 ? w + 300 : w;
            if (!norm.contains(id)) {
                norm.add(id);
            }
        }
        if (norm.isEmpty()) {
            return -1;
        }
        if (norm.size() == 1) {
            return norm.get(0);
        }
        List<Integer> free = new ArrayList<>();
        for (int id : norm) {
            if (avoidWorlds == null || !avoidWorlds.contains(id)) {
                free.add(id);
            }
        }
        List<Integer> pool = free.isEmpty() ? norm : free;
        String key = accountKey != null ? accountKey : "";
        int idx = Math.floorMod(key.hashCode(), pool.size());
        return pool.get(idx);
    }

    private static void writeWorld(File runeliteDir, int worldId, boolean pluginOn) {
        if (runeliteDir == null) {
            return;
        }
        if (!runeliteDir.isDirectory() && !runeliteDir.mkdirs()) {
            log.warn("Kon runelite.dir niet maken: {}", runeliteDir);
            return;
        }
        mergeSettings(new File(runeliteDir, "settings.properties"), worldId, pluginOn);
        File profiles2 = new File(runeliteDir, "profiles2");
        if (profiles2.isDirectory()) {
            File[] props = profiles2.listFiles((dir, name) ->
                    name != null && name.endsWith(".properties") && !name.startsWith("$"));
            if (props != null) {
                for (File f : props) {
                    mergeSettings(f, worldId, pluginOn);
                }
            }
        }
    }

    private static void mergeSettings(File file, int worldId, boolean pluginOn) {
        Properties p = new Properties();
        if (file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (Exception e) {
                log.debug("settings lezen {}: {}", file, e.toString());
            }
        }
        p.setProperty("runelite.defaultworldplugin", pluginOn ? "true" : "false");
        p.setProperty("defaultworld.defaultWorld", String.valueOf(Math.max(0, worldId)));
        p.setProperty("defaultworld.useLastWorld", "false");
        if (!pluginOn) {
            p.setProperty("defaultworld.lastWorld", "0");
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "LoneBot default world");
        } catch (Exception e) {
            log.warn("settings schrijven {}: {}", file, e.toString());
        }
    }
}
