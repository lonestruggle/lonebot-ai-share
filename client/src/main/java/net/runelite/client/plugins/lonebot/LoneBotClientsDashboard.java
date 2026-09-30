package net.runelite.client.plugins.lonebot;

import com.lonebot.launcher.ClientsUiSettings;
import com.lonebot.launcher.LauncherGlass;
import com.lonebot.launcher.embed.DwmThumbnailPanel;
import com.lonebot.launcher.embed.WindowEmbedHelper;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Launcher Clients-tab: live DWM-previews van alle clients + schaal-slider.
 * Geen samenvoegen/embed — clients blijven normale vensters.
 */
public final class LoneBotClientsDashboard extends JPanel {

    private final Consumer<String> statusSink;
    private final JPanel mosaic = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
    private final JScrollPane scroll;
    private final JLabel scaleLbl = new JLabel();
    private final JLabel emptyLbl = new JLabel("Geen live clients — start accounts via Accounts-tab", SwingConstants.CENTER);
    private final Map<String, ClientCard> cards = new LinkedHashMap<>();
    private final Timer refreshTimer;
    private int scalePercent = ClientsUiSettings.getMiniScalePercent();

    public LoneBotClientsDashboard(Consumer<String> statusSink) {
        this.statusSink = statusSink != null ? statusSink : s -> {};
        setLayout(new BorderLayout(0, 8));
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel hint = new JLabel(WindowEmbedHelper.isWindows()
                ? "<html><b>Live previews</b> (DWM) van alle clients. Schaal = vergroot/verklein. "
                + "Clients blijven losse vensters.</html>"
                : "<html>Live preview alleen op Windows.</html>");
        hint.setForeground(new Color(180, 185, 200));

        JPanel scaleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        scaleRow.setOpaque(false);
        JLabel scaleTitle = new JLabel("Preview schaal:");
        scaleTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        JSlider scaleSlider = new JSlider(100, 300, scalePercent);
        scaleSlider.setPreferredSize(new Dimension(200, 24));
        scaleSlider.setOpaque(false);
        scaleSlider.setMajorTickSpacing(50);
        scaleSlider.setPaintTicks(true);
        updateScaleLabel();
        scaleSlider.addChangeListener(e -> {
            scalePercent = ClientsUiSettings.clamp(scaleSlider.getValue());
            updateScaleLabel();
            if (!scaleSlider.getValueIsAdjusting()) {
                ClientsUiSettings.setMiniScalePercent(scalePercent);
                applyScaleToAll();
            }
        });
        scaleRow.add(scaleTitle);
        scaleRow.add(scaleSlider);
        scaleRow.add(scaleLbl);

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        north.add(hint, BorderLayout.NORTH);
        north.add(scaleRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        mosaic.setOpaque(true);
        mosaic.setBackground(new Color(30, 32, 40));
        scroll = new JScrollPane(mosaic);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 80)),
                "Live clients"));
        scroll.getViewport().setBackground(new Color(30, 32, 40));
        add(scroll, BorderLayout.CENTER);

        emptyLbl.setForeground(new Color(150, 155, 170));

        refreshTimer = new Timer(1000, e -> refresh());
        refreshTimer.setRepeats(true);
        refreshTimer.start();
        SwingUtilities.invokeLater(this::refresh);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (LauncherGlass.isOn()) {
            LauncherGlass.paintTint(g, getWidth(), getHeight());
        }
        super.paintComponent(g);
    }

    public void selectClientTab(String displayName) {
        // geen apart venster meer
    }

    public void releaseAllEmbeddings() {
        refreshTimer.stop();
        for (ClientCard c : new ArrayList<>(cards.values())) {
            c.dispose();
        }
        cards.clear();
        mosaic.removeAll();
    }

    private void updateScaleLabel() {
        int w = ClientsUiSettings.miniWidth(scalePercent);
        int h = ClientsUiSettings.miniHeight(scalePercent);
        scaleLbl.setForeground(new Color(180, 185, 200));
        scaleLbl.setText(scalePercent + "%  (" + w + "×" + h + ")");
    }

    private void applyScaleToAll() {
        int w = ClientsUiSettings.miniWidth(scalePercent);
        int h = ClientsUiSettings.miniHeight(scalePercent);
        for (ClientCard c : cards.values()) {
            c.applySize(w, h);
        }
        mosaic.revalidate();
        mosaic.repaint();
    }

    private void refresh() {
        // Andere tab geselecteerd → alle DWM-previews uit (niet laten hangen op Accounts)
        if (!isShowing()) {
            hideAllPreviews();
            return;
        }

        ManagedAccountsStore.ensureLoaded();
        ClientRemoteBus.purgeStaleStatuses();
        Set<String> live = ClientRemoteBus.listLiveDisplayNames();

        for (String k : new ArrayList<>(cards.keySet())) {
            boolean still = false;
            for (String n : live) {
                if (key(n).equals(k)) {
                    still = true;
                    break;
                }
            }
            if (!still) {
                ClientCard c = cards.remove(k);
                if (c != null) {
                    mosaic.remove(c);
                    c.dispose();
                }
            }
        }

        for (String name : live) {
            String k = key(name);
            if (!cards.containsKey(k)) {
                ClientCard card = new ClientCard(name);
                cards.put(k, card);
                mosaic.add(card);
                int w = ClientsUiSettings.miniWidth(scalePercent);
                int h = ClientsUiSettings.miniHeight(scalePercent);
                card.applySize(w, h);
            }
        }

        for (ClientCard c : cards.values()) {
            c.refresh();
        }

        if (cards.isEmpty()) {
            mosaic.removeAll();
            mosaic.add(emptyLbl);
        }

        mosaic.revalidate();
        mosaic.repaint();
        if (LauncherGlass.isOn()) {
            LauncherGlass.apply(this, true);
        }
    }

    /** Zet alle DWM-thumbnails uit (bij tab-wissel). */
    public void hideAllPreviews() {
        for (ClientCard c : cards.values()) {
            c.preview.setCaptureEnabled(false);
        }
    }

    public void showAllPreviews() {
        for (ClientCard c : cards.values()) {
            c.preview.setCaptureEnabled(true);
            c.preview.refreshSource();
        }
    }

    private static String key(String name) {
        return name != null ? name.trim().toLowerCase() : "";
    }

    private final class ClientCard extends JPanel {
        private final String displayName;
        private final DwmThumbnailPanel preview;
        private final JLabel title = new JLabel("", SwingConstants.CENTER);
        private final JCheckBox cbImp;
        private final JCheckBox cbCow;
        private final JCheckBox cbWc;
        private final JCheckBox cbFish;

        ClientCard(String displayName) {
            this.displayName = displayName;
            setLayout(new BorderLayout(0, 4));
            setOpaque(true);
            setBackground(new Color(40, 42, 52));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(70, 75, 95)),
                    new EmptyBorder(6, 6, 6, 6)));

            title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
            add(title, BorderLayout.NORTH);

            preview = new DwmThumbnailPanel(displayName);
            add(preview, BorderLayout.CENTER);

            JPanel south = new JPanel();
            south.setLayout(new BorderLayout(0, 2));
            south.setOpaque(false);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
            btns.setOpaque(false);
            JButton start = new JButton("▶");
            JButton pause = new JButton("⏸");
            JButton stop = new JButton("■");
            JButton brk = new JButton("☕");
            JButton cancelBrk = new JButton("↩");
            JButton close = new JButton("✕");
            start.setToolTipText("Start");
            pause.setToolTipText("Pause");
            stop.setToolTipText("Stop");
            brk.setToolTipText("Break nu");
            cancelBrk.setToolTipText("Annuleer break");
            close.setToolTipText("Close");
            start.addActionListener(e -> remote(ClientRemoteBus.Command.START));
            pause.addActionListener(e -> remote(ClientRemoteBus.Command.PAUSE));
            stop.addActionListener(e -> remote(ClientRemoteBus.Command.STOP));
            brk.addActionListener(e -> remote(ClientRemoteBus.Command.BREAK_NOW));
            cancelBrk.addActionListener(e -> remote(ClientRemoteBus.Command.CANCEL_BREAK));
            close.addActionListener(e -> remote(ClientRemoteBus.Command.CLOSE));
            btns.add(start);
            btns.add(pause);
            btns.add(stop);
            btns.add(brk);
            btns.add(cancelBrk);
            btns.add(close);
            south.add(btns, BorderLayout.NORTH);

            ManagedAccountsStore.ManagedAccount row = ManagedAccountsStore.findByDisplayName(displayName);
            if (row != null) {
                ManagedAccountsStore.normalizeSkills(row);
            }
            cbImp = new JCheckBox("Imp", row != null && row.impKillerEnabled);
            cbCow = new JCheckBox("Cow", row != null && row.cowCombatEnabled);
            cbWc = new JCheckBox("WC", row != null && row.woodcuttingEnabled);
            cbFish = new JCheckBox("Fish", row != null && row.fishingEnabled);
            styleCheck(cbImp);
            styleCheck(cbCow);
            styleCheck(cbWc);
            styleCheck(cbFish);
            JPanel skills = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            skills.setOpaque(false);
            skills.add(cbImp);
            skills.add(cbCow);
            skills.add(cbWc);
            skills.add(cbFish);
            cbImp.addActionListener(e -> {
                boolean on = cbImp.isSelected();
                if (on) {
                    cbCow.setSelected(false);
                    cbWc.setSelected(false);
                    cbFish.setSelected(false);
                }
                ManagedAccountsStore.setSkillEnabled(displayName, on, cbCow.isSelected(),
                        cbWc.isSelected(), cbFish.isSelected());
                remote(ClientRemoteBus.Command.START);
            });
            cbCow.addActionListener(e -> {
                boolean on = cbCow.isSelected();
                if (on) {
                    cbImp.setSelected(false);
                    cbWc.setSelected(false);
                    cbFish.setSelected(false);
                }
                ManagedAccountsStore.setSkillEnabled(displayName, cbImp.isSelected(), on,
                        cbWc.isSelected(), cbFish.isSelected());
                remote(ClientRemoteBus.Command.START);
            });
            cbWc.addActionListener(e -> {
                boolean on = cbWc.isSelected();
                if (on) {
                    cbImp.setSelected(false);
                    cbCow.setSelected(false);
                    cbFish.setSelected(false);
                }
                ManagedAccountsStore.setSkillEnabled(displayName, cbImp.isSelected(), cbCow.isSelected(),
                        on, cbFish.isSelected());
                remote(ClientRemoteBus.Command.START);
            });
            cbFish.addActionListener(e -> {
                boolean on = cbFish.isSelected();
                if (on) {
                    cbImp.setSelected(false);
                    cbCow.setSelected(false);
                    cbWc.setSelected(false);
                }
                ManagedAccountsStore.setSkillEnabled(displayName, cbImp.isSelected(), cbCow.isSelected(),
                        cbWc.isSelected(), on);
                remote(ClientRemoteBus.Command.START);
            });
            south.add(skills, BorderLayout.SOUTH);
            add(south, BorderLayout.SOUTH);

            refresh();
        }

        void applySize(int w, int h) {
            preview.setTargetSize(w, h);
            setPreferredSize(new Dimension(w + 16, h + 72));
            revalidate();
        }

        void refresh() {
            ClientRemoteBus.Status st = ClientRemoteBus.readStatus(displayName);
            String state = st != null ? st.shortLabel() + " " + st.state : "?";
            title.setText(displayName + "  " + state);
            preview.setCaptureEnabled(true);
            preview.refreshSource();

            ManagedAccountsStore.ManagedAccount row = ManagedAccountsStore.findByDisplayName(displayName);
            if (row != null) {
                ManagedAccountsStore.normalizeSkills(row);
                if (cbImp.isSelected() != row.impKillerEnabled) {
                    cbImp.setSelected(row.impKillerEnabled);
                }
                if (cbCow.isSelected() != row.cowCombatEnabled) {
                    cbCow.setSelected(row.cowCombatEnabled);
                }
                if (cbWc.isSelected() != row.woodcuttingEnabled) {
                    cbWc.setSelected(row.woodcuttingEnabled);
                }
                if (cbFish.isSelected() != row.fishingEnabled) {
                    cbFish.setSelected(row.fishingEnabled);
                }
            }
        }

        void dispose() {
            preview.disposePanel();
        }

        private void remote(ClientRemoteBus.Command cmd) {
            ClientRemoteBus.sendCommand(displayName, cmd);
            statusSink.accept(displayName + " → " + cmd);
        }
    }

    private static void styleCheck(JCheckBox cb) {
        LoneBotUiTheme.styleCheck(cb);
    }
}
