package net.storm.sdk.magic;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.magic.SpellBook;
import net.storm.api.widgets.Tab;
import net.storm.sdk.game.Skills;
import net.storm.sdk.game.Static;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Tabs;
import net.storm.sdk.widgets.Widgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Locale;

/**
 * Magic casting / autocast — volgt CombatBot + Storm
 * {@code selectSpell} → {@code setAutoCast(spell, defensive)}, met echte OSRS
 * Combat Options UI (widget 593 → spell-picker 201) en varbit 276 verificatie.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/magic/IMagic.html#setAutoCast(net.storm.api.magic.Spell,boolean)">IMagic.setAutoCast</a>
 */
public final class Magic {

    private static final Logger log = LoggerFactory.getLogger(Magic.class);

    /** RuneLite {@code VarbitID.AUTOCAST_SPELL} — huidige autocast-spell. */
    private static final int VARBIT_AUTOCAST_SPELL = 276;

    private static volatile String lastAutoCastSpell = null;

    private Magic() {
    }

    public static boolean canCast(String spellName) {
        if (spellName == null || spellName.isEmpty()) {
            return false;
        }
        int magic = Skills.getLevel(Skill.MAGIC);
        SpellBook.Standard s = findStandard(spellName);
        if (s != null) {
            return magic >= s.getLevel();
        }
        return magic >= 1;
    }

    public static boolean canCast(SpellBook.Standard spell) {
        if (spell == null || Skills.getLevel(Skill.MAGIC) < spell.getLevel()) {
            return false;
        }
        return spell != SpellBook.Standard.HOME_TELEPORT || !isHomeTeleportOnCooldown();
    }

