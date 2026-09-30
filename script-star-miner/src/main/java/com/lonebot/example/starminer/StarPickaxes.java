package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.Skill;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankSnapshot;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.EquipmentSlot;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.widgets.BankEquipment;

import java.util.ArrayList;
import java.util.List;

/**
 * Pickaxe by item-id (client-thread name reads are flaky).
 * Ranking + Mining/Attack-reqs zoals CombatBot {@code MiningConfig} / {@code ToolWieldHelper}.
 */
public final class StarPickaxes {

    static final class Pick {
        final String name;
        final int itemId;
        final int miningLevel;
        final int attackLevel;

        Pick(String name, int itemId, int miningLevel, int attackLevel) {
            this.name = name;
            this.itemId = itemId;
            this.miningLevel = miningLevel;
            this.attackLevel = attackLevel;
        }
    }

    /** Best → worst. Meerdere ids per naam (ornament / charged). */
    static final Pick[] PICKS = {
            new Pick("Crystal pickaxe", 23680, 71, 70),
            new Pick("Crystal pickaxe", 23863, 71, 70),
            new Pick("3rd age pickaxe", 20014, 61, 60),
            new Pick("Infernal pickaxe", 13243, 61, 60),
            new Pick("Infernal pickaxe", 13244, 61, 60),
            new Pick("Dragon pickaxe", 11920, 61, 60),
            new Pick("Dragon pickaxe", 12797, 61, 60),
            new Pick("Rune pickaxe", 1275, 41, 41),
            new Pick("Adamant pickaxe", 1271, 31, 31),
            new Pick("Mithril pickaxe", 1273, 21, 21),
            new Pick("Black pickaxe", 12297, 11, 11),
            new Pick("Steel pickaxe", 1269, 6, 6),
            new Pick("Iron pickaxe", 1267, 1, 1),
            new Pick("Bronze pickaxe", 1265, 1, 1)
    };

    static final int[] IDS;

    static {
        IDS = new int[PICKS.length];
        for (int i = 0; i < PICKS.length; i++) {
            IDS[i] = PICKS[i].itemId;
        }
    }

    static final int STARDUST_ID = 25527;
    static final int COINS_ID = 995;
    static final int GENIE_LAMP_ID = 2528;
    static final int[] UNCUT_GEMS = {1623, 1621, 1619, 1617, 1625, 1627, 1629, 1631};

    private StarPickaxes() {
    }

    static int miningLevel() {
        try {
            return Skills.getLevel(Skill.MINING);
        } catch (Throwable t) {
            return 1;
        }
    }

    static int attackLevel() {
        try {
            return Skills.getLevel(Skill.ATTACK);
        } catch (Throwable t) {
            return 99;
        }
    }

    static boolean isPickaxeId(int itemId) {
        if (itemId <= 0) {
            return false;
        }
        for (int id : IDS) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }

    static boolean equipped() {
        return Equipment.contains(IDS);
    }

