package net.runelite.client.plugins.lonebot;

import net.runelite.client.config.ConfigManager;
import net.storm.sdk.bot.BotRuntime;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Compacte Start/Pauze/Stop + script-keuze, vast bovenaan de RuneLite-sidebar.
 */
final class LoneBotSidebarQuickControl extends JPanel {

    private static final SkillItem[] SKILLS = {
            new SkillItem("(geen script)", BotRuntime.ActiveSkill.NONE),
            new SkillItem("Woodcutting", BotRuntime.ActiveSkill.WOODCUTTING),
            new SkillItem("Fishing", BotRuntime.ActiveSkill.FISHING),
            new SkillItem("Star Miner", BotRuntime.ActiveSkill.STAR),
            new SkillItem("Giants", BotRuntime.ActiveSkill.GIANTS),
            new SkillItem("Beginner Clue", BotRuntime.ActiveSkill.CLUE),
            new SkillItem("Imp Killer", BotRuntime.ActiveSkill.IMP),
            new SkillItem("Imps2", BotRuntime.ActiveSkill.IMP2),
            new SkillItem("Cow Combat", BotRuntime.ActiveSkill.COW),
            new SkillItem("Monk Killer", BotRuntime.ActiveSkill.MONK),
            new SkillItem("Quest", BotRuntime.ActiveSkill.QUEST)
    };

    private final ConfigManager configManager;
    private final LoneBotConfig config;
    private final Runnable bringControlToFront;
    private final Consumer<BotRuntime.ActiveSkill> onUserSkillPicked;
    private final AtomicBoolean suppressSkillWrite = new AtomicBoolean(false);

    private final JLabel statusDot = new JLabel("●  Uit");
    private final JLabel accountLbl = new JLabel(" ");
    private final JLabel liveLbl = new JLabel("Idle");
    private final JComboBox<SkillItem> skillCombo = new JComboBox<>(SKILLS);
    private final JButton btnStart;
    private final JButton btnPause;
    private final JButton btnStop;
    private final JButton btnFocus;
    private final JLabel titleLbl;
    private final JLabel hint;
    private final Timer refreshTimer;

