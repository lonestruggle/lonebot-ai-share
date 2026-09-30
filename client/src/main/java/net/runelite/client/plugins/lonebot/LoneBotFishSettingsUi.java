package net.runelite.client.plugins.lonebot;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Fishing settings — CombatBot-parity toggles, LIVE via {@link LoneBotBootstrapPlugin#applyFishSettingsFromUi()}.
 */
final class LoneBotFishSettingsUi {

    private LoneBotFishSettingsUi() {
    }

    static void fillBasis(JPanel sec, LoneBotWcImpSettingsUi.Host h, JCheckBox sharedEnable) {
        LoneBotConfig cfg = h.config();
        if (sharedEnable != null) {
            LoneBotWcImpSettingsUi.addExistingToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Fishing", sharedEnable);
        }

        JCheckBox cbBest = new JCheckBox();
        cbBest.setSelected(!cfg.fishingUseSpecificMethod());
        h.styleCheck(cbBest);
        JCheckBox cbSpecific = new JCheckBox();
        cbSpecific.setSelected(cfg.fishingUseSpecificMethod());
        h.styleCheck(cbSpecific);

        JTextField spotField = text(cfg.fishingSpotName() != null ? cfg.fishingSpotName() : "Fishing spot");
        JTextField actionField = text(cfg.fishingAction() != null ? cfg.fishingAction() : "Net");
        spotField.setEnabled(cfg.fishingUseSpecificMethod());
        actionField.setEnabled(cfg.fishingUseSpecificMethod());

        LoneBotConfigUiSync.boolInverted(cbBest, "fishingUseSpecificMethod");
        LoneBotConfigUiSync.bool(cbSpecific, "fishingUseSpecificMethod");
        LoneBotConfigUiSync.text(spotField, "fishingSpotName");
        LoneBotConfigUiSync.text(actionField, "fishingAction");
        LoneBotConfigUiSync.enableWhen(spotField, "fishingUseSpecificMethod", true);
        LoneBotConfigUiSync.enableWhen(actionField, "fishingUseSpecificMethod", true);

        Runnable applyMode = () -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            boolean specific = cbSpecific.isSelected();
            h.cm().setConfiguration("lonebot", "fishingUseSpecificMethod", specific);
            spotField.setEnabled(specific);
            actionField.setEnabled(specific);
            LoneBotBootstrapPlugin.applyFishSettingsFromUi();
            h.setStatus(specific ? "Specifieke methode" : "Beste methode (niveau + locatie)");
        };
        cbBest.addActionListener(e -> {
            if (cbBest.isSelected()) {
                cbSpecific.setSelected(false);
            } else if (!cbSpecific.isSelected()) {
                cbSpecific.setSelected(true);
            }
            applyMode.run();
        });
        cbSpecific.addActionListener(e -> {
            if (cbSpecific.isSelected()) {
                cbBest.setSelected(false);
            } else if (!cbBest.isSelected()) {
                cbBest.setSelected(true);
            }
            applyMode.run();
        });
        LoneBotWcImpSettingsUi.addToggleRow(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Beste methode (niveau)", cbBest);
        LoneBotWcImpSettingsUi.addToggleRow(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Specifieke methode", cbSpecific);

        addField(sec, h, "Spot naam", spotField, "fishingSpotName");
        addField(sec, h, "Vis actie", actionField, "fishingAction");

        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Vis droppen",
                "fishingDropFish", cfg.fishingDropFish(), () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Clues doen (bottle/scroll → solver)", "fishClueSolver", cfg.fishClueSolver(),
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());

        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "🔥 Cooking",
                "fishingCookEnabled", cfg.fishingCookEnabled(), () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
    }

    static void fillBaitBankGe(JPanel sec, LoneBotWcImpSettingsUi.Host h) {
        LoneBotConfig cfg = h.config();
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Bait restock (GE)",
                "fishingRestockEnabled", cfg.fishingRestockEnabled(),
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Bait min (bank onder)",
                "fishingBaitMin", cfg.fishingBaitMin(), 0, 500,
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Restock hoeveelheid",
                "fishingRestockAmount", cfg.fishingRestockAmount(), 50, 1000,
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Max gp / bait",
                "fishingBaitPrice", cfg.fishingBaitPrice(), 1, 50,
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Max gp / feather",
                "fishingFeatherPrice", cfg.fishingFeatherPrice(), 1, 50,
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Varrock teleport naar GE",
                "fishingUseVarrockTeleport", cfg.fishingUseVarrockTeleport(),
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
    }

    static void fillDelays(JPanel sec, LoneBotWcImpSettingsUi.Host h, JCheckBox sharedOverlay) {
        LoneBotConfig cfg = h.config();
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Fishing delay min (ms)",
                "fishingInteractDelayMin", cfg.fishingInteractDelayMin(), 0, 5000,
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE, "Fishing delay max (ms)",
                "fishingInteractDelayMax", cfg.fishingInteractDelayMax(), 0, 10000,
                () -> LoneBotBootstrapPlugin.applyFishSettingsFromUi());
        if (sharedOverlay != null) {
            LoneBotWcImpSettingsUi.addExistingToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                    "Fishing overlay", sharedOverlay);
        }
    }

    private static JTextField text(String initial) {
        JTextField f = new JTextField(initial != null ? initial : "", 12);
        f.setFont(LoneBotUiTheme.FONT_LABEL);
        f.setBackground(LoneBotUiTheme.INPUT_BG);
        f.setForeground(LoneBotUiTheme.INPUT_FG);
        f.setCaretColor(LoneBotUiTheme.INPUT_FG);
        LoneBotUiTheme.mark(f, LoneBotUiTheme.ROLE_INPUT);
        return f;
    }

    private static void addField(JPanel sec, LoneBotWcImpSettingsUi.Host h, String label,
                                 JTextField field, String key) {
        Runnable save = () -> {
            if (LoneBotConfigUiSync.applying()) {
                return;
            }
            h.cm().setConfiguration("lonebot", key, field.getText().trim());
            LoneBotBootstrapPlugin.applyFishSettingsFromUi();
        };
        field.addActionListener(e -> save.run());
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                save.run();
            }
        });
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel lbl = new JLabel("✅ " + label);
        lbl.setFont(LoneBotUiTheme.FONT_LABEL);
        lbl.setForeground(LoneBotUiTheme.TEXT);
        lbl.setPreferredSize(new Dimension(140, 24));
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        h.addComp(sec, row);
    }
}
