package net.runelite.client.plugins.lonebot;

import net.storm.api.account.GameAccount;
import net.storm.sdk.bot.BotRuntime;
import com.lonebot.launcher.LauncherGlass;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Accounts-tab: launcher (multi-client) of game-client (eigen besturing).
 */
public final class LoneBotAccountsTab extends JPanel {

    public enum Mode {
        /** Geen bot-besturing — accounts + hiscore + clients starten. */
        LAUNCHER,
        /** Alleen besturing voor déze client. */
        CLIENT
    }

    private static final int COL_ROT = 0;
    private static final int COL_INFO = 1;
    private static final int COL_USER = 2;
    private static final int COL_GP = 3;
    private static final int COL_CMB = 4;
    private static final int COL_ASD = 5;
    private static final int COL_WC = 6;
    private static final int COL_MIN = 7;
    private static final int COL_FISH = 8;
    private static final int COL_PRAY = 9;
    private static final int COL_MAGIC = 10;
    private static final int COL_SCRIPT = 11;
    private static final int COL_LAST = 12;
    private static final int COL_WORLDS = 13;
    private static final int COL_BREAK = 14;
    private static final int COL_REMOTE = 15;

    private static final File DEFAULT_STORM_JSON =
            new File(System.getProperty("user.home"), "Desktop" + File.separator + "storm-accountsjuli.json");
    /** Standaard pad: {@code Desktop/lonebot-client/osrs 2FA/accounts.json}. */
    private static final File DEFAULT_2FA_JSON =
            new File(System.getProperty("user.home"),
                    "Desktop" + File.separator + "lonebot-client" + File.separator
                            + "osrs 2FA" + File.separator + "accounts.json");

    private final Mode mode;
    private final Consumer<String> status;
    private final Consumer<ManagedAccountsStore.ManagedAccount> onPrepareLogin;
    private final net.runelite.client.config.ConfigManager configManager;
    private final LoneBotConfig config;
    private final List<ManagedAccountsStore.ManagedAccount> accountRows;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JTextArea pasteArea = new JTextArea(3, 20);
    private final JLabel importStatus = new JLabel(" ");
    private final JLabel botStatusLbl = new JLabel("●  Uit");
    private final JCheckBox cbHideBanned = new JCheckBox("Verberg vermoedelijk banned (hiscore 404)", true);

    private Timer persistDebounce;
    private Timer tableRefreshTimer;
    private Timer botUiTimer;
    private JPanel controlBar;
    private JPanel northPanel;
    private JPanel southPanel;
    private JScrollPane tableScroll;
    private JLabel pasteLbl;
    private JScrollPane pasteScroll;
    private boolean sidebarNarrow;
    private boolean hiscoreColsVisible = true;
    private final List<TableColumn> stashedHiscoreCols = new ArrayList<>();

    /** Client-mode (in-game panel). */
    public LoneBotAccountsTab(Consumer<String> status,
                              Consumer<ManagedAccountsStore.ManagedAccount> onPrepareLogin) {
        this(Mode.CLIENT, status, onPrepareLogin, null, null);
    }

    public LoneBotAccountsTab(Mode mode,
                              Consumer<String> status,
                              Consumer<ManagedAccountsStore.ManagedAccount> onPrepareLogin) {
        this(mode, status, onPrepareLogin, null, null);
    }

