package net.runelite.client.plugins.lonebot;

import net.runelite.client.ui.ColorScheme;
import net.storm.sdk.bot.BotRuntime;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

/**
 * Live ster-lijst (wereld + locatie, nieuwste eerst) voor sidebar én Control-tab.
 * Groen = deze account mag erheen; rood = geblokkeerd (skill-total, te hoog, …).
 */
final class LoneBotStarFeedView extends JPanel {

    private final JLabel header = new JLabel("Live sterren");
    private final JTextPane area = new JTextPane();
    private String lastHtml = "";

    LoneBotStarFeedView() {
        setLayout(new BorderLayout(0, 4));
        setOpaque(true);
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(4, 8, 8, 8));
        setAlignmentX(LEFT_ALIGNMENT);

        header.setForeground(ColorScheme.BRAND_ORANGE);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        add(header, BorderLayout.NORTH);

        area.setEditable(false);
        area.setOpaque(true);
        area.setBackground(new Color(30, 30, 30));
        area.setForeground(Color.WHITE);
        area.setCaretColor(Color.WHITE);
        area.setBorder(new EmptyBorder(6, 8, 6, 8));
        area.setFocusable(false);
        area.setContentType("text/html");
        HTMLEditorKit kit = new HTMLEditorKit();
        area.setEditorKit(kit);
        StyleSheet ss = kit.getStyleSheet();
        ss.addRule("body{font-family:monospace;font-size:12pt;color:#fff;background:#1e1e1e;margin:0;}");
        ss.addRule(".ok{color:#7cfc00;}");
        ss.addRule(".no{color:#ff5555;}");
        ss.addRule(".hint{color:#aaa;}");

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(62, 62, 62)));
        scroll.setPreferredSize(new Dimension(240, 168));
        scroll.setMinimumSize(new Dimension(120, 120));
        scroll.getViewport().setBackground(new Color(30, 30, 30));
        add(scroll, BorderLayout.CENTER);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        setPreferredSize(new Dimension(240, 200));

        Timer t = new Timer(1000, e -> refresh());
        t.setRepeats(true);
        t.start();
        refresh();
    }

    private void refresh() {
        List<String> lines = BotRuntime.starFeedLines;
        List<Boolean> okFlags = BotRuntime.starFeedVisitOk;
        int n = lines != null ? lines.size() : BotRuntime.starFeedCount;
        int green = 0;
        if (okFlags != null) {
            for (Boolean b : okFlags) {
                if (Boolean.TRUE.equals(b)) {
                    green++;
                }
            }
        }
        header.setText("Live sterren · " + n + " · groen " + green + " / rood " + Math.max(0, n - green));
        String html;
        if (lines == null || lines.isEmpty()) {
            html = "<html><body><span class=hint>Nog geen sterren in de feed.<br>"
                    + "Discord, osrsportal en 07.gg elke ~20s.</span></body></html>";
        } else {
            StringBuilder sb = new StringBuilder(256);
            sb.append("<html><body>");
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) {
                    continue;
                }
                boolean ok = okFlags != null && i < okFlags.size() && Boolean.TRUE.equals(okFlags.get(i));
                sb.append("<div><font color=\"").append(ok ? "#7cfc00" : "#ff5555").append("\">")
                        .append(esc(line)).append("</font></div>");
            }
            sb.append("</body></html>");
            html = sb.toString();
        }
        if (!html.equals(lastHtml)) {
            lastHtml = html;
            area.setText(html);
            area.setCaretPosition(0);
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
