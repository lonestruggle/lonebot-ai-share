package net.runelite.client.plugins.lonebot;

import com.lonebot.example.CluePlugin;
import net.runelite.client.config.ConfigManager;
import net.storm.sdk.bot.BotRuntime;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * Skills → Clue tab: Beginner Treasure Trail solver aan/uit.
 */
final class LoneBotClueSettingsUi {

    private LoneBotClueSettingsUi() {
    }

    static void fill(JPanel sec, LoneBotWcImpSettingsUi.Host h) {
        LoneBotConfig cfg = h.config();
        ConfigManager cm = h.cm();

        JCheckBox cbOn = new JCheckBox();
        cbOn.setSelected(cfg.clueEnabled() || BotRuntime.clueEnabled
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.CLUE);
        h.styleCheck(cbOn);
        LoneBotWcImpSettingsUi.addToggleRow(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Beginner Clue aan", cbOn);
        cbOn.addActionListener(e -> {
            boolean on = cbOn.isSelected();
            cm.setConfiguration("lonebot", "clueEnabled", on);
            if (on) {
                LoneBotBotControl.selectSkill(BotRuntime.ActiveSkill.CLUE, cm, h.config());
                h.setStatus("✓ Beginner Clue AAN — v" + liveVersion());
            } else if (BotRuntime.activeSkill == BotRuntime.ActiveSkill.CLUE) {
                LoneBotBotControl.selectSkill(BotRuntime.ActiveSkill.NONE, cm, h.config());
                h.setStatus("Beginner Clue uit");
            }
        });

        JCheckBox cbBank = new JCheckBox();
        cbBank.setSelected(cfg.clueBankCasket() || BotRuntime.clueBankCasket);
        h.styleCheck(cbBank);
        LoneBotWcImpSettingsUi.addToggleRow(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Casket banken (niet openen)", cbBank);
        cbBank.addActionListener(e -> {
            boolean on = cbBank.isSelected();
            cm.setConfiguration("lonebot", "clueBankCasket", on);
            BotRuntime.clueBankCasket = on;
            h.setStatus(on ? "Casket → bank" : "Casket → open + loot");
        });

        JCheckBox cbGe = new JCheckBox();
        cbGe.setSelected(cfg.clueGeBuyMissing() || BotRuntime.clueGeBuyMissing);
        h.styleCheck(cbGe);
        LoneBotWcImpSettingsUi.addToggleRow(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Clue items kopen (GE)", cbGe);
        cbGe.addActionListener(e -> {
            boolean on = cbGe.isSelected();
            cm.setConfiguration("lonebot", "clueGeBuyMissing", on);
            BotRuntime.clueGeBuyMissing = on;
            h.setStatus(on ? "GE-koop clue-kit aan" : "GE-koop clue-kit uit");
        });

        JLabel status = new JLabel("-");
        h.styleLabel(status);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel left = new JLabel("Status");
        h.styleLabel(left);
        row.add(left, BorderLayout.WEST);
        row.add(status, BorderLayout.CENTER);
        h.addComp(sec, row);

        JLabel hint = new JLabel("<html><i>Anagram, cryptic, emote, map dig, hot/cold, Charlie. "
                + "Geen scroll → wacht op geode/nest/drop (geen bank-spam). "
                + "Na hot/cold: Strange device → bank (RuneLite Hot/Cold-box wist). "
                + "Ook kiezen via sidebar-script dropdown.</i></html>");
        h.styleLabel(hint);
        h.addComp(sec, hint);

        Timer t = new Timer(800, ev -> {
            String s = BotRuntime.clueStatus != null ? BotRuntime.clueStatus : "-";
            String ver = liveVersion();
            status.setText((BotRuntime.clueEnabled ? "aan" : "uit") + " · v" + ver + " · " + s);
            boolean want = BotRuntime.clueEnabled || BotRuntime.activeSkill == BotRuntime.ActiveSkill.CLUE;
            if (cbOn.isSelected() != want) {
                cbOn.setSelected(want);
            }
            if (cbBank.isSelected() != BotRuntime.clueBankCasket) {
                cbBank.setSelected(BotRuntime.clueBankCasket);
            }
            if (cbGe.isSelected() != BotRuntime.clueGeBuyMissing) {
                cbGe.setSelected(BotRuntime.clueGeBuyMissing);
            }
        });
        t.setRepeats(true);
        t.start();
    }

    private static String liveVersion() {
        String v = BotRuntime.cluePluginVersion;
        if (v != null && !v.isBlank()) {
            return v;
        }
        try {
            return CluePlugin.VERSION;
        } catch (Throwable ignored) {
            return "?";
        }
    }
}
