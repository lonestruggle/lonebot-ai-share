package net.runelite.client.plugins.lonebot;

import net.runelite.client.ui.ColorScheme;
import com.lonebot.launcher.LauncherGlass;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Launcher News: changelog + GitHub updates + herstart.
 */
public final class LoneBotNewsPanel extends JPanel {

    private final Consumer<String> status;
    private final JLabel updateStatus = new JLabel(" ");
    private final JTextField tfRepo = new JTextField();
    private final JPasswordField tfToken = new JPasswordField();
    private final JButton btnInstallUpdate = new JButton("⬇ Installeer update + herstart");
    private final JCheckBox cbUpdateStart = new JCheckBox("Check updates bij start");

    public LoneBotNewsPanel(Consumer<String> status) {
        this.status = status != null ? status : s -> {};
        setLayout(new BorderLayout(0, 8));
        setOpaque(true);
        setBackground(new Color(40, 42, 52));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        loadPrefs();

        JLabel changelog = new JLabel(LoneBotChangelog.asHtml());
        changelog.setVerticalAlignment(JLabel.TOP);
        changelog.setOpaque(true);
        changelog.setBackground(new Color(34, 36, 46));
        JScrollPane changeScroll = new JScrollPane(changelog);
        changeScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(55, 60, 78)), "Changelog"));
        changeScroll.setOpaque(true);
        changeScroll.setBackground(new Color(40, 42, 52));
        changeScroll.getViewport().setOpaque(true);
        changeScroll.getViewport().setBackground(new Color(34, 36, 46));
        changeScroll.setPreferredSize(new Dimension(400, 220));

        JPanel updates = new JPanel();
        updates.setLayout(new BoxLayout(updates, BoxLayout.Y_AXIS));
        updates.setOpaque(true);
        updates.setBackground(new Color(40, 42, 52));
        updates.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(55, 60, 78)), "Updates"));

        addLabeled(updates, "Repo (owner/naam):", tfRepo);
        addLabeled(updates, "GitHub token:", tfToken);
        styleCheck(cbUpdateStart);
        cbUpdateStart.setAlignmentX(Component.LEFT_ALIGNMENT);
        updates.add(cbUpdateStart);
        updates.add(Box.createVerticalStrut(6));

        updateStatus.setForeground(ColorScheme.BRAND_ORANGE);
        updateStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        updates.add(updateStatus);
        updates.add(Box.createVerticalStrut(6));

        JButton btnCheck = new JButton("Controleer updates");
        btnCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnCheck.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        btnInstallUpdate.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnInstallUpdate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        btnInstallUpdate.setEnabled(LoneBotUpdater.lastResult().updateAvailable);
        btnInstallUpdate.setToolTipText("Download release-zip, vervang bestanden, herstart");

        JButton btnRestart = new JButton("↻ Herstart launcher (snel, geen build)");
        btnRestart.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRestart.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btnRestart.setFont(btnRestart.getFont().deriveFont(Font.BOLD));
        btnRestart.setToolTipText("Alleen launcher herstarten — geen installDist. Nieuwe jars: Rebuild.");
        btnRestart.addActionListener(e -> requestRestart());

        JButton btnRebuild = new JButton("🔨 Rebuild (installDist + nieuwe jars)");
        btnRebuild.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRebuild.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btnRebuild.setFont(btnRebuild.getFont().deriveFont(Font.BOLD));
        btnRebuild.setToolTipText("Zelfde als Rebuild-LoneBot.bat: stopt LoneBot-clients, bouwt jars, start launcher.");
        btnRebuild.addActionListener(e -> requestRebuild());

        updates.add(btnCheck);
        updates.add(Box.createVerticalStrut(4));
        updates.add(btnInstallUpdate);
        updates.add(Box.createVerticalStrut(10));
        updates.add(btnRestart);
        updates.add(Box.createVerticalStrut(4));
        updates.add(btnRebuild);

        add(changeScroll, BorderLayout.CENTER);
        add(updates, BorderLayout.SOUTH);

        Runnable refreshUpdateUi = this::refreshUpdateUi;
        Timer sync = new Timer(1500, ev -> refreshUpdateUi.run());
        sync.setRepeats(true);
        sync.start();
        refreshUpdateUi.run();

        btnCheck.addActionListener(e -> {
            savePrefs();
            status.accept("Updates controleren…");
            btnCheck.setEnabled(false);
            Thread t = new Thread(() -> {
                LoneBotUpdater.CheckResult r = LoneBotUpdater.check(
                        tfRepo.getText().trim(), new String(tfToken.getPassword()).trim());
                SwingUtilities.invokeLater(() -> {
                    btnCheck.setEnabled(true);
                    refreshUpdateUi.run();
                    status.accept(r.message);
                });
            }, "lonebot-news-update-check");
            t.setDaemon(true);
            t.start();
        });

        btnInstallUpdate.addActionListener(e -> {
            LoneBotUpdater.CheckResult r = LoneBotUpdater.lastResult();
            if (!r.updateAvailable) {
                status.accept("Geen update beschikbaar");
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this,
                    "Update naar v" + r.remoteVersion + "?\nLauncher herstart automatisch.",
                    "LoneBot update", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
            savePrefs();
            status.accept("Update downloaden…");
            btnInstallUpdate.setEnabled(false);
            String repo = tfRepo.getText().trim();
            String token = new String(tfToken.getPassword()).trim();
            Thread t = new Thread(() -> {
                String msg = LoneBotUpdater.installAndRestart(repo, token, r);
                SwingUtilities.invokeLater(() -> {
                    btnInstallUpdate.setEnabled(true);
                    status.accept(msg);
                });
            }, "lonebot-news-update-install");
            t.setDaemon(true);
            t.start();
        });

        tfRepo.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                savePrefs();
            }
        });
        tfToken.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                savePrefs();
            }
        });
        cbUpdateStart.addActionListener(e -> savePrefs());
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (LauncherGlass.isOn()) {
            LauncherGlass.paintTint(g, getWidth(), getHeight());
        }
        super.paintComponent(g);
    }

    public boolean checkUpdatesOnStartup() {
        return cbUpdateStart.isSelected();
    }

    /** Header-knop: zelfde check als in dit panel. */
    public void triggerUpdateCheck() {
        savePrefs();
        status.accept("Updates controleren…");
        Thread t = new Thread(() -> {
            LoneBotUpdater.CheckResult r = LoneBotUpdater.check(
                    tfRepo.getText().trim(), new String(tfToken.getPassword()).trim());
            SwingUtilities.invokeLater(() -> {
                refreshUpdateUi();
                status.accept(r.message);
                if (r.updateAvailable) {
                    int ok = JOptionPane.showConfirmDialog(this,
                            "Update v" + r.remoteVersion + " beschikbaar.\nNu installeren + herstarten?",
                            "LoneBot update", JOptionPane.YES_NO_OPTION);
                    if (ok == JOptionPane.YES_OPTION) {
                        btnInstallUpdate.doClick();
                    }
                } else if (!r.error) {
                    JOptionPane.showMessageDialog(this, r.message, "Updates", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, r.message, "Updates", JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "lonebot-news-header-check");
        t.setDaemon(true);
        t.start();
    }

    public void triggerRestart() {
        requestRestart();
    }

    public void triggerRebuild() {
        requestRebuild();
    }

    public void runStartupUpdateCheckIfEnabled() {
        if (!cbUpdateStart.isSelected()) {
            return;
        }
        String repo = tfRepo.getText().trim();
        String token = new String(tfToken.getPassword()).trim();
        if (repo.isEmpty() || token.isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> {
            LoneBotUpdater.CheckResult r = LoneBotUpdater.check(repo, token);
            SwingUtilities.invokeLater(() -> {
                refreshUpdateUi();
                status.accept(r.message);
            });
        }, "lonebot-news-startup-check");
        t.setDaemon(true);
        t.start();
    }

    private void requestRestart() {
        int ok = JOptionPane.showConfirmDialog(this,
                "Launcher opnieuw starten?\n\n"
                        + "• Geen Gradle / installDist (blijft snel)\n"
                        + "• Game-clients blijven open\n"
                        + "• Nieuwe client-jars: knop 🔨 Rebuild\n"
                        + "• Scripts: ↻ Scripts / ↺ Fish (hot-reload)",
                "LoneBot herstart",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        savePrefs();
        status.accept("Herstart over ±1s…");
        try {
            com.lonebot.launcher.LauncherRestartHelper.restartAfterDelay(900);
        } catch (Throwable t) {
            status.accept("Herstart mislukt: " + t.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Herstart mislukt:\n" + t + "\n\nSluit de launcher en start Start-LoneBot.bat opnieuw.",
                    "Herstart", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void requestRebuild() {
        int ok = JOptionPane.showConfirmDialog(this,
                "Rebuild = nieuwe client-jars (installDist)?\n\n"
                        + "• Stopt LoneBot launcher + game-clients\n"
                        + "• Andere Java-spellen blijven open\n"
                        + "• Duurt ~1–3 min (Gradle)\n"
                        + "• Daarna start de launcher opnieuw\n\n"
                        + "Scripts-only? Gebruik ↻ Scripts i.p.v. Rebuild.",
                "LoneBot Rebuild",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        savePrefs();
        status.accept("Rebuild start over ±1s…");
        try {
            com.lonebot.launcher.LauncherRestartHelper.rebuildAfterDelay(900);
        } catch (Throwable t) {
            status.accept("Rebuild mislukt: " + t.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Rebuild mislukt:\n" + t + "\n\nOf: dubbelklik Rebuild-LoneBot.bat",
                    "Rebuild", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshUpdateUi() {
        LoneBotUpdater.CheckResult r = LoneBotUpdater.lastResult();
        updateStatus.setText("<html>" + escapeHtml(r.message) + "</html>");
        if (r.error) {
            updateStatus.setForeground(new Color(255, 120, 120));
        } else if (r.updateAvailable) {
            updateStatus.setForeground(new Color(80, 220, 120));
        } else {
            updateStatus.setForeground(ColorScheme.BRAND_ORANGE);
        }
        btnInstallUpdate.setEnabled(r.updateAvailable);
    }

    private static void addLabeled(JPanel parent, String label, JTextField field) {
        JLabel l = new JLabel(label);
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(l);
        parent.add(field);
        parent.add(Box.createVerticalStrut(4));
    }

    private static void styleCheck(JCheckBox cb) {
        LoneBotUiTheme.styleCheck(cb);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static File prefsFile() {
        return new File(System.getProperty("user.home") + File.separator + ".lonebot",
                "update.properties");
    }

    private void loadPrefs() {
        Properties p = new Properties();
        File f = prefsFile();
        if (f.isFile()) {
            try (FileInputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (Exception ignored) {
            }
        }
        String repo = p.getProperty("githubRepo", LoneBotUpdater.DEFAULT_REPO);
        if (repo == null || repo.isBlank()) {
            repo = LoneBotUpdater.DEFAULT_REPO;
        }
        tfRepo.setText(repo);
        tfToken.setText(p.getProperty("githubToken", ""));
        cbUpdateStart.setSelected(Boolean.parseBoolean(p.getProperty("checkOnStartup", "false")));
    }

    private void savePrefs() {
        Properties p = new Properties();
        p.setProperty("githubRepo", tfRepo.getText().trim());
        p.setProperty("githubToken", new String(tfToken.getPassword()).trim());
        p.setProperty("checkOnStartup", Boolean.toString(cbUpdateStart.isSelected()));
        File f = prefsFile();
        try {
            File parent = f.getParentFile();
            if (parent != null) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                p.store(out, "LoneBot update settings");
            }
        } catch (Exception ignored) {
        }
    }
}
