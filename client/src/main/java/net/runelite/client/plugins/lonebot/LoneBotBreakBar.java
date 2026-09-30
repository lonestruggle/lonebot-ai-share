package net.runelite.client.plugins.lonebot;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.lonebot.login.RelogRuntime;
import net.storm.sdk.bot.BotRuntime;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Break nu + live countdown + re-log timers (ConfigManager keys, {@link LoneBotConfigUiSync}).
 */
final class LoneBotBreakBar extends JPanel {

    private final JLabel countdown = new JLabel("—");
    private final JButton cancelBtn;
    private final Timer tick;

    static LoneBotBreakBar full(ConfigManager cm, LoneBotConfig config) {
        return new LoneBotBreakBar(cm, config, true, true);
    }

    /** Alleen knop + countdown — Settings heeft al sliders. */
    static LoneBotBreakBar buttonAndTimer(ConfigManager cm, LoneBotConfig config) {
        return new LoneBotBreakBar(cm, config, false, false);
    }

    private LoneBotBreakBar(ConfigManager cm, LoneBotConfig config, boolean timers, boolean enableBox) {
        setOpaque(true);
        setBackground(LoneBotDzLook.BG);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(0, 8, 6, 8));
        setAlignmentX(LEFT_ALIGNMENT);

        boolean relogOn = config != null && config.reLogoutEnabled();
        int minOut = config != null ? config.reLogoutMinMinutes() : 60;
        int maxOut = config != null ? config.reLogoutMaxMinutes() : 90;
        int minPause = config != null ? config.reLogoutPauseMinMinutes() : 5;
        int maxPause = config != null ? config.reLogoutPauseMaxMinutes() : 15;

        JButton btn = LoneBotDzLook.actionBtn("☕ Break nu", new Color(70, 95, 140));
        btn.setToolTipText("Nu break: wacht veilig (geen combat) → uitloggen → pauze → weer in. Script hervat in-world.");
        btn.addActionListener(e -> RelogRuntime.requestBreakNow());
        cancelBtn = LoneBotDzLook.actionBtn("✕ Annuleer break", new Color(120, 55, 55));
        cancelBtn.setToolTipText("Stop de break: in game blijven, of pauze overslaan en inloggen.");
        cancelBtn.addActionListener(e -> RelogRuntime.cancelBreak());
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 6, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        btnRow.add(btn);
        btnRow.add(cancelBtn);
        add(btnRow);
        add(Box.createVerticalStrut(4));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        countdown.setForeground(new Color(230, 180, 80));
        countdown.setFont(LoneBotDzLook.FONT_ITEM.deriveFont(Font.BOLD, 12f));
        row.add(countdown);
        if (enableBox) {
            JCheckBox cb = LoneBotDzLook.box(relogOn);
            cb.setText("Re-log");
            cb.setForeground(LoneBotDzLook.TEXT);
            cb.setOpaque(false);
            cb.setToolTipText("Automatische break na min–max minuten");
            LoneBotConfigUiSync.bool(cb, "reLogoutEnabled");
            cb.addActionListener(e -> {
                if (LoneBotConfigUiSync.applying() || cm == null) {
                    return;
                }
                cm.setConfiguration("lonebot", "reLogoutEnabled", cb.isSelected());
            });
            row.add(cb);
        }
        add(row);

        if (timers) {
            add(Box.createVerticalStrut(2));
            add(spinRow(cm, "Tot uitlog (min)",
                    "reLogoutMinMinutes", minOut, 1, 240,
                    "reLogoutMaxMinutes", maxOut, 1, 240));
            add(spinRow(cm, "Pauze (min)",
                    "reLogoutPauseMinMinutes", minPause, 0, 60,
                    "reLogoutPauseMaxMinutes", maxPause, 0, 90));
        }

        tick = new Timer(400, e -> refreshCountdown());
        tick.setRepeats(true);
        refreshCountdown();
    }

    private JPanel spinRow(ConfigManager cm, String label,
                           String minKey, int minVal, int minLo, int minHi,
                           String maxKey, int maxVal, int maxLo, int maxHi) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel l = new JLabel(label);
        l.setForeground(LoneBotDzLook.MUTED);
        l.setFont(LoneBotDzLook.FONT_ITEM.deriveFont(11f));
        JSpinner min = LoneBotDzLook.spinner(minVal, minLo, minHi);
        JSpinner max = LoneBotDzLook.spinner(maxVal, maxLo, maxHi);
        min.setPreferredSize(new Dimension(64, 24));
        max.setPreferredSize(new Dimension(64, 24));
        bindSpin(cm, min, minKey);
        bindSpin(cm, max, maxKey);
        JLabel dash = new JLabel("–");
        dash.setForeground(LoneBotDzLook.MUTED);
        row.add(l);
        row.add(min);
        row.add(dash);
        row.add(max);
        return row;
    }

    private static void bindSpin(ConfigManager cm, JSpinner sp, String key) {
        LoneBotConfigUiSync.integer(sp, key);
        sp.addChangeListener(e -> {
            if (LoneBotConfigUiSync.applying() || cm == null) {
                return;
            }
            cm.setConfiguration("lonebot", key, sp.getValue());
        });
    }

    private void refreshCountdown() {
        String label = BotRuntime.relogLauncherLabel;
        if (label == null) {
            label = "";
        }
        if (label.isBlank()) {
            countdown.setText(BotRuntime.relogInfoEnabled ? "break …" : "timer uit");
            countdown.setForeground(LoneBotDzLook.MUTED);
            return;
        }
        countdown.setText(label);
        boolean hot = label.startsWith("wacht") || label.startsWith("pauze")
                || label.startsWith("uitlog") || label.startsWith("inlog")
                || BotRuntime.breakPending || BotRuntime.relogFlowActive;
        countdown.setForeground(hot ? new Color(230, 180, 80) : new Color(140, 200, 160));
        if (cancelBtn != null) {
            cancelBtn.setEnabled(hot);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (tick != null && !tick.isRunning()) {
            tick.start();
        }
    }

    @Override
    public void removeNotify() {
        if (tick != null) {
            tick.stop();
        }
        super.removeNotify();
    }
}
