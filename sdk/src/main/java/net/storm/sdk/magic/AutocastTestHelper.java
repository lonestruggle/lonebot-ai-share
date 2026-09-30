package net.storm.sdk.magic;

import net.storm.api.magic.SpellBook;
import net.storm.api.widgets.Tab;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Tabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handmatige autocast-tests — dropdown in LoneBot Test-tab.
 */
public final class AutocastTestHelper {

    private static final Logger log = LoggerFactory.getLogger(AutocastTestHelper.class);

    public enum Method {
        DUMP_STATE("0) Dump state (varbit + widgets)"),
        COMBAT_DYN_ACTION("1) FIX: Combat picker dyn-action invoke"),
        COMBATBOT_FULL("2) CombatBot: selectSpell + setAutoCast"),
        COMBAT_OPEN_ONLY("3) Alleen Choose spell openen"),
        MAGIC_SELECT_ONLY("4) Magic-tab selectSpell only"),
        DEFENSIVE_DYN("5) Defensive dyn-action invoke");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private AutocastTestHelper() {
    }

    public static String run(Method method, SpellBook.Standard spell) {
        if (method == null) {
            return "⚠ geen methode";
        }
        if (spell == null && method != Method.DUMP_STATE) {
            return "⚠ geen spell";
        }

        String before = "var276=?";
        try {
            before = AutocastCombatHelper.dumpCombatSnapshot();
        } catch (Throwable t) {
            before = "before-dump: " + t;
        }
        BotRuntime.logConsole("[AutocastTest] START " + method + " → "
                + (spell != null ? spell.getName() : "-"));
        BotRuntime.logConsole("[AutocastTest] before " + before);

        String result;
        try {
            switch (method) {
                case DUMP_STATE:
                    // Eerst Combat-tab forceren zodat 593 zichtbaar is
                    AutocastCombatHelper.ensureCombatTabReady(false);
                    Sleep.sleep(200, 350);
                    result = AutocastCombatHelper.dumpCombatSnapshot();
                    Magic.dumpKnownSpellWidgets();
                    if (AutocastCombatHelper.pickerOpen()
                            || AutocastCombatHelper.openPickerReliable(false)) {
                        Sleep.sleep(250, 400);
                        AutocastCombatHelper.dumpPickerSpells();
                        result += " | picker geopend";
                    } else {
                        result += " | Choose spell niet open (tab=" + Tabs.getCurrent() + ")";
                    }
                    break;
                case COMBAT_DYN_ACTION:
                    result = runDyn(spell, false);
                    break;
                case COMBATBOT_FULL:
                    Magic.clearAutoCastCache();
                    boolean ok = Magic.setAutoCast(spell, false);
                    result = "setAutoCast→" + ok + " isAuto=" + Magic.isAutoCasting(spell)
                            + " wantVar=" + AutocastCombatHelper.expectedVarbit(spell);
                    break;
                case COMBAT_OPEN_ONLY:
                    boolean ready = AutocastCombatHelper.ensureCombatTabReady(false);
                    boolean opened = ready && AutocastCombatHelper.openPickerReliable(false);
                    Sleep.sleep(300, 500);
                    if (opened && AutocastCombatHelper.pickerOpen()) {
                        AutocastCombatHelper.dumpPickerSpells();
                        result = "picker open OK tab=" + Tabs.getCurrent();
                    } else {
                        result = "picker open FAIL ready=" + ready + " opened=" + opened
                                + " tab=" + Tabs.getCurrent();
                    }
                    break;
                case MAGIC_SELECT_ONLY:
                    Tabs.open(Tab.MAGIC);
                    Sleep.sleep(300, 500);
                    Magic.dumpKnownSpellWidgets();
                    Magic.selectSpell(spell);
                    Sleep.sleep(400, 700);
                    result = "selectSpell via packed id=" + spell.getWidgetId()
                            + " @" + spell.getWidgetGroup() + "," + spell.getWidgetChild()
                            + " isAuto=" + Magic.isAutoCasting(spell);
                    break;
                case DEFENSIVE_DYN:
                    result = runDyn(spell, true);
                    break;
                default:
                    result = "⚠ onbekend";
            }
        } catch (Throwable t) {
            result = "⚠ exception: " + t;
            log.warn("[AutocastTest] {}", t.toString(), t);
        }

        boolean success = spell != null && Magic.isAutoCasting(spell);
        String line = (success ? "✓ OK " : "· ") + method.name()
                + (spell != null ? " " + spell.getName() : "")
                + " | " + result;
        log.info("[AutocastTest] {}", line);
        BotRuntime.logConsole("[AutocastTest] " + line);
        return line;
    }

    private static String runDyn(SpellBook.Standard spell, boolean defensive) {
        Magic.clearAutoCastCache();
        boolean ok = AutocastCombatHelper.setSpell(spell, defensive);
        return "dynInvoke→" + ok
                + " isAuto=" + Magic.isAutoCasting(spell)
                + " def=" + Magic.isDefensiveAutoCasting()
                + " wantVar=" + AutocastCombatHelper.expectedVarbit(spell);
    }
}
