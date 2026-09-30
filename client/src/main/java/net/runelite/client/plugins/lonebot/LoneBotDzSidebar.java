package net.runelite.client.plugins.lonebot;

import com.lonebot.example.giants.GiantsTypes;
import com.lonebot.example.imps.ImpsTypes;
import com.lonebot.example.quest.QuestBotTarget;
import net.runelite.client.config.ConfigManager;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.movement.MinimapZoomHelper;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.WalkClickSettings;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.utils.AntiBanSettings;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Client-sidebar: Start/Pauze/Stop + DZ/RuneLite-config. Control-venster blijft tabs.
 */
final class LoneBotDzSidebar extends JPanel {

    private static final String[] SCRIPTS = {
            "None", "Woodcutting", "Fishing", "Star Miner", "Giants", "Beginner Clue",
            "Quest Bot", "Imp Killer", "Cow Combat", "Monk Killer"
    };

    private final ConfigManager cm;
    private final LoneBotConfig config;
    private final AtomicBoolean suppress = new AtomicBoolean(false);
    private final JComboBox<String> scriptCombo;
    private final JButton btnStart;
    private final JButton btnPause;
    private final JButton btnStop;
    private final JLabel statusLbl;
    private final Timer syncTimer;
    private final LoneBotDzLook.Section woodcutSec;
    private final LoneBotDzLook.Section fishingSec;
    private final LoneBotDzLook.Section starSec;
    private final LoneBotDzLook.Section giantsSec;
    private final LoneBotDzLook.Section questSec;
    private final LoneBotDzLook.Section impsSec;
    private final LoneBotDzLook.Section combatSec;
    private LoneBotDzLook.Section clueSec;

