package com.lonebot.example;

import com.lonebot.example.starminer.StarFeed;
import com.lonebot.example.starminer.StarMinerLoop;
import com.lonebot.example.starminer.StarParkHandoff;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * LoneBot Star Miner — Discord/Portal/JSON feed → hop → loop → crashed star minen.
 */
@PluginDescriptor(
        name = "LoneBot Star Miner",
        description = "Shooting stars: Discord + OSRS Portal + 07.gg, world hop, lopen, Mine"
)
public class StarMinerPlugin extends LoopedPlugin {

    public static final String VERSION = "0.1.107";

    public static volatile boolean avoidWilderness = true;
    public static volatile boolean f2pOnly = true;
    public static volatile boolean hopEnabled = true;
    /** Skip feed-sterren onder deze tier (T1 is vaak al weg). Default 2. */
    public static volatile int minTier = 2;
    /** Te hoge ster: 0 = skip (andere minebare ster). 1 = T5-miner wacht bij T6, niet T7+. 8 = alle lagen. */
    public static volatile int waitTiersAbove = 1;
    /** Afgeleid van waitTiersAbove — true als we ergens mogen wachten. */
    public static volatile boolean waitForLevel = true;
    /** NONE / HIGH_ALCH / MINE / WOODCUT — string i.v.m. hot-reload. */
    public static volatile String waitActivity = "NONE";
    /**
     * Geen minebare tier: andere skill tot de feed een ster heeft die je aankunt.
     * {@link net.storm.sdk.bot.BotRuntime.ActiveSkill} name, of {@code NONE}.
     */
    public static volatile String waitParkSkill = "NONE";
    public static volatile String jsonUrl = "";
    /**
     * Travel-mode naar ster: {@link com.lonebot.example.starminer.StarTravelMode} name.
     * Default: FATAL_ONLY (doorlopen + Mine ≤30).
     */
    public static volatile String travelMode = "FATAL_ONLY";
    /**
     * Default uit: bestaande Star-travel. Aan: WorldWalker tot ≤30 tegels, daarna Mine-COS.
     */
    public static volatile boolean worldWalkerTravel = false;
    /** F2P spell-teles (Varrock/Lumb/Fally) + Home als Lumb-fallback. */
    public static volatile boolean teleportsEnabled = true;
    /** Uncut gems banken buiten minen. */
    public static volatile boolean bankGemsEnabled = false;
    /** Bank als gem-count ≥ deze waarde (1–28). */
    public static volatile int bankGemsAt = 10;

    public enum WaitActivity {
        NONE("Niets (wacht)", "wacht"),
        HIGH_ALCH("High Alchemy", "alch"),
        MINE("Mine in de buurt", "mine"),
        WOODCUT("Woodcut in de buurt", "wc");

        public final String label;
        public final String shortLabel;

        WaitActivity(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }

        @Override
        public String toString() {
            return label;
        }

        public static WaitActivity from(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            for (WaitActivity a : values()) {
                if (a.name().equalsIgnoreCase(raw) || a.label.equalsIgnoreCase(raw)) {
                    return a;
                }
            }
            return NONE;
        }
    }

    public static WaitActivity waitActivity() {
        return WaitActivity.from(waitActivity);
    }

    public static com.lonebot.example.starminer.StarTravelMode travelMode() {
        return com.lonebot.example.starminer.StarTravelMode.from(travelMode);
    }

    /** Checkbox of travel-mode 11. */
    public static boolean worldWalkerTravel() {
        if (worldWalkerTravel) {
            return true;
        }
        return travelMode() == com.lonebot.example.starminer.StarTravelMode.WORLD_WALKER;
    }

    public static int waitTiersAbove() {
        int v = waitTiersAbove;
        if (v < 0) {
            return 0;
        }
        return Math.min(8, v);
    }

    /** Laagste tier die we nog bezoeken (1–9). T1 vaak al weg → default 2. */
    public static int minTier() {
        int v = minTier;
        if (v < 1) {
            return 1;
        }
        return Math.min(9, v);
    }

    public static int bankGemsAt() {
        int v = bankGemsAt;
        if (v < 1) {
            return 1;
        }
        return Math.min(28, v);
    }

    /** Hoogste T-laag waar we naartoe gaan (minen of wachten). */
    public static int maxVisitTier(int miningLevel) {
        int can = com.lonebot.example.starminer.StarObjects.highestMineableTier(miningLevel);
        return Math.min(9, can + waitTiersAbove());
    }

    public static boolean wouldVisitTier(int starTier, int miningLevel) {
        if (starTier <= 0) {
            return true;
        }
        if (starTier < minTier()) {
            return false;
        }
        return starTier <= maxVisitTier(miningLevel);
    }

    private static final Logger log = LoggerFactory.getLogger(StarMinerPlugin.class);

