package net.storm.sdk.magic;

import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.magic.SpellBook;
import net.storm.api.widgets.Tab;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Skills;
import net.storm.sdk.utils.Sleep;
import net.storm.sdk.widgets.Tabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handmatige F2P-teleport tests — Test-tab.
 * Success = landings-tegel of teleport-animatie — niet “8 tegels gelopen”.
 */
public final class TeleportTestHelper {

    private static final Logger log = LoggerFactory.getLogger(TeleportTestHelper.class);
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    /** Bekende landingsplekken (F2P). */
    public static final WorldPoint LAND_HOME = new WorldPoint(3222, 3218, 0);
    public static final WorldPoint LAND_VARROCK = new WorldPoint(3213, 3424, 0);
    public static final WorldPoint LAND_LUMBRIDGE = new WorldPoint(3222, 3218, 0);
    public static final WorldPoint LAND_FALADOR = new WorldPoint(2965, 3378, 0);

    public enum Method {
        DUMP_WIDGET("0) Dump Home/Varrock/Lumb/Fally widgets"),
        MAGIC_CAST("1) Magic.cast — widget.interact Cast (CombatBot)"),
        WIDGET_TARGET("2) interactSpellWidget Cast + WIDGET_TARGET (alleen alch/select)"),
        CC_OP("3) interactSpellWidget Cast + CC_OP"),
        CC_OP_PARAM1("4) interactSpellWidget Cast + CC_OP (zelfde)"),
        ALL_METHODS("5) Alle methodes 1→3 op gekozen spell (lang)");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SpellChoice {
        HOME(SpellBook.Standard.HOME_TELEPORT, LAND_HOME, 22_000L),
        VARROCK(SpellBook.Standard.VARROCK_TELEPORT, LAND_VARROCK, 6_500L),
        LUMBRIDGE(SpellBook.Standard.LUMBRIDGE_TELEPORT, LAND_LUMBRIDGE, 6_500L),
        FALADOR(SpellBook.Standard.FALADOR_TELEPORT, LAND_FALADOR, 6_500L);

        final SpellBook.Standard spell;
        final WorldPoint land;
        final long waitMs;

        SpellChoice(SpellBook.Standard spell, WorldPoint land, long waitMs) {
            this.spell = spell;
            this.land = land;
            this.waitMs = waitMs;
        }

        @Override
        public String toString() {
            return spell.getName() + " (lvl " + spell.getLevel() + ")";
        }
    }

    private TeleportTestHelper() {
    }

    public static String run(Method method, SpellChoice choice) {
        if (method == null) {
            return "⚠ geen methode";
        }
        if (!BUSY.compareAndSet(false, true)) {
            String busy = "⚠ test al bezig — wacht tot vorige klaar (niet overlappen)";
            BotRuntime.logConsole("[TeleTest] " + busy);
            return busy;
        }
        BotRuntime.logConsole("[TeleTest] START " + method
                + (choice != null ? " → " + choice.spell.getName() : ""));
        try {
            switch (method) {
                case DUMP_WIDGET:
                    return dumpWidgets();
                case ALL_METHODS:
                    if (choice == null) {
                        return "⚠ kies spell";
                    }
                    return runAllMethods(choice);
                case MAGIC_CAST:
                case WIDGET_TARGET:
                case CC_OP:
                case CC_OP_PARAM1:
                    if (choice == null) {
                        return "⚠ kies spell";
                    }
                    return runOne(method, choice);
                default:
                    return "⚠ onbekend";
            }
        } catch (Throwable t) {
            log.warn("[TeleTest] {}", t.toString(), t);
            BotRuntime.logConsole("[TeleTest] ERR " + t);
            return "⚠ " + t.getClass().getSimpleName() + ": " + t.getMessage();
        } finally {
            BUSY.set(false);
        }
    }

    private static String dumpWidgets() {
        if (!Tabs.isOpen(Tab.MAGIC)) {
            Tabs.open(Tab.MAGIC);
            Sleep.sleep(200, 350);
        }
        Magic.dumpKnownSpellWidgets();
        StringBuilder sb = new StringBuilder("widgets ");
        for (SpellChoice sc : SpellChoice.values()) {
            SpellBook.Standard s = sc.spell;
            sb.append(s.getName()).append("=@")
                    .append(s.getWidgetGroup()).append(',').append(s.getWidgetChild())
                    .append(" packed=").append(s.getWidgetId()).append(" | ");
        }
        int magic = Skills.getLevel(net.runelite.api.Skill.MAGIC);
        sb.append("magic=").append(magic)
                .append(" homeCd=").append(Magic.isHomeTeleportOnCooldown());
        String msg = sb.toString();
        BotRuntime.logConsole("[TeleTest] " + msg);
        return msg;
    }

    private static String runAllMethods(SpellChoice choice) {
        StringBuilder out = new StringBuilder();
        Method[] methods = {Method.MAGIC_CAST, Method.CC_OP, Method.WIDGET_TARGET};
        for (Method m : methods) {
            String r = runOne(m, choice);
            out.append(m.name()).append("→").append(r).append(" || ");
            if (r != null && r.startsWith("✓")) {
                BotRuntime.logConsole("[TeleTest] WINNER " + m.name() + " — stop rest");
                break;
            }
            if (r != null && r.contains("cooldown")) {
                BotRuntime.logConsole("[TeleTest] Home cooldown — stop rest");
                break;
            }
            Sleep.sleep(800, 1200);
        }
        return out.toString();
    }

