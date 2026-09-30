package net.runelite.client.plugins.lonebot;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.lonebot.login.RelogRuntime;
import net.storm.sdk.bot.BotRuntime;

/**
 * Accounts Besturing-balk → BotRuntime master + scripts.
 */
public final class LoneBotBotControl {

    private LoneBotBotControl() {
    }

    public static void start() {
        BotRuntime.lastStartWasResume = BotRuntime.pausedForResume;
        BotRuntime.botStartGeneration++;
        BotRuntime.botEnabled = true;
        BotRuntime.pausedForResume = false;
        String acc = launchedAccountName();
        if (acc != null) {
            AccountSessionTimers.markSessionStart(acc);
        }
        LoopWatchHelper.reset();
        try {
            net.storm.sdk.movement.WorldWalker.cancel();
        } catch (Throwable ignored) {
        }
        try {
            net.storm.sdk.utils.AntiBan.get().startFidgetWorker();
        } catch (Throwable ignored) {
        }
        net.storm.sdk.loop.LoopHost.kickIfStalled();
        net.storm.sdk.movement.MinimapZoomHelper.ensureFullyZoomedOut(true);
        try {
            net.storm.sdk.bot.ActivityLog.setAccount(acc);
            net.storm.sdk.bot.ActivityLog.sessionStart(acc != null ? acc : "_default",
                    ActivitySessionHeaders.build());
            net.storm.sdk.bot.ActivityLog.step("Bot", "Start — " + BotRuntime.activeSkill
                    + (BotRuntime.lastStartWasResume ? " (hervat)" : ""));
        } catch (Throwable ignored) {
        }
        BotRuntime.logConsole("[BotControl] Bot AAN — loop "
                + (net.storm.sdk.loop.LoopHost.isLoopAlive() ? "ok" : "herstart")
                + " skill=" + BotRuntime.activeSkill
                + " star=" + BotRuntime.starMinerEnabled);
    }

    public static void pause() {
        BotRuntime.botEnabled = false;
        BotRuntime.pausedForResume = true;
        try {
            net.storm.sdk.bot.ActivityLog.pause();
        } catch (Throwable ignored) {
        }
        haltBackgroundMotion("Pauze");
    }

    public static void stop() {
        BotRuntime.clearClueResume();
        BotRuntime.clearStarPark();
        BotRuntime.botEnabled = false;
        BotRuntime.pausedForResume = false;
        BotRuntime.nextAccountRequested = false;
        BotRuntime.switchNowRequested = false;
        BotRuntime.geTradeTestEnabled = false;
        try {
            net.storm.sdk.bot.ActivityLog.sessionStop("Stop");
        } catch (Throwable ignored) {
        }
        haltBackgroundMotion("Stop");
        net.storm.sdk.loop.LoopHost.kickIfStalled();
    }

