package com.lonebot.example.woodcutter;

import net.runelite.api.Skill;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankSnapshot;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;

/**
 * WC-bijlen: item-id eerst (namen timeouten op de client-thread).
 * Bronze dagger (1205) is géén bijl — nooit op naam-prefix "Bronze" matchen.
 * <p>
 * Beste bijl = hoogste tier die WC-level toelaat, uit inv + equip + bank
 * (live als open, anders {@link BankSnapshot}).
 */
final class WcAxes {

    static final class Axe {
        final String name;
        final int itemId;
        final int wcLevel;
        final int attackLevel;

        Axe(String name, int itemId, int wcLevel, int attackLevel) {
            this.name = name;
            this.itemId = itemId;
            this.wcLevel = wcLevel;
            this.attackLevel = attackLevel;
        }
    }

    /** Best → worst. Extra uncharged/inactive ids in {@link #EXTRA_IDS}. */
    static final Axe[] AXES = {
            new Axe("3rd age axe", 20011, 61, 60),
            new Axe("Crystal axe", 23673, 71, 70),
            new Axe("Infernal axe", 13241, 61, 60),
            new Axe("Dragon axe", 6739, 61, 60),
            new Axe("Rune axe", 1359, 41, 40),
            new Axe("Adamant axe", 1357, 31, 30),
            new Axe("Mithril axe", 1355, 21, 20),
            new Axe("Black axe", 1361, 11, 10),
            new Axe("Steel axe", 1353, 6, 5),
            new Axe("Iron axe", 1349, 1, 1),
            new Axe("Bronze axe", 1351, 1, 1)
    };

    /** Infernal uncharged, crystal inactive — telt als bijl op persoon. */
    private static final int[] EXTRA_IDS = {13242, 23675};

    static final String[] NAMES;
    static final int[] IDS;

    static {
        NAMES = new String[AXES.length];
        IDS = new int[AXES.length + EXTRA_IDS.length];
        for (int i = 0; i < AXES.length; i++) {
            NAMES[i] = AXES[i].name;
            IDS[i] = AXES[i].itemId;
        }
        System.arraycopy(EXTRA_IDS, 0, IDS, AXES.length, EXTRA_IDS.length);
    }

    private WcAxes() {
    }

    static int idOf(String axeName) {
        if (axeName == null) {
            return 0;
        }
        for (Axe a : AXES) {
            if (a.name.equalsIgnoreCase(axeName)) {
                return a.itemId;
            }
        }
        return 0;
    }

    static String nameOf(int itemId) {
        for (Axe a : AXES) {
            if (a.itemId == itemId) {
                return a.name;
            }
        }
        if (itemId == 13242) {
            return "Infernal axe";
        }
        if (itemId == 23675) {
            return "Crystal axe";
        }
        return null;
    }

    static boolean isAxeId(int itemId) {
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

    /** 0 = best; hoger = slechter. Onbekend → groot. */
    static int tierIndex(String axeName) {
        if (axeName == null) {
            return Integer.MAX_VALUE;
        }
        for (int i = 0; i < AXES.length; i++) {
            if (AXES[i].name.equalsIgnoreCase(axeName)) {
                return i;
            }
        }
        if ("Infernal axe".equalsIgnoreCase(axeName)) {
            return 2;
        }
        if ("Crystal axe".equalsIgnoreCase(axeName)) {
            return 1;
        }
        return Integer.MAX_VALUE;
    }

    static int requiredWcLevel(String axeName) {
        Axe a = byName(axeName);
        return a != null ? a.wcLevel : 1;
    }

    static int requiredAttackToWield(String axeName) {
        Axe a = byName(axeName);
        return a != null ? a.attackLevel : 1;
    }

    static boolean canWield(String axeName) {
        try {
            return Skills.getLevel(Skill.ATTACK) >= requiredAttackToWield(axeName);
        } catch (Throwable t) {
            return true;
        }
    }

    static boolean isAxeName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        if (lower.contains("battleaxe") || lower.contains("pickaxe") || lower.contains("dagger")) {
            return false;
        }
        return lower.contains(" axe") || lower.endsWith("axe");
    }

    static boolean hasAxeOnPerson() {
        try {
            if (Inventory.contains(IDS) || Equipment.contains(IDS)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        for (String axe : NAMES) {
            try {
                if (Inventory.contains(axe) || Equipment.contains(axe)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    static String currentOnPerson() {
        return bestOnPerson();
    }

    /** Beste bijl die je nu op zak/equip hebt en mag gebruiken (WC-level). */
    static String bestOnPerson() {
        int wc = WcTrees.wcLevel();
        for (Axe a : AXES) {
            if (wc < a.wcLevel) {
                continue;
            }
            if (onPerson(a)) {
                return a.name;
            }
        }
        try {
            if (Inventory.contains(13242) || Equipment.contains(13242)) {
                return "Infernal axe";
            }
            if (Inventory.contains(23675) || Equipment.contains(23675)) {
                return "Crystal axe";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Beste bijl die WC-level toelaat uit inv + equip + bank
     * (live bank of {@link BankSnapshot}).
     */
    static String bestAvailable() {
        int wc = WcTrees.wcLevel();
        for (Axe a : AXES) {
            if (wc < a.wcLevel) {
                continue;
            }
            if (onPerson(a) || knownInBank(a)) {
                return a.name;
            }
        }
        String current = bestOnPerson();
        return current != null ? current : "Bronze axe";
    }

    /**
     * True als bank (snapshot/live) een betere bruikbare bijl heeft dan op persoon.
     * Zonder snapshot en bank dicht → false (geen blinde bank-walk).
     */
    static boolean needsUpgradeFromBank() {
        String onPerson = bestOnPerson();
        if (onPerson == null) {
            return false;
        }
        int personTier = tierIndex(onPerson);
        int wc = WcTrees.wcLevel();
        for (Axe a : AXES) {
            if (wc < a.wcLevel) {
                continue;
            }
            if (tierIndex(a.name) >= personTier) {
                continue;
            }
            if (knownInBank(a)) {
                return true;
            }
        }
        return false;
    }

    static boolean tryWieldIfAllowed(String axeName) {
        if (axeName == null) {
            return true;
        }
        int id = idOf(axeName);
        try {
            if ((id > 0 && Equipment.contains(id)) || Equipment.contains(axeName)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (!canWield(axeName)) {
            return true;
        }
        try {
            var item = id > 0 ? Inventory.getFirst(id) : null;
            if (item == null) {
                item = Inventory.getFirst(inv -> inv != null && inv.getName() != null
                        && inv.getName().equalsIgnoreCase(axeName));
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

    private static boolean onPerson(Axe a) {
        try {
            if (Inventory.contains(a.itemId) || Equipment.contains(a.itemId)
                    || Inventory.contains(a.name) || Equipment.contains(a.name)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean knownInBank(Axe a) {
        try {
            if (Bank.isOpen()) {
                if (Bank.contains(a.itemId) || Bank.contains(a.name)) {
                    return true;
                }
                return false;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (BankSnapshot.contains(a.itemId) || BankSnapshot.contains(a.name)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // Extra ids (uncharged/inactive) → zelfde “tier” bijl in bank
        if (a.itemId == 13241 && BankSnapshot.contains(13242)) {
            return true;
        }
        if (a.itemId == 23673 && BankSnapshot.contains(23675)) {
            return true;
        }
        return false;
    }

    private static Axe byName(String axeName) {
        if (axeName == null) {
            return null;
        }
        for (Axe a : AXES) {
            if (a.name.equalsIgnoreCase(axeName)) {
                return a;
            }
        }
        return null;
    }
}
