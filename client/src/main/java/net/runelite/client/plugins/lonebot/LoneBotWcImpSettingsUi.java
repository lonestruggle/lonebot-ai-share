package net.runelite.client.plugins.lonebot;

import com.lonebot.example.imps.ImpsTypes;
import net.runelite.client.config.ConfigManager;
import net.storm.sdk.bot.BotRuntime;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * CombatBot-parity settings UI voor Woodcutting + Imps.
 * Opmaak = CombatBot (label links, control rechts, GOLD headers via panel).
 * {@link Wire#LIVE} = runtime, {@link Wire#LATER} = alleen config/UI.
 */
final class LoneBotWcImpSettingsUi {

    enum Wire {
        LIVE("✅ "),
        LATER("⏳ ");

        final String prefix;

        Wire(String prefix) {
            this.prefix = prefix;
        }

        String label(String text) {
            return prefix + text;
        }
    }

    private LoneBotWcImpSettingsUi() {
    }

    interface Host {
        void addComp(JPanel sec, JComponent c);

        void styleCheck(JCheckBox cb);

        void styleLabel(JLabel l);

        void setStatus(String msg);

        LoneBotConfig config();

        ConfigManager cm();
    }

    static void fillWoodcutBasis(JPanel sec, Host h, JCheckBox sharedEnable) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        ConfigManager cm = h.cm();

        if (sharedEnable != null) {
            addExistingToggle(sec, h, Wire.LIVE, "Woodcutting", sharedEnable);
        }

        // Boom-modus: Beste (niveau) XOR Specifieke naam — geen knop
        JCheckBox cbBestTree = new JCheckBox();
        cbBestTree.setSelected(!cfg.wcUseSpecificTree());
        tip(cbBestTree, Wire.LIVE);
        h.styleCheck(cbBestTree);

        JCheckBox cbSpecificTree = new JCheckBox();
        cbSpecificTree.setSelected(cfg.wcUseSpecificTree());
        tip(cbSpecificTree, Wire.LIVE);
        h.styleCheck(cbSpecificTree);

        JTextField treeField = new JTextField(
                cfg.wcTreeName() != null ? cfg.wcTreeName() : "Willow", 12);
        treeField.setFont(LoneBotUiTheme.FONT_LABEL);
        treeField.setBackground(LoneBotUiTheme.INPUT_BG);
        treeField.setForeground(LoneBotUiTheme.INPUT_FG);
        treeField.setCaretColor(LoneBotUiTheme.INPUT_FG);
        treeField.setEnabled(cfg.wcUseSpecificTree());
        treeField.setToolTipText("Alleen als 'Specifieke boom' aan staat");
        LoneBotUiTheme.mark(treeField, LoneBotUiTheme.ROLE_INPUT);
        LoneBotConfigUiSync.boolInverted(cbBestTree, "wcUseSpecificTree");
        LoneBotConfigUiSync.bool(cbSpecificTree, "wcUseSpecificTree");
        LoneBotConfigUiSync.text(treeField, "wcTreeName");
        LoneBotConfigUiSync.enableWhen(treeField, "wcUseSpecificTree", true);

        Runnable applyTreeMode = () -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            boolean specific = cbSpecificTree.isSelected();
            cm.setConfiguration("lonebot", "wcUseSpecificTree", specific);
            treeField.setEnabled(specific);
            LoneBotBootstrapPlugin.applyWcSettingsFromUi();
            if (specific) {
                h.setStatus("Specifieke boom: " + treeField.getText().trim());
            } else {
                int lvl = resolveWcLevelSafe();
                String best = com.lonebot.example.woodcutter.WcTrees.bestTreeForLevel(lvl);
                h.setStatus("Beste boom (niveau): " + best + " (WC " + lvl + ")");
            }
        };

        cbBestTree.addActionListener(e -> {
            if (cbBestTree.isSelected()) {
                cbSpecificTree.setSelected(false);
            } else if (!cbSpecificTree.isSelected()) {
                cbSpecificTree.setSelected(true);
            }
            applyTreeMode.run();
        });
        cbSpecificTree.addActionListener(e -> {
            if (cbSpecificTree.isSelected()) {
                cbBestTree.setSelected(false);
            } else if (!cbBestTree.isSelected()) {
                cbBestTree.setSelected(true);
            }
            applyTreeMode.run();
        });

        addToggleRow(sec, h, Wire.LIVE, "Beste boom (niveau)", cbBestTree);
        addToggleRow(sec, h, Wire.LIVE, "Specifieke boom", cbSpecificTree);

        Runnable saveTree = () -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            cm.setConfiguration("lonebot", "wcTreeName", treeField.getText().trim());
            LoneBotBootstrapPlugin.applyWcSettingsFromUi();
        };
        treeField.addActionListener(e -> saveTree.run());
        treeField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveTree.run();
            }
        });
        JPanel treeRow = rowShell();
        treeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        treeRow.add(rowLabel("Boom naam", Wire.LIVE), BorderLayout.WEST);
        treeRow.add(treeField, BorderLayout.CENTER);
        h.addComp(sec, treeRow);

        // Locatie alleen op 📍 Centers-tab — hier schrijven ActionListeners per ongeluk AUTO terug
        JLabel locHint = new JLabel("<html><span style='color:#a0a0b0;font-size:10px'>"
                + "WC locatie (AUTO / spot) → tab <b>📍 Centers</b> → Woodcutting"
                + "</span></html>");
        h.styleLabel(locHint);
        h.addComp(sec, locHint);

        addLogModeCombo(sec, h);
        addToggle(sec, h, Wire.LIVE, "Forestry events (F2P)", "wcForestryEvents", cfg.wcForestryEvents(),
                () -> LoneBotBootstrapPlugin.applyWcSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Bird nests", "wcBirdNests", cfg.wcBirdNests(),
                () -> LoneBotBootstrapPlugin.applyWcSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Clues doen (geode/nest/bottle → solver)", "wcClueSolver", cfg.wcClueSolver(),
                () -> LoneBotBootstrapPlugin.applyWcSettingsFromUi());
        addToggle(sec, h, Wire.LATER, "Axe kopen via GE (nog niet klaar)", "wcGeAxeRestockEnabled", cfg.wcGeAxeRestockEnabled(),
                () -> LoneBotBootstrapPlugin.applyWcSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Bank: Lumbridge", "wcBankLumbridge", cfg.wcBankLumbridge(),
                () -> LoneBotBootstrapPlugin.applyWcSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Bank: Draynor", "wcBankDraynor", cfg.wcBankDraynor(),
                () -> LoneBotBootstrapPlugin.applyWcSettingsFromUi());
    }

    /** Bank / Firemaking / Drop — één keuze (banken was eerder “beide toggles uit”). */
    private static void addLogModeCombo(JPanel sec, Host h) {
        LoneBotConfig cfg = h.config();
        JComboBox<LoneBotConfig.WcLogMode> combo = new JComboBox<>(LoneBotConfig.WcLogMode.values());
        combo.setSelectedItem(LoneBotConfig.WcLogMode.fromFlags(cfg.wcFiremaking(), cfg.wcDropLogs()));
        combo.setToolTipText("Volle inv: Bank = storten; Firemaking = bonfire; Drop = droppen");
        tip(combo, Wire.LIVE);
        LoneBotConfigUiSync.wcLogModeCombo(combo);
        combo.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            LoneBotConfig.WcLogMode mode = (LoneBotConfig.WcLogMode) combo.getSelectedItem();
            if (mode == null) {
                return;
            }
            LoneBotBootstrapPlugin.applyWcLogMode(mode);
            h.setStatus("WC logs → " + mode);
        });
        addComboRow(sec, h, Wire.LIVE, "Logs (volle inv)", combo);
    }

    static void fillWoodcutDelays(JPanel sec, Host h, JCheckBox sharedOverlay) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        addSlider(sec, h, Wire.LATER, "WC delay min (ms)", "wcInteractDelayMin", cfg.wcInteractDelayMin(), 0, 5000,
                () -> LoneBotBootstrapPlugin.applyWcSettingsFromUi());
        addSlider(sec, h, Wire.LATER, "WC delay max (ms)", "wcInteractDelayMax", cfg.wcInteractDelayMax(), 0, 10000,
                () -> LoneBotBootstrapPlugin.applyWcSettingsFromUi());
        if (sharedOverlay != null) {
            addExistingToggle(sec, h, Wire.LIVE, "WC overlay", sharedOverlay);
        } else {
            JCheckBox cbOv = new JCheckBox();
            cbOv.setSelected(cfg.showWcOverlay() || cfg.wcDebugOverlay());
            tip(cbOv, Wire.LIVE);
            LoneBotConfigUiSync.bool(cbOv, "wcDebugOverlay");
            LoneBotConfigUiSync.bool(cbOv, "showWcOverlay");
            cbOv.addActionListener(e -> {
                if (LoneBotConfigUiSync.applying()) {
                    return;
                }
                boolean on = cbOv.isSelected();
                h.cm().setConfiguration("lonebot", "showWcOverlay", on);
                h.cm().setConfiguration("lonebot", "wcDebugOverlay", on);
                BotRuntime.wcDebugOverlayEnabled = on;
            });
            addToggleRow(sec, h, Wire.LIVE, "WC overlay", cbOv);
        }
    }

    static void fillImpsBasis(JPanel sec, Host h, JCheckBox sharedEnable,
                              JComboBox<ImpsTypes.ImpsCombatStyle> sharedStyle) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        ConfigManager cm = h.cm();

        if (sharedEnable != null) {
            addExistingToggle(sec, h, Wire.LIVE, "Imp Killer (Karamja)", sharedEnable);
        }

        addToggle(sec, h, Wire.LATER, "Farm money via Imps", "impsFarmMoneyEnabled", cfg.impsFarmMoneyEnabled(), null);
        addSlider(sec, h, Wire.LATER, "Farm money: min. coins", "impsFarmMoneyMinCoins",
                cfg.impsFarmMoneyMinCoins(), 1000, 100000, null);
        addSlider(sec, h, Wire.LATER, "Farm money: deposit dumps", "impsFarmMoneyDumpTarget",
                cfg.impsFarmMoneyDumpTarget(), 1, 20, null);

        if (sharedStyle != null) {
            LoneBotConfigUiSync.combo(sharedStyle, "impsCombatStyle");
            addComboRow(sec, h, Wire.LIVE, "Combat Style", sharedStyle);
        } else {
            JComboBox<ImpsTypes.ImpsCombatStyle> style = new JComboBox<>(ImpsTypes.ImpsCombatStyle.values());
            style.setSelectedItem(cfg.impsCombatStyle());
            LoneBotConfigUiSync.combo(style, "impsCombatStyle");
            style.addActionListener(e -> {
                if (LoneBotConfigUiSync.applying()) {
                    return;
                }
                ImpsTypes.ImpsCombatStyle s = (ImpsTypes.ImpsCombatStyle) style.getSelectedItem();
                if (s != null) {
                    cm.setConfiguration("lonebot", "impsCombatStyle", s);
                    LoneBotBootstrapPlugin.applyImpSettingsFromUi();
                }
            });
            addComboRow(sec, h, Wire.LIVE, "Combat Style", style);
        }

        JComboBox<String> meleeStyle = new JComboBox<>(new String[]{"BALANCED", "ATTACK", "STRENGTH", "DEFENCE"});
        meleeStyle.setSelectedItem(cfg.impsMeleeTrainingStyle());
        LoneBotConfigUiSync.combo(meleeStyle, "impsMeleeTrainingStyle");
        meleeStyle.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            cm.setConfiguration("lonebot", "impsMeleeTrainingStyle", String.valueOf(meleeStyle.getSelectedItem()));
        });
        addComboRow(sec, h, Wire.LATER, "Melee attack style", meleeStyle);

        JComboBox<ImpsTypes.ImpsMageSpell> spell = new JComboBox<>(ImpsTypes.ImpsMageSpell.values());
        spell.setSelectedItem(cfg.impsMageSpell());
        LoneBotConfigUiSync.combo(spell, "impsMageSpell");
        spell.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            ImpsTypes.ImpsMageSpell s = (ImpsTypes.ImpsMageSpell) spell.getSelectedItem();
            if (s != null) {
                cm.setConfiguration("lonebot", "impsMageSpell", s);
                LoneBotBootstrapPlugin.applyImpSettingsFromUi();
            }
        });
        addComboRow(sec, h, Wire.LIVE, "Mage Spell", spell);

        addTextField(sec, h, Wire.LIVE, "Loot items", "impsLootItems",
                cfg.impsLootItems() != null ? cfg.impsLootItems() : "",
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addTextField(sec, h, Wire.LIVE, "Speciale loots (direct)", "impsSpecialLootItems",
                cfg.impsSpecialLootItems() != null ? cfg.impsSpecialLootItems() : "",
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Loot delay (per kills)", "impsLootDelayEnabled",
                cfg.impsLootDelayEnabled(), () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "Kills vóór looten", "impsLootDelayKills",
                cfg.impsLootDelayKills(), 0, 15, () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());

        addToggle(sec, h, Wire.LIVE, "Scatter ashes", "impsScatterAshes", cfg.impsScatterAshes(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Clues doen (scroll/container → solver)", "impsClueSolver", cfg.impsClueSolver(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());

        // Loot pickup — prominent (CombatBot Skills Imps / General-stijl)
        fillImpsLootPickup(sec, h);

        addToggle(sec, h, Wire.LIVE, "Humanize ash loot", "impsAshHumanize", cfg.impsAshHumanize(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "Ash loot: kans %", "impsAshHumanizeChance",
                cfg.impsAshHumanizeChance(), 1, 100, () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LATER, "Ash loot: max tiles (speler)", "impsAshLootMaxPickupTiles",
                cfg.impsAshLootMaxPickupTiles(), 1, 25, null);
        addSlider(sec, h, Wire.LATER, "Ash loot: min. spacing", "impsAshLootMinSpacingTiles",
                cfg.impsAshLootMinSpacingTiles(), 1, 25, null);
        addSlider(sec, h, Wire.LIVE, "Bank threshold", "impsBankThreshold",
                cfg.impsBankThreshold(), 1, 28, () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "Min coins", "impsMinCoins",
                cfg.impsMinCoins(), 30, 500, () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "Idle roam (sec)", "impsIdleRoamSeconds",
                cfg.impsIdleRoamSeconds(), 0, 60, () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Imp gear prep", "impsGearPrepEnabled", cfg.impsGearPrepEnabled(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Imp quest loot", "impsQuestLootEnabled", cfg.impsQuestLootEnabled(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Melee: open met 1x Air Strike", "impsMeleeOpeningAirStrike",
                cfg.impsMeleeOpeningAirStrike(), () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        // Imp NPC ID hardcoded: LoneBotBootstrapPlugin.IMP_NPC_ID (5007)
        addSlider(sec, h, Wire.LATER, "Mage trip cast budget", "impsMageTripCastBudget",
                cfg.impsMageTripCastBudget(), 1, 200, null);
        addToggle(sec, h, Wire.LATER, "Stay inside hunt radius", "impsStayInsideRadius", cfg.impsStayInsideRadius(), null);
    }

    /** Loot pickup mode + strict — herbruikbaar in Skills Imps én Settings → Loot. */
    static void fillImpsLootPickup(JPanel sec, Host h) {
        LoneBotConfig cfg = h.config();
        ConfigManager cm = h.cm();

        JComboBox<ImpsTypes.LootPickupMode> mode = new JComboBox<>(ImpsTypes.LootPickupMode.values());
        mode.setSelectedItem(cfg.impsLootPickupMode());
        mode.setToolTipText("AUTO = Take-invoke (geen LMB op bomen). MOUSE_LEFT alleen als hover Take is.");
        LoneBotConfigUiSync.combo(mode, "impsLootPickupMode");
        mode.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            ImpsTypes.LootPickupMode m = (ImpsTypes.LootPickupMode) mode.getSelectedItem();
            if (m != null) {
                cm.setConfiguration("lonebot", "impsLootPickupMode", m.name());
                LoneBotBootstrapPlugin.applyImpSettingsFromUi();
                h.setStatus("Loot pickup → " + m.name());
            }
        });
        addComboRow(sec, h, Wire.LIVE, "Loot pickup mode", mode);

        addToggle(sec, h, Wire.LIVE, "Imps loot method strict", "impsLootPickupStrict",
                cfg.impsLootPickupStrict(), () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
    }

    static void fillImpsHunt(JPanel sec, Host h) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        JLabel hint = new JLabel("<html><div style='color:#a0a0b0;font-size:10px;width:260px'>"
                + "<i>Centers-tab: actieve Imps centers gaan vóór X/Y/radius hier.</i></div></html>");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(0, 10, 6, 10));
        h.addComp(sec, hint);
        addSlider(sec, h, Wire.LIVE, "Hunting X", "impsHuntingX", cfg.impsHuntingX(), 2700, 3000,
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "Hunting Y", "impsHuntingY", cfg.impsHuntingY(), 3100, 3300,
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "Hunting radius", "impsHuntingRadius", cfg.impsHuntingRadius(), 5, 80,
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
    }

    static void fillImpsGoblin(JPanel sec, Host h) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        addSlider(sec, h, Wire.LATER, "Goblin center X", "impsGoblinCoinCenterX", cfg.impsGoblinCoinCenterX(), 2950, 3050, null);
        addSlider(sec, h, Wire.LATER, "Goblin center Y", "impsGoblinCoinCenterY", cfg.impsGoblinCoinCenterY(), 3160, 3260, null);
        addSlider(sec, h, Wire.LATER, "Goblin radius", "impsGoblinCoinRadius", cfg.impsGoblinCoinRadius(), 3, 30, null);
        addToggle(sec, h, Wire.LATER, "Toon goblin coin overlay", "impsShowGoblinCoinOverlay",
                cfg.impsShowGoblinCoinOverlay(), null);
    }

    static void fillImpsScorpions(JPanel sec, Host h) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        addToggle(sec, h, Wire.LIVE, "Vermijd scorpion zones", "impsAvoidScorpions", cfg.impsAvoidScorpions(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "Scorpion NPC level", "impsScorpionLevel", cfg.impsScorpionLevel(), 1, 100,
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addToggle(sec, h, Wire.LIVE, "Val scorpions aan", "impsAttackScorpions", cfg.impsAttackScorpions(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "Scorpion zone radius", "impsScorpionAvoidRadius",
                cfg.impsScorpionAvoidRadius(), 1, 20, () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
    }

    static void fillImpsGeTeleports(JPanel sec, Host h) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        addToggle(sec, h, Wire.LIVE, "🛒 GE verkoop", "impsGeSellEnabled", cfg.impsGeSellEnabled(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "GE na X bank trips", "impsGeSellAfterBanks",
                cfg.impsGeSellAfterBanks(), 1, 20, () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LIVE, "GE verkoopprijs (gp)", "impsGeSellPrice",
                cfg.impsGeSellPrice(), 1, 10000, () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LATER, "Law rune koopprijs (gp)", "impsLawRuneBuyPrice",
                cfg.impsLawRuneBuyPrice(), 1, 2000, null);
        addToggle(sec, h, Wire.LATER, "Teleports: runes bijkopen", "impsTeleportBuyRunes",
                cfg.impsTeleportBuyRunes(), null);
        addToggle(sec, h, Wire.LATER, "Varrock teleport gebruiken", "impsUseVarrockTeleport",
                cfg.impsUseVarrockTeleport(), null);
        addToggle(sec, h, Wire.LATER, "Falador teleport gebruiken", "impsUseFaladorTeleport",
                cfg.impsUseFaladorTeleport(), null);
        addToggle(sec, h, Wire.LATER, "Lumbridge teleport gebruiken", "impsUseLumbridgeTeleport",
                cfg.impsUseLumbridgeTeleport(), null);
        addToggle(sec, h, Wire.LIVE, "Imp GE restock", "impsGeRestockEnabled", cfg.impsGeRestockEnabled(),
                () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LATER, "Ammo/rune koopprijs (gp)", "impsAmmoRestockPrice",
                cfg.impsAmmoRestockPrice(), 1, 5000, null);
        addToggle(sec, h, Wire.LATER, "Wereld-hop bij andere imp-jager", "impsCompetitorWorldHop",
                cfg.impsCompetitorWorldHop(), null);
    }

    static void fillImpsMeleeOpener(JPanel sec, Host h) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        addToggle(sec, h, Wire.LIVE, "Melee: open met 1x Air Strike", "impsMeleeOpeningAirStrike",
                cfg.impsMeleeOpeningAirStrike(), () -> LoneBotBootstrapPlugin.applyImpSettingsFromUi());
        addSlider(sec, h, Wire.LATER, "Melee opener min afstand", "impsMeleeOpeningAirStrikeMinDistance",
                cfg.impsMeleeOpeningAirStrikeMinDistance(), 1, 12, null);
        addToggle(sec, h, Wire.LATER, "Melee opener debug", "impsMeleeOpeningAirStrikeDebug",
                cfg.impsMeleeOpeningAirStrikeDebug(), null);
    }

    static void fillImpsStepsRally(JPanel sec, Host h) {
        addLegend(sec, h);
        LoneBotConfig cfg = h.config();
        addSlider(sec, h, Wire.LATER, "Loop stap min (tiles)", "impsStepMinDistance",
                cfg.impsStepMinDistance(), 3, 25, null);
        addSlider(sec, h, Wire.LATER, "Loop stap max (tiles)", "impsStepMaxDistance",
                cfg.impsStepMaxDistance(), 5, 35, null);
        addSlider(sec, h, Wire.LATER, "Rally point radius (tiles)", "impsRallyPointRadius",
                cfg.impsRallyPointRadius(), 1, 50, null);
        addSlider(sec, h, Wire.LATER, "Arrival radius (tiles)", "impsArrivalRadius",
                cfg.impsArrivalRadius(), 8, 20, null);
        addToggle(sec, h, Wire.LATER, "Toon rally radius overlay", "impsShowRallyRadius",
                cfg.impsShowRallyRadius(), null);
    }

    // ── CombatBot-style rows ──────────────────────────────────────────

    private static void addLegend(JPanel sec, Host h) {
        JLabel legend = new JLabel("<html><div style='color:#a0a0aa;font-size:10px;width:270px'>"
                + "✅ werkt nu &nbsp;&nbsp; ⏳ alleen UI (nog niet in bot)"
                + "</div></html>");
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        legend.setBorder(new EmptyBorder(2, 10, 6, 10));
        h.addComp(sec, legend);
    }

    private static JPanel rowShell() {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(LoneBotUiTheme.BG_SECTION);
        LoneBotUiTheme.mark(row, LoneBotUiTheme.ROLE_ROW);
        row.setBorder(new EmptyBorder(5, 10, 5, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static JLabel rowLabel(String text, Wire wire) {
        JLabel lbl = new JLabel(wire.label(text));
        lbl.setFont(LoneBotUiTheme.FONT_LABEL);
        lbl.setForeground(wire == Wire.LATER ? LoneBotUiTheme.TEXT_DIM : LoneBotUiTheme.TEXT);
        lbl.setPreferredSize(new Dimension(140, 24));
        tip(lbl, wire);
        return lbl;
    }

    private static void tip(JComponent c, Wire wire) {
        if (c == null) {
            return;
        }
        c.setToolTipText(wire == Wire.LIVE
                ? "Werkt: stuurt de bot/runtime aan"
                : "Nog niet: alleen opgeslagen — bot leest dit nog niet");
    }

    static void addToggle(JPanel sec, Host h, Wire wire, String label, String key,
                          boolean initial, Runnable after) {
        JCheckBox cb = new JCheckBox();
        cb.setSelected(initial);
        cb.setBackground(LoneBotUiTheme.BG_SECTION);
        cb.setForeground(LoneBotUiTheme.GREEN);
        cb.setOpaque(true);
        tip(cb, wire);
        LoneBotConfigUiSync.bool(cb, key);
        cb.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            h.cm().setConfiguration("lonebot", key, cb.isSelected());
            if (after != null) {
                after.run();
            }
            h.setStatus(label + " → " + cb.isSelected());
        });
        addToggleRow(sec, h, wire, label, cb);
    }

    static void addExistingToggle(JPanel sec, Host h, Wire wire, String label, JCheckBox existing) {
        if (existing == null) {
            return;
        }
        existing.setText("");
        existing.setBackground(LoneBotUiTheme.BG_SECTION);
        existing.setForeground(LoneBotUiTheme.GREEN);
        existing.setOpaque(true);
        tip(existing, wire);
        addToggleRow(sec, h, wire, label, existing);
    }

    static void addToggleRow(JPanel sec, Host h, Wire wire, String label, JCheckBox cb) {
        JPanel row = rowShell();
        row.add(rowLabel(label, wire), BorderLayout.WEST);
        row.add(cb, BorderLayout.EAST);
        h.addComp(sec, row);
    }

    static <T extends Enum<T>> void addEnum(JPanel sec, Host h, Wire wire, String label, String key,
                                            T[] values, T selected, Runnable after) {
        JComboBox<T> combo = new JComboBox<>(values);
        if (selected != null) {
            combo.setSelectedItem(selected);
        }
        LoneBotConfigUiSync.combo(combo, key);
        combo.addActionListener(e -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            Object v = combo.getSelectedItem();
            if (v != null) {
                h.cm().setConfiguration("lonebot", key, v);
                if (after != null) {
                    after.run();
                }
                h.setStatus(label + " → " + v);
            }
        });
        addComboRow(sec, h, wire, label, combo);
    }

    private static void addComboRow(JPanel sec, Host h, Wire wire, String label, JComboBox<?> combo) {
        JPanel row = rowShell();
        row.add(rowLabel(label, wire), BorderLayout.WEST);
        combo.setFont(LoneBotUiTheme.FONT_LABEL);
        LoneBotUiTheme.styleCombo(combo);
        LoneBotUiTheme.mark(combo, LoneBotUiTheme.ROLE_INPUT);
        tip(combo, wire);
        row.add(combo, BorderLayout.CENTER);
        h.addComp(sec, row);
    }

    private static int resolveWcLevelSafe() {
        try {
            return com.lonebot.example.woodcutter.WcTrees.wcLevel();
        } catch (Throwable t) {
            return 1;
        }
    }

    static void addTextField(JPanel sec, Host h, Wire wire, String label, String key,
                                     String initial, Runnable after) {
        JPanel row = rowShell();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.add(rowLabel(label, wire), BorderLayout.WEST);
        JTextField field = new JTextField(initial != null ? initial : "", 14);
        field.setFont(LoneBotUiTheme.FONT_LABEL);
        field.setBackground(LoneBotUiTheme.INPUT_BG);
        field.setForeground(LoneBotUiTheme.INPUT_FG);
        field.setCaretColor(LoneBotUiTheme.INPUT_FG);
        LoneBotUiTheme.mark(field, LoneBotUiTheme.ROLE_INPUT);
        tip(field, wire);
        LoneBotConfigUiSync.text(field, key);
        Consumer<Void> save = v -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            h.cm().setConfiguration("lonebot", key, field.getText().trim());
            if (after != null) {
                after.run();
            }
        };
        field.addActionListener(e -> save.accept(null));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                save.accept(null);
            }
        });
        row.add(field, BorderLayout.CENTER);
        h.addComp(sec, row);
    }

    static void addSlider(JPanel sec, Host h, Wire wire, String label, String key, int initial,
                          int min, int max, Runnable after) {
        JPanel row = rowShell();
        row.add(rowLabel(label, wire), BorderLayout.WEST);

        JLabel valueLabel = new JLabel(String.valueOf(initial));
        valueLabel.setFont(LoneBotUiTheme.FONT_VALUE);
        valueLabel.setForeground(LoneBotUiTheme.GREEN);
        valueLabel.setPreferredSize(new Dimension(50, 24));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        tip(valueLabel, wire);
        row.add(valueLabel, BorderLayout.EAST);

        JSlider slider = new JSlider(min, max, Math.max(min, Math.min(max, initial)));
        slider.setBackground(LoneBotUiTheme.BG_SECTION);
        slider.setForeground(LoneBotUiTheme.GREEN);
        slider.setFocusable(false);
        tip(slider, wire);
        LoneBotConfigUiSync.slider(slider, valueLabel, key);
        slider.addChangeListener(e -> {
            int v = slider.getValue();
            valueLabel.setText(String.valueOf(v));
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            if (!slider.getValueIsAdjusting()) {
                h.cm().setConfiguration("lonebot", key, v);
                if (after != null) {
                    after.run();
                }
            }
        });
        row.add(slider, BorderLayout.CENTER);
        h.addComp(sec, row);
    }
}