    /** AntiBan bg-workers + walker stoppen — anders camera/muis na Stop. */
    private static void haltBackgroundMotion(String why) {
        try {
            net.storm.sdk.utils.AntiBan.get().stopFidgetWorker();
        } catch (Throwable ignored) {
        }
        try {
            net.storm.sdk.movement.WorldWalker.cancel();
        } catch (Throwable ignored) {
        }
        try {
            net.storm.sdk.movement.MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
        BotRuntime.logConsole("[BotControl] achtergrond stil (" + why + ")");
    }

    public static void stopAndResetTimers(boolean resetTimers) {
        stop();
        if (resetTimers) {
            AccountSessionTimers.clearAll();
            BotRuntime.logConsole("[BotControl] Account-timers gereset bij Stop");
        }
    }

    public static void emergencyStop() {
        stop();
        BotRuntime.clearClueResume();
        BotRuntime.clearStarPark();
        PanelLogoutHelper.clear();
        BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
        BotRuntime.varrockEastBankTestEnabled = false;
        BotRuntime.cityCircleTestEnabled = false;
        BotRuntime.geTradeTestEnabled = false;
        BotRuntime.stopImps2WalkTest();
        BotRuntime.impStatus = "noodstop";
        BotRuntime.cowStatus = "noodstop";
        BotRuntime.wcStatus = "noodstop";
        BotRuntime.fishStatus = "noodstop";
        BotRuntime.starStatus = "noodstop";
        BotRuntime.bankTestStatus = "uit";
        BotRuntime.cityCircleStatus = "uit";
        BotRuntime.geTradeStatus = "uit";
        LoopWatchHelper.reset();
        if (RelogRuntime.relogger != null) {
            RelogRuntime.relogger.resetToIdleFromPanel();
        }
    }

    public static void resetRuntime() {
        BotRuntime.nextAccountRequested = false;
        BotRuntime.switchNowRequested = false;
        BotRuntime.pausedForResume = false;
        PanelLogoutHelper.clear();
        if (!BotRuntime.impKillerEnabled) {
            BotRuntime.impStatus = "uit";
        }
        if (!BotRuntime.cowCombatEnabled) {
            BotRuntime.cowStatus = "uit";
        }
        if (!BotRuntime.woodcuttingEnabled) {
            BotRuntime.wcStatus = "uit";
        }
        if (!BotRuntime.fishingEnabled) {
            BotRuntime.fishStatus = "uit";
        }
        if (!BotRuntime.starMinerEnabled) {
            BotRuntime.starStatus = "uit";
        }
        if (!BotRuntime.giantsKillerEnabled) {
            BotRuntime.giantsStatus = "uit";
        }
        LoopWatchHelper.reset();
        if (RelogRuntime.relogger != null) {
            RelogRuntime.relogger.resetToIdleFromPanel();
        }
    }

    public static void requestSwitchNow() {
        BotRuntime.switchNowRequested = true;
        BotRuntime.logConsole("[BotControl] Switch Now gevraagd");
    }

    public static void syncBotEnabledConfig(ConfigManager cm, boolean on) {
        if (cm == null) {
            return;
        }
        try {
            cm.setConfiguration("lonebot", "botEnabled", on);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sidebar / quick-control: één skill kiezen, config + per-account flags bijwerken.
     */
    public static void selectSkill(BotRuntime.ActiveSkill skill, ConfigManager cm, LoneBotConfig config) {
        BotRuntime.ActiveSkill want = skill != null ? skill : BotRuntime.ActiveSkill.NONE;
        BotRuntime.clearClueResume();
        BotRuntime.setActiveSkill(want);
        syncSkillFlagsToConfig(cm);
        String acc = launchedAccountName();
        if (acc != null) {
            if (want == BotRuntime.ActiveSkill.STAR) {
                ManagedAccountsStore.setPreferredScript(acc, "STAR");
            } else if (want == BotRuntime.ActiveSkill.IMP2) {
                ManagedAccountsStore.setPreferredScript(acc, "IMP2");
            } else if (want == BotRuntime.ActiveSkill.MONK) {
                ManagedAccountsStore.setPreferredScript(acc, "MONK");
            } else if (want == BotRuntime.ActiveSkill.GIANTS) {
                ManagedAccountsStore.setPreferredScript(acc, "GIANTS");
            } else if (want == BotRuntime.ActiveSkill.CLUE) {
                ManagedAccountsStore.setPreferredScript(acc, "CLUE");
            } else if (want == BotRuntime.ActiveSkill.QUEST) {
                ManagedAccountsStore.setPreferredScript(acc, "QUEST");
            } else {
                ManagedAccountsStore.setSkillEnabled(acc,
                        want == BotRuntime.ActiveSkill.IMP,
                        want == BotRuntime.ActiveSkill.COW,
                        want == BotRuntime.ActiveSkill.WOODCUTTING,
                        want == BotRuntime.ActiveSkill.FISHING);
            }
        }
        if (config != null) {
            if (want == BotRuntime.ActiveSkill.WOODCUTTING) {
                LoneBotBootstrapPlugin.applyWcSettings(config);
            } else if (want == BotRuntime.ActiveSkill.FISHING) {
                LoneBotBootstrapPlugin.applyFishSettings(config);
            } else if (want == BotRuntime.ActiveSkill.STAR) {
                LoneBotBootstrapPlugin.applyStarSettings(config);
            } else if (want == BotRuntime.ActiveSkill.IMP) {
                LoneBotBootstrapPlugin.applyImpSettings(config);
            } else if (want == BotRuntime.ActiveSkill.MONK) {
                LoneBotBootstrapPlugin.applyMonkSettings(config);
            } else if (want == BotRuntime.ActiveSkill.GIANTS) {
                LoneBotBootstrapPlugin.applyGiantsSettings(config);
            } else if (want == BotRuntime.ActiveSkill.CLUE) {
                // geen aparte apply-settings; flag via syncSkillFlagsToConfig
            } else if (want == BotRuntime.ActiveSkill.QUEST) {
                LoneBotBootstrapPlugin.applyQuestSettings(config);
            }
        }
        if (want == BotRuntime.ActiveSkill.IMP2) {
            com.lonebot.example.Imps2Plugin.resetLive();
        }
        BotRuntime.logConsole("[Quick] script=" + want);
    }

    /**
     * Legacy: alleen preferredScript → runtime (geen ConfigManager).
     * Voorkeur: {@link #applyAccountSkills(ManagedAccountsStore.ManagedAccount, ConfigManager)}.
     */
    public static void applyPreferredScript(String preferred) {
        if (preferred == null) {
            return;
        }
        switch (preferred.trim().toUpperCase()) {
            case "IMP":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.IMP);
                break;
            case "IMP2":
            case "IMPS2":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.IMP2);
                break;
            case "COW":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.COW);
                break;
            case "MONK":
            case "MONK_KILLER":
            case "MONKKILLER":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.MONK);
                break;
            case "WC":
            case "WOODCUTTER":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.WOODCUTTING);
                break;
            case "FISH":
            case "FISHING":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.FISHING);
                break;
            case "STAR":
            case "STARMINER":
            case "STARS":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.STAR);
                break;
            case "GIANTS":
            case "GIANT":
            case "HILLGIANTS":
            case "HILL_GIANTS":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.GIANTS);
                break;
            case "CLUE":
            case "CLUES":
            case "BEGINNER_CLUE":
            case "BEGINNERCLUE":
            case "TREASURE_TRAIL":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.CLUE);
                break;
            case "QUEST":
            case "QUESTER":
            case "COOKS_ASSISTANT":
            case "COOKS":
            case "GOBLIN_DIPLOMACY":
            case "GOBLIN":
            case "ROMEO_AND_JULIET":
            case "ROMEO":
            case "RUNE_MYSTERIES":
            case "RUNE":
            case "DORICS":
            case "DORICS_QUEST":
            case "DORIC":
            case "VAMPYRE_SLAYER":
            case "VAMPIRE_SLAYER":
            case "VAMPIRE":
            case "TUTORIAL":
            case "TUTORIAL_ISLAND":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.QUEST);
                applyQuestTargetFromPreferred(preferred);
                break;
            case "BANK_TEST":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                BotRuntime.varrockEastBankTestEnabled = true;
                BotRuntime.cityCircleTestEnabled = false;
                break;
            case "CITY_TEST":
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                BotRuntime.cityCircleTestEnabled = true;
                BotRuntime.varrockEastBankTestEnabled = false;
                break;
            case "EXAMPLE":
            case "NONE":
            default:
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                break;
        }
    }

    /** Pas per-account skills toe op BotRuntime (+ optioneel ConfigManager). */
    public static void applyAccountSkills(ManagedAccountsStore.ManagedAccount row, ConfigManager cm) {
        if (row == null) {
            return;
        }
        ManagedAccountsStore.normalizeSkills(row);
        String p = row.preferredScript != null ? row.preferredScript.trim().toUpperCase() : "NONE";
        boolean bank = "BANK_TEST".equals(p);
        boolean city = "CITY_TEST".equals(p);
        if ("STAR".equals(p) || "STARMINER".equals(p) || "STARS".equals(p)) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.STAR);
            BotRuntime.varrockEastBankTestEnabled = false;
            BotRuntime.cityCircleTestEnabled = false;
            syncSkillFlagsToConfig(cm);
            return;
        }
        if ("MONK".equals(p) || "MONK_KILLER".equals(p) || "MONKKILLER".equals(p)) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.MONK);
            BotRuntime.varrockEastBankTestEnabled = false;
            BotRuntime.cityCircleTestEnabled = false;
            syncSkillFlagsToConfig(cm);
            return;
        }
        if ("GIANTS".equals(p) || "GIANT".equals(p) || "HILLGIANTS".equals(p) || "HILL_GIANTS".equals(p)) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.GIANTS);
            BotRuntime.varrockEastBankTestEnabled = false;
            BotRuntime.cityCircleTestEnabled = false;
            syncSkillFlagsToConfig(cm);
            return;
        }
        if ("CLUE".equals(p) || "CLUES".equals(p) || "BEGINNER_CLUE".equals(p)
                || "BEGINNERCLUE".equals(p) || "TREASURE_TRAIL".equals(p)) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.CLUE);
            BotRuntime.varrockEastBankTestEnabled = false;
            BotRuntime.cityCircleTestEnabled = false;
            syncSkillFlagsToConfig(cm);
            return;
        }
        if ("IMP2".equals(p) || "IMPS2".equals(p)) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.IMP2);
            BotRuntime.varrockEastBankTestEnabled = false;
            BotRuntime.cityCircleTestEnabled = false;
            syncSkillFlagsToConfig(cm);
            return;
        }
        if (isQuestPreferred(p)) {
            BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.QUEST);
            applyQuestTargetFromPreferred(p);
            BotRuntime.varrockEastBankTestEnabled = false;
            BotRuntime.cityCircleTestEnabled = false;
            syncSkillFlagsToConfig(cm);
            return;
        }
        applySkillRuntime(row.impKillerEnabled, row.cowCombatEnabled, bank, city,
                row.woodcuttingEnabled, row.fishingEnabled);
        syncSkillFlagsToConfig(cm);
    }

    /**
     * Schrijf exclusieve skill-flags naar config — voorkomt stale fishingEnabled=true
     * terwijl WC actief is (panel las dat terug en zette Fish opnieuw aan).
     */
    public static void syncSkillFlagsToConfig(ConfigManager cm) {
        if (cm == null) {
            return;
        }
        cm.setConfiguration("lonebot", "impKillerEnabled", BotRuntime.impKillerEnabled);
        cm.setConfiguration("lonebot", "cowCombatEnabled", BotRuntime.cowCombatEnabled);
        cm.setConfiguration("lonebot", "monkKillerEnabled", BotRuntime.monkKillerEnabled);
        cm.setConfiguration("lonebot", "woodcuttingEnabled", BotRuntime.woodcuttingEnabled);
        cm.setConfiguration("lonebot", "fishingEnabled", BotRuntime.fishingEnabled);
        cm.setConfiguration("lonebot", "starMinerEnabled", BotRuntime.starMinerEnabled);
        cm.setConfiguration("lonebot", "giantsKillerEnabled", BotRuntime.giantsKillerEnabled);
        cm.setConfiguration("lonebot", "clueEnabled", BotRuntime.clueEnabled);
        cm.setConfiguration("lonebot", "questEnabled", BotRuntime.questEnabled);
    }

    static boolean isQuestPreferred(String p) {
        if (p == null) {
            return false;
        }
        String u = p.trim().toUpperCase();
        return "QUEST".equals(u) || "QUESTER".equals(u)
                || "COOKS_ASSISTANT".equals(u) || "COOKS".equals(u)
                || "GOBLIN_DIPLOMACY".equals(u) || "GOBLIN".equals(u)
                || "ROMEO_AND_JULIET".equals(u) || "ROMEO".equals(u)
                || "RUNE_MYSTERIES".equals(u) || "RUNE".equals(u)
                || "DORICS".equals(u) || "DORICS_QUEST".equals(u) || "DORIC".equals(u)
                || "VAMPYRE_SLAYER".equals(u) || "VAMPIRE_SLAYER".equals(u) || "VAMPIRE".equals(u)
                || "TUTORIAL".equals(u) || "TUTORIAL_ISLAND".equals(u);
    }

    private static void applyQuestTargetFromPreferred(String preferred) {
        String t = com.lonebot.example.quest.QuestBotTarget.fromPreferredScript(preferred).name();
        BotRuntime.questTarget = t;
        com.lonebot.example.QuesterPlugin.target = t;
    }

    private static void applySkillRuntime(boolean imp, boolean cow, boolean bank, boolean city,
                                          boolean wc, boolean fish) {
        BotRuntime.ActiveSkill skill = BotRuntime.ActiveSkill.NONE;
        if (imp) {
            skill = BotRuntime.ActiveSkill.IMP;
        } else if (wc) {
            skill = BotRuntime.ActiveSkill.WOODCUTTING;
        } else if (fish) {
            skill = BotRuntime.ActiveSkill.FISHING;
        } else if (cow) {
            skill = BotRuntime.ActiveSkill.COW;
        }
        BotRuntime.setActiveSkill(skill);
        BotRuntime.varrockEastBankTestEnabled = bank && skill == BotRuntime.ActiveSkill.NONE;
        BotRuntime.cityCircleTestEnabled = city && skill == BotRuntime.ActiveSkill.NONE && !bank;
    }

    /** Huidige launched account display name, of null. */
    public static String launchedAccountName() {
        String n = System.getProperty("lonebot.account");
        if (n == null || n.trim().isEmpty()) {
            return null;
        }
        return n.trim();
    }
}
