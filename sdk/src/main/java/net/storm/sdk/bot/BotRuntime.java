package net.storm.sdk.bot;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.TilePath;

/**
 * Gedeelde runtime-state (overlay / panel / hot-reload plugins).
 * <p>
 * <b>Skill-scripts:</b> max één actief via {@link #setActiveSkill(ActiveSkill)}.
 * Booleans ({@code woodcuttingEnabled}, …) zijn afgeleiden — nooit twee tegelijk {@code true}.
 * {@link net.storm.sdk.loop.LoopHost} tickt skill-plugins alleen als hun flag aan staat.
 */
public final class BotRuntime {

    private BotRuntime() {
    }

    /** Welk skill-script mag tickken (exclusief). */
    public enum ActiveSkill {
        NONE,
        WOODCUTTING,
        FISHING,
        IMP,
        IMP2,
        COW,
        MONK,
        STAR,
        GIANTS,
        CLUE,
        QUEST
    }

    public static volatile ActiveSkill activeSkill = ActiveSkill.NONE;

    public static volatile boolean cowCombatEnabled = false;
    public static volatile String cowStatus = "uit";
    public static volatile int cowLoopTicks = 0;

    public static volatile boolean monkKillerEnabled = false;
    public static volatile String monkStatus = "uit";
    public static volatile String monkPluginVersion = "?";

    public static volatile boolean giantsKillerEnabled = false;
    public static volatile String giantsStatus = "uit";
    public static volatile String giantsPluginVersion = "?";
    public static volatile DebugPaint giantsDebugPaint;
    public static volatile boolean giantsDebugOverlayEnabled = true;
    /** Live Monk settings (gesync vanaf LoneBotConfig / panel). */
    public static volatile int monkEatPercent = 50;
    public static volatile int monkCriticalHp = 3;
    public static volatile int monkFoodAmount = 10;
    /** Hoeveel cabbage per pluk-sessie. */
    public static volatile int monkCabbagePickAmount = 5;
    /** Cabbage van het veld plukken (aan/uit). */
    public static volatile boolean monkCabbagePickEnabled = true;
    /** AUTO | CABBAGE | BANK */
    public static volatile String monkFoodSource = "AUTO";
    public static volatile boolean monkBankWhenNoFood = true;
    public static volatile boolean monkLogoutWhenNoFood = false;
    /** Monastery: Talk-to monk voor heal i.p.v. meteen eten. */
    public static volatile boolean monkHealViaTalk = true;
    public static volatile String menuProbeSummary = "nog niet";
    public static volatile String mouseSummary = "-";
    public static volatile String debugSummary = "-";
    public static volatile String loadedBotClass = "builtin";

    public static volatile boolean varrockEastBankTestEnabled = false;
    public static volatile String bankTestStatus = "uit";

    public static volatile boolean cityCircleTestEnabled = false;
    public static volatile String cityCircleStatus = "uit";

    /** Test-tab: loop naar GE en koop/verkoop één item. */
    public static volatile boolean geTradeTestEnabled = false;
    public static volatile String geTradeStatus = "uit";
    public static volatile boolean geTradeBuy = true;
    public static volatile String geTradeItem = "";
    public static volatile int geTradeQty = 1;
    public static volatile int geTradePrice = 1;

    /** Imps2 walk-test (niet de oude Imp Killer). */
    public static volatile boolean imps2Enabled = false;
    public static volatile String imps2Status = "uit";
    public static volatile String imps2PluginVersion = "?";

    public static volatile boolean impKillerEnabled = false;
    public static volatile String impStatus = "uit";
    /** Plugin-side ImpKiller versie (hot-reload) — niet LoneBot client VERSION. */
    public static volatile String impPluginVersion = "?";
    /** Imp debug-panel paint (gezet door ImpKillerPlugin.startUp; hot-reload vervangt dit). */
    public static volatile DebugPaint impDebugPaint;
    public static volatile boolean impDebugOverlayEnabled = true;

