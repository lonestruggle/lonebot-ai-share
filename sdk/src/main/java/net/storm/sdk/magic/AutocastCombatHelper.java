package net.storm.sdk.magic;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.magic.SpellBook;
import net.storm.api.widgets.Tab;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Tabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Autocast via Combat Options picker (iface 201).
 * <p>
 * Live dump: spell staat in {@code actions[]} op dyn-children van 201,1;
 * {@code menuAction(childIndex, parentId, CC_OP, …)}.
 * Openen van picker faalt vaak als Combat-tab nog niet zichtbaar is na Magic-tab —
 * daarom: wait-for-button + skip-open als picker al open + muis-fallback.
 */
public final class AutocastCombatHelper {

    private static final Logger log = LoggerFactory.getLogger(AutocastCombatHelper.class);

    public static final int COMBAT_GROUP = 593;
    public static final int OFFENSIVE_CHOOSE = 28;
    public static final int DEFENSIVE_CHOOSE = 23;
    public static final int PICKER_GROUP = 201;
    public static final int PICKER_LIST = 1;
    public static final int VARBIT_AUTOCAST_SPELL = 276;

    private AutocastCombatHelper() {
    }

    public static boolean setSpell(SpellBook.Standard spell, boolean defensive) {
        if (spell == null) {
            return false;
        }
        if (Magic.isAutoCasting(spell) && Magic.isDefensiveAutoCasting() == defensive) {
            return true;
        }

        String want = spell.getName();

        // Picker al open (na dump / vorige poging) → niet opnieuw Choose spell
        if (!pickerOpen()) {
            if (!ensureCombatTabReady(defensive)) {
                BotRuntime.logConsole("[Autocast] FAIL Combat-tab / Choose spell niet zichtbaar"
                        + " (tab=" + Tabs.getCurrent() + ")");
                return false;
            }
            if (!openPickerReliable(defensive)) {
                BotRuntime.logConsole("[Autocast] FAIL open Choose spell"
                        + " (tab=" + Tabs.getCurrent() + " staff?)");
                return false;
            }
            if (!waitPickerOpen(2200)) {
                BotRuntime.logConsole("[Autocast] FAIL picker 201 niet open na Choose spell");
                return false;
            }
        } else {
            BotRuntime.logConsole("[Autocast] picker al open — skip Choose spell");
        }

        Sleep.sleep(120, 220);
        boolean clicked = clickSpellInPicker(want);
        if (!clicked) {
            BotRuntime.logConsole("[Autocast] FAIL spell niet geklikt: " + want);
            dumpPickerSpells();
            return false;
        }

        long deadline = System.currentTimeMillis() + 3200L;
        while (System.currentTimeMillis() < deadline) {
            if (Magic.isAutoCasting(spell)) {
                // Defensive mismatch: ok genoeg als spell klopt; mode is secundair
                BotRuntime.logConsole("[Autocast] OK " + want
                        + " var276=" + readVar(VARBIT_AUTOCAST_SPELL)
                        + " def=" + Magic.isDefensiveAutoCasting());
                return true;
            }
            Sleep.sleep(100, 180);
        }
        BotRuntime.logConsole("[Autocast] FAIL na klik var276=" + readVar(VARBIT_AUTOCAST_SPELL)
                + " want=" + want + " (" + expectedVarbit(spell) + ")");
        return Magic.isAutoCasting(spell);
    }

