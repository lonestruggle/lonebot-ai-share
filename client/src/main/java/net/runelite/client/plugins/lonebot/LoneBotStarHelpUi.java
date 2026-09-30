package net.runelite.client.plugins.lonebot;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;

/**
 * Star Miner-uitleg: Control Star-tab (onder overlay) + sidebar.
 */
final class LoneBotStarHelpUi {

    static final String PLAIN = ""
            + "Hoe Star Miner werkt\n"
            + "\n"
            + "Kies Star Miner in de script-dropdown en druk Start. "
            + "Start je al bij een ster: eerst kijken of je hem kunt minen; zo niet, dan wacht-extra of een andere ster.\n"
            + "\n"
            + "1. Feed — Discord (optioneel, ~/.lonebot/star-feed.properties), "
            + "OSRS Portal, 07.gg (https://07.gg/trackers/shooting-star), en optioneel JSON. Elke ~20s. "
            + "Groen = jij mag erheen; rood = F2P-blokkade (skill-total, te hoog, quest). "
            + "PvP-werelden staan er niet in. Alleen F2P aan: members uit de lijst; uitvinken toont ze weer.\n"
            + "2. Kiezen — voorkeur voor een ster die je nu kunt minen. "
            + "Filters: geen wilderness, alleen F2P (plek + wereld).\n"
            + "3. Gear — dichtstbijzijnde F2P-bank, beste pickaxe die jouw Mining aankan, rest storten. Geen GE-koop.\n"
            + "4. Hop + loop — hop naar de wereld (Switch world bevestigen), hopper dicht, lopen zoals webwalk.\n"
            + "5. Mine — alleen klikken als de live laag bekend is én jouw Mining die aankan. "
            + "Onbekend: eerst Prospect. Te hoog: Prospect af en toe, niet Mine. "
            + "Health: NPC-balk (o.a. id 10629) + Prospect-chat (mined % naar volgende laag). "
            + "Ster weg / lege plek → volgende. Geen minebare ster → uitloggen.\n"
            + "\n"
            + "Opties\n"
            + "\n"
            + "• Geen wilderness — skip wildy-locaties.\n"
            + "• Alleen F2P — hop + lijst: geen members-werelden/locaties. Uitvinken toont members weer.\n"
            + "• World hop — hop naar de wereld van de gekozen ster.\n"
            + "• Wacht extra lagen (0–8) — hoeveel lagen boven jouw minebare tier je nog bezoekt:\n"
            + "    Binnen die cap: hoogste laag die je nu kunt minen; geen minebare → dichtst bij jouw lvl wachten (T5 → T6, niet T8).\n"
            + "    0 = alleen sterren die je nu kunt minen; te hoge blijven in de lijst, bot gaat er niet heen.\n"
            + "    1 = je kunt T5 → wel naar T6 wachten, niet T7+.\n"
            + "    8 = ook naar T9 en wachten.\n"
            + "• Tijdens wachten — bij een te hoge ster binnen die cap: niets, High Alchemy, "
            + "mine in de buurt, of woodcut in de buurt (bijl meenemen).\n"
            + "  High Alch: Magic 55 + nature rune + fire runes of fire staff. "
            + "Elke tick kijkt de bot of de ster nu wel minebaar is (laag gezakt of mining omhoog); zo ja → Mine, alch stopt.\n"
            + "  Wel alchen: noted junk eerst, daarna sieraden e.d. (gold necklace).\n"
            + "  Niet alchen: coins, eten/pots, teleport-tabs, essence, stardust, clues, lamps, "
            + "uncut gems/geodes, pickaxe, hatchet, fire staff, nature/fire (en andere) runes, pouches.\n"
            + "• JSON-feed URL — extra GET-JSON naast Discord/Portal (sidebar).\n"
            + "• Star overlay — status, feed en doel op het spelcanvas.";