    static boolean onPerson() {
        try {
            if (Inventory.contains(IDS) || Equipment.contains(IDS)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        for (Pick p : PICKS) {
            try {
                if (Inventory.contains(p.name) || Equipment.contains(p.name)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    static boolean held(Pick p) {
        if (p == null) {
            return false;
        }
        try {
            if (Inventory.contains(p.itemId) || Equipment.contains(p.itemId)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return Inventory.contains(p.name) || Equipment.contains(p.name);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean inBankOrSnapshot(Pick p) {
        if (p == null) {
            return false;
        }
        try {
            if (Bank.isOpen() && (Bank.contains(p.itemId) || Bank.contains(p.name))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return BankSnapshot.contains(p.itemId) || BankSnapshot.contains(p.name);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Beste pickaxe die Mining toelaat én in inv/equip/bank (live of snapshot) zit. */
    static Pick bestAvailable() {
        int mining = miningLevel();
        for (Pick p : PICKS) {
            if (mining < p.miningLevel) {
                continue;
            }
            if (held(p) || inBankOrSnapshot(p)) {
                return p;
            }
        }
        return currentOnPerson();
    }

    /** Beste pickaxe die je nu bij je hebt (best → worst). */
    static Pick currentOnPerson() {
        for (Pick p : PICKS) {
            if (held(p)) {
                return p;
            }
        }
        return null;
    }

    static int rank(Pick p) {
        if (p == null) {
            return Integer.MAX_VALUE;
        }
        for (int i = 0; i < PICKS.length; i++) {
            if (PICKS[i].name.equalsIgnoreCase(p.name)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Bank/snapshot heeft een betere pickaxe dan wat je nu draagt/vasthoudt. */
    static boolean needsUpgradeFromBank() {
        Pick cur = currentOnPerson();
        Pick best = bestAvailable();
        if (cur == null || best == null) {
            return false;
        }
        return rank(best) < rank(cur);
    }

    static boolean hasSpareBesidesBest() {
        Pick best = currentOnPerson();
        if (best == null) {
            return false;
        }
        for (Pick p : PICKS) {
            if (p.name.equalsIgnoreCase(best.name)) {
                continue;
            }
            if (held(p)) {
                return true;
            }
        }
        return false;
    }

    static boolean canWield(String pickName) {
        Pick p = byName(pickName);
        int req = p != null ? p.attackLevel : 1;
        return attackLevel() >= req;
    }

    static int[] idsForName(String name) {
        if (name == null || name.isBlank()) {
            return new int[0];
        }
        List<Integer> ids = new ArrayList<>();
        for (Pick p : PICKS) {
            if (p.name.equalsIgnoreCase(name)) {
                ids.add(p.itemId);
            }
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    /**
     * Mining-loot / pickaxe / eten — geen reden om van de ster weg te banken.
     * Overige inv-items (wapens, random gear) → gear-prep.
     */
    static boolean isAllowedWhileMining(IInventoryItem item) {
        if (item == null) {
            return true;
        }
        int id = item.getId();
        if (isPickaxeId(id) || isUncutGem(id) || id == STARDUST_ID || id == COINS_ID || id == GENIE_LAMP_ID) {
            return true;
        }
        try {
            if (item.hasAction("Eat") || item.hasAction("Drink")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String n = null;
        try {
            n = item.getName();
        } catch (Throwable ignored) {
        }
        if (n == null || n.isBlank()) {
            return true;
        }
        String lower = n.toLowerCase();
        if (lower.contains("pickaxe")
                || lower.contains("stardust")
                || lower.startsWith("uncut")
                || lower.contains("geode")
                || lower.contains("clue")
                || lower.contains("lamp")
                || lower.contains("coins")) {
            return true;
        }
        if (lower.contains("axe") && !lower.contains("pickaxe")) {
            return true;
        }
        // Wacht-activiteit Mine / woodcut loot — niet naar bank voor dump
        if (lower.contains(" ore") || lower.endsWith(" ore") || lower.equals("ore")
                || lower.contains("coal") || lower.contains("clay")
                || lower.contains("logs") || lower.endsWith(" logs")) {
            return true;
        }
        if (lower.endsWith(" rune") || lower.contains("runes") || lower.contains("pouch")) {
            return true;
        }
        return lower.contains("staff of air")
                || lower.contains("air battlestaff")
                || lower.contains("mystic air")
                || lower.contains("brown apron")
                || lower.contains("crafting cape")
                || lower.contains("staff of fire")
                || lower.contains("fire battlestaff")
                || lower.contains("mystic fire")
                || lower.contains("lava battlestaff")
                || lower.contains("mystic lava")
                || (lower.contains("tome of fire") && !lower.contains("empty"));
    }

    static boolean hasForeignItems() {
        try {
            if (StarMinerPlugin.waitActivity() == StarMinerPlugin.WaitActivity.HIGH_ALCH) {
                return false;
            }
            return Inventory.getFirst(item -> item != null && !isAllowedWhileMining(item)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return true if we issued Wield */
    static boolean tryWield() {
        if (equipped()) {
            return false;
        }
        int mining = miningLevel();
        for (Pick p : PICKS) {
            if (mining < p.miningLevel) {
                continue;
            }
            if (!canWield(p.name)) {
                continue;
            }
            IInventoryItem it = Inventory.getFirst(p.itemId);
            if (it == null) {
                continue;
            }
            return it.interact("Wield") || it.interact("Wear");
        }
        return false;
    }

    static boolean tryWieldIfAllowed(String pickName) {
        if (pickName == null || pickName.isBlank()) {
            return true;
        }
        int[] ids = idsForName(pickName);
        try {
            if ((ids.length > 0 && Equipment.contains(ids)) || Equipment.contains(pickName)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (!canWield(pickName)) {
            return true;
        }
        try {
            IInventoryItem item = null;
            for (int id : ids) {
                item = Inventory.getFirst(id);
                if (item != null) {
                    break;
                }
            }
            if (item == null) {
                item = Inventory.getFirst(pickName);
            }
            if (item == null) {
                return true;
            }
            if (item.hasAction("Wield")) {
                item.interact("Wield");
            } else if (item.hasAction("Wear")) {
                item.interact("Wear");
            } else {
                item.interact("Wield");
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static String label(Pick p) {
        return p != null ? p.name : "geen";
    }

    static Pick bronzeFallback() {
        return PICKS[PICKS.length - 1];
    }

    /**
     * Bank open: haal een slechtere pickaxe van de weapon-slot zodat deposit die kan storten.
     *
     * @return true als Remove/unequip is uitgegeven
     */
    static boolean tryUnequipWorsePickaxe() {
        try {
            if (!Bank.isOpen()) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
        Pick best = bestAvailable();
        IInventoryItem wep;
        try {
            wep = Equipment.get(EquipmentSlot.WEAPON);
        } catch (Throwable t) {
            return false;
        }
        if (wep == null || !isPickaxeId(wep.getId())) {
            return false;
        }
        String wepName = nameOf(wep.getId());
        if (best != null && wepName != null && wepName.equalsIgnoreCase(best.name)) {
            return false;
        }
        try {
            if (BankEquipment.unequip(EquipmentSlot.WEAPON)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return wep.interact("Remove") || wep.interact("Unequip");
        } catch (Throwable t) {
            return false;
        }
    }

    static String nameOf(int itemId) {
        for (Pick p : PICKS) {
            if (p.itemId == itemId) {
                return p.name;
            }
        }
        return null;
    }

    private static boolean isUncutGem(int itemId) {
        for (int id : UNCUT_GEMS) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }

    private static Pick byName(String name) {
        if (name == null) {
            return null;
        }
        for (Pick p : PICKS) {
            if (p.name.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}
