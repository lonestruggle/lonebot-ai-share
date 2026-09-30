package net.runelite.client.plugins.lonebot;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JScrollPane;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * RuneLite/DZ config-look: oranje secties, label links, control rechts.
 */
final class LoneBotDzLook {

    static final Color BG = ColorScheme.DARKER_GRAY_COLOR;
    static final Color BG_PANEL = ColorScheme.DARK_GRAY_COLOR;
    /** Iets lichter dan de sidebar — zelfde als RuneLite/DZ invoervelden. */
    static final Color BG_INPUT = new Color(40, 40, 40);
    static final Color INPUT_BORDER = new Color(62, 62, 62);
    static final Color ORANGE = ColorScheme.BRAND_ORANGE;
    static final Color TEXT = Color.WHITE;
    static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
    static final Font FONT_TITLE = new Font(Font.DIALOG, Font.BOLD, 14);
    static final Font FONT_SECTION = new Font(Font.DIALOG, Font.BOLD, 13);
    static final Font FONT_ITEM = new Font(Font.DIALOG, Font.PLAIN, 11);
    private static final int ROW_H = 26;
    private static final int COMBO_H = 22;
    private static final int COMBO_POPUP_MAX_W = 420;

    private LoneBotDzLook() {
    }

    /** Kolom die de sidebar-breedte volgt — controls blijven in beeld, geen horizontale overflow. */
    static final class TrackWidthColumn extends JPanel implements Scrollable {
        TrackWidthColumn() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(true);
            setBackground(BG);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    static JPanel itemRow(String name, JComponent control) {
        if (control instanceof JComboBox) {
            return itemRowCombo(name, (JComboBox<?>) control);
        }
        int slot = controlSlot(control);
        fitControl(control, slot);
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(true);
        row.setBackground(BG);
        row.setBorder(new EmptyBorder(1, 6, 1, 6));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_H));
        row.setPreferredSize(new Dimension(10, ROW_H));
        JLabel lbl = new JLabel(name);
        lbl.setForeground(TEXT);
        lbl.setFont(FONT_ITEM);
        lbl.setToolTipText(name);
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.setPreferredSize(new Dimension(slot, COMBO_H));
        east.setMinimumSize(new Dimension(slot, COMBO_H));
        east.setMaximumSize(new Dimension(slot, COMBO_H));
        east.add(control, BorderLayout.CENTER);
        row.add(lbl, BorderLayout.CENTER);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    /**
     * Combo's full-width onder het label — lange namen (Draynor-Lumbridge…) blijven leesbaar.
     */
    static JPanel itemRowCombo(String name, JComboBox<?> control) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(true);
        row.setBackground(BG);
        row.setBorder(new EmptyBorder(2, 6, 4, 6));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JLabel lbl = new JLabel(name);
        lbl.setForeground(MUTED);
        lbl.setFont(FONT_ITEM);
        lbl.setToolTipText(name);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, COMBO_H));
        control.setPreferredSize(new Dimension(10, COMBO_H));
        control.setMinimumSize(new Dimension(60, COMBO_H));
        if (control.getToolTipText() == null || control.getToolTipText().isBlank()) {
            Object sel = control.getSelectedItem();
            if (sel != null) {
                control.setToolTipText(sel.toString());
            }
        }
        control.addActionListener(e -> {
            Object sel = control.getSelectedItem();
            control.setToolTipText(sel != null ? sel.toString() : name);
        });
        row.add(lbl);
        row.add(control);
        return row;
    }

    static JPanel itemRowFill(String name, JComponent control) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(true);
        row.setBackground(BG);
        row.setBorder(new EmptyBorder(2, 6, 6, 6));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        JLabel lbl = new JLabel(name);
        lbl.setForeground(TEXT);
        lbl.setFont(FONT_ITEM);
        lbl.setToolTipText(name);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(lbl);
        row.add(control);
        return row;
    }

    private static int controlSlot(JComponent control) {
        if (control instanceof JCheckBox) {
            return 22;
        }
        if (control instanceof JSpinner) {
            return 76;
        }
        if (control instanceof JComboBox) {
            return 140;
        }
        return 72;
    }

    private static void fitControl(JComponent control, int slot) {
        Dimension d = new Dimension(slot, COMBO_H);
        control.setPreferredSize(d);
        control.setMinimumSize(d);
        control.setMaximumSize(d);
    }

    static JCheckBox box(boolean on) {
        JCheckBox cb = new JCheckBox();
        cb.setSelected(on);
        cb.setOpaque(false);
        cb.setForeground(TEXT);
        cb.setBackground(BG);
        cb.setIcon(dzCheck(false));
        cb.setSelectedIcon(dzCheck(true));
        cb.setDisabledIcon(dzCheck(false));
        cb.setDisabledSelectedIcon(dzCheck(true));
        cb.setPressedIcon(dzCheck(true));
        cb.setRolloverIcon(dzCheck(false));
        cb.setRolloverSelectedIcon(dzCheck(true));
        cb.setFocusPainted(false);
        return cb;
    }

    private static Icon dzCheck(boolean selected) {
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
                g2.setColor(BG_INPUT);
                g2.fillRect(x, y + 1, 14, 14);
                g2.setColor(INPUT_BORDER);
                g2.drawRect(x, y + 1, 14, 14);
                if (selected) {
                    g2.setColor(MUTED);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 3, y + 8, x + 6, y + 12);
                    g2.drawLine(x + 6, y + 12, x + 11, y + 4);
                }
                g2.dispose();
            }
        };
    }

    static JComboBox<String> combo(String... items) {
        JComboBox<String> c = new JComboBox<>(items);
        styleCombo(c);
        return c;
    }

    static <T> JComboBox<T> comboOf(T[] items, T selected) {
        JComboBox<T> c = new JComboBox<>(items);
        styleCombo(c);
        if (selected != null) {
            c.setSelectedItem(selected);
        }
        return c;
    }

    private static void styleCombo(JComboBox<?> c) {
        c.setBackground(BG_INPUT);
        c.setForeground(TEXT);
        c.setFont(FONT_ITEM);
        c.setOpaque(true);
        c.setBorder(inputBorder());
        c.setMaximumRowCount(14);
        try {
            c.setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton b = new BasicArrowButton(
                            SwingConstants.SOUTH, BG_INPUT, INPUT_BORDER, MUTED, BG_INPUT);
                    b.setBorder(BorderFactory.createEmptyBorder());
                    b.setOpaque(true);
                    return b;
                }
            });
        } catch (Throwable ignored) {
        }
        c.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(FONT_ITEM);
                setBorder(new EmptyBorder(2, 6, 2, 6));
                setToolTipText(value != null ? value.toString() : null);
                if (isSelected) {
                    setBackground(new Color(55, 55, 55));
                    setForeground(TEXT);
                } else {
                    setBackground(BG_INPUT);
                    setForeground(TEXT);
                }
                return this;
            }
        });
        widenComboPopup(c);
    }

    /** Open lijst zo breed als de langste optie (max {@link #COMBO_POPUP_MAX_W}). */
    private static void widenComboPopup(JComboBox<?> combo) {
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                try {
                    Object acc = combo.getUI().getAccessibleChild(combo, 0);
                    if (!(acc instanceof BasicComboPopup)) {
                        return;
                    }
                    BasicComboPopup popup = (BasicComboPopup) acc;
                    JList<?> list = popup.getList();
                    int widest = Math.max(combo.getWidth(), 120);
                    FontMetrics fm = list.getFontMetrics(list.getFont());
                    for (int i = 0; i < combo.getItemCount(); i++) {
                        Object it = combo.getItemAt(i);
                        if (it != null) {
                            widest = Math.max(widest, fm.stringWidth(it.toString()) + 36);
                        }
                    }
                    widest = Math.min(widest, COMBO_POPUP_MAX_W);
                    list.setFixedCellWidth(widest);
                    Component parent = list.getParent();
                    if (parent != null && parent.getParent() instanceof JScrollPane) {
                        JScrollPane scroll = (JScrollPane) parent.getParent();
                        Dimension sd = scroll.getPreferredSize();
                        sd.width = widest;
                        scroll.setPreferredSize(sd);
                        scroll.setMaximumSize(new Dimension(widest, sd.height));
                    }
                    Dimension pd = popup.getPreferredSize();
                    pd.width = widest;
                    popup.setPreferredSize(pd);
                    popup.setSize(widest, pd.height);
                    popup.revalidate();
                } catch (Throwable ignored) {
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }

    static JSpinner spinner(int value, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        s.setFont(FONT_ITEM);
        s.setOpaque(true);
        s.setBackground(BG_INPUT);
        s.setForeground(TEXT);
        s.setBorder(inputBorder());
        s.setPreferredSize(new Dimension(76, COMBO_H));
        JComponent editor = s.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setOpaque(true);
            tf.setBackground(BG_INPUT);
            tf.setForeground(TEXT);
            tf.setCaretColor(TEXT);
            tf.setDisabledTextColor(MUTED);
            tf.setFont(FONT_ITEM);
            tf.setBorder(new EmptyBorder(1, 4, 1, 2));
            tf.setHorizontalAlignment(SwingConstants.LEFT);
        }
        editor.setOpaque(true);
        editor.setBackground(BG_INPUT);
        for (Component child : s.getComponents()) {
            child.setBackground(BG_INPUT);
            if (child instanceof JButton) {
                JButton b = (JButton) child;
                b.setOpaque(true);
                b.setBackground(BG_INPUT);
                b.setForeground(MUTED);
                b.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, INPUT_BORDER));
            }
        }
        return s;
    }

    private static javax.swing.border.Border inputBorder() {
        return BorderFactory.createLineBorder(INPUT_BORDER);
    }

    static JTextArea growingList(String value) {
        GrowingListArea a = new GrowingListArea(value != null ? value : "");
        a.setFont(FONT_ITEM);
        a.setBackground(BG_INPUT);
        a.setForeground(TEXT);
        a.setCaretColor(TEXT);
        a.setLineWrap(true);
        a.setWrapStyleWord(false);
        a.setOpaque(true);
        a.setBorder(BorderFactory.createCompoundBorder(
                inputBorder(),
                new EmptyBorder(4, 6, 4, 6)));
        a.getDocument().addDocumentListener(new DocumentListener() {
            private void relayout() {
                Container p = a.getParent();
                while (p != null) {
                    p.invalidate();
                    p.validate();
                    p = p.getParent();
                }
                a.revalidate();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                relayout();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                relayout();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                relayout();
            }
        });
        return a;
    }

    static JTextField textField(String value) {
        JTextField f = new JTextField(value != null ? value : "");
        f.setFont(FONT_ITEM);
        f.setBackground(BG_INPUT);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                inputBorder(),
                new EmptyBorder(2, 6, 2, 6)));
        return f;
    }

    /**
     * Lijst-veld zoals DZ: wrapt op de paneelbreedte en groeit in de hoogte.
     */
    static final class GrowingListArea extends JTextArea {
        private static final int MIN_H = 26;
        private static final int MAX_H = 180;

        GrowingListArea(String text) {
            super(text);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getWidth();
            if (w <= 0 && getParent() != null) {
                w = getParent().getWidth();
            }
            if (w <= 0) {
                w = 200;
            }
            setSize(w, Short.MAX_VALUE);
            Dimension d = super.getPreferredSize();
            int h = Math.max(MIN_H, Math.min(MAX_H, d.height));
            return new Dimension(w, h);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension p = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, p.height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(40, MIN_H);
        }
    }

    static JButton actionBtn(String text, Color bg) {
        JButton b = new JButton(text);
        Color fill = bg != null ? bg : new Color(55, 70, 100);
        b.setUI(new BasicButtonUI());
        b.setFont(FONT_ITEM.deriveFont(Font.BOLD, 11f));
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
                new EmptyBorder(6, 4, 6, 4)));
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

    static Section section(String title, boolean startOpen) {
        return new Section(title, startOpen);
    }

    static JButton resetButton() {
        JButton b = new JButton("Reset");
        b.setUI(new BasicButtonUI());
        b.setFont(FONT_ITEM.deriveFont(Font.BOLD, 13f));
        b.setForeground(TEXT);
        b.setBackground(BG_INPUT);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                inputBorder(),
                new EmptyBorder(8, 8, 8, 8)));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return b;
    }

    static final class SwitchToggle extends JToggleButton {
        SwitchToggle() {
            setPreferredSize(new Dimension(38, 18));
            setMaximumSize(new Dimension(38, 18));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean on = isSelected();
            g2.setColor(on ? new Color(46, 160, 67) : new Color(72, 72, 72));
            g2.fillRoundRect(0, 1, w - 1, h - 3, h - 3, h - 3);
            int knob = h - 6;
            int x = on ? w - knob - 3 : 2;
            g2.setColor(Color.WHITE);
            g2.fillOval(x, 2, knob, knob);
            g2.dispose();
        }
    }

    static final class Section {
        final JPanel root = new JPanel();
        final JPanel body = new JPanel();
        final JButton header;
        private boolean open;

        Section(String title, boolean startOpen) {
            this.open = startOpen;
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setOpaque(true);
            root.setBackground(BG);
            root.setAlignmentX(Component.LEFT_ALIGNMENT);
            root.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            header = new JButton((startOpen ? "▼  " : "▶  ") + title);
            header.setFont(FONT_SECTION);
            header.setForeground(ORANGE);
            header.setBackground(BG);
            header.setOpaque(true);
            header.setBorderPainted(false);
            header.setFocusPainted(false);
            header.setContentAreaFilled(false);
            header.setHorizontalAlignment(SwingConstants.LEFT);
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            header.setBorder(new EmptyBorder(8, 8, 6, 8));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setOpaque(true);
            body.setBackground(BG);
            body.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.setVisible(startOpen);
            body.setBorder(new EmptyBorder(0, 0, 6, 0));

            header.addActionListener(e -> setOpen(!open));
            root.add(header);
            root.add(body);
        }

        void addItem(JComponent row) {
            body.add(row);
        }

        void setOpen(boolean want) {
            open = want;
            body.setVisible(want);
            String t = header.getText();
            int sp = t.indexOf("  ");
            String title = sp >= 0 ? t.substring(sp + 2) : t;
            header.setText((want ? "▼  " : "▶  ") + title);
            root.revalidate();
        }
    }
}