    private static String runOne(Method method, SpellChoice choice) {
        WorldPoint before = localPos();
        int animBefore = localAnim();
        boolean homeCd = choice.spell == SpellBook.Standard.HOME_TELEPORT
                && Magic.isHomeTeleportOnCooldown();
        BotRuntime.logConsole("[TeleTest] before pos=" + fmt(before)
                + " anim=" + animBefore
                + " magic=" + Skills.getLevel(net.runelite.api.Skill.MAGIC)
                + " needLvl=" + choice.spell.getLevel()
                + " homeCd=" + homeCd
                + " land=" + fmt(choice.land));

        if (homeCd) {
            String skip = "✗ Home Teleport op cooldown (30 min) — CombatBot wacht tot canCast()";
            BotRuntime.logConsole("[TeleTest] " + method.name() + " " + skip);
            return skip;
        }

        if (!Tabs.isOpen(Tab.MAGIC)) {
            Tabs.open(Tab.MAGIC);
            Sleep.sleep(180, 320);
        }

        boolean clicked;
        switch (method) {
            case MAGIC_CAST:
                clicked = Magic.cast(choice.spell);
                break;
            case WIDGET_TARGET:
                clicked = Magic.interactSpellWidget(choice.spell, "Cast", MenuAction.WIDGET_TARGET);
                break;
            case CC_OP:
            case CC_OP_PARAM1:
                clicked = Magic.interactSpellWidget(choice.spell, "Cast", MenuAction.CC_OP);
                break;
            default:
                clicked = false;
        }
        BotRuntime.logConsole("[TeleTest] click=" + clicked + " method=" + method.name()
                + " spell=" + choice.spell.getName());

        long deadline = System.currentTimeMillis() + choice.waitMs;
        WorldPoint best = before;
        int maxAnim = animBefore;
        boolean nearLand = false;
        boolean sawTeleAnim = false;
        while (System.currentTimeMillis() < deadline) {
            Sleep.sleep(250, 400);
            WorldPoint now = localPos();
            int anim = localAnim();
            if (isTeleportAnim(anim)) {
                sawTeleAnim = true;
                maxAnim = anim;
            } else if (anim > 0 && anim != animBefore) {
                maxAnim = Math.max(maxAnim, anim);
            }
            if (now != null) {
                best = now;
            }
            if (landedAtDest(before, now, choice.land)) {
                nearLand = true;
                best = now;
                break;
            }
        }

        int dMove = (before != null && best != null && samePlane(before, best))
                ? before.distanceTo(best) : -1;
        int dLand = (best != null && choice.land != null && samePlane(best, choice.land))
                ? best.distanceTo(choice.land) : -1;

        String verdict;
        if (nearLand || (sawTeleAnim && dLand >= 0 && dLand <= 24)) {
            verdict = "✓ GELAND dMove=" + dMove + " dLand=" + dLand
                    + " @" + fmt(best) + " animMax=" + maxAnim;
        } else if (clicked && sawTeleAnim) {
            verdict = "· klik+tele-anim maar geen land dMove=" + dMove
                    + " dLand=" + dLand + " anim=" + maxAnim + " (channel/cancel?) @" + fmt(best);
        } else if (clicked && dMove >= 8 && (dLand < 0 || dLand > 24)) {
            verdict = "✗ geen teleport — alleen gelopen dMove=" + dMove
                    + " dLand=" + dLand + " @" + fmt(best)
                    + " (echte land=" + fmt(choice.land) + ")";
        } else if (clicked) {
            verdict = "✗ klik=true maar geen tele/land dMove=" + dMove
                    + " dLand=" + dLand + " @" + fmt(best)
                    + " — opcode/cooldown?";
        } else {
            verdict = "✗ klik=false widget/cast fail @" + fmt(before);
        }
        BotRuntime.logConsole("[TeleTest] " + method.name() + " " + verdict);
        return verdict;
    }

    /** Echt geland bij dest, niet 8 tegels verderop gelopen. */
    private static boolean landedAtDest(WorldPoint start, WorldPoint now, WorldPoint land) {
        if (now == null || land == null || !samePlane(now, land) || now.distanceTo(land) > 12) {
            return false;
        }
        if (start == null || !samePlane(start, land)) {
            return true;
        }
        return start.distanceTo(land) > 20;
    }

    /** Home tele ~4847+, standaard tele 714. */
    private static boolean isTeleportAnim(int anim) {
        if (anim < 0) {
            return false;
        }
        if (anim == 714 || anim == 1816 || anim == 4069) {
            return true;
        }
        return anim >= 4847 && anim <= 4863;
    }

    private static boolean samePlane(WorldPoint a, WorldPoint b) {
        return a != null && b != null && a.getPlane() == b.getPlane();
    }

    private static WorldPoint localPos() {
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            return me != null ? me.worldLocation : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int localAnim() {
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            return me != null ? me.animation : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String fmt(WorldPoint p) {
        return p == null ? "-" : p.getX() + "," + p.getY() + "," + p.getPlane();
    }
}
