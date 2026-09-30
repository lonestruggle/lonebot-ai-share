package net.runelite.client.plugins.lonebot;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Popup: werelden kiezen met filters, landvlaggen en vinkjes per rij.
 */
public final class AccountWorldPickerDialog {

    private AccountWorldPickerDialog() {
    }

    /**
     * @return true als opgeslagen
     */
    public static boolean show(Component parent, ManagedAccountsStore.ManagedAccount account) {
        if (account == null) {
            return false;
        }
        Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        if (owner == null && parent instanceof Window) {
            owner = (Window) parent;
        }

        JDialog dlg = new JDialog(owner, "Werelden — " + nullToEmpty(account.displayName),
                JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        Set<Integer> selected = new LinkedHashSet<>(account.preferredWorldIds());
        List<OsrsWorldCatalog.Entry> rows = new ArrayList<>();

        WorldTableModel tableModel = new WorldTableModel(rows, selected);
        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setBackground(LoneBotUiTheme.BG_SECTION);
        table.setForeground(LoneBotUiTheme.TEXT);
        table.setSelectionBackground(LoneBotUiTheme.BG_SECTION);
        table.setSelectionForeground(LoneBotUiTheme.TEXT);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(36);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(0).setMinWidth(32);
        table.getColumnModel().getColumn(1).setPreferredWidth(500);
        table.getColumnModel().getColumn(2).setPreferredWidth(56);
        table.getColumnModel().getColumn(2).setMaxWidth(64);
        table.getColumnModel().getColumn(2).setMinWidth(48);

        DefaultTableCellRenderer flagRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, "", false, false, row, column);
                c.setOpaque(true);
                c.setBackground(LoneBotUiTheme.BG_SECTION);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setIcon(value instanceof Icon ? (Icon) value : null);
                c.setText("");
                c.setBorder(new EmptyBorder(2, 4, 2, 2));
                return c;
            }
        };
        DefaultTableCellRenderer labelRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, column);
                c.setOpaque(true);
                c.setBackground(LoneBotUiTheme.BG_SECTION);
                c.setForeground(LoneBotUiTheme.TEXT);
                c.setFont(c.getFont().deriveFont(Font.PLAIN, 12f));
                c.setIcon(null);
                c.setBorder(new EmptyBorder(2, 6, 2, 4));
                return c;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(flagRenderer);
        table.getColumnModel().getColumn(1).setCellRenderer(labelRenderer);
        table.getColumnModel().getColumn(2).setHeaderValue("✓");
        DefaultTableCellRenderer headerCenter = (DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
        if (headerCenter != null) {
            headerCenter.setHorizontalAlignment(SwingConstants.CENTER);
        }

        JCheckBox cbF2p = new JCheckBox("F2P", true);
        JCheckBox cbMembers = new JCheckBox("Members", false);
        JCheckBox cbUk = regionCheck("UK", true);
        JCheckBox cbDe = regionCheck("DE", true);
        JCheckBox cbUs = regionCheck("US", true);
        JCheckBox cbAu = regionCheck("AU", true);
        JCheckBox cbHidePvp = new JCheckBox("Verberg PVP", true);
        JCheckBox cbHideHr = new JCheckBox("Verberg High Risk", true);
        JCheckBox cbHideSt = new JCheckBox("Verberg Skill Total", false);
        JCheckBox cbHideDm = new JCheckBox("Verberg Deadman", true);
        JCheckBox cbHop = new JCheckBox("Start client op gekozen wereld (Default World)",
                account.accountSwitchWorldHopEnabled || !selected.isEmpty());
        styleChecks(cbF2p, cbMembers, cbUk, cbDe, cbUs, cbAu, cbHidePvp, cbHideHr, cbHideSt, cbHideDm, cbHop);

        JTextField search = new JTextField();
        search.setToolTipText("Zoek op wereld-ID, land of activiteit");
        styleField(search);

        JLabel selectedLbl = new JLabel(" ");
        selectedLbl.setForeground(LoneBotUiTheme.TEXT_DIM);

        Runnable refreshList = () -> {
            rows.clear();
            rows.addAll(OsrsWorldCatalog.filter(
                    cbF2p.isSelected(), cbMembers.isSelected(),
                    cbHidePvp.isSelected(), cbHideHr.isSelected(),
                    cbHideSt.isSelected(), cbHideDm.isSelected(),
                    cbUk.isSelected(), cbDe.isSelected(), cbUs.isSelected(), cbAu.isSelected(),
                    search.getText()));
            tableModel.fireTableDataChanged();
            selectedLbl.setText(formatSelected(selected));
        };

        tableModel.setOnSelectionChanged(() -> {
            selectedLbl.setText(formatSelected(selected));
            if (!selected.isEmpty() && !cbHop.isSelected()) {
                cbHop.setSelected(true);
            }
        });

        DocumentListener dl = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshList.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshList.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshList.run();
            }
        };
        search.getDocument().addDocumentListener(dl);
        for (JCheckBox cb : new JCheckBox[]{
                cbF2p, cbMembers, cbUk, cbDe, cbUs, cbAu, cbHidePvp, cbHideHr, cbHideSt, cbHideDm}) {
            cb.addActionListener(e -> refreshList.run());
        }

        JTextField customId = new JTextField(6);
        styleField(customId);
        JButton addCustom = new JButton("Voeg ID toe");
        addCustom.addActionListener(e -> {
            try {
                int id = Integer.parseInt(customId.getText().trim());
                if (id <= 0) {
                    throw new NumberFormatException();
                }
                selected.add(id);
                customId.setText("");
                tableModel.fireTableDataChanged();
                selectedLbl.setText(formatSelected(selected));
                if (!cbHop.isSelected()) {
                    cbHop.setSelected(true);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dlg, "Ongeldig wereld-ID.", "Werelden", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton clearSel = new JButton("Wis selectie");
        clearSel.addActionListener(e -> {
            selected.clear();
            tableModel.fireTableDataChanged();
            selectedLbl.setText(formatSelected(selected));
        });

        JPanel filtersType = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filtersType.setOpaque(false);
        filtersType.add(cbF2p);
        filtersType.add(cbMembers);
        filtersType.add(cbHidePvp);
        filtersType.add(cbHideHr);
        filtersType.add(cbHideSt);
        filtersType.add(cbHideDm);

        JPanel filtersRegion = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        filtersRegion.setOpaque(false);
        JLabel landLbl = new JLabel("Land:");
        landLbl.setForeground(LoneBotUiTheme.TEXT_DIM);
        filtersRegion.add(landLbl);
        filtersRegion.add(regionFilterChip(cbUk));
        filtersRegion.add(regionFilterChip(cbDe));
        filtersRegion.add(regionFilterChip(cbUs));
        filtersRegion.add(regionFilterChip(cbAu));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(true);
        north.setBackground(LoneBotUiTheme.BG_DARK);
        north.setBorder(new EmptyBorder(8, 10, 4, 10));
        JLabel hint = new JLabel("<html>Vink werelden aan/uit in de rechterkolom (meerdere mogelijk). "
                + "Bij start wordt <b>één willekeurige</b> als Default World gezet.</html>");
        hint.setForeground(LoneBotUiTheme.TEXT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(hint);
        north.add(Box.createVerticalStrut(6));
        filtersType.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(filtersType);
        filtersRegion.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(filtersRegion);
        north.add(Box.createVerticalStrut(4));
        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setOpaque(false);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel searchLbl = new JLabel("Zoek:");
        searchLbl.setForeground(LoneBotUiTheme.TEXT_DIM);
        searchRow.add(searchLbl, BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(searchRow);
        north.add(Box.createVerticalStrut(4));
        selectedLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(selectedLbl);
        north.add(Box.createVerticalStrut(4));
        cbHop.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(cbHop);

        JPanel customRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        customRow.setOpaque(false);
        JLabel customLbl = new JLabel("Handmatig ID:");
        customLbl.setForeground(LoneBotUiTheme.TEXT_DIM);
        customRow.add(customLbl);
        customRow.add(customId);
        customRow.add(addCustom);
        customRow.add(clearSel);
        customRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(customRow);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(LoneBotUiTheme.BORDER));
        scroll.getViewport().setBackground(LoneBotUiTheme.BG_SECTION);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(true);
        south.setBackground(LoneBotUiTheme.BG_DARK);
        JButton cancel = new JButton("Annuleren");
        JButton ok = new JButton("Opslaan");
        final boolean[] saved = {false};
        ok.addActionListener(e -> {
            account.setPreferredWorldIds(new ArrayList<>(selected));
            account.accountSwitchWorldHopEnabled = cbHop.isSelected() && !selected.isEmpty();
            if (selected.isEmpty()) {
                account.accountSwitchWorld = "";
                account.accountSwitchWorldHopEnabled = false;
            }
            saved[0] = true;
            dlg.dispose();
        });
        cancel.addActionListener(e -> dlg.dispose());
        south.add(cancel);
        south.add(ok);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(LoneBotUiTheme.BG_DARK);
        root.setBorder(new EmptyBorder(4, 4, 4, 4));
        root.add(north, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.setSize(720, 560);
        dlg.setLocationRelativeTo(owner);
        refreshList.run();
        dlg.setVisible(true);
        return saved[0];
    }

    private static final class WorldTableModel extends AbstractTableModel {
        private final List<OsrsWorldCatalog.Entry> rows;
        private final Set<Integer> selected;
        private Runnable onSelectionChanged;

        WorldTableModel(List<OsrsWorldCatalog.Entry> rows, Set<Integer> selected) {
            this.rows = rows;
            this.selected = selected;
        }

        void setOnSelectionChanged(Runnable r) {
            this.onSelectionChanged = r;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0) {
                return "";
            }
            if (column == 1) {
                return "Wereld";
            }
            return "✓";
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Icon.class;
            }
            if (columnIndex == 2) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            OsrsWorldCatalog.Entry e = rows.get(rowIndex);
            if (columnIndex == 0) {
                return RegionFlagIcons.forRegion(e.region);
            }
            if (columnIndex == 2) {
                return selected.contains(e.id);
            }
            return e.listLabel();
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex != 2 || rowIndex < 0 || rowIndex >= rows.size()) {
                return;
            }
            OsrsWorldCatalog.Entry e = rows.get(rowIndex);
            boolean on = Boolean.TRUE.equals(aValue);
            if (on) {
                selected.add(e.id);
            } else {
                selected.remove(e.id);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
            if (onSelectionChanged != null) {
                onSelectionChanged.run();
            }
        }
    }

    private static JCheckBox regionCheck(String region, boolean selected) {
        JCheckBox cb = new JCheckBox(region, selected);
        cb.setOpaque(false);
        cb.setForeground(LoneBotUiTheme.TEXT);
        return cb;
    }

    /** Vlag-icoon + checkbox (emoji werkt niet betrouwbaar in Swing). */
    private static JPanel regionFilterChip(JCheckBox cb) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel flag = new JLabel(RegionFlagIcons.forRegion(cb.getText()));
        flag.setToolTipText(cb.getText());
        p.add(flag);
        p.add(cb);
        return p;
    }

    private static void styleChecks(JCheckBox... boxes) {
        for (JCheckBox cb : boxes) {
            cb.setOpaque(false);
            cb.setForeground(LoneBotUiTheme.TEXT);
        }
    }

    private static void styleField(JTextField f) {
        f.setBackground(LoneBotUiTheme.INPUT_BG);
        f.setForeground(LoneBotUiTheme.INPUT_FG);
        f.setCaretColor(LoneBotUiTheme.INPUT_FG);
    }

    private static String formatSelected(Set<Integer> selected) {
        if (selected == null || selected.isEmpty()) {
            return "Geselecteerd: (geen)";
        }
        StringBuilder sb = new StringBuilder("Geselecteerd (" + selected.size() + "): ");
        int i = 0;
        for (Integer id : selected) {
            if (i++ > 0) {
                sb.append(", ");
            }
            OsrsWorldCatalog.Entry e = OsrsWorldCatalog.find(id);
            if (e != null) {
                sb.append('[').append(e.region).append("] ").append(id)
                        .append(" (").append(e.typeLabel()).append(')');
            } else {
                sb.append(id);
            }
            if (i >= 8 && selected.size() > 8) {
                sb.append(" … +").append(selected.size() - 8);
                break;
            }
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
