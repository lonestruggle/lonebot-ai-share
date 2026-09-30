package com.lonebot.example.giants;

import com.lonebot.example.gear.ActivityGearProfile;
import com.lonebot.example.gear.ArmourLoadout;
import com.lonebot.example.gear.CombatStyle;
import com.lonebot.example.gear.StyleArmourHelper;
import com.lonebot.example.imps.ImpGearCheck;
import com.lonebot.example.imps.ImpsTypes;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.combat.RangedAmmoKit;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.HumanBanking;
import net.storm.sdk.items.Inventory;
import net.runelite.api.Skill;

import java.util.Locale;

/**
 * FULL F2P armour ({@link ActivityGearProfile#GIANTS}) + Imp Killer weapon/rune gates.
 */
public final class GiantsGear {

    private static final ArmourLoadout LOADOUT = ActivityGearProfile.GIANTS.loadout;

    private GiantsGear() {
    }

    public static CombatStyle gearStyle(GiantsTypes.GiantsCombatStyle style) {
        return style != null ? style.toGear() : CombatStyle.MELEE;
    }

    public static ImpGearCheck.Snapshot snapshot(
            GiantsTypes.GiantsCombatStyle style,
            GiantsTypes.GiantsMageSpell spell,
            boolean magicAutoUpdate
    ) {
        ImpsTypes.ImpsCombatStyle impsStyle = style != null ? style.toImps() : ImpsTypes.ImpsCombatStyle.MELEE;
        ImpsTypes.ImpsMageSpell impsSpell = spell != null
                ? spell.toImps()
                : ImpsTypes.ImpsMageSpell.WIND_STRIKE;
        return ImpGearCheck.evaluate(impsStyle, impsSpell, magicAutoUpdate, 0);
    }

    public static boolean suppliesLow(
            GiantsTypes.GiantsCombatStyle style,
            GiantsTypes.GiantsMageSpell spell,
            boolean magicAutoUpdate
    ) {
        ImpGearCheck.Snapshot s = snapshot(style, spell, magicAutoUpdate);
        if (style == GiantsTypes.GiantsCombatStyle.RANGED) {
            return s.arrowCount < 40;
        }
        if (style == GiantsTypes.GiantsCombatStyle.MAGE) {
            return s.usableCasts < 20;
        }
        return s.meleeWeapon == null && ImpGearCheck.firstUsableMeleeOnPerson() == null;
    }

    public static int tryEquipOne(GiantsTypes.GiantsCombatStyle style) {
        CombatStyle cs = gearStyle(style);
        if (style == GiantsTypes.GiantsCombatStyle.MELEE) {
            String w = ImpGearCheck.firstUsableMeleeOnPerson();
            if (w != null && !Equipment.contains(w)) {
                IInventoryItem item = Inventory.getFirst(w);
                if (item != null && wield(item)) {
                    GiantsLog.action("gear", "wield " + w);
                    return HumanBanking.afterEquipMs();
                }
            }
        }
        if (style == GiantsTypes.GiantsCombatStyle.RANGED) {
            String bow = ImpGearCheck.firstBowOnPerson();
            if (bow != null && !Equipment.contains(bow)) {
                IInventoryItem item = Inventory.getFirst(bow);
                if (item != null && wield(item)) {
                    GiantsLog.action("gear", "wield " + bow);
                    return HumanBanking.afterEquipMs();
                }
            }
            int rng = Skills.getLevel(Skill.RANGED);
            String ammo = RangedAmmoKit.tryEquipRangedAmmoFromInventory(rng, 0L);
            if (ammo != null && !ammo.isEmpty()) {
                GiantsLog.action("gear", "wield ammo " + ammo);
                return HumanBanking.afterEquipMs();
            }
        }
        if (style == GiantsTypes.GiantsCombatStyle.MAGE) {
            IInventoryItem staff = Inventory.getFirst(i -> i != null && i.getName() != null
                    && i.getName().toLowerCase(Locale.ROOT).contains("staff"));
            if (staff != null && !Equipment.contains(staff.getName())) {
                if (wield(staff)) {
                    GiantsLog.action("gear", "wield " + staff.getName());
                    return HumanBanking.afterEquipMs();
                }
            }
        }
        StyleArmourHelper.ArmourAction arm = StyleArmourHelper.tryEquipOneFromInventory(cs, LOADOUT);
        if (arm != null) {
            GiantsLog.action("gear", ("unequip".equals(arm.slot) ? "unequip " : "wear ") + arm.itemName);
            return HumanBanking.afterEquipMs();
        }
        return 0;
    }

