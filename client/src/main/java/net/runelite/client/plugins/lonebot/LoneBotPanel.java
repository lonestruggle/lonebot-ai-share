package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.lonebot.login.JagexLauncherPlayButton;
import net.runelite.client.plugins.lonebot.login.RelogRuntime;
import net.runelite.client.plugins.lonebot.login.WelcomeScreenPlayHelper;
import net.runelite.client.plugins.lonebot.dev.ObjectIdFilterStore;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.storm.api.account.GameAccount;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Game;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.input.MouseBackend;
import net.storm.sdk.input.MouseSettings;
import net.storm.sdk.interact.NpcTargetTests;
import net.storm.sdk.items.DepositBox;
import net.storm.sdk.loop.LoopHost;
import net.storm.sdk.movement.WalkCamera;
import net.storm.sdk.movement.WalkCameraSettings;
import net.storm.sdk.movement.WalkClickHelper;
import net.storm.sdk.movement.WalkClickSettings;
import net.storm.sdk.movement.WorldWalker;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.utils.AntiBanSettings;
import com.lonebot.example.Imps2Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sidebar: Storm-accountlijst + credentials paste + login klaarzetten.
 */
public class LoneBotPanel extends PluginPanel {

    private static final Logger log = LoggerFactory.getLogger(LoneBotPanel.class);
    private static final File DEFAULT_STORM_JSON =
            new File(System.getProperty("user.home"), "Desktop" + File.separator + "storm-accountsjuli.json");

    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final LoneBotConfig config;

    private final DefaultListModel<ManagedAccountsStore.ManagedAccount> listModel = new DefaultListModel<>();
    private final JList<ManagedAccountsStore.ManagedAccount> accountList = new JList<>(listModel);
    private final JTextArea credentialsArea = new JTextArea(6, 20);
    private final JLabel statusLabel = new JLabel("Importeer Storm JSON of plak JX_* credentials.");
    private final JTextArea consoleArea = new JTextArea(12, 20);
    /** Uit = blijf op scroll-positie (handig bij kopiëren); aan = spring naar nieuwste regel. */
    private volatile boolean consoleAutoScroll = true;
    private final JTextArea captureLogArea = new JTextArea(14, 20);
    private JLabel capturePreviewLabel;

    private JTabbedPane mainTabs;
    private JPanel sidebarBody;
    private LoneBotAccountsTab accountsTab;
    private JTabbedPane skillTabs;
    private Component skillsWrap;
    private Component panelSettings;
    private Component panelWoodcut;
    private Component panelFishing;
    private Component panelStar;
    private Component panelImps;
    private Component panelImps2;
    private Component panelCombat;
    private Component panelGeneral;
    private Component panelMining;
    private Component panelGiants;
    private Component panelClue;
    private Component panelQuest;
    private Component panelSos;
    private Component panelBarb;
    private Component panelStats;
    private Component panelCenters;
    private Component panelCapture;
    private JPanel panelDebug;
    private Component panelTest;
    private LoneBotAccordionNav clientAccordion;
    private JScrollPane clientAccordionScroll;
    private boolean clientAccordionActive;
    private LoneBotDzSidebar dzSidebar;
    private JPanel controlWindowHeader;
    private JFrame detachedFrame;
    private JLabel headerTitleLabel;
    private JLabel scriptVersionLabel;
    private Timer titleRefreshTimer;