    private final StarMinerLoop loop = new StarMinerLoop();

    @Override
    public void startUp() {
        super.startUp();
        BotRuntime.starPluginVersion = VERSION;
        StarFeed.get().start();
        publishOverlay("start");
        log.info("[StarMiner] startUp v{}", VERSION);
    }

    @Override
    public void shutDown() {
        StarFeed.get().stop();
        loop.reset();
        super.shutDown();
    }

    public StarMinerLoop getLoop() {
        return loop;
    }

    private long seenStartGen;
    private long seenParkResumeGen;
    /** Edge: alleen 1× stoppen bij AAN→UIT (geen spam elke tick). */
    private boolean wasRunning;

    @Override
    public int loop() {
        if (net.storm.sdk.loop.LoopHost.isReloading()) {
            return 50;
        }
        BotRuntime.starPluginVersion = VERSION;
        boolean on = BotRuntime.botEnabled && BotRuntime.starMinerEnabled;
        if (BotRuntime.botEnabled && BotRuntime.starParkActive && !BotRuntime.starMinerEnabled) {
            if (StarParkHandoff.tryResume()) {
                loop.onParkResume();
                BotRuntime.starStatus = "park hervat";
                publishOverlay(BotRuntime.starStatus);
                return 400;
            }
            BotRuntime.starStatus = "park · " + (BotRuntime.starParkWaitSkill != null
                    ? BotRuntime.starParkWaitSkill.name() : "?");
            publishOverlay(BotRuntime.starStatus);
            return 800;
        }
        if (on && BotRuntime.botStartGeneration != seenStartGen) {
            seenStartGen = BotRuntime.botStartGeneration;
            loop.onBotStarted(BotRuntime.lastStartWasResume);
            BotRuntime.logConsole("[Star] loop start v" + VERSION
                    + " travel=" + travelMode()
                    + " ww=" + worldWalkerTravel()
                    + " resume=" + BotRuntime.lastStartWasResume
                    + " allowed=" + BotRuntime.isSkillPluginAllowed("StarMinerPlugin"));
        }
        if (!on) {
            if (wasRunning) {
                wasRunning = false;
                try {
                    loop.onBotStopped();
                } catch (Throwable ignored) {
                }
            }
            BotRuntime.starStatus = BotRuntime.botEnabled ? "uit" : "bot uit";
            BotRuntime.starHopWorld = 0;
            publishOverlay(BotRuntime.starStatus);
            return 800;
        }
        wasRunning = true;
        if (BotRuntime.starParkResumeGen != seenParkResumeGen) {
            seenParkResumeGen = BotRuntime.starParkResumeGen;
            loop.onParkResume();
        }
        if (loop.isLoggingOut()) {
            try {
                int delay = loop.tick();
                BotRuntime.starStatus = loop.getStatus();
                publishOverlay(null);
                return delay;
            } catch (Throwable t) {
                log.warn("[StarMiner] logout loop: {}", t.toString(), t);
                return 800;
            }
        }
        BotRuntime.enforceExclusiveSkills();
        if (!BotRuntime.starMinerEnabled) {
            BotRuntime.starStatus = "uit (andere skill)";
            publishOverlay(BotRuntime.starStatus);
            return 800;
        }
        if (net.storm.sdk.bot.ClueSkillHandoff.tryHandoffFromActiveSkill()) {
            BotRuntime.starStatus = "clue handoff";
            publishOverlay(BotRuntime.starStatus);
            return 400;
        }

        try {
            int delay = loop.tick();
            BotRuntime.starStatus = loop.getStatus();
            publishOverlay(null);
            return delay;
        } catch (Throwable t) {
            log.warn("[StarMiner] loop error: {}", t.toString(), t);
            BotRuntime.starStatus = "err: " + t.getClass().getSimpleName();
            publishOverlay(BotRuntime.starStatus);
            return 1000;
        }
    }

    private void publishOverlay(String statusOverride) {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("Bot: " + (BotRuntime.botEnabled ? "AAN" : "UIT")
                    + (BotRuntime.starMinerEnabled ? " · Star" : " · Star-script uit"));
            if (statusOverride != null && !statusOverride.isBlank()) {
                lines.add("Status: " + statusOverride);
            } else {
                lines.add("Status: " + loop.getStatus());
            }
            List<String> dbg;
            boolean bankOpen = false;
            try {
                bankOpen = net.storm.sdk.items.Bank.isOpen();
            } catch (Throwable ignored) {
            }
            if (bankOpen) {
                dbg = java.util.List.of("Bank open — feed-scan uit");
            } else {
                dbg = loop.debugLines();
            }
            if (dbg != null) {
                lines.addAll(dbg);
            }
            BotRuntime.starDebugLines = lines;
        } catch (Throwable ignored) {
        }
    }
}
