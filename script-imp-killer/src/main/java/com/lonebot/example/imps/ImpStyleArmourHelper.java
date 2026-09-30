package com.lonebot.example.imps;

import com.lonebot.example.gear.ActivityGearProfile;
import com.lonebot.example.gear.ArmourLoadout;
import com.lonebot.example.gear.CombatStyle;
import com.lonebot.example.gear.StyleArmourHelper;

import java.util.List;

/**
 * Imp-facade: altijd {@link ActivityGearProfile#IMPS} ({@link ArmourLoadout#LIGHT}).
 * Nieuwe skills (Giants/SOH) gebruiken {@link StyleArmourHelper} direct met hun profile.
 */
public final class ImpStyleArmourHelper {

    private static final ArmourLoadout LOADOUT = ActivityGearProfile.IMPS.loadout;

    public static final class ArmourAction {
        public final String slot;
        public final String itemName;
        public final boolean withdraw;

        public ArmourAction(String slot, String itemName, boolean withdraw) {
            this.slot = slot;
            this.itemName = itemName;
            this.withdraw = withdraw;
        }

        static ArmourAction from(StyleArmourHelper.ArmourAction a) {
            return a == null ? null : new ArmourAction(a.slot, a.itemName, a.withdraw);
        }
    }

    private ImpStyleArmourHelper() {
    }

    private static CombatStyle cs(ImpsTypes.ImpsCombatStyle style) {
        return CombatStyle.fromImps(style);
    }

    public static List<String> armourItemNamesForStyle(ImpsTypes.ImpsCombatStyle style) {
        return StyleArmourHelper.armourItemNames(cs(style), LOADOUT);
    }

    public static boolean isStyleArmourName(ImpsTypes.ImpsCombatStyle style, String itemName) {
        return StyleArmourHelper.isStyleArmourName(cs(style), LOADOUT, itemName);
    }

    public static ArmourAction tryWithdrawOneUpgrade(ImpsTypes.ImpsCombatStyle style) {
        return ArmourAction.from(StyleArmourHelper.tryWithdrawOneUpgrade(cs(style), LOADOUT));
    }

    public static ArmourAction tryEquipOneFromInventory(ImpsTypes.ImpsCombatStyle style) {
        return ArmourAction.from(StyleArmourHelper.tryEquipOneFromInventory(cs(style), LOADOUT));
    }

    public static boolean hasUnmetArmourBankWithdraw(ImpsTypes.ImpsCombatStyle style) {
        return StyleArmourHelper.hasUnmetArmourBankWithdraw(cs(style), LOADOUT);
    }

    public static String statusSummary(ImpsTypes.ImpsCombatStyle style) {
        return StyleArmourHelper.statusSummary(cs(style), LOADOUT);
    }
}
