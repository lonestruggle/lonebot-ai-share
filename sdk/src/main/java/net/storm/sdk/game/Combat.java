package net.storm.sdk.game;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.actors.INPC;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.interact.mouse.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

/**
 * Storm-compat {@code net.storm.sdk.game.Combat}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/game/Combat.html">Storm Combat</a>
 */
public final class Combat {

    private static final Logger log = LoggerFactory.getLogger(Combat.class);

    private Combat() {
    }

    public static boolean isInCombat() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me.present && me.interacting;
    }

    public static int getCurrentHealth() {
        return Skills.getBoostedLevel(Skill.HITPOINTS);
    }

    public static int getMissingHealth() {
        return Math.max(0, Skills.getLevel(Skill.HITPOINTS) - getCurrentHealth());
    }

    /** 0.0–100.0 */
    public static double getHealthPercent() {
        int max = Skills.getLevel(Skill.HITPOINTS);
        if (max <= 0) {
            return 0;
        }
        return 100.0 * getCurrentHealth() / (double) max;
    }

    public static boolean isPoisoned() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.getVarpValue(VarPlayer.POISON) > 0;
            } catch (Throwable t) {
                return false;
            }
        }, false);
    }

    public static boolean isVenomed() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.getVarpValue(VarPlayer.POISON) >= 1000000;
            } catch (Throwable t) {
                return false;
            }
        }, false);
    }

    public static int getSpecEnergy() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            try {
                // Special attack energy is typically varp 300 / 10
                return c.getVarpValue(300) / 10;
            } catch (Throwable t) {
                return 0;
            }
        }, 0);
    }

    public static boolean isSpecEnabled() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.getVarpValue(301) == 1;
            } catch (Throwable t) {
                return false;
            }
        }, false);
    }

    public static boolean toggleSpec() {
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                java.lang.reflect.Field f = net.runelite.api.widgets.WidgetInfo.class.getField("MINIMAP_SPEC_ORB");
                Object info = f.get(null);
                java.lang.reflect.Method packed = info.getClass().getMethod("getPackedId");
                Widget orb = c.getWidget((Integer) packed.invoke(info));
                Point p = ClickPoints.forWidgetOnClient(orb);
                if (p != null) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
            Widget orb = c.getWidget(160, 36);
            return ClickPoints.forWidgetOnClient(orb);
        }, null);
        if (click == null) {
            log.warn("[Combat] toggleSpec — spec orb not found");
            return false;
        }
        return MouseManager.interactAt(click);
    }

    public static INPC getAttackableNPC(String... names) {
        return NPCs.getNearest(npc -> {
            if (npc == null || npc.getName() == null) {
                return false;
            }
            if (!npc.hasAction("Attack")) {
                return false;
            }
            if (npc.getHealthScale() > 0 && npc.getHealthRatio() == 0) {
                return false;
            }
            if (names == null || names.length == 0) {
                return true;
            }
            for (String n : names) {
                if (n != null && n.equalsIgnoreCase(npc.getName())) {
                    return true;
                }
            }
            return false;
        });
    }

    public static INPC getAttackableNPC(int... ids) {
        return NPCs.getNearest(npc -> {
            if (npc == null || !npc.hasAction("Attack")) {
                return false;
            }
            if (npc.getHealthScale() > 0 && npc.getHealthRatio() == 0) {
                return false;
            }
            if (ids == null || ids.length == 0) {
                return true;
            }
            for (int id : ids) {
                if (npc.getId() == id) {
                    return true;
                }
            }
            return false;
        });
    }

    public static INPC getAttackableNPC(Predicate<INPC> filter) {
        return NPCs.getNearest(npc -> {
            if (npc == null || !npc.hasAction("Attack")) {
                return false;
            }
            if (npc.getHealthScale() > 0 && npc.getHealthRatio() == 0) {
                return false;
            }
            return filter == null || filter.test(npc);
        });
    }

    /** @stub antifire varbit — best-effort */
    public static boolean isAntifired() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.getVarbitValue(Varbits.ANTIFIRE) > 0;
            } catch (Throwable t) {
                return false;
            }
        }, false);
    }
}
