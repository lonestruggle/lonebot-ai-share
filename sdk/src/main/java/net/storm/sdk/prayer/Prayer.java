package net.storm.sdk.prayer;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.interact.InteractMethod;
import net.storm.api.widgets.Tab;
import net.storm.api.widgets.WidgetGroup;
import net.storm.sdk.game.Skills;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.widgets.Tabs;
import net.storm.sdk.widgets.Widgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prayer helpers using {@link net.runelite.api.Prayer}.
 */
public final class Prayer {

    private static final Logger log = LoggerFactory.getLogger(Prayer.class);

    private Prayer() {
    }

    public static boolean isActive(net.runelite.api.Prayer prayer) {
        if (prayer == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.isPrayerActive(prayer);
            } catch (Throwable t) {
                try {
                    return c.getVarbitValue(prayer.getVarbit()) == 1;
                } catch (Throwable t2) {
                    return false;
                }
            }
        }, false));
    }

    /**
     * Toggle a prayer by opening the prayer tab and clicking its widget (best-effort).
     *
     * @stub Falls back to false if the prayer icon widget cannot be resolved.
     */
    public static boolean toggle(net.runelite.api.Prayer prayer) {
        if (prayer == null) {
            return false;
        }
        Tabs.open(Tab.PRAYER);
        Point click = Static.callOnClientThread(() -> findPrayerClickPoint(prayer), null);
        if (click == null) {
            log.warn("[Prayer] toggle stub — widget not found for {}", prayer);
            return false;
        }
        return MouseManager.interactAt(click);
    }

    /** String overload for Storm-compat callers. */
    public static boolean isActive(String prayerName) {
        net.runelite.api.Prayer p = resolve(prayerName);
        return p != null && isActive(p);
    }

    public static boolean toggle(String prayerName) {
        net.runelite.api.Prayer p = resolve(prayerName);
        if (p == null) {
            log.warn("[Prayer] toggle stub — unknown prayer {}", prayerName);
            return false;
        }
        return toggle(p);
    }

    public static boolean isQuickPrayerActive() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                return c.getVarbitValue(Varbits.QUICK_PRAYER) == 1;
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }

    /**
     * Click the quick-prayer orb (best-effort).
     *
     * @stub Returns false if orb widget missing.
     */
    public static boolean toggleQuickPrayer() {
        Point click = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget orb = c.getWidget(WidgetInfo.MINIMAP_QUICK_PRAYER_ORB);
            return ClickPoints.forWidgetOnClient(orb);
        }, null);
        if (click == null) {
            log.warn("[Prayer] toggleQuickPrayer stub — orb not found");
            return false;
        }
        return MouseManager.interactAt(click);
    }

    /**
     * Deactivate all prayers: turn quick-prayer off, then toggle each active prayer.
     */
    public static boolean flushPray() {
        boolean any = false;
        if (isQuickPrayerActive()) {
            any = toggleQuickPrayer();
        }
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            try {
                if (isActive(p)) {
                    if (toggle(p)) {
                        any = true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return any;
    }

    public static boolean setEnabled(net.runelite.api.Prayer prayer, boolean enabled) {
        if (prayer == null) {
            return false;
        }
        if (isActive(prayer) == enabled) {
            return true;
        }
        return toggle(prayer);
    }

    public static int getPoints() {
        return Skills.getBoostedLevel(Skill.PRAYER);
    }

    public static int getMissingPoints() {
        return Math.max(0, Skills.getLevel(Skill.PRAYER) - getPoints());
    }

    public static boolean isEnabled(net.runelite.api.Prayer prayer) {
        return isActive(prayer);
    }

    public static boolean anyActive() {
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            if (isActive(p)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canUse(net.runelite.api.Prayer prayer) {
        if (prayer == null || prayer.name().startsWith("RP_")) {
            return false;
        }
        int need = requiredLevel(prayer);
        if (need < 0) {
            return false;
        }
        return Skills.getLevel(Skill.PRAYER) >= need;
    }

    public static net.runelite.api.Prayer getBestMeleeOffensive() {
        return firstUsable(
                net.runelite.api.Prayer.PIETY,
                net.runelite.api.Prayer.CHIVALRY,
                net.runelite.api.Prayer.INCREDIBLE_REFLEXES,
                net.runelite.api.Prayer.IMPROVED_REFLEXES,
                net.runelite.api.Prayer.CLARITY_OF_THOUGHT
        );
    }

    public static net.runelite.api.Prayer getBestRangeOffensive() {
        return firstUsable(
                net.runelite.api.Prayer.RIGOUR,
                net.runelite.api.Prayer.DEADEYE,
                net.runelite.api.Prayer.EAGLE_EYE,
                net.runelite.api.Prayer.HAWK_EYE,
                net.runelite.api.Prayer.SHARP_EYE
        );
    }

    public static net.runelite.api.Prayer getBestMageOffensive() {
        return firstUsable(
                net.runelite.api.Prayer.AUGURY,
                net.runelite.api.Prayer.MYSTIC_VIGOUR,
                net.runelite.api.Prayer.MYSTIC_MIGHT,
                net.runelite.api.Prayer.MYSTIC_LORE,
                net.runelite.api.Prayer.MYSTIC_WILL
        );
    }

    public static void toggle(InteractMethod method, net.runelite.api.Prayer prayer) {
        if (prayer == null) {
            return;
        }
        if (method == InteractMethod.INVOKE) {
            Widget w = Static.callOnClientThread(() -> prayerWidget(prayer), null);
            if (w != null) {
                MenuInteract.invokeMenu("Activate", "", 1,
                        net.runelite.api.MenuAction.CC_OP.getId(), -1, w.getId());
                MenuInteract.invokeMenu("Deactivate", "", 1,
                        net.runelite.api.MenuAction.CC_OP.getId(), -1, w.getId());
                return;
            }
        }
        toggle(prayer);
    }

    public static void toggleQuickPrayer(InteractMethod method) {
        toggleQuickPrayer();
    }

    public static void toggleQuickPrayer(InteractMethod method, boolean enabled) {
        if (isQuickPrayerActive() != enabled) {
            toggleQuickPrayer(method);
        }
    }

    public static boolean isQuickPrayerEnabled() {
        return isQuickPrayerActive();
    }

    public static boolean isQuickPrayerOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget w = c.getWidget(InterfaceID.QUICKPRAYER, 0);
            if (w != null && !w.isHidden()) {
                return true;
            }
            try {
                Widget prayers = c.getWidget(WidgetInfo.QUICK_PRAYER_PRAYERS);
                return prayers != null && !prayers.isHidden();
            } catch (Throwable t) {
                return false;
            }
        }, false));
    }

    public static boolean openQuickPrayer() {
        if (isQuickPrayerOpen()) {
            return true;
        }
        IWidget orb = Widgets.wrap(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c == null ? null : c.getWidget(WidgetInfo.MINIMAP_QUICK_PRAYER_ORB);
        }, null));
        if (orb == null) {
            return false;
        }
        return orb.interact("Setup Quick-prayers")
                || orb.interact("Setup")
                || orb.interact("Select")
                || toggleQuickPrayer() && isQuickPrayerOpen();
    }

    public static java.util.List<net.runelite.api.Prayer> getSelectedQuickPrayers() {
        return Static.callOnClientThread(() -> {
            java.util.List<net.runelite.api.Prayer> out = new java.util.ArrayList<>();
            Client c = Static.getClient();
            if (c == null) {
                return out;
            }
            Widget container = null;
            try {
                container = c.getWidget(WidgetInfo.QUICK_PRAYER_PRAYERS);
            } catch (Throwable ignored) {
            }
            if (container == null) {
                return out;
            }
            Widget[] kids = container.getChildren();
            if (kids == null) {
                kids = container.getDynamicChildren();
            }
            if (kids == null) {
                return out;
            }
            for (int i = 0; i < kids.length; i++) {
                Widget k = kids[i];
                if (k == null || k.isHidden()) {
                    continue;
                }
                boolean on = false;
                try {
                    on = k.getBorderType() > 0 || k.getOpacity() == 0;
                } catch (Throwable ignored) {
                }
                if (!on) {
                    continue;
                }
                net.runelite.api.Prayer p = prayerForQuickChild(i);
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        }, new java.util.ArrayList<>());
    }

    public static java.util.List<net.runelite.api.Prayer> getActiveQuickPrayers() {
        java.util.List<net.runelite.api.Prayer> out = new java.util.ArrayList<>();
        if (!isQuickPrayerEnabled()) {
            return out;
        }
        for (net.runelite.api.Prayer p : getSelectedQuickPrayers()) {
            if (isActive(p)) {
                out.add(p);
            }
        }
        return out;
    }

    public static boolean setQuickPrayers(java.util.List<net.runelite.api.Prayer> prayers, boolean closeInterface) {
        if (!openQuickPrayer()) {
            return false;
        }
        java.util.Set<net.runelite.api.Prayer> want = new java.util.HashSet<>();
        if (prayers != null) {
            want.addAll(prayers);
        }
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            if (p.name().startsWith("RP_")) {
                continue;
            }
            boolean selected = getSelectedQuickPrayers().contains(p);
            boolean should = want.contains(p);
            if (selected != should) {
                clickQuickPrayerChild(p);
            }
        }
        if (closeInterface) {
            Keyboard.pressKey(java.awt.event.KeyEvent.VK_ESCAPE);
        }
        return true;
    }

    public static void disableAll(InteractMethod method) {
        flushPray();
    }

    private static net.runelite.api.Prayer firstUsable(net.runelite.api.Prayer... prayers) {
        if (prayers == null) {
            return null;
        }
        for (net.runelite.api.Prayer p : prayers) {
            if (canUse(p)) {
                return p;
            }
        }
        return null;
    }

    private static int requiredLevel(net.runelite.api.Prayer prayer) {
        switch (prayer) {
            case THICK_SKIN:
                return 1;
            case BURST_OF_STRENGTH:
                return 4;
            case CLARITY_OF_THOUGHT:
                return 7;
            case SHARP_EYE:
                return 8;
            case MYSTIC_WILL:
                return 9;
            case ROCK_SKIN:
                return 10;
            case SUPERHUMAN_STRENGTH:
                return 13;
            case IMPROVED_REFLEXES:
                return 16;
            case RAPID_RESTORE:
                return 19;
            case RAPID_HEAL:
                return 22;
            case PROTECT_ITEM:
                return 25;
            case HAWK_EYE:
                return 26;
            case MYSTIC_LORE:
                return 27;
            case STEEL_SKIN:
                return 28;
            case ULTIMATE_STRENGTH:
                return 31;
            case INCREDIBLE_REFLEXES:
                return 34;
            case PROTECT_FROM_MAGIC:
                return 37;
            case PROTECT_FROM_MISSILES:
                return 40;
            case PROTECT_FROM_MELEE:
                return 43;
            case EAGLE_EYE:
                return 44;
            case MYSTIC_MIGHT:
                return 45;
            case RETRIBUTION:
                return 46;
            case REDEMPTION:
                return 49;
            case SMITE:
                return 52;
            case PRESERVE:
                return 55;
            case CHIVALRY:
                return 60;
            case DEADEYE:
                return 62;
            case MYSTIC_VIGOUR:
                return 63;
            case PIETY:
                return 70;
            case RIGOUR:
                return 74;
            case AUGURY:
                return 77;
            default:
                return -1;
        }
    }

    private static Widget prayerWidget(net.runelite.api.Prayer prayer) {
        Client c = Static.getClient();
        if (c == null || prayer == null) {
            return null;
        }
        int ordinal = prayer.ordinal();
        int[] prayerWidgets = {
                InterfaceID.Prayerbook.PRAYER1, InterfaceID.Prayerbook.PRAYER2, InterfaceID.Prayerbook.PRAYER3,
                InterfaceID.Prayerbook.PRAYER4, InterfaceID.Prayerbook.PRAYER5, InterfaceID.Prayerbook.PRAYER6,
                InterfaceID.Prayerbook.PRAYER7, InterfaceID.Prayerbook.PRAYER8, InterfaceID.Prayerbook.PRAYER9,
                InterfaceID.Prayerbook.PRAYER10, InterfaceID.Prayerbook.PRAYER11, InterfaceID.Prayerbook.PRAYER12,
                InterfaceID.Prayerbook.PRAYER13, InterfaceID.Prayerbook.PRAYER14, InterfaceID.Prayerbook.PRAYER15,
                InterfaceID.Prayerbook.PRAYER16, InterfaceID.Prayerbook.PRAYER17, InterfaceID.Prayerbook.PRAYER18,
                InterfaceID.Prayerbook.PRAYER19, InterfaceID.Prayerbook.PRAYER20, InterfaceID.Prayerbook.PRAYER21,
                InterfaceID.Prayerbook.PRAYER22, InterfaceID.Prayerbook.PRAYER23, InterfaceID.Prayerbook.PRAYER24,
                InterfaceID.Prayerbook.PRAYER25, InterfaceID.Prayerbook.PRAYER26, InterfaceID.Prayerbook.PRAYER27,
                InterfaceID.Prayerbook.PRAYER28, InterfaceID.Prayerbook.PRAYER29, InterfaceID.Prayerbook.PRAYER30
        };
        if (ordinal >= 0 && ordinal < prayerWidgets.length) {
            return c.getWidget(prayerWidgets[ordinal]);
        }
        return null;
    }

    private static net.runelite.api.Prayer prayerForQuickChild(int child) {
        if (child == WidgetGroup.QuickPrayer.THICK_SKIN_CHILD_ID) return net.runelite.api.Prayer.THICK_SKIN;
        if (child == WidgetGroup.QuickPrayer.BURST_OF_STRENGTH_CHILD_ID) return net.runelite.api.Prayer.BURST_OF_STRENGTH;
        if (child == WidgetGroup.QuickPrayer.CLARITY_OF_THOUGHT_CHILD_ID) return net.runelite.api.Prayer.CLARITY_OF_THOUGHT;
        if (child == WidgetGroup.QuickPrayer.SHARP_EYE_CHILD_ID) return net.runelite.api.Prayer.SHARP_EYE;
        if (child == WidgetGroup.QuickPrayer.MYSTIC_WILL_CHILD_ID) return net.runelite.api.Prayer.MYSTIC_WILL;
        if (child == WidgetGroup.QuickPrayer.ROCK_SKIN_CHILD_ID) return net.runelite.api.Prayer.ROCK_SKIN;
        if (child == WidgetGroup.QuickPrayer.SUPERHUMAN_STRENGTH_CHILD_ID) return net.runelite.api.Prayer.SUPERHUMAN_STRENGTH;
        if (child == WidgetGroup.QuickPrayer.IMPROVED_REFLEXES_CHILD_ID) return net.runelite.api.Prayer.IMPROVED_REFLEXES;
        if (child == WidgetGroup.QuickPrayer.RAPID_RESTORE_CHILD_ID) return net.runelite.api.Prayer.RAPID_RESTORE;
        if (child == WidgetGroup.QuickPrayer.RAPID_HEAL_CHILD_ID) return net.runelite.api.Prayer.RAPID_HEAL;
        if (child == WidgetGroup.QuickPrayer.PROTECT_ITEM_CHILD_ID) return net.runelite.api.Prayer.PROTECT_ITEM;
        if (child == WidgetGroup.QuickPrayer.HAWK_EYE_CHILD_ID) return net.runelite.api.Prayer.HAWK_EYE;
        if (child == WidgetGroup.QuickPrayer.MYSTIC_LORE_CHILD_ID) return net.runelite.api.Prayer.MYSTIC_LORE;
        if (child == WidgetGroup.QuickPrayer.STEEL_SKIN_CHILD_ID) return net.runelite.api.Prayer.STEEL_SKIN;
        if (child == WidgetGroup.QuickPrayer.ULTIMATE_STRENGTH_CHILD_ID) return net.runelite.api.Prayer.ULTIMATE_STRENGTH;
        if (child == WidgetGroup.QuickPrayer.INCREDIBLE_REFLEXES_CHILD_ID) return net.runelite.api.Prayer.INCREDIBLE_REFLEXES;
        if (child == WidgetGroup.QuickPrayer.PROTECT_FROM_MAGIC_CHILD_ID) return net.runelite.api.Prayer.PROTECT_FROM_MAGIC;
        if (child == WidgetGroup.QuickPrayer.PROTECT_FROM_MISSILES_CHILD_ID) return net.runelite.api.Prayer.PROTECT_FROM_MISSILES;
        if (child == WidgetGroup.QuickPrayer.PROTECT_FROM_MELEE_CHILD_ID) return net.runelite.api.Prayer.PROTECT_FROM_MELEE;
        if (child == WidgetGroup.QuickPrayer.EAGLE_EYE_CHILD_ID) return net.runelite.api.Prayer.EAGLE_EYE;
        if (child == WidgetGroup.QuickPrayer.MYSTIC_MIGHT_CHILD_ID) return net.runelite.api.Prayer.MYSTIC_MIGHT;
        if (child == WidgetGroup.QuickPrayer.RETRIBUTION_CHILD_ID) return net.runelite.api.Prayer.RETRIBUTION;
        if (child == WidgetGroup.QuickPrayer.REDEMPTION_CHILD_ID) return net.runelite.api.Prayer.REDEMPTION;
        if (child == WidgetGroup.QuickPrayer.SMITE_CHILD_ID) return net.runelite.api.Prayer.SMITE;
        if (child == WidgetGroup.QuickPrayer.PRESERVE_CHILD_ID) return net.runelite.api.Prayer.PRESERVE;
        if (child == WidgetGroup.QuickPrayer.CHIVALRY_CHILD_ID) return net.runelite.api.Prayer.CHIVALRY;
        if (child == WidgetGroup.QuickPrayer.PIETY_CHILD_ID) return net.runelite.api.Prayer.PIETY;
        if (child == WidgetGroup.QuickPrayer.RIGOUR_CHILD_ID) return net.runelite.api.Prayer.RIGOUR;
        if (child == WidgetGroup.QuickPrayer.AUGURY_CHILD_ID) return net.runelite.api.Prayer.AUGURY;
        return null;
    }

    private static void clickQuickPrayerChild(net.runelite.api.Prayer prayer) {
        int child = -1;
        if (prayer == net.runelite.api.Prayer.THICK_SKIN) child = WidgetGroup.QuickPrayer.THICK_SKIN_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.BURST_OF_STRENGTH) child = WidgetGroup.QuickPrayer.BURST_OF_STRENGTH_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.CLARITY_OF_THOUGHT) child = WidgetGroup.QuickPrayer.CLARITY_OF_THOUGHT_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.SHARP_EYE) child = WidgetGroup.QuickPrayer.SHARP_EYE_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.MYSTIC_WILL) child = WidgetGroup.QuickPrayer.MYSTIC_WILL_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.ROCK_SKIN) child = WidgetGroup.QuickPrayer.ROCK_SKIN_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.SUPERHUMAN_STRENGTH) child = WidgetGroup.QuickPrayer.SUPERHUMAN_STRENGTH_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.IMPROVED_REFLEXES) child = WidgetGroup.QuickPrayer.IMPROVED_REFLEXES_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.RAPID_RESTORE) child = WidgetGroup.QuickPrayer.RAPID_RESTORE_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.RAPID_HEAL) child = WidgetGroup.QuickPrayer.RAPID_HEAL_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.PROTECT_ITEM) child = WidgetGroup.QuickPrayer.PROTECT_ITEM_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.HAWK_EYE) child = WidgetGroup.QuickPrayer.HAWK_EYE_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.MYSTIC_LORE) child = WidgetGroup.QuickPrayer.MYSTIC_LORE_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.STEEL_SKIN) child = WidgetGroup.QuickPrayer.STEEL_SKIN_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.ULTIMATE_STRENGTH) child = WidgetGroup.QuickPrayer.ULTIMATE_STRENGTH_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.INCREDIBLE_REFLEXES) child = WidgetGroup.QuickPrayer.INCREDIBLE_REFLEXES_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.PROTECT_FROM_MAGIC) child = WidgetGroup.QuickPrayer.PROTECT_FROM_MAGIC_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.PROTECT_FROM_MISSILES) child = WidgetGroup.QuickPrayer.PROTECT_FROM_MISSILES_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.PROTECT_FROM_MELEE) child = WidgetGroup.QuickPrayer.PROTECT_FROM_MELEE_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.EAGLE_EYE || prayer == net.runelite.api.Prayer.DEADEYE)
            child = WidgetGroup.QuickPrayer.EAGLE_EYE_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.MYSTIC_MIGHT || prayer == net.runelite.api.Prayer.MYSTIC_VIGOUR)
            child = WidgetGroup.QuickPrayer.MYSTIC_MIGHT_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.RETRIBUTION) child = WidgetGroup.QuickPrayer.RETRIBUTION_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.REDEMPTION) child = WidgetGroup.QuickPrayer.REDEMPTION_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.SMITE) child = WidgetGroup.QuickPrayer.SMITE_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.PRESERVE) child = WidgetGroup.QuickPrayer.PRESERVE_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.CHIVALRY) child = WidgetGroup.QuickPrayer.CHIVALRY_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.PIETY) child = WidgetGroup.QuickPrayer.PIETY_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.RIGOUR) child = WidgetGroup.QuickPrayer.RIGOUR_CHILD_ID;
        else if (prayer == net.runelite.api.Prayer.AUGURY) child = WidgetGroup.QuickPrayer.AUGURY_CHILD_ID;
        if (child < 0) {
            return;
        }
        IWidget w = Widgets.getChild(WidgetInfo.QUICK_PRAYER_PRAYERS.getPackedId(), child);
        if (w == null) {
            w = Widgets.get(InterfaceID.QUICKPRAYER, WidgetInfo.QUICK_PRAYER_PRAYERS.getChildId(), child);
        }
        if (w != null) {
            w.interact("Toggle");
            w.interact("Select");
        }
    }

    private static Point findPrayerClickPoint(net.runelite.api.Prayer prayer) {
        Client c = Static.getClient();
        if (c == null) {
            return null;
        }
        int ordinal = prayer.ordinal();
        // Classic prayerbook widgets PRAYER1..PRAYER30 map roughly to enum ordinals
        int[] prayerWidgets = {
                InterfaceID.Prayerbook.PRAYER1,
                InterfaceID.Prayerbook.PRAYER2,
                InterfaceID.Prayerbook.PRAYER3,
                InterfaceID.Prayerbook.PRAYER4,
                InterfaceID.Prayerbook.PRAYER5,
                InterfaceID.Prayerbook.PRAYER6,
                InterfaceID.Prayerbook.PRAYER7,
                InterfaceID.Prayerbook.PRAYER8,
                InterfaceID.Prayerbook.PRAYER9,
                InterfaceID.Prayerbook.PRAYER10,
                InterfaceID.Prayerbook.PRAYER11,
                InterfaceID.Prayerbook.PRAYER12,
                InterfaceID.Prayerbook.PRAYER13,
                InterfaceID.Prayerbook.PRAYER14,
                InterfaceID.Prayerbook.PRAYER15,
                InterfaceID.Prayerbook.PRAYER16,
                InterfaceID.Prayerbook.PRAYER17,
                InterfaceID.Prayerbook.PRAYER18,
                InterfaceID.Prayerbook.PRAYER19,
                InterfaceID.Prayerbook.PRAYER20,
                InterfaceID.Prayerbook.PRAYER21,
                InterfaceID.Prayerbook.PRAYER22,
                InterfaceID.Prayerbook.PRAYER23,
                InterfaceID.Prayerbook.PRAYER24,
                InterfaceID.Prayerbook.PRAYER25,
                InterfaceID.Prayerbook.PRAYER26,
                InterfaceID.Prayerbook.PRAYER27,
                InterfaceID.Prayerbook.PRAYER28,
                InterfaceID.Prayerbook.PRAYER29,
                InterfaceID.Prayerbook.PRAYER30
        };
        if (ordinal >= 0 && ordinal < prayerWidgets.length) {
            try {
                Widget w = c.getWidget(prayerWidgets[ordinal]);
                Point p = ClickPoints.forWidgetOnClient(w);
                if (p != null) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static net.runelite.api.Prayer resolve(String name) {
        if (name == null) {
            return null;
        }
        String norm = name.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return net.runelite.api.Prayer.valueOf(norm);
        } catch (IllegalArgumentException e) {
            for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
                if (p.name().equalsIgnoreCase(norm) || p.name().replace("_", "").equalsIgnoreCase(norm.replace("_", ""))) {
                    return p;
                }
            }
            return null;
        }
    }
}