    public static int tryWithdrawOne(GiantsTypes.GiantsCombatStyle style, GiantsTypes.GiantsMageSpell spell) {
        if (!Bank.isOpen()) {
            return 0;
        }
        CombatStyle cs = gearStyle(style);
        if (style == GiantsTypes.GiantsCombatStyle.MELEE) {
            if (ImpGearCheck.firstUsableMeleeOnPerson() == null) {
                String bankW = ImpGearCheck.firstUsableMeleeInBank();
                if (bankW != null) {
                    Bank.withdraw(bankW, 1);
                    GiantsLog.action("bank", "withdraw-1 " + bankW);
                    return HumanBanking.afterActionMs();
                }
            }
        }
        if (style == GiantsTypes.GiantsCombatStyle.RANGED) {
            if (ImpGearCheck.firstBowOnPerson() == null) {
                String bow = ImpGearCheck.firstBowInBank();
                if (bow != null) {
                    Bank.withdraw(bow, 1);
                    GiantsLog.action("bank", "withdraw-1 " + bow);
                    return HumanBanking.afterActionMs();
                }
            }
            String arrow = ImpGearCheck.firstArrowNameOnPerson();
            if (arrow == null || Inventory.getCount(arrow) + RangedAmmoKit.getEquippedRangedAmmoQuantity() < 80) {
                for (String a : new String[]{
                        "Rune arrow", "Adamant arrow", "Mithril arrow", "Steel arrow", "Iron arrow", "Bronze arrow"
                }) {
                    if (ImpGearCheck.canUseArrow(a) && Bank.contains(a)) {
                        Bank.withdrawAll(a);
                        GiantsLog.action("bank", "withdraw-all " + a);
                        return HumanBanking.afterActionMs();
                    }
                }
            }
        }
        if (style == GiantsTypes.GiantsCombatStyle.MAGE && spell != null) {
            ImpsTypes.ImpsMageSpell s = spell.toImps();
            String staff = s.getPreferredStaff();
            if (staff != null && !Equipment.contains(staff) && !Inventory.contains(staff) && Bank.contains(staff)) {
                Bank.withdraw(staff, 1);
                GiantsLog.action("bank", "withdraw-1 " + staff);
                return HumanBanking.afterActionMs();
            }
            String cat = s.getCatalystRune();
            if (cat != null && Inventory.getCount(cat) < 80 && Bank.contains(cat)) {
                Bank.withdrawAll(cat);
                GiantsLog.action("bank", "withdraw-all " + cat);
                return HumanBanking.afterActionMs();
            }
            if (s.needsAirRune() && Inventory.getCount("Air rune") < 80 && Bank.contains("Air rune")) {
                Bank.withdrawAll("Air rune");
                GiantsLog.action("bank", "withdraw-all Air rune");
                return HumanBanking.afterActionMs();
            }
            String el = s.getElementalRune();
            if (el != null && !el.equalsIgnoreCase("Air rune")
                    && Inventory.getCount(el) < 80 && Bank.contains(el)) {
                Bank.withdrawAll(el);
                GiantsLog.action("bank", "withdraw-all " + el);
                return HumanBanking.afterActionMs();
            }
        }
        StyleArmourHelper.ArmourAction w = StyleArmourHelper.tryWithdrawOneUpgrade(cs, LOADOUT);
        if (w != null) {
            GiantsLog.action("bank", "withdraw-1 " + w.itemName + " [" + w.slot + "]");
            return HumanBanking.afterActionMs();
        }
        return 0;
    }

    public static boolean isStyleKeepName(GiantsTypes.GiantsCombatStyle style, String name) {
        if (name == null) {
            return false;
        }
        if (StyleArmourHelper.isStyleArmourName(gearStyle(style), LOADOUT, name)) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("amulet of power") || lower.contains("amulet of strength")
                || lower.contains("amulet of magic") || lower.contains("amulet of accuracy")) {
            return true;
        }
        if (ImpGearCheck.isMeleeWeaponName(name) || lower.contains("bow") || lower.contains("staff")) {
            return true;
        }
        return lower.endsWith(" rune") || lower.contains("arrow");
    }

    private static boolean wield(IInventoryItem item) {
        if (item == null) {
            return false;
        }
        try {
            String action = item.hasAction("Wield") ? "Wield" : (item.hasAction("Wear") ? "Wear" : null);
            if (action == null) {
                return false;
            }
            item.interact(action);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