    LoneBotDzSidebar(ConfigManager configManager, LoneBotConfig config,
                     Runnable openControlWindow, Consumer<BotRuntime.ActiveSkill> onScript) {
        this.cm = configManager;
        this.config = config;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(LoneBotDzLook.BG);
        setBorder(new EmptyBorder(0, 0, 0, 0));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(true);
        north.setBackground(LoneBotDzLook.BG);

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(true);
        header.setBackground(LoneBotDzLook.BG);
        header.setBorder(new EmptyBorder(8, 10, 4, 10));
        header.setAlignmentX(LEFT_ALIGNMENT);
        JLabel title = new JLabel("LoneBot");
        title.setForeground(LoneBotDzLook.TEXT);
        title.setFont(LoneBotDzLook.FONT_TITLE);
        JButton pop = new JButton("📐");
        pop.setFocusPainted(false);
        pop.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        pop.setOpaque(false);
        pop.setContentAreaFilled(false);
        pop.setForeground(LoneBotDzLook.MUTED);
        pop.setToolTipText("Open Control-venster (tabs blijven hetzelfde)");
        pop.addActionListener(e -> {
            if (openControlWindow != null) {
                openControlWindow.run();
            }
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(pop);
        header.add(title, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        north.add(header);

        btnStart = LoneBotDzLook.actionBtn("▶ Start", new Color(32, 120, 62));
        btnPause = LoneBotDzLook.actionBtn("⏸ Pauze", new Color(130, 95, 28));
        btnStop = LoneBotDzLook.actionBtn("■ Stop", new Color(130, 42, 48));
        btnStart.setToolTipText("Hervat na pauze, of start opnieuw na Stop");
        btnPause.setToolTipText("Pauzeren — Start gaat verder waar je was");
        btnStop.setToolTipText("Stoppen — Start begint opnieuw");

        JPanel btns = new JPanel(new GridLayout(1, 3, 4, 0));
        btns.setOpaque(true);
        btns.setBackground(LoneBotDzLook.BG);
        btns.setBorder(new EmptyBorder(2, 8, 4, 8));
        btns.setAlignmentX(LEFT_ALIGNMENT);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btns.add(btnStart);
        btns.add(btnPause);
        btns.add(btnStop);
        north.add(btns);

        LoneBotBreakBar breakBar = LoneBotBreakBar.full(cm, config);
        north.add(breakBar);

        statusLbl = new JLabel("●  Uit");
        statusLbl.setForeground(LoneBotDzLook.MUTED);
        statusLbl.setFont(LoneBotDzLook.FONT_ITEM);
        statusLbl.setBorder(new EmptyBorder(0, 10, 6, 10));
        statusLbl.setAlignmentX(LEFT_ALIGNMENT);
        north.add(statusLbl);

        btnStart.addActionListener(e -> {
            applyScriptFromCombo(onScript);
            LoneBotBotControl.start();
            LoneBotBotControl.syncBotEnabledConfig(cm, true);
            BotRuntime.logConsole("[DZ] Start");
            refreshButtons();
        });
        btnPause.addActionListener(e -> {
            LoneBotBotControl.pause();
            LoneBotBotControl.syncBotEnabledConfig(cm, false);
            BotRuntime.logConsole("[DZ] Pauze");
            refreshButtons();
        });
        btnStop.addActionListener(e -> {
            boolean resetTimers = config == null || config.resetAccountTimersOnStop();
            LoneBotBotControl.stopAndResetTimers(resetTimers);
            LoneBotBotControl.syncBotEnabledConfig(cm, false);
            BotRuntime.logConsole("[DZ] Stop");
            refreshButtons();
        });

        LoneBotDzLook.TrackWidthColumn col = new LoneBotDzLook.TrackWidthColumn();

        Runnable wc = LoneBotBootstrapPlugin::applyWcSettingsFromUi;
        Runnable fish = LoneBotBootstrapPlugin::applyFishSettingsFromUi;
        Runnable star = LoneBotBootstrapPlugin::applyStarSettingsFromUi;
        Runnable giants = LoneBotBootstrapPlugin::applyGiantsSettingsFromUi;
        Runnable quest = LoneBotBootstrapPlugin::applyQuestSettingsFromUi;
        Runnable imp = LoneBotBootstrapPlugin::applyImpSettingsFromUi;
        Runnable walk = this::applyWalkLive;
        Runnable ab = this::applyAntiBanLive;

        LoneBotDzLook.Section main = LoneBotDzLook.section("LoneBot Config", true);
        LoneBotDzBind m = new LoneBotDzBind(cm, main);
        m.bool("Display Overlay", "statusOverlay", config.statusOverlay(), null);
        m.bool("Verberg null objecten (Alt)", "devObjectIdFilterHideUnnamed",
                config.devObjectIdFilterHideUnnamed(), null);
        JCheckBox cbInfo = LoneBotDzLook.box(
                config.fishDebugOverlay() || config.wcDebugOverlay() || config.impDebugOverlay()
                        || config.starDebugOverlay() || config.giantsDebugOverlay());
        cbInfo.setToolTipText("Grond/zones/pad van scripts. Status-panel blijft via Display Overlay.");
        cbInfo.addActionListener(e -> {
            boolean on = cbInfo.isSelected();
            cm.setConfiguration("lonebot", "fishDebugOverlay", on);
            cm.setConfiguration("lonebot", "wcDebugOverlay", on);
            cm.setConfiguration("lonebot", "impDebugOverlay", on);
            cm.setConfiguration("lonebot", "starDebugOverlay", on);
            cm.setConfiguration("lonebot", "giantsDebugOverlay", on);
            cm.setConfiguration("lonebot", "cowDebugOverlay", on);
            cm.setConfiguration("lonebot", "showImpsHuntOverlay", on);
            cm.setConfiguration("lonebot", "showWcOverlay", on);
            cm.setConfiguration("lonebot", "showFishingAreaOverlay", on);
            cm.setConfiguration("lonebot", "showCombatAreaOverlay", on);
            if (!on) {
                cm.setConfiguration("lonebot", "impsShowRallyRadius", false);
                cm.setConfiguration("lonebot", "impsShowGoblinCoinOverlay", false);
            }
            BotRuntime.impDebugOverlayEnabled = on;
            BotRuntime.wcDebugOverlayEnabled = on;
            BotRuntime.fishDebugOverlayEnabled = on;
            BotRuntime.starDebugOverlayEnabled = on;
            BotRuntime.giantsDebugOverlayEnabled = on;
            BotRuntime.cowDebugOverlayEnabled = on;
        });
        main.addItem(LoneBotDzLook.itemRow("Script overlays", cbInfo));

        scriptCombo = LoneBotDzLook.combo(SCRIPTS);
        scriptCombo.setSelectedItem(scriptLabel(BotRuntime.activeSkill));
        scriptCombo.addActionListener(e -> {
            if (suppress.get()) {
                return;
            }
            applyScriptFromCombo(onScript);
        });
        main.addItem(LoneBotDzLook.itemRow("Script", scriptCombo));
        m.bool("Reset timers bij stop", "resetAccountTimersOnStop", config.resetAccountTimersOnStop(), null);
        m.bool("LoopWatch herstel", "loopWatchRecoveryEnabled", config.loopWatchRecoveryEnabled(), null);
        m.integer("LoopWatch na (sec)", "loopWatchTriggerSec", config.loopWatchTriggerSec(), 45, 600, null);
        m.integer("LoopWatch logout (sec)", "loopWatchLogoutSec", config.loopWatchLogoutSec(), 60, 900, null);
        m.bool("Auto-continue dialog", "dialogAutoContinue", config.dialogAutoContinue(), () -> {
            BotRuntime.dialogAutoContinue = Boolean.parseBoolean(
                    String.valueOf(cm.getConfiguration("lonebot", "dialogAutoContinue")));
            BotRuntime.logConsole("[Dialog] auto-continue "
                    + (BotRuntime.dialogAutoContinue ? "AAN" : "UIT")
                    + " (Clue blijft force)");
        });
        col.add(main.root);

        woodcutSec = LoneBotDzLook.section("Woodcutting Config", BotRuntime.woodcuttingEnabled);
        LoneBotDzBind w = new LoneBotDzBind(cm, woodcutSec);
        JCheckBox cbBestTree = LoneBotDzLook.box(!config.wcUseSpecificTree());
        JCheckBox cbSpecTree = LoneBotDzLook.box(config.wcUseSpecificTree());
        LoneBotConfigUiSync.boolInverted(cbBestTree, "wcUseSpecificTree");
        LoneBotConfigUiSync.bool(cbSpecTree, "wcUseSpecificTree");
        cbBestTree.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            if (cbBestTree.isSelected()) {
                cbSpecTree.setSelected(false);
            } else if (!cbSpecTree.isSelected()) {
                cbSpecTree.setSelected(true);
            }
            cm.setConfiguration("lonebot", "wcUseSpecificTree", cbSpecTree.isSelected());
            wc.run();
        });
        cbSpecTree.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            if (cbSpecTree.isSelected()) {
                cbBestTree.setSelected(false);
            } else if (!cbBestTree.isSelected()) {
                cbBestTree.setSelected(true);
            }
            cm.setConfiguration("lonebot", "wcUseSpecificTree", cbSpecTree.isSelected());
            wc.run();
        });
        woodcutSec.addItem(LoneBotDzLook.itemRow("Beste boom (niveau)", cbBestTree));
        woodcutSec.addItem(LoneBotDzLook.itemRow("Specifieke boom", cbSpecTree));
        w.text("Boom naam", "wcTreeName", nz(config.wcTreeName(), "Willow"), wc);
        w.combo("Location", "wcLocation",
                com.lonebot.example.woodcutter.WcCenters.presetNames(config.wcCenters()),
                config.wcLocation(), wc);
        // Bank / Firemaking / Drop (banken was eerder “beide toggles uit”)
        JComboBox<LoneBotConfig.WcLogMode> logMode =
                LoneBotDzLook.comboOf(LoneBotConfig.WcLogMode.values(),
                        LoneBotConfig.WcLogMode.fromFlags(config.wcFiremaking(), config.wcDropLogs()));
        LoneBotConfigUiSync.wcLogModeCombo(logMode);
        logMode.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            LoneBotConfig.WcLogMode chosen = (LoneBotConfig.WcLogMode) logMode.getSelectedItem();
            if (chosen != null) {
                LoneBotBootstrapPlugin.applyWcLogMode(chosen);
            }
        });
        woodcutSec.addItem(LoneBotDzLook.itemRow("Logs (volle inv)", logMode));
        w.bool("Forestry events", "wcForestryEvents", config.wcForestryEvents(), wc);
        w.bool("Bird nests", "wcBirdNests", config.wcBirdNests(), wc);
        w.bool("Clues doen (geode/nest/bottle → solver)", "wcClueSolver", config.wcClueSolver(), wc);
        w.bool("Axe via GE (uit)", "wcGeAxeRestockEnabled", config.wcGeAxeRestockEnabled(), wc);
        w.bool("Bank Lumbridge", "wcBankLumbridge", config.wcBankLumbridge(), wc);
        w.bool("Bank Draynor", "wcBankDraynor", config.wcBankDraynor(), wc);
        w.integer("Delay min (ms)", "wcInteractDelayMin", config.wcInteractDelayMin(), 0, 5000, wc);
        w.integer("Delay max (ms)", "wcInteractDelayMax", config.wcInteractDelayMax(), 0, 10000, wc);
        w.bool("Show Overlay", "wcDebugOverlay", config.wcDebugOverlay(), () -> {
                cm.setConfiguration("lonebot", "showWcOverlay", config.wcDebugOverlay());
                BotRuntime.wcDebugOverlayEnabled = config.wcDebugOverlay();
        });
        col.add(woodcutSec.root);

        fishingSec = LoneBotDzLook.section("Fishing Config", BotRuntime.fishingEnabled);
        LoneBotDzBind f = new LoneBotDzBind(cm, fishingSec);
        JCheckBox cbBestFish = LoneBotDzLook.box(!config.fishingUseSpecificMethod());
        JCheckBox cbSpecFish = LoneBotDzLook.box(config.fishingUseSpecificMethod());
        LoneBotConfigUiSync.boolInverted(cbBestFish, "fishingUseSpecificMethod");
        LoneBotConfigUiSync.bool(cbSpecFish, "fishingUseSpecificMethod");
        cbBestFish.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            if (cbBestFish.isSelected()) {
                cbSpecFish.setSelected(false);
            } else if (!cbSpecFish.isSelected()) {
                cbSpecFish.setSelected(true);
            }
            cm.setConfiguration("lonebot", "fishingUseSpecificMethod", cbSpecFish.isSelected());
            fish.run();
        });
        cbSpecFish.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            if (cbSpecFish.isSelected()) {
                cbBestFish.setSelected(false);
            } else if (!cbBestFish.isSelected()) {
                cbBestFish.setSelected(true);
            }
            cm.setConfiguration("lonebot", "fishingUseSpecificMethod", cbSpecFish.isSelected());
            fish.run();
        });
        fishingSec.addItem(LoneBotDzLook.itemRow("Beste methode (niveau)", cbBestFish));
        fishingSec.addItem(LoneBotDzLook.itemRow("Specifieke methode", cbSpecFish));
        f.text("Spot naam", "fishingSpotName", nz(config.fishingSpotName(), "Fishing spot"), fish);
        f.combo("Location", "fishingLocation",
                AreaCenters.presetNames(config.fishingCenters()),
                config.fishingLocation(), fish);
        f.combo("Fishing Options", "fishingAction",
                new String[]{"Auto", "Net", "Bait", "Lure", "First Option"},
                nz(config.fishingAction(), "Auto"), () -> {
                    Object act = config.fishingAction();
                    boolean specific = act != null && !"Auto".equals(act);
                    cm.setConfiguration("lonebot", "fishingUseSpecificMethod", specific);
                    fish.run();
                });
        f.bool("Drop fish", "fishingDropFish", config.fishingDropFish(), fish);
        f.bool("Cook fish", "fishingCookEnabled", config.fishingCookEnabled(), fish);
        f.bool("Clues doen (bottle/scroll → solver)", "fishClueSolver", config.fishClueSolver(), fish);
        f.bool("GE restock (uit)", "fishingRestockEnabled", config.fishingRestockEnabled(), fish);
        f.integer("Bait min", "fishingBaitMin", config.fishingBaitMin(), 0, 5000, fish);
        f.integer("Restock hoeveelheid", "fishingRestockAmount", config.fishingRestockAmount(), 50, 1000, fish);
        f.integer("Max gp / bait", "fishingBaitPrice", config.fishingBaitPrice(), 1, 50, fish);
        f.integer("Max gp / feather", "fishingFeatherPrice", config.fishingFeatherPrice(), 1, 50, fish);
        f.bool("Varrock teleport naar GE", "fishingUseVarrockTeleport", config.fishingUseVarrockTeleport(), fish);
        f.integer("Delay min (ms)", "fishingInteractDelayMin", config.fishingInteractDelayMin(), 0, 5000, fish);
        f.integer("Delay max (ms)", "fishingInteractDelayMax", config.fishingInteractDelayMax(), 0, 10000, fish);
        f.bool("Show Overlay", "fishDebugOverlay", config.fishDebugOverlay(), () -> {
            BotRuntime.fishDebugOverlayEnabled = config.fishDebugOverlay();
            cm.setConfiguration("lonebot", "showFishingAreaOverlay", config.fishDebugOverlay());
        });
        col.add(fishingSec.root);

        starSec = LoneBotDzLook.section("Star Miner Config", BotRuntime.starMinerEnabled);
        starSec.addItem(new LoneBotStarFeedView());
        LoneBotDzBind st = new LoneBotDzBind(cm, starSec);
        st.bool("Geen wilderness", "starMinerAvoidWilderness", config.starMinerAvoidWilderness(), star);
        st.bool("Alleen F2P (lijst + hop)", "starMinerF2pOnly", config.starMinerF2pOnly(), star);
        st.bool("World hop", "starMinerHopEnabled", config.starMinerHopEnabled(), star);
        st.integer("Min tier (skip T1…)", "starMinerMinTier", config.starMinerMinTier(), 1, 9, star);
        st.integer("Wacht extra lagen", "starMinerWaitTiersAbove", config.starMinerWaitTiersAbove(), 0, 8, star);
        st.enums("Tijdens wachten", "starMinerWaitActivity", LoneBotConfig.StarWaitActivity.values(),
                config.starMinerWaitActivity(), star);
        st.enums("Geen minebare tier → skill", "starMinerParkSkill", LoneBotConfig.StarParkSkill.values(),
                config.starMinerParkSkill(), star);
        st.enums("Travel-mode (test)", "starMinerTravelMode", LoneBotConfig.StarTravelModeOpt.values(),
                config.starMinerTravelMode(), star);
        st.bool("Travel via WorldWalker", "starMinerWorldWalkerTravel", config.starMinerWorldWalkerTravel(), star);
        st.bool("Teleports (F2P)", "starMinerTeleportsEnabled", config.starMinerTeleportsEnabled(), star);
        st.bool("Gems banken", "starMinerBankGemsEnabled", config.starMinerBankGemsEnabled(), star);
        st.integer("Gems banken vanaf", "starMinerBankGemsAt", config.starMinerBankGemsAt(), 1, 28, star);
        st.bool("Clues doen (geode/scroll → solver)", "starClueSolver", config.starClueSolver(), star);
        st.text("JSON-feed URL", "starMinerJsonUrl", nz(config.starMinerJsonUrl(), ""), star);
        st.bool("Show Overlay", "starDebugOverlay", config.starDebugOverlay(), () ->
                BotRuntime.starDebugOverlayEnabled = config.starDebugOverlay());
        starSec.addItem(LoneBotStarHelpUi.panel());
        col.add(starSec.root);

        giantsSec = LoneBotDzLook.section("Giants Config", BotRuntime.giantsKillerEnabled);
        LoneBotDzBind g = new LoneBotDzBind(cm, giantsSec);
        g.enums("Combat style", "giantsCombatStyle", GiantsTypes.GiantsCombatStyle.values(),
                config.giantsCombatStyle(), giants);
        g.enums("Mage spell", "giantsMageSpell", GiantsTypes.GiantsMageSpell.values(),
                config.giantsMageSpell(), giants);
        g.text("Loot lijst", "giantsLootItems",
                nz(config.giantsLootItems(), com.lonebot.example.giants.GiantsLocations.DEFAULT_LOOT_CSV), giants);
        g.integer("Eten ≤ HP %", "giantsEatPercent", config.giantsEatPercent(), 5, 90, giants);
        g.integer("Food-bank drempel", "giantsFoodBankThreshold", config.giantsFoodBankThreshold(), 1, 20, giants);
        g.integer("Food uit bank", "giantsFoodAmount", config.giantsFoodAmount(), 1, 28, giants);
        g.bool("Clues doen (scroll/container → solver)", "giantsClueSolver", config.giantsClueSolver(), giants);
        g.bool("Show Overlay", "giantsDebugOverlay", config.giantsDebugOverlay(), () ->
                BotRuntime.giantsDebugOverlayEnabled = config.giantsDebugOverlay());
        col.add(giantsSec.root);

        questSec = LoneBotDzLook.section("Quest Bot Config", BotRuntime.questEnabled);
        LoneBotDzBind q = new LoneBotDzBind(cm, questSec);
        q.enums("Doel", "questTarget", QuestBotTarget.values(),
                QuestBotTarget.from(config.questTarget()), quest);
        q.bool("Rotatie", "questRotationEnabled", config.questRotationEnabled(), quest);
        q.bool("Skip te laag level", "questSkipLowLevel", config.questSkipLowLevel(), quest);
        col.add(questSec.root);

        impsSec = LoneBotDzLook.section("Imp Killer Config", BotRuntime.impKillerEnabled);
        LoneBotDzBind i = new LoneBotDzBind(cm, impsSec);
        i.enums("Combat Style", "impsCombatStyle", ImpsTypes.ImpsCombatStyle.values(),
                config.impsCombatStyle(), imp);
        i.combo("Melee attack style", "impsMeleeTrainingStyle",
                new String[]{"BALANCED", "ATTACK", "STRENGTH", "DEFENCE"},
                nz(config.impsMeleeTrainingStyle(), "BALANCED"), null);
        i.enums("Mage Spell", "impsMageSpell", ImpsTypes.ImpsMageSpell.values(),
                config.impsMageSpell(), imp);
        i.enums("Loot pickup mode", "impsLootPickupMode", ImpsTypes.LootPickupMode.values(),
                config.impsLootPickupMode(), imp);
        i.bool("Loot method strict", "impsLootPickupStrict", config.impsLootPickupStrict(), imp);
        i.text("Loot items", "impsLootItems", nz(config.impsLootItems(), ""), imp);
        i.text("Speciale loots", "impsSpecialLootItems", nz(config.impsSpecialLootItems(), ""), imp);
        i.bool("Loot delay (per kills)", "impsLootDelayEnabled", config.impsLootDelayEnabled(), imp);
        i.integer("Kills vóór looten", "impsLootDelayKills", config.impsLootDelayKills(), 0, 15, imp);
        i.bool("Scatter ashes", "impsScatterAshes", config.impsScatterAshes(), imp);
        i.bool("Clues doen (scroll/container → solver)", "impsClueSolver", config.impsClueSolver(), imp);
        i.bool("Humanize ash loot", "impsAshHumanize", config.impsAshHumanize(), imp);
        i.integer("Ash loot kans %", "impsAshHumanizeChance", config.impsAshHumanizeChance(), 1, 100, imp);
        i.integer("Ash max tiles", "impsAshLootMaxPickupTiles", config.impsAshLootMaxPickupTiles(), 1, 25, null);
        i.integer("Ash min spacing", "impsAshLootMinSpacingTiles", config.impsAshLootMinSpacingTiles(), 1, 25, null);
        i.integer("Bank threshold", "impsBankThreshold", config.impsBankThreshold(), 1, 28, imp);
        i.integer("Min coins", "impsMinCoins", config.impsMinCoins(), 30, 500, imp);
        i.integer("Idle roam (sec)", "impsIdleRoamSeconds", config.impsIdleRoamSeconds(), 0, 60, imp);
        i.bool("Gear prep", "impsGearPrepEnabled", config.impsGearPrepEnabled(), imp);
        i.bool("Quest loot", "impsQuestLootEnabled", config.impsQuestLootEnabled(), imp);
        i.bool("Farm money", "impsFarmMoneyEnabled", config.impsFarmMoneyEnabled(), null);
        i.integer("Farm money min coins", "impsFarmMoneyMinCoins", config.impsFarmMoneyMinCoins(), 1000, 100000, null);
        i.integer("Farm money dumps", "impsFarmMoneyDumpTarget", config.impsFarmMoneyDumpTarget(), 1, 20, null);
        i.bool("Melee Air Strike open", "impsMeleeOpeningAirStrike", config.impsMeleeOpeningAirStrike(), imp);
        i.integer("Melee opener min afstand", "impsMeleeOpeningAirStrikeMinDistance",
                config.impsMeleeOpeningAirStrikeMinDistance(), 1, 12, null);
        i.bool("Melee opener debug", "impsMeleeOpeningAirStrikeDebug", config.impsMeleeOpeningAirStrikeDebug(), null);
        i.integer("Mage trip cast budget", "impsMageTripCastBudget", config.impsMageTripCastBudget(), 1, 200, null);
        i.bool("Stay inside hunt radius", "impsStayInsideRadius", config.impsStayInsideRadius(), null);
        i.integer("Hunting X", "impsHuntingX", config.impsHuntingX(), 2700, 3000, imp);
        i.integer("Hunting Y", "impsHuntingY", config.impsHuntingY(), 3100, 3300, imp);
        i.integer("Hunt radius", "impsHuntingRadius", config.impsHuntingRadius(), 5, 80, imp);
        i.bool("Avoid scorpions", "impsAvoidScorpions", config.impsAvoidScorpions(), imp);
        i.bool("Attack scorpions", "impsAttackScorpions", config.impsAttackScorpions(), imp);
        i.integer("Scorpion radius", "impsScorpionAvoidRadius", config.impsScorpionAvoidRadius(), 1, 20, imp);
        i.integer("Scorpion NPC level", "impsScorpionLevel", config.impsScorpionLevel(), 1, 100, imp);
        i.bool("GE sell", "impsGeSellEnabled", config.impsGeSellEnabled(), imp);
        i.integer("GE na X bank trips", "impsGeSellAfterBanks", config.impsGeSellAfterBanks(), 1, 20, imp);
        i.integer("GE verkoopprijs", "impsGeSellPrice", config.impsGeSellPrice(), 1, 10000, imp);
        i.bool("GE restock (uit)", "impsGeRestockEnabled", config.impsGeRestockEnabled(), imp);
        i.integer("Ammo/rune koopprijs", "impsAmmoRestockPrice", config.impsAmmoRestockPrice(), 1, 5000, null);
        i.integer("Law rune koopprijs", "impsLawRuneBuyPrice", config.impsLawRuneBuyPrice(), 1, 2000, null);
        i.bool("Teleports: runes bijkopen", "impsTeleportBuyRunes", config.impsTeleportBuyRunes(), null);
        i.bool("Varrock teleport", "impsUseVarrockTeleport", config.impsUseVarrockTeleport(), null);
        i.bool("Falador teleport", "impsUseFaladorTeleport", config.impsUseFaladorTeleport(), null);
        i.bool("Lumbridge teleport", "impsUseLumbridgeTeleport", config.impsUseLumbridgeTeleport(), null);
        i.bool("Wereld-hop bij concurrent", "impsCompetitorWorldHop", config.impsCompetitorWorldHop(), null);
        i.integer("Loop stap min", "impsStepMinDistance", config.impsStepMinDistance(), 3, 25, null);
        i.integer("Loop stap max", "impsStepMaxDistance", config.impsStepMaxDistance(), 5, 35, null);
        i.integer("Rally radius", "impsRallyPointRadius", config.impsRallyPointRadius(), 1, 50, null);
        i.integer("Arrival radius", "impsArrivalRadius", config.impsArrivalRadius(), 8, 20, null);
        i.bool("Rally radius overlay", "impsShowRallyRadius", config.impsShowRallyRadius(), null);
        i.integer("Goblin coin X", "impsGoblinCoinCenterX", config.impsGoblinCoinCenterX(), 2950, 3050, null);
        i.integer("Goblin coin Y", "impsGoblinCoinCenterY", config.impsGoblinCoinCenterY(), 3160, 3260, null);
        i.integer("Goblin coin radius", "impsGoblinCoinRadius", config.impsGoblinCoinRadius(), 3, 30, null);
        i.bool("Goblin coin overlay", "impsShowGoblinCoinOverlay", config.impsShowGoblinCoinOverlay(), null);
        i.bool("Show Overlay", "impDebugOverlay", config.impDebugOverlay(), () -> {
            boolean on = config.impDebugOverlay();
            BotRuntime.impDebugOverlayEnabled = on;
            cm.setConfiguration("lonebot", "showImpsHuntOverlay", on);
            if (!on) {
                cm.setConfiguration("lonebot", "impsShowRallyRadius", false);
                cm.setConfiguration("lonebot", "impsShowGoblinCoinOverlay", false);
            }
        });
        col.add(impsSec.root);

        combatSec = LoneBotDzLook.section("Combat Config", BotRuntime.cowCombatEnabled);
        LoneBotDzBind cbt = new LoneBotDzBind(cm, combatSec);
        cbt.combo("Location", "combatLocation",
                AreaCenters.presetNames(config.combatCenters()),
                config.combatLocation(), null);
        cbt.bool("Loot pickup", "cowLootEnabled", config.cowLootEnabled(), () ->
                com.lonebot.example.CowCombatPlugin.lootEnabled = config.cowLootEnabled());
        cbt.bool("Clues doen (scroll/container → solver)", "combatClueSolver", config.combatClueSolver(),
                () -> LoneBotBootstrapPlugin.syncClueInterruptFlags(config));
        cbt.integer("Healen/eten ≤ HP %", "combatEatPercent", config.combatEatPercent(), 5, 90,
                LoneBotBootstrapPlugin::applyMonkSettingsFromUi);
        cbt.bool("Monk: heal via Talk-to", "monkHealViaTalk", config.monkHealViaTalk(),
                LoneBotBootstrapPlugin::applyMonkSettingsFromUi);
        cbt.integer("Nood-flee ≤ HP", "combatCriticalHp", config.combatCriticalHp(), 1, 15,
                LoneBotBootstrapPlugin::applyMonkSettingsFromUi);
        cbt.bool("Monk: cabbage plukken", "monkCabbagePickEnabled", config.monkCabbagePickEnabled(),
                LoneBotBootstrapPlugin::applyMonkSettingsFromUi);
        cbt.integer("Monk: cabbage stock (als HP vol)", "monkCabbagePickAmount", config.monkCabbagePickAmount(), 1, 28,
                LoneBotBootstrapPlugin::applyMonkSettingsFromUi);
        cbt.bool("Show Overlay", "cowDebugOverlay", config.cowDebugOverlay(), () -> {
            BotRuntime.cowDebugOverlayEnabled = config.cowDebugOverlay();
            cm.setConfiguration("lonebot", "showCombatAreaOverlay", config.cowDebugOverlay());
        });
        col.add(combatSec.root);

        clueSec = LoneBotDzLook.section("Clue Config", BotRuntime.clueEnabled
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.CLUE);
        LoneBotDzBind clue = new LoneBotDzBind(cm, clueSec);
        clue.bool("Casket banken (niet openen)", "clueBankCasket", config.clueBankCasket(),
                () -> LoneBotBootstrapPlugin.syncClueInterruptFlags(config));
        clue.bool("Clue items kopen (GE)", "clueGeBuyMissing", config.clueGeBuyMissing(),
                () -> LoneBotBootstrapPlugin.syncClueInterruptFlags(config));
        col.add(clueSec.root);

        LoneBotDzLook.Section overlay = LoneBotDzLook.section("Overlay Settings", false);
        LoneBotDzBind ov = new LoneBotDzBind(cm, overlay);
        ov.bool("Status overlay", "statusOverlay", config.statusOverlay(), null);
        ov.bool("Verberg null objecten (Alt)", "devObjectIdFilterHideUnnamed",
                config.devObjectIdFilterHideUnnamed(), null);
        ov.bool("Minimize panel", "paintOverlayMinimized", config.paintOverlayMinimized(), null);
        ov.bool("Paint: voortgang", "paintShowProgress", config.paintShowProgress(), null);
        ov.bool("Paint: doel", "paintShowTarget", config.paintShowTarget(), null);
        ov.bool("Paint: actie", "paintShowAction", config.paintShowAction(), null);
        ov.bool("Paint: kit / event", "paintShowKit", config.paintShowKit(), null);
        ov.bool("Paint: bank / supply", "paintShowBankSupply", config.paintShowBankSupply(), null);
        ov.bool("Paint: anti-ban", "paintShowAntiBan", config.paintShowAntiBan(), null);
        ov.bool("Paint: script debug details", "paintShowScriptDebug", config.paintShowScriptDebug(), null);
        ov.bool("Mouse debug", "mouseDebugOverlay", config.mouseDebugOverlay(), null);
        ov.bool("WC overlay (grond)", "wcDebugOverlay", config.wcDebugOverlay(), null);
        ov.bool("Fishing overlay (grond)", "fishDebugOverlay", config.fishDebugOverlay(), null);
        ov.bool("Star overlay (grond)", "starDebugOverlay", config.starDebugOverlay(), null);
        ov.bool("Giants overlay", "giantsDebugOverlay", config.giantsDebugOverlay(), null);
        ov.bool("Imp overlay (grond)", "impDebugOverlay", config.impDebugOverlay(), null);
        ov.bool("Cow overlay (grond)", "cowDebugOverlay", config.cowDebugOverlay(), null);
        ov.bool("Combat area overlay", "showCombatAreaOverlay", config.showCombatAreaOverlay(), null);
        ov.bool("Fishing area overlay", "showFishingAreaOverlay", config.showFishingAreaOverlay(), null);
        ov.bool("Mining area overlay", "showMiningAreaOverlay", config.showMiningAreaOverlay(), null);
        col.add(overlay.root);

        LoneBotDzLook.Section walkSec = LoneBotDzLook.section("Walk / Travel", false);
        LoneBotDzBind wk = new LoneBotDzBind(cm, walkSec);
        wk.bool("Walk: minimap", "walkUseMinimap", config.walkUseMinimap(), walk);
        wk.bool("Walk: minimap volledig uit", "walkMinimapFullyZoomedOut", config.walkMinimapFullyZoomedOut(), walk);
        wk.bool("Walk: canvas", "walkUseCanvas", config.walkUseCanvas(), walk);
        wk.bool("Walk: UI-zones", "walkUseUiZones", config.walkUseUiZones(), walk);
        wk.bool("Walk: camera nudge", "walkUseCameraNudge", config.walkUseCameraNudge(), walk);
        wk.bool("Walk: invoke WALK", "walkUseInvoke", config.walkUseInvoke(), walk);
        wk.bool("Walk: far canvas", "walkFarCanvas", config.walkFarCanvas(), walk);
        wk.bool("Walk: shortcuts (stile)", "walkUseShortcuts", config.walkUseShortcuts(), walk);
        wk.bool("Walk-camera aan", "walkCamEnabled", config.walkCamEnabled(), walk);
        wk.bool("Walk-camera MMB", "walkCamHumanMmb", config.walkCamHumanMmb(), walk);
        wk.bool("Walk-camera toetsenbord", "walkCamUseKeyboard", config.walkCamUseKeyboard(), walk);
        wk.bool("Grote stappen", "walkForceLargeSteps", config.walkForceLargeSteps(), walk);
        wk.bool("Twin quick clicks", "walkTwinQuickClicks", config.walkTwinQuickClicks(), walk);
        wk.bool("Auto-run", "walkAutoRun", config.walkAutoRun(), walk);
        wk.integer("Auto-run vanaf %", "walkAutoRunMinEnergy", config.walkAutoRunMinEnergy(), 5, 80, walk);
        wk.integer("Stap min", "walkStepMin", config.walkStepMin(), 1, 40, walk);
        wk.integer("Stap max", "walkStepMax", config.walkStepMax(), 1, 50, walk);
        wk.integer("Eindklik binnen (tegels)", "walkFinalClickTiles", config.walkFinalClickTiles(), 8, 40, walk);
        wk.integer("Pauze na klik min (ms)", "walkPostClickDelayMin", config.walkPostClickDelayMin(), 0, 1000, walk);
        wk.integer("Pauze na klik max (ms)", "walkPostClickDelayMax", config.walkPostClickDelayMax(), 0, 1500, walk);
        wk.integer("Hop-doorlink min (ms)", "walkChainClickMin", config.walkChainClickMin(), 20, 800, walk);
        wk.integer("Hop-doorlink max (ms)", "walkChainClickMax", config.walkChainClickMax(), 20, 1500, walk);
        wk.integer("Camera yaw offset", "walkCamYawOffsetDeg", config.walkCamYawOffsetDeg(), -180, 180, walk);
        wk.integer("Camera pitch", "walkCamPitch", config.walkCamPitch(), 128, 4096, walk);
        wk.integer("Camera zoom %", "walkCamZoomPercent", config.walkCamZoomPercent(), 0, 100, walk);
        col.add(walkSec.root);

        LoneBotDzLook.Section abSec = LoneBotDzLook.section("Anti-Ban", false);
        LoneBotDzBind a = new LoneBotDzBind(cm, abSec);
        a.bool("Anti-ban", "antiBanEnabled", config.antiBanEnabled(), ab);
        a.bool("AB: camera", "antiBanCamera", config.antiBanCamera(), ab);
        a.bool("AB: idle pauzes", "antiBanIdle", config.antiBanIdle(), ab);
        a.bool("AB: random muis", "antiBanMouse", config.antiBanMouse(), ab);
        a.bool("AB: random toetsenbord", "antiBanKeyboardPan", config.antiBanKeyboardPan(), ab);
        a.bool("AB: misclick", "antiBanMisclick", config.antiBanMisclick(), ab);
        a.bool("AB: tab glance", "antiBanTabGlance", config.antiBanTabGlance(), ab);
        a.bool("AB: continuous fidget", "antiBanMouseFidget", config.antiBanMouseFidget(), ab);
        a.integer("AB frequentie (sec)", "antiBanFrequencySec", config.antiBanFrequencySec(), 10, 180, ab);
        a.bool("Random events", "randomEventsEnabled", config.randomEventsEnabled(), ab);
        a.enums("Genie lamp skill", "genieLampSkill", LoneBotConfig.GenieLampSkill.values(),
                config.genieLampSkill(), ab);
        a.bool("Random muis-beweging", "mouseFidgetEnabled", config.mouseFidgetEnabled(), null);
        a.integer("Muis snelheid %", "mouseSpeedPercent", config.mouseSpeedPercent(), 25, 300, null);
        col.add(abSec.root);

        col.add(Box.createVerticalStrut(10));
        JButton reset = LoneBotDzLook.resetButton();
        reset.addActionListener(e -> {
            LoneBotBotControl.emergencyStop();
            LoneBotBotControl.syncBotEnabledConfig(cm, false);
            BotRuntime.logConsole("[DZ] Reset / noodstop");
            refreshButtons();
        });
        col.add(reset);
        col.add(Box.createVerticalStrut(12));

        JScrollPane scroll = new JScrollPane(col);
        scroll.setBorder(null);
        scroll.setOpaque(true);
        scroll.setBackground(LoneBotDzLook.BG);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(LoneBotDzLook.BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        syncTimer = new Timer(400, e -> syncFromRuntime());
        syncTimer.setRepeats(true);
        syncTimer.start();
        expandForScript();
        refreshButtons();
    }

    private void applyWalkLive() {
        WalkClickSettings.applyAll(
                config.walkUseMinimap(),
                config.walkUseCanvas(),
                config.walkUseUiZones(),
                config.walkUseCameraNudge(),
                config.walkUseInvoke(),
                config.walkFarCanvas(),
                config.walkStepMin(),
                config.walkStepMax(),
                config.walkForceLargeSteps(),
                config.walkReclickMs(),
                config.walkPostClickDelayMin(),
                config.walkPostClickDelayMax(),
                config.walkFlagProximityReclick(),
                config.walkReclickNearFlagMs(),
                config.walkTwinQuickClicks(),
                config.walkChainClickMin(),
                config.walkChainClickMax());
        WalkClickSettings.setFinalClickWithinTiles(config.walkFinalClickTiles());
        WalkClickSettings.autoRun = config.walkAutoRun();
        WalkClickSettings.autoRunMinEnergy = config.walkAutoRunMinEnergy();
        WalkClickSettings.minimapFullyZoomedOut = config.walkMinimapFullyZoomedOut();
        boolean shortcuts = config.walkUseShortcuts();
        if (WalkClickSettings.useShortcuts != shortcuts) {
            MovementHelper.clearPath();
        }
        WalkClickSettings.useShortcuts = shortcuts;
        if (WalkClickSettings.minimapFullyZoomedOut) {
            MinimapZoomHelper.ensureFullyZoomedOut();
        }
        LoneBotBootstrapPlugin.applyWalkCameraSettings(config);
    }

    private void applyAntiBanLive() {
        LoneBotConfig.GenieLampSkill gs = config.genieLampSkill();
        if (gs == null) {
            gs = LoneBotConfig.GenieLampSkill.NONE;
        }
        net.storm.sdk.game.RandomEventSettings.apply(config.randomEventsEnabled(), gs.name());
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
        AntiBan.get().restartFidgetWorkerIfNeeded();
    }

    private void applyScriptFromCombo(Consumer<BotRuntime.ActiveSkill> onScript) {
        BotRuntime.ActiveSkill skill = skillFromLabel(String.valueOf(scriptCombo.getSelectedItem()));
        LoneBotBotControl.selectSkill(skill, cm, config);
        expandForScript();
        if (onScript != null) {
            onScript.accept(skill);
        }
    }

    private void expandForScript() {
        String sel = String.valueOf(scriptCombo.getSelectedItem());
        woodcutSec.setOpen("Woodcutting".equals(sel));
        fishingSec.setOpen("Fishing".equals(sel));
        starSec.setOpen("Star Miner".equals(sel));
        giantsSec.setOpen("Giants".equals(sel));
        questSec.setOpen("Quest Bot".equals(sel));
        impsSec.setOpen("Imp Killer".equals(sel));
        combatSec.setOpen("Cow Combat".equals(sel) || "Monk Killer".equals(sel));
        if (clueSec != null) {
            clueSec.setOpen("Beginner Clue".equals(sel));
        }
    }

    private void syncFromRuntime() {
        refreshButtons();
        String want = scriptLabel(BotRuntime.activeSkill);
        if (!want.equals(String.valueOf(scriptCombo.getSelectedItem()))) {
            suppress.set(true);
            try {
                scriptCombo.setSelectedItem(want);
            } finally {
                suppress.set(false);
            }
        }
    }

    private void refreshButtons() {
        boolean on = BotRuntime.botEnabled;
        btnStart.setEnabled(!on);
        btnPause.setEnabled(on);
        btnStop.setEnabled(on || BotRuntime.pausedForResume);
        if (on) {
            statusLbl.setText("●  Aan");
            statusLbl.setForeground(new Color(80, 200, 120));
        } else if (BotRuntime.pausedForResume) {
            statusLbl.setText("●  Pauze");
            statusLbl.setForeground(new Color(230, 180, 80));
        } else {
            statusLbl.setText("●  Uit");
            statusLbl.setForeground(LoneBotDzLook.MUTED);
        }
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static String scriptLabel(BotRuntime.ActiveSkill skill) {
        if (skill == null) {
            return "None";
        }
        switch (skill) {
            case WOODCUTTING:
                return "Woodcutting";
            case FISHING:
                return "Fishing";
            case STAR:
                return "Star Miner";
            case GIANTS:
                return "Giants";
            case CLUE:
                return "Beginner Clue";
            case IMP:
                return "Imp Killer";
            case COW:
                return "Cow Combat";
            case MONK:
                return "Monk Killer";
            case QUEST:
                return "Quest Bot";
            case IMP2:
            default:
                return "None";
        }
    }

    private static BotRuntime.ActiveSkill skillFromLabel(String label) {
        if (label == null) {
            return BotRuntime.ActiveSkill.NONE;
        }
        switch (label) {
            case "Woodcutting":
                return BotRuntime.ActiveSkill.WOODCUTTING;
            case "Fishing":
                return BotRuntime.ActiveSkill.FISHING;
            case "Star Miner":
                return BotRuntime.ActiveSkill.STAR;
            case "Giants":
                return BotRuntime.ActiveSkill.GIANTS;
            case "Beginner Clue":
                return BotRuntime.ActiveSkill.CLUE;
            case "Imp Killer":
                return BotRuntime.ActiveSkill.IMP;
            case "Cow Combat":
                return BotRuntime.ActiveSkill.COW;
            case "Monk Killer":
                return BotRuntime.ActiveSkill.MONK;
            case "Quest":
            case "Quest Bot":
                return BotRuntime.ActiveSkill.QUEST;
            default:
                return BotRuntime.ActiveSkill.NONE;
        }
    }
}
