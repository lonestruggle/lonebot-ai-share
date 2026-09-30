package net.storm.sdk.loop;

import net.storm.api.plugins.LoopedPlugin;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.LoneBotPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs {@link LoopedPlugin#loop()} on a background thread (Storm-style).
 */
public final class LoopHost {

    private static final Logger log = LoggerFactory.getLogger(LoopHost.class);

    private static final List<LoopedPlugin> PLUGINS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    /** Verhoog bij stall-restart zodat de oude runLoop stopt. */
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final long STALL_MS = 8_000L;
    /** Script-reload: loop mag geen plugin.tick doen / classloader niet sluiten mid-tick. */
    private static final AtomicBoolean RELOADING = new AtomicBoolean(false);
    private static final AtomicLong LAST_RESTART_REQUEST_MS = new AtomicLong();
    private static final AtomicBoolean LOOP_IDLE = new AtomicBoolean(true);
    private static final long RELOAD_WAIT_MS = 1_500L;
    private static ExecutorService executor;
    private static Future<?> future;
    private static volatile Thread loopThread;
    /** Per-script ClassLoader — Imp-reload mag WC-loader niet sluiten. */
    private static final Map<String, URLClassLoader> HOT_LOADERS = new ConcurrentHashMap<>();
    /** Kopieën onder scripts/run/ zodat ~/.lonebot/scripts/*.jar niet gelockt blijft. */
    private static final Map<String, File> HOT_JAR_SNAPSHOTS = new ConcurrentHashMap<>();

    private LoopHost() {
    }

    /**
     * Pauzeer de bot-loop tot hij tussen ticks zit, daarna pas unload/reload.
     * Voorkomt client-freeze: classloader dicht terwijl WC nog in {@code loop()} zit.
     */
    public static void beginScriptReload() {
        RELOADING.set(true);
        long deadline = System.currentTimeMillis() + RELOAD_WAIT_MS;
        while (!LOOP_IDLE.get() && System.currentTimeMillis() < deadline) {
            Thread lt = loopThread;
            if (lt != null && lt.isAlive() && System.currentTimeMillis() + 1_500L > deadline) {
                // Laatste kans: interrupt lange sleep/bank-wait in script
                try {
                    lt.interrupt();
                } catch (Throwable ignored) {
                }
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!LOOP_IDLE.get()) {
            log.warn("Script-reload: loop niet idle na {}ms — force unload (risico)", RELOAD_WAIT_MS);
            BotRuntime.logConsole("[Scripts] reload force — loop was nog bezig");
        } else {
            BotRuntime.logConsole("[Scripts] loop gepauzeerd voor reload");
        }
    }

    public static void endScriptReload() {
        RELOADING.set(false);
        // Clear spurious interrupt op reload-thread
        // Loop hervat zelf zodra RELOADING false is
    }

    public static boolean isReloading() {
        return RELOADING.get();
    }

    public static void register(LoopedPlugin plugin) {
        if (plugin == null) {
            return;
        }
        PLUGINS.add(plugin);
        plugin.startUp();
        log.info("Registered LoopedPlugin: {}", plugin.getClass().getName());
    }

    public static void unregister(LoopedPlugin plugin) {
        if (plugin == null) {
            return;
        }
        plugin.shutDown();
        PLUGINS.remove(plugin);
    }

    /** Verwijder plugins waarvan de simple class name matcht (voor hot-reload). */
    public static void unregisterBySimpleName(String simpleName) {
        if (simpleName == null) {
            return;
        }
        for (LoopedPlugin p : new ArrayList<>(PLUGINS)) {
            if (p.getClass().getSimpleName().equals(simpleName)) {
                unregister(p);
            }
        }
    }

    public static List<LoopedPlugin> plugins() {
        return List.copyOf(PLUGINS);
    }

    public static List<String> listedPlugins() {
        List<String> names = new ArrayList<>();
        for (LoopedPlugin p : PLUGINS) {
            names.add(p.getClass().getName());
        }
        return names;
    }

    /**
     * Laadt Cow/tests/RandomEvent opnieuw uit example-plugin jar (child-first).
     * Imp zit in {@link #hotReloadImpJar} — apart script.
     */
    public static String hotReloadExampleJar(File jar) {
        String[] classes = {
                "com.lonebot.example.ExampleLoopedPlugin",
                "com.lonebot.example.CowCombatPlugin",
                "com.lonebot.example.MonkKillerPlugin",
                "com.lonebot.example.VarrockEastBankTestPlugin",
                "com.lonebot.example.CityCircleTestPlugin",
                "com.lonebot.example.RandomEventPlugin"
        };
        return hotReloadClasses(jar, "example", classes);
    }

    /** Alleen Imp Killer script — geen client-herstart. */
    public static String hotReloadImpJar(File jar) {
        return hotReloadLoopedPlugin(jar, "imp", "com.lonebot.example.ImpKillerPlugin");
    }

    /** Woodcutter script — geen client-herstart. */
    public static String hotReloadWoodcutterJar(File jar) {
        return hotReloadLoopedPlugin(jar, "woodcutter",
                "com.lonebot.example.woodcutter.WoodcutterPlugin");
    }

    public static String hotReloadFishingJar(File jar) {
        return hotReloadLoopedPlugin(jar, "fishing",
                "com.lonebot.example.fishing.FishingPlugin");
    }

    public static String hotReloadImps2Jar(File jar) {
        return hotReloadLoopedPlugin(jar, "imps2", "com.lonebot.example.Imps2Plugin");
    }

    public static String hotReloadStarMinerJar(File jar) {
        return hotReloadLoopedPlugin(jar, "star",
                "com.lonebot.example.StarMinerPlugin");
    }

    public static String hotReloadGiantsJar(File jar) {
        return hotReloadLoopedPlugin(jar, "giants",
                "com.lonebot.example.GiantsPlugin");
    }

    public static String hotReloadClueJar(File jar) {
        return hotReloadLoopedPlugin(jar, "clue",
                "com.lonebot.example.CluePlugin");
    }

    public static String hotReloadQuesterJar(File jar) {
        return hotReloadLoopedPlugin(jar, "quest",
                "com.lonebot.example.QuesterPlugin");
    }

    private static String hotReloadClasses(File jar, String loaderKey, String[] classes) {
        if (jar == null || !jar.isFile()) {
            return "Jar niet gevonden: " + jar;
        }
        StringBuilder sb = new StringBuilder();
        int ok = 0;
        for (String cn : classes) {
            String r = hotReloadLoopedPlugin(jar, loaderKey, cn);
            if (r != null && r.startsWith("Hot-reload OK")) {
                ok++;
            }
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(cn.substring(cn.lastIndexOf('.') + 1)).append(':')
                    .append(r != null && r.startsWith("Hot-reload OK") ? "OK" : "fail");
        }
        String msg = "Hot-reload " + ok + "/" + classes.length + " — " + sb;
        log.info(msg);
        return msg;
    }

    /**
     * Laadt een LoopedPlugin-class opnieuw uit een jar (child-first), zonder client-restart.
     * API/SDK-wijzigingen vereisen wél een herstart.
     */
    public static String hotReloadLoopedPlugin(File jar, String className) {
        return hotReloadLoopedPlugin(jar, loaderKeyFor(className), className);
    }

    public static String hotReloadLoopedPlugin(File jar, String loaderKey, String className) {
        if (jar == null || !jar.isFile()) {
            return "Jar niet gevonden: " + jar;
        }
        if (className == null || className.isEmpty()) {
            return "Geen className";
        }
        String key = loaderKey != null && !loaderKey.isBlank() ? loaderKey.trim() : loaderKeyFor(className);
        // Eerst loop pauzeren ZONDER LoopHost-lock — anders deadlock met ensureRunning
        beginScriptReload();
        try {
            synchronized (LoopHost.class) {
                unregisterBySimpleName(className.contains(".")
                        ? className.substring(className.lastIndexOf('.') + 1)
                        : className);

                releaseLoaderOnly(key);

                File loadJar = snapshotJar(jar, key);
                URLClassLoader loader = new ChildFirstJarLoader(
                        new URL[]{loadJar.toURI().toURL()},
                        LoopHost.class.getClassLoader(),
                        "com.lonebot.example.");
                HOT_LOADERS.put(key, loader);

                Class<?> clazz = Class.forName(className, true, loader);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (!(instance instanceof LoopedPlugin)) {
                    return "Class is geen LoopedPlugin: " + className;
                }
                register((LoopedPlugin) instance);
                if (!STARTED.get()) {
                    STARTED.set(true);
                    if (executor == null || executor.isShutdown()) {
                        executor = Executors.newSingleThreadExecutor(r -> {
                            Thread t = new Thread(r, "lonebot-loop");
                            t.setDaemon(true);
                            return t;
                        });
                        future = executor.submit(LoopHost::runLoop);
                    }
                }
                String msg = "Hot-reload OK: " + className + " uit " + jar.getName() + " [" + key + "]";
                log.info(msg);
                return msg;
            }
        } catch (Throwable t) {
            log.warn("Hot-reload mislukt: {}", t.toString());
            return "Hot-reload mislukt: " + t;
        } finally {
            endScriptReload();
        }
    }

    /**
     * Sluit hot-reload classloaders zodat Windows {@code ~/.lonebot/scripts/*.jar} kan overschrijven.
     * Launcher stuurt dit vóór copy; daarna RELOAD_SCRIPTS.
     */
    public static void releaseAllScriptLoaders() {
        beginScriptReload();
        try {
            synchronized (LoopHost.class) {
                releaseLoaderUnlocked("fishing");
                releaseLoaderUnlocked("imp");
                releaseLoaderUnlocked("imps2");
                releaseLoaderUnlocked("star");
                releaseLoaderUnlocked("giants");
                releaseLoaderUnlocked("clue");
                releaseLoaderUnlocked("quest");
                releaseLoaderUnlocked("woodcutter");
                releaseLoaderUnlocked("example");
            }
            BotRuntime.logConsole("[Scripts] jars ontgrendeld (classloaders dicht)");
        } finally {
            endScriptReload();
        }
    }

    public static void releaseLoader(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        beginScriptReload();
        try {
            synchronized (LoopHost.class) {
                releaseLoaderUnlocked(key.trim());
            }
        } finally {
            endScriptReload();
        }
    }

    private static void releaseLoaderUnlocked(String k) {
        switch (k) {
            case "fishing":
                unregisterBySimpleName("FishingPlugin");
                break;
            case "imp":
                unregisterBySimpleName("ImpKillerPlugin");
                break;
            case "imps2":
                unregisterBySimpleName("Imps2Plugin");
                break;
            case "star":
                unregisterBySimpleName("StarMinerPlugin");
                break;
            case "giants":
                unregisterBySimpleName("GiantsPlugin");
                break;
            case "clue":
                unregisterBySimpleName("CluePlugin");
                break;
            case "quest":
                unregisterBySimpleName("QuesterPlugin");
                break;
            case "woodcutter":
                unregisterBySimpleName("WoodcutterPlugin");
                break;
            case "example":
                unregisterBySimpleName("ExampleLoopedPlugin");
                unregisterBySimpleName("CowCombatPlugin");
                unregisterBySimpleName("MonkKillerPlugin");
                unregisterBySimpleName("VarrockEastBankTestPlugin");
                unregisterBySimpleName("CityCircleTestPlugin");
                unregisterBySimpleName("RandomEventPlugin");
                break;
            default:
                break;
        }
        releaseLoaderOnly(k);
    }

    private static void releaseLoaderOnly(String key) {
        URLClassLoader prev = HOT_LOADERS.remove(key);
        if (prev != null) {
            try {
                prev.close();
            } catch (Exception ignored) {
            }
        }
        File snap = HOT_JAR_SNAPSHOTS.remove(key);
        if (snap != null) {
            try {
                Files.deleteIfExists(snap.toPath());
            } catch (Exception ignored) {
            }
        }
        pruneRunJars(key, null);
    }

    private static File snapshotJar(File source, String key) {
        try {
            LoneBotPaths.ensureDirs();
            File runDir = new File(LoneBotPaths.scriptsDir(), "run");
            if (!runDir.isDirectory() && !runDir.mkdirs()) {
                return source;
            }
            File dest = new File(runDir, key + "-" + System.currentTimeMillis() + ".jar");
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dest.deleteOnExit();
            HOT_JAR_SNAPSHOTS.put(key, dest);
            pruneRunJars(key, dest);
            return dest;
        } catch (Exception e) {
            log.warn("snapshotJar {}: {}", key, e.toString());
            return source;
        }
    }

    private static void pruneRunJars(String key, File keep) {
        File runDir = new File(LoneBotPaths.scriptsDir(), "run");
        File[] files = runDir.listFiles((d, n) -> n.startsWith(key + "-") && n.endsWith(".jar"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (keep != null && f.getAbsolutePath().equalsIgnoreCase(keep.getAbsolutePath())) {
                continue;
            }
            try {
                Files.deleteIfExists(f.toPath());
            } catch (Exception ignored) {
            }
        }
    }

    private static String loaderKeyFor(String className) {
        if (className == null) {
            return "default";
        }
        if (className.contains("Imps2")) {
            return "imps2";
        }
        if (className.contains("ImpKiller")) {
            return "imp";
        }
        if (className.contains("woodcutter") || className.contains("Woodcutter")) {
            return "woodcutter";
        }
        if (className.contains("fishing") || className.contains("Fishing")) {
            return "fishing";
        }
        if (className.contains("StarMiner") || className.contains("starminer")) {
            return "star";
        }
        if (className.contains("GiantsPlugin") || className.contains("giants.Giants")) {
            return "giants";
        }
        if (className.contains("CluePlugin") || className.contains(".clue.")
                || className.endsWith(".CluePlugin")) {
            return "clue";
        }
        if (className.contains("Quester") || className.contains("quest.Quester")) {
            return "quest";
        }
        if (className.contains("com.lonebot.example.")) {
            return "example";
        }
        return className;
    }

    /** Een script mag de hele host niet 3–8s bevriezen (WC chop→fm bleef idle). */
    private static final int MAX_SLEEP_MS = 1200;

    public static void start() {
        ensureRunning();
    }

    /**
     * Start of herstart de loop-thread als die dood is (interrupt / crash) terwijl
     * {@code STARTED} nog true was — dan bleven scripts IDLE zonder ticks.
     */
    public static synchronized void ensureRunning() {
        if (isStalled()) {
            log.warn("LoopHost stall — interrupt + herstart");
            restartLoopThread("stall");
            return;
        }
        boolean alive = STARTED.get() && future != null && !future.isDone()
                && executor != null && !executor.isShutdown();
        if (alive) {
            return;
        }
        boolean wasStarted = STARTED.get();
        STARTED.set(true);
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "lonebot-loop");
                t.setDaemon(true);
                return t;
            });
        }
        future = executor.submit(LoopHost::runLoop);
        if (wasStarted) {
            log.warn("LoopHost herstart (thread was dood — scripts kregen geen ticks)");
        } else {
            log.info("LoopHost started");
        }
    }

    /** Start/Stop: als de loop vastzit in getName()/sleep, forceer een nieuwe cycle. */
    public static synchronized void kickIfStalled() {
        if (isStalled()) {
            restartLoopThread("kick");
        } else {
            ensureRunning();
        }
    }

    /**
     * Script-failsafe: herstart de loop-thread (niet vanaf de loop-thread zelf).
     * Throttle 5s — geen spam bij elke hop-tick.
     */
    public static void requestLoopRestart(String reason) {
        long now = System.currentTimeMillis();
        long prev = LAST_RESTART_REQUEST_MS.get();
        if (now - prev < 5_000L) {
            return;
        }
        if (!LAST_RESTART_REQUEST_MS.compareAndSet(prev, now)) {
            return;
        }
        String why = reason != null && !reason.isBlank() ? reason : "request";
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(80L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (LoopHost.class) {
                if (STARTED.get()) {
                    restartLoopThread(why);
                }
            }
        }, "lonebot-loop-restart");
        t.setDaemon(true);
        t.start();
    }

    /** Star moet hoppen: pad uit, geen farWalk-skip van Star. */
    private static boolean haltWalkForPendingStarHop() {
        if (!BotRuntime.botEnabled || !BotRuntime.starMinerEnabled) {
            return false;
        }
        int want = BotRuntime.starHopWorld;
        if (want <= 0) {
            return false;
        }
        try {
            int cur = net.storm.sdk.game.Worlds.getCurrentWorld();
            if (cur <= 0 || cur == want) {
                return false;
            }
            net.storm.sdk.movement.MovementHelper.clearPath();
            net.storm.sdk.movement.WorldWalker.cancel();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isStalled() {
        if (future == null || future.isDone() || executor == null || executor.isShutdown()) {
            return false;
        }
        if (!LOOP_IDLE.get()) {
            return false;
        }
        try {
            if (net.storm.sdk.movement.MovementHelper.isWaitingForTravelPath()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (net.storm.sdk.movement.WalkClickHelper.millisSinceLastClick() < 5000L) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (net.storm.sdk.input.Mouse.isBusy()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (net.storm.sdk.movement.Movement.isMoving()
                    || net.storm.sdk.movement.Movement.getDestination() != null) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            net.storm.sdk.entities.Players.LocalSnap me = net.storm.sdk.entities.Players.snapshotLocal();
            if (me != null && me.present && me.animating) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        long last = net.storm.sdk.bot.BotRuntime.loopLastTickMs;
        if (last <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - last > STALL_MS;
    }

    private static void restartLoopThread(String reason) {
        GENERATION.incrementAndGet();
        STARTED.set(true);
        Thread old = loopThread;
        if (future != null) {
            future.cancel(true);
        }
        if (old != null && old != Thread.currentThread()) {
            try {
                old.interrupt();
            } catch (Throwable ignored) {
            }
        }
        if (executor != null) {
            try {
                executor.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "lonebot-loop");
            t.setDaemon(true);
            return t;
        });
        future = executor.submit(LoopHost::runLoop);
        net.storm.sdk.bot.BotRuntime.logConsole("[LoopHost] thread herstart (" + reason + ")");
        log.warn("LoopHost thread herstart ({})", reason);
    }

    /** @return true als de loop-thread leeft en recent getikt heeft */
    public static boolean isLoopAlive() {
        return STARTED.get() && future != null && !future.isDone() && !isStalled();
    }

    public static void stop() {
        STARTED.set(false);
        if (future != null) {
            future.cancel(true);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        executor = null;
        future = null;
        for (LoopedPlugin p : PLUGINS) {
            p.shutDown();
        }
        PLUGINS.clear();
        for (String key : new ArrayList<>(HOT_LOADERS.keySet())) {
            releaseLoaderOnly(key);
        }
        HOT_LOADERS.clear();
        HOT_JAR_SNAPSHOTS.clear();
        log.info("LoopHost stopped");
    }

    private static void runLoop() {
        loopThread = Thread.currentThread();
        long gen = GENERATION.get();
        log.info("LoopHost runLoop begin gen={}", gen);
        while (STARTED.get() && GENERATION.get() == gen) {
            if (Thread.interrupted() && GENERATION.get() != gen) {
                break;
            }
            // Script hot-reload: geen plugin.tick / geen classloader-gebruik
            while (RELOADING.get() && STARTED.get() && GENERATION.get() == gen) {
                LOOP_IDLE.set(true);
                sleep(40);
            }
            if (!STARTED.get() || GENERATION.get() != gen) {
                break;
            }
            boolean botOn = false;
            boolean mapWalk = false;
            try {
                botOn = BotRuntime.botEnabled;
                mapWalk = net.storm.sdk.movement.WorldWalker.isActive();
            } catch (Throwable ignored) {
            }
            boolean relogBusy = false;
            boolean breakWait = false;
            try {
                relogBusy = BotRuntime.relogFlowActive;
                breakWait = BotRuntime.breakPending;
            } catch (Throwable ignored) {
            }
            if (relogBusy) {
                LOOP_IDLE.set(true);
                BotRuntime.loopLastTickMs = System.currentTimeMillis();
                sleep(400);
                continue;
            }
            if (!botOn && !mapWalk) {
                LOOP_IDLE.set(true);
                BotRuntime.loopLastTickMs = System.currentTimeMillis();
                sleep(400);
                continue;
            }
            LOOP_IDLE.set(false);
            int delay = 600;
            long tickStart = System.currentTimeMillis();
            try {
                net.storm.sdk.entities.Players.LocalSnap walkSnap =
                        net.storm.sdk.entities.Players.snapshotLocal();
                if (net.storm.sdk.movement.WorldWalker.isActive()
                        || (walkSnap != null && walkSnap.moving)
                        || (walkSnap != null && walkSnap.walkDestination != null)
                        || net.storm.sdk.movement.MovementHelper.getActiveDestination() != null) {
                    delay = 220;
                }
            } catch (Throwable ignored) {
            }
            net.storm.sdk.bot.BotRuntime.loopLastTickMs = System.currentTimeMillis();
            try {
                net.storm.sdk.movement.MovementHelper.beginLoopTick();
                try {
                    if (botOn && net.storm.sdk.game.Static.getClient() != null) {
                        net.storm.sdk.bot.ActivityLog.noteWorld(net.storm.sdk.game.Static.getClient().getWorld());
                    }
                } catch (Throwable ignored) {
                }
                boolean hopHold = haltWalkForPendingStarHop();
                if (!hopHold) {
                    net.storm.sdk.movement.MovementHelper.tickActive();
                }
                boolean farWalk = false;
                try {
                    net.runelite.api.coords.WorldPoint dest =
                            net.storm.sdk.movement.MovementHelper.getActiveDestination();
                    if (dest == null) {
                        dest = net.storm.sdk.movement.WorldWalker.destination();
                    }
                    net.storm.sdk.entities.Players.LocalSnap me =
                            net.storm.sdk.entities.Players.snapshotLocal();
                    if (dest != null && me != null && me.present && me.worldLocation != null) {
                        int dxy = Math.max(
                                Math.abs(me.worldLocation.getX() - dest.getX()),
                                Math.abs(me.worldLocation.getY() - dest.getY()));
                        farWalk = me.worldLocation.getPlane() != dest.getPlane() || dxy > 12;
                    }
                    if (net.storm.sdk.movement.WorldWalker.isActive()
                            || net.storm.sdk.bot.BotRuntime.cityCircleTestEnabled
                            || net.storm.sdk.bot.BotRuntime.varrockEastBankTestEnabled
                            || net.storm.sdk.bot.BotRuntime.geTradeTestEnabled) {
                        farWalk = true;
                    }
                    if (hopHold) {
                        farWalk = false;
                    }
                } catch (Throwable ignored) {
                }
                if (hopHold) {
                    farWalk = false;
                }
                if (!farWalk) {
                    net.storm.sdk.items.BankSnapshot.captureIfOpenThrottled();
                    net.storm.sdk.interact.InteractManager.processUpTo(3);
                }
                try {
                    int re = farWalk
                            ? net.storm.sdk.game.RandomEventHandler.tickTalkingRandomOnly()
                            : net.storm.sdk.game.RandomEventHandler.tick();
                    if (re > 0) {
                        delay = Math.max(delay, re);
                    }
                } catch (Throwable ignored) {
                }
                boolean randomBusy = false;
                try {
                    randomBusy = net.storm.sdk.game.RandomEventHandler.isBusy();
                } catch (Throwable ignored) {
                }
                int walkerDelay = 0;
                if (!hopHold) {
                    walkerDelay = net.storm.sdk.movement.WorldWalker.tick();
                }
                if (walkerDelay > 0) {
                    delay = Math.max(delay, walkerDelay);
                }
                for (LoopedPlugin plugin : PLUGINS) {
                    if (GENERATION.get() != gen || RELOADING.get()) {
                        break;
                    }
                    if (!plugin.isRunning()) {
                        continue;
                    }
                    try {
                        String simple = plugin.getClass().getSimpleName();
                        boolean keepTest = "CityCircleTestPlugin".equals(simple)
                                || "VarrockEastBankTestPlugin".equals(simple)
                                || "GeTradeTestPlugin".equals(simple);
                        // Park-Star niet tijdens farWalk: tryResume + hopper-cache blokkeerde
                        // doorlinken (Fish/WC stap-voor-stap). Feed-thread hervat Star.
                        // Opt-in: FarWalk.keepSkillTicking — WC COS mid-approach e.d.
                        if (farWalk && !keepTest) {
                            if (!net.storm.sdk.movement.FarWalk.allowsPlugin(simple)) {
                                walkSkipLog();
                                continue;
                            }
                        }
                        // Bij bank-open ook park-Star overslaan: anders 8–15s tussen Deposit-All.
                        if ("StarMinerPlugin".equals(simple)
                                && net.storm.sdk.bot.BotRuntime.starParkActive
                                && !net.storm.sdk.bot.BotRuntime.starMinerEnabled) {
                            boolean bankOpen = false;
                            try {
                                bankOpen = net.storm.sdk.items.Bank.isOpen();
                            } catch (Throwable ignored) {
                            }
                            if (bankOpen) {
                                continue;
                            }
                        }
                        if (!net.storm.sdk.bot.BotRuntime.isSkillPluginAllowed(simple)) {
                            continue;
                        }
                        if (breakWait && net.storm.sdk.bot.BotRuntime.isNamedSkillPlugin(simple)) {
                            continue;
                        }
                        if (randomBusy && net.storm.sdk.bot.BotRuntime.isNamedSkillPlugin(simple)) {
                            randomBusySkipLog();
                            continue;
                        }
                        if ("RandomEventPlugin".equals(simple)) {
                            continue;
                        }
                        int d = plugin.loop();
                        if (d > 0) {
                            delay = Math.max(delay, d);
                        }
                    } catch (Throwable t) {
                        log.warn("LoopedPlugin error in {}: {}", plugin.getClass().getSimpleName(), t.toString());
                        delay = Math.max(delay, 1000);
                    }
                }
            } catch (Throwable t) {
                log.warn("LoopHost tick error: {}", t.toString());
                delay = 1000;
            } finally {
                // Heartbeat ná de tick: een 12–22s hopper/A*-tick mag niet als "dood" tellen.
                BotRuntime.heartbeat();
                LOOP_IDLE.set(true);
                long dt = System.currentTimeMillis() - tickStart;
                if (dt >= 400L && BotRuntime.botEnabled
                        && (net.storm.sdk.movement.MovementHelper.getActiveDestination() != null
                        || net.storm.sdk.movement.WorldWalker.isActive())) {
                    slowTickLog(dt);
                }
            }
            if (GENERATION.get() != gen) {
                break;
            }
            // Tijdens lopen: 220ms zodat TilePathWalker 4 tegels vóór de vlag doorklikt.
            // Script-delay (WC/Imp 600–1200) daaroverheen = 1 klik + stilstand bij de vlag.
            // Stilstand/chop: script-delay blijft (geen 280-cap op skills).
            boolean walkPulse = false;
            try {
                walkPulse = net.storm.sdk.movement.WorldWalker.isActive()
                        || net.storm.sdk.movement.MovementHelper.getActiveDestination() != null;
                if (!walkPulse) {
                    net.storm.sdk.entities.Players.LocalSnap s =
                            net.storm.sdk.entities.Players.snapshotLocal();
                    walkPulse = s != null && (s.moving || s.walkDestination != null);
                }
            } catch (Throwable ignored) {
            }
            if (walkPulse) {
                delay = 220;
            }
            sleep(delay);
        }
        LOOP_IDLE.set(true);
        if (loopThread == Thread.currentThread()) {
            loopThread = null;
        }
        log.info("LoopHost runLoop einde gen={}", gen);
    }

    private static long lastWalkSkipLogMs;
    private static long lastRandomBusyLogMs;
    private static long lastSlowTickLogMs;

    private static void slowTickLog(long dtMs) {
        long now = System.currentTimeMillis();
        if (now - lastSlowTickLogMs < 1500L) {
            return;
        }
        lastSlowTickLogMs = now;
        BotRuntime.logConsole("[Loop] tick " + dtMs + "ms (te traag voor doorlinken)");
    }

    private static void walkSkipLog() {
        long now = System.currentTimeMillis();
        if (now - lastWalkSkipLogMs < 4000L) {
            return;
        }
        lastWalkSkipLogMs = now;
        net.runelite.api.coords.WorldPoint d = net.storm.sdk.movement.WorldWalker.destination();
        if (d == null) {
            d = net.storm.sdk.movement.MovementHelper.getActiveDestination();
        }
        BotRuntime.logConsole("[Loop] scripts gepauzeerd — travel dest="
                + (d != null ? d.getX() + "," + d.getY() + "," + d.getPlane() : "?"));
    }

    private static void randomBusySkipLog() {
        long now = System.currentTimeMillis();
        if (now - lastRandomBusyLogMs < 4000L) {
            return;
        }
        lastRandomBusyLogMs = now;
        BotRuntime.logConsole("[Loop] skills gepauzeerd — lamp/random");
    }

    private static void sleep(long ms) {
        long wait = Math.max(50L, Math.min(ms, MAX_SLEEP_MS));
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            if (!STARTED.get()) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Laadt com.lonebot.example.* uit de jar eerst (niet uit parent classpath). */
    private static final class ChildFirstJarLoader extends URLClassLoader {
        private final String prefix;

        ChildFirstJarLoader(URL[] urls, ClassLoader parent, String prefix) {
            super(urls, parent);
            this.prefix = prefix;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null && name.startsWith(prefix)) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                if (c == null) {
                    c = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }
}