    public static volatile boolean fishingEnabled = false;
    public static volatile String fishStatus = "uit";
    public static volatile String fishPluginVersion = "?";
    public static volatile DebugPaint fishDebugPaint;
    public static volatile java.util.List<String> fishDebugLines;
    public static volatile boolean fishDebugOverlayEnabled = true;
    public static volatile boolean fishDebugOverlayMinimized = false;

    public static volatile boolean starMinerEnabled = false;
    public static volatile String starStatus = "uit";
    public static volatile String starPluginVersion = "?";
    /** Doelwereld tijdens Star-hop — LoopHost mag travel niet vóór hop zetten. */
    public static volatile int starHopWorld = 0;
    public static volatile java.util.List<String> starDebugLines;
    /** Live feed-regels voor Star-tab (wereld + locatie, nieuwste eerst). */
    public static volatile java.util.List<String> starFeedLines = java.util.Collections.emptyList();
    /** Parallel aan {@link #starFeedLines}: mag deze account erheen? */
    public static volatile java.util.List<Boolean> starFeedVisitOk = java.util.Collections.emptyList();
    public static volatile int starFeedCount = 0;
    /** Sessieteller: sterren die we liv hebben gezien (aanwezig bij bezoek), 1× per ster. */
    public static volatile int starSuccessCount = 0;
    public static volatile boolean starDebugOverlayEnabled = true;

    public static volatile boolean clueEnabled = false;
    /** Reward casket → bank i.p.v. Open + loot. */
    public static volatile boolean clueBankCasket = false;
    /** Ontbrekende kit/Charlie-items via GE kopen (niet Reldo-device). */
    public static volatile boolean clueGeBuyMissing = true;
    /**
     * Algemene Dialog.autoContinue (Space/invoke). Clue gebruikt force=true en negeert dit.
     */
    public static volatile boolean dialogAutoContinue = true;
    /** Skill om te hervatten na Clue-interrupt; NONE = handmatig Clue / geen resume. */
    public static volatile ActiveSkill clueResumeSkill = ActiveSkill.NONE;
    /** Per-skill “Clues doen” (gesync vanaf LoneBotConfig). Default uit. */
    public static volatile boolean wcClueSolver = false;
    public static volatile boolean fishClueSolver = false;
    public static volatile boolean starClueSolver = false;
    public static volatile boolean impsClueSolver = false;
    public static volatile boolean imps2ClueSolver = false;
    public static volatile boolean giantsClueSolver = false;
    public static volatile boolean monkClueSolver = false;
    public static volatile boolean cowClueSolver = false;
    public static volatile String clueStatus = "uit";
    public static volatile String cluePluginVersion = "?";

    public static volatile boolean questEnabled = false;
    public static volatile String questStatus = "uit";
    public static volatile String questPluginVersion = "?";
    /** {@code ROTATION} or a specific F2P target name. */
    public static volatile String questTarget = "ROTATION";
    public static volatile boolean questRotationEnabled = true;
    public static volatile boolean questSkipLowLevel = true;

    public static volatile boolean woodcuttingEnabled = false;
    public static volatile String wcStatus = "uit";
    public static volatile String wcPluginVersion = "?";
    /** WC canvas paint (hot-reload). */
    public static volatile DebugPaint wcDebugPaint;
    /**
     * Live WC debug-regels (gezet door de tickende WoodcutterPlugin).
     * Overlay moet DIT tonen — niet een stale {@link #wcDebugPaint} van een dode hot-reload instance.
     */
    public static volatile java.util.List<String> wcDebugLines;
    public static volatile boolean wcDebugOverlayEnabled = true;
    public static volatile boolean wcDebugOverlayMinimized = false;
    /** Laatste volledige LoopHost-cycle (ms). Overlay: loop dood als dit te oud is. */
    public static volatile long loopLastTickMs;

    /** Tijdens lange muis/walk-sleep: LoopHost mag de thread niet als stall herstarten. */
    public static void heartbeat() {
        loopLastTickMs = System.currentTimeMillis();
    }

    public static volatile boolean cowDebugOverlayEnabled = true;
    public static volatile DebugPaint cowDebugPaint;

