package net.runelite.client.plugins.lonebot;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DZ-stijl: één kolom inklapbare secties (geen tabs). Content wordt
 * in/uit het Control-venster verplaatst — één parent tegelijk.
 */
final class LoneBotAccordionNav extends JPanel {

    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final Map<String, JScrollPane> scrollHosts = new LinkedHashMap<>();

    LoneBotAccordionNav() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setBackground(LoneBotUiTheme.BG_DARK);
        LoneBotUiTheme.mark(this, LoneBotUiTheme.ROLE_COLUMN);
    }

    void addSection(String id, String title, boolean accent, boolean startOpen) {
        Slot slot = new Slot(id, title, accent, startOpen);
        slots.put(id, slot);
        add(slot.outer);
        add(javax.swing.Box.createVerticalStrut(2));
    }

    /** Verplaats tab-content naar deze sectie (JScrollPane wordt uitgepakt). */
    void putTabContent(String id, Component tabComponent) {
        Slot slot = slots.get(id);
        if (slot == null || tabComponent == null) {
            return;
        }
        slot.body.removeAll();
        if (tabComponent instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane) tabComponent;
            scrollHosts.put(id, sp);
            Component view = sp.getViewport().getView();
            if (view != null) {
                sp.setViewportView(null);
                adopt(slot.body, view);
            }
        } else {
            scrollHosts.remove(id);
            adopt(slot.body, tabComponent);
        }
        slot.body.revalidate();
        slot.body.repaint();
    }

    /** Haal content terug in de oorspronkelijke tab-vorm (scrollpane indien van toepassing). */
    Component takeTabContent(String id) {
        Slot slot = slots.get(id);
        if (slot == null) {
            return null;
        }
        Component view = slot.body.getComponentCount() > 0 ? slot.body.getComponent(0) : null;
        if (view != null) {
            slot.body.remove(view);
        }
        JScrollPane sp = scrollHosts.remove(id);
        if (sp != null) {
            if (view != null) {
                sp.setViewportView(view);
            }
            return sp;
        }
        return view;
    }

    void setOpen(String id, boolean open) {
        Slot slot = slots.get(id);
        if (slot != null) {
            slot.setOpen(open);
        }
    }

    void expandSkillPage(String page) {
        for (String id : new String[]{"Woodcut", "Fishing", "Imps", "Combat"}) {
            setOpen(id, id.equals(page));
        }
    }

    private static void adopt(Container dest, Component c) {
        Container p = c.getParent();
        if (p != null) {
            p.remove(c);
        }
        if (c instanceof JComponent) {
            ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        }
        dest.add(c, BorderLayout.CENTER);
    }

    private static final class Slot {
        final JPanel outer = new JPanel();
        final JButton header;
        final JPanel body = new JPanel(new BorderLayout());
        final String title;
        boolean open;

        Slot(String id, String title, boolean accent, boolean startOpen) {
            this.title = title;
            this.open = startOpen;
            outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
            outer.setOpaque(true);
            outer.setBackground(LoneBotUiTheme.BG_DARK);
            outer.setAlignmentX(LEFT_ALIGNMENT);
            outer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LoneBotUiTheme.BORDER));

            header = new JButton((startOpen ? "▼  " : "▶  ") + title);
            header.setFont(LoneBotUiTheme.FONT_SECTION);
            header.setForeground(LoneBotUiTheme.GOLD);
            header.setHorizontalAlignment(SwingConstants.LEFT);
            header.setBorderPainted(false);
            header.setFocusPainted(false);
            header.setContentAreaFilled(false);
            header.setOpaque(true);
            header.setBackground(LoneBotUiTheme.BG_HEADER);
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            header.setBorder(new EmptyBorder(9, 10, 9, 10));
            header.setAlignmentX(LEFT_ALIGNMENT);
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            LoneBotUiTheme.mark(header, accent ? LoneBotUiTheme.ROLE_HEADER_ACCENT : LoneBotUiTheme.ROLE_HEADER);
            LoneBotUiTheme.wireHeaderButton(header, accent);

            body.setOpaque(true);
            body.setBackground(LoneBotUiTheme.BG_SECTION);
            body.setAlignmentX(LEFT_ALIGNMENT);
            body.setVisible(startOpen);
            body.setBorder(new EmptyBorder(4, 6, 8, 6));

            header.addActionListener(e -> setOpen(!open));
            outer.add(header);
            outer.add(body);
        }

        void setOpen(boolean want) {
            open = want;
            body.setVisible(want);
            header.setText((want ? "▼  " : "▶  ") + title);
            outer.revalidate();
            Container up = outer.getParent();
            while (up != null) {
                up.revalidate();
                up.repaint();
                up = up.getParent();
            }
        }
    }
}