    public LoneBotAccountsTab(Mode mode,
                              Consumer<String> status,
                              Consumer<ManagedAccountsStore.ManagedAccount> onPrepareLogin,
                              net.runelite.client.config.ConfigManager configManager,
                              LoneBotConfig config) {
        this.mode = mode != null ? mode : Mode.CLIENT;
        this.status = status != null ? status : s -> {};
        this.onPrepareLogin = onPrepareLogin != null ? onPrepareLogin : a -> {};
        this.configManager = configManager;
        this.config = config;
        this.accountRows = ManagedAccountsStore.mutableAccounts();

        setLayout(new BorderLayout(0, 6));
        setOpaque(true);
        setBackground(LoneBotUiTheme.BG_DARK);
        LoneBotUiTheme.mark(this, LoneBotUiTheme.ROLE_ROOT);
        setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(true);
        north.setBackground(LoneBotUiTheme.BG_DARK);
        LoneBotUiTheme.mark(north, LoneBotUiTheme.ROLE_COLUMN);
        this.northPanel = north;
        if (this.mode == Mode.CLIENT) {
            north.add(buildControlBar());
            north.add(Box.createVerticalStrut(6));
        } else {
            JLabel launcherHint = new JLabel("<html><b>Launcher</b> — geen bot-besturing hier. "
                    + "Start een account → die client heeft eigen Start/Stop.</html>");
            launcherHint.setForeground(LoneBotUiTheme.TEXT);
            launcherHint.setAlignmentX(Component.LEFT_ALIGNMENT);
            north.add(launcherHint);
            north.add(Box.createVerticalStrut(6));
        }
        add(north, BorderLayout.NORTH);

        String[] cols = mode == Mode.LAUNCHER
                ? new String[]{
                "Selecteer", "ℹ", "Gebruikersnaam", "GP ~", "Cmb", "A/S/D",
                "WC", "Min", "Fish", "Pray", "Magic", "Script", "Laatst", "Werelden", "Break", "Remote"
        }
                : new String[]{
                "Selecteer", "ℹ", "Gebruikersnaam", "GP ~", "Cmb", "A/S/D",
                "WC", "Min", "Fish", "Pray", "Magic", "Script", "Laatst", "Werelden"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == COL_ROT ? Boolean.class : Object.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                if (column == COL_ROT) {
                    return true;
                }
                return mode == Mode.LAUNCHER && column == COL_REMOTE;
            }
        };

        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                LauncherGlass.styleCell(c, isCellSelected(row, column));
                return c;
            }
        };
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setSelectionForeground(Color.WHITE);
        table.setShowGrid(true);
        table.setRowHeight(mode == Mode.LAUNCHER ? 30 : 24);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        applyThemeTableColors();
        // Cell renderer: altijd actieve thema-kleuren (niet vast bij constructie)
        DefaultTableCellRenderer themedCell = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                          int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(LoneBotUiTheme.BG_SECTION);
                    c.setForeground(LoneBotUiTheme.TEXT);
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, themedCell);
        table.getColumnModel().getColumn(COL_ROT).setPreferredWidth(72);
        table.getColumnModel().getColumn(COL_ROT).setMinWidth(64);
        table.getColumnModel().getColumn(COL_ROT).setHeaderValue("Selecteer");
        table.getTableHeader().getColumnModel().getColumn(COL_ROT)
                .setHeaderValue("Selecteer");
        // Tooltip via header: klik vinkjes / Select all om clients te starten
        table.getTableHeader().setToolTipText(
                "Selecteer = meenemen bij Start. Gebruik □ Select all om alles aan/uit te zetten.");
        table.getColumnModel().getColumn(COL_INFO).setPreferredWidth(28);
        table.getColumnModel().getColumn(COL_INFO).setMaxWidth(36);
        table.getColumnModel().getColumn(COL_USER).setPreferredWidth(110);
        for (int c = COL_GP; c <= COL_LAST; c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(c == COL_ASD || c == COL_SCRIPT ? 70 : 48);
        }
        table.getColumnModel().getColumn(COL_WORLDS).setPreferredWidth(88);
        table.getColumnModel().getColumn(COL_WORLDS).setMinWidth(72);
        table.getColumnModel().getColumn(COL_WORLDS).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                          int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                if (!isSelected) {
                    c.setOpaque(true);
                    c.setBackground(LoneBotUiTheme.BG_SECTION);
                    c.setForeground(LoneBotUiTheme.GOLD);
                }
                c.setToolTipText("Klik om werelden toe te wijzen (meerdere + filters)");
                return c;
            }
        });
        if (mode == Mode.LAUNCHER && table.getColumnCount() > COL_BREAK) {
            table.getColumnModel().getColumn(COL_BREAK).setPreferredWidth(108);
            table.getColumnModel().getColumn(COL_BREAK).setMinWidth(88);
            table.getColumnModel().getColumn(COL_BREAK).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                               boolean hasFocus, int row, int column) {
                    JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                    c.setHorizontalAlignment(SwingConstants.CENTER);
                    String text = value != null ? value.toString() : "—";
                    c.setText(text);
                    c.setToolTipText("Break / re-log timer");
                    if (!isSelected) {
                        c.setOpaque(true);
                        c.setBackground(LoneBotUiTheme.BG_SECTION);
                        boolean active = text.startsWith("wacht") || text.startsWith("pauze")
                                || text.startsWith("uitlog") || text.startsWith("inlog");
                        c.setForeground(active ? new Color(230, 180, 80) : LoneBotUiTheme.TEXT);
                    }
                    return c;
                }
            });
        }
        if (mode == Mode.LAUNCHER && table.getColumnCount() > COL_REMOTE) {
            table.getColumnModel().getColumn(COL_REMOTE).setPreferredWidth(178);
            table.getColumnModel().getColumn(COL_REMOTE).setMinWidth(168);
            RemoteCell remoteCell = new RemoteCell();
            table.getColumnModel().getColumn(COL_REMOTE).setCellRenderer(remoteCell);
            table.getColumnModel().getColumn(COL_REMOTE).setCellEditor(remoteCell);
        }

        table.getColumnModel().getColumn(COL_INFO).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                          int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setText("ℹ");
                c.setToolTipText("Live hiscore-stats (OSRS API)");
                if (!isSelected) {
                    c.setOpaque(true);
                    c.setBackground(LoneBotUiTheme.BG_SECTION);
                    c.setForeground(LoneBotUiTheme.GOLD);
                }
                return c;
            }
        });

        tableModel.addTableModelListener(e -> {
            if (e.getType() != TableModelEvent.UPDATE || e.getColumn() != COL_ROT) {
                return;
            }
            ManagedAccountsStore.ManagedAccount row = rowAt(e.getFirstRow());
            if (row != null) {
                row.rotationEnabled = Boolean.TRUE.equals(tableModel.getValueAt(e.getFirstRow(), COL_ROT));
                row.enabled = row.rotationEnabled;
                schedulePersist();
            }
        });

        wireTableMouse();

        tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(mode == Mode.LAUNCHER ? 720 : 420, 240));
        tableScroll.setOpaque(true);
        tableScroll.getViewport().setOpaque(true);
        add(tableScroll, BorderLayout.CENTER);
        add(buildSouth(), BorderLayout.SOUTH);

        cbHideBanned.setOpaque(false);
        cbHideBanned.setForeground(LoneBotUiTheme.TEXT);
        cbHideBanned.setText("Verberg vermoedelijk banned");
        cbHideBanned.setToolTipText("Verberg accounts met hiscore 404 (vermoedelijk banned)");
        cbHideBanned.setFont(cbHideBanned.getFont().deriveFont(Font.BOLD, 12f));
        cbHideBanned.setToolTipText("Verberg accounts met bevestigde hiscore-404 (niet bij netwerkfout)");
        cbHideBanned.addActionListener(e -> {
            refillTable();
            status.accept(cbHideBanned.isSelected()
                    ? "Vermoedelijk banned verborgen"
                    : "Vermoedelijk banned zichtbaar");
        });

        refillTable();
        tableRefreshTimer = new Timer(mode == Mode.LAUNCHER ? 2000 : 8000, e -> refillTable());
        tableRefreshTimer.start();
        if (this.mode == Mode.CLIENT) {
            botUiTimer = new Timer(500, e -> refreshControlButtons());
            botUiTimer.start();
        }
        applyTheme();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (mode == Mode.LAUNCHER && LauncherGlass.isOn()) {
            LauncherGlass.paintTint(g, getWidth(), getHeight());
        }
        super.paintComponent(g);
    }

    private JPanel buildControlBar() {
        // CombatBotPanel#createAccountsBotControlBar — headline + knoppen op één rij
        JPanel wrap = new JPanel(new BorderLayout(12, 0));
        wrap.setBackground(LoneBotUiTheme.BG_SECTION);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(LoneBotUiTheme.BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        LoneBotUiTheme.mark(wrap, LoneBotUiTheme.ROLE_SECTION_CONTENT);

        JLabel headline = new JLabel("Besturing");
        headline.setForeground(LoneBotUiTheme.GOLD);
        headline.setFont(headline.getFont().deriveFont(Font.BOLD, 12f));
        headline.putClientProperty("lonebot.accountsHeadline", Boolean.TRUE);

        JButton btnStart = controlBtn("▶ Start", new Color(32, 120, 62));
        JButton btnPause = controlBtn("⏸ Pauze", new Color(130, 95, 28));
        JButton btnStop = controlBtn("■ Stop", new Color(130, 42, 48));
        JButton btnNextSkill = controlBtn("⏭ Next skill", new Color(45, 90, 145));
        JButton btnNextAccount = controlBtn("⚡ Next account", new Color(85, 60, 120));
        JButton btnReset = controlBtn("↺ Reset", new Color(68, 62, 110));
        JButton btnEmergency = controlBtn("⛔ Noodstop", new Color(160, 20, 28));
        btnNextSkill.setPreferredSize(new Dimension(102, 30));
        btnNextAccount.setPreferredSize(new Dimension(116, 30));
        btnEmergency.setPreferredSize(new Dimension(104, 30));
        btnStart.setToolTipText("Hervat na pauze (zelfde state) of fresh start na Stop");
        btnPause.setToolTipText("Tijdelijk pauzeren — Start gaat verder waar je gebleven was");
        btnStop.setToolTipText("Stoppen — Start = opnieuw beginnen");
        btnNextSkill.setToolTipText("Direct naar volgende skill (switchNow)");
        btnNextAccount.setToolTipText("Direct switchen naar volgende account in rotatie");
        btnReset.setToolTipText("Runtime state opschonen; huidige scripts behouden");
        btnEmergency.setToolTipText("Bot + alle scripts direct uit");

        botStatusLbl.setHorizontalAlignment(SwingConstants.RIGHT);
        botStatusLbl.setForeground(LoneBotUiTheme.TEXT_DIM);

        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(headline, BorderLayout.WEST);
        head.add(botStatusLbl, BorderLayout.EAST);

        JPanel primary = new JPanel(new GridLayout(1, 3, 6, 0));
        primary.setOpaque(false);
        primary.add(btnStart);
        primary.add(btnPause);
        primary.add(btnStop);

        JPanel extra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        extra.setOpaque(false);
        extra.add(btnNextSkill);
        extra.add(btnNextAccount);
        extra.add(btnReset);
        extra.add(btnEmergency);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        primary.setAlignmentX(Component.LEFT_ALIGNMENT);
        extra.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(head);
        stack.add(Box.createVerticalStrut(6));
        stack.add(primary);
        if (configManager != null && config != null) {
            LoneBotBreakBar breakBar = LoneBotBreakBar.full(configManager, config);
            breakBar.setAlignmentX(Component.LEFT_ALIGNMENT);
            stack.add(Box.createVerticalStrut(4));
            stack.add(breakBar);
        }
        stack.add(extra);
        wrap.add(stack, BorderLayout.CENTER);

        btnStart.addActionListener(e -> {
            LoneBotBotControl.start();
            LoneBotBotControl.syncBotEnabledConfig(configManager, true);
            applyLaunchedAccountSkillsOnly();
            refreshControlButtons();
            status.accept("Bot AAN");
        });
        btnPause.addActionListener(e -> {
            LoneBotBotControl.pause();
            LoneBotBotControl.syncBotEnabledConfig(configManager, false);
            refreshControlButtons();
            status.accept("Bot pauze");
        });
        btnStop.addActionListener(e -> {
            boolean resetTimers = config == null || config.resetAccountTimersOnStop();
            LoneBotBotControl.stopAndResetTimers(resetTimers);
            LoneBotBotControl.syncBotEnabledConfig(configManager, false);
            refreshControlButtons();
            status.accept("Bot STOP");
        });
        btnNextSkill.addActionListener(e -> {
            LoneBotBotControl.requestSwitchNow();
            status.accept("Next skill / Switch Now");
        });
        btnNextAccount.addActionListener(e -> {
            BotRuntime.nextAccountRequested = true;
            status.accept("Next account gevraagd");
        });
        btnReset.addActionListener(e -> {
            LoneBotBotControl.resetRuntime();
            refreshControlButtons();
            status.accept("Runtime gereset");
        });
        btnEmergency.addActionListener(e -> {
            LoneBotBotControl.emergencyStop();
            LoneBotBotControl.syncBotEnabledConfig(configManager, false);
            refreshControlButtons();
            status.accept("NOODSTOP — alles uit");
        });

        wrap.putClientProperty("btnStart", btnStart);
        wrap.putClientProperty("btnPause", btnPause);
        wrap.putClientProperty("btnStop", btnStop);
        wrap.putClientProperty("btnNextSkill", btnNextSkill);
        wrap.putClientProperty("btnNextAccount", btnNextAccount);
        this.controlBar = wrap;
        return wrap;
    }

    private void refreshControlButtons() {
        if (controlBar == null || !controlBar.isDisplayable()) {
            return;
        }
        boolean on = BotRuntime.botEnabled;
        JButton btnStart = (JButton) controlBar.getClientProperty("btnStart");
        JButton btnPause = (JButton) controlBar.getClientProperty("btnPause");
        JButton btnStop = (JButton) controlBar.getClientProperty("btnStop");
        JButton btnNextSkill = (JButton) controlBar.getClientProperty("btnNextSkill");
        JButton btnNextAccount = (JButton) controlBar.getClientProperty("btnNextAccount");
        if (btnStart != null) {
            btnStart.setEnabled(!on);
        }
        if (btnPause != null) {
            btnPause.setEnabled(on);
        }
        if (btnStop != null) {
            btnStop.setEnabled(on || BotRuntime.pausedForResume);
        }
        if (btnNextSkill != null) {
            btnNextSkill.setEnabled(on);
        }
        if (btnNextAccount != null) {
            btnNextAccount.setEnabled(on);
        }
        if (on) {
            botStatusLbl.setText("●  Aan");
            botStatusLbl.setForeground(new Color(80, 200, 120));
        } else if (BotRuntime.pausedForResume) {
            botStatusLbl.setText("●  Pauze");
            botStatusLbl.setForeground(new Color(230, 180, 80));
        } else {
            botStatusLbl.setText("●  Uit");
            botStatusLbl.setForeground(new Color(130, 130, 145));
        }
    }

    /**
     * Smalle RuneLite-sidebar: Start/Pauze/Stop zit al in de pin-balk;
     * hiscore-kolommen weg zodat de tabel past.
     */
    void setSidebarNarrow(boolean narrow) {
        if (mode != Mode.CLIENT) {
            return;
        }
        sidebarNarrow = narrow;
        if (controlBar != null) {
            controlBar.setVisible(!narrow);
        }
        setHiscoreColumnsVisible(!narrow);
        if (tableScroll != null) {
            tableScroll.setPreferredSize(new Dimension(narrow ? 210 : 420, narrow ? 130 : 240));
        }
        revalidate();
        repaint();
    }

    private void setHiscoreColumnsVisible(boolean show) {
        if (table == null || show == hiscoreColsVisible) {
            return;
        }
        TableColumnModel cm = table.getColumnModel();
        if (!show) {
            stashedHiscoreCols.clear();
            int[] hide = {COL_GP, COL_CMB, COL_ASD, COL_WC, COL_MIN, COL_FISH, COL_PRAY, COL_MAGIC, COL_LAST};
            for (int modelId : hide) {
                int view = table.convertColumnIndexToView(modelId);
                if (view >= 0) {
                    TableColumn col = cm.getColumn(view);
                    stashedHiscoreCols.add(col);
                    cm.removeColumn(col);
                }
            }
        } else {
            for (TableColumn col : stashedHiscoreCols) {
                cm.addColumn(col);
            }
            stashedHiscoreCols.clear();
            for (int model = 0; model < table.getModel().getColumnCount(); model++) {
                int view = table.convertColumnIndexToView(model);
                if (view >= 0 && view != model && model < cm.getColumnCount()) {
                    cm.moveColumn(view, model);
                }
            }
        }
        hiscoreColsVisible = show;
    }

    /**
     * Play mag skills van het <b>gelanceerde</b> account zetten — nooit de
     * geselecteerde tabelrij (die kan een ander account zijn en WC uitzetten).
     */
    private void applyLaunchedAccountSkillsOnly() {
        String launched = LoneBotBotControl.launchedAccountName();
        if (launched == null || launched.isBlank()) {
            return;
        }
        ManagedAccountsStore.ManagedAccount row = ManagedAccountsStore.findByDisplayName(launched);
        if (row == null) {
            return;
        }
        LoneBotBotControl.applyAccountSkills(row, configManager);
    }

    private JPanel buildSouth() {
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(true);
        south.setBackground(LoneBotUiTheme.BG_DARK);
        LoneBotUiTheme.mark(south, LoneBotUiTheme.ROLE_COLUMN);
        south.setBorder(new EmptyBorder(4, 0, 0, 0));
        this.southPanel = south;

        // Rij 1: filter + hiscore
        JPanel rowHiscore = toolRow();
        cbHideBanned.setFont(cbHideBanned.getFont().deriveFont(Font.PLAIN, 11f));
        rowHiscore.add(cbHideBanned);
        rowHiscore.add(Box.createHorizontalStrut(6));
        JButton btnHiscore = compactBtn("Hiscore", new Color(45, 95, 160));
        btnHiscore.setToolTipText("Hiscore ophalen — alleen HTTP 404 op alle boards = banned; netwerkfout wijzigt flag niet");
        btnHiscore.addActionListener(e -> refreshHiscoreSelected());
        rowHiscore.add(btnHiscore);
        JButton btnCheckAll = compactBtn("Check alle", new Color(45, 95, 160));
        btnCheckAll.setToolTipText("Stale/nieuw. Banned-accounts worden overgeslagen tot Wis ban-flags. Netwerkfout ≠ ban.");
        btnCheckAll.addActionListener(e -> checkAllHiscores(false));
        rowHiscore.add(btnCheckAll);
        if (mode == Mode.LAUNCHER) {
            JButton btnForce = compactBtn("Forceer", new Color(90, 85, 140));
            btnForce.setToolTipText("Hercheck niet-banned accounts (ook recent). Banned blijven tot Wis ban-flags.");
            btnForce.addActionListener(e -> checkAllHiscores(true));
            rowHiscore.add(btnForce);
            JButton btnClearBan = compactBtn("Wis ban-flags", new Color(120, 90, 50));
            btnClearBan.setToolTipText("Wist opgeslagen ban-flags. Daarna Check alle = alles opnieuw hiscore.");
            btnClearBan.addActionListener(e -> {
                int n = HiscoreBanChecker.clearAllBanFlags();
                refillTable();
                status.accept("Ban-flags gewist: " + n + " account(s)");
                importStatus.setText("Ban-flags gewist: " + n);
            });
            rowHiscore.add(btnClearBan);
        }
        south.add(rowHiscore);

        if (mode == Mode.LAUNCHER) {
            // Rij 2: clients starten / sluiten
            JPanel rowLaunch = toolRow();
            rowLaunch.add(sectionTag("Clients"));
            JButton btnSelectAll = compactBtn("☑ Select all", new Color(70, 95, 130));
            btnSelectAll.setToolTipText("Alles selecteren of alles uitvinken (kolom Selecteer)");
            btnSelectAll.addActionListener(e -> toggleSelectAllForStart());
            rowLaunch.add(btnSelectAll);
            JButton btnLaunchSel = compactBtn("▶ Start selectie", new Color(28, 140, 70));
            btnLaunchSel.setToolTipText(
                    "Aangevinkt (Selecteer) én/of Ctrl+klik rijen — start clients (niet-banned)");
            btnLaunchSel.addActionListener(e -> launchSelectedClients());
            rowLaunch.add(btnLaunchSel);
            JButton btnCloseSel = compactBtn("✕ Sluit selectie", new Color(150, 55, 55));
            btnCloseSel.setToolTipText("Aangevinkt (Selecteer) én/of Ctrl+klik rijen — sluit die clients");
            btnCloseSel.addActionListener(e -> closeSelectedClients());
            rowLaunch.add(btnCloseSel);
            JButton btnCloseAll = compactBtn("✕ Sluit alle", new Color(160, 40, 45));
            btnCloseAll.setToolTipText("Sluit alle door deze launcher gestarte clients");
            btnCloseAll.addActionListener(e -> closeAllLiveClients());
            rowLaunch.add(btnCloseAll);
            south.add(rowLaunch);

            JPanel rowDevMode = toolRow();
            rowDevMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            rowDevMode.add(sectionTag("RuneLite"));
            JCheckBox cbRlDev = new JCheckBox("Developer Tools (Players/NPCs/Widget Inspector)",
                    com.lonebot.launcher.RuneliteDeveloperMode.isEnabled());
            styleCheck(cbRlDev);
            cbRlDev.setOpaque(false);
            cbRlDev.setToolTipText(
                    "Officiële RuneLite DevTools-sidebar: Players, NPCs, Game Objects, Widget Inspector, "
                            + "Detached Camera, Script Inspector, … Bug-icoon na de volgende client-start.");
            cbRlDev.addActionListener(e -> {
                boolean on = cbRlDev.isSelected();
                com.lonebot.launcher.RuneliteDeveloperMode.setEnabled(on);
                if (configManager != null) {
                    configManager.setConfiguration("lonebot",
                            com.lonebot.launcher.RuneliteDeveloperMode.CONFIG_KEY, on);
                }
                status.accept(on
                        ? "✓ RuneLite developer mode AAN — start of herstart de client voor DevTools"
                        : "RuneLite developer mode uit — herstart client om DevTools te verbergen");
            });
            rowDevMode.add(cbRlDev);
            south.add(rowDevMode);
        }

        // Rij 3: accounts beheer
        JPanel rowAccounts = toolRow();
        rowAccounts.add(sectionTag("Accounts"));
        JButton addBtn = compactBtn("+ Nieuw", new Color(32, 125, 70));
        JButton jagexLoginBtn = compactBtn("Jagex login", new Color(40, 120, 100));
        jagexLoginBtn.setToolTipText(
                "Klein OAuth-loginvenster (Chrome/Edge) → session + characters → credentials.properties");
        JButton jagexBtn = compactBtn("Jagex importeren", new Color(28, 110, 90));
        jagexBtn.setToolTipText("Opent Jagex Launcher — na Play worden characters in de lijst gezet (naam = e-mail tot RSN bekend)");
        JButton importPasteBtn = compactBtn("Import plak", new Color(45, 95, 160));
        JButton importStormBtn = compactBtn("Storm JSON", new Color(45, 95, 160));
        importStormBtn.setToolTipText("Storm-accounts.json of LoneBot export (andere pc)");
        JButton import2faBtn = compactBtn("2FA JSON", new Color(45, 95, 160));
        import2faBtn.setToolTipText(
                "Importeer osrs 2FA/accounts.json (email, pass, secret) — TOTP in bewerken / Kopieer OTP");
        JButton importDesktopBtn = compactBtn("Desktop JSON", new Color(45, 95, 160));
        JButton exportBtn = compactBtn("Export JSON", new Color(150, 105, 40));
        exportBtn.setToolTipText("Geselecteerde accounts (vinkjes + gemarkeerde rijen) + settings naar JSON voor een andere pc");
        JButton editBtn = compactBtn("Bewerken", new Color(90, 85, 140));
        JButton otpBtn = compactBtn("Kopieer OTP", new Color(28, 110, 90));
        otpBtn.setToolTipText("Kopieer huidige authenticator-code van geselecteerd account");
        JButton delBtn = compactBtn("Verwijder", new Color(160, 45, 50));
        JButton refreshBtn = compactBtn("↺", new Color(70, 75, 95));
        refreshBtn.setToolTipText("Lijst vernieuwen");
        rowAccounts.add(addBtn);
        if (mode == Mode.LAUNCHER) {
            rowAccounts.add(jagexLoginBtn);
            rowAccounts.add(jagexBtn);
        }
        rowAccounts.add(importPasteBtn);
        rowAccounts.add(importStormBtn);
        rowAccounts.add(import2faBtn);
        rowAccounts.add(importDesktopBtn);
        rowAccounts.add(exportBtn);
        rowAccounts.add(editBtn);
        rowAccounts.add(otpBtn);
        rowAccounts.add(delBtn);
        if (mode == Mode.CLIENT) {
            JButton loginBtn = compactBtn("Login klaarzetten", new Color(40, 100, 70));
            rowAccounts.add(loginBtn);
            loginBtn.addActionListener(e -> loginSelected());
        }
        rowAccounts.add(refreshBtn);
        south.add(rowAccounts);

        importStatus.setForeground(LoneBotUiTheme.TEXT_DIM);
        importStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        importStatus.setBorder(new EmptyBorder(2, 2, 2, 2));
        south.add(importStatus);

        pasteArea.setLineWrap(true);
        pasteArea.setWrapStyleWord(true);
        pasteArea.setBackground(LoneBotUiTheme.INPUT_BG);
        pasteArea.setForeground(LoneBotUiTheme.INPUT_FG);
        pasteArea.setCaretColor(LoneBotUiTheme.INPUT_FG);
        LoneBotUiTheme.mark(pasteArea, LoneBotUiTheme.ROLE_INPUT);
        pasteScroll = new JScrollPane(pasteArea);
        pasteScroll.setPreferredSize(new Dimension(420, 56));
        pasteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        pasteScroll.setOpaque(true);
        pasteScroll.setBackground(LoneBotUiTheme.BG_DARK);
        pasteScroll.getViewport().setOpaque(true);
        pasteScroll.getViewport().setBackground(LoneBotUiTheme.INPUT_BG);
        pasteLbl = new JLabel("Plak credentials (Import plak):");
        pasteLbl.setForeground(LoneBotUiTheme.TEXT);
        pasteLbl.setFont(pasteLbl.getFont().deriveFont(11f));
        south.add(pasteLbl);
        south.add(pasteScroll);

        addBtn.addActionListener(e -> {
            ManagedAccountsStore.ManagedAccount r = new ManagedAccountsStore.ManagedAccount();
            r.displayName = ManagedAccountsStore.nextDefaultDisplayName();
            r.rotationEnabled = true;
            r.enabled = true;
            accountRows.add(r);
            refillTable();
            schedulePersist();
            int idx = table.getRowCount() - 1;
            if (idx >= 0) {
                table.setRowSelectionInterval(idx, idx);
                editSelected();
            }
        });
        jagexLoginBtn.addActionListener(e -> openJagexOauthLogin());
        jagexBtn.addActionListener(e -> openJagexImport());
        importPasteBtn.addActionListener(e -> importFromPaste());
        importStormBtn.addActionListener(e -> importStormChooser());
        import2faBtn.addActionListener(e -> import2faChooser());
        importDesktopBtn.addActionListener(e -> importStormFile(DEFAULT_STORM_JSON));
        exportBtn.addActionListener(e -> exportSelectedAccounts());
        editBtn.addActionListener(e -> editSelected());
        otpBtn.addActionListener(e -> copySelectedOtp());
        delBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> {
            ManagedAccountsStore.ensureLoaded();
            refillTable();
            status.accept(accountRows.size() + " account(s)");
        });
        return south;
    }

    private static JPanel toolRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        row.setOpaque(true);
        row.setBackground(LoneBotUiTheme.BG_DARK);
        LoneBotUiTheme.mark(row, LoneBotUiTheme.ROLE_COLUMN);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return row;
    }

    private static JLabel sectionTag(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(LoneBotUiTheme.TEXT_DIM);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 10f));
        l.setBorder(new EmptyBorder(0, 0, 0, 4));
        return l;
    }

    private static JButton compactBtn(String text, Color bg) {
        JButton b = new JButton(text);
        styleActionButton(b, bg, 10.5f, false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(brighter(bg != null ? bg : new Color(55, 70, 100), 1.25f), 1),
                new EmptyBorder(3, 7, 3, 7)));
        return b;
    }

    private void wireTableMouse() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem miEdit = new JMenuItem("Bewerken…");
        JMenuItem miDelete = new JMenuItem("Verwijderen");
        JMenuItem miHiscore = new JMenuItem("Hiscore…");
        JMenuItem miExport = new JMenuItem("Export JSON (selectie)…");
        popup.add(miEdit);
        popup.add(miHiscore);
        popup.add(miExport);
        miExport.addActionListener(e -> exportSelectedAccounts());
        if (mode == Mode.LAUNCHER) {
            JMenuItem miStart = new JMenuItem("▶ Start client");
            popup.add(miStart);
            miStart.addActionListener(e -> {
                ManagedAccountsStore.ManagedAccount a = rowAt(table.getSelectedRow());
                if (a != null) {
                    launchClientsAsync(List.of(a.copy()));
                }
            });
        } else {
            JMenuItem miLogin = new JMenuItem("Login klaarzetten");
            popup.add(miLogin);
            miLogin.addActionListener(e -> loginSelected());
        }
        popup.add(miDelete);
        miEdit.addActionListener(e -> editSelected());
        miDelete.addActionListener(e -> deleteSelected());
        miHiscore.addActionListener(e -> {
            ManagedAccountsStore.ManagedAccount a = rowAt(table.getSelectedRow());
            if (a != null) {
                AccountStatsGridDialog.show(SwingUtilities.getWindowAncestor(this), a.displayName);
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                int viewCol = table.columnAtPoint(e.getPoint());
                if (viewCol < 0) {
                    return;
                }
                int col = table.convertColumnIndexToModel(viewCol);
                // Ctrl/Shift/Meta = multi-select laten staan (niet terugzetten naar 1 rij)
                if (!e.isControlDown() && !e.isMetaDown() && !e.isShiftDown()) {
                    table.setRowSelectionInterval(row, row);
                }
                ManagedAccountsStore.ManagedAccount clicked = rowAt(row);
                // Werelden: enkele klik → picker
                if (col == COL_WORLDS && clicked != null) {
                    if (AccountWorldPickerDialog.show(LoneBotAccountsTab.this, clicked)) {
                        ManagedAccountsStore.save();
                        refillTable();
                        status.accept("Werelden opgeslagen voor " + clicked.displayName);
                    }
                    return;
                }
                // Remote: 1 klik op knop (geen dubbelklik)
                if (mode == Mode.LAUNCHER && col == COL_REMOTE) {
                    if (table.editCellAt(row, COL_REMOTE)) {
                        Component editor = table.getEditorComponent();
                        if (editor != null) {
                            java.awt.Point p = SwingUtilities.convertPoint(table, e.getPoint(), editor);
                            Component target = SwingUtilities.getDeepestComponentAt(editor, p.x, p.y);
                            if (target instanceof JButton) {
                                ((JButton) target).doClick();
                            }
                        }
                    }
                    return;
                }
                // Enkele klik = alleen selecteren
                if (e.getClickCount() < 2) {
                    return;
                }
                if (col == COL_ROT) {
                    return;
                }
                ManagedAccountsStore.ManagedAccount acc = rowAt(row);
                if (acc == null) {
                    return;
                }
                if (col == COL_INFO) {
                    AccountStatsGridDialog.show(SwingUtilities.getWindowAncestor(LoneBotAccountsTab.this),
                            acc.displayName);
                    return;
                }
                if (mode == Mode.LAUNCHER) {
                    launchClientsAsync(List.of(acc.copy()));
                } else {
                    onPrepareLogin.accept(acc.copy());
                }
            }

            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    public void refillTable() {
        String preserve = null;
        int sr = table.getSelectedRow();
        if (sr >= 0 && sr < table.getRowCount()) {
            Object u = table.getValueAt(sr, COL_USER);
            preserve = u != null ? u.toString().replace(" [BANNED?]", "") : null;
        }
        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps = AccountStatSnapshotsStore.load();
        tableModel.setRowCount(0);
        boolean hideBanned = cbHideBanned.isSelected();
        for (ManagedAccountsStore.ManagedAccount r : accountRows) {
            if (r == null || r.isEmpty()) {
                continue;
            }
            AccountStatSnapshotsStore.AccountStatSnapshot s =
                    AccountStatSnapshotsStore.snapshotForRow(snaps, r.displayName);
            if (hideBanned && AccountStatSnapshotsStore.isBannedAccount(r, snaps)) {
                continue;
            }
            String user = nullToEmpty(r.displayName);
            if (AccountStatSnapshotsStore.isBannedAccount(r, snaps)) {
                user = user + " [BANNED?]";
            }
            String gp = s != null ? AccountStatSnapshotsStore.formatGp(s.totalGpApprox) : "—";
            String cmb = s != null && s.combatLevel > 0 ? String.valueOf(s.combatLevel) : "—";
            String asd = s != null ? s.attack + "/" + s.strength + "/" + s.defence : "—";
            String wc = s != null && s.woodcutting > 0 ? String.valueOf(s.woodcutting) : "—";
            String mi = s != null && s.mining > 0 ? String.valueOf(s.mining) : "—";
            String fi = s != null && s.fishing > 0 ? String.valueOf(s.fishing) : "—";
            String pr = s != null && s.prayer > 0 ? String.valueOf(s.prayer) : "—";
            String mg = s != null && s.magic > 0 ? String.valueOf(s.magic) : "—";
            String script = r.preferredScript != null && !r.preferredScript.isEmpty() ? r.preferredScript : "NONE";
            String last;
            if (r.jagexUnrankedNew
                    && !com.lonebot.launcher.jagex.HiscoreNameRules.isHiscoreEligibleName(
                            r.displayName, r.characterId)) {
                last = "nieuw";
            } else {
                last = s != null ? AccountStatSnapshotsStore.formatAgo(s.updatedEpochMs) : "—";
            }
            String worlds = r.preferredWorldsCellText();
            if (mode == Mode.LAUNCHER) {
                ClientRemoteBus.Status st = ClientRemoteBus.readStatus(r.displayName);
                String remoteHint = st.shortLabel() + (st.processAlive ? " live" : "");
                String breakLbl = (st.breakLabel != null && !st.breakLabel.isBlank()) ? st.breakLabel : "—";
                tableModel.addRow(new Object[]{
                        r.rotationEnabled, "ℹ", user, gp, cmb, asd, wc, mi, fi, pr, mg, script, last, worlds,
                        breakLbl, remoteHint
                });
            } else {
                tableModel.addRow(new Object[]{
                        r.rotationEnabled, "ℹ", user, gp, cmb, asd, wc, mi, fi, pr, mg, script, last, worlds
                });
            }
        }
        if (preserve != null) {
            for (int i = 0; i < table.getRowCount(); i++) {
                Object u = table.getValueAt(i, COL_USER);
                String name = u != null ? u.toString().replace(" [BANNED?]", "") : "";
                if (preserve.equalsIgnoreCase(name)) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    private ManagedAccountsStore.ManagedAccount rowAt(int tableRow) {
        if (tableRow < 0) {
            return null;
        }
        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps = AccountStatSnapshotsStore.load();
        boolean hideBanned = cbHideBanned.isSelected();
        int i = 0;
        for (ManagedAccountsStore.ManagedAccount r : accountRows) {
            if (r == null || r.isEmpty()) {
                continue;
            }
            AccountStatSnapshotsStore.AccountStatSnapshot s =
                    AccountStatSnapshotsStore.snapshotForRow(snaps, r.displayName);
            if (hideBanned && AccountStatSnapshotsStore.isBannedAccount(r, snaps)) {
                continue;
            }
            if (i == tableRow) {
                return r;
            }
            i++;
        }
        return null;
    }

    private void schedulePersist() {
        if (persistDebounce != null) {
            persistDebounce.stop();
        }
        persistDebounce = new Timer(400, e -> {
            ManagedAccountsStore.save();
            refillTable();
        });
        persistDebounce.setRepeats(false);
        persistDebounce.start();
    }

    private void editSelected() {
        ManagedAccountsStore.ManagedAccount sel = rowAt(table.getSelectedRow());
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Selecteer eerst een rij.", "Bewerken", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (showEditDialog(SwingUtilities.getWindowAncestor(this), sel)) {
            schedulePersist();
            refillTable();
            status.accept("Account bijgewerkt: " + sel.displayName);
            // Live Imp-settings voor dit account opnieuw pushen
            try {
                LoneBotBootstrapPlugin.applyImpSettingsFromUi();
            } catch (Throwable ignored) {
            }
        }
    }

    private void deleteSelected() {
        ManagedAccountsStore.ManagedAccount sel = rowAt(table.getSelectedRow());
        if (sel == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Verwijder \"" + sel.displayName + "\"?",
                "Verwijderen", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        accountRows.remove(sel);
        ManagedAccountsStore.save();
        refillTable();
        status.accept("Account verwijderd");
    }

    private void loginSelected() {
        ManagedAccountsStore.ManagedAccount sel = rowAt(table.getSelectedRow());
        if (sel == null) {
            List<JagexCredentialsHelper.ParsedJagexAccount> list =
                    JagexCredentialsHelper.parsePastedCredentials(pasteArea.getText());
            if (list.isEmpty()) {
                status.accept("⚠ Selecteer account of plak credentials");
                return;
            }
            JagexCredentialsHelper.ParsedJagexAccount p = list.get(0);
            GameAccount ga = p.toGameAccount();
            ManagedAccountsStore.ManagedAccount tmp = new ManagedAccountsStore.ManagedAccount();
            tmp.displayName = p.displayName != null ? p.displayName : "";
            tmp.characterId = ga.getCharacterId() != null ? ga.getCharacterId() : "";
            tmp.sessionId = ga.getSessionId() != null ? ga.getSessionId() : "";
            onPrepareLogin.accept(tmp);
            return;
        }
        fillPasteFrom(sel);
        onPrepareLogin.accept(sel.copy());
    }

    private void closeSelectedClients() {
        List<ManagedAccountsStore.ManagedAccount> targets = accountsForStartOrClose(false);
        if (targets.isEmpty()) {
            status.accept("Vink Selecteer of Ctrl+klik rijen om te sluiten");
            return;
        }
        int n = 0;
        for (ManagedAccountsStore.ManagedAccount a : targets) {
            if (a.displayName != null) {
                ClientRemoteBus.closeClient(a.displayName);
                n++;
            }
        }
        refillTable();
        status.accept("Sluit-cmd gestuurd (" + n + ")");
    }

    /** Kolom Selecteer (vinkjes) ∪ Ctrl/Shift-rijselectie. */
    private List<ManagedAccountsStore.ManagedAccount> accountsForStartOrClose(boolean skipBanned) {
        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps =
                skipBanned ? AccountStatSnapshotsStore.load() : null;
        LinkedHashMap<String, ManagedAccountsStore.ManagedAccount> byKey = new LinkedHashMap<>();
        for (ManagedAccountsStore.ManagedAccount a : checkedAccounts(false)) {
            addIfEligible(byKey, a, skipBanned, snaps);
        }
        int[] rows = table.getSelectedRows();
        if (rows != null) {
            for (int row : rows) {
                addIfEligible(byKey, rowAt(row), skipBanned, snaps);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private static void addIfEligible(LinkedHashMap<String, ManagedAccountsStore.ManagedAccount> byKey,
                                      ManagedAccountsStore.ManagedAccount a,
                                      boolean skipBanned,
                                      Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps) {
        if (a == null || a.isEmpty()) {
            return;
        }
        if (skipBanned && AccountStatSnapshotsStore.isBannedAccount(a, snaps)) {
            return;
        }
        String key = a.displayName != null ? a.displayName.trim().toLowerCase(Locale.ROOT) : ("#" + System.identityHashCode(a));
        if (key.isEmpty()) {
            key = "#" + System.identityHashCode(a);
        }
        byKey.putIfAbsent(key, a);
    }

    /** Alle aangevinkte accounts (kolom Selecteer). */
    private List<ManagedAccountsStore.ManagedAccount> checkedAccounts(boolean skipBanned) {
        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps =
                skipBanned ? AccountStatSnapshotsStore.load() : null;
        List<ManagedAccountsStore.ManagedAccount> list = new ArrayList<>();
        for (ManagedAccountsStore.ManagedAccount a : accountRows) {
            if (a == null || a.isEmpty() || !a.rotationEnabled) {
                continue;
            }
            if (skipBanned && AccountStatSnapshotsStore.isBannedAccount(a, snaps)) {
                continue;
            }
            list.add(a);
        }
        return list;
    }

    /** Alles aan als er nog iets uit staat; anders alles uit. */
    private void toggleSelectAllForStart() {
        boolean anyOff = false;
        int eligible = 0;
        for (ManagedAccountsStore.ManagedAccount a : accountRows) {
            if (a == null || a.isEmpty()) {
                continue;
            }
            eligible++;
            if (!a.rotationEnabled) {
                anyOff = true;
            }
        }
        if (eligible == 0) {
            status.accept("Geen accounts in de lijst");
            return;
        }
        boolean want = anyOff;
        for (ManagedAccountsStore.ManagedAccount a : accountRows) {
            if (a == null || a.isEmpty()) {
                continue;
            }
            a.rotationEnabled = want;
            a.enabled = want;
        }
        refillTable();
        schedulePersist();
        status.accept(want
                ? "Alles geselecteerd (" + eligible + ")"
                : "Alles uitgevinkt (" + eligible + ")");
        importStatus.setText(want ? "Select all: aan" : "Select all: uit");
    }

    private void launchSelectedClients() {
        List<ManagedAccountsStore.ManagedAccount> list = accountsForStartOrClose(true);
        if (list.isEmpty()) {
            status.accept("Vink Selecteer of Ctrl+klik rijen (niet-banned)");
            return;
        }
        if (list.size() > 1) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Start " + list.size() + " client(s) sequentieel?\n"
                            + "(wacht tot elke client klaar is vóór de volgende)",
                    "Start selectie", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
        }
        launchClientsAsync(list);
    }

    private void closeAllLiveClients() {
        int n = 0;
        for (ManagedAccountsStore.ManagedAccount a : accountRows) {
            if (a == null || a.displayName == null) {
                continue;
            }
            if (ClientRemoteBus.isClientAlive(a.displayName)) {
                ClientRemoteBus.closeClient(a.displayName);
                n++;
            }
        }
        refillTable();
        status.accept(n > 0 ? "Alle live clients gesloten (" + n + ")" : "Geen live clients");
    }

    private void remoteStart(ManagedAccountsStore.ManagedAccount a) {
        if (a == null) {
            return;
        }
        if (!ClientRemoteBus.isClientAlive(a.displayName)) {
            status.accept("Client niet open — start eerst de client");
            return;
        }
        ClientRemoteBus.sendCommand(a.displayName, ClientRemoteBus.Command.START);
        status.accept("▶ Start → " + a.displayName);
        refillTable();
    }

    private void remotePause(ManagedAccountsStore.ManagedAccount a) {
        if (a == null) {
            return;
        }
        ClientRemoteBus.sendCommand(a.displayName, ClientRemoteBus.Command.PAUSE);
        status.accept("⏸ Pauze → " + a.displayName);
        refillTable();
    }

    private void remoteStop(ManagedAccountsStore.ManagedAccount a) {
        if (a == null) {
            return;
        }
        ClientRemoteBus.sendCommand(a.displayName, ClientRemoteBus.Command.STOP);
        status.accept("■ Stop → " + a.displayName);
        refillTable();
    }

    private void remoteBreak(ManagedAccountsStore.ManagedAccount a) {
        if (a == null) {
            return;
        }
        if (!ClientRemoteBus.isClientAlive(a.displayName)) {
            status.accept("Client niet open — start eerst de client");
            return;
        }
        ClientRemoteBus.sendCommand(a.displayName, ClientRemoteBus.Command.BREAK_NOW);
        status.accept("☕ Break nu → " + a.displayName);
        refillTable();
    }

    private void remoteCancelBreak(ManagedAccountsStore.ManagedAccount a) {
        if (a == null) {
            return;
        }
        if (!ClientRemoteBus.isClientAlive(a.displayName)) {
            status.accept("Client niet open — start eerst de client");
            return;
        }
        ClientRemoteBus.sendCommand(a.displayName, ClientRemoteBus.Command.CANCEL_BREAK);
        status.accept("✕ Break geannuleerd → " + a.displayName);
        refillTable();
    }

    private void remoteClose(ManagedAccountsStore.ManagedAccount a) {
        if (a == null) {
            return;
        }
        ClientRemoteBus.closeClient(a.displayName);
        status.accept("✕ Sluit → " + a.displayName);
        refillTable();
    }

    /** Mini remote in launcher-tabel: ▶ ⏸ ■ ✕ */
    private final class RemoteCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        private final JButton btnStart = mini("▶", new Color(28, 140, 70), "Start bot");
        private final JButton btnPause = mini("⏸", new Color(150, 110, 30), "Pauze");
        private final JButton btnStop = mini("■", new Color(140, 50, 50), "Stop bot");
        private final JButton btnBreak = mini("☕", new Color(70, 95, 140), "Break nu (uitloggen → pauze → in)");
        private final JButton btnCancelBreak = mini("↩", new Color(120, 70, 50), "Annuleer break");
        private final JButton btnClose = mini("✕", new Color(120, 40, 45), "Sluit client");
        private int editingRow = -1;

        RemoteCell() {
            panel.setOpaque(true);
            panel.add(btnStart);
            panel.add(btnPause);
            panel.add(btnStop);
            panel.add(btnBreak);
            panel.add(btnCancelBreak);
            panel.add(btnClose);
            btnStart.addActionListener(e -> {
                remoteStart(rowAt(editingRow));
                stopCellEditing();
            });
            btnPause.addActionListener(e -> {
                remotePause(rowAt(editingRow));
                stopCellEditing();
            });
            btnStop.addActionListener(e -> {
                remoteStop(rowAt(editingRow));
                stopCellEditing();
            });
            btnBreak.addActionListener(e -> {
                remoteBreak(rowAt(editingRow));
                stopCellEditing();
            });
            btnCancelBreak.addActionListener(e -> {
                remoteCancelBreak(rowAt(editingRow));
                stopCellEditing();
            });
            btnClose.addActionListener(e -> {
                remoteClose(rowAt(editingRow));
                stopCellEditing();
            });
        }

        private JButton mini(String text, Color bg, String tip) {
            JButton b = new JButton(text);
            b.setUI(new BasicButtonUI());
            b.setFont(b.getFont().deriveFont(Font.BOLD, 11f));
            b.setForeground(Color.WHITE);
            b.setBackground(bg);
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            b.setToolTipText(tip);
            b.setMargin(new java.awt.Insets(1, 4, 1, 4));
            return b;
        }

        private void syncEnabled(int row) {
            ManagedAccountsStore.ManagedAccount a = rowAt(row);
            boolean alive = a != null && ClientRemoteBus.isClientAlive(a.displayName);
            btnStart.setEnabled(alive);
            btnPause.setEnabled(alive);
            btnStop.setEnabled(alive);
            btnBreak.setEnabled(alive);
            btnCancelBreak.setEnabled(alive);
            btnClose.setEnabled(alive);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            panel.setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
            syncEnabled(row);
            return panel;
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object value, boolean isSelected, int row, int column) {
            editingRow = row;
            panel.setBackground(t.getSelectionBackground());
            syncEnabled(row);
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    private void launchClientsAsync(List<ManagedAccountsStore.ManagedAccount> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        status.accept("Clients starten (" + list.size() + ")…");
        Thread t = new Thread(() -> {
            LoneBotClientLauncher.LaunchResult r = LoneBotClientLauncher.launchAccounts(list, 2500L);
            SwingUtilities.invokeLater(() -> {
                status.accept(r.message);
                importStatus.setText(r.message);
            });
        }, "lonebot-multi-launch");
        t.setDaemon(true);
        t.start();
    }

    private void refreshHiscoreSelected() {
        ManagedAccountsStore.ManagedAccount sel = rowAt(table.getSelectedRow());
        if (sel == null || sel.displayName == null || sel.displayName.trim().isEmpty()) {
            status.accept("Selecteer een account voor hiscore");
            return;
        }
        final String rsn = sel.displayName.trim();
        status.accept("Hiscore ophalen voor " + rsn + "…");
        Thread t = new Thread(() -> {
            HiscoreBanChecker.Outcome o = HiscoreBanChecker.checkAndPersistOutcome(rsn);
            SwingUtilities.invokeLater(() -> {
                if (o == HiscoreBanChecker.Outcome.SUSPECT_BANNED) {
                    status.accept("⚠ Hiscore 404 — vermoedelijk banned: " + rsn);
                } else if (o == HiscoreBanChecker.Outcome.UNRANKED) {
                    status.accept("geen RSN (e-mail/placeholder) — hiscore overgeslagen: " + rsn);
                } else if (o == HiscoreBanChecker.Outcome.UNCERTAIN) {
                    status.accept("⏳ Hiscore tijdelijk mislukt (netwerk) — ban-flag ongewijzigd: " + rsn);
                } else {
                    AccountStatSnapshotsStore.AccountStatSnapshot snap =
                            AccountStatSnapshotsStore.snapshotForRow(AccountStatSnapshotsStore.load(), rsn);
                    int cmb = snap != null ? snap.combatLevel : 0;
                    status.accept("✓ Hiscore ok: " + rsn + " cmb " + cmb);
                }
                refillTable();
            });
        }, "lonebot-hiscore");
        t.setDaemon(true);
        t.start();
    }

    private void checkAllHiscores(boolean forceAll) {
        status.accept(forceAll ? "Forceer hiscore-check alle accounts…" : "Hiscore-check (stale/nieuw)…");
        HiscoreBanChecker.checkDueAsync(forceAll, r -> SwingUtilities.invokeLater(() -> {
            status.accept(r.message);
            importStatus.setText(r.message);
            refillTable();
        }));
    }

    private void openJagexOauthLogin() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        com.lonebot.launcher.jagex.oauth.JagexOauthLoginDialog dlg =
                new com.lonebot.launcher.jagex.oauth.JagexOauthLoginDialog(owner, status, () -> {
                    ManagedAccountsStore.ensureLoaded();
                    refillTable();
                    selectNewestJagexRows();
                });
        dlg.setVisible(true);
        dlg.startLogin();
    }

    private void openJagexImport() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        com.lonebot.launcher.jagex.JagexImportDialog dlg =
                new com.lonebot.launcher.jagex.JagexImportDialog(owner, status, () -> {
                    ManagedAccountsStore.ensureLoaded();
                    refillTable();
                    selectNewestJagexRows();
                });
        dlg.setVisible(true);
        dlg.startImport();
    }

    private void selectNewestJagexRows() {
        int first = -1;
        int last = -1;
        for (int i = 0; i < table.getRowCount(); i++) {
            ManagedAccountsStore.ManagedAccount a = rowAt(i);
            if (a != null && a.jagexUnrankedNew) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (first >= 0) {
            table.setRowSelectionInterval(first, last);
            table.scrollRectToVisible(table.getCellRect(last, 0, true));
        }
    }

    private void importFromPaste() {
        List<JagexCredentialsHelper.ParsedJagexAccount> parsed =
                JagexCredentialsHelper.parsePastedCredentials(pasteArea.getText());
        if (parsed.isEmpty()) {
            status.accept("⚠ Geen JX_* in plakveld");
            return;
        }
        int added = 0;
        int updated = 0;
        for (JagexCredentialsHelper.ParsedJagexAccount p : parsed) {
            String dn = p.displayName;
            GameAccount ga = p.toGameAccount();
            ManagedAccountsStore.ManagedAccount row = null;
            for (ManagedAccountsStore.ManagedAccount x : accountRows) {
                if (dn != null && dn.equalsIgnoreCase(x.displayName)) {
                    row = x;
                    break;
                }
            }
            if (row == null) {
                row = new ManagedAccountsStore.ManagedAccount();
                accountRows.add(row);
                added++;
            } else {
                updated++;
            }
            row.displayName = dn != null ? dn : row.displayName;
            if (ga.getCharacterId() != null && !ga.getCharacterId().isEmpty()) {
                row.characterId = ga.getCharacterId();
            }
            if (ga.getSessionId() != null && !ga.getSessionId().isEmpty()) {
                row.sessionId = ga.getSessionId();
            }
            row.rotationEnabled = true;
            row.enabled = true;
        }
        ManagedAccountsStore.save();
        refillTable();
        String msg = "Import plak: " + added + " nieuw, " + updated + " bijgewerkt";
        importStatus.setText(msg);
        status.accept(msg);
    }

    private void importStormChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Accounts JSON (Storm of LoneBot export)");
        fc.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
        File desktop = new File(System.getProperty("user.home"), "Desktop");
        if (desktop.isDirectory()) {
            fc.setCurrentDirectory(desktop);
        }
        if (DEFAULT_STORM_JSON.isFile()) {
            fc.setSelectedFile(DEFAULT_STORM_JSON);
        }
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        importStormFile(fc.getSelectedFile());
    }

    private void import2faChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("OSRS 2FA accounts.json (email / pass / secret)");
        fc.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
        File twoFaDir = DEFAULT_2FA_JSON.getParentFile();
        if (twoFaDir != null && twoFaDir.isDirectory()) {
            fc.setCurrentDirectory(twoFaDir);
        } else {
            File desktop = new File(System.getProperty("user.home"), "Desktop");
            if (desktop.isDirectory()) {
                fc.setCurrentDirectory(desktop);
            }
        }
        if (DEFAULT_2FA_JSON.isFile()) {
            fc.setSelectedFile(DEFAULT_2FA_JSON);
        }
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        import2faFile(fc.getSelectedFile());
    }

    private void import2faFile(File file) {
        if (file == null || !file.isFile()) {
            String msg = "Bestand niet gevonden";
            importStatus.setText(msg);
            status.accept(msg);
            JOptionPane.showMessageDialog(this, msg, "2FA JSON import", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            ManagedAccountsStore.ensureLoaded();
            Osrs2faBulkImport.Result res = Osrs2faBulkImport.importFile(
                    file, ManagedAccountsStore.mutableAccounts());
            if (res.fatalError == null) {
                ManagedAccountsStore.save();
            }
            refillTable();
            importStatus.setText(res.summary());
            status.accept("2FA: " + res.summary());
            JOptionPane.showMessageDialog(this, res.summary(), "2FA JSON import",
                    res.fatalError != null ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            String msg = "Lezen mislukt: " + e.getMessage();
            importStatus.setText(msg);
            status.accept(msg);
            JOptionPane.showMessageDialog(this, msg, "2FA JSON import", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copySelectedOtp() {
        ManagedAccountsStore.ManagedAccount sel = rowAt(table.getSelectedRow());
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Selecteer eerst een rij.", "Kopieer OTP",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!TotpHelper.hasSecret(sel.totpSecret)) {
            JOptionPane.showMessageDialog(this,
                    "Geen TOTP-secret voor \"" + nullToEmpty(sel.displayName) + "\".\n"
                            + "Importeer 2FA JSON of vul secret in via Bewerken.",
                    "Kopieer OTP", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String code = TotpHelper.now(sel.totpSecret);
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "OTP kon niet worden berekend (ongeldige secret?).",
                    "Kopieer OTP", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
        status.accept("OTP " + code + " gekopieerd (" + TotpHelper.secondsRemaining() + "s geldig)");
    }

    private void importStormFile(File file) {
        if (file == null || !file.isFile()) {
            String msg = "Bestand niet gevonden";
            importStatus.setText(msg);
            status.accept(msg);
            JOptionPane.showMessageDialog(this, msg, "JSON import", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (AccountPackTransfer.isPack(json)) {
                AccountPackTransfer.Result res = AccountPackTransfer.importJson(json);
                refillTable();
                importStatus.setText(res.summary());
                status.accept(res.summary());
                JOptionPane.showMessageDialog(this, res.summary(), "LoneBot accounts import",
                        res.fatalError != null ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            ManagedAccountsStore.ensureLoaded();
            StormAccountsBulkImport.Result res = StormAccountsBulkImport.importInto(
                    new ArrayList<>(ManagedAccountsStore.getAccounts()), json);
            refillTable();
            importStatus.setText(res.summary());
            status.accept(res.summary());
            JOptionPane.showMessageDialog(this, res.summary(), "Storm JSON import",
                    res.fatalError != null ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            String msg = "Lezen mislukt: " + e.getMessage();
            importStatus.setText(msg);
            status.accept(msg);
            JOptionPane.showMessageDialog(this, msg, "JSON import", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Geselecteerd = kolom Selecteer ∪ gemarkeerde rijen. Leeg → vraag alle accounts.
     */
    private void exportSelectedAccounts() {
        List<ManagedAccountsStore.ManagedAccount> selected = accountsForStartOrClose(false);
        if (selected.isEmpty()) {
            int all = 0;
            for (ManagedAccountsStore.ManagedAccount a : accountRows) {
                if (a != null && !a.isEmpty()) {
                    all++;
                }
            }
            if (all == 0) {
                JOptionPane.showMessageDialog(this, "Geen accounts om te exporteren.",
                        "Export JSON", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this,
                    "Geen vinkjes/rijen geselecteerd.\nAlle " + all + " account(s) exporteren?",
                    "Export JSON", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) {
                return;
            }
            selected = new ArrayList<>();
            for (ManagedAccountsStore.ManagedAccount a : accountRows) {
                if (a != null && !a.isEmpty()) {
                    selected.add(a);
                }
            }
        }
        int warn = JOptionPane.showConfirmDialog(this,
                "Export van " + selected.size() + " account(s).\n"
                        + "Het JSON-bestand bevat Jagex-session tokens — bewaar het privé.\nDoorgaan?",
                "Export JSON", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (warn != JOptionPane.OK_OPTION) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Accounts exporteren");
        fc.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
        File desktop = new File(System.getProperty("user.home"), "Desktop");
        if (desktop.isDirectory()) {
            fc.setCurrentDirectory(desktop);
        }
        fc.setSelectedFile(new File(desktop.isDirectory() ? desktop : new File("."),
                "lonebot-accounts-" + LocalDate.now() + ".json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dest = fc.getSelectedFile();
        if (dest == null) {
            return;
        }
        if (!dest.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
            dest = new File(dest.getParentFile(), dest.getName() + ".json");
        }
        if (dest.isFile()) {
            int ov = JOptionPane.showConfirmDialog(this, dest.getName() + " bestaat al. Overschrijven?",
                    "Export JSON", JOptionPane.YES_NO_OPTION);
            if (ov != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            AccountPackTransfer.Pack pack = AccountPackTransfer.buildPack(selected);
            AccountPackTransfer.writeFile(dest, pack);
            String msg = selected.size() + " account(s) → " + dest.getName()
                    + " (" + pack.files.size() + " extra bestand(en))";
            BotRuntime.logConsole("[Accounts] export " + msg);
            importStatus.setText(msg);
            status.accept(msg);
            JOptionPane.showMessageDialog(this, msg + "\n\nOp de andere pc: Accounts → Storm JSON / kies dit bestand.",
                    "Export JSON", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            String msg = "Export mislukt: " + e.getMessage();
            importStatus.setText(msg);
            status.accept(msg);
            JOptionPane.showMessageDialog(this, msg, "Export JSON", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean showEditDialog(Window parent, ManagedAccountsStore.ManagedAccount r) {
        JDialog dlg = new JDialog(parent, "Account bewerken — " + nullToEmpty(r.displayName),
                Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(12, 12, 12, 12));
        form.setBackground(LoneBotUiTheme.BG_DARK);

        JTextField fName = field(r.displayName);
        JTextField fEmail = field(r.loginEmail);
        JTextField fPass = field(r.loginPassword);
        JTextField fTotp = field(r.totpSecret);
        JTextField fMailCode = field(r.lastMailCode);
        JTextField fChar = field(r.characterId);
        JTextField fSess = field(r.sessionId);
        JTextArea fNotes = new JTextArea(r.notes != null ? r.notes : "", 2, 28);
        fNotes.setLineWrap(true);
        fNotes.setBackground(LoneBotUiTheme.INPUT_BG);
        fNotes.setForeground(LoneBotUiTheme.INPUT_FG);
        JCheckBox cbRot = new JCheckBox("Selecteren om te starten", r.rotationEnabled);
        styleCheck(cbRot);

        JLabel otpPreview = new JLabel(" ");
        otpPreview.setForeground(LoneBotUiTheme.GOLD);
        otpPreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton btnCopyOtp = new JButton("Kopieer OTP");
        Runnable refreshOtp = () -> {
            if (!TotpHelper.hasSecret(fTotp.getText())) {
                otpPreview.setText("Geen geldige TOTP-secret");
                return;
            }
            String code = TotpHelper.now(fTotp.getText());
            if (code.isEmpty()) {
                otpPreview.setText("Secret ongeldig");
            } else {
                otpPreview.setText("OTP: " + code + "  (" + TotpHelper.secondsRemaining() + "s)");
            }
        };
        refreshOtp.run();
        btnCopyOtp.addActionListener(e -> {
            refreshOtp.run();
            String code = TotpHelper.now(fTotp.getText());
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Geen geldige OTP.", "OTP", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
        });
        Timer otpTimer = new Timer(1000, e -> refreshOtp.run());
        otpTimer.start();
        dlg.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                otpTimer.stop();
            }
        });

        JComboBox<String> comboScript = new JComboBox<>(new String[]{
                "NONE", "IMP", "COW", "MONK", "WC", "FISHING", "STAR", "GIANTS", "CLUE", "IMP2",
                "QUEST", "COOKS_ASSISTANT", "GOBLIN_DIPLOMACY", "ROMEO_AND_JULIET",
                "RUNE_MYSTERIES", "DORICS", "VAMPYRE_SLAYER", "TUTORIAL",
                "EXAMPLE", "BANK_TEST", "CITY_TEST"
        });
        ManagedAccountsStore.normalizeSkills(r);
        comboScript.setSelectedItem(r.preferredScript != null ? r.preferredScript : "NONE");
        JCheckBox cbImpSkill = new JCheckBox("Imp Killer aan", r.impKillerEnabled);
        styleCheck(cbImpSkill);
        JCheckBox cbCowSkill = new JCheckBox("Cow combat aan", r.cowCombatEnabled);
        styleCheck(cbCowSkill);
        JCheckBox cbWcSkill = new JCheckBox("Woodcutting aan", r.woodcuttingEnabled);
        styleCheck(cbWcSkill);
        JCheckBox cbFishSkill = new JCheckBox("Fishing aan", r.fishingEnabled);
        styleCheck(cbFishSkill);
        cbImpSkill.addActionListener(e -> {
            if (cbImpSkill.isSelected()) {
                cbCowSkill.setSelected(false);
                cbWcSkill.setSelected(false);
                cbFishSkill.setSelected(false);
                comboScript.setSelectedItem("IMP");
            } else if (!cbCowSkill.isSelected() && !cbWcSkill.isSelected() && !cbFishSkill.isSelected()) {
                comboScript.setSelectedItem("NONE");
            }
        });
        cbCowSkill.addActionListener(e -> {
            if (cbCowSkill.isSelected()) {
                cbImpSkill.setSelected(false);
                cbWcSkill.setSelected(false);
                cbFishSkill.setSelected(false);
                comboScript.setSelectedItem("COW");
            } else if (!cbImpSkill.isSelected() && !cbWcSkill.isSelected() && !cbFishSkill.isSelected()) {
                comboScript.setSelectedItem("NONE");
            }
        });
        cbWcSkill.addActionListener(e -> {
            if (cbWcSkill.isSelected()) {
                cbImpSkill.setSelected(false);
                cbCowSkill.setSelected(false);
                cbFishSkill.setSelected(false);
                comboScript.setSelectedItem("WC");
            } else if (!cbImpSkill.isSelected() && !cbCowSkill.isSelected() && !cbFishSkill.isSelected()) {
                comboScript.setSelectedItem("NONE");
            }
        });
        cbFishSkill.addActionListener(e -> {
            if (cbFishSkill.isSelected()) {
                cbImpSkill.setSelected(false);
                cbCowSkill.setSelected(false);
                cbWcSkill.setSelected(false);
                comboScript.setSelectedItem("FISHING");
            } else if (!cbImpSkill.isSelected() && !cbCowSkill.isSelected() && !cbWcSkill.isSelected()) {
                comboScript.setSelectedItem("NONE");
            }
        });
        comboScript.addActionListener(e -> {
            String s = String.valueOf(comboScript.getSelectedItem());
            if ("IMP".equals(s)) {
                cbImpSkill.setSelected(true);
                cbCowSkill.setSelected(false);
                cbWcSkill.setSelected(false);
                cbFishSkill.setSelected(false);
            } else if ("COW".equals(s)) {
                cbCowSkill.setSelected(true);
                cbImpSkill.setSelected(false);
                cbWcSkill.setSelected(false);
                cbFishSkill.setSelected(false);
            } else if ("WC".equals(s)) {
                cbWcSkill.setSelected(true);
                cbImpSkill.setSelected(false);
                cbCowSkill.setSelected(false);
                cbFishSkill.setSelected(false);
            } else if ("FISHING".equals(s) || "FISH".equals(s)) {
                cbFishSkill.setSelected(true);
                cbImpSkill.setSelected(false);
                cbCowSkill.setSelected(false);
                cbWcSkill.setSelected(false);
            } else if ("MONK".equals(s) || "STAR".equals(s) || "GIANTS".equals(s) || "GIANT".equals(s)
                    || "CLUE".equals(s) || "CLUES".equals(s) || "BEGINNER_CLUE".equals(s)
                    || "IMP2".equals(s) || "IMPS2".equals(s)
                    || LoneBotBotControl.isQuestPreferred(s)
                    || "NONE".equals(s) || "EXAMPLE".equals(s) || "BANK_TEST".equals(s) || "CITY_TEST".equals(s)) {
                // preferredScript-only skills: geen Imp/Cow/WC/Fish-flag
                cbImpSkill.setSelected(false);
                cbCowSkill.setSelected(false);
                cbWcSkill.setSelected(false);
                cbFishSkill.setSelected(false);
            }
        });

        JComboBox<String> comboImpStyle = new JComboBox<>(new String[]{
                "Globaal (Imps-tab)", "MELEE", "RANGED", "MAGE"
        });
        comboImpStyle.setToolTipText(
                "Imps: Globaal = Combat style op Scripts/Imp Killer; anders alleen dit account (MELEE / RANGED / MAGE).");
        String ov = ManagedAccountsStore.normalizeImpsCombatStyle(r.impsCombatStyleOverride);
        if (ov.isEmpty()) {
            comboImpStyle.setSelectedIndex(0);
        } else {
            comboImpStyle.setSelectedItem(ov);
            if (comboImpStyle.getSelectedIndex() < 0) {
                comboImpStyle.setSelectedIndex(0);
            }
        }

        JComboBox<String> comboAsh = new JComboBox<>(new String[]{
                "0% (globaal)", "25%", "50%", "75%", "100%"
        });
        comboAsh.setToolTipText("Kans ashes oppakken voor dit account (0 = Imps-tab ash humanize).");
        int ashPct = r.impsAshLootPickPercent;
        if (ashPct >= 100) {
            comboAsh.setSelectedIndex(4);
        } else if (ashPct >= 75) {
            comboAsh.setSelectedIndex(3);
        } else if (ashPct >= 50) {
            comboAsh.setSelectedIndex(2);
        } else if (ashPct >= 25) {
            comboAsh.setSelectedIndex(1);
        } else {
            comboAsh.setSelectedIndex(0);
        }

        JCheckBox cbMagic = new JCheckBox("Magic auto-update", r.magicAutoUpdate);
        styleCheck(cbMagic);
        cbMagic.setToolTipText("Bij Magic 13+ in Imps MAGE naar Fire Strike upgraden (CombatBot-stijl).");
        JCheckBox cbFarm = new JCheckBox("⏳ Imps farm money", r.impsFarmMoneyEnabled);
        styleCheck(cbFarm);
        cbFarm.setToolTipText("Nog niet: alleen opgeslagen — Imp-handler leest farm-money nog niet");
        cbFarm.setForeground(new java.awt.Color(180, 165, 120));
        JCheckBox cbBankCal = new JCheckBox("⏳ Calibrate bank next login (stub)", r.calibrateBankOnNextLogin);
        styleCheck(cbBankCal);
        cbBankCal.setToolTipText("Nog niet: stub — nog geen bank-calibratie");
        cbBankCal.setForeground(new java.awt.Color(180, 165, 120));

        JTextField fWorld = field(String.join(",",
                r.preferredWorldIds().stream().map(String::valueOf).toArray(String[]::new)));
        if (fWorld.getText().isEmpty() && r.accountSwitchWorld != null) {
            fWorld.setText(r.accountSwitchWorld);
        }
        JCheckBox cbWorldHop = new JCheckBox("Start client op gekozen wereld", r.accountSwitchWorldHopEnabled);
        styleCheck(cbWorldHop);
        JButton btnWorlds = new JButton("Kies werelden…");
        btnWorlds.addActionListener(e -> {
            if (AccountWorldPickerDialog.show(dlg, r)) {
                fWorld.setText(String.join(",",
                        r.preferredWorldIds().stream().map(String::valueOf).toArray(String[]::new)));
                cbWorldHop.setSelected(r.accountSwitchWorldHopEnabled);
            }
        });

        JSpinner spAtk = new JSpinner(new SpinnerNumberModel(Math.max(0, r.targetAttackLevel), 0, 99, 1));
        JSpinner spStr = new JSpinner(new SpinnerNumberModel(Math.max(0, r.targetStrengthLevel), 0, 99, 1));
        JSpinner spDef = new JSpinner(new SpinnerNumberModel(Math.max(0, r.targetDefenceLevel), 0, 99, 1));

        form.add(sectionTitle("Login"));
        form.add(labeled("Gebruikersnaam", fName));
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("E-mail (2FA / Jagex)", fEmail));
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("Wachtwoord", fPass));
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("TOTP secret (Base32)", fTotp));
        form.add(Box.createVerticalStrut(2));
        form.add(otpPreview);
        form.add(btnCopyOtp);
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("Laatste mail-code", fMailCode));
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("Character ID", fChar));
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("Session ID", fSess));
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("Notes", new JScrollPane(fNotes)));
        form.add(Box.createVerticalStrut(4));
        form.add(cbRot);

        form.add(Box.createVerticalStrut(10));
        form.add(sectionTitle("Script / rotatie"));
        form.add(labeled("Preferred script", comboScript));
        form.add(cbImpSkill);
        form.add(cbCowSkill);
        form.add(cbWcSkill);
        form.add(cbFishSkill);
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("Werelden (komma-ID’s of picker)", fWorld));
        form.add(btnWorlds);
        form.add(cbWorldHop);

        form.add(Box.createVerticalStrut(10));
        form.add(sectionTitle("Imp Killer overrides"));
        form.add(labeled("Combat style", comboImpStyle));
        form.add(Box.createVerticalStrut(4));
        form.add(labeled("Ash loot oppak-kans", comboAsh));
        form.add(cbMagic);
        form.add(cbFarm);
        JLabel impHint = new JLabel("<html><i>Opgeslagen in ~/.lonebot/accounts/&lt;RSN&gt;/imps-settings.json</i></html>");
        impHint.setForeground(LoneBotUiTheme.TEXT_DIM);
        impHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(impHint);

        form.add(Box.createVerticalStrut(10));
        form.add(sectionTitle("Melee targets (stub tot trainer)"));
        form.add(labeled("Target Attack", spAtk));
        form.add(labeled("Target Strength", spStr));
        form.add(labeled("Target Defence", spDef));
        form.add(cbBankCal);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton ok = new JButton("Opslaan");
        JButton cancel = new JButton("Annuleren");
        buttons.add(ok);
        buttons.add(cancel);
        form.add(Box.createVerticalStrut(10));
        form.add(buttons);

        final boolean[] saved = {false};
        ok.addActionListener(e -> {
            String name = fName.getText() != null ? fName.getText().trim() : "";
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Gebruikersnaam mag niet leeg zijn.", "Bewerken", JOptionPane.WARNING_MESSAGE);
                return;
            }
            r.displayName = name;
            r.loginEmail = fEmail.getText() != null ? fEmail.getText().trim() : "";
            r.loginPassword = fPass.getText() != null ? fPass.getText().trim() : "";
            r.totpSecret = TotpHelper.normalizeSecret(fTotp.getText());
            r.lastMailCode = fMailCode.getText() != null ? fMailCode.getText().trim() : "";
            r.characterId = fChar.getText() != null ? fChar.getText().trim() : "";
            r.sessionId = fSess.getText() != null ? fSess.getText().trim() : "";
            r.notes = fNotes.getText() != null ? fNotes.getText().trim() : "";
            r.rotationEnabled = cbRot.isSelected();
            r.enabled = r.rotationEnabled;
            r.preferredScript = String.valueOf(comboScript.getSelectedItem());
            r.impKillerEnabled = cbImpSkill.isSelected();
            r.cowCombatEnabled = cbCowSkill.isSelected() && !r.impKillerEnabled;
            r.woodcuttingEnabled = cbWcSkill.isSelected() && !r.impKillerEnabled && !r.cowCombatEnabled;
            r.fishingEnabled = cbFishSkill.isSelected()
                    && !r.impKillerEnabled && !r.cowCombatEnabled && !r.woodcuttingEnabled;
            String pref = r.preferredScript != null ? r.preferredScript.trim().toUpperCase() : "NONE";
            if ("MONK".equals(pref) || "STAR".equals(pref) || "GIANTS".equals(pref) || "GIANT".equals(pref)
                    || "CLUE".equals(pref) || "CLUES".equals(pref) || "BEGINNER_CLUE".equals(pref)
                    || "IMP2".equals(pref) || "IMPS2".equals(pref)
                    || LoneBotBotControl.isQuestPreferred(pref)
                    || "BANK_TEST".equals(pref) || "CITY_TEST".equals(pref)) {
                r.impKillerEnabled = false;
                r.cowCombatEnabled = false;
                r.woodcuttingEnabled = false;
                r.fishingEnabled = false;
            }
            ManagedAccountsStore.normalizeSkills(r);
            int styleIdx = comboImpStyle.getSelectedIndex();
            if (styleIdx <= 0) {
                r.impsCombatStyleOverride = "";
            } else {
                r.impsCombatStyleOverride = ManagedAccountsStore.normalizeImpsCombatStyle(
                        String.valueOf(comboImpStyle.getSelectedItem()));
            }
            int ashIdx = comboAsh.getSelectedIndex();
            r.impsAshLootPickPercent = ashIdx <= 0 ? 0 : (ashIdx == 1 ? 25 : ashIdx == 2 ? 50 : ashIdx == 3 ? 75 : 100);
            r.magicAutoUpdate = cbMagic.isSelected();
            r.impsFarmMoneyEnabled = cbFarm.isSelected();
            r.calibrateBankOnNextLogin = cbBankCal.isSelected();
            r.setPreferredWorldIds(ManagedAccountsStore.ManagedAccount.parseWorldIds(
                    fWorld.getText() != null ? fWorld.getText().trim() : ""));
            r.accountSwitchWorldHopEnabled = cbWorldHop.isSelected() && !r.preferredWorldIds().isEmpty();
            r.targetAttackLevel = (Integer) spAtk.getValue();
            r.targetStrengthLevel = (Integer) spStr.getValue();
            r.targetDefenceLevel = (Integer) spDef.getValue();
            AccountImpsSettingsStore.writeFromManaged(r);
            saved[0] = true;
            dlg.dispose();
        });
        cancel.addActionListener(e -> dlg.dispose());

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        dlg.setContentPane(scroll);
        dlg.setSize(460, 780);
        dlg.setMinimumSize(new Dimension(380, 400));
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
        return saved[0];
    }

    private static JLabel sectionTitle(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(LoneBotUiTheme.GOLD);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        return l;
    }

    private static void styleCheck(JCheckBox cb) {
        LoneBotUiTheme.styleCheck(cb);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static JPanel labeled(String title, Component c) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(title);
        l.setForeground(LoneBotUiTheme.TEXT);
        p.add(l, BorderLayout.NORTH);
        if (c instanceof JComponent) {
            ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            ((JComponent) c).setMaximumSize(new Dimension(Integer.MAX_VALUE, ((JComponent) c).getPreferredSize().height + 8));
        }
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static JTextField field(String v) {
        JTextField f = new JTextField(v != null ? v : "", 28);
        f.setBackground(LoneBotUiTheme.INPUT_BG);
        f.setForeground(LoneBotUiTheme.INPUT_FG);
        f.setCaretColor(LoneBotUiTheme.INPUT_FG);
        return f;
    }

    private static JButton smallBtn(String text, Color bg) {
        JButton b = new JButton(text);
        styleActionButton(b, bg, 11f, false);
        return b;
    }

    private static JButton controlBtn(String text, Color bg) {
        JButton b = new JButton(text);
        styleActionButton(b, bg, 11f, true);
        b.setPreferredSize(new Dimension(90, 32));
        return b;
    }

    /**
     * Tabel + panel-achtergronden volgens actief {@link LoneBotUiTheme}.
     * Actieknoppen (Start/Stop/…) blijven semantisch gekleurd.
     */
    void applyTheme() {
        setBackground(LoneBotUiTheme.BG_DARK);
        if (northPanel != null) {
            northPanel.setBackground(LoneBotUiTheme.BG_DARK);
        }
        if (southPanel != null) {
            southPanel.setBackground(LoneBotUiTheme.BG_DARK);
            for (Component c : southPanel.getComponents()) {
                if (c instanceof JPanel) {
                    c.setBackground(LoneBotUiTheme.BG_DARK);
                }
            }
        }
        if (controlBar != null) {
            controlBar.setBackground(LoneBotUiTheme.BG_SECTION);
            controlBar.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(LoneBotUiTheme.BORDER, 1, true),
                    new EmptyBorder(10, 12, 10, 12)));
            for (Component c : controlBar.getComponents()) {
                applyThemeRecursiveLabels(c);
            }
        }
        applyThemeTableColors();
        cbHideBanned.setForeground(LoneBotUiTheme.TEXT);
        importStatus.setForeground(LoneBotUiTheme.TEXT_DIM);
        if (pasteLbl != null) {
            pasteLbl.setForeground(LoneBotUiTheme.TEXT);
        }
        pasteArea.setBackground(LoneBotUiTheme.INPUT_BG);
        pasteArea.setForeground(LoneBotUiTheme.INPUT_FG);
        pasteArea.setCaretColor(LoneBotUiTheme.INPUT_FG);
        if (pasteScroll != null) {
            pasteScroll.setBackground(LoneBotUiTheme.BG_DARK);
            pasteScroll.getViewport().setBackground(LoneBotUiTheme.INPUT_BG);
        }
        if (botStatusLbl != null && botStatusLbl.getText() != null && botStatusLbl.getText().contains("Uit")) {
            botStatusLbl.setForeground(LoneBotUiTheme.TEXT_DIM);
        }
        if (mode == Mode.LAUNCHER && LauncherGlass.isOn()) {
            LauncherGlass.apply(this, true);
        }
        revalidate();
        repaint();
    }

    private void applyThemeRecursiveLabels(Component c) {
        if (c instanceof JLabel) {
            JLabel l = (JLabel) c;
            if (Boolean.TRUE.equals(l.getClientProperty("lonebot.accountsHeadline"))) {
                l.setForeground(LoneBotUiTheme.GOLD);
            }
        }
        if (c instanceof Container) {
            for (Component ch : ((Container) c).getComponents()) {
                applyThemeRecursiveLabels(ch);
            }
        }
    }

    private void applyThemeTableColors() {
        if (table == null) {
            return;
        }
        table.setBackground(LoneBotUiTheme.BG_SECTION);
        table.setForeground(LoneBotUiTheme.TEXT);
        table.setSelectionBackground(LoneBotUiTheme.TAB_SELECTED);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(LoneBotUiTheme.BORDER);
        applyThemedTableHeader(table);
        if (tableScroll != null) {
            tableScroll.setBorder(BorderFactory.createLineBorder(LoneBotUiTheme.BORDER));
            tableScroll.setBackground(LoneBotUiTheme.BG_DARK);
            tableScroll.getViewport().setBackground(LoneBotUiTheme.BG_SECTION);
        }
        table.repaint();
        if (table.getTableHeader() != null) {
            table.getTableHeader().repaint();
        }
    }

    private static void applyThemedTableHeader(JTable table) {
        if (table.getTableHeader() == null) {
            return;
        }
        table.getTableHeader().setBackground(LoneBotUiTheme.BG_HEADER);
        table.getTableHeader().setForeground(LoneBotUiTheme.TEXT);
        table.getTableHeader().setOpaque(true);
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                          boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setOpaque(true);
                c.setBackground(LoneBotUiTheme.BG_HEADER);
                c.setForeground(LoneBotUiTheme.TEXT);
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                c.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, LoneBotUiTheme.BORDER),
                        new EmptyBorder(4, 4, 4, 4)));
                return c;
            }
        });
    }

    private static void applyDarkTableHeader(JTable table) {
        applyThemedTableHeader(table);
    }

    /**
     * Windows L&F negeert vaak setBackground — forceer opaque + border zodat tekst/kleur leesbaar is.
     */
    private static void styleActionButton(JButton b, Color bg, float fontSize, boolean bold) {
        Color fill = bg != null ? bg : new Color(55, 70, 100);
        Color border = brighter(fill, 1.35f);
        b.setUI(new BasicButtonUI());
        b.setFont(b.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, fontSize));
        b.setForeground(Color.WHITE);
        b.setBackground(fill);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1),
                new EmptyBorder(5, 10, 5, 10)));
        b.addChangeListener(e -> {
            if (b.getModel().isPressed()) {
                b.setBackground(darker(fill, 0.82f));
            } else if (b.getModel().isRollover()) {
                b.setBackground(brighter(fill, 1.18f));
            } else {
                b.setBackground(fill);
            }
            b.setForeground(Color.WHITE);
        });
    }

    private static Color brighter(Color c, float factor) {
        int r = Math.min(255, Math.round(c.getRed() * factor));
        int g = Math.min(255, Math.round(c.getGreen() * factor));
        int b = Math.min(255, Math.round(c.getBlue() * factor));
        return new Color(r, g, b);
    }

    private static Color darker(Color c, float factor) {
        int r = Math.max(0, Math.round(c.getRed() * factor));
        int g = Math.max(0, Math.round(c.getGreen() * factor));
        int b = Math.max(0, Math.round(c.getBlue() * factor));
        return new Color(r, g, b);
    }

    private void fillPasteFrom(ManagedAccountsStore.ManagedAccount a) {
        pasteArea.setText("JX_DISPLAY_NAME=" + nullToEmpty(a.displayName) + "\n"
                + "JX_CHARACTER_ID=" + nullToEmpty(a.characterId) + "\n"
                + "JX_SESSION_ID=" + nullToEmpty(a.sessionId) + "\n");
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