    /** Master bot aan/uit (Accounts Besturing-balk). */
    public static volatile boolean botEnabled = false;
    /** Na Pauze: Start hervat zonder scripts te clearen. */
    public static volatile boolean pausedForResume = false;
    /** Laatste Start was hervatting na Pauze (niet na Stop). */
    public static volatile boolean lastStartWasResume = false;
    /** Stijgt bij elke Start-klik — scripts zien Stop→Start ook als de loop nog niet UIT was. */
    public static volatile long botStartGeneration = 0;
    /** Request: switch naar volgend rotatie-account (stub tot switcher). */
    public static volatile boolean nextAccountRequested = false;
    /** Request: next skill in rotatie (CombatBot switchNow). */
    public static volatile boolean switchNowRequested = false;

    private static long lastExclusiveLogMs;

    /**
     * Zet precies één skill aan (of {@link ActiveSkill#NONE}).
     * Enige bedoelde API om skill-scripts te wisselen — booleans volgen hieruit.
     */
    public static synchronized void setActiveSkill(ActiveSkill skill) {
        ActiveSkill want = skill != null ? skill : ActiveSkill.NONE;
        ActiveSkill prev = activeSkill;
        activeSkill = want;

        woodcuttingEnabled = want == ActiveSkill.WOODCUTTING;
        fishingEnabled = want == ActiveSkill.FISHING;
        starMinerEnabled = want == ActiveSkill.STAR;
        giantsKillerEnabled = want == ActiveSkill.GIANTS;
        impKillerEnabled = want == ActiveSkill.IMP;
        imps2Enabled = want == ActiveSkill.IMP2;
        cowCombatEnabled = want == ActiveSkill.COW;
        monkKillerEnabled = want == ActiveSkill.MONK;
        clueEnabled = want == ActiveSkill.CLUE;
        questEnabled = want == ActiveSkill.QUEST;

        wcStatus = woodcuttingEnabled ? "aan" : "uit";
        fishStatus = fishingEnabled ? "aan" : "uit";
        starStatus = starMinerEnabled ? "aan" : "uit";
        giantsStatus = giantsKillerEnabled ? "aan" : "uit";
        impStatus = impKillerEnabled ? "aan" : "uit";
        imps2Status = imps2Enabled ? "aan" : "uit";
        cowStatus = cowCombatEnabled ? "aan" : "uit";
        monkStatus = monkKillerEnabled ? "aan" : "uit";
        clueStatus = clueEnabled ? "aan" : "uit";
        questStatus = questEnabled ? "aan" : "uit";

        if (prev != want) {
            logConsole("[Skills] active=" + want + (prev != ActiveSkill.NONE ? " (was " + prev + ")" : ""));
            if (botEnabled && want != ActiveSkill.NONE) {
                try {
                    ActivityLog.step("Skill", prev + " → " + want);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static void stopImps2WalkTest() {
        if (activeSkill == ActiveSkill.IMP2) {
            setActiveSkill(ActiveSkill.NONE);
            return;
        }
        imps2Enabled = false;
        imps2Status = "uit";
    }

    /**
     * Herstel exclusiviteit als booleans ooit direct gezet zijn (panel/config race).
     * Prioriteit bij conflict: WC → Fish → Imp → Cow.
     */
    public static synchronized void enforceExclusiveSkills() {
        int n = (woodcuttingEnabled ? 1 : 0)
                + (fishingEnabled ? 1 : 0)
                + (starMinerEnabled ? 1 : 0)
                + (giantsKillerEnabled ? 1 : 0)
                + (impKillerEnabled ? 1 : 0)
                + (imps2Enabled ? 1 : 0)
                + (cowCombatEnabled ? 1 : 0)
                + (monkKillerEnabled ? 1 : 0)
                + (clueEnabled ? 1 : 0)
                + (questEnabled ? 1 : 0);
        ActiveSkill want = ActiveSkill.NONE;
        if (n == 0) {
            want = ActiveSkill.NONE;
        } else if (n == 1) {
            if (woodcuttingEnabled) {
                want = ActiveSkill.WOODCUTTING;
            } else if (fishingEnabled) {
                want = ActiveSkill.FISHING;
            } else if (starMinerEnabled) {
                want = ActiveSkill.STAR;
            } else if (giantsKillerEnabled) {
                want = ActiveSkill.GIANTS;
            } else if (impKillerEnabled) {
                want = ActiveSkill.IMP;
            } else if (imps2Enabled) {
                want = ActiveSkill.IMP2;
            } else if (monkKillerEnabled) {
                want = ActiveSkill.MONK;
            } else if (clueEnabled) {
                want = ActiveSkill.CLUE;
            } else if (questEnabled) {
                want = ActiveSkill.QUEST;
            } else {
                want = ActiveSkill.COW;
            }
        } else {
            // Conflict — vaste prioriteit
            if (woodcuttingEnabled) {
                want = ActiveSkill.WOODCUTTING;
            } else if (fishingEnabled) {
                want = ActiveSkill.FISHING;
            } else if (starMinerEnabled) {
                want = ActiveSkill.STAR;
            } else if (giantsKillerEnabled) {
                want = ActiveSkill.GIANTS;
            } else if (impKillerEnabled) {
                want = ActiveSkill.IMP;
            } else if (imps2Enabled) {
                want = ActiveSkill.IMP2;
            } else if (monkKillerEnabled) {
                want = ActiveSkill.MONK;
            } else if (clueEnabled) {
                want = ActiveSkill.CLUE;
            } else if (questEnabled) {
                want = ActiveSkill.QUEST;
            } else {
                want = ActiveSkill.COW;
            }
            logExclusive(want.name(),
                    fishingEnabled && want != ActiveSkill.FISHING,
                    impKillerEnabled && want != ActiveSkill.IMP,
                    cowCombatEnabled && want != ActiveSkill.COW,
                    woodcuttingEnabled && want != ActiveSkill.WOODCUTTING);
        }
        setActiveSkill(want);
    }

    /**
     * LoopHost: mag deze plugin-tick een skill-script uitvoeren?
     * Non-skill plugins (RandomEvent, tests) altijd {@code true}.
     */
    public static boolean isSkillPluginAllowed(String simpleClassName) {
        if (simpleClassName == null) {
            return true;
        }
        switch (simpleClassName) {
            case "WoodcutterPlugin":
                return woodcuttingEnabled && activeSkill == ActiveSkill.WOODCUTTING;
            case "FishingPlugin":
                return fishingEnabled && activeSkill == ActiveSkill.FISHING;
            case "StarMinerPlugin":
                return (starMinerEnabled && activeSkill == ActiveSkill.STAR)
                        || (starParkActive && botEnabled);
            case "GiantsPlugin":
                return giantsKillerEnabled && activeSkill == ActiveSkill.GIANTS;
            case "ImpKillerPlugin":
                return impKillerEnabled && activeSkill == ActiveSkill.IMP;
            case "Imps2Plugin":
                return imps2Enabled && activeSkill == ActiveSkill.IMP2;
            case "CowCombatPlugin":
                return cowCombatEnabled && activeSkill == ActiveSkill.COW;
            case "MonkKillerPlugin":
                return monkKillerEnabled && activeSkill == ActiveSkill.MONK;
            case "CluePlugin":
                return clueEnabled && activeSkill == ActiveSkill.CLUE;
            case "QuesterPlugin":
                return questEnabled && activeSkill == ActiveSkill.QUEST;
            default:
                return true;
        }
    }

    /** Skill-script (niet RandomEvent/tests) — tijdens break-wacht overslaan. */
    public static boolean isNamedSkillPlugin(String simpleClassName) {
        if (simpleClassName == null) {
            return false;
        }
        switch (simpleClassName) {
            case "WoodcutterPlugin":
            case "FishingPlugin":
            case "StarMinerPlugin":
            case "GiantsPlugin":
            case "ImpKillerPlugin":
            case "Imps2Plugin":
            case "CowCombatPlugin":
            case "MonkKillerPlugin":
            case "CluePlugin":
            case "QuesterPlugin":
                return true;
            default:
                return false;
        }
    }

    private static void logExclusive(String keep, boolean fish, boolean imp, boolean cow, boolean wc) {
        long now = System.currentTimeMillis();
        if (now - lastExclusiveLogMs < 8_000L) {
            return;
        }
        lastExclusiveLogMs = now;
        StringBuilder sb = new StringBuilder("[Skills] conflict → ").append(keep).append(" (force-uit:");
        if (wc) {
            sb.append(" WC");
        }
        if (fish) {
            sb.append(" Fish");
        }
        if (imp) {
            sb.append(" Imp");
        }
        if (cow) {
            sb.append(" Cow");
        }
        if (starMinerEnabled && !"STAR".equals(keep)) {
            sb.append(" Star");
        }
        if (giantsKillerEnabled && !"GIANTS".equals(keep)) {
            sb.append(" Giants");
        }
        if (clueEnabled && !"CLUE".equals(keep)) {
            sb.append(" Clue");
        }
        if (questEnabled && !"QUEST".equals(keep)) {
            sb.append(" Quest");
        }
        sb.append(")");
        logConsole(sb.toString());
    }

    /** Panel: uitloggen gepland (wacht combat/loot → Game.logout). */
    public static volatile boolean panelLogoutRequested = false;
    public static volatile long panelLogoutRequestedAtMs = 0L;
    /** Same-account re-log bezig (LoopHost slaat scripts over). */
    public static volatile boolean relogFlowActive = false;
    /** Timer klaar: wacht veilig moment; skill-scripts pauzeren nieuwe acties. */
    public static volatile boolean breakPending = false;
    public static volatile String lastRelogStatus = "";
    public static volatile boolean relogInfoEnabled = false;
    public static volatile long relogSecondsUntilLogout = 0L;
    public static volatile long relogPauseSeconds = 0L;
    public static volatile boolean relogInPause = false;
    /** idle / wait / logout / pause / login */
    public static volatile String relogPhase = "";
    /** Launcher-regel, bv. {@code break 12:34}. */
    public static volatile String relogLauncherLabel = "";
    /** LoopWatch overlay/console status. */
    public static volatile String loopWatchStatus = "-";

    public static volatile boolean canvasDebugEnabled = false;
    /** Storm Developer Tools → Debug Logging. */
    public static volatile boolean debugLogging = false;
    /** Muis-kruisje / target — los van status overlay. */
    public static volatile boolean mouseDebugEnabled = false;

    /** Random events: laatste actie (overlay/console). */
    public static volatile String randomEventStatus = "-";

    public static volatile TilePath debugPath;
    public static volatile WorldPoint debugTarget;

    /** Console-tab in LoneBot panel (gezet door panel startUp). */
    public static volatile java.util.function.Consumer<String> consoleSink;


    /** Wis Clue-resume (Stop / noodstop / handmatige skill-keuze). */
    public static void clearClueResume() {
        clueResumeSkill = ActiveSkill.NONE;
    }

    /** True als de actieve skill “Clues doen” aan heeft. */
    public static boolean isClueSolverEnabledFor(ActiveSkill skill) {
        if (skill == null) {
            return false;
        }
        switch (skill) {
            case WOODCUTTING:
                return wcClueSolver;
            case FISHING:
                return fishClueSolver;
            case STAR:
                return starClueSolver;
            case IMP:
                return impsClueSolver;
            case IMP2:
                return imps2ClueSolver;
            case GIANTS:
                return giantsClueSolver;
            case MONK:
                return monkClueSolver;
            case COW:
                return cowClueSolver;
            default:
                return false;
        }
    }

    /**
     * Handoff skill → CLUE zonder preferredScript te wijzigen.
     * @return true als er gewisseld is
     */
    public static synchronized boolean interruptForClue() {
        ActiveSkill cur = activeSkill;
        if (cur == ActiveSkill.NONE || cur == ActiveSkill.CLUE || cur == ActiveSkill.QUEST) {
            return false;
        }
        if (!isClueSolverEnabledFor(cur)) {
            return false;
        }
        clueResumeSkill = cur;
        setActiveSkill(ActiveSkill.CLUE);
        logConsole("[Clue/Handoff] " + cur + " → CLUE");
        try {
            ActivityLog.step("Clue", cur + " → CLUE");
        } catch (Throwable ignored) {
        }
        return true;
    }

    /**
     * Na Clue-idle: terug naar skill die interruptte. No-op als handmatig Clue (resume=NONE).
     * @return true als hervat
     */
    public static synchronized boolean resumeAfterClueIfNeeded() {
        ActiveSkill resume = clueResumeSkill;
        if (resume == null || resume == ActiveSkill.NONE) {
            return false;
        }
        if (activeSkill != ActiveSkill.CLUE) {
            return false;
        }
        clueResumeSkill = ActiveSkill.NONE;
        setActiveSkill(resume);
        logConsole("[Clue/Handoff] CLUE → " + resume);
        try {
            ActivityLog.step("Clue", "CLUE → " + resume);
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static volatile boolean starParkActive = false;
    public static volatile ActiveSkill starParkWaitSkill = ActiveSkill.NONE;
    public static volatile long starParkResumeGen = 0;

    /**
     * Geen minebare ster: Star pauzeert, andere skill tot de feed een minebare tier heeft.
     */
    public static synchronized boolean parkStarForWaitSkill(ActiveSkill wait) {
        if (!botEnabled || wait == null || wait == ActiveSkill.NONE || wait == ActiveSkill.STAR
                || wait == ActiveSkill.CLUE || wait == ActiveSkill.QUEST) {
            return false;
        }
        starParkActive = true;
        starParkWaitSkill = wait;
        setActiveSkill(wait);
        logConsole("[Star/park] STAR → " + wait + " (tot minebare ster)");
        try {
            ActivityLog.step("Star", "park → " + wait);
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static synchronized boolean resumeStarFromPark() {
        if (!starParkActive) {
            return false;
        }
        ActiveSkill wait = starParkWaitSkill;
        starParkActive = false;
        starParkWaitSkill = ActiveSkill.NONE;
        starParkResumeGen++;
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        try {
            net.storm.sdk.movement.WorldWalker.cancel();
        } catch (Throwable ignored) {
        }
        setActiveSkill(ActiveSkill.STAR);
        logConsole("[Star/park] " + (wait != null ? wait : "?") + " → STAR (gear prep)");
        try {
            ActivityLog.step("Star", (wait != null ? wait : "?") + " → STAR");
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static synchronized void clearStarPark() {
        starParkActive = false;
        starParkWaitSkill = ActiveSkill.NONE;
    }

    public static void logConsole(String msg) {
        if (msg == null || msg.isEmpty()) {
            return;
        }
        java.util.function.Consumer<String> sink = consoleSink;
        if (sink != null) {
            try {
                sink.accept(msg);
            } catch (Throwable ignored) {
            }
        }
    }

    private static volatile String scriptReloadRequested;
    private static volatile long lastScriptReloadRequestMs;

    /** Script vraagt ↻-reload (bv. Star-hop vast). Bootstrap voert het uit. */
    public static void requestScriptReload(String scriptId) {
        if (scriptId == null || scriptId.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (scriptId.equals(scriptReloadRequested) && now - lastScriptReloadRequestMs < 5_000L) {
            return;
        }
        lastScriptReloadRequestMs = now;
        scriptReloadRequested = scriptId.trim();
        logConsole("[Scripts] reload gevraagd: " + scriptReloadRequested);
    }

    public static String takeScriptReloadRequest() {
        String key = scriptReloadRequested;
        scriptReloadRequested = null;
        return key;
    }

    /**
     * Plugin-owned overlay content (Graphics2D). Client overlay shell delegeert hierheen —
     * zo blijft Imp paint/versie hot-reloadbaar zonder client-restart.
     */
    @FunctionalInterface
    public interface DebugPaint {
        java.awt.Dimension render(java.awt.Graphics2D g);
    }
}