    @Inject
    public LoneBotPanel(Client client, ClientThread clientThread, ConfigManager configManager, LoneBotConfig config) {
        super(false);
        this.client = client;
        this.clientThread = clientThread;
        this.configManager = configManager;
        this.config = config;

        LoneBotUiTheme.apply(LoneBotUiTheme.resolve(
                config.uiTheme() != null ? config.uiTheme() : LoneBotUiTheme.Preset.BALANS));

        setBorder(new EmptyBorder(0, 0, 0, 0));
        setBackground(LoneBotDzLook.BG);
        LoneBotUiTheme.mark(this, LoneBotUiTheme.ROLE_ROOT);
        setLayout(new BorderLayout(0, 6));

        headerTitleLabel = new JLabel(LoneBotBootstrapPlugin.controlPanelHeaderText());
        headerTitleLabel.setForeground(LoneBotUiTheme.GOLD);
        headerTitleLabel.setFont(headerTitleLabel.getFont().deriveFont(Font.BOLD, 13f));
        headerTitleLabel.setToolTipText("Dit venster is gebonden aan deze client/account (niet de multi-account Launcher)");

        scriptVersionLabel = new JLabel(scriptVersionsLine());
        scriptVersionLabel.setForeground(LoneBotUiTheme.TEXT_DIM);
        scriptVersionLabel.setFont(scriptVersionLabel.getFont().deriveFont(Font.PLAIN, 11f));
        scriptVersionLabel.setToolTipText("Geladen script-jars (hot-reload). Zelfde versie als in-game debug.");

        JButton btnPopout = new JButton("📐");
        btnPopout.setToolTipText("Open Control Panel in apart venster");
        btnPopout.setFocusPainted(false);
        btnPopout.addActionListener(e -> toggleDetachedWindow());

        JPanel headerText = new JPanel();
        headerText.setOpaque(false);
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        headerText.add(headerTitleLabel);
        headerText.add(scriptVersionLabel);

        controlWindowHeader = new JPanel(new BorderLayout(6, 0));
        controlWindowHeader.setOpaque(false);
        controlWindowHeader.add(headerText, BorderLayout.CENTER);
        controlWindowHeader.add(btnPopout, BorderLayout.EAST);

        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        accountList.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        accountList.setVisibleRowCount(6);
        accountList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                fillPasteFromSelection();
            }
        });
        JScrollPane listScroll = new JScrollPane(accountList);
        listScroll.setPreferredSize(new Dimension(PANEL_WIDTH - 24, 120));

        credentialsArea.setLineWrap(true);
        credentialsArea.setWrapStyleWord(true);
        credentialsArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        credentialsArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        credentialsArea.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
        credentialsArea.setToolTipText("JX_DISPLAY_NAME=...\nJX_CHARACTER_ID=...\nJX_SESSION_ID=...");
        JScrollPane credScroll = new JScrollPane(credentialsArea);
        credScroll.setPreferredSize(new Dimension(PANEL_WIDTH - 24, 90));

        JCheckBox cbCow = new JCheckBox("Cow combat", config.cowCombatEnabled() || BotRuntime.cowCombatEnabled);
        cbCow.setOpaque(false);
        cbCow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        cbCow.setToolTipText("Zichtbare muisklik op cows (gebruikt gekozen Mouse backend)");

        JCheckBox cbMonk = new JCheckBox(
                skillCheckLabel("Monk Killer", livePluginVersion(BotRuntime.monkPluginVersion,
                        "com.lonebot.example.MonkKillerPlugin")),
                config.monkKillerEnabled() || BotRuntime.monkKillerEnabled);
        styleCheck(cbMonk);
        cbMonk.setToolTipText("Monastery: monks attack, eat, cabbage/Edge bank, flee ≤3 HP");

        JCheckBox cbGiants = new JCheckBox(
                skillCheckLabel("Giants (Hill Giants)", livePluginVersion(BotRuntime.giantsPluginVersion,
                        "com.lonebot.example.GiantsPlugin")),
                config.giantsKillerEnabled() || BotRuntime.giantsKillerEnabled);
        styleCheck(cbGiants);
        cbGiants.setToolTipText("Edgeville Dungeon Hill Giants: brass key, shed/trapdoor, loot, eigen food-bank");

        JCheckBox cbImps = new JCheckBox(
                skillCheckLabel("Imp Killer (Karamja)", livePluginVersion(BotRuntime.impPluginVersion,
                        "com.lonebot.example.ImpKillerPlugin")),
                config.impKillerEnabled() || BotRuntime.impKillerEnabled);
        styleCheck(cbImps);
        cbImps.setToolTipText("Hunt → loot → boat → deposit → restock → terug (per-account opgeslagen)");

        JCheckBox cbWc = new JCheckBox(
                skillCheckLabel("Woodcutting", livePluginVersion(BotRuntime.wcPluginVersion,
                        "com.lonebot.example.woodcutter.WoodcutterPlugin")),
                config.woodcuttingEnabled() || BotRuntime.woodcuttingEnabled);
        styleCheck(cbWc);
        cbWc.setToolTipText("Draynor willows / Port Sarim / yews — bonfire, forestry kit, bird nests");

        JCheckBox cbFish = new JCheckBox(
                skillCheckLabel("Fishing", livePluginVersion(BotRuntime.fishPluginVersion,
                        "com.lonebot.example.fishing.FishingPlugin")),
                config.fishingEnabled() || BotRuntime.fishingEnabled);
        styleCheck(cbFish);
        cbFish.setToolTipText("Draynor Net/Bait · Barbarian Lure · bank/GE/cook");

        cbCow.addActionListener(e -> {
            boolean on = cbCow.isSelected();
            if (on) {
                cbImps.setSelected(false);
                cbMonk.setSelected(false);
                if (cbGiants != null) {
                    cbGiants.setSelected(false);
                }
                cbWc.setSelected(false);
                cbFish.setSelected(false);
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.COW);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                BotRuntime.menuProbeSummary = "pending…";
            } else {
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
            }
            persistAccountSkills(cbImps.isSelected(), on, cbWc.isSelected(), cbFish.isSelected());
            setStatus(on ? "✓ Cow combat AAN — backend: " + Mouse.getBackend().name() : "Cow combat uit");
        });

        cbMonk.addActionListener(e -> {
            boolean on = cbMonk.isSelected();
            if (on) {
                cbCow.setSelected(false);
                cbImps.setSelected(false);
                cbGiants.setSelected(false);
                cbWc.setSelected(false);
                cbFish.setSelected(false);
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.MONK);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
                setStatus("✓ Monk Killer AAN — monastery " + com.lonebot.example.MonkKillerPlugin.FIGHT_CENTER.getX()
                        + "," + com.lonebot.example.MonkKillerPlugin.FIGHT_CENTER.getY());
            } else {
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                setStatus("Monk Killer uit");
            }
            persistAccountSkills(cbImps.isSelected(), cbCow.isSelected(), cbWc.isSelected(), cbFish.isSelected());
        });

        JCheckBox cbLoot = new JCheckBox("Cow loot oppakken", config.cowLootEnabled());
        cbLoot.setOpaque(false);
        cbLoot.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        cbLoot.setToolTipText("Bones / Cowhide / Raw beef / Coins via TileItems.pickup");
        cbLoot.addActionListener(e -> {
            boolean on = cbLoot.isSelected();
            configManager.setConfiguration("lonebot", "cowLootEnabled", on);
            com.lonebot.example.CowCombatPlugin.lootEnabled = on;
            setStatus(on ? "✓ Loot AAN" : "Loot uit");
        });
        LoneBotConfigUiSync.bool(cbLoot, "cowLootEnabled");
        com.lonebot.example.CowCombatPlugin.lootEnabled = cbLoot.isSelected();
        JCheckBox cbCombatClue = new JCheckBox("Clues doen (scroll/container → solver)", config.combatClueSolver());
        cbCombatClue.setOpaque(false);
        cbCombatClue.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        cbCombatClue.setToolTipText("Oppakken → openen → clue solver → daarna combat hervatten (Cow/Monk/…)");
        cbCombatClue.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "combatClueSolver", cbCombatClue.isSelected());
            LoneBotBootstrapPlugin.syncClueInterruptFlags(config);
        });
        LoneBotConfigUiSync.bool(cbCombatClue, "combatClueSolver");
        LoneBotBootstrapPlugin.syncClueInterruptFlags(config);

        JCheckBox cbMonkHealTalk = new JCheckBox("Heal via Talk-to (monk)", config.monkHealViaTalk());
        styleCheck(cbMonkHealTalk);
        cbMonkHealTalk.setToolTipText(
                "Praat met rustige monk (Can you heal me?). Geen talkable monk → food/cabbage/bank");
        cbMonkHealTalk.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "monkHealViaTalk", cbMonkHealTalk.isSelected());
            LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
        });
        LoneBotConfigUiSync.bool(cbMonkHealTalk, "monkHealViaTalk");

        JLabel monkEatLbl = new JLabel("Healen/eten ≤ " + config.combatEatPercent() + "%");
        styleLabel(monkEatLbl);
        JSlider monkEatSlider = new JSlider(5, 90, config.combatEatPercent());
        monkEatSlider.setOpaque(false);
        monkEatSlider.setToolTipText("HP-% waarbij heal/eten start (alle combat-locaties)");
        monkEatSlider.addChangeListener(ev -> {
            int v = monkEatSlider.getValue();
            monkEatLbl.setText("Healen/eten ≤ " + v + "%");
            if (!monkEatSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "combatEatPercent", v);
                LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
            }
        });
        LoneBotConfigUiSync.slider(monkEatSlider, monkEatLbl, "combatEatPercent");

        JLabel monkCritLbl = new JLabel("Nood-flee ≤ " + config.combatCriticalHp() + " HP");
        styleLabel(monkCritLbl);
        JSlider monkCritSlider = new JSlider(1, 15, config.combatCriticalHp());
        monkCritSlider.setOpaque(false);
        monkCritSlider.setToolTipText("Nood-flee bij absolute HP (alle combat-locaties)");
        monkCritSlider.addChangeListener(ev -> {
            int v = monkCritSlider.getValue();
            monkCritLbl.setText("Nood-flee ≤ " + v + " HP");
            if (!monkCritSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "combatCriticalHp", v);
                LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
            }
        });
        LoneBotConfigUiSync.slider(monkCritSlider, monkCritLbl, "combatCriticalHp");

        JLabel monkFoodAmtLbl = new JLabel("Food uit bank: " + config.monkFoodAmount());
        styleLabel(monkFoodAmtLbl);
        JSlider monkFoodAmtSlider = new JSlider(1, 28, config.monkFoodAmount());
        monkFoodAmtSlider.setOpaque(false);
        monkFoodAmtSlider.addChangeListener(ev -> {
            int v = monkFoodAmtSlider.getValue();
            monkFoodAmtLbl.setText("Food uit bank: " + v);
            if (!monkFoodAmtSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "monkFoodAmount", v);
                LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
            }
        });
        LoneBotConfigUiSync.slider(monkFoodAmtSlider, monkFoodAmtLbl, "monkFoodAmount");

        JCheckBox cbMonkCabbage = new JCheckBox("Cabbage plukken", config.monkCabbagePickEnabled());
        styleCheck(cbMonkCabbage);
        cbMonkCabbage.setToolTipText("Veld-cabbage als food-restock; uit = alleen Talk-heal / bank / inv-food");
        cbMonkCabbage.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "monkCabbagePickEnabled", cbMonkCabbage.isSelected());
            LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
        });
        LoneBotConfigUiSync.bool(cbMonkCabbage, "monkCabbagePickEnabled");

        JLabel monkCabAmtLbl = new JLabel("Cabbage aantal: " + config.monkCabbagePickAmount());
        styleLabel(monkCabAmtLbl);
        JSlider monkCabAmtSlider = new JSlider(1, 28, config.monkCabbagePickAmount());
        monkCabAmtSlider.setOpaque(false);
        monkCabAmtSlider.setToolTipText(
                "Stock als HP vol. Bij tekort: pluk genoeg (cabbage = 1 HP) om vol te komen");
        monkCabAmtSlider.addChangeListener(ev -> {
            int v = monkCabAmtSlider.getValue();
            monkCabAmtLbl.setText("Cabbage aantal: " + v);
            if (!monkCabAmtSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "monkCabbagePickAmount", v);
                LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
            }
        });
        LoneBotConfigUiSync.slider(monkCabAmtSlider, monkCabAmtLbl, "monkCabbagePickAmount");

        JComboBox<LoneBotConfig.MonkFoodSource> comboMonkFoodSrc =
                new JComboBox<>(LoneBotConfig.MonkFoodSource.values());
        comboMonkFoodSrc.setSelectedItem(config.monkFoodSource());
        comboMonkFoodSrc.setToolTipText("AUTO = cabbage eerst, anders bank");
        comboMonkFoodSrc.addActionListener(e -> {
            LoneBotConfig.MonkFoodSource src =
                    (LoneBotConfig.MonkFoodSource) comboMonkFoodSrc.getSelectedItem();
            if (src != null) {
                configManager.setConfiguration("lonebot", "monkFoodSource", src.name());
                LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
            }
        });
        LoneBotConfigUiSync.combo(comboMonkFoodSrc, "monkFoodSource");

        JCheckBox cbMonkBank = new JCheckBox("Bank bij geen food", config.monkBankWhenNoFood());
        styleCheck(cbMonkBank);
        cbMonkBank.setToolTipText("Edgeville bank als food op is (tenzij food-bron = alleen cabbage)");
        cbMonkBank.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "monkBankWhenNoFood", cbMonkBank.isSelected());
            LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
        });
        LoneBotConfigUiSync.bool(cbMonkBank, "monkBankWhenNoFood");

        JCheckBox cbMonkLogout = new JCheckBox("Uitloggen zonder food", config.monkLogoutWhenNoFood());
        styleCheck(cbMonkLogout);
        cbMonkLogout.setToolTipText("Geen cabbage/bank food meer → bot uit + logout");
        cbMonkLogout.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "monkLogoutWhenNoFood", cbMonkLogout.isSelected());
            LoneBotBootstrapPlugin.applyMonkSettingsFromUi();
        });
        LoneBotConfigUiSync.bool(cbMonkLogout, "monkLogoutWhenNoFood");

        LoneBotBootstrapPlugin.applyMonkSettingsFromUi();

        cbImps.addActionListener(e -> {
            boolean on = cbImps.isSelected();
            if (on) {
                cbCow.setSelected(false);
                cbMonk.setSelected(false);
                cbGiants.setSelected(false);
                cbWc.setSelected(false);
                cbFish.setSelected(false);
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.IMP);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                LoneBotBootstrapPlugin.applyImpSettings(config);
                setStatus("✓ Imp Killer AAN — overlay: Imp");
            } else {
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                setStatus("Imp Killer uit");
            }
            persistAccountSkills(on, cbCow.isSelected(), cbWc.isSelected(), cbFish.isSelected());
        });
        if (cbImps.isSelected()) {
            LoneBotBootstrapPlugin.applyImpSettings(config);
        }

        // WC logs: Bank / FM / Drop — UI op Woodcut-tab + DZ (niet hier als losse toggles)
        JCheckBox cbWcForestry = new JCheckBox("WC forestry events", config.wcForestryEvents());
        styleCheck(cbWcForestry);
        cbWcForestry.setToolTipText("Kit bij Friendly Forester + F2P events");
        cbWcForestry.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "wcForestryEvents", cbWcForestry.isSelected());
            LoneBotBootstrapPlugin.applyWcSettings(config);
        });
        LoneBotConfigUiSync.bool(cbWcForestry, "wcForestryEvents");

        JCheckBox cbWcNests = new JCheckBox("WC bird nests", config.wcBirdNests());
        styleCheck(cbWcNests);
        cbWcNests.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "wcBirdNests", cbWcNests.isSelected());
            LoneBotBootstrapPlugin.applyWcSettings(config);
        });
        LoneBotConfigUiSync.bool(cbWcNests, "wcBirdNests");

        JCheckBox cbWcClue = new JCheckBox("WC Clues doen (geode/nest/bottle → solver)", config.wcClueSolver());
        styleCheck(cbWcClue);
        cbWcClue.setToolTipText("Oppakken → openen → clue solver → daarna WC hervatten");
        cbWcClue.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "wcClueSolver", cbWcClue.isSelected());
            LoneBotBootstrapPlugin.applyWcSettings(config);
        });
        LoneBotConfigUiSync.bool(cbWcClue, "wcClueSolver");

        JLabel wcLocLabel = new JLabel("WC locatie:");
        styleLabel(wcLocLabel);
        JComboBox<String> comboWcLoc = new JComboBox<>(
                com.lonebot.example.woodcutter.WcCenters.presetNames(config.wcCenters()));
        // Voorkomt: refresh → removeAllItems → ActionListener schrijft "null"/"AUTO" over opgeslagen keuze
        final java.util.concurrent.atomic.AtomicBoolean suppressWcLocWrite =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        String savedLoc = config.wcLocation();
        suppressWcLocWrite.set(true);
        try {
            if (savedLoc != null && !savedLoc.isBlank() && !"null".equalsIgnoreCase(savedLoc)) {
                comboWcLoc.setSelectedItem(savedLoc);
                if (comboWcLoc.getSelectedIndex() < 0) {
                    comboWcLoc.addItem(savedLoc);
                    comboWcLoc.setSelectedItem(savedLoc);
                }
            }
            if (comboWcLoc.getSelectedIndex() < 0) {
                comboWcLoc.setSelectedItem("AUTO");
            }
        } finally {
            suppressWcLocWrite.set(false);
        }
        comboWcLoc.setToolTipText("AUTO = per WC-level; anders vaste spot uit wcCenters (alleen hier opslaan)");
        comboWcLoc.addActionListener(e -> {
            if (suppressWcLocWrite.get() || LoneBotConfigUiSync.applying()) {
                return;
            }
            Object sel = comboWcLoc.getSelectedItem();
            if (sel == null) {
                return;
            }
            String loc = String.valueOf(sel).trim();
            if (loc.isEmpty() || "null".equalsIgnoreCase(loc)) {
                return;
            }
            configManager.setConfiguration("lonebot", "wcLocation", loc);
            LoneBotBootstrapPlugin.applyWcSettings(config);
            setStatus("WC locatie → " + loc);
        });
        LoneBotConfigUiSync.combo(comboWcLoc, "wcLocation");

        JCheckBox cbWcCentersMenu = new JCheckBox("Rechtermenu: WC centers", config.wcCentersMenuEnabled());
        styleCheck(cbWcCentersMenu);
        cbWcCentersMenu.setToolTipText("In-game choose-option: center toevoegen / radius ± / verwijderen");
        cbWcCentersMenu.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "wcCentersMenuEnabled", cbWcCentersMenu.isSelected());
            setStatus(cbWcCentersMenu.isSelected()
                    ? "✓ WC centers-menu AAN — rechtsklik op tegel"
                    : "WC centers-menu uit");
        });

        JCheckBox cbWcAreaOv = new JCheckBox("WC area overlay", config.showWcOverlay());
        styleCheck(cbWcAreaOv);
        cbWcAreaOv.addActionListener(e -> {
            boolean on = cbWcAreaOv.isSelected();
            configManager.setConfiguration("lonebot", "showWcOverlay", on);
            configManager.setConfiguration("lonebot", "wcDebugOverlay", on);
            BotRuntime.wcDebugOverlayEnabled = on;
            setStatus(on ? "✓ WC area overlay AAN" : "WC area overlay uit");
        });
        LoneBotConfigUiSync.bool(cbWcAreaOv, "showWcOverlay");
        LoneBotConfigUiSync.bool(cbWcAreaOv, "wcDebugOverlay");

        JLabel wcEditLabel = new JLabel("Bewerk center:");
        styleLabel(wcEditLabel);
        JComboBox<String> comboWcEdit = new JComboBox<>();
        LoneBotUiTheme.styleCombo(comboWcEdit);
        LoneBotUiTheme.mark(comboWcEdit, LoneBotUiTheme.ROLE_INPUT);
        JTextField tfWcName = new JTextField(14);
        tfWcName.setFont(LoneBotUiTheme.FONT_LABEL);
        tfWcName.setBackground(LoneBotUiTheme.INPUT_BG);
        tfWcName.setForeground(LoneBotUiTheme.INPUT_FG);
        tfWcName.setCaretColor(LoneBotUiTheme.INPUT_FG);
        LoneBotUiTheme.mark(tfWcName, LoneBotUiTheme.ROLE_INPUT);
        tfWcName.setToolTipText("Nieuwe naam (geen : of |)");

        java.util.List<Integer> wcEditIndexList = new java.util.ArrayList<>();
        final Runnable[] refreshWcCenterUiHolder = new Runnable[1];
        refreshWcCenterUiHolder[0] = () -> {
            String blob = LoneBotBootstrapPlugin.liveWcCentersBlob();
            String ensured = com.lonebot.example.woodcutter.WcCenters.ensureNames(blob);
            if (!ensured.equals(blob)) {
                LoneBotBootstrapPlugin.persistWcCenters(ensured);
                blob = ensured;
            }
            // Altijd config als bron — niet de combo tijdens rebuild (was null → AUTO overwrite)
            String keepLoc = config.wcLocation();
            if (keepLoc == null || keepLoc.isBlank() || "null".equalsIgnoreCase(keepLoc)) {
                keepLoc = "AUTO";
            }
            suppressWcLocWrite.set(true);
            try {
                comboWcLoc.removeAllItems();
                for (String n : com.lonebot.example.woodcutter.WcCenters.presetNames(blob)) {
                    comboWcLoc.addItem(n);
                }
                comboWcLoc.setSelectedItem(keepLoc);
                if (comboWcLoc.getSelectedIndex() < 0) {
                    comboWcLoc.addItem(keepLoc);
                    comboWcLoc.setSelectedItem(keepLoc);
                }
                if (comboWcLoc.getSelectedIndex() < 0) {
                    comboWcLoc.setSelectedItem("AUTO");
                }
            } finally {
                suppressWcLocWrite.set(false);
            }
            wcEditIndexList.clear();
            Object selEdit = comboWcEdit.getSelectedItem();
            comboWcEdit.removeAllItems();
            java.util.List<com.lonebot.example.woodcutter.WcCenters.Center> all =
                    com.lonebot.example.woodcutter.WcCenters.parseAll(blob);
            for (int i = 0; i < all.size(); i++) {
                com.lonebot.example.woodcutter.WcCenters.Center c = all.get(i);
                if (c == null) {
                    continue;
                }
                wcEditIndexList.add(i);
                comboWcEdit.addItem(com.lonebot.example.woodcutter.WcCenters.displayLabel(c));
            }
            if (comboWcEdit.getItemCount() > 0) {
                if (selEdit != null) {
                    comboWcEdit.setSelectedItem(selEdit);
                }
                if (comboWcEdit.getSelectedIndex() < 0) {
                    comboWcEdit.setSelectedIndex(0);
                }
                int idx = comboWcEdit.getSelectedIndex();
                if (idx >= 0 && idx < wcEditIndexList.size()) {
                    int ci = wcEditIndexList.get(idx);
                    if (ci >= 0 && ci < all.size() && all.get(ci) != null) {
                        tfWcName.setText(com.lonebot.example.woodcutter.WcCenters.displayName(all.get(ci)));
                    }
                }
            } else {
                tfWcName.setText("");
            }
        };
        Runnable refreshWcCenterUi = () -> refreshWcCenterUiHolder[0].run();

        JButton btnResetWcCenters = new JButton("Reset WC centers (defaults)");
        btnResetWcCenters.setToolTipText("Herstel Draynor / Port Sarim / yew-edge defaults");
        btnResetWcCenters.addActionListener(e -> {
            LoneBotBootstrapPlugin.resetWcCentersToDefault();
            refreshWcCenterUi.run();
            suppressWcLocWrite.set(true);
            try {
                comboWcLoc.setSelectedItem("AUTO");
            } finally {
                suppressWcLocWrite.set(false);
            }
            configManager.setConfiguration("lonebot", "wcLocation", "AUTO");
            LoneBotBootstrapPlugin.applyWcSettings(config);
            setStatus("WC centers → defaults");
        });

        comboWcEdit.addActionListener(e -> {
            int idx = comboWcEdit.getSelectedIndex();
            if (idx < 0 || idx >= wcEditIndexList.size()) {
                return;
            }
            String blob = LoneBotBootstrapPlugin.liveWcCentersBlob();
            java.util.List<com.lonebot.example.woodcutter.WcCenters.Center> all =
                    com.lonebot.example.woodcutter.WcCenters.parseAll(blob);
            int ci = wcEditIndexList.get(idx);
            if (ci >= 0 && ci < all.size() && all.get(ci) != null) {
                tfWcName.setText(com.lonebot.example.woodcutter.WcCenters.displayName(all.get(ci)));
            }
        });

        JButton btnSaveWcName = new JButton("Opslaan naam");
        btnSaveWcName.setToolTipText("Sla de naam van het geselecteerde center op");
        btnSaveWcName.addActionListener(e -> {
            int idx = comboWcEdit.getSelectedIndex();
            if (idx < 0 || idx >= wcEditIndexList.size()) {
                setStatus("Geen center geselecteerd");
                return;
            }
            int ci = wcEditIndexList.get(idx);
            String newName = tfWcName.getText() != null ? tfWcName.getText().trim() : "";
            String blob = LoneBotBootstrapPlugin.liveWcCentersBlob();
            String next = com.lonebot.example.woodcutter.WcCenters.renameAt(blob, ci, newName);
            LoneBotBootstrapPlugin.persistWcCenters(next);
            String saved = com.lonebot.example.woodcutter.WcCenters.displayName(
                    com.lonebot.example.woodcutter.WcCenters.parseAll(next).get(ci));
            configManager.setConfiguration("lonebot", "wcLocation", saved);
            refreshWcCenterUi.run();
            suppressWcLocWrite.set(true);
            try {
                comboWcLoc.setSelectedItem(saved);
            } finally {
                suppressWcLocWrite.set(false);
            }
            LoneBotBootstrapPlugin.applyWcSettings(config);
            setStatus("WC center hernoemd → " + saved);
        });

        JButton btnRefreshWcLoc = new JButton("Vernieuw locatie-lijst");
        btnRefreshWcLoc.setToolTipText("Herlaad namen uit opgeslagen wcCenters (na in-game edits)");
        btnRefreshWcLoc.addActionListener(e -> {
            refreshWcCenterUi.run();
            setStatus("WC locatie-lijst vernieuwd ("
                    + com.lonebot.example.woodcutter.WcCenters.parseAll(
                    LoneBotBootstrapPlugin.liveWcCentersBlob()).size() + " centers)");
        });

        SwingUtilities.invokeLater(refreshWcCenterUi);

        JCheckBox cbCombatCentersMenu = new JCheckBox("Rechtermenu: Combat centers", config.combatCentersMenuEnabled());
        styleCheck(cbCombatCentersMenu);
        cbCombatCentersMenu.addActionListener(e ->
                configManager.setConfiguration("lonebot", "combatCentersMenuEnabled", cbCombatCentersMenu.isSelected()));
        JCheckBox cbCombatAreaOv = new JCheckBox("Combat area overlay", config.showCombatAreaOverlay());
        styleCheck(cbCombatAreaOv);
        cbCombatAreaOv.addActionListener(e ->
                configManager.setConfiguration("lonebot", "showCombatAreaOverlay", cbCombatAreaOv.isSelected()));

        JLabel combatLocLabel = new JLabel("Combat locatie:");
        styleLabel(combatLocLabel);
        final java.util.concurrent.atomic.AtomicBoolean suppressCombatLocWrite =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        JComboBox<String> comboCombatLoc = newAreaLocationCombo(
                AreaCenters.Skill.COMBAT, "combatLocation", config.combatLocation(),
                suppressCombatLocWrite, null);
        AreaCenterRenameUi combatRename = wireAreaCenterRename(
                AreaCenters.Skill.COMBAT, "combatLocation", comboCombatLoc, suppressCombatLocWrite, null);
        JButton btnRefreshCombatLoc = new JButton("Vernieuw locatie-lijst");
        final Runnable[] refreshCombatLocHolder = new Runnable[1];
        refreshCombatLocHolder[0] = combatRename.refresh;
        btnRefreshCombatLoc.addActionListener(e -> {
            refreshCombatLocHolder[0].run();
            setStatus("Combat locatie-lijst vernieuwd");
        });
        JButton btnResetCombat = new JButton("Reset Combat centers");
        btnResetCombat.addActionListener(e -> {
            LoneBotBootstrapPlugin.resetAreaCentersToDefault(AreaCenters.Skill.COMBAT);
            refreshCombatLocHolder[0].run();
            setStatus("Combat centers → Varrock guards default");
        });

        JCheckBox cbMiningCentersMenu = new JCheckBox("Rechtermenu: Mining centers", config.miningCentersMenuEnabled());
        styleCheck(cbMiningCentersMenu);
        cbMiningCentersMenu.addActionListener(e ->
                configManager.setConfiguration("lonebot", "miningCentersMenuEnabled", cbMiningCentersMenu.isSelected()));
        JCheckBox cbMiningAreaOv = new JCheckBox("Mining area overlay", config.showMiningAreaOverlay());
        styleCheck(cbMiningAreaOv);
        cbMiningAreaOv.addActionListener(e ->
                configManager.setConfiguration("lonebot", "showMiningAreaOverlay", cbMiningAreaOv.isSelected()));

        JLabel miningLocLabel = new JLabel("Mining locatie:");
        styleLabel(miningLocLabel);
        final java.util.concurrent.atomic.AtomicBoolean suppressMiningLocWrite =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        JComboBox<String> comboMiningLoc = newAreaLocationCombo(
                AreaCenters.Skill.MINING, "miningLocation", config.miningLocation(),
                suppressMiningLocWrite, null);
        AreaCenterRenameUi miningRename = wireAreaCenterRename(
                AreaCenters.Skill.MINING, "miningLocation", comboMiningLoc, suppressMiningLocWrite, null);
        JButton btnRefreshMiningLoc = new JButton("Vernieuw locatie-lijst");
        final Runnable[] refreshMiningLocHolder = new Runnable[1];
        refreshMiningLocHolder[0] = miningRename.refresh;
        btnRefreshMiningLoc.addActionListener(e -> {
            refreshMiningLocHolder[0].run();
            setStatus("Mining locatie-lijst vernieuwd");
        });
        JButton btnResetMining = new JButton("Reset Mining centers");
        btnResetMining.addActionListener(e -> {
            LoneBotBootstrapPlugin.resetAreaCentersToDefault(AreaCenters.Skill.MINING);
            refreshMiningLocHolder[0].run();
            setStatus("Mining centers → CombatBot defaults");
        });

        JCheckBox cbFishingCentersMenu = new JCheckBox("Rechtermenu: Fishing centers", config.fishingCentersMenuEnabled());
        styleCheck(cbFishingCentersMenu);
        cbFishingCentersMenu.addActionListener(e ->
                configManager.setConfiguration("lonebot", "fishingCentersMenuEnabled", cbFishingCentersMenu.isSelected()));
        JCheckBox cbFishingAreaOv = new JCheckBox("Fishing area overlay", config.showFishingAreaOverlay());
        styleCheck(cbFishingAreaOv);
        cbFishingAreaOv.addActionListener(e ->
                configManager.setConfiguration("lonebot", "showFishingAreaOverlay", cbFishingAreaOv.isSelected()));

        JLabel fishLocLabel = new JLabel("Fishing locatie:");
        styleLabel(fishLocLabel);
        final java.util.concurrent.atomic.AtomicBoolean suppressFishLocWrite =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        JComboBox<String> comboFishLoc = newAreaLocationCombo(
                AreaCenters.Skill.FISHING, "fishingLocation", config.fishingLocation(),
                suppressFishLocWrite, () -> LoneBotBootstrapPlugin.applyFishSettings(config));
        AreaCenterRenameUi fishRename = wireAreaCenterRename(
                AreaCenters.Skill.FISHING, "fishingLocation", comboFishLoc, suppressFishLocWrite,
                () -> LoneBotBootstrapPlugin.applyFishSettings(config));
        JButton btnRefreshFishLoc = new JButton("Vernieuw locatie-lijst");
        final Runnable[] refreshFishLocHolder = new Runnable[1];
        refreshFishLocHolder[0] = fishRename.refresh;
        btnRefreshFishLoc.addActionListener(e -> {
            refreshFishLocHolder[0].run();
            setStatus("Fishing locatie-lijst vernieuwd");
        });
        JButton btnResetFishing = new JButton("Reset Fishing centers");
        btnResetFishing.addActionListener(e -> {
            LoneBotBootstrapPlugin.resetAreaCentersToDefault(AreaCenters.Skill.FISHING);
            refreshFishLocHolder[0].run();
            LoneBotBootstrapPlugin.applyFishSettings(config);
            setStatus("Fishing centers → Barbarian/Draynor defaults");
        });
        SwingUtilities.invokeLater(() -> {
            refreshCombatLocHolder[0].run();
            refreshMiningLocHolder[0].run();
            refreshFishLocHolder[0].run();
        });

        cbWc.addActionListener(e -> {
            boolean on = cbWc.isSelected();
            if (on) {
                cbImps.setSelected(false);
                cbCow.setSelected(false);
                cbMonk.setSelected(false);
                cbGiants.setSelected(false);
                cbFish.setSelected(false);
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.WOODCUTTING);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                LoneBotBootstrapPlugin.applyWcSettings(config);
                LoneBotBotControl.start();
                setStatus("✓ Woodcutting AAN — overlay: WC");
            } else {
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                setStatus("Woodcutting uit");
            }
            persistAccountSkills(cbImps.isSelected(), cbCow.isSelected(), on, cbFish.isSelected());
        });

        cbFish.addActionListener(e -> {
            boolean on = cbFish.isSelected();
            if (on) {
                cbImps.setSelected(false);
                cbCow.setSelected(false);
                cbMonk.setSelected(false);
                cbGiants.setSelected(false);
                cbWc.setSelected(false);
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.FISHING);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                LoneBotBootstrapPlugin.applyFishSettings(config);
                LoneBotBotControl.start();
                setStatus("✓ Fishing AAN — overlay: Fish");
            } else {
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                setStatus("Fishing uit");
            }
            persistAccountSkills(cbImps.isSelected(), cbCow.isSelected(), cbWc.isSelected(), on);
        });

        // Kritiek: checkbox-init mag NOOIT beide flags true zetten (stale config OR-runtime).
        applyExclusiveSkillCheckboxes(cbImps, cbCow, cbMonk, cbGiants, cbWc, cbFish);

        JCheckBox cbImpDebug = new JCheckBox("Imp overlay (grond)", config.impDebugOverlay());
        styleCheck(cbImpDebug);
        cbImpDebug.setToolTipText("Hunt/rally/zones + looppad. Status-panel blijft via Display Overlay.");
        cbImpDebug.addActionListener(e -> {
            boolean on = cbImpDebug.isSelected();
            configManager.setConfiguration("lonebot", "impDebugOverlay", on);
            configManager.setConfiguration("lonebot", "showImpsHuntOverlay", on);
            if (!on) {
                configManager.setConfiguration("lonebot", "impsShowRallyRadius", false);
                configManager.setConfiguration("lonebot", "impsShowGoblinCoinOverlay", false);
            }
            BotRuntime.impDebugOverlayEnabled = on;
            setStatus(on ? "✓ Imp grond-overlay AAN" : "Imp grond-overlay uit (status blijft)");
        });
        BotRuntime.impDebugOverlayEnabled = cbImpDebug.isSelected();
        LoneBotConfigUiSync.bool(cbImpDebug, "impDebugOverlay");
        LoneBotConfigUiSync.bool(cbImpDebug, "showImpsHuntOverlay");

        JCheckBox cbWcDebug = new JCheckBox("WC overlay (grond)", config.wcDebugOverlay());
        styleCheck(cbWcDebug);
        cbWcDebug.setToolTipText("WC zones + looppad. Status-panel blijft via Display Overlay.");
        cbWcDebug.addActionListener(e -> {
            boolean on = cbWcDebug.isSelected();
            configManager.setConfiguration("lonebot", "wcDebugOverlay", on);
            configManager.setConfiguration("lonebot", "showWcOverlay", on);
            BotRuntime.wcDebugOverlayEnabled = on;
            setStatus(on ? "✓ WC grond-overlay AAN" : "WC grond-overlay uit (status blijft)");
        });
        BotRuntime.wcDebugOverlayEnabled = cbWcDebug.isSelected();
        LoneBotConfigUiSync.bool(cbWcDebug, "wcDebugOverlay");
        LoneBotConfigUiSync.bool(cbWcDebug, "showWcOverlay");

        JCheckBox cbFishDebug = new JCheckBox("Fishing overlay (grond)", config.fishDebugOverlay());
        styleCheck(cbFishDebug);
        cbFishDebug.setToolTipText("Fishing zones. Status-panel blijft via Display Overlay.");
        cbFishDebug.addActionListener(e -> {
            boolean on = cbFishDebug.isSelected();
            configManager.setConfiguration("lonebot", "fishDebugOverlay", on);
            configManager.setConfiguration("lonebot", "showFishingAreaOverlay", on);
            BotRuntime.fishDebugOverlayEnabled = on;
            setStatus(on ? "✓ Fishing grond-overlay AAN" : "Fishing grond-overlay uit (status blijft)");
        });
        BotRuntime.fishDebugOverlayEnabled = cbFishDebug.isSelected();
        LoneBotConfigUiSync.bool(cbFishDebug, "fishDebugOverlay");
        LoneBotConfigUiSync.bool(cbFishDebug, "showFishingAreaOverlay");

        JCheckBox cbStarDebug = new JCheckBox("Star debug overlay", config.starDebugOverlay());
        styleCheck(cbStarDebug);
        cbStarDebug.setToolTipText("Apart Star Miner canvas (feed, doel, mining). Chip om te verbergen.");
        cbStarDebug.addActionListener(e -> {
            boolean on = cbStarDebug.isSelected();
            configManager.setConfiguration("lonebot", "starDebugOverlay", on);
            BotRuntime.starDebugOverlayEnabled = on;
            setStatus(on ? "✓ Star debug overlay AAN" : "Star debug overlay uit");
        });
        BotRuntime.starDebugOverlayEnabled = cbStarDebug.isSelected();
        LoneBotConfigUiSync.bool(cbStarDebug, "starDebugOverlay");

        JCheckBox cbGiantsDebug = new JCheckBox("Giants overlay", config.giantsDebugOverlay());
        styleCheck(cbGiantsDebug);
        cbGiantsDebug.setToolTipText("Giants status tijdens hunt. Verborgen bij open bank/GE.");
        cbGiantsDebug.addActionListener(e -> {
            boolean on = cbGiantsDebug.isSelected();
            configManager.setConfiguration("lonebot", "giantsDebugOverlay", on);
            BotRuntime.giantsDebugOverlayEnabled = on;
            setStatus(on ? "✓ Giants overlay AAN" : "Giants overlay uit");
        });
        BotRuntime.giantsDebugOverlayEnabled = cbGiantsDebug.isSelected();
        LoneBotConfigUiSync.bool(cbGiantsDebug, "giantsDebugOverlay");

        JCheckBox cbWcDebugMin = new JCheckBox("WC overlay geminimaliseerd", OverlayMinimizeStore.isMinimized("wc"));
        styleCheck(cbWcDebugMin);
        cbWcDebugMin.setToolTipText("Alleen groene chip. Klik de chip in-game om te togglen.");
        cbWcDebugMin.addActionListener(e -> {
            OverlayMinimizeStore.setMinimized("wc", cbWcDebugMin.isSelected());
            setStatus(cbWcDebugMin.isSelected() ? "✓ WC overlay geminimaliseerd" : "WC overlay uitgeklapt");
        });

        JCheckBox cbCowDebug = new JCheckBox("Cow debug overlay", config.cowDebugOverlay());
        styleCheck(cbCowDebug);
        cbCowDebug.setToolTipText("Apart Cow Combat canvas met script-status.");
        cbCowDebug.addActionListener(e -> {
            boolean on = cbCowDebug.isSelected();
            configManager.setConfiguration("lonebot", "cowDebugOverlay", on);
            BotRuntime.cowDebugOverlayEnabled = on;
            setStatus(on ? "✓ Cow debug overlay AAN" : "Cow debug overlay uit");
        });
        BotRuntime.cowDebugOverlayEnabled = cbCowDebug.isSelected();
        LoneBotConfigUiSync.bool(cbCowDebug, "cowDebugOverlay");

        JComboBox<com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle> comboImpStyle =
                new JComboBox<>(com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle.values());
        comboImpStyle.setSelectedItem(config.impsCombatStyle());
        LoneBotConfigUiSync.combo(comboImpStyle, "impsCombatStyle");
        comboImpStyle.setToolTipText("Imps combat style");
        comboImpStyle.addActionListener(e -> {
            com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle s =
                    (com.lonebot.example.imps.ImpsTypes.ImpsCombatStyle) comboImpStyle.getSelectedItem();
            if (s != null) {
                configManager.setConfiguration("lonebot", "impsCombatStyle", s.name());
                LoneBotBootstrapPlugin.applyImpSettings(config);
                setStatus("Imps style → " + s.name());
            }
        });

        JCheckBox cbImpAvoid = new JCheckBox("Imps avoid scorpions", config.impsAvoidScorpions());
        styleCheck(cbImpAvoid);
        cbImpAvoid.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "impsAvoidScorpions", cbImpAvoid.isSelected());
            LoneBotBootstrapPlugin.applyImpSettings(config);
        });

        JLabel scorpRLabel = new JLabel("Scorpion radius: " + config.impsScorpionAvoidRadius());
        styleLabel(scorpRLabel);
        JSlider scorpRSlider = new JSlider(1, 15, config.impsScorpionAvoidRadius());
        scorpRSlider.setOpaque(false);
        scorpRSlider.setMajorTickSpacing(2);
        scorpRSlider.setPaintTicks(true);
        scorpRSlider.setToolTipText("Tiles rond vaste zones én live scorpions — geen loot/hunt in radius");
        scorpRSlider.addChangeListener(e -> {
            int r = scorpRSlider.getValue();
            scorpRLabel.setText("Scorpion radius: " + r);
            if (!scorpRSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "impsScorpionAvoidRadius", r);
                LoneBotBootstrapPlugin.applyImpSettings(config);
                setStatus("Scorpion radius → " + r);
            }
        });

        JCheckBox cbImpGe = new JCheckBox("Imps GE sell", config.impsGeSellEnabled());
        styleCheck(cbImpGe);
        cbImpGe.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "impsGeSellEnabled", cbImpGe.isSelected());
            LoneBotBootstrapPlugin.applyImpSettings(config);
        });

        JCheckBox cbImpGear = new JCheckBox("Imp gear prep", config.impsGearPrepEnabled());
        styleCheck(cbImpGear);
        cbImpGear.setToolTipText("Bank withdraw/equip voor combat style");
        cbImpGear.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "impsGearPrepEnabled", cbImpGear.isSelected());
            LoneBotBootstrapPlugin.applyImpSettings(config);
        });

        JCheckBox cbImpGeBuy = new JCheckBox("Imp GE restock", config.impsGeRestockEnabled());
        styleCheck(cbImpGeBuy);
        cbImpGeBuy.setToolTipText("GE-koop ammo/runes/staff bij tekort");
        cbImpGeBuy.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "impsGeRestockEnabled", cbImpGeBuy.isSelected());
            LoneBotBootstrapPlugin.applyImpSettings(config);
        });

        JCheckBox cbImpQuest = new JCheckBox("Imp quest loot", config.impsQuestLootEnabled());
        styleCheck(cbImpQuest);
        cbImpQuest.setToolTipText("Hammer / Cadava / Clay / Ball of wool");
        cbImpQuest.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "impsQuestLootEnabled", cbImpQuest.isSelected());
            LoneBotBootstrapPlugin.applyImpSettings(config);
        });

        JCheckBox cbImpAshHum = new JCheckBox("Imp ash humanize", config.impsAshHumanize());
        styleCheck(cbImpAshHum);
        cbImpAshHum.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "impsAshHumanize", cbImpAshHum.isSelected());
            LoneBotBootstrapPlugin.applyImpSettings(config);
        });

        JCheckBox cbImpAirOpen = new JCheckBox("Imp melee Air Strike open", config.impsMeleeOpeningAirStrike());
        styleCheck(cbImpAirOpen);
        cbImpAirOpen.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "impsMeleeOpeningAirStrike", cbImpAirOpen.isSelected());
            LoneBotBootstrapPlugin.applyImpSettings(config);
        });

        JCheckBox cbImpHuntOverlay = new JCheckBox("Imp hunting overlay", config.showImpsHuntOverlay());
        styleCheck(cbImpHuntOverlay);
        cbImpHuntOverlay.setToolTipText("Teken 3 hunt-zones + scorpion + rally (CombatBot-stijl)");
        cbImpHuntOverlay.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "showImpsHuntOverlay", cbImpHuntOverlay.isSelected());
            setStatus(cbImpHuntOverlay.isSelected() ? "✓ Imp hunting overlay AAN" : "Imp hunting overlay uit");
        });
        LoneBotConfigUiSync.bool(cbImpAvoid, "impsAvoidScorpions");
        LoneBotConfigUiSync.slider(scorpRSlider, scorpRLabel, "impsScorpionAvoidRadius");
        LoneBotConfigUiSync.bool(cbImpGe, "impsGeSellEnabled");
        LoneBotConfigUiSync.bool(cbImpGear, "impsGearPrepEnabled");
        LoneBotConfigUiSync.bool(cbImpGeBuy, "impsGeRestockEnabled");
        LoneBotConfigUiSync.bool(cbImpQuest, "impsQuestLootEnabled");
        LoneBotConfigUiSync.bool(cbImpAshHum, "impsAshHumanize");
        LoneBotConfigUiSync.bool(cbImpAirOpen, "impsMeleeOpeningAirStrike");
        LoneBotConfigUiSync.bool(cbImpHuntOverlay, "showImpsHuntOverlay");

        JLabel huntRLabel = new JLabel("Hunt radius: " + config.impsHuntingRadius());
        styleLabel(huntRLabel);
        JSlider huntRSlider = new JSlider(5, 40, config.impsHuntingRadius());
        huntRSlider.setOpaque(false);
        huntRSlider.setMajorTickSpacing(5);
        huntRSlider.setPaintTicks(true);
        huntRSlider.setToolTipText("Radius voor main + extra zones (zelfde als handler)");
        huntRSlider.addChangeListener(e -> {
            int r = huntRSlider.getValue();
            huntRLabel.setText("Hunt radius: " + r);
            if (!huntRSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "impsHuntingRadius", r);
                LoneBotBootstrapPlugin.applyImpSettings(config);
                setStatus("Hunt radius → " + r);
            }
        });
        LoneBotConfigUiSync.slider(huntRSlider, huntRLabel, "impsHuntingRadius");

        JCheckBox cbVarrockBank = new JCheckBox("Test: Varrock East bank", BotRuntime.varrockEastBankTestEnabled);
        cbVarrockBank.setOpaque(false);
        cbVarrockBank.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        cbVarrockBank.setToolTipText("Lopen naar Varrock East (3253,3422) + bank openen — stopt automatisch bij open bank");
        cbVarrockBank.addActionListener(e -> {
            boolean on = cbVarrockBank.isSelected();
            BotRuntime.varrockEastBankTestEnabled = on;
            if (on) {
                BotRuntime.cityCircleTestEnabled = false;
                BotRuntime.stopImps2WalkTest();
                cbCow.setSelected(false);
                cbMonk.setSelected(false);
                cbImps.setSelected(false);
                cbGiants.setSelected(false);
                cbWc.setSelected(false);
                cbFish.setSelected(false);
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                LoneBotBotControl.start();
                BotRuntime.bankTestStatus = "starten…";
                setStatus("✓ Varrock East bank-test AAN — overlay: Bank test (werkt vanaf elke floor/grot via climb)");
            } else {
                BotRuntime.bankTestStatus = "uit";
                setStatus("Varrock East bank-test uit");
            }
        });
        // Keep checkbox in sync when test auto-finishes / mutually exclusive tests
        javax.swing.Timer bankTestSync = new javax.swing.Timer(500, ev -> {
            boolean en = BotRuntime.varrockEastBankTestEnabled;
            if (cbVarrockBank.isSelected() != en) {
                cbVarrockBank.setSelected(en);
            }
        });
        bankTestSync.setRepeats(true);
        bankTestSync.start();

        JCheckBox cbCityCircle = new JCheckBox("Test: F2P banken-cirkel", BotRuntime.cityCircleTestEnabled);
        styleCheck(cbCityCircle);
        cbCityCircle.setToolTipText(
                "Lumb boven → Al Kharid (Pay-toll) → Varrock E/W → GE → Edge → Falador E/W → Draynor. Start bij dichtstbijzijnde.");
        cbCityCircle.addActionListener(e -> {
            boolean on = cbCityCircle.isSelected();
            BotRuntime.cityCircleTestEnabled = on;
            if (on) {
                BotRuntime.geTradeTestEnabled = false;
                BotRuntime.varrockEastBankTestEnabled = false;
                BotRuntime.stopImps2WalkTest();
                cbCow.setSelected(false);
                cbMonk.setSelected(false);
                cbImps.setSelected(false);
                cbGiants.setSelected(false);
                cbVarrockBank.setSelected(false);
                cbWc.setSelected(false);
                cbFish.setSelected(false);
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                LoneBotBotControl.start();
                BotRuntime.cityCircleStatus = "starten…";
                setStatus("✓ F2P banken-cirkel AAN — start bij dichtstbijzijnde bank (Lumb = plane 2)");
            } else {
                BotRuntime.cityCircleStatus = "uit";
                BotRuntime.debugPath = null;
                BotRuntime.debugTarget = null;
                setStatus("F2P banken-cirkel uit");
            }
        });
        javax.swing.Timer circleSync = new javax.swing.Timer(500, ev -> {
            boolean circ = BotRuntime.cityCircleTestEnabled;
            if (cbCityCircle.isSelected() != circ) {
                cbCityCircle.setSelected(circ);
            }
        });
        circleSync.setRepeats(true);
        circleSync.start();

        JCheckBox cbImps2Walk = new JCheckBox("Imps2 (walk-test)", BotRuntime.imps2Enabled);
        styleCheck(cbImps2Walk);
        JCheckBox cbImps2Clue = new JCheckBox("Clues doen (scroll/container → solver)", config.imps2ClueSolver());
        styleCheck(cbImps2Clue);
        cbImps2Clue.setToolTipText("Oppakken → openen → clue solver → daarna Imps2 hervatten");
        cbImps2Clue.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "imps2ClueSolver", cbImps2Clue.isSelected());
            BotRuntime.imps2ClueSolver = cbImps2Clue.isSelected();
        });
        LoneBotConfigUiSync.bool(cbImps2Clue, "imps2ClueSolver");
        BotRuntime.imps2ClueSolver = cbImps2Clue.isSelected();
        cbImps2Walk.setToolTipText(
                "Van overal naar Port Sarim dock, boot naar Musa, lopen naar imps, "
                        + "terug naar Port Sarim dock, door naar Draynor. 30gp voor de boot.");
        JCheckBox cbImps2Script = new JCheckBox(
                skillCheckLabel("Imps2 (walk-test)", livePluginVersion(BotRuntime.imps2PluginVersion,
                        "com.lonebot.example.Imps2Plugin")),
                BotRuntime.imps2Enabled);
        styleCheck(cbImps2Script);
        cbImps2Script.setToolTipText(cbImps2Walk.getToolTipText());

        java.util.function.Consumer<Boolean> applyImps2 = on -> {
            if (on) {
                BotRuntime.varrockEastBankTestEnabled = false;
                BotRuntime.cityCircleTestEnabled = false;
                cbVarrockBank.setSelected(false);
                cbCityCircle.setSelected(false);
                cbCow.setSelected(false);
                cbMonk.setSelected(false);
                cbImps.setSelected(false);
                cbGiants.setSelected(false);
                cbWc.setSelected(false);
                cbFish.setSelected(false);
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.IMP2);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                Imps2Plugin.resetLive();
                LoneBotBotControl.start();
                BotRuntime.imps2Status = "starten…";
                setStatus("✓ Imps2 AAN — imps → Port Sarim dock → Draynor");
            } else {
                BotRuntime.stopImps2WalkTest();
                BotRuntime.debugPath = null;
                BotRuntime.debugTarget = null;
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                setStatus("Imps2 uit");
            }
        };
        cbImps2Walk.addActionListener(e -> applyImps2.accept(cbImps2Walk.isSelected()));
        cbImps2Script.addActionListener(e -> applyImps2.accept(cbImps2Script.isSelected()));
        javax.swing.Timer imps2Sync = new javax.swing.Timer(500, ev -> {
            boolean on = BotRuntime.imps2Enabled;
            if (cbImps2Walk.isSelected() != on) {
                cbImps2Walk.setSelected(on);
            }
            if (cbImps2Script.isSelected() != on) {
                cbImps2Script.setSelected(on);
            }
            setCheckText(cbImps2Script, skillCheckLabel("Imps2 (walk-test)",
                    livePluginVersion(BotRuntime.imps2PluginVersion, "com.lonebot.example.Imps2Plugin")));
        });
        imps2Sync.setRepeats(true);
        imps2Sync.start();

        cbGiants.addActionListener(e -> {
            boolean on = cbGiants.isSelected();
            if (on) {
                cbCow.setSelected(false);
                cbMonk.setSelected(false);
                cbImps.setSelected(false);
                cbWc.setSelected(false);
                cbFish.setSelected(false);
                cbImps2Walk.setSelected(false);
                cbImps2Script.setSelected(false);
                BotRuntime.stopImps2WalkTest();
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.GIANTS);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                LoneBotBootstrapPlugin.applyGiantsSettingsFromUi();
                String acc = LoneBotBotControl.launchedAccountName();
                if (acc != null) {
                    ManagedAccountsStore.setPreferredScript(acc, "GIANTS");
                }
                setStatus("✓ Giants AAN — Hill Giants Edgeville Dungeon");
            } else {
                BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
                LoneBotBotControl.syncSkillFlagsToConfig(configManager);
                persistAccountSkills(cbImps.isSelected(), cbCow.isSelected(), cbWc.isSelected(), cbFish.isSelected());
                setStatus("Giants uit");
            }
        });
        if (cbGiants.isSelected()) {
            LoneBotBootstrapPlugin.applyGiantsSettingsFromUi();
        }

        JCheckBox cbCanvasDebug = new JCheckBox("Canvas debug", config.canvasDebugOverlay() || BotRuntime.canvasDebugEnabled);
        styleCheck(cbCanvasDebug);
        cbCanvasDebug.setToolTipText("City-ring markers op de canvas (geen pad/CLICK)");
        cbCanvasDebug.addActionListener(e -> {
            boolean on = cbCanvasDebug.isSelected();
            BotRuntime.canvasDebugEnabled = on;
            configManager.setConfiguration("lonebot", "canvasDebugOverlay", on);
            setStatus(on ? "✓ Canvas debug AAN" : "Canvas debug uit");
        });
        BotRuntime.canvasDebugEnabled = cbCanvasDebug.isSelected();

        // ---- ▶ Bot Control (CombatBotPanel) ----
        JCheckBox cbBotEnabled = new JCheckBox("Bot inschakelen", BotRuntime.botEnabled);
        styleCheck(cbBotEnabled);
        cbBotEnabled.setToolTipText("Master bot — sync met Accounts Besturing");
        cbBotEnabled.addActionListener(e -> {
            boolean on = cbBotEnabled.isSelected();
            if (on) {
                LoneBotBotControl.start();
            } else {
                LoneBotBotControl.pause();
            }
            LoneBotBotControl.syncBotEnabledConfig(configManager, on);
            setStatus(on ? "✓ Bot AAN" : "Bot pauze");
        });

        JCheckBox cbLargeSteps = new JCheckBox("Grote loopstappen 15-20 (globaal)", config.walkForceLargeSteps());
        styleCheck(cbLargeSteps);
        cbLargeSteps.setToolTipText("Zelfde als Walk: grote stappen — globaal vanuit Bot Control");
        cbLargeSteps.addActionListener(e -> {
            boolean on = cbLargeSteps.isSelected();
            configManager.setConfiguration("lonebot", "walkForceLargeSteps", on);
            WalkClickSettings.forceLargeSteps = on;
            setStatus("Grote loopstappen → " + on);
        });

        JCheckBox cbResetTimers = new JCheckBox("Reset per-account timers bij stop", config.resetAccountTimersOnStop());
        styleCheck(cbResetTimers);
        cbResetTimers.addActionListener(e ->
                configManager.setConfiguration("lonebot", "resetAccountTimersOnStop", cbResetTimers.isSelected()));
        LoneBotConfigUiSync.bool(cbResetTimers, "resetAccountTimersOnStop");

        JCheckBox cbLoopWatch = new JCheckBox("LoopWatch: herstel bij loop", config.loopWatchRecoveryEnabled());
        styleCheck(cbLoopWatch);
        cbLoopWatch.setToolTipText("<html>Bij vaste skill+status+tile te lang:<br>"
                + "± herstel-sec zacht herstel, ± logout-sec bot uit + logout.</html>");
        cbLoopWatch.addActionListener(e ->
                configManager.setConfiguration("lonebot", "loopWatchRecoveryEnabled", cbLoopWatch.isSelected()));
        LoneBotConfigUiSync.bool(cbLoopWatch, "loopWatchRecoveryEnabled");

        JLabel lwTriggerLbl = new JLabel("LoopWatch herstel: " + config.loopWatchTriggerSec() + "s");
        styleLabel(lwTriggerLbl);
        JSlider lwTriggerSlider = new JSlider(45, 600, config.loopWatchTriggerSec());
        lwTriggerSlider.setOpaque(false);
        lwTriggerSlider.setMajorTickSpacing(100);
        lwTriggerSlider.setPaintTicks(true);
        lwTriggerSlider.addChangeListener(e -> {
            int v = lwTriggerSlider.getValue();
            lwTriggerLbl.setText("LoopWatch herstel: " + v + "s");
            if (!lwTriggerSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "loopWatchTriggerSec", v);
            }
        });
        LoneBotConfigUiSync.slider(lwTriggerSlider, lwTriggerLbl, "loopWatchTriggerSec");

        JLabel lwLogoutLbl = new JLabel("LoopWatch logout: " + config.loopWatchLogoutSec() + "s");
        styleLabel(lwLogoutLbl);
        JSlider lwLogoutSlider = new JSlider(60, 900, config.loopWatchLogoutSec());
        lwLogoutSlider.setOpaque(false);
        lwLogoutSlider.setMajorTickSpacing(120);
        lwLogoutSlider.setPaintTicks(true);
        lwLogoutSlider.addChangeListener(e -> {
            int v = lwLogoutSlider.getValue();
            lwLogoutLbl.setText("LoopWatch logout: " + v + "s");
            if (!lwLogoutSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "loopWatchLogoutSec", v);
            }
        });
        LoneBotConfigUiSync.slider(lwLogoutSlider, lwLogoutLbl, "loopWatchLogoutSec");

        JCheckBox cbPanelLogout = new JCheckBox("🚪 Uitloggen (wacht → Game.logout)", false);
        styleCheck(cbPanelLogout);
        cbPanelLogout.setToolTipText("Plant uitloggen op client-thread (CombatBot panelLogout)");
        cbPanelLogout.addActionListener(e -> {
            if (cbPanelLogout.isSelected()) {
                PanelLogoutHelper.requestLogout();
                setStatus("🚪 Uitloggen gepland");
                javax.swing.SwingUtilities.invokeLater(() -> cbPanelLogout.setSelected(false));
            }
        });

        JCheckBox cbRelog = new JCheckBox("Re-log inschakelen", config.reLogoutEnabled());
        styleCheck(cbRelog);
        cbRelog.setToolTipText("Na min–max minuten uitloggen en met hetzelfde account weer in (CombatBot).");
        cbRelog.addActionListener(e ->
                configManager.setConfiguration("lonebot", "reLogoutEnabled", cbRelog.isSelected()));
        LoneBotConfigUiSync.bool(cbRelog, "reLogoutEnabled");

        JLabel relogMinLbl = new JLabel("Min. minuten tot re-log: " + config.reLogoutMinMinutes());
        styleLabel(relogMinLbl);
        JSlider relogMinSlider = new JSlider(1, 240, config.reLogoutMinMinutes());
        relogMinSlider.setOpaque(false);
        relogMinSlider.addChangeListener(e -> {
            int v = relogMinSlider.getValue();
            relogMinLbl.setText("Min. minuten tot re-log: " + v);
            if (!relogMinSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "reLogoutMinMinutes", v);
            }
        });
        LoneBotConfigUiSync.slider(relogMinSlider, relogMinLbl, "reLogoutMinMinutes",
                v -> "Min. minuten tot re-log: " + v);

        JLabel relogMaxLbl = new JLabel("Max. minuten tot re-log: " + config.reLogoutMaxMinutes());
        styleLabel(relogMaxLbl);
        JSlider relogMaxSlider = new JSlider(1, 240, config.reLogoutMaxMinutes());
        relogMaxSlider.setOpaque(false);
        relogMaxSlider.addChangeListener(e -> {
            int v = relogMaxSlider.getValue();
            relogMaxLbl.setText("Max. minuten tot re-log: " + v);
            if (!relogMaxSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "reLogoutMaxMinutes", v);
            }
        });
        LoneBotConfigUiSync.slider(relogMaxSlider, relogMaxLbl, "reLogoutMaxMinutes",
                v -> "Max. minuten tot re-log: " + v);

        JLabel relogPauseMinLbl = new JLabel("Pauze min (minuten): " + config.reLogoutPauseMinMinutes());
        styleLabel(relogPauseMinLbl);
        JSlider relogPauseMinSlider = new JSlider(0, 60, config.reLogoutPauseMinMinutes());
        relogPauseMinSlider.setOpaque(false);
        relogPauseMinSlider.addChangeListener(e -> {
            int v = relogPauseMinSlider.getValue();
            relogPauseMinLbl.setText("Pauze min (minuten): " + v);
            if (!relogPauseMinSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "reLogoutPauseMinMinutes", v);
            }
        });
        LoneBotConfigUiSync.slider(relogPauseMinSlider, relogPauseMinLbl, "reLogoutPauseMinMinutes",
                v -> "Pauze min (minuten): " + v);

        JLabel relogPauseMaxLbl = new JLabel("Pauze max (minuten): " + config.reLogoutPauseMaxMinutes());
        styleLabel(relogPauseMaxLbl);
        JSlider relogPauseMaxSlider = new JSlider(0, 90, config.reLogoutPauseMaxMinutes());
        relogPauseMaxSlider.setOpaque(false);
        relogPauseMaxSlider.addChangeListener(e -> {
            int v = relogPauseMaxSlider.getValue();
            relogPauseMaxLbl.setText("Pauze max (minuten): " + v);
            if (!relogPauseMaxSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "reLogoutPauseMaxMinutes", v);
            }
        });
        LoneBotConfigUiSync.slider(relogPauseMaxSlider, relogPauseMaxLbl, "reLogoutPauseMaxMinutes",
                v -> "Pauze max (minuten): " + v);

        JLabel relogAccLbl = new JLabel("Account (re-log):");
        styleLabel(relogAccLbl);
        JTextField relogAccField = new JTextField(
                config.reLogoutAccount() != null ? config.reLogoutAccount() : "", 18);
        relogAccField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        relogAccField.setToolTipText("Leeg = launcher-account. pasted:Naam, jagex:pad, of email:wachtwoord");
        relogAccField.addActionListener(e ->
                configManager.setConfiguration("lonebot", "reLogoutAccount", relogAccField.getText().trim()));
        relogAccField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                configManager.setConfiguration("lonebot", "reLogoutAccount", relogAccField.getText().trim());
            }
        });
        LoneBotConfigUiSync.text(relogAccField, "reLogoutAccount");
        LoneBotUiTheme.mark(relogAccField, LoneBotUiTheme.ROLE_INPUT);

        JCheckBox cbLoginNow = new JCheckBox("🔐 Log in (nu)", false);
        styleCheck(cbLoginNow);
        cbLoginNow.setToolTipText("Op het login-scherm: account zetten + Play Now (CombatBot tryLoginNow)");
        cbLoginNow.addActionListener(e -> {
            if (cbLoginNow.isSelected()) {
                configManager.setConfiguration("lonebot", "loginNow", true);
                setStatus("🔐 Log in (nu)");
                SwingUtilities.invokeLater(() -> cbLoginNow.setSelected(false));
            }
        });

        JLabel playYLbl = new JLabel("Jagex Play Now Y%: " + config.jagexLoginPlayNowCenterYPct());
        styleLabel(playYLbl);
        JSlider playYSlider = new JSlider(35, 70, config.jagexLoginPlayNowCenterYPct());
        playYSlider.setOpaque(false);
        playYSlider.addChangeListener(e -> {
            int v = playYSlider.getValue();
            playYLbl.setText("Jagex Play Now Y%: " + v);
            if (!playYSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "jagexLoginPlayNowCenterYPct", v);
            }
        });
        LoneBotConfigUiSync.slider(playYSlider, playYLbl, "jagexLoginPlayNowCenterYPct");

        JLabel playWLbl = new JLabel("Jagex Play Now breedte %: " + config.jagexLoginPlayNowBtnWidthPct());
        styleLabel(playWLbl);
        JSlider playWSlider = new JSlider(12, 50, config.jagexLoginPlayNowBtnWidthPct());
        playWSlider.setOpaque(false);
        playWSlider.addChangeListener(e -> {
            int v = playWSlider.getValue();
            playWLbl.setText("Jagex Play Now breedte %: " + v);
            if (!playWSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "jagexLoginPlayNowBtnWidthPct", v);
            }
        });
        LoneBotConfigUiSync.slider(playWSlider, playWLbl, "jagexLoginPlayNowBtnWidthPct");

        JLabel playHLbl = new JLabel("Jagex Play Now hoogte %: " + config.jagexLoginPlayNowBtnHeightPct());
        styleLabel(playHLbl);
        JSlider playHSlider = new JSlider(5, 25, config.jagexLoginPlayNowBtnHeightPct());
        playHSlider.setOpaque(false);
        playHSlider.addChangeListener(e -> {
            int v = playHSlider.getValue();
            playHLbl.setText("Jagex Play Now hoogte %: " + v);
            if (!playHSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "jagexLoginPlayNowBtnHeightPct", v);
            }
        });
        LoneBotConfigUiSync.slider(playHSlider, playHLbl, "jagexLoginPlayNowBtnHeightPct");

        JLabel playMapLbl = new JLabel("Jagex Play Now schaal-modus:");
        styleLabel(playMapLbl);
        JComboBox<LoneBotConfig.JagexLoginPlayNowMapMode> comboPlayMap =
                new JComboBox<>(LoneBotConfig.JagexLoginPlayNowMapMode.values());
        comboPlayMap.setSelectedItem(config.jagexLoginPlayNowMapMode());
        comboPlayMap.addActionListener(e -> {
            LoneBotConfig.JagexLoginPlayNowMapMode m =
                    (LoneBotConfig.JagexLoginPlayNowMapMode) comboPlayMap.getSelectedItem();
            if (m != null) {
                configManager.setConfiguration("lonebot", "jagexLoginPlayNowMapMode", m.name());
            }
        });
        LoneBotConfigUiSync.combo(comboPlayMap, "jagexLoginPlayNowMapMode");

        JLabel webGuiLbl = new JLabel("Web GUI URL:");
        styleLabel(webGuiLbl);
        JTextField webGuiField = new JTextField(config.webGuiUrl() != null ? config.webGuiUrl() : "", 18);
        webGuiField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JButton btnOpenWebGui = new JButton("🌐 Open Web GUI");
        btnOpenWebGui.setToolTipText("Opent Web GUI URL in de browser");
        btnOpenWebGui.addActionListener(e -> {
            String url = webGuiField.getText() != null ? webGuiField.getText().trim() : "";
            configManager.setConfiguration("lonebot", "webGuiUrl", url);
            if (url.isEmpty()) {
                setStatus("Web GUI URL leeg");
                return;
            }
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                setStatus("Web GUI geopend");
            } catch (Throwable t) {
                setStatus("Web GUI open mislukt: " + t.getMessage());
            }
        });
        webGuiField.addActionListener(e ->
                configManager.setConfiguration("lonebot", "webGuiUrl", webGuiField.getText().trim()));
        LoneBotUiTheme.mark(webGuiField, LoneBotUiTheme.ROLE_INPUT);
        webGuiField.setBackground(LoneBotUiTheme.INPUT_BG);
        webGuiField.setForeground(LoneBotUiTheme.INPUT_FG);
        webGuiField.setCaretColor(LoneBotUiTheme.INPUT_FG);

        JLabel themeLbl = new JLabel("GUI thema:");
        styleLabel(themeLbl);
        JComboBox<LoneBotUiTheme.Preset> comboTheme = new JComboBox<>(LoneBotUiTheme.Preset.selectable());
        LoneBotUiTheme.Preset themeSel = LoneBotUiTheme.resolve(
                config.uiTheme() != null ? config.uiTheme() : LoneBotUiTheme.Preset.BALANS);
        comboTheme.setSelectedItem(themeSel);
        comboTheme.setToolTipText("Mid-tones: hover blijft leesbaar (hoog contrast)");
        LoneBotUiTheme.mark(comboTheme, LoneBotUiTheme.ROLE_INPUT);
        comboTheme.setBackground(LoneBotUiTheme.INPUT_BG);
        comboTheme.setForeground(LoneBotUiTheme.INPUT_FG);
        comboTheme.addActionListener(e -> {
            LoneBotUiTheme.Preset p = (LoneBotUiTheme.Preset) comboTheme.getSelectedItem();
            if (p == null) {
                return;
            }
            p = LoneBotUiTheme.resolve(p);
            configManager.setConfiguration("lonebot", "uiTheme", p);
            applyUiTheme(p);
            setStatus("GUI thema → " + p);
        });

        JButton btnSwitchNow = new JButton("⚡ Switch Now");
        btnSwitchNow.setToolTipText("Volgende skill (switchNow) — zelfde als Next skill");
        btnSwitchNow.addActionListener(e -> {
            LoneBotBotControl.requestSwitchNow();
            setStatus("Switch Now");
        });

        // ---- Anti-ban (Storm/CombatBot) ----
        JCheckBox cbAb = new JCheckBox("Anti-ban", config.antiBanEnabled());
        styleCheck(cbAb);
        cbAb.setToolTipText("Periodieke camera / idle / muis / tab / misclick");

        JCheckBox cbAbCam = new JCheckBox("AB: camera", config.antiBanCamera());
        styleCheck(cbAbCam);
        JCheckBox cbAbIdle = new JCheckBox("AB: idle pauzes", config.antiBanIdle());
        styleCheck(cbAbIdle);
        JCheckBox cbAbMouse = new JCheckBox("AB: random muis", config.antiBanMouse());
        styleCheck(cbAbMouse);
        JCheckBox cbAbKeys = new JCheckBox("AB: random toetsenbord", config.antiBanKeyboardPan());
        styleCheck(cbAbKeys);
        cbAbKeys.setToolTipText("Pijltje links/rechts — beeld draait. Zeldzamer dan muis, niet tijdens lopen.");
        JCheckBox cbAbMis = new JCheckBox("AB: misclick", config.antiBanMisclick());
        styleCheck(cbAbMis);
        JCheckBox cbAbTab = new JCheckBox("AB: tab glance", config.antiBanTabGlance());
        styleCheck(cbAbTab);
        JCheckBox cbAbFidget = new JCheckBox("AB: continuous fidget", config.antiBanMouseFidget());
        styleCheck(cbAbFidget);
        cbAbFidget.setToolTipText("Achtergrond micro-muis (CombatBot MouseFidgetWorker)");

        JLabel abFreqLabel = new JLabel("AB frequentie: " + config.antiBanFrequencySec() + "s");
        abFreqLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        JSlider abFreqSlider = new JSlider(10, 180, config.antiBanFrequencySec());
        abFreqSlider.setOpaque(false);
        abFreqSlider.setMajorTickSpacing(40);
        abFreqSlider.setPaintTicks(true);

        JCheckBox cbRandomEv = new JCheckBox("Random events", config.randomEventsEnabled());
        styleCheck(cbRandomEv);
        JComboBox<LoneBotConfig.GenieLampSkill> comboGenie =
                new JComboBox<>(LoneBotConfig.GenieLampSkill.values());
        comboGenie.setSelectedItem(config.genieLampSkill());
        comboGenie.setToolTipText("Genie lamp skill — Geen = Genie Dismiss");

        Runnable applyAb = () -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            configManager.setConfiguration("lonebot", "antiBanEnabled", cbAb.isSelected());
            configManager.setConfiguration("lonebot", "antiBanCamera", cbAbCam.isSelected());
            configManager.setConfiguration("lonebot", "antiBanIdle", cbAbIdle.isSelected());
            configManager.setConfiguration("lonebot", "antiBanMouse", cbAbMouse.isSelected());
            configManager.setConfiguration("lonebot", "antiBanKeyboardPan", cbAbKeys.isSelected());
            configManager.setConfiguration("lonebot", "antiBanMisclick", cbAbMis.isSelected());
            configManager.setConfiguration("lonebot", "antiBanTabGlance", cbAbTab.isSelected());
            configManager.setConfiguration("lonebot", "antiBanMouseFidget", cbAbFidget.isSelected());
            configManager.setConfiguration("lonebot", "antiBanFrequencySec", abFreqSlider.getValue());
            configManager.setConfiguration("lonebot", "randomEventsEnabled", cbRandomEv.isSelected());
            LoneBotConfig.GenieLampSkill gs = (LoneBotConfig.GenieLampSkill) comboGenie.getSelectedItem();
            if (gs == null) {
                gs = LoneBotConfig.GenieLampSkill.NONE;
            }
            configManager.setConfiguration("lonebot", "genieLampSkill", gs);
            net.storm.sdk.game.RandomEventSettings.apply(cbRandomEv.isSelected(), gs.name());
            AntiBanSettings.apply(
                    cbAb.isSelected(),
                    cbAbCam.isSelected(),
                    cbAbIdle.isSelected(),
                    cbAbMouse.isSelected(),
                    cbAbMis.isSelected(),
                    cbAbTab.isSelected(),
                    cbAbFidget.isSelected(),
                    cbAbKeys.isSelected(),
                    abFreqSlider.getValue());
            AntiBan.get().restartFidgetWorkerIfNeeded();
            setStatus("Anti-ban → " + (cbAb.isSelected() ? "aan" : "uit")
                    + " | RE " + (cbRandomEv.isSelected() ? "aan" : "uit")
                    + " lamp=" + gs
                    + " freq=" + abFreqSlider.getValue() + "s");
        };
        cbAb.addActionListener(e -> applyAb.run());
        cbAbCam.addActionListener(e -> applyAb.run());
        cbAbIdle.addActionListener(e -> applyAb.run());
        cbAbMouse.addActionListener(e -> applyAb.run());
        cbAbKeys.addActionListener(e -> applyAb.run());
        cbAbMis.addActionListener(e -> applyAb.run());
        cbAbTab.addActionListener(e -> applyAb.run());
        cbAbFidget.addActionListener(e -> applyAb.run());
        cbRandomEv.addActionListener(e -> applyAb.run());
        comboGenie.addActionListener(e -> applyAb.run());
        abFreqSlider.addChangeListener(e -> {
            abFreqLabel.setText("AB frequentie: " + abFreqSlider.getValue() + "s");
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            if (!abFreqSlider.getValueIsAdjusting()) {
                applyAb.run();
            }
        });
        LoneBotConfigUiSync.bool(cbAb, "antiBanEnabled");
        LoneBotConfigUiSync.bool(cbAbCam, "antiBanCamera");
        LoneBotConfigUiSync.bool(cbAbIdle, "antiBanIdle");
        LoneBotConfigUiSync.bool(cbAbMouse, "antiBanMouse");
        LoneBotConfigUiSync.bool(cbAbKeys, "antiBanKeyboardPan");
        LoneBotConfigUiSync.bool(cbAbMis, "antiBanMisclick");
        LoneBotConfigUiSync.bool(cbAbTab, "antiBanTabGlance");
        LoneBotConfigUiSync.bool(cbAbFidget, "antiBanMouseFidget");
        LoneBotConfigUiSync.bool(cbRandomEv, "randomEventsEnabled");
        LoneBotConfigUiSync.combo(comboGenie, "genieLampSkill");
        LoneBotConfigUiSync.slider(abFreqSlider, abFreqLabel, "antiBanFrequencySec");
        AntiBanSettings.apply(
                cbAb.isSelected(),
                cbAbCam.isSelected(),
                cbAbIdle.isSelected(),
                cbAbMouse.isSelected(),
                cbAbMis.isSelected(),
                cbAbTab.isSelected(),
                cbAbFidget.isSelected(),
                cbAbKeys.isSelected(),
                abFreqSlider.getValue());

        // ---- Walk click strategy toggles ----
        JCheckBox cbWalkMini = new JCheckBox("Walk: minimap", config.walkUseMinimap());
        styleCheck(cbWalkMini);
        cbWalkMini.setToolTipText("Voorkeur — minimap-klik is bijna altijd Walk here");
        cbWalkMini.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkUseMinimap", cbWalkMini.isSelected());
            WalkClickSettings.useMinimap = cbWalkMini.isSelected();
            setStatus("Walk minimap → " + cbWalkMini.isSelected());
        });

        JCheckBox cbWalkMiniZoom = new JCheckBox("Walk: minimap volledig uit", config.walkMinimapFullyZoomedOut());
        styleCheck(cbWalkMiniZoom);
        cbWalkMiniZoom.setToolTipText("Minimap helemaal uitzoomen — hops vallen niet op de rand");
        cbWalkMiniZoom.addActionListener(e -> {
            boolean on = cbWalkMiniZoom.isSelected();
            configManager.setConfiguration("lonebot", "walkMinimapFullyZoomedOut", on);
            WalkClickSettings.minimapFullyZoomedOut = on;
            if (on) {
                net.storm.sdk.movement.MinimapZoomHelper.ensureFullyZoomedOut(true);
            }
            setStatus("Minimap zoom-out → " + on);
        });
        WalkClickSettings.minimapFullyZoomedOut = cbWalkMiniZoom.isSelected();

        JCheckBox cbWalkCanvas = new JCheckBox("Walk: canvas", config.walkUseCanvas());
        styleCheck(cbWalkCanvas);
        cbWalkCanvas.setToolTipText("Grond-klik op spelvenster (UI-safe als zones aan)");
        cbWalkCanvas.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkUseCanvas", cbWalkCanvas.isSelected());
            WalkClickSettings.useCanvas = cbWalkCanvas.isSelected();
            setStatus("Walk canvas → " + cbWalkCanvas.isSelected());
        });

        JCheckBox cbWalkUi = new JCheckBox("Walk: UI-zones", config.walkUseUiZones());
        styleCheck(cbWalkUi);
        cbWalkUi.setToolTipText("Geen canvas-klik over chat / inventory / minimap-chrome");
        cbWalkUi.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkUseUiZones", cbWalkUi.isSelected());
            WalkClickSettings.useUiZones = cbWalkUi.isSelected();
            setStatus("Walk UI-zones → " + cbWalkUi.isSelected());
        });

        JCheckBox cbWalkCam = new JCheckBox("Walk: camera nudge", config.walkUseCameraNudge());
        styleCheck(cbWalkCam);
        cbWalkCam.setToolTipText("Storm AntiBan MMB-drag camera (geen setYaw / geen key-pulse)");
        cbWalkCam.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkUseCameraNudge", cbWalkCam.isSelected());
            WalkClickSettings.useCameraNudge = cbWalkCam.isSelected();
            setStatus("Walk camera → " + cbWalkCam.isSelected());
        });

        JCheckBox cbWalkInvoke = new JCheckBox("Walk: invoke WALK", config.walkUseInvoke());
        styleCheck(cbWalkInvoke);
        cbWalkInvoke.setToolTipText("Client.menuAction(WALK) — geen muis, vanilla RL");
        cbWalkInvoke.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkUseInvoke", cbWalkInvoke.isSelected());
            WalkClickSettings.useInvokeWalk = cbWalkInvoke.isSelected();
            setStatus("Walk invoke → " + cbWalkInvoke.isSelected());
        });

        JCheckBox cbWalkObstacleScan = new JCheckBox("Scan deuren/hekken/trappen (achtergrond)", config.walkObstacleScanEnabled());
        styleCheck(cbWalkObstacleScan);
        cbWalkObstacleScan.setToolTipText("Niet nodig om te lopen. Uit = geen scan. Zelfde knop in Developer Tools.");
        cbWalkObstacleScan.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkObstacleScanEnabled", cbWalkObstacleScan.isSelected());
            setStatus(cbWalkObstacleScan.isSelected()
                    ? "✓ Obstacle-scan AAN → ~/.lonebot/walk-obstacle-*.tsv"
                    : "Obstacle-scan uit");
        });

        JCheckBox cbWalkShortcuts = new JCheckBox("Walk: shortcuts (stile)", config.walkUseShortcuts());
        styleCheck(cbWalkShortcuts);
        cbWalkShortcuts.setToolTipText("Uit = stiles overslaan en eromheen lopen. Aan = Climb-over als het pad dat vraagt.");
        cbWalkShortcuts.addActionListener(e -> {
            boolean on = cbWalkShortcuts.isSelected();
            configManager.setConfiguration("lonebot", "walkUseShortcuts", on);
            WalkClickSettings.useShortcuts = on;
            net.storm.sdk.movement.MovementHelper.clearPath();
            setStatus(on ? "Shortcuts AAN" : "Shortcuts uit — eromheen lopen");
        });
        WalkClickSettings.useShortcuts = cbWalkShortcuts.isSelected();

        JCheckBox cbWalkFar = new JCheckBox("Walk: far canvas (lage camera)", config.walkFarCanvas());
        styleCheck(cbWalkFar);
        cbWalkFar.setToolTipText("Pitch omlaag + camera draait mee met looprichting + canvas-first");
        cbWalkFar.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkFarCanvas", cbWalkFar.isSelected());
            WalkClickSettings.useFarCanvasWalk = cbWalkFar.isSelected();
            setStatus(cbWalkFar.isSelected() ? "✓ Far canvas AAN" : "Far canvas uit");
        });

        JCheckBox cbWalkTwin = new JCheckBox("Walk: 2 snelle kliks", config.walkTwinQuickClicks());
        styleCheck(cbWalkTwin);
        cbWalkTwin.setToolTipText("Twin: 2e klik op de volgende padtegel (niet dezelfde)");
        cbWalkTwin.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkTwinQuickClicks", cbWalkTwin.isSelected());
            WalkClickSettings.twinQuickClicks = cbWalkTwin.isSelected();
            setStatus("Twin quick → " + cbWalkTwin.isSelected());
        });

        JCheckBox cbWalkAutoRun = new JCheckBox("Auto-run", config.walkAutoRun());
        styleCheck(cbWalkAutoRun);
        cbWalkAutoRun.setToolTipText("OSRS zet run uit bij 0% energie. Aan = orb weer aanklikken vanaf de drempel.");
        cbWalkAutoRun.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkAutoRun", cbWalkAutoRun.isSelected());
            WalkClickSettings.autoRun = cbWalkAutoRun.isSelected();
            setStatus("Auto-run → " + cbWalkAutoRun.isSelected());
        });
        WalkClickSettings.autoRun = cbWalkAutoRun.isSelected();

        JLabel walkAutoRunLbl = new JLabel("Auto-run vanaf: " + config.walkAutoRunMinEnergy() + "%");
        styleLabel(walkAutoRunLbl);
        JSlider walkAutoRunSlider = new JSlider(5, 80, Math.min(80, Math.max(5, config.walkAutoRunMinEnergy())));
        walkAutoRunSlider.setOpaque(false);
        walkAutoRunSlider.setMajorTickSpacing(15);
        walkAutoRunSlider.setPaintTicks(true);
        walkAutoRunSlider.addChangeListener(e -> {
            int v = walkAutoRunSlider.getValue();
            walkAutoRunLbl.setText("Auto-run vanaf: " + v + "%");
            WalkClickSettings.autoRunMinEnergy = v;
            if (!walkAutoRunSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "walkAutoRunMinEnergy", v);
                setStatus("Auto-run drempel → " + v + "%");
            }
        });
        WalkClickSettings.autoRunMinEnergy = walkAutoRunSlider.getValue();
        LoneBotConfigUiSync.bool(cbWalkMini, "walkUseMinimap");
        LoneBotConfigUiSync.bool(cbWalkMiniZoom, "walkMinimapFullyZoomedOut");
        LoneBotConfigUiSync.bool(cbWalkCanvas, "walkUseCanvas");
        LoneBotConfigUiSync.bool(cbWalkUi, "walkUseUiZones");
        LoneBotConfigUiSync.bool(cbWalkCam, "walkUseCameraNudge");
        LoneBotConfigUiSync.bool(cbWalkInvoke, "walkUseInvoke");
        LoneBotConfigUiSync.bool(cbWalkFar, "walkFarCanvas");
        LoneBotConfigUiSync.bool(cbWalkTwin, "walkTwinQuickClicks");
        LoneBotConfigUiSync.bool(cbWalkAutoRun, "walkAutoRun");
        LoneBotConfigUiSync.slider(walkAutoRunSlider, walkAutoRunLbl, "walkAutoRunMinEnergy");
        LoneBotConfigUiSync.bool(cbWalkObstacleScan, "walkObstacleScanEnabled");
        LoneBotConfigUiSync.bool(cbWalkShortcuts, "walkUseShortcuts");

        JCheckBox cbWalkFlagWait = new JCheckBox("Walk: langer wachten dicht bij flag",
                config.walkFlagProximityReclick());
        styleCheck(cbWalkFlagWait);
        cbWalkFlagWait.setToolTipText("Dichter bij gele flag → langer wachten vóór opnieuw lopen-klik");
        cbWalkFlagWait.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkFlagProximityReclick", cbWalkFlagWait.isSelected());
            WalkClickSettings.flagProximityReclick = cbWalkFlagWait.isSelected();
            setStatus("Flag-proximity reclick → " + cbWalkFlagWait.isSelected());
        });
        LoneBotConfigUiSync.bool(cbWalkFlagWait, "walkFlagProximityReclick");

        JCheckBox cbWalkLarge = new JCheckBox("Walk: grote stappen 15-20", config.walkForceLargeSteps());
        styleCheck(cbWalkLarge);
        cbWalkLarge.setToolTipText("Forceer stapbereik 15–20 (Storm2)");
        cbWalkLarge.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "walkForceLargeSteps", cbWalkLarge.isSelected());
            WalkClickSettings.forceLargeSteps = cbWalkLarge.isSelected();
            setStatus("Force large steps → " + cbWalkLarge.isSelected());
        });
        LoneBotConfigUiSync.bool(cbWalkLarge, "walkForceLargeSteps");

        JLabel walkStepLabel = new JLabel("Stap: " + config.walkStepMin() + "–" + config.walkStepMax() + " tiles");
        styleLabel(walkStepLabel);
        JSlider walkStepMinSlider = new JSlider(3, 25, config.walkStepMin());
        walkStepMinSlider.setMajorTickSpacing(5);
        walkStepMinSlider.setPaintTicks(true);
        JSlider walkStepMaxSlider = new JSlider(5, 40, config.walkStepMax());
        walkStepMaxSlider.setMajorTickSpacing(5);
        walkStepMaxSlider.setPaintTicks(true);
        Runnable syncStepLabel = () -> {
            int a = walkStepMinSlider.getValue();
            int b = Math.max(a, walkStepMaxSlider.getValue());
            if (walkStepMaxSlider.getValue() < a) {
                walkStepMaxSlider.setValue(a);
            }
            walkStepLabel.setText("Stap: " + a + "–" + b + " tiles");
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            configManager.setConfiguration("lonebot", "walkStepMin", a);
            configManager.setConfiguration("lonebot", "walkStepMax", b);
            WalkClickSettings.stepMin = a;
            WalkClickSettings.stepMax = b;
        };
        walkStepMinSlider.addChangeListener(e -> {
            if (!walkStepMinSlider.getValueIsAdjusting()) {
                syncStepLabel.run();
                setStatus("Walk stap min → " + walkStepMinSlider.getValue());
            } else {
                syncStepLabel.run();
            }
        });
        walkStepMaxSlider.addChangeListener(e -> {
            if (!walkStepMaxSlider.getValueIsAdjusting()) {
                syncStepLabel.run();
                setStatus("Walk stap max → " + walkStepMaxSlider.getValue());
            } else {
                syncStepLabel.run();
            }
        });
        LoneBotConfigUiSync.slider(walkStepMinSlider, walkStepLabel, "walkStepMin");
        LoneBotConfigUiSync.slider(walkStepMaxSlider, walkStepLabel, "walkStepMax");

        JLabel walkFinalLabel = new JLabel("Eindklik binnen: " + config.walkFinalClickTiles() + " tegels");
        styleLabel(walkFinalLabel);
        JSlider walkFinalSlider = new JSlider(8, 40, Math.min(40, Math.max(8, config.walkFinalClickTiles())));
        walkFinalSlider.setMajorTickSpacing(4);
        walkFinalSlider.setPaintTicks(true);
        walkFinalSlider.setToolTipText("Laatste N tegels op het pad: click-on-sight op B. Mislukt: 5 padtegels verder. Niet hemelsbreed.");
        walkFinalSlider.addChangeListener(e -> {
            int v = walkFinalSlider.getValue();
            walkFinalLabel.setText("Eindklik binnen: " + v + " tegels");
            if (!walkFinalSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "walkFinalClickTiles", v);
                WalkClickSettings.setFinalClickWithinTiles(v);
                setStatus("Eindklik binnen → " + v + " tegels");
            }
        });
        WalkClickSettings.setFinalClickWithinTiles(walkFinalSlider.getValue());

        JLabel walkReclickLabel = new JLabel("Reclick (ver): " + config.walkReclickMs() + " ms");
        styleLabel(walkReclickLabel);
        JSlider walkReclickSlider = new JSlider(150, 2000, Math.min(2000, Math.max(150, config.walkReclickMs())));
        walkReclickSlider.setMajorTickSpacing(250);
        walkReclickSlider.setPaintTicks(true);
        walkReclickSlider.addChangeListener(e -> {
            int v = walkReclickSlider.getValue();
            walkReclickLabel.setText("Reclick (ver): " + v + " ms");
            if (!walkReclickSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "walkReclickMs", v);
                WalkClickSettings.minReclickMs = v;
                setStatus("Walk reclick → " + v + "ms");
            }
        });

        JLabel walkNearLabel = new JLabel("Reclick (dicht bij flag): " + config.walkReclickNearFlagMs() + " ms");
        styleLabel(walkNearLabel);
        JSlider walkNearSlider = new JSlider(400, 3000,
                Math.min(3000, Math.max(400, config.walkReclickNearFlagMs())));
        walkNearSlider.setMajorTickSpacing(400);
        walkNearSlider.setPaintTicks(true);
        walkNearSlider.addChangeListener(e -> {
            int v = walkNearSlider.getValue();
            walkNearLabel.setText("Reclick (dicht bij flag): " + v + " ms");
            if (!walkNearSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "walkReclickNearFlagMs", v);
                WalkClickSettings.reclickNearFlagMs = v;
                setStatus("Near-flag reclick → " + v + "ms");
            }
        });

        JLabel walkPostLabel = new JLabel("Pauze na klik: " + config.walkPostClickDelayMin()
                + "–" + config.walkPostClickDelayMax() + " ms");
        styleLabel(walkPostLabel);
        walkPostLabel.setToolTipText("Adempauze direct na een walk-klik, daarna mag de volgende hop.");
        JSlider walkPostMinSlider = new JSlider(0, 1000, Math.min(1000, Math.max(0, config.walkPostClickDelayMin())));
        JSlider walkPostMaxSlider = new JSlider(0, 1500, Math.min(1500, Math.max(0, config.walkPostClickDelayMax())));
        Runnable syncPost = () -> {
            int a = walkPostMinSlider.getValue();
            int b = Math.max(a, walkPostMaxSlider.getValue());
            if (walkPostMaxSlider.getValue() < a) {
                walkPostMaxSlider.setValue(a);
            }
            walkPostLabel.setText("Pauze na klik: " + a + "–" + b + " ms");
            configManager.setConfiguration("lonebot", "walkPostClickDelayMin", a);
            configManager.setConfiguration("lonebot", "walkPostClickDelayMax", b);
            WalkClickSettings.postClickDelayMinMs = a;
            WalkClickSettings.postClickDelayMaxMs = b;
        };
        walkPostMinSlider.addChangeListener(e -> syncPost.run());
        walkPostMaxSlider.addChangeListener(e -> syncPost.run());

        JLabel walkChainLabel = new JLabel("Hop-doorlink: " + config.walkChainClickMin()
                + "–" + config.walkChainClickMax() + " ms");
        styleLabel(walkChainLabel);
        walkChainLabel.setToolTipText("Pauze tussen hops als je dicht bij de gele vlag bent (lange run).");
        JSlider walkChainMinSlider = new JSlider(20, 800, Math.min(800, Math.max(20, config.walkChainClickMin())));
        JSlider walkChainMaxSlider = new JSlider(20, 1500, Math.min(1500, Math.max(20, config.walkChainClickMax())));
        Runnable syncChain = () -> {
            int a = walkChainMinSlider.getValue();
            int b = Math.max(a, walkChainMaxSlider.getValue());
            if (walkChainMaxSlider.getValue() < a) {
                walkChainMaxSlider.setValue(a);
            }
            walkChainLabel.setText("Hop-doorlink: " + a + "–" + b + " ms");
            configManager.setConfiguration("lonebot", "walkChainClickMin", a);
            configManager.setConfiguration("lonebot", "walkChainClickMax", b);
            WalkClickSettings.setChainClickDelay(a, b);
        };
        walkChainMinSlider.addChangeListener(e -> syncChain.run());
        walkChainMaxSlider.addChangeListener(e -> syncChain.run());

        WalkClickSettings.applyAll(
                cbWalkMini.isSelected(),
                cbWalkCanvas.isSelected(),
                cbWalkUi.isSelected(),
                cbWalkCam.isSelected(),
                cbWalkInvoke.isSelected(),
                cbWalkFar.isSelected(),
                walkStepMinSlider.getValue(),
                walkStepMaxSlider.getValue(),
                cbWalkLarge.isSelected(),
                walkReclickSlider.getValue(),
                walkPostMinSlider.getValue(),
                walkPostMaxSlider.getValue(),
                cbWalkFlagWait.isSelected(),
                walkNearSlider.getValue(),
                cbWalkTwin.isSelected(),
                walkChainMinSlider.getValue(),
                walkChainMaxSlider.getValue());
        WalkClickSettings.setFinalClickWithinTiles(walkFinalSlider.getValue());

        JButton btnTestInvokeWalk = new JButton("🧪 Test invoke WALK (+5N)");
        btnTestInvokeWalk.setToolTipText("Alleen menuAction WALK 5 tiles noord — geen muis");
        btnTestInvokeWalk.addActionListener(e -> testInvokeWalk());

        JButton btnTestRunRead = new JButton("🏃 Run: lees staat");
        btnTestRunRead.setToolTipText("Alleen AAN of UIT. Extra widget-info in console als [Walk/run/dbg].");
        btnTestRunRead.addActionListener(e -> testRunRead());

        JButton btnTestRunOn = new JButton("🏃 Run AAN");
        btnTestRunOn.setToolTipText("Zet run-orb aan, herlees na 700 ms. Auto-run 8 s stil.");
        btnTestRunOn.addActionListener(e -> testRunSet(true));

        JButton btnTestRunOff = new JButton("🏃 Run UIT");
        btnTestRunOff.setToolTipText("Zet run-orb uit, herlees na 700 ms. Auto-run 8 s stil.");
        btnTestRunOff.addActionListener(e -> testRunSet(false));

        JButton btnTestMmb = new JButton("🧪 Test MMB camera-drag");
        btnTestMmb.setToolTipText("Synthetic MMB (geen focus-steal) — zie overlay Cam MMB / Δyaw");
        btnTestMmb.addActionListener(e -> testMmbCamera());

        JButton btnTestDbl = new JButton("🧪 Test snappy+double-click");
        btnTestDbl.setToolTipText("Forceer snappy hover + double-click op canvas-midden — zie Walk hum");
        btnTestDbl.addActionListener(e -> testForceDoubleClick());

        JButton btnTestLogoutAuto = new JButton("🚪 Logout AUTO (Game.logout)");
        btnTestLogoutAuto.setToolTipText("Zelfde sequentie als de bot: hopper ESC → Click here → knop → tab");
        btnTestLogoutAuto.addActionListener(e -> testLogout(net.storm.sdk.game.Game.LogoutMethod.AUTO, "AUTO"));

        JButton btnTestLogoutConfirm = new JButton("🚪 Logout 182,6 Click here");
        btnTestLogoutConfirm.setToolTipText("Alleen widget 182,6 — Click here to logout");
        btnTestLogoutConfirm.addActionListener(e ->
                testLogout(net.storm.sdk.game.Game.LogoutMethod.CONFIRM_CLICK_HERE, "182,6"));

        JButton btnTestLogout12 = new JButton("🚪 Logout 182,12 Logout");
        btnTestLogout12.setToolTipText("Alleen widget 182,12 — Logout");
        btnTestLogout12.addActionListener(e ->
                testLogout(net.storm.sdk.game.Game.LogoutMethod.BUTTON_182_12, "182,12"));

        JButton btnTestLogoutFirst = new JButton("🚪 Logout 182,12 1e actie");
        btnTestLogoutFirst.setToolTipText("CombatBot interact(0) — eerste actie, geen naam-check");
        btnTestLogoutFirst.addActionListener(e ->
                testLogout(net.storm.sdk.game.Game.LogoutMethod.FIRST_ACTION_182_12, "182,12 first"));

        JButton btnTestLogoutPacked = new JButton("🚪 Logout packed InterfaceID");
        btnTestLogoutPacked.setToolTipText("InterfaceID.Logout.LOGOUT");
        btnTestLogoutPacked.addActionListener(e ->
                testLogout(net.storm.sdk.game.Game.LogoutMethod.PACKED_LOGOUT, "packed"));

        JButton btnTestLogoutMenu = new JButton("🚪 Logout MenuInteract CC_OP");
        btnTestLogoutMenu.setToolTipText("invokeMenu Logout op packed id");
        btnTestLogoutMenu.addActionListener(e ->
                testLogout(net.storm.sdk.game.Game.LogoutMethod.MENU_CC_OP, "CC_OP"));

        JButton btnTestLogoutTab = new JButton("🚪 Logout tab openen (915)");
        btnTestLogoutTab.setToolTipText("Alleen logout-tab openen, geen bevestiging");
        btnTestLogoutTab.addActionListener(e ->
                testLogout(net.storm.sdk.game.Game.LogoutMethod.OPEN_TAB, "tab"));

        JButton btnTestAutoLogin = new JButton("🔁 Test auto-login (re-log)");
        btnTestAutoLogin.setToolTipText("Alleen op login-scherm: zelfde pad als na re-log pauze (meerdere Play-pogingen)");
        btnTestAutoLogin.addActionListener(e -> testAutoLoginRelog());

        JButton btnTestJagexPlayNow = new JButton("▶ Test Jagex Play Now");
        btnTestJagexPlayNow.setToolTipText("Grijze Play Now op het login-scherm (canvas-klik + kalibratie-log)");
        btnTestJagexPlayNow.addActionListener(e -> testJagexPlayNow());

        JButton btnTestWelcomePlay = new JButton("▶ Test welkomst Play");
        btnTestWelcomePlay.setToolTipText("CLICK HERE TO PLAY op iface 378,77 (welkomst-lobby)");
        btnTestWelcomePlay.addActionListener(e -> testWelcomePlay());

        JButton btnTestHopConfirm = new JButton("🌍 Hop: Switch world (193,0)");
        btnTestHopConfirm.setToolTipText(
                "Klik Switch world op de hop-bevestiging (Are you sure you wish to switch to World …?)");
        btnTestHopConfirm.addActionListener(e -> testHopConfirm());

        JButton btnDumpQty = new JButton("📋 Dump deposit qty (192)");
        btnDumpQty.setToolTipText(
                "Deposit box open: dump 1/5/10/X/All. Actie aanwezig = UIT, ontbreekt = AAN.");
        btnDumpQty.addActionListener(e -> testDepositQtyDump());

        JPanel qtyTestRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        qtyTestRow.setOpaque(false);
        for (DepositBox.QtyMode m : DepositBox.QtyMode.values()) {
            JButton b = new JButton("Test " + m.label);
            b.setToolTipText("Klik 192," + m.child + " alleen als die UIT is. Al AAN → geen klik, wel debug.");
            DepositBox.QtyMode mode = m;
            b.addActionListener(e -> testDepositQtyClick(mode));
            qtyTestRow.add(b);
        }

        JTextField geBuyItem = new JTextField("Bronze arrow", 12);
        JTextField geBuyQty = new JTextField("1", 4);
        JTextField geBuyPrice = new JTextField("50", 6);
        JButton btnGeBuy = new JButton("Koop op GE");
        btnGeBuy.setToolTipText("Ingelogd account loopt naar GE en plaatst een buy-offer");
        JPanel geBuyRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        geBuyRow.setOpaque(false);
        geBuyRow.add(new JLabel("Item"));
        geBuyRow.add(geBuyItem);
        geBuyRow.add(new JLabel("Aantal"));
        geBuyRow.add(geBuyQty);
        geBuyRow.add(new JLabel("gp/stuk"));
        geBuyRow.add(geBuyPrice);
        geBuyRow.add(btnGeBuy);

        JTextField geSellItem = new JTextField("", 12);
        geSellItem.setToolTipText("Exacte inventory-naam (bijv. Cowhide)");
        JTextField geSellQty = new JTextField("1", 4);
        JTextField geSellPrice = new JTextField("1", 6);
        JButton btnGeSell = new JButton("Verkoop op GE");
        btnGeSell.setToolTipText("Ingelogd account loopt naar GE en plaatst een sell-offer");
        JPanel geSellRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        geSellRow.setOpaque(false);
        geSellRow.add(new JLabel("Item"));
        geSellRow.add(geSellItem);
        geSellRow.add(new JLabel("Aantal"));
        geSellRow.add(geSellQty);
        geSellRow.add(new JLabel("gp/stuk"));
        geSellRow.add(geSellPrice);
        geSellRow.add(btnGeSell);

        JLabel geTestStatus = new JLabel("GE-test: uit");
        styleLabel(geTestStatus);
        JButton btnGeStop = new JButton("Stop GE-test");
        btnGeBuy.addActionListener(e -> startGeTradeTest(true,
                geBuyItem.getText(), geBuyQty.getText(), geBuyPrice.getText()));
        btnGeSell.addActionListener(e -> startGeTradeTest(false,
                geSellItem.getText(), geSellQty.getText(), geSellPrice.getText()));
        btnGeStop.addActionListener(e -> {
            BotRuntime.geTradeTestEnabled = false;
            BotRuntime.geTradeStatus = "uit";
            setStatus("GE-test gestopt");
        });
        javax.swing.Timer geTestSync = new javax.swing.Timer(400, ev -> {
            String st = BotRuntime.geTradeStatus != null ? BotRuntime.geTradeStatus : "uit";
            geTestStatus.setText("GE-test: " + st);
        });
        geTestSync.setRepeats(true);
        geTestSync.start();

        JButton btnTestWalkCam = new JButton("🎥 Preview camera (geen walk)");
        btnTestWalkCam.setToolTipText("Alleen camera naar 10N — geen lopen. Gebruik sliders hieronder.");
        btnTestWalkCam.addActionListener(e -> testWalkCameraPreview());

        JButton btnTestWalkCamWalk = new JButton("🚶 Walk + camera test (+10N)");
        btnTestWalkCamWalk.setToolTipText("Echt lopen 10 tegels noord + camera volgens sliders.");
        btnTestWalkCamWalk.addActionListener(e -> testWalkCameraWalk());

        JButton btnCamReport = new JButton("📋 Kopieer camera-rapport");
        btnCamReport.setToolTipText("Kopieert alle walk-camera settings + live yaw/pitch/scale — plak in chat naar AI.");
        btnCamReport.addActionListener(e -> copyWalkCameraReport());

        JCheckBox cbWalkCamOn = new JCheckBox("Walk-camera aan", config.walkCamEnabled());
        styleCheck(cbWalkCamOn);
        cbWalkCamOn.setToolTipText("Bij elke walk: yaw/pitch/zoom toepassen");

        JCheckBox cbWalkCamMmb = new JCheckBox("Menselijke MMB-camera (anti-ban stijl)", config.walkCamHumanMmb());
        styleCheck(cbWalkCamMmb);
        cbWalkCamMmb.setToolTipText("Vloeiende middle-mouse drag i.p.v. blokkerige setYaw/setPitch");

        JCheckBox cbWalkCamKb = new JCheckBox("Walk-camera toetsenbord (pijltjes)", config.walkCamUseKeyboard());
        styleCheck(cbWalkCamKb);
        cbWalkCamKb.setToolTipText("←→ yaw, ↑↓ pitch i.p.v. MMB. Zoom blijft scrollwiel. Stabieler dan MMB.");
        // KB aan → MMB uit (wederzijds)
        if (cbWalkCamKb.isSelected()) {
            cbWalkCamMmb.setSelected(false);
        }

        JComboBox<WalkCameraSettings.YawMode> comboYawMode = new JComboBox<>(WalkCameraSettings.YawMode.values());
        try {
            comboYawMode.setSelectedItem(WalkCameraSettings.YawMode.valueOf(config.walkCamYawMode().trim().toUpperCase()));
        } catch (Exception ignored) {
            comboYawMode.setSelectedItem(WalkCameraSettings.YawMode.FOLLOW);
        }
        comboYawMode.setToolTipText(
                "FOLLOW=vooruit · CONTRA=achteruit · OFFSET=follow+graden. Wijziging draait camera meteen (moet ingelogd zijn).");

        JLabel yawOffLabel = new JLabel("Yaw offset: " + config.walkCamYawOffsetDeg() + "°");
        styleLabel(yawOffLabel);
        JSlider yawOffSlider = new JSlider(-180, 180, config.walkCamYawOffsetDeg());
        yawOffSlider.setOpaque(false);
        yawOffSlider.setMinorTickSpacing(5);
        yawOffSlider.setMajorTickSpacing(45);
        yawOffSlider.setPaintTicks(true);
        yawOffSlider.setSnapToTicks(false);
        yawOffSlider.setToolTipText("Extra draai. FOLLOW+180° ≈ CONTRA. Laat los → camera past aan.");

        int pitchInit = Math.max(WalkCameraSettings.PITCH_ABS_MIN,
                Math.min(WalkCameraSettings.PITCH_ABS_MAX, config.walkCamPitch()));
        JLabel pitchLabel = new JLabel("Hoogte (pitch): " + pitchInit
                + "   [" + WalkCameraSettings.PITCH_ABS_MIN + "← →"
                + WalkCameraSettings.PITCH_ABS_MAX + " | default 3064]");
        styleLabel(pitchLabel);
        JSlider pitchSlider = new JSlider(
                WalkCameraSettings.PITCH_ABS_MIN, WalkCameraSettings.PITCH_ABS_MAX, pitchInit);
        pitchSlider.setOpaque(false);
        pitchSlider.setMinorTickSpacing(64);
        pitchSlider.setMajorTickSpacing(512);
        pitchSlider.setPaintTicks(true);
        pitchSlider.setSnapToTicks(false);
        pitchSlider.setToolTipText("Jouw loop-hoek ≈ 3064 (client getCameraPitch).");

        int skipInit = Math.max(50, config.walkCamVisibilitySkip());
        JLabel skipLabel = new JLabel("Skip camera als face ≥ " + skipInit + "%  (75=strak FOLLOW)");
        styleLabel(skipLabel);
        JSlider skipSlider = new JSlider(50, 95, skipInit);
        skipSlider.setOpaque(false);
        skipSlider.setMinorTickSpacing(5);
        skipSlider.setMajorTickSpacing(15);
        skipSlider.setPaintTicks(true);
        skipSlider.setSnapToTicks(false);
        skipSlider.setToolTipText("Te laag (bv. 23%) = camera skipt terwijl je nog scheef kijkt. 75% ≈ max ~45° fout.");

        int zoomInit = config.walkCamZoomPercent();
        if (zoomInit == 15 && config.walkCamZoomScaleMax() != 320) {
            // migrate legacy once if user had changed scale
            zoomInit = WalkCameraSettings.scaleToPercent(config.walkCamZoomScaleMax());
        }
        JLabel zoomLabel = new JLabel("Zoom: " + zoomInit + "%   [0=uit ← → 100=in]");
        styleLabel(zoomLabel);
        JSlider zoomSlider = new JSlider(0, 100, zoomInit);
        zoomSlider.setOpaque(false);
        zoomSlider.setMinorTickSpacing(1);
        zoomSlider.setMajorTickSpacing(10);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setSnapToTicks(false);
        zoomSlider.setToolTipText("0%=volledig uitgezoomd, 100%=ingedraaid. Stap 1%.");

        Runnable syncWalkCam = () -> {
            WalkCameraSettings.YawMode mode = (WalkCameraSettings.YawMode) comboYawMode.getSelectedItem();
            if (mode == null) {
                mode = WalkCameraSettings.YawMode.FOLLOW;
            }
            int off = yawOffSlider.getValue();
            int pitch = pitchSlider.getValue();
            int zoom = zoomSlider.getValue();
            int skip = skipSlider.getValue();
            yawOffLabel.setText("Yaw offset: " + off + "°");
            pitchLabel.setText("Hoogte (pitch): " + pitch + "   ["
                    + WalkCameraSettings.PITCH_ABS_MIN + "← →"
                    + WalkCameraSettings.PITCH_ABS_MAX + " | default 3064]");
            skipLabel.setText("Skip camera als face ≥ " + skip + "%  (75=strak FOLLOW)");
            zoomLabel.setText("Zoom: " + zoom + "%   [0=uit ← → 100=in]  scale~"
                    + (WalkCameraSettings.ZOOM_SCALE_OUT
                    + zoom * (WalkCameraSettings.ZOOM_SCALE_IN - WalkCameraSettings.ZOOM_SCALE_OUT) / 100));
            WalkCameraSettings.apply(
                    cbWalkCamOn.isSelected(),
                    cbWalkCamMmb.isSelected() && !cbWalkCamKb.isSelected(),
                    mode, off, pitch, 30, zoom);
            WalkCameraSettings.useKeyboard = cbWalkCamKb.isSelected();
            WalkCameraSettings.visibilitySkipPercent = skip;
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            configManager.setConfiguration("lonebot", "walkCamEnabled", cbWalkCamOn.isSelected());
            configManager.setConfiguration("lonebot", "walkCamHumanMmb", cbWalkCamMmb.isSelected());
            configManager.setConfiguration("lonebot", "walkCamUseKeyboard", cbWalkCamKb.isSelected());
            configManager.setConfiguration("lonebot", "walkCamYawMode", mode.name());
            configManager.setConfiguration("lonebot", "walkCamYawOffsetDeg", off);
            configManager.setConfiguration("lonebot", "walkCamPitch", pitch);
            configManager.setConfiguration("lonebot", "walkCamZoomPercent", zoom);
            configManager.setConfiguration("lonebot", "walkCamVisibilitySkip", skip);
        };
        cbWalkCamOn.addActionListener(e -> {
            syncWalkCam.run();
            setStatus("Walk-camera → " + cbWalkCamOn.isSelected());
        });
        cbWalkCamMmb.addActionListener(e -> {
            if (cbWalkCamMmb.isSelected()) {
                cbWalkCamKb.setSelected(false);
            }
            syncWalkCam.run();
            setStatus("MMB human → " + cbWalkCamMmb.isSelected());
        });
        cbWalkCamKb.addActionListener(e -> {
            if (cbWalkCamKb.isSelected()) {
                cbWalkCamMmb.setSelected(false);
            }
            syncWalkCam.run();
            setStatus("Walk-cam KB → " + cbWalkCamKb.isSelected());
            applyWalkCamLive(false);
        });
        comboYawMode.addActionListener(e -> {
            syncWalkCam.run();
            setStatus("Yaw mode → " + comboYawMode.getSelectedItem() + " — camera toepassen…");
            applyWalkCamLive(false);
        });
        yawOffSlider.addChangeListener(e -> {
            syncWalkCam.run();
            if (!yawOffSlider.getValueIsAdjusting()) {
                setStatus("Yaw offset → " + yawOffSlider.getValue() + "° — camera toepassen…");
                applyWalkCamLive(false);
            }
        });
        pitchSlider.addChangeListener(e -> {
            syncWalkCam.run();
            if (!pitchSlider.getValueIsAdjusting()) {
                setStatus("Pitch → " + pitchSlider.getValue() + " — camera toepassen…");
                applyWalkCamLive(false);
            }
        });
        skipSlider.addChangeListener(e -> {
            syncWalkCam.run();
            if (!skipSlider.getValueIsAdjusting()) {
                setStatus("Skip zicht → " + skipSlider.getValue() + "%");
            }
        });
        zoomSlider.addChangeListener(e -> {
            syncWalkCam.run();
            if (!zoomSlider.getValueIsAdjusting()) {
                setStatus("Zoom → " + zoomSlider.getValue() + "% — camera toepassen…");
                applyWalkCamLive(false);
            }
        });
        // Geen live apply bij eerste sync (panel-load) — alleen bij user-wijziging
        syncWalkCam.run();
        LoneBotConfigUiSync.bool(cbWalkCamOn, "walkCamEnabled");
        LoneBotConfigUiSync.bool(cbWalkCamMmb, "walkCamHumanMmb");
        LoneBotConfigUiSync.bool(cbWalkCamKb, "walkCamUseKeyboard");
        LoneBotConfigUiSync.combo(comboYawMode, "walkCamYawMode");
        LoneBotConfigUiSync.slider(yawOffSlider, yawOffLabel, "walkCamYawOffsetDeg");
        LoneBotConfigUiSync.slider(pitchSlider, pitchLabel, "walkCamPitch");
        LoneBotConfigUiSync.slider(skipSlider, skipLabel, "walkCamVisibilitySkip");
        LoneBotConfigUiSync.slider(zoomSlider, zoomLabel, "walkCamZoomPercent");

        JCheckBox cbRandom = new JCheckBox("Random muis-beweging", config.mouseFidgetEnabled());
        cbRandom.setOpaque(false);
        cbRandom.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        cbRandom.setToolTipText("Periodiek Bezier-pad naar random canvas-punt (niet alleen midden)");
        cbRandom.addActionListener(e -> {
            boolean on = cbRandom.isSelected();
            configManager.setConfiguration("lonebot", "mouseFidgetEnabled", on);
            MouseSettings.setRandomMoveEnabled(on);
            com.lonebot.example.ExampleLoopedPlugin.mouseFidgetEnabled = on;
            setStatus(on ? "✓ Random muis AAN" : "Random muis uit");
        });
        MouseSettings.setRandomMoveEnabled(cbRandom.isSelected());
        com.lonebot.example.ExampleLoopedPlugin.mouseFidgetEnabled = cbRandom.isSelected();
        LoneBotConfigUiSync.bool(cbRandom, "mouseFidgetEnabled");

        JLabel speedLabel = new JLabel("Muis snelheid: " + config.mouseSpeedPercent() + "%");
        speedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        JSlider speedSlider = new JSlider(25, 300, config.mouseSpeedPercent());
        speedSlider.setOpaque(false);
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setPaintTicks(true);
        speedSlider.addChangeListener(e -> {
            int pct = speedSlider.getValue();
            speedLabel.setText("Muis snelheid: " + pct + "%");
            if (!speedSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "mouseSpeedPercent", pct);
                MouseSettings.setSpeedPercent(pct);
                setStatus("Muis snelheid → " + pct + "%");
            }
        });
        LoneBotConfigUiSync.slider(speedSlider, speedLabel, "mouseSpeedPercent");
        MouseSettings.setSpeedPercent(config.mouseSpeedPercent());

        JComboBox<MouseBackend> comboMouse = new JComboBox<>(new MouseBackend[]{
                MouseBackend.CANVAS_DISPATCH,
                MouseBackend.CANVAS_EDT,
                MouseBackend.CANVAS_EDT_SCREEN,
                MouseBackend.MENU_ONLY
        });
        MouseBackend currentBe = Mouse.getBackend();
        if (currentBe != null && currentBe.isFocusSafe()) {
            comboMouse.setSelectedItem(currentBe);
        } else {
            Mouse.setBackend(MouseBackend.CANVAS_EDT);
            comboMouse.setSelectedItem(MouseBackend.CANVAS_EDT);
        }
        comboMouse.setToolTipText("Alleen canvas/menu — Robot/focus geblokkeerd (venster blijft inactief)");
        comboMouse.addActionListener(e -> {
            MouseBackend b = (MouseBackend) comboMouse.getSelectedItem();
            Mouse.setBackend(b);
            setStatus("Mouse backend → " + (b != null ? b.label() : "?"));
        });

        JButton btnTestRandom = new JButton("🧪 Test random muis (geen klik)");
        btnTestRandom.setToolTipText("Bezier naar random canvas-punt — zie kruis/overlay meebewegen");
        btnTestRandom.addActionListener(e -> runMouseTest("Random move…", () -> Mouse.moveRandom(false)));

        JButton btnTestMouse = new JButton("🧪 Test muis (midden + klik)");
        btnTestMouse.setToolTipText("Klik midden van game-canvas met huidige backend");
        btnTestMouse.addActionListener(e -> runMouseTest("Mouse test…", Mouse::testClickCenter));

        JButton btnMoveCow = new JButton("① MOVE → cow (geen klik)");
        btnMoveCow.setToolTipText("Client-thread hull-coords → Bezier naar cow, geen klik");
        btnMoveCow.addActionListener(e -> runTargetTest(NpcTargetTests.Mode.MOVE_HULL));

        JButton btnClickHull = new JButton("② CLICK hull (cow)");
        btnClickHull.setToolTipText("Bezier + klik op convex-hull punt (Storm-stijl)");
        btnClickHull.addActionListener(e -> runTargetTest(NpcTargetTests.Mode.CLICK_HULL));

        JButton btnClickL2c = new JButton("③ CLICK localToCanvas (cow)");
        btnClickL2c.setToolTipText("Bezier + klik op Perspective.localToCanvas (zonder hull)");
        btnClickL2c.addActionListener(e -> runTargetTest(NpcTargetTests.Mode.CLICK_LOCAL_TO_CANVAS));

        JButton btnMenu = new JButton("④ MENU invoke Attack");
        btnMenu.setToolTipText("Geen muis — Client.invokeMenuAction (vaak broken op vanilla RL)");
        btnMenu.addActionListener(e -> runTargetTest(NpcTargetTests.Mode.MENU_INVOKE));

        JLabel autocastHint = new JLabel("<html>Staff wielden → methode + spell → Test. "
                + "Start met <b>0) Dump state</b>. Resultaat in Debug-tab (console).</html>");
        styleLabel(autocastHint);

        JComboBox<net.storm.sdk.magic.AutocastTestHelper.Method> comboAutocastMethod =
                new JComboBox<>(net.storm.sdk.magic.AutocastTestHelper.Method.values());
        comboAutocastMethod.setSelectedItem(net.storm.sdk.magic.AutocastTestHelper.Method.COMBAT_DYN_ACTION);
        comboAutocastMethod.setToolTipText("Verschillende manieren om autocast te zetten");

        JComboBox<net.storm.api.magic.SpellBook.Standard> comboAutocastSpell =
                new JComboBox<>(new net.storm.api.magic.SpellBook.Standard[]{
                        net.storm.api.magic.SpellBook.Standard.WIND_STRIKE,
                        net.storm.api.magic.SpellBook.Standard.WATER_STRIKE,
                        net.storm.api.magic.SpellBook.Standard.EARTH_STRIKE,
                        net.storm.api.magic.SpellBook.Standard.FIRE_STRIKE,
                        net.storm.api.magic.SpellBook.Standard.WIND_BOLT,
                        net.storm.api.magic.SpellBook.Standard.FIRE_BOLT
                });
        comboAutocastSpell.setSelectedItem(net.storm.api.magic.SpellBook.Standard.FIRE_STRIKE);
        comboAutocastSpell.setToolTipText("Spell voor autocast-test");

        JButton btnAutocastTest = new JButton("🧪 Test autocast (geselecteerde methode)");
        btnAutocastTest.setToolTipText("Draait op background-thread; zie Console + statusbalk");
        btnAutocastTest.addActionListener(e -> {
            net.storm.sdk.magic.AutocastTestHelper.Method m =
                    (net.storm.sdk.magic.AutocastTestHelper.Method) comboAutocastMethod.getSelectedItem();
            net.storm.api.magic.SpellBook.Standard sp =
                    (net.storm.api.magic.SpellBook.Standard) comboAutocastSpell.getSelectedItem();
            runAutocastTest(m, sp);
        });

        JLabel teleHint = new JLabel("<html>F2P tele: sta veilig, runes/staff klaar (Home=geen runes). "
                + "Eerst <b>0) Dump</b>. Success = land bij echte tegel (Lumb 3222,3218), niet 8 tegels lopen. "
                + "Home wacht tot ~20s — niet bewegen. Geen twee tests tegelijk. Console: [TeleTest].</html>");
        styleLabel(teleHint);

        JComboBox<net.storm.sdk.magic.TeleportTestHelper.Method> comboTeleMethod =
                new JComboBox<>(net.storm.sdk.magic.TeleportTestHelper.Method.values());
        comboTeleMethod.setSelectedItem(net.storm.sdk.magic.TeleportTestHelper.Method.MAGIC_CAST);
        comboTeleMethod.setToolTipText("Cast-methode (Star gebruikt Magic.cast / widget.interact Cast)");

        JComboBox<net.storm.sdk.magic.TeleportTestHelper.SpellChoice> comboTeleSpell =
                new JComboBox<>(net.storm.sdk.magic.TeleportTestHelper.SpellChoice.values());
        comboTeleSpell.setSelectedItem(net.storm.sdk.magic.TeleportTestHelper.SpellChoice.HOME);
        comboTeleSpell.setToolTipText("Home / Varrock / Lumbridge / Falador");

        JButton btnTeleTest = new JButton("🪄 Test F2P teleport (methode + spell)");
        btnTeleTest.setToolTipText("Background-thread; wacht op land. Bot mag UIT.");
        btnTeleTest.addActionListener(e -> {
            net.storm.sdk.magic.TeleportTestHelper.Method m =
                    (net.storm.sdk.magic.TeleportTestHelper.Method) comboTeleMethod.getSelectedItem();
            net.storm.sdk.magic.TeleportTestHelper.SpellChoice sp =
                    (net.storm.sdk.magic.TeleportTestHelper.SpellChoice) comboTeleSpell.getSelectedItem();
            runTeleportTest(m, sp);
        });

        JButton btnTeleAllMethods = new JButton("🪄 Tele: alle methodes op gekozen spell");
        btnTeleAllMethods.setToolTipText("Probeert Magic.cast → CC_OP → WIDGET_TARGET; stopt bij eerste echte land");
        btnTeleAllMethods.addActionListener(e -> {
            net.storm.sdk.magic.TeleportTestHelper.SpellChoice sp =
                    (net.storm.sdk.magic.TeleportTestHelper.SpellChoice) comboTeleSpell.getSelectedItem();
            runTeleportTest(net.storm.sdk.magic.TeleportTestHelper.Method.ALL_METHODS, sp);
        });

        JLabel fmLightHint = new JLabel("<html>Tinderbox + logs in inv → kies methode → Test. "
                + "Eerst <b>Diagnose</b> als bot light nooit bereikt. Console: [FmLightTest].</html>");
        styleLabel(fmLightHint);

        JComboBox<String> comboFmLight = new JComboBox<>(com.lonebot.example.woodcutter.FmLightHelper.methodLabels());
        comboFmLight.setSelectedIndex(0);
        comboFmLight.setToolTipText("10 light-methodes (zelfde als WC bonfire-rotatie)");

        JButton btnFmLightTest = new JButton("🔥 Test FM light (geselecteerde methode)");
        btnFmLightTest.setToolTipText("Roept FmLightHelper.runTest aan — bot mag uit");
        btnFmLightTest.addActionListener(e -> {
            int idx = comboFmLight.getSelectedIndex();
            runFmLightTest(idx);
        });

        JButton btnFmLightAll = new JButton("🔥 Test alle 10 light-methodes (sequentieel)");
        btnFmLightAll.setToolTipText("Probeert #1…#10 met korte pauze — stop als vuur aangaat");
        btnFmLightAll.addActionListener(e -> runFmLightTestAll());

        JButton btnFmDiagnose = new JButton("🔎 Diagnose FM gates (waarom geen light?)");
        btnFmDiagnose.setToolTipText("prodOpen/qty/moving/burnable — zonder klik");
        btnFmDiagnose.addActionListener(e -> runFmDiagnose());

        JButton btnAnimDump = new JButton("🎬 Dump huidige animatie + known count");
        btnAnimDump.setToolTipText("AnimationIds RuneLite-dump: nameOf(local) + size()");
        btnAnimDump.addActionListener(e -> runAnimDump());

        // Script-reload knoppen: createScriptsToolbar() (Settings + Skills General)

        JButton btnImportStorm = new JButton("📥 Import Storm JSON");
        btnImportStorm.setToolTipText("Importeert storm-accounts*.json (zoals storm-accountsjuli.json)");
        btnImportStorm.addActionListener(e -> importStormJson());

        JButton btnImportDefault = new JButton("📥 Desktop storm-accountsjuli.json");
        btnImportDefault.setToolTipText(DEFAULT_STORM_JSON.getAbsolutePath());
        btnImportDefault.addActionListener(e -> importStormFile(DEFAULT_STORM_JSON));

        JButton btnApply = new JButton("Login klaarzetten");
        btnApply.setToolTipText("Zet Jagex-credentials live (zoals Storm) — geen herstart. Daarna Play op login-scherm.");
        btnApply.addActionListener(e -> applyLogin());

        JButton btnRefresh = new JButton("↺ Vernieuw lijst");
        btnRefresh.addActionListener(e -> refreshList());

        JCheckBox cbStatusOverlay = new JCheckBox("Control panel (canvas)", config.statusOverlay());
        styleCheck(cbStatusOverlay);
        cbStatusOverlay.setToolTipText("CombatBotPaint-stijl panel: status ●, walk, actieve skill");
        cbStatusOverlay.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "statusOverlay", cbStatusOverlay.isSelected());
            setStatus(cbStatusOverlay.isSelected() ? "✓ Control panel AAN" : "Control panel uit");
        });
        LoneBotConfigUiSync.bool(cbStatusOverlay, "statusOverlay");

        JCheckBox cbPaintMin = new JCheckBox("Control panel geminimaliseerd", config.paintOverlayMinimized());
        styleCheck(cbPaintMin);
        cbPaintMin.setToolTipText("Alleen goud chip. Klik de chip in-game om te togglen.");
        cbPaintMin.addActionListener(e -> {
            OverlayMinimizeStore.setMinimized("control", cbPaintMin.isSelected());
            setStatus(cbPaintMin.isSelected() ? "✓ Panel geminimaliseerd" : "Panel uitgeklapt");
        });
        LoneBotConfigUiSync.bool(cbPaintMin, "paintOverlayMinimized");

        JCheckBox cbPaintProgress = new JCheckBox("Paint: voortgang (XP)", config.paintShowProgress());
        styleCheck(cbPaintProgress);
        cbPaintProgress.setToolTipText("Sectie voortgang op goud control panel");
        cbPaintProgress.addActionListener(e ->
                configManager.setConfiguration("lonebot", "paintShowProgress", cbPaintProgress.isSelected()));
        LoneBotConfigUiSync.bool(cbPaintProgress, "paintShowProgress");

        JCheckBox cbPaintTarget = new JCheckBox("Paint: doel", config.paintShowTarget());
        styleCheck(cbPaintTarget);
        cbPaintTarget.addActionListener(e ->
                configManager.setConfiguration("lonebot", "paintShowTarget", cbPaintTarget.isSelected()));
        LoneBotConfigUiSync.bool(cbPaintTarget, "paintShowTarget");

        JCheckBox cbPaintAction = new JCheckBox("Paint: actie", config.paintShowAction());
        styleCheck(cbPaintAction);
        cbPaintAction.addActionListener(e ->
                configManager.setConfiguration("lonebot", "paintShowAction", cbPaintAction.isSelected()));
        LoneBotConfigUiSync.bool(cbPaintAction, "paintShowAction");

        JCheckBox cbPaintKit = new JCheckBox("Paint: kit / event", config.paintShowKit());
        styleCheck(cbPaintKit);
        cbPaintKit.addActionListener(e ->
                configManager.setConfiguration("lonebot", "paintShowKit", cbPaintKit.isSelected()));
        LoneBotConfigUiSync.bool(cbPaintKit, "paintShowKit");

        JCheckBox cbPaintBank = new JCheckBox("Paint: bank / supply", config.paintShowBankSupply());
        styleCheck(cbPaintBank);
        cbPaintBank.addActionListener(e ->
                configManager.setConfiguration("lonebot", "paintShowBankSupply", cbPaintBank.isSelected()));
        LoneBotConfigUiSync.bool(cbPaintBank, "paintShowBankSupply");

        JCheckBox cbPaintAb = new JCheckBox("Paint: anti-ban", config.paintShowAntiBan());
        styleCheck(cbPaintAb);
        cbPaintAb.addActionListener(e ->
                configManager.setConfiguration("lonebot", "paintShowAntiBan", cbPaintAb.isSelected()));
        LoneBotConfigUiSync.bool(cbPaintAb, "paintShowAntiBan");

        JCheckBox cbPaintScriptDbg = new JCheckBox("Paint: script debug details", config.paintShowScriptDebug());
        styleCheck(cbPaintScriptDbg);
        cbPaintScriptDbg.setToolTipText("Extra script-debugLines (FM-diag, sticky, pref, …) — paneel groeit mee");
        cbPaintScriptDbg.addActionListener(e ->
                configManager.setConfiguration("lonebot", "paintShowScriptDebug", cbPaintScriptDbg.isSelected()));
        LoneBotConfigUiSync.bool(cbPaintScriptDbg, "paintShowScriptDebug");

        JCheckBox cbMouseDebug = new JCheckBox("Muis debug (kruisje)",
                config.mouseDebugOverlay() || BotRuntime.mouseDebugEnabled);
        styleCheck(cbMouseDebug);
        cbMouseDebug.setToolTipText("Muis-kruisje + target op canvas — los van status overlay");
        cbMouseDebug.addActionListener(e -> {
            boolean on = cbMouseDebug.isSelected();
            BotRuntime.mouseDebugEnabled = on;
            configManager.setConfiguration("lonebot", "mouseDebugOverlay", on);
            setStatus(on ? "✓ Muis debug AAN" : "Muis debug uit");
        });
        LoneBotConfigUiSync.bool(cbMouseDebug, "mouseDebugOverlay");
        BotRuntime.mouseDebugEnabled = cbMouseDebug.isSelected();

        // ---- Updates: zie launcher News-tab (LoneBotNewsPanel) ----

        JLabel tileCountLabel = new JLabel("Excluded tiles: " + net.storm.sdk.tiles.ExcludedTiles.size());
        styleLabel(tileCountLabel);
        JCheckBox cbShowTiles = new JCheckBox("Toon excluded tiles", config.showExcludedTiles());
        styleCheck(cbShowTiles);
        cbShowTiles.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "showExcludedTiles", cbShowTiles.isSelected());
            setStatus(cbShowTiles.isSelected() ? "✓ Tile overlay AAN" : "Tile overlay uit");
        });
        JButton btnClearTiles = new JButton("🗑 Wis alle excluded tiles");
        btnClearTiles.setToolTipText("Verwijdert alle tegels voor élk account (gedeeld ~/.lonebot/excluded-tiles.txt)");
        btnClearTiles.addActionListener(e -> {
            net.storm.sdk.tiles.ExcludedTiles.clear();
            net.storm.sdk.tiles.ExcludedTiles.saveShared();
            configManager.setConfiguration("lonebot", "excludedTiles", "");
            tileCountLabel.setText("Excluded tiles: 0");
            setStatus("Excluded tiles gewist (alle accounts)");
        });
        JLabel tileHint = new JLabel("<html><i>Rechtsklik tegel → Markeer tile. Lijst is gedeeld voor alle accounts.</i></html>");
        styleLabel(tileHint);
        JCheckBox cbWallsOverlay = new JCheckBox("Toon muren & deuren overlay", config.wallsOverlayEnabled());
        styleCheck(cbWallsOverlay);
        cbWallsOverlay.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "wallsOverlayEnabled", cbWallsOverlay.isSelected());
            setStatus(cbWallsOverlay.isSelected() ? "✓ Walls overlay AAN" : "Walls overlay uit");
        });
        JLabel wallsRadiusLbl = new JLabel("Get walls scan-radius: " + config.wallsScanRadius());
        styleLabel(wallsRadiusLbl);
        JSlider wallsRadiusSlider = new JSlider(4, 24, Math.min(24, Math.max(4, config.wallsScanRadius())));
        wallsRadiusSlider.setOpaque(false);
        wallsRadiusSlider.setMajorTickSpacing(4);
        wallsRadiusSlider.setPaintTicks(true);
        wallsRadiusSlider.setToolTipText("Tegel-radius van Get walls (4–24). Loslaten slaat op; daarna opnieuw Get walls.");
        wallsRadiusSlider.addChangeListener(e -> {
            int r = wallsRadiusSlider.getValue();
            wallsRadiusLbl.setText("Get walls scan-radius: " + r);
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            if (!wallsRadiusSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "wallsScanRadius", r);
                setStatus("Get walls radius → " + r);
            }
        });
        LoneBotConfigUiSync.slider(wallsRadiusSlider, null, "wallsScanRadius");
        JButton btnClearWalls = new JButton("Wis walls-scan overlay");
        btnClearWalls.addActionListener(e -> {
            net.storm.sdk.walls.WallDoorCaptureState.clear();
            setStatus("Walls-scan gewist");
        });
        javax.swing.Timer tileCountSync = new javax.swing.Timer(1000, ev ->
                tileCountLabel.setText("Excluded tiles: " + net.storm.sdk.tiles.ExcludedTiles.size()));
        tileCountSync.setRepeats(true);
        tileCountSync.start();

        consoleArea.setEditable(false);
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        consoleArea.setBackground(LoneBotUiTheme.INPUT_BG);
        consoleArea.setForeground(LoneBotUiTheme.INPUT_FG);
        consoleArea.setCaretColor(LoneBotUiTheme.INPUT_FG);
        LoneBotUiTheme.mark(consoleArea, LoneBotUiTheme.ROLE_INPUT);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JButton btnClearConsole = new JButton("Wis console");
        btnClearConsole.setToolTipText("Leegt het console-log");
        btnClearConsole.addActionListener(e -> {
            consoleArea.setText("");
            appendConsole("Console gewist");
        });
        JCheckBox cbConsoleAutoScroll = new JCheckBox("Auto-scroll", true);
        cbConsoleAutoScroll.setToolTipText(
                "Aan: spring naar nieuwste regel. Uit: blijf staan zodat je tekst kunt selecteren/kopiëren.");
        cbConsoleAutoScroll.addActionListener(e -> {
            consoleAutoScroll = cbConsoleAutoScroll.isSelected();
            if (consoleAutoScroll) {
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }
        });
        BotRuntime.consoleSink = this::appendConsole;

        // ========== TABS (CombatBotPanel-volgorde) ==========
        // 👤 Accounts | ⚙ Settings | 🎯 Skills | 📊 Stats | 📍 Centers | 🔧 Developer Tools | 🔍 Debug | 🧪 Test
        // Elke Swing-control mag maar 1 parent hebben — skill-details in Skills; Settings = overzicht + gedeelde opties.
        mainTabs = new JTabbedPane();
        polishMainTabs(mainTabs);

        accountsTab = new LoneBotAccountsTab(
                LoneBotAccountsTab.Mode.CLIENT, this::setStatus, this::prepareLoginFromAccount,
                configManager, config);
        mainTabs.addTab("Accounts", accountsTab);

        panelSettings = wrapTabScroll(buildTabColumn(col -> {
            col.add(createScriptsToolbar());
            col.add(Box.createVerticalStrut(8));
            col.add(createCollapsibleSection("▶ Bot Control", true, true, sec -> {
                addSectionComp(sec, cbBotEnabled);
                addSectionComp(sec, createStopWorldWalkerButton());
                addSectionComp(sec, themeLbl);
                addSectionComp(sec, comboTheme);
                addSectionComp(sec, cbLargeSteps);
                addSectionComp(sec, cbResetTimers);
                addSectionComp(sec, cbLoopWatch);
                addSectionComp(sec, lwTriggerLbl);
                addSectionComp(sec, lwTriggerSlider);
                addSectionComp(sec, lwLogoutLbl);
                addSectionComp(sec, lwLogoutSlider);
                addSectionComp(sec, cbPanelLogout);
                addSectionComp(sec, webGuiLbl);
                addSectionComp(sec, webGuiField);
                addSectionComp(sec, btnOpenWebGui);
                addSectionComp(sec, btnSwitchNow);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("📍 Tile presets (snel)", false, false, sec -> {
                addSectionComp(sec, tileHint);
                addSectionComp(sec, tileCountLabel);
                addSectionComp(sec, cbShowTiles);
                addSectionComp(sec, btnClearTiles);
                addSectionComp(sec, cbWallsOverlay);
                addSectionComp(sec, wallsRadiusLbl);
                addSectionComp(sec, wallsRadiusSlider);
                addSectionComp(sec, btnClearWalls);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("💾 Zelfde instellingen op andere PC", false, false, sec -> {
                addSectionComp(sec, hintLabel("Storm-accounts / login-lijst importeren (Accounts-tab dekt beheer):"));
                addSectionComp(sec, btnImportStorm);
                addSectionComp(sec, btnImportDefault);
                addSectionComp(sec, btnRefresh);
                addSectionComp(sec, comingSoon("Volledige LoneBot-config export/import volgt later."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🖼 Overlay Settings", false, false, sec -> {
                addSectionComp(sec, cbStatusOverlay);
                addSectionComp(sec, cbPaintMin);
                addSectionComp(sec, hintLabel("Paint-secties (voortgang/doel/…) → tab Debug → Paint secties"));
                addSectionComp(sec, cbMouseDebug);
                addSectionComp(sec, cbImpDebug);
                addSectionComp(sec, cbWcDebug);
                addSectionComp(sec, cbFishDebug);
                addSectionComp(sec, cbCowDebug);
                addSectionComp(sec, cbImpHuntOverlay);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("⚔ Combat Settings", false, false, sec -> {
                addSectionComp(sec, hintLabel("Cow / combat → tab 🎯 Skills → Combat"));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("💰 Loot Settings", false, false, sec -> {
                addSectionComp(sec, hintLabel("Imps loot pickup (ook onder Skills → Imps):"));
                LoneBotWcImpSettingsUi.fillImpsLootPickup(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🏦 Banking", false, false, sec -> {
                addSectionComp(sec, comingSoon("Gedeeld bank/GE-menu volgt."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🪓 Woodcutting", false, true, sec -> {
                addSectionComp(sec, hintLabel("FM/drop/boom → Skills → Woodcut. Locatie → Centers hieronder."));
                LoneBotWcImpSettingsUi.fillWoodcutDelays(sec, wcImpHost(), null);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("⛏ Mining", false, false, sec -> {
                addSectionComp(sec, comingSoon("Mining-handler nog niet in LoneBot."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🐟 Fishing", false, false, sec -> {
                addSectionComp(sec, hintLabel("Methode/drop/cook → Skills → Fishing. Locatie → Centers."));
                LoneBotFishSettingsUi.fillDelays(sec, wcImpHost(), null);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🚶 Lopen / Travel", true, true, sec -> {
                addSectionComp(sec, hintLabel("Ver: hops. Dichtbij: 1× eindklik. Pauzes hieronder. ESC stopt WorldWalker."));
                addSectionComp(sec, createStopWorldWalkerButton());
                addSectionComp(sec, cbWalkMini);
                addSectionComp(sec, cbWalkMiniZoom);
                addSectionComp(sec, cbWalkCanvas);
                addSectionComp(sec, cbWalkUi);
                addSectionComp(sec, cbWalkCam);
                addSectionComp(sec, cbWalkInvoke);
                addSectionComp(sec, cbWalkObstacleScan);
                addSectionComp(sec, cbWalkShortcuts);
                addSectionComp(sec, cbWalkFar);
                addSectionComp(sec, cbWalkTwin);
                addSectionComp(sec, cbWalkAutoRun);
                addSectionComp(sec, walkAutoRunLbl);
                addSectionComp(sec, walkAutoRunSlider);
                addSectionComp(sec, cbWalkLarge);
                addSectionComp(sec, walkStepLabel);
                addSectionComp(sec, new JLabel("Stap min:"));
                addSectionComp(sec, walkStepMinSlider);
                addSectionComp(sec, new JLabel("Stap max:"));
                addSectionComp(sec, walkStepMaxSlider);
                addSectionComp(sec, walkFinalLabel);
                addSectionComp(sec, walkFinalSlider);
                addSectionComp(sec, walkPostLabel);
                addSectionComp(sec, new JLabel("Pauze na klik min:"));
                addSectionComp(sec, walkPostMinSlider);
                addSectionComp(sec, new JLabel("Pauze na klik max:"));
                addSectionComp(sec, walkPostMaxSlider);
                addSectionComp(sec, walkChainLabel);
                addSectionComp(sec, new JLabel("Hop-doorlink min:"));
                addSectionComp(sec, walkChainMinSlider);
                addSectionComp(sec, new JLabel("Hop-doorlink max:"));
                addSectionComp(sec, walkChainMaxSlider);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🔄 Skill Rotation", false, false, sec -> {
                addSectionComp(sec, comingSoon("Skill-rotatie (CombatBot) volgt later."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🔁 Re-log (zelfde account)", false, false, sec -> {
                addSectionComp(sec, hintLabel(
                        "Timer uit → pauze op login-scherm → Play Now + CLICK HERE TO PLAY. Leeg account = deze launcher-client."));
                addSectionComp(sec, LoneBotBreakBar.buttonAndTimer(configManager, config));
                addSectionComp(sec, cbRelog);
                addSectionComp(sec, relogMinLbl);
                addSectionComp(sec, relogMinSlider);
                addSectionComp(sec, relogMaxLbl);
                addSectionComp(sec, relogMaxSlider);
                addSectionComp(sec, relogPauseMinLbl);
                addSectionComp(sec, relogPauseMinSlider);
                addSectionComp(sec, relogPauseMaxLbl);
                addSectionComp(sec, relogPauseMaxSlider);
                addSectionComp(sec, relogAccLbl);
                addSectionComp(sec, relogAccField);
                addSectionComp(sec, cbLoginNow);
                addSectionComp(sec, playYLbl);
                addSectionComp(sec, playYSlider);
                addSectionComp(sec, playWLbl);
                addSectionComp(sec, playWSlider);
                addSectionComp(sec, playHLbl);
                addSectionComp(sec, playHSlider);
                addSectionComp(sec, playMapLbl);
                addSectionComp(sec, comboPlayMap);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🔔 Discord", false, false, sec -> {
                addSectionComp(sec, hintLabel(
                        "Webhook: kanaal → Integraties → Webhook. Logs bij Stop, LOOP, of elke ~10 min."));
                JTextField webhookField = new JTextField(
                        config.activityDiscordWebhook() != null ? config.activityDiscordWebhook() : "", 22);
                webhookField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                webhookField.setToolTipText("https://discord.com/api/webhooks/...");
                webhookField.addActionListener(e -> applyActivityWebhook(webhookField.getText()));
                webhookField.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        applyActivityWebhook(webhookField.getText());
                    }
                });
                LoneBotUiTheme.mark(webhookField, LoneBotUiTheme.ROLE_INPUT);
                webhookField.setBackground(LoneBotUiTheme.INPUT_BG);
                webhookField.setForeground(LoneBotUiTheme.INPUT_FG);
                webhookField.setCaretColor(LoneBotUiTheme.INPUT_FG);
                addSectionComp(sec, webhookField);
                applyActivityWebhook(webhookField.getText());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🛒 GE Verkoop", false, false, sec -> {
                addSectionComp(sec, hintLabel("Imp GE → Skills → Imps"));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🛡 Anti-Ban", true, true, sec -> {
                addSectionComp(sec, cbAb);
                addSectionComp(sec, cbAbCam);
                addSectionComp(sec, cbAbIdle);
                addSectionComp(sec, cbAbMouse);
                addSectionComp(sec, cbAbKeys);
                addSectionComp(sec, cbAbMis);
                addSectionComp(sec, cbAbTab);
                addSectionComp(sec, cbAbFidget);
                addSectionComp(sec, abFreqLabel);
                addSectionComp(sec, abFreqSlider);
                addSectionComp(sec, cbRandomEv);
                addSectionComp(sec, new JLabel("Genie lamp skill:"));
                addSectionComp(sec, comboGenie);
                addSectionComp(sec, speedLabel);
                addSectionComp(sec, speedSlider);
                addSectionComp(sec, comboMouse);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("💬 CC / Chat", false, false, sec -> {
                addSectionComp(sec, comingSoon("CC/chat-opties volgen later."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🎯 Imps Mode (Karamja)", false, true, sec -> {
                LoneBotWcImpSettingsUi.fillImpsBasis(sec, wcImpHost(), null, null);
                sec.add(Box.createVerticalStrut(4));
                LoneBotWcImpSettingsUi.fillImpsHunt(sec, wcImpHost());
                sec.add(Box.createVerticalStrut(4));
                LoneBotWcImpSettingsUi.fillImpsGoblin(sec, wcImpHost());
                sec.add(Box.createVerticalStrut(4));
                LoneBotWcImpSettingsUi.fillImpsScorpions(sec, wcImpHost());
                sec.add(Box.createVerticalStrut(4));
                LoneBotWcImpSettingsUi.fillImpsGeTeleports(sec, wcImpHost());
                sec.add(Box.createVerticalStrut(4));
                LoneBotWcImpSettingsUi.fillImpsMeleeOpener(sec, wcImpHost());
                sec.add(Box.createVerticalStrut(4));
                LoneBotWcImpSettingsUi.fillImpsStepsRally(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🗡 Giants Mode (Edgeville Dungeon)", false, false, sec -> {
                LoneBotGiantsSettingsUi.fillBasis(sec, wcImpHost(), null);
                sec.add(Box.createVerticalStrut(4));
                LoneBotGiantsSettingsUi.fillFood(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("📜 Quest Bot", false, false, sec -> {
                LoneBotQuestSettingsUi.fill(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🛡 Stronghold of Security (SOS)", false, false, sec -> {
                addSectionComp(sec, comingSoon("SOS Flesh Crawler / Minotaur volgt later."));
            }));
        }));
        mainTabs.addTab("Settings", panelSettings);

        skillTabs = new JTabbedPane();
        polishMainTabs(skillTabs);
        skillTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        panelGeneral = wrapTabScroll(buildTabColumn(col -> {
            col.add(createScriptsToolbar());
            col.add(Box.createVerticalStrut(8));
            col.add(createCollapsibleSection("▶ Bot Control", true, true, sec -> {
                addSectionComp(sec, hintLabel("Master Start/Stop: Accounts → Besturing of Settings → Bot Control."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🚶 Lopen / Travel", false, false, sec -> {
                addSectionComp(sec, hintLabel("Walk-opties → Settings → 🚶 Lopen / Travel"));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🏦 Banking & food (Combat/Giants)", false, false, sec -> {
                addSectionComp(sec, hintLabel("Giants heeft een eigen food-bank, los van Monk/global."));
                LoneBotGiantsSettingsUi.fillFood(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("Example (loop smoke)", false, false, sec -> {
                addSectionComp(sec, hintLabel("LoneBot Example — smoke-test loop + random muis"));
                addSectionComp(sec, cbRandom);
            }));
        }));
        skillTabs.addTab("General", panelGeneral);

        panelCombat = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("⚔ Combat (basis)", false, true, sec -> {
                addSectionComp(sec, cbMonk);
                addSectionComp(sec, hintLabel("Monastery monks · center 3051,3492 r15"));
                addSectionComp(sec, cbCow);
                addSectionComp(sec, cbLoot);
                addSectionComp(sec, cbCombatClue);
                addSectionComp(sec, monkEatLbl);
                addSectionComp(sec, monkEatSlider);
                addSectionComp(sec, monkCritLbl);
                addSectionComp(sec, monkCritSlider);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🍗 Monk — food & bank", false, true, sec -> {
                addSectionComp(sec, cbMonkHealTalk);
                addSectionComp(sec, monkFoodAmtLbl);
                addSectionComp(sec, monkFoodAmtSlider);
                addSectionComp(sec, cbMonkCabbage);
                addSectionComp(sec, monkCabAmtLbl);
                addSectionComp(sec, monkCabAmtSlider);
                addSectionComp(sec, hintLabel("Food-bron:"));
                addSectionComp(sec, comboMonkFoodSrc);
                addSectionComp(sec, cbMonkBank);
                addSectionComp(sec, cbMonkLogout);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🏹 Pijlen & runes", false, false, sec -> {
                addSectionComp(sec, comingSoon("Pijlen/runes banking volgt met combat-port."));
            }));
            col.add(createCollapsibleSection("🧞 Genie lamp (random event)", false, false, sec -> {
                addSectionComp(sec, hintLabel("Genie lamp → Settings → Anti-Ban"));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("💰 Loot", false, false, sec -> {
                addSectionComp(sec, hintLabel("Cow loot-toggle zit bij Combat (basis)."));
            }));
        }));
        skillTabs.addTab("Combat", panelCombat);

        panelWoodcut = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("🪓 Woodcutting (basis)", false, true, sec -> {
                LoneBotWcImpSettingsUi.fillWoodcutBasis(sec, wcImpHost(), cbWc);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("⏱ WC delays & overlay", false, false, sec -> {
                LoneBotWcImpSettingsUi.fillWoodcutDelays(sec, wcImpHost(), cbWcDebug);
            }));
        }));
        skillTabs.addTab("Woodcut", panelWoodcut);

        panelMining = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("⛏ Mining (basis)", false, true, sec -> {
                addSectionComp(sec, comingSoon("Mining-handler nog niet geport."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("⏱ Mining delays & overlay", false, false, sec -> {
                addSectionComp(sec, comingSoon("Mining delays/overlay volgen."));
            }));
        }));
        skillTabs.addTab("Mining", panelMining);

        panelFishing = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("🐟 Fishing (basis)", false, true, sec -> {
                LoneBotFishSettingsUi.fillBasis(sec, wcImpHost(), cbFish);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🎣 Bait, bank & GE", false, false, sec -> {
                LoneBotFishSettingsUi.fillBaitBankGe(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("⏱ Fishing delays & overlay", false, false, sec -> {
                LoneBotFishSettingsUi.fillDelays(sec, wcImpHost(), cbFishDebug);
            }));
        }));
        skillTabs.addTab("Fishing", panelFishing);

        panelStar = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("⭐ Live sterren", false, true, sec -> {
                addSectionComp(sec, hintLabel(
                        "Wereld + locatie, nieuwste call bovenaan. Leeftijd · W-nummer · plek."));
                addSectionComp(sec, new LoneBotStarFeedView());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("⭐ Star Miner — filters", false, true, sec -> {
                LoneBotStarSettingsUi.fillBasis(sec, wcImpHost(), cbStarDebug);
            }));
        }));
        skillTabs.addTab("Star", panelStar);

        panelImps = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("🎯 Imps — basis", false, true, sec -> {
                LoneBotWcImpSettingsUi.fillImpsBasis(sec, wcImpHost(), cbImps, comboImpStyle);
                addSectionComp(sec, hintLabel("Nieuwe bouw: Imps2 (alleen lopen voor nu)."));
                addSectionComp(sec, cbImps2Script);
                addSectionComp(sec, cbImps2Clue);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("📍 Jachtgebied (fallback X/Y)", false, false, sec -> {
                LoneBotWcImpSettingsUi.fillImpsHunt(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🪙 Goblin coin-recovery gebied", false, false, sec -> {
                LoneBotWcImpSettingsUi.fillImpsGoblin(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🦂 Scorpions", false, false, sec -> {
                LoneBotWcImpSettingsUi.fillImpsScorpions(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🛒 GE verkoop & teleports", false, false, sec -> {
                LoneBotWcImpSettingsUi.fillImpsGeTeleports(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("⚔ Melee opener (Air Strike)", false, false, sec -> {
                LoneBotWcImpSettingsUi.fillImpsMeleeOpener(sec, wcImpHost());
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🚶 Loop-stappen & rally", false, false, sec -> {
                LoneBotWcImpSettingsUi.fillImpsStepsRally(sec, wcImpHost());
                addSectionComp(sec, cbImpDebug);
            }));
        }));
        skillTabs.addTab("Imps", panelImps);

        panelImps2 = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("🎯 Imps2 — walk-test", false, true, sec -> {
                addSectionComp(sec, hintLabel(
                        "Imps2 v" + livePluginVersion(BotRuntime.imps2PluginVersion,
                                "com.lonebot.example.Imps2Plugin")
                                + " — fase 1: lopen. 30 gp voor de boot."));
                addSectionComp(sec, cbImps2Walk);
                addSectionComp(sec, cbImps2Clue);
                addSectionComp(sec, hintLabel(
                        "Route: overal → Port Sarim dock → Musa → imps → terug dock → Draynor. "
                                + "Ook kiezen via sidebar-script of ↺ Imps2."));
            }));
        }));
        skillTabs.addTab("Imps2", panelImps2);

        panelGiants = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("🗡 Giants — basis", false, true, sec -> {
                LoneBotGiantsSettingsUi.fillBasis(sec, wcImpHost(), cbGiants);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("💰 Loot & overlay", false, false, sec -> {
                LoneBotGiantsSettingsUi.fillLoot(sec, wcImpHost(), cbGiantsDebug);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🍗 Food banking (onafhankelijk)", false, true, sec -> {
                LoneBotGiantsSettingsUi.fillFood(sec, wcImpHost());
            }));
        }));
        skillTabs.addTab("Giants", panelGiants);

        panelClue = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("📜 Beginner Clue", false, true, sec -> {
                LoneBotClueSettingsUi.fill(sec, wcImpHost());
            }));
        }));
        skillTabs.addTab("Clue", panelClue);

        panelQuest = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("📜 Quest Bot", false, true, sec -> {
                LoneBotQuestSettingsUi.fill(sec, wcImpHost());
            }));
        }));
        skillTabs.addTab("Quest", panelQuest);

        panelSos = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("🛡 SOS — rotatie / start", false, true, sec -> {
                addSectionComp(sec, comingSoon("SOS-rotatie volgt."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🕷 Flesh Crawlers — overlay", false, true, sec -> {
                addSectionComp(sec, comingSoon("Flesh Crawler overlay volgt."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🛡 Minotaurs — combat & loot", false, true, sec -> {
                addSectionComp(sec, comingSoon("SOS Minotaur combat volgt."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("💰 Minotaurs — loot & overlay", false, false, sec -> {
                addSectionComp(sec, comingSoon("Minotaur loot/overlay volgt."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🍗 Minotaurs — food & bank (onafhankelijk)", false, true, sec -> {
                addSectionComp(sec, comingSoon("Minotaur food/bank volgt."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🔍 Minotaurs — debug", false, true, sec -> {
                addSectionComp(sec, comingSoon("Minotaur debug volgt."));
            }));
        }));
        skillTabs.addTab("SOS", panelSos);

        panelBarb = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("🛡 Barbarian (Longhall) — basis", false, true, sec -> {
                addSectionComp(sec, comingSoon("Barbarian Longhall volgt."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("📍 Longhall gebied", false, false, sec -> {
                addSectionComp(sec, comingSoon("Longhall gebied volgt."));
            }));
        }));
        skillTabs.addTab("Barbarian", panelBarb);

        String initialPage = skillPageFor(BotRuntime.activeSkill);
        selectSkillTab(initialPage);
        skillsWrap = skillTabs;
        mainTabs.addTab("Skills", skillsWrap);

        JLabel lblRuntime = new JLabel("00:00:00");
        JLabel lblActiveSkill = new JLabel("-");
        JLabel lblBotStatus = new JLabel(BotRuntime.botEnabled ? "Aan" : "Uit");
        JLabel lblWcXpStat = new JLabel("-");
        JLabel lblImpStat = new JLabel("-");
        JLabel lblCowStat = new JLabel("-");
        JLabel lblMonkStat = new JLabel("-");
        JLabel lblFishStat = new JLabel("-");
        styleLabel(lblRuntime);
        styleLabel(lblActiveSkill);
        styleLabel(lblBotStatus);
        styleLabel(lblWcXpStat);
        styleLabel(lblImpStat);
        styleLabel(lblCowStat);
        styleLabel(lblMonkStat);
        styleLabel(lblFishStat);
        javax.swing.Timer statsTimer = new javax.swing.Timer(1000, ev -> {
            lblBotStatus.setText(BotRuntime.botEnabled
                    ? (BotRuntime.pausedForResume ? "Pauze" : "Aan")
                    : "Uit");
            lblActiveSkill.setText(activeSkillWithVersion());
            refreshSkillCheckLabels(cbImps, cbWc, cbFish, cbGiants);
            refreshScriptVersionHeader();
            lblImpStat.setText(trimStat(BotRuntime.impStatus));
            lblCowStat.setText(trimStat(BotRuntime.cowStatus));
            lblMonkStat.setText(trimStat(BotRuntime.monkStatus));
            lblWcXpStat.setText(trimStat(BotRuntime.wcStatus));
            lblFishStat.setText(trimStat(BotRuntime.fishStatus));
            long sec = AccountSessionTimers.elapsedSec(LoneBotBotControl.launchedAccountName());
            lblRuntime.setText(String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60));
        });
        statsTimer.setRepeats(true);
        statsTimer.start();

        panelStats = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("Status", true, true, sec -> {
                addSectionComp(sec, rowLabel("Runtime:", lblRuntime));
                addSectionComp(sec, rowLabel("Actieve skill:", lblActiveSkill));
                addSectionComp(sec, rowLabel("Bot:", lblBotStatus));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("XP Verdiend", false, true, sec -> {
                addSectionComp(sec, hintLabel("XP-tellers op canvas control panel; hier later CombatBot-stats."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("Statistieken", false, true, sec -> {
                addSectionComp(sec, rowLabel("Imp status:", lblImpStat));
                addSectionComp(sec, rowLabel("Cow status:", lblCowStat));
                addSectionComp(sec, rowLabel("Monk status:", lblMonkStat));
                addSectionComp(sec, rowLabel("WC status:", lblWcXpStat));
                addSectionComp(sec, rowLabel("Fish status:", lblFishStat));
            }));
        }));
        mainTabs.addTab("Stats", panelStats);

        panelCenters = wrapTabScroll(buildTabColumn(col -> {
            JLabel centersInfo = new JLabel("<html><b>Center locaties per skill</b><br>"
                    + "In-game: <b>rechtsklik op tegel</b> → Voeg center / Radius ± / Verwijder<br>"
                    + "(Combat · WC · Mining · Fishing — elk apart aan/uit).<br>"
                    + "<span style='color:#a0a0b0;font-size:10px'>Mining-script volgt later; Fishing zit onder Skills → Fishing.</span></html>");
            styleLabel(centersInfo);
            col.add(centersInfo);
            col.add(Box.createVerticalStrut(8));
            col.add(createCollapsibleSection("⚔ Combat", false, true, sec -> {
                addSectionComp(sec, cbCombatCentersMenu);
                addSectionComp(sec, cbCombatAreaOv);
                addSectionComp(sec, combatLocLabel);
                addSectionComp(sec, comboCombatLoc);
                addSectionComp(sec, btnRefreshCombatLoc);
                addSectionComp(sec, combatRename.editLabel);
                addSectionComp(sec, combatRename.comboEdit);
                addSectionComp(sec, combatRename.tfName);
                addSectionComp(sec, combatRename.btnSave);
                addSectionComp(sec, btnResetCombat);
                addSectionComp(sec, hintLabel(
                        "Default: Varrock guards. Nieuw center → Vernieuw. Naam wijzigen: kies center → typ naam → Opslaan naam."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🪓 Woodcutting", false, true, sec -> {
                addSectionComp(sec, cbWcCentersMenu);
                addSectionComp(sec, cbWcAreaOv);
                addSectionComp(sec, wcLocLabel);
                addSectionComp(sec, comboWcLoc);
                addSectionComp(sec, btnRefreshWcLoc);
                addSectionComp(sec, wcEditLabel);
                addSectionComp(sec, comboWcEdit);
                addSectionComp(sec, tfWcName);
                addSectionComp(sec, btnSaveWcName);
                addSectionComp(sec, btnResetWcCenters);
                addSectionComp(sec, hintLabel(
                        "Nieuw center → Vernieuw. Naam wijzigen: kies center → typ naam → Opslaan naam."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("⛏ Mining centers", false, true, sec -> {
                addSectionComp(sec, cbMiningCentersMenu);
                addSectionComp(sec, cbMiningAreaOv);
                addSectionComp(sec, miningLocLabel);
                addSectionComp(sec, comboMiningLoc);
                addSectionComp(sec, btnRefreshMiningLoc);
                addSectionComp(sec, miningRename.editLabel);
                addSectionComp(sec, miningRename.comboEdit);
                addSectionComp(sec, miningRename.tfName);
                addSectionComp(sec, miningRename.btnSave);
                addSectionComp(sec, btnResetMining);
                addSectionComp(sec, hintLabel(
                        "Defaults: Varrock east / lumb south / draynor / alkarid. Naam wijzigen: kies center → typ naam → Opslaan naam."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🐟 Fishing", false, true, sec -> {
                addSectionComp(sec, cbFishingCentersMenu);
                addSectionComp(sec, cbFishingAreaOv);
                addSectionComp(sec, fishLocLabel);
                addSectionComp(sec, comboFishLoc);
                addSectionComp(sec, btnRefreshFishLoc);
                addSectionComp(sec, fishRename.editLabel);
                addSectionComp(sec, fishRename.comboEdit);
                addSectionComp(sec, fishRename.tfName);
                addSectionComp(sec, fishRename.btnSave);
                addSectionComp(sec, btnResetFishing);
                addSectionComp(sec, hintLabel(
                        "Defaults: Barbarian village + Draynor village. Naam wijzigen: kies center → typ naam → Opslaan naam."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🎯 Imps Mode (Karamja)", false, true, sec -> {
                addSectionComp(sec, huntRLabel);
                addSectionComp(sec, huntRSlider);
                addSectionComp(sec, hintLabel("X/Y + scorpions → Skills → Imps. Overlay → Settings → Overlay."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🗡 Giants (Edgeville Dungeon)", false, false, sec -> {
                addSectionComp(sec, hintLabel(
                        "Vaste F2P-tiles: hunt 3115,9837 r24 · shed ~3115,3452 + Brass key · "
                                + "Edge trapdoor 3097,3468 id 12342 · key-spawn 3131,9862."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🕷 SOS Flesh Crawler", false, false, sec -> {
                addSectionComp(sec, comingSoon("SOS FC centers volgen."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🛡 SOS Minotaurs", false, false, sec -> {
                addSectionComp(sec, comingSoon("SOS Minotaur centers volgen."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🛡 Barbarian (Longhall)", false, false, sec -> {
                addSectionComp(sec, comingSoon("Barbarian centers volgen."));
            }));
        }));
        mainTabs.addTab("Centers", panelCenters);

        panelCapture = buildCaptureTab();
        mainTabs.addTab("Developer Tools", panelCapture);

        panelDebug = new JPanel(new BorderLayout(0, 4));
        panelDebug.setOpaque(false);
        panelDebug.add(wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("⚙ Debug opties", false, true, sec -> {
                addSectionComp(sec, createRuneliteDeveloperModeCheck());
                addSectionComp(sec, hintLabel(
                        "RuneLite DevTools: Players / NPCs / Objects / Widget Inspector — sidebar bug-icoon na client-herstart."));
                addSectionComp(sec, cbCanvasDebug);
                addSectionComp(sec, hintLabel("Canvas path / steden / target · WC centers-menu → Centers-tab"));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🖼 Paint secties (goud paneel)", false, true, sec -> {
                addSectionComp(sec, hintLabel("Welke blokken in het gouden statuspaneel zichtbaar zijn."));
                addSectionComp(sec, cbPaintProgress);
                addSectionComp(sec, cbPaintTarget);
                addSectionComp(sec, cbPaintAction);
                addSectionComp(sec, cbPaintKit);
                addSectionComp(sec, cbPaintBank);
                addSectionComp(sec, cbPaintAb);
                addSectionComp(sec, cbPaintScriptDbg);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🖼 Overlays (ook zonder bot)", false, false, sec -> {
                addSectionComp(sec, hintLabel("Grond/muis overlays → Settings → Overlay Settings"));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🔧 Tools & logbestanden", false, false, sec -> {
                addSectionComp(sec, btnClearConsole);
                addSectionComp(sec, cbConsoleAutoScroll);
                addSectionComp(sec, hintLabel("Console-log hieronder. Auto-scroll uit = stil blijven bij kopiëren."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("📋 Log bronnen filter", false, false, sec -> {
                addSectionComp(sec, comingSoon("Log-bronfilter zoals CombatBot DebugLog volgt."));
            }));
        })), BorderLayout.NORTH);
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setBorder(null);
        consoleScroll.setPreferredSize(new Dimension(100, 220));
        panelDebug.add(consoleScroll, BorderLayout.CENTER);
        mainTabs.addTab("Debug", panelDebug);

        panelTest = wrapTabScroll(buildTabColumn(col -> {
            col.add(createCollapsibleSection("ℹ Build info", false, true, sec -> {
                addSectionComp(sec, hintLabel("LoneBot v" + LoneBotBootstrapPlugin.VERSION
                        + " | " + scriptVersionsLine()));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🚪 Logout & hop tests", false, true, sec -> {
                addSectionComp(sec, hintLabel(
                        "Client-herstart nodig (niet ↻ Scripts). Hop-confirm openen in-game, dan Switch world."));
                addSectionComp(sec, btnTestHopConfirm);
                addSectionComp(sec, btnTestLogoutAuto);
                addSectionComp(sec, btnTestLogoutConfirm);
                addSectionComp(sec, btnTestLogout12);
                addSectionComp(sec, btnTestLogoutFirst);
                addSectionComp(sec, btnTestLogoutPacked);
                addSectionComp(sec, btnTestLogoutMenu);
                addSectionComp(sec, btnTestLogoutTab);
                addSectionComp(sec, btnTestAutoLogin);
                addSectionComp(sec, btnTestJagexPlayNow);
                addSectionComp(sec, btnTestWelcomePlay);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("📦 Deposit qty (Imps dump All)", false, true, sec -> {
                addSectionComp(sec, hintLabel(
                        "Deposit box open. UIT = Default quantity-actie aanwezig. AAN = actie weg (niet klikken)."));
                addSectionComp(sec, btnDumpQty);
                addSectionComp(sec, qtyTestRow);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🧪 Interactie tests", false, true, sec -> {
                addSectionComp(sec, cbVarrockBank);
                addSectionComp(sec, cbCityCircle);
                addSectionComp(sec, btnTestInvokeWalk);
                addSectionComp(sec, createStopWorldWalkerButton());
                addSectionComp(sec, btnTestRunRead);
                addSectionComp(sec, btnTestRunOn);
                addSectionComp(sec, btnTestRunOff);
                addSectionComp(sec, btnTestMmb);
                addSectionComp(sec, btnTestDbl);
                addSectionComp(sec, btnTestRandom);
                addSectionComp(sec, btnTestMouse);
                addSectionComp(sec, btnMoveCow);
                addSectionComp(sec, btnClickHull);
                addSectionComp(sec, btnClickL2c);
                addSectionComp(sec, btnMenu);
                addSectionComp(sec, autocastHint);
                addSectionComp(sec, comboAutocastMethod);
                addSectionComp(sec, comboAutocastSpell);
                addSectionComp(sec, btnAutocastTest);
                addSectionComp(sec, teleHint);
                addSectionComp(sec, comboTeleMethod);
                addSectionComp(sec, comboTeleSpell);
                addSectionComp(sec, btnTeleTest);
                addSectionComp(sec, btnTeleAllMethods);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🛒 Grand Exchange test", false, true, sec -> {
                addSectionComp(sec, hintLabel(
                        "Ingelogd account loopt naar de GE en plaatst de offer. Andere skills gaan uit; Bot AAN. "
                                + "Koop: exacte GE-naam. Verkoop: item moet in inventory staan."));
                addSectionComp(sec, hintLabel("Koop — item + aantal + gp per stuk"));
                addSectionComp(sec, geBuyRow);
                addSectionComp(sec, hintLabel("Verkoop — item + aantal + gp per stuk"));
                addSectionComp(sec, geSellRow);
                addSectionComp(sec, btnGeStop);
                addSectionComp(sec, geTestStatus);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🔥 FM light tests (tinderbox→logs)", true, true, sec -> {
                addSectionComp(sec, fmLightHint);
                addSectionComp(sec, comboFmLight);
                addSectionComp(sec, btnFmLightTest);
                addSectionComp(sec, btnFmLightAll);
                addSectionComp(sec, btnFmDiagnose);
                addSectionComp(sec, btnAnimDump);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🐭 Anti-ban muis tests", false, true, sec -> {
                addSectionComp(sec, hintLabel("Muis-backend + speed → Settings → Anti-Ban. Tests → sectie Interactie tests hierboven."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🐂 SOS Minotaur walk-test", false, true, sec -> {
                addSectionComp(sec, comingSoon("SOS Minotaur walk-test volgt met SOS-port."));
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("🎭 Emote test (performEmote)", false, true, sec -> {
                addSectionComp(sec, hintLabel(
                        "Open Emotes-tab in-game → klik een knop hier. Log: console / [Emote]."));
                JPanel emoteRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
                emoteRow.setOpaque(false);
                String[][] emoteTests = {
                        {"Bow", "bow"},
                        {"Cheer", "cheer"},
                        {"Clap", "clap"},
                        {"Panic", "panic"},
                        {"Raspberry", "raspberry"},
                        {"Spin", "spin"},
                        {"Yes", "yes"},
                        {"No", "no"},
                        {"Wave", "wave"},
                };
                for (String[] pair : emoteTests) {
                    JButton btn = new JButton(pair[0]);
                    btn.setToolTipText("performEmote(\"" + pair[1] + "\")");
                    btn.addActionListener(e -> clientThread.invoke(() -> {
                        boolean ok = EmoteHelper.performEmote(pair[1]);
                        SwingUtilities.invokeLater(() -> setStatus(ok
                                ? "✓ Emote " + pair[0]
                                : "Emote " + pair[0] + " mislukt — Emotes-tab open?"));
                    }));
                    emoteRow.add(btn);
                }
                JButton rescan = new JButton("Rescan emotes");
                rescan.setToolTipText("Scan open emote-panel → Capture-log");
                rescan.addActionListener(e -> clientThread.invoke(() -> {
                    for (String line : EmoteWidgetRegistry.rescanAndLogLines()) {
                        HoverCaptureLog.add("emote-scan", line, line);
                        BotRuntime.logConsole("[Emote] " + line);
                    }
                    SwingUtilities.invokeLater(() -> setStatus("Emote rescan klaar"));
                }));
                emoteRow.add(rescan);
                addSectionComp(sec, emoteRow);
            }));
            col.add(Box.createVerticalStrut(6));
            col.add(createCollapsibleSection("Walk / camera tests", false, true, sec -> {
                JLabel camHint = new JLabel("<html><i>Preview = alleen camera. Walk+camera = echt lopen.</i></html>");
                styleLabel(camHint);
                addSectionComp(sec, camHint);
                addSectionComp(sec, cbWalkCamOn);
                addSectionComp(sec, cbWalkCamMmb);
                addSectionComp(sec, cbWalkCamKb);
                addSectionComp(sec, new JLabel("Yaw mode:"));
                addSectionComp(sec, comboYawMode);
                addSectionComp(sec, yawOffLabel);
                addSectionComp(sec, yawOffSlider);
                addSectionComp(sec, pitchLabel);
                addSectionComp(sec, pitchSlider);
                addSectionComp(sec, skipLabel);
                addSectionComp(sec, skipSlider);
                addSectionComp(sec, zoomLabel);
                addSectionComp(sec, zoomSlider);
                addSectionComp(sec, btnTestWalkCam);
                addSectionComp(sec, btnTestWalkCamWalk);
                addSectionComp(sec, btnCamReport);
            }));
        }));
        mainTabs.addTab("Test", panelTest);

        for (int i = 0; i < mainTabs.getTabCount(); i++) {
            mainTabs.setForegroundAt(i, new Color(185, 192, 210));
            mainTabs.setBackgroundAt(i, new Color(42, 45, 56));
        }
        mainTabs.addChangeListener(e -> refreshTabColors(mainTabs));
        refreshTabColors(mainTabs);

        mainTabs.setSelectedIndex(0);

        statusLabel.setForeground(LoneBotUiTheme.TEXT_DIM);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));

        applyUiTheme(LoneBotUiTheme.resolve(
                config.uiTheme() != null ? config.uiTheme() : LoneBotUiTheme.Preset.BALANS));

        dzSidebar = new LoneBotDzSidebar(configManager, config, this::showControlWindow, skill -> {
            if (detachedFrame != null && detachedFrame.isVisible()) {
                showSkillSettingsPage(skill);
            }
        });
        add(dzSidebar, BorderLayout.CENTER);

        setPreferredSize(new Dimension(PANEL_WIDTH, 480));

        titleRefreshTimer = new Timer(1500, e -> refreshBoundAccountTitle());
        titleRefreshTimer.setRepeats(true);
        titleRefreshTimer.start();
        refreshBoundAccountTitle();

        SwingUtilities.invokeLater(() -> {
            ManagedAccountsStore.ensureLoaded();
            if (ManagedAccountsStore.getAccounts().isEmpty() && DEFAULT_STORM_JSON.isFile()) {
                StormAccountsBulkImport.Result r = StormAccountsBulkImport.importFile(DEFAULT_STORM_JSON);
                log.info("Auto-import Desktop storm-accountsjuli.json: {}", r.summary());
            }
            appendConsole("LoneBot panel klaar — tabs: Accounts / Settings / Skills / Stats / Centers / Developer Tools / Debug / Test");
            refreshCaptureLogView();
            setStatus(ManagedAccountsStore.getAccounts().size() + " account(s)");
            showControlWindow();
        });
    }

    private JPanel buildDevWidgetInspectorSection() {
        return createCollapsibleSection("🔎 Widget inspector + tools", false, true, sec -> {
            addSectionComp(sec, hintLabel(
                    "Alt ingedrukt: object-IDs highlighten met + / −. Alt+linksklik voegt de ID toe. "
                            + "Geen limiet op het aantal IDs."));
            addSectionComp(sec, hintLabel(
                    "Standaard filter: 7120–7126 (naamloze mijn-scenery) + naamloze objecten (null). "
                            + "Iron/Tin/Clay-rotsen blijven zichtbaar."));

            JCheckBox cbDevWidgetTip = new JCheckBox("Widget hover tooltip", config.devWidgetHoverTooltip());
            styleCheck(cbDevWidgetTip);
            cbDevWidgetTip.setToolTipText("iface / id / text / actions onder de muis");
            cbDevWidgetTip.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devWidgetHoverTooltip", cbDevWidgetTip.isSelected());
                setStatus(cbDevWidgetTip.isSelected() ? "✓ Widget tooltip AAN" : "Widget tooltip uit");
            });

            JCheckBox cbDevWidgetHi = new JCheckBox("Widget hover highlight", config.devWidgetHoverHighlight());
            styleCheck(cbDevWidgetHi);
            cbDevWidgetHi.setToolTipText("Paars kader om widget onder de muis");
            cbDevWidgetHi.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devWidgetHoverHighlight", cbDevWidgetHi.isSelected());
                setStatus(cbDevWidgetHi.isSelected() ? "✓ Widget highlight AAN" : "Widget highlight uit");
            });

            JCheckBox cbDevInvIds = new JCheckBox("Inventory item-IDs", config.devInventoryItemIds());
            styleCheck(cbDevInvIds);
            cbDevInvIds.setToolTipText("Item-ID op elk inventory-vakje (inventory-tab open)");
            cbDevInvIds.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devInventoryItemIds", cbDevInvIds.isSelected());
                setStatus(cbDevInvIds.isSelected() ? "✓ Inv item-IDs AAN" : "Inv item-IDs uit");
            });

            JCheckBox cbDevNpcIds = new JCheckBox("NPC IDs", config.devNpcIds());
            styleCheck(cbDevNpcIds);
            cbDevNpcIds.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devNpcIds", cbDevNpcIds.isSelected());
                setStatus(cbDevNpcIds.isSelected() ? "✓ NPC IDs AAN" : "NPC IDs uit");
            });

            JCheckBox cbDevObjIds = new JCheckBox("Object IDs", config.devObjectIds());
            styleCheck(cbDevObjIds);
            cbDevObjIds.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devObjectIds", cbDevObjIds.isSelected());
                setStatus(cbDevObjIds.isSelected() ? "✓ Object IDs AAN" : "Object IDs uit");
            });

            JCheckBox cbDevGroundIds = new JCheckBox("Ground item IDs", config.devGroundItemIds());
            styleCheck(cbDevGroundIds);
            cbDevGroundIds.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devGroundItemIds", cbDevGroundIds.isSelected());
                setStatus(cbDevGroundIds.isSelected() ? "✓ Ground IDs AAN" : "Ground IDs uit");
            });

            JCheckBox cbOidFilter = new JCheckBox("Object-ID filter", config.devObjectIdFilterEnabled());
            styleCheck(cbOidFilter);
            cbOidFilter.setToolTipText("Verberg IDs in de lijst hieronder");
            cbOidFilter.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devObjectIdFilterEnabled", cbOidFilter.isSelected());
                setStatus(cbOidFilter.isSelected() ? "✓ Object-ID filter AAN" : "Object-ID filter uit");
            });

            JCheckBox cbHideUnnamed = new JCheckBox("Verberg null objecten (ook bij Alt)",
                    config.devObjectIdFilterHideUnnamed());
            styleCheck(cbHideUnnamed);
            cbHideUnnamed.setToolTipText(
                    "Aan = geen 'OBJ null oid=…' labels, ook niet als je Alt vasthoudt. Uit = nulls zichtbaar bij Alt.");
            cbHideUnnamed.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devObjectIdFilterHideUnnamed",
                        cbHideUnnamed.isSelected());
                setStatus(cbHideUnnamed.isSelected() ? "✓ Null objecten verborgen" : "Null objecten zichtbaar");
            });
            LoneBotConfigUiSync.bool(cbHideUnnamed, "devObjectIdFilterHideUnnamed");

            JCheckBox cbAltEdit = new JCheckBox("Alt: highlight +/− / klik → filter",
                    config.devObjectIdFilterAltEdit());
            styleCheck(cbAltEdit);
            cbAltEdit.setToolTipText("Alt vasthouden toont + en −. Alt+linksklik voegt de object-ID toe.");
            cbAltEdit.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devObjectIdFilterAltEdit", cbAltEdit.isSelected());
                setStatus(cbAltEdit.isSelected() ? "✓ Alt object-filter AAN" : "Alt object-filter uit");
            });

            JCheckBox cbDevMiddle = new JCheckBox("Middenklik → Developer Tools", config.devHoverCaptureMiddleClick());
            styleCheck(cbDevMiddle);
            cbDevMiddle.setToolTipText("Middenmuisklik zet widget/NPC/object/tegel in de Developer Tools-log (niet Debug).");
            cbDevMiddle.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "devHoverCaptureMiddleClick", cbDevMiddle.isSelected());
                setStatus(cbDevMiddle.isSelected() ? "✓ Hover capture AAN" : "Hover capture uit");
            });
            LoneBotConfigUiSync.bool(cbDevMiddle, "devHoverCaptureMiddleClick");

            JCheckBox cbInspectEx = new JCheckBox("Middenklik: speler/menu/collision", config.captureInspectExtras());
            styleCheck(cbInspectEx);
            cbInspectEx.setToolTipText("Extra dumps: animatie/dest, menu-opcodes, collision-flags van de tegel");
            cbInspectEx.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "captureInspectExtras", cbInspectEx.isSelected());
                setStatus(cbInspectEx.isSelected() ? "✓ Inspect-extras AAN" : "Inspect-extras uit");
            });
            LoneBotConfigUiSync.bool(cbInspectEx, "captureInspectExtras");

            JCheckBox cbVarWatch = new JCheckBox("Varbit-watch", config.varWatchEnabled());
            styleCheck(cbVarWatch);
            cbVarWatch.setToolTipText("Log var-wijzigingen naar capture-log. Filter IDs leeg = alles (na skip noisy).");
            cbVarWatch.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "varWatchEnabled", cbVarWatch.isSelected());
                setStatus(cbVarWatch.isSelected() ? "✓ Varbit-watch AAN" : "Varbit-watch uit");
                BotRuntime.logConsole("[Inspect/var] watch " + (cbVarWatch.isSelected() ? "AAN" : "uit"));
            });
            LoneBotConfigUiSync.bool(cbVarWatch, "varWatchEnabled");

            JCheckBox cbVb = new JCheckBox("Watch varbits", config.varWatchVarbits());
            styleCheck(cbVb);
            cbVb.addActionListener(e ->
                    configManager.setConfiguration("lonebot", "varWatchVarbits", cbVb.isSelected()));
            LoneBotConfigUiSync.bool(cbVb, "varWatchVarbits");

            JCheckBox cbVp = new JCheckBox("Watch varps", config.varWatchVarps());
            styleCheck(cbVp);
            cbVp.addActionListener(e ->
                    configManager.setConfiguration("lonebot", "varWatchVarps", cbVp.isSelected()));
            LoneBotConfigUiSync.bool(cbVp, "varWatchVarps");

            JCheckBox cbVc = new JCheckBox("Watch varcs (interface)", config.varWatchVarcs());
            styleCheck(cbVc);
            cbVc.setToolTipText("Client-vars: bank/dialog/UI");
            cbVc.addActionListener(e ->
                    configManager.setConfiguration("lonebot", "varWatchVarcs", cbVc.isSelected()));
            LoneBotConfigUiSync.bool(cbVc, "varWatchVarcs");

            JCheckBox cbSkipNoisy = new JCheckBox("Skip noisy vars", config.varWatchSkipNoisy());
            styleCheck(cbSkipNoisy);
            cbSkipNoisy.setToolTipText("Mute IDs die >6× per 2s wijzigen (12s mute)");
            cbSkipNoisy.addActionListener(e ->
                    configManager.setConfiguration("lonebot", "varWatchSkipNoisy", cbSkipNoisy.isSelected()));
            LoneBotConfigUiSync.bool(cbSkipNoisy, "varWatchSkipNoisy");

            JTextField varFilterField = new JTextField(config.varWatchFilterIds(), 12);
            varFilterField.setToolTipText("Leeg = alles. Anders alleen: 123, vb:456, vp:173, vc:5");
            varFilterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            varFilterField.addActionListener(e ->
                    configManager.setConfiguration("lonebot", "varWatchFilterIds", varFilterField.getText()));
            LoneBotConfigUiSync.text(varFilterField, "varWatchFilterIds");

            JCheckBox cbWalkScan = new JCheckBox("Scan deuren/hekken/ladders", config.walkObstacleScanEnabled());
            styleCheck(cbWalkScan);
            cbWalkScan.setToolTipText("Achtergrond: slaat deuren, hekken, stiles, ladders, trappen op in ~/.lonebot/walk-obstacle-*.tsv");
            cbWalkScan.addActionListener(e -> {
                configManager.setConfiguration("lonebot", "walkObstacleScanEnabled", cbWalkScan.isSelected());
                setStatus(cbWalkScan.isSelected()
                        ? "✓ Obstacle-scan AAN → ~/.lonebot/walk-obstacle-*.tsv"
                        : "Obstacle-scan uit");
            });

            JLabel oidCount = new JLabel();
            styleLabel(oidCount);
            JTextField oidCsv = new JTextField(config.devObjectIdFilter(), 12);
            oidCsv.setToolTipText("Komma-gescheiden object-IDs — geen maximum");
            oidCsv.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            Runnable refreshOidUi = () -> {
                oidCount.setText("Geen limiet — nu " + ObjectIdFilterStore.size() + " IDs in filter");
                if (!oidCsv.hasFocus()) {
                    String s = ObjectIdFilterStore.serialize();
                    if (!s.equals(oidCsv.getText())) {
                        oidCsv.setText(s);
                    }
                }
            };
            ObjectIdFilterStore.addListener(() -> SwingUtilities.invokeLater(refreshOidUi));
            refreshOidUi.run();
            oidCsv.addActionListener(e -> ObjectIdFilterStore.replaceFromCsv(oidCsv.getText()));

            JButton btnApplyOid = new JButton("Filterlijst toepassen");
            btnApplyOid.addActionListener(e -> {
                ObjectIdFilterStore.replaceFromCsv(oidCsv.getText());
                setStatus("✓ Object-ID filter: " + ObjectIdFilterStore.size() + " IDs");
            });
            JButton btnResetOid = new JButton("Reset default (7120–7126)");
            btnResetOid.setToolTipText("Zet de lijst terug naar naamloze mijn-scenery 7120 t/m 7126");
            btnResetOid.addActionListener(e -> {
                ObjectIdFilterStore.resetToDefault();
                setStatus("✓ Object-ID filter reset (7 IDs)");
            });

            JTextField dumpFilterField = new JTextField(config.devWidgetDumpFilter(), 12);
            dumpFilterField.setToolTipText("Filter substring voor widget-dump (leeg = alles interessant)");
            dumpFilterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            dumpFilterField.addActionListener(e ->
                    configManager.setConfiguration("lonebot", "devWidgetDumpFilter", dumpFilterField.getText()));

            JButton btnDumpWidgets = new JButton("Dump widgets → Console");
            btnDumpWidgets.setToolTipText("Zichtbare widgets met naam/tekst/item/actions → Console-tab");
            btnDumpWidgets.addActionListener(e -> {
                String filter = dumpFilterField.getText() != null ? dumpFilterField.getText() : "";
                configManager.setConfiguration("lonebot", "devWidgetDumpFilter", filter);
                setStatus("Widget dump…");
                clientThread.invoke(() -> {
                    int n = net.runelite.client.plugins.lonebot.dev.WidgetDumpHelper.dumpVisible(client, filter, 400);
                    SwingUtilities.invokeLater(() -> setStatus("✓ Widget dump: " + n + " regels → Console"));
                });
            });

            addSectionComp(sec, cbDevWidgetTip);
            addSectionComp(sec, cbDevWidgetHi);
            addSectionComp(sec, cbDevInvIds);
            addSectionComp(sec, cbDevNpcIds);
            addSectionComp(sec, cbDevObjIds);
            addSectionComp(sec, cbDevGroundIds);
            addSectionComp(sec, cbOidFilter);
            addSectionComp(sec, cbHideUnnamed);
            addSectionComp(sec, cbAltEdit);
            addSectionComp(sec, cbDevMiddle);
            addSectionComp(sec, cbInspectEx);
            addSectionComp(sec, cbVarWatch);
            addSectionComp(sec, cbVb);
            addSectionComp(sec, cbVp);
            addSectionComp(sec, cbVc);
            addSectionComp(sec, cbSkipNoisy);
            addSectionComp(sec, new JLabel("Var-filter IDs:"));
            addSectionComp(sec, varFilterField);
            addSectionComp(sec, cbWalkScan);
            addSectionComp(sec, oidCount);
            addSectionComp(sec, oidCsv);
            addSectionComp(sec, btnApplyOid);
            addSectionComp(sec, btnResetOid);
            addSectionComp(sec, new JLabel("Dump filter:"));
            addSectionComp(sec, dumpFilterField);
            addSectionComp(sec, btnDumpWidgets);
        });
    }

    private JPanel buildCaptureTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel hint = new JLabel("<html><div style='color:#a8a8b8;font-size:10px'>"
                + "<b>Developer Tools</b> — widget inspector, object-ID filter, Storm-highlights + capture.<br>"
                + "<b>RuneLite developer mode</b> (hieronder): officiële DevTools in de sidebar (bug-icoon). "
                + "Client herstarten na toggle.<br>"
                + "World map of grond: rechtsklik <b>Walk here (Pathfind)</b> = webwalker.<br>"
                + "<b>Stop WorldWalker</b> of in-game <b>ESC</b> stopt het lopen.<br>"
                + "<b>Scan deuren/hekken/trappen</b> uitzetten: checkbox hieronder of bij Lopen / Travel (standaard uit).<br>"
                + "Middenklik → widget/NPC/object + speler/menu/collision.<br>"
                + "Rechtsklik <b>Dump menu → capture</b> voor opcodes. Varbit-watch: filter IDs of skip noisy.<br>"
                + "Get walls / coords blijven in de log hieronder.</div></html>");
        styleLabel(hint);

        JCheckBox cbRlDevMode = createRuneliteDeveloperModeCheck();

        JCheckBox cbDbgLog = new JCheckBox("Debug Logging", config.devDebugLogging());
        styleCheck(cbDbgLog);
        cbDbgLog.addActionListener(e -> {
            configManager.setConfiguration("lonebot", "devDebugLogging", cbDbgLog.isSelected());
            BotRuntime.debugLogging = cbDbgLog.isSelected();
        });
        BotRuntime.debugLogging = cbDbgLog.isSelected();

        JCheckBox cbDbgOv = new JCheckBox("Debug Overlay", config.devDebugOverlay());
        styleCheck(cbDbgOv);
        cbDbgOv.setToolTipText("Extra debug (collision/path altijd zichtbaar tijdens lopen)");
        cbDbgOv.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devDebugOverlay", cbDbgOv.isSelected()));

        JCheckBox cbWidDbg = new JCheckBox("Widget Debugging", config.devWidgetDebugging());
        styleCheck(cbWidDbg);
        cbWidDbg.addActionListener(e -> {
            boolean on = cbWidDbg.isSelected();
            configManager.setConfiguration("lonebot", "devWidgetDebugging", on);
            configManager.setConfiguration("lonebot", "devWidgetHoverTooltip", on);
            configManager.setConfiguration("lonebot", "devWidgetHoverHighlight", on);
        });

        JCheckBox cbReach = new JCheckBox("Debug Reachable Tiles", config.devReachableTiles());
        styleCheck(cbReach);
        cbReach.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devReachableTiles", cbReach.isSelected()));

        JCheckBox cbGo = new JCheckBox("Highlight Game Objects", config.devHighlightGameObjects());
        styleCheck(cbGo);
        cbGo.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devHighlightGameObjects", cbGo.isSelected()));

        JCheckBox cbWall = new JCheckBox("Highlight Wall Objects", config.devHighlightWallObjects());
        styleCheck(cbWall);
        cbWall.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devHighlightWallObjects", cbWall.isSelected()));

        JCheckBox cbGround = new JCheckBox("Highlight Ground Objects", config.devHighlightGroundObjects());
        styleCheck(cbGround);
        cbGround.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devHighlightGroundObjects", cbGround.isSelected()));

        JCheckBox cbDeco = new JCheckBox("Highlight Decorative Objects", config.devHighlightDecorativeObjects());
        styleCheck(cbDeco);
        cbDeco.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devHighlightDecorativeObjects", cbDeco.isSelected()));

        JCheckBox cbNpcHi = new JCheckBox("Highlight NPCs", config.devHighlightNpcs());
        styleCheck(cbNpcHi);
        cbNpcHi.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devHighlightNpcs", cbNpcHi.isSelected()));

        JCheckBox cbNpcGfx = new JCheckBox("Ext. NPC Graphics", config.devExtNpcGraphics());
        styleCheck(cbNpcGfx);
        cbNpcGfx.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devExtNpcGraphics", cbNpcGfx.isSelected()));

        JCheckBox cbCol = new JCheckBox("Debug Collision Data", config.devCollisionData());
        styleCheck(cbCol);
        cbCol.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devCollisionData", cbCol.isSelected()));

        JCheckBox cbProj = new JCheckBox("Debug Projectiles", config.devProjectiles());
        styleCheck(cbProj);
        cbProj.addActionListener(e ->
                configManager.setConfiguration("lonebot", "devProjectiles", cbProj.isSelected()));

        JCheckBox cbCapOn = new JCheckBox("Capture-log aan", config.captureLogEnabled());
        styleCheck(cbCapOn);
        cbCapOn.addActionListener(e ->
                configManager.setConfiguration("lonebot", "captureLogEnabled", cbCapOn.isSelected()));

        JCheckBox cbCapDup = new JCheckBox("Capture skip duplicaten", config.captureSkipDuplicates());
        styleCheck(cbCapDup);
        cbCapDup.addActionListener(e ->
                configManager.setConfiguration("lonebot", "captureSkipDuplicates", cbCapDup.isSelected()));

        JCheckBox cbCapCoords = new JCheckBox("Coördinaten bij capture", config.captureCoordsEnabled());
        styleCheck(cbCapCoords);
        cbCapCoords.setToolTipText("Middenklik (DevTools) + menu: [coords] in deze log");
        cbCapCoords.addActionListener(e ->
                configManager.setConfiguration("lonebot", "captureCoordsEnabled", cbCapCoords.isSelected()));

        JCheckBox cbWallsOv = new JCheckBox("Toon muren & deuren overlay", config.wallsOverlayEnabled());
        styleCheck(cbWallsOv);
        cbWallsOv.addActionListener(e ->
                configManager.setConfiguration("lonebot", "wallsOverlayEnabled", cbWallsOv.isSelected()));

        JLabel radLbl = new JLabel("Get walls scan-radius: " + config.wallsScanRadius());
        styleLabel(radLbl);
        JSlider radSlider = new JSlider(4, 24, Math.min(24, Math.max(4, config.wallsScanRadius())));
        radSlider.setOpaque(false);
        radSlider.setMajorTickSpacing(4);
        radSlider.setPaintTicks(true);
        radSlider.setToolTipText("Tegel-radius van Get walls (4–24). Loslaten slaat op; daarna opnieuw Get walls.");
        radSlider.addChangeListener(e -> {
            int r = radSlider.getValue();
            radLbl.setText("Get walls scan-radius: " + r);
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            if (!radSlider.getValueIsAdjusting()) {
                configManager.setConfiguration("lonebot", "wallsScanRadius", r);
                setStatus("Get walls radius → " + r);
            }
        });
        LoneBotConfigUiSync.slider(radSlider, null, "wallsScanRadius");

        JButton btnCopy = new JButton("Kopieer alles");
        btnCopy.addActionListener(e -> {
            String text = HoverCaptureLog.formatForCopyAll();
            if (text.isEmpty()) {
                setStatus("Capture-log leeg");
                return;
            }
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                setStatus("✓ Capture gekopieerd (" + HoverCaptureLog.size() + ")");
            } catch (Throwable t) {
                setStatus("⚠ Clipboard: " + t.getMessage());
            }
        });
        JButton btnClear = new JButton("Wis alles");
        btnClear.addActionListener(e -> {
            HoverCaptureLog.clear();
            net.storm.sdk.walls.WallDoorCaptureState.clear();
            refreshCaptureLogView();
            setStatus("Capture gewist");
        });
        JButton btnPlayer = new JButton("Capture speler");
        btnPlayer.setToolTipText("Speler-state (anim/dest/interacting) + coords + collision");
        btnPlayer.addActionListener(e -> clientThread.invoke(() -> {
            var me = client.getLocalPlayer();
            if (me == null || me.getWorldLocation() == null) {
                SwingUtilities.invokeLater(() -> setStatus("Niet ingelogd"));
                return;
            }
            net.runelite.client.plugins.lonebot.dev.DevInspectCapture.addPlayer(client, config);
            net.runelite.client.plugins.lonebot.dev.DevInspectCapture.addCollision(
                    client, config, me.getWorldLocation());
            if (config.captureCoordsEnabled()) {
                WallDoorCaptureReport.Result r = WallDoorCaptureReport.formatCoords(me.getWorldLocation(), true);
                if (r != null && !(config.captureSkipDuplicates()
                        && HoverCaptureLog.wouldSkipDuplicate(r.kind, r.line, r.detail))) {
                    HoverCaptureLog.add(r.kind, r.line, r.detail);
                }
            }
            SwingUtilities.invokeLater(() -> {
                refreshCaptureLogView();
                setStatus("✓ Speler-snapshot");
            });
        }));
        JButton btnDumpMenu = new JButton("Dump menu");
        btnDumpMenu.setToolTipText("Huidige menu-entries (opcodes). Rechtsklik in-game óf hover, dan deze knop — of menu-entry Dump menu.");
        btnDumpMenu.addActionListener(e -> clientThread.invoke(() -> {
            net.runelite.client.plugins.lonebot.dev.DevInspectCapture.addMenu(client, config);
            SwingUtilities.invokeLater(() -> {
                refreshCaptureLogView();
                setStatus("✓ Menu-dump");
            });
        }));
        JButton btnIfaces = new JButton("Dump open interfaces");
        btnIfaces.setToolTipText("Toplevel + zichtbare widget-roots + dialog/bank");
        btnIfaces.addActionListener(e -> clientThread.invoke(() -> {
            net.runelite.client.plugins.lonebot.dev.DevInspectCapture.addOpenInterfaces(client, config);
            SwingUtilities.invokeLater(() -> {
                refreshCaptureLogView();
                setStatus("✓ Open interfaces");
            });
        }));

        capturePreviewLabel = new JLabel("(laatste Get walls preview…)");
        capturePreviewLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        capturePreviewLabel.setForeground(new Color(180, 220, 255));
        capturePreviewLabel.setOpaque(true);
        capturePreviewLabel.setBackground(new Color(30, 32, 42));
        capturePreviewLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        captureLogArea.setEditable(false);
        captureLogArea.setLineWrap(true);
        captureLogArea.setWrapStyleWord(true);
        captureLogArea.setBackground(new Color(22, 24, 32));
        captureLogArea.setForeground(new Color(220, 230, 245));
        captureLogArea.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
        captureLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(hint);
        north.add(Box.createVerticalStrut(4));
        north.add(cbRlDevMode);
        north.add(Box.createVerticalStrut(4));
        north.add(createStopWorldWalkerButton());
        north.add(Box.createVerticalStrut(4));
        north.add(buildDevWidgetInspectorSection());
        north.add(Box.createVerticalStrut(6));
        north.add(cbDbgLog);
        north.add(cbDbgOv);
        north.add(cbWidDbg);
        north.add(cbReach);
        north.add(cbGo);
        north.add(cbWall);
        north.add(cbGround);
        north.add(cbDeco);
        north.add(cbNpcHi);
        north.add(cbNpcGfx);
        north.add(cbCol);
        north.add(cbProj);
        north.add(Box.createVerticalStrut(6));
        north.add(cbCapOn);
        north.add(cbCapDup);
        north.add(cbCapCoords);
        north.add(cbWallsOv);
        north.add(radLbl);
        north.add(radSlider);
        JPanel btns = new JPanel();
        btns.setLayout(new BoxLayout(btns, BoxLayout.Y_AXIS));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        btns.add(btnCopy);
        btns.add(Box.createVerticalStrut(2));
        btns.add(btnClear);
        btns.add(Box.createVerticalStrut(2));
        btns.add(btnPlayer);
        btns.add(Box.createVerticalStrut(2));
        btns.add(btnDumpMenu);
        btns.add(Box.createVerticalStrut(2));
        btns.add(btnIfaces);
        north.add(Box.createVerticalStrut(4));
        north.add(btns);
        north.add(Box.createVerticalStrut(4));
        north.add(capturePreviewLabel);

        JScrollPane northScroll = new JScrollPane(north);
        northScroll.setBorder(null);
        northScroll.setOpaque(false);
        northScroll.getViewport().setOpaque(false);
        northScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        northScroll.getVerticalScrollBar().setUnitIncrement(16);
        northScroll.setPreferredSize(new Dimension(10, 360));

        panel.add(northScroll, BorderLayout.NORTH);
        panel.add(new JScrollPane(captureLogArea), BorderLayout.CENTER);

        HoverCaptureLog.addListener(() -> SwingUtilities.invokeLater(this::refreshCaptureLogView));
        refreshCaptureLogView();
        return panel;
    }

    private void refreshCaptureLogView() {
        if (captureLogArea != null) {
            captureLogArea.setText(HoverCaptureLog.formatForDisplay());
            captureLogArea.setCaretPosition(0);
        }
        if (capturePreviewLabel != null) {
            List<HoverCaptureLog.Entry> snap = HoverCaptureLog.snapshot();
            if (snap.isEmpty()) {
                capturePreviewLabel.setText("(nog geen capture)");
            } else {
                HoverCaptureLog.Entry last = snap.get(snap.size() - 1);
                capturePreviewLabel.setText(last.line);
            }
        }
    }

    private static JPanel buildTabColumn(Consumer<JPanel> fill) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(true);
        col.setBackground(LoneBotUiTheme.BG_DARK);
        LoneBotUiTheme.mark(col, LoneBotUiTheme.ROLE_COLUMN);
        col.setBorder(new EmptyBorder(8, 8, 8, 8));
        fill.accept(col);
        col.add(Box.createVerticalStrut(8));
        col.add(Box.createVerticalGlue());
        return col;
    }

    private static JScrollPane wrapTabScroll(JPanel col) {
        JScrollPane scroll = new JScrollPane(col);
        scroll.setBorder(null);
        scroll.setOpaque(true);
        scroll.setBackground(LoneBotUiTheme.BG_DARK);
        LoneBotUiTheme.mark(scroll, LoneBotUiTheme.ROLE_SCROLL);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(LoneBotUiTheme.BG_DARK);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Client-sidebar 📐: venster openen of naar voren; tabs blijven hetzelfde. */
    private void showControlWindow() {
        if (detachedFrame != null && detachedFrame.isVisible()) {
            detachedFrame.toFront();
            return;
        }
        toggleDetachedWindow();
    }

    /**
     * Pop-out: zelfde content in apart resizable JFrame (Combat Bot-stijl).
     */
    private void toggleDetachedWindow() {
        if (detachedFrame != null) {
            dockBackFromDetached();
            return;
        }

        if (statusLabel.getParent() != null) {
            statusLabel.getParent().remove(statusLabel);
        }
        if (accountsTab != null) {
            accountsTab.setSidebarNarrow(false);
        }
        revalidate();
        repaint();

        detachedFrame = new JFrame(LoneBotBootstrapPlugin.controlPanelWindowTitle());
        detachedFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        detachedFrame.setSize(560, 820);
        detachedFrame.setMinimumSize(new Dimension(420, 520));
        detachedFrame.getContentPane().setBackground(LoneBotUiTheme.BG_DARK);
        detachedFrame.setLayout(new BorderLayout(0, 4));
        try {
            detachedFrame.setAlwaysOnTop(false);
        } catch (Throwable ignored) {
        }

        JPanel frameBody = new JPanel(new BorderLayout(0, 4));
        frameBody.setBorder(new EmptyBorder(8, 8, 8, 8));
        frameBody.setBackground(LoneBotUiTheme.BG_DARK);
        if (controlWindowHeader.getParent() != null) {
            controlWindowHeader.getParent().remove(controlWindowHeader);
        }
        frameBody.add(controlWindowHeader, BorderLayout.NORTH);
        frameBody.add(mainTabs, BorderLayout.CENTER);
        frameBody.add(statusLabel, BorderLayout.SOUTH);
        detachedFrame.add(frameBody, BorderLayout.CENTER);
        detachedFrame.setLocationRelativeTo(null);
        detachedFrame.setVisible(true);
        detachedFrame.toFront();

        detachedFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (detachedFrame != null) {
                    dockBackFromDetached();
                }
            }
        });
        refreshBoundAccountTitle();
        setStatus("Control Panel open — gebonden aan dit account/client");
    }

    private void refreshBoundAccountTitle() {
        String header = LoneBotBootstrapPlugin.controlPanelHeaderText();
        String window = LoneBotBootstrapPlugin.controlPanelWindowTitle();
        if (headerTitleLabel != null && !header.equals(headerTitleLabel.getText())) {
            headerTitleLabel.setText(header);
        }
        if (detachedFrame != null && detachedFrame.isDisplayable()
                && !window.equals(detachedFrame.getTitle())) {
            detachedFrame.setTitle(window);
        }
        refreshScriptVersionHeader();
    }

    private void dockBackFromDetached() {
        JFrame f = detachedFrame;
        detachedFrame = null;

        if (mainTabs.getParent() != null) {
            mainTabs.getParent().remove(mainTabs);
        }
        if (statusLabel.getParent() != null) {
            statusLabel.getParent().remove(statusLabel);
        }

        if (f != null) {
            try {
                f.dispose();
            } catch (Throwable ignored) {
            }
        }

        if (f != null) {
            try {
                f.dispose();
            } catch (Throwable ignored) {
            }
        }

        revalidate();
        repaint();
    }

    private static void addSectionComp(JPanel section, Component c) {
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
        } else if (c instanceof Box.Filler) {
            // strut / glue — leave as-is
        }
        section.add(c);
    }

    private JPanel createCollapsibleSection(String title, boolean accent, boolean expandedInitially,
                                            Consumer<JPanel> fillContent) {
        String cleanTitle = LoneBotUiTheme.cleanSectionTitle(title);
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setOpaque(true);
        outer.setBackground(LoneBotUiTheme.BG_DARK);
        LoneBotUiTheme.mark(outer, LoneBotUiTheme.ROLE_SECTION_OUTER);
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.setBorder(BorderFactory.createLineBorder(LoneBotUiTheme.BORDER, 1, true));
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JButton toggle = new JButton((expandedInitially ? "▼ " : "▶ ") + cleanTitle);
        styleCollapsibleHeader(toggle, accent);
        LoneBotUiTheme.mark(toggle, accent ? LoneBotUiTheme.ROLE_HEADER_ACCENT : LoneBotUiTheme.ROLE_HEADER);
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(LoneBotUiTheme.BG_SECTION);
        LoneBotUiTheme.mark(content, LoneBotUiTheme.ROLE_SECTION_CONTENT);
        content.setOpaque(true);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setVisible(expandedInitially);

        fillContent.accept(content);

        toggle.addActionListener(e -> {
            boolean open = !content.isVisible();
            content.setVisible(open);
            toggle.setText((open ? "▼ " : "▶ ") + cleanTitle);
            outer.revalidate();
            outer.repaint();
            Container up = outer.getParent();
            while (up != null) {
                up.revalidate();
                up.repaint();
                up = up.getParent();
            }
        });

        outer.add(toggle);
        outer.add(content);
        return outer;
    }

    private void styleCollapsibleHeader(JButton btn, boolean accent) {
        btn.setFont(LoneBotUiTheme.FONT_SECTION);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 10, 8, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        LoneBotUiTheme.wireHeaderButton(btn, accent);
    }

    private void applyUiTheme(LoneBotUiTheme.Preset preset) {
        LoneBotUiTheme.apply(preset);
        setBackground(LoneBotUiTheme.BG_DARK);
        if (mainTabs != null) {
            LoneBotUiTheme.polishTabs(mainTabs);
            mainTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            refreshTabColors(mainTabs);
            for (int i = 0; i < mainTabs.getTabCount(); i++) {
                Component tab = mainTabs.getComponentAt(i);
                if (tab instanceof JTabbedPane) {
                    LoneBotUiTheme.polishTabs((JTabbedPane) tab);
                    ((JTabbedPane) tab).setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
                    refreshTabColors((JTabbedPane) tab);
                    LoneBotUiTheme.rethemeTree(tab);
                } else if (tab instanceof LoneBotAccountsTab) {
                    ((LoneBotAccountsTab) tab).applyTheme();
                    LoneBotUiTheme.rethemeTree(tab);
                } else {
                    LoneBotUiTheme.rethemeTree(tab);
                }
            }
        }
        if (dzSidebar != null && dzSidebar.getParent() == this) {
            remove(dzSidebar);
        }
        LoneBotUiTheme.rethemeTree(this);
        if (skillTabs != null) {
            LoneBotUiTheme.polishTabs(skillTabs);
            skillTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            refreshTabColors(skillTabs);
        }
        if (dzSidebar != null) {
            add(dzSidebar, BorderLayout.CENTER);
        }
        if (statusLabel != null) {
            statusLabel.setForeground(LoneBotUiTheme.TEXT_DIM);
        }
        if (consoleArea != null) {
            consoleArea.setBackground(LoneBotUiTheme.INPUT_BG);
            consoleArea.setForeground(LoneBotUiTheme.INPUT_FG);
            consoleArea.setCaretColor(LoneBotUiTheme.INPUT_FG);
        }
        revalidate();
        repaint();
    }

    private void persistAccountSkills(boolean imp, boolean cow, boolean wc, boolean fish) {
        String acc = LoneBotBotControl.launchedAccountName();
        if (acc == null) {
            return;
        }
        ManagedAccountsStore.setSkillEnabled(acc, imp,
                cow && !imp && !wc && !fish,
                wc && !imp && !cow && !fish,
                fish && !imp && !cow && !wc);
    }

    /**
     * Panel-open / init: max één skill-checkbox. Stale config had soms WC+Fish beide true;
     * daarna {@code BotRuntime.fishingEnabled = cbFish.isSelected()} zette Fish opnieuw aan.
     */
    private void applyExclusiveSkillCheckboxes(JCheckBox cbImps, JCheckBox cbCow, JCheckBox cbMonk,
                                               JCheckBox cbGiants, JCheckBox cbWc, JCheckBox cbFish) {
        boolean imp = cbImps.isSelected();
        boolean cow = cbCow.isSelected();
        boolean monk = cbMonk.isSelected();
        boolean giants = cbGiants != null && cbGiants.isSelected();
        boolean wc = cbWc.isSelected();
        boolean fish = cbFish.isSelected();
        BotRuntime.ActiveSkill skill = BotRuntime.ActiveSkill.NONE;
        if (imp) {
            skill = BotRuntime.ActiveSkill.IMP;
        } else if (wc) {
            skill = BotRuntime.ActiveSkill.WOODCUTTING;
        } else if (fish) {
            skill = BotRuntime.ActiveSkill.FISHING;
        } else if (monk) {
            skill = BotRuntime.ActiveSkill.MONK;
        } else if (giants) {
            skill = BotRuntime.ActiveSkill.GIANTS;
        } else if (cow) {
            skill = BotRuntime.ActiveSkill.COW;
        } else if (BotRuntime.activeSkill == BotRuntime.ActiveSkill.STAR
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.IMP2
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.MONK
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.GIANTS
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.CLUE
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.QUEST) {
            skill = BotRuntime.activeSkill;
        }
        cbImps.setSelected(skill == BotRuntime.ActiveSkill.IMP);
        cbCow.setSelected(skill == BotRuntime.ActiveSkill.COW);
        cbMonk.setSelected(skill == BotRuntime.ActiveSkill.MONK);
        if (cbGiants != null) {
            cbGiants.setSelected(skill == BotRuntime.ActiveSkill.GIANTS);
        }
        cbWc.setSelected(skill == BotRuntime.ActiveSkill.WOODCUTTING);
        cbFish.setSelected(skill == BotRuntime.ActiveSkill.FISHING);
        BotRuntime.setActiveSkill(skill);
        LoneBotBotControl.syncSkillFlagsToConfig(configManager);
        if (skill == BotRuntime.ActiveSkill.WOODCUTTING) {
            LoneBotBootstrapPlugin.applyWcSettings(config);
        } else if (skill == BotRuntime.ActiveSkill.FISHING) {
            LoneBotBootstrapPlugin.applyFishSettings(config);
        } else if (skill == BotRuntime.ActiveSkill.IMP) {
            LoneBotBootstrapPlugin.applyImpSettings(config);
        } else if (skill == BotRuntime.ActiveSkill.STAR) {
            LoneBotBootstrapPlugin.applyStarSettings(config);
        } else if (skill == BotRuntime.ActiveSkill.GIANTS) {
            LoneBotBootstrapPlugin.applyGiantsSettings(config);
        } else if (skill == BotRuntime.ActiveSkill.QUEST) {
            LoneBotBootstrapPlugin.applyQuestSettings(config);
        }
    }

    private void applyActivityWebhook(String raw) {
        String url = raw != null ? raw.trim() : "";
        try {
            configManager.setConfiguration("lonebot", "activityDiscordWebhook", url);
        } catch (Throwable ignored) {
        }
        net.storm.sdk.bot.ActivityLog.discordWebhook = url;
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg != null ? msg : "");
        appendConsole(msg);
        log.info("LoneBot: {}", msg);
    }

    private void appendConsole(String msg) {
        if (msg == null || msg.isEmpty()) {
            return;
        }
        String line = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + msg;
        SwingUtilities.invokeLater(() -> {
            if (consoleArea.getDocument().getLength() > 80_000) {
                consoleArea.setText("");
            }
            consoleArea.append(line + "\n");
            if (consoleAutoScroll) {
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }
        });
    }

    private void runMouseTest(String pending, java.util.function.BooleanSupplier action) {
        setStatus(pending);
        Thread t = new Thread(() -> {
            boolean ok = false;
            try {
                ok = action.getAsBoolean();
            } catch (Throwable ex) {
                log.warn("mouse test: {}", ex.toString());
            }
            boolean finalOk = ok;
            SwingUtilities.invokeLater(() -> setStatus(
                    (finalOk ? "✓ " : "⚠ ") + Mouse.getLastAction() + " | " + Mouse.getLastProof()));
        }, "lonebot-mouse-test");
        t.setDaemon(true);
        t.start();
    }

    private void runTargetTest(NpcTargetTests.Mode mode) {
        setStatus("Target test " + mode.name() + "…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = NpcTargetTests.run(mode);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("target test: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-target-test");
        t.setDaemon(true);
        t.start();
    }

    private void runAutocastTest(net.storm.sdk.magic.AutocastTestHelper.Method method,
                                 net.storm.api.magic.SpellBook.Standard spell) {
        if (method == null) {
            setStatus("⚠ kies autocast-methode");
            return;
        }
        setStatus("Autocast test: " + method + "…");
        appendConsole("Autocast test starten: " + method
                + (spell != null ? " → " + spell.getName() : ""));
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = net.storm.sdk.magic.AutocastTestHelper.run(method, spell);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("autocast test: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> {
                String shortMsg = finalMsg != null && finalMsg.length() > 90
                        ? finalMsg.substring(0, 87) + "…"
                        : finalMsg;
                setStatus(shortMsg);
            });
        }, "lonebot-autocast-test");
        t.setDaemon(true);
        t.start();
    }

    private void runTeleportTest(net.storm.sdk.magic.TeleportTestHelper.Method method,
                                 net.storm.sdk.magic.TeleportTestHelper.SpellChoice spell) {
        if (method == null) {
            setStatus("⚠ kies teleport-methode");
            return;
        }
        setStatus("Tele test: " + method + "…");
        appendConsole("[TeleTest] starten: " + method
                + (spell != null ? " → " + spell : ""));
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = net.storm.sdk.magic.TeleportTestHelper.run(method, spell);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("teleport test: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> {
                appendConsole("[TeleTest] " + finalMsg);
                String shortMsg = finalMsg != null && finalMsg.length() > 90
                        ? finalMsg.substring(0, 87) + "…"
                        : finalMsg;
                setStatus(shortMsg);
            });
        }, "lonebot-teleport-test");
        t.setDaemon(true);
        t.start();
    }

    private void runFmLightTest(int methodIndex) {
        setStatus("FM light test #" + (methodIndex + 1) + "…");
        appendConsole("[FmLightTest] start methode #" + (methodIndex + 1));
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = com.lonebot.example.woodcutter.FmLightHelper.runTest(methodIndex);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("fm light test: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> {
                appendConsole("[FmLightTest] " + finalMsg);
                String shortMsg = finalMsg != null && finalMsg.length() > 90
                        ? finalMsg.substring(0, 87) + "…"
                        : finalMsg;
                setStatus(shortMsg);
            });
        }, "lonebot-fm-light-test");
        t.setDaemon(true);
        t.start();
    }

    private void runFmLightTestAll() {
        setStatus("FM light: alle 10 methodes…");
        appendConsole("[FmLightTest] sequentieel #1–#10");
        Thread t = new Thread(() -> {
            String last = "";
            for (int i = 0; i < com.lonebot.example.woodcutter.FmLightHelper.METHOD_COUNT; i++) {
                try {
                    last = com.lonebot.example.woodcutter.FmLightHelper.runTest(i);
                    final String line = "#" + (i + 1) + " " + last;
                    SwingUtilities.invokeLater(() -> appendConsole("[FmLightTest] " + line));
                    if (last != null && last.startsWith("✓")) {
                        break;
                    }
                    Thread.sleep(600L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    last = "interrupted";
                    break;
                } catch (Throwable ex) {
                    last = "⚠ #" + (i + 1) + " " + ex;
                    log.warn("fm light all: {}", ex.toString());
                }
            }
            String finalMsg = last;
            SwingUtilities.invokeLater(() -> {
                appendConsole("[FmLightTest] klaar: " + finalMsg);
                setStatus(finalMsg != null && finalMsg.length() > 90
                        ? finalMsg.substring(0, 87) + "…"
                        : finalMsg);
            });
        }, "lonebot-fm-light-all");
        t.setDaemon(true);
        t.start();
    }

    private void runFmDiagnose() {
        setStatus("FM diagnose…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = com.lonebot.example.woodcutter.FmLightHelper.diagnose();
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> {
                appendConsole("[FmDiagnose] " + finalMsg);
                setStatus(finalMsg != null && finalMsg.length() > 90
                        ? finalMsg.substring(0, 87) + "…"
                        : finalMsg);
            });
        }, "lonebot-fm-diagnose");
        t.setDaemon(true);
        t.start();
    }

    private void runAnimDump() {
        setStatus("Anim dump…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                int id = net.storm.sdk.game.Animations.ofLocal();
                String named = net.storm.sdk.game.AnimationIds.nameOf(id);
                msg = "local=" + net.storm.sdk.game.Animations.describe(id)
                        + " | nameOf=" + (named != null ? named : "—")
                        + " | known=" + net.storm.sdk.game.AnimationIds.size()
                        + " | wc=" + net.storm.sdk.game.AnimationIds.isWoodcutting(id)
                        + " | fm=" + net.storm.sdk.game.AnimationIds.isFiremakingRelated(id);
                BotRuntime.logConsole("[AnimDump] " + msg);
                BotRuntime.logConsole("[AnimDump] docs: animation-ids-runelite.json / .txt");
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> {
                appendConsole("[AnimDump] " + finalMsg);
                setStatus(finalMsg != null && finalMsg.length() > 90
                        ? finalMsg.substring(0, 87) + "…"
                        : finalMsg);
            });
        }, "lonebot-anim-dump");
        t.setDaemon(true);
        t.start();
    }

    private void prepareLoginFromAccount(ManagedAccountsStore.ManagedAccount selected) {
        if (selected == null) {
            setStatus("⚠ Geen account");
            return;
        }
        JagexCredentialsHelper.ParsedJagexAccount parsed = JagexCredentialsHelper.fromManaged(selected);
        String label = selected.displayName;

        if (parsed == null || parsed.toGameAccount().getSessionId() == null
                || parsed.toGameAccount().getSessionId().isEmpty()) {
            setStatus("⚠ Geen session/token — bewerk account of Storm-export mist session_id?");
            return;
        }

        if (!JagexCredentialsHelper.writeJagexCredentials(parsed)) {
            setStatus("⚠ Schrijven credentials.properties mislukt");
            return;
        }

        GameAccount ga = parsed.toGameAccount();
        Game.setGameAccount(ga);

        setStatus("… " + label + " live toepassen…");
        clientThread.invoke(() -> {
            boolean ok = JagexRuntimeLogin.applyInMemory(client, parsed);
            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    setStatus("✓ " + label + " klaar — klik Play op login (geen herstart)");
                } else {
                    setStatus("⚠ " + label + " file gezet, live apply mislukt — zie log");
                }
            });
        });
    }

    private void refreshList() {
        // legacy no-op — Accounts-tab beheert eigen tabel
        setStatus(ManagedAccountsStore.getAccounts().size() + " account(s)");
    }

    private void fillPasteFromSelection() {
        // legacy — paste zit in LoneBotAccountsTab
    }

    private void importStormJson() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Kies Storm accounts JSON");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        File desktop = new File(System.getProperty("user.home"), "Desktop");
        if (desktop.isDirectory()) {
            chooser.setCurrentDirectory(desktop);
        }
        if (DEFAULT_STORM_JSON.isFile()) {
            chooser.setSelectedFile(DEFAULT_STORM_JSON);
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        importStormFile(chooser.getSelectedFile());
    }

    private void importStormFile(File file) {
        StormAccountsBulkImport.Result result = StormAccountsBulkImport.importFile(file);
        setStatus(result.summary());
        if (result.fatalError != null) {
            JOptionPane.showMessageDialog(this, result.fatalError, "Storm import", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, result.summary(), "Storm import", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void applyLogin() {
        // legacy — gebruik Accounts-tab
        setStatus("Gebruik Accounts-tab → selecteer rij → Login klaarzetten (of dubbelklik)");
    }

    private void writeSelectedCredentials(ManagedAccountsStore.ManagedAccount a) {
        JagexCredentialsHelper.ParsedJagexAccount p = JagexCredentialsHelper.fromManaged(a);
        if (p != null) {
            JagexCredentialsHelper.writeJagexCredentials(p);
        }
    }

    private void requestClientRestart() {
        String role = System.getProperty("lonebot.role", "client");
        boolean launcher = "launcher".equalsIgnoreCase(role);
        String what = launcher ? "launcher" : "game-client";
        int ok = JOptionPane.showConfirmDialog(this,
                "Herstart dit " + what + "-venster?\n\n"
                        + "• Geen Gradle / installDist\n"
                        + "• Andere vensters blijven open\n"
                        + "• Nieuwe jars: Rebuild-LoneBot.bat\n"
                        + "• Scripts: ↺ Alle scripts",
                "LoneBot herstart",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        setStatus("Herstart over ±1s…");
        appendConsole("↻ Herstart " + what + " (geen installDist)");
        try {
            com.lonebot.launcher.LauncherRestartHelper.restartFreshAfterDelay(600);
        } catch (Throwable t) {
            setStatus("Herstart mislukt: " + t.getMessage());
        }
    }

    private void reloadBotJar() {
        setStatus("Example-scripts bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageExampleJar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                return;
            }
            String msg = ScriptReloadHelper.reloadExampleInThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-example");
        t.setDaemon(true);
        t.start();
    }

    private void reloadImpJar() {
        setStatus("Imp-script bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageImpJar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                return;
            }
            String msg = ScriptReloadHelper.reloadImpInThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-imp");
        t.setDaemon(true);
        t.start();
    }

    private void reloadWcJar() {
        setStatus("Woodcutter bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageWoodcutterJar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                return;
            }
            String msg = ScriptReloadHelper.reloadWoodcutterInThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-wc");
        t.setDaemon(true);
        t.start();
    }

    private void reloadFishJar() {
        setStatus("Fishing bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageFishingJar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                return;
            }
            String msg = ScriptReloadHelper.reloadFishingInThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-fish");
        t.setDaemon(true);
        t.start();
    }

    private void reloadImps2Jar() {
        setStatus("Imps2-script bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageImps2Jar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                return;
            }
            String msg = ScriptReloadHelper.reloadImps2InThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-imps2");
        t.setDaemon(true);
        t.start();
    }

    private void reloadStarJar() {
        setStatus("Star Miner bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageStarMinerJar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                return;
            }
            String msg = ScriptReloadHelper.reloadStarMinerInThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-star");
        t.setDaemon(true);
        t.start();
    }

    private void reloadGiantsJar() {
        setStatus("Giants bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageGiantsJar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                return;
            }
            String msg = ScriptReloadHelper.reloadGiantsInThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-giants");
        t.setDaemon(true);
        t.start();
    }

    private void reloadClueJar() {
        setStatus("Clue-script bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageClueJar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                SwingUtilities.invokeLater(() -> setStatus("⚠ Clue jar niet gebouwd"));
                return;
            }
            String msg = ScriptReloadHelper.reloadClueInThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-clue");
        t.setDaemon(true);
        t.start();
    }

    private void reloadQuestJar() {
        setStatus("Quest-script bouwen + herladen…");
        Thread t = new Thread(() -> {
            File jar = ScriptReloadHelper.buildAndStageQuesterJar(msg ->
                    SwingUtilities.invokeLater(() -> setStatus(msg)));
            if (jar == null) {
                return;
            }
            String msg = ScriptReloadHelper.reloadQuesterInThisClient();
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-quest");
        t.setDaemon(true);
        t.start();
    }

    private void reloadAllScripts() {
        setStatus("Alle scripts bouwen + herladen…");
        Thread t = new Thread(() -> {
            String msg = ScriptReloadHelper.reloadAllInThisClient(s ->
                    SwingUtilities.invokeLater(() -> setStatus(s)));
            SwingUtilities.invokeLater(() -> setStatus(msg));
        }, "lonebot-reload-all");
        t.setDaemon(true);
        t.start();
    }

    private static JButton accentActionBtn(String text, Color bg) {
        JButton b = new JButton(text);
        try {
            b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        } catch (Throwable ignored) {
        }
        b.setFocusPainted(false);
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(
                        Math.min(255, bg.getRed() + 35),
                        Math.min(255, bg.getGreen() + 35),
                        Math.min(255, bg.getBlue() + 35)), 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 11f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton createStopWorldWalkerButton() {
        JButton b = accentActionBtn("⏹ Stop WorldWalker", new Color(150, 45, 45));
        b.setToolTipText("Stopt Pathfind-walk. In-game doet ESC hetzelfde.");
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.addActionListener(e -> {
            WorldWalker.cancel();
            setStatus("WorldWalker gestopt");
        });
        return b;
    }

    /** Scripts-kaart (Imp/WC/Example reload + client-herstart). Elke call = nieuwe knoppen (multi-parent). */
    private JPanel createScriptsToolbar() {
        JButton reloadAll = accentActionBtn("↺ Alle scripts", new Color(120, 85, 40));
        reloadAll.setToolTipText("Bouw + hot-reload Imp, Woodcutter, Fishing én example in deze client");
        reloadAll.addActionListener(e -> reloadAllScripts());

        JButton restart = accentActionBtn("↻ Herstart", new Color(55, 90, 140));
        restart.setToolTipText("Herstart dit venster (geen installDist). Nieuwe jars: Rebuild-LoneBot.bat.");
        restart.addActionListener(e -> requestClientRestart());

        JButton reloadImp = accentActionBtn("↺ Imp", new Color(140, 95, 45));
        reloadImp.setToolTipText(":script-imp-killer:jar → hot-reload. Geen client-herstart.");
        reloadImp.addActionListener(e -> reloadImpJar());

        JButton reloadWc = accentActionBtn("↺ WC", new Color(45, 120, 75));
        reloadWc.setToolTipText(":script-woodcutter:jar → hot-reload. Geen client-herstart.");
        reloadWc.addActionListener(e -> reloadWcJar());

        JButton reloadFish = accentActionBtn("↺ Fish", new Color(40, 110, 180));
        reloadFish.setToolTipText(":script-fishing:jar → hot-reload. Geen client-herstart.");
        reloadFish.addActionListener(e -> reloadFishJar());

        JButton reloadImps2 = accentActionBtn("↺ Imps2", new Color(160, 90, 50));
        reloadImps2.setToolTipText(":script-imps2:jar → hot-reload. Geen client-herstart.");
        reloadImps2.addActionListener(e -> reloadImps2Jar());

        JButton reloadStar = accentActionBtn("↺ Star", new Color(120, 70, 160));
        reloadStar.setToolTipText(":script-star-miner:jar → hot-reload. Geen client-herstart.");
        reloadStar.addActionListener(e -> reloadStarJar());

        JButton reloadGiants = accentActionBtn("↺ Giants", new Color(90, 130, 55));
        reloadGiants.setToolTipText(":script-giants:jar → hot-reload. Geen client-herstart.");
        reloadGiants.addActionListener(e -> reloadGiantsJar());

        JButton reloadClue = accentActionBtn("↺ Clue", new Color(150, 120, 50));
        reloadClue.setToolTipText(":script-clue:jar → hot-reload. Geen client-herstart.");
        reloadClue.addActionListener(e -> reloadClueJar());

        JButton reloadEx = accentActionBtn("↺ Example", new Color(90, 95, 120));
        reloadEx.setToolTipText("Cow/tests/RandomEvent — :example-plugin:jar hot-reload");
        reloadEx.addActionListener(e -> reloadBotJar());

        JPanel bar = new JPanel(new BorderLayout(0, 6));
        bar.setOpaque(true);
        bar.setBackground(LoneBotUiTheme.BG_SECTION);
        LoneBotUiTheme.mark(bar, LoneBotUiTheme.ROLE_SECTION_CONTENT);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LoneBotUiTheme.BORDER, 1, true),
                new EmptyBorder(10, 10, 10, 10)));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        JLabel title = new JLabel("Scripts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(LoneBotUiTheme.GOLD);

        JLabel hint = new JLabel("<html><span style='color:#9aa3b5'>↺ scripts na code-wijziging · "
                + "↻ herstart voor SDK/client</span></html>");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(LoneBotUiTheme.TEXT_DIM);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(hint);

        JPanel row1 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        row1.setOpaque(false);
        row1.add(reloadAll);
        row1.add(restart);

        JPanel row2 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        row2.setOpaque(false);
        row2.add(reloadImp);
        row2.add(reloadWc);
        row2.add(reloadFish);
        row2.add(reloadImps2);
        row2.add(reloadStar);
        row2.add(reloadGiants);
        row2.add(reloadClue);
        JButton reloadQuest = accentActionBtn("↺ Quest", new Color(90, 70, 130));
        reloadQuest.setToolTipText(":script-quest:jar → hot-reload. Geen client-herstart.");
        reloadQuest.addActionListener(e -> reloadQuestJar());
        row2.add(reloadQuest);
        row2.add(reloadEx);

        JPanel btns = new JPanel();
        btns.setLayout(new BoxLayout(btns, BoxLayout.Y_AXIS));
        btns.setOpaque(false);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        btns.add(row1);
        btns.add(row2);

        bar.add(titles, BorderLayout.NORTH);
        bar.add(btns, BorderLayout.CENTER);
        return bar;
    }

    private LoneBotWcImpSettingsUi.Host wcImpHost() {
        return new LoneBotWcImpSettingsUi.Host() {
            @Override
            public void addComp(JPanel sec, JComponent c) {
                addSectionComp(sec, c);
            }

            @Override
            public void styleCheck(JCheckBox cb) {
                LoneBotPanel.this.styleCheck(cb);
            }

            @Override
            public void styleLabel(JLabel l) {
                LoneBotPanel.this.styleLabel(l);
            }

            @Override
            public void setStatus(String msg) {
                LoneBotPanel.this.setStatus(msg);
            }

            @Override
            public LoneBotConfig config() {
                return config;
            }

            @Override
            public ConfigManager cm() {
                return configManager;
            }
        };
    }

    private void refreshScriptVersionHeader() {
        if (scriptVersionLabel == null) {
            return;
        }
        String line = scriptVersionsLine();
        if (!line.equals(scriptVersionLabel.getText())) {
            scriptVersionLabel.setText(line);
        }
    }

    private void refreshSkillCheckLabels(JCheckBox cbImps, JCheckBox cbWc, JCheckBox cbFish, JCheckBox cbGiants) {
        setCheckText(cbImps, skillCheckLabel("Imp Killer (Karamja)",
                livePluginVersion(BotRuntime.impPluginVersion, "com.lonebot.example.ImpKillerPlugin")));
        setCheckText(cbWc, skillCheckLabel("Woodcutting",
                livePluginVersion(BotRuntime.wcPluginVersion, "com.lonebot.example.woodcutter.WoodcutterPlugin")));
        setCheckText(cbFish, skillCheckLabel("Fishing",
                livePluginVersion(BotRuntime.fishPluginVersion, "com.lonebot.example.fishing.FishingPlugin")));
        setCheckText(cbGiants, skillCheckLabel("Giants (Hill Giants)",
                livePluginVersion(BotRuntime.giantsPluginVersion, "com.lonebot.example.GiantsPlugin")));
    }

    private static void setCheckText(JCheckBox box, String text) {
        if (box != null && text != null && !text.equals(box.getText())) {
            box.setText(text);
        }
    }

    private static String scriptVersionsLine() {
        return "Fish v" + livePluginVersion(BotRuntime.fishPluginVersion, "com.lonebot.example.fishing.FishingPlugin")
                + "  ·  WC v" + livePluginVersion(BotRuntime.wcPluginVersion, "com.lonebot.example.woodcutter.WoodcutterPlugin")
                + "  ·  Imp v" + livePluginVersion(BotRuntime.impPluginVersion, "com.lonebot.example.ImpKillerPlugin")
                + "  ·  Imps2 v" + livePluginVersion(BotRuntime.imps2PluginVersion, "com.lonebot.example.Imps2Plugin")
                + "  ·  Star v" + livePluginVersion(BotRuntime.starPluginVersion, "com.lonebot.example.StarMinerPlugin")
                + "  ·  Giants v" + livePluginVersion(BotRuntime.giantsPluginVersion, "com.lonebot.example.GiantsPlugin")
                + "  ·  Clue v" + livePluginVersion(BotRuntime.cluePluginVersion, "com.lonebot.example.CluePlugin")
                + "  ·  Quest v" + livePluginVersion(BotRuntime.questPluginVersion, "com.lonebot.example.QuesterPlugin");
    }

    private static String activeSkillWithVersion() {
        if (BotRuntime.woodcuttingEnabled) {
            return "Woodcutting v" + livePluginVersion(BotRuntime.wcPluginVersion,
                    "com.lonebot.example.woodcutter.WoodcutterPlugin");
        }
        if (BotRuntime.fishingEnabled) {
            return "Fishing v" + livePluginVersion(BotRuntime.fishPluginVersion,
                    "com.lonebot.example.fishing.FishingPlugin");
        }
        if (BotRuntime.starMinerEnabled) {
            return "Star Miner v" + livePluginVersion(BotRuntime.starPluginVersion,
                    "com.lonebot.example.StarMinerPlugin");
        }
        if (BotRuntime.giantsKillerEnabled) {
            return "Giants v" + livePluginVersion(BotRuntime.giantsPluginVersion,
                    "com.lonebot.example.GiantsPlugin");
        }
        if (BotRuntime.clueEnabled) {
            return "Beginner Clue v" + livePluginVersion(BotRuntime.cluePluginVersion,
                    "com.lonebot.example.CluePlugin");
        }
        if (BotRuntime.impKillerEnabled) {
            return "Imp Killer v" + livePluginVersion(BotRuntime.impPluginVersion,
                    "com.lonebot.example.ImpKillerPlugin");
        }
        if (BotRuntime.imps2Enabled) {
            return "Imps2 v" + livePluginVersion(BotRuntime.imps2PluginVersion,
                    "com.lonebot.example.Imps2Plugin");
        }
        if (BotRuntime.cowCombatEnabled) {
            return "Cow Combat";
        }
        if (BotRuntime.monkKillerEnabled) {
            return "Monk Killer v" + livePluginVersion(BotRuntime.monkPluginVersion,
                    "com.lonebot.example.MonkKillerPlugin");
        }
        if (BotRuntime.questEnabled) {
            return "Quest v" + livePluginVersion(BotRuntime.questPluginVersion,
                    "com.lonebot.example.QuesterPlugin");
        }
        if (BotRuntime.cityCircleTestEnabled) {
            return "F2P banken-cirkel";
        }
        if (BotRuntime.varrockEastBankTestEnabled) {
            return "Bank-test";
        }
        return "Idle";
    }

    private static String skillCheckLabel(String name, String version) {
        return name + "  v" + version;
    }

    private static String livePluginVersion(String runtime, String className) {
        if (runtime != null && !runtime.isBlank() && !"?".equals(runtime.trim())) {
            return runtime.trim();
        }
        try {
            Object v = Class.forName(className).getField("VERSION").get(null);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    /**
     * Locatie-combo voor Combat/Mining/Fishing centers — zelfde patroon als WC locatie.
     * @param onSelect optional LIVE apply (fishing); combat/mining scaffold = null
     */
    private JComboBox<String> newAreaLocationCombo(AreaCenters.Skill skill, String configKey,
                                                   String savedLoc,
                                                   java.util.concurrent.atomic.AtomicBoolean suppressWrite,
                                                   Runnable onSelect) {
        String blob = LoneBotBootstrapPlugin.liveAreaCentersBlob(skill);
        JComboBox<String> combo = new JComboBox<>(AreaCenters.presetNames(blob));
        LoneBotUiTheme.styleCombo(combo);
        LoneBotUiTheme.mark(combo, LoneBotUiTheme.ROLE_INPUT);
        suppressWrite.set(true);
        try {
            selectLocationKeeping(combo, savedLoc);
        } finally {
            suppressWrite.set(false);
        }
        combo.setToolTipText("AUTO = per skill-level / defaults; anders vaste spot uit " + skill.configKey);
        combo.addActionListener(e -> {
            if (suppressWrite.get() || LoneBotConfigUiSync.applying()) {
                return;
            }
            Object sel = combo.getSelectedItem();
            if (sel == null) {
                return;
            }
            String loc = String.valueOf(sel).trim();
            if (loc.isEmpty() || "null".equalsIgnoreCase(loc)) {
                return;
            }
            configManager.setConfiguration("lonebot", configKey, loc);
            if (onSelect != null) {
                onSelect.run();
            }
            setStatus(skill.label + " locatie → " + loc);
        });
        LoneBotConfigUiSync.combo(combo, configKey);
        return combo;
    }

    private static final class AreaCenterRenameUi {
        final JLabel editLabel;
        final JComboBox<String> comboEdit;
        final JTextField tfName;
        final JButton btnSave;
        final Runnable refresh;

        AreaCenterRenameUi(JLabel editLabel, JComboBox<String> comboEdit, JTextField tfName,
                           JButton btnSave, Runnable refresh) {
            this.editLabel = editLabel;
            this.comboEdit = comboEdit;
            this.tfName = tfName;
            this.btnSave = btnSave;
            this.refresh = refresh;
        }
    }

    /** Zelfde hernoem-UI als Woodcutting: kies center → typ naam → Opslaan naam. */
    private AreaCenterRenameUi wireAreaCenterRename(
            AreaCenters.Skill skill,
            String locKey,
            JComboBox<String> comboLoc,
            java.util.concurrent.atomic.AtomicBoolean suppressLocWrite,
            Runnable afterPersist) {
        JLabel editLabel = new JLabel("Bewerk center:");
        styleLabel(editLabel);
        JComboBox<String> comboEdit = new JComboBox<>();
        LoneBotUiTheme.styleCombo(comboEdit);
        LoneBotUiTheme.mark(comboEdit, LoneBotUiTheme.ROLE_INPUT);
        JTextField tfName = new JTextField(14);
        tfName.setFont(LoneBotUiTheme.FONT_LABEL);
        tfName.setBackground(LoneBotUiTheme.INPUT_BG);
        tfName.setForeground(LoneBotUiTheme.INPUT_FG);
        tfName.setCaretColor(LoneBotUiTheme.INPUT_FG);
        LoneBotUiTheme.mark(tfName, LoneBotUiTheme.ROLE_INPUT);
        tfName.setToolTipText("Nieuwe naam (geen : of |)");

        java.util.List<Integer> editIndexList = new java.util.ArrayList<>();
        final Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = () -> {
            String blob = LoneBotBootstrapPlugin.liveAreaCentersBlob(skill);
            String ensured = AreaCenters.ensureNames(blob, skill.label);
            if (!ensured.equals(blob)) {
                LoneBotBootstrapPlugin.persistAreaCenters(skill, ensured);
                blob = ensured;
            }
            refillAreaLocationCombo(comboLoc, skill, locKey, suppressLocWrite);
            editIndexList.clear();
            Object selEdit = comboEdit.getSelectedItem();
            comboEdit.removeAllItems();
            java.util.List<AreaCenters.Center> all = AreaCenters.parseAll(blob);
            for (int i = 0; i < all.size(); i++) {
                AreaCenters.Center c = all.get(i);
                if (c == null) {
                    continue;
                }
                editIndexList.add(i);
                comboEdit.addItem(AreaCenters.displayLabel(c));
            }
            if (comboEdit.getItemCount() > 0) {
                if (selEdit != null) {
                    comboEdit.setSelectedItem(selEdit);
                }
                if (comboEdit.getSelectedIndex() < 0) {
                    comboEdit.setSelectedIndex(0);
                }
                int idx = comboEdit.getSelectedIndex();
                if (idx >= 0 && idx < editIndexList.size()) {
                    int ci = editIndexList.get(idx);
                    if (ci >= 0 && ci < all.size() && all.get(ci) != null) {
                        tfName.setText(AreaCenters.displayName(all.get(ci)));
                    }
                }
            } else {
                tfName.setText("");
            }
        };
        Runnable refresh = () -> refreshHolder[0].run();

        comboEdit.addActionListener(e -> {
            int idx = comboEdit.getSelectedIndex();
            if (idx < 0 || idx >= editIndexList.size()) {
                return;
            }
            String blob = LoneBotBootstrapPlugin.liveAreaCentersBlob(skill);
            java.util.List<AreaCenters.Center> all = AreaCenters.parseAll(blob);
            int ci = editIndexList.get(idx);
            if (ci >= 0 && ci < all.size() && all.get(ci) != null) {
                tfName.setText(AreaCenters.displayName(all.get(ci)));
            }
        });

        JButton btnSave = new JButton("Opslaan naam");
        btnSave.setToolTipText("Sla de naam van het geselecteerde center op");
        btnSave.addActionListener(e -> {
            int idx = comboEdit.getSelectedIndex();
            if (idx < 0 || idx >= editIndexList.size()) {
                setStatus("Geen center geselecteerd");
                return;
            }
            int ci = editIndexList.get(idx);
            String newName = tfName.getText() != null ? tfName.getText().trim() : "";
            String blob = LoneBotBootstrapPlugin.liveAreaCentersBlob(skill);
            String next = AreaCenters.renameAt(blob, ci, newName);
            LoneBotBootstrapPlugin.persistAreaCenters(skill, next);
            java.util.List<AreaCenters.Center> renamed = AreaCenters.parseAll(next);
            if (ci < 0 || ci >= renamed.size() || renamed.get(ci) == null) {
                setStatus("Hernoemen mislukt");
                return;
            }
            String saved = AreaCenters.displayName(renamed.get(ci));
            configManager.setConfiguration("lonebot", locKey, saved);
            refresh.run();
            suppressLocWrite.set(true);
            try {
                comboLoc.setSelectedItem(saved);
            } finally {
                suppressLocWrite.set(false);
            }
            if (afterPersist != null) {
                afterPersist.run();
            }
            setStatus(skill.label + " center hernoemd → " + saved);
        });

        return new AreaCenterRenameUi(editLabel, comboEdit, tfName, btnSave, refresh);
    }

    private void refillAreaLocationCombo(JComboBox<String> combo, AreaCenters.Skill skill,
                                         String configKey,
                                         java.util.concurrent.atomic.AtomicBoolean suppressWrite) {
        if (combo == null || skill == null) {
            return;
        }
        String keep = configManager.getConfiguration("lonebot", configKey);
        if (keep == null || keep.isBlank() || "null".equalsIgnoreCase(keep)) {
            keep = "AUTO";
        }
        suppressWrite.set(true);
        try {
            combo.removeAllItems();
            for (String n : AreaCenters.presetNames(LoneBotBootstrapPlugin.liveAreaCentersBlob(skill))) {
                combo.addItem(n);
            }
            selectLocationKeeping(combo, keep);
        } finally {
            suppressWrite.set(false);
        }
    }

    private static void selectLocationKeeping(JComboBox<String> combo, String savedLoc) {
        if (combo == null) {
            return;
        }
        String keep = savedLoc;
        if (keep == null || keep.isBlank() || "null".equalsIgnoreCase(keep)) {
            keep = "AUTO";
        }
        combo.setSelectedItem(keep);
        if (combo.getSelectedIndex() < 0) {
            combo.addItem(keep);
            combo.setSelectedItem(keep);
        }
        if (combo.getSelectedIndex() < 0) {
            combo.setSelectedItem("AUTO");
        }
    }

    private JLabel hintLabel(String htmlOrText) {
        String t = htmlOrText == null ? "" : htmlOrText;
        JLabel l = new JLabel(t.startsWith("<html") ? t : "<html><i>" + t + "</i></html>");
        styleLabel(l);
        return l;
    }

    private JLabel comingSoon(String msg) {
        JLabel l = new JLabel("<html><i>" + (msg != null ? msg : "Volgt later.") + "</i></html>");
        styleLabel(l);
        l.setForeground(LoneBotUiTheme.TEXT_DIM);
        return l;
    }

    private JPanel rowLabel(String left, JLabel right) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JLabel l = new JLabel(left);
        styleLabel(l);
        row.add(l, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        return row;
    }

    private static String trimStat(String s) {
        if (s == null || s.isBlank()) {
            return "-";
        }
        return s.length() <= 48 ? s : s.substring(0, 47) + "…";
    }

    private void polishMainTabs(JTabbedPane tabs) {
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 11f));
        tabs.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        LoneBotUiTheme.polishTabs(tabs);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    }

    private void buildClientAccordion() {
        clientAccordion = new LoneBotAccordionNav();
        String initial = skillPageFor(BotRuntime.activeSkill);
        clientAccordion.addSection("Woodcut", "Woodcutting", true, "Woodcut".equals(initial));
        clientAccordion.addSection("Fishing", "Fishing", true, "Fishing".equals(initial));
        clientAccordion.addSection("Star", "Star Miner", true, "Star".equals(initial));
        clientAccordion.addSection("Imps", "Imps", true, "Imps".equals(initial));
        clientAccordion.addSection("Combat", "Combat", true, "Combat".equals(initial));
        clientAccordion.addSection("Settings", "Settings", false, false);
        clientAccordion.addSection("Accounts", "Accounts", false, false);
        clientAccordion.addSection("Centers", "Centers", false, false);
        clientAccordion.addSection("Stats", "Stats", false, false);
        clientAccordion.addSection("Developer Tools", "Developer Tools", false, false);
        clientAccordion.addSection("Debug", "Debug", false, false);
        clientAccordion.addSection("Test", "Test", false, false);
        clientAccordion.addSection("General", "General", false, false);
        clientAccordion.addSection("Mining", "Mining", false, false);
        clientAccordion.addSection("Giants", "Giants", false, false);
        clientAccordion.addSection("Quest", "Quest", false, false);
        clientAccordion.addSection("SOS", "SOS", false, false);
        clientAccordion.addSection("Barbarian", "Barbarian", false, false);
        clientAccordionScroll = wrapTabScroll(clientAccordion);
        clientAccordionScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    private void installClientAccordion() {
        if (clientAccordion == null) {
            return;
        }
        if (!clientAccordionActive) {
            clientAccordion.putTabContent("Woodcut", panelWoodcut);
            clientAccordion.putTabContent("Fishing", panelFishing);
            clientAccordion.putTabContent("Star", panelStar);
            clientAccordion.putTabContent("Imps", panelImps);
            clientAccordion.putTabContent("Combat", panelCombat);
            clientAccordion.putTabContent("Settings", panelSettings);
            clientAccordion.putTabContent("Accounts", accountsTab);
            clientAccordion.putTabContent("Centers", panelCenters);
            clientAccordion.putTabContent("Stats", panelStats);
            clientAccordion.putTabContent("Developer Tools", panelCapture);
            clientAccordion.putTabContent("Debug", panelDebug);
            clientAccordion.putTabContent("Test", panelTest);
            clientAccordion.putTabContent("General", panelGeneral);
            clientAccordion.putTabContent("Mining", panelMining);
            clientAccordion.putTabContent("Giants", panelGiants);
            clientAccordion.putTabContent("Quest", panelQuest);
            clientAccordion.putTabContent("SOS", panelSos);
            clientAccordion.putTabContent("Barbarian", panelBarb);
            clientAccordionActive = true;
        }
        if (mainTabs != null && mainTabs.getParent() != null) {
            mainTabs.getParent().remove(mainTabs);
        }
        if (clientAccordionScroll.getParent() == null && sidebarBody != null) {
            sidebarBody.add(clientAccordionScroll, BorderLayout.CENTER);
        }
        clientAccordion.expandSkillPage(skillPageFor(BotRuntime.activeSkill));
        if (accountsTab != null) {
            accountsTab.setSidebarNarrow(true);
        }
        sidebarBody.revalidate();
        sidebarBody.repaint();
    }

    private void restoreWindowTabs() {
        if (clientAccordion == null || !clientAccordionActive) {
            return;
        }
        putTab("Accounts", clientAccordion.takeTabContent("Accounts"));
        putTab("Settings", clientAccordion.takeTabContent("Settings"));
        if (skillTabs != null) {
            addSkillCard("Woodcut", clientAccordion.takeTabContent("Woodcut"));
            addSkillCard("Fishing", clientAccordion.takeTabContent("Fishing"));
            addSkillCard("Star", clientAccordion.takeTabContent("Star"));
            addSkillCard("Imps", clientAccordion.takeTabContent("Imps"));
            addSkillCard("Combat", clientAccordion.takeTabContent("Combat"));
            addSkillCard("General", clientAccordion.takeTabContent("General"));
            addSkillCard("Mining", clientAccordion.takeTabContent("Mining"));
            addSkillCard("Giants", clientAccordion.takeTabContent("Giants"));
            addSkillCard("Quest", clientAccordion.takeTabContent("Quest"));
            addSkillCard("SOS", clientAccordion.takeTabContent("SOS"));
            addSkillCard("Barbarian", clientAccordion.takeTabContent("Barbarian"));
        }
        putTab("Skills", skillsWrap);
        putTab("Stats", clientAccordion.takeTabContent("Stats"));
        putTab("Centers", clientAccordion.takeTabContent("Centers"));
        putTab("Developer Tools", clientAccordion.takeTabContent("Developer Tools"));
        putTab("Debug", clientAccordion.takeTabContent("Debug"));
        putTab("Test", clientAccordion.takeTabContent("Test"));
        if (clientAccordionScroll != null && clientAccordionScroll.getParent() != null) {
            clientAccordionScroll.getParent().remove(clientAccordionScroll);
        }
        clientAccordionActive = false;
    }

    private void putTab(String title, Component c) {
        if (mainTabs == null || c == null) {
            return;
        }
        int idx = tabIndex(title);
        if (idx >= 0) {
            mainTabs.setComponentAt(idx, c);
        }
    }

    private void addSkillCard(String name, Component c) {
        if (skillTabs == null || c == null) {
            return;
        }
        Container p = c.getParent();
        if (p != null) {
            p.remove(c);
        }
        for (int i = 0; i < skillTabs.getTabCount(); i++) {
            if (name.equals(skillTabs.getTitleAt(i))) {
                skillTabs.setComponentAt(i, c);
                return;
            }
        }
        skillTabs.addTab(name, c);
    }

    private int tabIndex(String title) {
        if (mainTabs == null) {
            return -1;
        }
        for (int i = 0; i < mainTabs.getTabCount(); i++) {
            if (title.equals(mainTabs.getTitleAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private void showSkillSettingsPage(BotRuntime.ActiveSkill skill) {
        String page = skillPageFor(skill);
        if (clientAccordionActive && clientAccordion != null) {
            clientAccordion.expandSkillPage(page);
            return;
        }
        if (mainTabs == null || skillTabs == null) {
            return;
        }
        for (int i = 0; i < mainTabs.getTabCount(); i++) {
            if ("Skills".equals(mainTabs.getTitleAt(i))) {
                mainTabs.setSelectedIndex(i);
                break;
            }
        }
        selectSkillTab(page);
    }

    private void selectSkillTab(String page) {
        if (skillTabs == null || page == null) {
            return;
        }
        for (int i = 0; i < skillTabs.getTabCount(); i++) {
            if (page.equals(skillTabs.getTitleAt(i))) {
                skillTabs.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String skillPageFor(BotRuntime.ActiveSkill skill) {
        if (skill == null) {
            return "Woodcut";
        }
        switch (skill) {
            case FISHING:
                return "Fishing";
            case STAR:
                return "Star";
            case GIANTS:
                return "Giants";
            case CLUE:
                return "Clue";
            case IMP:
                return "Imps";
            case IMP2:
                return "Imps2";
            case COW:
                return "Combat";
            case MONK:
                return "Combat";
            case QUEST:
                return "Quest";
            case WOODCUTTING:
            case NONE:
            default:
                return "Woodcut";
        }
    }

    private static void refreshTabColors(JTabbedPane tabs) {
        LoneBotUiTheme.refreshTabColors(tabs, -1);
    }

    /**
     * Pas huidige yaw/pitch/zoom settings direct toe op de camera (geen popup).
     * Nodig omdat mode/offset anders alleen opgeslagen worden tot Preview/walk.
     */
    private void applyWalkCamLive(boolean showPopup) {
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = WalkCamera.runPreview(8);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("applyWalkCamLive: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> {
                setStatus(finalMsg.replace("\n", " "));
                appendConsole("WalkCam live: " + finalMsg.replace("\n", " | "));
                if (showPopup) {
                    JOptionPane.showMessageDialog(this, finalMsg, "Camera",
                            finalMsg.startsWith("OK") ? JOptionPane.INFORMATION_MESSAGE
                                    : JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "lonebot-cam-live");
        t.setDaemon(true);
        t.start();
    }

    private void testWalkCameraPreview() {
        setStatus("Camera preview…");
        applyWalkCamLive(true);
    }

    private void testWalkCameraWalk() {
        setStatus("Walk + camera test…");
        Component parent = this;
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = WalkCamera.runWalkTest(10);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testWalkCameraWalk: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> {
                setStatus(finalMsg.replace("\n", " "));
                appendConsole("WalkCamera walk: " + finalMsg.replace("\n", " | "));
                JOptionPane.showMessageDialog(parent, finalMsg, "Walk + camera",
                        finalMsg.startsWith("OK") ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            });
        }, "lonebot-cam-walk");
        t.setDaemon(true);
        t.start();
    }

    private void copyWalkCameraReport() {
        String report = WalkCameraSettings.reportBlock();
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
            setStatus("✓ Camera-rapport gekopieerd — plak in chat");
            appendConsole(report);
            JOptionPane.showMessageDialog(this, report + "\n\n(ook op klembord)", "Camera-rapport",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Throwable t) {
            setStatus("⚠ Clipboard: " + t.getMessage());
            appendConsole(report);
            JOptionPane.showMessageDialog(this, report, "Camera-rapport", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void testHopConfirm() {
        setStatus("Hop Switch world test…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                boolean open = net.storm.sdk.community.WorldHopper.isHopConfirmOpen();
                String diag = net.storm.sdk.community.WorldHopper.diagnose();
                boolean ok = net.storm.sdk.community.WorldHopper.confirmSwitchWorld();
                int world = net.storm.sdk.game.Worlds.getCurrentWorld();
                msg = (ok ? "✓ " : "⚠ ") + "Switch world"
                        + " open=" + open
                        + " klik=" + ok
                        + " wereld=" + world;
                String line = "[HopTest] " + msg + " | " + diag;
                SwingUtilities.invokeLater(() -> appendConsole(line));
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testHopConfirm: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-hop");
        t.setDaemon(true);
        t.start();
    }

    private void testLogout(net.storm.sdk.game.Game.LogoutMethod method, String label) {
        setStatus("Logout test " + label + "…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                boolean wasIn = net.storm.sdk.game.Game.isLoggedIn();
                if (!wasIn && net.storm.sdk.game.Game.isOnLoginScreen()) {
                    msg = "⚠ logout " + label + " — al op login-scherm";
                    BotRuntime.logConsole("[LogoutTest] " + msg);
                } else {
                    boolean clicked = net.storm.sdk.game.Game.logout(method);
                    BotRuntime.logConsole("[LogoutTest] " + label
                            + " klik=" + clicked + " — wacht op login-scherm (state volgt later)");
                    boolean left = waitUntilLoggedOut(2200);
                    if (!left && method == net.storm.sdk.game.Game.LogoutMethod.AUTO) {
                        BotRuntime.logConsole("[LogoutTest] nog in-game → 1 extra AUTO (tab/bevestigen)");
                        net.storm.sdk.game.Game.logout(method);
                        left = waitUntilLoggedOut(2200);
                    }
                    boolean login = net.storm.sdk.game.Game.isOnLoginScreen();
                    boolean still = net.storm.sdk.game.Game.isLoggedIn();
                    msg = (left || login ? "✓ " : "⚠ ") + "logout " + label
                            + " klik=" + clicked
                            + " wasIn=" + wasIn
                            + " nogIn=" + still
                            + " loginScreen=" + login;
                    BotRuntime.logConsole("[LogoutTest] " + msg);
                }
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testLogout {}: {}", label, ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-logout");
        t.setDaemon(true);
        t.start();
    }

    /** Logout-packet is al weg; GameState blijft kort LOGGED_IN. */
    private static boolean waitUntilLoggedOut(int maxMs) {
        long deadline = System.currentTimeMillis() + Math.max(400, maxMs);
        while (System.currentTimeMillis() < deadline) {
            if (net.storm.sdk.game.Game.isOnLoginScreen() || !net.storm.sdk.game.Game.isLoggedIn()) {
                return true;
            }
            try {
                Thread.sleep(180L + (long) (Math.random() * 80));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return net.storm.sdk.game.Game.isOnLoginScreen()
                        || !net.storm.sdk.game.Game.isLoggedIn();
            }
        }
        return net.storm.sdk.game.Game.isOnLoginScreen() || !net.storm.sdk.game.Game.isLoggedIn();
    }

    private void testAutoLoginRelog() {
        setStatus("Test auto-login…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                if (RelogRuntime.relogger == null) {
                    msg = "⚠ Relogger niet gestart — herstart client";
                } else {
                    RelogRuntime.relogger.startTestAutoLoginFromLoginScreen();
                    msg = "🔁 Test auto-login gestart — console [Relog]";
                }
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testAutoLoginRelog: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> {
                appendConsole("[Relog] " + finalMsg);
                setStatus(finalMsg);
            });
        }, "lonebot-test-autologin");
        t.setDaemon(true);
        t.start();
    }

    private void testJagexPlayNow() {
        setStatus("Test Jagex Play Now…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                JagexLauncherPlayButton.debugTestJagexLoginPlayNow();
                msg = "▶ Play Now-test gedaan — console [Login]";
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testJagexPlayNow: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-playnow");
        t.setDaemon(true);
        t.start();
    }

    private void testWelcomePlay() {
        setStatus("Test welkomst Play…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                WelcomeScreenPlayHelper.debugTestClickPlay();
                msg = "▶ Welkomst Play-test gedaan — console [Login]";
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testWelcomePlay: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-welcome-play");
        t.setDaemon(true);
        t.start();
    }

    private void testDepositQtyDump() {
        setStatus("Dump deposit qty…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                java.util.List<String> lines = DepositBox.dumpQuantityButtons();
                msg = lines.isEmpty() ? "geen dump" : lines.get(0);
                BotRuntime.logConsole("[QtyTest] " + msg);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testDepositQtyDump: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-qty-dump");
        t.setDaemon(true);
        t.start();
    }

    private void testDepositQtyClick(DepositBox.QtyMode mode) {
        setStatus("Test qty " + mode.label + "…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                java.util.List<String> dump = DepositBox.dumpQuantityButtons();
                if (!dump.isEmpty()) {
                    BotRuntime.logConsole("[QtyTest] " + dump.get(0));
                }
                msg = DepositBox.clickQuantityIfOff(mode);
                BotRuntime.logConsole("[QtyTest] " + msg);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testDepositQtyClick {}: {}", mode.label, ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-qty-click");
        t.setDaemon(true);
        t.start();
    }

    private void testRunRead() {
        setStatus("Run: lees staat…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = net.storm.sdk.movement.Movement.dumpRunState();
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testRunRead: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-run-read");
        t.setDaemon(true);
        t.start();
    }

    private void testRunSet(boolean on) {
        setStatus(on ? "Run AAN…" : "Run UIT…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = net.storm.sdk.movement.Movement.testSetRunEnabled(on);
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testRunSet: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-run-set");
        t.setDaemon(true);
        t.start();
    }

    private void startGeTradeTest(boolean buy, String itemRaw, String qtyRaw, String priceRaw) {
        String item = itemRaw != null ? itemRaw.trim() : "";
        if (item.isEmpty()) {
            setStatus("GE-test: vul een itemnaam in");
            BotRuntime.logConsole("[GE/test] geen itemnaam");
            return;
        }
        int qty;
        int price;
        try {
            qty = Math.max(1, Integer.parseInt(qtyRaw != null ? qtyRaw.trim() : "1"));
            price = Math.max(1, Integer.parseInt(priceRaw != null ? priceRaw.trim() : "1"));
        } catch (NumberFormatException e) {
            setStatus("GE-test: aantal en gp moeten getallen zijn");
            return;
        }
        BotRuntime.cityCircleTestEnabled = false;
        BotRuntime.varrockEastBankTestEnabled = false;
        BotRuntime.setActiveSkill(BotRuntime.ActiveSkill.NONE);
        LoneBotBotControl.syncSkillFlagsToConfig(configManager);
        BotRuntime.geTradeBuy = buy;
        BotRuntime.geTradeItem = item;
        BotRuntime.geTradeQty = qty;
        BotRuntime.geTradePrice = price;
        BotRuntime.geTradeStatus = (buy ? "koop " : "verkoop ") + qty + "× " + item;
        BotRuntime.geTradeTestEnabled = true;
        LoneBotBotControl.start();
        String kind = buy ? "koop" : "verkoop";
        setStatus("GE-test " + kind + " " + qty + "× " + item + " @" + price + "gp — naar GE");
        BotRuntime.logConsole("[GE/test] start " + kind + " " + qty + "× " + item + " @" + price + "gp");
    }

    private void testInvokeWalk() {
        setStatus("Test invoke WALK…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                net.runelite.api.coords.WorldPoint from = net.storm.sdk.entities.Players.snapshotLocal() != null
                        && net.storm.sdk.entities.Players.snapshotLocal().present
                        ? net.storm.sdk.entities.Players.snapshotLocal().worldLocation
                        : null;
                if (from == null) {
                    msg = "⚠ geen speler";
                } else {
                    net.runelite.api.coords.WorldPoint dest =
                            new net.runelite.api.coords.WorldPoint(from.getX(), from.getY() + 5, from.getPlane());
                    boolean ok = WalkClickHelper.invokeWalk(dest);
                    msg = (ok ? "✓ " : "⚠ ") + "invoke WALK → " + dest
                            + (ok ? "" : " (client-thread timeout of tile niet in scene)");
                    if (ok) {
                        WalkClickSettings.lastMethod = "invoke-WALK";
                        WalkClickSettings.lastDetail = dest.toString();
                    }
                }
            } catch (Throwable ex) {
                msg = "⚠ " + ex;
                log.warn("testInvokeWalk: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-invoke-walk");
        t.setDaemon(true);
        t.start();
    }

    private void testMmbCamera() {
        setStatus("MMB synthetic-test… (geen focus-steal)");
        Thread t = new Thread(() -> {
            String msg;
            try {
                int startX = 300 + (int) (Math.random() * 120);
                int startY = 200 + (int) (Math.random() * 80);
                boolean right = Math.random() < 0.5;
                int endX = startX + (right ? 80 : -80);
                int endY = startY + (int) (Math.random() * 10 - 5);
                int yawBefore = net.storm.sdk.game.Camera.getYaw();
                boolean ok = Mouse.mmbCameraDragSynthetic(startX, startY, endX, endY, 24, 25, 50);
                int yawAfter = net.storm.sdk.game.Camera.getYaw();
                int dYaw = yawAfter - yawBefore;
                if (dYaw > 1024) {
                    dYaw -= 2048;
                }
                if (dYaw < -1024) {
                    dYaw += 2048;
                }
                String dbg = (ok ? "synth MMB" : "FAIL") + " Δyaw=" + dYaw
                        + (dYaw == 0 ? " (geen cam — normaal zonder focus)" : "");
                WalkClickSettings.lastCameraDebug = dbg;
                AntiBan.get().markBotActivity();
                msg = (ok ? "✓ " : "⚠ ") + dbg;
            } catch (Throwable ex) {
                msg = "⚠ MMB: " + ex;
                log.warn("testMmbCamera: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-mmb");
        t.setDaemon(true);
        t.start();
    }

    private void testForceDoubleClick() {
        setStatus("Force snappy+DBL…");
        Thread t = new Thread(() -> {
            String msg;
            try {
                boolean ok = WalkClickHelper.testForceSnappyDoubleClick();
                msg = (ok ? "✓ " : "⚠ ") + WalkClickSettings.lastHumanSummary
                        + " | " + WalkClickSettings.lastMethod;
            } catch (Throwable ex) {
                msg = "⚠ DBL: " + ex;
                log.warn("testForceDoubleClick: {}", ex.toString());
            }
            String finalMsg = msg;
            SwingUtilities.invokeLater(() -> setStatus(finalMsg));
        }, "lonebot-test-dbl");
        t.setDaemon(true);
        t.start();
    }

    private JCheckBox createRuneliteDeveloperModeCheck() {
        boolean on = com.lonebot.launcher.RuneliteDeveloperMode.isEnabled();
        String label = com.lonebot.launcher.RuneliteDeveloperMode.isCurrentProcessEnabled()
                ? "RuneLite Developer Tools (sidebar actief)"
                : "RuneLite Developer Tools (Players/NPCs/Widget Inspector)";
        JCheckBox cb = new JCheckBox(label, on);
        styleCheck(cb);
        cb.setToolTipText("Officiële RuneLite DevTools: Players, NPCs, Game Objects, Widget Inspector, "
                + "Detached Camera, Script Inspector, … Bug-icoon in de sidebar. Client herstarten na toggle.");
        cb.addActionListener(e -> {
            boolean enabled = cb.isSelected();
            com.lonebot.launcher.RuneliteDeveloperMode.setEnabled(enabled);
            configManager.setConfiguration("lonebot",
                    com.lonebot.launcher.RuneliteDeveloperMode.CONFIG_KEY, enabled);
            if (enabled && com.lonebot.launcher.RuneliteDeveloperMode.isCurrentProcessEnabled()) {
                setStatus("✓ RuneLite DevTools al actief in deze client");
                BotRuntime.logConsole("[DevTools] RuneLite Developer Tools al geladen in deze client.");
            } else if (enabled) {
                setStatus("✓ Developer Tools AAN — herstart deze client voor de sidebar (bug-icoon)");
                BotRuntime.logConsole("[DevTools] AAN — herstart de game-client voor Players/NPCs/Widget Inspector.");
            } else {
                setStatus("Developer Tools uit — herstart client om de sidebar te verbergen");
                BotRuntime.logConsole("[DevTools] uit — herstart de game-client.");
            }
        });
        LoneBotConfigUiSync.bool(cb, com.lonebot.launcher.RuneliteDeveloperMode.CONFIG_KEY);
        return cb;
    }

    private static void styleCheck(JCheckBox cb) {
        LoneBotUiTheme.styleCheck(cb);
    }

    private static void styleLabel(JLabel lbl) {
        LoneBotUiTheme.styleLabel(lbl);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