    private static final String HTML = "<html><body style='font-family:Dialog;font-size:11px;color:#d0d0d0;margin:6px'>"
            + "<b style='color:#ff981f'>Hoe Star Miner werkt</b><br><br>"
            + "Kies <b>Star Miner</b> in de script-dropdown en druk Start. "
            + "Start je al bij een ster: eerst kijken of je hem kunt minen; zo niet, dan wacht-extra of een andere ster.<br><br>"
            + "<b>1. Feed</b> — Discord (optioneel, <code>~/.lonebot/star-feed.properties</code>), "
            + "OSRS Portal, 07.gg, en optioneel JSON. Elke ~20s. "
            + "Groen = jij mag erheen; rood = F2P-blokkade (skill-total, te hoog, quest). "
            + "PvP-werelden staan er niet in. Alleen F2P aan: members uit de lijst; uitvinken toont ze weer.<br>"
            + "<b>2. Kiezen</b> — voorkeur voor een ster die je nu kunt minen. "
            + "Filters: geen wilderness, alleen F2P (plek + wereld).<br>"
            + "<b>3. Gear</b> — dichtstbijzijnde F2P-bank, beste pickaxe die jouw Mining aankan, rest storten. "
            + "Geen GE-koop.<br>"
            + "<b>4. Hop + loop</b> — hop naar de wereld (Switch world bevestigen), hopper dicht, lopen zoals webwalk.<br>"
            + "<b>5. Mine</b> — alleen klikken als de live laag bekend is én jouw Mining die aankan. "
            + "Onbekend: eerst Prospect. Te hoog: Prospect af en toe, niet Mine. "
            + "Health: NPC-balk (o.a. id 10629) + Prospect-chat. "
            + "Ster weg / lege plek → volgende. "
            + "Geen minebare ster → uitloggen.<br><br>"
            + "<b style='color:#ff981f'>Opties</b><br><br>"
            + "<b>Geen wilderness</b> — skip wildy-locaties.<br>"
            + "<b>Alleen F2P</b> — hop + lijst: geen members-werelden/locaties. Uitvinken toont members weer.<br>"
            + "<b>World hop</b> — hop naar de wereld van de gekozen ster.<br>"
            + "<b>Wacht extra lagen (0–8)</b> — hoeveel lagen boven jouw minebare tier je nog bezoekt.<br>"
            + "Binnen die cap: hoogste laag die je nu kunt minen; geen minebare → dichtst bij jouw lvl wachten (T5 → T6, niet T8).<br>"
            + "&nbsp;&nbsp;0 = alleen sterren die je nu kunt minen; te hoge blijven in de lijst, bot gaat er niet heen.<br>"
            + "&nbsp;&nbsp;1 = je kunt T5 → wel naar T6 wachten, niet T7+.<br>"
            + "&nbsp;&nbsp;8 = ook naar T9 en wachten.<br>"
            + "<b>Tijdens wachten</b> — bij een te hoge ster binnen die cap: niets, High Alchemy, "
            + "mine in de buurt, of woodcut in de buurt (bijl meenemen).<br>"
            + "High Alch: Magic 55 + nature rune + fire runes of fire staff. "
            + "Elke tick: is de ster nu minebaar (laag gezakt / mining omhoog)? Zo ja → Mine, alch stopt.<br>"
            + "Wel alchen: noted junk eerst, daarna sieraden e.d. (gold necklace).<br>"
            + "Niet alchen: coins, eten/pots, teleport-tabs, essence, stardust, clues, lamps, "
            + "uncut gems/geodes, pickaxe, hatchet, fire staff, nature/fire (en andere) runes, pouches.<br>"
            + "<b>JSON-feed URL</b> — extra GET-JSON naast Discord/Portal (sidebar).<br>"
            + "<b>Star overlay</b> — status, feed en doel op het spelcanvas."
            + "</body></html>";

    private LoneBotStarHelpUi() {
    }

    static JComponent panel() {
        JEditorPane pane = new JEditorPane("text/html", HTML);
        pane.setEditable(false);
        pane.setOpaque(true);
        pane.setBackground(new Color(28, 28, 28));
        pane.setBorder(new EmptyBorder(2, 4, 2, 4));
        pane.setCaretPosition(0);
        pane.putClientProperty("JEditorPane.honorDisplayProperties", Boolean.TRUE);
        pane.setFocusable(false);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(62, 62, 62)));
        scroll.setOpaque(true);
        scroll.getViewport().setBackground(new Color(28, 28, 28));
        scroll.setAlignmentX(javax.swing.JComponent.LEFT_ALIGNMENT);
        Dimension size = new Dimension(240, 280);
        scroll.setPreferredSize(size);
        scroll.setMinimumSize(new Dimension(120, 180));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        return scroll;
    }
}