    /**
     * Storm {@code isHomeTeleportOnCooldown} — 30 min F2P.
     * Varp {@link VarPlayer#LAST_HOME_TELEPORT} = minuten sinds Unix-epoch.
     */
    public static boolean isHomeTeleportOnCooldown() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            int lastMin = c.getVarpValue(VarPlayer.LAST_HOME_TELEPORT);
            if (lastMin <= 0) {
                return false;
            }
            long nowMin = Instant.now().getEpochSecond() / 60L;
            return lastMin + 30L > nowMin;
        }, false));
    }

    /** Minuten tot Home Teleport weer mag. 0 = klaar (of varp onleesbaar). */
    public static int homeTeleportMinutesLeft() {
        Integer left = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            int lastMin = c.getVarpValue(VarPlayer.LAST_HOME_TELEPORT);
            if (lastMin <= 0) {
                return 0;
            }
            long nowMin = Instant.now().getEpochSecond() / 60L;
            long rem = lastMin + 30L - nowMin;
            return rem > 0 ? (int) rem : 0;
        }, 0);
        return left != null ? left : 0;
    }

    public static boolean cast(String spellName) {
        SpellBook.Standard s = findStandard(spellName);
        if (s != null) {
            return cast(s);
        }
        if (spellName == null) {
            return false;
        }
        if (!Tabs.isOpen(Tab.MAGIC)) {
            Tabs.open(Tab.MAGIC);
            Sleep.sleep(120, 220);
        }
        return clickSpellByName(spellName);
    }

    public static boolean cast(SpellBook.Standard spell) {
        if (spell == null) {
            return false;
        }
        if (spell == SpellBook.Standard.HOME_TELEPORT && isHomeTeleportOnCooldown()) {
            BotRuntime.logConsole("[Magic] Home Teleport op cooldown — skip Cast");
            return false;
        }
        if (!Tabs.isOpen(Tab.MAGIC)) {
            Tabs.open(Tab.MAGIC);
            Sleep.sleep(120, 220);
        }
        // CombatBot / Storm: widget.interact("Cast") = CC_OP. WIDGET_TARGET = spell selecteren (alch/npc).
        IWidget wrap = Widgets.get(spell.getWidgetId());
        if (wrap == null || wrap.isHidden()) {
            wrap = Widgets.get(spell.getWidgetGroup(), spell.getWidgetChild());
        }
        if (wrap != null && !wrap.isHidden() && wrap.interact("Cast")) {
            BotRuntime.logConsole("[Magic] Cast " + spell.getName() + " via widget.interact(Cast)");
            return true;
        }
        return interactSpellWidget(spell, "Cast", MenuAction.CC_OP);
    }

    public static boolean cast(SpellBook.Standard spell, INPC npc) {
        if (spell == null || npc == null) {
            return false;
        }
        // Targeted: WIDGET_TARGET select, daarna Cast op NPC — niet untargeted CC_OP.
        selectSpell(spell);
        Sleep.sleep(80, 160);
        return npc.interact("Cast") || npc.interact("Attack");
    }

    public static void clearAutoCastCache() {
        lastAutoCastSpell = null;
    }

    public static String getCachedAutoCastSpell() {
        return lastAutoCastSpell;
    }

    /**
     * Storm {@link net.storm.api.magic.IMagic#selectSpell}: selecteer spell in Magic-tab
     * via vaste packed widget-id (niet name-scan — die klikt vaak verkeerd).
     */
    public static void selectSpell(SpellBook.Standard spell) {
        if (spell == null) {
            return;
        }
        if (!Tabs.isOpen(Tab.MAGIC)) {
            Tabs.open(Tab.MAGIC);
            Sleep.sleep(220, 400);
        }
        if (interactSpellWidget(spell, "Cast", MenuAction.WIDGET_TARGET)) {
            log.info("[Magic] selectSpell → {} widget={}/{} id={}",
                    spell.getName(), spell.getWidgetGroup(), spell.getWidgetChild(), spell.getWidgetId());
            net.storm.sdk.bot.BotRuntime.logConsole("[Magic] selectSpell → " + spell.getName()
                    + " @" + spell.getWidgetGroup() + "," + spell.getWidgetChild());
        } else {
            log.warn("[Magic] selectSpell mislukt: {} id={}", spell.getName(), spell.getWidgetId());
            net.storm.sdk.bot.BotRuntime.logConsole("[Magic] selectSpell FAIL " + spell.getName()
                    + " id=" + spell.getWidgetId());
        }
    }

    /**
     * Interact op vaste MagicSpellbook widget-id (InterfaceID).
     */
    public static boolean interactSpellWidget(SpellBook.Standard spell, String option, MenuAction action) {
        if (spell == null) {
            return false;
        }
        MenuAction ma = action != null ? action : MenuAction.WIDGET_TARGET;
        String opt = option != null ? option : "Cast";
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget w = c.getWidget(spell.getWidgetId());
            if (w == null || w.isHidden()) {
                w = c.getWidget(spell.getWidgetGroup(), spell.getWidgetChild());
            }
            if (w == null || w.isHidden()) {
                log.warn("[Magic] widget missing {} packed={}", spell.getName(), spell.getWidgetId());
                return false;
            }
            String name = w.getName();
            String clean = name != null ? name.replaceAll("<[^>]*>", "") : "";
            String[] acts = w.getActions();
            String actDump = formatActions(acts);
            log.info("[Magic] widget hit {} packed={} child={} name={} opcode={} actions={} bounds={}",
                    spell.getName(), spell.getWidgetId(), spell.getWidgetChild(), clean, ma, actDump,
                    w.getBounds());
            net.storm.sdk.bot.BotRuntime.logConsole("[Magic] click " + spell.getName()
                    + " packed=" + spell.getWidgetId()
                    + " name=" + clean
                    + " opcode=" + ma
                    + " actions=" + actDump);
            try {
                if (ma == MenuAction.CC_OP || ma == MenuAction.CC_OP_LOW_PRIORITY) {
                    // CombatBot/Storm widget.interact("Cast"): identifier = actionIndex+1, niet 0.
                    int identifier = actionIdentifier(acts, opt);
                    int p0 = w.getIndex();
                    if (p0 < 0) {
                        p0 = -1;
                    }
                    int itemId = w.getItemId() > 0 ? w.getItemId() : 0;
                    c.menuAction(p0, w.getId(), ma, identifier, itemId, opt, spell.getName());
                } else {
                    c.menuAction(-1, w.getId(), ma, 0, 0, opt, spell.getName());
                }
                return true;
            } catch (Throwable t) {
                return MenuInteract.invokeMenu(opt, spell.getName(), 0, ma.getId(), -1, w.getId());
            }
        }, false));
    }

    /**
     * Storm {@code setAutoCast(spell, defensive)} — CombatBot-flow:
     * Magic-tab + {@link #selectSpell}, daarna Combat Options spell-picker,
     * geverifieerd via varbit 276 (niet alleen een sessie-cache).
     *
     * @param defensive {@code true} = defensive autocast (Defence XP), {@code false} = offensive
     */
    public static boolean setAutoCast(SpellBook.Standard spell, boolean defensive) {
        if (spell == null) {
            return false;
        }
        if (isAutoCasting(spell) && isDefensiveAutoCasting() == defensive) {
            lastAutoCastSpell = spell.getName();
            return true;
        }

        String name = spell.getName();
        log.info("[Magic] autocast instellen → {} (defensive={})", name, defensive);
        net.storm.sdk.bot.BotRuntime.logConsole("[Magic] autocast → " + name
                + (defensive ? " (def)" : ""));

        // Direct Combat Options (Magic-tab selectSpell zet geen autocast)
        if (setAutoCastViaCombatOptions(spell, defensive)) {
            lastAutoCastSpell = name;
            try {
                Tabs.open(Tab.INVENTORY);
            } catch (Throwable ignored) {
            }
            log.info("[Magic] autocast OK → {} (varbit)", name);
            net.storm.sdk.bot.BotRuntime.logConsole("[Magic] autocast OK → " + name);
            return true;
        }

        // Een retry
        Sleep.sleep(200, 400);
        if (setAutoCastViaCombatOptions(spell, defensive)) {
            lastAutoCastSpell = name;
            try {
                Tabs.open(Tab.INVENTORY);
            } catch (Throwable ignored) {
            }
            log.info("[Magic] autocast OK na retry → {}", name);
            return true;
        }

        log.warn("[Magic] autocast mislukt: {} (varbit nu={})", name, readAutocastVarbit());
        net.storm.sdk.bot.BotRuntime.logConsole("[Magic] autocast FAIL → " + name
                + " varbit=" + readAutocastVarbit());
        return false;
    }

    /** Compat: offensive autocast. */
    public static boolean setAutoCast(SpellBook.Standard spell) {
        return setAutoCast(spell, false);
    }

    /**
     * Echte OSRS-autocast: Combat Options → Choose spell → dyn-child invoke (action-naam).
     */
    private static boolean setAutoCastViaCombatOptions(SpellBook.Standard spell, boolean defensive) {
        return AutocastCombatHelper.setSpell(spell, defensive);
    }

    /**
     * True als varbit 276 de gevraagde spell aangeeft (niet alleen sessie-cache).
     */
    public static boolean isAutoCasting(SpellBook.Standard spell) {
        if (spell == null) {
            return false;
        }
        int want = autocastVarbitValue(spell);
        if (want <= 0) {
            return lastAutoCastSpell != null
                    && lastAutoCastSpell.equalsIgnoreCase(spell.getName());
        }
        int cur = readAutocastVarbit();
        if (cur == want) {
            lastAutoCastSpell = spell.getName();
            return true;
        }
        return false;
    }

    public static boolean isAutoCasting() {
        return readAutocastVarbit() > 0;
    }

    public static boolean isDefensiveAutoCasting() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.getVarbitValue(Varbits.DEFENSIVE_CASTING_MODE) == 1;
            } catch (Throwable t) {
                try {
                    return c.getVarbitValue(2668) == 1;
                } catch (Throwable ignored) {
                    return false;
                }
            }
        }, false));
    }

    public static void deselectAutoCast() {
        lastAutoCastSpell = null;
        // Open combat + klik huidige spell-box opnieuw is game-afhankelijk; cache clear is genoeg voor bot-retry
    }

    public static boolean isSpellbookOpen() {
        return Tabs.isOpen(Tab.MAGIC);
    }

    private static int readAutocastVarbit() {
        Integer v = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            try {
                return c.getVarbitValue(VARBIT_AUTOCAST_SPELL);
            } catch (Throwable t) {
                return 0;
            }
        }, 0);
        return v != null ? v : 0;
    }

    /**
     * Modern spellbook values voor varbit 276 (Microbot / Rs2CombatSpells ordinal+1).
     */
    private static int autocastVarbitValue(SpellBook.Standard spell) {
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

    private static boolean clickSpellByName(String spellName) {
        SpellBook.Standard s = findStandard(spellName);
        if (s != null) {
            return interactSpellWidget(s, "Cast", MenuAction.WIDGET_TARGET);
        }
        // Fallback: exact name-match (geen fuzzy contains)
        Boolean inv = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget w = findSpellWidgetExactOnClient(c, spellName);
            if (w == null) {
                return false;
            }
            try {
                c.menuAction(-1, w.getId(), MenuAction.WIDGET_TARGET, 0, 0, "Cast", spellName);
                return true;
            } catch (Throwable t) {
                return MenuInteract.invokeMenu("Cast", spellName, 0,
                        MenuAction.WIDGET_TARGET.getId(), -1, w.getId());
            }
        }, false);
        if (Boolean.TRUE.equals(inv)) {
            return true;
        }
        log.warn("[Magic] spell widget not found: {}", spellName);
        return false;
    }

    /** Exacte naam-match op magic-tab (geen substring). */
    private static Widget findSpellWidgetExactOnClient(Client c, String spellName) {
        String want = spellName.toLowerCase(Locale.ROOT);
        for (int child = 0; child < 200; child++) {
            try {
                Widget w = c.getWidget(218, child);
                if (w == null || w.isHidden()) {
                    continue;
                }
                String n = w.getName();
                if (n == null) {
                    continue;
                }
                String clean = n.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
                if (clean.equals(want)) {
                    return w;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Tweede helft van High Alch e.d.: spell is al geselecteerd (WIDGET_TARGET),
     * klik inventory-item met Cast.
     */
    public static boolean clickCastOnInventory(IInventoryItem item) {
        if (item == null) {
            return false;
        }
        if (!Tabs.isOpen(Tab.INVENTORY)) {
            Tabs.open(Tab.INVENTORY);
            Sleep.sleep(80, 140);
        }
        final int slot = item.getSlot();
        final int itemId = item.getId();
        final String name = item.getName() != null ? item.getName() : "";
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || slot < 0 || itemId <= 0) {
                return false;
            }
            int packed = ComponentID.INVENTORY_CONTAINER;
            try {
                c.menuAction(slot, packed, MenuAction.WIDGET_TARGET_ON_WIDGET, 0, itemId, "Cast", name);
                return true;
            } catch (Throwable t) {
                return MenuInteract.invokeMenu("Cast", name, 0, MenuAction.WIDGET_TARGET_ON_WIDGET.getId(),
                        slot, packed, itemId);
            }
        }, false));
    }

    /** Dump bekende spell widgets (vaste IDs) naar console — voor Test-tab. */
    public static void dumpKnownSpellWidgets() {
        Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            BotRuntime.logConsole("[Magic] --- bekende spell widgets (packed) ---");
            for (SpellBook.Standard s : SpellBook.Standard.values()) {
                Widget w = c.getWidget(s.getWidgetId());
                if (w == null) {
                    w = c.getWidget(s.getWidgetGroup(), s.getWidgetChild());
                }
                String name = w != null && w.getName() != null
                        ? w.getName().replaceAll("<[^>]*>", "") : "-";
                BotRuntime.logConsole("[Magic] " + s.name()
                        + " packed=" + s.getWidgetId()
                        + " @" + s.getWidgetGroup() + "," + s.getWidgetChild()
                        + " hidden=" + (w == null || w.isHidden())
                        + " name=" + name
                        + " actions=" + (w != null ? formatActions(w.getActions()) : "-")
                        + " sprite=" + (w != null ? w.getSpriteId() : -1));
            }
            return null;
        }, null);
    }

    private static String formatActions(String[] acts) {
        if (acts == null || acts.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < acts.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(acts[i] == null ? "null" : acts[i].replaceAll("<[^>]*>", ""));
        }
        return sb.append(']').toString();
    }

    /** Eerste matching actie → CC_OP identifier (index + 1), zoals {@code RlWidget.interact}. */
    private static int actionIdentifier(String[] acts, String option) {
        if (acts == null || option == null) {
            return 1;
        }
        String want = option.replaceAll("<[^>]*>", "").trim();
        for (int i = 0; i < acts.length; i++) {
            if (acts[i] == null) {
                continue;
            }
            if (acts[i].replaceAll("<[^>]*>", "").equalsIgnoreCase(want)) {
                return i + 1;
            }
        }
        return 1;
    }

    private static SpellBook.Standard findStandard(String name) {
        for (SpellBook.Standard s : SpellBook.Standard.values()) {
            if (s.getName().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }
}
