package net.runelite.client.plugins.lonebot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicComboBoxUI;

/**
 * Mid-tone GUI-thema's (niet te licht, niet te donker). Default = {@link Preset#BALANS}.
 * Headers/tabs: vaste hoog-contrast hover (geen groen-op-groen).
 */
public final class LoneBotUiTheme {

    public enum Preset {
        BALANS(
                "Balans",
                new Color(58, 64, 78),
                new Color(72, 78, 94),
                new Color(82, 90, 108),
                new Color(235, 195, 70),
                new Color(90, 200, 145),
                new Color(235, 238, 245),
                new Color(175, 185, 200),
                new Color(95, 105, 125),
                new Color(48, 54, 68),
                new Color(235, 238, 245),
                new Color(70, 125, 185),
                new Color(110, 145, 195),
                new Color(255, 255, 255)
        ),
        MIST(
                "Mist",
                new Color(78, 86, 100),
                new Color(92, 100, 116),
                new Color(102, 112, 128),
                new Color(230, 190, 65),
                new Color(85, 190, 140),
                new Color(240, 242, 248),
                new Color(190, 198, 210),
                new Color(115, 125, 142),
                new Color(68, 76, 92),
                new Color(240, 242, 248),
                new Color(75, 130, 185),
                new Color(120, 155, 200),
                new Color(255, 255, 255)
        ),
        GRAPHITE(
                "Grafiet",
                new Color(45, 50, 62),
                new Color(56, 62, 76),
                new Color(66, 74, 90),
                new Color(240, 200, 75),
                new Color(95, 205, 150),
                new Color(230, 234, 242),
                new Color(165, 175, 190),
                new Color(80, 90, 108),
                new Color(38, 44, 56),
                new Color(230, 234, 242),
                new Color(65, 115, 175),
                new Color(100, 140, 190),
                new Color(255, 255, 255)
        ),
        COMBAT(
                "CombatBot",
                new Color(40, 42, 54),
                new Color(50, 54, 68),
                new Color(58, 64, 80),
                new Color(255, 215, 0),
                new Color(74, 210, 130),
                new Color(225, 228, 235),
                new Color(165, 170, 185),
                new Color(70, 76, 92),
                new Color(35, 38, 50),
                new Color(235, 238, 245),
                new Color(55, 105, 165),
                new Color(95, 140, 195),
                new Color(255, 255, 255)
        ),
        FOREST(
                "Bos",
                new Color(52, 68, 58),
                new Color(64, 82, 70),
                new Color(74, 94, 80),
                new Color(220, 185, 70),
                new Color(80, 185, 120),
                new Color(232, 240, 234),
                new Color(170, 190, 175),
                new Color(85, 110, 95),
                new Color(42, 56, 48),
                new Color(232, 240, 234),
                new Color(55, 130, 95),
                new Color(90, 160, 120),
                new Color(255, 255, 255)
        ),
        OCEAN(
                "Oceaan",
                new Color(48, 62, 78),
                new Color(60, 76, 94),
                new Color(70, 88, 108),
                new Color(225, 185, 70),
                new Color(70, 185, 175),
                new Color(230, 238, 245),
                new Color(165, 185, 205),
                new Color(85, 105, 128),
                new Color(40, 52, 68),
                new Color(230, 238, 245),
                new Color(55, 120, 170),
                new Color(95, 150, 195),
                new Color(255, 255, 255)
        ),
        WARM(
                "Warm",
                new Color(62, 56, 52),
                new Color(76, 70, 64),
                new Color(88, 80, 72),
                new Color(235, 185, 70),
                new Color(120, 185, 120),
                new Color(240, 235, 228),
                new Color(185, 175, 165),
                new Color(110, 100, 90),
                new Color(52, 46, 42),
                new Color(240, 235, 228),
                new Color(150, 110, 70),
                new Color(175, 140, 95),
                new Color(255, 255, 255)
        );

        /** Alleen deze in de GUI-combo (geen legacy-duplicaten). */
        public static Preset[] selectable() {
            return new Preset[]{BALANS, MIST, GRAPHITE, COMBAT, FOREST, OCEAN, WARM};
        }

        private final String displayName;
        final Color bgDark;
        final Color bgSection;
        final Color bgHeader;
        final Color gold;
        final Color green;
        final Color text;
        final Color textDim;
        final Color border;
        final Color inputBg;
        final Color inputFg;
        final Color tabSelected;
        final Color hoverBg;
        final Color hoverFg;