    LoneBotSidebarQuickControl(ConfigManager configManager, LoneBotConfig config,
                               Runnable bringControlToFront,
                               Consumer<BotRuntime.ActiveSkill> onUserSkillPicked) {
        this.configManager = configManager;
        this.config = config;
        this.bringControlToFront = bringControlToFront;
        this.onUserSkillPicked = onUserSkillPicked;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(2, 2, 4, 2));

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        titleLbl = new JLabel("Plugin control");
        titleLbl.setForeground(LoneBotUiTheme.GOLD);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 12f));
        titleLbl.setAlignmentX(LEFT_ALIGNMENT);
        col.add(titleLbl);
        col.add(Box.createVerticalStrut(4));

        JPanel statusCard = card();
        statusDot.setFont(statusDot.getFont().deriveFont(Font.BOLD, 13f));
        statusDot.setAlignmentX(LEFT_ALIGNMENT);
        accountLbl.setForeground(LoneBotUiTheme.TEXT_DIM);
        accountLbl.setFont(accountLbl.getFont().deriveFont(Font.PLAIN, 11f));
        accountLbl.setAlignmentX(LEFT_ALIGNMENT);
        liveLbl.setForeground(LoneBotUiTheme.TEXT);
        liveLbl.setFont(liveLbl.getFont().deriveFont(Font.PLAIN, 11f));
        liveLbl.setAlignmentX(LEFT_ALIGNMENT);
        statusCard.add(statusDot);
        statusCard.add(Box.createVerticalStrut(2));
        statusCard.add(accountLbl);
        statusCard.add(Box.createVerticalStrut(6));
        statusCard.add(liveLbl);
        col.add(statusCard);
        col.add(Box.createVerticalStrut(6));

        JLabel scriptLbl = new JLabel("Script");
        scriptLbl.setForeground(LoneBotUiTheme.TEXT_DIM);
        scriptLbl.setFont(scriptLbl.getFont().deriveFont(Font.PLAIN, 11f));
        scriptLbl.setAlignmentX(LEFT_ALIGNMENT);
        col.add(scriptLbl);
        col.add(Box.createVerticalStrut(4));

        LoneBotUiTheme.styleCombo(skillCombo);
        skillCombo.setAlignmentX(LEFT_ALIGNMENT);
        skillCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        skillCombo.setPrototypeDisplayValue(SKILLS[1]);
        skillCombo.setToolTipText("Welk skill-script draait op deze client");
        skillCombo.addActionListener(e -> {
            if (suppressSkillWrite.get()) {
                return;
            }
            SkillItem item = (SkillItem) skillCombo.getSelectedItem();
            BotRuntime.ActiveSkill skill = item != null ? item.skill : BotRuntime.ActiveSkill.NONE;
            LoneBotBotControl.selectSkill(skill, configManager, config);
            if (onUserSkillPicked != null) {
                onUserSkillPicked.accept(skill);
            }
            refresh();
        });
        col.add(skillCombo);
        col.add(Box.createVerticalStrut(6));

        btnStart = actionBtn("▶  Start", new Color(32, 120, 62));
        btnPause = actionBtn("⏸  Pauze", new Color(130, 95, 28));
        btnStop = actionBtn("■  Stop", new Color(130, 42, 48));
        btnStart.setToolTipText("Hervat na pauze, of start opnieuw na Stop");
        btnPause.setToolTipText("Pauzeren — Start gaat verder waar je was");
        btnStop.setToolTipText("Stoppen — Start begint opnieuw");

        JPanel btns = new JPanel(new GridLayout(1, 3, 4, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(LEFT_ALIGNMENT);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        btns.setPreferredSize(new Dimension(200, 30));
        btns.add(btnStart);
        btns.add(btnPause);
        btns.add(btnStop);
        col.add(btns);
        col.add(Box.createVerticalStrut(4));
        LoneBotBreakBar breakBar = LoneBotBreakBar.full(configManager, config);
        breakBar.setOpaque(false);
        col.add(breakBar);
        col.add(Box.createVerticalStrut(6));

        btnStart.addActionListener(e -> {
            SkillItem item = (SkillItem) skillCombo.getSelectedItem();
            LoneBotBotControl.selectSkill(
                    item != null ? item.skill : BotRuntime.ActiveSkill.NONE, configManager, config);
            LoneBotBotControl.start();
            LoneBotBotControl.syncBotEnabledConfig(configManager, true);
            BotRuntime.logConsole("[Quick] Start");
            refresh();
        });
        btnPause.addActionListener(e -> {
            LoneBotBotControl.pause();
            LoneBotBotControl.syncBotEnabledConfig(configManager, false);
            BotRuntime.logConsole("[Quick] Pauze");
            refresh();
        });
        btnStop.addActionListener(e -> {
            boolean resetTimers = config == null || config.resetAccountTimersOnStop();
            LoneBotBotControl.stopAndResetTimers(resetTimers);
            LoneBotBotControl.syncBotEnabledConfig(configManager, false);
            BotRuntime.logConsole("[Quick] Stop");
            refresh();
        });

        btnFocus = actionBtn("📐  Control naar voren", new Color(45, 70, 120));
        btnFocus.setToolTipText("Brengt het volledige Control-venster naar voren");
        btnFocus.setAlignmentX(LEFT_ALIGNMENT);
        btnFocus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnFocus.addActionListener(e -> {
            if (bringControlToFront != null) {
                bringControlToFront.run();
            }
        });
        col.add(btnFocus);
        col.add(Box.createVerticalStrut(8));

        hint = new JLabel("<html><div style='color:#8a90a4;font-size:10px'>"
                + "Settings hieronder, of 📐 voor een breed venster."
                + "</div></html>");
        hint.setAlignmentX(LEFT_ALIGNMENT);
        col.add(hint);

        add(col, BorderLayout.NORTH);

        refreshTimer = new Timer(500, e -> refresh());
        refreshTimer.setRepeats(true);
        setStandalone(false);
        refresh();
    }

    /** true = Control is los venster. false = pinned boven de tabs in de client. */
    void setStandalone(boolean standalone) {
        if (titleLbl != null) {
            titleLbl.setVisible(standalone);
        }
        if (liveLbl != null) {
            liveLbl.setVisible(standalone);
        }
        if (btnFocus != null) {
            btnFocus.setVisible(standalone);
        }
        if (hint != null) {
            hint.setVisible(standalone);
        }
        setBorder(new EmptyBorder(standalone ? 8 : 2, standalone ? 4 : 2, standalone ? 8 : 4, 2));
        revalidate();
        repaint();
    }

    void startRefresh() {
        if (!refreshTimer.isRunning()) {
            refreshTimer.start();
        }
        refresh();
    }

    void stopRefresh() {
        refreshTimer.stop();
    }

    private void refresh() {
        boolean on = BotRuntime.botEnabled;
        btnStart.setEnabled(!on);
        btnPause.setEnabled(on);
        btnStop.setEnabled(on || BotRuntime.pausedForResume);

        if (on) {
            statusDot.setText("●  Aan");
            statusDot.setForeground(new Color(80, 200, 120));
        } else if (BotRuntime.pausedForResume) {
            statusDot.setText("●  Pauze");
            statusDot.setForeground(new Color(230, 180, 80));
        } else {
            statusDot.setText("●  Uit");
            statusDot.setForeground(new Color(130, 130, 145));
        }

        String acc = LoneBotBotControl.launchedAccountName();
        long sec = AccountSessionTimers.elapsedSec(acc);
        if (acc == null || acc.isBlank()) {
            accountLbl.setText("geen account");
        } else if (sec > 0) {
            accountLbl.setText(acc + "  ·  " + formatElapsed(sec));
        } else {
            accountLbl.setText(acc);
        }

        liveLbl.setText("<html>" + escape(liveStatusLine()) + "</html>");

        SkillItem want = itemFor(BotRuntime.activeSkill);
        SkillItem cur = (SkillItem) skillCombo.getSelectedItem();
        if (want != cur && (cur == null || want.skill != cur.skill)) {
            suppressSkillWrite.set(true);
            try {
                skillCombo.setSelectedItem(want);
            } finally {
                suppressSkillWrite.set(false);
            }
        }
    }

    private static String liveStatusLine() {
        if (BotRuntime.woodcuttingEnabled) {
            return nz(BotRuntime.wcStatus, "Woodcutting");
        }
        if (BotRuntime.fishingEnabled) {
            return nz(BotRuntime.fishStatus, "Fishing");
        }
        if (BotRuntime.starMinerEnabled) {
            return nz(BotRuntime.starStatus, "Star Miner");
        }
        if (BotRuntime.giantsKillerEnabled) {
            return nz(BotRuntime.giantsStatus, "Giants");
        }
        if (BotRuntime.impKillerEnabled) {
            return nz(BotRuntime.impStatus, "Imp Killer");
        }
        if (BotRuntime.imps2Enabled) {
            return nz(BotRuntime.imps2Status, "Imps2");
        }
        if (BotRuntime.cowCombatEnabled) {
            return nz(BotRuntime.cowStatus, "Cow Combat");
        }
        if (BotRuntime.monkKillerEnabled) {
            return nz(BotRuntime.monkStatus, "Monk Killer");
        }
        return "Idle — kies een script";
    }

    private static String nz(String s, String fallback) {
        if (s == null || s.isBlank() || "uit".equalsIgnoreCase(s.trim())) {
            return fallback;
        }
        String t = s.trim();
        return t.length() > 90 ? t.substring(0, 87) + "…" : t;
    }

    private static String formatElapsed(long sec) {
        long h = sec / 3600L;
        long m = (sec % 3600L) / 60L;
        long s = sec % 60L;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%d:%02d", m, s);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static SkillItem itemFor(BotRuntime.ActiveSkill skill) {
        BotRuntime.ActiveSkill want = skill != null ? skill : BotRuntime.ActiveSkill.NONE;
        for (SkillItem it : SKILLS) {
            if (it.skill == want) {
                return it;
            }
        }
        return SKILLS[0];
    }

    private static JPanel card() {
        JPanel p = new JPanel();
        p.setOpaque(true);
        p.setBackground(LoneBotUiTheme.BG_SECTION);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(LoneBotUiTheme.BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        return p;
    }

    private static JButton actionBtn(String text, Color bg) {
        JButton b = new JButton(text);
        Color fill = bg != null ? bg : new Color(55, 70, 100);
        b.setUI(new BasicButtonUI());
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        b.setForeground(Color.WHITE);
        b.setBackground(fill);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(
                        Math.min(255, Math.round(fill.getRed() * 1.35f)),
                        Math.min(255, Math.round(fill.getGreen() * 1.35f)),
                        Math.min(255, Math.round(fill.getBlue() * 1.35f))), 1),
                new EmptyBorder(6, 8, 6, 8)));
        b.addChangeListener(e -> {
            if (b.getModel().isPressed()) {
                b.setBackground(new Color(
                        Math.max(0, Math.round(fill.getRed() * 0.82f)),
                        Math.max(0, Math.round(fill.getGreen() * 0.82f)),
                        Math.max(0, Math.round(fill.getBlue() * 0.82f))));
            } else if (b.getModel().isRollover()) {
                b.setBackground(new Color(
                        Math.min(255, Math.round(fill.getRed() * 1.18f)),
                        Math.min(255, Math.round(fill.getGreen() * 1.18f)),
                        Math.min(255, Math.round(fill.getBlue() * 1.18f))));
            } else {
                b.setBackground(fill);
            }
            b.setForeground(Color.WHITE);
        });
        return b;
    }

    private static final class SkillItem {
        final String label;
        final BotRuntime.ActiveSkill skill;

        SkillItem(String label, BotRuntime.ActiveSkill skill) {
            this.label = label;
            this.skill = skill;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
