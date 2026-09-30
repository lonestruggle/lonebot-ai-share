package com.lonebot.example.gear;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.game.Skills;
import net.storm.sdk.game.Static;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gedeelde F2P style-armour + amulet prep (wiki + CombatBot {@code StyleArmourBankHelper}).
 * <p>
 * Altijd <b>beste dat je levels aankunnen</b> (Defence / Ranged / Magic + Dragon Slayer I waar nodig).
 * Loadout:
 * <ul>
 *   <li>{@link ArmourLoadout#LIGHT} — imps: geen plate (gewicht)</li>
 *   <li>{@link ArmourLoadout#FULL} — giants/SOH/…: volledige F2P set</li>
 * </ul>
 * Armour is best-effort (blokkeert combat-ready niet); wapen/runes/ammo doen de harde gate.
 */
public final class StyleArmourHelper {

    public static final class ArmourAction {
        public final String slot;
        public final String itemName;
        public final boolean withdraw;

        public ArmourAction(String slot, String itemName, boolean withdraw) {
            this.slot = slot;
            this.itemName = itemName;
            this.withdraw = withdraw;
        }
    }

    private static final class GearPiece {
        final String slot;
        final String name;
        final int defenseReq;
        final int rangedReq;
        final int magicReq;
        final boolean needsDragonSlayer;

        GearPiece(String slot, String name, int def, int range, int magic, boolean needsDs) {
            this.slot = slot;
            this.name = name;
            this.defenseReq = def;
            this.rangedReq = range;
            this.magicReq = magic;
            this.needsDragonSlayer = needsDs;
        }
    }

    private static GearPiece gp(String slot, String name, int def, int range, int magic) {
        return new GearPiece(slot, name, def, range, magic, false);
    }

    private static GearPiece gpDs(String slot, String name, int def, int range, int magic) {
        return new GearPiece(slot, name, def, range, magic, true);
    }

    /** Accessories (alle loadouts). */
    private static final GearPiece[] MELEE_ACCESSORIES = {
            gp("NECK", "Amulet of strength", 1, 0, 0),
            gp("NECK", "Amulet of power", 1, 0, 0),
            gp("NECK", "Amulet of accuracy", 1, 0, 0),
            gp("NECK", "Amulet of defence", 1, 0, 0),
            gp("HANDS", "Leather gloves", 1, 0, 0),
            gp("FEET", "Fighting boots", 1, 0, 0),
            gp("FEET", "Leather boots", 1, 0, 0),
            gp("CAPE", "Black cape", 1, 0, 0), gp("CAPE", "Red cape", 1, 0, 0),
            gp("CAPE", "Blue cape", 1, 0, 0), gp("CAPE", "Yellow cape", 1, 0, 0),
            gp("CAPE", "Green cape", 1, 0, 0), gp("CAPE", "Orange cape", 1, 0, 0),
            gp("CAPE", "Purple cape", 1, 0, 0),
    };

    /** F2P plate — alleen {@link ArmourLoadout#FULL}. */
    private static final GearPiece[] MELEE_PLATE = {
            gp("HEAD", "Rune full helm", 40, 0, 0), gp("HEAD", "Rune med helm", 40, 0, 0),
            gp("HEAD", "Adamant full helm", 30, 0, 0), gp("HEAD", "Adamant med helm", 30, 0, 0),
            gp("HEAD", "Mithril full helm", 20, 0, 0), gp("HEAD", "Mithril med helm", 20, 0, 0),
            gp("HEAD", "Black full helm", 10, 0, 0), gp("HEAD", "Steel full helm", 5, 0, 0),
            gp("HEAD", "Steel med helm", 5, 0, 0), gp("HEAD", "Iron full helm", 1, 0, 0),
            gp("HEAD", "Bronze full helm", 1, 0, 0),

            gp("BODY", "Rune chainbody", 40, 0, 0),
            gpDs("BODY", "Rune platebody", 40, 0, 0),
            gp("BODY", "Adamant platebody", 30, 0, 0), gp("BODY", "Adamant chainbody", 30, 0, 0),
            gp("BODY", "Mithril platebody", 20, 0, 0), gp("BODY", "Mithril chainbody", 20, 0, 0),
            gp("BODY", "Black platebody", 10, 0, 0), gp("BODY", "Steel platebody", 5, 0, 0),
            gp("BODY", "Iron platebody", 1, 0, 0), gp("BODY", "Bronze platebody", 1, 0, 0),

            gp("LEGS", "Rune platelegs", 40, 0, 0), gp("LEGS", "Rune plateskirt", 40, 0, 0),
            gp("LEGS", "Adamant platelegs", 30, 0, 0), gp("LEGS", "Mithril platelegs", 20, 0, 0),
            gp("LEGS", "Black platelegs", 10, 0, 0), gp("LEGS", "Steel platelegs", 5, 0, 0),
            gp("LEGS", "Iron platelegs", 1, 0, 0), gp("LEGS", "Bronze platelegs", 1, 0, 0),

            gp("SHIELD", "Rune kiteshield", 40, 0, 0), gp("SHIELD", "Adamant kiteshield", 30, 0, 0),
            gp("SHIELD", "Mithril kiteshield", 20, 0, 0), gp("SHIELD", "Black kiteshield", 10, 0, 0),
            gp("SHIELD", "Steel kiteshield", 5, 0, 0), gp("SHIELD", "Iron kiteshield", 1, 0, 0),
            gp("SHIELD", "Wooden shield", 1, 0, 0),
    };

    /**
     * Ranged F2P — reqs = Defence + Ranged (wiki / OSRS).
     * Green d'hide: Def 40 + Range 40 (+ DS1 body).
     */
    private static final GearPiece[] RANGED_ARMOUR_TIER = {
            gp("NECK", "Amulet of power", 1, 0, 0),
            gp("NECK", "Amulet of accuracy", 1, 0, 0),
            gp("NECK", "Amulet of defence", 1, 0, 0),

            gp("HEAD", "Green d'hide coif", 40, 40, 0),
            gp("HEAD", "Coif", 1, 20, 0),
            gp("HEAD", "Leather cowl", 1, 1, 0),

            gpDs("BODY", "Green d'hide body", 40, 40, 0),
            gp("BODY", "Studded body", 20, 20, 0),
            gp("BODY", "Hardleather body", 10, 1, 0),
            gp("BODY", "Leather body", 1, 1, 0),

            gp("LEGS", "Green d'hide chaps", 40, 40, 0),
            gp("LEGS", "Studded chaps", 20, 20, 0),
            gp("LEGS", "Leather chaps", 1, 1, 0),

            gp("HANDS", "Green d'hide vambraces", 40, 40, 0),
            gp("HANDS", "Leather vambraces", 1, 1, 0),

            gp("FEET", "Leather boots", 1, 1, 0),
            gp("FEET", "Fighting boots", 1, 0, 0),

            gp("CAPE", "Black cape", 1, 0, 0), gp("CAPE", "Red cape", 1, 0, 0),
            gp("CAPE", "Blue cape", 1, 0, 0), gp("CAPE", "Yellow cape", 1, 0, 0),
            gp("CAPE", "Green cape", 1, 0, 0), gp("CAPE", "Orange cape", 1, 0, 0),
            gp("CAPE", "Purple cape", 1, 0, 0),
    };

    /** Mage F2P (wiki Early Game) — geen mystic (members). */
    private static final GearPiece[] MAGE_ARMOUR_TIER = {
            gp("NECK", "Amulet of magic", 1, 0, 0),
            gp("NECK", "Amulet of power", 1, 0, 0),
            gp("NECK", "Amulet of accuracy", 1, 0, 0),
            gp("NECK", "Amulet of defence", 1, 0, 0),

            gp("HEAD", "Blue wizard hat", 1, 0, 1),
            gp("HEAD", "Wizard hat", 1, 0, 1),

            gp("BODY", "Blue wizard robe", 1, 0, 1),
            gp("BODY", "Wizard robe", 1, 0, 1),

            gp("LEGS", "Zamorak monk bottom", 1, 0, 1),
            gp("LEGS", "Monk's robe", 1, 0, 1),

            gp("HANDS", "Leather gloves", 1, 0, 0),

            gp("FEET", "Leather boots", 1, 0, 0),
            gp("FEET", "Fighting boots", 1, 0, 0),

            gp("CAPE", "Black cape", 1, 0, 0), gp("CAPE", "Red cape", 1, 0, 0),
            gp("CAPE", "Blue cape", 1, 0, 0), gp("CAPE", "Yellow cape", 1, 0, 0),
            gp("CAPE", "Green cape", 1, 0, 0), gp("CAPE", "Orange cape", 1, 0, 0),
            gp("CAPE", "Purple cape", 1, 0, 0),
    };

    private static final GearPiece[] MELEE_LIGHT = MELEE_ACCESSORIES;
    private static final GearPiece[] MELEE_FULL = concat(MELEE_PLATE, MELEE_ACCESSORIES);

    private StyleArmourHelper() {
    }

    public static List<String> armourItemNames(CombatStyle style, ArmourLoadout loadout) {
        GearPiece[] list = armourTiers(style, loadout);
        List<String> names = new ArrayList<>();
        for (GearPiece g : list) {
            boolean dup = false;
            for (String n : names) {
                if (n.equalsIgnoreCase(g.name)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                names.add(g.name);
            }
        }
        return names;
    }

    public static boolean isStyleArmourName(CombatStyle style, ArmourLoadout loadout, String itemName) {
        return findPiece(style, loadout, itemName) != null;
    }

    public static boolean isMeleePlateName(String itemName) {
        return findPieceIn(MELEE_PLATE, itemName) != null;
    }

    public static ArmourAction tryWithdrawOneUpgrade(CombatStyle style, ArmourLoadout loadout) {
        if (!Bank.isOpen()) {
            return null;
        }
        GearPiece[] list = armourTiers(style, loadout);
        for (String slot : gearSlots(list)) {
            GearPiece bankBest = bestBankWearable(list, slot, style);
            if (bankBest == null) {
                continue;
            }
            int bankIdx = gearTierIndex(list, bankBest.name);
            GearPiece equipped = bestEquippedWearable(list, slot, style);
            if (equipped != null && gearTierIndex(list, equipped.name) <= bankIdx) {
                continue;
            }
            GearPiece owned = bestOwnedWearable(list, slot, style);
            int ownedIdx = owned != null ? gearTierIndex(list, owned.name) : Integer.MAX_VALUE;
            if (bankIdx >= ownedIdx) {
                continue;
            }
            // Zelfde tier al in inv (plain of (t)/(g)) — equip-fase
            if (owned != null && owned.name.equalsIgnoreCase(bankBest.name)
                    && GearItemNames.findInInventory(owned.name) != null) {
                continue;
            }
            clearWorseInInv(list, slot, bankBest);
            String bankName = GearItemNames.findInBank(bankBest.name);
            if (bankName == null || Inventory.isFull()) {
                continue;
            }
            Bank.withdraw(bankName, 1);
            return new ArmourAction(slot, bankName, true);
        }
        return null;
    }

    public static ArmourAction tryEquipOneFromInventory(CombatStyle style, ArmourLoadout loadout) {
        if (style == CombatStyle.RANGED && tryUnequipBlockingMeleePlate()) {
            return new ArmourAction("unequip", "melee-plate", false);
        }
        GearPiece[] list = armourTiers(style, loadout);
        for (String slot : gearSlots(list)) {
            if (bestEquippedWearable(list, slot, style) != null) {
                continue;
            }
            for (GearPiece g : list) {
                if (!g.slot.equals(slot) || !handsAllowed(style, g) || !canWear(g)) {
                    continue;
                }
                IInventoryItem hit = GearItemNames.firstInventoryItem(g.name);
                if (hit == null) {
                    continue;
                }
                String action = hit.hasAction("Wear") ? "Wear"
                        : (hit.hasAction("Wield") ? "Wield" : null);
                if (action == null) {
                    continue;
                }
                try {
                    hit.interact(action);
                    return new ArmourAction(slot, hit.getName(), false);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static boolean hasUnmetArmourBankWithdraw(CombatStyle style, ArmourLoadout loadout) {
        if (!Bank.isOpen()) {
            return false;
        }
        GearPiece[] list = armourTiers(style, loadout);
        for (String slot : gearSlots(list)) {
            GearPiece bankBest = bestBankWearable(list, slot, style);
            if (bankBest == null) {
                continue;
            }
            GearPiece owned = bestOwnedWearable(list, slot, style);
            int bankIdx = gearTierIndex(list, bankBest.name);
            int ownedIdx = owned != null ? gearTierIndex(list, owned.name) : Integer.MAX_VALUE;
            if (bankIdx < ownedIdx) {
                return true;
            }
        }
        return false;
    }

    public static String statusSummary(CombatStyle style, ArmourLoadout loadout) {
        GearPiece[] list = armourTiers(style, loadout);
        StringBuilder sb = new StringBuilder();
        sb.append(loadout == ArmourLoadout.LIGHT ? "light" : "full");
        for (String slot : gearSlots(list)) {
            GearPiece eq = bestEquippedWearable(list, slot, style);
            sb.append(' ').append(slot).append('=');
            if (eq == null) {
                sb.append('-');
            } else {
                String actual = GearItemNames.findInEquipment(eq.name);
                sb.append(shortName(actual != null ? actual : eq.name));
            }
        }
        int rng = safeLevel(Skill.RANGED);
        int def = safeLevel(Skill.DEFENCE);
        sb.append(" | R").append(rng).append("/D").append(def);
        return sb.toString();
    }

    // ---- internals ----

    private static GearPiece[] armourTiers(CombatStyle style, ArmourLoadout loadout) {
        if (style == CombatStyle.RANGED) {
            return RANGED_ARMOUR_TIER;
        }
        if (style == CombatStyle.MAGE) {
            return MAGE_ARMOUR_TIER;
        }
        return loadout == ArmourLoadout.FULL ? MELEE_FULL : MELEE_LIGHT;
    }

    private static GearPiece[] concat(GearPiece[] a, GearPiece[] b) {
        GearPiece[] out = new GearPiece[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static GearPiece findPiece(CombatStyle style, ArmourLoadout loadout, String itemName) {
        return findPieceIn(armourTiers(style, loadout), itemName);
    }

    private static GearPiece findPieceIn(GearPiece[] list, String itemName) {
        if (itemName == null) {
            return null;
        }
        for (GearPiece g : list) {
            if (GearItemNames.matches(g.name, itemName)) {
                return g;
            }
        }
        return null;
    }

    /** Alleen wearbaar op huidige levels (+ DS1). */
    private static boolean canWear(GearPiece g) {
        if (g == null) {
            return false;
        }
        if (g.needsDragonSlayer && !isDragonSlayerFinished()) {
            return false;
        }
        return safeLevel(Skill.DEFENCE) >= g.defenseReq
                && safeLevel(Skill.RANGED) >= g.rangedReq
                && safeLevel(Skill.MAGIC) >= g.magicReq;
    }

    private static boolean isDragonSlayerFinished() {
        try {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            return Quest.DRAGON_SLAYER_I.getState(c) == QuestState.FINISHED;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int safeLevel(Skill skill) {
        try {
            return Skills.getLevel(skill);
        } catch (Throwable t) {
            return 1;
        }
    }

    private static boolean handsAllowed(CombatStyle style, GearPiece g) {
        if (g == null || !"HANDS".equals(g.slot)) {
            return true;
        }
        String lower = g.name.toLowerCase(Locale.ROOT);
        boolean vamb = lower.contains("vambrace");
        boolean glove = lower.contains("glove") && !vamb;
        if (style == CombatStyle.RANGED) {
            return vamb;
        }
        if (style == CombatStyle.MAGE) {
            return glove;
        }
        return vamb || glove;
    }

    private static List<String> gearSlots(GearPiece[] list) {
        List<String> out = new ArrayList<>();
        for (GearPiece g : list) {
            if (!out.contains(g.slot)) {
                out.add(g.slot);
            }
        }
        return out;
    }

    private static int gearTierIndex(GearPiece[] list, String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < list.length; i++) {
            if (list[i].name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static GearPiece bestEquippedWearable(GearPiece[] list, String slot, CombatStyle style) {
        for (GearPiece g : list) {
            if (!g.slot.equals(slot) || !handsAllowed(style, g) || !canWear(g)) {
                continue;
            }
            if (GearItemNames.findInEquipment(g.name) != null) {
                return g;
            }
        }
        return null;
    }

    private static GearPiece bestOwnedWearable(GearPiece[] list, String slot, CombatStyle style) {
        for (GearPiece g : list) {
            if (!g.slot.equals(slot) || !handsAllowed(style, g) || !canWear(g)) {
                continue;
            }
            if (GearItemNames.ownedOnPerson(g.name)) {
                return g;
            }
        }
        return null;
    }

    private static GearPiece bestBankWearable(GearPiece[] list, String slot, CombatStyle style) {
        for (GearPiece g : list) {
            if (!g.slot.equals(slot) || !handsAllowed(style, g) || !canWear(g)) {
                continue;
            }
            if (GearItemNames.ownedInBank(g.name)) {
                return g;
            }
        }
        return null;
    }

    private static void clearWorseInInv(GearPiece[] list, String slot, GearPiece bankBest) {
        int bankIdx = gearTierIndex(list, bankBest.name);
        for (GearPiece g : list) {
            if (!g.slot.equals(slot)) {
                continue;
            }
            if (gearTierIndex(list, g.name) <= bankIdx) {
                continue;
            }
            for (IInventoryItem i : Inventory.getAll()) {
                if (i == null || i.getName() == null) {
                    continue;
                }
                if (GearItemNames.matches(g.name, i.getName())) {
                    try {
                        Bank.depositAll(i.getName());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private static boolean tryUnequipBlockingMeleePlate() {
        if (Inventory.getFreeSlots() < 1) {
            return false;
        }
        try {
            for (IInventoryItem eq : Equipment.getAll()) {
                if (eq == null || eq.getName() == null) {
                    continue;
                }
                if (findPieceIn(MELEE_PLATE, eq.getName()) == null) {
                    continue;
                }
                for (String action : new String[]{"Remove", "Unequip"}) {
                    if (eq.hasAction(action)) {
                        eq.interact(action);
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String shortName(String name) {
        if (name == null) {
            return "?";
        }
        return name.length() <= 18 ? name : name.substring(0, 16) + "…";
    }
}