    /** Combat-tab open + Choose-spell knop zichtbaar (of picker al open). */
    public static boolean ensureCombatTabReady(boolean defensive) {
        if (pickerOpen()) {
            return true;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!Tabs.isOpen(Tab.COMBAT)) {
                Tabs.open(Tab.COMBAT);
            }
            long deadline = System.currentTimeMillis() + 1800L;
            while (System.currentTimeMillis() < deadline) {
                if (pickerOpen()) {
                    return true;
                }
                if (Tabs.isOpen(Tab.COMBAT) && chooseSpellVisible(defensive)) {
                    return true;
                }
                Sleep.sleep(80, 140);
            }
            // Forceer opnieuw (soms blijft Magic-tab hangen)
            Tabs.open(Tab.INVENTORY);
            Sleep.sleep(120, 200);
            Tabs.open(Tab.COMBAT);
            Sleep.sleep(200, 350);
        }
        return chooseSpellVisible(defensive) || pickerOpen();
    }

    public static boolean openPickerReliable(boolean defensive) {
        if (pickerOpen()) {
            return true;
        }
        // 1) Invoke
        if (openPicker(defensive) && waitPickerOpen(900)) {
            return true;
        }
        // 2) Muis op Choose spell
        if (mouseClickChooseSpell(defensive) && waitPickerOpen(1200)) {
            BotRuntime.logConsole("[Autocast] Choose spell via muis OK");
            return true;
        }
        // 3) Andere knop (defensive ↔ offensive) als fallback open
        if (openPicker(!defensive) && waitPickerOpen(900)) {
            BotRuntime.logConsole("[Autocast] Choose spell via andere knop OK");
            return true;
        }
        return pickerOpen();
    }

    public static boolean openPicker(boolean defensive) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget box = findChooseSpellOnClient(c, defensive);
            if (box == null) {
                log.info("[Autocast] Choose spell widget null defensive={}", defensive);
                return false;
            }
            boolean ok = invokeWidget(c, box, "Choose spell");
            log.info("[Autocast] Choose spell invoke={} id={}", ok, box.getId());
            return ok;
        }, false));
    }

    private static boolean mouseClickChooseSpell(boolean defensive) {
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget box = findChooseSpellOnClient(c, defensive);
            if (box == null) {
                return null;
            }
            java.awt.Rectangle b = box.getBounds();
            if (b == null || b.width < 2 || b.height < 2) {
                return null;
            }
            int x = b.x + b.width / 2 + ThreadLocalRandom.current().nextInt(-3, 4);
            int y = b.y + b.height / 2 + ThreadLocalRandom.current().nextInt(-3, 4);
            return new Point(x, y);
        }, null);
        if (click == null) {
            return false;
        }
        BotRuntime.logConsole("[Autocast] muis Choose spell @ " + click.getX() + "," + click.getY());
        if (!Mouse.moveRaw(click.getX(), click.getY())) {
            return false;
        }
        Sleep.sleep(40, 90);
        return Mouse.clickOnly(click.getX(), click.getY(), true);
    }

    public static boolean chooseSpellVisible(boolean defensive) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null && findChooseSpellOnClient(c, defensive) != null;
        }, false));
    }

    private static Widget findChooseSpellOnClient(Client c, boolean defensive) {
        Widget box = defensive
                ? c.getWidget(WidgetInfo.COMBAT_DEFENSIVE_SPELL_BOX)
                : c.getWidget(WidgetInfo.COMBAT_SPELL_BOX);
        if (box == null || box.isHidden()) {
            box = c.getWidget(COMBAT_GROUP, defensive ? DEFENSIVE_CHOOSE : OFFENSIVE_CHOOSE);
        }
        if (box == null || box.isHidden()) {
            return null;
        }
        return box;
    }

    public static boolean pickerOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget root = c.getWidget(PICKER_GROUP, PICKER_LIST);
            if (root != null && !root.isHidden()) {
                Widget[] kids = root.getDynamicChildren();
                // Echt open als er spell-actions zijn (niet alleen lege shell)
                if (kids != null && kids.length > 1) {
                    return true;
                }
                return true;
            }
            return false;
        }, false));
    }

    private static boolean waitPickerOpen(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (pickerOpen()) {
                return true;
            }
            Sleep.sleep(70, 120);
        }
        return pickerOpen();
    }

    /**
     * Zoek dynamic child met action == spellName en invoke met juiste child-index.
     */
    public static boolean clickSpellInPicker(String spellName) {
        if (spellName == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget list = c.getWidget(PICKER_GROUP, PICKER_LIST);
            if (list == null || list.isHidden()) {
                return false;
            }
            Widget[] kids = list.getDynamicChildren();
            if (kids == null || kids.length == 0) {
                kids = list.getChildren();
            }
            if (kids == null) {
                return false;
            }
            for (int i = 0; i < kids.length; i++) {
                Widget k = kids[i];
                if (k == null || k.isHidden()) {
                    continue;
                }
                String action = firstAction(k);
                if (action == null || !action.equalsIgnoreCase(spellName)) {
                    continue;
                }
                int param0 = i;
                int packed = list.getId();
                log.info("[Autocast] picker invoke idx={} id={} opt={}", param0, packed, action);
                BotRuntime.logConsole("[Autocast] picker → idx=" + param0 + " \"" + action + "\" (dyn" + i + ")");
                try {
                    c.menuAction(param0, packed, MenuAction.CC_OP, 1, -1, action, "");
                    return true;
                } catch (Throwable t) {
                    return MenuInteract.invokeMenu(action, "", 1, MenuAction.CC_OP.getId(), param0, packed);
                }
            }
            return false;
        }, false));
    }

    public static void dumpPickerSpells() {
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            dumpPickerSpellsOnClient(c);
            return null;
        }, null);
    }

    public static String dumpCombatSnapshot() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "geen client";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("var276=").append(safeVar(c, VARBIT_AUTOCAST_SPELL))
                    .append(" var275=").append(safeVar(c, 275))
                    .append(" def=").append(safeVar(c, 2668))
                    .append(" tab=").append(Tabs.getCurrent());
            BotRuntime.logConsole("[Autocast] " + sb);
            BotRuntime.logConsole("[Autocast] --- 593 ---");
            for (int i = 0; i < 50; i++) {
                Widget w = c.getWidget(COMBAT_GROUP, i);
                if (w == null || w.isHidden()) {
                    continue;
                }
                String act = firstAction(w);
                String text = w.getText();
                if ((act == null || act.isEmpty()) && (text == null || text.isEmpty()) && w.getSpriteId() <= 0) {
                    continue;
                }
                BotRuntime.logConsole("[Autocast] 593," + i
                        + " act=" + act
                        + " text=" + clean(text)
                        + " sprite=" + w.getSpriteId());
            }
            Widget list = c.getWidget(PICKER_GROUP, PICKER_LIST);
            if (list != null && !list.isHidden()) {
                BotRuntime.logConsole("[Autocast] --- picker open ---");
                dumpPickerSpellsOnClient(c);
            } else {
                BotRuntime.logConsole("[Autocast] picker 201 dicht");
            }
            return sb.toString();
        }, "dump fail");
    }

    private static void dumpPickerSpellsOnClient(Client c) {
        Widget list = c.getWidget(PICKER_GROUP, PICKER_LIST);
        if (list == null) {
            return;
        }
        Widget[] kids = list.getDynamicChildren();
        if (kids == null) {
            return;
        }
        for (int i = 0; i < kids.length; i++) {
            Widget k = kids[i];
            if (k == null) {
                continue;
            }
            String act = firstAction(k);
            if (act == null && k.getSpriteId() <= 0) {
                continue;
            }
            BotRuntime.logConsole("[Autocast] dyn" + i + " idx=" + k.getIndex()
                    + " act=" + act + " sprite=" + k.getSpriteId());
        }
    }

    private static boolean invokeWidget(Client c, Widget w, String hint) {
        if (w == null) {
            return false;
        }
        String[] actions = w.getActions();
        int op = 1;
        String opt = hint != null ? hint : "Select";
        if (actions != null) {
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] == null) {
                    continue;
                }
                if (hint != null && actions[i].toLowerCase(Locale.ROOT).contains(hint.toLowerCase(Locale.ROOT))) {
                    op = i + 1;
                    opt = actions[i];
                    break;
                }
            }
            if (op == 1 && actions.length > 0 && actions[0] != null) {
                opt = actions[0];
            }
        }
        try {
            c.menuAction(-1, w.getId(), MenuAction.CC_OP, op, -1, opt, "");
            return true;
        } catch (Throwable t) {
            return MenuInteract.invokeMenu(opt, "", op, MenuAction.CC_OP.getId(), -1, w.getId());
        }
    }

    private static String firstAction(Widget w) {
        if (w == null) {
            return null;
        }
        String[] a = w.getActions();
        if (a == null) {
            return null;
        }
        for (String s : a) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private static int safeVar(Client c, int id) {
        try {
            return c.getVarbitValue(id);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int readVar(int id) {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null ? safeVar(c, id) : -1;
        }, -1);
        return v != null ? v : -1;
    }

    public static int expectedVarbit(SpellBook.Standard spell) {
        if (spell == null) {
            return -1;
        }
        switch (spell) {
            case WIND_STRIKE:
                return 1;
            case WATER_STRIKE:
                return 2;
            case EARTH_STRIKE:
                return 3;
            case FIRE_STRIKE:
                return 4;
            case WIND_BOLT:
                return 5;
            case WATER_BOLT:
                return 6;
            case EARTH_BOLT:
                return 7;
            case FIRE_BOLT:
                return 8;
            default:
                return -1;
        }
    }

    private static String clean(String s) {
        if (s == null) {
            return "-";
        }
        return s.replaceAll("<[^>]*>", "").replace('\n', ' ');
    }
}
