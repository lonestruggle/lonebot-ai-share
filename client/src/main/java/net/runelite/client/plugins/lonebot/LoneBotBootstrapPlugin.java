package net.runelite.client.plugins.lonebot;

import com.google.inject.Provides;
import com.lonebot.example.CityCircleTestPlugin;
import com.lonebot.example.CowCombatPlugin;
import com.lonebot.example.GeTradeTestPlugin;
import com.lonebot.example.MonkKillerPlugin;
import com.lonebot.example.ExampleLoopedPlugin;
import com.lonebot.example.RandomEventPlugin;
import com.lonebot.example.ImpKillerPlugin;
import com.lonebot.example.Imps2Plugin;
import com.lonebot.example.StarMinerPlugin;
import com.lonebot.example.GiantsPlugin;
import com.lonebot.example.giants.GiantsTypes;
import com.lonebot.example.QuesterPlugin;
import com.lonebot.example.VarrockEastBankTestPlugin;
import com.lonebot.example.woodcutter.WcCenters;
import com.lonebot.example.woodcutter.WoodcutterPlugin;
import com.lonebot.example.fishing.FishingPlugin;
import com.lonebot.example.imps.ImpsTypes;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.storm.sdk.tiles.ExcludedTiles;
import net.storm.sdk.tiles.NoWalkZones;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.lonebot.login.RelogLoop;
import net.runelite.client.plugins.lonebot.login.RelogRuntime;
import net.runelite.client.plugins.lonebot.login.SameAccountRelogger;
import net.runelite.client.plugins.lonebot.dev.HoverCaptureHelper;
import net.runelite.client.plugins.lonebot.dev.InventoryItemIdOverlay;
import net.runelite.client.plugins.lonebot.dev.ObjectIdFilterStore;
import net.runelite.client.plugins.lonebot.dev.SceneIdOverlay;
import net.runelite.client.plugins.lonebot.dev.VarWatchHelper;
import net.runelite.client.plugins.lonebot.dev.WalkObstacleScan;
import net.runelite.client.plugins.lonebot.dev.WidgetHoverOverlay;
import net.runelite.client.plugins.lonebot.dev.DevToolsOverlay;
import net.runelite.client.game.WorldService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.storm.sdk.api.ApiCatalog;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.LoneBotPaths;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.input.MouseBackend;
import net.storm.sdk.input.MouseSettings;
import net.storm.sdk.interact.mouse.BezierCurveMouseMovement;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.loop.LoopHost;
import net.storm.sdk.movement.WalkCameraSettings;
import net.storm.sdk.movement.WalkClickSettings;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.utils.AntiBanSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

