package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.World;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zet de login-wereld vóór inloggen — zelfde patroon als RuneLite {@code DefaultWorldPlugin}
 * ({@code createWorld} + {@code changeWorld}), geen hop-UI.
 * <p>
 * Props: {@code -Dlonebot.world=301} (voorkeur deze sessie) en/of {@code -Dlonebot.worlds=301,308}
 * + {@code -Dlonebot.worldHop=true} (legacy naam = “apply default world”).
 */
public final class StartupWorldHop {

    private static final Logger log = LoggerFactory.getLogger(StartupWorldHop.class);

    private static volatile boolean attempted;
    private static volatile boolean applied;
    private static volatile long nextTryMs;
    private static volatile int tries;

    private StartupWorldHop() {
    }

    public static void resetSession() {
        attempted = false;
        applied = false;
        nextTryMs = 0L;
        tries = 0;
    }

    public static boolean isApplied() {
        return applied;
    }

    /**
     * Roep aan vanuit startUp / login-screen / game-tick.
     */
    public static void tick(Client client, ClientThread clientThread, WorldService worldService) {
        if (attempted || applied || client == null) {
            return;
        }
        if (!"true".equalsIgnoreCase(System.getProperty("lonebot.worldHop", "false"))) {
            attempted = true;
            return;
        }
        List<Integer> pool = resolvePool();
        if (pool.isEmpty()) {
            attempted = true;
            return;
        }
        GameState gs = client.getGameState();
        if (gs != GameState.LOGIN_SCREEN && gs != GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            if (gs == GameState.LOGGED_IN || gs == GameState.HOPPING || gs == GameState.LOADING) {
                attempted = true;
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextTryMs) {
            return;
        }
        if (tries >= 12) {
            attempted = true;
            log.warn("[DefaultWorld] opgegeven na {} pogingen", tries);
            return;
        }
        tries++;
        nextTryMs = now + 900L + ThreadLocalRandom.current().nextInt(200, 500);

        int pick = pickWorld(pool, safeWorld(client));
        if (pick <= 0) {
            attempted = true;
            return;
        }
        if (safeWorld(client) == pick) {
            log.info("[DefaultWorld] client al op w{} — klaar", pick);
            applied = true;
            attempted = true;
            return;
        }

        final int worldId = pick;
        Runnable apply = () -> applyDefaultWorld(client, worldService, worldId, pool);
        if (clientThread != null) {
            clientThread.invokeLater(apply);
        } else {
            apply.run();
        }
    }

    /** Compat: zonder WorldService. */
    public static void tick(Client client, ClientThread clientThread) {
        tick(client, clientThread, null);
    }

    private static void applyDefaultWorld(Client client, WorldService worldService, int worldId, List<Integer> pool) {
        try {
            GameState gs = client.getGameState();
            if (gs != GameState.LOGIN_SCREEN && gs != GameState.LOGIN_SCREEN_AUTHENTICATOR) {
                return;
            }
            int corrected = worldId < 300 ? worldId + 300 : worldId;
            if (client.getWorld() == corrected) {
                applied = true;
                attempted = true;
                return;
            }

            if (worldService != null) {
                WorldResult worldResult = worldService.getWorlds();
                if (worldResult == null) {
                    log.debug("[DefaultWorld] world list nog leeg — refresh");
                    try {
                        worldService.refresh();
                    } catch (Throwable ignored) {
                    }
                    return;
                }
                net.runelite.http.api.worlds.World httpWorld = worldResult.findWorld(corrected);
                if (httpWorld == null) {
                    log.warn("[DefaultWorld] w{} niet in WorldService (poging {})", corrected, tries);
                    return;
                }
                EnumSet<WorldType> types = httpWorld.getTypes();
                if (types != null && (types.contains(WorldType.BETA_WORLD) || types.contains(WorldType.NOSAVE_MODE))) {
                    log.warn("[DefaultWorld] skip beta/nosave w{}", corrected);
                    return;
                }
                World rsWorld = client.createWorld();
                rsWorld.setActivity(httpWorld.getActivity());
                rsWorld.setAddress(httpWorld.getAddress());
                rsWorld.setId(httpWorld.getId());
                rsWorld.setPlayerCount(httpWorld.getPlayers());
                rsWorld.setLocation(httpWorld.getLocation());
                rsWorld.setTypes(WorldUtil.toWorldTypes(types));
                client.changeWorld(rsWorld);
                log.info("[DefaultWorld] changeWorld → w{} (pool={}) — RuneLite Default World-stijl",
                        corrected, pool);
                applied = true;
                attempted = true;
                return;
            }

            // Fallback zonder WorldService: bestaande client-lijst + changeWorld
            World dest = findWorld(client, corrected);
            if (dest == null) {
                log.warn("[DefaultWorld] w{} niet in client world list (poging {})", corrected, tries);
                return;
            }
            client.changeWorld(dest);
            log.info("[DefaultWorld] changeWorld (fallback list) → w{}", corrected);
            applied = true;
            attempted = true;
        } catch (Throwable t) {
            log.warn("[DefaultWorld] fout: {}", t.toString());
        }
    }

    static List<Integer> resolvePool() {
        List<Integer> single = parseWorldsProp(System.getProperty("lonebot.world", ""));
        if (!single.isEmpty()) {
            return single;
        }
        return parseWorldsProp(System.getProperty("lonebot.worlds", ""));
    }

    private static int pickWorld(List<Integer> pool, int current) {
        if (pool == null || pool.isEmpty()) {
            return -1;
        }
        if (pool.size() == 1) {
            return pool.get(0) < 300 ? pool.get(0) + 300 : pool.get(0);
        }
        String key = System.getProperty("lonebot.characterId",
                System.getProperty("lonebot.account", ""));
        int pick = InstanceDefaultWorldConfig.pickWorld(pool, key, null);
        if (pick == current) {
            for (int w : pool) {
                int c = w < 300 ? w + 300 : w;
                if (c != current) {
                    return c;
                }
            }
        }
        return pick;
    }

    private static int safeWorld(Client client) {
        try {
            return client.getWorld();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static World findWorld(Client client, int worldId) {
        if (worldId <= 0) {
            return null;
        }
        try {
            World[] list = client.getWorldList();
            if (list != null) {
                for (World w : list) {
                    if (w != null && w.getId() == worldId) {
                        return w;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static List<Integer> parseWorldsProp(String raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String part : raw.split("[,;\\s]+")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.toLowerCase(Locale.ROOT).startsWith("w")) {
                p = p.substring(1);
            }
            try {
                int id = Integer.parseInt(p);
                if (id > 0 && !out.contains(id)) {
                    out.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
