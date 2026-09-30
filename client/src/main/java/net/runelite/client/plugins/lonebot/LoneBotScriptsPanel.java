package net.runelite.client.plugins.lonebot;

import net.runelite.client.ui.ColorScheme;
import com.lonebot.launcher.LauncherGlass;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Launcher-tab: alle scripts, filter Werkend/Beta/Test, versie + update (Gradle in launcher, niet in de game-client).
 */
public final class LoneBotScriptsPanel extends JPanel {

    private static final Color BG = new Color(40, 42, 52);
    private static final Color CARD = new Color(34, 36, 46);
    private static final Color LINE = new Color(55, 60, 78);

    private final Consumer<String> status;
    private final JPanel list = new JPanel();
    private final JTextArea log = new JTextArea(6, 40);
    private final JLabel filterHint = new JLabel(" ");
    private LoneBotScriptCatalog.Maturity filter;
    private volatile boolean busy;

    public LoneBotScriptsPanel(Consumer<String> status) {
        this.status = status != null ? status : s -> {
        };
        setLayout(new BorderLayout(0, 8));
        setOpaque(true);
        setBackground(BG);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Scripts");
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        JLabel sub = new JLabel("v in de lijst = deze launcher. In-game = jar onder ~/.lonebot/scripts. Update kopieert client-lib (geen Gradle nodig).");
        sub.setForeground(new Color(180, 185, 200));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(title, BorderLayout.NORTH);
        head.add(sub, BorderLayout.SOUTH);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        ButtonGroup g = new ButtonGroup();
        JRadioButton all = radio("Alles", true);
        JRadioButton w = radio("Werkend", false);
        JRadioButton b = radio("Beta", false);
        JRadioButton t = radio("Test", false);
        g.add(all);
        g.add(w);
        g.add(b);
        g.add(t);
        all.addActionListener(e -> setFilter(null));
        w.addActionListener(e -> setFilter(LoneBotScriptCatalog.Maturity.WERKEND));
        b.addActionListener(e -> setFilter(LoneBotScriptCatalog.Maturity.BETA));
        t.addActionListener(e -> setFilter(LoneBotScriptCatalog.Maturity.TEST));
        filters.add(all);
        filters.add(w);
        filters.add(b);
        filters.add(t);

        JButton allVis = headerBtn("↻ Update zichtbare", new Color(140, 95, 45));
        allVis.addActionListener(e -> updateVisible());
        JButton refresh = headerBtn("Ververs", new Color(55, 90, 140));
        refresh.addActionListener(e -> rebuildList());
        filters.add(Box.createHorizontalStrut(12));
        filters.add(allVis);
        filters.add(refresh);

        filterHint.setForeground(new Color(160, 165, 180));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(head);
        north.add(Box.createVerticalStrut(8));
        north.add(filters);
        north.add(filterHint);
        add(north, BorderLayout.NORTH);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(true);
        list.setBackground(BG);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        scroll.getViewport().setBackground(BG);
        add(scroll, BorderLayout.CENTER);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setBackground(CARD);
        log.setForeground(new Color(210, 214, 224));
        log.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(LINE), "Log"));
        logScroll.setPreferredSize(new Dimension(400, 120));
        add(logScroll, BorderLayout.SOUTH);

        rebuildList();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (LauncherGlass.isOn()) {
            LauncherGlass.paintTint(g, getWidth(), getHeight());
        }
        super.paintComponent(g);
    }

    private void setFilter(LoneBotScriptCatalog.Maturity m) {
        this.filter = m;
        rebuildList();
    }

    private void rebuildList() {
        list.removeAll();
        List<LoneBotScriptCatalog.Entry> rows = LoneBotScriptCatalog.all().stream()
                .filter(e -> filter == null || e.maturity == filter)
                .collect(Collectors.toList());
        filterHint.setText(rows.size() + " script(s)"
                + (filter != null ? " — filter " + filter.label : ""));
        for (LoneBotScriptCatalog.Entry e : rows) {
            list.add(card(e));
            list.add(Box.createVerticalStrut(6));
        }
        list.add(Box.createVerticalGlue());
        if (LauncherGlass.isOn()) {
            LauncherGlass.apply(this, true);
        }
        list.revalidate();
        list.repaint();
    }

    private JPanel card(LoneBotScriptCatalog.Entry e) {
        JPanel p = new JPanel(new BorderLayout(10, 4));
        p.setOpaque(true);
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(8, 10, 8, 10)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));
        p.setAlignmentX(LEFT_ALIGNMENT);

        JLabel name = new JLabel(e.name);
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        JLabel meta = new JLabel("Catalogus v" + e.version + "  ·  " + e.maturity.label + "  ·  " + e.note);
        meta.setForeground(new Color(170, 175, 190));
        String change = LoneBotScriptChangelog.latestSummary(e.id);
        if (change != null && change.length() > 78) {
            change = change.substring(0, 77) + "…";
        }
        JLabel lastChange = new JLabel(change != null ? change : " ");
        lastChange.setForeground(new Color(150, 158, 175));
        lastChange.setFont(lastChange.getFont().deriveFont(11f));
        File staged = ScriptReloadHelper.newestStaged(e.stagedJar);
        String stagedTxt;
        if (staged != null && staged.isFile()) {
            stagedTxt = "In-game jar: " + staged.getName() + "  "
                    + new SimpleDateFormat("dd-MM HH:mm").format(new Date(staged.lastModified()))
                    + "  (niet catalogus-v tot Update)";
        } else {
            stagedTxt = "In-game jar: nog niet gestaged — client lib na start/Update";
        }
        JLabel jar = new JLabel("Jar: " + stagedTxt);
        jar.setForeground(new Color(140, 150, 170));
        jar.setFont(jar.getFont().deriveFont(11f));

        JPanel text = new JPanel(new GridLayout(4, 1, 0, 2));
        text.setOpaque(false);
        text.add(name);
        text.add(meta);
        text.add(lastChange);
        text.add(jar);

        JButton upd = headerBtn("↻ Update", new Color(28, 125, 85));
        upd.setToolTipText("Zonder broncode: lib-jar → ~/.lonebot/scripts + herlaad clients. Met Gradle: " + e.gradleTask);
        upd.addActionListener(ev -> updateOne(e));

        p.add(text, BorderLayout.CENTER);
        p.add(upd, BorderLayout.EAST);
        return p;
    }

    private void updateOne(LoneBotScriptCatalog.Entry e) {
        if (busy) {
            appendLog("Nog bezig…");
            return;
        }
        busy = true;
        appendLog("Update " + e.name + "…");
        status.accept("Script update: " + e.name);
        Thread t = new Thread(() -> {
            try {
                ScriptReloadHelper.buildOneAndNotifyClients(e, this::appendLogUi);
            } finally {
                busy = false;
                SwingUtilities.invokeLater(this::rebuildList);
            }
        }, "lonebot-script-update-" + e.id);
        t.setDaemon(true);
        t.start();
    }

    private void updateVisible() {
        if (busy) {
            appendLog("Nog bezig…");
            return;
        }
        List<LoneBotScriptCatalog.Entry> rows = LoneBotScriptCatalog.all().stream()
                .filter(e -> filter == null || e.maturity == filter)
                .collect(Collectors.toList());
        if (rows.isEmpty()) {
            appendLog("Niets zichtbaar");
            return;
        }
        busy = true;
        appendLog("Update " + rows.size() + " zichtbare script(s)…");
        Thread t = new Thread(() -> {
            try {
                if (filter == null) {
                    ScriptReloadHelper.buildAllScriptsAndNotifyClients(this::appendLogUi);
                } else {
                    java.util.Set<String> done = new java.util.HashSet<>();
                    for (LoneBotScriptCatalog.Entry e : rows) {
                        if (!done.add(e.gradleTask)) {
                            continue;
                        }
                        ScriptReloadHelper.buildOneAndNotifyClients(e, this::appendLogUi);
                    }
                }
            } finally {
                busy = false;
                SwingUtilities.invokeLater(this::rebuildList);
            }
        }, "lonebot-script-update-visible");
        t.setDaemon(true);
        t.start();
    }

    private void appendLogUi(String msg) {
        SwingUtilities.invokeLater(() -> {
            appendLog(msg);
            status.accept(msg);
        });
    }

    private void appendLog(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        if (log.getText().length() > 8000) {
            log.setText("");
        }
        log.append(msg.trim() + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private static JRadioButton radio(String text, boolean selected) {
        JRadioButton r = new JRadioButton(text, selected);
        r.setOpaque(false);
        r.setForeground(new Color(220, 224, 232));
        return r;
    }

    private static JButton headerBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        return b;
    }
}