        Preset(String displayName, Color bgDark, Color bgSection, Color bgHeader,
               Color gold, Color green, Color text, Color textDim, Color border,
               Color inputBg, Color inputFg, Color tabSelected,
               Color hoverBg, Color hoverFg) {
            this.displayName = displayName;
            this.bgDark = bgDark;
            this.bgSection = bgSection;
            this.bgHeader = bgHeader;
            this.gold = gold;
            this.green = green;
            this.text = text;
            this.textDim = textDim;
            this.border = border;
            this.inputBg = inputBg;
            this.inputFg = inputFg;
            this.tabSelected = tabSelected;
            this.hoverBg = hoverBg;
            this.hoverFg = hoverFg;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final String ROLE = "lonebot.themeRole";
    public static final String ROLE_ROOT = "root";
    public static final String ROLE_COLUMN = "column";
    public static final String ROLE_SECTION_OUTER = "sectionOuter";
    public static final String ROLE_SECTION_CONTENT = "sectionContent";
    public static final String ROLE_HEADER = "header";
    public static final String ROLE_HEADER_ACCENT = "headerAccent";
    public static final String ROLE_ROW = "row";
    public static final String ROLE_INPUT = "input";
    public static final String ROLE_SCROLL = "scroll";

    static final Font FONT_SECTION = new Font("SansSerif", Font.BOLD, 12);
    static final Font FONT_LABEL = new Font("SansSerif", Font.PLAIN, 11);
    static final Font FONT_VALUE = new Font("SansSerif", Font.BOLD, 11);

    private static volatile Preset active = Preset.BALANS;

    static Color BG_DARK = Preset.BALANS.bgDark;
    static Color BG_SECTION = Preset.BALANS.bgSection;
    static Color BG_HEADER = Preset.BALANS.bgHeader;
    static Color GOLD = Preset.BALANS.gold;
    static Color GREEN = Preset.BALANS.green;
    static Color TEXT = Preset.BALANS.text;
    static Color TEXT_DIM = Preset.BALANS.textDim;
    static Color BORDER = Preset.BALANS.border;
    static Color INPUT_BG = Preset.BALANS.inputBg;
    static Color INPUT_FG = Preset.BALANS.inputFg;
    static Color TAB_SELECTED = Preset.BALANS.tabSelected;
    static Color HOVER_BG = Preset.BALANS.hoverBg;
    static Color HOVER_FG = Preset.BALANS.hoverFg;

    private LoneBotUiTheme() {
    }

    public static Preset active() {
        return active;
    }

    /** Oude config-namen → mid-tone preset. */
    public static Preset resolve(Preset raw) {
        if (raw == null) {
            return Preset.BALANS;
        }
        String n = raw.name();
        if ("LIGHT".equals(n) || "SOFT".equals(n)) {
            return Preset.MIST;
        }
        if ("SLATE".equals(n)) {
            return Preset.BALANS;
        }
        if ("MIDNIGHT".equals(n)) {
            return Preset.GRAPHITE;
        }
        return raw;
    }

    /** Parse config string veilig (legacy LIGHT/SOFT/…). */
    public static Preset fromConfig(String name) {
        if (name == null || name.isBlank()) {
            return Preset.BALANS;
        }
        try {
            return resolve(Preset.valueOf(name.trim().toUpperCase()));
        } catch (Exception e) {
            return Preset.BALANS;
        }
    }

    public static void apply(Preset preset) {
        Preset p = resolve(preset);
        active = p;
        BG_DARK = p.bgDark;
        BG_SECTION = p.bgSection;
        BG_HEADER = p.bgHeader;
        GOLD = p.gold;
        GREEN = p.green;
        TEXT = p.text;
        TEXT_DIM = p.textDim;
        BORDER = p.border;
        INPUT_BG = p.inputBg;
        INPUT_FG = p.inputFg;
        TAB_SELECTED = p.tabSelected;
        HOVER_BG = p.hoverBg;
        HOVER_FG = p.hoverFg;
    }

    public static void mark(JComponent c, String role) {
        if (c != null && role != null) {
            c.putClientProperty(ROLE, role);
        }
    }

    /** Strip leading ▶/▼ zodat createCollapsibleSection geen dubbele pijl zet. */
    public static String cleanSectionTitle(String title) {
        String t = title == null ? "" : title.trim();
        while (t.startsWith("▶") || t.startsWith("▼") || t.startsWith(">")) {
            t = t.substring(1).trim();
        }
        return t;
    }

    /**
     * Accordion-header: neutraal BG + goud/groen tekst; hover = hoog contrast (wit op blauw).
     */
    public static void wireHeaderButton(AbstractButton btn, boolean accent) {
        if (btn == null) {
            return;
        }
        btn.setRolloverEnabled(true);
        applyHeaderIdle(btn, accent);
        ChangeListener cl = e -> {
            ButtonModel m = btn.getModel();
            if (m.isRollover() || m.isArmed() || m.isPressed()) {
                btn.setBackground(HOVER_BG);
                btn.setForeground(HOVER_FG);
            } else {
                applyHeaderIdle(btn, accent);
            }
        };
        // vermijd dubbele listeners bij retheme
        Object prev = btn.getClientProperty("lonebot.headerCl");
        if (prev instanceof ChangeListener) {
            btn.removeChangeListener((ChangeListener) prev);
        }
        btn.addChangeListener(cl);
        btn.putClientProperty("lonebot.headerCl", cl);
        btn.putClientProperty("lonebot.headerAccent", accent);
    }

    static void applyHeaderIdle(AbstractButton btn, boolean accent) {
        btn.setBackground(BG_HEADER);
        btn.setForeground(accent ? GREEN : GOLD);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
    }

    public static void rethemeTree(Component root) {
        if (root == null) {
            return;
        }
        rethemeOne(root);
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                rethemeTree(child);
            }
        }
        if (root instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) root;
            if (sp.getViewport() != null && sp.getViewport().getView() != null) {
                rethemeTree(sp.getViewport().getView());
            }
            sp.getViewport().setBackground(BG_DARK);
            sp.setBackground(BG_DARK);
        }
    }

    private static void rethemeOne(Component c) {
        Object role = c instanceof JComponent ? ((JComponent) c).getClientProperty(ROLE) : null;
        if (ROLE_ROOT.equals(role) || ROLE_COLUMN.equals(role) || ROLE_SCROLL.equals(role)) {
            c.setBackground(BG_DARK);
            if (c instanceof JComponent) {
                ((JComponent) c).setOpaque(true);
            }
        } else if (ROLE_SECTION_OUTER.equals(role)) {
            c.setBackground(BG_DARK);
            if (c instanceof JComponent) {
                ((JComponent) c).setBorder(new LineBorder(BORDER, 1, true));
            }
        } else if (ROLE_SECTION_CONTENT.equals(role) || ROLE_ROW.equals(role)) {
            c.setBackground(BG_SECTION);
            if (c instanceof JComponent) {
                ((JComponent) c).setOpaque(true);
            }
        } else if (ROLE_HEADER.equals(role) || ROLE_HEADER_ACCENT.equals(role)) {
            boolean accent = ROLE_HEADER_ACCENT.equals(role)
                    || Boolean.TRUE.equals(((JComponent) c).getClientProperty("lonebot.headerAccent"));
            if (c instanceof AbstractButton) {
                wireHeaderButton((AbstractButton) c, accent);
            }
        } else if (ROLE_INPUT.equals(role)) {
            c.setBackground(INPUT_BG);
            c.setForeground(INPUT_FG);
            if (c instanceof JTextField) {
                ((JTextField) c).setCaretColor(INPUT_FG);
            }
            if (c instanceof JTextArea) {
                ((JTextArea) c).setCaretColor(INPUT_FG);
            }
        }

        if (c instanceof JCheckBox) {
            styleCheck((JCheckBox) c);
        }
        if (c instanceof JComboBox && ROLE_INPUT.equals(role)) {
            styleCombo((JComboBox<?>) c);
        }
        if (c instanceof JSlider) {
            c.setBackground(BG_SECTION);
            c.setForeground(GREEN);
        }
        if (c instanceof JTabbedPane) {
            polishTabs((JTabbedPane) c);
            installTabHover((JTabbedPane) c);
        }
    }

    static void polishTabs(JTabbedPane tabs) {
        if (tabs == null) {
            return;
        }
        tabs.setBackground(BG_DARK);
        tabs.setForeground(TEXT);
        tabs.setOpaque(true);
        try {
            // Hover/select: contrastrijk — vermijd zelfde tint als tekst
            javax.swing.UIManager.put("TabbedPane.selected", TAB_SELECTED);
            javax.swing.UIManager.put("TabbedPane.background", BG_HEADER);
            javax.swing.UIManager.put("TabbedPane.foreground", TEXT);
            javax.swing.UIManager.put("TabbedPane.contentAreaColor", BG_DARK);
            javax.swing.UIManager.put("TabbedPane.darkShadow", BORDER);
            javax.swing.UIManager.put("TabbedPane.highlight", HOVER_BG);
            javax.swing.UIManager.put("TabbedPane.light", BG_HEADER);
            javax.swing.UIManager.put("TabbedPane.focus", TAB_SELECTED);
            javax.swing.UIManager.put("TabbedPane.shadow", BORDER);
            javax.swing.UIManager.put("TabbedPane.selectHighlight", HOVER_BG);
            tabs.updateUI();
        } catch (Throwable ignored) {
        }
        refreshTabColors(tabs, -1);
        installTabHover(tabs);
    }

    /** Idle / selected / hover met leesbare fg/bg. */
    public static void refreshTabColors(JTabbedPane tabs, int hoverIndex) {
        if (tabs == null) {
            return;
        }
        int sel = tabs.getSelectedIndex();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (i == hoverIndex && i != sel) {
                tabs.setBackgroundAt(i, HOVER_BG);
                tabs.setForegroundAt(i, HOVER_FG);
            } else if (i == sel) {
                tabs.setBackgroundAt(i, TAB_SELECTED);
                tabs.setForegroundAt(i, Color.WHITE);
            } else {
                tabs.setBackgroundAt(i, BG_HEADER);
                tabs.setForegroundAt(i, TEXT);
            }
        }
    }

    private static void installTabHover(JTabbedPane tabs) {
        if (tabs == null || Boolean.TRUE.equals(tabs.getClientProperty("lonebot.tabHover"))) {
            return;
        }
        tabs.putClientProperty("lonebot.tabHover", Boolean.TRUE);
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = tabs.indexAtLocation(e.getX(), e.getY());
                refreshTabColors(tabs, idx);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                refreshTabColors(tabs, -1);
            }
        };
        tabs.addMouseMotionListener(hover);
        tabs.addMouseListener(hover);
        tabs.addChangeListener(e -> refreshTabColors(tabs, -1));
    }

    static void styleLabel(JLabel lbl) {
        if (lbl == null) {
            return;
        }
        lbl.setForeground(TEXT);
        lbl.setFont(FONT_LABEL);
    }

    /** Zichtbare dropdown-pijl (niet donker-op-donker). */
    static void styleCombo(JComboBox<?> combo) {
        if (combo == null) {
            return;
        }
        combo.setBackground(INPUT_BG);
        combo.setForeground(INPUT_FG);
        combo.setOpaque(true);
        try {
            combo.setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton b = new JButton("▼");
                    b.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
                    b.setForeground(Color.WHITE);
                    b.setBackground(HOVER_BG);
                    b.setOpaque(true);
                    b.setBorder(new LineBorder(BORDER.brighter(), 1));
                    b.setFocusPainted(false);
                    b.setContentAreaFilled(true);
                    return b;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Witte vink op donker vak — beter zichtbaar dan L&F default. */
    static void styleCheck(JCheckBox cb) {
        if (cb == null) {
            return;
        }
        cb.setOpaque(false);
        cb.setForeground(TEXT);
        cb.setIcon(checkIcon(false));
        cb.setSelectedIcon(checkIcon(true));
        cb.setDisabledIcon(checkIcon(false));
        cb.setDisabledSelectedIcon(checkIcon(true));
        cb.setPressedIcon(checkIcon(true));
        cb.setRolloverIcon(checkIcon(false));
        cb.setRolloverSelectedIcon(checkIcon(true));
    }

    private static Icon checkIcon(boolean selected) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(INPUT_BG.brighter());
                g2.fillRoundRect(x, y + 1, 14, 14, 3, 3);
                g2.setColor(new Color(210, 220, 230));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(x, y + 1, 14, 14, 3, 3);
                if (selected) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 3, y + 8, x + 6, y + 12);
                    g2.drawLine(x + 6, y + 12, x + 11, y + 4);
                }
                g2.dispose();
            }
        };
    }
}