@PluginDescriptor(
        name = "LoneBot Bootstrap",
        description = "Wires Storm-compat SDK + LoopedPlugin host + canvas mouse + account login",
        tags = {"lonebot", "bot", "storm", "login", "combat"},
        enabledByDefault = true
)
public class LoneBotBootstrapPlugin extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(LoneBotBootstrapPlugin.class);

    public static final String VERSION = "0.3.657";

    /** Hardcoded Imp NPC (geen UI-slider). */
    public static final int IMP_NPC_ID = 5007;
    private static final long ACCOUNT_STAT_SNAPSHOT_INTERVAL_MS = 45_000L;
    private static final long HISCORE_BAN_CHECK_INTERVAL_MS = 30 * 60_000L;
    private static final long REMOTE_STATUS_INTERVAL_MS = 2_000L;

    private long lastAccountStatSnapshotMs;
    private long lastHiscoreBanCheckMs;
    private long lastRemoteStatusMs;
    private long lastAccountRenameMs;
    private long lastScriptReloadConsumeMs;
    private boolean launchedCredsApplied;
    private JagexCredentialsHelper.ParsedJagexAccount launchedParsed;
    private PaintOverlayMouseHandler paintOverlayMouseHandler;
    private final WorldWalkerHotkeys worldWalkerHotkeys = new WorldWalkerHotkeys();

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private LoneBotStatusOverlay statusOverlay;

    @Inject
    private LoneBotControlOverlay controlOverlay;

    @Inject
    private LoneBotMouseDebugOverlay mouseDebugOverlay;

    @Inject
    private LoneBotDebugOverlay debugOverlay;

    @Inject
    private ImpDebugOverlay impDebugOverlay;

    @Inject
    private WcDebugOverlay wcDebugOverlay;

    @Inject
    private FishDebugOverlay fishDebugOverlay;

    @Inject
    private StarDebugOverlay starDebugOverlay;

    @Inject
    private GiantsDebugOverlay giantsDebugOverlay;

    @Inject
    private CowDebugOverlay cowDebugOverlay;

    @Inject
    private ImpHuntAreaOverlay impHuntAreaOverlay;

    @Inject
    private WcHuntAreaOverlay wcHuntAreaOverlay;

    @Inject
    private SkillAreaOverlay skillAreaOverlay;

    @Inject
    private TileMarkerOverlay tileMarkerOverlay;

    @Inject
    private WallDoorCaptureOverlay wallDoorCaptureOverlay;

    @Inject
    private WidgetHoverOverlay widgetHoverOverlay;

    @Inject
    private InventoryItemIdOverlay inventoryItemIdOverlay;

    @Inject
    private SceneIdOverlay sceneIdOverlay;

    @Inject
    private DevToolsOverlay devToolsOverlay;

    @Inject
    private HoverCaptureHelper hoverCaptureHelper;

    @Inject
    private VarWatchHelper varWatchHelper;

    @Inject
    private TileMarkerMenuListener tileMarkerMenuListener;

    @Inject
    private ImpChopMenuFilter impChopMenuFilter;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private EventBus eventBus;

    @Inject
    private ConfigManager configManager;

    @Inject
    private WorldService worldService;

    @Inject
    private LoneBotConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private LoneBotPanel panel;

    private NavigationButton navButton;
    private final ExampleLoopedPlugin example = new ExampleLoopedPlugin();
    private final CowCombatPlugin cowCombat = new CowCombatPlugin();
    private final MonkKillerPlugin monkKiller = new MonkKillerPlugin();
    private final VarrockEastBankTestPlugin varrockEastBankTest = new VarrockEastBankTestPlugin();
    private final CityCircleTestPlugin cityCircleTest = new CityCircleTestPlugin();
    private final GeTradeTestPlugin geTradeTest = new GeTradeTestPlugin();
    private final ImpKillerPlugin impKiller = new ImpKillerPlugin();
    private final Imps2Plugin imps2 = new Imps2Plugin();
    private final WoodcutterPlugin woodcutter = new WoodcutterPlugin();
    private final FishingPlugin fishing = new FishingPlugin();
    private final StarMinerPlugin starMiner = new StarMinerPlugin();
    private final GiantsPlugin giants = new GiantsPlugin();
    private final QuesterPlugin quester = new QuesterPlugin();
    private final RandomEventPlugin randomEvents = new RandomEventPlugin();

    private boolean potatoNoWalkTriedThisSession;
    private final AtomicBoolean applyingConfig = new AtomicBoolean();

    /** Voor UI → live Imp apply na account-bewerken. */
    private static volatile LoneBotBootstrapPlugin INSTANCE;

    @Override
    protected void startUp() {
        INSTANCE = this;
        Static.setClient(client);
        Static.setClientThread(r -> clientThread.invoke(r));
        if (config != null) {
            ExcludedTiles.loadSharedOrMigrate(config.excludedTiles());
        }
        OverlayMinimizeStore.bind(configManager);
        ObjectIdFilterStore.init(configManager,
                config != null ? config.devObjectIdFilter() : ObjectIdFilterStore.DEFAULT_CSV);
        forceBotOffOnClientStart();
        if (config != null) {
            try {
                net.storm.sdk.bot.ActivityLog.discordWebhook =
                        config.activityDiscordWebhook() != null ? config.activityDiscordWebhook().trim() : "";
            } catch (Throwable ignored) {
            }
        }
        Mouse.installNativeCanvasGuard();
        overlayManager.add(statusOverlay);
        if (controlOverlay != null) {
            controlOverlay.register(overlayManager);
        }
        paintOverlayMouseHandler = new PaintOverlayMouseHandler(client);
        paintOverlayMouseHandler.install();
        overlayManager.add(mouseDebugOverlay);
        overlayManager.add(debugOverlay);
        overlayManager.add(impDebugOverlay);
        overlayManager.add(wcDebugOverlay);
        overlayManager.add(fishDebugOverlay);
        overlayManager.add(starDebugOverlay);
        overlayManager.add(giantsDebugOverlay);
        overlayManager.add(cowDebugOverlay);
        overlayManager.add(impHuntAreaOverlay);
        overlayManager.add(wcHuntAreaOverlay);
        overlayManager.add(skillAreaOverlay);
        overlayManager.add(tileMarkerOverlay);
        overlayManager.add(wallDoorCaptureOverlay);
        overlayManager.add(widgetHoverOverlay);
        overlayManager.add(inventoryItemIdOverlay);
        overlayManager.add(sceneIdOverlay);
        overlayManager.add(devToolsOverlay);
        hoverCaptureHelper.install();
        worldWalkerHotkeys.install();
        eventBus.register(tileMarkerMenuListener);
        eventBus.register(varWatchHelper);
        eventBus.register(impChopMenuFilter);

        ExampleLoopedPlugin.mouseFidgetEnabled = config == null || config.mouseFidgetEnabled();
        BotRuntime.dialogAutoContinue = config == null || config.dialogAutoContinue();
        // Config kan stale beide true hebben — altijd via setActiveSkill / sync
        boolean cfgWc = config != null && config.woodcuttingEnabled();
        boolean cfgFish = config != null && config.fishingEnabled();
        boolean cfgStar = config != null && config.starMinerEnabled();
        boolean cfgGiants = config != null && config.giantsKillerEnabled();
        boolean cfgImp = config != null && config.impKillerEnabled();
        boolean cfgCow = config != null && config.cowCombatEnabled();
        boolean cfgMonk = config != null && config.monkKillerEnabled();
        boolean cfgQuest = config != null && config.questEnabled();
        boolean cfgClue = config != null && config.clueEnabled();
        if (cfgImp) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.IMP);
        } else if (cfgWc) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.WOODCUTTING);
        } else if (cfgFish) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.FISHING);
        } else if (cfgStar) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.STAR);
        } else if (cfgGiants) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.GIANTS);
        } else if (cfgMonk) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.MONK);
        } else if (cfgClue) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.CLUE);
        } else if (cfgQuest) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.QUEST);
        } else if (cfgCow) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.COW);
        } else {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
        }
        LoneBotBotControl.syncSkillFlagsToConfig(configManager);
        // Per-account skills + WC-settings
        String launchedAcc = System.getProperty("lonebot.account");
        if (launchedAcc != null && !launchedAcc.trim().isEmpty()) {
            ManagedAccountsStore.ensureLoaded();
            ManagedAccountsStore.ManagedAccount row =
                    ManagedAccountsStore.findByDisplayName(launchedAcc.trim());
            if (row != null) {
                LoneBotBotControl.applyAccountSkills(row, configManager);
            }
            if (AccountWcSettingsStore.applyToConfigManager(launchedAcc.trim(), configManager)
                    && config != null) {
                // Config proxy kan stale zijn — herlees via manager values al gezet
                log.info("WC per-account settings geladen voor {}", launchedAcc.trim());
            }
        }
        BotRuntime.impDebugOverlayEnabled = config == null || config.impDebugOverlay();
        BotRuntime.wcDebugOverlayEnabled = config == null || config.wcDebugOverlay();
        BotRuntime.wcDebugOverlayMinimized = OverlayMinimizeStore.isMinimized("wc");
        BotRuntime.fishDebugOverlayEnabled = config == null || config.fishDebugOverlay();
        BotRuntime.fishDebugOverlayMinimized = OverlayMinimizeStore.isMinimized("fish");
        BotRuntime.starDebugOverlayEnabled = config == null || config.starDebugOverlay();
        BotRuntime.giantsDebugOverlayEnabled = config == null || config.giantsDebugOverlay();
        BotRuntime.cowDebugOverlayEnabled = config == null || config.cowDebugOverlay();
        if (config != null) {
            com.lonebot.example.CowCombatPlugin.lootEnabled = config.cowLootEnabled();
            BotRuntime.canvasDebugEnabled = config.canvasDebugOverlay();
            BotRuntime.mouseDebugEnabled = config.mouseDebugOverlay();
            applyImpSettings(config);
            applyWcSettings(config);
            applyFishSettings(config);
            applyStarSettings(config);
            applyGiantsSettings(config);
            applyMonkSettings(config);
            applyQuestSettings(config);
        }
        BotRuntime.loadedBotClass = "builtin CowCombatPlugin";
        if (config != null) {
            unifyMouseToggles();
            applyMovementFromConfig();
            syncRuneliteDeveloperModeFromFile();
        }
        if (com.lonebot.launcher.RuneliteDeveloperMode.isCurrentProcessEnabled()) {
            BotRuntime.logConsole("[DevTools] RuneLite developer mode AAN — sidebar: Developer Tools (bug-icoon).");
        } else if (com.lonebot.launcher.RuneliteDeveloperMode.isEnabled()) {
            BotRuntime.logConsole("[DevTools] Developer mode aangevinkt — herstart deze client voor de RuneLite DevTools-sidebar.");
        }
        MouseManager.setMovementStrategy(new BezierCurveMouseMovement());
        // Force focus-safe mouse (no Robot / requestFocus)
        if (Mouse.getBackend() == null || Mouse.getBackend().stealsFocus()) {
            Mouse.setBackend(MouseBackend.CANVAS_EDT);
        }
        AntiBan.get().startFidgetWorker();
        log.info(ApiCatalog.summaryLine());
        log.info("Mouse speed={}% random={} | Walk mini={} canvas={} ui={} cam={} invoke={} far={} step={}-{} reclick={} | canvasDebug={} | AntiBan on={} freq={}s",
                MouseSettings.getSpeedPercent(), MouseSettings.isRandomMoveEnabled(),
                WalkClickSettings.useMinimap, WalkClickSettings.useCanvas,
                WalkClickSettings.useUiZones, WalkClickSettings.useCameraNudge,
                WalkClickSettings.useInvokeWalk, WalkClickSettings.useFarCanvasWalk,
                WalkClickSettings.effectiveStepMin(), WalkClickSettings.effectiveStepMax(),
                WalkClickSettings.minReclickMs, BotRuntime.canvasDebugEnabled,
                AntiBanSettings.enabled, AntiBanSettings.frequencySec);

        LoopHost.register(randomEvents);
        LoopHost.register(example);
        LoopHost.register(cowCombat);
        LoopHost.register(monkKiller);
        LoopHost.register(varrockEastBankTest);
        LoopHost.register(cityCircleTest);
        LoopHost.register(geTradeTest);
        ScriptReloadHelper.prefetchStagedJarsFromBuild(s -> {
        });
        if (!stagedJarPresent(LoneBotPaths.impKillerJar())) {
            LoopHost.register(impKiller);
        }
        if (!stagedJarPresent(LoneBotPaths.imps2Jar())) {
            LoopHost.register(imps2);
        }
        if (!stagedJarPresent(LoneBotPaths.woodcutterJar())) {
            LoopHost.register(woodcutter);
        }
        if (!stagedJarPresent(LoneBotPaths.fishingJar())) {
            LoopHost.register(fishing);
        }
        if (!stagedJarPresent(LoneBotPaths.starMinerJar())) {
            LoopHost.register(starMiner);
        }
        if (!stagedJarPresent(LoneBotPaths.giantsJar())) {
            LoopHost.register(giants);
        }
        if (!stagedJarPresent(LoneBotPaths.questJar())) {
            LoopHost.register(quester);
        }
        LoopHost.start();
        SameAccountRelogger relogger = new SameAccountRelogger(config);
        RelogRuntime.config = config;
        RelogRuntime.configManager = configManager;
        RelogRuntime.relogger = relogger;
        RelogLoop relogLoop = new RelogLoop(relogger, config, configManager);
        RelogRuntime.loop = relogLoop;
        relogLoop.start();
        try {
            SideloadPlugins.load(pluginManager);
        } catch (Throwable t) {
            log.warn("[Sideload] {}", t.toString());
        }
        scheduleStagedScriptLoad();
        applyStartupDefaultWorldConfig();
        StartupWorldHop.tick(client, clientThread, worldService);
        statusOverlay.setMessage(BotRuntime.cowCombatEnabled
                ? "Cow combat AAN (Bezier move)"
                : ApiCatalog.summaryLine());

        if (config != null && config.updateCheckOnStartup()) {
            final String repo = config.updateGithubRepo();
            final String token = config.updateGithubToken();
            Thread t = new Thread(() -> {
                LoneBotUpdater.CheckResult r = LoneBotUpdater.check(repo, token);
                log.info("[Updater] {}", r.message);
                if (r.updateAvailable) {
                    statusOverlay.setMessage("⬆ " + r.message);
                }
            }, "lonebot-update-check");
            t.setDaemon(true);
            t.start();
        }

        BufferedImage icon = loadPluginIcon();
        navButton = NavigationButton.builder()
                .tooltip("LoneBot Control · v" + VERSION)
                .icon(icon)
                .priority(1)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);

        // Multi-client: forceer account-creds in geheugen (file kan al geladen zijn als shared)
        clientThread.invokeLater(this::maybeApplyLaunchedAccountCredentials);
        String launchedName = System.getProperty("lonebot.account");
        if (launchedName != null && !launchedName.trim().isEmpty()) {
            ClientRemoteBus.publishReady(launchedName.trim());
            clientThread.invokeLater(this::maybeApplyLaunchedAccountCredentials);
        }
    }

    @Override
    protected void shutDown() {
        if (INSTANCE == this) {
            INSTANCE = null;
        }
        AntiBan.get().stopFidgetWorker();
        if (RelogRuntime.loop != null) {
            RelogRuntime.loop.stop();
            RelogRuntime.loop = null;
            RelogRuntime.relogger = null;
        }
        LoopHost.stop();
        try {
            configManager.setConfiguration("lonebot", "excludedTiles", ExcludedTiles.serialize());
            ExcludedTiles.saveShared();
        } catch (Throwable ignored) {
        }
        eventBus.unregister(tileMarkerMenuListener);
        if (varWatchHelper != null) {
            eventBus.unregister(varWatchHelper);
        }
        eventBus.unregister(impChopMenuFilter);
        if (paintOverlayMouseHandler != null) {
            paintOverlayMouseHandler.uninstall();
            paintOverlayMouseHandler = null;
        }
        worldWalkerHotkeys.uninstall();
        hoverCaptureHelper.uninstall();
        overlayManager.remove(statusOverlay);
        if (controlOverlay != null) {
            controlOverlay.unregister(overlayManager);
        }
        overlayManager.remove(mouseDebugOverlay);
        overlayManager.remove(debugOverlay);
        overlayManager.remove(impDebugOverlay);
        overlayManager.remove(wcDebugOverlay);
        overlayManager.remove(fishDebugOverlay);
        overlayManager.remove(starDebugOverlay);
        overlayManager.remove(giantsDebugOverlay);
        overlayManager.remove(cowDebugOverlay);
        overlayManager.remove(impHuntAreaOverlay);
        overlayManager.remove(wcHuntAreaOverlay);
        overlayManager.remove(skillAreaOverlay);
        overlayManager.remove(tileMarkerOverlay);
        overlayManager.remove(wallDoorCaptureOverlay);
        overlayManager.remove(widgetHoverOverlay);
        overlayManager.remove(inventoryItemIdOverlay);
        overlayManager.remove(sceneIdOverlay);
        overlayManager.remove(devToolsOverlay);
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        Static.setClientThread(null);
        Static.setClient(null);
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        try {
            net.storm.sdk.entities.Players.refreshFromClient();
        } catch (Throwable ignored) {
        }
        maybeUpdateAccountStatSnapshot();
        maybeRenameAccountFromLivePlayer();
        maybeRunPeriodicHiscoreBanCheck();
        maybePollRemoteCommands();
        maybePublishRemoteStatus();
        maybeForcePastWelcome();
        StartupWorldHop.tick(client, clientThread, worldService);
        maybeWriteClientPreview();
        maybePublishClientWindowHwnd();
        if (hoverCaptureHelper != null) {
            hoverCaptureHelper.ensureCanvasListener();
        }
        if (config != null) {
            try {
                WalkObstacleScan.tick(client, config.walkObstacleScanEnabled());
            } catch (Throwable t) {
                log.debug("[Walk/scan] {}", t.toString());
            }
        }
        if (paintOverlayMouseHandler != null) {
            paintOverlayMouseHandler.ensureCanvasListener();
        }
        PanelLogoutHelper.tick(clientThread);
        LoopWatchHelper.tick(config, configManager);
        if (BotRuntime.botEnabled) {
            LoopHost.ensureRunning();
            BotRuntime.enforceExclusiveSkills();
            maybeConsumeScriptReload();
        }
        maybeRefreshPotatoNoWalk();
        net.storm.sdk.movement.Movement.tickAutoRun();
        if (net.storm.sdk.movement.WorldWalker.isActive() && !BotRuntime.botEnabled) {
            net.storm.sdk.movement.WorldWalker.tick();
        }
        // Style/account overrides opnieuw pushen (hot-reload + login RSN)
        if (BotRuntime.impKillerEnabled && config != null) {
            applyImpSettings(config);
        }
        if (BotRuntime.woodcuttingEnabled && config != null) {
            applyWcSettings(config);
        }
        if (BotRuntime.fishingEnabled && config != null) {
            applyFishSettings(config);
        }
        if ((BotRuntime.starMinerEnabled || BotRuntime.starParkActive) && config != null) {
            applyStarSettings(config);
        }
        if (BotRuntime.giantsKillerEnabled && config != null) {
            applyGiantsSettings(config);
        }
        if (BotRuntime.questEnabled && config != null) {
            applyQuestSettings(config);
        }
    }

    /** Star/andere scripts: hop-failsafe vraagt ↻ zonder de loop-thread te unloaden. */
    private void maybeConsumeScriptReload() {
        String key = BotRuntime.takeScriptReloadRequest();
        if (key == null || key.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScriptReloadConsumeMs < 15_000L) {
            BotRuntime.logConsole("[Scripts] reload skip — te snel (" + key + ")");
            return;
        }
        lastScriptReloadConsumeMs = now;
        Thread t = new Thread(() -> {
            try {
                if ("star".equalsIgnoreCase(key)) {
                    BotRuntime.logConsole("[Scripts] Star herstart (hop-failsafe)");
                    ScriptReloadHelper.reloadStarMinerInThisClient();
                } else {
                    BotRuntime.logConsole("[Scripts] reload onbekend: " + key);
                    return;
                }
                LoopHost.requestLoopRestart("script-reload-" + key);
            } catch (Throwable ex) {
                log.warn("[Scripts] failsafe reload: {}", ex.toString());
            }
        }, "lonebot-script-failsafe-reload");
        t.setDaemon(true);
        t.start();
    }

    /** Laad staged script-jars na register (achtergrond — geen freeze op EDT). */
    private void scheduleStagedScriptLoad() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(400L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                ScriptReloadHelper.reloadStagedInThisClient();
            } catch (Throwable ex) {
                log.warn("[Scripts] startup staged mislukt: {}", ex.toString());
            }
            ensureBuiltinScriptsIfMissing();
        }, "lonebot-startup-staged-scripts");
        t.setDaemon(true);
        t.start();
    }

    private static boolean stagedJarPresent(java.io.File dest) {
        java.io.File f = ScriptReloadHelper.newestStaged(dest);
        return f != null && f.isFile();
    }

    private void ensureBuiltinScriptsIfMissing() {
        boolean fish = false;
        boolean wc = false;
        boolean imp = false;
        boolean imps2Ok = false;
        boolean star = false;
        boolean giantsOk = false;
        boolean quest = false;
        for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
            if (p == null) {
                continue;
            }
            String n = p.getClass().getSimpleName();
            if ("FishingPlugin".equals(n)) {
                fish = true;
            } else if ("WoodcutterPlugin".equals(n)) {
                wc = true;
            } else if ("ImpKillerPlugin".equals(n)) {
                imp = true;
            } else if ("Imps2Plugin".equals(n)) {
                imps2Ok = true;
            } else if ("StarMinerPlugin".equals(n)) {
                star = true;
            } else if ("GiantsPlugin".equals(n)) {
                giantsOk = true;
            } else if ("QuesterPlugin".equals(n)) {
                quest = true;
            }
        }
        if (!imp) {
            LoopHost.register(impKiller);
        }
        if (!imps2Ok) {
            LoopHost.register(imps2);
        }
        if (!wc) {
            LoopHost.register(woodcutter);
        }
        if (!fish) {
            LoopHost.register(fishing);
        }
        if (!star) {
            LoopHost.register(starMiner);
        }
        if (!giantsOk) {
            LoopHost.register(giants);
        }
        if (!quest) {
            LoopHost.register(quester);
        }
    }

    /** Client-start: bot altijd UIT — nooit vorige sessie herstellen. */
    private void forceBotOffOnClientStart() {
        BotRuntime.botEnabled = false;
        BotRuntime.pausedForResume = false;
        try {
            if (configManager != null) {
                configManager.setConfiguration("lonebot", "botEnabled", false);
            }
        } catch (Throwable ignored) {
        }
        log.info("[BotControl] client start → bot UIT");
    }

    private void maybeRefreshPotatoNoWalk() {
        if (potatoNoWalkTriedThisSession || NoWalkZones.hasPotatoCache()) {
            return;
        }
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        if (NoWalkZones.refreshPotatoFieldIfNear(client)) {
            potatoNoWalkTriedThisSession = true;
            log.info("[NoWalk] potato flood-fill: {} tegels", NoWalkZones.potatoTileCount());
        }
    }

    private void maybeWriteClientPreview() {
        String launched = System.getProperty("lonebot.account");
        if (launched == null || launched.trim().isEmpty()) {
            return;
        }
        ClientPreviewHelper.maybeWrite(client, launched.trim());
    }

    private void maybePublishClientWindowHwnd() {
        String launched = System.getProperty("lonebot.account");
        if (launched == null || launched.trim().isEmpty()) {
            return;
        }
        // Swing EDT — Window.getWindows / setTitle
        javax.swing.SwingUtilities.invokeLater(() ->
                com.lonebot.launcher.embed.ClientWindowIpc.publishFromClient(launched.trim()));
    }

    /** Credentials geladen maar UI op New/Existing → forceer loginIndex 2. */
    private void maybeForcePastWelcome() {
        if (client == null || launchedParsed == null) {
            return;
        }
        GameState gs = client.getGameState();
        if (gs != GameState.LOGIN_SCREEN && gs != GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            return;
        }
        if (LoginWelcomeAdvanceHelper.tickForcePastWelcome(client, launchedParsed)) {
            if (!launchedCredsApplied) {
                launchedCredsApplied = true;
                statusOverlay.setMessage("✓ " + System.getProperty("lonebot.account", "") + " — login klaar");
            }
        }
    }

    private void maybePollRemoteCommands() {
        String launched = System.getProperty("lonebot.account");
        if (launched == null || launched.trim().isEmpty()) {
            return;
        }
        ClientRemoteBus.Command cmd = ClientRemoteBus.pollCommand(launched.trim());
        if (cmd == null) {
            return;
        }
        log.info("Remote command ontvangen: {}", cmd);
        switch (cmd) {
            case START:
                LoneBotBotControl.start();
                ManagedAccountsStore.ManagedAccount row = findAccountByDisplayName(launched.trim());
                if (row != null) {
                    LoneBotBotControl.applyAccountSkills(row, configManager);
                    if (row.impKillerEnabled && config != null) {
                        applyImpSettings(config);
                    }
                    if (row.woodcuttingEnabled && config != null) {
                        applyWcSettings(config);
                    }
                    if (row.fishingEnabled && config != null) {
                        applyFishSettings(config);
                    }
                }
                statusOverlay.setMessage("Remote ▶ Start");
                break;
            case PAUSE:
                LoneBotBotControl.pause();
                statusOverlay.setMessage("Remote ⏸ Pauze");
                break;
            case STOP:
                LoneBotBotControl.stop();
                statusOverlay.setMessage("Remote ■ Stop");
                break;
            case BREAK_NOW:
                net.runelite.client.plugins.lonebot.login.RelogRuntime.requestBreakNow();
                statusOverlay.setMessage("Remote ☕ Break nu");
                BotRuntime.logConsole("[Remote] Break nu");
                break;
            case CANCEL_BREAK:
                net.runelite.client.plugins.lonebot.login.RelogRuntime.cancelBreak();
                statusOverlay.setMessage("Remote ✕ Break geannuleerd");
                BotRuntime.logConsole("[Remote] Annuleer break");
                break;
            case APPLY:
                launchedCredsApplied = false;
                maybeApplyLaunchedAccountCredentials();
                statusOverlay.setMessage("Remote ✎ Creds opnieuw toegepast");
                break;
            case RELOAD_SCRIPTS:
                Thread reload = new Thread(() -> {
                    String finalMsg = ScriptReloadHelper.reloadStagedInThisClient();
                    SwingUtilities.invokeLater(() -> {
                        statusOverlay.setMessage("Remote ↻ " + finalMsg);
                        BotRuntime.logConsole("[Remote] " + finalMsg);
                    });
                }, "lonebot-remote-reload-scripts");
                reload.setDaemon(true);
                reload.start();
                break;
            case UNLOAD_SCRIPTS:
                // Niet op client/EDT: wacht op loop-idle + classloader close kan seconden duren
                Thread unload = new Thread(() -> {
                    LoopHost.releaseAllScriptLoaders();
                    SwingUtilities.invokeLater(() -> {
                        statusOverlay.setMessage("Remote 🔓 script-jars ontgrendeld");
                        BotRuntime.logConsole("[Remote] script-jars ontgrendeld voor reload");
                    });
                }, "lonebot-remote-unload-scripts");
                unload.setDaemon(true);
                unload.start();
                break;
            case CLOSE:
                LoneBotBotControl.emergencyStop();
                ClientRemoteBus.writeStatus(launched.trim(), ClientRemoteBus.State.DEAD, 0);
                statusOverlay.setMessage("Remote ✕ Sluiten…");
                Thread closer = new Thread(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    System.exit(0);
                }, "lonebot-remote-close");
                closer.setDaemon(true);
                closer.start();
                break;
            default:
                break;
        }
        ClientRemoteBus.publishLocalBotStatus(launched.trim());
    }

    private void maybePublishRemoteStatus() {
        String launched = System.getProperty("lonebot.account");
        if (launched == null || launched.trim().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRemoteStatusMs < REMOTE_STATUS_INTERVAL_MS) {
            return;
        }
        lastRemoteStatusMs = now;
        ClientRemoteBus.publishLocalBotStatus(launched.trim());
    }

    private static ManagedAccountsStore.ManagedAccount findAccountByDisplayName(String name) {
        if (name == null) {
            return null;
        }
        for (ManagedAccountsStore.ManagedAccount a : ManagedAccountsStore.getAccounts()) {
            if (a != null && a.displayName != null && name.equalsIgnoreCase(a.displayName.trim())) {
                return a;
            }
        }
        return null;
    }

    private void maybeUpdateAccountStatSnapshot() {
        long now = System.currentTimeMillis();
        if (now - lastAccountStatSnapshotMs < ACCOUNT_STAT_SNAPSHOT_INTERVAL_MS) {
            return;
        }
        if (client == null || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) {
            return;
        }
        String name = client.getLocalPlayer().getName();
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        lastAccountStatSnapshotMs = now;
        try {
            AccountStatSnapshotsStore.AccountStatSnapshot prev =
                    AccountStatSnapshotsStore.snapshotForRow(AccountStatSnapshotsStore.load(), name);
            AccountStatSnapshotsStore.AccountStatSnapshot snap = new AccountStatSnapshotsStore.AccountStatSnapshot();
            snap.combatLevel = client.getLocalPlayer().getCombatLevel();
            snap.attack = client.getRealSkillLevel(Skill.ATTACK);
            snap.strength = client.getRealSkillLevel(Skill.STRENGTH);
            snap.defence = client.getRealSkillLevel(Skill.DEFENCE);
            snap.magic = client.getRealSkillLevel(Skill.MAGIC);
            snap.prayer = client.getRealSkillLevel(Skill.PRAYER);
            snap.woodcutting = client.getRealSkillLevel(Skill.WOODCUTTING);
            snap.mining = client.getRealSkillLevel(Skill.MINING);
            snap.fishing = client.getRealSkillLevel(Skill.FISHING);
            snap.updatedEpochMs = now;
            if (prev != null) {
                snap.hiscoreSuspectBanned = prev.hiscoreSuspectBanned;
                snap.totalGpApprox = prev.totalGpApprox;
                snap.quests = prev.quests;
            }
            AccountStatSnapshotsStore.merge(name, snap);
        } catch (Throwable t) {
            log.debug("account snapshot: {}", t.toString());
        }
    }

    private void maybeRunPeriodicHiscoreBanCheck() {
        if (!BotRuntime.botEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHiscoreBanCheckMs < HISCORE_BAN_CHECK_INTERVAL_MS) {
            return;
        }
        lastHiscoreBanCheckMs = now;
        ManagedAccountsStore.ManagedAccount row = findAccountForLocalPlayer();
        if (row == null || row.displayName == null || row.displayName.trim().isEmpty()) {
            return;
        }
        if (!com.lonebot.launcher.jagex.HiscoreNameRules.isHiscoreEligibleName(row.displayName, row.characterId)) {
            return;
        }
        final String rsn = row.displayName.trim();
        AccountStatSnapshotsStore.AccountStatSnapshot prev =
                AccountStatSnapshotsStore.snapshotForRow(AccountStatSnapshotsStore.load(), rsn);
        if (prev != null && prev.hiscoreSuspectBanned) {
            return; // CombatBot: eenmaal banned = geen periodieke recheck
        }
        Thread t = new Thread(() -> HiscoreBanChecker.checkAndPersist(rsn), "lonebot-hiscore-ban");
        t.setDaemon(true);
        t.start();
    }

    private void unifyMouseToggles() {
        if (config == null || configManager == null) {
            return;
        }
        boolean on = config.mouseFidgetEnabled();
        if (config.antiBanMouse() == on && config.antiBanMouseFidget() == on) {
            return;
        }
        applyingConfig.set(true);
        try {
            configManager.setConfiguration("lonebot", "antiBanMouse", on);
            configManager.setConfiguration("lonebot", "antiBanMouseFidget", on);
        } finally {
            applyingConfig.set(false);
        }
    }

    /** Sync RuneLite DevTools-toggle met ~/.lonebot/developer-mode.properties. */
    private void syncRuneliteDeveloperModeFromFile() {
        if (configManager == null) {
            return;
        }
        boolean fileOn = com.lonebot.launcher.RuneliteDeveloperMode.isEnabled();
        boolean cfgOn = config != null && config.runeliteDeveloperMode();
        if (fileOn == cfgOn) {
            return;
        }
        applyingConfig.set(true);
        try {
            configManager.setConfiguration("lonebot",
                    com.lonebot.launcher.RuneliteDeveloperMode.CONFIG_KEY, fileOn);
        } finally {
            applyingConfig.set(false);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event == null || !"lonebot".equals(event.getGroup()) || config == null) {
            return;
        }
        if (applyingConfig.get() || LoneBotConfigUiSync.applying()) {
            return;
        }
        String key = event.getKey();
        if (com.lonebot.launcher.RuneliteDeveloperMode.CONFIG_KEY.equals(key)) {
            boolean on = Boolean.parseBoolean(String.valueOf(event.getNewValue()));
            if (com.lonebot.launcher.RuneliteDeveloperMode.isEnabled() != on) {
                com.lonebot.launcher.RuneliteDeveloperMode.setEnabled(on);
            }
            BotRuntime.logConsole(on
                    ? "[DevTools] RuneLite developer mode AAN — herstart de client voor de DevTools-sidebar."
                    : "[DevTools] RuneLite developer mode uit — herstart de client om DevTools te verbergen.");
        }
        if ("mouseFidgetEnabled".equals(key)
                || "antiBanMouse".equals(key)
                || "antiBanMouseFidget".equals(key)) {
            boolean on = Boolean.parseBoolean(String.valueOf(event.getNewValue()));
            boolean already = config.mouseFidgetEnabled() == on
                    && config.antiBanMouse() == on
                    && config.antiBanMouseFidget() == on;
            if (!already) {
                applyingConfig.set(true);
                try {
                    configManager.setConfiguration("lonebot", "mouseFidgetEnabled", on);
                    configManager.setConfiguration("lonebot", "antiBanMouse", on);
                    configManager.setConfiguration("lonebot", "antiBanMouseFidget", on);
                } finally {
                    applyingConfig.set(false);
                }
            }
        }
        applyMovementFromConfig();
        if (BotRuntime.starMinerEnabled) {
            applyStarSettings(config);
        }
        if (BotRuntime.giantsKillerEnabled) {
            applyGiantsSettings(config);
        }
        if (BotRuntime.questEnabled || "questEnabled".equals(key) || "questTarget".equals(key)
                || "questRotationEnabled".equals(key) || "questSkipLowLevel".equals(key)) {
            applyQuestSettings(config);
        }
        LoneBotConfigUiSync.apply(key, event.getNewValue());
        if ("dialogAutoContinue".equals(key)) {
            BotRuntime.dialogAutoContinue = Boolean.parseBoolean(String.valueOf(event.getNewValue()));
        }
    }

    private void applyMovementFromConfig() {
        if (config == null) {
            return;
        }
        MouseSettings.setSpeedPercent(config.mouseSpeedPercent());
        MouseSettings.setRandomMoveEnabled(config.mouseFidgetEnabled());
        ExampleLoopedPlugin.mouseFidgetEnabled = config.mouseFidgetEnabled();
        int stepMin = config.walkStepMin();
        int stepMax = config.walkStepMax();
        // Oude 10–22 / te grote hops → 6–12 (doorlinken 4 vóór de vlag).
        if (!config.walkForceLargeSteps() && (stepMax > 12 || (stepMin == 10 && stepMax == 22))) {
            stepMin = Math.min(6, Math.max(3, stepMin));
            stepMax = 12;
            if (configManager != null) {
                configManager.setConfiguration("lonebot", "walkStepMin", stepMin);
                configManager.setConfiguration("lonebot", "walkStepMax", 12);
            }
        }
        WalkClickSettings.applyAll(
                config.walkUseMinimap(),
                config.walkUseCanvas(),
                config.walkUseUiZones(),
                config.walkUseCameraNudge(),
                config.walkUseInvoke(),
                config.walkFarCanvas(),
                stepMin,
                stepMax,
                config.walkForceLargeSteps(),
                config.walkReclickMs(),
                config.walkPostClickDelayMin(),
                config.walkPostClickDelayMax(),
                config.walkFlagProximityReclick(),
                config.walkReclickNearFlagMs(),
                config.walkTwinQuickClicks(),
                config.walkChainClickMin(),
                config.walkChainClickMax());
        // Invoke-only: nooit minimap/canvas-muis, ook als oude config die nog aan heeft.
        if (MouseSettings.forceInvokeOnly()) {
            WalkClickSettings.useMinimap = false;
            WalkClickSettings.useCanvas = false;
            WalkClickSettings.useInvokeWalk = true;
            WalkClickSettings.useFarCanvasWalk = false;
        }
        WalkClickSettings.setFinalClickWithinTiles(config.walkFinalClickTiles());
        WalkClickSettings.autoRun = config.walkAutoRun();
        WalkClickSettings.autoRunMinEnergy = config.walkAutoRunMinEnergy();
        WalkClickSettings.minimapFullyZoomedOut = config.walkMinimapFullyZoomedOut();
        WalkClickSettings.useShortcuts = config.walkUseShortcuts();
        BotRuntime.debugLogging = config.devDebugLogging();
        WalkClickSettings.canvasPreferRealClick = true;
        applyWalkCameraSettings(config);
        AntiBanSettings.apply(
                config.antiBanEnabled(),
                config.antiBanCamera(),
                config.antiBanIdle(),
                config.antiBanMouse(),
                config.antiBanMisclick(),
                config.antiBanTabGlance(),
                config.antiBanMouseFidget(),
                config.antiBanKeyboardPan(),
                config.antiBanFrequencySec());
        LoneBotConfig.GenieLampSkill lamp = config.genieLampSkill();
        net.storm.sdk.game.RandomEventSettings.apply(
                config.randomEventsEnabled(),
                lamp != null ? lamp.name() : "NONE");
        BotRuntime.randomEventStatus = config.randomEventsEnabled() ? "idle" : "uit";
        try {
            AntiBan.get().restartFidgetWorkerIfNeeded();
        } catch (Throwable ignored) {
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        String msg = event.getMessage();
        try {
            // Altijd de LIVE LoopHost-instance (hot-reload) — niet de dode builtin field
            boolean delivered = false;
            for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
                if (p == null) {
                    continue;
                }
                String simple = p.getClass().getSimpleName();
                if ("WoodcutterPlugin".equals(simple) || "FishingPlugin".equals(simple)
                        || "ImpKillerPlugin".equals(simple) || "CluePlugin".equals(simple)) {
                    try {
                        p.getClass().getMethod("onGameMessage", String.class).invoke(p, msg);
                        delivered = true;
                    } catch (Throwable t) {
                        log.debug("chat hook live: {}", t.toString());
                    }
                }
            }
            if (!delivered && woodcutter != null) {
                woodcutter.onGameMessage(msg);
            }
            if (fishing != null) {
                try {
                    fishing.onGameMessage(msg);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            log.debug("WC chat hook: {}", t.toString());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        Static.setClient(client);
        if (event.getGameState() == GameState.LOGGED_IN) {
            statusOverlay.setMessage(BotRuntime.cowCombatEnabled ? "Logged in — cow combat" : "Logged in");
            potatoNoWalkTriedThisSession = false;
            clientThread.invokeLater(() -> {
                if (net.storm.sdk.walls.WallDoorCaptureState.getLatest() != null
                        && NoWalkZones.applyIfPotatoField(
                        net.storm.sdk.walls.WallDoorCaptureState.getLatest())) {
                    potatoNoWalkTriedThisSession = true;
                    log.info("[NoWalk] potato from last Get walls: {} tegels",
                            NoWalkZones.potatoTileCount());
                }
            });
        } else if (event.getGameState() == GameState.LOGIN_SCREEN
                || event.getGameState() == GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            maybeApplyLaunchedAccountCredentials();
            StartupWorldHop.tick(client, clientThread, worldService);
            statusOverlay.setMessage("Login screen — LoneBot panel → credentials");
        }
    }

    /**
     * RuneLite Default World-plugin aanzetten + wereld zetten voor deze sessie
     * ({@code -Dlonebot.world} / worlds-pool).
     */
    private void applyStartupDefaultWorldConfig() {
        if (!"true".equalsIgnoreCase(System.getProperty("lonebot.worldHop", "false"))) {
            try {
                if (configManager != null) {
                    configManager.setConfiguration("runelite", "defaultworldplugin", false);
                    configManager.setConfiguration("defaultworld", "useLastWorld", false);
                }
            } catch (Throwable ignored) {
            }
            return;
        }
        java.util.List<Integer> pool = StartupWorldHop.resolvePool();
        if (pool.isEmpty() || configManager == null) {
            return;
        }
        // Zelfde pick als launcher: -Dlonebot.world (één ID), niet altijd pool.get(0)
        int pick = pool.size() == 1
                ? pool.get(0)
                : InstanceDefaultWorldConfig.pickWorld(
                        pool,
                        System.getProperty("lonebot.characterId",
                                System.getProperty("lonebot.account", "")),
                        null);
        if (pick < 300) {
            pick += 300;
        }
        try {
            configManager.setConfiguration("runelite", "defaultworldplugin", true);
            configManager.setConfiguration("defaultworld", "defaultWorld", pick);
            configManager.setConfiguration("defaultworld", "useLastWorld", false);
            log.info("[DefaultWorld] config: w{} (plugin aan, useLastWorld=false)", pick);
        } catch (Throwable t) {
            log.warn("[DefaultWorld] config zetten mislukt: {}", t.toString());
        }
    }

    /**
     * Sub-client gestart via launcher: pas {@code lonebot.account} credentials toe
     * en sla New/Existing User over tot Play Now.
     */
    private void maybeApplyLaunchedAccountCredentials() {
        String launched = System.getProperty("lonebot.account");
        if (launched == null || launched.trim().isEmpty()) {
            return;
        }
        if (launchedCredsApplied && launchedParsed != null) {
            return;
        }
        try {
            ManagedAccountsStore.ensureLoaded();
            ManagedAccountsStore.ManagedAccount row = null;
            String want = launched.trim();
            for (ManagedAccountsStore.ManagedAccount a : ManagedAccountsStore.getAccounts()) {
                if (a == null || a.displayName == null) {
                    continue;
                }
                if (want.equalsIgnoreCase(a.displayName.trim())) {
                    row = a;
                    break;
                }
            }
            JagexCredentialsHelper.ParsedJagexAccount parsed = null;
            if (row != null) {
                parsed = JagexCredentialsHelper.fromManaged(row);
            }
            if (parsed == null) {
                parsed = JagexCredentialsHelper.loadFromFile(JagexCredentialsHelper.defaultCredentialsFile());
            }
            if (parsed == null) {
                log.warn("Geen credentials voor gelanceerd account {}", want);
                return;
            }
            JagexCredentialsHelper.writeJagexCredentials(parsed);
            launchedParsed = parsed;
            LoginWelcomeAdvanceHelper.reset();
            LoginWelcomeAdvanceHelper.applyJagexAccount(client, parsed);
            // Direct proberen voorbij welcome te gaan
            LoginWelcomeAdvanceHelper.tickForcePastWelcome(client, parsed);
            launchedCredsApplied = LoginWelcomeAdvanceHelper.isPastWelcome(client);
            log.info("Launched-account apply account={} pastWelcome={} loginIndex={} char={}",
                    want, launchedCredsApplied, safeLoginIndex(),
                    parsed.toProperties().getProperty("JX_CHARACTER_ID", ""));
            statusOverlay.setMessage(launchedCredsApplied
                    ? ("✓ " + want + " — Play Now")
                    : ("… " + want + " — loginIndex forceren…"));
            ClientRemoteBus.publishReady(want);
        } catch (Throwable t) {
            log.warn("Launched-account apply mislukt: {}", t.toString());
        }
    }

    private int safeLoginIndex() {
        try {
            return client != null ? client.getLoginIndex() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Provides
    LoneBotConfig provideConfig(ConfigManager cm) {
        return cm.getConfig(LoneBotConfig.class);
    }

    /** Accounts-tab / panel: opnieuw Imp settings vanaf huidige config + ingelogde account. */
    public static void applyImpSettingsFromUi() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null && inst.config != null) {
            applyImpSettings(inst.config);
        }
    }

    static void applyWalkCameraSettings(LoneBotConfig config) {
        if (config == null) {
            return;
        }
        WalkCameraSettings.YawMode mode = WalkCameraSettings.YawMode.FOLLOW;
        try {
            mode = WalkCameraSettings.YawMode.valueOf(config.walkCamYawMode().trim().toUpperCase());
        } catch (Exception ignored) {
        }
        WalkCameraSettings.apply(
                config.walkCamEnabled(),
                config.walkCamHumanMmb(),
                mode,
                config.walkCamYawOffsetDeg(),
                config.walkCamPitch(),
                config.walkCamPitchBand(),
                config.walkCamZoomPercent());
        WalkCameraSettings.useKeyboard = config.walkCamUseKeyboard();
        WalkCameraSettings.visibilitySkipPercent = Math.max(50, Math.min(95, config.walkCamVisibilitySkip()));
    }

    static void applyMonkSettings(LoneBotConfig config) {
        if (config == null) {
            return;
        }
        syncClueInterruptFlags(config);
        // Combat-locaties: één eat%/flee — sync naar monk runtime
        int eat = Math.max(5, Math.min(90, config.combatEatPercent()));
        int crit = Math.max(1, Math.min(15, config.combatCriticalHp()));
        BotRuntime.monkEatPercent = eat;
        BotRuntime.monkCriticalHp = crit;
        BotRuntime.monkFoodAmount = Math.max(1, Math.min(28, config.monkFoodAmount()));
        BotRuntime.monkCabbagePickAmount = Math.max(1, Math.min(28, config.monkCabbagePickAmount()));
        BotRuntime.monkCabbagePickEnabled = config.monkCabbagePickEnabled();
        LoneBotConfig.MonkFoodSource src = config.monkFoodSource();
        BotRuntime.monkFoodSource = src != null ? src.name() : "AUTO";
        BotRuntime.monkBankWhenNoFood = config.monkBankWhenNoFood();
        BotRuntime.monkLogoutWhenNoFood = config.monkLogoutWhenNoFood();
        BotRuntime.monkHealViaTalk = config.monkHealViaTalk();
    }

    public static void applyMonkSettingsFromUi() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null && inst.config != null) {
            applyMonkSettings(inst.config);
        }
    }

    static void applyImpSettings(LoneBotConfig config) {
        if (config == null) {
            return;
        }
                syncClueInterruptFlags(config);
String accountKey = resolveLoggedInAccountKey();
        ManagedAccountsStore.ManagedAccount row = ManagedAccountsStore.findByDisplayName(accountKey);
        ImpsTypes.ImpsCombatStyle style = ManagedAccountsStore.resolveImpsCombatStyle(config, accountKey);

        int ashPct = config.impsAshHumanizeChance();
        boolean ashHumanize = config.impsAshHumanize();
        if (row != null && row.impsAshLootPickPercent > 0) {
            ashHumanize = true;
            ashPct = row.impsAshLootPickPercent;
        }

        ImpsTypes.ImpsMageSpell spell = config.impsMageSpell();
        boolean magicAuto = row != null && row.magicAutoUpdate;
        ImpKillerPlugin.applySettings(
                style,
                spell,
                config.impsHuntingX(),
                config.impsHuntingY(),
                config.impsHuntingRadius(),
                IMP_NPC_ID,
                config.impsLootItems(),
                config.impsScatterAshes(),
                config.impsBankThreshold(),
                config.impsMinCoins(),
                config.impsAvoidScorpions(),
                config.impsAttackScorpions(),
                config.impsScorpionAvoidRadius(),
                config.impsScorpionLevel(),
                config.impsGeSellEnabled(),
                config.impsGeSellAfterBanks(),
                config.impsGeSellPrice(),
                config.impsGeSellItems(),
                config.impsGearPrepEnabled(),
                config.impsGeRestockEnabled(),
                config.impsQuestLootEnabled(),
                ashHumanize,
                ashPct,
                config.impsIdleRoamSeconds(),
                config.impsMeleeOpeningAirStrike(),
                config.impsLootPickupMode() != null ? config.impsLootPickupMode().name() : "AUTO",
                config.impsLootPickupStrict(),
                accountKey != null ? accountKey : "",
                magicAuto,
                config.impsLootDelayEnabled(),
                config.impsLootDelayKills(),
                config.impsSpecialLootItems());
    }

    public static void applyWcSettingsFromUi() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null) {
            applyWcSettings(inst.config);
        }
    }

    /** UI: Bank / Firemaking / Drop — mutually exclusive via legacy flags. */
    public static void applyWcLogMode(LoneBotConfig.WcLogMode mode) {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst == null || inst.configManager == null || mode == null) {
            return;
        }
        boolean fm = mode == LoneBotConfig.WcLogMode.FIREMAKING;
        boolean drop = mode == LoneBotConfig.WcLogMode.DROP;
        inst.configManager.setConfiguration("lonebot", "wcFiremaking", fm);
        inst.configManager.setConfiguration("lonebot", "wcDropLogs", drop);
        applyWcSettings(inst.config);
        log.info("[WC] log-mode → {} (fm={} drop={})", mode, fm, drop);
    }

    public static LoneBotConfig.WcLogMode currentWcLogMode() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst == null || inst.config == null) {
            return LoneBotConfig.WcLogMode.BANK;
        }
        return LoneBotConfig.WcLogMode.fromFlags(inst.config.wcFiremaking(), inst.config.wcDropLogs());
    }

    public static void applyFishSettingsFromUi() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null) {
            applyFishSettings(inst.config);
        }
    }

    public static void applyStarSettingsFromUi() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null) {
            applyStarSettings(inst.config);
        }
    }

    public static void applyGiantsSettingsFromUi() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null && inst.config != null) {
            applyGiantsSettings(inst.config);
        }
    }

    static void applyGiantsSettings(LoneBotConfig config) {
        if (config == null) {
            return;
        }
                syncClueInterruptFlags(config);
GiantsTypes.GiantsCombatStyle style = config.giantsCombatStyle();
        GiantsTypes.GiantsMageSpell spell = config.giantsMageSpell();
        GiantsPlugin.applySettings(
                style,
                spell,
                config.giantsLootItems(),
                config.giantsEatPercent(),
                config.giantsFoodAmount(),
                config.giantsFoodBankThreshold(),
                false,
                "AUTO",
                false);
    }

    public static void applyQuestSettingsFromUi() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null) {
            applyQuestSettings(inst.config);
        }
    }

    static void applyQuestSettings(LoneBotConfig config) {
        if (config == null) {
            return;
        }
        String tgt = com.lonebot.example.quest.QuestBotTarget.from(config.questTarget()).name();
        boolean rot = config.questRotationEnabled();
        boolean skip = config.questSkipLowLevel();
        BotRuntime.questTarget = tgt;
        BotRuntime.questRotationEnabled = rot;
        BotRuntime.questSkipLowLevel = skip;
        QuesterPlugin.target = tgt;
        QuesterPlugin.rotationEnabled = rot;
        QuesterPlugin.skipLowLevel = skip;
        for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"QuesterPlugin".equals(p.getClass().getSimpleName())) {
                continue;
            }
            try {
                Class<?> c = p.getClass();
                c.getField("target").set(null, tgt);
                c.getField("rotationEnabled").setBoolean(null, rot);
                c.getField("skipLowLevel").setBoolean(null, skip);
                Object ver = c.getField("VERSION").get(null);
                if (ver != null) {
                    BotRuntime.questPluginVersion = String.valueOf(ver);
                }
            } catch (Throwable t) {
                log.warn("applyQuestSettings live: {}", t.toString());
            }
        }
    }

    /** Sla WC-centers blob op + sync runtime (choose-option / panel). */
    public static void persistWcCenters(String blob) {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst == null || inst.configManager == null) {
            return;
        }
        String value = blob != null && !blob.isBlank() ? blob : WcCenters.DEFAULT_BLOB;
        value = WcCenters.ensureNames(value);
        inst.configManager.setConfiguration("lonebot", "wcCenters", value);
        WoodcutterPlugin.centersBlob = value;
        applyWcSettings(inst.config);
        // Hot-reload plugins: force blob even if config proxy nog stale is
        for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"WoodcutterPlugin".equals(p.getClass().getSimpleName())) {
                continue;
            }
            try {
                p.getClass().getField("centersBlob").set(null, value);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Sla WC-locatie combo op (na ‘voeg center toe’ — anders blijft AUTO). */
    public static void persistWcLocation(String loc) {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst == null || inst.configManager == null) {
            return;
        }
        String value = loc == null || loc.isBlank() || "null".equalsIgnoreCase(loc.trim())
                ? "AUTO" : loc.trim();
        inst.configManager.setConfiguration("lonebot", "wcLocation", value);
        WoodcutterPlugin.preferredLocation = value;
        for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"WoodcutterPlugin".equals(p.getClass().getSimpleName())) {
                continue;
            }
            try {
                p.getClass().getField("preferredLocation").set(null, value);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Live blob (ConfigManager eerst — proxy kan stale zijn na setConfiguration). */
    public static String liveWcCentersBlob() {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null && inst.configManager != null) {
            String fromCm = inst.configManager.getConfiguration("lonebot", "wcCenters");
            if (fromCm != null && !fromCm.isBlank()) {
                return fromCm;
            }
        }
        if (WoodcutterPlugin.centersBlob != null && !WoodcutterPlugin.centersBlob.isBlank()) {
            return WoodcutterPlugin.centersBlob;
        }
        if (inst != null && inst.config != null
                && inst.config.wcCenters() != null && !inst.config.wcCenters().isBlank()) {
            return inst.config.wcCenters();
        }
        return WcCenters.DEFAULT_BLOB;
    }

    /** Reset naar default spots. */
    public static void resetWcCentersToDefault() {
        persistWcCenters(WcCenters.DEFAULT_BLOB);
    }

    /** Sla Combat/Mining/Fishing centers op. */
    public static void persistAreaCenters(AreaCenters.Skill skill, String blob) {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst == null || inst.configManager == null || skill == null) {
            return;
        }
        String value = blob != null && !blob.isBlank() ? blob : skill.defaultBlob();
        value = AreaCenters.ensureNames(value, skill.label);
        inst.configManager.setConfiguration("lonebot", skill.configKey, value);
        if (skill == AreaCenters.Skill.FISHING) {
            applyFishSettings(inst.config);
            for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
                if (p == null || !"FishingPlugin".equals(p.getClass().getSimpleName())) {
                    continue;
                }
                try {
                    p.getClass().getField("centersBlob").set(null, value);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static void resetAreaCentersToDefault(AreaCenters.Skill skill) {
        if (skill != null) {
            persistAreaCenters(skill, skill.defaultBlob());
        }
    }

    public static String readAreaCenters(AreaCenters.Skill skill) {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst == null || inst.config == null || skill == null) {
            return skill != null ? skill.defaultBlob() : "";
        }
        LoneBotConfig cfg = inst.config;
        switch (skill) {
            case COMBAT:
                return AreaCenters.orDefault(cfg.combatCenters(), skill);
            case MINING:
                return AreaCenters.orDefault(cfg.miningCenters(), skill);
            case FISHING:
                return AreaCenters.orDefault(cfg.fishingCenters(), skill);
            default:
                return skill.defaultBlob();
        }
    }

    /** Live blob (ConfigManager eerst — proxy kan stale zijn na setConfiguration). */
    public static String liveAreaCentersBlob(AreaCenters.Skill skill) {
        if (skill == null) {
            return "";
        }
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst != null && inst.configManager != null) {
            String fromCm = inst.configManager.getConfiguration("lonebot", skill.configKey);
            if (fromCm != null && !fromCm.isBlank()) {
                return fromCm;
            }
        }
        return readAreaCenters(skill);
    }

    public static boolean isAreaCentersMenuEnabled(AreaCenters.Skill skill) {
        LoneBotBootstrapPlugin inst = INSTANCE;
        if (inst == null || inst.config == null || skill == null) {
            return true;
        }
        LoneBotConfig cfg = inst.config;
        switch (skill) {
            case COMBAT:
                return cfg.combatCentersMenuEnabled();
            case MINING:
                return cfg.miningCentersMenuEnabled();
            case FISHING:
                return cfg.fishingCentersMenuEnabled();
            default:
                return false;
        }
    }


    static void syncClueInterruptFlags(LoneBotConfig config) {
        if (config == null) {
            return;
        }
        BotRuntime.wcClueSolver = config.wcClueSolver();
        BotRuntime.fishClueSolver = config.fishClueSolver();
        BotRuntime.starClueSolver = config.starClueSolver();
        BotRuntime.impsClueSolver = config.impsClueSolver();
        BotRuntime.imps2ClueSolver = config.imps2ClueSolver();
        BotRuntime.giantsClueSolver = config.giantsClueSolver();
        // Combat: één knop voor Cow + Monk (+ toekomstige combat-locs)
        boolean combatClue = config.combatClueSolver();
        BotRuntime.monkClueSolver = combatClue;
        BotRuntime.cowClueSolver = combatClue;
        BotRuntime.clueBankCasket = config.clueBankCasket();
        BotRuntime.clueGeBuyMissing = config.clueGeBuyMissing();
        BotRuntime.dialogAutoContinue = config.dialogAutoContinue();
    }

    static void applyWcSettings(LoneBotConfig config) {
        if (config == null) {
            return;
        }
                syncClueInterruptFlags(config);
boolean firemaking = config.wcFiremaking();
        boolean dropLogs = config.wcDropLogs();
        boolean forestryEvents = config.wcForestryEvents();
        boolean birdNests = config.wcBirdNests();
        boolean useSpecificTree = config.wcUseSpecificTree();
        String treeName = config.wcTreeName() != null ? config.wcTreeName().trim() : "";
        String loc = config.wcLocation();
        String preferred = (loc != null && !loc.isBlank() && !"null".equalsIgnoreCase(loc.trim()))
                ? loc.trim() : "AUTO";
        String centersBlob = config.wcCenters();
        if (centersBlob == null || centersBlob.isBlank()) {
            centersBlob = WcCenters.DEFAULT_BLOB;
        }

        boolean anyLive = false;
        for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"WoodcutterPlugin".equals(p.getClass().getSimpleName())) {
                continue;
            }
            try {
                Class<?> c = p.getClass();
                c.getField("firemaking").setBoolean(null, firemaking);
                c.getField("dropLogs").setBoolean(null, dropLogs);
                c.getField("forestryEvents").setBoolean(null, forestryEvents);
                c.getField("birdNests").setBoolean(null, birdNests);
                try {
                    c.getField("geAxeRestockEnabled").setBoolean(null, config.wcGeAxeRestockEnabled());
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("bankLumbridge").setBoolean(null, config.wcBankLumbridge());
                    c.getField("bankDraynor").setBoolean(null, config.wcBankDraynor());
                } catch (NoSuchFieldException ignored) {
                }
                c.getField("preferredLocation").set(null, preferred);
                try {
                    c.getField("centersBlob").set(null, centersBlob);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("useSpecificTree").setBoolean(null, useSpecificTree);
                    c.getField("treeName").set(null, treeName);
                } catch (NoSuchFieldException ignored) {
                }
                anyLive = true;
                try {
                    Object ver = c.getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.wcPluginVersion = String.valueOf(ver);
                    }
                } catch (Throwable ignored) {
                }
            } catch (Throwable t) {
                log.warn("applyWcSettings live: {}", t.toString());
            }
        }
        // Parent-classpath statics (cold start / geen hot-reload)
        WoodcutterPlugin.firemaking = firemaking;
        WoodcutterPlugin.dropLogs = dropLogs;
        WoodcutterPlugin.forestryEvents = forestryEvents;
        WoodcutterPlugin.birdNests = birdNests;
        WoodcutterPlugin.geAxeRestockEnabled = config.wcGeAxeRestockEnabled();
        WoodcutterPlugin.bankLumbridge = config.wcBankLumbridge();
        WoodcutterPlugin.bankDraynor = config.wcBankDraynor();
        WoodcutterPlugin.preferredLocation = preferred;
        WoodcutterPlugin.centersBlob = centersBlob;
        WoodcutterPlugin.useSpecificTree = useSpecificTree;
        WoodcutterPlugin.treeName = treeName;
        if (!anyLive) {
            // ok — parent instance is registered at startUp
        }
        // Persist per RSN (Imp-achtig)
        String acc = resolveLoggedInAccountKey();
        if (acc != null && !acc.isBlank()) {
            AccountWcSettingsStore.writeFromConfig(acc, config);
        }
    }

    static void applyFishSettings(LoneBotConfig config) {
        if (config == null) {
            return;
        }
                syncClueInterruptFlags(config);
boolean dropFish = config.fishingDropFish();
        boolean cookEnabled = config.fishingCookEnabled();
        boolean restockEnabled = config.fishingRestockEnabled();
        boolean useSpecific = config.fishingUseSpecificMethod();
        boolean useVarrock = config.fishingUseVarrockTeleport();
        String spotName = config.fishingSpotName() != null ? config.fishingSpotName().trim() : "Fishing spot";
        String action = config.fishingAction() != null ? config.fishingAction().trim() : "Net";
        String centersBlob = config.fishingCenters();
        if (centersBlob == null || centersBlob.isBlank()) {
            centersBlob = AreaCenters.DEFAULT_FISHING;
        }
        String loc = config.fishingLocation();
        String preferred = (loc != null && !loc.isBlank() && !"null".equalsIgnoreCase(loc.trim()))
                ? loc.trim() : "AUTO";
        int baitMin = config.fishingBaitMin();
        int restockAmount = config.fishingRestockAmount();
        int baitPrice = config.fishingBaitPrice();
        int featherPrice = config.fishingFeatherPrice();
        int delayMin = config.fishingInteractDelayMin();
        int delayMax = config.fishingInteractDelayMax();

        for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"FishingPlugin".equals(p.getClass().getSimpleName())) {
                continue;
            }
            try {
                Class<?> c = p.getClass();
                c.getField("dropFish").setBoolean(null, dropFish);
                c.getField("cookEnabled").setBoolean(null, cookEnabled);
                c.getField("restockEnabled").setBoolean(null, restockEnabled);
                c.getField("useSpecificMethod").setBoolean(null, useSpecific);
                c.getField("useVarrockTeleport").setBoolean(null, useVarrock);
                c.getField("spotName").set(null, spotName);
                c.getField("action").set(null, action);
                c.getField("centersBlob").set(null, centersBlob);
                try {
                    c.getField("preferredLocation").set(null, preferred);
                } catch (NoSuchFieldException ignored) {
                }
                c.getField("baitMin").setInt(null, baitMin);
                c.getField("restockAmount").setInt(null, restockAmount);
                c.getField("baitPrice").setInt(null, baitPrice);
                c.getField("featherPrice").setInt(null, featherPrice);
                c.getField("interactDelayMin").setInt(null, delayMin);
                c.getField("interactDelayMax").setInt(null, delayMax);
                Object ver = c.getField("VERSION").get(null);
                if (ver != null) {
                    BotRuntime.fishPluginVersion = String.valueOf(ver);
                }
            } catch (Throwable t) {
                log.warn("applyFishSettings live: {}", t.toString());
            }
        }
        FishingPlugin.dropFish = dropFish;
        FishingPlugin.cookEnabled = cookEnabled;
        FishingPlugin.restockEnabled = restockEnabled;
        FishingPlugin.useSpecificMethod = useSpecific;
        FishingPlugin.useVarrockTeleport = useVarrock;
        FishingPlugin.spotName = spotName;
        FishingPlugin.action = action;
        FishingPlugin.centersBlob = centersBlob;
        FishingPlugin.preferredLocation = preferred;
        FishingPlugin.baitMin = baitMin;
        FishingPlugin.restockAmount = restockAmount;
        FishingPlugin.baitPrice = baitPrice;
        FishingPlugin.featherPrice = featherPrice;
        FishingPlugin.interactDelayMin = delayMin;
        FishingPlugin.interactDelayMax = delayMax;
    }

    static void applyStarSettings(LoneBotConfig config) {
        if (config == null) {
            return;
        }
                syncClueInterruptFlags(config);
boolean avoidWild = config.starMinerAvoidWilderness();
        boolean f2pOnly = config.starMinerF2pOnly();
        boolean hop = config.starMinerHopEnabled();
        int waitExtra = Math.max(0, Math.min(8, config.starMinerWaitTiersAbove()));
        boolean waitLvl = waitExtra > 0;
        int minTier = Math.max(1, Math.min(9, config.starMinerMinTier()));
        LoneBotConfig.StarWaitActivity waitAct = config.starMinerWaitActivity();
        String waitActName = waitAct != null ? waitAct.name() : "NONE";
        LoneBotConfig.StarParkSkill parkSkill = config.starMinerParkSkill();
        String parkSkillName = parkSkill != null ? parkSkill.name() : "NONE";
        LoneBotConfig.StarTravelModeOpt travel = config.starMinerTravelMode();
        String travelName = travel != null ? travel.name() : "FATAL_ONLY";
        boolean wwTravel = config.starMinerWorldWalkerTravel();
        boolean teles = config.starMinerTeleportsEnabled();
        boolean bankGems = config.starMinerBankGemsEnabled();
        int gemsAt = Math.max(1, Math.min(28, config.starMinerBankGemsAt()));
        String jsonUrl = config.starMinerJsonUrl() != null ? config.starMinerJsonUrl().trim() : "";

        for (net.storm.api.plugins.LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"StarMinerPlugin".equals(p.getClass().getSimpleName())) {
                continue;
            }
            try {
                Class<?> c = p.getClass();
                c.getField("avoidWilderness").setBoolean(null, avoidWild);
                c.getField("f2pOnly").setBoolean(null, f2pOnly);
                c.getField("hopEnabled").setBoolean(null, hop);
                try {
                    c.getField("waitForLevel").setBoolean(null, waitLvl);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("waitTiersAbove").setInt(null, waitExtra);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("minTier").setInt(null, minTier);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("waitActivity").set(null, waitActName);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("waitParkSkill").set(null, parkSkillName);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("travelMode").set(null, travelName);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("worldWalkerTravel").setBoolean(null, wwTravel);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("teleportsEnabled").setBoolean(null, teles);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("bankGemsEnabled").setBoolean(null, bankGems);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    c.getField("bankGemsAt").setInt(null, gemsAt);
                } catch (NoSuchFieldException ignored) {
                }
                c.getField("jsonUrl").set(null, jsonUrl);
                Object ver = c.getField("VERSION").get(null);
                if (ver != null) {
                    BotRuntime.starPluginVersion = String.valueOf(ver);
                }
            } catch (Throwable t) {
                log.warn("applyStarSettings live: {}", t.toString());
            }
        }
        StarMinerPlugin.avoidWilderness = avoidWild;
        StarMinerPlugin.f2pOnly = f2pOnly;
        StarMinerPlugin.hopEnabled = hop;
        StarMinerPlugin.waitTiersAbove = waitExtra;
        StarMinerPlugin.minTier = minTier;
        StarMinerPlugin.waitForLevel = waitLvl;
        StarMinerPlugin.waitActivity = waitActName;
        StarMinerPlugin.waitParkSkill = parkSkillName;
        StarMinerPlugin.travelMode = travelName;
        StarMinerPlugin.worldWalkerTravel = wwTravel;
        StarMinerPlugin.teleportsEnabled = teles;
        StarMinerPlugin.bankGemsEnabled = bankGems;
        StarMinerPlugin.bankGemsAt = gemsAt;
        StarMinerPlugin.jsonUrl = jsonUrl;
    }

    /** Titel voor client control panel (niet de multi-account launcher). */
    public static String controlPanelWindowTitle() {
        String acc = resolveLoggedInAccountKey();
        if (acc == null || acc.isBlank()) {
            return "LoneBot Control · (geen account) · v" + VERSION;
        }
        return "LoneBot Control · " + acc + " · v" + VERSION;
    }

    /** Korte headerregel in de sidebar/pop-out. */
    public static String controlPanelHeaderText() {
        String acc = resolveLoggedInAccountKey();
        if (acc == null || acc.isBlank()) {
            return "LoneBot Control · (geen account) · v" + VERSION;
        }
        return "LoneBot Control · " + acc + " · v" + VERSION;
    }

    /** Ingelogde RSN, anders launcher {@code lonebot.account}. */
    public static String resolveLoggedInAccountKey() {
        try {
            Client c = Static.getClient();
            if (c != null && c.getLocalPlayer() != null && c.getLocalPlayer().getName() != null) {
                return c.getLocalPlayer().getName().replace('\u00A0', ' ').trim();
            }
        } catch (Throwable ignored) {
        }
        String launched = System.getProperty("lonebot.account");
        return launched != null ? launched.trim() : "";
    }

    private static ManagedAccountsStore.ManagedAccount findAccountForLocalPlayer() {
        String cid = System.getProperty("lonebot.characterId", "");
        if (cid != null && !cid.isBlank()) {
            ManagedAccountsStore.ManagedAccount byChar = ManagedAccountsStore.findByCharacterId(cid.trim());
            if (byChar != null) {
                return byChar;
            }
        }
        return ManagedAccountsStore.findByDisplayName(resolveLoggedInAccountKey());
    }

    private void maybeRenameAccountFromLivePlayer() {
        if (client == null || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAccountRenameMs < 4000L) {
            return;
        }
        String name = client.getLocalPlayer().getName();
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        lastAccountRenameMs = now;
        String cid = System.getProperty("lonebot.characterId", "");
        try {
            AccountDisplayRename.applyLiveRsn(cid, name);
        } catch (Throwable t) {
            log.debug("[Jagex/import] RSN rename: {}", t.toString());
        }
    }

    private static BufferedImage loadPluginIcon() {
        try {
            BufferedImage img = ImageUtil.loadImageResource(LoneBotBootstrapPlugin.class, "icon.png");
            if (img != null) {
                return ImageUtil.resizeImage(img, 16, 16);
            }
        } catch (Throwable ignored) {
        }
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = icon.createGraphics();
        g.setColor(new java.awt.Color(26, 26, 30));
        g.fillRect(0, 0, 16, 16);
        g.setColor(new java.awt.Color(79, 195, 247));
        g.fillRect(3, 2, 3, 12);
        g.fillRect(3, 11, 10, 3);
        g.setColor(new java.awt.Color(255, 179, 0));
        g.fillRect(12, 2, 2, 2);
        g.dispose();
        return icon;
    }
}
