package net.runelite.client.plugins.lonebot;

import com.lonebot.example.QuesterPlugin;
import com.lonebot.example.quest.QuestBotTarget;
import net.runelite.client.config.ConfigManager;
import net.storm.sdk.bot.BotRuntime;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * Quest tab + Settings → Quest Bot. Replaces comingSoon.
 */
final class LoneBotQuestSettingsUi {

    private LoneBotQuestSettingsUi() {
    }

    static void fill(JPanel sec, LoneBotWcImpSettingsUi.Host h) {
        LoneBotConfig cfg = h.config();
        ConfigManager cm = h.cm();

        JCheckBox cbOn = new JCheckBox();
        cbOn.setSelected(cfg.questEnabled() || BotRuntime.questEnabled
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.QUEST);
        h.styleCheck(cbOn);
        LoneBotWcImpSettingsUi.addToggleRow(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Quest Bot aan", cbOn);
        cbOn.addActionListener(e -> {
            boolean on = cbOn.isSelected();
            cm.setConfiguration("lonebot", "questEnabled", on);
            if (on) {
                LoneBotBotControl.selectSkill(BotRuntime.ActiveSkill.QUEST, cm, h.config());
                LoneBotBootstrapPlugin.applyQuestSettingsFromUi();
                h.setStatus("✓ Quest Bot AAN — " + QuestBotTarget.from(QuesterPlugin.target).label);
            } else if (BotRuntime.activeSkill == BotRuntime.ActiveSkill.QUEST) {
                LoneBotBotControl.selectSkill(BotRuntime.ActiveSkill.NONE, cm, h.config());
                h.setStatus("Quest Bot uit");
            }
        });

        LoneBotWcImpSettingsUi.addEnum(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Doel", "questTarget", QuestBotTarget.values(),
                QuestBotTarget.from(cfg.questTarget()),
                LoneBotBootstrapPlugin::applyQuestSettingsFromUi);

        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Rotatie (volgende open F2P)", "questRotationEnabled",
                cfg.questRotationEnabled(), LoneBotBootstrapPlugin::applyQuestSettingsFromUi);
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Skip te laag level (Doric Mining≥10)", "questSkipLowLevel",
                cfg.questSkipLowLevel(), LoneBotBootstrapPlugin::applyQuestSettingsFromUi);

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

        JLabel hint = new JLabel("<html><i>F2P: Cook's Assistant, Goblin Diplomacy, Romeo &amp; Juliet, "
                + "Rune Mysteries, Doric's, Vampire Slayer, Tutorial Island. "
                + "Geen Zoinkwiz runtime. Start via Accounts → Besturing.</i></html>");
        h.styleLabel(hint);
        h.addComp(sec, hint);

        Timer t = new Timer(800, ev -> {
            String s = BotRuntime.questStatus != null ? BotRuntime.questStatus : "-";
            String ver = BotRuntime.questPluginVersion != null ? BotRuntime.questPluginVersion : "?";
            String tgt = QuesterPlugin.target != null ? QuesterPlugin.target : BotRuntime.questTarget;
            status.setText((BotRuntime.questEnabled ? "aan" : "uit") + " · " + tgt + " · v" + ver + " · " + s);
        });
        t.setRepeats(true);
        t.start();
    }
}
